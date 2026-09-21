package org.dromara.lqg.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.domain.WxBind;
import org.dromara.lqg.auth.domain.WxIdentity;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.dromara.lqg.auth.mapper.WxBindMapper;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.service.ISysPermissionService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 按手机号绑定或自动建外部账号（FLOW:F-AUTH-01.step3）。
 *
 * <p>★ 本项目的账号模型只有三条口径，本类就是它们的代码化（ADR-0003）：
 * <ol>
 *   <li><b>身份只认微信返回的手机号</b>——入参是 {@link WxIdentity}（openid + 微信背书的手机号），
 *       请求体里的任何身份字段在 {@code WxLoginCredentials} 反序列化那一步就已经被丢掉了；</li>
 *   <li><b>同一手机号永远只对应一个账号</b>——先按手机号查 {@code sys_user}，
 *       查到就绑 openid，<b>查不到才新建</b>。绝不按 openid 查不到就新建：
 *       同一个人换个微信号会变成两个账号；</li>
 *   <li><b>外部不设准入</b>——查不到就自动建外部账号（角色 {@code lqg_external}）
 *       + 一行 {@code bind_status='unbound'} 的外部档案，不需要任何人审批。</li>
 * </ol>
 *
 * <p>并发：两次首登同时到达时用 Redisson 按手机号加锁（key = {@code lqg:auth:wx-bind:<手机号>}），
 * 后到的那个进临界区时前一个已提交，重查就能命中同一账号 —— **建不出两个账号**。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAccountBindService {

    /**
     * 外部人员角色 id（doc/lint-profile.yaml 的角色段：101 管理员 / 102 内部 / 103 外部）
     */
    public static final long ROLE_ID_EXTERNAL = 103L;

    /**
     * 手机号首登锁：等锁 10s、租约 30s（临界区只有几条 insert，够用且不会锁死）
     */
    private static final long LOCK_WAIT_SECONDS = 10L;

    private static final long LOCK_LEASE_SECONDS = 30L;

    /**
     * 微信登录自动创建的用户名前缀（SSOT 里的既有约定：wx_<手机号>）
     */
    private static final String WX_USER_NAME_PREFIX = "wx_";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final WxBindMapper wxBindMapper;
    private final ExtProfileMapper extProfileMapper;
    private final ISysPermissionService permissionService;
    private final RedissonClient redissonClient;

    /**
     * 自身的代理引用：让 {@link #bindOrCreateLocked} 真的走 Spring 事务代理。
     *
     * <p>{@code this.bindOrCreateLocked(...)} 是**自调用**，会绕过事务代理 —— {@code @Transactional}
     * 就成装饰品（账号、角色、档案、绑定行分属四个事务，中途失败会留下半个账号）。
     * {@code ObjectProvider} 是懒取的，不会在构造期形成循环依赖。
     */
    private final ObjectProvider<WxAccountBindService> selfProvider;

    /**
     * 绑定或建号，返回库里的账号行。
     *
     * <p>返回 {@code SysUserVo}（ruoyi-system 的类型）而不是框架的 {@code LoginUser}：
     * 后者由 ruoyi-admin 的 {@code SysLoginService.buildLoginUser} 构建，而 ruoyi-admin 依赖本模块
     * ——本模块反过来引它会成环（ADR-0001 的单向依赖）。组装 {@code LoginUser} 留在接线层做。
     *
     * @param identity 微信换回来的 openid + 手机号
     * @return 该手机号对应的账号（新建或既有）
     */
    public SysUserVo bindOrCreate(WxIdentity identity) {
        String phone = identity.getPhone();
        RLock lock = redissonClient.getLock("lqg:auth:wx-bind:" + phone);
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("登录繁忙，请重试");
        }
        if (!locked) {
            throw new ServiceException("登录繁忙，请重试");
        }
        try {
            // 走代理（selfProvider.get() 拿到的是事务代理），不能写 this.bindOrCreateLocked
            return selfProvider.getObject().bindOrCreateLocked(identity);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 临界区（同一时刻同一手机号只有一个线程）：查 → 建 / 绑 → 更新登录时间。
     * 整段一个事务，要么账号 + 角色 + 档案 + 绑定行都在，要么都不在。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUserVo bindOrCreateLocked(WxIdentity identity) {
        String phone = identity.getPhone();
        String openid = identity.getOpenid();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getPhonenumber, phone));
        if (user == null) {
            user = createExternalUser(phone);
        } else if (SystemConstants.DISABLE.equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", phone);
            throw new ServiceException("user.blocked", phone);
        }
        upsertBind(user.getUserId(), openid, identity.getUnionid(), phone);
        touchLoginTime(user.getUserId());
        return toVo(user);
    }

    /**
     * 新建外部账号：{@code sys_user}（{@code user_type='app_user'}、口令为空 = 不能走账号密码登录）
     * + 角色 103 + 一行 {@code unbound} 的外部档案。
     */
    private SysUser createExternalUser(String phone) {
        SysUser user = new SysUser();
        user.setUserName(WX_USER_NAME_PREFIX + phone);
        user.setNickName(WX_USER_NAME_PREFIX + phone);
        user.setUserType(UserType.APP_USER.getUserType());
        user.setPhonenumber(phone);
        // 口令留空：外部人员只能走微信登录（ADR-0003 的账号模型里没有外部账号密码这一说）
        user.setPassword("");
        user.setStatus(SystemConstants.NORMAL);
        // 审计字段：本类跑在 LoginHelper.login **之前**，取不到当前登录用户；不显式写的话
        // MyBatis-Plus 的自动填充会去取 token session 并在无 token 时抛错。
        // 0 表示「系统自动创建」（框架自己的 registerUser 也用 0）。
        user.setCreateBy(0L);
        user.setUpdateBy(0L);
        userMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(ROLE_ID_EXTERNAL);
        userRoleMapper.insert(userRole);

        ExtProfile profile = new ExtProfile();
        profile.setUserId(user.getUserId());
        profile.setBindStatus("unbound");
        profile.setCreateBy(0L);
        profile.setUpdateBy(0L);
        extProfileMapper.insert(profile);

        log.info("首次登录自动创建外部账号 userId={}（角色 {}，档案 bind_status=unbound）",
            user.getUserId(), ROLE_ID_EXTERNAL);
        return user;
    }

    /**
     * 绑定行：该 openid 不在 {@code t_lqg_wx_bind} 里才插一行（软删过的行不挡重建，
     * 靠的是 {@code uk_wx_openid … WHERE del_flag='0'} 这个部分唯一索引）。
     */
    private void upsertBind(Long userId, String openid, String unionid, String phone) {
        WxBind existing = wxBindMapper.selectOne(new LambdaQueryWrapper<WxBind>()
            .eq(WxBind::getOpenid, openid));
        if (existing == null) {
            WxBind bind = new WxBind();
            bind.setUserId(userId);
            bind.setOpenid(openid);
            bind.setUnionid(unionid);
            bind.setPhone(phone);
            bind.setLastLoginTime(new Date());
            bind.setCreateBy(userId);
            bind.setUpdateBy(userId);
            wxBindMapper.insert(bind);
            return;
        }
        if (!userId.equals(existing.getUserId())) {
            // 同一 openid 先绑了 A、现在拿着 B 的手机号来登：宁可拒绝，也不悄悄改绑
            log.warn("openid {} 已绑定 userId={}，本次登录手机号对应 userId={}，拒绝改绑",
                openid, existing.getUserId(), userId);
            throw new ServiceException("该微信号已绑定其他手机号，请联系实验室");
        }
        WxBind patch = new WxBind();
        patch.setId(existing.getId());
        patch.setPhone(phone);
        patch.setUnionid(unionid);
        patch.setLastLoginTime(new Date());
        patch.setUpdateBy(userId);
        wxBindMapper.updateById(patch);
    }

    /**
     * 更新 {@code sys_user.login_date}（FLOW:F-AUTH-01.step4 的写入项）。
     * 显式写 update_by：理由同 {@link #createExternalUser}（此处还没有 token session）。
     */
    private void touchLoginTime(Long userId) {
        SysUser patch = new SysUser();
        patch.setUserId(userId);
        patch.setLoginDate(new Date());
        patch.setUpdateBy(userId);
        userMapper.updateById(patch);
    }

    /**
     * 把 {@code sys_user} 实体转成框架的 {@code SysUserVo}。
     *
     * <p>不引 MapStruct：这里只用到 8 个字段，而 {@code SysUserVo} 是上游类，加 {@code @AutoMapper}
     * 会把上游类卷进编译期生成（gotchas §9.1 的坑），不值得。
     */
    private SysUserVo toVo(SysUser user) {
        SysUserVo vo = new SysUserVo();
        vo.setUserId(user.getUserId());
        vo.setUserName(user.getUserName());
        vo.setNickName(user.getNickName());
        vo.setUserType(user.getUserType());
        vo.setPhonenumber(user.getPhonenumber());
        vo.setStatus(user.getStatus());
        vo.setDeptId(user.getDeptId());
        return vo;
    }

    /**
     * 当前用户角色键集合（给 {@code /mp/me} 判内外部用）。
     */
    public Set<String> roleKeysOf(Long userId) {
        return permissionService.getRolePermission(userId);
    }

}
