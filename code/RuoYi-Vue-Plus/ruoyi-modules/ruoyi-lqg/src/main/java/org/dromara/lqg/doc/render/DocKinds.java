package org.dromara.lqg.doc.render;

import org.dromara.common.core.exception.ServiceException;

import java.util.List;

/**
 * 渲染产物的三个文档种类 + 合并件（字典 {@code lqg_doc_kind}，FIELD:t_lqg_doc_file.doc_kind）。
 *
 * <p>★ 与 {@code QcDocRules} 的 {@code DOC_TYPE_*} 字面相同（{@code sample_qc / organoid_qc /
 * organoid_score}），但那是**质控草稿**的类型（用户可见的三张表），这里是**渲染产物**的种类
 * ——多一个 {@code merged}。两者故意不合并：渲染要能对「三份草稿拼起来的那一份」建产物，
 * 而草稿侧永远不会有 merged。
 *
 * <p>★ URL 路径段与字典取值**同名**（api-contract 第 83~85 行：{@code /lqg/doc/{sampleId}/{docKind}/…}），
 * 不像质控侧那样有连字符/下划线两套（那是 CR 遗留的历史包袱）。
 *
 * @author DOC-RENDER-001
 */
public final class DocKinds {

    private DocKinds() {
    }

    /** 样本质控表（含图片位 orig / observe / pretreat）。 */
    public static final String SAMPLE_QC = "sample_qc";
    /** 类器官质控表（含图片位 organoid_observe）。 */
    public static final String ORGANOID_QC = "organoid_qc";
    /** 类器官质量评分表（没有图片位）。 */
    public static final String ORGANOID_SCORE = "organoid_score";
    /** 合并件：已完成的三份依次拼接、每份另起一页（ADR-0005）。 */
    public static final String MERGED = "merged";

    /** 合并件的拼接顺序（ticket §2 写死的顺序，不是完成时间）。 */
    public static final List<String> MERGED_ORDER = List.of(SAMPLE_QC, ORGANOID_QC, ORGANOID_SCORE);

    /** 有独立模板的三个种类（merged 没有自己的模板，它是三份的拼接）。 */
    public static final List<String> TEMPLATED = MERGED_ORDER;

    /**
     * 中文名（拼文件名用，{@code DocRenderModelFactory#displayName}）。
     *
     * <p>★ 合并件的名字是 <b>「质控文档（合并）」</b>（DOC-MP-002 ticket §0 口径 5：合并文件叫
     * 「质控文档（合并）」）—— 它同时是微信里「发送到微信」看到的那个名字，
     * 所以前端 {@code pages/doc/download.ts#downloadFileName} 用的是同一条规则
     * （两条必须一字不差，否则下载下来的与发出去的名字不一样）。
     */
    public static String label(String docKind) {
        return switch (docKind) {
            case SAMPLE_QC -> "样本质控表";
            case ORGANOID_QC -> "类器官质控表";
            case ORGANOID_SCORE -> "类器官质量评分表";
            case MERGED -> "质控文档（合并）";
            default -> docKind;
        };
    }

    /**
     * 校验路径段；不认识的一律 400（不静默当默认种类）。
     */
    public static String require(String docKind) {
        if (docKind == null || !List.of(SAMPLE_QC, ORGANOID_QC, ORGANOID_SCORE, MERGED).contains(docKind)) {
            throw new ServiceException("文档种类只能是 sample_qc / organoid_qc / organoid_score / merged，收到："
                + docKind, 400);
        }
        return docKind;
    }

    /** 是不是合并件（合并件不能直接拿来算「文档自己的字段」）。 */
    public static boolean isMerged(String docKind) {
        return MERGED.equals(docKind);
    }
}
