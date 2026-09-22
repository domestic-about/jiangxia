package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 往图片位里加一张图（{@code POST /lqg/qc/{sampleId}/{docType}/image}，
 * doc/api-contract.md 第 81 行：{@code {slot, ossId}}）。
 *
 * <p>★ 归属（slot 属不属于该文档类型）与每位至多 3 张的校验在
 * {@code QcDocRules} / {@code QcDocService}，被拒时<b>库里不变</b>。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "图片位新增一张图")
public class DocImageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "图片位：orig / observe / pretreat / organoid_observe")
    private String slot;

    @Schema(description = "原图 oss_id（预览图由后端判定是否另存）")
    private Long ossId;

}
