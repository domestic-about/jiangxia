package org.dromara.lqg.ocr.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 原始文本行 → 预填字段的**纯函数**解析器（FLOW:F-OCR-01.step3）。
 *
 * <p>★ <b>它不认识 provider、不连数据库、不抛业务异常</b>：入参是「识别出来的文本行 +
 * 当前 active 的单位名集合」，出参是「能确定的字段」。单测因此可以逐例喂
 * {@code doc/verify/fixtures/ocr-cases.json}，不需要 Spring、不需要 Redis、不需要库。
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
     * 全部标签，按长度倒序（{@code 患者姓名} 要压过 {@code 姓名}）。
     */
    private static final List<String> ALL_LABELS = LABELS.values().stream()
        .flatMap(List::stream)
        .sorted(Comparator.comparingInt(String::length).reversed())
        .toList();

    /**
     * 全部标签的正则交替（长的排前面），给「值到下一个标签为止」的前瞻用。
     *
     * <p>★ 前瞻里额外要求「标签前面不是字母数字」：{@code 性别:男年龄:56岁} 的值 {@code 男}
     * 后面紧跟 {@code 年龄} 且前面是中文，算标签边界（值到 {@code 男} 为止）；而
     * {@code 住院号:ZY0000002床号:12} 里 {@code 床号} 前面是数字 {@code 2}，**不算边界**
     * （住院号的值先整段拿到，再由 {@code valueOf} 按冒号截断 + 前缀收敛把 {@code ZY0000002} 取出来）。
     */
    private static final String LABEL_LOOKAHEAD = ALL_LABELS.stream()
        .map(Pattern::quote)
        .collect(java.util.stream.Collectors.joining("|", "(?<![\\p{Alnum}])(?:", ")"));

    /**
     * 冒号形状：{@code 标签:值}。值到<b>下一个词表标签或行尾</b>为止 ——
     * {@code 性别:男年龄:56岁} 里性别的值是 {@code 男}、年龄的值是 {@code 56岁}；
     * {@code 住院号:ZY0000002床号:12} 里 {@code 床号} 不是词表标签，于是住院号的值是
     * {@code ZY0000002床号:12}，交给 {@link #validHospitalNo} 用「5-20 位字母数字」把它挡掉。
     *
     * <p>标签段（冒号前）可以带排版空格（{@code 住 院 号 码}），整段归一后必须是词表标签。
     */
    private static final Pattern COLON_FORM = Pattern.compile(
        "^([^:：]{1,10}?)[：:]\\s*(.*)$");

    /**
     * 冒号形状的标签段允许的最大长度（含排版空格：{@code 住 院 号 码} 归一前 5 个字符）。
     */
    private static final int COLON_LABEL_MAX = 10;

    /**
     * 松散形状（无冒号）里标签的最大长度（示例：{@code 患者姓名} 4、{@code 住院号码} 4）。
     */
    private static final int LOOSE_LABEL_MAX = 6;

    /**
     * 全角冒号（与半角混用是常见排版；归一成半角后再解析）。
     */
    private static final char FULL_WIDTH_COLON = '：';

    /**
     * 年龄里的整数：{@code (?!\d)} 保证吃满（别退化成 {@code 56} → {@code 5}），
     * {@code (?![-\d])} 挡「数字-数字」（{@code 2-8} 是温度范围不是年龄）。
     */
    private static final Pattern AGE_DIGITS = Pattern.compile("^\\d{1,3}(?!-?\\d)");

    /**
     * 住院号：5-20 位字母 / 数字，允许中间的连字符（{@code ZY0000001} / {@code ZY-0000001}）。
     */
    private static final Pattern HOSPITAL_NO = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{4,19}$");

    private OcrFieldParser() {
    }

    /**
     * 解析。
     *
     * @param rawLines        识别服务返回的原始文本行（null / 空 → 空结果）
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
        for (String raw : rawLines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            // ① 单位名：整行（只去首尾空白，**不动行内空格**）与 active 单位名完全相等才认
            String line = raw.strip();
            if (units.contains(line)) {
                found.putIfAbsent("sourceUnitName", line);
                continue;
            }
            // ② 其余字段：值必须挂在词表标签后面。
            //    先去掉**全部**空白（`性 别 男` / `年 龄 61 岁` 是排版），再从左到右**逐个**摘标签：
            //    一行里可以有好几个字段（fixture 第 01 例 `性别：男  年龄：56岁`、第 04 例
            //    `住院号:ZY0000002 床号:12`），只认第一个是不够的。
            String rest = glue(line);
            while (!rest.isEmpty()) {
                Matcher colon = COLON_FORM.matcher(rest);
                // 带冒号 → 只走冒号形状：`性别:男年龄:56岁` 必须按标签切，不能退到
                // 「标签 + 值」的松散形状（那会把 `性别` 切成 `性` + `别男...`）
                LabelHit hit = colon.matches() && colon.group(1).length() <= COLON_LABEL_MAX
                    ? colonHit(colon.group(1), colon.group(2))
                    // 不带冒号 → 只走松散形状：`性 别 男` / `年 龄 61 岁`
                    : looseHit(rest);
                if (hit == null) {
                    break;
                }
                String value = valueOf(hit.field(), hit.rawValue());
                if (value != null) {
                    found.putIfAbsent(hit.field(), value);
                }
                rest = rest.substring(Math.min(hit.consumed(), rest.length()));
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
     * 冒号形状命中的标签：标签段已确定，值是冒号之后的片段 —— 先按 {@link #colonValue}
     * 切成「本字段的值」，再交给 {@link #valueOf} 校验。
     *
     * <p>★ 值<b>先切出来</b>再算消费位置，而不是在整行上算一个下标：整行下标同时要照顾
     * 「标签长度」与「下一个冒号的位置」两套坐标系，之前就是在这里把
     * {@code 住院号:ZY0000002床号:12} 的住院号算成了 {@code ZY0000002床号}。
     *
     * @param label 行首的标签段（可带排版空格，交给 {@link #fieldOfLabel} 归一）
     * @param value 冒号之后的剩余串（正则的 {@code group(2)}）
     * @return 命中；标签不在词表里 / 没有值 → null
     */
    static LabelHit colonHit(String label, String value) {
        String field = fieldOfLabel(label);
        if (field == null || value == null) {
            return null;
        }
        String raw = colonValue(value);
        if (raw.isEmpty()) {
            return null;
        }
        return new LabelHit(field, raw, label.length() + 1 + raw.length());
    }

    /**
     * 冒号之后的「本字段的值」：从冒号后第一个字符算，到<b>停点</b>为止。
     *
     * <p>★ 停点 = 「下一个冒号」或「值里出现非 ASCII 字符（CJK 或空白）或行尾」，
     * 再与 {@link #tailEnd}（下一个词表标签）取更早的那个：
     * <ul>
     *   <li>{@code 住院号:ZY0000002床号:12} → 值 {@code ZY0000002}：{@code 床号} 虽不在词表里，
     *       但它是中文 —— 英文数字的值遇到中文就该停（否则 {@code ZY0000002床号} 这种
     *       「字母数字」形状会被当成合法住院号，accept 1 的第 04 例就是为它埋的）；</li>
     *   <li>{@code 性别:男年龄:56岁} → 值 {@code 男}：{@code 年龄} 是词表标签，切在它前面；</li>
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
     * 值里第一个「不该继续」的位置：已经出现过 ASCII 之后遇到非 ASCII（中文标签 / 空白）就停；
     * 全是中文（姓名 / 组织类型）则不停，交给 {@link #tailEnd} 与词表标签决断。
     *
     * @return 停点下标；不该停 → 串长
     */
    static int stopOf(String value) {
        boolean seenAscii = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 128) {
                if (c == ' ') {
                    return i;
                }
                seenAscii = true;
            } else if (seenAscii) {
                return i;
            }
        }
        return value.length();
    }

    /**
     * 松散形状（无冒号）命中的标签：{@code 标签 + 值}，值到下一个词表标签为止。
     *
     * @param glued 已归一（无空白）的剩余串
     * @return 命中的 (字段, 值起止)；没有标签命中 / 标签就是整串（没有值）→ null
     */
    static LabelHit looseHit(String glued) {
        if (glued == null) {
            return null;
        }
        int max = Math.min(LOOSE_LABEL_MAX, glued.length() - 1);
        for (int len = 1; len <= max; len++) {
            String field = fieldOfLabel(glued.substring(0, len));
            if (field == null) {
                continue;
            }
            // 标签后面可能还带冒号（`年龄:56岁` 的 `年龄` 后紧跟着冒号）
            int start = glued.charAt(len) == ':' ? len + 1 : len;
            int end = start + tailEnd(glued.substring(start));
            if (end <= start) {
                return null;
            }
            return new LabelHit(field, glued.substring(start, end), end);
        }
        return null;
    }

    /**
     * 值在片段里的结束位置：到「下一个词表标签」为止，没有下一个标签就是片段全长。
     *
     * <p>{@code 男年龄:56岁} → 1（值是 {@code 男}，剩下的是 {@code 年龄:56岁}）；
     * {@code 测试供体甲} → 全长（名字里没有标签）。
     */
    static int tailEnd(String segment) {
        if (segment == null) {
            return 0;
        }
        for (int at = 1; at < segment.length(); at++) {
            int max = Math.min(LOOSE_LABEL_MAX, segment.length() - at);
            for (int len = 1; len <= max; len++) {
                if (fieldOfLabel(segment.substring(at, at + len)) != null) {
                    return at;
                }
            }
        }
        return segment.length();
    }

    /**
     * 命中。
     *
     * @param field    字段名
     * @param rawValue 本字段的原始值片段（交给 {@link #valueOf} 校验；不含标签与分隔冒号）
     * @param consumed 剩余串里消费到的下标（下一个字段从 {@code consumed} 开始）
     */
    record LabelHit(String field, String rawValue, int consumed) {
    }

    /**
     * 标签段（已去掉冒号与两侧空白）→ 字段名。判等前去掉标签**内部**的空白：
     * {@code 性 别} / {@code 年 龄} / {@code 住 院 号} 都是排版噪声，不是别的标签。
     *
     * @return 词表里的字段名；不是词表标签 → null（{@code 临床诊断} / {@code 保持 2-8℃ 低温运输}）
     */
    static String fieldOfLabel(String labelSegment) {
        String key = labelSegment.replaceAll("\\s+", "");
        for (Map.Entry<String, List<String>> entry : LABELS.entrySet()) {
            if (entry.getValue().contains(key)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 行归一：去掉全部空白、全角冒号换半角。归一后 {@code 性 别 男} → {@code 性别男}、
     * {@code 年 龄 61 岁} → {@code 年龄61岁}、{@code 住院号:ZY0000002 床号:12} →
     * {@code 住院号:ZY0000002床号:12}（值直接贴上下一个标签，于是不会被一起吃进来）。
     */
    static String glue(String raw) {
        return raw.replace(FULL_WIDTH_COLON, ':').replaceAll("\\s+", "");
    }

    /**
     * 标签后的片段（已归一、无空白）→ 合法字段值；不合法 → null（宁缺毋滥）。
     *
     * <p>片段可能比「本字段的值」长一点，因为「值到下一个词表标签为止」的前瞻只在
     * <b>词表标签边界</b>切得开：{@code 性别:男年龄:56岁} 的性别片段归一后是 {@code 男年}
     * （要等「年龄」两个字到齐才算标签边界），{@code 住院号:ZY0000002床号:12} 的住院号片段是
     * {@code ZY0000002床号:12}（{@code 床号} 不是词表标签）。所以：冒号形状在
     * {@link #colonValueEnd} 里按「下一个词表标签 <b>或下一个冒号</b>」切 —— 下一个冒号就是
     * 下一个字段的分隔符（哪怕那个字段的标签不在词表里，{@code 床号} 就是）。
     */
    static String valueOf(String field, String segment) {
        if (segment == null || segment.isEmpty()) {
            return null;
        }
        return accept(field, segment);
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
     * 姓名：至少两个字符，且不能是「男 / 女」这种被人名标签误吃进来的邻字段值。
     */
    private static String validName(String value) {
        if (value.length() < 2) {
            return null;
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
