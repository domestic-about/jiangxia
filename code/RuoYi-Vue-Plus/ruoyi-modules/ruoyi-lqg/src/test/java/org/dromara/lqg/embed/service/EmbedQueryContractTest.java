package org.dromara.lqg.embed.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.embed.domain.Embed;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 列表 wrapper 的契约测试（EMBED-MODEL-001）。
 *
 * <p>钉三件事，全部拿 {@link EmbedQueryService#buildWrapper} <b>真生成的 SQL 片段</b>断言
 * （{@code getTargetSql()} = WHERE 段、{@code #{}} 已换成 {@code ?}；{@code getSqlSegment()} 另含
 * {@code last()} 里的 {@code ORDER BY}）：
 * <ol>
 *   <li><b>按染色筛选不是 {@code LIKE '%HE%'}</b>（accept 3 最后一段 / ticket §2 第 8 条）：
 *       {@code OTHER} 这个值里含 {@code HE} 三个字母（O-T-<b>HE</b>-R），
 *       {@code %HE%} 会把「其他（Masson）」也筛出来。本实现用「两侧补逗号再整体 LIKE」，
 *       参数里是 {@code ,IHC,}、SQL 里没有 {@code %} 通配；</li>
 *   <li><b>{@code mine} 那一组 OR 必须被括号包住</b>并与其它条件相与
 *       —— 顶层裸 {@code .or()} 会让 SQL 退化成 {@code (A AND B) OR C}（D2 的 S1 #105 形态，
 *       全仓已扫过一遍，新写的查询别引入同类形态）；</li>
 *   <li><b>排序两档</b>：不带 {@code sort} = 待核验置顶；带 {@code sort=recent} =
 *       {@code COALESCE(update_time, create_time)} 倒序（EMBED-MP-001 的历史编辑记录）。</li>
 * </ol>
 *
 * <p>★ <b>一个必须知道的 MP 行为</b>：{@code apply(sql, args)} 的实参是<b>惰性</b>登记的
 * —— 不先渲染一次 SQL（{@code getTargetSql()} / {@code getSqlSegment()}），
 * {@code getParamNameValuePairs()} 会是空 map。本类的断言因此一律先渲染再取参数
 * （初稿就踩了：第二个 wrapper 里 {@code containsValue(",HE,")} 假红）。
 *
 * @author EMBED-MODEL-001
 */
class EmbedQueryContractTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Embed.class);
    }

    private static EmbedQueryBo query() {
        return new EmbedQueryBo();
    }

    @Test
    @DisplayName("① 染色筛选 = 数组包含：SQL 里没有 % 通配，参数是 ,IHC, / ,HE,（不是 %IHC%）")
    void stainFilterMatchesWholeElement() {
        EmbedQueryBo q = query();
        q.setStain("IHC");
        LambdaQueryWrapper<Embed> wrapper = EmbedQueryService.buildWrapper(q, null, null);
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("(',' || stain_types || ',') LIKE"),
            "按染色筛选必须补逗号做整元素匹配：" + sql);
        assertFalse(sql.contains("%HE%"), "绝不能出现子串通配的写法：" + sql);
        // ★ 整串必须是 '%,IHC,%'：只写 ',IHC,' 会变成「整串恰好等于 ,IHC,」→ 一行都查不到
        //   （本票第一版就是这样，accept 3 最后一段实测 0 行。参数少一个 % 也是错）
        assertTrue(wrapper.getParamNameValuePairs().containsValue("%,IHC,%"),
            "参数应是两侧带逗号、外层带通配的整元素模式：" + wrapper.getParamNameValuePairs());

        // 反证：查 HE 时的参数是 '%,HE,%'；「其他（Masson）」那条（OTHER）不会命中这个判据
        // （O-T-HE-R 只在 %HE% 下命中 —— 这正是 accept 3 最后一段点名的形态）
        EmbedQueryBo he = query();
        he.setStain("HE");
        LambdaQueryWrapper<Embed> heWrapper = EmbedQueryService.buildWrapper(he, null, null);
        heWrapper.getTargetSql();
        assertTrue(heWrapper.getParamNameValuePairs().containsValue("%,HE,%"),
            heWrapper.getParamNameValuePairs().toString());
        assertFalse(heWrapper.getParamNameValuePairs().containsValue("%HE%"));
        assertFalse(heWrapper.getParamNameValuePairs().containsValue(",HE,"));
    }

    @Test
    @DisplayName("② mine=true 的 OR 包成一组（AND (create_by = ? OR update_by = ?)），不带 sort 时不出现")
    void mineOrIsGrouped() {
        EmbedQueryBo q = query();
        q.setSort("recent");
        q.setMine(true);
        q.setVerifyStatus("valid");
        LambdaQueryWrapper<Embed> wrapper = EmbedQueryService.buildWrapper(q, null, 9000000101L);
        assertTrue(wrapper.getTargetSql().contains("AND (create_by = ? OR update_by = ?)"),
            "一组 OR 必须被括号包住、并与前面的条件相与（顶层裸 OR 会退化成 (A AND B) OR C）："
                + wrapper.getTargetSql());

        EmbedQueryBo noSort = query();
        noSort.setMine(true);
        LambdaQueryWrapper<Embed> noSortWrapper = EmbedQueryService.buildWrapper(noSort, null, 9000000101L);
        assertFalse(noSortWrapper.getTargetSql().contains("create_by"),
            "不带 sort=recent 时 mine 没有范围可收窄，不该多出经手人条件：" + noSortWrapper.getTargetSql());

        // 取不到登录人时不造「我」：条件不出现（也不报错）
        EmbedQueryBo noMe = query();
        noMe.setSort("recent");
        noMe.setMine(true);
        assertFalse(EmbedQueryService.buildWrapper(noMe, null, null).getTargetSql().contains("create_by"));
    }

    @Test
    @DisplayName("③ 排序两档：默认待核验置顶；sort=recent 按 COALESCE 倒序")
    void orderByTwoModes() {
        LambdaQueryWrapper<Embed> plain = EmbedQueryService.buildWrapper(query(), null, null);
        assertTrue(plain.getSqlSegment().contains("ORDER BY (verify_status = 'pending') DESC"),
            "默认必须待核验置顶：" + plain.getSqlSegment());

        EmbedQueryBo recent = query();
        recent.setSort("recent");
        LambdaQueryWrapper<Embed> recentWrapper = EmbedQueryService.buildWrapper(recent, null, null);
        assertTrue(recentWrapper.getSqlSegment().contains("ORDER BY COALESCE(update_time, create_time) DESC"),
            "sort=recent 必须按最后修改（没有则创建）倒序：" + recentWrapper.getSqlSegment());
        assertFalse(recentWrapper.getSqlSegment().contains("verify_status = 'pending'"),
            "sort=recent 只管排序，不再叠待核验置顶");
    }

    @Test
    @DisplayName("④ 等值 / 区间筛选都进 WHERE 且都是 AND（WHERE 段里没有裸 OR）")
    void filtersAreAnded() {
        EmbedQueryBo q = query();
        q.setSampleId(9000001001L);
        q.setVerifyStatus("valid");
        q.setSubmitSource("internal");
        q.setParaffinBlockNo("T-E01");
        q.setSectionTimeBegin(java.time.LocalDate.of(2026, 9, 1));
        q.setSectionTimeEnd(java.time.LocalDate.of(2026, 9, 30));
        LambdaQueryWrapper<Embed> wrapper = EmbedQueryService.buildWrapper(q, null, null);
        // 只看 WHERE 段：getSqlSegment() 里的 "ORDER BY" 本身含 "OR" 三个字母，不能拿来断 OR
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("sample_id = ?"), sql);
        assertTrue(sql.contains("verify_status = ?"), sql);
        assertTrue(sql.contains("submit_source = ?"), sql);
        assertTrue(sql.contains("paraffin_block_no LIKE ?"), sql);
        assertTrue(sql.contains("section_time >= ?"), sql);
        assertTrue(sql.contains("section_time <= ?"), sql);
        assertFalse(sql.contains(" OR "), "WHERE 段里不应出现裸 OR：" + sql);
    }

}
