-- doc/verify/seed/03-unit-group.sql —— 由 gen_seed.py 生成，别手改（改 gen_seed.py 重新生成）
-- requires: t_lqg_source_unit t_lqg_unit_group
-- 只许灌进 dev / test 库（reseed.sh 拒绝对名字不含 dev / test 的库动手）。
-- 加密列用测试口令 LqgTestAesKey#01 预先算好密文（后端 dev / test 的 mybatis-encryptor.password 必须是它）。
BEGIN;

-- ── 来源单位与组别
INSERT INTO t_lqg_source_unit (id, unit_name, unit_status, create_by, create_time) VALUES (9000009001, 'A 医院', 'active', 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_source_unit (id, unit_name, unit_status, create_by, create_time) VALUES (9000009002, 'B 大学', 'active', 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_source_unit (id, unit_name, unit_status, create_by, create_time) VALUES (9000009003, '已停用单位', 'disabled', 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_unit_group (id, unit_id, group_name, group_status, create_by, create_time) VALUES (9000009101, 9000009001, '肝胆外科组', 'active', 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_unit_group (id, unit_id, group_name, group_status, create_by, create_time) VALUES (9000009102, 9000009001, '消化内科组', 'active', 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_unit_group (id, unit_id, group_name, group_status, create_by, create_time) VALUES (9000009103, 9000009002, '类器官课题组', 'active', 9000000100, (now() - interval '30 days'));

COMMIT;
