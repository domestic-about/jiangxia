package org.dromara.lqg.sample.verify;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部「修改送检段后重提」的入参（FLOW:F-SAMPLE-01.step4，ticket §2 的
 * {@code resubmitByExternal(sampleId, userId, 送检段字段)}）。
 *
 * <p>★ 只有**送检段**字段，没有核验段、也没有状态字段：
 * <ul>
 *   <li>{@code tissue}（样本记录信息表）：来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注；</li>
 *   <li>{@code organoid}（类器官收样记录）：来源单位、类器官类型、备注。</li>
 * </ul>
 * 另一类的类型列（{@code tissueType} / {@code organoidType}）由 service 按样本自己的
 * {@code sample_kind} 取舍 —— 传了也不生效。{@code submitNo / submitSource / submitterId /
 * verifyStatus / internalNo / receiveDate} 更是不在这里：外部**改不动**它们。
 *
 * <p>★ 语义 = 「送检段整体替换」：没传的送检段字段按清空处理（外部填写页整份提交）。
 *
 * @author SAMPLE-VERIFY-001
 */
@Data
@Schema(description = "外部修改送检段后重提的入参")
public class SampleResubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "来源单位 id；有值时 sourceUnitName 取单位表的名称快照")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称（没选单位时用它）")
    private String sourceUnitName;

    @Schema(description = "供体姓名（加密落库；tissue 段）")
    private String donorName;

    @Schema(description = "性别 male / female / unknown（tissue 段）")
    private String gender;

    @Schema(description = "年龄（文本；tissue 段）")
    private String age;

    @Schema(description = "住院号（加密落库；tissue 段）")
    private String hospitalNo;

    @Schema(description = "组织类型（tissue 段）")
    private String tissueType;

    @Schema(description = "有无病理 Y / N（tissue 段）")
    private String hasPathology;

    @Schema(description = "类器官类型（organoid 段）")
    private String organoidType;

    @Schema(description = "备注（两段都有）")
    private String remark;

}
