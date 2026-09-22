package org.dromara.lqg.doc.render;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 内容指纹的哈希函数（FLOW:F-DOC-01.step1 / FIELD:t_lqg_doc_file.content_hash：{@code sha256}）。
 *
 * <p>单独一个类是为了让「指纹怎么算」这件事**能单测**：{@code DocFingerprintTest} 不碰库、
 * 不起 Spring，直接对 {@link DocRenderModel#canonical()} 的字符串翻转一个字段看哈希变不变。
 *
 * @author DOC-RENDER-001
 */
public final class DocContentHash {

    private DocContentHash() {
    }

    /** SHA-256 → 64 位小写十六进制（{@code content_hash VARCHAR(64)} 正好装下）。 */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256；真没有就是环境坏了，不能静默退回别的算法（指纹换了 = 全量重出）
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
