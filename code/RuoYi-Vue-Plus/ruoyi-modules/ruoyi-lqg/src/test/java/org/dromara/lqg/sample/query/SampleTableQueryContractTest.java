package org.dromara.lqg.sample.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.query.mapper.SampleSubmitterProfileMapper;
import org.junit.jupiter.api.Test;

/**
 * 工作台样本总表的筛选契约测试（SAMPLE-WEB-001）。
 *
 * <p>本类不启 Spring 容器，钉的是<b>票面最容易做反</b>而端到端 accept 又要跑一遍后端才撞得到的几件事：
 *
 * <ol>
 *   <li><b>{@code submitSource} 钉在样本行的 {@code submit_source} 列上</b>，<b>不按提交人当前角色现算</b>。
 *       该列是「提交当时」的快照；按角色现算是这条口径最自然也最错的实现
 *       （SAMPLE-VERIFY-001 的 accept 2 段 5/6 用两侧不同源的独立取证钉住了这一点）。
 *       本类只能钉「这个筛选存在且是样本表上的普通列条件」，现算与否由 accept 1 的
 *       {@code submitSource=internal} 段真值钉（9 条 seed 里角色与快照恰好不一致）。</li>
 *   <li><b>日期区间两端都含</b>（{@code >= begin} 且 {@code <= end}）：accept 1 的两个端点取在
 *       两条样本的外侧各一天，开区间 / 漏一端都会让 1004 或 1005 掉出去。</li>
 *   <li><b>组别筛选先查外部档案、再按 {@code submitter_id} 收窄</b>：样本表上<b>没有</b> group_id 列
 *       （ticket §0 口径复述 2），所以必须有一条「按 {@code t_lqg_ext_profile} 的 group 取
 *       user_id」的语句，而且它<b>不能</b>去过滤档案的核验状态
 *       （accept 1 的 {@code groupId=9000009101} 期望里含 extE 送来的待核验样本）。</li>
 *   <li><b>★ 来源单位不走那条档案查询，走样本行自己的 {@code source_unit_id}</b>（issue #96）：
 *       两条口径曾经被合在一起，导致内部人员录的行（没有外部档案）永远筛不出来 ——
 *       而工作台「来源单位」列显示的正是样本行那一列。本类用「档案 SQL 里不许出现 unit_id」
 *       ＋「service 必须 {@code eq(Sample::getSourceUnitId)} 且只把 groupId 传给档案查询」两条钉死。</li>
 *   <li><b>「待核验置顶」是排序</b>，并且与 {@code sort=recent} 那一档互斥（后者仍按最后修改倒序）。</li>
 * </ol>
 *
 * @author SAMPLE-WEB-001
 */
class SampleTableQueryContractTest {

    @Test
    void queryBoDeclaresTheSevenFiltersTheWorkbenchNeeds() throws Exception {
        // 字段名与 doc/api-contract.md 第 45 行的参数逐字一致（Spring MVC 按名字绑定查询参数）
        String[] expected = {
            "sourceUnitId", "groupId", "submitSource",
            "receiveDateBegin", "receiveDateEnd", "tissueType", "operatorName"
        };
        for (String name : expected) {
            Field field = SampleQueryBo.class.getDeclaredField(name);
            assertNotNull(field, "SampleQueryBo 缺筛选字段 " + name);
        }
        assertEquals(Long.class, SampleQueryBo.class.getDeclaredField("sourceUnitId").getType());
        assertEquals(Long.class, SampleQueryBo.class.getDeclaredField("groupId").getType());
        assertEquals(String.class, SampleQueryBo.class.getDeclaredField("submitSource").getType());
        assertEquals(LocalDate.class, SampleQueryBo.class.getDeclaredField("receiveDateBegin").getType(),
            "日期区间要用 LocalDate 绑 yyyy-MM-dd（String 绑不出来）");
        assertEquals(LocalDate.class, SampleQueryBo.class.getDeclaredField("receiveDateEnd").getType());
        assertEquals(String.class, SampleQueryBo.class.getDeclaredField("tissueType").getType());
        assertEquals(String.class, SampleQueryBo.class.getDeclaredField("operatorName").getType());

        // 老筛选一个都不能被弄丢（SAMPLE-MODEL-001 的 accept 仍钉着它们）
        for (String name : new String[] {"sampleKind", "verifyStatus", "internalNo", "donorName", "hospitalNo"}) {
            assertNotNull(SampleQueryBo.class.getDeclaredField(name), "老筛选 " + name + " 不许丢");
        }
    }

    @Test
    void rowCarriesSubmitterNameAndGroup() throws Exception {
        // 每行补 submitterName / groupId / groupName（ticket §2.1「读时带出」）
        assertEquals(String.class, SampleVo.class.getDeclaredField("submitterName").getType());
        assertEquals(Long.class, SampleVo.class.getDeclaredField("groupId").getType());
        assertEquals(String.class, SampleVo.class.getDeclaredField("groupName").getType());
    }

    @Test
    void groupFilterReadsTheSubmittersProfileAndDoesNotLookAtBindStatus() throws Exception {
        Method select = SampleSubmitterProfileMapper.class.getMethod("selectSubmitterIds", Long.class);
        Select select_ = select.getAnnotation(Select.class);
        assertNotNull(select_, "按组别取提交人必须有一条显式 SQL");
        String sql = String.join(" ", select_.value()).toLowerCase();
        assertTrue(sql.contains("t_lqg_ext_profile"), "组别住在提交人的外部档案上，SQL 必须查 t_lqg_ext_profile");
        assertTrue(sql.contains("user_id"), "取的是提交人 user_id（下一步 in(submitter_id) 收窄样本）");
        assertTrue(sql.contains("group_id"), "组别筛选读档案的 group_id");
        assertTrue(sql.contains("del_flag"), "软删的档案行要排除；不能靠参数拼 SQL");
        assertTrue(!sql.contains("unit_id"),
            "★ issue #96：来源单位不在这条档案查询里（它在样本行自己的 source_unit_id 上）。"
                + "再往里加 unit_id 会让内部人员录的行永远筛不出来");
        assertTrue(!sql.contains("bind_status"),
            "★ 不许按档案的核验状态过滤：核验状态是「这个人的组别认不认」，不是「这条样本算不算这个组的」"
                + "（accept 1 的 groupId 期望里含 extE 送来的待核验样本 1007）");
        assertTrue(!sql.contains("${"), "★ 只许 #{} 占位符，不许字符串拼接（组别是查询参数）");
    }

    /**
     * ★ issue #96（S1）的静态机器取证：来源单位 = <b>样本行自己的 source_unit_id</b>；
     * 传给档案查询的<b>只有 groupId</b>。
     *
     * <p>端到端的真值由 accept 1（{@code sourceUnitId=9000009001} 仍 6 条、{@code groupId=9000009101}
     * 仍 5 条）＋ impl 探针（{@code sourceUnitId=9000009002} 必须含内部录的 SJ90000009）钉；
     * 这里钉的是「实现不许再走回头路」的结构形态。
     */
    @Test
    void sourceUnitFilterReadsTheSampleRowNotTheSubmitterProfile() throws Exception {
        String source = readSource("SampleQueryService.java");
        assertTrue(source.contains(".eq(q.getSourceUnitId() != null, Sample::getSourceUnitId, q.getSourceUnitId())"),
            "★ 来源单位必须 eq 样本行自己的 source_unit_id（内部录的行也要筛得出来）");
        assertTrue(source.contains("Sample::getSourceUnitId"),
            "★ 来源单位筛选要打在样本行的列上");
        assertTrue(source.contains("submitterProfileQuery.submitterIds(q.getGroupId())"),
            "★ 档案查询只接 groupId（组别）—— 来源单位不许再塞进去");
        assertTrue(!source.contains("submitterIds(q.getSourceUnitId()"),
            "★ issue #96 的病灶写法：把 sourceUnitId 传给档案查询取 submitter_id 集合");
        assertTrue(!source.contains("submitterIds(q.getSourceUnitId(), q.getGroupId())"),
            "★ issue #96 的病灶写法（两个参数一起传）");
    }

    @Test
    void submitterNameQueryJoinsTheGroupNameOnTheSameStatement() throws Exception {
        Method profiles = SampleSubmitterProfileMapper.class.getMethod("selectProfiles", java.util.Collection.class);
        Select select = profiles.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value()).toLowerCase();
        assertTrue(sql.contains("t_lqg_unit_group"), "组别名要 join t_lqg_unit_group");
        assertTrue(sql.contains("foreach"), "一页 N 行只查一次：用 IN 批量取，不许每行一次查询");
        assertTrue(!sql.contains("${"), "只许 #{} 占位符");
    }

    /**
     * 「待核验置顶」必须是排序，且不能把 {@code sort=recent} 那一档的排序弄丢。
     *
     * <p>钉法：排序表达式只能走 {@code wrapper.last(...)}（MyBatis-Plus 3.5.16 没有按列名排序的重载），
     * 所以结构上的证据 = {@code SampleQueryService} 里那句 {@code ORDER BY (verify_status = 'pending') DESC}
     * 存在、且 {@code sort=recent} 的 {@code COALESCE(update_time, create_time)} 还在。
     * 纯字符串取证（读源码）而不是反射 —— 这两句本来就只可能是字面量 SQL。
     */
    @Test
    void pendingRowsSortFirstWhileRecentSortKeepsItsOwnOrder() throws Exception {
        String source = readSource("SampleQueryService.java");
        assertTrue(source.contains("ORDER BY (verify_status = 'pending') DESC"),
            "★ 总表（不带 sort=recent）必须「待核验置顶」");
        assertTrue(source.contains("ORDER BY COALESCE(update_time, create_time) DESC"),
            "★ sort=recent（小程序历史编辑记录）的排序不许被「待核验置顶」顶掉");
        assertTrue(source.contains("create_time DESC"), "置顶之后其余按创建时间倒序（UI:admin.sample.list）");
    }

    /**
     * 两条「语义最易做反」的机器取证（静态形态）：
     * <ul>
     *   <li>{@code submitSource} 在样本 wrapper 上是 {@code StringUtils.isNotBlank} 守卫的普通 eq
     *       —— 不许出现按当前角色判断提交来源的分支；</li>
     *   <li>日期区间是 {@code ge / le}（两端都含），不是 {@code gt / lt}。</li>
     * </ul>
     */
    @Test
    void submitSourceIsASnapshotColumnComparisonAndDateRangeIsInclusive() throws Exception {
        String source = readSource("SampleQueryService.java");
        assertTrue(source.contains("Sample::getSubmitSource"),
            "★ submitSource 筛选必须打在样本行的 submit_source 列上（提交当时的快照）");
        assertTrue(!source.contains("LoginHelper.getLoginUser"),
            "★ 读路径不许看当前登录人的角色/身份来推提交来源（快照列说了算）");
        assertTrue(source.contains(".ge(q.getReceiveDateBegin() != null, Sample::getReceiveDate, q.getReceiveDateBegin())"),
            "日期区间起点必须含端点（ge）");
        assertTrue(source.contains(".le(q.getReceiveDateEnd() != null, Sample::getReceiveDate, q.getReceiveDateEnd())"),
            "日期区间终点必须含端点（le）");
    }

    @Test
    void emptyProfileHitMeansEmptyPageNotWholeTable() throws Exception {
        // 空集必须直接回空页：`in(submitter_id, 空集)` 若被写成「条件不成立 → 不加过滤」会退化成全表，
        // 那是「按组别筛选」最隐蔽的一种假绿（筛了个不存在的组，却看到全部样本）。
        String source = readSource("SampleQueryService.java");
        assertTrue(source.contains("submitterIds.isEmpty()"),
            "★ 档案查空的单位 / 组别必须回空页，不能退化成「不过滤 = 全表」");
        SampleQueryBo bo = new SampleQueryBo();
        assertNull(bo.getSourceUnitId());
        assertNull(bo.getGroupId());
        assertNull(bo.getSubmitSource());
        assertNull(bo.getReceiveDateBegin());
        assertNull(bo.getReceiveDateEnd());
    }

    private static String readSource(String fileName) throws Exception {
        // src/main/java/org/dromara/lqg/sample/service/SampleQueryService.java
        java.nio.file.Path path = java.nio.file.Path.of("src/main/java/org/dromara/lqg/sample/service", fileName);
        if (!java.nio.file.Files.exists(path)) {
            path = java.nio.file.Path.of("ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/service", fileName);
        }
        if (!java.nio.file.Files.exists(path)) {
            fail("找不到 " + fileName + "（cwd=" + java.nio.file.Path.of(".").toAbsolutePath() + "）");
        }
        return java.nio.file.Files.readString(path);
    }

    @Test
    void sampleMapperStillHasNoCustomDeleteSqlSoSoftDeletedRowsStayHidden() {
        // 软删行（seed 1010）在任何筛选下都不许出现 —— 兜底是实体上的 @TableLogic，
        // 本票新增的两条 @Select 都在档案表上、不碰 t_lqg_sample，所以这条不变量没被削弱。
        assertTrue(Arrays.stream(SampleSubmitterProfileMapper.class.getDeclaredMethods())
                .allMatch(m -> !m.getName().toLowerCase().contains("delete")),
            "档案读侧不许出现任何 delete 方法");
    }

}
