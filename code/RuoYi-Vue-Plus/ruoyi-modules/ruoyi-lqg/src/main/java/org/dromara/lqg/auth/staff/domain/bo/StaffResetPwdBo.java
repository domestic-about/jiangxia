package org.dromara.lqg.auth.staff.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置工作台密码入参（doc/api-contract.md：PUT /lqg/auth/staff/{userId}/reset-pwd）。
 *
 * @author AUTH-STAFF-001
 */
@Data
public class StaffResetPwdBo {

    /**
     * 新口令（明文，服务端 BCrypt 后入库）
     */
    @NotBlank(message = "新密码不能为空")
    private String password;

}
