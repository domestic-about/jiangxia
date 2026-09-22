package org.dromara.lqg.embed.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 石蜡包埋核验入参（doc/api-contract.md 的 {@code PUT /lqg/embed/{id}/verify}）：
 * {@code {action:"valid"|"invalid", paraffinBlockNo, reason}}。
 *
 * <p>★ 必填按 action 分化（{@code valid} → {@code paraffinBlockNo} 必填且全库唯一；
 * {@code invalid} → {@code reason} 必填），所以不在这里用 Bean Validation 表达 ——
 * 三条都在 {@code EmbedVerifyService} 里判，且<b>判完才写库</b>（ticket §2 第 5 条：
 * 缺了就拒绝、库里什么都不变）。
 *
 * <p>★ 这个 BO 里<b>没有</b> {@code verifyStatus}：目标状态只能由 {@code action} 经
 * {@code VerifyTransitions} 推出来，请求体里夹带 {@code verifyStatus} 不生效。
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

}
