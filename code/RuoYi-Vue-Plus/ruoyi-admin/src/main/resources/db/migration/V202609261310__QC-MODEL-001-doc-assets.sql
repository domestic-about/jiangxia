-- V202609261310__QC-MODEL-001-doc-assets.sql
--
-- QC-MODEL-001 · 质控文档的图片位与通用附件两张表（t_lqg_doc_image / t_lqg_doc_attachment）。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D6 = 20260926（本票 phase D6）；HHmm 按域分段，QC = 13xx。
--   field-ssot.yaml 里两张表的 migration 字段都写着 V202609261310__QC-MODEL-001-doc-assets.sql
--   → 取 1310（本票第一支 1300 之后的下一个分钟）。大于库里已应用的最大值
--   202609241210 → out-of-order=false 下不会触发 FlywayValidateException。
--
-- ★★ doc_type 的取值来自字典 lqg_doc_type：**图片**只有 sample_qc / organoid_qc
--    （评分表没有图片位）；**附件**多一个 organoid_score。规则在
--    QcDocRules（Java）里，不在这张表上（表上只有一个字典列）。
--
-- ★★ preview_oss_id 与 oss_id 分开存（FIELD:t_lqg_doc_image.preview_oss_id）：
--    非 jpg / png（TIFF、BMP…）或长边 > 2000px 的图，浏览器打不开 / 太大，
--    另存一份长边 ≤ 2000px 的 JPEG，进 Word 的也是它；其余情况 preview_oss_id = oss_id。
--
-- 内容 = `python3 doc/tools/gen_ddl_pg.py --migration V202609261310__QC-MODEL-001-doc-assets.sql`
-- 的逐字节输出（改字段改 SSOT，别手改本段）。本支不含菜单。

BEGIN;

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_doc_image：质控文档的图片位（每位 1-3 张）；原图与预览图分开存
CREATE TABLE t_lqg_doc_image (
    id                      BIGINT NOT NULL,
    doc_type                VARCHAR(16) NOT NULL,
    doc_id                  BIGINT NOT NULL,
    slot                    VARCHAR(24) NOT NULL,
    oss_id                  BIGINT NOT NULL,
    preview_oss_id          BIGINT,
    sort                    INTEGER NOT NULL DEFAULT 0,
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_doc_image PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_doc_image IS '质控文档的图片位（每位 1-3 张）；原图与预览图分开存';
COMMENT ON COLUMN t_lqg_doc_image.id IS '主键';
COMMENT ON COLUMN t_lqg_doc_image.doc_type IS 'sample_qc / organoid_qc（评分表没有图片位）';
COMMENT ON COLUMN t_lqg_doc_image.doc_id IS '对应质控文档表的主键';
COMMENT ON COLUMN t_lqg_doc_image.slot IS 'orig / observe / pretreat（样本质控表）｜organoid_observe（类器官质控表）';
COMMENT ON COLUMN t_lqg_doc_image.oss_id IS '原图 FK→sys_oss.oss_id（预览页点开看的就是它）';
COMMENT ON COLUMN t_lqg_doc_image.preview_oss_id IS '预览图 FK→sys_oss.oss_id（长边 ≤ 2000px 的 JPEG；TIFF 等浏览器打不开的格式靠它显示；进 Word 的也是它）';
COMMENT ON COLUMN t_lqg_doc_image.sort IS '同一图片位内的顺序';
COMMENT ON COLUMN t_lqg_doc_image.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_doc_image.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_doc_image.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_doc_image.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_doc_image.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_doc_image.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE INDEX idx_doc_image_doc ON t_lqg_doc_image (doc_type, doc_id, slot);

-- ── t_lqg_doc_attachment：质控文档的通用附件（不进 Word 正文，在预览页下方列出）
CREATE TABLE t_lqg_doc_attachment (
    id                      BIGINT NOT NULL,
    doc_type                VARCHAR(16) NOT NULL,
    doc_id                  BIGINT NOT NULL,
    oss_id                  BIGINT NOT NULL,
    file_name               VARCHAR(200) NOT NULL,
    file_size               INTEGER NOT NULL,
    sort                    INTEGER NOT NULL DEFAULT 0,
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_doc_attachment PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_doc_attachment IS '质控文档的通用附件（不进 Word 正文，在预览页下方列出）';
COMMENT ON COLUMN t_lqg_doc_attachment.id IS '主键';
COMMENT ON COLUMN t_lqg_doc_attachment.doc_type IS 'sample_qc / organoid_qc / organoid_score';
COMMENT ON COLUMN t_lqg_doc_attachment.doc_id IS '对应质控文档表的主键';
COMMENT ON COLUMN t_lqg_doc_attachment.oss_id IS 'FK→sys_oss.oss_id';
COMMENT ON COLUMN t_lqg_doc_attachment.file_name IS '原始文件名';
COMMENT ON COLUMN t_lqg_doc_attachment.file_size IS '字节数（单个 ≤ 50MB）';
COMMENT ON COLUMN t_lqg_doc_attachment.sort IS '显示顺序';
COMMENT ON COLUMN t_lqg_doc_attachment.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_doc_attachment.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_doc_attachment.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_doc_attachment.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_doc_attachment.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_doc_attachment.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE INDEX idx_doc_attachment_doc ON t_lqg_doc_attachment (doc_type, doc_id);

COMMIT;
