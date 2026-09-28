package org.dromara.lqg.qc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.exception.OssException;
import org.dromara.common.oss.properties.OssProperties;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 质控编辑页图片 / 附件地址的口径（Kevin 本机验收「网页工作台」第 3 行：私有桶下缩略图全是「图片加载失败」）。
 *
 * <p>客户端是<b>真的</b> {@link OssClient}（本地构造，签名是离线算的，不连任何 OSS）：
 * <ol>
 *   <li><b>私有桶</b>（access_policy=0）→ 10 分钟签名链接，不是 {@code sys_oss.url} 原值；</li>
 *   <li><b>公有桶</b>（1 / 2）→ 原样给 {@code sys_oss.url}，行为不变；</li>
 *   <li>每行按<b>自己的</b> {@code sys_oss.service} 取配置（minio / image 可以是两套）；</li>
 *   <li>签不出来（seed 假数据没有对应配置）→ 照旧给原值、不抛；</li>
 *   <li>{@link QcDocService} 的读路径真的走这里（不是又把 {@code getUrl()} 原样交出去）。</li>
 * </ol>
 *
 * @author H 批 H2 组
 */
class QcOssUrlsTest {

    private static final String KEY = "2026/09/24/3f3a020767414800b5068d2899d39ab0.png";

    private static OssClient client(String accessPolicy) {
        OssProperties p = new OssProperties();
        p.setEndpoint("127.0.0.1:59999");
        p.setAccessKey("ak-test");
        p.setSecretKey("sk-test");
        p.setBucketName("lqg-test");
        p.setIsHttps("N");
        p.setAccessPolicy(accessPolicy);
        return new OssClient("minio", p);
    }

    private static SysOss oss(long id, String service, String key) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setService(service);
        oss.setFileName(key);
        oss.setUrl(key == null ? "https://seed.invalid/x.png" : "http://127.0.0.1:59999/lqg-test/" + key);
        return oss;
    }

    @Test
    @DisplayName("① 私有桶：给 10 分钟签名链接，不是原样的 sys_oss.url（原样那条在私有桶上是 403）")
    void privateBucketIsSigned() {
        SysOss row = oss(1L, "minio", KEY);
        String url = new QcOssUrls(s -> client("0")).urlOf(row);
        assertNotEquals(row.getUrl(), url, "私有桶还在原样给直链");
        assertTrue(url.startsWith("http://127.0.0.1:59999/lqg-test/" + KEY + "?"), url);
        assertTrue(url.contains("X-Amz-Signature="), "没有签名：" + url);
        assertTrue(url.contains("X-Amz-Expires=600"), "有效期应与渲染产物一致（10 分钟）：" + url);
    }

    @Test
    @DisplayName("② 公有桶（public / custom）：原样给 sys_oss.url，行为不变")
    void publicBucketUnchanged() {
        for (String policy : List.of("1", "2")) {
            SysOss row = oss(2L, "minio", KEY);
            assertEquals(row.getUrl(), new QcOssUrls(s -> client(policy)).urlOf(row), "access_policy=" + policy);
        }
    }

    @Test
    @DisplayName("③ 每行按自己的 service 取配置；同一个 service 一批里只取一次客户端")
    void eachRowUsesItsOwnService() {
        Map<String, Integer> lookups = new HashMap<>();
        Map<String, OssClient> byService = Map.of("minio", client("0"), "image", client("1"));
        Function<String, OssClient> clients = s -> {
            lookups.merge(s, 1, Integer::sum);
            return byService.get(s);
        };
        List<SysOss> rows = List.of(oss(1L, "minio", KEY), oss(2L, "image", KEY), oss(3L, "minio", "2026/09/24/b.png"));
        Map<Long, String> urls = new QcOssUrls(clients).urlsOf(rows);
        assertTrue(urls.get(1L).contains("X-Amz-Signature="), "minio 是私有桶：" + urls.get(1L));
        assertEquals(rows.get(1).getUrl(), urls.get(2L), "image 是公有桶：原样");
        assertTrue(urls.get(3L).contains("/b.png?"), urls.get(3L));
        assertEquals(Map.of("minio", 1, "image", 1), lookups, "同一个 service 应只取一次客户端");
    }

    @Test
    @DisplayName("④ 签不出来（seed 假数据没有对应配置 / 缺对象键）：照旧给原值，不抛")
    void unsignableFallsBackToStoredUrl() {
        QcOssUrls urls = new QcOssUrls(s -> {
            throw new OssException("系统异常, '" + s + "'配置信息不存在!");
        });
        SysOss seed = oss(9L, "seed", "seed/orig.png");
        seed.setUrl("https://seed.invalid/orig.png");
        assertEquals("https://seed.invalid/orig.png", urls.urlOf(seed));

        SysOss noKey = oss(10L, "minio", null);
        assertEquals(noKey.getUrl(), new QcOssUrls(s -> client("0")).urlOf(noKey), "没有对象键无从签名");
        assertNull(urls.urlOf(null));
        assertTrue(urls.urlsOf(null).isEmpty());
    }

    @Test
    @DisplayName("⑤ QcDocService 读图片 / 附件地址真的走签名：私有桶 → 签名链接，查不到的 oss 不在结果里")
    void qcDocServiceReadsSignedUrls() {
        List<SysOss> stored = List.of(oss(1L, "minio", KEY), oss(2L, "minio", "2026/09/24/att.pdf"));
        List<Collection<?>> queried = new ArrayList<>();
        SysOssMapper mapper = (SysOssMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{SysOssMapper.class}, (proxy, method, args) -> switch (method.getName()) {
                case "selectByIds" -> {
                    Collection<?> ids = (Collection<?>) args[0];
                    queried.add(ids);
                    yield stored.stream().filter(o -> ids.contains(o.getOssId())).toList();
                }
                case "toString" -> "stub-SysOssMapper";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        QcDocService service = new QcDocService(null, null, null, null, null, mapper, null, null, null, null,
            new QcOssUrls(s -> client("0")));

        List<Long> ids = new ArrayList<>(List.of(1L, 2L, 1L, 404L));
        ids.add(null);
        Map<Long, String> urls = service.ossUrls(ids);
        assertEquals(1, queried.size());
        assertEquals(List.of(1L, 2L, 404L), new ArrayList<>(queried.get(0)), "去重、去空后一次查齐");
        assertEquals(2, urls.size(), "查不到的 404 不给地址：" + urls.keySet());
        for (SysOss row : stored) {
            String url = urls.get(row.getOssId());
            assertFalse(url.equals(row.getUrl()), "又把 sys_oss.url 原样交出去了：" + url);
            assertTrue(url.contains("X-Amz-Signature="), url);
        }
        assertTrue(service.ossUrls(List.of()).isEmpty());
    }
}
