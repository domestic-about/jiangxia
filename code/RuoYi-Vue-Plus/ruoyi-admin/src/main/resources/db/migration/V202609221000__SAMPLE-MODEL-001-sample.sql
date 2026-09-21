-- SAMPLE-MODEL-001 · 样本主档 t_lqg_sample + 送检单号序列 + 「样本总表」菜单（doc/lint-profile.yaml）
--
-- 取号依据 doc/lint-profile.yaml：
--   * 日期段 D2 = 20260922（本票 phase D2）；HHmm 按域分段，SAMPLE = 10xx；
--   * 1000 未被占用，且大于全部 D1 迁移（最大 V202609210930）—— out-of-order=false 下不会触发
--     FlywayValidateException（SYS-WEB-001 踩过跨域补号的坑：版本号小于已应用的迁移会让后端启动即失败）。
--   * 菜单号依据同文件：SAMPLE 域 5200-5299。
--
-- 第一段是 doc/tools/gen_ddl_pg.py --migration V202609221000__SAMPLE-MODEL-001-sample.sql 的**逐字节输出**
-- （t_lqg_sample：公共 6 字段、两个**部分唯一索引** uk_sample_submit_no / uk_sample_internal_no
--  WHERE del_flag='0'、普通索引 3 个、无 tenant_id / del_unique）＋同一生成器追加的序列 seq_lqg_submit_no。
--
-- ★ ADR-0010：**一张表**同时承载「样本记录信息表」(sample_kind='tissue') 与「类器官收样记录」
--   (sample_kind='organoid')；不照两份模板建两张表。「类器官类型」只是 organoid 类才填的一列。
-- ★ 内部编号 internal_no **手填不自动生成**（编号规则是甲方自己的），列可空（待核验的外部样本没有），
--   唯一性靠部分唯一索引 → 软删后可重用同一个编号。
-- ★ donor_name / hospital_no 的加密（ADR-0006）在 service 层用 EncryptUtils 读写（见 SampleFieldCipher），
--   不用框架 @EncryptField 拦截器：拦截器会加 "ENC_" 前缀，而 doc/verify/seed/ 与 ADR-0006 的既有密文
--   都是裸 Base64；两套混用会让 seed 行读出来还是密文（accept 第 2 条的 openssl 对照会红）。

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_sample：样本主档：承载「样本记录信息表」(tissue) 与「类器官收样记录」(organoid)；外部提交直接写本表
CREATE TABLE t_lqg_sample (
    id                      BIGINT NOT NULL,
    submit_no               VARCHAR(32) NOT NULL,
    sample_kind             VARCHAR(16) NOT NULL,
    submit_source           VARCHAR(16) NOT NULL,
    submitter_id            BIGINT NOT NULL,
    verify_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    verify_by               BIGINT,
    verify_time             TIMESTAMP(0),
    invalid_reason          VARCHAR(200),
    source_unit_id          BIGINT,
    source_unit_name        VARCHAR(100) NOT NULL,
    donor_name              VARCHAR(255),
    gender                  VARCHAR(16),
    age                     VARCHAR(20),
    hospital_no             VARCHAR(255),
    tissue_type             VARCHAR(100),
    organoid_type           VARCHAR(100),
    has_pathology           CHAR(1),
    receive_date            DATE,
    internal_no             VARCHAR(64),
    is_fixed                CHAR(1),
    process_time            TIMESTAMP(0),
    has_qc_sheet            CHAR(1),
    has_viability_report    CHAR(1),
    operator_name           VARCHAR(50),
    remark                  VARCHAR(500),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_sample PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_sample IS '样本主档：承载「样本记录信息表」(tissue) 与「类器官收样记录」(organoid)；外部提交直接写本表';
COMMENT ON COLUMN t_lqg_sample.id IS '主键';
COMMENT ON COLUMN t_lqg_sample.submit_no IS '送检单号 SJ+8 位序号（序列 seq_lqg_submit_no）；外部用它指代样本，因为内部编号默认不对外';
COMMENT ON COLUMN t_lqg_sample.sample_kind IS 'tissue 组织样本（样本记录信息表）/ organoid 类器官（类器官收样记录）';
COMMENT ON COLUMN t_lqg_sample.submit_source IS '★ internal / external：提交当时按提交人身份落库，之后不随账号升降级而变';
COMMENT ON COLUMN t_lqg_sample.submitter_id IS '提交人 user_id（外部可见范围按它算）';
COMMENT ON COLUMN t_lqg_sample.verify_status IS '★ pending 待核验 / valid 有效 / invalid 无效；内部录入直接 valid；外部只在 pending、invalid 时能改';
COMMENT ON COLUMN t_lqg_sample.verify_by IS '核验人 user_id';
COMMENT ON COLUMN t_lqg_sample.verify_time IS '核验时间';
COMMENT ON COLUMN t_lqg_sample.invalid_reason IS '判无效的原因（外部可见）';
COMMENT ON COLUMN t_lqg_sample.source_unit_id IS 'FK→t_lqg_source_unit.id；自填单位名时为空';
COMMENT ON COLUMN t_lqg_sample.source_unit_name IS '来源单位名称（导出用；选了单位就存单位名快照）';
COMMENT ON COLUMN t_lqg_sample.donor_name IS '★ 供体姓名，@EncryptField 加密落库（ADR-0006），只支持精确查询；organoid 类可空';
COMMENT ON COLUMN t_lqg_sample.gender IS '性别 male / female / unknown';
COMMENT ON COLUMN t_lqg_sample.age IS '年龄（文本：56 / 3月龄，模板没限定单位）';
COMMENT ON COLUMN t_lqg_sample.hospital_no IS '★ 住院号，@EncryptField 加密落库（ADR-0006），只支持精确查询';
COMMENT ON COLUMN t_lqg_sample.tissue_type IS '组织类型（tissue 类必填；自由文本，联想词来自字典 lqg_hint_tissue_type——不用历史值联想，防止外部看到别的单位填过的内容）';
COMMENT ON COLUMN t_lqg_sample.organoid_type IS '类器官类型（organoid 类必填；自由文本，联想词来自字典 lqg_hint_organoid_type）';
COMMENT ON COLUMN t_lqg_sample.has_pathology IS '有无病理 Y / N（模板批注 N5 前半句；不进 14 列导出）';
COMMENT ON COLUMN t_lqg_sample.receive_date IS '收样日期（内部填；核验为 valid 时必填）';
COMMENT ON COLUMN t_lqg_sample.internal_no IS '★ 内部编号：内部手填、全库唯一、贯穿到冻存；pending 的外部样本为空；默认不出现在任何 Ext*Vo 里（ADR-0004）';
COMMENT ON COLUMN t_lqg_sample.is_fixed IS '有无固定 Y 有 / N 无（按钮）';
COMMENT ON COLUMN t_lqg_sample.process_time IS '处理时间';
COMMENT ON COLUMN t_lqg_sample.has_qc_sheet IS '质控表 Y 有 / N 无（按钮，手点，不自动推导）';
COMMENT ON COLUMN t_lqg_sample.has_viability_report IS '细胞活率报告 Y 有 / N 无（按钮，手点）';
COMMENT ON COLUMN t_lqg_sample.operator_name IS '操作人（姓名文本，默认带当前登录人，可改）';
COMMENT ON COLUMN t_lqg_sample.remark IS '备注';
COMMENT ON COLUMN t_lqg_sample.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_sample.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_sample.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_sample.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_sample.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_sample.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_sample_submit_no ON t_lqg_sample (submit_no) WHERE del_flag = '0';
CREATE UNIQUE INDEX uk_sample_internal_no ON t_lqg_sample (internal_no) WHERE del_flag = '0';
CREATE INDEX idx_sample_submitter ON t_lqg_sample (submitter_id);
CREATE INDEX idx_sample_unit ON t_lqg_sample (source_unit_id);
CREATE INDEX idx_sample_status_date ON t_lqg_sample (verify_status, receive_date);

-- 送检单号序列（submit_no = 'SJ' || lpad(nextval::text, 8, '0')）
CREATE SEQUENCE IF NOT EXISTS seq_lqg_submit_no START 1;


-- ═══════════════════════════════════════════════════════════════════════════════
-- 第二段：菜单与按钮（不是生成器的输出）
-- ═══════════════════════════════════════════════════════════════════════════════
--
--   5200 「样本总表」 C path='sample' component='lqg/sample/index' (UI:admin.sample.list)
--   5201-5205 五个 F 按钮 = 五个权限串 lqg:sample:{list,query,add,edit,remove}
--
-- ★ 5200 的父目录是 0（SAMPLE 是工作台一级菜单），与 AUTH 的 5100 目录是两棵不同的树。
--   上游 SysMenuServiceImpl.selectMenuTreeByUserId 用 getChildPerms(menus, 0) 建树：C 菜单挂在 0 下
--   才出得来（SYS-WEB-001 / AUTH-GROUP-001 都踩过「父节点没授权 → 子菜单整条消失」）。
-- ★ 同时授给 101（lqg_admin）与 102（lqg_internal）：内部人员是录样本的主力。
-- ★ 本票只做**内部**接口，菜单 C 页面（lqg/sample/index）由 SAMPLE-WEB-001 落；这里先把菜单与
--   权限串建好，否则 @SaCheckPermission("lqg:sample:*") 对 102 恒 403。

BEGIN;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES (5200, '样本总表', 0, 4, 'sample', 'lqg/sample/index', '',
        '1', '0', 'C', '0', '0', 'lqg:sample:list', 'table',
        103, 1, now(), 'SAMPLE-MODEL-001：样本主档总表（内部增删改查；页面由 SAMPLE-WEB-001 落）')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon,
                      create_dept, create_by, create_time, remark)
VALUES
    (5201, '样本查询', 5200, 1, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:list',   '#', 103, 1, now(), ''),
    (5202, '样本详情', 5200, 2, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:query',  '#', 103, 1, now(), ''),
    (5203, '样本新增', 5200, 3, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:add',    '#', 103, 1, now(), ''),
    (5204, '样本修改', 5200, 4, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:edit',   '#', 103, 1, now(), ''),
    (5205, '样本删除', 5200, 5, '', NULL, '', '1', '0', 'F', '0', '0', 'lqg:sample:remove', '#', 103, 1, now(), '')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM (VALUES (101), (102)) AS r(role_id)
CROSS JOIN (SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 5200 AND 5205) AS m
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
