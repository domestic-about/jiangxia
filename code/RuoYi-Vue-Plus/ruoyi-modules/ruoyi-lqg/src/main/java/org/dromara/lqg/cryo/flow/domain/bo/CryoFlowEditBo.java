package org.dromara.lqg.cryo.flow.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 改一笔冻存登记的入参（{@code PUT /lqg/cryo/batch/{id}/flow/{flowId}}）。
 *
 * <p>★ <b>登记类型不能改</b>（CR-20260917-04 / FLOW:F-CRYO-02.step5）：要换类型就删掉重登。
 * 本 BO <b>仍然收 {@code flowType}</b>，但不是用来改的 —— 收了才好在「传了且与原来不同」时
 * 明确回 400（accept 2 第 4 段点名「取走改成补入」要被拒）；不收的话这个请求会静默改成功。
 *
 * <p>★ {@code qty} 按<b>原类型</b>解释：原 {@code take} / {@code add} 给正整数，
 * 原 {@code adjust} 给带符号不为 0 的值。
 *
 * <p>★ {@code fromLocation} 不在入参里：改登记时它<b>保持登记时的值</b>
 * （accept 2 第 2 段查的就是 {@code minus80} 没被按批次当前位置重算）。
 *
 * <p>★ 四个字段都是 patch 语义：不传 = 不动（{@code qty} / {@code flowTime} 不传时保留原值），
 * 传了就必须合法（{@code qty=0} / 非法时间格式一律 400，不静默忽略）。
 *
 * @author CRYO-FLOW-001
 */
@Data
@Schema(description = "冻存出入库流水修改入参")
public class CryoFlowEditBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "支数（按原类型解释：take / add 正整数，adjust 带符号不为 0）")
    private Integer qty;

    @Schema(description = "用途 / 原因（原类型是 adjust 时必填）")
    private String purpose;

    @Schema(description = "经手人")
    private String operatorName;

    @Schema(description = "发生时间 yyyy-MM-dd HH:mm:ss（不传 = 不动）")
    private String flowTime;

    @Schema(description = "登记类型：只用于「传了且与原来不同 → 400」，改不了它")
    private String flowType;

}
