-- V202609301000__CR-20260930-11-qc-docs-menu-and-organoid-rename.sql
--
-- CR-20260930-11（甲方 2026-09-29/30 飞书测试问题表，Kevin 2026-09-30 拍板）：
--   ① 网页工作台第 17 行：三份质控表（样本质控表 / 类器官质控表 / 类器官质量评分表）要有
--      **单独的板块**「质控文档」—— 能看列表、能筛选、点进去进入质控文档编辑。
--   ② 小程序第 17 行：「类器官收样记录」改名「类器官送样记录」（内外部、小程序与工作台统一叫法）。
--
-- 取号：已应用的最大版本是 202609284001 → 本迁移 202609301000 更大，out-of-order=false 下安全。
--       菜单号依据 doc/lint-profile.yaml：QC/DOC 域 5500-5599，已占 5500-5505 / 5510-5513 / 5520，
--       本迁移取 5530（C 页面）。
--
-- ── 改了什么 ───────────────────────────────────────────────────────────────────
--   1) 5530「质控文档」：顶级 C 菜单，path='qc-docs'、component='lqg/qc/list/index'、perms='lqg:qc:query'。
--      列表接口 GET /lqg/qc/list 用的就是 lqg:qc:query（5501 已授给 101/102），不另造权限串。
--      ★ 5500 那个同名目录是**隐藏**的路由容器（挂质控文档编辑页），不动它、也不把它改成可见：
--        它的子页面都要带 sampleId 才有意义，改成可见会在侧边栏多出一个进去是空白引导的入口。
--   2) 5220 改名「类器官送样记录」（路由 / 组件 / 权限都不变，已有链接照旧能用）。
--   3) 授权：凡是能看质控文档编辑页（5510）的角色，一律补上 5530（按现有授权平移，不写死角色号）。
--   4) 顶级菜单排序：样本记录信息表 4 → 类器官送样记录 5 → 石蜡包埋 6 → 冻存管理 7 →
--      质控文档 8 → 人员与单位 9（四张业务表仍连在一起，质控文档紧随其后）。
--
-- ★ 只动菜单与授权，不建表、不动任何业务数据。可重复执行（ON CONFLICT / 按值 UPDATE）。

BEGIN;

-- ── 1) 5530：质控文档（C：顶级菜单，可见）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5530, '质控文档', 0, 8, 'qc-docs', 'lqg/qc/list/index', '',
        '1', '0', 'C', '0', '0', 'lqg:qc:query', 'documentation',
        103, 1, now(), 'CR-20260930-11：质控文档板块 —— 有效样本 + 三份质控表状态的列表，可筛选，点「进入」到质控文档编辑页')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2) 5220 改名：类器官收样记录 → 类器官送样记录
UPDATE sys_menu
   SET menu_name = '类器官送样记录',
       remark    = 'CR-20260930-11：由「类器官收样记录」改名（内外部统一叫法）；类器官样本（sample_kind=organoid）',
       update_time = now()
 WHERE menu_id = 5220;

-- ── 3) 授权平移：看得到 5510（质控文档编辑）的角色都补上 5530
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 5530
  FROM sys_role_menu rm
 WHERE rm.menu_id = 5510
   AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id = 5530)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ── 4) 顶级菜单排序
UPDATE sys_menu SET order_num = 8 WHERE menu_id = 5530;
UPDATE sys_menu SET order_num = 9 WHERE menu_id = 5100;

COMMIT;
