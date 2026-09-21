package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部「组织样本」送检入参（{@code POST /mp/ext/sample} / {@code PUT /mp/ext/sample/{id}}）。
 *
 * <p>★ <b>只有送检段字段</b>（FLOW:F-SAMPLE-01.step1 的 {@code writes} 清单，
 * ticket §0 口径 4 点名的一条）：
 * 来源单位（id 或名称）、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注。
 *
 * <p>★ <b>刻意不复用内部的 {@code SampleSubmitBo}</b>：那份带 {@code internalNo} /
 * {@code receiveDate} / {@code hasQcSheet} / {@code hasViabilityReport} / {@code operatorName}
 * 等核验段与收样段字段，「复用 + 忽略多余字段」的写法哪天内部 BO 加一个字段，外部就多能写一个
 * （counterfeit 点名的形态）。本类<b>没有</b>这些键 → 请求体里夹带也<b>反序列化不到任何地方</b>，
 * 不是「读到了再丢掉」。
 *
 * <p>★ 同样没有 {@code sampleKind} / {@code submitSource} / {@code verifyStatus} / {@code submitterId}：
 * 它们由服务端按端点与当前登录人写死（{@code tissue} / {@code external} / {@code pending} / 本人）。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "外部组织样本送检入参")
public class ExtSampleSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "来源单位 id（选了列表项时用）")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称（列表里没有时自填）")
    private String sourceUnitName;

    @Schema(description = "供体姓名")
    private String donorName;

    @Schema(description = "性别 male / female / unknown")
    private String gender;

    @Schema(description = "年龄（文本）")
    private String age;

    @Schema(description = "住院号")
    private String hospitalNo;

    @Schema(description = "组织类型")
    private String tissueType;

    @Schema(description = "有无病理 Y / N")
    private String hasPathology;

    @Schema(description = "备注")
    private String remark;

}
