package org.dromara.lqg.auth.staff.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.staff.directory.StaffDirectoryService;
import org.dromara.lqg.auth.staff.domain.vo.StaffOptionVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 「选人」用的内部人员名单（CR-20261012-22）：四张表上的操作人 / 包埋人 / 冻存人从这里选。
 *
 * <pre>
 * GET /lqg/staff/options     → data:[{userId, name}, …]   工作台
 * GET /mp/int/staff/options  → 同上                        小程序（内部人员）
 * </pre>
 *
 * <p>★ 与「内部人员授权」页的 {@code GET /lqg/auth/staff} 刻意分开：那个要 {@code lqg:auth:staff:list}
 * （只有管理员有），带手机号与角色；这里<b>每个内部人员都要能调</b>（谁录记录谁选人），只给 id 与姓名。
 *
 * <p>★ 鉴权是「内部角色闸」，不挂权限串：工作台那条与首页同一组角色键
 * （{@code StaffGrantRules.INTERNAL_ROLE_KEYS}），小程序那条与 {@code /mp/int/**} 其它接口同一个角色。
 * 外部账号两条都是 403 —— 名单是中心内部的人名，不给合作单位看。
 *
 * @author CR-20261012-22
 */
@RequiredArgsConstructor
@RestController
public class StaffDirectoryController {

    private final StaffDirectoryService staffDirectoryService;

    /**
     * 工作台：内部人员名单（按姓名排序）。
     */
    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
    @GetMapping("/lqg/staff/options")
    public R<List<StaffOptionVo>> options() {
        return R.ok(staffDirectoryService.options());
    }

    /**
     * 小程序（内部人员）：内部人员名单。
     */
    @SaCheckRole("lqg_internal")
    @GetMapping("/mp/int/staff/options")
    public R<List<StaffOptionVo>> mpOptions() {
        return R.ok(staffDirectoryService.options());
    }

}
