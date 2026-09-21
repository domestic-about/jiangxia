---
id: ADR-0002
status: accepted
implementation_status: not-yet-implemented
supersedes: []
superseded_by: []
amends: []
amended_by: []
verified: 2026-09-17
anchor: "doc/authority/field-ssot.yaml"
decision:
  key: db.engine
  value: "PostgreSQL 16，用 Docker 装在云服务器上与后端同机运行；不购买 RDS；每日 pg_dump 备份到 OSS 私有桶"
  rejected_values:
    - "阿里云 RDS 托管数据库"
    - "MySQL（栈包默认方言）"
  decided_by: kevin
  decided_at: 2026-09-17
---
# ADR-0002: 数据库用 PostgreSQL，Docker 部署，不用 RDS

**决策者**: Kevin（2026-09-17「不需要 rds，直接在服务器上使用 docker 安装 postgres 即可」；合同第二条第 3 款、附件第 14 行同口径）
**关联**: REQ-SYS-003 / REQ-SYS-004、ADR-0009（由此推出的方言约定）、code/deploy/

## 背景

云资源费合同写死 2000 元/年（服务器 + OSS + 域名），装不下一台 RDS。若依自带 `script/sql/postgres/` 建表脚本、pom 里有驱动、
`application-dev.yml` 有注释好的 postgres 配置，已在 dongjiaoshan 代码里核过。

## 决策

按 frontmatter。「阿里云 RDS 托管数据库」不采纳：预算装不下。「MySQL（栈包默认方言）」不采纳：Kevin 指定 PostgreSQL。

## 后果

- 数据库和应用同机：服务器一坏两样一起坏，所以**每日备份 + 做过一次真实恢复演练**是上线前硬要求（REQ-SYS-004），不是可选项。
- 数据库端口不映射到公网，只在 compose 内网暴露。
- dongjiaoshan 的部署清单和 aliyun-deploy skill 是按 MySQL / RDS 写的，部署 ticket 不能照抄。
- 栈包的 DDL renderer、verify-lib.sh 是 MySQL 方言，本项目用自带的 `doc/tools/gen_ddl_pg.py` 与 `doc/verify/db.py`。
