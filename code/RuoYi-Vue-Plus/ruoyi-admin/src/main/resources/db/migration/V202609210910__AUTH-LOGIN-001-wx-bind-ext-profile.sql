-- AUTH-LOGIN-001 · 小程序登录后端：微信绑定表 + 外部用户档案表 + mp 客户端行
--
-- 取号依据 doc/lint-profile.yaml：D1 = 20260921，AUTH 域 HHmm 09xx → 0910。
--
-- 两张表的 CREATE/COMMENT/INDEX 段落由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml
-- 生成（ADR-0009：PostgreSQL 方言、公共 6 字段、唯一性一律部分唯一索引 WHERE del_flag = '0'，
-- 不带 tenant_id、不带 del_unique —— 那是 MySQL 金标准的退路，本项目用不上）。
-- 改字段改 SSOT 重新生成；已应用的迁移终身不改。

-- ── mp 小程序客户端（client_id = md5('mp' || client_secret)，secret 取 mpDevSecret#2026）
-- dev / test 用的公开常量，不是真密钥：真实小程序 appid / secret 走部署期配置（ADR-0008 只约束 mock 登录）。
INSERT INTO sys_client (id, client_id, client_key, client_secret, grant_type, device_type, active_timeout, timeout, status, del_flag, create_dept, create_by, create_time)
SELECT 3, '22b2aecd0710671691ec1c07f2542b9d', 'mp', 'mpDevSecret#2026', 'xcx', 'xcx', 1800, 604800, '0', '0', 103, 1, now()
WHERE NOT EXISTS (SELECT 1 FROM sys_client WHERE client_key = 'mp' AND del_flag = '0');

-- 由 doc/tools/gen_ddl_pg.py 从 doc/authority/field-ssot.yaml 生成；改字段改 SSOT，别手改本段

-- ── t_lqg_wx_bind：微信身份绑定（openid ↔ sys_user；同一手机号换微信登录会多一行）
CREATE TABLE t_lqg_wx_bind (
    id                      BIGINT NOT NULL,
    user_id                 BIGINT NOT NULL,
    openid                  VARCHAR(64) NOT NULL,
    unionid                 VARCHAR(64),
    phone                   VARCHAR(20) NOT NULL,
    last_login_time         TIMESTAMP(0),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_wx_bind PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_wx_bind IS '微信身份绑定（openid ↔ sys_user；同一手机号换微信登录会多一行）';
COMMENT ON COLUMN t_lqg_wx_bind.id IS '主键';
COMMENT ON COLUMN t_lqg_wx_bind.user_id IS 'FK→sys_user.user_id';
COMMENT ON COLUMN t_lqg_wx_bind.openid IS '小程序 openid';
COMMENT ON COLUMN t_lqg_wx_bind.unionid IS 'unionid（有就存，没有不强求）';
COMMENT ON COLUMN t_lqg_wx_bind.phone IS '★ 登录当时微信返回的手机号；内外部判定只认它，不认前端传的任何身份字段';
COMMENT ON COLUMN t_lqg_wx_bind.last_login_time IS '最近登录时间';
COMMENT ON COLUMN t_lqg_wx_bind.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_wx_bind.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_wx_bind.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_wx_bind.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_wx_bind.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_wx_bind.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_wx_openid ON t_lqg_wx_bind (openid) WHERE del_flag = '0';
CREATE INDEX idx_wx_bind_user ON t_lqg_wx_bind (user_id);
CREATE INDEX idx_wx_bind_phone ON t_lqg_wx_bind (phone);

-- ── t_lqg_ext_profile：外部用户档案（单位、组别与核验状态；内部人员没有这张表的行）
CREATE TABLE t_lqg_ext_profile (
    id                      BIGINT NOT NULL,
    user_id                 BIGINT NOT NULL,
    real_name               VARCHAR(50),
    unit_id                 BIGINT,
    group_id                BIGINT,
    unit_name_input         VARCHAR(100),
    group_name_input        VARCHAR(100),
    bind_status             VARCHAR(16) NOT NULL DEFAULT 'unbound',
    verified_by             BIGINT,
    verified_time           TIMESTAMP(0),
    reject_reason           VARCHAR(200),
    create_dept             BIGINT,
    create_by               BIGINT,
    create_time             TIMESTAMP(0),
    update_by               BIGINT,
    update_time             TIMESTAMP(0),
    del_flag                CHAR(1) NOT NULL DEFAULT '0',
    CONSTRAINT pk_lqg_ext_profile PRIMARY KEY (id)
);
COMMENT ON TABLE t_lqg_ext_profile IS '外部用户档案（单位、组别与核验状态；内部人员没有这张表的行）';
COMMENT ON COLUMN t_lqg_ext_profile.id IS '主键';
COMMENT ON COLUMN t_lqg_ext_profile.user_id IS 'FK→sys_user.user_id，一人一行';
COMMENT ON COLUMN t_lqg_ext_profile.real_name IS '姓名（外部自填）';
COMMENT ON COLUMN t_lqg_ext_profile.unit_id IS 'FK→t_lqg_source_unit.id（自选或核验时归入）';
COMMENT ON COLUMN t_lqg_ext_profile.group_id IS 'FK→t_lqg_unit_group.id';
COMMENT ON COLUMN t_lqg_ext_profile.unit_name_input IS '单位不在列表时外部自填的单位名';
COMMENT ON COLUMN t_lqg_ext_profile.group_name_input IS '组别不在列表时外部自填的组别名';
COMMENT ON COLUMN t_lqg_ext_profile.bind_status IS '★ unbound / pending / verified / rejected；只有 verified 才参与「同组互看」，改单位或组别后回到 pending';
COMMENT ON COLUMN t_lqg_ext_profile.verified_by IS '核验人 user_id';
COMMENT ON COLUMN t_lqg_ext_profile.verified_time IS '核验时间';
COMMENT ON COLUMN t_lqg_ext_profile.reject_reason IS '驳回原因';
COMMENT ON COLUMN t_lqg_ext_profile.create_dept IS '创建部门';
COMMENT ON COLUMN t_lqg_ext_profile.create_by IS '创建者';
COMMENT ON COLUMN t_lqg_ext_profile.create_time IS '创建时间';
COMMENT ON COLUMN t_lqg_ext_profile.update_by IS '更新者';
COMMENT ON COLUMN t_lqg_ext_profile.update_time IS '更新时间';
COMMENT ON COLUMN t_lqg_ext_profile.del_flag IS '删除标志（0 存在 / 1 删除）';
CREATE UNIQUE INDEX uk_ext_profile_user ON t_lqg_ext_profile (user_id) WHERE del_flag = '0';
CREATE INDEX idx_ext_profile_group ON t_lqg_ext_profile (group_id, bind_status);

