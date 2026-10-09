package org.dromara.lqg.qc.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 「质控文档」板块列表的一行 = 一个已核验有效的样本 + 它三份质控表各自的状态。
 *
 * <p>三份状态的取值：{@code null}（还没打开过，库里没有这一份）/ {@code draft}（草稿）/
 * {@code published}（已完成并同步）。编辑页首次打开会就地建三份空草稿，所以
 * 「三份都是 null」才算「未开始」。
 */
@Data
@Schema(description = "质控文档列表行")
public class QcDocListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "样本 id（进编辑页用）")
    private Long sampleId;

    @Schema(description = "内部编号")
    private String internalNo;

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "来源单位")
    private String sourceUnitName;

    @Schema(description = "种属（样本行上的，CR-20261009-18）")
    private String species;

    @Schema(description = "组织类型（tissue）或类器官类型（organoid）")
    private String typeName;

    @Schema(description = "收样日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @Schema(description = "样本质控表状态：null / draft / published")
    private String sampleQcStatus;

    @Schema(description = "类器官质控表状态：null / draft / published")
    private String organoidQcStatus;

    @Schema(description = "类器官质量评分表状态：null / draft / published")
    private String scoreStatus;

    @Schema(description = "已完成并同步的份数（0-3）")
    private Integer publishedCount;

    @Schema(description = "none / doing / done")
    private String progress;

    @Schema(description = "评分表合计分（评分表存在时才有）")
    private Integer totalScore;

    @Schema(description = "三份里最近一次修改的时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUpdateTime;

}
