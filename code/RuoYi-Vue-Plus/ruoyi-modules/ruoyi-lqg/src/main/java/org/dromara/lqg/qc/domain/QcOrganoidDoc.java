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
 * 类器官质控表 t_lqg_qc_organoid（REQ-QC-005，FLOW:F-QC-01.step3）。
 *
 * <p>★★ <b>五栏都是自由文本，模板没给选项就不擅自做成下拉</b>（ticket §0 口径复述 3）：
 * 尤其 {@code formed_time}（形成类器官时间）与 {@code feedback_time}（反馈时间）是
 * <b>VARCHAR 文本列、不是 DATE</b> —— 实验员要写「约第 5 天」（seed 的 9000005101 就是
 * 「第 5 天」）。accept 1 第 3 段直接查 information_schema 断这两列是
 * {@code character varying}。
 *
 * @author QC-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_qc_organoid")
public class QcOrganoidDoc extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_sample.id，一个样本一份（部分唯一索引 uk_qc_organoid_sample WHERE del_flag='0'）
     */
    private Long sampleId;

    /**
     * 形成类器官时间（文本：日期选择器选了就是 yyyy-MM-dd，也允许手改成「约第 5 天」）
     */
    private String formedTime;

    /**
     * 生长状态
     */
    private String growthState;

    /**
     * 类器官生长情况
     */
    private String growthDesc;

    /**
     * 预计筛药（纯文本）
     */
    private String plannedDrugScreen;

    /**
     * 反馈时间（文本，同 formedTime）
     */
    private String feedbackTime;

    /**
     * draft / published，规则同 {@link QcSampleDoc#getDocStatus()}。
     * <p>★ 本票只建 draft。
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
     * 软删标志（{@code @TableLogic}）
     */
    @TableLogic
    private String delFlag;

}
