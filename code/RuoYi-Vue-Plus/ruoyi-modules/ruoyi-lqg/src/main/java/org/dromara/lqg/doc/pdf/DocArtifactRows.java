package org.dromara.lqg.doc.pdf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code t_lqg_doc_file} 的**产物行**读写（DOC-RENDER-001 的行口径 + 本票的 pdf/png 行）。
 *
 * <p>★★ 一行 = 一份产物，键 = {@code (sample_id, doc_kind, audience, file_format, page_no)}
 * （部分唯一索引 {@code uk_doc_file WHERE del_flag='0'}）：
 *
 * <pre>
 *   docx  page_no=0   ← DOC-RENDER-001 渲染的 Word，**同时充当「这份文档的整体状态」**（header 行）
 *   pdf   page_no=0   ← 本票：整份 docx 交给 Gotenberg 转出来的 PDF
 *   png   page_no≥1   ← 本票：PDF 的第 N 页（150 DPI），page_no 从 1 起
 * </pre>
 *
 * <p>★ <b>为什么 docx 行是 header：</b>{@code render} / {@code pages} / {@code download} 都要回答
 * 「这份 (docKind, audience) 现在是什么状态」。流水线任何一步失败 → header 行 {@code failed} +
 * {@code error_msg}，pdf 行也跟着 failed —— 于是「旧产物保留但不再被当成最新返回」这条自然成立：
 * 旧行的 {@code content_hash} 与 header 上的（新）指纹不一致，下载与取图都过不了指纹这一关
 * （ticket §2、accept 2 第 5/6 段断的就是这个事故形态）。
 *
 * <p>★ <b>软删</b>：{@code del_flag} 用显式 UPDATE 置 '1'（并补 {@code update_by/update_time}，
 * 与 {@link #mark} 同一个理由 —— {@code @TableLogic} 的 delete 不带公共字段）。
 *
 * <p>★★ <b>「这一版产物齐不齐」只有一个判据 {@link #completeSet}</b>（独立验收 V04 修复）：
 * header {@code done} + PDF 行 {@code done} 且同指纹 + 至少一页同指纹的页面图。渲染命中缓存、
 * 下载、预览、对内对外可用性都用它 —— 看的是<b>产物实际对应的指纹</b>，不是 header 行自己说了算。
 *
 * <p>★★ <b>失效 = 真失效</b>：{@link #markMergedStale} 把合并件置回 {@code pending}
 * （不再改写指纹），在途渲染的收尾 {@link #markHeaderDone} 是条件更新 —— 失效之后
 * 它落不下 {@code done}，旧成员拼出来的那一份不会借一次在途渲染「复活」。
 *
 * @author DOC-PDF-001 · 独立验收 V04 / V23 修复
 */
@Component
@RequiredArgsConstructor
public class DocArtifactRows {

    public static final String FORMAT_DOCX = "docx";
    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_PNG = "png";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    /** {@code show_internal_no}：这一版「内部编号」一格印出了。 */
    public static final String FLAG_YES = "Y";
    /** {@code show_internal_no}：这一版「内部编号」一格留空。 */
    public static final String FLAG_NO = "N";

    /** {@code error_msg} 列宽 500（SSOT），留一点余量。 */
    private static final int ERROR_MSG_MAX = 480;

    /**
     * 合并件失效时写在 {@code error_msg} 上的标记（状态同时置回 {@code pending}）。
     *
     * <p>它有两个用处：工作台看得懂「为什么在重新生成」；在途渲染的收尾
     * {@link #markHeaderDone} 以「{@code error_msg} 为空」为前提 —— 标记一落，那次渲染就落不了 {@code done}。
     */
    public static final String STALE_MERGED_MSG = "合并件的成员有变化（撤回 / 新完成 / 改内容），正在按最新的已完成文档重新生成";

    private final DocFileMapper docFileMapper;

    /** header 行：docx / page_no=0，这份文档的「整体状态」就在它身上。 */
    public DocFile header(Long sampleId, String docKind, String audience) {
        return find(sampleId, docKind, audience, FORMAT_DOCX, 0);
    }

    public DocFile find(Long sampleId, String docKind, String audience, String fileFormat, int pageNo) {
        return docFileMapper.selectOne(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getSampleId, sampleId)
            .eq(DocFile::getDocKind, docKind)
            .eq(DocFile::getAudience, audience)
            .eq(DocFile::getFileFormat, fileFormat)
            .eq(DocFile::getPageNo, pageNo));
    }

    /**
     * 当前指纹下的页面图片行（升序）。
     *
     * <p>★ 三道过滤：{@code file_format='png'} + {@code content_hash=当前指纹} + {@code done}。
     * 指纹那一道是关键：上一次渲染留下的旧页 {@code page_no} 可能还在（我们**不删**旧产物），
     * 但它不属于当前这一版，一个都不许返回。
     */
    public List<DocFile> pngPages(Long sampleId, String docKind, String audience, String contentHash) {
        return docFileMapper.selectList(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getSampleId, sampleId)
            .eq(DocFile::getDocKind, docKind)
            .eq(DocFile::getAudience, audience)
            .eq(DocFile::getFileFormat, FORMAT_PNG)
            .eq(DocFile::getContentHash, contentHash)
            .eq(DocFile::getRenderStatus, STATUS_DONE)
            .orderByAsc(DocFile::getPageNo));
    }

    /**
     * 取这一份产物那一行；没有就插一行 {@code pending}。已有行则「重置成这一版待生成」：
     * 指纹/模板版本换成新的、状态回 pending、清掉上次的失败原因（**可重试**）。
     */
    public DocFile upsertPending(Long sampleId, String docKind, String audience, String fileFormat,
                                 int pageNo, String contentHash, String templateVersion) {
        DocFile row = find(sampleId, docKind, audience, fileFormat, pageNo);
        if (row == null) {
            DocFile entity = new DocFile();
            entity.setSampleId(sampleId);
            entity.setDocKind(docKind);
            entity.setAudience(audience);
            entity.setFileFormat(fileFormat);
            entity.setPageNo(pageNo);
            entity.setContentHash(contentHash);
            entity.setTemplateVersion(templateVersion);
            entity.setRenderStatus(STATUS_PENDING);
            entity.setMissingImageCount(0);
            docFileMapper.insert(entity);
            return entity;
        }
        mark(row.getId(), w -> w
            .set(DocFile::getContentHash, contentHash)
            .set(DocFile::getTemplateVersion, templateVersion)
            .set(DocFile::getRenderStatus, STATUS_PENDING)
            .set(DocFile::getErrorMsg, null)
            .set(DocFile::getMissingImageCount, 0)
            .set(DocFile::getMissingImages, null));
        row.setContentHash(contentHash);
        row.setTemplateVersion(templateVersion);
        row.setRenderStatus(STATUS_PENDING);
        row.setErrorMsg(null);
        row.setMissingImageCount(0);
        row.setMissingImages(null);
        return row;
    }

    /** 产物落好了：oss_id + 指纹 + done（并记 rendered_time）。 */
    public void markDone(Long id, Long ossId, String contentHash, String templateVersion) {
        mark(id, w -> w
            .set(DocFile::getOssId, ossId)
            .set(DocFile::getContentHash, contentHash)
            .set(DocFile::getTemplateVersion, templateVersion)
            .set(DocFile::getRenderStatus, STATUS_DONE)
            .set(DocFile::getErrorMsg, null)
            .set(DocFile::getRenderedTime, new Date()));
    }

    /**
     * header 行这一版落 {@code done}（带缺图记账），<b>条件更新</b>：只有这一行此刻仍是
     * 「本次渲染置的 pending」（同指纹、没有失效标记）才改得到。
     *
     * <p>★ 为什么是条件更新（V04）：渲染要几秒，期间有人撤回 / 新完成了一份文档，
     * {@link #markMergedStale} 会把这一行置回 pending 并写上失效标记 —— 此时这次渲染拼出来的
     * 是<b>旧成员</b>，绝不能再落成 done（否则撤回的那份会借这次在途渲染重新对外）。
     *
     * <p>★ {@code internalNoShown} 同时落进 {@code show_internal_no}：这一版「内部编号」一格印没印。
     * 与指纹、产物同一次写下，发出去之前（{@code DocRenderService#deliverable}）按它与开关此刻的值核一遍 ——
     * 渲染途中有人关了开关，落下的这一版照样是 {@code Y}，照样发不出去。
     *
     * @return {@code true} = 落上了；{@code false} = 这一版已经过期（调用方按新成员重渲）
     */
    public boolean markHeaderDone(Long id, Long ossId, String contentHash, String templateVersion,
                                  int missingCount, String missingSummary, boolean internalNoShown) {
        LambdaUpdateWrapper<DocFile> wrapper = new LambdaUpdateWrapper<DocFile>()
            .eq(DocFile::getId, id)
            .eq(DocFile::getContentHash, contentHash)
            .eq(DocFile::getRenderStatus, STATUS_PENDING)
            .isNull(DocFile::getErrorMsg);
        wrapper.set(DocFile::getOssId, ossId)
            .set(DocFile::getTemplateVersion, templateVersion)
            .set(DocFile::getRenderStatus, STATUS_DONE)
            .set(DocFile::getErrorMsg, null)
            .set(DocFile::getMissingImageCount, missingCount)
            .set(DocFile::getMissingImages, truncate(missingSummary))
            .set(DocFile::getShowInternalNo, internalNoShown ? FLAG_YES : FLAG_NO)
            .set(DocFile::getRenderedTime, new Date())
            .set(DocFile::getUpdateBy, currentUserId())
            .set(DocFile::getUpdateTime, new Date());
        return docFileMapper.update(null, wrapper) > 0;
    }

    /** 失败：状态 + 原因（原因截断到列宽内）。**oss_id 原样保留**（旧产物不删）。 */
    public void markFailed(Long id, String reason) {
        String msg = truncate(reason);
        mark(id, w -> w
            .set(DocFile::getRenderStatus, STATUS_FAILED)
            .set(DocFile::getErrorMsg, msg));
    }

    /** header 行失败（连同这一版查出来的缺图一起记，工作台清单要显示「缺了哪几张」）。 */
    public void markFailed(Long id, String reason, int missingCount, String missingSummary) {
        String msg = truncate(reason);
        String detail = truncate(missingSummary);
        mark(id, w -> w
            .set(DocFile::getRenderStatus, STATUS_FAILED)
            .set(DocFile::getErrorMsg, msg)
            .set(DocFile::getMissingImageCount, missingCount)
            .set(DocFile::getMissingImages, detail));
    }

    /**
     * ★★ <b>合并件失效 = 真失效</b>（独立验收 V04）：该样本合并件的 header（docx）与 PDF 行
     * 置回 {@code pending}，并在 {@code error_msg} 写上 {@link #STALE_MERGED_MSG}。
     *
     * <p>和旧版 {@code markStale} 的区别（旧版正是 V04 的病根）：旧版只把 header 的
     * {@code content_hash} 改写成新指纹、状态仍是 {@code done}，于是
     * <ul>
     *   <li>下载 Word 照样下发含已撤回文档的旧 docx（header 行的 oss_id 没动）；</li>
     *   <li>PDF 与页面图停在旧指纹 → PDF 下载 400、预览「已完成却 0 页」；</li>
     *   <li>下一次 render 算出的新指纹恰好等于被改写的那个 → 永远命中缓存、「重新生成」空转。</li>
     * </ul>
     * 现在：指纹保持产物<b>真实对应</b>的那一个，状态回 pending（下载 / 预览 / 对外清单一律不给旧产物），
     * 重新渲染由 {@code DocRenderService#invalidateMerged} 在后台接着做。
     *
     * <p>{@code failed} 的行不动（原因要留着给人看；重渲会覆盖它）。只动 docx / pdf 的 page_no=0 行，
     * 页面图行不用动 —— 它们只在 header {@code done} 且同指纹时才会被取出来。
     *
     * @return 置回 pending 的行数
     */
    public int markMergedStale(Long sampleId) {
        LambdaUpdateWrapper<DocFile> wrapper = new LambdaUpdateWrapper<DocFile>()
            .eq(DocFile::getSampleId, sampleId)
            .eq(DocFile::getDocKind, DocKinds.MERGED)
            .in(DocFile::getFileFormat, FORMAT_DOCX, FORMAT_PDF)
            .eq(DocFile::getPageNo, 0)
            .in(DocFile::getRenderStatus, STATUS_PENDING, STATUS_DONE);
        wrapper.set(DocFile::getRenderStatus, STATUS_PENDING)
            .set(DocFile::getErrorMsg, STALE_MERGED_MSG)
            .set(DocFile::getUpdateBy, currentUserId())
            .set(DocFile::getUpdateTime, new Date());
        return docFileMapper.update(null, wrapper);
    }

    /**
     * 一份 (sample, docKind, audience) 的全部产物行软删（OSS 对象保留）。
     *
     * <p>用途：合并件一个成员都不剩（全部撤回）—— 它无从拼起，也不该再以任何状态出现
     * （「还没生成」比「正在生成」更如实：不会有人来生成它）。
     *
     * @return 软删了几行
     */
    public int softDeleteArtifacts(Long sampleId, String docKind, String audience) {
        List<DocFile> all = docFileMapper.selectList(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getSampleId, sampleId)
            .eq(DocFile::getDocKind, docKind)
            .eq(DocFile::getAudience, audience));
        for (DocFile row : all) {
            softDelete(row.getId());
        }
        return all.size();
    }

    /**
     * ★★ <b>这一版产物齐不齐</b>（渲染命中、下载、预览、可用性共用的唯一判据）：
     * header {@code done} 且有产物 + PDF 行 {@code done}、有产物、与 header 同指纹
     * + 至少一页与 header 同指纹的 {@code done} 页面图。
     *
     * <p>看的是<b>产物实际对应的指纹</b>：三种产物由同一次渲染用同一个指纹写下，
     * 只要有一种对不上，这一版就是「撕裂」的，不许当成最新的给出去。
     */
    public boolean completeSet(Long sampleId, String docKind, String audience, DocFile header) {
        if (header == null
            || !STATUS_DONE.equals(header.getRenderStatus())
            || header.getOssId() == null
            || header.getContentHash() == null) {
            return false;
        }
        String hash = header.getContentHash();
        DocFile pdf = find(sampleId, docKind, audience, FORMAT_PDF, 0);
        if (pdf == null
            || !STATUS_DONE.equals(pdf.getRenderStatus())
            || pdf.getOssId() == null
            || !hash.equals(pdf.getContentHash())) {
            return false;
        }
        return !pngPages(sampleId, docKind, audience, hash).isEmpty();
    }

    /**
     * 外部版<b>已生成好</b>的 header 行（docx / page_no=0 / done）—— 内部编号开关切换后，
     * {@code DocRenderService#requestExternalRefresh} 从这里挑出「按旧设置出的」逐份重出。
     */
    public List<DocFile> externalDoneHeaders() {
        return docFileMapper.selectList(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getAudience, DocAudiences.EXTERNAL)
            .eq(DocFile::getFileFormat, FORMAT_DOCX)
            .eq(DocFile::getPageNo, 0)
            .eq(DocFile::getRenderStatus, STATUS_DONE)
            .orderByAsc(DocFile::getSampleId)
            .orderByAsc(DocFile::getId));
    }

    /**
     * 页数变少时把多余的旧页行软删（ticket §2：{@code PageImageService} 的收尾动作）。
     *
     * @return 软删了几行
     */
    public int prunePagesAbove(Long sampleId, String docKind, String audience, int keepPageNo) {
        List<DocFile> extra = docFileMapper.selectList(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getSampleId, sampleId)
            .eq(DocFile::getDocKind, docKind)
            .eq(DocFile::getAudience, audience)
            .eq(DocFile::getFileFormat, FORMAT_PNG)
            .gt(DocFile::getPageNo, keepPageNo));
        for (DocFile row : extra) {
            softDelete(row.getId());
        }
        return extra.size();
    }

    private void softDelete(Long id) {
        LambdaUpdateWrapper<DocFile> wrapper = new LambdaUpdateWrapper<DocFile>().eq(DocFile::getId, id);
        wrapper.set(DocFile::getDelFlag, "1")
            .set(DocFile::getUpdateBy, currentUserId())
            .set(DocFile::getUpdateTime, new Date());
        docFileMapper.update(null, wrapper);
    }

    private void mark(Long id, Consumer<LambdaUpdateWrapper<DocFile>> sets) {
        LambdaUpdateWrapper<DocFile> wrapper = new LambdaUpdateWrapper<DocFile>().eq(DocFile::getId, id);
        sets.accept(wrapper);
        // update(null, wrapper) 不会自动填 update_by / update_time，必须显式补（QC-MODEL-001 的坑）
        wrapper.set(DocFile::getUpdateBy, currentUserId()).set(DocFile::getUpdateTime, new Date());
        docFileMapper.update(null, wrapper);
    }

    /** 异常 → 给人看的一句话（进 {@code error_msg}：工作台显示、可重试的依据）。 */
    public static String reason(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        if (cause != e && !message.contains(cause.toString())) {
            message = message + "（" + cause + "）";
        }
        return truncate(message);
    }

    public static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MSG_MAX ? message.substring(0, ERROR_MSG_MAX) : message;
    }

    private static Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
