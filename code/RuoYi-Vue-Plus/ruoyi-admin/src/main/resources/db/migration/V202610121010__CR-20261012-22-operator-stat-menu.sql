-- V202610121010__CR-20261012-22-operator-stat-menu.sql
--
-- CR-20261012-22：工作台加菜单「操作人统计」—— 四张表各算各的，按人、按时间段看做了多少。
--
-- 菜单号依据 doc/lint-profile.yaml：SYS 域 5000-5099，此前一个都没占，本迁移取 5010（C：顶级菜单）。
-- 排在「冻存管理」(7) 之后、「人员与单位」(9) 之前。
--
-- ★ 接口 GET /lqg/stat/operator 走的是「内部角色闸」（与工作台首页同一个做法），不认权限串；
--   这里的 perms 只是让菜单行有个名字，给两个业务角色授的是「看得到这个菜单」。
-- ★ 授权不写死角色号：凡是看得到「样本记录信息表」(5210) 的角色都补上 —— 统计的就是那几张表，
--   看得到表的人就看得到统计（Kevin 2026-10-12：全员可见）。
--
-- 只动菜单与授权，不动业务数据。可重复执行。

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5010, '操作人统计', 0, 8, 'operator-stat', 'lqg/stat/operator/index', '',
        '1', '0', 'C', '0', '0', 'lqg:stat:operator', 'chart',
        103, 1, now(), 'CR-20261012-22：按人、按时间段统计四张表各做了多少（质控表不统计）')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 5010
  FROM sys_role_menu rm
 WHERE rm.menu_id = 5210
   AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id = 5010)
ON CONFLICT (role_id, menu_id) DO NOTHING;
