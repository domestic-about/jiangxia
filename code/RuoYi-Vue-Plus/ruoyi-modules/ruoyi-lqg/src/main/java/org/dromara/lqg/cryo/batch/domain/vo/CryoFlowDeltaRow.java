package org.dromara.lqg.cryo.batch.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条 {@code GROUP BY batch_id} 的原始结果行（CRYO-MODEL-001）。
 *
 * <p>刻意与 {@link CryoBatchVo} 分开：这里是 <b>SQL 的列形状</b>（一个批次一行、只有
 * 「流水 delta 之和」这一个数），{@link CryoBatchVo} 是对外形状（剩余 = 初始支数 + 这个数）。
 * 合并成一个类就得在 VO 上挂一个「只给 mapper 用」的字段，对外会多一个键。
 *
 * @author CRYO-MODEL-001
 */
@Data
public class CryoFlowDeltaRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批次 id（{@code GROUP BY} 的键） */
    private Long batchId;

    /** 这个批次<b>未删</b>流水的 delta 之和（没有流水时 {@code COALESCE} 成 0，但那样也不会出行） */
    private Integer totalDelta;

}
