package org.dromara.lqg.qc.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 样本质控表（文档本体 + 图片位 + 通用附件），{@code GET /lqg/qc/{sampleId}} 的
 * {@code data.sampleQc}。
 *
 * <p>★ {@code patientNo} 是<b>明文</b>（库里的密文由 service 解密后塞进 VO）：
 * 工作台内部人员要对着它编辑（ADR-0006）。
 *
 * <p>★ {@code images} <b>按 slot 分组</b>：{@code {"orig":[…], "observe":[…], "pretreat":[…]}}，
 * 组内按 {@code sort} 升序。没有图的位是空列表（不是 null）。
 *
 * <p>★ {@code viabilityOssId / viabilityFileName} 是表上<b>单独一栏</b>的细胞活率测定附件
 * —— 它<b>不在</b> {@code attachments} 里（那是通用附件）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "样本质控表")
public class QcSampleDocVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "样本 id")
    private Long sampleId;

    @Schema(description = "患者编号（明文；库里是密文）")
    private String patientNo;

    @Schema(description = "取样部位")
    private String samplingSite;

    @Schema(description = "取样方式")
    private String samplingMethod;

    @Schema(description = "临床诊断/既往治疗")
    private String clinicalDiagnosis;

    @Schema(description = "收样描述")
    private String receiveDesc;

    @Schema(description = "细胞活率测定附件 oss_id（单独一栏，不在 attachments 里）")
    private Long viabilityOssId;

    @Schema(description = "细胞活率测定附件的原始文件名")
    private String viabilityFileName;

    @Schema(description = "收样原始情况 · 情况描述")
    private String origDesc;

    @Schema(description = "样本观察情况 · 情况描述")
    private String observeDesc;

    @Schema(description = "样本预处理情况 · 情况描述")
    private String pretreatDesc;

    @Schema(description = "draft / published")
    private String docStatus;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    @Schema(description = "完成人 user_id")
    private Long publishedBy;

    @Schema(description = "图片位（按 slot 分组，组内按 sort 升序）")
    private Map<String, List<DocImageVo>> images;

    @Schema(description = "通用附件（按 sort 升序；细胞活率测定附件不在其中）")
    private List<DocAttachmentVo> attachments;

}
