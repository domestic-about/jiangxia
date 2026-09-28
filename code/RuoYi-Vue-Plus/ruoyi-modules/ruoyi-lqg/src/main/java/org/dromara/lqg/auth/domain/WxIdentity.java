package org.dromara.lqg.auth.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信登录换回来的最小事实集：openid + **微信背书的手机号**。
 *
 * <p>mock 路径与真实路径（code2session / 手机号快速验证）拿到的东西都收敛到这个形状，
 * 之后走**同一段**代码（FLOW:F-AUTH-01.step3 起）—— 两条路径只在「怎么拿到这两个值」上有差别，
 * 不允许在绑定 / 建号逻辑上出现第二份实现。
 *
 * @param openid 小程序 openid
 * @param phone  微信返回的手机号（内外部判定只认它）
 * @param unionid unionid，微信开放平台下才返回，可空
 * @author AUTH-LOGIN-001
 */
@Data
public class WxIdentity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String openid;

    @NotBlank
    private String phone;

    /**
     * unionid（没有就是 null，不强求 —— SSOT 里 unionid 可空）
     */
    private String unionid;

    public WxIdentity() {
    }

    public WxIdentity(String openid, String phone, String unionid) {
        this.openid = openid;
        this.phone = phone;
        this.unionid = unionid;
    }

}
