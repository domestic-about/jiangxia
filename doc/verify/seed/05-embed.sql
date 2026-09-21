-- doc/verify/seed/05-embed.sql —— 由 gen_seed.py 生成，别手改（改 gen_seed.py 重新生成）
-- requires: t_lqg_sample t_lqg_embed t_lqg_embed_marker
-- 只许灌进 dev / test 库（reseed.sh 拒绝对名字不含 dev / test 的库动手）。
-- 加密列用测试口令 LqgTestAesKey#01 预先算好密文（后端 dev / test 的 mybatis-encryptor.password 必须是它）。
BEGIN;

-- ── 石蜡包埋：A1 两块（一块已切片 HE+IHC、一块未切片）｜B1 无染色｜D1 其他染色｜I1 一块【软删】（病灶：不得计入切片染色提示）｜A2 extA 提交的送样【待核验、没有石蜡块编号，所挂样本也待核验】（病灶：判有效必须被拒；不算一块石蜡；外部看得到）。李工（staff）建的 = 2001、2003 + 软删的 2005 → 内部历史编辑记录期望 {2001, 2003}
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, tissue_receive_time, tissue_process_time, agarose_embed_time, embed_by, dehydrate_time, agarose_send_time, paraffin_embed_time, section_time, stain_types, operator_name, submit_source, submitter_id, verify_status, verify_by, create_by, create_time) VALUES (9000002001, 9000001001, 'T-E01-1', '组织', CURRENT_DATE - 30, CURRENT_DATE - 30, CURRENT_DATE - 29, '李工', CURRENT_DATE - 28, CURRENT_DATE - 28, CURRENT_DATE - 27, CURRENT_DATE - 26, 'HE,IHC', '李工', 'internal', 9000000101, 'valid', 9000000101, 9000000101, (now() - interval '30 days'));
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, organoid_source_type, agarose_embed_time, submit_source, submitter_id, verify_status, verify_by, create_by, create_time) VALUES (9000002002, 9000001001, 'T-E01-2', '类器官', '肝类器官', CURRENT_DATE - 12, 'internal', 9000000100, 'valid', 9000000100, 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, paraffin_embed_time, section_time, stain_types, submit_source, submitter_id, verify_status, verify_by, create_by, create_time) VALUES (9000002003, 9000001004, 'T-E02-1', '组织', CURRENT_DATE - 20, CURRENT_DATE - 19, 'NONE', 'internal', 9000000101, 'valid', 9000000101, 9000000101, (now() - interval '30 days'));
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, section_time, stain_types, stain_other, submit_source, submitter_id, verify_status, verify_by, create_by, create_time) VALUES (9000002004, 9000001006, 'T-E04-1', '组织', CURRENT_DATE - 10, 'OTHER', 'Masson', 'internal', 9000000100, 'valid', 9000000100, 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, section_time, stain_types, del_flag, submit_source, submitter_id, verify_status, verify_by, create_by, create_time) VALUES (9000002005, 9000001008, 'T-E05-X', '组织', CURRENT_DATE - 40, 'HE,IF', '1', 'internal', 9000000101, 'valid', 9000000101, 9000000101, (now() - interval '30 days'));
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, submit_source, submitter_id, verify_status, create_by, create_time) VALUES (9000002006, 9000001002, NULL, '组织', 'external', 9000000111, 'pending', 9000000111, (CURRENT_DATE + TIME '00:00:04'));
INSERT INTO t_lqg_embed_marker (id, embed_id, marker_name, expression, sort, create_by, create_time) VALUES (9000002101, 9000002001, 'Ki67', 'strong', 1, 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_embed_marker (id, embed_id, marker_name, expression, sort, create_by, create_time) VALUES (9000002102, 9000002001, 'CK19', 'negative', 2, 9000000100, (now() - interval '30 days'));
INSERT INTO t_lqg_embed_marker (id, embed_id, marker_name, expression, sort, create_by, create_time) VALUES (9000002103, 9000002004, NULL, 'weak', 1, 9000000100, (now() - interval '30 days'));

COMMIT;
