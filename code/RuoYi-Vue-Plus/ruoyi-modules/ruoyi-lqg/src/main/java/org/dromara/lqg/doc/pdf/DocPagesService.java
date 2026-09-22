package org.dromara.lqg.doc.pdf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.doc.pdf.domain.vo.DocPageItemVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesAttachmentVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesImageVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.DocAttachmentMapper;
import org.dromara.lqg.qc.mapper.DocImageMapper;
import org.dromara.lqg.qc.service.QcDocRules;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /lqg/doc/{sampleId}/{docKind}/pages?audience=} 的组装
 * （doc/api-contract.md 的 QC / DOC 一节 + ticket §2）。
 *
 * <pre>
 *   {status, errorMsg, pages:[{pageNo,url}], images:[{url,previewUrl}], attachments:[{fileName,fileSize,url}]}
 * </pre>
 *
 * <p>★★ <b>三件事是这个接口的语义核心</b>：
 *
 * <ol>
 *   <li><b>状态取自 header 行</b>（docx / page_no=0）：{@code done} 才给页面图片；
 *       {@code pending} / {@code failed} 一律 {@code pages=[]} ——「Word 是新的、预览图是旧的」
 *       这类事故就是在这里被堵死的（accept 2 第 5/6 段）。</li>
 *   <li><b>只认当前指纹的页</b>：旧版本的 png 行**保留在库里**（ticket §2 的「旧产物保留」），
 *       但 {@code content_hash} 对不上就一个都不返回。</li>
 *   <li><b>图与附件不是从 docx 里读的</b>：它们在 {@code t_lqg_doc_image / t_lqg_doc_attachment}
 *       （QC-MODEL-001 的表）上，{@code docId} 是质控文档行的 id；merged 取三份的并集。</li>
 * </ol>
 *
 * @author DOC-PDF-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocPagesService {

    private final DocArtifactRows rows;
    private final DocArtifactStore store;
    private final DocRenderModelFactory modelFactory;
    private final DocImageMapper docImageMapper;
    private final DocAttachmentMapper docAttachmentMapper;

    public DocPagesVo pages(Long sampleId, String docKind, String audience) {
        String kind = DocKinds.require(docKind);
        String aud = DocAudiences.require(audience);
        modelFactory.requireSample(sampleId);
        return DataPermissionHelper.ignore(() -> {
            DocFile header = rows.header(sampleId, kind, aud);
            if (header == null) {
                throw new ServiceException("这份文档还没生成（先 POST /lqg/doc/" + sampleId + "/" + kind
                    + "/render?audience=" + aud + "）", 400);
            }
            DocPagesVo vo = new DocPagesVo();
            vo.setDocKind(kind);
            vo.setAudience(aud);
            vo.setStatus(header.getRenderStatus());
            vo.setErrorMsg(header.getErrorMsg());
            vo.setContentHash(header.getContentHash());
            vo.setTemplateVersion(header.getTemplateVersion());

            if (DocArtifactRows.STATUS_DONE.equals(header.getRenderStatus())
                && header.getContentHash() != null) {
                List<DocPageItemVo> pages = new ArrayList<>();
                for (DocFile row : rows.pngPages(sampleId, kind, aud, header.getContentHash())) {
                    String url = store.signedUrl(row.getOssId());
                    if (url != null) {
                        pages.add(new DocPageItemVo(row.getPageNo(), url));
                    }
                }
                vo.setPages(pages);
            }
            vo.setImages(images(sampleId, kind));
            vo.setAttachments(attachments(sampleId, kind));
            return vo;
        });
    }

    /**
     * 图片位：merged = 三份的并集（按 样本质控表 → 类器官质控表 → 评分表 的顺序，
     * 与合并件的拼接顺序同一个 {@code MERGED_ORDER}）。
     */
    private List<DocPagesImageVo> images(Long sampleId, String docKind) {
        List<DocPagesImageVo> result = new ArrayList<>();
        for (String docType : docTypesOf(docKind)) {
            Long docId = docId(sampleId, docType);
            if (docId == null) {
                continue;
            }
            List<DocImage> list = docImageMapper.selectList(new LambdaQueryWrapper<DocImage>()
                .eq(DocImage::getDocType, docType)
                .eq(DocImage::getDocId, docId)
                .orderByAsc(DocImage::getSlot)
                .orderByAsc(DocImage::getSort)
                .orderByAsc(DocImage::getId));
            for (DocImage image : list) {
                Long previewOssId = image.getPreviewOssId() != null ? image.getPreviewOssId() : image.getOssId();
                result.add(new DocPagesImageVo(store.signedUrl(image.getOssId()), store.signedUrl(previewOssId)));
            }
        }
        return result;
    }

    private List<DocPagesAttachmentVo> attachments(Long sampleId, String docKind) {
        List<DocPagesAttachmentVo> result = new ArrayList<>();
        for (String docType : docTypesOf(docKind)) {
            Long docId = docId(sampleId, docType);
            if (docId == null) {
                continue;
            }
            List<DocAttachment> list = docAttachmentMapper.selectList(new LambdaQueryWrapper<DocAttachment>()
                .eq(DocAttachment::getDocType, docType)
                .eq(DocAttachment::getDocId, docId)
                .orderByAsc(DocAttachment::getSort)
                .orderByAsc(DocAttachment::getId));
            for (DocAttachment attachment : list) {
                result.add(new DocPagesAttachmentVo(attachment.getFileName(), attachment.getFileSize(),
                    store.signedUrl(attachment.getOssId())));
            }
        }
        return result;
    }

    /** docKind → 质控文档类型（merged 是三份的并集，顺序 = 合并件拼接顺序）。 */
    private static List<String> docTypesOf(String docKind) {
        if (DocKinds.isMerged(docKind)) {
            return DocKinds.MERGED_ORDER;
        }
        return List.of(docKind);
    }

    /** 质控文档行的 id（缺草稿行 → null，不建行；渲染链路只读不写，见 QC-MODEL-001 的坑 6）。 */
    private Long docId(Long sampleId, String docType) {
        return switch (docType) {
            case QcDocRules.DOC_TYPE_SAMPLE_QC -> {
                QcSampleDoc doc = modelFactory.sampleDoc(sampleId);
                yield doc == null ? null : doc.getId();
            }
            case QcDocRules.DOC_TYPE_ORGANOID_QC -> {
                QcOrganoidDoc doc = modelFactory.organoidDoc(sampleId);
                yield doc == null ? null : doc.getId();
            }
            case QcDocRules.DOC_TYPE_ORGANOID_SCORE -> {
                QcScoreDoc doc = modelFactory.scoreDoc(sampleId);
                yield doc == null ? null : doc.getId();
            }
            default -> null;
        };
    }
}
