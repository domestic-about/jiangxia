package org.dromara.lqg.cryo.flow.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 改 / 删一笔登记被拒时的提示（G 批 B2：2026-09-24 起小程序也能改删登记，两端原样显示后端这句话）。
 *
 * <p>钉两件事：判定仍是 {@link CryoBalanceChecker#requireNonNegative} 那一个（过 / 不过与它一致）；
 * 提示指出<b>第一次</b>变负的那一笔（时间 + 支数）与变成了多少，不再是给「改初始支数」用的那句
 * 「已取走 N 支，冻存数量不能少于 N」。
 *
 * @author G 批 B2
 */
class CryoFlowOverdraftMessageTest {

    private static CryoBalanceChecker.Flow flow(long id, String time, int delta) {
        return new CryoBalanceChecker.Flow(id, LocalDateTime.parse(time), delta);
    }

    /** 与 H5 实测同形：初始 4，取 3（改后）→ 补入 1 → 取 3：最后一笔之后剩 -1 */
    private static final List<CryoBalanceChecker.Flow> OVERDRAFT = List.of(
        flow(1L, "2026-09-24T10:20:00", -3),
        flow(2L, "2026-09-24T10:21:00", 1),
        flow(3L, "2026-09-24T10:27:00", -3));

    @Test
    @DisplayName("① 改一笔被拒：指出第一次变负的那一笔与变成多少")
    void editRejectedPointsAtTheStep() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> CryoFlowService.requireNonNegative(4, OVERDRAFT, "这样改"));
        assertEquals("这样改会让 09-24 10:27 那一笔（-3 支）之后的剩余变成 -1 支，没有保存", e.getMessage());
        assertEquals(Integer.valueOf(400), e.getCode());
    }

    @Test
    @DisplayName("② 删一笔被拒：同一句式，动作换成「删掉这一笔」")
    void deleteRejected() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> CryoFlowService.requireNonNegative(2, List.of(
                flow(1L, "2026-09-20T09:00:00", -1),
                flow(2L, "2026-09-21T09:00:00", -2)), "删掉这一笔"));
        assertEquals("删掉这一笔会让 09-21 09:00 那一笔（-2 支）之后的剩余变成 -1 支，没有保存", e.getMessage());
    }

    @Test
    @DisplayName("③ 过 / 不过与上游唯一判据一致：恰好到 0 能过")
    void sameVerdictAsChecker() {
        assertDoesNotThrow(() -> CryoFlowService.requireNonNegative(5, OVERDRAFT, "这样改"));
        assertDoesNotThrow(() -> CryoBalanceChecker.requireNonNegative(5, OVERDRAFT));
        assertThrows(ServiceException.class, () -> CryoBalanceChecker.requireNonNegative(4, OVERDRAFT));
    }

}
