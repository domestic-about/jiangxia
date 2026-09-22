package org.dromara.lqg.ocr.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 识别通道状态 —— {@code GET /lqg/ocr/status} 的 data（形状权威：doc/api-contract.md「OCR」一节）。
 *
 * @author OCR-IMPL-001
 */
@Data
@Schema(description = "识别通道状态")
public class OcrStatusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前**实际生效**的识别方案（{@code none} = 未接识别服务，前端引导手填）。
     */
    @Schema(description = "当前生效的识别方案：none / stub / 真实实现名")
    private String provider;

    /**
     * 按次收费通道开关的运行期真值。
     *
     * <p>★ 报的是配置值而不是「当前 provider 收不收费」：工作台要能看出
     * 「通道开着但没配付费实现」这种半吊子状态（accept 2 第 1 段断言默认 false）。
     */
    @Schema(description = "按次收费通道是否打开（缺省 false；prod 配置文件里不出现这个键）")
    private Boolean paidEnabled;

}
