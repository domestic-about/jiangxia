package org.dromara.lqg.cryo.flow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.flow.domain.bo.CryoFlowEditBo;
import org.dromara.lqg.cryo.flow.domain.bo.CryoFlowSubmitBo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冻存流水写侧的并发与判据测试（CRYO-FLOW-001）。
 *
 * <p>★★ <b>真实线程池 + 真实并发</b>（ticket §2 的单测要求：10 线程各取 1 支、剩余 3 →
 * 恰好 3 个成功）。用一个 JDK 动态代理当 {@code CryoBatchMapper} / {@code CryoFlowMapper}，
 * 里面用 {@link ReentrantLock} 模拟 {@code SELECT … FOR UPDATE} 的行锁语义 ——
 * 于是「锁住批次行 → 读流水 → 逐笔校验 → 落库」这段临界区有真实竞争，
 * 断言的是「成功次数」与「最终剩余」两个数，不是「方法被调过几次」。
 *
 * <p>★ 为什么不去连真库：本模块的测试类路径只有 JUnit（没有 Spring 上下文、没有数据源，
 * 见 {@code ruoyi-lqg/pom.xml}）。<b>真库上的并发证据在 accept 3</b>（5 个真实 HTTP 请求抢
 * 最后 2 支）——这一条是快速回归：把「忘了先锁批次行」这个改动当场打红。
 *
 * @author CRYO-FLOW-001
 */
class CryoFlowConcurrencyTest {

    /** 模拟一个批次行（{@code init_qty} 与 {@code in_minus80} 是判据用到的两列）。 */
    private static final long BATCH_ID = 9000003005L;

    @Test
    @DisplayName("① 10 个线程各取 1 支、剩余 3 → 恰好 3 个成功、剩余恰好 0、流水恰好 3 笔")
    void concurrentTakeExactlyRemaining() throws Exception {
        FakeCryo fake = new FakeCryo(3);
        CryoFlowService service = service(fake);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    fake.withLock(() -> service.create(BATCH_ID, post("take", 1, "并发取用")));
                    ok.incrementAndGet();
                } catch (ServiceException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.add(String.valueOf(e));
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发线程没有在 30 秒内跑完");
        pool.shutdownNow();

        assertTrue(unexpected.isEmpty(), "不该有非业务异常：" + unexpected);
        assertEquals(3, ok.get(), "★ 只应有 3 次成功（每次取 1 支、初始 3 支）");
        assertEquals(7, rejected.get(), "另外 7 次必须被拒（超取），不能静默成功");
        assertEquals(3, fake.undeletedFlows().size(), "落库的流水恰好 3 笔");
        assertEquals(0, remaining(fake), "★ 剩余恰好 0，绝不为负");
        for (CryoFlow flow : fake.undeletedFlows()) {
            assertEquals(-1, flow.getDelta(), "每一笔 take 的 delta 都必须是负数（靠 flow_type 现算加减就错了）");
            assertEquals("minus80", flow.getFromLocation(), "取自位置由批次当时所在位置带出");
        }
    }

    @Test
    @DisplayName("② 剩余 2 支时取 3 支被拒：库里一笔都不多（超取不能先落盘再报错）")
    void takeMoreThanRemainingRejectedWithoutSideEffect() {
        FakeCryo fake = new FakeCryo(2);
        CryoFlowService service = service(fake);

        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.create(BATCH_ID, post("take", 3, "超取"))));
        assertEquals(0, fake.flows.size(), "★ 被拒之后库里必须一行都不多");
        assertEquals(2, remaining(fake), "剩余不变");
    }

    @Test
    @DisplayName("③ 删一笔补入让后面的取走不够 → 拒且库里不变；删一笔取走 → 软删放行")
    void deleteRecomputesEveryStep() {
        FakeCryo fake = new FakeCryo(5);
        CryoFlowService service = service(fake);
        Long add = fake.withLock(() -> service.create(BATCH_ID, post("add", 2, "同批补冻")));
        Long take = fake.withLock(() -> service.create(BATCH_ID, post("take", 7, "全部取用")));
        assertNotNull(add);
        assertNotNull(take);
        assertEquals(0, remaining(fake), "初始 5 + 2 - 7 = 0");

        // 删掉那笔补入 → 7 支的取走在第 2 步透支 → 必须拒，且库里不变
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.delete(BATCH_ID, add)));
        assertEquals(2, fake.undeletedFlows().size(), "★ 被拒之后一笔都没被软删");

        // 删掉那笔取走 → 放行（软删，不是物理删）
        fake.withLock(() -> service.delete(BATCH_ID, take));
        assertEquals(1, fake.undeletedFlows().size());
        assertEquals(2, fake.flows.size(), "★ 软删不是物理删：两行都还在库里（追溯不能断）");
        assertEquals("1", flow(fake, take).getDelFlag(), "软删 = del_flag='1'，不是 DELETE FROM");
        assertEquals(7, remaining(fake), "初始 5 + 留着的补入 2 = 7");
    }

    @Test
    @DisplayName("④ 改一笔取走：改大到超过初始值 → 拒（第一步就为负）；改小 → 落库且不动 from_location")
    void updateRecomputesEveryStep() {
        FakeCryo fake = new FakeCryo(6);
        CryoFlowService service = service(fake);
        Long take = fake.withLock(() -> service.create(BATCH_ID, post("take", 4, "取用")));

        CryoFlowEditBo bigger = new CryoFlowEditBo();
        bigger.setQty(7);
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.update(BATCH_ID, take, bigger)));
        assertEquals(-4, flow(fake, take).getDelta(), "★ 被拒之后 delta 不变");

        CryoFlowEditBo smaller = new CryoFlowEditBo();
        smaller.setQty(1);
        smaller.setPurpose("复苏培养（更正）");
        fake.withLock(() -> service.update(BATCH_ID, take, smaller));
        assertEquals(-1, flow(fake, take).getDelta());
        assertEquals("复苏培养（更正）", flow(fake, take).getPurpose());
        assertEquals("minus80", flow(fake, take).getFromLocation(), "★ 改登记不动 from_location");
        assertEquals("take", flow(fake, take).getFlowType(), "★ 改登记不动 flow_type");
        assertNotNull(flow(fake, take).getUpdateTime(), "★ 记下修改时间");
    }

    /** 「清空用途」那一条 UPDATE 用了 lambda 列（FIX V33 的新测试），不起容器时要手工注册列缓存。 */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoFlow.class);
    }

    @Test
    @DisplayName("④b ★ FIX V33：取走登记把用途清空 → 库里真的清空（以前 updateById 跳过 null，提示已保存却没清掉）")
    void clearingPurposeReallyClearsIt() {
        FakeCryo fake = new FakeCryo(6);
        CryoFlowService service = service(fake);
        Long take = fake.withLock(() -> service.create(BATCH_ID, post("take", 1, "复苏培养")));
        assertEquals("复苏培养", flow(fake, take).getPurpose());

        CryoFlowEditBo clear = new CryoFlowEditBo();
        clear.setPurpose("");
        fake.withLock(() -> service.update(BATCH_ID, take, clear));
        assertEquals(null, flow(fake, take).getPurpose(), "★ 传了空用途 = 清空");
        assertEquals(-1, flow(fake, take).getDelta(), "别的列不动");

        // 不传用途 = 不动
        Long take2 = fake.withLock(() -> service.create(BATCH_ID, post("take", 1, "药敏")));
        CryoFlowEditBo keep = new CryoFlowEditBo();
        keep.setQty(2);
        fake.withLock(() -> service.update(BATCH_ID, take2, keep));
        assertEquals("药敏", flow(fake, take2).getPurpose(), "不传用途 = 沿用");

        // 盘点调整清空原因 → 400（原因必填），库里不变
        Long adjust = fake.withLock(() -> service.create(BATCH_ID, post("adjust", -1, "盘点少 1 支")));
        CryoFlowEditBo clearAdjust = new CryoFlowEditBo();
        clearAdjust.setPurpose(" ");
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.update(BATCH_ID, adjust, clearAdjust)));
        assertEquals("盘点少 1 支", flow(fake, adjust).getPurpose());
    }

    @Test
    @DisplayName("⑤ 登记类型改不了：PUT 传了不同的 flowType → 400，且库里不变")
    void flowTypeCannotBeChanged() {
        FakeCryo fake = new FakeCryo(6);
        CryoFlowService service = service(fake);
        Long take = fake.withLock(() -> service.create(BATCH_ID, post("take", 1, "取用")));

        CryoFlowEditBo bo = new CryoFlowEditBo();
        bo.setQty(1);
        bo.setFlowType("add");
        ServiceException e = assertThrows(ServiceException.class,
            () -> fake.withLock(() -> service.update(BATCH_ID, take, bo)));
        assertEquals(400, e.getCode());
        assertEquals(-1, flow(fake, take).getDelta());
        assertEquals("take", flow(fake, take).getFlowType());
    }

    @Test
    @DisplayName("⑥ 盘点调整必须写原因、不能为 0；取走/补入必须是正整数")
    void adjustRules() {
        FakeCryo fake = new FakeCryo(6);
        CryoFlowService service = service(fake);
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.create(BATCH_ID, post("adjust", -1, null))));
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.create(BATCH_ID, post("adjust", 0, "归零"))));
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.create(BATCH_ID, post("take", -1, "负的取走"))));
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.create(BATCH_ID, post("take", 0, "取 0"))));
        assertThrows(ServiceException.class, () -> fake.withLock(() -> service.create(BATCH_ID, post("move", 1, "未知类型"))));
        assertEquals(0, fake.flows.size(), "★ 六次被拒之后库里一行都不多");
    }

    @Test
    @DisplayName("⑦ ★ 判据用「那一刻的剩余」不是「最小初始支数」：初始 5 + 补入 2 → 取走 7 支全部取用必须成功")
    void takeUsesBalanceAtThatMomentNotMinimumInitQty() {
        FakeCryo fake = new FakeCryo(5);
        CryoFlowService service = service(fake);
        fake.withLock(() -> service.create(BATCH_ID, post("add", 2, "同批补冻")));
        // ★ 最小初始支数判据会要求 init ≥ 7、于是把这一笔合法的「全部取用」拒掉（本票实跑红过）
        fake.withLock(() -> service.create(BATCH_ID, post("take", 7, "全部取用")));
        assertEquals(0, remaining(fake), "初始 5 + 补入 2 − 取走 7 = 0");
        assertEquals(2, fake.undeletedFlows().size());
    }

    // ── 夹具 ─────────────────────────────────────────────────────────────────

    private static CryoFlowService service(FakeCryo fake) {
        // nameResolver 只被 GET …/flows 用到（本测试不碰读侧），传 null 即可
        return new CryoFlowService(fake.batchMapper(), fake.flowMapper(),
            new org.dromara.lqg.sample.service.SampleNameResolver(null));
    }
    private static CryoFlowSubmitBo post(String flowType, int qty, String purpose) {
        CryoFlowSubmitBo bo = new CryoFlowSubmitBo();
        bo.setFlowType(flowType);
        bo.setQty(qty);
        bo.setPurpose(purpose);
        return bo;
    }

    private static CryoFlow flow(FakeCryo fake, Long id) {
        return fake.flows.stream().filter(f -> id.equals(f.getId())).findFirst().orElseThrow();
    }

    private static int remaining(FakeCryo fake) {
        int sum = fake.initQty;
        for (CryoFlow flow : fake.undeletedFlows()) {
            sum += flow.getDelta();
        }
        return sum;
    }

    /**
     * 一个「假库」：一个批次行 + 一张流水表 + 一把模拟 {@code FOR UPDATE} 的行锁。
     *
     * <p>★ 行锁是这里的关键：{@code selectByIdForUpdate} 拿锁，调用方读完流水、校验、
     * 写完才释放 —— 与真库上一个事务持有行锁到 commit 等价。没有这把锁，
     * 10 个线程会全部读到剩余 3 并全部成功（本测试就是抓这个）。
     */
    private static final class FakeCryo {

        private final int initQty;
        private final ReentrantLock rowLock = new ReentrantLock();
        private final AtomicLong seq = new AtomicLong(9000003200L);
        private final List<CryoFlow> flows = Collections.synchronizedList(new ArrayList<>());

        FakeCryo(int initQty) {
            this.initQty = initQty;
        }

        List<CryoFlow> undeletedFlows() {
            List<CryoFlow> out = new ArrayList<>();
            synchronized (flows) {
                for (CryoFlow flow : flows) {
                    if (BATCH_ID == flow.getBatchId() && !"1".equals(flow.getDelFlag())) {
                        out.add(flow);
                    }
                }
            }
            return out;
        }

        CryoBatchMapper batchMapper() {
            return (CryoBatchMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CryoBatchMapper.class}, (proxy, method, args) -> {
                    if ("selectByIdForUpdate".equals(method.getName())) {
                        // ★ 模拟 SELECT … FOR UPDATE：拿锁 → 交还调用方。
                        //   锁由调用方在「读完流水 / 校验 / 写完」之后释放（withLock）；
                        //   被拒时由 withLock 的 finally 放掉 —— 与真库里事务回滚释放行锁等价。
                        rowLock.lock();
                        CryoBatch batch = new CryoBatch();
                        batch.setId(BATCH_ID);
                        batch.setInitQty(initQty);
                        batch.setInMinus80("Y");
                        batch.setFreezeTime(java.time.LocalDate.now().minusDays(14));
                        return batch;
                    }
                    return defaults(proxy, method, args);
                });
        }

        /**
         * 在「锁住批次行」的临界区里执行一段操作，结束（正常或抛异常）一定释放行锁。
         *
         * <p>★ 真库里的等价物是「一个事务持有行锁到 commit / rollback」；
         * {@code ReentrantLock} 是可重入的，所以进临界区前要先把本线程可能残留的持有次数清零
         * （本假库不对调用方承诺「上个操作有没有释放」——直接用锁的持有次数当唯一真相）。
         *
         * @throws ServiceException 原样往上抛业务判据的异常（让 {@code assertThrows} 看得见）
         */
        <T> T withLock(java.util.concurrent.Callable<T> handle) {
            while (rowLock.isHeldByCurrentThread() && rowLock.getHoldCount() > 0) {
                rowLock.unlock();
            }
            rowLock.lock();
            try {
                return handle.call();
            } catch (RuntimeException e) {
                // 业务判据抛的是 ServiceException（RuntimeException）：原样往上抛，让断言看得见
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("锁内操作抛了受检异常（本测试不该出现）", e);
            } finally {
                while (rowLock.isHeldByCurrentThread() && rowLock.getHoldCount() > 0) {
                    rowLock.unlock();
                }
            }
        }

        /** {@code void} 版本（{@code delete} / {@code update} 这类没有返回值的操作）。 */
        void withLock(Runnable handle) {
            withLock(() -> {
                handle.run();
                return null;
            });
        }

        CryoFlowMapper flowMapper() {
            return (CryoFlowMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CryoFlowMapper.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "insert" -> {
                            CryoFlow flow = (CryoFlow) args[0];
                            flow.setId(seq.incrementAndGet());
                            flow.setCreateBy(9000000101L);
                            flow.setCreateTime(new java.util.Date());
                            flow.setFlowTime(flow.getFlowTime() == null
                                ? java.time.LocalDateTime.now() : flow.getFlowTime());
                            flows.add(flow);
                            return 1;
                        }
                        case "selectList" -> {
                            return undeletedFlows();
                        }
                        case "selectById" -> {
                            Long id = (Long) args[0];
                            for (CryoFlow flow : undeletedFlows()) {
                                if (id.equals(flow.getId())) {
                                    return flow;
                                }
                            }
                            return null;
                        }
                        case "updateById" -> {
                            // 实体路径：非 null 的列才进 SET（等价于 SQL 真的执行了）
                            CryoFlow entity = (CryoFlow) args[0];
                            CryoFlow target = flows.stream()
                                .filter(f -> entity.getId() != null && entity.getId().equals(f.getId()))
                                .findFirst().orElseThrow();
                            if (entity.getDelta() != null) {
                                target.setDelta(entity.getDelta());
                            }
                            if (entity.getPurpose() != null) {
                                target.setPurpose(entity.getPurpose());
                            }
                            if (entity.getFlowTime() != null) {
                                target.setFlowTime(entity.getFlowTime());
                            }
                            if (entity.getOperatorName() != null) {
                                target.setOperatorName(entity.getOperatorName());
                            }
                            if (entity.getUpdateBy() != null) {
                                target.setUpdateBy(entity.getUpdateBy());
                            }
                            target.setUpdateTime(new java.util.Date());
                            return 1;
                        }
                        case "update" -> {
                            // wrapper 路径（FIX V33 只发这一种：把 purpose 显式写成 NULL）
                            LambdaUpdateWrapper<?> w = (LambdaUpdateWrapper<?>) args[1];
                            String set = w.getSqlSet();
                            w.getSqlSegment();   // WHERE 的参数是惰性注册的：先生成一次片段
                            Long id = w.getParamNameValuePairs().values().stream()
                                .filter(v -> v instanceof Long)
                                .map(v -> (Long) v)
                                .filter(v -> flows.stream().anyMatch(f -> v.equals(f.getId())))
                                .findFirst().orElseThrow();
                            CryoFlow target = flows.stream().filter(f -> id.equals(f.getId())).findFirst().orElseThrow();
                            if (set.contains("purpose=")) {
                                target.setPurpose(null);
                            }
                            target.setUpdateTime(new java.util.Date());
                            return 1;
                        }
                        case "deleteById" -> {
                            Long id = (Long) args[0];
                            for (CryoFlow flow : flows) {
                                if (id.equals(flow.getId())) {
                                    flow.setDelFlag("1");
                                    flow.setUpdateTime(new java.util.Date());
                                }
                            }
                            return 1;
                        }
                        default -> {
                            return defaults(proxy, method, args);
                        }
                    }
                });
        }

        private static Object defaults(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return "FakeCryoMapper";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }

    }

}
