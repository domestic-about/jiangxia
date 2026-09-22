# CRYO-FLOW-001 · 完工报告

- **ticket**：CRYO-FLOW-001（track CRYO / phase D4 / size M）—— **D4 第二张**：冻存流水的登记 / 修改 / 删除 + 并发正确性
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑全绿，真实输出见 §4）
- **分支**：`task/D4`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 12:00:28`，后端进程 **PID 13600**（8081，dev profile + `--api-decrypt.enabled=false`）
- **单测**：`Tests run: 159, Failures: 0, Errors: 0, Skipped: 0`（本票前 144 → 本票 **+15**：15 = 3 端点契约 + 7 并发/判据 + 5 标量规则）
- **回归**：`CRYO-MODEL-001` accept 1/2/3 **全绿** · `SAMPLE-VERIFY-001` accept 1 **全绿** · `ExtChokepointContractTest` **全绿（4 例）** · D1 `verify.sh --skip-build` = **41 绿 / 1 红 / 0 环境错**（唯一那条红是**既有** harness 缺陷：flyway 迁移总数写死成 7，issue #82）
- **权限**：`lqg:cryo:flow` = `sys_menu` 5407，由上游 CRYO-MODEL-001 落好，**本票不新增迁移、不改 `sys_menu`**
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/,gen_seed.py,fixtures/**,verify.env}`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`
- **未越界**：`touches` 声明的两条路径（`cryo/flow/src/{main,test}/**`）**覆盖了全部代码改动**；`cryo/batch/**`、根 pom、`ruoyi-common-*`、`ruoyi-system`、`code/plus-ui`、`code/miniapp` **一个字节没动**

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D4`；`git status --porcelain` 只有本票 3 条路径（`cryo/flow/src/{main,test}/**` + 报告目录） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `CRYO-MODEL-001.md` = `status: done`；`CryoBalanceChecker`（9 例绿）/ `CryoBatchMapper.selectByIdForUpdate`（`FOR UPDATE`）/ `CryoQueryService` 全在盘，本票**直接复用** |
| 扫 `doc/change-log.md`（本票相关 CR） | ✅ PASS | 逐条核过，见下「CR 影响」 |
| 逐条取权威锚（10 个 `blueprint_refs`） | ✅ PASS | 10 个锚全部 `authority_lint.py show` 过，逐条对照见 §1 |
| ADR-0010 全文 | ✅ PASS | `rejected_values` 里**有**「冻存剩余支数允许直接改数字」→ 剩余只算不存；本票的 `balanceAfter` 也是**读时算**，不落库 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | 三个容器全程在跑；8081 起停只按 PID；**没碰 8080 / 5432 / 6379 / 9000 / 9001** |
| 上游产物可用（两张表 + 权限行） | ✅ PASS | `t_lqg_cryo_batch` / `t_lqg_cryo_flow` 在库；`sys_menu` 5407 = `lqg:cryo:flow`；`flyway_schema_history` 15 支 success |
| 动手前代码是绿的 | ✅ PASS | 改动前 `mvn -o -pl ruoyi-modules/ruoyi-lqg -am test` = `Tests run: 144, Failures: 0`（上游收口值） |
| 必查环境纪律 | ✅ PASS | 只按 PID 关进程；**从没用过 `pkill -f 'ruoyi-admin.jar'`**；没跑 `api.sh --fresh-module`（沙箱禁 `ps`，见 §4.0）；没改 `api.sh` |

**STOP 判定：无。** 未出现「上游产物缺失」「与权威冲突且无法判断」「环境不可用」三类硬阻塞。

### CR 影响（逐条核过）

| CR | 与本票的关系 | 判定 |
|---|---|---|
| **CR-20260917-04** | ★ **直接覆盖本票正文**：「冻存流水只增不改不删」被推翻成**可改可删（软删）**；改删后按 `flow_time` 正序逐笔累加、任何一步 < 0 都拒（`FLOW:F-CRYO-02.step5`）；登记类型不可改；`from_location` 保持登记时的值 | **已按 CR 实现**：`CryoFlowService.update`（类型不同 → 400）/ `delete`（软删 + 逐笔重算）/ `create` 都先 `selectByIdForUpdate`；accept 2 的 13 段是它的机器证据 |
| **CR-20260917-05** | 小程序只查看：写接口**只在** `/lqg/cryo/**`，`/mp/int/cryo/**` 只有只读 `…/flows`，**不转发任何写操作** | **已按 CR 实现**：本票只在 `/lqg/cryo/batch/{id}/**` 下加 5 个端点，包里**没有任何 `/mp/**` 路由**（§4.4 P4 实测 `/mp/int/cryo/batch/x/flow` → 404 No endpoint） |
| **CR-20260918-07** | ② 历史编辑记录「默认全中心 + `mine` 开关」在 `cryo/batch/**`（上游已做）；⑤ 超期阈值不在本票 | 不受影响（本票不碰阈值，全仓仍无 `14` 字面量，§4.5 探针 ①） |
| **CR-20260921-08 / CR-20260917-06** | 小程序视觉与图廊 | 纯前端，本票不碰 |

---

## §1 口径复述（ticket §0/§2 逐条对 accept 与权威核）

| # | 口径（ticket §0 / §2，权威锚） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | ★ **登记可以改、可以删**（CR-20260917-04）。删 = **软删**；**登记类型不能改**；`from_location` **保持登记时的值** | `CryoFlowService.update`（`bo.flowType` 与原来不同 → 400）/ `delete`（`deleteById` = `@TableLogic` 软删）/ `from_location` 只在 `create` 里由批次当时位置带出，`update` 的 patch 实体里**没有这一列** | accept 2 第 2 段 `…|minus80|0|yes`、第 4 段改类型 400、第 8 段 `del_flag=1`、第 11 段删补入被拒；`CryoFlowConcurrencyTest` ④（改小放行且 `from_location`/`flow_type` 不变）、③（软删行仍在库里）；`CryoFlowEndpointContractTest` ②（`@TableLogic` 在 `delFlag` 上） |
| 2 | ★ **校验看每一步，不只看最后**：改/删之后从初始支数出发按 `flow_time` 正序（同刻按 id）逐笔累加，**任一步 < 0 都拒** | 改 / 删 / 改初始支数三处都调上游纯函数 `CryoBalanceChecker.requireNonNegative`（`ordered` 排序 + `requiredInitQty`）；**没有另写第二套判据**（`FLOW:F-CRYO-02.step5` 要求「校验相同」） | accept 2 第 6 段（把 3104 挪到 30 天前并改成 6 支 → 400、`-3|true` 库内不变）、第 11 段（删补入让后面不够 → 400）；`CryoBalanceCheckerTest` 9 例（含「最终为正但中途为负」）本票复跑仍绿 |
| 3 | `from_location` 由批次**当时所在位置自动带出**、不让人选：已登记转液氮（或直接进液氮）→ `ln2`，否则 `minus80` | `create` 里 `CryoBalanceChecker.locationOf(batch.inMinus80, batch.toLn2Time)`；入参 BO **刻意没有 `fromLocation` 字段** | accept 1 第 9 段集合里 `take:-1:ln2`（3003 已转液氮）；`CryoFlowSubmitBo` 上无 `fromLocation`（§4.4 P5 反射取证） |
| 4 | **取走数 > 剩余 → 拒绝**。★ 写 / 改 / 删流水前都对批次行 `SELECT … FOR UPDATE` 再重算 | 三个写方法第一件事都是 `CryoBatchMapper.selectByIdForUpdate`（上游 `@Select`，在手写 `del_flag='0'` 之后 `FOR UPDATE`）；改 / 删的 `flowId` 归属校验在其之前（避免无谓锁） | accept 1 第 4 段超取 9 支 → 400；accept 3 第 1-2 段 5 并发抢 2 支恰好 1 成功、剩余恰好 0；`CryoFlowConcurrencyTest` ①（10 线程各取 1 支、初始 3 → 恰好 3 成功、剩余 0） |
| 5 | 四个动作各自的校验：调整必须写原因；支数不能为负/零；转液氮登记后**批次位置变液氮**、`toLn2Time` 不早于 `freezeTime` | `deltaOf`（take/add 正整数、adjust ≠ 0）/ `purposeOf`（adjust 必填）/ `CryoBalanceChecker.requireLn2NotBeforeFreeze` + 位置必填 / `toLn2` 后 `in_minus80='Y'` + `to_ln2_time` 非空 → `location=ln2` | accept 1 第 5/6/7 段三条被拒 + 第 8 段 adjust 带原因 200；accept 3 第 3 段 `2020-01-01` → 400、第 5 段成功后 `.data.location=="ln2"` 且 `ln2Location` 对上；`CryoFlowRulesContractTest` ①② |
| 6 | **这些写接口只在 `/lqg/cryo/**`（工作台）**；`/mp/int/cryo/**` **只读 `…/flows`、不转发任何写操作** | `CryoFlowController` 的类级 `@RequestMapping("/lqg/cryo/batch/{id}")`；包里**没有** `mp` 包 / `@RequestMapping` 前缀 | §4.4 P4：`POST /mp/int/cryo/batch/9000003003/flow` → `404 No endpoint`；`CryoFlowEndpointContractTest` ③ 断类级前缀必须是 `/lqg/cryo/` |
| 7 | **被拒时库里必须什么都不变**（accept 每一段「被拒」后面都跟库内断言） | 所有校验都在第一条写操作之前，方法一个 `@Transactional(rollbackFor = Exception.class)` | accept 1 第 9 段集合**精确相等**（4 行，多一行就红）、accept 2 第 5/7/12 段库内断言；`CryoFlowConcurrencyTest` ②⑥ |
| 8 | `POST` 权限 `lqg:cryo:flow`；`PUT/DELETE …/flow/{flowId}` 同权限；`PUT …/to-ln2` 用 `lqg:cryo:edit`；`GET …/flows` 用 `lqg:cryo:query` | 四个方法上的 `@SaCheckPermission` 逐字 | `CryoFlowEndpointContractTest` ①（五条路由 + 方法 + 权限串**精确相等**） |
| 9 | `GET …/flows`：未删流水、时间倒序、每行带 `balanceAfter`（正序累计算、不落库）、`edited`、`updateByName`、`updateTime` | `CryoFlowService.list`：`CryoBalanceChecker.ordered` 正序算 `balanceAfter` → 按 `flow_time DESC, id DESC` 输出 → `edited` / `updateByName` 逐行装配 | §4.4 P2（3003 三行：`4 / 7 / 5`；改一笔后 `3 / 6 / 4` + `edited=true` + `updateByName=李工`） |
| 10 | ★ **判据是「那一刻的剩余」不是「整条序列的最小初始支数」**（本票实跑踩到的实现坑，见 §8-②） | `create` 用 `requireInsertable`（把已有流水按 `flow_time` 走一遍拿到当时余额，再要 `余额 + delta ≥ 0`）；`update` / `delete` 才用 `requireNonNegative`（它们会改历史） | accept 2 第 12 段「初始 5 + 补入 2 → 取走 7 支全部取用」= 200；`CryoFlowConcurrencyTest` ⑦ 钉这一条 |

---

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway

**0 支。** 本票 `touches` 里本来就没有迁移；两张表与 `lqg:cryo:flow` 权限行（`sys_menu` 5407）都由 `CRYO-MODEL-001` 的 `V202609241200__CRYO-MODEL-001-cryo.sql` 落好（开工前实测 `flyway_schema_history` 15 支 success、5407 在册）。

### 2.2 后端（`ruoyi-lqg`，新包 `org.dromara.lqg.cryo.flow`）

| 文件 | 职责 |
|---|---|
| `flow/controller/CryoFlowController.java` | 五个端点（`@RequestMapping("/lqg/cryo/batch/{id}")`）：`POST /flow`、`PUT /flow/{flowId}`、`DELETE /flow/{flowId}`、`PUT /to-ln2`、`GET /flows`。`GET` 先经 `CryoQueryService.entity` 判批次存在（不存在 → 400「冻存批次不存在」，与详情口同语义） |
| `flow/service/CryoFlowService.java` | ★ 全部写侧 + 流水读侧：`create` / `update` / `delete` / `toLn2` / `list`。**每个写方法都是「锁批次行 → 读未删流水 → 逐笔校验 → 通过才写库」**，复用上游 `CryoBalanceChecker` 与 `CryoBatchMapper.selectByIdForUpdate` |
| `flow/domain/bo/CryoFlowSubmitBo.java` | 登记入参：`flowType / qty / purpose / operatorName / flowTime`。**刻意没有 `fromLocation`**（不让人选） |
| `flow/domain/bo/CryoFlowEditBo.java` | 改登记入参：`qty / purpose / operatorName / flowTime` + `flowType`（只用于「传了且不同 → 400」，改不了它） |
| `flow/domain/bo/CryoToLn2Bo.java` | 转液氮入参：`toLn2Time / ln2Location` 都是 `String`（**纯日期 `yyyy-MM-dd` 与带时间两种都接**，见 §8-④） |
| `flow/domain/vo/CryoFlowRecordVo.java` | 流水行：上游列形状 + `balanceAfter / edited / updateByName / remainingQty`（四格全读时算）。★ **名字不叫 `CryoFlowVo`**，见 §8-① |

**接口清单（本票）**

```
POST   /lqg/cryo/batch/{id}/flow            {flowType:take|add|adjust, qty, purpose?, operatorName?, flowTime?}  lqg:cryo:flow
PUT    /lqg/cryo/batch/{id}/flow/{flowId}   {qty?, purpose?, operatorName?, flowTime?, flowType?}                lqg:cryo:flow
DELETE /lqg/cryo/batch/{id}/flow/{flowId}   软删                                                 lqg:cryo:flow
PUT    /lqg/cryo/batch/{id}/to-ln2          {toLn2Time, ln2Location}                             lqg:cryo:edit
GET    /lqg/cryo/batch/{id}/flows           未删流水，时间倒序；每行带 balanceAfter / edited / updateByName  lqg:cryo:query
```

### 2.3 测试（`src/test/java/org/dromara/lqg/cryo/flow/`）

| 文件 | 例数 | 钉住什么 |
|---|---|---|
| `service/CryoFlowConcurrencyTest.java` | 7 | ★ **10 线程各取 1 支、初始 3 → 恰好 3 成功、剩余恰好 0**（真实线程池 + JDK 动态代理当假库，代理里用 `ReentrantLock` 模拟 `FOR UPDATE` 的行锁语义）· 超取被拒后库里一笔不多 · 删补入让后面不够 → 拒且一笔没被软删 · 软删不是物理删 · 改大到透支 → 拒且 delta 不变 · 改类型 → 400 · adjust 缺原因/为 0、take 负数/0、未知类型 → 全拒且库不变 · ★ 判据是「那一刻的剩余」（初始 5 + 补入 2 → 取 7 全取 200） |
| `service/CryoFlowRulesContractTest.java` | 5 | take/add 正整数且 `delta = ∓qty` · adjust 带符号不为 0 且原因必填 · 时间两种形态都接（`yyyy-MM-dd HH:mm:ss` / 纯日期）、乱写的格式拒 · `edited` 三条判据 · 流水 VO 的四格都在 |
| `controller/CryoFlowEndpointContractTest.java` | 3 | ★ 五条路由 + HTTP 方法 + 权限串**精确相等** · `CryoFlow.delFlag` 上有 `@TableLogic`、`selectByIdForUpdate` 的 SQL 里 `FOR UPDATE` 与手写 `del_flag='0'` · 类级前缀必须 `/lqg/cryo/`（没有任何 `/mp/**`） |
| **合计** | **15** | `Tests run: 159`（本票前 144） |

### 2.4 报告与取证脚本（`doc/waves/reports/CRYO-FLOW-001/`）

`accept-runners/flow001-acc1.sh` · `flow001-acc2.sh` · `flow001-acc3.sh`（与 ticket 的 `run` 逐条对应；两处偏差见 §4.0/§4.1）

---

## §3 对照 ticket `touches`

`touches` 两条（`cryo/flow/src/main/**`、`cryo/flow/src/test/**`）**覆盖了全部代码改动**，**零越界**：6 个 Java 主文件全在 `org.dromara.lqg.cryo.flow`（`controller` / `service` / `domain/bo` / `domain/vo`）+ 3 个测试类。没有碰根 pom、`ruoyi-common-*`、`ruoyi-system`、`cryo/batch/**`（上游那个 `CryoFlowVo` 一个字没改，见 §8-①）、前端、小程序。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，与 D1-D4 全部票同源）

`doc/verify/api.sh` 第 71 行 `ps -o lstart=` 在本 subagent 沙箱被禁。按规矩**没有改 `api.sh`**。等价反 stale 证据（八项）：

```
jar            : code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
jar mtime      : 2026-09-22 12:00:28（epoch 1790049628）
8081 PID       : 13600                    （lsof -ti tcp:8081 -sTCP:LISTEN）
PID holds jar  : 2                        （lsof -p 13600 | grep -c 'ruoyi-admin/target/ruoyi-admin.jar'）
进程启动时刻    : 1790049650 ≥ jar 1790049628 → True
                 （libproc.proc_pidinfo 的 pbi_start_tvsec，不用 ps；等价于「12:00:50 启动」）
lqg src newer  : []                       ← 没有比 jar 新的 ruoyi-lqg 源码
admin src newer: []                       ← 同上（migration 也在 jar 里）
嵌套 jar 复核   : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 org/dromara/lqg/cryo/flow/** 18 个条目
                 （6 个 .class + 内嵌类 + Javadoc：CryoFlowController / CryoFlowService /
                  CryoFlowRecordVo / CryoFlowSubmitBo / CryoFlowEditBo / CryoToLn2Bo）
上游产物复核    : flyway_schema_history 202609241200|V202609241200__CRYO-MODEL-001-cryo.sql|true
                 sys_menu 5407|lqg:cryo:flow
```

> ★ jar 在 12:00:28 重新打过一次（**修掉 §8-② 的判据缺陷后重新打包**），后端随之**按 PID 重启**（旧 2464 → 新 13600），三条 accept + 全部回归都是**在这一次重启之后**跑的。

### 4.1 accept 1 · DATA —— ✅

```
$ bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc1.sh
take:-2:minus80
add:1:minus80
take:-1:ln2
adjust:-1:minus80
true
9000003002:2
9000003003:3
ACCEPT-1 EXIT=0
```

（第 5 行 `true` = `remainingQty` 集合断言；第 6-7 行是 `--col-set` 打印的**直连库独立汇总**。）

**★ 本 runner 与 ticket 的两处偏差（唯一的两处，逐条说明）：**

1. ticket 的 `flow()` **没有** `--bizcode`，而 `api.sh` 不带它时吐的是**原始 JSON**（首字符 `{`），原文的
   `grep -qE '^200'` / `'^(400|500)'` 在这种输出上**恒不成立**（第一版 runner 照抄原文 → **实跑红**，
   连一个 `&&` 都过不去）。改成断同一个「业务码」字段：`grep -qE '"code":200'` / `'"code":(400|500)'`
   —— 判据等价（都是业务码，不是 HTTP 码），不依赖 `--bizcode` 的 `code\tmsg` 形态。
2. 去掉 `--fresh-module ruoyi-lqg`（沙箱禁 `ps`），补上面 §4.0 的八项等价证据。

**counterfeit 逐条排掉：**

```
$ # 「take 的 delta 存成正数、靠 flow_type 现算加减」→ 第 9 段集合里会是 take:2:minus80 → 红；实际 take:-2:minus80
$ python3 doc/verify/db.py --sql "SELECT flow_type||'|'||delta FROM t_lqg_cryo_flow WHERE batch_id=9000003002 AND del_flag='0' AND create_time > now() - interval '5 minutes' ORDER BY id"
take|-2
add|1
adjust|-1
$ # 「超取只在前端用步进器上限拦」→ 第 4 段照收 → 红；实际 400 取走支数超过当前剩余（当前剩余 3 支，本次要 9 支）
$ # 「取自位置让前端传」→ 3003 已转液氮却记成 minus80 → 集合红；实际那条是 take:-1:ln2
$ python3 doc/verify/db.py --sql "SELECT b.in_minus80||'|'||COALESCE(b.to_ln2_time::text,'-') FROM t_lqg_cryo_batch b WHERE b.id=9000003003"
Y|2026-08-23
$ # 「被拒的四次有任意一次落了流水」→ 集合是精确相等（不是包含），多一行就红；实际恰好 4 行
$ # 两侧不同源：接口的 remainingQty vs db.py 里独立写的 LEFT JOIN 汇总 → 第 5/6 段两边都钉住 seed 期望值
```

### 4.2 accept 2 · STATE —— ✅

```
$ bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc2.sh
true
-1|复苏培养（更正）|minus80|0|yes
-1|复苏培养（更正）
-3|true
true
1
true
true
2
true
9000003001:7
9000003003:2
9000003006:0
ACCEPT-2 EXIT=0
```

逐段（原文 16 段，`reseed` 之后起；本 runner 与 ticket **逐字相同**，无 `--fresh-module`）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `PUT /lqg/cryo/batch/9000003001/flow/9000003101 {"qty":1,…}` 的 `.code==200` | `true` | ★ **改登记改得动**（CR-20260917-04） |
| 2 | `SELECT delta\|\|purpose\|\|from_location\|\|del_flag\|\|update_by是非空 … id=9000003101` | `-1\|复苏培养（更正）\|minus80\|0\|yes` | ★ 支数改了 / 用途改了 / **`from_location` 保持登记时的 `minus80`** / 未删 / **记下了修改人** |
| 3 | 同一笔再 `PUT {"flowType":"add",…}` 的 `grep '^(400\|500)'` | （静默） | ★ **登记类型不能改** |
| 4 | `PUT /lqg/cryo/batch/9000003002/flow/9000003101`（**跨批次**）的 `grep '^(400\|404\|500)'` | （静默） | ★ 用 3002 的路径改不到 3001 的登记（`flowId` 归属校验 → 404） |
| 5 | `SELECT delta\|\|purpose … id=9000003101` | `-1\|复苏培养（更正）` | ★ 两次被拒之后库里不变 |
| 6 | `PUT …/9000003003/flow/9000003104 {"qty":6,"flowTime":now-30d}` 的 `grep '^(400\|500)'` | （静默） | ★★ **挪早 + 加量**：从初始 6 出发，30 天前那一步是 −6、10 天前那一步是 −3，**逐笔算的有一步为负 → 拒**（若只看最终剩余 5 支就会放过） |
| 7 | `SELECT delta\|\|(flow_time::date = CURRENT_DATE - 10) … id=9000003104` | `-3\|true` | ★ 被拒后 delta 与时间都没动 |
| 8 | `DELETE …/9000003003/flow/9000003103` 的 `.code==200` | `true` | 删一笔补入（删完剩余 4 → 2，仍非负）放行 |
| 9 | `SELECT del_flag … id=9000003103` | `1` | ★ **软删**（`@TableLogic`），行还在库里 |
| 10-11 | `POST 3006 {add,2}` `.code==200`、`POST 3006 {take,7}` `.code==200` | `true` `true` | ★★ 本票**实跑红过**的那一段：初始 5 + 补入 2 → 取走 7「全部取用」必须成功（见 §8-②） |
| 12 | `DELETE /lqg/cryo/batch/9000003006/flow/${A}`（那笔 add）的 `grep '^(400\|500)'` | （静默） | ★ **删补入让后面的取走不够 → 拒**（若放行，剩余会变成 −2） |
| 13 | `SELECT count(*) … batch_id=9000003006 AND del_flag='0'` | `2` | ★ 被拒之后一笔都没被软删 |
| 14 | `GET /lqg/cryo/batch/list?pageSize=100` 的 `jq -e`（id:remainingQty 集合） | `true` | ★ 接口侧：`3001:7 / 3003:2 / 3006:0` |
| 15 | 直连库 `init_qty + SUM(delta)` 集合断言 | `9000003001:7` `9000003003:2` `9000003006:0` | ★★ 与接口**不同源**的独立汇总，两边都等于 seed 钉住的期望值 |

**counterfeit 逐条排掉：**

```
$ # 「只校验最终剩余、不逐笔算」→ 第 6 段拿到 200 → 红；实际 400
$ python3 doc/verify/db.py --sql "SELECT init_qty||'|'||COALESCE((SELECT SUM(delta) FROM t_lqg_cryo_flow WHERE batch_id=9000003003 AND del_flag='0'),0) FROM t_lqg_cryo_batch WHERE id=9000003003"
6|-2                                    ← 最终剩 4；30 天前那一步是 −6（初始 6，一步就空），所以「改成 6 支 + 挪到最早」必然让某一步为负
$ # 「删除做成物理删除」→ 第 9 段查不到行 → 红；实际 del_flag=1
$ # 「删补入不校验后面的取走」→ 第 12 段拿到 200 → 红；实际 400，且第 13 段仍是 2 笔未删
$ # 「改登记允许换类型」→ 第 3 段拿到 200 → 红；实际 400（登记类型不能改）
$ # 「不校验 flowId 归属」→ 第 4 段改到了 3001 的登记 → 第 5 段红；实际 404 且 3001 那笔没动
$ # 「改登记时把 from_location 按批次当前位置重算 / 没记 update_by」→ 第 2 段的 minus80 / yes 红；实际都对
$ # 两侧不同源：接口 remainingQty（第 14 段）vs db.py 独立 LEFT JOIN 汇总（第 15 段），期望集合由 seed 钉住
```

### 4.3 accept 3 · STATE —— ✅

```
$ bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc3.sh
0
true
true
ACCEPT-3 EXIT=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `for i in 1..5` 并发 `POST /lqg/cryo/batch/9000003005/flow {"take",2}` 后 `grep -c '^200'` | `test … = 1` 通过 | ★★ **5 个真实 HTTP 请求抢最后 2 支：恰好 1 个成功**（写流水前先 `SELECT … FOR UPDATE` 锁批次行） |
| 2 | `SELECT init_qty + SUM(delta) … b.id=9000003005` | `0` | ★★ **剩余恰好 0、不为负** |
| 3 | `PUT …/9000003001/to-ln2 {"toLn2Time":"2020-01-01"}` 的 `grep '^(400\|500)'` | （静默） | ★ 转液氮时间早于冻存时间（冻存 = 20 天前）→ 400 |
| 4 | `PUT …/to-ln2 {"toLn2Time":today,"ln2Location":"3号罐-2架-C1"}` 的 `.code==200` | `true` | 正常登记 |
| 5 | `GET /lqg/cryo/batch/9000003001` 的 `jq -e '.data.location=="ln2" and .data.ln2Location=="3号罐-2架-C1"'` | `true` | ★ **登记后批次位置当场变液氮** |

**counterfeit 排掉：**

```
$ # 「写流水时没锁批次行（先 SELECT 剩余、再 INSERT）」→ 5 个都读到剩余 2、都成功 → 成功数 5、剩余 −8 → 红
$ #   本票的锁在 CryoFlowService.create 的第一条读流水之前：SELECT * FROM t_lqg_cryo_batch WHERE id=? AND del_flag='0' FOR UPDATE
$ # 「转液氮不校验时间先后」→ 2020-01-01 那段拿到 200 → 红；实际 400
$ #   400 转移至液氮时间（2020-01-01）不能早于冻存时间（2026-09-02）
$ # 结尾 reseed：本 runner 最后一段就是 reseed.sh --yes（不把转了液氮的 3001 留给后面的断言）
$ # 5 个并发文件分别落在 /tmp/lqg-race-{1..5}.txt，断言完 rm -f（收尾干净）
```

### 4.4 追加证据（accept 之外的机器证据）

**(P1) Java 单测：`Tests run: 159, Failures: 0, Errors: 0, Skipped: 0`（本票前 144，本票 +15）**

```
$ mvn -o -pl ruoyi-modules/ruoyi-lqg -am test -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest                 Tests run: 4, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.CryoBalanceCheckerTest            Tests run: 9, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.service.CryoQueryContractTest     Tests run: 5, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.guard.CryoChildrenCheckerContractTest  Tests run: 5, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.domain.CryoShapeContractTest      Tests run: 5, Failures: 0
[INFO] Running org.dromara.lqg.cryo.flow.controller.CryoFlowEndpointContractTest  Tests run: 3, Failures: 0
[INFO] Running org.dromara.lqg.cryo.flow.service.CryoFlowConcurrencyTest    Tests run: 7, Failures: 0
[INFO] Running org.dromara.lqg.cryo.flow.service.CryoFlowRulesContractTest  Tests run: 5, Failures: 0
[INFO] Tests run: 159, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**(P2) `GET …/{id}/flows`：`balanceAfter` 正序算、时间倒序给；改一笔后三行全跟着变**

```
$ bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003003/flows | jq -c '[.data[]|{id:(.id|tostring),type:.flowType,delta,from:.fromLocation,t:(.flowTime|tostring),after:.balanceAfter,edited,ubn:.updateByName}]'
[{"id":"9000003104","type":"take","delta":-3,"from":"ln2","t":"2026-09-12 10:00:00","after":4,"edited":false,"ubn":"测试管理员"},
 {"id":"9000003103","type":"add","delta":2,"from":"ln2","t":"2026-09-02 10:00:00","after":7,"edited":false,"ubn":"测试管理员"},
 {"id":"9000003102","type":"take","delta":-1,"from":"minus80","t":"2026-08-18 10:00:00","after":5,"edited":false,"ubn":"测试管理员"}]
         ← 3003 的真实账：初始 6 →(−1) 5 →(+2) 7 →(−3) 4；倒序输出但 after 是正序算的

$ bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003003/flow/9000003102 '{"qty":2,"purpose":"药敏实验（更正）"}' | jq -c '.code'
200
$ bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003003/flows | jq -c '[.data[]|{id:(.id|tostring),delta,t:(.flowTime|tostring),after:.balanceAfter,edited,ubn:.updateByName}]'
[{"id":"9000003104","delta":-3,"t":"2026-09-12 10:00:00","after":3,"edited":false,"ubn":"测试管理员"},
 {"id":"9000003103","delta":2,"t":"2026-09-02 10:00:00","after":6,"edited":false,"ubn":"测试管理员"},
 {"id":"9000003102","delta":-2,"t":"2026-08-18 10:00:00","after":4,"edited":true,"ubn":"李工"}]
         ← -1 → -2 之后：三步全变（5→4 / 7→6 / 4→3）；edited=true；updateByName 从创建人（测试管理员）变成修改人（李工）
```

**(P3) 「不存在」与「跨批次」两条语义**

```
$ bash doc/verify/api.sh --as staff --bizcode GET /lqg/cryo/batch/9000003999/flows
400	冻存批次不存在
$ bash doc/verify/api.sh --as staff --bizcode DELETE /lqg/cryo/batch/9000003003/flow/9000003999
404	冻存出入库登记不存在
```

**(P4) ★ 小程序侧写接口不存在（CR-20260917-05）**

```
$ bash doc/verify/api.sh --as staff --bizcode POST /mp/int/cryo/batch/9000003003/flow '{"flowType":"take","qty":1,"purpose":"小程序不该有"}'
404	No endpoint POST /mp/int/cryo/batch/9000003003/flow.
$ grep -rn "mp/int\|/mp/" code/.../cryo/flow/**/*.java | grep -v '^\S*: \*'   # 只有 Javadoc 里提到，没有任何 @RequestMapping
（空）
```

**(P5) `fromLocation` 不在登记入参里（反射取证）**

```
$ javap / 反射：CryoFlowSubmitBo / CryoFlowEditBo 的字段集
CryoFlowSubmitBo : flowType, qty, purpose, operatorName, flowTime      ← 没有 fromLocation
CryoFlowEditBo   : qty, purpose, operatorName, flowTime, flowType      ← 没有 fromLocation
CryoToLn2Bo      : toLn2Time, ln2Location
```

**(P6) 不撞下游 grep：`CRYO-REMIND-001` accept 2 最后一段的探针现在仍过**

```
$ test "$(grep -rlE 'freeze_time|freezeTime' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src --include=*.java \
        | xargs grep -lE '\b14\b|OVERDUE_DAYS' | grep -v '/cryo/remind/' | wc -l | tr -d ' ')" = 0
命中文件数=0 PASS
```

---

## §5 回归自查

| 回归 | 命令 | 结果 |
|---|---|---|
| **上游 `CRYO-MODEL-001` accept 1**（DDL） | `bash …/CRYO-MODEL-001/accept-runners/cryo001-acc1.sh` | **`ACCEPT-1 EXIT=0`** — `✓ 2 张表与 SSOT 逐列相符…` / `1` |
| **上游 `CRYO-MODEL-001` accept 2**（STATE） | `…/cryo001-acc2.sh` | **`ACCEPT-2 EXIT=0`** — `0` / `true` / `10\|yes` / `10` / `0` |
| **上游 `CRYO-MODEL-001` accept 3**（DATA） | `…/cryo001-acc3.sh` | **`ACCEPT-3 EXIT=0`** — 7 行 `id:remaining` 全对（`3001:6 … 3007:5`）+ `true` |
| **D2 `SAMPLE-VERIFY-001` accept 1**（★ 对 1002 的 `valid→invalid` 仍放行） | `…/SAMPLE-VERIFY-001/accept-runners/verify001-acc1.sh` | **`ACCEPT-1 EXIT=0`** — `9000001002:invalid:误判，退回` / `9000001003:valid:` |
| **D1 的 L0 咽喉门** | `ExtChokepointContractTest` | `Tests run: 4, Failures: 0`（本票代码全在 `cryo` 包，四条不变量不受影响） |
| **D1 回归包** | `bash doc/waves/regression/D1/verify.sh --skip-build` | **41 绿 / 1 红 / 0 环境错，rc=1** —— 唯一那条红是**既有** harness 缺陷 |

```
$ D1_VERIFY_RC=1  green=41 red=1 env=0
$   ✓ L1.1 flyway 无失败行（success IS NOT TRUE 的行 = 0）
$   ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '15' 15
$ 失败 1 条：
$   - L1.1 D1 的 7 支迁移全部记录在案 → [FAIL] 期望 '7'，实际 '15' 15
```

★ 与 `CRYO-MODEL-001` §5 逐字一致（同一条既有假红，state.json 的 issue #82）。**本票不重复计数。**

> ★ **第一次跑 D1 时出现过 5 条红（L1.1 + L1.2 + L1.3c×3），不是实现缺陷**：那是**同一张 ticket 里
> 先跑 accept、后跑回归**留下的运行时账号（accept 用 `--as staff` mock 登录 + `POST /lqg/auth/staff`
> 建出按手机号授权路径的 `sys_user`，D1 的 L1.2/L1.3c 恰好查这两个手机号）。**处置**：
> `clean-orphan-accounts.sh --yes` + `reseed.sh --yes` 之后**重跑**，回到 41 绿 / 1 红（既有那条）。
> 这里如实记一笔：**跑 D1 回归前必须先清孤儿账号**，否则会把「跑过 accept 的副作用」误读成回归失败。

---

## §6 遗留与 raise（ticket §4.3）

1. **越出 `touches` 的改动：0。** 6 个主文件 + 3 个测试类全在 `org.dromara.lqg.cryo.flow` 及其子包。
2. **与 `doc/api-contract.md` 的差异（逐条核过，均为「契约有、本票不做」）：**
   - 契约 §CRYO 列的 `GET /lqg/cryo/overdue`（CRYO-REMIND-001）、`POST /lqg/cryo/batch/export`（CRYO-WEB-001）、`/mp/int/cryo/**`（CRYO-MP-001）**本票都不实现**（ticket §3 边界）。
   - 契约 `GET …/{id}/flows` 的字段清单本票**逐字实现**（`balanceAfter / edited / updateByName / updateTime` 都在；另加了便利键 `remainingQty` 只填在最新一笔上——**契约没写、也不冲突**）。
   - `PUT …/{id}/flow/{flowId}` 契约把 `flowType` 写成「传了不同的 → 400」：本票照此实现，**`flowType` 字段在 BO 里保留**（不收它就分不清「没传」与「传了不同的」）。
   - `POST …/{id}/flow` 契约没写 `adjust` 的 `purpose` 必填 —— 本票按 `FLOW:F-CRYO-02.step3`（权威）实现为**必填**。
3. **没把握 / 需要主会话确认的口径（不改权威，只报）：**
   - **`POST` 的时间入参口径**：契约与 ticket 都让 `flowTime` 可传。本票对 `create` 的判据用**「登记那一刻的余额」**（见 §8-②），所以：
     **带 `--bizcode` 之外的极端情形**——若有人在 `create` 时把 `flowTime` 特意改成一个**早于已有流水**的时刻，本票**不会**因此拒绝（那一刻余额够就放行）。这与 `PUT`（会改历史，必须整条序列重算）**不同**。理由与取舍见 §8-②；若甲方要求 `create` 也按整条历史重算，改 `CryoFlowService.create` 里那**一个** `requireInsertable` 调用为 `requireNonNegative` 即可 —— 但会让 accept 2 第 12 段「初始 5 + 补入 2 → 取走 7」变成 400（**这正是本票实跑红过的那一格**）。
   - **`operatorName` 缺省**：写侧在无 Sa-Token 上下文（单测）时为 null；接口路径上取当前登录人昵称（与样本 / 包埋域同款）。契约没写缺省，accept 每次都给。
   - **`GET …/{id}/flows` 的权限串**：契约没写；本票用已有权限行 5402 `lqg:cryo:query`（能看批次详情的人本来就该能看它的流水），**没有新造权限串、没有新迁移**。
4. **给 CRYO-WEB-001 的交接**：
   - 流水抽屉直接消费 `GET /lqg/cryo/batch/{id}/flows`（已带 `balanceAfter / edited / updateByName / updateTime`）；改 / 删登记走 `PUT|DELETE …/flow/{flowId}`，转液氮走 `PUT …/to-ln2`。**权限行不用你补**：5407 `lqg:cryo:flow` / 5404 `lqg:cryo:edit` / 5402 `lqg:cryo:query` 都已在册。
   - 前端的「取走支数」步进器**上限定多少都不影响后端**（后端按当时余额硬拦），但 `balanceAfter` 已经是现成的显示值。
5. **给 CRYO-MP-001 的交接**：
   - 小程序**只读**：`GET /mp/int/cryo/batch/{id}/flows`（本票的 service `list` 可直接复用；**别**把 `create/update/delete/toLn2` 转发到 `/mp/int/cryo/**`——CR-20260917-05 明文禁止，且 D1 的 `ExtChokepointContractTest` 会扫 `ext` 包的 mapper 持有）。
   - 本票的 `/lqg/cryo/**` 写接口**不感知来源端**；小程序若要「历史编辑记录里改本人录的冻存数量」，那一格改的是**批次的 `initQty`**（`PUT /mp/int/cryo/batch`，上游 `CryoBatchService.update` 已有同一套逐笔校验），**不是**改流水。
6. **给 CRYO-REMIND-001 的交接**：本票**没有**引入任何阈值字面量（§4.4 P6 探针 ① 现在过）；`to-ln2` 改了 `to_ln2_time` 之后「该批次出提醒清单」由你的判定读时算（`in_minus80='Y' AND to_ln2_time IS NULL` 这一组条件**本票没碰**）。
7. **明确没做（ticket §3 边界，逐条核过）**：不做页面（工作台 / 小程序）、不做审批与「取走申请」、不按用途做统计、**不建登记修改历史表**（只记最后修改人与时间）、不做超期提醒与 `overdue*`、不做导出。

---

## §7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| **WARN-1** | **S2** | harness | **本票的 `--fresh-module` 替代证据再多一项也替代不了「进程启动时刻」本身**：`ps -o lstart=` 在 subagent 沙箱被禁（第 12 次命中，issue #1/#13/#33 同源） | 本票用 `libproc.proc_pidinfo(pbi_start_tvsec)` 拿到启动秒（1790049650 ≥ jar 1790049628）作为等价证据，但这是 **macOS 专属** API，Linux/CI 上不成立。影响：**所有** D4 及之后的 API accept 首调都撞这个坑，且不同 agent 会各写一份替代脚本。方案：`api.sh` 把启动时间换成 `lsof` / 日志 mtime 的兼容写法（本票按规矩没改 `api.sh`）。 |
| **WARN-2** | **S2** | harness | **ticket accept 1 的 `grep -qE '^200'` 与 `api.sh` 的默认输出形态不兼容** —— 不带 `--bizcode` 时它吐的是原始 JSON（首字符 `{`），那个 grep **恒不成立**；本票第一版 runner 照抄原文（只去掉 `--fresh-module`）**第一段就红** | 影响：**任何** accept 里用 `api.sh`（不带 `--bizcode`）却 grep `^200` / `^(400\|500)` 的票，在 subagent 沙箱里都会「实现对了也永远红」。已被 `CRYO-MODEL-001` acc3 与 `EMBED-MODEL-001` acc2/acc4 踩过（它们的 runner 里也带着同样的形态）。方案（择一）：① 生成器出 accept 时，凡 grep 业务码一律要求 `--bizcode`（`code\tmsg`）或 `jq -e '.code==200'`；② `api.sh` 给一个「保留 fresh 守卫但不改输出」的组合开关。**本票 runner 的处置**见 §4.1（把 grep 换成断同一个 `"code":` 字段）。 |
| **WARN-3** | **S3** | harness | **同一张票里「先跑 accept、后跑 D1 回归」会让 D1 的 L1.2 / L1.3c 假红 5 条**（本票实测：L1.1 + L1.2 + L1.3c×3），根因是 accept 用 `--as staff` mock 登录与 `POST /lqg/auth/staff` 留下的运行时账号 | 影响：D1 回归包把「reseed 快照」当不变量，但**跑过任何 accept 之后都必须先 `clean-orphan-accounts.sh` + `reseed.sh` 才能重放**——否则会把副作用误读成回归失败（本票第一轮就误读了一次，多花一轮排查）。方案：把「D1 回归前先清孤儿 + reseed」写进 `zhixing` 的 QA 分片指令（或让 `verify.sh` 自己开头先清一次孤儿账号）。 |
| **WARN-4** | **S3** | debt | **`CryoFlowService` 的 `GET …/flows` 逐行查 `sys_user`**（本行数 × 1 次同名 SQL），与上游 `handlerName`（WARN-3 of CRYO-MODEL-001）同款 | 影响：一页 10 笔流水 = 10 次 `SELECT … FROM sys_user WHERE user_id=?`。方案：给 `SampleNameResolver` 加一个 `namesOf(Collection<Long>)` 批量口，CRYO / EMBED / SAMPLE 三处一起换。不属于本票 ticket 要求（它只点名 `balanceAfter / edited / updateByName` 三个键）。 |
| **WARN-5** | **S3** | clarify | **`POST …/{id}/flow` 与 `PUT …/{id}/flow/{flowId}` 的判据口径不同**（create = 「那一刻的余额」；update = 「整条序列逐笔重算」）—— ticket / 契约都没写这一区分 | 影响：`create` 时若特意把 `flowTime` 改到早于已有流水，本票不会因此拒绝（那一刻余额够就放行），而 `PUT` 会拒。取证：accept 2 第 12 段「初始 5 + 补入 2 → 取走 7」**必须**用「那一刻的余额」判（用最小初始支数判会误拒，本票实跑红过），而 accept 2 第 6 段「挪早 + 加量」**必须**用「整条序列」判 —— 两条合起来证明这个区分是**被 accept 钉死的**，不是实现随手选的。方案：在 `doc/api-contract.md` 的 `POST` 那一行补一句「判据 = 登记时刻的余额；`PUT` 判据 = 改后整条序列」。 |
| **WARN-6** | **S3** | harness | **MyBatis 类型别名按「短名」去重，两个包同名 VO 会让 Spring 启动即 `TypeException`** | 取证：本票第一版把流水行 VO 命名成 `org.dromara.lqg.cryo.flow.domain.vo.CryoFlowVo`（上游 `cryo/batch/domain/vo/CryoFlowVo` 已存在同名短名）→ 后端启动直接 `The alias 'CryoFlowVo' is already mapped to the value 'org.dromara.lqg.cryo.flow.domain.vo.CryoFlowVo'`，**整个应用起不来**（不是某个接口 500）。影响：D4 之后多个票会在同一个域里各建一份 VO / BO，**任何一个跨包同名都会让全后端起不来**，而现有单测（不带 Spring 上下文）**拦不住**它（本票的 159 条单测当时全绿）。方案：① 生成器在 ticket 里给「同域新增类的类名加域后缀」的约定；② 给 `ruoyi-lqg` 加一条「启动冒烟」契约测试（或 QA 分片把「后端能起来」列为独立断言）。本票处置见 §8-①。 |

> 已在上游报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数、`api.sh` token 缓存只看 mtime、`update(null, LambdaUpdateWrapper)` 不填 `update_by`（本票改走实体 `updateById`，由 `updateFill` 自动填，见 §8-③）、`PageQuery` 只有两参构造、`@SaCheckPermission` 缺菜单行是 403、D1 回归包 flyway 写死总数（issue #82）、`reseed.sh` 清不掉运行时账号（issue #34）。

---

## §8 坑与解法（给下游，3-5 行）

1. **★ MyBatis 的 `type-aliases-package` 按短名注册 —— 跨包同名 VO 会让 Spring 启动即挂，而单测拦不住。**
   本票第一版把流水行 VO 命名成 `org.dromara.lqg.cryo.flow.domain.vo.CryoFlowVo`，与上游
   `org.dromara.lqg.cryo.batch.domain.vo.CryoFlowVo` 短名相撞 → `sqlSessionFactory` 建 bean 失败，
   后端**根本没起来**（`The alias 'CryoFlowVo' is already mapped to …`），而当时 159 条单测**全绿**
   （它们不带 Spring 上下文）。改名成 `CryoFlowRecordVo` 即可，**别去动上游那个类**。
   （另：本票的 `touches` 只有 `cryo/flow/**`，改上游 VO 就是越界。）

2. **★★ 「剩余不为负」的判据要分清「那一刻的余额」与「整条序列的最小初始支数」—— 混用会误拒合法操作。**
   上游 `CryoBalanceChecker.requireNonNegative` 的 N 是**让整条序列合法的最小初始支数**（逐笔累加
   最大的透支额）。`create` 时直接用它判「已有流水 + 这一笔」会把
   「初始 5 + 补入 2 → 取走 7（全部取用）」判成 `requiredInitQty = 7 > 5` 而**拒掉**
   （accept 2 第 12 段就是这个形态，本票实跑红过）。正确做法：`create` 把已有流水按 `flow_time`
   走一遍拿到**当时余额**，再要 `余额 + delta ≥ 0`（透支时失败消息里的 N 就是 `-delta`，
   正好对上「当时只剩几支却取走了几支」）；而 `update` / `delete` **会改历史**，必须继续用
   整条序列的 `requireNonNegative`（accept 2 第 6 段「挪早 + 加量」靠它拦住）。两条判据都复用上游
   同一个纯函数与同一套排序，**没有另写第二套算法**。

3. **`update(null, LambdaUpdateWrapper)` 要手动补 `update_by`；改走实体 `updateById` 更省事。**
   本票的改登记用 `cryoFlowMapper.updateById(patch)`（patch 实体只有 `id` + 要改的列非 null）：
   MP 的 `updateFill` 会自动填 `update_by / update_time`（accept 2 第 2 段断的就是 `yes`），
   非 null 才进 SET（所以 `operatorName` 不传时不会被清空），且 `@TableLogic` 给 UPDATE 补
   `del_flag='0'`（改不到已软删的行）。`deleteById` 同理走逻辑删。

4. **时间入参一律用 `String` 接、自己解析，别直接用 `LocalDate` / `LocalDateTime`。**
   全局 Jackson 只注册了 `LocalDateTime` 的 `yyyy-MM-dd HH:mm:ss` 反序列化器；`to-ln2` 的
   `toLn2Time` 在 accept 3 里传的是**纯日期** `"2020-01-01"`，直接绑 `LocalDate` 会在
   **实现正确**的情况下 400（verify/README 坑 4 的同型坑）。本票的 `parseDate` / `flowTimeOf`
   两种形态都接（纯日期补零点），乱写的格式才 400。

5. **accept 链里的「被拒」断言必须配「库里不变」的库内断言，且集合式断言要精确相等。**
   本票 accept 1 第 9 段用的是 `--col-set` **集合精确相等**（4 行，多一行就红）——这正是抓
   「被拒的操作偷偷落了流水」的形态；accept 2 的第 5/7/12 段各跟一条 `--eq` 库内断言。**别**把
   这种链写成一行 `a && b && c` 依赖 `set -e`：`set -e` 不管 `&&` 链非末尾命令的失败，
   实现错了会静默 exit 0（本票 runner 一律把整条链包进 `run()` 再判 `$?`）。

---

## §9 验证用长进程（ticket §4.5）

- **后端 java（8081）：收尾时已关**（见 §10；`lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID → `kill <PID>`（本轮 = 13600，之前 2464）；**没用 `pkill -f 'ruoyi-admin.jar'`**，本机 8080 上有 Kevin 的另一个 java 服务，全程没碰）。起法 `bash .tmp/run-backend.sh`（gitignore）。
- **docker 容器：留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），未停。
- **未占 8080 / 5432 / 6379 / 9000 / 9001**（Kevin 本机日常服务；8080 上的 PID 66428 是别人的，全程未碰）。
- **DB 收尾** = `reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`。
- 前端 / 小程序**一个字节没动**，本票没有起过任何前端进程。

---

## §10 收尾记录（ticket §4.5 / 派单「收尾两步」）

```
1) bash doc/verify/reseed.sh --yes                     # 回干净 seed
2) bash doc/waves/tools/clean-orphan-accounts.sh --yes # 清运行时按手机号建的雪花 id 账号（0 行）
3) lsof -ti tcp:8081 -sTCP:LISTEN  →  PID 13600  →  kill 13600   # 只按 PID；没用 pkill -f 'ruoyi-admin.jar'
4) lsof -ti tcp:8081 -sTCP:LISTEN  →  （空）           # 8081 已无监听；8080 全程未碰
5) docker ps：lqg-dev-postgres(5433) / lqg-dev-redis(6380) / lqg-dev-minio(9002+9003) 全部保留在跑，未停
```

- **git**：只 `git add` 本票三条路径（`ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/flow/**`、
  `ruoyi-lqg/src/test/java/org/dromara/lqg/cryo/flow/**`、`doc/waves/reports/CRYO-FLOW-001/**`），
  **没有 `git add -A`**；未 push、未 merge、未动 `doc/waves/state.json` 与 `_manifest.json`。
- **本报告引用过的中间产物**（供复现，不是交付物）：`/tmp/cryo-flow-build.log`（打包）、
  `/tmp/cryo-flow-backend{,2,3}.log`（三次启动）、`/tmp/cryo-flow-tests.log`（159 条单测）、
  `/tmp/d1-cryo-flow{,2}.log`（D1 两轮：第一轮 5 红 = 跑过 accept 的副作用，第二轮 1 红 = 既有假红）。
  正式取证脚本已随报告落盘在 `doc/waves/reports/CRYO-FLOW-001/accept-runners/`。
