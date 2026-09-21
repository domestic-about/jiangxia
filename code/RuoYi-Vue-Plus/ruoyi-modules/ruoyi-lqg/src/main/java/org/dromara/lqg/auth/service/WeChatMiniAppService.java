package org.dromara.lqg.auth.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.auth.domain.WxIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 微信小程序侧的两步换取：{@code xcxCode → openid / unionid}、{@code phoneCode → 手机号}
 * （FLOW:F-AUTH-01.step1 / step2）。
 *
 * <p>本类只负责「向微信要事实」，不碰账号。拿到 {@link WxIdentity} 之后由
 * {@link WxAccountBindService} 走**同一段**绑定 / 建号代码 —— mock 路径也一样（ADR-0008）。
 *
 * <p>appid / secret 走配置（{@code lqg.wx.miniapp.*}），缺省为空串：dev / test 一律走 mock，
 * 真机联调时由部署方用环境变量注入（{@code LQG_WX_APPID} / {@code LQG_WX_SECRET}），
 * 与 ADR-0008「mock 只在 dev / test」互不冲突。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
@Service
public class WeChatMiniAppService {

    private final WxMaService wxMaService;

    public WeChatMiniAppService(@Value("${lqg.wx.miniapp.appid:}") String appid,
                                @Value("${lqg.wx.miniapp.secret:}") String secret) {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(appid);
        config.setSecret(secret);
        WxMaServiceImpl service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        this.wxMaService = service;
    }

    /**
     * code2session：小程序 {@code wx.login} 的 code 换 openid / unionid。
     *
     * @param xcxCode wx.login 拿到的 code
     * @return openid / unionid（unionid 可能为 null：小程序未关联微信开放平台时微信不返回）
     */
    public WxIdentity resolveOpenid(String xcxCode) {
        try {
            WxMaJscode2SessionResult session = wxMaService.jsCode2SessionInfo(xcxCode);
            if (session == null || session.getOpenid() == null || session.getOpenid().isBlank()) {
                throw new ServiceException("微信登录失败：code2session 没有返回 openid");
            }
            return new WxIdentity(session.getOpenid(), null, session.getUnionid());
        } catch (WxErrorException e) {
            log.warn("code2session 失败：{}", e.getMessage());
            throw new ServiceException("微信登录失败：" + e.getMessage());
        }
    }

    /**
     * 「手机号快速验证」：动态 code 换**微信背书的手机号**。
     *
     * @param phoneCode 小程序 {@code getPhoneNumber} 回调里的 code
     * @return 手机号（取 purePhoneNumber，即不带区号的 11 位）
     */
    public String resolvePhone(String phoneCode) {
        try {
            WxMaPhoneNumberInfo info = wxMaService.getUserService().getPhoneNumber(phoneCode);
            String phone = info == null ? null : info.getPurePhoneNumber();
            if (phone == null || phone.isBlank()) {
                throw new ServiceException("微信登录失败：手机号快速验证没有返回手机号");
            }
            return phone;
        } catch (WxErrorException e) {
            log.warn("手机号快速验证失败：{}", e.getMessage());
            throw new ServiceException("微信登录失败：" + e.getMessage());
        }
    }

}
