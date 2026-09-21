-- AUTH-GROUP-001 · 来源单位与组别两张表 + 「来源单位与组别」「外部用户」两个菜单（doc/lint-profile.yaml）
--
-- 取号依据 doc/lint-profile.yaml：
--   * 日期段 D1 = 20260921（本票 phase D1）；HHmm 按域分段，AUTH = 09xx；
--   * 0930 未被占用，且大于当前最大版本 0920（AUTH-STAFF-001）—— out-of-order=false 下不会触发
--     FlywayValidateException（SYS-WEB-001 踩过跨域补号的坑：版本号小于已应用的迁移会让后端启动即失败）。
-- 菜单号依据：AUTH 域 5100-5199。5100 目录（AUTH-STAFF-001 已建）+ 本票的 5120 / 5130 挂同一目录下。
--
-- 第一段是 doc/tools/gen_ddl_pg.py --migration V202609210930__AUTH-GROUP-001-unit-group.sql 的**逐字节输出**
-- （t_lqg_source_unit / t_lqg_unit_group：公共 6 字段、部分唯一索引 WHERE del_flag='0'、无 tenant_id / del_unique）。

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_source_unit：来源单位（实验室在工作台维护；外部自填的新单位先落 pending）
CREATE TABLE t_lqg_source_unit (
    id                      BIGINT NOT NULL,
    unit_name               VARCHAR(100) NOT NULL,
    unit_status             VARCHAR(16) NOT NULL DEFAULT 'active',
    remark                  VARCHAR(500),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_source_unit PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_source_unit IS '来源单位（实验室在工作台维护；外部自填的新单位先落 pending）';
COMMENT ON COLUMN t_lqg_source_unit.id IS '主键（雪花）';
COMMENT ON COLUMN t_lqg_source_unit.unit_name IS '单位名称';
COMMENT ON COLUMN t_lqg_source_unit.unit_status IS 'active 启用 / pending 待核验（外部自填）/ disabled 停用';
COMMENT ON COLUMN t_lqg_source_unit.remark IS '备注';
COMMENT ON COLUMN t_lqg_source_unit.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_source_unit.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_source_unit.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_source_unit.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_source_unit.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_source_unit.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_unit_name ON t_lqg_source_unit (unit_name) WHERE del_flag = '0';

-- ── t_lqg_unit_group：组别（挂在来源单位下；同单位同组的外部用户互看样本）
CREATE TABLE t_lqg_unit_group (
    id                      BIGINT NOT NULL,
    unit_id                 BIGINT NOT NULL,
    group_name              VARCHAR(100) NOT NULL,
    group_status            VARCHAR(16) NOT NULL DEFAULT 'active',
    remark                  VARCHAR(500),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_unit_group PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_unit_group IS '组别（挂在来源单位下；同单位同组的外部用户互看样本）';
COMMENT ON COLUMN t_lqg_unit_group.id IS '主键';
COMMENT ON COLUMN t_lqg_unit_group.unit_id IS 'FK→t_lqg_source_unit.id';
COMMENT ON COLUMN t_lqg_unit_group.group_name IS '组别名称（同一单位内唯一）';
COMMENT ON COLUMN t_lqg_unit_group.group_status IS 'active 启用 / pending 待核验 / disabled 停用';
COMMENT ON COLUMN t_lqg_unit_group.remark IS '备注';
COMMENT ON COLUMN t_lqg_unit_group.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_unit_group.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_unit_group.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_unit_group.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_unit_group.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_unit_group.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_unit_group ON t_lqg_unit_group (unit_id, group_name) WHERE del_flag = '0';
CREATE INDEX idx_group_unit ON t_lqg_unit_group (unit_id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- 第二段：菜单与按钮（不是生成器的输出）
-- ═══════════════════════════════════════════════════════════════════════════════
--
--   5120 「来源单位与组别」 C path='unit'    component='lqg/auth/unit/index'    (UI:admin.auth.unit)
--   5130 「外部用户」     C path='extuser' component='lqg/auth/extuser/index' (UI:admin.auth.extuser)
--   5121-5124 / 5131-5135 各自的 F 按钮
--
-- ★ 两页都授给 101（lqg_admin）**与** 102（lqg_internal）：核验组别是日常工作，普通内部人员也要能做。
--   accept 第 3 条用 `--as staff`（= 102）取 /system/menu/getRouters，要求两个页面都在——
--   只授管理员就会红。这与 AUTH-STAFF-001 的「内部人员授权只授 101」是两条不同的口径，别照抄。
-- ★ 父目录 5100 一起授（且对 102 也补授一次）：上游 SysMenuServiceImpl.selectMenuTreeByUserId 用
--   getChildPerms(menus, 0) 建树，父节点不在授权集合里，子菜单**整条消失**（不报错、就是没有）。
--   SYS-WEB-001 / AUTH-STAFF-001 都踩过。AUTH-STAFF-001 的 0920 迁移末尾显式 DELETE 过 102 对 5100 的
--   授权，所以这里必须重新 INSERT 一次（ON CONFLICT DO NOTHING 保证幂等）。
-- ★ 单位 / 组别**不物理删**（只启用 / 停用），所以按钮里没有「删除」，只有查询 / 新增 / 修改 / 启停。

BEGIN;

-- ── 菜单：来源单位与组别 ────────────────────────────────────────────────────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5120, '来源单位与组别', 5100, 2, 'unit', 'lqg/auth/unit/index', '',
        '1', '0', 'C', '0', '0', 'lqg:auth:unit:list', 'tree',
        103, 1, now(), 'AUTH-GROUP-001：左单位右组别，增改与启用/停用（不物理删）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 菜单：外部用户（组别核验）──────────────────────────────────────────────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5130, '外部用户', 5100, 3, 'extuser', 'lqg/auth/extuser/index', '',
        '1', '0', 'C', '0', '0', 'lqg:auth:extuser:list', 'people',
        103, 1, now(), 'AUTH-GROUP-001：外部用户列表 + 组别核验（通过 / 驳回 / 改归组）')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 按钮（F）：单位 / 组别 / 外部用户核验的权限串 ───────────────────────────
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5121, '单位查询',   5120, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:unit:list',   '#', 103, 1, now(), ''),
    (5122, '单位新增',   5120, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:unit:add',    '#', 103, 1, now(), ''),
    (5123, '单位修改',   5120, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:unit:edit',   '#', 103, 1, now(), ''),
    (5124, '单位启停',   5120, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:unit:toggle', '#', 103, 1, now(), ''),
    (5125, '组别查询',   5120, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:group:list',   '#', 103, 1, now(), ''),
    (5126, '组别新增',   5120, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:group:add',    '#', 103, 1, now(), ''),
    (5127, '组别修改',   5120, 7, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:group:edit',   '#', 103, 1, now(), ''),
    (5128, '组别启停',   5120, 8, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:group:toggle', '#', 103, 1, now(), ''),
    (5131, '外部用户查询', 5130, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:extuser:list',   '#', 103, 1, now(), ''),
    (5132, '组别核验',    5130, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:extuser:verify', '#', 103, 1, now(), ''),
    (5133, '改归组',      5130, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:auth:extuser:regroup','#', 103, 1, now(), '')
ON CONFLICT (menu_id) DO NOTHING;

-- ── 授权：101（lqg_admin）与 102（lqg_internal）都拿到目录 + 两个菜单 + 全部按钮 ──
-- 目录 5100 对 101 是重复 INSERT（0920 已授过一次）→ ON CONFLICT DO NOTHING 幂等；
-- 对 102 是**必须**的（0920 末尾把它删掉过，本票要授回来）。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (
    SELECT menu_id FROM sys_menu
    WHERE menu_id IN (5100, 5120, 5121, 5122, 5123, 5124, 5125, 5126, 5127, 5128,
                      5130, 5131, 5132, 5133)
) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
