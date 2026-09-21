-- ============================================================================
-- SYS-WEB-001 · 网页工作台壳：给 lqg_admin 开「字典管理 / 参数设置」，插两行系统参数
--
-- 取号依据 doc/lint-profile.yaml：D1 = 20260921，SYS 域 HHmm 08xx；0830 未被占用
--（已有 0800 / 0810 / 0820 / 0910）。
--
-- 菜单 id 不猜号：全部按上游 sys_menu 里 component/path 的**真实值**取。
--   基线（V202609210800）实测：menu_id 1 = 系统管理（M, path=system, parent 0）
--                               menu_id 105 = 字典管理（C, component=system/dict/index, path=dict）
--                               menu_id 106 = 参数设置（C, component=system/config/index, path=config）
--                               105 的子按钮 F 1026-1030；106 的子按钮 F 1031-1035
--
-- 只授「字典管理 / 参数设置」两条 C 菜单 + 它们自己的 F 按钮 + 它们共同的父目录
-- **不授**系统管理的其它子菜单（用户 / 角色 / 菜单 / 部门 / 岗位 / 通知公告 / 日志 / 文件 …）。
-- 父目录必须一起授：上游 selectMenuTreeByUserId 用 getChildPerms(menus, 0) 建树，
-- 父节点不在授权集合里时子菜单挂不上去、getRouters 里就看不到（accept 第 1 条会红）。
--
-- 角色 102（lqg_internal）不授任何系统管理菜单（menu_id < 5000 的行一行都没有）；
-- 内部人员的业务菜单由各业务域的 WEB ticket 在自己号段（5100+）里 seed。
--
-- 两行 sys_config 来自 CR-20260918-07（UI:admin.config）：
--   lqg.ext.show-internal-no  合作单位可见内部编号，默认 false
--   lqg.cryo.overdue-days     -80 冻存超期提醒天数，默认 14
-- config_type = 'N'（非系统内置，内部人员可改；改动走若依自己的操作日志）。
-- ============================================================================

BEGIN;

-- ── 1. 角色 101（lqg_admin）：字典管理 / 参数设置 两条菜单 + 各自按钮 + 共同父目录 ──
WITH target_menu AS (
    SELECT m.menu_id
    FROM sys_menu m
    WHERE m.component IN ('system/dict/index', 'system/config/index')
),
target_parent AS (
    SELECT DISTINCT m.parent_id AS menu_id
    FROM sys_menu m
    WHERE m.component IN ('system/dict/index', 'system/config/index')
),
target_button AS (
    SELECT b.menu_id
    FROM sys_menu b
    WHERE b.parent_id IN (SELECT menu_id FROM target_menu)
      AND b.menu_type = 'F'
)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 101, menu_id
FROM (
    SELECT menu_id FROM target_menu
    UNION
    SELECT menu_id FROM target_parent
    UNION
    SELECT menu_id FROM target_button
) t
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ── 2. 幂等兜底：本票不往 102 授系统管理菜单，这里显式清一次（防手工/前序残留） ──
DELETE FROM sys_role_menu
WHERE role_id = 102
  AND menu_id IN (
      SELECT m.menu_id FROM sys_menu m WHERE m.menu_id < 5000
  );

-- ── 3. 两行系统参数（本项目参数段 5001/5002，避开上游 1-11 与雪花 id） ──
INSERT INTO sys_config (config_id, tenant_id, config_name, config_key, config_value, config_type,
                        create_dept, create_by, create_time, remark)
SELECT 5001, '000000', '合作单位可见内部编号', 'lqg.ext.show-internal-no', 'false', 'N',
       103, 1, now(),
       'CR-20260918-07：false = 外部样本详情与外部 VO 里不带内部编号；true = 多显示一行内部编号（只作用于接口返回的页面数据）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'lqg.ext.show-internal-no');

INSERT INTO sys_config (config_id, tenant_id, config_name, config_key, config_value, config_type,
                        create_dept, create_by, create_time, remark)
SELECT 5002, '000000', '-80 冻存超期提醒天数', 'lqg.cryo.overdue-days', '14', 'N',
       103, 1, now(),
       'CR-20260918-07：超期判定每次读时取这个值，改完下一次读即生效（FLOW:F-CRYO-01.step2）'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'lqg.cryo.overdue-days');

COMMIT;
