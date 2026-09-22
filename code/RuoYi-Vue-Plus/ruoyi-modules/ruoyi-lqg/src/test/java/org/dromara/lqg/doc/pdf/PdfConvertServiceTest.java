package org.dromara.lqg.doc.pdf;

import com.sun.net.httpserver.HttpServer;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 转换失败必须**可见**（ticket accept 2：转换服务不可用时 status=failed 且 error_msg 非空）。
 *
 * <p>这里钉住三件最容易做错的事：
 * <ol>
 *   <li>连不上 → 抛一句能读的「转换服务不可用（…）」，**不吞异常、不返回空字节**；</li>
 *   <li>HTTP 非 2xx → 抛出状态码与响应体片段；</li>
 *   <li>返回的不是 PDF（代理页/错误页）→ 判失败，不能把一段 HTML 当 PDF 发出去。</li>
 * </ol>
 *
 * <p>用的都是 JDK 自带的 {@code com.sun.net.httpserver}（测试专用，不引新依赖）与
 * 「1 号端口没人听」这种毫秒级失败，不依赖网络也不依赖 Gotenberg 起着。
 *
 * @author DOC-PDF-001
 */
class PdfConvertServiceTest {

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    @Test
    void unreachableServiceGivesReadableReason() {
        PdfConvertService service = new PdfConvertService("http://127.0.0.1:1", 5);
        try {
            ServiceException e = assertThrows(ServiceException.class,
                () -> service.toPdf("docx".getBytes(StandardCharsets.UTF_8), "x.docx"));
            assertTrue(e.getMessage().contains("转换服务不可用"), "实际信息：" + e.getMessage());
            assertTrue(e.getMessage().contains("/forms/libreoffice/convert"), "实际信息：" + e.getMessage());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void blankUrlFailsLoudly() {
        PdfConvertService service = new PdfConvertService("  ", 5);
        try {
            ServiceException e = assertThrows(ServiceException.class,
                () -> service.toPdf("docx".getBytes(StandardCharsets.UTF_8), "x.docx"));
            assertTrue(e.getMessage().contains("没配置"), "实际信息：" + e.getMessage());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void emptyWordNeverReachesTheService() {
        PdfConvertService service = new PdfConvertService("http://127.0.0.1:1", 5);
        try {
            assertThrows(ServiceException.class, () -> service.toPdf(new byte[0], "x.docx"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void errorStatusIsReportedWithBodySnippet() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forms/libreoffice/convert", exchange -> {
            byte[] payload = "libreoffice is not available".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        PdfConvertService service = new PdfConvertService("http://127.0.0.1:" + port, 5);
        try {
            ServiceException e = assertThrows(ServiceException.class,
                () -> service.toPdf("docx".getBytes(StandardCharsets.UTF_8), "x.docx"));
            assertTrue(e.getMessage().contains("HTTP 503"), "实际信息：" + e.getMessage());
            assertTrue(e.getMessage().contains("libreoffice is not available"), "实际信息：" + e.getMessage());
        } finally {
            service.shutdown();
            server.stop(0);
        }
    }

    @Test
    void nonPdfResponseIsRejectedAndRequestBodyIsMultipart() throws Exception {
        AtomicReference<byte[]> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forms/libreoffice/convert", exchange -> {
            body.set(readAll(exchange.getRequestBody()));
            byte[] payload = "<html>proxy error</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        PdfConvertService service = new PdfConvertService("http://127.0.0.1:" + port, 5);
        try {
            ServiceException e = assertThrows(ServiceException.class,
                () -> service.toPdf("docx-bytes".getBytes(StandardCharsets.UTF_8), "x.docx"));
            assertTrue(e.getMessage().contains("不是 PDF"), "实际信息：" + e.getMessage());

            String raw = new String(body.get(), StandardCharsets.UTF_8);
            assertTrue(raw.contains("Content-Disposition: form-data; name=\"files\""), "请求体不是 files 字段：" + raw);
            assertTrue(raw.contains(MultipartForm.BOUNDARY), "请求体里没有边界");
            assertTrue(raw.contains("docx-bytes"), "Word 字节没进请求体");
        } finally {
            service.shutdown();
            server.stop(0);
        }
    }
}
