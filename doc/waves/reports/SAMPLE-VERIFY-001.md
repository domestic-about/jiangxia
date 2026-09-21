# SAMPLE-VERIFY-001 · 完工报告

- **ticket**：SAMPLE-VERIFY-001（track SAMPLE / phase D2 / size M）——D2 第二张（`tickets/SAMPLE-VERIFY-001/prompt.md`）
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑；唯一改动 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`，等价证据见 §4.0）
- **分支**：`task/D2`（未切分支 / 未 push / 未 merge / 未动 `main`、`integration`、`doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 22:50:58`，后端进程 **PID 57771**（8081，dev profile + `--api-decrypt.enabled=false`）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/,gen_seed.py,verify.env,README.md}`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/waves/state.json`、`doc/verify/fixtures/**`、`_manifest.json`
- **未越界**：`code/plus-ui`、`code/miniapp` 一个字节没动；根 pom / `ruoyi-common-*` / `ruoyi-system` 没动；**唯一越出 `touches` 的一条是 migration**（见 §6 WARN-1，briefing 已授权「确需则 WARN + SAMPLE 10xx 取号」）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D2`；`git status --porcelain` 只有本票 4 条路径 |
| `depends_on` 全部 done | ✅ PASS | `SAMPLE-MODEL-001` / `AUTH-STAFF-001` 报告均 `status：**done**`（另两张 D1 报告亦 done）；产物在盘：`t_lqg_sample`、`SampleController`、`/lqg/auth/staff`、`StaffGrantRules.isInternal` |
| 扫 `doc/change-log.md` 顶部四条 CR | ✅ PASS | 逐条核过，见下方「CR 影响」 |
| 逐条取权威锚（9 个 `blueprint_refs`） | ✅ PASS | 全部 `authority_lint.py show` 过，与 ticket 表格逐条对照见 §1 |
| ADR-0003 / ADR-0010 | ✅ PASS | ADR-0003：内外部只由角色决定（复用 `StaffGrantRules.isInternal`，不看 `user_type`）；ADR-0010：样本主档一张表、`internal_no` 全库唯一且待核验可空 —— 本票只改状态与收样段，不动模型 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | 三个容器在跑；8081 起停只按 PID；**未碰 8080 / 5432 / 6379**（`lsof -ti tcp:8081` 唯一定位本进程） |
| 动手前代码是绿的 | ✅ PASS | 改动前 `mvn -pl ruoyi-modules/ruoyi-lqg test` = `Tests run: 31, Failures: 0`（SAMPLE-MODEL-001 报告的原值）；本票后 **39** |

**STOP 判定：无。** 未出现「上游产物缺失」「与权威冲突且无法判断」「环境不可用」三类硬阻塞。

### CR 影响（顶部四条）

| CR | 与本票的关系 | 判定 |
|---|---|---|
| **CR-20260921-08**（小程序视觉方向 A） | 只动小程序 token 与图廊，本票不做前端 | 不受影响 |
| **CR-20260918-07**（甲方 9 条） | 5 处改动都在 `Ext*Vo` / 小程序表格页 / 冻存阈值（AUTH-EXT-002、CRYO-*、EMBED-*、SAMPLE-MP-*、SYS-MP-001）；**没有一条落在 `/lqg/sample/{id}/verify` 或状态机上** | 不受影响 |
| **CR-20260917-06**（图廊删稿 +「我的」重绘） | 纯设计稿，本票无 UI | 不受影响 |
| **CR-20260917-05**（小程序结构四条） | ★ **直接覆盖本票正文**：`FLOW:F-SAMPLE-01.step4` 改为「历史编辑记录」（本票只写 service，外部 controller 归 AUTH-EXT-001）；**状态机不分样本种类、转移表抽成 `VerifyTransitions` 给石蜡包埋复用**、外部也可提交类器官收样（走同一张转移表） | **已按 CR 实现**：`VerifyTransitions` 无 `sample_kind` 判据（§4.3 用例 7 钉死）；`resubmitByExternal` 按 `sample_kind` 只分化「写哪几列」，不分化「能不能转」 |

`authority_lint.py diff` 口径：本票不动 `flows` / `field-ssot` / `ui-index`。

## §1 口径复述（本张最容易做反的四点，逐条对 accept 与权威核）

| # | 口径（ticket §0 / §2，权威锚） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **判有效必须同时给 `receiveDate` 与 `internalNo`（且内部编号唯一）；判无效必须给 `reason`；缺了就拒且库里什么都不变**（`FLOW:F-SAMPLE-01.step3`） | `SampleVerifyService.applyValid` 先查两必填再查唯一性、`applyInvalid` 先查 `reason`；全部在 `@Transactional` 的**写操作之前** | accept 1 第 2/3/4 段输出 `500 判有效必须同时给收样日期与内部编号，缺少：内部编号` / `500 内部编号「T-hli01」已存在，请换一个` / `500 判无效必须写原因`；第 5 段库内 `pending\|-\|-` |
| 2 | **`submit_source` 是提交当时的快照**（`FIELD:t_lqg_sample.submit_source`：「提交当时按提交人身份落库，之后不随账号升降级而变」） | `SampleVerifyService` **一处都不写** `submitSource`（核验 / 改判 / 重提全不碰）；`PUT /lqg/sample`（SAMPLE-MODEL-001）也不改 | accept 2：extA 升级成 `lqg_internal` 后，老样本仍 `external:3`（其中 SQL join 独立算出「现在是内部角色且 submit_source=external」= **3**）；新录的那条才是 `internal:1` |
| 3 | **状态只能由内部改**：外部不能把自己的样本改成 valid；外部重提只回到 pending；`invalid→valid`（内部改判）要放开且**必须清掉旧的 `invalid_reason`**（`FLOW:F-SAMPLE-01.step5`） | `VerifyTransitions.LEGAL` 是白名单：`invalid→pending` 只给 `actorIsInternal=false`，`→valid` 只给 `true`；`applyValid` 无条件 `.set(invalidReason, null)` | accept 1 倒数第 3 段 `invalid→valid` 200；最后一段集合 `9000001003:valid:`（`invalid_reason` 已清空）。反证：`--as extA` 调 `/verify` → `403 没有访问权限`（§4.3 探针 1） |
| 4 | **状态机不分 `sample_kind`；转移表是与表无关的纯函数，EMBED-MODEL-001 直接复用**（`FLOW:F-SAMPLE-01.step5` + CR-20260917-05） | `VerifyTransitions.check(String from, String to, boolean actorIsInternal)`：静态、无 Spring、无库、无样本字段；样本特有的「内部编号必填 / 唯一」「收样日期」「下游记录检查」全在 service | §4.3 用例 1/2 把 18 个组合**穷举跑两遍**（tissue / organoid，逐格结果相同）；用例 7 用反射断言签名只有 3 个参数、类里没有任何 `org.dromara.lqg.sample` 类型的成员或 `*Mapper` 字段 |

**另外两条 ticket §2 硬要求**：

- `SampleVerifyService` 集中管状态转移 ✅ —— 全仓 `verify_status` 的写入点只有本类 2 处 + SAMPLE-MODEL-001 的内部新增/修改（新增走 valid，修改 BO 里没有该字段）。
- `PUT /lqg/sample` 不许再直接改 `verify_status`（从入参 BO 里拿掉该字段）✅ —— `SampleSubmitBo` **本来就没有** `verifyStatus`（SAMPLE-MODEL-001 已做对），本票用反射用例把它**钉住**，防止以后有人加回来。

## §2 改了哪些文件（ticket §4.2）

### 2.1 Flyway（★ 越出 `touches` 一条，见 §6 WARN-1）

**`code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609221001__SAMPLE-VERIFY-001-verify-menu.sql`**（新增，仅 1 个菜单 + 2 行 `sys_role_menu`，不建表、不动业务数据）

**取号依据 `doc/lint-profile.yaml`**：D2 = `20260922`；HHmm 域分段 **SAMPLE = 10xx**；`1000` 已被 SAMPLE-MODEL-001 占用 → 取 **`1001`**，且**避开**留给 SAMPLE-WEB-001 的 `1010-1019`；菜单号 SAMPLE 域 `5200-5299` 里取未占用的 **5206**。

**为什么必须加**：ticket §2 要求 `/lqg/sample/{id}/verify` 的权限串是 `lqg:sample:verify`，而 SAMPLE-MODEL-001 的迁移只建到 5205（`lqg:sample:{list,query,add,edit,remove}`）——SAMPLE-MODEL-001 完工报告 §2.1 自己也写了「没有做『导出 / 核验』菜单项，那是 SAMPLE-EXPORT-001 / SAMPLE-VERIFY-001 的权限串」。若依 `@SaCheckPermission` 的权限集合来自「角色 → 菜单 perms」，缺这一行 `--as staff`（102 `lqg_internal`）**恒 403**，accept 1 整条拿不到 200。实测取证（§4.3 探针 1 是反方向：外部 403；迁移前 staff 也是 403）。

```
$ python3 doc/verify/db.py --sql "SELECT menu_id||':'||menu_name||':'||menu_type||':'||perms FROM sys_menu WHERE menu_id BETWEEN 5200 AND 5206 ORDER BY menu_id"
5200:样本总表:C:lqg:sample:list
5201:样本查询:F:lqg:sample:list
5202:样本详情:F:lqg:sample:query
5203:样本新增:F:lqg:sample:add
5204:样本修改:F:lqg:sample:edit
5205:样本删除:F:lqg:sample:remove
5206:样本核验:F:lqg:sample:verify          ← 本票

$ python3 doc/verify/db.py --sql "SELECT role_id||':'||menu_id FROM sys_role_menu WHERE menu_id=5206 ORDER BY role_id"
101:5206
102:5206

$ python3 doc/verify/db.py --sql "SELECT version||'|'||script||'|success='||success FROM flyway_schema_history WHERE version LIKE '2026092210%' ORDER BY installed_rank"
202609221000|V202609221000__SAMPLE-MODEL-001-sample.sql|success=true
202609221001|V202609221001__SAMPLE-VERIFY-001-verify-menu.sql|success=true      ← 启动时自动应用，无跨域补号告警
```

### 2.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.sample.verify`）

| 文件 | 职责 |
|---|---|
| `verify/VerifyTransitions.java` | **纯函数**状态机：`check(from, to, actorIsInternal)`、`isKnownAction` / `targetOf` / `isKnownStatus` / `legalTransitions` / `rejectionMessage`；合法 5 条边写成白名单 `Set`，其余一律拒绝（含 null / 未知值 / 自环） |
| `verify/SampleVerifyBo.java` | `PUT /lqg/sample/{id}/verify` 入参：`action, receiveDate, internalNo, isFixed, processTime, hasQcSheet, hasViabilityReport, operatorName, reason`；**没有** `verifyStatus` |
| `verify/SampleResubmitBo.java` | 外部重提的**送检段**入参（tissue 8 项 / organoid 3 项）；没有核验段、没有状态、没有 `internalNo/receiveDate/submitNo/submitSource/submitterId` |
| `verify/SampleVerifyService.java` | 状态唯一出口：`verify(id, bo)`（4 条内部转移）+ `resubmitByExternal(sampleId, userId, bo)`；先判转移表 → 再判必填 → 再判唯一性 / 下游记录 → 最后才写库（整段一个事务）；写库显式补 `update_by/update_time`（wrapper 不自动填，SAMPLE-MODEL-001 坑 1） |
| `verify/controller/SampleVerifyController.java` | `PUT /lqg/sample/{id}/verify`，`@SaCheckPermission("lqg:sample:verify")` |
| `src/test/java/.../sample/verify/VerifyTransitionsContractTest.java` | 8 个用例，见 §4.3 |

**接口清单（本票）**

```
PUT  /lqg/sample/{id}/verify   {action:"valid"|"invalid", receiveDate, internalNo, isFixed, processTime,
                                hasQcSheet, hasViabilityReport, operatorName, reason}
                                → 权限 lqg:sample:verify（101 lqg_admin / 102 lqg_internal）
```

**没改任何上游文件**、没改 `ruoyi-lqg/pom.xml`（无新依赖：复用 `SampleFieldCipher` / `SampleChildrenCheckers` / `SampleKindRules` / `StaffGrantRules.isInternal` / `UnitQueryService`）。

## §3 对照 ticket `touches`

`touches` 列两处：`sample/verify/src/main/**` 与 `sample/verify/src/test/**` —— 本票的 4 个 Java 类 + 1 个测试类**全在其中**。

**越界 1 条**：`ruoyi-admin/src/main/resources/db/migration/V202609221001__SAMPLE-VERIFY-001-verify-menu.sql`（原因与取证见 §2.1；已按 briefing「确需则 WARN + SAMPLE 10xx 取号、别抢 `101*`」执行，列 §6 WARN-1）。其余零越界。

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，与 D1 六张票 + SAMPLE-MODEL-001 同源）

`doc/verify/api.sh` 第 71 行 `ps -o lstart=` 在 subagent 沙箱被禁。**按规矩没有改 `api.sh`**。本票实测两条：

```
$ bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --bizcode GET /lqg/sample/9000001002
EXIT=1
[stderr] doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
         date: illegal option -- d          ← 回退走 GNU `date -d`，macOS 的 date 不认
         bash: /bin/ps: Operation not permitted

$ ps -o lstart= -p 57771
ps_exit=126
```

（注：SAMPLE-MODEL-001 报告里写的是 exit 2；本票实测是 **exit 1** —— `ps` 被禁后 `|| date -d` 两半都失败，`set -e` 在赋值那一行直接退出。结论相同：恒非 0，且与本票实现无关。）

**等价反 stale 证据**（覆盖那道守卫的两个半边，五项）：

```
jar            : code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
jar mtime      : 2026-09-21 22:50:58
8081 PID       : 57771                       （lsof -ti tcp:8081 -sTCP:LISTEN）
lqg src newer  : （空）                       ← 没有比 jar 新的 ruoyi-lqg 源码
admin src newer: （空）                       ← 同上（migration 也在 jar 里）
PID 打开该 jar  : 2 处                        （lsof -p 57771 | grep -c 'ruoyi-admin/target/ruoyi-admin.jar'）
后端日志 mtime  : 2026-09-21 22:51:19        （进程起于打包之后 = 没 stale）
嵌套 jar 复核   : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含
                  org/dromara/lqg/sample/verify/{VerifyTransitions,SampleVerifyBo,SampleResubmitBo,
                  SampleVerifyService}.class + verify/controller/SampleVerifyController.class
Flyway 启动日志 : "Migrating schema public to version 202609221001 - SAMPLE-VERIFY-001-verify-menu"
                  "Successfully applied 1 migration ... now at version v202609221001"
```

**复跑脚本随报告落盘**：`doc/waves/reports/SAMPLE-VERIFY-001/accept-runners/{verify001-acc1,verify001-acc2}.sh`（与本次实跑的逐字相同，从工作区根或任意 cwd 执行均可；唯一差异 = 去掉 `--fresh-module`）。两条都把长链包进函数判**整条** rc，并在开头 `rm -f $TMPDIR/lqg-verify-token-*`（api.sh 的 token 缓存只按 mtime 判新鲜，改库/改身份后会假 401 / 假 200）。

### 4.1 accept 1 · STATE —— ✅

```
$ bash doc/waves/reports/SAMPLE-VERIFY-001/accept-runners/verify001-acc1.sh
pending|-|-
true
valid|T-hli77|9000000101|Y|external
true
true
9000001002:invalid:误判，退回
9000001003:valid:
ACCEPT-1 EXIT=0
ACC1_RC=0
```

逐段（原文 10 段 `&&` 链）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 2 | `--as staff --bizcode PUT …/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17"}'` | `500	判有效必须同时给收样日期与内部编号，缺少：内部编号` | 缺内部编号被拒（命中 `^(400\|500)`） |
| 3 | 同上 + `"internalNo":"T-hli01"` | `500	内部编号「T-hli01」已存在，请换一个` | **走的是真实分支**：`T-hli01` 是 seed 里挂在 1001 上的真编号 |
| 4 | `'{"action":"invalid"}'` | `500	判无效必须写原因` | 判无效缺原因被拒 |
| 5 | `db.py … id=9000001002 --eq "pending\|-\|-"` | `pending\|-\|-` | ★ **三次被拒之后库里一字不变**（不是先 UPDATE 再校验） |
| 6 | `… '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli77","isFixed":"Y","operatorName":"李工"}' \| jq -e '.code==200'` | `true` | pending→valid 200 |
| 7 | `SELECT verify_status\|\|'\|\|'internal_no\|\|'\|\|'verify_by\|\|'\|\|'is_fixed\|\|'\|\|'submit_source …` | `valid\|T-hli77\|9000000101\|Y\|external` | 收样段一并落库、`verify_by`=李工、**`submit_source` 仍是 `external`（快照没被重算）** |
| 8 | `… '{"action":"invalid","reason":"误判，退回"}' \| jq -e '.code==200'` | `true` | valid→invalid（内部误判纠正）200 |
| 9 | `--as staff PUT …/9000001003/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli78"}'` | `true` | ★ **invalid→valid 这条内部改判路径确实放开**（1003 是 seed 里的无效样本） |
| 10 | `SELECT id\|\|':'… WHERE id IN (9000001002,9000001003) --col-set "…"` | `9000001002:invalid:误判，退回`<br>`9000001003:valid:` | ★ 1003 改判有效后 **`invalid_reason` 已清空**（不再是「信息不全：缺住院号」） |
| 11 | `reseed.sh --yes` | （静默） | 收尾回干净 seed |

**counterfeit 逐条排掉**（ticket 点名的 4 种形态）：

```
$ # 「判有效不校验内部编号必填」→ 段 2 会拿到 200；实测 500
$ # 「先 UPDATE 再校验 / 事务没回滚」→ 段 5 不是 pending|-|-；实测 pending|-|-（见上）
$ # 「invalid→valid 没放开」→ 段 9 红；实测 200，且段 10 的 1003 是 valid:
$ # 「改判后没清 invalid_reason」→ 1003 带「信息不全：缺住院号」；实测 9000001003:valid:
$ # 走的是真实分支：段 3 用的是 seed 里真实存在的 T-hli01
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli01' AND del_flag='0'" --eq 1
1
```

### 4.2 accept 2 · DATA —— ✅

```
$ bash doc/waves/reports/SAMPLE-VERIFY-001/accept-runners/verify001-acc2.sh
true
true
external:3
internal:1
3
true
ACCEPT-2 EXIT=0
ACC2_RC=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 2 | `--as admin POST /lqg/auth/staff '{"phone":"13800000011","name":"王医生","roleKey":"lqg_internal",…}'` | `true` | extA **原地升级**成内部角色（`.data.upgraded==true`） |
| 4 | `--as extA POST /lqg/sample '{…,"internalNo":"T-snap01"}'` | `true` | 升级后 extA 能走内部新增（200） |
| 5 | `SELECT submit_source\|\|':'…submitter_id=9000000111 AND del_flag='0' GROUP BY submit_source --col-set "external:3,internal:1"` | `external:3` / `internal:1` | ★ **老的三条仍是 external**（快照），只有新录的一条是 internal |
| 6 | `SELECT count(*) … JOIN sys_user_role JOIN sys_role … r.role_key='lqg_internal' AND s.submit_source='external' --eq 3` | `3` | ★★ **两侧不同源的独立取证**：一侧是授权接口改的角色（join 出来现在是内部），一侧是样本表里早先落下的列 → 仍有 **3** 行「现在是内部角色但 submit_source=external」 |
| 7 | `--as staff GET '/lqg/sample/list?submitSource=external&pageSize=100' \| jq -e '…index("9000001001") != null'` | `true` | 1001 在结果里 —— ⚠️ **这条是空转**，见 §6 WARN-2（`submitSource` 筛选根本没实现，所以返回全表；绿不代表验证到了筛选） |
| 8 | `rm extA token` + `reseed.sh --yes` | （静默） | 不把升级后的身份留给后面的断言 |

**counterfeit 排掉**：

```
$ # 「submit_source 不落库、每次按提交人当前角色现算」→ 段 5/6 会变成全 internal；实测 external:3 + join 计数 3
$ # 「列表筛选也是现算的」→ 该筛选压根没实现（WARN-2），这一格本票证不到，留给 SAMPLE-WEB-001
$ # 补一条：核验/重提也不重算来源
$ python3 doc/verify/db.py --sql "SELECT submit_no || '|' || submit_source || '|' || submitter_id FROM t_lqg_sample WHERE id=9000001002" --eq "SJ90000002|external|9000000111"
SJ90000002|external|9000000111          ← 本票把它核验成 valid 之后，三列一字未动
```

### 4.3 追加证据（accept 之外的机器证据）

**(a) 转移表单测 —— 8 个用例全绿，tissue / organoid 各跑一遍**

```
$ mvn -pl ruoyi-modules/ruoyi-lqg test -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome
[INFO] Running org.dromara.lqg.sample.verify.VerifyTransitionsContractTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0        ← 31（本票前）+ 8（本票）
[INFO] BUILD SUCCESS
```

用例清单（`VerifyTransitionsContractTest`）与「每一格」的覆盖方式：

| # | 用例 | 钉住什么 |
|---|---|---|
| 1 | `tissueKind_wholeTransitionTableIsCovered` | 3 状态 × 3 目标 × 2 身份 = **18 个组合穷举**（tissue 视角）：合法恰好 **5**、非法恰好 **13**；期望值是 ticket 表格的**独立重述**，不是从实现反推 |
| 2 | `organoidKind_wholeTransitionTableIsCovered` | 同一张表在 **organoid** 视角再跑一遍，逐格必须一致（状态机不分 `sample_kind`，CR-20260917-05） |
| 3 | `theFiveLegalTransitionsAreExactlyTheOnesInTheTicket` | 5 条合法边逐条正面断言 |
| 4 | `externalCanNeverReachValid_andInternalCannotResubmitToPending` | 外部 `→valid` 两条都拒；`valid→pending` 谁都不行；`invalid→pending` 只给外部；外部不能把 pending 置 invalid |
| 5 | `selfLoopsAndUnknownValuesAreRejected` | 3 个自环全拒；`weird`/null/空串的 from、to 全拒（「任何人把状态直接写成任意值」） |
| 6 | `actionMapsToTargetStatusOnlyThroughTheTable` | `action → targetOf → check` 是唯一判定链；未知 action 抛 `IllegalArgumentException` |
| 7 | `transitionTableIsTableAgnosticPureFunction` | ★ **EMBED-MODEL-001 的复用前提**：`check` 静态、3 参 `(String,String,boolean)`；类内任何方法参数 / 字段都不得是 `org.dromara.lqg.sample.*` 或 `*Mapper`；同参同结果（纯） |
| 8 | `requestBodiesCannotCarryVerifyStatus` | `SampleSubmitBo` / `SampleVerifyBo` / `SampleResubmitBo` 都不得有 `verifyStatus` 字段或 `setVerifyStatus`（ticket §2 末条） |

**(b) 对抗性探针（accept 之外，证明「不是靠拒绝一切也能绿」）**

```
$ bash doc/verify/reseed.sh --yes >/dev/null && rm -f ${TMPDIR:-/tmp}/lqg-verify-token-*

探针1  外部身份调 /verify                    → 403	没有访问权限，请联系管理员授权
       库内 1002                            → pending|-|-          （状态只能由内部改）
探针3  action=pending                        → 500	核验动作只能是 valid（判有效）或 invalid（判无效）
探针4  action=approve                        → 500	（同上）
探针5  请求体夹带 verifyStatus:"pending"      → 200 且库内 1003 = valid   （状态由 action 推，不由请求体定）
探针6  valid→valid 自环                      → 500	非法核验状态转移：valid → valid（操作者是内部人员）；合法转移只有
                                               pending→valid(内部)、pending→invalid(内部)、invalid→pending(外部)、
                                               invalid→valid(内部)、valid→invalid(内部)
探针7b pending 样本(1007) 判有效缺收样日期    → 500	判有效必须同时给收样日期与内部编号，缺少：收样日期
       库内 1007                            → pending|-            （被拒后没落库）
探针8  reason="   "（空白）                   → 500	判无效必须写原因
探针10 核验写入                              → valid|9000000101|9000000101|2026-09-21 22:52
                                               （verify_by 与 update_by 都是李工 —— wrapper 显式补 update_by 生效）
探针11 核验不动三列                           → SJ90000002|external|9000000111
```

**正向对照（避免「一律拒绝也能绿」）**：accept 1 的段 6/8/9 三次 200 + 段 7/10 两次库内取值，都是真实成功路径。

## §5 遗留与 raise（ticket §4.4）

1. **越出 `touches`**：`V202609221001__SAMPLE-VERIFY-001-verify-menu.sql`（权限串 `lqg:sample:verify`）。不做则 accept 1 整条 403，见 §2.1 / WARN-1。
2. **与 `doc/api-contract.md` 的差异**：
   - 契约第 47 行列的形状 `{action, receiveDate, internalNo, isFixed, processTime, hasQcSheet, hasViabilityReport, operatorName, reason}` —— **逐字实现**。
   - 契约第 45 行 `GET /lqg/sample/list` 的参数列了 `submitSource` —— **实际未实现**（不是本票范围，但 accept 2 有一格依赖它）→ WARN-2。
3. **没把握 / 需要确认的口径**（三处，均已在代码注释里写明，不改权威）：
   - 判有效时「一并落收样段其余字段」的 null 语义：本实现 = **给了就落、没给的保持原值**（动作语义）。理由：从 pending / invalid 进 valid 时这些列本来就是 NULL，两种取法在 accept 上完全等价；选 patch 是因为不会误清数据。若 SAMPLE-WEB-001 的核验抽屉按「整份表单替换」预期，改 `applyValid` 里五行的 `set(condition, …)` 即可 → WARN-5。
   - `resubmitByExternal` 的送检段语义 = **整体替换**（没传的清空），与本仓既有先例 `ExtProfileUpdateService`（AUTH-GROUP-001 的 `PUT /mp/ext/profile`）同款；并按 `sample_kind` **必须给本类类型列**（tissue→`tissueType`、organoid→`organoidType`），否则拒。AUTH-EXT-001 落外部 controller 时若预期 patch 语义，需回来对齐。
   - `resubmitByExternal` **本票没有运行时入口**（ticket §3：外部 controller 在 AUTH-EXT-001）→ 它只有「转移表 + 状态判定」这部分被单测钉住，service 那一段没被 accept 打到，建议 AUTH-EXT-001 补端到端断言 → WARN-8。
4. **`SAMPLE-MODEL-001` 的 `PUT /lqg/sample`**：`SampleSubmitBo` 本来就没有 `verify_status` 字段，**无需改动**（不是漏做）；本票用反射用例把它钉死，防回退。
5. **`SampleChildrenChecker` 的 valid→invalid 前置检查**本票跑得通是因为**注册数 = 0**（SAMPLE-MODEL-001 的预期值）。EMBED-MODEL-001（D3）注册实现后，seed 里 1002 名下有一条待核验石蜡包埋送样 2006，本票 accept 1 的 `valid→invalid` 会因此变红 → WARN-4。
6. **没做（ticket §3 边界，逐条核过）**：不做工作台核验抽屉（SAMPLE-WEB-001）、不做外部 controller（AUTH-EXT-001）、不做核验通知、不做核验历史流水表（只记最后一次 `verify_by/verify_time`）、不做列表筛选（SAMPLE-WEB-001）、不做导出（SAMPLE-EXPORT-001）。

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | doc-drift | **ticket `touches` 不含 migration，但 `lqg:sample:verify` 权限串只能由本票补** | 本票新增 `V202609221001__SAMPLE-VERIFY-001-verify-menu.sql`（SAMPLE 10xx，避开 SAMPLE-WEB-001 的 `101*`）。影响：`touches` 白名单 / QA 越界核查会把它标成越界改动；不补则 `--as staff` 调 `/verify` 恒 403、accept 1 全红。方案：把该文件补进 ticket `touches`（它确实属于本票交付物），或把 5206 回填进 SAMPLE-MODEL-001 的迁移并注明「核验权限串由 SAMPLE-VERIFY-001 使用」。**本票不改权威**。 |
| WARN-2 | **S2** | counterfeit-risk / vacuous-assertion | **accept 2 第 7 段（`GET /lqg/sample/list?submitSource=external`）是空转**：`SampleQueryBo` 根本没有 `submitSource` 字段，Spring 忽略未知参数 → 返回全表，`index("9000001001") != null` 自然成立 | 取证：`{"total":9,"ids":[…,9000001008,9000001009],"sources":[…,"internal","internal"]}` —— **internal 的 1008/1009 也回来了**，证明筛选被忽略。影响：契约第 45 行列了 `submitSource` 但未实现；这格「筛选用存下来的列」当前没有任何机器证据（真正证明快照的是同段第 6 段的 SQL join，它是有意义的）。方案：SAMPLE-WEB-001（认领列表其余筛选）把 `submitSource` 加进 `SampleQueryBo` + `SampleQueryService`，直接 `.eq(Sample::getSubmitSource, …)`（**不许按角色现算**）；同时把该 accept 段改成「返回集合精确等于 external 的 id 集合」以去掉空转。 |
| WARN-3 | S3 | doc-drift | 派单 briefing 说「`GET /lqg/sample/list` 已支持 `submitSource` 等筛选」与盘上代码不符 | 同 WARN-2 的取证。影响：后续 ticket 若照这句假设写 accept，会得到空转绿。方案：修 briefing / 在 `doc/waves/state.json` 的派单模板里改成「list 目前只支持 5 个筛选（sampleKind/verifyStatus/internalNo/donorName/hospitalNo）」。 |
| WARN-4 | **S2** | downstream-risk | `valid→invalid` 的下游记录检查在 EMBED-MODEL-001（D3）注册后会命中 seed 1002 的待核验送样 2006 → 本票 accept 1 在 D3 之后复跑会红 | 影响：D3+ 的回归 / 若 QA 跨波复跑本票。方案（择一，建议前者）：① 要求 EMBED / CRYO / QC 的 `SampleChildrenChecker` 只把**已生效**（非软删、且业务上已成立）的下游记录算 children，待核验的外部送样不算；② 本票 accept 1 把 `valid→invalid` 换到名下确实没有下游记录的样本（1003 已被占用，需另造或改用 1007）。建议派 EMBED-MODEL-001 时把这条前置条件写进它的 ticket。 |
| WARN-5 | S3 | clarify | `PUT /lqg/sample/{id}/verify` 判有效时「一并落收样段其余字段」的 null 语义 ticket 没写 | 本实现取「给了就落、没给的保持原值」（动作语义）。影响：SAMPLE-WEB-001 的核验抽屉若按整份表单替换预期，可能观察到「没传的没清空」。方案：在契约那一行补一句判有效的字段语义（整体替换 / 部分更新）。 |
| WARN-6 | S3 | harness | `api.sh --fresh-module` 在 subagent 沙箱恒非 0（**第 8 次命中**）；本票实测为 **exit 1**（不是此前报告的 exit 2） | `ps -o lstart=` 被禁 → `\|\| date -d` 回退也失败（macOS date 不认 `-d`）→ `set -e` 在赋值行退出，故为 1。只影响 subagent 沙箱，人工 / CI 正常。本票用五项等价证据替代（§4.0）。方案：把启动时间判断换成 `lsof` / 日志 mtime 的兼容写法，或给 `api.sh` 加「跳过启动时间守卫」开关。 |
| WARN-7 | S3 | harness | `reseed.sh` 清不掉运行时按手机号建的雪花 id 账号（同 AUTH-STAFF-001 WARN-7 / AUTH-GROUP-001 WARN-7 / SAMPLE-MODEL-001 WARN-6，已记过三次） | 本票收尾跑 `clean-orphan-accounts.sh --yes`（accept 2 会把 extA 升级，属于运行时改账号）。方案：把该脚本收进标准收尾步骤。 |
| WARN-8 | S3 | debt | `resubmitByExternal` 本票没有运行时入口，service 那一段无端到端断言 | ticket §3 边界（外部 controller 在 AUTH-EXT-001），不是缺陷。影响：invalid→pending 的「清 `invalid_reason/verify_by/verify_time`」「pending 改完仍 pending」「valid 拒绝」「非本人拒绝」四条只有代码审查，没有机器证据。方案：AUTH-EXT-001 落 `PUT /mp/ext/sample/{id}` 时补一条 accept（或 QA 在 D2 之外补）。 |

> 已在上游报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数、surefire `groups` 假绿、`api.sh` token 缓存、`updateById` 忽略 null、跨域补号的 Flyway 坑、`PageQuery` 只有两参构造、`@EncryptField` 的 `ENC_` 前缀与裸 Base64 不同源（SAMPLE-MODEL-001 WARN-2 —— 本票不新增加密列，未再撞）。

## §7 坑与解法（给下游）

1. **权限串不在迁移里 = accept 全 403，而不是 500**：`@SaCheckPermission` 的权限集合来自「角色 → 菜单 perms」，`lqg:sample:verify` 这种「动作型」权限必须在 `sys_menu` 有一行（`menu_type='F'`）并授给角色。SAMPLE-MODEL-001 只建到 5205，本票补 5206（`V202609221001`）。**新票凡是新增 `@SaCheckPermission("lqg:xxx:yyy")`，先 `SELECT perms FROM sys_menu WHERE perms LIKE 'lqg:xxx%'` 核一遍**，别等 accept 报 403 才回头。
2. **`--fresh-module` 挂的形态是 exit 1，不是 2**：`ps -o lstart=`（line 71）在沙箱被禁后 `||` 回退到 GNU `date -d`，macOS 的 `date` 不认 → 两半都失败 → `set -e` 在赋值行退出（1）。等价证据要覆盖**两个半边**：源码不比 jar 新 + 进程不早于 jar（用 `lsof -p PID` + 日志 mtime + 嵌套 jar 内容）。
3. **转移表要「与表无关」得把样本字段全推出去**：`check(from,to,actorIsInternal)` 里只留状态与身份；「内部编号必填 / 唯一」「收样日期」「下游记录检查」都是 service 的活。这样 EMBED-MODEL-001 才能原样复用它 —— 反射用例 7 就是防有人日后往表里塞 `sampleKind`。
4. **`valid→invalid` 的 children 检查本票恒 false（注册数 0），但别把它当永远如此**：一旦 EMBED/CRYO/QC 注册了 checker，seed 1002 名下的待核验送样 2006 就会让本票 accept 的那一段变红（WARN-4）。checker 的语义（是否只数「已生效」的下游记录）要在派 D3 票时说清。
5. **`update(null, LambdaUpdateWrapper)` 必须显式补 `update_by/update_time`**（SAMPLE-MODEL-001 坑 1 的同源形态）：本票 `applyValid` / `applyInvalid` / `resubmitByExternal` 三处都 `.set(updateBy, operatorId).set(updateTime, now)` —— 探针 10 实测 `update_by=9000000101`（不是 seed 的创建人）。
6. **先校验后写库要落到「同一个事务内的顺序」上**：accept 1 的第 5 段断的是「三次被拒后 `pending|-\|-`」，所以必填 / 唯一性 / 转移表判定必须在第一条 `SET` 之前；`@Transactional(rollbackFor = Exception.class)` 是第二道保险，不是第一道。
7. **`submit_source` 千万别回填**：本类三处写库都没碰这一列，探针 11 与 accept 2 段 6 是两道独立的机器证据。想看「现在是什么角色」用 `StaffGrantRules.isInternal(token 的 rolePermission)`，别把它写回业务列。

## §8 验证用长进程（ticket §4.5）

- **后端 java（8081）：收尾时已关**（`lsof -ti tcp:8081 -sTCP:LISTEN` → PID 57771 → `kill 57771`；**没用 `pkill -f ruoyi-admin.jar`**，本机 8080 上有 Kevin 的另一个 java 服务）。起法 `bash .tmp/run-backend.sh`（gitignore），日志 `.tmp/backend-verify.log`。
- **docker 容器：留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），未停。
- **未占 8080 / 5432 / 6379 / 9000 / 9001**（Kevin 本机日常服务）。
- **DB 收尾** = `reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`；复核 `t_lqg_sample` = 9 行 `del_flag='0'`（valid 6 / pending 2 / invalid 1）+ 1 行 `del_flag='1'`（seed 原样）。
- 前端**一个字节没动**，本票没有起过任何前端进程。
