package org.dromara.lqg.embed.guard;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.embed.domain.bo.EmbedFillBo;
import org.dromara.lqg.embed.domain.bo.EmbedMarkerBo;
import org.dromara.lqg.sample.domain.bo.PatchBody;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 石蜡包埋<b>补填段</b>（{@link EmbedFillBo}）的键分组与格式规则——<b>纯函数</b>，无 Spring、无库，契约测试直接钉。
 *
 * <p>★ 键分两组，口径逐条对得上权威（FIX V02b / issue #147）：
 * <ul>
 *   <li>{@link #SUBMIT_KEYS}：<b>外部送样填的两项</b>（{@code UI:mp.embed.form} 外部只渲染「选择样本 → 样本类型、类器官来源类型」，
 *       {@code FLOW:F-EMBED-01.step6} 的 writes）；</li>
 *   <li>{@link #LAB_KEYS}：<b>实验室补填的 13 项</b>——{@code UI:mp.embed.form}「石蜡块编号、工序时间、包埋人、染色、marker、操作人、备注
 *       都不渲染（实验室核验有效后填）」、{@code UI:admin.embed.list}「判有效后照常补填工序与染色」、
 *       {@code FLOW:F-EMBED-01.step2 / step3}。</li>
 * </ul>
 * 核验抽屉「判为有效并保存」两组都收；「判为无效」只收 {@link #SUBMIT_KEYS}（实验室的补填只属于有效的石蜡块，
 * 同样本核验「判无效的样本不补收样信息」一个口径），带了 {@link #LAB_KEYS} 明确 400，不静默丢。
 *
 * <p>★ 长度 = DDL 的 VARCHAR 长度（{@code FIELD:t_lqg_embed.*}、{@code t_lqg_embed_marker.marker_name}）：
 * 以前超长一路走到 {@code UPDATE} 才被 PostgreSQL 拦下，响应里是整段 SQL（与独立验收 V03 同一类）；
 * 现在所有写入口在<b>任何写库之前</b>过一遍，违规 {@code code=400} + 字段级人话。
 *
 * @author FIX-V02b
 */
public final class EmbedFillRules {

    /** 报错前缀（响应 msg = 前缀 + 逐条违规，用「；」隔开） */
    public static final String MSG_PREFIX = "补填的内容不符合要求：";

    /** 外部送样填的两项（判有效、判无效都随核验一起保存） */
    public static final List<String> SUBMIT_KEYS = List.of("sampleType", "organoidSourceType");

    /** 实验室补填的 13 项（只在判为有效时随核验一起保存） */
    public static final List<String> LAB_KEYS = List.of(
        "tissueReceiveTime", "tissueProcessTime", "agaroseEmbedTime", "embedBy", "dehydrateTime",
        "agaroseSendTime", "paraffinEmbedTime", "sectionTime", "stainTypes", "stainOther", "markers",
        "operatorName", "remark");

    /** 补填段全部 15 项 = {@link EmbedFillBo} 的字段（契约测试钉住两边一致） */
    public static final List<String> ALL_KEYS = Stream.concat(SUBMIT_KEYS.stream(), LAB_KEYS.stream()).toList();

    /**
     * {@code PUT /lqg/embed} 有、核验时的 {@code fill} 里<b>不收</b>的键：所挂样本核验时不能换
     * （抽屉里是只读文本「所挂样本已确定，不能更换」）；石蜡块编号走请求顶层的 {@code paraffinBlockNo}。
     * 夹带了明确 400，不静默丢。
     */
    public static final List<String> NOT_IN_FILL = List.of("sampleId", "paraffinBlockNo");

    /** 字段中文名（报错用；顺序 = 抽屉里的顺序） */
    public static final Map<String, String> LABELS;

    /** {@code t_lqg_embed.sample_type} VARCHAR(50) */
    public static final int MAX_SAMPLE_TYPE = 50;
    /** {@code t_lqg_embed.organoid_source_type} VARCHAR(100) */
    public static final int MAX_ORGANOID_SOURCE_TYPE = 100;
    /** {@code t_lqg_embed.embed_by} VARCHAR(50) */
    public static final int MAX_EMBED_BY = 50;
    /** {@code t_lqg_embed.stain_other} VARCHAR(100) */
    public static final int MAX_STAIN_OTHER = 100;
    /** {@code t_lqg_embed.operator_name} VARCHAR(50) */
    public static final int MAX_OPERATOR_NAME = 50;
    /** {@code t_lqg_embed.remark} VARCHAR(500) */
    public static final int MAX_REMARK = 500;
    /** {@code t_lqg_embed_marker.marker_name} VARCHAR(50) */
    public static final int MAX_MARKER_NAME = 50;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("sampleType", "样本类型");
        labels.put("organoidSourceType", "类器官来源类型");
        labels.put("embedBy", "包埋人");
        labels.put("tissueReceiveTime", "组织收样时间");
        labels.put("tissueProcessTime", "组织处理时间");
        labels.put("agaroseEmbedTime", "琼脂糖包埋样本时间");
        labels.put("dehydrateTime", "脱水时间");
        labels.put("agaroseSendTime", "琼脂糖包埋样本送样时间");
        labels.put("paraffinEmbedTime", "石蜡包埋时间");
        labels.put("sectionTime", "切片时间");
        labels.put("stainTypes", "染色");
        labels.put("stainOther", "其他染色名称");
        labels.put("markers", "marker 表达");
        labels.put("operatorName", "操作人");
        labels.put("remark", "备注");
        labels.put("sampleId", "所挂样本");
        labels.put("paraffinBlockNo", "石蜡块编号");
        LABELS = Collections.unmodifiableMap(labels);
    }

    private EmbedFillRules() {
    }

    /**
     * 补丁里<b>出现了</b>的文本键的长度违规（空列表 = 通过）。没出现的键不看（补丁语义：没带 = 不动）。
     */
    public static List<String> lengthViolations(PatchBody<? extends EmbedFillBo> body) {
        List<String> out = new ArrayList<>();
        if (body == null || body.value() == null) {
            return out;
        }
        EmbedFillBo v = body.value();
        checkLength(out, body, "sampleType", v.getSampleType(), MAX_SAMPLE_TYPE);
        checkLength(out, body, "organoidSourceType", v.getOrganoidSourceType(), MAX_ORGANOID_SOURCE_TYPE);
        checkLength(out, body, "embedBy", v.getEmbedBy(), MAX_EMBED_BY);
        checkLength(out, body, "stainOther", v.getStainOther(), MAX_STAIN_OTHER);
        checkLength(out, body, "operatorName", v.getOperatorName(), MAX_OPERATOR_NAME);
        checkLength(out, body, "remark", v.getRemark(), MAX_REMARK);
        if (body.has("markers") && v.getMarkers() != null) {
            int row = 0;
            for (EmbedMarkerBo marker : v.getMarkers()) {
                row++;
                if (marker != null && tooLong(marker.getMarkerName(), MAX_MARKER_NAME)) {
                    out.add("第 " + row + " 行 marker 名称不能超过 " + MAX_MARKER_NAME + " 字");
                }
            }
        }
        return out;
    }

    /**
     * 核验 {@code fill} 里不许出现的键（{@link #NOT_IN_FILL}），按出现顺序。
     */
    public static List<String> notAllowedInFill(Collection<String> keys) {
        return keys == null ? List.of() : keys.stream().filter(NOT_IN_FILL::contains).toList();
    }

    /**
     * 实验室补填键（{@link #LAB_KEYS}），按出现顺序 —— 判为无效时它们不收。
     */
    public static List<String> labKeysIn(Collection<String> keys) {
        return keys == null ? List.of() : keys.stream().filter(LAB_KEYS::contains).toList();
    }

    /**
     * 键 → 中文名，用「、」连起来（报错用）。
     */
    public static String labelsOf(Collection<String> keys) {
        return String.join("、", keys.stream().map(k -> LABELS.getOrDefault(k, k)).toList());
    }

    /**
     * 有违规就抛 {@code ServiceException(前缀 + 逐条, 400)}；没有就什么都不做。
     */
    public static void throwIfAny(List<String> violations) {
        if (violations != null && !violations.isEmpty()) {
            throw new ServiceException(MSG_PREFIX + String.join("；", violations), 400);
        }
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private static void checkLength(List<String> out, PatchBody<? extends EmbedFillBo> body, String key, String value, int max) {
        if (body.has(key) && tooLong(value, max)) {
            out.add(LABELS.get(key) + "不能超过 " + max + " 字");
        }
    }

    private static boolean tooLong(String value, int max) {
        String v = StringUtils.isBlank(value) ? null : value.trim();
        return v != null && v.codePointCount(0, v.length()) > max;
    }

}
