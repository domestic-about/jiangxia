package org.dromara.lqg.stat.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.stat.domain.vo.PersonCountRow;

import java.time.LocalDate;
import java.util.List;

/**
 * 操作人统计的聚合查询（CR-20261012-22）：每张表一条 GROUP BY，按「人」分组。
 *
 * <p>★ <b>「人」的分组键 = 账号 id；没关联账号的按姓名文字；两样都没有的归一组</b>（没填）。
 * 这与列表上「按人筛」的口径（{@code PersonKey}）一一对应 —— 统计页的数字点过去，列表筛出来的就是这么多条。
 *
 * <p>★ 各表的口径（与对应列表的筛选对得上）：
 * <ul>
 *   <li>样本两张表：未删、<b>已核验有效</b>（待核验 / 判无效的不算收样），按<b>收样日期</b>；</li>
 *   <li>石蜡包埋：未删、已核验有效（待核验的送样还没有石蜡块编号，不算一块），按<b>石蜡包埋时间</b>；</li>
 *   <li>-80 冻存：未删的批次，按<b>冻存时间</b>；{@code qty} = 冻存数量（初始支数）之和，不是当前剩余。</li>
 * </ul>
 * 带了时间段时，那一栏时间没填的记录自然不在里面（只在「全部」里）。
 *
 * <p>★ 包名以 {@code .mapper} 结尾（若依的 mapper 扫描路径是 {@code org.dromara.**.mapper}）。
 *
 * @author CR-20261012-22
 */
public interface OperatorStatMapper {

    /**
     * 样本（组织 / 类器官）按操作人。
     */
    @Select("""
        <script>
        SELECT t.operator_id AS "userId",
               CASE WHEN t.operator_id IS NULL THEN NULLIF(BTRIM(t.operator_name), '') END AS "text",
               MAX(BTRIM(t.operator_name)) AS "snapshot",
               COUNT(*) AS "count",
               0 AS "qty"
          FROM t_lqg_sample t
         WHERE t.del_flag = '0'
           AND t.verify_status = 'valid'
           AND t.sample_kind = #{kind}
        <if test="begin != null">AND t.receive_date &gt;= #{begin}</if>
        <if test="end != null">AND t.receive_date &lt;= #{end}</if>
         GROUP BY 1, 2
        </script>
        """)
    List<PersonCountRow> sampleByOperator(@Param("kind") String kind, @Param("begin") LocalDate begin,
                                          @Param("end") LocalDate end);

    /**
     * 石蜡包埋按包埋人。
     */
    @Select("""
        <script>
        SELECT t.embed_by_id AS "userId",
               CASE WHEN t.embed_by_id IS NULL THEN NULLIF(BTRIM(t.embed_by), '') END AS "text",
               MAX(BTRIM(t.embed_by)) AS "snapshot",
               COUNT(*) AS "count",
               0 AS "qty"
          FROM t_lqg_embed t
         WHERE t.del_flag = '0'
           AND t.verify_status = 'valid'
        <if test="begin != null">AND t.paraffin_embed_time &gt;= #{begin}</if>
        <if test="end != null">AND t.paraffin_embed_time &lt;= #{end}</if>
         GROUP BY 1, 2
        </script>
        """)
    List<PersonCountRow> embedByEmbedder(@Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * 石蜡包埋按操作人。
     */
    @Select("""
        <script>
        SELECT t.operator_id AS "userId",
               CASE WHEN t.operator_id IS NULL THEN NULLIF(BTRIM(t.operator_name), '') END AS "text",
               MAX(BTRIM(t.operator_name)) AS "snapshot",
               COUNT(*) AS "count",
               0 AS "qty"
          FROM t_lqg_embed t
         WHERE t.del_flag = '0'
           AND t.verify_status = 'valid'
        <if test="begin != null">AND t.paraffin_embed_time &gt;= #{begin}</if>
        <if test="end != null">AND t.paraffin_embed_time &lt;= #{end}</if>
         GROUP BY 1, 2
        </script>
        """)
    List<PersonCountRow> embedByOperator(@Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * -80 冻存按冻存人：批次数 + 冻存数量之和。
     */
    @Select("""
        <script>
        SELECT t.frozen_by_id AS "userId",
               CASE WHEN t.frozen_by_id IS NULL THEN NULLIF(BTRIM(t.frozen_by), '') END AS "text",
               MAX(BTRIM(t.frozen_by)) AS "snapshot",
               COUNT(*) AS "count",
               COALESCE(SUM(t.init_qty), 0) AS "qty"
          FROM t_lqg_cryo_batch t
         WHERE t.del_flag = '0'
        <if test="begin != null">AND t.freeze_time &gt;= #{begin}</if>
        <if test="end != null">AND t.freeze_time &lt;= #{end}</if>
         GROUP BY 1, 2
        </script>
        """)
    List<PersonCountRow> cryoByFrozenBy(@Param("begin") LocalDate begin, @Param("end") LocalDate end);

}
