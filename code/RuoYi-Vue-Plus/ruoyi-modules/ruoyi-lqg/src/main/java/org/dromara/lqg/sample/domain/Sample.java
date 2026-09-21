package org.dromara.lqg.sample.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Date;

/**
 * 样本主档 t_lqg_sample（ADR-0010，FLOW:F-SAMPLE-02.step1 / step2）。
 *
 * <p>★ <b>一张表承载两种收样记录</b>：{@code sample_kind='tissue'} 是「样本记录信息表」，
 * {@code sample_kind='organoid'} 是「类器官收样记录」；「类器官类型」只是 organoid 类才填的一列。
 * 不照两份模板建两张表 —— 那样「内部编号贯穿到底」「外部提交实时同步到该表」「所有样本一张表」
 * 三句话没法同时成立（ADR-0010 的 rejected_values）。
 *
 * <p>★ {@code internalNo} <b>手填、不自动生成</b>（编号规则是甲方自己的，合同约定由甲方提供）：
 * 列可空（外部刚提交、还没核验的样本没有；seed 里 1002/1003/1007/1010 就是 NULL），
 * 唯一性靠部分唯一索引 {@code uk_sample_internal_no WHERE del_flag='0'} → 软删后同一个编号可重用。
 *
 * <p>★ {@code donorName} / {@code hospitalNo} 的加密（ADR-0006）**不走框架 {@code @EncryptField} 拦截器**，
 * 见 {@code SampleFieldCipher}：拦截器写库时会加 {@code ENC_} 前缀，而 seed 与 ADR-0006 的既有密文是
 * <b>裸 Base64</b>；两套混用会让 seed 行读出来还是密文。列定义权威：doc/authority/field-ssot.yaml。
 *
 * @author SAMPLE-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_sample")
public class Sample extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 送检单号 'SJ' + lpad(nextval('seq_lqg_submit_no'), 8, '0')；服务端取号，不可改
     */
    private String submitNo;

    /**
     * tissue 组织样本（样本记录信息表）/ organoid 类器官（类器官收样记录）
     */
    private String sampleKind;

    /**
     * internal / external：提交当时按提交人身份落库，之后不随账号升降级而变
     */
    private String submitSource;

    /**
     * 提交人 user_id（外部可见范围按它算）
     */
    private Long submitterId;

    /**
     * pending 待核验 / valid 有效 / invalid 无效；内部录入直接 valid
     */
    private String verifyStatus;

    /**
     * 核验人 user_id
     */
    private Long verifyBy;

    /**
     * 核验时间
     */
    private Date verifyTime;

    /**
     * 判无效的原因（外部可见）
     */
    private String invalidReason;

    /**
     * FK→t_lqg_source_unit.id；自填单位名时为空
     */
    private Long sourceUnitId;

    /**
     * 来源单位名称（导出用；选了单位就存单位名快照）
     */
    private String sourceUnitName;

    /**
     * 供体姓名（加密落库；只支持精确查询；organoid 类可空）
     */
    private String donorName;

    /**
     * 性别 male / female / unknown
     */
    private String gender;

    /**
     * 年龄（文本：56 / 3月龄，模板没限定单位）
     */
    private String age;

    /**
     * 住院号（加密落库；只支持精确查询）
     */
    private String hospitalNo;

    /**
     * 组织类型（tissue 类必填；自由文本，联想词来自字典 lqg_hint_tissue_type）
     */
    private String tissueType;

    /**
     * 类器官类型（organoid 类必填；自由文本，联想词来自字典 lqg_hint_organoid_type）
     */
    private String organoidType;

    /**
     * 有无病理 Y / N
     */
    private String hasPathology;

    /**
     * 收样日期
     */
    private LocalDate receiveDate;

    /**
     * 内部编号：内部手填、全库唯一、贯穿到冻存；pending 的外部样本为空
     */
    private String internalNo;

    /**
     * 有无固定 Y 有 / N 无（按钮）
     */
    private String isFixed;

    /**
     * 处理时间
     */
    private Date processTime;

    /**
     * 质控表 Y 有 / N 无（按钮，手点，不自动推导）
     */
    private String hasQcSheet;

    /**
     * 细胞活率报告 Y 有 / N 无（按钮，手点）
     */
    private String hasViabilityReport;

    /**
     * 操作人（姓名文本，默认带当前登录人，可改）
     */
    private String operatorName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 软删标志（{@code @TableLogic}：所有 mapper 查询自带 {@code del_flag='0'}，软删行永远查不到）
     */
    @TableLogic
    private String delFlag;

}
