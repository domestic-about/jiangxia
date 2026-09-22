package org.dromara.lqg.sample.hint;

import java.util.List;
import java.util.TreeSet;

/**
 * 「切片染色提示」的染色口径 —— <b>纯函数</b>（SAMPLE-HINT-001 / FLOW:F-SAMPLE-02.step4）。
 *
 * <p>★ 这段是整张票最容易做反的一处，逐条对着权威核：
 * <ol>
 *   <li><b>{@code NONE} 不是一种染色</b>：库里 {@code stain_types} 是逗号分隔的
 *       {@code lqg_stain_type} 值（{@code HE,IF,IHC,OTHER / NONE}），{@code NONE} 表示
 *       「这一块做过切片、明确没染色」。把它当作一种染色带出去 → seed 的 1004
 *       （一块、{@code section_time} 非空、{@code stain_types='NONE'}）会变成
 *       {@code ["NONE"]}，accept 1 的 {@code [1,true,[]]} 立刻红；</li>
 *   <li><b>并集 + 去重 + 按字典顺序</b>：一个样本名下多块各有各的染色，提示要的是
 *       「这个样本做过的染色种类」。顺序取字典序（{@code TreeSet} 的自然序）而不是
 *       落库时的固定顺序 —— 各块之间的顺序在库里没有意义，只有并集的元素顺序才是稳定的；</li>
 *   <li><b>空 / 脏值一律忽略</b>：{@code null}、空串、多写的逗号、首尾空白都不算一种染色
 *       （{@code stain_types} 可空，{@code STRING_AGG} 在整列都是空时回 {@code null}）。</li>
 * </ol>
 *
 * <p>★ 为什么不复用 {@code org.dromara.lqg.embed.guard.StainRules}：那个是<b>写侧</b>的口径
 * （五个按钮的固定顺序、NONE 与其余互斥、OTHER 必备名称、字典白名单）。此处是<b>读侧</b>的
 * 一步聚合，两者判据不同（写侧要拦非法输入，读侧只对已落库的值做并集），混在一起会让
 * 改一处动两处。字典白名单由写侧保证，读侧不再重校验（历史脏值原样带出，与导出同口径）。
 *
 * @author SAMPLE-HINT-001
 */
public final class StainHintRules {

    /** 「无染色」的字典值：并集里必须被去掉（权威 FIELD:t_lqg_embed.stain_types）。 */
    public static final String NONE = "NONE";

    private StainHintRules() {
    }

    /**
     * 逗号分隔的染色串 → 去 {@code NONE}、去重、按字典顺序的并集。
     *
     * @param stainCsv 各块 {@code stain_types} 用逗号拼起来的串（可空 / 可有空元素）
     * @return 不可变列表；没有任何染色时是空列表（<b>不是 null</b> —— 前端会
     *         {@code .stains.map(...)}）
     */
    public static List<String> union(String stainCsv) {
        if (stainCsv == null || stainCsv.isBlank()) {
            return List.of();
        }
        TreeSet<String> kinds = new TreeSet<>();
        for (String part : stainCsv.split(",")) {
            String value = part.trim();
            if (value.isEmpty() || NONE.equals(value)) {
                continue;
            }
            kinds.add(value);
        }
        return List.copyOf(kinds);
    }

}
