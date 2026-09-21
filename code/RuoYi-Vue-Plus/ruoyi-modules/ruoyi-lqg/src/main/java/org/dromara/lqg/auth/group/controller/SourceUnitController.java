package org.dromara.lqg.auth.group.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.group.domain.bo.SourceUnitBo;
import org.dromara.lqg.auth.group.domain.bo.StatusBo;
import org.dromara.lqg.auth.group.domain.vo.SourceUnitVo;
import org.dromara.lqg.auth.group.service.SourceUnitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 来源单位维护（doc/api-contract.md 的 {@code GET/POST/PUT/DELETE /lqg/auth/unit}，UI:admin.auth.unit 左栏）。
 *
 * <pre>
 * GET  /lqg/auth/unit             列表（含停用项 —— 工作台要能重新启用）
 * POST /lqg/auth/unit             新增
 * PUT  /lqg/auth/unit/{id}        改名 / 改备注
 * PUT  /lqg/auth/unit/{id}/status 启用 / 停用
 * </pre>
 *
 * <p>★ 契约里写的 `DELETE` **本票不做**：单位不物理删（ticket §2.2 与 §1 的「启用 / 停用」口径），
 * 删掉会静默毁掉已绑定外部用户的可见范围。启停走 {@code PUT …/status}，按钮权限串是
 * {@code lqg:auth:unit:toggle}（不是 {@code …:remove}）。这条与契约的字面差异记在完工报告里。
 *
 * @author AUTH-GROUP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/auth/unit")
public class SourceUnitController {

    private final SourceUnitService sourceUnitService;

    /**
     * 单位列表（带组别数）。
     */
    @SaCheckPermission("lqg:auth:unit:list")
    @GetMapping
    public R<List<SourceUnitVo>> list() {
        return R.ok(sourceUnitService.list());
    }

    /**
     * 新增单位。
     */
    @SaCheckPermission("lqg:auth:unit:add")
    @PostMapping
    public R<Long> add(@Valid @RequestBody SourceUnitBo bo) {
        return R.ok(sourceUnitService.create(bo));
    }

    /**
     * 改名 / 改备注。
     */
    @SaCheckPermission("lqg:auth:unit:edit")
    @PutMapping("/{unitId}")
    public R<Void> edit(@PathVariable Long unitId, @Valid @RequestBody SourceUnitBo bo) {
        sourceUnitService.update(unitId, bo);
        return R.ok();
    }

    /**
     * 启用 / 停用（不物理删）。已绑定的人不受影响。
     */
    @SaCheckPermission("lqg:auth:unit:toggle")
    @PutMapping("/{unitId}/status")
    public R<Void> toggleStatus(@PathVariable Long unitId, @Valid @RequestBody StatusBo bo) {
        sourceUnitService.toggleStatus(unitId, bo.getStatus());
        return R.ok();
    }

}
