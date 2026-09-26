package org.dromara.lqg.embed.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.embed.domain.bo.EmbedFillBo;
import org.dromara.lqg.embed.domain.bo.EmbedVerifyBo;
import org.dromara.lqg.embed.service.EmbedVerifyService;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.service.PatchBodyReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code PUT /lqg/embed/{id}/verify} 的入参读法（FIX V02b / issue #147）：请求体里的 {@code fill} 要带着
 * 「出现过哪些键」交给 service（补丁语义：没带 = 不动、带了 null = 清空）；不带 {@code fill} 与修复前完全一样。
 *
 * <p>病灶的另一半就在这一层：以前 controller 收的是 {@code EmbedVerifyBo}（3 个键），抽屉里补填的内容
 * 在反序列化这一步就没了 —— 这里钉住「fill 一路交到 service、键一个不丢」。
 *
 * @author FIX-V02b
 */
class EmbedControllerVerifyContractTest {

    /** 与容器里那个 ObjectMapper 同口径：未知键忽略 */
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 记下 controller 交给 service 的三样东西 */
    private static final class Captured {
        Long id;
        EmbedVerifyBo bo;
        PatchBody<EmbedFillBo> fill;
        int calls;
    }

    private static EmbedController controller(Captured captured) {
        EmbedVerifyService service = new EmbedVerifyService(null, null, null, null) {
            @Override
            public void verify(Long id, EmbedVerifyBo bo, PatchBody<EmbedFillBo> fill) {
                captured.id = id;
                captured.bo = bo;
                captured.fill = fill;
                captured.calls++;
            }
        };
        return new EmbedController(null, null, service, null, new PatchBodyReader(MAPPER));
    }

    @Test
    @DisplayName("① ★ 带 fill：键与值一路交到 service（带了 null 的键也算出现 = 清空）")
    void fillIsPassedWithItsKeys() throws Exception {
        Captured c = new Captured();
        controller(c).verify(9000002006L, MAPPER.readTree("{\"action\":\"valid\",\"paraffinBlockNo\":\"T-E06-1\","
            + "\"fill\":{\"sectionTime\":\"2026-09-21\",\"embedBy\":null,\"stainTypes\":[\"HE\"],"
            + "\"markers\":[{\"markerName\":\"Ki67\",\"expression\":\"strong\"}]}}"));
        assertEquals(1, c.calls);
        assertNotNull(c.fill, "★ fill 必须带着键交到 service（修复前在反序列化这一步就没了）");
        assertEquals(9000002006L, c.id);
        assertEquals("valid", c.bo.getAction());
        assertEquals("T-E06-1", c.bo.getParaffinBlockNo());
        assertEquals(Set.of("sectionTime", "embedBy", "stainTypes", "markers"), c.fill.keys());
        assertEquals(LocalDate.of(2026, 9, 21), c.fill.value().getSectionTime());
        assertNull(c.fill.value().getEmbedBy());
        assertEquals(List.of("HE"), c.fill.value().getStainTypes());
        assertEquals("Ki67", c.fill.value().getMarkers().get(0).getMarkerName());
    }

    @Test
    @DisplayName("② 不带 fill / fill 为 null（老调用方）：service 收到的补填段是 null（一个字都不动）")
    void noFillMeansNull() throws Exception {
        List<String> bodies = new ArrayList<>(List.of(
            "{\"action\":\"valid\",\"paraffinBlockNo\":\"T-E06-1\"}",
            "{\"action\":\"invalid\",\"reason\":\"信息不符\",\"fill\":null}"));
        for (String json : bodies) {
            Captured c = new Captured();
            controller(c).verify(9000002006L, MAPPER.readTree(json));
            assertEquals(1, c.calls, json);
            assertNull(c.fill, "没带补填段就不许凭空造一个：" + json);
        }
    }

    @Test
    @DisplayName("③ fill 形状不对（不是对象 / 日期格式错）→ 400，service 一次都没被调")
    void malformedFillIs400() {
        for (String json : List.of(
            "{\"action\":\"valid\",\"paraffinBlockNo\":\"T-E06-1\",\"fill\":\"x\"}",
            "{\"action\":\"valid\",\"paraffinBlockNo\":\"T-E06-1\",\"fill\":[1]}",
            "{\"action\":\"valid\",\"paraffinBlockNo\":\"T-E06-1\",\"fill\":{\"sectionTime\":\"2026/09/21\"}}")) {
            Captured c = new Captured();
            ServiceException e = assertThrows(ServiceException.class, () -> controller(c).verify(9000002006L, MAPPER.readTree(json)));
            assertEquals(Integer.valueOf(400), e.getCode(), json);
            assertTrue(e.getMessage().contains("fill"), "报错要指到 fill：" + e.getMessage());
            assertEquals(0, c.calls, "形状不对就不该走到核验：" + json);
        }
    }

}
