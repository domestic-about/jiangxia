package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 石蜡包埋记录的对外形状（{@code ExtSampleDetailVo.embeds} 的元素类型）。
 *
 * <p>★ <b>本票（AUTH-EXT-001）不装配它</b> —— 外部看石蜡包埋记录是 AUTH-EXT-002 的活
 * （{@code GET /mp/ext/embed/**}），本类在这里只是因为
 * {@code ExtSampleDetailVo.embeds} 需要一个对外类型：契约把 {@code embeds} 列进了详情形状，
 * 而返回值只能是 ext 包里的 {@code Ext*} 类型（ADR-0004 的 I2）。
 * AUTH-EXT-002 落 {@code GET /mp/ext/embed/**} 时把它<b>加满字段</b>并接上数据。
 *
 * <p>★ I3 的例外正是<b>这一个类</b>（CR-20260918-07：甲方要外部看得到操作人与包埋人）：
 * {@code ExtChokepointContractTest.BANNED_EXEMPT} 按「VO 名 + 字段名」精确豁免
 * {@code operatorName} / {@code embedBy}，别扩大到别的 VO。
 *
 * @author AUTH-EXT-001
 */
@Data
@Schema(description = "石蜡包埋记录（本票占位，AUTH-EXT-002 接数据）")
public class ExtEmbedVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所挂样本 id")
    private Long sampleId;

    @Schema(description = "石蜡块编号")
    private String paraffinBlockNo;

    @Schema(description = "核验状态")
    private String verifyStatus;

    @Schema(description = "操作人（CR-20260918-07 起对外可见）")
    private String operatorName;

    @Schema(description = "包埋人（CR-20260918-07 起对外可见）")
    private String embedBy;

}
