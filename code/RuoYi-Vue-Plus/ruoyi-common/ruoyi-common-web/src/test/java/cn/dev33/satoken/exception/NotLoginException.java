package cn.dev33.satoken.exception;

/**
 * <b>测试替身</b>：与 Sa-Token 的 {@code NotLoginException} 同名同包。
 *
 * <p>ruoyi-common-web 不依赖 Sa-Token，{@code DataAccessExceptionHandler} 按<b>类名</b>识别
 * 「mapper 里取登录用户时 token 已失效」这一支（→ 401）。本类只存在于本模块的测试类路径，
 * 用来证明按类名识别确实生效；不会进任何产物。
 */
public class NotLoginException extends RuntimeException {

    public NotLoginException(String message) {
        super(message);
    }

}
