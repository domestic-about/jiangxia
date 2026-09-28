package org.dromara.lqg.embed.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 石蜡包埋核验入参（doc/api-contract.md 的 {@code PUT /lqg/embed/{id}/verify}）：
 * {@code {action:"valid"|"invalid", paraffinBlockNo, reason, fill?}}。
 *
 * <p>★ 必填按 action 分化（{@code valid} → {@code paraffinBlockNo} 必填且全库唯一；
 * {@code invalid} → {@code reason} 必填），所以不在这里用 Bean Validation 表达 ——
 * 三条都在 {@code EmbedVerifyService} 里判，且<b>判完才写库</b>（ticket §2 第 5 条：
 * 缺了就拒绝、库里什么都不变）。
 *
 * <p>★ 这个 BO 里<b>没有</b> {@code verifyStatus}：目标状态只能由 {@code action} 经
 * {@code VerifyTransitions} 推出来，请求体里夹带 {@code verifyStatus} 不生效。
 *
 * <p>★ FIX V02b（issue #147）：可选的 {@link #fill} —— 核验抽屉里除石蜡块编号外的内容（样本类型、类器官来源类型、
 * 七个工序时间、包埋人、染色、marker、操作人、备注）以前显示成可填、请求体里却没有，填了被静默丢弃。
 * 现在随核验结论<b>同一个事务、同一条 UPDATE</b> 落库，规则与普通保存 {@code PUT /lqg/embed} 同一份
 * （{@code EmbedFillWriter}），补丁语义：没带的键不动、带了空值清空、{@code markers} 带了就整组替换。
 * <ul>
 *   <li>判为有效：15 项都收；</li>
 *   <li>判为无效：只收样本类型、类器官来源类型（外部送样填的两项）；工序、包埋人、染色、marker、操作人、备注是
 *       「实验室核验有效后填」的，带了 → 400、库里不变（不静默丢）；</li>
 *   <li>{@code fill} 里带 {@code sampleId} / {@code paraffinBlockNo} → 400（所挂样本核验时不能换；编号用顶层键）。</li>
 * </ul>
 * 不带 {@code fill}（或 {@code fill: null}）= 补填段一个字都不动，老调用方不受影响。
 *
 * @author EMBED-MODEL-001
 */
@Data
@Schema(description = "石蜡包埋核验入参")
public class EmbedVerifyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "核验动作不能为空")
    @Schema(description = "valid / invalid")
    private String action;

    @Schema(description = "石蜡块编号（判有效必填；全库唯一）")
    private String paraffinBlockNo;

    @Schema(description = "判无效的原因（判无效必填；外部可见）")
    private String reason;

    /**
     * 核验抽屉里一并保存的补填段（可选；补丁语义）。接口层按原始 JSON 读出「带了哪些键」再交给
     * {@code EmbedVerifyService}（区分「没带」与「带了 null」），本字段同时是接口文档里的形状。
     */
    @Schema(description = "补填段（可选；与核验结论同一事务保存，补丁语义同 PUT /lqg/embed；判无效只收 sampleType / organoidSourceType）")
    private EmbedFillBo fill;

}
