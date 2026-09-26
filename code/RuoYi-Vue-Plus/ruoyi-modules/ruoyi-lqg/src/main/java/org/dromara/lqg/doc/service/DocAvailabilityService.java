package org.dromara.lqg.doc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.doc.pdf.DocArtifactRows;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.service.QcDocRules;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * <b>「这一份文档现在能不能给出去」的唯一判据</b>（按 audience 参数化）。
 *
 * <p>★★ <b>为什么单独抽一个类</b>：这条判据有五个条件，其中「产物完整性」那一条
 * （{@code DocArtifactRows#completeSet}）是踩过坑才写对的 —— 内部清单（DOC-MP-001）与外部清单
 * （AUTH-EXT-003）必须给出**同一个**答案，各写一份迟早漂移。
 * 外部的读口 {@link DocExternalQueryService} 把 {@code audience} 写死成
 * {@link org.dromara.lqg.doc.render.DocAudiences#EXTERNAL}（它的方法签名里没有这个入参，
 * 「请求里带 audience=internal」在外部链路上没有落点），内部清单则显式传
 * {@link org.dromara.lqg.doc.render.DocAudiences#INTERNAL}。
 *
 * <p>★★ <b>五个条件</b>（DOC-PUBLISH-001 / AUTH-EXT-003 逐条核过；第 5 条是 G 批 C 组加的）：
 *
 * <ol>
 *   <li>有这一版（{@code audience}）的 header 行（{@code file_format='docx'} / {@code page_no=0}）；</li>
 *   <li>它 {@code render_status='done'} 且有产物（渲染在途 / 失败都不给 —— FLOW:F-DOC-02.step1 的 produces）；</li>
 *   <li><b>这一版产物真的完整</b>（{@code DocArtifactRows#completeSet}：当前指纹下有页图 + PDF 行与 header 同版）；</li>
 *   <li>单份文档还要 {@code doc_status='published'}（{@link #isPublished}）。
 *       合并件没有自己的 {@code doc_status} —— 它由成员决定，成员状态变了就走第 3 条；</li>
 *   <li><b>内部编号开关</b>（甲方 2026-09-24 意见第 23 行）：外部版印了内部编号、系统参数
 *       {@code lqg.ext.show-internal-no} 此刻是关的 → 不给（{@code DocRenderService#delivery}，
 *       后台已按新设置重出）。按旧设置 / 旧模板出、给出去无妨的一版照给，同时在后台重出。</li>
 * </ol>
 *
 * <p>★ <b>单份文档为什么不查「此刻指纹」</b>：模板版本号进指纹，一次模板升级会让
 * <b>所有</b>历史产物的指纹都对不上。单份文档的「内容改了」由 {@code doc_status} 兜住
 * （改内容 → 回草稿），所以再拿指纹当门槛只会在模板升级后把全部历史文档对送检方隐藏。
 * 所以：<b>单份 = 状态 + 产物完整；合并件 = 产物完整</b>。
 *
 * <p>★ 独立验收 V04 之后：合并件的成员一变，{@code DocRenderService#markMergedStale} 在同一个请求里
 * 把它置回 pending，这里的「产物完整」当场为假；重出完成前，合并件对内对外都不出现。
 * 「产物完整」的判据挪到了 {@code DocArtifactRows#completeSet}（渲染命中、下载、预览、这里共用一处）。
 *
 * @author DOC-MP-001（从 AUTH-EXT-003 的 {@code DocExternalQueryService} 原样抽出，行为不变）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocAvailabilityService {

    private final DocRenderModelFactory modelFactory;
    private final DocArtifactRows rows;
    private final DocRenderService renderService;

    /**
     * 这一份文档现在能不能给出去（清单 / 预览 / 下载必须共用同一处判据，
     * 否则会出现「列表里有、点进去 404」或「列表里没有、却能直接预览」）。
     *
     * <p>清单逐行判用这个：需要重出的排进后台单线程队列（不一下子把整页清单都压给转换服务）。
     */
    public boolean available(Long sampleId, String docKind, String audience) {
        return available(sampleId, docKind, audience, false);
    }

    /**
     * @param urgent {@code true} = 有人正点开这一份（预览 / 下载）：需要重出的立刻在后台重出
     */
    public boolean available(Long sampleId, String docKind, String audience, boolean urgent) {
        String kind = DocKinds.require(docKind);
        if (sampleId == null) {
            return false;
        }
        DocFile header = rows.header(sampleId, kind, audience);
        if (!rows.completeSet(sampleId, kind, audience, header)) {
            return false;
        }
        if (!DocKinds.isMerged(kind) && !isPublished(sampleId, kind)) {
            return false;
        }
        // 最后一道：内部编号开关（印了内部编号的外部版、开关关着 → 不给）+ 旧设置 / 旧模板的后台重出
        return renderService.delivery(sampleId, kind, audience, header, urgent) != DocRenderService.Delivery.BLOCKED;
    }

    /**
     * 不可用一律 404（不泄露存在性：草稿 / 这一版没渲染成功 / 合并件过期，对外长得跟
     * 「没这份文档」一样）。<b>不区分原因</b>是刻意的 —— 说「还是草稿」等于告诉对方
     * 内部已经写了这份文档。
     */
    public void requireAvailable(Long sampleId, String docKind, String audience) {
        if (!available(sampleId, docKind, audience, true)) {
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

}
