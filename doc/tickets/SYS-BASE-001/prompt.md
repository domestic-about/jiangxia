---
ticket: SYS-BASE-001
track: SYS
phase: D1
size: L
req_refs:
  - REQ-SYS-003
  - REQ-SYS-010
depends_on: []
touches:
  - code/RuoYi-Vue-Plus/**
  - code/deploy/dev/**
  - .gitignore
adr_refs:
  - ADR-0001
  - ADR-0002
  - ADR-0009
blueprint_refs:
  - FLOW:F-OPS-01.step1
accept:
  - name: "库是 PostgreSQL 16、基线与字典与角色都出自本票 Flyway、26 个字典逐个点名、评分分值落在 remark、多租户已关"
    form: DDL
    run: |-
      python3 doc/verify/db.py --sql "SELECT current_setting('server_version_num')::int / 10000" --eq 16 &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260921080%__SYS-BASE-001-%' OR success AND script LIKE 'V20260921081%__SYS-BASE-001-%' OR success AND script LIKE 'V20260921082%__SYS-BASE-001-%'" --eq 3 &&
      python3 doc/verify/db.py --sql "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='sys_user' AND column_name IN ('create_dept','create_by','create_time','update_by','update_time','del_flag')" --col-set create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT dict_type FROM sys_dict_type WHERE dict_type LIKE 'lqg\_%'" --col-set lqg_sample_kind,lqg_submit_source,lqg_verify_status,lqg_gender,lqg_has_none,lqg_yes_no,lqg_stain_type,lqg_marker_expr,lqg_cryo_flow_type,lqg_cryo_location,lqg_doc_status,lqg_doc_type,lqg_doc_kind,lqg_doc_audience,lqg_file_format,lqg_render_status,lqg_image_slot,lqg_unit_status,lqg_bind_status,lqg_hint_tissue_type,lqg_hint_organoid_type,lqg_hint_sample_type,lqg_score_pre_culture,lqg_score_culture_days,lqg_score_count,lqg_score_diameter &&
      python3 doc/verify/db.py --sql "SELECT dict_value || ':' || remark FROM sys_dict_data WHERE dict_type='lqg_score_count'" --col-set "lt100:0,100to1500:10,1500to4000:25,gt4000:40" &&
      python3 doc/verify/db.py --sql "SELECT dict_value FROM sys_dict_data WHERE dict_type='lqg_stain_type'" --col-set HE,IF,IHC,OTHER,NONE &&
      python3 doc/verify/db.py --sql "SELECT role_id || ':' || role_key FROM sys_role WHERE role_id BETWEEN 101 AND 103 AND del_flag='0'" --col-set "101:lqg_admin,102:lqg_internal,103:lqg_external" &&
      test "$(ls code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921080*__SYS-BASE-001-*.sql code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921081*__SYS-BASE-001-*.sql code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921082*__SYS-BASE-001-*.sql | wc -l | tr -d ' ')" = 3 &&
      awk '/^tenant:/{f=1} f&&/enable:/{print; exit}' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application.yml | grep -qE 'enable:[[:space:]]*false'
    counterfeit: |-
      段1 沿用 dongjiaoshan 的 MySQL 连接 → 这条 SQL 在 MySQL 上直接报错，exit 2。
      段2 在库里手工灌脚本、没写成 Flyway 迁移（本地绿、测试与生产环境永远拿不到）→ 3 变 0 红；只写了基线没写字典迁移 → 2 红。
      段3 基线用了 MySQL 版脚本改出来的表（列名对、但 sys_user 少 create_dept）→ 集合不等红。
      段4 字典手写漏了一个（最容易漏 lqg_hint_* 三个和 lqg_doc_audience）或多建了一个 → 集合不等红。
      段5 评分分值写进了 dict_label 或写死在 Java 里、remark 为空 → 红（将来改分值就得改代码）。
      段6 染色漏了会上补的「无染色」NONE → 红。
      段8 迁移文件没进源码树（只在 target/ 里）→ ls 失败红。
      段9 多租户没关（上游默认 true）→ grep 红；之后所有 INSERT 都会被租户拦截器改写。
  - name: "业务模块真的挂进了后端且反映真实环境：ping 的 db / tenant / encrypt 三项取自运行期而不是写死"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET /lqg/sys/ping | jq -e '.code==200 and .data.module=="ruoyi-lqg" and .data.db=="PostgreSQL" and (.data.dbVersion|tostring|startswith("16")) and .data.tenantEnabled==false and .data.encryptEnabled==true' &&
      bash doc/verify/api.sh --as anon --bizcode GET /lqg/sys/ping | grep -qE '^401'
    counterfeit: |-
      ruoyi-lqg 建了但没加进 ruoyi-admin 的依赖 → 接口 404，jq 红。
      ping 里把 "PostgreSQL" / "16" 写死成常量：骗不过第 1 条 DDL 断言的 server_version_num，但这里要求 dbVersion 与之同源——报告里要贴 ping 输出与 db.py 输出两份对照。
      dev 没开字段加密（上游默认 enable: false）→ encryptEnabled=false 红；之后 seed 里的密文读出来会是乱码。
      接口忘了鉴权（@SaIgnore）→ 匿名请求拿到 200，第 3 段红。
  - name: "测试账号灌得进真实的若依表结构：8 个账号与角色逐一对上，且此刻其余 6 段 seed 因表未建被跳过而不是报错"
    form: DATA
    run: |-
      OUT="$(bash doc/verify/reseed.sh --yes)" && printf '%s' "${OUT}" | grep -q '已灌 01-accounts.sql' && printf '%s' "${OUT}" | grep -q '跳过 04-sample.sql' &&
      python3 doc/verify/db.py --sql "SELECT u.user_name || ':' || r.role_key FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.user_id JOIN sys_role r ON r.role_id = ur.role_id WHERE u.user_id BETWEEN 9000000000 AND 9000009999 AND u.del_flag='0'" --col-set "lqgadmin:lqg_admin,lqg_13800000001:lqg_internal,wx_13800000011:lqg_external,wx_13800000012:lqg_external,wx_13800000013:lqg_external,wx_13800000014:lqg_external,wx_13800000015:lqg_external,wx_13800000016:lqg_external"
    counterfeit: |-
      角色 id 没按 lint-profile 取 101 / 102 / 103（比如让序列自增）→ seed 里的 sys_user_role 指向不存在的角色，JOIN 后集合为空红。
      为了让 seed 灌进去而去改 seed 文件（把列名改成自己建的列）→ 本条不红，但 gen_seed.py 重新生成时会对着 SSOT 报错；完工报告要求贴 `git status doc/verify` 为空。
      reseed 在表未建时整体失败退出（而不是逐段跳过）→ 第一行就红。
---

# SYS-BASE-001 · 后端工程骨架：若依 + PostgreSQL + Flyway 基线 + ruoyi-lqg 模块 + 字典与角色 seed + 本地开发环境

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：无（本任务的起点之一）
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0001**：从干净上游起项目，dongjiaoshan 只搬做法不搬代码；全部业务进 `ruoyi-modules/ruoyi-lqg` 一个模块；多租户关
  - **ADR-0002 / ADR-0009**：PostgreSQL 16（Docker）；业务表 6 公共字段、不带 tenant_id、不带 del_unique；Flyway 文件名与号段见 `doc/lint-profile.yaml`
  - 栈包坑手册 `~/claude-config/stacks/ruoyi-vue-plus/gotchas.md` 整篇过一遍——**§1、§4 的 del_unique 一节不适用于本项目**（那是 MySQL 的退路）
- [ ] 口径复述（本张最容易做反的）：
  1. **别拷 dongjiaoshan 的代码树**。那边是 MySQL + 多租户预埋 + 农场业务，拷过来删不干净。从上游 RuoYi-Vue-Plus 5.5.x 拉。
  2. **基线走 Flyway，不是手工灌库**。上游 `script/sql/postgres/postgres_ry_vue_5.X.sql` 原样作为第一支迁移；之后任何表结构变化都是新迁移。手工在库里建的表，生产环境永远拿不到。
  3. **字典 seed 不手写**：`python3 doc/tools/gen_ddl_pg.py --dicts` 的输出就是迁移文件的内容（26 个字典，评分分值在 remark 列）。

## 1 背景与口径

合同技术架构一行（REQ-SYS-003）：若依 Spring Boot + PostgreSQL，Docker 部署。Kevin 2026-09-16 定栈、09-17 定 PostgreSQL。
这一张把「能跑起来的空壳」立住：后面每一张 ticket 都假设后端能起、库是 PostgreSQL、Flyway 在管表、`ruoyi-lqg` 模块已挂进 `ruoyi-admin`、
验收执行器（`doc/verify/`）连得上库、`reseed.sh` 灌得进测试账号。
REQ-SYS-010 要的「测试环境只用测试数据」在本地的落点就是 `doc/verify/seed/`——那份 seed 是需求层写的，**不要改它来迁就实现**；灌不进去说明表和 SSOT 对不上。

## 2 实现要点

### 2.1 后端骨架
- `code/RuoYi-Vue-Plus/`：上游 5.5.x。数据源切 PostgreSQL（`application-dev.yml` 里上游已有注释好的 postgres 配置），库名 `lqg_dev`。
- `tenant.enable: false`（`application.yml`）。不引 `ruoyi-workflow`；SnailJob 不启用（本项目的定时任务用 Spring `@Scheduled`）。
- 新模块 `ruoyi-modules/ruoyi-lqg`（artifactId 同名），包根 `org.dromara.lqg`，按域分子包 `auth / sample / embed / cryo / qc / doc / ocr / ext / sys`。
  两处接入：`ruoyi-modules/pom.xml` 的 `<modules>` + `ruoyi-admin/pom.xml` 的依赖。
- `GET /lqg/sys/ping`（登录即可调）：返回 `doc/api-contract.md` 约定的形状，`db` / `dbVersion` 取自真实连接的 `DatabaseMetaData`，不写死。

### 2.2 Flyway
- 引 `flyway-core` + `flyway-database-postgresql`；`out-of-order=false`、`baseline-on-migrate=false`（空库直接从第一支迁移跑起）。
- 迁移目录 `ruoyi-admin/src/main/resources/db/migration/`，D1 日期段 `20260921`，SYS 域 HHmm `08xx`：
  - `V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql` = 上游 postgres 脚本原样
  - `V202609210810__SYS-BASE-001-lqg-dicts.sql` = `gen_ddl_pg.py --dicts` 的输出
  - `V202609210820__SYS-BASE-001-lqg-roles.sql` = 三个角色：101 `lqg_admin` 实验室管理员 / 102 `lqg_internal` 内部人员 / 103 `lqg_external` 外部人员

### 2.3 字段加密与本地环境
- `mybatis-encryptor`：dev / test 写死 `enable: true, algorithm: AES, encode: BASE64, password: LqgTestAesKey#01`（seed 的密文按它算的）；
  prod 的 password 取环境变量，**不进仓库**。
- `code/deploy/dev/docker-compose.yml`：`postgres:16-alpine`（库 `lqg_dev`，只绑 127.0.0.1）、`redis`、`minio`（本地对象存储，bucket 私有）。
- 根 `.gitignore` 加 `doc/verify/verify.env`、各端构建产物、`*.env.local`。
- 照 `doc/verify/verify.env.example` 建 `doc/verify/verify.env`，跑通 `bash doc/verify/reseed.sh --yes`（此时只会灌 `01-accounts.sql`，其余分段提示「表还没建」是正常的）。

## 3 边界（明确不做）

- 不建任何 `t_lqg_*` 业务表（各域的建模 ticket 各自建）
- 不做小程序登录、不建 mp 的 client 行（AUTH-LOGIN-001）
- 不动若依自带模块的源码（`ruoyi-common-*`、`ruoyi-system` 等只读）
- 不上生产配置、不写部署脚本（SYS-STAGING-001 / SYS-PROD-001）
- 不改 `doc/verify/seed/` 与 `gen_seed.py`

## 4 完工报告要求

1. 上游版本号与 commit；`ruoyi-lqg` 两处接入的 diff
2. 三支迁移的文件名；`SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank` 的输出
3. `reseed.sh --yes` 的完整输出（应为：已灌 01，其余 6 段跳过）
4. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
5. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
6. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
7. 验证用的后端 / 前端长进程已关，或明示留给谁
