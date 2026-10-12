package org.dromara.lqg.sample.query;

import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存版的种属筛选（{@link SampleSpeciesFilter#matches}，小程序「已完成文档清单」用）——
 * 口径必须与 SQL 版（{@code bySampleId} / 样本表的 eq）一致：空 = 不筛、{@code __none__} = 没填的、其余等值。
 *
 * @author CR-20261009-18
 */
class SampleSpeciesFilterTest {

    @Test
    @DisplayName("筛选为空（null / 空白）= 不筛：什么种属、没填的都留下")
    void blankFilterKeepsEverything() {
        assertTrue(SampleSpeciesFilter.matches(null, "人"));
        assertTrue(SampleSpeciesFilter.matches("  ", null));
        assertTrue(SampleSpeciesFilter.matches("", "移植猪"));
    }

    @Test
    @DisplayName("等值：去首尾空白后逐字相等；「猪」筛不出「移植猪」（不是模糊匹配）")
    void exactMatchOnly() {
        assertTrue(SampleSpeciesFilter.matches(" 移植猪 ", "移植猪"));
        assertFalse(SampleSpeciesFilter.matches("猪", "移植猪"));
        assertFalse(SampleSpeciesFilter.matches("人", "鼠兔"));
        assertFalse(SampleSpeciesFilter.matches("人", null), "没填种属的不算任何一个种属");
    }

    @Test
    @DisplayName("__none__ = 只要还没填种属的（null / 空白）")
    void noneMeansUnset() {
        assertTrue(SampleSpeciesFilter.matches(SampleQueryBo.SPECIES_NONE, null));
        assertTrue(SampleSpeciesFilter.matches(SampleQueryBo.SPECIES_NONE, "  "));
        assertFalse(SampleSpeciesFilter.matches(SampleQueryBo.SPECIES_NONE, "人"));
    }

}
