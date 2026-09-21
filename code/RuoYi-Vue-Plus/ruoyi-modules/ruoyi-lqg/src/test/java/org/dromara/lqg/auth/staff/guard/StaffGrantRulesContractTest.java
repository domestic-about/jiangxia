package org.dromara.lqg.auth.staff.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内部人员授权规则的契约测试（纯函数，无 Spring 上下文、无数据库）。
 *
 * <p>钉住本票最容易做反的决策：撤销 ≠ 删号（降回外部）、不能撤销自己、不能把最后一个管理员弄没、
 * 工作台准入只认内部角色（外部账号哪怕有口令也拒）。
 *
 * @author AUTH-STAFF-001
 */
class StaffGrantRulesContractTest {

    @Test
    @DisplayName("R1 工作台准入：含 lqg_internal / lqg_admin 才算内部；只有 lqg_external 的不算")
    void workbenchAdmissionIsRoleBased() {
        assertTrue(StaffGrantRules.isInternal(Set.of("lqg_internal")));
        assertTrue(StaffGrantRules.isInternal(Set.of("lqg_admin")));
        // 管理员同时挂 lqg_internal 也要算内部（两行角色）
        assertTrue(StaffGrantRules.isInternal(Set.of("lqg_admin", "lqg_internal")));
        // ★ 外部账号带着可用口令也是外部：判据是角色，不是「能不能过口令校验」
        assertFalse(StaffGrantRules.isInternal(Set.of("lqg_external")));
        assertFalse(StaffGrantRules.isInternal(Set.of()));
        assertFalse(StaffGrantRules.isInternal(null));
    }

    @Test
    @DisplayName("R2 撤销后只剩 103：仍是合法账号（不是删号），但不能进工作台")
    void revokedAccountIsExternalNotDeleted() {
        Set<String> afterRevoke = Set.of("lqg_external");
        assertTrue(StaffGrantRules.isExternalOnly(afterRevoke));
        assertFalse(StaffGrantRules.isInternal(afterRevoke));
        // 撤销前是内部：撤销必须把内部角色摘掉（旧 token 因此失效，见 accept 第 1 条）
        assertTrue(StaffGrantRules.isInternal(Set.of("lqg_internal", "lqg_external")));
        assertFalse(StaffGrantRules.isExternalOnly(Set.of("lqg_internal", "lqg_external")));
    }

    @Test
    @DisplayName("R3 reset-pwd 的目标判据：外部账号（只有 103）必须被拒")
    void resetPwdRejectsExternalAccount() {
        // 内部账号可以设口令
        assertTrue(StaffGrantRules.isInternal(Set.of("lqg_internal")));
        // 外部账号不行 —— accept 第 2 条要求 password 长度仍是 0
        assertFalse(StaffGrantRules.isInternal(Set.of("lqg_external")));
        assertTrue(StaffGrantRules.isExternalOnly(Set.of("lqg_external")));
    }

    @Test
    @DisplayName("R4 不能撤销自己")
    void cannotRevokeSelf() {
        assertFalse(StaffGrantRules.canRevokeSelf(9000000100L, 9000000100L));
        assertTrue(StaffGrantRules.canRevokeSelf(9000000100L, 9000000116L));
        assertFalse(StaffGrantRules.canRevokeSelf(null, 9000000116L));
    }

    @Test
    @DisplayName("R5 不能把系统里最后一个 lqg_admin 撤掉或降级")
    void cannotDropLastAdmin() {
        // 目标是管理员且库里没有别的管理员 → 拒绝
        assertFalse(StaffGrantRules.canLoseAdmin(true, 0L));
        // 目标是管理员但还有别人 → 允许
        assertTrue(StaffGrantRules.canLoseAdmin(true, 1L));
        // 目标不是管理员 → 与「最后一个管理员」无关
        assertTrue(StaffGrantRules.canLoseAdmin(false, 0L));
    }

    @Test
    @DisplayName("R6 lqg_admin 同时挂 lqg_internal（管理员在小程序里也是内部人员）；普通内部人员不多挂")
    void adminAlsoCarriesInternalRole() {
        assertEquals(List.of("lqg_admin", "lqg_internal"), StaffGrantRules.grantedRoleKeys("lqg_admin"));
        assertEquals(List.of("lqg_internal"), StaffGrantRules.grantedRoleKeys("lqg_internal"));
        // 展开后仍然算内部（两条都算）；且不接受 103 —— 外部角色只能靠撤销得到
        assertTrue(StaffGrantRules.isInternal(Set.copyOf(StaffGrantRules.grantedRoleKeys("lqg_admin"))));
        assertTrue(StaffGrantRules.isInternal(Set.copyOf(StaffGrantRules.grantedRoleKeys("lqg_internal"))));
        assertFalse(StaffGrantRules.grantedRoleKeys("lqg_external").contains("lqg_external"));
    }

}
