package org.dromara.lqg.cryo.flow.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.flow.domain.vo.CryoFlowRecordVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冻存流水四条标量判据的契约测试（CRYO-FLOW-001）。
 *
 * <p>钉 ticket §2 逐条点名的口径：
 * <ol>
 *   <li>{@code take} / {@code add} 的 {@code qty} 必须是<b>正整数</b>，落库 {@code delta = ∓qty}
 *       （take 的 delta 存成正数靠 {@code flow_type} 区分加减 = accept 1 的 counterfeit）；</li>
 *   <li>{@code adjust} 带符号、<b>不为 0</b>、原因必填；</li>
 *   <li>时间入参两种形态都要接住：{@code yyyy-MM-dd HH:mm:ss} 与纯日期
 *       （verify/README 坑 4：写成 ISO 的 {@code T} 或只给日期都可能「实现对了却 400」）；</li>
 *   <li>{@code edited} 的判据（换过人，或时间晚于创建时间）—— {GET …/flows} 的每行都带它。</li>
 * </ol>
 *
 * @author CRYO-FLOW-001
 */
class CryoFlowRulesContractTest {

    @Test
    @DisplayName("① take / add 的 qty 是正整数，delta = ∓qty；0 与负数都拒")
    void takeAndAddArePositive() {
        assertEquals(-2, CryoFlowService.deltaOf("take", 2));
        assertEquals(2, CryoFlowService.deltaOf("add", 2));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("take", 0));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("add", 0));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("take", -1));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("add", -1));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("take", null));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("update", 1));
    }

    @Test
    @DisplayName("② adjust 带符号、不为 0，原因必填")
    void adjustIsSignedAndNeedsReason() {
        assertEquals(-1, CryoFlowService.deltaOf("adjust", -1));
        assertEquals(3, CryoFlowService.deltaOf("adjust", 3));
        assertThrows(ServiceException.class, () -> CryoFlowService.deltaOf("adjust", 0));
        assertThrows(ServiceException.class, () -> CryoFlowService.purposeOf("adjust", null));
        assertThrows(ServiceException.class, () -> CryoFlowService.purposeOf("adjust", "   "));
        assertEquals("盘点少一支", CryoFlowService.purposeOf("adjust", " 盘点少一支 "));
        // take / add 的用途可空（契约只对 adjust 写了「原因必填」）
        assertNull(CryoFlowService.purposeOf("take", null));
        assertNull(CryoFlowService.purposeOf("add", ""));
    }

    @Test
    @DisplayName("③ 发生时间：缺省取当前；带时间与纯日期两种形态都接；乱写的格式拒")
    void flowTimeParsing() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        LocalDateTime now = CryoFlowService.flowTimeOf(null);
        assertTrue(now.isAfter(before), "缺省必须是当前时间");

        assertEquals(LocalDateTime.of(2026, 9, 22, 10, 30, 0),
            CryoFlowService.flowTimeOf("2026-09-22 10:30:00"));
        assertEquals(LocalDateTime.of(2026, 9, 22, 0, 0, 0),
            CryoFlowService.flowTimeOf("2026-09-22"), "只给日期时补零点，别让实现对了却 400");
        assertThrows(ServiceException.class, () -> CryoFlowService.flowTimeOf("2026/09/22"));
        assertThrows(ServiceException.class, () -> CryoFlowService.flowTimeOf("昨天"));
    }

    @Test
    @DisplayName("④ edited 判据：换过人 → true；同一个人改的看时间；没改过 → false")
    void editedFlag() {
        Date created = new Date(1_700_000_000_000L);

        org.dromara.lqg.cryo.batch.domain.CryoFlow untouched = new org.dromara.lqg.cryo.batch.domain.CryoFlow();
        untouched.setCreateBy(101L);
        untouched.setCreateTime(created);
        assertFalse(CryoFlowService.isEdited(untouched), "刚建的登记不算改过");

        org.dromara.lqg.cryo.batch.domain.CryoFlow otherPerson = new org.dromara.lqg.cryo.batch.domain.CryoFlow();
        otherPerson.setCreateBy(101L);
        otherPerson.setCreateTime(created);
        otherPerson.setUpdateBy(102L);
        otherPerson.setUpdateTime(created);
        assertTrue(CryoFlowService.isEdited(otherPerson), "换了人改 → 一定是改过");

        org.dromara.lqg.cryo.batch.domain.CryoFlow samePerson = new org.dromara.lqg.cryo.batch.domain.CryoFlow();
        samePerson.setCreateBy(101L);
        samePerson.setCreateTime(created);
        samePerson.setUpdateBy(101L);
        samePerson.setUpdateTime(new Date(created.getTime() + 5_000L));
        assertTrue(CryoFlowService.isEdited(samePerson), "同一人晚些时候改的 → 也算改过");
    }

    @Test
    @DisplayName("⑤ 流水 VO 的读时三格都在（balanceAfter / edited / updateByName），不是 null 占位")
    void flowVoCarriesComputedKeys() throws Exception {
        for (String name : new String[]{"balanceAfter", "edited", "updateByName"}) {
            assertNotNull(CryoFlowRecordVo.class.getDeclaredField(name),
                "★ GET …/flows 的每行要带 " + name + "（上游 VO 刻意留白、本票补上）");
        }
    }

}