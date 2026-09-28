package org.dromara.lqg.qc.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三份质控文档的**纯规则**（无 Spring、无库）：文档类型 / 图片位归属 / 张数上限 /
 * 附件大小上限 / 三段模板默认文字 / 评分档位到字典的映射。
 *
 * <p>★ 单独拎出来的理由：这些是 accept 与页面票都要引用的**口径**，
 * 散在 service 里迟早两边不一致（{@code QcDocRulesContractTest} 用反射与常量逐条钉住，
 * 不需要起 Spring 上下文）。
 *
 * @author QC-MODEL-001
 */
public final class QcDocRules {

    private QcDocRules() {
    }

    // ── 字典 lqg_doc_type 的三个取值（库里落的值） ──────────────────────────
    public static final String DOC_TYPE_SAMPLE_QC = "sample_qc";
    public static final String DOC_TYPE_ORGANOID_QC = "organoid_qc";
    public static final String DOC_TYPE_ORGANOID_SCORE = "organoid_score";

    // ── 路径段（doc/api-contract.md 第 90 行：路径里用连字符） ──────────────
    public static final String DOC_TYPE_PATH_SAMPLE_QC = "sample-qc";
    public static final String DOC_TYPE_PATH_ORGANOID_QC = "organoid-qc";
    public static final String DOC_TYPE_PATH_SCORE = "score";

    // ── 字典 lqg_image_slot 的四个取值 ─────────────────────────────────────
    public static final String SLOT_ORIG = "orig";
    public static final String SLOT_OBSERVE = "observe";
    public static final String SLOT_PRETREAT = "pretreat";
    public static final String SLOT_ORGANOID_OBSERVE = "organoid_observe";

    /**
     * ★ <b>图片位归属</b>（FIELD:t_lqg_doc_image.slot）：
     * {@code orig / observe / pretreat} 属样本质控表，{@code organoid_observe} 属类器官质控表，
     * <b>评分表没有图片位</b>（空列表）。
     */
    private static final Map<String, List<String>> SLOTS_BY_DOC_TYPE = Map.of(
        DOC_TYPE_SAMPLE_QC, List.of(SLOT_ORIG, SLOT_OBSERVE, SLOT_PRETREAT),
        DOC_TYPE_ORGANOID_QC, List.of(SLOT_ORGANOID_OBSERVE),
        DOC_TYPE_ORGANOID_SCORE, List.of()
    );

    /** 路径段 → 字典取值。 */
    private static final Map<String, String> DOC_TYPE_BY_PATH = Map.of(
        DOC_TYPE_PATH_SAMPLE_QC, DOC_TYPE_SAMPLE_QC,
        DOC_TYPE_PATH_ORGANOID_QC, DOC_TYPE_ORGANOID_QC,
        DOC_TYPE_PATH_SCORE, DOC_TYPE_ORGANOID_SCORE
    );

    /** 每个图片位至多几张图。 */
    public static final int MAX_IMAGES_PER_SLOT = 3;

    /** 单个通用附件至多多少字节（50MB）。 */
    public static final long MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024;

    // ── 字典 lqg_doc_status ────────────────────────────────────────────────
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";

    // ── 三段模板默认文字（ticket §2：新建样本质控表时逐字预填） ★ 一个字都别改 ──
    public static final String RECEIVE_DESC_DEFAULT =
        "样本按质控要求，保持2-8℃低温环境运输至实验室。";
    public static final String OBSERVE_DESC_DEFAULT =
        "样本外观呈黄白色。";
    public static final String PRETREAT_DESC_DEFAULT =
        "样本经剪切等预处理，显微镜下观察组织漏出细胞量适中，细胞活性中等；培养3d照片如左图所示。";

    // ── 评分：入参字段 → 字典类型 → 落库的分值列 ────────────────────────────
    /**
     * 四个档位字段与它们的字典，<b>顺序 = 文档上的顺序</b>
     * （培养前样本评分 / 培养天数 / 类器官数量 / 类器官直径）。
     * <p>分值的唯一来源是 {@code sys_dict_data.remark}（{@code QcScoreDictionary}）。
     */
    public static final String DICT_PRE_CULTURE = "lqg_score_pre_culture";
    public static final String DICT_CULTURE_DAYS = "lqg_score_culture_days";
    public static final String DICT_ORGANOID_COUNT = "lqg_score_count";
    public static final String DICT_DIAMETER = "lqg_score_diameter";

    /** 四个评分字典的类型清单（给 {@code QcScoreDictMapper} 与测试共用）。 */
    public static final List<String> SCORE_DICT_TYPES = List.of(
        DICT_PRE_CULTURE, DICT_CULTURE_DAYS, DICT_ORGANOID_COUNT, DICT_DIAMETER);

    /**
     * 字典类型 → 中文名（拼错误信息用；顺序表）。
     */
    public static final Map<String, String> SCORE_DICT_LABELS = new LinkedHashMap<>() {{
        put(DICT_PRE_CULTURE, "培养前样本评分");
        put(DICT_CULTURE_DAYS, "培养天数");
        put(DICT_ORGANOID_COUNT, "类器官数量");
        put(DICT_DIAMETER, "类器官直径");
    }};

    /**
     * 路径段 → 字典取值；不认识的一律 400（不静默当默认类型）。
     *
     * @param docTypePath {@code sample-qc | organoid-qc | score}
     */
    public static String requireDocType(String docTypePath) {
        String docType = docTypePath == null ? null : DOC_TYPE_BY_PATH.get(docTypePath);
        if (docType == null) {
            throw new ServiceException("文档类型只能是 sample-qc / organoid-qc / score，收到：" + docTypePath, 400);
        }
        return docType;
    }

    /**
     * 该文档类型的图片位清单（评分表是空列表）。
     */
    public static List<String> slotsOf(String docType) {
        return SLOTS_BY_DOC_TYPE.getOrDefault(docType, List.of());
    }

    /**
     * ★ <b>图片位必须属于该文档类型</b>：{@code organoid_observe} 挂到样本质控表上、
     * 或往评分表挂任何图片位 → 400 且库里不变。
     *
     * @return 归一化后的 slot
     */
    public static String requireSlotOf(String docType, String slot) {
        String value = StringUtils.isBlank(slot) ? null : slot.trim();
        if (value == null) {
            throw new ServiceException("图片位不能为空（" + docType + " 的图片位："
                + String.join(" / ", slotsOf(docType)) + "）", 400);
        }
        if (!slotsOf(docType).contains(value)) {
            throw new ServiceException("图片位 " + value + " 不属于 " + docType
                + "（它只有：" + (slotsOf(docType).isEmpty() ? "没有图片位" : String.join(" / ", slotsOf(docType)))
                + "）", 400);
        }
        return value;
    }

}
