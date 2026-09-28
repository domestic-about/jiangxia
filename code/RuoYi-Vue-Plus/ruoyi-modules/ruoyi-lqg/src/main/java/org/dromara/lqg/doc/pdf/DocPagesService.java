package org.dromara.lqg.doc.pdf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.doc.pdf.domain.vo.DocPageItemVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesAttachmentVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesImageVo;
import org.dromara.lqg.doc.pdf.domain.vo.DocPagesVo;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.DocAttachmentMapper;
import org.dromara.lqg.qc.mapper.DocImageMapper;
import org.dromara.lqg.qc.service.QcDocRules;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code GET /lqg/doc/{sampleId}/{docKind}/pages?audience=} 的组装
 * （doc/api-contract.md 的 QC / DOC 一节）；内部小程序与外部预览的取数也落在这里。
 *
 * <pre>
 *   {status, errorMsg, pages:[{pageNo,url}], images:[{url,previewUrl}], attachments:[{fileName,fileSize,url}],
 *    missingImageCount, missingImages}
 * </pre>
 *
 * <p>★★ <b>四件事是这个接口的语义核心</b>：
 *
 * <ol>
 *   <li><b>状态取自这一版产物</b>：三种产物齐全且同指纹（{@link DocArtifactRows#completeSet}）才是 {@code done}、
 *       才给页面图；header 说 done 但产物不齐（撕裂的一版）按 {@code pending} 报，并在后台补一次渲染
 *       ——「预览显示完成却 0 页」这种状态不再出现（独立验收 V04）。</li>
 *   <li><b>只认当前指纹的页</b>：旧版本的 png 行保留在库里，但 {@code content_hash} 对不上就一个都不返回。</li>
 *   <li><b>图与附件不是从 docx 里读的</b>：它们在 {@code t_lqg_doc_image / t_lqg_doc_attachment}
 *       （QC-MODEL-001 的表）上；样本质控表的「细胞活率测定」附件单独一栏（{@code viability_oss_id}），
 *       也并进附件列表（否则文档里印了文件名、预览里却打不开，独立验收 G21）。
 *       合并件只取<b>已完成</b>的成员的图与附件 —— 与合并件本身的成员一致，草稿的东西不混进来。</li>
 *   <li><b>外部版的咽喉校验</b>（独立验收 V24）：外部这条链路先由调用方断言「样本可见 + 这份文档对外可用」，
 *       到这里再逐个核签发对象：页面图的对象键必须是本样本、{@code external} 那一段；
 *       图片与附件若指向渲染产物目录（{@code lqg/doc/...}），也只许是本样本的外部版产物 ——
 *       外部永远拿不到别的样本或内部版的对象。</li>
 * </ol>
 *
 * <p>★ 页面图还要过内部编号开关那一道（{@code DocRenderService#delivery}）：外部版印了内部编号、
 * 开关此刻是关的 → 按「生成中」报、一页都不给；按旧设置 / 旧模板出的一版照给页面图，
 * 同时带 {@code outdated=true}，工作台据此提示「正在按新设置重新生成」并轮询到新的一版。
 *
 * @author DOC-PDF-001 · 独立验收 V04 / V23 / V24 / G21 修复 · G 批 C 组（内部编号开关）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocPagesService {

    /** 渲染产物的对象键前缀（{@code DocOssBytes#objectKey}）。 */
    private static final String ARTIFACT_KEY_PREFIX = "lqg/doc/";

    /** 这一份（这一版）从没生成过：不是产物行上的状态，只在本接口的返回里出现。 */
    public static final String STATUS_NONE = "none";

    private final DocArtifactRows rows;
    private final DocArtifactStore store;
    private final DocRenderModelFactory modelFactory;
    private final DocImageMapper docImageMapper;
    private final DocAttachmentMapper docAttachmentMapper;
    private final DocRenderService renderService;

    public DocPagesVo pages(Long sampleId, String docKind, String audience) {
        String kind = DocKinds.require(docKind);
        String aud = DocAudiences.require(audience);
        modelFactory.requireSample(sampleId);
        final boolean external = DocAudiences.EXTERNAL.equals(aud);
        return DataPermissionHelper.ignore(() -> {
            DocFile header = rows.header(sampleId, kind, aud);
            DocPagesVo vo = new DocPagesVo();
            vo.setDocKind(kind);
            vo.setAudience(aud);
            if (header == null) {
                // ★ 从没生成过是正常的初始态，不是错误（Kevin 本机验收「网页工作台」第 6 行：切页签连弹两条
                //   「这份文档还没生成」）：回 status=none、一页不给，工作台只在预览区显示空态。
                //   只有用户点下载而确实没有产物时才提示（download 那条仍回 400 说清原因）。
                //   小程序两条链路（/mp/int、/mp/ext）先过 requireAvailable，走不到这里。
                vo.setStatus(STATUS_NONE);
                return vo;
            }
            vo.setErrorMsg(header.getErrorMsg());
            vo.setContentHash(header.getContentHash());
            vo.setTemplateVersion(header.getTemplateVersion());
            vo.setMissingImageCount(header.getMissingImageCount() == null ? 0 : header.getMissingImageCount());
            vo.setMissingImages(header.getMissingImages());
            vo.setInternalNoShown(DocArtifactRows.FLAG_YES.equals(header.getShowInternalNo()));

            boolean complete = rows.completeSet(sampleId, kind, aud, header);
            DocRenderService.Delivery delivery = complete
                ? renderService.delivery(sampleId, kind, aud, header, true)
                : null;
            if (complete && delivery != DocRenderService.Delivery.BLOCKED) {
                vo.setOutdated(delivery == DocRenderService.Delivery.OUTDATED);
                vo.setStatus(DocArtifactRows.STATUS_DONE);
                List<DocPageItemVo> pages = new ArrayList<>();
                for (DocFile row : rows.pngPages(sampleId, kind, aud, header.getContentHash())) {
                    if (external && !isOwnArtifact(row.getOssId(), sampleId, DocAudiences.EXTERNAL)) {
                        log.error("外部预览：页面图的对象键不是本样本的外部版，拒绝签发 sampleId={} docKind={} ossId={}",
                            sampleId, kind, row.getOssId());
                        continue;
                    }
                    String url = store.signedUrl(row.getOssId());
                    if (url != null) {
                        pages.add(new DocPageItemVo(row.getPageNo(), url));
                    }
                }
                vo.setPages(pages);
            } else if (DocArtifactRows.STATUS_FAILED.equals(header.getRenderStatus())) {
                vo.setStatus(DocArtifactRows.STATUS_FAILED);
            } else if (delivery == DocRenderService.Delivery.BLOCKED) {
                // 外部版印了内部编号、开关此刻关着：一页都不给，按「生成中」报（delivery 已在后台按新设置重出）
                vo.setStatus(DocArtifactRows.STATUS_PENDING);
                vo.setOutdated(true);
            } else {
                // pending（在途 / 失效待重出）或撕裂的 done：一律按「生成中」报，没人在渲染就在后台补一次
                vo.setStatus(DocArtifactRows.STATUS_PENDING);
                renderService.requestRender(sampleId, kind, aud);
            }
            List<String> docTypes = docTypesOf(sampleId, kind);
            vo.setImages(images(sampleId, docTypes, external));
            vo.setAttachments(attachments(sampleId, docTypes, external));
            return vo;
        });
    }

    /**
     * 图片位：按 样本质控表 → 类器官质控表 → 评分表 的顺序（与合并件的拼接顺序同一个 {@code MERGED_ORDER}）。
     */
    private List<DocPagesImageVo> images(Long sampleId, List<String> docTypes, boolean external) {
        List<DocPagesImageVo> result = new ArrayList<>();
        for (String docType : docTypes) {
            Long docId = docId(sampleId, docType);
            if (docId == null) {
                continue;
            }
            List<DocImage> list = new ArrayList<>(docImageMapper.selectList(new LambdaQueryWrapper<DocImage>()
                .eq(DocImage::getDocType, docType)
                .eq(DocImage::getDocId, docId)
                .orderByAsc(DocImage::getSort)
                .orderByAsc(DocImage::getId)));
            // 按文档里图片位的先后排（收样原始 → 样本观察 → 样本预处理），不是按图片位名字的字母序
            List<String> slotOrder = QcDocRules.slotsOf(docType);
            list.sort(Comparator.comparingInt((DocImage image) -> {
                int i = slotOrder.indexOf(image.getSlot());
                return i < 0 ? Integer.MAX_VALUE : i;
            }));
            for (DocImage image : list) {
                Long previewOssId = image.getPreviewOssId() != null ? image.getPreviewOssId() : image.getOssId();
                String url = signed(image.getOssId(), sampleId, external);
                String previewUrl = signed(previewOssId, sampleId, external);
                if (external && url == null && previewUrl == null) {
                    // 外部侧：两张都签不出（对象不在 / 被咽喉拦下）就不列这一格，不给一个点了没反应的空位
                    continue;
                }
                result.add(new DocPagesImageVo(url, previewUrl));
            }
        }
        return result;
    }

    /**
     * 附件：样本质控表的「细胞活率测定」附件在前（它在 Word 正文里嵌成了「图标 + 文件名」，PDF 与手机预览里点不开，从这里打开），然后是各份的通用附件；
     * 同一个对象只列一次。
     */
    private List<DocPagesAttachmentVo> attachments(Long sampleId, List<String> docTypes, boolean external) {
        List<DocPagesAttachmentVo> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String docType : docTypes) {
            Long docId = docId(sampleId, docType);
            if (docId == null) {
                continue;
            }
            if (QcDocRules.DOC_TYPE_SAMPLE_QC.equals(docType)) {
                QcSampleDoc doc = modelFactory.sampleDoc(sampleId);
                Long viabilityOssId = doc == null ? null : doc.getViabilityOssId();
                if (viabilityOssId != null && seen.add(viabilityOssId)) {
                    SysOss oss = store.oss(viabilityOssId);
                    String name = StringUtils.isNotBlank(doc.getViabilityFileName()) ? doc.getViabilityFileName()
                        : (oss == null ? "细胞活率测定" : oss.getOriginalName());
                    DocPagesAttachmentVo vo = new DocPagesAttachmentVo(name, sizeOf(oss), signed(viabilityOssId, sampleId, external));
                    if (!external || vo.getUrl() != null) {
                        result.add(vo);
                    }
                }
            }
            List<DocAttachment> list = docAttachmentMapper.selectList(new LambdaQueryWrapper<DocAttachment>()
                .eq(DocAttachment::getDocType, docType)
                .eq(DocAttachment::getDocId, docId)
                .orderByAsc(DocAttachment::getSort)
                .orderByAsc(DocAttachment::getId));
            for (DocAttachment attachment : list) {
                if (attachment.getOssId() != null && !seen.add(attachment.getOssId())) {
                    continue;
                }
                String url = signed(attachment.getOssId(), sampleId, external);
                if (external && url == null) {
                    continue;
                }
                result.add(new DocPagesAttachmentVo(attachment.getFileName(), attachment.getFileSize(), url));
            }
        }
        return result;
    }

    /**
     * 签发一个图片 / 附件对象的短时链接。外部侧先过咽喉：指向渲染产物目录的对象只许是本样本的外部版产物。
     */
    private String signed(Long ossId, Long sampleId, boolean external) {
        if (ossId == null) {
            return null;
        }
        if (external) {
            SysOss oss = store.oss(ossId);
            String key = oss == null ? null : oss.getFileName();
            if (key == null) {
                return null;
            }
            if (key.startsWith(ARTIFACT_KEY_PREFIX) && !isOwnArtifactKey(key, sampleId, DocAudiences.EXTERNAL)) {
                log.error("外部预览：图片/附件指向了别的样本或内部版的渲染产物，拒绝签发 sampleId={} ossId={} key={}",
                    sampleId, ossId, key);
                return null;
            }
        }
        return store.signedUrl(ossId);
    }

    /** 渲染产物（页面图）的对象键是不是本样本、这个 audience 的。 */
    private boolean isOwnArtifact(Long ossId, Long sampleId, String audience) {
        SysOss oss = store.oss(ossId);
        return oss != null && isOwnArtifactKey(oss.getFileName(), sampleId, audience);
    }

    /**
     * 对象键 {@code lqg/doc/<sampleId>/<docKind>/<audience>/…}（{@code DocOssBytes#objectKey}）
     * 是不是本样本、这个 audience 的 —— 按段比，不按子串猜。
     */
    static boolean isOwnArtifactKey(String key, Long sampleId, String audience) {
        if (key == null || sampleId == null || !key.startsWith(ARTIFACT_KEY_PREFIX)) {
            return false;
        }
        String[] parts = key.substring(ARTIFACT_KEY_PREFIX.length()).split("/");
        return parts.length >= 4
            && parts[0].equals(String.valueOf(sampleId))
            && parts[2].equals(audience);
    }

    /**
     * docKind → 取图与附件的质控文档类型：单份就是它自己；合并件 = <b>已完成</b>的成员（顺序 = 拼接顺序）。
     */
    private List<String> docTypesOf(Long sampleId, String docKind) {
        if (!DocKinds.isMerged(docKind)) {
            return List.of(docKind);
        }
        List<String> members = new ArrayList<>();
        for (String docType : DocKinds.MERGED_ORDER) {
            if (QcDocRules.STATUS_PUBLISHED.equals(docStatus(sampleId, docType))) {
                members.add(docType);
            }
        }
        return members;
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

    private String docStatus(Long sampleId, String docType) {
        return switch (docType) {
            case QcDocRules.DOC_TYPE_SAMPLE_QC -> {
                QcSampleDoc doc = modelFactory.sampleDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case QcDocRules.DOC_TYPE_ORGANOID_QC -> {
                QcOrganoidDoc doc = modelFactory.organoidDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case QcDocRules.DOC_TYPE_ORGANOID_SCORE -> {
                QcScoreDoc doc = modelFactory.scoreDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            default -> null;
        };
    }

    /** {@code sys_oss.ext1} 里上传侧写下的字节数（没有 → null，前端显示「大小未知」）。 */
    private static Integer sizeOf(SysOss oss) {
        if (oss == null || StringUtils.isBlank(oss.getExt1())) {
            return null;
        }
        try {
            SysOssExt ext = JsonUtils.parseObject(oss.getExt1(), SysOssExt.class);
            Long size = ext == null ? null : ext.getFileSize();
            return size == null ? null : (int) Math.min(size, Integer.MAX_VALUE);
        } catch (Exception e) {
            return null;
        }
    }
}
