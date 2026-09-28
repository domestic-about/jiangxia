package org.dromara.lqg.auth.group.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 启用 / 停转入参（{@code PUT /lqg/auth/unit/{id}/status}、{@code PUT /lqg/auth/group/{id}/status}）。
 *
 * <p>单位与组别**不物理删**（ticket §2.2）：停用只是不再出现在小程序选择器里，
 * 已绑定的人不受影响。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "启用 / 停转入参")
public class StatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * active 启用 / disabled 停用
     */
    @NotBlank(message = "状态不能为空")
    @Schema(description = "active / disabled")
    private String status;

}
