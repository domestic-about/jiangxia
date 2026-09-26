package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部文档预览里的一个附件（{@code ExtDocPagesVo.attachments} 的元素类型；含细胞活率测定附件）。
 *
 * <p>★ {@code url} 是后端签发的 <b>10 分钟短时签名链接</b>；签发前已在 doc 域读口过咽喉
 * （只签本样本、已完成成员的对象，独立验收 V24）。
 *
 * @author 独立验收 V24 修复（外部预览给原图与附件）
 */
@Data
@Schema(description = "外部文档的一个附件")
public class ExtDocAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "附件文件名")
    private String fileName;

    @Schema(description = "字节数（可能为空）")
    private Integer fileSize;

    @Schema(description = "10 分钟有效的签名链接")
    private String url;

    public ExtDocAttachmentVo() {
    }

    public ExtDocAttachmentVo(String fileName, Integer fileSize, String url) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.url = url;
    }

}
