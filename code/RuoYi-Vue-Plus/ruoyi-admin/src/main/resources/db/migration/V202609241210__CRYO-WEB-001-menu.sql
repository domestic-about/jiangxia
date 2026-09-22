-- V202609241210__CRYO-WEB-001-menu.sql
--
-- CRYO-WEB-001 · 工作台「冻存管理」页的菜单与按钮权限（CRYO 号段 5410 段）。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D4 = 20260924（本票 phase D4）；HHmm 按域分段，CRYO = 12xx。
--   已占用：1200（CRYO-MODEL-001 两张表 + 5401-5407 权限行）、1205（CRYO-REMIND-001 config）。
--   票面 §2 点名 V202609241210__CRYO-WEB-001-menu.sql —— 版本号 202609241210 大于已应用的
--   最大值 202609241205 → out-of-order=false 下不会触发 FlywayValidateException
--   （SYS-WEB-001 / AUTH-EXT-002 踩过跨域补号的坑）。
--
-- ★★ 为什么本票**不需要**像 EMBED-WEB-001 那样搬号：
--   accept 2 同时要求
--     ① SELECT menu_id||':'||path||':'||component WHERE menu_id = 5410  == '5410:cryo:lqg/cryo/index'
--     ② getRouters 里 component == 'lqg/cryo/index' 的路由**恰好 1 条**
--   CRYO-MODEL-001 当时**刻意只建 F 按钮（5401-5407）、不建 C 页面菜单**，
--   正是为了把 5410 留给本票（见 V202609241200 的第 28-35 行注释）。上游已核对：
--   `SELECT count(*) FROM sys_menu WHERE component = 'lqg/cryo/index'` = 0（本迁移前）。
--   所以这里直接 5410 + 5411-5417 新建即可，**不删任何既有菜单行**。
--
-- ★ 5401-5407 保留不动：它们的 perms 与 5411-5417 逐字相同，sa-token 的权限集合是字符串集合、
--   与 menu_id 无关，所以 /lqg/cryo/** 的鉴权一个字都没变；重复的 perms 行无害。
--   （反面：若把 5401-5407 删掉，就是给别的票/别的角色的授权挖坑，而本票没有搬号的理由。）
--
-- ★ 只动菜单与授权：不建表、不加列、不动任何业务数据、不动任何 t_lqg_* 表。

BEGIN;

-- ── 1) 5410：冻存管理（C：顶级菜单，path='cryo' → 工作台路由 /cryo）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5410, '冻存管理', 0, 6, 'cryo', 'lqg/cryo/index', '',
        '1', '0', 'C', '0', '0', 'lqg:cryo:list', 'table',
        103, 1, now(), 'CRYO-WEB-001：工作台冻存批次列表（页签计数、超期置顶标红、出入库与盘点调整、转液氮、流水抽屉、按模板导出）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2) 5411-5417：七个按钮权限（与 @SaCheckPermission 逐字一致）
--    export / flow 两条串 5406 / 5407 已由上游落好；这里逐字重建是为了让 5410 段的
--    perms 集合自足（页面上的按钮用 v-hasPermi 直接读它），不改变任何鉴权结果。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5411, '冻存查询', 5410, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:list',   '#', 103, 1, now(), '列表（GET /lqg/cryo/batch/list，行带 remainingQty / location / overdue）'),
    (5412, '冻存详情', 5410, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:query',  '#', 103, 1, now(), '详情与流水（GET /lqg/cryo/batch/{id}、GET …/{id}/flows）'),
    (5413, '冻存新增', 5410, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:add',    '#', 103, 1, now(), '新建批次（POST /lqg/cryo/batch）'),
    (5414, '冻存修改', 5410, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:edit',   '#', 103, 1, now(), '修改批次 / 登记转液氮（PUT /lqg/cryo/batch、PUT …/{id}/to-ln2；初始支数可改，逐笔不得为负）'),
    (5415, '冻存删除', 5410, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:remove', '#', 103, 1, now(), '软删批次（DELETE /lqg/cryo/batch/{ids}；有未删流水的不许删）'),
    (5416, '冻存导出', 5410, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:export', '#', 103, 1, now(), '按模板导出 xlsx（POST /lqg/cryo/batch/export，模板 9 列 + 代数 + 当前剩余/支）'),
    (5417, '冻存流水', 5410, 7, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:flow',   '#', 103, 1, now(), '取走 / 补入 / 盘点调整 / 改删登记（POST|PUT|DELETE …/{id}/flow）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3) 授权：101（lqg_admin）与 102（lqg_internal）都能调 /lqg/cryo/**
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5410 AND 5417) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
