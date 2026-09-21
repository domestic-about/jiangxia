package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;

/**
 * 外部样本列表筛选（{@code GET /mp/ext/sample/list}，doc/api-contract.md 第 50 行）。
 *
 * <p>★ <b>参数只有三个</b>：{@code sampleKind?}、{@code verifyStatus?}、{@code onlyMine?}。
 * 它们都是「在可见集合之上再收窄」，<b>不是</b>新的一套范围 —— 可见集合永远先由
 * {@code ExtScopeService.visibleSampleIds} 算出来，本类只决定那之后怎么筛。
 *
 * <p>★ {@code onlyMine} 是「历史编辑记录」顶部的「只看我提交的」开关：按
 * {@code submitter_id = 本人} 收窄。<b>不是</b> {@code create_by} —— 同组的人替别人建的记录
 * 不算「他提交的」（accept 第 2 条钉 extA / extB 各自的集合，外加与状态筛选叠加的空集）。
 *
 * @author AUTH-EXT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "外部样本列表筛选")
public class ExtSampleQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 无参构造（显式转调父类；{@code PageQuery} 在 5.5.3 里没有无参构造，
     * 缺了它 Spring MVC 绑不出这个 BO —— SAMPLE-MODEL-001 踩过）。
     */
    public ExtSampleQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "只看我提交的（在可见集合里再按 submitter_id 收窄）")
    private Boolean onlyMine;

}
