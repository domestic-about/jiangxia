package org.dromara.lqg.auth.group.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.group.domain.bo.ExtVerifyBo;
import org.dromara.lqg.auth.group.domain.vo.ExtUserVo;
import org.dromara.lqg.auth.group.service.ExtUserVerifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外部用户与组别核验（doc/api-contract.md，UI:admin.auth.extuser，FLOW:F-AUTH-03.step3）。
 *
 * <pre>
 * GET /lqg/auth/ext-user/list                  列表：姓名、手机号、单位、组别（自填的标「自填」）、
 *                                              核验状态、提交样本数、最近登录（全部读时算）
 * PUT /lqg/auth/ext-user/{userId}/verify       核验：approve / reject + 归口决定
 * </pre>
 *
 * <p>拒绝**只走业务码，不动 HTTP 状态码**：上游 {@code GlobalExceptionHandler} 对
 * {@code ServiceException} 的约定是 HTTP 200 + {@code code:500}，前端 {@code utils/request.ts}
 * 按 {@code code} 分支把 {@code msg} 原样弹给用户（AUTH-STAFF-001 报告坑 2 踩过）。
 *
 * @author AUTH-GROUP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/auth/ext-user")
public class ExtUserController {

    private final ExtUserVerifyService extUserVerifyService;

    /**
     * 外部用户列表。
     *
     * @param bindStatus 状态筛选（unbound / pending / verified / rejected），空 = 全部
     * @param unitId     单位筛选，空 = 全部
     */
    @SaCheckPermission("lqg:auth:extuser:list")
    @GetMapping("/list")
    public R<List<ExtUserVo>> list(@RequestParam(value = "bindStatus", required = false) String bindStatus,
                                   @RequestParam(value = "unitId", required = false) Long unitId) {
        return R.ok(extUserVerifyService.list(bindStatus, unitId));
    }

    /**
     * 核验：通过（自填的必须带「新建」或「归并」）或驳回（原因必填）。
     */
    @SaCheckPermission("lqg:auth:extuser:verify")
    @PutMapping("/{userId}/verify")
    public R<Void> verify(@PathVariable Long userId, @Valid @RequestBody ExtVerifyBo bo) {
        extUserVerifyService.verify(userId, bo);
        return R.ok();
    }

}
