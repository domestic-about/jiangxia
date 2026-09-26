package org.dromara.lqg.doc.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片位里 1~3 张图怎么排（甲方 2026-09-24 意见第 26 行：「不太美观」）—— 纯函数，单测直接钉。
 *
 * <p>★★ 三条硬要求（C 组任务书要求二第 4 条）：<b>不变形</b>（每张等比缩放）、<b>不溢出单元格</b>
 * （所有图排完落在「格子能放图的框」里，框比模板的格子略小一圈，Word / LibreOffice 的列宽算法有几个像素的出入）、
 * <b>好看</b>（张数多时不挤成一排小图）。
 *
 * <p>★ 做法：图在模板里是同一段里的几个行内图（段落居中），放不下就自动折到下一行。于是「排几列」
 * 只由每张图的宽度决定：宽度不超过 {@code 框宽 / 列数}，一行就放得下这么多张。
 * 对 1..n 列逐个试：每张图等比塞进 {@code (框宽 / 列数) × (框高 / 行数)} 的小格，
 * 取「最小那张图面积最大」的列数（一样大取列少的）。于是：
 * <ul>
 *   <li>1 张：占满框宽（高度按比例，超高就按高度收）；</li>
 *   <li>2 张横图：样本质控表那种近方形的框里上下摞（每张比并排大），类器官质控表那种竖长框里也上下摞；
 *       两张竖图则左右并排；</li>
 *   <li>3 张：近方形框里两张一行、第三张居中另起一行；竖长框里按图的比例挑。</li>
 * </ul>
 *
 * <p>★ 多张时图与图之间要有缝：行内图挨在一起没有间距，所以每张图外面包一圈 {@code pad} 像素的白边
 * （{@code DocxRenderer} 在图片字节上加），这里算尺寸时把白边算进去 —— 相邻两张之间就是 2×pad 的缝，
 * 贴格子边的一侧留 pad。1 张时不加白边。
 *
 * @author G 批 C 组（样张 / 图片区排版）
 */
public final class DocImageLayout {

    private DocImageLayout() {
    }

    /** 宽高（px，96dpi；图片原始像素或显示尺寸）。 */
    public record Size(int width, int height) {

        public Size {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }
    }

    /** 一张图的排版结果：显示尺寸（含白边）+ 白边宽度（显示像素）。 */
    public record Placement(Size display, int pad) {
    }

    /**
     * @param sources 每张图的原始宽高（读不出尺寸的按正方形给）；顺序即排版顺序
     * @param boxW    框宽（px）
     * @param boxH    框高（px）
     * @param pad     多张时每张图四周的白边（px）；1 张时不加
     */
    public static List<Placement> fit(List<Size> sources, int boxW, int boxH, int pad) {
        int n = sources == null ? 0 : sources.size();
        List<Placement> best = new ArrayList<>();
        if (n == 0) {
            return best;
        }
        int p = n > 1 ? Math.max(0, pad) : 0;
        long bestScore = -1;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            int cellW = boxW / cols;
            int cellH = boxH / rows;
            List<Placement> trial = new ArrayList<>(n);
            long score = Long.MAX_VALUE;
            for (Size src : sources) {
                Size content = scaleInto(src, cellW - 2 * p, cellH - 2 * p);
                trial.add(new Placement(new Size(content.width() + 2 * p, content.height() + 2 * p), p));
                score = Math.min(score, (long) content.width() * content.height());
            }
            if (score > bestScore) {
                bestScore = score;
                best = trial;
            }
        }
        return best;
    }

    /**
     * 等比缩放进 {@code (maxW, maxH)}（放大也行：文档里的显示尺寸与像素数无关）。
     *
     * <p>先定受限的那一边（顶满），另一边按比例四舍五入 —— 比两边各自向下取整更准，
     * 细长图（例如 400×2000）也不会因为取整差出好几个像素而看着变形。
     */
    static Size scaleInto(Size src, int maxW, int maxH) {
        int w = Math.max(1, maxW);
        int h = Math.max(1, maxH);
        if ((double) w / src.width() <= (double) h / src.height()) {
            int outH = (int) Math.round(w * (double) src.height() / src.width());
            return new Size(w, Math.min(h, outH));
        }
        int outW = (int) Math.round(h * (double) src.width() / src.height());
        return new Size(Math.min(w, outW), h);
    }
}
