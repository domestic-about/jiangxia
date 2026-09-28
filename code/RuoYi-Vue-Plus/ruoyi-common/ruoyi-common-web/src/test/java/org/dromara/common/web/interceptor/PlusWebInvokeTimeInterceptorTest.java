package org.dromara.common.web.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求日志脱敏（独立验收 V19）：外部提交里的供体姓名、住院号、手机号不许以明文进 {@code [PLUS]开始请求} 日志。
 *
 * <p>测的是拦截器里真正拼日志内容的两个函数（JSON 体、表单 / 查询参数），不起容器。
 */
class PlusWebInvokeTimeInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 外部小程序提交组织样本的请求体形状（字段名取自 lqg 的 BO；值是 seed 的测试数据）。
     */
    private static final String EXT_SUBMIT = """
        {"sampleKind":"tissue","donorName":"测试供体甲","hospitalNo":"ZY0000001","patientNo":"P-0001",
         "gender":"male","age":56,"contactPhone":"13800000011","remark":"加急",
         "password":"admin123","oldPassword":"x","newPassword":"y","confirmPassword":"z",
         "items":[{"donorName":"测试供体乙","hospitalNo":"ZY0000002"}],
         "ext":{"profile":{"phonenumber":"13800000012","email":"a@b.cn"}},
         "login":{"xcxCode":"mock:extA","phoneCode":"mock:13800000011"}}
        """;

    private static final List<String> PLAINTEXT = List.of("测试供体甲", "测试供体乙", "ZY0000001", "ZY0000002",
        "P-0001", "13800000011", "13800000012", "a@b.cn", "mock:extA", "admin123");

    @Test
    @DisplayName("JSON 体：供体姓名 / 住院号 / 患者编号 / 手机号 / 邮箱 / 登录 code 全部打码，任意深度与数组里都一样")
    void jsonBodyIsMasked() throws Exception {
        String logged = PlusWebInvokeTimeInterceptor.sanitizeJson(EXT_SUBMIT, MAPPER);
        for (String plain : PLAINTEXT) {
            assertFalse(logged.contains(plain), "日志里出现了明文「" + plain + "」：" + logged);
        }
        JsonNode node = MAPPER.readTree(logged);
        assertEquals("******", node.get("donorName").asText());
        assertEquals("******", node.get("hospitalNo").asText());
        assertEquals("******", node.get("patientNo").asText());
        assertEquals("138****0011", node.get("contactPhone").asText(), "手机号留前 3 后 4");
        assertEquals("138****0012", node.at("/ext/profile/phonenumber").asText());
        assertEquals("******", node.at("/items/0/donorName").asText());
        assertEquals("******", node.at("/login/xcxCode").asText());
        // 不敏感的字段原样（排查要用）
        assertEquals("tissue", node.get("sampleKind").asText());
        assertEquals(56, node.get("age").asInt());
        assertEquals("加急", node.get("remark").asText());
        // 口令类照旧整个删掉（上游行为）
        for (String removed : List.of("password", "oldPassword", "newPassword", "confirmPassword")) {
            assertFalse(node.has(removed), "口令字段应当删掉：" + removed);
        }
    }

    @Test
    @DisplayName("字段名大小写不敏感；null / 空串原样（「没传」本身不敏感）")
    void caseInsensitiveAndEmpty() throws Exception {
        JsonNode node = MAPPER.readTree(PlusWebInvokeTimeInterceptor.sanitizeJson(
            "{\"DonorName\":\"张三\",\"HOSPITALNO\":\"ZY1\",\"donorNameMasked\":\"张*\",\"hospitalNo\":null,\"phone\":\"\"}",
            MAPPER));
        assertEquals("******", node.get("DonorName").asText());
        assertEquals("******", node.get("HOSPITALNO").asText());
        assertTrue(node.get("hospitalNo").isNull());
        assertEquals("", node.get("phone").asText());
        assertEquals("张*", node.get("donorNameMasked").asText(), "已经打过码的展示字段不在清单里，原样");
    }

    @Test
    @DisplayName("对象 / 数组形状的敏感字段整体换成掩码，不往里递归露值")
    void structuredSensitiveValue() throws Exception {
        JsonNode node = MAPPER.readTree(PlusWebInvokeTimeInterceptor.sanitizeJson(
            "{\"donorName\":{\"first\":\"张\",\"last\":\"三\"},\"phone\":[\"13800000011\"]}", MAPPER));
        assertEquals("******", node.get("donorName").asText());
        assertEquals("******", node.get("phone").asText());
    }

    @Test
    @DisplayName("不是合法 JSON 的请求体：一个字都不打印（半截 JSON 里同样可能有明文）")
    void invalidJsonIsNotPrinted() {
        String logged = PlusWebInvokeTimeInterceptor.sanitizeJson("{\"donorName\":\"测试供体甲\",", MAPPER);
        assertFalse(logged.contains("测试供体甲"), logged);
        assertTrue(logged.contains("不打印"), logged);
    }

    @Test
    @DisplayName("表单 / 查询参数：同一份清单；口令删掉，手机号留前 3 后 4，其余打码；原参数表不被改")
    void paramsAreMasked() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("donorName", new String[]{"测试供体甲"});
        params.put("phonenumber", new String[]{"13800000011", "021-1234567"});
        params.put("password", new String[]{"admin123"});
        params.put("pageSize", new String[]{"10"});
        Map<String, String[]> logged = PlusWebInvokeTimeInterceptor.sanitizeParams(params);
        assertArrayEquals(new String[]{"******"}, logged.get("donorName"));
        assertArrayEquals(new String[]{"138****0011", "******"}, logged.get("phonenumber"));
        assertFalse(logged.containsKey("password"));
        assertArrayEquals(new String[]{"10"}, logged.get("pageSize"));
        assertArrayEquals(new String[]{"测试供体甲"}, params.get("donorName"), "请求本身的参数不能被改");
    }

    @Test
    @DisplayName("清单覆盖 V19 点名的字段与手机号")
    void listCoversV19() {
        for (String field : List.of("donorname", "hospitalno", "patientno", "phone", "phonenumber", "contactphone",
            "mobile", "xcxcode", "phonecode", "secretkey", "accesskey")) {
            assertTrue(PlusWebInvokeTimeInterceptor.MASKED_FIELDS.contains(field), field);
        }
    }

}
