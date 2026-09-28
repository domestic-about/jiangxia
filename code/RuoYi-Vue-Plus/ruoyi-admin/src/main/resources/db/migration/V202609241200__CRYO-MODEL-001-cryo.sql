-- V202609241200__CRYO-MODEL-001-cryo.sql
--
-- CRYO-MODEL-001 · 冻存批次与出入库流水两张表 + 内部增删改查接口的权限菜单。
--
-- 取号依据（doc/lint-profile.yaml）：
--   D4 = 20260924（本票 phase D4）；HHmm 按域分段，CRYO = 12xx。
--   field-ssot.yaml 里两张表的 migration 字段都写着 V202609241200__CRYO-MODEL-001-cryo.sql
--   → 取 1200。版本号大于当前已应用的最大值 202609231110 → out-of-order=false 下不会触发
--   FlywayValidateException（SYS-WEB-001 / AUTH-EXT-002 踩过跨域补号的坑）。
--   ★ CRYO-REMIND-001 的 V202609241205__CRYO-REMIND-001-config.sql、CRYO-WEB-001 的
--     V202609241210__CRYO-WEB-001-menu.sql 都取在本支之后的分钟，互不打架。
--
-- ★★ accept 1 第 2 段断的是 `script LIKE 'V20260924120%__CRYO-MODEL-001-%'` 恰 1 行
--    —— 所以**两张表 + 菜单必须在同一支迁移里**（拆成 1200 + 1201 会让这一格变成 2 而红）。
--
-- ★★ 表上**没有、也不许加**「剩余支数」这一列（accept 1 第 3 段查
--    information_schema 里 %remain% / %current% / %stock% 必须为空；ddl_vs_ssot 也会红）。
--    剩余 = init_qty + SUM(未删流水的 delta)，读时算（ADR-0010 的 rejected_values 点名的
--    「冻存剩余支数允许直接改数字」就是这个形态）。
--
-- 内容两段：
--   1) 第一段 = `python3 doc/tools/gen_ddl_pg.py --migration V202609241200__CRYO-MODEL-001-cryo.sql`
--      的**逐字节输出**（已用 Python 子串校验 GEN-BYTE-IDENTICAL）：t_lqg_cryo_batch 12 业务列 +
--      t_lqg_cryo_flow 8 业务列，各带 ADR-0009 的 6 个公共字段与普通索引。
--   2) 第二段（手写，非生成器输出）：菜单 5401-5407 七个 F 按钮权限串，授给 101（lqg_admin）
--      与 102（lqg_internal）。
--
-- ★ 为什么只建 F 按钮、不建 C 页面菜单（本票最容易抢别人号的一步）：
--   CRYO-WEB-001 的 accept 2 同时要求
--     ① SELECT menu_id||':'||path||':'||component WHERE menu_id = 5410 == '5410:cryo:lqg/cryo/index'
--     ② getRouters 里 component == 'lqg/cryo/index' 的路由**恰好 1 条**
--   若本票先把 5400 建成 C 菜单（component='lqg/cryo/index'，EMBED-MODEL-001 的先例），
--   ① 之后 ② 会数成 2 条 —— CRYO-WEB-001 就得回头搬号（EMBED-WEB-001 被 5300 逼着搬过一次）。
--   所以本票**不建 C 菜单**：只补权限串（`@SaCheckPermission` 的权限集合来自「角色 → perms」，
--   与 menu_type 无关，F 行足够），页面菜单留给 CRYO-WEB-001 的 5410。
--
-- ★ 为什么还要落 lqg:cryo:flow：CRYO-FLOW-001（D4 第二张，写流水与转液氮）自己的 `touches`
--   里**没有迁移**，它的 accept 一上来就 `--as staff POST /lqg/cryo/batch/{id}/flow` 并要 200；
--   缺 `lqg:cryo:flow` 这一行就是 403（不是 500）。本票是 CRYO 链头，把这一串一并落下。
--   CRYO-WEB-001 的 5411-5417 会再建一次（同串重复无害：sa-token 的权限是字符串集合）。
--
-- ★ 只动菜单与授权，不动任何业务数据、不动任何 t_lqg_* 表。

BEGIN;

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_cryo_batch：冻存批次（-80 冻存模板的一行）；★ 剩余支数不落库 = init_qty + 本批次流水 delta 之和（ADR-0010）
CREATE TABLE t_lqg_cryo_batch (
    id                      BIGINT NOT NULL,
    sample_id               BIGINT NOT NULL,
    cryo_name               VARCHAR(100) NOT NULL,
    passage                 VARCHAR(10) NOT NULL,
    freeze_time             DATE NOT NULL,
    init_qty                INTEGER NOT NULL,
    density                 VARCHAR(50),
    in_minus80              CHAR(1) NOT NULL,
    frozen_by               VARCHAR(50),
    to_ln2_time             DATE,
    ln2_location            VARCHAR(100),
    remark                  VARCHAR(500),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_cryo_batch PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_cryo_batch IS '冻存批次（-80 冻存模板的一行）；★ 剩余支数不落库 = init_qty + 本批次流水 delta 之和（ADR-0010）';
COMMENT ON COLUMN t_lqg_cryo_batch.id IS '主键';
COMMENT ON COLUMN t_lqg_cryo_batch.sample_id IS 'FK→t_lqg_sample.id（内部编号贯穿：冻存必须挂到一个样本上）';
COMMENT ON COLUMN t_lqg_cryo_batch.cryo_name IS '冻存样品名称（如 hli39-GZ-N-P3-EM2-2e5），手填，系统不解析；选样本后用「内部编号-」预填';
COMMENT ON COLUMN t_lqg_cryo_batch.passage IS '★ 代数，形如 P3（正则 ^P\d{1,3}$）；同一样本多条批次的代数不要求连续';
COMMENT ON COLUMN t_lqg_cryo_batch.freeze_time IS '冻存时间（超期提醒从它起算）';
COMMENT ON COLUMN t_lqg_cryo_batch.init_qty IS '冻存数量/支（初始支数，>0；可改，改后按时间逐笔算剩余不得为负，见 FLOW:F-CRYO-02.step5）';
COMMENT ON COLUMN t_lqg_cryo_batch.density IS '冻存密度（文本，如 2e5）';
COMMENT ON COLUMN t_lqg_cryo_batch.in_minus80 IS '暂存 -80 度超低温冰箱 Y 是 / N 否（按钮）；N = 直接进液氮，ln2_location 必填';
COMMENT ON COLUMN t_lqg_cryo_batch.frozen_by IS '冻存人';
COMMENT ON COLUMN t_lqg_cryo_batch.to_ln2_time IS '★ -80 转移至液氮时间；非空 = 已转液氮，不再参与超期提醒，此后取走的 from_location = ln2';
COMMENT ON COLUMN t_lqg_cryo_batch.ln2_location IS '液氮储存位置（文本；登记转液氮时必填）';
COMMENT ON COLUMN t_lqg_cryo_batch.remark IS '备注';
COMMENT ON COLUMN t_lqg_cryo_batch.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_cryo_batch.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_cryo_batch.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_cryo_batch.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_cryo_batch.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_cryo_batch.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE INDEX idx_cryo_batch_sample ON t_lqg_cryo_batch (sample_id);
CREATE INDEX idx_cryo_batch_remind ON t_lqg_cryo_batch (in_minus80, to_ln2_time, freeze_time);

-- ── t_lqg_cryo_flow：冻存出入库流水（追溯靠它；取走 / 补入登记可改可删，删为软删；改删后逐笔剩余不得为负）
CREATE TABLE t_lqg_cryo_flow (
    id                      BIGINT NOT NULL,
    batch_id                BIGINT NOT NULL,
    flow_type               VARCHAR(16) NOT NULL,
    delta                   INTEGER NOT NULL,
    from_location           VARCHAR(16) NOT NULL,
    operator_name           VARCHAR(50) NOT NULL,
    flow_time               TIMESTAMP(0) NOT NULL,
    purpose                 VARCHAR(200),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_cryo_flow PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_cryo_flow IS '冻存出入库流水（追溯靠它；取走 / 补入登记可改可删，删为软删；改删后逐笔剩余不得为负）';
COMMENT ON COLUMN t_lqg_cryo_flow.id IS '主键';
COMMENT ON COLUMN t_lqg_cryo_flow.batch_id IS 'FK→t_lqg_cryo_batch.id';
COMMENT ON COLUMN t_lqg_cryo_flow.flow_type IS 'take 取走 / add 补入 / adjust 盘点调整';
COMMENT ON COLUMN t_lqg_cryo_flow.delta IS '★ 带符号变化量：take 恒为负、add 恒为正、adjust 可正可负且不为 0；落库前校验「剩余 + delta ≥ 0」';
COMMENT ON COLUMN t_lqg_cryo_flow.from_location IS 'minus80 / ln2：由批次当时所在位置自动带出，不让人选';
COMMENT ON COLUMN t_lqg_cryo_flow.operator_name IS '经手人（默认当前登录人）';
COMMENT ON COLUMN t_lqg_cryo_flow.flow_time IS '发生时间（默认当前）';
COMMENT ON COLUMN t_lqg_cryo_flow.purpose IS '用途 / 原因（adjust 必填）';
COMMENT ON COLUMN t_lqg_cryo_flow.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_cryo_flow.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_cryo_flow.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_cryo_flow.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_cryo_flow.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_cryo_flow.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE INDEX idx_cryo_flow_batch ON t_lqg_cryo_flow (batch_id, flow_time);


-- ══════════════════════════════════════════════════════════════════════════
-- 第二段：菜单与按钮权限（手写；第一段是生成器逐字节输出，别混）
-- 号段依据 doc/lint-profile.yaml：CRYO 菜单 5400-5499；本票取 5401-5407，
-- 让开 CRYO-WEB-001 票面点名的 5410（C）+ 5411-5417（F）。
-- ══════════════════════════════════════════════════════════════════════════
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5401, '冻存查询', 0, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:list',   '#', 103, 1, now(), '批次列表（GET /lqg/cryo/batch/list）——页面菜单由 CRYO-WEB-001 落 5410'),
    (5402, '冻存详情', 0, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:query',  '#', 103, 1, now(), '批次详情（GET /lqg/cryo/batch/{id}）'),
    (5403, '冻存新增', 0, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:add',    '#', 103, 1, now(), '新建批次（POST /lqg/cryo/batch）'),
    (5404, '冻存修改', 0, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:edit',   '#', 103, 1, now(), '修改批次（PUT /lqg/cryo/batch；初始支数可改，逐笔不得为负）'),
    (5405, '冻存删除', 0, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:remove', '#', 103, 1, now(), '软删批次（DELETE /lqg/cryo/batch/{ids}；有未删流水的不许删）'),
    (5406, '冻存导出', 0, 6, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:export', '#', 103, 1, now(), '按模板导出 xlsx（POST /lqg/cryo/batch/export，端点在 CRYO-WEB-001）'),
    (5407, '冻存流水', 0, 7, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:cryo:flow',   '#', 103, 1, now(), '取走 / 补入 / 盘点调整 / 改删登记（端点在 CRYO-FLOW-001）')
ON CONFLICT (menu_id) DO NOTHING;

-- 授权：101（lqg_admin）与 102（lqg_internal）都能调 /lqg/cryo/**
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5401 AND 5407) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
