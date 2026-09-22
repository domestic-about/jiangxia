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
 * 类器官质量评分表（文档本体 + 通用附件），{@code GET /lqg/qc/{sampleId}} 的
 * {@code data.score}。
 *
 * <p>★ <b>没有图片位</b>：{@code images} 恒为空 map（评分表可以挂通用附件）。
 *
 * <p>★ {@code totalScore} 的语义见 {@code QcScoreDoc}：四项齐才是和，否则 <b>null</b>
 * （accept 3 第 1 段断 {@code .data.score.totalScore==18}）。
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "类器官质量评分表")
public class QcScoreDocVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "样本 id")
    private Long sampleId;

    @Schema(description = "培养前样本评分档位")
    private String preCultureLevel;

    @Schema(description = "培养天数档位")
    private String cultureDaysLevel;

    @Schema(description = "类器官数量档位")
    private String organoidCountLevel;

    @Schema(description = "类器官直径档位")
    private String diameterLevel;

    @Schema(description = "该档分值快照")
    private Integer preCultureScore;

    @Schema(description = "该档分值快照")
    private Integer cultureDaysScore;

    @Schema(description = "该档分值快照")
    private Integer organoidCountScore;

    @Schema(description = "该档分值快照")
    private Integer diameterScore;

    @Schema(description = "合计（任一项未选则为 null）")
    private Integer totalScore;

    @Schema(description = "draft / published")
    private String docStatus;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    @Schema(description = "完成人 user_id")
    private Long publishedBy;

    @Schema(description = "图片位：评分表没有图片位，恒为空 map")
    private Map<String, List<DocImageVo>> images;

    @Schema(description = "通用附件（按 sort 升序）")
    private List<DocAttachmentVo> attachments;

}
