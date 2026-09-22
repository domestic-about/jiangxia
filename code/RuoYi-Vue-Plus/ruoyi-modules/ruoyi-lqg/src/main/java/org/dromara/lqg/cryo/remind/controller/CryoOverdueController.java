package org.dromara.lqg.cryo.remind.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.cryo.remind.domain.vo.CryoOverdueVo;
import org.dromara.lqg.cryo.remind.service.CryoOverdueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 超期提醒的内部接口（doc/api-contract.md 的 CRYO 一节）。
 *
 * <pre>
 * GET /lqg/cryo/overdue   超期批次清单（按已超天数倒序；每行 overdueDays = 今天 − 冻存日 − 阈值）
 * </pre>
 *
 * <p>★ 权限串沿用 CRYO-MODEL-001 已落的 <b>5401 {@code lqg:cryo:list}</b>（授给 101 lqg_admin 与
 * 102 lqg_internal）：能看冻存列表的人本来就该能看超期清单，本票<b>不新建权限行、不建菜单</b>
 * （ticket §3 边界；页面菜单归 CRYO-WEB-001 的 5410 段）。外部角色没有这两串 → 403。
 *
 * <p>★ 判定是<b>读时算</b>的：登记转液氮或支数取空之后，这一条下一次请求就不在清单里了。
 *
 * @author CRYO-REMIND-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/cryo")
public class CryoOverdueController {

    private final CryoOverdueService cryoOverdueService;

    /**
     * 超期批次清单。
     */
    @SaCheckPermission("lqg:cryo:list")
    @GetMapping("/overdue")
    public R<List<CryoOverdueVo>> overdue() {
        return R.ok(cryoOverdueService.listOverdue());
    }

}
