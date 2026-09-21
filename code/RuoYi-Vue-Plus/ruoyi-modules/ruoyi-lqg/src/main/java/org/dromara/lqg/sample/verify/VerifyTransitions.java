package org.dromara.lqg.sample.verify;

import java.util.Set;

/**
 * 核验状态机（FLOW:F-SAMPLE-01.step5，FIELD:t_lqg_sample.verify_status）—— <b>纯函数</b>：
 * 无 Spring、无库、无任何样本特有的字段，契约测试直接钉这张转移表。
 *
 * <p>★ 合法转移只有 5 条（ticket §2 的表格逐字抄下来），第 3 个参数是「操作者是不是内部人员」：
 * <pre>
 *   pending   → valid     内部     收样日期 + 内部编号必填（编号唯一性在 service 校验）
 *   pending   → invalid   内部     原因必填
 *   invalid   → pending   外部本人 修改送检段后重提（清 invalid_reason / verify_by / verify_time）
 *   invalid   → valid     内部     直接改判，条件同 pending→valid
 *   valid     → invalid   内部     误判纠正，原因必填，且该样本名下没有下游记录
 *   其余一律拒绝 —— 含 valid→pending、外部把状态改成 valid、把状态直接写成任意值
 * </pre>
 *
 * <p>★ <b>与表无关</b>（ticket §2 / §0 口径 4）：判据里<b>没有</b> {@code sample_kind}、没有
 * {@code internalNo} / {@code receiveDate}、没有下游记录检查 —— 那些都是**样本表**的字段与前置条件，
 * 属于 {@link SampleVerifyService} 的职责。石蜡包埋送样核验（EMBED-MODEL-001，{@code t_lqg_embed}）
 * 复用同一个 {@link #check(String, String, boolean)}，合法转移只有这一份。
 *
 * <p>★ 拒绝时**库里必须一个字都不变**：service 先判这张表、先做全部校验，最后才写库，
 * 整段一个事务（accept 第 1 条第 5 段断的就是「三次被拒之后 status 仍是 pending|-|-」）。
 *
 * @author SAMPLE-VERIFY-001
 */
public final class VerifyTransitions {

    /**
     * 待核验（外部提交后的初始态）
     */
    public static final String PENDING = "pending";

    /**
     * 有效（内部核验通过 / 内部直接录入）
     */
    public static final String VALID = "valid";

    /**
     * 无效（内部驳回，带原因）
     */
    public static final String INVALID = "invalid";

    /**
     * 核验动作：判有效（doc/api-contract.md 的 {@code PUT /lqg/sample/{id}/verify} 的 action）
     */
    public static final String ACTION_VALID = "valid";

    /**
     * 核验动作：判无效
     */
    public static final String ACTION_INVALID = "invalid";

    private static final String INTERNAL = "|internal";

    private static final String EXTERNAL = "|external";

    /**
     * 合法转移：{@code "from->to|操作者身份"}。
     *
     * <p>用「三元组做白名单」而不是「if 一串」：白名单天然拒绝任何没列出来的组合
     * （包括 null、空串、别人新加的第四种状态），不需要再补一条「其余一律拒绝」的兜底。
     */
    private static final Set<String> LEGAL = Set.of(
        edge(PENDING, VALID, true),
        edge(PENDING, INVALID, true),
        edge(INVALID, PENDING, false),
        edge(INVALID, VALID, true),
        edge(VALID, INVALID, true)
    );

    private VerifyTransitions() {
    }

    /**
     * 这条转移是否合法。
     *
     * @param from            当前状态（{@link #PENDING} / {@link #VALID} / {@link #INVALID}）
     * @param to              目标状态
     * @param actorIsInternal 操作者是不是内部人员（由账号角色算，见 service）
     * @return 合法 → true；{@code from} / {@code to} 为 null 或未知值 → false（不放行任何意外组合）
     */
    public static boolean check(String from, String to, boolean actorIsInternal) {
        if (from == null || to == null) {
            return false;
        }
        return LEGAL.contains(edge(from, to, actorIsInternal));
    }

    /**
     * 是不是认识的状态值。
     */
    public static boolean isKnownStatus(String status) {
        return PENDING.equals(status) || VALID.equals(status) || INVALID.equals(status);
    }

    /**
     * 是不是认识的核验动作。
     */
    public static boolean isKnownAction(String action) {
        return ACTION_VALID.equals(action) || ACTION_INVALID.equals(action);
    }

    /**
     * 核验动作要落到的目标状态。
     *
     * @param action valid / invalid
     * @return 目标状态；动作不认识时抛 {@link IllegalArgumentException}（调用方负责包成人话）
     */
    public static String targetOf(String action) {
        if (ACTION_VALID.equals(action)) {
            return VALID;
        }
        if (ACTION_INVALID.equals(action)) {
            return INVALID;
        }
        throw new IllegalArgumentException("不认识的核验动作：" + action);
    }

    /**
     * 合法转移的完整清单（给人看的报错文案与「单测钉住每一格」共用）。
     *
     * @return 形如 {@code ["pending→valid(内部)", …]} 的数组
     */
    public static String[] legalTransitions() {
        return new String[]{
            PENDING + "→" + VALID + "(内部)",
            PENDING + "→" + INVALID + "(内部)",
            INVALID + "→" + PENDING + "(外部)",
            INVALID + "→" + VALID + "(内部)",
            VALID + "→" + INVALID + "(内部)"
        };
    }

    /**
     * 非法转移的人话报错（service 抛 {@code ServiceException} 用它）。
     */
    public static String rejectionMessage(String from, String to, boolean actorIsInternal) {
        return String.format("非法核验状态转移：%s → %s（操作者%s）；合法转移只有 %s",
            from, to, actorIsInternal ? "是内部人员" : "不是内部人员",
            String.join("、", legalTransitions()));
    }

    private static String edge(String from, String to, boolean actorIsInternal) {
        return from + "->" + to + (actorIsInternal ? INTERNAL : EXTERNAL);
    }

}
