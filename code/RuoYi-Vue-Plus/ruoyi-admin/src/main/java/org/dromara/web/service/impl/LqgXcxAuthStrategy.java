package org.dromara.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.domain.model.XcxLoginUser;
import org.dromara.common.core.utils.ValidatorUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.domain.WxIdentity;
import org.dromara.lqg.auth.domain.WxLoginCredentials;
import org.dromara.lqg.auth.service.WxAccountBindService;
import org.dromara.lqg.auth.service.WxIdentityResolver;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.springframework.stereotype.Service;

/**
 * 小程序 xcx 认证策略（grantType = {@code xcx}）—— **框架接线层**。
 *
 * <p>为什么这个类在 ruoyi-admin 而不在 ruoyi-lqg：{@code IAuthStrategy} 与 {@code LoginVo}
 * 本身就是 ruoyi-admin 里的类型（上游把它们放在 web 包），而 ruoyi-admin 依赖 ruoyi-lqg，
 * 反向依赖会成环。所以按职责切开：
 * <ul>
 *   <li><b>本类</b>只做框架接线 —— 解析入参、签发 token、返回 {@code LoginVo}；</li>
 *   <li><b>业务规则全在 ruoyi-lqg</b>：{@link WxIdentityResolver}（真实 / mock 两条路径与
 *       ADR-0008 的开关）、{@link WxAccountBindService}（按手机号绑定 / 自动建外部账号）。</li>
 * </ul>
 *
 * <p>★ 本类顶替上游 {@code XcxAuthStrategy} 空壳：上游那个 {@code loadUserByOpenid} 里只有
 * {@code // todo 自行实现}，连 {@code SysUserVo} 都是 {@code new} 出来的空对象。
 * {@code IAuthStrategy.java} 按 {@code grantType + "AuthStrategy"} 取 bean，所以本类沿用同一个
 * bean 名（{@code xcxAuthStrategy}）；上游空壳已删除以免同名 bean 冲突 —— 见完工报告。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
@Service("xcx" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class LqgXcxAuthStrategy implements IAuthStrategy {

    private final WxIdentityResolver wxIdentityResolver;
    private final WxAccountBindService wxAccountBindService;
    private final SysLoginService loginService;

    @Override
    public LoginVo login(String body, SysClientVo client) {
        WxLoginCredentials credentials = JsonUtils.parseObject(body, WxLoginCredentials.class);
        ValidatorUtils.validate(credentials);
        // ★ 从这里往下，请求体里除 xcxCode / phoneCode 以外的任何字段都不再被读到：
        //   内外部判定只认微信返回的手机号（ADR-0003 / FLOW:F-AUTH-01.step2）。
        WxIdentity identity = wxIdentityResolver.resolve(credentials.getXcxCode(), credentials.getPhoneCode());
        // ★ 整段包 DataPermissionHelper.ignore：登录这一刻**还没有 token**（LoginHelper.login 在后面），
        //   而 SysUserMapper 的 selectXxxById / updateById 与角色、权限查询都带 @DataPermission——
        //   PlusDataPermissionHandler 会调 LoginHelper.getLoginUser() 取当前用户，Sa-Token 随即抛
        //   NotLoginException「token 已被冻结」，被 MybatisExceptionHandler 包成 401。
        //   现象极具迷惑性：账号、绑定行、外部档案都建出来了，接口却回 401。
        //   RuoYi 自己在 SysLoginService.recordLoginInfo 里用的是同一个 helper。
        XcxLoginUser loginUser = new XcxLoginUser();
        DataPermissionHelper.ignore(() -> {
            // 查号 / 建号 / 绑定 / 更新登录时间（业务规则全在 ruoyi-lqg，一个事务）
            SysUserVo user = wxAccountBindService.bindOrCreate(identity);
            // 构建登录用户：走框架自己的 buildLoginUser，角色 / 权限 / 部门名称与其它登录方式同一份口径
            LoginUser built = loginService.buildLoginUser(user);
            BeanUtil.copyProperties(built, loginUser);
            return null;
        });
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        loginUser.setOpenid(identity.getOpenid());

        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType(client.getDeviceType());
        // 不同用户体系可以给不同 token 时效，走 sys_client 里配的值
        model.setTimeout(client.getTimeout());
        model.setActiveTimeout(client.getActiveTimeout());
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        loginVo.setOpenid(identity.getOpenid());
        return loginVo;
    }

}
