package org.dromara.lqg.ocr.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试桩 provider（<b>只允许 dev / test</b>）：按请求头 {@code X-Ocr-Stub-Case} 的前两位，
 * 从 classpath 的 {@code ocr-cases.json} 里取那一组 {@code rawLines}；<b>没带这个头时取
 * {@link #DEFAULT_CASE} 号样例</b>。
 *
 * <p>★ 为什么缺头时给缺省样例而不是报错（F4 修复，独立验收 V10 / 合同功能对照 G1）：
 * 小程序真机与 H5 都不会发 {@code X-Ocr-Stub-Case}（那是验收脚本专用的头），
 * 旧实现缺头就抛「测试桩需要请求头…」，本机与试用环境里<b>永远走不到预填流程</b>，
 * 甲方试用时只能看到一句报错。现在缺头返回第 01 号样例（印刷标签，六个字段齐全），
 * 拍照 → 识别 → 预填 → 标「请核对」整条链路在 dev / test 都能演示；
 * 验收脚本照旧用请求头逐例取（accept 1 不受影响）。prod 下本类不存在（{@code @Profile}），
 * 配成 stub 仍被 {@link org.dromara.lqg.ocr.guard.OcrProviderGuard} 拒绝启动。
 *
 * <p>★ 为什么需要它：本票<b>不接真实识别服务</b>（ticket §3，等 OCR-SPIKE-001），
 * 但 accept 1 必须能逐例验证「解析规则对噪声不猜」—— 于是由测试桩把 fixture 的文本行
 * 原样吐给解析器，链路（接口 → provider → 解析器 → 响应）还是真的。
 *
 * <p>★ 夹具从哪来：本模块 {@code src/main/resources/ocr-cases.json}（classpath 根）。它是
 * {@code doc/verify/fixtures/ocr-cases.json}（验收脚本读的那份）的<b>逐字节副本</b> ——
 * V31 起后端构建只靠后端目录自己，不再跨目录去拷；两份是否一致由 {@code FixtureCopiesSyncTest}
 * 在整仓检出时比对。解析器单测读的也是这一份。
 *
 * <p>★ prod 下配成 stub → <b>应用拒绝启动</b>（{@link org.dromara.lqg.ocr.guard.OcrProviderGuard}，
 * 与 {@code MockLoginGuard} 同一思路）：否则生产上会返回测试用例的假文本、用户以为识别坏了。
 *
 * @author OCR-IMPL-001
 */
@Slf4j
// bean 名必须与配置里的 lqg.ocr.provider 值一致；注解值要编译期常量，所以这里写字面量
// "stub"（NAME 常量与它逐字相同，由 OcrProviderSelectionTest 钉住）。
@Component("stub")
@Profile({"dev", "test"})
public class StubOcrProvider implements OcrProvider {

    /**
     * 配在 {@code lqg.ocr.provider} 里的名字。
     */
    public static final String NAME = "stub";

    /**
     * 决定返回哪一组用例的请求头（ticket §2）。
     */
    public static final String CASE_HEADER = "X-Ocr-Stub-Case";

    /**
     * 没带 {@link #CASE_HEADER} 时返回的样例（第 01 号：印刷标签，姓名 / 性别 / 年龄 / 住院号 /
     * 组织类型 / 来源单位六项齐全，最能演示预填）。
     */
    public static final String DEFAULT_CASE = "01";

    /**
     * classpath 里的夹具名（本模块 main resources 里的那一份）。
     */
    static final String FIXTURE_RESOURCE = "ocr-cases.json";

    /**
     * {@code case 名前两位} → {@code rawLines}。
     */
    private final Map<String, List<String>> cases = new LinkedHashMap<>();

    /**
     * 夹具读取器：**故意用裸 Jackson 而不是 {@code JsonUtils}** —— 后者的静态初始化会向
     * hutool 的 {@code SpringUtil} 要 bean 工厂，纯单测（不起容器）里会
     * {@code ExceptionInInitializerError}。夹具是构建期产物、结构固定，不需要 Spring 里的
     * 那套定制（时间格式 / Long 序列化），裸 mapper 更稳。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public StubOcrProvider() {
        loadFixture();
    }

    /**
     * 读夹具并建索引。夹具缺失 / 坏掉时**不抛**（否则 dev 环境起不来），
     * 只打一条 ERROR，之后任何 case 都返回空行 → 解析器产出空字段。
     */
    private void loadFixture() {
        try (InputStream in = StubOcrProvider.class.getClassLoader().getResourceAsStream(FIXTURE_RESOURCE)) {
            if (in == null) {
                log.error("识别测试桩找不到夹具 {}（应在 ruoyi-lqg 的 src/main/resources/ 下）", FIXTURE_RESOURCE);
                return;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Fixture fixture = MAPPER.readValue(text, Fixture.class);
            if (fixture == null || fixture.cases == null) {
                log.error("识别测试桩夹具 {} 解析为空", FIXTURE_RESOURCE);
                return;
            }
            for (FixtureCase item : fixture.cases) {
                if (item != null && item.caseName != null && item.rawLines != null
                    && item.caseName.length() >= 2) {
                    cases.put(item.caseName.substring(0, 2), item.rawLines);
                }
            }
            log.info("识别测试桩已就绪：{} 组用例（{}）", cases.size(), FIXTURE_RESOURCE);
        } catch (IOException | RuntimeException e) {
            log.error("识别测试桩夹具 {} 读取失败：{}", FIXTURE_RESOURCE, e.getMessage());
        }
    }

    /**
     * 当前桩里认得的 case 前缀（按夹具顺序；测试与排查用）。
     */
    public List<String> caseKeys() {
        return List.copyOf(cases.keySet());
    }

    @Override
    public List<String> recognize(byte[] image) {
        return List.of();
    }

    /**
     * 按 case 前缀取原始文本行：没给（{@code null} / 空白）→ {@link #DEFAULT_CASE}；
     * 认不得的前缀 → 空列表（解析器产出空字段，前端提示「没识别出来，请手动填写」）。
     */
    public List<String> recognize(String caseKey) {
        String key = caseKey == null || caseKey.isBlank() ? DEFAULT_CASE : caseKey.strip();
        if (caseKey == null || caseKey.isBlank()) {
            log.debug("识别测试桩：请求没带 {}，返回缺省样例 {}", CASE_HEADER, DEFAULT_CASE);
        }
        List<String> lines = cases.get(key);
        return lines == null ? List.of() : lines;
    }

    /**
     * 夹具文件的外层结构（{@code ocr-cases.json}）。
     */
    static final class Fixture {
        public List<FixtureCase> cases;
    }

    /**
     * 一条用例。字段名与夹具里的键逐字一致（{@code case} 是 Java 关键字，用 {@code @JsonProperty} 映射）。
     */
    static final class FixtureCase {
        @com.fasterxml.jackson.annotation.JsonProperty("case")
        public String caseName;

        public List<String> rawLines;
    }

}
