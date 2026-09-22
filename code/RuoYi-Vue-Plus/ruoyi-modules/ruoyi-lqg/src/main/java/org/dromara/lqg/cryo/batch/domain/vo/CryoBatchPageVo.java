package org.dromara.lqg.cryo.batch.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.io.Serial;
import java.util.Map;

/**
 * 冻存批次列表的响应体（{@code GET /lqg/cryo/batch/list}，doc/api-contract.md 的 CRYO 一节）。
 *
 * <p>★ 就是在 {@link TableDataInfo} 的 {@code total / rows / code / msg} 之上多带一个
 * <b>{@code tabCounts}</b>（ticket §2 末句 / 契约「响应另带 {@code tabCounts:{all, overdue, ln2}}」）：
 * <pre>
 * { "code":200, "msg":"查询成功", "total":7, "rows":[…],
 *   "tabCounts":{"all":7,"overdue":2,"ln2":2} }
 * </pre>
 *
 * <p>★ <b>为什么不改上游的 {@code TableDataInfo}</b>：那是全仓共用的分页壳，加一个只有冻存用的键
 * 会把别的接口的响应形状一起改掉（有 accept 断过 keys 集合）。所以这里用<b>子类</b>，
 * 只让这个端点多带一格；{@code CryoBatchController#list} 的返回类型写成它，
 * Jackson 才会序列化这个字段（别只写父类型再指望运行期类型）。
 *
 * <p>★ 三个数的口径：{@code all} = 未删批次数、{@code ln2} = 当前位置为液氮的（直接进液氮
 * {@code in_minus80='N'} <b>或</b>已登记转液氮 {@code to_ln2_time} 非空，与行上的
 * {@code location} 同源）、{@code overdue} = {@code CryoOverdueService.countOverdue()}
 * —— 与超期清单、工作台首页计数<b>同一个函数、同一段 where</b>。
 * 三个数都是<b>整表口径</b>（不随列表的筛选收窄）：页签是「这张表上有多少」的导航，
 * 与 {@code total}（当前筛选下的行数）是两件事。
 *
 * @author CRYO-REMIND-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "冻存批次分页响应（多带 tabCounts）")
public class CryoBatchPageVo extends TableDataInfo<CryoBatchVo> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 页签计数：{@code all}（全部）/ {@code overdue}（超期）/ {@code ln2}（液氮）。
     *
     * <p>键顺序固定为 all → overdue → ln2（{@link java.util.LinkedHashMap}），
     * 断言可以逐字比。
     */
    @Schema(description = "页签计数：{all, overdue, ln2}（整表口径）")
    private Map<String, Long> tabCounts;

}
