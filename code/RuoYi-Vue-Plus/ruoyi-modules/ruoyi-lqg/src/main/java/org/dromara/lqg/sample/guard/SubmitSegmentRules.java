package org.dromara.lqg.sample.guard;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.lqg.auth.group.guard.UnitGroupRules;
import org.dromara.lqg.cryo.batch.CryoBalanceChecker;
import org.dromara.lqg.sample.domain.bo.SampleSubmitSegmentBo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 样本（与外部石蜡包埋送样）写入口的<b>必填与格式</b>——<b>纯函数</b>，无 Spring、无库，契约测试直接钉。
 *
 * <p>★ 为什么要有这一层（FIX V03：issue #88 / #111）：外部写接口缺字段、给字典外的值时，
 * 以前一路走到 {@code INSERT} / {@code UPDATE} 才被 PostgreSQL 的非空约束 / 长度约束拦下，
 * 全局异常处理把整段 SQL 与整行数据（含供体姓名、住院号密文与内部 user_id）回吐给外部。
 * 现在所有写入口在<b>任何写库之前</b>过一遍本类，违规一律 {@code code=400} + 字段级人话，
 * 响应里没有表名、列名、SQL。
 *
 * <p>★ 规则的来源（逐条对得上，不另造口径）：
 * <ul>
 *   <li>必填：{@code FIELD:t_lqg_sample.source_unit_name} 是 NOT NULL（没选单位就必须给名称）；
 *       {@code tissue_type}「tissue 类必填」、{@code organoid_type}「organoid 类必填」；
 *       {@code species} 两类都必填（CR-20261009-18）；
 *       {@code donor_name}「organoid 类可空」→ 外部送<b>组织样本</b>必须写供体姓名
 *       （小程序填写页本来就这么要求，这里把口径收回后端，G26）。</li>
 *   <li>字典：{@code lqg_gender} = male / female / unknown；{@code lqg_has_none} / {@code lqg_yes_no} = Y / N。</li>
 *   <li>代数（类器官，CR-20260924-10）：选填；填了必须形如 {@code P3}，与冻存批次的代数同一条规则
 *       （{@link #isValidPassage}）。</li>
 *   <li>长度：DDL 的 VARCHAR 长度；两个加密列（供体姓名、住院号）明文封顶 50 字、且 UTF-8 不超过
 *       {@value #MAX_CIPHER_PLAIN_BYTES} 字节 —— AES/ECB + Base64 之后密文才一定放得进 VARCHAR(255)。</li>
 * </ul>
 *
 * <p>★ 内部与外部<b>共用</b>这一份（{@link Writer} 只决定「外部组织样本多一个供体姓名必填」）：
 * 工作台新增 / 修改、核验时一并保存送检段、小程序内部修改、外部提交与重提 —— 同一份规则，
 * 不会出现「工作台能存、小程序存不进」或反过来。
 *
 * @author FIX-V03
 */
public final class SubmitSegmentRules {

    /**
     * 写入方：外部（小程序合作单位）/ 内部（工作台、小程序内部人员）。
     */
    public enum Writer {
        /** 合作单位：组织样本多一个供体姓名必填 */
        EXTERNAL,
        /** 实验室内部人员 */
        INTERNAL
    }

    /** 统一的报错前缀（响应 msg = 前缀 + 逐条违规，用「；」隔开） */
    public static final String MSG_PREFIX = "提交的内容不符合要求：";

    /** {@code t_lqg_sample.source_unit_name} VARCHAR(100) */
    public static final int MAX_UNIT_NAME = 100;
    /** 供体姓名（加密列）明文字数上限 —— 与工作台输入框的 maxlength 一致 */
    public static final int MAX_DONOR_NAME = 50;
    /** {@code t_lqg_sample.age} VARCHAR(20) */
    public static final int MAX_AGE = 20;
    /** 住院号（加密列）明文字数上限 —— 与工作台输入框的 maxlength 一致 */
    public static final int MAX_HOSPITAL_NO = 50;
    /** {@code t_lqg_sample.species} VARCHAR(50)（CR-20261009-18） */
    public static final int MAX_SPECIES = 50;
    /** {@code tissue_type} / {@code organoid_type} VARCHAR(100) */
    public static final int MAX_TYPE = 100;
    /** {@code t_lqg_sample.remark} VARCHAR(500) */
    public static final int MAX_REMARK = 500;
    /** {@code t_lqg_sample.operator_name} VARCHAR(50) */
    public static final int MAX_OPERATOR_NAME = 50;
    /** {@code t_lqg_sample.internal_no} VARCHAR(64) */
    public static final int MAX_INTERNAL_NO = 64;
    /** {@code t_lqg_sample.invalid_reason} VARCHAR(200) */
    public static final int MAX_INVALID_REASON = 200;
    /** {@code t_lqg_embed.sample_type} VARCHAR(50) */
    public static final int MAX_EMBED_SAMPLE_TYPE = 50;
    /** {@code t_lqg_embed.organoid_source_type} VARCHAR(100) */
    public static final int MAX_EMBED_ORGANOID_SOURCE_TYPE = 100;
    /**
     * 加密列明文的 UTF-8 字节上限：AES 分组 16 字节 + PKCS5 至少补 1 字节 → 175 字节明文 = 11 组 = 176 字节密文
     * → Base64 236 字符 ≤ 255；176 字节明文就会变成 12 组、Base64 256 字符，放不进 VARCHAR(255)。
     */
    public static final int MAX_CIPHER_PLAIN_BYTES = 175;

    /** 代数格式不对时的提示（CR-20260924-10；工作台与小程序填写页的前端提示与它同一句） */
    public static final String MSG_PASSAGE = "代数请填 P 加数字，如 P3";

    /** 字典 {@code lqg_gender} */
    public static final Set<String> GENDERS = Set.of("male", "female", "unknown");
    /** 字典 {@code lqg_has_none} / {@code lqg_yes_no}（两态按钮） */
    public static final Set<String> FLAGS = Set.of("Y", "N");

    private SubmitSegmentRules() {
    }

    // ── 送检段 ────────────────────────────────────────────────────────────────

    /**
     * 送检段的全部违规（空列表 = 通过）。按样本<b>自己的</b>类别只看本类别的字段：
     * 类器官不看供体姓名等组织样本字段（它们在类器官的写路径上根本不落库）。
     *
     * @param kind   样本类别 tissue / organoid（调用方已按身份定好）
     * @param seg    送检段（null 按「什么都没给」处理）
     * @param writer 写入方
     */
    public static List<String> submitViolations(String kind, SampleSubmitSegmentBo seg, Writer writer) {
        SampleSubmitSegmentBo s = seg == null ? new SampleSubmitSegmentBo() : seg;
        List<String> out = new ArrayList<>();
        boolean organoid = SampleKindRules.KIND_ORGANOID.equals(SampleKindRules.normalize(kind));

        // 来源单位：选了单位（id）就不要求名称；否则名称必填（NOT NULL 列）
        if (s.getSourceUnitId() == null && StringUtils.isBlank(s.getSourceUnitName())) {
            out.add(writer == Writer.EXTERNAL
                ? "来源单位不能为空（请填写单位名称，或先在「我的 → 单位与组别」绑定单位，绑定后会自动带出）"
                : "来源单位不能为空（请选择单位或填写单位名称）");
        }
        checkLength(out, "来源单位名称", s.getSourceUnitName(), MAX_UNIT_NAME);

        // 种属（CR-20261009-18）：两类都必填；文本，常用值来自字典 lqg_species，列表里没有的可以手填 → 只判非空与长度
        if (StringUtils.isBlank(s.getSpecies())) {
            out.add("种属不能为空");
        }
        checkLength(out, "种属", s.getSpecies(), MAX_SPECIES);

        if (organoid) {
            if (StringUtils.isBlank(s.getOrganoidType())) {
                out.add("类器官类型不能为空");
            }
            checkLength(out, "类器官类型", s.getOrganoidType(), MAX_TYPE);
            if (!isValidPassage(s.getPassage())) {
                out.add(MSG_PASSAGE);
            }
        } else {
            // 组织样本没有代数：传了也不报错、不落库（写路径一律写 NULL，与「另一类的类型列传了不生效」同口径）
            if (StringUtils.isBlank(s.getTissueType())) {
                out.add("组织类型不能为空");
            }
            checkLength(out, "组织类型", s.getTissueType(), MAX_TYPE);
            if (writer == Writer.EXTERNAL && StringUtils.isBlank(s.getDonorName())) {
                out.add("供体姓名不能为空");
            }
            checkCipherPlain(out, "供体姓名", s.getDonorName(), MAX_DONOR_NAME);
            checkEnum(out, "性别", s.getGender(), GENDERS, "male（男）/ female（女）/ unknown（未知）");
            checkLength(out, "年龄", s.getAge(), MAX_AGE);
            checkCipherPlain(out, "住院号", s.getHospitalNo(), MAX_HOSPITAL_NO);
            checkEnum(out, "有无病理", s.getHasPathology(), FLAGS, "Y（有）或 N（无）");
        }
        checkLength(out, "备注", s.getRemark(), MAX_REMARK);
        return out;
    }

    // ── 代数（类器官，CR-20260924-10） ───────────────────────────────────────

    /**
     * 代数的<b>落库值</b>：去首尾空白、开头的小写 {@code p} 转大写；空 → {@code null}（选填）。
     *
     * <p>只做归一化、不判格式 —— 格式由 {@link #isValidPassage} 判（写库之前已经在
     * {@link #submitViolations} 里拒掉了不合格的值）。写路径（{@code SampleSubmitSegmentWriter}）
     * 与校验共用这一个函数，校验通过的值与落库的值必然是同一个。
     *
     * <pre>
     * " P3 " → "P3"      "p12" → "P12"      "" / "  " / null → null
     * </pre>
     */
    public static String normalizePassage(String value) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        return v.charAt(0) == 'p' ? "P" + v.substring(1) : v;
    }

    /**
     * 代数格式：空（选填）或归一化后形如 {@code P3}。
     *
     * <p>★ <b>复用冻存批次的那一条规则</b>（{@link CryoBalanceChecker#requirePassage}，{@code ^P\d{1,3}$}）
     * —— 甲方口里的「代数」在收样记录与冻存批次上是同一个概念，两处各写一个正则迟早会漂。
     * 与冻存那边唯一的不同是这里<b>先把小写 p 转大写</b>（收样记录是外部手填的，{@code p3} 这种
     * 一眼能看懂的写法不必让人重填）；{@code 3} / {@code 第3代} / {@code P1234} 照样拒。
     */
    public static boolean isValidPassage(String value) {
        String v = normalizePassage(value);
        if (v == null) {
            return true;
        }
        try {
            CryoBalanceChecker.requirePassage(v);
            return true;
        } catch (ServiceException e) {
            return false;
        }
    }

    // ── 收样段（只有内部写） ──────────────────────────────────────────────────

    /**
     * 收样段的格式违规（不含必填：必填按场景分，见 {@link #receiveRequiredViolations}）。
     */
    public static List<String> receiveViolations(String internalNo, String isFixed, String hasQcSheet,
                                                 String hasViabilityReport, String operatorName) {
        List<String> out = new ArrayList<>();
        checkLength(out, "内部编号", internalNo, MAX_INTERNAL_NO);
        checkEnum(out, "有无固定", isFixed, FLAGS, "Y（有）或 N（无）");
        checkEnum(out, "质控表", hasQcSheet, FLAGS, "Y（有）或 N（无）");
        checkEnum(out, "细胞活率报告", hasViabilityReport, FLAGS, "Y（有）或 N（无）");
        checkLength(out, "操作人", operatorName, MAX_OPERATOR_NAME);
        return out;
    }

    /**
     * 内部录入 / 修改有效样本时收样段的必填：内部编号、收样日期（FLOW:F-SAMPLE-02.step1 / step2）。
     */
    public static List<String> receiveRequiredViolations(String internalNo, Object receiveDate) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isBlank(internalNo)) {
            out.add("内部编号不能为空");
        }
        if (receiveDate == null || StringUtils.isBlank(String.valueOf(receiveDate))) {
            out.add("收样日期不能为空");
        }
        return out;
    }

    // ── 外部石蜡包埋送样 ──────────────────────────────────────────────────────

    /**
     * 外部石蜡包埋送样（{@code POST /mp/ext/embed}、{@code PUT /mp/ext/embed/{id}}）的必填与格式。
     *
     * @param requireSampleId POST 必须给所挂样本；PUT 不给 = 不改所挂样本
     */
    public static List<String> externalEmbedViolations(Long sampleId, boolean requireSampleId,
                                                       String sampleType, String organoidSourceType) {
        List<String> out = new ArrayList<>();
        if (requireSampleId && sampleId == null) {
            out.add("请选择要送样的样本（sampleId 不能为空）");
        }
        checkLength(out, "样本类型", sampleType, MAX_EMBED_SAMPLE_TYPE);
        checkLength(out, "类器官来源类型", organoidSourceType, MAX_EMBED_ORGANOID_SOURCE_TYPE);
        return out;
    }

    // ── 外部提交的来源单位归属（V01 / issue #112） ────────────────────────────

    /**
     * 一个来源单位引用：{@code id} 为空 = 自填单位名（只有名称快照）。
     *
     * @param id   {@code t_lqg_source_unit.id}
     * @param name 名称（快照）
     */
    public record UnitRef(Long id, String name) {
    }

    /**
     * 外部提交 / 重提时，样本该挂哪个来源单位（<b>纯函数</b>，查库在调用方）。
     *
     * <p>★ 口径（FIX V01）：外部<b>只能</b>把样本挂到自己「可用」的单位上 ——
     * 即外部档案里绑定的单位（档案 {@code pending} 或 {@code verified}；{@code unbound} /
     * {@code rejected} 没有可用单位，见 {@code ExtProfileQueryService#boundUnitOf}）。
     * 前端不发 id 也要对：表单里的单位名与可用单位同名（去空白、大小写不敏感，
     * {@link UnitGroupRules#sameName}）就挂上这个单位的 id。
     * <ol>
     *   <li>带了 {@code sourceUnitId}：必须等于可用单位（或这条样本<b>已经挂着</b>的单位 ——
     *       实验室核验时挂的，外部原样带回来）；否则 400，<b>不许</b>外部把样本挂到别的单位；</li>
     *   <li>没带 id、名称为空 → 400「来源单位不能为空」（#111：未绑定单位的新外部用户第一次提交，
     *       给明确提示而不是 500）；</li>
     *   <li>没带 id、名称与可用单位同名 → 挂可用单位的 id（名称取单位表的正式名称）；</li>
     *   <li>重提时名称没改（与这条样本当前的名称快照同名）→ 沿用已挂的单位；</li>
     *   <li>其余 → 只存名称快照、id 为空（自填单位名，由实验室核验时归口）。</li>
     * </ol>
     *
     * @param bound         提交人可用的单位（没有 = null）
     * @param requestedId   请求里的 {@code sourceUnitId}
     * @param requestedName 请求里的 {@code sourceUnitName}
     * @param existing      重提时这条样本当前的单位（新建时 null）
     * @return 要落库的单位
     */
    public static UnitRef attributeExternalUnit(UnitRef bound, Long requestedId, String requestedName, UnitRef existing) {
        if (requestedId != null) {
            if (bound != null && requestedId.equals(bound.id())) {
                return bound;
            }
            if (existing != null && requestedId.equals(existing.id())) {
                return existing;
            }
            throw new ServiceException(MSG_PREFIX
                + "来源单位只能选您在「我的 → 单位与组别」里绑定的单位；其他单位请直接填写单位名称", 400);
        }
        String name = trimToNull(requestedName);
        if (name == null) {
            throw new ServiceException(MSG_PREFIX
                + "来源单位不能为空（请填写单位名称，或先在「我的 → 单位与组别」绑定单位，绑定后会自动带出）", 400);
        }
        if (bound != null && UnitGroupRules.sameName(name, bound.name())) {
            return bound;
        }
        if (existing != null && existing.id() != null && UnitGroupRules.sameName(name, existing.name())) {
            return existing;
        }
        return new UnitRef(null, name);
    }

    // ── 出口 ─────────────────────────────────────────────────────────────────

    /**
     * 有违规就抛 {@code ServiceException(前缀 + 逐条, 400)}；没有就什么都不做。
     */
    public static void throwIfAny(List<String> violations) {
        if (violations != null && !violations.isEmpty()) {
            throw new ServiceException(MSG_PREFIX + String.join("；", violations), 400);
        }
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private static void checkLength(List<String> out, String label, String value, int max) {
        String v = trimToNull(value);
        if (v != null && v.codePointCount(0, v.length()) > max) {
            out.add(label + "不能超过 " + max + " 字");
        }
    }

    private static void checkCipherPlain(List<String> out, String label, String value, int maxChars) {
        String v = trimToNull(value);
        if (v == null) {
            return;
        }
        if (v.codePointCount(0, v.length()) > maxChars || v.getBytes(StandardCharsets.UTF_8).length > MAX_CIPHER_PLAIN_BYTES) {
            out.add(label + "不能超过 " + maxChars + " 字");
        }
    }

    private static void checkEnum(List<String> out, String label, String value, Set<String> allowed, String hint) {
        String v = trimToNull(value);
        if (v != null && !allowed.contains(v)) {
            out.add(label + "只能是 " + hint);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
