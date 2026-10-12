package org.dromara.lqg.auth.staff.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.auth.staff.domain.vo.StaffMemberVo;
import org.dromara.lqg.auth.staff.domain.vo.StaffOptionVo;
import org.dromara.system.domain.SysUser;

import java.util.Collection;
import java.util.List;

/**
 * 内部人员授权页的读侧 SQL。
 *
 * <p>写侧一律走 {@code ruoyi-system} 自己的 mapper（{@code SysUserMapper} / {@code SysUserRoleMapper}）：
 * 本类的 SQL 全部是 SELECT，只补上游没有的「带角色键的人员列表」这一个形状。
 *
 * <p>★ 这些 SQL 都在 {@code DataPermissionHelper.ignore} 里跑（见 {@code StaffGrantService}）：
 * {@code sys_user} 带 {@code @DataPermission}，而调用它们的场景里有「还没登录 / 非管理员上下文」
 * 的可能，不 ignore 会被数据范围静默滤成空集（AUTH-LOGIN-001 报告坑 1）。
 *
 * @author AUTH-STAFF-001
 */
public interface StaffAccountMapper extends BaseMapperPlus<SysUser, StaffMemberVo> {

    /**
     * 带内部角色（101 / 102）的账号列表，行上直接带角色键与角色名。
     *
     * <p>一个账号理论上只有一条内部角色行；真出现两条时这里会出两行 —— 由 service 侧按 userId
     * 去重（保留 role_id 小的那条，101 优先，管理员权限不会被内部角色行掩盖）。
     */
    @Select("""
        SELECT u.user_id      AS userId,
               u.nick_name    AS name,
               u.phonenumber  AS phone,
               r.role_key     AS roleKey,
               r.role_name    AS roleName
          FROM sys_user u
          JOIN sys_user_role ur ON ur.user_id = u.user_id
          JOIN sys_role r       ON r.role_id  = ur.role_id
         WHERE u.del_flag = '0'
           AND r.del_flag = '0'
           AND r.role_key IN ('lqg_admin', 'lqg_internal')
         ORDER BY r.role_sort, u.user_id
        """)
    List<StaffMemberVo> selectStaffList();

    /**
     * 「选人」用的内部人员名单（CR-20261012-22）：带内部角色、账号未停用的人，只取 id 与姓名。
     *
     * <p>一个账号挂了两条内部角色行时会出两行 —— 由 service 侧按 userId 去重。
     */
    @Select("""
        SELECT u.user_id   AS userId,
               u.nick_name AS name
          FROM sys_user u
          JOIN sys_user_role ur ON ur.user_id = u.user_id
          JOIN sys_role r       ON r.role_id  = ur.role_id
         WHERE u.del_flag = '0'
           AND u.status = '0'
           AND r.del_flag = '0'
           AND r.role_key IN ('lqg_admin', 'lqg_internal')
         ORDER BY u.nick_name, u.user_id
        """)
    List<StaffOptionVo> selectDirectory();

    /**
     * 按账号 id 取姓名（CR-20261012-22）：统计页显示用。<b>不看角色</b> ——
     * 已经撤销授权的人，他名下的历史记录照样要显示得出名字。
     */
    @Select("""
        <script>
        SELECT u.user_id   AS userId,
               u.nick_name AS name
          FROM sys_user u
         WHERE u.user_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<StaffOptionVo> selectNamesByIds(@Param("ids") Collection<Long> ids);

}
