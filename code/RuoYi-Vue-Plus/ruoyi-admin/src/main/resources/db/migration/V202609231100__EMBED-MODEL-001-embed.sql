-- V202609231100__EMBED-MODEL-001-embed.sql
--
-- EMBED-MODEL-001 · 石蜡包埋送样记录：t_lqg_embed + t_lqg_embed_marker + 「石蜡包埋」菜单权限。
--
-- 取号依据 doc/lint-profile.yaml：
--   * 日期段 D3 = 20260923（本票 phase D3）；HHmm 按域分段，EMBED = 11xx；
--   * 1100 = SSOT 里两张表登记的 migration（FIELD-ssot 的 t_lqg_embed.migration / t_lqg_embed_marker.migration
--     都是 V202609231100__EMBED-MODEL-001-embed.sql），且大于已应用的最大迁移 V202609221010
--     —— out-of-order=false 下不会触发 FlywayValidateException（SYS-WEB-001 踩过跨域补号的坑）。
--   * ★ 两张表 + 菜单**同一支迁移**：accept 1 第 2 段断的是
--     `SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260923110%__EMBED-MODEL-001-%'`
--     恰 1 行 —— 拆成两支（100 + 101）会让这一格变成 2 而红。
--   * 菜单号依据同文件：EMBED 域 5300-5399。本票取 **5300（C）+ 5301-5307（F）**，
--     **避开** EMBED-WEB-001 票面点名的 5310-5317（那是它的号，别抢）。
--
-- 第一段 = doc/tools/gen_ddl_pg.py --migration V202609231100__EMBED-MODEL-001-embed.sql 的**逐字节输出**
--   （t_lqg_embed 22 业务列 + 公共 6 字段、部分唯一索引 uk_embed_block_no WHERE del_flag='0'、
--    普通索引 3 个；t_lqg_embed_marker 4 业务列 + 公共 6 字段、普通索引 1 个；无 tenant_id / del_unique）。
--
-- ★ 七个工序时间（tissue_receive_time / tissue_process_time / agarose_embed_time / dehydrate_time /
--   agarose_send_time / paraffin_embed_time / section_time）**全部可空**：这张表会被反复打开补填，
--   建块当天只有石蜡块编号（FLOW:F-EMBED-01.step2）。任何一个建成 NOT NULL → accept 1 红。
-- ★ paraffin_block_no **可空 + 部分唯一索引**（ADR-0009）：外部送样在核验前没有编号，落不了库就没法核验；
--   唯一性只约束 del_flag='0' 的行 → 软删后可重用同一个编号。
-- ★ marker **单独一张表**（一个蜡块可测多个 marker）；主表上**没有** marker_name / expression 两列。
-- ★ 模板的「样本编号」= 样本主档的 internal_no，**读时带出、不落库**：本表只有 sample_id（accept 1 的
--   counterfeit 点名「建成一列 sample_no 存字符串」会红）。

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_embed：石蜡包埋送样记录（一个样本可有多个石蜡块；工序时间陆续补填，全部可空；外部也可提交送样，实验室核验有效后给石蜡块编号——CR-20260917-05）
CREATE TABLE t_lqg_embed (
    id                      BIGINT NOT NULL,
    sample_id               BIGINT NOT NULL,
    submit_source           VARCHAR(16) NOT NULL,
    submitter_id            BIGINT NOT NULL,
    verify_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    verify_by               BIGINT,
    verify_time             TIMESTAMP(0),
    invalid_reason          VARCHAR(200),
    paraffin_block_no       VARCHAR(64),
    sample_type             VARCHAR(50),
    organoid_source_type    VARCHAR(100),
    tissue_receive_time     DATE,
    tissue_process_time     DATE,
    agarose_embed_time      DATE,
    embed_by                VARCHAR(50),
    dehydrate_time          DATE,
    agarose_send_time       DATE,
    paraffin_embed_time     DATE,
    section_time            DATE,
    stain_types             VARCHAR(64),
    stain_other             VARCHAR(100),
    operator_name           VARCHAR(50),
    remark                  VARCHAR(500),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_embed PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_embed IS '石蜡包埋送样记录（一个样本可有多个石蜡块；工序时间陆续补填，全部可空；外部也可提交送样，实验室核验有效后给石蜡块编号——CR-20260917-05）';
COMMENT ON COLUMN t_lqg_embed.id IS '主键';
COMMENT ON COLUMN t_lqg_embed.sample_id IS 'FK→t_lqg_sample.id；模板的「样本编号」= 该样本的内部编号，读时带出不落库';
COMMENT ON COLUMN t_lqg_embed.submit_source IS '★ internal / external：提交当时按提交人身份落库（同样本主档）';
COMMENT ON COLUMN t_lqg_embed.submitter_id IS '提交人 user_id（外部只能挂自己送检过的样本、只能改自己提交的）';
COMMENT ON COLUMN t_lqg_embed.verify_status IS '★ pending / valid / invalid；内部录入直接 valid；外部提交先 pending，判有效时必须给石蜡块编号且所挂样本已有效';
COMMENT ON COLUMN t_lqg_embed.verify_by IS '核验人 user_id（不对外）';
COMMENT ON COLUMN t_lqg_embed.verify_time IS '核验时间';
COMMENT ON COLUMN t_lqg_embed.invalid_reason IS '判无效的原因（外部可见）';
COMMENT ON COLUMN t_lqg_embed.paraffin_block_no IS '★ 石蜡块编号（如 E15-1-2026.07.29）：实验室手填、全库唯一（部分唯一索引不约束空值）；外部提交的送样在判有效前为空；对外展示包埋情况时用它，不用内部编号';
COMMENT ON COLUMN t_lqg_embed.sample_type IS '样本类型（自由文本，联想词来自字典 lqg_hint_sample_type）';
COMMENT ON COLUMN t_lqg_embed.organoid_source_type IS '类器官来源类型';
COMMENT ON COLUMN t_lqg_embed.tissue_receive_time IS '组织收样时间（默认带样本的收样日期，可改）';
COMMENT ON COLUMN t_lqg_embed.tissue_process_time IS '组织处理时间（默认带样本处理时间的日期部分，可改）';
COMMENT ON COLUMN t_lqg_embed.agarose_embed_time IS '琼脂糖包埋样本时间';
COMMENT ON COLUMN t_lqg_embed.embed_by IS '包埋人';
COMMENT ON COLUMN t_lqg_embed.dehydrate_time IS '脱水时间';
COMMENT ON COLUMN t_lqg_embed.agarose_send_time IS '琼脂糖包埋样本送样时间';
COMMENT ON COLUMN t_lqg_embed.paraffin_embed_time IS '石蜡包埋时间';
COMMENT ON COLUMN t_lqg_embed.section_time IS '切片时间（非空 = 已切片，总表的切片染色提示读它）';
COMMENT ON COLUMN t_lqg_embed.stain_types IS '★ 染色，多选，逗号分隔的 lqg_stain_type 值（HE,IF,IHC,OTHER / NONE）；NONE 与其余互斥；空 = 还没选';
COMMENT ON COLUMN t_lqg_embed.stain_other IS '选了 OTHER 时写具体染色名';
COMMENT ON COLUMN t_lqg_embed.operator_name IS '操作人';
COMMENT ON COLUMN t_lqg_embed.remark IS '备注';
COMMENT ON COLUMN t_lqg_embed.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_embed.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_embed.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_embed.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_embed.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_embed.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_embed_block_no ON t_lqg_embed (paraffin_block_no) WHERE del_flag = '0';
CREATE INDEX idx_embed_sample ON t_lqg_embed (sample_id);
CREATE INDEX idx_embed_submitter ON t_lqg_embed (submitter_id);
CREATE INDEX idx_embed_status ON t_lqg_embed (verify_status);

-- ── t_lqg_embed_marker：石蜡块的 marker 表达（一个蜡块可测多个 marker；导出时拼成「Ki67：强表达；CK19：阴性」一格）
CREATE TABLE t_lqg_embed_marker (
    id                      BIGINT NOT NULL,
    embed_id                BIGINT NOT NULL,
    marker_name             VARCHAR(50),
    expression              VARCHAR(16) NOT NULL,
    sort                    INTEGER NOT NULL DEFAULT 0,
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_embed_marker PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_embed_marker IS '石蜡块的 marker 表达（一个蜡块可测多个 marker；导出时拼成「Ki67：强表达；CK19：阴性」一格）';
COMMENT ON COLUMN t_lqg_embed_marker.id IS '主键';
COMMENT ON COLUMN t_lqg_embed_marker.embed_id IS 'FK→t_lqg_embed.id';
COMMENT ON COLUMN t_lqg_embed_marker.marker_name IS 'marker 名称（可空：只记表达情况不写名称也允许）';
COMMENT ON COLUMN t_lqg_embed_marker.expression IS 'negative 阴性 / weak 弱表达 / strong 强表达（按钮单选）';
COMMENT ON COLUMN t_lqg_embed_marker.sort IS '显示顺序';
COMMENT ON COLUMN t_lqg_embed_marker.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_embed_marker.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_embed_marker.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_embed_marker.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_embed_marker.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_embed_marker.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE INDEX idx_embed_marker_embed ON t_lqg_embed_marker (embed_id);


-- ══════════════════════════════════════════════════════════════════════════════
-- 第二段（手写，非生成器输出）：「石蜡包埋」菜单与 7 个按钮权限
--
-- ★ 为什么本票必须建菜单（SAMPLE-VERIFY-001 踩过的同一坑）：@SaCheckPermission 的权限集合来自
--   「角色 → sys_menu.perms」；没有这几行，`--as staff` 调 /lqg/embed/** 恒 403（不是 500），
--   本票 accept 2/3/4 全红。EMBED-WEB-001 的票面给它的页面留了 5310（C）+ 5311-5317（F），
--   本票只落 5300 + 5301-5307 —— **EMBED-WEB-001 落页面时按 SAMPLE-WEB-001 的先例把这一段
--   整体搬到 5310 段**（它那条 accept 同时要 `menu_id=5310` 与 getRouters 里
--   component='lqg/embed/index' 恰 1 条，两条都要求搬），见本票完工报告的交接说明。
-- ★ 页面（plus-ui 的 lqg/embed/index.vue）**本票不产出**（ticket §3 边界，EMBED-WEB-001 的活）；
--   这一行 C 菜单只为让权限串存在。导出（lqg:embed:export）也只有权限行、没有端点（导出归 EMBED-WEB-001）。
-- ★ 只动菜单与授权，不建表、不动任何业务数据、不动任何 t_lqg_* 表。
BEGIN;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5300, '石蜡包埋', 0, 5, 'embed', 'lqg/embed/index', '',
        '1', '0', 'C', '0', '0', 'lqg:embed:list', 'table',
        103, 1, now(), 'EMBED-MODEL-001：石蜡包埋送样记录（页面由 EMBED-WEB-001 落，届时整体搬到 5310 段）')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5301, '包埋查询', 5300, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:list',   '#', 103, 1, now(), '列表（GET /lqg/embed/list）'),
    (5302, '包埋详情', 5300, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:query',  '#', 103, 1, now(), '详情（GET /lqg/embed/{id}）'),
    (5303, '包埋新增', 5300, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:add',    '#', 103, 1, now(), '内部录入（POST /lqg/embed，石蜡块编号必填、直接 valid）'),
    (5304, '包埋修改', 5300, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:edit',   '#', 103, 1, now(), '修改 / 补填（PUT /lqg/embed，待核验 / 无效的直接 400）'),
    (5305, '包埋删除', 5300, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:remove', '#', 103, 1, now(), '软删（DELETE /lqg/embed/{ids}）'),
    (5306, '包埋导出', 5300, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:export', '#', 103, 1, now(), '按筛选导出 xlsx（端点归 EMBED-WEB-001，本票只留权限串）'),
    (5307, '包埋核验', 5300, 7, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:embed:verify', '#', 103, 1, now(), '核验 / 改判（PUT /lqg/embed/{id}/verify）')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5300 AND 5307) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
