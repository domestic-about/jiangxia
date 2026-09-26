package org.dromara.common.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.ServletException;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.exception.OssException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理的回显口径（独立验收 V03 / V05 / V19）。纯单测：直接调处理方法，不起容器。
 *
 * <p>判据两半：该给用户看的提示<b>原样</b>（业务异常、校验、上传超限、请求形状错误）；
 * 其余<b>一个字的原文都不回</b>，只回通用提示 + 错误编号。
 */
class GlobalExceptionHandlerTest {

    /**
     * V03 活体复现时响应体里吐出来的那一类原文（节选改写，保留全部敏感形状）。
     */
    static final String LEAKY_MESSAGE = """

        ### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column "source_unit_id" of relation "t_lqg_sample" violates not-null constraint
          Detail: Failing row contains (9000001999, organoid, null, ZmFrZUNpcGhlcg==, 9000000111, ...).
        ### The error may exist in org/dromara/lqg/sample/mapper/SampleMapper.java (best guess)
        ### SQL: INSERT INTO t_lqg_sample ( id, sample_kind, donor_name, hospital_no, create_by ) VALUES ( ?, ?, ?, ?, ? )
        """;

    /**
     * 回给前端的文本里不许出现的片段。
     */
    static final List<String> FORBIDDEN = List.of("INSERT", "t_lqg_", "PSQLException", "Mapper", "Failing row",
        "ZmFrZUNpcGhlcg", "9000000111", "org.dromara", "org.postgresql", "###");

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mp/ext/sample");

    static void assertNoLeak(R<?> result) {
        for (String bad : FORBIDDEN) {
            assertFalse(result.getMsg().contains(bad), "响应体泄露了「" + bad + "」：" + result.getMsg());
        }
    }

    static void assertHasErrorRef(R<?> result) {
        assertTrue(result.getMsg().matches(".*（错误编号 [0-9A-F]{8}）$"), "要带 8 位错误编号：" + result.getMsg());
    }

    @Test
    @DisplayName("兜底 RuntimeException：不回原始 message，回通用提示 + 错误编号")
    void runtimeExceptionDoesNotEchoMessage() {
        R<Void> result = handler.handleRuntimeException(new IllegalStateException(LEAKY_MESSAGE), request);
        assertEquals(R.FAIL, result.getCode());
        assertNoLeak(result);
        assertHasErrorRef(result);
        assertFalse(result.getMsg().contains("系统异常"), "回归脚本把「系统异常」当页面坏了的判据，别用这四个字");
    }

    @Test
    @DisplayName("兜底 Exception：同上")
    void exceptionDoesNotEchoMessage() {
        R<Void> result = handler.handleException(new IOException(LEAKY_MESSAGE), request);
        assertEquals(R.FAIL, result.getCode());
        assertNoLeak(result);
        assertHasErrorRef(result);
    }

    @Test
    @DisplayName("每次的错误编号不同（日志里按编号找堆栈）")
    void errorRefIsFresh() {
        String a = handler.handleRuntimeException(new RuntimeException("x"), request).getMsg();
        String b = handler.handleRuntimeException(new RuntimeException("x"), request).getMsg();
        assertFalse(a.equals(b), "两次的错误编号撞了：" + a);
    }

    @Test
    @DisplayName("文件存储异常（上游 OssException）按类名识别：回「文件存储服务出错」，不回存储服务原文")
    void ossExceptionIsSummarized() {
        R<Void> result = handler.handleRuntimeException(
            new OssException("上传文件失败，请检查配置信息:[endpoint=127.0.0.1:9000 bucket=ruoyi AccessDenied]"), request);
        assertTrue(result.getMsg().startsWith("文件存储服务出错"), result.getMsg());
        assertFalse(result.getMsg().contains("bucket"), result.getMsg());
        assertHasErrorRef(result);
    }

    @Test
    @DisplayName("业务异常 ServiceException：提示与业务码原样保留（本来就是写给用户看的）")
    void serviceExceptionKeepsMessage() {
        R<Void> plain = handler.handleServiceException(new ServiceException("请先拍照或选择图片"), request);
        assertEquals("请先拍照或选择图片", plain.getMsg());
        assertEquals(R.FAIL, plain.getCode());
        R<Void> coded = handler.handleServiceException(new ServiceException("没有访问权限", 403), request);
        assertEquals("没有访问权限", coded.getMsg());
        assertEquals(403, coded.getCode());
    }

    @Test
    @DisplayName("参数校验：提示原样；日志摘要不带被拒绝的值（供体姓名不落明文日志）")
    void validationKeepsMessageButLogsNoRejectedValue() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "sampleBo");
        binding.addError(new FieldError("sampleBo", "donorName", "张三丰张三丰张三丰张三丰张三丰", false,
            null, null, "供体姓名不能超过 20 个字"));
        BindException e = new BindException(binding);
        assertEquals("供体姓名不能超过 20 个字", handler.handleBindException(e).getMsg());

        String summary = GlobalExceptionHandler.describeErrors(binding.getAllErrors());
        assertEquals("sampleBo.donorName: 供体姓名不能超过 20 个字", summary);
        assertFalse(summary.contains("张三丰"), "日志摘要带了被拒绝的值：" + summary);
        assertTrue(e.getMessage().contains("张三丰"), "对照：框架原文确实带值（所以日志不能直接打 e.getMessage()）");
    }

    @Test
    @DisplayName("上传超限（V05）：413 + 中文提示，数值取当前 multipart 配置")
    void uploadTooLargeIsExplained() {
        MaxUploadSizeExceededException e = new MaxUploadSizeExceededException(-1,
            new IllegalStateException("UT000020: Connection terminated as request was larger than 62914560"));
        R<Void> byDefault = handler.handleMaxUploadSizeExceededException(e, request);
        assertEquals(413, byDefault.getCode());
        assertEquals("上传的文件太大：单个文件不能超过 50MB，一次上传合计不能超过 60MB", byDefault.getMsg());

        MultipartProperties properties = new MultipartProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(20));
        properties.setMaxRequestSize(DataSize.ofKilobytes(30 * 1024 + 512));
        R<Void> configured = new GlobalExceptionHandler(properties).handleMaxUploadSizeExceededException(e, request);
        assertEquals("上传的文件太大：单个文件不能超过 20MB，一次上传合计不能超过 31232KB", configured.getMsg());
        assertFalse(configured.getMsg().contains("UT000020"));
    }

    @Test
    @DisplayName("其它 multipart 解析失败：400 + 中文提示，不回 Undertow 原文")
    void otherMultipartFailures() {
        R<Void> result = handler.handleMultipartException(
            new MultipartException("Failed to parse multipart servlet request", new IOException("UT000036: Connection terminated")),
            request);
        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().startsWith("文件上传失败"), result.getMsg());
        assertFalse(result.getMsg().contains("UT0000"), result.getMsg());
    }

    @Test
    @DisplayName("ServletException：框架的请求形状错误原样；其它回通用提示")
    void servletExceptions() {
        R<Void> missing = handler.handleServletException(
            new MissingServletRequestParameterException("file", "MultipartFile"), request);
        assertTrue(missing.getMsg().contains("'file'"), "缺参数的提示要保留：" + missing.getMsg());
        assertFalse(missing.getMsg().contains("MultipartFile"), "不回方法参数的 Java 类型：" + missing.getMsg());

        R<Void> wrapped = handler.handleServletException(new ServletException(LEAKY_MESSAGE), request);
        assertNoLeak(wrapped);
        assertHasErrorRef(wrapped);
    }

    @Test
    @DisplayName("请求体反序列化失败（字段类型不对）：只回「请求参数格式不正确」，不回内部类名；日志摘要不带被拒绝的值")
    void unreadableBodyIsGeneric() {
        InvalidFormatException cause = null;
        try {
            new ObjectMapper().readValue("{\"age\":\"张三\"}", Probe.class);
        } catch (InvalidFormatException e) {
            cause = e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertTrue(cause != null, "Jackson 应当报 InvalidFormatException");
        assertTrue(cause.getMessage().contains("org.dromara"), "对照：Jackson 原文确实带内部类名 —— " + cause.getMessage());
        HttpMessageNotReadableException e = new HttpMessageNotReadableException("JSON parse error: " + cause.getMessage(),
            cause, new MockHttpInputMessage(new byte[0]));
        R<Void> result = handler.handleHttpMessageNotReadableException(e, request);
        assertEquals(400, result.getCode());
        assertEquals("请求参数格式不正确", result.getMsg());

        String logged = GlobalExceptionHandler.describeUnreadable(cause);
        assertEquals("字段 age 期望类型 java.lang.Integer（InvalidFormatException）", logged);
        assertFalse(logged.contains("张三"), "日志摘要带了被拒绝的值：" + logged);
    }

    @Test
    @DisplayName("请求体不是合法 JSON：同一句通用提示")
    void malformedJsonIsGeneric() {
        com.fasterxml.jackson.core.JsonParseException parse = null;
        try {
            new ObjectMapper().readTree("{\"donorName\": 测试供体甲}");
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            parse = e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertTrue(parse != null);
        assertEquals("请求参数格式不正确", handler.handleJsonParseException(parse, request).getMsg());
        HttpMessageNotReadableException wrapped = new HttpMessageNotReadableException("JSON parse error", parse,
            new MockHttpInputMessage(new byte[0]));
        assertEquals("请求参数格式不正确", handler.handleHttpMessageNotReadableException(wrapped, request).getMsg());
    }

    @Test
    @DisplayName("路径 / 查询参数类型不对（MethodArgumentTypeMismatchException）：只回「请求参数格式不正确」，不回类型名与输入值")
    void typeMismatchIsGeneric() throws Exception {
        org.springframework.core.MethodParameter parameter = new org.springframework.core.MethodParameter(
            Probe.class.getMethod("byId", Long.class), 0);
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e =
            new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException("abc", Long.class, "id",
                parameter, new NumberFormatException("For input string: \"abc\""));
        R<Void> result = handler.handleMethodArgumentTypeMismatchException(e, request);
        assertEquals(400, result.getCode());
        assertEquals("请求参数格式不正确", result.getMsg());
        assertFalse(result.getMsg().contains("Long") || result.getMsg().contains("abc"), result.getMsg());
    }

    @Test
    @DisplayName("SpEL 异常：服务端配置问题，表达式原文不外露")
    void spelIsGeneric() {
        R<Void> result = handler.handleSpelException(
            new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, "T(org.dromara.Secret)"), request);
        assertFalse(result.getMsg().contains("org.dromara"), result.getMsg());
        assertHasErrorRef(result);
    }

    @Test
    @DisplayName("按类名判断异常（含父类）")
    void instanceOfByName() {
        assertTrue(GlobalExceptionHandler.isInstanceOf(new IllegalStateException(), "java.lang.RuntimeException"));
        assertFalse(GlobalExceptionHandler.isInstanceOf(new IllegalStateException(), "java.io.IOException"));
        assertEquals("50MB", GlobalExceptionHandler.humanSize(DataSize.ofMegabytes(50)));
    }

    /**
     * Jackson 反序列化的探针（{@code age} 是整数，喂字符串就报 InvalidFormatException）；
     * {@link #byId} 给类型不匹配的用例当方法参数。
     */
    public static class Probe {
        public Integer age;

        public void byId(Long id) {
        }
    }

}
