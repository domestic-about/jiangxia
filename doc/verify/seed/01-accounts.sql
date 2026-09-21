-- doc/verify/seed/01-accounts.sql —— 由 gen_seed.py 生成，别手改（改 gen_seed.py 重新生成）
-- requires: 
-- 只许灌进 dev / test 库（reseed.sh 拒绝对名字不含 dev / test 的库动手）。
-- 加密列用测试口令 LqgTestAesKey#01 预先算好密文（后端 dev / test 的 mybatis-encryptor.password 必须是它）。
BEGIN;

-- ── 角色（正式环境由 AUTH-STAFF-001 的迁移建；这里 ON CONFLICT 兜底，方便单独灌 seed）
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_time) VALUES (101, '实验室管理员', 'lqg_admin', 11, '1', '0', '0', now()) ON CONFLICT (role_id) DO NOTHING;
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_time) VALUES (102, '内部人员', 'lqg_internal', 12, '1', '0', '0', now()) ON CONFLICT (role_id) DO NOTHING;
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, del_flag, create_time) VALUES (103, '外部人员', 'lqg_external', 13, '1', '0', '0', now()) ON CONFLICT (role_id) DO NOTHING;

-- ── 测试账号：1 管理员 + 1 内部 + 6 外部（手机号 138000000xx 是号段里的测试号，不是真人）
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000100, 'lqgadmin', '测试管理员', 'sys_user', '13800000000', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000100, 101);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000101, 'lqg_13800000001', '李工', 'sys_user', '13800000001', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000101, 102);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000111, 'wx_13800000011', '王医生', 'app_user', '13800000011', '', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000111, 103);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000112, 'wx_13800000012', '陈医生', 'app_user', '13800000012', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000112, 103);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000113, 'wx_13800000013', '赵医生', 'app_user', '13800000013', '', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000113, 103);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000114, 'wx_13800000014', '孙老师', 'app_user', '13800000014', '', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000114, 103);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000115, 'wx_13800000015', '周医生', 'app_user', '13800000015', '', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000115, 103);
INSERT INTO sys_user (user_id, user_name, nick_name, user_type, phonenumber, password, status, del_flag, create_time) VALUES (9000000116, 'wx_13800000016', '吴同学', 'app_user', '13800000016', '', '0', '0', (now() - interval '30 days'));
INSERT INTO sys_user_role (user_id, role_id) VALUES (9000000116, 103);

COMMIT;
