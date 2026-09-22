package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.EmbedMarker;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.domain.vo.EmbedMarkerVo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.dromara.lqg.embed.guard.StainRules;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.embed.mapper.EmbedMarkerMapper;
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
 * 石蜡包埋读侧（doc/api-contract.md 的 {@code GET /lqg/embed/list}、{@code GET /lqg/embed/{id}}）。
 *
 * <p>★ <b>三个「读时带出、不落库」的键</b>（accept 2 / 3 逐条断）：
 * {@code internalNo}（= 所挂样本的内部编号，模板的「样本编号」）、{@code submitNo}
 * （= 所挂样本的送检单号）、{@code sampleVerifyStatus}（= 所挂样本的核验状态，
 * 工作台核验抽屉据此置灰「判为有效」）。三者都由本类从样本主档<b>批量</b>取，本表没有这些列。
 *
 * <p>★ <b>软删不出现</b>：软删由实体上的 {@code @TableLogic} 兜住，本类不写任何原生 SQL、
 * 不手写 join —— seed 里软删的 {@code T-E05-X}（2005）因此永远不会出现（accept 3 第 2 段）。
 *
 * <p>★★ <b>按染色筛选绝不用 {@code LIKE '%HE%'}</b>（accept 3 最后一段）：
 * {@code stain_types} 是逗号串，{@code %HE%} 会把 {@code OTHER} 串出来（O-T-<b>HE</b>-R）。
 * 本实现是「两侧补逗号再整体 LIKE」= 数组包含语义（见 {@link #buildWrapper}）。
 *
 * <p>★ <b>排序</b>：不带 {@code sort} = 待核验置顶（{@code ORDER BY (verify_status = 'pending') DESC,
 * create_time DESC, id DESC}，工作台那一档）；带 {@code sort=recent} = 按
 * {@code COALESCE(update_time, create_time)} 倒序（EMBED-MP-001 的历史编辑记录，CR-20260918-07）。
 *
 * @author EMBED-MODEL-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedQueryService {

    private final EmbedMapper embedMapper;
    private final EmbedMarkerMapper embedMarkerMapper;
    private final SampleMapper sampleMapper;
    private final SampleNameResolver nameResolver;

    /**
     * 列表（工作台 {@code GET /lqg/embed/list}）。
     *
     * <p>★ {@code internalNo} 是<b>所挂样本的</b>列：先按编号查样本 id 集合、再 {@code in(sample_id)}；
     * 空集合 → 直接回空页（别退化成「不过滤 = 全表」）。
     */
    public TableDataInfo<EmbedVo> list(EmbedQueryBo query) {
        EmbedQueryBo q = query == null ? new EmbedQueryBo() : query;
        return DataPermissionHelper.ignore(() -> {
            Page<Embed> page = q.build();
            List<Long> sampleIds = null;
            if (StringUtils.isNotBlank(q.getInternalNo())) {
                sampleIds = sampleIdsOfInternalNo(q.getInternalNo());
                if (sampleIds.isEmpty()) {
                    return emptyPage(page);
                }
            }
            LambdaQueryWrapper<Embed> wrapper = buildWrapper(q, sampleIds, keywordSampleIds(q), currentUserId());
            Page<Embed> result = embedMapper.selectPage(page, wrapper);
            List<EmbedVo> rows = assemble(result.getRecords());
            return TableDataInfo.build(new Page<EmbedVo>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(rows));
        });
    }

    /**
     * 按<b>调用方算好的</b> wrapper 分页查并装配 —— 给外部接口（AUTH-EXT-002）用。
     *
     * <p>★ 为什么外部接口不自己注入 {@code EmbedMapper}：ADR-0004 的不变量 I4 规定 ext 包里除
     * {@code ExtScopeServiceImpl} 外任何类都不得持有 {@code *Mapper} 字段
     * （{@code ExtChokepointContractTest} 扫整个 ext 包）。所以「按可见样本 id 集合查一页」
     * 这个读操作放在 embed 包，ext 包只把算好的 wrapper 传进来拼装
     * （与 AUTH-EXT-001 的 {@code SampleQueryService.selectExtPage} 同款）。
     *
     * <p>调用方负责把可见性条件写进 wrapper；本方法只执行查询与装配，不额外加过滤。
     */
    public TableDataInfo<EmbedVo> pageByWrapper(Page<Embed> page, LambdaQueryWrapper<Embed> wrapper) {
        return DataPermissionHelper.ignore(() -> {
            Page<Embed> result = embedMapper.selectPage(page, wrapper);
            List<EmbedVo> rows = assemble(result.getRecords());
            return TableDataInfo.build(new Page<EmbedVo>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(rows));
        });
    }

    /**
     * 单条详情；不存在 / 已软删 → {@code null}（调用方回「不存在」语义，不泄露存在性）。
     */
    public EmbedVo detail(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            Embed embed = embedMapper.selectById(id);
            if (embed == null) {
                return null;
            }
            List<EmbedVo> rows = assemble(List.of(embed));
            return rows.isEmpty() ? null : rows.get(0);
        });
    }

    /**
     * 一个样本名下的全部未删记录（AUTH-EXT-002 的样本详情 {@code embeds} 用）。
     *
     * <p>排序：<b>有编号的按创建时间在前、没编号的（待核验 / 无效送样）在后</b> ——
     * 契约要的「外部看得到自己送的样走到哪一步」。
     */
    public List<EmbedVo> listBySampleId(Long sampleId) {
        if (sampleId == null) {
            return List.of();
        }
        return listBySampleIds(List.of(sampleId));
    }

    /**
     * 多个样本名下的全部未删记录（AUTH-EXT-002 的列表按可见样本 id 集合取数用）。
     */
    public List<EmbedVo> listBySampleIds(Collection<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            return List.of();
        }
        return DataPermissionHelper.ignore(() -> {
            LambdaQueryWrapper<Embed> wrapper = new LambdaQueryWrapper<Embed>()
                .in(Embed::getSampleId, sampleIds)
                .last("ORDER BY (paraffin_block_no IS NULL) ASC, create_time ASC, id ASC");
            return assemble(embedMapper.selectList(wrapper));
        });
    }

    /**
     * 单条<b>实体</b>（给「先读现状再判断」的写路径与外部可见性断言用）；不存在 / 已软删 → {@code null}。
     */
    public Embed entity(Long id) {
        if (id == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> embedMapper.selectById(id));
    }

    /**
     * <b>导出用的整表（不分页）</b>：与 {@link #list(EmbedQueryBo)} 同一份 wrapper、
     * 同一份装配 —— {@code POST /lqg/embed/export} 与 {@code GET /lqg/embed/list} 口径逐条一致
     * （accept 1 的「带筛选导出只出筛选结果」与「行数与列表 total 一致」两段钉的就是这个）。
     *
     * <p>★ 排序与筛选用<b>同一个</b> {@link #buildWrapper}：直接拿实体 VO 导出、
     * 或者另写一份「导出专用 SQL」，正是 accept 1 counterfeit 点名要抓的形态。
     *
     * <p>★ <b>软删不出现、所挂样本软删的也不出现</b>：实体 {@code @TableLogic} 兜住前者；
     * 后者由装配时 {@code sampleMapper.selectBatchIds} 查不到样本 → 该行 {@code internalNo} 为
     * {@code null} 暴露出来，导出侧按「样本已删 → 不导」跳过（与样本导出口径一致）。
     */
    public List<EmbedVo> exportRows(EmbedQueryBo query) {
        EmbedQueryBo q = query == null ? new EmbedQueryBo() : query;
        return DataPermissionHelper.ignore(() -> {
            List<Long> sampleIds = null;
            if (StringUtils.isNotBlank(q.getInternalNo())) {
                sampleIds = sampleIdsOfInternalNo(q.getInternalNo());
                if (sampleIds.isEmpty()) {
                    return List.of();
                }
            }
            List<EmbedVo> rows = assemble(embedMapper
                .selectList(buildWrapper(q, sampleIds, keywordSampleIds(q), currentUserId())));
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
     * 但导出按 ticket 口径要剔掉 —— 「行数 = 未删的石蜡包埋送样记录数，<b>且所挂样本未删</b>」。
     */
    private Set<Long> missingSampleIds(Collection<EmbedVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        Set<Long> wanted = new LinkedHashSet<>();
        for (EmbedVo row : rows) {
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

    // ── wrapper / 装配 ────────────────────────────────────────────────────────

    /**
     * 组装列表的 {@code WHERE} 链与 {@code ORDER BY}（<b>包内可见</b>：契约测试直接拿它生成的
     * SQL 断「染色筛选不是 {@code LIKE '%HE%'}」与「{@code mine} 那一组 OR 被括号包住」）。
     *
     * <p>★ 所有 OR 一律走 {@code and(w -&gt; …)} 嵌套：MyBatis-Plus 只给 {@code and(consumer)}
     * 补括号，顶层裸 {@code .or()} 会把整条 AND 链拆成 {@code (A AND B) OR C}
     * （D2 的 S1 #105 就是这个，全仓已扫过一遍 —— 本类不引入同类形态）。
     *
     * <p>本重载保留给「只要原有筛选」的调用方（工作台导出 / 既有契约测试）：
     * 等价于不带 {@code keyword} 的那一条路。
     */
    static LambdaQueryWrapper<Embed> buildWrapper(EmbedQueryBo q, List<Long> sampleIds, Long me) {
        return buildWrapper(q, sampleIds, null, me);
    }

    /**
     * 组装列表的 {@code WHERE} 链与 {@code ORDER BY}。
     *
     * @param q                 查询入参
     * @param sampleIds         按 {@code internalNo} 查出来的样本 id 集合（{@code null} = 不带这个筛选）
     * @param keywordSampleIds  按 {@code keyword} 命中的样本内部编号查出来的样本 id 集合
     *                          （{@code null} / 空 = 这个搜索只可能命中石蜡块编号）
     * @param me                当前登录人（{@code mine=true} 时才用得上；取不到 = {@code null}）
     */
    static LambdaQueryWrapper<Embed> buildWrapper(EmbedQueryBo q, List<Long> sampleIds,
                                                 List<Long> keywordSampleIds, Long me) {
        LambdaQueryWrapper<Embed> wrapper = new LambdaQueryWrapper<Embed>()
            .like(StringUtils.isNotBlank(q.getParaffinBlockNo()), Embed::getParaffinBlockNo,
                trim(q.getParaffinBlockNo()))
            .in(sampleIds != null, Embed::getSampleId, sampleIds == null ? List.of() : sampleIds)
            .eq(q.getSampleId() != null, Embed::getSampleId, q.getSampleId())
            .ge(q.getSectionTimeBegin() != null, Embed::getSectionTime, q.getSectionTimeBegin())
            .le(q.getSectionTimeEnd() != null, Embed::getSectionTime, q.getSectionTimeEnd())
            .eq(StringUtils.isNotBlank(q.getVerifyStatus()), Embed::getVerifyStatus, trim(q.getVerifyStatus()))
            // 提交来源钉在已落库的列上（提交当时的快照），不按提交人当前角色现算
            .eq(StringUtils.isNotBlank(q.getSubmitSource()), Embed::getSubmitSource, trim(q.getSubmitSource()));
        // ★ 搜索框（UI:mp.embed.list：石蜡块编号 / 内部编号）：两个判据**合成一组 OR**，
        //   与别的筛选相与。所挂样本的内部编号是等值（这一列不是加密列，等值才能让
        //   「T-hli01」一次命中；石蜡块编号按模糊，用户常常只记得前几段）。
        String keyword = trim(q.getKeyword());
        if (StringUtils.isNotBlank(keyword)) {
            List<Long> hitSamples = keywordSampleIds == null ? List.of() : keywordSampleIds;
            if (hitSamples.isEmpty()) {
                wrapper.like(Embed::getParaffinBlockNo, keyword);
            } else {
                wrapper.and(w -> w.like(Embed::getParaffinBlockNo, keyword)
                    .or().in(Embed::getSampleId, hitSamples));
            }
        }
        // ★ 染色：数组包含，不是 LIKE '%HE%'。
        //   两侧补逗号、外层再加通配 —— 整串 = '%,HE,%'，精确等价于「逗号串里有一个元素 == HE」：
        //   HE,IHC 命中；OTHER（O-T-HE-R）不命中；IF 不命中。参数走 {0} 占位，无字符串插值。
        if (StringUtils.isNotBlank(q.getStain())) {
            String stain = q.getStain().trim();
            wrapper.apply("(',' || stain_types || ',') LIKE {0}", "%," + stain + ",%");
        }
        if (EmbedQueryBo.isRecentSort(q.getSort())) {
            if (Boolean.TRUE.equals(q.getMine()) && me != null) {
                // 一组 OR 包一层；与其它筛选相与（不是并列）
                wrapper.and(w -> w.eq(Embed::getCreateBy, me).or().eq(Embed::getUpdateBy, me));
            }
            // 表达式排序只能走 last()：MP 3.5.16 的 Func 接口没有「按列名 / 表达式」的重载
            wrapper.last("ORDER BY COALESCE(update_time, create_time) DESC, id DESC");
        } else {
            // 待核验置顶：布尔表达式 true 在前；其余按创建时间倒序（accept 3 第 2 段 rows[0] == 2006）
            wrapper.last("ORDER BY (verify_status = 'pending') DESC, create_time DESC, id DESC");
        }
        return wrapper;
    }

    /**
     * 实体列表 → VO 列表（批量取样本与 marker，不是每行一次查询）。
     */
    List<EmbedVo> assemble(List<Embed> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> sampleIds = new LinkedHashSet<>();
        Set<Long> embedIds = new LinkedHashSet<>();
        for (Embed row : rows) {
            if (row.getSampleId() != null) {
                sampleIds.add(row.getSampleId());
            }
            if (row.getId() != null) {
                embedIds.add(row.getId());
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
        Map<Long, List<EmbedMarkerVo>> markers = new HashMap<>();
        if (!embedIds.isEmpty()) {
            List<EmbedMarker> found = embedMarkerMapper.selectList(new LambdaQueryWrapper<EmbedMarker>()
                .in(EmbedMarker::getEmbedId, embedIds)
                .orderByAsc(EmbedMarker::getSort)
                .orderByAsc(EmbedMarker::getId));
            if (found != null) {
                for (EmbedMarker marker : found) {
                    EmbedMarkerVo vo = new EmbedMarkerVo();
                    vo.setMarkerName(marker.getMarkerName());
                    vo.setExpression(marker.getExpression());
                    vo.setSort(marker.getSort());
                    markers.computeIfAbsent(marker.getEmbedId(), key -> new ArrayList<>()).add(vo);
                }
            }
        }
        Long me = currentUserId();
        List<EmbedVo> out = new ArrayList<>(rows.size());
        for (Embed row : rows) {
            out.add(toVo(row, samples.get(row.getSampleId()),
                markers.getOrDefault(row.getId(), List.of()), me));
        }
        return out;
    }

    private EmbedVo toVo(Embed embed, Sample sample, List<EmbedMarkerVo> markers, Long me) {
        EmbedVo vo = new EmbedVo();
        vo.setId(embed.getId());
        vo.setSampleId(embed.getSampleId());
        if (sample != null) {
            // ★ 模板的「样本编号」= 内部编号，读时带出、不落库
            vo.setInternalNo(sample.getInternalNo());
            vo.setSubmitNo(sample.getSubmitNo());
            vo.setSampleVerifyStatus(sample.getVerifyStatus());
            vo.setSourceUnitName(sample.getSourceUnitName());
        }
        vo.setParaffinBlockNo(embed.getParaffinBlockNo());
        vo.setSampleType(embed.getSampleType());
        vo.setOrganoidSourceType(embed.getOrganoidSourceType());
        vo.setTissueReceiveTime(embed.getTissueReceiveTime());
        vo.setTissueProcessTime(embed.getTissueProcessTime());
        vo.setAgaroseEmbedTime(embed.getAgaroseEmbedTime());
        vo.setEmbedBy(embed.getEmbedBy());
        vo.setDehydrateTime(embed.getDehydrateTime());
        vo.setAgaroseSendTime(embed.getAgaroseSendTime());
        vo.setParaffinEmbedTime(embed.getParaffinEmbedTime());
        vo.setSectionTime(embed.getSectionTime());
        vo.setSectioned(embed.getSectionTime() != null);
        // ★ 对外是数组（库里是逗号串），顺序 = 固定顺序
        vo.setStainTypes(StainRules.fromCsv(embed.getStainTypes()));
        vo.setStainOther(embed.getStainOther());
        vo.setMarkers(markers == null ? List.of() : markers);
        vo.setOperatorName(embed.getOperatorName());
        vo.setRemark(embed.getRemark());
        vo.setSubmitSource(embed.getSubmitSource());
        vo.setSubmitterId(embed.getSubmitterId());
        vo.setVerifyStatus(embed.getVerifyStatus());
        vo.setVerifyBy(embed.getVerifyBy());
        vo.setVerifyTime(embed.getVerifyTime());
        vo.setInvalidReason(embed.getInvalidReason());
        vo.setCreateTime(embed.getCreateTime());
        vo.setUpdateTime(embed.getUpdateTime());
        // 经手人 = 最后修改人，没改过就是创建人（与 SAMPLE 域同一条口径，CR-20260918-07）
        Long handlerId = embed.getUpdateBy() != null ? embed.getUpdateBy() : embed.getCreateBy();
        String handlerName = nameResolver.nameOf(handlerId);
        vo.setHandlerName(handlerName);
        vo.setUpdateByName(handlerName);
        vo.setMine(isHandledBy(embed, me));
        // 普通保存能不能改：只有 valid 能改（待核验 / 无效的只走核验接口）
        vo.setEditable(embed.getVerifyStatus() != null && "valid".equals(embed.getVerifyStatus()));
        return vo;
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
     * 搜索框（{@code keyword}）里「所挂样本内部编号」那一半命中的样本 id 集合。
     *
     * <p>★ 与 {@code internalNo} 筛选同一个查法（等值），但语义不同：{@code internalNo} 是
     * <b>只按内部编号</b>筛（命中不了就回空页），{@code keyword} 是<b>或</b>关系 ——
     * 这一个半边落空时还有「石蜡块编号模糊」那一半，所以调用方拿到空集合时
     * <b>不能</b>直接回空页（{@link #buildWrapper} 会退化成只按石蜡块编号 LIKE）。
     */
    private List<Long> keywordSampleIds(EmbedQueryBo q) {
        if (q == null || StringUtils.isBlank(q.getKeyword())) {
            return null;
        }
        return sampleIdsOfInternalNo(q.getKeyword());
    }

    private static TableDataInfo<EmbedVo> emptyPage(Page<Embed> page) {
        return TableDataInfo.build(new Page<EmbedVo>(page.getCurrent(), page.getSize(), 0L).setRecords(List.of()));
    }

    /**
     * 这一行是不是这个用户经手的：{@code create_by = 我 OR update_by = 我}
     * —— 与 {@code mine=true} 的收窄口径同一个判据（CR-20260918-07）。
     */
    static boolean isHandledBy(Embed embed, Long userId) {
        if (embed == null || userId == null) {
            return false;
        }
        return userId.equals(embed.getCreateBy()) || userId.equals(embed.getUpdateBy());
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
