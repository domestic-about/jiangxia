package org.dromara.lqg.cryo.remind.service;

import cn.hutool.core.lang.Dict;
import org.dromara.common.core.service.ConfigService;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowDeltaRow;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.remind.domain.vo.CryoOverdueVo;
import org.dromara.lqg.cryo.remind.mapper.CryoOverdueMapper;
import org.dromara.lqg.sample.mapper.SampleMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 超期判定与清单 / 计数的单测（CRYO-REMIND-001，accept 1 的 Maven 段
 * {@code -Dtest='CryoOverdue*Test'} 点名本类）。
 *
 * <p>★ 钉四件事：
 * <ol>
 *   <li><b>边界四例</b>（阈值当天算、前一天不算、取空了不算、直接液氮 / 已转液氮不算）；</li>
 *   <li><b>阈值换个数，边界跟着移动</b>（阈值是入参，不是常量）；</li>
 *   <li><b>阈值每次现读、不缓存</b>（{@code CryoOverdueProperties#days()} 连读两次拿到两个值）；</li>
 *   <li><b>{@code countOverdue() = listOverdue().size()}</b>（清单长度与页签 / 首页那个数字恒等）。</li>
 * </ol>
 *
 * <p>★ 本类<b>不启 Spring 上下文</b>（ticket 的 Maven 段只跑这一个测试类）：
 * {@link CryoOverdueService#isOverdue} 是纯函数直接调；需要 mapper 的两条路用
 * {@link Proxy} 造桩。所以本类<b>证明不了</b>「后端能起来」——那一格由 accept 的实跑
 * {@code GET /lqg/sys/ping} 200 覆盖（CRYO-FLOW-001 正是「159 条单测全绿、后端起不来」）。
 *
 * @author CRYO-REMIND-001
 */
class CryoOverdueServiceTest {

    /** 探针阈值：默认口径那一档（accept 里由系统参数给，这里是入参）。 */
    private static final int DAYS_DEFAULT = 14;

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    // ── 边界四例 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 阈值当天就算（第 N 天当天），前一天不算 —— >= 不是 >")
    void boundaryOnTheVeryDay() {
        CryoBatch onDay = batch("Y", null, TODAY.minusDays(DAYS_DEFAULT));
        assertTrue(CryoOverdueService.isOverdue(onDay, 2, TODAY, DAYS_DEFAULT),
            "★ 恰好第 N 天必须算超期（seed 的 3005 就是这一格，accept 1 钉它）");
        assertEquals(Integer.valueOf(0), CryoOverdueService.overdueDaysOf(onDay, 2, TODAY, DAYS_DEFAULT),
            "阈值当天 = 已超 0 天");

        CryoBatch before = batch("Y", null, TODAY.minusDays(DAYS_DEFAULT - 1));
        assertFalse(CryoOverdueService.isOverdue(before, 5, TODAY, DAYS_DEFAULT),
            "差一天不算（seed 的 3006 是第 13 天 → 不算）");
        assertNull(CryoOverdueService.overdueDaysOf(before, 5, TODAY, DAYS_DEFAULT),
            "没超期 → 已超天数是 null，不是 0");
    }

    @Test
    @DisplayName("② 取空了不算：剩余 = 0 的批次不进清单（别提醒人去转一个空盒子）")
    void emptiedIsNotOverdue() {
        CryoBatch emptied = batch("Y", null, TODAY.minusDays(DAYS_DEFAULT));
        assertFalse(CryoOverdueService.isOverdue(emptied, 0, TODAY, DAYS_DEFAULT),
            "★ 剩余 0 不算（seed 的 3004 是这一格）");
        assertFalse(CryoOverdueService.isOverdue(emptied, -1, TODAY, DAYS_DEFAULT), "剩余为负更不算");
        assertTrue(CryoOverdueService.isOverdue(emptied, 1, TODAY, DAYS_DEFAULT), "还剩 1 支就算");
    }

    @Test
    @DisplayName("③ 已登记转液氮不算、直接进液氮不算 —— 转完提醒当场消失")
    void ln2IsNotOverdue() {
        CryoBatch moved = batch("Y", TODAY.minusDays(3), TODAY.minusDays(DAYS_DEFAULT + 10));
        assertFalse(CryoOverdueService.isOverdue(moved, 4, TODAY, DAYS_DEFAULT),
            "★ to_ln2_time 落值就退出清单（CR-20260918-07 甲方问「转移后还会有提示吗」：不会）");

        CryoBatch direct = batch("N", null, TODAY.minusDays(DAYS_DEFAULT + 40));
        assertFalse(CryoOverdueService.isOverdue(direct, 5, TODAY, DAYS_DEFAULT),
            "直接进液氮（in_minus80='N'）永不超期");
    }

    @Test
    @DisplayName("④ 阈值换成 7：边界跟着移动（阈值是入参，函数自己不读配置）")
    void thresholdMovesBoundary() {
        int seven = 7;
        CryoBatch day13 = batch("Y", null, TODAY.minusDays(13));
        assertFalse(CryoOverdueService.isOverdue(day13, 5, TODAY, DAYS_DEFAULT), "阈值 14 时 13 天不算");
        assertTrue(CryoOverdueService.isOverdue(day13, 5, TODAY, seven), "阈值 7 时 13 天就算");
        assertEquals(Integer.valueOf(6), CryoOverdueService.overdueDaysOf(day13, 5, TODAY, seven), "13 − 7 = 已超 6 天");

        CryoBatch day6 = batch("Y", null, TODAY.minusDays(6));
        assertFalse(CryoOverdueService.isOverdue(day6, 5, TODAY, seven), "阈值 7 时第 6 天仍不算");
        assertTrue(CryoOverdueService.isOverdue(day6, 5, TODAY, 6), "阈值 6 时第 6 天当天就算");
    }

    // ── 阈值：唯一取值处，每次现读 ────────────────────────────────────────────

    @Test
    @DisplayName("⑤ 阈值每次现读、不缓存：同一次运行里改参数，下一次 days() 就是新值")
    void daysAreReadEveryTime() {
        String[] slot = {"13"};
        CryoOverdueProperties properties = new CryoOverdueProperties(config(slot));
        assertEquals(13, properties.days(), "读得到就用参数值");
        slot[0] = "21";
        assertEquals(21, properties.days(), "★ 改完下一次读即生效（不许缓存进静态字段）");
    }

    @Test
    @DisplayName("⑥ 缺行 / 非正整数 / 读失败 → 回落默认值并仍可用")
    void daysFallBackToDefault() {
        assertEquals(DAYS_DEFAULT, new CryoOverdueProperties(config(new String[] {null})).days(), "缺行");
        assertEquals(DAYS_DEFAULT, new CryoOverdueProperties(config(new String[] {""})).days(), "空串");
        assertEquals(DAYS_DEFAULT, new CryoOverdueProperties(config(new String[] {"abc"})).days(), "解析不了");
        assertEquals(DAYS_DEFAULT, new CryoOverdueProperties(config(new String[] {"0"})).days(), "0 不是正整数");
        assertEquals(DAYS_DEFAULT, new CryoOverdueProperties(config(new String[] {"-3"})).days(), "负数不是正整数");
        assertEquals(9, new CryoOverdueProperties(config(new String[] {" 9 "})).days(), "两边空白要去掉");
        assertEquals(DAYS_DEFAULT,
            new CryoOverdueProperties(throwingConfig()).days(), "底层抛异常也要回落，不能把请求炸掉");
    }

    // ── 计数 = 清单长度 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("⑦ countOverdue() = listOverdue().size()，且两边的已超天数与纯函数逐个相等")
    void countEqualsListSize() {
        List<CryoBatch> candidates = seedLikeCandidates();
        Map<Long, Integer> deltas = seedLikeDeltas();

        CryoOverdueService service = service(new String[] {"14"}, candidates, deltas);
        List<CryoOverdueVo> list = service.listOverdue();
        assertEquals(2, list.size(), "≈ seed：超期只有 3001 与 3005 两条");
        assertEquals(service.countOverdue(), (long) list.size(),
            "★ 计数函数与清单必须同源（页签数字与清单长度不等就是各写各的 where）");

        Map<Long, Integer> daysById = new LinkedHashMap<>();
        for (CryoOverdueVo vo : list) {
            daysById.put(vo.getId(), vo.getOverdueDays());
            assertTrue(vo.getOverdue(), "清单里每行 overdue 恒 true");
        }
        assertEquals(Integer.valueOf(6), daysById.get(3001L), "3001：冻存 20 天前 − 阈值 14 = 已超 6 天");
        assertEquals(Integer.valueOf(0), daysById.get(3005L), "3005：恰好第 14 天 → 已超 0 天");

        // 阈值改成 13：第 13 天的 3006 进来；两个数字一起动
        CryoOverdueService tighter = service(new String[] {"13"}, candidates, deltas);
        List<CryoOverdueVo> wider = tighter.listOverdue();
        assertEquals(3, wider.size(), "阈值 13 → 3006 也算");
        assertEquals(tighter.countOverdue(), (long) wider.size(), "计数跟着阈值一起动");
        Map<Long, Integer> widerDays = new LinkedHashMap<>();
        for (CryoOverdueVo vo : wider) {
            widerDays.put(vo.getId(), vo.getOverdueDays());
        }
        assertEquals(Integer.valueOf(7), widerDays.get(3001L), "20 − 13 = 7");
        assertEquals(Integer.valueOf(1), widerDays.get(3005L), "14 − 13 = 1");
        assertEquals(Integer.valueOf(0), widerDays.get(3006L), "13 − 13 = 0");
    }

    // ── 桩与夹具 ──────────────────────────────────────────────────────────────

    private static CryoBatch batch(String inMinus80, LocalDate toLn2, LocalDate freezeTime) {
        CryoBatch batch = new CryoBatch();
        batch.setInMinus80(inMinus80);
        batch.setToLn2Time(toLn2);
        batch.setFreezeTime(freezeTime);
        return batch;
    }

    /**
     * 与 seed 同形的候选集合（id 用 seed 的号，读起来能对上 README 的冻存速查表）。
     * {@code countOverdue} 那条桩按<b>测试自己独立写</b>的四条件筛，所以
     * 「计数 = 清单长度」不是在自证：只要服务的行判定与四条件分叉，这条就会红。
     */
    private static List<CryoBatch> seedLikeCandidates() {
        List<CryoBatch> rows = new ArrayList<>();
        rows.add(candidate(3001L, "Y", null, TODAY.minusDays(20), 8, null));
        rows.add(candidate(3002L, "Y", null, TODAY.minusDays(5), 4, null));
        rows.add(candidate(3003L, "Y", TODAY.minusDays(30), TODAY.minusDays(40), 6, null));
        rows.add(candidate(3004L, "Y", null, TODAY.minusDays(14), 3, null));
        rows.add(candidate(3005L, "Y", null, TODAY.minusDays(14), 2, null));
        rows.add(candidate(3006L, "Y", null, TODAY.minusDays(13), 5, null));
        rows.add(candidate(3007L, "N", null, TODAY.minusDays(60), 5, null));
        return rows;
    }

    private static CryoBatch candidate(Long id, String inMinus80, LocalDate toLn2, LocalDate freezeTime,
                                       int initQty, String ln2Location) {
        CryoBatch batch = new CryoBatch();
        batch.setId(id);
        batch.setInMinus80(inMinus80);
        batch.setToLn2Time(toLn2);
        batch.setFreezeTime(freezeTime);
        batch.setInitQty(initQty);
        batch.setLn2Location(ln2Location);
        return batch;
    }

    /** 与 {@link #seedLikeCandidates()} 配套的「未删流水 delta 之和」。 */
    private static Map<Long, Integer> seedLikeDeltas() {
        Map<Long, Integer> deltas = new LinkedHashMap<>();
        deltas.put(3001L, -2);
        deltas.put(3003L, -2);
        deltas.put(3004L, -3);
        return deltas;
    }

    private static CryoOverdueService service(String[] configValue, List<CryoBatch> candidates,
                                              Map<Long, Integer> deltas) {
        CryoOverdueMapper mapper = (CryoOverdueMapper) Proxy.newProxyInstance(
            CryoOverdueMapper.class.getClassLoader(), new Class<?>[] {CryoOverdueMapper.class},
            (proxy, method, args) -> {
                int days = (int) args[0];
                return switch (method.getName()) {
                    // SELECT 侧：与 SQL 同口径地按四条件挑（测试自己独立实现，别调被测代码）
                    case "selectOverdueList" -> candidates.stream()
                        .filter(row -> independentOverdue(row, remainingOf(row, deltas), TODAY, days))
                        .toList();
                    case "selectOverdueCount" -> candidates.stream()
                        .filter(row -> independentOverdue(row, remainingOf(row, deltas), TODAY, days))
                        .count();
                    default -> defaultValue(method.getReturnType());
                };
            });
        CryoFlowMapper flowMapper = (CryoFlowMapper) Proxy.newProxyInstance(
            CryoFlowMapper.class.getClassLoader(), new Class<?>[] {CryoFlowMapper.class},
            (proxy, method, args) -> {
                if ("selectDeltaSums".equals(method.getName())) {
                    List<CryoFlowDeltaRow> rows = new ArrayList<>();
                    for (Long batchId : (Iterable<Long>) args[0]) {
                        Integer delta = deltas.get(batchId);
                        if (delta != null) {
                            CryoFlowDeltaRow row = new CryoFlowDeltaRow();
                            row.setBatchId(batchId);
                            row.setTotalDelta(delta);
                            rows.add(row);
                        }
                    }
                    return rows;
                }
                return defaultValue(method.getReturnType());
            });
        SampleMapper sampleMapper = (SampleMapper) Proxy.newProxyInstance(
            SampleMapper.class.getClassLoader(), new Class<?>[] {SampleMapper.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new CryoOverdueService(mapper, flowMapper, sampleMapper,
            new CryoOverdueProperties(config(configValue)));
    }

    /**
     * 测试自己写的一份四条件判定（故意不复用 {@code CryoOverdueService.isOverdue}）：
     * 服务侧的行判定与它分叉时，「计数 = 清单长度」那条会红。
     */
    private static boolean independentOverdue(CryoBatch batch, int remaining, LocalDate today, int days) {
        if (!"Y".equals(batch.getInMinus80())) {
            return false;
        }
        if (batch.getToLn2Time() != null) {
            return false;
        }
        if (remaining <= 0) {
            return false;
        }
        return today.toEpochDay() - batch.getFreezeTime().toEpochDay() >= days;
    }

    private static int remainingOf(CryoBatch batch, Map<Long, Integer> deltas) {
        return (batch.getInitQty() == null ? 0 : batch.getInitQty()) + deltas.getOrDefault(batch.getId(), 0);
    }

    /** 一个只会回同一个值的 {@code ConfigService}（值放在数组里，测试中途可以改）。 */
    private static ConfigService config(String[] slot) {
        return new ConfigService() {
            @Override
            public String getConfigValue(String configKey) {
                return slot[0];
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

    private static ConfigService throwingConfig() {
        return new ConfigService() {
            @Override
            public String getConfigValue(String configKey) {
                throw new IllegalStateException("缓存不可用");
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
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    @Test
    @DisplayName("⑧ 桩自检：夹具本身对得上 README 的冻存速查（剩余 6/4/4/0/2/5/5）")
    void fixturesAreSane() {
        List<CryoBatch> candidates = seedLikeCandidates();
        Map<Long, Integer> deltas = seedLikeDeltas();
        assertEquals(7, candidates.size(), "7 条未删批次");
        assertEquals(6, remainingOf(candidates.get(0), deltas), "3001 剩 6");
        assertEquals(4, remainingOf(candidates.get(1), deltas), "3002 剩 4");
        assertEquals(4, remainingOf(candidates.get(2), deltas), "3003 剩 4");
        assertEquals(0, remainingOf(candidates.get(3), deltas), "3004 取空了");
        assertEquals(2, remainingOf(candidates.get(4), deltas), "3005 剩 2");
        assertEquals(5, remainingOf(candidates.get(5), deltas), "3006 剩 5");
        assertEquals(5, remainingOf(candidates.get(6), deltas), "3007 剩 5");
        assertNotNull(candidates.get(0).getFreezeTime());
    }

}
