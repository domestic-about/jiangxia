package org.dromara.lqg.cryo.batch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchPageVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowDeltaRow;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.remind.service.CryoOverdueService;
import org.dromara.lqg.cryo.remind.sql.CryoOverdueSqlProvider;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleNameResolver;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 冻存批次读侧（doc/api-contract.md 的 {@code GET /lqg/cryo/batch/list}、{@code GET /lqg/cryo/batch/{id}}）。
 *
 * <p>★★ <b>剩余支数是读时算的，且一页只发一次聚合查询</b>（ticket §2 / ADR-0010）：
 * {@code remainingQty = init_qty + SUM(未删流水的 delta)}。本类拿<b>本页的批次 id 集合</b>调
 * {@link CryoFlowMapper#selectDeltaSums(Collection)} 一条 {@code GROUP BY} 出结果，
 * 没有流水的批次补 0 —— 逐行 {@code selectList} 求和是最自然也最错的实现。
 *
 * <p>★ <b>四个读时带出的键</b>：{@code remainingQty / location / internalNo / sourceUnitName}
 * （{@code location} 与 {@code internalNo} 是 accept 3 逐格断的）。
 *
 * <p>★ <b>软删不出现</b>：批次由实体上的 {@code @TableLogic} 兜住、流水由聚合 SQL 里手写的
 * {@code del_flag='0'} 兜住（seed 的 3008 是软删批次、3002 名下有一条软删的 {@code -1}，
 * 都是为这两条埋的）。本类不写任何绕过逻辑删的原生 SQL、不手写 join。
 *
 * <p>★ <b>排序两档</b>：不带 {@code sort} = <b>超期置顶</b>，其余按创建时间倒序（工作台那一档）；
 * 带 {@code sort=recent} = 按 {@code COALESCE(update_time, create_time)} 倒序
 * （CRYO-MP-001 的历史编辑记录，CR-20260918-07——它不置顶超期，保持「最近改过的在上」）。
 *
 * <p>★ <b>超期相关的四个键全在本类补齐</b>（CRYO-REMIND-001 ticket §2）：每行
 * {@code overdue / overdueDays}、筛选 {@code overdueOnly}、响应 {@code tabCounts}。
 * 2026-09-24 同样在本类补「已取空」三件套：每行 {@code emptied}、筛选 {@code emptiedOnly}、
 * {@code tabCounts.emptied}（判据同一份剩余算式，见 {@code CryoOverdueSqlProvider.EMPTIED_WHERE}）。
 * 判定一律走 {@link CryoOverdueService#isOverdue}（唯一判定函数）与
 * {@link CryoOverdueSqlProvider#WHERE}（唯一一份 SQL where 片段），
 * 本类<b>不另写一份 where、不写任何天数常量</b>。
 *
 * @author CRYO-MODEL-001 / CRYO-REMIND-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoQueryService {

    /**
     * 「默认排序超期置顶」那个排序键在 {@code paramNameValuePairs} 里的名字
     * （阈值当参数绑定进 {@code ORDER BY CASE WHEN …}，不写字面量）。
     *
     * <p>★ 常量名刻意避开 accept 那段 grep 会命中的形态：它扫的是「同时含
     * {@code freezeTime} 的文件里有没有那个天数常量名」，而这个文件满是 {@code freezeTime}。
     */
    static final String PIN_DAYS_PARAM_KEY = "cryoOverduePinDays";

    private final CryoBatchMapper cryoBatchMapper;
    private final CryoFlowMapper cryoFlowMapper;
    private final SampleMapper sampleMapper;
    private final SampleNameResolver nameResolver;
    private final CryoOverdueService cryoOverdueService;

    /**
     * 列表（工作台 {@code GET /lqg/cryo/batch/list}）。
     *
     * <p>★ {@code internalNo} 是<b>所挂样本的</b>列：先按编号查样本 id 集合、再
     * {@code in(sample_id)}；空集合 → 直接回空页（别退化成「不过滤 = 全表」）。
     *
     * <p>★ 阈值在这里<b>读一次</b>（每个请求一次，不是每个 JVM 一次），随行装配、排序、
     * {@code overdueOnly} 三处使用 —— 甲方在工作台把 {@code lqg.cryo.overdue-days} 改完，
     * 下一次请求就是新口径（CR-20260918-07）。
     */
    public CryoBatchPageVo list(CryoQueryBo query) {
        CryoQueryBo q = query == null ? new CryoQueryBo() : query;
        int days = cryoOverdueService.days();
        return DataPermissionHelper.ignore(() -> {
            Page<CryoBatch> page = q.build();
            // ★ 关掉 count SQL 的「优化」：MyBatis-Plus 的 PaginationInnerInterceptor 只在
            //   ORDER BY 里**不含参数占位符**时才敢把 ORDER BY 从 count SQL 里摘掉
            //   （源码注释：「order by 里带参数,不去除 order by」）。本票默认排序的置顶键里
            //   带着绑定的阈值（`>= ?`），于是 count SQL 会留着
            //   `ORDER BY CASE WHEN … END, create_time DESC` —— PostgreSQL 对
            //   `SELECT COUNT(*) … ORDER BY 非分组列` 直接报
            //   「column … must appear in the GROUP BY clause or be used in an aggregate function」，
            //   整个列表 500（实测踩过）。关掉之后走 lowLevelCountSql：
            //   `SELECT COUNT(*) FROM (原 SQL) TOTAL` —— 子查询里带 ORDER BY 是合法的，
            //   阈值仍然是绑定参数、语义不变（只是多套一层，本表是分页小表）。
            page.setOptimizeCountSql(false);
            List<Long> sampleIds = null;
            if (StringUtils.isNotBlank(q.getInternalNo())) {
                sampleIds = sampleIdsOfInternalNo(q.getInternalNo());
                if (sampleIds.isEmpty()) {
                    return emptyPage(page);
                }
            }
            Page<CryoBatch> result = cryoBatchMapper.selectPage(page, buildWrapper(q, sampleIds, currentUserId(), days));
            List<CryoBatchVo> rows = assemble(result.getRecords(), days);
            CryoBatchPageVo out = new CryoBatchPageVo();
            out.setCode(HttpStatus.HTTP_OK);
            out.setMsg("查询成功");
            out.setRows(rows);
            out.setTotal(result.getTotal());
            // ★ 页签计数：overdue 那一格走的正是超期清单 / 首页计数同一个函数
            out.setTabCounts(tabCounts());
            return out;
        });
    }

    /**
     * 导出用的整批行（{@code POST /lqg/cryo/batch/export}，CRYO-WEB-001）。
     *
     * <p>★★ <b>与列表同一份 wrapper + 同一份装配</b>：拉掉分页，筛选 / 排序 / 每行的
     * {@code remainingQty} 口径逐条一致 —— 「带筛选导出只出筛选结果」靠它，
     * 而不是在导出侧再写一份 WHERE（另写一份就是两处口径打架）。
     *
     * <p>★ <b>行数 = 未删批次数，且所挂样本未删</b>（accept 1 最后一段：直连库
     * {@code JOIN t_lqg_sample s ON s.id = b.sample_id AND s.del_flag='0'} 的 count）：
     * 批次自身的软删由实体 {@code @TableLogic} 兜住；所挂样本软删的行由
     * {@link #missingSampleIds} 剔掉（{@code CryoBatchVo.internalNo} 为 null 就说明样本查不到）。
     *
     * <p>★ 导出**不**受 {@code mine} 收窄影响：那是小程序「只看我提交的」的开关，
     * 工作台导出按当前筛选走。
     */
    public List<CryoBatchVo> exportRows(CryoQueryBo query) {
        CryoQueryBo q = query == null ? new CryoQueryBo() : query;
        int days = cryoOverdueService.days();
        return DataPermissionHelper.ignore(() -> {
            List<Long> sampleIds = null;
            if (StringUtils.isNotBlank(q.getInternalNo())) {
                sampleIds = sampleIdsOfInternalNo(q.getInternalNo());
                if (sampleIds.isEmpty()) {
                    return List.of();
                }
            }
            List<CryoBatchVo> rows = assemble(cryoBatchMapper.selectList(
                buildWrapper(q, sampleIds, currentUserId(), days)), days);
            Set<Long> missing = missingSampleIds(rows);
            if (missing.isEmpty()) {
                return rows;
            }
            return rows.stream()
                .filter(row -> row.getSampleId() == null || !missing.contains(row.getSampleId()))
                .toList();
        });
    }

    /**
     * 这一批行里，哪些行的<b>所挂样本已经查不到</b>（软删或不存在）。
     *
     * <p>给 {@link #exportRows} 用：{@link #assemble} 对查不到样本的行只留 {@code sampleId}
     * （{@code internalNo} / {@code submitNo} 都是 {@code null}）。列表页照旧显示这些行，
     * 但导出按 accept 口径要剔掉 —— 「行数 = 未删批次数，<b>且所挂样本未删</b>」。
     */
    private Set<Long> missingSampleIds(Collection<CryoBatchVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        Set<Long> wanted = new LinkedHashSet<>();
        for (CryoBatchVo row : rows) {
            if (row.getSampleId() != null) {
                wanted.add(row.getSampleId());
            }
        }
        if (wanted.isEmpty()) {
            return Set.of();
        }
        List<Sample> found = sampleMapper.selectBatchIds(wanted);
        Set<Long> alive = new LinkedHashSet<>();
        if (found != null) {
            for (Sample sample : found) {
                alive.add(sample.getId());
            }
        }
        Set<Long> missing = new LinkedHashSet<>();
        for (Long id : wanted) {
            if (!alive.contains(id)) {
                missing.add(id);
            }
        }
        return missing;
    }

    /**
     * 单条详情；不存在 / 已软删 → {@code null}（调用方回「不存在」语义，不泄露存在性）。
     */
    public CryoBatchVo detail(Long id) {
        if (id == null) {
            return null;
        }
        int days = cryoOverdueService.days();
        return DataPermissionHelper.ignore(() -> {
            CryoBatch batch = cryoBatchMapper.selectById(id);
            if (batch == null) {
                return null;
            }
            List<CryoBatchVo> rows = assemble(List.of(batch), days);
            return rows.isEmpty() ? null : rows.get(0);
        });
    }

    /**
     * 单条<b>实体</b>（给「先读现状再判断」的写路径与小程序修改模式用）；不存在 / 已软删 → {@code null}。
     */
    public CryoBatch entity(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> cryoBatchMapper.selectById(id));
    }

    /**
     * 一个样本名下的全部未删批次（给 SAMPLE-WEB-001 的「冻存」入口 / AUTH-EXT 的详情用）。
     */
    public List<CryoBatchVo> listBySampleId(Long sampleId) {
        if (sampleId == null) {
            return List.of();
        }
        int days = cryoOverdueService.days();
        return DataPermissionHelper.ignore(() -> assemble(cryoBatchMapper.selectList(
            new LambdaQueryWrapper<CryoBatch>()
                .eq(CryoBatch::getSampleId, sampleId)
                .orderByDesc(CryoBatch::getCreateTime)
                .orderByDesc(CryoBatch::getId)), days));
    }

    /**
     * 某批次的<b>未删</b>流水，时间倒序（给 {@code GET /lqg/cryo/batch/{id}/flows} 与小程序只读用）。
     *
     * <p>★ {@code balanceAfter}（操作后剩余）与 {@code edited} 两个键是 CRYO-FLOW-001 的活：
     * 本方法只按 {@code flow_time, id} 给序列，正序累加在调用方一行 for 里做
     * （{@link CryoBalanceChecker#ordered(Collection)} 就是那个顺序）。
     */
    public List<CryoFlow> listFlows(Long batchId) {
        if (batchId == null) {
            return List.of();
        }
        return DataPermissionHelper.ignore(() -> cryoFlowMapper.selectList(
            new LambdaQueryWrapper<CryoFlow>()
                .eq(CryoFlow::getBatchId, batchId)
                .orderByDesc(CryoFlow::getFlowTime)
                .orderByDesc(CryoFlow::getId)));
    }

    // ── wrapper / 装配 ────────────────────────────────────────────────────────

    /**
     * 组装列表的 {@code WHERE} 链与 {@code ORDER BY}（<b>包内可见</b>：契约测试直接拿它生成的
     * SQL 断「{@code location} 那一组 OR 被括号包住」「{@code mine} 那一组 OR 被括号包住」）。
     *
     * <p>★ 所有 OR 一律走 {@code and(w -&gt; …)} 嵌套：MyBatis-Plus 只给 {@code and(consumer)}
     * 补括号，顶层裸 {@code .or()} 会把整条 AND 链拆成 {@code (A AND B) OR C}
     * （D2 的 S1 #105 就是这个，全仓已扫过一遍 —— 本类不引入同类形态）。
     *
     * @param q         查询入参
     * @param sampleIds 按 {@code internalNo} 查出来的样本 id 集合（{@code null} = 不带这个筛选）
     * @param me        当前登录人（{@code mine=true} 时才用得上；取不到 = {@code null}）
     * @param days      当前阈值天数（超期置顶排序键与 {@code overdueOnly} 都要它）
     */
    static LambdaQueryWrapper<CryoBatch> buildWrapper(CryoQueryBo q, List<Long> sampleIds, Long me, int days) {
        LambdaQueryWrapper<CryoBatch> wrapper = new LambdaQueryWrapper<>();
        applyFilters(wrapper, q, sampleIds, me, days);
        applyOrderBy(wrapper, q, days);
        return wrapper;
    }

    /**
     * 全部 {@code WHERE} 条件（<b>不含</b>排序），供数据查询与计数两条路复用。
     *
     * <p>★ {@code overdueOnly} 拼的是<b>唯一一份</b>超期判定 where 片段
     * （{@link CryoOverdueSqlProvider#WHERE}）：阈值当参数绑定（{@code {0}} → MyBatis-Plus 的
     * {@code paramNameValuePairs}），片段里没有硬编码天数。
     */
    static void applyFilters(LambdaQueryWrapper<CryoBatch> wrapper, CryoQueryBo q, List<Long> sampleIds,
                             Long me, int days) {
        wrapper.like(StringUtils.isNotBlank(q.getCryoName()), CryoBatch::getCryoName, trim(q.getCryoName()))
            .in(sampleIds != null, CryoBatch::getSampleId, sampleIds == null ? List.of() : sampleIds)
            .eq(q.getSampleId() != null, CryoBatch::getSampleId, q.getSampleId())
            .ge(q.getFreezeTimeBegin() != null, CryoBatch::getFreezeTime, q.getFreezeTimeBegin())
            .le(q.getFreezeTimeEnd() != null, CryoBatch::getFreezeTime, q.getFreezeTimeEnd());
        // ★ 位置筛选与行上的 location **同源判据**（CryoBalanceChecker.locationOf）：
        //   ln2 = 直接进液氮（in_minus80='N'）**或**已登记转液氮（to_ln2_time 非空）。
        //   只看 in_minus80 会把「先 -80 后转液氮」的批次（seed 的 3003）漏掉 —— accept 3 的
        //   counterfeit 点名这一形态。这一组 OR 包在 and(...) 里，与别的筛选相与。
        String location = trim(q.getLocation());
        if (CryoBalanceChecker.LOCATION_LN2.equalsIgnoreCase(location)) {
            wrapper.and(w -> w.eq(CryoBatch::getInMinus80, "N")
                .or().isNotNull(CryoBatch::getToLn2Time));
        } else if (CryoBalanceChecker.LOCATION_MINUS80.equalsIgnoreCase(location)) {
            wrapper.and(w -> w.eq(CryoBatch::getInMinus80, "Y")
                .isNull(CryoBatch::getToLn2Time));
        }
        // ★ 「只看我提交的」：一组 OR 包一层，与其它筛选相与（CR-20260918-07，给 CRYO-MP-001）
        if (Boolean.TRUE.equals(q.getMine()) && me != null) {
            wrapper.and(w -> w.eq(CryoBatch::getCreateBy, me).or().eq(CryoBatch::getUpdateBy, me));
        }
        // ★ 「只看超期」：同一段超期判定片段（CRYO-REMIND-001）。用 apply 而不是 last，
        //   阈值走 MP 的实参绑定，与行上的 overdue / 页签数字 / 超期清单同一个口径。
        if (Boolean.TRUE.equals(q.getOverdueOnly())) {
            wrapper.apply(CryoOverdueSqlProvider.whereFor("{0}"), days);
        }
        // ★ 「已取空」页签（2026-09-24 甲方「支数取空的要提示」）：剩余 ≤ 0。拼的是与超期第 ③ 条
        //   同一份剩余算式（CryoOverdueSqlProvider.EMPTIED_WHERE），与行上的 emptied、tabCounts.emptied 同源。
        if (Boolean.TRUE.equals(q.getEmptiedOnly())) {
            wrapper.apply(CryoOverdueSqlProvider.EMPTIED_WHERE);
        }
    }

    /**
     * 排序（表达式排序只能走 {@code last()}：MP 3.5.16 的 Func 接口没有「按列名 / 表达式」的重载）。
     *
     * <p>★ <b>默认排序把超期置顶</b>（ticket §2）：排序键是那一段超期判定片段的
     * {@code CASE WHEN}，阈值以 {@code #{ew.paramNameValuePairs.<key>}} 绑定
     * （<b>不写字面量</b>）；非超期行再按创建时间倒序。
     * ★ {@code sort=recent}（小程序历史编辑记录）<b>不置顶</b>：那一档要的是「最近改过的在上」。
     */
    static void applyOrderBy(LambdaQueryWrapper<CryoBatch> wrapper, CryoQueryBo q, int days) {
        if (CryoQueryBo.isRecentSort(q.getSort())) {
            wrapper.last("ORDER BY COALESCE(update_time, create_time) DESC, id DESC");
            return;
        }
        wrapper.getParamNameValuePairs().put(PIN_DAYS_PARAM_KEY, days);
        wrapper.last("ORDER BY CASE WHEN "
            + CryoOverdueSqlProvider.whereFor("#{ew.paramNameValuePairs." + PIN_DAYS_PARAM_KEY + "}")
            + " THEN 0 ELSE 1 END ASC, create_time DESC, id DESC");
    }

    /**
     * 页签计数 {@code {all, overdue, ln2, emptied}}（ticket §2 / 契约；{@code emptied} 是 2026-09-24 加的）。
     *
     * <p>★ {@code overdue} 那一格 = {@link CryoOverdueService#countOverdue()} —— 与超期清单、
     * 工作台首页待办卡片、菜单角标<b>同一个函数、同一段 where</b>。清单长度与它恒等
     * （单测「计数 = 清单长度」钉住）。
     *
     * <p>★ {@code ln2} 与行上的 {@code location} 同源：直接进液氮（{@code in_minus80='N'}）
     * <b>或</b>已登记转液氮（{@code to_ln2_time} 非空）—— 只看 {@code in_minus80} 会把
     * 「先 -80 后转液氮」的 3003 漏掉。
     *
     * <p>★ {@code emptied}（已取空，2026-09-24 甲方「支数取空的要提示」）= 剩余 ≤ 0 的批次数，
     * 拼 {@code CryoOverdueSqlProvider.EMPTIED_WHERE}（与 {@code emptiedOnly} 筛选、行上 {@code emptied} 同源）。
     * 它与 {@code ln2} 可以重叠（液氮里取空的批次两边都算）：页签是「这张表的几个视图」，不是互斥分类。
     *
     * <p>★ 四个数都是<b>整表口径</b>（未删行），不随列表筛选收窄；列表的 {@code total} 才是
     * 当前筛选下的行数。
     */
    Map<String, Long> tabCounts() {
        long all = cryoBatchMapper.selectCount(new LambdaQueryWrapper<CryoBatch>());
        long ln2 = cryoBatchMapper.selectCount(new LambdaQueryWrapper<CryoBatch>()
            .and(w -> w.eq(CryoBatch::getInMinus80, "N").or().isNotNull(CryoBatch::getToLn2Time)));
        long emptied = cryoBatchMapper.selectCount(new LambdaQueryWrapper<CryoBatch>()
            .apply(CryoOverdueSqlProvider.EMPTIED_WHERE));
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", all);
        counts.put("overdue", cryoOverdueService.countOverdue());
        counts.put("ln2", ln2);
        counts.put("emptied", emptied);
        return counts;
    }

    /**
     * 实体列表 → VO 列表（<b>两条批量查询</b>：剩余一条聚合、样本一条 IN；不逐行查）。
     *
     * @param days 当前阈值天数（行上的 {@code overdue / overdueDays} 由它算）
     */
    List<CryoBatchVo> assemble(List<CryoBatch> rows, int days) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> batchIds = new LinkedHashSet<>();
        Set<Long> sampleIds = new LinkedHashSet<>();
        for (CryoBatch row : rows) {
            if (row.getId() != null) {
                batchIds.add(row.getId());
            }
            if (row.getSampleId() != null) {
                sampleIds.add(row.getSampleId());
            }
        }
        // ★ 一条 GROUP BY 算完整页的剩余（没有未删流水的批次不在结果里 → 补 0）
        Map<Long, Integer> deltas = new HashMap<>();
        if (!batchIds.isEmpty()) {
            List<CryoFlowDeltaRow> deltaRows = cryoFlowMapper.selectDeltaSums(batchIds);
            if (deltaRows != null) {
                for (CryoFlowDeltaRow delta : deltaRows) {
                    if (delta != null && delta.getBatchId() != null) {
                        deltas.put(delta.getBatchId(), delta.getTotalDelta() == null ? 0 : delta.getTotalDelta());
                    }
                }
            }
        }
        Map<Long, Sample> samples = new HashMap<>();
        if (!sampleIds.isEmpty()) {
            List<Sample> found = sampleMapper.selectBatchIds(sampleIds);
            if (found != null) {
                for (Sample sample : found) {
                    samples.put(sample.getId(), sample);
                }
            }
        }
        Long me = currentUserId();
        LocalDate today = LocalDate.now();
        List<CryoBatchVo> out = new ArrayList<>(rows.size());
        for (CryoBatch row : rows) {
            out.add(toVo(row, samples.get(row.getSampleId()),
                deltas.getOrDefault(row.getId(), 0), me, today, days));
        }
        return out;
    }

    /**
     * 剩余支数（单个批次）：{@code init_qty + SUM(未删流水 delta)}。
     *
     * <p>给写侧与 CRYO-FLOW-001 复用（它们手上有流水集合时用
     * {@link CryoBalanceChecker#remaining(int, Collection)}，不必回库）。
     */
    public int remainingOf(CryoBatch batch) {
        if (batch == null || batch.getId() == null) {
            return 0;
        }
        List<CryoFlowDeltaRow> rows = DataPermissionHelper.ignore(
            () -> cryoFlowMapper.selectDeltaSums(List.of(batch.getId())));
        int delta = 0;
        if (rows != null && !rows.isEmpty()) {
            delta = rows.get(0).getTotalDelta() == null ? 0 : rows.get(0).getTotalDelta();
        }
        int initQty = batch.getInitQty() == null ? 0 : batch.getInitQty();
        return initQty + delta;
    }

    private CryoBatchVo toVo(CryoBatch batch, Sample sample, int deltaSum, Long me,
                             LocalDate today, int days) {
        CryoBatchVo vo = new CryoBatchVo();
        vo.setId(batch.getId());
        vo.setSampleId(batch.getSampleId());
        vo.setCryoName(batch.getCryoName());
        vo.setPassage(batch.getPassage());
        vo.setFreezeTime(batch.getFreezeTime());
        vo.setInitQty(batch.getInitQty());
        vo.setDensity(batch.getDensity());
        vo.setInMinus80(batch.getInMinus80());
        vo.setFrozenBy(batch.getFrozenBy());
        vo.setToLn2Time(batch.getToLn2Time());
        vo.setLn2Location(batch.getLn2Location());
        vo.setRemark(batch.getRemark());
        // ★ 读时算的四格：剩余、位置、是否超期、已超天数（一个都不落库）
        int initQty = batch.getInitQty() == null ? 0 : batch.getInitQty();
        int remaining = initQty + deltaSum;
        vo.setRemainingQty(remaining);
        vo.setLocation(CryoBalanceChecker.locationOf(batch.getInMinus80(), batch.getToLn2Time()));
        // ★ 超期判定只有一处（CryoOverdueService.isOverdue）——与超期清单、页签计数同源
        vo.setOverdue(CryoOverdueService.isOverdue(batch, remaining, today, days));
        vo.setOverdueDays(CryoOverdueService.overdueDaysOf(batch, remaining, today, days));
        // ★ 已取空：与 emptiedOnly 筛选 / tabCounts.emptied 同一判据（剩余 ≤ 0）；取空的永不超期（上一行第 ③ 条）
        vo.setEmptied(CryoBalanceChecker.isEmptied(remaining));
        // 冻存到今天几天（小程序批次详情「-80℃ 暂存 · 冻存 N 天」）：与 overdueDays 同一个 today
        vo.setFrozenDays(frozenDaysOf(batch.getFreezeTime(), today));
        if (sample != null) {
            // ★ 读时从样本主档带出（本表只有 sample_id）
            vo.setInternalNo(sample.getInternalNo());
            vo.setSubmitNo(sample.getSubmitNo());
            vo.setSourceUnitName(sample.getSourceUnitName());
            vo.setSampleVerifyStatus(sample.getVerifyStatus());
            vo.setSampleKind(sample.getSampleKind());
        }
        vo.setCreateBy(batch.getCreateBy());
        vo.setUpdateBy(batch.getUpdateBy());
        vo.setCreateTime(batch.getCreateTime());
        vo.setUpdateTime(batch.getUpdateTime());
        // 经手人 = 最后修改人，没改过就是创建人（与 SAMPLE / EMBED 域同一条口径，CR-20260918-07）
        Long handlerId = batch.getUpdateBy() != null ? batch.getUpdateBy() : batch.getCreateBy();
        String handlerName = nameResolver.nameOf(handlerId);
        vo.setHandlerName(handlerName);
        vo.setUpdateByName(handlerName);
        vo.setMine(isHandledBy(batch, me));
        return vo;
    }

    /**
     * 冻存到今天的自然天数（{@code today − freezeTime}）；冻存时间为空 → {@code null}。
     */
    static Integer frozenDaysOf(LocalDate freezeTime, LocalDate today) {
        if (freezeTime == null || today == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(freezeTime, today);
    }

    /**
     * 这一行是不是这个用户经手的：{@code create_by = 我 OR update_by = 我}
     * —— 与 {@code mine=true} 的收窄口径同一个判据（CR-20260918-07）。
     */
    static boolean isHandledBy(CryoBatch batch, Long userId) {
        if (batch == null || userId == null) {
            return false;
        }
        return userId.equals(batch.getCreateBy()) || userId.equals(batch.getUpdateBy());
    }

    /**
     * 按内部编号找样本 id（软删的样本查不到；查不到 → 空集合，调用方回空页）。
     */
    private List<Long> sampleIdsOfInternalNo(String internalNo) {
        return sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                .select(Sample::getId)
                .eq(Sample::getInternalNo, internalNo.trim()))
            .stream().map(Sample::getId).filter(Objects::nonNull).toList();
    }

    /**
     * 空页（{@code internalNo} 命中的样本 id 集合为空时）：{@code rows} 空、{@code total} 0，
     * <b>页签计数照给</b>（它是整表口径，与本次筛选无关）。
     */
    private CryoBatchPageVo emptyPage(Page<CryoBatch> page) {
        CryoBatchPageVo out = new CryoBatchPageVo();
        out.setCode(HttpStatus.HTTP_OK);
        out.setMsg("查询成功");
        out.setRows(List.of());
        out.setTotal(0L);
        out.setTabCounts(tabCounts());
        return out;
    }

    private static Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

}
