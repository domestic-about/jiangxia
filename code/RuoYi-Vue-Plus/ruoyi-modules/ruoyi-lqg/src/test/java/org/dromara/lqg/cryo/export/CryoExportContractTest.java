package org.dromara.lqg.cryo.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 导出视图与单元格格式的契约测试（CRYO-WEB-001）。
 *
 * <p>钉住三条「靠人盯会回退」的口径（不启 Spring 上下文）：
 * <ol>
 *   <li><b>11 列表头逐字、按序</b>：前面 9 列照甲方模板原件
 *       {@code _input/templates/-80冻存模板.xlsx}，<b>其后</b>追加「代数」「当前剩余/支」
 *       —— 追加列插到中间任何位置就不等。本类同时读一遍模板原件做两侧对账
 *       （读不到模板时打印原因并跳过，不让「文件不在」伪装成「口径对」）；</li>
 *   <li><b>「冻存数量/支」= 初始支数，不是剩余</b>（accept 1 counterfeit 第一条）；
 *       「暂存-80度超低温冰箱」= 是 / 否；</li>
 *   <li><b>导出视图不是实体 VO</b>：{@code CryoExportVo} 上不许出现
 *       {@code remainingQty 单独顶替 initQty} / {@code 剩余支数 / 当前位置 / 创建时间 /
 *       样本ID / 内部编号} 这些「顺手多带」的列。</li>
 * </ol>
 *
 * @author CRYO-WEB-001
 */
class CryoExportContractTest {

    /**
     * 甲方模板原件：从当前工作目录往上找 {@code _input/templates/-80冻存模板.xlsx}
     * （不写死 {@code ../../..} —— 在模块目录跑还是在工作区根跑都能找到）。
     */
    private static Path templatePath() {
        // V31（F4）：先读本模块测试资源里的副本（classpath 的 export-templates/，与 _input/templates/ 逐字节一致，
        // FixtureCopiesSyncTest 比对），只检出后端目录也能对账；找不到再按老办法往上找 _input/templates/
        java.net.URL copy = CryoExportContractTest.class.getClassLoader()
            .getResource("export-templates/-80冻存模板.xlsx");
        if (copy != null) {
            try {
                return Path.of(copy.toURI());
            } catch (java.net.URISyntaxException ignored) {
                // 落到下面的老办法
            }
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("_input").resolve("templates").resolve("-80冻存模板.xlsx");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * 甲方模板原件的 9 列（逐字，含顺序）。
     */
    private static final List<String> TEMPLATE_HEADER = List.of(
        "冻存时间", "冻存样品", "冻存数量/支", "冻存密度", "暂存-80度超低温冰箱", "冻存人",
        "-80度超低温冰箱转移至液氮时间", "液氮储存位置", "备注");

    /**
     * 允许追加在模板列<b>之后</b>的两列（顺序也是钉死的）。
     */
    private static final List<String> EXTRA_HEADER = List.of("代数", "当前剩余/支");

    /**
     * 导出视图的 11 列 = 模板 9 列 + 追加 2 列。
     */
    private static final List<String> HEADER = concat(TEMPLATE_HEADER, EXTRA_HEADER);

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    @Test
    @DisplayName("① 导出视图 11 列、表头逐字同序（模板 9 列 + 追加 2 列）；没有顺手多带的列")
    void exportViewMatchesTemplate() {
        Map<Integer, String[]> byIndex = new TreeMap<>();
        for (Field field : CryoExportVo.class.getDeclaredFields()) {
            ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
            if (annotation == null) {
                continue;
            }
            byIndex.put(annotation.index(), annotation.value());
        }
        List<String> actual = new ArrayList<>(byIndex.size());
        int expected = 0;
        for (Map.Entry<Integer, String[]> entry : byIndex.entrySet()) {
            // index 必须连续从 0 开始（缺号 = 列序错位，导出会多一列空列）
            assertEquals(expected++, entry.getKey(), "ExcelProperty index 必须从 0 连续递增");
            assertEquals(1, entry.getValue().length, "ExcelProperty value 应当只有一个列名");
            actual.add(entry.getValue()[0]);
        }
        assertEquals(HEADER, actual, "导出视图表头必须 = 模板 9 列 + 追加「代数」「当前剩余/支」");
        assertEquals(11, actual.size(), "模板 9 列 + 追加 2 列 = 11 列，不多不少");

        // ★ 追加列必须挂在模板 9 列**之后**：插到中间 = 表头顺序不等（accept 1 counterfeit）
        for (int i = 0; i < TEMPLATE_HEADER.size(); i++) {
            assertEquals(TEMPLATE_HEADER.get(i), actual.get(i), "第 " + (i + 1) + " 列必须与模板逐字一致");
        }
        assertEquals(EXTRA_HEADER, actual.subList(9, 11), "追加的两列只能跟在模板 9 列之后");

        // 「冻存数量/支」是模板第 3 列（index 2），「当前剩余/支」是追加列（index 10）——
        // 两格都在、不是拿一个顶替另一个
        assertEquals("冻存数量/支", actual.get(2));
        assertEquals("当前剩余/支", actual.get(10));

        // 顺手多带列的典型形态（本表是「冻存批次」，不是通用批次列表）
        for (String banned : List.of("剩余支数", "当前位置", "创建时间", "样本ID", "内部编号",
            "冻存数量", "当前剩余")) {
            assertTrue(!actual.contains(banned), "导出视图不该出现「" + banned + "」这一列");
        }
    }

    @Test
    @DisplayName("② 模板原件第 1 行 = 本类的 9 列常量（读不到模板时按环境缺件处理，不当成通过）")
    void headerMatchesTemplate() {
        Path template = templatePath();
        if (template == null) {
            System.out.println("[CryoExportContractTest] 跳过与模板原件的对账：往上找不到 "
                + "_input/templates/-80冻存模板.xlsx");
            return;
        }
        List<String> header = firstRowOfXlsx(template);
        assertEquals(TEMPLATE_HEADER, header, "模板 9 列必须与甲方原件第 1 行逐字同序");
    }

    @Test
    @DisplayName("③ 「冻存数量/支」导的是**初始**支数不是剩余；「暂存-80」是 是/否；日期 yyyy-MM-dd")
    void initQtyIsNotRemaining() {
        CryoBatchVo row = new CryoBatchVo();
        row.setFreezeTime(LocalDate.of(2026, 9, 2));
        row.setCryoName("T-hli01-GZ-N-P2-EM2-2e5");
        row.setInitQty(8);
        row.setRemainingQty(6);
        row.setDensity("2e5");
        row.setInMinus80("Y");
        row.setFrozenBy("李工");
        row.setPassage("P2");

        CryoExportVo vo = CryoExportService.toExportVo(row);
        assertEquals("8", vo.getInitQty(), "★ 冻存数量/支 = 初始支数（不是剩余 6），且是「8」不是「8.0」");
        assertEquals("6", vo.getRemainingQty(), "当前剩余/支 = 读时算的剩余");
        assertEquals("是", vo.getInMinus80(), "暂存 -80 = 是（不是 Y）");
        assertEquals("P2", vo.getPassage());
        assertEquals("2026-09-02", vo.getFreezeTime(), "日期 = yyyy-MM-dd");
        assertNull(vo.getToLn2Time(), "没转液氮 = 空格子（不是 null 字符串）");

        // 「否」那一档 + 液氮位置（accept 1 第二段找的那一行）
        CryoBatchVo ln2 = new CryoBatchVo();
        ln2.setCryoName("T-oco01-JC-T-P4-EM1-5e5");
        ln2.setInitQty(5);
        ln2.setRemainingQty(5);
        ln2.setInMinus80("N");
        ln2.setLn2Location("1号罐-1架-A2");
        ln2.setPassage("P4");
        CryoExportVo ln2Vo = CryoExportService.toExportVo(ln2);
        assertEquals("否", ln2Vo.getInMinus80(), "暂存 -80 = 否（不是 N）");
        assertEquals("1号罐-1架-A2", ln2Vo.getLn2Location());
        assertEquals("5", ln2Vo.getInitQty());

        // 取空的批次（初始 3 / 剩余 0）—— 两个数都得在，不能把 0 当成空
        CryoBatchVo empty = new CryoBatchVo();
        empty.setCryoName("T-hli01-GZ-N-P5-EM2-1e5");
        empty.setInitQty(3);
        empty.setRemainingQty(0);
        empty.setInMinus80("Y");
        CryoExportVo emptyVo = CryoExportService.toExportVo(empty);
        assertEquals("3", emptyVo.getInitQty());
        assertEquals("0", emptyVo.getRemainingQty(), "取空 = 「0」，不是空格子");
    }

    @Test
    @DisplayName("④ flagText：Y/N → 是/否；空 → 空格子；其它脏值原样带出；text 日期格式")
    void flagAndDateCells() {
        assertEquals("是", CryoExportService.flagText("Y"));
        assertEquals("是", CryoExportService.flagText("y"));
        assertEquals("是", CryoExportService.flagText(" Y "));
        assertEquals("否", CryoExportService.flagText("N"));
        assertNull(CryoExportService.flagText(null), "空 → 空格子");
        assertNull(CryoExportService.flagText("  "), "空白 → 空格子");
        assertEquals("X", CryoExportService.flagText("X"), "字典外的历史值原样带出");

        assertEquals("2026-09-02", CryoExportService.text(LocalDate.of(2026, 9, 2)));
        assertNull(CryoExportService.text(null), "空 → 空格子");

        // ★ 计数格子必须是「8」而不是「8.0」：写成数值型时 FastExcel 落 <v>8.0</v>，
        //   对账脚本读回来是 8.0，与模板期望的 8 不等（本票实跑踩到）
        assertEquals("8", CryoExportService.numberText(8));
        assertEquals("0", CryoExportService.numberText(0), "取空 = 「0」，不是空格子");
        assertEquals("-3", CryoExportService.numberText(-3));
        assertNull(CryoExportService.numberText(null), "没有值 → 空格子");
    }

    @Test
    @DisplayName("⑤ 表头索引自检表与注解一致（给小程序导出复用）")
    void headerIndexTable() {
        Map<String, Integer> index = CryoExportService.headerIndex();
        assertEquals(11, index.size());
        int i = 0;
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(index).entrySet()) {
            assertEquals(i++, entry.getValue(), "headerIndex() 的顺序必须与模板列序 + 追加列一致");
            assertEquals(HEADER.get(entry.getValue()), entry.getKey());
        }
    }

    /**
     * 读 xml 用（namespace-aware = false：xlsx 的默认命名空间会让 {@code getElementsByTagName}
     * 一个节点都取不到 —— EMBED-WEB-001 在这一格踩过假红）。
     */
    private static javax.xml.parsers.DocumentBuilderFactory documentBuilderFactory() {
        javax.xml.parsers.DocumentBuilderFactory factory =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory;
    }

    private static org.w3c.dom.NodeList allElements(org.w3c.dom.Document document, String tag) {
        return document.getElementsByTagName(tag);
    }

    private static org.w3c.dom.NodeList allElements(org.w3c.dom.Element element, String tag) {
        return element.getElementsByTagName(tag);
    }

    /**
     * 读 xlsx 第一个工作表的第 1 行（单元格文本，末尾空列剔除）。
     *
     * <p>★ 为什么不用 FastExcel 读：本模块的<b>测试</b>类路径里 POI 与 commons-io 的版本不配套
     * （{@code NoSuchMethodError: BoundedInputStream.builder()}，EMBED-WEB-001 实录）。
     */
    private static List<String> firstRowOfXlsx(Path file) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
            List<String> shared = sharedStrings(zip);
            java.util.zip.ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            assertTrue(sheet != null, "xlsx 里必须有 xl/worksheets/sheet1.xml");
            var document = documentBuilderFactory().newDocumentBuilder().parse(zip.getInputStream(sheet));
            org.w3c.dom.NodeList rows = allElements(document, "row");
            assertTrue(rows.getLength() > 0, "模板必须有表头行");
            org.w3c.dom.Element first = (org.w3c.dom.Element) rows.item(0);
            org.w3c.dom.NodeList cells = allElements(first, "c");
            List<String> out = new ArrayList<>(cells.getLength());
            for (int i = 0; i < cells.getLength(); i++) {
                org.w3c.dom.Element cell = (org.w3c.dom.Element) cells.item(i);
                String type = cell.getAttribute("t");
                String text = "";
                if ("inlineStr".equals(type)) {
                    org.w3c.dom.NodeList is = allElements(cell, "t");
                    if (is.getLength() > 0) {
                        text = is.item(0).getTextContent();
                    }
                } else {
                    org.w3c.dom.NodeList values = allElements(cell, "v");
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

    /**
     * {@code xl/sharedStrings.xml} 的文本表（每个 {@code <si>} 一段，拼所有 {@code <t>}）。
     */
    private static List<String> sharedStrings(java.util.zip.ZipFile zip) {
        List<String> out = new ArrayList<>();
        java.util.zip.ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return out;
        }
        try {
            var document = documentBuilderFactory().newDocumentBuilder().parse(zip.getInputStream(entry));
            org.w3c.dom.NodeList items = allElements(document, "si");
            for (int i = 0; i < items.getLength(); i++) {
                org.w3c.dom.NodeList texts = allElements((org.w3c.dom.Element) items.item(i), "t");
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

}
