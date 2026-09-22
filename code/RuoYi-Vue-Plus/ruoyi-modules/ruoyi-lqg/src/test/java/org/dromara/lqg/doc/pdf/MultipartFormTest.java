package org.dromara.lqg.doc.pdf;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * multipart 请求体的形状（Gotenberg 的 {@code POST /forms/libreoffice/convert} 只认字段名
 * {@code files}；边界是固定串，可复现）。
 *
 * @author DOC-PDF-001
 */
class MultipartFormTest {

    @Test
    void buildsSingleFileFieldWithBoundaryAndCrlf() {
        byte[] body = MultipartForm.singleFile("files", "lqg-doc.docx", "application/x-test", "ABC".getBytes(StandardCharsets.UTF_8));
        String raw = new String(body, StandardCharsets.UTF_8);

        assertTrue(raw.startsWith("--" + MultipartForm.BOUNDARY + "\r\n"), "开头不是边界");
        assertTrue(raw.contains("Content-Disposition: form-data; name=\"files\"; filename=\"lqg-doc.docx\"\r\n"),
            "字段名 / 文件名不对：" + raw);
        assertTrue(raw.contains("Content-Type: application/x-test\r\n\r\n"), "字段头不对：" + raw);
        assertTrue(raw.endsWith("\r\n--" + MultipartForm.BOUNDARY + "--\r\n"), "结尾边界不对：" + raw);
        assertTrue(raw.contains("\r\n\r\nABC\r\n"), "正文没落在头与结尾边界之间：" + raw);
    }

    @Test
    void contentTypeCarriesTheBoundary() {
        assertTrue(MultipartForm.contentType().endsWith("boundary=" + MultipartForm.BOUNDARY));
    }

    @Test
    void outputFileNameSlugIsAsciiOnly() {
        // Gotenberg 的输出名头只收 ASCII：中文文档名会把请求打成 400
        assertTrue(MultipartForm.asciiSlug().matches("[\\x20-\\x7E]+"), "slug 不是 ASCII：" + MultipartForm.asciiSlug());
    }

    @Test
    void emptyContentStillProducesAWellFormedBody() {
        byte[] body = MultipartForm.singleFile("files", "x.docx", "application/x-test", new byte[0]);
        String raw = new String(body, StandardCharsets.UTF_8);
        assertTrue(raw.contains("\r\n\r\n\r\n--"), "空正文的 body 形状不对：" + raw);
        assertEquals(-1, raw.indexOf("null"), "不该出现 null");
    }
}
