---
id: ADR-0009
status: proposed
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "doc/tools/gen_ddl_pg.py"
decision:
  key: db.pg_conventions
  value: "表名 t_lqg_<实体>；主键 BIGINT 雪花；业务表固定带 6 个公共字段（create_dept / create_by / create_time / update_by / update_time / del_flag），不带 tenant_id；软删后同键可重建用部分唯一索引（CREATE UNIQUE INDEX … WHERE del_flag = '0'），不建 del_unique 列；时间用 TIMESTAMP(0)、日期用 DATE；字典值存 VARCHAR；迁移走 Flyway，文件名 V<yyyyMMddHHmm>__<TICKET>-<desc>.sql，日期段按任务、HHmm 按域分段（见 lint-profile.yaml）；建表 DDL 由 doc/tools/gen_ddl_pg.py 从 field-ssot.yaml 生成"
  rejected_values:
    - "沿用栈包的 del_unique 普通列方案"
    - "业务表预埋 tenant_id"
---
# ADR-0009: PostgreSQL 下的建表与迁移约定

**决策者**: 待 Kevin 过目（由 ADR-0002 推出的实现口径，AI 定，可回滚）
**关联**: ADR-0002、doc/lint-profile.yaml、doc/authority/field-ssot.yaml、doc/tools/gen_ddl_pg.py、全部 DDL 类 accept

## 背景

栈包（`~/claude-config/stacks/ruoyi-vue-plus/`）的建表金标准是为 MySQL 写的：`del_unique` 那一整套是因为 MySQL 8 不支持部分唯一索引才有的退路。
换到 PostgreSQL 再照抄，是把别人的拐杖也一起搬过来。

## 决策

按 frontmatter。

- 「沿用栈包的 del_unique 普通列方案」不采纳：PostgreSQL 原生支持 `WHERE del_flag = '0'` 的部分唯一索引，软删后同键自然可以重建，
  不需要应用层在软删时回填，也就没有栈包 gotchas §4 那一串「静默失败」的坑。
- 「业务表预埋 tenant_id」不采纳：多租户关闭（ADR-0001），预埋一个永远是同一个值的列只会让每个唯一索引都多一列。

## 后果

- DDL 类 accept 断的公共字段全集 = lint-profile.yaml 的 6 个；唯一性断言断的是 `pg_indexes.indexdef` 里带 `WHERE (del_flag = '0'::bpchar)`。
- 软删仍走框架的 `@TableLogic`（del_flag '0' → '1'），**不需要**显式 set 第二列。
- Flyway 需自己接进后端（若依上游不带）；基线 = 若依自带的 `script/sql/postgres/` 脚本。
- 若依 PostgreSQL 脚本里布尔、时间类型与 MySQL 版略有差异，业务代码里不写方言相关的原生 SQL 函数（日期差用 Java 算或用标准 SQL）。
