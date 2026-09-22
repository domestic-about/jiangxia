-- V202609261420__DOC-PDF-001-doc-console.sql
--
-- DOC-PDF-001 · 工作台「文档渲染状态」页（hidden C 路由）+ 它的授权。
--
-- 为什么要有这一页（FLOW:F-DOC-01.step6，本票的 blueprint_refs 之一）：
--   「在质控文档页点『重新生成』」—— 可那一页（QC-WEB-001 的 lqg/qc/editor/index）**还没建**，
--   本票不越界去建它（票面 §3 / 任务书都写明整页编辑页归 QC-WEB-001）。
--   于是把「failed 显示原因 + 重新生成」做成一个**独立的、可路由的**小页面
--   （views/lqg/doc/index.vue，走 /lqg/doc/** 三个接口），
--   等 QC-WEB-001 / DOC-PUBLISH-001 落地后把同一套 api/lqg/doc 接进预览面板即可。
--
-- 取号依据：D6 = 20260926；库里已应用的最大版本 = 202609261410（DOC-RENDER-001），
--   1410 < 1420，out-of-order=false 下安全。菜单号段 5500-5599（QC/DOC）：
--   5501-5503 = QC 三个 F、5504-5505 = DOC 的两个 F（V202609261410）、5510 留给 QC-WEB-*，
--   本票取 5520 作 DOC 的 C 页面菜单。
--
-- ★ visible='1' = **隐藏**（若依：0 显示 / 1 隐藏）：这是一个「有路由、不进侧边栏」的运维页
--   ——与 QC-WEB-001 的 5510 同一个先例。path 与 component 都必须非空，否则 vue-router 崩。

BEGIN;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5520, '文档渲染状态', 0, 6, 'doc-console', 'lqg/doc/index', '', '1', '0', 'C', '1', '0',
     'lqg:doc:query', 'documentation', 103, 1, now(),
     'DOC-PDF-001：查看 docx/pdf/页面图的渲染状态；failed 时显示原因并「重新生成」（FLOW:F-DOC-01.step6）')
ON CONFLICT (menu_id) DO NOTHING;

-- 授权：101（lqg_admin）/ 102（lqg_internal）—— 与 5504/5505 同一批角色
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id = 5520) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
