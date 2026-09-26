-- V202609281000__CR-20260924-10-sample-passage.sql
--
-- CR-20260924-10（甲方 2026-09-24 测试问题记录表第 18 行）：类器官收样记录加一项「代数」。
--   甲方原话：「合作单位填类器官收样记录只有三项：来源单位、类器官类型、备注」
--             合作单位和内部人员的都要再添加一项：代数
--
-- 取号：G 批 A 组号段 V2026092810xx；大于已应用的最大版本 202609272000（out-of-order=false 不会报错）。
--
-- ★ 口径（与冻存批次的代数 t_lqg_cryo_batch.passage 同一规则）：
--   · 只有 sample_kind = 'organoid' 的行有意义；组织样本（tissue）恒为空 —— 写路径
--     （SampleSubmitSegmentWriter）对组织样本一律写 NULL，不是靠前端不发；
--   · 选填（列可空）：已有的类器官收样记录没有这一项，照旧有效，不回填；
--   · 填了就必须形如 P3（^P\d{1,3}$，去首尾空白、小写 p 转大写后再判），校验在
--     SubmitSegmentRules（复用 CryoBalanceChecker.requirePassage），库里不加 CHECK ——
--     与冻存批次那一列同样只在服务层把关，免得两处规则各写一份。
--   · 长度与冻存批次的代数列一致：VARCHAR(10)。
--
-- ★ 这一列不是内部字段：外部自己填的代数，外部详情里看得到（ExtSampleDetailVo）。
--
-- 只加列，不动任何已有数据。

ALTER TABLE t_lqg_sample ADD COLUMN IF NOT EXISTS passage VARCHAR(10);

COMMENT ON COLUMN t_lqg_sample.passage IS
    '代数（类器官收样记录才有；选填，形如 P3，与冻存批次代数同一规则 ^P\d{1,3}$）；组织样本恒为空';
