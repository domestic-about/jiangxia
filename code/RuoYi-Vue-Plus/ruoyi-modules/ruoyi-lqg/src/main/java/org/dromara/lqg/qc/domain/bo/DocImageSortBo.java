package org.dromara.lqg.qc.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 调整个图片位内的顺序（{@code PUT /lqg/qc/{sampleId}/{docType}/image/sort}）。
 *
 * <p>★ <b>{@code ids} 是「这个位里全部图的 id，按目标顺序排列」</b>：后端按数组下标
 * 重排 {@code sort = 1,2,3…}。只收一个位、且必须给全（少给一张 → 400），
 * 免得出现「有的图没排到、顺序不定」。
 *
 * <p>（doc/api-contract.md 没有定这个端点的请求体形状，这里按最小可用定义并在本票报告里
 * 记 WARN —— 页面票 QC-WEB-002 若另有约定，以页面票为准改这一处。）
 *
 * @author QC-MODEL-001
 */
@Data
@Schema(description = "图片位排序")
public class DocImageSortBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "该图片位里全部图的 id，按目标顺序")
    private List<Long> ids;

}
