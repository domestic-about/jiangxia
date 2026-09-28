package org.dromara.lqg.doc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.lqg.doc.pdf.PdfFonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 真模板 + 真 poi-tl（图片从替身取）：图片区排版、合并件分节、转 PDF 前的字体副本（G 批 C 组）。
 *
 * <ol>
 *   <li>图片位 1 / 2 / 3 张：进 Word 的显示尺寸等比、不超过这一格的框（{@link DocxRenderer#SLOT_BOX_PX}）；
 *       多张时带白边（相邻两张有缝）；</li>
 *   <li>合并件：每份自成一节、从新的一页开始，最后一节是最后一份自己的纸张（评分表原件是 Letter），
 *       不再有「第 1、2 份之间没断页」「最后多一张空白页」；</li>
 *   <li>下载的 Word 保留原件字体；转 PDF 的副本只剩容器字体（Noto Serif SC / Tinos）。</li>
 * </ol>
 *
 * @author G 批 C 组
 */
class DocxRendererLayoutTest {

    private static final long EMU_PER_PX = 9525L;
    private static final Pattern EXTENT = Pattern.compile("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"/>");

    /** ossId → 图片字节（1xx 横图 1600x1200，2xx 竖图 1200x1600）。 */
    private final Map<Long, byte[]> images = new HashMap<>();

    private final DocxRenderer renderer = new DocxRenderer(new DocOssBytes(null) {
        @Override
        public Fetched fetch(Long ossId) {
            byte[] bytes = images.computeIfAbsent(ossId, id -> jpeg(id >= 200 ? 1200 : 1600, id >= 200 ? 1600 : 1200));
            return new Fetched(bytes, null);
        }
    });

    @Test
    @DisplayName("① 样本质控表：1/2/3 张图等比、不超框；多张带白边（图片字节比原图大一圈）")
    void sampleSlotLayouts() throws Exception {
        int[] box = DocxRenderer.SLOT_BOX_PX.get("orig");
        for (List<Long> ossIds : List.of(List.of(101L), List.of(101L, 102L), List.of(101L, 102L, 103L),
            List.of(201L, 202L), List.of(101L, 201L, 102L))) {
            DocRenderModel model = sampleModel().imageSlot("orig", ossIds);
            byte[] docx = renderer.render(model, new ArrayList<>());
            List<long[]> extents = extents(xml(docx, "word/document.xml"));
            assertEquals(ossIds.size(), extents.size(), "每张图一个行内图：" + ossIds);
            for (long[] e : extents) {
                long w = e[0] / EMU_PER_PX;
                long h = e[1] / EMU_PER_PX;
                assertTrue(w <= box[0] && h <= box[1], "超框 " + w + "x" + h + "（" + ossIds + "）");
            }
            if (ossIds.size() > 1) {
                BufferedImage first = ImageIO.read(new ByteArrayInputStream(media(docx).get(0)));
                assertTrue(first.getWidth() > 1200, "多张时图片包了白边（原图宽 1200 / 1600）：" + first.getWidth());
                Color corner = new Color(first.getRGB(0, 0));
                assertTrue(corner.getRed() > 245 && corner.getGreen() > 245 && corner.getBlue() > 245, "白边是白的：" + corner);
            }
        }
    }

    @Test
    @DisplayName("② 类器官质控表：2 张横图上下摞，每张接近占满框宽（旧排法每张只有一半宽）")
    void organoidTwoLandscapesStack() throws Exception {
        int[] box = DocxRenderer.SLOT_BOX_PX.get("organoid_observe");
        DocRenderModel model = new DocRenderModel(DocKinds.ORGANOID_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .imageSlot("organoid_observe", List.of(101L, 102L));
        List<long[]> extents = extents(xml(renderer.render(model, new ArrayList<>()), "word/document.xml"));
        assertEquals(2, extents.size());
        assertTrue(extents.get(0)[0] / EMU_PER_PX > box[0] * 0.8, "上下摞、接近占满框宽：" + extents.get(0)[0] / EMU_PER_PX);
    }

    @Test
    @DisplayName("③ 合并件：每份自成一节从新页开始；最后一节是评分表自己的 Letter 纸；没有旧的「页尾分页符」")
    void mergedKeepsEachSection() throws Exception {
        byte[] sample = renderer.render(sampleModel(), new ArrayList<>());
        byte[] organoid = renderer.render(new DocRenderModel(DocKinds.ORGANOID_QC, DocAudiences.INTERNAL,
            DocTemplate.version()).text("growth_state", "良好"), new ArrayList<>());
        byte[] score = renderer.render(new DocRenderModel(DocKinds.ORGANOID_SCORE, DocAudiences.INTERNAL,
            DocTemplate.version()).text("sc_total", "85"), new ArrayList<>());
        String merged = xml(renderer.merge(List.of(sample, organoid, score)), "word/document.xml");

        String body = merged.substring(0, merged.lastIndexOf("<w:sectPr"));
        assertEquals(2, count(body, "<w:sectPr"), "前两份各以一个分节符结束（第三份用整篇的最后一节）");
        assertFalse(merged.contains("w:type=\"page\""), "不再用页尾分页符（旧做法第 1、2 份之间没断页、最后多一张空白页）");
        String lastSection = merged.substring(merged.lastIndexOf("<w:sectPr"));
        assertTrue(lastSection.contains("w:w=\"12240\""), "最后一节是评分表原件的 Letter 纸：" + lastSection);
        List<String> sections = sections(body);
        assertTrue(sections.get(0).contains("w:w=\"11906\"") && sections.get(1).contains("w:w=\"11906\""),
            "样本质控表 / 类器官质控表保持 A4");
        int sampleTitle = merged.indexOf("样本质控表");
        int organoidTitle = merged.indexOf("类器官质控表");
        int scoreTitle = merged.indexOf("类器官质量评分表");
        assertTrue(sampleTitle < body.indexOf("<w:sectPr") && body.indexOf("<w:sectPr") < organoidTitle,
            "样本质控表那一节在类器官质控表之前结束");
        assertTrue(organoidTitle < scoreTitle, "拼接顺序 = 样本质控表 → 类器官质控表 → 评分表");
    }

    @Test
    @DisplayName("④ 下载的 Word 保留原件字体（宋体 / Times New Roman）；转 PDF 的副本只剩 Noto Serif SC / Tinos")
    void conversionCopyUsesContainerFonts() throws Exception {
        byte[] docx = renderer.render(sampleModel(), new ArrayList<>());
        String word = xml(docx, "word/document.xml");
        assertTrue(word.contains("w:eastAsia=\"宋体\"") && word.contains("Times New Roman"), "下载的 Word 是原件字体");
        assertFalse(word.contains("Noto Serif SC") || word.contains("Tinos"));

        byte[] copy = PdfFonts.forConversion(docx);
        for (String part : List.of("word/document.xml", "word/styles.xml")) {
            String x = xml(copy, part);
            assertFalse(x.contains("\"宋体\""), part + " 里还有宋体（容器里没有，LibreOffice 会回退成黑体一类）");
            assertFalse(x.contains("Times New Roman"), part + " 里还有 Times New Roman（LibreOffice 会回退到 Liberation）");
            assertFalse(x.contains("asciiTheme") || x.contains("eastAsiaTheme"), part + " 里还有主题字体（会回退）");
        }
        // 主题：拉丁 / 东亚 / 简体中文三格指向容器字体（阿拉伯文、希伯来文那几个 script 的字体用不上，不动）
        String theme = xml(copy, "word/theme/theme1.xml");
        assertFalse(Pattern.compile("<a:(latin|ea|cs) typeface=\"(?!Tinos|Noto Serif SC)").matcher(theme).find(), theme);
        assertTrue(theme.contains("<a:font script=\"Hans\" typeface=\"Noto Serif SC\"/>"));
        assertTrue(xml(copy, "word/document.xml").contains("w:eastAsia=\"Noto Serif SC\""));
        byte[] notDocx = "not a docx".getBytes(StandardCharsets.UTF_8);
        assertSame(notDocx, PdfFonts.forConversion(notDocx), "不是 docx 就原样交给转换服务");
    }

    @Test
    @DisplayName("⑤ 评分表原件里写宋体的分值格：转换副本里数字也用宋体一类（Noto Serif SC），不是 Tinos")
    void cjkNamedLatinSlotsMapToCjkFont() {
        String tag = "<w:rFonts w:hint=\"eastAsia\" w:ascii=\"宋体\" w:hAnsi=\"宋体\" w:eastAsia=\"宋体\"/>";
        String out = PdfFonts.rewriteRunFonts(tag);
        assertTrue(out.contains("w:ascii=\"Noto Serif SC\"") && out.contains("w:hint=\"eastAsia\""), out);
        String latin = PdfFonts.rewriteRunFonts("<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:eastAsia=\"宋体\"/>");
        assertTrue(latin.contains("w:ascii=\"Tinos\"") && latin.contains("w:eastAsia=\"Noto Serif SC\""), latin);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 小工具
    // ══════════════════════════════════════════════════════════════════════

    private static DocRenderModel sampleModel() {
        return new DocRenderModel(DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .text("donor_name", "张某某")
            .text("internal_no", "T-demo01")
            .imageSlot("orig", List.of())
            .imageSlot("observe", List.of())
            .imageSlot("pretreat", List.of());
    }

    private static byte[] jpeg(int w, int h) {
        try {
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(120, 140, 120));
            g.fillRect(0, 0, w, h);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String xml(byte[] docx, String part) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(part)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    private static List<byte[]> media(byte[] docx) throws Exception {
        List<byte[]> out = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/media/")) {
                    out.add(zip.readAllBytes());
                }
            }
        }
        return out;
    }

    private static List<long[]> extents(String xml) {
        List<long[]> out = new ArrayList<>();
        Matcher m = EXTENT.matcher(xml);
        while (m.find()) {
            out.add(new long[] {Long.parseLong(m.group(1)), Long.parseLong(m.group(2))});
        }
        return out;
    }

    private static List<String> sections(String body) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<w:sectPr.*?</w:sectPr>", Pattern.DOTALL).matcher(body);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
