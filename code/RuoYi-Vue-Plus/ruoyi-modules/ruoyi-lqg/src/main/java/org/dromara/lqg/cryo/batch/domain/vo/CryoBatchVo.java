package org.dromara.lqg.cryo.batch.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 冻存批次行 / 详情（UI:admin.cryo.list，doc/api-contract.md 的 {@code GET /lqg/cryo/batch/list}）。
 *
 * <p>★★ <b>四个读时算 / 读时带出的键</b>（ticket §2 的「读模型」，一行都不能少）：
 * <ul>
 *   <li>{@code remainingQty} = {@code init_qty + SUM(未删流水的 delta)} —— <b>不落库</b>，
 *       由 {@code CryoQueryService} 一条 {@code GROUP BY} 按本页 id 集合算出来；</li>
 *   <li>{@code location} = {@code in_minus80='N' 或 to_ln2_time 非空 → 'ln2'}，否则 {@code 'minus80'}
 *       （{@code CryoBalanceChecker.locationOf}）；</li>
 *   <li>{@code internalNo} = <b>所挂样本</b>的内部编号（模板的「样本编号」），本表只有
 *       {@code sample_id}，从样本主档批量带出；</li>
 *   <li>{@code sourceUnitName} = 所挂样本的来源单位名称快照，同样读时带出。</li>
 * </ul>
 *
 * <p>★ {@code overdue / overdueDays} 是 <b>CRYO-REMIND-001</b> 在本 VO 上补的两个键
 * （{@code tabCounts} 补在响应体上，见 {@code CryoBatchPageVo}）：超期判定是<b>读时算</b>的，
 * 判据只有一处（{@code CryoOverdueService.isOverdue}），阈值每次判定现读系统参数
 * {@code lqg.cryo.overdue-days}。本 VO 里没有任何天数常量。
 *
 * <p>★ {@code handlerName} / {@code mine} 是<b>预置给 CRYO-MP-001</b> 的两个键（口径同 SAMPLE /
 * EMBED 域，CR-20260918-07）：{@code handlerName} = 经手人 = <b>最后修改人</b>，从没改过就是
 * 创建人（<b>不是</b> {@code frozenBy} —— 冻存人是在冰箱前冻样的人，经手人是最后动这条记录的人）；
 * {@code mine} = {@code create_by = 我 OR update_by = 我}。工作台不读它们，行为不变。
 *
 * @author CRYO-MODEL-001
 */
@Data
@Schema(description = "冻存批次（工作台 / 小程序）")
public class CryoBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "冻存样品名称（手填）")
    private String cryoName;

    @Schema(description = "代数，形如 P3")
    private String passage;

    @Schema(description = "冻存时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate freezeTime;

    @Schema(description = "冻存数量/支（初始支数）")
    private Integer initQty;

    @Schema(description = "冻存密度")
    private String density;

    @Schema(description = "暂存 -80 Y / N")
    private String inMinus80;

    @Schema(description = "冻存人")
    private String frozenBy;

    @Schema(description = "转移至液氮时间（空 = 还在 -80）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate toLn2Time;

    @Schema(description = "液氮储存位置")
    private String ln2Location;

    @Schema(description = "备注")
    private String remark;

    /**
     * ★ 剩余支数：读时算，不落库（{@code init_qty + SUM(未删流水 delta)}）。
     */
    @Schema(description = "剩余支数（读时算：初始 + 未删流水累计）")
    private Integer remainingQty;

    /**
     * ★ 当前位置：{@code ln2} / {@code minus80}（读时算）。
     */
    @Schema(description = "当前位置：ln2 / minus80")
    private String location;

    /**
     * 所挂样本的内部编号（读时带出；软删 / 查不到样本时为 null）。
     */
    @Schema(description = "样本编号（所挂样本的内部编号，读时带出）")
    private String internalNo;

    @Schema(description = "所挂样本的送检单号（读时带出）")
    private String submitNo;

    @Schema(description = "所挂样本的来源单位名称（读时带出）")
    private String sourceUnitName;

    @Schema(description = "所挂样本的种属（读时带出，CR-20261009-18；本表不存）")
    private String species;

    @Schema(description = "所挂样本的核验状态（读时带出）")
    private String sampleVerifyStatus;

    /**
     * 所挂样本的类别（读时带出；样本软删 / 查不到时为 null）。
     *
     * <p>工作台「内部编号」点回样本用：组织样本回「样本记录信息表」、类器官回「类器官收样记录」
     * （Kevin 本机验收意见：四张表之间要能双向回）。
     */
    @Schema(description = "所挂样本的类别 tissue / organoid（读时带出）")
    private String sampleKind;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间（null = 从没改过）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @Schema(description = "创建人 user_id")
    private Long createBy;

    @Schema(description = "最后修改人 user_id")
    private Long updateBy;

    /**
     * 经手人姓名（最后修改人；没改过就是创建人）—— 给 CRYO-MP-001 的历史编辑记录。
     */
    @Schema(description = "经手人姓名（最后修改人；没改过就是创建人）")
    private String handlerName;

    /**
     * 与 {@link #handlerName} 同值，用工作台习惯的键名（「最后修改：某某 · 时间」）。
     */
    @Schema(description = "最后修改人姓名（工作台键名，与 handlerName 同值）")
    private String updateByName;

    /**
     * 这一行是不是当前登录人经手的（{@code create_by = 我 OR update_by = 我}）。
     */
    @Schema(description = "是否当前登录人经手")
    private Boolean mine;

    /**
     * ★ <b>是否超期</b>（CRYO-REMIND-001，读时算，<b>不落库</b>）。
     *
     * <p>判据只有一处：{@code CryoOverdueService.isOverdue} —— 暂存 -80 为是
     * <b>且</b> 没登记转液氮 <b>且</b> 剩余 &gt; 0 <b>且</b> 冻存满阈值天数（阈值当天即算）。
     * 所以登记转液氮或支数被取空之后，这一格<b>当场</b>变 false（不必等定时任务）。
     */
    @Schema(description = "是否超期（读时算：暂存 -80 且未转液氮且剩余>0且冻存满阈值天数）")
    private Boolean overdue;

    /**
     * ★ <b>已超天数</b>（读时算）：{@code 今天 − 冻存日 − 阈值天数}；阈值当天是 <b>0</b>，未超期是 null。
     */
    @Schema(description = "已超天数（读时算：今天 − 冻存日 − 阈值天数；阈值当天为 0，未超期为 null）")
    private Integer overdueDays;

    /**
     * ★ <b>已取空</b>（读时算，不落库）：剩余支数 ≤ 0（{@code CryoBalanceChecker.isEmptied}）。
     *
     * <p>2026-09-24 甲方「支数取空的要提示」：工作台列表、小程序表格与批次详情弹层的「已取空」标记
     * 都认这一格，与筛选 {@code emptiedOnly}、页签计数 {@code tabCounts.emptied} 是<b>同一份剩余算式</b>
     * （SQL 侧 {@code CryoOverdueSqlProvider.EMPTIED_WHERE}）。取空的批次永不超期（超期判定第 ③ 条）。
     */
    @Schema(description = "是否已取空（读时算：剩余 ≤ 0）")
    private Boolean emptied;

    /**
     * 冻存到今天的自然天数（{@code 今天 − 冻存日}，读时算；冻存时间为空时为 null）。
     *
     * <p>小程序批次详情弹层「-80℃ 暂存 · 冻存 N 天」那一句用它 —— 天数只在后端按服务器日期算一次，
     * 与 {@code overdueDays} 同一个时钟，前端不自己拿手机日期减（跨时区 / 手机日期不准时两个数会对不上）。
     */
    @Schema(description = "冻存到今天的天数（读时算）")
    private Integer frozenDays;

}
