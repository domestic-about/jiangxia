package org.dromara.lqg.embed.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Date;

/**
 * 石蜡包埋送样记录 t_lqg_embed（FLOW:F-EMBED-01，FIELD-ssot 的 t_lqg_embed）。
 *
 * <p>★ 四条最容易做反的建模口径（逐条对着 ticket accept 核）：
 * <ol>
 *   <li><b>七个工序时间全部可空</b>：{@code tissueReceiveTime / tissueProcessTime / agaroseEmbedTime /
 *       dehydrateTime / agaroseSendTime / paraffinEmbedTime / sectionTime} —— 这张表会被反复打开补填，
 *       建块当天只有石蜡块编号（FLOW:F-EMBED-01.step2）。少一个或建成非空 → accept 1 红；</li>
 *   <li><b>{@code paraffinBlockNo} 可空</b>：外部提交的送样在核验前没有编号（FIELD:t_lqg_embed.paraffin_block_no），
 *       唯一性由部分唯一索引 {@code uk_embed_block_no WHERE del_flag='0'} 兜底；</li>
 *   <li><b>模板的「样本编号」不落库</b>：本表只有 {@code sampleId}，内部编号读时从样本主档带出
 *       （建成一列 {@code sample_no} 存字符串 → accept 1 的 counterfeit 点名它红）；</li>
 *   <li><b>marker 不在这张表上</b>：一个蜡块可测多个 marker → 单独一张 {@link EmbedMarker}
 *       （主表上多出 {@code markerName} / {@code expression} 两列 → accept 1 红）。</li>
 * </ol>
 *
 * @author EMBED-MODEL-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_lqg_embed")
public class Embed extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * FK→t_lqg_sample.id（模板的「样本编号」= 该样本的 internal_no，读时带出、不落库）
     */
    private Long sampleId;

    /**
     * internal / external：提交当时按提交人身份落库（同样本主档，之后不随账号升降级而变）
     */
    private String submitSource;

    /**
     * 提交人 user_id（外部只能挂自己送检过的样本、只能改自己提交的）
     */
    private Long submitterId;

    /**
     * pending 待核验 / valid 有效 / invalid 无效；内部录入直接 valid
     */
    private String verifyStatus;

    /**
     * 核验人 user_id（不对外）
     */
    private Long verifyBy;

    /**
     * 核验时间
     */
    private Date verifyTime;

    /**
     * 判无效的原因（外部可见）
     */
    private String invalidReason;

    /**
     * 石蜡块编号（如 E15-1-2026.07.29）：实验室手填、全库唯一、**可空**（外部送样核验前为空）
     */
    private String paraffinBlockNo;

    /**
     * 样本类型（自由文本，联想词来自字典 lqg_hint_sample_type）
     */
    private String sampleType;

    /**
     * 类器官来源类型
     */
    private String organoidSourceType;

    /**
     * 组织收样时间（新增时默认带样本的收样日期，可改）
     */
    private LocalDate tissueReceiveTime;

    /**
     * 组织处理时间（新增时默认带样本处理时间的日期部分，可改）
     */
    private LocalDate tissueProcessTime;

    /**
     * 琼脂糖包埋样本时间
     */
    private LocalDate agaroseEmbedTime;

    /**
     * 包埋人
     */
    private String embedBy;

    /**
     * 脱水时间
     */
    private LocalDate dehydrateTime;

    /**
     * 琼脂糖包埋样本送样时间
     */
    private LocalDate agaroseSendTime;

    /**
     * 石蜡包埋时间
     */
    private LocalDate paraffinEmbedTime;

    /**
     * 切片时间（非空 = 已切片）
     */
    private LocalDate sectionTime;

    /**
     * 染色：逗号分隔的 {@code lqg_stain_type} value，**按固定顺序** HE,IF,IHC,OTHER,NONE 落库；
     * 空 = 还没选。对外是数组（见 {@code EmbedVo.stainTypes}）。
     */
    private String stainTypes;

    /**
     * 选了 OTHER 时写具体染色名（不含 OTHER 时置空）
     */
    private String stainOther;

    /**
     * 操作人
     */
    private String operatorName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 软删标志（{@code @TableLogic}：所有 mapper 查询自带 {@code del_flag='0'}，软删行永远查不到）
     */
    @TableLogic
    private String delFlag;

}
