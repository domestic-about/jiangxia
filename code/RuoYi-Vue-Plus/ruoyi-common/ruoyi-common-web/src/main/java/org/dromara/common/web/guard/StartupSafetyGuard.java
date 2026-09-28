package org.dromara.common.web.guard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 启动护栏（上线安全加固，独立验收 V13 / V14 / V15 / V16）：在 Spring 容器创建之前
 * （连库、Flyway 迁移、打开端口之前）检查，不合格就<b>拒绝启动</b>并说清原因。
 *
 * <ol>
 *   <li><b>必须显式声明 profile</b>（V16）：jar 里不再写死缺省 profile；没声明就不启动，
 *       不许把「没配」当成开发环境（那会带着 mock 登录、测试密钥连上 LQG_DB_* 指向的库）。
 *       本机开发照旧 {@code --spring.profiles.active=dev}（qa-up.sh 已带），测试 / 生产由 compose 给。</li>
 *   <li><b>profile 含 prod 时</b>三把密钥必须来自环境变量、不许是公开的缺省值：
 *     <ul>
 *       <li>{@code sa-token.jwt-secret-key}（V13）：非空、≠ 若依默认 {@code abcdefghijklmnopqrstuvwxyz}、至少 32 个字符；</li>
 *       <li>接口加解密开着时 {@code api-decrypt.publicKey / privateKey}（V14）：非空、能解析成 RSA 密钥、
 *           不短于 2048 位（若依公开的那对是 512 位，自然被挡）；</li>
 *       <li>字段加密开着时 {@code mybatis-encryptor.password}（V15）：非空、≠ 测试口令 {@code LqgTestAesKey#01}、
 *           长度 16 / 24 / 32（AES 的硬要求；不满足的话第一次读写加密列才炸）。</li>
 *     </ul></li>
 * </ol>
 *
 * <p>★ 为什么是 {@link EnvironmentPostProcessor} 而不是 {@code ApplicationRunner}（mock 登录护栏用的那种）：
 * Runner 在容器完全起来之后才跑，那时 Flyway 已经对着目标库迁移过一遍了。密钥缺失 / profile 漏配
 * 这类问题要在碰任何外部资源之前挡住。
 *
 * <p>★ 本类的检查规则是纯函数 {@link #check(String[], PropertyLookup)}，单测直接喂属性表。
 *
 * @author F4（V13 / V14 / V15 / V16）
 */
public class StartupSafetyGuard implements EnvironmentPostProcessor, Ordered {

    /**
     * 若依（Sa-Token 示例）默认的 JWT 密钥。
     */
    static final String UPSTREAM_JWT_SECRET = "abcdefghijklmnopqrstuvwxyz";

    /**
     * dev / test 的字段加密口令（doc/verify/seed/ 的密文按它算的，写在仓库里）。
     */
    static final String TEST_ENCRYPT_PASSWORD = "LqgTestAesKey#01";

    /**
     * JWT 密钥最短长度（HS256 的密钥至少 256 位）。
     */
    static final int JWT_SECRET_MIN_LENGTH = 32;

    /**
     * 接口加解密 RSA 密钥最短位数。
     */
    static final int RSA_MIN_BITS = 2048;

    private static final Set<Integer> AES_KEY_LENGTHS = Set.of(16, 24, 32);

    /**
     * 属性读取（隔离 {@link Environment}，单测可以直接给一张表）。
     */
    @FunctionalInterface
    public interface PropertyLookup {
        /**
         * @return 属性值；没配返回 null
         * @throws IllegalArgumentException 值里的占位符解析不了（例如 {@code ${LQG_JWT_SECRET}} 而环境变量没给）
         */
        String get(String key);
    }

    @Override
    public int getOrder() {
        // 在 ConfigDataEnvironmentPostProcessor（加载 application*.yml、确定 profile）之后
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> problems = check(environment.getActiveProfiles(), environment::getProperty);
        if (!problems.isEmpty()) {
            String message = "启动护栏：拒绝启动（profile=" + Arrays.toString(environment.getActiveProfiles()) + "）\n  - "
                + String.join("\n  - ", problems);
            // 这时日志系统还没初始化：先直接写 stderr，保证 docker logs / 控制台一定看得到原因
            System.err.println(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * 护栏规则本体。
     *
     * @param activeProfiles 当前激活的 profile
     * @param properties     属性读取
     * @return 不合格项（空 = 放行）
     */
    public static List<String> check(String[] activeProfiles, PropertyLookup properties) {
        List<String> problems = new ArrayList<>();
        String[] profiles = activeProfiles == null ? new String[0] : activeProfiles;
        boolean declared = Arrays.stream(profiles).anyMatch(p -> p != null && !p.isBlank());
        if (!declared) {
            problems.add("没有声明 spring.profiles.active。jar 里不再带缺省 profile（V16）：本机开发用 "
                + "--spring.profiles.active=dev，测试 / 生产由 compose 给 test / prod。不许把「没配」当成开发环境。");
            return problems;
        }
        boolean prod = Arrays.stream(profiles)
            .filter(p -> p != null && !p.isBlank())
            .map(p -> p.trim().toLowerCase(Locale.ROOT))
            .anyMatch("prod"::equals);
        if (!prod) {
            return problems;
        }
        checkJwtSecret(properties, problems);
        checkApiCrypto(properties, problems);
        checkFieldEncryption(properties, problems);
        return problems;
    }

    private static void checkJwtSecret(PropertyLookup properties, List<String> problems) {
        Value secret = read(properties, "sa-token.jwt-secret-key");
        if (secret.error() != null || isBlank(secret.value())) {
            problems.add("sa-token.jwt-secret-key 没有配置：生产从环境变量 LQG_JWT_SECRET 注入（无缺省值）" + secret.hint());
        } else if (UPSTREAM_JWT_SECRET.equals(secret.value().trim())) {
            problems.add("sa-token.jwt-secret-key 还是若依公开的默认值：换成随机生成的强密钥（LQG_JWT_SECRET）");
        } else if (secret.value().trim().length() < JWT_SECRET_MIN_LENGTH) {
            problems.add("sa-token.jwt-secret-key 太短（" + secret.value().trim().length() + " 个字符）：至少 "
                + JWT_SECRET_MIN_LENGTH + " 个字符（LQG_JWT_SECRET）");
        }
    }

    private static void checkApiCrypto(PropertyLookup properties, List<String> problems) {
        Value enabled = read(properties, "api-decrypt.enabled");
        // application.yml 缺省开着；只有明确写 false 才跳过
        if (enabled.error() == null && "false".equalsIgnoreCase(trim(enabled.value()))) {
            return;
        }
        Value publicKey = read(properties, "api-decrypt.publicKey");
        if (publicKey.error() != null || isBlank(publicKey.value())) {
            problems.add("api-decrypt.publicKey（响应加密公钥）没有配置：生产从环境变量 LQG_API_RESPONSE_PUBLIC_KEY 注入"
                + publicKey.hint());
        } else {
            String error = rsaProblem(publicKey.value(), true);
            if (error != null) {
                problems.add("api-decrypt.publicKey（LQG_API_RESPONSE_PUBLIC_KEY）" + error);
            }
        }
        Value privateKey = read(properties, "api-decrypt.privateKey");
        if (privateKey.error() != null || isBlank(privateKey.value())) {
            problems.add("api-decrypt.privateKey（请求解密私钥）没有配置：生产从环境变量 LQG_API_REQUEST_PRIVATE_KEY 注入"
                + privateKey.hint());
        } else {
            String error = rsaProblem(privateKey.value(), false);
            if (error != null) {
                problems.add("api-decrypt.privateKey（LQG_API_REQUEST_PRIVATE_KEY）" + error);
            }
        }
    }

    private static void checkFieldEncryption(PropertyLookup properties, List<String> problems) {
        Value enabled = read(properties, "mybatis-encryptor.enable");
        if (enabled.error() == null && !"true".equalsIgnoreCase(trim(enabled.value()))) {
            return;
        }
        Value password = read(properties, "mybatis-encryptor.password");
        if (password.error() != null || isBlank(password.value())) {
            problems.add("mybatis-encryptor.password（字段加密口令）没有配置：生产从环境变量 LQG_ENCRYPT_PASSWORD 注入"
                + password.hint());
        } else if (TEST_ENCRYPT_PASSWORD.equals(password.value())) {
            problems.add("mybatis-encryptor.password 是仓库里的测试口令 LqgTestAesKey#01：生产必须独立生成（ADR-0006）");
        } else if (!AES_KEY_LENGTHS.contains(password.value().length())) {
            problems.add("mybatis-encryptor.password 长度 " + password.value().length()
                + " 不合法：AES 口令必须是 16 / 24 / 32 个字符（LQG_ENCRYPT_PASSWORD）");
        }
    }

    /**
     * RSA 密钥的问题（null = 没问题）：要 Base64 的 X.509（公钥）/ PKCS#8（私钥），不带 PEM 头尾；≥ 2048 位。
     */
    static String rsaProblem(String base64, boolean publicKey) {
        byte[] der;
        try {
            der = Base64.getMimeDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return "不是 Base64：要 " + (publicKey ? "X.509（SubjectPublicKeyInfo）" : "PKCS#8") + " 的 DER 再 Base64，不带 PEM 头尾";
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            BigInteger modulus = publicKey
                ? ((RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der))).getModulus()
                : ((RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der))).getModulus();
            int bits = modulus.bitLength();
            if (bits < RSA_MIN_BITS) {
                return "只有 " + bits + " 位（若依公开的默认密钥就是 512 位）：生产要求至少 " + RSA_MIN_BITS
                    + " 位，重新生成（code/deploy/prod/gen-secrets.sh）";
            }
            return null;
        } catch (Exception e) {
            return "解析不出 RSA " + (publicKey ? "公钥（要 X.509）" : "私钥（要 PKCS#8）") + "：" + e.getClass().getSimpleName();
        }
    }

    private static Value read(PropertyLookup properties, String key) {
        try {
            return new Value(properties.get(key), null);
        } catch (IllegalArgumentException e) {
            return new Value(null, e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 读到的值；占位符解析失败时 {@code error} 是解析器的原话（点名缺的是哪个环境变量）。
     */
    private record Value(String value, String error) {
        String hint() {
            return error == null ? "" : "（" + error + "）";
        }
    }

}
