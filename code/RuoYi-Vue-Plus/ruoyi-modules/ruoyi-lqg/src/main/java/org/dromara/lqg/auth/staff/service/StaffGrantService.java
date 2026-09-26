package org.dromara.lqg.auth.staff.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.domain.WxBind;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.dromara.lqg.auth.mapper.WxBindMapper;
import org.dromara.lqg.auth.staff.domain.bo.StaffGrantBo;
import org.dromara.lqg.auth.staff.guard.StaffGrantRules;
import org.dromara.lqg.auth.staff.domain.vo.StaffCheckVo;
import org.dromara.lqg.auth.staff.domain.vo.StaffGrantVo;
import org.dromara.lqg.auth.staff.domain.vo.StaffMemberVo;
import org.dromara.lqg.auth.staff.mapper.StaffAccountMapper;
import org.dromara.system.domain.SysRole;
import org.dromara.system.domain.SysUser;
import org.dromara.system.domain.SysUserRole;
import org.dromara.system.mapper.SysRoleMapper;
import org.dromara.system.mapper.SysUserMapper;
import org.dromara.system.mapper.SysUserRoleMapper;
import org.dromara.system.service.ISysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内部人员按手机号授权 / 改角色 / 重置密码 / 撤销（FLOW:F-AUTH-02，UI:admin.auth.staff）。
 *
 * <p>★ 本类最重要的三条口径（ADR-0003，做反了同一手机号会有两个账号）：
 * <ol>
 *   <li><b>授权 = 找到就改角色，找不到才预建</b>：这个人多半已经用小程序登录过（当时是外部）。
 *       查到既有账号就原地升级 —— 删掉 103、挂上目标内部角色、姓名以本次填的为准、
 *       设置工作台口令、返回 {@code upgraded:true}；<b>不新建账号、不动 {@code t_lqg_wx_bind}</b>。
 *       查不到才新建（{@code user_name='lqg_'+手机号}、{@code user_type='sys_user'}）。</li>
 *   <li><b>撤销 ≠ 删号</b>：角色改回只剩 103、踢掉全部 token；账号、微信绑定、他以前录的样本全留着。
 *       没有外部档案时补建一行 {@code unbound}。</li>
 *   <li><b>不许撤销自己</b>、<b>不许把系统里最后一个 lqg_admin 撤掉</b>：两条都在**写库之前**拦，
 *       免得「返回了错误码却已经把角色删了」。</li>
 * </ol>
 *
 * <p>为什么整段包 {@link DataPermissionHelper#ignore}：{@code sys_user} / {@code sys_user_role}
 * 带 {@code @DataPermission} 与逻辑删除，而本类的调用方是工作台管理员（数据范围本就最大）之外的
 * 场景也有（例如给还没登录的外部账号升级）——不 ignore 会被数据范围静默滤成空结果。
 * 见 AUTH-LOGIN-001 报告的坑 1（现象是「账号建出来了，接口回 401 / 字段全 null」）。
 *
 * @author AUTH-STAFF-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffGrantService {

    /**
     * 内部人员角色 id（doc/lint-profile.yaml 的角色段）
     */
    public static final long ROLE_ID_ADMIN = 101L;

    /**
     * 内部人员角色 id
     */
    public static final long ROLE_ID_INTERNAL = 102L;

    /**
     * 外部人员角色 id
     */
    public static final long ROLE_ID_EXTERNAL = 103L;

    /**
     * 可授予的内部角色键 → 角色 id（白名单：不接受 103，也不接受上游角色）
     */
    private static final Map<String, Long> GRANTABLE_ROLE_IDS = Map.of(
        "lqg_admin", ROLE_ID_ADMIN,
        "lqg_internal", ROLE_ID_INTERNAL
    );

    private static final String INTERNAL_USER_NAME_PREFIX = "lqg_";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final StaffAccountMapper staffAccountMapper;
    private final WxBindMapper wxBindMapper;
    private final ExtProfileMapper extProfileMapper;
    private final ISysRoleService roleService;

    // ── 读 ───────────────────────────────────────────────────────────────────

    /**
     * 只读预检：这个手机号是不是已经有账号（前端弹窗的「将把原账号升级为内部人员」提示）。
     */
    public StaffCheckVo check(String phone) {
        return DataPermissionHelper.ignore(() -> {
            StaffCheckVo vo = new StaffCheckVo();
            SysUser user = selectUserByPhone(phone);
            if (user == null) {
                vo.setExists(false);
                vo.setExternal(false);
                return vo;
            }
            Set<String> roles = roleKeysOf(user.getUserId());
            vo.setExists(true);
            vo.setExternal(roles.contains("lqg_external"));
            vo.setName(user.getNickName());
            return vo;
        });
    }

    /**
     * 列表：带内部角色（101 / 102）的账号 + 是否绑过微信。
     */
    public List<StaffMemberVo> list() {
        return DataPermissionHelper.ignore(() -> {
            List<StaffMemberVo> rows = staffAccountMapper.selectStaffList();
            Map<Long, StaffMemberVo> byUser = new LinkedHashMap<>();
            for (StaffMemberVo row : rows) {
                // 一个账号理论上只有一条内部角色行；真出现两条时 101 优先（列表里不该把管理员显示成内部人员）
                byUser.merge(row.getUserId(), row, (a, b) ->
                    "lqg_admin".equals(a.getRoleKey()) ? a : b);
            }
            List<StaffMemberVo> result = new ArrayList<>(byUser.values());
            for (StaffMemberVo row : result) {
                row.setWxBound(wxBindMapper.exists(new LambdaQueryWrapper<WxBind>()
                    .eq(WxBind::getUserId, row.getUserId())));
            }
            return result;
        });
    }

    // ── 写 ───────────────────────────────────────────────────────────────────

    /**
     * 按手机号授权（FLOW:F-AUTH-02.step1 / step2）。
     */
    @Transactional(rollbackFor = Exception.class)
    public StaffGrantVo grant(StaffGrantBo bo) {
        List<Long> roleIds = grantedRoleIds(bo.getRoleKey());
        String phone = StringUtils.trim(bo.getPhone());
        return DataPermissionHelper.ignore(() -> {
            StaffGrantVo vo = new StaffGrantVo();
            SysUser existing = selectUserByPhone(phone);
            if (existing != null) {
                // ── 升级路径：同一个账号，改角色 / 姓名 / 口令；不新建、不动 t_lqg_wx_bind ──
                upgradeToInternal(existing, roleIds, bo.getName(), bo.getPassword(), bo.getRoleKey());
                vo.setUserId(existing.getUserId());
                vo.setUpgraded(true);
                log.info("按手机号授权：升级既有账号 userId={} → 角色 {}", existing.getUserId(), bo.getRoleKey());
                return vo;
            }
            // ── 预建路径：库里没有这个手机号，建内部账号（首次小程序登录时按手机号自然对上）──
            SysUser created = createInternalUser(phone, roleIds, bo.getName(), bo.getPassword());
            vo.setUserId(created.getUserId());
            vo.setUpgraded(false);
            log.info("按手机号授权：预建内部账号 userId={} → 角色 {}", created.getUserId(), bo.getRoleKey());
            return vo;
        });
    }

    /**
     * 改内部角色（PUT /lqg/auth/staff/{userId}/role）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeRole(Long userId, String roleKey) {
        List<Long> roleIds = grantedRoleIds(roleKey);
        DataPermissionHelper.ignore(() -> {
            SysUser user = requireStaff(userId);
            guardRoleChange(userId, roleKey);
            replaceRoles(userId, roleIds);
            // ★ FIX V33（台账 #23 同类）：改完角色必须踢下线。Sa-Token 的角色 / 菜单权限读的是登录时
            //   缓存在 token 会话里的 LoginUser（SaPermissionImpl），不踢的话「管理员降成内部人员」之后
            //   旧 token 仍带着 lqg_admin，工作台的管理员操作照样能做，直到 token 过期。
            //   手段与 revoke 相同：主手段 StpUtil.logout(loginId) —— 不依赖上游 cleanOnlineUser 的
            //   Redis 扫描快照（Caffeine 5 秒缓存，刚签发的 token 会漏踢）；cleanOnlineUser 只留作兜底。
            //   注：按手机号授权（外部账号原地升级）不踢 —— 升级只会让旧 token 权限更小，而 AUTH-STAFF-001
            //   accept 1 要求升级后旧 token 调 /mp/me 立刻看到 internal（FLOW:F-AUTH-02.step2「下次请求即生效」）。
            StpUtil.logout(user.getUserType() + ":" + userId);
            roleService.cleanOnlineUser(List.of(userId));
            log.info("改角色：userId={} → {}（已踢下线，重新登录后按新角色生效）", userId, roleKey);
            return null;
        });
    }

    /**
     * 重置工作台口令（PUT /lqg/auth/staff/{userId}/reset-pwd）。
     *
     * <p>★ 只对内部账号生效：外部账号（只有 103）必须被拒 —— 外部人员走微信登录，
     * 不给工作台口令（ADR-0003 / accept 第 2 条）。判据是**库里当下**的角色，不是请求里的任何字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String rawPassword) {
        if (StringUtils.isBlank(rawPassword)) {
            throw new ServiceException("新密码不能为空");
        }
        DataPermissionHelper.ignore(() -> {
            SysUser user = requireStaff(userId);
            Set<String> roles = roleKeysOf(userId);
            if (!StaffGrantRules.isInternal(roles)) {
                throw new ServiceException("该账号不是内部人员，不能设置工作台密码");
            }
            SysUser patch = new SysUser();
            patch.setUserId(user.getUserId());
            patch.setPassword(BCrypt.hashpw(rawPassword));
            userMapper.updateById(patch);
            log.info("重置工作台密码：userId={}", userId);
            return null;
        });
    }

    /**
     * 撤销内部授权（DELETE /lqg/auth/staff/{userId}，FLOW:F-AUTH-02.step3）。
     *
     * <p>角色改回只剩 103 → 没有外部档案则补建一行 {@code unbound} → 踢掉该账号全部 token。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long userId) {
        DataPermissionHelper.ignore(() -> {
            // ① 不能撤销自己（写库之前拦）
            if (!StaffGrantRules.canRevokeSelf(LoginHelper.getUserId(), userId)) {
                throw new ServiceException("不能撤销自己的内部授权");
            }
            SysUser user = requireStaff(userId);
            if (!StaffGrantRules.canLoseAdmin(isAdmin(userId), otherAdminCount(userId))) {
                throw new ServiceException("不能撤销系统里最后一个实验室管理员的权限");
            }
            // ③ 角色改回只剩 103（不删账号、不动 t_lqg_wx_bind、不动他录过的样本）
            replaceRoles(userId, List.of(ROLE_ID_EXTERNAL));
            // ④ 没有外部档案则补建一行 unbound（有就原样留着：单位 / 组别 / 核验状态不因撤销而丢）
            if (!extProfileMapper.exists(new LambdaQueryWrapper<ExtProfile>().eq(ExtProfile::getUserId, userId))) {
                ExtProfile profile = new ExtProfile();
                profile.setUserId(userId);
                profile.setBindStatus("unbound");
                extProfileMapper.insert(profile);
            }
            // ⑤ 踢下线：★ 不能只改库 —— accept 会用被撤销人的**旧 token** 打 /mp/me，要求 401。
            //
            //   主手段是 `StpUtil.logout(loginId)`：loginId 的形状是 `userType + ":" + userId`
            //   （上游 LoginUser.getLoginId()），Sa-Token 按 loginId 找到它名下的**全部** token 逐个注销。
            //
            //   ★ 为什么不能只用上游的 roleService.cleanOnlineUser(userIds)：它内部先
            //   `StpUtil.searchTokenValue("", 0, -1, false)`，而 PlusSaTokenDao.searchData 把整个
            //   Redis 扫描结果塞进 Caffeine（expireAfterWrite=5s）——撤销前 5 秒内刚签发的 token
            //   不在那份快照里，于是**漏踢**：被撤销的人拿着旧 token 继续调 /mp/me 照样 200。
            //   这与 ticket 点名的假绿形态「撤了权限的人还能看半小时数据」是同一条，实测可复现
            //   （先触发一次 cleanOnlineUser 把快照坐实，再登录 → 撤销 → 旧 token 仍可用）。
            //   cleanOnlineUser 只留作兜底：万一同一 userId 存在别种 userType 前缀的历史 token。
            StpUtil.logout(user.getUserType() + ":" + userId);
            roleService.cleanOnlineUser(List.of(userId));
            log.info("撤销内部授权：userId={}（角色改回 103、已踢下线）", userId);
            return null;
        });
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private SysUser selectUserByPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            throw new ServiceException("手机号不能为空");
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getPhonenumber, phone)
            .last("LIMIT 1"));
    }

    /**
     * 既有账号原地升级：删掉 103（以及任何其它内部角色）、挂上目标角色、姓名以本次为准、设置口令。
     *
     * <p>{@code user_name} / {@code user_type} 刻意不动：他还是那个登录过小程序的账号。
     */
    private void upgradeToInternal(SysUser user, List<Long> roleIds, String name, String rawPassword, String roleKey) {
        guardRoleChange(user.getUserId(), roleKey);
        replaceRoles(user.getUserId(), roleIds);
        SysUser patch = new SysUser();
        patch.setUserId(user.getUserId());
        patch.setNickName(name);
        patch.setPassword(BCrypt.hashpw(rawPassword));
        userMapper.updateById(patch);
    }

    /**
     * 预建内部账号：{@code user_name='lqg_'+手机号}、{@code user_type='sys_user'}。
     *
     * <p>不建 {@code t_lqg_wx_bind}、不建外部档案 —— 他还没登录过小程序；等首次登录时
     * AUTH-LOGIN-001 的按手机号绑定会自然对上这个账号。
     */
    private SysUser createInternalUser(String phone, List<Long> roleIds, String name, String rawPassword) {
        SysUser user = new SysUser();
        user.setUserName(INTERNAL_USER_NAME_PREFIX + phone);
        user.setNickName(name);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setPhonenumber(phone);
        user.setPassword(BCrypt.hashpw(rawPassword));
        user.setStatus(SystemConstants.NORMAL);
        userMapper.insert(user);
        replaceRoles(user.getUserId(), roleIds);
        return user;
    }

    /**
     * 账号的角色行整体替换成给定角色集合（在这里是「只留一个角色」的语义）。
     */
    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole row = new SysUserRole();
            row.setUserId(userId);
            row.setRoleId(roleId);
            userRoleMapper.insert(row);
        }
    }

    /**
     * 目标账号必须带内部角色（101 / 102）—— 防止把 reset-pwd / role 打到任意外部账号上。
     */
    private SysUser requireStaff(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("账号不存在");
        }
        Set<String> roles = roleKeysOf(userId);
        if (!StaffGrantRules.isInternal(roles)) {
            throw new ServiceException("该账号不是内部人员");
        }
        return user;
    }

    /**
     * 改角色前的守卫：目标当前是 lqg_admin 而新角色不是 → 系统里必须还有别的 lqg_admin。
     *
     * <p>在**写库之前**抛（不允许「先落盘再报错」——accept 会紧跟一条库内断言）。
     */
    private void guardRoleChange(Long userId, String newRoleKey) {
        if (!"lqg_admin".equals(newRoleKey)
            && !StaffGrantRules.canLoseAdmin(isAdmin(userId), otherAdminCount(userId))) {
            throw new ServiceException("不能把系统里最后一个实验室管理员改成普通内部人员");
        }
    }

    /**
     * 目标当前是否带 lqg_admin。
     */
    private boolean isAdmin(Long userId) {
        return roleKeysOf(userId).contains("lqg_admin");
    }

    /**
     * 库里除目标外还带 lqg_admin 的账号数。
     */
    private long otherAdminCount(Long userId) {
        Long others = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getRoleId, ROLE_ID_ADMIN)
            .ne(SysUserRole::getUserId, userId));
        return others == null ? 0L : others;
    }

    private Long requireGrantableRole(String roleKey) {
        Long roleId = GRANTABLE_ROLE_IDS.get(roleKey);
        if (roleId == null) {
            throw new ServiceException("只能授内部人员（lqg_internal）或实验室管理员（lqg_admin）");
        }
        // 角色行必须在库里且未删、未停用 —— 否则会授出一个永远生效不了的角色
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || SystemConstants.DISABLE.equals(role.getStatus())) {
            throw new ServiceException("角色不存在或已停用：" + roleKey);
        }
        return roleId;
    }

    /**
     * 本次授权 / 改角色实际要挂的角色 id 集合。
     *
     * <p>★ {@code lqg_admin} 同时挂 {@code lqg_internal}（ticket §2.1）：管理员在小程序里也是内部人员，
     * 否则同一人在网页端是管理员、在小程序里被当外部账号。角色键 → id 的展开在
     * {@link StaffGrantRules#grantedRoleKeys(String)} 里（纯函数，有契约测试）。
     */
    private List<Long> grantedRoleIds(String roleKey) {
        List<Long> ids = new ArrayList<>();
        for (String key : StaffGrantRules.grantedRoleKeys(roleKey)) {
            ids.add(requireGrantableRole(key));
        }
        return ids;
    }

    private Set<String> roleKeysOf(Long userId) {
        Set<String> keys = roleService.selectRolePermissionByUserId(userId);
        return keys == null ? Set.of() : keys;
    }

}
