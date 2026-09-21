package org.dromara.lqg.sample.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 样本行 / 详情（UI:admin.sample.list，doc/api-contract.md 的 {@code GET /lqg/sample/list}）。
 *
 * <p>★ {@code donorName} / {@code hospitalNo} 在这里是**明文**：库里的密文由
 * {@code SampleQueryService} 读出时解出来再塞进 VO（ADR-0006：内部人员要对着全名核样本，不打码）。
 * 查询侧只支持**精确匹配**（service 层先把查询值加密再 eq），不做 LIKE。
 *
 * <p>★ 与 {@code org.dromara.lqg.ext.domain.vo.ExtSampleDetailVo} 刻意**不共用**：那个对外，
 * 没有 {@code operatorName / verifyBy / internalNo} 这些键（ADR-0004 + CR-20260918-07）。
 *
 * @author SAMPLE-MODEL-001
 */
@Data
@Schema(description = "样本（工作台）")
public class SampleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "送检单号")
    private String submitNo;

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "internal / external")
    private String submitSource;

    @Schema(description = "提交人 user_id")
    private Long submitterId;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "核验人 user_id")
    private Long verifyBy;

    @Schema(description = "核验时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date verifyTime;

    @Schema(description = "判无效的原因")
    private String invalidReason;

    @Schema(description = "来源单位 id")
    private Long sourceUnitId;

    @Schema(description = "来源单位名称")
    private String sourceUnitName;

    @Schema(description = "供体姓名（明文）")
    private String donorName;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "年龄")
    private String age;

    @Schema(description = "住院号（明文）")
    private String hospitalNo;

    @Schema(description = "组织类型")
    private String tissueType;

    @Schema(description = "类器官类型")
    private String organoidType;

    @Schema(description = "有无病理 Y / N")
    private String hasPathology;

    @Schema(description = "收样日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @Schema(description = "内部编号")
    private String internalNo;

    @Schema(description = "有无固定 Y / N")
    private String isFixed;

    @Schema(description = "处理时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date processTime;

    @Schema(description = "质控表 Y / N")
    private String hasQcSheet;

    @Schema(description = "细胞活率报告 Y / N")
    private String hasViabilityReport;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 提交人姓名（SAMPLE-WEB-001）。
     *
     * <p>读时取自提交人的外部档案 {@code t_lqg_ext_profile.real_name}；<b>内部人员提交的行是 null</b>
     * （内部人员不是「外部用户」，档案表里没有他们的行）—— 前端照 null 渲染成空，不是错误。
     */
    @Schema(description = "提交人姓名（外部档案；内部录入的行是 null）")
    private String submitterName;

    /**
     * 提交人的组别 id（SAMPLE-WEB-001）。
     *
     * <p>★ 组别<b>不在样本行上</b>：按组别筛选与显示都走提交人的外部档案
     * （{@code t_lqg_ext_profile.group_id} → {@code t_lqg_unit_group.group_name}）。
     * 内部人员录入、以及自填单位名的外部用户，这个字段是 null。
     */
    @Schema(description = "提交人的组别 id（外部档案；内部录入的行是 null）")
    private Long groupId;

    @Schema(description = "提交人的组别名（外部档案；内部录入的行是 null）")
    private String groupName;

    /**
     * 最后修改人姓名（REQ-SAMPLE-016 / CR-20260917-04：修改页显示「最后修改：某某 · 时间」）。
     * 读时按 {@code update_by} 回 sys_user 取昵称；没有 update_by 的行取 {@code create_by} 的姓名。
     *
     * <p>★ <b>「最后修改」的判据是 {@code update_by} 非空</b>（见 {@code SampleQueryService.toVo}）：
     * 从没改过的行这里会有「创建人姓名」，但 {@code updateTime} 是 {@code null}
     * —— 小程序「历史编辑记录」靠 {@code updateTime} 空不空显示「新增 / 修改」。
     */
    @Schema(description = "最后修改人姓名")
    private String updateByName;

    /**
     * 经手人姓名（SAMPLE-MP-001 / CR-20260918-07）：<b>最后修改人</b>，没改过就是<b>创建人</b>。
     *
     * <p>与 {@link #updateByName} 同一个值，刻意各留一个键：
     * {@code updateByName} 是工作台从 REQ-SAMPLE-016 起就在用的名字（不能改名，改了就破契约），
     * {@code handlerName} 是 {@code doc/api-contract.md} 第 49 行给小程序「历史编辑记录」定的键。
     * 取 {@code sys_user.nick_name}，内部账号才有意义（外部账号不是「中心经手人」）。
     */
    @Schema(description = "经手人姓名（最后修改人；没改过就是创建人）")
    private String handlerName;

    /**
     * 这一行是不是<b>当前登录人</b>经手的（SAMPLE-MP-001 / CR-20260918-07）：
     * {@code create_by = 我 OR update_by = 我} —— 与 {@code mine=true} 的收窄口径同一个判据，
     * 前端据此在行上把经手人显示成「我」。
     *
     * <p>匿名 / 取不到登录人时是 {@code false}（不猜、不默认本人）。
     */
    @Schema(description = "是否当前登录人经手（create_by 或 update_by 是我）")
    private Boolean mine;

    /**
     * 这一行在<b>小程序内部接口</b>上能不能改（SAMPLE-MP-001 / CR-20260918-07）：
     * {@code verify_status = 'valid'}（内部录入的直接有效；外部送来待核验 / 无效的一律只读，
     * 核验与改判在工作台）。
     *
     * <p>★ 前端拿这个值决定渲染成表单还是只读页，<b>不自己按状态重新判断</b>
     * ——「editable 以后端详情里的为准」是 ticket §0 的口径复述第 2 条。
     * {@code /lqg/sample/**} 的工作台路径不读它（那里本来就没有只读模式）。
     */
    @Schema(description = "小程序内部接口上是否可改（valid 才可改）")
    private Boolean editable;

}
