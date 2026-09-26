package org.dromara.lqg.doc.render.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 渲染结果（{@code POST /lqg/doc/{sampleId}/{docKind}/render} 的 {@code data}）。
 *
 * <p>{@code status} 就是这份文档的渲染状态（pending / done / failed）：渲染失败不抛 500，
 * 而是 {@code failed} + {@code errorMsg}，工作台据此显示原因并「重新生成」。
 * 内部版有图片取不到时照样 {@code done}，但 {@code missingImageCount / missingImages} 会说明缺了哪几张。
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

    @Schema(description = "这一版取不到的图片张数（内部版照出并记缺图；外部版有缺图即 failed）")
    private int missingImageCount;

    @Schema(description = "缺了哪几张（给人看的一句话）")
    private String missingImages;
}
