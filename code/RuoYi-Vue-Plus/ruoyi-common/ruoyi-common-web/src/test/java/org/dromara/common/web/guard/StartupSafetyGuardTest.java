package org.dromara.common.web.guard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动护栏（V13 / V14 / V15 / V16）的规则逐条钉住。
 */
class StartupSafetyGuardTest {

    /**
     * 若依公开的那对 512 位密钥（application.yml 里原样写着）。
     */
    static final String UPSTREAM_PUBLIC_KEY = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJnNwrj4hi/y3CCJu868ghCG5dUj8wZK++RNlTLcXoMmdZWEQ/u02RgD5LyLAXGjLOjbMtC+/J9qofpSGTKSx/MCAwEAAQ==";

    static final String UPSTREAM_PRIVATE_KEY = "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAqhHyZfSsYourNxaY7Nt+PrgrxkiA50efORdI5U5lsW79MmFnusUA355oaSXcLhu5xxB38SMSyP2KvuKNPuH3owIDAQABAkAfoiLyL+Z4lf4Myxk6xUDgLaWGximj20CUf+5BKKnlrK+Ed8gAkM0HqoTt2UZwA5E2MzS4EI2gjfQhz5X28uqxAiEA3wNFxfrCZlSZHb0gn2zDpWowcSxQAgiCstxGUoOqlW8CIQDDOerGKH5OmCJ4Z21v+F25WaHYPxCFMvwxpcw99EcvDQIgIdhDTIqD2jfYjPTY8Jj3EDGPbH2HHuffvflECt3Ek60CIQCFRlCkHpi7hthhYhovyloRYsM+IS9h/0BzlEAuO0ktMQIgSPT3aFAgJYwKpqRYKlLDVcflZFCKY7u3UP8iWi1Qw0Y=";

    static String strongPublicKey;

    static String strongPrivateKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        strongPublicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        strongPrivateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }

    /**
     * 一套合格的生产配置（在它上面逐项弄坏）。
     */
    private static Map<String, String> goodProd() {
        Map<String, String> map = new HashMap<>();
        map.put("sa-token.jwt-secret-key", "3f9c2a7b1e5d8c4a6b0e2f7d9c1a3b5e7f9d2c4a6b8e0f1d3c5a7b9e1f3d5c7a");
        map.put("api-decrypt.enabled", "true");
        map.put("api-decrypt.publicKey", strongPublicKey);
        map.put("api-decrypt.privateKey", strongPrivateKey);
        map.put("mybatis-encryptor.enable", "true");
        map.put("mybatis-encryptor.password", "Zq8wR2tY6uI0oP4aS7dF1gH5jK9lX3cV");
        return map;
    }

    private static List<String> check(String[] profiles, Map<String, String> properties) {
        return StartupSafetyGuard.check(profiles, key -> {
            String value = properties.get(key);
            if (value != null && value.startsWith("${")) {
                throw new IllegalArgumentException("Could not resolve placeholder '" + value.substring(2, value.length() - 1)
                    + "' in value \"" + value + "\"");
            }
            return value;
        });
    }

    private static void assertOneProblem(List<String> problems, String mustContain) {
        assertEquals(1, problems.size(), "应当恰好一条：" + problems);
        assertTrue(problems.get(0).contains(mustContain), "提示要点名「" + mustContain + "」：" + problems);
    }

    // ── V16：profile ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("V16：没声明 profile → 拒绝启动（不许把没配当成开发环境）")
    void noProfileIsRefused() {
        assertOneProblem(check(new String[0], Map.of()), "spring.profiles.active");
        assertOneProblem(check(null, Map.of()), "spring.profiles.active");
        assertOneProblem(check(new String[]{" "}, Map.of()), "spring.profiles.active");
    }

    @Test
    @DisplayName("dev / test：不查生产密钥（本地与测试环境照常起）")
    void devAndTestAreNotChecked() {
        Map<String, String> devLike = new HashMap<>(Map.of(
            "sa-token.jwt-secret-key", StartupSafetyGuard.UPSTREAM_JWT_SECRET,
            "api-decrypt.publicKey", UPSTREAM_PUBLIC_KEY,
            "mybatis-encryptor.enable", "true",
            "mybatis-encryptor.password", StartupSafetyGuard.TEST_ENCRYPT_PASSWORD));
        assertTrue(check(new String[]{"dev"}, devLike).isEmpty());
        assertTrue(check(new String[]{"test"}, devLike).isEmpty());
    }

    // ── 生产：合格配置放行 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("prod：三把密钥都合格 → 放行")
    void goodProdPasses() {
        assertTrue(check(new String[]{"prod"}, goodProd()).isEmpty(), check(new String[]{"prod"}, goodProd()).toString());
    }

    @Test
    @DisplayName("prod 大小写 / 多 profile 同罪")
    void prodDetectionIsCaseInsensitive() {
        Map<String, String> bad = goodProd();
        bad.put("sa-token.jwt-secret-key", StartupSafetyGuard.UPSTREAM_JWT_SECRET);
        assertOneProblem(check(new String[]{"PROD"}, bad), "若依公开的默认值");
        assertOneProblem(check(new String[]{"dev", "prod"}, bad), "若依公开的默认值");
    }

    // ── V13：JWT ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("V13：JWT 密钥缺失（环境变量没给）/ 若依默认 / 太短 → 拒绝启动")
    void jwtSecretRules() {
        Map<String, String> missing = goodProd();
        missing.put("sa-token.jwt-secret-key", "${LQG_JWT_SECRET}");
        assertOneProblem(check(new String[]{"prod"}, missing), "LQG_JWT_SECRET");

        Map<String, String> upstream = goodProd();
        upstream.put("sa-token.jwt-secret-key", "abcdefghijklmnopqrstuvwxyz");
        assertOneProblem(check(new String[]{"prod"}, upstream), "若依公开的默认值");

        Map<String, String> tooShort = goodProd();
        tooShort.put("sa-token.jwt-secret-key", "short-secret");
        assertOneProblem(check(new String[]{"prod"}, tooShort), "太短");
    }

    // ── V14：接口加解密 RSA ───────────────────────────────────────────────────────

    @Test
    @DisplayName("V14：RSA 密钥缺失 / 若依公开的 512 位 / 不是合法密钥 → 拒绝启动；开关关着则不查")
    void apiCryptoRules() {
        Map<String, String> missing = goodProd();
        missing.put("api-decrypt.publicKey", "${LQG_API_RESPONSE_PUBLIC_KEY}");
        assertOneProblem(check(new String[]{"prod"}, missing), "LQG_API_RESPONSE_PUBLIC_KEY");

        Map<String, String> upstream = goodProd();
        upstream.put("api-decrypt.publicKey", UPSTREAM_PUBLIC_KEY);
        upstream.put("api-decrypt.privateKey", UPSTREAM_PRIVATE_KEY);
        List<String> problems = check(new String[]{"prod"}, upstream);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.contains("512 位")), problems.toString());

        Map<String, String> garbage = goodProd();
        garbage.put("api-decrypt.privateKey", "not-a-key");
        assertOneProblem(check(new String[]{"prod"}, garbage), "LQG_API_REQUEST_PRIVATE_KEY");

        Map<String, String> swapped = goodProd();
        swapped.put("api-decrypt.privateKey", strongPublicKey);
        assertOneProblem(check(new String[]{"prod"}, swapped), "PKCS#8");

        Map<String, String> disabled = goodProd();
        disabled.put("api-decrypt.enabled", "false");
        disabled.put("api-decrypt.publicKey", "${LQG_API_RESPONSE_PUBLIC_KEY}");
        disabled.put("api-decrypt.privateKey", UPSTREAM_PRIVATE_KEY);
        assertTrue(check(new String[]{"prod"}, disabled).isEmpty(), "接口加密关着时不查这两把");
    }

    // ── V15：字段加密口令 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("V15：字段加密口令缺失 / 等于测试口令 / 长度不是 16|24|32 → 拒绝启动")
    void fieldEncryptionRules() {
        Map<String, String> missing = goodProd();
        missing.put("mybatis-encryptor.password", "${LQG_ENCRYPT_PASSWORD}");
        assertOneProblem(check(new String[]{"prod"}, missing), "LQG_ENCRYPT_PASSWORD");

        Map<String, String> testKey = goodProd();
        testKey.put("mybatis-encryptor.password", "LqgTestAesKey#01");
        assertOneProblem(check(new String[]{"prod"}, testKey), "测试口令");

        Map<String, String> badLength = goodProd();
        badLength.put("mybatis-encryptor.password", "only-17-chars-xyz");
        assertOneProblem(check(new String[]{"prod"}, badLength), "16 / 24 / 32");
    }

    @Test
    @DisplayName("缺的不止一样时一次全报出来（部署时不用一轮一轮试）")
    void reportsEverythingAtOnce() {
        Map<String, String> none = new HashMap<>();
        none.put("sa-token.jwt-secret-key", "${LQG_JWT_SECRET}");
        none.put("api-decrypt.publicKey", "${LQG_API_RESPONSE_PUBLIC_KEY}");
        none.put("api-decrypt.privateKey", "${LQG_API_REQUEST_PRIVATE_KEY}");
        none.put("mybatis-encryptor.enable", "true");
        none.put("mybatis-encryptor.password", "${LQG_ENCRYPT_PASSWORD}");
        assertEquals(4, check(new String[]{"prod"}, none).size());
    }

    // ── 接线：EnvironmentPostProcessor 真的会抛 ────────────────────────────────────────

    @Test
    @DisplayName("接线：postProcessEnvironment 对不合格的环境抛 IllegalStateException，原因写在 message 里")
    void postProcessorRefuses() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("t", Map.of(
            "sa-token.jwt-secret-key", "${LQG_JWT_SECRET_THAT_IS_NOT_SET}")));
        environment.setActiveProfiles("prod");
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new StartupSafetyGuard().postProcessEnvironment(environment, new SpringApplication()));
        assertTrue(error.getMessage().contains("LQG_JWT_SECRET_THAT_IS_NOT_SET"), error.getMessage());
        assertTrue(error.getMessage().startsWith("启动护栏：拒绝启动"), error.getMessage());

        StandardEnvironment noProfile = new StandardEnvironment();
        assertThrows(IllegalStateException.class,
            () -> new StartupSafetyGuard().postProcessEnvironment(noProfile, new SpringApplication()));

        StandardEnvironment dev = new StandardEnvironment();
        dev.setActiveProfiles("dev");
        new StartupSafetyGuard().postProcessEnvironment(dev, new SpringApplication());
    }

}
