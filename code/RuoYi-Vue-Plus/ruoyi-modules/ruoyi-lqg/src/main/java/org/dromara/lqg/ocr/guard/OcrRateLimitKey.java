package org.dromara.lqg.ocr.guard;

import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

/**
 * 识别接口限流 key 的「用户维度」提供者（ticket §2：<b>每用户</b>每分钟 6 次）。
 *
 * <p>★ 为什么要有这个 bean：框架的 {@code @RateLimiter} 把 key 拼成
 * {@code global:rate_limit:<URI>:<自定义key>}，缺省不含用户；而 Redisson 的
 * {@code RateLimiter} 是<b>按 key 的令牌桶</b>、状态一天才清。不加用户维度的实测后果：
 * 一个人刷完以后所有人都拿到「识别太频繁」（连验收用例都跑不完），而且桶跨进程共享 ——
 * 同一台机上的 8081 后端也会把 8094 的桶吃空。
 *
 * <p>★ 为什么不能直接写 {@code #T(...)}：框架建的是
 * {@code MethodBasedEvaluationContext}，<b>没有设 TypeLocator</b>，
 * 实测 {@code #T(...)} → {@code EL1006E: Function 'T' could not be found}；
 * 写 {@code user:#T(...)} 还会先撞 SpEL 的冒号语法 → {@code EL1041E}。
 * 它设了 {@code BeanFactoryResolver}（{@code @beanName.method()} 可用），所以走这一条。
 *
 * <p>★ 方法名是 {@code userKey} 而不是 {@code key}：SpEL 里
 * {@code @bean.key()} 的 {@code key} 容易被误读成注解属性，取个不会歧义的名字。
 * 本类只读登录态、不查库、不写库（ADR-0004 的 I4 不受影响）。
 *
 * @author OCR-IMPL-001
 */
@Component("ocrRateLimitKey")
public class OcrRateLimitKey {

    /**
     * 当前登录用户 id（String 形式，直接用进限流 key）。
     *
     * @return 用户 id；框架的限流切面跑在 Sa-Token 拦截器之后，这里一定拿得到
     */
    public String userKey() {
        return LoginHelper.getUserIdStr();
    }

}
