package org.dromara.lqg.doc.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Gotenberg 要的 {@code multipart/form-data} 请求体（{@code POST /forms/libreoffice/convert}
 * 的字段名是 {@code files}）。
 *
 * <p>★ 手搓而不是引一个 HTTP 客户端库：本模块只需要「一个文件字段」这一种请求，
 * 而 multipart 的边界/CRLF 规则是**可单测**的纯函数（{@code MultipartFormTest} 断的就是它）——
 * 引 OkHttp / RestTemplate 反而多一层要配超时与代理的东西（见 {@link PdfConvertService}）。
 *
 * @author DOC-PDF-001
 */
public final class MultipartForm {

    /** 固定边界：每次请求内容不同也无所谓（Gotenberg 只按字段名取），可复现便于单测。 */
    public static final String BOUNDARY = "----lqgDocRenderBoundary7MA4YWxkTrZu0gW";

    private MultipartForm() {
    }

    public static String contentType() {
        return "multipart/form-data; boundary=" + BOUNDARY;
    }

    /** 一个文件字段：{@code name="files"; filename="x.docx"} + 二进制体。 */
    public static byte[] singleFile(String field, String fileName, String contentType, byte[] content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 512);
            out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream 不会抛 IOException，只有签名逼着 catch
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 传给 Gotenberg 的 {@code Gotenberg-Output-Filename} 头只许 ASCII：
     * 中文文档名（样本质控表-T-hga03）会把请求打成 400，所以这里换成固定 slug
     * —— 我们只取响应体里的 PDF 字节，文件名叫什么由下载接口自己拼。
     */
    public static String asciiSlug() {
        return "lqg-doc";
    }
}
