package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部文档预览（{@code GET /mp/ext/doc/{sampleId}/{docKind}/pages} 的 {@code data}）。
 *
 * <pre>
 *   {docKind, status, pages:[{pageNo, url}]}
 * </pre>
 *
 * <p>★★ <b>这个形状是「只回答能拿到的这一份长什么样」</b>，不是内部
 * {@code DocPagesVo} 的对外版：
 *
 * <ul>
 *   <li>没有失败原因 —— 外部不许知道「渲染挂在哪里」（ticket §3 的边界：
 *       「不能看渲染失败的原因」）。渲染没成功的那一份压根不在清单里，
 *       直接来打预览也是 404；</li>
 *   <li>没有内容指纹、没有模板版本 —— 那是产物缓存的内部键，对外没有意义，
 *       露出去只会变成「内部版本号」的旁路；</li>
 *   <li>没有内部编号、没有渲染人 / 完成人 —— 同上；</li>
 *   <li>没有图片位与附件（内部 {@code /lqg/doc/**} 有）—— 对外这条链路只给页面图。</li>
 * </ul>
 *
 * <p>★ {@code status} 恒为 {@code done}：能走到这里就说明外部版已经渲染成功
 * （判据在 doc 域的读口 {@code DocExternalQueryService#available}，与清单同一处）。
 * 留着这个键是为了让小程序不必再靠「pages 空不空」猜状态。
 *
 * <p>★ {@code docKind} 是回显（{@code sample_qc / organoid_qc / organoid_score / merged}），
 * 合并件与单份走同一个端点。
 *
 * @author AUTH-EXT-003
 */
@Data
@Schema(description = "外部文档预览（页面图）")
public class ExtDocPagesVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "恒为 done（外部只发已完成且外部版已渲染成功的）")
    private String status;

    @Schema(description = "页面图（pageNo 从 1 起；url 是 10 分钟签名链接）")
    private List<ExtDocPageItemVo> pages = new ArrayList<>();

}
