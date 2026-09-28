package org.dromara.lqg.sample.hint.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 样本总表的「切片染色提示」（SAMPLE-HINT-001 / UI:admin.sample.list.hint / FLOW:F-SAMPLE-02.step4）。
 *
 * <p><b>读时计算</b>：不在 {@code t_lqg_sample} 上落任何冗余列（accept 2 用 {@code ddl_vs_ssot}
 * 卡死这件事）。每行都由 {@code SampleHintService.hintsOf(整页 id 集合)} 一条 GROUP BY 查出来。
 *
 * <p>★ <b>每一行都有这个对象，没有包埋记录的行是零值</b>（{@code blockCount=0 / sectioned=false /
 * stains=[]}），<b>不是 null</b>：小程序端会 {@code hint.blockCount}，null 会当场炸。
 *
 * @author SAMPLE-HINT-001
 */
@Data
@Schema(description = "样本的切片染色提示（读时计算）")
public class SampleHintVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "已核验有效的石蜡块数（软删 / 待核验 / 无效的不算）")
    private Integer blockCount;

    @Schema(description = "有没有已切片（任一有效石蜡块的切片时间非空）")
    private Boolean sectioned;

    @Schema(description = "做过的染色种类并集（去 NONE、按字典顺序）")
    private List<String> stains;

    public SampleHintVo() {
    }

    public SampleHintVo(Integer blockCount, Boolean sectioned, List<String> stains) {
        this.blockCount = blockCount;
        this.sectioned = sectioned;
        this.stains = stains;
    }

    /**
     * 零值：没有（有效的）石蜡块。
     *
     * <p>为什么要一个静态工厂而不是散在各处 {@code new}：口径只有一份
     * —— 「没有包埋记录的行也必须带 hint」这条不变量靠它收口。
     */
    public static SampleHintVo empty() {
        return new SampleHintVo(0, false, List.of());
    }

}
