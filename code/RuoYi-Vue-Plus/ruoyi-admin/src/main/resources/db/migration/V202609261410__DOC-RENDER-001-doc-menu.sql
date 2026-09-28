-- V202609261410__DOC-RENDER-001-doc-menu.sql
--
-- DOC-RENDER-001 · 渲染 / 下载两个端点的按钮权限 + 内部人员上传质控图片所需的 OSS 权限。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D6 = 20260926；QC/DOC 域 13xx–14xx，本票第二支取 1410（第一支 1400 的下一分钟）。
--   库里已应用的最大版本是 202609261310 → 1410 > 1310，out-of-order=false 下安全。
--   菜单号段 5500-5599 取自 V202609261300 的注释：5501-5503 是 QC 的三个 F 行，
--   本票接着取 5504-5505；**C 页面菜单留给 DOC-MP-* / DOC-WEB-***（同一先例：
--   5501-5503 先落 F 行、5510 留给 QC-WEB-*）。
--
-- ★★ 为什么还要授 system:oss:upload / query / download（1600-1602）：
--    accept 1 用 `--as staff`（角色 102 lqg_internal）调 POST /resource/oss/upload 传图片，
--    而这三个 F 行此前**只授给了 role 3（超管）**——102 一个都没有，接口会 403，
--    `.data.ossId` 为空，accept 1 直接红。内部人员在质控页上传图片/附件本来就要走这个接口，
--    所以这里是**功能性授权**，不是为了让断言变绿而放水。
--    本票只加 F 行与授权，不改 1600-1603 的既有定义，也不建任何 C 页面菜单。
--
-- ★ 只落 F 行（menu_type='F'）：`lqg:doc:render` 触发渲染、`lqg:doc:query` 取签名链接。
--   下载端点归 query（能看就能下），不另造一个 download 权限串。

BEGIN;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5504, '文档渲染', 0, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:doc:render', '#', 103, 1, now(), '触发 Word 渲染（POST /lqg/doc/{sampleId}/{docKind}/render）'),
    (5505, '文档下载', 0, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:doc:query',  '#', 103, 1, now(), '取短时签名下载链接（GET /lqg/doc/{sampleId}/{docKind}/download）')
ON CONFLICT (menu_id) DO NOTHING;

-- 授权：101（lqg_admin）与 102（lqg_internal）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5504 AND 5505) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 授权：OSS 上传 / 查询 / 下载（1600-1602 已由基线建好，这里只加 role_menu 行）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id IN (1600, 1601, 1602)) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
