package org.dromara.lqg.auth.group.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 外部用户行（UI:admin.auth.extuser）——「姓名、手机号、单位、组别（自填的标「自填」）、
 * 核验状态、提交样本数、最近登录」，全部**读时算**。
 *
 * <p>{@code selfInput=true} 表示单位 / 组别是外部自己填的名字，还没归到库里的单位 / 组别上
 * （前端把这两个格子渲染成斜体 + 「自填」标签，并通过它决定核验弹窗要不要出「新建 / 归并」二选一）。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "外部用户（工作台核验页）")
public class ExtUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "姓名（档案里的 real_name；空时回落账号昵称）")
    private String name;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "单位 id（自填时为空）")
    private Long unitId;

    @Schema(description = "单位名称（自填时给自填名）")
    private String unitName;

    @Schema(description = "组别 id（自填时为空）")
    private Long groupId;

    @Schema(description = "组别名称（自填时给自填名）")
    private String groupName;

    @Schema(description = "外部自填的单位原名（自填时非空；核验弹窗用它显示「新建为 X」）")
    private String unitNameInput;

    @Schema(description = "外部自填的组别原名（自填时非空）")
    private String groupNameInput;

    @Schema(description = "单位 / 组别是否外部自填（true = 还没归到库里）")
    private Boolean selfInput;

    @Schema(description = "核验状态：unbound / pending / verified / rejected")
    private String bindStatus;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "提交样本数（读时 count；样本表还没建时为 0）")
    private Long sampleCount;

    @Schema(description = "最近登录时间")
    private Date lastLoginTime;

}
