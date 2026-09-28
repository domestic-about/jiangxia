package org.dromara.common.web.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 上传体积闸（V05）：超限的 multipart 请求按 Content-Length 直接回中文提示，不读请求体、不进后续过滤器。
 */
class UploadSizeLimitFilterTest {

    private static final long MB = 1024L * 1024;

    private static MockHttpServletRequest upload(String contentType, long contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/resource/oss/upload") {
            @Override
            public long getContentLengthLong() {
                return contentLength;
            }

            @Override
            public int getContentLength() {
                return (int) Math.min(Integer.MAX_VALUE, contentLength);
            }
        };
        request.setContentType(contentType);
        return request;
    }

    @Test
    @DisplayName("60MB + 表单开销的 multipart：不往下走，回 {code:413, msg:中文提示}")
    void rejectsOversizeMultipart() throws Exception {
        UploadSizeLimitFilter filter = new UploadSizeLimitFilter(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(upload("multipart/form-data; boundary=x", 60 * MB + 250), response, chain);

        assertNull(chain.getRequest(), "超限的请求不许再往下传（后面的过滤器会去读请求体）");
        assertEquals(200, response.getStatus(), "若依惯例：HTTP 200，业务码在响应体里");
        assertEquals("close", response.getHeader("Connection"));
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(413, body.get("code").asInt());
        assertEquals("上传的文件太大：单个文件不能超过 50MB，一次上传合计不能超过 60MB", body.get("msg").asText());
    }

    @Test
    @DisplayName("不超限 / 不是 multipart / 没有 Content-Length（分块）：原样放行")
    void passesEverythingElse() throws Exception {
        UploadSizeLimitFilter filter = new UploadSizeLimitFilter(null);
        for (MockHttpServletRequest request : new MockHttpServletRequest[]{
            upload("multipart/form-data; boundary=x", 45 * MB),
            upload("multipart/form-data; boundary=x", 60 * MB),
            upload("application/json", 80 * MB),
            upload("multipart/form-data; boundary=x", -1)}) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest(), "不该拦：" + request.getContentType() + " " + request.getContentLengthLong());
        }
    }

    @Test
    @DisplayName("上限跟随 spring.servlet.multipart.max-request-size")
    void followsConfiguredLimit() throws Exception {
        MultipartProperties properties = new MultipartProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(5));
        properties.setMaxRequestSize(DataSize.ofMegabytes(10));
        UploadSizeLimitFilter filter = new UploadSizeLimitFilter(properties);

        MockFilterChain passed = new MockFilterChain();
        filter.doFilter(upload("multipart/form-data", 10 * MB), new MockHttpServletResponse(), passed);
        assertNotNull(passed.getRequest());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain blocked = new MockFilterChain();
        filter.doFilter(upload("MULTIPART/FORM-DATA", 10 * MB + 1), response, blocked);
        assertNull(blocked.getRequest());
        assertEquals("上传的文件太大：单个文件不能超过 5MB，一次上传合计不能超过 10MB",
            new ObjectMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("msg").asText());
    }

}
