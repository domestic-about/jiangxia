package org.dromara.lqg.embed.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 石蜡包埋记录的<b>补填段</b>：身份（{@code id} / 所挂样本）与石蜡块编号以外、工作台抽屉里能填的全部 15 项。
 *
 * <p>★ 分两组（口径：{@code UI:mp.embed.form}、{@code FLOW:F-EMBED-01.step6 / step7}，键清单在
 * {@code EmbedFillRules}）：
 * <ul>
 *   <li><b>外部送样填的两项</b>：样本类型、类器官来源类型；</li>
 *   <li><b>实验室补填的 13 项</b>：七个工序时间、包埋人、染色（含「其他」的名称）、marker、操作人、备注 ——
 *       外部填写页「都不渲染（实验室核验有效后填）」。</li>
 * </ul>
 *
 * <p>★ 三条写路径共用这一个形状、共用 {@code EmbedFillWriter} 那一份校验与落库列（FIX V02b / issue #147）：
 * <ul>
 *   <li>内部新增 / 修改 {@code POST|PUT /lqg/embed}、{@code POST|PUT /mp/int/embed}（{@link EmbedSubmitBo} 继承本类）；</li>
 *   <li>工作台核验抽屉 {@code PUT /lqg/embed/{id}/verify} 里一并保存的 {@code fill}（{@link EmbedVerifyBo#getFill()}）。</li>
 * </ul>
 *
 * <p>★ 语义 = <b>补丁</b>（与 {@code PUT /lqg/embed} 同一份，FIX V33）：键没出现 = 不动；键出现、值为空 = 清空；
 * {@code markers} 出现就整组替换。
 *
 * @author FIX-V02b
 */
@Data
@Schema(description = "石蜡包埋补填段（补丁语义：没带的键不动、带了空值清空）")
public class EmbedFillBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "样本类型（≤ 50 字）", maxLength = 50)
    private String sampleType;

    @Schema(description = "类器官来源类型（≤ 100 字）", maxLength = 100)
    private String organoidSourceType;

    @Schema(description = "组织收样时间（新增时默认带样本的收样日期）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate tissueReceiveTime;

    @Schema(description = "组织处理时间（新增时默认带样本处理时间的日期部分）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate tissueProcessTime;

    @Schema(description = "琼脂糖包埋样本时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate agaroseEmbedTime;

    @Schema(description = "包埋人（≤ 50 字）", maxLength = 50)
    private String embedBy;

    @Schema(description = "脱水时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dehydrateTime;

    @Schema(description = "琼脂糖包埋样本送样时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate agaroseSendTime;

    @Schema(description = "石蜡包埋时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paraffinEmbedTime;

    @Schema(description = "切片时间（非空 = 已切片）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sectionTime;

    @Schema(description = "染色多选（HE / IF / IHC / OTHER / NONE；NONE 与其余互斥；空 = 还没选）")
    private List<String> stainTypes;

    @Schema(description = "选了 OTHER 时必须写的具体染色名（≤ 100 字）", maxLength = 100)
    private String stainOther;

    @Schema(description = "marker 表达（整组替换；没传 = 不动；名称 ≤ 50 字）")
    private List<EmbedMarkerBo> markers;

    @Schema(description = "操作人（≤ 50 字）", maxLength = 50)
    private String operatorName;

    @Schema(description = "备注（≤ 500 字）", maxLength = 500)
    private String remark;

}
