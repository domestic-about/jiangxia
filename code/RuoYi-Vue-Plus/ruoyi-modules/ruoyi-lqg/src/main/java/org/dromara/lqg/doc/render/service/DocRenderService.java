package org.dromara.lqg.doc.render.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.pdf.DocArtifactStore;
import org.dromara.lqg.doc.pdf.PageImageService;
import org.dromara.lqg.doc.pdf.PdfConvertService;
import org.dromara.lqg.doc.pdf.PdfFonts;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocOleEmbedder;
import org.dromara.lqg.doc.render.DocRenderModel;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.DocTemplate;
import org.dromara.lqg.doc.render.DocxRenderer;
import org.dromara.lqg.doc.render.MissingImage;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.domain.vo.DocRenderVo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 文档渲染的编排（FLOW:F-DOC-01.step2 → step3 → step4，ADR-0005）。
 *
 * <p>★★ <b>流水线 = 一个指纹、三种产物</b>（DOC-PDF-001 扩写）：
 *
 * <pre>
 *   算指纹 → 这一版三种产物齐全且指纹一致 → 直接返回（**一个字节都不写库**，rendered_time/oss_id 原样）
 *          → 否则 header 置 pending
 *          → poi-tl 出 docx（取不到的图逐张记缺图；字体是甲方原件的宋体 / Times New Roman；
 *                          格子定高、长字缩小、值格居中；细胞活率附件作为嵌入对象放进那一格）
 *          → 外部版有缺图 → 整份 failed（#217：外部不给缺图的文档）
 *          → Gotenberg 把这份 docx 整体转 PDF        （PdfConvertService；转的是副本：嵌入对象换成图标图片
 *                                                    DocOleEmbedder#forConversion，字体换成容器字体 PdfFonts）
 *          → PDFBox 按 150 DPI 出每页 PNG            （PageImageService，page_no 从 1 起）
 *          → 三个产物都存 OSS（同一个 content_hash）→ 三行都 done（内部版带缺图数与明细）
 *          → **任何一步失败** → 整体 failed + error_msg（旧产物保留但不再被当成最新返回）
 * </pre>
 *
 * <p>★ 「内容没变不重出」这条是**零写库**而不是「写回同样的值」：快照带着
 * {@code rendered_time}，任何一次无谓的 UPDATE 都会让两次快照不同（DOC-RENDER-001 的 accept）。
 * 命中的判据是 {@link DocArtifactRows#completeSet}：看<b>产物实际对应的指纹</b>（header、PDF、页面图三者同指纹），
 * 不是 header 行自己说了算 —— 撕裂的一版（例如只有 header 被改过）永远不算命中。
 *
 * <p>★ 「重新生成」要真重渲：{@code force=true} 跳过缓存判定（工作台的「重新生成」按钮带它）。
 *
 * <p>★ <b>audience 在缓存键里</b>（部分唯一索引 {@code uk_doc_file} 带上它）：内外部各一行，
 * 谁后渲染都不会覆盖谁。
 *
 * <p>★ <b>merged</b>：先把几份 docx 拼成一份（每份另起一页），再**整体转 PDF**（不是拼 PDF）
 * ——合并版的 Word 与 PDF 同源（ADR-0005）。成员 = 此刻 {@code doc_status='published'} 的几份；
 * 成员一变（完成 / 撤回 / 改内容回草稿），{@link #markMergedStale} 当场让旧合并件失效，
 * {@link #invalidateMerged} 在后台按新成员重出（一份都不剩就把合并件撤下）。
 *
 * <p>★ <b>同一份 (样本, 种类, 版本) 的渲染串行</b>（进程内锁）：两次渲染交错写同一组产物行，
 * 会拼出「header 是这次的、页面图一半是上次的」那种撕裂。本项目后端单实例部署，进程内锁足够。
 *
 * <p>★★ <b>发出去之前还有最后一道 {@link #delivery}</b>（甲方 2026-09-24 意见第 23 行）：系统参数
 * {@code lqg.ext.show-internal-no} 也管外部版文档了。「这一格印什么」进了指纹，但指纹回答不了
 * 「已经落下的那一份印没印」，所以每一版随产物记下 {@code show_internal_no}，下载 / 预览 / 清单
 * 发出去之前按它与开关此刻的值核一遍：<b>印了内部编号、开关此刻是关的 → 不给</b>，并在后台按新设置重出；
 * 按旧设置 / 旧模板出、但给出去无妨的一版照给，同时排进后台单线程队列重出（{@link #requestRefresh}）。
 * 开关一切（{@code DocInternalNoSwitchAspect}）就把外部版里按旧设置出的逐份排队重出（{@link #requestExternalRefresh}），
 * 已完成的文档不用人工重新「完成并同步」。
 *
 * @author DOC-RENDER-001 · DOC-PDF-001（pdf / png 与整体失败语义）· 独立验收 V04 / V23 修复 · G 批 C 组（内部编号开关）
 *         · H 批 H4 组（嵌入附件的转换副本）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocRenderService {

    /** Word 的产物格式（header 行）。 */
    public static final String FORMAT_DOCX = DocArtifactRows.FORMAT_DOCX;

    /** 签名链接有效期（ticket §2：10 分钟）。 */
    public static final Duration SIGNED_URL_TTL = DocArtifactStore.SIGNED_URL_TTL;

    /** 一次 render 调用里，因「渲染期间成员变了」而重来的最多次数（防止极端情况下无限重渲）。 */
    private static final int MAX_ATTEMPTS = 3;

    /** 外部版缺图时失败原因的总长（error_msg 列宽 500，DocArtifactRows 截到 480）。 */
    private static final int MISSING_REASON_MAX = 470;

    private final DocRenderModelFactory modelFactory;
    private final DocxRenderer renderer;
    private final DocArtifactRows rows;
    private final DocArtifactStore artifactStore;
    private final PdfConvertService pdfConvertService;
    private final PageImageService pageImageService;

    /** (sampleId/docKind/audience) → 渲染锁。 */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 后台补渲染用的执行器（默认公共池；测试里换成收集器，保证可复现）。 */
    private Executor asyncExecutor = ForkJoinPool.commonPool();

    /** 仅供同包测试替换执行器。 */
    void setAsyncExecutor(Executor executor) {
        this.asyncExecutor = executor;
    }

    /**
     * 后台「按新设置重出」的队列：<b>单线程</b>，一次只转一份（FLOW:F-DOC-01「渲染异步排队、并发 1」）。
     * 开关一切可能要重出一大批外部版，不能一下子全压到转换服务上；清单页读到的过期文档也走这里。
     */
    private Executor refreshExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lqg-doc-refresh");
        thread.setDaemon(true);
        return thread;
    });

    /** 已经排进 {@link #refreshExecutor}、还没开始的 (样本/种类/版本)：同一份只排一次。 */
    private final Set<String> refreshQueued = ConcurrentHashMap.newKeySet();

    /** 「扫一遍外部版」已经排上、还没开始：再来的请求不必再排（那一轮开始时读的就是最新的开关）。 */
    private final AtomicBoolean scanQueued = new AtomicBoolean();

    /** 上一轮扫描时开关的值（null = 本进程还没扫过）；读路径上撞见旧设置的产物时据此决定要不要再扫。 */
    private volatile Boolean scannedSwitch;

    /** 仅供同包测试替换执行器。 */
    void setRefreshExecutor(Executor executor) {
        this.refreshExecutor = executor;
    }

    /** 进程退出时停掉后台重出的线程（排着的活不等了：下次读到时照样会自愈）。 */
    @PreDestroy
    void shutdownRefreshExecutor() {
        if (refreshExecutor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    /** 一次产出的结果。 */
    private enum Outcome { DONE, FAILED, SUPERSEDED }

    /**
     * 一版<b>产物齐全</b>的文档此刻能不能发出去（{@link #delivery}）。
     */
    public enum Delivery {
        /** 给：与当前设置、当前模板一致。 */
        OK,
        /** 给，但它是按旧设置 / 旧模板出的（给出去无妨），后台已排队按新设置重出。 */
        OUTDATED,
        /** 不给：外部版印了内部编号、而开关此刻是关的；后台已在按新设置重出。 */
        BLOCKED
    }

    /** 外部版有缺图：按渲染失败处理（#217）。 */
    static final class MissingImagesException extends RuntimeException {
        MissingImagesException(String message) {
            super(message);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /lqg/doc/{sampleId}/{docKind}/render
    // ══════════════════════════════════════════════════════════════════════

    public DocRenderVo render(Long sampleId, String docKind, String audience) {
        return render(sampleId, docKind, audience, false);
    }

    /**
     * @param force {@code true} = 「重新生成」：不看缓存，一定重出一版
     */
    public DocRenderVo render(Long sampleId, String docKind, String audience, boolean force) {
        String kind = DocKinds.require(docKind);
        String aud = DocAudiences.require(audience);
        modelFactory.requireSample(sampleId);
        ReentrantLock lock = lockOf(sampleId, kind, aud);
        lock.lock();
        try {
            return renderLocked(sampleId, kind, aud, force);
        } finally {
            lock.unlock();
        }
    }

    /** 调用方必须已经持有这一份的锁。 */
    private DocRenderVo renderLocked(Long sampleId, String kind, String aud, boolean force) {
        boolean skipCache = force;
        for (int attempt = 1; ; attempt++) {
            // ── 1. 组装输入（指纹与渲染数据同源；锁内算，拿到的是此刻的成员）────
            final String version = DocTemplate.version();
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
            final boolean bypass = skipCache;

            Object result = DataPermissionHelper.ignore(() -> {
                // ── 2. 命中缓存：三种产物齐全且同指纹 → 零写库返回 ────────────
                DocFile header = rows.header(sampleId, kind, aud);
                if (!bypass && isHit(sampleId, kind, aud, header, hash)) {
                    log.info("渲染命中缓存：sampleId={} docKind={} audience={} hash={}", sampleId, kind, aud, hash);
                    DocRenderVo vo = toVo(header);
                    vo.setCached(true);
                    return vo;
                }
                return produce(sampleId, kind, aud, model, members, hash, version);
            });
            if (result instanceof DocRenderVo cached) {
                return cached;
            }
            Outcome outcome = (Outcome) result;
            if (outcome != Outcome.SUPERSEDED || attempt >= MAX_ATTEMPTS) {
                if (outcome == Outcome.SUPERSEDED) {
                    log.warn("合并件连续 {} 次在渲染期间成员又变了，先停在 pending：sampleId={} audience={}",
                        attempt, sampleId, aud);
                }
                return DataPermissionHelper.ignore(() -> toVo(rows.header(sampleId, kind, aud)));
            }
            // 这一版在渲染期间被判过期（成员变了）：按此刻的成员再来一次
            log.info("渲染期间成员有变化，按新成员重出：sampleId={} docKind={} audience={} 第 {} 次",
                sampleId, kind, aud, attempt + 1);
            skipCache = false;
        }
    }

    /**
     * 产出一版（header 置 pending → docx → [外部版缺图即失败] → pdf → png → 三行 done）。
     */
    private Outcome produce(Long sampleId, String kind, String aud, DocRenderModel model,
                            List<DocRenderModel> members, String hash, String version) {
        DocFile pending = rows.upsertPending(sampleId, kind, aud, DocArtifactRows.FORMAT_DOCX, 0, hash, version);
        DocFile pdfRow = null;
        List<MissingImage> missing = new ArrayList<>();
        try {
            // ── docx（取不到的图记进 missing）──────────────────────────────
            byte[] docx = renderDocx(model, members, missing);
            if (DocAudiences.EXTERNAL.equals(aud) && !missing.isEmpty()) {
                // #217：外部版取图失败 = 渲染失败，不对外发一份缺图的文档
                String head = "外部版有 " + missing.size() + " 张图取不到，按规定不发给送检方：";
                String tail = "。把图补上后点「重新生成」";
                throw new MissingImagesException(head
                    + MissingImage.summary(missing, MISSING_REASON_MAX - head.length() - tail.length()) + tail);
            }
            // ── docx → pdf（转换服务不可用 / 超时都在这里抛）────────────────
            //    下载的 Word 保留甲方原件的字体与嵌入的附件；转 PDF 的是副本：嵌入对象换成同一张图标图片
            //    （PDF 里是「图标 + 文件名」，转换服务不必解析 OLE、也不多传附件字节），字体换成容器开源字体（PdfFonts）
            String fileName = modelFactory.displayName(sampleId, kind, aud) + ".docx";
            byte[] pdf = pdfConvertService.toPdf(PdfFonts.forConversion(DocOleEmbedder.forConversion(docx)), fileName);

            // ── 三种产物同一个指纹，逐个落 OSS + 落行 ─────────────────────
            Long docxOssId = artifactStore.uploadDocx(sampleId, kind, aud, hash, docx);
            pdfRow = rows.upsertPending(sampleId, kind, aud, DocArtifactRows.FORMAT_PDF, 0, hash, version);
            Long pdfOssId = artifactStore.uploadPdf(sampleId, kind, aud, hash, pdf);

            // ── PDF → 每页 PNG（页数变少时软删多余旧页）────────────────────
            int pageCount = pageImageService.publish(sampleId, kind, aud, hash, version, pdf);

            // ── header 最后落 done（条件更新：渲染期间被判过期就落不上）────────
            //    「内部编号」一格印没印随这一版一起落下，发出去之前按它核开关（delivery）
            String missingSummary = MissingImage.summary(missing);
            if (!rows.markHeaderDone(pending.getId(), docxOssId, hash, version, missing.size(), missingSummary,
                model.isInternalNoShown())) {
                log.info("这一版在渲染期间已经过期（成员有变化），不落 done：sampleId={} docKind={} audience={} hash={}",
                    sampleId, kind, aud, hash);
                return Outcome.SUPERSEDED;
            }
            rows.markDone(pdfRow.getId(), pdfOssId, hash, version);
            if (missing.isEmpty()) {
                log.info("渲染完成：sampleId={} docKind={} audience={} hash={} docxOssId={} pdfOssId={} pages={}",
                    sampleId, kind, aud, hash, docxOssId, pdfOssId, pageCount);
            } else {
                log.warn("渲染完成但缺图（内部版照出，已记入渲染记录）：sampleId={} docKind={} audience={} 缺 {} 张：{}",
                    sampleId, kind, aud, missing.size(), missingSummary);
            }
            return Outcome.DONE;
        } catch (Exception e) {
            // 失败可见可重试（FLOW:F-DOC-01.step6）：状态 failed + 原因，不甩 500、不吞异常。
            // 旧产物（上一版的 oss_id / 页行）原样保留，只是不再被当成最新返回。
            String reason = e instanceof MissingImagesException ? e.getMessage() : DocArtifactRows.reason(e);
            log.warn("文档渲染失败：sampleId={} docKind={} audience={} hash={}：{}",
                sampleId, kind, aud, hash, reason);
            rows.markFailed(pending.getId(), reason, missing.size(), MissingImage.summary(missing));
            if (pdfRow != null) {
                rows.markFailed(pdfRow.getId(), reason);
            }
            return Outcome.FAILED;
        }
    }

    /**
     * docx 字节：单体直接渲染；合并件先把各成员拼起来（每份另起一页），拼好之后再嵌附件（poi-tl 拼接时不搬嵌入对象）。
     * 缺图逐张记进 {@code missing}。
     */
    private byte[] renderDocx(DocRenderModel model, List<DocRenderModel> members, List<MissingImage> missing) {
        if (members == null) {
            return renderer.render(model, missing);
        }
        return renderer.renderMerged(members, missing);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 合并件的失效（DOC-PUBLISH-001「任何一份变化都让该样本的 merged 失效」）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * ★★ <b>同步</b>：成员集合刚变（完成并同步 / 撤回 / 已完成的文档被改回草稿）→ 该样本的合并件
     * 当场置回 pending、打上失效标记（{@link DocArtifactRows#markMergedStale}）。
     *
     * <p>由 {@code DocPublishService} 在改完 {@code doc_status} 的同一个请求里调用：接口返回时，
     * 旧合并件已经不会再被下载、预览或出现在任何清单里；在途的那次渲染也落不下 done。
     * 真正的重出在后台（{@link #invalidateMerged}）。
     */
    public void markMergedStale(Long sampleId) {
        int changed = DataPermissionHelper.ignore(() -> rows.markMergedStale(sampleId));
        if (changed > 0) {
            log.info("合并件已失效（成员有变化），等待按新成员重出：sampleId={} 行数={}", sampleId, changed);
        }
    }

    /**
     * ★ <b>后台</b>：按此刻的成员把合并件重出一版；一份成员都不剩 → 把合并件撤下（软删产物行）。
     *
     * <p>「过期没过期」不去猜：用此刻该有的指纹去问 {@link #isHit}（产物齐全且同指纹才算没过期）。
     * 没过期（例如改的是一份草稿，成员没变）→ 什么都不做；过期了 → 重渲（与 {@link #render} 同一把锁）。
     * 这个样本从没渲染过合并件 → 不替谁生成（还没人要过它）。
     */
    public void invalidateMerged(Long sampleId) {
        modelFactory.requireSample(sampleId);
        for (String aud : DocAudiences.ALL) {
            ReentrantLock lock = lockOf(sampleId, DocKinds.MERGED, aud);
            lock.lock();
            try {
                DocFile header = DataPermissionHelper.ignore(() -> rows.header(sampleId, DocKinds.MERGED, aud));
                if (header == null) {
                    continue;
                }
                String hash = mergedHash(sampleId, aud);
                if (hash == null) {
                    int removed = DataPermissionHelper.ignore(() -> rows.softDeleteArtifacts(sampleId, DocKinds.MERGED, aud));
                    log.info("合并件已没有任何已完成的成员，撤下旧合并件：sampleId={} audience={} 软删 {} 行",
                        sampleId, aud, removed);
                    continue;
                }
                boolean fresh = DataPermissionHelper.ignore(() ->
                    isHit(sampleId, DocKinds.MERGED, aud, rows.header(sampleId, DocKinds.MERGED, aud), hash));
                if (fresh) {
                    continue;
                }
                log.info("合并件按新成员重出：sampleId={} audience={} 新指纹={}", sampleId, aud, hash);
                renderLocked(sampleId, DocKinds.MERGED, aud, false);
            } catch (Exception e) {
                log.warn("合并件重出失败（状态已如实落在渲染记录上）：sampleId={} audience={}：{}",
                    sampleId, aud, e.toString());
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 合并件此刻该有的指纹；<b>一份成员都没有</b>时返回 {@code null}（合并件无从拼起）。
     */
    private String mergedHash(Long sampleId, String audience) {
        try {
            List<DocRenderModel> members = modelFactory.mergedMembers(sampleId, audience);
            return DocRenderModel.merged(audience, DocTemplate.version(), members).contentHash();
        } catch (ServiceException e) {
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 自愈：读的时候发现「该重出却没人在重出」→ 在后台补一次
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 在后台补一次渲染（这一份此刻没有渲染在跑时才补；在跑就什么都不做）。
     *
     * <p>用途：预览 / 下载读到 pending（失效后还没重出、或上一次渲染中途进程重启）或撕裂的一版时，
     * 不让页面停在「生成中」干等 —— 成员集合变化后，下载或预览要么触发重算、要么明确提示生成中，
     * 这里两件事一起做。
     *
     * @return {@code true} = 这次确实排了一次渲染
     */
    public boolean requestRender(Long sampleId, String docKind, String audience) {
        ReentrantLock lock = lockOf(sampleId, docKind, audience);
        if (lock.isLocked()) {
            return false;
        }
        asyncExecutor.execute(() -> {
            try {
                if (DocKinds.isMerged(docKind)) {
                    // 合并件走失效那条路：有成员就按新成员重出，一份不剩就撤下（不会停在永远的「生成中」）
                    invalidateMerged(sampleId);
                } else {
                    render(sampleId, docKind, audience, false);
                }
            } catch (Exception e) {
                log.warn("后台补渲染没成：sampleId={} docKind={} audience={}：{}", sampleId, docKind, audience, e.toString());
            }
        });
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /lqg/doc/{sampleId}/{docKind}/download
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 只发**10 分钟内的短时签名链接**（产物在私有桶里，绝不发公开地址）。
     *
     * <p>★ {@code format=docx} 与 {@code format=pdf} 都支持；别的值明确 400，不悄悄给一份别的格式。
     *
     * <p>★★ <b>只下发「这一版三种产物齐全且同指纹」的产物</b>（{@link DocArtifactRows#completeSet}）：
     * 失效（pending）、失败、撕裂的一版一律 400 并说清楚是哪一种 —— 绝不把上一版（例如含已撤回文档的
     * 旧合并件）当成最新文档发出去。pending / 撕裂时顺手在后台补一次渲染（{@link #requestRender}）。
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
            requireDeliverable(sampleId, kind, aud, header);
            DocFile target = DocArtifactRows.FORMAT_PDF.equals(fmt)
                ? rows.find(sampleId, kind, aud, DocArtifactRows.FORMAT_PDF, 0)
                : header;
            DocDownloadVo vo = new DocDownloadVo();
            vo.setUrl(artifactStore.signedUrlOrFail(target.getOssId()));
            vo.setFileName(modelFactory.displayName(sampleId, kind, aud) + "." + fmt);
            return vo;
        });
    }

    /**
     * 这一版能不能下发；不能就抛 400，并把「还没生成 / 正在生成 / 上次失败」说清楚。
     */
    private void requireDeliverable(Long sampleId, String kind, String aud, DocFile header) {
        if (header == null) {
            throw new ServiceException(DocKinds.isMerged(kind)
                ? "合并件还没有生成（至少要有一份已完成的质控文档）"
                : "这份文档还没生成，请先在质控文档页点「预览」", 400);
        }
        if (DocArtifactRows.STATUS_FAILED.equals(header.getRenderStatus())) {
            throw new ServiceException("这份文档上次生成失败："
                + StringUtils.blankToDefault(header.getErrorMsg(), "（没有记下原因）")
                + "。请在质控文档页点「重新生成」", 400);
        }
        if (!rows.completeSet(sampleId, kind, aud, header)) {
            requestRender(sampleId, kind, aud);
            throw new ServiceException(DocKinds.isMerged(kind)
                ? "合并件正在按最新的已完成文档重新生成，请稍后再下载"
                : "这份文档正在生成，请稍后再下载", 400);
        }
        if (delivery(sampleId, kind, aud, header, true) == Delivery.BLOCKED) {
            throw new ServiceException("「外部可见内部编号」的设置改过了，这份文档正在按新设置重新生成，请稍后再下载", 400);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 发出去之前的最后一道：内部编号开关 + 过期自愈（甲方 2026-09-24 意见第 23 行）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * ★★ 一版<b>产物齐全</b>的文档（调用方已过 {@link DocArtifactRows#completeSet}）此刻能不能发出去。
     *
     * <p>下载、预览（页面图）、对内对外清单都走这一个判据：
     * <ul>
     *   <li>{@link Delivery#BLOCKED}：<b>外部版印了内部编号、开关此刻是关的</b> —— 不给，并在后台按新设置重出。
     *       看的是这一版落库时记下的 {@code show_internal_no}，不是指纹：渲染途中有人关了开关、合并件、
     *       页面图、Word 与 PDF 都由这一版的 header 行说了算，于是开关关着时外部任何路径都拿不到印了编号的那一份；</li>
     *   <li>{@link Delivery#OUTDATED}：按旧设置 / 旧模板出、但给出去无妨（外部版留空而开关已打开、模板版本升了）——
     *       照给，同时在后台重出。重出期间已完成的文档不会从清单上消失；</li>
     *   <li>{@link Delivery#OK}：与当前设置、当前模板一致。</li>
     * </ul>
     *
     * @param urgent {@code true} = 有人正在看这一份（预览 / 下载）：立刻在后台重出；
     *               {@code false} = 清单逐行判的：排进单线程队列，不一下子压给转换服务
     */
    public Delivery delivery(Long sampleId, String kind, String aud, DocFile header, boolean urgent) {
        if (!internalNoAllowed(aud, header)) {
            log.info("外部版印了内部编号、开关此刻是关的，不发出去，按新设置重出：sampleId={} docKind={}", sampleId, kind);
            if (urgent) {
                requestRender(sampleId, kind, aud);
            } else {
                requestRefresh(sampleId, kind, aud);
            }
            requestExternalRefresh(false);
            return Delivery.BLOCKED;
        }
        if (isOutdated(sampleId, kind, aud, header)) {
            if (urgent) {
                requestRender(sampleId, kind, aud);
            } else {
                requestRefresh(sampleId, kind, aud);
            }
            return Delivery.OUTDATED;
        }
        return Delivery.OK;
    }

    /**
     * 这一版的「内部编号」一格与开关此刻的值冲不冲突 —— 只有「外部版印了、开关关着」这一种不许发。
     * 内部版一直印，不看开关。
     */
    public boolean internalNoAllowed(String aud, DocFile header) {
        if (DocAudiences.isInternal(aud) || header == null) {
            return true;
        }
        return !DocArtifactRows.FLAG_YES.equals(header.getShowInternalNo()) || modelFactory.showsInternalNo(aud);
    }

    /**
     * 这一版是不是按旧设置 / 旧模板出的：模板版本号不是当前的；或（外部版）「内部编号」一格印没印
     * 与开关此刻该有的样子不一致。
     *
     * <p>★ 模板升级也在这里自愈：单份文档的对外判据不看指纹（见 {@code DocAvailabilityService} 的类注释），
     * 所以升模板后已完成的文档照给旧版、同时在后台按新模板重出 —— 不用人工逐份点「重新生成」。
     */
    public boolean isOutdated(Long sampleId, String kind, String aud, DocFile header) {
        if (header == null) {
            return false;
        }
        if (!DocTemplate.version().equals(header.getTemplateVersion())) {
            return true;
        }
        if (DocAudiences.isInternal(aud)) {
            return false;
        }
        boolean shown = DocArtifactRows.FLAG_YES.equals(header.getShowInternalNo());
        return shown != expectedInternalNoShown(sampleId, kind, modelFactory.showsInternalNo(aud));
    }

    /**
     * 按开关此刻的值，这一份外部版的「内部编号」一格该不该印：样本质控表随开关；合并件随开关且要有
     * 已完成的样本质控表成员；类器官质控表 / 评分表没有这一格，永远不印。
     */
    private boolean expectedInternalNoShown(Long sampleId, String kind, boolean switchOn) {
        if (!switchOn) {
            return false;
        }
        if (DocKinds.hasInternalNoCell(kind)) {
            return true;
        }
        return DocKinds.isMerged(kind) && modelFactory.isPublished(sampleId, DocKinds.SAMPLE_QC);
    }

    /**
     * 排进后台单线程队列按新设置重出一份（同一份已经在队里就不再排）。
     *
     * @return {@code true} = 这次确实排上了
     */
    public boolean requestRefresh(Long sampleId, String kind, String aud) {
        String key = lockKey(sampleId, kind, aud);
        if (!refreshQueued.add(key)) {
            return false;
        }
        refreshExecutor.execute(() -> {
            refreshQueued.remove(key);
            try {
                // 与 render 同一把锁、同一套缓存判定：已经是新的就零写库返回
                render(sampleId, kind, aud, false);
            } catch (Exception e) {
                log.warn("后台按新设置重出没成：sampleId={} docKind={} audience={}：{}", sampleId, kind, aud, e.toString());
            }
        });
        return true;
    }

    /**
     * ★ 内部编号开关切换之后：把外部版里<b>按旧设置出的</b>（印了而开关已关 / 该印而没印）逐份排进后台重出，
     * 已完成的文档不用人工重新「完成并同步」。
     *
     * @param force {@code true} = 开关刚被改过（{@code DocInternalNoSwitchAspect}），一定扫一遍；
     *              {@code false} = 读路径上撞见了按旧设置出的一版：只有「上一轮扫描之后开关又变了」才再扫
     *              （兜住改库 + 刷缓存、进程重启这类绕过了参数设置页的切换）
     */
    public void requestExternalRefresh(boolean force) {
        if (!force && Boolean.valueOf(modelFactory.showsInternalNo(DocAudiences.EXTERNAL)).equals(scannedSwitch)) {
            return;
        }
        if (!scanQueued.compareAndSet(false, true)) {
            return;
        }
        refreshExecutor.execute(() -> {
            scanQueued.set(false);
            try {
                scanExternal();
            } catch (Exception e) {
                log.warn("按内部编号开关扫描外部版失败（读路径上还会逐份自愈）：{}", e.toString());
            }
        });
    }

    /**
     * 扫一遍外部版已生成好的 header 行，挑出「内部编号」一格与开关此刻不一致的，逐份排队重出。
     * 草稿不对外，不替它重出（下次「完成并同步」会按当时的设置出）。
     *
     * @return 这一轮排上了几份
     */
    int scanExternal() {
        boolean switchOn = modelFactory.showsInternalNo(DocAudiences.EXTERNAL);
        scannedSwitch = switchOn;
        int queued = DataPermissionHelper.ignore(() -> {
            int n = 0;
            for (DocFile header : rows.externalDoneHeaders()) {
                Long sampleId = header.getSampleId();
                String kind = header.getDocKind();
                if (sampleId == null || kind == null) {
                    continue;
                }
                if (!DocKinds.isMerged(kind) && !modelFactory.isPublished(sampleId, kind)) {
                    continue;
                }
                boolean shown = DocArtifactRows.FLAG_YES.equals(header.getShowInternalNo());
                if (shown != expectedInternalNoShown(sampleId, kind, switchOn)
                    && requestRefresh(sampleId, kind, DocAudiences.EXTERNAL)) {
                    n++;
                }
            }
            return n;
        });
        log.info("内部编号开关此刻是{}：外部版里按旧设置出的 {} 份已排队重出", switchOn ? "开" : "关", queued);
        return queued;
    }

    /** 命中判据：这一版三种产物齐全且同指纹（看产物，不只看 header 行）。 */
    private boolean isHit(Long sampleId, String kind, String aud, DocFile header, String hash) {
        return header != null
            && hash.equals(header.getContentHash())
            && rows.completeSet(sampleId, kind, aud, header);
    }

    private ReentrantLock lockOf(Long sampleId, String docKind, String audience) {
        return locks.computeIfAbsent(lockKey(sampleId, docKind, audience), k -> new ReentrantLock());
    }

    private static String lockKey(Long sampleId, String docKind, String audience) {
        return sampleId + "/" + docKind + "/" + audience;
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
        vo.setMissingImageCount(row.getMissingImageCount() == null ? 0 : row.getMissingImageCount());
        vo.setMissingImages(row.getMissingImages());
        return vo;
    }
}
