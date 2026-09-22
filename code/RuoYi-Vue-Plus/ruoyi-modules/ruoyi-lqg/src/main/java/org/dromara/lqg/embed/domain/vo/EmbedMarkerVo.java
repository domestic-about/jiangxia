package org.dromara.lqg.embed.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条 marker 表达（{@code EmbedVo.markers} 的元素）。
 *
 * <p>形状与契约一致（{@code {markerName, expression}}）；{@code markerName} 可空
 * （只记表达情况不写名称也允许），{@code expression} ∈ 字典 {@code lqg_marker_expr}。
 *
 * @author EMBED-MODEL-001
 */
@Data
@Schema(description = "marker 表达")
public class EmbedMarkerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "marker 名称（可空）")
    private String markerName;

    @Schema(description = "negative 阴性 / weak 弱表达 / strong 强表达")
    private String expression;

    @Schema(description = "显示顺序（请求里的数组下标）")
    private Integer sort;

}
