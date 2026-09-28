package org.dromara.lqg.embed.guard;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * marker 表达的纯函数规则（FLOW:F-EMBED-01.step3，FIELD:t_lqg_embed_marker.expression）。
 *
 * <p>每条 marker 的 {@code expression} 是<b>按钮单选</b>：negative（阴性）/ weak（弱表达）/
 * strong（强表达），必须落在字典 {@code lqg_marker_expr} 内；{@code markerName} 可空
 * （只记表达情况、不写名称也允许）。
 *
 * <p>与 {@link StainRules} 同款：无 Spring、无库，字典的允许值由调用方传进来。
 *
 * @author EMBED-MODEL-001
 */
public final class MarkerExprRules {

    /**
     * 三个表达（= 字典 {@code lqg_marker_expr} 的 value 与顺序）。
     */
    public static final List<String> ALLOWED = List.of("negative", "weak", "strong");

    private MarkerExprRules() {
    }

    /**
     * 校验并修剪一条 marker 的表达。
     *
     * @param raw        请求里的表达
     * @param dictValues 字典 {@code lqg_marker_expr} 的 value 集合（空集合 = 只按 {@link #ALLOWED} 校验）
     * @return 修剪后的表达
     * @throws ServiceException 空 / 字典外的值
     */
    public static String normalize(String raw, Collection<String> dictValues) {
        String value = StringUtils.isBlank(raw) ? null : raw.trim();
        if (value == null) {
            throw new ServiceException("marker 的表达不能为空（阴性 / 弱表达 / 强表达 三选一）");
        }
        boolean known = ALLOWED.contains(value)
            && (dictValues == null || dictValues.isEmpty() || dictValues.contains(value));
        if (!known) {
            throw new ServiceException("marker 表达只能是 negative（阴性）/ weak（弱表达）/ strong（强表达），"
                + "收到「" + value + "」");
        }
        return value;
    }

}
