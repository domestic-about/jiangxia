package org.dromara.lqg.cryo.flow.domain.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登记转液氮的入参（{@code PUT /lqg/cryo/batch/{id}/to-ln2}，FLOW:F-CRYO-01.step4）。
 *
 * <p>★ 两个字段都<b>必填</b>：「已转液氮的批次不可以清空位置」（ticket §2）——
 * 东西进了液氮罐却没人知道在哪，比没登记更糟。
 *
 * <p>★ {@code toLn2Time} 用 {@code String} 收、服务层解析成 {@code LocalDate}：
 * accept 3 第 5 段传的是纯日期 {@code "2020-01-01"}，契约示例是 {@code yyyy-MM-dd}；
 * 用 {@code LocalDate} + 全局 {@code yyyy-MM-dd HH:mm:ss} 反序列化器接不住纯日期，
 * 会在<b>实现正确</b>的情况下 400（verify/README 坑 4 的同型坑）。
 *
 * @author CRYO-FLOW-001
 */
@Data
@Schema(description = "登记转液氮入参")
public class CryoToLn2Bo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "转移至液氮时间 yyyy-MM-dd（不得早于冻存时间）")
    private String toLn2Time;

    @Schema(description = "液氮储存位置（必填）")
    private String ln2Location;

}
