package org.dromara.lqg.auth.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.domain.model.XcxLoginBody;

import java.io.Serial;

/**
 * 小程序登录入参（AUTH-LOGIN-001）。
 *
 * <p>比上游 {@link XcxLoginBody} 多一个 {@code phoneCode}：微信「手机号快速验证」拿到的动态 code，
 * 后端拿它去换**微信背书的手机号**（FLOW:F-AUTH-01.step2）。
 *
 * <p>★ 本类**刻意只收这三个字段**。请求体里夹带的 {@code identity} / {@code role} / {@code isInternal}
 * 之类字段在这里没有对应属性，Jackson 反序列化时直接丢掉 —— 内外部判定只认微信返回的手机号，
 * 认的就是这个类没有那些字段（甲方原话「万一就是外部的人员想看我们内部更多的信息」，ADR-0003）。
 *
 * <p>{@code clientId} / {@code grantType} / {@code tenantId} 继承自上游 {@code LoginBody}，
 * 登录入口（{@code AuthController}）在进策略之前就用它们校验客户端与授权类型。
 *
 * @author AUTH-LOGIN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WxLoginCredentials extends XcxLoginBody {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 微信「手机号快速验证」的动态 code；dev / test 下 mock 形式是 {@code mock:<手机号>}。
     */
    @NotBlank(message = "{xcx.phone.code.not.blank}")
    private String phoneCode;

}
