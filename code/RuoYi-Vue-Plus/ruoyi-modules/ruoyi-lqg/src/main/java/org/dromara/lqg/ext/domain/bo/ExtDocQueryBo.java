package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;

/**
 * 外部质控文档列表筛选（{@code GET /mp/ext/doc/list}，doc/api-contract.md 的 QC / DOC 一节）。
 *
 * <p>★ <b>四个参数都是「在可见集合之上再收窄」</b>，没有一个能放大范围：
 * 可见样本集合永远先由 {@code ExtScopeService.visibleSampleIds} 算出来，本类只决定那之后怎么筛。
 *
 * <ul>
 *   <li>{@code sampleId}：预览页顶部那三份切换条用（CR-20260917-04）。★ 它是
 *       <b>先与可见集合求交</b>、再按样本过滤 —— 反过来写（先按 sampleId 查库再判可见）
 *       会让异组用户拿猜到的 id 换到一份真文档（ticket accept 1 的 counterfeit 点名此处）。
 *       不可见样本给<b>空列表</b>（不是 404：列表本来就是「0 行也正常」的形状，
 *       404 反而是在确认「这个 id 存在但你不能看」）。</li>
 *   <li>{@code docKind}：{@code sample_qc / organoid_qc / organoid_score / merged}。</li>
 *   <li>{@code publishedBegin} / {@code publishedEnd}：完成时间范围（含端点），
 *       {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss} 两种写法都收
 *       —— 只给日期的 {@code End} 按当天的 23:59:59 收口。</li>
 * </ul>
 *
 * <p>★ <b>没有 audience 参数</b>：外部永远只拿 external 那一版（ticket §0 口径 2），
 * 「传 internal 就换一版」这件事在这个接口上根本没有落点。
 *
 * <p>★ 无参构造显式转调父类：{@code PageQuery} 在 5.5.3 里<b>没有无参构造</b>，
 * 缺了它 Spring MVC 绑不出这个 BO（SAMPLE-MODEL-001 踩过，同 {@code ExtSampleQueryBo}）。
 *
 * @author AUTH-EXT-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "外部质控文档列表筛选")
public class ExtDocQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 无参构造（显式转调父类；{@code PageQuery} 在 5.5.3 里没有无参构造）。
     */
    public ExtDocQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "只看这个样本的文档（先与可见集合求交；不可见 → 空列表）")
    private Long sampleId;

    @Schema(description = "sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "完成时间起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）")
    private String publishedBegin;

    @Schema(description = "完成时间止（含端点）")
    private String publishedEnd;

}
