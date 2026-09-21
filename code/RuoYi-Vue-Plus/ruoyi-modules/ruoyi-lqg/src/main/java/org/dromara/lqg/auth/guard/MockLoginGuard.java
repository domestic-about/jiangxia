package org.dromara.lqg.auth.guard;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Locale;

/**
 * mock 登录护栏（ADR-0008）：mock 登录只存在于 dev / test；prod 下被打开则**应用拒绝启动**。
 *
 * <p>为什么要有它：mock 登录能让调用方拿任意手机号换到 token（见 {@code WxIdentityResolver}），
 * 等于一个不用微信的后门。ADR-0008 的后果一节写死「上线 ticket 的 accept 必须有一条：
 * 对生产地址发 mock 登录请求，被拒」——本类是那条断言的服务端依据。
 *
 * <p>判定口径（三条都是踩过的坑，别简化）：
 * <ol>
 *   <li>profile 比较**大小写不敏感**：{@code PROD} / {@code Prod} 与 {@code prod} 同罪；</li>
 *   <li>{@code spring.profiles.active} 没配（空 / null）时**不当成开发环境**放行——
 *       生产上漏配 profile 是真实事故，缺省必须按不安全处理；</li>
 *   <li>只有明确声明了 dev / test（之一）才允许 mock=true。</li>
 * </ol>
 *
 * <p>本类**刻意只有静态纯函数**、不带任何 Spring 注解：需求层给的契约测试
 * {@code doc/verify/fixtures/java/MockLoginGuardContractTest.java} 直接调
 * {@link #check(String[], boolean)}，要改签名先改契约测试（accept 用 {@code cmp} 校验它逐字节未改）。
 * 「启动时用真实 profiles 与配置值调它」这件事由 {@link MockLoginGuardAutoConfiguration} 负责。
 *
 * <p>{@code application-prod.yml} 里**不允许**出现 {@code mock-login} 这个键：缺省即 false。
 * 写一行 {@code mock-login: false}「以示安全」反而危险——离被人改成 true 只差一次手滑。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
public final class MockLoginGuard {

    /**
     * 允许打开 mock 登录的 profile（小写比较）。profiles.active 为空时**不在这里面** —— 见口径 2。
     */
    private static final String[] MOCK_ALLOWED_PROFILES = {"dev", "test"};

    /**
     * 非法 profile：出现即视为生产。只认 prod；staging / uat 之类由部署方按 REQ-SYS-010 拼进 prod。
     */
    private static final String PROD_PROFILE = "prod";

    private MockLoginGuard() {
    }

    /**
     * 护栏本体：mock 与 profile 的组合是否合法。
     *
     * @param activeProfiles 当前激活的 profile（Spring 的实际值；null / 空 = 没声明）
     * @param mockEnabled    配置里的 {@code lqg.auth.mock-login}
     * @throws IllegalStateException profile 含 prod（或没声明 profile）却把 mock 打开时
     */
    public static void check(String[] activeProfiles, boolean mockEnabled) {
        if (!mockEnabled) {
            // 关着的 mock 在任何 profile 下都安全
            return;
        }
        String[] profiles = activeProfiles == null ? new String[0] : activeProfiles;
        boolean prod = Arrays.stream(profiles)
            .filter(p -> p != null && !p.isBlank())
            .map(p -> p.trim().toLowerCase(Locale.ROOT))
            .anyMatch(PROD_PROFILE::equals);
        if (prod) {
            throw new IllegalStateException(
                "lqg.auth.mock-login=true 与 profile=" + Arrays.toString(profiles)
                    + " 冲突：mock 登录只允许存在于 dev / test（ADR-0008）。已拒绝启动。");
        }
        boolean declared = Arrays.stream(profiles).anyMatch(p -> p != null && !p.isBlank());
        if (!declared) {
            throw new IllegalStateException(
                "lqg.auth.mock-login=true 但 spring.profiles.active 没声明："
                    + "不许把「没配 profile」当成开发环境（漏配 profile 的生产实例会静默开后门）。已拒绝启动。");
        }
        boolean allowed = Arrays.stream(profiles)
            .filter(p -> p != null && !p.isBlank())
            .map(p -> p.trim().toLowerCase(Locale.ROOT))
            .anyMatch(p -> Arrays.stream(MOCK_ALLOWED_PROFILES).anyMatch(p::equals));
        if (!allowed) {
            throw new IllegalStateException(
                "lqg.auth.mock-login=true 只允许在 " + Arrays.toString(MOCK_ALLOWED_PROFILES)
                    + " 下打开，当前 profile=" + Arrays.toString(profiles) + "。已拒绝启动。");
        }
        log.warn("mock 登录已开启（profile={}）：xcxCode 以 mock: 开头时跳过微信，"
            + "手机号直接取请求里的值。仅限开发 / 测试环境（ADR-0008）。", Arrays.toString(profiles));
    }

}
