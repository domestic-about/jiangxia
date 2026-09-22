package org.dromara.lqg.doc.pdf;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * docx → PDF：把 Word 字节交给 Gotenberg 容器（{@code POST /forms/libreoffice/convert}）。
 * 见 ADR-0005 / FLOW:F-DOC-01.step3。
 *
 * <p>★★ <b>三件必须做对的事</b>：
 *
 * <ol>
 *   <li><b>串行</b>（ticket §0 口径复述 2 / §2）：单线程执行器排队。LibreOffice 转一份几百 MB
 *       内存，2 核 4G 的机器经不起并发。排在后面的人不会饿死 —— 整体预算超了就失败，见第 3 条。</li>
 *   <li><b>不吞异常</b>：连不上 / HTTP 非 2xx / 超时 / 返回的不是 PDF，都要抛出一句人能读的话，
 *       由 {@code DocRenderService} 落成 {@code failed + error_msg}（accept 2 断「转换服务不可用时
 *       status=failed 且 error_msg 非空」）。**绝不**在这里返回空字节让上层误以为成功。</li>
 *   <li><b>超时 60 秒</b>（ticket §2）：整个调用（含排队）的预算是 {@code timeout-seconds}，
 *       HTTP 自身的超时也取同一个值，另留 15 秒余量让「HTTP 超时」先于「get 超时」触发，
 *       于是错误信息里能带上到底是哪一步卡住。</li>
 * </ol>
 *
 * <p>★ <b>代理</b>：这个客户端**显式不走任何代理**。本机的 macOS 系统代理会被 JDK 灌成
 * {@code http.proxyHost}（DOC-RENDER-001 的血泪，见其报告 WARN-1），而 Gotenberg 是内网服务；
 * 让它走代理只会重演「连接被关」的 120 秒事故。{@code NO_PROXY} 语义在代码里写死，不依赖环境变量。
 *
 * <p>★ 请求体用 JDK 自带的 {@code java.net.http.HttpClient} + 手搓 multipart
 * （{@link MultipartForm}）：不为一个 POST 引额外的 HTTP 客户端依赖。
 *
 * @author DOC-PDF-001
 */
@Slf4j
@Component
public class PdfConvertService {

    /** Gotenberg 的 LibreOffice 转换路由。 */
    static final String CONVERT_PATH = "/forms/libreoffice/convert";

    private static final String DOCX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** HTTP 超时之外多给的余量：让「HTTP 超时」先报出来，而不是笼统的 future 超时。 */
    private static final long OVERALL_GRACE_SECONDS = 15;

    /** 错误信息里带多少字节的响应体（Gotenberg 的错误是一句 JSON/纯文本）。 */
    private static final int ERROR_BODY_MAX = 300;

    private final String baseUrl;
    private final int timeoutSeconds;
    private final ExecutorService worker;
    private final HttpClient http;

    public PdfConvertService(@Value("${lqg.doc.gotenberg.url:}") String baseUrl,
                             @Value("${lqg.doc.gotenberg.timeout-seconds:60}") int timeoutSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.timeoutSeconds = timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lqg-gotenberg-convert");
            // 守护线程：容器停掉时不让一个卡住的转换拖住 JVM 退出
            thread.setDaemon(true);
            return thread;
        });
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(new NoProxySelector())
            .build();
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }

    /** 配置的转换服务地址（报错信息与探活用）。 */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 转 PDF。
     *
     * @param docx     Word 字节
     * @param fileName 只进日志与 Gotenberg 的输出名提示（ASCII slug，见 {@link MultipartForm#asciiSlug()}）
     * @return PDF 字节（非空、以 {@code %PDF-} 开头）
     * @throws ServiceException 转换服务不可用 / 超时 / 返回异常时；message 直接给人看
     */
    public byte[] toPdf(byte[] docx, String fileName) {
        if (docx == null || docx.length == 0) {
            throw new ServiceException("要转 PDF 的 Word 是空的", 500);
        }
        if (StringUtils.isBlank(baseUrl)) {
            throw new ServiceException("转换服务没配置（lqg.doc.gotenberg.url 为空），文档出不了 PDF", 500);
        }
        final String url = baseUrl.replaceAll("/+$", "") + CONVERT_PATH;
        long overallSeconds = timeoutSeconds + OVERALL_GRACE_SECONDS;
        Future<byte[]> future = worker.submit(() -> call(url, docx, fileName));
        try {
            return future.get(overallSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ServiceException("转换超时：等待 " + overallSeconds + " 秒仍未拿到 PDF（"
                + "单份预算是 " + timeoutSeconds + " 秒，前面可能还排着别的文档）", 500);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof ServiceException serviceException) {
                // call() 里精心写好的那句话原样往上传（error_msg 里看到的就是它）
                throw serviceException;
            }
            throw new ServiceException(DocArtifactRows.reason(cause), 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("转换被中断（服务正在关停？）", 500);
        }
    }

    private byte[] call(String url, byte[] docx, String fileName) {
        byte[] body = MultipartForm.singleFile("files", MultipartForm.asciiSlug() + ".docx",
            DOCX_CONTENT_TYPE, docx);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", MultipartForm.contentType())
            .header("Gotenberg-Output-Filename", MultipartForm.asciiSlug())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            // 最常见的一种：容器停了 / 端口没人听（java.net.ConnectException: Connection refused）
            throw new ServiceException("转换服务不可用（POST " + url + " 失败：" + e + "）", 500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("转换被中断（POST " + url + "）", 500);
        }
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new ServiceException("转换失败（HTTP " + status + "）："
                + snippet(new String(response.body(), StandardCharsets.UTF_8)), 500);
        }
        byte[] pdf = response.body();
        if (pdf == null || pdf.length == 0) {
            throw new ServiceException("转换服务返回了空响应（POST " + url + "）", 500);
        }
        if (!looksLikePdf(pdf)) {
            throw new ServiceException("转换服务返回的不是 PDF（" + pdf.length + " 字节）："
                + snippet(new String(pdf, StandardCharsets.UTF_8)), 500);
        }
        log.info("docx → PDF 完成：{} 字节（file={}）", pdf.length, fileName);
        return pdf;
    }

    static boolean looksLikePdf(byte[] bytes) {
        byte[] magic = {'%', 'P', 'D', 'F', '-'};
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String snippet(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > ERROR_BODY_MAX ? oneLine.substring(0, ERROR_BODY_MAX) + "…" : oneLine;
    }

    /**
     * 「一个代理都不用」的选择器。
     *
     * <p>本机 macOS 的系统代理会被 JDK 读成 {@code http.proxyHost}，而默认
     * {@code nonProxyHosts} **不含 127.0.0.1**（只有 localhost）→ 发往本机/MinIO 的请求被丢给代理
     * 然后连接被关（DOC-RENDER-001 花了很久才查出的病灶）。Gotenberg 也是内网服务，
     * 这里直接把代理这条路堵死，不依赖任何人记得 export NO_PROXY。
     */
    private static final class NoProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // 不需要重试逻辑：失败原样抛给调用方
        }
    }
}
