package org.dromara.lqg.ext.service;

import lombok.RequiredArgsConstructor;
import org.dromara.lqg.auth.group.domain.vo.SourceUnitVo;
import org.dromara.lqg.auth.group.domain.vo.UnitGroupVo;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.ext.domain.vo.ExtUnitGroupVo;
import org.dromara.lqg.ext.domain.vo.ExtUnitVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对外单位—组别选择器（{@code GET /mp/ext/units}，UI:mp.me.profile）。
 *
 * <p>★ 两条硬口径：
 * <ol>
 *   <li><b>只含 {@code active}</b>：停用的单位与停用的组别都不返回（外部看不见它们）。</li>
 *   <li><b>只有 id 与名称、不带任何人数</b>：返回值是 {@link ExtUnitVo} / {@link ExtUnitGroupVo}，
 *       **刻意不复用**工作台的 {@code UnitGroupVo}（那个带 {@code verifiedCount}）。
 *       accept 第 3 条会把响应体里所有对象的键名过一遍 {@code test("count|Count")}，命中一个就红。</li>
 * </ol>
 *
 * <p>★ 本类**不持有任何 Mapper**：ADR-0004 的咽喉不变量 i4 规定 ext 包里除
 * {@code ExtScopeServiceImpl} 外任何类都不得持有 {@code *Mapper} 字段
 * （D2 的 {@code ExtChokepointContractTest} 扫整个 ext 包）。读库在
 * {@code org.dromara.lqg.auth.group.service.UnitQueryService} 里，本类只做拼装。
 *
 * @author AUTH-GROUP-001
 */
@Service
@RequiredArgsConstructor
public class ExtCatalogQueryService {

    private final UnitQueryService unitQueryService;

    /**
     * 启用的单位（含各自启用的组别），按创建时间升序。
     */
    public List<ExtUnitVo> activeUnits() {
        List<SourceUnitVo> units = unitQueryService.listUnits();
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        return units.stream()
            .filter(u -> "active".equals(u.getUnitStatus()))
            .map(u -> {
                ExtUnitVo vo = new ExtUnitVo();
                vo.setUnitId(u.getUnitId());
                vo.setUnitName(u.getUnitName());
                List<UnitGroupVo> groups = unitQueryService.listGroups(u.getUnitId());
                vo.setGroups(groups == null ? List.of() : groups.stream()
                    .filter(g -> "active".equals(g.getGroupStatus()))
                    .map(g -> {
                        ExtUnitGroupVo gv = new ExtUnitGroupVo();
                        gv.setGroupId(g.getGroupId());
                        gv.setGroupName(g.getGroupName());
                        return gv;
                    }).collect(Collectors.toList()));
                return vo;
            })
            .collect(Collectors.toList());
    }

}
