-- V202610091000__CR-20261009-18-sample-species.sql
--
-- CR-20261009-18（甲方 2026-10-09 微信语音）：样本记录信息表、类器官收样记录、石蜡包埋送样记录、-80 冻存记录、
--   样本质控表都要加一项「种属」，比如人、鼠兔、移植猪、鸡，还可以添加其他的（甲方按种属归类，原来 Excel 台账一个种属一个工作表）。
--
-- 取号：大于已应用的最大版本 202610051000。
--
-- ★ 口径：
--   · 种属是「样本」的属性，只落在 t_lqg_sample 一处：石蜡块、冻存批次、质控文档都挂在样本上，读时带出，
--     不各存一份（各存一份就会出现「同一个样本、蜡块说是人、冻存说是鼠」）；
--   · 文本列：常用值来自字典 lqg_species（人 / 鼠兔 / 移植猪 / 鸡），列表里没有的可以直接手填 ——
--     「还可以添加其他的」。常填的新种属由管理员在「系统管理 → 字典管理」里加进字典，下拉里就有了；
--     字典的值与标签都写中文本身（与 lqg_hint_* 联想词字典同一个写法），库里存的就是看到的字；
--   · 新提交 / 修改时两类样本都必填（SubmitSegmentRules，服务层把关，库里不加 NOT NULL）；
--     本迁移之前录的样本没有种属，列表里显示「未填」，可按「未填」筛出来补；不猜着回填；
--   · 长度 VARCHAR(50)。
--
-- 只加列、加字典，不动任何已有数据。可重复执行。

ALTER TABLE t_lqg_sample ADD COLUMN IF NOT EXISTS species VARCHAR(50);

COMMENT ON COLUMN t_lqg_sample.species IS
    '种属（两类样本都必填；文本，常用值来自字典 lqg_species，可手填其它值；石蜡包埋 / 冻存 / 质控文档读时从这里带出）';

-- 按种属筛（工作台四张表、质控文档列表都有这个筛选；石蜡 / 冻存是子查询 sample_id IN (… species = ?)）
CREATE INDEX IF NOT EXISTS idx_sample_species ON t_lqg_sample (species) WHERE del_flag = '0';

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100026, '种属', 'lqg_species', 1, now(), '类器官送检系统；常用种属，下拉里列出，列表里没有的可以手填（CR-20261009-18）') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002600, 1, '人', '人', 'lqg_species', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002601, 2, '鼠兔', '鼠兔', 'lqg_species', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002602, 3, '移植猪', '移植猪', 'lqg_species', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002603, 4, '鸡', '鸡', 'lqg_species', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
