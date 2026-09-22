package org.dromara.lqg.qc.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 类器官质控表（文档本体 + 一个图片位 + 通用附件），{@code GET /lqg/qc/{sampleId}} 的
 * {@code data.organoidQc}。
 *
 * <p>★ 五栏都是自由文本（{@code formedTime} / {@code feedbackTime} 也是文本，不是日期）。
 * <p>★ {@code images} 只有 {@code organoid_observe} 一个键。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "类器官质控表")
public class QcOrganoidDocVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "样本 id")
    private Long sampleId;

    @Schema(description = "形成类器官时间（文本）")
    private String formedTime;

    @Schema(description = "生长状态")
    private String growthState;

    @Schema(description = "类器官生长情况")
    private String growthDesc;

    @Schema(description = "预计筛药")
    private String plannedDrugScreen;

    @Schema(description = "反馈时间（文本）")
    private String feedbackTime;

    @Schema(description = "draft / published")
    private String docStatus;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    @Schema(description = "完成人 user_id")
    private Long publishedBy;

    @Schema(description = "图片位（按 slot 分组；只有 organoid_observe）")
    private Map<String, List<DocImageVo>> images;

    @Schema(description = "通用附件（按 sort 升序）")
    private List<DocAttachmentVo> attachments;

}
