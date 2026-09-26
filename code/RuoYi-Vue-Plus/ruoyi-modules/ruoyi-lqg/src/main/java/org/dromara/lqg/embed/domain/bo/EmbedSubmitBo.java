package org.dromara.lqg.embed.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serial;

/**
 * 石蜡包埋送样记录入参（doc/api-contract.md 的 {@code POST /lqg/embed}、{@code PUT /lqg/embed}）。
 *
 * <p>★ <b>不可改 / 不可提交的列刻意不在这里声明</b>（ticket §2 第 6 条）：
 * {@code verifyStatus}（状态只经核验接口改）、{@code verifyBy} / {@code verifyTime} /
 * {@code invalidReason}（核验段）、{@code submitSource} / {@code submitterId}（提交当时落库）、
 * {@code submitNo} / {@code internalNo}（那是<b>所挂样本</b>的列，本表根本没有）。
 * 请求里夹带它们不会生效 —— 不是「读到了再丢掉」，是接口里没有这个键
 * （accept 4 最后一段就是把 {@code verifyStatus:"pending"} 夹带进来，断库里仍是 {@code valid}）。
 *
 * <p>★ <b>{@code PUT} 是「补填」语义</b>（ticket §1「这张表会被反复打开补填」、
 * EMBED-MP-001 的「补填就是修改」）：<b>只改传了的字段</b>，没传的保持库里现值。
 * 七个工序时间全部可空、保存不要求填完 —— 建块当天只有石蜡块编号。
 * {@code markers} 是唯一例外：<b>传了（哪怕传空数组）就整组替换</b>（ticket §2），
 * 没传（{@code null}）则不动。
 *
 * <p>FIX V02b（issue #147）：除身份与石蜡块编号外的 15 项整体上移到 {@link EmbedFillBo}
 * （工作台核验抽屉一并保存的 {@code fill} 也用它），本类只多 {@code id / sampleId / paraffinBlockNo} ——
 * 普通保存与核验时补填是同一个形状、同一份校验、同一组落库列（{@code EmbedFillWriter}）。
 *
 * @author EMBED-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "石蜡包埋送样记录新增 / 修改入参")
public class EmbedSubmitBo extends EmbedFillBo {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键（PUT 必填）")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "石蜡块编号（内部新增必填、全库唯一；外部送样核验前为空）")
    private String paraffinBlockNo;

}
