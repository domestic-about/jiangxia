package org.dromara.lqg.cryo.export;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.lqg.sys.export.NumericTextConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 「-80 冻存」导出视图（FLOW:F-CRYO-01.step5，{@code POST /lqg/cryo/batch/export}）。
 *
 * <p>★★ <b>本类不是 {@code CryoBatchVo}</b>（accept 1 的 counterfeit 第一条）：
 * 直接拿实体 VO 导，表头会变成「冻存样品 / 样本ID / 剩余支数 / 当前位置 / 创建时间 …」，
 * 与甲方模板原件 {@code _input/templates/-80冻存模板.xlsx} 第 1 行逐字不等 → 红。
 * 甲方拿导出去对纸质记录，列名变了就对不上。
 *
 * <p>★★ <b>列 = 模板 9 列 + 追加 2 列</b>（权威 FLOW:F-CRYO-01.step5：「导出『-80 冻存』9 列，
 * 表头与列序和模板逐字一致；其后追加两列『代数』『当前剩余/支』」）：
 * <pre>
 * 冻存时间 / 冻存样品 / 冻存数量/支 / 冻存密度 / 暂存-80度超低温冰箱 / 冻存人 /
 * -80度超低温冰箱转移至液氮时间 / 液氮储存位置 / 备注             ← 模板原件 9 列，逐字同序
 * 代数 / 当前剩余/支 / 种属                                     ← 只许追加在模板列之后（种属：CR-20261009-18）
 * </pre>
 * ★ 追加列<b>只能挂在第 9 列之后</b>：插到中间任何一个位置表头顺序就不等（accept 1 counterfeit）。
 *
 * <p>★ <b>「冻存数量/支」导的是初始支数（{@code initQty}），不是剩余</b>
 * （ticket §0 口径复述 1）：甲方拿导出去对纸质记录，对的是当初冻了几支；
 * 剩余另起一列 {@code 当前剩余/支}（读时算 {@code init_qty + SUM(未删流水 delta)}，
 * ADR-0010：批次表上连剩余列都没有）。
 *
 * <p>★ 所有值都在 Java 里拼好再落格（不用 {@code ExcelDictConvert}）：
 * <ul>
 *   <li>「暂存-80度超低温冰箱」= <b>是 / 否</b>（{@code in_minus80} 是 Y/N），不是 {@code Y}/{@code N}；
 *       模板上这一格写的是「是 否（按钮）」（accept 1 counterfeit 最后一条：工作台上它也用
 *       {@code SegButtons} 两个按钮，不是开关）；</li>
 *   <li>日期 = {@code yyyy-MM-dd}（冻存时间 / 转液氮时间），空 → 空格子。</li>
 * </ul>
 *
 * @author CRYO-WEB-001
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "-80 冻存导出视图（模板 9 列 + 代数 + 当前剩余/支 + 种属）")
public class CryoExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "冻存时间", index = 0)
    @Schema(description = "冻存时间 yyyy-MM-dd")
    private String freezeTime;

    @ExcelProperty(value = "冻存样品", index = 1)
    @Schema(description = "冻存样品名称（手填）")
    private String cryoName;


    /**
     * ★ <b>初始</b>支数（不是当前剩余）—— 「当前剩余/支」是第 11 列。
     *
     * <p>类型是 {@code String}、不是 {@code Integer}：导出侧所有格子都在 Java 里拼成文本
     * （与 SAMPLE-EXPORT-001 / EMBED-WEB-001 同一个约定）。写成数值型时 FastExcel 落的是
     * {@code <v>8.0</v>}，甲方拿 openpyxl 之类的工具读回来就是 {@code 8.0} ——
     * 「对纸质记录」要的是 {@code 8}。
     */
    @ExcelProperty(value = "冻存数量/支", index = 2, converter = NumericTextConverter.class)
    @Schema(description = "冻存数量/支 = 初始支数（不是剩余）")
    private String initQty;

    @ExcelProperty(value = "冻存密度", index = 3)
    private String density;

    @ExcelProperty(value = "暂存-80度超低温冰箱", index = 4)
    @Schema(description = "是 / 否（由 in_minus80 的 Y / N 映射）")
    private String inMinus80;

    @ExcelProperty(value = "冻存人", index = 5)
    private String frozenBy;

    @ExcelProperty(value = "-80度超低温冰箱转移至液氮时间", index = 6)
    @Schema(description = "转移至液氮时间 yyyy-MM-dd（空 = 还在 -80）")
    private String toLn2Time;

    @ExcelProperty(value = "液氮储存位置", index = 7)
    private String ln2Location;

    @ExcelProperty(value = "备注", index = 8)
    private String remark;

    /**
     * ★ 追加列 1：代数（模板 9 列之后）。
     */
    @ExcelProperty(value = "代数", index = 9)
    @Schema(description = "代数，形如 P2（追加列，在模板 9 列之后）")
    private String passage;

    /**
     * ★ 追加列 2：当前剩余/支（读时算；与接口 / 库内独立汇总一致）。
     *
     * <p>同样是文本型（见 {@link #initQty} 的说明）；取空是 {@code "0"}，不是空格子。
     */
    @ExcelProperty(value = "当前剩余/支", index = 10, converter = NumericTextConverter.class)
    @Schema(description = "当前剩余支数（读时算：初始 + 未删流水累计）")
    private String remainingQty;

    /**
     * 追加列（模板里没有，CR-20261009-18）：所挂样本的种属，排在「当前剩余/支」之后 ——
     * 与「代数」「当前剩余/支」同一条规则，追加列只挂在模板 9 列之后。
     */
    @ExcelProperty(value = "种属", index = 11)
    @Schema(description = "所挂样本的种属；样本没填留空")
    private String species;

}
