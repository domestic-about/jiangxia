package org.dromara.lqg.auth.group.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.domain.UnitGroup;
import org.dromara.lqg.auth.group.domain.bo.UnitGroupBo;
import org.dromara.lqg.auth.group.domain.vo.UnitGroupVo;
import org.dromara.lqg.auth.group.guard.UnitGroupRules;
import org.dromara.lqg.auth.group.mapper.UnitGroupMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 组别维护（FLOW:F-AUTH-03.step1，UI:admin.auth.unit 右栏；ticket §2.2）。
 *
 * <p>★ 三条口径：
 * <ol>
 *   <li><b>同一单位内组别名唯一、不同单位可重名</b>（部分唯一索引 {@code uk_unit_group (unit_id, group_name)}
 *       {@code WHERE del_flag='0'}）；service 层归一化查重给人话报错；</li>
 *   <li><b>不物理删</b>：只启用 / 停用。已有人绑定的组别停用后，**已绑定的人不受影响** ——
 *       冻结的是「不再出现在选择器里」，不是「把已核验的档案打回 pending」；</li>
 *   <li><b>不允许把组别挪到别的单位下</b>：那会悄悄改变已绑定外部用户的可见范围，
 *       必须走核验（改归组）那条路。</li>
 * </ol>
 *
 * @author AUTH-GROUP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitGroupService {

    private final UnitGroupMapper unitGroupMapper;
    private final UnitQueryService unitQueryService;

    /**
     * 新增组别（缺省 {@code active}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(UnitGroupBo bo) {
        String name = StringUtils.trim(bo.getGroupName());
        Long unitId = bo.getUnitId();
        return DataPermissionHelper.ignore(() -> {
            requireUnit(unitId);
            requireUniqueName(unitId, name, null);
            UnitGroup group = new UnitGroup();
            group.setUnitId(unitId);
            group.setGroupName(name);
            group.setGroupStatus(statusOrDefault(bo.getGroupStatus(), UnitGroupRules.STATUS_ACTIVE));
            group.setRemark(bo.getRemark());
            unitGroupMapper.insert(group);
            log.info("新增组别：id={} unitId={} name={}", group.getId(), unitId, name);
            return group.getId();
        });
    }

    /**
     * 改名 / 改备注 / 改状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long groupId, UnitGroupBo bo) {
        String name = StringUtils.trim(bo.getGroupName());
        DataPermissionHelper.ignore(() -> {
            UnitGroup exists = requireGroup(groupId);
            if (!exists.getUnitId().equals(bo.getUnitId())) {
                throw new ServiceException("不能把组别挪到别的单位下；请在该单位下新建组别");
            }
            requireUnit(bo.getUnitId());
            requireUniqueName(bo.getUnitId(), name, groupId);
            String status = StringUtils.isNotBlank(bo.getGroupStatus())
                ? statusOrDefault(bo.getGroupStatus(), exists.getGroupStatus())
                : exists.getGroupStatus();
            // UpdateWrapper 而不是 updateById：备注清空要能把 NULL 写进去
            unitGroupMapper.update(null, new LambdaUpdateWrapper<UnitGroup>()
                .eq(UnitGroup::getId, exists.getId())
                .set(UnitGroup::getGroupName, name)
                .set(UnitGroup::getGroupStatus, status)
                .set(UnitGroup::getRemark, bo.getRemark())
                .set(UnitGroup::getUpdateTime, new Date()));
            log.info("改组别：id={} name={}", groupId, name);
            return null;
        });
    }

    /**
     * 启用 / 停用（{@code PUT /lqg/auth/group/{id}/status}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long groupId, String status) {
        String target = StringUtils.trim(status);
        if (!UnitGroupRules.isToggleTarget(target)) {
            throw new ServiceException("状态只能是 active（启用）或 disabled（停用）");
        }
        DataPermissionHelper.ignore(() -> {
            UnitGroup exists = requireGroup(groupId);
            UnitGroup patch = new UnitGroup();
            patch.setId(exists.getId());
            patch.setGroupStatus(target);
            unitGroupMapper.updateById(patch);
            log.info("组别 {} → {}", groupId, target);
            return null;
        });
    }

    /**
     * 单位下的组别列表（带读时 {@code verifiedCount}）。
     */
    public List<UnitGroupVo> list(Long unitId) {
        return unitQueryService.listGroups(unitId);
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private void requireUnit(Long unitId) {
        SourceUnit unit = unitQueryService.findUnit(unitId);
        if (unit == null) {
            throw new ServiceException("单位不存在");
        }
        if (UnitGroupRules.STATUS_DISABLED.equals(unit.getUnitStatus())) {
            throw new ServiceException("该单位已停用，不能在其下维护组别");
        }
    }

    private UnitGroup requireGroup(Long groupId) {
        UnitGroup group = unitQueryService.findGroup(groupId);
        if (group == null) {
            throw new ServiceException("组别不存在");
        }
        return group;
    }

    private void requireUniqueName(Long unitId, String name, Long excludeId) {
        if (StringUtils.isBlank(name)) {
            throw new ServiceException("组别名称不能为空");
        }
        UnitGroup hit = unitQueryService.findGroupByName(unitId, name);
        if (hit != null && !hit.getId().equals(excludeId)) {
            throw new ServiceException("该单位下已有组别「" + name + "」");
        }
    }

    private String statusOrDefault(String status, String fallback) {
        if (StringUtils.isBlank(status)) {
            return fallback;
        }
        String value = StringUtils.trim(status);
        if (!UnitGroupRules.isKnownStatus(value)) {
            throw new ServiceException("状态只能是 active（启用）/ disabled（停用）");
        }
        return value;
    }

}
