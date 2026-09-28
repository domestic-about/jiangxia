package org.dromara.lqg.cryo.remind.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.lqg.cryo.remind.domain.vo.CryoOverdueVo;
import org.dromara.lqg.cryo.remind.service.CryoOverdueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每天 08:00 把当日超期批次数与清单写一行 INFO 日志（ticket §2）。
 *
 * <p>★★ <b>它不是判定依据</b>（ticket §0 口径 4 / FLOW:F-CRYO-01.step2）：
 * 「是否超期」是读时算的，这个任务<b>不刷任何标志位、不写任何缓存</b>，
 * 只在日志里点名一遍，方便运维回溯「那天系统认为哪些该转液氮」。
 * 所以登记转液氮之后，提醒<b>当场</b>消失，不必等到第二天早上 8 点
 * （CR-20260918-07 甲方问「转移后还会有提示吗」——这正是那条 counterfeit 钉的形态）。
 *
 * <p>★ 阈值每天现读（{@link CryoOverdueService#days()} → sys_config），
 * 所以日志里的数字与工作台上看到的数字永远同一口径。
 *
 * <p>★ {@code @EnableScheduling} 在本项目的 dev / prod 下默认没开
 * （{@code snail-job.enabled=false} 关掉了上游 {@code SnailJobConfig} 里的那个注解），
 * 而 ADR-0001 定的是「定时任务用 Spring {@code @Scheduled}」——
 * 所以本包自带 {@link org.dromara.lqg.cryo.remind.config.CryoOverdueScheduleConfig}
 * 把调度器打开。没有它，这条 {@code @Scheduled} 会<b>静默不跑</b>。
 *
 * @author CRYO-REMIND-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryoOverdueDailyJob {

    private final CryoOverdueService cryoOverdueService;

    /**
     * 每天 08:00 点名一次（cron 六段：秒 分 时 日 月 周）。
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void logDailyOverdue() {
        try {
            int days = cryoOverdueService.days();
            List<CryoOverdueVo> rows = cryoOverdueService.listOverdue();
            log.info("冻存超期每日点名：阈值 {} 天，超期 {} 批{}", days, rows.size(),
                rows.stream()
                    .map(row -> " " + row.getId() + "(已超" + row.getOverdueDays() + "天)")
                    .toList());
        } catch (Exception e) {
            // 定时任务不该因为库抖动把调度线程炸掉；下一次照跑
            log.warn("冻存超期每日点名失败：{}", e.getMessage());
        }
    }

}
