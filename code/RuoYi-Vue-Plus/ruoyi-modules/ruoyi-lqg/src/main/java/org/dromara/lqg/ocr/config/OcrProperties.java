package org.dromara.lqg.ocr.config;

import org.dromara.lqg.ocr.provider.NoneOcrProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 识别域配置（{@code lqg.ocr.*}）的运行期读取（ADR-0007）。
 *
 * <p>★ <b>{@code paid-enabled} 缺省 false，且 {@code application-prod.yml} 里刻意不出现这个键</b>
 * —— 与 {@code lqg.auth.mock-login} 同一思路：写一行 {@code paid-enabled: false}「以示安全」
 * 反而危险，它离被改成 true 只差一次手滑（accept 2 第 2 段扫的就是这个）。生产上要开付费通道，
 * 由部署方在环境变量 / 外部配置里显式打开，并留下书面同意（合同第二条第 5 款）。
 *
 * <p>★ {@code provider} 缺省 {@code none}：没配 provider 时走 {@link NoneOcrProvider}，
 * 小程序提示「请手动填写」而不是弹系统异常（accept 2 第 3 段）。
 *
 * @author OCR-IMPL-001
 */
@Component
public class OcrProperties {

    /**
     * 当前使用的识别方案名（{@code none} / {@code stub} / 将来的真实实现类名）。
     */
    @Value("${lqg.ocr.provider:" + NoneOcrProvider.NAME + "}")
    private String provider;

    /**
     * 按次收费通道开关。**缺省 false**；prod 配置文件里不出现这个键。
     */
    @Value("${lqg.ocr.paid-enabled:false}")
    private boolean paidEnabled;

    /**
     * 单张图大小上限（字节，缺省 5MB，ticket §2）。由 {@code MpOcrController} 自己判，
     * 与 {@code spring.servlet.multipart} 的全局上限（附件 50MB / 一次 60MB）无关 —— 那个只是更外面的一道闸。
     */
    @Value("${lqg.ocr.max-bytes:5242880}")
    private long maxBytes;

    public String getProvider() {
        return provider;
    }

    public boolean isPaidEnabled() {
        return paidEnabled;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

}
