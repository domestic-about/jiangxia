package org.dromara.lqg.doc.mp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 内部文档清单的一行（{@code GET /mp/int/doc/list}，FLOW:F-DOC-02.step1）。
 *
 * <p>形状与外部那份（{@code ExtDocVo}）<b>对齐到同一组键</b>，只是内容的来源不同：
 *
 * <ul>
 *   <li>{@code title} / {@code subtitle} 由<b>后端按身份给</b>：
 *       内部 = 内部编号 / 来源单位（本接口）；外部 = 送检单号 / 掩码姓名。
 *       ★ 前端只渲染这两个字段，<b>不自己拼内部编号</b>（ticket §0 口径 1 与
 *       accept 2 的禁字 grep 点的就是这件事）。</li>
 *   <li>{@code internalNo} / {@code sourceUnitName} 是同一份数据的「原料」，
 *       内部接口可以给（外部接口一个都不给）；前端不读它们。</li>
 *   <li>{@code totalScore} 只在评分表那一行上有（{@code NON_NULL}：别的行连键都不出）。</li>
 * </ul>
 *
 * @author DOC-MP-001
 */
@Data
@Schema(description = "内部质控文档列表行")
public class MpDocVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "样本 id")
    private Long sampleId;

    @Schema(description = "组标题（内部：内部编号；缺则回落送检单号）")
    private String title;

    @Schema(description = "组副标题（来源单位）")
    private String subtitle;

    @Schema(description = "内部编号（原料字段，前端不读）")
    private String internalNo;

    @Schema(description = "来源单位名称（原料字段，前端不读）")
    private String sourceUnitName;

    @Schema(description = "sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishedTime;

    @Schema(description = "评分表合计分（只在评分表那一行上有；别的行连键都不出）")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer totalScore;
}
