package org.dromara.lqg.cryo.mp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.lqg.cryo.batch.domain.bo.CryoQueryBo;

import java.io.Serial;

/**
 * 小程序<b>内部人员</b>侧「-80 冻存工作表 / 历史编辑记录」的列表筛选
 * （doc/api-contract.md 第 73 行：{@code GET /mp/int/cryo/batch/list}）。
 *
 * <p>★ 形状与工作台那一份<b>完全相同</b>（{@code /lqg/cryo/batch/list} 的 {@link CryoQueryBo}，
 * 契约第 73 行「同形状」）：本类不新增一个字段，只用来给小程序侧一个稳定的入参类型。
 *
 * <p>★ <b>{@code mine} 与 {@code sort} 是两个互不兼职的参数</b>（CR-20260918-07）：
 * <ul>
 *   <li>{@code sort=recent} —— 「历史编辑记录」那一档：按
 *       {@code COALESCE(update_time, create_time)} 倒序（<b>不</b>超期置顶）；</li>
 *   <li>{@code mine=true} —— 顶部「只看我提交的」开关<b>打开时才带</b>：收窄到
 *       {@code create_by = 我 OR update_by = 我}。不带 = 中心全部内部人员（默认口径）。</li>
 * </ul>
 * 两个语义都落在 {@code CryoQueryService.buildWrapper} 上 —— 与 SAMPLE / EMBED 两张历史页签
 * 同一条读路径，小程序这边不另写一份 where。
 *
 * <p>★ {@code overdueOnly} / {@code location} / {@code emptiedOnly} 是「-80 冻存工作表」四个页签的档：
 * 全部（都不带）/ -80 超期（{@code overdueOnly=true}）/ 液氮（{@code location=ln2}）/
 * 已取空（{@code emptiedOnly=true}，2026-09-24 甲方「支数取空的要提示」，剩余 ≤ 0）。
 * 小程序表格页支持 {@code ?sheet=cryo&tab=overdue|ln2|emptied|all} 直达其中一档。
 * 超期那一段拼的是 {@code CryoOverdueSqlProvider.WHERE}（CRYO-REMIND-001 的唯一判定片段），
 * 阈值是参数、不在这里写任何天数常量。
 *
 * @author CRYO-MP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "小程序 internal 侧冻存批次列表筛选（同工作台形状）")
public class MpCryoQueryBo extends CryoQueryBo {

    @Serial
    private static final long serialVersionUID = 1L;

}
