package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对外选择器里的组别 —— {@code GET /mp/ext/units} 的 {@code data[].groups[]}。
 *
 * <p>★ **只有 id 与名称**。刻意不复用工作台的 {@code UnitGroupVo}（那个带 {@code verifiedCount}）：
 * 外部不能看到每个组有多少人（accept 第 3 条用 {@code map(select(test("count|Count")))} 卡死），
 * 复用会让工作台新加的字段顺着漏出去（ADR-0004 的咽喉口径）。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "对外：组别（仅 id 与名称）")
public class ExtUnitGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组别 id")
    private Long groupId;

    @Schema(description = "组别名称")
    private String groupName;

}
