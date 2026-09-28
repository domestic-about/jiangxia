package org.dromara.lqg.auth.staff.guard;

import java.util.List;
import java.util.Set;

/**
 * 内部人员授权的纯规则（不碰数据库、不碰 Spring）。
 *
 * <p>抽出来的理由：本票最容易做反的是**决策**，而决策本身与「怎么读库 / 怎么发 SQL」无关。
 * 决策做成静态纯函数，契约测试（{@code StaffGrantRulesContractTest}）才能在没有 Spring 上下文、
 * 没有库的情况下把每条规则钉住 —— 与 AUTH-LOGIN-001 的 {@code MockLoginGuard} 同一手法。
 *
 * <p>三条规则（ADR-0003 / FLOW:F-AUTH-02）：
 * <ol>
 *   <li>{@link #isInternal(Set)} —— 工作台准入只认内部角色，不认 {@code user_type}；</li>
 *   <li>{@link #canRevokeSelf(Long, Long)} —— 不能撤销自己；</li>
 *   <li>{@link #canLoseAdmin(boolean, long)} —— 不能把系统里最后一个 lqg_admin 撤掉 / 降级。</li>
 * </ol>
 *
 * @author AUTH-STAFF-001
 */
public final class StaffGrantRules {

    /**
     * 内部角色键（工作台准入判据）。{@code superadmin} 是上游超级管理员，始终放行。
     */
    public static final List<String> INTERNAL_ROLE_KEYS = List.of("lqg_internal", "lqg_admin", "superadmin");

    /**
     * 外部角色键：撤销授权后**只留**它。
     */
    public static final String EXTERNAL_ROLE_KEY = "lqg_external";

    /**
     * 实验室管理员角色键。
     */
    public static final String ADMIN_ROLE_KEY = "lqg_admin";

    /**
     * 内部人员角色键。
     */
    public static final String INTERNAL_ROLE_KEY = "lqg_internal";

    private StaffGrantRules() {
    }

    /**
     * 本次授权 / 改角色实际要挂上的角色键集合（ticket §2.1：{@code lqg_admin} 同时挂 {@code lqg_internal}）。
     *
     * <p>为什么管理员要**两个都挂**：小程序侧的内部功能（内部样本、内部编号等）按 {@code lqg_internal}
     * 判身份，只挂 101 的话管理员在网页端是管理员、在小程序里却不是内部人员，两端口径劈叉。
     * 反过来不成立 —— 内部人员不该顺手拿到管理员角色。
     *
     * <p>纯函数（不碰库）：可被契约测试直接钉住，见 {@code StaffGrantRulesContractTest}。
     */
    public static List<String> grantedRoleKeys(String requestedRoleKey) {
        return ADMIN_ROLE_KEY.equals(requestedRoleKey)
            ? List.of(ADMIN_ROLE_KEY, INTERNAL_ROLE_KEY)
            : List.of(INTERNAL_ROLE_KEY);
    }

    /**
     * 该账号是否算「内部」（能进工作台、能被 reset-pwd / revoke 操作）。
     *
     * <p>判据只看角色键：seed 里内部账号是 {@code user_type='sys_user'}、外部是 {@code app_user}，
     * 那是巧合不是口径 —— ADR-0003 明确「内外部只由角色决定」。
     */
    public static boolean isInternal(Set<String> roleKeys) {
        return roleKeys != null && roleKeys.stream().anyMatch(INTERNAL_ROLE_KEYS::contains);
    }

    /**
     * 该账号是否是外部账号（只有 103、没有内部角色）。
     *
     * <p>reset-pwd 对「是外部账号」必须被拒（accept 第 2 条：外部账号的 password 长度必须仍是 0）。
     */
    public static boolean isExternalOnly(Set<String> roleKeys) {
        return roleKeys != null && roleKeys.contains(EXTERNAL_ROLE_KEY) && !isInternal(roleKeys);
    }

    /**
     * 撤销：不能撤销自己。
     */
    public static boolean canRevokeSelf(Long operatorUserId, Long targetUserId) {
        return operatorUserId != null && !operatorUserId.equals(targetUserId);
    }

    /**
     * 撤销 / 降级：目标当前是 lqg_admin 时，系统里必须还有**别的** lqg_admin 才允许动他。
     *
     * @param targetIsAdmin     目标账号当前是否带 lqg_admin
     * @param otherAdminCount   库里除目标之外还带 lqg_admin 的账号数
     */
    public static boolean canLoseAdmin(boolean targetIsAdmin, long otherAdminCount) {
        return !targetIsAdmin || otherAdminCount > 0L;
    }

}
