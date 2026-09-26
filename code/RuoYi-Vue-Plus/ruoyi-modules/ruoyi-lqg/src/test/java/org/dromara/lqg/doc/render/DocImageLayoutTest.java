package org.dromara.lqg.doc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.lqg.doc.render.DocImageLayout.Placement;
import org.dromara.lqg.doc.render.DocImageLayout.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片区排版（甲方 2026-09-24 意见第 26 行「不太美观」；C 组任务书要求二第 4 条）：
 * 1 / 2 / 3 张、横图 / 竖图 / 混排，都要<b>不变形、不溢出、按一行能放几张实打实地排得下</b>。
 *
 * @author G 批 C 组
 */
class DocImageLayoutTest {

    private static final int PAD = 3;
    private static final Size LANDSCAPE = new Size(1600, 1200);
    private static final Size WIDE = new Size(1500, 1000);
    private static final Size PORTRAIT = new Size(1200, 1600);
    private static final Size TALL = new Size(400, 2000);

    /** 样本质控表的图片位框（与 DocxRenderer.SLOT_BOX_PX 同一个数）。 */
    private static final int[] SAMPLE_BOX = DocxRenderer.SLOT_BOX_PX.get("orig");
    /** 类器官质控表的图片位框。 */
    private static final int[] ORGANOID_BOX = DocxRenderer.SLOT_BOX_PX.get("organoid_observe");

    @Test
    @DisplayName("1 张：不加白边，占满框宽（横图）/ 按框高收（竖图），等比")
    void single() {
        Placement p = DocImageLayout.fit(List.of(LANDSCAPE), SAMPLE_BOX[0], SAMPLE_BOX[1], PAD).get(0);
        assertEquals(0, p.pad(), "一张图不需要缝");
        assertEquals(SAMPLE_BOX[0], p.display().width(), "横图占满框宽");
        assertRatio(LANDSCAPE, p);

        Placement tall = DocImageLayout.fit(List.of(TALL), SAMPLE_BOX[0], SAMPLE_BOX[1], PAD).get(0);
        assertEquals(SAMPLE_BOX[1], tall.display().height(), "细长竖图按框高收");
        assertRatio(TALL, tall);
    }

    @Test
    @DisplayName("所有组合：每张等比（含白边后内容等比）、宽高都不超框、排成的行数装得进框高")
    void everyCombinationFitsAndKeepsRatio() {
        List<Size> kinds = List.of(LANDSCAPE, WIDE, PORTRAIT, TALL);
        for (int[] box : List.of(SAMPLE_BOX, ORGANOID_BOX, DocxRenderer.SLOT_BOX_PX.get("pretreat"))) {
            for (List<Size> combo : combos(kinds)) {
                List<Placement> out = DocImageLayout.fit(combo, box[0], box[1], PAD);
                assertEquals(combo.size(), out.size());
                for (int i = 0; i < combo.size(); i++) {
                    Placement p = out.get(i);
                    assertTrue(p.display().width() <= box[0] && p.display().height() <= box[1], "超框：" + p);
                    assertRatio(combo.get(i), p);
                }
                // 行内图按「一行塞满再折行」排（Word / LibreOffice 都这么排）：逐行累加的高度装得进框
                assertTrue(fitsByRows(out, box), "排不进框高：box=" + box[0] + "x" + box[1] + " " + out);
            }
        }
    }

    @Test
    @DisplayName("样本质控表（近方形的框）：2 张横图上下摞、3 张横图两张一行 + 第三张另起一行，不挤成一排小图")
    void samplePicksAReadableGrid() {
        List<Placement> two = DocImageLayout.fit(List.of(LANDSCAPE, LANDSCAPE), SAMPLE_BOX[0], SAMPLE_BOX[1], PAD);
        assertTrue(two.get(0).display().width() > SAMPLE_BOX[0] / 2, "2 张横图上下摞（每张比并排时大）：" + two);
        List<Placement> three = DocImageLayout.fit(List.of(LANDSCAPE, LANDSCAPE, LANDSCAPE), SAMPLE_BOX[0], SAMPLE_BOX[1], PAD);
        int w = three.get(0).display().width();
        assertTrue(w <= SAMPLE_BOX[0] / 2 && w > SAMPLE_BOX[0] / 3, "3 张横图两张一行：" + three);
        assertTrue(w >= 110, "不挤成一排小图（旧排法每张只有 86px 宽）：" + w);
    }

    @Test
    @DisplayName("类器官质控表（竖长的框）：2 张横图上下摞、每张接近占满框宽")
    void organoidStacksLandscapes() {
        List<Placement> two = DocImageLayout.fit(List.of(LANDSCAPE, LANDSCAPE), ORGANOID_BOX[0], ORGANOID_BOX[1], PAD);
        assertTrue(two.get(0).display().width() > ORGANOID_BOX[0] * 0.8, "竖长框里两张横图上下摞：" + two);
    }

    @Test
    @DisplayName("多张时每张都带白边（相邻两张之间有 2×pad 的缝）；一张时不带")
    void padOnlyWhenSeveral() {
        assertEquals(0, DocImageLayout.fit(List.of(LANDSCAPE), 250, 226, PAD).get(0).pad());
        for (Placement p : DocImageLayout.fit(List.of(LANDSCAPE, PORTRAIT), 250, 226, PAD)) {
            assertEquals(PAD, p.pad());
        }
        assertTrue(DocImageLayout.fit(List.of(), 250, 226, PAD).isEmpty());
    }

    // ── 小工具 ──────────────────────────────────────────────────────────────

    /** 内容（去掉白边）的宽高比与原图一致：按比例推出来的那一边与实际差不到 1px（取整误差）。 */
    private static void assertRatio(Size src, Placement p) {
        int w = p.display().width() - 2 * p.pad();
        int h = p.display().height() - 2 * p.pad();
        double hFromW = w * (double) src.height() / src.width();
        double wFromH = h * (double) src.width() / src.height();
        assertTrue(Math.abs(hFromW - h) <= 1.0 || Math.abs(wFromH - w) <= 1.0,
            "变形了：原图 " + src + " → 内容 " + w + "x" + h);
    }

    /** 按「每行塞满再折行」的真实排法逐行累加高度，看装不装得进框。 */
    private static boolean fitsByRows(List<Placement> out, int[] box) {
        int used = 0;
        int lineWidth = 0;
        int lineHeight = 0;
        for (Placement p : out) {
            if (lineWidth + p.display().width() > box[0] && lineWidth > 0) {
                used += lineHeight;
                lineWidth = 0;
                lineHeight = 0;
            }
            lineWidth += p.display().width();
            lineHeight = Math.max(lineHeight, p.display().height());
        }
        return used + lineHeight <= box[1];
    }

    private static List<List<Size>> combos(List<Size> kinds) {
        List<List<Size>> out = new ArrayList<>();
        for (Size a : kinds) {
            out.add(List.of(a));
            for (Size b : kinds) {
                out.add(List.of(a, b));
                for (Size c : kinds) {
                    out.add(List.of(a, b, c));
                }
            }
        }
        return out;
    }
}
