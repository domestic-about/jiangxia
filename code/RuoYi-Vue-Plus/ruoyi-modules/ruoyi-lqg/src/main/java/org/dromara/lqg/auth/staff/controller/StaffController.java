package org.dromara.lqg.auth.staff.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.staff.domain.bo.StaffGrantBo;
import org.dromara.lqg.auth.staff.domain.bo.StaffResetPwdBo;
import org.dromara.lqg.auth.staff.domain.bo.StaffRoleBo;
import org.dromara.lqg.auth.staff.domain.vo.StaffCheckVo;
import org.dromara.lqg.auth.staff.domain.vo.StaffGrantVo;
import org.dromara.lqg.auth.staff.domain.vo.StaffMemberVo;
import org.dromara.lqg.auth.staff.service.StaffGrantService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部人员授权（doc/api-contract.md「AUTH」一节，UI:admin.auth.staff）。
 *
 * <pre>
 * GET    /lqg/auth/staff                    列表（带内部角色的账号）
 * GET    /lqg/auth/staff/check?phone=       只读预检（弹窗的「将把原账号升级」提示）
 * POST   /lqg/auth/staff                    按手机号授权（已有账号 → 升级；没有 → 预建）
 * PUT    /lqg/auth/staff/{userId}/role      改内部角色
 * PUT    /lqg/auth/staff/{userId}/reset-pwd 重置工作台密码（**只对内部账号**）
 * DELETE /lqg/auth/staff/{userId}           撤销内部授权（改回 103 + 踢下线，不删号）
 * </pre>
 *
 * <p>权限串与 {@code V202609210920__AUTH-STAFF-001-menu.sql} 里的菜单按钮逐字一致，
 * 且只授给 101（lqg_admin）。
 *
 * @author AUTH-STAFF-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/auth/staff")
public class StaffController {

    private final StaffGrantService staffGrantService;

    /**
     * 列表：姓名、手机号、角色、是否已绑定微信。
     */
    @SaCheckPermission("lqg:auth:staff:list")
    @GetMapping
    public R<List<StaffMemberVo>> list() {
        return R.ok(staffGrantService.list());
    }

    /**
     * 只读预检：该手机号是否已有账号 / 是否外部账号（前端弹窗提示用）。
     *
     * <p>刻意不做权限之外的副作用：不写库、不发消息。
     */
    @SaCheckPermission("lqg:auth:staff:list")
    @GetMapping("/check")
    public R<StaffCheckVo> check(@RequestParam("phone") String phone) {
        return R.ok(staffGrantService.check(phone));
    }

    /**
     * 按手机号授权。
     */
    @SaCheckPermission("lqg:auth:staff:grant")
    @PostMapping
    public R<StaffGrantVo> grant(@Valid @RequestBody StaffGrantBo bo) {
        return R.ok(staffGrantService.grant(bo));
    }

    /**
     * 改内部角色。
     */
    @SaCheckPermission("lqg:auth:staff:edit")
    @PutMapping("/{userId}/role")
    public R<Void> changeRole(@PathVariable Long userId, @Valid @RequestBody StaffRoleBo bo) {
        staffGrantService.changeRole(userId, bo.getRoleKey());
        return R.ok();
    }

    /**
     * 重置工作台密码（只对内部账号放行，外部账号 → 业务码 500）。
     *
     * <p>★ 拒绝**只走业务码，不动 HTTP 状态码**：上游 {@code GlobalExceptionHandler} 对
     * {@code ServiceException} 的约定就是 HTTP 200 + {@code code:500}，前端 {@code utils/request.ts}
     * 正是按 {@code code} 分支把 {@code msg} 原样弹给用户的。若在这里额外把 HTTP 码改成 500，
     * axios 会落进 error 分支，前端只能显示「系统接口500异常」——真实原因（「该账号不是内部人员」）
     * 反而被吞掉。验收侧的 {@code api.sh --bizcode} 读的也是响应体里的业务码，不是 HTTP 码。
     */
    @SaCheckPermission("lqg:auth:staff:resetPwd")
    @PutMapping("/{userId}/reset-pwd")
    public R<Void> resetPwd(@PathVariable Long userId, @Valid @RequestBody StaffResetPwdBo bo) {
        staffGrantService.resetPassword(userId, bo.getPassword());
        return R.ok();
    }

    /**
     * 撤销内部授权（不删号：角色改回 103 + 踢下线；拒绝同样只走业务码）。
     */
    @SaCheckPermission("lqg:auth:staff:revoke")
    @DeleteMapping("/{userId}")
    public R<Void> revoke(@PathVariable Long userId) {
        staffGrantService.revoke(userId);
        return R.ok();
    }

}
