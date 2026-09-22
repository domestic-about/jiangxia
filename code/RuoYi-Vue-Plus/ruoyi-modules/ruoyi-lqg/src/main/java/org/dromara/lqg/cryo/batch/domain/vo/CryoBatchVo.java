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
 * <p>★ {@code overdue / overdueDays / tabCounts} 三个键<b>不在本票</b>（ticket §2 末句）：
 * 超期判定是 CRYO-REMIND-001 的活，它在本 VO 与列表响应上补。本票一个字都不写阈值常量 ——
 * CRYO-REMIND-001 的 accept 2 有一段 grep 就断「阈值口径不许散落在 cryo/remind 包之外」。
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

    @Schema(description = "所挂样本的核验状态（读时带出）")
    private String sampleVerifyStatus;

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

}
