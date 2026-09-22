package org.dromara.lqg.ext.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部「石蜡包埋送样」的提交 / 改后重提入参（{@code POST /mp/ext/embed}、{@code PUT /mp/ext/embed/{id}}）。
 *
 * <p>★ <b>只有三个键</b>（doc/api-contract.md 第 63 行 / ticket §0 口径 3 与 4）：
 * {@code sampleId}（挂哪个本人送检的样本）、{@code sampleType}、{@code organoidSourceType}。
 *
 * <p>★ <b>刻意没有</b>石蜡块编号、七个工序时间、染色、marker、包埋人、操作人、核验状态、提交来源
 * —— 它们全部由实验室在核验 / 补填时填，外部夹带 <b>连反序列化的落点都没有</b>
 * （不是「读到了再丢掉」）。accept 段 2 就是拿这些字段来夹带：
 * <pre>
 *   PUT {"sampleId":…,"sampleType":"改实验室的块"}
 *   POST {…, "paraffinBlockNo":"T-hack9", "verifyStatus":"valid", "embedBy":"外部",
 *         "stainTypes":["HE"], "operatorName":"外部", "submitSource":"internal"}
 *   → 库里必须还是 external|pending|-|-|-|-|王医生
 * </pre>
 * 复用内部 {@code EmbedSubmitBo} 会让上面那一串全部落库（counterfeit 点名的头号形态）。
 *
 * @author AUTH-EXT-002
 */
@Data
@Schema(description = "外部石蜡包埋送样入参")
public class ExtEmbedSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所挂样本 id（必须是本人送检过、没被判无效的样本）")
    private Long sampleId;

    @Schema(description = "样本类型")
    private String sampleType;

    @Schema(description = "类器官来源类型")
    private String organoidSourceType;

}
