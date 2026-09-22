package org.dromara.lqg.cryo.remind.sql;

/**
 * 超期判定的 <b>SQL 侧同口径片段</b>（CRYO-REMIND-001 ticket §2）。
 *
 * <p>★ <b>只此一份</b>：超期清单（{@code SELECT …}）、页签 / 首页计数（{@code SELECT COUNT(*)}）
 * 两条自定义 SQL 拼的是同一个 {@link #WHERE} 常量；工作台列表的 {@code overdueOnly=true}
 * 与「默认排序超期置顶」也把它渲染进 MyBatis-Plus 的 wrapper（{@link #whereFor(String)}）。
 * 各写各的 where，四个数字迟早不相等 —— 这是本票最核心的一条。
 *
 * <p>★ <b>四个条件缺一不可</b>（FLOW:F-CRYO-01.step2，与 Java 侧
 * {@code CryoOverdueService#isOverdue} 逐条对应）：
 * <ol>
 *   <li>{@code in_minus80 = 'Y'} —— 暂存 -80 才谈得上「该转液氮」；直接进液氮的不算；</li>
 *   <li>{@code to_ln2_time IS NULL} —— 已经登记转液氮的当场退出（CR-20260918-07 甲方问
 *       「转移后还会有提示吗」：不会）；</li>
 *   <li>{@code init_qty + SUM(未删流水 delta) > 0} —— 取空了的批次不再提醒
 *       （否则提醒人去把一个空盒子转进液氮）；子查询里的 {@code del_flag = '0'} 是
 *       <b>手写</b>的，自定义 SQL 不吃 {@code @TableLogic}；</li>
 *   <li>{@code CURRENT_DATE - freeze_time >= 阈值} —— <b>当天即算</b>（{@code >=} 不是 {@code >}）。</li>
 * </ol>
 *
 * <p>★ <b>阈值是参数不是字面量</b>：片段里写的是 {@code #{days}} 占位符，由 mapper 方法上的
 * {@code @Param("days")} 绑定；wrapper 侧由 {@link #whereFor(String)} 换成该侧能用的占位符
 * （{@code {0}} = MyBatis-Plus 的 {@code apply} 实参，{@code #{ew.paramNameValuePairs.*}} = 排序键）。
 * 片段本身<b>没有</b>任何硬编码天数。
 *
 * @author CRYO-REMIND-001
 */
public final class CryoOverdueSqlProvider {

    /**
     * mapper 方法上的参数名（{@code @Param("days")}）—— 片段里的占位符与它逐字对应。
     */
    public static final String DAYS_PARAM = "days";

    /**
     * {@code @Select} 里用的阈值占位符（{@code #{days}}）。
     */
    public static final String MAPPER_DAYS = "#{days}";

    /**
     * ★ <b>唯一一份超期判定 where 片段</b>（不含 {@code SELECT} / {@code ORDER BY}）。
     *
     * <p>列名用<b>全表名</b>限定而不是别名：MyBatis-Plus 的 wrapper 查询生成的是
     * {@code FROM t_lqg_cryo_batch}（没有别名），同名限定让这段片段在两条自定义 SQL 与
     * wrapper 三条路上都能原样拼。
     */
    public static final String WHERE = "del_flag = '0'"
        + " AND in_minus80 = 'Y'"
        + " AND to_ln2_time IS NULL"
        + " AND (CURRENT_DATE - freeze_time) >= #{days}"
        + " AND init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f"
        + " WHERE f.batch_id = t_lqg_cryo_batch.id AND f.del_flag = '0'), 0) > 0";

    /**
     * 超期清单：按<b>已超天数倒序</b>（冻存越早的越靠前），同日按 id 倒序稳定。
     */
    public static final String SELECT_LIST =
        "SELECT * FROM t_lqg_cryo_batch WHERE " + WHERE
            + " ORDER BY (CURRENT_DATE - freeze_time) DESC, id DESC";

    /**
     * 超期计数：与 {@link #SELECT_LIST} <b>同一个</b> {@link #WHERE}（页签 / 首页 / 清单三处同源）。
     */
    public static final String SELECT_COUNT = "SELECT COUNT(*) FROM t_lqg_cryo_batch WHERE " + WHERE;

    private CryoOverdueSqlProvider() {
    }

    /**
     * 把片段里的 {@link #MAPPER_DAYS} 换成调用方那一侧能用的占位符，其余逐字不变。
     *
     * @param daysPlaceholder MyBatis-Plus 侧：{@code {0}}（配 {@code apply(sql, days)}）；
     *                        排序键侧：{@code #{ew.paramNameValuePairs.<key>}}
     * @return 同一段 where 的另一种占位符渲染
     */
    public static String whereFor(String daysPlaceholder) {
        return WHERE.replace(MAPPER_DAYS, daysPlaceholder);
    }

}
