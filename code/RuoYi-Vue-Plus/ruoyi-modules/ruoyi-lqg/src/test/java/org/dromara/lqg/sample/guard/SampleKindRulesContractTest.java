package org.dromara.lqg.sample.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 样本类别规则的契约测试（SAMPLE-MODEL-001）。
 *
 * <p>钉住本张最容易做反的四点里与「纯规则」有关的三点：
 * <ol>
 *   <li>不是两份模板两张表：一个 {@code sample_kind} 分化出两套必填集；</li>
 *   <li>另一类的类型字段<b>不是</b>本类的必填项（给 organoid 传 tissueType 不报错，
 *       但 organoid 缺 organoidType 一定拒）；</li>
 *   <li>内部编号手填、不自动生成 —— 规则里只有「空就拒」，没有任何生成逻辑。</li>
 * </ol>
 *
 * <p>这些用例**必须**与 accept 第 3 条的两个病灶同向：accept 里
 * {@code {"sampleKind":"organoid",…} 不带 organoidType} 必须被拒（400/500 + 库内 0 行），
 * {@code {"sampleKind":"tissue",…} 不带 internalNo} 必须被拒。
 *
 * @author SAMPLE-MODEL-001
 */
class SampleKindRulesContractTest {

    @Test
    void tissueRequiresTissueTypeButNotOrganoidType() {
        // tissue：组织类型 + 内部编号 + 收样日期 都齐 → 不缺
        assertTrue(SampleKindRules.missingRequiredFields(
            "tissue", "肝组织", null, "T-hli01", LocalDate.of(2026, 9, 17)).isEmpty());
        // tissue 缺组织类型 → 拒
        assertEquals(List.of("组织类型"), SampleKindRules.missingRequiredFields(
            "tissue", null, "肝类器官", "T-hli01", LocalDate.of(2026, 9, 17)));
        // tissue 带 organoidType 但没有 tissueType → 仍拒（不许拿别类的字段顶上）
        assertEquals(List.of("组织类型"), SampleKindRules.missingRequiredFields(
            "tissue", null, "肝类器官", "T-hli01", LocalDate.of(2026, 9, 17)));
    }

    @Test
    void organoidRequiresOrganoidTypeButNotTissueType() {
        // organoid：类器官类型 + 内部编号 + 收样日期 都齐 → 不缺
        assertTrue(SampleKindRules.missingRequiredFields(
            "organoid", null, "结直肠类器官", "T-oco01", LocalDate.of(2026, 9, 17)).isEmpty());
        // organoid 缺类器官类型（accept 第 3 条的 T-oco77 就是这个形态）→ 拒
        assertEquals(List.of("类器官类型"), SampleKindRules.missingRequiredFields(
            "organoid", null, null, "T-oco77", LocalDate.of(2026, 9, 17)));
        // organoid 只带 tissueType 也不行
        assertEquals(List.of("类器官类型"), SampleKindRules.missingRequiredFields(
            "organoid", "肝组织", null, "T-oco77", LocalDate.of(2026, 9, 17)));
    }

    @Test
    void internalNoIsFilledByHandAndMustNotBeBlank() {
        // 手填：空 → 拒（accept 第 3 条的 T-hli01 形态）
        assertEquals(List.of("内部编号"), SampleKindRules.missingRequiredFields(
            "tissue", "肝组织", null, null, LocalDate.of(2026, 9, 17)));
        assertEquals(List.of("内部编号"), SampleKindRules.missingRequiredFields(
            "tissue", "肝组织", null, "   ", LocalDate.of(2026, 9, 17)));
        // 规则里**没有**任何「自动生成」的痕迹：不传就只是缺失，不会补一个值出来
        assertFalse(SampleKindRules.missingRequiredFields(
            "tissue", "肝组织", null, "T-hli01", LocalDate.of(2026, 9, 17)).contains("内部编号"));
    }

    @Test
    void receiveDateIsRequiredForBothKinds() {
        assertEquals(List.of("收样日期"), SampleKindRules.missingRequiredFields(
            "tissue", "肝组织", null, "T-hli01", null));
        assertEquals(List.of("收样日期"), SampleKindRules.missingRequiredFields(
            "organoid", null, "结直肠类器官", "T-oco01", null));
    }

    @Test
    void unknownKindIsNotSilentlyTreatedAsTissue() {
        assertTrue(SampleKindRules.isKnownKind("tissue"));
        assertTrue(SampleKindRules.isKnownKind(" organoid "));
        assertFalse(SampleKindRules.isKnownKind(null));
        assertFalse(SampleKindRules.isKnownKind(""));
        assertFalse(SampleKindRules.isKnownKind("para"));
    }

    @Test
    void submitNoIsSjPlusEightDigits() {
        // 形状 = 'SJ' + lpad(seq, 8, '0')；accept 第 2 条断 .submitNo 匹配 ^SJ[0-9]{8}$
        assertEquals("SJ00000001", SampleKindRules.formatSubmitNo(1));
        assertEquals("SJ90000001", SampleKindRules.formatSubmitNo(90000001));
        assertEquals("SJ99999999", SampleKindRules.formatSubmitNo(99999999));
        assertTrue(SampleKindRules.formatSubmitNo(1234).matches("^SJ[0-9]{8}$"));
        assertFalse(SampleKindRules.formatSubmitNo(1).matches("^SJ[0-9]{7}$"));
    }

}
