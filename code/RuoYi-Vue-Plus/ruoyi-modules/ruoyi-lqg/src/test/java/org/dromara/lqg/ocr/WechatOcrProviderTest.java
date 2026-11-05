package org.dromara.lqg.ocr;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.ocr.config.OcrProviderFactory;
import org.dromara.lqg.ocr.guard.OcrProviderGuard;
import org.dromara.lqg.ocr.provider.NoneOcrProvider;
import org.dromara.lqg.ocr.provider.OcrProvider;
import org.dromara.lqg.ocr.provider.PaidOcrProvider;
import org.dromara.lqg.ocr.provider.WechatOcrProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信官方通用印刷体识别的纯逻辑：响应解析、错误码映射、超大图缩小，以及「它不是付费实现」的选择口径。
 * （HTTP 本身不在单测里打微信，避免耗免费额度；真实链路上线后用真实照片验。）
 *
 * @author OCR-IMPL-001
 */
class WechatOcrProviderTest {

    /** parse / fitForWechat 是包内静态方法（provider 在别的包），用反射取，不为测试放宽可见性。 */
    private static Object parse(String json) throws Exception {
        Class<?> c = Class.forName("org.dromara.lqg.ocr.provider.WechatOcrProvider");
        Method m = c.getDeclaredMethod("parse", String.class);
        m.setAccessible(true);
        return m.invoke(null, json);
    }

    private static Object field(Object result, String name) throws Exception {
        var f = result.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("成功响应：按 items 顺序取 text，去掉首尾空白与空串")
    void parsesItemsInOrder() throws Exception {
        String json = "{\"errcode\":0,\"errmsg\":\"ok\",\"items\":["
            + "{\"text\":\"姓名：张三\",\"pos\":{}},{\"text\":\"  \"},{\"text\":\" 住院号 12345 \"}],\"img_size\":{\"w\":10,\"h\":10}}";
        Object r = parse(json);
        assertEquals(List.of("姓名：张三", "住院号 12345"), (List<String>) field(r, "lines"));
        assertEquals(null, field(r, "error"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("没识别出文字（items 为空）= 空列表，不是异常")
    void emptyItemsIsEmptyList() throws Exception {
        Object r = parse("{\"errcode\":0,\"errmsg\":\"ok\",\"items\":[]}");
        assertTrue(((List<String>) field(r, "lines")).isEmpty());
        assertEquals(null, field(r, "error"));
    }

    @Test
    @DisplayName("access_token 失效（40001 / 40014 / 42001）→ 标记可重试，用户提示仍是手填")
    void tokenInvalidIsRetryable() throws Exception {
        for (int code : new int[]{40001, 40014, 42001}) {
            Object r = parse("{\"errcode\":" + code + ",\"errmsg\":\"invalid credential\"}");
            assertEquals(true, field(r, "tokenInvalid"), "errcode " + code);
            assertNotNull(field(r, "error"));
        }
    }

    @Test
    @DisplayName("当日额度用完（45009）→ 明确告诉用户次数用完；其它错误码不透出 errcode")
    void quotaAndGenericMessages() throws Exception {
        ServiceException quota = (ServiceException) field(parse("{\"errcode\":45009,\"errmsg\":\"reach max api daily quota limit\"}"), "error");
        assertTrue(quota.getMessage().contains("识别次数已用完"));
        // 2026-11-05 生产 appid 实测：服务市场没购买时回 101003 not enough market quota
        ServiceException market = (ServiceException) field(parse("{\"errcode\":101003,\"errmsg\":\"not enough market quota\"}"), "error");
        assertTrue(market.getMessage().contains("识别次数已用完"));
        ServiceException other = (ServiceException) field(parse("{\"errcode\":47001,\"errmsg\":\"x\"}"), "error");
        assertEquals("识别失败，请手动填写", other.getMessage());
        assertFalse(other.getMessage().contains("47001"));
    }

    @Test
    @DisplayName("返回不是 JSON（网关错误页等）→ 手填提示，不是 500")
    void nonJsonIsGeneric() throws Exception {
        ServiceException e = (ServiceException) field(parse("<html>502</html>"), "error");
        assertEquals("识别失败，请手动填写", e.getMessage());
    }

    @Test
    @DisplayName("超过 2MB 的图在内存里缩到 2MB 内；小图原样不动")
    void oversizedImageIsShrunk() throws Exception {
        Class<?> c = Class.forName("org.dromara.lqg.ocr.provider.WechatOcrProvider");
        Method fit = c.getDeclaredMethod("fitForWechat", byte[].class);
        fit.setAccessible(true);

        byte[] small = new byte[]{1, 2, 3};
        assertTrue(small == fit.invoke(null, (Object) small), "小图应原样返回同一个数组");

        // 随机噪声压不动：用 PNG 造一张 > 1.9MB 的图
        BufferedImage img = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(7);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        byte[] big = bos.toByteArray();
        assertTrue(big.length > 1_900_000, "造图应 > 1.9MB，实际 " + big.length);

        byte[] out = (byte[]) fit.invoke(null, (Object) big);
        assertTrue(out.length <= 1_900_000, "缩小后应 ≤ 1.9MB，实际 " + out.length);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(out)), "缩小后仍是可解码的图");
    }

    @Test
    @DisplayName("选择口径：wechat 不是付费实现 → 不需要 paid-enabled 就选得中；名字如实报 wechat")
    void wechatIsSelectableWithoutPaidSwitch() {
        OcrProvider wechat = new WechatOcrProvider(null, 15);
        assertFalse(wechat instanceof PaidOcrProvider);
        Map<String, OcrProvider> providers = Map.of("none", new NoneOcrProvider(), "wechat", wechat);

        assertEquals(wechat, OcrProviderGuard.select("wechat", false, providers));
        assertEquals("wechat", OcrProviderFactory.nameOf(wechat));
        assertEquals(Optional.empty(), OcrProviderGuard.configError("wechat", false,
            Set.of("none", "wechat"), Set.of(), new String[]{"prod"}));
    }
}
