package org.dromara.lqg.auth.group.guard;

import java.util.Set;

/**
 * 外部档案核验状态机（FLOW:F-AUTH-03.step2 / step3 / step4，FIELD:t_lqg_ext_profile.bind_status）
 * —— **纯函数**，无 Spring、无库，契约测试直接钉这张转移表。
 *
 * <p>★ 这张表是本票最容易做反的地方，逐条对着 accept 抄：
 * <pre>
 *   pending   → verified   核验通过（自填的必须先「新建」或「归并」，见 {@link #needsSelfInputDecision}）
 *   pending   → rejected   驳回（原因必填）
 *   verified  → pending    外部自己改了单位 / 组别 → **立刻**回到待核验，不是等核验人驳回才失效
 *   rejected  → pending    外部改后重提
 *   verified  → verified   内部「改归组」（换个组，仍然已核验）
 *   其余一律拒绝 —— 且拒绝时**库里必须一个字都不变**（先校验、后写库）
 * </pre>
 *
 * <p>★ 「只有 verified 才参与同组互看」：pending / rejected / unbound 一律只看本人的样本。
 * 可见范围本身在 AUTH-EXT-001 算，但状态在这里维护 —— 状态错了那边全错。
 *
 * @author AUTH-GROUP-001
 */
public final class ExtBindStateMachine {

    /**
     * 未填写
     */
    public static final String UNBOUND = "unbound";

    /**
     * 待核验
     */
    public static final String PENDING = "pending";

    /**
     * 已核验
     */
    public static final String VERIFIED = "verified";

    /**
     * 已驳回
     */
    public static final String REJECTED = "rejected";

    /**
     * 核验动作：通过
     */
    public static final String ACTION_APPROVE = "approve";

    /**
     * 核验动作：驳回
     */
    public static final String ACTION_REJECT = "reject";

    /**
     * 「改归组」= 对已核验档案再走一次 approve，目标仍是 verified（含换组 / 不换组）
     */
    public static final java.util.Map<String, String> ACTION_TARGET = java.util.Map.of(
        ACTION_APPROVE, VERIFIED,
        ACTION_REJECT, REJECTED
    );

    /**
     * 合法转移：{@code from -> to}
     */
    private static final Set<String> LEGAL = Set.of(
        PENDING + "->" + VERIFIED,
        PENDING + "->" + REJECTED,
        VERIFIED + "->" + PENDING,
        REJECTED + "->" + PENDING,
        VERIFIED + "->" + VERIFIED
    );

    private ExtBindStateMachine() {
    }

    /**
     * 核验动作名是否认识。
     */
    public static boolean isKnownAction(String action) {
        return ACTION_TARGET.containsKey(action);
    }

    /**
     * 这个动作要落到的目标状态。
     *
     * @param action approve / reject
     * @return 目标状态；动作不认识时抛 {@link IllegalArgumentException}（调用方负责包成人话）
     */
    public static String targetOf(String action) {
        String target = ACTION_TARGET.get(action);
        if (target == null) {
            throw new IllegalArgumentException("不认识的核验动作：" + action);
        }
        return target;
    }

    /**
     * 转移是否合法。{@code from} / {@code to} 任一为 null 或空都算不合法。
     */
    public static boolean isLegal(String from, String to) {
        return from != null && to != null && LEGAL.contains(from + "->" + to);
    }

    /**
     * 核验动作在当前状态上是否合法（判据 = 目标状态的那条边在不在表里）。
     */
    public static boolean isLegalAction(String current, String action) {
        if (!isKnownAction(action)) {
            return false;
        }
        return isLegal(current, targetOf(action));
    }

    /**
     * 外部**保存**档案后的状态：任何一次成功保存一律回到 {@code pending}
     * （accept 第 2 条：extA 从 verified 改个组别，落库就是 {@code pending} + {@code verified_by} 清空）。
     *
     * <p>保存合法与否不在这里判：能走到这一步就说明内容本身是合法的（单位存在、组别属于该单位），
     * 而「回 pending」是**无条件**的 —— 不是「改没改才看」，因为重填一遍也意味着信息要重新确认。
     */
    public static String afterProfileSave() {
        return PENDING;
    }

    /**
     * 自填档案（{@code unit_id} 为空、只有 {@code unit_name_input}）在核验通过时，
     * 必须由核验人二选一：「新建」（{@code createUnit + createGroup}）或「归并」（{@code unitId + groupId}）。
     *
     * <p>★ 不许留着一个 {@code unit_id} 为空却 {@code verified} 的档案：同组互看是按
     * {@code group_id} 算的，group_id 为空的人「核验通过」了却谁也匹配不上，而且再也不会回到核验队列。
     *
     * @param currentStatus       档案当前状态
     * @param hasUnitId           档案当前有没有 unit_id（选了列表项就有，自填就没有）
     * @param hasGroupId          档案当前有没有 group_id
     * @param createUnit          入参 createUnit
     * @param createGroup         入参 createGroup
     * @param hasIncomingUnitId   入参有没有带上 unitId
     * @param hasIncomingGroupId  入参有没有带上 groupId
     * @return null = 不需要二选一（照常核验）；否则返回给人看的拒绝原因
     */
    public static String needsSelfInputDecision(String currentStatus, boolean hasUnitId, boolean hasGroupId,
                                                boolean createUnit, boolean createGroup,
                                                boolean hasIncomingUnitId, boolean hasIncomingGroupId) {
        // 只有「变成 verified」这一条才需要决定归口；驳回不需要
        if (!PENDING.equals(currentStatus)) {
            return null;
        }
        if (hasUnitId && hasGroupId) {
            return null;
        }
        boolean newPath = createUnit && createGroup;
        boolean mergePath = hasIncomingUnitId && hasIncomingGroupId;
        if (newPath || mergePath) {
            return null;
        }
        return "该档案的单位 / 组别是外部自填的，核验通过前必须二选一："
            + "「新建」（createUnit + createGroup）或「归并到已有」（unitId + groupId）";
    }

    /**
     * 这个状态是否参与「同组互看」。
     */
    public static boolean participatesInGroupSharing(String status) {
        return VERIFIED.equals(status);
    }

}
