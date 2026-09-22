package org.dromara.lqg.cryo.remind.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 超期清单的一行（{@code GET /lqg/cryo/overdue}，doc/api-contract.md 的 CRYO 一节）。
 *
 * <p>★ {@code overdueDays} = <b>今天 − 冻存日 − 阈值天数</b>（阈值当天算 0 —— seed 的 3005
 * 恰好踩在阈值当天就是「已超 0 天」）；它不是「今天 − 冻存日」，也不是落库的列。
 *
 * <p>★ 本 VO 的短名 {@code CryoOverdueVo} 与全仓既有别名不冲突（MyBatis 的
 * {@code type-aliases-package=org.dromara.**.domain} 按<b>短名</b>注册，跨包同名会让后端
 * 启动即 {@code TypeException} —— CRYO-FLOW-001 踩过一次）。
 *
 * @author CRYO-REMIND-001
 */
@Data
@Schema(description = "超期冻存批次（读时算：暂存 -80 + 未转液氮 + 剩余>0 + 冻存满阈值天数）")
public class CryoOverdueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "批次主键")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "样本编号（所挂样本的内部编号，读时带出）")
    private String internalNo;

    @Schema(description = "冻存样品名称")
    private String cryoName;

    @Schema(description = "代数，形如 P3")
    private String passage;

    @Schema(description = "冻存时间（超期从它起算）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate freezeTime;

    @Schema(description = "冻存数量/支（初始支数）")
    private Integer initQty;

    @Schema(description = "剩余支数（读时算：初始 + 未删流水累计）")
    private Integer remainingQty;

    @Schema(description = "暂存 -80 Y / N（超期行恒为 Y）")
    private String inMinus80;

    @Schema(description = "转移至液氮时间（超期行恒为空）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate toLn2Time;

    @Schema(description = "液氮储存位置")
    private String ln2Location;

    @Schema(description = "冻存人")
    private String frozenBy;

    @Schema(description = "★ 已超天数 = 今天 − 冻存日 − 阈值天数（阈值当天为 0）")
    private Integer overdueDays;

    @Schema(description = "是否超期（本清单里恒为 true；与工作台列表行上的同名键同源）")
    private Boolean overdue;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

}
