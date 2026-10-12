-- V202610121000__CR-20261012-22-operator-user.sql
--
-- CR-20261012-22（甲方 2026-10-10 / 10-11 微信，Kevin 2026-10-12 拍板）：按人统计工作量
--   「某个操作人这个月 / 今年 / 一共做了多少个」—— 样本记录信息表、类器官送样记录、石蜡包埋送样记录、-80 冻存记录各算各的。
--
-- 四张表上的人名（操作人 / 包埋人 / 冻存人）以前只有一栏手填文字，「刘佳」「刘 佳」「小刘」会被算成三个人。
-- 这里在每栏文字旁边加一栏**内部人员账号 id**；原来的姓名文字**保留**（导出 Excel、Word 文档、对外展示照旧读它，
-- 人员改名 / 撤销授权之后历史记录上的名字也不变）。
--
--   t_lqg_sample.operator_id        ← operator_name（操作人）
--   t_lqg_embed.embed_by_id         ← embed_by（包埋人）
--   t_lqg_embed.operator_id         ← operator_name（操作人）
--   t_lqg_cryo_batch.frozen_by_id   ← frozen_by（冻存人）
--
-- ★ 不加外键：与 verify_by / submitter_id 一个做法（sys_user 归上游管，撤销授权不删账号）。
-- ★ 冻存取用流水的「经手人」（t_lqg_cryo_flow.operator_name）不在本次范围：甲方要的是「冻了多少」，不是取用次数。
--
-- ── 历史数据 ───────────────────────────────────────────────────────────────────
--   姓名与**恰好一个**内部人员（角色 lqg_admin / lqg_internal）的姓名完全一致（去首尾空白）→ 自动关联；
--   对不上的（没建账号、写法不一样、重名）**不猜**，id 留空、文字原样留着 —— 统计页单列为「未关联账号」，
--   在工作台把人建好后重新选一次即可。不动 update_by / update_time（不让历史记录显示成「刚被改过」）。
--
-- 可重复执行（IF NOT EXISTS；回填只填 id 仍为空的）。

ALTER TABLE t_lqg_sample     ADD COLUMN IF NOT EXISTS operator_id  BIGINT;
ALTER TABLE t_lqg_embed      ADD COLUMN IF NOT EXISTS embed_by_id  BIGINT;
ALTER TABLE t_lqg_embed      ADD COLUMN IF NOT EXISTS operator_id  BIGINT;
ALTER TABLE t_lqg_cryo_batch ADD COLUMN IF NOT EXISTS frozen_by_id BIGINT;

COMMENT ON COLUMN t_lqg_sample.operator_id        IS '操作人 user_id（内部人员；CR-20261012-22。空 = 没填，或历史手填的姓名对不上账号）';
COMMENT ON COLUMN t_lqg_sample.operator_name      IS '操作人姓名（选人时的快照；历史数据是手填文字）';
COMMENT ON COLUMN t_lqg_embed.embed_by_id         IS '包埋人 user_id（内部人员；CR-20261012-22）';
COMMENT ON COLUMN t_lqg_embed.embed_by            IS '包埋人姓名（选人时的快照；历史数据是手填文字）';
COMMENT ON COLUMN t_lqg_embed.operator_id         IS '操作人 user_id（内部人员；CR-20261012-22）';
COMMENT ON COLUMN t_lqg_embed.operator_name       IS '操作人姓名（选人时的快照；历史数据是手填文字）';
COMMENT ON COLUMN t_lqg_cryo_batch.frozen_by_id   IS '冻存人 user_id（内部人员；CR-20261012-22）';
COMMENT ON COLUMN t_lqg_cryo_batch.frozen_by      IS '冻存人姓名（选人时的快照；历史数据是手填文字）';

CREATE INDEX IF NOT EXISTS idx_sample_operator      ON t_lqg_sample (operator_id);
CREATE INDEX IF NOT EXISTS idx_embed_embed_by       ON t_lqg_embed (embed_by_id);
CREATE INDEX IF NOT EXISTS idx_embed_operator       ON t_lqg_embed (operator_id);
CREATE INDEX IF NOT EXISTS idx_cryo_batch_frozen_by ON t_lqg_cryo_batch (frozen_by_id);

-- ── 回填：姓名恰好对上一个内部人员的才关联 ─────────────────────────────────────
DROP TABLE IF EXISTS tmp_lqg_staff_name;
CREATE TEMP TABLE tmp_lqg_staff_name AS
SELECT btrim(u.nick_name) AS name, min(u.user_id) AS user_id
  FROM sys_user u
  JOIN sys_user_role ur ON ur.user_id = u.user_id
  JOIN sys_role r       ON r.role_id  = ur.role_id
 WHERE u.del_flag = '0'
   AND r.del_flag = '0'
   AND r.role_key IN ('lqg_admin', 'lqg_internal')
   AND btrim(coalesce(u.nick_name, '')) <> ''
 GROUP BY btrim(u.nick_name)
HAVING count(DISTINCT u.user_id) = 1;

UPDATE t_lqg_sample t
   SET operator_id = s.user_id
  FROM tmp_lqg_staff_name s
 WHERE t.operator_id IS NULL
   AND btrim(t.operator_name) = s.name;

UPDATE t_lqg_embed t
   SET embed_by_id = s.user_id
  FROM tmp_lqg_staff_name s
 WHERE t.embed_by_id IS NULL
   AND btrim(t.embed_by) = s.name;

UPDATE t_lqg_embed t
   SET operator_id = s.user_id
  FROM tmp_lqg_staff_name s
 WHERE t.operator_id IS NULL
   AND btrim(t.operator_name) = s.name;

UPDATE t_lqg_cryo_batch t
   SET frozen_by_id = s.user_id
  FROM tmp_lqg_staff_name s
 WHERE t.frozen_by_id IS NULL
   AND btrim(t.frozen_by) = s.name;

DROP TABLE IF EXISTS tmp_lqg_staff_name;
