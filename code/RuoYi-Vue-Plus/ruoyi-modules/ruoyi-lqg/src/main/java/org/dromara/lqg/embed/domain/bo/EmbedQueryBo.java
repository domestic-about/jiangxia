package org.dromara.lqg.embed.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 石蜡包埋列表筛选（doc/api-contract.md 的 {@code GET /lqg/embed/list}）。
 *
 * <p>ticket §2 点名的六个筛选全部落在这里：{@code paraffinBlockNo / internalNo / sampleId /
 * stain / sectionTimeBegin/End / verifyStatus / submitSource}（前两个是搜索、其余是等值/区间）。
 *
 * <p>★★ <b>{@code stain} 绝不能用 {@code LIKE '%HE%'}</b>（ticket §2 第 8 条 / accept 3 最后一段）：
 * {@code stain_types} 是逗号串，「含 OTHER 的名称会串」—— 按 {@code %HE%} 查会把
 * {@code OTHER} 也带出来（O-T-<b>HE</b>-R）。本实现用「两侧补逗号再整体 LIKE」
 * （{@code (',' || stain_types || ',') LIKE '%,HE,%'}），是<b>数组包含</b>语义的精确等价，
 * 参数化拼接、不做字符串插值。
 *
 * <p>★ {@code internalNo} 是<b>所挂样本的</b>内部编号（本表只有 sample_id）：
 * 先按 internal_no 查样本 id 集合，再 {@code in(sample_id)}；空集合 → 直接回空页
 * （别退化成「不过滤 = 全表」）。
 *
 * <p>★ {@code sort} / {@code mine} 是<b>预置给 EMBED-MP-001</b> 的两个参数
 * （口径同 SAMPLE 域，CR-20260918-07）：{@code sort=recent} → 按
 * {@code COALESCE(update_time, create_time)} 倒序；{@code mine=true} → 再收窄到
 * {@code create_by = 我 OR update_by = 我}。工作台不带这两个参数，行为一字不变。
 *
 * <p>注意上游 {@link PageQuery} <b>只有</b> {@code (pageSize, pageNum)} 构造器（5.5.3 没有无参构造），
 * 所以必须显式写一个无参构造转调 {@code super(...)}，否则 Spring MVC 绑不出这个 BO（编译期就报
 * 「constructor PageQuery cannot be applied to given types」，SAMPLE-MODEL-001 坑 4）。
 *
 * @author EMBED-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "石蜡包埋列表筛选")
public class EmbedQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 无参构造（显式转调父类；缺省与 {@link PageQuery#build()} 的缺省一致：查全部）。
     */
    public EmbedQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "石蜡块编号（模糊）")
    private String paraffinBlockNo;

    @Schema(description = "内部编号（所挂样本的，等值）")
    private String internalNo;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "染色（数组包含：HE / IF / IHC / OTHER / NONE 之一，精确匹配）")
    private String stain;

    @Schema(description = "切片时间起（含）yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sectionTimeBegin;

    @Schema(description = "切片时间止（含）yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sectionTimeEnd;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "internal / external（提交当时的快照）")
    private String submitSource;

    @Schema(description = "recent = 按最后修改（没有则创建）时间倒序；不带 = 待核验置顶")
    private String sort;

    @Schema(description = "true = 只要当前用户经手的（「只看我提交的」开关打开时才带）")
    private Boolean mine;

    /**
     * 是不是「历史编辑记录」那一档排序（EMBED-MP-001）。
     */
    public static boolean isRecentSort(String sort) {
        return "recent".equalsIgnoreCase(sort == null ? null : sort.trim());
    }

}
