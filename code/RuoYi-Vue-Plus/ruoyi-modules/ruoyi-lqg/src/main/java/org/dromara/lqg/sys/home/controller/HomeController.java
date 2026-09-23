package org.dromara.lqg.sys.home.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
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
 * <p>★ <b>鉴权是「内部角色闸」，不是「登录门」</b>：两个端点都挂
 * {@code @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)}
 * （101 / 102 / 上游超管，见 {@code V202609210820__SYS-BASE-001-lqg-roles.sql}），
 * 103 {@code lqg_external} 一律 403。
 *
 * <p>★★ <b>这三个角色键必须与 {@code StaffGrantRules.INTERNAL_ROLE_KEYS}
 * （项目「内部角色」的唯一口径来源）逐字一致</b> —— 口径来源指过去：
 * {@code WorkbenchLoginGuardAspect} 正是用那条常量判「能不能登工作台」（AUTH-STAFF-001 §2.2）。
 * 少了 {@code superadmin} 就会出现「上游超管能登进工作台、却打不开首页」的不一致
 * （首页是登录后的第一屏，那就是坏页）。<b>注解里不能引用 {@code List.of(...)} 常量</b>
 * （它不是编译期常量），所以只能硬写这三个字符串，<b>防漂移靠契约测试</b>：
 * {@code HomeCounterContractTest} 第 ⑥ 条断言这个集合<b>恰等于</b>
 * {@code INTERNAL_ROLE_KEYS} —— 将来谁改了常量，那条测试立刻红。
 *
 * <p>★★ <b>{@code mode = SaMode.OR} 不能省</b>：Sa-Token 的 {@code @SaCheckRole} 默认
 * {@code SaMode.AND}（实测本机 sa-token-core 1.45.0 的注解默认值），写成
 * {@code @SaCheckRole({"lqg_admin", "lqg_internal"})} 的语义是「两个角色<b>都必须有</b>」——
 * 而 seed 里 {@code lqgadmin} 只有 101、内部人员只有 102，结果是<b>工作台管理员和内部人员
 * 一起 403</b>（实测：两个端点对 admin / staff 都是 403「没有访问权限，请联系管理员授权」）。
 * 「放行 101 或 102」只能靠 OR。
 *
 * <p>★ <b>为什么不能只挂 {@code @SaCheckLogin}（D7 r1 L3 的 S1 修复）</b>：小程序（mp client）
 * 签发的 token 同样是「已登录」，只挂登录门 = 把工作台首屏交给外部账号 —— 实测五个外部身份
 * 都能读到五个聚合数，以及最近 10 条送检单号 / 来源单位 / 内外部 / 核验状态
 * （跨 A 医院 / B 大学 / 本中心）。工作台那条「外部不得登录工作台」的护栏
 * （AUTH-STAFF-001 §2.2）只拦 <b>pc 密码登录</b>这一条路，mp token 根本不经过它，
 * 所以「登录门 ⇒ 必然是内部」这个前提不成立。外部隔离是本项目由
 * {@code ExtChokepointContractTest} 守着的不变量（ADR-0004 的咽喉体系），
 * 这两个端点绕过了那个咽喉 —— 这是隔离缺陷，不是 cosmetic。
 * ticket §2 写的「登录即可调（101 / 102）」里括注的正是这两个内部角色，本改动按角色读它。
 *
 * <p>★ <b>仍然不挂权限串、不新建菜单/权限行</b>（本票的设计决定不变）：首页是任何人登录后落地的
 * 第一屏，让它因为少一行 {@code sys_menu} 而 403，是「功能坏了」和「没有待办」分不清的翻版
 * （本票的 accept 正是不许把这两种情况混起来）。<b>角色闸与权限串是两件事</b>：角色闸问
 * 「你是不是内部的人」（内外部隔离），权限串问「这个菜单你有没有被授权」——上游 admin 的
 * {@code *:*:*} 答得了后者，答不了前者，所以内外部隔离只能用角色闸表达。
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
     *
     * <p>内部角色闸（{@code mode = SaMode.OR}）：101 {@code lqg_admin} / 102
     * {@code lqg_internal} / 上游 {@code superadmin} 任一即可（= {@code INTERNAL_ROLE_KEYS}），
     * 外部 403。
     */
    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
    @GetMapping("/todo")
    public R<HomeTodoVo> todo() {
        return R.ok(homeCounterService.todo());
    }

    /**
     * 最近提交 10 条（送检时间倒序；不含软删的样本）。
     *
     * <p>内部角色闸（{@code mode = SaMode.OR}）：101 {@code lqg_admin} / 102
     * {@code lqg_internal} / 上游 {@code superadmin} 任一即可（= {@code INTERNAL_ROLE_KEYS}），
     * 外部 403 —— 这一条带跨单位送检单号，比待办数更敏感。
     */
    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
    @GetMapping("/recent")
    public R<List<HomeRecentVo>> recent() {
        return R.ok(homeCounterService.recent());
    }

}
