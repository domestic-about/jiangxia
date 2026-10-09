package org.dromara.lqg.embed.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 石蜡包埋送样记录行 / 详情（doc/api-contract.md 的 {@code GET /lqg/embed/list}、
 * {@code GET /lqg/embed/{id}}，UI:admin.embed.list）。
 *
 * <p>★ <b>两个「读时带出、不落库」的键</b>（ticket §2 / accept 2 第 1 段）：
 * <ul>
 *   <li>{@link #internalNo} —— 模板的「样本编号」= <b>所挂样本主档的内部编号</b>（本表只有 sampleId）；</li>
 *   <li>{@link #submitNo} —— <b>所挂样本的</b>送检单号（不是本表的，本表没有送检单号）；</li>
 *   <li>{@link #sampleVerifyStatus} —— 所挂样本的核验状态。工作台核验抽屉据此决定
 *       「判为有效」是否置灰（EMBED-WEB-001 的口径复述 4），所以它必须在行里。</li>
 * </ul>
 *
 * <p>★ <b>染色对外是数组</b>（{@link #stainTypes}），库里是逗号串：契约第 66 行要的是
 * {@code ["HE","IHC"]}；每个端各自 {@code split} 迟早有一个忘了处理空串（accept 2 第 1 段）。
 * 顺序 = 落库顺序 = 固定顺序 HE,IF,IHC,OTHER,NONE（★ 不存 {@code IHC,HE}）。
 *
 * <p>★ {@link #markers} 是<b>成组</b>返回的：一个蜡块可测多个 marker，导出时拼成
 * 「Ki67：强表达；CK19：阴性」一格。
 *
 * @author EMBED-MODEL-001
 */
@Data
@Schema(description = "石蜡包埋送样记录（工作台）")
public class EmbedVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "样本编号（读时带出=样本主档的 internal_no；待核验的外部样本为空）")
    private String internalNo;

    @Schema(description = "所挂样本的送检单号")
    private String submitNo;

    @Schema(description = "所挂样本的核验状态（核验抽屉据此置灰「判为有效」）")
    private String sampleVerifyStatus;

    @Schema(description = "所挂样本的来源单位名称（读时带出）")
    private String sourceUnitName;

    @Schema(description = "所挂样本的种属（读时带出，CR-20261009-18；本表不存）")
    private String species;

    /**
     * 所挂样本的类别（读时带出；样本软删 / 查不到时为 null）。
     *
     * <p>工作台「样本编号」点回样本用：组织样本回「样本记录信息表」、类器官回「类器官收样记录」
     * （两页的路径只认 {@code views/lqg/sample/pages.ts}，Kevin 本机验收意见：四张表之间要能双向回）。
     */
    @Schema(description = "所挂样本的类别 tissue / organoid（读时带出）")
    private String sampleKind;

    @Schema(description = "石蜡块编号；外部送样在核验前为空")
    private String paraffinBlockNo;

    @Schema(description = "样本类型")
    private String sampleType;

    @Schema(description = "类器官来源类型")
    private String organoidSourceType;

    @Schema(description = "组织收样时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate tissueReceiveTime;

    @Schema(description = "组织处理时间")
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

    @Schema(description = "切片时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sectionTime;

    @Schema(description = "是否已切片（读时由 sectionTime 推，不落库）")
    private Boolean sectioned;

    @Schema(description = "染色（数组；落库是逗号串，固定顺序 HE,IF,IHC,OTHER,NONE）")
    private List<String> stainTypes;

    @Schema(description = "选了 OTHER 时的具体染色名")
    private String stainOther;

    @Schema(description = "marker 表达（一个蜡块可多行）")
    private List<EmbedMarkerVo> markers;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "internal / external（提交当时的快照，不按当前角色现算）")
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

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间（空 = 从没被改过）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 经手人姓名（EMBED-MP-001 的历史编辑记录用）：<b>最后修改人</b>，没改过就是<b>创建人</b>。
     *
     * <p>与 SAMPLE 域同一条口径（CR-20260918-07）：取 {@code sys_user.nick_name}，
     * <b>不是</b>包埋人 {@code embedBy}（那个是业务字段「包埋人」，和「谁最后动了这条记录」是两回事）。
     */
    @Schema(description = "经手人姓名（最后修改人；没改过就是创建人）")
    private String handlerName;

    @Schema(description = "最后修改人姓名（工作台编辑抽屉的「最后修改：某某」）")
    private String updateByName;

    /**
     * 这一行是不是当前登录人经手的（{@code create_by = 我 OR update_by = 我}）—— 口径同 SAMPLE 域。
     */
    @Schema(description = "是否当前登录人经手（create_by 或 update_by 是我）")
    private Boolean mine;

    /**
     * 这一行在<b>普通保存</b>路径上能不能改：{@code verify_status = 'valid'} 才可改
     * （待核验 / 无效的外部送样只能经核验接口改，ticket §2 第 6 条）。
     */
    @Schema(description = "普通保存能否改（valid 才可改；待核验 / 无效的只读）")
    private Boolean editable;

}
