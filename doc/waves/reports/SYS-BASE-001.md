# SYS-BASE-001 · 完工报告

- **ticket**：SYS-BASE-001（track SYS / phase D1 / size L）
- **status**：**done**
- **accept**：**3/3 绿**（第 2 条里 `--fresh-module` 的「后端进程启动时间」两行在本会话沙箱里跑不了 —— 见 WARN-1 与下面的手工替代验证）
- **分支**：`task/D1`，本地 commit `8bf9324`（未 push / 未 merge；`doc/waves/`、`_manifest.json` 未动）
- **上游**：`dromara/RuoYi-Vue-Plus` **v5.5.3**，commit `91ba3869e7520a43cae5f36fdcb2e9507ee4eb1e`（ticket 写「5.5.x」，取 5.5.x 末版；记为 doc-drift 级 WARN-4）

---

## 1 上游版本与 ruoyi-lqg 的两处接入

原样落盘，**不带 `.git`、不带 dongjiaoshan 任何文件**：

```
$ git ls-remote --tags https://github.com/dromara/RuoYi-Vue-Plus | grep v5.5.3
9fccad92194bd70ac7eb40514f722c71da4847d5  refs/tags/v5.5.3
91ba3869e7520a43cae5f36fdcb2e9507ee4eb1e  refs/tags/v5.5.3^{}   ← 实际 checkout
$ du -sh code/RuoYi-Vue-Plus && ls
15M   .editorconfig .gitee .gitignore .run LICENSE pom.xml README.md ruoyi-admin ruoyi-common ruoyi-extend ruoyi-modules script
```

> 拉取路径：gitee 的 `git clone` / tarball 在本机反复 `early EOF` 截断，改用 github 镜像；两边 tag 指向**同一个 commit**（上面两行 `git ls-remote` 一致），所以内容与 gitee 版一致。

`ruoyi-lqg` 两处接入的 diff：

```diff
--- a/code/RuoYi-Vue-Plus/ruoyi-modules/pom.xml
+++ b/code/RuoYi-Vue-Plus/ruoyi-modules/pom.xml
@@
         <module>ruoyi-workflow</module>
+        <!--  本项目业务模块（ADR-0001）——全部业务代码只在这一个模块里  -->
+        <module>ruoyi-lqg</module>
     </modules>

--- a/code/RuoYi-Vue-Plus/ruoyi-admin/pom.xml
+++ b/code/RuoYi-Vue-Plus/ruoyi-admin/pom.xml
@@
+        <!--  本项目业务模块（ADR-0001：全部业务代码只在这一个模块里，包根 org.dromara.lqg）  -->
+        <dependency>
+            <groupId>org.dromara</groupId>
+            <artifactId>ruoyi-lqg</artifactId>
+            <version>${revision}</version>
+        </dependency>
```

新模块：`ruoyi-modules/ruoyi-lqg`（artifactId 同名，包根 `org.dromara.lqg`），按域分 9 个子包
`auth / sample / embed / cryo / qc / doc / ocr / ext / sys`，每域下 `controller / service / domain / mapper`
（共 36 个 `package-info.java` 占位，把后续 ticket 的落点固定下来；本票不在这些域写业务逻辑）。
`ruoyi-lqg` 只依赖 `ruoyi-common-{core,doc,mybatis,security,web,tenant,encrypt}`，**不引 `ruoyi-workflow`**。

> ⚠️ `ruoyi-workflow` 的 Maven 模块**保留在上游原样**（`ruoyi-modules/pom.xml` 的 `<modules>` 与
> `ruoyi-admin` 的依赖都没删），只是 `warm-flow.enabled: false` 关掉运行。理由：删模块要动上游 pom
> 结构，而 ticket 只要求「不引、不启用」；关掉之后 `flow_*` 表不需要（基线里也没有），
> 验收/后端启动都验证过。这也和「不动若依自带模块」的边界一致。

## 2 三支迁移

| 文件名 | 内容 |
|---|---|
| `V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql` | 上游 `script/sql/postgres/postgres_ry_vue_5.X.sql` **原样**（只在最前面加了一段 `/* */` 说明），93057 B |
| `V202609210810__SYS-BASE-001-lqg-dicts.sql` | `python3 doc/tools/gen_ddl_pg.py --dicts` 的输出**逐字节**落盘（26 个字典 / 81 行；已用 `diff` 核过），26715 B |
| `V202609210820__SYS-BASE-001-lqg-roles.sql` | 三个角色 101/102/103（显式列名 INSERT + `ON CONFLICT (role_id) DO NOTHING`），2066 B |

取号依据 `doc/lint-profile.yaml`：D1 = `20260921`，SYS 域 HHmm `08xx` → 0800 / 0810 / 0820。

```
$ python3 doc/verify/db.py --sql "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank"
202609210800|V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql|True
202609210810|V202609210810__SYS-BASE-001-lqg-dicts.sql|True
202609210820|V202609210820__SYS-BASE-001-lqg-roles.sql|True
```

**空库端到端复验过**（不是「在已有库上补跑」）：

```
$ docker exec lqg-dev-postgres psql -U lqg -d postgres -c "DROP DATABASE IF EXISTS lqg_dev WITH (FORCE)" -c "CREATE DATABASE lqg_dev OWNER lqg"
$ (重启后端)
155:  - Migrating schema "public" to version "202609210800 - SYS-BASE-001-ruoyi-postgres-baseline"
2482: - Migrating schema "public" to version "202609210810 - SYS-BASE-001-lqg-dicts"
2847: - Migrating schema "public" to version "202609210820 - SYS-BASE-001-lqg-roles"
2891: - Successfully applied 3 migrations to schema "public", now at version v202609210820 (execution time 00:00.356s)
2940: - Started DromaraApplication in 5.621 seconds
```

## 3 `reseed.sh --yes` 完整输出

```
  已灌 01-accounts.sql
  跳过 02-wx-bind-ext-profile.sql：表还没建 → t_lqg_wx_bind t_lqg_ext_profile
  跳过 03-unit-group.sql：表还没建 → t_lqg_source_unit t_lqg_unit_group
  跳过 04-sample.sql：表还没建 → t_lqg_sample
  跳过 05-embed.sql：表还没建 → t_lqg_sample t_lqg_embed t_lqg_embed_marker
  跳过 06-cryo.sql：表还没建 → t_lqg_sample t_lqg_cryo_batch t_lqg_cryo_flow
  跳过 07-qc-docs.sql：表还没建 → t_lqg_sample t_lqg_qc_sample t_lqg_qc_organoid t_lqg_qc_score t_lqg_doc_image t_lqg_doc_attachment
reseed 完成
```

`doc/verify/seed/`、`gen_seed.py` **一个字节没动**：`git status doc/verify` 为空（`verify.env` 是 ignore 的）。

## 4 改了哪些文件

**新增（源码树）**

- `code/RuoYi-Vue-Plus/**`：上游 v5.5.3 全树（862 个文件，不含 `.git`）
- `ruoyi-admin/src/main/resources/db/migration/V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql`
- `ruoyi-admin/src/main/resources/db/migration/V202609210810__SYS-BASE-001-lqg-dicts.sql`
- `ruoyi-admin/src/main/resources/db/migration/V202609210820__SYS-BASE-001-lqg-roles.sql`
- `ruoyi-modules/ruoyi-lqg/pom.xml`
- `ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sys/controller/SysPingController.java` ← `GET /lqg/sys/ping`
- `ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sys/domain/vo/SysPingVo.java`
- `ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/<域>/{controller,service,domain,mapper}/package-info.java` ×36
- `code/deploy/dev/docker-compose.yml`、`code/deploy/dev/.env.example`（真实 `.env` 已被 ignore）
- `.mvn-settings.xml`（本机构建加速用的 aliyun 镜像；已提交，见坑 1）

**修改（上游文件）**

| 文件 | 改动 |
|---|---|
| `ruoyi-modules/pom.xml` | `<modules>` 加 `ruoyi-lqg` |
| `ruoyi-admin/pom.xml` | 依赖加 `ruoyi-lqg`；MySQL 驱动 → PostgreSQL 驱动；加 `flyway-core` + `flyway-database-postgresql` |
| `ruoyi-admin/.../application.yml` | `server.port: ${LQG_API_PORT:8081}`；`tenant.enable: false`；`spring.flyway`（locations / baseline-on-migrate=false / out-of-order=false / validate-on-migrate=true）；`warm-flow.enabled: false`；`mybatis-encryptor` 注释写清 dev/prod 口令策略 |
| `ruoyi-admin/.../application-dev.yml` | 数据源换 PostgreSQL（`${LQG_DB_*}`，默认 5433/lqg_dev）；Redis 换 `6380` + `${LQG_REDIS_PASSWORD}`；`spring.boot.admin.client.enabled: false`；`snail-job.enabled: false`（其余键保留）；`captcha.enable: false`；`mybatis-encryptor: enable/algorithm/encode/password = true/AES/BASE64/LqgTestAesKey#01` |
| `ruoyi-admin/.../application-prod.yml` | 数据源换 PostgreSQL，**口令一律 `${LQG_DB_PASSWORD}` / `${LQG_REDIS_PASSWORD}` 走环境变量、不进仓库**；`snail-job.enabled: false`；`spring.boot.admin.client.enabled: false` |
| 根 `.gitignore` | 加 `*.env.local`、`.m2repo/`、`.buildhome/`、`.tmp/`、`code/**/dist-ssr/`、`code/**/.vite/`、`code/**/unpackage/`、`code/**/.hbuilderx/`（`doc/verify/verify.env` 原本就有） |

**接口清单（本票）**：`GET /lqg/sys/ping`（登录即可调，无 `@SaIgnore`；匿名 → 401）。无前端页面（本票不产出页面）。

## 5 accept 逐条 ✅/❌ + 关键输出

### accept 1 · DDL —— ✅

```
$ python3 doc/verify/db.py --sql "SELECT current_setting('server_version_num')::int / 10000" --eq 16
16
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260921080%__SYS-BASE-001-%' OR success AND script LIKE 'V20260921081%__SYS-BASE-001-%' OR success AND script LIKE 'V20260921082%__SYS-BASE-001-%'" --eq 3
3
$ python3 doc/verify/db.py --sql "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='sys_user' AND column_name IN ('create_dept','create_by','create_time','update_by','update_time','del_flag')" --col-set create_dept,create_by,create_time,update_by,update_time,del_flag
create_dept
create_by
create_time
update_by
update_time
del_flag
$ python3 doc/verify/db.py --sql "SELECT dict_type FROM sys_dict_type WHERE dict_type LIKE 'lqg\_%'" --col-set lqg_sample_kind,...,lqg_score_diameter
lqg_sample_kind … lqg_score_diameter       （26 个，逐行打印，集合精确相等）
$ python3 doc/verify/db.py --sql "SELECT dict_value || ':' || remark FROM sys_dict_data WHERE dict_type='lqg_score_count'" --col-set "lt100:0,100to1500:10,1500to4000:25,gt4000:40"
lt100:0
100to1500:10
1500to4000:25
gt4000:40
$ python3 doc/verify/db.py --sql "SELECT dict_value FROM sys_dict_data WHERE dict_type='lqg_stain_type'" --col-set HE,IF,IHC,OTHER,NONE
HE IF IHC OTHER NONE
$ python3 doc/verify/db.py --sql "SELECT role_id || ':' || role_key FROM sys_role WHERE role_id BETWEEN 101 AND 103 AND del_flag='0'" --col-set "101:lqg_admin,102:lqg_internal,103:lqg_external"
101:lqg_admin
102:lqg_internal
103:lqg_external
$ test "$(ls …/V20260921080*… …/V20260921081*… …/V20260921082*… | wc -l | tr -d ' ')" = 3          # 3，通过
$ awk '/^tenant:/{f=1} f&&/enable:/{print; exit}' …/application.yml
  enable: false
ACCEPT-1 EXIT=0
```

### accept 2 · API —— ✅（除沙箱跑不了的两行进程检查，见 WARN-1）

```
$ bash doc/verify/api.sh --as admin GET /lqg/sys/ping | jq -e '…'
true
$ bash doc/verify/api.sh --as admin GET /lqg/sys/ping | jq .
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "module": "ruoyi-lqg",
    "db": "PostgreSQL",
    "dbVersion": 16,
    "tenantEnabled": false,
    "encryptEnabled": true,
    "mockLogin": null,
    "profile": "dev",
    "buildCommit": "unknown"
  }
}
$ bash doc/verify/api.sh --as anon --bizcode GET /lqg/sys/ping
401	认证失败，无法访问系统资源
```

**db / dbVersion 同源对照**（ticket 明确要求贴两份）：ping 的 `dbVersion=16` 与下面这条直连 SQL 同源，
两者都来自真实连接 —— ping 走的是 `DataSource.getConnection().getMetaData()`，SQL 走的是 5433 上那个库：

```
$ python3 doc/verify/db.py --sql "SELECT current_setting('server_version_num')::int / 10000" --eq 16
16
$ docker exec lqg-dev-postgres psql -U lqg -d lqg_dev -Atc "select version()"
PostgreSQL 16.13 on aarch64-unknown-linux-musl …
```

`--fresh-module` 原样跑法（沙箱里失败）：

```
$ bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET /lqg/sys/ping
doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
date: illegal option -- d
（exit 2）
```

**手工替代验证**（`api.sh` 第 62-67 行的源码比 jar 新那一半在沙箱里能跑，且通过）：

```
$ JAR=code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
$ stat -f '%Sm' -t '%Y-%m-%d %H:%M:%S' $JAR
2026-09-21 14:25:40
$ find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -type f -newer $JAR | head -1
（空 —— 没有比 jar 新的源码）
$ lsof -ti tcp:8081 -sTCP:LISTEN
68823          # 后端进程
$ ls -l /tmp/lqg-backend-fresh.log   # 该进程 14:26:48 启动，晚于 jar 的 14:25:40
```

### accept 3 · DATA —— ✅

```
$ OUT="$(bash doc/verify/reseed.sh --yes)" && printf '%s' "${OUT}" | grep -q '已灌 01-accounts.sql' && printf '%s' "${OUT}" | grep -q '跳过 04-sample.sql'
（输出见 §3；EXIT=0）
$ python3 doc/verify/db.py --sql "SELECT u.user_name || ':' || r.role_key FROM sys_user u JOIN sys_user_role ur … " --col-set "lqgadmin:lqg_admin,…,wx_13800000016:lqg_external"
lqgadmin:lqg_admin
lqg_13800000001:lqg_internal
wx_13800000011:lqg_external
wx_13800000012:lqg_external
wx_13800000013:lqg_external
wx_13800000014:lqg_external
wx_13800000015:lqg_external
wx_13800000016:lqg_external
ACCEPT-3 EXIT=0
```

> 8 个账号都能**真的登上**（accept 3 只查库，这里补一条运行期证据）：`api.sh --as admin` 用的是
> `sys_client.client_id = e5cd7e4891bf95d1d19206ce24a7b32e` + `grantType=password` + `lqgadmin/admin123`，
> 登录成功才拿到 token（否则 api.sh 直接 exit 2）。

### 加密口径的旁证（ticket §2.3 要求）

seed 里 `t_lqg_sample.donor_name / hospital_no` 的密文用 `LqgTestAesKey#01`（AES/ECB/PKCS5Padding，Base64）
能解出明文，证明 dev 写死的四项与 seed 一致：

```
明文样本: 测试供体甲 / ZY0000001 / 测试供体乙 / ZY0000002 …
```

## 6 遗留与 raise

- **`GET /lqg/sys/ping` 的 `mockLogin` 字段返回 `null`**：ADR-0008 说的 `lqg.auth.mock-login=true`
  是 AUTH-LOGIN-001 的活，本票没有 mock 登录实现，所以没有可报的真值 —— 按「不许写死」的口径先不返回。
  字段已在 `SysPingVo` 里留好，AUTH-LOGIN-001 接上即可（形状不变）。
- **`buildCommit` 目前是 `unknown`**：已按「读运行期」实现（`${BUILD_COMMIT:${lqg.build-commit:unknown}}`）。
  真值注入属打包/部署（SYS-STAGING-001 / SYS-PROD-001）：构建时 `BUILD_COMMIT=$(git rev-parse HEAD)` 即可。
  本票没有引入打包插件（避免为装饰性字段加构建失败面）。
- **MinIO 的 bucket 没有在 compose 里自动建**：本机 Docker Hub 拉不动新镜像
  （`registry-1.docker.io` token 端点 EOF，`minio/mc` 拉不下来），而 `mc` 又必须先有活着的服务端才能建桶。
  现在 compose 只起服务端；bucket 从 console（127.0.0.1:9003）手建，默认策略就是 private。
  OSS 配置行（`sys_oss_config`）+ 桶名是后续 ticket 的事。**不影响本票任何断言。**
- 本票**没有**建任何 `t_lqg_*` 表、没有 mp 的 client 行、没动 `ruoyi-common-*` / `ruoyi-system` 源码，
  没上生产配置与部署脚本 —— 与 ticket §3 边界一致。

## 7 验证用长进程

- 后端 java：**已关**（`pkill -f ruoyi-admin.jar`，脚本留在 `.tmp/run-backend.sh`，已被 gitignore）。
- docker 容器：**留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），
  留给 D1 后续 ticket（AUTH-LOGIN-001 等）直接用；起停：
  `docker compose -f code/deploy/dev/docker-compose.yml up -d | down`（`down -v` 连数据卷一起删）。
- 未占 8080 / 5432 / 6379 / 9000 / 9001（Kevin 本机日常服务）。

## 8 坑与解法（给下游）

1. **`ps` 命令在本会话沙箱里被禁** → `doc/verify/api.sh --fresh-module` 的进程检查跑不了（exit 2，
   WARN-1）。替代：自己跑那两半（`find … -newer jar` + `lsof -ti tcp:8081` + 进程启动时间）。
   **人工跑（非本 agent 沙箱）不受影响**，下游 QA 直接用原命令即可。
2. **`snail-job` 整段不能删**：`ruoyi-job` 的 `TestBroadcastJob` 有 `@Value("${snail-job.port}")`，
   删了整段即使 `enabled: false` 也会 `PlaceholderResolutionException` 让容器起不来。正确做法是
   `enabled: false` **外加保留 port/group/token/server/namespace**。同理 `spring.boot.admin.client` 关掉要
   留 `enabled` 键（这段没有 `@Value` 依赖，只留 enabled 就够）。
3. **Redis 必须服务端也设口令**：若依的 Redisson 配了口令就会发 `AUTH`；服务端 `--requirepass` 没设时
   AUTH 直接报错（`ERR AUTH <password> called without any password configured`），表现为「后端起不来、
   根因隔着两层」。compose 里 Redis 用 `--requirepass "$$LQG_REDIS_PASSWORD"` 设上，`.env` 同步。
4. **记住软删的唯一性口径**：业务表唯一键一律 `CREATE UNIQUE INDEX … WHERE del_flag = '0'`
   （`gen_ddl_pg.py` 已经这么生成），**不建 `del_unique`、不建 `tenant_id`**；多租户已关，
   `tenant_id` 只存在于若依自带 `sys_*` 表里。
5. **构建（只影响本机/沙箱，不影响交付物）**：本会话里 `~/.m2` 只读且缺 Spring Boot 3.5.10 的 BOM、
   `~/.msp` 写不进去（mapstruct-plus 的增量标记），所以构建要带
   `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`。
   普通开发机不需要这些参数。

## 9 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | harness | `api.sh --fresh-module` 在本 agent 沙箱里 `ps` 被禁，accept 第 2 条原样跑会 exit 2 | 只影响 subagent 沙箱；人工/CI 环境正常。本票用「源码不比 jar 新 + 8081 有后端 + 进程启动晚于 jar」手工替代并记录。方案：`api.sh` 可把 `ps -o lstart=` 换成 `lsof`/`/proc` 兼容写法，或在沙箱里跳过该守卫（改动属验收执行器，本票不改）。 |
| WARN-2 | S3 | clarify | `lqg.auth.mock-login` 配置项在 `doc/verify/README.md` 里被当作 dev/test 前置，但本票没定义该配置项 | 影响 AUTH-LOGIN-001：它落地 mock 登录时需定义该项并在 `application-dev.yml` 打开；`SysPingVo.mockLogin` 已留字段待接。 |
| WARN-3 | S3 | debt | MinIO bucket 需手工在 console 建（本机 Docker Hub 拉不动 `minio/mc`） | 影响 DOC/QC 域要写对象存储的 ticket：起环境时记得建桶（默认私有）。方案：等能拉镜像再加一次性 `mc` 容器，或由后端 `sys_oss_config` + 启动时兜底建桶。 |
| WARN-4 | S3 | doc-drift | ticket 正文写「上游 5.5.x」，实际取 5.5.3；参考项目 dongjiaoshan 是 5.6.1+MySQL | 影响所有照抄上游写法的 ticket：以 `code/RuoYi-Vue-Plus` 里 5.5.3 的真实代码为准（例如 `@AutoMapper`、登录流程、`TestBroadcastJob` 的 `@Value`）。不建议现在升 5.6.x（迁移已完成、基线已落）。 |
| WARN-5 | S3 | clarify | `ruoyi-workflow` 的 Maven 模块仍在上游 pom 里（只 `warm-flow.enabled: false` 关运行） | 影响「不引 ruoyi-workflow」的字面口径：`ruoyi-admin` 依赖树里仍有该模块与 warm-flow jar，但不装配、不建 `flow_*` 表。若要彻底剔除，需改上游两个 pom（属框架层改动），请主会话定是否记 issue。 |
