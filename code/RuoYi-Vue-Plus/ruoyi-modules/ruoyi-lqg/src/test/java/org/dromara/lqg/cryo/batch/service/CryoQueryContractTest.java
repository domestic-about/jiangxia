package org.dromara.lqg.cryo.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;
import org.dromara.lqg.cryo.batch.mapper.CryoBatchMapper;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.remind.mapper.CryoOverdueMapper;
import org.dromara.lqg.cryo.remind.sql.CryoOverdueSqlProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * 列表 wrapper 与三条自定义 SQL 的契约测试（CRYO-MODEL-001；②③⑥⑦ 与 ③ 的排序档由 CRYO-REMIND-001 扩写）。
 *
 * <p>钉七件事，前四件（+⑥）拿 {@link CryoQueryService#buildWrapper} <b>真生成的 SQL 片段</b>断言
 * （{@code getTargetSql()} = WHERE 段 + {@code last()} 的 {@code ORDER BY}、
 * {@code #{}} 已换成 {@code ?}；{@code getSqlSegment()} 同样含 {@code last()}）：
 * <ol>
 *   <li><b>{@code location} 的判据与行上的 location 同源</b>（accept 3 counterfeit：只看
 *       {@code in_minus80} 会把「先 -80 后转液氮」的 3003 漏掉）—— 用例 ①；</li>
 *   <li><b>{@code mine} 那一组 OR 必须被括号包住</b>并与其它条件相与
 *       —— 顶层裸 {@code .or()} 会让 SQL 退化成 {@code (A AND B) OR C}（D2 的 S1 #105）；</li>
 *   <li><b>排序两档</b>：默认「超期置顶 + 创建时间倒序」（CRYO-REMIND-001 起）、
 *       {@code sort=recent} 按 {@code COALESCE} 倒序且<b>不置顶</b>；</li>
 *   <li><b>不带 location / mine 时 WHERE 段里没有裸 OR</b>（别顺手把 OR 提到顶层）；</li>
 *   <li><b>两条自定义 SQL 的原文</b>（用例 ⑤）：剩余聚合必须自己写 {@code del_flag='0'}
 *       （{@code @Select} 不吃 {@code @TableLogic}）、取批次行必须 {@code FOR UPDATE}
 *       （FLOW:F-CRYO-02.step4 的行锁）；</li>
 *   <li><b>{@code overdueOnly} 拼的是唯一那段超期判定片段</b>（用例 ⑥）：四个条件一条不少、
 *       阈值是绑定参数不是字面量、软删流水自己排除；</li>
 *   <li><b>超期清单与计数拼同一段 where</b>（用例 ⑦）：拿两条 {@code @Select} 的注解原文断
 *       「都含 {@code CryoOverdueSqlProvider.WHERE}」—— 清单长度与页签数字恒等的前提。</li>
 * </ol>
 *
 * <p>★ <b>一个必须知道的 MP 行为</b>：{@code apply(sql, args)} 的实参是<b>惰性</b>登记的
 * —— 不先渲染一次 SQL（{@code getTargetSql()} / {@code getSqlSegment()}），
 * {@code getParamNameValuePairs()} 会是空 map（EMBED-MODEL-001 的坑 2）。
 *
 * @author CRYO-MODEL-001
 */
class CryoQueryContractTest {

    /**
     * 本测试用的探针阈值（只是把「阈值」这个入参喂进 wrapper，断的是 SQL 形态，不是口径）。
     */
    private static final int PROBE_DAYS = 9;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    private static CryoQueryBo query() {
        return new CryoQueryBo();
    }

    /**
     * 只取 <b>WHERE 段</b>（切掉 {@code last()} 里的 {@code ORDER BY}）。
     *
     * <p>★ CRYO-REMIND-001 起默认排序里带着「超期置顶」的判定表达式（含 {@code in_minus80}），
     * 而 {@code getTargetSql()} 是「WHERE + last()」拼起来的 —— 断「不带某筛选时一个字都不多」
     * 必须先把 ORDER BY 切掉，否则会把排序键误读成筛选条件。
     */
    private static String whereOnly(LambdaQueryWrapper<CryoBatch> wrapper) {
        String sql = wrapper.getTargetSql();
        int idx = sql.indexOf("ORDER BY");
        return idx < 0 ? sql : sql.substring(0, idx);
    }

    @Test
    @DisplayName("① location 判据与行上的 location 同源：ln2 = (直接进液氮 OR 已转液氮)；minus80 反过来")
    void locationFilterMatchesRowJudgement() {
        CryoQueryBo ln2 = query();
        ln2.setLocation("ln2");
        LambdaQueryWrapper<CryoBatch> ln2Wrapper = CryoQueryService.buildWrapper(ln2, null, null, PROBE_DAYS);
        String ln2Sql = ln2Wrapper.getTargetSql();
        assertTrue(ln2Sql.contains("(in_minus80 = ? OR to_ln2_time IS NOT NULL)"),
            "★ ln2 必须同时认「直接进液氮」与「已登记转液氮」（seed 3003 / 3007）：" + ln2Sql);
        assertTrue(ln2Wrapper.getParamNameValuePairs().containsValue("N"), ln2Wrapper.getParamNameValuePairs().toString());

        CryoQueryBo minus80 = query();
        minus80.setLocation("minus80");
        String minus80Sql = CryoQueryService.buildWrapper(minus80, null, null, PROBE_DAYS).getTargetSql();
        assertTrue(minus80Sql.contains("(in_minus80 = ? AND to_ln2_time IS NULL)"),
            "minus80 = 暂存 -80 且还没转液氮：" + minus80Sql);

        // 不带 location 时一个字都不多（只看 WHERE 段：默认排序键里本来就有 in_minus80）
        assertFalse(whereOnly(CryoQueryService.buildWrapper(query(), null, null, PROBE_DAYS)).contains("in_minus80"));
    }

    @Test
    @DisplayName("② mine=true 的 OR 包成一组（AND (create_by = ? OR update_by = ?)）；取不到登录人不造「我」")
    void mineOrIsGrouped() {
        CryoQueryBo q = query();
        q.setMine(true);
        q.setSort("recent");
        q.setCryoName("T-hli");
        LambdaQueryWrapper<CryoBatch> wrapper = CryoQueryService.buildWrapper(q, null, 9000000101L, PROBE_DAYS);
        assertTrue(wrapper.getTargetSql().contains("AND (create_by = ? OR update_by = ?)"),
            "一组 OR 必须被括号包住、并与前面的条件相与（顶层裸 OR 会退化成 (A AND B) OR C）："
                + wrapper.getTargetSql());

        CryoQueryBo noMe = query();
        noMe.setMine(true);
        assertFalse(whereOnly(CryoQueryService.buildWrapper(noMe, null, null, PROBE_DAYS)).contains("create_by"),
            "取不到登录人时不造「我」：条件不出现（也不报错）");

        // 不带 mine 时一个字都不多
        assertFalse(whereOnly(CryoQueryService.buildWrapper(query(), null, 9000000101L, PROBE_DAYS)).contains("create_by"));
    }

    @Test
    @DisplayName("③ 排序两档：默认超期置顶 + 创建时间倒序；sort=recent 按 COALESCE(update_time, create_time) 倒序")
    void orderByTwoModes() {
        LambdaQueryWrapper<CryoBatch> plain = CryoQueryService.buildWrapper(query(), null, null, PROBE_DAYS);
        String plainSql = plain.getSqlSegment();
        // ★ CRYO-REMIND-001 起默认排序多了「超期置顶」这一档：排序键是唯一那段超期判定片段，
        //   阈值以 #{ew.paramNameValuePairs.<key>} 绑定（不写字面量），非超期行再按创建时间倒序。
        assertTrue(plainSql.contains("ORDER BY CASE WHEN"), "默认排序必须先把超期置顶：" + plainSql);
        assertTrue(plainSql.contains("in_minus80 = 'Y'"), "置顶键就是超期判定的四条件：" + plainSql);
        assertTrue(plainSql.contains("create_time DESC, id DESC"), "置顶之后按创建时间倒序：" + plainSql);
        assertTrue(plain.getParamNameValuePairs().containsKey(CryoQueryService.PIN_DAYS_PARAM_KEY),
            "★ 阈值必须当参数绑定进排序键（不写字面量）：" + plain.getParamNameValuePairs());

        CryoQueryBo recent = query();
        recent.setSort("recent");
        LambdaQueryWrapper<CryoBatch> recentWrapper = CryoQueryService.buildWrapper(recent, null, null, PROBE_DAYS);
        assertTrue(recentWrapper.getSqlSegment().contains("ORDER BY COALESCE(update_time, create_time) DESC"),
            "sort=recent 必须按最后修改（没有则创建）倒序：" + recentWrapper.getSqlSegment());
        assertFalse(recentWrapper.getSqlSegment().contains("CASE WHEN"),
            "sort=recent 那一档（历史编辑记录）不置顶超期：" + recentWrapper.getSqlSegment());
    }

    @Test
    @DisplayName("④ 等值 / 区间筛选都进 WHERE 且都是 AND（不带 location / mine 时 WHERE 段没有裸 OR）")
    void filtersAreAnded() {
        CryoQueryBo q = query();
        q.setSampleId(9000001001L);
        q.setCryoName("T-hli01");
        q.setFreezeTimeBegin(LocalDate.of(2026, 9, 1));
        q.setFreezeTimeEnd(LocalDate.of(2026, 9, 30));
        LambdaQueryWrapper<CryoBatch> wrapper = CryoQueryService.buildWrapper(q, null, null, PROBE_DAYS);
        // 只看 WHERE 段：getSqlSegment() 里的 "ORDER BY" 本身含 "OR" 三个字母，不能拿来断 OR
        String sql = whereOnly(wrapper);
        assertTrue(sql.contains("sample_id = ?"), sql);
        assertTrue(sql.contains("cryo_name LIKE ?"), sql);
        assertTrue(sql.contains("freeze_time >= ?"), sql);
        assertTrue(sql.contains("freeze_time <= ?"), sql);
        assertFalse(sql.contains(" OR "), "WHERE 段里不应出现裸 OR：" + sql);

        // internalNo 那一侧：命中的样本 id 集合走 IN；空集合时别退化成「不过滤 = 全表」
        LambdaQueryWrapper<CryoBatch> byInternal = CryoQueryService.buildWrapper(q, java.util.List.of(9000001001L), null, PROBE_DAYS);
        assertTrue(byInternal.getTargetSql().contains("sample_id IN (?)"), byInternal.getTargetSql());
        LambdaQueryWrapper<CryoBatch> empty = CryoQueryService.buildWrapper(q, java.util.List.of(), null, PROBE_DAYS);
        assertTrue(empty.getTargetSql().contains("sample_id IN ()"), empty.getTargetSql());
    }

    @Test
    @DisplayName("⑥ overdueOnly=true 拼的是唯一那段超期判定片段（阈值当参数绑定，不是字面量）")
    void overdueOnlyUsesSharedFragment() {
        CryoQueryBo q = query();
        q.setOverdueOnly(true);
        LambdaQueryWrapper<CryoBatch> wrapper = CryoQueryService.buildWrapper(q, null, null, PROBE_DAYS);
        String sql = wrapper.getTargetSql();
        // 四个条件一条都不能少（与 CryoOverdueService#isOverdue 逐条对应）
        assertTrue(sql.contains("in_minus80 = 'Y'"), sql);
        assertTrue(sql.contains("to_ln2_time IS NULL"), sql);
        assertTrue(sql.contains("(CURRENT_DATE - freeze_time) >= ?"), sql);
        assertTrue(sql.contains("> 0"), sql);
        assertTrue(sql.contains("f.del_flag = '0'"), "软删流水必须自己排除：" + sql);
        assertFalse(sql.contains("'14'"), "阈值不许写字面量：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(PROBE_DAYS),
            "阈值必须作为参数绑定进来：" + wrapper.getParamNameValuePairs());

        // 不带 overdueOnly 时一个字都不多（只看 WHERE 段）
        assertFalse(whereOnly(CryoQueryService.buildWrapper(query(), null, null, PROBE_DAYS)).contains("in_minus80"));
    }

    @Test
    @DisplayName("⑦ 超期清单与计数的两条自定义 SQL 拼的是同一段 where（清单长度与计数恒等的前提）")
    void overdueSqlSharesOneFragment() throws Exception {
        Method list = CryoOverdueMapper.class.getMethod("selectOverdueList", int.class);
        Method count = CryoOverdueMapper.class.getMethod("selectOverdueCount", int.class);
        String listSql = String.join("\n", list.getAnnotation(Select.class).value());
        String countSql = String.join("\n", count.getAnnotation(Select.class).value());
        assertTrue(listSql.contains(CryoOverdueSqlProvider.WHERE), listSql);
        assertTrue(countSql.contains(CryoOverdueSqlProvider.WHERE), countSql);
        assertTrue(listSql.contains("ORDER BY (CURRENT_DATE - freeze_time) DESC"), "清单按已超天数倒序：" + listSql);
        assertTrue(listSql.contains("del_flag = '0'"), "自定义 SQL 要自己写逻辑删条件：" + listSql);
        assertTrue(listSql.contains("#{days}"), "阈值是参数占位符，不是字面量：" + listSql);
        assertFalse(CryoOverdueSqlProvider.WHERE.contains("'14'"), CryoOverdueSqlProvider.WHERE);
    }

    @Test
    @DisplayName("⑤ 两条自定义 SQL 的原文：剩余聚合自己写 del_flag='0'；取批次行 FOR UPDATE")
    void customSqlTexts() throws Exception {
        Method sum = CryoFlowMapper.class.getMethod("selectDeltaSums", java.util.Collection.class);
        String sumSql = String.join("\n", sum.getAnnotation(Select.class).value());
        assertTrue(sumSql.contains("del_flag = '0'"),
            "★ @Select 不吃 @TableLogic：软删流水必须自己排除（seed 3002 名下就有一条软删的 -1）：" + sumSql);
        assertTrue(sumSql.contains("GROUP BY f.batch_id"), "剩余必须一条 GROUP BY 算完整页，不逐行查：" + sumSql);
        assertTrue(sumSql.contains("SUM(f.delta)"), sumSql);

        Method lock = CryoBatchMapper.class.getMethod("selectByIdForUpdate", Long.class);
        String lockSql = lock.getAnnotation(Select.class).value()[0];
        assertTrue(lockSql.contains("FOR UPDATE"), "改初始支数 / 删批次前必须锁批次行：" + lockSql);
        assertTrue(lockSql.contains("del_flag = '0'"), "自定义 SQL 要自己写逻辑删条件：" + lockSql);

        // 本票不写流水：FlowMapper 自己声明的方法里没有 insert / update / delete 形态的写口
        long writes = Arrays.stream(CryoFlowMapper.class.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("insert") || m.getName().startsWith("update")
                || m.getName().startsWith("delete"))
            .count();
        assertEquals(0, writes, "写流水的接口属于 CRYO-FLOW-001，本票不该新增：" + writes);
    }

    @Test
    @DisplayName("种属（CR-20261009-18）：按所挂样本的种属筛 = 子查询 + 绑定参数；__none__ = 样本没填；不带 = 不筛")
    void speciesFiltersByTheSamplesSpecies() {
        CryoQueryBo q = query();
        q.setSpecies("移植猪");
        LambdaQueryWrapper<CryoBatch> wrapper = CryoQueryService.buildWrapper(q, null, null, PROBE_DAYS);
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("sample_id IN (SELECT id FROM t_lqg_sample WHERE del_flag = '0' AND species = ?)"), sql);
        assertFalse(sql.contains("移植猪"), "值不许拼进 SQL：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("移植猪"), wrapper.getParamNameValuePairs().toString());

        CryoQueryBo none = query();
        none.setSpecies(org.dromara.lqg.sample.domain.bo.SampleQueryBo.SPECIES_NONE);
        assertTrue(CryoQueryService.buildWrapper(none, null, null, PROBE_DAYS).getTargetSql().contains("species IS NULL"));
        assertFalse(whereOnly(CryoQueryService.buildWrapper(query(), null, null, PROBE_DAYS)).contains("species"));
    }

}
