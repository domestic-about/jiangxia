/*
 * SYS-BASE-001 · Flyway 第 3 支迁移：三个业务角色
 *
 * role_id 取号依据 doc/lint-profile.yaml：101 = lqg_admin（实验室管理员）｜102 = lqg_internal（内部人员）
 *                                      103 = lqg_external（外部人员）
 * 号段 101 起是项目约定（若依自带 superadmin = 1；3000+ 留给未来升级），并行 ticket 不许跨段占用。
 *
 * 用显式列名而不是 `insert into sys_role values(...)`：上游 PG 表有 tenant_id / menu_check_strictly /
 * dept_check_strictly 这些列，按值顺手写会随上游列序漂移而静默错位。
 * 多租户已关（ADR-0001）→ tenant_id 取框架默认值 '000000'（关着的时候没人按它过滤）。
 *
 * 01-accounts.sql 里有一份 ON CONFLICT 兜底的同名角色（为了能单独灌 seed）；正式环境以本迁移为准。
 */
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
                      dept_check_strictly, status, del_flag, create_dept, create_by, create_time, remark)
VALUES (101, '000000', '实验室管理员', 'lqg_admin', 11, '1', true, true, '0', '0', 103, 1, now(), '类器官送检系统角色（SYS-BASE-001）')
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
                      dept_check_strictly, status, del_flag, create_dept, create_by, create_time, remark)
VALUES (102, '000000', '内部人员', 'lqg_internal', 12, '1', true, true, '0', '0', 103, 1, now(), '类器官送检系统角色（SYS-BASE-001）')
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
                      dept_check_strictly, status, del_flag, create_dept, create_by, create_time, remark)
VALUES (103, '000000', '外部人员', 'lqg_external', 13, '1', true, true, '0', '0', 103, 1, now(), '类器官送检系统角色（SYS-BASE-001）')
ON CONFLICT (role_id) DO NOTHING;
