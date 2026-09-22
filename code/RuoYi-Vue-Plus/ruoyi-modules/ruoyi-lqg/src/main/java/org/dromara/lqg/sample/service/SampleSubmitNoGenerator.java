package org.dromara.lqg.sample.service;

import lombok.RequiredArgsConstructor;
import org.dromara.lqg.sample.guard.SampleKindRules;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.springframework.stereotype.Service;

/**
 * 送检单号取号（ticket §2.1 口径 3）。
 *
 * <p>★ <b>走序列 {@code seq_lqg_submit_no}，不用 {@code max(submit_no)+1}</b>：
 * 后者在两个人同时提交时会取到同一个号（accept 的 counterfeit 点名这一形态）。
 * 序列由本票的 Flyway 迁移建（accept 第 1 条点名断它存在）。
 *
 * <p>取号是「消耗性」的：校验失败、事务回滚都不还号。送检单号<b>允许有洞、不允许重复</b> ——
 * 这是序列的正确语义（还号反而会引入重复风险）。
 *
 * @author SAMPLE-MODEL-001
 */
@Service
@RequiredArgsConstructor
public class SampleSubmitNoGenerator {

    private final SampleMapper sampleMapper;

    /**
     * 取下一个送检单号，形状 {@code SJ + 8 位序号}。
     *
     * <p>注意本方法**若被调用就一定消耗一个序列值**（{@code nextval} 不是事务性的）：
     * 只在真正要落库的那一次调用它，别拿它当「可用性探测」。
     */
    public String next() {
        Long seq = sampleMapper.nextSubmitNoSeq();
        if (seq == null) {
            throw new IllegalStateException("seq_lqg_submit_no 取号失败：序列不存在？本票的 Flyway 迁移建它");
        }
        return SampleKindRules.formatSubmitNo(seq);
    }

}
