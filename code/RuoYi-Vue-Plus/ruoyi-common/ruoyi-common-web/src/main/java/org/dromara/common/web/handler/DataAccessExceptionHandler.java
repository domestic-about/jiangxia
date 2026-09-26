package org.dromara.common.web.handler;

import cn.hutool.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

/**
 * 数据库类异常的统一出口（独立验收 V03，2026-09-23）：<b>只回通用提示，细节只进服务端日志</b>。
 *
 * <p>活体取证：外部写接口缺字段时，数据库的 NOT NULL 违例经兜底处理器 {@code R.fail(e.getMessage())}
 * 原样回给了前端 —— INSERT 语句、表名列名、整行数据（含供体姓名 / 住院号的密文、内部 user_id）、
 * Mapper 路径全在响应体里。本类把这一整类异常收口：
 * <ul>
 *   <li>{@link DuplicateKeyException} → 409「数据库中已存在该记录」（与上游 {@code MybatisExceptionHandler} 同文案）；</li>
 *   <li>{@link DataIntegrityViolationException}（非空 / 外键 / 检查约束）→「数据不完整或与已有记录冲突」；</li>
 *   <li>其余 {@link DataAccessException}（含 {@code MyBatisSystemException}、SQL 语法错、连不上库）、
 *       {@link TransactionException}、{@link SQLException} →「数据处理出错」；</li>
 *   <li>全部带 8 位错误编号，日志里用同一个编号记完整堆栈（排查照样有据）。</li>
 * </ul>
 *
 * <p>★ <b>为什么是最高优先级</b>：Spring 按 advice 的顺序找第一个「有匹配方法」的 advice。
 * 上游 {@code MybatisExceptionHandler}（ruoyi-common-mybatis）对 {@code MyBatisSystemException}
 * 也回 {@code e.getMessage()}，而兜底的 {@code GlobalExceptionHandler} 又有 {@code RuntimeException}
 * 的方法 —— 谁先谁后取决于 bean 注册顺序，不可靠。本类实现 {@link Ordered} 排在最前，
 * 数据库类异常一律先到这里。上游那两条有语义的分支在这里照搬：
 * 根因是 Sa-Token 的 {@code NotLoginException}（mapper 里取登录用户时 token 已失效 / 被冻结）→ 401；
 * 根因是动态数据源的 {@code CannotFindDataSourceException} → 通用的数据源提示。
 * （这两个类不在本模块的编译期依赖里，按类名识别。）
 *
 * @author F4（V03）
 */
@Slf4j
@RestControllerAdvice
public class DataAccessExceptionHandler implements Ordered {

    static final String MSG_DUPLICATE = "数据库中已存在该记录，请联系管理员确认";

    static final String MSG_INTEGRITY = "保存失败：数据不完整或与已有记录冲突，请检查必填项后重试（错误编号 %s）";

    static final String MSG_DATA_ACCESS = "数据处理出错，请稍后重试；如一直出现请联系管理员（错误编号 %s）";

    static final String MSG_NOT_LOGIN = "认证失败，无法访问系统资源";

    static final String MSG_NO_DATASOURCE = "未找到数据源，请联系管理员确认";

    private static final String NOT_LOGIN_EXCEPTION = "cn.dev33.satoken.exception.NotLoginException";

    private static final String NO_DATASOURCE_EXCEPTION =
        "com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 主键或 UNIQUE 索引冲突。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        String ref = GlobalExceptionHandler.newErrorRef();
        log.error("[{}] 请求地址'{}',数据库中已存在记录", ref, request.getRequestURI(), e);
        return R.fail(HttpStatus.HTTP_CONFLICT, MSG_DUPLICATE);
    }

    /**
     * 非空 / 外键 / 检查约束违例（V03 活体复现的就是这一支：外部提交缺字段 → NOT NULL）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<Void> handleDataIntegrityViolationException(DataIntegrityViolationException e,
                                                         HttpServletRequest request) {
        String ref = GlobalExceptionHandler.newErrorRef();
        log.error("[{}] 请求地址'{}',数据完整性约束违例", ref, request.getRequestURI(), e);
        return R.fail(HttpStatus.HTTP_INTERNAL_ERROR, String.format(MSG_INTEGRITY, ref));
    }

    /**
     * 其余数据库类异常（含 {@code MyBatisSystemException}、{@code BadSqlGrammarException}、连不上库、事务异常）。
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class, SQLException.class})
    public R<Void> handleDataAccessException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        Throwable notLogin = findCause(e, NOT_LOGIN_EXCEPTION);
        if (notLogin != null) {
            log.error("请求地址'{}',认证失败'{}',无法访问系统资源", requestURI, notLogin.getMessage());
            return R.fail(HttpStatus.HTTP_UNAUTHORIZED, MSG_NOT_LOGIN);
        }
        String ref = GlobalExceptionHandler.newErrorRef();
        if (findCause(e, NO_DATASOURCE_EXCEPTION) != null) {
            log.error("[{}] 请求地址'{}', 未找到数据源", ref, requestURI, e);
            return R.fail(HttpStatus.HTTP_INTERNAL_ERROR, MSG_NO_DATASOURCE);
        }
        log.error("[{}] 请求地址'{}',数据库操作异常", ref, requestURI, e);
        return R.fail(HttpStatus.HTTP_INTERNAL_ERROR, String.format(MSG_DATA_ACCESS, ref));
    }

    /**
     * 在异常链里按类名（含父类）找一个异常；找不到返回 null。
     */
    static Throwable findCause(Throwable e, String className) {
        Throwable t = e;
        int depth = 0;
        while (t != null && depth++ < 32) {
            if (GlobalExceptionHandler.isInstanceOf(t, className)) {
                return t;
            }
            if (t.getCause() == t) {
                break;
            }
            t = t.getCause();
        }
        return null;
    }

}
