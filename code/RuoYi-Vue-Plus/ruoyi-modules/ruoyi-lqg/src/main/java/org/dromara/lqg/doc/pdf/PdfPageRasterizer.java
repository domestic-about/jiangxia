package org.dromara.lqg.doc.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF → 每页 PNG（FLOW:F-DOC-01.step4 的决策值字面就是「PDFBox 按 150 DPI 把 PDF 每页渲染成 PNG」）。
 *
 * <p>★ 纯函数、无 Spring 无库：{@code PdfPageRasterizerTest} 自己拿 PDFBox 造一份两页 PDF 就能跑，
 * 断「页数一致 / 是 PNG / A4@150DPI 的尺寸 / 不是白图」。真正的存取与 page_no 落库在
 * {@link PageImageService}。
 *
 * <p>★ <b>150 DPI</b>：A4 出 1240×1754。accept 1 断 {@code w>=1000 and h>=1400} 就是这条
 * —— 96 DPI 会掉到 794×1123，直接红；再往上（300 DPI）是给打印用的，预览用不着，徒增 OSS 体积。
 *
 * @author DOC-PDF-001
 */
public final class PdfPageRasterizer {

    /** ticket §2 写死的 DPI。 */
    public static final int DEFAULT_DPI = 150;

    private PdfPageRasterizer() {
    }

    /**
     * @param pdf PDF 字节
     * @param dpi 渲染 DPI
     * @return 每页一张 PNG 的字节，**下标 0 = 第 1 页**
     */
    public static List<byte[]> toPng(byte[] pdf, int dpi) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            List<byte[]> images = new ArrayList<>(pageCount);
            for (int index = 0; index < pageCount; index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", out)) {
                    throw new IllegalStateException("JVM 里没有 PNG 编码器，出不了页面图片");
                }
                images.add(out.toByteArray());
            }
            return images;
        } catch (IOException e) {
            throw new IllegalStateException("PDF 解析失败，出不了页面图片：" + e, e);
        }
    }
}
