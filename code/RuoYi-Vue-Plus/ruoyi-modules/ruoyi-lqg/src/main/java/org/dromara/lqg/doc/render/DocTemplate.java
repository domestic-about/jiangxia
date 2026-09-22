package org.dromara.lqg.doc.render;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三份 docx 模板的**唯一取用口**（{@code src/main/resources/lqg/doc-templates/}）。
 *
 * <pre>
 *   sample_qc.docx  organoid_qc.docx  organoid_score.docx   ← 由甲方原件改占位符得到
 *   template-version.txt                                   ← 模板版本号（从 1 起）
 * </pre>
 *
 * <p>★★ <b>模板是唯一的版式来源</b>（ADR-0005）：产物长得跟甲方样张一样，靠的就是这三份
 * 从原件改出来的 docx（见 {@code doc/waves/reports/DOC-RENDER-001/make-doc-templates.py}），
 * 不是代码里拿 HTML/表格重画的。改模板 = 换文件 + {@code template-version.txt} 加一，
 * 指纹随之失效、下次渲染自动重出（REQ-DOC-011 的 clarify 口径）。
 *
 * <p>★ 模板文件打进了 jar（{@code src/main/resources}），所以**在 IDE 里改完不重新打包
 * 不会生效** —— 这正是要的：版本号与文件同生共死，不会出现「代码说 v2、jar 里还是 v1」。
 *
 * @author DOC-RENDER-001
 */
public final class DocTemplate {

    /** 模板目录（classpath）。 */
    public static final String DIR = "lqg/doc-templates/";

    /** 模板版本号文件。 */
    public static final String VERSION_RESOURCE = DIR + "template-version.txt";

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private DocTemplate() {
    }

    /**
     * 当前模板版本号。文件缺失 / 为空 = 打包坏了，直接 500（不能静默用 ""，
     * 那会让「升了模板版本」在指纹里消失）。
     */
    public static String version() {
        byte[] raw = readResource(VERSION_RESOURCE);
        if (raw == null) {
            throw new ServiceException("模板版本文件不存在：" + VERSION_RESOURCE, 500);
        }
        String version = new String(raw, StandardCharsets.UTF_8).trim();
        if (StringUtils.isBlank(version)) {
            throw new ServiceException("模板版本文件是空的：" + VERSION_RESOURCE, 500);
        }
        return version;
    }

    /**
     * 某个种类的模板字节（{@code merged} 没有自己的模板 → 400）。
     */
    public static byte[] bytes(String docKind) {
        if (!DocKinds.TEMPLATED.contains(docKind)) {
            throw new ServiceException("「" + docKind + "」没有独立模板", 400);
        }
        return CACHE.computeIfAbsent(docKind, k -> {
            byte[] raw = readResource(DIR + k + ".docx");
            if (raw == null || raw.length == 0) {
                throw new ServiceException("模板文件不存在：" + DIR + k + ".docx", 500);
            }
            return raw;
        });
    }

    private static byte[] readResource(String path) {
        try (InputStream in = DocTemplate.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new ServiceException("读模板资源失败：" + path + "（" + e.getMessage() + "）", 500);
        }
    }
}
