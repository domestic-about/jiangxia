package org.dromara.lqg.auth.staff.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 按手机号授权的入参（doc/api-contract.md：POST /lqg/auth/staff）。
 *
 * <p>字段与契约逐字一致：{@code {phone, name, roleKey, password}}。
 * {@code roleKey} 只接受 {@code lqg_internal} / {@code lqg_admin}（本票只做这两种内部角色）。
 *
 * @author AUTH-STAFF-001
 */
@Data
public class StaffGrantBo {

    /**
     * 手机号（账号的唯一业务键：ADR-0003「一套账号，手机号定身份」）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 姓名（写入 sys_user.nick_name，以本次填的为准）
     */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /**
     * 目标内部角色：lqg_internal / lqg_admin
     */
    @NotBlank(message = "角色不能为空")
    private String roleKey;

    /**
     * 工作台初始密码（内部账号走账号密码登录，所以必须有）
     */
    @NotBlank(message = "工作台初始密码不能为空")
    private String password;

}
