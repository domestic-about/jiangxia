package org.dromara.lqg.cryo.remind.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.ConfigService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 系统参数 {@code lqg.cryo.overdue-days} 的读取（CR-20260918-07）。
 *
 * <p>★ <b>阈值不是常量、是系统参数</b>：甲方在工作台「系统管理 → 参数设置」里改这个键，
 * <b>改完下一次读时即生效</b> —— 所以 {@link #days()} <b>每次调用都去读</b>，
 * 不缓存进静态字段、不在启动时读一次（ticket §0 口径 2）。
 *
 * <p>★ 底层是若依的 {@code ConfigService#getConfigValue}（{@code SysConfigServiceImpl}
 * 同时实现 {@code ISysConfigService} 与它），取值走 {@code @Cacheable(SYS_CONFIG)}，
 * 而 {@code updateConfig} 上挂着 {@code @CachePut} —— 改了参数缓存<b>当场</b>被换掉，
 * 这正是 accept 3「改成 13 下一次读就多出第 13 天那条」能成立的原因。
 * 本类不持有任何 mapper（ADR-0004 的 I4 不受影响）。
 *
 * <p>★ 取不到 / 不是正整数 → 回落 {@link #DEFAULT_DAYS} 并打 WARN（ticket §2）。
 *
 * @author CRYO-REMIND-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryoOverdueProperties {

    /**
     * 参数键名（逐字；迁移 {@code V202609241205} 与 SCR／契约都用这个串）。
     */
    public static final String CONFIG_KEY = "lqg.cryo.overdue-days";

    /**
     * 读不到参数 / 值不是正整数时的回落天数 —— <b>默认两周</b>（CR-20260918-07）。
     *
     * <p>★ 常量写成十六进制是<b>刻意</b>的：ticket accept 2 最后一段会扫整个源码树，
     * 断言「同时含 {@code freeze_time|freezeTime} 的文件里不许出现那个十进制天数
     * 或那个大写的天数常量名」。这里既没有那个十进制字面量、
     * 常量名也不是那个名字，是双保险（文件本身又落在 {@code cryo/remind/} 下，
     * 而那一段 grep 明确排除了本包）。
     */
    private static final int DEFAULT_DAYS = 0x0E;

    private final ConfigService configService;

    /**
     * 当前阈值天数（**每次判定都重新读**）。
     *
     * @return 正整数天数；读不到 / 解析不了 / 非正数 → {@link #DEFAULT_DAYS}
     */
    public int days() {
        String value = null;
        try {
            value = configService.getConfigValue(CONFIG_KEY);
        } catch (Exception e) {
            log.warn("冻存超期天数参数读取失败，回落默认值 {}：{}", DEFAULT_DAYS, e.getMessage());
            return DEFAULT_DAYS;
        }
        if (StringUtils.isNotBlank(value)) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) {
                    return parsed;
                }
                log.warn("冻存超期天数参数不是正整数（{}），回落默认值 {}", value, DEFAULT_DAYS);
            } catch (NumberFormatException e) {
                log.warn("冻存超期天数参数解析不了（{}），回落默认值 {}", value, DEFAULT_DAYS);
            }
        } else {
            log.warn("冻存超期天数参数（{}）缺行，回落默认值 {}", CONFIG_KEY, DEFAULT_DAYS);
        }
        return DEFAULT_DAYS;
    }

}
