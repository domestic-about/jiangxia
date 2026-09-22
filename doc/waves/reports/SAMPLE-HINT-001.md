# SAMPLE-HINT-001 · 完工报告

- **ticket**：SAMPLE-HINT-001（track SAMPLE / phase D3 / size S）—— 样本总表的切片染色提示：**读时计算**，工作台一列、小程序表格页一列
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑；唯一改动 = accept 1 去掉本沙箱恒非 0 的 `--fresh-module ruoyi-lqg`，accept 2 的 Maven 行补本机三参数 —— 见 §4.0）
- **分支**：`task/D3`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 09:36:55`，后端进程 **PID 23244**（8081，dev profile + `--api-decrypt.enabled=false`）；plus-ui dev server 8082；小程序 H5 dev server 9200
- **迁移**：**本票不新增 Flyway**（`touches` 里没有迁移；读时计算不建表、不加列、不加菜单）—— 库里仍是 14 支，最大 `V202609231110`
- **单测**：Java `Tests run: 112, Failures: 0, Errors: 0, Skipped: 0`（含本票新增 `SampleHintContractTest` 9 例）；小程序 vitest 本票新增 `sheets.spec.ts` **8/8**
- **回归**：`EMBED-MODEL-001` acc1/2 ✅ · `EMBED-WEB-001` acc1/2 ✅ · `SAMPLE-WEB-001` acc1 ✅ · `SAMPLE-MP-002` acc2 ✅ · `SAMPLE-EXPORT-001` acc1 ✅ · `ExtChokepointContractTest` 4/4 ✅ · D1 回归包 **8 绿 / 1 红**（唯一那条红 = **既有** harness 缺陷：D1 把 flyway 迁移总数写死成 7）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,ddl_vs_ssot.py,seed/**,gen_seed.py,fixtures/**,xlsx_header.py}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/api-contract.md`、`doc/lint-profile.yaml`、`doc/waves/state.json`、`_manifest.json`、`doc/waves/regression/**`
- **没碰**：8080（Kevin 的本机服务）/ 5432 / 6379；关进程一律按 `lsof -ti tcp:<端口> -sTCP:LISTEN` 拿 PID 再 `kill`（**从没用过 `pkill -f`**，`nohup &` 也一次没用）
- **产物**：后端 5 个新类 + 2 个既有文件小改 + 1 个既有测试的构造器修补；前端 1 个新组件 + 1 个页面接线 + 2 本 i18n + 1 个 api 类型；小程序 1 个文件改 + 1 个新 spec

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D3`；全程未切分支、未 push、未 merge |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `state.json`：`EMBED-MODEL-001` / `SAMPLE-WEB-001` / `SAMPLE-MP-002` 都 `done`；`EmbedQueryService`（`stain_types` 的逗号串 + `section_time`）、`views/lqg/sample/index.vue`、`pages/ledger/sheets.ts` 的「切片染色」预留列全部在盘且被本票真调用 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | 两条**逐条落进实现**：**CR-20260918-07**「工作台首页五张卡片…**切片染色提示只数已核验有效的石蜡块**」→ 聚合 SQL 叠 `verify_status='valid'`（accept 1 的 1002 = `[0,false,[]]`）；**CR-20260917-05**「小程序放查看、筛选、导出」+「小程序的切片染色提示改成表格页一列」→ 小程序**只在表格页最后一列**渲染，没做小程序内的提示入口。CR 覆盖 ticket 正文，两条都以 CR 为准 |
| 5 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `FLOW:F-SAMPLE-02.step4`（「读时计算…NONE 不计…没有包埋记录显示「—」」）· `UI:admin.sample.list.hint`（徽标组 + 悬停列编号与切片时间 + 点击跳包埋页 + 读时计算不可编辑）· `FIELD:t_lqg_embed.{section_time,stain_types,verify_status}` 全部 `active`；逐条落点见 §1 |
| 口径复述 4 条（读时算 / 软删与 NONE 与待核验 / 一页一查 / 只数已核验有效） | ✅ PASS | §1 逐条 + 机器证据：accept 1 的五段期望、`SampleHintContractTest` 9 例、counterfeit 探针 7 条 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | `docker ps` 三个容器全程在跑，未停；**没碰** 8080 / 5432 / 6379 |
| 动手前代码是绿的 | ✅ PASS | 开工基线 `Tests run: 98, Failures: 0`（EMBED-WEB-001 收口值）；本票后 **112**（+9 本票，其余 +5 是同期已 done 的 SAMPLE-EXPORT-001） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

## §1 口径复述（逐条对 accept 核）

| # | 口径（ticket §0/§2 + 权威锚 + CR） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **读时计算，不落库**：`SampleHintService.hintsOf(Collection<Long>)` → `Map<Long, HintVo{blockCount, sectioned, stains}>`，一条 GROUP BY | `sample/hint/{SampleHintService,SampleHintMapper,StainHintRules}.java` + `sample/hint/vo/{SampleHintVo,SampleHintRow}.java` | accept 2 段 1 `ddl_vs_ssot`（样本表多一列就红）；counterfeit ⑥ `information_schema` 里 `block_count/has_section/...` = 0 |
| 2 | **计数口径与 `EmbedChildrenChecker` 同源**：`e.del_flag='0' AND e.verify_status='valid'`，再 join 所挂样本 `s.del_flag='0'` | `SampleHintMapper.selectHints` 的 `@Select` | 后端 p6spy 原文（§4.3(a)）逐字含三道守卫；accept 1 段 4/5（1008 软删 → 0；1002 待核验 → 0） |
| 3 | **`NONE` 不是一种染色**：并集拆开去掉 `NONE`、去重、按字典顺序 | `StainHintRules.union`（纯函数） | accept 1 段 2（1004 = `[1,true,[]]`）+ 单测 4 例 + counterfeit ③（库里是 `NONE`、接口给 `[]`） |
| 4 | **没有包埋记录的行也有 hint（零值，不是 null）** | `SampleHintService.hintsOf` 先给每个 id 铺 `SampleHintVo.empty()`；`SampleQueryService.fillHints` 再 `getOrDefault` 兜一层 | accept 1 段 6（整页 `hint == null` 的行 = 0）；counterfeit ④（两条读路径都断） |
| 5 | **一页只发一次聚合查询** | `hintsOf` 收整页 id 集合 → 一个 `IN` + 一条 `GROUP BY` | 单测「传 20 个样本 id 只触发 1 次查询」（mapper spy 数调用次数）；counterfeit ⑤（真日志：一次 9 行的请求只出现 1 条聚合 SQL） |
| 6 | **两侧同源**：`/lqg/sample/list` 与 `/mp/int/sample/list` 每行挂同一个 `hint` | 两个端点**共用** `SampleQueryService.list`（`MpSampleService.list` 只是转发） | accept 1 段 8；counterfeit ⑦（两条读路径逐行 `hint` 相等） |
| 7 | **工作台提示组件**：徽标「石蜡块 N」「已切片」+ 染色缩写；悬停列各石蜡块编号与切片时间（悬停时再查）；点击带 `sampleId` 跳石蜡包埋页；没有包埋记录显示「—」 | `views/lqg/sample/HintBadges.vue` + `index.vue` 一列 | §4.4 的 DOM 证据（表头 24 列、`切片染色` 在操作人之后；悬停浮层 `["T-E01-2 —","T-E01-1 2026-08-27"]`；点击 → `/embed?sampleId=9000001001` 两行） |
| 8 | **小程序表格页最后一列「切片染色」**（`tissue` + `organoid` 两个工作表） | `pages/ledger/sheets.ts` 的 `stainHintText` + `toTableRows` | vitest 8/8；§4.5 端侧：两个表头最后一列都是「切片染色」，DOM 最后一格与接口 `hint` 逐行相等（`mismatches = []`） |
| 9 | **明确不做**（ticket §3）：外部列表不加提示；不统计冻存 / 质控文档；不做成可筛选条件 | —— | 本票没有碰 `/mp/ext/**`、没有给 `SampleQueryBo` 加任何筛选、没有做冻存与文档的计数 |

---

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway / 取号

**本票不新增迁移。** 依据：`touches` 三处里没有 `db/migration/**`；本票读时计算（不建表、不加列、不加菜单、不需要权限串）—— `t_lqg_sample` 与 `t_lqg_embed` 的表结构由 SAMPLE-MODEL-001 / EMBED-MODEL-001 落好，列表端点与菜单由 SAMPLE-WEB-001 / EMBED-WEB-001 落好。库里仍是 14 支，最大 `V202609231110`。实测后端启动：`Successfully validated 14 migrations`。

### 2.2 后端 · 新增（`org.dromara.lqg.sample.hint`，**在 `touches` 内**）

| 文件 | 职责 |
|---|---|
| `hint/StainHintRules.java` | **纯函数** `union(String stainCsv)`：拆逗号 → 去空白/空元素 → **去 `NONE`** → 去重 → 字典序（`TreeSet`）。写侧的口径在 `embed.guard.StainRules`，这是**读侧**的并集，刻意分开（写侧要拦非法输入，读侧只对已落库的值做并集） |
| `hint/vo/SampleHintVo.java` | 对外形状 `{blockCount, sectioned, stains:[…]}` + `empty()` 工厂（零值口径只此一份） |
| `hint/vo/SampleHintRow.java` | 一条 GROUP BY 的原始行 `{sampleId, blockCount, sectioned, stainCsv}`（染色在 SQL 里还是逗号串，拆开交给纯函数） |
| `hint/mapper/SampleHintMapper.java` | **唯一一条 SQL**：`@Select` 的 `<script>`，`JOIN t_lqg_sample s ON s.id=e.sample_id AND s.del_flag='0'` + `e.del_flag='0'` + `e.verify_status='valid'` + `foreach` 一个 `IN` + `GROUP BY`；`BOOL_OR(section_time IS NOT NULL)`、`STRING_AGG(stain_types, ',')` |
| `hint/SampleHintService.java` | `hintsOf(Collection<Long>)`：洗 id（去 null / 去重）→ **每个 id 先铺零值** → 一条查询 → 覆盖真值；另给一行口 `hintOf(Long)`（详情用，列表**不许**用） |

★ **包名以 `.mapper` 结尾**：若依的 mapper 扫描路径是 `org.dromara.**.mapper`（`application.yml` 的 `mybatis-plus.mapperPackage`），只写到 `sample.hint` 会起不来（SAMPLE-WEB-001 踩过同型坑）。

### 2.3 后端 · 修改（**越出 `touches`**，逐条见 §5.1）

| 文件 | 改动 | 为什么非改不可 |
|---|---|---|
| `sample/domain/vo/SampleVo.java` | +`hint` 字段（`SampleHintVo`，带口径注释） | 列表行的类型就是 `SampleVo`（`TableDataInfo<SampleVo>`）；`hint` 不挂在它上面就没有第二个地方可挂 |
| `sample/service/SampleQueryService.java` | +`SampleHintService` 构造器依赖 + 私有 `fillHints(List<SampleVo>)`；`list()` 里在 `submitterProfileQuery.fill(rows)` 之后调一次 | 两个端点（`/lqg/sample/list`、`/mp/int/sample/list`）**共用这一个** `list()`；挂在这里两侧同时生效、口径只有一份（挂到 controller 就是两份） |
| `sample/service/SampleRecentFilterContractTest.java` | `new SampleQueryService(...)` 补第 5 个 `null` 实参 | 构造器多了一个依赖 → 不补编译不过（D2 的 issue #105 回归测试，本类不碰提示） |

**没有改签名**：`EmbedQueryService` / `EmbedService` / `EmbedChildrenChecker` / 三个 embed 纯函数 guard / 两个 mapper **一个字节没动**；`SampleService` / `SampleSubmitterProfileQuery` / `MpSampleService` / `MpSampleController` 也没动。

### 2.4 接口清单（本票**不新增端点**）

```
GET /lqg/sample/list      每行多一个 hint:{blockCount, sectioned, stains:[…]}   （契约第 45 行早已写明）
GET /mp/int/sample/list   同上（同一份装配）
```
两个端点都是既有端点，本票只往行上加一个键 —— 与 `doc/api-contract.md` 第 45 / 49 行**逐字对齐，无 doc-drift**。

### 2.5 前端 · 新增 / 修改（`code/plus-ui`）

| 文件 | 内容 |
|---|---|
| `src/views/lqg/sample/HintBadges.vue`（新） | 徽标组「石蜡块 N」「已切片」+ 染色缩写（`HE/IF/IHC/其他`，字典外原值带出）；`el-popover trigger="hover"` 的 `@show` **悬停时再查** `/lqg/embed/list?sampleId=&verifyStatus=valid`（与徽标同源），浮层两列「石蜡块编号 / 切片时间」；点徽标 → `router.push('/embed?sampleId=…')`；`blockCount===0` 显示「—」。零颜色字面量（全 `var(--lqg-*)`） |
| `src/views/lqg/sample/index.vue` | +一列「切片染色」（`width=200`），位置在**操作人之后**（权威 `UI:admin.sample.list` 的列序里它就在操作人与备注之间）+ `import HintBadges from './HintBadges.vue'` |
| `src/lang/lqg/sample.zh_CN.ts` / `sample.en_US.ts` | +`col.hint` + 一组 `hint.*`（9 个 key，两边 key 集合一致）；**没往上游 `zh_CN.ts` / `en_US.ts` 加一个 key** |
| `src/api/lqg/sample/index.ts`（**越出 `touches`**） | +`SampleHintVO` 类型 + `SampleVO.hint` |

### 2.6 小程序 · 修改（`code/miniapp`）

| 文件 | 内容 |
|---|---|
| `src/pages/ledger/sheets.ts`（**在 `touches` 内**） | +`stainHintText(row)`（纯函数）：`blockCount<=0` → `—`；否则「石蜡块 N · 已切片 · HE / IHC」（与工作台徽标同一口径）→ 在 `toTableRows` 里注入 `stainHint` 再交给 `ledgerCellText`（**列名/列序仍只从 `columns.ts` 来**；`api/ledger.ts` 一个字没改） |
| `src/pages/ledger/sheets.spec.ts`（新，**越出 `touches`**） | 8 例：seed 的五个期望 + 有块未切片 + 字典外值 + 两个工作表的最后一格 |

---

## §3 关键设计取舍（本票自己定的两处）

1. **悬停浮层只列「已核验有效」的块**（查 `verifyStatus=valid`）：权威只说「悬停列出各石蜡块编号与切片时间」。待核验的外部送样**没有石蜡块编号**、也不算进徽标的块数，列进来会出现「徽标写 2 块、浮层 3 行（其中一行编号是 `—`）」的对不上。取舍记 WARN-2。
2. **`hint` 只挂 list 的行**，`GET /lqg/sample/{id}` 详情与两张 Excel 导出**不带**：契约第 45 行把 `hint` 只写在 list 的行上，而导出用的是各域自己的 `*ExportVo`（多一个键就多一列）。`SampleHintService.hintOf(Long)` 已经备好，详情要的时候一行接上。取舍记 WARN-3。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 两处差异（都能单独复现）

**(a) `--fresh-module ruoyi-lqg`（既有 WARN，本沙箱恒非 0）** —— `api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted`，回退的 `date -d` 在 macOS 上不认。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的**两个半边**：

```
jar mtime      : 2026-09-22 09:36:55（epoch 1790041015）
lqg src newer  : （空）      ← find ruoyi-lqg/src -type f -newer <jar> 一个文件都没有
8081 PID       : 23244       （lsof -ti tcp:8081 -sTCP:LISTEN）
PID holds jar  : 2           （lsof -p 23244 | grep -c 'ruoyi-admin/target/ruoyi-admin.jar'）
进程启动 ≥ jar  : True        （libproc.proc_pidinfo：启动 1790041027 / 09:37:07 ≥ jar 1790041015）
后端日志       : 09:37:0x "The following 1 profile is active: dev" → "Successfully validated 14 migrations"
                 → "Started DromaraApplication in 6.145 seconds"
嵌套 jar 复核   : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含
                 org/dromara/lqg/sample/hint/{SampleHintService,StainHintRules}.class、
                 .../hint/mapper/SampleHintMapper.class、.../hint/vo/{SampleHintVo,SampleHintRow}.class
```

**(b) accept 2 的 Maven 行补本机三参数** `-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`（离线仓库与 settings 都在工作区里；沙箱不读 `~/.m2`）。accept 2 的 `run` 里**本来就没有** `--fresh-module`。

两条 accept 的 `run` 都是长链，**包进函数判整条 rc**（`set -e` 不管非末尾位置的失败 → 会静默短路 exit 0）。runner 在 `accept-runners/`，开头都 `rm -f $TMPDIR/lqg-verify-token-*`。
★ 四条 accept / 探针**串行**跑（各自 reseed 首尾，并行会互相打断）。

### 4.1 accept 1 · DATA —— ✅

```
$ bash doc/waves/reports/SAMPLE-HINT-001/accept-runners/hint001-acc1.sh
########## SAMPLE-HINT-001 · accept 1（DATA）##########
（差异：去掉 --fresh-module ruoyi-lqg）
---- 1) 1001 两块、一块已切片 HE+IHC
[2,true,["HE","IHC"]]
---- 2) 1004 一块已切片、无染色（NONE 不是一种染色）
[1,true,[]]
---- 3) 1006 一块已切片、OTHER
[1,true,["OTHER"]]
---- 4) 1008 名下唯一那块是软删 → 零值
[0,false,[]]
---- 5) 1002 名下只有待核验的外部送样 → 零值
[0,false,[]]
---- 6) 没有包埋记录的行 hint 也不许是 null（整页扫一遍）
true
---- 7) 各行块数之和 == 直连库的独立 count（两侧不同源）
db=4 sum=4
4
---- 8) /mp/int/sample/list 同样带 hint
true
---- 9) 收尾 reseed
ACCEPT-1 EXIT=0
```

原文 8 段逐字对应（本 runner 把 ticket 里那行长链拆成带回显的 8 段；断言表达式一字未改）：

| 段 | ticket 原文 | 真实输出 | 说明 |
|---|---|---|---|
| 1-5 | `test "$(h 9000001001)" = '[2,true,["HE","IHC"]]'` … | 五个期望逐字命中 | ★ 1001 两块一已切片 HE+IHC / 1004 `[1,true,[]]`（NONE 不算染色）/ 1006 `OTHER` / 1008 `[0,false,[]]`（软删）/ 1002 `[0,false,[]]`（待核验） |
| 6 | （追加）整页没有 `hint == null` 的行 | `true` | 没有包埋记录的行也有零值（1002 / 1003 / 1005 / 1007 这些） |
| 7 | `python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id AND s.del_flag='0' WHERE e.del_flag='0' AND e.verify_status='valid'" --eq "$(… jq '[.rows[].hint.blockCount]|add')"` | `db=4 sum=4` → `4` | ★ 两侧不同源：一侧是接口逐行返回的块数求和，一侧是直连库的独立 count |
| 8 | `bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' \| jq -e '[.rows[]\|select((.id\|tostring)=="9000001001")\|.hint.blockCount]==[2]'` | `true` | 小程序内部接口同样带 `hint` |

### 4.2 accept 2 · DDL / 接线 / 一页一查 —— ✅

```
$ bash doc/waves/reports/SAMPLE-HINT-001/accept-runners/hint001-acc2.sh
########## SAMPLE-HINT-001 · accept 2（DDL / 接线 / 一页一查）##########
（差异：Maven 补三参数；本票 run 里没有 --fresh-module）
---- 1) 样本表上没有被偷加的冗余列（ddl_vs_ssot 逐列相符）
✓ 1 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）
---- 2) 工作台总表已接入提示组件
---- 3) 契约测试（含「传 20 个样本 id 只触发 1 次查询」）
ACCEPT-2 EXIT=0
```

| 段 | ticket 原文 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `ddl_vs_ssot.py --table t_lqg_sample --require-public create_dept,create_by,create_time,update_by,update_time,del_flag` | `✓ 1 张表与 SSOT 逐列相符` | ★ 读时计算：`t_lqg_sample` 上**没有** `block_count` / `has_section` 之类冗余列（多一列就红） |
| 2 | `grep -q 'HintBadges' …/index.vue && test -f …/HintBadges.vue` | 命中 | 工作台总表已接入 |
| 3 | `mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='SampleHint*Test' -Dsurefire.failIfNoSpecifiedTests=true` | rc=0（`Tests run: 9, Failures: 0`） | ★ 其中一条就是 counterfeit 逐字要求的「传 20 个样本 id 只触发 1 次查询」（mapper spy 数调用次数；逐行查会数出 20） |

**counterfeit 逐条排掉**（完整转录：`accept-runners/counterfeit.out`；探针脚本 `hint001-counterfeit.sh`）：

```
== ① 不加 del_flag 守卫：1008 名下会数出 1 块（那块 2005 是软删的） ==
1                                       ← 病灶形态
   正确口径（叠上 del_flag='0'）：
0
== ② 不加 verify_status 守卫：1002 名下会数出 1 块（那 2006 还在待核验） ==
1                                       ← 病灶形态
   正确口径（叠上 verify_status='valid'）：
0
== ③ 不拆 NONE：1004 那一块的 stain_types 原样是「NONE」 ==
NONE                                    ← 病灶形态（做成 ["NONE"] 就红）
   接口给的是（应为空数组）：
[]
== ④ 每一行都有 hint 对象（含没有包埋记录的行），没有一个是 null ==
true / true                             （/lqg 与 /mp/int 两条读路径都断）
== ⑤ 一页只发一次聚合查询：一次请求在前端日志里只出现 1 条那条 SQL ==
   一次 /lqg/sample/list?pageSize=100（9 行）触发的聚合 SQL 条数 = 1
   一次 /mp/int/sample/list?pageSize=100（9 行）触发的聚合 SQL 条数 = 1
== ⑥ 样本表上没有被偷加的冗余列 ==
0
== ⑦ 两条读路径同源：/lqg/sample/list 与 /mp/int/sample/list 的 hint 逐行相等 ==
   true（两侧逐行一致）
COUNTERFEIT PROBES EXIT=0
```

| counterfeit（ticket 原文） | 本实现为什么不中 |
|---|---|
| 聚合 SQL 手写、忘了 `e.del_flag='0'` → 1008 名下那块软删的石蜡块被数进来 | 探针 ① 实测「不加就是 1、加了是 0」；accept 1 段 4 的 `[0,false,[]]` |
| 把 `NONE` 当成一种染色 → 1004 变成 `["NONE"]` 红 | 探针 ③：库里的值原样是 `NONE`，接口给 `[]`；`StainHintRules.union` 4 例 + accept 1 段 2 |
| 没有包埋记录的行 `hint` 为 `null` → 前端 `undefined.blockCount` | 探针 ④：两条读路径整页扫，`type != "object"` 的行 = 0 |
| 把外部提交还没核验的送样也数成一块 → 1002 变成 `[1,false,[]]` 红 | 探针 ②：不加 `verify_status` 就是 1、加了是 0；accept 1 段 5 |
| 两侧不同源：一侧是接口逐行求和、一侧是直连库 count | accept 1 段 7（`db=4 sum=4`）+ 探针 ⑦（两条读路径逐行相等） |
| 为了列表快在 `t_lqg_sample` 上加 `block_count` / `has_section` 并回写 | accept 2 段 1 `ddl_vs_ssot` + 探针 ⑥（`information_schema` 里那五个列名 count = 0） |
| 单测里要有一条「传 20 个样本 id 只触发 1 次查询」（逐行查过不了） | `SampleHintContractTest.twentySampleIdsTriggerExactlyOneQuery`：`assertEquals(1, mapper.calls)` + 那一次 `asked` = 全部 20 个 id |

### 4.3 追加证据（accept 之外的机器证据）

**(a) ★ 落到库上的那条 SQL（后端 p6spy 原文，一次 `/lqg/sample/list?pageSize=100`）**

```
SELECT e.sample_id                         AS "sampleId",
       COUNT(*)                            AS "blockCount",
       BOOL_OR(e.section_time IS NOT NULL) AS "sectioned",
       STRING_AGG(e.stain_types, ',')      AS "stainCsv"
FROM t_lqg_embed e
JOIN t_lqg_sample s ON s.id = e.sample_id AND s.del_flag = '0'
WHERE e.del_flag = '0'
  AND e.verify_status = 'valid'
  AND e.sample_id IN ( 9000001007 , 9000001002 , 9000001003 , 9000001006 , 9000001005 , 9000001004 , 9000001001 , 9000001008 , 9000001009 )
GROUP BY e.sample_id
```

★ 整页 **9 个 id 挤在同一个 `IN`** 里 —— 「一页一次」是机器可读的事实，不是注释里的承诺。

**(b) 原始行（`--as staff GET /lqg/sample/list?pageSize=100` 节选）**

```json
{"id":9000001001,"internalNo":"T-hli01","hint":{"blockCount":2,"sectioned":true,"stains":["HE","IHC"]}}
{"id":9000001004,"internalNo":"T-hli02","hint":{"blockCount":1,"sectioned":true,"stains":[]}}
{"id":9000001006,"internalNo":"T-hco04","hint":{"blockCount":1,"sectioned":true,"stains":["OTHER"]}}
{"id":9000001002,"internalNo":null,      "hint":{"blockCount":0,"sectioned":false,"stains":[]}}
{"id":9000001008,"internalNo":"T-hli05","hint":{"blockCount":0,"sectioned":false,"stains":[]}}
```

**(c) Java 单测：`Tests run: 112, Failures: 0, Errors: 0, Skipped: 0`（本票 +9；开工基线 98，其余 +5 是同期已 done 的 SAMPLE-EXPORT-001）**

```
[INFO] Running org.dromara.lqg.sample.hint.SampleHintContractTest            Tests run:  9, Failures: 0   ← 本票新增
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest                 Tests run:  4, Failures: 0   ← D2 的 L0 咽喉门仍绿
[INFO] Running org.dromara.lqg.sample.service.SampleRecentFilterContractTest Tests run:  6, Failures: 0   ← 构造器补参后仍绿
[INFO] Running org.dromara.lqg.sample.query.SampleTableQueryContractTest     Tests run:  9, Failures: 0
[INFO] Running org.dromara.lqg.sample.export.SampleExportContractTest        Tests run:  5, Failures: 0
[INFO] Tests run: 112, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**(d) 小程序 vitest：本票新增 `sheets.spec.ts` 8/8**

```
$ npm_config_store_dir=<ws>/.pnpm-store pnpm vitest run src/pages/ledger/sheets.spec.ts --reporter=json
{"numTotalTests":8,"numPassedTests":8,"numFailedTests":0}
```

**(e) plus-ui 生产构建：`✓ built in 7.94s`（rc=0）**，产物里真有这个组件：
`dist/assets/HintBadges-1CEPcFmp.js`（含 `lqg-hint__badge`）、`dist/assets/index-DU_B2mfi.js`（含 i18n 「切片染色」「已切片」「石蜡块」）。

**(f) DOM / 样式证据（截图只落盘，全程没有把任何 PNG 读进上下文）**

| 文件 | 覆盖项 | 实测断言输出（`probe-*.json`） |
|---|---|---|
| `01-sample-list-hint-column.png` | 工作台总表「切片染色」列 | `headerCount: 24`、`hintIndex: 20`、`hintHeader: "切片染色"`；逐行徽标：`T-hli01 → ["石蜡块 2","已切片","HE","IHC"]`、`T-hli02 → ["石蜡块 1","已切片"]`、`T-hco04 → ["石蜡块 1","已切片","其他"]`、`T-hli05 → —`（无徽标）；「已切片」徽标底色 `rgb(223, 241, 230)` = `--lqg-ok-soft` |
| `02-hint-hover-blocks.png` | 悬停 1001 的徽标 → 浮层 | `{"found":true,"visible":true,"header":["石蜡块编号","切片时间"],"rows":["T-E01-2 —","T-E01-1 2026-08-27"]}` |
| `03-hint-click-to-embed.png` | 点击 → 石蜡包埋页并按样本过滤 | `url=http://127.0.0.1:8082/embed?sampleId=9000001001`、`hasEmbedPage=true`、`filterTag=["只看样本 9000001001 的包埋记录",…]`、`rowCount=2` |
| `04-mp-ledger-tissue-stain-hint.png` | 小程序 · 样本记录（最后一列） | 表头最后一列 `"切片染色"`；各行最后一格 `["SJ90000007","—"],["SJ90000002","—"],["SJ90000003","—"],["T-hco04","石蜡块 1 · 已切片 · 其他"],["T-hga03","—"],["T-hli02","石蜡块 1 · 已切片"],["T-hli01","石蜡块 2 · 已切片 · HE / IHC"],["T-hli05","—"]` |
| `05-mp-ledger-organoid-stain-hint.png` | 小程序 · 类器官收样（最后一列） | 表头最后一列 `"切片染色"`；`[["T-oco01","—"]]` |
| `probe-mp-stain-hint.json` | 两侧对账 | 小程序 DOM 最后一格 vs `/mp/int/sample/list` 的 `hint` 逐行相等 → `mismatches: []` |

> 截图 / DOM 探针脚本：`accept-runners/hint001-shots.mjs`（工作台，需要放在有 `puppeteer-core` 的目录里跑，例如 `cp` 到 `/tmp/shots/`）、`accept-runners/hint001-mp-shots.mjs`（小程序 H5，`cp` 到 `code/miniapp/` 后 `node`）。**微信开发者工具 / 真机未覆盖**（沙箱跑不通，同 SYS-MP-001 / SAMPLE-MP-001 / SAMPLE-MP-002 的既有 WARN），端侧证据用 H5 dev（9200，`VITE_MOCK_LOGIN=1`）+ Playwright 覆盖，如实写明不用 H5 冒充真机。

### 4.4 回归自查（串行；runner：`accept-runners/hint001-regression.sh`）

```
==================== java-full-test ====================   GREEN
==================== embed001-acc1 ====================    GREEN
==================== embed001-acc2 ====================    GREEN
==================== embedweb001-acc1 ==================== GREEN
==================== embedweb001-acc2 ==================== GREEN
==================== web001-acc1 ====================      GREEN
==================== mp002-acc2 ====================       GREEN
==================== sampleexp001-acc1 ==================== GREEN
==================== d1-verify ====================        RED（既有 harness 缺陷）

==================== 汇总 ====================
green=8 red=1
  - d1-verify
```

| 回归 | 结果 |
|---|---|
| `EMBED-MODEL-001` accept 1 | ✅ `ACCEPT-1 EXIT=0`（`✓ 2 张表与 SSOT 逐列相符`） |
| `EMBED-MODEL-001` accept 2 | ✅ `ACCEPT-2 EXIT=0`（15 段状态机逐段绿） |
| `EMBED-WEB-001` accept 1 | ✅ `ACCEPT-1 EXIT=0`（六段导出表头逐字） |
| `EMBED-WEB-001` accept 2 | ✅ `ACCEPT-2 EXIT=0`（5310 段菜单 + vitest 19/19） |
| `SAMPLE-WEB-001` accept 1 | ✅ `ACCEPT-1 GREEN (exit 0)`（10 段；★ 不含段 4 的已知规格冲突——那条在 accept 2 里，见下） |
| `SAMPLE-MP-002` accept 2 | ✅ `ACCEPT-2 EXIT=0`（两段 true：构建产物 + 7 段 grep + vitest + 四份 xlsx 逐字 diff） |
| `SAMPLE-EXPORT-001` accept 1 | ✅ 全绿（两张导出与模板原件逐字） |
| `ExtChokepointContractTest` | ✅ `Tests run: 4, Failures: 0`（含在 java-full-test 里） |
| D1 回归包 `verify.sh --skip-build` | ⚠️ **1 红**：`L1.1 D1 的 7 支迁移全部记录在案 → [FAIL] 期望 '7'，实际 '14'` —— **既有 harness 假红**（把迁移总数写死成 7；D1 的 7 支逐个点名全绿、`success IS NOT TRUE` 的行 = 0）。**本票不新增迁移**，所以这条红与本票无关，不重复计数。`L0.2 新鲜度` 那条（EMBED-WEB-001 报告里的 SIGPIPE 假红）**本次实测是绿的**：`✓ 源码不新于 jar；pid 23244 持有该 jar；进程启动 09:37:07 ≥ jar 09:36:55` |

> ★ `SAMPLE-WEB-001` **accept 2 段 4** 的已知规格冲突（issue #129，与导出权限互斥）本票**没跑那一条**（派单只要求 acc1）；如实注明，不是本票引入的问题。
> ★ 本票最容易打红别人的地方是 `SampleQueryService` 的构造器：多了一个依赖 → D2 的 `SampleRecentFilterContractTest` 编译不过；已按「只补一个实参」修好，`Tests run: 6, Failures: 0` 仍绿。

---

## §5 遗留与 raise（ticket §4.3）

### 5.1 ★ 越出 `touches` 的改动（WARN，逐条列清）

`touches` 只列了：`sample/hint/src/{main,test}/**`、`plus-ui/src/views/lqg/sample/{index.vue,HintBadges.vue}`、`miniapp/src/pages/ledger/sheets.ts`。**实际越出 5 处**（全为新增调用 / 新增字段 / 新增类型，**没有改任何既有方法签名**）：

| # | 文件 | 为什么非改不可 | 风险 |
|---|---|---|---|
| 1 | `sample/domain/vo/SampleVo.java` | 列表行的类型就是 `SampleVo`；`hint` 不挂它上面就没有别处可挂（`TableDataInfo<SampleVo>` 是端点签名） | 极低：纯新增一个字段 + 一个 import |
| 2 | `sample/service/SampleQueryService.java` | `/lqg/sample/list` 与 `/mp/int/sample/list` **共用这一个** `list()`；只加一个构造器依赖 + 一个私有 `fillHints`（在整页上调用一次） | 低：唯一可观测的副作用是构造器多一参（见 #3）；`Tests run: 112` 全绿 |
| 3 | `sample/service/SampleRecentFilterContractTest.java`（**测试**） | #2 让构造器从 4 参变 5 参 → 不补编译不过（D2 issue #105 的回归测试） | 极低：补一个 `null` 实参 + 一行注释，断言一字未改 |
| 4 | `plus-ui/src/api/lqg/sample/index.ts` | `SampleVO.hint` 的类型来源；不补就得在组件里 `as any`，类型反而更脏 | 极低：纯新增 `SampleHintVO` + 一个可选字段 |
| 5 | `miniapp/src/pages/ledger/sheets.spec.ts`（**新测试**） | 把「切片染色」这一列的 5 条 seed 期望钉在单测里（accept 只钉后端；小程序侧原先没有任何测试） | 零：新增 spec，8/8 绿 |

另：`plus-ui/src/lang/lqg/sample.{zh_CN,en_US}.ts` 是派单**明确允许**的路径（「`plus-ui/src/lang/lqg/sample.*.ts`（若需）」），不计越界。
**刻意没碰**：`code/miniapp/src/api/ledger.ts`（渲染逻辑放在 `touches` 内的 `sheets.ts`，`LedgerRow` 的索引签名足够读 `row.hint`）、`MpSampleController` / `MpSampleService` / `SampleController`（装配在 service 层就够了两条路径）。

### 5.2 与 `doc/api-contract.md` 的差异

**没有差异。** 契约第 45 行已经写着 `行内带 hint:{blockCount, sectioned, stains:[…]}`，第 49 行写着 `list 的行 = 该工作表的全部模板列 + hint` —— 本票逐字实现（键名、形状、位置全对）。本票**不改契约**，无 doc-drift。

**明确没做（ticket §3 边界逐条核过）**：不给外部列表加提示（`/mp/ext/**` 一个字节没动）｜不统计冻存、不统计质控文档｜不做成可筛选条件（`SampleQueryBo` 一个字段没加）｜不做石蜡包埋页本身、不做导出、不做小程序包埋填写页（各自的票）。

### 5.3 没把握 / 需要确认的口径

1. **悬停浮层只列已核验有效的块**（查 `verifyStatus=valid`）。若甲方/后续票希望浮层把「外部提交还没核验的送样」也列出来（好让内部人员知道有人在等核验），去掉组件里那个 `verifyStatus: 'valid'` 即可 —— 但那时浮层行数会**多于**徽标上的块数（待核验的没有石蜡块编号，会显示 `—`）。**取舍需要拍一下** → WARN-2。
2. **`hint` 只在 list 的行上**，详情 `GET /lqg/sample/{id}` 与两张导出不带。契约只把 `hint` 写在 list 上；若后续票要在详情页也显示，`SampleQueryService.detail()` 里加一句 `vo.setHint(sampleHintService.hintOf(id))` 即可（多一次单行查询）→ WARN-3。
3. **徽标文案是「石蜡块 N · 已切片 · HE / IHC」**（工作台徽标组 / 小程序一行小字），染色缩写按 `UI:admin.sample.list.hint` 的「HE / IF / IHC / 其他」；字典外的历史脏值按原值带出（不吞）。若甲方希望小程序里也是徽标样式（而不是一行文字），那要动 `LedgerTable.vue`（哑组件的 cells 目前是纯字符串矩阵，本票没碰它）。

---

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | ticket-drift | **ticket 的 `touches` 与实现不符**：要给列表行挂 `hint` 就必须动 `sample/domain/vo/SampleVo.java`（+字段）与 `sample/service/SampleQueryService.java`（+依赖 +整页装配），再连带修 `SampleRecentFilterContractTest` 的构造器；前端要动 `plus-ui/src/api/lqg/sample/index.ts`（类型） | 五处全为**新增**（字段 / 依赖 / 私有方法 / 类型 / 新 spec），无签名变更，已在 §5.1 逐条列清并在 §2.3 给出理由。方案：ticket 生成器把「读侧装配文件 + 行 VO + 其类型定义」也写进 `touches`（SAMPLE-WEB-001 WARN-1、EMBED-WEB-001 WARN-1、AUTH-EXT-001、SAMPLE-MP-001、SAMPLE-VERIFY-001 已**六次**同型命中）。 |
| WARN-2 | S3 | clarify | **悬停浮层要不要含「待核验的外部送样」**：权威只写「悬停列出各石蜡块编号与切片时间」 | 本实现取**只列已核验有效的块**（与徽标上的块数同源：徽标 2 块 → 浮层 2 行）；待核验的送样没有石蜡块编号、也不算块，列进来会出现行数对不上。若甲方要「顺便看到有人在等核验」，去掉 `HintBadges.vue` 里 `verifyStatus: 'valid'` 一个参数即可（那时浮层语义变成「这个样本的全部送样」）。 |
| WARN-3 | S3 | clarify | **`hint` 只挂 list 的行**，`GET /lqg/sample/{id}` 与两张 Excel 导出不带 | 依据是 `doc/api-contract.md` 第 45 / 49 行只把 `hint` 写在 list 上，且导出用的是各域自己的 `*ExportVo`（多一个键就多一列）。`SampleHintService.hintOf(Long)` 已备好，详情要显示时接一行。方案：若要详情也带，在契约/票面里写明。 |
| WARN-4 | S3 | harness（既有，**本票不重复计数**） | **D1 回归包把 flyway 迁移总数写死成 7** —— 现在是 14，那条断言恒假红 | 取证见 §4.4：D1 的 7 支逐个点名全绿、`success IS NOT TRUE` 的行 = 0；红的是 `--eq 7`。state.json 已记（D2 r1 首次命中）。**本票没改回归包**（`doc/waves/regression/**` 不在 `touches`）。 |
| WARN-5 | S3 | debt | **小程序侧「切片染色」是纯文本一行小字，不是徽标** | `LedgerTable.vue` 的 `cells` 是纯字符串矩阵（哑组件、无函数 prop），要在小程序里也做徽标得改组件（不在 `touches`）。本票把文案口径与工作台对齐（`石蜡块 N · 已切片 · HE / IHC`），并在 `sheets.spec.ts` 里钉死。方案：若甲方要视觉一致，单独一张小票改 `LedgerTable.vue` 支持「单元格富内容」。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 沙箱恒非 0、Maven 必须带三参数、pnpm store 只能用环境变量传、`db.py --quiet` 不打印任何值、`reseed.sh` 清不掉运行时账号（收尾跑 `clean-orphan-accounts.sh`）、surefire `groups` 假绿、`PageQuery` 只有两参构造、`updateById` 忽略 null、MyBatis-Plus 无按列名排序重载、`@SaCheckPermission` 缺 `sys_menu` 行是 403、微信开发者工具在沙箱跑不通。

---

## §7 坑与解法（给下游，5 行）

1. ★ **`@TableLogic` 只保证 `del_flag='0'`，不保证业务状态** —— 提示的计数必须显式叠 `verify_status='valid'`。这两条各自都埋了病灶（1008 只有软删的块、1002 只有待核验的送样），少任何一条 accept 1 立刻红，而且会与 `EmbedChildrenChecker` 的「判无效闸」互相打架（D2 的 `SAMPLE-VERIFY-001` accept 1 跟着红）。**三方口径必须一致。**
2. ★ **一条 GROUP BY 的「一页一次」要能被机器证明**：`hintsOf` 收整页 id 集合（`foreach` 一个 `IN`），单测用**数调用次数的 mapper 替身**（不引 Mockito：本模块测试类路径只有 JUnit）断言 `calls == 1`；再加一条真日志的探针（一次 9 行的请求只出现 1 条聚合 SQL）。只写注释里的承诺是没牙的。
3. ★ **`IHC`,`HE` 这类「并集」别在 SQL 里做**：`STRING_AGG` 只负责拼串，拆开 / 去 `NONE` / 去重 / 排序交给纯函数（`StainHintRules.union`）—— 聚合里的字符串处理塞进 SQL 会写出没人敢改的表达式，而且没法单测。
4. ★ **前端宽表做悬停证据不要用 `page.mouse.move(x, y)`**：23+ 列的表格宽 2600px+，目标列的坐标会落在视口外，鼠标其实没移上去（第一版就这么假红：浮层 `visible:false`）。用 `elementHandle.hover()`（自己滚进视口）。**另外**：每一行各有一个 `el-popover`，全都被 teleport 到 body —— `document.querySelector('.lqg-hint__popper')` 拿到的是**第一个**（别人的、隐藏的）浮层的，要按可见性挑（或 `waitForSelector(..., { visible: true })`）。第二版又踩了这一下。
5. ★ **给 service 的 `@RequiredArgsConstructor` 加依赖会打断既有单测**：不启 Spring 的契约测试是 `new SampleQueryService(4 个实参)` 直接构造的，加第 5 个依赖编译就断（不是运行时）。加依赖前先 `grep -rn "new <类名>(" src/test` 扫一遍。

---

## §8 收尾（ticket §4.4）

- **后端 java（8081，PID 23244）**：收尾按 PID 关掉（`lsof -ti tcp:8081 -sTCP:LISTEN` → `kill`；**没用过 `pkill -f ruoyi-admin.jar`**，也没用过 `nohup &`）。
- **plus-ui dev server（8082）/ 小程序 H5 dev server（9200）**：收尾按 PID 关掉。
- **8080（Kevin）**：全程没碰（开工 / 收尾 `lsof` 都为空）。**未占** 8083 / 8099 / 9201。
- **5433（`lqg-dev-postgres`）/ 6380（`lqg-dev-redis`）/ 9002-9003（`lqg-dev-minio`）**：全程在跑，**没停**（留给后续 ticket）。
- **DB 收尾**：`reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`。
- **`git checkout -- code/miniapp/src/pages.json code/plus-ui/.eslintrc-auto-import.json`**：已执行（H5 dev / plus-ui dev 改生成产物是常态）。
- **提交**：只 `git add` 本票自己的路径（见 §9），`doc/waves/state.json` / `_manifest.json` 一个字节没动；不 push、不 merge。

## §9 提交清单

```
A  code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/hint/**   （5 个新类）
A  code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/hint/SampleHintContractTest.java
M  .../sample/domain/vo/SampleVo.java                       （越出 touches，§5.1-1）
M  .../sample/service/SampleQueryService.java               （越出 touches，§5.1-2）
M  .../sample/service/SampleRecentFilterContractTest.java    （越出 touches，§5.1-3）
A  code/plus-ui/src/views/lqg/sample/HintBadges.vue
M  code/plus-ui/src/views/lqg/sample/index.vue
M  code/plus-ui/src/api/lqg/sample/index.ts                 （越出 touches，§5.1-4）
M  code/plus-ui/src/lang/lqg/sample.zh_CN.ts
M  code/plus-ui/src/lang/lqg/sample.en_US.ts
M  code/miniapp/src/pages/ledger/sheets.ts
A  code/miniapp/src/pages/ledger/sheets.spec.ts             （越出 touches，§5.1-5）
A  doc/waves/reports/SAMPLE-HINT-001/**                     （报告 + 5 张截图 + 5 份 runner + 5 份 probe/transcript）
```
