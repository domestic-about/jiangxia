package org.dromara.lqg.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.lqg.ocr.domain.OcrFieldParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析规则的**逐例**验收（accept 2 第 3 段点名本类）：
 * 夹具是 {@code doc/verify/fixtures/ocr-cases.json}（验收脚本读的权威那份）在本模块 main resources 里的
 * 逐字节副本 {@code ocr-cases.json}（V31：构建只靠后端目录；两份一致由 {@code FixtureCopiesSyncTest} 比对）。
 *
 * <p>每条用例两半，缺一不算过：
 * <ul>
 *   <li>{@code expect} 里的每个键：值<b>必须相等</b>；</li>
 *   <li>{@code absent} 里的每个键：<b>必须不存在</b>（不是「值为空」—— 空串会被前端当识别结果预填）。</li>
 * </ul>
 *
 * <p>★ 单位名白名单不查库：夹具第 01 例要 {@code A 医院} 认出来、第 02 / 05 例不许认出来，
 * 这里直接把 seed 的 active 单位名（{@code A 医院 / B 大学}）当入参 —— 解析器是纯函数，
 * 这正是它能被逐例单测的原因。
 *
 * <p>★ {@code OcrFieldParser} 是纯函数：本类<b>不起 Spring 容器</b>、不连库、不碰 Redis。
 *
 * @author OCR-IMPL-001
 */
class OcrFieldParserFixtureTest {

    /**
     * 夹具名（本模块 main resources 里的那一份，classpath 根）。
     */
    private static final String FIXTURE = "ocr-cases.json";

    /**
     * 夹具读取器：**裸 Jackson**，不用 {@code JsonUtils} —— 后者的静态初始化会向 hutool 的
     * {@code SpringUtil} 要 bean 工厂，本类是纯单测（不起容器），用它会
     * {@code ExceptionInInitializerError}。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * 夹具里 {@code sourceUnitName} 用例的 active 单位名白名单：与
     * {@code doc/verify/seed/03-unit-group.sql} 的 active 行逐字一致（{@code 已停用单位} 不在内）。
     */
    private static final Set<String> ACTIVE_UNITS = new LinkedHashSet<>(List.of("A 医院", "B 大学"));

    /**
     * 夹具全部用例（顺序即文件顺序）。
     */
    private static List<Case> cases() {
        try (InputStream in = OcrFieldParserFixtureTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "夹具 " + FIXTURE + " 必须在 classpath 上（ruoyi-lqg 的 src/main/resources/）");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Fixture fixture = MAPPER.readValue(text, Fixture.class);
            assertNotNull(fixture, "夹具 " + FIXTURE + " 解析为空");
            assertNotNull(fixture.cases, "夹具 " + FIXTURE + " 缺 cases");
            assertFalse(fixture.cases.isEmpty(), "夹具 " + FIXTURE + " 的 cases 为空");
            return fixture.cases;
        } catch (IOException e) {
            throw new AssertionError("读不了夹具 " + FIXTURE + "：" + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("夹具自带五例，用例名可读（改名要同步反思 accept 的前缀约定）")
    void fixtureIsSane() {
        List<Case> cases = cases();
        assertEquals(5, cases.size(), "夹具应是五例");
        for (Case item : cases) {
            assertNotNull(item.caseName, "用例名不能为空");
            assertTrue(item.caseName.length() >= 2,
                "用例名前两位是 StubOcrProvider 的索引键（X-Ocr-Stub-Case），不能短于 2 个字符："
                    + item.caseName);
            assertNotNull(item.rawLines, "用例 " + item.caseName + " 缺 rawLines");
        }
        assertEquals("01", cases.get(0).caseName.substring(0, 2));
        assertEquals("05", cases.get(4).caseName.substring(0, 2));
    }

    @Test
    @DisplayName("五例逐例：expect 必须相等、absent 必须不出现")
    void everyCaseParsesAsFixtureSays() {
        for (Case item : cases()) {
            Map<String, String> fields = OcrFieldParser.parse(item.rawLines, ACTIVE_UNITS);
            String where = "用例 " + item.caseName + " → " + fields;

            // ① expect：值必须相等
            Map<String, String> expect = item.expect == null ? Map.of() : item.expect;
            for (Map.Entry<String, String> want : expect.entrySet()) {
                assertTrue(fields.containsKey(want.getKey()),
                    "该出现的字段没出现：" + want.getKey() + "（" + where + "）");
                assertEquals(want.getValue(), fields.get(want.getKey()),
                    "字段值不对：" + want.getKey() + "（" + where + "）");
            }

            // ② absent：必须**不存在这个键**（空串也不行 —— 前端会拿它去预填）
            List<String> absent = item.absent == null ? List.of() : item.absent;
            for (String key : absent) {
                assertFalse(fields.containsKey(key),
                    "不该出现的字段出现了（宁缺毋滥）：" + key + "（" + where + "）");
            }

            // ③ 解析器只许产出白名单里的键，且没有空值
            for (Map.Entry<String, String> actual : fields.entrySet()) {
                assertTrue(Set.of("donorName", "gender", "age", "hospitalNo", "tissueType", "sourceUnitName")
                        .contains(actual.getKey()),
                    "解析器产出了表外的键：" + actual.getKey());
                assertNotNull(actual.getValue(), "字段值不许是 null：" + actual.getKey());
                assertFalse(actual.getValue().isEmpty(), "字段值不许是空串：" + actual.getKey());
            }

            // ④ 夹具没写 expect 也没写 absent 的键：允许出现，但必须显式化（防止夹具与实现悄悄漂开）
            for (String key : fields.keySet()) {
                assertTrue(expect.containsKey(key) || absent.contains(key),
                    "解析器多认了夹具里没声明的键：" + key + "（" + where + "）");
            }
        }
    }

    @Test
    @DisplayName("噪声用例（第 03 例）单独再钉一遍：温度与日期里的数字不许变成任何字段")
    void noiseCaseNeverGuesses() {
        Case noise = cases().stream()
            .filter(c -> c.caseName.startsWith("03"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("夹具里没有 03 号噪声用例"));
        Map<String, String> fields = OcrFieldParser.parse(noise.rawLines, ACTIVE_UNITS);
        assertTrue(fields.isEmpty(), "只有噪声时不许解析出任何字段，实际：" + fields);
    }

    @Test
    @DisplayName("单位名必须整行完全相等：相似名不硬匹配，停用单位不在白名单里")
    void unitNameIsExactMatchOnly() {
        // 相似但不相等 → 不认（第 05 例的病灶）
        assertFalse(OcrFieldParser.parse(List.of("某某市第九医院"), ACTIVE_UNITS).containsKey("sourceUnitName"));
        assertFalse(OcrFieldParser.parse(List.of("A医院"), Set.of("A 医院")).containsKey("sourceUnitName"),
            "去掉空格后的名字不许算相等（行内空格是数据的一部分）");
        // 完全相等 → 认（第 01 例）
        assertEquals("A 医院", OcrFieldParser.parse(List.of("A 医院"), ACTIVE_UNITS).get("sourceUnitName"));
        // 白名单为空 → 一定不返回
        assertFalse(OcrFieldParser.parse(List.of("A 医院"), Set.of()).containsKey("sourceUnitName"));
        assertFalse(OcrFieldParser.parse(List.of("A 医院"), null).containsKey("sourceUnitName"));
    }

    @Test
    @DisplayName("裸数字没有标签一律不认；标签值才做范围校验")
    void bareNumbersAreNeverFields() {
        Map<String, String> noise = OcrFieldParser.parse(List.of(
            "保持 2-8℃ 低温运输", "2026-09-17 10:30", "床号:12", "易碎品 轻拿轻放"), ACTIVE_UNITS);
        assertTrue(noise.isEmpty(), "无标签的裸数字/日期/床号都不许变成字段，实际：" + noise);

        // 有标签才认，且年龄要落在 1-120（而且不许把段首数字之外的数字偷偷取出来当年龄）
        assertEquals("56", OcrFieldParser.parse(List.of("年龄：56岁"), ACTIVE_UNITS).get("age"));
        assertEquals("61", OcrFieldParser.parse(List.of("年 龄 61 岁"), ACTIVE_UNITS).get("age"));
        assertFalse(OcrFieldParser.parse(List.of("年龄:0"), ACTIVE_UNITS).containsKey("age"),
            "0 不是有效年龄");
        assertFalse(OcrFieldParser.parse(List.of("年龄:121"), ACTIVE_UNITS).containsKey("age"),
            "121 超范围，不许被截成 1 或 12");
        assertFalse(OcrFieldParser.parse(List.of("年龄:2-8"), ACTIVE_UNITS).containsKey("age"),
            "2-8 不是年龄，不许被截成 2");
        assertFalse(OcrFieldParser.parse(List.of("保持 2-8℃ 低温运输"), ACTIVE_UNITS).containsKey("age"),
            "无标签的温度范围不认");
    }

    @Test
    @DisplayName("性别只认男/女并转字典 code；住院号要 5-20 位字母数字")
    void genderAndHospitalNoRules() {
        assertEquals("male", OcrFieldParser.parse(List.of("性别:男"), ACTIVE_UNITS).get("gender"));
        assertEquals("female", OcrFieldParser.parse(List.of("性 别 女"), ACTIVE_UNITS).get("gender"));
        assertFalse(OcrFieldParser.parse(List.of("性别:未知"), ACTIVE_UNITS).containsKey("gender"));

        assertEquals("ZY0000001", OcrFieldParser.parse(List.of("住院号：ZY0000001"), ACTIVE_UNITS).get("hospitalNo"));
        assertEquals("ZY-0000001", OcrFieldParser.parse(List.of("住院号码:ZY-0000001"), ACTIVE_UNITS).get("hospitalNo"));
        assertFalse(OcrFieldParser.parse(List.of("住院号:1234"), ACTIVE_UNITS).containsKey("hospitalNo"),
            "短于 5 位不是住院号");
        assertFalse(OcrFieldParser.parse(List.of("床号:12 日期:20260917"), ACTIVE_UNITS).containsKey("hospitalNo"),
            "日期落在 **不是住院号标签** 的行上 → 不认（第 04 例的床号、第 03 例的日期）");
        assertFalse(OcrFieldParser.parse(List.of("2026-09-17 10:30"), ACTIVE_UNITS).containsKey("hospitalNo"),
            "裸日期没有标签 → 不认");
    }

    @Test
    @DisplayName("同一行多个字段能各归各位（值不被下一个标签吃掉）")
    void multipleFieldsOnOneLine() {
        Map<String, String> fields = OcrFieldParser.parse(
            List.of("性别:男  年龄:56岁", "住院号:ZY0000002 床号:12"), ACTIVE_UNITS);
        assertEquals("male", fields.get("gender"));
        assertEquals("56", fields.get("age"));
        assertEquals("ZY0000002", fields.get("hospitalNo"));
        assertFalse(fields.containsKey("tissueType"));
        assertFalse(fields.containsKey("donorName"));
    }

    /**
     * 夹具外层结构（只取本测试要用的键；{@code _doc} 忽略）。
     */
    static final class Fixture {
        public List<Case> cases;
    }

    /**
     * 一条用例；{@code case} 是 Java 关键字，用注解映射。
     */
    static final class Case {
        @JsonProperty("case")
        public String caseName;

        public List<String> rawLines;

        public Map<String, String> expect;

        public List<String> absent;
    }

}
