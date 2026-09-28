package org.dromara.lqg.embed.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * 染色 / marker 表达的纯函数规则契约测试（EMBED-MODEL-001）。
 *
 * <p>钉住 ticket §0 口径复述 1 的四条与 §2 的「落库按固定顺序」：
 * <b>固定顺序</b>（{@code ["IHC","HE"]} → 落 {@code HE,IHC}）、<b>NONE 互斥</b>、
 * <b>OTHER 必须写名称</b>、<b>字典外的值被拒</b>（accept 2 的 PAS）。
 *
 * <p>期望值是 ticket 正文的独立重述，不是从实现反推：五个按钮
 * {@code HE / IF / IHC / OTHER / NONE}（会 L46 补的「无染色」）。
 *
 * @author EMBED-MODEL-001
 */
class EmbedRulesContractTest {

    /**
     * 字典 lqg_stain_type 的 value（与 SSOT 逐字一致）。
     */
    private static final Set<String> STAIN_DICT = Set.of("HE", "IF", "IHC", "OTHER", "NONE");

    /**
     * 字典 lqg_marker_expr 的 value。
     */
    private static final Set<String> MARKER_DICT = Set.of("negative", "weak", "strong");

    @Test
    @DisplayName("落库按固定顺序 HE,IF,IHC,OTHER,NONE：IHC,HE → HE,IHC")
    void orderIsFixed() {
        List<String> ordered = StainRules.normalize(List.of("IHC", "HE"), STAIN_DICT, null);
        assertEquals(List.of("HE", "IHC"), ordered);
        assertEquals("HE,IHC", StainRules.toCsv(ordered), "落库串必须是固定顺序，不是请求顺序");
        // seed 里 2005 的 HE,IF 与 2004 的 OTHER 也要能原样往返
        assertEquals("HE,IF", StainRules.toCsv(StainRules.normalize(List.of("IF", "HE"), STAIN_DICT, null)));
        assertEquals("OTHER", StainRules.toCsv(StainRules.normalize(List.of("OTHER"), STAIN_DICT, "Masson")));
    }

    @Test
    @DisplayName("五个按钮：HE / IF / IHC / OTHER / NONE 是全部合法值，顺序即落库顺序")
    void fiveButtonsAreTheWholeSet() {
        assertEquals(List.of("HE", "IF", "IHC", "OTHER", "NONE"), StainRules.ORDER);
        // NONE 与其余互斥，所以「四个非 NONE 全选」才是能落库的最大一组
        assertEquals("HE,IF,IHC,OTHER",
            StainRules.toCsv(StainRules.normalize(List.of("OTHER", "IHC", "HE", "IF"), STAIN_DICT, "Masson")));
        assertThrows(ServiceException.class,
            () -> StainRules.normalize(List.of("NONE", "IHC", "HE", "IF"), STAIN_DICT, null));
    }

    @Test
    @DisplayName("NONE 与其余四个互斥（后端就得拦，不能只靠前端）")
    void noneIsExclusive() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> StainRules.normalize(List.of("NONE", "HE"), STAIN_DICT, null));
        assertTrue(e.getMessage().contains("互斥"), e.getMessage());
        // NONE 单独选是合法的（1004 的 T-E02-1 就是它）
        assertEquals(List.of("NONE"), StainRules.normalize(List.of("NONE"), STAIN_DICT, null));
    }

    @Test
    @DisplayName("选了 OTHER 必须写具体名称；不选 OTHER 时不带名称")
    void otherRequiresName() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> StainRules.normalize(List.of("OTHER"), STAIN_DICT, "   "));
        assertTrue(e.getMessage().contains("其他"), e.getMessage());
        // 只有含 OTHER 时 stainOther 才是「必填」；不含时落库置空（由 EmbedService 负责置空）
        assertTrue(StainRules.hasOther(List.of("HE", "OTHER")));
        assertTrue(!StainRules.hasOther(List.of("HE")));
    }

    @Test
    @DisplayName("字典外的值被拒（accept 2 第 3 段的 PAS）")
    void unknownValueRejected() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> StainRules.normalize(List.of("PAS"), STAIN_DICT, null));
        assertTrue(e.getMessage().contains("PAS"), e.getMessage());
        // 即使字典服务返回的集合缺了某个合法值，也仍然拒（两层判据）
        assertThrows(ServiceException.class,
            () -> StainRules.normalize(List.of("IF"), Set.of("HE"), null));
    }

    @Test
    @DisplayName("读库形态：逗号串 → 数组（空串不是 [\"\"]），字典外的历史值也原样带出")
    void fromCsvIsSafe() {
        assertEquals(List.of("HE", "IHC"), StainRules.fromCsv("HE,IHC"));
        assertEquals(List.of("HE", "IHC"), StainRules.fromCsv("IHC,HE"), "读侧也按固定顺序重排");
        assertEquals(List.of(), StainRules.fromCsv(null));
        assertEquals(List.of(), StainRules.fromCsv(""));
        assertEquals(List.of(), StainRules.fromCsv(" , "));
        assertEquals(List.of("HE", "ZZZ"), StainRules.fromCsv("ZZZ,HE"), "脏数据不吞掉，排在已知值之后");
    }

    @Test
    @DisplayName("去空白、去重；空数组 = 还没选（落库 null）")
    void blanksAndDuplicates() {
        assertEquals(List.of("HE"), StainRules.normalize(List.of("HE", "  "), STAIN_DICT, null));
        assertEquals(List.of("HE"), StainRules.normalize(List.of("HE", "HE"), STAIN_DICT, null));
        assertEquals(null, StainRules.toCsv(StainRules.normalize(List.of(), STAIN_DICT, null)));
        assertEquals(null, StainRules.toCsv(StainRules.normalize(null, STAIN_DICT, null)));
    }

    @Test
    @DisplayName("marker 表达：negative / weak / strong 三选一，空与字典外的值都拒")
    void markerExpression() {
        assertEquals("strong", MarkerExprRules.normalize(" strong ", MARKER_DICT));
        assertEquals(List.of("negative", "weak", "strong"), MarkerExprRules.ALLOWED);
        assertThrows(ServiceException.class, () -> MarkerExprRules.normalize(null, MARKER_DICT));
        assertThrows(ServiceException.class, () -> MarkerExprRules.normalize("  ", MARKER_DICT));
        assertThrows(ServiceException.class, () -> MarkerExprRules.normalize("STRONG", MARKER_DICT));
        assertThrows(ServiceException.class, () -> MarkerExprRules.normalize("positive", MARKER_DICT));
    }

}
