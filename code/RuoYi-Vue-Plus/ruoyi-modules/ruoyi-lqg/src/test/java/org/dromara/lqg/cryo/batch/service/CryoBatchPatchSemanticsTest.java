package org.dromara.lqg.cryo.batch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.auth.staff.directory.StaffDirectoryStub;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.bo.CryoBatchSubmitBo;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.sample.domain.bo.PatchBody;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
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
 * 冻存批次修改的<b>补丁语义</b>（FIX V33）：键没出现 = 不动；键出现、值为空 = 清空；清必填项 = 400。
 *
 * <p>病灶：{@code CryoBatchService.update} 以前一律「null = 不动」—— 工作台冻存抽屉里清掉填错的
 * 「-80 转移至液氮时间」点保存（请求体 {@code "toLn2Time": null}），提示已保存而库里还在：
 * 这一批永远算「已转液氮」、退出超期提醒。
 *
 * @author FIX-V33
 */
class CryoBatchPatchSemanticsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    @Test
    @DisplayName("① ★ 清掉填错的转液氮时间与位置（暂存 -80 的批次）→ 真的写成 NULL")
    void clearingToLn2TimeReallyClears() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000003003,\"toLn2Time\":null,\"ln2Location\":null,\"remark\":\"\"}"));
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertTrue(set.containsKey("to_ln2_time") && set.get("to_ln2_time") == null, "★ 转液氮时间必须真的清掉：" + set);
        assertTrue(set.containsKey("ln2_location") && set.get("ln2_location") == null, set.toString());
        assertTrue(set.containsKey("remark") && set.get("remark") == null);
        for (String untouched : List.of("init_qty", "cryo_name", "passage", "freeze_time", "density", "frozen_by", "in_minus80")) {
            assertFalse(set.containsKey(untouched), "没带的键不许写：" + untouched);
        }
    }

    @Test
    @DisplayName("② 只带初始支数的那种老请求体：只写那一列（accept 的形状不受影响）")
    void partialBodyKeepsTheRest() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000003003,\"frozenBy\":\"王工\"}"));
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals("王工", set.get("frozen_by"));
        assertFalse(set.containsKey("to_ln2_time"), "没带 toLn2Time = 不动（不能顺手清掉已转液氮）");
    }

    @Test
    @DisplayName("③ 清必填项（冻存数量 / 冻存时间 / 样品名称 / 代数）→ 400，不写库；液氮批次清掉位置 → 400")
    void clearingRequiredIs400() {
        Fake fake = new Fake();
        for (String body : List.of("{\"id\":9000003003,\"initQty\":null}", "{\"id\":9000003003,\"freezeTime\":null}",
            "{\"id\":9000003003,\"cryoName\":\"\"}", "{\"id\":9000003003,\"passage\":null}",
            "{\"id\":9000003003,\"ln2Location\":null}")) {
            ServiceException e = assertThrows(ServiceException.class, () -> fake.service().update(parse(body)), body);
            assertEquals(Integer.valueOf(400), e.getCode(), body);
        }
        assertEquals(0, fake.updates.size());
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    @Test
    void creatingStoresCycleAndLeavesLegacyBodyEmpty() throws Exception {
        Fake fake = new Fake();
        String fields = "\"sampleId\":9000001001,\"cryoName\":\"周期测试\",\"passage\":\"P3\","
            + "\"freezeTime\":\"2026-10-30\",\"initQty\":5,\"inMinus80\":\"Y\"";
        fake.service().create(parse("{" + fields + ",\"passageCycleDays\":7}").value());
        assertEquals(7, fake.inserts.get(0).getPassageCycleDays());
        fake.service().create(parse("{" + fields + "}").value());
        assertEquals(null, fake.inserts.get(1).getPassageCycleDays());
        assertThrows(ServiceException.class,
            () -> fake.service().create(parse("{" + fields + ",\"passageCycleDays\":0}").value()));
        assertEquals(2, fake.inserts.size());
    }

    @Test
    void passageCycleSupportsSetClearAndAbsent() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000003003,\"passageCycleDays\":7}"));
        assertEquals(7, setValues(fake.updates.get(0)).get("passage_cycle_days"));
        fake.service().update(parse("{\"id\":9000003003,\"remark\":\"只改备注\"}"));
        assertFalse(setValues(fake.updates.get(1)).containsKey("passage_cycle_days"));
        for (String empty : List.of("null", "\"\"", "\"  \"")) {
            fake.service().update(parse("{\"id\":9000003003,\"passageCycleDays\":" + empty + "}"));
            Map<String, Object> values = setValues(fake.updates.get(fake.updates.size() - 1));
            assertTrue(values.containsKey("passage_cycle_days") && values.get("passage_cycle_days") == null);
        }
    }

    @Test
    void invalidCycleCannotWriteOrBeTruncated() {
        Fake fake = new Fake();
        for (String invalid : List.of("0", "-1", "3.5", "3.0", "2147483648", "\"3.5\"", "\"abc\"", "true", "[]")) {
            ServiceException e = assertThrows(ServiceException.class,
                () -> fake.service().update(parse("{\"id\":9000003003,\"passageCycleDays\":" + invalid + "}")), invalid);
            assertEquals(400, e.getCode(), invalid);
        }
        assertTrue(fake.updates.isEmpty(), "非法天数必须在写入前拒绝");
    }

    private static PatchBody<CryoBatchSubmitBo> parse(String json) throws Exception {
        return PatchBody.parse(MAPPER, MAPPER.readTree(json), CryoBatchSubmitBo.class);
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

    /** seed 的 3003：先 -80 后转液氮（in_minus80='Y'、to_ln2_time 与位置都有值）。 */
    private static final class Fake {

        final List<LambdaUpdateWrapper<?>> updates = new ArrayList<>();
        final List<CryoBatch> inserts = new ArrayList<>();

        CryoBatchService service() {
            CryoBatchMapper batchMapper = (CryoBatchMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CryoBatchMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "selectByIdForUpdate" -> {
                        CryoBatch b = new CryoBatch();
                        b.setId(9000003003L);
                        b.setSampleId(9000001008L);
                        b.setInMinus80("Y");
                        b.setFreezeTime(LocalDate.now().minusDays(40));
                        b.setToLn2Time(LocalDate.now().minusDays(30));
                        b.setLn2Location("2号罐-1架-A1");
                        b.setInitQty(6);
                        yield b;
                    }
                    case "update" -> {
                        updates.add((LambdaUpdateWrapper<?>) args[1]);
                        yield 1;
                    }
                    case "insert" -> {
                        CryoBatch batch = (CryoBatch) args[0];
                        batch.setId(9000003010L + inserts.size());
                        inserts.add(batch);
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            SampleMapper samples = (SampleMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SampleMapper.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("selectById")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    Sample sample = new Sample();
                    sample.setVerifyStatus("valid");
                    return sample;
                });
            return new CryoBatchService(batchMapper, null, samples, StaffDirectoryStub.empty());
        }
    }

}
