package org.dromara.lqg.auth.group.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 组别新增 / 修改入参（{@code POST /lqg/auth/group}、{@code PUT /lqg/auth/group/{id}}）。
 *
 * <p>{@code groupName} 在**同一单位内**唯一；不同单位可以重名（唯一索引是
 * {@code uk_unit_group (unit_id, group_name) WHERE del_flag='0'}）。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "组别入参")
public class UnitGroupBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属单位 id（修改时也必填：不允许把一个组别悄悄挪到别的单位下 —— 那会让已绑定的外部用户
     * 的可见范围变化，必须走核验那条路）
     */
    @NotNull(message = "所属单位不能为空")
    private Long unitId;

    @NotBlank(message = "组别名称不能为空")
    @Size(max = 100, message = "组别名称不能超过 100 字")
    private String groupName;

    /**
     * 状态：active / pending / disabled；新增时缺省 active
     */
    private String groupStatus;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;

}
