package org.dromara.lqg.auth.staff.domain.vo;

import lombok.Data;

/**
 * 只读预检结果（GET /lqg/auth/staff/check?phone=…）。
 *
 * <p>授权弹窗在提交前调它：这个手机号如果已经有外部账号，就把「将把原账号升级为内部人员」这句
 * 提示显示出来（UI:admin.auth.staff）。只读、不写库、不需要权限串以外的任何东西。
 *
 * @author AUTH-STAFF-001
 */
@Data
public class StaffCheckVo {

    /**
     * 该手机号在 sys_user 里是否已有账号（true = 提交时会走升级路径）
     */
    private Boolean exists;

    /**
     * 既有账号是否带外部角色 lqg_external（true = 「已登录过小程序」的那种账号）
     */
    private Boolean external;

    /**
     * 既有账号姓名（sys_user.nick_name，没账号时为 null），给弹窗回显用
     */
    private String name;

}
