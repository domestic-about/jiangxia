# EMBED-MODEL-001 · 完工报告

- **ticket**：EMBED-MODEL-001（track EMBED / phase D3 / size M）—— **D3 链头**（另外四张 D3 票等它：EMBED-WEB-001 / EMBED-MP-001 / AUTH-EXT-002 / SAMPLE-HINT-001 / SAMPLE-EXPORT-001）
- **status**：**done**
- **accept**：**4/4 绿**（四条 `run` 逐条实跑；唯一改动 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`，等价证据见 §4.0）
- **分支**：`task/D3`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 08:31:41`，后端进程 **PID 4776**（8081，dev profile + `--api-decrypt.enabled=false`）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/,gen_seed.py,verify.env,fixtures/**}`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`
- **未越界**：`code/plus-ui` / `code/miniapp` 一个字节没动；根 pom / `ruoyi-common-*` / `ruoyi-system` 没动；**唯一一处 `touches` 之外的改动 = 0**（迁移 `V202609231100` 在 `touches` 声明的 `V20260923110*__EMBED-MODEL-001-*.sql` 里；其余全在 `embed/**`）
- **单测**：`Tests run: 86, Failures: 0, Errors: 0, Skipped: 0`（本票前 64 → 本票 +22）
- **回归**：D1 `verify.sh --skip-build` = **41 绿 / 1 红 / 0 环境错**（唯一那条红是**既有** harness 缺陷：D1 回归包把 flyway 迁移总数写死成 7，D2 起每加一支就假红；state.json 已记 issue）· D2 `SAMPLE-VERIFY-001` accept 1+2 **全绿**（★ 本票那颗定时炸弹已拆）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D3`；`git log --oneline -1` = `d6b2734 merge(task/D2)` |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `SAMPLE-VERIFY-001` 报告 `status: done`；`VerifyTransitions` / `SampleChildrenChecker(s)` / `SampleVerifyService` 在盘且 8 用例绿 |
| 扫 `doc/change-log.md` | ✅ PASS | 顶部四条逐条核过，见下「CR 影响」 |
| 逐条取权威锚（14 个 `blueprint_refs`） | ✅ PASS | 全部 `authority_lint.py show` 过，逐条对照见 §1 |
| ADR-0010 / ADR-0009 | ✅ PASS | ADR-0010：一张表两列自由文本（sample_type / organoid_source_type）、工序时间不落冗余；ADR-0009：**部分唯一索引** `uk_embed_block_no WHERE del_flag='0'`、6 公共字段、无 `tenant_id` / `del_unique` |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | 三个容器全程在跑；8081 起停只按 PID；**没碰 8080 / 5432 / 6379** |
| 动手前代码是绿的 | ✅ PASS | `mvn -o -pl ruoyi-modules/ruoyi-lqg -am test` = `Tests run: 64, Failures: 0`（D2 收口值，本票前） |
| 必查环境纪律 | ✅ PASS | 只 `lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID 再 `kill`；**从没用过 `pkill -f`**；8080 上无监听（未碰） |

**STOP 判定：无。** 未出现「上游产物缺失」「与权威冲突且无法判断」「环境不可用」三类硬阻塞。

### CR 影响（顶部四条）

| CR | 与本票的关系 | 判定 |
|---|---|---|
| **CR-20260921-08**（小程序视觉方向 A） | 只动小程序 token 与图廊 / 图廊 11 份 brief；本票不做前端 | 不受影响 |
| **CR-20260918-07**（甲方 9 条） | 本票落两处：② 「历史编辑记录内部看全中心 + `mine` 开关」（`EmbedQueryBo.sort/mine` + `EmbedVo.handlerName/mine`，给 EMBED-MP-001 预置）；⑦ 「外部可见包埋人 / 操作人」（本票的 VO 里 `embedBy` / `operatorName` 都在，AUTH-EXT-002 装配 `ExtEmbedVo` 时直接用） | **已按 CR 实现** |
| **CR-20260917-06**（图廊删稿 +「我的」重绘） | 纯设计稿 | 不受影响 |
| **CR-20260917-05**（外部也能填石蜡包埋） | ★ **本票正文的一半**：`FLOW:F-EMBED-01.step6/step7`；外部 service（`EmbedExternalService`）落 pending、实验室核验给编号；**合法转移复用 `VerifyTransitions`** | **已按 CR 实现**（`EmbedVerifyService` 只调 `VerifyTransitions.check`） |
| **CR-20260917-04** | 冻存流水与工作台「最后修改」；本票的 `update_by` 显式补写与它同源（同一份 `LambdaUpdateWrapper` 坑） | 不受影响（口径已复用） |

`authority_lint.py` 口径：本票不动 `flows` / `field-ssot` / `ui-index`（只读锚）。

## §1 口径复述（逐条对 accept 核）

| # | 口径（ticket §0 / §2，权威锚） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **染色是五个按钮**（HE / IF / IHC / OTHER / NONE），多选；**NONE 与其余四个互斥**；**选 OTHER 必须写名称**；值必须落在字典 `lqg_stain_type` 内；**落库按固定顺序**（不存 `IHC,HE`） | `guard/StainRules.normalize`（纯函数，字典值由 `EmbedDictService` 注入）；`EmbedService` 两处调用 | accept 2 段 1/2/3 三次 400/500；段 8 库内 `HE,IHC`；`EmbedRulesContractTest` 8 例 |
| 2 | **七个工序时间全部可空**（少一个就红），保存不要求填完 | DDL 七列全 `NULL`（generator 输出）；`EmbedSubmitBo` 七字段；PUT = patch | accept 1 `ddl_vs_ssot`；§4.4(a) 的 `is_nullable=YES` 七个；`EmbedShapeContractTest` 用例 ② |
| 3 | 模板的「样本编号」= 样本主档**内部编号，读时带出、不落库**；本表只存 `sample_id`。**内部录入只能挂已核验有效样本**，且**石蜡块编号必填、落地即 valid** | `EmbedVo.internalNo`（`EmbedQueryService.toVo` 从 `Sample` 带出）；`EmbedService.requireValidSample` + `EmbedBlockNoGuard` | accept 2 段 4/6 两次红；段 7 库内 `internal|valid`；accept 3 段 1 `.data.internalNo=="T-hli01"` |
| 4 | **外部送样**只收两个字段（样本类型 / 类器官来源类型），只能挂**本人送检过、没被判无效**的样本（**待核验可挂**）；落库 `pending`、编号空、`submit_source='external'` | `EmbedExternalService.submit(userId, sampleId, sampleType, organoidSourceType)`（签名即白名单） | 签名里没有 blockNo/日期/染色/marker/状态；AUTH-EXT-002 的 accept 会端到端打它（本票无 controller） |
| 5 | **判有效**要同时满足：编号**非空且全库唯一** + 所挂样本**已核验有效**；**判无效必须写原因**；缺了就拒，**库里什么都不变** | `EmbedVerifyService.applyValid/applyInvalid`（全部校验在第一条 `SET` 之前，整段 `@Transactional`） | accept 4 段 1/2/5/6 四次红 + 段 4/8 两次库内断言 `pending\|-\|-`、`pending\|-` |
| 6 | **待核验 / 无效的送样不能被普通保存改掉**：`PUT /lqg/embed` 对这两种状态直接 400，入参 BO 里**没有** `verifyStatus` | `EmbedService.update` 状态闸（`ServiceException(..., 400)`）；`EmbedSubmitBo` / `EmbedVerifyBo` 都不声明 `verifyStatus` | accept 4 段 3 红 + 段 4 库内 `pending\|-\|-|组织`；accept 4 末段夹带 `verifyStatus:"pending"` 仍 `valid`；`EmbedShapeContractTest` 用例 ① |
| 7 | **marker 单独一张表**，一个蜡块多个 marker；主表上不许有这两列 | `t_lqg_embed_marker` + `EmbedMarker`；`EmbedService.insertMarkers`（整组替换，同一事务） | accept 1 `ddl_vs_ssot`（主表多一列就红）；accept 2 段 9 库内 count=2；accept 3 段 1 markers 成组 |
| 8 | 列表：**待核验置顶**、软删不出现、行带**所挂样本核验状态**与送检单号；**按染色筛选不许用 `LIKE '%HE%'`** | `EmbedQueryService.buildWrapper` 默认 `ORDER BY (verify_status='pending') DESC`；`StainRules`/apply 的整元素匹配 | accept 3 五段全绿；§4.4(c) `stain=HE` 只出 `T-E01-1`（OTHER 不串） |
| 9 | **状态机复用 `VerifyTransitions`**，不另写一份 | `EmbedVerifyService` 只调 `check/isKnownAction/targetOf/rejectionMessage`；`EmbedExternalService.resubmit` 的 `invalid→pending` 也走同一张表 | `ExtChokepointContractTest` 与 `VerifyTransitionsContractTest` 全绿；本票零新增状态机类 |
| 10 | 字典 seed 已就绪，**别再插字典**；菜单号段 EMBED `5300-5399`，别抢 EMBED-WEB-001 的号 | `EmbedDictService` 只读 `lqg_stain_type` / `lqg_marker_expr`；迁移只建业务表 + 菜单 **5300/5301-5307**（EMBED-WEB-001 用 5310-5317） | 迁移里没有任何 `sys_dict_*`；`SELECT menu_id BETWEEN 5300 AND 5307` 七行（§4.4(e)） |

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway（**1 支**，在 `touches` 内）

**`code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609231100__EMBED-MODEL-001-embed.sql`**（新增，170 行）

**取号依据 `doc/lint-profile.yaml`**：D3 = `20260923`；HHmm 域分段 **EMBED = 11xx**；SSOT 里两张表的 `migration` 字段都写着 `V202609231100__EMBED-MODEL-001-embed.sql` → 取 **`1100`**。版本号大于已应用最大值 `202609221010`，`out-of-order=false` 下不会触发 `FlywayValidateException`（SYS-WEB-001 踩过跨域补号的坑）。

★ **两张表 + 菜单必须在同一支迁移里**：accept 1 第 2 段断的是
`script LIKE 'V20260923110%__EMBED-MODEL-001-%'` 恰 **1** 行 —— 拆成 `1100` + `1101` 会让这一格变成 2 而红。

内容两段：

1. **第一段** = `python3 doc/tools/gen_ddl_pg.py --migration V202609231100__EMBED-MODEL-001-embed.sql` 的**逐字节输出**（已用 Python 子串校验：`gen contained verbatim: True`）：`t_lqg_embed` 22 业务列 + 公共 6 字段 + 部分唯一索引 `uk_embed_block_no WHERE del_flag='0'` + 3 个普通索引；`t_lqg_embed_marker` 4 业务列 + 公共 6 字段 + 1 个普通索引。
2. **第二段**（手写）：菜单 `5300`「石蜡包埋」（`C`、`path='embed'`、`component='lqg/embed/index'`、perms `lqg:embed:list`）+ 按钮 `5301-5307`（`lqg:embed:{list,query,add,edit,remove,export,verify}`）+ `sys_role_menu` 授给 **101（lqg_admin）与 102（lqg_internal）**。

```
$ python3 doc/verify/db.py --sql "SELECT menu_id||':'||menu_type||':'||COALESCE(path,'-')||':'||COALESCE(component,'-')||':'||perms FROM sys_menu WHERE menu_id BETWEEN 5300 AND 5307 ORDER BY menu_id"
5300:C:embed:lqg/embed/index:lqg:embed:list
5301:F::-:lqg:embed:list
5302:F::-:lqg:embed:query
5303:F::-:lqg:embed:add
5304:F::-:lqg:embed:edit
5305:F::-:lqg:embed:remove
5306:F::-:lqg:embed:export
5307:F::-:lqg:embed:verify
$ python3 doc/verify/db.py --sql "SELECT role_id||':'||string_agg(menu_id::text,',' ORDER BY menu_id) FROM sys_role_menu WHERE menu_id BETWEEN 5300 AND 5307 GROUP BY role_id ORDER BY role_id"
101:5300,5301,5302,5303,5304,5305,5306,5307
102:5300,5301,5302,5303,5304,5305,5306,5307
```

> ★★ **为什么必须建菜单**（SAMPLE-VERIFY-001 踩过的同一坑）：`@SaCheckPermission` 的权限集合来自「角色 → `sys_menu.perms`」。缺这几行 `--as staff` 调 `/lqg/embed/**` 恒 **403**（不是 500），accept 2/3/4 全红。
> ★ **为什么取 5300 而不是 5310**：EMBED-WEB-001 的票面点名 `5310 + 5311-5317`（它的 accept 同时要 `menu_id=5310` 与 getRouters 里 `component='lqg/embed/index'` **恰 1 条**），派单也要求「别抢它的号」。本票只落 5300 段 —— **交接：EMBED-WEB-001 落页面时按 SAMPLE-WEB-001 的先例把 5300-5307 整体搬到 5310-5317**（它那条 accept 的两条都要求搬，见 §6 WARN-1）。
> ★ **页面 / 导出端点都不在本票**（ticket §3）：`lqg:embed:export` 只有权限行、没有 `POST /lqg/embed/export`。

### 2.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.embed`）

| 文件 | 职责 |
|---|---|
| `domain/Embed.java` | `t_lqg_embed` 实体，逐列照 SSOT；`@TableLogic delFlag`；公共字段走 `BaseEntity` |
| `domain/EmbedMarker.java` | `t_lqg_embed_marker` 实体（marker 单独一张表） |
| `domain/vo/EmbedVo.java` | 行 / 详情：`stainTypes` 是**数组**、`markers` **成组**、`internalNo`/`submitNo`/`sampleVerifyStatus` **读时带出**、`sectioned` 派生、`handlerName`/`mine`/`editable`（EMBED-MP-001 / EMBED-WEB-001 用） |
| `domain/vo/EmbedMarkerVo.java` | `{markerName, expression, sort}` |
| `domain/bo/EmbedSubmitBo.java` | 新增 / 修改入参；**没有** `verifyStatus` / 核验段 / 来源段（ticket §2 第 6 条） |
| `domain/bo/EmbedMarkerBo.java` | 一条 marker 入参（`markerName` 可空） |
| `domain/bo/EmbedQueryBo.java` | 列表筛选：`paraffinBlockNo / internalNo / sampleId / stain / sectionTimeBegin/End / verifyStatus / submitSource`（+ 给 EMBED-MP-001 预置的 `sort` / `mine`）；显式无参构造（`PageQuery` 5.5.3 只有两参构造） |
| `domain/bo/EmbedVerifyBo.java` | 核验入参 `{action, paraffinBlockNo, reason}`；**没有** `verifyStatus` |
| `mapper/EmbedMapper.java` · `mapper/EmbedMarkerMapper.java` | 两个 `BaseMapperPlus`，**零自定义 SQL**（连「按内部编号筛」也不写 join） |
| `guard/StainRules.java` | **纯函数**：五个值 + **固定顺序** + NONE 互斥 + OTHER 必备名称 + 逗号串读写 |
| `guard/MarkerExprRules.java` | **纯函数**：`negative / weak / strong` 三选一 |
| `guard/EmbedChildrenChecker.java` | ★ **注册给 `SampleChildrenChecker` 的实现**（见 §4.4(b) 的 SQL 判据） |
| `service/EmbedService.java` | 写侧：内部新增（编号必填、直接 valid）、修改（patch；待核验 / 无效 → 400）、软删；marker 整组替换 |
| `service/EmbedQueryService.java` | 读侧：六筛选 + 两档排序 + 批量装配（样本 / marker 各一次 IN）；`buildWrapper` 包内可见给契约测试 |
| `service/EmbedVerifyService.java` | 核验状态机：**只调 `VerifyTransitions`**，判有效落编号 + 核验人、判无效必填原因 |
| `service/EmbedExternalService.java` | 外部 service（controller 在 AUTH-EXT-002）：`submit` / `resubmit`，只收两个字段，只能挂本人未判无效的样本 |
| `service/EmbedBlockNoGuard.java` | 石蜡块编号唯一性（新增 / 修改 / 判有效三处一份判据；软删后可重用） |
| `service/EmbedDictService.java` | 只读两本字典（`lqg_stain_type` / `lqg_marker_expr`），**不插字典** |
| `controller/EmbedController.java` | `/lqg/embed`：list / {id} / POST / PUT / DELETE / `{id}/verify` 六个端点 |

**接口清单（本票）**

```
GET    /lqg/embed/list?paraffinBlockNo&internalNo&sampleId&stain&sectionTimeBegin&sectionTimeEnd
                        &verifyStatus&submitSource&sort&mine&pageNum&pageSize   lqg:embed:list
GET    /lqg/embed/{id}                                                             lqg:embed:query
POST   /lqg/embed        {sampleId, paraffinBlockNo, sampleType, organoidSourceType, 7×工序时间,
                          embedBy, stainTypes[], stainOther, markers[], operatorName, remark}
                          → submit_source='internal'、verify_status='valid'、编号必填     lqg:embed:add
PUT    /lqg/embed        同形状 + id；patch 语义；待核验 / 无效 → 400                  lqg:embed:edit
DELETE /lqg/embed/{ids}   软删（含名下 marker）                                       lqg:embed:remove
PUT    /lqg/embed/{id}/verify  {action:"valid"|"invalid", paraffinBlockNo, reason}    lqg:embed:verify
```

### 2.3 测试（`src/test/java/org/dromara/lqg/embed/`）

| 文件 | 例数 | 钉住什么 |
|---|---|---|
| `guard/EmbedRulesContractTest.java` | 8 | 固定顺序 / 五按钮全集 / NONE 互斥 / OTHER 必备名称 / 字典外值（PAS）/ fromCsv 空串 / marker 表达 |
| `guard/EmbedChildrenCheckerContractTest.java` | 4 | ★ checker 的 SQL **只有 valid**（pending / invalid 不在参数里）+ 两张表都带 `@TableLogic` |
| `service/EmbedQueryContractTest.java` | 4 | ★ 染色筛选的整元素模式 `%,HE,%`（**不是 `%HE%`**）/ mine 的 OR 包组 / 两档排序 / WHERE 无裸 OR |
| `domain/EmbedShapeContractTest.java` | 6 | 入参无 `verifyStatus` / 七个工序时间一个不少 / 主表无 `marker_name`·`expression`·`sample_no` / 染色库里 String 对外 List |
| **合计** | **22** | `Tests run: 86`（本票前 64） |

## §3 对照 ticket `touches`

`touches` 三处（`embed/src/main/**`、`embed/src/test/**`、`V20260923110*__EMBED-MODEL-001-*.sql`）**覆盖了本票全部改动**，**零越界**：22 个 Java 文件全在 `org.dromara.lqg.embed`（`domain` / `domain/bo` / `domain/vo` / `mapper` / `guard` / `service` / `controller`）+ 1 支迁移。没有碰根 pom、`ruoyi-common-*`、`ruoyi-system`、前端、小程序、`ExtChokepointContractTest` fixture。

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，与 D1/D2 全部票同源）

`doc/verify/api.sh` 第 71 行 `ps -o lstart=` 在本 subagent 沙箱被禁（`/bin/ps: Operation not permitted`），`|| date -d` 回退在 macOS 上也不认 → **恒非 0 退出**。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的**两个半边**：

```
jar            : code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
jar mtime      : 2026-09-22 08:31:41
lqg src newer  : []                       ← 没有比 jar 新的 ruoyi-lqg 源码
admin src newer: []                       ← 同上（migration 也在 jar 里）
8081 PID       : 4776                     （lsof -ti tcp:8081 -sTCP:LISTEN）
PID holds jar  : 2                        （lsof -p 4776 | grep -c 'ruoyi-admin/target/ruoyi-admin.jar'）
start >= jar   : 1790037105 >= 1790037101 -> True   （libproc.proc_pidinfo，不用 ps）
backend log    : 2026-09-22 08:32:49
嵌套 jar 复核   : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含
                  org/dromara/lqg/embed/{controller/EmbedController,
                  service/{EmbedService,EmbedQueryService,EmbedVerifyService,EmbedExternalService,
                           EmbedBlockNoGuard,EmbedDictService},
                  guard/{StainRules,MarkerExprRules,EmbedChildrenChecker},
                  domain/{Embed,EmbedMarker},domain/bo/*,domain/vo/*}.class
Flyway 启动日志 : "Migrating schema \"public\" to version \"202609231100 - EMBED-MODEL-001-embed\""
                  "Successfully applied 1 migration to schema \"public\", now at version v202609231100"
D1 回归 L0.2    : ✓ 新鲜度：源码不新于 jar；pid 4776 持有该 jar；进程启动 08:31:45 ≥ jar 08:31:41
```

**复跑脚本随报告落盘**：`doc/waves/reports/EMBED-MODEL-001/accept-runners/{embed001-acc1,acc2,acc3,acc4}.sh`
（与本次实跑逐字相同，从工作区根执行即可；唯一差异 = 去掉 `--fresh-module`）。四条都把长链包进函数判**整条** rc，并在开头 `rm -f $TMPDIR/lqg-verify-token-*`。

> ★ 踩过一条**跑法坑**：四条 accept 各自 `reseed` 首尾，**绝不能并行跑**（我第一次并行跑 acc3/acc4，acc3 第 5 段被 acc4 的收尾 reseed 打断，假红）。本报告的四条输出是**串行**实跑的。

### 4.1 accept 1 · DDL —— ✅

```
$ bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc1.sh
✓ 2 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）
1
ACCEPT-1 EXIT=0
```

`ddl_vs_ssot` 逐列相符；`flyway_schema_history` 里 `V20260923110%__EMBED-MODEL-001-%` 恰 **1** 行且 success；迁移文件含 `CREATE TABLE t_lqg_embed_marker`。**counterfeit 逐条排掉**：

```
$ # 「七个工序时间建成 NOT NULL」→ 这张表会被反复打开补填，建块当天只有编号；实际全 YES
$ python3 doc/verify/db.py --sql "SELECT string_agg(column_name||'='||is_nullable, ',' ORDER BY column_name) FROM information_schema.columns WHERE table_name='t_lqg_embed' AND column_name IN ('tissue_receive_time','tissue_process_time','agarose_embed_time','dehydrate_time','agarose_send_time','paraffin_embed_time','section_time')"
agarose_embed_time=YES,agarose_send_time=YES,dehydrate_time=YES,paraffin_embed_time=YES,section_time=YES,tissue_process_time=YES,tissue_receive_time=YES
$ # 「石蜡块编号建成 NOT NULL」→ 外部送样核验前没有编号，落不了库就没法核验；实际可空 + 部分唯一
$ python3 doc/verify/db.py --sql "SELECT is_nullable FROM information_schema.columns WHERE table_name='t_lqg_embed' AND column_name='paraffin_block_no'"
YES
$ python3 doc/verify/db.py --sql "SELECT indexdef FROM pg_indexes WHERE tablename='t_lqg_embed' AND indexname='uk_embed_block_no'"
CREATE UNIQUE INDEX uk_embed_block_no ON public.t_lqg_embed USING btree (paraffin_block_no) WHERE (del_flag = '0'::bpchar)
$ # 「样本编号建成一列 sample_no / internal_no」→ 库里有、SSOT 没有会红；实际 0
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM information_schema.columns WHERE table_name='t_lqg_embed' AND column_name IN ('sample_no','internal_no','marker_name','expression')" --eq 0
0
$ # 「marker 直接在主表上建 marker_name + expression」→ 实际两列只在 marker 表上
$ python3 doc/verify/db.py --sql "SELECT string_agg(table_name||'.'||column_name, ',' ORDER BY table_name, column_name) FROM information_schema.columns WHERE column_name IN ('marker_name','expression')"
t_lqg_embed_marker.expression,t_lqg_embed_marker.marker_name
```

### 4.2 accept 2 · STATE —— ✅

```
$ bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc2.sh
0
True
2
valid
ACCEPT-2 EXIT=0
```

逐段（原文 15 段 `&&` 链，段 2 起）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1-6 | 六个 `post … \| grep -qE '^(400\|500)'` | （静默） | `["NONE","HE"]` 互斥 / `["OTHER"]` 缺名称 / `["PAS"]` 字典外 / 挂待核验样本 1002 / 撞号 `T-E01-1` / 内部缺编号 —— **六次全拒** |
| 7 | `SELECT count(*) … paraffin_block_no LIKE 'T-X%' OR (…'T-E01-1' AND sample_id<>9000001001) OR (… IS NULL AND submit_source='internal') --eq 0` | `0` | ★ **六次被拒之后库里一行都没多**（不是先落盘再报错） |
| 8 | `post {T-OK1, stainTypes:["IHC","HE"], markers:[Ki67/weak, (无名)/negative]}` | 命中 `^200` | 正向对照：不是「一律拒绝也能绿」 |
| 9 | `SELECT stain_types \|\| tissue_receive_time \|\| submit_source \|\| verify_status = …` | `True` | ★ 落库 **`HE,IHC`**（固定顺序，不是请求顺序 `IHC,HE`）+ `tissue_receive_time` = 样本收样日期 + `internal` + `valid` |
| 10 | `SELECT count(*) FROM t_lqg_embed_marker … T-OK1 AND del_flag='0' --eq 2` | `2` | marker 成组落库（第二条 `markerName` 为空也允许） |
| 11 | `--as staff --bizcode PUT /lqg/sample/9000001004/verify {"action":"invalid",…}` | `500 该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效` | ★★ **checker 已注册且生效**：1004 名下有已核验有效的 2003 |
| 12 | `SELECT verify_status FROM t_lqg_sample WHERE id=9000001004 --eq valid` | `valid` | 被拒后样本一字未变 |
| 13 | `reseed.sh --yes` | （静默） | 收尾 |

### 4.3 accept 3 · API —— ✅

```
$ bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc3.sh
true
true
true
true
true
ACCEPT-3 EXIT=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `GET /lqg/embed/9000002001 \| jq` | `true` | `stainTypes==["HE","IHC"]`（**数组**）、`internalNo=="T-hli01"`（读时带出）、markers 成组 `CK19:negative`/`Ki67:strong` |
| 2 | `GET /lqg/embed/list?pageSize=100 \| jq` | `true` | id 集合恰 `{2001,2002,2003,2004,2006}`（**软删的 2005 不出现**）+ `rows[0]=2006`（**待核验置顶**）+ `verifyStatus=pending`/`submitSource=external`/`paraffinBlockNo=null`/`submitNo=SJ90000002`/`sampleVerifyStatus=pending` |
| 3 | `GET /lqg/embed/list?internalNo=T-hli01` | `true` | 恰 2 行（2001、2002 —— 内部编号是**所挂样本**的，本表只有 sample_id） |
| 4 | `GET /lqg/embed/list?verifyStatus=pending` | `true` | 恰 `["9000002006"]` |
| 5 | `GET /lqg/embed/list?stain=IHC` | `true` | `["T-E01-1"]` —— 整元素匹配（见 §4.4(c) 的 `stain=HE` 反证） |

**counterfeit 逐条排掉（grep 源码形态的反例实测）**：

```
$ # 「stainTypes 原样返回逗号串」→ 实际是数组（acc3 段 1 的 ==["HE","IHC"]）
$ # 「列表手写 SQL join 忘带 del_flag」→ 软删的 2005 不出现（acc3 段 2 + 下面整表）
$ # ★ 「按染色用 LIKE '%HE%'」→ OTHER 会被串出来；实际：
$ bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=HE'    | jq -c '[.rows[].paraffinBlockNo]'
["T-E01-1"]                       ← 只有那一块；T-E04-1（OTHER / Masson）不串
$ bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=OTHER' | jq -c '[.rows[].paraffinBlockNo]'
["T-E04-1"]
$ bash doc/verify/api.sh --as staff GET '/lqg/embed/list?pageSize=100' | jq -c '[.rows[].paraffinBlockNo]'
[null,"T-E04-1","T-E02-1","T-E01-2","T-E01-1"]   ← 2006 编号为 null、2005（软删）不在
```

### 4.4 accept 4 · STATE（外部送样核验状态机）—— ✅

```
$ bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc4.sh
pending|-|-|组织
true
pending|-
valid|T-E06-1|9000000101|external|9000000111|T-hli77
true
valid|2026-09-17
ACCEPT-4 EXIT=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 2 | `v 9000002006 {valid, T-E06-1}` | `500 所挂样本还未核验有效（当前状态：pending），不能判为有效` | ★ 判有效**同时看所挂样本**（1002 还是 pending） |
| 3 | `v 9000002006 {invalid}` | `500 判无效必须写原因` | |
| 4 | `--bizcode PUT /lqg/embed {id:2006, sampleType:"被普通保存改掉", …}` | `400 待核验 / 无效的送样不能通过普通保存修改…` | ★ 普通保存绕不过核验 |
| 5 | `SELECT verify_status\|para\|verify_by\|sample_type … id=2006` | `pending\|-\|-|组织` | ★ **三次被拒之后库里一字不变**（先校验后写 + 一个事务） |
| 6 | `PUT /lqg/sample/9000001002/verify {valid, T-hli77}` | `.code==200` | 把所挂样本核验有效 |
| 7 | `v 9000002006 {valid}`（无编号） | `500 判有效必须填石蜡块编号` | |
| 8 | `v 9000002006 {valid, T-E01-1}`（撞号） | `500 石蜡块编号「T-E01-1」已存在，请换一个` | 编号全库唯一（T-E01-1 在 2001 上） |
| 9 | `SELECT verify_status\|para … id=2006` | `pending\|-` | 两次被拒后仍 pending |
| 10 | `v 9000002006 {valid, T-E06-1}` | 命中 `^200` | 正向对照 |
| 11 | `SELECT verify_status\|para\|verify_by\|submit_source\|submitter_id\|s.internal_no` | `valid\|T-E06-1\|9000000101\|external\|9000000111\|T-hli77` | ★ 落编号与**核验人**；**`submit_source` 仍是 `external`（提交当时的快照没被重算）**、`submitter_id` 仍是 extA |
| 12 | `PUT /lqg/embed {id:2006, paraffinBlockNo:"T-E06-1", dehydrateTime, **verifyStatus:"pending"**}` | `.code==200` | valid 记录可改；**夹带的 `verifyStatus` 不生效**（BO 里没有这个键） |
| 13 | `SELECT verify_status\|dehydrate_time …` | `valid\|2026-09-17` | ★ 状态仍是 valid，只落了脱水时间 |
| 14 | `reseed.sh --yes` | （静默） | 收尾 |

### 4.5 追加证据（accept 之外的机器证据）

**(a) Java 单测：`Tests run: 86, Failures: 0, Errors: 0, Skipped: 0`（本票前 64）**

```
$ (cd code/RuoYi-Vue-Plus && mvn -o -pl ruoyi-modules/ruoyi-lqg -am test -s <ws>/.mvn-settings.xml …)
[INFO] Running org.dromara.lqg.embed.service.EmbedQueryContractTest        Tests run: 4, Failures: 0
[INFO] Running org.dromara.lqg.embed.guard.EmbedRulesContractTest          Tests run: 8, Failures: 0
[INFO] Running org.dromara.lqg.embed.guard.EmbedChildrenCheckerContractTest Tests run: 4, Failures: 0
[INFO] Running org.dromara.lqg.embed.domain.EmbedShapeContractTest         Tests run: 6, Failures: 0
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest               Tests run: 4, Failures: 0   ← D2 的 L0 咽喉门仍绿
[INFO] Running org.dromara.lqg.sample.verify.VerifyTransitionsContractTest Tests run: 8, Failures: 0
[INFO] Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**(b) ★★ checker 的 SQL 判据（本票背的那颗定时炸弹的解药）**

`EmbedChildrenChecker.hasChildren(sampleId)` 落在**一个** `selectCount` 上（`@TableLogic` 自动补 `del_flag`），等价 SQL：

```sql
SELECT COUNT(*) FROM t_lqg_embed
 WHERE del_flag = '0'          -- 软删的不算（seed 2005 软删，挂 1008）
   AND sample_id = ?           -- 只数这个样本名下的
   AND verify_status = 'valid' -- ★ 待核验的外部送样不算（seed 2006 pending，挂 1002）
```

**判据 = 「已生效」才算 children**：软删不算、待核验不算、无效不算。
★ **SAMPLE-HINT-001 / EMBED-WEB-001 读同一份计数口径**：SAMPLE-HINT-001 那边是
`e.del_flag='0' AND e.verify_status='valid'` 再 join 样本 `s.del_flag='0'`；本类按 `sample_id` 单条查、不做样本侧 join（调用方拿到的就是一条未软删的样本）。**三方必须一致，否则 1002 / 1008 的期望会互相打架。**

```
$ # 反面：把 pending 也算 children → D2 的 SAMPLE-VERIFY-001 accept 1 立刻红（它要 1002 valid→invalid）
$ # 三条探针（reseed 起点，串行）：
$ # P1 1004（名下有已核验有效的 2003）改判无效
$ bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001004/verify '{"action":"invalid","reason":"探针"}'
500	该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效
$ python3 doc/verify/db.py --sql "SELECT verify_status FROM t_lqg_sample WHERE id=9000001004" --eq valid
valid
$ # P2 1008（名下唯一的块 2005 是软删）改判无效 —— 应放行（del_flag 排除）
$ bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001008/verify '{"action":"invalid","reason":"探针：只有软删的块"}'
200	操作成功
$ python3 doc/verify/db.py --sql "SELECT verify_status FROM t_lqg_sample WHERE id=9000001008" --eq invalid
invalid
$ # P3 1002（名下只有待核验的 2006）pending→valid→invalid —— 应放行（pending 排除）
$ bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-probe99"}' | jq -e '.code==200'
true
$ bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001002/verify '{"action":"invalid","reason":"探针：只有待核验送样"}'
200	操作成功
$ python3 doc/verify/db.py --sql "SELECT verify_status FROM t_lqg_sample WHERE id=9000001002" --eq invalid
invalid
```

**(c) 染色筛选的「字符通配 vs 整元素」反证**（accept 3 第 5 段 + accept 的 counterfeit 备注要求另贴 `stain=HE`）：

```
$ bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=HE'    | jq -c '[.rows[].paraffinBlockNo]'   → ["T-E01-1"]
$ bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=OTHER' | jq -c '[.rows[].paraffinBlockNo]'   → ["T-E04-1"]
$ # 落到 SQL（p6spy 原文，后端 dev 日志）：
$ grep -o "stain_types[^)]*)" .tmp/backend-verify.log | tail -2
stain_types || ',') LIKE '%,IHC,%'
stain_types || ',') LIKE '%,HE,%'
```

**(d) 外部 service 的形状**（controller 在 AUTH-EXT-002，本票只出 service）：

```
EmbedExternalService.submit(Long userId, Long sampleId, String sampleType, String organoidSourceType)
EmbedExternalService.resubmit(Long userId, Long embedId, String sampleType, String organoidSourceType)
EmbedExternalService.resubmit(Long userId, Long embedId, Long sampleId, String sampleType, String organoidSourceType)
```
签名里**没有**石蜡块编号 / 工序时间 / 染色 / marker / 状态 / 包埋人 / 操作人 —— 夹带无从生效（不是「读到了再丢掉」）。
给 AUTH-EXT-002 复用的读口：`EmbedQueryService.detail(id)` / `listBySampleId(id)` / `listBySampleIds(ids)` / `entity(id)` / `pageByWrapper(page, wrapper)`。

## §5 回归自查（本票最容易打红别人的地方）

| 回归 | 命令 | 结果 |
|---|---|---|
| **D2 `SAMPLE-VERIFY-001` accept 1**（★ 对 1002 的 `valid→invalid`） | `bash doc/waves/reports/SAMPLE-VERIFY-001/accept-runners/verify001-acc1.sh` | **`ACCEPT-1 EXIT=0`**（逐字输出：`pending\|-\|-` / `valid\|T-hli77\|9000000101\|Y\|external` / `9000001002:invalid:误判，退回` / `9000001003:valid:`） |
| **D2 `SAMPLE-VERIFY-001` accept 2** | `bash doc/waves/reports/SAMPLE-VERIFY-001/accept-runners/verify001-acc2.sh` | **`ACCEPT-2 EXIT=0`**（`external:3` / `internal:1` / `3`） |
| **D1 回归包** | `bash doc/waves/regression/D1/verify.sh --skip-build` | **41 绿 / 1 红 / 0 环境错，rc=1** —— 唯一那条红是**既有 harness 缺陷**，与实现无关（见下） |
| D2 的 L0 咽喉门 | `ExtChokepointContractTest` | `Tests run: 4, Failures: 0`（embed 包不在 ext 包，四条不变量全绿） |

```
$ D1_VERIFY_RC=1  green=41 red=1 env=0
$   ✓ L1.1 flyway 无失败行（success IS NOT TRUE 的行 = 0）
$   ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '11' 11
$ 失败 1 条：
$   - L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '11' 11
```

★ 这一条**不是**「以前好的坏了」：D1 的 7 支迁移**逐个点名全绿**、无失败行、迁移文件在源码树全绿；红的是
`SELECT count(*) FROM flyway_schema_history --eq 7` 这个**写死总数**的断言（D2 加 3 支 → 10，本票加 1 支 → 11）。
state.json 已记 issue：「D1 回归包把 flyway 迁移总数写死成 7，D2 起每加一支迁移就假红一条」（D2 r1 L0+L1 片首次命中）。
**本票不重复计数**，只在 §6 引用。

**其余跨票依赖面（EMBED-MODEL-001 是 D3 链头，这些是给下游的接口证据）**：

- `SampleChildrenCheckers.registeredCount()` 现在 = **1**（本票注册 `EmbedChildrenChecker`）。D2 报告 §7 坑 4 / WARN-4 预言的「注册后会打红 D2」**没有发生**，因为判据被限定成「已生效（未删 + valid）」—— P3 探针就是它的正面证据。
- 工作台首页「待核验石蜡包埋送样」数字（SYS-HOME-001）读 `t_lqg_embed.verify_status='pending' AND del_flag='0'`，本票的列表口径与它同源。

## §6 遗留与 raise（ticket §4.3）

1. **越出 `touches` 的改动：0**。
2. **与 `doc/api-contract.md` 的差异**：
   - 契约 EMBED 一节列的 `POST /lqg/embed/export` **本票不实现**（ticket §3：导出归 EMBED-WEB-001 的 `embed/export/**`）。本票只 seed 了 `lqg:embed:export` 权限行。
   - 契约的 `/mp/int/embed/**`（EMBED-MP-001）、`/mp/ext/embed/**`（AUTH-EXT-002）不在本票；本票提供 service 与 VO 形状（§4.5(d)）。
   - 契约说 list 行带 `submitNo`（**所挂样本的**送检单号）—— 逐字实现（本表没有送检单号）。
3. **没把握 / 需要主会话确认的口径**（不改权威，只报）：
   - **`PUT /lqg/embed` 的语义**：本实现 = **patch（只改传了的字段）**，`markers` 是唯一例外（传了就整组替换）。依据是 ticket §1「这张表会被反复打开补填」+ EMBED-MP-001 的「补填就是修改」；EMBED-WEB-001 的编辑抽屉若整份提交，patch 与替换等价。若甲方要「整份替换」，改 `EmbedService.update` 的 `.set(cond, …)` 即可 → WARN-3。
   - **`paraffinBlockNo` 模糊 vs 精确**：本票取**模糊**（`LIKE`）——它是工作台的搜索框（EMBED-WEB-001 的筛选列）。accept 没有钉它；若要精确匹配改一处 `like` → `eq` 即可。
   - **`EmbedVo` 里没有 `submitterName`**（提交人姓名）：`AUTH-EXT-002` 的 `ExtEmbedVo` 有这一键，由它自己的装配层从 `t_lqg_ext_profile` 取（本票不额外加 N+1 查询）。
4. **给 EMBED-WEB-001 的交接（必读）**：本票的迁移落了 **5300（C）+ 5301-5307（F）**；你的票面是 **5310（C）+ 5311-5317（F）**，且你的 accept 要 `getRouters` 里 `component='lqg/embed/index'` **恰 1 条** → 请照 SAMPLE-WEB-001 的先例（它把 SAMPLE-MODEL-001 的 5200-5206 整体搬到 5210-5216）把 5300-5307 DELETE 掉再重建到 5310 段。见 WARN-1。
5. **给 EMBED-MP-001 的交接**：`EmbedQueryBo` 已经带 `sort=recent` / `mine=true` 两个参数、`EmbedVo` 已经带 `handlerName` / `mine` / `editable`（口径同 SAMPLE 域：`handlerName` = **最后修改人，没改过就是创建人**，**不是** `embedBy`；`mine` = `create_by OR update_by = 我`）。你的 `/mp/int/embed/**` 可以直接复用 `EmbedQueryService.list` + `EmbedService.update`。
6. **明确没做（ticket §3 边界，逐条核过）**：不做工作台页面（EMBED-WEB-001）、不做小程序页（EMBED-MP-001）、不做外部 controller（AUTH-EXT-002）、不做切片染色提示（SAMPLE-HINT-001）、不做三张 Excel 导出（SAMPLE-EXPORT-001）、**不校验工序时间先后顺序**（甲方没提）、**不自动生成石蜡块编号**、不做核验通知、不做核验历史流水（只记最后一次 `verify_by/verify_time`）。

## §7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | cross-ticket | **本票落了 EMBED 菜单 5300-5307，EMBED-WEB-001 的票面是 5310-5317 —— 它必须把 5300 段搬走，否则它自己的 accept 会红** | 取证：`sys_menu` 5300（C，`component='lqg/embed/index'`）+5301-5307（F）已存在。EMBED-WEB-001 的 accept 2 同时要 ① `menu_id=5310` 且 `component='lqg/embed/index'` ② `getRouters` 里该 component **恰 1 条** —— 两条都要求把 5300 段 DELETE 后在 5310 段重建（SAMPLE-WEB-001 对 5200→5210 做过同一件事，先例在盘）。**不补菜单也不行**：缺 perms 行 `--as staff` 调 `/lqg/embed/**` 恒 403。方案：派 EMBED-WEB-001 时把「搬 5300→5310」写进它的 §2（它的迁移里加 `DELETE FROM sys_role_menu/sys_menu WHERE menu_id BETWEEN 5300 AND 5307`）。 |
| WARN-2 | **S2** | counterfeit-risk | **染色筛选的 LIKE 模式少一个 `%` 就静默返回 0 行**（本票第一版实测） | 第一版写的是 `(',' \|\| stain_types \|\| ',') LIKE ',' \|\| ? \|\| ','`（= `',IHC,'`）→ 语义变成「整串恰好等于 `,IHC,`」→ `HE,IHC` 一行都查不到（accept 3 第 5 段实测 `total:0`）。修法 = 模式两侧补通配 `'%,IHC,%'`。**下游注意**：SAMPLE-HINT-001 若也用「补逗号 LIKE」筛染色，务必用 `'%,值,%'` 并在单测里断言**完整模式串**（本票的 `EmbedQueryContractTest` 已从「断言 `,IHC,`」收紧成「断言 `%,IHC,%`」）。方案：把这句写进 SAMPLE-HINT-001 / EMBED-WEB-001 的必读。 |
| WARN-3 | S3 | clarify | **`PUT /lqg/embed` 的字段语义（patch vs 整份替换）ticket 没写** | 本实现取 **patch**（`null` = 不动），`markers` 例外（传了就整组替换）。依据：ticket §1「会被反复打开补填」+ EMBED-MP-001 明文要 patch。影响：EMBED-WEB-001 的编辑抽屉若按「整份表单替换」预期，会观察到「没传的字段没被清空」。方案：在契约那一行补一句 PUT 的字段语义。 |
| WARN-4 | S3 | clarify | **`paraffinBlockNo` 筛选的匹配方式 ticket / 契约都没写** | 本票取**模糊**（它是工作台搜索框）。影响：若 EMBED-WEB-001 的 fixture 期望精确匹配，会多出前缀命中的行。方案：在契约 EMBED 那行注明「`paraffinBlockNo` 模糊、`internalNo` 等值」。 |
| WARN-5 | S3 | debt | **给下游预置的读口没有端到端 accept**（`handlerName` / `mine` / `sort=recent` 预置给 EMBED-MP-001；`pageByWrapper` / `listBySampleIds` / `entity` 预置给 AUTH-EXT-002） | 本票的 4 条 accept / 22 条单测都不走 `/mp/int/embed` 与 `/mp/ext/embed`（边界：那两个 controller 不在本票）。它们只有代码与形状契约（`EmbedShapeContractTest` 钉了字段），没有运行时证据。方案：EMBED-MP-001 / AUTH-EXT-002 的 accept 直接打这两组端点（它们的票面已经这么写了）。 |
| WARN-6 | S2 | harness（既有，本票不重复计数） | **D1 回归包把 flyway 迁移总数写死成 7** —— 本票的迁移把它推到 11，那条断言仍假红 | 取证见 §5：D1 的 7 支逐个点名全绿、无失败行；红的是 `--eq 7`。state.json 已记（D2 r1 L0+L1 片首次命中）。方案：改成断「D1 那 7 支版本号逐个存在且无 `success=false`」，别断总数。**本票没改回归包**（`doc/waves/regression/**` 不在 `touches`）。 |

> 已在上游报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数、`api.sh --fresh-module` 沙箱恒非 0、`api.sh` token 缓存只看 mtime、`updateById` 忽略 null、`PageQuery` 只有两参构造、`@EncryptField` 的 `ENC_` 前缀与裸 Base64 不同源、`reseed.sh` 清不掉运行时按手机号建的账号（本票收尾跑了 `clean-orphan-accounts.sh`）。
>
> **本票新踩、已在上游记过形态的坑**：顶层裸 `.or()`（D2 S1 #105）—— 本票的 `mine` 那一组 OR 用 `and(w -> …)` 包住，`EmbedQueryContractTest` 用例 ② 断言 SQL 里出现 `AND (create_by = ? OR update_by = ?)`。

## §8 坑与解法（给下游，3-5 行）

1. **`@SaCheckPermission` 的权限串必须在 `sys_menu` 有行，否则是 403 不是 500**：本票因此必须自带菜单迁移（5300-5307）。新票加权限串前先 `SELECT perms FROM sys_menu WHERE perms LIKE 'lqg:xxx%'` 核一遍，别等 accept 报 403 才回头（SAMPLE-VERIFY-001 坑 1 的同源形态）。
2. **MP 的 `apply(sql, args)` 是惰性登记的**：不先渲染一次 SQL（`getTargetSql()` / `getSqlSegment()`），`getParamNameValuePairs()` 是**空 map** —— 本票单测初稿就因此假红（第二个 wrapper 里 `containsValue(",HE,")` 为 false）。凡是要拿参数做断言的测试，先渲染再取参数。
3. **「补逗号 + LIKE」的整元素匹配，通配符不能少**：`'%,HE,%'` 才是数组包含；漏成 `',HE,'` 会静默 0 行（accept 3 抓到的真 bug）。单测要断言**完整模式串**，只断言 `containsValue(",HE,")` 是没牙的。
4. **`@TableLogic` 只保证 `del_flag='0'`，不保证业务状态**：`SampleChildrenChecker` 的 children 判据必须显式叠 `verify_status='valid'`。少这一条，seed 里 1002 名下 extA 提交的**待核验**送样 2006 就会让 D2 的 `SAMPLE-VERIFY-001` accept 1 变红（D2 报告 WARN-4 / §7 坑 4 点名的那颗炸弹）—— 本票用 P1/P2/P3 三条探针 + `EmbedChildrenCheckerContractTest` 钉死。
5. **`update(null, LambdaUpdateWrapper)` 不填 `update_by`**（SAMPLE-MODEL-001 坑 1 的第三次命中）：`EmbedService.update` / `EmbedVerifyService` / `EmbedExternalService` 三处都显式 `.set(updateBy, …).set(updateTime, …)` —— EMBED-MP-001 的「经手人 = 最后动手的人」「改完排到历史最前」全靠它。

## §9 验证用长进程（ticket §4.4）

- **后端 java（8081）：收尾时已关**（`lsof -ti tcp:8081 -sTCP:LISTEN` → PID 4776 → `kill 4776`；**没用 `pkill -f ruoyi-admin.jar`**）。
- **docker 容器：留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），未停。
- **未占 8080 / 5432 / 6379**（Kevin 本机日常服务）。
- **DB 收尾** = `reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`。
- 前端 / 小程序**一个字节没动**，本票没有起过任何前端进程。
