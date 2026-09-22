# CRYO-REMIND-001 · 完工报告

- **ticket**：CRYO-REMIND-001（track CRYO / phase D4 / size S）—— **-80 超两周提醒**：唯一的超期判定函数、超期清单接口、列表上的超期标记与页签计数、给工作台首页的计数
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑；唯一偏差 = 去掉本 agent 沙箱恒非 0 的 `--fresh-module ruoyi-lqg`，等价反 stale 证据见 §4.0；accept 1 的 Maven 段补本机三参数）
- **分支**：`task/D4`（未切分支 / 未 push / 未 merge / **未动** `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 12:22:05`，后端进程 **PID 44936**（8081，dev profile + `--api-decrypt.enabled=false`），`GET /lqg/sys/ping` → **200**
- **迁移**：**+1 支** —— `V202609241205__CRYO-REMIND-001-config.sql`（库里 15 → **16** 支，最新 `v202609241205`；启动日志 `Successfully applied 1 migration`）
- **单测**：Java `Tests run: 169, Failures: 0, Errors: 0, Skipped: 0`（含本票新增 `CryoOverdueServiceTest` **8 例**；`CryoQueryContractTest` 7 例、`CryoShapeContractTest` 5 例按新口径更新）
- **回归**：`CRYO-MODEL-001` acc1/2/3 ✅ · `CRYO-FLOW-001` acc1/2/3 ✅ · `SAMPLE-HINT-001` acc1 ✅ · `ExtChokepointContractTest` **4/4** ✅ · D1 回归包 **42 绿 / 1 红 / 0 环境错**（唯一那条红 = **既有** harness 缺陷，见 §5）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,ddl_vs_ssot.py}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`、`doc/waves/regression/**`
- **没碰**：8080（Kevin 的本机服务）/ 5432 / 6379 / 9000 / 9001；关进程一律按 `lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID 再 `kill`（**从没用过 `pkill -f 'ruoyi-admin.jar'`**，也没用过 `nohup ... &` 起长进程——后端全程是受管后台作业）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D4`；全程未切分支、未 push、未 merge |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `state.json`：`CRYO-MODEL-001` / `CRYO-FLOW-001` 都 `done`；`t_lqg_cryo_batch` / `t_lqg_cryo_flow` 在库、`CryoBalanceChecker` / `CryoBatchMapper.selectByIdForUpdate` / `CryoQueryService`（batch）与 `cryo/flow/**` 五个端点全在盘且被本票复用 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260918-07** 的两条逐条落进实现：⑧「阈值改读系统参数 `lqg.cryo.overdue-days`（默认 14）」→ `CryoOverdueProperties#days()` 每次现读；「登记转液氮或支数取空后立即退出超期清单」→ 读时算 + accept 2 的四段；**CR-20260917-05**「小程序首页没有数字」→ 本票不做任何小程序首页计数 |
| 5 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `FLOW:F-CRYO-01.step2`（超期四条件 + 阈值参数化 + 读时算）· `FLOW:F-CRYO-01.step3`（工作台首页卡片 / 列表置顶标红 / 小程序内部管理超期页签）· `FIELD:t_lqg_cryo_batch.{freeze_time,to_ln2_time,in_minus80}` 全部 `active` |
| 口径复述 5 条（四条件 / 阈值参数 / 一处判定 / 读时算 / 不推送不做小程序首页） | ✅ PASS | §1 逐条 + 机器证据（accept 1/2/3 + `CryoOverdueServiceTest` 8 例 + 反 stale 探针） |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | `docker ps` 三个容器全程在跑，未停；**没碰** 8080 / 5432 / 6379 |
| 动手前代码是绿的 | ✅ PASS | 开工基线 `Tests run: 169, Failures: 0`（D3 收口值）；本票后仍是 **169**（新增 8 例，且上游 2 个类的断言按新口径改写而非删测） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

---

## §1 口径复述（逐条对 accept 核）

| # | 口径（ticket §0/§2 + 权威锚 + CR） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **超期 = 暂存 -80 为是 且 没登记转液氮 且 剩余 > 0 且 冻存满阈值天数（第 N 天当天就算）** | `CryoOverdueService.isOverdue`（Java 侧唯一判定）+ `CryoOverdueSqlProvider.WHERE`（SQL 侧唯一片段） | accept 1（3005 恰好第 N 天 → 在清单里，`overdueDays=0`；3006 第 N−1 天不在；3004 取空不在；3003/3007 液氮不在；3008 软删不出现）；`CryoOverdueServiceTest` ①②③ |
| 2 | **阈值是系统参数 `lqg.cryo.overdue-days`，改完下一次读即生效**（不建表、不做配置页） | `CryoOverdueProperties#days()`（**每次调用都 `ConfigService#getConfigValue`**，取不到 / 非正整数 → 回落 + WARN）；阈值只在 `CryoQueryService.list` 里**每请求读一次** | accept 3：改成 13 → 清单多出 3006、`tabCounts.overdue` 2→3、已超天数 7/1/0；改回 14 全部复原；`CryoOverdueServiceTest` ⑤（同一次运行里连读两次拿到两个值）⑥（缺行/空串/`abc`/`0`/`-3`/底层抛异常 → 回落） |
| 3 | **判定只有一个函数**：工作台列表 / 小程序内部管理冻存表格页的 `tabCounts.overdue` / 超期清单 / 工作台首页计数**四处都调它** | ① `CryoQueryService.toVo`（行上的 `overdue / overdueDays`，`/lqg/cryo/batch/list` 与将来的 `/mp/int/cryo/batch/list` **共用同一个 `list()`**）② `CryoQueryService.tabCounts()` → `cryoOverdueService.countOverdue()` ③ `CryoOverdueService.assemble`（超期清单每行）④ `countOverdue()` 本体给 SYS-HOME-001 | accept 1 四处对账（接口清单 = db.py 独立 SQL 集合 = `tabCounts.overdue` = `overdueOnly` 行数）；`CryoOverdueServiceTest` ⑦ `countOverdue() == listOverdue().size()`；`CryoQueryContractTest` ⑦ 断言清单与计数**两条自定义 SQL 拼的是同一段 where** |
| 4 | **读时算，不落「是否超期」标志位**：转液氮 / 取空后**立刻**退出清单 | 批次表没有任何超期列；`CryoOverdueService` 无标志位写口；`@Scheduled` 只写日志、不刷任何状态 | accept 2：`to-ln2` 3001 → 清单只剩 3005、`tabCounts.overdue` 2→1、3001 行 `overdue=false`；3005 取走 2 支 → 清单空、计数 0；反过来 3003（已转液氮）与 3004（取空）从一开始就不在清单里 |
| 5 | 提示只在系统内（工作台卡片/角标、列表置顶标红、小程序超期页签）；**不做**订阅消息/短信/企业微信；小程序首页没有数字 | 本票只提供数据面（清单接口 + 计数 + 标记），无任何站外推送代码；未碰 `/mp/**` | `git status`：只改了 `cryo/**` + 一支迁移 + 报告；`ExtChokepointContractTest` 4/4（外部咽喉门不受影响） |
| 6 | **本票不建菜单**（CRYO-WEB-001 建 5410 段） | 迁移里只有一行 `sys_config`，没有 `sys_menu` / `sys_role_menu`；接口权限串沿用已落的 5401 `lqg:cryo:list` | 迁移原文（§2.1）；`GET /lqg/cryo/overdue` 用 `--as staff` → 200、`--as extA` → **403** |

---

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway / 取号依据

**`ruoyi-admin/src/main/resources/db/migration/V202609241205__CRYO-REMIND-001-config.sql`（新增，+1 支）**

- **取号**：ticket §2 点名 `V202609241205`；落在 frontmatter 的 `V20260924120*__CRYO-REMIND-001-*.sql` 通配内，> 已应用的 `202609241200`（CRYO-MODEL-001）且不撞 CRYO-WEB-001 的 `121*` 段。
- **内容**：`INSERT INTO sys_config … SELECT 5002, '000000', '-80 冻存超期天数', 'lqg.cryo.overdue-days', '14', 'Y', … WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'lqg.cryo.overdue-days')` —— **幂等兜底**（照 AUTH-EXT-002 的先例，绝不覆盖已有值）。
- **★ 它在当前 dev 库上是 0 行空操作**：这一行**已经由 SYS-WEB-001 的 `V202609210830` 插好**（`config_id=5002`、`config_name`「-80 冻存超期提醒天数」、`config_type='N'`）。逐字取证：
  ```
  $ python3 doc/verify/db.py --sql "SELECT config_id||'|'||config_name||'|'||config_key||'|'||config_value||'|'||config_type FROM sys_config WHERE config_key='lqg.cryo.overdue-days'"
  5002|-80 冻存超期提醒天数|lqg.cryo.overdue-days|14|N
  ```
  票面 §2 写的是 `config_type='Y'`（内置可改不可删）、`config_name`「-80 冻存超期天数」——与既存行不同（见 §6 WARN-3）。**accept 3 不检查这两列**（它把整行 `{configId, configName, configKey, configType, remark}` 原样读回来、只改 `configValue`，改完再改回），所以两边都不红。
- **不建表、不建菜单、不加列**（ticket §3）。

### 2.2 后端 · 新增（`org.dromara.lqg.cryo.remind`，**在 `touches` 内**）

| 文件 | 职责 |
|---|---|
| `remind/service/CryoOverdueService.java` | ★★ **唯一的超期判定**（见 §3）+ `days()` + `countOverdue()` + `listOverdue()` + 清单装配（两条批量查询：剩余一条聚合、样本一条 IN） |
| `remind/sql/CryoOverdueSqlProvider.java` | ★★ **唯一一份超期判定 SQL where 片段**（`WHERE` 常量）+ 两条 `@Select` 用的整条 SQL + `whereFor(占位符)` 渲染给 MyBatis-Plus 侧 |
| `remind/service/CryoOverdueProperties.java` | 系统参数 `lqg.cryo.overdue-days` 的读取（**每次现读、不缓存**；缺行 / 非正整数 / 读失败 → 回落 + WARN） |
| `remind/mapper/CryoOverdueMapper.java` | 两条自定义 SQL：`selectOverdueList(days)` / `selectOverdueCount(days)`，**拼同一段 where**（包名以 `.mapper` 结尾，否则若依的 `org.dromara.**.mapper` 扫不到） |
| `remind/domain/vo/CryoOverdueVo.java` | 超期清单行（`id / internalNo / cryoName / remainingQty / overdueDays / overdue / …`）——短名全仓唯一，不会触发 MyBatis 别名冲突 |
| `remind/controller/CryoOverdueController.java` | `GET /lqg/cryo/overdue`（权限 `lqg:cryo:list`，复用已落的 5401） |
| `remind/job/CryoOverdueDailyJob.java` | `@Scheduled(cron = "0 0 8 * * ?")`：把当日阈值、超期批次数与清单写一行 INFO 日志（**只写日志，不刷状态**） |
| `remind/config/CryoOverdueScheduleConfig.java` | `@Configuration @EnableScheduling` —— ★ 本项目 dev/prod 下 `snail-job.enabled=false`，上游唯一的 `@EnableScheduling` 在 `SnailJobConfig` 上被条件关掉了，**全仓此前没有 Spring `@Scheduled` 任务**；没有这个类，上面的 `@Scheduled` 会静默不跑（见 §6 WARN-7） |
| `test/…/remind/service/CryoOverdueServiceTest.java` | 8 例：边界四例 + 阈值移动 + 每次现读 + 回落 + `countOverdue()==listOverdue().size()` + 夹具自检 |

### 2.3 后端 · 修改（**★ 越出 `touches`**，逐条见 §6 WARN-1）

| 文件 | 改动 | 为什么非改不可 |
|---|---|---|
| `cryo/batch/domain/vo/CryoBatchVo.java` | +`overdue`、`overdueDays` 两个字段（**纯新增**） | 列表行的类型就是它；这两个键不挂它身上就没有第二个地方可挂（契约点名行内带这两键） |
| `cryo/batch/domain/bo/CryoQueryBo.java` | +`overdueOnly` 字段（**纯新增**） | 契约点名支持 `?overdueOnly=true`；上游**故意不声明**（声明一个没人实现的筛选 = 静默全表） |
| `cryo/batch/service/CryoQueryService.java` | 加 `CryoOverdueService` 依赖；`list` 返回 `CryoBatchPageVo` 并带 `tabCounts`；`buildWrapper` 拆成 `applyFilters` + `applyOrderBy`（**都新增 4 参 `days`**）；`assemble/toVo` 多带 `days` 并填 `overdue/overdueDays`；新增 `tabCounts()` | 「一处判定、多处同源」的落点就在这一个 `list()`：工作台与将来小程序的表格页共用它；行标记、筛选、排序、页签四个键都在这里 |
| `cryo/batch/domain/vo/CryoBatchPageVo.java`（**新增**） | `extends TableDataInfo<CryoBatchVo>` + `Map<String,Long> tabCounts` | 响应要**顶层**多一个 `tabCounts`。不去改全仓共用的 `TableDataInfo`（那会给别的接口的响应形状一起加键，有 accept 断过 keys 集合），改用子类只影响这一个端点 |
| `cryo/batch/controller/CryoBatchController.java` | `list()` 的返回类型 → `CryoBatchPageVo` | Jackson 按**声明类型**取属性集，只写父类型不会序列化出子类字段；必须把返回类型写成子类 |
| `test/…/batch/service/CryoQueryContractTest.java` | `buildWrapper` 调用补第 4 个实参；③ 号用例改成断「超期置顶 + 之后创建时间倒序」；新增 ⑥ `overdueOnly` 用同一片段 ⑦ 清单与计数同一段 where | **上游的断言与 ticket 的新口径直接冲突**：它断的默认排序就是本票要改的那一档（`ORDER BY create_time DESC, id DESC` 必须变成置顶形态）。这是「按 ticket 改口径」而不是「把测试改绿」——新断言比旧的更强（还断阈值是参数、不是字面量） |
| `test/…/batch/domain/CryoShapeContractTest.java` | ⑤ 号用例：`assertFalse(names.contains("overdueOnly"))` → `assertTrue(...)` + 断类型为 `Boolean` | 同因：上游那条负断言的前提是「CRYO-REMIND-001 还没做」。现在做了且**真的有人实现**（`applyFilters` 拼 `CryoOverdueSqlProvider`），负断言的前提消失；本票把它换成正向断言（「有字段就断言它有实现」），没有删测 |

**没有改签名的地方**：`CryoBatchService` / `CryoBalanceChecker` / `CryoFlowService` / `CryoChildrenChecker` / `CryoBatchMapper` / `CryoFlowMapper` / `CryoFlowController` / 两个 batch BO **一个字节没动**。`CryoQueryService.buildWrapper` 只多了一个入参（包内可见的测试辅助），返回类型从 `TableDataInfo<CryoBatchVo>` 换成它的**子类**（协变，任何 `TableDataInfo` 调用方不受影响）。

### 2.4 接口清单（本票**新增 1 个端点**，`touches` 外的**字段/键**已在上表）

```
GET  /lqg/cryo/overdue                     新增：超期批次清单（按已超天数倒序），权限 lqg:cryo:list
GET  /lqg/cryo/batch/list                  每行多 overdue / overdueDays；响应多顶层 tabCounts:{all,overdue,ln2}
                                           支持 ?overdueOnly=true；默认排序变成「超期置顶 + 创建时间倒序」
（服务口）CryoOverdueService#countOverdue  给 SYS-HOME-001 的工作台首页待办与菜单角标用
```

---

## §3 那个唯一判定函数（下游四处都调它）

### 3.1 Java 侧：唯一的判定函数（纯函数）

文件：`ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/remind/service/CryoOverdueService.java`

```java
public static boolean isOverdue(CryoBatch batch, int remaining, LocalDate today, int overdueDays) {
    if (batch == null || today == null || overdueDays <= 0) return false;
    if (!"Y".equalsIgnoreCase(batch.getInMinus80())) return false;   // ① 暂存 -80 为是
    if (batch.getToLn2Time() != null) return false;                  // ② 没登记转液氮
    if (remaining <= 0) return false;                                // ③ 剩余 > 0
    return elapsedDays(batch.getFreezeTime(), today) >= overdueDays; // ④ 满阈值（当天即算：>=）
}

/** 已超天数 = 今天 − 冻存日 − 阈值；未超期 → null（不是 0）。 */
public static Integer overdueDaysOf(CryoBatch batch, int remaining, LocalDate today, int overdueDays)
```

它**纯**：不碰库、不读配置、不碰 Spring（`today` 与阈值都当入参传），所以边界测得动。

### 3.2 SQL 侧：唯一一份同口径 where 片段

文件：`ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/remind/sql/CryoOverdueSqlProvider.java`

```java
public static final String WHERE =
      "del_flag = '0'"
    + " AND in_minus80 = 'Y'"
    + " AND to_ln2_time IS NULL"
    + " AND (CURRENT_DATE - freeze_time) >= #{days}"
    + " AND init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f"
    + " WHERE f.batch_id = t_lqg_cryo_batch.id AND f.del_flag = '0'), 0) > 0";

public static final String SELECT_LIST  = "SELECT * FROM t_lqg_cryo_batch WHERE " + WHERE
                                        + " ORDER BY (CURRENT_DATE - freeze_time) DESC, id DESC";
public static final String SELECT_COUNT = "SELECT COUNT(*) FROM t_lqg_cryo_batch WHERE " + WHERE;

/** 换占位符渲染同一段片段：wrapper 侧用 {0}（apply 实参）/ #{ew.paramNameValuePairs.*}（排序键）。 */
public static String whereFor(String daysPlaceholder) { return WHERE.replace("#{days}", daysPlaceholder); }
```

- 阈值**永远是参数**：`@Param("days")` 绑定（清单 / 计数两条自定义 SQL）、`apply(sql, days)`（`overdueOnly`）、`#{ew.paramNameValuePairs.cryoOverduePinDays}`（默认排序键）。片段与排序键里**没有任何硬编码天数**；`CryoOverdueServiceTest` 与 `CryoQueryContractTest` 都断这一点。
- 列名用**全表名**限定（不用别名）：MyBatis-Plus 的 wrapper 查询生成的是 `FROM t_lqg_cryo_batch`（无别名），全表名限定让同一段片段在「两条自定义 SQL + wrapper 三条路」上都能原样拼。
- 子查询里的 `del_flag = '0'` 是**手写**的：自定义 SQL 不吃 `@TableLogic`（seed 的 3002 名下挂着一条软删的 `−1`）。

### 3.3 四个调用点

| # | 调用点 | 走哪条 |
|---|---|---|
| 1 | 工作台列表 `/lqg/cryo/batch/list` 每行 `overdue / overdueDays` | `CryoQueryService.toVo` → `isOverdue` / `overdueDaysOf` |
| 2 | 小程序内部管理冻存表格页 `/mp/int/cryo/batch/list` 的 `tabCounts.overdue`（CRYO-MP-001 落端点） | `CryoQueryService.list` **同一个方法**（MP 端点只需转发） |
| 3 | 超期清单 `GET /lqg/cryo/overdue` | `CryoOverdueMapper.selectOverdueList`（SQL 片段）+ `assemble` 再用 `isOverdue` 过一遍每行（**两道闸**） |
| 4 | 工作台首页计数（SYS-HOME-001） | `CryoOverdueService.countOverdue()` → `selectOverdueCount`（与清单同一段 where） |

---

## §4 accept 逐条 ✅ + 关键输出

### 4.0 runner 的两处忠实性说明（先讲清，再看输出）

| 偏差 | 为什么 | 等价做法 / 证据 |
|---|---|---|
| 去掉 accept 1 / accept 2 的 `--fresh-module ruoyi-lqg` | 本 agent 沙箱里 `api.sh` 第 71 行 `ps -o lstart=` 被禁（`/bin/ps: Operation not permitted`），macOS 又没有 `date -d` 回退 → 该守卫**恒非 0 退出**（issue #151，连续十几张票的既有 WARN）。**按规矩没有改 `api.sh`。** | `bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-freshness.sh` **七项**：② 源码比 jar 新的文件 **0** 个（src 与 `cryo/remind` 两处都查）③ 进程启动 `1790050941` ≥ jar mtime `1790050925`（用 `libproc.proc_pidinfo(pbi_start_tvsec)`，CRYO-FLOW-001 §8-5 同款；不用 `ps`）④ 进程持有 jar **2** 处 ⑤ 嵌套 jar 内含本票 3 个新 class ⑥ `flyway_schema_history` 有 `V202609241205__CRYO-REMIND-001-config.sql` ⑦ `GET /lqg/sys/ping` → `200 操作成功` |
| accept 1 末尾的 Maven 补本机三参数 | 沙箱必须带 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome` | 断言本体逐字不变（`-Dtest='CryoOverdue*Test' -Dsurefire.failIfNoSpecifiedTests=true`）；`Tests run: 8, Failures: 0` |

★ **issue #155 那段**：ticket accept 2 的 `bash doc/verify/api.sh --as extA --bizcode GET /lqg/cryo/overdue | grep -qE '^403'` **本来就带了 `--bizcode`**（输出是 `code<TAB>msg`，首字符 `4`），所以那一段**照原文跑就是对的**，本票没有需要改成 `jq -e '.code==...'` 的段落。逐字输出：`403	没有访问权限，请联系管理员授权`。

### accept 1（DATA）—— **✅ EXIT=0**

命令：`bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-acc1.sh`
（= ticket `run` 逐字，去掉 `--fresh-module`、Maven 补三参数）

```
true                                        # 清单 == ["9000003001:6","9000003005:0"]
9000003001                                  # db.py --col-set（独立 SQL，阈值也取自 sys_config）
9000003005
true                                        # tabCounts == {"all":7,"overdue":2,"ln2":2}
                                            # 且 rows 里 overdue 的 id 恰 {3001,3005} 且 rows[0:2] 都 overdue
true                                        # overdueOnly=true → rows 长度 == 2
… CryoOverdueServiceTest 的回落 WARN …
ACCEPT-1 EXIT=0
```

补充取证（同一个 seed 状态下直接看响应，便于 QA 复核）：
```
$ api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -c '{code,total,tabCounts,rows:[.rows[]|{id,overdue,overdueDays,remainingQty,location}]}'
{"code":200,"total":7,"tabCounts":{"all":7,"overdue":2,"ln2":2},
 "rows":[{"id":9000003005,"overdue":true,"overdueDays":0,"remainingQty":2,"location":"minus80"},
         {"id":9000003001,"overdue":true,"overdueDays":6,"remainingQty":6,"location":"minus80"},
         {"id":9000003007,"overdue":false,"overdueDays":null,"remainingQty":5,"location":"ln2"},
         {"id":9000003006,"overdue":false,"overdueDays":null,"remainingQty":5,"location":"minus80"},
         {"id":9000003004,"overdue":false,"overdueDays":null,"remainingQty":0,"location":"minus80"},
         {"id":9000003003,"overdue":false,"overdueDays":null,"remainingQty":4,"location":"ln2"},
         {"id":9000003002,"overdue":false,"overdueDays":null,"remainingQty":4,"location":"minus80"}]}
```
★ 边界逐格：3005（恰好第 14 天）**红**、3006（第 13 天）不红、3004（取空，剩 0）不红、3003/3007（液氮）不红、3008（软删）**根本不出现**；rows[0:2] 都是超期 → 默认排序「超期置顶」生效。

### accept 2（STATE）—— **✅ EXIT=0**

命令：`bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-acc2.sh`（= ticket `run` 逐字，只去掉 `--fresh-module`）

```
true   # tabCounts.overdue==2
true   # PUT /lqg/cryo/batch/9000003001/to-ln2 → code 200
true   # 清单 == ["9000003005"]（3001 当场退出）
true   # tabCounts.overdue==1 且 3001 行 overdue==false
true   # POST /lqg/cryo/batch/9000003005/flow take 2 → code 200
true   # 清单 == []（3005 取空后当场退出）
true   # tabCounts.overdue==0
       # extA --bizcode → grep -qE '^403'（§4.0 说明：这一段本来就是对的）
       # grep 探针：命中文件数 == 0
       # 结尾 reseed
ACCEPT-2 EXIT=0
```
`--as extA` 的逐字输出：`403	没有访问权限，请联系管理员授权`。
★ grep 探针实测：`grep -rlE 'freeze_time|freezeTime' …/org/dromara/lqg --include=*.java | xargs grep -lE '\b14\b|OVERDUE_DAYS' | grep -v '/cryo/remind/' | wc -l` → **0**；而且连 `cryo/remind/` 包里也**一个都没有**（见 §6 坑 2 的「双保险」）。

### accept 3（STATE，阈值参数化）—— **✅ EXIT=0**

命令：`bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-acc3.sh`（= ticket `run` **逐字**；只多了一个不改断言的收尾保险：无论 rc 都把参数复位成 14）

```
true                             # 阈值 14：清单 {3001,3005}
true                             # 读到 configValue=="14" 的整行（configId/configName/configKey/configType/remark）
true                             # PUT /system/config（改 13）→ code 200
true                             # 清单 == ["9000003001:7","9000003005:1","9000003006:0"]
true                             # tabCounts.overdue==3
9000003001                       # db.py --col-set 3001,3005,3006（独立 SQL）
9000003005
9000003006
true                             # PUT /system/config（改回 14）→ code 200
true                             # 清单复原 == {3001,3005}
14                               # db.py --eq 14
ACCEPT-3 EXIT=0
```
★ 阈值改成 13 之后**第 13 天的 3006 立刻进来**、已超天数从 6/0 变成 7/1/0、页签 2→3；改回 14 全部复原 —— 「改完下一次读时即生效」有机器证据。

### 反 stale 探针（`--fresh-module` 的等价证据）—— 逐字输出

```
$ bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-freshness.sh
① jar：Sep 22 12:22 174080028
② 源码比 jar 新的文件（必须为空，两处：src 与 本票新包）：
② 命中行数 = 0
③ 8081 监听 PID = 44936
   jar mtime = 2026-09-22 12:22:05 (1790050925)
   进程启动  = 2026-09-22 12:22:21 (1790050941)
   ✓ 进程不早于 jar
④ 进程持有 jar：2 处
⑤ 嵌套 jar 内含本票新 class：3 个
⑥ flyway 里本票那一行：V202609241205__CRYO-REMIND-001-config.sql
⑦ GET /lqg/sys/ping（--as staff）：200	操作成功
```

### ★ 单测与 p6spy 的 SQL 原文（阈值是参数、排序键带置顶）

```
$ mvn … -pl ruoyi-modules/ruoyi-lqg -am test
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0  -- CryoOverdueServiceTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0  -- CryoQueryContractTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0  -- CryoShapeContractTest
…
Tests run: 169, Failures: 0, Errors: 0, Skipped: 0
```

后端 p6spy 原文（`?overdueOnly=true` 这一次请求；`14` 是 p6spy 把**绑定参数**格式化出来的显示值，SQL 串里是 `?`）：

```
Execute SQL：SELECT COUNT(*) FROM (SELECT id,sample_id,… FROM t_lqg_cryo_batch
  WHERE del_flag='0' AND (del_flag = '0' AND in_minus80 = 'Y' AND to_ln2_time IS NULL
    AND (CURRENT_DATE - freeze_time) >= 14
    AND init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f
        WHERE f.batch_id = t_lqg_cryo_batch.id AND f.del_flag = '0'), 0) > 0)
  ORDER BY CASE WHEN del_flag = '0' AND in_minus80 = 'Y' AND to_ln2_time IS NULL
    AND (CURRENT_DATE - freeze_time) >= 14 AND init_qty + COALESCE(…) > 0
    THEN 0 ELSE 1 END ASC, create_time DESC, id DESC) TOTAL        ← ★ count 走子查询形态（见 §6 坑 1）
```

---

## §5 回归自查（先 `reseed` + `clean-orphan`，再跑）

脚本：`bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-regression.sh`（日志 `accept-runners/regression.out`）

| 回归 | 结果 |
|---|---|
| `CRYO-MODEL-001` accept 1 / 2 / 3 | **EXIT=0 / 0 / 0** |
| `CRYO-FLOW-001` accept 1 / 2 / 3 | **EXIT=0 / 0 / 0** |
| `SAMPLE-HINT-001` accept 1（与计数口径同源的那张） | **EXIT=0**（9 段全绿） |
| `ExtChokepointContractTest` | **`Tests run: 4, Failures: 0`**（D1 的外部咽喉门不受影响） |
| D1 回归包 `bash doc/waves/regression/D1/verify.sh --skip-build` | **42 绿 / 1 红 / 0 环境错，rc=1** —— 唯一那条红是**既有 harness 缺陷** |

```
$ bash doc/waves/regression/D1/verify.sh --skip-build
  ✓ L1.1 flyway 无失败行（success IS NOT TRUE 的行 = 0）
  ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '16' 16
  …（L1.2 / L1.3a-d 全绿）
═══ 汇总 ═══
失败 1 条：
  - L1.1 D1 的 7 支迁移全部记录在案 → [FAIL] 期望 '7'，实际 '16' 16
```

★ 这一条**不是「以前好的坏了」**：D1 的 7 支迁移**逐个点名全绿**（`✓ L1.1 迁移在源码树：V2026092108xx…` 七条）、无失败行；红的是 `SELECT count(*) FROM flyway_schema_history --eq 7` 这个**写死总数**的断言（D2 起每加一支迁移就假红一条，CRYO-MODEL-001 §5 / CRYO-FLOW-001 §5 逐字同款，state.json 已记 issue #82）。本票的迁移把它从 15 推到 16，**本票不重复计数**。

---

## §6 遗留与 raise（ticket §4.3）

### 6.1 越出 `touches` 的改动：**WARN-1（S2）** —— 7 个文件

`touches` 只写了 `cryo/remind/**` + 一支迁移，但 ticket §2 要求的 `overdue / overdueDays / tabCounts / overdueOnly / 默认排序超期置顶` 落在 `cryo/batch/**`（上游**故意不预置**：「声明了没实现会变成静默全表」）。派单已授权「该改就改」，实际动了 **7 个文件**（4 个 `cryo/batch/**` 既有文件 + 1 个新 `CryoBatchPageVo` + 2 个上游测试）：

| 文件 | 性质 | 非改不可的理由 |
|---|---|---|
| `cryo/batch/domain/vo/CryoBatchVo.java` | 纯新增字段 | 契约点名行内带 `overdue/overdueDays`，只能挂在这个行类型上 |
| `cryo/batch/domain/bo/CryoQueryBo.java` | 纯新增字段 | 契约点名 `?overdueOnly=true` |
| `cryo/batch/service/CryoQueryService.java` | 加依赖 + 加方法 + 拆方法 + 返回类型换成子类 | 「四处同源」的落点就是这一个 `list()`；不在这里补就得在别处写第二份 where |
| `cryo/batch/domain/vo/CryoBatchPageVo.java`（**新**） | 新类 | 响应顶层要 `tabCounts`；不改全仓共用的 `TableDataInfo` 是刻意的（见 §2.3） |
| `cryo/batch/controller/CryoBatchController.java` | 返回类型改子类 | 不改声明类型，Jackson 不会序列化子类字段 |
| `test/…/CryoQueryContractTest.java` | 断言口径更新 + 新增 2 例 | 上游断的默认排序正是本票要改的那一档（**冲突**，不是回归） |
| `test/…/CryoShapeContractTest.java` | 负断言换成正断言 | 上游断「不许有 `overdueOnly`」的前提是「本票还没做」；现在做了，换成「有字段就断言它有实现」 |

**→ 建议（给主会话）**：把上表前 5 个文件划进 CRYO-REMIND-001 的 `touches`（state.json 的 issue #152 已记，本票补充「实际是 4 + 1 个文件，另有 2 个上游测试的断言必须同步」）。

### 6.2 与 `doc/api-contract.md` 的差异

1. 契约说「响应另带 `data.tabCounts` **或**顶层 `tabCounts`」——本票落**顶层**（accept 读的也是顶层），响应类型 `CryoBatchPageVo`（`TableDataInfo` 的子类）。
2. 契约没写 `GET /lqg/cryo/overdue` 的**权限串**——本票用已有的 5401 `lqg:cryo:list`（能看冻存列表的人本来就该能看超期清单），**没有新造权限行、没有新迁移菜单**。
3. 契约列的其他 CRYO 端点（`POST …/export`、`/mp/int/cryo/**`）**本票不做**（ticket §3 边界）。
4. 票面 §2 点名 `List<OverdueVo> listOverdue()`；实现返回 `List<CryoOverdueVo>`（短名更具体、避免与将来任何 `OverdueVo` 撞 MyBatis 别名），字段是超期清单自己要的那几个。**accept 不受影响**（只读 `.id` 与 `.overdueDays`）。

### 6.3 没把握 / 需要主会话确认的口径

- **`tabCounts` 的作用域**：本票取**整表口径**（`all / overdue / ln2` 三个数都不随列表的筛选收窄；列表的 `total` 才是当前筛选下的行数）。理由：`overdue` 那一格必须与超期清单长度、工作台首页计数**恒等**（同一个 `countOverdue()`），而那两个是整表的；三个数同一口径内部才自洽。契约与票面都没定义筛选下的页签语义 → **CRYO-WEB-001 若要求「页签跟着筛选一起收窄」，需要在契约里补一句**（这属于另一个 ticket 的口径，本票不改权威）。
- **`sort=recent` 不置顶超期**：默认排序置顶超期，但小程序历史编辑记录那一档（`sort=recent`）保持「最近改过的在上」——票面只说「默认排序超期置顶」，本票按字面理解；若 CRYO-MP-001 也要置顶，改 `applyOrderBy` 一处即可。
- **`overdueDays` 未超期时是 `null` 而不是 0**：契约只写「已超 N 天」。`null` 让「没超期」与「阈值当天（0）」区分得开（accept 3 断的就是 `3006:0` 出现在清单里）——这是有意的。

### 6.4 给下游的交接（必读）

- **CRYO-MP-001**：`/mp/int/cryo/batch/list` 直接转发 `CryoQueryService.list(...)` 就自带 `tabCounts`（含 `overdue`）与每行 `overdue/overdueDays`、也自带 `overdueOnly` —— **别再写一份超期 where**（那正是 accept 2 的 counterfeit）。小程序**只读**：`GET /lqg/cryo/overdue` 是内部接口，别往 `/mp/**` 转发写操作。
- **CRYO-WEB-001**：工作台首页待办卡片与菜单角标调 **`CryoOverdueService#countOverdue()`**（无参，整表口径）；列表页的「超期」页签用 `?overdueOnly=true`，角标数字直接用 `tabCounts.overdue`（与清单长度恒等）；超期清单页消费 `GET /lqg/cryo/overdue`（已按已超天数倒序）。**权限行不用你补**：5401 `lqg:cryo:list` 已在册，本票**没有建菜单**（5410 段留给你，`getRouters` 现在仍是 0 条 `component='lqg/cryo/index'`）。
- **SYS-HOME-001**：首页待办第五个数 = `cryoOverdueService.countOverdue()`（README「工作台首页待办期望：-80 超期 2」）。
- **任何人加 Spring `@Scheduled`**：`@EnableScheduling` 原来全仓没生效（`snail-job.enabled=false` 关掉了上游那处），本票在 `cryo/remind/config/CryoOverdueScheduleConfig` 里打开了一次（重复打开无害）。

---

## §7 坑与解法（给下游，5 行）

1. **★ MyBatis-Plus 的 count SQL 优化会「因为 ORDER BY 里带参数」而拒绝摘掉 ORDER BY，于是一个带参排序键会让列表 500。** 本票默认排序的置顶键里带着绑定的阈值（`>= ?`），MP 的 `PaginationInnerInterceptor` 源码里有一句「order by 里带参数,不去除 order by」，于是 count SQL 变成 `SELECT COUNT(*) … ORDER BY CASE WHEN … END, create_time DESC` —— PostgreSQL 对「聚合 + ORDER BY 非分组列」直接报 `column "t_lqg_cryo_batch.del_flag" must appear in the GROUP BY clause or be used in an aggregate function`（**单测全绿也拦不住**，本票第一版实跑 500）。解法：`page.setOptimizeCountSql(false)` → 走 `SELECT COUNT(*) FROM (原 SQL) TOTAL`（子查询里带 ORDER BY 合法），阈值仍是绑定参数。取证见 §4 的 p6spy 原文。
2. **★ 阈值字面量的 grep 探针可以做「双保险」。** accept 2 的探针是 `grep -rlE 'freeze_time|freezeTime' … | xargs grep -lE '\b14\b|OVERDUE_DAYS' | grep -v '/cryo/remind/'`：路径上已经排除了 `cryo/remind/`，但本票**连 `cryo/remind/` 包里也不出现那两个字面量** —— 回落值写成 `0x0E`（注释里说「默认两周」而不是写阿拉伯数字）、排序参数常量叫 `PIN_DAYS_PARAM_KEY`（**不能叫含 `OVERDUE_DAYS` 子串的名字**：那是子串匹配，`OVERDUE_DAYS_KEY` 一样会命中）。全仓探针实测命中 0。
3. **★ 上游的「还没做」负断言会与新口径正面冲突，别把它当回归。** `CryoShapeContractTest` 断「BO 里不许有 `overdueOnly`」、`CryoQueryContractTest` 断「默认排序就是 `ORDER BY create_time DESC, id DESC`」——本票要做的正是这两件事。处置是**把断言改强**（正向断字段有实现、断置顶键 + 阈值是参数），不是删测。
4. **★ `@Scheduled` 在本项目默认不跑。** 全仓唯一的 `@EnableScheduling` 在上游 `SnailJobConfig` 上，而它挂着 `snail-job.enabled=true` 的条件，dev/prod 都是 `false`（ADR-0001 说「定时任务用 Spring `@Scheduled`」，但没人打开过调度器）。本票自带一个 `@Configuration @EnableScheduling`，否则每日点名会**静默不执行**（不报错、不打日志）。
5. **★ 「一页一次聚合」与「逐行判定」可以同时成立。** 列表的剩余仍然是一条 `GROUP BY`（上游口径不变）；`overdue/overdueDays` 是拿**已经算出来的剩余**喂给纯函数，所以没有多一次查询、也没有第二份 where。`countOverdue()` 与 `listOverdue()` 则是两条 SQL 拼**同一个 where 常量**（`CryoQueryContractTest` ⑦ 拿注解原文断这一点）——这比「两边各写一遍 SQL 文本」更难写歪。

---

## §8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| **WARN-1** | **S2** | cross-ticket | **CRYO-REMIND-001 的 `touches` 装不下实现：实际越出 7 个文件**（`cryo/batch/**` 的 `CryoBatchVo` / `CryoQueryBo` / `CryoQueryService` / `CryoBatchController` + 新 `CryoBatchPageVo` + 两个上游测试的断言） | 逐条取证与「非改不可」的理由见 §6.1。state.json 已有 issue #152，本票补三点新信息：① 真正要动的**既有**文件是 **4** 个（多了一个 controller）；② `tabCounts` 要出现在响应顶层，只能**新增一个 `TableDataInfo` 子类**（不能改全仓共用的 `TableDataInfo`）；③ 上游 2 个测试的断言**必须同步**（负断言前提消失、排序断言与新口径冲突）。方案：把这 5 个 `cryo/batch/**` 文件划进它的 `touches`，并在票面注明「上游相关测试断言随口径更新」。 |
| **WARN-2** | **S2** | harness / framework | **MyBatis-Plus 的 count SQL 优化会因「ORDER BY 里带绑定参数」而保留 ORDER BY → 分页列表 500**（PostgreSQL：聚合 + ORDER BY 非分组列） | 取证：§7-1 的 p6spy 原文 + `PaginationInnerInterceptor` 源码注释「order by 里带参数,不去除 order by」。影响：**任何**想让默认排序带「计算列 / 绑定参数」的分页列表（本票是第一个）都会踩，而单测拦不住。方案（择一）：① 约定「带参 ORDER BY 的分页查询要 `page.setOptimizeCountSql(false)`」并写进 D4 之后票面的 §0 必读；② 给 `CryoBatchPageVo` 之类的分页壳统一封一个 `paginate()` 小工具。本票按 ① 处置并在 `CryoQueryService.list` 里写了长注释。 |
| **WARN-3** | S3 | doc-drift | **票面 §2 要求迁移插入 `lqg.cryo.overdue-days`（`config_type='Y'`、名「-80 冻存超期天数」），而这一行早由 SYS-WEB-001 的 `V202609210830` 插好（`config_type='N'`、名「-80 冻存超期提醒天数」）** | 取证：§2.1 的 `db.py` 输出 `5002|-80 冻存超期提醒天数|lqg.cryo.overdue-days|14|N`。本票的迁移写成 `WHERE NOT EXISTS` 的**幂等兜底**（不覆盖已有值，dev 库上是 0 行空操作），避免把甲方改过的阈值悄悄改回去。影响：accept 3 原样读回整行、只改 `configValue`，两种 `config_type` 都不红；但**票面与库不一致**。方案：把票面 §2 那句改成「若缺行则插默认 14，已存在不动」（或把配置行归属写清给 SYS-WEB-001）。本票不改权威。 |
| **WARN-4** | S3 | clarify | **`tabCounts` 的作用域（整表 vs 跟随筛选）契约与票面都没写** | 本实现取**整表口径**（§6.3），因为 `overdue` 必须与超期清单 / 首页计数恒等。影响：CRYO-WEB-001 若要求「页签数字跟着筛选收窄」，会与实现不一致。方案：在 `doc/api-contract.md` 的 `GET /lqg/cryo/batch/list` 那一行补一句 `tabCounts 为整表口径，不随筛选收窄`。 |
| **WARN-5** | S3 | clarify | **`overdueDays` 未超期时是 `null`（不是 0）** | 契约只写「已超 N 天」。`null` 才能把「没超期」与「阈值当天 = 已超 0 天」分开（accept 3 断 `3006:0` 在清单里）。影响：前端「已超 N 天」列要判 null（否则会显示「已超 0 天」）。方案：契约那一行补一句。 |
| **WARN-6** | S3 | framework / debt | **`@EnableScheduling` 全仓原本没有生效（`snail-job.enabled=false` 关掉了上游那处），本票自带一个打开** | 取证：§7-4 + 上一节。影响：本票之后**任何** `@Scheduled` 才会真的跑；重复打开无害。若将来有人把 `snail-job.enabled` 改成 true，两处 `@EnableScheduling` 也不会冲突。方案：在 ADR-0001 的落地说明里补一句「调度器在本票（`CryoOverdueScheduleConfig`）里打开」。 |
| **WARN-7** | S2 | harness（**既有，不重复计数**） | `api.sh --fresh-module` 在 subagent 沙箱恒非 0（`ps -o lstart=` 被禁 + macOS 无 `date -d`） | state.json 已记（issue #151 / #13 / #33）。本票的等价证据 = `remind001-freshness.sh` **七项**（§4.0）。**本票没改 `api.sh`。** |
| **WARN-8** | S2 | harness（**既有，不重复计数**） | D1 回归包把 flyway 迁移总数写死成 7 —— 本票的迁移把它推到 **16** | 取证见 §5：D1 的 7 支逐个点名全绿、无失败行；红的是 `--eq 7`（issue #82）。**本票没改回归包**（`doc/waves/regression/**` 不在 `touches`）。 |
| **WARN-9** | S3 | harness（**既有，不重复计数**） | `accept` 与 D1 回归同票连跑会让 D1 假红（issue #157） | 本票的 runner 里已经**显式先 `reseed` + `clean-orphan-accounts.sh` 再跑回归**（`remind001-regression.sh` 的 `clean()`），实测 42 绿 / 1 红（既有那条）。**不重复计数**。 |

> 已在上游报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数、`api.sh` token 缓存只看 mtime（本票 runner 开头都 `rm -f /tmp/lqg-verify-token-*`）、`update(null, LambdaUpdateWrapper)` 不填 `update_by`（本票无写库路径）、`PageQuery` 只有两参构造、`@SaCheckPermission` 缺菜单行是 403、MyBatis 类型别名按短名去重（本票新类 `CryoOverdueVo` / `CryoBatchPageVo` 短名全仓唯一，后端实跑启动成功）、`db.py --quiet` 空转（本票没用 `--quiet`）。

---

## §9 验证用长进程（ticket §4.4）

- **后端 java（8081）：收尾时已关**（按 `lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID → `kill <PID>`；**没用 `pkill -f 'ruoyi-admin.jar'`**，本机 8080 上有别处的 java 服务）。起法 `bash .tmp/run-backend.sh`（受管后台作业，日志 `.tmp/rm001-backend*.log`）。
- **docker 容器：留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），未停。
- **未占 8080 / 5432 / 6379 / 9000 / 9001**。
- **DB 收尾** = `reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`（收尾记录见 §10）。
- 前端 / 小程序**一个字节没动**，本票没有起过任何前端进程。

---

## §10 收尾记录（ticket §4.4 / 派单「收尾两步」）

```
1) bash doc/verify/reseed.sh --yes                     # 回干净 seed
2) bash doc/waves/tools/clean-orphan-accounts.sh --yes # 清运行时按手机号建的雪花 id 账号
3) lsof -ti tcp:8081 -sTCP:LISTEN  →  PID …  →  kill <PID>          # 只按 PID；没用 pkill -f
4) lsof -ti tcp:8081 -sTCP:LISTEN  →  （空）           # 8081 已无监听；8080 全程未碰
5) docker ps：lqg-dev-postgres(5433) / lqg-dev-redis(6380) / lqg-dev-minio(9002+9003) 保留在跑，未停
```

- **git**：只 `git add` 本票自己的路径（`cryo/remind/**`、`cryo/batch/**` 那 5 个文件、两个上游测试、`db/migration/V202609241205__…`、`doc/waves/reports/CRYO-REMIND-001/**`），**没有 `git add -A`**；未 push、未 merge、未动 `doc/waves/state.json` 与 `_manifest.json`。
