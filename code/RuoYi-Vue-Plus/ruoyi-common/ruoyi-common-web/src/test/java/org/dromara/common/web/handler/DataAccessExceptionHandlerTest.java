package org.dromara.common.web.handler;

import cn.dev33.satoken.exception.NotLoginException;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import static org.dromara.common.web.handler.GlobalExceptionHandlerTest.LEAKY_MESSAGE;
import static org.dromara.common.web.handler.GlobalExceptionHandlerTest.assertHasErrorRef;
import static org.dromara.common.web.handler.GlobalExceptionHandlerTest.assertNoLeak;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库类异常只回通用提示（独立验收 V03）。
 *
 * <p>两层：① 直接调处理方法，钉每一类异常的文案与业务码；② 起一个最小的 Spring MVC 异常解析器，
 * 把一个「像上游 MybatisExceptionHandler 那样回 e.getMessage()」的 advice <b>先注册</b>，
 * 证明数据库异常仍然落到本处理器（最高优先级），而业务异常、兜底异常的走向不受影响。
 */
class DataAccessExceptionHandlerTest {

    private final DataAccessExceptionHandler handler = new DataAccessExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mp/ext/sample");

    // ── ① 每一类异常 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("非空约束违例（V03 活体那一支）：不回 SQL / 表名 / 行数据 / Mapper，提示检查必填项")
    void integrityViolation() {
        R<Void> result = handler.handleDataIntegrityViolationException(
            new DataIntegrityViolationException(LEAKY_MESSAGE), request);
        assertEquals(R.FAIL, result.getCode());
        assertNoLeak(result);
        assertTrue(result.getMsg().contains("必填项"), result.getMsg());
        assertHasErrorRef(result);
    }

    @Test
    @DisplayName("主键 / 唯一索引冲突：409 + 与上游同一句提示")
    void duplicateKey() {
        R<Void> result = handler.handleDuplicateKeyException(new DuplicateKeyException(LEAKY_MESSAGE), request);
        assertEquals(409, result.getCode());
        assertEquals("数据库中已存在该记录，请联系管理员确认", result.getMsg());
    }

    @Test
    @DisplayName("其余数据库异常（MyBatis 系统异常 / 超时 / 连不上库 / 裸 SQLException）：通用提示 + 错误编号")
    void otherDataAccessFailures() {
        List<Exception> failures = List.of(
            new UncategorizedDataAccessException(LEAKY_MESSAGE, new SQLException(LEAKY_MESSAGE)) {
            },
            new QueryTimeoutException(LEAKY_MESSAGE),
            new CannotCreateTransactionException(LEAKY_MESSAGE),
            new SQLException(LEAKY_MESSAGE));
        for (Exception failure : failures) {
            R<Void> result = handler.handleDataAccessException(failure, request);
            assertEquals(500, result.getCode(), failure.getClass().getName());
            assertNoLeak(result);
            assertHasErrorRef(result);
        }
    }

    @Test
    @DisplayName("根因是 Sa-Token 的 NotLoginException（token 失效 / 被冻结）→ 401，与上游 MybatisExceptionHandler 同口径")
    void notLoginRootCauseStays401() {
        DataAccessException e = new UncategorizedDataAccessException("nested",
            new RuntimeException("wrap", new NotLoginException("token 已被冻结"))) {
        };
        R<Void> result = handler.handleDataAccessException(e, request);
        assertEquals(401, result.getCode());
        assertEquals("认证失败，无法访问系统资源", result.getMsg());
    }

    @Test
    @DisplayName("最高优先级（先于上游 MybatisExceptionHandler 与兜底处理器）")
    void highestPrecedence() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, handler.getOrder());
    }

    // ── ② 真实的 advice 解析顺序 ──────────────────────────────────────────────────

    @Test
    @DisplayName("解析器里：上游式 advice 先注册，数据库异常照样落到本处理器；业务异常与兜底异常走向不变")
    void resolverPicksThisHandlerFirst() throws Exception {
        StaticWebApplicationContext context = new StaticWebApplicationContext();
        context.setServletContext(new MockServletContext());
        // 注册顺序故意把「会回原文」的 advice 放在最前面（模拟上游 MybatisExceptionHandler 先注册的情形）
        context.registerSingleton("leakyUpstreamAdvice", LeakyUpstreamAdvice.class);
        context.registerSingleton("globalExceptionHandler", GlobalExceptionHandler.class);
        context.registerSingleton("dataAccessExceptionHandler", DataAccessExceptionHandler.class);
        context.refresh();
        ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
        resolver.setApplicationContext(context);
        resolver.setMessageConverters(List.of(new MappingJackson2HttpMessageConverter()));
        resolver.afterPropertiesSet();
        HandlerMethod handlerMethod = new HandlerMethod(new DummyController(), DummyController.class.getMethod("submit"));

        String db = resolve(resolver, handlerMethod, new DataIntegrityViolationException(LEAKY_MESSAGE));
        assertFalse(db.contains("INSERT"), "数据库异常被上游式 advice 截走、回了原文：" + db);
        assertTrue(db.contains("必填项"), db);

        String mybatis = resolve(resolver, handlerMethod,
            new UncategorizedDataAccessException(LEAKY_MESSAGE, new SQLException("x")) {
            });
        assertFalse(mybatis.contains("t_lqg_"), mybatis);

        String business = resolve(resolver, handlerMethod, new ServiceException("该账号无权登录工作台，请使用小程序登录"));
        assertTrue(business.contains("该账号无权登录工作台"), "业务异常的提示要原样：" + business);

        String fallback = resolve(resolver, handlerMethod, new IllegalArgumentException(LEAKY_MESSAGE));
        assertFalse(fallback.contains("INSERT"), fallback);
        assertTrue(fallback.contains("错误编号"), fallback);
    }

    private static String resolve(ExceptionHandlerExceptionResolver resolver, HandlerMethod handlerMethod,
                                  Exception exception) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mp/ext/sample");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertNotNull(resolver.resolveException(request, response, handlerMethod, exception),
            "没有 advice 处理 " + exception.getClass().getName());
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 模拟上游 {@code MybatisExceptionHandler} 的形状：对数据库异常回 {@code e.getMessage()}。
     */
    @RestControllerAdvice
    public static class LeakyUpstreamAdvice {

        @ExceptionHandler(DataAccessException.class)
        public R<Void> leak(DataAccessException e) {
            return R.fail(500, e.getMessage());
        }

    }

    /**
     * 解析器需要一个「出错的处理方法」做上下文。
     */
    public static class DummyController {

        public R<Void> submit() {
            return R.ok();
        }

    }

}
