package org.dromara.lqg.sample.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 样本新增 / 修改入参（doc/api-contract.md 的 {@code POST /lqg/sample}、{@code PUT /lqg/sample}）。
 *
 * <p>★ <b>不可改</b>的三列刻意**不在这里声明**（ticket §2.2 / 契约）：
 * {@code submitNo}（服务端按序列取号）、{@code submitSource}（提交当时按提交人身份落库）、
 * {@code submitterId}（提交人）。请求里夹带它们不会生效 —— 不是「前端不显示」，是接口里没有。
 *
 * <p>★ {@code internalNo} 是**手填**的（甲方自己的编号规则，系统只做唯一性校验 + 软删后可重用）；
 * 对外部提交的样本它可以为空，它在库里也确实是可空列。
 *
 * @author SAMPLE-MODEL-001
 */
@Data
@Schema(description = "样本新增 / 修改入参")
public class SampleSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 修改时必填（新增时忽略）。
     */
    @Schema(description = "主键（PUT 必填）")
    private Long id;

    @NotNull(message = "样本类别不能为空")
    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "来源单位 id；有值时 sourceUnitName 取单位表的名称快照")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称（没选单位时用它）")
    private String sourceUnitName;

    @Schema(description = "供体姓名（加密落库）")
    private String donorName;

    @Schema(description = "性别 male / female / unknown")
    private String gender;

    @Schema(description = "年龄（文本）")
    private String age;

    @Schema(description = "住院号（加密落库）")
    private String hospitalNo;

    @Schema(description = "组织类型（tissue 类必填）")
    private String tissueType;

    @Schema(description = "类器官类型（organoid 类必填）")
    private String organoidType;

    @Schema(description = "有无病理 Y / N")
    private String hasPathology;

    @Schema(description = "收样日期 yyyy-MM-dd（两类都必填）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @Schema(description = "内部编号（手填；全库唯一；软删后可重用）")
    private String internalNo;

    @Schema(description = "有无固定 Y / N")
    private String isFixed;

    @Schema(description = "处理时间 yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    @Schema(description = "质控表 Y / N（按钮手点，不自动推导）")
    private String hasQcSheet;

    @Schema(description = "细胞活率报告 Y / N（按钮手点）")
    private String hasViabilityReport;

    @Schema(description = "操作人（默认带当前登录人，可改）")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

}
