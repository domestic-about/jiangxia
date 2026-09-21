package org.dromara.lqg.ext.guard;

import org.dromara.common.core.utils.StringUtils;

/**
 * 对外展示的掩码规则（纯函数，无 Spring 无库 —— 契约测试可直接钉）。
 *
 * <p>★ <b>掩码只作用于「列表」</b>：外部看<b>本人与同组</b>的记录，供体姓名在列表上打码
 * （姓 + 两个星号），在<b>详情</b>里给全名 —— 同组的人本来就该认得自己那批样本。
 * 「全部打码」与「全部不打码」都不采纳（ticket §2.3 / FLOW:F-EXT-01.step3）。
 *
 * <p>★ 打码值是 {@code donorNameMasked} 这个<b>独立字段</b>，原始列 {@code donorName} 不在
 * {@code ExtSampleVo} 里 —— 不是「返回全名再由前端打码」，那样全名已经过网线了。
 * 数据在库里是密文（ADR-0006），本类只处理<b>解密之后</b>的明文。
 *
 * @author AUTH-EXT-001
 */
public final class MaskRules {

    private MaskRules() {
    }

    /**
     * 供体姓名掩码：首字（用 code point 取，别按 char 切，免得把代理对切坏）+ {@code "**"}。
     *
     * <ul>
     *   <li>{@code null} / 空白 → {@code null}（organoid 样本本来就没有供体姓名）；</li>
     *   <li>单字 → 该字 + {@code "**"}（{@code "王"} → {@code "王**"}）—— 不因为短就漏掉掩码。</li>
     * </ul>
     */
    public static String maskDonorName(String donorName) {
        if (StringUtils.isBlank(donorName)) {
            return null;
        }
        String value = donorName.trim();
        int firstEnd = value.offsetByCodePoints(0, 1);
        return value.substring(0, firstEnd) + "**";
    }

}
