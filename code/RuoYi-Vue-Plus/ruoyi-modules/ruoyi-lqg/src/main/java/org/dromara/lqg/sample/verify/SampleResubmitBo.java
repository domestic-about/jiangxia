package org.dromara.lqg.sample.verify;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;

import java.io.Serial;

/**
 * 外部「修改送检段后重提」的入参（FLOW:F-SAMPLE-01.step4，ticket §2 的
 * {@code resubmitByExternal(sampleId, userId, 送检段字段)}）。
 *
 * <p>★ 只有**送检段**字段，没有核验段、也没有状态字段：
 * <ul>
 *   <li>{@code tissue}（样本记录信息表）：来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注；</li>
 *   <li>{@code organoid}（类器官收样记录）：来源单位、类器官类型、代数、备注。</li>
 * </ul>
 * 另一类的类型列（{@code tissueType} / {@code organoidType} / {@code passage}）由 service 按样本自己的
 * {@code sample_kind} 取舍 —— 传了也不生效。{@code submitNo / submitSource / submitterId /
 * verifyStatus / internalNo / receiveDate} 更是不在这里：外部**改不动**它们。
 *
 * <p>★ 语义 = 「送检段整体替换」：没传的送检段字段按清空处理（外部填写页整份提交）。
 *
 * <p>FIX-V02：字段整体上移到 {@link SampleSubmitSegmentBo}（工作台核验抽屉一并保存送检段也用它），
 * 本类只保留「外部重提」这个名字 —— 两条路径的送检段是同一个形状、同一份校验、同一组落库列。
 *
 * @author SAMPLE-VERIFY-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "外部修改送检段后重提的入参")
public class SampleResubmitBo extends SampleSubmitSegmentBo {

    @Serial
    private static final long serialVersionUID = 1L;

}
