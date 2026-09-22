package org.dromara.lqg.cryo.batch.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link CryoChildrenChecker} 的判据契约测试（CRYO-MODEL-001）。
 *
 * <p>判据 = 「该样本名下有没有<b>未删</b>的冻存批次」，落在<b>一个</b> {@code selectCount} 上：
 * <pre>
 * SELECT count(*) FROM t_lqg_cryo_batch WHERE sample_id = ? AND del_flag = '0'
 * </pre>
 * 前一个条件由本类显式写（{@code eq(sampleId)}），后一个由 {@code @TableLogic} 在注入期补
 * （{@code getTargetSql()} 里看不到它，所以用例 ④ 另外用反射钉住 {@code delFlag} 上的注解）。
 *
 * <p>★ <b>为什么这里没有 {@code verify_status}</b>（与 {@code EmbedChildrenChecker} 的
 * 唯一差异）：{@code t_lqg_cryo_batch} 没有核验状态列 —— 冻存批次建出来即生效，
 * 没有「待核验」这一档。用例 ③ 正面钉这个事实（SQL 里冒出 {@code verify_status} 就是抄错了表）。
 *
 * <p>★ <b>为什么这不会打红 D2 的 {@code SAMPLE-VERIFY-001} accept 1</b>：它对该样本
 * <b>1002</b> 做 {@code valid → invalid}，而 1002 名下没有任何冻存批次
 * （3001/3004 挂 1001、3005 挂 1004、3002/3007 挂 1009、3003/3006/3008 挂 1008）。
 *
 * @author CRYO-MODEL-001
 */
class CryoChildrenCheckerContractTest {

    /** seed 里挂了冻存批次的样本（3005 挂 1004）。 */
    private static final long SAMPLE_WITH_BATCH = 9000001004L;

    /** seed 里<b>没有</b>冻存批次的样本（D2 的 accept 1 对 1002 做 valid→invalid）。 */
    private static final long SAMPLE_WITHOUT_BATCH = 9000001002L;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    /**
     * 用一个只认 {@code selectCount} 的动态代理把 checker 的查询条件截下来。
     */
    @SuppressWarnings("unchecked")
    private static CryoChildrenChecker checkerCapturing(AtomicReference<Wrapper<CryoBatch>> captured, long count) {
        CryoBatchMapper mapper = (CryoBatchMapper) Proxy.newProxyInstance(
            CryoChildrenCheckerContractTest.class.getClassLoader(),
            new Class<?>[]{CryoBatchMapper.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "selectCount" -> {
                        captured.set((Wrapper<CryoBatch>) args[0]);
                        return count;
                    }
                    case "toString" -> {
                        return "stub-CryoBatchMapper";
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(proxy);
                    }
                    case "equals" -> {
                        return proxy == args[0];
                    }
                    default -> throw new UnsupportedOperationException(
                        "本测试只允许 selectCount，实际调用了：" + method.getName());
                }
            });
        return new CryoChildrenChecker(mapper);
    }

    @Test
    @DisplayName("① 判据只按 sample_id 收窄（一条 selectCount，不逐行查、不手写 SQL）")
    void countsBySampleIdOnly() {
        AtomicReference<Wrapper<CryoBatch>> captured = new AtomicReference<>();
        CryoChildrenChecker checker = checkerCapturing(captured, 1L);

        assertTrue(checker.hasChildren(SAMPLE_WITH_BATCH), "名下有未删批次 → true");

        Wrapper<CryoBatch> wrapper = captured.get();
        assertNotNull(wrapper);
        LambdaQueryWrapper<CryoBatch> query = (LambdaQueryWrapper<CryoBatch>) wrapper;
        String sql = query.getTargetSql();
        assertTrue(sql.contains("sample_id"), "必须按样本收窄：" + sql);
        assertTrue(query.getParamNameValuePairs().containsValue(SAMPLE_WITH_BATCH),
            "参数里就是调用方给的样本 id：" + query.getParamNameValuePairs());
    }

    @Test
    @DisplayName("② count = 0 → false（1002 名下没有冻存批次，D2 的 valid→invalid 仍放行）")
    void zeroCountMeansNoChildren() {
        AtomicReference<Wrapper<CryoBatch>> captured = new AtomicReference<>();
        CryoChildrenChecker checker = checkerCapturing(captured, 0L);
        assertFalse(checker.hasChildren(SAMPLE_WITHOUT_BATCH));
    }

    @Test
    @DisplayName("③ 判据里没有 verify_status —— 冻存批次表没有核验状态这一档（抄包埋的判据就会多出来）")
    void noVerifyStatusInCryoJudgement() {
        AtomicReference<Wrapper<CryoBatch>> captured = new AtomicReference<>();
        checkerCapturing(captured, 1L).hasChildren(SAMPLE_WITH_BATCH);
        LambdaQueryWrapper<CryoBatch> query = (LambdaQueryWrapper<CryoBatch>) captured.get();
        String sql = query.getTargetSql();
        assertFalse(sql.contains("verify_status"), "t_lqg_cryo_batch 没有核验状态列，判据里不该有它：" + sql);
        assertFalse(query.getParamNameValuePairs().containsValue("valid"), query.getParamNameValuePairs().toString());
        assertFalse(query.getParamNameValuePairs().containsValue("pending"), query.getParamNameValuePairs().toString());
        // 实体上确实没有这一列（不是「判据漏写」而是「本表没有这个概念」）
        for (Field field : CryoBatch.class.getDeclaredFields()) {
            assertFalse("verifyStatus".equals(field.getName()),
                "冻存批次不该有 verifyStatus —— 建出来即生效");
        }
    }

    @Test
    @DisplayName("④ sampleId 为空 → false，且不查库（不抛异常）")
    void nullSampleIdIsFalse() {
        // 代理的 default 分支会抛 UnsupportedOperationException —— 走到查库就会炸，证明没查
        CryoChildrenChecker checker = checkerCapturing(new AtomicReference<>(), 1L);
        assertFalse(checker.hasChildren(null));
    }

    @Test
    @DisplayName("⑤ 软删过滤靠 @TableLogic：CryoBatch.delFlag 必须是逻辑删列（seed 的 3008 是软删的）")
    void entityIsLogicDeleted() throws Exception {
        Field delFlag = CryoBatch.class.getDeclaredField("delFlag");
        assertNotNull(delFlag.getAnnotation(com.baomidou.mybatisplus.annotation.TableLogic.class),
            "t_lqg_cryo_batch.del_flag 必须带 @TableLogic（软删的 3008 不算下游记录）");
        assertEquals("t_lqg_cryo_batch", CryoBatch.class.getAnnotation(
            com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

}
