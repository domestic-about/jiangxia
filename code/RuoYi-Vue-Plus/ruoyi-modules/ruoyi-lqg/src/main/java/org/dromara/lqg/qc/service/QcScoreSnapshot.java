package org.dromara.lqg.qc.service;

import lombok.Data;

/**
 * 一次评分保存算出来的四个分值快照 + 合计（读时算，不缓存）。
 *
 * <p>★ <b>任一字段为 {@code null} 的语义</b>：该项<b>没选</b> → 落库 NULL。
 * 「选了 0 分档」是 {@code 0}（不是 null）—— 用 {@code Integer} 而不是 {@code int}
 * 就是为了把这两件事分开（accept 2 的 counterfeit 点名 {@code if (score)} 那种写法）。
 *
 * @author QC-MODEL-001
 */
@Data
public class QcScoreSnapshot {

    /** 培养前样本评分（没选 → null）。 */
    private Integer preCultureScore;

    /** 培养天数（{@code gt14} 是 0 分，不是「没选」）。 */
    private Integer cultureDaysScore;

    /** 类器官数量（{@code lt100} 是 0 分，不是「没选」）。 */
    private Integer organoidCountScore;

    /** 类器官直径。 */
    private Integer diameterScore;

    /** 合计 = 四项之和；<b>任一项没选 → null</b>。 */
    private Integer totalScore;

    /**
     * 四项都选了 → 求和；否则合计为空。
     */
    public static QcScoreSnapshot of(Integer preCulture, Integer cultureDays,
                                     Integer organoidCount, Integer diameter) {
        QcScoreSnapshot snapshot = new QcScoreSnapshot();
        snapshot.setPreCultureScore(preCulture);
        snapshot.setCultureDaysScore(cultureDays);
        snapshot.setOrganoidCountScore(organoidCount);
        snapshot.setDiameterScore(diameter);
        if (preCulture != null && cultureDays != null && organoidCount != null && diameter != null) {
            snapshot.setTotalScore(preCulture + cultureDays + organoidCount + diameter);
        }
        return snapshot;
    }

}
