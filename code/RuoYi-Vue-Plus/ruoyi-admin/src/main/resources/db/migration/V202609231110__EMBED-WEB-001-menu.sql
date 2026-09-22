-- V202609231110__EMBED-WEB-001-menu.sql
--
-- EMBED-WEB-001 · 工作台「石蜡包埋」页的菜单与按钮权限（把 EMBED-MODEL-001 落的 5300 段搬到 5310 段）。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D3 = 20260923（本票 phase D3）；HHmm 按域分段，EMBED = 11xx。
--   1100（EMBED-MODEL-001）已占用；1110 未被占用，且不碰 EMBED-MODEL-001 声明的那一支
--   （它 accept 1 第 2 段断的是 `V20260923110%__EMBED-MODEL-001-%` 恰 1 行 —— 本文件名里没有
--   EMBED-MODEL-001 这个串，不影响那一格）。
--   版本号 202609231110 大于已应用的最大值 202609231100 → out-of-order=false 下不会触发
--   FlywayValidateException（SYS-WEB-001 / AUTH-EXT-002 踩过跨域补号的坑）。
--
-- ★★ 为什么必须把 5300-5307 整体搬成 5310-5317（本票最容易做错的一步）：
--   accept 2 同时要求
--     ① SELECT menu_id||':'||path||':'||component WHERE menu_id = 5310  == '5310:embed:lqg/embed/index'
--     ② getRouters 里 component == 'lqg/embed/index' 的路由**恰好 1 条**
--   而 EMBED-MODEL-001 的 V202609231100 已经把 5300 建成了「石蜡包埋」（C、path='embed'、
--   component='lqg/embed/index'）。若在 5300 下再补一行 5310，② 会数成 2 条 → 红
--   （SAMPLE-WEB-001 对 5200→5210 做过同一件事，先例在盘）。
--   所以本迁移：先建 5310（C，顶级）+ 5311-5317（F，七个权限串），授给 101（lqg_admin）与
--   102（lqg_internal），再删掉 5300-5307（旧节点与旧授权行）。
--   授权**不丢**：5301-5307 的七个权限串在 5311-5317 上逐字重建；sa-token 的权限集合来自
--   perms 字符串、与 menu_id 无关，所以 /lqg/embed/** 的鉴权一个字都没变。
--   5310 下的 perms 集合因此恰好是 accept 要的那七个（list/query/add/edit/remove/export/verify）。
--
-- ★ 只动菜单与授权，不建表、不动任何业务数据、不动任何 t_lqg_* 表、不动本票新加的导出端点。

BEGIN;

-- ── 1) 5310：石蜡包埋（C：顶级菜单，从 5300 搬来，order_num 不变 = 5）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5310, '石蜡包埋', 0, 5, 'embed', 'lqg/embed/index', '',
        '1', '0', 'C', '0', '0', 'lqg:embed:list', 'table',
        103, 1, now(), 'EMBED-WEB-001：工作台石蜡包埋送样记录（内 / 外部与核验状态、待核验置顶、核验抽屉、按模板导出）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 2) 5311-5317：七个按钮权限（与 @SaCheckPermission 逐字一致）
--    七个权限串与 5301-5307 逐字相同；export 对应的端点在 EMBED-WEB-001 才落
--    （POST /lqg/embed/export，同一个迁移批次的后端代码）。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5311, '包埋查询', 5310, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:list',   '#', 103, 1, now(), '列表（GET /lqg/embed/list）'),
    (5312, '包埋详情', 5310, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:query',  '#', 103, 1, now(), '详情（GET /lqg/embed/{id}）'),
    (5313, '包埋新增', 5310, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:add',    '#', 103, 1, now(), '内部录入（POST /lqg/embed，石蜡块编号必填、直接 valid）'),
    (5314, '包埋修改', 5310, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:edit',   '#', 103, 1, now(), '修改 / 补填（PUT /lqg/embed，待核验 / 无效的直接 400）'),
    (5315, '包埋删除', 5310, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:remove', '#', 103, 1, now(), '软删（DELETE /lqg/embed/{ids}）'),
    (5316, '包埋导出', 5310, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:export', '#', 103, 1, now(), '按筛选导出 xlsx（POST /lqg/embed/export，表头照甲方模板 16 列）'),
    (5317, '包埋核验', 5310, 7, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:verify', '#', 103, 1, now(), '核验 / 改判（PUT /lqg/embed/{id}/verify）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 3) 授权：101（lqg_admin）与 102（lqg_internal）都能调 /lqg/embed/**
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5310 AND 5317) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ── 4) 卸掉旧挂载点 5300-5307（内容已在 5310-5317 上重建，避免同一 component 出现两条路由）
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 5300 AND 5307;
DELETE FROM sys_menu      WHERE menu_id BETWEEN 5300 AND 5307;

COMMIT;
