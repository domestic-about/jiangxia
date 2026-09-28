package org.dromara.lqg.auth.group.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 外部档案核验状态机 + 单位 / 组别命名规则的契约测试（AUTH-GROUP-001）。
 *
 * <p>钉住的是 accept 第 2 条那张转移表与「自填必须二选一」的闸门：
 * 这两条一旦做反，可见范围（AUTH-EXT-001）那边全错，而它们在 accept 里只是几行 grep/jq，
 * 看不出「为什么」。这里把它们变成**能被红的用例**。
 *
 * @author AUTH-GROUP-001
 */
class ExtBindStateMachineContractTest {

    // ── 状态机：五条合法边 ────────────────────────────────────────────────────

    @Test
    void legalTransitionsAreExactlyTheFiveInTheTicket() {
        assertTrue(ExtBindStateMachine.isLegal("pending", "verified"), "pending→verified");
        assertTrue(ExtBindStateMachine.isLegal("pending", "rejected"), "pending→rejected");
        assertTrue(ExtBindStateMachine.isLegal("verified", "pending"), "verified→pending（外部自己改）");
        assertTrue(ExtBindStateMachine.isLegal("rejected", "pending"), "rejected→pending（改后重提）");
        assertTrue(ExtBindStateMachine.isLegal("verified", "verified"), "verified→verified（改归组）");
    }

    @Test
    void everyOtherTransitionIsRefused() {
        // 逐条点名 ticket 明说非法的那些（不写成「遍历全部组合再 isLegal」——
        // 那等于用被测函数证明它自己）
        assertFalse(ExtBindStateMachine.isLegal("unbound", "verified"), "unbound→verified 非法");
        assertFalse(ExtBindStateMachine.isLegal("unbound", "rejected"), "unbound→rejected 非法");
        assertFalse(ExtBindStateMachine.isLegal("unbound", "pending"), "unbound→pending 只能由外部保存触发");
        assertFalse(ExtBindStateMachine.isLegal("verified", "rejected"), "verified→rejected 非法");
        assertFalse(ExtBindStateMachine.isLegal("rejected", "verified"), "rejected→verified 非法");
        assertFalse(ExtBindStateMachine.isLegal("rejected", "rejected"), "rejected→rejected 非法");
        assertFalse(ExtBindStateMachine.isLegal("pending", "pending"), "pending→pending 非法");
        assertFalse(ExtBindStateMachine.isLegal("verified", "unbound"), "verified→unbound 非法");
        assertFalse(ExtBindStateMachine.isLegal(null, "verified"), "null→verified 非法");
        assertFalse(ExtBindStateMachine.isLegal("pending", null), "pending→null 非法");
        assertFalse(ExtBindStateMachine.isLegal("", "verified"), "空状态→verified 非法");
        assertFalse(ExtBindStateMachine.isLegal("PENDING", "verified"), "状态大小写敏感（字典值就是小写）");
    }

    @Test
    void actionToTargetMapping() {
        assertEquals("verified", ExtBindStateMachine.targetOf("approve"));
        assertEquals("rejected", ExtBindStateMachine.targetOf("reject"));
        assertTrue(ExtBindStateMachine.isKnownAction("approve"));
        assertTrue(ExtBindStateMachine.isKnownAction("reject"));
        assertFalse(ExtBindStateMachine.isKnownAction("drop"));
        assertTrue(ExtBindStateMachine.isLegalAction("pending", "approve"));
        assertTrue(ExtBindStateMachine.isLegalAction("pending", "reject"));
        assertTrue(ExtBindStateMachine.isLegalAction("verified", "approve"));
        assertFalse(ExtBindStateMachine.isLegalAction("verified", "reject"), "已核验的不能被驳回");
        assertFalse(ExtBindStateMachine.isLegalAction("unbound", "approve"), "unbound 不能被核验");
        assertFalse(ExtBindStateMachine.isLegalAction("rejected", "approve"), "rejected 只能等外部重提");
    }

    @Test
    void onlyVerifiedParticipatesInGroupSharing() {
        assertTrue(ExtBindStateMachine.participatesInGroupSharing("verified"));
        assertFalse(ExtBindStateMachine.participatesInGroupSharing("pending"));
        assertFalse(ExtBindStateMachine.participatesInGroupSharing("rejected"));
        assertFalse(ExtBindStateMachine.participatesInGroupSharing("unbound"));
        assertFalse(ExtBindStateMachine.participatesInGroupSharing(null));
    }

    // ── 自填必须二选一 ───────────────────────────────────────────────────────

    @Test
    void selfInputProfileCannotBeApprovedWithoutNewOrMerge() {
        // 自填（unit_id / group_id 都空），approve 又不带 createUnit/createGroup 也不带 unitId/groupId
        String refuse = ExtBindStateMachine.needsSelfInputDecision(
            "pending", false, false, false, false, false, false);
        assertNotNull(refuse, "自填档案没选新建 / 归并时必须拒绝");
        assertTrue(refuse.contains("新建") && refuse.contains("归并"), "拒绝原因要说清二选一");
    }

    @Test
    void selfInputProfileCanBeApprovedByCreateOrByMerge() {
        // 新建：createUnit + createGroup
        assertNull(ExtBindStateMachine.needsSelfInputDecision(
            "pending", false, false, true, true, false, false), "新建两条腿都给 → 放行");
        // 归并：unitId + groupId
        assertNull(ExtBindStateMachine.needsSelfInputDecision(
            "pending", false, false, false, false, true, true), "归并两条腿都给 → 放行");
        // 只给一条腿不算（这正是 counterfeit 里点名的「新建只建了单位没建组别」）
        assertNotNull(ExtBindStateMachine.needsSelfInputDecision(
            "pending", false, false, true, false, false, false), "只有 createUnit 不算决定完");
        assertNotNull(ExtBindStateMachine.needsSelfInputDecision(
            "pending", false, false, false, false, true, false), "只有 unitId 不算决定完");
    }

    @Test
    void profileThatAlreadyPickedFromListNeedsNoDecision() {
        assertNull(ExtBindStateMachine.needsSelfInputDecision(
            "pending", true, true, false, false, false, false), "选了列表项 → 直接可核验");
        // 半自填（只有 unit_id没有 group_id）仍然要决定归口
        assertNotNull(ExtBindStateMachine.needsSelfInputDecision(
            "pending", true, false, false, false, false, false), "缺 group_id 仍要二选一");
    }

    @Test
    void approveOfVerifiedProfileRegroupDoesNotNeedDecision() {
        // verified→verified（改归组）：档案例本就有 unit_id/group_id，不该被「二选一」挡住
        assertNull(ExtBindStateMachine.needsSelfInputDecision(
            "verified", true, true, false, false, false, false));
    }

    // ── 保存一律回 pending ───────────────────────────────────────────────────

    @Test
    void anySuccessfulSaveReturnsToPending() {
        assertEquals("pending", ExtBindStateMachine.afterProfileSave());
    }

    // ── 命名规则 ─────────────────────────────────────────────────────────────

    @Test
    void nameComparisonIsTrimmedAndCaseInsensitive() {
        assertTrue(UnitGroupRules.sameName("A 医院", " a 医院 "));
        assertTrue(UnitGroupRules.sameName("C  研究所", "C 研究所"), "连续空白折成一个");
        assertFalse(UnitGroupRules.sameName("A 医院", "B 大学"));
        assertFalse(UnitGroupRules.sameName("", ""), "空名不算同一个（否则空名会互相挡住）");
        assertFalse(UnitGroupRules.sameName(null, "A 医院"));
    }

    @Test
    void toggleOnlyAcceptsActiveOrDisabled() {
        assertTrue(UnitGroupRules.isToggleTarget("active"));
        assertTrue(UnitGroupRules.isToggleTarget("disabled"));
        assertFalse(UnitGroupRules.isToggleTarget("pending"), "pending 只由外部自填产生，不能人工置回");
        assertFalse(UnitGroupRules.isToggleTarget(null));
        assertTrue(UnitGroupRules.isKnownStatus("pending"));
        assertFalse(UnitGroupRules.isKnownStatus("archived"));
    }

}
