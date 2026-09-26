package org.dromara.common.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 上传体积的「先看请求头」闸（独立验收 V05）：multipart 请求的 Content-Length 已经超过
 * {@code spring.servlet.multipart.max-request-size}，就<b>不读请求体</b>、直接回
 * {@code {"code":413,"msg":"上传的文件太大…"}}。
 *
 * <p>★ 为什么不全交给异常处理：浏览器与小程序不发 {@code Expect: 100-continue}，超限时
 * Undertow 在解析时抛出、Spring 转成 {@code MaxUploadSizeExceededException}，全局异常处理能回中文提示；
 * 但带 {@code Expect: 100-continue} 的客户端（curl、多数 HTTP 库、脚本）会被 Undertow 在发 100 之前
 * 直接回一个<b>空响应的 417</b> —— 又回到了「只有状态码、没有提示」。这里抢在任何人读请求体之前按
 * 请求头判掉，两类客户端拿到的是同一句话。
 *
 * <p>没有 Content-Length（分块上传）的请求放行，交给 Undertow 边读边判 + 全局异常处理。
 * 业务码沿用若依惯例：HTTP 200，响应体里 {@code code=413}。
 *
 * @author F4（V05）
 */
@Slf4j
public class UploadSizeLimitFilter implements Filter {

    private final MultipartProperties multipartProperties;

    public UploadSizeLimitFilter(MultipartProperties multipartProperties) {
        this.multipartProperties = multipartProperties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request instanceof HttpServletRequest http && response instanceof HttpServletResponse httpResponse
            && tooLarge(http)) {
            log.warn("请求地址'{}',上传超限：Content-Length={} > {}", http.getRequestURI(), http.getContentLengthLong(),
                maxRequestBytes());
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            httpResponse.setHeader("Connection", "close");
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            httpResponse.getWriter().write("{\"code\":413,\"msg\":\""
                + GlobalExceptionHandler.uploadTooLargeMessage(multipartProperties) + "\",\"data\":null}");
            httpResponse.flushBuffer();
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * multipart 请求、带了 Content-Length、且超过一次请求的上限。
     */
    boolean tooLarge(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return false;
        }
        long max = maxRequestBytes();
        long length = request.getContentLengthLong();
        return max > 0 && length > max;
    }

    private long maxRequestBytes() {
        return GlobalExceptionHandler.maxRequestSize(multipartProperties).toBytes();
    }

}
