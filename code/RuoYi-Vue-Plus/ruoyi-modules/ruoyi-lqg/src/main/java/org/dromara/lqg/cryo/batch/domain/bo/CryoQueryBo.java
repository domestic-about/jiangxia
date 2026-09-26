package org.dromara.lqg.cryo.batch.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 冻存批次列表筛选（doc/api-contract.md 的 {@code GET /lqg/cryo/batch/list}）。
 *
 * <p>本票落五个筛选：{@code internalNo}（所挂样本的，等值）、{@code cryoName}（模糊）、
 * {@code location}（{@code minus80} / {@code ln2}，判据与行上的 {@code location} <b>同源</b>）、
 * {@code freezeTimeBegin/End}（冻存时间区间）、{@code sampleId}（从样本总表带 sampleId 跳入）。
 *
 * <p>★ {@code overdueOnly} 是 <b>CRYO-REMIND-001</b> 补的（ticket §2：{@code /lqg/cryo/batch/list}
 * 支持 {@code overdueOnly=true}）：它拼的是<b>同一段</b>超期判定 where 片段
 * （{@code CryoOverdueSqlProvider.WHERE}，阈值用参数绑定），不是另写一份 where。
 * {@code overdue} / {@code overdueDays} / {@code tabCounts} 三个键在 VO 与响应体上。
 *
 * <p>★ {@code sort=recent} / {@code mine=true} 是<b>预置给 CRYO-MP-001</b> 的两个参数
 * （口径同 SAMPLE / EMBED 域，CR-20260918-07）：{@code sort=recent} → 按
 * {@code COALESCE(update_time, create_time)} 倒序；{@code mine=true} → 再收窄到
 * {@code create_by = 我 OR update_by = 我}。工作台不带这两个参数，行为不变。
 *
 * <p>注意上游 {@link PageQuery} <b>只有</b> {@code (pageSize, pageNum)} 构造器（5.5.3 没有无参构造），
 * 所以必须显式写一个无参构造转调 {@code super(...)}，否则 Spring MVC 绑不出这个 BO
 * （编译期就报「constructor PageQuery cannot be applied to given types」，SAMPLE-MODEL-001 坑 4）。
 *
 * @author CRYO-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "冻存批次列表筛选")
public class CryoQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 无参构造（显式转调父类；缺省与 {@link PageQuery#build()} 的缺省一致：查全部）。
     */
    public CryoQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "内部编号（所挂样本的，等值）")
    private String internalNo;

    @Schema(description = "冻存样品名称（模糊）")
    private String cryoName;

    @Schema(description = "所挂样本 id（从样本总表跳入时带）")
    private Long sampleId;

    @Schema(description = "minus80 / ln2（与行上的 location 同源判据）")
    private String location;

    @Schema(description = "冻存时间起（含）yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate freezeTimeBegin;

    @Schema(description = "冻存时间止（含）yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate freezeTimeEnd;

    @Schema(description = "recent = 按最后修改（没有则创建）时间倒序")
    private String sort;

    @Schema(description = "true = 只要当前用户经手的（「只看我提交的」开关打开时才带）")
    private Boolean mine;

    /**
     * ★ 只看超期批次（CRYO-REMIND-001）：超期「页签」点进去时带。
     *
     * <p>拼的是 {@code CryoOverdueSqlProvider.WHERE}（唯一一份超期判定 where 片段），
     * 阈值由 {@code CryoQueryService} 现读系统参数后当参数绑定 —— 所以这一格与行上的
     * {@code overdue}、页签数字 {@code tabCounts.overdue}、超期清单三处恒等。
     */
    @Schema(description = "true = 只看超期批次（判据与行上的 overdue 同源）")
    private Boolean overdueOnly;

    /**
     * ★ 只看<b>已取空</b>的批次（2026-09-24 甲方「支数取空的要提示」：两端冻存列表的「已取空」页签）。
     *
     * <p>拼的是 {@code CryoOverdueSqlProvider.EMPTIED_WHERE}（剩余 ≤ 0，与超期判定第 ③ 条同一份剩余算式），
     * 所以这一档与行上的 {@code emptied}、页签数字 {@code tabCounts.emptied} 三处恒等。
     * 与 {@code overdueOnly} 同时带 = 空集（取空的永不超期），前端两个页签互斥。
     */
    @Schema(description = "true = 只看已取空的批次（剩余 ≤ 0，判据与行上的 emptied 同源）")
    private Boolean emptiedOnly;

    /**
     * 是不是「历史编辑记录」那一档排序（CRYO-MP-001）。
     */
    public static boolean isRecentSort(String sort) {
        return "recent".equalsIgnoreCase(sort == null ? null : sort.trim());
    }

}
