package org.dromara.lqg.embed.guard;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 染色的纯函数规则（FLOW:F-EMBED-01.step3，FIELD:t_lqg_embed.stain_types / stain_other）。
 *
 * <p>★ <b>本类无 Spring、无库、无实体</b>：字典的允许值由调用方传进来（service 从
 * {@code DictService.getDictData("lqg_stain_type")} 取），这样规则可以被契约测试直接钉住，
 * 也不会把「字典怎么读」和「组合怎么判」搅在一起。
 *
 * <p>四条口径（ticket §0 口径复述 1 / §2，逐条对 accept 2 核）：
 * <ol>
 *   <li><b>染色是五个按钮</b>：HE / IF / IHC / OTHER / NONE，多选；</li>
 *   <li><b>值必须落在字典 {@code lqg_stain_type} 内</b>：{@code ["PAS"]} 一律拒（accept 2 第 3 段）；</li>
 *   <li><b>NONE 与其余四个互斥</b>：{@code ["NONE","HE"]} 一律拒（accept 2 第 1 段）——
 *       「互斥只在前端做了」正是这条要抓的假绿形态；</li>
 *   <li><b>选 OTHER 必须写具体名称</b>：{@code ["OTHER"]} 不带 {@code stainOther} 一律拒
 *       （accept 2 第 2 段）—— 否则导出时染色一格只有「其他」两个字。</li>
 * </ol>
 *
 * <p>★ <b>落库按固定顺序</b>：{@link #ORDER} = HE, IF, IHC, OTHER, NONE（即字典
 * {@code lqg_stain_type} 的 dict_sort 顺序）。{@code ["IHC","HE"]} 必须落成 {@code "HE,IHC"}
 * —— accept 2 第 7 段断的就是这一格：「落库没按固定顺序排（存成了 IHC,HE）→ 红」。
 * 顺序不是「请求里的顺序」，所以不能原样 {@code String.join}。
 *
 * @author EMBED-MODEL-001
 */
public final class StainRules {

    /**
     * 固定顺序 = 字典 {@code lqg_stain_type} 的五个 value（HE, IF, IHC, OTHER, NONE）。
     *
     * <p>它同时是「合法值清单」与「落库顺序」的唯一来源：{@link #ORDER} 之外的 value 一律拒，
     * 落库一律按这个下标排序。
     */
    public static final List<String> ORDER = List.of("HE", "IF", "IHC", "OTHER", "NONE");

    /**
     * 无染色：与其余四个互斥。
     */
    public static final String NONE = "NONE";

    /**
     * 其他：必须写具体名称。
     */
    public static final String OTHER = "OTHER";

    private StainRules() {
    }

    /**
     * 校验并规范化染色多选。
     *
     * @param raw        请求里的染色数组（可空 = 还没选；元素可含空白 / 重复）
     * @param dictValues 字典 {@code lqg_stain_type} 里当前允许的 value 集合（由 service 传入）
     * @param stainOther 选了 OTHER 时的具体名称（可空）
     * @return 去空、去重、<b>按 {@link #ORDER} 排好序</b>的数组（可空 = 空数组，调用方按 null 落库）
     * @throws ServiceException 值不在字典里 / NONE 与别的并存 / 选了 OTHER 没写名称
     */
    public static List<String> normalize(List<String> raw, Collection<String> dictValues, String stainOther) {
        Set<String> picked = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                String trimmed = value.trim();
                if (!ORDER.contains(trimmed) || (dictValues != null && !dictValues.isEmpty()
                    && !dictValues.contains(trimmed))) {
                    // 报错必须带上收到的那个值（accept 2 第 3 段给的是 PAS）：
                    // 只说「只能是这五个」用户不知道是哪个值被拒了
                    throw new ServiceException("染色「" + trimmed + "」不在字典 lqg_stain_type 里（只能是 "
                        + String.join(" / ", ORDER) + "）");
                }
                picked.add(trimmed);
            }
        }
        if (picked.contains(NONE) && picked.size() > 1) {
            throw new ServiceException("「无染色」与其余染色互斥，不能同时选");
        }
        if (picked.contains(OTHER) && StringUtils.isBlank(stainOther)) {
            throw new ServiceException("选了「其他」必须写具体染色名称");
        }
        List<String> ordered = new ArrayList<>(ORDER.size());
        for (String value : ORDER) {
            if (picked.contains(value)) {
                ordered.add(value);
            }
        }
        return ordered;
    }

    /**
     * 数组 → 落库形态（固定顺序的逗号串）。
     *
     * @param ordered 已排好序的数组（通常是 {@link #normalize} 的返回值）
     * @return 逗号串；空 → {@code null}（「空 = 还没选」，不是空串）
     */
    public static String toCsv(List<String> ordered) {
        if (ordered == null || ordered.isEmpty()) {
            return null;
        }
        return String.join(",", ordered);
    }

    /**
     * 落库形态 → 对外数组。
     *
     * <p>读时按 {@link #ORDER} 再排一次：库里的串可能来自更早的写入路径（或 seed），
     * 对外形状不能依赖写入端一定排过序。
     *
     * @param csv 逗号串（可空）
     * @return 数组（可空串 → 空数组，不是 {@code [""]}）
     */
    public static List<String> fromCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        Set<String> picked = new LinkedHashSet<>();
        for (String piece : csv.split(",")) {
            if (StringUtils.isNotBlank(piece)) {
                picked.add(piece.trim());
            }
        }
        List<String> ordered = new ArrayList<>(picked.size());
        for (String value : ORDER) {
            if (picked.contains(value)) {
                ordered.add(value);
            }
        }
        // 字典外的历史值也原样带出来（读侧不做业务校验，免得一行脏数据把整个列表打挂）
        for (String value : picked) {
            if (!ORDER.contains(value)) {
                ordered.add(value);
            }
        }
        return ordered;
    }

    /**
     * 这组染色里有没有 OTHER（有 → {@code stainOther} 必填，没有 → 落库时置空）。
     */
    public static boolean hasOther(Collection<String> values) {
        return values != null && values.contains(OTHER);
    }

}
