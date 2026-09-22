package org.dromara.lqg.sample.verify.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.sample.verify.SampleVerifyBo;
import org.dromara.lqg.sample.verify.SampleVerifyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 样本核验（doc/api-contract.md 的 {@code PUT /lqg/sample/{id}/verify}）。
 *
 * <pre>
 * PUT /lqg/sample/{id}/verify   {action:"valid"|"invalid", receiveDate, internalNo, isFixed,
 *                                processTime, hasQcSheet, hasViabilityReport, operatorName, reason}
 * </pre>
 *
 * <p>★ 权限串 {@code lqg:sample:verify}，与 Flyway 迁移
 * {@code V202609221001__SAMPLE-VERIFY-001-verify-menu.sql} 里菜单 5206 的 perms 逐字一致，
 * 授给 101（lqg_admin）与 102（lqg_internal）。本票之前那张（SAMPLE-MODEL-001）只建到 5205，
 * 没有这个权限串 —— 缺了它对 {@code --as staff} 恒 403，见完工报告的 WARN 清单。
 *
 * <p>★ 本 controller 只是状态的**入口**：转移合法性、必填、唯一性、下游记录检查全在
 * {@link SampleVerifyService} / {@code VerifyTransitions} 里，外部接口（AUTH-EXT-001）与石蜡包埋
 * 核验（EMBED-MODEL-001）复用同一份判据。
 *
 * @author SAMPLE-VERIFY-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/sample")
public class SampleVerifyController {

    private final SampleVerifyService sampleVerifyService;

    /**
     * 核验 / 改判。
     */
    @SaCheckPermission("lqg:sample:verify")
    @PutMapping("/{id}/verify")
    public R<Void> verify(@PathVariable Long id, @Valid @RequestBody SampleVerifyBo bo) {
        sampleVerifyService.verify(id, bo);
        return R.ok();
    }

}
