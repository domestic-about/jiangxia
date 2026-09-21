-- V202609221010__SAMPLE-WEB-001-sample-menu.sql
--
-- SAMPLE-WEB-001 · 工作台样本总表的菜单与按钮权限。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D2 = 20260922（本票 phase D2）；HHmm 按域分段，SAMPLE = 10xx。
--   1000（SAMPLE-MODEL-001）、1001（SAMPLE-VERIFY-001）已占用；1010 未被占用，
--   且目录名里 SAMPLE 域 1002-1009 是给后续 SAMPLE 票预留的段 —— 本票按派单取 1010。
--   版本号大于已应用的最大值（202609221001）→ out-of-order=false 下不会触发 FlywayValidateException
--   （SYS-WEB-001 踩过跨域补号的坑：版本号小于已应用迁移会让后端启动即失败）。
--
-- ★ 为什么把 5200 段整体搬成 5210 段（而不是在 5200 下补一行 C 菜单）：
--   accept 第 2 条同时要求
--     ① SELECT menu_id||':'||path||':'||component WHERE menu_id = 5210  == '5210:sample:lqg/sample/index'
--     ② getRouters 里 component == 'lqg/sample/index' 的路由**恰好 1 条**
--   而 SAMPLE-MODEL-001 的 V202609221000 已经把 5200 建成了「样本总表」（C、path='sample'、
--   component='lqg/sample/index'）。再插一条同样 component 的 5210 会让 ② 数成 2 条 → 红。
--   所以本迁移把菜单节点**整体搬到 5210 段**：先建 5210（C，顶级）+ 5211-5216（F，六个权限串），
--   授给 101（lqg_admin）与 102（lqg_internal），再删掉 5200-5206（旧节点与旧授权行）。
--   授权**不丢**：5201-5205 的权限串在 5211-5215 上逐字重建，5206 的 verify 在 5216 上重建。
--   按钮 5210 下的 perms 集合因此恰好是 accept 要的那六个，且 role_menu 里没有指向已删菜单的孤儿行。
--
-- ★ 只动菜单与授权，不建表、不动任何业务数据、不动任何 t_lqg_* 表。

BEGIN;

-- ── 1) 5210：样本总表（C：顶级菜单，落 5200 段之后的 5210 段）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5210, '样本总表', 0, 4, 'sample', 'lqg/sample/index', '',
        '1', '0', 'C', '0', '0', 'lqg:sample:list', 'table',
        103, 1, now(), 'SAMPLE-WEB-001：工作台样本总表（一张表看全部样本；内外部与核验状态标识、录入 / 编辑 / 核验抽屉）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2) 5211-5216：六个按钮权限（与 @SaCheckPermission 逐字一致）
--    list / query / add / edit / remove 是 SAMPLE-MODEL-001 建的（原 5201-5205），
--    verify 是 SAMPLE-VERIFY-001 建的（原 5206）—— 本迁移只换挂载点，权限串一字不改。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5211, '样本查询', 5210, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:list',   '#', 103, 1, now(), '样本总表列表 + 查询'),
    (5212, '样本详情', 5210, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:query',  '#', 103, 1, now(), '样本详情（GET /lqg/sample/{id}）'),
    (5213, '样本新增', 5210, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:add',    '#', 103, 1, now(), '内部新增（POST /lqg/sample）'),
    (5214, '样本修改', 5210, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:edit',   '#', 103, 1, now(), '修改（PUT /lqg/sample，有效样本随时可改）'),
    (5215, '样本删除', 5210, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:remove', '#', 103, 1, now(), '软删（DELETE /lqg/sample/{ids}）'),
    (5216, '样本核验', 5210, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:verify', '#', 103, 1, now(), '核验 / 改判（PUT /lqg/sample/{id}/verify）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3) 授权：101（lqg_admin）与 102（lqg_internal）都能调 /lqg/sample/**
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5210 AND 5216) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ── 4) 卸掉旧挂载点 5200-5206（内容已在 5210-5216 上重建，避免同一 component 出现两条路由）
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 5200 AND 5206;
DELETE FROM sys_menu      WHERE menu_id BETWEEN 5200 AND 5206;

COMMIT;
