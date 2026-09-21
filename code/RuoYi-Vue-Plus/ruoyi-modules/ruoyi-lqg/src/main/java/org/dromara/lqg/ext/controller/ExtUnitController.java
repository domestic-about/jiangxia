package org.dromara.lqg.ext.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.lqg.ext.domain.vo.ExtUnitVo;
import org.dromara.lqg.ext.service.ExtCatalogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外单位—组别选择器：{@code GET /mp/ext/units}（doc/api-contract.md，UI:mp.me.profile）。
 *
 * <p>小程序「我的 → 单位与组别」用它渲染「单位 → 组别」联动选择器；末项「列表里没有，手动填写」
 * 由前端加，服务端只给启用的真单位 / 真组别。
 *
 * <p>ADR-0004 的咽喉：本包（{@code org.dromara.lqg.ext}）的每个 controller 路径都以 {@code /mp/ext}
 * 开头，且只返回 {@code Ext*Vo}（D2 的 {@code ExtChokepointContractTest} 的 i1 / i2 会扫这个包）。
 * 不加 {@code @SaIgnore} 也不加角色注解：匿名访问落在 Sa-Token 全局拦截器上（401），
 * 登录后内外部都能调（选择器在外部填单位之前就要能拉）。
 *
 * @author AUTH-GROUP-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/ext")
public class ExtUnitController {

    private final ExtCatalogQueryService extCatalogQueryService;

    /**
     * 启用的单位及其启用的组别（只有 id 与名称，不带任何人数）。
     */
    @GetMapping("/units")
    public R<List<ExtUnitVo>> units() {
        return R.ok(extCatalogQueryService.activeUnits());
    }

}
