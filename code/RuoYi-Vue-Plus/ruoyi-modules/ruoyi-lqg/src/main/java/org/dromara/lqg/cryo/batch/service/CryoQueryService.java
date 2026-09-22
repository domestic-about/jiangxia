package org.dromara.lqg.cryo.batch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowDeltaRow;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleNameResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * <p>★ <b>排序两档</b>：不带 {@code sort} = 按创建时间倒序（工作台那一档，超期置顶由
 * CRYO-REMIND-001 补）；带 {@code sort=recent} = 按 {@code COALESCE(update_time, create_time)}
 * 倒序（CRYO-MP-001 的历史编辑记录，CR-20260918-07）。
 *
 * @author CRYO-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoQueryService {

    private final CryoBatchMapper cryoBatchMapper;
    private final CryoFlowMapper cryoFlowMapper;
    private final SampleMapper sampleMapper;
    private final SampleNameResolver nameResolver;

    /**
     * 列表（工作台 {@code GET /lqg/cryo/batch/list}）。
     *
     * <p>★ {@code internalNo} 是<b>所挂样本的</b>列：先按编号查样本 id 集合、再
     * {@code in(sample_id)}；空集合 → 直接回空页（别退化成「不过滤 = 全表」）。
     */
    public TableDataInfo<CryoBatchVo> list(CryoQueryBo query) {
        CryoQueryBo q = query == null ? new CryoQueryBo() : query;
        return DataPermissionHelper.ignore(() -> {
            Page<CryoBatch> page = q.build();
            List<Long> sampleIds = null;
            if (StringUtils.isNotBlank(q.getInternalNo())) {
                sampleIds = sampleIdsOfInternalNo(q.getInternalNo());
                if (sampleIds.isEmpty()) {
                    return emptyPage(page);
                }
            }
            Page<CryoBatch> result = cryoBatchMapper.selectPage(page, buildWrapper(q, sampleIds, currentUserId()));
            List<CryoBatchVo> rows = assemble(result.getRecords());
            return TableDataInfo.build(new Page<CryoBatchVo>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(rows));
        });
    }

    /**
     * 单条详情；不存在 / 已软删 → {@code null}（调用方回「不存在」语义，不泄露存在性）。
     */
    public CryoBatchVo detail(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            CryoBatch batch = cryoBatchMapper.selectById(id);
            if (batch == null) {
                return null;
            }
            List<CryoBatchVo> rows = assemble(List.of(batch));
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
        return DataPermissionHelper.ignore(() -> assemble(cryoBatchMapper.selectList(
            new LambdaQueryWrapper<CryoBatch>()
                .eq(CryoBatch::getSampleId, sampleId)
                .orderByDesc(CryoBatch::getCreateTime)
                .orderByDesc(CryoBatch::getId))));
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
     */
    static LambdaQueryWrapper<CryoBatch> buildWrapper(CryoQueryBo q, List<Long> sampleIds, Long me) {
        LambdaQueryWrapper<CryoBatch> wrapper = new LambdaQueryWrapper<CryoBatch>()
            .like(StringUtils.isNotBlank(q.getCryoName()), CryoBatch::getCryoName, trim(q.getCryoName()))
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
        // 表达式排序只能走 last()：MP 3.5.16 的 Func 接口没有「按列名 / 表达式」的重载
        wrapper.last(CryoQueryBo.isRecentSort(q.getSort())
            ? "ORDER BY COALESCE(update_time, create_time) DESC, id DESC"
            : "ORDER BY create_time DESC, id DESC");
        return wrapper;
    }

    /**
     * 实体列表 → VO 列表（<b>两条批量查询</b>：剩余一条聚合、样本一条 IN；不逐行查）。
     */
    List<CryoBatchVo> assemble(List<CryoBatch> rows) {
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
        List<CryoBatchVo> out = new ArrayList<>(rows.size());
        for (CryoBatch row : rows) {
            out.add(toVo(row, samples.get(row.getSampleId()),
                deltas.getOrDefault(row.getId(), 0), me));
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

    private CryoBatchVo toVo(CryoBatch batch, Sample sample, int deltaSum, Long me) {
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
        // ★ 读时算的两格：剩余与位置（都不落库）
        int initQty = batch.getInitQty() == null ? 0 : batch.getInitQty();
        vo.setRemainingQty(initQty + deltaSum);
        vo.setLocation(CryoBalanceChecker.locationOf(batch.getInMinus80(), batch.getToLn2Time()));
        if (sample != null) {
            // ★ 读时从样本主档带出（本表只有 sample_id）
            vo.setInternalNo(sample.getInternalNo());
            vo.setSubmitNo(sample.getSubmitNo());
            vo.setSourceUnitName(sample.getSourceUnitName());
            vo.setSampleVerifyStatus(sample.getVerifyStatus());
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

    private static TableDataInfo<CryoBatchVo> emptyPage(Page<CryoBatch> page) {
        return TableDataInfo.build(new Page<CryoBatchVo>(page.getCurrent(), page.getSize(), 0L).setRecords(List.of()));
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
