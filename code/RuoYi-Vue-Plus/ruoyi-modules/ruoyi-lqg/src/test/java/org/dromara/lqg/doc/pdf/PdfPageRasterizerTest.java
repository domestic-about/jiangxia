package org.dromara.lqg.doc.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDFBox 出图的机器证据（FLOW:F-DOC-01.step4）：
 * **页数一致、每页都是真 PNG、A4@150DPI 的尺寸、图不是白板**。
 *
 * <p>accept 1 在真链路上断同样三件事（`pages` 长度 == `pdfinfo` 的页数、PNG ≥1000×1400、
 * 灰度极值不是纯白）；这里把它们钉在单元测试里，改坏 DPI 或漏掉一页会先在这里红。
 *
 * @author DOC-PDF-001
 */
class PdfPageRasterizerTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    /** 用 PDFBox 现造一份 N 页 A4 PDF，每页画一行黑字（不是白板）。 */
    private static byte[] pdfWithPages(int pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 36);
                    content.newLineAtOffset(80, 700);
                    content.showText("page " + (i + 1));
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void rendersOnePngPerPageInPdfOrder() throws Exception {
        List<byte[]> images = PdfPageRasterizer.toPng(pdfWithPages(3), PdfPageRasterizer.DEFAULT_DPI);
        assertEquals(3, images.size(), "页数必须与 PDF 的页数一致");
        for (byte[] png : images) {
            assertTrue(png.length > 1024, "一页 PNG 只有 " + png.length + " 字节，不像是真图");
            for (int i = 0; i < PNG_MAGIC.length; i++) {
                assertEquals(PNG_MAGIC[i], png[i], "第 " + (i + 1) + " 个字节不是 PNG 魔数");
            }
        }
    }

    @Test
    void a4At150DpiIsAtLeast1000x1400AndNotBlank() throws Exception {
        List<byte[]> images = PdfPageRasterizer.toPng(pdfWithPages(1), PdfPageRasterizer.DEFAULT_DPI);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(images.get(0)));
        // A4 @150DPI = 1240×1754；accept 1 断的是 >=1000 / >=1400
        assertTrue(image.getWidth() >= 1000, "宽 " + image.getWidth() + " 太小（DPI 是不是被改小了？）");
        assertTrue(image.getHeight() >= 1400, "高 " + image.getHeight() + " 太小（DPI 是不是被改小了？）");
        // 不是白板：至少要有相当深的像素（画了黑字）
        int min = 255;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int gray = image.getRGB(x, y) & 0xFF;
                if (gray < min) {
                    min = gray;
                }
            }
        }
        assertTrue(min < 80, "整页最深的像素是 " + min + "（>=80 说明图是白板）");
    }

    @Test
    void rejectsGarbageInsteadOfReturningEmptyList() {
        assertThrows(RuntimeException.class, () -> PdfPageRasterizer.toPng("not a pdf".getBytes(), 150));
    }
}
