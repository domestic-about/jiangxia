package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条 marker 表达的<b>对外形状</b>（{@code ExtEmbedVo.markers} 的元素）。
 *
 * <p>★ 只有<b>名称与表达</b>两个键：内部的 {@code EmbedMarkerVo} 还带 {@code sort}
 * （marker 表里的显示顺序，只服务于工作台与导出），对外没有用处 ——
 * 这里是<b>另一个类</b>而不是复用内部 VO，理由就是「键集合即白名单」：
 * 复用会让 {@code sort} 悄悄出现在外部 JSON 里，而 accept 的差集断言只看<b>顶层</b>键，
 * 抓不到这种字段级外泄。
 *
 * <p>★ 本类也在 I3 的扫描范围里（{@code Ext*} + {@code *Vo}）：字段必须继续只有这两个，
 * 加「操作人 / 核验人 / 手机号」这类名字会直接红。
 *
 * @author AUTH-EXT-002
 */
@Data
@Schema(description = "marker 表达（外部）")
public class ExtEmbedMarkerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "marker 名称（可空）")
    private String markerName;

    @Schema(description = "negative 阴性 / weak 弱表达 / strong 强表达")
    private String expression;

}
