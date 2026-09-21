package org.dromara.lqg.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.auth.domain.WxIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 把「登录请求里的两个 code」换成 {@link WxIdentity}（openid + 微信背书的手机号）
 * —— FLOW:F-AUTH-01.step1 / step2 的两种实现。
 *
 * <p>★ 真实路径与 mock 路径**只在「怎么拿到这两个值」上有差别**：
 * <pre>
 *   mock（dev / test，ADR-0008）  xcxCode="mock:&lt;key&gt;"     → openid="mock-openid-" + &lt;key&gt;
 *                                phoneCode="mock:&lt;手机号&gt;" → phone = &lt;手机号&gt;
 *   真实                         xcxCode  → code2session          → openid / unionid
 *                                phoneCode → 手机号快速验证      → 微信背书的手机号
 * </pre>
 * 两条路径都收敛到同一个 {@link WxIdentity}，之后由 {@link WxAccountBindService} 走**同一段**
 * 查号 / 建号 / 绑定代码 —— 不在绑定逻辑上出现第二份实现。
 *
 * <p>放在 ruoyi-lqg 而不是 ruoyi-admin：这是账号模型的口径（ADR-0003 + ADR-0008），
 * 是业务规则，不是框架接线。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxIdentityResolver {

    /**
     * mock 前缀：{@code mock:}。doc/verify/api.sh 的 {@code --as <身份>} 依赖这个约定。
     */
    private static final String MOCK_PREFIX = "mock:";

    /**
     * mock openid 前缀：{@code mock-openid-<key>}，与 doc/verify/seed/02-wx-bind-ext-profile.sql 里的
     * {@code mock-openid-staff} / {@code mock-openid-extA} 逐字对应。
     */
    private static final String MOCK_OPENID_PREFIX = "mock-openid-";

    /**
     * 小程序 mock 登录开关（ADR-0008）。缺省 false：prod 配置文件里不出现这个键；
     * 运行期合法性由 {@code MockLoginGuard} 在启动时把关（prod 打开则拒绝启动）。
     */
    @Value("${lqg.auth.mock-login:false}")
    private boolean mockLogin;

    private final WeChatMiniAppService weChatMiniAppService;

    /**
     * 解析登录身份。
     *
     * <p>★ 只有 {@code xcxCode} / {@code phoneCode} 会被读到：调用方从
     * {@code WxLoginCredentials} 里取，那个类**压根没有** identity / role / isInternal 之类的属性，
     * 请求体里夹带的身份字段在反序列化时就被丢掉了（ADR-0003）。
     *
     * @param xcxCode   wx.login 的 code，或 mock:&lt;key&gt;
     * @param phoneCode 手机号快速验证的 code，或 mock:&lt;手机号&gt;
     * @return openid + 微信背书的手机号
     */
    public WxIdentity resolve(String xcxCode, String phoneCode) {
        if (isMock(xcxCode)) {
            return mockIdentity(xcxCode, phoneCode);
        }
        String phone = weChatMiniAppService.resolvePhone(phoneCode);
        WxIdentity identity = weChatMiniAppService.resolveOpenid(xcxCode);
        identity.setPhone(phone);
        return identity;
    }

    /**
     * 是否走 mock：**既要求 xcxCode 以 {@code mock:} 开头，也要求开关打开**。
     * 开关关着时 mock 串会被当成普通 code 拿去 code2session，失败即失败 —— 不会静默降级成后门。
     */
    private boolean isMock(String xcxCode) {
        return mockLogin && xcxCode != null && xcxCode.startsWith(MOCK_PREFIX);
    }

    /**
     * mock 身份：openid = {@code mock-openid-} + 冒号后的 key，手机号 = phoneCode 冒号后的串。
     */
    private WxIdentity mockIdentity(String xcxCode, String phoneCode) {
        String key = xcxCode.substring(MOCK_PREFIX.length()).trim();
        if (key.isEmpty()) {
            throw new ServiceException("mock 登录参数不合法：xcxCode 形如 mock:<key>");
        }
        String phone = StringUtils.isBlank(phoneCode) ? null : phoneCode.trim();
        if (phone != null && phone.startsWith(MOCK_PREFIX)) {
            phone = phone.substring(MOCK_PREFIX.length()).trim();
        }
        if (StringUtils.isBlank(phone)) {
            throw new ServiceException("mock 登录参数不合法：phoneCode 形如 mock:<手机号>");
        }
        log.info("mock 登录：key={} phone={}（仅 dev / test，ADR-0008）", key, phone);
        return new WxIdentity(MOCK_OPENID_PREFIX + key, phone, null);
    }

}
