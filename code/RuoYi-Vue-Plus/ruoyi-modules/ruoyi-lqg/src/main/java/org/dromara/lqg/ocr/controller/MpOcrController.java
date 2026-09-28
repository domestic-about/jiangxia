package org.dromara.lqg.ocr.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.ratelimiter.annotation.RateLimiter;
import org.dromara.lqg.ocr.config.OcrProperties;
import org.dromara.lqg.ocr.domain.vo.OcrRecognizeVo;
import org.dromara.lqg.ocr.provider.StubOcrProvider;
import org.dromara.lqg.ocr.service.OcrRecognizeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 拍照识别（doc/api-contract.md「OCR」一节）。
 *
 * <pre>
 * POST /mp/ocr/recognize        multipart file（jpg / png，≤5MB）→ {rawLines, fields}   内外部都能调
 * </pre>
 *
 * <p>工作台那半（{@code GET /lqg/ocr/status}）在 {@code OcrStatusController}：
 * 两条路径的身份口径不同（一个内外部都能调，一个只给工作台），拆两类更好读。
 *
 * <p>★ <b>识别接口不写任何业务表、不存图</b>（ADR-0007 / ticket §0 口径 2）：图片只在内存里，
 * 识别完即弃；本类与 {@code OcrRecognizeService} 都不碰 OSS / {@code sys_oss}。
 *
 * <p>★ <b>内外部都能调</b>（契约「通用」一节的表把 {@code /mp/ocr/**} 与 {@code /mp/me}、
 * {@code /mp/dict/**} 并列）：所以不加 {@code @SaCheckRole} / {@code @SaCheckPermission}，
 * 只有 Sa-Token 的登录拦截（匿名 401）。路径不是 {@code /mp/ext} 开头、类不在 {@code ext} 包内，
 * 不影响 {@code ExtChokepointContractTest} 的四条不变量。
 *
 * <p>★ <b>限流</b>：{@code @RateLimiter}（框架自带，Redisson 令牌桶）每用户每分钟 6 次；
 * 超了返回明确 msg（不是让它去刷免费 OCR）。{@code @SaCheckRole} / {@code @RateLimiter}
 * 是两类不同的问题：前者是「谁能调」，后者是「能调几次」。
 *
 * @author OCR-IMPL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ocr")
public class MpOcrController {

    /**
     * 只收这两种图（ticket §2）。
     */
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png");

    /**
     * 限流窗口（秒）与次数（ticket §2：每用户每分钟 6 次）。
     */
    private static final int RATE_TIME_SECONDS = 60;

    private static final int RATE_COUNT = 6;

    private final OcrRecognizeService ocrRecognizeService;
    private final OcrProperties ocrProperties;

    /**
     * 拍照识别：收图 → 识别 → 规则解析 → 返回预填字段。
     *
     * <p>★ <b>限流的 key 为什么带 {@code #T(...).getUserIdStr()} 这一维</b>：框架的
     * {@code @RateLimiter} 缺省把 key 拼成 {@code global:rate_limit:<URI>:<自定义key>}，
     * <b>不含用户</b>；而 Redisson 的 {@code RateLimiter} 是<b>按 key 的令牌桶</b>、且
     * {@code timeout} 一天才清一次状态。不加用户维度的后果实测过两次：
     * ① 一个用户刷完以后<b>所有人</b>都拿到「识别太频繁」（连测试用例都跑不完）；
     * ② 桶的状态跨进程共享 —— 同一台机上的 8081 后端也会把 8094 的桶吃空。
     * 加上 userId 之后每个用户一个桶，才是 ticket §2 说的「<b>每用户</b>每分钟 6 次」。
     * 表达式写成 {@code #{@bean.method()}} 模板形：框架只把<b>含 {@code #} 的 key</b> 当表达式
     * （{@code @bean.method()} 会被当字面量、整个 key 变成 bean 名，实测过），
     * 而它的 SpEL 上下文<b>没有 TypeLocator</b>，{@code #T(...)} 会 {@code EL1006E}；
     * 冒号又是保留语法，也不能写 {@code user:#T(...)}。最终 key 形如
     * {@code global:rate_limit:/mp/ocr/recognize:9000000112}。
     *
     * @param file     图片（multipart 字段名 {@code file}）
     * @param stubCase 测试桩用例（仅 dev / test 的 {@code StubOcrProvider} 读它）
     */
    @RateLimiter(key = "#{@ocrRateLimitKey.userKey()}",
        time = RATE_TIME_SECONDS, count = RATE_COUNT, message = "识别太频繁了，请稍后再试（每分钟最多 6 次）")
    @PostMapping("/recognize")
    public R<OcrRecognizeVo> recognize(@RequestParam("file") MultipartFile file,
                                       @RequestHeader(value = StubOcrProvider.CASE_HEADER, required = false) String stubCase) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请先拍照或选择图片");
        }
        long maxBytes = ocrProperties.getMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new ServiceException("图片不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException("只支持 jpg / png 格式的图片");
        }
        byte[] image;
        try {
            image = file.getBytes();
        } catch (IOException e) {
            throw new ServiceException("图片读取失败，请重新拍照：" + e.getMessage());
        }
        // 图片只在这里的内存里活着：识别完即弃，不落 OSS、不进业务表
        return R.ok(ocrRecognizeService.recognize(image, stubCase));
    }

    /**
     * 扩展名（小写；无扩展名 → 空串）。
     */
    static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

}
