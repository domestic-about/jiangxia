package org.dromara.lqg.doc.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.lqg.doc.render.DocOssBytes;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Duration;

/**
 * 渲染产物（docx / pdf / 每页 png）的 OSS 存取：上传 + 落 {@code sys_oss} 行 + 签发短时链接。
 *
 * <p>★ 对象键沿用 DOC-RENDER-001 的约定 {@code lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前 12 位>}
 * （{@link DocOssBytes#objectKey}），后缀区分产物：
 *
 * <pre>
 *   …/<hash12>.docx           Word
 *   …/<hash12>.pdf            PDF
 *   …/<hash12>-p3.png         第 3 页 PNG（page_no 从 1 起，进键里 → 每一页一个对象）
 * </pre>
 *
 * <p>★ <b>产物永远在私有桶里</b>，对外只发**短时签名链接**（REQ-SYS-004 / ADR-0005；
 * ticket §2：pages 里的 url 都是短时签名链接）。有效期与下载一致：10 分钟。
 *
 * @author DOC-PDF-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocArtifactStore {

    /** 签名链接有效期（ticket §2：短时）。与 {@code DocRenderService.SIGNED_URL_TTL} 同一个值。 */
    public static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);

    private static final String DOCX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PNG_CONTENT_TYPE = "image/png";

    private final SysOssMapper sysOssMapper;

    public Long uploadDocx(Long sampleId, String docKind, String audience, String contentHash, byte[] bytes) {
        return upload(DocOssBytes.objectKey(sampleId, docKind, audience, contentHash, ".docx"),
            bytes, ".docx", DOCX_CONTENT_TYPE);
    }

    public Long uploadPdf(Long sampleId, String docKind, String audience, String contentHash, byte[] bytes) {
        return upload(DocOssBytes.objectKey(sampleId, docKind, audience, contentHash, ".pdf"),
            bytes, ".pdf", PDF_CONTENT_TYPE);
    }

    public Long uploadPng(Long sampleId, String docKind, String audience, String contentHash, int pageNo, byte[] bytes) {
        return upload(DocOssBytes.objectKey(sampleId, docKind, audience, contentHash, "-p" + pageNo + ".png"),
            bytes, ".png", PNG_CONTENT_TYPE);
    }

    /** 上传并落一条 {@code sys_oss}，返回 {@code oss_id}。 */
    public Long upload(String key, byte[] bytes, String suffix, String contentType) {
        OssClient client = OssFactory.instance();
        UploadResult result = client.upload(new ByteArrayInputStream(bytes), key, (long) bytes.length, contentType);
        SysOss oss = new SysOss();
        oss.setFileName(result.getFilename());
        oss.setOriginalName(key.substring(key.lastIndexOf('/') + 1));
        oss.setFileSuffix(suffix);
        oss.setUrl(result.getUrl());
        oss.setService(client.getConfigKey());
        sysOssMapper.insert(oss);
        return oss.getOssId();
    }

    public SysOss oss(Long ossId) {
        return ossId == null ? null : sysOssMapper.selectById(ossId);
    }

    /**
     * 短时签名链接；{@code ossId} 为空 / 元数据缺失 / 没配 OSS 一律返回 {@code null}
     * （读取侧容忍：一页图出不来不该让整个接口 500）。
     */
    public String signedUrl(Long ossId) {
        if (ossId == null) {
            return null;
        }
        SysOss oss = sysOssMapper.selectById(ossId);
        if (oss == null || StringUtils.isBlank(oss.getFileName())) {
            return null;
        }
        try {
            OssClient client = OssFactory.instance(oss.getService());
            return client.createPresignedGetUrl(oss.getFileName(), SIGNED_URL_TTL);
        } catch (Exception e) {
            log.warn("签发签名链接失败 ossId={} service={} file={}：{}",
                ossId, oss.getService(), oss.getFileName(), e.toString());
            return null;
        }
    }

    /** 下载用：拿不到元数据就是「产物记录坏了」，明确报 500（不是静默给个空链接）。 */
    public String signedUrlOrFail(Long ossId) {
        SysOss oss = oss(ossId);
        if (oss == null || StringUtils.isBlank(oss.getFileName())) {
            throw new ServiceException("产物的 OSS 元数据缺失（ossId=" + ossId + "）", 500);
        }
        return OssFactory.instance(oss.getService()).createPresignedGetUrl(oss.getFileName(), SIGNED_URL_TTL);
    }
}
