package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.io.Serial;

/**
 * 工作台「质控文档」板块的列表筛选（{@code GET /lqg/qc/list}）。
 *
 * <p>甲方 2026-09-30 确认（飞书「网页工作台」第 17 行）：三份质控表要有<b>单独的板块</b>，
 * 能看列表、能筛选、点进去编辑 —— 本 BO 就是那一排筛选。
 *
 * <p>★ 无参构造显式转调父类：{@code PageQuery} 在 5.5.3 里<b>没有无参构造</b>，
 * 缺了它 Spring MVC 绑不出这个 BO（同 {@code MpDocQueryBo}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "质控文档列表筛选")
public class QcDocListQueryBo extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    public QcDocListQueryBo() {
        super(DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
    }

    @Schema(description = "内部编号 / 来源单位 / 送检单号，模糊匹配")
    private String keyword;

    @Schema(description = "tissue（样本记录信息表）/ organoid（类器官送样记录）；空 = 全部")
    private String sampleKind;

    @Schema(description = "none（三份都没动过）/ doing（填写中）/ done（三份都已完成并同步）；空 = 全部")
    private String progress;

    @Schema(description = "收样日期起（yyyy-MM-dd，含当天）")
    private String receiveBegin;

    @Schema(description = "收样日期止（yyyy-MM-dd，含当天）")
    private String receiveEnd;

}
