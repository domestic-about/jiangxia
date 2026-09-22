package org.dromara.lqg.cryo.batch.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.cryo.batch.domain.CryoFlow;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowDeltaRow;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowVo;

import java.util.Collection;
import java.util.List;

/**
 * 冻存流水 mapper（FIELD-ssot 的 t_lqg_cryo_flow）。
 *
 * <p>★ <b>本票只有一条聚合 SQL</b>（{@link #selectDeltaSums(Collection)}）：
 * 「剩余 = init_qty + SUM(未删流水的 delta)」按<b>本页的批次 id 集合</b>一次算完，
 * 不逐行查（ticket §2：剩余用一条聚合查询按本页 id 集合算）。逐行 {@code selectList} 求和
 * 是最自然也最错的实现 —— 一页 20 行就是 20 次往返。
 *
 * <p>★ <b>软删流水必须排除</b>（accept 3 的 counterfeit 点名）：seed 的 3002 名下挂了一条
 * {@code del_flag='1'} 的 {@code -1}，算进去会让它的剩余从 4 变成 3。
 * 这条 SQL 是自定义 {@code @Select}，MP 不会自动补逻辑删条件，所以 {@code del_flag = '0'} <b>手写</b>。
 *
 * <p>★ 本票<b>不写</b>流水（增删改在 CRYO-FLOW-001）：本接口除聚合外只有 MP 自带的读方法。
 *
 * @author CRYO-MODEL-001
 */
public interface CryoFlowMapper extends BaseMapperPlus<CryoFlow, CryoFlowVo> {

    /**
     * 按批次 id 集合批量算出「未删流水 delta 之和」。
     *
     * <p>只返回<b>有未删流水</b>的批次；没有流水的批次由调用方补 0
     * （{@code CryoQueryService.remainingOf}）—— 所以调用方<b>不许</b>把「查不到」当成「批次不存在」。
     *
     * @param batchIds 本页的批次 id（调用方保证非空、无重复）
     * @return 每个有未删流水的批次一行
     */
    @Select("""
        <script>
        SELECT f.batch_id                 AS "batchId",
               COALESCE(SUM(f.delta), 0)  AS "totalDelta"
        FROM t_lqg_cryo_flow f
        WHERE f.del_flag = '0'
          AND f.batch_id IN
        <foreach collection="batchIds" item="bid" open="(" separator="," close=")">#{bid}</foreach>
        GROUP BY f.batch_id
        ORDER BY f.batch_id
        </script>
        """)
    List<CryoFlowDeltaRow> selectDeltaSums(@Param("batchIds") Collection<Long> batchIds);

}
