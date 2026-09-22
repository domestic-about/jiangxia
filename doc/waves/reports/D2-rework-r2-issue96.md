# D2 返工 r2 · issue #96（S1）· 工作台「来源单位」筛选筛不出内部录的样本

- 分支：`task/D2`（未切分支、未 push、未 merge、未动 `doc/waves/state.json` / `_manifest.json`）
- 范围：**只修这一条 S1**（issue #96）；r2 的 3 条 S2/S3 一律未碰
- 结论：**修好且四门证据齐**——QA 专探针 `CONFIRMED_BUG → NOT_REPRODUCED`；impl 探针 `BROKEN → FIXED`；
  `L2r2-web.mjs` `26/28 → 28/28`；`SAMPLE-WEB-001` accept 1 / accept 2 全绿；`mvn … -pl ruoyi-modules/ruoyi-lqg -am test`
  `Tests run: 56, Failures: 0`；plus-ui `build:prod` `✓ built in 7.55s`。

---

## 1. 现象（纯 seed 即可复现，未改 seed）

| 步骤 | 修前 | 修后 |
|---|---|---|
| `GET /lqg/sample/list?internalNo=T-oco01` | `SJ90000009`，行内「来源单位」= **B 大学** | 同左（本来就能筛到） |
| `GET /lqg/sample/list?sourceUnitId=9000009002`（B 大学） | `total=1`，只回 `SJ90000006`（外部 extD 送的） | `total=2`，回 `SJ90000006` + **`SJ90000009`** |
| 库内 | `SJ90000009 = internal \| source_unit_id 9000009002 \| B 大学 \| T-oco01` | 同左 |

界面证据（QA 的浏览器探针）：先按内部编号筛出 1009、行内单位列写 **B 大学**
（`doc/waves/regression/D2/shots/L2r2-web/18-sourceunit-via-internalNo.png`），
改按来源单位 = B 大学筛则它消失 —— 修前截图 `…/19-sourceunit-filter-B.png` 只有 1006（**QA r2 原图，本票跑完已还原**），
修后同一步的截图存档在 `doc/waves/reports/D2-rework-r2-issue96/evidence/19-sourceunit-filter-B-AFTER.png`
与 `…/evidence/11-filter-2-sourceUnit-B-AFTER.png`（两行：1006 + 1009）。

## 2. 根因

`SampleQueryService.list` 对「组别 **/** 来源单位」**一律**先走
`SampleSubmitterProfileQuery.submitterIds(unitId, groupId)`（SQL 只查 `t_lqg_ext_profile.unit_id / group_id`）
拿提交人 id 集合，再 `submitter_id IN (...)` 收窄；注释给的理由「样本行上没有 group_id」**只对组别成立**。
来源单位在样本行上本来就有 `source_unit_id` / `source_unit_name` 快照（`FIELD:t_lqg_sample.source_unit_id`，
authority 明确 `source_unit_id` 可空、`source_unit_name` 非空），于是**内部账号提交的行**
（`submit_source='internal'`、提交人没有外部档案）永远进不了 `submitter_id` 集合。

为什么以前没暴露：旧 accept 只测「来源单位 = A 医院」，而 A 医院下没有内部样本
（seed 1008「本中心」没有单位 id、1009 只在 B 大学）→ L1 与 r1 的 L2 都刚刚好绕过。

## 3. 修法（两条口径的分界）

**来源单位 = 样本行自己的 `source_unit_id`（内部/外部一视同仁）**
`SampleQueryService.list` 新增

```java
.eq(q.getSourceUnitId() != null, Sample::getSourceUnitId, q.getSourceUnitId())
```

**组别 = 提交人的外部档案（保持不变）**
`submitterProfileQuery.submitterIds(q.getGroupId())` + `in(submitter_id)`；
档案查空 → 仍直接回空页（不许退化成全表）的短路只服务组别。
`SampleSubmitterProfileMapper.selectSubmitterIds` 去掉 `unitId` 参数与 `<if test="unitId != null">` 分支
（口径收窄后这条 SQL **只**服务组别），`SampleSubmitterProfileQuery.submitterIds` 同步收成一个参数。

**名字与 id 的边界（写进注释/BO javadoc，也写清裁定）**
- 按名字筛只有一条路：`keyword` 的 `source_unit_name LIKE`（给小程序搜索框，未改）。
- `sourceUnitId` 是 **id 相等**：自填单位名（`source_unit_id` 为空、只有 `source_unit_name`，如 seed 1008「本中心」）
  的行**不落进**按 id 的筛选；**同名不同 id 时以 id 为准**（下拉给的就是单位表的 id）。
  seed 里「名字同为 B 大学但 id 为空/不同」的行 = **0**，所以该裁定不影响任何 seed 期望值。

**同类一次清干净**：核了 `SampleQueryBo` 的**全部**筛选参数（本票探针 C8–C16 逐条现算）：
`internalNo / sampleKind / submitSource / verifyStatus / receiveDateBegin+End / tissueType(LIKE) /
operatorName(LIKE) / donorName(加密精确) / hospitalNo(加密精确)` **都打在样本行自己的列上，对内部行全部成立**；
`keyword` 走样本行的 `internal_no` / `source_unit_name`；`sort=recent / mine` 与样本行经手人有关 ——
**除 `sourceUnitId` 外没有第二处「把样本行上的列绕道提交人档案」的错误**。

改动文件（5 个）：
`SampleQueryService.java` · `SampleSubmitterProfileQuery.java` · `SampleSubmitterProfileMapper.java` ·
`SampleQueryBo.java`（javadoc 口径改写） · `SampleTableQueryContractTest.java`（断言 + 新增 1 个测试）。

## 4. 反 stale（沙箱 `--fresh-module` 恒非 0，自行等价核实）

- jar：`code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar` mtime **Sep 22 06:13**（`spring-boot:repackage` 重打）；
  `find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer <jar>` = **0**。
- 进程：8081 PID **30878**，`lsof -p 30878 | grep -c ruoyi-admin.jar` = **2**（确实持有新 jar 的 fd）；
  日志 `.tmp/issue96-backend2.log` 有 `RuoYi-Vue-Plus启动成功` + `mock 登录已开启（profile=[dev]）`。
- 前端：plus-ui dev 8082（本票受管作业）；后端 `.tmp/issue96-backend2.log` 对应 `bash .tmp/run-backend.sh`。
- 8080 全程未碰；DB 只用 5433（Kevin 的 5432 未碰）。

## 5. 探针：修前 / 修后

### 5.1 QA 专探针 `doc/waves/regression/D2/L2r2-probe-sourceunit.mjs`（未改动，原文跑）

```
修前（evidence/qa-probe-before.txt；与 QA r2 存档一致）   修后（evidence/qa-probe-after.txt）
{"filter_B_university":{"total":1,          {"filter_B_university":{"total":2,
  "submitNos":["SJ90000006"]},               "submitNos":["SJ90000006","SJ90000009"]},
 "tableHas1009":false,                      "tableHas1009":true,
 "db":{"s1009":"internal|9000009002|B 大学|T-oco01",   "db":{…同左…}},   ← 库侧证据两次一致
 "dbRowsWithUnitB":"SJ90000006,SJ90000009"}}
VERDICT CONFIRMED_BUG                       VERDICT NOT_REPRODUCED
```

> 该探针只有 `CONFIRMED_BUG / NOT_REPRODUCED` 两个词，**修完不会说 FIXED**；故另写等价接口级探针（下）。
> 原始输出存档：`evidence/qa-probe-before.txt` · `evidence/qa-probe-after.txt`。

### 5.2 impl 探针（新增）`doc/waves/reports/D2-rework-r2-issue96/probe-sourceunit-impl.mjs`

退出码即判据（0=FIXED / 1=BROKEN），期望值**一律库内现算**，不抄 accept 常数。

```
修前 → VERDICT BROKEN (bad=3)                        修后 → VERDICT FIXED (bad=0)
FAIL C1 sourceUnitId=9000009002(B 大学) == 库内样本行 source_unit_id 集合
        got=[SJ90000006] expect=[SJ90000006,SJ90000009] total=1
        → 修后 PASS got=[SJ90000006,SJ90000009] expect=[SJ90000006,SJ90000009] total=2
FAIL C2 sourceUnitId=9000009002 + submitSource=internal got=[] expect=[SJ90000009]
        → 修后 PASS got=[SJ90000009]
FAIL C3 该条件下返回了 submit_source=internal 的行 submitNo=NONE
        → 修后 PASS submitNo=SJ90000009
PASS C4  unit(样本行) + group(提交人档案) 组合 == join 现算            （修前修后都 PASS：1009 无档案，被组别那支正确排除）
PASS C5  sourceUnitId=9000009003(无样本) == 空集，不退化成全表
PASS C6  sourceUnitId=9000009001(A 医院) == 6 条（**与改前一致**）
PASS C7  groupId=9000009101 == 5 条（**组别口径不变**）
PASS C8–C16 其余全部筛选对内部行成立（含两个加密列精确：donorName=测试供体辛 / hospitalNo=ZY0000008 → SJ90000008）
INFO C17 seed 中「名字同为 B 大学但 id 不同/为空」的行 = 无（id 口径与名字口径在 seed 上恰好等价）
```

## 6. `doc/waves/regression/D2/L2r2-web.mjs`：修前 / 修后

（原始输出：`evidence/l2r2-web-before.txt` · `evidence/l2r2-web-after.txt`）

```
修前：== D2-r2-L2-web 26/28 通过 ==   （两条红都由这一条 S1 引起）
  FAIL R2W-04.2 筛选「来源单位=B 大学（含内部录的 1009）」 got=["SJ90000006"] dbExpect=["SJ90000006","SJ90000009"]
  FAIL R2W-05    11 种筛选 UI 驱动全部与「库内现算」一致   bad=1/12

修后：== D2-r2-L2-web 28/28 通过 ==   （其余 26 条一字未动，全 PASS）
  PASS R2W-04.2 … got=["SJ90000006","SJ90000009"] dbExpect=["SJ90000006","SJ90000009"]
  PASS R2W-05    11 种筛选 UI 驱动全部与「库内现算」一致   bad=0/12
  PASS R2W-04.1 来源单位=A 医院 → 6 条（不变）
  PASS R2W-04.3 组别=肝胆外科组（先选来源单位）→ 5 条（不变）
  PASS R2W-06   组合（A 医院+有效+外部）→ {1001,1004,1005}（不变）
```

脚本没改一行（本票只跑不改 QA 资产）。修前那次跑完 DB 被它自己改成 `1002=valid / 1003=invalid`，
后续探针/accept 前都先 `reseed.sh --yes`。

## 7. 重跑受影响的 accept（原文；仅去掉沙箱恒非 0 的 `--fresh-module`）

runner 落在 `doc/waves/reports/D2-rework-r2-issue96/`（原文照抄，文件头写明唯一差异 + 改库后清 token 缓存）：

- `accept1-sample-web-001.sh` → **PASS**
  - `sourceUnitId=9000009001` = `["9000001001"…"1005","1007"]`（**6 条，与改前一致** —— A 医院下没有内部样本）；
  - `groupId=9000009101` = `["9000001001","9000001002","9000001003","9000001004","9000001007"]`（**5 条，不变**）；
  - `sampleKind=organoid` = `["9000001009"]`；`submitSource=internal` = `["9000001008","9000001009"]`；
  - `verifyStatus=pending` = `["9000001002","9000001007"]`；日期区间 = `["9000001004","9000001005"]`；
  - 三条件组合 `sourceUnitId=9000009001&verifyStatus=valid&submitSource=external` = 3 条；全量 = **9 条**；
  - `pageSize=2` 前两行都是 `pending` 且 `total=9`。
- `accept2-sample-web-001.sh`（MENU/权限）→ **PASS**（菜单 5210 路径/组件、角色 101+102、六个 perms 集合、
  `getRouters` 只出现一次、`SegButtons` 计数 ≥ 4、抽屉里没有 `el-switch/el-select` 按钮字段）。

`/mp/int/sample/list` 与本票共用 `SampleQueryService.list`，但不发 `sourceUnitId/groupId` → 口径不变；
烟测 `--as staff GET '/mp/int/sample/list?pageSize=100&sort=recent'` = `total 2`，与库内「内部账号经手过」行数 2 一致。

## 8. 单测 / 契约测试（新增 + 改动，均在本次 `mvn test` 里绿）

`code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/query/SampleTableQueryContractTest.java`

- 改：`groupFilterReadsTheSubmittersProfileAndDoesNotLookAtBindStatus`
  —— 反射签名收成 `selectSubmitterIds(Long)`；新增硬断言 **`!sql.contains("unit_id")`**
  （「来源单位不许再回到这条档案 SQL 上」）；保留 `t_lqg_ext_profile / group_id / del_flag / 无 bind_status / 无 ${}`。
- 增：`sourceUnitFilterReadsTheSampleRowNotTheSubmitterProfile`
  —— 源码级钉死 `.eq(q.getSourceUnitId() != null, Sample::getSourceUnitId, q.getSourceUnitId())`
  与 `submitterProfileQuery.submitterIds(q.getGroupId())`，并反向断言**不许**出现
  `submitterIds(q.getSourceUnitId()…)`（issue #96 的病灶写法）。

`mvn -s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome -pl ruoyi-modules/ruoyi-lqg -am test`
→ `Tests run: 56, Failures: 0, Errors: 0, Skipped: 0`（`SampleTableQueryContractTest` 8 → 9 条），`BUILD SUCCESS`。

plus-ui：`pnpm run build:prod` → `✓ built in 7.55s`（exit 0）。

## 9. 坑与解法（3–5 行）

1. **URL 里的中文**：探针一开始直接把 `tissueType=结直肠` 拼进 `api.sh` 的 URL → curl 拿到空响应、`JSON.parse` 炸；
   一律 `encodeURIComponent`（`api.sh` 不做编码）。日期/英文参数不受影响，容易只踩中文那两条。
2. **`--fresh-module` 恒非 0**（沙箱禁 `ps` + 回退 GNU `date -d`）→ 反 stale 自己核三件套：
   jar mtime ≮ 源码、`find <src> -newer <jar>` 为空、`lsof -p <PID> | grep ruoyi-admin.jar` 非 0；改完必须重打包 + 按 PID 重启。
3. **改库/改身份后清 token 缓存**：`reseed.sh` 之后不清 `$TMPDIR/lqg-verify-token-*`，`api.sh` 可能拿旧 token 判身份 → 断言诡异地红/绿。
4. **QA 脚本自己会改库**：`L2r2-web.mjs` 会把 1002 判有效、1003 判无效；跑完必须 `reseed.sh --yes` 再跑探针 / accept，
   否则探针「库内现算」的期望值会被上一次运行污染。
5. **端口/进程**：8080 是 Kevin 的，`doc/verify/verify.env` 已把 `LQG_API_BASE` 指到 8081；关进程只按 `lsof -ti tcp:8081` 给出的 PID kill，不用 `pkill -f`。

## 10. WARN 清单（不拦门，未修，留台账）

| # | severity | type | title | 影响范围 / 方案 |
|---|---|---|---|---|
| W1 | S2 | doc-authority | `doc/tickets/SAMPLE-WEB-001/prompt.md:102` 把 `sourceUnitId` 与 `groupId` 并列写成「join `t_lqg_ext_profile` on `submitter_id`」——这正是 issue #96 的实现依据 | 下个 agent 照 ticket 原文重写读侧就会把 S1 带回来（本票新契约测试 + QA R2W-04.2 会红，但仍浪费一轮）。方案：authority 侧改这一行 + `doc/change-log.md` 记一笔（本票刻意**不动**冻结的 ticket 原文与 `state.json`） |
| W2 | S2 | accept-gap | `SAMPLE-WEB-001` accept 1 的 9 条筛选没有一条覆盖「来源单位下有内部样本」 | A 医院下无内部样本 → S1 一路漏到 r2 才被抓。方案：accept 1 增一行 `q 'sourceUnitId=9000009002'` = `["9000001006","9000001009"]`；当下覆盖由 QA `R2W-04.2` + 本票 impl 探针 C1/C2 顶上 |
| W3 | S3 | clarify | authority 没写明「自填单位名（`source_unit_id` 为空）/ 同名不同 id」时按什么筛 | 现在只在代码注释与 `SampleQueryBo` javadoc 里定了「id 为准、自填名走 `keyword` LIKE」。方案：把这条裁定写进 authority（或 change-log），免得与 `keyword` 口径打架 |
| W4 | S3 | usability | plus-ui 的「组别」下拉在未选来源单位时 `:disabled` → 只能「某个单位下的某个组」，无法只按组别筛 | accept 1 / R2W-04.3 都是先选单位再选组，所以不拦门；老师若想「跨单位只看肝胆外科组」做不到。方案：确认甲方是否需要，需要则去掉 `:disabled` 并把 `groupId` 单独可筛 |
| W5 | S3 | harness | QA `L2r2-web.mjs` 的 R2W-04.1/04.2 期望集合按 `source_unit_name` 现算，而产品口径是 `source_unit_id` | seed 里两者恰好等价（C17 核过：没有「名字对、id 不对」的行），但将来一旦出现自填单位名行就会**假红**。方案：期望改按 `source_unit_id` 现算，或显式断「id 口径」 |

## 11. 未做 / 边界（如实记账）

- 只改读侧；`SampleService` / `ExtSampleSubmitService` 的**写侧快照**（选单位就存单位名、没选就用自填名）一行未动。
- r2 的另外 3 条 S2/S3（无效原因列、旧回归断言退化等）**未碰**。
- 前端只做了 `build:prod` 验证，未改任何 plus-ui 源码（本条的病灶在后端；前端本来就发 `sourceUnitId`）。
- 重跑 `L2r2-web.mjs` 会重写 `doc/waves/regression/D2/shots/L2r2-web/*.png`（QA 资产）：本票**跑完已
  `git checkout --` 还原**，不把这些字节改进本 commit（否则 QA r2 文字证据与图片会对不上）；
  修后截图另存 `doc/waves/reports/D2-rework-r2-issue96/evidence/*-AFTER.png`。
- D3 的 `SampleExport` 尚未实现，本票只保证它将来复用的 `SampleQueryService.list` 口径已正确。
