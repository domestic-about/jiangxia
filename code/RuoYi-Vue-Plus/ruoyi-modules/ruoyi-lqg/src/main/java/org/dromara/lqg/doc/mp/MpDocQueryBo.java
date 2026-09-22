package org.dromara.lqg.doc.mp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;

/**
 * 内部文档清单筛选（{@code GET /mp/int/doc/list}，ticket §2）。
 *
 * <p>四个参数与外部那份（{@code ExtDocQueryBo}）逐字对齐 —— 清单的形状两边一致，
 * 前端的筛选条与分组逻辑只有一套：
 *
 * <ul>
 *   <li>{@code sampleId}：外部样本详情第三段「质控文档」按样本取清单用（CR-20260917-04 的
 *       切换条也是这个口）；内部没有可见范围限制，带它就是收窄到一个样本。</li>
 *   <li>{@code docKind}：{@code sample_qc / organoid_qc / organoid_score / merged}；
 *       不认识的值 → <b>空列表</b>（与外部一致，不是 400）。</li>
 *   <li>{@code publishedBegin} / {@code publishedEnd}：完成时间范围（含端点），
 *       {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss} 两种写法都收
 *       —— 只给日期的 {@code End} 按当天的 23:59:59 收口。</li>
 * </ul>
 *
 * <p>★ 无参构造显式转调父类：{@code PageQuery} 在 5.5.3 里<b>没有无参构造</b>，
 * 缺了它 Spring MVC 绑不出这个 BO（SAMPLE-MODEL-001 踩过，同 {@code ExtDocQueryBo}）。
 *
 * @author DOC-MP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "内部质控文档列表筛选")
public class MpDocQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 无参构造（显式转调父类；{@code PageQuery} 在 5.5.3 里没有无参构造）。 */
    public MpDocQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "只看这个样本的文档")
    private Long sampleId;

    @Schema(description = "sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "完成时间起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）")
    private String publishedBegin;

    @Schema(description = "完成时间止（含端点）")
    private String publishedEnd;

}
