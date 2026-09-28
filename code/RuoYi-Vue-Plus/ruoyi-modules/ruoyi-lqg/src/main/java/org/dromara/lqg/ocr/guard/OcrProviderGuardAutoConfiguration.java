package org.dromara.lqg.ocr.guard;

import lombok.extern.slf4j.Slf4j;
import org.dromara.lqg.ocr.config.OcrProviderFactory;
import org.dromara.lqg.ocr.config.OcrProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 识别 provider 护栏的**启动接线**：用运行期真实的 profile 与配置值调用
 * {@link OcrProviderGuard#configError}，不合法就让应用起不来（ADR-0007）。
 *
 * <p>★ 与 {@code MockLoginGuardAutoConfiguration} 同一形态，且**调用点必须在护栏类之外**：
 * 防的是「护栏写了但启动时没人调用它」的装饰品形态（同一课在 AUTH-LOGIN-001 上已经吃过）。
 *
 * <p>★ 为什么是 {@link ApplicationRunner}：profile 与配置在 bean 初始化阶段还没保证解析完，
 * 「配错了就别让它起完」这句话在容器就绪后说才作数。
 *
 * @author OCR-IMPL-001
 */
@Slf4j
@Component
public class OcrProviderGuardAutoConfiguration implements ApplicationRunner {

    private final OcrProviderFactory providerFactory;
    private final OcrProperties properties;
    private final Environment environment;

    public OcrProviderGuardAutoConfiguration(OcrProviderFactory providerFactory,
                                            OcrProperties properties,
                                            Environment environment) {
        this.providerFactory = providerFactory;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<String> error = OcrProviderGuard.configError(
            properties.getProvider(),
            properties.isPaidEnabled(),
            providerFactory.availableNames(),
            providerFactory.paidNames(),
            environment.getActiveProfiles());
        if (error.isPresent()) {
            throw new IllegalStateException(error.get());
        }
        log.info("识别通道已就绪：provider={}，paid-enabled={}",
            providerFactory.providerName(), properties.isPaidEnabled());
    }

}
