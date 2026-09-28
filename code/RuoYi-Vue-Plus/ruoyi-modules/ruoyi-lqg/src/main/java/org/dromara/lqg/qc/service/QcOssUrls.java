package org.dromara.lqg.qc.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.enums.AccessPolicyType;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.lqg.doc.pdf.DocArtifactStore;
import org.dromara.system.domain.SysOss;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 质控编辑页里图片位 / 附件的<b>访问地址</b>（{@code GET /lqg/qc/{sampleId}} 的 {@code url / previewUrl}）。
 *
 * <p>★ 为什么不能原样给 {@code sys_oss.url}（Kevin 本机验收「网页工作台」第 3 行：图片上传成功但预览全是「图片加载失败」）：
 * 文件存储是<b>私有桶</b>（本机 {@code access_policy='0'}，生产同样是私有桶），{@code sys_oss.url} 是不带签名的直链，
 * 私有桶上一律 403 —— 缩略图、点图放大、附件下载全打不开；而 Word 里的图走的是后端直接读字节
 * （{@code DocOssBytes}），所以下载的 Word 是好的，两边对不上。
 *
 * <p>★ 口径与若依 {@code SysOssServiceImpl#matchingUrl} 一致：按这一行 {@code sys_oss.service} 取<b>它自己那套</b>
 * OSS 配置（不是默认配置键）→ 私有桶签临时链接，公有桶照旧给 {@code sys_oss.url}（行为不变）。
 * 有效期与渲染产物同一个值 {@link DocArtifactStore#SIGNED_URL_TTL}（10 分钟，ADR-0005「对外只发短时签名链接」）；
 * 编辑页开久了链接过期，由工作台在「图片加载失败 / 点图放大 / 点附件」时重取一次地址
 * （{@code views/lqg/qc/editor/index.vue} 的 {@code ensureFreshUrls}），不靠把有效期拉长。
 *
 * <p>★ <b>签不出来不抛</b>：本机确定性测试数据里 {@code service='seed'} 的行没有对应配置、URL 是假地址，
 * 照旧给原值（与 {@code ossUrls} 原来「读时容忍」一致）—— 一张图的地址出不来不该让整份质控文档 500。
 *
 * @author H 批 H2 组（Kevin 本机验收「网页工作台」第 3 行）
 */
@Slf4j
@Component
public class QcOssUrls {

    /** 签名链接有效期：与渲染产物、下载链接同一个值（10 分钟）。 */
    static final Duration SIGNED_URL_TTL = DocArtifactStore.SIGNED_URL_TTL;

    /** {@code sys_oss.service} → 那一套 OSS 客户端（生产走 {@link OssFactory}；单测换成本地构造的客户端）。 */
    private final Function<String, OssClient> clients;

    public QcOssUrls() {
        this(OssFactory::instance);
    }

    QcOssUrls(Function<String, OssClient> clients) {
        this.clients = clients;
    }

    /**
     * 一批 {@code sys_oss} 行 → {@code oss_id → 能访问的地址}。
     * 同一个 service 只取一次客户端（一份质控文档最多十来张图 + 附件，都在同一个桶里）。
     */
    public Map<Long, String> urlsOf(Collection<SysOss> rows) {
        Map<Long, String> urls = new LinkedHashMap<>();
        if (rows == null) {
            return urls;
        }
        Map<String, OssClient> byService = new HashMap<>();
        for (SysOss oss : rows) {
            if (oss != null) {
                urls.put(oss.getOssId(), urlOf(oss, byService));
            }
        }
        return urls;
    }

    /** 单行的地址（私有桶 → 签名链接；公有桶 / 签不出 → {@code sys_oss.url} 原值）。 */
    public String urlOf(SysOss oss) {
        return oss == null ? null : urlOf(oss, new HashMap<>());
    }

    private String urlOf(SysOss oss, Map<String, OssClient> byService) {
        if (StringUtils.isBlank(oss.getService()) || StringUtils.isBlank(oss.getFileName())) {
            return oss.getUrl();
        }
        try {
            OssClient client = byService.computeIfAbsent(oss.getService(), clients);
            if (client.getAccessPolicy() == AccessPolicyType.PRIVATE) {
                return client.createPresignedGetUrl(oss.getFileName(), SIGNED_URL_TTL);
            }
        } catch (Exception e) {
            log.warn("质控文档取图片 / 附件地址：签不出签名链接，照旧给原地址 ossId={} service={} file={}：{}",
                oss.getOssId(), oss.getService(), oss.getFileName(), e.toString());
        }
        return oss.getUrl();
    }
}
