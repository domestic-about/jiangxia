package org.dromara.lqg.doc.render.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.DocOssBytes;
import org.dromara.lqg.doc.render.DocRenderModel;
import org.dromara.lqg.doc.render.DocRenderModelFactory;
import org.dromara.lqg.doc.render.DocTemplate;
import org.dromara.lqg.doc.render.DocxRenderer;
import org.dromara.lqg.doc.render.domain.DocFile;
import org.dromara.lqg.doc.render.domain.vo.DocDownloadVo;
import org.dromara.lqg.doc.render.domain.vo.DocRenderVo;
import org.dromara.lqg.doc.render.mapper.DocFileMapper;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Word 渲染的编排（FLOW:F-DOC-01.step1 / step2，ADR-0005）。
 *
 * <p>★★ <b>本票的核心是这张缓存表，不是渲染本身</b>：
 *
 * <pre>
 *   算指纹 → 已有 done 行且指纹一致 → 直接返回（**一个字节都不写库**，rendered_time/oss_id 原样）
 *          → 否则置 pending → 渲染 docx → 传 OSS（私有）→ done
 *          → 渲染抛异常 → failed + error_msg（不把异常甩成 500，DOC-PDF-001 要看得见、要能重试）
 * </pre>
 *
 * <p>★ 「内容没变不重出」这条之所以是**零写库**而不是「写回同样的值」：accept 2 的快照
 * 带着 {@code rendered_time}，任何一次无谓的 UPDATE 都会让 {@code S1 != S2}。
 *
 * <p>★ <b>audience 在缓存键里</b>（部分唯一索引 {@code uk_doc_file} 带上它）：内外部各一行，
 * 谁后渲染都不会覆盖谁。
 *
 * <p>★ 只写 {@code file_format='docx'} / {@code page_no=0} 的行；pdf / 每页 png 是 DOC-PDF-001
 * 的事，它们在同一张表上另起行（键里有 format 与 page_no）。
 *
 * @author DOC-RENDER-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocRenderService {

    /** 本票只产 Word。 */
    public static final String FORMAT_DOCX = "docx";

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_FAILED = "failed";

    private static final String DOCX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** 签名链接有效期（ticket §2：10 分钟）。 */
    public static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);

    private static final int ERROR_MSG_MAX = 480;

    private final DocRenderModelFactory modelFactory;
    private final DocxRenderer renderer;
    private final DocFileMapper docFileMapper;
    private final SysOssMapper sysOssMapper;

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
            DocFile row = findRow(sampleId, kind, aud);
            if (isHit(row, hash)) {
                log.info("渲染命中缓存：sampleId={} docKind={} audience={} hash={}", sampleId, kind, aud, hash);
                DocRenderVo vo = toVo(row);
                vo.setCached(true);
                return vo;
            }

            // ── 3. 置 pending ────────────────────────────────────────────
            DocFile pending = upsertPending(row, sampleId, kind, aud, hash, version);

            // ── 4. 渲染 → 传 OSS → done；任何异常都是 failed + 原因 ───────
            try {
                byte[] bytes;
                if (members == null) {
                    bytes = renderer.render(model);
                } else {
                    List<byte[]> parts = new ArrayList<>();
                    for (DocRenderModel member : members) {
                        parts.add(renderer.render(member));
                    }
                    bytes = renderer.merge(parts);
                }
                Long ossId = store(sampleId, kind, aud, hash, bytes);
                mark(pending.getId(), w -> w
                    .set(DocFile::getOssId, ossId)
                    .set(DocFile::getRenderStatus, STATUS_DONE)
                    .set(DocFile::getErrorMsg, null)
                    .set(DocFile::getRenderedTime, new Date()));
                log.info("渲染完成：sampleId={} docKind={} audience={} hash={} ossId={}",
                    sampleId, kind, aud, hash, ossId);
            } catch (Exception e) {
                log.warn("渲染失败：sampleId={} docKind={} audience={} hash={}：{}",
                    sampleId, kind, aud, hash, e.toString());
                mark(pending.getId(), w -> w
                    .set(DocFile::getRenderStatus, STATUS_FAILED)
                    .set(DocFile::getErrorMsg, reason(e)));
            }
            return toVo(findById(pending.getId()));
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /lqg/doc/{sampleId}/{docKind}/download
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 只发**10 分钟内的短时签名链接**（产物在私有桶里，绝不发公开地址）。
     *
     * <p>★ {@code format} 目前只认 {@code docx}：PDF / 页面图是 DOC-PDF-001 的产物，
     * 这一票不做（ticket §3 边界），传 pdf 明确 400 而不是悄悄给一份 docx。
     */
    public DocDownloadVo download(Long sampleId, String docKind, String format, String audience) {
        String kind = DocKinds.require(docKind);
        String aud = DocAudiences.require(audience);
        String fmt = StringUtils.isBlank(format) ? FORMAT_DOCX : format.trim();
        if (!FORMAT_DOCX.equals(fmt)) {
            throw new ServiceException("本接口当前只支持 format=docx（format=" + fmt
                + " 的产物属于 DOC-PDF-001）", 400);
        }
        return DataPermissionHelper.ignore(() -> {
            DocFile row = findRow(sampleId, kind, aud);
            if (row == null || !STATUS_DONE.equals(row.getRenderStatus()) || row.getOssId() == null) {
                throw new ServiceException("这份文档还没生成（先 POST /lqg/doc/" + sampleId + "/" + kind
                    + "/render?audience=" + aud + "）", 400);
            }
            SysOss oss = sysOssMapper.selectById(row.getOssId());
            if (oss == null || StringUtils.isBlank(oss.getFileName())) {
                throw new ServiceException("渲染产物的 OSS 元数据缺失（ossId=" + row.getOssId() + "）", 500);
            }
            OssClient client = OssFactory.instance(oss.getService());
            DocDownloadVo vo = new DocDownloadVo();
            vo.setUrl(client.createPresignedGetUrl(oss.getFileName(), SIGNED_URL_TTL));
            vo.setFileName(modelFactory.displayName(sampleId, kind, aud) + ".docx");
            return vo;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 缓存行
    // ══════════════════════════════════════════════════════════════════════

    private DocFile findRow(Long sampleId, String docKind, String audience) {
        return docFileMapper.selectOne(new LambdaQueryWrapper<DocFile>()
            .eq(DocFile::getSampleId, sampleId)
            .eq(DocFile::getDocKind, docKind)
            .eq(DocFile::getAudience, audience)
            .eq(DocFile::getFileFormat, FORMAT_DOCX)
            .eq(DocFile::getPageNo, 0));
    }

    private DocFile findById(Long id) {
        return docFileMapper.selectById(id);
    }

    /** 命中判据：done + 指纹一致 + 有产物。 */
    private static boolean isHit(DocFile row, String hash) {
        return row != null
            && STATUS_DONE.equals(row.getRenderStatus())
            && hash.equals(row.getContentHash())
            && row.getOssId() != null;
    }

    private DocFile upsertPending(DocFile row, Long sampleId, String docKind, String audience,
                                  String hash, String version) {
        if (row == null) {
            DocFile entity = new DocFile();
            entity.setSampleId(sampleId);
            entity.setDocKind(docKind);
            entity.setAudience(audience);
            entity.setFileFormat(FORMAT_DOCX);
            entity.setPageNo(0);
            entity.setContentHash(hash);
            entity.setTemplateVersion(version);
            entity.setRenderStatus(STATUS_PENDING);
            docFileMapper.insert(entity);
            return entity;
        }
        mark(row.getId(), w -> w
            .set(DocFile::getContentHash, hash)
            .set(DocFile::getTemplateVersion, version)
            .set(DocFile::getRenderStatus, STATUS_PENDING)
            .set(DocFile::getErrorMsg, null));
        return row;
    }

    private void mark(Long id, java.util.function.Consumer<LambdaUpdateWrapper<DocFile>> sets) {
        LambdaUpdateWrapper<DocFile> wrapper = new LambdaUpdateWrapper<DocFile>().eq(DocFile::getId, id);
        sets.accept(wrapper);
        // update(null, wrapper) 不会自动填 update_by / update_time，必须显式补（QC-MODEL-001 的坑）
        wrapper.set(DocFile::getUpdateBy, currentUserId()).set(DocFile::getUpdateTime, new Date());
        docFileMapper.update(null, wrapper);
    }

    /** 产出的 docx 传 OSS（对象键按 ticket §2 的约定，audience 进路径），并落一条 {@code sys_oss}。 */
    private Long store(Long sampleId, String docKind, String audience, String hash, byte[] bytes) {
        String key = DocOssBytes.objectKey(sampleId, docKind, audience, hash, ".docx");
        OssClient client = OssFactory.instance();
        UploadResult result = client.upload(new ByteArrayInputStream(bytes), key,
            (long) bytes.length, DOCX_CONTENT_TYPE);
        SysOss oss = new SysOss();
        oss.setFileName(result.getFilename());
        oss.setOriginalName(key.substring(key.lastIndexOf('/') + 1));
        oss.setFileSuffix(".docx");
        oss.setUrl(result.getUrl());
        oss.setService(client.getConfigKey());
        sysOssMapper.insert(oss);
        return oss.getOssId();
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

    private static String reason(Exception e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
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
