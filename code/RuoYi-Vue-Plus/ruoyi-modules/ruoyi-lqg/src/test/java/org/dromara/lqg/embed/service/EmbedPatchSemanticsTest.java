package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.bo.EmbedSubmitBo;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 石蜡包埋内部修改 / 补填的<b>补丁语义</b>（FIX V33）：键没出现 = 不动；键出现、值为空 = 清空；清必填项 = 400。
 *
 * <p>病灶：{@code EmbedService.update} 以前一律「null = 不动」—— 工作台抽屉里清掉填错的切片时间点保存，
 * 请求体里是 {@code "sectionTime": null}，提示「已保存」而库里还在（切片染色提示一直显示「已切片」）。
 * 与 {@code updateById} 忽略 null 是同一类「清不掉」（台账 #28）。
 *
 * @author FIX-V33
 */
class EmbedPatchSemanticsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final long ID = 9000002001L;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Embed.class);
    }

    @Test
    @DisplayName("① ★ 带了 null / 空串 = 清空（切片时间、包埋人）；没带的工序时间一个都不碰")
    void explicitNullClears() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000002001,\"sectionTime\":null,\"embedBy\":\"\"}"));
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertTrue(set.containsKey("section_time") && set.get("section_time") == null, "★ 切片时间必须真的清掉：" + set);
        assertTrue(set.containsKey("embed_by") && set.get("embed_by") == null, set.toString());
        for (String untouched : List.of("dehydrate_time", "paraffin_embed_time", "agarose_embed_time", "sample_type",
            "stain_types", "paraffin_block_no", "sample_id", "remark")) {
            assertFalse(set.containsKey(untouched), "没带的键不许写：" + untouched + " ∈ " + set.keySet());
        }
    }

    @Test
    @DisplayName("② 补填：只带做完的那一步 → 只写那一列（accept 的请求体就是这种形状）")
    void partialFillOnlyTouchesWhatWasSent() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000002001,\"dehydrateTime\":\"2026-09-16\"}"));
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals(LocalDate.of(2026, 9, 16), set.get("dehydrate_time"));
        assertFalse(set.containsKey("section_time"));
        assertFalse(set.containsKey("embed_by"));
    }

    @Test
    @DisplayName("③ 清必填项（石蜡块编号 / 所挂样本）→ 400，不写库")
    void clearingRequiredIs400() {
        Fake fake = new Fake();
        ServiceException block = assertThrows(ServiceException.class,
            () -> fake.service().update(parse("{\"id\":9000002001,\"paraffinBlockNo\":null}")));
        assertEquals(Integer.valueOf(400), block.getCode());
        assertTrue(block.getMessage().contains("石蜡块编号不能为空"), block.getMessage());
        ServiceException sample = assertThrows(ServiceException.class,
            () -> fake.service().update(parse("{\"id\":9000002001,\"sampleId\":null}")));
        assertEquals(Integer.valueOf(400), sample.getCode());
        assertEquals(0, fake.updates.size(), "被拒之后一条 UPDATE 都没有");
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private static PatchBody<EmbedSubmitBo> parse(String json) throws Exception {
        return PatchBody.parse(MAPPER, MAPPER.readTree(json), EmbedSubmitBo.class);
    }

    private static Map<String, Object> setValues(LambdaUpdateWrapper<?> w) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String part : w.getSqlSet().split(",")) {
            int eq = part.indexOf('=');
            String expr = part.substring(eq + 1).trim();
            out.put(part.substring(0, eq).trim(),
                w.getParamNameValuePairs().get(expr.substring(expr.lastIndexOf('.') + 1, expr.length() - 1)));
        }
        return out;
    }

    private static final class Fake {

        final List<LambdaUpdateWrapper<?>> updates = new ArrayList<>();

        EmbedService service() {
            EmbedMapper embedMapper = (EmbedMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EmbedMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectById" -> {
                        Embed e = new Embed();
                        e.setId(ID);
                        e.setSampleId(9000001001L);
                        e.setVerifyStatus("valid");
                        e.setParaffinBlockNo("T-E01-1");
                        e.setSectionTime(LocalDate.of(2026, 9, 1));
                        yield e;
                    }
                    case "update" -> {
                        updates.add((LambdaUpdateWrapper<?>) args[1]);
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            // FIX V02b：补填段的校验与落库列在 EmbedFillWriter（本组用例不碰染色 / marker，两个依赖留空）
            return new EmbedService(embedMapper, null, null, new EmbedBlockNoGuard(embedMapper),
                new EmbedFillWriter(null, null));
        }
    }

}
