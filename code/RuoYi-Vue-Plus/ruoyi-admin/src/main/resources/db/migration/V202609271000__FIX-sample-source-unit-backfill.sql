-- V202609271000__FIX-sample-source-unit-backfill.sql
--
-- FIX V01（独立验收报告 2026-09-23，台账 issue #112）：历史外部样本回填来源单位 id。
--
-- ── 为什么要回填 ─────────────────────────────────────────────────────────────
-- 小程序「样本记录信息表」（组织样本）的外部填写页只发来源单位名称、不发 sourceUnitId，
-- 修复前后端 ExtSampleSubmitService 照写请求里的 id（= NULL）→ 这些样本 source_unit_id 为空：
-- 工作台「来源单位」筛选（按 source_unit_id 等值，issue #96 的口径）与按单位导出都漏掉它们，
-- 组别筛选又要先选单位，于是按组别也筛不出来。类器官路径带 id，不受影响。
-- 修复后后端按「提交人绑定的单位 + 表单单位名」解析 id（SubmitSegmentRules.attributeExternalUnit），
-- 新数据不再为空；本迁移只处理修复之前已经落库的历史行。
--
-- ── 回填规则（逐条） ──────────────────────────────────────────────────────────
-- 对象：t_lqg_sample 里 submit_source = 'external'、source_unit_id IS NULL、del_flag = '0' 的行
--       （待核验 / 有效 / 无效三种状态都算；软删行不动）。
-- 匹配：source_unit_name 与「有效单位」的 unit_name 按同一口径归一化后相等 ——
--       去首尾空白、连续空白压成一个空格、转小写（与 Java 侧 UnitGroupRules.normalizeName 同口径）；
--       有效单位 = t_lqg_source_unit 里 del_flag = '0' AND unit_status = 'active' 的行。
-- 唯一：归一化后的名字在有效单位里恰好只对应一个单位（count = 1）才回填。
-- 回填：只写 source_unit_id；source_unit_name 保留当时的名称快照不改（导出看到的文字不变）。
--       不写 update_by / update_time：这是数据修复，不是有人改了样本
--       （工作台「最后修改」与小程序「历史编辑记录」不该因此变化）。
--
-- ── 匹配不上的一律不动（source_unit_id 保持为空） ────────────────────────────
--   · 外部自填的新单位名（单位表里没有，例如 seed 的「C 研究所」）；
--   · 错别字 / 简称 / 全称不一致；
--   · 只匹配到停用（disabled）、待核验（pending）或已删除的单位；
--   · 归一化后同名的有效单位不止一个（无法判断归哪个）；
--   · 内部录入的行（submit_source = 'internal'，不在本次修复范围）。
-- 这些行由实验室在工作台打开该样本、在「来源单位」下拉里选定后保存即可挂上 id。
--
-- ── 取号 ─────────────────────────────────────────────────────────────────────
-- 修复批次统一用 V2026092710NN__FIX-*（NN 从 00 起）；库里已应用的最大版本是 202609261430，
-- 202609271000 > 202609261430（日期段 0927 > 0926）→ out-of-order=false 下不会触发 FlywayValidateException。
-- 本支只改数据、不改表结构，重复执行（例如手工再跑一次）也只会命中仍为空的行，幂等。

WITH unit_key AS (
    SELECT id,
           lower(regexp_replace(regexp_replace(unit_name, '^\s+|\s+$', '', 'g'), '\s+', ' ', 'g')) AS k
      FROM t_lqg_source_unit
     WHERE del_flag = '0'
       AND unit_status = 'active'
), unique_key AS (
    SELECT k, min(id) AS unit_id
      FROM unit_key
     GROUP BY k
    HAVING count(*) = 1
)
UPDATE t_lqg_sample s
   SET source_unit_id = u.unit_id
  FROM unique_key u
 WHERE s.submit_source = 'external'
   AND s.source_unit_id IS NULL
   AND s.del_flag = '0'
   AND s.source_unit_name IS NOT NULL
   AND lower(regexp_replace(regexp_replace(s.source_unit_name, '^\s+|\s+$', '', 'g'), '\s+', ' ', 'g')) = u.k;
