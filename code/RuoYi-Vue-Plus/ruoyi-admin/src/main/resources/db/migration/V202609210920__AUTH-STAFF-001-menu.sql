-- AUTH-STAFF-001 · 内部人员授权页的菜单与按钮权限（doc/lint-profile.yaml：AUTH 域菜单号段 5100-5199）
--
-- 取号依据 doc/lint-profile.yaml：
--   * 日期段 D1 = 20260921（本票 phase D1）；HHmm 按域分段，AUTH = 09xx；
--   * 0920 未被占用，且大于当前最大版本 0910 —— out-of-order=false 下不会触发 FlywayValidateException。
-- 菜单号依据：AUTH 域 5100-5199。
--   5100 「人员与单位」目录（M，AUTH-GROUP-001 的单位/组别页后续挂同一目录下）
--   5110 「内部人员授权」菜单（C，path='staff'，component='lqg/auth/staff/index'）
--   5111-5115 五个按钮（F），权限串 lqg:auth:staff:{list,grant,edit,resetPwd,revoke}
--
-- ★ 为什么连目录 5100 一起授给 101：上游 SysMenuServiceImpl.selectMenuTreeByUserId 用
--   getChildPerms(menus, 0) 建树，父节点不在授权集合里，子菜单**整条消失**（不报错、就是没有菜单）。
--   SYS-WEB-001 已经踩过一次（见 doc/waves/reports/SYS-WEB-001.md §4）。
-- ★ 按钮权限串只出现在这里；sys_role_menu 只授给 101（lqg_admin）。102（lqg_internal）不授本页
--   —— 普通内部人员不该给别人授权（accept 第 3 条断的就是「只授给管理员」）。

BEGIN;

-- ── 目录：人员与单位 ────────────────────────────────────────────────────────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5100, '人员与单位', 0, 5, 'auth', NULL, '',
        '1', '0', 'M', '0', '0', NULL, 'user',
        103, 1, now(), 'AUTH 域目录（AUTH-STAFF-001）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 菜单：内部人员授权 ──────────────────────────────────────────────────────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5110, '内部人员授权', 5100, 1, 'staff', 'lqg/auth/staff/index', '',
        '1', '0', 'C', '0', '0', 'lqg:auth:staff:list', 'peoples',
        103, 1, now(), 'AUTH-STAFF-001：按手机号授权 / 改角色 / 重置密码 / 撤销')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 按钮（F）：五个权限串 ───────────────────────────────────────────────────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5111, '人员授权查询', 5110, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:staff:list',     '#', 103, 1, now(), ''),
    (5112, '人员授权',     5110, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:staff:grant',    '#', 103, 1, now(), ''),
    (5113, '人员改角色',   5110, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:staff:edit',     '#', 103, 1, now(), ''),
    (5114, '重置工作台密码', 5110, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:staff:resetPwd', '#', 103, 1, now(), ''),
    (5115, '撤销内部授权', 5110, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:staff:revoke',   '#', 103, 1, now(), '')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 授权给 101（lqg_admin）：目录 + 菜单 + 五个按钮 ──────────────────────────
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 101, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (5100, 5110, 5111, 5112, 5113, 5114, 5115)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 兜底：本票不把「内部人员授权」给 102（lqg_internal）。102 的业务菜单由各业务域 WEB ticket
-- 在自己号段（5100+）里 seed；这里显式清一次本页相关的行，防手滑重复授权（幂等）。
DELETE FROM sys_role_menu WHERE role_id = 102 AND menu_id IN (5100, 5110, 5111, 5112, 5113, 5114, 5115);

COMMIT;
