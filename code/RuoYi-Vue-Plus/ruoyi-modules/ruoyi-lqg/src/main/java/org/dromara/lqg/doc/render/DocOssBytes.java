package org.dromara.lqg.doc.render;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 按 {@code sys_oss.oss_id} 取对象字节（进 Word 的图走这里）。
 *
 * <p>★ <b>走每一行自己的 {@code service}</b>（与 {@code QcImagePreviewResolver} 同源）：
 * {@code sys_oss.service='minio'} 的行要用 minio 那套配置，不能一律用默认配置键 ——
 * 上传侧可能用了非默认的存储服务。
 *
 * <p>★★ <b>取不到一律返回 {@code null}，不抛异常</b>（本票如实登记的口径，见完工报告 WARN）：
 * 本机确定性数据里 {@code sys_oss.service='seed'}、URL 是 {@code https://seed.invalid/...}
 * 的假地址，根本取不到字节。accept 2 要渲染的
 * {@code 9000001001/organoid_qc} 名下正好有一张这种假图，而它<b>必须渲染成 {@code done}</b>
 * ——「取不到图就整份 failed」会让那条断言永远红。所以：取不到的图**跳过并打 WARN**，
 * 文档照样出，页面上少一张图不会比「送检方永远拿不到文档」更糟。
 * （DOC-PDF-001 的 counterfeit 里写的是「应整体 failed」，与本票 accept 冲突，
 * 已在完工报告的「给下游的坑」里点名，请下游按本票的行为对齐。）
 *
 * @author DOC-RENDER-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocOssBytes {

    private final SysOssMapper sysOssMapper;

    /**
     * @param ossId 原图 / 预览图的 oss_id
     * @return 字节；取不到（没有元数据 / 没配 OSS / 对象不在 / 网络不通）返回 {@code null}
     */
    public byte[] read(Long ossId) {
        if (ossId == null) {
            return null;
        }
        SysOss oss = sysOssMapper.selectById(ossId);
        if (oss == null) {
            log.warn("渲染取图：sys_oss 里没有 ossId={}", ossId);
            return null;
        }
        return read(oss);
    }

    /**
     * 已知元数据时直接用（省一次查库）。
     */
    public byte[] read(SysOss oss) {
        if (oss == null || StringUtils.isBlank(oss.getService()) || StringUtils.isBlank(oss.getFileName())) {
            return null;
        }
        try {
            OssClient client = OssFactory.instance(oss.getService());
            try (InputStream in = client.getObjectContent(oss.getFileName())) {
                return in == null ? null : in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("渲染取图失败，跳过这张图 ossId={} service={} file={}：{}",
                oss.getOssId(), oss.getService(), oss.getFileName(), e.toString());
            return null;
        }
    }

    /**
     * 拼对象键的助手：{@code lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前 12 位>.docx}
     * （ticket §2 的约定，audience 进路径 —— 外部接口签发链接时据此再核一遍）。
     */
    public static String objectKey(Long sampleId, String docKind, String audience, String contentHash, String suffix) {
        String shortHash = contentHash == null || contentHash.length() < 12 ? contentHash : contentHash.substring(0, 12);
        return "lqg/doc/" + sampleId + "/" + docKind + "/" + audience + "/" + shortHash + suffix;
    }
}
