package org.dromara.lqg.doc.render;

import org.apache.poi.hpsf.ClassIDPredefined;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dromara.common.core.exception.ServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 把附件<b>作为嵌入对象</b>放进 Word（Kevin 本机验收「网页工作台」第 5 行：「下载下来的 word 里，细胞活率测定里
 * 应该嵌套的是一个文件，要能点击打开」；甲方原件这一格本来就标着「表格里嵌了一个文件附件，显示一个图标，双击就能打开」）。
 *
 * <p>★★ 做法 = Word「插入 → 对象 → 由文件创建 → 显示为图标」产出的那种<b>OLE 包</b>（ProgID {@code Package}，
 * 任意类型的文件都能装，Word / WPS 双击即用系统里关联的程序打开）：
 * <ul>
 *   <li>{@code word/embeddings/oleObjectN.bin}：一个 OLE 复合文档（用 POI 的 POIFS 写），根存储的类 ID 是
 *       Package（{@code 0003000C-0000-0000-C000-000000000046}），里面两条流：
 *       {@code \u0001Ole10Native}（文件名 + 原文件字节，格式见 {@link #ole10Native}）与 {@code \u0001CompObj}
 *       （登记「这是一个 OLE Package」，见 {@link #COMP_OBJ}）；</li>
 *   <li>正文里一个 {@code <w:object>}：VML {@code v:shape} 显示图标图片（{@code word/media/}），
 *       {@code <o:OLEObject Type="Embed" ProgID="Package" DrawAspect="Icon" r:id=…/>} 指向上面那个 bin；</li>
 *   <li>文件名作为正文文字印在图标右边（图标里不画中文：服务器上不必为画图装中文字体，文字也能被检索、复制）。</li>
 * </ul>
 *
 * <p>★ POI 自带的 {@code Ole10Native#writeOut} 不能直接用：它把文件名按 UTF-8 写进 ANSI 字段、长度却按字符数记，
 * 中文文件名写出来的流是坏的，也不写 Unicode 文件名。这里按格式手写这条流：ANSI 字段用 GBK（中文 Windows 的
 * 系统代码页，编不了的字符换成下划线），尾部再带一份 UTF-16 的文件名（Windows 7 起的「对象包装程序」优先用它）。
 * 容器（复合文档）仍用 POIFS 写，读回用 POI 的 {@code Ole10Native} 解析（单测往返比对字节）。
 *
 * <p>★ 两步走：渲染时先在那一格放一个<b>记号</b>（{@link #marker}），合并件拼好之后（或单份渲染完）再由
 * {@link #embed} 把记号换成对象、补上部件与关系 —— poi-tl 拼合并件时不会搬嵌入对象，最后一步再嵌就不依赖它。
 *
 * <p>★ PDF：{@link #forConversion} 在转 PDF 的副本上把对象换成同一张图标图片（去掉嵌入的 bin）——
 * PDF 里是「图标 + 文件名」，转换服务不必解析嵌入对象，也不因为附件大而多传几十 MB。
 *
 * @author H 批 H4 组
 */
public final class DocOleEmbedder {

    private DocOleEmbedder() {
    }

    /** 单个附件嵌进 Word 的上限（20MB）：再大就只印文件名并提示去附件里下（Word 会跟着变得很大，合并件还要再装一份）。 */
    public static final long MAX_EMBED_BYTES = 20L * 1024 * 1024;

    /** 图标在文档里的显示尺寸（磅）：16 × 20，与五号 / 小四字的一行差不多高。 */
    public static final int ICON_WIDTH_PT = 16;
    public static final int ICON_HEIGHT_PT = 20;

    /** 图标所在那一段的行高下限（twips）：比图标高 2 磅，Word 与 LibreOffice 都不裁图标。 */
    public static final int ICON_LINE_TWIPS = (ICON_HEIGHT_PT + 2) * 20;

    /** 图标加它后面那个空格占的宽度（twips，估算折行用）。 */
    public static final int ICON_ADVANCE_TWIPS = ICON_WIDTH_PT * 20 + 60;

    /** 图标图片（{@code lqg/doc-templates/ole-icons/*.png}，生成脚本见 H4 组交付的 make_ole_icons.py）。 */
    private static final String ICON_DIR = DocTemplate.DIR + "ole-icons/";

    private static final String MARKER_HEAD = "\u27E6LQG-OLE:";
    private static final String MARKER_TAIL = "\u27E7";

    private static final String REL_OLE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";
    private static final String REL_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    private static final String CT_OLE = "application/vnd.openxmlformats-officedocument.oleObject";

    private static final Map<String, String> ROOT_NAMESPACES = new LinkedHashMap<>();

    static {
        ROOT_NAMESPACES.put("v", "urn:schemas-microsoft-com:vml");
        ROOT_NAMESPACES.put("o", "urn:schemas-microsoft-com:office:office");
        ROOT_NAMESPACES.put("r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");
    }

    /**
     * {@code \u0001CompObj} 流（MS-OLEDS 2.3.8）：版本头 + 类 ID（Package）+「OLE Package」+ 无剪贴板格式 +「Package」
     * + Unicode 标记与三个空的 Unicode 串 —— 与 Word 插入文件对象时写的字节一致。
     */
    static final byte[] COMP_OBJ = compObj();

    /**
     * 一个要嵌进 Word 的附件。
     *
     * @param marker   渲染时放在那一格里的记号（{@link #marker}）
     * @param fileName 显示的文件名（也是双击后打开的临时文件名）
     * @param data     原文件字节
     */
    public record Attachment(String marker, String fileName, byte[] data) {
    }

    /** 某一格的记号：一个不会出现在正常文字里的串（渲染完一定被换掉；换不掉单测会红）。 */
    public static String marker(String docKind, String slot) {
        return MARKER_HEAD + docKind + ":" + slot + MARKER_TAIL;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 嵌进 docx
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 把 docx 里的记号换成嵌入对象。没有附件就原样返回（字节不变）；某个记号找不到就跳过（记号本来就不在正文里）。
     */
    public static byte[] embed(byte[] docx, List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return docx;
        }
        try {
            Map<String, byte[]> parts = readZip(docx);
            String document = new String(parts.get("word/document.xml"), StandardCharsets.UTF_8);
            String rels = new String(parts.get("word/_rels/document.xml.rels"), StandardCharsets.UTF_8);
            String types = new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8);
            Map<String, byte[]> added = new LinkedHashMap<>();
            boolean first = !document.contains("id=\"_x0000_t75\"");
            for (Attachment attachment : attachments) {
                Matcher run = markerRun(attachment.marker()).matcher(document);
                if (!run.find()) {
                    continue;
                }
                int n = nextIndex(parts.keySet(), added.keySet(), "word/embeddings/oleObject", ".bin");
                String binPart = "word/embeddings/oleObject" + n + ".bin";
                String iconPart = "word/media/lqgOleIcon" + n + ".png";
                String oleRel = uniqueRelId(rels, "rIdLqgOle" + n);
                String iconRel = uniqueRelId(rels, "rIdLqgOleIcon" + n);
                added.put(binPart, packageObject(attachment.fileName(), attachment.data()));
                added.put(iconPart, icon(attachment.fileName()));
                rels = rels.replace("</Relationships>",
                    "<Relationship Id=\"" + oleRel + "\" Type=\"" + REL_OLE + "\" Target=\"embeddings/oleObject" + n + ".bin\"/>"
                        + "<Relationship Id=\"" + iconRel + "\" Type=\"" + REL_IMAGE + "\" Target=\"media/lqgOleIcon" + n + ".png\"/>"
                        + "</Relationships>");
                String replacement = "<w:r>" + runProperties(run.group()) + objectXml(n, oleRel, iconRel, first) + "</w:r>";
                document = document.substring(0, run.start()) + replacement + document.substring(run.end());
                first = false;
            }
            if (added.isEmpty()) {
                return docx;
            }
            document = withRootNamespaces(document);
            types = withDefault(types, "bin", CT_OLE);
            types = withDefault(types, "png", "image/png");
            parts.put("word/document.xml", document.getBytes(StandardCharsets.UTF_8));
            parts.put("word/_rels/document.xml.rels", rels.getBytes(StandardCharsets.UTF_8));
            parts.put("[Content_Types].xml", types.getBytes(StandardCharsets.UTF_8));
            parts.putAll(added);
            return writeZip(parts);
        } catch (IOException | RuntimeException e) {
            throw new ServiceException("把附件嵌进 Word 失败：" + e.getMessage(), 500);
        }
    }

    /**
     * 转 PDF 的副本：每个嵌入对象换成同一张图标图片（{@code w:pict} 里只留 {@code v:shape}），
     * 去掉 {@code word/embeddings/*.bin} 与指向它们的关系。不是 docx、没有嵌入对象就原样返回。
     */
    public static byte[] forConversion(byte[] docx) {
        if (docx == null || docx.length < 4 || docx[0] != 'P' || docx[1] != 'K') {
            return docx;
        }
        try {
            Map<String, byte[]> parts = readZip(docx);
            byte[] documentBytes = parts.get("word/document.xml");
            if (documentBytes == null) {
                return docx;
            }
            String document = new String(documentBytes, StandardCharsets.UTF_8);
            if (!document.contains("<w:object")) {
                return docx;
            }
            Matcher object = Pattern.compile("<w:object\\b[^>]*>(.*?)</w:object>", Pattern.DOTALL).matcher(document);
            StringBuilder sb = new StringBuilder();
            while (object.find()) {
                String inner = object.group(1).replaceAll("<o:OLEObject\\b[^>]*/>", "")
                    .replaceAll("\\s+o:ole=\"[^\"]*\"", "");
                object.appendReplacement(sb, Matcher.quoteReplacement("<w:pict>" + inner + "</w:pict>"));
            }
            object.appendTail(sb);
            parts.put("word/document.xml", sb.toString().getBytes(StandardCharsets.UTF_8));
            byte[] relsBytes = parts.get("word/_rels/document.xml.rels");
            if (relsBytes != null) {
                String rels = new String(relsBytes, StandardCharsets.UTF_8)
                    .replaceAll("<Relationship\\b[^>]*relationships/oleObject\"[^>]*/>", "");
                parts.put("word/_rels/document.xml.rels", rels.getBytes(StandardCharsets.UTF_8));
            }
            parts.keySet().removeIf(name -> name.startsWith("word/embeddings/") && name.endsWith(".bin"));
            return writeZip(parts);
        } catch (IOException | RuntimeException e) {
            // 副本做不出来就原样交给转换服务（它会如实报错），不在这里吞掉整次渲染
            return docx;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // OLE 包
    // ══════════════════════════════════════════════════════════════════════

    /** 一个装着 {@code data} 的 OLE Package 复合文档（{@code oleObjectN.bin} 的全部字节）。 */
    public static byte[] packageObject(String fileName, byte[] data) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem();
             ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 4096)) {
            DirectoryNode root = fs.getRoot();
            root.setStorageClsid(ClassIDPredefined.OLE_V1_PACKAGE.getClassID());
            root.createDocument("\u0001CompObj", new ByteArrayInputStream(COMP_OBJ));
            root.createDocument(Ole10Native.OLE10_NATIVE, new ByteArrayInputStream(ole10Native(fileName, data)));
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    /**
     * {@code \u0001Ole10Native} 流（「对象包装程序」的原生数据）：
     * <pre>
     *   总长(4) | 02 00 | 显示名 ANSI\0 | 源路径 ANSI\0 | 00 00 | 03 00（嵌入的文件）
     *   | 临时路径长度(4，含结尾 0) | 临时路径 ANSI\0 | 数据长度(4) | 数据
     *   | 临时路径字数(4) | 临时路径 UTF-16LE | 显示名字数(4) | 显示名 UTF-16LE | 源路径字数(4) | 源路径 UTF-16LE
     * </pre>
     * 三个名字都用文件名本身（不带目录）：双击时对象包装程序按它在临时目录里放一份再打开。
     */
    public static byte[] ole10Native(String fileName, byte[] data) {
        String name = safeName(fileName);
        byte[] ansi = ansi(name);
        ByteArrayOutputStream body = new ByteArrayOutputStream(data.length + 256);
        writeShort(body, 2);
        body.writeBytes(ansi);
        body.write(0);
        body.writeBytes(ansi);
        body.write(0);
        writeShort(body, 0);
        writeShort(body, 3);
        writeInt(body, ansi.length + 1);
        body.writeBytes(ansi);
        body.write(0);
        writeInt(body, data.length);
        body.writeBytes(data);
        byte[] unicode = name.getBytes(StandardCharsets.UTF_16LE);
        for (int i = 0; i < 3; i++) {
            writeInt(body, name.length());
            body.writeBytes(unicode);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.size() + 4);
        writeInt(out, body.size());
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    /** 从 {@code oleObjectN.bin} 里取回原文件字节（单测与核对样张用）。 */
    public static byte[] extract(byte[] bin) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bin))) {
            return Ole10Native.createFromEmbeddedOleObject(fs).getDataBuffer();
        } catch (org.apache.poi.poifs.filesystem.Ole10NativeException e) {
            throw new IOException("不是 OLE 包：" + e.getMessage(), e);
        }
    }

    /** 文件名里不能出现的字符（路径分隔、控制字符）换成下划线；空名字给一个缺省名。 */
    static String safeName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}:*?\"<>|]", "_");
        return name.isEmpty() ? "附件" : name;
    }

    private static byte[] ansi(String name) {
        try {
            CharsetEncoder gbk = Charset.forName("GBK").newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(new byte[] {'_'});
            ByteBuffer buf = gbk.encode(CharBuffer.wrap(name));
            byte[] out = new byte[buf.remaining()];
            buf.get(out);
            return out;
        } catch (Exception e) {
            return name.replaceAll("[^\\x20-\\x7E]", "_").getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static byte[] compObj() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0x01, 0x00, (byte) 0xFE, (byte) 0xFF, 0x03, 0x0A, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        // Package 的类 ID {0003000C-0000-0000-C000-000000000046}（小端序存储）
        out.writeBytes(new byte[] {0x0C, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46});
        lengthPrefixed(out, "OLE Package");
        writeInt(out, 0);
        lengthPrefixed(out, "Package");
        writeInt(out, 0x71B239F4);
        writeInt(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        return out.toByteArray();
    }

    private static void lengthPrefixed(ByteArrayOutputStream out, String ascii) {
        byte[] bytes = ascii.getBytes(StandardCharsets.US_ASCII);
        writeInt(out, bytes.length + 1);
        out.writeBytes(bytes);
        out.write(0);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 图标
    // ══════════════════════════════════════════════════════════════════════

    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp", "heic", "heif");
    private static final Set<String> WORD_EXT = Set.of("doc", "docx", "wps", "rtf", "odt", "txt");
    private static final Set<String> EXCEL_EXT = Set.of("xls", "xlsx", "et", "csv", "ods");

    /** 按扩展名挑图标：PDF / 图片 / Word / Excel / 其它。 */
    static String iconKind(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).trim();
        if ("pdf".equals(ext)) {
            return "pdf";
        }
        if (IMAGE_EXT.contains(ext)) {
            return "image";
        }
        if (WORD_EXT.contains(ext)) {
            return "word";
        }
        if (EXCEL_EXT.contains(ext)) {
            return "excel";
        }
        return "file";
    }

    static byte[] icon(String fileName) throws IOException {
        String path = ICON_DIR + iconKind(fileName) + ".png";
        try (InputStream in = DocOleEmbedder.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("图标资源不存在：" + path);
            }
            return in.readAllBytes();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // XML
    // ══════════════════════════════════════════════════════════════════════

    /** 记号所在的整个 run（{@code <w:r>…<w:t>记号</w:t></w:r>}）。 */
    private static Pattern markerRun(String marker) {
        return Pattern.compile("<w:r(?:\\s[^>]*)?>(?:(?!</w:r>).)*?<w:t(?:\\s[^>]*)?>" + Pattern.quote(marker)
            + "</w:t>(?:(?!</w:r>).)*?</w:r>", Pattern.DOTALL);
    }

    private static String runProperties(String run) {
        Matcher m = Pattern.compile("<w:rPr>.*?</w:rPr>", Pattern.DOTALL).matcher(run);
        return m.find() ? m.group() : "";
    }

    /**
     * Word 自己写的那种「显示为图标」的 OLE 对象（第一个对象带上 {@code _x0000_t75} 图片形状的定义）。
     */
    static String objectXml(int n, String oleRel, String iconRel, boolean withShapeType) {
        String shapeId = "_x0000_i" + (2000 + n);
        StringBuilder sb = new StringBuilder();
        sb.append("<w:object w:dxaOrig=\"").append(ICON_WIDTH_PT * 20).append("\" w:dyaOrig=\"")
            .append(ICON_HEIGHT_PT * 20).append("\">");
        if (withShapeType) {
            sb.append("<v:shapetype id=\"_x0000_t75\" coordsize=\"21600,21600\" o:spt=\"75\" o:preferrelative=\"t\"")
                .append(" path=\"m@4@5l@4@11@9@11@9@5xe\" filled=\"f\" stroked=\"f\">")
                .append("<v:stroke joinstyle=\"miter\"/><v:formulas>")
                .append("<v:f eqn=\"if lineDrawn pixelLineWidth 0\"/><v:f eqn=\"sum @0 1 0\"/><v:f eqn=\"sum 0 0 @1\"/>")
                .append("<v:f eqn=\"prod @2 1 2\"/><v:f eqn=\"prod @3 21600 pixelWidth\"/><v:f eqn=\"prod @3 21600 pixelHeight\"/>")
                .append("<v:f eqn=\"sum @0 0 1\"/><v:f eqn=\"prod @6 1 2\"/><v:f eqn=\"prod @7 21600 pixelWidth\"/>")
                .append("<v:f eqn=\"sum @8 21600 0\"/><v:f eqn=\"prod @7 21600 pixelHeight\"/><v:f eqn=\"sum @10 21600 0\"/>")
                .append("</v:formulas><v:path o:extrusionok=\"f\" gradientshapeok=\"t\" o:connecttype=\"rect\"/>")
                .append("<o:lock v:ext=\"edit\" aspectratio=\"t\"/></v:shapetype>");
        }
        sb.append("<v:shape id=\"").append(shapeId).append("\" type=\"#_x0000_t75\" style=\"width:")
            .append(ICON_WIDTH_PT).append("pt;height:").append(ICON_HEIGHT_PT).append("pt\" o:ole=\"\">")
            .append("<v:imagedata r:id=\"").append(iconRel).append("\" o:title=\"\"/></v:shape>")
            .append("<o:OLEObject Type=\"Embed\" ProgID=\"Package\" ShapeID=\"").append(shapeId)
            .append("\" DrawAspect=\"Icon\" ObjectID=\"_").append(1900000000 + n).append("\" r:id=\"").append(oleRel)
            .append("\"/></w:object>");
        return sb.toString();
    }

    /** 根元素上补齐 VML / Office / 关系三个命名空间（poi-tl 另存时可能把没用到的声明去掉）。 */
    private static String withRootNamespaces(String document) {
        Matcher root = Pattern.compile("<w:document\\b[^>]*>").matcher(document);
        if (!root.find()) {
            return document;
        }
        String tag = root.group();
        StringBuilder extra = new StringBuilder();
        for (Map.Entry<String, String> ns : ROOT_NAMESPACES.entrySet()) {
            if (!tag.contains("xmlns:" + ns.getKey() + "=")) {
                extra.append(" xmlns:").append(ns.getKey()).append("=\"").append(ns.getValue()).append('"');
            }
        }
        if (extra.length() == 0) {
            return document;
        }
        String patched = tag.substring(0, tag.length() - 1) + extra + ">";
        return document.substring(0, root.start()) + patched + document.substring(root.end());
    }

    private static String withDefault(String types, String extension, String contentType) {
        if (types.matches("(?si).*<Default\\s+Extension=\"" + extension + "\".*")) {
            return types;
        }
        return types.replaceFirst("<Types([^>]*)>",
            Matcher.quoteReplacement("<Types") + "$1" + Matcher.quoteReplacement(
                "><Default Extension=\"" + extension + "\" ContentType=\"" + contentType + "\"/>"));
    }

    private static String uniqueRelId(String rels, String wanted) {
        String id = wanted;
        for (int i = 2; rels.contains("Id=\"" + id + "\""); i++) {
            id = wanted + "_" + i;
        }
        return id;
    }

    private static int nextIndex(Set<String> existing, Set<String> added, String prefix, String suffix) {
        int n = 1;
        while (existing.contains(prefix + n + suffix) || added.contains(prefix + n + suffix)) {
            n++;
        }
        return n;
    }

    // ══════════════════════════════════════════════════════════════════════
    // zip
    // ══════════════════════════════════════════════════════════════════════

    private static Map<String, byte[]> readZip(byte[] docx) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                parts.put(entry.getName(), in.readAllBytes());
            }
        }
        if (!parts.containsKey("word/document.xml") || !parts.containsKey("[Content_Types].xml")
            || !parts.containsKey("word/_rels/document.xml.rels")) {
            throw new IOException("不是一份完整的 docx");
        }
        return parts;
    }

    private static byte[] writeZip(Map<String, byte[]> parts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            List<String> names = new ArrayList<>(parts.keySet());
            // [Content_Types].xml 放第一个（OPC 不强制，但 Word / WPS 与各种校验工具都习惯它在最前）
            names.remove("[Content_Types].xml");
            names.add(0, "[Content_Types].xml");
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                out.write(parts.get(name));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
