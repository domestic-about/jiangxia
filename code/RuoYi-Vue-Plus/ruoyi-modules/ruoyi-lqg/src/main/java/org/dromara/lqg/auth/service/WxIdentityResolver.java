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
 *                                phoneCode="mock:&lt;手机号&gt;" → **只做形状校验**；手机号取服务端预置清单
 *                                                            （{@code lqg.auth.mock-identities}，2026-09-28 起）
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

    /**
     * mock 允许的**预置身份清单**：{@code key:手机号} 逗号分隔（dev / test 各配一份，prod 不出现这个键）。
     *
     * <p>★ 2026-09-28 加（台账 #346，Kevin 要求测试体验版保留登录页快捷入口的同一次改动）：
     * 原来 mock 登录**采信调用方传的手机号**，而 {@link WxAccountBindService} 是按手机号复用已有账号的
     * —— 于是「报出种子里内部人员的号码」就能拿到 internal 身份；又因为工作台鉴权是**角色制**（不是 client 制），
     * 同一个 token 还能读写工作台业务数据。测试环境公网可达，这等于一个开放的身份入口。
     *
     * <p>现在改成：mock **只认这份清单里的 key**，且**用哪个手机号由服务端从清单取**（不再看 phoneCode 的值）。
     * 空清单 = 谁都不许（fail-closed），而不是「谁都行」。
     * 清单与 {@code code/miniapp/src/api/mock-seeds.ts} 的面板身份、
     * {@code doc/verify/seed/02-wx-bind-ext-profile.sql} 的绑定逐字对应。
     */
    @Value("${lqg.auth.mock-identities:}")
    private String mockIdentities;

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
     * mock 身份：openid = {@code mock-openid-} + 冒号后的 key；手机号**由服务端预置清单决定**。
     *
     * <p>调用方仍要带 {@code phoneCode}（形状校验，与面板一致），但它的**值不再被采信** ——
     * 这是 2026-09-28 修 #346 的关键一步（见 {@link #mockIdentities}）。
     */
    private WxIdentity mockIdentity(String xcxCode, String phoneCode) {
        String key = xcxCode.substring(MOCK_PREFIX.length()).trim();
        if (key.isEmpty()) {
            throw new ServiceException("mock 登录参数不合法：xcxCode 形如 mock:<key>");
        }
        if (StringUtils.isBlank(phoneCode)) {
            throw new ServiceException("mock 登录参数不合法：phoneCode 形如 mock:<手机号>");
        }
        String phone = mockPhoneFor(mockIdentities, key);
        log.info("mock 登录：key={} phone={}（取服务端预置清单；仅 dev / test，ADR-0008）", key, phone);
        return new WxIdentity(MOCK_OPENID_PREFIX + key, phone, null);
    }

    /**
     * 从预置清单里取该 mock key 对应的手机号（纯函数，便于单测）。
     *
     * <p>清单格式 {@code key:手机号,key:手机号}。三条口径：
     * <ul>
     *   <li>清单为空 → 拒绝（fail-closed）。**不要**退化成「不校验」，那正是要修的那个洞。</li>
     *   <li>key 不在清单里 → 拒绝，并把 key 带在报错里。</li>
     *   <li>命中 → 返回**清单里的**手机号；调用方传什么都不影响。</li>
     * </ul>
     *
     * @param rawIdentities 配置值 {@code lqg.auth.mock-identities}
     * @param key           调用方给的 mock key（{@code mock:} 之后那段）
     * @return 该身份的手机号
     */
    static String mockPhoneFor(String rawIdentities, String key) {
        if (StringUtils.isBlank(rawIdentities)) {
            throw new ServiceException(
                "mock 登录未配置允许的身份清单（lqg.auth.mock-identities）—— 为安全起见拒绝本次 mock 登录");
        }
        for (String pair : rawIdentities.split(",")) {
            int at = pair.indexOf(':');
            if (at <= 0) {
                continue;
            }
            if (pair.substring(0, at).trim().equals(key)) {
                String phone = pair.substring(at + 1).trim();
                if (StringUtils.isNotBlank(phone)) {
                    return phone;
                }
            }
        }
        throw new ServiceException("mock 身份「" + key + "」不在允许清单里（lqg.auth.mock-identities）");
    }

}
