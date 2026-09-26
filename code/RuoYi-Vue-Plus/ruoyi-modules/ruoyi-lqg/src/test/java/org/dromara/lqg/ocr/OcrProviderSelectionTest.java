package org.dromara.lqg.ocr;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.ocr.config.OcrProviderFactory;
import org.dromara.lqg.ocr.config.OcrProperties;
import org.dromara.lqg.ocr.guard.OcrProviderGuard;
import org.dromara.lqg.ocr.provider.NoneOcrProvider;
import org.dromara.lqg.ocr.provider.OcrProvider;
import org.dromara.lqg.ocr.provider.PaidOcrProvider;
import org.dromara.lqg.ocr.provider.StubOcrProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * provider 选择口径的契约测试（accept 2 第 3 段点名本类）。
 *
 * <p>三条口径逐条钉住（ADR-0007、ticket §2、合同第二条第 5 款）：
 * <ol>
 *   <li>缺省（没配 provider）→ {@link NoneOcrProvider}，且它报的是「请手动填写」而不是 500；</li>
 *   <li>配了付费实现但 {@code paid-enabled=false} → <b>选不中</b>；打开才选得中；</li>
 *   <li>{@code stub} 在 prod（或没声明 profile）→ 启动护栏报错；dev / test 才允许。</li>
 * </ol>
 *
 * <p>★ 本类是**纯单测**：护栏与选择都是静态纯函数，provider 用测试替身，不起 Spring 容器。
 *
 * @author OCR-IMPL-001
 */
class OcrProviderSelectionTest {

    /**
     * 测试替身：按次收费的实现（真实实现等 OCR-SPIKE-001，本票只验「开关没开就选不中」）。
     */
    static class FakePaidProvider implements PaidOcrProvider {
        int calls = 0;

        @Override
        public List<String> recognize(byte[] image) {
            calls++;
            return List.of("付费通道不该在本票被调到");
        }
    }

    /**
     * 测试替身：免费 / 自建的实现（不实现 {@link PaidOcrProvider}）。
     */
    static class FakeFreeProvider implements OcrProvider {
        @Override
        public List<String> recognize(byte[] image) {
            return List.of("免费实现");
        }
    }

    private static Map<String, OcrProvider> providers(OcrProvider... beans) {
        Map<String, OcrProvider> map = new LinkedHashMap<>();
        map.put(NoneOcrProvider.NAME, new NoneOcrProvider());
        for (OcrProvider bean : beans) {
            map.put(OcrProviderFactory.nameOf(bean), bean);
        }
        return map;
    }

    // ── 口径 1：缺省 → none，且报「请手动填写」 ────────────────────────────────

    @Test
    @DisplayName("缺省：没配 provider → 选 none；none 抛的是带业务 msg 的 ServiceException")
    void defaultSelectsNoneAndAsksManualFill() {
        OcrProvider selected = OcrProviderGuard.select(null, false, providers());
        assertInstanceOf(NoneOcrProvider.class, selected);
        assertInstanceOf(NoneOcrProvider.class, OcrProviderGuard.select("", false, providers()));
        assertInstanceOf(NoneOcrProvider.class, OcrProviderGuard.select("   ", false, providers()));

        ServiceException error = assertThrows(ServiceException.class, () -> selected.recognize(new byte[]{1}));
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("手动填写"),
            "缺省 provider 必须提示手填，实际：" + error.getMessage());
        assertFalse(error.getMessage().contains("系统异常"), "不许让前端弹系统异常");
    }

    // ── 口径 2：按次收费的实现只在 paid-enabled=true 时被选中 ───────────────────

    @Test
    @DisplayName("付费开关关着：配了付费实现也选不中，回落 none")
    void paidProviderIsNotSelectedWhenSwitchIsOff() {
        FakePaidProvider paid = new FakePaidProvider();
        Map<String, OcrProvider> all = providers(paid);
        assertFalse(OcrProviderGuard.paidSelectable(OcrProviderFactory.nameOf(paid), false, all));

        OcrProvider selected = OcrProviderGuard.select(OcrProviderFactory.nameOf(paid), false, all);
        assertInstanceOf(NoneOcrProvider.class, selected, "paid-enabled=false 时付费实现一个都不许选中");
        assertEquals(0, paid.calls, "选不中就不该被调到");
    }

    @Test
    @DisplayName("付费开关打开：选中付费实现")
    void paidProviderIsSelectedWhenSwitchIsOn() {
        FakePaidProvider paid = new FakePaidProvider();
        Map<String, OcrProvider> all = providers(paid);
        assertTrue(OcrProviderGuard.paidSelectable(OcrProviderFactory.nameOf(paid), true, all));

        OcrProvider selected = OcrProviderGuard.select(OcrProviderFactory.nameOf(paid), true, all);
        assertInstanceOf(FakePaidProvider.class, selected);
        selected.recognize(new byte[]{1});
        assertEquals(1, paid.calls);
    }

    @Test
    @DisplayName("免费的实现不被付费开关影响（关着也能选中）")
    void freeProviderIgnoresPaidSwitch() {
        FakeFreeProvider free = new FakeFreeProvider();
        OcrProvider selected = OcrProviderGuard.select(OcrProviderFactory.nameOf(free), false, providers(free));
        assertInstanceOf(FakeFreeProvider.class, selected);
    }

    @Test
    @DisplayName("付费开关打开但配的不是付费实现 → 启动护栏报错（不许悄悄生效）")
    void paidSwitchWithNonPaidProviderIsAConfigError() {
        FakeFreeProvider free = new FakeFreeProvider();
        String freeName = OcrProviderFactory.nameOf(free);
        var error = OcrProviderGuard.configError(freeName, true, Set.of(NoneOcrProvider.NAME, freeName),
            Set.of(), new String[]{"dev"});
        assertTrue(error.isPresent(), "paid-enabled=true 配了一个不收费的实现必须被拦下");
        assertTrue(error.get().contains("付费") || error.get().contains("按次收费"),
            "错误文案要说清是付费通道的问题：" + error.get());
    }

    // ── 口径 3：stub 只在 dev / test ─────────────────────────────────────────

    @Test
    @DisplayName("prod / 未声明 profile 下配 stub → 启动护栏报错且拒绝启动")
    void stubIsRejectedInProd() {
        Set<String> names = Set.of(NoneOcrProvider.NAME, StubOcrProvider.NAME);

        var inProd = OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[]{"prod"});
        assertTrue(inProd.isPresent(), "prod 下配 stub 必须拒绝启动");
        assertTrue(inProd.get().contains("stub"), "错误文案要点名 stub：" + inProd.get());

        var noProfile = OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[0]);
        assertTrue(noProfile.isPresent(), "没声明 profile 要按不安全处理（同 MockLoginGuard）");

        var mixed = OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[]{"dev", "PROD"});
        assertTrue(mixed.isPresent(), "大小写不同的 prod 同罪");

        assertTrue(OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[]{"dev"}).isEmpty(), "dev 下配 stub 合法");
        assertTrue(OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[]{"test"}).isEmpty(), "test 下配 stub 合法");
        assertFalse(OcrProviderGuard.stubAllowed(new String[]{"prod"}));
        assertTrue(OcrProviderGuard.stubAllowed(new String[]{"dev"}));
    }

    @Test
    @DisplayName("配了不存在的实现 → 运行期回落 none，启动期报错")
    void unknownProviderFallsBackAtRuntimeAndFailsAtStartup() {
        Map<String, OcrProvider> all = providers();
        assertInstanceOf(NoneOcrProvider.class, OcrProviderGuard.select("不存在的方案", false, all));
        assertTrue(OcrProviderGuard.configError("不存在的方案", false, all.keySet(), Set.of(),
            new String[]{"dev"}).isPresent());
    }

    @Test
    @DisplayName("测试桩夹具在 classpath 上，且 01-05 五例都认得")
    void stubProviderLoadsFixtureFromClasspath() {
        StubOcrProvider stub = new StubOcrProvider();
        assertEquals(List.of("01", "02", "03", "04", "05"), stub.caseKeys(),
            "测试桩要能按 case 前两位取到五组 rawLines（夹具在模块自己的 main resources 里）");
        assertFalse(stub.recognize("03").isEmpty(), "03 号噪声用例也有文本行");
        assertTrue(stub.recognize("99").isEmpty(), "认不得的 case 前缀 → 空行，不是异常");
    }

    @Test
    @DisplayName("没带 X-Ocr-Stub-Case（小程序真机 / H5 都不发）→ 返回缺省样例 01，预填流程可演示")
    void stubProviderFallsBackToDefaultCaseWithoutHeader() {
        StubOcrProvider stub = new StubOcrProvider();
        assertEquals("01", StubOcrProvider.DEFAULT_CASE);
        List<String> expected = stub.recognize("01");
        assertFalse(expected.isEmpty(), "缺省样例必须有文本行");
        assertEquals(expected, stub.recognize((String) null), "缺头 → 01 号样例，不许再抛「需要请求头」");
        assertEquals(expected, stub.recognize(""), "空头同缺头");
        assertEquals(expected, stub.recognize("   "), "空白头同缺头");
        assertEquals(expected, stub.recognize(" 01 "), "头值两侧的空白不影响取样例");

        // 缺省样例解析出来要能真的预填（六项齐全，否则演示不出「识别 · 请核对」）
        var fields = org.dromara.lqg.ocr.domain.OcrFieldParser.parse(stub.recognize((String) null),
            Set.of("A 医院", "B 大学"));
        assertEquals(Set.of("donorName", "gender", "age", "hospitalNo", "tissueType", "sourceUnitName"),
            fields.keySet(), "缺省样例解析结果：" + fields);
    }

    @Test
    @DisplayName("缺省样例不改变 prod 护栏：prod / 未声明 profile 下仍然不许选 stub")
    void defaultCaseDoesNotWeakenProdGuard() {
        Set<String> names = Set.of(NoneOcrProvider.NAME, StubOcrProvider.NAME);
        assertTrue(OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[]{"prod"}).isPresent());
        assertTrue(OcrProviderGuard.configError(StubOcrProvider.NAME, false, names, Set.of(),
            new String[0]).isPresent());
        assertFalse(OcrProviderGuard.stubAllowed(new String[]{"prod"}));
    }

    @Test
    @DisplayName("provider 名与默认值：none 是缺省、stub 的名字是协议的一部分")
    void providerNamesAreStable() {
        assertEquals("none", NoneOcrProvider.NAME);
        assertEquals("stub", StubOcrProvider.NAME);
        assertEquals("X-Ocr-Stub-Case", StubOcrProvider.CASE_HEADER);
        assertTrue(NoneOcrProvider.MESSAGE.contains("手动填写"));
        OcrProperties properties = new OcrProperties();
        assertNotNull(properties);
    }

}
