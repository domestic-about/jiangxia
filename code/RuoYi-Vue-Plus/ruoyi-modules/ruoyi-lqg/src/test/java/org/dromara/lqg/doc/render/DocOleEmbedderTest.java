package org.dromara.lqg.doc.render;

import org.apache.poi.hpsf.ClassIDPredefined;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.dromara.lqg.doc.pdf.PdfFonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 细胞活率附件嵌进 Word（Kevin 本机验收「网页工作台」第 5 行）：OLE 包往返、真模板渲染、合并件、嵌不了时的退路、PDF 副本。
 *
 * <p>往返的判据与任务书一致：解压 docx → 取出 {@code word/embeddings/oleObjectN.bin} → 用 POI 的
 * {@code Ole10Native} 解析 → 得到的字节必须与原附件<b>逐字节相同</b>。
 *
 * @author H 批 H4 组
 */
class DocOleEmbedderTest {

    private static final long PDF_OSS = 900L;
    private static final long PNG_OSS = 901L;
    private static final long BIG_OSS = 902L;
    private static final long MISSING_OSS = 903L;

    /** 一份「PDF」附件（随机字节，含 0x00 与高位字节 —— 逐字节比对才有意义）。 */
    private static final byte[] PDF = pdfLike(180_000);

    private final Map<Long, byte[]> store = new HashMap<>();

    private final DocxRenderer renderer = new DocxRenderer(new DocOssBytes(null) {
        @Override
        public Fetched fetch(Long ossId) {
            if (ossId == BIG_OSS) {
                return new Fetched(new byte[(int) DocOleEmbedder.MAX_EMBED_BYTES + 1], null);
            }
            byte[] bytes = store.get(ossId);
            return bytes == null ? new Fetched(null, "存储里读不到这个文件") : new Fetched(bytes, null);
        }
    });

    DocOleEmbedderTest() {
        store.put(PDF_OSS, PDF);
        store.put(PNG_OSS, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 1, 2, 3});
    }

    @Test
    @DisplayName("OLE 包往返：Package 类 ID + \\1Ole10Native + \\1CompObj；POI 解析出的字节与原文件逐字节相同；中文文件名走 Unicode 字段")
    void packageRoundTrip() throws Exception {
        String name = "细胞活率测定报告（复测）.pdf";
        byte[] bin = DocOleEmbedder.packageObject(name, PDF);
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bin))) {
            assertEquals(ClassIDPredefined.OLE_V1_PACKAGE.getClassID(), fs.getRoot().getStorageClsid());
            assertTrue(fs.getRoot().hasEntryCaseInsensitive("\u0001Ole10Native"));
            assertTrue(fs.getRoot().hasEntryCaseInsensitive("\u0001CompObj"));
            Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(fs);
            assertArrayEquals(PDF, ole.getDataBuffer(), "取出的字节必须与原附件逐字节相同");
            assertEquals(name, ole.getLabel2(), "Unicode 显示名");
            assertEquals(name, ole.getFileName2(), "Unicode 源文件名");
            assertEquals(name, ole.getCommand2(), "Unicode 临时文件名（双击时按它落盘再打开）");
            assertEquals(3, ole.getUnknown1(), "3 = 嵌入的文件（不是链接）");
        }
        assertArrayEquals(PDF, DocOleEmbedder.extract(bin));
        // ANSI 字段按 GBK（中文 Windows 的系统代码页）
        byte[] gbk = name.getBytes(Charset.forName("GBK"));
        byte[] stream = DocOleEmbedder.ole10Native(name, PDF);
        assertEquals(stream.length - 4, readInt(stream, 0), "开头 4 字节 = 其后总长");
        byte[] label = new byte[gbk.length];
        System.arraycopy(stream, 6, label, 0, gbk.length);
        assertArrayEquals(gbk, label);
        // CompObj：Word 写的那种「OLE Package / Package」
        String comp = new String(DocOleEmbedder.COMP_OBJ, StandardCharsets.ISO_8859_1);
        assertTrue(comp.contains("OLE Package\0") && comp.contains("Package\0"));
        assertEquals((byte) 0xFE, DocOleEmbedder.COMP_OBJ[2]);
    }

    @Test
    @DisplayName("空文件、零字节、带路径的文件名也能包；名字里的路径与非法字符去掉")
    void packageEdgeCases() throws Exception {
        assertArrayEquals(new byte[0], DocOleEmbedder.extract(DocOleEmbedder.packageObject("空.txt", new byte[0])));
        assertEquals("a_b.pdf", DocOleEmbedder.safeName("C:\\Users\\x\\a:b.pdf"));
        assertEquals("附件", DocOleEmbedder.safeName("  "));
        assertEquals("pdf", DocOleEmbedder.iconKind("X.PDF"));
        assertEquals("image", DocOleEmbedder.iconKind("4 6.png"));
        assertEquals("word", DocOleEmbedder.iconKind("报告.docx"));
        assertEquals("excel", DocOleEmbedder.iconKind("计数.xlsx"));
        assertEquals("file", DocOleEmbedder.iconKind("数据.zip"));
        for (String kind : List.of("a.pdf", "a.png", "a.doc", "a.xls", "a.bin")) {
            byte[] icon = DocOleEmbedder.icon(kind);
            assertEquals((byte) 0x89, icon[0], kind + " 的图标是 PNG");
        }
    }

    @Test
    @DisplayName("真模板渲染：「细胞活率测定」一格是 OLE 对象（图标 + 文件名），部件 / 关系 / 类型齐全，取出与原附件逐字节相同，不留记号")
    void sampleQcEmbedsTheAttachment() throws Exception {
        byte[] docx = renderer.render(sampleModel(PDF_OSS, "细胞活率测定报告.pdf"), new ArrayList<>());
        Map<String, byte[]> parts = unzip(docx);
        String document = text(parts, "word/document.xml");
        String rels = text(parts, "word/_rels/document.xml.rels");
        String types = text(parts, "[Content_Types].xml");

        assertTrue(parts.containsKey("word/embeddings/oleObject1.bin"), parts.keySet().toString());
        assertTrue(parts.containsKey("word/media/lqgOleIcon1.png"));
        assertTrue(rels.contains("relationships/oleObject\" Target=\"embeddings/oleObject1.bin\""), rels);
        assertTrue(rels.contains("Target=\"media/lqgOleIcon1.png\""), rels);
        assertTrue(types.contains("Extension=\"bin\" ContentType=\"application/vnd.openxmlformats-officedocument.oleObject\""), types);
        assertTrue(document.contains("ProgID=\"Package\"") && document.contains("DrawAspect=\"Icon\"")
            && document.contains("Type=\"Embed\""), "OLE 对象按「显示为图标」嵌入");
        assertTrue(document.contains("<v:imagedata r:id=\"rIdLqgOleIcon1\""));
        assertTrue(document.contains("xmlns:v=\"urn:schemas-microsoft-com:vml\"") && document.contains("xmlns:o="));
        assertFalse(document.contains("LQG-OLE"), "记号必须全部换掉");
        assertTrue(document.contains("细胞活率测定报告.pdf"), "文件名照样印在图标旁边");
        // 对象就在「细胞活率测定」那一格：同一个单元格里先是对象、后是文件名
        Matcher cell = Pattern.compile("<w:tc>(?:(?!</w:tc>).)*?ProgID=\"Package\"(?:(?!</w:tc>).)*?</w:tc>", Pattern.DOTALL)
            .matcher(document);
        assertTrue(cell.find());
        assertTrue(cell.group().indexOf("<w:object") < cell.group().indexOf("细胞活率测定报告.pdf"));
        assertArrayEquals(PDF, DocOleEmbedder.extract(parts.get("word/embeddings/oleObject1.bin")), "逐字节相同");
        try (XWPFDocument opened = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertEquals(1, opened.getTables().size(), "POI 打得开（包结构合法）");
        }
    }

    @Test
    @DisplayName("合并件：三份拼好之后再嵌，只嵌一次，取出仍与原附件逐字节相同")
    void mergedEmbedsOnce() throws Exception {
        DocRenderModel organoid = new DocRenderModel(DocKinds.ORGANOID_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .text("growth_state", "良好").imageSlot("organoid_observe", List.of());
        DocRenderModel score = new DocRenderModel(DocKinds.ORGANOID_SCORE, DocAudiences.INTERNAL, DocTemplate.version())
            .text("sc_total", "85");
        byte[] merged = renderer.renderMerged(List.of(sampleModel(PNG_OSS, "4 6.png"), organoid, score), new ArrayList<>());
        Map<String, byte[]> parts = unzip(merged);
        long bins = parts.keySet().stream().filter(n -> n.startsWith("word/embeddings/")).count();
        assertEquals(1, bins);
        String document = text(parts, "word/document.xml");
        assertEquals(1, document.split("ProgID=\"Package\"", -1).length - 1);
        assertFalse(document.contains("LQG-OLE"));
        assertArrayEquals(store.get(PNG_OSS), DocOleEmbedder.extract(parts.get("word/embeddings/oleObject1.bin")));
        assertTrue(text(parts, "word/_rels/document.xml.rels").contains("embeddings/oleObject1.bin"));
    }

    @Test
    @DisplayName("取不到附件：不嵌、不留记号，只印文件名；超过 20MB：不嵌，文件名后注明去附件里看")
    void fallbacksNeverLeaveAMarker() throws Exception {
        String missing = text(unzip(renderer.render(sampleModel(MISSING_OSS, "活率.pdf"), new ArrayList<>())),
            "word/document.xml");
        assertFalse(missing.contains("<w:object") || missing.contains("LQG-OLE"));
        assertTrue(missing.contains("活率.pdf"));

        Map<String, byte[]> big = unzip(renderer.render(sampleModel(BIG_OSS, "大报告.pdf"), new ArrayList<>()));
        String bigXml = text(big, "word/document.xml");
        assertFalse(bigXml.contains("<w:object") || bigXml.contains("LQG-OLE"));
        assertTrue(bigXml.contains("大报告.pdf（大于 20MB，未嵌入，请在附件中查看）"), "文件名后注明没嵌入的原因");
        assertFalse(big.keySet().stream().anyMatch(n -> n.startsWith("word/embeddings/")));

        byte[] none = renderer.render(sampleModel(null, ""), new ArrayList<>());
        assertFalse(text(unzip(none), "word/document.xml").contains("<w:object"), "没有附件就没有对象");
    }

    @Test
    @DisplayName("转 PDF 的副本：对象换成同一张图标图片（w:pict），去掉嵌入的 bin 与关系；不是 docx / 没有对象原样返回")
    void conversionCopyDropsTheObject() throws Exception {
        byte[] docx = renderer.render(sampleModel(PDF_OSS, "细胞活率测定报告.pdf"), new ArrayList<>());
        byte[] copy = PdfFonts.forConversion(DocOleEmbedder.forConversion(docx));
        Map<String, byte[]> parts = unzip(copy);
        String document = text(parts, "word/document.xml");
        assertFalse(document.contains("<w:object") || document.contains("OLEObject"), "PDF 副本里没有 OLE 对象");
        assertTrue(document.contains("<w:pict>") && document.contains("<v:imagedata r:id=\"rIdLqgOleIcon1\""),
            "图标还在（PDF 里是图标 + 文件名）");
        assertTrue(document.contains("细胞活率测定报告.pdf"));
        assertFalse(parts.keySet().stream().anyMatch(n -> n.startsWith("word/embeddings/")), "附件字节不再传给转换服务");
        assertFalse(text(parts, "word/_rels/document.xml.rels").contains("oleObject"));
        assertTrue(parts.containsKey("word/media/lqgOleIcon1.png"));

        byte[] plain = renderer.render(sampleModel(null, ""), new ArrayList<>());
        assertSame(plain, DocOleEmbedder.forConversion(plain), "没有嵌入对象：原样返回");
        byte[] notDocx = "not a docx".getBytes(StandardCharsets.UTF_8);
        assertSame(notDocx, DocOleEmbedder.forConversion(notDocx));
        assertSame(notDocx, DocOleEmbedder.embed(notDocx, List.of()), "没有附件：原样返回（字节不变）");
    }

    // ══════════════════════════════════════════════════════════════════════
    // 小工具
    // ══════════════════════════════════════════════════════════════════════

    private static DocRenderModel sampleModel(Long viabilityOss, String fileName) {
        return new DocRenderModel(DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .text("donor_name", "张某某")
            .text("internal_no", "T-demo01")
            .text("viability_file_name", fileName)
            .embed("viability_file_name", viabilityOss, fileName)
            .imageSlot("orig", List.of())
            .imageSlot("observe", List.of())
            .imageSlot("pretreat", List.of());
    }

    private static byte[] pdfLike(int size) {
        byte[] bytes = new byte[size];
        new Random(5).nextBytes(bytes);
        byte[] head = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
    }

    static Map<String, byte[]> unzip(byte[] docx) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    private static String text(Map<String, byte[]> parts, String name) {
        return new String(parts.get(name), StandardCharsets.UTF_8);
    }
}
