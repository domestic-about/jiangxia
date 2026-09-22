package org.dromara.lqg.ext.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.ext.domain.bo.ExtOrganoidSubmitBo;
import org.dromara.lqg.sample.service.ExtSampleSubmitService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部「类器官收样记录」（doc/api-contract.md 第 53 行，CR-20260917-05）：
 *
 * <pre>
 * POST /mp/ext/organoid       外部填类器官收样（来源单位、类器官类型、备注）
 * PUT  /mp/ext/organoid/{id}  本人待核验 / 无效的类器官样本修改重提
 * </pre>
 *
 * <p>★ 与 {@link ExtSampleController} <b>同一个模式、各一个入参对象</b>：不复用组织样本的 BO，
 * 因为类器官只填三项，夹带 {@code donorName / hospitalNo / tissueType} 等组织样本字段
 * 反序列化不到任何地方（accept 第 3 条断「借组织样本的口改类器官样本」那条）。
 *
 * <p>★ 类级 {@code @SaCheckRole("lqg_external")} 与 I4 的口径同 {@code ExtSampleController}：
 * 读写都在 {@code org.dromara.lqg.sample.service.ExtSampleSubmitService}，本类只转发。
 *
 * @author AUTH-EXT-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ext/organoid")
@SaCheckRole("lqg_external")
public class ExtOrganoidController {

    private final ExtSampleSubmitService extSampleSubmitService;

    /**
     * 类器官收样送检。
     */
    @PostMapping
    public R<Long> submit(@Valid @RequestBody ExtOrganoidSubmitBo bo) {
        return R.ok(extSampleSubmitService.submitOrganoid(currentUserId(), bo));
    }

    /**
     * 类器官收样修改重提（本人 + pending/invalid）。
     */
    @PutMapping("/{id}")
    public R<Void> resubmit(@PathVariable Long id, @RequestBody ExtOrganoidSubmitBo bo) {
        extSampleSubmitService.resubmitOrganoid(currentUserId(), id, bo);
        return R.ok();
    }

    private Long currentUserId() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("未登录", 401);
        }
        return userId;
    }

}
