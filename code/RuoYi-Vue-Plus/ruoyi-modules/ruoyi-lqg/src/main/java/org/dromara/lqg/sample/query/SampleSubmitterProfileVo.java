package org.dromara.lqg.sample.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 样本行上「提交人的外部档案」那一小块（SAMPLE-WEB-001）。
 *
 * <p>只带工作台总表要显示 / 筛选的两列：<b>提交人姓名</b>与<b>组别</b>。
 * 单位名称样本行上本来就有（{@code source_unit_name}，读时不 join）；
 * 组别名称走与 AUTH 域同一条路（{@code t_lqg_unit_group.group_name}）。
 *
 * <p>★ 内部人员录入的样本（{@code sample.submitter_id} 是内部账号）在这张表里<b>没有行</b> ——
 * 内部人员不是「外部用户」，两个字段就是 {@code null}，不是错误。
 *
 * @author SAMPLE-WEB-001
 */
@Data
@Schema(description = "样本提交人的外部档案片段（提交人姓名 / 组别）")
public class SampleSubmitterProfileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "提交人 user_id")
    private Long userId;

    @Schema(description = "提交人姓名（t_lqg_ext_profile.real_name）")
    private String submitterName;

    @Schema(description = "组别 id（t_lqg_ext_profile.group_id）")
    private Long groupId;

    @Schema(description = "组别名称（内部人员 / 自填单位名为 null）")
    private String groupName;

}
