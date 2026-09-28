package org.dromara.lqg.auth.group.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.domain.bo.SourceUnitBo;
import org.dromara.lqg.auth.group.domain.vo.SourceUnitVo;
import org.dromara.lqg.auth.group.guard.UnitGroupRules;
import org.dromara.lqg.auth.group.mapper.SourceUnitMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 来源单位维护（FLOW:F-AUTH-03.step1，UI:admin.auth.unit 左栏；ticket §2.2）。
 *
 * <p>★ 三条口径：
 * <ol>
 *   <li><b>单位名全库唯一</b>：service 层归一化查重给人话报错，部分唯一索引
 *       {@code uk_unit_name WHERE del_flag='0'} 兜底（软删后可重建同名）；</li>
 *   <li><b>不物理删</b>：只 {@code active ↔ disabled} 切换。停用后不再出现在小程序选择器里，
 *       但已绑定的人不受影响（档案行的 {@code unit_id} 不动）；</li>
 *   <li>写侧包 {@link DataPermissionHelper#ignore}：与读侧同口径（见 {@code UnitQueryService}）。</li>
 * </ol>
 *
 * @author AUTH-GROUP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceUnitService {

    private final SourceUnitMapper sourceUnitMapper;
    private final UnitQueryService unitQueryService;

    /**
     * 新增单位（缺省 {@code active}；核验通过时点「新建」也会走到这里，那条路传的是 {@code active}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SourceUnitBo bo) {
        String name = StringUtils.trim(bo.getUnitName());
        return DataPermissionHelper.ignore(() -> {
            requireUniqueName(name, null);
            SourceUnit unit = new SourceUnit();
            unit.setUnitName(name);
            unit.setUnitStatus(statusOrDefault(bo.getUnitStatus(), UnitGroupRules.STATUS_ACTIVE, false));
            unit.setRemark(bo.getRemark());
            sourceUnitMapper.insert(unit);
            log.info("新增来源单位：id={} name={}", unit.getId(), name);
            return unit.getId();
        });
    }

    /**
     * 改名 / 改备注 / 改状态（status 走 {@link #toggleStatus}，这里只处理 active / pending 两种存量值）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long unitId, SourceUnitBo bo) {
        String name = StringUtils.trim(bo.getUnitName());
        DataPermissionHelper.ignore(() -> {
            SourceUnit exists = requireUnit(unitId);
            requireUniqueName(name, unitId);
            String status = StringUtils.isNotBlank(bo.getUnitStatus())
                // 存量的 pending（外部自填）允许在改名时顺手清成 active；不允许人工置回 pending
                ? statusOrDefault(bo.getUnitStatus(), exists.getUnitStatus(), false)
                : exists.getUnitStatus();
            // UpdateWrapper 而不是 updateById：remarks 清空要能把 NULL 写进去
            sourceUnitMapper.update(null, new LambdaUpdateWrapper<SourceUnit>()
                .eq(SourceUnit::getId, exists.getId())
                .set(SourceUnit::getUnitName, name)
                .set(SourceUnit::getUnitStatus, status)
                .set(SourceUnit::getRemark, bo.getRemark())
                .set(SourceUnit::getUpdateTime, new Date()));
            log.info("改来源单位：id={} name={}", unitId, name);
            return null;
        });
    }

    /**
     * 启用 / 停用（{@code PUT /lqg/auth/unit/{id}/status}）。
     *
     * <p>停用**不动**任何档案行：已绑定的外部用户继续看得到同组的样本（ticket §2.2 的边界），
     * 只是这个单位不再出现在小程序选择器里。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long unitId, String status) {
        String target = StringUtils.trim(status);
        if (!UnitGroupRules.isToggleTarget(target)) {
            throw new ServiceException("状态只能是 active（启用）或 disabled（停用）");
        }
        DataPermissionHelper.ignore(() -> {
            SourceUnit exists = requireUnit(unitId);
            SourceUnit patch = new SourceUnit();
            patch.setId(exists.getId());
            patch.setUnitStatus(target);
            sourceUnitMapper.updateById(patch);
            log.info("来源单位 {} → {}", unitId, target);
            return null;
        });
    }

    /**
     * 列表（工作台：含停用项）。
     */
    public List<SourceUnitVo> list() {
        return unitQueryService.listUnits();
    }

    // ── 私有 ─────────────────────────────────────────────────────────────────

    private SourceUnit requireUnit(Long unitId) {
        SourceUnit unit = sourceUnitMapper.selectById(unitId);
        if (unit == null) {
            throw new ServiceException("单位不存在");
        }
        return unit;
    }

    /**
     * 单位名全库唯一（归一化后比较；{@code excludeId} 是「改自己」时排除自己）。
     *
     * <p>在**写库之前**抛 —— accept 会紧跟库内断言，「先落盘再报错」是它要抓的假绿形态。
     */
    private void requireUniqueName(String name, Long excludeId) {
        if (StringUtils.isBlank(name)) {
            throw new ServiceException("单位名称不能为空");
        }
        SourceUnit hit = unitQueryService.findUnitByName(name);
        if (hit != null && !hit.getId().equals(excludeId)) {
            throw new ServiceException("单位「" + name + "」已存在，请改用「归并到已有」");
        }
    }

    private String statusOrDefault(String status, String fallback, boolean allowPending) {
        if (StringUtils.isBlank(status)) {
            return fallback;
        }
        String value = StringUtils.trim(status);
        if (!UnitGroupRules.isKnownStatus(value)) {
            throw new ServiceException("状态只能是 active（启用）/ disabled（停用）");
        }
        if (!allowPending && UnitGroupRules.STATUS_PENDING.equals(value)) {
            throw new ServiceException("状态只能是 active（启用）或 disabled（停用）");
        }
        return value;
    }

}
