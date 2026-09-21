package org.dromara.lqg.sample.mp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;

import java.io.Serial;

/**
 * 小程序内部「样本记录信息表」的列表筛选（doc/api-contract.md 第 49 行的
 * {@code GET /mp/int/sample/list}）。
 *
 * <p>★ 直接 extends {@link SampleQueryBo}：契约给 {@code /mp/int/sample/list} 写的筛选条件
 * （{@code keyword / donorName / verifyStatus / sampleKind / sort / mine}）里，
 * 除 {@code keyword} 外的四个与工作台那五个是<b>同一套字段名</b>。刻意不另立一套 BO ——
 * 两套字段名迟早会漂移，而 {@code SampleQueryService.list} 只认一份判据。
 *
 * <p>★ {@code sort=recent} 与 {@code mine=true} 的语义写在父类那两个字段上
 * （CR-20260918-07）。本类只是把它们与工作台的调用面分开：
 * {@code /lqg/sample/list} 收的是父类型、拿不到 {@code keyword}，
 * {@code /mp/int/sample/list} 收本类型、按契约形状说话。
 *
 * <p>★ <b>本票（SAMPLE-MP-001）刻意不声明 {@code keyword}</b>：那个条件是
 * 「内部管理」表格页（SAMPLE-MP-002）的搜索框要的（契约写的是「内部编号 / 来源单位」）。
 * <b>SAMPLE-MP-002 已把它补在父类 {@link SampleQueryBo} 上</b>（判据只有
 * {@code SampleQueryService.list} 一条路，落在那里才与别的筛选同源）——本类因此仍然
 * 一个字段都不用声明。当时留的移交注释见 git 历史。
 *
 * <p>注意上游 {@link org.dromara.common.mybatis.core.page.PageQuery} 只有
 * {@code (pageSize, pageNum)} 这个构造器（5.5.3 没有无参构造），父类已经显式转调过了，
 * 本类不必重写。
 *
 * @author SAMPLE-MP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "小程序内部 · 样本列表筛选")
public class MpSampleQueryBo extends SampleQueryBo {

    @Serial
    private static final long serialVersionUID = 1L;

    // keyword（内部编号 / 来源单位）：SAMPLE-MP-002 的表格页搜索用；已落在父类 SampleQueryBo 上。

}
