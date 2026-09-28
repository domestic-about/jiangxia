package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对外选择器里的单位 —— {@code GET /mp/ext/units} 的 {@code data[]}。
 *
 * <p>形状（doc/api-contract.md）：{@code [{unitId, unitName, groups:[{groupId, groupName}]}]}，
 * **只含 active**（停用的单位与停用的组别都不返回）。
 *
 * <p>★ 不带任何人数 / 计数键：accept 第 3 条会把整个响应体里所有对象的键名过一遍
 * {@code test("count|Count")}，命中一个就红。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "对外：单位与它的组别（仅 id 与名称，仅启用项）")
public class ExtUnitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "单位 id")
    private Long unitId;

    @Schema(description = "单位名称")
    private String unitName;

    @Schema(description = "该单位下启用的组别")
    private List<ExtUnitGroupVo> groups;

}
