package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 类器官质量评分表的保存入参（{@code PUT /lqg/qc/{sampleId}/score}，FLOW:F-QC-01.step4）。
 *
 * <p>★★ <b>只收四个 {@code *Level}，一个 {@code *Score} 都不收</b>
 * （ticket §2 + accept 2 的 counterfeit）：分值由后端按字典 {@code remark} 回填
 * （{@code QcScoreDictionary}）。前端在请求里夹带 {@code preCultureScore: 99}
 * 之类的键<b>不生效</b> —— 本类根本没有这些字段（Jackson 默认忽略未知键）。
 *
 * <p>★ {@code null}（或不传）= 这一项<b>没选</b> → 该项分值与合计都落 NULL。
 * 「0 分档」与「没选」是两件事：{@code cultureDaysLevel:"gt14"} 是<b>选了 0 分</b>，
 * 不是没选。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "类器官质量评分表保存（只收四个档位）")
public class QcScoreSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "培养前样本评分档位 lt40 / 40to80 / gt80")
    private String preCultureLevel;

    @Schema(description = "培养天数档位 gt14 / le14")
    private String cultureDaysLevel;

    @Schema(description = "类器官数量档位 lt100 / 100to1500 / 1500to4000 / gt4000")
    private String organoidCountLevel;

    @Schema(description = "类器官直径档位 lt30 / 30to100 / gt100")
    private String diameterLevel;

}
