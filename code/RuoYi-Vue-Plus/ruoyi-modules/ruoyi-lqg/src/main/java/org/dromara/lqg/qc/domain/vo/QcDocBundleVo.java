package org.dromara.lqg.qc.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * {@code GET /lqg/qc/{sampleId}} 的返回体（doc/api-contract.md 第 79 行）：
 * 样本主档只读字段 + 三份文档（各带图片位与附件）。
 *
 * <pre>
 * data: { sample: {...}, sampleQc: {...}, organoidQc: {...}, score: {...} }
 * </pre>
 *
 * <p>★ 三份文档<b>一定都在</b>（首次访问就「缺哪份建哪份」空草稿），不存在 null
 * —— 前端三个页签不用处理「文档还没建」这一档。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "质控文档聚合（三份文档 + 样本只读字段）")
public class QcDocBundleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "样本主档带出的只读字段")
    private QcSampleRefVo sample;

    @Schema(description = "样本质控表")
    private QcSampleDocVo sampleQc;

    @Schema(description = "类器官质控表")
    private QcOrganoidDocVo organoidQc;

    @Schema(description = "类器官质量评分表")
    private QcScoreDocVo score;

}
