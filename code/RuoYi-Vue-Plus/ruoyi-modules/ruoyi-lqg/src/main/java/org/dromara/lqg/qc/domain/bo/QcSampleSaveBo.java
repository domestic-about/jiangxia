package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 样本质控表的保存入参（{@code PUT /lqg/qc/{sampleId}/sample-qc}，FLOW:F-QC-01.step2）。
 *
 * <p>★ <b>补丁语义</b>：{@code null} = 这一栏不动（前端只传改过的栏）；要清空一栏传空串。
 * 新建草稿时预填的三段模板原文因此不会被「只传了患者编号」的保存抹掉。
 *
 * <p>★ <b>入参里没有来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 / 内部编号</b>
 * —— 那七项是样本主档的只读字段（ticket §0 口径复述 1），本类根本没有这些字段。
 *
 * <p>★ <b>入参里也没有任何 {@code *_score}</b>：分值只在评分表的入参里，
 * 而且那里也是后端按字典回填（见 {@code QcScoreSaveBo}）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "样本质控表保存")
public class QcSampleSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "患者编号（明文传入，密文落库；null = 不动，空串 = 清空）")
    private String patientNo;

    @Schema(description = "取样部位")
    private String samplingSite;

    @Schema(description = "取样方式")
    private String samplingMethod;

    @Schema(description = "临床诊断/既往治疗")
    private String clinicalDiagnosis;

    @Schema(description = "收样描述")
    private String receiveDesc;

    /**
     * 细胞活率测定附件。
     * <p>★ {@code null} = 不动；{@code 0} = <b>摘掉</b>（同时清 {@code viabilityFileName}）。
     * 用 0 而不是 null 当「摘掉」的哨兵，是为了与「这栏不动」区分开。
     */
    @Schema(description = "细胞活率测定附件 oss_id（null = 不动，0 = 摘掉）")
    private Long viabilityOssId;

    @Schema(description = "细胞活率测定附件的原始文件名（随 viabilityOssId 一起传）")
    private String viabilityFileName;

    @Schema(description = "收样原始情况 · 情况描述")
    private String origDesc;

    @Schema(description = "样本观察情况 · 情况描述")
    private String observeDesc;

    @Schema(description = "样本预处理情况 · 情况描述")
    private String pretreatDesc;

}
