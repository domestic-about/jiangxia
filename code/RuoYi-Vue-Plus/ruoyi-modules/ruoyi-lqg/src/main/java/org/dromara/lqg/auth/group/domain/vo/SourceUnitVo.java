package org.dromara.lqg.auth.group.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 来源单位行（UI:admin.auth.unit 左栏；工作台内部用，可以带停用项与状态）。
 *
 * <p>注意与 {@code org.dromara.lqg.ext.domain.vo.ExtUnitVo} 的区别：那个是对外的、只有 id 与名称、
 * 只含 active。两者**刻意不共用**，免得哪天给工作台加的字段（例如这里的 status / createTime）
 * 顺着复用漏到外部接口上（ADR-0004）。
 *
 * @author AUTH-GROUP-001
 */
@Data
@Schema(description = "来源单位（工作台）")
public class SourceUnitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "单位 id")
    private Long unitId;

    @Schema(description = "单位名称")
    private String unitName;

    @Schema(description = "状态：active / pending / disabled")
    private String unitStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "该单位下的组别数（工作台左栏用）")
    private Long groupCount;

    @Schema(description = "创建时间")
    private Date createTime;

}
