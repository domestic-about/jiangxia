-- V202609221001__SAMPLE-VERIFY-001-verify-menu.sql
--
-- SAMPLE-VERIFY-001 唯一的 DDL/DML 迁移：补上核验权限串 lqg:sample:verify。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D2 = 20260922（本票 phase D2）；HHmm 按域分段，SAMPLE = 10xx。
--   1000 已被 SAMPLE-MODEL-001 占用；本票取 1001（SAMPLE 域内、且**避开**留给
--   SAMPLE-WEB-001 的 1010-1019）。
--
-- ★ 为什么本票必须动迁移（ticket 原文说「touches 里没有 migration」）：
--   ticket §2 要求 `PUT /lqg/sample/{id}/verify` 的权限是 `lqg:sample:verify`，
--   而 SAMPLE-MODEL-001 的迁移只建到 5205（perms = lqg:sample:{list,query,add,edit,remove}），
--   没有 verify。若依的 @SaCheckPermission 从「角色 → 菜单 perms」取权限集合，
--   缺这一行会让 `--as staff`（102 lqg_internal）恒 403，accept 第 1 条整条拿不到 200。
--   对照 SAMPLE-MODEL-001 完工报告 §2.1 的自述：「没有按 CR-20260917-05 的 UI:admin.sample.list
--   做『导出 / 核验』菜单项——那是 SAMPLE-EXPORT-001 / SAMPLE-VERIFY-001 的权限串」。
--   → 那两串本该由各自 ticket 补，本票补属**份内**，但确实越出 touches 一条：
--     已在完工报告 WARN 清单里列为 S2（ticket touches 与实现不符）。
--
-- ★ 本迁移**只加权限行**，不建表、不动任何业务数据：
--   5206 的父节点是 5200（样本总表，本票不改它），menu_type='F'（按钮级权限，不出现在侧边栏），
--   同时授给 101（lqg_admin）与 102（lqg_internal）—— 「状态只能由内部改」。

BEGIN;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5206, '样本核验', 5200, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:verify', '#',
        103, 1, now(), 'SAMPLE-VERIFY-001：核验 / 改判（PUT /lqg/sample/{id}/verify）')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id = 5206) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
