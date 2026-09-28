package org.dromara.lqg.doc.render;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHeight;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTParaRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVerticalJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;

import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表格里每一格的<b>版式</b>：格子定高、长文字自动缩小、填值的格子一律居中（Kevin 本机验收「网页工作台」第 4、7 行）。
 *
 * <p>在 poi-tl <b>渲染之前</b>对编译好的模板动手（这时占位符还在，能分清哪格是填值的、哪格是印死的标签）：
 * <ol>
 *   <li><b>行距一律固定</b>：表格里每一段设「不对齐文档网格」+ 固定行距 = 字号 × 1.3（12 磅 → 15.6 磅）。
 *       甲方原件带文档网格（每行 15.6 磅）：Word / WPS 里 12 磅宋体正好占一格；但转 PDF 的 LibreOffice 用思源宋体，
 *       字体自带的行高比一格高，一行文字被吸到<b>两格（31.2 磅）</b>—— 预览里两行字就把一格撑成原件的两倍高，
 *       这正是第 4 行截图里「文字一多整行被撑开」的主因。改成固定行距后三家排出来一样高，Word 里的样子不变；
 *       放图的段落只取消对齐网格（固定行距会把图裁掉），行高随图；</li>
 *   <li><b>这一行原本多高</b>：模板的最小行高，与这一行里标签格（印死的字）、填值格一行字所需高度取大 ——
 *       也就是「甲方原件填上一行短字时的样子」；纵向合并的格子取所跨各行之和；</li>
 *   <li><b>填值的格子挑字号</b>：按 {@link DocCellFit} 从原字号起逐档缩小，取放得下的最大一档；
 *       缩到最小仍放不下就用最小字号、让这一行长高（行高是「最小值」，不会截断文字）；</li>
 *   <li><b>填值的格子居中</b>：水平、垂直都居中，和标签格一致（类器官质控表原件的值是左对齐、描述是两端对齐）。
 *       垂直居中的格子里去掉段前段后（原件标签段前 2.8 磅、类器官质控表的值段前段后各 2 磅，
 *       居中之后同一行的标签与值会差出一两磅，看着不齐）。</li>
 * </ol>
 *
 * <p>★ 图片格：只取消对齐网格，排法仍由 {@link DocImageLayout} 定（1~3 张等比塞进框里，框比格子小一圈）。
 *
 * @author H 批 H4 组
 */
final class DocCellLayout {

    private DocCellLayout() {
    }

    /** 段落里没写字号、样式里也没有时按五号字（10.5 磅，中文 Word 的默认）。 */
    static final double DEFAULT_PT = 10.5;

    /** 单元格左右边距缺省（Word 的缺省 0.19 厘米 = 108 twips），上下缺省 0。 */
    private static final int DEFAULT_MARGIN_LR = 108;

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** {@code {{tag}}}（文字）/ {@code {{@tag}}}（图片）。 */
    private static final Pattern TAG = Pattern.compile("\\{\\{(@?)(\\w+)\\}\\}");

    /**
     * 填值格第一行开头要让出来的东西（嵌入附件的图标）。
     *
     * @param widthTwips   让出的宽度（图标 + 与文字之间的空格）
     * @param minLineTwips 这一段每行至少多高（图标比字高）
     */
    record Prefix(int widthTwips, int minLineTwips) {
    }

    /**
     * 排版结果：每个文字标签挑了多大字号（单测与演示用），以及标签所在段落（渲染后往细胞活率那一格插图标用）。
     */
    record Result(Map<String, DocCellFit.Fit> fits, Map<String, XWPFParagraph> paragraphs) {
    }

    /** 一格的几何信息。 */
    private record Cell(XWPFTableCell cell, int row, int gridStart, int widthTwips, int[] margins, Merge merge,
                        boolean hasText, boolean hasImage, String firstTag, double valuePt,
                        int valueSpacingTwips) {
    }

    private enum Merge { NONE, RESTART, CONTINUE }

    /**
     * @param values   文字标签 → 值（与喂给 poi-tl 的同一份）
     * @param prefixes 文字标签 → 要让出的前缀（只有嵌了附件图标的那一格有）
     */
    static Result apply(XWPFDocument document, Map<String, String> values, Map<String, Prefix> prefixes) {
        Map<String, DocCellFit.Fit> fits = new LinkedHashMap<>();
        Map<String, XWPFParagraph> paragraphs = new LinkedHashMap<>();
        for (XWPFTable table : document.getTables()) {
            layoutTable(table, values, prefixes, fits, paragraphs);
        }
        return new Result(fits, paragraphs);
    }

    private static void layoutTable(XWPFTable table, Map<String, String> values, Map<String, Prefix> prefixes,
                                    Map<String, DocCellFit.Fit> fits, Map<String, XWPFParagraph> paragraphs) {
        int[] grid = grid(table);
        int[] tableMargins = margins(table.getCTTbl().getTblPr() == null ? null
            : cellMarginsOf(table.getCTTbl().getTblPr()), new int[] {0, DEFAULT_MARGIN_LR, 0, DEFAULT_MARGIN_LR});
        List<XWPFTableRow> rows = table.getRows();
        int[] target = new int[rows.size()];
        List<List<Cell>> cells = new ArrayList<>();

        // ── 1. 每格的宽度、边距、合并状态、是不是填值格 ─────────────────────────
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            target[r] = minHeight(row.getCtRow().getTrPr());
            int[] rowMargins = row.getCtRow().isSetTblPrEx() && row.getCtRow().getTblPrEx().isSetTblCellMar()
                ? margins(row.getCtRow().getTblPrEx().getTblCellMar(), tableMargins) : tableMargins;
            List<Cell> rowCells = new ArrayList<>();
            int col = 0;
            for (XWPFTableCell cell : row.getTableCells()) {
                CTTcPr pr = cell.getCTTc().getTcPr();
                int span = pr != null && pr.isSetGridSpan() && pr.getGridSpan().getVal() != null
                    ? Math.max(1, pr.getGridSpan().getVal().intValue()) : 1;
                int width = 0;
                for (int c = col; c < col + span && c < grid.length; c++) {
                    width += grid[c];
                }
                int[] cellMargins = pr != null && pr.isSetTcMar() ? margins(pr.getTcMar(), rowMargins) : rowMargins;
                Merge merge = Merge.NONE;
                if (pr != null && pr.isSetVMerge()) {
                    merge = pr.getVMerge().getVal() == STMerge.RESTART ? Merge.RESTART : Merge.CONTINUE;
                }
                boolean hasText = false;
                boolean hasImage = false;
                String firstTag = null;
                double valuePt = DEFAULT_PT;
                int valueSpacing = 0;
                boolean filled = TAG.matcher(cell.getText()).find();
                if (filled || isVerticallyCentered(pr)) {
                    // 垂直居中的格子（填值格一律居中）：段前段后归零，文字真正落在格子的竖直中线上
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        noSpaceAround(p);
                    }
                }
                for (XWPFParagraph p : cell.getParagraphs()) {
                    Matcher m = TAG.matcher(p.getText());
                    while (m.find()) {
                        if (m.group(1).isEmpty()) {
                            if (!hasText) {
                                firstTag = m.group(2);
                                valuePt = sizeOf(p);
                                valueSpacing = spacingBefore(p, valuePt) + spacingAfter(p, valuePt);
                            }
                            hasText = true;
                        } else {
                            hasImage = true;
                        }
                    }
                }
                rowCells.add(new Cell(cell, r, col, width, cellMargins, merge, hasText, hasImage, firstTag,
                    valuePt, valueSpacing));
                col += span;
            }
            cells.add(rowCells);
        }

        // ── 2. 这一行原本多高：最小行高、标签格所需、填值格一行字所需，取大 ───────────
        for (List<Cell> rowCells : cells) {
            for (Cell c : rowCells) {
                if (c.merge() != Merge.NONE || c.hasImage()) {
                    continue;
                }
                int need;
                if (c.hasText()) {
                    Prefix prefix = prefixes.get(c.firstTag());
                    int line = Math.max(DocCellFit.lineTwips(c.valuePt()), prefix == null ? 0 : prefix.minLineTwips());
                    need = c.margins()[0] + c.margins()[2] + c.valueSpacingTwips() + line;
                } else {
                    need = labelHeight(c);
                }
                target[c.row()] = Math.max(target[c.row()], need);
            }
        }

        // ── 3. 逐格落版式 ────────────────────────────────────────────────────
        for (List<Cell> rowCells : cells) {
            for (Cell c : rowCells) {
                int available = c.merge() == Merge.RESTART ? mergedHeight(cells, target, c) : target[c.row()];
                for (XWPFParagraph p : c.cell().getParagraphs()) {
                    String text = p.getText();
                    Matcher m = TAG.matcher(text);
                    boolean image = false;
                    String tag = null;
                    while (m.find()) {
                        if (m.group(1).isEmpty()) {
                            tag = tag == null ? m.group(2) : tag;
                        } else {
                            image = true;
                        }
                    }
                    if (image) {
                        // 放图的段落：只取消对齐网格（固定行距会把图裁掉），行高随图
                        noGrid(p);
                    } else if (tag != null) {
                        DocCellFit.Fit fit = fitValue(c, p, text, available, values, prefixes.get(tag));
                        fits.put(tag, fit);
                        paragraphs.put(tag, p);
                    } else {
                        double size = sizeOf(p);
                        lineSpacing(p, DocCellFit.lineTwips(size), STLineSpacingRule.EXACT);
                        noGrid(p);
                    }
                }
                if (c.hasText()) {
                    verticalCenter(c.cell());
                }
            }
        }
    }

    /** 填值的一段：挑字号、固定行距、居中。 */
    private static DocCellFit.Fit fitValue(Cell c, XWPFParagraph p, String templateText, int availableRow,
                                           Map<String, String> values, Prefix prefix) {
        double original = sizeOf(p);
        String value = fill(templateText, values);
        int width = c.widthTwips() - c.margins()[1] - c.margins()[3];
        int height = availableRow - c.margins()[0] - c.margins()[2]
            - spacingBefore(p, original) - spacingAfter(p, original);
        DocCellFit.Fit fit = DocCellFit.fit(value, original, width, height,
            prefix == null ? 0 : prefix.widthTwips(), prefix == null ? 0 : prefix.minLineTwips());
        for (XWPFRun run : p.getRuns()) {
            run.setFontSize(fit.sizePt());
            run.setComplexScriptFontSize(fit.sizePt());
        }
        markSize(p, fit.halfPoints());
        // 有图标的那一段用「最小值」行距：固定行距会把比字高的图标裁掉
        lineSpacing(p, fit.lineTwips(), prefix == null ? STLineSpacingRule.EXACT : STLineSpacingRule.AT_LEAST);
        noGrid(p);
        p.setAlignment(ParagraphAlignment.CENTER);
        return fit;
    }

    /** 模板这一段的文字把占位符换成值（段落里只有一个占位符时就是值本身）。 */
    private static String fill(String templateText, Map<String, String> values) {
        Matcher m = TAG.matcher(templateText);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = m.group(1).isEmpty() ? values.getOrDefault(m.group(2), "") : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 标签格（印死的字）排下来要多高。 */
    private static int labelHeight(Cell c) {
        int width = c.widthTwips() - c.margins()[1] - c.margins()[3];
        int height = c.margins()[0] + c.margins()[2];
        for (XWPFParagraph p : c.cell().getParagraphs()) {
            double size = sizeOf(p);
            height += spacingBefore(p, size) + spacingAfter(p, size)
                + DocCellFit.lines(p.getText(), size, width, 0) * DocCellFit.lineTwips(size);
        }
        return height;
    }

    /** 纵向合并的格子：从这一行起往下，同一列上接着合并的各行高度之和。 */
    private static int mergedHeight(List<List<Cell>> cells, int[] target, Cell start) {
        int height = target[start.row()];
        for (int r = start.row() + 1; r < cells.size(); r++) {
            Cell below = null;
            for (Cell c : cells.get(r)) {
                if (c.gridStart() == start.gridStart()) {
                    below = c;
                    break;
                }
            }
            if (below == null || below.merge() != Merge.CONTINUE) {
                break;
            }
            height += target[r];
        }
        return height;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 读模板
    // ══════════════════════════════════════════════════════════════════════

    private static int[] grid(XWPFTable table) {
        if (table.getCTTbl().getTblGrid() == null) {
            return new int[0];
        }
        CTTblGridCol[] cols = table.getCTTbl().getTblGrid().getGridColArray();
        int[] out = new int[cols.length];
        for (int i = 0; i < cols.length; i++) {
            out[i] = twips(cols[i].getW());
        }
        return out;
    }

    /** 行的最小高度（{@code trHeight}，atLeast / exact 都按它；没写就 0）。 */
    private static int minHeight(CTTrPr trPr) {
        if (trPr == null || trPr.sizeOfTrHeightArray() == 0) {
            return 0;
        }
        CTHeight h = trPr.getTrHeightArray(0);
        return twips(h.getVal());
    }

    private static XmlObject cellMarginsOf(CTTblPr tblPr) {
        return tblPr.isSetTblCellMar() ? tblPr.getTblCellMar() : null;
    }

    /** {上, 左, 下, 右}（twips）；这一层没写的边沿用上一层。 */
    private static int[] margins(XmlObject mar, int[] fallback) {
        int[] out = fallback.clone();
        if (mar == null) {
            return out;
        }
        String[] sides = {"top", "left", "bottom", "right"};
        for (int i = 0; i < sides.length; i++) {
            XmlObject[] found = mar.selectChildren(new QName(W_NS, sides[i]));
            if (found.length == 0 && ("left".equals(sides[i]) || "right".equals(sides[i]))) {
                found = mar.selectChildren(new QName(W_NS, "left".equals(sides[i]) ? "start" : "end"));
            }
            if (found.length > 0 && found[0] instanceof CTTblWidth w && w.isSetW()) {
                out[i] = twips(w.getW());
            }
        }
        return out;
    }

    /** 段落字号（磅）：各 run 里最大的；run 都没写就看段落标记；再没有按五号字。 */
    static double sizeOf(XWPFParagraph p) {
        double max = 0;
        for (XWPFRun run : p.getRuns()) {
            Double size = run.getFontSizeAsDouble();
            if (size != null && size > max) {
                max = size;
            }
        }
        if (max > 0) {
            return max;
        }
        CTPPr ppr = p.getCTP().getPPr();
        if (ppr != null && ppr.isSetRPr() && ppr.getRPr().sizeOfSzArray() > 0) {
            int half = twips(ppr.getRPr().getSzArray(0).getVal());
            if (half > 0) {
                return half / 2.0;
            }
        }
        return DEFAULT_PT;
    }

    private static int spacingBefore(XWPFParagraph p, double sizePt) {
        CTSpacing sp = spacing(p);
        if (sp == null) {
            return 0;
        }
        if (sp.isSetBeforeLines() && sp.getBeforeLines() != null) {
            return sp.getBeforeLines().intValue() * DocCellFit.lineTwips(sizePt) / 100;
        }
        return sp.isSetBefore() ? twips(sp.getBefore()) : 0;
    }

    private static int spacingAfter(XWPFParagraph p, double sizePt) {
        CTSpacing sp = spacing(p);
        if (sp == null) {
            return 0;
        }
        if (sp.isSetAfterLines() && sp.getAfterLines() != null) {
            return sp.getAfterLines().intValue() * DocCellFit.lineTwips(sizePt) / 100;
        }
        return sp.isSetAfter() ? twips(sp.getAfter()) : 0;
    }

    private static CTSpacing spacing(XWPFParagraph p) {
        CTPPr ppr = p.getCTP().getPPr();
        return ppr != null && ppr.isSetSpacing() ? ppr.getSpacing() : null;
    }

    /** docx 里的长度值：多数是整数 twips；也可能带单位（"12pt"、"0.5in"），一律换成 twips。 */
    static int twips(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = value.toString().trim();
        try {
            if (s.endsWith("pt")) {
                return (int) Math.round(Double.parseDouble(s.substring(0, s.length() - 2)) * 20);
            }
            if (s.endsWith("in")) {
                return (int) Math.round(Double.parseDouble(s.substring(0, s.length() - 2)) * 1440);
            }
            if (s.endsWith("mm")) {
                return (int) Math.round(Double.parseDouble(s.substring(0, s.length() - 2)) * 1440 / 25.4);
            }
            if (s.endsWith("cm")) {
                return (int) Math.round(Double.parseDouble(s.substring(0, s.length() - 2)) * 1440 / 2.54);
            }
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 写属性
    // ══════════════════════════════════════════════════════════════════════

    private static CTPPr pPr(XWPFParagraph p) {
        return p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
    }

    /** 段落不对齐文档网格（{@code <w:snapToGrid w:val="0"/>}，与 Word 自己写的字面一致）。 */
    private static void noGrid(XWPFParagraph p) {
        CTPPr ppr = pPr(p);
        CTOnOff snap = ppr.isSetSnapToGrid() ? ppr.getSnapToGrid() : ppr.addNewSnapToGrid();
        try (XmlCursor cursor = snap.newCursor()) {
            cursor.setAttributeText(new QName(W_NS, "val"), "0");
        }
    }

    private static boolean isVerticallyCentered(CTTcPr pr) {
        return pr != null && pr.isSetVAlign() && pr.getVAlign().getVal() == STVerticalJc.CENTER;
    }

    /** 去掉段前段后（按行数写的也去掉）。 */
    private static void noSpaceAround(XWPFParagraph p) {
        CTSpacing sp = spacing(p);
        if (sp == null) {
            return;
        }
        if (sp.isSetBefore()) {
            sp.unsetBefore();
        }
        if (sp.isSetAfter()) {
            sp.unsetAfter();
        }
        if (sp.isSetBeforeLines()) {
            sp.unsetBeforeLines();
        }
        if (sp.isSetAfterLines()) {
            sp.unsetAfterLines();
        }
    }

    /**
     * 单元格垂直居中。★ 不用 POI 的 {@code XWPFTableCell#setVerticalAlignment}：它每次都 {@code addNewVAlign}，
     * 模板里本来就有 {@code w:vAlign} 时会写出两个（不合 schema，Word 可能报「内容有问题」）。
     */
    private static void verticalCenter(XWPFTableCell cell) {
        CTTcPr pr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTVerticalJc vAlign = pr.isSetVAlign() ? pr.getVAlign() : pr.addNewVAlign();
        vAlign.setVal(STVerticalJc.CENTER);
    }

    private static void lineSpacing(XWPFParagraph p, int lineTwips, STLineSpacingRule.Enum rule) {
        CTPPr ppr = pPr(p);
        CTSpacing sp = ppr.isSetSpacing() ? ppr.getSpacing() : ppr.addNewSpacing();
        sp.setLine(BigInteger.valueOf(lineTwips));
        sp.setLineRule(rule);
    }

    /** 段落标记（回车符）的字号跟着改：空格子、最后一行的行高也按新字号算。 */
    private static void markSize(XWPFParagraph p, int halfPoints) {
        CTPPr ppr = pPr(p);
        CTParaRPr mark = ppr.isSetRPr() ? ppr.getRPr() : ppr.addNewRPr();
        CTHpsMeasure sz = mark.sizeOfSzArray() > 0 ? mark.getSzArray(0) : mark.addNewSz();
        sz.setVal(BigInteger.valueOf(halfPoints));
        CTHpsMeasure szCs = mark.sizeOfSzCsArray() > 0 ? mark.getSzCsArray(0) : mark.addNewSzCs();
        szCs.setVal(BigInteger.valueOf(halfPoints));
    }
}
