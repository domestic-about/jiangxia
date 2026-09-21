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
import org.dromara.lqg.auth.group.guard.ExtBindStateMachine;
import org.dromara.lqg.auth.group.guard.UnitGroupRules;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.dromara.lqg.ext.domain.bo.ExtProfileUpdateBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 外部填 / 改自己的姓名、单位、组别（{@code PUT /mp/ext/profile}，FLOW:F-AUTH-03.step2 / step4）。
 *
 * <p>★ 只写**当前登录人自己那一行**（user_id 取自 token，请求体里没有 user_id 可传），
 * 所以它不是「可见样本范围」那条路，不经 {@code ExtScopeService}；但库访问留在本类
 * （auth 包），ext 包的 controller 只做转发 —— ADR-0004 的不变量 i4 规定 ext 包里除
 * {@code ExtScopeServiceImpl} 外任何类都不得持有 {@code *Mapper} 字段。
 *
 * <p>★ 三条口径（ticket §2.2，accept 第 2 条逐条断）：
 * <ol>
 *   <li>两套写法互斥：选了列表项（{@code unitId + groupId}）就清空自填项；列表里没有就用
 *       {@code unitNameInput + groupNameInput}，两个 id 清空。两套都不给 → 拒绝。</li>
 *   <li>任何一次**成功**保存 → {@code bind_status='pending'}，并清
 *       {@code verified_by / verified_time / reject_reason}。不是「改了才回 pending」——
 *       重填一遍也意味着信息要重新确认；「改完还是 verified」正是 ticket 点名的假绿形态
 *       （他换到别的组之后还能继续看原来那组的样本）。</li>
 *   <li>被拒时**库里一个字都不变**：所有校验在任何写操作之前完成。</li>
 * </ol>
 *
 * @author AUTH-GROUP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtProfileUpdateService {

    private final ExtProfileMapper extProfileMapper;
    private final UnitQueryService unitQueryService;

    /**
     * 保存当前登录人的档案。
     *
     * @param bo 姓名 + （单位 / 组别）两套写法之一
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(ExtProfileUpdateBo bo) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("未登录");
        }
        // ① 全部校验在任何写操作之前（被拒时库里不变）
        String realName = StringUtils.trim(bo.getRealName());
        if (StringUtils.isBlank(realName)) {
            throw new ServiceException("姓名不能为空");
        }
        boolean bySelection = bo.getUnitId() != null || bo.getGroupId() != null;
        boolean bySelfInput = StringUtils.isNotBlank(bo.getUnitNameInput())
            || StringUtils.isNotBlank(bo.getGroupNameInput());
        if (!bySelection && !bySelfInput) {
            throw new ServiceException("请在列表里选择单位与组别，或手动填写单位名与组别名");
        }

        Long unitId = null;
        Long groupId = null;
        String unitNameInput = null;
        String groupNameInput = null;

        if (bySelection) {
            // ② 列表项路径：单位、组别都必须存在且**启用**，组别必须属于该单位
            unitId = bo.getUnitId();
            groupId = bo.getGroupId();
            if (unitId == null || groupId == null) {
                throw new ServiceException("从列表选择时必须同时给 unitId 与 groupId");
            }
            SourceUnit unit = unitQueryService.findUnit(unitId);
            if (unit == null) {
                throw new ServiceException("单位不存在");
            }
            if (!UnitGroupRules.STATUS_ACTIVE.equals(unit.getUnitStatus())) {
                throw new ServiceException("该单位已停用，不能选择");
            }
            UnitGroup group = unitQueryService.findGroup(groupId);
            if (group == null) {
                throw new ServiceException("组别不存在");
            }
            if (!UnitGroupRules.STATUS_ACTIVE.equals(group.getGroupStatus())) {
                throw new ServiceException("该组别已停用，不能选择");
            }
            if (!group.getUnitId().equals(unitId)) {
                throw new ServiceException("组别「" + group.getGroupName() + "」不属于单位「" + unit.getUnitName() + "」");
            }
            // 选了列表项 → 自填项清空（下面写库时把两个 *NameInput 显式置 null）
        } else {
            // ③ 自填路径：两个名字都要有（只填一个等于没填全，核验人没法二选一归口）
            unitNameInput = StringUtils.trim(bo.getUnitNameInput());
            groupNameInput = StringUtils.trim(bo.getGroupNameInput());
            if (StringUtils.isBlank(unitNameInput) || StringUtils.isBlank(groupNameInput)) {
                throw new ServiceException("手动填写时单位名与组别名都要填");
            }
        }

        // 下面这些变量在 lambda 里被引用，所以拷进 record 保持 effectively final
        // （Java 的 lambda 捕获要求 final / effectively final，而上面按分支赋值过）
        ProfileValues values = new ProfileValues(realName, unitId, groupId, unitNameInput, groupNameInput);
        DataPermissionHelper.ignore(() -> {
            apply(userId, values);
            return null;
        });
    }

    /**
     * 真正落库那一段（单独一个方法，避开 lambda 捕获 effectively-final 的限制）。
     */
    private void apply(Long userId, ProfileValues values) {
        ExtProfile profile = extProfileMapper.selectOne(new LambdaQueryWrapper<ExtProfile>()
            .eq(ExtProfile::getUserId, userId));
        if (profile == null) {
            // 外部账号登录时就会建 unbound 档案；真没有（历史数据）就在这里补一行
            ExtProfile insert = new ExtProfile();
            insert.setUserId(userId);
            insert.setRealName(values.realName());
            insert.setUnitId(values.unitId());
            insert.setGroupId(values.groupId());
            insert.setUnitNameInput(values.unitNameInput());
            insert.setGroupNameInput(values.groupNameInput());
            insert.setBindStatus(ExtBindStateMachine.afterProfileSave());
            extProfileMapper.insert(insert);
            log.info("外部档案保存（补建）：userId={} unitId={} groupId={} 自填={} → bind_status=pending",
                userId, values.unitId(), values.groupId(), values.unitNameInput());
            return;
        }
        // ★ 必须用 UpdateWrapper 而不是 updateById：MyBatis-Plus 的 updateById 默认忽略 null 字段
        //   （FieldStrategy.NOT_NULL），于是 `verified_by` / `verified_time` / `reject_reason`
        //   **清不掉** —— 实测踩过：bind_status 已经回到 pending，verified_by 还是老的核验人，
        //   而 accept 第 2 条恰好断这一格（`COALESCE(verified_by::text,'-')` == '-'）。
        extProfileMapper.update(null, new LambdaUpdateWrapper<ExtProfile>()
            .eq(ExtProfile::getId, profile.getId())
            .set(ExtProfile::getRealName, values.realName())
            .set(ExtProfile::getUnitId, values.unitId())
            .set(ExtProfile::getGroupId, values.groupId())
            .set(ExtProfile::getUnitNameInput, values.unitNameInput())
            .set(ExtProfile::getGroupNameInput, values.groupNameInput())
            // ★ 任何一次成功保存 → pending + 清核验痕迹（FLOW:F-AUTH-03.step4）
            .set(ExtProfile::getBindStatus, ExtBindStateMachine.afterProfileSave())
            .set(ExtProfile::getVerifiedBy, null)
            .set(ExtProfile::getVerifiedTime, null)
            .set(ExtProfile::getRejectReason, null)
            .set(ExtProfile::getUpdateTime, new Date()));
        log.info("外部档案保存：userId={} unitId={} groupId={} 自填={} → bind_status=pending",
            userId, values.unitId(), values.groupId(), values.unitNameInput());
    }

    /**
     * 校验完之后要落库的那几个值（record 只是为了让 lambda 能捕获，没有别的语义）。
     */
    private record ProfileValues(String realName, Long unitId, Long groupId,
                                 String unitNameInput, String groupNameInput) {
    }

}
