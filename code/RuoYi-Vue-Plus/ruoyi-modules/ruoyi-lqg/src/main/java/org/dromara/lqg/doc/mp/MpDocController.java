package org.dromara.lqg.doc.mp;

import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序<b>内部人员</b>侧「文档」的三个端点（FLOW:F-DOC-02.step1 / step2 / step3、
 * UI:mp.doc.list + UI:mp.doc.preview）。
 *
 * <pre>
 * GET /mp/int/doc/list?sampleId=&amp;docKind=&amp;publishedBegin=&amp;publishedEnd=&amp;pageNum=&amp;pageSize=
 * GET /mp/int/doc/{sampleId}/{docKind}/pages      页面图 + 图片位 + 附件（10 分钟签名链接）
 * GET /mp/int/doc/{sampleId}/{docKind}/download   10 分钟签名下载链接（format=docx|pdf）
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_internal")}</b>（契约第 19 行 / ADR-0004 的 I1）：
 * 外部角色（{@code lqg_external}）打这三个端点一律 403 —— 内部那一版带内部编号，
 * 它不是「少几个字段」的问题，而是整条路不许进。外部那两个走
 * {@code /mp/ext/doc/{sampleId}/{docKind}/pages|download}（AUTH-EXT-003）。
 *
 * <p>★ <b>audience 不是入参</b>（与外部那两个端点同一个结构）：这三个 handler 的签名里
 * 没有 {@code audience}，内部版由 {@link MpDocService} 写死 {@code internal}。
 * 内外部产物是两行独立对象（ADR-0005），小程序里不存在「内部账号看外部版」这条路。
 *
 * <p>★ <b>本类不查库、不碰 ADR-0004 的咽喉</b>：{@code /mp/int/**} 是内部侧，
 * 取数与拼装全在 {@link MpDocService}；{@code /mp/ext/**} 那一组（外部侧）仍然只由
 * {@code org.dromara.lqg.ext} 包提供。
 *
 * <p>★ 与 {@code DocRenderController}（{@code /lqg/doc/**}）的分工：那边是工作台用的
 * 「生成 / 预览 / 下载某一版」，带显式 {@code audience} 入参、要 {@code lqg:doc:query} 权限；
 * 小程序**不直连**那一条（API-CONTRACT 第 86 行点名这两个 mp 端点）。
 *
 * @author DOC-MP-001（list） / DOC-MP-002（pages + download）
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/int/doc")
@SaCheckRole("lqg_internal")
public class MpDocController {

    private final MpDocService mpDocService;

    /**
     * 内部文档清单：<b>全部样本</b>里「已完成且内部版渲染成功」的文档，
     * 同一个样本的行相邻，组的先后 = 组内最新完成时间倒序。
     */
    @GetMapping("/list")
    public TableDataInfo<MpDocVo> list(MpDocQueryBo query) {
        return mpDocService.list(query);
    }

    /**
     * 预览页图 + 「文档中的图片」图片位 + 附件（UI:mp.doc.preview 的上/中/下三段）。
     *
     * <p>形状与工作台那条 {@code /lqg/doc/{sampleId}/{docKind}/pages}（契约第 85 行）**同一个**
     * {@link DocPagesVo}：页面图自带 {@code pageNo}，图片位带 {@code url}（原图，看细节用）与
     * {@code previewUrl}（缩略图），附件带 {@code fileName / fileSize / url}。
     * 所有 url 都是 10 分钟签名链接，前端不缓存（DOC-MP-001 §7.5）。
     *
     * <p>★ 不可用（草稿 / 内部版没渲染成功 / 合并件这一版不完整）一律业务码 <b>404</b>，
     * 与「没这份文档」不可区分 —— 判据是 {@code DocAvailabilityService}（与清单同一处）。
     * 合并件「份数够但还没渲染好」在小程序侧由清单里有没有 {@code merged} 行判定并轮询
     * （doc/waves/reports/DOC-MP-002.md §merged）。
     */
    @GetMapping("/{sampleId}/{docKind}/pages")
    public R<DocPagesVo> pages(@PathVariable Long sampleId, @PathVariable String docKind) {
        return R.ok(mpDocService.pages(sampleId, docKind));
    }

    /**
     * 取 10 分钟签名下载链接 + 文件名（{@code format=docx|pdf}，默认 docx）。
     *
     * <p>列表上每份的「下载」、组底「合并下载」与预览页底部 {@code DownloadBar} 调的都是这一个
     * （CR-20260917-04：两处入口一套实现）。文件名由后端按身份给
     * （内部 = 文档名-内部编号），前端不自己拼（只有平台 API 需要文件名的兜底才用
     * {@code pages/doc/download.ts#downloadFileName}）。
     */
    @GetMapping("/{sampleId}/{docKind}/download")
    public R<DocDownloadVo> download(@PathVariable Long sampleId,
                                     @PathVariable String docKind,
                                     @RequestParam(required = false, defaultValue = "docx") String format) {
        return R.ok(mpDocService.download(sampleId, docKind, format));
    }

}
