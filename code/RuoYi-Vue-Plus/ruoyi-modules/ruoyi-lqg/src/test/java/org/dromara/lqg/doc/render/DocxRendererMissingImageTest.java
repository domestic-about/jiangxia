package org.dromara.lqg.doc.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 取图失败不再静默（#217，独立验收 V23）：真模板 + 真 poi-tl 渲染，取不到的那张
 * <b>逐张记进缺图清单</b>，取到的照常嵌进去，图位不残留占位符。
 *
 * @author 独立验收 V23 修复
 */
class DocxRendererMissingImageTest {

    private static final long OK_OSS = 1L;
    private static final long MISSING_OSS = 2L;

    /** 取图替身：1 号给一张真 PNG，2 号「存储里读不到」。 */
    private static final class FakeBytes extends DocOssBytes {
        FakeBytes() {
            super(null);
        }

        @Override
        public Fetched fetch(Long ossId) {
            if (ossId == OK_OSS) {
                return new Fetched(png(), null);
            }
            return new Fetched(null, "存储里读不到这个文件");
        }
    }

    private static byte[] png() {
        try {
            BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("样本质控表：orig 位 2 张里 1 张取不到 → 缺图清单恰好记下那一张，另一张照常进 Word")
    void recordsExactlyTheMissingOnes() throws Exception {
        DocRenderModel model = new DocRenderModel(DocKinds.SAMPLE_QC, DocAudiences.INTERNAL, DocTemplate.version())
            .text("donor_name", "测试供体")
            .imageSlot("orig", List.of(OK_OSS, MISSING_OSS))
            .imageSlot("observe", List.of())
            .imageSlot("pretreat", List.of());
        List<MissingImage> missing = new ArrayList<>();
        byte[] docx = new DocxRenderer(new FakeBytes()).render(model, missing);

        assertEquals(1, missing.size(), "只缺一张：" + missing);
        MissingImage m = missing.get(0);
        assertEquals("orig", m.slot());
        assertEquals(2, m.index());
        assertEquals(MISSING_OSS, m.ossId());
        assertEquals(DocKinds.SAMPLE_QC, m.docKind());
        assertEquals("样本质控表·收样原始情况 第 2 张（存储里读不到这个文件）", m.label());

        int media = 0;
        String documentXml = "";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/media/")) {
                    media++;
                } else if (entry.getName().equals("word/document.xml")) {
                    documentXml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        assertEquals(1, media, "取到的那一张照常嵌进 Word");
        assertFalse(documentXml.contains("{{@orig_img"), "图位占位符不残留");
        assertTrue(documentXml.contains("测试供体"));
    }
}
