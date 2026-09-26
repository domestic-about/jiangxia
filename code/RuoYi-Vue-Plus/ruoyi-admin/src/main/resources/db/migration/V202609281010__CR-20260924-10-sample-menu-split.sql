-- V202609281010__CR-20260924-10-sample-menu-split.sql
--
-- CR-20260924-10（甲方 2026-09-24 测试问题记录表第 25 行）：工作台「样本总表」拆成两个菜单页。
--   甲方原话：「组织样本和类器官样本同在一张表」，应该是分开的表，导出4个EXCEL表。
--   Kevin 拍板：拆成「样本记录信息表」（sample_kind = tissue）与「类器官收样记录」（sample_kind = organoid）
--   两个菜单页，左侧菜单顺序 = 样本记录信息表、类器官收样记录、石蜡包埋、冻存管理（与甲方四张 Excel 对上）。
--
-- 取号：G 批 A 组号段 V2026092810xx；1000 是同批的「代数」加列，本迁移 1010。
--       菜单号依据 doc/lint-profile.yaml：SAMPLE 域 5200-5299，5220 段未被占用。
--
-- ── 改了什么 ───────────────────────────────────────────────────────────────────
--   1) 5210：「样本总表」改名「样本记录信息表」。路由（path='sample'、component='lqg/sample/index'）
--      不变，现在只放组织样本 —— 已有的 /sample 链接、按钮权限挂载点、SAMPLE-WEB-001 那两条
--      「5210:sample:lqg/sample/index」「component=lqg/sample/index 的路由恰好 1 条」都不受影响。
--   2) 5220：新增「类器官收样记录」C 菜单（顶级），path='sample-organoid'、
--      component='lqg/sample/organoid'（organoid.vue 复用 index.vue 的列表、把类别钉成 organoid，列表只有一份）。
--   3) 按钮权限：沿用 5210 下的 5211-5217 那一组（lqg:sample:list / query / add / edit / remove / verify /
--      export），不在 5220 下再复制一份按钮行 —— 后端接口一个没拆，两页调的是同一组 /lqg/sample/**
--      端点、同一组权限串；复制一份会让「WHERE perms = 'lqg:sample:export'」查出两行
--      （SAMPLE-EXPORT-001 accept 断它恰好是 5217），角色管理里同一个权限串也会出现两个勾选框，
--      勾一个不勾另一个时到底有没有这个权限说不清。
--   4) 授权：凡是原先看得到样本总表（5210）的角色，一律补上 5220（按现有授权平移，不写死 101 / 102；
--      按钮权限本来就在 5211-5217 上，不用动）。
--   5) 顶级菜单排序：样本记录信息表 4 → 类器官收样记录 5 → 石蜡包埋 6 → 冻存管理 7 → 人员与单位 8。
--      以前石蜡包埋与人员与单位同为 5（并列时数据库返回顺序不固定，admin 与内部人员看到的顺序都不一样），
--      这里一并拉开，保证四张业务表连在一起、顺序固定。质控文档（5500）是隐藏目录，不动。
--
-- ★ 只动菜单与授权，不建表、不动任何业务数据、不动任何 t_lqg_* 表。可重复执行（ON CONFLICT / 按值 UPDATE）。

BEGIN;

-- ── 1) 5210 改名：样本总表 → 样本记录信息表（路由不变，只放组织样本）
UPDATE sys_menu
   SET menu_name = '样本记录信息表',
       remark    = 'CR-20260924-10：原「样本总表」拆成两页之一 —— 组织样本（sample_kind=tissue），对应甲方「样本记录信息表」',
       update_time = now()
 WHERE menu_id = 5210;

-- ── 2) 5220：类器官收样记录（C：顶级菜单）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5220, '类器官收样记录', 0, 5, 'sample-organoid', 'lqg/sample/organoid', '',
        '1', '0', 'C', '0', '0', 'lqg:sample:list', 'table',
        103, 1, now(), 'CR-20260924-10：原「样本总表」拆成两页之一 —— 类器官收样（sample_kind=organoid），对应甲方「类器官收样记录」')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3) 按钮权限沿用 5211-5217（不复制按钮行，理由见文件头）

-- ── 4) 授权平移：看得到 5210 的角色都补上 5220
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 5220
  FROM sys_role_menu rm
 WHERE rm.menu_id = 5210
   AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id = 5220)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ── 5) 顶级菜单排序固定下来：四张业务表连在一起（与甲方四张 Excel 同序），人员与单位排在后面
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5210;
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 5220;
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 5310;
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 5410;
UPDATE sys_menu SET order_num = 8 WHERE menu_id = 5100;

COMMIT;
