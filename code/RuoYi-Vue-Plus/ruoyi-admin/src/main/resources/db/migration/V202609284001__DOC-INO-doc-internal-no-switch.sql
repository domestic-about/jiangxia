-- V202609284001__DOC-INO-doc-internal-no-switch.sql
--
-- 甲方 2026-09-24 意见第 23 行：质控文档里的「内部编号」内部人员要能看到，最好能选择是否让外部人员看到。
-- Kevin 拍板：系统参数 lqg.ext.show-internal-no（工作台「系统管理 → 参数设置」）从此也管**外部版文档**：
--   开 → 外部版（单份与合并）「内部编号」一格印内部编号；关（默认）→ 留空。内部版一直印。
--
-- 取号：G 批 C 组号段 V2026092840xx；库里已应用的最大版本是 202609272000，4001 > 2000，
--   out-of-order=false 下安全。已有迁移一律不改。
--
-- 一、t_lqg_doc_file 加一列 show_internal_no（flag：Y / N）：
--   这一版产物的「内部编号」一格是不是按「印出」渲染的（只在 header 行 docx / page_no=0 上有意义）。
--   ★ 为什么要落库、不能只靠内容指纹：指纹只能回答「此刻该出的那一版是不是它」，回答不了
--     「这一份旧产物上印没印内部编号」—— 模板升级之后旧产物的指纹谁都对不上，而开关关着时
--     外部任何路径都不许拿到印了内部编号的那一份。于是下载 / 预览 / 清单三处在发出去之前
--     都按这一列与开关此刻的值核一遍（DocRenderService#deliverable）：Y 且开关关着 → 不给、后台按新设置重出。
--   ★ 需要同步改 doc/authority/field-ssot.yaml（ddl_vs_ssot.py 列集合精确比对），见 C 组 DONE.md。
--
-- 二、历史行回填：外部版此前一律留空 → 保持默认 N；内部版的样本质控表与合并件一直印着内部编号 → Y
--   （内部版这一列只是如实记账，放行判断只看外部版）。

BEGIN;

ALTER TABLE t_lqg_doc_file ADD COLUMN IF NOT EXISTS show_internal_no CHAR(1) NOT NULL DEFAULT 'N';
COMMENT ON COLUMN t_lqg_doc_file.show_internal_no IS '这一版「内部编号」一格是否印出 Y / N（header 行上有意义）；外部版随系统参数 lqg.ext.show-internal-no，开关关着时印了内部编号的外部版一律不对外';

UPDATE t_lqg_doc_file
   SET show_internal_no = 'Y'
 WHERE audience = 'internal'
   AND doc_kind IN ('sample_qc', 'merged');

COMMIT;
