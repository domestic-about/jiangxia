package org.dromara.lqg.ocr.provider;

/**
 * 「按次收费的第三方识别服务」这一类的标记接口（ADR-0007、合同第二条第 5 款）。
 *
 * <p>★ 为什么要有这个标记，而不是在工厂里按 provider 名字硬编码一个字符串：
 * 选择口径是「按次收费的实现<b>必须</b>同时满足 {@code lqg.ocr.paid-enabled=true} 才会被选中」。
 * 将来 OCR-SPIKE-001 结论出来、真实 provider 落成实现类（或再加第二家），只要它实现本接口，
 * 付费开关就自动对它生效 —— 不需要回去改工厂里的 if 分支，也就不会漏掉一条线。
 *
 * <p>★ 本接口<b>不新增方法</b>：识别签名就是 {@link OcrProvider#recognize(byte[])}。
 * 收费与否是「这一类的性质」，不是「这次调用的参数」。
 *
 * @author OCR-IMPL-001
 */
public interface PaidOcrProvider extends OcrProvider {
}
