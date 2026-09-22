package org.dromara.lqg.sample.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.idev.excel.annotation.ExcelProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 两张样本导出的契约测试（SAMPLE-EXPORT-001，不启 Spring 上下文）。
 *
 * <p>钉住四条「靠人盯会回退」的口径：
 * <ol>
 *   <li><b>tissue 14 列 / organoid 7 列，表头逐字、按序</b>照甲方模板原件；
 *       本类<b>同时读一遍</b> {@code _input/templates/样本记录信息表模板.xlsx} 与
 *       {@code 类器官收样记录模板.xlsx} 的第 1 行做两侧对账
 *       （读不到模板时打印原因并跳过那一条 —— 不让「文件不在」伪装成「口径对」）；</li>
 *   <li><b>导出视图不是列表 VO</b>：不许出现「送检单号 / 核验状态 / 有无病理 / 提交人 / 组别 /
 *       最后修改」这些顺手多带的列；</li>
 *   <li><b>三个「有无」按钮列导「有 / 无」，性别导中文</b>（不是 Y / N，不是 male / female）；</li>
 *   <li><b>日期 / 时间格的格式</b>：收样日期 {@code yyyy-MM-dd}、处理时间
 *       {@code yyyy-MM-dd HH:mm:ss}；空 → 空格子。</li>
 * </ol>
 *
 * @author SAMPLE-EXPORT-001
 */
class SampleExportContractTest {

    private static final List<String> TISSUE_HEADER = List.of(
        "来源单位", "供体姓名", "性别", "年龄", "住院号", "组织类型", "收样日期", "内部编号",
        "有无固定", "处理时间", "质控表", "细胞活率报告", "操作人", "备注");

    private static final List<String> ORGANOID_HEADER = List.of(
        "来源单位", "类器官类型", "收样日期", "内部编号", "处理时间", "细胞活率报告", "操作人");

    @Test
    @DisplayName("① 两个导出视图的列数 / 列名 / 列序，且与甲方模板原件第 1 行一致")
    void exportViewsMatchTemplates() {
        assertEquals(14, annotatedHeader(SampleTissueExportVo.class).size(), "tissue 模板是 14 列");
        assertEquals(7, annotatedHeader(SampleOrganoidExportVo.class).size(), "organoid 模板是 7 列");
        assertEquals(TISSUE_HEADER, annotatedHeader(SampleTissueExportVo.class));
        assertEquals(ORGANOID_HEADER, annotatedHeader(SampleOrganoidExportVo.class));

        assertEquals(TISSUE_HEADER, firstRowOfTemplate("样本记录信息表模板.xlsx"));
        assertEquals(ORGANOID_HEADER, firstRowOfTemplate("类器官收样记录模板.xlsx"));
    }

    @Test
    @DisplayName("② 导出视图不许出现列表 VO 的列（送检单号 / 核验状态 / 有无病理 / 提交人 / 组别）")
    void noListOnlyColumns() {
        List<String> tissue = annotatedHeader(SampleTissueExportVo.class);
        for (String banned : List.of("送检单号", "核验状态", "有无病理", "提交人", "组别", "最后修改", "类别", "来源")) {
            assertTrue(!tissue.contains(banned), "「样本记录信息表」不该出现「" + banned + "」这一列");
        }
        List<String> organoid = annotatedHeader(SampleOrganoidExportVo.class);
        for (String banned : List.of("供体姓名", "性别", "年龄", "住院号", "组织类型", "有无固定", "质控表", "备注",
            "送检单号", "核验状态", "有无病理")) {
            assertTrue(!organoid.contains(banned), "「类器官收样记录」不该出现「" + banned + "」这一列");
        }
        // 两张表的列集必须不同（同一张 t_lqg_sample 的两个视图，不是一张表导两遍）
        assertTrue(!tissue.equals(organoid));
    }

    @Test
    @DisplayName("③ 按钮列导「有 / 无」、性别导中文；没选留空")
    void buttonAndGenderCells() {
        assertEquals("有", SampleExportService.flagText("Y"));
        assertEquals("无", SampleExportService.flagText("N"));
        assertEquals("有", SampleExportService.flagText(" Y "));
        assertNull(SampleExportService.flagText(null), "没选 = 空格子");
        assertNull(SampleExportService.flagText(""), "没选 = 空格子");
        assertNull(SampleExportService.flagText("   "), "没选 = 空格子");
        // counterfeit：「按钮字段导出了 Y / N」
        assertTrue(!"Y".equals(SampleExportService.flagText("Y")));
        assertTrue(!"N".equals(SampleExportService.flagText("N")));

        assertEquals("男", SampleExportService.genderText("male"));
        assertEquals("女", SampleExportService.genderText("female"));
        assertEquals("未知", SampleExportService.genderText("unknown"));
        assertNull(SampleExportService.genderText(null), "空 = 空格子");
        // counterfeit：「性别导出了 male / female」
        assertTrue(!"male".equals(SampleExportService.genderText("male")));
    }

    @Test
    @DisplayName("④ 日期与时间格：收样日期 yyyy-MM-dd、处理时间 yyyy-MM-dd HH:mm:ss、空留空")
    void dateCells() {
        assertEquals("2026-08-24", SampleExportService.dateText(LocalDate.of(2026, 8, 24)));
        assertNull(SampleExportService.dateText(null), "空日期 = 空格子（不是 \"null\"）");

        Date stamp = java.sql.Timestamp.valueOf(java.time.LocalDateTime.of(2026, 8, 24, 14, 20, 0));
        assertEquals("2026-08-24 14:20:00", SampleExportService.dateTimeText(stamp));
        assertEquals("2026-08-24", SampleExportService.dateText(stamp));
        assertNull(SampleExportService.dateTimeText(null), "空时间 = 空格子");
    }

    @Test
    @DisplayName("⑤ 表头索引自检表与注解一致（给小程序导出复用）")
    void headerIndexTables() {
        assertIndexConsistent(SampleExportService.tissueHeaderIndex(), TISSUE_HEADER);
        assertIndexConsistent(SampleExportService.organoidHeaderIndex(), ORGANOID_HEADER);
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    private static void assertIndexConsistent(Map<String, Integer> index, List<String> header) {
        assertEquals(header.size(), index.size());
        int i = 0;
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(index).entrySet()) {
            assertEquals(i++, entry.getValue(), "headerIndex() 的顺序必须与模板列序一致");
            assertEquals(header.get(entry.getValue()), entry.getKey());
        }
    }

    /**
     * 取 {@code @ExcelProperty(index = …)} 排好序的表头（顺带断 index 从 0 连续递增 ——
     * 缺号 = 列序错位，导出会多一列空列）。
     */
    private static List<String> annotatedHeader(Class<?> clazz) {
        Map<Integer, String> byIndex = new TreeMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
            if (annotation == null) {
                continue;
            }
            assertEquals(1, annotation.value().length, "ExcelProperty value 应当只有一个列名");
            byIndex.put(annotation.index(), annotation.value()[0]);
        }
        List<String> actual = new ArrayList<>(byIndex.size());
        int expected = 0;
        for (Map.Entry<Integer, String> entry : byIndex.entrySet()) {
            assertEquals(expected++, entry.getKey(), "ExcelProperty index 必须从 0 连续递增");
            actual.add(entry.getValue());
        }
        return actual;
    }

    /**
     * 甲方模板原件第 1 行；找不到文件时<b>直接失败</b>（模板是表头的唯一来源，缺件必须是红的）。
     */
    private static List<String> firstRowOfTemplate(String fileName) {
        Path template = templatePath(fileName);
        assertTrue(template != null,
            "找不到甲方模板原件 _input/templates/" + fileName + "（表头的唯一来源，缺了没法对账）");
        return firstRowOfXlsx(template);
    }

    /**
     * 从当前工作目录往上找 {@code _input/templates/<fileName>}（不写死 {@code ../../..} ——
     * 在模块目录跑还是在工作区根跑都能找到）。
     */
    private static Path templatePath(String fileName) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("_input").resolve("templates").resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * 读 xlsx 第一个工作表的第 1 行（单元格文本，末尾空列剔除）。
     *
     * <p>★ 为什么不用 FastExcel 读：本模块的<b>测试</b>类路径里 POI 与 commons-io 的版本不配套
     * （{@code NoSuchMethodError: BoundedInputStream.builder()}），而为了一个表头对账去动
     * {@code ruoyi-lqg/pom.xml} 的依赖是拿全模块的编译风险换一条断言（EMBED-WEB-001 的结论）。
     * xlsx 就是一个 zip，用 JDK 自带的 zip + DOM 读（★ namespace-aware = false，
     * 否则 {@code getElementsByTagNameNS} 一个节点都取不到）。
     */
    private static List<String> firstRowOfXlsx(Path file) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
            List<String> shared = sharedStrings(zip);
            java.util.zip.ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            assertTrue(sheet != null, "xlsx 里必须有 xl/worksheets/sheet1.xml");
            var document = builderFactory().newDocumentBuilder().parse(zip.getInputStream(sheet));
            org.w3c.dom.NodeList rows = document.getElementsByTagName("row");
            assertTrue(rows.getLength() > 0, "模板必须有表头行");
            org.w3c.dom.Element first = (org.w3c.dom.Element) rows.item(0);
            org.w3c.dom.NodeList cells = first.getElementsByTagName("c");
            List<String> out = new ArrayList<>(cells.getLength());
            for (int i = 0; i < cells.getLength(); i++) {
                org.w3c.dom.Element cell = (org.w3c.dom.Element) cells.item(i);
                String type = cell.getAttribute("t");
                String text = "";
                if ("inlineStr".equals(type)) {
                    org.w3c.dom.NodeList is = cell.getElementsByTagName("t");
                    if (is.getLength() > 0) {
                        text = is.item(0).getTextContent();
                    }
                } else {
                    org.w3c.dom.NodeList values = cell.getElementsByTagName("v");
                    if (values.getLength() > 0) {
                        String raw = values.item(0).getTextContent();
                        if ("s".equals(type)) {
                            int index = Integer.parseInt(raw.trim());
                            text = index < shared.size() ? shared.get(index) : "";
                        } else {
                            text = raw;
                        }
                    }
                }
                out.add(text == null ? "" : text);
            }
            while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
                out.remove(out.size() - 1);
            }
            return out;
        } catch (Exception e) {
            throw new AssertionError("读不了甲方模板原件 " + file + "：" + e, e);
        }
    }

    private static List<String> sharedStrings(java.util.zip.ZipFile zip) {
        List<String> out = new ArrayList<>();
        java.util.zip.ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return out;
        }
        try {
            var document = builderFactory().newDocumentBuilder().parse(zip.getInputStream(entry));
            org.w3c.dom.NodeList items = document.getElementsByTagName("si");
            for (int i = 0; i < items.getLength(); i++) {
                org.w3c.dom.NodeList texts = ((org.w3c.dom.Element) items.item(i)).getElementsByTagName("t");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < texts.getLength(); j++) {
                    sb.append(texts.item(j).getTextContent());
                }
                out.add(sb.toString());
            }
        } catch (Exception e) {
            throw new AssertionError("读不了 xl/sharedStrings.xml：" + e, e);
        }
        return out;
    }

    private static javax.xml.parsers.DocumentBuilderFactory builderFactory() {
        javax.xml.parsers.DocumentBuilderFactory factory =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory;
    }

}
