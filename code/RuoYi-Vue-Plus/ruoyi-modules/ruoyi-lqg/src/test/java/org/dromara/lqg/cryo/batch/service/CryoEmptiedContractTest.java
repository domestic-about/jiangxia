package org.dromara.lqg.cryo.batch.service;

import cn.hutool.core.lang.Dict;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.service.ConfigService;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoBatchVo;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowDeltaRow;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.remind.mapper.CryoOverdueMapper;
import org.dromara.lqg.cryo.remind.service.CryoOverdueProperties;
import org.dromara.lqg.cryo.remind.service.CryoOverdueService;
import org.dromara.lqg.cryo.remind.sql.CryoOverdueSqlProvider;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleNameResolver;
import org.dromara.system.mapper.SysUserMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「已取空」三件套的契约测试（2026-09-24 甲方「支数取空的要提示」，G 批 B2）。
 *
 * <p>钉四件事：
 * <ol>
 *   <li><b>筛选 {@code emptiedOnly=true}</b> 拼的是 {@link CryoOverdueSqlProvider#EMPTIED_WHERE}
 *       （剩余 ≤ 0、软删流水自己排除），不带时一个字都不多；</li>
 *   <li><b>取空与超期第 ③ 条是同一份剩余算式的补集</b>：两段 where 都含
 *       {@link CryoOverdueSqlProvider#REMAINING_SQL}，一个 {@code > 0}、一个 {@code <= 0}
 *       —— 「取空了不进超期」在 SQL 上结构性成立；</li>
 *   <li><b>{@code tabCounts}</b> 键序固定 all → overdue → ln2 → emptied，{@code emptied} 那一格的计数
 *       走的就是这段片段（桩 mapper 记下每次 {@code selectCount} 的 wrapper 来断）；</li>
 *   <li><b>行上的 {@code emptied} / {@code overdue} / {@code frozenDays}</b>：取空的批次冻存再久也
 *       {@code overdue=false}；{@code frozenDays} 是今天 − 冻存日。</li>
 * </ol>
 *
 * <p>★ 不启 Spring：mapper 一律 {@link Proxy} 造桩（与 {@code CryoOverdueServiceTest} 同一个套路）。
 * 真库上的「3004 在已取空页签、计数 1」由 DONE.md 里的接口实测覆盖。
 *
 * @author G 批 B2（冻存取空提示）
 */
class CryoEmptiedContractTest {

    /** 探针阈值（只把「阈值」这个入参喂进 wrapper / 判定，断的是形态不是口径）。 */
    private static final int PROBE_DAYS = 9;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    /** 只取 WHERE 段（切掉默认排序里的超期置顶键）。 */
    private static String whereOnly(LambdaQueryWrapper<CryoBatch> wrapper) {
        String sql = wrapper.getTargetSql();
        int idx = sql.indexOf("ORDER BY");
        return idx < 0 ? sql : sql.substring(0, idx);
    }

    @Test
    @DisplayName("① emptiedOnly=true 拼「剩余 ≤ 0」那一段（软删流水自己排除）；不带时一个字都不多")
    void emptiedOnlyUsesSharedFragment() {
        CryoQueryBo q = new CryoQueryBo();
        q.setEmptiedOnly(true);
        String sql = whereOnly(CryoQueryService.buildWrapper(q, null, null, PROBE_DAYS));
        assertTrue(sql.contains(CryoOverdueSqlProvider.EMPTIED_WHERE), "必须原样拼那一段片段：" + sql);
        assertTrue(sql.contains("<= 0"), sql);
        assertTrue(sql.contains("f.del_flag = '0'"), "软删流水不算（seed 3002 名下就有一条软删的 -1）：" + sql);

        String plain = whereOnly(CryoQueryService.buildWrapper(new CryoQueryBo(), null, null, PROBE_DAYS));
        assertFalse(plain.contains("t_lqg_cryo_flow"), "不带 emptiedOnly 时 WHERE 段不许多出剩余子查询：" + plain);

        CryoQueryBo off = new CryoQueryBo();
        off.setEmptiedOnly(false);
        assertFalse(whereOnly(CryoQueryService.buildWrapper(off, null, null, PROBE_DAYS)).contains("t_lqg_cryo_flow"),
            "emptiedOnly=false 与不带同义");
    }

    @Test
    @DisplayName("② 取空与超期第 ③ 条拼同一份剩余算式：一个 > 0、一个 <= 0（取空了结构上就进不了超期）")
    void emptiedIsComplementOfOverdueClause3() {
        String remaining = CryoOverdueSqlProvider.REMAINING_SQL;
        assertTrue(CryoOverdueSqlProvider.WHERE.contains(remaining + " > 0"),
            "超期第 ③ 条 = 剩余 > 0：" + CryoOverdueSqlProvider.WHERE);
        assertEquals(remaining + " <= 0", CryoOverdueSqlProvider.EMPTIED_WHERE);
        assertTrue(remaining.contains("f.del_flag = '0'"), "剩余算式里的流水软删条件是手写的：" + remaining);
        // 超期那段原文一个字没变（它还被两条 @Select 注解原样引用）
        assertTrue(CryoOverdueSqlProvider.WHERE.startsWith("del_flag = '0' AND in_minus80 = 'Y' AND to_ln2_time IS NULL"),
            CryoOverdueSqlProvider.WHERE);

        // 同时带 overdueOnly 与 emptiedOnly = 两段互斥的条件相与（空集）；前端两个页签互斥
        CryoQueryBo both = new CryoQueryBo();
        both.setOverdueOnly(true);
        both.setEmptiedOnly(true);
        String sql = whereOnly(CryoQueryService.buildWrapper(both, null, null, PROBE_DAYS));
        assertTrue(sql.contains("> 0") && sql.contains("<= 0"), sql);
    }

    @Test
    @DisplayName("③ tabCounts 四个键 all → overdue → ln2 → emptied；emptied 那一格走取空片段")
    void tabCountsCarryEmptied() {
        List<String> countSqls = new ArrayList<>();
        CryoBatchMapper batchMapper = (CryoBatchMapper) Proxy.newProxyInstance(
            CryoBatchMapper.class.getClassLoader(), new Class<?>[] {CryoBatchMapper.class},
            (proxy, method, args) -> {
                if ("selectCount".equals(method.getName())) {
                    Wrapper<?> wrapper = (Wrapper<?>) args[0];
                    String sql = wrapper == null ? "" : wrapper.getSqlSegment();
                    countSqls.add(sql);
                    if (sql.contains("<= 0")) {
                        return 1L;
                    }
                    if (sql.contains("to_ln2_time")) {
                        return 2L;
                    }
                    return 7L;
                }
                return defaultValue(method.getReturnType());
            });
        CryoQueryService service = new CryoQueryService(batchMapper, flowMapper(Map.of()), sampleMapper(),
            new SampleNameResolver(userMapper()), overdueService(2L));

        Map<String, Long> counts = service.tabCounts();
        assertEquals(List.of("all", "overdue", "ln2", "emptied"), new ArrayList<>(counts.keySet()), "键序固定");
        assertEquals(Long.valueOf(7), counts.get("all"));
        assertEquals(Long.valueOf(2), counts.get("overdue"), "overdue 仍是 CryoOverdueService.countOverdue()");
        assertEquals(Long.valueOf(2), counts.get("ln2"));
        assertEquals(Long.valueOf(1), counts.get("emptied"));
        assertTrue(countSqls.stream().anyMatch(sql -> sql.contains(CryoOverdueSqlProvider.EMPTIED_WHERE)),
            "emptied 的计数必须拼取空片段（不许拿当前页 rows 数）：" + countSqls);
    }

    @Test
    @DisplayName("④ 行上：取空的批次 emptied=true 且冻存再久也不超期；frozenDays = 今天 − 冻存日")
    void rowFlagsOfEmptiedBatch() {
        LocalDate today = LocalDate.now();
        List<CryoBatch> rows = new ArrayList<>();
        // 与 seed 同形：3004 冻存 20 天、初始 3、取走 3 → 取空；3001 冻存 20 天、剩 6 → 超期
        rows.add(batch(3004L, today.minusDays(20), 3));
        rows.add(batch(3001L, today.minusDays(20), 8));
        Map<Long, Integer> deltas = new LinkedHashMap<>();
        deltas.put(3004L, -3);
        deltas.put(3001L, -2);
        CryoQueryService service = new CryoQueryService(batchMapper(), flowMapper(deltas), sampleMapper(),
            new SampleNameResolver(userMapper()), overdueService(0L));

        List<CryoBatchVo> vos = service.assemble(rows, PROBE_DAYS);
        CryoBatchVo emptied = vos.get(0);
        assertEquals(Integer.valueOf(0), emptied.getRemainingQty());
        assertTrue(emptied.getEmptied(), "剩余 0 → 已取空");
        assertFalse(emptied.getOverdue(), "★ 取空的批次冻存再久也不算超期（别提醒人去转一个空盒子）");
        assertNull(emptied.getOverdueDays());
        assertEquals(Integer.valueOf(20), emptied.getFrozenDays(), "冻存 20 天");

        CryoBatchVo alive = vos.get(1);
        assertFalse(alive.getEmptied(), "还剩 6 支");
        assertTrue(alive.getOverdue(), "探针阈值 9 天、冻存 20 天、还剩 6 支 → 超期");
        assertEquals(Integer.valueOf(20), alive.getFrozenDays());
    }

    @Test
    @DisplayName("⑤ 纯函数：isEmptied 是 ≤ 0；frozenDaysOf 缺冻存时间给 null")
    void pureFunctions() {
        assertTrue(CryoBalanceChecker.isEmptied(0));
        assertTrue(CryoBalanceChecker.isEmptied(-1), "历史坏账也当「没了」");
        assertFalse(CryoBalanceChecker.isEmptied(1));
        LocalDate today = LocalDate.of(2026, 9, 24);
        assertEquals(Integer.valueOf(0), CryoQueryService.frozenDaysOf(today, today), "当天冻的 = 0 天");
        assertEquals(Integer.valueOf(40), CryoQueryService.frozenDaysOf(today.minusDays(40), today));
        assertNull(CryoQueryService.frozenDaysOf(null, today));
    }

    // ── 桩 ────────────────────────────────────────────────────────────────────

    private static CryoBatch batch(Long id, LocalDate freezeTime, int initQty) {
        CryoBatch batch = new CryoBatch();
        batch.setId(id);
        batch.setInMinus80("Y");
        batch.setFreezeTime(freezeTime);
        batch.setInitQty(initQty);
        return batch;
    }

    private static CryoBatchMapper batchMapper() {
        return (CryoBatchMapper) Proxy.newProxyInstance(
            CryoBatchMapper.class.getClassLoader(), new Class<?>[] {CryoBatchMapper.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static CryoFlowMapper flowMapper(Map<Long, Integer> deltas) {
        return (CryoFlowMapper) Proxy.newProxyInstance(
            CryoFlowMapper.class.getClassLoader(), new Class<?>[] {CryoFlowMapper.class},
            (proxy, method, args) -> {
                if ("selectDeltaSums".equals(method.getName())) {
                    List<CryoFlowDeltaRow> out = new ArrayList<>();
                    for (Long batchId : (Iterable<Long>) args[0]) {
                        Integer delta = deltas.get(batchId);
                        if (delta != null) {
                            CryoFlowDeltaRow row = new CryoFlowDeltaRow();
                            row.setBatchId(batchId);
                            row.setTotalDelta(delta);
                            out.add(row);
                        }
                    }
                    return out;
                }
                return defaultValue(method.getReturnType());
            });
    }

    private static SampleMapper sampleMapper() {
        return (SampleMapper) Proxy.newProxyInstance(
            SampleMapper.class.getClassLoader(), new Class<?>[] {SampleMapper.class},
            (proxy, method, args) -> "selectBatchIds".equals(method.getName()) ? List.of() : defaultValue(method.getReturnType()));
    }

    private static SysUserMapper userMapper() {
        return (SysUserMapper) Proxy.newProxyInstance(
            SysUserMapper.class.getClassLoader(), new Class<?>[] {SysUserMapper.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    /** 超期计数恒回 {@code overdueCount} 的服务（本类不测超期判定本身，那是 CryoOverdueServiceTest 的事）。 */
    private static CryoOverdueService overdueService(long overdueCount) {
        CryoOverdueMapper mapper = (CryoOverdueMapper) Proxy.newProxyInstance(
            CryoOverdueMapper.class.getClassLoader(), new Class<?>[] {CryoOverdueMapper.class},
            (proxy, method, args) -> "selectOverdueCount".equals(method.getName())
                ? overdueCount : defaultValue(method.getReturnType()));
        return new CryoOverdueService(mapper, flowMapper(Map.of()), sampleMapper(),
            new CryoOverdueProperties(fixedConfig(String.valueOf(PROBE_DAYS))));
    }

    private static ConfigService fixedConfig(String value) {
        return new ConfigService() {
            @Override
            public String getConfigValue(String configKey) {
                return value;
            }

            @Override
            public Dict getConfigMap(String configKey) {
                return null;
            }

            @Override
            public List<Dict> getConfigArrayMap(String configKey) {
                return List.of();
            }

            @Override
            public <T> T getConfigObject(String configKey, Class<T> clazz) {
                return null;
            }

            @Override
            public <T> List<T> getConfigArray(String configKey, Class<T> clazz) {
                return List.of();
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

}
