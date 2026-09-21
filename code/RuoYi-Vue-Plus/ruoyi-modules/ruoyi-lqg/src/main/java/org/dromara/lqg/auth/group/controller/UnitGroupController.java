package org.dromara.lqg.auth.group.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.auth.group.domain.bo.StatusBo;
import org.dromara.lqg.auth.group.domain.bo.UnitGroupBo;
import org.dromara.lqg.auth.group.domain.vo.UnitGroupVo;
import org.dromara.lqg.auth.group.service.UnitGroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组别维护（doc/api-contract.md 的 {@code GET/POST/PUT/DELETE /lqg/auth/group}，UI:admin.auth.unit 右栏）。
 *
 * <pre>
 * GET  /lqg/auth/group?unitId=     某单位下的组别（行上带读时 verifiedCount）
 * POST /lqg/auth/group             新增
 * PUT  /lqg/auth/group/{id}        改名 / 改备注
 * PUT  /lqg/auth/group/{id}/status 启用 / 停用
 * </pre>
 *
 * <p>同 {@code SourceUnitController}：契约里的 {@code DELETE} 本票不做（组别不物理删）。
 * 已有人绑定的组别停用后**已绑定的人不受影响** —— 这是 ticket §2.2 明确的口径。
 *
 * @author AUTH-GROUP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/lqg/auth/group")
public class UnitGroupController {

    private final UnitGroupService unitGroupService;

    /**
     * 单位下的组别列表（带 {@code verifiedCount}）。
     */
    @SaCheckPermission("lqg:auth:group:list")
    @GetMapping
    public R<List<UnitGroupVo>> list(@RequestParam(value = "unitId", required = false) Long unitId) {
        return R.ok(unitGroupService.list(unitId));
    }

    /**
     * 新增组别。
     */
    @SaCheckPermission("lqg:auth:group:add")
    @PostMapping
    public R<Long> add(@Valid @RequestBody UnitGroupBo bo) {
        return R.ok(unitGroupService.create(bo));
    }

    /**
     * 改名 / 改备注。
     */
    @SaCheckPermission("lqg:auth:group:edit")
    @PutMapping("/{groupId}")
    public R<Void> edit(@PathVariable Long groupId, @Valid @RequestBody UnitGroupBo bo) {
        unitGroupService.update(groupId, bo);
        return R.ok();
    }

    /**
     * 启用 / 停用（不物理删）。
     */
    @SaCheckPermission("lqg:auth:group:toggle")
    @PutMapping("/{groupId}/status")
    public R<Void> toggleStatus(@PathVariable Long groupId, @Valid @RequestBody StatusBo bo) {
        unitGroupService.toggleStatus(groupId, bo.getStatus());
        return R.ok();
    }

}
