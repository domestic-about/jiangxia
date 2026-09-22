package org.dromara.lqg.sample.verify;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 核验入参（doc/api-contract.md 的 {@code PUT /lqg/sample/{id}/verify}）：
 * {@code {action:"valid"|"invalid", receiveDate, internalNo, isFixed, processTime, hasQcSheet,
 * hasViabilityReport, operatorName, reason}}。
 *
 * <p>★ 必填是**按 action 分化**的，所以不在这里用 Bean Validation 表达（那会把 valid / invalid
 * 两组必填混成一组超集）：{@code action=valid} 时 {@code receiveDate} + {@code internalNo} 必填，
 * {@code action=invalid} 时 {@code reason} 必填 —— 三条都在 {@link SampleVerifyService} 里判，
 * 且**判完才写库**（ticket §0 口径 1）。
 *
 * <p>★ 这个 BO 里**没有** {@code verifyStatus}：目标状态只能由 {@code action} 经
 * {@link VerifyTransitions} 推出来，请求体里夹带 {@code verifyStatus} 不生效。
 *
 * @author SAMPLE-VERIFY-001
 */
@Data
@Schema(description = "样本核验入参")
public class SampleVerifyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * valid 判有效 / invalid 判无效
     */
    @NotBlank(message = "核验动作不能为空")
    @Schema(description = "valid / invalid")
    private String action;

    /**
     * 收样日期（action=valid 必填）
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "收样日期 yyyy-MM-dd（判有效必填）")
    private LocalDate receiveDate;

    /**
     * 内部编号（action=valid 必填、全库唯一、软删后可重用）
     */
    @Schema(description = "内部编号（判有效必填；全库唯一）")
    private String internalNo;

    /**
     * 有无固定 Y / N
     */
    @Schema(description = "有无固定 Y / N")
    private String isFixed;

    /**
     * 处理时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "处理时间 yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    /**
     * 质控表 Y / N（手点，不自动推导）
     */
    @Schema(description = "质控表 Y / N")
    private String hasQcSheet;

    /**
     * 细胞活率报告 Y / N（手点）
     */
    @Schema(description = "细胞活率报告 Y / N")
    private String hasViabilityReport;

    /**
     * 操作人（默认带当前登录人，可改）
     */
    @Schema(description = "操作人姓名")
    private String operatorName;

    /**
     * 无效原因（action=invalid 必填；外部可见）
     */
    @Schema(description = "判无效的原因（判无效必填）")
    private String reason;

}
