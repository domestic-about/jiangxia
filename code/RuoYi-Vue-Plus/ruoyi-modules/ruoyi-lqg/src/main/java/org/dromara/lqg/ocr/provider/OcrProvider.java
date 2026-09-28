package org.dromara.lqg.ocr.provider;

import java.util.List;

/**
 * 识别适配层（ADR-0007）：<b>一个方案一个实现类，配置切换</b>。
 *
 * <p>★ 只做一件事：把图片字节变成原始文本行。「解析成字段」是
 * {@link org.dromara.lqg.ocr.domain.OcrFieldParser} 的事，与 provider 无关 ——
 * 换一家付费服务，字段解析规则一个字都不用改。
 *
 * <p>★ 图片只在内存里：入参是 {@code byte[]}，实现<b>不许</b>把图写进 OSS / sys_oss，
 * 也不许落任何业务表（accept 1 的 counterfeit 第 5 条：多留一份图 = 多留一份供体信息）。
 *
 * <p>实现类：{@link NoneOcrProvider}（缺省，提示手填）、{@link StubOcrProvider}
 * （只 dev / test）、{@code org.dromara.lqg.ocr.provider.PaidOcrProvider}（按次收费，等
 * OCR-SPIKE-001 探路结论出来后落真实实现）。
 *
 * @author OCR-IMPL-001
 */
public interface OcrProvider {

    /**
     * 识别。
     *
     * @param image 图片字节（jpg / png；调用方已校验大小与格式）
     * @return 原始文本行（按服务返回的顺序；空列表表示「没识别出文字」而不是异常）
     * @throws org.dromara.common.core.exception.ServiceException provider 不可用 / 超时 / 服务报错时
     */
    List<String> recognize(byte[] image);

}
