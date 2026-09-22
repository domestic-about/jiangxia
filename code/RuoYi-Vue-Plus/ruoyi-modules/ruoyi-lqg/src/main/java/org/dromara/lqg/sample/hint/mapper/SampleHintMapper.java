package org.dromara.lqg.sample.hint.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.sample.hint.vo.SampleHintRow;

import java.util.Collection;
import java.util.List;

/**
 * 「切片染色提示」的<b>唯一一条聚合查询</b>（SAMPLE-HINT-001 / FLOW:F-SAMPLE-02.step4）。
 *
 * <p>★ <b>计数口径必须与 {@code EmbedChildrenChecker} 同源</b>（EMBED-MODEL-001 背的那颗
 * 定时炸弹的解药）：
 * <pre>
 *   e.del_flag = '0'            -- 软删的石蜡块不算（seed 的 2005 挂 1008，就是为这条埋的）
 *   e.verify_status = 'valid'   -- 外部提交还没核验 / 判了无效的送样不算一块（seed 的 2006
 *                                  ／挂 1002，pending；CR-20260917-05）
 *   s.del_flag = '0'            -- 所挂样本已软删的行不出现（SAMPLE-HINT-001 accept 1 的
 *                                  独立计数 SQL 里也有这一条 JOIN 条件）
 * </pre>
 * 少任何一条，accept 1 的 1002 / 1008 期望就红，而且与 {@code SampleChildrenChecker}
 * 的「判无效闸」互相打架（D2 的 {@code SAMPLE-VERIFY-001} accept 1 会跟着红）。
 *
 * <p>★ <b>一条 GROUP BY，不是逐行查</b>：一页 20 行只发一次，入参是本页的样本 id 集合
 * （{@code foreach} 拼 {@code IN}）。「逐行 for 里调一次 select」是最自然也最错的实现
 * —— accept 2 的单测用 mapper spy 数调用次数（{@code SampleHintContractTest}）。
 *
 * <p>★ <b>不动库</b>：本接口只有这一个读方法，没有任何写 / 建列 / 回写。在
 * {@code t_lqg_sample} 上偷加 {@code block_count} / {@code has_section} 会让 accept 2 的
 * {@code ddl_vs_ssot} 报「库里有、SSOT 没有」红。
 *
 * <p>★ 包名以 {@code .mapper} 结尾（若依的 mapper 扫描路径是 {@code org.dromara.**.mapper}，
 * 见 {@code application.yml} 的 {@code mybatis-plus.mapperPackage}）。只写到
 * {@code sample.hint} 会起不来（SAMPLE-WEB-001 踩过同型坑：构造器注入找不到 bean）。
 *
 * @author SAMPLE-HINT-001
 */
public interface SampleHintMapper {

    /**
     * 按样本 id 集合批量算出「石蜡块数 / 有没有切片 / 染色并集」。
     *
     * <p>只返回<b>有有效石蜡块</b>的样本；没有包的样本由调用方补零值
     * （{@code SampleHintService.hintsOf}）。
     *
     * <p>染色在 SQL 里只做 {@code STRING_AGG}（拼串），拆开 / 去 {@code NONE} / 去重 /
     * 排序交给 {@code StainHintRules.union} 这个纯函数 —— 聚合里的字符串处理要按块读，
     * 塞进 SQL 会写出没人敢改的表达式。
     *
     * @param sampleIds 本页的样本 id（调用方保证非空、无重复）
     * @return 每个有有效石蜡块的样本一行
     */
    @Select("""
        <script>
        SELECT e.sample_id                         AS "sampleId",
               COUNT(*)                            AS "blockCount",
               BOOL_OR(e.section_time IS NOT NULL) AS "sectioned",
               STRING_AGG(e.stain_types, ',')      AS "stainCsv"
        FROM t_lqg_embed e
        JOIN t_lqg_sample s ON s.id = e.sample_id AND s.del_flag = '0'
        WHERE e.del_flag = '0'
          AND e.verify_status = 'valid'
          AND e.sample_id IN
        <foreach collection="sampleIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
        GROUP BY e.sample_id
        ORDER BY e.sample_id
        </script>
        """)
    List<SampleHintRow> selectHints(@Param("sampleIds") Collection<Long> sampleIds);

}
