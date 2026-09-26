package org.dromara.lqg.ocr.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * OCR 原始文本行 → 预填字段的**纯函数**解析器（FLOW:F-OCR-01.step3）。
 *
 * <p>★ <b>它不认识 provider、不连数据库、不抛业务异常</b>：入参是「识别出来的文本行 +
 * 当前 active 的单位名集合」，出参是「能确定的字段」。单测因此可以逐例喂
 * {@code ocr-cases.json}，不需要 Spring、不需要 Redis、不需要库。
 *
 * <p>★ <b>宁缺毋滥</b>（ticket §0 口径 3 与 fixture 的 {@code _doc}）：
 * <ul>
 *   <li><b>没有标签的裸数字一律不认</b> —— {@code 保持 2-8℃ 低温运输}、{@code 2026-09-17 10:30}
 *       里没有任何「年龄」「住院号」，fixture 第 03 例就是为此埋的（那两个数字不许被猜成字段）；</li>
 *   <li>年龄：标签值里 1-120 的整数（{@code 61 岁} → 61；{@code 2-8} 不是年龄）；</li>
 *   <li>性别：只认 男 / 女 → 字典 code {@code male / female}（不猜 unknown）；</li>
 *   <li>住院号：标签值里 5-20 位字母数字（可带连字符）；日期没有标签，不认；</li>
 *   <li>单位名：<b>整行与 active 单位名完全相等</b>才认 —— 不相似度、不包含
 *       （{@code 某某市第九医院} 不许硬凑成 A 医院，fixture 第 05 例）。</li>
 * </ul>
 *
 * <p>★ <b>#203：值到哪里为止</b>。送检单上一行常常并排好几个「标签：值」，其中不少标签
 * 并不是要预填的字段（床号、科室、诊断、日期……）。旧实现先把整行空白全部去掉再切，
 * {@code 姓名:张三 床号:12} 变成 {@code 姓名:张三床号:12}，而「床号」不在词表里 ——
 * 于是姓名被吃成 {@code 张三床号}。现在分三层把边界找回来：
 * <ol>
 *   <li><b>行内先按「空白 + 下一个标签冒号」切块</b>（{@link #chunks}）：空白后面紧跟
 *       「词表标签（允许字间排版空格）+ 冒号」，或者「前面已经有过冒号 + 值、后面是任意短标签 + 冒号」，
 *       这个空白就是字段分隔；标签内部的排版空格（{@code 住 院 号：}）不会被切开；</li>
 *   <li><b>「只作边界、不产字段」的停止标签</b>（{@link #STOP_LABELS}：床号、科室、诊断、日期……）：
 *       块内没有空白时（{@code 姓名:张三床号:12}）靠它把值截在标签前；它自己的值被消费掉、不出现在结果里；</li>
 *   <li><b>换行</b>：一条 rawLine 里带换行时按行拆开；「{@code 姓名：}」独占一行、值在下一行时，
 *       只有下一行是<b>单个无空白、无冒号、不以标签开头</b>的片段才取作值（宁缺毋滥）。</li>
 * </ol>
 *
 * <p>解析不出的字段<b>根本不放进返回 Map</b>（不是空串）：前端拿 {@code null} 才不预填，
 * 空串会被当成「识别出一个空值」照填（accept 1 counterfeit 第 4 条）。
 *
 * @author OCR-IMPL-001
 */
public final class OcrFieldParser {

    /**
     * 字段顺序（返回 Map 保持稳定顺序，便于前端与人工核对）+ 白名单（解析器不得产出表外的键）。
     */
    private static final List<String> FIELD_ORDER = List.of(
        "donorName", "gender", "age", "hospitalNo", "tissueType", "sourceUnitName");

    /**
     * 标签词表：字段 → 该字段的标签（一个字段可有多别名）。
     */
    private static final Map<String, List<String>> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("donorName", List.of("患者姓名", "供体姓名", "姓名"));
        LABELS.put("gender", List.of("性别"));
        LABELS.put("age", List.of("年龄"));
        LABELS.put("hospitalNo", List.of("住院号码", "住院号"));
        LABELS.put("tissueType", List.of("组织类型", "标本"));
    }

    /**
     * 停止标签：送检单 / 病理申请单上常见、但<b>不预填</b>的标签。它们只做两件事 ——
     * 给前一个字段的值当边界、把自己的值消费掉。
     *
     * <p>★ 只收「两个字以上、不会出现在姓名 / 组织类型 / 住院号值里」的词；
     * {@code 标本类型} / {@code 样本类型} 放在这里是为了压过字段标签 {@code 标本}
     * （否则 {@code 标本类型 组织} 会被读成组织类型 = {@code 类型组织}）。
     */
    static final List<String> STOP_LABELS = List.of(
        "床号", "床位", "科室", "送检科室", "科别", "病区", "病房",
        "临床诊断", "送检医生", "主治医生", "医生", "医师",
        "送检日期", "取材日期", "采样日期", "日期", "送检时间", "采集时间", "离体时间", "固定时间",
        "取材部位", "标本类型", "样本类型", "标本编号", "标本号", "样本编号",
        "病理号", "门诊号", "登记号", "病案号", "ID号", "条码",
        "联系电话", "电话", "身份证号", "地址", "民族", "职业", "婚否", "备注");

    /**
     * 标签（字段标签 + 停止标签）的最大长度（{@code 患者姓名} / {@code 住院号码} / {@code 送检科室} 都是 4）。
     */
    private static final int LABEL_MAX = Stream.concat(LABELS.values().stream().flatMap(List::stream),
            STOP_LABELS.stream())
        .mapToInt(String::length)
        .max()
        .orElse(4);

    /**
     * 全部标签（字段 + 停止），按长度倒序：同一位置上长的先匹配（{@code 住院号码} 压过 {@code 住院号}，
     * {@code 标本类型} 压过 {@code 标本}）。
     */
    private static final List<String> ALL_LABELS = Stream.concat(
            LABELS.values().stream().flatMap(List::stream), STOP_LABELS.stream())
        .sorted(Comparator.comparingInt(String::length).reversed())
        .toList();

    /**
     * 冒号形状：{@code 标签:值}。标签段（冒号前）可以带排版空格（{@code 住 院 号 码}），整段归一后必须是词表标签。
     */
    private static final Pattern COLON_FORM = Pattern.compile(
        "^([^:：]{1,10}?)[：:]\\s*(.*)$");

    /**
     * 冒号形状的标签段允许的最大长度（含排版空格：{@code 住 院 号 码} 归一前 5 个字符）。
     */
    private static final int COLON_LABEL_MAX = 10;

    /**
     * 全角冒号（与半角混用是常见排版；归一成半角后再解析）。
     */
    private static final char FULL_WIDTH_COLON = '：';

    /**
     * 空白：JDK 的 {@code \s} 只认 ASCII 空白，OCR 结果里常见的全角空格（U+3000）与不换行空格也要算。
     */
    private static final String WS = "[\\p{javaWhitespace}\\u00A0\\u2007\\u202F]";

    private static final Pattern WS_RUN = Pattern.compile(WS + "+");

    /**
     * 行内分块时「空白后面是一个任意短标签 + 冒号」（不在词表里的 {@code 日期:} / {@code 编号:} 之类）。
     * 标签里不许有空白与冒号 —— 带字间空格的只认词表标签（见 {@link #knownLabelEnd}）。
     */
    private static final Pattern ANY_LABEL_COLON = Pattern.compile(
        "^[^:\\p{javaWhitespace}\\u00A0\\u2007\\u202F]{1,6}:");

    /**
     * 年龄里的整数：{@code (?!\d)} 保证吃满（别退化成 {@code 56} → {@code 5}），
     * {@code (?![-\d])} 挡「数字-数字」（{@code 2-8} 是温度范围不是年龄）。
     */
    private static final Pattern AGE_DIGITS = Pattern.compile("^\\d{1,3}(?!-?\\d)");

    /**
     * 住院号：5-20 位字母 / 数字，允许中间的连字符（{@code ZY0000001} / {@code ZY-0000001}）。
     */
    private static final Pattern HOSPITAL_NO = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{4,19}$");

    /**
     * 姓名的最大长度（含少数民族长名，如「阿卜杜热合曼·买买提」10 个字符）；
     * 超长多半是切块失败把后面的内容一起吃了进来 —— 宁缺毋滥。
     */
    private static final int NAME_MAX = 20;

    private OcrFieldParser() {
    }

    /**
     * 解析。
     *
     * @param rawLines        识别服务返回的原始文本行（null / 空 → 空结果；单条里带换行会先按行拆开）
     * @param activeUnitNames 当前 active 的来源单位名（{@code t_lqg_source_unit.unit_name = active}；
     *                        null / 空 → 一定不返回 {@code sourceUnitName}）
     * @return 只含确定认得的字段，顺序同 {@link #FIELD_ORDER}
     */
    public static Map<String, String> parse(List<String> rawLines, Set<String> activeUnitNames) {
        Map<String, String> found = new LinkedHashMap<>();
        if (rawLines == null || rawLines.isEmpty()) {
            return found;
        }
        Set<String> units = activeUnitNames == null ? Set.of() : activeUnitNames;
        List<String> lines = splitLines(rawLines);
        // 上一行以「字段标签 + 冒号」收尾、没有值 → 值可能在下一行（{@code 姓名：} / {@code 张三}）
        String pendingField = null;
        for (String line : lines) {
            // ① 单位名：整行（只去首尾空白，**不动行内空格**）与 active 单位名完全相等才认
            if (units.contains(line)) {
                found.putIfAbsent("sourceUnitName", line);
                pendingField = null;
                continue;
            }
            // ② 上一行留下的「标签：」+ 本行是单个干净片段 → 取作值
            if (pendingField != null) {
                String field = pendingField;
                pendingField = null;
                if (isBareValueLine(line)) {
                    String value = valueOf(field, line);
                    if (value != null) {
                        found.putIfAbsent(field, value);
                    }
                    continue;
                }
            }
            // ③ 其余字段：值必须挂在词表标签后面；一行可以有好几个字段
            for (String chunk : chunks(line)) {
                pendingField = parseChunk(glue(chunk), found);
            }
        }
        // 白名单 + 稳定顺序
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String key : FIELD_ORDER) {
            String value = found.get(key);
            if (value != null) {
                ordered.put(key, value);
            }
        }
        return ordered;
    }

    /**
     * 解析一个已归一（无空白、冒号半角）的块，把认出的字段放进 {@code found}。
     *
     * <p>每一轮从剩余串开头取一个「标签 + 值」，三种形状按顺序试：
     * <ol>
     *   <li><b>词表标签 + 冒号</b>（{@code 性别:男年龄:56岁}）：值到下一个冒号 / 下一个标签为止；
     *       冒号后面紧跟另一个标签（{@code 姓名:性别:男}）或什么都没有 → 这个字段<b>没有值</b>，不猜；</li>
     *   <li><b>词表标签打头、没有冒号</b>（{@code 性别男}、{@code 姓名张三床号:12}）：松散形状；</li>
     *   <li>开头<b>不是词表标签</b>（不认识的标签 {@code 编号:A12姓名:王五}，或上一个值的尾巴
     *       {@code 岁住院号:…}）：跳到下一个词表标签接着解析；后面没有词表标签就收手。
     *       跳过的内容一个字段都不产 —— 值只认挂在词表标签后面的。</li>
     * </ol>
     *
     * @return 块以「字段标签 + 冒号」收尾且没有值时，返回那个字段名（值可能在下一行）；否则 null
     */
    private static String parseChunk(String glued, Map<String, String> found) {
        String rest = glued;
        while (!rest.isEmpty()) {
            Matcher colon = COLON_FORM.matcher(rest);
            boolean colonShape = colon.matches() && colon.group(1).length() <= COLON_LABEL_MAX;
            LabelHit hit;
            if (colonShape && isLabel(colon.group(1))) {
                String after = colon.group(2);
                if (after.isEmpty()) {
                    // `姓名:` 收尾、没有值：字段标签记下来交给下一行（停止标签不管）
                    return fieldOfLabel(colon.group(1));
                }
                if (startsWithLabel(after)) {
                    // `姓名:性别:男`：姓名没有值（宁缺毋滥），从下一个标签接着解析
                    rest = after;
                    continue;
                }
                hit = colonHit(colon.group(1), after);
            } else {
                hit = looseHit(rest);
            }
            if (hit == null) {
                // 开头不是词表标签：跳到下一个词表标签（没有就收手）
                int next = tailEnd(rest);
                if (next >= rest.length()) {
                    return null;
                }
                rest = rest.substring(next);
                continue;
            }
            if (hit.field() != null) {
                String value = valueOf(hit.field(), hit.rawValue());
                if (value != null) {
                    found.putIfAbsent(hit.field(), value);
                }
            }
            rest = rest.substring(Math.min(hit.consumed(), rest.length()));
        }
        return null;
    }

    /**
     * 剩余串是不是以某个标签（字段或停止标签）开头。
     */
    private static boolean startsWithLabel(String glued) {
        for (int len = Math.min(LABEL_MAX, glued.length()); len >= 1; len--) {
            if (isLabel(glued.substring(0, len))) {
                return true;
            }
        }
        return false;
    }

    /**
     * rawLines → 逐行（单条里带换行的拆开；去首尾空白；丢空行）。
     */
    static List<String> splitLines(List<String> rawLines) {
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split("\\R")) {
                String line = part.strip();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * 行内切块（#203 的第一层）：只在「空白 + 下一个标签冒号」处切，全角冒号先归一。
     *
     * <p>一个空白 {@code [ws, we)} 切不切：
     * <ul>
     *   <li>后面是<b>词表标签</b>（字间允许排版空格）+ 冒号 → 切。{@code 姓名:张三 床号:12}、
     *       {@code 年龄:56岁 住 院 号:ZY…}、{@code 性别:男  年龄:56岁} 都在这里分开；</li>
     *   <li>后面是<b>任意短标签</b>（1-6 个非空白字符）+ 冒号，且当前块里已经有过「冒号 + 值」→ 切。
     *       {@code 床号:12 日期:20260917} 在这里分开；而 {@code 住 院 号：} 的
     *       {@code 住 院} 里还没有冒号，所以 {@code 号：} 不会被当成新标签切出去。</li>
     * </ul>
     */
    static List<String> chunks(String line) {
        String text = line.replace(FULL_WIDTH_COLON, ':');
        List<String> result = new ArrayList<>();
        Matcher ws = WS_RUN.matcher(text);
        int chunkStart = 0;
        while (ws.find()) {
            int wsStart = ws.start();
            int wsEnd = ws.end();
            if (wsStart == 0 || wsEnd >= text.length()) {
                continue;
            }
            String after = text.substring(wsEnd);
            String current = text.substring(chunkStart, wsStart);
            boolean knownLabelNext = knownLabelEnd(after) > 0;
            boolean anyLabelNext = ANY_LABEL_COLON.matcher(after).find() && hasColonValue(current);
            if (knownLabelNext || anyLabelNext) {
                result.add(current);
                chunkStart = wsEnd;
            }
        }
        result.add(text.substring(chunkStart));
        return result;
    }

    /**
     * 块里是否已经有「冒号 + 至少一个非空白字符」（也就是正处在某个字段的值里）。
     */
    private static boolean hasColonValue(String chunk) {
        int colon = chunk.indexOf(':');
        return colon >= 0 && !chunk.substring(colon + 1).isBlank();
    }

    /**
     * 片段开头是不是「词表标签 + 可选空白 + 冒号」；标签的字与字之间允许排版空白。
     *
     * @return 冒号之后的下标；不是 → -1
     */
    static int knownLabelEnd(String text) {
        for (String label : ALL_LABELS) {
            int i = 0;
            int matched = 0;
            while (i < text.length() && matched < label.length()) {
                char c = text.charAt(i);
                if (matched > 0 && isSpace(c)) {
                    i++;
                    continue;
                }
                if (c != label.charAt(matched)) {
                    break;
                }
                matched++;
                i++;
            }
            if (matched < label.length()) {
                continue;
            }
            while (i < text.length() && isSpace(text.charAt(i))) {
                i++;
            }
            if (i < text.length() && text.charAt(i) == ':') {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' ';
    }

    /**
     * 「上一行 {@code 姓名：}、本行是值」时，本行必须是单个干净片段：
     * 没有空白、没有冒号、不以任何标签开头（否则多半是另一个字段或整句噪声，宁缺毋滥）。
     */
    private static boolean isBareValueLine(String line) {
        if (WS_RUN.matcher(line).find() || line.indexOf(':') >= 0 || line.indexOf(FULL_WIDTH_COLON) >= 0) {
            return false;
        }
        return !startsWithLabel(line);
    }

    /**
     * 冒号形状命中的标签：标签段已确定，值是冒号之后的片段 —— 先按 {@link #colonValue}
     * 切成「本字段的值」，再交给 {@link #valueOf} 校验。
     *
     * <p>★ 值<b>先切出来</b>再算消费位置，而不是在整行上算一个下标：整行下标同时要照顾
     * 「标签长度」与「下一个冒号的位置」两套坐标系，之前就是在这里把
     * {@code 住院号:ZY0000002床号:12} 的住院号算成了 {@code ZY0000002床号}。
     *
     * @param label 行首的标签段（可带排版空格，交给 {@link #fieldOfLabel} 归一）
     * @param value 冒号之后的剩余串（正则的 {@code group(2)}）
     * @return 命中（停止标签的命中 {@code field} 为 null：值被消费、不产字段）；
     *         标签不在词表里 / 没有值 → null
     */
    static LabelHit colonHit(String label, String value) {
        if (!isLabel(label) || value == null) {
            return null;
        }
        String raw = colonValue(value);
        if (raw.isEmpty()) {
            return null;
        }
        return new LabelHit(fieldOfLabel(label), raw, label.length() + 1 + raw.length());
    }

    /**
     * 冒号之后的「本字段的值」：从冒号后第一个字符算，到<b>停点</b>为止。
     *
     * <p>★ 停点 = 「下一个冒号」或「值里出现非 ASCII 字符（CJK 或空白）或行尾」，
     * 再与 {@link #tailEnd}（下一个词表标签，含停止标签）取更早的那个：
     * <ul>
     *   <li>{@code 住院号:ZY0000002床号:12} → 值 {@code ZY0000002}：{@code 床号} 是停止标签，
     *       即便不是，英文数字的值遇到中文也该停（accept 1 的第 04 例就是为它埋的）；</li>
     *   <li>{@code 性别:男年龄:56岁} → 值 {@code 男}：{@code 年龄} 是词表标签，切在它前面；</li>
     *   <li>{@code 姓名:张三床号:12} → 值 {@code 张三}：{@code 床号} 是停止标签（#203）；</li>
     *   <li>{@code 姓名:测试供体甲} → 值整段：名字里的中文<b>属于值</b>，所以中文不是无条件停点，
     *       只有「值已经出现过 ASCII」之后再遇中文才停（见 {@link #stopOf}）。</li>
     * </ul>
     */
    static String colonValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int nextColon = value.indexOf(':');
        int nextLabel = tailEnd(value);
        int stop = stopOf(value);
        int length = Math.min(Math.min(nextColon < 0 ? value.length() : nextColon, nextLabel), stop);
        return value.substring(0, Math.max(length, 0));
    }

    /**
     * 值里第一个「不该继续」的位置：已经出现过 ASCII 字母 / 数字之后遇到非 ASCII（中文标签 / 单位
     * 「岁」）就停；全是中文（姓名 / 组织类型）则不停，交给 {@link #tailEnd} 与词表标签决断。
     * ASCII 标点（括号、连字符）不算「出现过字母数字」：{@code 肝组织(右叶)} 整段是值。
     *
     * @return 停点下标；不该停 → 串长
     */
    static int stopOf(String value) {
        boolean seenAlnum = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 128) {
                if (c == ' ') {
                    return i;
                }
                if (Character.isLetterOrDigit(c)) {
                    seenAlnum = true;
                }
            } else if (seenAlnum) {
                return i;
            }
        }
        return value.length();
    }

    /**
     * 松散形状（无冒号）命中的标签：{@code 标签 + 值}，值到下一个词表标签为止。
     *
     * <p>★ 同一位置<b>长标签优先</b>：{@code 标本类型组织} 是停止标签 {@code 标本类型}，
     * 不是字段标签 {@code 标本} + 值 {@code 类型组织}。
     *
     * @param glued 已归一（无空白）的剩余串
     * @return 命中的 (字段, 值起止)（停止标签 → 字段为 null）；没有标签命中 / 标签就是整串（没有值）→ null
     */
    static LabelHit looseHit(String glued) {
        if (glued == null) {
            return null;
        }
        int max = Math.min(LABEL_MAX, glued.length() - 1);
        for (int len = max; len >= 1; len--) {
            String label = glued.substring(0, len);
            if (!isLabel(label)) {
                continue;
            }
            // 标签后面可能还带冒号（`年龄:56岁` 的 `年龄` 后紧跟着冒号）
            int start = glued.charAt(len) == ':' ? len + 1 : len;
            int end = start + tailEnd(glued.substring(start));
            if (end <= start) {
                return null;
            }
            return new LabelHit(fieldOfLabel(label), glued.substring(start, end), end);
        }
        return null;
    }

    /**
     * 值在片段里的结束位置：到「下一个标签（字段标签或停止标签）」为止，没有下一个标签就是片段全长。
     *
     * <p>{@code 男年龄:56岁} → 1（值是 {@code 男}，剩下的是 {@code 年龄:56岁}）；
     * {@code 张三床号:12} → 2；{@code 测试供体甲} → 全长（名字里没有标签）。
     */
    static int tailEnd(String segment) {
        if (segment == null) {
            return 0;
        }
        for (int at = 1; at < segment.length(); at++) {
            int max = Math.min(LABEL_MAX, segment.length() - at);
            for (int len = 1; len <= max; len++) {
                if (isLabel(segment.substring(at, at + len))) {
                    return at;
                }
            }
        }
        return segment.length();
    }

    /**
     * 命中。
     *
     * @param field    字段名；停止标签（床号 / 科室 …）为 null：值被消费、不进结果
     * @param rawValue 本字段的原始值片段（交给 {@link #valueOf} 校验；不含标签与分隔冒号）
     * @param consumed 剩余串里消费到的下标（下一个字段从 {@code consumed} 开始）
     */
    record LabelHit(String field, String rawValue, int consumed) {
    }

    /**
     * 标签段（已去掉冒号与两侧空白）→ 字段名。判等前去掉标签**内部**的空白：
     * {@code 性 别} / {@code 年 龄} / {@code 住 院 号} 都是排版噪声，不是别的标签。
     *
     * @return 词表里的字段名；不是字段标签（含停止标签）→ null
     */
    static String fieldOfLabel(String labelSegment) {
        String key = labelSegment.replaceAll(WS, "");
        for (Map.Entry<String, List<String>> entry : LABELS.entrySet()) {
            if (entry.getValue().contains(key)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 是不是标签（字段标签或停止标签；标签内部的排版空白不算）。
     */
    static boolean isLabel(String labelSegment) {
        if (fieldOfLabel(labelSegment) != null) {
            return true;
        }
        return STOP_LABELS.contains(labelSegment.replaceAll(WS, ""));
    }

    /**
     * 块归一：去掉全部空白（含全角空格）、全角冒号换半角。归一后 {@code 性 别 男} → {@code 性别男}、
     * {@code 年 龄 61 岁} → {@code 年龄61岁}。
     */
    static String glue(String raw) {
        return raw.replace(FULL_WIDTH_COLON, ':').replaceAll(WS, "");
    }

    /**
     * 标签后的片段（已归一、无空白）→ 合法字段值；不合法 → null（宁缺毋滥）。
     */
    static String valueOf(String field, String segment) {
        if (segment == null || segment.isEmpty()) {
            return null;
        }
        return accept(field, glue(segment));
    }

    /**
     * 单个候选值的合法性判定。
     */
    private static String accept(String field, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return switch (field) {
            case "donorName" -> validName(value);
            case "tissueType" -> value;
            case "gender" -> switch (value) {
                case "男" -> "male";
                case "女" -> "female";
                default -> null;
            };
            case "age" -> validAge(value);
            case "hospitalNo" -> HOSPITAL_NO.matcher(value).matches() ? value : null;
            default -> null;
        };
    }

    /**
     * 姓名：2-{@value #NAME_MAX} 个字符、不含数字与冒号，且不能是「男 / 女」这种被人名标签误吃进来的邻字段值。
     */
    private static String validName(String value) {
        if (value.length() < 2 || value.length() > NAME_MAX) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == ':') {
                return null;
            }
        }
        return switch (value) {
            case "男", "女", "未知" -> null;
            default -> value;
        };
    }

    /**
     * 年龄：1-120 的整数，<b>后面不许再跟数字或减号</b>。
     *
     * <p>★ 这两条负向前瞻都是踩出来的：
     * <ul>
     *   <li>{@code (?!\d)} —— 否则 {@code 121} 会被正则退成 {@code 1}、{@code 12}（数字本身
     *       不是这个值，就不能要）；</li>
     *   <li>{@code (?![-\d])} 叠在 3 位上不完整：{@code 2-8} 的 {@code 2} 后面是减号，
     *       必须一起挡掉 —— 那是温度范围不是年龄。</li>
     * </ul>
     */
    private static String validAge(String value) {
        Matcher matcher = AGE_DIGITS.matcher(value);
        if (!matcher.find() || matcher.start() != 0) {
            return null;
        }
        int age = Integer.parseInt(matcher.group());
        if (age < 1 || age > 120) {
            return null;
        }
        return String.valueOf(age);
    }

}
