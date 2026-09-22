package org.dromara.lqg.doc.pdf.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个质控图片位：{@code {url, previewUrl}}（都是短时签名链接）。
 *
 * <p>★ {@code previewUrl} = 预览图（≤2000px 的 JPEG，QC-MODEL-001 的回落口径：
 * 解析不出预览图时等于 {@code url}）；点开看原图用 {@code url}。
 *
 * @author DOC-PDF-001
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "质控图片位（预览用预览图，点开看原图）")
public class DocPagesImageVo {

    @Schema(description = "原图的短时签名链接")
    private String url;

    @Schema(description = "预览图的短时签名链接（进 Word 的也是它）")
    private String previewUrl;
}
