package org.dromara.lqg.auth.group.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.lqg.auth.group.domain.vo.ExtUserVo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 外部用户列表（UI:admin.auth.extuser）：姓名、手机号、单位、组别（自填的标「自填」）、核验状态、
 * 提交样本数、最近登录 —— 全部**读时算**（ticket §2.2）。
 *
 * <p>用一句显式 SQL 而不是 mapper：形状是「档案 LEFT JOIN 单位 / 组别 / 账号」，
 * 而 {@code t_lqg_source_unit} / {@code t_lqg_unit_group} 在本模块自己手里，
 * 不需要为它建 XML（同 {@code UnitQueryService} 的口径）。
 *
 * <p>★ {@code sampleCount} 是**可选**的：{@code t_lqg_sample} 属 SAMPLE 域的票，本票落地时库里没有它。
 * PostgreSQL 会先规划整条语句 —— 把子查询写死成 {@code LEFT JOIN t_lqg_sample} 在表不存在时直接
 * {@code ERROR: relation "t_lqg_sample" does not exist}（AUTH-LOGIN-001 报告坑 3 实测踩过）。
 * 所以按 {@code to_regclass(...) IS NOT NULL} 探针决定要不要带上这一段：表建好之后同一段代码
 * 自动开始出真数字，不用回头改。
 *
 * <p>★ 读侧包 {@link DataPermissionHelper#ignore}：这里列的是**业务数据**（外部人员及其单位），
 * 不是「当前登录人自己的行」；带 {@code @DataPermission} 的上游 mapper 在非管理员上下文会被
 * 数据范围静默滤空（AUTH-LOGIN-001 报告坑 1）。
 *
 * @author AUTH-GROUP-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtUserQueryService {

    private static final String SQL_SAMPLE_TABLE_EXISTS =
        "SELECT to_regclass('public.t_lqg_sample') IS NOT NULL";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 外部用户列表（带 {@code lqg_external} 角色且尚未升为内部的人）。
     *
     * @param bindStatus 状态筛选（unbound / pending / verified / rejected）；空 = 全部
     * @param unitId     单位筛选；空 = 全部（自填未归口的档案在按单位筛时也会被筛掉，这是预期：
     *                   它们还没有单位）
     */
    public List<ExtUserVo> list(String bindStatus, Long unitId) {
        return DataPermissionHelper.ignore(() -> {
            StringBuilder sql = new StringBuilder("""
                SELECT p.user_id                                  AS user_id,
                       COALESCE(NULLIF(p.real_name, ''), u.nick_name) AS name,
                       u.phonenumber                              AS phone,
                       p.unit_id                                  AS unit_id,
                       COALESCE(su.unit_name, p.unit_name_input)   AS unit_name,
                       p.group_id                                 AS group_id,
                       COALESCE(sg.group_name, p.group_name_input) AS group_name,
                       p.unit_name_input                           AS unit_name_input,
                       p.group_name_input                          AS group_name_input,
                       p.bind_status                              AS bind_status,
                       p.reject_reason                            AS reject_reason,
                       u.login_date                               AS last_login_time,
                       %s                                         AS sample_count
                  FROM t_lqg_ext_profile p
                  JOIN sys_user u   ON u.user_id = p.user_id AND u.del_flag = '0'
                  JOIN sys_user_role ur ON ur.user_id = u.user_id
                  JOIN sys_role r   ON r.role_id = ur.role_id AND r.del_flag = '0'
                  LEFT JOIN t_lqg_source_unit su ON su.id = p.unit_id AND su.del_flag = '0'
                  LEFT JOIN t_lqg_unit_group  sg ON sg.id = p.group_id AND sg.del_flag = '0'
                 WHERE p.del_flag = '0'
                   AND r.role_key = 'lqg_external'
                """.formatted(sampleCountExpression()));

            List<Object> args = new ArrayList<>();
            if (StringUtils.isNotBlank(bindStatus)) {
                sql.append(" AND p.bind_status = ?");
                args.add(bindStatus);
            }
            if (unitId != null) {
                sql.append(" AND p.unit_id = ?");
                args.add(unitId);
            }
            sql.append(" ORDER BY p.create_time, p.user_id");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            return rows.stream().map(ExtUserQueryService::toVo).toList();
        });
    }

    /**
     * 样本数表达式：样本表在 → 真子查询；不在 → 常量 0（不能写死表名，见类注释）。
     */
    private String sampleCountExpression() {
        try {
            Boolean exists = jdbcTemplate.queryForObject(SQL_SAMPLE_TABLE_EXISTS, Boolean.class);
            if (Boolean.TRUE.equals(exists)) {
                return "(SELECT count(*) FROM t_lqg_sample s WHERE s.del_flag = '0' AND s.submitter_id = p.user_id)";
            }
        } catch (Exception e) {
            log.warn("探 t_lqg_sample 是否存在失败（按「没有样本表」处理）：{}", e.getMessage());
        }
        return "0";
    }

    private static ExtUserVo toVo(Map<String, Object> row) {
        ExtUserVo vo = new ExtUserVo();
        vo.setUserId(number(row.get("user_id")));
        vo.setName((String) row.get("name"));
        vo.setPhone((String) row.get("phone"));
        vo.setUnitId(number(row.get("unit_id")));
        vo.setUnitName((String) row.get("unit_name"));
        vo.setGroupId(number(row.get("group_id")));
        vo.setGroupName((String) row.get("group_name"));
        vo.setUnitNameInput((String) row.get("unit_name_input"));
        vo.setGroupNameInput((String) row.get("group_name_input"));
        vo.setBindStatus((String) row.get("bind_status"));
        vo.setRejectReason((String) row.get("reject_reason"));
        vo.setSampleCount(number(row.get("sample_count")));
        Object login = row.get("last_login_time");
        if (login instanceof Timestamp ts) {
            vo.setLastLoginTime(ts);
        } else if (login instanceof java.util.Date d) {
            vo.setLastLoginTime(d);
        }
        // 「自填」= 没归到库里的单位 / 组别上（两个都没归口才算自填，前端据此决定核验弹窗出不出二选一）
        vo.setSelfInput(vo.getUnitId() == null && vo.getGroupId() == null);
        return vo;
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

}
