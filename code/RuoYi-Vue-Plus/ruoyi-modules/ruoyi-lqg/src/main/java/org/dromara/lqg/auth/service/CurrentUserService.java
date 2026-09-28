package org.dromara.lqg.auth.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.domain.vo.MPUserVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * {@code GET /mp/me} 的取数逻辑（doc/api-contract.md「AUTH」一节）。
 *
 * <p>手机号与姓名**回库取**而不是从 token 里取：框架的 {@code LoginUser} 没有 phonenumber 字段，
 * 而且手机号属于「库里的现状」，读运行期值才不会与 {@code sys_user} 漂开。
 *
 * @author AUTH-LOGIN-001
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /**
     * 内部身份角色键：带其中任意一个 → {@code internal}，否则 {@code external}
     */
    private static final Set<String> INTERNAL_ROLE_KEYS = Set.of("lqg_internal", "lqg_admin");

    private final ExtProfileQueryService extProfileQueryService;
    private final SysUserMapper userMapper;
    private final WxAccountBindService wxAccountBindService;

    /**
     * 当前登录者的身份视图。
     *
     * <p>identity **只由账号角色决定**：不看 {@code user_type}（seed 里内部人员是 {@code sys_user}、
     * 外部是 {@code app_user}，但那是巧合不是判据；角色才是 ADR-0003 的口径），
     * 更不看请求体里传的字段。
     */
    public MPUserVo currentUser() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        MPUserVo vo = new MPUserVo();
        if (loginUser == null) {
            return vo;
        }
        Long userId = loginUser.getUserId();
        vo.setUserId(userId);
        vo.setName(loginUser.getNickname());
        // 包 DataPermissionHelper.ignore：「读自己这一行」不该被数据权限过滤。
        // SysUserMapper#selectVoById 带 @DataPermission，不忽略的话部门数据范围会把本行滤掉
        // （实测现象：name 只剩 token 里的昵称、phoneMasked 与 ext 全 null，接口却回 200）。
        DataPermissionHelper.ignore(() -> {
            SysUserVo user = userMapper.selectVoById(userId);
            if (user != null) {
                vo.setName(user.getNickName());
                vo.setPhoneMasked(maskPhone(user.getPhonenumber()));
            }
            return null;
        });
        Set<String> roles = wxAccountBindService.roleKeysOf(userId);
        boolean internal = roles.stream().anyMatch(INTERNAL_ROLE_KEYS::contains);
        vo.setIdentity(internal ? "internal" : "external");
        if (!internal) {
            vo.setExt(extProfileQueryService.profileOf(userId));
        }
        return vo;
    }

    /**
     * 手机号掩码：中间四位换成 {@code ****}。
     *
     * <p>{@code 13800000099 → 138****0099}；非 11 位按「前 3 后 4」兜底，
     * 短于 7 位的整串打码（宁可比必要的更严，也不漏号）。
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() == 11) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        if (value.length() > 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        return "*".repeat(value.length());
    }

}
