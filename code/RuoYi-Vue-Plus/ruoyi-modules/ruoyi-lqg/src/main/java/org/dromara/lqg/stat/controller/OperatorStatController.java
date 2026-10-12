package org.dromara.lqg.stat.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo;
import org.dromara.lqg.stat.service.OperatorStatService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 操作人统计（CR-20261012-22）：按人、按时间段看四张表各做了多少。
 *
 * <pre>
 * GET /lqg/stat/operator?begin=yyyy-MM-dd&amp;end=yyyy-MM-dd     工作台
 * GET /mp/int/stat/operator?begin=…&amp;end=…                    小程序（内部人员）
 * </pre>
 * {@code begin} / {@code end} 都可不带：都不带 = 全部；只带 {@code end} = 这一天及之前；只带 {@code begin} = 这一天及之后。
 *
 * <p>★ 鉴权是「内部角色闸」（与工作台首页、选人名单同一个做法），外部账号一律 403。
 * 不挂权限串：全员可见（Kevin 2026-10-12），不让它因为少一行授权而打不开。
 *
 * @author CR-20261012-22
 */
@RequiredArgsConstructor
@RestController
public class OperatorStatController {

    private final OperatorStatService operatorStatService;

    /**
     * 工作台：操作人统计。
     */
    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
    @GetMapping("/lqg/stat/operator")
    public R<OperatorStatVo> stat(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return R.ok(operatorStatService.stat(begin, end));
    }

    /**
     * 小程序（内部人员）：操作人统计。
     */
    @SaCheckRole("lqg_internal")
    @GetMapping("/mp/int/stat/operator")
    public R<OperatorStatVo> mpStat(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return R.ok(operatorStatService.stat(begin, end));
    }

}
