package org.dromara.lqg.auth.group.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 组别行（UI:admin.auth.unit 右栏；工作台内部用）。
 *
 * <p>{@code verifiedCount} 是**读时** count 出来的（该组别下 {@code bind_status='verified'} 的外部人数），
 * 不是表上的列。对外接口（{@code /mp/ext/units}）**不许**复用本 VO —— 外部不能看到任何人数
 * （accept 第 3 条用 {@code map(select(test("count|Count")))} 卡死），对外的形状见
 * {@code org.dromara.lqg.ext.domain.vo.ExtUnitGroupVo}。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "组别（工作台）")
public class UnitGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组别 id")
    private Long groupId;

    @Schema(description = "所属单位 id")
    private Long unitId;

    @Schema(description = "单位名称")
    private String unitName;

    @Schema(description = "组别名称")
    private String groupName;

    @Schema(description = "状态：active / pending / disabled")
    private String groupStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "该组别下已核验的外部人数（读时 count）")
    private Long verifiedCount;

    @Schema(description = "创建时间")
    private Date createTime;

}
