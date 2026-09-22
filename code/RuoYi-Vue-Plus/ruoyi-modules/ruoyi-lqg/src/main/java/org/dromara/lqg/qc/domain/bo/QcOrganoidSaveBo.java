package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 类器官质控表的保存入参（{@code PUT /lqg/qc/{sampleId}/organoid-qc}，FLOW:F-QC-01.step3）。
 *
 * <p>★ 五栏<b>都是自由文本</b>（补丁语义，同 {@code QcSampleSaveBo}）：
 * {@code formedTime} / {@code feedbackTime} 也允许「约第 5 天」这种写法 ——
 * 后端<b>不做日期格式校验</b>（列本身是 VARCHAR）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "类器官质控表保存")
public class QcOrganoidSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "形成类器官时间（文本）")
    private String formedTime;

    @Schema(description = "生长状态")
    private String growthState;

    @Schema(description = "类器官生长情况")
    private String growthDesc;

    @Schema(description = "预计筛药")
    private String plannedDrugScreen;

    @Schema(description = "反馈时间（文本）")
    private String feedbackTime;

}
