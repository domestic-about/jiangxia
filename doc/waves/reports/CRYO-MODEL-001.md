# CRYO-MODEL-001 · 完工报告

- **ticket**：CRYO-MODEL-001（track CRYO / phase D4 / size M）—— **D4 链头**（CRYO-FLOW-001 / CRYO-REMIND-001 / CRYO-WEB-001 / CRYO-MP-001 四张票等它）
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑；唯一改动 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`，等价证据见 §4.0）
- **分支**：`task/D4`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 11:43:29`，后端进程 **PID 16591**（8081，dev profile + `--api-decrypt.enabled=false`）
  （★ 本票实跑过**两轮**：第一轮 11:38:20 / PID 83172 发现「本票注释里写了字面量 `14`」会打红 **CRYO-REMIND-001 accept 2 最后一段的 grep**，改词后重新打包重启，第二轮 11:43:29 / PID 16591 把三条 accept + 四条回归 + 探针**全部复跑一遍**；本报告贴的是第二轮）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,ddl_vs_ssot.py,seed/,gen_seed.py,verify.env,fixtures/**}`、`doc/tools/gen_ddl_pg.py`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`
- **未越界**：`code/plus-ui` / `code/miniapp` 一个字节没动；根 pom / `ruoyi-common-*` / `ruoyi-system` 没动；`touches` 声明的三条路径覆盖了全部改动（`cryo/batch/src/{main,test}/**` + `V20260924120*__CRYO-MODEL-001-*.sql`），**越界 0 条**
- **单测**：`Tests run: 144, Failures: 0, Errors: 0, Skipped: 0`（本票前 120 → 本票 **+24**）
- **回归**：`SAMPLE-VERIFY-001` accept 1+2 **全绿**（★ 对 1002 的 `valid→invalid` 仍放行）· `EMBED-MODEL-001` accept 2 **全绿** · `SAMPLE-WEB-001` accept 1 **全绿** · `ExtChokepointContractTest` **全绿** · D1 `verify.sh --skip-build` = **41 绿 / 1 红 / 0 环境错**（唯一那条红是**既有** harness 缺陷：D1 回归包把 flyway 迁移总数写死成 7，D2 起每加一支就假红）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D4`；`git status --porcelain` 只有本票 4 条路径 |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `SAMPLE-VERIFY-001` 报告 `status: **done**`；`VerifyTransitions` / `SampleVerifyService` / `SampleChildrenCheckers` 在盘且 8 用例绿 |
| 扫 `doc/change-log.md`（本票相关 CR） | ✅ PASS | 逐条核过，见下「CR 影响」 |
| 逐条取权威锚（8 个 `blueprint_refs`） | ✅ PASS | 全部 `authority_lint.py show` 过，逐条对照见 §1 |
| ADR-0010 / ADR-0009 | ✅ PASS | ADR-0010：**「冻存剩余支数允许直接改数字」在 rejected_values 里** → 剩余只算不存；ADR-0009：6 公共字段、无 `tenant_id` / `del_unique`、时间 `TIMESTAMP(0)`、日期 `DATE`、迁移由生成器出 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | 三个容器全程在跑；8081 起停只按 PID（`lsof -ti tcp:8081 -sTCP:LISTEN`）；**没碰 8080 / 5432 / 6379** |
| 动手前代码是绿的 | ✅ PASS | 改动前 `mvn -o -pl ruoyi-modules/ruoyi-lqg -am test` = `Tests run: 120, Failures: 0`（D3 收口值） |
| 必查环境纪律 | ✅ PASS | 只按 PID 关进程；**从没用过 `pkill -f 'ruoyi-admin.jar'`**；没跑 `api.sh --fresh-module`（沙箱禁 `ps`）；没跑 `--fresh-module` 变体、没改 `api.sh` |

**STOP 判定：无。** 未出现「上游产物缺失」「与权威冲突且无法判断」「环境不可用」三类硬阻塞。

### CR 影响（逐条核过）

| CR | 与本票的关系 | 判定 |
|---|---|---|
| **CR-20260917-04** | ★ **直接覆盖本票正文**：`FIELD:t_lqg_cryo_batch.init_qty`、`FLOW:F-CRYO-01.step1`、`F-CRYO-02.step4 / step5` 全部改写 —— 「初始支数建后不可改」被推翻成 **可改**，且改前要**锁批次行 + 从新初始支数出发按 `flow_time` 正序逐笔累加、任何一步 < 0 就拒**；accept 由「改被拒」翻成「改得动、改负被拒」 | **已按 CR 实现**：`CryoBatchService.update` 先 `selectByIdForUpdate`（`FOR UPDATE`）→ `CryoBalanceChecker.requireNonNegative` → 通过才 `update`；accept 2 第 8/10/12 段与 §4.4 P3 是它的机器证据 |
| **CR-20260917-05** | 冻存取用 / 补入 / 盘点 / 改删登记全在工作台，小程序只查看（写接口不在本票）；本票只保证读模型（`/lqg/cryo/batch/list`、`/{id}`）能被两端共用 | 不受影响（本票无 `/mp/**` 端点） |
| **CR-20260918-07** | 本票落两处：② 「历史编辑记录默认全中心 + `mine` 开关」→ `CryoQueryBo.sort/mine` + `CryoBatchVo.handlerName/mine`（给 CRYO-MP-001 预置）；⑤ 超期阈值改读系统参数 → 判定与阈值**不在本票**（CRYO-REMIND-001），本票一个字都不写阈值常量 | **已按 CR 实现**（超期面交 CRYO-REMIND-001，见 §7 WARN-1） |
| **CR-20260921-08 / CR-20260917-06** | 小程序视觉与图廊 | 纯前端，本票不碰 |

`authority_lint.py` 口径：本票不改 `flows` / `field-ssot` / `ui-index`（只读锚）。

## §1 口径复述（本张最容易做反的，逐条对 accept 与权威核）

| # | 口径（ticket §0 / §2，权威锚） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | ★ **表里没有「剩余支数」这一列，也不许加**；剩余 = `init_qty + SUM(未删流水的 delta)`，读时算（ADR-0010 的 rejected_values） | `CryoBatch` 12 业务列逐列照 SSOT；`CryoBatchVo.remainingQty` 是**读时算**的键；`CryoFlowMapper.selectDeltaSums` 一条 `GROUP BY` | accept 1 第 3 段 `%remain%/%current%/%stock%` = **0 行**；`ddl_vs_ssot` 逐列相符；`CryoShapeContractTest` 用例 ①（字段集精确相等 + 无 remain/current/stock 名） |
| 2 | ★ **初始支数可以改**（CR-20260917-04）：保存前**锁批次行** → 从**新的**初始支数出发按 `flow_time` 正序（同一时刻按 `id`）逐笔累加未删流水 → 任何一步 < 0 就拒；逐笔校验写成 **`CryoBalanceChecker`**（CRYO-FLOW-001 复用同一个） | `CryoBatchMapper.selectByIdForUpdate`（`FOR UPDATE`）+ `CryoBalanceChecker.requireNonNegative`；纯函数在 `org.dromara.lqg.cryo.batch` | accept 2 第 8/10/12 段：`init=10` 200、`init=1` 400（消息「已取走 2 支，冻存数量不能少于 2」）、库内仍 10；`CryoBalanceCheckerTest` 9 例（含「最终为正但中途为负」） |
| 3 | **冻存样品名称手填、系统不解析不自动拼**；代数单独一栏形如 `P3`（正则 `^P\d{1,3}$`） | `CryoBatch.cryoName` / `CryoBatch.passage`；全仓**没有任何**从 `cryoName` 拆代数 / 拼名称的代码 | accept 2 第 1 段 `passage:"3"` 被拒（`400 代数格式不对，应形如 P3（P + 1~3 位数字）：3`）；`CryoBalanceCheckerTest` 用例 ⑧（`3 / p3 / P1234 / 第3代 / P / 空` 六种全拒） |
| 4 | **暂存 -80 选「否」= 直接进液氮**：液氮储存位置**必填**；直接进液氮的批次**永远不参与超期提醒** | `CryoBalanceChecker.requireLn2Location`；`location` 判据 = `in_minus80='N'` **或** `to_ln2_time` 非空 → `ln2` | accept 2 第 3 段 `inMinus80:"N"` 无位置被拒（`400 直接进液氮（暂存 -80 选「否」）必须填液氮储存位置`）；accept 3 末段 3007（直接进液氮）`location=="ln2"` |
| 5 | **批次校验五条全部被拒且库里不变**：代数格式 / `initQty=0` / 直接进液氮缺位置 / 挂未核验有效样本 / `toLn2Time` 早于 `freezeTime` | `CryoBatchService.create`（五条校验全在第一条写操作之前，整段 `@Transactional`） | accept 2 第 1-5 段五次 400 + 第 6 段 `count(*) WHERE cryo_name LIKE 'T-BAD%'` = **0**；§4.4 P1 逐条贴出真实消息 |
| 6 | ★ **注册 `SampleChildrenChecker` 的冻存实现，判据与 embed 同源：只把「未删且已生效」的冻存批次算 children**；**不许打红 D2** | `CryoChildrenChecker`（`@Component`，一个 `selectCount`） | §4.4 P6（1009 名下只有冻存批次 → 改判无效被拒）· P7（1002 名下无冻存批次 → `valid→invalid` 仍放行，D2 accept 1 绿）· P8（软删的批次不算 → 放行）· `CryoChildrenCheckerContractTest` 5 例 |
| 7 | 菜单号段 CRYO = `5400-5499`，**别抢 CRYO-WEB-001 的号**（它的迁移是 `V20260924121*`，票面点名 5410 + 5411-5417） | 本票只落 **5401-5407 七个 F 权限行**，**刻意不建 C 页面菜单** | `sys_menu` 5401-5407 + `sys_role_menu` 授 101/102（§2.1 取证）；`getRouters` 里 `component='lqg/cryo/index'` 现在 **0 条** → CRYO-WEB-001 建 5410 后天然「恰 1 条」（见 §6 交接 2） |

**另外两条 ticket §2 硬要求**：

- 读模型每行带 `remainingQty / location / internalNo / sourceUnitName` ✅（accept 3 三段全绿；§4.4 P4 另贴筛选同源证据）。
- 剩余**用一条聚合查询按本页 id 集合算，别逐行查** ✅ —— p6spy 原文（§4.4(b)）：一页 7 行只有 **1 条** `GROUP BY`，入参是本页 7 个批次 id。

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway（**1 支**，在 `touches` 内）

**`code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609241200__CRYO-MODEL-001-cryo.sql`**（新增，153 行）

**取号依据 `doc/lint-profile.yaml`**：D4 = `20260924`；HHmm 域分段 **CRYO = 12xx**；SSOT 里两张表的 `migration` 字段都写着 `V202609241200__CRYO-MODEL-001-cryo.sql` → 取 **`1200`**。版本号大于已应用最大值 `202609231110` → `out-of-order=false` 下不会触发 `FlywayValidateException`（SYS-WEB-001 / AUTH-EXT-002 踩过跨域补号的坑）。CRYO-REMIND-001 的 `1205`、CRYO-WEB-001 的 `1210` 都在本支之后，互不打架。

★ **两张表 + 菜单必须在同一支迁移里**：accept 1 第 2 段断的是 `script LIKE 'V20260924120%__CRYO-MODEL-001-%'` 恰 **1** 行 —— 拆成 `1200` + `1201` 会让这一格变成 2 而红。

内容两段：

1. **第一段** = `python3 doc/tools/gen_ddl_pg.py --migration V202609241200__CRYO-MODEL-001-cryo.sql` 的**逐字节输出**（已用 Python 子串校验：`gen contained verbatim: True`）：`t_lqg_cryo_batch` 12 业务列 + `t_lqg_cryo_flow` 8 业务列，各带 ADR-0009 的 6 个公共字段与普通索引。
2. **第二段**（手写）：菜单 **5401-5407** 七个 `F` 按钮权限串 + `sys_role_menu` 授给 **101（lqg_admin）与 102（lqg_internal）**。

```
$ python3 doc/verify/db.py --sql "SELECT menu_id||':'||menu_name||':'||menu_type||':'||perms FROM sys_menu WHERE menu_id BETWEEN 5400 AND 5409 ORDER BY menu_id"
5401:冻存查询:F:lqg:cryo:list
5402:冻存详情:F:lqg:cryo:query
5403:冻存新增:F:lqg:cryo:add
5404:冻存修改:F:lqg:cryo:edit
5405:冻存删除:F:lqg:cryo:remove
5406:冻存导出:F:lqg:cryo:export
5407:冻存流水:F:lqg:cryo:flow
$ python3 doc/verify/db.py --sql "SELECT role_id||':'||string_agg(menu_id::text,',' ORDER BY menu_id) FROM sys_role_menu WHERE menu_id BETWEEN 5401 AND 5407 GROUP BY role_id ORDER BY role_id"
101:5401,5402,5403,5404,5405,5406,5407
102:5401,5402,5403,5404,5405,5406,5407
$ python3 doc/verify/db.py --sql "SELECT version||'|'||script||'|success='||success FROM flyway_schema_history WHERE version LIKE '2026092412%' ORDER BY installed_rank"
202609241200|V202609241200__CRYO-MODEL-001-cryo.sql|success=true      ← 启动时自动应用，无跨域补号告警
```

> ★ **为什么必须落菜单**（SAMPLE-VERIFY-001 坑 1 / EMBED-MODEL-001 踩过的同一坑）：`@SaCheckPermission` 的权限集合来自「角色 → `sys_menu.perms`」。缺这几行 `--as staff` 调 `/lqg/cryo/**` 恒 **403**（不是 500），accept 2/3 全红。
> ★ **为什么只建 F、不建 C**：CRYO-WEB-001 的 accept 2 同时要 `menu_id=5410` 与 `getRouters` 里 `component='lqg/cryo/index'` **恰 1 条**。本票若照 EMBED-MODEL-001 的先例把 5400 建成 C 页面菜单，就会逼 CRYO-WEB-001 回头搬号（EMBED-WEB-001 被 5300 逼着搬过一次）。**F 行足够过鉴权**（perms 与 `menu_type` 无关），页面菜单留给 CRYO-WEB-001 的 5410。
> ★ **5406 `lqg:cryo:export` / 5407 `lqg:cryo:flow` 是替下游落的权限行**：ticket §2 只点名 `{list,query,add,edit,remove,export}`；`lqg:cryo:flow` 是 **CRYO-FLOW-001** 需要的，而它自己的 `touches` 里**没有迁移**，它的 accept 首段就 `--as staff POST /lqg/cryo/batch/{id}/flow` 并要 200 —— 本票作为链头把这一串一并落下（见 §7 WARN-2）。两个端点本身**不在本票**（§3 边界）。

### 2.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.cryo.batch`）

| 文件 | 职责 |
|---|---|
| `CryoBalanceChecker.java` | ★ **纯函数，不碰库**（ticket §2 明说放 `org.dromara.lqg.cryo.batch`）：`Flow` 记录、`ordered`（`flow_time` 正序、同一时刻按 id）、`remaining`、`requiredInitQty`、`requireNonNegative`、`checkAndRemaining`；另含四条标量判据 `requirePassage`（`^P\d{1,3}$`）/ `requireFreezeTime` / `requirePositiveInitQty` / `requireInMinus80` / `requireLn2Location` / `requireLn2NotBeforeFreeze` / `locationOf`。**CRYO-FLOW-001 的写 / 改 / 删流水三处直接复用**（`FLOW:F-CRYO-02.step5` 要求「校验相同」） |
| `domain/CryoBatch.java` | `t_lqg_cryo_batch` 实体，**12 业务列逐列照 SSOT，没有剩余列**；`@TableLogic delFlag`；公共字段走 `BaseEntity` |
| `domain/CryoFlow.java` | `t_lqg_cryo_flow` 实体（本票只建表 + 读：算剩余与逐笔校验） |
| `domain/vo/CryoBatchVo.java` | 行 / 详情：`remainingQty` / `location` **读时算**、`internalNo` / `submitNo` / `sourceUnitName` / `sampleVerifyStatus` **读时带出**、`handlerName` / `updateByName` / `mine`（预置给 CRYO-MP-001） |
| `domain/vo/CryoFlowVo.java` | 流水行形状（**端点归 CRYO-FLOW-001**；它会在上面补 `balanceAfter` / `edited` / `updateByName`） |
| `domain/vo/CryoFlowDeltaRow.java` | 一条 `GROUP BY batch_id` 的原始结果行（`batchId` / `totalDelta`） |
| `domain/bo/CryoBatchSubmitBo.java` | 新增 / 修改入参（12 个可写字段，**没有 remainingQty 之类的键**） |
| `domain/bo/CryoQueryBo.java` | 列表筛选：`internalNo / cryoName / sampleId / location / freezeTimeBegin/End` + 预置的 `sort` / `mine`；显式无参构造（`PageQuery` 5.5.3 只有两参构造）。**刻意不声明 `overdueOnly`**（CRYO-REMIND-001 的活；声明了没实现 = 静默全表） |
| `mapper/CryoBatchMapper.java` | 唯一的自定义 SQL：`selectByIdForUpdate`（`FOR UPDATE` + 手写 `del_flag='0'`）—— 改初始支数 / 删批次前的**行锁** |
| `mapper/CryoFlowMapper.java` | 唯一的自定义 SQL：`selectDeltaSums(batchIds)` —— **一条 `GROUP BY` 算完整页剩余**，软删流水**手写** `del_flag='0'` 排除 |
| `guard/CryoChildrenChecker.java` | ★ 注册给 `SampleChildrenChecker` 的实现（判据见 §5 末） |
| `service/CryoBatchService.java` | 写侧：`create`（五条校验）/ `update`（锁行 + 逐笔校验 + patch，显式补 `update_by`）/ `remove`（有未删流水一律拒） |
| `service/CryoQueryService.java` | 读侧：五筛选 + 两档排序 + **两条批量查询装配**（剩余一条聚合、样本一条 IN）+ `listBySampleId` / `listFlows` / `entity` / `remainingOf` 给下游复用 |
| `controller/CryoBatchController.java` | `/lqg/cryo/batch`：`list / {id} / POST / PUT / DELETE {ids}` 五个端点 |

**接口清单（本票）**

```
GET    /lqg/cryo/batch/list?internalNo&cryoName&sampleId&location(minus80|ln2)
                            &freezeTimeBegin&freezeTimeEnd&sort&mine&pageNum&pageSize   lqg:cryo:list
GET    /lqg/cryo/batch/{id}                                                            lqg:cryo:query
POST   /lqg/cryo/batch   {sampleId, cryoName, passage, freezeTime, initQty, density,
                          inMinus80, frozenBy, toLn2Time, ln2Location, remark}          lqg:cryo:add
PUT    /lqg/cryo/batch   同形状 + id；patch 语义；initQty 改了就锁行逐笔校验            lqg:cryo:edit
DELETE /lqg/cryo/batch/{ids}  软删（有未删流水的不许删）                                lqg:cryo:remove
```

### 2.3 测试（`src/test/java/org/dromara/lqg/cryo/batch/`）

| 文件 | 例数 | 钉住什么 |
|---|---|---|
| `CryoBalanceCheckerTest.java` | 9 | ★ 最终为正但中途为负必须拒 / **同一时刻按 id 排序** / 剩余 = 初始 + Σdelta / 恰好取到 0 不算负 / 空集合不炸 / `ordered` 是纯函数 / 位置判据 / 代数格式六种反例 / 四条标量判据 |
| `guard/CryoChildrenCheckerContractTest.java` | 5 | ★ 判据只有一个 `selectCount` 且只按 `sample_id` / count=0 → false / **判据里没有 `verify_status`**（本表没有这一档）/ null 不查库 / `@TableLogic` |
| `domain/CryoShapeContractTest.java` | 5 | 两张表的列集与 SSOT **精确相等** / 没有 remain·current·stock 类字段 / 入参白名单 / VO 带读时两格 / 筛选 BO 不声明 `overdueOnly` |
| `service/CryoQueryContractTest.java` | 5 | ★ `location` 判据与行上同源（`(in_minus80=? OR to_ln2_time IS NOT NULL)`）/ `mine` 的 OR 被括号包住 / 两档排序 / WHERE 段没有裸 OR / **两条自定义 SQL 原文**（聚合自己写 `del_flag='0'`、取批次行 `FOR UPDATE`） |
| **合计** | **24** | `Tests run: 144`（本票前 120） |

## §3 对照 ticket `touches`

`touches` 三条（`cryo/batch/src/main/**`、`cryo/batch/src/test/**`、`db/migration/V20260924120*__CRYO-MODEL-001-*.sql`）**覆盖了本票全部改动**，**零越界**：14 个 Java 文件全在 `org.dromara.lqg.cryo.batch` 及其子包（`domain` / `domain/bo` / `domain/vo` / `mapper` / `guard` / `service` / `controller`，`CryoBalanceChecker` 按 ticket 原文放在包根）+ 1 支迁移 + 4 个测试类。没有碰根 pom、`ruoyi-common-*`、`ruoyi-system`、`sample/service/SampleNameResolver`（`handlerName` 复用它的现有口）、前端、小程序。

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，与 D1/D2/D3 全部票同源）

`doc/verify/api.sh` 第 71 行 `ps -o lstart=` 在本 subagent 沙箱被禁。按规矩**没有改 `api.sh`**。本票实测：

```
$ bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping
FRESH_EXIT=1
[stderr] doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
         doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
         date: illegal option -- d
```

**等价反 stale 证据**（覆盖那道守卫的**两个半边**，七项）：

```
jar            : code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
jar mtime      : 2026-09-22 11:43:29
8081 PID       : 16591                       （lsof -ti tcp:8081 -sTCP:LISTEN）
PID holds jar  : 2                           （lsof -p 16591 | grep -c 'ruoyi-admin/target/ruoyi-admin.jar'）
进程启动时刻    : 2026-09-22 11:43:43（epoch 1790048623 ≥ jar 1790048609 → True，libproc.proc_pidinfo，不用 ps）
lqg src newer  : []                          ← 没有比 jar 新的 ruoyi-lqg 源码
admin src newer: []                          ← 同上（migration 也在 jar 里）
嵌套 jar 复核   : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 org/dromara/lqg/cryo/batch/ 下 38 个条目
                 （15 个 .class + 内嵌类 + Javadoc），含 CryoBalanceChecker(+$Flow) / CryoBatchController /
                 CryoBatch / CryoFlow / CryoBatchSubmitBo / CryoQueryBo / CryoBatchVo / CryoFlowVo /
                 CryoFlowDeltaRow / CryoBatchMapper / CryoFlowMapper / CryoChildrenChecker /
                 CryoBatchService / CryoQueryService
迁移落地复核    : flyway_schema_history 出现且仅出现 202609241200|V202609241200__CRYO-MODEL-001-cryo.sql|success=true
                 （本票前该表最大 version = 202609231110，无跨域补号）
无阈值字面量复核 : CRYO-REMIND-001 accept 2 最后一段的 grep 现在过：
                 test "$(grep -rlE 'freeze_time|freezeTime' …/org/dromara/lqg --include=*.java \
                        | xargs grep -lE '\b14\b|OVERDUE_DAYS' | grep -v '/cryo/remind/' | wc -l)" = 0  → 命中文件数=0
```

**复跑脚本随报告落盘**：`doc/waves/reports/CRYO-MODEL-001/accept-runners/{cryo001-acc1,cryo001-acc2,cryo001-acc3}.sh`
（与本次实跑逐字相同，从工作区根或任意 cwd 执行均可；唯一差异 = 去掉 `--fresh-module`）。三条都把长链包进函数判**整条** rc，并在开头 `rm -f $TMPDIR/lqg-verify-token-*`（api.sh 的 token 缓存只按 mtime 判新鲜，改库 / 改身份后会假 401 / 假 200）。

> ★ 三条 accept 各自 `reseed` 首尾，**串行跑**（并行的第一条会被另一条的收尾 reseed 打断，EMBED-MODEL-001 踩过）。本报告的三条输出是**串行**实跑的。

### 4.1 accept 1 · DDL —— ✅

```
$ bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc1.sh
✓ 2 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）
1
ACCEPT-1 EXIT=0
ACC1_RC=0
```

`ddl_vs_ssot` 逐列相符；`flyway_schema_history` 里 `V20260924120%__CRYO-MODEL-001-%` 恰 **1** 行且 success；`t_lqg_cryo_batch` 上 `%remain%/%current%/%stock%` **0 行**。

**counterfeit 逐条排掉**：

```
$ # 「加了 remaining_qty 列并在写流水时回写」→ 第 3 段非空红；实际 0
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='t_lqg_cryo_batch' AND (column_name LIKE '%remain%' OR column_name LIKE '%current%' OR column_name LIKE '%stock%')" --eq 0
0
$ # 反证：VO 上**有** remainingQty（读时算的那一格），表上**没有** —— 两张表的列集与 SSOT 精确相等
$ python3 doc/verify/db.py --sql "SELECT string_agg(column_name, ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name='t_lqg_cryo_batch'"
id,sample_id,cryo_name,passage,freeze_time,init_qty,density,in_minus80,frozen_by,to_ln2_time,ln2_location,remark,create_dept,create_by,create_time,update_by,update_time,del_flag
$ python3 doc/verify/db.py --sql "SELECT string_agg(column_name, ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name='t_lqg_cryo_flow'"
id,batch_id,flow_type,delta,from_location,operator_name,flow_time,purpose,create_dept,create_by,create_time,update_by,update_time,del_flag
$ # 「流水表没建 create_dept」→ 公共字段缺失红；实际 6 个公共字段全在（上面两行末尾）
$ # 「可空的列被建成 NOT NULL」→ 实际（SSOT 里 nullable:true 的五列全 YES）
$ python3 doc/verify/db.py --sql "SELECT string_agg(column_name||'='||is_nullable, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name='t_lqg_cryo_batch' AND column_name IN ('to_ln2_time','ln2_location','density','frozen_by','remark')"
density=YES, frozen_by=YES, to_ln2_time=YES, ln2_location=YES, remark=YES
$ python3 doc/verify/db.py --sql "SELECT column_name||'|'||data_type||'|nullable='||is_nullable||'|default='||COALESCE(column_default,'-') FROM information_schema.columns WHERE table_name IN ('t_lqg_cryo_batch','t_lqg_cryo_flow') AND column_name IN ('del_flag','init_qty','delta','flow_time','freeze_time') ORDER BY table_name, column_name"
del_flag|character|nullable=NO|default='0'::bpchar
freeze_time|date|nullable=NO|default=-
init_qty|integer|nullable=NO|default=-
del_flag|character|nullable=NO|default='0'::bpchar
delta|integer|nullable=NO|default=-
flow_time|timestamp without time zone|nullable=NO|default=-
$ # 索引逐条对账（SSOT 两个普通索引 + 两个主键）
$ python3 doc/verify/db.py --sql "SELECT indexname||' => '||indexdef FROM pg_indexes WHERE tablename IN ('t_lqg_cryo_batch','t_lqg_cryo_flow') ORDER BY tablename, indexname"
idx_cryo_batch_remind => CREATE INDEX idx_cryo_batch_remind ON public.t_lqg_cryo_batch USING btree (in_minus80, to_ln2_time, freeze_time)
idx_cryo_batch_sample => CREATE INDEX idx_cryo_batch_sample ON public.t_lqg_cryo_batch USING btree (sample_id)
pk_lqg_cryo_batch => CREATE UNIQUE INDEX pk_lqg_cryo_batch ON public.t_lqg_cryo_batch USING btree (id)
idx_cryo_flow_batch => CREATE INDEX idx_cryo_flow_batch ON public.t_lqg_cryo_flow USING btree (batch_id, flow_time)
pk_lqg_cryo_flow => CREATE UNIQUE INDEX pk_lqg_cryo_flow ON public.t_lqg_cryo_flow USING btree (id)
```

### 4.2 accept 2 · STATE —— ✅

```
$ bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc2.sh
0
true
10|yes
10
0
ACCEPT-2 EXIT=0
ACC2_RC=0
```

逐段（原文 16 段 `&&` 链，`reseed` 之后起）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1-5 | 五次 `post {T-BAD1..T-BAD5} \| grep -qE '^(400\|500)'` | （静默） | 代数 `"3"` / `initQty:0` / 直接进液氮缺位置 / 挂 pending 样本 1002 / `toLn2Time` 早于 `freezeTime` —— **五次全拒**（逐条消息见 §4.4 P1） |
| 6 | `SELECT count(*) … cryo_name LIKE 'T-BAD%' --eq 0` | `0` | ★ **五次被拒之后库里一行都没多**（不是先落盘再报错） |
| 7 | `post {T-hga03-W-N-P12-EM2-1e5, passage:P12, initQty:4, density:1e5, frozenBy:李工}` | 命中 `^200` | 正向对照：不是「一律拒绝也能绿」 |
| 8 | `GET /lqg/cryo/batch/9000003001 \| jq -c '.data \| {id,sampleId,cryoName,passage,freezeTime,density,inMinus80,frozenBy}'` | `{"id":9000003001,"sampleId":9000001001,"cryoName":"T-hli01-GZ-N-P2-EM2-2e5","passage":"P2","freezeTime":"2026-09-02","density":"2e5","inMinus80":"Y","frozenBy":"李工"}` | 详情读模型完整 |
| 9 | `PUT /lqg/cryo/batch` + `initQty:10` 的 `.code==200` | `true` | ★ **初始支数改得动**（CR-20260917-04） |
| 10 | `SELECT init_qty \|\| '\|' \|\| CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END … id=9000003001 --eq "10\|yes"` | `10\|yes` | ★ 落库 10 且**记下修改人**（wrapper 显式补 `update_by` 生效） |
| 11 | `PUT … initQty:1` 的 `grep -qE '^(400\|500)'` | `400	已取走 2 支，冻存数量不能少于 2` | ★ **逐笔校验**：3001 已取走 2 支，改成 1 会让第 1 步为 −1 → 拒 |
| 12 | `SELECT init_qty … --eq 10` | `10` | ★ 被拒后库里不变（不是先 UPDATE 再校验） |
| 13 | `DELETE /lqg/cryo/batch/9000003001` 的 `grep -qE '^(400\|500)'` | `400	该批次已有 1 笔出入库登记，不能删除（删了「谁取走了几支」就查不到了）` | ★ 有流水的批次不许删 |
| 14 | `SELECT del_flag … --eq 0` | `0` | ★ 被拒后没软删 |
| 15 | `reseed.sh --yes` | （静默） | 收尾回干净 seed |

**counterfeit 逐条排掉**（ticket 点名的 5 种形态）：

```
$ # 「代数不校验格式」→ 段 1 会拿到 200；实测 400（见 §4.4 P1 的完整消息）
$ # 「直接进液氮不要求位置」→ 段 3 红；实测 400 直接进液氮（暂存 -80 选「否」）必须填液氮储存位置
$ # 「PUT 时静默忽略 initQty」→ 段 10 会不是 10|yes；实测 10|yes（段 9 也拿到 200）
$ # 「改初始支数不校验流水」→ 段 11 拿到 200；实测 400 且段 12 库内仍 10
$ # 「有流水的批次允许删除」→ 段 13 拿到 200、段 14 del_flag=1；实测 400 且 del_flag=0
$ # 走的是真实分支：段 4 用的是 seed 里真实的 pending 样本 1002、段 11 用的是 3001 真实存在的 −2 流水
$ python3 doc/verify/db.py --sql "SELECT init_qty || '|' || (SELECT COALESCE(SUM(delta),0) FROM t_lqg_cryo_flow WHERE batch_id=9000003001 AND del_flag='0') FROM t_lqg_cryo_batch WHERE id=9000003001"
8|-2                                   ← 3001 的真实账：初始 8、未删流水 −2 → 改成 1 必然为负
```

### 4.3 accept 3 · DATA —— ✅

```
$ bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc3.sh
9000003007:5
9000003001:6
9000003003:4
9000003005:2
9000003004:0
9000003006:5
9000003002:4
true
ACCEPT-3 EXIT=0
ACC3_RC=0
```
（★ 中间那七行是 `db.py --col-set` 打印的**直连库独立汇总**结果，`--col-set` 按集合语义输出、行序不固定；`true` 是最后一段 `jq -e` 的输出。第一轮实跑时行序是 `3001,3002,3003,3004,3005,3006,3007`，断言不依赖行序。）

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `GET '/lqg/cryo/batch/list?pageSize=100'` 的 `API=` 赋值（接口侧） | （被下面段 2 吃掉，不打印） | 接口给的逐批剩余 |
| 2 | `test "${API}" = "9000003001:6,…"` | 通过 | ★ **接口侧**逐批剩余与 seed 期望**逐字相等** |
| 3 | `db.py --sql "… b.init_qty + COALESCE(SUM(f.delta),0) …" --col-set "${API}"` | 上面 7 行（`--col-set` 打印取值）<br>`9000003001:6` … `9000003007:5` | ★★ **直连库独立汇总**（`LEFT JOIN` + `f.del_flag='0'`，与接口不同源）逐批一致 |
| 4 | `jq -e '([.rows[]\|select(.location=="ln2")\|.id\|tostring]\|sort)==["9000003003","9000003007"] and ([.rows[]\|select((.id\|tostring)=="9000003001")\|.internalNo]==["T-hli01"])'` | `true` | ★ `location` 判据（3003 先 -80 后转液氮、3007 直接进液氮）+ `internalNo` 读时带出 |
| 5 | `reseed.sh --yes` | （静默） | 收尾 |

**counterfeit 逐条排掉**：

```
$ # 「汇总时没过滤软删流水」→ 3002 的剩余会变成 3；实测 4（seed 的 3106 是软删的 −1，就是为这条埋的）
$ python3 doc/verify/db.py --sql "SELECT id||'|'||delta||'|'||del_flag FROM t_lqg_cryo_flow WHERE batch_id=9000003002"
9000003106|-1|1
$ # 「剩余为 0 的批次被列表过滤掉了」→ 少了 3004；实测它在
$ python3 doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003004 | jq -c '.data|{id,initQty,remainingQty,location}'
{"id":9000003004,"initQty":3,"remainingQty":0,"location":"minus80"}
$ # 「location 只看 in_minus80、不看 to_ln2_time」→ 3003 会被判成 minus80；实测它是 ln2
$ python3 doc/verify/db.py --sql "SELECT id||'|in_minus80='||in_minus80||'|to_ln2_time='||COALESCE(to_ln2_time::text,'NULL') FROM t_lqg_cryo_batch WHERE id IN (9000003003,9000003007) ORDER BY id"
9000003003|in_minus80=Y|to_ln2_time=2026-08-23
9000003007|in_minus80=N|to_ln2_time=NULL
$ # 「软删批次出现」→ 3008 不在 7 行里；详情也查不到
$ python3 doc/verify/api.sh --as staff --bizcode GET /lqg/cryo/batch/9000003008
400	冻存批次不存在
$ # 两侧不同源：一侧是接口（应用层聚合），一侧是 db.py 里独立写的 SQL；再用 seed 的期望值把两边一起钉住
```

### 4.4 追加证据（accept 之外的机器证据）

**(a) Java 单测：`Tests run: 144, Failures: 0, Errors: 0, Skipped: 0`（本票前 120，本票 +24）**

```
$ mvn -o -pl ruoyi-modules/ruoyi-lqg -am test -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome
[INFO] Running org.dromara.lqg.cryo.batch.CryoBalanceCheckerTest                    Tests run: 9, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.service.CryoQueryContractTest             Tests run: 5, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.guard.CryoChildrenCheckerContractTest     Tests run: 5, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.domain.CryoShapeContractTest              Tests run: 5, Failures: 0
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest                        Tests run: 4, Failures: 0   ← D2 的 L0 咽喉门仍绿
[INFO] Running org.dromara.lqg.sample.verify.VerifyTransitionsContractTest          Tests run: 8, Failures: 0
[INFO] Tests run: 144, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**(b) ★ 剩余是「一条聚合查询 + 手写 del_flag」的 p6spy 原文**（后端 dev 日志，一页 7 行的完整 SQL 序列）：

```
SELECT COUNT(*) AS total FROM t_lqg_cryo_batch WHERE del_flag = '0'
SELECT id,sample_id,…,del_flag,create_dept,create_by,create_time,update_by,update_time
  FROM t_lqg_cryo_batch WHERE del_flag='0' ORDER BY create_time DESC, id DESC LIMIT 100
★ SELECT f.batch_id AS "batchId", COALESCE(SUM(f.delta), 0) AS "totalDelta"
    FROM t_lqg_cryo_flow f WHERE f.del_flag = '0'
     AND f.batch_id IN ( 9000003007 , 9000003006 , 9000003005 , 9000003004 , 9000003003 , 9000003002 , 9000003001 )
   GROUP BY f.batch_id ORDER BY f.batch_id                    ← 1 条，不是 7 条；del_flag 手写（@Select 不吃 @TableLogic）
SELECT id,submit_no,…,internal_no,…,del_flag,… FROM t_lqg_sample WHERE id IN ( … ) AND del_flag='0'
```

**(c) ★ 对抗性探针**（`doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-probes.sh`，全文实跑输出）：

```
== P0 干净 seed 起点
批次(未删)=7
批次(含软删)=8
流水(未删)=5 流水(含软删)=6

== P1 五条被拒的批次校验（逐条真实消息）+ 库里 T-BAD* 行数
400	代数格式不对，应形如 P3（P + 1~3 位数字）：3
400	冻存数量必须是正整数（当前：0）
400	直接进液氮（暂存 -80 选「否」）必须填液氮储存位置
400	冻存只能挂到已核验有效的样本（当前状态：pending）
400	转移至液氮时间（2026-09-01）不能早于冻存时间（2026-09-17）
400	冻存样品名称不能为空
400	缺少所挂样本 id
0

== P2 正面：新建一条（挂已核验有效的 1005）
200	操作成功

== P3 改初始支数：10 成功（并记 update_by）；1 被拒 + 库内不变
{"id":9000003001,…,"freezeTime":"2026-09-02",…}
{"code":200,"msg":"操作成功"}
init=10 update_by=9000000101 update_time非空=true
400	已取走 2 支，冻存数量不能少于 2
库内 init=10
400	该批次已有 1 笔出入库登记，不能删除（删了「谁取走了几支」就查不到了）
库内 del_flag=0

== P4 位置筛选与行上 location 同源（3003 先 -80 后转液氮、3007 直接进液氮）
["9000003003","9000003007"]                                          ← ?location=ln2
["9000003001","9000003002","9000003004","9000003005","9000003006"]   ← ?location=minus80（7 减 2）
["9000003001:T-hli01-GZ-N-P2-EM2-2e5:6:minus80","9000003004:T-hli01-GZ-N-P5-EM2-1e5:0:minus80"]   ← ?internalNo=T-hli01

== P5 软删批次不出现 / 详情查不到；取空的批次仍在列表里
400	冻存批次不存在
{"id":9000003004,"initQty":3,"remainingQty":0,"location":"minus80"}
sample 1008 未删批次数=2        ← 3008 软删，不计

== P6 D4 L3：有冻存批次的样本改判无效被拒（1009 名下只有冻存批次、没有石蜡块）
500	该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效
1009 verify_status=valid

== P7 D2 回归：1002 名下没有冻存批次 → valid→invalid 仍放行（SAMPLE-VERIFY-001 accept 1 那一格）
{"code":200}
200	操作成功
1002 verify_status=invalid

== P8 软删的冻存批次不算 children：1005 建批 → 被拒；软删该批 → 放行
新建批次 id=2102241470470688770
500	该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效
200	操作成功
软删后 del_flag=1
200	操作成功
1005 verify_status=invalid

== P9 权限：外部身份进不了 /lqg/cryo/**
403	没有访问权限，请联系管理员授权
403	没有访问权限，请联系管理员授权
PROBES DONE
```

> ★ **P6 是最干净的一条**：1009（`T-oco01`，内部录入的类器官样本）名下**没有任何石蜡块**（embed 的 2001-2006 分别挂 1001/1004/1006/1002），所以那条 500 **只可能**来自本票注册的 `CryoChildrenChecker` —— 这是「D4 的 L3 要求已生效」的独立证据，不靠 1004（它同时有 embed 2003 + cryo 3005，会显得证据过定）。
> ★ **P8 是「软删不算 children」的对照**：1005 建一条批次 → 改判无效被拒；软删该批（无流水，删得掉）→ 同一请求放行。

**(d) 五条标量判据的边界**（`CryoBalanceCheckerTest` 的正面/反面，逐条）：

```
requirePassage : P3 / P12 / P999 通过；3 / p3 / P1234 / 第3代 / P / "" / null 全拒
requirePositiveInitQty : 4 通过；0 / -1 / null 全拒
requireLn2NotBeforeFreeze : toLn2Time == freezeTime 通过、null 通过；早一天拒
requireLn2Location : in_minus80='N' 缺位置拒、to_ln2_time 非空缺位置拒；不需要时原样返回（不清空）
locationOf : ('N',null)→ln2、('Y',转液氮)→ln2、('N',转液氮)→ln2、('Y',null)→minus80
ordered : 同一时刻按 id 升序（+3 在 −3 之前通过；把 id 对调则被拒）；流程时间相同、id 为 null 也不炸
```

## §5 回归自查（本票注册了 children checker，最容易打红 D2）

| 回归 | 命令 | 结果 |
|---|---|---|
| **D2 `SAMPLE-VERIFY-001` accept 1**（★ 对 **1002** 的 `valid→invalid`） | `bash doc/waves/reports/SAMPLE-VERIFY-001/accept-runners/verify001-acc1.sh` | **`ACCEPT-1 EXIT=0`** — 逐字输出：`pending\|-\|-` / `valid\|T-hli77\|9000000101\|Y\|external` / `9000001002:invalid:误判，退回` / `9000001003:valid:`（★ 1002 那一格**仍放行**） |
| **D2 `SAMPLE-VERIFY-001` accept 2** | `…/verify001-acc2.sh` | **`ACCEPT-2 EXIT=0`** — `external:3` / `internal:1` / `3` / `true` |
| **D3 `EMBED-MODEL-001` accept 2** | `…/EMBED-MODEL-001/accept-runners/embed001-acc2.sh` | **`ACCEPT-2 EXIT=0`** — `0` / `True` / `2` / `valid` |
| **D2 `SAMPLE-WEB-001` accept 1** | `…/SAMPLE-WEB-001/accept-runners/web001-acc1-api.sh` | **`ACCEPT-1 GREEN (exit 0)`** — 10 段筛选集合全对 |
| **D1 的 L0 咽喉门** | `ExtChokepointContractTest` | `Tests run: 4, Failures: 0`（本票的代码全在 `cryo` 包，四条不变量不受影响） |
| **D1 回归包** | `bash doc/waves/regression/D1/verify.sh --skip-build` | **41 绿 / 1 红 / 0 环境错，rc=1** —— 唯一那条红是**既有 harness 缺陷**（见下） |

```
$ D1_VERIFY_RC=1  green=41 red=1 env=0
$   ✓ L1.1 flyway 无失败行（success IS NOT TRUE 的行 = 0）
$   ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '15' 15
$ 失败 1 条：
$   - L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '15' 15
```

★ 这一条**不是**「以前好的坏了」：D1 的 7 支迁移**逐个点名全绿**、无失败行、迁移文件在源码树全绿；红的是 `SELECT count(*) FROM flyway_schema_history --eq 7` 这个**写死总数**的断言（D2 起每加一支迁移就假红一条）。state.json 已记 issue。**本票不重复计数**，只在 §7 引用。

**children checker 的注册现状与口径（`SampleChildrenCheckers.registeredCount()` = 2：EMBED + CRYO）**：

```sql
-- CryoChildrenChecker.hasChildren(sampleId) 的全部判据（一个 selectCount，@TableLogic 自动补 del_flag）
SELECT COUNT(*) FROM t_lqg_cryo_batch
 WHERE sample_id = ?      -- 只数这个样本名下的
   AND del_flag  = '0'    -- ★ 软删的批次不算（seed 的 3008 软删，挂 1008）
-- ★ 没有 verify_status 这一条：t_lqg_cryo_batch 没有核验状态列 —— 冻存批次由内部人员建、建出来即生效，
--   没有「待核验」这一档。这与 EmbedChildrenChecker（要显式叠 verify_status='valid'）的唯一差异，
--   是**逐表照该表的「生效」定义**写，不是「只靠 del_flag 就够」的简化。
```

**下游口径必须与它同源**（ticket 完工要求点名）：CRYO-REMIND-001 的 `countOverdue` / 列表 `tabCounts`、CRYO-MP-001 的工作表计数、SAMPLE-HINT-001 类的「这个样本冻了几批」，都读**同一个**「未删批次」口径；差别只在**再叠什么条件**（超期要叠 `in_minus80='Y' AND to_ln2_time IS NULL AND 剩余>0 AND 距今天数≥阈值`，那是**另一个问题**，不要混进 children 判据 —— 取空的批次仍是一条有效的下游记录，仍要能查到它的流水）。

## §6 遗留与 raise（ticket §4.3）

1. **越出 `touches` 的改动：0**。
2. **与 `doc/api-contract.md` 的差异**：
   - 契约 CRYO 一节列的 `POST /lqg/cryo/batch/export`（CRYO-WEB-001）、`POST/PUT/DELETE …/{id}/flow`、`PUT …/{id}/to-ln2`、`GET …/{id}/flows`（CRYO-FLOW-001）、`GET /lqg/cryo/overdue`（CRYO-REMIND-001）、`/mp/int/cryo/**`（CRYO-MP-001）**本票都不实现**（ticket §3 边界）。本票只落权限行并给出可复用读口（§2.2 末四行）。
   - 契约说 list 行带 `overdue / overdueDays`、响应另带 `tabCounts` —— 本票**刻意不预置**（见 §7 WARN-1）。
   - 契约说 list 参数含 `overdueOnly` —— 本票**不声明**（声明一个没人实现的筛选 = 静默全表）。
3. **没把握 / 需要主会话确认的口径**（不改权威，只报）：
   - **`PUT /lqg/cryo/batch` 的字段语义**：本实现 = **patch**（没传的不动），依据 `FLOW:F-CRYO-01.step1` 的「之后批次的每一项都可以改（含初始支数）」；前端抽屉若整份提交，两者等价。若甲方要「整份替换」，改 `CryoBatchService.update` 里那几行 `.set(条件, …)` 即可 → WARN-4。
   - **本票的 PUT 允许直接改 `toLn2Time` / `ln2Location`**（并校验「不早于冻存时间」「直接进液氮 / 已转液氮时位置必填」）：这样编辑抽屉一次提交就能改完；`PUT …/{id}/to-ln2`、`{id}/flows` 的主入口仍归 CRYO-FLOW-001。patch 语义下**清空不了**已经填过的位置（传空串会被必填判据拒）→ WARN-5。
   - **`frozenBy` 的默认值**：没传时取当前登录人昵称（与样本 / 包埋域的 `operatorName` 同款）；契约没写，accept 每次都给。
4. **给 CRYO-FLOW-001 的交接（必读）**：
   - 复用 `CryoBalanceChecker`（`Flow` 记录 + `ordered` / `remaining` / `requireNonNegative` / `requiredInitQty`）与它的 9 条单测 —— **别另写一份判据**（`FLOW:F-CRYO-02.step5` 要求「校验相同」）。
   - 复用 `CryoBatchMapper.selectByIdForUpdate`（`FOR UPDATE`，锁批次行后再重算）+ `CryoQueryService.listFlows(batchId)`（未删、时间倒序）。
   - `lqg:cryo:flow` 的权限行**本票已经落了**（5407），你不需要再落迁移（你的 `touches` 里本来也没有）。
   - 你的 accept 里 `GET /lqg/cryo/batch/list` 的 `remainingQty` 由本票提供，改动后当场生效（读时算，无缓存）。
5. **给 CRYO-REMIND-001 的交接（必读）**：
   - `overdue / overdueDays / tabCounts / overdueOnly` 与「默认排序超期置顶」必须在 `CryoBatchVo` / `CryoQueryBo` / `CryoQueryService` 上补 —— **这三个文件在本票的 `cryo/batch/**` 里，不在你的 `touches` 里**（见 §7 WARN-1）。
   - 别在 `cryo/remind/` 之外写阈值字面量：本票全仓**没有一个 `14`**，你的 accept 2 最后一段的 grep 现在就能过（口径见 §4.4 (c) 的 P1/P3 —— 本票的错误消息里也没有 `14`）。
6. **给 CRYO-WEB-001 的交接（必读）**：
   - 本票**没有建 C 页面菜单**，`getRouters` 里现在有 **0 条** `component='lqg/cryo/index'` → 你建 5410 后天然「恰 1 条」，**不需要**像 EMBED-WEB-001 那样搬号。
   - 我的权限行是 **5401-5407**（不是 5400-5407）。你若照 EMBED-WEB-001 的先例搬号，`DELETE … WHERE menu_id BETWEEN 5400 AND 5409` 能一次清干净；不搬也不会冲突（同串权限重复无害）。
7. **给 CRYO-MP-001 的交接**：`CryoQueryBo` 已带 `sort=recent` / `mine=true`，`CryoBatchVo` 已带 `handlerName` / `mine`（口径同 SAMPLE / EMBED：`handlerName` = **最后修改人**，没改过就是创建人，**不是** `frozenBy`；`mine` = `create_by OR update_by = 我`）。修改走 `CryoBatchService.update`（同一套逐笔校验，别另写 update）。
8. **明确没做（ticket §3 边界，逐条核过）**：不做写流水 / 转液氮接口（CRYO-FLOW-001）、不做超期判定与阈值参数（CRYO-REMIND-001）、不做页面与导出（CRYO-WEB-001 / CRYO-MP-001）、**不建液氮罐 / 架 / 盒的位置字典**（位置就是一段文本）、**不解析冻存命名、不校验命名里的代数与 `passage` 一致**、不做审批 / 用途统计 / 登记修改历史表。

## §7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | cross-ticket | **CRYO-REMIND-001 的 `touches`（`cryo/remind/**` + 一支迁移）装不下它的 §2 要求**：它要给 `/lqg/cryo/batch/list` 补 `overdue / overdueDays / tabCounts`、支持 `overdueOnly=true`、默认排序改成超期置顶 —— 这三样都落在本票的 `cryo/batch/{domain/vo/CryoBatchVo,domain/bo/CryoQueryBo,service/CryoQueryService}` 里 | 取证：本票迁移只建表与菜单；`CryoQueryBo` 里**故意**没有 `overdueOnly`（声明了没实现 = 静默全表，SAMPLE-VERIFY-001 WARN-2 的形态）。影响：CRYO-REMIND-001 照票面做会被 QA 判越界，或为了不越界写出「阈值另一个真相源」（它的 accept 2 最后一段 grep 会红）。方案（择一）：① 派 CRYO-REMIND-001 时把上述三个文件补进它的 `touches`；② 改它的票面，把「列表补 overdue/tabCounts」挪进本票（那要在本票就落超期判定，与它 depends_on 的方向相反）。**本票不改权威、也不预置 null**。 |
| WARN-2 | **S2** | cross-ticket | **本票替两个下游各落了一个权限行：5406 `lqg:cryo:export`（票面有）与 5407 `lqg:cryo:flow`（票面没有）** | 取证：`sys_menu` 5407 = `lqg:cryo:flow`。为什么必须落：**CRYO-FLOW-001 的 `touches` 里没有迁移**，而它的 accept 1 第一段就 `--as staff POST /lqg/cryo/batch/9000003002/flow … \| grep -qE '^200'` —— 缺这一行是 **403（不是 500）**，整条 accept 红。影响：CRYO-WEB-001 的 5411-5417 会再建一次同串（重复无害，sa-token 权限是字符串集合）。方案：把 `lqg:cryo:flow` 写进 CRYO-FLOW-001 的票面「权限由 CRYO-MODEL-001 落」或让它自己带迁移。**本票不改权威**。 |
| WARN-3 | S3 | debt / perf | **`handlerName` 逐行查 `sys_user`（本页 7 行 → 7 次同名 SQL）** | 取证见 §4.4(b)：一页 7 行的 SQL 序列里有 7 条 `SELECT … FROM sys_user WHERE user_id=…`。与 **EMBED-MODEL-001 同款**（它也是逐行 `SampleNameResolver.nameOf`），本票 ticket 只要求「**剩余**用一条聚合查询按本页 id 集合算」（已满足：1 条 `GROUP BY`）。方案：给 `SampleNameResolver`（SAMPLE 域，本票 `touches` 外）加一个 `namesOf(Collection<Long>)` 批量口，CRYO / EMBED 一起换。 |
| WARN-4 | S3 | clarify | **`PUT /lqg/cryo/batch` 的字段语义（patch vs 整份替换）ticket / 契约都没写** | 本实现取 **patch**（null = 不动），依据 `FLOW:F-CRYO-01.step1` 的「之后批次的每一项都可以改」。影响：CRYO-WEB-001 的编辑抽屉若按「整份表单替换」预期，会观察到「没传的字段没被清空」；CRYO-MP-001 的修改模式同理（它的 accept 只断「改了的字段落库、其他字段没被清空」——patch 正好满足）。方案：在契约那一行补一句 PUT 的字段语义。 |
| WARN-5 | S3 | clarify | **本票的 `PUT` 允许改 `toLn2Time` / `ln2Location`，而它们的主入口（`PUT …/{id}/to-ln2`）在 CRYO-FLOW-001** | 取证：`CryoBatchService.update` 里对这两列做了「不早于冻存时间」「直接进液氮 / 已转液氮时位置必填」的校验，且 patch 语义下**清空不了**已填的位置（传空串命中必填判据）。影响：两处入口若校验口径日后分叉，会出现「同一个字段两条规则」。方案：契约为这两列注明「编辑抽屉可改；`to-ln2` 是专用入口，口径一致（CRYO-FLOW-001 与本票共用 `CryoBalanceChecker`）」。 |
| WARN-6 | S3 | harness（**既有，不重复计数**） | `doc/verify/api.sh --fresh-module` 在 subagent 沙箱恒非 0（第 11 次命中）；本票实测 **exit 1** | `ps -o lstart=`（line 71）被禁 → `\|\| date -d` 回退在 macOS 也不认 → `set -e` 在赋值行退出。只影响 subagent 沙箱，人工 / CI 正常。本票用七项等价证据替代（§4.0）。state.json 已记。 |
| WARN-7 | S3 | harness（**既有，不重复计数**） | `reseed.sh` 清不掉运行时按手机号建的雪花 id 账号 | 本票收尾跑 `doc/waves/tools/clean-orphan-accounts.sh --yes`。state.json 已记。 |
| WARN-8 | S2 | harness（**既有，不重复计数**） | **D1 回归包把 flyway 迁移总数写死成 7** —— 本票的迁移把它推到 15，那条断言仍假红 | 取证见 §5：D1 的 7 支逐个点名全绿、无失败行；红的是 `--eq 7`。state.json 已记。**本票没改回归包**（`doc/waves/regression/**` 不在 `touches`）。 |

> 已在上游报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数、`api.sh` token 缓存只看 mtime、`update(null, LambdaUpdateWrapper)` 不填 `update_by`（本票已显式补）、`PageQuery` 只有两参构造（本票已写无参构造）、`@EncryptField` 的 `ENC_` 前缀与裸 Base64 不同源（本票无加密列）、`@SaCheckPermission` 缺菜单行是 403（本票已自带菜单迁移）、`reseed.sh` 清不掉运行时账号。

## §8 坑与解法（给下游，3-5 行）

1. **`@Select` 是自定义 SQL，MyBatis-Plus 的逻辑删不会自动补**：`selectDeltaSums` 与 `selectByIdForUpdate` 里的 `del_flag = '0'` 必须**手写**（`selectById` / `selectList` 才吃 `@TableLogic`）。少写一处，seed 里 3002 名下那条软删的 `−1` 就会把它的剩余从 4 变成 3（accept 3 抓到）。单测直接断言注解原文（`CryoQueryContractTest` 用例 ⑤）。
2. **`@TableLogic` 只保证 `del_flag`，不保证业务状态**（EMBED-MODEL-001 的教训）—— 但**要逐表照该表的「生效」定义写**：EMBED 要显式叠 `verify_status='valid'`；CRYO 表**没有**核验状态列，所以「已生效」就等价于「未删」，判据里**不该**出现 `verify_status`（`CryoChildrenCheckerContractTest` 用例 ③ 正面钉这个事实）。抄错表会写出一个恒 false 或恒 true 的检查器。
3. **顶层裸 `.or()` 会把整条 AND 链拆成 `(A AND B) OR C`**（D2 的 S1 #105）：`location` 与 `mine` 两组 OR 都用 `and(w -> …)` 包住，单测断言 `AND (in_minus80 = ? OR to_ln2_time IS NOT NULL)` 与 `AND (create_by = ? OR update_by = ?)` 的**括号形态**，不是断言「有 OR」。
4. **`update(null, LambdaUpdateWrapper)` 必须显式补 `update_by / update_time`**（SAMPLE-MODEL-001 坑 1 的第 N 次命中）：accept 2 第 10 段查的就是 `init_qty='10' AND update_by IS NOT NULL`；不补的话「谁把初始支数改了」永远查不到，而 CR-20260917-04 恰恰要求「记下修改人」。
5. **`--fresh-module` 在 subagent 沙箱恒非 0（本票 exit 1）**：等价证据要覆盖**两个半边** —— 源码不比 jar 新（`find … -newer jar` 两处为空）+ 进程不早于 jar（本票不用 `ps`，改 `libproc.proc_pidinfo` 拿 `pbi_start_tvsec`，再叠 `lsof -p PID` 持有 jar + 嵌套 jar 内含本票 class + `flyway_schema_history` 里出现本票那一行）。
6. **纯函数要「真纯」才测得动边界**：`CryoBalanceChecker` 不碰库、不读配置、不碰 Spring，所以「最终为正但中途为负」「同一时刻按 id」两条边界能用一个单测钉死；阈值 / 流水集合都当入参传进来（CRYO-FLOW-001 的新增流水是「内存里的那一笔 + 库里的那些」）。**`requiredInitQty` 是「最小合法初始支数」而不是「已取走总数」**：它是逐笔累加过程中最大的透支额，失败消息的 N 取的就是它（`CryoBalanceCheckerTest` 用例 ①③ 钉住，别按「Σ 负数」算错）。

## §9 验证用长进程（ticket §4.5）

- **后端 java（8081）：收尾时已关**（见 §10 收尾记录；`lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID → `kill <PID>`（第二轮 = 16591）；**没用 `pkill -f ruoyi-admin.jar`**，本机 8080 上有 Kevin 的另一个 java 服务）。起法 `bash .tmp/run-backend.sh`（gitignore）。
- **docker 容器：留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），未停。
- **未占 8080 / 5432 / 6379 / 9000 / 9001**（Kevin 本机日常服务）。
- **DB 收尾** = `reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`。
- 前端 / 小程序**一个字节没动**，本票没有起过任何前端进程。

---

## §10 收尾记录（ticket §4.5 / 派单「收尾两步」）

```
1) bash doc/verify/reseed.sh --yes                     # 回干净 seed
2) bash doc/waves/tools/clean-orphan-accounts.sh --yes # 清运行时按手机号建的雪花 id 账号
3) lsof -ti tcp:8081 -sTCP:LISTEN  →  PID 16591  →  kill 16591     # 只按 PID；没用 pkill -f 'ruoyi-admin.jar'
4) lsof -ti tcp:8081 -sTCP:LISTEN  →  （空）           # 8081 已无监听；8080 全程未碰
5) docker ps：lqg-dev-postgres(5433) / lqg-dev-redis(6380) / lqg-dev-minio(9002+9003) 全部保留在跑，未停
```

- **git**：只 `git add` 本票四条路径（`ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/batch/**`、`ruoyi-lqg/src/test/java/org/dromara/lqg/cryo/**`、`db/migration/V202609241200__CRYO-MODEL-001-cryo.sql`、`doc/waves/reports/CRYO-MODEL-001*`），**没有 `git add -A`**；未 push、未 merge、未动 `doc/waves/state.json` 与 `_manifest.json`。
- **本报告引用过的中间产物**（供复现，不是交付物）：`/tmp/cryo-probes.out` / `/tmp/cryo-probes2.out`（探针两轮实跑输出）、`/tmp/d1-cryo.log` / `/tmp/d1-cryo2.log`（D1 回归两轮）；正式取证脚本已随报告落盘在 `doc/waves/reports/CRYO-MODEL-001/accept-runners/`。
