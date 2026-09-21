package org.dromara.lqg.auth.guard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 契约测试（由需求层提供，AUTH-LOGIN-001 的 accept 用 cmp 校验本文件**逐字节未改**后再跑）。
 * 守的是 ADR-0008：mock 登录只存在于 dev / test；prod 下一旦被打开，应用必须拒绝启动。
 *
 * 被测：org.dromara.lqg.auth.guard.MockLoginGuard#check(String[] activeProfiles, boolean mockEnabled)
 *   · 合法组合 → 正常返回
 *   · activeProfiles 含 "prod" 且 mockEnabled=true → 抛 IllegalStateException
 * 应用启动时（ApplicationRunner / @PostConstruct）必须用真实的 profiles 与配置值调用它。
 */
class MockLoginGuardContractTest {

    @Test
    void prodWithMockEnabledMustRefuseToStart() {
        assertThrows(IllegalStateException.class, () -> MockLoginGuard.check(new String[] {"prod"}, true));
        assertThrows(IllegalStateException.class, () -> MockLoginGuard.check(new String[] {"common", "prod"}, true));
        assertThrows(IllegalStateException.class, () -> MockLoginGuard.check(new String[] {"PROD"}, true));
    }

    @Test
    void prodWithMockDisabledStartsNormally() {
        assertDoesNotThrow(() -> MockLoginGuard.check(new String[] {"prod"}, false));
    }

    @Test
    void devAndTestMayEnableMock() {
        assertDoesNotThrow(() -> MockLoginGuard.check(new String[] {"dev"}, true));
        assertDoesNotThrow(() -> MockLoginGuard.check(new String[] {"test"}, true));
        assertDoesNotThrow(() -> MockLoginGuard.check(new String[] {"dev"}, false));
    }

    @Test
    void noActiveProfileIsTreatedAsUnsafe() {
        // 没声明 profile 时不能默认当成开发环境放行 mock——生产上漏配 profile 是真实事故
        assertThrows(IllegalStateException.class, () -> MockLoginGuard.check(new String[] {}, true));
        assertThrows(IllegalStateException.class, () -> MockLoginGuard.check(null, true));
    }
}
