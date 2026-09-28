package org.dromara.lqg.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.domain.vo.ExtProfileVo;
import org.dromara.lqg.auth.group.domain.SourceUnit;
import org.dromara.lqg.auth.group.guard.ExtBindStateMachine;
import org.dromara.lqg.auth.group.service.UnitQueryService;
import org.dromara.lqg.auth.mapper.ExtProfileMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 外部档案的读侧：档案行 + 单位 / 组别名称的**读时 join**。
 *
 * <p>为什么不把 join 直接写进 {@link ExtProfileMapper} 的 SQL：{@code t_lqg_source_unit} /
 * {@code t_lqg_unit_group} 是 **AUTH-GROUP-001 才建的表**，本票落地时库里没有它们，
 * 而 PostgreSQL 会**先规划整条语句**——写在 {@code CASE WHEN to_regclass(...) IS NULL} 的
 * 不可达分支里的子查询照样会被解析，`relation "t_lqg_source_unit" does not exist` 直接 500
 * （实测踩过）。所以这里按「表在不在」分两步：在就查名字，不在就只回 id，
 * AUTH-GROUP-001 建表之后**同一段代码自动开始带出名称**，不用回头改。
 *
 * <p>查名称失败（表不存在之外的原因）只记日志、不抛：{@code /mp/me} 是首页每次进入都要调的接口，
 * 不该因为「单位表临时读不到」整页打不开 —— 名称缺失是空值，不是错误。
 *
 * @author AUTH-LOGIN-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtProfileQueryService {

    private static final String SQL_SOURCE_UNIT_EXISTS = "SELECT to_regclass('public.t_lqg_source_unit') IS NOT NULL";
    private static final String SQL_UNIT_GROUP_EXISTS = "SELECT to_regclass('public.t_lqg_unit_group') IS NOT NULL";

    private final ExtProfileMapper extProfileMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UnitQueryService unitQueryService;

    /**
     * 按 user_id 读外部档案（含单位 / 组别名称）。
     *
     * @param userId 用户 id
     * @return 档案视图；内部人员 / 没有档案行时返回 null
     */
    public ExtProfileVo profileOf(Long userId) {
        ExtProfile profile = extProfileMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExtProfile>()
                .eq(ExtProfile::getUserId, userId));
        if (profile == null) {
            return null;
        }
        ExtProfileVo vo = new ExtProfileVo();
        vo.setUnitId(profile.getUnitId());
        vo.setGroupId(profile.getGroupId());
        vo.setUnitNameInput(profile.getUnitNameInput());
        vo.setGroupNameInput(profile.getGroupNameInput());
        vo.setBindStatus(profile.getBindStatus());
        vo.setRejectReason(profile.getRejectReason());
        vo.setUnitName(lookupName("t_lqg_source_unit", "unit_name", SQL_SOURCE_UNIT_EXISTS, profile.getUnitId()));
        vo.setGroupName(lookupName("t_lqg_unit_group", "group_name", SQL_UNIT_GROUP_EXISTS, profile.getGroupId()));
        return vo;
    }

    /**
     * 外部用户提交样本时「可用的来源单位」（FIX V01 / issue #112）。
     *
     * <p>★ 口径 = 现有的外部单位绑定规则（FLOW:F-AUTH-03、{@link ExtBindStateMachine}）：
     * <ul>
     *   <li>档案里<b>选了列表里的单位</b>（{@code unit_id} 非空）且档案是 {@code pending}（已选、等实验室核验）
     *       或 {@code verified}（已核验）→ 这个单位就是他的；</li>
     *   <li>{@code unbound}（没填）、{@code rejected}（被实验室驳回）、只自填了单位名（{@code unit_id} 为空）
     *       → 没有可用单位（样本只存名称快照，由实验室核验时归口）；</li>
     *   <li>单位行已删除 → 没有；单位只是停用 → 照样算（「停用的单位不再出现在选择器里，但已绑定的人不受影响」）。</li>
     * </ul>
     * 外部<b>不能</b>凭一个 id 把样本挂到这个单位以外的地方（{@code SubmitSegmentRules.attributeExternalUnit}）。
     *
     * @param userId 外部用户
     * @return 可用单位；没有 → null
     */
    public SourceUnit boundUnitOf(Long userId) {
        if (userId == null) {
            return null;
        }
        ExtProfile profile = DataPermissionHelper.ignore(() -> extProfileMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExtProfile>()
                .eq(ExtProfile::getUserId, userId)
                .last("limit 1")));
        if (profile == null || profile.getUnitId() == null) {
            return null;
        }
        String status = profile.getBindStatus();
        if (!ExtBindStateMachine.PENDING.equals(status) && !ExtBindStateMachine.VERIFIED.equals(status)) {
            return null;
        }
        return unitQueryService.findUnit(profile.getUnitId());
    }

    /**
     * 读一个名称列：表不存在 → null，行不存在 → null，其它异常 → 记日志后 null。
     *
     * @param table      目标表（调用方传的是字面量常量，不是用户输入）
     * @param nameColumn 名称列（同上）
     * @param existsSql  该表是否存在的探针 SQL
     * @param id         外键值；为 null 直接返回 null
     */
    private String lookupName(String table, String nameColumn, String existsSql, Long id) {
        if (id == null) {
            return null;
        }
        try {
            Boolean exists = jdbcTemplate.queryForObject(existsSql, Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                return null;
            }
            List<String> names = jdbcTemplate.queryForList(
                "SELECT " + nameColumn + " FROM " + table + " WHERE id = ?", String.class, id);
            return names.isEmpty() ? null : names.get(0);
        } catch (DataAccessException e) {
            log.warn("读 {}.{} 失败（按「名称缺失」处理）：{}", table, nameColumn, e.getMessage());
            return null;
        }
    }

}
