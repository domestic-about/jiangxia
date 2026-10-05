package org.dromara.lqg.sys.cache;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.CacheNames;
import org.dromara.common.redis.utils.CacheUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时清掉字典缓存（{@code sys_dict}）。
 *
 * <p>★ 为什么要有：若依把字典数据缓存在 Redis 里、<b>没有过期时间</b>，只有走后台「字典管理」改时才会刷新。
 * Flyway 迁移直接改 {@code sys_dict_data}（例如 CR-20261005-16 给核验状态补标签颜色）时，Redis 里的旧值会一直留着，
 * 测试站 / 正式站上就看不到迁移的效果。每次部署都会重启后端，所以在启动完成时清一次：之后第一次用到再从库里读。
 * 代价只是启动后第一次读字典多查一次库。
 */
@Slf4j
@Component
public class DictCacheResetOnStartup {

    @EventListener(ApplicationReadyEvent.class)
    public void reset() {
        try {
            CacheUtils.clear(CacheNames.SYS_DICT);
            log.info("启动时已清空字典缓存 {}（迁移改过的字典立即生效）", CacheNames.SYS_DICT);
        } catch (Exception e) {
            // 清不掉不影响启动：最坏情况是迁移改的字典要等后台改一次才生效
            log.warn("启动时清空字典缓存失败：{}", e.getMessage());
        }
    }
}
