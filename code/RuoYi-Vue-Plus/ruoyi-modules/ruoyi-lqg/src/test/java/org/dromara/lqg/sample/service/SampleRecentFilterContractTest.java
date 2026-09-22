package org.dromara.lqg.sample.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.encrypt.properties.EncryptorProperties;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code sort=recent} 支的「经手人」OR 包组 + {@code mine=true} 收窄 的契约测试
 * （issue #105 · S1 · D2 r3 返工）。
 *
 * <p>钉法：拿 {@link SampleQueryService#buildWrapper} **真生成的 SQL 片段**做结构断言
 * （{@code getTargetSql()}，{@code #{}} 已换成 {@code ?}），而不是读源码字符串 ——
 * 后者只能证明「写了某个写法」，证明不了「拼出来的 SQL 结构对」。
 *
 * <p>★ 判据是 {@link #handlerGroupIsAnded}：<b>「经手人」那两项必须共处一个括号组，
 * 并被一个 {@code AND} 接到前面的筛选链上</b>（即片段里出现
 * {@code " AND (create_by IN (…) OR update_by IN (…))"}）。
 *
 * <p>为什么不能用「括号深度 0 处没有 OR」当判据（本类初稿踩过的坑）：MyBatis-Plus 3.5.16 的
 * {@code getCustomSqlSegment()} 会把整段表达式再套一层括号，于是**修前**的病灶
 * {@code (sample_kind = ? AND create_by IN (…) OR update_by IN (…))} 里那个 OR 也在深度 1
 * —— 深度判据对它恒绿，抓不到任何东西。修前的 p6spy 原始 SQL 就是这个形状
 * （见 {@code doc/waves/reports/D2-rework-r3-issue105.md}）：外层的括号只把整段包起来，
 * 里层缺的括号让 SQL 退化成 {@code (sample_kind AND create_by∈内部) OR update_by∈内部}，
 * 只要某行的 {@code update_by} 是内部账号，{@code sampleKind / sourceUnitId / internalNo}
 * 等筛选**全部**失效。
 *
 * <p>类目身份不可越类改（issue #105 的另一半）在
 * {@code org.dromara.lqg.sample.mp.MpSampleContractTest}
 * —— 那个断言要碰包内可见的 {@code MpSampleService.assertKindUnchanged}。
 *
 * @author D2-issue105 返工
 */
class SampleRecentFilterContractTest {

    /**
     * 不启 Spring 容器时 MyBatis-Plus 的 lambda 列名缓存（{@code TableInfo}）不会自动建，
     * 直接用 {@code Sample::getSampleKind} 会抛
     * 「MybatisPlus can not find lambda cache for this entity」—— 手工注册一次
     * （等价于启动时 MP 的实体扫描）。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Sample.class);
    }

    private static final String INTERNAL = SampleQueryService.INTERNAL_USERS_SQL;

    /** 修后应有的形状：一组 OR 被括号包住、整体 AND 在前面那些筛选后面。 */
    private static final String HANDLER_GROUP =
        "(create_by IN (" + INTERNAL + ") OR update_by IN (" + INTERNAL + "))";

    /** 「只看我提交的」那一组（同样是 OR，同样包住）。 */
    private static final String MINE_GROUP = "(create_by = ? OR update_by = ?)";

    /** 不启 Spring：本类只碰 wrapper 组装，用不到 mapper / 档案查询（加密列两个筛选都不传 → 不会真加密）。 */
    private static SampleQueryService service() {
        return new SampleQueryService(
            null,
            new SampleFieldCipher(new EncryptorProperties()),
            null,
            null);
    }

    private static SampleQueryBo recentQuery() {
        SampleQueryBo q = new SampleQueryBo();
        q.setSort("recent");
        return q;
    }

    // ── 1. OR 包组 ────────────────────────────────────────────────────────────

    /**
     * 组合筛选（{@code sampleKind=organoid}）下，「经手人」那一组必须被括号包住、与筛选链相与。
     *
     * <p>这就是 D2 r3 L2 的病灶形态：{@code ?sampleKind=organoid&sort=recent} 修前返回
     * {@code [9000001001(tissue), 9000001009(organoid)]}，修后只返回 organoid 那一条。
     */
    @Test
    void recentSortWrapsTheHandlerOrSoItCannotSwallowEarlierFilters() {
        SampleQueryBo q = recentQuery();
        q.setSampleKind("organoid");

        String sql = service().buildWrapper(q, null, null).getTargetSql();

        assertTrue(sql.contains("sample_kind = ? AND " + HANDLER_GROUP),
            "★ sampleKind 与「经手人」那一组之间必须是 AND 一个**括号组**；实际片段 = " + sql);
        assertTrue(handlerGroupIsAnded(sql), "★ 经手人那两项必须共处一个括号组；实际片段 = " + sql);
    }

    /** 多个筛选 + {@code keyword}（自己那一组 OR）+ {@code sort=recent}：两组 OR 都要各自包住。 */
    @Test
    void keywordGroupAndHandlerGroupAreBothNestedUnderTheAndChain() {
        SampleQueryBo q = recentQuery();
        q.setSampleKind("tissue");
        q.setSourceUnitId(9000009002L);
        q.setInternalNo("T-hli06");
        q.setKeyword("A 医院");

        String sql = service().buildWrapper(q, null, null).getTargetSql();

        assertTrue(handlerGroupIsAnded(sql), "★ 经手人那一组要包住；实际片段 = " + sql);
        assertTrue(sql.contains("(internal_no = ? OR source_unit_name LIKE ?)"),
            "keyword 那一组仍是 own 括号（SAMPLE-MP-002 的搜索框）");
    }

    // ── 2. mine=true 收窄 ────────────────────────────────────────────────────

    /**
     * {@code mine=true} 与不带 {@code mine} 的对照：多出来的必须是一个 **AND 上的**括号组
     * （{@code create_by = 我 OR update_by = 我}），而不是挂在第二个析取项上的尾巴。
     *
     * <p>这是「admin 建的一行、staff 从没碰过，{@code sort=recent&mine=true} 仍返回它」
     * 那条复现的机器钉子。
     */
    @Test
    void mineNarrowsWithAnAndedGroupInsteadOfLandingOnTheSecondDisjunct() {
        Long me = 9000000101L;

        SampleQueryBo withoutMine = recentQuery();
        SampleQueryBo withMine = recentQuery();
        withMine.setMine(Boolean.TRUE);

        String base = service().buildWrapper(withoutMine, null, me).getTargetSql();
        String narrowed = service().buildWrapper(withMine, null, me).getTargetSql();

        String orderBy = " ORDER BY COALESCE(update_time, create_time) DESC, id DESC";

        assertTrue(narrowed.contains(HANDLER_GROUP + " AND " + MINE_GROUP),
            "★ mine 那一组必须 AND 在「经手人 ∈ 内部」之后（相与，不是并列）；实际片段 = " + narrowed);
        // 「收窄」的形状证据：等于基线 + 一组 AND（严格更强，不是换一组条件）
        assertEquals(base.replace(orderBy, ""), narrowed.replace(" AND " + MINE_GROUP, "").replace(orderBy, ""),
            "mine=true 只多了那一组 AND，别的筛选一个字都不许少");
        assertTrue(narrowed.endsWith("ORDER BY COALESCE(update_time, create_time) DESC, id DESC"),
            "收窄不改排序（历史编辑记录按最后修改倒序）");
    }

    /** 取不到当前登录人（匿名 / 定时任务）：{@code mine=true} 不猜成本人，也不把条件拼坏。 */
    @Test
    void mineWithoutAValidUserAddsNothing() {
        SampleQueryBo q = recentQuery();
        q.setMine(Boolean.TRUE);
        String sql = service().buildWrapper(q, null, null).getTargetSql();
        assertFalse(sql.contains("create_by = ? OR update_by = ?"), "取不到登录人时不许凭空造一个「我」");
        assertTrue(sql.contains(HANDLER_GROUP), "经手人那一组还在（唯一条件，MP 自己那层括号包住）；实际片段 = " + sql);
    }

    /** 不带 {@code sort=recent}（内部管理表格页 / 工作台总表）不许带任何「经手人」条件。 */
    @Test
    void theHandlerGroupOnlyExistsOnTheRecentBranch() {
        SampleQueryBo q = new SampleQueryBo();
        q.setSampleKind("tissue");
        String sql = service().buildWrapper(q, null, 9000000101L).getTargetSql();
        assertFalse(sql.contains("create_by IN ("), "不带 sort=recent 时不该有「经手人 ∈ 内部」这一组");
        assertTrue(sql.contains("ORDER BY (verify_status = 'pending') DESC"), "不带 sort=recent 走「待核验置顶」");
        // 顺带钉住「待核验置顶」还在（SAMPLE-WEB-001 accept 1 末段）
        assertTrue(sql.contains("create_time DESC") && sql.contains("id DESC"));
    }

    /**
     * ★ 判据自检（防同义反复）：用**修前 / 修后各自的 p6spy 原始 SQL**（issue #105 的证据）
     * 走同一个判据，修前的必须判否、修后的必须判是 —— 否则上面几条断言什么也没证明。
     *
     * <p>两句都来自 {@code doc/waves/reports/D2-rework-r3-issue105.md} 的
     * {@code sampleKind=organoid + sort=recent} 那一条。
     */
    @Test
    void thePredicateDiscriminatesThePreFixSqlFromThePostFixSql() {
        String preFix = "sample_kind = ? AND create_by IN (" + INTERNAL + ")"
            + " OR update_by IN (" + INTERNAL + ")"
            + " AND (create_by = ? OR update_by = ?)";
        String postFix = "sample_kind = ? AND (create_by IN (" + INTERNAL + ")"
            + " OR update_by IN (" + INTERNAL + "))"
            + " AND (create_by = ? OR update_by = ?)";

        assertFalse(handlerGroupIsAnded(preFix), "★ 判据必须能判否修前那种「顶层 OR」");
        assertTrue(handlerGroupIsAnded(postFix), "修后那种「AND 一个括号组」必须判是");
    }

    // ── helper ───────────────────────────────────────────────────────────────

    /**
     * 「经手人」那两项是否共处一个括号组。
     *
     * <p>判据只认两种形状（都说明这两项被当成**一组**、而不是裸挂在 OR 链上）：
     * <ul>
     *   <li>{@code AND (create_by IN (…) OR update_by IN (…))} —— 前面还有别的筛选时；</li>
     *   <li>{@code ((create_by IN (…) OR update_by IN (…)))} —— 它是唯一条件时，
     *       MyBatis-Plus 自己那层外括号把它整个包住（没有 AND 可找）。</li>
     * </ul>
     *
     * <p>修前（顶层裸 {@code .or()}）两种都不成立：要么是
     * {@code AND create_by IN (…) OR update_by IN (…) …}（组的开括号不见了），
     * 要么那层外括号里还夹着后面的 {@code AND}（说明 OR 没把后面的条件圈进来）。
     */
    private static boolean handlerGroupIsAnded(String sql) {
        return sql.contains("AND " + HANDLER_GROUP) || sql.contains("(" + HANDLER_GROUP + ")");
    }

}
