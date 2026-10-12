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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冻存人从内部人员名单里选（CR-20261012-22）：修改批次时「账号 id + 姓名」这一对怎么落。
 * 样本的操作人、石蜡包埋的包埋人 / 操作人走的是同一份规则（{@code StaffDirectoryService.resolve}）。
 *
 * @author CR-20261012-22
 */
class CryoFrozenByStaffTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Map<Long, String> STAFF = Map.of(9000000101L, "李工", 9000000102L, "刘佳");

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    @Test
    @DisplayName("① 选了人：账号 id 与姓名一起落，姓名取名单里的")
    void pickingAPersonWritesBothColumns() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000003003,\"frozenById\":9000000102,\"frozenBy\":\"乱写的\"}"));
        Map<String, Object> set = setValues(fake.updates.get(0));
        assertEquals(9000000102L, set.get("frozen_by_id"));
        assertEquals("刘佳", set.get("frozen_by"));
    }

    @Test
    @DisplayName("② 旧版客户端只带名字：对上唯一一个人 → 自动关联；对不上 → 只存文字、id 写成 NULL")
    void nameOnlyLinksWhenUnique() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000003003,\"frozenBy\":\"刘佳\"}"));
        assertEquals(9000000102L, setValues(fake.updates.get(0)).get("frozen_by_id"));
        fake.service().update(parse("{\"id\":9000003003,\"frozenBy\":\"袁琴琴\"}"));
        Map<String, Object> set = setValues(fake.updates.get(1));
        assertTrue(set.containsKey("frozen_by_id") && set.get("frozen_by_id") == null, set.toString());
        assertEquals("袁琴琴", set.get("frozen_by"));
    }

    @Test
    @DisplayName("③ ★ 选了名单外的账号 → 400，库里一个字都不写")
    void unknownAccountIs400() {
        Fake fake = new Fake();
        ServiceException e = assertThrows(ServiceException.class,
            () -> fake.service().update(parse("{\"id\":9000003003,\"frozenById\":9000000999}")));
        assertEquals(Integer.valueOf(400), e.getCode());
        assertTrue(e.getMessage().contains("冻存人"), e.getMessage());
        assertEquals(0, fake.updates.size());
    }

    @Test
    @DisplayName("④ 这一对键都没带 = 两列都不动；带了空值 = 两列一起清空")
    void absentKeepsAndBlankClears() throws Exception {
        Fake fake = new Fake();
        fake.service().update(parse("{\"id\":9000003003,\"remark\":\"只改备注\"}"));
        Map<String, Object> untouched = setValues(fake.updates.get(0));
        assertFalse(untouched.containsKey("frozen_by_id") || untouched.containsKey("frozen_by"), untouched.toString());
        fake.service().update(parse("{\"id\":9000003003,\"frozenById\":null,\"frozenBy\":\"\"}"));
        Map<String, Object> cleared = setValues(fake.updates.get(1));
        assertTrue(cleared.containsKey("frozen_by_id") && cleared.containsKey("frozen_by"));
        assertNull(cleared.get("frozen_by_id"));
        assertNull(cleared.get("frozen_by"));
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

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

    /** seed 的 3003：冻存人是李工（已关联账号）。 */
    private static final class Fake {

        final List<LambdaUpdateWrapper<?>> updates = new ArrayList<>();

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
                        b.setFrozenById(9000000101L);
                        b.setFrozenBy("李工");
                        yield b;
                    }
                    case "update" -> {
                        updates.add((LambdaUpdateWrapper<?>) args[1]);
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            return new CryoBatchService(batchMapper, null, null, StaffDirectoryStub.of(STAFF));
        }
    }

}
