package org.dromara.lqg.sample.relation.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「石蜡包埋 / 冻存」关联数的原始结果行：一个样本一行（{@code SampleRelationMapper.selectCounts}）。
 *
 * <p>与 {@link SampleRelationVo} 分开的理由同 {@code SampleHintRow}：这里是 SQL 的列形状（带 GROUP 键
 * {@code sampleId}），VO 是挂在样本行上的对外形状（不重复带 id）。
 */
@Data
public class SampleRelationRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 样本 id */
    private Long sampleId;

    /** 这个样本名下<b>待核验、未软删</b>的石蜡包埋送样数（合作单位送来、还没核验的） */
    private Integer pendingEmbedCount;

    /** 这个样本名下<b>未软删</b>的冻存批次数 */
    private Integer cryoBatchCount;

}
