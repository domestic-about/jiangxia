package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部「类器官收样记录」送检入参（{@code POST /mp/ext/organoid} / {@code PUT /mp/ext/organoid/{id}}）。
 *
 * <p>★ 按 {@code doc/api-contract.md} 第 53 行与 {@code CR-20260917-05}：外部填类器官只填
 * <b>来源单位（id 或名称）、类器官类型、备注</b>三项 —— 比组织样本还少。
 *
 * <p>★ <b>刻意不复用内部的 {@code SampleSubmitBo}，也不复用 {@link ExtSampleSubmitBo}</b>：
 * 前者带 {@code internalNo} / {@code receiveDate} / {@code hasViabilityReport} /
 * {@code operatorName} 等收样段字段（accept 第 3 条用它们断「夹带不生效」），
 * 后者带 {@code donorName} / {@code hospitalNo} / {@code tissueType} 等组织样本专属字段
 * （accept 第 3 条用它们断「借组织样本的口改类器官样本」）。
 * 本类没有这些键 → 夹带的反序列化不到任何地方。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "外部类器官收样送检入参")
public class ExtOrganoidSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "来源单位 id（选了列表项时用）")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称（列表里没有时自填）")
    private String sourceUnitName;

    @Schema(description = "类器官类型")
    private String organoidType;

    @Schema(description = "备注")
    private String remark;

}
