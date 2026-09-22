package org.dromara.lqg.ocr.provider;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缺省 provider（{@code lqg.ocr.provider=none}，也是没配时的缺省值）：
 * <b>不识别，明确让用户手填</b>。
 *
 * <p>★ 它存在的意义就是「没接识别服务时，小程序照常能用」：抛的是带业务 msg 的
 * {@link ServiceException}（前端弹「识别暂不可用，请手动填写」），<b>不是</b>
 * {@code NullPointerException}（那会弹「系统异常」，用户以为坏了，accept 2 counterfeit 第 3 条）。
 *
 * <p>★ 本票<b>不接任何真实识别服务</b>（ticket §3、ADR-0007）：真实 provider 等
 * OCR-SPIKE-001 的探路结论；在那之前这个类是唯一被选中的实现（除非 dev / test 配了
 * {@link StubOcrProvider}）。
 *
 * @author OCR-IMPL-001
 */
@Component("none")
public class NoneOcrProvider implements OcrProvider {

    /**
     * 配在 {@code lqg.ocr.provider} 里的名字。
     */
    public static final String NAME = "none";

    /**
     * 给前端看的提示语（accept 2 断言「明确报请手动填写而不是 500」的判据）。
     */
    public static final String MESSAGE = "识别暂不可用，请手动填写";

    @Override
    public List<String> recognize(byte[] image) {
        throw new ServiceException(MESSAGE);
    }

}
