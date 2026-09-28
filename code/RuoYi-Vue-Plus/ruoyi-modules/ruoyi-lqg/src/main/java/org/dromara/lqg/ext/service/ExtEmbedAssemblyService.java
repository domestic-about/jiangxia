package org.dromara.lqg.ext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.vo.EmbedMarkerVo;
import org.dromara.lqg.embed.domain.vo.EmbedVo;
import org.dromara.lqg.embed.service.EmbedQueryService;
import org.dromara.lqg.ext.domain.bo.ExtEmbedQueryBo;
import org.dromara.lqg.ext.domain.vo.ExtEmbedMarkerVo;
import org.dromara.lqg.ext.domain.vo.ExtEmbedVo;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外部石蜡包埋记录的<b>拼装</b>（{@code GET /mp/ext/embed/list}、{@code GET /mp/ext/embed/{id}}，
 * 以及样本详情里的 {@code embeds}）。
 *
 * <p>★ 本类<b>不持有任何 {@code *Mapper}</b>（ADR-0004 的 I4，
 * {@code ExtChokepointContractTest#i4_onlyExtScopeServiceImplTouchesMappers} 扫整个 ext 包）：
 * 读库分两处 —— 可见集合经 {@link ExtScopeService}（ext 包里唯一能碰 mapper 的实现类），
 * 石蜡包埋行经 {@code org.dromara.lqg.embed.service.EmbedQueryService}（embed 包）。
 * 本类只做「把内部行装成对外 VO」这一件事。
 *
 * <p>★ <b>三条拼装口径</b>，逐条对着 accept 段 1 抄：
 * <ol>
 *   <li><b>{@code mine} / {@code editable} 按这条记录自己的提交人算</b>（不是所挂样本的提交人）：
 *       {@code mine} = {@code embed.submitterId == 我}，
 *       {@code editable} = {@code mine && verifyStatus ∈ {pending, invalid}}
 *       —— 与 {@code onlyMine} 的收窄口径同一处判据，不会出现「列表筛掉了、行上却标我可改」。</li>
 *   <li><b>{@code sectioned}</b> 由 {@code sectionTime != null} 推（不落库）；
 *       <b>没编号的送样也要列出来</b>（{@code paraffinBlockNo} 为 null、带状态与无效原因）——
 *       外部要能看到自己送的样走到哪一步，这正是 {@code FLOW:F-EMBED-01.step4} 的 produces。</li>
 *   <li><b>提交人姓名批量取</b>（{@code ExtScopeService.submitterNames}，一次 IN），
 *       逐行查会随页大小线性放大；实验室录入的送样没有外部档案 → 姓名 null。</li>
 * </ol>
 *
 * @author AUTH-EXT-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtEmbedAssemblyService {

    private final ExtScopeService extScopeService;
    private final EmbedQueryService embedQueryService;

    /**
     * 外部石蜡包埋列表：可见样本集合 → 按 {@code verifyStatus / onlyMine} 收窄 →
     * 按 {@code COALESCE(update_time, create_time)} 倒序装配。
     *
     * <p>★ 可见集合<b>先</b>算：没有任何筛选能把范围放大。空集合直接回空页
     * （既不查库，也避开 {@code in ()} 那种空集合 SQL）。
     */
    public TableDataInfo<ExtEmbedVo> list(Long userId, ExtEmbedQueryBo query) {
        ExtEmbedQueryBo q = query == null ? new ExtEmbedQueryBo() : query;
        Set<Long> visible = extScopeService.visibleSampleIds(userId);
        if (visible.isEmpty()) {
            return TableDataInfo.build(new ArrayList<>());
        }
        String status = trimToNull(q.getVerifyStatus());
        boolean onlyMine = Boolean.TRUE.equals(q.getOnlyMine());

        Page<Embed> page = q.build();
        LambdaQueryWrapper<Embed> wrapper = new LambdaQueryWrapper<Embed>()
            .in(Embed::getSampleId, visible)
            .eq(status != null, Embed::getVerifyStatus, status)
            // ★ 「只看我提交的」按本条记录的提交人收窄（不是所挂样本的提交人）：
            //   extB 带 onlyMine 不该多出实验室在他样本上建的 2003（accept 段 1）
            .eq(onlyMine && userId != null, Embed::getSubmitterId, userId)
            // 表达式排序只能走 last()：MP 3.5.16 的 Func 接口没有按列名 / 表达式的重载
            // （AUTH-EXT-001 §9 坑 1）。分页插件的 LIMIT 加在这段 ORDER BY 之后，不冲突。
            .last("ORDER BY COALESCE(update_time, create_time) DESC, id DESC");

        TableDataInfo<EmbedVo> pageData = embedQueryService.pageByWrapper(page, wrapper);
        TableDataInfo<ExtEmbedVo> out = TableDataInfo.build(convert(pageData.getRows(), userId));
        out.setTotal(pageData.getTotal());
        return out;
    }

    /**
     * 单条石蜡包埋记录。
     *
     * <p>★ <b>先过 {@link ExtScopeService#assertEmbedVisible}</b>（FIX V17 / issue #299）：
     * 记录不存在、已软删、存在但所挂样本不可见 —— 三种情况<b>同一个</b>业务码 404、<b>同一句</b>
     * {@link ExtScopeService#EMBED_NOT_FOUND}（以前「不存在」回「石蜡包埋记录不存在」、「不可见」回
     * 「样本不存在」，提示不同本身就泄露了存在性）。与写口 {@code PUT /mp/ext/embed/{id}} 同一个判据。
     */
    public ExtEmbedVo detail(Long userId, Long embedId) {
        extScopeService.assertEmbedVisible(userId, embedId);
        EmbedVo src = embedQueryService.detail(embedId);
        if (src == null) {
            return null;
        }
        List<ExtEmbedVo> one = convert(List.of(src), userId);
        return one.isEmpty() ? null : one.get(0);
    }

    /**
     * 一个样本名下的全部未删记录（{@code ExtSampleDetailVo.embeds} 用）。
     *
     * <p>★ 排序由 {@code EmbedQueryService.listBySampleId} 定：<b>有编号的在前、没编号的
     * （待核验 / 无效的送样）在后</b>；同一档内按提交时间与 id 升序，结果稳定。
     * accept 段 1 断 1001 的两块恰是 {@code ["T-E01-1","T-E01-2"]}。
     *
     * <p>调用方（{@code ExtSampleAssemblyService.detail}）已经过 {@code assertVisible}，
     * 所以这里不再断言可见性（同一个样本不会两处判）。
     */
    public List<ExtEmbedVo> embedsOfSample(Long userId, Long sampleId) {
        return convert(embedQueryService.listBySampleId(sampleId), userId);
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * 内部行 → 对外 VO（一次批量取提交人姓名，随后纯内存转换）。
     */
    private List<ExtEmbedVo> convert(List<EmbedVo> rows, Long userId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> submitters = new LinkedHashSet<>();
        for (EmbedVo row : rows) {
            if (row.getSubmitterId() != null) {
                submitters.add(row.getSubmitterId());
            }
        }
        Map<Long, String> names = extScopeService.submitterNames(submitters);
        List<ExtEmbedVo> out = new ArrayList<>(rows.size());
        for (EmbedVo row : rows) {
            out.add(toVo(row, userId, names));
        }
        return out;
    }

    /**
     * 一行的装配。字段清单与顺序对着 {@link ExtEmbedVo} 的白名单抄 ——
     * 这里<b>没有</b>内部编号、备注、核验人、冻存，所以那几类键不可能「顺手」出现在外部 JSON 里。
     */
    private ExtEmbedVo toVo(EmbedVo src, Long userId, Map<Long, String> names) {
        ExtEmbedVo vo = new ExtEmbedVo();
        vo.setId(src.getId());
        vo.setSampleId(src.getSampleId());
        vo.setSubmitNo(src.getSubmitNo());
        vo.setParaffinBlockNo(src.getParaffinBlockNo());
        vo.setSampleType(src.getSampleType());
        vo.setOrganoidSourceType(src.getOrganoidSourceType());
        vo.setTissueReceiveTime(src.getTissueReceiveTime());
        vo.setTissueProcessTime(src.getTissueProcessTime());
        vo.setAgaroseEmbedTime(src.getAgaroseEmbedTime());
        vo.setDehydrateTime(src.getDehydrateTime());
        vo.setAgaroseSendTime(src.getAgaroseSendTime());
        vo.setParaffinEmbedTime(src.getParaffinEmbedTime());
        vo.setSectionTime(src.getSectionTime());
        // 派生值：不给 null（accept 段 1 断两块分别是 true / false）
        vo.setSectioned(src.getSectionTime() != null);
        vo.setStainTypes(src.getStainTypes() == null ? List.of() : src.getStainTypes());
        vo.setStainOther(src.getStainOther());
        vo.setMarkers(markersOf(src.getMarkers()));
        vo.setVerifyStatus(src.getVerifyStatus());
        vo.setInvalidReason(src.getInvalidReason());
        vo.setSubmitterName(src.getSubmitterId() == null ? null : names.get(src.getSubmitterId()));
        // ★「我提交的这条送样」：按本条记录的提交人，不是所挂样本的提交人
        boolean mine = userId != null && userId.equals(src.getSubmitterId());
        vo.setMine(mine);
        vo.setEditable(mine && isOpenForExternalEdit(src.getVerifyStatus()));
        // CR-20260918-07：对外可见（冻存信息与核验人仍然不给，本 VO 里根本没有那两类键）
        vo.setEmbedBy(src.getEmbedBy());
        vo.setOperatorName(src.getOperatorName());
        return vo;
    }

    /**
     * marker 只带名称与表达（内部 VO 的 {@code sort} 是工作台 / 导出用的，不对外）。
     */
    private static List<ExtEmbedMarkerVo> markersOf(List<EmbedMarkerVo> markers) {
        if (markers == null || markers.isEmpty()) {
            return List.of();
        }
        List<ExtEmbedMarkerVo> out = new ArrayList<>(markers.size());
        for (EmbedMarkerVo marker : markers) {
            ExtEmbedMarkerVo vo = new ExtEmbedMarkerVo();
            vo.setMarkerName(marker.getMarkerName());
            vo.setExpression(marker.getExpression());
            out.add(vo);
        }
        return out;
    }

    /**
     * 外部能不能改后重提这条记录：{@code pending} / {@code invalid} 才可以
     * （{@code EmbedExternalService.resubmit} 里那条闸的同一判据 —— 这边只用来算行上的 {@code editable}）。
     */
    private static boolean isOpenForExternalEdit(String verifyStatus) {
        return VerifyTransitions.PENDING.equals(verifyStatus) || VerifyTransitions.INVALID.equals(verifyStatus);
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
