package org.dromara.lqg.auth.group.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 核验入参：{@code PUT /lqg/auth/ext-user/{userId}/verify}（FLOW:F-AUTH-03.step3）。
 *
 * <p>形状与 doc/api-contract.md 逐字对齐：
 * {@code {action:"approve"|"reject", unitId?, groupId?, createUnit?, createGroup?, reason?}}。
 *
 * <p>「自填的单位 / 组别必须二选一」的判据在 service 里，不在 Bean Validation 里：
 * 它依赖**档案当前是不是自填的**，只有读了库才知道（写库之前抛）。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "外部用户组别核验入参")
public class ExtVerifyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * approve 通过 / reject 驳回
     */
    @NotNull(message = "action 不能为空")
    @Schema(description = "approve / reject")
    private String action;

    /**
     * 归并到的既有单位（自填档案核验时的「归并到已有」第一条腿）
     */
    private Long unitId;

    /**
     * 归并到的既有组别（必须属于上面的单位）
     */
    private Long groupId;

    /**
     * 「新建」第一条腿：把自填的单位名建成 active 单位
     */
    private Boolean createUnit;

    /**
     * 「新建」第二条腿：把自填的组别名建成该单位下的 active 组别
     */
    private Boolean createGroup;

    /**
     * 驳回原因（reject 时必填）
     */
    private String reason;

}
