package org.dromara.lqg.auth.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.domain.vo.MPUserVo;
import org.dromara.lqg.auth.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前身份：{@code GET /mp/me}（doc/api-contract.md「AUTH」一节）。
 *
 * <p>内外部都能调，不带任何样本数据。不加 {@code @SaIgnore} 也不加 {@code @SaCheckRole}：
 * 匿名访问落在 Sa-Token 全局拦截器上（401），登录后不分角色都能读自己这一行。
 *
 * @author AUTH-LOGIN-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp")
public class MPMemberController {

    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public R<MPUserVo> me() {
        return R.ok(currentUserService.currentUser());
    }

}
