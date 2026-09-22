package org.dromara.lqg.doc.render.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 下载信息（{@code GET /lqg/doc/{sampleId}/{docKind}/download} 的 {@code data}）。
 *
 * <p>★ {@code url} 是 <b>10 分钟签名链接</b>（ticket §2）：产物在私有桶里，页面拿到的是
 * 短时链接，不是公开地址。链接过期就再调一次这个接口。
 *
 * <p>★ {@code fileName} = 文档名 + 内部编号（内部版）/ 送检单号（外部版），带 {@code .docx}。
 *
 * @author DOC-RENDER-001
 */
@Data
@Schema(description = "文档下载信息")
public class DocDownloadVo {

    @Schema(description = "10 分钟有效的签名下载链接")
    private String url;

    @Schema(description = "文件名（含扩展名）")
    private String fileName;
}
