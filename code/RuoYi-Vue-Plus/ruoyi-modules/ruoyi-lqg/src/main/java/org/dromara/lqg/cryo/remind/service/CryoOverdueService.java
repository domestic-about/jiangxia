package org.dromara.lqg.cryo.remind.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.cryo.batch.domain.CryoBatch;
import org.dromara.lqg.cryo.batch.domain.vo.CryoFlowDeltaRow;
import org.dromara.lqg.cryo.batch.mapper.CryoFlowMapper;
import org.dromara.lqg.cryo.remind.domain.vo.CryoOverdueVo;
import org.dromara.lqg.cryo.remind.mapper.CryoOverdueMapper;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.mapper.SampleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * -80 超期提醒的<b>唯一判定</b>与清单 / 计数（CRYO-REMIND-001，REQ-CRYO-003 / REQ-CRYO-901）。
 *
 * <pre>
 * GET /lqg/cryo/overdue       超期批次清单（按已超天数倒序）
 * （工作台列表的 overdue / overdueDays / tabCounts.overdue、小程序内部管理冻存表格页的
 *   tabCounts.overdue、工作台首页待办计数，四处都调本类的判定 —— 见下方 ★★）
 * </pre>
 *
 * <p>★★ <b>{@link #isOverdue(CryoBatch, int, LocalDate, int)} 是唯一的判定函数</b>
 * （ticket §0 口径 3）。四个条件缺一个都会在真实场景里误报或漏报：
 * <ol>
 *   <li><b>暂存 -80 为是</b>（{@code in_minus80='Y'}）—— 直接进液氮的批次（seed 3007）不算；</li>
 *   <li><b>没登记转液氮</b>（{@code to_ln2_time} 为空）—— 转了就当场退出（seed 3003）；</li>
 *   <li><b>剩余 &gt; 0</b> —— 取空了的批次（seed 3004）不算：提醒人去把一个空盒子转进液氮是误报；</li>
 *   <li><b>冻存满阈值天数</b>（{@code 今天 − freeze_time ≥ 阈值}，<b>第 N 天当天就算</b>，
 *       所以是 {@code >=} 不是 {@code >}：seed 3005 恰好踩在阈值当天 → 红）。</li>
 * </ol>
 * 它是<b>纯函数</b>：不碰库、不读配置、不碰 Spring（阈值当入参传进来），所以边界测得动。
 * 配置读取只在 {@link CryoOverdueProperties#days()} 一处，且**每次判定现读**。
 *
 * <p>★★ <b>读时算，不落「是否超期」标志位</b>（ticket §0 口径 4）：没有任何定时任务刷新某个标志列，
 * 每天的 {@code @Scheduled} 只写一行日志。所以登记转液氮（{@code to_ln2_time} 落值）或
 * 支数被取空（剩余 = 0）之后，这一条<b>立刻</b>退出清单、提醒消失（CR-20260918-07，
 * 甲方 9-18 问「转移后还会有提示吗」——答：不会）。
 *
 * <p>★ <b>SQL 侧同口径的那一份</b>是 {@code CryoOverdueSqlProvider.WHERE}（只写一份），
 * 清单与计数拼同一段 —— {@link #countOverdue()} 与 {@code listOverdue().size()} 必须恒等。
 *
 * @author CRYO-REMIND-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoOverdueService {

    private final CryoOverdueMapper cryoOverdueMapper;
    private final CryoFlowMapper cryoFlowMapper;
    private final SampleMapper sampleMapper;
    private final CryoOverdueProperties overdueProperties;

    // ── 唯一的判定函数 ────────────────────────────────────────────────────────

    /**
     * ★★ <b>唯一的超期判定函数</b>（纯函数：不查库、不读配置、不碰 Spring）。
     *
     * <p>工作台列表行上的 {@code overdue}、超期清单的筛选、页签计数、首页计数，
     * 全部最终归结到这一个方法（SQL 侧同口径片段见
     * {@code CryoOverdueSqlProvider.WHERE}，两者逐条对应）。
     *
     * @param batch      批次（读 {@code in_minus80 / to_ln2_time / freeze_time} 三列）
     * @param remaining  剩余支数（{@code init_qty + SUM(未删流水 delta)}，读时算）
     * @param today      今天（当入参传，别在函数里取系统时间 —— 否则边界测不动）
     * @param overdueDays 阈值天数（来自系统参数，当入参传，别在函数里读配置）
     * @return 这一条此刻算不算超期
     */
    public static boolean isOverdue(CryoBatch batch, int remaining, LocalDate today, int overdueDays) {
        if (batch == null || today == null || overdueDays <= 0) {
            return false;
        }
        // ① 暂存 -80 为是（='N' 是直接进液氮，永不超期）
        if (!"Y".equalsIgnoreCase(batch.getInMinus80())) {
            return false;
        }
        // ② 没登记转液氮（转了就当场退出清单）
        if (batch.getToLn2Time() != null) {
            return false;
        }
        // ③ 剩余 > 0（取空了的不再提醒）
        if (remaining <= 0) {
            return false;
        }
        // ④ 冻存满阈值天数：第 N 天当天就算（>=）
        return elapsedDays(batch.getFreezeTime(), today) >= overdueDays;
    }

    /**
     * 已超天数 = {@code 今天 − 冻存日 − 阈值天数}（阈值当天为 0）。
     *
     * @return 未超期 → {@code null}（列表上「没超期」的键就是空的，不是 0）
     */
    public static Integer overdueDaysOf(CryoBatch batch, int remaining, LocalDate today, int overdueDays) {
        if (!isOverdue(batch, remaining, today, overdueDays)) {
            return null;
        }
        return (int) (elapsedDays(batch.getFreezeTime(), today) - overdueDays);
    }

    /**
     * 冻存到今天的自然天数（{@code today − freezeTime}）。
     */
    static long elapsedDays(LocalDate freezeTime, LocalDate today) {
        if (freezeTime == null || today == null) {
            return Long.MIN_VALUE;
        }
        return ChronoUnit.DAYS.between(freezeTime, today);
    }

    // ── 阈值（唯一取值处，每次现读） ──────────────────────────────────────────

    /**
     * 当前阈值天数 —— 转发给 {@link CryoOverdueProperties#days()}（<b>不缓存</b>）。
     */
    public int days() {
        return overdueProperties.days();
    }

    // ── 计数（给页签与工作台首页计数） ────────────────────────────────────────

    /**
     * 超期批次数 —— 与清单<b>同一段 where</b>（{@code CryoOverdueSqlProvider.SELECT_COUNT}）。
     *
     * <p>消费者：{@code GET /lqg/cryo/batch/list} 的 {@code tabCounts.overdue}、
     * 工作台首页待办卡片与菜单角标（SYS-HOME-001 调它）。清单长度与它恒等。
     */
    public long countOverdue() {
        int days = days();
        return DataPermissionHelper.ignore(() -> cryoOverdueMapper.selectOverdueCount(days));
    }

    // ── 清单（给 GET /lqg/cryo/overdue） ─────────────────────────────────────

    /**
     * 超期批次清单（按已超天数倒序）。
     *
     * <p>行里的 {@code overdueDays} 由 {@link #overdueDaysOf} 算 —— 与行上的 {@code overdue}
     * 及 SQL 片段同一个口径，两条读路径不可能给出不同的「已超 N 天」。
     */
    public List<CryoOverdueVo> listOverdue() {
        int days = days();
        LocalDate today = LocalDate.now();
        return DataPermissionHelper.ignore(() -> {
            List<CryoBatch> rows = cryoOverdueMapper.selectOverdueList(days);
            return assemble(rows, today, days);
        });
    }

    /**
     * 候选批次 → 超期清单 VO（<b>两条批量查询</b>：剩余一条聚合、样本一条 IN；不逐行查）。
     */
    List<CryoOverdueVo> assemble(List<CryoBatch> rows, LocalDate today, int days) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> batchIds = new LinkedHashSet<>();
        Set<Long> sampleIds = new LinkedHashSet<>();
        for (CryoBatch row : rows) {
            if (row != null && row.getId() != null) {
                batchIds.add(row.getId());
            }
            if (row != null && row.getSampleId() != null) {
                sampleIds.add(row.getSampleId());
            }
        }
        Map<Long, Integer> deltas = deltaSums(batchIds);
        Map<Long, Sample> samples = samplesOf(sampleIds);
        List<CryoOverdueVo> out = new ArrayList<>(rows.size());
        for (CryoBatch row : rows) {
            if (row == null) {
                continue;
            }
            int initQty = row.getInitQty() == null ? 0 : row.getInitQty();
            int remaining = initQty + deltas.getOrDefault(row.getId(), 0);
            // ★ 行也过一遍唯一的判定函数：SQL 片段与 Java 判定必须同时说「超期」才出行
            if (!isOverdue(row, remaining, today, days)) {
                continue;
            }
            out.add(toVo(row, remaining, samples.get(row.getSampleId()), today, days));
        }
        return out;
    }

    // ── 内部：装配 ────────────────────────────────────────────────────────────

    private Map<Long, Integer> deltaSums(Collection<Long> batchIds) {
        Map<Long, Integer> deltas = new HashMap<>();
        if (batchIds == null || batchIds.isEmpty()) {
            return deltas;
        }
        List<CryoFlowDeltaRow> deltaRows = cryoFlowMapper.selectDeltaSums(batchIds);
        if (deltaRows != null) {
            for (CryoFlowDeltaRow delta : deltaRows) {
                if (delta != null && delta.getBatchId() != null) {
                    deltas.put(delta.getBatchId(), delta.getTotalDelta() == null ? 0 : delta.getTotalDelta());
                }
            }
        }
        return deltas;
    }

    private Map<Long, Sample> samplesOf(Collection<Long> sampleIds) {
        Map<Long, Sample> samples = new HashMap<>();
        if (sampleIds == null || sampleIds.isEmpty()) {
            return samples;
        }
        List<Sample> found = sampleMapper.selectBatchIds(sampleIds);
        if (found != null) {
            for (Sample sample : found) {
                if (sample != null && sample.getId() != null) {
                    samples.put(sample.getId(), sample);
                }
            }
        }
        return samples;
    }

    private CryoOverdueVo toVo(CryoBatch batch, int remaining, Sample sample, LocalDate today, int days) {
        CryoOverdueVo vo = new CryoOverdueVo();
        vo.setId(batch.getId());
        vo.setSampleId(batch.getSampleId());
        vo.setCryoName(batch.getCryoName());
        vo.setPassage(batch.getPassage());
        vo.setFreezeTime(batch.getFreezeTime());
        vo.setInitQty(batch.getInitQty());
        vo.setRemainingQty(remaining);
        vo.setInMinus80(batch.getInMinus80());
        vo.setToLn2Time(batch.getToLn2Time());
        vo.setLn2Location(batch.getLn2Location());
        vo.setFrozenBy(batch.getFrozenBy());
        vo.setCreateTime(batch.getCreateTime());
        vo.setOverdue(true);
        vo.setOverdueDays(overdueDaysOf(batch, remaining, today, days));
        if (sample != null) {
            vo.setInternalNo(sample.getInternalNo());
        }
        return vo;
    }

}
