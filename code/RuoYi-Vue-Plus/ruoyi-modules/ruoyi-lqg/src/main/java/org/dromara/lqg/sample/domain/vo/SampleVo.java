package org.dromara.lqg.sample.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 样本行 / 详情（UI:admin.sample.list，doc/api-contract.md 的 {@code GET /lqg/sample/list}）。
 *
 * <p>★ {@code donorName} / {@code hospitalNo} 在这里是**明文**：库里的密文由
 * {@code SampleQueryService} 读出时解出来再塞进 VO（ADR-0006：内部人员要对着全名核样本，不打码）。
 * 查询侧只支持**精确匹配**（service 层先把查询值加密再 eq），不做 LIKE。
 *
 * <p>★ 与 {@code org.dromara.lqg.ext.domain.vo.ExtSampleDetailVo} 刻意**不共用**：那个对外，
 * 没有 {@code operatorName / verifyBy / internalNo} 这些键（ADR-0004 + CR-20260918-07）。
 *
 * @author SAMPLE-MODEL-001
 */
@Data
@Schema(description = "样本（工作台）")
public class SampleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "送检单号")
    private String submitNo;

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "internal / external")
    private String submitSource;

    @Schema(description = "提交人 user_id")
    private Long submitterId;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "核验人 user_id")
    private Long verifyBy;

    @Schema(description = "核验时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date verifyTime;

    @Schema(description = "判无效的原因")
    private String invalidReason;

    @Schema(description = "来源单位 id")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称")
    private String sourceUnitName;

    @Schema(description = "供体姓名（明文）")
    private String donorName;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "年龄")
    private String age;

    @Schema(description = "住院号（明文）")
    private String hospitalNo;

    @Schema(description = "组织类型")
    private String tissueType;

    @Schema(description = "类器官类型")
    private String organoidType;

    @Schema(description = "有无病理 Y / N")
    private String hasPathology;

    @Schema(description = "收样日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @Schema(description = "内部编号")
    private String internalNo;

    @Schema(description = "有无固定 Y / N")
    private String isFixed;

    @Schema(description = "处理时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    @Schema(description = "质控表 Y / N")
    private String hasQcSheet;

    @Schema(description = "细胞活率报告 Y / N")
    private String hasViabilityReport;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 最后修改人姓名（REQ-SAMPLE-016 / CR-20260917-04：修改页显示「最后修改：某某 · 时间」）。
     * 读时按 {@code update_by} 回 sys_user 取昵称；没有 update_by 的行取 {@code create_by} 的姓名。
     */
    @Schema(description = "最后修改人姓名")
    private String updateByName;

}
