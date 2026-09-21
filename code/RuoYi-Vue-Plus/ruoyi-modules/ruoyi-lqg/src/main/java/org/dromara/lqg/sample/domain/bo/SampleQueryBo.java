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

}
