package org.dromara.lqg.ocr.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.auth.service.WeChatMiniAppService;
import org.dromara.lqg.doc.pdf.MultipartForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 微信官方「通用印刷体识别」（{@code POST https://api.weixin.qq.com/cv/ocr/comm}）。
 *
 * <p>为什么选它（2026-11-04 Kevin 提议、OCR-SPIKE-001 之前的第一个真实实现）：
 * 用的就是小程序自己的 access_token（登录那套 appid / secret，不用另开服务、不用另配密钥）；
 * 微信侧有每日免费额度（官方文档写的是「100 次/天」，超出需**主动购买资源包**才继续，不会自动扣费），
 * 符合合同第二条第 5 款「优先不产生按次费用」，所以<b>不实现 {@link PaidOcrProvider}</b>、不需要 paid-enabled。
 *
 * <p>★ <b>只识别印刷体</b>：官方接口列表里没有手写体能力。手写占比高的照片会识别得很差，
 * 这正是 OCR-SPIKE-001 要拿甲方真实照片量的事；识别不出时前端照旧提示手填，不影响使用。
 *
 * <p>★ 图片只在内存里：请求体是内存里拼的 multipart（同 {@code PdfConvertService} 的口径），不写临时文件、不落 OSS。
 *
 * <p>★ 微信回的 errcode 不透给用户：日志里记 errcode / errmsg 供排查，给用户的永远是
 * 「请手动填写」类提示（与 {@link NoneOcrProvider} 同口径，不弹「系统异常」）。
 *
 * @author OCR-IMPL-001
 */
@Slf4j
// bean 名必须与配置 lqg.ocr.provider 的值一致（OcrProviderFactory 按 bean 名取）
@Component("wechat")
public class WechatOcrProvider implements OcrProvider {

    /** 配在 {@code lqg.ocr.provider} 里的名字。 */
    public static final String NAME = "wechat";

    static final String ENDPOINT = "https://api.weixin.qq.com/cv/ocr/comm?access_token=";

    /** 微信对图片大小的上限（官方旧文档：小于 2MB）；超过就在内存里缩小，留一点余量。 */
    static final int WX_MAX_BYTES = 1_900_000;

    /** token 失效的 errcode（40001 invalid credential / 40014 invalid access_token / 42001 access_token expired）：刷新后重试一次。 */
    private static final List<Integer> TOKEN_INVALID = List.of(40001, 40014, 42001);

    /** 当日额度用完（45009 reach max api daily quota limit）。 */
    private static final int QUOTA_EXCEEDED = 45009;

    static final String MSG_GENERIC = "识别失败，请手动填写";
    static final String MSG_QUOTA = "今日识别次数已用完，请手动填写";

    /** 裸 Jackson：纯单测里不起 Spring（同 StubOcrProvider 的口径）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WeChatMiniAppService wx;
    private final HttpClient http;
    private final Duration timeout;

    public WechatOcrProvider(WeChatMiniAppService wx,
                             @Value("${lqg.ocr.wechat.timeout-seconds:15}") int timeoutSeconds) {
        this.wx = wx;
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public List<String> recognize(byte[] image) {
        byte[] body = fitForWechat(image);
        String json = post(body, false);
        Result first = parse(json);
        if (first.tokenInvalid) {
            // access_token 被微信作废（别处重新取过 / 过期）：强刷一次再试，仍失败才放弃
            first = parse(post(body, true));
        }
        if (first.error != null) {
            throw first.error;
        }
        return first.lines;
    }

    private String post(byte[] image, boolean forceRefresh) {
        String token = wx.accessToken(forceRefresh);
        byte[] form = MultipartForm.singleFile("img", "ocr" + extensionOf(image), mimeOf(image), image);
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + token))
            .timeout(timeout)
            .header("Content-Type", MultipartForm.contentType())
            .POST(HttpRequest.BodyPublishers.ofByteArray(form))
            .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(MSG_GENERIC);
        } catch (IOException e) {
            // 注意别把 URL（带 access_token）写进日志
            log.warn("微信 OCR 请求失败：{}", e.getClass().getSimpleName());
            throw new ServiceException(MSG_GENERIC);
        }
    }

    /** 解析结果：成功带 lines；失败带 error；token 失效另标一个位让调用方重试。 */
    static final class Result {
        final List<String> lines;
        final ServiceException error;
        final boolean tokenInvalid;

        Result(List<String> lines, ServiceException error, boolean tokenInvalid) {
            this.lines = lines;
            this.error = error;
            this.tokenInvalid = tokenInvalid;
        }
    }

    /**
     * 解析微信返回：{@code {"errcode":0,"errmsg":"ok","items":[{"text":"…","pos":{…}}],"img_size":{…}}}。
     * 纯函数，可单测。
     */
    static Result parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (IOException e) {
            log.warn("微信 OCR 返回不是 JSON");
            return new Result(List.of(), new ServiceException(MSG_GENERIC), false);
        }
        int errcode = root.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = root.path("errmsg").asText("");
            log.warn("微信 OCR 报错：errcode={} errmsg={}", errcode, errmsg);
            if (TOKEN_INVALID.contains(errcode)) {
                return new Result(List.of(), new ServiceException(MSG_GENERIC), true);
            }
            String msg = errcode == QUOTA_EXCEEDED ? MSG_QUOTA : MSG_GENERIC;
            return new Result(List.of(), new ServiceException(msg), false);
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            String text = item.path("text").asText("").strip();
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }
        return new Result(lines, null, false);
    }

    /**
     * 微信只收小于 2MB 的图：超了就在内存里等比缩小并按 JPEG 重编码（小程序端已压到长边 2000px，
     * 正常到不了这里；是兜底）。解码失败就原样发，让微信自己判。
     */
    static byte[] fitForWechat(byte[] image) {
        if (image.length <= WX_MAX_BYTES) {
            return image;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
            if (src == null) {
                return image;
            }
            double scale = 1.0;
            byte[] out = image;
            for (int i = 0; i < 4 && out.length > WX_MAX_BYTES; i++) {
                scale *= 0.75;
                int w = Math.max(1, (int) (src.getWidth() * scale));
                int h = Math.max(1, (int) (src.getHeight() * scale));
                BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = dst.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, w, h, null);
                g.dispose();
                out = toJpeg(dst);
            }
            return out;
        } catch (IOException | RuntimeException e) {
            log.warn("识别图缩小失败，按原图发送：{}", e.getClass().getSimpleName());
            return image;
        }
    }

    private static byte[] toJpeg(BufferedImage img) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private static boolean isPng(byte[] image) {
        return image.length > 4 && (image[0] & 0xFF) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G';
    }

    static String mimeOf(byte[] image) {
        return isPng(image) ? "image/png" : "image/jpeg";
    }

    static String extensionOf(byte[] image) {
        return isPng(image) ? ".png" : ".jpg";
    }
}
