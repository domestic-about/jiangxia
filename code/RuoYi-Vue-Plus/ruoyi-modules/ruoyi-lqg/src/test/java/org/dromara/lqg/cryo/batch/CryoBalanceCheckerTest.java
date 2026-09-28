package org.dromara.lqg.cryo.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link CryoBalanceChecker} 的契约测试（CRYO-MODEL-001 ticket §2 点名的单测）。
 *
 * <p>钉三件事：
 * <ol>
 *   <li><b>最终为正但中途为负必须拒绝</b>（FLOW:F-CRYO-02.step5 的原话：「只看最终剩余不够：
 *       会改出『当时只剩 2 支却取走了 3 支』的账」）—— 用例 ①；</li>
 *   <li><b>同一时刻按 id 排序</b>（ticket §2）—— 用例 ②：同一 {@code flowTime} 的两笔
 *       （取走与补入同一秒录入是常事）换个 id 顺序就换个结论，若不排序这条会随机红；</li>
 *   <li><b>剩余 = 初始 + Σ(未删 delta)</b> 与<b>恰好取到 0 不算负</b> —— 用例 ③④。</li>
 * </ol>
 *
 * <p>★ 本类不碰库、不碰 Spring：判据是纯函数，CRYO-FLOW-001 的写 / 改 / 删流水三处直接复用。
 *
 * @author CRYO-MODEL-001
 */
class CryoBalanceCheckerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 17, 10, 0);

    private static CryoBalanceChecker.Flow flow(long id, LocalDateTime time, int delta) {
        return new CryoBalanceChecker.Flow(id, time, delta);
    }

    @Test
    @DisplayName("① 最终为正、但中途某一步为负 → 拒绝（只看最终剩余会放过的账）")
    void rejectsIntermediateNegativeEvenWhenFinalIsPositive() {
        // 初始 1 支 → 先取 2 支（第 1 步就是 -1）→ 后补 5 支
        List<CryoBalanceChecker.Flow> flows = List.of(
            flow(1L, T, -2),
            flow(2L, T.plusDays(1), 5));
        // 最终剩余 = 1 - 2 + 5 = 4 > 0，只看最后一步会放过它
        assertEquals(4, CryoBalanceChecker.remaining(1, flows));
        // 最低点出现在第 1 笔（0 - 2 = -2）→ 最小合法初始支数 = 2
        assertEquals(2, CryoBalanceChecker.requiredInitQty(flows));

        ServiceException e = assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireNonNegative(1, flows));
        assertEquals("已取走 2 支，冻存数量不能少于 2", e.getMessage());
        assertEquals(400, e.getCode());

        // 改成够用的初始支数就通过（1 → 2）：不是「一律拒绝」也能绿
        CryoBalanceChecker.requireNonNegative(2, flows);
        assertEquals(5, CryoBalanceChecker.checkAndRemaining(2, flows));
    }

    @Test
    @DisplayName("② 同一时刻按 id 排序：+3 在 -3 之前通过，反过来被拒")
    void ordersByFlowTimeThenId() {
        // 同一时刻：id 小的先算
        CryoBalanceChecker.requireNonNegative(2, List.of(
            flow(1L, T, 3),
            flow(2L, T, -3)));
        assertEquals(0, CryoBalanceChecker.requiredInitQty(List.of(
            flow(1L, T, 3),
            flow(2L, T, -3))));

        // 只把 id 对调（-3 先算）→ 第 1 步就是 -3 → 最小合法初始支数 = 3
        ServiceException e = assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireNonNegative(2, List.of(
                flow(1L, T, -3),
                flow(2L, T, 3))));
        assertEquals("已取走 3 支，冻存数量不能少于 3", e.getMessage());
        // 给够 3 支就通过 —— 结论确实由 id 顺序决定
        CryoBalanceChecker.requireNonNegative(3, List.of(
            flow(1L, T, -3),
            flow(2L, T, 3)));

        // 入参顺序反过来不影响结论：排序是判据的一部分，不是调用方的责任
        CryoBalanceChecker.requireNonNegative(2, List.of(
            flow(2L, T, -3),
            flow(1L, T, 3)));
    }

    @Test
    @DisplayName("③ 剩余 = 初始 + Σ(delta)；消息里的 N 是「最小合法初始支数」= 最大透支额")
    void remainingIsInitPlusDeltas() {
        // seed 的 3003：初始 6，流水 -1 / +2 / -3 → 4
        List<CryoBalanceChecker.Flow> flows = List.of(
            flow(3102L, T.minusDays(35), -1),
            flow(3103L, T.minusDays(20), 2),
            flow(3104L, T.minusDays(10), -3));
        assertEquals(4, CryoBalanceChecker.remaining(6, flows));
        // 最低点出现在第 3 笔（0 -1 +2 -3 = -2）→ 最小合法初始支数 = 2
        assertEquals(2, CryoBalanceChecker.requiredInitQty(flows));
        assertEquals(4, CryoBalanceChecker.checkAndRemaining(6, flows));

        // 初始改成 1 < 2 → 拒
        ServiceException e = assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireNonNegative(1, flows));
        assertEquals("已取走 2 支，冻存数量不能少于 2", e.getMessage());
        // 初始改成 2（刚好在最低点持平为 0）→ 不拒绝
        CryoBalanceChecker.requireNonNegative(2, flows);
        assertEquals(0, CryoBalanceChecker.remaining(2, flows));

        // 两笔取走叠加的透支：最低点 = 第 2 笔（初始 3：3-2-3 = -2）
        List<CryoBalanceChecker.Flow> twoTakes = List.of(
            flow(1L, T, -2),
            flow(2L, T.plusHours(1), -3));
        ServiceException e2 = assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireNonNegative(4, twoTakes));
        assertEquals("已取走 5 支，冻存数量不能少于 5", e2.getMessage());
    }

    @Test
    @DisplayName("④ 恰好取到 0 不算负（seed 的 3004：初始 3、取 3 → 剩余 0）")
    void exactlyZeroIsAllowed() {
        List<CryoBalanceChecker.Flow> flows = List.of(flow(3105L, T, -3));
        CryoBalanceChecker.requireNonNegative(3, flows);
        assertEquals(0, CryoBalanceChecker.checkAndRemaining(3, flows));
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requireNonNegative(2, flows));
    }

    @Test
    @DisplayName("⑤ 没有流水 / 空集合 / null 不炸：剩余 = 初始支数")
    void emptyFlowsAreSafe() {
        assertEquals(5, CryoBalanceChecker.remaining(5, List.of()));
        assertEquals(5, CryoBalanceChecker.remaining(5, null));
        assertEquals(0, CryoBalanceChecker.requiredInitQty(null));
        CryoBalanceChecker.requireNonNegative(5, null);
        assertEquals(List.of(), CryoBalanceChecker.ordered(null));
    }

    @Test
    @DisplayName("⑥ ordered() 不改入参，且 flowTime 为空的行排在最前（不抛 NPE）")
    void orderedIsPureAndNullSafe() {
        List<CryoBalanceChecker.Flow> input = new ArrayList<>();
        input.add(flow(2L, T.plusDays(1), -1));
        input.add(flow(1L, null, 1));
        List<CryoBalanceChecker.Flow> ordered = CryoBalanceChecker.ordered(input);
        assertEquals(2, ordered.size());
        assertEquals(1L, ordered.get(0).id());
        assertEquals(2L, ordered.get(1).id());
        // 原列表顺序没被改（纯函数）
        assertEquals(2L, input.get(0).id());
    }

    @Test
    @DisplayName("⑦ 位置判据：直接进液氮 / 已转液氮都算 ln2（只看 in_minus80 会漏掉 3003）")
    void locationJudgement() {
        assertEquals("ln2", CryoBalanceChecker.locationOf("N", null));
        assertEquals("ln2", CryoBalanceChecker.locationOf("Y", LocalDate.of(2026, 9, 1)));
        assertEquals("ln2", CryoBalanceChecker.locationOf("N", LocalDate.of(2026, 9, 1)));
        assertEquals("minus80", CryoBalanceChecker.locationOf("Y", null));
    }

    @Test
    @DisplayName("⑧ 代数格式 ^P\\d{1,3}$：3 / p3 / P1234 / 空 都被拒，P3 / P12 通过")
    void passageFormat() {
        assertEquals("P3", CryoBalanceChecker.requirePassage(" P3 "));
        assertEquals("P12", CryoBalanceChecker.requirePassage("P12"));
        assertEquals("P999", CryoBalanceChecker.requirePassage("P999"));
        for (String bad : List.of("3", "p3", "P1234", "第3代", "P", "")) {
            assertThrows(ServiceException.class, () -> CryoBalanceChecker.requirePassage(bad),
                "代数 " + bad + " 必须被拒");
        }
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requirePassage(null));
    }

    @Test
    @DisplayName("⑨ 初始支数 >0、转液氮不早于冻存、暂存 -80 只认 Y/N")
    void scalarRules() {
        assertEquals(4, CryoBalanceChecker.requirePositiveInitQty(4));
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requirePositiveInitQty(0));
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requirePositiveInitQty(-1));
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requirePositiveInitQty(null));

        LocalDate freeze = LocalDate.of(2026, 9, 17);
        CryoBalanceChecker.requireLn2NotBeforeFreeze(freeze, freeze);
        CryoBalanceChecker.requireLn2NotBeforeFreeze(freeze, null);
        assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireLn2NotBeforeFreeze(freeze, LocalDate.of(2026, 9, 1)));

        assertEquals("Y", CryoBalanceChecker.requireInMinus80("y"));
        assertEquals("N", CryoBalanceChecker.requireInMinus80("N"));
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requireInMinus80("是"));

        // 直接进液氮（N）或已登记转液氮 → 位置必填
        assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireLn2Location("N", null, "  "));
        assertThrows(ServiceException.class,
            () -> CryoBalanceChecker.requireLn2Location("Y", LocalDate.of(2026, 9, 17), null));
        assertEquals("1号罐-1架-A2",
            CryoBalanceChecker.requireLn2Location("N", null, " 1号罐-1架-A2 "));
        assertEquals("2号罐", CryoBalanceChecker.requireLn2Location("Y", null, "2号罐"));
    }

}
