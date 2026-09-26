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
 *   {docKind, status, pages:[{pageNo, url}], images:[{url, previewUrl}], attachments:[{fileName, fileSize, url}]}
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
 * </ul>
 *
 * <p>★ <b>图片与附件</b>（独立验收 V24，UI:mp.doc.preview / FLOW:F-DOC-02.step2 要求给）：
 * 与页面图走同一个咽喉 —— 样本可见（{@code ExtScopeService}）+ 这份文档对外可用（已完成且外部版渲染成功）
 * + 签发前逐个核对象（只签本样本、已完成成员的对象；指向渲染产物目录的只许是本样本的外部版）。
 * 都是 10 分钟签名链接。
 *
 * <p>★ {@code status} 恒为 {@code done}：能走到这里就说明外部版已经渲染成功
 * （判据在 doc 域的读口 {@code DocExternalQueryService#available}，与清单同一处）。
 * 留着这个键是为了让小程序不必再靠「pages 空不空」猜状态。
 *
 * <p>★ {@code docKind} 是回显（{@code sample_qc / organoid_qc / organoid_score / merged}），
 * 合并件与单份走同一个端点。
 *
 * @author AUTH-EXT-003 · 独立验收 V24（原图与附件）
 */
@Data
@Schema(description = "外部文档预览（页面图、原图、附件）")
public class ExtDocPagesVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "sample_qc / organoid_qc / organoid_score / merged")
    private String docKind;

    @Schema(description = "恒为 done（外部只发已完成且外部版已渲染成功的）")
    private String status;

    @Schema(description = "页面图（pageNo 从 1 起；url 是 10 分钟签名链接）")
    private List<ExtDocPageItemVo> pages = new ArrayList<>();

    @Schema(description = "文档中的图片（previewUrl 缩略，url 原图；都是 10 分钟签名链接）")
    private List<ExtDocImageVo> images = new ArrayList<>();

    @Schema(description = "附件（含细胞活率测定附件；url 是 10 分钟签名链接）")
    private List<ExtDocAttachmentVo> attachments = new ArrayList<>();

}
