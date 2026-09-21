package org.dromara.lqg.auth.staff.domain.vo;

import lombok.Data;

/**
 * 内部人员授权列表的一行（UI:admin.auth.staff 的列：姓名、手机号、角色、是否已绑定微信）。
 *
 * @author AUTH-STAFF-001
 */
@Data
public class StaffMemberVo {

    /**
     * 账号 id
     */
    private Long userId;

    /**
     * 姓名（sys_user.nick_name）
     */
    private String name;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 内部角色键：lqg_admin / lqg_internal
     */
    private String roleKey;

    /**
     * 内部角色名：实验室管理员 / 内部人员
     */
    private String roleName;

    /**
     * 是否已绑定微信（该账号在 t_lqg_wx_bind 有没有未删的行）—— 读时算，不落列
     */
    private Boolean wxBound;

}
