package org.dromara.lqg.cryo.batch.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 冻存出入库流水行（doc/api-contract.md 的 {@code GET /lqg/cryo/batch/{id}/flows}）。
 *
 * <p>★ 本票（CRYO-MODEL-001）只出<b>形状</b>，不落端点：流水列表 / 取走 / 补入 / 盘点调整 /
 * 转液氮的接口全在 CRYO-FLOW-001（ticket §3 边界）。这里先把列形状定下来，免得两个域各写一份。
 *
 * <p>★ CRYO-FLOW-001 会在这个 VO 上补三个键（{@code balanceAfter} = 按时间正序累计算出的
 * 操作后剩余、不落库；{@code edited} = 改过为 true；{@code updateByName}）——
 * 本票<b>刻意不预置</b>它们为 null：预置了会让「忘记实现」看起来像「实现了但没数据」。
 *
 * @author CRYO-MODEL-001
 */
@Data
@Schema(description = "冻存出入库流水")
public class CryoFlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "批次 id")
    private Long batchId;

    @Schema(description = "take 取走 / add 补入 / adjust 盘点调整")
    private String flowType;

    @Schema(description = "带符号变化量")
    private Integer delta;

    @Schema(description = "minus80 / ln2（登记时的位置，不随后续转移重算）")
    private String fromLocation;

    @Schema(description = "经手人")
    private String operatorName;

    @Schema(description = "发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flowTime;

    @Schema(description = "用途 / 原因")
    private String purpose;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @Schema(description = "创建人 user_id")
    private Long createBy;

    @Schema(description = "最后修改人 user_id")
    private Long updateBy;

}
