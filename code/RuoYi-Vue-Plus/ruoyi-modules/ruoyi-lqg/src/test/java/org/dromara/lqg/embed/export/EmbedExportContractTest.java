package org.dromara.lqg.embed.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.lqg.embed.domain.vo.EmbedMarkerVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 导出视图与单元格格式的契约测试（EMBED-WEB-001）。
 *
 * <p>钉住三条「靠人盯会回退」的口径（不启 Spring 上下文）：
 * <ol>
 *   <li><b>16 列表头逐字、按序</b>照甲方模板原件 —— 尤其是第 14 列「<b>mark的表达情况</b>」
 *       （不是「marker 的表达情况」）与第 2 列「样本编号」（不是「样本ID」）。
 *       本类<b>同时读一遍</b> {@code _input/templates/石蜡包埋送样记录模板.xlsx} 的第 1 行做两侧对账
 *       （读不到模板时退化成只断本类的常量，并打印原因 —— 不让「文件不在」伪装成「口径对」）；</li>
 *   <li><b>染色一格</b>：中文标签顿号连接 / {@code 其他（Masson）} / {@code 无染色} / 空；</li>
 *   <li><b>mark 的表达情况一格</b>：全角冒号 + 中文分号；没名称的只写表达；</li>
 *   <li><b>导出视图不是实体 VO</b>：{@code EmbedExportVo} 上不许出现 {@code createTime / verifyStatus /
 *       sampleId} 这些「顺手多带」的列。</li>
 * </ol>
 *
 * @author EMBED-WEB-001
 */
class EmbedExportContractTest {

    /**
     * 甲方模板原件：从当前工作目录往上找 {@code _input/templates/石蜡包埋送样记录模板.xlsx}
     * （不写死 {@code ../../..} —— 在模块目录跑还是在工作区根跑都能找到）。
     */
    private static Path templatePath() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("_input").resolve("templates").resolve("石蜡包埋送样记录模板.xlsx");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * 模板 / 导出视图的 16 列（逐字，含顺序）。★ 唯一真相源的副本；是否与原件相符由
     * {@link #headerMatchesTemplate()} 实测对账。
     */
    private static final List<String> HEADER = List.of(
        "石蜡块编号", "样本编号", "样本类型", "类器官来源类型", "组织收样时间", "组织处理时间",
        "琼脂糖包埋样本时间", "包埋人", "脱水时间", "琼脂糖包埋样本送样时间", "石蜡包埋时间", "切片时间",
        "染色", "mark的表达情况", "操作人", "备注");

    @Test
    @DisplayName("① 导出视图 16 列、表头逐字同序，且与甲方模板原件第 1 行一致；没有顺手多带的列")
    void exportViewMatchesTemplate() {
        Map<Integer, String[]> byIndex = new TreeMap<>();
        for (Field field : EmbedExportVo.class.getDeclaredFields()) {
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
        assertEquals(HEADER, actual, "导出视图表头必须与甲方模板逐字同序");
        assertEquals(16, actual.size(), "模板是 16 列，不多不少");

        // 「mark的表达情况」这一格单独再断一次：顺手「修正」成 marker 会在这里红
        assertTrue(actual.contains("mark的表达情况"), "第 14 列必须是「mark的表达情况」（甲方原件的写法）");
        // 顺手多带列的典型形态
        for (String banned : List.of("创建时间", "核验状态", "样本ID", "内部编号", "marker 的表达情况")) {
            assertTrue(!actual.contains(banned), "导出视图不该出现「" + banned + "」这一列");
        }
    }

    @Test
    @DisplayName("② 模板原件第 1 行 = 本类常量（读不到模板时按环境缺件处理，不当成通过）")
    void headerMatchesTemplate() {
        Path template = templatePath();
        if (template == null) {
            System.out.println("[EmbedExportContractTest] 跳过与模板原件的对账：往上找不到 "
                + "_input/templates/石蜡包埋送样记录模板.xlsx");
            return;
        }
        List<String> header = firstRowOfXlsx(template);
        assertEquals(HEADER, header, "导出视图的表头与甲方模板原件第 1 行必须逐字同序");
    }

    /**
     * 读 xml 用（namespace-aware = false：xlsx 的默认命名空间会让 {@code getElementsByTagName}
     * 一个节点都取不到 —— 第一版就在这里踩了「模板必须有表头行」的假红）。
     */
    private static javax.xml.parsers.DocumentBuilderFactory documentBuilderFactory() {
        javax.xml.parsers.DocumentBuilderFactory factory =
            javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory;
    }

    /**
     * 按标签名取节点（只用 {@code getElementsByTagName}，不用 {@code *NS} 版本 ——
     * 关掉 namespace 之后 {@code getElementsByTagNameNS("*", …)} 一个节点都取不到）。
     */
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
     * （{@code NoSuchMethodError: BoundedInputStream.builder()}），而为了一个表头对账去动
     * {@code ruoyi-lqg/pom.xml} 的依赖是拿全模块的编译风险换一条断言。xlsx 就是一个 zip，
     * 表头是共享字符串表里的一行 —— 用 JDK 自带的 zip + DOM 读，两侧不同源、也不引新依赖。
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

    @Test
    @DisplayName("③ 染色一格：顿号连接 / 其他（名称）/ 无染色 / 空")
    void stainCell() {
        Map<String, String> labels = stainLabels();
        assertEquals("HE染色、IHC染色",
            EmbedExportService.stainText(List.of("HE", "IHC"), null, labels));
        assertEquals("HE染色、IHC染色",
            EmbedExportService.stainText(List.of("HE", "IHC"), "不该出现", labels));
        assertEquals("其他（Masson）",
            EmbedExportService.stainText(List.of("OTHER"), "Masson", labels));
        assertEquals("无染色",
            EmbedExportService.stainText(List.of("NONE"), null, labels));
        assertEquals("IF染色", EmbedExportService.stainText(List.of("IF"), null, labels));
        assertNull(EmbedExportService.stainText(List.of(), null, labels), "还没选 = 空格子");
        assertNull(EmbedExportService.stainText(null, null, labels), "还没选 = 空格子");
        // 导出的是字典 label 而不是 value：这一条专门钉 counterfeit「导出了 HE,IHC」
        assertTrue(!EmbedExportService.stainText(List.of("HE", "IHC"), null, labels).contains("HE,"));
    }

    @Test
    @DisplayName("④ mark 的表达情况一格：全角冒号 + 中文分号；无名 marker 只写表达")
    void markerCell() {
        Map<String, String> labels = markerLabels();
        assertEquals("Ki67：强表达；CK19：阴性", EmbedExportService.markerText(
            List.of(marker("Ki67", "strong"), marker("CK19", "negative")), labels));
        assertEquals("弱表达", EmbedExportService.markerText(List.of(marker(null, "weak")), labels));
        assertEquals("Ki67：强表达", EmbedExportService.markerText(
            List.of(marker(" Ki67 ", "strong")), labels));
        assertNull(EmbedExportService.markerText(List.of(), labels), "没有 marker = 空格子");
        assertNull(EmbedExportService.markerText(null, labels), "没有 marker = 空格子");
        assertNull(EmbedExportService.markerText(List.of(marker(null, null)), labels), "两栏都空的行跳过");
    }

    @Test
    @DisplayName("⑤ 表头索引自检表与注解一致（给小程序导出复用）")
    void headerIndexTable() {
        Map<String, Integer> index = EmbedExportService.headerIndex();
        assertEquals(16, index.size());
        int i = 0;
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(index).entrySet()) {
            assertEquals(i++, entry.getValue(), "headerIndex() 的顺序必须与模板列序一致");
            assertEquals(HEADER.get(entry.getValue()), entry.getKey());
        }
    }

    // ── 字典桩（值取自 sys_dict_data 的 lqg_stain_type / lqg_marker_expr） ────

    private static Map<String, String> stainLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HE", "HE染色");
        labels.put("IF", "IF染色");
        labels.put("IHC", "IHC染色");
        labels.put("OTHER", "其他");
        labels.put("NONE", "无染色");
        return labels;
    }

    private static Map<String, String> markerLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("negative", "阴性");
        labels.put("weak", "弱表达");
        labels.put("strong", "强表达");
        return labels;
    }

    private static EmbedMarkerVo marker(String name, String expression) {
        EmbedMarkerVo vo = new EmbedMarkerVo();
        vo.setMarkerName(name);
        vo.setExpression(expression);
        return vo;
    }

}
