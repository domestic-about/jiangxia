package org.dromara.lqg.embed.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 石蜡包埋列表 / 导出按<b>所挂样本的种属</b>筛（CR-20261009-18，{@code SampleSpeciesFilter}）。
 *
 * <p>钉三件事（拿 {@link EmbedQueryService#buildWrapper} 真生成的 SQL 断）：
 * <ol>
 *   <li>是子查询 {@code sample_id IN (SELECT id FROM t_lqg_sample WHERE del_flag = '0' AND species = ?)}，
 *       值走绑定参数，SQL 文本里没有「人」这个字（不拼字符串）；</li>
 *   <li>{@code __none__} = 样本还没填种属（{@code species IS NULL}），没有参数；</li>
 *   <li>不带 / 空白 = 不筛（SQL 里没有 {@code species}）。</li>
 * </ol>
 *
 * @author CR-20261009-18
 */
class EmbedSpeciesFilterContractTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Embed.class);
    }

    private static String sqlOf(String species) {
        EmbedQueryBo q = new EmbedQueryBo();
        q.setSpecies(species);
        return EmbedQueryService.buildWrapper(q, null, null).getTargetSql();
    }

    @Test
    @DisplayName("① 种属 = 人：子查询 + 绑定参数，已软删的样本不算")
    void speciesIsASubQueryWithABoundValue() {
        EmbedQueryBo q = new EmbedQueryBo();
        q.setSpecies(" 人 ");
        LambdaQueryWrapper<Embed> wrapper = EmbedQueryService.buildWrapper(q, null, null);
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("sample_id IN (SELECT id FROM t_lqg_sample WHERE del_flag = '0' AND species = ?)"), sql);
        assertFalse(sql.contains("人"), "值不许拼进 SQL：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("人"),
            "参数是去了首尾空白的种属：" + wrapper.getParamNameValuePairs());
    }

    @Test
    @DisplayName("② __none__ = 样本还没填种属（老记录）：species IS NULL")
    void noneMeansSpeciesIsNull() {
        String sql = sqlOf(SampleQueryBo.SPECIES_NONE);
        assertTrue(sql.contains("sample_id IN (SELECT id FROM t_lqg_sample WHERE del_flag = '0' AND species IS NULL)"), sql);
    }

    @Test
    @DisplayName("③ 不带 / 空白 = 不筛")
    void blankMeansNoFilter() {
        assertFalse(sqlOf(null).contains("species"));
        assertFalse(sqlOf("  ").contains("species"));
    }

}
