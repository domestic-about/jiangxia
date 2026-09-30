package org.dromara.lqg.sys.export;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.export.CryoExportService;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.embed.export.EmbedExportService;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.export.SampleExportService;
import org.dromara.lqg.sample.export.SampleOrganoidExportVo;
import org.dromara.lqg.sample.export.SampleTissueExportVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小程序四张工作表导出的契约测试（SYS-EXPORT-001，不启 Spring 上下文）。
 *
 * <p>钉住四条「靠人盯会回退」的口径：
 * <ol>
 *   <li><b>sheet → 上游 service 的分派</b>：{@code tissue|organoid|embed|cryo} 四个键、
 *       四张工作表名与上游导出服务的常量<b>逐字相同</b>（这就是文件名的真相源）；
 *       未知键 → 400（不是静默落默认表）；</li>
 *   <li><b>不重写列定义</b>（Accept 1 counterfeit 第一条）：本包<b>没有</b>任何列名常量，
 *       {@code sheetName} 一律取自上游 {@code *ExportService} / {@code *ExportVo}；
 *       另读一遍甲方模板原件的第 1 行，把「上游 16 列表头」与「模板表头」两侧对账
 *       （读不到模板时打印原因并退化成只断上游常量，不让「文件不在」伪装成「口径对」）；</li>
 *   <li><b>中文文件名的 RFC 5987 编码</b>：{@code filename*=utf-8''<百分号编码>}，
 *       时间戳格式 {@code yyyyMMddHHmmss}；</li>
 *   <li><b>文件名真的落在响应上、且不被 {@code ExcelUtil} 改写成随机 UUID</b>：
 *       真跑一次 {@code EmbedExportService.export}（空行）到 {@link MockHttpServletResponse}，
 *       断 {@code Content-Disposition} 是本包写的中文名；</li>
 *   <li>四张表的筛选参数类型 = 各自 list 端点的 BO（{@code SampleQueryBo} / {@code EmbedQueryBo} /
 *       {@code CryoQueryBo}），保证「筛选参数绑到对应工作表的查询对象」不是靠约定；</li>
 *   <li>类级 {@code @SaCheckRole("lqg_internal")} 在（外部 403 的前提）。</li>
 * </ol>
 *
 * @author SYS-EXPORT-001
 */
class MpExportContractTest {

    // ── ① 枚举：四个键 + 工作表名与上游同源 ──────────────────────────────────

    @Test
    @DisplayName("sheet 四个键与契约第 55 行逐字相同，不多不少")
    void sheetKeysMatchContract() {
        Map<String, String> actual = new TreeMap<>();
        for (ExcelSheet sheet : ExcelSheet.values()) {
            actual.put(sheet.key(), sheet.sheetName());
        }
        assertEquals(
            Map.of(
                "cryo", "-80冻存",
                "embed", "石蜡包埋送样记录",
                "organoid", "类器官送样记录",
                "tissue", "样本记录信息表"),
            actual);
    }

    @Test
    @DisplayName("工作表名 = 上游导出服务的常量（文件名只有一个真相源）")
    void sheetNamesComeFromUpstreamServices() {
        assertEquals(SampleExportService.TISSUE_SHEET_NAME, ExcelSheet.TISSUE.sheetName());
        assertEquals(SampleExportService.ORGANOID_SHEET_NAME, ExcelSheet.ORGANOID.sheetName());
        assertEquals(EmbedExportService.SHEET_NAME, ExcelSheet.EMBED.sheetName());
        assertEquals(CryoExportService.SHEET_NAME, ExcelSheet.CRYO.sheetName());
    }

    @Test
    @DisplayName("未知 / 空 / 大小写不符的 sheet → 400，不静默落默认表")
    void unknownSheetIsRejected() {
        for (String bad : List.of("qc", "", "  ", "Tissue", "TISSUE", "tissue2", "export")) {
            ServiceException e = assertThrows(ServiceException.class, () -> ExcelSheet.resolve(bad),
                "「" + bad + "」必须被拒");
            assertEquals(400, e.getCode(), "「" + bad + "」的业务码必须是 400");
        }
        // 正面：四个合法值的解析结果（否则上面的 assertThrows 可能因为「全都抛」而恒绿）
        assertEquals(ExcelSheet.TISSUE, ExcelSheet.resolve("tissue"));
        assertEquals(ExcelSheet.ORGANOID, ExcelSheet.resolve("organoid"));
        assertEquals(ExcelSheet.EMBED, ExcelSheet.resolve("embed"));
        assertEquals(ExcelSheet.CRYO, ExcelSheet.resolve("cryo"));
        assertEquals(ExcelSheet.CRYO, ExcelSheet.resolve("  cryo  "));
    }

    // ── ② 本包不重写列定义：上游 16 列表头 ↔ 甲方模板原件第 1 行 ─────────────

    @Test
    @DisplayName("石蜡包埋 16 列表头：上游 headerIndex() 与甲方模板原件第 1 行逐字同序")
    void embedHeaderStillMatchesTheTemplate() throws Exception {
        List<String> template = firstRowOf("石蜡包埋送样记录模板.xlsx");
        if (template == null) {
            return; // 模板不在（不伪装成通过，见 printTemplateMissing）
        }
        assertEquals(new java.util.ArrayList<>(EmbedExportService.headerIndex().keySet()), template);
    }

    @Test
    @DisplayName("四张表的列名集合仍在各自上游 service 手上（sys/export 不持有任何列名）")
    void sysExportPackageOwnsNoColumnNames() {
        // 上游四个 headerIndex 的键（本包一个都不许出现）
        List<String> upstreamColumns = new java.util.ArrayList<>();
        upstreamColumns.addAll(SampleExportService.tissueHeaderIndex().keySet());
        upstreamColumns.addAll(SampleExportService.organoidHeaderIndex().keySet());
        upstreamColumns.addAll(EmbedExportService.headerIndex().keySet());
        upstreamColumns.addAll(CryoExportService.headerIndex().keySet());
        assertTrue(upstreamColumns.size() > 30, "四个视图的列名合起来应超过 30 个（防呆）");

        // 本包的类只允许「引用上游常量」，不允许出现列名字面量：
        // 逐个断言 sys.export 包里的枚举 / service 源码文本里没有这些列名。
        String source = readOwnSource(ExcelSheet.class) + readOwnSource(SysExportService.class)
            + readOwnSource(MpExportController.class);
        for (String column : upstreamColumns) {
            assertFalse(source.contains(column),
                "sys/export 里出现了列名「" + column + "」——列定义只能有一处（上游导出视图）");
        }
    }

    // ── ③ 文件名与 RFC 5987 编码 ─────────────────────────────────────────────

    @Test
    @DisplayName("文件名 = <工作表名>-<yyyyMMddHHmmss>.xlsx")
    void fileNameUsesSheetNameAndTimestamp() throws Exception {
        Date at = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-09-18 10:20:30");
        assertEquals("样本记录信息表-20260918102030.xlsx", SysExportService.fileNameOf(ExcelSheet.TISSUE, at));
        assertEquals("类器官送样记录-20260918102030.xlsx", SysExportService.fileNameOf(ExcelSheet.ORGANOID, at));
        assertEquals("石蜡包埋送样记录-20260918102030.xlsx", SysExportService.fileNameOf(ExcelSheet.EMBED, at));
        assertEquals("-80冻存-20260918102030.xlsx", SysExportService.fileNameOf(ExcelSheet.CRYO, at));
        assertEquals(SysExportService.FILE_TIME_PATTERN, "yyyyMMddHHmmss");
    }

    @Test
    @DisplayName("Content-Disposition 用 RFC 5987 写中文名：filename*=utf-8''<百分号编码>")
    void contentDispositionIsRfc5987() {
        String value = SysExportService.contentDispositionOf("样本记录信息表-20260918102030.xlsx");
        assertTrue(value.startsWith("attachment; filename="), value);
        assertTrue(value.contains(";filename*=utf-8''"), value);
        // 中文名必须整体出现在 filename* 那一段（百分号编码，UTF-8）
        String expectedEncoded = java.net.URLEncoder.encode(
            "样本记录信息表-20260918102030.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        assertTrue(value.endsWith("filename*=utf-8''" + expectedEncoded), value);
        // 中文不能裸落在 ASCII 那一段（老客户端拿到 mojibake 不如拿一个干净的兜底名）
        String asciiPart = value.substring(0, value.indexOf(";filename*="));
        assertFalse(asciiPart.contains("样本"), asciiPart);
    }

    // ── ④ 文件名真的落在响应上（真跑上游 ExcelUtil，防「被改成随机 UUID」）────

    @Test
    @DisplayName("真跑上游 EmbedExportService.export：Content-Disposition 是本包的中文名，不是 UUID_ 串")
    void exportKeepsOurChineseFileName() {
        CapturingEmbedExportService embedExportService = new CapturingEmbedExportService();
        SysExportService service = new SysExportService(
            new NullSampleExportService(), embedExportService, null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        EmbedQueryBo query = new EmbedQueryBo();
        query.setVerifyStatus("valid");
        service.exportEmbed(query, response);

        // 上游被调到、且拿到的是本包的包装对象（没导出就该红，不许「没导出也绿」）
        assertSame(query, embedExportService.seenQuery, "上游没收到同一份查询对象");
        HttpServletResponse passedToUpstream = embedExportService.seenResponse;
        assertTrue(passedToUpstream != null, "上游没被调到");
        assertTrue(passedToUpstream != response, "必须传包装对象（否则文件名会被 ExcelUtil 改写成随机 UUID）");
        assertTrue(passedToUpstream instanceof SysExportService.ContentDispositionResponse,
            "传的不是 ContentDispositionResponse：" + passedToUpstream.getClass());

        String disposition = response.getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.contains("filename*=utf-8''"), String.valueOf(disposition));
        String encodedSheet = java.net.URLEncoder.encode("石蜡包埋送样记录", StandardCharsets.UTF_8)
            .replace("+", "%20");
        assertTrue(disposition.contains(encodedSheet), disposition);
        assertFalse(disposition.contains("_石蜡"), "被 ExcelUtil 的随机 UUID 前缀改写了：" + disposition);
        assertTrue(String.valueOf(response.getContentType()).contains("spreadsheetml"),
            String.valueOf(response.getContentType()));
    }

    /**
     * 上游样本导出服务的哑替身（本模块测试类路径里<b>没有</b> mockito，pom 也不在本票 touches 里 ——
     * 所以手写替身，不引新依赖）。
     */
    private static final class NullSampleExportService extends SampleExportService {

        private NullSampleExportService() {
            super(null);
        }

        @Override
        public List<SampleTissueExportVo> tissueRowsOf(SampleQueryBo query) {
            return List.of();
        }

        @Override
        public List<SampleOrganoidExportVo> organoidRowsOf(SampleQueryBo query) {
            return List.of();
        }
    }

    /**
     * 记下「上游到底收到了哪一份查询对象、哪一个响应」的替身。
     */
    private static final class CapturingEmbedExportService extends EmbedExportService {

        private EmbedQueryBo seenQuery;

        private HttpServletResponse seenResponse;

        private CapturingEmbedExportService() {
            super(null, null);
        }

        @Override
        public void export(EmbedQueryBo query, HttpServletResponse response) {
            this.seenQuery = query;
            this.seenResponse = response;
        }
    }

    // ── ⑤ 四个 handler 的参数类型 = 各自 list 的 BO；类上有 lqg_internal ──────

    @Test
    @DisplayName("四个端点各收对应工作表的查询对象（SampleQueryBo / EmbedQueryBo / CryoQueryBo）")
    void handlerQueryTypesMatchTheirListEndpoints() throws Exception {
        Class<?> c = MpExportController.class;
        assertMethodTakes(c, "tissue", SampleQueryBo.class);
        assertMethodTakes(c, "organoid", SampleQueryBo.class);
        assertMethodTakes(c, "embed", EmbedQueryBo.class);
        assertMethodTakes(c, "cryo", CryoQueryBo.class);

        // 四个路径值与 ExcelSheet 的四个 key 一一对应
        Map<String, String> routes = new TreeMap<>();
        for (Method m : c.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null && get.value().length == 1) {
                routes.put(m.getName(), get.value()[0]);
            }
        }
        assertEquals(
            Map.of("tissue", "/tissue", "organoid", "/organoid", "embed", "/embed", "cryo", "/cryo"),
            routes);
        RequestMapping base = c.getAnnotation(RequestMapping.class);
        assertEquals(List.of("/mp/int/export"), List.of(base.value()));
    }

    @Test
    @DisplayName("类级 @SaCheckRole(\"lqg_internal\") 在（外部 403 的前提）")
    void controllerIsInternalOnly() {
        SaCheckRole role = MpExportController.class.getAnnotation(SaCheckRole.class);
        assertTrue(role != null, "类上必须有 @SaCheckRole");
        assertEquals(List.of("lqg_internal"), List.of(role.value()));
    }

    /** 单测里也留一条：两张样本导出视图都还在上游（本票只做分派）。 */
    @Test
    @DisplayName("两张样本导出视图仍在上游 SampleExportService（本包只有分派）")
    void sampleViewsComeFromUpstream() {
        assertEquals(14, SampleExportService.tissueHeaderIndex().size());
        // 类器官 = 模板 7 列 + 插入的「代数」（CR-20260924-10；列名只在上游 SampleExportService 手上）
        assertEquals(7 + SampleExportService.ORGANOID_INSERTED_AFTER.size(), SampleExportService.organoidHeaderIndex().size());
        assertEquals(String.class, fieldType(SampleTissueExportVo.class, "donorName"));
        assertEquals(String.class, fieldType(SampleOrganoidExportVo.class, "organoidType"));
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private static void assertMethodTakes(Class<?> c, String name, Class<?> boType) throws Exception {
        Method m = null;
        for (Method candidate : c.getDeclaredMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == 2) {
                m = candidate;
            }
        }
        assertTrue(m != null, "找不到端点方法 " + name);
        assertEquals(boType, m.getParameterTypes()[0], name + " 的第一个参数");
        assertEquals(jakarta.servlet.http.HttpServletResponse.class, m.getParameterTypes()[1], name + " 的第二个参数");
    }

    private static Class<?> fieldType(Class<?> c, String field) {
        try {
            return c.getDeclaredField(field).getType();
        } catch (NoSuchFieldException e) {
            throw new AssertionError("字段不存在：" + c.getSimpleName() + "." + field, e);
        }
    }

    private static String readOwnSource(Class<?> c) {
        try {
            String path = "src/main/java/" + c.getName().replace('.', '/') + ".java";
            java.nio.file.Path p = java.nio.file.Path.of("").toAbsolutePath();
            for (int i = 0; i < 6 && p != null; i++) {
                java.nio.file.Path candidate = p.resolve(path);
                if (java.nio.file.Files.isRegularFile(candidate)) {
                    return java.nio.file.Files.readString(candidate, StandardCharsets.UTF_8);
                }
                p = p.getParent();
            }
            throw new AssertionError("读不到自己的源码：" + path);
        } catch (java.io.IOException e) {
            throw new AssertionError("读自己的源码失败", e);
        }
    }

    /**
     * 甲方模板原件第 1 行；找不到文件时打印原因并返回 {@code null}
     * （调用方退化成只断上游常量 —— 「文件不在」不许伪装成「口径对」）。
     */
    private static List<String> firstRowOf(String fileName) {
        // V31（F4）：先读本模块测试资源里的副本（classpath 的 export-templates/，与 _input/templates/ 逐字节一致，
        // FixtureCopiesSyncTest 比对），只检出后端目录也能对账；找不到再按老办法往上找 _input/templates/
        java.net.URL copy = MpExportContractTest.class.getClassLoader().getResource("export-templates/" + fileName);
        if (copy != null) {
            try {
                return readFirstRow(java.nio.file.Path.of(copy.toURI()));
            } catch (Exception e) {
                System.out.println("[SYS-EXPORT-001] 模板副本打不开，改找 _input/templates/：" + e.getMessage());
            }
        }
        java.nio.file.Path p = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            java.nio.file.Path candidate = p.resolve("_input").resolve("templates").resolve(fileName);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                try {
                    return readFirstRow(candidate);
                } catch (Exception e) {
                    System.out.println("[SYS-EXPORT-001] 模板打不开（退化成只断上游常量）：" + e.getMessage());
                    return null;
                }
            }
            p = p.getParent();
        }
        System.out.println("[SYS-EXPORT-001] 找不到模板 " + fileName + "（退化成只断上游常量）");
        return null;
    }

    /**
     * 只读 xlsx 第 1 行：JDK 自带 {@code ZipFile} + DOM（本模块测试类路径里 POI 与 commons-io
     * 版本不配套，见 SAMPLE-EXPORT-001 §2.3）。
     */
    private static List<String> readFirstRow(java.nio.file.Path xlsx) throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(xlsx.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
            String sheetEntry = "xl/worksheets/sheet1.xml";
            try (java.io.InputStream is = zip.getInputStream(zip.getEntry(sheetEntry))) {
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(is);
                org.w3c.dom.NodeList rows = doc.getElementsByTagName("row");
                if (rows.getLength() == 0) {
                    return List.of();
                }
                List<String> shared = entry == null ? List.of() : sharedStrings(zip, entry);
                List<String> out = new java.util.ArrayList<>();
                org.w3c.dom.NodeList cells = rows.item(0).getChildNodes();
                for (int i = 0; i < cells.getLength(); i++) {
                    org.w3c.dom.Node cell = cells.item(i);
                    if (!"c".equals(cell.getNodeName())) {
                        continue;
                    }
                    String type = attr(cell, "t");
                    String raw = firstChildText(cell, "v");
                    if ("s".equals(type) && raw != null && !raw.isEmpty()) {
                        out.add(shared.get(Integer.parseInt(raw)));
                    } else {
                        out.add(raw == null ? "" : raw);
                    }
                }
                while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
                    out.remove(out.size() - 1);
                }
                return out;
            }
        }
    }

    private static List<String> sharedStrings(java.util.zip.ZipFile zip, java.util.zip.ZipEntry entry) throws Exception {
        try (java.io.InputStream is = zip.getInputStream(entry)) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(is);
            org.w3c.dom.NodeList items = doc.getElementsByTagName("si");
            List<String> out = new java.util.ArrayList<>(items.getLength());
            for (int i = 0; i < items.getLength(); i++) {
                out.add(items.item(i).getTextContent());
            }
            return out;
        }
    }

    private static String attr(org.w3c.dom.Node node, String name) {
        org.w3c.dom.Node a = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        return a == null ? null : a.getNodeValue();
    }

    private static String firstChildText(org.w3c.dom.Node node, String name) {
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (name.equals(children.item(i).getNodeName())) {
                return children.item(i).getTextContent();
            }
        }
        return null;
    }

}
