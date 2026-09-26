package org.dromara.lqg.doc.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 格子定高、长文字自动缩小的<b>估算规则</b>（纯函数，单测直接钉；Kevin 本机验收「网页工作台」第 4 行：
 * 「当文字较多时，预览时不要撑开内容，模板的高度尽量固定大小，文字可以适当的缩小」）。
 *
 * <p>★★ 规则：
 * <ol>
 *   <li><b>字号阶梯</b>：先试原字号，放不下依次试 {@link #STEPS_PT} 里比原字号小的（12、10.5、9、8、7.5 磅），
 *       取<b>第一个放得下的</b>（= 放得下的最大字号）；</li>
 *   <li><b>放得下</b> = 行数 × 行高 ≤ 这一格可用的高度。行高 = 字号 × {@link #LINE_RATIO}（段落设成固定行距，
 *       Word / WPS / LibreOffice 三家都按这个值排，不再各按各的字体度量与文档网格 —— 见 {@code DocCellLayout}）；</li>
 *   <li><b>行数</b>按格子净宽逐字模拟折行：中文（及全角标点、其它宽字符）1 个字宽；拉丁字母、数字、半角标点
 *       按 Times New Roman 的字宽（下载的 Word 是 Times New Roman，PDF 里换成的 Tinos 与它同度量）；
 *       中文与西文 / 数字相邻处按 Word 的「自动调整中西文间距」加 1/4 字宽；西文单词、数字串不从中间断
 *       （连字符后可断，比一行还长才逐字断）；句末标点不出现在行首（带着前一个字一起折）、开括号不留在行尾；
 *       显式换行照算；净宽只用 {@link #WIDTH_SAFETY}（两家的列宽取整有几个像素的出入，宁可估多一行）；</li>
 *   <li>缩到最小字号仍放不下：用最小字号，<b>这一行允许长高</b>（行高是「最小值」，格子自己会撑开），
 *       <b>绝不截断文字</b>。</li>
 * </ol>
 *
 * <p>★ 单位：宽高一律 twips（1/20 磅，与 docx 里的列宽、行高同一单位）；字号用磅。
 *
 * @author H 批 H4 组
 */
public final class DocCellFit {

    private DocCellFit() {
    }

    /** 缩字阶梯（磅），从大到小。原字号放不下时依次试其中<b>比原字号小</b>的几档。 */
    public static final double[] STEPS_PT = {12, 10.5, 9, 8, 7.5};

    /** 最小字号（磅）：缩到这里还放不下就让这一行长高。 */
    public static final double MIN_PT = 7.5;

    /** 行高 = 字号 × 1.3（固定行距；12 磅 → 15.6 磅，正好是模板文档网格的一行，原件的样子不变）。 */
    public static final double LINE_RATIO = 1.3;

    /** 估宽时只用格子净宽的 98%（Word、LibreOffice 排自动列宽的表格时实际给的宽度有 1%~2% 的出入）。 */
    public static final double WIDTH_SAFETY = 0.98;

    /** 中文与西文 / 数字相邻处的自动间距（字宽的 1/4，Word 的「自动调整中文与西文的间距」）。 */
    static final double AUTO_SPACE_EM = 0.25;

    /** 不能出现在行首的标点（跟前一个字走）。 */
    private static final String NO_LINE_START = "，。、；：？！）」』】〉》〕｝”’％…—·．,.;:?!)]}%";

    /** 不能留在行尾的标点（跟后一个字走）。 */
    private static final String NO_LINE_END = "（「『【〈《〔｛“‘([{";

    /**
     * Times New Roman 的 ASCII 字宽（千分之一字宽，0x20..0x7E；与 Adobe Times-Roman 度量一致，Tinos 同度量）。
     */
    private static final int[] TIMES_WIDTH = {
        250, 333, 408, 500, 500, 833, 778, 180, 333, 333, 500, 564, 250, 333, 250, 278,   // space ! " # $ % & ' ( ) * + , - . /
        500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 278, 278, 564, 564, 564, 444,   // 0-9 : ; < = > ?
        921, 722, 667, 667, 722, 611, 556, 722, 722, 333, 389, 722, 611, 889, 722, 722,   // @ A-O
        556, 722, 667, 556, 611, 722, 722, 944, 722, 722, 611, 333, 278, 333, 469, 500,   // P-Z [ \ ] ^ _
        333, 444, 500, 444, 500, 444, 333, 500, 500, 278, 278, 500, 278, 778, 500, 500,   // ` a-o
        500, 500, 333, 389, 278, 500, 500, 722, 500, 500, 444, 480, 200, 480, 541          // p-z { | } ~
    };

    /**
     * 一次估算的结论。
     *
     * @param sizePt       用的字号（磅）
     * @param lines        折成几行
     * @param lineTwips    每行多高（twips，固定行距的值）
     * @param heightTwips  文字总高 = lines × lineTwips
     * @param fits         {@code false} = 缩到最小字号仍放不下（这一行会长高）
     */
    public record Fit(double sizePt, int lines, int lineTwips, int heightTwips, boolean fits) {

        /** docx 里的字号单位（半磅）。 */
        public int halfPoints() {
            return (int) Math.round(sizePt * 2);
        }

        /** 与原字号比是不是缩过。 */
        public boolean shrunk(double originalPt) {
            return sizePt < originalPt;
        }
    }

    /**
     * 这个原字号要试的字号，从大到小：原字号 + 阶梯里比它小的几档。
     */
    public static List<Double> ladder(double originalPt) {
        List<Double> sizes = new ArrayList<>();
        sizes.add(originalPt);
        for (double step : STEPS_PT) {
            if (step < originalPt - 1e-9) {
                sizes.add(step);
            }
        }
        return sizes;
    }

    /** 这个字号的固定行距（twips）。 */
    public static int lineTwips(double sizePt) {
        return (int) Math.round(sizePt * LINE_RATIO * 20);
    }

    /**
     * 挑字号：从原字号起逐档试，取第一个「行数 × 行高 ≤ 可用高度」的。
     *
     * @param text          要填的文字（null = 空；可含换行）
     * @param originalPt    模板上这一格原来的字号
     * @param widthTwips    格子净宽（已扣左右单元格边距）
     * @param heightTwips   可用高度（行高扣掉上下单元格边距、段前段后）
     * @param prefixTwips   第一行开头已被占掉的宽度（例如嵌入附件的图标；没有就 0）
     * @param minLineTwips  每行至少多高（这一段里有比字高的东西时用，例如图标；没有就 0）
     */
    public static Fit fit(String text, double originalPt, int widthTwips, int heightTwips,
                          int prefixTwips, int minLineTwips) {
        Fit last = null;
        for (double size : ladder(originalPt)) {
            int lineHeight = Math.max(lineTwips(size), minLineTwips);
            int lines = lines(text, size, widthTwips, prefixTwips);
            int height = lines * lineHeight;
            if (height <= heightTwips) {
                return new Fit(size, lines, lineHeight, height, true);
            }
            last = new Fit(size, lines, lineHeight, height, false);
        }
        return last;
    }

    /** 同上，没有前缀、没有行高下限（普通文字格）。 */
    public static Fit fit(String text, double originalPt, int widthTwips, int heightTwips) {
        return fit(text, originalPt, widthTwips, heightTwips, 0, 0);
    }

    /**
     * 这段文字在这个字号、这个净宽下折成几行（空文字也占一行：空段落照样有一行高）。
     */
    public static int lines(String text, double sizePt, int widthTwips, int prefixTwips) {
        double available = Math.max(1, widthTwips * WIDTH_SAFETY);
        double em = sizePt * 20;
        String[] hardLines = (text == null ? "" : text).split("\r\n|\n|\r", -1);
        int total = 0;
        for (int i = 0; i < hardLines.length; i++) {
            total += wrap(units(hardLines[i]), em, available, i == 0 ? Math.max(0, prefixTwips) : 0);
        }
        return Math.max(1, total);
    }

    /** 一个不可再分的排版单位（一个中文字连同粘着它的标点 / 一个西文单词片段 / 一个空格）。 */
    private record Unit(String text, boolean space, boolean cjkEdgeStart, boolean cjkEdgeEnd) {
    }

    /** 一行（没有显式换行）按贪心折行，返回行数。 */
    private static int wrap(List<Unit> units, double em, double available, double prefix) {
        int lines = 1;
        double used = prefix;
        boolean lineHasText = false;
        Unit previous = null;
        for (Unit unit : units) {
            if (unit.space()) {
                // 行首的空格不占地方；行尾的空格挂在行外，也不引起折行
                if (lineHasText) {
                    used += widthEm(' ') * em;
                }
                previous = unit;
                continue;
            }
            double width = widthOf(unit.text(), em);
            if (lineHasText && previous != null && !previous.space()
                && previous.cjkEdgeEnd() != unit.cjkEdgeStart()) {
                width += AUTO_SPACE_EM * em;
            }
            if (used + width <= available + 1e-6) {
                used += width;
                lineHasText = true;
            } else if (!lineHasText && used <= prefix + 1e-6 && prefix <= 0) {
                // 一个单位比一整行还宽（很长的编号、网址）：只能逐字断
                double[] state = breakChars(unit.text(), em, available, used);
                lines += (int) state[0];
                used = state[1];
                lineHasText = true;
            } else {
                lines++;
                used = 0;
                double plain = widthOf(unit.text(), em);
                if (plain <= available + 1e-6) {
                    used = plain;
                } else {
                    double[] state = breakChars(unit.text(), em, available, 0);
                    lines += (int) state[0];
                    used = state[1];
                }
                lineHasText = true;
            }
            previous = unit;
        }
        return lines;
    }

    /** 逐字断：返回 {多出的行数, 最后一行已用宽度}。 */
    private static double[] breakChars(String text, double em, double available, double start) {
        int extra = 0;
        double used = start;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            double w = widthEm(cp) * em;
            if (used > 0 && used + w > available + 1e-6) {
                extra++;
                used = 0;
            }
            used += w;
            i += Character.charCount(cp);
        }
        return new double[] {extra, used};
    }

    /**
     * 切成排版单位：中文字各自一个（句末标点粘前一个字、开括号粘后一个字），西文按单词（连字符后可断），空格单独一个。
     */
    private static List<Unit> units(String line) {
        List<Unit> units = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        StringBuilder pendingOpen = new StringBuilder();
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            i += Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            if (cp == ' ' || cp == '\t' || cp == 0x00A0) {
                flushWord(units, word, pendingOpen);
                units.add(new Unit(" ", true, false, false));
                continue;
            }
            boolean wide = isWide(cp);
            if (NO_LINE_START.indexOf(cp) >= 0 && (wide || word.length() == 0)) {
                // 句末标点：粘到前一个单位上（没有前一个就自成一个）
                flushWord(units, word, pendingOpen);
                if (!units.isEmpty() && !units.get(units.size() - 1).space()) {
                    Unit prev = units.remove(units.size() - 1);
                    units.add(new Unit(prev.text() + ch, false, prev.cjkEdgeStart(), wide || prev.cjkEdgeEnd()));
                } else {
                    units.add(new Unit(ch, false, wide, wide));
                }
                continue;
            }
            if (NO_LINE_END.indexOf(cp) >= 0 && (wide || word.length() == 0)) {
                flushWord(units, word, pendingOpen);
                pendingOpen.append(ch);
                continue;
            }
            if (wide) {
                flushWord(units, word, pendingOpen);
                String text = pendingOpen + ch;
                pendingOpen.setLength(0);
                units.add(new Unit(text, false, true, true));
                continue;
            }
            word.append(ch);
            if (cp == '-') {
                // 连字符后可以断（「2026-09-」「24」）
                flushWord(units, word, pendingOpen);
            }
        }
        flushWord(units, word, pendingOpen);
        if (pendingOpen.length() > 0) {
            units.add(new Unit(pendingOpen.toString(), false, true, true));
        }
        return units;
    }

    private static void flushWord(List<Unit> units, StringBuilder word, StringBuilder pendingOpen) {
        if (word.length() == 0) {
            return;
        }
        boolean openWide = pendingOpen.length() > 0 && isWide(pendingOpen.codePointAt(0));
        units.add(new Unit(pendingOpen + word.toString(), false, openWide, false));
        pendingOpen.setLength(0);
        word.setLength(0);
    }

    private static double widthOf(String text, double em) {
        double w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += widthEm(cp) * em;
            i += Character.charCount(cp);
        }
        return w;
    }

    /**
     * 一个字符的宽度（以字宽为单位）：ASCII 按 Times New Roman；Latin-1 补充按 0.72（偏宽估）；
     * 中文、全角标点与其它宽字符按 1。
     */
    static double widthEm(int cp) {
        if (cp >= 0x20 && cp <= 0x7E) {
            return TIMES_WIDTH[cp - 0x20] / 1000.0;
        }
        if (cp >= 0xA0 && cp <= 0xFF) {
            return 0.72;
        }
        return 1.0;
    }

    /** 宽字符（按一个整字宽排、与西文之间有自动间距）：ASCII 与 Latin-1 以外的一律算宽。 */
    static boolean isWide(int cp) {
        return cp > 0xFF;
    }
}
