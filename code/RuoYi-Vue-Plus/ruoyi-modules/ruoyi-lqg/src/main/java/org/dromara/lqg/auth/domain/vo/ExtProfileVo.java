package org.dromara.lqg.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部档案视图 —— {@code GET /mp/me} 的 {@code data.ext}（形状权威：doc/api-contract.md「AUTH」一节）。
 *
 * <p>内部人员没有档案行，{@code ext} 整个是 {@code null}（不是空对象）——前端据此渲染。
 *
 * <p>{@code unitName} / {@code groupName} 是**读时 join** 出来的：档案行只存 id，
 * 名称以 {@code t_lqg_source_unit} / {@code t_lqg_unit_group} 当时的名称为准。
 *
 * @author AUTH-LOGIN-001
 */
@Data
@Schema(description = "外部用户档案（单位 / 组别 / 核验状态）")
public class ExtProfileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "单位 id（未选为空）")
    private Long unitId;

    @Schema(description = "单位名称（读时 join；单位表未建时为空）")
    private String unitName;

    @Schema(description = "组别 id（未选为空）")
    private Long groupId;

    @Schema(description = "组别名称（读时 join；组别表未建时为空）")
    private String groupName;

    @Schema(description = "单位不在列表时自填的单位名")
    private String unitNameInput;

    @Schema(description = "组别不在列表时自填的组别名")
    private String groupNameInput;

    @Schema(description = "核验状态：unbound / pending / verified / rejected")
    private String bindStatus;

    @Schema(description = "驳回原因（rejected 时有值）")
    private String rejectReason;

}
