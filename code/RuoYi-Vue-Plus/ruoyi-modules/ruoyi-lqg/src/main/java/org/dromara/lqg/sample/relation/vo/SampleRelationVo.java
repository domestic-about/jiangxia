package org.dromara.lqg.sample.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 样本行上的「石蜡包埋 / 冻存」关联数（工作台样本两页的关联列；Kevin 2026-09-24 本机验收
 * 「四种表之间的关系看着有点乱」）。
 *
 * <p>★ <b>读时计算、整页一条查询</b>（{@code SampleRelationService.countsOf}），与切片染色提示
 * （{@code SampleHintVo}）同一个做法；样本表上不落任何冗余列。
 *
 * <p>★ <b>三个数的口径各自对准一张目标页</b>，点过去看到的条数与这里一致：
 * <ul>
 *   <li>蜡块数 —— 不在这里，沿用 {@code hint.blockCount}（已核验有效、未软删的石蜡块，
 *       与 {@code EmbedChildrenChecker} 同源），不另起一份口径；</li>
 *   <li>{@link #pendingEmbedCount} —— 合作单位送来、还待核验的石蜡包埋送样
 *       （= 石蜡包埋页按这个样本 + 待核验筛出来的条数）；</li>
 *   <li>{@link #cryoBatchCount} —— 未软删的冻存批次（= 冻存管理页按这个样本筛出来的条数；
 *       与 {@code CryoChildrenChecker} 同一判据：冻存批次没有核验状态，未删即生效）。</li>
 * </ul>
 *
 * <p>★ 每一行都有这个对象，没有关联记录的行是零值（不是 null）。只挂在列表上（与 {@code hint} 一样），
 * 详情与导出不带。只进内部 {@code SampleVo}，外部 VO 没有它（冻存只有内部人员能看）。
 */
@Data
@Schema(description = "样本的石蜡包埋 / 冻存关联数（读时计算）")
public class SampleRelationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "待核验的石蜡包埋送样数（合作单位送来、还没核验；软删的不算）")
    private Integer pendingEmbedCount;

    @Schema(description = "冻存批次数（软删的不算）")
    private Integer cryoBatchCount;

    public SampleRelationVo() {
    }

    public SampleRelationVo(Integer pendingEmbedCount, Integer cryoBatchCount) {
        this.pendingEmbedCount = pendingEmbedCount;
        this.cryoBatchCount = cryoBatchCount;
    }

    /** 零值：名下没有待核验送样、也没有冻存批次 */
    public static SampleRelationVo empty() {
        return new SampleRelationVo(0, 0);
    }

}
