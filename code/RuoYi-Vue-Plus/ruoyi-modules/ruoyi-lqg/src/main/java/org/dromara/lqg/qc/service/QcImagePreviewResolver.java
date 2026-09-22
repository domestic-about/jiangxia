package org.dromara.lqg.qc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;

/**
 * 图片位的**预览图判定**（ticket §2 + FIELD:t_lqg_doc_image.preview_oss_id）：
 *
 * <pre>
 *   非 jpg / png（TIFF、BMP…） 或 长边 &gt; 2000px
 *        → 另存一张长边 ≤ 2000px 的 JPEG，preview_oss_id = 新 oss
 *   否则
 *        → preview_oss_id = oss_id（原图本身就是能进浏览器 / 进 Word 的那张）
 * </pre>
 *
 * <p>★ <b>「取不到原文件」一律退回原图</b>（{@code preview_oss_id = oss_id}）并打 WARN，
 * <b>不抛异常、不阻断保存</b>：本机的确定性测试数据里 {@code sys_oss} 的
 * {@code service='seed'}、URL 是 {@code https://seed.invalid/...} 的假地址，
 * 根本读不到字节。为一条「预览图没生成」把整个图片位保存打成 500，比预览图缺失更糟。
 * 真实的 TIFF / 大图（走工作台上传、{@code service='minio'}）才会走到转换分支。
 *
 * <p>★ 转换用 JDK 自带的 {@code ImageIO}（不引新依赖）：TIFF 本机 ImageIO 读不了时
 * {@code read} 返回 null → 同样退回原图。这一点在本票报告里如实记 WARN
 * （要覆盖 TIFF 得加 {@code imageio-tiff} 之类的插件，属于后续票的范围）。
 *
 * @author QC-MODEL-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QcImagePreviewResolver {

    /** 长边上限：超过它就另存预览（进 Word 的也是预览图）。 */
    static final int MAX_LONG_EDGE = 2000;

    /** 浏览器 / Word 都直接认的两种格式；其余一律转 JPEG。 */
    private static final Set<String> WEB_SAFE_SUFFIXES = Set.of("jpg", "jpeg", "png");

    private final SysOssMapper sysOssMapper;

    /**
     * 算这条图片位该用哪张当预览图。
     *
     * @param ossId 原图 oss_id
     * @return 预览图 oss_id（任何一步做不到就返回原 {@code ossId}；{@code ossId} 本身为 null 时返回 null）
     */
    public Long resolvePreviewOssId(Long ossId) {
        if (ossId == null) {
            return null;
        }
        try {
            SysOss oss = sysOssMapper.selectById(ossId);
            if (oss == null) {
                // 元数据都没有：无从判断，退回原图（读图时 URL 也取不到，由查询侧容忍）
                return ossId;
            }
            String suffix = normalizeSuffix(oss.getFileSuffix());
            byte[] bytes = readObjectBytes(oss);
            if (bytes == null) {
                return ossId;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                // 本机 ImageIO 打不开（TIFF 等）：无从缩放，退回原图
                log.warn("预览图判定：读不出图片内容，退回原图 ossId={} suffix={}", ossId, suffix);
                return ossId;
            }
            int longEdge = Math.max(image.getWidth(), image.getHeight());
            if (WEB_SAFE_SUFFIXES.contains(suffix) && longEdge <= MAX_LONG_EDGE) {
                return ossId;
            }
            Long preview = storePreviewJpeg(image, longEdge);
            return preview == null ? ossId : preview;
        } catch (Exception e) {
            log.warn("预览图判定失败，退回原图 ossId={}：{}", ossId, e.toString());
            return ossId;
        }
    }

    /**
     * 把原图字节读进内存（走 {@code sys_oss.service} 指向的 OSS 配置）。
     *
     * @return 字节；读不到（配置不存在 / 对象不存在 / 网络不通）返回 {@code null}
     */
    private byte[] readObjectBytes(SysOss oss) {
        if (StringUtils.isBlank(oss.getService()) || StringUtils.isBlank(oss.getFileName())) {
            return null;
        }
        try {
            OssClient client = OssFactory.instance(oss.getService());
            try (InputStream in = client.getObjectContent(oss.getFileName())) {
                return in == null ? null : in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("预览图判定：取不到原文件 ossId={} service={} file={}：{}",
                oss.getOssId(), oss.getService(), oss.getFileName(), e.toString());
            return null;
        }
    }

    /**
     * 缩放（必要时）→ 编成 JPEG → 存进默认 OSS → 落一条 {@code sys_oss} 元数据。
     *
     * @return 新 oss_id；任何一步失败返回 {@code null}（调用方退回原图）
     */
    private Long storePreviewJpeg(BufferedImage source, int longEdge) {
        try {
            BufferedImage scaled = source;
            if (longEdge > MAX_LONG_EDGE) {
                double ratio = (double) MAX_LONG_EDGE / longEdge;
                int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
                int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
                scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(source, 0, 0, width, height, null);
                } finally {
                    g.dispose();
                }
            } else if (scaled.getType() != BufferedImage.TYPE_INT_RGB) {
                // JPEG 没有 alpha 通道：单色 / 带透明度的原图先铺成 RGB
                BufferedImage rgb = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.drawImage(scaled, 0, 0, null);
                } finally {
                    g.dispose();
                }
                scaled = rgb;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(scaled, "jpg", out)) {
                return null;
            }
            OssClient client = OssFactory.instance();
            UploadResult result = client.uploadSuffix(out.toByteArray(), ".jpg", "image/jpeg");
            SysOss preview = new SysOss();
            preview.setFileName(result.getFilename());
            preview.setOriginalName("preview-" + result.getFilename());
            preview.setFileSuffix(".jpg");
            preview.setUrl(result.getUrl());
            preview.setService(client.getConfigKey());
            sysOssMapper.insert(preview);
            log.info("预览图已另存：source={}x{} → ossId={}", source.getWidth(), source.getHeight(), preview.getOssId());
            return preview.getOssId();
        } catch (Exception e) {
            log.warn("预览图另存失败，退回原图：{}", e.toString());
            return null;
        }
    }

    private static String normalizeSuffix(String suffix) {
        if (StringUtils.isBlank(suffix)) {
            return "";
        }
        return suffix.trim().toLowerCase().replace(".", "");
    }

}
