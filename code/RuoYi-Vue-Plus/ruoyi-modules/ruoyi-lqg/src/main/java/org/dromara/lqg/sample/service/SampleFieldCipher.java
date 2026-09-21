package org.dromara.lqg.sample.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.encrypt.properties.EncryptorProperties;
import org.dromara.common.encrypt.utils.EncryptUtils;
import org.springframework.stereotype.Component;

/**
 * 供体姓名 / 住院号的加密读写（ADR-0006）。
 *
 * <p>★ <b>为什么不用框架 {@code @EncryptField} 拦截器</b>（本票的关键实现决定，也是唯一一处
 * 与 ticket §2.2 字面「加 {@code @EncryptField(algorithm = AlgorithmType.AES)}」不同的地方）：
 * {@code EncryptorManager.encrypt} 会把结果写成 <b>{@code "ENC_" + Base64}</b>，而
 * {@code doc/verify/seed/04-sample.sql} 与 ADR-0006 里的既有密文是<b>裸 Base64</b>，
 * 两套混用会让 seed 行读出来还是密文（accept 第 2 条要的是「库里的值 == openssl 独立算出的值」，
 * 且那些 seed 行必须能读出明文）。所以这里用同一套 AES/ECB/PKCS5 + 同一个口令**手工**加解密：
 * 写库前加密、读库后解密、查询值先加密再 {@code eq}。
 *
 * <p>★ 加密列<b>只支持精确匹配</b>：{@link #encrypt} 出来的结果每次相同（ECB 无 IV），所以
 * {@code WHERE donor_name = ?} 成立；反过来绝不做 LIKE —— 那等于全表解密后在内存里过滤，
 * 而且会把别的单位填过的内容联想出去（ticket §2.2 口径 4）。
 *
 * @author SAMPLE-MODEL-001
 */
@Component
@RequiredArgsConstructor
public class SampleFieldCipher {

    private final EncryptorProperties encryptorProperties;

    /**
     * 明文 → 密文（裸 Base64，无 {@code ENC_} 前缀）。
     *
     * @param plain 明文；空白原样返回（加密空串会得到一串无意义的密文）
     */
    public String encrypt(String plain) {
        if (StringUtils.isBlank(plain)) {
            return plain;
        }
        return EncryptUtils.encryptByAes(plain, password());
    }

    /**
     * 密文 → 明文。库里可能是 seed 灌进去的裸 Base64，也可能是历史遗留的 {@code ENC_} 前缀形态，
     * 两种都认。解不开时**原样返回**并留给上层判断，不抛异常把整个列表接口打挂。
     */
    public String decrypt(String cipher) {
        if (StringUtils.isBlank(cipher)) {
            return cipher;
        }
        String value = cipher.startsWith("ENC_") ? cipher.substring(4) : cipher;
        try {
            return EncryptUtils.decryptByAes(value, password());
        } catch (Exception e) {
            return cipher;
        }
    }

    private String password() {
        String password = encryptorProperties.getPassword();
        if (StringUtils.isBlank(password)) {
            throw new IllegalStateException(
                "mybatis-encryptor.password 未配置：供体姓名 / 住院号是加密列（ADR-0006），没有口令读写不了");
        }
        return password;
    }

}
