package org.dromara.lqg.doc.render.controller;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.lqg.doc.pdf.DocPagesService;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.domain.vo.DocRenderVo;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * 实现备注（给维护的人看，不进接口文档 / Swagger）：
 *
 * 文档渲染与下载（doc/api-contract.md 的 QC / DOC 一节，ticket §2）。
 *
 * <pre>
 *   POST /lqg/doc/{sampleId}/{docKind}/render?audience=internal|external[&force=true]
 *   GET  /lqg/doc/{sampleId}/{docKind}/download?format=docx|pdf&audience=internal|external
 *   GET  /lqg/doc/{sampleId}/{docKind}/pages?audience=internal|external
 * </pre>
 *
 * <p>★ <b>{@code docKind} 的取值是字典 {@code lqg_doc_kind} 的原文</b>
 * （{@code sample_qc / organoid_qc / organoid_score / merged}）—— 与质控侧的
 * {@code sample-qc}（连字符）不同，这边路径段与字典同名（api-contract 第 83~85 行）。
 *
 * <p>★ <b>{@code audience} 是显式入参</b>而不是从登录身份推：内部人员要能预览「外部版长什么样」
 * （甲方样张核对就是这个用法），所以内部端点也接受 {@code audience=external}。
 * 真正的对外隔离在 {@code /mp/ext/**} 那一组（ADR-0004 咽喉），不在这个内部接口上。
 *
 * <p>★ 权限：{@code lqg:doc:render} / {@code lqg:doc:query}，授给 101 / 102
 * （见 V202609261410 迁移）。**不建 C 页面菜单** —— 页面菜单归 DOC-MP-* / DOC-WEB-* 。
 *
 * @author DOC-RENDER-001
 */
/**
 * 文档生成、预览与下载（工作台用）。
 *
 * <pre>
 *   POST /lqg/doc/{sampleId}/{docKind}/render?audience=internal|external[&force=true]   生成（force=true 为「重新生成」）
 *   GET  /lqg/doc/{sampleId}/{docKind}/pages?audience=internal|external                  页面图、原图、附件、状态
 *   GET  /lqg/doc/{sampleId}/{docKind}/download?format=docx|pdf&audience=internal|external  10 分钟签名下载链接
 * </pre>
 *
 * <p>docKind 取 sample_qc / organoid_qc / organoid_score / merged；audience 取 internal（含内部编号）
 * 或 external（送检方看到的那一版，内部人员可以用它核对：内部编号一格随系统参数「合作单位可见内部编号」，默认留空）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/doc")
public class DocRenderController extends BaseController {

    private final DocRenderService docRenderService;
    private final DocPagesService docPagesService;

    /**
     * 生成文档（内容没变且上一版产物齐全时直接返回，不重出）；{@code force=true} 表示「重新生成」，一定重出一版。
     */
    @SaCheckPermission("lqg:doc:render")
    @PostMapping("/{sampleId}/{docKind}/render")
    public R<DocRenderVo> render(@PathVariable Long sampleId,
                                 @PathVariable String docKind,
                                 @RequestParam String audience,
                                 @RequestParam(required = false, defaultValue = "false") boolean force) {
        return R.ok(docRenderService.render(sampleId, docKind, audience, force));
    }

    /**
     * 取 10 分钟签名下载链接 + 文件名（{@code format=docx|pdf}）。
     */
    @SaCheckPermission("lqg:doc:query")
    @GetMapping("/{sampleId}/{docKind}/download")
    public R<DocDownloadVo> download(@PathVariable Long sampleId,
                                     @PathVariable String docKind,
                                     @RequestParam(required = false, defaultValue = "docx") String format,
                                     @RequestParam String audience) {
        return R.ok(docRenderService.download(sampleId, docKind, format, audience));
    }

    /**
     * 页面图片 + 文档中的图片 + 附件 + 状态（工作台预览面板用）。
     *
     * <p>★ {@code data.status} 取自这份文档的整体状态：{@code failed} 时 {@code pages} 一定是空的，
     * 并带 {@code errorMsg} 说明原因（工作台据此显示失败原因与「重新生成」按钮）。
     */
    @SaCheckPermission("lqg:doc:query")
    @GetMapping("/{sampleId}/{docKind}/pages")
    public R<DocPagesVo> pages(@PathVariable Long sampleId,
                               @PathVariable String docKind,
                               @RequestParam String audience) {
        return R.ok(docPagesService.pages(sampleId, docKind, audience));
    }
}
