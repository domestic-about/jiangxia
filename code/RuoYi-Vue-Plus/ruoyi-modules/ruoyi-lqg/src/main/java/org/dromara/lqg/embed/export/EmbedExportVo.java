package org.dromara.lqg.embed.export;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「石蜡包埋送样记录」导出视图（FLOW:F-EMBED-01.step5，{@code POST /lqg/embed/export}）。
 *
 * <p>★★ <b>本类不是 {@code EmbedVo}</b>（accept 1 的 counterfeit 第一条）：
 * 直接拿实体 VO 导，表头会变成「石蜡块编号 / 样本ID / …」而且多出创建时间、核验状态等列，
 * 与甲方模板原件
 * {@code _input/templates/石蜡包埋送样记录模板.xlsx} 第 1 行逐字不等 → 红。
 * 甲方拿导出去对他们的旧台账，列名变了就对不上。
 *
 * <p>★ <b>16 列表头逐字、按序照模板原件</b>（{@code @ExcelProperty(index = …)} 的顺序 = 模板列序）：
 * <pre>
 * 石蜡块编号 / 样本编号 / 样本类型 / 类器官来源类型 / 组织收样时间 / 组织处理时间 /
 * 琼脂糖包埋样本时间 / 包埋人 / 脱水时间 / 琼脂糖包埋样本送样时间 / 石蜡包埋时间 / 切片时间 /
 * 染色 / mark的表达情况 / 操作人 / 备注
 * </pre>
 * ★ 第 14 列是「<b>mark的表达情况</b>」——甲方原件就是这么写的，<b>不是</b>「marker 的表达情况」，
 * 顺手「修正」一个字母就逐字比对红。
 *
 * <p>★ <b>所有值都在 Java 里拼好再落格</b>（不用 {@code ExcelDictConvert}）：
 * <ul>
 *   <li>「样本编号」= {@code EmbedVo.internalNo} = <b>所挂样本的内部编号</b>（读时带出，本表只有 sample_id）；</li>
 *   <li>染色 = 中文标签、顿号连接（{@code HE染色、IHC染色}）；「其他」= {@code 其他（Masson）}；「无染色」= {@code 无染色}；</li>
 *   <li>mark 的表达情况 = {@code Ki67：强表达；CK19：阴性}（全角冒号 + 中文分号；没有名称的只写表达）；</li>
 *   <li>日期 = {@code yyyy-MM-dd}（值本身是 {@code LocalDate}，走 {@code toString()} 就是它）。</li>
 * </ul>
 * 导出与列表<b>同一个读视图</b>（{@code EmbedQueryService}）：小程序表格页的导出
 * （{@code /mp/int/export/embed}，SYS-EXPORT-001）直接复用本类，两处文件逐列一致。
 *
 * @author EMBED-WEB-001
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "石蜡包埋送样记录导出视图（16 列，与甲方模板逐字同序）")
public class EmbedExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "石蜡块编号", index = 0)
    @Schema(description = "石蜡块编号；待核验 / 无效的外部送样为空")
    private String paraffinBlockNo;

    @ExcelProperty(value = "样本编号", index = 1)
    @Schema(description = "所挂样本的内部编号（读时带出）")
    private String internalNo;

    @ExcelProperty(value = "种属", index = 2)
    @Schema(description = "插入列（模板里没有，CR-20261009-18）：紧跟「样本编号」，读所挂样本的种属")
    private String species;

    @ExcelProperty(value = "样本类型", index = 3)
    private String sampleType;

    @ExcelProperty(value = "类器官来源类型", index = 4)
    private String organoidSourceType;

    @ExcelProperty(value = "组织收样时间", index = 5)
    private String tissueReceiveTime;

    @ExcelProperty(value = "组织处理时间", index = 6)
    private String tissueProcessTime;

    @ExcelProperty(value = "琼脂糖包埋样本时间", index = 7)
    private String agaroseEmbedTime;

    @ExcelProperty(value = "包埋人", index = 8)
    private String embedBy;

    @ExcelProperty(value = "脱水时间", index = 9)
    private String dehydrateTime;

    @ExcelProperty(value = "琼脂糖包埋样本送样时间", index = 10)
    private String agaroseSendTime;

    @ExcelProperty(value = "石蜡包埋时间", index = 11)
    private String paraffinEmbedTime;

    @ExcelProperty(value = "切片时间", index = 12)
    private String sectionTime;

    @ExcelProperty(value = "染色", index = 13)
    @Schema(description = "中文标签顿号连接；其他（具体名称）；无染色")
    private String stain;

    /**
     * ★ 甲方模板原件里就是「mark的表达情况」（没有「er」）。
     */
    @ExcelProperty(value = "mark的表达情况", index = 14)
    @Schema(description = "名称：表达，中文分号连接；没有名称的只写表达")
    private String markerExpression;

    @ExcelProperty(value = "操作人", index = 15)
    private String operatorName;

    @ExcelProperty(value = "备注", index = 16)
    private String remark;

}
