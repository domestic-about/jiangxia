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
 *   template-version.txt                                   ← 模板版本号（从 1 起，当前 6）
 *   ole-icons/*.png                                        ← 嵌入附件的图标（PDF / 图片 / Word / Excel / 其它）
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
 * <p>★ <b>v4（G 批 C 组，甲方 2026-09-24 意见第 24 / 26 行）</b>：重新从甲方原件生成（生成脚本
 * {@code make_doc_templates.py} 随 C 组交付），与 v3 的区别 ——
 * <ul>
 *   <li>字体<b>保留原件的宋体 / Times New Roman</b>（v3 直接写成了 Noto Serif SC / Tinos，甲方电脑上没有这两个字体，
 *       下载的 Word 打开来反而不像原件）；转 PDF 时由 {@code PdfFonts} 在转换副本上换成容器里的开源字体；</li>
 *   <li>样本质控表的三个图片行「不跨页断开」（放了图的那一行整行挪页，不再一张图在上一页、一张在下一页）；</li>
 *   <li>评分表去掉末尾的空段落（加了合计行之后它被挤到第二页，单独导出多一张空白页）。</li>
 * </ul>
 * 表格结构、列宽、行高、边框、页边距、纸张、两句「注」与原件逐字节一致（{@code DocTemplateContractTest}）。
 *
 * <p>★ <b>v5（H 批 H4 组，Kevin 本机验收「网页工作台」第 4、5、7 行）</b>：三份 docx 与 v4 相同，变的是渲染时的版式规则
 * —— 版本号管的是「模板 + 版式规则」，它进指纹，升了之后已完成的文档照给旧版、后台按新规则重出（{@code DocRenderService#isOutdated}）：
 * <ul>
 *   <li>表格里的段落行距固定、不对齐文档网格（转 PDF 的 LibreOffice 不再把一行字排成两格高），
 *       填值的格子放不下时自动缩小字号、不撑高格子（{@link DocCellLayout} / {@link DocCellFit}）；</li>
 *   <li>填值的格子一律水平、垂直居中；</li>
 *   <li>「细胞活率测定」一格嵌入附件本身（图标 + 文件名，Word / WPS 里双击打开，{@link DocOleEmbedder}）。</li>
 * </ul>
 *
 * <p>★ <b>v6（CR-20261009-18，甲方 2026-10-09 要求样本质控表加「种属」）</b>：样本质控表在「性别 / 临床诊断」行后
 * 多一行「种属 | {@code {{species}}}」，照抄「收样描述」那一行的格式（左格标签 + 右格跨 5 列），
 * 改法见 {@code doc/waves/reports/CR-20261009-18/add-species-row.py}；另两份模板不变。
 *
 * @author DOC-RENDER-001 · G 批 C 组（v4）· H 批 H4 组（v5）· CR-20261009-18（v6）
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
