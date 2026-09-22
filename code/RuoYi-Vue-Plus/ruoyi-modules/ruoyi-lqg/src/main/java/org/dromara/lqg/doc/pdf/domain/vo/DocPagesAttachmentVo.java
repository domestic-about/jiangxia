package org.dromara.lqg.doc.pdf.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个通用附件：{@code {fileName, fileSize, url}}（url 是短时签名链接）。
 *
 * @author DOC-PDF-001
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "质控文档的通用附件")
public class DocPagesAttachmentVo {

    @Schema(description = "附件文件名（原样给用户看）")
    private String fileName;

    @Schema(description = "字节数")
    private Integer fileSize;

    @Schema(description = "短时签名链接")
    private String url;
}
