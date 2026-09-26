package org.dromara.lqg.ext.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.service.ConfigService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 系统参数 {@code lqg.ext.show-internal-no} 的读取（CR-20260918-07 / REQ-AUTH-012，OQ-2 已关）。
 *
 * <p>★ <b>从 {@code application.yml} 配置项改成若依 {@code sys_config} 系统参数</b>：
 * 甲方在工作台「系统管理 → 参数设置」里自己改，<b>运行时生效</b>，不用发版。
 * 那一行由 SYS-WEB-001 的迁移 {@code V202609210830} 插入（{@code config_key='lqg.ext.show-internal-no'}，
 * {@code config_value='false'}），AUTH-EXT-002 的迁移是「已存在则跳过」。
 *
 * <p>★ 注入的是 {@code ConfigService}（{@code ruoyi-system} 的 {@code SysConfigServiceImpl}
 * 同时实现了 {@code ISysConfigService} 与它，取值的 {@code selectConfigByKey} 带
 * {@code @Cacheable(CacheNames.SYS_CONFIG)}）→ 本类<b>不持有任何 {@code *Mapper}</b>，
 * ADR-0004 的 I4 不受影响。
 *
 * <p>★ <b>关着连键都不出</b>：本类返回 false 时 {@code ExtSampleDetailVo.internalNo} 保持 null，
 * 由 {@code @JsonInclude(NON_NULL)} 把整个键省掉（accept 第 2 条的 keys 差集断言就是钉这个）。
 *
 * <p>★ 甲方 2026-09-24 意见第 23 行起，同一个开关也管<b>外部版质控文档</b>里的「内部编号」一格：
 * doc 域的 {@code DocRenderModelFactory#showsInternalNo} 读的就是本类（读口只有这一个，
 * 「读不到就当关」的姿态两边一致）。
 *
 * @author AUTH-EXT-001
 */
@Service
@RequiredArgsConstructor
public class ExtInternalNoSwitch {

    /**
     * 参数键名（逐字；契约与 CR 都用这个串）
     */
    public static final String CONFIG_KEY = "lqg.ext.show-internal-no";

    private final ConfigService configService;

    /**
     * 外部接口要不要带内部编号。缺行 / 读不到 / 值不是 true → false（**默认关**）。
     *
     * <p>「读不到就当关」是刻意的失败姿态：这个参数没配好时宁可少给字段，也不能把内部编号漏出去。
     */
    public boolean enabled() {
        try {
            String value = configService.getConfigValue(CONFIG_KEY);
            return StringUtils.isNotBlank(value) && Boolean.parseBoolean(value.trim());
        } catch (Exception e) {
            return false;
        }
    }

}
