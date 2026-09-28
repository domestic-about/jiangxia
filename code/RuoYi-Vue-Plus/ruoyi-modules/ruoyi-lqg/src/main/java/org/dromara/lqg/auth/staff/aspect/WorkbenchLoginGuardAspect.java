package org.dromara.lqg.auth.staff.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.model.PasswordLoginBody;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.staff.guard.StaffGrantRules;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.service.ISysRoleService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工作台只让内部进（FLOW:F-AUTH-02.step4 / ADR-0003 第 3 条）。
 *
 * <p>★ 为什么用 AOP 而不是改密码登录策略：上游 {@code PasswordAuthStrategy} 是**框架代码**，
 * 本项目不动它（ADR-0001 的边界 + 上游升级面）。这里在**本模块**里对
 * {@code org.dromara.web.service.impl.PasswordAuthStrategy.login(..)} 做后置校验：
 * 口令先由上游正常校验（错口令仍然走「用户名或密码错误」分支），校验通过、token 也已签发之后，
 * 再按角色决定这次登录是否成立 —— 不含 {@code lqg_internal} / {@code lqg_admin}（以及上游
 * {@code superadmin}）的账号，一律以「无权登录工作台」拒绝。
 *
 * <p>★ 这是**后端行为**（不是「登录成功后前端没菜单」）：小程序外部账号即便知道工作台口令，
 * 登录接口本身就不发 token。
 *
 * <p>★ 为什么要 {@link DataPermissionHelper#ignore}：本切面在登录事务里读 {@code sys_user}，
 * 而 {@code sys_user} 带 {@code @DataPermission}；不 ignore 会在无 token / 非管理员上下文里被
 * 数据范围静默滤成空集，表现为「外部账号照样能登进来」（漏拦）——比报错更难查。
 *
 * @author AUTH-STAFF-001
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class WorkbenchLoginGuardAspect {

    private final SysUserMapper userMapper;
    private final ISysRoleService roleService;

    /**
     * 密码登录返回之后（= 口令已通过、token 已签发）再判一次角色。
     *
     * <p>用 {@code @AfterReturning} 而不是 {@code @Around}：口令错的分支由上游抛异常，
     * 本方法根本不会执行 —— 于是「密码错」与「无权」两个分支天然分开。
     */
    @AfterReturning("execution(* org.dromara.web.service.impl.PasswordAuthStrategy.login(..)) && args(body, client)")
    public void afterPasswordLogin(JoinPoint joinPoint, String body, Object client) {
        PasswordLoginBody loginBody = JsonUtils.parseObject(body, PasswordLoginBody.class);
        if (loginBody == null || StringUtils.isBlank(loginBody.getUsername())) {
            return;
        }
        String username = loginBody.getUsername();
        SysUserVo user = DataPermissionHelper.ignore(() -> userMapper.selectVoOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserName, username)));
        if (user == null) {
            return;
        }
        // 上游超级管理员（user_id=1）永远能进工作台
        if (SystemConstants.SUPER_ADMIN_ID.equals(user.getUserId())) {
            return;
        }
        Set<String> roleKeys = DataPermissionHelper.ignore(
            () -> roleService.selectRolePermissionByUserId(user.getUserId()));
        boolean internal = roleKeys != null
            && roleKeys.stream().anyMatch(StaffGrantRules.INTERNAL_ROLE_KEYS::contains);
        if (!internal) {
            log.warn("工作台登录被拒：账号 {}（userId={}）不含内部角色 {}", username, user.getUserId(), roleKeys);
            // msg 里必须有「无权」二字：前端靠它把「不是密码错」说清楚，accept 也用它区分两个分支
            throw new ServiceException("该账号无权登录工作台，请使用小程序登录");
        }
    }

}
