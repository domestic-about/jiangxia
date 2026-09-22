package org.dromara.lqg.sample.export;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「类器官收样记录」导出视图（{@code POST /lqg/sample/export/organoid}，FLOW:F-SAMPLE-02.step5）。
 *
 * <p>★★ <b>与 {@link SampleTissueExportVo} 是同一张 {@code t_lqg_sample} 的两个视图</b>
 * （ADR-0010：一张表承载两种收样记录）：组织样本导 14 列，类器官导 7 列，<b>列集不同</b>
 * （类器官没有供体段、没有固定 / 质控表 / 备注）。拿 tissue 的 VO 导 organoid
 * → 表头与 {@code _input/templates/类器官收样记录模板.xlsx} 逐字不等红；反过来也一样。
 *
 * <p>★ <b>7 列表头逐字、按序照模板原件</b>：
 * <pre>
 * 来源单位 / 类器官类型 / 收样日期 / 内部编号 / 处理时间 / 细胞活率报告 / 操作人
 * </pre>
 *
 * <p>★ 值口径与 {@link SampleTissueExportVo} 共用一套纯函数（都在 {@link SampleExportService}）：
 * 「细胞活率报告」导<b>「有 / 无」</b>、收样日期 {@code yyyy-MM-dd}、
 * 处理时间 {@code yyyy-MM-dd HH:mm:ss}、内部编号待核验为空。
 *
 * @author SAMPLE-EXPORT-001
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "类器官收样记录导出视图（7 列，与甲方模板逐字同序）")
public class SampleOrganoidExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "来源单位", index = 0)
    @Schema(description = "来源单位名称快照；自填单位名时是自填值")
    private String sourceUnitName;

    @ExcelProperty(value = "类器官类型", index = 1)
    private String organoidType;

    @ExcelProperty(value = "收样日期", index = 2)
    @Schema(description = "yyyy-MM-dd")
    private String receiveDate;

    @ExcelProperty(value = "内部编号", index = 3)
    @Schema(description = "待核验 / 无效的外部样本还没有内部编号 → 空格子")
    private String internalNo;

    @ExcelProperty(value = "处理时间", index = 4)
    @Schema(description = "yyyy-MM-dd HH:mm:ss")
    private String processTime;

    @ExcelProperty(value = "细胞活率报告", index = 5)
    @Schema(description = "有 / 无；没选留空")
    private String hasViabilityReport;

    @ExcelProperty(value = "操作人", index = 6)
    private String operatorName;

}
