package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部文档预览里的一张「文档中的图片」（{@code ExtDocPagesVo.images} 的元素类型）。
 *
 * <p>★ 两个都是后端签发的 <b>10 分钟短时签名链接</b>：{@code previewUrl} 是缩略用的预览图，
 * {@code url} 是点开看细节的原图（UI:mp.doc.preview 的「两层放大」第二层）。
 * 签发前已在 doc 域读口过咽喉：只签本样本、已完成成员的对象（独立验收 V24）。
 *
 * @author 独立验收 V24 修复（外部预览给原图与附件）
 */
@Data
@Schema(description = "外部文档中的一张图片")
public class ExtDocImageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "原图的 10 分钟签名链接（点开看细节）")
    private String url;

    @Schema(description = "预览图的 10 分钟签名链接（缩略用）")
    private String previewUrl;

    public ExtDocImageVo() {
    }

    public ExtDocImageVo(String url, String previewUrl) {
        this.url = url;
        this.previewUrl = previewUrl;
    }

}
