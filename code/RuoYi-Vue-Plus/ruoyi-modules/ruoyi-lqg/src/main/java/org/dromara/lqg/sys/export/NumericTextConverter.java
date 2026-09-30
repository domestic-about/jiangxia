package org.dromara.lqg.sys.export;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.converters.WriteConverterContext;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.data.WriteCellData;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 「本质是数量、但库里存成文本」的列：纯数字就写成<b>数字单元格</b>，否则原样写文本。
 *
 * <p>★ 2026-09-30 飞书「小程序」第 15 行截图：年龄一列每格左上角都有绿三角（Excel 提示「以文本形式存储的数字」），
 * 也没法求和 / 排序。年龄在库里是文本（模板允许写「3月龄」这类），所以不能整列改类型，
 * 只能逐格判断：{@code 52} → 数字 52；{@code 3月龄} → 照旧是文本。
 *
 * <p>★ 只挂在「年龄 / 冻存数量 / 当前剩余」这类数量列上（{@code @ExcelProperty(converter = …)}），
 * <b>不要</b>挂到住院号、内部编号这类编号列：编号是标识不是数量，{@code 0012} 变成 12 就错了。
 */
public class NumericTextConverter implements Converter<String> {

    /** 整数或小数（不收科学计数法、不收前导 0 的多位数，免得把编号误判成数字） */
    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9]\\d{0,14})(\\.\\d+)?");

    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<String> context) {
        String value = context.getValue();
        if (value == null) {
            return new WriteCellData<>("");
        }
        String trimmed = value.trim();
        if (NUMBER.matcher(trimmed).matches()) {
            return new WriteCellData<>(new BigDecimal(trimmed));
        }
        return new WriteCellData<>(value);
    }
}
