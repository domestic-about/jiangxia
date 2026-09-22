-- V202609261430__QC-WEB-001-qc-menu.sql
--
-- QC-WEB-001 · 工作台「质控文档编辑页」的隐藏路由 + 一个把所有隐藏 C 页面串起来的隐藏目录。
--
-- ── 取号依据（issue #222）────────────────────────────────────────────────────
--   doc/lint-profile.yaml 给 QC 域分的是 13xx（D6=20260926）。票面 touches 原写
--   V20260926132*，但库里**已应用的最大版本是 202609261420**（DOC-PDF-001 的
--   V202609261420__DOC-PDF-001-doc-console.sql，2026-09-22 落库），而本项目
--   `spring.flyway.out-of-order=false`（framework 默认）→ 版本号小于已应用最大值时
--   后端**启动即 FlywayValidateException**（本项目因此重建过 4 次开发库）。
--   1320 < 1420 → 1320 不可用；13xx 段里所有 ≤1420 的号都不可用。
--   于是按 lint-profile「已应用的迁移终身不改名不改内容」+「保证后做的任务版本号一定更大」
--   两条规则，取**下一个未用的分钟 1430**：1430 > 1420（已应用最大值）→ 安全。
--   DOC 域 14xx 的下一分钟本来也是 1430，而本票在 D6 内、DOC-PDF-001 之后落地，
--   取 1430 不占用任何后续 ticket 的号（已记 issue #222）。
--
-- ── 为什么要有 5500 这个「隐藏目录」（issue #221 / #227）──────────────────────
--   若依 `SysMenuServiceImpl.buildMenus()` 对 `parent_id=0 且 menu_type='C' 且 is_frame='1'`
--   走的是 `isMenuFrame()` 专用分支：外层包一个 `Layout` 外壳（外壳 `hidden=true`、
--   `meta=null`、`name=路由名+menuId`），真正的页面变成**子路由且不带 hidden**。
--   用户可见行为是对的（外壳 hidden → 侧边栏不渲染），但 QC-WEB-001 accept 1 第 2 段
--   断的是「component = lqg/qc/editor/index 的那条路由自身 hidden==true」→ 照原样会红。
--   上游惯例（sys_menu 116/130/131/132/133）是所有隐藏 C 页面都挂在**目录菜单**下：
--   parent_id≠0 → 走 else 分支 → 路由自身就拿到 hidden=true。
--   方案 a（本票采用，见完工报告 §「方案选择」）：新建一个 `type='M'`、`visible='1'`、
--   `parent_id=0`、带合法 path 的隐藏目录，把 5510 与 DOC-PDF-001 的 5520 一起挂进去。
--   M 目录在 parent=0 时走 else 分支，自身也拿到 hidden=true → 整条链隐藏且可路由，
--   侧边栏什么都不出现，也不再依赖「Layout 外壳」那条特殊分支。
--   代价：路由 URL 从 `/doc-console` 变成 `/qc-console/doc-console`，5510 是
--   `/qc-console/qc-editor`（父目录 path 会前缀）。菜单 5510 的 path 仍按票面要求是
--   `qc-editor`（父目录的 path 是路由容器，不改票面断言的字段）。
--   ★ V202609261420 已应用，**不改它**：5520 的挂载点用本迁移 UPDATE，不动那一支的内容。
--
-- ── 菜单号（lint-profile：QC/DOC 5500-5599）──────────────────────────────────
--   5501-5503 = QC 三个 F（V202609261300）、5504-5505 = DOC 两个 F（V202609261410）、
--   5510 = 本票的 C 页面、5520 = DOC-PDF-001 的 C 页面。
--   本票新占：5500（隐藏 M 目录）/ 5510（C）/ 5511-5513（F 按钮）。
--
-- ── 关于 5511-5513（票面 §2 字面要求）────────────────────────────────────────
--   5501-5503 已经落了 `lqg:qc:{query,edit,publish}` 三串并授给 101/102
--   （QC-MODEL-001「权限串是本域地基，一次落齐」）。本票再落 5511-5513 是**同一组
--   perm 的第二份菜单行**：`SysMenuMapper.selectMenuPermsByUserId` 返回 `HashSet`
--   → 权限集合自动去重，accept 1 第 3 段的 sort 断言不受影响；代价只是菜单管理树里
--   多出三个同名按钮。按票面字面落，并记 WARN（见完工报告）。
--
-- ★ 只动菜单与授权，不动任何业务数据、不动任何 t_lqg_* 表。

BEGIN;

-- ── 1. 隐藏目录：所有隐藏 C 页面挂它下面（自身 visible='1' → 路由 hidden=true）──
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5500, '质控文档', 0, 5, 'qc-console', NULL, '', '1', '0', 'M', '1', '0', '', 'documentation', 103, 1, now(),
     'QC-WEB-001：隐藏路由容器（visible=1 → 自身 hidden=true，不进侧边栏）；挂 5510 质控文档编辑页与 5520 文档渲染状态页。为什么要有它见本迁移头注释（issue #221）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2. C 页面菜单 5510（隐藏；path / component 都非空，否则 vue-router 崩）────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5510, '质控文档编辑', 5500, 1, 'qc-editor', 'lqg/qc/editor/index', '', '1', '0', 'C', '1', '0',
     'lqg:qc:query', 'form', 103, 1, now(),
     'QC-WEB-001：三份质控文档的整页编辑（三个页签 + 图片位 + 附件 + 评分预览）；从样本总表行操作「质控文档」带 sampleId 进入')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3. F 按钮 5511-5513（票面 §2 字面要求；与 5501-5503 同 perm，集合去重）─────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5511, '质控查询', 5510, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:qc:query',   '#', 103, 1, now(), 'GET /lqg/qc/{sampleId}（与 5501 同 perm）'),
    (5512, '质控编辑', 5510, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:qc:edit',    '#', 103, 1, now(), 'PUT / POST / DELETE /lqg/qc/{sampleId}/**（与 5502 同 perm）'),
    (5513, '质控发布', 5510, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:qc:publish', '#', 103, 1, now(), '完成并同步 / 撤回（端点在 DOC-PUBLISH-001；与 5503 同 perm）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 4. 把 DOC-PDF-001 的 5520 也挂到隐藏目录下（整条链一致，见头注释方案 a）────
UPDATE sys_menu SET parent_id = 5500, order_num = 2 WHERE menu_id = 5520;

-- ── 5. 授权：101（lqg_admin）/ 102（lqg_internal）────────────────────────────
-- 5500 必须一起授：若依按 sys_role_menu 取菜单树，父目录没授权时子路由不会下发。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id IN (5500, 5510, 5511, 5512, 5513)) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
