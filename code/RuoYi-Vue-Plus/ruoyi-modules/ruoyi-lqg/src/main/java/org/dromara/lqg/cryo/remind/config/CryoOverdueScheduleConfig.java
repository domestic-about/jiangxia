package org.dromara.lqg.cryo.remind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开 Spring 的 {@code @Scheduled} 调度器（CRYO-REMIND-001 的每日点名需要它）。
 *
 * <p>★ <b>为什么本票要自带这一个注解</b>：全仓唯一一处 {@code @EnableScheduling} 在上游
 * {@code org.dromara.common.job.config.SnailJobConfig} 上，而它挂着
 * {@code @ConditionalOnProperty(prefix="snail-job", name="enabled", havingValue="true")}——
 * 本项目 dev / prod 都把 {@code snail-job.enabled} 设成 {@code false}（ADR-0001：定时任务用
 * Spring {@code @Scheduled}，不连 SnailJob 调度端）。于是那处注解<b>不生效</b>，
 * 而全仓此前没有任何 Spring {@code @Scheduled} 任务，所以没人发现。
 * 没有本类，{@link org.dromara.lqg.cryo.remind.job.CryoOverdueDailyJob} 的
 * {@code @Scheduled} 会静默不跑（不报错、不打日志）。
 *
 * <p>★ 本类只开调度器，不注册任何任务；任务本体在 {@code job} 包。
 *
 * @author CRYO-REMIND-001
 */
@Configuration
@EnableScheduling
public class CryoOverdueScheduleConfig {
}
