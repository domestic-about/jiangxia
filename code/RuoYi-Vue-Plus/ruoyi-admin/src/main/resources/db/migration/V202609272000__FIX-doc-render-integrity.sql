-- V202609272000__FIX-doc-render-integrity.sql
--
-- 独立验收（2026-09-23）V04 / V23 的修复：渲染产物的完整性与缺图记录。
--
-- 取号：修复批次统一用 2026092720NN（F2 组从 00 起）；库里已应用的最大版本是 202609261430，
--   2000 > 1430，out-of-order=false 下安全。已有迁移一律不改。
--
-- 一、t_lqg_doc_file 加两列（#217 口径：内部版照出但要记下缺图，外部版缺图按渲染失败处理）：
--   missing_image_count  这一版渲染时取不到字节的图片张数（只在 header 行 docx / page_no=0 上有意义）
--   missing_images       缺了哪几张（给人看的一句话；工作台质控页与首页「渲染失败与缺图」清单显示它）
--   ★ 需要同步改 doc/authority/field-ssot.yaml（ddl_vs_ssot.py 列集合精确比对），见修复报告。
--
-- 二、修历史「撕裂」的合并件（V04）：旧版 invalidateMerged 的 markStale 只把新指纹写进 header 行、
--   状态仍是 done，PDF 行与页面图还停在旧指纹 —— 下载 Word 给的是含已撤回文档的旧产物、
--   PDF 下载 400、预览「已完成却 0 页」、render 永远命中缓存。
--   这里把「header 是 done、但同指纹的 PDF 行或页面图不全」的行一律置回 pending（产物不再下发），
--   下一次预览 / 下载会触发重新渲染（后端 DocRenderService#requestRender）。
--   旧产物的 OSS 对象与行都不删（与既有「旧产物保留」口径一致）。

BEGIN;

ALTER TABLE t_lqg_doc_file ADD COLUMN IF NOT EXISTS missing_image_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_lqg_doc_file ADD COLUMN IF NOT EXISTS missing_images VARCHAR(500);
COMMENT ON COLUMN t_lqg_doc_file.missing_image_count IS '这一版渲染时取不到字节的图片张数（header 行上有意义）；内部版照出并记缺图，外部版缺图即 failed（#217）';
COMMENT ON COLUMN t_lqg_doc_file.missing_images IS '缺了哪几张（给人看的一句话，工作台质控页与首页清单显示）';

UPDATE t_lqg_doc_file h
   SET render_status = 'pending',
       error_msg     = NULL,
       update_time   = now()
 WHERE h.del_flag = '0'
   AND h.file_format = 'docx'
   AND h.page_no = 0
   AND h.render_status = 'done'
   AND (
        NOT EXISTS (SELECT 1 FROM t_lqg_doc_file p
                     WHERE p.del_flag = '0' AND p.sample_id = h.sample_id AND p.doc_kind = h.doc_kind
                       AND p.audience = h.audience AND p.file_format = 'pdf' AND p.page_no = 0
                       AND p.render_status = 'done' AND p.oss_id IS NOT NULL
                       AND p.content_hash = h.content_hash)
     OR NOT EXISTS (SELECT 1 FROM t_lqg_doc_file g
                     WHERE g.del_flag = '0' AND g.sample_id = h.sample_id AND g.doc_kind = h.doc_kind
                       AND g.audience = h.audience AND g.file_format = 'png' AND g.page_no >= 1
                       AND g.render_status = 'done' AND g.content_hash = h.content_hash)
   );

COMMIT;
