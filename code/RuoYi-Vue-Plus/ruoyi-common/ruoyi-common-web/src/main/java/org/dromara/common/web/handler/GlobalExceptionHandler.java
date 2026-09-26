package org.dromara.common.web.handler;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpStatus;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.exception.SseException;
import org.dromara.common.core.exception.base.BaseException;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.expression.ExpressionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 全局异常处理器
 *
 * <p>★ 安全口径（独立验收 V03，2026-09-23）：<b>只有本来就写给用户看的提示才原样回给前端</b>——
 * {@link ServiceException} / {@link BaseException}、参数校验、上传超限、请求形状错误（缺参数、方法不对）。
 * 其余一律回<b>通用提示 + 错误编号</b>，原始 message 与堆栈只进服务端日志（带同一个编号，方便对账）：
 * <ul>
 *   <li>兜底的 {@link RuntimeException} / {@link Exception}：message 里可能是 SQL、表名列名、
 *       整行数据（含加密列密文、内部 user_id）、Mapper 路径、内部类名；</li>
 *   <li>数据库类异常另有 {@link DataAccessExceptionHandler}（最高优先级，先于上游的
 *       {@code MybatisExceptionHandler}），这里的兜底是第二道保险。</li>
 * </ul>
 * 日志里的参数校验异常只记字段名与提示，不记被拒绝的值（V19：供体姓名 / 住院号不落明文日志）。
 *
 * @author Lion Li
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 兜底提示（{@code %s} = 错误编号）。刻意不用「系统异常」四个字：小程序与工作台的回归脚本把它当作「页面坏了」的判据。
     */
    static final String MSG_SERVER_ERROR = "服务器处理出错，请稍后重试；如一直出现请联系管理员（错误编号 %s）";

    /**
     * 请求参数 / 请求体格式不对时的统一提示：不回 Jackson / Spring 的原文（里面有内部类名，
     * 例如 {@code org.dromara.lqg.ext.domain.bo.ExtSampleSubmitBo}、字段的 Java 类型、被拒绝的值），
     * 细节只进服务端日志。
     */
    static final String MSG_BAD_REQUEST = "请求参数格式不正确";

    /**
     * 上传超限提示（{@code %s} = 单个文件上限、一次上传合计上限）。
     */
    static final String MSG_UPLOAD_TOO_LARGE = "上传的文件太大：单个文件不能超过 %s，一次上传合计不能超过 %s";

    /**
     * 文件存储（OSS / MinIO）出错的提示（{@code %s} = 错误编号）。上游 OssException 的 message 里带
     * 存储服务的原始报错（endpoint、桶名），不外露。
     */
    static final String MSG_STORAGE_ERROR = "文件存储服务出错，请稍后重试；如一直出现请联系管理员（错误编号 %s）";

    /**
     * 上游文件存储异常的类名（ruoyi-common-web 不依赖 ruoyi-common-oss，按类名识别）。
     */
    private static final String OSS_EXCEPTION = "org.dromara.common.oss.exception.OssException";

    /**
     * multipart 上限的缺省值（与 application.yml 的 spring.servlet.multipart 同口径；拿不到配置时兜底用）。
     */
    private static final DataSize DEFAULT_MAX_FILE_SIZE = DataSize.ofMegabytes(50);

    private static final DataSize DEFAULT_MAX_REQUEST_SIZE = DataSize.ofMegabytes(60);

    /**
     * 当前生效的 multipart 配置（{@code spring.servlet.multipart.*}）；为 null 时用上面的缺省值。
     */
    private final MultipartProperties multipartProperties;

    public GlobalExceptionHandler() {
        this(null);
    }

    public GlobalExceptionHandler(MultipartProperties multipartProperties) {
        this.multipartProperties = multipartProperties;
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                       HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return R.fail(HttpStatus.HTTP_BAD_METHOD, e.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.error(e.getMessage());
        Integer code = e.getCode();
        return ObjectUtil.isNotNull(code) ? R.fail(code, e.getMessage()) : R.fail(e.getMessage());
    }

    /**
     * 认证失败
     */
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(SseException.class)
    public String handleNotLoginException(SseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.debug("请求地址'{}',认证失败'{}',无法访问系统资源", requestURI, e.getMessage());
        return JsonUtils.toJsonString(R.fail(HttpStatus.HTTP_UNAUTHORIZED, "认证失败，无法访问系统资源"));
    }

    /**
     * servlet异常
     *
     * <p>Spring MVC 自己抛的请求形状错误（缺参数、缺文件、Content-Type 不对……）都实现了
     * {@link ErrorResponse}，它们的 message 讲的是「请求哪里不对」，照旧回给调用方；
     * 其它 ServletException（多半是过滤器里包出来的）回通用提示。
     */
    @ExceptionHandler(ServletException.class)
    public R<Void> handleServletException(ServletException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (e instanceof ErrorResponse errorResponse) {
            log.error("请求地址'{}',请求不合法：{}", requestURI, e.getMessage());
            // 用 ProblemDetail 的 detail（「Required parameter 'file' is not present.」这一类），
            // 不用 getMessage()：后者带方法参数的 Java 类型
            String detail = errorResponse.getBody().getDetail();
            return R.fail(detail == null || detail.isBlank() ? MSG_BAD_REQUEST : detail);
        }
        String ref = newErrorRef();
        log.error("[{}] 请求地址'{}',发生未知异常.", ref, requestURI, e);
        return R.fail(String.format(MSG_SERVER_ERROR, ref));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.error(e.getMessage());
        return R.fail(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public R<Void> handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI);
        return R.fail(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        Class<?> requiredType = e.getRequiredType();
        log.error("请求地址'{}',请求参数类型不匹配：参数[{}]要求类型[{}]", request.getRequestURI(), e.getName(),
            requiredType == null ? "?" : requiredType.getName());
        return R.fail(HttpStatus.HTTP_BAD_REQUEST, MSG_BAD_REQUEST);
    }

    /**
     * 找不到路由
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}'不存在.", requestURI);
        return R.fail(HttpStatus.HTTP_NOT_FOUND, e.getMessage());
    }

    /**
     * 上传超限（V05）：单个文件超过 {@code spring.servlet.multipart.max-file-size}，
     * 或一次请求超过 {@code max-request-size}。回明确的中文提示，而不是 Undertow 的英文原文。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("请求地址'{}',上传超限：{}", request.getRequestURI(), rootMessage(e));
        return R.fail(HttpStatus.HTTP_ENTITY_TOO_LARGE, uploadTooLargeMessage(multipartProperties));
    }

    /**
     * 其它 multipart 解析失败（不是 multipart 请求、边界不完整、客户端中途断开）。
     */
    @ExceptionHandler(MultipartException.class)
    public R<Void> handleMultipartException(MultipartException e, HttpServletRequest request) {
        log.warn("请求地址'{}',上传请求解析失败：{}", request.getRequestURI(), rootMessage(e));
        return R.fail(HttpStatus.HTTP_BAD_REQUEST, "文件上传失败：请求不完整或格式不正确，请重新选择文件上传");
    }

    /**
     * 拦截未知的运行时异常
     */
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IOException.class)
    public void handleIoException(IOException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (requestURI.contains("sse")) {
            // sse 经常性连接中断 例如关闭浏览器 直接屏蔽
            return;
        }
        log.error("请求地址'{}',连接中断", requestURI, e);
    }

    /**
     * sse 连接超时异常 不需要处理
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleRuntimeException(AsyncRequestTimeoutException e) {
    }

    /**
     * 拦截未知的运行时异常：<b>不回原始 message</b>（V03），细节带错误编号进日志。
     */
    @ExceptionHandler(RuntimeException.class)
    public R<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String ref = newErrorRef();
        log.error("[{}] 请求地址'{}',发生未知异常.", ref, requestURI, e);
        if (isInstanceOf(e, OSS_EXCEPTION)) {
            return R.fail(String.format(MSG_STORAGE_ERROR, ref));
        }
        return R.fail(String.format(MSG_SERVER_ERROR, ref));
    }

    /**
     * 系统异常：<b>不回原始 message</b>（V03），细节带错误编号进日志。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String ref = newErrorRef();
        log.error("[{}] 请求地址'{}',发生系统异常.", ref, requestURI, e);
        return R.fail(String.format(MSG_SERVER_ERROR, ref));
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        log.error("参数校验失败：{}", describeErrors(e.getAllErrors()));
        String message = StreamUtils.join(e.getAllErrors(), DefaultMessageSourceResolvable::getDefaultMessage, ", ");
        return R.fail(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> constraintViolationException(ConstraintViolationException e) {
        log.error(e.getMessage());
        String message = StreamUtils.join(e.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
        return R.fail(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("参数校验失败：{}", describeErrors(e.getBindingResult().getAllErrors()));
        String message = StreamUtils.join(e.getBindingResult().getAllErrors(), DefaultMessageSourceResolvable::getDefaultMessage, ", ");
        return R.fail(message);
    }

    /**
     * 方法参数校验异常 用于处理 @Validated 注解
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public R<Void> handlerMethodValidationException(HandlerMethodValidationException e) {
        log.error(e.getMessage());
        String message = StreamUtils.join(e.getAllErrors(), MessageSourceResolvable::getDefaultMessage, ", ");
        return R.fail(message);
    }

    /**
     * JSON 解析异常（Jackson 在处理 JSON 格式出错时抛出）
     * 可能是请求体格式非法，也可能是服务端反序列化失败
     */
    @ExceptionHandler(JsonParseException.class)
    public R<Void> handleJsonParseException(JsonParseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}' 发生 JSON 解析异常: {}", requestURI, e.getOriginalMessage());
        return R.fail(HttpStatus.HTTP_BAD_REQUEST, MSG_BAD_REQUEST);
    }

    /**
     * 请求体读取异常（通常是请求参数格式非法、字段类型不匹配等）
     *
     * <p>只回 {@value #MSG_BAD_REQUEST}：Jackson 原文里有内部类名（{@code org.dromara.….XxxBo}）、
     * 字段的 Java 类型与被拒绝的值。日志里记出错的字段路径（{@code items[0].age}）与期望类型，
     * <b>不记被拒绝的值</b>（可能是填错位置的供体姓名 / 住院号，V19）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.error("请求地址'{}', 参数解析失败：{}", request.getRequestURI(), describeUnreadable(e.getMostSpecificCause()));
        return R.fail(HttpStatus.HTTP_BAD_REQUEST, MSG_BAD_REQUEST);
    }

    /**
     * 请求体解析失败的日志摘要：字段路径 + 期望类型（映射错误）或解析器的原话（JSON 本身不合法，不含请求内容）。
     */
    static String describeUnreadable(Throwable cause) {
        if (cause instanceof MismatchedInputException mismatch) {
            Class<?> target = mismatch.getTargetType();
            return "字段 " + orUnknown(fieldPath(mismatch)) + " 期望类型 " + (target == null ? "?" : target.getName())
                + "（" + cause.getClass().getSimpleName() + "）";
        }
        if (cause instanceof JsonMappingException mapping) {
            return "字段 " + orUnknown(fieldPath(mapping)) + "（" + cause.getClass().getSimpleName() + "）";
        }
        if (cause instanceof JsonProcessingException processing) {
            return cause.getClass().getSimpleName() + ": " + processing.getOriginalMessage();
        }
        return cause.getClass().getSimpleName();
    }

    private static String orUnknown(String field) {
        return field == null || field.isEmpty() ? "?" : field;
    }

    /**
     * SpEL 表达式相关异常（服务端配置问题，不是调用方的错；表达式原文不外露）
     */
    @ExceptionHandler(ExpressionException.class)
    public R<Void> handleSpelException(ExpressionException e, HttpServletRequest request) {
        String ref = newErrorRef();
        log.error("[{}] 请求地址'{}'，SpEL解析异常", ref, request.getRequestURI(), e);
        return R.fail(HttpStatus.HTTP_INTERNAL_ERROR, String.format(MSG_SERVER_ERROR, ref));
    }

    /**
     * 上传超限提示（按当前生效的 multipart 配置取值；{@code UploadSizeLimitFilter} 与本类共用同一句话）。
     *
     * @param multipartProperties {@code spring.servlet.multipart.*}；null → 缺省 50MB / 60MB
     */
    public static String uploadTooLargeMessage(MultipartProperties multipartProperties) {
        return String.format(MSG_UPLOAD_TOO_LARGE, humanSize(maxFileSize(multipartProperties)),
            humanSize(maxRequestSize(multipartProperties)));
    }

    /**
     * 单个文件上限（{@code spring.servlet.multipart.max-file-size}）；拿不到配置时 50MB。
     */
    public static DataSize maxFileSize(MultipartProperties multipartProperties) {
        return multipartProperties == null || multipartProperties.getMaxFileSize() == null
            ? DEFAULT_MAX_FILE_SIZE : multipartProperties.getMaxFileSize();
    }

    /**
     * 一次请求上限（{@code spring.servlet.multipart.max-request-size}）；拿不到配置时 60MB。
     */
    public static DataSize maxRequestSize(MultipartProperties multipartProperties) {
        return multipartProperties == null || multipartProperties.getMaxRequestSize() == null
            ? DEFAULT_MAX_REQUEST_SIZE : multipartProperties.getMaxRequestSize();
    }

    /**
     * 50MB / 512KB 这种写法（整 MB 用 MB，否则 KB）。
     */
    static String humanSize(DataSize size) {
        long bytes = size.toBytes();
        if (bytes > 0 && bytes % (1024 * 1024) == 0) {
            return (bytes / (1024 * 1024)) + "MB";
        }
        if (bytes > 0 && bytes % 1024 == 0) {
            return (bytes / 1024) + "KB";
        }
        return bytes + "B";
    }

    /**
     * 错误编号：8 位，日志与返回给前端的提示里是同一个，运维按它在日志里找堆栈。
     */
    static String newErrorRef() {
        return IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /**
     * 校验错误的日志摘要：只要「对象.字段: 提示」，<b>不要被拒绝的值</b>
     * （{@link BindException#getMessage()} 原文里带 rejected value，会把供体姓名 / 住院号明文写进日志）。
     */
    static String describeErrors(List<ObjectError> errors) {
        return StreamUtils.join(errors, error -> {
            String name = error instanceof FieldError fieldError
                ? error.getObjectName() + "." + fieldError.getField()
                : error.getObjectName();
            return name + ": " + error.getDefaultMessage();
        }, "; ");
    }

    /**
     * Jackson 映射异常里出错字段的路径（{@code items[0].age}）；取不到返回空串。
     */
    static String fieldPath(JsonMappingException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    /**
     * 按类名判断异常（含父类），用于识别本模块没有编译期依赖的异常类型。
     */
    static boolean isInstanceOf(Throwable e, String className) {
        for (Class<?> type = e.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

}
