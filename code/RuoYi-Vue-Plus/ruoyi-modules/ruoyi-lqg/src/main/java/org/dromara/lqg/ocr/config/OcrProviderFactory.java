package org.dromara.lqg.ocr.config;

import lombok.extern.slf4j.Slf4j;
import org.dromara.lqg.ocr.domain.vo.OcrStatusVo;
import org.dromara.lqg.ocr.guard.OcrProviderGuard;
import org.dromara.lqg.ocr.provider.NoneOcrProvider;
import org.dromara.lqg.ocr.provider.OcrProvider;
import org.dromara.lqg.ocr.provider.PaidOcrProvider;
import org.dromara.lqg.ocr.provider.StubOcrProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 把 {@code lqg.ocr.provider} 配置解析成**运行时那一个** {@link OcrProvider}
 * （ADR-0007：一个方案一个实现类、配置切换）。
 *
 * <p>★ provider <b>每次请求现选</b>，不缓存进字段：{@code lqg.ocr.provider} /
 * {@code lqg.ocr.paid-enabled} 改了、下一次请求就生效（与 {@code CryoOverdueProperties} 同一口径）。
 *
 * <p>★ 选择口径全在 {@link OcrProviderGuard}（纯函数、可单测）；本类只做两件机器活：
 * 把 Spring 注入的 provider 收成「bean 名 → 实例」、把 stub 的用例头透传给测试桩。
 *
 * @author OCR-IMPL-001
 */
@Slf4j
@Component
public class OcrProviderFactory {

    private final OcrProperties properties;

    /**
     * 所有可用实现（Spring 按 bean 名注入；{@code none} 一定在 —— {@link NoneOcrProvider} 是 {@code @Component}）。
     */
    private final Map<String, OcrProvider> providers;

    public OcrProviderFactory(OcrProperties properties, Map<String, OcrProvider> providers) {
        this.properties = properties;
        this.providers = Map.copyOf(providers);
    }

    /**
     * 识别（统一入口；调用方已校验图片大小 / 格式）。
     *
     * @param image    图片字节（只在内存里，识别完即弃）
     * @param stubCase 请求头 {@code X-Ocr-Stub-Case} 的值（非测试桩实现忽略它）
     * @return 原始文本行
     */
    public List<String> recognize(byte[] image, String stubCase) {
        OcrProvider provider = resolve();
        if (provider instanceof StubOcrProvider stub) {
            return stub.recognize(stubCase);
        }
        return provider.recognize(image);
    }

    /**
     * 当前请求实际会用的 provider（每次现选）。
     */
    public OcrProvider resolve() {
        return OcrProviderGuard.select(properties.getProvider(), properties.isPaidEnabled(), providers);
    }

    /**
     * 当前生效的 provider 名（{@code GET /lqg/ocr/status} 的 {@code provider}）。
     *
     * <p>返回<b>实际生效</b>的实现名而不是配置里的字符串：配置写成不存在的方案时这里如实报
     * {@code none}（否则工作台会显示一个根本不存在的 provider）。
     */
    public String providerName() {
        return nameOf(resolve());
    }

    /**
     * 状态视图（工作台）。
     */
    public OcrStatusVo status() {
        OcrStatusVo vo = new OcrStatusVo();
        vo.setProvider(providerName());
        // paidEnabled 报的是**配置真值**，不是「当前选中的是不是付费实现」：
        // 工作台要能看出「通道开着但没配实现」这种半吊子状态。
        vo.setPaidEnabled(properties.isPaidEnabled());
        return vo;
    }

    /**
     * 可用实现名（含 none），给启动护栏与排查用。
     */
    public Set<String> availableNames() {
        return new TreeSet<>(providers.keySet());
    }

    /**
     * 可用实现里按次收费的那些名字。
     */
    public Set<String> paidNames() {
        Set<String> paid = new TreeSet<>();
        providers.forEach((name, provider) -> {
            if (provider instanceof PaidOcrProvider) {
                paid.add(name);
            }
        });
        return paid;
    }

    /**
     * 实现名：{@code none} / {@code stub} 是**协议名**（配置值、请求头、bean 名三处逐字一致），
     * 其余实现用类名首字母小写（Spring 默认 bean 名）。
     */
    public static String nameOf(OcrProvider provider) {
        if (provider instanceof NoneOcrProvider) {
            return NoneOcrProvider.NAME;
        }
        if (provider instanceof StubOcrProvider) {
            return StubOcrProvider.NAME;
        }
        String simple = provider.getClass().getSimpleName();
        return simple.isEmpty() ? "ocrProvider" : Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

}
