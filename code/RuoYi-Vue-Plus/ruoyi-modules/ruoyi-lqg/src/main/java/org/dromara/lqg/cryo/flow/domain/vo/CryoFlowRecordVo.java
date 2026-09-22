package org.dromara.lqg.cryo.flow.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 冻存出入库流水行（{@code GET /lqg/cryo/batch/{id}/flows}，CRYO-FLOW-001）。
 *
 * <p>形状与上游 {@link org.dromara.lqg.cryo.batch.domain.vo.CryoFlowVo}（CRYO-MODEL-001 留的
 * 表列投影）逐列一致，另加本票要补的三格 + 一格便利值：
 * <ul>
 *   <li>{@code balanceAfter} —— 这一笔之后的剩余（{@code init_qty + 到这一笔为止的 delta 累计}），
 *       <b>读时算、不落库</b>（ADR-0010：批次上连剩余列都没有，更不会给流水存快照）；</li>
 *   <li>{@code edited} —— 这一笔被改过没有（{@code update_by} 换过人，或
 *       {@code update_time} 晚于 {@code create_time}）；</li>
 *   <li>{@code updateByName} —— 最后修改人姓名（改过取 {@code update_by} 的、没改过取
 *       {@code create_by} 的，与 SAMPLE / EMBED 域同一条口径）；</li>
 *   <li>{@code remainingQty} —— 批次<b>当前</b>剩余，只填在时间倒序的第一行上，
 *       让调用方不必再发一次详情请求。</li>
 * </ul>
 *
 * <p>★ <b>为什么不叫 {@code CryoFlowVo}</b>（本类原来的名字）：MyBatis 的<b>类型别名注册表是按
 * 短名去重的</b>，与上游 {@code org.dromara.lqg.cryo.batch.domain.vo.CryoFlowVo} 撞名后启动即
 * {@code TypeException: The alias 'CryoFlowVo' is already mapped to the value
 * 'org.dromara.lqg.cryo.flow.domain.vo.CryoFlowVo'}（本票实跑踩到，见完工报告 §8）。
 * 另起一个不撞名的短名最省事，也不必去动上游那个类。
 *
 * <p>★ <b>为什么不直接改上游那个 VO</b>：上游 {@code cryo/batch/**} 不在本票的
 * {@code touches} 里（本票只在自己的包 {@code cryo/flow/**} 内新增文件）。
 *
 * <p>★ <b>时间格式</b>：{@code yyyy-MM-dd HH:mm:ss}（与 verify/README 坑 4 同款，
 * 不用 ISO 的 {@code T}）。
 *
 * @author CRYO-FLOW-001
 */
@Data
@Schema(description = "冻存出入库流水")
public class CryoFlowRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "批次 id")
    private Long batchId;

    @Schema(description = "take 取走 / add 补入 / adjust 盘点调整")
    private String flowType;

    @Schema(description = "带符号变化量（take 恒为负、add 恒为正、adjust 可正可负）")
    private Integer delta;

    @Schema(description = "minus80 / ln2（登记时的位置，不随后续转移重算）")
    private String fromLocation;

    @Schema(description = "经手人")
    private String operatorName;

    @Schema(description = "发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flowTime;

    @Schema(description = "用途 / 原因")
    private String purpose;

    @Schema(description = "这一笔之后的剩余支数（读时算，不落库）")
    private Integer balanceAfter;

    @Schema(description = "是否被修改过")
    private Boolean edited;

    @Schema(description = "最后修改人姓名")
    private String updateByName;

    @Schema(description = "批次当前剩余支数（只填在最新一笔上）")
    private Integer remainingQty;

    @Schema(description = "创建人 user_id")
    private Long createBy;

    @Schema(description = "最后修改人 user_id")
    private Long updateBy;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
