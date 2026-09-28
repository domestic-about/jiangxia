package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.EmbedMarker;
import org.dromara.lqg.embed.domain.bo.EmbedFillBo;
import org.dromara.lqg.embed.domain.bo.EmbedMarkerBo;
import org.dromara.lqg.embed.guard.EmbedFillRules;
import org.dromara.lqg.embed.guard.MarkerExprRules;
import org.dromara.lqg.embed.guard.StainRules;
import org.dromara.lqg.embed.mapper.EmbedMarkerMapper;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 石蜡包埋<b>补填段</b>（{@link EmbedFillBo} 那 15 项）唯一的校验与落库列口径（FIX V02b / issue #147）。
 *
 * <p>★ 为什么单独一个类：补填段有三条写路径 —— 内部新增、内部修改（{@code PUT /lqg/embed}、{@code PUT /mp/int/embed}）、
 * 工作台核验抽屉「判为有效并保存」时一并保存的 {@code fill}。以前核验那条路径根本不收补填段（抽屉里填的工序、染色、marker
 * 被静默丢弃）；现在三条路径<b>同一份规则、同一组列</b>：
 * <ul>
 *   <li>校验（{@link #prepare}）：长度（{@link EmbedFillRules}）、染色组合与字典（{@link StainRules}）、marker 表达字典
 *       （{@link MarkerExprRules}）—— 全部在<b>任何写库之前</b>，任何一条不过就拒，库里一个字都不变；</li>
 *   <li>落库（{@link #applyTo} + {@link #replaceMarkers}）：补丁语义（FIX V33）—— 键没出现 = 不动；
 *       出现、值为空 = 清空；{@code markers} 出现就整组替换（先软删旧的再插新的，同一事务）。</li>
 * </ul>
 * 调用方负责把 {@link #applyTo} 拼进<b>自己那一条</b> UPDATE（核验时就是核验结论那一条 —— 原子）。
 *
 * @author FIX-V02b
 */
@Service
@RequiredArgsConstructor
public class EmbedFillWriter {

    private final EmbedMarkerMapper embedMarkerMapper;
    private final EmbedDictService dictService;

    /**
     * 校验过的补丁：等着拼进调用方那一条 UPDATE、再整组替换 marker。
     *
     * @param body    补丁（出现过哪些键 + 值）
     * @param stains  补丁里带了 {@code stainTypes} 时规范化后的染色（固定顺序）；没带 = {@code null}
     * @param markers 补丁里带了 {@code markers} 时校验过的整组 marker（{@code null} 按空组）；没带 = {@code null}
     */
    public record Prepared(PatchBody<? extends EmbedFillBo> body, List<String> stains, List<EmbedMarkerBo> markers) {

        /**
         * 这个键在补丁里出现过没有。
         */
        public boolean has(String key) {
            return body.has(key);
        }
    }

    /**
     * 校验补丁里<b>出现了</b>的键（没出现的不看）：长度 → 染色 → marker。任何写库之前调用。
     *
     * @param body 补丁；{@code null} = 调用方没带补填段
     * @return 校验过的补丁；{@code body == null} → {@code null}（补填段一个字都不动）
     */
    public Prepared prepare(PatchBody<? extends EmbedFillBo> body) {
        if (body == null || body.value() == null) {
            return null;
        }
        EmbedFillRules.throwIfAny(EmbedFillRules.lengthViolations(body));
        EmbedFillBo bo = body.value();
        List<String> stains = null;
        if (body.has("stainTypes")) {
            // 传了就整组替换（含 stainOther 的置空 / 必填；传 null / [] = 清空染色）
            stains = normalizeStains(bo.getStainTypes() == null ? List.of() : bo.getStainTypes(), bo.getStainOther());
        }
        List<EmbedMarkerBo> markers = null;
        if (body.has("markers")) {
            markers = bo.getMarkers() == null ? List.of() : bo.getMarkers();
            validateMarkers(markers);
        }
        return new Prepared(body, stains, markers);
    }

    /**
     * 把补丁里出现了的标量列拼进调用方的 UPDATE（{@code markers} 另走 {@link #replaceMarkers}）。
     *
     * @param patch    调用方那一条 UPDATE
     * @param prepared {@link #prepare} 的结果；{@code null} = 什么都不拼
     */
    public void applyTo(LambdaUpdateWrapper<Embed> patch, Prepared prepared) {
        if (prepared == null) {
            return;
        }
        EmbedFillBo bo = prepared.body().value();
        // 染色：传了就整组替换（含 stainOther 的置空 / 必填）；只给 stainOther 时单独改
        if (prepared.stains() != null) {
            patch.set(Embed::getStainTypes, StainRules.toCsv(prepared.stains()))
                .set(Embed::getStainOther, StainRules.hasOther(prepared.stains()) ? trimToNull(bo.getStainOther()) : null);
        } else if (prepared.has("stainOther")) {
            patch.set(Embed::getStainOther, trimToNull(bo.getStainOther()));
        }
        // 其余标量：没传 = 不动；传了空值 = 清空（FIX V33：以前 null = 不动，日期清不掉）
        patch.set(prepared.has("sampleType"), Embed::getSampleType, trimToNull(bo.getSampleType()))
            .set(prepared.has("organoidSourceType"), Embed::getOrganoidSourceType, trimToNull(bo.getOrganoidSourceType()))
            .set(prepared.has("tissueReceiveTime"), Embed::getTissueReceiveTime, bo.getTissueReceiveTime())
            .set(prepared.has("tissueProcessTime"), Embed::getTissueProcessTime, bo.getTissueProcessTime())
            .set(prepared.has("agaroseEmbedTime"), Embed::getAgaroseEmbedTime, bo.getAgaroseEmbedTime())
            .set(prepared.has("embedBy"), Embed::getEmbedBy, trimToNull(bo.getEmbedBy()))
            .set(prepared.has("dehydrateTime"), Embed::getDehydrateTime, bo.getDehydrateTime())
            .set(prepared.has("agaroseSendTime"), Embed::getAgaroseSendTime, bo.getAgaroseSendTime())
            .set(prepared.has("paraffinEmbedTime"), Embed::getParaffinEmbedTime, bo.getParaffinEmbedTime())
            .set(prepared.has("sectionTime"), Embed::getSectionTime, bo.getSectionTime())
            .set(prepared.has("operatorName"), Embed::getOperatorName, trimToNull(bo.getOperatorName()))
            .set(prepared.has("remark"), Embed::getRemark, trimToNull(bo.getRemark()));
    }

    /**
     * marker：补丁里带了就整组替换（先软删旧的、再插新的；传 null / [] = 清空），没带不动。
     * 与 {@link #applyTo} 拼进的那条 UPDATE 同一个事务（调用方的 {@code @Transactional}）。
     */
    public void replaceMarkers(Long embedId, Prepared prepared) {
        if (prepared == null || prepared.markers() == null) {
            return;
        }
        embedMarkerMapper.delete(new LambdaQueryWrapper<EmbedMarker>()
            .eq(EmbedMarker::getEmbedId, embedId));
        insertMarkers(embedId, prepared.markers());
    }

    // ── 新增路径也用的两块（同一份规则） ─────────────────────────────────────

    /**
     * 染色组合 + 字典校验并按固定顺序规范化（{@link StainRules#normalize}）。
     */
    public List<String> normalizeStains(List<String> raw, String stainOther) {
        return StainRules.normalize(raw, dictService.stainValues(), stainOther);
    }

    /**
     * 一整组 marker：每条表达必须落在字典 {@code lqg_marker_expr} 内；{@code markerName} 可空。
     *
     * <p>先全组校验、再逐条插 —— 第 2 条不合法时第 1 条也不该落库（事务是第二道保险，不是第一道）。
     */
    public void validateMarkers(List<EmbedMarkerBo> markers) {
        if (markers == null || markers.isEmpty()) {
            return;
        }
        List<String> allowed = dictService.markerExprValues();
        for (EmbedMarkerBo marker : markers) {
            if (marker == null) {
                continue;
            }
            MarkerExprRules.normalize(marker.getExpression(), allowed);
        }
    }

    /**
     * 逐条插 marker（{@code sort} 从 0 起按请求顺序）。调用方已 {@link #validateMarkers} 过。
     */
    public void insertMarkers(Long embedId, List<EmbedMarkerBo> markers) {
        if (markers == null || markers.isEmpty()) {
            return;
        }
        List<String> allowed = dictService.markerExprValues();
        int sort = 0;
        for (EmbedMarkerBo marker : markers) {
            if (marker == null) {
                continue;
            }
            EmbedMarker entity = new EmbedMarker();
            entity.setEmbedId(embedId);
            entity.setMarkerName(trimToNull(marker.getMarkerName()));
            entity.setExpression(MarkerExprRules.normalize(marker.getExpression(), allowed));
            entity.setSort(sort++);
            embedMarkerMapper.insert(entity);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
