package org.dromara.lqg.ext.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 外部「历史编辑记录」的样本行（{@code GET /mp/ext/sample/list}，doc/api-contract.md 第 50 行）。
 *
 * <p>★ 形状与契约<b>逐键对齐</b>：{@code id, submitNo, sampleKind, donorNameMasked, tissueType,
 * organoidType, verifyStatus, submitterName, mine, editable, createTime, updateTime}。
 * 多一个键就是「外部多看到一个内部字段」，少一个键前端会渲染空列。
 *
 * <p>★ 本类<b>不 extends</b> 内部 VO（{@code SampleVo}）：那会把 {@code operatorName} /
 * {@code verifyBy} / {@code internalNo} 顺着继承漏出来，而且哪天内部 VO 加字段外部就自动多看到
 * —— ADR-0004 的 I3 正是钉这一条（含继承来的字段），{@code ExtChokepointContractTest} 会扫本类。
 *
 * <p>★ 列表里给的是 {@code donorNameMasked}（姓 + 两个星号），**没有** {@code donorName} 原列；
 * 原列也不在 {@code hospitalNo} / {@code receiveDate} / {@code internalNo} 这些内部专用字段里。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "外部样本行（历史编辑记录）")
public class ExtSampleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "送检单号")
    private String submitNo;

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "供体姓名掩码（首字 + **）")
    private String donorNameMasked;

    @Schema(description = "组织类型")
    private String tissueType;

    @Schema(description = "类器官类型")
    private String organoidType;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "提交人姓名（外部档案的 real_name）")
    private String submitterName;

    @Schema(description = "是否本人提交")
    private Boolean mine;

    @Schema(description = "本人提交且状态是 pending / invalid 才可改")
    private Boolean editable;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "最后修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
