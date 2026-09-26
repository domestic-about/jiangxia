package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部「类器官收样记录」送检入参（{@code POST /mp/ext/organoid} / {@code PUT /mp/ext/organoid/{id}}）。
 *
 * <p>★ 按 {@code doc/api-contract.md} 第 53 行与 {@code CR-20260917-05}：外部填类器官只填
 * <b>来源单位（id 或名称）、类器官类型、备注</b>三项 —— 比组织样本还少；
 * CR-20260924-10（甲方 2026-09-24 第 18 行）在「类器官类型」后加一项<b>代数</b>（选填，形如 P3），共四项。
 *
 * <p>★ <b>刻意不复用内部的 {@code SampleSubmitBo}，也不复用 {@link ExtSampleSubmitBo}</b>：
 * 前者带 {@code internalNo} / {@code receiveDate} / {@code hasViabilityReport} /
 * {@code operatorName} 等收样段字段（accept 第 3 条用它们断「夹带不生效」），
 * 后者带 {@code donorName} / {@code hospitalNo} / {@code tissueType} 等组织样本专属字段
 * （accept 第 3 条用它们断「借组织样本的口改类器官样本」）。
 * 本类没有这些键 → 夹带的反序列化不到任何地方。
 *
 * <p>★ FIX V03：必填与格式在 service 里写库之前校验（违规 {@code code=400} + 字段级提示）：
 * 来源单位（未选单位时名称必填，≤ 100 字）、类器官类型（必填，≤ 100 字）、代数（选填，填了必须形如 P3）、
 * 备注（≤ 500 字）；
 * {@code sourceUnitId} 只能是提交人绑定的单位（FIX V01）。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "外部类器官收样送检入参")
public class ExtOrganoidSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "来源单位 id（只能是本人绑定的单位；不带时后端按单位名对上本人绑定的单位）")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称（未带 id 时必填，≤ 100 字）", maxLength = 100)
    private String sourceUnitName;

    @Schema(description = "类器官类型（必填，≤ 100 字）", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    private String organoidType;

    @Schema(description = "代数（选填，形如 P3；小写 p 自动转大写）", example = "P3")
    private String passage;

    @Schema(description = "备注（≤ 500 字）", maxLength = 500)
    private String remark;

}
