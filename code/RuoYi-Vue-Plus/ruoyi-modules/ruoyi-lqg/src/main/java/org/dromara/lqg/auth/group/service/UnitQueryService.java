package org.dromara.lqg.auth.group.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.domain.UnitGroup;
import org.dromara.lqg.auth.group.domain.vo.SourceUnitVo;
import org.dromara.lqg.auth.group.domain.vo.UnitGroupVo;
import org.dromara.lqg.auth.group.guard.UnitGroupRules;
import org.dromara.lqg.auth.group.mapper.SourceUnitMapper;
import org.dromara.lqg.auth.group.mapper.UnitGroupMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 来源单位 / 组别的**读侧**（UI:admin.auth.unit）。
 *
 * <p>为什么用 {@code JdbcTemplate} 而不是 mapper 的 {@code @Select}：本页要的是「单位行带组别数」、
 * 「组别行带已核验人数」这种**读时算**出来的形状，两张表都在本模块自己手里，
 * 用一句显式 SQL 比拼 XML / 动态注解更直白（同 {@code ExtProfileQueryService} 的口径）。
 *
 * <p>读侧**必须**包 {@link DataPermissionHelper#ignore}：{@code /mp/ext/units} 是外部身份调的，
 * 那一刻 token 里没有部门上下文；而工作台侧将来给这两张表加 {@code @DataPermission} 时，
 * 这里也不该被数据范围静默滤空（AUTH-LOGIN-001 报告坑 1 的形态）。
 *
 * @author AUTH-GROUP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitQueryService {

    private final SourceUnitMapper sourceUnitMapper;
    private final UnitGroupMapper unitGroupMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 单位列表（含停用，工作台要能重新启用）+ 每行的组别数。按创建时间升序（先建的排前面）。
     */
    public List<SourceUnitVo> listUnits() {
        return DataPermissionHelper.ignore(() -> {
            List<SourceUnit> units = sourceUnitMapper.selectList(
                new LambdaQueryWrapper<SourceUnit>().orderByAsc(SourceUnit::getCreateTime, SourceUnit::getId));
            Map<Long, Long> groupCounts = countGroupsByUnit();
            return units.stream().map(u -> {
                SourceUnitVo vo = new SourceUnitVo();
                vo.setUnitId(u.getId());
                vo.setUnitName(u.getUnitName());
                vo.setUnitStatus(u.getUnitStatus());
                vo.setRemark(u.getRemark());
                vo.setCreateTime(u.getCreateTime());
                vo.setGroupCount(groupCounts.getOrDefault(u.getId(), 0L));
                return vo;
            }).toList();
        });
    }

    /**
     * 某个单位下的组别列表（含停用）+ 每行的 {@code verifiedCount}（读时 count）。
     *
     * @param unitId 单位 id；为空时返回空列表（前端在左栏还没选中单位时的空态）
     */
    public List<UnitGroupVo> listGroups(Long unitId) {
        if (unitId == null) {
            return List.of();
        }
        return DataPermissionHelper.ignore(() -> {
            SourceUnit unit = sourceUnitMapper.selectById(unitId);
            String unitName = unit == null ? null : unit.getUnitName();
            List<UnitGroup> groups = unitGroupMapper.selectList(new LambdaQueryWrapper<UnitGroup>()
                .eq(UnitGroup::getUnitId, unitId)
                .orderByAsc(UnitGroup::getCreateTime, UnitGroup::getId));
            Map<Long, Long> verified = countVerifiedByGroup(unitId);
            return groups.stream().map(g -> {
                UnitGroupVo vo = new UnitGroupVo();
                vo.setGroupId(g.getId());
                vo.setUnitId(g.getUnitId());
                vo.setUnitName(unitName);
                vo.setGroupName(g.getGroupName());
                vo.setGroupStatus(g.getGroupStatus());
                vo.setRemark(g.getRemark());
                vo.setCreateTime(g.getCreateTime());
                vo.setVerifiedCount(verified.getOrDefault(g.getId(), 0L));
                return vo;
            }).toList();
        });
    }

    /**
     * 该单位下所有组别的已核验外部人数（一次 GROUP BY，不逐组查）。
     */
    private Map<Long, Long> countVerifiedByGroup(Long unitId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT p.group_id AS group_id, count(*) AS cnt
              FROM t_lqg_ext_profile p
              JOIN t_lqg_unit_group g ON g.id = p.group_id AND g.unit_id = ?
             WHERE p.del_flag = '0' AND p.bind_status = 'verified'
             GROUP BY p.group_id
            """, unitId);
        return rows.stream().collect(Collectors.toMap(
            r -> ((Number) r.get("group_id")).longValue(),
            r -> ((Number) r.get("cnt")).longValue(),
            (a, b) -> a));
    }

    /**
     * 每个单位下的组别数（含停用）。
     */
    private Map<Long, Long> countGroupsByUnit() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT unit_id, count(*) AS cnt
              FROM t_lqg_unit_group
             WHERE del_flag = '0'
             GROUP BY unit_id
            """);
        return rows.stream().collect(Collectors.toMap(
            r -> ((Number) r.get("unit_id")).longValue(),
            r -> ((Number) r.get("cnt")).longValue(),
            (a, b) -> a));
    }

    // ── 给核验用的最小读法 ───────────────────────────────────────────────────

    /**
     * 按 id 取单位（不存在 → null）。
     */
    public SourceUnit findUnit(Long unitId) {
        if (unitId == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> sourceUnitMapper.selectById(unitId));
    }

    /**
     * 按 id 取组别（不存在 → null）。
     */
    public UnitGroup findGroup(Long groupId) {
        if (groupId == null) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> unitGroupMapper.selectById(groupId));
    }

    /**
     * 单位名 → 既有单位（归一化后比较：去空白 + 大小写不敏感），用于「新建」时的幂等提示。
     *
     * @param unitName 名字
     * @return 命中行；没有 → null
     */
    public SourceUnit findUnitByName(String unitName) {
        if (StringUtils.isBlank(unitName)) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            List<SourceUnit> all = sourceUnitMapper.selectList(new LambdaQueryWrapper<>());
            Function<SourceUnit, String> key = u -> UnitGroupRules.normalizeName(u.getUnitName());
            String target = UnitGroupRules.normalizeName(unitName);
            return all.stream().filter(u -> key.apply(u).equals(target)).findFirst().orElse(null);
        });
    }

    /**
     * 同一单位下同名组别（归一化后比较）。
     */
    public UnitGroup findGroupByName(Long unitId, String groupName) {
        if (unitId == null || StringUtils.isBlank(groupName)) {
            return null;
        }
        return DataPermissionHelper.ignore(() -> {
            List<UnitGroup> all = unitGroupMapper.selectList(new LambdaQueryWrapper<UnitGroup>()
                .eq(UnitGroup::getUnitId, unitId));
            String target = UnitGroupRules.normalizeName(groupName);
            return all.stream()
                .filter(g -> UnitGroupRules.normalizeName(g.getGroupName()).equals(target))
                .findFirst().orElse(null);
        });
    }

}
