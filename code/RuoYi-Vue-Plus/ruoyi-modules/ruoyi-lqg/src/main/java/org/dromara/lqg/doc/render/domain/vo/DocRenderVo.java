package org.dromara.lqg.doc.render.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 渲染结果（{@code POST /lqg/doc/{sampleId}/{docKind}/render} 的 {@code data}）。
 *
 * <p>★ {@code status} 就是 {@code t_lqg_doc_file.render_status}（pending / done / failed）：
 * 渲染失败**不抛 500**，而是 {@code failed} + {@code errorMsg} —— 送检方要能看见失败、
 * 也要能重试（DOC-PDF-001 的 accept 2 就是断这一条）。
 *
 * @author DOC-RENDER-001
 */
@Data
@Schema(description = "文档渲染结果")
public class DocRenderVo {

    @Schema(description = "样本 id")
    private Long sampleId;

    @Schema(description = "文档种类：sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "文档版本：internal / external")
    private String audience;

    @Schema(description = "pending / done / failed")
    private String status;

    @Schema(description = "产物 oss_id（done 时非空）")
    private Long ossId;

    @Schema(description = "本次算出的内容指纹")
    private String contentHash;

    @Schema(description = "所用模板版本号")
    private String templateVersion;

    @Schema(description = "失败原因（failed 时非空）")
    private String errorMsg;

    @Schema(description = "生成完成时间")
    private Date renderedTime;

    @Schema(description = "true = 这次命中缓存、没有重新渲染")
    private boolean cached;
}
