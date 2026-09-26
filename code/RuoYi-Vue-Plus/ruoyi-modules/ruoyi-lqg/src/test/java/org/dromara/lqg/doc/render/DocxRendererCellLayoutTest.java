package org.dromara.lqg.doc.render;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STVerticalJc;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真模板 + 真 poi-tl：格子定高、长文字自动缩小、填值的格子居中（Kevin 本机验收「网页工作台」第 4、7 行）。
 *
 * <ol>
 *   <li>三份文档所有填值的格子：段落水平居中、单元格垂直居中（类器官质控表原件的值是左对齐，第 7 行截图）；</li>
 *   <li>行高不动：每一行的最小行高与模板逐行相同（没被改成更高，也没被改成「固定值」—— 放不下时允许长高）；</li>
 *   <li>表格里的文字段落一律「不对齐文档网格 + 固定行距」（LibreOffice 转 PDF 时不再把一行字排成两格高）；</li>
 *   <li>字号：短的不缩、长的按阶梯缩到放得下的最大一档、超长的缩到 7.5 磅且一个字不少。</li>
 * </ol>
 *
 * @author H 批 H4 组
 */
class DocxRendererCellLayoutTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^}]*}}");

    private final DocxRenderer renderer = new DocxRenderer(new DocOssBytes(null) {
        @Override
        public Fetched fetch(Long ossId) {
            return new Fetched(null, "测试里不取图");
        }
    });

    @Test
    @DisplayName("① 三份文档所有填值的格子水平、垂直居中（和标签格一致）")
    void everyFilledCellIsCentered() throws Exception {
        for (DocRenderModel model : List.of(sample(Map.of()), organoid(), score())) {
            int checked = 0;
            for (Map.Entry<String, Object[]> e : filledCells(model).entrySet()) {
                XWPFTableCell cell = (XWPFTableCell) e.getValue()[0];
                XWPFParagraph p = (XWPFParagraph) e.getValue()[1];
                assertEquals(ParagraphAlignment.CENTER, p.getAlignment(), model.getDocKind() + " 的「" + e.getKey() + "」没有水平居中");
                CTTcPr pr = cell.getCTTc().getTcPr();
                assertTrue(pr != null && pr.isSetVAlign() && pr.getVAlign().getVal() == STVerticalJc.CENTER,
                    model.getDocKind() + " 的「" + e.getKey() + "」没有垂直居中");
                assertTrue(p.getCTP().getPPr().getSpacing() == null || !p.getCTP().getPPr().getSpacing().isSetBefore(),
                    "居中的格子不留段前（否则与同一行的标签差出一两磅）");
                checked++;
            }
            assertTrue(checked >= 5, model.getDocKind() + " 只找到 " + checked + " 个填值格");
        }
    }

    @Test
    @DisplayName("② 行高与模板逐行相同；③ 文字段落不对齐网格、固定行距（放图的段落只取消对齐网格）")
    void rowHeightsKeptAndLineSpacingFixed() throws Exception {
        for (DocRenderModel model : List.of(sample(Map.of("patient_no", "我的天哪，怎么测试呢诶")), organoid(), score())) {
            XWPFDocument template = new XWPFDocument(new ByteArrayInputStream(DocTemplate.bytes(model.getDocKind())));
            XWPFDocument rendered = new XWPFDocument(new ByteArrayInputStream(renderer.render(model, new ArrayList<>())));
            assertEquals(rowHeights(template), rowHeights(rendered), model.getDocKind() + " 的行高被改了");
            int checked = 0;
            for (int t = 0; t < rendered.getTables().size(); t++) {
                List<XWPFTableRow> rows = rendered.getTables().get(t).getRows();
                for (int r = 0; r < rows.size(); r++) {
                    List<XWPFTableCell> cells = rows.get(r).getTableCells();
                    for (int c = 0; c < cells.size(); c++) {
                        List<XWPFParagraph> paragraphs = cells.get(c).getParagraphs();
                        for (int k = 0; k < paragraphs.size(); k++) {
                            XWPFParagraph p = paragraphs.get(k);
                            String before = template.getTables().get(t).getRows().get(r).getTableCells().get(c)
                                .getParagraphs().get(k).getText();
                            CTPPr ppr = p.getCTP().getPPr();
                            assertNotNull(ppr, "段落没有属性：" + before);
                            assertTrue(ppr.isSetSnapToGrid(), model.getDocKind() + "「" + before + "」仍对齐文档网格");
                            if (!before.contains("{{@")) {
                                // 放图的段落不许固定行距（会把图裁掉），其余一律固定行距
                                assertEquals(STLineSpacingRule.EXACT, ppr.getSpacing().getLineRule(),
                                    model.getDocKind() + "「" + before + "」不是固定行距");
                                assertEquals(DocCellFit.lineTwips(DocCellLayout.sizeOf(p)),
                                    DocCellLayout.twips(ppr.getSpacing().getLine()), "行距 = 字号 × 1.3：" + before);
                            } else {
                                assertFalse(ppr.getSpacing() != null
                                    && ppr.getSpacing().getLineRule() == STLineSpacingRule.EXACT, "放图的段落用了固定行距");
                            }
                            checked++;
                        }
                    }
                }
            }
            assertTrue(checked > 10);
        }
    }

    @Test
    @DisplayName("④ 字号：短的不缩；Kevin 截图里的两格缩一两档；超长的缩到 7.5 磅且全文都在（不截断）；没有残留占位符")
    void longTextShrinksWithoutTruncation() throws Exception {
        String overlong = "样本经剪切、酶消化等预处理，显微镜下观察组织漏出细胞量适中，细胞活性良好。".repeat(12);
        DocRenderModel model = sample(Map.of(
            "patient_no", "我的天哪，怎么测试呢诶",
            "sampling_site", "啊六点十分看见啊啥的",
            "pretreat_desc", overlong));
        XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(renderer.render(model, new ArrayList<>())));
        Map<String, Double> sizes = sizesByValue(doc);
        assertEquals(12.0, sizes.get("张某某"), "短的不缩");
        assertEquals(12.0, sizes.get("T-demo01"));
        assertEquals(10.5, sizes.get("我的天哪，怎么测试呢诶"), "三行缩成两行");
        assertEquals(8.0, sizes.get("啊六点十分看见啊啥的"), "取样部位那一格窄：缩到 8 磅两行");
        assertEquals(DocCellFit.MIN_PT, sizes.get(overlong), "超长：最小字号");
        String all = doc.getTables().get(0).getText();
        assertTrue(all.contains(overlong), "超长文字一个字不少");
        assertFalse(PLACEHOLDER.matcher(all).find(), "残留占位符");
    }

    // ══════════════════════════════════════════════════════════════════════
    // 模型与读取
    // ══════════════════════════════════════════════════════════════════════

    private static DocRenderModel sample(Map<String, String> overrides) {
        DocRenderModel model = new DocRenderModel(DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .text("patient_no", "P-2609017").text("source_unit_name", "示例医院").text("donor_name", "张某某")
            .text("sampling_site", "肝右叶").text("sampling_method", "手术切除").text("gender", "女")
            .text("clinical_diagnosis", "肝细胞癌").text("receive_desc", "样本按质控要求，保持2-8℃低温环境运输至实验室。")
            .text("receive_date", "2026-09-20").text("process_time", "2026-09-20 14:30").text("operator_name", "李某")
            .text("internal_no", "T-demo01").text("viability_file_name", "细胞活率测定报告.pdf")
            .text("orig_desc", "组织块约 1.2cm。").text("observe_desc", "样本外观呈暗红色。").text("pretreat_desc", "经剪切预处理。")
            .imageSlot("orig", List.of()).imageSlot("observe", List.of()).imageSlot("pretreat", List.of());
        overrides.forEach(model::text);
        return model;
    }

    private static DocRenderModel organoid() {
        return new DocRenderModel(DocKinds.ORGANOID_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .text("formed_time", "2026-08-04").text("growth_state", "很好").text("growth_desc", "拉客的风景")
            .text("planned_drug_screen", "索拉非尼").text("feedback_time", "2026-09-17")
            .imageSlot("organoid_observe", List.of());
    }

    private static DocRenderModel score() {
        return new DocRenderModel(DocKinds.ORGANOID_SCORE, DocAudiences.INTERNAL, DocTemplate.version())
            .text("sc_pre_culture", "20").text("sc_culture_days", "10").text("sc_count", "25")
            .text("sc_diameter", "30").text("sc_total", "85");
    }

    /** 渲染后「填了值的」格子：模板里那一格有文字占位符 → 同一位置的渲染结果。键 = 占位符名。 */
    private Map<String, Object[]> filledCells(DocRenderModel model) throws Exception {
        XWPFDocument template = new XWPFDocument(new ByteArrayInputStream(DocTemplate.bytes(model.getDocKind())));
        XWPFDocument rendered = new XWPFDocument(new ByteArrayInputStream(renderer.render(model, new ArrayList<>())));
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (int t = 0; t < template.getTables().size(); t++) {
            List<XWPFTableRow> rows = template.getTables().get(t).getRows();
            for (int r = 0; r < rows.size(); r++) {
                List<XWPFTableCell> cells = rows.get(r).getTableCells();
                for (int c = 0; c < cells.size(); c++) {
                    List<XWPFParagraph> paragraphs = cells.get(c).getParagraphs();
                    for (int k = 0; k < paragraphs.size(); k++) {
                        String text = paragraphs.get(k).getText();
                        if (text.matches("\\{\\{\\w+}}")) {
                            XWPFTableCell cell = rendered.getTables().get(t).getRows().get(r).getTableCells().get(c);
                            out.put(text, new Object[] {cell, cell.getParagraphs().get(k)});
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<String> rowHeights(XWPFDocument doc) {
        List<String> out = new ArrayList<>();
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                var trPr = row.getCtRow().getTrPr();
                if (trPr == null || trPr.sizeOfTrHeightArray() == 0) {
                    out.add("-");
                } else {
                    var h = trPr.getTrHeightArray(0);
                    out.add(h.getVal() + "/" + (h.isSetHRule() ? h.getHRule() : "atLeast"));
                }
            }
        }
        return out;
    }

    /** 文字 → 字号（每个填值段落取第一个 run 的字号）。 */
    private static Map<String, Double> sizesByValue(XWPFDocument doc) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        for (XWPFRun run : p.getRuns()) {
                            if (run.getFontSizeAsDouble() != null && !p.getText().isBlank()) {
                                out.putIfAbsent(p.getText(), run.getFontSizeAsDouble());
                                break;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
