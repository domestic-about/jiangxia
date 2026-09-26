package org.dromara.lqg.doc.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 转 PDF 之前，在 docx 的<b>转换副本</b>上把甲方原件的字体换成 Gotenberg 容器里装着的开源字体。
 *
 * <p>★★ 为什么是「转换副本」而不是改模板（G 批 C 组，甲方 2026-09-24 意见第 24 行：
 * 「要求下载下来是我发给你的模板样子」）：
 * <ul>
 *   <li><b>下载的 Word</b> 保留原件的字体（宋体 / Times New Roman）—— 甲方在自己电脑上用 Word / WPS 打开，
 *       看到的就是他们模板本来的样子。此前模板里直接写的是 Noto Serif SC / Tinos，甲方电脑上没有这两个字体，
 *       Word 会拿别的字体顶替，打开来反而不像原件；</li>
 *   <li><b>PDF 与预览页面图</b>在服务器上出，服务器只有开源字体（已告知甲方）：宋体 → Noto Serif SC（思源宋体），
 *       Times New Roman → Tinos（与 Times New Roman 同度量，拉丁字母与数字宽度不变）。
 *       两套字体的中文都是等宽方块字、拉丁同度量，所以 Word 与 PDF 的换行、分页一致。</li>
 * </ul>
 *
 * <p>★ 替换规则与 DOC-PDF-001 的 {@code make-font-templates.py} 同一张表（它当年是直接改模板），
 * 区别只在于这里<b>按属性分</b>：{@code eastAsia} 一律 Noto Serif SC；{@code ascii / hAnsi / cs}
 * 原件是宋体一类的（评分表的分值格就是）→ Noto Serif SC，其余 → Tinos。主题字体
 * （{@code asciiTheme} 一类）展开成显式字体，{@code theme1.xml} 的 majorFont / minorFont 也改 ——
 * 不给 LibreOffice 留任何回退到 Liberation / DejaVu 的路（DOC-PDF-001 accept 断 {@code pdffonts} 里没有它们）。
 *
 * <p>★ 不是 docx（zip 解不开）就原样返回：本类只管换字体，判断「这是不是一份合法文档」是转换服务的事。
 *
 * @author G 批 C 组（样张 / 模板保真）
 */
public final class PdfFonts {

    /** 容器里的中文字体（思源宋体的 Google 发行版）。 */
    public static final String CJK_FONT = "Noto Serif SC";
    /** 容器里的拉丁字体（Times New Roman 度量兼容）。 */
    public static final String LATIN_FONT = "Tinos";

    /** 这些名字都按「宋体一类」处理（原件里 ascii 也写宋体的格子，数字照样用宋体一类的字形）。 */
    private static final Set<String> CJK_NAMES = Set.of("宋体", "SimSun", "NSimSun", "新宋体", "思源宋体",
        "Source Han Serif SC", "Noto Serif CJK SC", "Noto Serif SC", "仿宋", "FangSong", "等线", "DengXian");

    private static final Pattern RFONTS = Pattern.compile("<w:rFonts\\b[^>]*/?>");
    private static final Pattern ATTR = Pattern.compile("([\\w:]+)=\"([^\"]*)\"");

    private PdfFonts() {
    }

    /**
     * @param docx 渲染出来的 docx（原件字体）
     * @return 字体换成容器字体的副本；不是 docx 就原样返回
     */
    public static byte[] forConversion(byte[] docx) {
        if (docx == null || docx.length < 4 || docx[0] != 'P' || docx[1] != 'K') {
            return docx;
        }
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(docx));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream(docx.length + 1024)) {
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    byte[] data = in.readAllBytes();
                    String name = entry.getName();
                    if (name.endsWith(".xml") && name.startsWith("word/")) {
                        String xml = new String(data, StandardCharsets.UTF_8);
                        String rewritten = rewriteRunFonts(xml);
                        if (name.startsWith("word/theme/")) {
                            rewritten = rewriteTheme(rewritten);
                        }
                        if (!rewritten.equals(xml)) {
                            data = rewritten.getBytes(StandardCharsets.UTF_8);
                        }
                    }
                    out.putNextEntry(new ZipEntry(name));
                    out.write(data);
                    out.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (Exception e) {
            // 解不开就原样交给转换服务：它会给出「这不是一份能转的文档」的明确失败
            return docx;
        }
    }

    /** 每个 {@code <w:rFonts …/>} 重写成显式的容器字体（保留 {@code w:hint}）。 */
    public static String rewriteRunFonts(String xml) {
        Matcher m = RFONTS.matcher(xml);
        StringBuilder sb = new StringBuilder(xml.length());
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(rewriteRunFont(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String rewriteRunFont(String tag) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher a = ATTR.matcher(tag);
        while (a.find()) {
            attrs.put(a.group(1), a.group(2));
        }
        StringBuilder sb = new StringBuilder("<w:rFonts");
        if (attrs.containsKey("w:hint")) {
            sb.append(" w:hint=\"").append(attrs.get("w:hint")).append('"');
        }
        sb.append(" w:ascii=\"").append(latinFor(attrs.get("w:ascii"))).append('"');
        sb.append(" w:hAnsi=\"").append(latinFor(attrs.get("w:hAnsi"))).append('"');
        sb.append(" w:eastAsia=\"").append(CJK_FONT).append('"');
        sb.append(" w:cs=\"").append(latinFor(attrs.get("w:cs"))).append('"');
        return sb.append("/>").toString();
    }

    private static String latinFor(String original) {
        return original != null && CJK_NAMES.contains(original.trim()) ? CJK_FONT : LATIN_FONT;
    }

    /** 主题字体（majorFont / minorFont）：拉丁 → Tinos，东亚与简体中文 script → Noto Serif SC。 */
    static String rewriteTheme(String xml) {
        return xml
            .replaceAll("(<a:latin typeface=\")[^\"]*(\")", "$1" + LATIN_FONT + "$2")
            .replaceAll("(<a:cs typeface=\")[^\"]*(\")", "$1" + LATIN_FONT + "$2")
            .replaceAll("(<a:ea typeface=\")[^\"]*(\")", "$1" + CJK_FONT + "$2")
            .replaceAll("(<a:font script=\"Hans\" typeface=\")[^\"]*(\")", "$1" + CJK_FONT + "$2");
    }
}
