package org.dromara.lqg.doc.render.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.pdf.DocArtifactStore;
import org.dromara.lqg.doc.pdf.PageImageService;
import org.dromara.lqg.doc.pdf.PdfConvertService;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModel;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.DocTemplate;
import org.dromara.lqg.doc.render.DocxRenderer;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.domain.vo.DocRenderVo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档渲染的编排（FLOW:F-DOC-01.step2 → step3 → step4，ADR-0005）。
 *
 * <p>★★ <b>流水线 = 一个指纹、三种产物</b>（DOC-PDF-001 扩写）：
 *
 * <pre>
 *   算指纹 → 已有 done header 且指纹一致 → 直接返回（**一个字节都不写库**，rendered_time/oss_id 原样）
 *          → 否则 header 置 pending
 *          → poi-tl 出 docx
 *          → Gotenberg 把这份 docx 整体转 PDF        （PdfConvertService）
 *          → PDFBox 按 150 DPI 出每页 PNG            （PageImageService，page_no 从 1 起）
 *          → 三个产物都存 OSS（同一个 content_hash）→ 三行都 done
 *          → **任何一步失败** → 整体 failed + error_msg（旧产物保留但不再被当成最新返回）
 * </pre>
 *
 * <p>★ 「任何一步失败 → 整体 failed」是 ticket §2 与 accept 2 的原话。之所以把
 * {@code (docKind, audience)} 的失败记在 docx 那一行（header）上：{@code render} / {@code pages} /
 * {@code download} 都要有一个地方回答「这份文档现在什么状态」，而一行一份产物的表结构里，
 * docx/page_no=0 是最自然的那一行（见 {@link DocArtifactRows} 的类注释）。
 *
 * <p>★ 「内容没变不重出」这条是**零写库**而不是「写回同样的值」：accept 2 的快照带着
 * {@code rendered_time}，任何一次无谓的 UPDATE 都会让 {@code S1 != S2}（DOC-RENDER-001 的 accept）。
 *
 * <p>★ <b>audience 在缓存键里</b>（部分唯一索引 {@code uk_doc_file} 带上它）：内外部各一行，
 * 谁后渲染都不会覆盖谁。
 *
 * <p>★ <b>merged</b>：先把几份 docx 拼成一份（每份另起一页），再**整体转 PDF**（不是拼 PDF）
 * ——合并版的 Word 与 PDF 同源（ticket §0 口径复述 4 / ADR-0005）。
 *
 * @author DOC-RENDER-001 · DOC-PDF-001（pdf / png 与整体失败语义）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocRenderService {

    /** Word 的产物格式（header 行）。 */
    public static final String FORMAT_DOCX = DocArtifactRows.FORMAT_DOCX;

    /** 签名链接有效期（ticket §2：10 分钟）。 */
    public static final Duration SIGNED_URL_TTL = DocArtifactStore.SIGNED_URL_TTL;

    private final DocRenderModelFactory modelFactory;
    private final DocxRenderer renderer;
    private final DocArtifactRows rows;
    private final DocArtifactStore artifactStore;
    private final PdfConvertService pdfConvertService;
    private final PageImageService pageImageService;

    // ══════════════════════════════════════════════════════════════════════
    // POST /lqg/doc/{sampleId}/{docKind}/render
    // ══════════════════════════════════════════════════════════════════════

    public DocRenderVo render(Long sampleId, String docKind, String audience) {
        String kind = DocKinds.require(docKind);
        String aud = DocAudiences.require(audience);
        modelFactory.requireSample(sampleId);
        String version = DocTemplate.version();

        // ── 1. 组装输入（指纹与渲染数据同源）────────────────────────────
        final List<DocRenderModel> members;
        final DocRenderModel model;
        if (DocKinds.isMerged(kind)) {
            members = modelFactory.mergedMembers(sampleId, aud);
            model = DocRenderModel.merged(aud, version, members);
        } else {
            members = null;
            model = modelFactory.single(sampleId, kind, aud);
        }
        final String hash = model.contentHash();

        return DataPermissionHelper.ignore(() -> {
            // ── 2. 命中缓存：指纹一致 + done + 有产物 → 零写库返回 ────────
            DocFile header = rows.header(sampleId, kind, aud);
            if (isHit(header, hash)) {
                log.info("渲染命中缓存：sampleId={} docKind={} audience={} hash={}", sampleId, kind, aud, hash);
                DocRenderVo vo = toVo(header);
                vo.setCached(true);
                return vo;
            }

            // ── 3. 这一版开始：header（docx）置 pending ───────────────────
            DocFile pending = rows.upsertPending(sampleId, kind, aud,
                DocArtifactRows.FORMAT_DOCX, 0, hash, version);
            DocFile pdfRow = null;
            try {
                // ── 4. docx → pdf（转换服务不可用 / 超时都在这里抛）────────
                byte[] docx = renderDocx(model, members);
                String fileName = modelFactory.displayName(sampleId, kind, aud) + ".docx";
                byte[] pdf = pdfConvertService.toPdf(docx, fileName);

                // ── 5. 三种产物同一个指纹，逐个落 OSS + 落行 ───────────────
                Long docxOssId = artifactStore.uploadDocx(sampleId, kind, aud, hash, docx);
                pdfRow = rows.upsertPending(sampleId, kind, aud,
                    DocArtifactRows.FORMAT_PDF, 0, hash, version);
                Long pdfOssId = artifactStore.uploadPdf(sampleId, kind, aud, hash, pdf);

                // ── 6. PDF → 每页 PNG（页数变少时软删多余旧页）────────────
                int pageCount = pageImageService.publish(sampleId, kind, aud, hash, version, pdf);

                // ── 7. 三行全绿才算这一版 done（header 最后置 done）────────
                rows.markDone(pending.getId(), docxOssId, hash, version);
                rows.markDone(pdfRow.getId(), pdfOssId, hash, version);
                log.info("渲染完成：sampleId={} docKind={} audience={} hash={} docxOssId={} pdfOssId={} pages={}",
                    sampleId, kind, aud, hash, docxOssId, pdfOssId, pageCount);
            } catch (Exception e) {
                // 失败可见可重试（FLOW:F-DOC-01.step6）：状态 failed + 原因，不甩 500、不吞异常。
                // 旧产物（上一版的 oss_id / 页行）原样保留，只是不再被当成最新返回。
                String reason = DocArtifactRows.reason(e);
                log.warn("文档渲染失败：sampleId={} docKind={} audience={} hash={}：{}",
                    sampleId, kind, aud, hash, reason);
                rows.markFailed(pending.getId(), reason);
                if (pdfRow != null) {
                    rows.markFailed(pdfRow.getId(), reason);
                }
            }
            return toVo(rows.header(sampleId, kind, aud));
        });
    }

    /** docx 字节：单体直接渲染；合并件先把各成员拼起来（每份另起一页）。 */
    private byte[] renderDocx(DocRenderModel model, List<DocRenderModel> members) {
        if (members == null) {
            return renderer.render(model);
        }
        List<byte[]> parts = new ArrayList<>();
        for (DocRenderModel member : members) {
            parts.add(renderer.render(member));
        }
        return renderer.merge(parts);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 合并件的失效（DOC-PUBLISH-001 的「任何一份变化都让该样本的 merged 失效」）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * ★ <b>把该样本的合并件产物标记成过期</b>（不软删、不删 OSS 对象）：成员集合
     * （{@code doc_status='published'} 的那几份）一变，合并件就不是最新的了。
     *
     * <p>「怎么算过期」不去猜：直接复用指纹 —— 用合并件自己的
     * {@link DocRenderModel#merged} 算出<b>此刻</b>该有的指纹，与库里 header 行上的
     * {@code content_hash} 比。一致 = 没事；不一致 = 把 header 行的指纹换成新的，
     * 于是「页面图 / 下载只认当前指纹」那几道过滤自然不再返回旧产物，
     * 下一次「预览」会重新渲染（ticket §2 的口径复述 4）。
     *
     * <p>★ 一份成员都没有（全部撤回）时也要标记：合并件无从拼起，旧产物更不能露出去。
     */
    public void invalidateMerged(Long sampleId) {
        modelFactory.requireSample(sampleId);
        DataPermissionHelper.ignore(() -> {
            for (String aud : DocAudiences.ALL) {
                DocFile header = rows.header(sampleId, DocKinds.MERGED, aud);
                if (header == null) {
                    continue;
                }
                String hash = mergedHash(sampleId, aud);
                if (hash.equals(header.getContentHash())) {
                    continue;
                }
                rows.markStale(header.getId(), hash);
                log.info("合并件已标记过期：sampleId={} audience={} 旧指纹={} 新指纹={}",
                    sampleId, aud, header.getContentHash(), hash);
            }
        });
    }

    /**
     * 合并件此刻该有的指纹；<b>一份成员都没有</b>时返回一个稳定的哨兵值
     * （不能让 {@code invalidateMerged} 抛 400 —— 撤回最后一份文档也要把旧合并件标记成过期）。
     */
    private String mergedHash(Long sampleId, String audience) {
        try {
            List<DocRenderModel> members = modelFactory.mergedMembers(sampleId, audience);
            return DocRenderModel.merged(audience, DocTemplate.version(), members).contentHash();
        } catch (Exception e) {
            log.info("合并件没有可用成员（旧产物标记成过期）：sampleId={} audience={}", sampleId, audience);
            return "merged-empty";
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /lqg/doc/{sampleId}/{docKind}/download
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 只发**10 分钟内的短时签名链接**（产物在私有桶里，绝不发公开地址）。
     *
     * <p>★ {@code format=docx}（DOC-RENDER-001）与 {@code format=pdf}（本票）都支持；
     * 别的值明确 400，不悄悄给一份别的格式。
     *
     * <p>★ <b>PDF 必须与 header 同一个指纹</b>：转换失败时 header 是 failed（直接 400）；
     * 就算状态是 done、而 pdf 行还停在上一版（指纹对不上），也一律 400
     * —— 宁可让用户点「重新生成」，也不能把上一版分数的 PDF 当成最新文档发出去
     * （accept 2 counterfeit 里那句「Word 是新的、预览图是旧的」的事故形态）。
     */
    public DocDownloadVo download(Long sampleId, String docKind, String format, String audience) {
        String kind = DocKinds.require(docKind);
        String aud = DocAudiences.require(audience);
        String fmt = StringUtils.isBlank(format) ? FORMAT_DOCX : format.trim();
        if (!DocArtifactRows.FORMAT_DOCX.equals(fmt) && !DocArtifactRows.FORMAT_PDF.equals(fmt)) {
            throw new ServiceException("format 只能是 docx 或 pdf，收到：" + fmt, 400);
        }
        return DataPermissionHelper.ignore(() -> {
            DocFile header = rows.header(sampleId, kind, aud);
            if (header == null
                || !DocArtifactRows.STATUS_DONE.equals(header.getRenderStatus())
                || header.getOssId() == null) {
                throw new ServiceException("这份文档还没生成（先 POST /lqg/doc/" + sampleId + "/" + kind
                    + "/render?audience=" + aud + "）", 400);
            }
            DocFile target = header;
            if (DocArtifactRows.FORMAT_PDF.equals(fmt)) {
                target = rows.find(sampleId, kind, aud, DocArtifactRows.FORMAT_PDF, 0);
                boolean stale = target == null
                    || !DocArtifactRows.STATUS_DONE.equals(target.getRenderStatus())
                    || target.getOssId() == null
                    || !header.getContentHash().equals(target.getContentHash());
                if (stale) {
                    throw new ServiceException("这份文档的 PDF 还没生成好（转换失败或还是上一版），"
                        + "请重新生成：POST /lqg/doc/" + sampleId + "/" + kind
                        + "/render?audience=" + aud, 400);
                }
            }
            DocDownloadVo vo = new DocDownloadVo();
            vo.setUrl(artifactStore.signedUrlOrFail(target.getOssId()));
            vo.setFileName(modelFactory.displayName(sampleId, kind, aud) + "." + fmt);
            return vo;
        });
    }

    /** 命中判据：done + 指纹一致 + 有产物。 */
    private static boolean isHit(DocFile row, String hash) {
        return row != null
            && DocArtifactRows.STATUS_DONE.equals(row.getRenderStatus())
            && hash.equals(row.getContentHash())
            && row.getOssId() != null;
    }

    private DocRenderVo toVo(DocFile row) {
        DocRenderVo vo = new DocRenderVo();
        if (row == null) {
            return vo;
        }
        vo.setSampleId(row.getSampleId());
        vo.setDocKind(row.getDocKind());
        vo.setAudience(row.getAudience());
        vo.setStatus(row.getRenderStatus());
        vo.setOssId(row.getOssId());
        vo.setContentHash(row.getContentHash());
        vo.setTemplateVersion(row.getTemplateVersion());
        vo.setErrorMsg(row.getErrorMsg());
        vo.setRenderedTime(row.getRenderedTime());
        return vo;
    }
}
