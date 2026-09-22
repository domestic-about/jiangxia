package org.dromara.lqg.cryo.flow.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 冻存出入库流水的登记入参（{@code POST /lqg/cryo/batch/{id}/flow}）。
 *
 * <p>★ <b>刻意没有 {@code fromLocation}</b>（FLOW:F-CRYO-02.step1 / FIELD:t_lqg_cryo_flow.from_location）：
 * 取自位置由批次<b>当时</b>所在位置自动带出，不让人选 —— 收了它，前端就能把「已转液氮」的批次
 * 记成 {@code minus80}（accept 1 的 counterfeit 点名这一形态）。
 *
 * <p>★ {@code qty} 的符号语义按 {@code flowType} 解释（ticket §2 / 契约）：
 * <ul>
 *   <li>{@code take} / {@code add}：{@code qty} 是<b>正整数</b>，落库 {@code delta = ∓qty}；</li>
 *   <li>{@code adjust}：{@code qty} 带符号、<b>不为 0</b>、{@code purpose} 必填。</li>
 * </ul>
 *
 * @author CRYO-FLOW-001
 */
@Data
@Schema(description = "冻存出入库流水登记入参")
public class CryoFlowSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "take 取走 / add 补入 / adjust 盘点调整")
    private String flowType;

    @Schema(description = "支数：take / add 正整数；adjust 带符号不为 0")
    private Integer qty;

    @Schema(description = "用途 / 原因（adjust 必填）")
    private String purpose;

    @Schema(description = "经手人（缺省取当前登录人姓名）")
    private String operatorName;

    @Schema(description = "发生时间 yyyy-MM-dd HH:mm:ss（缺省取当前时间）")
    private String flowTime;

}
