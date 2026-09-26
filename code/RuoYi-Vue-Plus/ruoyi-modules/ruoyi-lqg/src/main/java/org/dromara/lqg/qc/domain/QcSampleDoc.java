package org.dromara.lqg.qc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 样本质控表 t_lqg_qc_sample（REQ-QC-001，FLOW:F-QC-01.step2）。
 *
 * <p>★★ <b>这张表上没有来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 / 内部编号</b>
 * （ticket §0 口径复述 1）：那七项从样本主档 {@code t_lqg_sample} 读时带出、只读展示
 * —— 在质控表里再存一份 = 两个真相源，样本主档改了名字文档上还是旧的。
 * {@code ddl_vs_ssot} 会把这些多出来的列报红。
 *
 * <p>★ <b>{@code patientNo} 是加密列</b>（ADR-0006，FIELD:t_lqg_qc_sample.patient_no）：
 * 实体上写的是<b>密文</b>，加解密由 {@code QcDocService} 用 {@code SampleFieldCipher}
 * 手工做（裸 Base64）—— 与本项目另外两列同源。为什么不挂框架 {@code @EncryptField}
 * 见 {@code SampleFieldCipher} 的类注释（拦截器会写 {@code ENC_} 前缀，与 seed 里的裸
 * Base64 密文两套混用会让 seed 行读出来还是密文）。
 *
 * <p>★ {@code receiveDesc / observeDesc / pretreatDesc} 三段模板原文是<b>新建草稿时的默认值</b>
 * （ticket §0 口径复述 4，逐字照模板）；常量在 {@code QcDocRules}，
 * accept 3 第 1 段逐字比对那三句。
 *
 * @author QC-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_qc_sample")
public class QcSampleDoc extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_sample.id，一个样本一份（部分唯一索引 uk_qc_sample_sample WHERE del_flag='0'）
     */
    private Long sampleId;

    /**
     * 患者编号（<b>密文落库</b>，ADR-0006；读时解密给工作台内部人员看明文）
     */
    private String patientNo;

    /**
     * 取样部位
     */
    private String samplingSite;

    /**
     * 取样方式
     */
    private String samplingMethod;

    /**
     * 临床诊断/既往治疗
     */
    private String clinicalDiagnosis;

    /**
     * 收样描述（新建草稿默认填模板原文）
     */
    private String receiveDesc;

    /**
     * 细胞活率测定附件 FK→sys_oss.oss_id（单独一栏，不走通用附件表）
     */
    private Long viabilityOssId;

    /**
     * 细胞活率测定附件的原始文件名（文档里这一格嵌入附件本身，图标旁边印这个文件名；H 批起做 OLE 嵌入）
     */
    private String viabilityFileName;

    /**
     * 收样原始情况 · 情况描述
     */
    private String origDesc;

    /**
     * 样本观察情况 · 情况描述（新建草稿默认填模板原文）
     */
    private String observeDesc;

    /**
     * 样本预处理情况 · 情况描述（新建草稿默认填模板原文）
     */
    private String pretreatDesc;

    /**
     * draft 草稿 / published 已完成；只有 published 对外可见。
     * <p>★ 本票（QC-MODEL-001）只会建 draft：状态机（完成并同步 / 撤回）在 DOC-PUBLISH-001。
     */
    private String docStatus;

    /**
     * 完成时间
     */
    private Date publishedTime;

    /**
     * 完成人 user_id
     */
    private Long publishedBy;

    /**
     * 软删标志（{@code @TableLogic}：所有 mapper 查询自带 {@code del_flag='0'}）
     */
    @TableLogic
    private String delFlag;

}
