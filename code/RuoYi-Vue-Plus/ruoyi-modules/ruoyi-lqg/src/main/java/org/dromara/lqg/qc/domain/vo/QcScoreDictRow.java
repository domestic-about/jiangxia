package org.dromara.lqg.qc.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 评分字典行（{@code QcScoreDictMapper} 的取值行）：
 * {@code sys_dict_data} 的一条「档位 → 分值」。
 *
 * <p>★ 分值在 {@code remark} 列（ADR 口径：改分值改字典 remark，不改代码）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "评分字典行")
public class QcScoreDictRow {

    @Schema(description = "字典类型，如 lqg_score_pre_culture")
    private String dictType;

    @Schema(description = "档位取值，如 gt80")
    private String dictValue;

    @Schema(description = "该档分值（字符串形态的整数）")
    private String remark;

}
