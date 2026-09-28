package org.dromara.lqg.sample.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 样本的<b>送检段</b>（FLOW:F-SAMPLE-01.step1 的 {@code writes} 里除身份列以外的那一组）：
 * 来源单位、供体姓名、性别、年龄、住院号、组织类型 / 类器官类型、代数（类器官）、有无病理、备注。
 *
 * <p>★ 三条写路径共用这一个形状、共用 {@code SubmitSegmentRules} 那一份必填与格式、共用
 * {@code SampleSubmitSegmentWriter} 那一份落库列（FIX V02，避免两套规则）：
 * <ul>
 *   <li>工作台内部修改 {@code PUT /lqg/sample}（整份表单，{@code SampleService.update}）；</li>
 *   <li>工作台核验抽屉 {@code PUT /lqg/sample/{id}/verify} 里一并保存的 {@code submitSegment}
 *       （核验时改的送检信息与核验结论同一个事务落库，issue #147 / V02）；</li>
 *   <li>外部改后重提 {@code PUT /mp/ext/sample/{id}}、{@code PUT /mp/ext/organoid/{id}}
 *       （{@code SampleResubmitBo} 继承本类）。</li>
 * </ul>
 *
 * <p>★ 语义 = <b>整段替换</b>：按样本<b>自己的</b> {@code sample_kind} 写本类别的那几列，没传的按清空处理
 * （组织样本：来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注；
 * 类器官：来源单位、类器官类型、代数、备注）。另一类的类型列（含代数）传了也不生效。
 *
 * <p>★ 本类<b>没有</b>身份列（{@code submitNo / submitSource / submitterId / sampleKind}）、
 * <b>没有</b>核验段与收样段（{@code verifyStatus / internalNo / receiveDate / operatorName …}）：
 * 夹带它们反序列化不到任何地方。
 *
 * @author FIX-V02
 */
@Data
@Schema(description = "样本送检段（整段替换）")
public class SampleSubmitSegmentBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "来源单位 id；有值时 sourceUnitName 取单位表的名称快照")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称（没选单位时必填，≤ 100 字）", maxLength = 100)
    private String sourceUnitName;

    @Schema(description = "供体姓名（组织样本；加密落库，≤ 50 字）", maxLength = 50)
    private String donorName;

    @Schema(description = "性别（组织样本）", allowableValues = {"male", "female", "unknown"})
    private String gender;

    @Schema(description = "年龄（组织样本；文本，≤ 20 字）", maxLength = 20)
    private String age;

    @Schema(description = "住院号（组织样本；加密落库，≤ 50 字）", maxLength = 50)
    private String hospitalNo;

    @Schema(description = "组织类型（组织样本必填，≤ 100 字）", maxLength = 100)
    private String tissueType;

    @Schema(description = "类器官类型（类器官必填，≤ 100 字）", maxLength = 100)
    private String organoidType;

    @Schema(description = "代数（类器官选填，形如 P3；小写 p 自动转大写；组织样本不落库）", example = "P3")
    private String passage;

    @Schema(description = "有无病理（组织样本）", allowableValues = {"Y", "N"})
    private String hasPathology;

    @Schema(description = "备注（≤ 500 字）", maxLength = 500)
    private String remark;

}
