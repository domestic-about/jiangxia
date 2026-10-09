package org.dromara.lqg.sample.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;

/**
 * 「按所挂样本的种属筛」（CR-20261009-18）—— 石蜡包埋、-80 冻存两张表的列表 / 导出共用。
 *
 * <p>★ 种属只住在样本行上（{@code t_lqg_sample.species}），石蜡块与冻存批次只有 {@code sample_id}：
 * 这里用<b>子查询</b> {@code sample_id IN (SELECT id FROM t_lqg_sample WHERE …)}，而不是先查出 id 集合再
 * {@code in(…)} —— 「人」可能对应几千个样本，id 列表拼进 SQL 会越来越长；子查询交给库。
 * 子查询里带 {@code del_flag = '0'}：已软删的样本不让它名下的行混进筛选结果（与样本表自己的口径一致）。
 *
 * <p>★ 值走 {@code {0}} 占位参数，不拼字符串；{@link SampleQueryBo#SPECIES_NONE} = 查还没填种属的样本名下的行。
 *
 * @author CR-20261009-18
 */
public final class SampleSpeciesFilter {

    private SampleSpeciesFilter() {
    }

    /**
     * 给一条以 {@code sample_id} 挂样本的表的查询加上「所挂样本的种属 = species」。
     *
     * @param wrapper 石蜡包埋 / 冻存批次的查询
     * @param species 种属筛选值；空 = 不筛
     */
    public static <T> LambdaQueryWrapper<T> bySampleId(LambdaQueryWrapper<T> wrapper, String species) {
        String value = StringUtils.isBlank(species) ? null : species.trim();
        if (value == null) {
            return wrapper;
        }
        if (SampleQueryBo.SPECIES_NONE.equals(value)) {
            return wrapper.apply("sample_id IN (SELECT id FROM t_lqg_sample WHERE del_flag = '0' AND species IS NULL)");
        }
        return wrapper.apply("sample_id IN (SELECT id FROM t_lqg_sample WHERE del_flag = '0' AND species = {0})", value);
    }

}
