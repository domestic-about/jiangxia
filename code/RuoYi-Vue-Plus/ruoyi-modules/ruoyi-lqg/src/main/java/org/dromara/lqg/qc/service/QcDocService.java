package org.dromara.lqg.qc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.domain.bo.DocAttachmentBo;
import org.dromara.lqg.qc.domain.bo.DocImageBo;
import org.dromara.lqg.qc.domain.bo.DocImageSortBo;
import org.dromara.lqg.qc.domain.bo.QcOrganoidSaveBo;
import org.dromara.lqg.qc.domain.bo.QcSampleSaveBo;
import org.dromara.lqg.qc.domain.bo.QcScoreSaveBo;
import org.dromara.lqg.qc.domain.vo.DocAttachmentVo;
import org.dromara.lqg.qc.domain.vo.DocImageVo;
import org.dromara.lqg.qc.domain.vo.QcDocBundleVo;
import org.dromara.lqg.qc.domain.vo.QcOrganoidDocVo;
import org.dromara.lqg.qc.domain.vo.QcSampleDocVo;
import org.dromara.lqg.qc.domain.vo.QcSampleRefVo;
import org.dromara.lqg.qc.domain.vo.QcScoreDocVo;
import org.dromara.lqg.doc.publish.DocPublishService;
import org.dromara.lqg.qc.mapper.DocAttachmentMapper;
import org.dromara.lqg.qc.mapper.DocImageMapper;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.dromara.lqg.sample.service.SampleFieldCipher;
import org.dromara.lqg.sample.verify.VerifyTransitions;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 三份质控文档的内部读写（FLOW:F-QC-01.step1 ~ step4，doc/api-contract.md 的 QC 一节）。
 *
 * <p>★ <b>一个样本一份</b>：{@code ensureXxxDoc} 对「缺哪份建哪份空草稿」幂等，
 * 并发首次打开时靠 {@code sample_id} 上的<b>部分唯一索引</b>
 * （{@code uk_qc_sample_sample / uk_qc_organoid_sample / uk_qc_score_sample}
 * {@code WHERE del_flag='0'}）兜底：撞唯一键的那一次抓住 {@code DataIntegrityViolationException}
 * 回头再查一遍。这就是 accept 1 第 1 段（ddl_vs_ssot 逐条断那个 WHERE）与 accept 3 第 3 段
 * （连开两次仍是 1|1|1）要的东西。
 *
 * <p>★ <b>本方法刻意不加 {@code @Transactional}</b>：一旦撞唯一键，PostgreSQL 会把整个事务
 * 标成 aborted，再查一遍也只会拿到 {@code current transaction is aborted}
 * —— 「靠唯一索引兜底」就变成一句空话。不裹事务时每条语句各自提交，
 * 抓住冲突后可以干净地重查。
 *
 * <p>★ <b>所有校验在任何写操作之前</b>：样本必须已核验有效、图片位必须属于该文档类型、
 * 每位至多 3 张 —— 被拒时库里不变（accept 3 第 5~7 段）。
 *
 * <p>★ <b>{@code doc_status} 的口径（DOC-PUBLISH-001 收口 issue #228）</b>：
 * 新建是 {@code draft}；「完成并同步 / 撤回」两条显式转移在
 * {@link DocPublishService}；而<b>任何内容改动</b>（三个 PUT + 图片 / 附件的增删排序）
 * 都会把一份 {@code published} 的文档打回 {@code draft} 并清掉完成人 / 完成时间
 * （{@link #afterChange}，FLOW:F-QC-01.step7）。
 * 这采的是 {@code V202609261300} DDL 注释那一侧（「published 后再保存内容 → 回到 draft」），
 * 不是 QC-MODEL-001 类注释里「本票一个字都不动」那句 —— 后者只说明那张票的范围。
 *
 * @author QC-MODEL-001 · DOC-PUBLISH-001（内容改动回 draft 的钩子）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcDocService {

    private final QcSampleDocMapper sampleDocMapper;
    private final QcOrganoidDocMapper organoidDocMapper;
    private final QcScoreDocMapper scoreDocMapper;
    private final DocImageMapper docImageMapper;
    private final DocAttachmentMapper docAttachmentMapper;
    private final SysOssMapper sysOssMapper;
    private final SampleMapper sampleMapper;
    private final QcScoreDictionary scoreDictionary;
    private final QcImagePreviewResolver previewResolver;
    private final SampleFieldCipher fieldCipher;
    /** 图片 / 附件的访问地址：私有桶签临时链接，公有桶原样（Kevin 本机验收「网页工作台」第 3 行） */
    private final QcOssUrls ossUrlSigner;

    /**
     * ★★ <b>发布状态机</b>（DOC-PUBLISH-001）：本类每个写接口保存成功后调
     * {@link DocPublishService#onContentChanged} —— 已完成的文档一旦内容被改就自动回到草稿
     * （FLOW:F-QC-01.step7）。
     *
     * <p>为什么用「字段 + setter / 字段注入」而不是构造器注入：{@link DocPublishService} 要读三张质控表，
     * 而本类是写这三张表的人，构造器互相注入会成环。setter 注入把环断开
     * （Spring 先构造再回填），依赖方向仍是 qc → doc.publish 一条直线。
     *
     * @see #afterChange(Long, String)
     */
    @Setter
    @Autowired
    private DocPublishService docPublishService;

    // ══════════════════════════════════════════════════════════════════════
    // 读：GET /lqg/qc/{sampleId}
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 三份文档 + 图片 + 附件 + 样本主档只读字段。首次访问就地建齐三份空草稿。
     */
    public QcDocBundleVo bundle(Long sampleId) {
        // ★ 先校验样本（不是有效样本 → 400 且什么都不建）
        Sample sample = requireValidSample(sampleId);
        return DataPermissionHelper.ignore(() -> {
            QcSampleDoc sampleDoc = ensureSampleDoc(sampleId);
            QcOrganoidDoc organoidDoc = ensureOrganoidDoc(sampleId);
            QcScoreDoc scoreDoc = ensureScoreDoc(sampleId);

            QcDocBundleVo vo = new QcDocBundleVo();
            vo.setSample(toSampleRef(sample));
            vo.setSampleQc(toSampleQcVo(sampleDoc));
            vo.setOrganoidQc(toOrganoidQcVo(organoidDoc));
            vo.setScore(toScoreVo(scoreDoc));
            return vo;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 写：PUT …/sample-qc、…/organoid-qc、…/score
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 保存样本质控表（<b>补丁语义</b>：{@code null} = 这一栏不动，空串 = 清空）。
     */
    public void saveSampleQc(Long sampleId, QcSampleSaveBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        requireValidSample(sampleId);
        Long userId = currentUserId();
        DataPermissionHelper.ignore(() -> {
            QcSampleDoc doc = ensureSampleDoc(sampleId);
            LambdaUpdateWrapper<QcSampleDoc> patch = new LambdaUpdateWrapper<QcSampleDoc>()
                .eq(QcSampleDoc::getId, doc.getId())
                // update(null, wrapper) 不会自动填 update_by / update_time，必须显式补
                .set(QcSampleDoc::getUpdateBy, userId)
                .set(QcSampleDoc::getUpdateTime, new Date());
            // ★ 患者编号：明文进、密文落库（ADR-0006）。空串 = 清空（存 NULL）。
            if (bo.getPatientNo() != null) {
                String plain = bo.getPatientNo().trim();
                patch.set(QcSampleDoc::getPatientNo, plain.isEmpty() ? null : fieldCipher.encrypt(plain));
            }
            if (bo.getSamplingSite() != null) {
                patch.set(QcSampleDoc::getSamplingSite, trimToNull(bo.getSamplingSite()));
            }
            if (bo.getSamplingMethod() != null) {
                patch.set(QcSampleDoc::getSamplingMethod, trimToNull(bo.getSamplingMethod()));
            }
            if (bo.getClinicalDiagnosis() != null) {
                patch.set(QcSampleDoc::getClinicalDiagnosis, trimToNull(bo.getClinicalDiagnosis()));
            }
            if (bo.getReceiveDesc() != null) {
                patch.set(QcSampleDoc::getReceiveDesc, trimToNull(bo.getReceiveDesc()));
            }
            if (bo.getOrigDesc() != null) {
                patch.set(QcSampleDoc::getOrigDesc, trimToNull(bo.getOrigDesc()));
            }
            if (bo.getObserveDesc() != null) {
                patch.set(QcSampleDoc::getObserveDesc, trimToNull(bo.getObserveDesc()));
            }
            if (bo.getPretreatDesc() != null) {
                patch.set(QcSampleDoc::getPretreatDesc, trimToNull(bo.getPretreatDesc()));
            }
            // ★ 细胞活率测定附件：null = 不动，0 = 摘掉（这一格在 Word 里嵌入附件本身、显示图标 + 文件名，不走通用附件）
            if (bo.getViabilityOssId() != null) {
                if (bo.getViabilityOssId() == 0L) {
                    patch.set(QcSampleDoc::getViabilityOssId, null)
                        .set(QcSampleDoc::getViabilityFileName, null);
                } else {
                    patch.set(QcSampleDoc::getViabilityOssId, bo.getViabilityOssId())
                        .set(QcSampleDoc::getViabilityFileName, trimToNull(bo.getViabilityFileName()));
                }
            }
            sampleDocMapper.update(null, patch);
            // ★★ 内容改了 → 已完成的文档自动回到草稿（FLOW:F-QC-01.step7；ticket §2 的钩子）
            afterChange(sampleId, QcDocRules.DOC_TYPE_PATH_SAMPLE_QC);
            log.info("保存样本质控表：sampleId={} docId={} operator={}", sampleId, doc.getId(), userId);
        });
    }

    /**
     * 保存类器官质控表（补丁语义；五栏自由文本，不做日期格式校验）。
     */
    public void saveOrganoidQc(Long sampleId, QcOrganoidSaveBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        requireValidSample(sampleId);
        Long userId = currentUserId();
        DataPermissionHelper.ignore(() -> {
            QcOrganoidDoc doc = ensureOrganoidDoc(sampleId);
            LambdaUpdateWrapper<QcOrganoidDoc> patch = new LambdaUpdateWrapper<QcOrganoidDoc>()
                .eq(QcOrganoidDoc::getId, doc.getId())
                .set(QcOrganoidDoc::getUpdateBy, userId)
                .set(QcOrganoidDoc::getUpdateTime, new Date());
            if (bo.getFormedTime() != null) {
                patch.set(QcOrganoidDoc::getFormedTime, trimToNull(bo.getFormedTime()));
            }
            if (bo.getGrowthState() != null) {
                patch.set(QcOrganoidDoc::getGrowthState, trimToNull(bo.getGrowthState()));
            }
            if (bo.getGrowthDesc() != null) {
                patch.set(QcOrganoidDoc::getGrowthDesc, trimToNull(bo.getGrowthDesc()));
            }
            if (bo.getPlannedDrugScreen() != null) {
                patch.set(QcOrganoidDoc::getPlannedDrugScreen, trimToNull(bo.getPlannedDrugScreen()));
            }
            if (bo.getFeedbackTime() != null) {
                patch.set(QcOrganoidDoc::getFeedbackTime, trimToNull(bo.getFeedbackTime()));
            }
            organoidDocMapper.update(null, patch);
            // ★★ 同上：改类器官质控表的内容 → 这一份回草稿
            afterChange(sampleId, QcDocRules.DOC_TYPE_PATH_ORGANOID_QC);
            log.info("保存类器官质控表：sampleId={} docId={} operator={}", sampleId, doc.getId(), userId);
        });
    }

    /**
     * 保存类器官质量评分表。
     *
     * <p>★★ <b>只认四个档位，分值一律由后端按字典 remark 回填</b>：
     * 请求体里夹带的 {@code *Score} 连字段都没有（{@code QcScoreSaveBo}）。
     * 四个都选了才写合计，缺一个就写 NULL。
     */
    public void saveScore(Long sampleId, QcScoreSaveBo bo) {
        if (bo == null) {
            throw new ServiceException("请求体不能为空", 400);
        }
        requireValidSample(sampleId);
        // ★ 先把四个档位换算成分值（含「非法档位 → 400」），再做任何写操作
        QcScoreSnapshot snapshot = scoreDictionary.resolve(bo);
        Long userId = currentUserId();
        DataPermissionHelper.ignore(() -> {
            QcScoreDoc doc = ensureScoreDoc(sampleId);
            LambdaUpdateWrapper<QcScoreDoc> patch = new LambdaUpdateWrapper<QcScoreDoc>()
                .eq(QcScoreDoc::getId, doc.getId())
                .set(QcScoreDoc::getUpdateBy, userId)
                .set(QcScoreDoc::getUpdateTime, new Date())
                .set(QcScoreDoc::getPreCultureLevel, trimToNull(bo.getPreCultureLevel()))
                .set(QcScoreDoc::getCultureDaysLevel, trimToNull(bo.getCultureDaysLevel()))
                .set(QcScoreDoc::getOrganoidCountLevel, trimToNull(bo.getOrganoidCountLevel()))
                .set(QcScoreDoc::getDiameterLevel, trimToNull(bo.getDiameterLevel()))
                // LambdaUpdateWrapper.set 会把 null 一起写进去 —— 「没选 → NULL」正是要的
                .set(QcScoreDoc::getPreCultureScore, snapshot.getPreCultureScore())
                .set(QcScoreDoc::getCultureDaysScore, snapshot.getCultureDaysScore())
                .set(QcScoreDoc::getOrganoidCountScore, snapshot.getOrganoidCountScore())
                .set(QcScoreDoc::getDiameterScore, snapshot.getDiameterScore())
                .set(QcScoreDoc::getTotalScore, snapshot.getTotalScore());
            scoreDocMapper.update(null, patch);
            // ★★ 改评分档位 → 已完成的评分表回草稿（accept 1 第 7 段）
            afterChange(sampleId, QcDocRules.DOC_TYPE_PATH_SCORE);
            log.info("保存评分表：sampleId={} docId={} levels={}/{}/{}/{} scores={}/{}/{}/{} total={} operator={}",
                sampleId, doc.getId(), bo.getPreCultureLevel(), bo.getCultureDaysLevel(),
                bo.getOrganoidCountLevel(), bo.getDiameterLevel(), snapshot.getPreCultureScore(),
                snapshot.getCultureDaysScore(), snapshot.getOrganoidCountScore(),
                snapshot.getDiameterScore(), snapshot.getTotalScore(), userId);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 图片位：POST / DELETE / PUT sort
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 往图片位加一张图（归属与张数上限先校验，被拒时库里不变）。
     *
     * @return 新图片位记录 id
     */
    public Long addImage(Long sampleId, String docTypePath, DocImageBo bo) {
        requireValidSample(sampleId);
        String docType = QcDocRules.requireDocType(docTypePath);
        String slot = QcDocRules.requireSlotOf(docType, bo == null ? null : bo.getSlot());
        if (bo == null || bo.getOssId() == null) {
            throw new ServiceException("缺少图片 oss_id", 400);
        }
        return DataPermissionHelper.ignore(() -> {
            Long docId = docIdOf(sampleId, docType);
            List<DocImage> existing = listImages(docType, docId, slot);
            if (existing.size() >= QcDocRules.MAX_IMAGES_PER_SLOT) {
                throw new ServiceException("图片位 " + slot + " 已有 " + existing.size() + " 张，最多 "
                    + QcDocRules.MAX_IMAGES_PER_SLOT + " 张", 400);
            }
            int nextSort = 1;
            for (DocImage row : existing) {
                nextSort = Math.max(nextSort, (row.getSort() == null ? 0 : row.getSort()) + 1);
            }
            DocImage entity = new DocImage();
            entity.setDocType(docType);
            entity.setDocId(docId);
            entity.setSlot(slot);
            entity.setOssId(bo.getOssId());
            // 预览图：非 jpg / png 或长边 > 2000px 才另存，否则就等于原图
            entity.setPreviewOssId(previewResolver.resolvePreviewOssId(bo.getOssId()));
            entity.setSort(nextSort);
            docImageMapper.insert(entity);
            log.info("图片位新增：sampleId={} docType={} docId={} slot={} ossId={} preview={}",
                sampleId, docType, docId, slot, bo.getOssId(), entity.getPreviewOssId());
            // ★★ 增删图片也是内容改动（accept 1 最后一段：加一张图 → 回草稿）
            afterChange(sampleId, docType);
            return entity.getId();
        });
    }

    /**
     * 软删一张图（只能删本样本、本份文档、本位里的图）。
     */
    public void removeImage(Long sampleId, String docTypePath, Long imageId) {
        requireValidSample(sampleId);
        String docType = QcDocRules.requireDocType(docTypePath);
        if (imageId == null) {
            throw new ServiceException("缺少图片 id", 400);
        }
        DataPermissionHelper.ignore(() -> {
            Long docId = docIdOf(sampleId, docType);
            DocImage row = docImageMapper.selectOne(new LambdaQueryWrapper<DocImage>()
                .eq(DocImage::getId, imageId)
                .eq(DocImage::getDocType, docType)
                .eq(DocImage::getDocId, docId));
            if (row == null) {
                throw new ServiceException("图片不存在（或不属于这个样本的这份文档）", 400);
            }
            // @TableLogic：deleteById 是软删，与 accept 3 断言里的 del_flag='0' 一致
            docImageMapper.deleteById(row.getId());
            log.info("图片位删除：sampleId={} docType={} docId={} imageId={}", sampleId, docType, docId, imageId);
            afterChange(sampleId, docType);
        });
    }

    /**
     * 重排某个图片位内的顺序：{@code ids} 必须是这个位里<b>全部</b>图的 id（按目标顺序）。
     */
    public void sortImages(Long sampleId, String docTypePath, DocImageSortBo bo) {
        requireValidSample(sampleId);
        String docType = QcDocRules.requireDocType(docTypePath);
        List<Long> ids = bo == null ? null : bo.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("缺少要排序的图片 id 列表", 400);
        }
        DataPermissionHelper.ignore(() -> {
            Long docId = docIdOf(sampleId, docType);
            List<DocImage> rows = docImageMapper.selectList(new LambdaQueryWrapper<DocImage>()
                .in(DocImage::getId, ids)
                .eq(DocImage::getDocType, docType)
                .eq(DocImage::getDocId, docId));
            if (rows.size() != new LinkedHashSet<>(ids).size()) {
                throw new ServiceException("排序列表里有不存在 / 不属于这份文档的图片", 400);
            }
            Set<String> slots = new HashSet<>();
            for (DocImage row : rows) {
                slots.add(row.getSlot());
            }
            if (slots.size() != 1) {
                throw new ServiceException("一次只能排一个图片位内的顺序", 400);
            }
            String slot = slots.iterator().next();
            long total = listImages(docType, docId, slot).size();
            if (total != rows.size()) {
                throw new ServiceException("排序要给出该图片位里全部 " + total + " 张图的 id", 400);
            }
            Long userId = currentUserId();
            int index = 1;
            for (Long id : ids) {
                docImageMapper.update(null, new LambdaUpdateWrapper<DocImage>()
                    .eq(DocImage::getId, id)
                    .set(DocImage::getSort, index)
                    .set(DocImage::getUpdateBy, userId)
                    .set(DocImage::getUpdateTime, new Date()));
                index++;
            }
            log.info("图片位排序：sampleId={} docType={} docId={} slot={} ids={}", sampleId, docType, docId, slot, ids);
            // 重排也是内容改动（文档里图的顺序变了 → 指纹变 → 已完成的文档回草稿）
            afterChange(sampleId, docType);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 通用附件：POST / DELETE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 挂一个通用附件（三份文档都能挂；单个 ≤ 50MB）。
     *
     * <p>★ {@code fileSize} 没传时回落到 {@code sys_oss.ext1} 里的 {@code fileSize}
     * （工作台上传时由 {@code SysOssServiceImpl} 写进去）；两边都没有才落 0。
     *
     * @return 新附件记录 id
     */
    public Long addAttachment(Long sampleId, String docTypePath, DocAttachmentBo bo) {
        requireValidSample(sampleId);
        String docType = QcDocRules.requireDocType(docTypePath);
        if (bo == null || bo.getOssId() == null) {
            throw new ServiceException("缺少附件 oss_id", 400);
        }
        return DataPermissionHelper.ignore(() -> {
            Long docId = docIdOf(sampleId, docType);
            SysOss oss = sysOssMapper.selectById(bo.getOssId());
            String fileName = trimToNull(bo.getFileName());
            if (fileName == null && oss != null) {
                fileName = trimToNull(oss.getOriginalName());
            }
            if (fileName == null) {
                throw new ServiceException("缺少附件文件名", 400);
            }
            Integer fileSize = bo.getFileSize() != null ? bo.getFileSize() : sizeOf(oss);
            if (fileSize != null && fileSize > QcDocRules.MAX_ATTACHMENT_BYTES) {
                throw new ServiceException("单个附件不能超过 50MB（当前：" + fileSize + " 字节）", 400);
            }
            int nextSort = 1;
            for (DocAttachment row : listAttachments(docType, docId)) {
                nextSort = Math.max(nextSort, (row.getSort() == null ? 0 : row.getSort()) + 1);
            }
            DocAttachment entity = new DocAttachment();
            entity.setDocType(docType);
            entity.setDocId(docId);
            entity.setOssId(bo.getOssId());
            entity.setFileName(fileName);
            entity.setFileSize(fileSize == null ? 0 : fileSize);
            entity.setSort(nextSort);
            docAttachmentMapper.insert(entity);
            log.info("附件新增：sampleId={} docType={} docId={} ossId={} fileName={} fileSize={}",
                sampleId, docType, docId, bo.getOssId(), fileName, entity.getFileSize());
            // ★★ 附件增删同样算内容改动（ticket §0 口径复述 1 明写「含增删图片、附件」）
            afterChange(sampleId, docType);
            return entity.getId();
        });
    }

    /**
     * 软删一个通用附件（只能是本样本、本份文档里的）。
     */
    public void removeAttachment(Long sampleId, String docTypePath, Long attachmentId) {
        requireValidSample(sampleId);
        String docType = QcDocRules.requireDocType(docTypePath);
        if (attachmentId == null) {
            throw new ServiceException("缺少附件 id", 400);
        }
        DataPermissionHelper.ignore(() -> {
            Long docId = docIdOf(sampleId, docType);
            DocAttachment row = docAttachmentMapper.selectOne(new LambdaQueryWrapper<DocAttachment>()
                .eq(DocAttachment::getId, attachmentId)
                .eq(DocAttachment::getDocType, docType)
                .eq(DocAttachment::getDocId, docId));
            if (row == null) {
                throw new ServiceException("附件不存在（或不属于这个样本的这份文档）", 400);
            }
            docAttachmentMapper.deleteById(row.getId());
            log.info("附件删除：sampleId={} docType={} docId={} attachmentId={}", sampleId, docType, docId, attachmentId);
            afterChange(sampleId, docType);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 草稿的幂等建立（缺哪份建哪份）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 样本质控表：没有就建一份空草稿（三段模板原文逐字预填），有就返回已有的。
     */
    QcSampleDoc ensureSampleDoc(Long sampleId) {
        QcSampleDoc exists = sampleDocMapper.selectOne(
            new LambdaQueryWrapper<QcSampleDoc>().eq(QcSampleDoc::getSampleId, sampleId));
        if (exists != null) {
            return exists;
        }
        QcSampleDoc entity = new QcSampleDoc();
        entity.setSampleId(sampleId);
        entity.setReceiveDesc(QcDocRules.RECEIVE_DESC_DEFAULT);
        entity.setObserveDesc(QcDocRules.OBSERVE_DESC_DEFAULT);
        entity.setPretreatDesc(QcDocRules.PRETREAT_DESC_DEFAULT);
        entity.setDocStatus(QcDocRules.STATUS_DRAFT);
        return insertOrReload(entity, () -> sampleDocMapper.insert(entity),
            () -> sampleDocMapper.selectOne(new LambdaQueryWrapper<QcSampleDoc>()
                .eq(QcSampleDoc::getSampleId, sampleId)));
    }

    /**
     * 类器官质控表：没有就建一份空草稿。
     */
    QcOrganoidDoc ensureOrganoidDoc(Long sampleId) {
        QcOrganoidDoc exists = organoidDocMapper.selectOne(
            new LambdaQueryWrapper<QcOrganoidDoc>().eq(QcOrganoidDoc::getSampleId, sampleId));
        if (exists != null) {
            return exists;
        }
        QcOrganoidDoc entity = new QcOrganoidDoc();
        entity.setSampleId(sampleId);
        entity.setDocStatus(QcDocRules.STATUS_DRAFT);
        return insertOrReload(entity, () -> organoidDocMapper.insert(entity),
            () -> organoidDocMapper.selectOne(new LambdaQueryWrapper<QcOrganoidDoc>()
                .eq(QcOrganoidDoc::getSampleId, sampleId)));
    }

    /**
     * 评分表：没有就建一份空草稿（四个档位全空、合计为空 —— 不是 0）。
     */
    QcScoreDoc ensureScoreDoc(Long sampleId) {
        QcScoreDoc exists = scoreDocMapper.selectOne(
            new LambdaQueryWrapper<QcScoreDoc>().eq(QcScoreDoc::getSampleId, sampleId));
        if (exists != null) {
            return exists;
        }
        QcScoreDoc entity = new QcScoreDoc();
        entity.setSampleId(sampleId);
        entity.setDocStatus(QcDocRules.STATUS_DRAFT);
        return insertOrReload(entity, () -> scoreDocMapper.insert(entity),
            () -> scoreDocMapper.selectOne(new LambdaQueryWrapper<QcScoreDoc>()
                .eq(QcScoreDoc::getSampleId, sampleId)));
    }

    /**
     * 插入；撞部分唯一索引（并发首登的第二个人）就回头把对方建好的那份查出来。
     */
    private static <T> T insertOrReload(T entity, Runnable insert, java.util.function.Supplier<T> reload) {
        try {
            insert.run();
            return entity;
        } catch (DataIntegrityViolationException e) {
            T other = reload.get();
            if (other == null) {
                throw e;
            }
            return other;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VO 组装
    // ══════════════════════════════════════════════════════════════════════

    private QcSampleRefVo toSampleRef(Sample sample) {
        QcSampleRefVo vo = new QcSampleRefVo();
        vo.setId(sample.getId());
        vo.setSubmitNo(sample.getSubmitNo());
        vo.setSampleKind(sample.getSampleKind());
        vo.setInternalNo(sample.getInternalNo());
        vo.setSourceUnitName(sample.getSourceUnitName());
        // 供体姓名是加密列：读出来解密再给工作台（ADR-0006，内部看明文）
        vo.setDonorName(fieldCipher.decrypt(sample.getDonorName()));
        vo.setGender(sample.getGender());
        vo.setReceiveDate(sample.getReceiveDate());
        vo.setProcessTime(sample.getProcessTime());
        vo.setOperatorName(sample.getOperatorName());
        return vo;
    }

    private QcSampleDocVo toSampleQcVo(QcSampleDoc doc) {
        QcSampleDocVo vo = new QcSampleDocVo();
        vo.setId(doc.getId());
        vo.setSampleId(doc.getSampleId());
        vo.setPatientNo(fieldCipher.decrypt(doc.getPatientNo()));
        vo.setSamplingSite(doc.getSamplingSite());
        vo.setSamplingMethod(doc.getSamplingMethod());
        vo.setClinicalDiagnosis(doc.getClinicalDiagnosis());
        vo.setReceiveDesc(doc.getReceiveDesc());
        vo.setViabilityOssId(doc.getViabilityOssId());
        vo.setViabilityFileName(doc.getViabilityFileName());
        vo.setOrigDesc(doc.getOrigDesc());
        vo.setObserveDesc(doc.getObserveDesc());
        vo.setPretreatDesc(doc.getPretreatDesc());
        vo.setDocStatus(doc.getDocStatus());
        vo.setPublishedTime(doc.getPublishedTime());
        vo.setPublishedBy(doc.getPublishedBy());
        vo.setImages(imagesOf(QcDocRules.DOC_TYPE_SAMPLE_QC, doc.getId()));
        vo.setAttachments(attachmentVos(QcDocRules.DOC_TYPE_SAMPLE_QC, doc.getId()));
        return vo;
    }

    private QcOrganoidDocVo toOrganoidQcVo(QcOrganoidDoc doc) {
        QcOrganoidDocVo vo = new QcOrganoidDocVo();
        vo.setId(doc.getId());
        vo.setSampleId(doc.getSampleId());
        vo.setFormedTime(doc.getFormedTime());
        vo.setGrowthState(doc.getGrowthState());
        vo.setGrowthDesc(doc.getGrowthDesc());
        vo.setPlannedDrugScreen(doc.getPlannedDrugScreen());
        vo.setFeedbackTime(doc.getFeedbackTime());
        vo.setDocStatus(doc.getDocStatus());
        vo.setPublishedTime(doc.getPublishedTime());
        vo.setPublishedBy(doc.getPublishedBy());
        vo.setImages(imagesOf(QcDocRules.DOC_TYPE_ORGANOID_QC, doc.getId()));
        vo.setAttachments(attachmentVos(QcDocRules.DOC_TYPE_ORGANOID_QC, doc.getId()));
        return vo;
    }

    private QcScoreDocVo toScoreVo(QcScoreDoc doc) {
        QcScoreDocVo vo = new QcScoreDocVo();
        vo.setId(doc.getId());
        vo.setSampleId(doc.getSampleId());
        vo.setPreCultureLevel(doc.getPreCultureLevel());
        vo.setCultureDaysLevel(doc.getCultureDaysLevel());
        vo.setOrganoidCountLevel(doc.getOrganoidCountLevel());
        vo.setDiameterLevel(doc.getDiameterLevel());
        vo.setPreCultureScore(doc.getPreCultureScore());
        vo.setCultureDaysScore(doc.getCultureDaysScore());
        vo.setOrganoidCountScore(doc.getOrganoidCountScore());
        vo.setDiameterScore(doc.getDiameterScore());
        vo.setTotalScore(doc.getTotalScore());
        vo.setDocStatus(doc.getDocStatus());
        vo.setPublishedTime(doc.getPublishedTime());
        vo.setPublishedBy(doc.getPublishedBy());
        // ★ 评分表没有图片位：空 map（不是 null），页面票不用处理 null
        vo.setImages(new LinkedHashMap<>());
        vo.setAttachments(attachmentVos(QcDocRules.DOC_TYPE_ORGANOID_SCORE, doc.getId()));
        return vo;
    }

    /**
     * 图片按 slot 分组：<b>该文档类型的每个位都在 map 里</b>（没有图的是空列表），
     * 组内按 {@code sort} 升序。
     */
    private Map<String, List<DocImageVo>> imagesOf(String docType, Long docId) {
        List<DocImage> rows = docImageMapper.selectList(new LambdaQueryWrapper<DocImage>()
            .eq(DocImage::getDocType, docType)
            .eq(DocImage::getDocId, docId)
            .orderByAsc(DocImage::getSlot)
            .orderByAsc(DocImage::getSort)
            .orderByAsc(DocImage::getId));
        Map<Long, String> urls = ossUrls(rows.stream().flatMap(r -> {
            List<Long> ids = new ArrayList<>();
            ids.add(r.getOssId());
            ids.add(r.getPreviewOssId());
            return ids.stream();
        }).toList());
        Map<String, List<DocImageVo>> grouped = new LinkedHashMap<>();
        for (String slot : QcDocRules.slotsOf(docType)) {
            grouped.put(slot, new ArrayList<>());
        }
        for (DocImage row : rows) {
            DocImageVo vo = new DocImageVo();
            vo.setId(row.getId());
            vo.setSlot(row.getSlot());
            vo.setOssId(row.getOssId());
            vo.setPreviewOssId(row.getPreviewOssId());
            vo.setSort(row.getSort());
            vo.setUrl(urls.get(row.getOssId()));
            vo.setPreviewUrl(urls.get(row.getPreviewOssId()));
            grouped.computeIfAbsent(row.getSlot(), k -> new ArrayList<>()).add(vo);
        }
        return grouped;
    }

    private List<DocAttachmentVo> attachmentVos(String docType, Long docId) {
        List<DocAttachment> rows = listAttachments(docType, docId);
        Map<Long, String> urls = ossUrls(rows.stream().map(DocAttachment::getOssId).toList());
        List<DocAttachmentVo> out = new ArrayList<>();
        for (DocAttachment row : rows) {
            DocAttachmentVo vo = new DocAttachmentVo();
            vo.setId(row.getId());
            vo.setOssId(row.getOssId());
            vo.setFileName(row.getFileName());
            vo.setFileSize(row.getFileSize());
            vo.setSort(row.getSort());
            vo.setUrl(urls.get(row.getOssId()));
            out.add(vo);
        }
        return out;
    }

    /**
     * 读时带出 oss 的访问地址（查不到 → null，不抛）。
     *
     * <p>★ 不能原样给 {@code sys_oss.url}：私有桶上那是 403 的直链（缩略图、放大图、附件全打不开）。
     * 私有桶签 10 分钟临时链接、公有桶照旧，口径见 {@link QcOssUrls}。
     * （包可见：{@code QcDocServiceUrlTest} 钉「读路径真的走签名」）
     */
    Map<Long, String> ossUrls(List<Long> ossIds) {
        List<Long> ids = ossIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return ossUrlSigner.urlsOf(sysOssMapper.selectByIds(ids));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 小工具
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 解析样本：必须存在且<b>已核验有效</b>（待核验 / 无效 / 已软删一律 400，
     * accept 3 第 4 段断的就是这一格）。
     */
    private Sample requireValidSample(Long sampleId) {
        if (sampleId == null) {
            throw new ServiceException("缺少样本 id", 400);
        }
        Sample sample = sampleMapper.selectById(sampleId);
        if (sample == null) {
            throw new ServiceException("样本不存在", 400);
        }
        if (!VerifyTransitions.VALID.equals(sample.getVerifyStatus())) {
            throw new ServiceException("质控文档只能对已核验有效的样本打开（当前状态："
                + sample.getVerifyStatus() + "）", 400);
        }
        return sample;
    }

    /** 该文档类型对应的文档行 id（没有就地建草稿）。 */
    private Long docIdOf(Long sampleId, String docType) {
        return switch (docType) {
            case QcDocRules.DOC_TYPE_SAMPLE_QC -> ensureSampleDoc(sampleId).getId();
            case QcDocRules.DOC_TYPE_ORGANOID_QC -> ensureOrganoidDoc(sampleId).getId();
            case QcDocRules.DOC_TYPE_ORGANOID_SCORE -> ensureScoreDoc(sampleId).getId();
            default -> throw new ServiceException("不认识的文档类型：" + docType, 400);
        };
    }

    private List<DocImage> listImages(String docType, Long docId, String slot) {
        return docImageMapper.selectList(new LambdaQueryWrapper<DocImage>()
            .eq(DocImage::getDocType, docType)
            .eq(DocImage::getDocId, docId)
            .eq(DocImage::getSlot, slot)
            .orderByAsc(DocImage::getSort)
            .orderByAsc(DocImage::getId));
    }

    private List<DocAttachment> listAttachments(String docType, Long docId) {
        return docAttachmentMapper.selectList(new LambdaQueryWrapper<DocAttachment>()
            .eq(DocAttachment::getDocType, docType)
            .eq(DocAttachment::getDocId, docId)
            .orderByAsc(DocAttachment::getSort)
            .orderByAsc(DocAttachment::getId));
    }

    /** {@code sys_oss.ext1} 里由上传侧写下的字节数（没有 → null）。 */
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

    /**
     * ★★ 内容写成功后的统一收口：文档若已完成（published）→ 自动回到草稿（清完成人 / 完成时间）。
     *
     * <p><b>只读不改</b>：文档本来就是草稿时 {@link DocPublishService#onContentChanged} 什么都不做，
     * 所以「第一次打开质控页建三份空草稿」这类读路径不会被误算成内容改动。
     *
     * <p><b>为什么放在 service 而不是 controller</b>（ticket §2 原话「在 qc 包的 service 里接这个钩子，
     * 别靠 controller 记得调」）：本类的每个写方法自己收口 —— 将来多一个写入口（或别的控制器
     * 复用本类的方法）也不会漏。controller 层的 {@code DocPublishController} 只管显式的
     * 「完成并同步 / 撤回」。
     */
    private void afterChange(Long sampleId, String docType) {
        if (docPublishService == null) {
            // 单测里手工 new 本类时没有 Spring 上下文：状态机缺席不该让写接口挂掉
            log.warn("DocPublishService 未注入（单测？），跳过内容改动后的状态收口：sampleId={} docType={}",
                sampleId, docType);
            return;
        }
        docPublishService.onContentChanged(sampleId, docType);
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static Long currentUserId() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

}
