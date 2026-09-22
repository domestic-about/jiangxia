package org.dromara.lqg.doc.pdf.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /lqg/doc/{sampleId}/{docKind}/pages?audience=} 的 {@code data}
 * （doc/api-contract.md 的 QC / DOC 一节 + ticket §2）。
 *
 * <pre>
 *   {status, errorMsg, pages:[{pageNo,url}], images:[{url,previewUrl}], attachments:[{fileName,fileSize,url}]}
 * </pre>
 *
 * <p>★ 所有 url 都是**短时签名链接**（10 分钟，私有桶）。
 *
 * <p>★ {@code status} 就是 header 行（docx / page_no=0）的 {@code render_status}：
 * {@code failed} 时 {@code pages} **一定是空数组** —— 旧产物仍在 OSS 与库里保留，
 * 但绝不再被当成最新返回（ticket §2、accept 2 第 5/6 段）。
 *
 * <p>★ {@code errorMsg} 是票面形状之外**多加的一个键**：工作台要在 failed 时显示「为什么失败」
 * （FLOW:F-DOC-01.step6）。加键不改形状，DOC-MP-* / QC-WEB-* 直接取用即可。
 *
 * @author DOC-PDF-001
 */
@Data
@Schema(description = "文档页面图片 + 图片位 + 附件（预览页用）")
public class DocPagesVo {

    @Schema(description = "pending / done / failed")
    private String status;

    @Schema(description = "失败原因（failed 时非空；工作台显示 + 「重新生成」的依据）")
    private String errorMsg;

    @Schema(description = "文档种类：sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "文档版本：internal / external")
    private String audience;

    @Schema(description = "当前内容指纹（产物缓存键）")
    private String contentHash;

    @Schema(description = "所用模板版本号")
    private String templateVersion;

    @Schema(description = "页面图片（pageNo 从 1 起，仅 done 时非空）")
    private List<DocPageItemVo> pages = new ArrayList<>();

    @Schema(description = "质控图片位（点开看原图 previewUrl / 原图 url）")
    private List<DocPagesImageVo> images = new ArrayList<>();

    @Schema(description = "通用附件")
    private List<DocPagesAttachmentVo> attachments = new ArrayList<>();
}
