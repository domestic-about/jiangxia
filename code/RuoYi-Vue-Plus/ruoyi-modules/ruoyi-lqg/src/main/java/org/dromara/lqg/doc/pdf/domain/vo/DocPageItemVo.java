package org.dromara.lqg.doc.pdf.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一页图片：{@code {pageNo, url}}（page_no 从 1 起，url = 短时签名链接）。
 *
 * @author DOC-PDF-001
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档的一页图片")
public class DocPageItemVo {

    @Schema(description = "页码（从 1 起，与 PDF 的页序一致）")
    private Integer pageNo;

    @Schema(description = "短时签名链接（PNG，150 DPI）")
    private String url;
}
