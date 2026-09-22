-- V202609261300__QC-MODEL-001-qc-docs.sql
--
-- QC-MODEL-001 · 三份质控文档的数据模型（样本质控表 / 类器官质控表 / 类器官质量评分表）
--   + 内部读写接口的三个权限串。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D6 = 20260926（本票 phase D6）；HHmm 按域分段，QC = 13xx。
--   field-ssot.yaml 里三张表的 migration 字段都写着 V202609261300__QC-MODEL-001-qc-docs.sql
--   → 取 1300。版本号 202609261300 大于当前已应用的最大值 202609241210
--   → out-of-order=false 下不会触发 FlywayValidateException。
--   ★ 本票第二支 V202609261310__QC-MODEL-001-doc-assets.sql（图片 / 附件）取在 1310。
--
-- ★★ accept 1 第 2 段断的是 `script LIKE 'V2026092613%__QC-MODEL-001-%'` 恰 2 行
--    —— 三张文档表 + 菜单必须在第一支、图片 / 附件在第二支，**不许再加第三支**。
--
-- ★★ 三张表上**没有、也不许加**来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 /
--    内部编号这七项（ticket §0 口径复述 1）：它们从样本主档 t_lqg_sample 带出（只读）。
--    在质控表里再存一份 = 两个真相源，样本主档改了名字文档上还是旧的。
--    ddl_vs_ssot 会把多出来的列报红。
--
-- ★★ 类器官质控表的 formed_time / feedback_time 是 **VARCHAR(100) 文本列，不是 DATE**
--    （ticket §0 口径复述 3）：实验员要写「约第 5 天」。accept 1 第 3 段就是断
--    information_schema 里这两列 data_type = 'character varying'。
--
-- ★★ 四张文档表各自的 sample_id 上是**部分唯一索引** `WHERE del_flag='0'`
--    （uk_qc_sample_sample / uk_qc_organoid_sample / uk_qc_score_sample）：
--    一个样本每种文档至多一份，并发首次打开时兜底（ddl_vs_ssot 会逐条断这个 WHERE）。
--
-- 内容两段：
--   1) 第一段 = `python3 doc/tools/gen_ddl_pg.py --migration V202609261300__QC-MODEL-001-qc-docs.sql`
--      的逐字节输出（改字段改 SSOT，别手改本段）。
--   2) 第二段（手写，非生成器输出）：菜单 5501-5503 三个 F 按钮权限串，
--      授给 101（lqg_admin）与 102（lqg_internal）。
--
-- ★ 为什么只建 F 按钮、不建 C 页面菜单：页面在 QC-WEB-001 / 002
--   （lint-profile.yaml 给 QC/DOC 域 5500-5599，页面菜单留给 5510）。
--   @SaCheckPermission 的权限集合来自「角色 → perms」，与 menu_type 无关，F 行足够。
--
-- ★ 为什么一并落 lqg:qc:publish（本票不实现 publish / unpublish —— 那是 DOC-PUBLISH-001）：
--   权限串是本域的地基，一次落齐（CRYO-MODEL-001 先例），免得后继票再补一支迁移。
--
-- ★ 只动表结构与菜单授权，不动任何业务数据，不动任何别的 t_lqg_* 表。

BEGIN;

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_qc_sample：样本质控表；来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 / 内部编号 从样本主档带出，不在本表重复存
CREATE TABLE t_lqg_qc_sample (
    id                      BIGINT NOT NULL,
    sample_id               BIGINT NOT NULL,
    patient_no              VARCHAR(255),
    sampling_site           VARCHAR(100),
    sampling_method         VARCHAR(100),
    clinical_diagnosis      TEXT,
    receive_desc            VARCHAR(500),
    viability_oss_id        BIGINT,
    viability_file_name     VARCHAR(200),
    orig_desc               TEXT,
    observe_desc            TEXT,
    pretreat_desc           TEXT,
    doc_status              VARCHAR(16) NOT NULL DEFAULT 'draft',
    published_time          TIMESTAMP(0),
    published_by            BIGINT,
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_qc_sample PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_qc_sample IS '样本质控表；来源单位 / 患者姓名 / 性别 / 收样时间 / 处理时间 / 操作人 / 内部编号 从样本主档带出，不在本表重复存';
COMMENT ON COLUMN t_lqg_qc_sample.id IS '主键';
COMMENT ON COLUMN t_lqg_qc_sample.sample_id IS 'FK→t_lqg_sample.id，一个样本一份';
COMMENT ON COLUMN t_lqg_qc_sample.patient_no IS '患者编号，@EncryptField 加密落库（ADR-0006）';
COMMENT ON COLUMN t_lqg_qc_sample.sampling_site IS '取样部位';
COMMENT ON COLUMN t_lqg_qc_sample.sampling_method IS '取样方式';
COMMENT ON COLUMN t_lqg_qc_sample.clinical_diagnosis IS '临床诊断/既往治疗';
COMMENT ON COLUMN t_lqg_qc_sample.receive_desc IS '收样描述（新建时默认填模板原文：样本按质控要求，保持2-8℃低温环境运输至实验室。）';
COMMENT ON COLUMN t_lqg_qc_sample.viability_oss_id IS '细胞活率测定附件 FK→sys_oss.oss_id（文档里这一格印文件名，不做 OLE 嵌入）';
COMMENT ON COLUMN t_lqg_qc_sample.viability_file_name IS '细胞活率测定附件的原始文件名';
COMMENT ON COLUMN t_lqg_qc_sample.orig_desc IS '收样原始情况 · 情况描述';
COMMENT ON COLUMN t_lqg_qc_sample.observe_desc IS '样本观察情况 · 情况描述（默认模板原文：样本外观呈黄白色。）';
COMMENT ON COLUMN t_lqg_qc_sample.pretreat_desc IS '样本预处理情况 · 情况描述（默认模板原文）';
COMMENT ON COLUMN t_lqg_qc_sample.doc_status IS '★ draft 草稿 / published 已完成；只有 published 对外可见；published 后再保存内容 → 回到 draft';
COMMENT ON COLUMN t_lqg_qc_sample.published_time IS '完成时间（文档列表按它倒序）';
COMMENT ON COLUMN t_lqg_qc_sample.published_by IS '完成人 user_id';
COMMENT ON COLUMN t_lqg_qc_sample.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_qc_sample.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_qc_sample.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_qc_sample.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_qc_sample.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_qc_sample.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_qc_sample_sample ON t_lqg_qc_sample (sample_id) WHERE del_flag = '0';

-- ── t_lqg_qc_organoid：类器官质控表（六栏；除图片位外都是自由文本，模板没给选项就不擅自做成下拉）
CREATE TABLE t_lqg_qc_organoid (
    id                      BIGINT NOT NULL,
    sample_id               BIGINT NOT NULL,
    formed_time             VARCHAR(100),
    growth_state            VARCHAR(200),
    growth_desc             TEXT,
    planned_drug_screen     TEXT,
    feedback_time           VARCHAR(100),
    doc_status              VARCHAR(16) NOT NULL DEFAULT 'draft',
    published_time          TIMESTAMP(0),
    published_by            BIGINT,
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_qc_organoid PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_qc_organoid IS '类器官质控表（六栏；除图片位外都是自由文本，模板没给选项就不擅自做成下拉）';
COMMENT ON COLUMN t_lqg_qc_organoid.id IS '主键';
COMMENT ON COLUMN t_lqg_qc_organoid.sample_id IS 'FK→t_lqg_sample.id，一个样本一份';
COMMENT ON COLUMN t_lqg_qc_organoid.formed_time IS '形成类器官时间（文本：日期选择器选了就是 yyyy-MM-dd，也允许手改成「约第 5 天」）';
COMMENT ON COLUMN t_lqg_qc_organoid.growth_state IS '生长状态';
COMMENT ON COLUMN t_lqg_qc_organoid.growth_desc IS '类器官生长情况';
COMMENT ON COLUMN t_lqg_qc_organoid.planned_drug_screen IS '预计筛药（纯文本；不延伸成药敏模块，REQ-SYS-013 deferred）';
COMMENT ON COLUMN t_lqg_qc_organoid.feedback_time IS '反馈时间（文本，同 formed_time）';
COMMENT ON COLUMN t_lqg_qc_organoid.doc_status IS 'draft / published，规则同 t_lqg_qc_sample.doc_status';
COMMENT ON COLUMN t_lqg_qc_organoid.published_time IS '完成时间';
COMMENT ON COLUMN t_lqg_qc_organoid.published_by IS '完成人 user_id';
COMMENT ON COLUMN t_lqg_qc_organoid.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_qc_organoid.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_qc_organoid.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_qc_organoid.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_qc_organoid.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_qc_organoid.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_qc_organoid_sample ON t_lqg_qc_organoid (sample_id) WHERE del_flag = '0';

-- ── t_lqg_qc_score：类器官质量评分表：四个变量各选一档 → 分值取字典 remark 并快照落库 → 合计
CREATE TABLE t_lqg_qc_score (
    id                      BIGINT NOT NULL,
    sample_id               BIGINT NOT NULL,
    pre_culture_level       VARCHAR(16),
    culture_days_level      VARCHAR(16),
    organoid_count_level    VARCHAR(16),
    diameter_level          VARCHAR(16),
    pre_culture_score       INTEGER,
    culture_days_score      INTEGER,
    organoid_count_score    INTEGER,
    diameter_score          INTEGER,
    total_score             INTEGER,
    doc_status              VARCHAR(16) NOT NULL DEFAULT 'draft',
    published_time          TIMESTAMP(0),
    published_by            BIGINT,
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_qc_score PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_qc_score IS '类器官质量评分表：四个变量各选一档 → 分值取字典 remark 并快照落库 → 合计';
COMMENT ON COLUMN t_lqg_qc_score.id IS '主键';
COMMENT ON COLUMN t_lqg_qc_score.sample_id IS 'FK→t_lqg_sample.id，一个样本一份';
COMMENT ON COLUMN t_lqg_qc_score.pre_culture_level IS '培养前样本评分档位 lt40(8) / 40to80(16) / gt80(20)';
COMMENT ON COLUMN t_lqg_qc_score.culture_days_level IS '培养天数档位 gt14(0) / le14(10)';
COMMENT ON COLUMN t_lqg_qc_score.organoid_count_level IS '类器官数量档位 lt100(0) / 100to1500(10) / 1500to4000(25) / gt4000(40)';
COMMENT ON COLUMN t_lqg_qc_score.diameter_level IS '类器官直径档位 lt30(10) / 30to100(20) / gt100(30)';
COMMENT ON COLUMN t_lqg_qc_score.pre_culture_score IS '该档分值快照（后端按字典 remark 回填，前端传来的分值一律忽略）';
COMMENT ON COLUMN t_lqg_qc_score.culture_days_score IS '该档分值快照';
COMMENT ON COLUMN t_lqg_qc_score.organoid_count_score IS '该档分值快照';
COMMENT ON COLUMN t_lqg_qc_score.diameter_score IS '该档分值快照';
COMMENT ON COLUMN t_lqg_qc_score.total_score IS '★ 合计 = 四项分值之和（任一项未选则为空）；不出「偏差 / 中等 / 良好」结论，那是文档页脚的固定注释';
COMMENT ON COLUMN t_lqg_qc_score.doc_status IS 'draft / published，规则同 t_lqg_qc_sample.doc_status';
COMMENT ON COLUMN t_lqg_qc_score.published_time IS '完成时间';
COMMENT ON COLUMN t_lqg_qc_score.published_by IS '完成人 user_id';
COMMENT ON COLUMN t_lqg_qc_score.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_qc_score.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_qc_score.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_qc_score.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_qc_score.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_qc_score.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_qc_score_sample ON t_lqg_qc_score (sample_id) WHERE del_flag = '0';

-- ══════════════════════════════════════════════════════════════════════════
-- 第二段：菜单与按钮权限（手写；第一段是生成器逐字节输出，别混）
-- 号段依据 doc/lint-profile.yaml：QC/DOC 菜单 5500-5599；本票取 5501-5503，
-- 让开 QC-WEB-001 / 002 将要落的 5510（C 页面菜单）。
-- ══════════════════════════════════════════════════════════════════════════
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5501, '质控查询', 0, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:qc:query',   '#', 103, 1, now(), '三份文档 + 图片 + 附件（GET /lqg/qc/{sampleId}）——页面菜单由 QC-WEB-001 落 5510'),
    (5502, '质控编辑', 0, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:qc:edit',    '#', 103, 1, now(), '保存草稿 / 图片位 / 附件（PUT / POST / DELETE /lqg/qc/{sampleId}/**）'),
    (5503, '质控发布', 0, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:qc:publish', '#', 103, 1, now(), '完成并同步 / 撤回（端点在 DOC-PUBLISH-001）')
ON CONFLICT (menu_id) DO NOTHING;

-- 授权：101（lqg_admin）与 102（lqg_internal）都能调 /lqg/qc/**
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5501 AND 5503) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
