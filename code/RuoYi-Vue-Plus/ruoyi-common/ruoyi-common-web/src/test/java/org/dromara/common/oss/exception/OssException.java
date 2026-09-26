package org.dromara.common.oss.exception;

/**
 * <b>测试替身</b>：与 ruoyi-common-oss 的 {@code OssException} 同名同包。
 *
 * <p>ruoyi-common-web 不依赖 ruoyi-common-oss，{@code GlobalExceptionHandler} 按<b>类名</b>识别
 * 文件存储异常（回「文件存储服务出错」而不是存储服务的原始报错）。本类只存在于本模块的测试类路径。
 */
public class OssException extends RuntimeException {

    public OssException(String message) {
        super(message);
    }

}
