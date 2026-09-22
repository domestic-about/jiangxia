package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;

/**
 * 外部石蜡包埋列表筛选（{@code GET /mp/ext/embed/list}，doc/api-contract.md 第 63 行）。
 *
 * <p>★ <b>参数只有两个</b>：{@code onlyMine?}、{@code verifyStatus?}。两个都是「在可见样本集合之上
 * 再收窄」，<b>不是</b>新的一套范围 —— 可见集合永远先由 {@code ExtScopeService.visibleSampleIds}
 * 算出来（本人的样本 ∪ 同组已核验者的样本），本类只决定那之后怎么筛。
 *
 * <p>★ {@code onlyMine} 按<b>这条石蜡包埋记录自己的提交人</b>（{@code t_lqg_embed.submitter_id}）收窄，
 * <b>不是</b>按所挂样本的提交人：
 * <ul>
 *   <li>如果按样本的提交人算，extB 带 {@code onlyMine=true} 会多出 2003
 *       （实验室在<b>他</b>的样本 1004 上建的块，提交人是李工）—— accept 段 1 断它必须是空集；</li>
 *   <li>extA 带 {@code onlyMine=true} 只该剩 2006（他自己提交的那条待核验送样）。</li>
 * </ul>
 *
 * <p>★ 无参构造显式转调父类：{@code PageQuery} 在 5.5.3 里<b>没有无参构造</b>，
 * 缺了它 Spring MVC 绑不出这个 BO（SAMPLE-MODEL-001 踩过，{@code ExtSampleQueryBo} 同款）。
 *
 * @author AUTH-EXT-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "外部石蜡包埋列表筛选")
public class ExtEmbedQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 无参构造（显式转调父类；{@code PageQuery} 在 5.5.3 里没有无参构造）。
     */
    public ExtEmbedQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "只看我提交的送样（在可见样本集合里再按本条记录的 submitter_id 收窄）")
    private Boolean onlyMine;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

}
