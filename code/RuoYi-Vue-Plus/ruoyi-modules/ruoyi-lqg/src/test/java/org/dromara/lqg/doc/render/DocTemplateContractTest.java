package org.dromara.lqg.doc.render;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三份 docx 模板的资源契约（ticket §0 口径复述 1 + §2）。
 *
 * <p>这份测试盯的是**模板文件本身**（不是渲染结果）：占位符齐不齐、两句印死的注是否逐字、
 * 甲方原件里的示例文字有没有清干净。渲染结果由 accept 1 的 {@code docx_check.py} 断。
 *
 * <p>★ 用 POI 打开模板顺带证明一件事：**生成出来的 docx 是 POI 打得开的合法文件**
 * （模板是拿原件做 XML 手术改出来的，见
 * {@code doc/waves/reports/DOC-RENDER-001/make-doc-templates.py}）。
 *
 * <p>★ 比较前去掉全部空白，与 {@code doc/verify/docx_check.py} 同一判据 ——
 * 两句注在原件里被拆在多个 run 上（{@code ＜1x10} + {@code 4} + {@code 。}），
 * 只有拼起来再比才有意义。
 *
 * @author DOC-RENDER-001
 */
class DocTemplateContractTest {

    /** 样本质控表的注（REQ-QC-004，一个字都不许动）。 */
    private static final String SAMPLE_QC_NOTE =
        "注：合格，活率≥70%；基本合格，50%~70%；不合格，＜50%或活细胞＜1x104。";

    /** 类器官质量评分表的注（REQ-QC-008）。 */
    private static final String SCORE_NOTE =
        "注：类器官质量评分≤50表示类器官质量偏差，药敏实验失败风险较大；"
            + "50~75表示类器官质量中等；≥75表示类器官质量良好。";

    private static String flatten(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String textOf(String docKind) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(DocTemplate.bytes(docKind)));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return flatten(extractor.getText());
        }
    }

    @Test
    @DisplayName("模板版本号文件存在且非空")
    void versionExists() {
        assertTrue(DocTemplate.version().matches("\\d+"), "模板版本号应该是数字，当前：" + DocTemplate.version());
    }

    @Test
    @DisplayName("三个种类都有模板，且模板里没有残留的甲方示例文字")
    void templatesExistAndAreClean() throws Exception {
        for (String kind : DocKinds.TEMPLATED) {
            assertTrue(DocTemplate.bytes(kind).length > 0, kind + " 模板为空");
            String text = textOf(kind);
            assertFalse(text.contains("要求图片可以放大"),
                kind + " 模板里还留着「要求图片可以放大」（应换成图片占位符）");
        }
    }

    @Test
    @DisplayName("样本质控表：所有占位符都在，两句注逐字保留")
    void sampleQcTemplate() throws Exception {
        String text = textOf(DocKinds.SAMPLE_QC);
        for (String tag : List.of("patient_no", "source_unit_name", "donor_name", "sampling_site",
            "sampling_method", "gender", "species", "clinical_diagnosis", "receive_desc", "receive_date",
            "process_time", "operator_name", "internal_no", "viability_file_name",
            "orig_desc", "observe_desc", "pretreat_desc",
            "@orig_img1", "@orig_img2", "@orig_img3",
            "@observe_img1", "@observe_img2", "@observe_img3",
            "@pretreat_img1", "@pretreat_img2", "@pretreat_img3")) {
            assertTrue(text.contains("{{" + tag + "}}"), "样本质控表模板缺占位符 {{" + tag + "}}");
        }
        assertTrue(text.contains(flatten(SAMPLE_QC_NOTE)), "样本质控表的注不是逐字原文");
    }

    @Test
    @DisplayName("类器官质控表：五个字段 + 一个图片位，占位符都在")
    void organoidQcTemplate() throws Exception {
        String text = textOf(DocKinds.ORGANOID_QC);
        for (String tag : List.of("formed_time", "growth_state", "growth_desc",
            "planned_drug_screen", "feedback_time",
            "@organoid_observe_img1", "@organoid_observe_img2", "@organoid_observe_img3")) {
            assertTrue(text.contains("{{" + tag + "}}"), "类器官质控表模板缺占位符 {{" + tag + "}}");
        }
    }

    @Test
    @DisplayName("评分表：四个纵合并的分值格 + 合计行，注逐字保留")
    void scoreTemplate() throws Exception {
        String text = textOf(DocKinds.ORGANOID_SCORE);
        for (String tag : List.of("sc_pre_culture", "sc_culture_days", "sc_count", "sc_diameter", "sc_total")) {
            assertTrue(text.contains("{{" + tag + "}}"), "评分表模板缺占位符 {{" + tag + "}}");
        }
        assertTrue(text.contains("合计"), "评分表模板表尾缺合计行");
        assertTrue(text.contains(flatten(SCORE_NOTE)), "评分表的注不是逐字原文");
        // 原件的 12 个档位文字必须原样在（选中档是靠分值格区分，不是把没选的档删掉）
        for (String option : List.of("<40", "40~80", ">80", ">14d", "≤14d",
            "<100", "100~1500", "1500~4000", ">4000", "<30μm", "30~100μm", ">100μm")) {
            assertTrue(text.contains(option), "评分表模板缺档位文字 " + option);
        }
    }

    @Test
    @DisplayName("模板里没有甲方批注留下的高亮 / 彩色底纹（独立验收 V11：成品里曾带亮绿底），版本号已加一")
    void templatesCarryNoAnnotationHighlight() throws Exception {
        for (String kind : DocKinds.TEMPLATED) {
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(DocTemplate.bytes(kind)))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.getName().endsWith(".xml")) {
                        continue;
                    }
                    String xml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertFalse(xml.contains("<w:highlight"),
                        kind + " 的 " + entry.getName() + " 里还有 w:highlight（甲方批注的绿色高亮会印进成品）");
                    java.util.regex.Matcher shd = Pattern.compile("<w:shd [^>]*w:fill=\"([0-9A-Fa-f]{6})\"").matcher(xml);
                    while (shd.find()) {
                        assertEquals("FFFFFF", shd.group(1).toUpperCase(),
                            kind + " 的 " + entry.getName() + " 里有彩色底纹 " + shd.group(1));
                    }
                }
            }
        }
        // 去高亮 = 换模板文件 → 版本号加一（指纹随之失效，下次渲染用新模板；V11 之前是 2）
        assertTrue(Integer.parseInt(DocTemplate.version()) >= 3, "模板改过之后版本号要加一，当前：" + DocTemplate.version());
    }

    @Test
    @DisplayName("G 批 C 组：模板保留甲方原件的字体（宋体 / Times New Roman），不再写死容器字体；版本号 ≥ 4")
    void templatesKeepTheClientsFonts() throws Exception {
        for (String kind : DocKinds.TEMPLATED) {
            for (String part : List.of("word/document.xml", "word/styles.xml", "word/theme/theme1.xml")) {
                String xml = part(kind, part);
                assertFalse(xml.contains("Noto Serif SC") || xml.contains("Tinos"),
                    kind + " 的 " + part + " 里还写着容器字体（下载的 Word 在甲方电脑上会被别的字体顶替）");
            }
            String doc = part(kind, "word/document.xml");
            assertTrue(doc.contains("w:eastAsia=\"宋体\""), kind + " 的中文字体应是原件的宋体");
            assertTrue(doc.contains("Times New Roman"), kind + " 的拉丁字体应是原件的 Times New Roman");
        }
        assertTrue(Integer.parseInt(DocTemplate.version()) >= 4, "换回原件字体 = 换模板文件，版本号要加一：" + DocTemplate.version());
    }

    @Test
    @DisplayName("G 批 C 组：评分表末尾不留空段落（加了合计行后它会被挤到第二页，单独导出多一张空白页）")
    void scoreTemplateHasNoTrailingEmptyParagraph() throws Exception {
        String xml = part(DocKinds.ORGANOID_SCORE, "word/document.xml");
        String body = xml.substring(0, xml.lastIndexOf("<w:sectPr"));
        String last = body.substring(body.lastIndexOf("<w:p "));
        assertTrue(last.contains("<w:t"), "评分表正文最后一段应是那句「注」，不是空段落：" + last.substring(0, Math.min(200, last.length())));
    }

    @Test
    @DisplayName("G 批 C 组：样本质控表的三个图片行不跨页断开（放了图的那一行整行挪页，不会一张图在上一页、一张在下一页）")
    void sampleImageRowsDoNotSplitAcrossPages() throws Exception {
        String xml = part(DocKinds.SAMPLE_QC, "word/document.xml");
        for (String slot : List.of("orig", "observe", "pretreat")) {
            int tag = xml.indexOf("{{@" + slot + "_img1}}");
            String rowHead = xml.substring(xml.lastIndexOf("<w:tr ", tag), tag);
            assertTrue(rowHead.contains("<w:cantSplit/>"), slot + " 所在行没有 cantSplit");
        }
    }

    @Test
    @DisplayName("图片位「能放图的框」放得进格子：宽 ≤ 格宽扣左右单元格边距，高 ≤ 模板这一行的最小行高 —— 图排完不撑破格子")
    void imageBoxFitsInsideTheCell() throws Exception {
        assertFits(DocKinds.SAMPLE_QC, "orig");
        assertFits(DocKinds.SAMPLE_QC, "observe");
        assertFits(DocKinds.SAMPLE_QC, "pretreat");
        assertFits(DocKinds.ORGANOID_QC, "organoid_observe");
    }

    private static String part(String kind, String name) throws Exception {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
            new ByteArrayInputStream(DocTemplate.bytes(kind)))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    /** 从模板量出「占位符所在格子」的可用宽度与最小行高（twips），与渲染器用的框（px ×15）比。 */
    private static void assertFits(String kind, String slot) throws Exception {
        String xml = part(kind, "word/document.xml");
        java.util.List<Integer> grid = new java.util.ArrayList<>();
        java.util.regex.Matcher col = Pattern.compile("<w:gridCol w:w=\"(\\d+)\"").matcher(
            xml.substring(xml.indexOf("<w:tblGrid>"), xml.indexOf("</w:tblGrid>")));
        while (col.find()) {
            grid.add(Integer.parseInt(col.group(1)));
        }
        java.util.regex.Matcher mar = Pattern.compile(
            "<w:tblCellMar>.*?<w:left w:w=\"(\\d+)\".*?<w:right w:w=\"(\\d+)\"", Pattern.DOTALL).matcher(xml);
        assertTrue(mar.find(), kind + " 模板里找不到 tblCellMar");
        int margins = Integer.parseInt(mar.group(1)) + Integer.parseInt(mar.group(2));
        int tag = xml.indexOf("{{@" + slot + "_img1}}");
        assertTrue(tag > 0, kind + " 模板里没有 " + slot + " 的图片占位符");
        String row = xml.substring(xml.lastIndexOf("<w:tr", tag), xml.indexOf("</w:tr>", tag));
        int start = 0;
        int span = 1;
        java.util.regex.Matcher tc = Pattern.compile("<w:tc>(.*?)</w:tc>", Pattern.DOTALL).matcher(row);
        while (tc.find()) {
            java.util.regex.Matcher gs = Pattern.compile("<w:gridSpan w:val=\"(\\d+)\"").matcher(tc.group(1));
            int s = gs.find() ? Integer.parseInt(gs.group(1)) : 1;
            if (tc.group(1).contains("{{@" + slot + "_img1}}")) {
                span = s;
                break;
            }
            start += s;
        }
        int cellTwips = 0;
        for (int i = start; i < start + span; i++) {
            cellTwips += grid.get(i);
        }
        int usable = cellTwips - margins;
        int[] box = DocxRenderer.SLOT_BOX_PX.get(slot);
        int renderTwips = box[0] * 15;
        assertTrue(renderTwips <= usable, kind + "/" + slot + "：框宽 " + renderTwips + " twips 超过格子可用宽度 "
            + usable + " twips（格宽 " + cellTwips + " − 边距 " + margins + "）");
        java.util.regex.Matcher h = Pattern.compile("<w:trHeight w:val=\"(\\d+)\"").matcher(row);
        assertTrue(h.find(), kind + "/" + slot + " 所在行没有最小行高");
        int rowTwips = Integer.parseInt(h.group(1));
        assertTrue(box[1] * 15 <= rowTwips, kind + "/" + slot + "：框高 " + box[1] * 15 + " twips 超过模板行高 " + rowTwips
            + " twips（图会把这一行撑高、表格被推到下一页）");
    }

    @Test
    @DisplayName("v6（CR-20261009-18）：样本质控表「性别」行后是一行「种属 | {{species}}」，格式照抄「收样描述」行；列宽不动；版本号 ≥ 6")
    void sampleQcHasTheSpeciesRow() throws Exception {
        String xml = part(DocKinds.SAMPLE_QC, "word/document.xml");
        java.util.List<String> rows = new java.util.ArrayList<>();
        java.util.regex.Matcher tr = Pattern.compile("<w:tr\\b.*?</w:tr>", Pattern.DOTALL).matcher(xml);
        while (tr.find()) {
            rows.add(tr.group());
        }
        int gender = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).contains(">性别<")) {
                gender = i;
            }
        }
        assertTrue(gender >= 0, "找不到「性别」那一行");
        String species = rows.get(gender + 1);
        String receive = rows.get(gender + 2);
        assertTrue(species.contains(">种属<") && species.contains("{{species}}"), "「性别」行的下一行应是「种属」");
        assertTrue(receive.contains("{{receive_desc}}"), "「种属」行后面仍是「收样描述」");
        // 结构与「收样描述」行一致：去掉文字与段落 id 后逐字相同（左格标签 + 右格跨 5 列、同一套边框与行高）
        java.util.function.Function<String, String> shape = row -> row
            .replaceAll("w14:paraId=\"[0-9A-F]+\"", "")
            .replace(">种属<", "><").replace(">收样描述<", "><")
            .replace("{{species}}", "").replace("{{receive_desc}}", "");
        assertEquals(shape.apply(receive), shape.apply(species), "「种属」行的格式应照抄「收样描述」行");
        assertTrue(xml.contains("<w:tblGrid><w:gridCol w:w=\"1835\"/><w:gridCol w:w=\"1626\"/><w:gridCol w:w=\"1400\"/>"
            + "<w:gridCol w:w=\"1122\"/><w:gridCol w:w=\"846\"/><w:gridCol w:w=\"1444\"/></w:tblGrid>"), "列宽不动");
        assertTrue(Integer.parseInt(DocTemplate.version()) >= 6, "加了一行 = 换模板文件，版本号要加一：" + DocTemplate.version());
    }

    @Test
    @DisplayName("模板里的占位符语法是 poi-tl 的 {{…}}（docx_check 的 --no-placeholder 也认这个）")
    void placeholderSyntax() {
        assertTrue(Pattern.compile("\\{\\{[^}]+}}").matcher("{{patient_no}}").find());
    }
}
