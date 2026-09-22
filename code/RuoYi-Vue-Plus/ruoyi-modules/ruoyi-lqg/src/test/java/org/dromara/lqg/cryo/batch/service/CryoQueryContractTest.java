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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * 列表 wrapper 与两条自定义 SQL 的契约测试（CRYO-MODEL-001）。
 *
 * <p>钉五件事，前四件拿 {@link CryoQueryService#buildWrapper} <b>真生成的 SQL 片段</b>断言
 * （{@code getTargetSql()} = WHERE 段、{@code #{}} 已换成 {@code ?}；{@code getSqlSegment()} 另含
 * {@code last()} 里的 {@code ORDER BY}）：
 * <ol>
 *   <li><b>{@code location} 的判据与行上的 location 同源</b>（accept 3 counterfeit：只看
 *       {@code in_minus80} 会把「先 -80 后转液氮」的 3003 漏掉）—— 用例 ①；</li>
 *   <li><b>{@code mine} 那一组 OR 必须被括号包住</b>并与其它条件相与
 *       —— 顶层裸 {@code .or()} 会让 SQL 退化成 {@code (A AND B) OR C}（D2 的 S1 #105）；</li>
 *   <li><b>排序两档</b>：默认创建时间倒序、{@code sort=recent} 按 {@code COALESCE} 倒序；</li>
 *   <li><b>不带 location / mine 时 WHERE 段里没有裸 OR</b>（别顺手把 OR 提到顶层）；</li>
 *   <li><b>两条自定义 SQL 的原文</b>（用例 ⑤）：剩余聚合必须自己写 {@code del_flag='0'}
 *       （{@code @Select} 不吃 {@code @TableLogic}）、取批次行必须 {@code FOR UPDATE}
 *       （FLOW:F-CRYO-02.step4 的行锁）。</li>
 * </ol>
 *
 * <p>★ <b>一个必须知道的 MP 行为</b>：{@code apply(sql, args)} 的实参是<b>惰性</b>登记的
 * —— 不先渲染一次 SQL（{@code getTargetSql()} / {@code getSqlSegment()}），
 * {@code getParamNameValuePairs()} 会是空 map（EMBED-MODEL-001 的坑 2）。
 *
 * @author CRYO-MODEL-001
 */
class CryoQueryContractTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CryoBatch.class);
    }

    private static CryoQueryBo query() {
        return new CryoQueryBo();
    }

    @Test
    @DisplayName("① location 判据与行上的 location 同源：ln2 = (直接进液氮 OR 已转液氮)；minus80 反过来")
    void locationFilterMatchesRowJudgement() {
        CryoQueryBo ln2 = query();
        ln2.setLocation("ln2");
        LambdaQueryWrapper<CryoBatch> ln2Wrapper = CryoQueryService.buildWrapper(ln2, null, null);
        String ln2Sql = ln2Wrapper.getTargetSql();
        assertTrue(ln2Sql.contains("(in_minus80 = ? OR to_ln2_time IS NOT NULL)"),
            "★ ln2 必须同时认「直接进液氮」与「已登记转液氮」（seed 3003 / 3007）：" + ln2Sql);
        assertTrue(ln2Wrapper.getParamNameValuePairs().containsValue("N"), ln2Wrapper.getParamNameValuePairs().toString());

        CryoQueryBo minus80 = query();
        minus80.setLocation("minus80");
        String minus80Sql = CryoQueryService.buildWrapper(minus80, null, null).getTargetSql();
        assertTrue(minus80Sql.contains("(in_minus80 = ? AND to_ln2_time IS NULL)"),
            "minus80 = 暂存 -80 且还没转液氮：" + minus80Sql);

        // 不带 location 时一个字都不多
        assertFalse(CryoQueryService.buildWrapper(query(), null, null).getTargetSql().contains("in_minus80"));
    }

    @Test
    @DisplayName("② mine=true 的 OR 包成一组（AND (create_by = ? OR update_by = ?)）；取不到登录人不造「我」")
    void mineOrIsGrouped() {
        CryoQueryBo q = query();
        q.setMine(true);
        q.setSort("recent");
        q.setCryoName("T-hli");
        LambdaQueryWrapper<CryoBatch> wrapper = CryoQueryService.buildWrapper(q, null, 9000000101L);
        assertTrue(wrapper.getTargetSql().contains("AND (create_by = ? OR update_by = ?)"),
            "一组 OR 必须被括号包住、并与前面的条件相与（顶层裸 OR 会退化成 (A AND B) OR C）："
                + wrapper.getTargetSql());

        CryoQueryBo noMe = query();
        noMe.setMine(true);
        assertFalse(CryoQueryService.buildWrapper(noMe, null, null).getTargetSql().contains("create_by"),
            "取不到登录人时不造「我」：条件不出现（也不报错）");

        // 不带 mine 时一个字都不多
        assertFalse(CryoQueryService.buildWrapper(query(), null, 9000000101L).getTargetSql().contains("create_by"));
    }

    @Test
    @DisplayName("③ 排序两档：默认创建时间倒序；sort=recent 按 COALESCE(update_time, create_time) 倒序")
    void orderByTwoModes() {
        LambdaQueryWrapper<CryoBatch> plain = CryoQueryService.buildWrapper(query(), null, null);
        assertTrue(plain.getSqlSegment().contains("ORDER BY create_time DESC, id DESC"),
            "默认按创建时间倒序：" + plain.getSqlSegment());

        CryoQueryBo recent = query();
        recent.setSort("recent");
        LambdaQueryWrapper<CryoBatch> recentWrapper = CryoQueryService.buildWrapper(recent, null, null);
        assertTrue(recentWrapper.getSqlSegment().contains("ORDER BY COALESCE(update_time, create_time) DESC"),
            "sort=recent 必须按最后修改（没有则创建）倒序：" + recentWrapper.getSqlSegment());
    }

    @Test
    @DisplayName("④ 等值 / 区间筛选都进 WHERE 且都是 AND（不带 location / mine 时 WHERE 段没有裸 OR）")
    void filtersAreAnded() {
        CryoQueryBo q = query();
        q.setSampleId(9000001001L);
        q.setCryoName("T-hli01");
        q.setFreezeTimeBegin(LocalDate.of(2026, 9, 1));
        q.setFreezeTimeEnd(LocalDate.of(2026, 9, 30));
        LambdaQueryWrapper<CryoBatch> wrapper = CryoQueryService.buildWrapper(q, null, null);
        // 只看 WHERE 段：getSqlSegment() 里的 "ORDER BY" 本身含 "OR" 三个字母，不能拿来断 OR
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("sample_id = ?"), sql);
        assertTrue(sql.contains("cryo_name LIKE ?"), sql);
        assertTrue(sql.contains("freeze_time >= ?"), sql);
        assertTrue(sql.contains("freeze_time <= ?"), sql);
        assertFalse(sql.contains(" OR "), "WHERE 段里不应出现裸 OR：" + sql);

        // internalNo 那一侧：命中的样本 id 集合走 IN；空集合时别退化成「不过滤 = 全表」
        LambdaQueryWrapper<CryoBatch> byInternal = CryoQueryService.buildWrapper(q, java.util.List.of(9000001001L), null);
        assertTrue(byInternal.getTargetSql().contains("sample_id IN (?)"), byInternal.getTargetSql());
        LambdaQueryWrapper<CryoBatch> empty = CryoQueryService.buildWrapper(q, java.util.List.of(), null);
        assertTrue(empty.getTargetSql().contains("sample_id IN ()"), empty.getTargetSql());
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

}
