package org.dromara.lqg.cryo.batch.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 冻存批次 t_lqg_cryo_batch（FLOW:F-CRYO-01.step1，FIELD-ssot 的 t_lqg_cryo_batch）。
 *
 * <p>★★ <b>这张表上没有「剩余支数」这一列，也不许加</b>（ADR-0010 的 rejected_values
 * 「冻存剩余支数允许直接改数字」就是它）：
 * <pre>
 *   剩余 = init_qty + SUM(未删流水的 delta)        -- 读时算，见 CryoBalanceChecker
 * </pre>
 * accept 1 第 3 段会用 {@code information_schema} 查 {@code %remain% / %current% / %stock%}
 * 必须为空，{@code ddl_vs_ssot} 也会把多出来的列报红。一旦有这一列，就会有人直接改它，
 * 「取走需要追溯」就成了空话。
 *
 * <p>★ <b>暂存 -80 选「否」= 直接进液氮</b>：{@code in_minus80='N'} 时 {@code ln2_location}
 * 必填（校验在 {@code CryoBatchService}），且这条批次永远不参与超期提醒
 * （判定归 CRYO-REMIND-001）。
 *
 * <p>★ 每一条业务列都可以改（含 {@code init_qty}，CR-20260917-04 推翻了「建后不可改」）——
 * 改初始支数前先锁本行、再按 {@code flow_time} 正序逐笔校验（{@link #getId()}）。
 *
 * @author CRYO-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_cryo_batch")
public class CryoBatch extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_sample.id（内部编号贯穿：冻存必须挂到一个样本上；只能是已核验有效的样本）
     */
    private Long sampleId;

    /**
     * 冻存样品名称（如 {@code hli39-GZ-N-P3-EM2-2e5}）：<b>手填，系统不解析、不自动拼</b>
     * （选样本后用「内部编号-」预填由前端做，ticket §0 口径复述 3）
     */
    private String cryoName;

    /**
     * 代数，形如 {@code P3}（正则 {@code ^P\d{1,3}$}）；同一批次的代数不要求连续
     */
    private String passage;

    /**
     * 冻存时间（超期提醒从它起算）
     */
    private LocalDate freezeTime;

    /**
     * 冻存数量 / 支（初始支数，&gt;0；可改，改后逐笔算剩余不得为负）
     */
    private Integer initQty;

    /**
     * 冻存密度（文本，如 {@code 2e5}）
     */
    private String density;

    /**
     * 暂存 -80 度超低温冰箱 Y 是 / N 否（按钮）。N = 直接进液氮，{@code ln2Location} 必填
     */
    private String inMinus80;

    /**
     * 冻存人（姓名文本，默认当前登录人，可改）
     */
    private String frozenBy;

    /**
     * 转移至液氮时间；非空 = 已转液氮，不再参与超期提醒
     */
    private LocalDate toLn2Time;

    /**
     * 液氮储存位置（一段文本，不建罐 / 架 / 盒字典；登记转液氮时必填）
     */
    private String ln2Location;

    /**
     * 备注
     */
    private String remark;

    /**
     * 软删标志（{@code @TableLogic}：所有 mapper 查询自带 {@code del_flag='0'}）
     */
    @TableLogic
    private String delFlag;

}
