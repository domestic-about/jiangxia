package org.dromara.lqg.cryo.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.lqg.cryo.flow.domain.bo.CryoFlowEditBo;
import org.dromara.lqg.cryo.flow.domain.bo.CryoFlowSubmitBo;
import org.dromara.lqg.cryo.flow.domain.bo.CryoToLn2Bo;
import org.dromara.lqg.cryo.flow.domain.vo.CryoFlowRecordVo;
import org.dromara.lqg.cryo.flow.service.CryoFlowService;
import org.dromara.lqg.cryo.batch.service.CryoQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 冻存出入库登记与转液氮（doc/api-contract.md 的 CRYO 一节，CRYO-FLOW-001）。
 *
 * <pre>
 * POST   /lqg/cryo/batch/{id}/flow            登记：take 取走 / add 补入 / adjust 盘点调整   lqg:cryo:flow
 * PUT    /lqg/cryo/batch/{id}/flow/{flowId}   改一笔登记（类型不可改；记修改人）            lqg:cryo:flow
 * DELETE /lqg/cryo/batch/{id}/flow/{flowId}   软删一笔登记                                   lqg:cryo:flow
 * PUT    /lqg/cryo/batch/{id}/to-ln2          登记转液氮（批次位置当场变液氮）              lqg:cryo:edit
 * GET    /lqg/cryo/batch/{id}/flows           未删流水，时间倒序，每行带操作后剩余           lqg:cryo:query
 * </pre>
 *
 * <p>★ <b>写接口仍只有这一套</b>，但调用方从 2026-09-24 起是<b>两端</b>：甲方看设计稿 v3 后要求
 * 「小程序和工作台界面都能操作」冻存取用登记，小程序内部人员的批次详情弹层（取走 / 补入 / 转液氮 /
 * 改删登记）<b>直接调本类这几个路径</b>，不在 {@code /mp/int/cryo/**} 上另开转发口 —— 同一个
 * {@code CryoFlowService}、同一把批次行锁、同一套逐笔校验，两端规则一字不差。
 * 能调通靠的是权限串：小程序内部人员的账号带 {@code lqg_internal}（角色 102），5407 / 5404
 * 本来就授给它；外部角色（103）没有这些权限串，照旧 403。小程序里<b>不做</b>盘点调整（只在工作台）。
 *
 * <p>★ 权限串的来源：{@code lqg:cryo:flow} = {@code sys_menu} 5407，由上游 CRYO-MODEL-001 的迁移
 * {@code V202609241200__CRYO-MODEL-001-cryo.sql} 落的<b>权限行</b>（本票的 {@code touches} 里没有迁移）。
 * 缺它是 <b>403（不是 500）</b>——新加 {@code @SaCheckPermission} 前先查 {@code sys_menu.perms}。
 *
 * <p>★ {@code GET …/{id}/flows} 用已有权限行 5402 {@code lqg:cryo:query}（契约把它归在详情读口下），
 * 不新造权限串：能看批次详情的人本来就该能看它的流水。
 *
 * @author CRYO-FLOW-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/cryo/batch/{id}")
public class CryoFlowController {

    private final CryoFlowService cryoFlowService;
    private final CryoQueryService cryoQueryService;

    /**
     * 登记一笔流水（取走 / 补入 / 盘点调整）。
     */
    @SaCheckPermission("lqg:cryo:flow")
    @PostMapping("/flow")
    public R<Long> add(@PathVariable("id") Long id, @Valid @RequestBody CryoFlowSubmitBo bo) {
        return R.ok(cryoFlowService.create(id, bo));
    }

    /**
     * 改一笔登记。{@code flowId} 不属于这个批次 / 已删 → 404。
     */
    @SaCheckPermission("lqg:cryo:flow")
    @PutMapping("/flow/{flowId}")
    public R<Void> edit(@PathVariable("id") Long id, @PathVariable("flowId") Long flowId,
                        @Valid @RequestBody CryoFlowEditBo bo) {
        cryoFlowService.update(id, flowId, bo);
        return R.ok();
    }

    /**
     * 软删一笔登记（不是物理删：追溯不能断）。
     */
    @SaCheckPermission("lqg:cryo:flow")
    @DeleteMapping("/flow/{flowId}")
    public R<Void> remove(@PathVariable("id") Long id, @PathVariable("flowId") Long flowId) {
        cryoFlowService.delete(id, flowId);
        return R.ok();
    }

    /**
     * 登记转液氮（{@code toLn2Time} 不早于冻存时间；保存后批次位置 = 液氮）。
     */
    @SaCheckPermission("lqg:cryo:edit")
    @PutMapping("/to-ln2")
    public R<Void> toLn2(@PathVariable("id") Long id, @Valid @RequestBody CryoToLn2Bo bo) {
        cryoFlowService.toLn2(id, bo);
        return R.ok();
    }

    /**
     * 某批次的流水（时间倒序；每行带 {@code balanceAfter} / {@code edited} / {@code updateByName}）。
     */
    @SaCheckPermission("lqg:cryo:query")
    @GetMapping("/flows")
    public R<List<CryoFlowRecordVo>> flows(@PathVariable("id") Long id) {
        if (cryoQueryService.entity(id) == null) {
            throw new ServiceException("冻存批次不存在", 400);
        }
        return R.ok(cryoFlowService.list(id));
    }

}
