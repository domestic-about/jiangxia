package org.dromara.lqg.ext.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 质控文档的对外形状（{@code ExtSampleDetailVo.docs} 的元素类型）。
 *
 * <p>★ <b>本票（AUTH-EXT-001）不装配它</b> —— 外部看文档是 AUTH-EXT-003 的活
 * （{@code GET /mp/ext/doc/**}）。本类在这里只是因为契约把 {@code docs} 列进了详情形状，
 * 而返回值只能是 ext 包里的 {@code Ext*} 类型（ADR-0004 的 I2）。
 *
 * <p>★ 形状按 {@code doc/api-contract.md} 第 87 行的 {@code ExtDocVo} 抄：
 * {@code {sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}}。
 * <b>没有</b>内部编号、没有渲染人、没有内部文件路径 —— 外部版文档里的内部编号一格仍留空
 * （CR-20260918-07：{@code lqg.ext.show-internal-no} 不作用于预渲染文档）。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "外部质控文档元信息（本票占位，AUTH-EXT-003 接数据）")
public class ExtDocVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "样本 id")
    private Long sampleId;

    @Schema(description = "送检单号")
    private String submitNo;

    @Schema(description = "供体姓名掩码")
    private String donorNameMasked;

    @Schema(description = "sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    @Schema(description = "评分表合计分")
    private Integer totalScore;

}
