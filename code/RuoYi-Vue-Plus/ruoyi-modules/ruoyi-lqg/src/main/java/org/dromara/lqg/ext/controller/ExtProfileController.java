package org.dromara.lqg.ext.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.group.service.ExtProfileUpdateService;
import org.dromara.lqg.ext.domain.bo.ExtProfileUpdateBo;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部填 / 改自己的姓名、单位、组别：{@code PUT /mp/ext/profile}
 * （doc/api-contract.md，FLOW:F-AUTH-03.step2 / step4，UI:mp.me.profile）。
 *
 * <p>只写当前登录人自己那一行；保存成功后档案回到 {@code bind_status='pending'}，
 * 小程序「我的」页显示「待核验」，等实验室在「外部用户」页核验。
 *
 * @author AUTH-GROUP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ext")
public class ExtProfileController {

    private final ExtProfileUpdateService extProfileUpdateService;

    /**
     * 保存档案。
     *
     * <p>返回 {@code R<Void>}（形状与契约的 {@code → bindStatus=pending} 一致：前端保存后
     * 重新拉 {@code GET /mp/me} 拿新的状态，不靠这个响应体）。
     */
    @PutMapping("/profile")
    public R<Void> save(@Valid @RequestBody ExtProfileUpdateBo bo) {
        extProfileUpdateService.save(bo);
        return R.ok();
    }

}
