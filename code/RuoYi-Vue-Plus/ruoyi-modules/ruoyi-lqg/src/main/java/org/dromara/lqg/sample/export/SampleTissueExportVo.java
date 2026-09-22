package org.dromara.lqg.sample.export;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「样本记录信息表」导出视图（{@code POST /lqg/sample/export/tissue}，FLOW:F-SAMPLE-02.step5）。
 *
 * <p>★★ <b>本类不是 {@code SampleVo}</b>（accept 1 的 counterfeit 第一条）：
 * 直接拿列表 VO 导，表头会变成「内部编号 / 送检单号 / 来源单位 / 类别 / 来源 / 核验状态 / … / 最后修改」，
 * 多出送检单号、核验状态、有无病理、提交人、组别等列，与甲方模板原件
 * {@code _input/templates/样本记录信息表模板.xlsx} 第 1 行逐字不等 → 红。
 * 甲方拿导出去对他们的旧台账，列名与列序变了就对不上。
 *
 * <p>★ <b>14 列表头逐字、按序照模板原件</b>（{@code @ExcelProperty(index = …)} 的顺序 = 模板列序）：
 * <pre>
 * 来源单位 / 供体姓名 / 性别 / 年龄 / 住院号 / 组织类型 / 收样日期 / 内部编号 /
 * 有无固定 / 处理时间 / 质控表 / 细胞活率报告 / 操作人 / 备注
 * </pre>
 * ★ <b>「有无病理」不进这张表</b>（模板里没有这一列）—— 它只住在 {@code t_lqg_sample.has_pathology}
 * 与工作台总表上。
 *
 * <p>★ <b>所有值都在 Java 里拼好再落格</b>（不用 {@code ExcelDictConvert}）：
 * <ul>
 *   <li>三个「有无」按钮列（有无固定 / 质控表 / 细胞活率报告）导<b>「有 / 无」</b>
 *       （字典 {@code lqg_has_none}），没选的（null）留 {@code null} = 空格子；</li>
 *   <li>性别导<b>中文</b>（字典 {@code lqg_gender}：male→男 / female→女 / unknown→未知）；</li>
 *   <li>★ 供体姓名、住院号导<b>明文</b>（ADR-0006：工作台内部人员看明文；库里是裸 Base64 密文，
 *       由 {@code SampleQueryService} 读出时解密 —— <b>不要绕过实体直接查 Map</b>，
 *       那样导出的是密文）；</li>
 *   <li>收样日期 = {@code yyyy-MM-dd}；处理时间 = {@code yyyy-MM-dd HH:mm:ss}
 *       （与 {@code SampleVo} 的 {@code @JsonFormat} 同一个格式）。</li>
 * </ul>
 *
 * @author SAMPLE-EXPORT-001
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "样本记录信息表导出视图（14 列，与甲方模板逐字同序）")
public class SampleTissueExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "来源单位", index = 0)
    @Schema(description = "来源单位名称快照；自填单位名时是自填值")
    private String sourceUnitName;

    @ExcelProperty(value = "供体姓名", index = 1)
    @Schema(description = "明文（ADR-0006 内部人员看明文）")
    private String donorName;

    @ExcelProperty(value = "性别", index = 2)
    @Schema(description = "中文：男 / 女 / 未知")
    private String gender;

    @ExcelProperty(value = "年龄", index = 3)
    @Schema(description = "文本：56 / 3月龄（模板没限定单位）")
    private String age;

    @ExcelProperty(value = "住院号", index = 4)
    @Schema(description = "明文（ADR-0006 内部人员看明文）")
    private String hospitalNo;

    @ExcelProperty(value = "组织类型", index = 5)
    private String tissueType;

    @ExcelProperty(value = "收样日期", index = 6)
    @Schema(description = "yyyy-MM-dd")
    private String receiveDate;

    @ExcelProperty(value = "内部编号", index = 7)
    @Schema(description = "待核验 / 无效的外部样本还没有内部编号 → 空格子")
    private String internalNo;

    @ExcelProperty(value = "有无固定", index = 8)
    @Schema(description = "有 / 无；没选留空")
    private String isFixed;

    @ExcelProperty(value = "处理时间", index = 9)
    @Schema(description = "yyyy-MM-dd HH:mm:ss")
    private String processTime;

    @ExcelProperty(value = "质控表", index = 10)
    @Schema(description = "有 / 无；没选留空")
    private String hasQcSheet;

    @ExcelProperty(value = "细胞活率报告", index = 11)
    @Schema(description = "有 / 无；没选留空")
    private String hasViabilityReport;

    @ExcelProperty(value = "操作人", index = 12)
    private String operatorName;

    @ExcelProperty(value = "备注", index = 13)
    private String remark;

}
