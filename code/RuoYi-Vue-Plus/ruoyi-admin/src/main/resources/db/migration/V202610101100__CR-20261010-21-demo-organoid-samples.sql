-- V202610101100__CR-20261010-21-demo-organoid-samples.sql
--
-- CR-20261010-21（Kevin 2026-10-10「可以造一点数据」）：给测试站补几条**类器官收样记录的演示数据**。
--   甲方要逐表验「选了种属只出相应的记录」，而模拟数据里类器官收样记录只有 T-oco01 一条，
--   那一页按种属筛只能看到「鼠兔 1 条 / 其它 0 条」；「石蜡包埋 · 鼠兔」「-80 冻存 · 鸡」也是空的。
--
-- 5 条类器官收样记录（都已核验有效）+ 2 个石蜡块 + 2 批冻存，种属覆盖人 / 移植猪 / 鸡 / 鼠兔：
--   T-oli11 人   肝类器官 P2      A 医院  + 冻存 1 批
--   T-oga12 人   胃类器官 P4      A 医院
--   T-oli13 移植猪 肝类器官 P3    B 大学  + 石蜡块 T-E13-1
--   T-oco14 鸡   结直肠类器官 P1  B 大学（合作单位孙老师送的，已核验）+ 冻存 1 批
--   T-opa15 鼠兔 胰腺类器官 P5    本中心  + 石蜡块 T-E15-1
--
-- ★ 这是演示数据，**不是验收基线**：没有写进 doc/verify/seed/（历史 accept 里「类器官样本只有 T-oco01 一条」
--   之类的断言都钉在 seed 上）。reseed 会把它们清掉（TRUNCATE 全部 t_lqg_*）；要重新灌，把本文件再执行一遍即可
--   （见 doc/verify/README.md「演示数据」）。备注里写着「演示数据」，要删：id 在 9000001101-9000001105 及其名下的行。
--
-- ★ 四道保护：
--   1. **只在已经灌过模拟数据的库里插**：认 seed 的 T-oco01（id 9000001009 + 送检单号 SJ90000009）和内部人员李工
--      （sys_user 9000000101）都在。生产库里没有这些行 → 在生产上本迁移一行都不插。
--   2. `ON CONFLICT DO NOTHING`（不指定冲突目标 = 任何唯一约束都算）：主键、送检单号、内部编号、石蜡块编号
--      只要有一个已被占用，这一行就跳过 —— 不会因为测试站上有人恰好录过同一个编号而让迁移失败、后端起不来。
--   3. 石蜡块 / 冻存批次只挂在「确实插进去了的」演示样本上（样本被第 2 条跳过时，它名下的也不插，不留孤儿行）。
--   4. 冻存批次都是「直接进液氮」（in_minus80 = 'N'）：不进 -80 超期提醒，不给测试站凭空多出待办。
--
-- 日期按执行当天往前推（收样在最近两周内）。可重复执行。

-- ── 样本 ────────────────────────────────────────────────────────────────────
INSERT INTO t_lqg_sample (id, submit_no, sample_kind, submit_source, submitter_id, verify_status, source_unit_id, source_unit_name,
                          create_by, create_time, species, organoid_type, passage, receive_date, internal_no, process_time,
                          has_viability_report, operator_name, verify_by, verify_time, remark)
SELECT v.id, v.submit_no, 'organoid', v.submit_source, v.submitter_id, 'valid', v.unit_id, v.unit_name,
       v.submitter_id, (CURRENT_DATE - v.days + TIME '10:00'), v.species, v.organoid_type, v.passage, CURRENT_DATE - v.days, v.internal_no,
       (CURRENT_DATE - v.days + TIME '15:30'), v.viability, '李工', 9000000101, (CURRENT_DATE - v.days + TIME '11:00'), '演示数据'
  FROM (VALUES
        (9000001101::bigint, 'SJ90000101', 'internal', 9000000101::bigint, 9000009001::bigint, 'A 医院', '人',     '肝类器官',     'P2', 'T-oli11', 12, 'Y'),
        (9000001102::bigint, 'SJ90000102', 'internal', 9000000101::bigint, 9000009001::bigint, 'A 医院', '人',     '胃类器官',     'P4', 'T-oga12',  9, 'N'),
        (9000001103::bigint, 'SJ90000103', 'internal', 9000000101::bigint, 9000009002::bigint, 'B 大学', '移植猪', '肝类器官',     'P3', 'T-oli13',  7, 'Y'),
        (9000001104::bigint, 'SJ90000104', 'external', 9000000114::bigint, 9000009002::bigint, 'B 大学', '鸡',     '结直肠类器官', 'P1', 'T-oco14',  5, 'Y'),
        (9000001105::bigint, 'SJ90000105', 'internal', 9000000101::bigint, NULL::bigint,       '本中心', '鼠兔',   '胰腺类器官',   'P5', 'T-opa15',  3, 'Y')
       ) AS v(id, submit_no, submit_source, submitter_id, unit_id, unit_name, species, organoid_type, passage, internal_no, days, viability)
 WHERE EXISTS (SELECT 1 FROM t_lqg_sample s WHERE s.id = 9000001009 AND s.submit_no = 'SJ90000009')
   AND EXISTS (SELECT 1 FROM sys_user u WHERE u.user_id = 9000000101)
ON CONFLICT DO NOTHING;

-- ── 石蜡块（挂在演示样本上；样本没插进去的不插）─────────────────────────────
INSERT INTO t_lqg_embed (id, sample_id, paraffin_block_no, sample_type, organoid_source_type, tissue_receive_time, paraffin_embed_time,
                         section_time, stain_types, operator_name, submit_source, submitter_id, verify_status, verify_by, create_by, create_time, remark)
SELECT v.id, v.sample_id, v.block_no, '类器官', v.source_type, CURRENT_DATE - v.days, CURRENT_DATE - v.days + 2,
       CURRENT_DATE - v.days + 3, v.stains, '李工', 'internal', 9000000101, 'valid', 9000000101, 9000000101, (CURRENT_DATE - v.days + TIME '16:00'), '演示数据'
  FROM (VALUES
        (9000002101::bigint, 9000001103::bigint, 'SJ90000103', 'T-E13-1', '肝类器官',   7, 'HE'),
        (9000002102::bigint, 9000001105::bigint, 'SJ90000105', 'T-E15-1', '胰腺类器官', 3, 'HE,IHC')
       ) AS v(id, sample_id, sample_submit_no, block_no, source_type, days, stains)
 WHERE EXISTS (SELECT 1 FROM t_lqg_sample s WHERE s.id = v.sample_id AND s.submit_no = v.sample_submit_no AND s.del_flag = '0')
ON CONFLICT DO NOTHING;

-- ── 冻存批次（直接进液氮，不参与 -80 超期提醒）──────────────────────────────
INSERT INTO t_lqg_cryo_batch (id, sample_id, cryo_name, passage, freeze_time, init_qty, density, in_minus80, ln2_location, frozen_by,
                              create_by, create_time, remark)
SELECT v.id, v.sample_id, v.cryo_name, v.passage, CURRENT_DATE - v.days, v.qty, v.density, 'N', v.ln2, '李工',
       9000000101, (CURRENT_DATE - v.days + TIME '17:00'), '演示数据'
  FROM (VALUES
        (9000003101::bigint, 9000001101::bigint, 'SJ90000101', 'T-oli11-GZ-N-P2-EM2-2e5', 'P2', 10, 5, '2e5', '2号罐-1架-C3'),
        (9000003102::bigint, 9000001104::bigint, 'SJ90000104', 'T-oco14-JC-T-P1-EM1-5e5', 'P1',  4, 3, '5e5', '2号罐-2架-A1')
       ) AS v(id, sample_id, sample_submit_no, cryo_name, passage, days, qty, density, ln2)
 WHERE EXISTS (SELECT 1 FROM t_lqg_sample s WHERE s.id = v.sample_id AND s.submit_no = v.sample_submit_no AND s.del_flag = '0')
ON CONFLICT DO NOTHING;
