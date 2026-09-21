-- doc/verify/seed/02-wx-bind-ext-profile.sql —— 由 gen_seed.py 生成，别手改（改 gen_seed.py 重新生成）
-- requires: t_lqg_wx_bind t_lqg_ext_profile
-- 只许灌进 dev / test 库（reseed.sh 拒绝对名字不含 dev / test 的库动手）。
-- 加密列用测试口令 LqgTestAesKey#01 预先算好密文（后端 dev / test 的 mybatis-encryptor.password 必须是它）。
BEGIN;

-- ── 微信绑定：mock 登录 xcxCode=mock:<身份> ↔ openid=mock-openid-<身份>
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000200, 9000000101, 'mock-openid-staff', '13800000001', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000201, 9000000111, 'mock-openid-extA', '13800000011', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000202, 9000000112, 'mock-openid-extB', '13800000012', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000203, 9000000113, 'mock-openid-extC', '13800000013', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000204, 9000000114, 'mock-openid-extD', '13800000014', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000205, 9000000115, 'mock-openid-extE', '13800000015', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_wx_bind (id, user_id, openid, phone, last_login_time, create_by, create_time) VALUES (9000000206, 9000000116, 'mock-openid-extF', '13800000016', (CURRENT_DATE - 1 + TIME '10:00'), 9000000100, (now() - interval '30 days'));

-- ── 外部档案：A、B 同组已核验｜C 同单位异组｜D 异单位｜E 与 A 同组但【未核验】（病灶）｜F 自填单位待核验
INSERT INTO t_lqg_ext_profile (id, user_id, real_name, unit_id, group_id, bind_status, verified_by, verified_time, create_by, create_time) VALUES (9000000311, 9000000111, '王医生', 9000009001, 9000009101, 'verified', 9000000100, (CURRENT_DATE - 40 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_ext_profile (id, user_id, real_name, unit_id, group_id, bind_status, verified_by, verified_time, create_by, create_time) VALUES (9000000312, 9000000112, '陈医生', 9000009001, 9000009101, 'verified', 9000000100, (CURRENT_DATE - 40 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_ext_profile (id, user_id, real_name, unit_id, group_id, bind_status, verified_by, verified_time, create_by, create_time) VALUES (9000000313, 9000000113, '赵医生', 9000009001, 9000009102, 'verified', 9000000100, (CURRENT_DATE - 40 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_ext_profile (id, user_id, real_name, unit_id, group_id, bind_status, verified_by, verified_time, create_by, create_time) VALUES (9000000314, 9000000114, '孙老师', 9000009002, 9000009103, 'verified', 9000000100, (CURRENT_DATE - 40 + TIME '10:00'), 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_ext_profile (id, user_id, real_name, unit_id, group_id, bind_status, create_by, create_time) VALUES (9000000315, 9000000115, '周医生', 9000009001, 9000009101, 'pending', 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_ext_profile (id, user_id, real_name, unit_name_input, group_name_input, bind_status, create_by, create_time) VALUES (9000000316, 9000000116, '吴同学', 'C 研究所', '肿瘤组', 'pending', 9000000100, (now() - interval '30 days'));

COMMIT;
