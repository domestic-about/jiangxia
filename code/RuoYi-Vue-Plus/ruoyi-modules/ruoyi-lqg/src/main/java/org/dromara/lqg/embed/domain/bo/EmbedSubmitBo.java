package org.dromara.lqg.embed.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

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
 * @author EMBED-MODEL-001
 */
@Data
@Schema(description = "石蜡包埋送样记录新增 / 修改入参")
public class EmbedSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键（PUT 必填）")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "石蜡块编号（内部新增必填、全库唯一；外部送样核验前为空）")
    private String paraffinBlockNo;

    @Schema(description = "样本类型")
    private String sampleType;

    @Schema(description = "类器官来源类型")
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

    @Schema(description = "包埋人")
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

    @Schema(description = "选了 OTHER 时必须写的具体染色名")
    private String stainOther;

    @Schema(description = "marker 表达（整组替换；没传 = 不动）")
    private List<EmbedMarkerBo> markers;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

}
