package org.dromara.lqg.doc.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 格子定高、长文字自动缩小的估算规则（{@link DocCellFit}，纯函数）。
 *
 * <p>几何取样本质控表「患者编号」那一格（模板 v5）：列宽 1626 twips、左右单元格边距各 108 → 净宽 1410；
 * 这一行最小行高 602 twips（30.1 磅）。12 磅一行 15.6 磅，放得下一行、放不下三行 —— 正好是 Kevin 截图里被撑高的那种。
 *
 * @author H 批 H4 组
 */
class DocCellFitTest {

    private static final int WIDTH = 1410;
    private static final int HEIGHT = 602;

    @Test
    @DisplayName("字号阶梯：原字号 + 比它小的 12 / 10.5 / 9 / 8 / 7.5；行距 = 字号 × 1.3")
    void ladderAndLineHeight() {
        assertEquals(List.of(12.0, 10.5, 9.0, 8.0, 7.5), DocCellFit.ladder(12));
        assertEquals(List.of(14.0, 12.0, 10.5, 9.0, 8.0, 7.5), DocCellFit.ladder(14));
        assertEquals(List.of(10.5, 9.0, 8.0, 7.5), DocCellFit.ladder(10.5));
        assertEquals(312, DocCellFit.lineTwips(12), "12 磅一行 15.6 磅 = 模板文档网格的一行");
        assertEquals(273, DocCellFit.lineTwips(10.5));
        assertEquals(195, DocCellFit.lineTwips(7.5));
    }

    @Test
    @DisplayName("① 短：「P-2609017」原字号 12 磅一行放下，不缩")
    void shortTextKeepsOriginalSize() {
        DocCellFit.Fit fit = DocCellFit.fit("P-2609017", 12, WIDTH, HEIGHT);
        assertEquals(12.0, fit.sizePt());
        assertEquals(1, fit.lines());
        assertTrue(fit.fits());
        assertFalse(fit.shrunk(12));
    }

    @Test
    @DisplayName("② 中：Kevin 截图里的「我的天哪，怎么测试呢诶」12 磅要三行（撑高），缩到 10.5 磅两行放下")
    void mediumTextShrinksOneStep() {
        assertEquals(3, DocCellFit.lines("我的天哪，怎么测试呢诶", 12, WIDTH, 0), "12 磅一行只放得下 5 个字");
        DocCellFit.Fit fit = DocCellFit.fit("我的天哪，怎么测试呢诶", 12, WIDTH, HEIGHT);
        assertEquals(10.5, fit.sizePt());
        assertEquals(2, fit.lines());
        assertEquals(546, fit.heightTwips());
        assertTrue(fit.fits() && fit.heightTwips() <= HEIGHT);
    }

    @Test
    @DisplayName("③ 长：14 个字 10.5 磅要三行，9 磅两行放下 → 9 磅（取放得下的最大一档）")
    void longTextShrinksFurther() {
        String text = "肝右叶近膈顶处及门静脉右后支";
        assertEquals(14, text.length());
        assertEquals(3, DocCellFit.lines(text, 10.5, WIDTH, 0));
        DocCellFit.Fit fit = DocCellFit.fit(text, 12, WIDTH, HEIGHT);
        assertEquals(9.0, fit.sizePt());
        assertEquals(2, fit.lines());
        assertTrue(fit.fits());
    }

    @Test
    @DisplayName("④ 超长：缩到最小 7.5 磅仍放不下 → 用 7.5 磅、如实报出要几行（这一行长高），不截断")
    void overlongTextUsesMinimumAndGrows() {
        String text = "类器官样本来自肝右叶切除标本边缘区域组织块编号甲乙丙丁戊";
        DocCellFit.Fit fit = DocCellFit.fit(text, 12, WIDTH, HEIGHT);
        assertEquals(DocCellFit.MIN_PT, fit.sizePt());
        assertFalse(fit.fits());
        assertEquals(DocCellFit.lines(text, 7.5, WIDTH, 0), fit.lines());
        assertTrue(fit.heightTwips() > HEIGHT, "放不下时报出的总高就是这一行要长到的高度");
        assertEquals(fit.lines() * DocCellFit.lineTwips(7.5), fit.heightTwips());
    }

    @Test
    @DisplayName("挑的永远是放得下的最大一档：随机文字逐档核对（大一档一定放不下）")
    void picksTheLargestSizeThatFits() {
        Random random = new Random(20260924);
        String pool = "样本组织细胞活率类器官质控表肝右叶手术切除，。；（）ABCDEFG abcdefg 0123456789-:/";
        for (int round = 0; round < 500; round++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + random.nextInt(80);
            for (int i = 0; i < len; i++) {
                sb.append(pool.charAt(random.nextInt(pool.length())));
            }
            int width = 600 + random.nextInt(3000);
            int height = 300 + random.nextInt(3000);
            DocCellFit.Fit fit = DocCellFit.fit(sb.toString(), 12, width, height);
            for (double bigger : DocCellFit.ladder(12)) {
                if (bigger <= fit.sizePt()) {
                    break;
                }
                int need = DocCellFit.lines(sb.toString(), bigger, width, 0) * DocCellFit.lineTwips(bigger);
                assertTrue(need > height, "更大的 " + bigger + " 磅其实放得下：" + sb);
            }
            if (fit.fits()) {
                assertTrue(fit.heightTwips() <= height);
            } else {
                assertEquals(DocCellFit.MIN_PT, fit.sizePt());
            }
        }
    }

    @Test
    @DisplayName("折行规则：显式换行照算；西文单词不从中间断；连字符后可断；句末标点不上行首")
    void wrapRules() {
        assertEquals(2, DocCellFit.lines("第一行\n第二行", 12, 5000, 0), "显式换行");
        assertEquals(3, DocCellFit.lines("a\r\n\nb", 12, 5000, 0), "空行也占一行");
        assertEquals(1, DocCellFit.lines("", 12, 5000, 0), "空格子也有一行高");
        // 「ab Hepatocellular」一行差一点放不下（Hepatocellular ≈ 5.9 字宽）：单词整体换到下一行，不从中间断
        assertEquals(2, DocCellFit.lines("ab Hepatocellular", 12, 7 * 240 + 40, 0));
        // 「2026-09-24」在连字符后可断
        assertEquals(2, DocCellFit.lines("2026-09-24", 12, (int) Math.ceil(3.2 * 240 / DocCellFit.WIDTH_SAFETY), 0));
        // 每行放 3 个字：「，」不能起行，带着前一个字一起折 →「甲乙」「丙，丁」「戊己」「庚，」4 行
        //（不管标点的话是「甲乙丙」「，丁戊」「己庚，」3 行 —— 实际排版器不会这么排，估少了格子就被撑高）
        assertEquals(4, DocCellFit.lines("甲乙丙，丁戊己庚，", 12, (int) Math.ceil(3 * 240 / DocCellFit.WIDTH_SAFETY), 0));
        // 超过一行的长串（网址、编号）只能逐字断
        assertEquals(3, DocCellFit.lines("ABCDEFGHABCDEFGHABCDEFGH", 12, 8 * 240, 0));
    }

    @Test
    @DisplayName("前缀（嵌入附件的图标）占掉第一行开头；行高下限（图标比字高）按每行计")
    void prefixAndMinimumLine() {
        String name = "细胞活率测定报告.pdf";
        int width = 3196;
        assertEquals(1, DocCellFit.lines(name, 12, width, DocOleEmbedder.ICON_ADVANCE_TWIPS));
        DocCellFit.Fit fit = DocCellFit.fit(name, 12, width, 614, DocOleEmbedder.ICON_ADVANCE_TWIPS,
            DocOleEmbedder.ICON_LINE_TWIPS);
        assertEquals(12.0, fit.sizePt(), "常见的文件名一行放得下，不缩");
        assertEquals(DocOleEmbedder.ICON_LINE_TWIPS, fit.lineTwips(), "有图标的那一段每行至少比图标高");
        assertTrue(DocCellFit.lines("一二三四五六七八九十一二三", 12, width, 0)
            < DocCellFit.lines("一二三四五六七八九十一二三", 12, width, 2000), "前缀越宽，第一行放的字越少");
    }
}
