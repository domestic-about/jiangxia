package org.dromara.lqg.embed.mp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.lqg.embed.domain.bo.EmbedQueryBo;

import java.io.Serial;

/**
 * 小程序<b>内部人员</b>侧「石蜡包埋工作表 / 历史编辑记录」的列表筛选
 * （doc/api-contract.md 第 63 行：{@code GET /mp/int/embed/list}）。
 *
 * <p>★ 形状与工作台那一份<b>完全相同</b>（{@code /lqg/embed/list} 的 {@link EmbedQueryBo}）：
 * 契约第 63 行写的是「同形状」，所以本类只做一件工作台不做的事 ——
 * 把「内部管理表格页的搜索框」那一个 {@code keyword} 透传给 embed 域的同一条读路径
 * （{@code keyword} 在 {@link EmbedQueryBo} 上，见那一处的注释：石蜡块编号模糊 OR 所挂样本内部编号等值）。
 *
 * <p>★ {@code sort=recent} / {@code mine=true} 是 CR-20260918-07 给「历史编辑记录」的两个参数，
 * 语义全在 {@code EmbedQueryService.buildWrapper}：{@code sort=recent} 只按
 * {@code COALESCE(update_time, create_time)} 倒序，{@code mine=true} 只在「只看我提交的」开关
 * 打开时才带、且只在 {@code sort=recent} 这一档生效（与 SAMPLE 域同一形状）。
 *
 * @author EMBED-MP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "小程序 internal 侧石蜡包埋列表筛选（同工作台形状 + 搜索框）")
public class MpEmbedQueryBo extends EmbedQueryBo {

    @Serial
    private static final long serialVersionUID = 1L;

}
