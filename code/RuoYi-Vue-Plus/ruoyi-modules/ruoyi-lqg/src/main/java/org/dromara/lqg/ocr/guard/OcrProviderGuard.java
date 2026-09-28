package org.dromara.lqg.ocr.guard;

import org.dromara.lqg.ocr.provider.NoneOcrProvider;
import org.dromara.lqg.ocr.provider.OcrProvider;
import org.dromara.lqg.ocr.provider.PaidOcrProvider;
import org.dromara.lqg.ocr.provider.StubOcrProvider;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 识别 provider 的**选择口径**（纯函数，与 Spring 无关 —— 所以 {@code OcrProviderSelectionTest}
 * 能直接构造数据跑，不需要起容器）。
 *
 * <p>三条口径（ADR-0007、ticket §2）：
 * <ol>
 *   <li><b>按次收费的实现必须同时满足 {@code lqg.ocr.paid-enabled=true} 才会被选中</b>
 *       （用 {@link PaidOcrProvider} 标记接口判类，不按 provider 名字硬编码）；</li>
 *   <li>付费通道开着但配的不是付费实现 → <b>拒绝启动</b>（配错了别装作没事）；</li>
 *   <li>{@code stub} 只允许 dev / test：prod（或压根没声明 profile）配成 stub → <b>拒绝启动</b>
 *       （与 {@code MockLoginGuard} 同一思路）。</li>
 * </ol>
 *
 * <p>★ 运行期与启动期分工：{@link #select} 任何一条不满足都回落 {@link NoneOcrProvider}
 * （前端提示手填，不 500）；{@link #configError} 把同一批配置问题变成启动期错误文案。
 *
 * @author OCR-IMPL-001
 */
public final class OcrProviderGuard {

    /**
     * 允许打开 stub 的 profile（小写比较）。
     */
    private static final Set<String> STUB_ALLOWED_PROFILES = Set.of("dev", "test");

    /**
     * 非法 profile：出现即视为生产（同 {@code MockLoginGuard}：认 prod，staging / uat 由部署方拼进来）。
     */
    private static final String PROD_PROFILE = "prod";

    private OcrProviderGuard() {
    }

    /**
     * 当前是否允许选中付费 provider：
     * 开关必须是 true，且配置指向的实现确实声明了自己按次收费。
     */
    public static boolean paidSelectable(String providerName, boolean paidEnabled, Map<String, OcrProvider> providers) {
        if (!paidEnabled) {
            return false;
        }
        return providers.get(name(providerName)) instanceof PaidOcrProvider;
    }

    /**
     * 选中当前应使用的 provider。
     *
     * @param providerName 配置里的 {@code lqg.ocr.provider}
     * @param paidEnabled  {@code lqg.ocr.paid-enabled}
     * @param providers    可用实现（名 → 实例；应含 {@code none}）
     * @return 选中的实例；配了不可用 / 不该用的东西时 → {@code none}
     */
    public static OcrProvider select(String providerName, boolean paidEnabled, Map<String, OcrProvider> providers) {
        String target = name(providerName);
        OcrProvider candidate = providers.get(target);
        if (candidate == null) {
            // 配了一个不存在的实现：运行期回落手填（启动期由 configError 拦）
            return fallback(providers);
        }
        if (candidate instanceof PaidOcrProvider && !paidEnabled) {
            // 付费通道关着：按次收费的实现一个都不许选中（合同第二条第 5 款）
            return fallback(providers);
        }
        return candidate;
    }

    /**
     * 启动期护栏：这套配置合不合法。返回空 = 合法。
     *
     * @param providerName   配置里的 {@code lqg.ocr.provider}
     * @param paidEnabled    {@code lqg.ocr.paid-enabled}
     * @param availableNames 当前可用的实现名（含 {@code none}）
     * @param paidNames      可用实现里**按次收费**的那些名字
     * @param activeProfiles 运行期真实 profiles（null / 空 = 没声明 profile，按不安全处理）
     * @return 给运维看的中文错误；合法时为空
     */
    public static Optional<String> configError(String providerName, boolean paidEnabled,
                                               Set<String> availableNames, Set<String> paidNames,
                                               String[] activeProfiles) {
        String target = name(providerName);
        Set<String> paid = paidNames == null ? Set.of() : paidNames;
        if (paidEnabled && !paid.contains(target)) {
            return Optional.of("lqg.ocr.paid-enabled=true 但 lqg.ocr.provider=" + target
                + " 不是按次收费的实现（按次收费：" + sorted(paid) + "）：付费通道必须与付费实现配套"
                + "（ADR-0007、合同第二条第 5 款）。已拒绝启动。");
        }
        if (!availableNames.contains(target)) {
            return Optional.of("lqg.ocr.provider=" + target + " 没有对应实现（可用：" + sorted(availableNames)
                + "）：配了一个不存在的方案，识别会静默回落到手填。已拒绝启动。");
        }
        if (StubOcrProvider.NAME.equals(target) && isProd(activeProfiles)) {
            return Optional.of("lqg.ocr.provider=" + StubOcrProvider.NAME + " 与 profile="
                + Arrays.toString(activeProfiles) + " 冲突：测试桩只允许存在于 dev / test（ADR-0007）。已拒绝启动。");
        }
        return Optional.empty();
    }

    /**
     * 规范化的 provider 名（空 → {@code none}）。
     */
    static String name(String providerName) {
        return providerName == null || providerName.isBlank() ? NoneOcrProvider.NAME : providerName.strip();
    }

    /**
     * 回落实现：优先用容器里的 none bean，没有就现造一个。
     */
    private static OcrProvider fallback(Map<String, OcrProvider> providers) {
        return providers.getOrDefault(NoneOcrProvider.NAME, new NoneOcrProvider());
    }

    /**
     * profile 里有没有 prod（或压根没声明 profile）—— 两种情况都当生产看待。
     */
    static boolean isProd(String[] activeProfiles) {
        if (activeProfiles == null) {
            return true;
        }
        boolean declared = false;
        for (String profile : activeProfiles) {
            if (profile == null || profile.isBlank()) {
                continue;
            }
            declared = true;
            if (PROD_PROFILE.equals(profile.strip().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return !declared;
    }

    /**
     * 集合的稳定排序串（错误文案用）。
     */
    private static String sorted(Set<String> names) {
        return new TreeSet<>(names == null ? Set.of() : names).toString();
    }

    /**
     * 「这个 profile 集合是否允许开 stub」—— 与 {@link #isProd} 反义，供测试与排查直读。
     */
    public static boolean stubAllowed(String[] activeProfiles) {
        return !isProd(activeProfiles) && Arrays.stream(activeProfiles)
            .filter(p -> p != null && !p.isBlank())
            .map(p -> p.strip().toLowerCase(Locale.ROOT))
            .anyMatch(STUB_ALLOWED_PROFILES::contains);
    }

}
