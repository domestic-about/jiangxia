package org.dromara.lqg.cryo.batch;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 冻存剩余支数的<b>唯一判据</b>（ADR-0010 / FLOW:F-CRYO-02.step4 / step5）。
 *
 * <p>★ <b>纯函数，不碰库、不碰 Spring</b>（ticket §2）。调用方负责在<b>锁住批次行之后</b>
 * 读未删流水再交给它 —— 本类只回答两个问题：
 * <ol>
 *   <li>剩余是多少：{@code 初始支数 + Σ(未删流水的 delta)}（{@link #remaining(int, Collection)}）；</li>
 *   <li>把初始支数改成某个值，逐笔算下来会不会有哪一步为负
 *       （{@link #requireNonNegative(int, Collection)}）。</li>
 * </ol>
 *
 * <p>★★ <b>为什么不能只看最终剩余</b>（FLOW:F-CRYO-02.step5 的原话）：只看最后一步会放过
 * 「当时只剩 2 支却取走了 3 支」的账 —— 后面一笔补入把余额补回来之后，最终剩余是正的，
 * 但中间那一刻的账面是负数。所以判据是「从初始支数出发、按 {@code flow_time} 正序
 * <b>逐笔</b>累加，任何一步 &lt; 0 都拒绝」。
 *
 * <p>★ <b>同一时刻按 {@code id} 排序</b>（ticket §2）：{@code flow_time} 相同的两笔
 * （同一秒录入取走与补入是常事）若顺序不定，同一次改初始支数可能一次过一次不过。
 *
 * <p>★ <b>{@link #requireNonNegative} 的失败信息必须是「已取走 N 支，冻存数量不能少于 N」</b>
 * （ticket §0 口径复述 2）：N = 逐笔算下来<b>最大的那一次透支</b> = 让整条序列合法的
 * 最小初始支数。用户看到的是「最少能填几支」，不是一个内部错误码。
 *
 * <p>★ 复用方：CRYO-MODEL-001 的改初始支数（{@code PUT /lqg/cryo/batch}）与
 * CRYO-FLOW-001 的写 / 改 / 删一笔登记，三处共用本类（FLOW:F-CRYO-02.step5 要求「校验相同」）。
 *
 * @author CRYO-MODEL-001
 */
public final class CryoBalanceChecker {

    private CryoBalanceChecker() {
    }

    /**
     * 一笔流水的最小投影：只留排序键与变化量（{@code t_lqg_cryo_flow} 的三列）。
     *
     * <p>刻意不收实体、不收 Map：CRYO-FLOW-001 手上是「改后的流水集合」（有的行还没落库），
     * 用它拼 {@code List<Flow>} 即可，不必先写库再查。
     *
     * @param id       流水 id（同一时刻的第二排序键；未落库的新行给 {@code null} 或负数即可）
     * @param flowTime 发生时间
     * @param delta    带符号变化量（take 负 / add 正 / adjust 可正可负）
     */
    public record Flow(Long id, LocalDateTime flowTime, int delta) {
    }

    /**
     * 按 {@code flow_time} 正序、同一时刻按 {@code id} 正序排好（纯函数，不改入参）。
     *
     * @param flows 未删流水（可为 null / 空）
     * @return 排好序的新列表；null / 空 → 空列表
     */
    public static List<Flow> ordered(Collection<Flow> flows) {
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }
        List<Flow> out = new ArrayList<>(flows.size());
        for (Flow flow : flows) {
            if (flow != null) {
                out.add(flow);
            }
        }
        Comparator<Flow> byTime = Comparator.comparing(Flow::flowTime,
            Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()));
        Comparator<Flow> byId = Comparator.comparing(Flow::id,
            Comparator.nullsFirst(Comparator.<Long>naturalOrder()));
        out.sort(byTime.thenComparing(byId));
        return out;
    }

    /**
     * 剩余 = 初始支数 + Σ(未删流水的 delta)（求和与顺序无关，所以不排序）。
     *
     * @param initQty 初始支数
     * @param flows   未删流水
     * @return 剩余支数（可能为负：这只说明库里已经是坏账，写侧必须靠
     *         {@link #requireNonNegative} 拒绝产生坏账的操作）
     */
    public static int remaining(int initQty, Collection<Flow> flows) {
        int sum = initQty;
        if (flows != null) {
            for (Flow flow : flows) {
                if (flow != null) {
                    sum += flow.delta();
                }
            }
        }
        return sum;
    }

    /**
     * 让整条序列合法的<b>最小初始支数</b> = 逐笔累加过程中最大的透支额。
     *
     * @param flows 未删流水
     * @return ≥ 0；没有任何一笔透支时为 0
     */
    public static int requiredInitQty(Collection<Flow> flows) {
        int balance = 0;
        int required = 0;
        for (Flow flow : ordered(flows)) {
            balance += flow.delta();
            if (-balance > required) {
                required = -balance;
            }
        }
        return required;
    }

    /**
     * 逐笔校验：从 {@code initQty} 出发按时间正序累加，任何一步 &lt; 0 → 抛业务异常、库里不变。
     *
     * @param initQty 新的初始支数
     * @param flows   未删流水
     * @throws ServiceException 任一步为负（消息「已取走 N 支，冻存数量不能少于 N」，N = 最小合法初始支数）
     */
    public static void requireNonNegative(int initQty, Collection<Flow> flows) {
        int required = requiredInitQty(flows);
        if (required > initQty) {
            throw new ServiceException("已取走 " + required + " 支，冻存数量不能少于 " + required, 400);
        }
    }

    /**
     * 校验并返回剩余：等价于 {@link #requireNonNegative} 之后再 {@link #remaining}。
     *
     * @param initQty 初始支数
     * @param flows   未删流水
     * @return 剩余支数（必然 ≥ 0）
     */
    public static int checkAndRemaining(int initQty, Collection<Flow> flows) {
        requireNonNegative(initQty, flows);
        return remaining(initQty, flows);
    }

    /**
     * 代数格式：{@code P} + 1~3 位数字（如 {@code P3} / {@code P12}）。
     *
     * <p>★ {@code 3} / {@code p3} / {@code 第3代} 一律拒（ticket §2 + accept 2 第 1 段）：
     * 冻存样品名称是手填的自由文本（系统不解析），但「代数」是单独一栏、要能排序与导出，
     * 一列里混进 {@code 3} 与 {@code P3} 会让导出和筛选全乱。
     *
     * @param passage 入参
     * @return 去掉首尾空白后的代数
     * @throws ServiceException 格式不符
     */
    public static String requirePassage(String passage) {
        String value = passage == null ? null : passage.trim();
        if (StringUtils.isBlank(value) || !value.matches("^P\\d{1,3}$")) {
            throw new ServiceException("代数格式不对，应形如 P3（P + 1~3 位数字）：" + passage, 400);
        }
        return value;
    }

    /**
     * 冻结时间不能为空（超期提醒从它起算，SSOT 里 NOT NULL）。
     *
     * @param freezeTime 冻存时间（{@code yyyy-MM-dd}）
     * @return 原值
     */
    public static java.time.LocalDate requireFreezeTime(java.time.LocalDate freezeTime) {
        if (freezeTime == null) {
            throw new ServiceException("冻存时间不能为空", 400);
        }
        return freezeTime;
    }

    /**
     * 初始支数必须是正整数（SSOT：{@code >0}）。
     *
     * @param initQty 冻存数量 / 支
     * @return 原值
     */
    public static int requirePositiveInitQty(Integer initQty) {
        if (initQty == null) {
            throw new ServiceException("冻存数量不能为空", 400);
        }
        if (initQty <= 0) {
            throw new ServiceException("冻存数量必须是正整数（当前：" + initQty + "）", 400);
        }
        return initQty;
    }

    /**
     * 是否暂存 -80：只认 Y / N 两个按钮值。
     *
     * @param inMinus80 入参
     * @return 归一化后的 Y / N
     */
    public static String requireInMinus80(String inMinus80) {
        String value = inMinus80 == null ? null : inMinus80.trim().toUpperCase();
        if (!"Y".equals(value) && !"N".equals(value)) {
            throw new ServiceException("「暂存 -80 度超低温冰箱」只能是 Y（是）或 N（否）", 400);
        }
        return value;
    }

    /**
     * 转液氮时间不得早于冻存时间（FLOW:F-CRYO-01.step4；accept 2 第 5 段点名这一形态）。
     *
     * @param freezeTime 冻存时间
     * @param toLn2Time  转移至液氮时间（可为空 = 还没转）
     * @throws ServiceException 早于冻存时间
     */
    public static void requireLn2NotBeforeFreeze(java.time.LocalDate freezeTime, java.time.LocalDate toLn2Time) {
        if (freezeTime == null || toLn2Time == null) {
            return;
        }
        if (toLn2Time.isBefore(freezeTime)) {
            throw new ServiceException("转移至液氮时间（" + toLn2Time + "）不能早于冻存时间（" + freezeTime + "）", 400);
        }
    }

    /**
     * 液氮储存位置判据：<b>直接进液氮（{@code in_minus80='N'}）或已登记转液氮
     * （{@code to_ln2_time} 非空）时必填</b>（FIELD:t_lqg_cryo_batch.ln2_location；
     * accept 2 第 3 段点名「东西进了液氮罐却没人知道在哪」要拒）。
     *
     * <p>不需要位置的场合（暂存 -80 且没转液氮）原样返回入参，<b>不校验、不清空</b>。
     *
     * @param inMinus80   暂存 -80 标志（Y / N）
     * @param toLn2Time   转移至液氮时间（可空）
     * @param ln2Location 入参里的位置
     * @return 归一化后的位置（不需要时 = 入参原值）
     * @throws ServiceException 需要位置但没给
     */
    public static String requireLn2Location(String inMinus80, java.time.LocalDate toLn2Time, String ln2Location) {
        boolean needsLocation = "N".equals(inMinus80) || toLn2Time != null;
        if (!needsLocation) {
            return ln2Location;
        }
        if (StringUtils.isBlank(ln2Location)) {
            throw new ServiceException("直接进液氮（暂存 -80 选「否」）必须填液氮储存位置", 400);
        }
        return ln2Location.trim();
    }

    /**
     * 位置判据（读时算，不落库）：{@code in_minus80='N'}（直接进液氮）<b>或</b>
     * {@code to_ln2_time} 非空（已登记转液氮）→ {@code ln2}；否则 {@code minus80}。
     *
     * <p>★ 只 {@code in_minus80='Y'} 一条不够（accept 3 counterfeit 点名）：seed 的 3003
     * 是先 -80 后转液氮的，只看 {@code in_minus80} 会把它误判成 {@code minus80}。
     *
     * @param inMinus80  暂存 -80 标志
     * @param toLn2Time  转液氮时间
     * @return {@code ln2} / {@code minus80}
     */
    public static String locationOf(String inMinus80, java.time.LocalDate toLn2Time) {
        if ("N".equals(inMinus80) || toLn2Time != null) {
            return LOCATION_LN2;
        }
        return LOCATION_MINUS80;
    }

    /** 液氮（{@code lqg_cryo_location} 字典的 value）。 */
    public static final String LOCATION_LN2 = "ln2";

    /** -80 超低温冰箱（{@code lqg_cryo_location} 字典的 value）。 */
    public static final String LOCATION_MINUS80 = "minus80";

}
