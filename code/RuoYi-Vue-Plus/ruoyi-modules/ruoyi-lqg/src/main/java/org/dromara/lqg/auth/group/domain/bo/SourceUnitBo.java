package org.dromara.lqg.auth.group.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 来源单位新增 / 修改入参（{@code POST /lqg/auth/unit}、{@code PUT /lqg/auth/unit/{id}}）。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "来源单位入参")
public class SourceUnitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 单位名称：全库唯一（service 层查重给人话报错，唯一索引兜底）
     */
    @NotBlank(message = "单位名称不能为空")
    @Size(max = 100, message = "单位名称不能超过 100 字")
    private String unitName;

    /**
     * 状态：active / pending / disabled；新增时缺省 active
     */
    private String unitStatus;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;

}
