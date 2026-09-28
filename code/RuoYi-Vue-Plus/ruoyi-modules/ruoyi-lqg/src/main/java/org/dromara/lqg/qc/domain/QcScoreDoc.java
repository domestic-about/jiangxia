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
 * 类器官质量评分表 t_lqg_qc_score（REQ-QC-007，FLOW:F-QC-01.step4）。
 *
 * <p>★★ <b>四个 {@code *_score} 是「该档分值的快照」，由后端按字典 remark 回填</b>
 * （FIELD:t_lqg_qc_score.total_score + ADR 口径）：前端传来的分值一律忽略 ——
 * 见 {@code QcScoreDictionary}。写死在 Java 枚举里会让「改分值改字典 remark」
 * 这句话失效（accept 2 第 4 段拿库里落下的分值与字典表 JOIN 对账）。
 *
 * <p>★★ <b>{@code total_score} = 四项分值之和，任一项未选则为空（NULL，不是 0）</b>。
 * 注意 0 分是<b>合法档位</b>：{@code lqg_score_culture_days} 的 {@code gt14}（培养天数 &gt;14d）
 * 和 {@code lqg_score_count} 的 {@code lt100} 都是 0 分 —— 用 {@code if (score)} 判断
 * 「有没有选」会把 0 分档当成未选（accept 2 的 counterfeit 点名的就是这个）。
 *
 * <p>★ 本表<b>没有图片位</b>（FIELD:t_lqg_doc_image.slot 的取值里没有评分表，
 * {@code QcDocRules.SLOTS_BY_DOC_TYPE} 给 organoid_score 的是空集合）。
 * 评分表可以挂通用附件。
 *
 * @author QC-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_qc_score")
public class QcScoreDoc extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_sample.id，一个样本一份（部分唯一索引 uk_qc_score_sample WHERE del_flag='0'）
     */
    private Long sampleId;

    /**
     * 培养前样本评分档位 lt40(8) / 40to80(16) / gt80(20)，字典 lqg_score_pre_culture
     */
    private String preCultureLevel;

    /**
     * 培养天数档位 gt14(0) / le14(10)，字典 lqg_score_culture_days
     */
    private String cultureDaysLevel;

    /**
     * 类器官数量档位 lt100(0) / 100to1500(10) / 1500to4000(25) / gt4000(40)，字典 lqg_score_count
     */
    private String organoidCountLevel;

    /**
     * 类器官直径档位 lt30(10) / 30to100(20) / gt100(30)，字典 lqg_score_diameter
     */
    private String diameterLevel;

    /**
     * 该档分值快照（后端按字典 remark 回填，前端传来的分值一律忽略）
     */
    private Integer preCultureScore;

    /**
     * 该档分值快照
     */
    private Integer cultureDaysScore;

    /**
     * 该档分值快照
     */
    private Integer organoidCountScore;

    /**
     * 该档分值快照
     */
    private Integer diameterScore;

    /**
     * 合计 = 四项分值之和（任一项未选则为空）；<b>不出「偏差 / 中等 / 良好」结论</b>，
     * 那是文档页脚的固定注释。
     */
    private Integer totalScore;

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
