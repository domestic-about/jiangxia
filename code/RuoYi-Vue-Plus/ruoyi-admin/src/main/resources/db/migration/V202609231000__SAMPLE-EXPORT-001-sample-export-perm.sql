-- V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql
--
-- SAMPLE-EXPORT-001 · 样本两张 Excel 导出（POST /lqg/sample/export/tissue|organoid）的按钮权限。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D3 = 20260923（本票 phase D3）；HHmm 按域分段，SAMPLE = 10xx。
--   D3 段里 SAMPLE 域的 1000 未被占用（D2 的 SAMPLE 三支落在 202609221000/1001/1010），
--   其它 SAMPLE 票（SAMPLE-HINT-001）不建迁移 —— 本票按 ticket §2 取 202609231000。
--   文件名与 ticket §2 的 `V202609231000__SAMPLE-EXPORT-001-perm.sql` 同一支，只把
--   description 写全成 sample-export-perm（Flyway 的 description 不参与任何断言）。
--
-- ★★ 版本号 202609231000 **小于**已应用的 202609231110（EMBED-WEB-001），而
--   spring.flyway.out-of-order=false → 在**已有库**上直接启动会 FlywayValidateException。
--   本票按项目先例（issue #12 / SYS-WEB-001）**重建 dev 库**（DROP DATABASE lqg_dev;
--   CREATE DATABASE lqg_dev OWNER lqg;）让全部迁移按版本号升序跑一遍，
--   **没有**改 out-of-order 配置（prod 语义要保持）：
--     202609221010 → 202609230902 → **202609231000** → 202609231100 → 202609231110
--   升序排列，空库端到端可跑通。
--
-- ★ 只做三件事（ticket §2 / accept 2）：
--     ① 5217：F 按钮，parent_id=5210，perms='lqg:sample:export'
--     ② 授给 101（lqg_admin）与 102（lqg_internal）
--     ③ 别的什么都不动 —— 尤其**不碰 5210 的 path='sample'**（accept 2 第 3 段断它）
--       与 5211-5216（SAMPLE-WEB-001 建的六个权限串），也不建第二条 lqg/sample/index 路由
--       （accept 2 第 4 段断 getRouters 里它恰 1 条）。
--
-- ★ 为什么 5217 由本票的迁移建、而不是复用 SAMPLE-WEB-001 的 5210-5216：
--   @SaCheckPermission("lqg:sample:export") 没有 sys_menu.perms 行时是 **403 不是 500**
--   —— 两个导出端点会静默全 403。5216（verify）之后的下一个空号就是 5217。
--
-- ★ 只动菜单与授权，不建表、不动任何业务数据、不动任何 t_lqg_* 表。

BEGIN;

-- ── 1) 5217：样本导出（F：按钮权限，挂在 5210「样本总表」下）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5217, '样本导出', 5210, 7, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:export', '#',
        103, 1, now(),
        'SAMPLE-EXPORT-001：按当前筛选导出「样本记录信息表」/「类器官收样记录」xlsx（POST /lqg/sample/export/tissue|organoid）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2) 授权：101（lqg_admin）与 102（lqg_internal）都能调两个导出端点
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id = 5217) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
