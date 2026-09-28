package org.dromara.lqg.sample.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.junit.jupiter.api.Test;

/**
 * 核验状态机的契约测试（SAMPLE-VERIFY-001，FLOW:F-SAMPLE-01.step5）。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li><b>转移表每一格</b>：3 个状态 × 3 个目标 × 2 种操作者身份 = 18 个组合**穷举**，
 *       合法恰好 5 条、非法恰好 13 条；同一张表 **tissue 与 organoid 各跑一遍**
 *       （状态机不分 {@code sample_kind}，CR-20260917-05）；</li>
 *   <li><b>转移表与表无关</b>：{@code check} 的签名只有 {@code (String, String, boolean)}，
 *       类里没有任何指向样本实体 / 样本类别 / mapper 的成员 —— EMBED-MODEL-001 的
 *       石蜡包埋送样核验要直接复用这一份；</li>
 *   <li><b>请求体带不动状态</b>：{@code SampleSubmitBo}（{@code PUT /lqg/sample}）与
 *       {@code SampleVerifyBo} 都不得声明 {@code verifyStatus} 字段（ticket §2 末条：
 *       {@code PUT /lqg/sample} 不许再直接改 {@code verify_status}）。</li>
 * </ol>
 *
 * <p>这些用例与 accept 第 1 条同向：{@code pending→valid} 需要内部身份 + 两个必填；
 * {@code invalid→valid}（内部改判）与 {@code invalid→pending}（外部重提）都在表里；
 * {@code valid→pending} 与「外部把状态改成 valid」必须被表挡掉。
 *
 * @author SAMPLE-VERIFY-001
 */
class VerifyTransitionsContractTest {

    private static final String[] STATUSES = {
        VerifyTransitions.PENDING, VerifyTransitions.VALID, VerifyTransitions.INVALID
    };

    private static final boolean[] ACTORS = {true, false};

    @Test
    void tissueKind_wholeTransitionTableIsCovered() {
        assertWholeTable("tissue");
    }

    @Test
    void organoidKind_wholeTransitionTableIsCovered() {
        // 类器官走同一张转移表：这一遍必须与 tissue 那遍逐格一致（表里没有 sample_kind 判据）
        assertWholeTable("organoid");
    }

    /**
     * 穷举整张表。期望值在这里**独立重述**一遍 ticket §2 的表格（不是从 {@code LEGAL} 反推），
     * 所以实现写错时这里会红，而不是跟着一起错。
     */
    private void assertWholeTable(String kind) {
        int legal = 0;
        int illegal = 0;
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                for (boolean internal : ACTORS) {
                    boolean expected = expected(from, to, internal);
                    assertEquals(expected, VerifyTransitions.check(from, to, internal),
                        String.format("%s 的转移 %s → %s（操作者%s）判错了", kind, from, to,
                            internal ? "内部" : "外部"));
                    if (expected) {
                        legal++;
                    } else {
                        illegal++;
                    }
                }
            }
        }
        assertEquals(5, legal, kind + "：合法转移必须恰好 5 条");
        assertEquals(13, illegal, kind + "：非法组合必须恰好 13 条（18 个组合穷举）");
        assertEquals(5, VerifyTransitions.legalTransitions().length, "给人看的合法清单也必须是 5 条");
    }

    /**
     * ticket §2 表格的独立重述。
     */
    private static boolean expected(String from, String to, boolean internal) {
        return (VerifyTransitions.PENDING.equals(from) && VerifyTransitions.VALID.equals(to) && internal)
            || (VerifyTransitions.PENDING.equals(from) && VerifyTransitions.INVALID.equals(to) && internal)
            || (VerifyTransitions.INVALID.equals(from) && VerifyTransitions.PENDING.equals(to) && !internal)
            || (VerifyTransitions.INVALID.equals(from) && VerifyTransitions.VALID.equals(to) && internal)
            || (VerifyTransitions.VALID.equals(from) && VerifyTransitions.INVALID.equals(to) && internal);
    }

    @Test
    void theFiveLegalTransitionsAreExactlyTheOnesInTheTicket() {
        assertTrue(VerifyTransitions.check("pending", "valid", true));
        assertTrue(VerifyTransitions.check("pending", "invalid", true));
        assertTrue(VerifyTransitions.check("invalid", "pending", false));
        assertTrue(VerifyTransitions.check("invalid", "valid", true));
        assertTrue(VerifyTransitions.check("valid", "invalid", true));
    }

    @Test
    void externalCanNeverReachValid_andInternalCannotResubmitToPending() {
        // 外部不能把自己的样本改成 valid（accept 第 1 条的「外部重提只回到 pending」）
        assertFalse(VerifyTransitions.check("pending", "valid", false));
        assertFalse(VerifyTransitions.check("invalid", "valid", false));
        // valid → pending 谁都不行（「有效后外部只读」）
        assertFalse(VerifyTransitions.check("valid", "pending", false));
        assertFalse(VerifyTransitions.check("valid", "pending", true));
        // invalid → pending 是「外部本人重提」这条边：内部身份不该走它
        assertFalse(VerifyTransitions.check("invalid", "pending", true));
        // 判无效只能内部做，外部不能自己把 pending 样本置无效
        assertFalse(VerifyTransitions.check("pending", "invalid", false));
    }

    @Test
    void selfLoopsAndUnknownValuesAreRejected() {
        for (String status : STATUSES) {
            assertFalse(VerifyTransitions.check(status, status, true), status + " 的自环不是合法转移");
            assertFalse(VerifyTransitions.check(status, status, false), status + " 的自环不是合法转移");
        }
        // 「任何人把状态直接写成任意值」：未知状态一律不放行
        assertFalse(VerifyTransitions.check("weird", "valid", true));
        assertFalse(VerifyTransitions.check("pending", "weird", true));
        assertFalse(VerifyTransitions.check(null, "valid", true));
        assertFalse(VerifyTransitions.check("pending", null, true));
        assertFalse(VerifyTransitions.check("", "valid", true));
        assertFalse(VerifyTransitions.isKnownStatus("weird"));
        assertTrue(VerifyTransitions.isKnownStatus("pending"));
        assertTrue(VerifyTransitions.isKnownStatus("valid"));
        assertTrue(VerifyTransitions.isKnownStatus("invalid"));
    }

    @Test
    void actionMapsToTargetStatusOnlyThroughTheTable() {
        assertTrue(VerifyTransitions.isKnownAction("valid"));
        assertTrue(VerifyTransitions.isKnownAction("invalid"));
        assertFalse(VerifyTransitions.isKnownAction("approve"));
        assertFalse(VerifyTransitions.isKnownAction(null));
        assertEquals("valid", VerifyTransitions.targetOf("valid"));
        assertEquals("invalid", VerifyTransitions.targetOf("invalid"));
        assertThrows(IllegalArgumentException.class, () -> VerifyTransitions.targetOf("approve"));
        // 动作 → 目标状态 → 判表：这是 service 唯一的判定链
        assertTrue(VerifyTransitions.check("pending", VerifyTransitions.targetOf("valid"), true));
        assertFalse(VerifyTransitions.check("pending", VerifyTransitions.targetOf("valid"), false));
    }

    /**
     * ★ EMBED-MODEL-001 的复用前提：转移表是**与表无关的纯函数**。
     *
     * <p>签名只许是 {@code (String, String, boolean)}；类里不许出现任何样本实体 / 样本类别 /
     * mapper 类型的成员 —— 一旦有人把「内部编号必填」「sample_kind」塞进判据，石蜡包埋就复用不了。
     */
    @Test
    void transitionTableIsTableAgnosticPureFunction() throws Exception {
        Method check = VerifyTransitions.class.getMethod("check", String.class, String.class, boolean.class);
        assertTrue(Modifier.isStatic(check.getModifiers()), "check 必须是静态纯函数");
        assertEquals(3, check.getParameterCount());
        assertEquals(boolean.class, check.getReturnType());

        List<String> offenders = new ArrayList<>();
        for (Method m : VerifyTransitions.class.getDeclaredMethods()) {
            for (Class<?> p : m.getParameterTypes()) {
                if (p.getName().startsWith("org.dromara.lqg.sample")) {
                    offenders.add(m.getName() + " 的参数 " + p.getSimpleName());
                }
            }
        }
        for (Field f : VerifyTransitions.class.getDeclaredFields()) {
            String type = f.getType().getName();
            if (type.startsWith("org.dromara.lqg.sample") || type.endsWith("Mapper")) {
                offenders.add("字段 " + f.getName() + " : " + f.getType().getSimpleName());
            }
        }
        assertTrue(offenders.isEmpty(), "转移表里混进了样本特有的东西：" + offenders);

        // 纯：同样的入参永远给同样的结果
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                boolean first = VerifyTransitions.check(from, to, true);
                assertEquals(first, VerifyTransitions.check(from, to, true), "check 不是纯函数");
            }
        }
    }

    @Test
    void requestBodiesCannotCarryVerifyStatus() {
        // PUT /lqg/sample 的入参里没有 verify_status（状态只能经 /verify 改）
        assertFalse(hasField(SampleSubmitBo.class, "verifyStatus"),
            "SampleSubmitBo 声明了 verifyStatus —— PUT /lqg/sample 会绕过状态机");
        assertFalse(hasSetter(SampleSubmitBo.class, "setVerifyStatus"));
        // 核验入参里也没有：目标状态只能由 action 经转移表推出来
        assertFalse(hasField(SampleVerifyBo.class, "verifyStatus"),
            "SampleVerifyBo 声明了 verifyStatus —— 请求体能直接指定终态");
        assertFalse(hasSetter(SampleVerifyBo.class, "setVerifyStatus"));
        // 重提入参里同样没有（外部改不动状态）
        assertFalse(hasField(SampleResubmitBo.class, "verifyStatus"));
        assertFalse(hasSetter(SampleResubmitBo.class, "setVerifyStatus"));
    }

    private static boolean hasField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSetter(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

}
