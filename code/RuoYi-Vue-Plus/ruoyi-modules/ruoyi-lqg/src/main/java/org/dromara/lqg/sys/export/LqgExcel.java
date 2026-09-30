package org.dromara.lqg.sys.export;

import cn.idev.excel.FastExcel;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.context.SheetWriteHandlerContext;
import cn.idev.excel.write.metadata.style.WriteCellStyle;
import cn.idev.excel.write.metadata.style.WriteFont;
import cn.idev.excel.write.style.HorizontalCellStyleStrategy;
import cn.idev.excel.write.style.row.SimpleRowHeightStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.excel.convert.ExcelBigNumberConvert;
import org.dromara.common.excel.utils.ExcelUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 四张业务表（样本记录信息表 / 类器官送样记录 / 石蜡包埋送样记录 / -80 冻存）导出 Excel 的统一版式。
 *
 * <p>★ 2026-09-30 飞书「小程序」第 15、16 行：导出的表「格式和排版要规范且统一」。截图里的问题都出在
 * 若依通用的 {@code ExcelUtil.exportExcel}（它只管数据、不管版式）：
 * <ul>
 *   <li>表头宋体 14 号、正文宋体 11 号，中文显示成衬线字、和数字字体不统一 → 统一「微软雅黑」，表头 11 号加粗、正文 10 号；</li>
 *   <li>正文没有框线 → 表头与正文一律细框线；</li>
 *   <li>列宽按「字节数」估，中文 / 编号经常放不下（住院号被截） → 按显示宽度（汉字算 2、其他算 1）逐列取最长值再留余量；</li>
 *   <li>年龄、支数等存成文本，单元格左上角绿三角 → 见 {@link NumericTextConverter}（挂在具体列上）；</li>
 *   <li>另外冻结表头行，往下翻时列名一直在。</li>
 * </ul>
 *
 * <p>★ 只换版式，不动数据：列序 / 表头 / 取值仍由各自的导出视图（{@code *ExportVo}）决定，
 * 响应头与 {@code ExcelUtil} 一样（随机前缀文件名 + xlsx MIME），
 * 所以小程序那条出口外面包的 {@code ContentDispositionResponse} 照旧能把文件名改成中文。
 */
public final class LqgExcel {

    static final String FONT = "微软雅黑";

    /** 列宽的上下限（单位：Excel 字符宽度） */
    static final int MIN_WIDTH = 8;
    static final int MAX_WIDTH = 50;

    private LqgExcel() {
    }

    /** 写到 HTTP 响应（与 {@code ExcelUtil.exportExcel(list, sheetName, clazz, response)} 同一个调用方式）。 */
    public static <T> void export(List<T> rows, String sheetName, Class<T> clazz, HttpServletResponse response) {
        try {
            FileUtils.setAttachmentResponseHeader(response, ExcelUtil.encodingFilename(sheetName));
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
            write(rows, sheetName, clazz, response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("导出Excel异常");
        }
    }

    /** 写到任意输出流（单测直接写内存流再用 POI 读回来断言）。 */
    public static <T> void write(List<T> rows, String sheetName, Class<T> clazz, OutputStream os) {
        FastExcel.write(os, clazz)
            .autoCloseStream(false)
            .registerConverter(new ExcelBigNumberConvert())
            .registerWriteHandler(new HorizontalCellStyleStrategy(headStyle(), contentStyle()))
            .registerWriteHandler(new SimpleRowHeightStyleStrategy((short) 24, (short) 20))
            .registerWriteHandler(new LayoutHandler(columnWidths(rows, clazz)))
            .sheet(sheetName)
            .doWrite(rows);
    }

    // ── 样式 ─────────────────────────────────────────────────────────────────

    static WriteCellStyle headStyle() {
        WriteCellStyle style = bordered();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        style.setHorizontalAlignment(HorizontalAlignment.CENTER);
        style.setWrapped(true);
        style.setWriteFont(font((short) 11, true));
        return style;
    }

    static WriteCellStyle contentStyle() {
        WriteCellStyle style = bordered();
        style.setHorizontalAlignment(HorizontalAlignment.LEFT);
        style.setWriteFont(font((short) 10, false));
        return style;
    }

    private static WriteCellStyle bordered() {
        WriteCellStyle style = new WriteCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static WriteFont font(short size, boolean bold) {
        WriteFont font = new WriteFont();
        font.setFontName(FONT);
        font.setFontHeightInPoints(size);
        font.setBold(bold);
        return font;
    }

    // ── 列宽 ─────────────────────────────────────────────────────────────────

    /**
     * 每一列的宽度（列号 → 字符宽度）：表头与每一行取值里最宽的那个 + 2 格余量，夹在 [8, 50]。
     *
     * <p>先把数据扫一遍再写，比逐格回调里「边写边放宽」简单，且结果与写入顺序无关。
     */
    static <T> Map<Integer, Integer> columnWidths(List<T> rows, Class<T> clazz) {
        Map<Integer, Integer> widths = new TreeMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property == null || property.index() < 0) {
                continue;
            }
            field.setAccessible(true);
            int width = 0;
            for (String head : property.value()) {
                width = Math.max(width, displayWidth(head));
            }
            if (rows != null) {
                for (T row : rows) {
                    Object value;
                    try {
                        value = row == null ? null : field.get(row);
                    } catch (IllegalAccessException e) {
                        value = null;
                    }
                    if (value != null) {
                        width = Math.max(width, displayWidth(String.valueOf(value)));
                    }
                }
            }
            widths.put(property.index(), Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, width + 2)));
        }
        return widths;
    }

    /** 显示宽度：ASCII 算 1，其余（汉字、全角标点）算 2。 */
    static int displayWidth(String text) {
        if (text == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) < 128 ? 1 : 2;
        }
        return width;
    }

    /** 建好工作表后：定列宽 + 冻结表头行。 */
    static final class LayoutHandler implements SheetWriteHandler {

        private final Map<Integer, Integer> widths;

        LayoutHandler(Map<Integer, Integer> widths) {
            this.widths = widths;
        }

        @Override
        public void afterSheetCreate(SheetWriteHandlerContext context) {
            Sheet sheet = context.getWriteSheetHolder().getSheet();
            widths.forEach((column, width) -> sheet.setColumnWidth(column, width * 256));
            sheet.createFreezePane(0, 1);
        }
    }
}
