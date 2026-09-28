package org.dromara.lqg.doc.render;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.doc.render.mapper.DocDictMapper;
import org.dromara.lqg.ext.service.ExtInternalNoSwitch;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.DocAttachmentMapper;
import org.dromara.lqg.qc.mapper.DocImageMapper;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.qc.service.QcDocRules;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleFieldCipher;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 把三张质控表 + 样本主档 + 图片位 + 附件**翻译成一份 {@link DocRenderModel}**。
 *
 * <p>★★ <b>渲染数据与指纹同源</b>：这个类只产出 {@code DocRenderModel}，指纹由它算、
 * poi-tl 由它填。于是「文档上印了什么」与「指纹覆盖了什么」不可能分叉 —— 想漏一个字段，
 * 得先在 {@link DocRenderModel#canonical()} 里漏掉它（{@code DocFingerprintTest} 逐类钉）。
 *
 * <p>★ <b>只读</b>（DocOssBytes 同域的口径）：渲染链路绝不写质控表。缺草稿行 = 400
 * （页面上还没打开过那份文档），不是偷偷建一份。
 *
 * <p>★ <b>从样本主档带出的字段</b>（患者姓名 / 来源单位 / 性别 / 收样时间 / 处理时间 /
 * 操作人 / 内部编号）**不在质控表里存**（QC-MODEL-001 的类注释写死了这条），所以只能从这里
 * 读；改了样本主档 → 指纹变 → 文档重出。这正是 accept 2 的「只改来源单位」那条。
 *
 * <p>★ <b>外部版</b>：{@code internal_no} 在外部版自己的模型里按系统参数
 * {@code lqg.ext.show-internal-no} 取值 —— 关着（默认）是空串，开着印内部编号（不是在下载时抹或补），
 * 于是 external 的指纹、对象键、产物都与 internal 完全独立（ticket §0 口径复述 2）。
 * 开关<b>每次组装只读一次</b>（{@link #mergedMembers} 把同一个值传给三个成员）：
 * 合并件里不会出现「前一份印了、后一份没印」的半新半旧。
 *
 * @author DOC-RENDER-001 · G 批 C 组（外部版内部编号随开关）· H 批 H4 组（细胞活率附件嵌进 Word）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocRenderModelFactory {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /**
     * 「处理时间」印到分钟：那一格很窄（原件列宽约 4.5 个字），带上秒会折成三行（「2026-08-」「25」「14:20:00」），
     * 到分钟是两行；纸面表格上秒数也没有意义（G 批 C 组，甲方 2026-09-24 意见第 26 行「不太美观」）。
     */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SampleMapper sampleMapper;
    private final QcSampleDocMapper sampleDocMapper;
    private final QcOrganoidDocMapper organoidDocMapper;
    private final QcScoreDocMapper scoreDocMapper;
    private final DocImageMapper docImageMapper;
    private final DocAttachmentMapper docAttachmentMapper;
    private final DocDictMapper dictMapper;
    private final SampleFieldCipher fieldCipher;
    private final ExtInternalNoSwitch internalNoSwitch;

    /**
     * 这个版本此刻该不该印内部编号：内部版一直印；外部版看系统参数 {@code lqg.ext.show-internal-no}
     * （读不到 / 没配 = 关，宁可少印也不漏出去 —— 与外部页面数据同一个读口 {@link ExtInternalNoSwitch}）。
     */
    public boolean showsInternalNo(String audience) {
        return DocAudiences.isInternal(audience) || internalNoSwitch.enabled();
    }

    /** 取样本（软删的查不到 → 400）。 */
    public Sample requireSample(Long sampleId) {
        if (sampleId == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        Sample sample = sampleMapper.selectById(sampleId);
        if (sample == null) {
            throw new ServiceException("样本不存在", 400);
        }
        return sample;
    }

    /**
     * 一份非合并文档的模型（内部编号开关此刻读一次）。
     */
    public DocRenderModel single(Long sampleId, String docKind, String audience) {
        return single(sampleId, docKind, audience, showsInternalNo(audience));
    }

    /**
     * 一份非合并文档的模型；{@code showInternalNo} = 「内部编号」一格印不印（调用方读好开关传进来）。
     */
    public DocRenderModel single(Long sampleId, String docKind, String audience, boolean showInternalNo) {
        Sample sample = requireSample(sampleId);
        DocRenderModel model = new DocRenderModel(docKind, audience, DocTemplate.version())
            .internalNoShown(showInternalNo && DocKinds.hasInternalNoCell(docKind));
        switch (docKind) {
            case DocKinds.SAMPLE_QC -> fillSampleQc(model, sample);
            case DocKinds.ORGANOID_QC -> fillOrganoidQc(model, sample);
            case DocKinds.ORGANOID_SCORE -> fillScore(model, sample);
            default -> throw new ServiceException("「" + docKind + "」没有独立模板，请走合并件分支", 400);
        }
        return model;
    }

    /**
     * 合并件的成员：按 {@link DocKinds#MERGED_ORDER} 取**已完成**（{@code doc_status='published'}）
     * 的几份。ticket §2 明说本张里 published 的判断直接读 {@code doc_status}。
     */
    public List<DocRenderModel> mergedMembers(Long sampleId, String audience) {
        requireSample(sampleId);
        // 开关只读一次，三个成员用同一个值（合并件里不会一份印了、一份没印）
        boolean showInternalNo = showsInternalNo(audience);
        List<DocRenderModel> members = new ArrayList<>();
        if (isPublished(sampleId, DocKinds.SAMPLE_QC)) {
            members.add(single(sampleId, DocKinds.SAMPLE_QC, audience, showInternalNo));
        }
        if (isPublished(sampleId, DocKinds.ORGANOID_QC)) {
            members.add(single(sampleId, DocKinds.ORGANOID_QC, audience, showInternalNo));
        }
        if (isPublished(sampleId, DocKinds.ORGANOID_SCORE)) {
            members.add(single(sampleId, DocKinds.ORGANOID_SCORE, audience, showInternalNo));
        }
        if (members.isEmpty()) {
            throw new ServiceException("这个样本还没有已完成的质控文档，合并件无从拼起", 400);
        }
        return members;
    }

    /** 这份单文档此刻是不是已完成（{@code doc_status='published'}）；合并件没有自己的状态 → false。 */
    public boolean isPublished(Long sampleId, String docKind) {
        String status = switch (docKind) {
            case DocKinds.SAMPLE_QC -> {
                QcSampleDoc doc = sampleDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case DocKinds.ORGANOID_QC -> {
                QcOrganoidDoc doc = organoidDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case DocKinds.ORGANOID_SCORE -> {
                QcScoreDoc doc = scoreDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            default -> null;
        };
        return QcDocRules.STATUS_PUBLISHED.equals(status);
    }

    /** 下载用的文件名：`样本质控表-T-hga03.docx`（内部版）/ `样本质控表-SJ90000005.docx`（外部版）。 */
    public String displayName(Long sampleId, String docKind, String audience) {
        Sample sample = requireSample(sampleId);
        String suffix = DocAudiences.isInternal(audience) ? sample.getInternalNo() : sample.getSubmitNo();
        String tail = StringUtils.isBlank(suffix) ? "" : "-" + suffix;
        return DocKinds.label(docKind) + tail;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 三份文档各自的字段
    // ══════════════════════════════════════════════════════════════════════

    private void fillSampleQc(DocRenderModel model, Sample sample) {
        QcSampleDoc doc = sampleDoc(sample.getId());
        if (doc == null) {
            throw new ServiceException("这个样本还没有样本质控表（先在工作台打开一次质控页）", 400);
        }
        model
            .text("patient_no", fieldCipher.decrypt(doc.getPatientNo()))
            .text("source_unit_name", sample.getSourceUnitName())
            .text("donor_name", fieldCipher.decrypt(sample.getDonorName()))
            .text("sampling_site", doc.getSamplingSite())
            .text("sampling_method", doc.getSamplingMethod())
            .text("gender", dictLabel("lqg_gender", sample.getGender()))
            .text("clinical_diagnosis", doc.getClinicalDiagnosis())
            // ★ 三段模板原文只有 QcDocRules 一个真相源；这里做**渲染期回落**：
            //   seed 里 9000001005 的草稿行三段是 NULL（不是走「新建草稿」建的），
            //   空白就按原件的标准文字印 —— 否则甲方样张上的那句话会凭空消失。
            .text("receive_desc", blankTo(doc.getReceiveDesc(), QcDocRules.RECEIVE_DESC_DEFAULT))
            .text("receive_date", sample.getReceiveDate() == null ? "" : sample.getReceiveDate().format(DATE))
            .text("process_time", dateTime(sample.getProcessTime()))
            .text("operator_name", sample.getOperatorName())
            // ★ 内部编号：内部版一直印；外部版按系统参数（关着 = 模型里就没有，不是下载时抹）
            .text("internal_no", model.isInternalNoShown() ? sample.getInternalNo() : "")
            .text("viability_file_name", doc.getViabilityFileName())
            .text("orig_desc", doc.getOrigDesc())
            .text("observe_desc", blankTo(doc.getObserveDesc(), QcDocRules.OBSERVE_DESC_DEFAULT))
            .text("pretreat_desc", blankTo(doc.getPretreatDesc(), QcDocRules.PRETREAT_DESC_DEFAULT))
            // ★ 细胞活率附件：作为嵌入对象放进这一格（图标 + 文件名，Word / WPS 里双击打开；Kevin 本机验收「网页工作台」第 5 行）。
            //   换一个附件 = oss_id 变 = 指纹变 = 重出（DocRenderModel#canonical 的 embed 行）
            .embed("viability_file_name", doc.getViabilityOssId(), doc.getViabilityFileName())
            .part("doc_status=" + doc.getDocStatus());
        fillImages(model, QcDocRules.DOC_TYPE_SAMPLE_QC, doc.getId());
        fillAttachments(model, QcDocRules.DOC_TYPE_SAMPLE_QC, doc.getId());
    }

    private void fillOrganoidQc(DocRenderModel model, Sample sample) {
        QcOrganoidDoc doc = organoidDoc(sample.getId());
        if (doc == null) {
            throw new ServiceException("这个样本还没有类器官质控表（先在工作台打开一次质控页）", 400);
        }
        model
            .text("formed_time", doc.getFormedTime())
            .text("growth_state", doc.getGrowthState())
            .text("growth_desc", doc.getGrowthDesc())
            .text("planned_drug_screen", doc.getPlannedDrugScreen())
            .text("feedback_time", doc.getFeedbackTime())
            .part("doc_status=" + doc.getDocStatus());
        fillImages(model, QcDocRules.DOC_TYPE_ORGANOID_QC, doc.getId());
        fillAttachments(model, QcDocRules.DOC_TYPE_ORGANOID_QC, doc.getId());
    }

    private void fillScore(DocRenderModel model, Sample sample) {
        QcScoreDoc doc = scoreDoc(sample.getId());
        if (doc == null) {
            throw new ServiceException("这个样本还没有类器官质量评分表（先在工作台打开一次质控页）", 400);
        }
        model
            // ★ 分值取**库里落下的快照**（保存时由后端按字典 remark 回填），渲染不再查字典：
            //   文档上印的就是当时算出来的分，字典后来改了也不会让历史文档的数字变。
            .text("sc_pre_culture", score(doc.getPreCultureScore()))
            .text("sc_culture_days", score(doc.getCultureDaysScore()))
            .text("sc_count", score(doc.getOrganoidCountScore()))
            .text("sc_diameter", score(doc.getDiameterScore()))
            .text("sc_total", score(doc.getTotalScore()))
            // 四个档位也进指纹：档位变了（哪怕分值恰好相同）也算内容变了
            .part("level:pre_culture=" + doc.getPreCultureLevel())
            .part("level:culture_days=" + doc.getCultureDaysLevel())
            .part("level:count=" + doc.getOrganoidCountLevel())
            .part("level:diameter=" + doc.getDiameterLevel())
            .part("doc_status=" + doc.getDocStatus());
        fillAttachments(model, QcDocRules.DOC_TYPE_ORGANOID_SCORE, doc.getId());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 图片位 / 附件
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 图片位 → oss_id 序列。★ 用的是 <b>{@code preview_oss_id}</b>（进 Word 的必须是预览图，
     * ≤2000px 的 JPEG —— 一张显微 TIFF 几十 MB，ADR-0005 / ticket §0 口径复述 4）。
     * 解析不出预览图时 {@code preview_oss_id = oss_id}（QC-MODEL-001 的回落口径）。
     */
    private void fillImages(DocRenderModel model, String docType, Long docId) {
        List<DocImage> rows = docImageMapper.selectList(new LambdaQueryWrapper<DocImage>()
            .eq(DocImage::getDocType, docType)
            .eq(DocImage::getDocId, docId)
            .orderByAsc(DocImage::getSlot)
            .orderByAsc(DocImage::getSort)
            .orderByAsc(DocImage::getId));
        for (String slot : QcDocRules.slotsOf(docType)) {
            List<Long> ossIds = new ArrayList<>();
            for (DocImage row : rows) {
                if (slot.equals(row.getSlot())) {
                    ossIds.add(row.getPreviewOssId() != null ? row.getPreviewOssId() : row.getOssId());
                }
            }
            model.imageSlot(slot, ossIds);
        }
    }

    /** 通用附件的 oss_id 序列（不进 Word 正文，但改了必须重出 —— 预览页下方那一栏是它）。 */
    private void fillAttachments(DocRenderModel model, String docType, Long docId) {
        List<DocAttachment> rows = docAttachmentMapper.selectList(new LambdaQueryWrapper<DocAttachment>()
            .eq(DocAttachment::getDocType, docType)
            .eq(DocAttachment::getDocId, docId)
            .orderByAsc(DocAttachment::getSort)
            .orderByAsc(DocAttachment::getId));
        model.attachments(rows.stream().map(DocAttachment::getOssId).toList());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 行取用
    // ══════════════════════════════════════════════════════════════════════

    public QcSampleDoc sampleDoc(Long sampleId) {
        return sampleDocMapper.selectOne(new LambdaQueryWrapper<QcSampleDoc>()
            .eq(QcSampleDoc::getSampleId, sampleId));
    }

    public QcOrganoidDoc organoidDoc(Long sampleId) {
        return organoidDocMapper.selectOne(new LambdaQueryWrapper<QcOrganoidDoc>()
            .eq(QcOrganoidDoc::getSampleId, sampleId));
    }

    public QcScoreDoc scoreDoc(Long sampleId) {
        return scoreDocMapper.selectOne(new LambdaQueryWrapper<QcScoreDoc>()
            .eq(QcScoreDoc::getSampleId, sampleId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 小工具
    // ══════════════════════════════════════════════════════════════════════

    /** 字典标签；查不到回落原值（字典没配好不该让文档出不来）。 */
    private String dictLabel(String dictType, String dictValue) {
        if (StringUtils.isBlank(dictValue)) {
            return "";
        }
        try {
            String label = dictMapper.selectLabel(dictType, dictValue.trim());
            return StringUtils.isBlank(label) ? dictValue : label;
        } catch (Exception e) {
            log.warn("字典 {} 取值 {} 的标签查不到，原样印：{}", dictType, dictValue, e.toString());
            return dictValue;
        }
    }

    private static String blankTo(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private static String dateTime(Date date) {
        return date == null ? "" : DATE_TIME.format(date.toInstant().atZone(java.time.ZoneId.systemDefault()));
    }

    private static String score(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
