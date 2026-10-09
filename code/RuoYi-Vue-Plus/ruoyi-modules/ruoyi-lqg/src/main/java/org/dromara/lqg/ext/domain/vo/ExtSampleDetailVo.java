package org.dromara.lqg.ext.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 外部样本详情（{@code GET /mp/ext/sample/{id}}，doc/api-contract.md 第 51 行）。
 *
 * <p>★ <b>键集合就是白名单</b>：{@code id, submitNo, sampleKind, sourceUnitName, species, donorName, gender,
 * age, hospitalNo, tissueType, organoidType, passage, hasPathology, remark, verifyStatus, invalidReason,
 * submitterName, mine, editable, createTime, embeds, docs}。
 * {@code passage}（代数）是 CR-20260924-10 加的：外部自己在类器官收样记录里填的一项，<b>不是内部字段</b>
 * （外部改后重提是整段替换，详情不带它的话，改一次备注就会把代数洗成空）。
 * accept 第 2 条拿这份清单做<b>差集</b>断言 —— 多一个键（{@code receiveDate} / {@code operatorName}
 * / {@code internalNo} / {@code verifyBy} / 冻存信息）就红。
 *
 * <p>★ 本类<b>不 extends</b> 内部 VO：继承来的字段也算「外部看得到」（ADR-0004 的 I3，
 * {@code ExtChokepointContractTest} 沿 {@code getSuperclass()} 扫）。
 *
 * <p>★ {@code internalNo} 是<b>唯一</b>按系统参数 {@code lqg.ext.show-internal-no}
 * （CR-20260918-07，默认 false）决定给不给的键：
 * <ul>
 *   <li>关着 → 装配时<b>根本不填</b>，配 {@code @JsonInclude(NON_NULL)} 连键都不出；</li>
 *   <li>开着 → 填值，键出现。</li>
 * </ul>
 * 「返回 null 占位」不采纳：accept 的 keys 差集断言与 ADR-0004「关着连键都不出」都要求键本身不存在。
 *
 * <p>★ {@code embeds} / {@code docs} 本票返回空数组（AUTH-EXT-002 / AUTH-EXT-003 接）：
 * 契约把这两个键列进了形状，返回 {@code []} 而不是省略，前端不必写两种取值分支。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "外部样本详情")
public class ExtSampleDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "送检单号")
    private String submitNo;

    @Schema(description = "tissue / organoid")
    private String sampleKind;

    @Schema(description = "来源单位名称")
    private String sourceUnitName;

    @Schema(description = "种属（CR-20261009-18：外部自己填的一项，改后重提要带回来）")
    private String species;

    @Schema(description = "供体姓名（详情给全名）")
    private String donorName;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "年龄")
    private String age;

    @Schema(description = "住院号")
    private String hospitalNo;

    @Schema(description = "组织类型")
    private String tissueType;

    @Schema(description = "类器官类型")
    private String organoidType;

    @Schema(description = "代数（类器官送样记录才有，形如 P3；外部自己填的，外部可见）")
    private String passage;

    @Schema(description = "有无病理 Y / N")
    private String hasPathology;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "pending / valid / invalid")
    private String verifyStatus;

    @Schema(description = "判无效的原因（外部可见）")
    private String invalidReason;

    /**
     * 内部编号：**只有**系统参数 {@code lqg.ext.show-internal-no} 打开时才装配，
     * 关着时本字段为 null 且被 {@code @JsonInclude(NON_NULL)} 连带整个键一起省掉。
     */
    @Schema(description = "内部编号（系统参数 lqg.ext.show-internal-no 打开时才出现）")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String internalNo;

    @Schema(description = "提交人姓名（外部档案的 real_name）")
    private String submitterName;

    @Schema(description = "是否本人提交")
    private Boolean mine;

    @Schema(description = "本人提交且状态是 pending / invalid 才可改")
    private Boolean editable;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "石蜡包埋记录（AUTH-EXT-002 接；本票恒为空数组）")
    private List<ExtEmbedVo> embeds;

    @Schema(description = "质控文档（AUTH-EXT-003 接；本票恒为空数组）")
    private List<ExtDocVo> docs;

}
