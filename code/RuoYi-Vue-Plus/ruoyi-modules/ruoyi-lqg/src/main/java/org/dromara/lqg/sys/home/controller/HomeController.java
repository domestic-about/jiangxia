package org.dromara.lqg.sys.home.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.sys.home.domain.vo.HomeRecentVo;
import org.dromara.lqg.sys.home.domain.vo.HomeTodoVo;
import org.dromara.lqg.sys.home.service.HomeCounterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作台首页（UI:admin.home，REQ-SYS-901）—— doc/api-contract.md 的
 * {@code GET /lqg/home/todo} 与 {@code GET /lqg/home/recent}。
 *
 * <pre>
 * GET /lqg/home/todo     → data:{pendingSamples, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}
 * GET /lqg/home/recent   → data:[{submitTime, submitNo, sourceUnitName, submitSource, verifyStatus}, …]
 * </pre>
 *
 * <p>★ <b>登录即可调</b>（ticket §2「登录即可调（101 / 102）」）：两个端点都只挂
 * {@link SaCheckLogin}，<b>不挂权限串、不新建菜单/权限行</b> —— 首页是任何人登录后落地的第一屏，
 * 让它因为少一行 {@code sys_menu} 而 403，是「功能坏了」和「没有待办」分不清的翻版
 * （本票的 accept 正是不许把这两种情况混起来）。
 *
 * <p>★ 端点也<b>不落任何计数缓存</b>：没有 Redis key、没有 {@code @Cacheable}、
 * 没有任何「今日快照」表。五个数每次请求现算（见 {@link HomeCounterService}）。
 *
 * @author SYS-HOME-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/home")
public class HomeController {

    private final HomeCounterService homeCounterService;

    /**
     * 五个待办数（卡片与侧边菜单角标共用这一次请求的结果）。
     */
    @SaCheckLogin
    @GetMapping("/todo")
    public R<HomeTodoVo> todo() {
        return R.ok(homeCounterService.todo());
    }

    /**
     * 最近提交 10 条（送检时间倒序；不含软删的样本）。
     */
    @SaCheckLogin
    @GetMapping("/recent")
    public R<List<HomeRecentVo>> recent() {
        return R.ok(homeCounterService.recent());
    }

}
