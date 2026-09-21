package org.dromara.lqg.auth.group.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.domain.UnitGroup;
import org.dromara.lqg.auth.group.domain.bo.ExtVerifyBo;
import org.dromara.lqg.auth.group.domain.bo.SourceUnitBo;
import org.dromara.lqg.auth.group.domain.bo.UnitGroupBo;
import org.dromara.lqg.auth.group.domain.vo.ExtUserVo;
import org.dromara.lqg.auth.group.guard.ExtBindStateMachine;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 外部用户组别核验（FLOW:F-AUTH-03.step3，UI:admin.auth.extuser）。
 *
 * <p>★ 本类是本票最容易做反的地方，三条铁律：
 * <ol>
 *   <li><b>先校验、后写库</b>：非法转移与「自填没选新建 / 归并」都在**任何写操作之前**抛。
 *       accept 每段「被拒」后面都紧跟一条库内状态断言 —— 只看业务码的话，「先落盘再返回 400」
 *       也是绿的，这正是它要抓的假绿形态。</li>
 *   <li><b>自填的单位 / 组别必须二选一</b>：{@code createUnit:true + createGroup:true}（新建）
 *       或 {@code unitId + groupId}（归并）。不许留着一个 {@code unit_id} 为空却 {@code verified}
 *       的档案 —— 同组互看按 {@code group_id} 算，group_id 为空的人核验通过后谁也匹配不上，
 *       还再也不会回到核验队列。</li>
 *   <li><b>合法转移只有五条</b>（{@link ExtBindStateMachine}）：{@code pending→verified}、
 *       {@code pending→rejected}、{@code verified→pending}（外部自己改）、{@code rejected→pending}
 *       （外部改后重提）、{@code verified→verified}（内部改归组）。其余拒绝。</li>
 * </ol>
 *
 * @author AUTH-GROUP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtUserVerifyService {

    private final ExtProfileMapper extProfileMapper;
    private final UnitQueryService unitQueryService;
    private final SourceUnitService sourceUnitService;
    private final UnitGroupService unitGroupService;
    private final ExtUserQueryService extUserQueryService;

    /**
     * 外部用户列表（UI:admin.auth.extuser）。
     */
    public List<ExtUserVo> list(String bindStatus, Long unitId) {
        return extUserQueryService.list(bindStatus, unitId);
    }

    /**
     * 核验（{@code PUT /lqg/auth/ext-user/{userId}/verify}）。
     *
     * @param userId 目标外部账号
     * @param bo     approve / reject + 归口决定
     */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long userId, ExtVerifyBo bo) {
        // 当前核验人在进 ignore 之前取（DataPermissionHelper.ignore 内部不保证 token 上下文可用）
        Long operatorId = LoginHelper.getUserId();
        DataPermissionHelper.ignore(() -> {
            ExtProfile profile = extProfileMapper.selectOne(new LambdaQueryWrapper<ExtProfile>()
                .eq(ExtProfile::getUserId, userId));
            if (profile == null) {
                throw new ServiceException("该账号没有外部档案，不能核验");
            }
            String action = StringUtils.trim(bo.getAction());
            if (!ExtBindStateMachine.isKnownAction(action)) {
                throw new ServiceException("action 只能是 approve（通过）或 reject（驳回）");
            }
            String target = ExtBindStateMachine.targetOf(action);
            if (!ExtBindStateMachine.isLegal(profile.getBindStatus(), target)) {
                throw new ServiceException("当前状态是「" + profile.getBindStatus() + "」，不能执行 "
                    + action + "（合法转移只有 pending→verified、pending→rejected、"
                    + "verified→pending、rejected→pending、verified→verified）");
            }
            if (ExtBindStateMachine.ACTION_APPROVE.equals(action)) {
                approve(profile, bo, operatorId);
            } else {
                reject(profile, bo);
            }
            return null;
        });
    }

    // ── approve ──────────────────────────────────────────────────────────────

    /**
     * 通过：先把「归口」（unit_id / group_id）定下来，再一次性写 verified ——
     * 单条 UPDATE 落盘，「新建」那条路上新建的单位 / 组别在同事务里一起提交。
     */
    private void approve(ExtProfile profile, ExtVerifyBo bo, Long operatorId) {
        boolean hasUnit = profile.getUnitId() != null;
        boolean hasGroup = profile.getGroupId() != null;
        boolean createUnit = Boolean.TRUE.equals(bo.getCreateUnit());
        boolean createGroup = Boolean.TRUE.equals(bo.getCreateGroup());
        boolean merge = bo.getUnitId() != null && bo.getGroupId() != null;

        // ① 自填 / 半自填：必须先二选一 —— 这一句是「不许留 unit_id 为空却 verified」的闸门
        String refuse = ExtBindStateMachine.needsSelfInputDecision(profile.getBindStatus(), hasUnit, hasGroup,
            createUnit, createGroup, bo.getUnitId() != null, bo.getGroupId() != null);
        if (refuse != null) {
            throw new ServiceException(refuse);
        }

        Long unitId;
        Long groupId;
        boolean clearSelfInput;
        if (createUnit || createGroup) {
            // ② 「新建」：把外部自填的名字建成 active 单位 / 组别
            unitId = resolveUnitForCreate(profile);
            groupId = resolveGroupForCreate(unitId, profile);
            clearSelfInput = true;
        } else if (merge) {
            // ③ 「归并到已有」：unitId + groupId 都用列表里的。
            //    ★ 也要清自填名：自填档案归并之后，那两个名字已经不是他的单位 / 组别了，
            //    留着会让「外部用户」页继续按 unit_name_input 显示一个过期的名字
            //    （实测踩过：归并成功了，列表里还挂着「C 研究所 / 肿瘤组」）。
            unitId = bo.getUnitId();
            groupId = bo.getGroupId();
            clearSelfInput = StringUtils.isNotBlank(profile.getUnitNameInput())
                || StringUtils.isNotBlank(profile.getGroupNameInput());
        } else {
            // ④ 已经选过列表项（档案本来就有 unit_id / group_id）：内部只是点「通过」/「改归组」
            unitId = hasUnit ? profile.getUnitId() : bo.getUnitId();
            groupId = hasGroup ? profile.getGroupId() : bo.getGroupId();
            clearSelfInput = false;
        }

        requireUnitAndGroupMatch(unitId, groupId);

        // ★ 用 UpdateWrapper 而不是 updateById：MyBatis-Plus 的 updateById 默认忽略 null 字段
        //   （FieldStrategy.NOT_NULL），`reject_reason` 与「自填名清空」都清不掉 ——
        //   实测踩过（bind_status 回到 pending 了、verified_by 还留着老核验人，accept 第 2 条断的正是这格）。
        LambdaUpdateWrapper<ExtProfile> update = new LambdaUpdateWrapper<ExtProfile>()
            .eq(ExtProfile::getId, profile.getId())
            .set(ExtProfile::getUnitId, unitId)
            .set(ExtProfile::getGroupId, groupId)
            .set(ExtProfile::getBindStatus, ExtBindStateMachine.VERIFIED)
            .set(ExtProfile::getVerifiedBy, operatorId)
            .set(ExtProfile::getVerifiedTime, new Date())
            .set(ExtProfile::getRejectReason, null)
            .set(ExtProfile::getUpdateTime, new Date());
        if (clearSelfInput) {
            // 名字已经变成真单位 / 真组别了，自填栏清空（留着会让前端继续显示「自填」标签）
            update.set(ExtProfile::getUnitNameInput, null).set(ExtProfile::getGroupNameInput, null);
        }
        extProfileMapper.update(null, update);
        log.info("核验通过：userId={} unitId={} groupId={} 清自填={} 操作人={}",
            profile.getUserId(), unitId, groupId, clearSelfInput, operatorId);
    }

    /**
     * 驳回：原因必填（accept 第 4 段：不带 reason 的 reject 必须被拒）。
     */
    private void reject(ExtProfile profile, ExtVerifyBo bo) {
        String reason = StringUtils.trim(bo.getReason());
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("驳回必须填写原因");
        }
        // 驳回即清核验痕迹：他从来没被核验过（同样要用 UpdateWrapper 才能把 null 写进去）
        extProfileMapper.update(null, new LambdaUpdateWrapper<ExtProfile>()
            .eq(ExtProfile::getId, profile.getId())
            .set(ExtProfile::getBindStatus, ExtBindStateMachine.REJECTED)
            .set(ExtProfile::getRejectReason, reason)
            .set(ExtProfile::getVerifiedBy, null)
            .set(ExtProfile::getVerifiedTime, null)
            .set(ExtProfile::getUpdateTime, new Date()));
        log.info("核验驳回：userId={} 原因={}", profile.getUserId(), reason);
    }

    // ── 归口解析 ─────────────────────────────────────────────────────────────

    /**
     * 「新建」路径：把档案里的自填单位名建成（或复用）一个 active 单位，返回 unit_id。
     */
    private Long resolveUnitForCreate(ExtProfile profile) {
        String unitName = StringUtils.trim(profile.getUnitNameInput());
        if (StringUtils.isBlank(unitName)) {
            throw new ServiceException("档案里没有自填的单位名，不能走「新建」；请改用「归并到已有」");
        }
        SourceUnit existing = unitQueryService.findUnitByName(unitName);
        if (existing != null) {
            // 同名单位已经在库里（可能是上一次核验建的）→ 直接复用，不新建第二个
            return existing.getId();
        }
        SourceUnitBo unitBo = new SourceUnitBo();
        unitBo.setUnitName(unitName);
        unitBo.setUnitStatus("active");
        return sourceUnitService.create(unitBo);
    }

    /**
     * 「新建」路径：把档案里的自填组别名建成该单位下的 active 组别，返回 group_id。
     */
    private Long resolveGroupForCreate(Long unitId, ExtProfile profile) {
        String groupName = StringUtils.trim(profile.getGroupNameInput());
        if (StringUtils.isBlank(groupName)) {
            throw new ServiceException("档案里没有自填的组别名，不能走「新建」；请改用「归并到已有」");
        }
        UnitGroup existing = unitQueryService.findGroupByName(unitId, groupName);
        if (existing != null) {
            return existing.getId();
        }
        UnitGroupBo groupBo = new UnitGroupBo();
        groupBo.setUnitId(unitId);
        groupBo.setGroupName(groupName);
        groupBo.setGroupStatus("active");
        return unitGroupService.create(groupBo);
    }

    /**
     * 最终落库前的一致性闸门：unit_id 与 group_id 都必须非空，且组别必须属于该单位。
     *
     * <p>accept 最后一段：组别 9000009103 属 B 大学，却和单位 A 医院一起提交 → 必须拒绝。
     */
    private void requireUnitAndGroupMatch(Long unitId, Long groupId) {
        if (unitId == null || groupId == null) {
            throw new ServiceException("核验通过前必须确定单位与组别（unit_id / group_id 都不能为空）");
        }
        SourceUnit unit = unitQueryService.findUnit(unitId);
        if (unit == null) {
            throw new ServiceException("单位不存在");
        }
        UnitGroup group = unitQueryService.findGroup(groupId);
        if (group == null) {
            throw new ServiceException("组别不存在");
        }
        if (!group.getUnitId().equals(unitId)) {
            throw new ServiceException("组别「" + group.getGroupName() + "」不属于单位「" + unit.getUnitName() + "」");
        }
    }

}
