package org.dromara.lqg.sample.relation.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.sample.relation.vo.SampleRelationRow;

import java.util.Collection;
import java.util.List;

/**
 * 样本行「石蜡包埋 / 冻存」关联数的<b>唯一一条查询</b>（整页一次，不逐行查）。
 *
 * <p>★ 两个数的判据逐字对准目标页的列表口径（点过去的条数要与这里一致）：
 * <pre>
 *   待核验送样：t_lqg_embed       del_flag = '0' AND verify_status = 'pending'
 *   冻存批次  ：t_lqg_cryo_batch  del_flag = '0'（冻存批次没有核验状态，与 CryoChildrenChecker 同源）
 * </pre>
 * 蜡块数（已核验有效的石蜡块）不在这里 —— 沿用 {@code SampleHintMapper.selectHints} 那一份。
 *
 * <p>★ 为什么不往 {@code SampleHintMapper} 里加方法：那条是「切片染色提示」的口径（只数有效块），
 * 契约测试用一个实现了该接口的计数替身钉「一页一次查询」；再加抽象方法会把两件事绑在一起。
 *
 * <p>★ 包名以 {@code .mapper} 结尾（若依 mapper 扫描路径 {@code org.dromara.**.mapper}）。
 */
public interface SampleRelationMapper {

    /**
     * 按样本 id 集合一次查出每个样本的待核验送样数与冻存批次数。
     *
     * @param sampleIds 本页的样本 id（调用方保证非空、无重复）
     * @return 每个存在的样本一行（两列都是计数，没有的是 0）
     */
    @Select("""
        <script>
        SELECT s.id AS "sampleId",
               (SELECT COUNT(*) FROM t_lqg_embed e
                 WHERE e.sample_id = s.id AND e.del_flag = '0' AND e.verify_status = 'pending') AS "pendingEmbedCount",
               (SELECT COUNT(*) FROM t_lqg_cryo_batch c
                 WHERE c.sample_id = s.id AND c.del_flag = '0') AS "cryoBatchCount"
        FROM t_lqg_sample s
        WHERE s.id IN
        <foreach collection="sampleIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
        ORDER BY s.id
        </script>
        """)
    List<SampleRelationRow> selectCounts(@Param("sampleIds") Collection<Long> sampleIds);

}
