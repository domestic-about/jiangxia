package org.dromara.lqg.doc.publish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.ext.service.ExtInternalNoSwitch;
import org.dromara.system.domain.bo.SysConfigBo;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 系统参数 {@code lqg.ext.show-internal-no} 一改，就把外部版里<b>按旧设置出的</b>质控文档排队重出
 * （甲方 2026-09-24 意见第 23 行：开关也管外部版文档，切换后已完成的文档不用人工重新「完成并同步」）。
 *
 * <p>★ 为什么用 AOP：参数是在若依的「系统管理 → 参数设置」里改的（{@code ISysConfigService}，框架代码，
 * 本项目不动它 —— 与 {@code WorkbenchLoginGuardAspect} 同一个理由）。这里在<b>本模块</b>对它的
 * 增 / 改 / 删 / 刷新缓存做后置通知，只做一件事：请 {@link DocRenderService#requestExternalRefresh} 扫一遍。
 *
 * <p>★ 为什么是 {@link Ordered#HIGHEST_PRECEDENCE}：{@code updateConfig} 上挂着
 * {@code @CachePut}，开关的新值要等缓存切面写完才读得到。本切面排在最外层，它的「返回之后」
 * 发生在缓存切面写完之后，扫描读到的一定是新值（扫描本身还在后台单线程里跑，不拖慢保存）。
 *
 * <p>★ 本类<b>不是</b>安全边界：开关关着时「印了内部编号的外部版不许发出去」由读路径上的
 * {@code DocRenderService#delivery} 逐份核（改库再刷缓存、进程重启这类绕过参数设置页的切换，照样挡得住）。
 * 这里只负责让重出尽早开始。
 *
 * @author G 批 C 组（内部编号开关作用到外部版文档）
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DocInternalNoSwitchAspect {

    private final DocRenderService renderService;

    /**
     * 新增 / 修改一个参数之后：改的是这个开关才扫。
     */
    @AfterReturning("execution(* org.dromara.system.service.ISysConfigService.insertConfig(..))"
        + " || execution(* org.dromara.system.service.ISysConfigService.updateConfig(..))")
    public void afterSave(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof SysConfigBo bo
            && ExtInternalNoSwitch.CONFIG_KEY.equals(bo.getConfigKey())) {
            log.info("系统参数 {} 改成了「{}」：外部版质控文档按新设置排队重出", bo.getConfigKey(), bo.getConfigValue());
            renderService.requestExternalRefresh(true);
        }
    }

    /**
     * 删参数 / 刷新参数缓存之后：拿不到是改了哪个，一律扫一遍（没有要重出的就什么都不做）。
     */
    @AfterReturning("execution(* org.dromara.system.service.ISysConfigService.deleteConfigByIds(..))"
        + " || execution(* org.dromara.system.service.ISysConfigService.resetConfigCache(..))")
    public void afterBulkChange() {
        renderService.requestExternalRefresh(true);
    }
}
