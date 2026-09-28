package org.dromara.lqg.ext.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.ext.domain.bo.ExtSampleQueryBo;
import org.dromara.lqg.ext.domain.bo.ExtSampleSubmitBo;
import org.dromara.lqg.ext.domain.vo.ExtSampleDetailVo;
import org.dromara.lqg.ext.domain.vo.ExtSampleVo;
import org.dromara.lqg.ext.service.ExtSampleAssemblyService;
import org.dromara.lqg.ext.service.ExtScopeService;
import org.dromara.lqg.sample.service.ExtSampleSubmitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部「样本记录信息表」的四个端点（doc/api-contract.md 第 50-52 行）：
 *
 * <pre>
 * GET  /mp/ext/sample/list   历史编辑记录的样本页签（可见集合 + sampleKind/verifyStatus/onlyMine 收窄）
 * GET  /mp/ext/sample/{id}   详情（先 assertVisible；不可见按「不存在」回 404）
 * POST /mp/ext/sample        组织样本送检（送检段字段，服务端写死 external/pending/tissue）
 * PUT  /mp/ext/sample/{id}   本人待核验 / 无效的组织样本修改重提
 * </pre>
 *
 * <p>★ <b>类级 {@code @SaCheckRole("lqg_external")}</b>（ADR-0004 的 I1 配套）：
 * 角色 103 <b>不授任何菜单与权限串</b>，所以内部账号打这里天然 403、外部账号打
 * {@code /lqg/**} 与 {@code /mp/int/**} 也天然 403（accept 第 2 条最后三段逐个断）。
 *
 * <p>★ 本类<b>只做转发、不查库</b>：可见范围在 {@link ExtScopeService}（ext 包里唯一允许持有
 * {@code *Mapper} 的实现类），样本读写分别在 {@code ExtSampleAssemblyService} 与
 * {@code org.dromara.lqg.sample.service.ExtSampleSubmitService}（sample 包）—— ADR-0004 的 I4。
 *
 * <p>★ <b>没有</b> {@code GET /mp/ext/home}：CR-20260917-05 删掉了外部首页的数字与最近记录，
 * 留着就是一个没人维护的外部出口（accept 第 2 条断它 404）。
 *
 * @author AUTH-EXT-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ext/sample")
@SaCheckRole("lqg_external")
public class ExtSampleController {

    private final ExtSampleAssemblyService extSampleAssemblyService;
    private final ExtSampleSubmitService extSampleSubmitService;
    private final ExtScopeService extScopeService;

    /**
     * 列表：可见集合 → 按 {@code sampleKind / verifyStatus / onlyMine} 收窄 → 掩码装配。
     */
    @GetMapping("/list")
    public TableDataInfo<ExtSampleVo> list(ExtSampleQueryBo query) {
        return extSampleAssemblyService.list(currentUserId(), query);
    }

    /**
     * 详情：先断言可见（不可见 → 业务码 404、data 为空），再装配白名单字段。
     */
    @GetMapping("/{id}")
    public R<ExtSampleDetailVo> detail(@PathVariable Long id) {
        Long userId = currentUserId();
        extScopeService.assertVisible(userId, id);
        ExtSampleDetailVo vo = extSampleAssemblyService.detail(userId, id);
        if (vo == null) {
            throw new ServiceException("样本不存在", 404);
        }
        return R.ok(vo);
    }

    /**
     * 组织样本送检。
     */
    @PostMapping
    public R<Long> submit(@Valid @RequestBody ExtSampleSubmitBo bo) {
        return R.ok(extSampleSubmitService.submitTissue(currentUserId(), bo));
    }

    /**
     * 组织样本修改重提（本人 + pending/invalid）。
     */
    @PutMapping("/{id}")
    public R<Void> resubmit(@PathVariable Long id, @RequestBody ExtSampleSubmitBo bo) {
        extSampleSubmitService.resubmitTissue(currentUserId(), id, bo);
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
