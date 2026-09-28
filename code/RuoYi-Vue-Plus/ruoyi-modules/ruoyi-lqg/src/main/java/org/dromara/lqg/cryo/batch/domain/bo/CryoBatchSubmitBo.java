package org.dromara.lqg.cryo.batch.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 冻存批次的新增 / 修改入参（doc/api-contract.md 的 {@code POST|PUT /lqg/cryo/batch}）。
 *
 * <p>★ <b>入参里没有「剩余支数」</b>：剩余 = 初始支数 + 未删流水累计，读时算（ADR-0010）。
 * 请求里夹带 {@code remainingQty} 之类的键一律不生效 —— 本类根本没有这个字段
 * （{@code CryoShapeContractTest} 用反射钉住）。
 *
 * <p>★ <b>{@code initQty} 是<b>初始</b>支数、<b>可以改</b></b>（CR-20260917-04 推翻了原来的
 * 「建后不可改」）：{@code PUT} 时锁批次行 → 从新的初始支数出发按 {@code flow_time} 正序
 * （同一时刻按 {@code id}）逐笔累加未删流水 → 任何一步 &lt; 0 就拒绝且库里不变
 * （{@code CryoBalanceChecker}）。<b>不许静默忽略入参里的 {@code initQty}</b>
 * —— accept 2 的 counterfeit 点名「拿到 200 但库里仍是 8」这一形态。
 *
 * @author CRYO-MODEL-001
 */
@Data
@Schema(description = "冻存批次新增 / 修改")
public class CryoBatchSubmitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（{@code PUT} 必填；{@code POST} 忽略）。
     */
    @Schema(description = "主键（修改时必填）")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "冻存样品名称（手填，系统不解析）")
    private String cryoName;

    @Schema(description = "代数，形如 P3")
    private String passage;

    @Schema(description = "冻存时间 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate freezeTime;

    @Schema(description = "冻存数量/支（初始支数，正整数）")
    private Integer initQty;

    @Schema(description = "冻存密度（文本，如 2e5）")
    private String density;

    @Schema(description = "暂存 -80 度超低温冰箱 Y 是 / N 否")
    private String inMinus80;

    @Schema(description = "冻存人")
    private String frozenBy;

    @Schema(description = "转移至液氮时间 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate toLn2Time;

    @Schema(description = "液氮储存位置（inMinus80='N' 或登记转液氮时必填）")
    private String ln2Location;

    @Schema(description = "备注")
    private String remark;

}
