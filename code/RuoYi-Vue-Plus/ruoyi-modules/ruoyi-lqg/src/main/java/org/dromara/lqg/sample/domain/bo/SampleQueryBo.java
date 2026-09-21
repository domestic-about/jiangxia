package org.dromara.lqg.sample.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;

/**
 * 样本列表筛选（doc/api-contract.md 的 {@code GET /lqg/sample/list}）。
 *
 * <p>★ 本票（SAMPLE-MODEL-001）只落五个条件：{@code sampleKind / verifyStatus / internalNo /
 * donorName（精确）/ hospitalNo（精确）}；其余筛选（来源单位 / 组别 / 收样日期区间 / 组织类型 /
 * 操作人）在 SAMPLE-WEB-001 补。此处**刻意不声明**那些字段，免得写出半截筛选让人以为已经支持。
 *
 * <p>★ <b>SAMPLE-MP-001 加的两个只给小程序内部接口用的参数</b>：{@code sort=recent} 与
 * {@code mine=true}（CR-20260918-07）。它们与上面五个筛选挂在<b>同一个 BO</b> 上，因为
 * {@code /mp/int/sample/list} 与 {@code /lqg/sample/list} 共用 {@code SampleQueryService.list}
 * 那一条读路径、共用同一份五个筛选；工作台不带这两个参数，行为一字不变。
 * <b>这两个参数只在 {@code sort=recent} 这一档里起作用</b>（{@code mine} 不带 {@code recent}
 * 也没有范围可收窄 —— 那正是「内部管理」表格页的全表）。
 *
 * <p>★ {@code donorName} / {@code hospitalNo} 是<b>精确匹配</b>（ADR-0006）：service 层先把查询值
 * 加密再 {@code eq}。加密列不做 LIKE —— 那等于全表解密后在内存里过滤。
 *
 * <p>注意上游 {@link PageQuery} <b>只有</b> {@code (pageSize, pageNum)} 这个构造器（5.5.3 里没有无参构造），
 * 所以这里必须显式写一个无参构造转调 {@code super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM)}，
 * 否则 Spring MVC 绑不出这个 BO（编译期就报「constructor PageQuery cannot be applied to given types」）。
 *
 * @author SAMPLE-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "样本列表筛选")
public class SampleQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 无参构造（显式转调父类；缺省与 {@link PageQuery#build()} 的缺省一致：查全部）。
     */
    public SampleQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "内部编号（精确）")
    private String internalNo;

    @Schema(description = "供体姓名（精确；加密列）")
    private String donorName;

    @Schema(description = "住院号（精确；加密列）")
    private String hospitalNo;

    /**
     * 排序模式（SAMPLE-MP-001 / CR-20260918-07）：只认一个字面量 {@code recent}。
     *
     * <p>{@code recent} = 「我的 → 历史编辑记录」的取数口，与其它筛选<b>联动</b>：
     * 它不只是排序，还会把「没人经手过的」行挡在外面（{@code create_by} 与 {@code update_by}
     * 都不是内部账号的不进这张清单 —— 外部送来待核验 / 无效、以及外部自己改过的都算没人经手）。
     * 不带 = 「内部管理」表格页的全表（按创建时间倒序，口径不变）。
     */
    @Schema(description = "recent = 历史编辑记录：中心内部人员经手过的，按最后修改（没有则创建）时间倒序")
    private String sort;

    /**
     * 「只看我提交的」开关（SAMPLE-MP-001 / CR-20260918-07）：<b>只在开关打开时才带</b>。
     *
     * <p>★ 默认 {@code false} = 中心<b>全部</b>内部人员经手过的记录（甲方 9-18：
     * 「我们内部人员也有多个哦，江夏实验室所有的工作人员」）；带了 {@code true} 才在上面那个
     * 范围里再收窄到 {@code create_by = 当前用户 OR update_by = 当前用户}。
     * <b>别拿它兼当排序</b> —— 排序是 {@link #sort} 的事。
     */
    @Schema(description = "true = 只要当前用户经手的（「只看我提交的」开关打开时才带）")
    private Boolean mine;

    /**
     * 是不是「历史编辑记录」那一档排序 / 范围。
     */
    public static boolean isRecentSort(String sort) {
        return "recent".equalsIgnoreCase(sort == null ? null : sort.trim());
    }

}
