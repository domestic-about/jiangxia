package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部文档的下载信息（{@code GET /mp/ext/doc/{sampleId}/{docKind}/download} 的 {@code data}）。
 *
 * <p>★ {@code url} 是 <b>10 分钟签名链接</b>（FLOW:F-DOC-02.step3），后端先按咽喉断言可见、
 * 再按「已完成 + 外部版渲染成功」判可用、<b>最后再核一遍对象键里的 audience 段是 external</b>
 * 才签发。对象键约定见 {@code DocOssBytes.objectKey} + DOC-RENDER-001。
 *
 * <p>★ 内部版与外部版是<b>两份独立产物</b>（{@code audience} 进对象键、进内容指纹），
 * 所以外部永远拿不到带内部编号的那一份 —— 不是下载时抹掉，是那一份根本没进这条链路。
 *
 * <p>★ 文件名是文档名 + <b>送检单号</b>（外部版口径，{@code DocRenderModelFactory#displayName}），
 * 不含内部编号。
 *
 * @author AUTH-EXT-003
 */
@Data
@Schema(description = "外部文档下载信息")
public class ExtDocDownloadVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "10 分钟有效的签名下载链接")
    private String url;

    @Schema(description = "文件名（含扩展名）")
    private String fileName;

}
