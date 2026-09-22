package org.dromara.lqg.qc.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.qc.service.QcDocRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link QcChildrenChecker} 的判据契约测试。
 *
 * <p>判据 = 「该样本名下有没有<b>未删且已完成（published）</b>的质控文档」，三张表任意一张有就算。
 * 两条排除各对应 seed 里一个病灶：
 * <ul>
 *   <li>{@code del_flag='0'}（{@code @TableLogic} 自动补）→ 软删的文档不算；</li>
 *   <li><b>{@code doc_status='published'}</b> → {@code GET /lqg/qc/{sampleId}} <b>自动建出来的空草稿不算</b>
 *       （seed 里 1005 名下只有一份 draft 的样本质控表：「点开看过一眼」不等于「有下游记录」）。</li>
 * </ul>
 *
 * <p>★ 为什么第二条这么重要：若把 draft 也算 children，任何人在工作台上点开过一次质控文档，
 * 这个样本就永远不能改判无效 —— SAMPLE-VERIFY-001 的 {@code valid → invalid} 会莫名其妙地红。
 * 本测试拿<b>真生成的 wrapper</b>断言条件里只有 {@code published}，不是读源码字符串。
 *
 * @author QC-MODEL-001
 */
class QcChildrenCheckerContractTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, QcSampleDoc.class);
        TableInfoHelper.initTableInfo(assistant, QcOrganoidDoc.class);
        TableInfoHelper.initTableInfo(assistant, QcScoreDoc.class);
    }

    @SuppressWarnings("unchecked")
    private static <M> M mapperStub(Class<M> type, AtomicReference<Wrapper<?>> captured, long count) {
        return (M) Proxy.newProxyInstance(
            QcChildrenCheckerContractTest.class.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "selectCount" -> {
                        captured.set((Wrapper<?>) args[0]);
                        return count;
                    }
                    case "toString" -> {
                        return "stub-" + type.getSimpleName();
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
    }

    private static QcChildrenChecker checker(long sampleCount, long organoidCount, long scoreCount,
                                             AtomicReference<Wrapper<?>> captured) {
        return new QcChildrenChecker(
            mapperStub(QcSampleDocMapper.class, captured, sampleCount),
            mapperStub(QcOrganoidDocMapper.class, captured, organoidCount),
            mapperStub(QcScoreDocMapper.class, captured, scoreCount));
    }

    @Test
    @DisplayName("① 判据里有 sample_id 与 doc_status，且只认 published（draft 不算）")
    void countsOnlyPublishedDocs() {
        AtomicReference<Wrapper<?>> captured = new AtomicReference<>();
        QcChildrenChecker checker = checker(1L, 0L, 0L, captured);

        assertTrue(checker.hasChildren(9000001006L), "有一份 published 的评分表 → true");

        LambdaQueryWrapper<?> query = (LambdaQueryWrapper<?>) captured.get();
        assertNotNull(query, "必须查了库");
        String sql = query.getTargetSql();
        assertTrue(sql.contains("sample_id"), "必须按样本收窄：" + sql);
        assertTrue(sql.contains("doc_status"), "必须带文档状态判据：" + sql);
        assertTrue(query.getParamNameValuePairs().containsValue(QcDocRules.STATUS_PUBLISHED),
            query.getParamNameValuePairs().toString());
        // ★ 只有 published：draft 绝不能出现在条件里（否则「点开过一次」的样本永远不能改判）
        assertFalse(query.getParamNameValuePairs().containsValue(QcDocRules.STATUS_DRAFT),
            "★ 自动建出来的空草稿不算 children：" + query.getParamNameValuePairs());
    }

    @Test
    @DisplayName("② 三张表都没有 published → false（1005 那种「只有草稿」的样本可以改判无效）")
    void zeroCountMeansNoChildren() {
        AtomicReference<Wrapper<?>> captured = new AtomicReference<>();
        assertFalse(checker(0L, 0L, 0L, captured).hasChildren(9000001005L));
        assertNotNull(captured.get(), "查过库（只是没有 published 的行）");
    }

    @Test
    @DisplayName("③ 类器官质控表 / 评分表任意一张 published 也算 children（三张表都查）")
    void anyOfThreeTablesCounts() {
        assertTrue(checker(0L, 1L, 0L, new AtomicReference<>()).hasChildren(9000001001L));
        assertTrue(checker(0L, 0L, 1L, new AtomicReference<>()).hasChildren(9000001001L));
        // 样本质控表没有就先短路了，但结论仍是 true
        assertTrue(checker(0L, 1L, 1L, new AtomicReference<>()).hasChildren(9000001001L));
    }

    @Test
    @DisplayName("④ sampleId 为空 → false，且不查库（不抛异常）")
    void nullSampleIdIsFalse() {
        // 代理的 default 分支会抛 UnsupportedOperationException —— 走到查库就会炸，证明没查
        assertFalse(checker(1L, 1L, 1L, new AtomicReference<>()).hasChildren(null));
    }

    @Test
    @DisplayName("⑤ 三张文档表的 del_flag 都是逻辑删列，表名与 SSOT 一致")
    void entitiesAreLogicDeleted() throws Exception {
        for (Class<?> type : new Class<?>[]{QcSampleDoc.class, QcOrganoidDoc.class, QcScoreDoc.class}) {
            Field del = type.getDeclaredField("delFlag");
            assertNotNull(del.getAnnotation(com.baomidou.mybatisplus.annotation.TableLogic.class),
                type.getSimpleName() + ".del_flag 必须带 @TableLogic（软删的文档不算一份）");
        }
        assertEquals("t_lqg_qc_sample", QcSampleDoc.class.getAnnotation(
            com.baomidou.mybatisplus.annotation.TableName.class).value());
        assertEquals("t_lqg_qc_organoid", QcOrganoidDoc.class.getAnnotation(
            com.baomidou.mybatisplus.annotation.TableName.class).value());
        assertEquals("t_lqg_qc_score", QcScoreDoc.class.getAnnotation(
            com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

}
