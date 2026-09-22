package org.dromara.lqg.sample.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.lqg.sample.domain.bo.SampleQueryBo;
import org.dromara.lqg.sample.domain.bo.SampleSubmitBo;
import org.dromara.lqg.sample.domain.vo.SampleVo;
import org.dromara.lqg.sample.service.SampleQueryService;
import org.dromara.lqg.sample.service.SampleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 样本主档的内部增删改查（doc/api-contract.md 的 SAMPLE 一节）。
 *
 * <pre>
 * GET    /lqg/sample/list      总表（本票支持 sampleKind / verifyStatus / internalNo / donorName / hospitalNo）
 * GET    /lqg/sample/{id}      详情（带 updateByName / updateTime）
 * POST   /lqg/sample           内部新增 → submit_source='internal'、verify_status='valid'
 * PUT    /lqg/sample           内部修改（submitNo / submitSource / submitterId 不可改）
 * DELETE /lqg/sample/{ids}     软删（软删后内部编号可重用）
 * </pre>
 *
 * <p>权限串 {@code lqg:sample:{list,query,add,edit,remove}}，与 Flyway 迁移里 5200-5205 的菜单 perms
 * 逐字一致；101（lqg_admin）与 102（lqg_internal）都授了（内部人员是录样本的主力）。
 *
 * <p>★ 本票只做**内部**接口：外部提交（AUTH-EXT-001）、核验状态机与 {@code /verify}
 * （SAMPLE-VERIFY-001）、导出（SAMPLE-EXPORT-001）、工作台页面（SAMPLE-WEB-001）都不在这里。
 *
 * @author SAMPLE-MODEL-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/sample")
public class SampleController {

    private final SampleService sampleService;
    private final SampleQueryService sampleQueryService;

    /**
     * 总表（分页）。
     */
    @SaCheckPermission("lqg:sample:list")
    @GetMapping("/list")
    public TableDataInfo<SampleVo> list(SampleQueryBo query) {
        return sampleQueryService.list(query);
    }

    /**
     * 详情。已软删 / 不存在一律按「不存在」处理（不泄露存在性）。
     */
    @SaCheckPermission("lqg:sample:query")
    @GetMapping("/{id}")
    public R<SampleVo> detail(@PathVariable Long id) {
        SampleVo vo = sampleQueryService.detail(id);
        if (vo == null) {
            throw new ServiceException("样本不存在");
        }
        return R.ok(vo);
    }

    /**
     * 内部新增。
     */
    @SaCheckPermission("lqg:sample:add")
    @PostMapping
    public R<Long> add(@Valid @RequestBody SampleSubmitBo bo) {
        return R.ok(sampleService.create(bo));
    }

    /**
     * 内部修改。
     */
    @SaCheckPermission("lqg:sample:edit")
    @PutMapping
    public R<Void> edit(@Valid @RequestBody SampleSubmitBo bo) {
        sampleService.update(bo);
        return R.ok();
    }

    /**
     * 软删（契约是 {@code /{ids}} 逗号分隔，与若依上游删法一致）。
     */
    @SaCheckPermission("lqg:sample:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable String ids) {
        sampleService.remove(ids);
        return R.ok();
    }

}
