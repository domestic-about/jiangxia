package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 挂一个通用附件（{@code POST /lqg/qc/{sampleId}/{docType}/attachment}）。
 *
 * <p>★ doc/api-contract.md 第 81 行给的形状是 {@code {ossId, fileName}}；
 * {@code fileSize} <b>可选</b>（给了就校验 ≤ 50MB 并落库，没给落 0）
 * —— {@code sys_oss} 本身不存字节数，接口契约里也没有第三个键。
 * 这一处是<b>契约与「单个 ≤ 50MB」之间的缝</b>，已记 WARN。
 *
 * <p>★ 样本质控表的「细胞活率测定」附件<b>不走这里</b>（它是表上单独一栏，
 * 见 {@code QcSampleSaveBo.viabilityOssId}）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "通用附件新增")
public class DocAttachmentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文件 oss_id")
    private Long ossId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "字节数（可选；给了就校验 ≤ 50MB）")
    private Integer fileSize;

}
