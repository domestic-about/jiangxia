package org.dromara.lqg.embed.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.EmbedMarker;
import org.dromara.lqg.embed.mapper.EmbedMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link EmbedChildrenChecker} 的判据契约测试 —— <b>本票背的那颗定时炸弹的解药</b>。
 *
 * <p>判据 = 「该样本名下有没有<b>未删且已核验有效</b>的石蜡块」。两条排除各对应 seed 里一个病灶：
 * <ul>
 *   <li>{@code del_flag='0'}（{@code @TableLogic} 自动补）→ 1008 名下软删的 2005 不算；</li>
 *   <li>{@code verify_status='valid'} → <b>1002 名下 extA 提交的待核验送样 2006 不算</b>。</li>
 * </ul>
 *
 * <p>★ 为什么第二条这么重要：一旦把 pending 也算 children，D2 的 {@code SAMPLE-VERIFY-001}
 * accept 1 对 1002 的 {@code valid → invalid} 会立刻变红（它的 WARN-4 / §7 坑 4 点名的就是这一条）。
 * 本测试直接拿<b>真生成的 wrapper</b> 断言「少了 pending / invalid 的判据就红」，不是读源码字符串。
 *
 * @author EMBED-MODEL-001
 */
class EmbedChildrenCheckerContractTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Embed.class);
        TableInfoHelper.initTableInfo(assistant, EmbedMarker.class);
    }

    /**
     * 用一个只认 {@code selectCount} 的动态代理把 checker 的查询条件截下来。
     */
    @SuppressWarnings("unchecked")
    private static EmbedChildrenChecker checkerCapturing(AtomicReference<Wrapper<Embed>> captured, long count) {
        EmbedMapper mapper = (EmbedMapper) Proxy.newProxyInstance(
            EmbedChildrenCheckerContractTest.class.getClassLoader(),
            new Class<?>[]{EmbedMapper.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "selectCount" -> {
                        captured.set((Wrapper<Embed>) args[0]);
                        return count;
                    }
                    case "toString" -> {
                        return "stub-EmbedMapper";
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
        return new EmbedChildrenChecker(mapper);
    }

    @Test
    @DisplayName("① 判据里有 sample_id 与 verify_status，且只认 valid（pending / invalid 都不算）")
    void countsOnlyValidBlocks() {
        AtomicReference<Wrapper<Embed>> captured = new AtomicReference<>();
        EmbedChildrenChecker checker = checkerCapturing(captured, 1L);

        assertTrue(checker.hasChildren(9000001002L), "有一条 valid 块 → true");

        Wrapper<Embed> wrapper = captured.get();
        assertNotNull(wrapper);
        LambdaQueryWrapper<Embed> query = (LambdaQueryWrapper<Embed>) wrapper;
        String sql = query.getTargetSql();
        assertTrue(sql.contains("sample_id"), "必须按样本收窄：" + sql);
        assertTrue(sql.contains("verify_status"), "必须带核验状态判据：" + sql);
        // 参数里只有 valid；pending 绝不能出现（否则 1002 的 2006 会把它变成 true → D2 红）
        assertTrue(query.getParamNameValuePairs().containsValue("valid"), query.getParamNameValuePairs().toString());
        assertFalse(query.getParamNameValuePairs().containsValue("pending"),
            "★ 待核验的送样不算 children：" + query.getParamNameValuePairs());
        assertFalse(query.getParamNameValuePairs().containsValue("invalid"),
            "无效的送样不算 children：" + query.getParamNameValuePairs());
    }

    @Test
    @DisplayName("② count = 0 → false（没有块 / 只有软删块 / 只有待核验送样的样本都能改判无效）")
    void zeroCountMeansNoChildren() {
        AtomicReference<Wrapper<Embed>> captured = new AtomicReference<>();
        EmbedChildrenChecker checker = checkerCapturing(captured, 0L);
        assertFalse(checker.hasChildren(9000001002L));
    }

    @Test
    @DisplayName("③ sampleId 为空 → false，且不查库（不抛异常）")
    void nullSampleIdIsFalse() {
        // 代理的 default 分支会抛 UnsupportedOperationException —— 走到查库就会炸，证明没查
        EmbedChildrenChecker checker = checkerCapturing(new AtomicReference<>(), 1L);
        assertFalse(checker.hasChildren(null));
    }

    @Test
    @DisplayName("④ 软删过滤靠 @TableLogic：两张表的 delFlag 都必须是逻辑删列")
    void entitiesAreLogicDeleted() throws Exception {
        Field embedDel = Embed.class.getDeclaredField("delFlag");
        assertNotNull(embedDel.getAnnotation(com.baomidou.mybatisplus.annotation.TableLogic.class),
            "t_lqg_embed.del_flag 必须带 @TableLogic（软删的 2005 不算一块）");
        Field markerDel = EmbedMarker.class.getDeclaredField("delFlag");
        assertNotNull(markerDel.getAnnotation(com.baomidou.mybatisplus.annotation.TableLogic.class),
            "t_lqg_embed_marker.del_flag 必须带 @TableLogic（marker 整组替换走软删）");
        assertEquals("t_lqg_embed", Embed.class.getAnnotation(
            com.baomidou.mybatisplus.annotation.TableName.class).value());
        assertEquals("t_lqg_embed_marker", EmbedMarker.class.getAnnotation(
            com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

}
