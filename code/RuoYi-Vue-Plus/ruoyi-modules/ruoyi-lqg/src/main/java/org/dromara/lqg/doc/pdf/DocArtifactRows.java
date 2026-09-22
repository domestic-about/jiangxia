package org.dromara.lqg.doc.pdf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
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
 * @author DOC-PDF-001
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

    /** {@code error_msg} 列宽 500（SSOT），留一点余量。 */
    private static final int ERROR_MSG_MAX = 480;

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
            docFileMapper.insert(entity);
            return entity;
        }
        mark(row.getId(), w -> w
            .set(DocFile::getContentHash, contentHash)
            .set(DocFile::getTemplateVersion, templateVersion)
            .set(DocFile::getRenderStatus, STATUS_PENDING)
            .set(DocFile::getErrorMsg, null));
        row.setContentHash(contentHash);
        row.setTemplateVersion(templateVersion);
        row.setRenderStatus(STATUS_PENDING);
        row.setErrorMsg(null);
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

    /** 失败：状态 + 原因（原因截断到列宽内）。**oss_id 原样保留**（旧产物不删）。 */
    public void markFailed(Long id, String reason) {
        String msg = truncate(reason);
        mark(id, w -> w
            .set(DocFile::getRenderStatus, STATUS_FAILED)
            .set(DocFile::getErrorMsg, msg));
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
