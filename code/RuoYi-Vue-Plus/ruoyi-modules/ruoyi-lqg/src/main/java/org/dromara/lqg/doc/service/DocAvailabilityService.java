package org.dromara.lqg.doc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.service.QcDocRules;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * <b>「这一份文档现在能不能给出去」的唯一判据</b>（按 audience 参数化）。
 *
 * <p>★★ <b>为什么单独抽一个类</b>：这条判据有四个条件，其中「产物完整性」那一条
 * （{@link #artifactComplete}）是踩过坑才写对的 —— 内部清单（DOC-MP-001）与外部清单
 * （AUTH-EXT-003）必须给出**同一个**答案，各写一份迟早漂移。
 * 外部的读口 {@link DocExternalQueryService} 把 {@code audience} 写死成
 * {@link org.dromara.lqg.doc.render.DocAudiences#EXTERNAL}（它的方法签名里没有这个入参，
 * 「请求里带 audience=internal」在外部链路上没有落点），内部清单则显式传
 * {@link org.dromara.lqg.doc.render.DocAudiences#INTERNAL}。
 *
 * <p>★★ <b>四个条件</b>（DOC-PUBLISH-001 / AUTH-EXT-003 逐条核过）：
 *
 * <ol>
 *   <li>有这一版（{@code audience}）的 header 行（{@code file_format='docx'} / {@code page_no=0}）；</li>
 *   <li>它 {@code render_status='done'} 且有产物（渲染在途 / 失败都不给 —— FLOW:F-DOC-02.step1 的 produces）；</li>
 *   <li>{@link #artifactComplete}：<b>这一版产物真的完整</b>（当前指纹下有页图 + PDF 行与 header 同版）；</li>
 *   <li>单份文档还要 {@code doc_status='published'}（{@link #isPublished}）。
 *       合并件没有自己的 {@code doc_status} —— 它由成员决定，成员状态变了就走第 3 条。</li>
 * </ol>
 *
 * <p>★ <b>单份文档为什么不查「此刻指纹」</b>：模板版本号进指纹，一次模板升级会让
 * <b>所有</b>历史产物的指纹都对不上。单份文档的「内容改了」由 {@code doc_status} 兜住
 * （改内容 → 回草稿），所以再拿指纹当门槛只会在模板升级后把全部历史文档对送检方隐藏。
 * 所以：<b>单份 = 状态 + 产物完整；合并件 = 产物完整</b>。
 *
 * @author DOC-MP-001（从 AUTH-EXT-003 的 {@code DocExternalQueryService} 原样抽出，行为不变）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocAvailabilityService {

    private final DocRenderModelFactory modelFactory;
    private final DocArtifactRows rows;

    /**
     * 这一份文档现在能不能给出去（清单 / 预览 / 下载必须共用同一处判据，
     * 否则会出现「列表里有、点进去 404」或「列表里没有、却能直接预览」）。
     */
    public boolean available(Long sampleId, String docKind, String audience) {
        String kind = DocKinds.require(docKind);
        if (sampleId == null) {
            return false;
        }
        DocFile header = rows.header(sampleId, kind, audience);
        if (header == null
            || !DocArtifactRows.STATUS_DONE.equals(header.getRenderStatus())
            || header.getOssId() == null) {
            return false;
        }
        if (!artifactComplete(sampleId, kind, audience, header)) {
            return false;
        }
        return DocKinds.isMerged(kind) || isPublished(sampleId, kind);
    }

    /**
     * 不可用一律 404（不泄露存在性：草稿 / 这一版没渲染成功 / 合并件过期，对外长得跟
     * 「没这份文档」一样）。<b>不区分原因</b>是刻意的 —— 说「还是草稿」等于告诉对方
     * 内部已经写了这份文档。
     */
    public void requireAvailable(Long sampleId, String docKind, String audience) {
        if (!available(sampleId, docKind, audience)) {
            throw notFound();
        }
    }

    /**
     * {@code format} 归一化（与 {@code DocRenderService.download} 同一句话）。
     * 必须先判：核对象键要按格式找行，格式非法时找行会得到 null 而错误地报 404。
     */
    public static String requireFormat(String format) {
        String fmt = StringUtils.isBlank(format) ? DocArtifactRows.FORMAT_DOCX : format.trim();
        if (!DocArtifactRows.FORMAT_DOCX.equals(fmt) && !DocArtifactRows.FORMAT_PDF.equals(fmt)) {
            throw new ServiceException("format 只能是 docx 或 pdf，收到：" + fmt, 400);
        }
        return fmt;
    }

    public static ServiceException notFound() {
        return new ServiceException("文档不存在", 404);
    }

    // ── 取数（三张质控表各自的完成状态 / 完成时间 / 合计分）─────────────────────

    /** 三张质控表各自的 {@code doc_status}；行不存在 → null（不建行）。 */
    public String docStatusOf(Long sampleId, String docKind) {
        return switch (docKind) {
            case DocKinds.SAMPLE_QC -> {
                QcSampleDoc doc = modelFactory.sampleDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case DocKinds.ORGANOID_QC -> {
                QcOrganoidDoc doc = modelFactory.organoidDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case DocKinds.ORGANOID_SCORE -> {
                QcScoreDoc doc = modelFactory.scoreDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            default -> null;
        };
    }

    public boolean isPublished(Long sampleId, String docKind) {
        return QcDocRules.STATUS_PUBLISHED.equals(docStatusOf(sampleId, docKind));
    }

    /** 完成时间（合并件没有自己的完成时间 → null，由调用方按成员取）。 */
    public Date publishedTimeOf(Long sampleId, String docKind) {
        return switch (docKind) {
            case DocKinds.SAMPLE_QC -> {
                QcSampleDoc doc = modelFactory.sampleDoc(sampleId);
                yield doc == null ? null : doc.getPublishedTime();
            }
            case DocKinds.ORGANOID_QC -> {
                QcOrganoidDoc doc = modelFactory.organoidDoc(sampleId);
                yield doc == null ? null : doc.getPublishedTime();
            }
            case DocKinds.ORGANOID_SCORE -> {
                QcScoreDoc doc = modelFactory.scoreDoc(sampleId);
                yield doc == null ? null : doc.getPublishedTime();
            }
            default -> null;
        };
    }

    /** 合计分只在评分表那一行上有。 */
    public Integer totalScoreOf(Long sampleId, String docKind) {
        if (!DocKinds.ORGANOID_SCORE.equals(docKind)) {
            return null;
        }
        QcScoreDoc doc = modelFactory.scoreDoc(sampleId);
        return doc == null ? null : doc.getTotalScore();
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    /**
     * ★★ <b>这一版产物真的完整</b>：当前指纹下有一组 {@code done} 的页图，且 PDF 行与 header
     * 行是同一版（{@code content_hash} 相等且都 {@code done}）。
     *
     * <p>为什么不是「把 header 行的 {@code content_hash} 与此刻算出来的指纹比」
     * （DOC-PUBLISH-001 给下游 §6.1 写的那一条）—— <b>实测这条规则无效</b>
     * （AUTH-EXT-003 探针 P28/P29/P30 从红转绿的证据）：
     * 合并件的成员集合一变，{@code DocRenderService#invalidateMerged} 会把 header 行的
     * {@code content_hash} <b>改写成此刻该有的指纹</b>（{@code DocArtifactRows#markStale}），
     * 于是「header 的指纹 == 此刻算出来的指纹」当场成立，而桶里那份 docx 还是旧成员拼的
     * —— 撤回了一份文档之后，送检方仍能列到、预览到、下载到含该文档的旧合并件。
     *
     * <p>{@code markStale} <b>只动 header 的 {@code content_hash}</b>：页图行与 PDF 行保持旧指纹。
     * 所以「页图 + PDF 同版」这两条才是真正锚在<b>产物</b>上的判据 —— 它们也正是内部
     * {@code pages}（{@code DocArtifactRows#pngPages} 按指纹取页）与 {@code download}
     * （PDF 必须与 header 同指纹）本来就用的那两道闸。合并件的成员一变，这里立刻为假，
     * 不依赖那次异步的 {@code invalidateMerged} 有没有跑完。
     */
    private boolean artifactComplete(Long sampleId, String docKind, String audience, DocFile header) {
        String hash = header.getContentHash();
        if (hash == null) {
            return false;
        }
        if (rows.pngPages(sampleId, docKind, audience, hash).isEmpty()) {
            return false;
        }
        DocFile pdf = rows.find(sampleId, docKind, audience, DocArtifactRows.FORMAT_PDF, 0);
        return pdf != null
            && DocArtifactRows.STATUS_DONE.equals(pdf.getRenderStatus())
            && hash.equals(pdf.getContentHash());
    }
}
