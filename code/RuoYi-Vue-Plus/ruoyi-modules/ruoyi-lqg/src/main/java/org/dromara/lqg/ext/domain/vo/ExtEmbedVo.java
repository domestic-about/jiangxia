package org.dromara.lqg.ext.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 石蜡包埋记录的<b>对外形状</b>（{@code GET /mp/ext/embed/list}、{@code GET /mp/ext/embed/{id}}，
 * 以及 {@code ExtSampleDetailVo.embeds} 的元素；doc/api-contract.md 第 64 行）。
 *
 * <p>★★ <b>键集合就是白名单</b>（accept 段 1 拿它做差集断言，24 个键）：
 * <pre>
 *   id, sampleId, submitNo, paraffinBlockNo, sampleType, organoidSourceType,
 *   tissueReceiveTime, tissueProcessTime, agaroseEmbedTime, dehydrateTime, agaroseSendTime,
 *   paraffinEmbedTime, sectionTime, sectioned, stainTypes, stainOther, markers,
 *   verifyStatus, invalidReason, submitterName, mine, editable, embedBy, operatorName
 * </pre>
 * <b>没有</b>：内部编号（{@code internalNo} 只按 {@code lqg.ext.show-internal-no} 出现在<b>样本详情</b>上）、
 * 备注（{@code remark}）、核验人（{@code verifyBy} / {@code verifyTime}）、提交来源与提交人 id、
 * 冻存的一切。多一个键 → accept 段 1 的差集非空 → 红。
 *
 * <p>★ <b>{@code embedBy}（包埋人）与 {@code operatorName}（操作人）是 CR-20260918-07 放开给外部的</b>
 * （甲方原话「这儿应该是可以看得到操作人、包埋人，看不到冻存信息」）：
 * {@code ExtChokepointContractTest} 的 I3 禁用字段表对<b>本类这两个字段</b>精确豁免
 * （{@code BANNED_EXEMPT = {"ExtEmbedVo": {"operatorName","embedBy"}}}），
 * 别的 {@code Ext*Vo} 上出现这两个名字仍然红 —— 别把这个类当成先例去放宽别的 VO。
 *
 * <p>★ <b>本类不 extends 内部 {@code EmbedVo}</b>：继承来的字段也算「外部看得到」
 * （I3 沿 {@code getSuperclass()} 扫，{@code verifyBy} / {@code createBy} / {@code updateBy}
 * 都是禁用字段），而且本类刻意不带 {@code remark} / {@code submitSource} 这类只为工作台存在的键。
 *
 * <p>★ {@code sectioned} <b>不是列</b>：它是 {@code sectionTime != null} 的派生值
 * （accept 段 1 断「一块 {@code sectioned==true}、另一块 {@code sectioned==false}」——
 * 给 {@code null} 或漏掉这个键都会红）。
 *
 * @author AUTH-EXT-002
 */
@Data
@Schema(description = "石蜡包埋记录（外部）")
public class ExtEmbedVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "所挂样本的送检单号（读时带出，本表没有这一列）")
    private String submitNo;

    @Schema(description = "石蜡块编号；外部提交还没核验的送样为空（对外的标识就用它，不用内部编号）")
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

    @Schema(description = "是否已切片（由 sectionTime 派生，不落库）")
    private Boolean sectioned;

    @Schema(description = "染色（数组；库里是逗号串，固定顺序 HE,IF,IHC,OTHER,NONE）")
    private List<String> stainTypes;

    @Schema(description = "选了 OTHER 时的具体染色名")
    private String stainOther;

    @Schema(description = "marker 表达（只有名称与表达，没有工作台的 sort 键）")
    private List<ExtEmbedMarkerVo> markers;

    @Schema(description = "pending 待核验 / valid 有效 / invalid 无效")
    private String verifyStatus;

    @Schema(description = "判无效的原因（外部可见，据它改后重提）")
    private String invalidReason;

    @Schema(description = "提交人姓名（外部档案的 real_name；实验室录入的送样没有外部档案 → null）")
    private String submitterName;

    @Schema(description = "是不是本人提交的这条送样（同组别人提交的为 false）")
    private Boolean mine;

    @Schema(description = "本人提交且状态是 pending / invalid 才可改后重提")
    private Boolean editable;

    @Schema(description = "包埋人（CR-20260918-07 起对外可见）")
    private String embedBy;

    @Schema(description = "操作人（CR-20260918-07 起对外可见）")
    private String operatorName;

}
