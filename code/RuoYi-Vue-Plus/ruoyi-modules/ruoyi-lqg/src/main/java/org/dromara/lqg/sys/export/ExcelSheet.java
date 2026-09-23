package org.dromara.lqg.sys.export;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.export.CryoExportService;
import org.dromara.lqg.embed.export.EmbedExportService;
import org.dromara.lqg.sample.export.SampleExportService;

/**
 * 小程序表格页「导出 Excel」的四张工作表（doc/api-contract.md 第 55 行）。
 *
 * <pre>
 * GET /mp/int/export/{sheet}    sheet ∈ tissue | organoid | embed | cryo（未知值 400）
 * </pre>
 *
 * <p>★★ <b>这里一个列名都不写</b>（SYS-EXPORT-001 §2 / Accept 1 的 counterfeit 第一条）：
 * 四张表的表头、列序、单元格格式化全部是**上游已钉死的导出视图**
 * （{@link SampleExportService} 的两张、{@link EmbedExportService}、{@code CryoExportService}），
 * 本枚举只回答「哪一个 sheet 交给哪一个 service、落到哪一个 {@code sheetName}」。
 * 在 sys 包里再写一份列定义 = 两份真相源，迟早漂。
 *
 * <p>★ <b>{@code sheetName} 与上游的导出服务同名</b>：它就是 xlsx 的工作表名，
 * 也是下游 {@code Content-Disposition} 的文件名主干（{@code <工作表名>-<yyyyMMddHHmmss>.xlsx}）——
 * 「小程序下载下来的文件叫什么」与「工作台导出的文件叫什么」同一份口径。
 *
 * <p>★ 未知值一律 {@link ServiceException} + 码 <b>400</b>（不是 404、不是静默落默认表）：
 * 若依把 404 / 403 / 500 全包进响应体，所以验收断的是**业务码**
 * （{@code api.sh --bizcode}）。
 *
 * @author SYS-EXPORT-001
 */
public enum ExcelSheet {

    /**
     * 「样本记录信息表」（{@code t_lqg_sample}, {@code sample_kind='tissue'}）。
     */
    TISSUE("tissue", SampleExportService.TISSUE_SHEET_NAME),

    /**
     * 「类器官收样记录」（同一张样本表的另一个视图，{@code sample_kind='organoid'}）。
     */
    ORGANOID("organoid", SampleExportService.ORGANOID_SHEET_NAME),

    /**
     * 「石蜡包埋送样记录」。
     */
    EMBED("embed", EmbedExportService.SHEET_NAME),

    /**
     * 「-80 冻存」。
     */
    CRYO("cryo", CryoExportService.SHEET_NAME);

    private final String key;

    private final String sheetName;

    ExcelSheet(String key, String sheetName) {
        this.key = key;
        this.sheetName = sheetName;
    }

    /**
     * 路径里的 {@code sheet} 值（契约第 55 行的四个字面量）。
     */
    public String key() {
        return key;
    }

    /**
     * 工作表名 = 导出文件名主干（与上游导出服务的常量逐字相同）。
     */
    public String sheetName() {
        return sheetName;
    }

    /**
     * {@code sheet} 路径值 → 枚举。
     *
     * <p>未知 / 空 / 大小写不符（{@code "Tissue"}）一律 400：大小写不敏感会把
     * 「拼错的那一个」静默接到一张表上，正是 ticket counterfeit 点名的形态。
     *
     * @param raw 路径里的 sheet 值
     * @throws ServiceException 未知工作表（码 400）
     */
    public static ExcelSheet resolve(String raw) {
        String value = raw == null ? "" : raw.trim();
        for (ExcelSheet sheet : values()) {
            if (sheet.key.equals(value)) {
                return sheet;
            }
        }
        throw new ServiceException("不认识的工作表：" + value + "（只支持 tissue / organoid / embed / cryo）", 400);
    }

}
