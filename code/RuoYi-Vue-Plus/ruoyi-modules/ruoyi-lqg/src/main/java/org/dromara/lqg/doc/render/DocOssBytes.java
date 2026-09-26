package org.dromara.lqg.doc.render;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.exception.OssException;
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
 * <p>★★ <b>取不到不抛异常，但一定带回原因</b>（{@link #fetch}）：
 * 取图失败不是「跳过就算了」的小事（#217，2026-09-23 定的口径）——
 * <ul>
 *   <li><b>外部版</b>：只要有一张图取不到，整份按渲染失败处理（{@code render_status='failed'}），
 *       外部清单、页面图、下载一律不可见；图补上后重新生成才恢复；</li>
 *   <li><b>内部版</b>：照样出，但缺了哪几张要<b>持久化</b>到渲染记录上
 *       （{@code t_lqg_doc_file.missing_image_count / missing_images}），工作台质控页与首页都看得见，
 *       并计入首页的「渲染失败」数。</li>
 * </ul>
 * 判定与落库在 {@link DocxRenderer} / {@code DocRenderService}，本类只负责「取到了什么、没取到为什么」。
 *
 * @author DOC-RENDER-001 · 独立验收 V23 修复（取图失败带原因）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocOssBytes {

    private final SysOssMapper sysOssMapper;

    /**
     * 一次取图的结果：{@code bytes} 非空 = 取到了；否则 {@code reason} 是给人看的原因。
     */
    public record Fetched(byte[] bytes, String reason) {

        public boolean ok() {
            return bytes != null && bytes.length > 0;
        }

        static Fetched of(byte[] bytes) {
            return new Fetched(bytes, null);
        }

        static Fetched missing(String reason) {
            return new Fetched(null, reason);
        }
    }

    /**
     * 取字节并带回原因（渲染用这个：取不到的图要记进缺图清单）。
     *
     * @param ossId 原图 / 预览图的 oss_id
     */
    public Fetched fetch(Long ossId) {
        if (ossId == null) {
            return Fetched.missing("图片记录缺少文件");
        }
        SysOss oss = sysOssMapper.selectById(ossId);
        if (oss == null) {
            log.warn("渲染取图：sys_oss 里没有 ossId={}", ossId);
            return Fetched.missing("文件记录不存在");
        }
        return fetch(oss);
    }

    /**
     * 已知元数据时直接用（省一次查库）。
     */
    public Fetched fetch(SysOss oss) {
        if (oss == null) {
            return Fetched.missing("文件记录不存在");
        }
        if (StringUtils.isBlank(oss.getService()) || StringUtils.isBlank(oss.getFileName())) {
            log.warn("渲染取图：ossId={} 的文件记录不完整（service={} file={}）",
                oss.getOssId(), oss.getService(), oss.getFileName());
            return Fetched.missing("文件记录不完整");
        }
        final OssClient client;
        try {
            client = OssFactory.instance(oss.getService());
        } catch (OssException e) {
            log.warn("渲染取图失败 ossId={} service={}：存储配置不存在（{}）", oss.getOssId(), oss.getService(), e.getMessage());
            return Fetched.missing("存储配置「" + oss.getService() + "」不存在");
        }
        try (InputStream in = client.getObjectContent(oss.getFileName())) {
            byte[] bytes = in == null ? null : in.readAllBytes();
            if (bytes == null || bytes.length == 0) {
                log.warn("渲染取图失败 ossId={} file={}：对象为空", oss.getOssId(), oss.getFileName());
                return Fetched.missing("文件是空的");
            }
            return Fetched.of(bytes);
        } catch (Exception e) {
            log.warn("渲染取图失败 ossId={} service={} file={}：{}",
                oss.getOssId(), oss.getService(), oss.getFileName(), e.toString());
            return Fetched.missing("存储里读不到这个文件");
        }
    }

    /**
     * 兼容旧调用：取不到返回 {@code null}（原因只进日志）。渲染链路请用 {@link #fetch}。
     */
    public byte[] read(Long ossId) {
        Fetched fetched = fetch(ossId);
        return fetched.ok() ? fetched.bytes() : null;
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
