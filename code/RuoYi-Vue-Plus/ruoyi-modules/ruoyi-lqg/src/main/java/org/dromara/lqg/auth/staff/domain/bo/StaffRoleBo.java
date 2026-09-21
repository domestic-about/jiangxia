package org.dromara.lqg.auth.staff.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 改角色入参（doc/api-contract.md：PUT /lqg/auth/staff/{userId}/role）。
 *
 * @author AUTH-STAFF-001
 */
@Data
public class StaffRoleBo {

    /**
     * 目标内部角色：lqg_internal / lqg_admin
     */
    @NotBlank(message = "角色不能为空")
    private String roleKey;

}
