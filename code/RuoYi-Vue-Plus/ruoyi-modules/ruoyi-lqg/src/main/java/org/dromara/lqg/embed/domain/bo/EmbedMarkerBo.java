package org.dromara.lqg.embed.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条 marker 表达入参（{@code EmbedSubmitBo.markers} 的元素）。
 *
 * <p>★ {@code markerName} 可空、{@code expression} 必填且落在字典 {@code lqg_marker_expr}
 * （negative / weak / strong）：accept 2 第 8 段给的第二个元素就只有 {@code expression}，
 * 必须能落库并计入 markers 数（count = 2）。
 *
 * @author EMBED-MODEL-001
 */
@Data
@Schema(description = "marker 表达入参")
public class EmbedMarkerBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "marker 名称（可空）")
    private String markerName;

    @Schema(description = "negative 阴性 / weak 弱表达 / strong 强表达")
    private String expression;

}
