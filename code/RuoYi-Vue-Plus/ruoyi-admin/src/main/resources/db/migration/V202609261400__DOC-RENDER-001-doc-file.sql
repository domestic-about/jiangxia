-- V202609261400__DOC-RENDER-001-doc-file.sql
--
-- DOC-RENDER-001 · 渲染产物缓存表（t_lqg_doc_file）。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D6 = 20260926（本票 phase D6）；HHmm 按域分段，QC/DOC = 13xx–14xx。
--   field-ssot.yaml 里这张表的 migration 字段写的就是
--   V202609261400__DOC-RENDER-001-doc-file.sql → 逐字照抄。
--   库里已应用的最大版本是 202609261310（QC-MODEL-001），1400 > 1310；
--   Flyway 是 out-of-order=false，所以号码必须**单调递增**，这一支不会触发
--   FlywayValidateException。
--
-- ★★ 一行 = 一份产物，键 =（sample_id, doc_kind, audience, file_format, page_no）
--    的部分唯一索引 uk_doc_file WHERE del_flag='0'：
--      · audience 在键里 → 内部版与外部版各一行、互不覆盖（accept 2 断「行数恰好 2」）；
--      · file_format + page_no 在键里 → 本票的 docx（page_no=0）与 DOC-PDF-001 的
--        pdf / 每页 png 共表不抢行。
--
-- ★ 内容 = `python3 doc/tools/gen_ddl_pg.py --migration V202609261400__DOC-RENDER-001-doc-file.sql`
--   的逐字节输出（改字段改 SSOT，别手改本段）。字典 lqg_doc_kind / lqg_doc_audience /
--   lqg_file_format / lqg_render_status 在 V202609210810 的基线里已经有了，本支不重复落。
--
-- 菜单 / 权限在下一支 V202609261410。

BEGIN;

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_doc_file：渲染产物缓存（Word / PDF / 每页 PNG；内部版与外部版各一套；内容或模板变了按指纹失效重出，ADR-0005）
CREATE TABLE t_lqg_doc_file (
    id                      BIGINT NOT NULL,
    sample_id               BIGINT NOT NULL,
    doc_kind                VARCHAR(16) NOT NULL,
    audience                VARCHAR(16) NOT NULL,
    file_format             VARCHAR(8) NOT NULL,
    page_no                 INTEGER NOT NULL DEFAULT 0,
    oss_id                  BIGINT,
    content_hash            VARCHAR(64) NOT NULL,
    template_version        VARCHAR(20) NOT NULL,
    render_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    error_msg               VARCHAR(500),
    rendered_time           TIMESTAMP(0),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_doc_file PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_doc_file IS '渲染产物缓存（Word / PDF / 每页 PNG；内部版与外部版各一套；内容或模板变了按指纹失效重出，ADR-0005）';
COMMENT ON COLUMN t_lqg_doc_file.id IS '主键';
COMMENT ON COLUMN t_lqg_doc_file.sample_id IS 'FK→t_lqg_sample.id';
COMMENT ON COLUMN t_lqg_doc_file.doc_kind IS 'sample_qc / organoid_qc / organoid_score / merged';
COMMENT ON COLUMN t_lqg_doc_file.audience IS '★ internal 内部版（含内部编号）/ external 外部版（内部编号按开关留空）；外部接口只许取 external';
COMMENT ON COLUMN t_lqg_doc_file.file_format IS 'docx / pdf / png';
COMMENT ON COLUMN t_lqg_doc_file.page_no IS 'png 的页码（从 1 起）；docx / pdf 恒为 0';
COMMENT ON COLUMN t_lqg_doc_file.oss_id IS '产物 FK→sys_oss.oss_id（私有桶；对外只发短时签名链接）';
COMMENT ON COLUMN t_lqg_doc_file.content_hash IS '★ 内容指纹 = sha256(文档各字段 + 图片 oss_id 列表 + 模板版本 + audience)；与当前算出的不一致 = 缓存过期，必须重出';
COMMENT ON COLUMN t_lqg_doc_file.template_version IS '渲染所用模板版本号';
COMMENT ON COLUMN t_lqg_doc_file.render_status IS 'pending / done / failed';
COMMENT ON COLUMN t_lqg_doc_file.error_msg IS '失败原因（工作台可见、可重试）';
COMMENT ON COLUMN t_lqg_doc_file.rendered_time IS '生成完成时间';
COMMENT ON COLUMN t_lqg_doc_file.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_doc_file.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_doc_file.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_doc_file.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_doc_file.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_doc_file.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_doc_file ON t_lqg_doc_file (sample_id, doc_kind, audience, file_format, page_no) WHERE del_flag = '0';
CREATE INDEX idx_doc_file_sample ON t_lqg_doc_file (sample_id, doc_kind, audience);


COMMIT;
