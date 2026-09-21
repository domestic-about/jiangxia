package org.dromara.lqg.auth.staff.domain.vo;

import lombok.Data;

/**
 * 授权结果（doc/api-contract.md：POST /lqg/auth/staff → {@code data:{userId, upgraded}}）。
 *
 * <p>{@code upgraded} 是本票的核心语义标记：{@code true} = 这个手机号**已经有账号**（多半是
 * 以外部身份登录过小程序），本次是原地升级、没有新建账号；{@code false} = 库里没有这个手机号，
 * 本次预建了内部账号（FLOW:F-AUTH-02.step2）。
 *
 * @author AUTH-STAFF-001
 */
@Data
public class StaffGrantVo {

    /**
     * 账号 id（升级路径下就是原账号的 id）
     */
    private Long userId;

    /**
     * 是否升级了既有账号（true = 升级，false = 预建）
     */
    private Boolean upgraded;

}
