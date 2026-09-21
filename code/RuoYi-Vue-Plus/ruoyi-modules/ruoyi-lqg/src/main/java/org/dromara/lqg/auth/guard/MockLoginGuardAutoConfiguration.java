package org.dromara.lqg.auth.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * mock 登录护栏的**启动接线**：用运行时真实的 profiles 与配置值调用
 * {@link MockLoginGuard#check(String[], boolean)}，不合法就让应用起不来（ADR-0008）。
 *
 * <p>为什么单独一个类、且必须是「护栏类之外」的调用点：accept 第 4 段用
 * {@code grep -rn 'MockLoginGuard.check' … | grep -v '/guard/MockLoginGuard.java'} 必须有命中——
 * 防的就是「护栏类写了但启动时没人调用它」的装饰品形态。调用点写在护栏类自己内部，
 * 那条 grep 会把它排掉，等于没接线。
 *
 * <p>为什么是 {@link ApplicationRunner} 而不是 {@code @PostConstruct}：profile 与配置在 bean
 * 初始化阶段还没保证解析完，且「配错了就别让它起完」这句话在容器就绪后说才作数。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
@Component
public class MockLoginGuardAutoConfiguration implements ApplicationRunner {

    /**
     * 小程序 mock 登录开关（ADR-0008）。缺省 false —— prod 配置文件里不出现这个键，靠的就是它。
     */
    @Value("${lqg.auth.mock-login:false}")
    private boolean mockEnabled;

    /**
     * 运行期真实的 profile 来源。
     */
    private final Environment environment;

    public MockLoginGuardAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        MockLoginGuard.check(environment.getActiveProfiles(), mockEnabled);
    }

}
