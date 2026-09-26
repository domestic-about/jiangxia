package org.dromara.lqg.ocr;

import org.dromara.lqg.ocr.domain.OcrFieldParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #203：常见标签组合下「值到哪里为止」（独立验收 V10 口径 1 的前置修复）。
 *
 * <p>覆盖面按修复单：姓名、性别、年龄、住院号、床号、科室、组织类型七个标签的组合，
 * 全角 / 半角冒号、半角 / 全角空格、同一行与换行分隔（含「标签独占一行、值在下一行」）。
 * 其中床号、科室不是预填字段 —— 它们只能当边界，自己的值不许出现在结果里。
 *
 * <p>与 {@link OcrFieldParserFixtureTest}（夹具逐例、宁缺毋滥）互补：夹具钉「不许猜」，
 * 本类钉「该切开的要切开」。纯函数、不起容器。
 *
 * @author F4（#203）
 */
class OcrFieldParserLabelComboTest {

    private static final Set<String> UNITS = Set.of("A 医院", "B 大学");

    private static Map<String, String> parse(String... lines) {
        return OcrFieldParser.parse(List.of(lines), UNITS);
    }

    private static Map<String, String> expect(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    // ── #203 原样复现 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#203：姓名:张三 床号:12 → 姓名是「张三」，不是「张三床号」；床号不产字段")
    void nameStopsBeforeBedNo() {
        assertEquals(expect("donorName", "张三"), parse("姓名:张三 床号:12"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "姓名:张三 床号:12",       // 半角冒号 + 半角空格
        "姓名：张三 床号：12",      // 全角冒号
        "姓名：张三　床号：12",     // 全角空格（U+3000）
        "姓名:张三\t床号:12",      // 制表符
        "姓名:张三床号:12",        // 没有空白：靠停止标签「床号」截断
        "姓名 : 张三  床号 : 12",   // 冒号两侧有空格
        "姓 名：张 三 床 号：12"    // 标签与值都带 OCR 的字间空格
    })
    @DisplayName("姓名 + 床号：各种冒号与空白组合，姓名都只到「张三」")
    void nameAndBedNoAcrossSeparators(String line) {
        assertEquals(expect("donorName", "张三"), parse(line), "输入：" + line);
    }

    // ── 七个标签同一行 ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "姓名:张三 性别:男 年龄:45岁 住院号:ZY20260917 床号:12 科室:肝胆外科 组织类型:肝组织",
        "姓名：张三　性别：男　年龄：45岁　住院号：ZY20260917　床号：12　科室：肝胆外科　组织类型：肝组织",
        "姓名：张三 性别:男  年龄: 45 岁 住院号：ZY20260917  床号:12 科室：肝胆外科 组织类型:肝组织",
        "姓名:张三性别:男年龄:45岁住院号:ZY20260917床号:12科室:肝胆外科组织类型:肝组织"
    })
    @DisplayName("七个标签挤在一行（冒号全角 / 半角、空白有 / 无）：五个字段各归各位，床号与科室不出现")
    void allSevenLabelsOnOneLine(String line) {
        assertEquals(expect("donorName", "张三", "gender", "male", "age", "45",
            "hospitalNo", "ZY20260917", "tissueType", "肝组织"), parse(line), "输入：" + line);
    }

    @Test
    @DisplayName("七个标签换行分隔：一行一个，或一条文本里带换行符，结果相同")
    void allSevenLabelsSeparatedByNewlines() {
        Map<String, String> want = expect("donorName", "李四", "gender", "female", "age", "62",
            "hospitalNo", "ZY-0000077", "tissueType", "结肠组织");
        assertEquals(want, parse("姓名：李四", "性别：女", "年龄：62岁", "住院号：ZY-0000077",
            "床号：3", "科室：胃肠外科", "组织类型：结肠组织"));
        assertEquals(want, parse("姓名：李四\n性别：女\n年龄：62岁\n住院号：ZY-0000077\n床号：3\n科室：胃肠外科\n组织类型：结肠组织"));
        assertEquals(want, parse("姓名:李四 性别:女\r\n年龄:62 住院号:ZY-0000077\r\n床号:3 科室:胃肠外科\n组织类型:结肠组织"));
    }

    @Test
    @DisplayName("标签独占一行、值在下一行（表格型单据）：只收单个干净片段")
    void labelOnOneLineValueOnNext() {
        assertEquals(expect("donorName", "张三", "gender", "female", "age", "33"),
            parse("姓名：", "张三", "性别：", "女", "年龄：", "33岁", "床号：", "12", "科室：", "肝胆外科"));
        // 下一行本身就是另一个字段 → 姓名没有值（不猜），性别照常认
        assertEquals(expect("gender", "male"), parse("姓名：", "性别：男"));
        // 下一行有空白 / 是整句 → 不当值（宁缺毋滥）
        assertFalse(parse("姓名：", "张 三 男 45岁").containsKey("donorName"));
        // 下一行是停止标签开头 → 不当值
        assertTrue(parse("姓名：", "床号12").isEmpty());
        // 下一行是单位名 → 按单位认，不当姓名
        assertEquals(expect("sourceUnitName", "A 医院"), parse("姓名：", "A 医院"));
    }

    // ── 顺序与相邻关系 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("床号 / 科室在前、在中间、紧贴着（无空白）：都只当边界")
    void stopLabelsInAnyPosition() {
        assertEquals(expect("donorName", "李四"), parse("床号:12 姓名:李四 科室:骨科"));
        assertEquals(expect("tissueType", "结肠组织"), parse("科室：肝胆外科 组织类型：结肠组织 床号：3"));
        assertEquals(expect("tissueType", "肝组织"), parse("组织类型:肝组织床号:12"));
        assertEquals(expect("hospitalNo", "ZY0000009"), parse("住院号:ZY0000009科室:外科"));
        assertEquals(expect("hospitalNo", "ZY0000002"), parse("住院号：ZY0000002 床号：12 科室：肝胆外科"));
        assertEquals(expect("gender", "male", "age", "45"), parse("科室:肝胆外科 性别:男 床号:8 年龄:45"));
    }

    @Test
    @DisplayName("松散形状（标签与值之间只有空格、没有冒号）同样按标签切")
    void looseShapeWithStopLabels() {
        assertEquals(expect("donorName", "张三", "gender", "male", "age", "45"),
            parse("姓名 张三 性别 男 年龄 45 床号 12"));
        assertEquals(expect("donorName", "王五"), parse("姓 名：王 五 床 号：7"));
        assertEquals(expect("donorName", "张三", "gender", "male"), parse("姓名：张 三 性别：男"));
        assertEquals(expect("donorName", "张三"), parse("姓名 张三 床号:12"));
    }

    // ── 停止标签的值永远不进结果 ─────────────────────────────────────────────────

    @Test
    @DisplayName("床号 / 科室 / 病理号的值再像字段也不认（宁缺毋滥）")
    void stopLabelValuesNeverBecomeFields() {
        assertTrue(parse("床号:12").isEmpty());
        assertTrue(parse("科室：ZY12345").isEmpty(), "科室的值长得像住院号也不许认");
        assertTrue(parse("病理号：P202609001").isEmpty());
        assertTrue(parse("床号：12 科室：肝胆外科").isEmpty());
        assertFalse(parse("标本类型：组织").containsKey("tissueType"),
            "「标本类型」是停止标签，不能读成组织类型标签「标本」+ 值「类型组织」");
        assertFalse(parse("标本类型 组织").containsKey("tissueType"));
    }

    @Test
    @DisplayName("字段没有值 / 值不合法：不猜")
    void emptyOrInvalidValuesAreDropped() {
        assertEquals(expect("gender", "male"), parse("姓名:性别:男"), "姓名冒号后直接是下一个标签 → 姓名没有值");
        assertFalse(parse("姓名:12345").containsKey("donorName"), "数字不是姓名");
        assertFalse(parse("姓名缩写:ZS").containsKey("donorName"), "不认识的标签「姓名缩写」不能拆成「姓名」+ 值");
        assertEquals(expect("donorName", "王五"), parse("编号:A12 姓名:王五"), "不认识的标签连同它的值跳过");
        assertEquals(expect("donorName", "王五"), parse("编号:A12姓名:王五"));
        assertTrue(parse("日期：2026-09-17 10:30").isEmpty(), "日期与时间里的数字不是年龄 / 住院号");
    }

}
