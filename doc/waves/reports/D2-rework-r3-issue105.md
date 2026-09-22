# D2 返工 r3 · issue #105（S1）· `sort=recent` 的 OR 包组 + 类目身份不可越类改

- **issue**：D2 QA r3 唯一拦门项 `#105`（S1；L1 片与 L2 片独立复现后去重合并为同一条）
- **范围**：只修这一条 S1；QA 报的 S2/S3 一条未碰（见文末 WARN 清单）
- **分支**：`task/D2`（未 push / 未 merge / 未动 `doc/waves/state.json` 与 `_manifest.json`）
- **日期**：2026-09-22（r4 最后一轮验证之前的返工轮）
- **证据目录（本地，`.tmp` 已 gitignore）**：`.tmp/rework-r3/`、后端 p6spy 日志 `.tmp/rework-r3-backend-prefix.log`（修前）/ `.tmp/rework-r3-backend-postfix.log`（修后）

---

## 1 现象

两条同根表现，都在小程序内部侧（`/mp/int/sample/list` 的 `sort=recent` 支）：

1. **筛选被吞、页签串台**：reseed + staff 改 1001（tissue）之后
   `GET /mp/int/sample/list?sort=recent&sampleKind=organoid&pageSize=100`
   返回 **tissue 的 1001 + organoid 的 1009**；`sampleKind=bogus` 也返回 1001。
   UI 上「我的 → 历史编辑记录」两个页签（样本记录信息表 / 类器官收样记录）**行集合相交**，
   类器官页签里点一行会进 `pages/organoid/form?id=9000001001&mode=edit`（一条组织样本的类器官表单）。
2. **`mine=true` 静默失效**：admin 建一行（staff 从没碰过）后，staff 带 `mine=true` 仍能返回它 ——
   「只看我提交的」开关打开后没有收窄。

危害升级路径（L2 片给的证据）：类器官表单保存时固定发 `updateIntSample({sampleKind:'organoid', …})`，
而后端 `PUT /mp/int/sample` 对 valid 行「谁录的都能改」→ **一条组织样本点错行进来保存后被静默改判成类器官**
（两页签、两套必填集跟着错）。

## 2 根因

`SampleQueryService.list` 的 `sort=recent` 分支把「经手人」两项写成**顶层裸 `.or()`**：

```java
wrapper.inSql(Sample::getCreateBy, internalUsers)
       .or()
       .inSql(Sample::getUpdateBy, internalUsers);
```

MyBatis-Plus **只给** `and(consumer)` / `nested(consumer)` 补括号；顶层 `.or()` 只是往 SQL 里塞一个 `OR`。
于是整条 AND 链按优先级退化成 `(全部筛选 AND 经手A) OR 经手B`；只要某行的 `update_by` 是内部账号，
前面的 `sampleKind / sourceUnitId / internalNo / keyword` 与后面的 `mine` 全部被短路
（`mine` 那个 `.and(...)` 只落到第二个析取项上）。

### 2.1 修前 p6spy 原始 SQL（节选，`.tmp/rework-r3-backend-prefix.log`）

```
-- ?sort=recent&sampleKind=organoid
SELECT ... FROM t_lqg_sample WHERE del_flag='0' AND (sample_kind = 'organoid'
  AND create_by IN (SELECT user_id FROM sys_user WHERE user_type = 'sys_user' AND del_flag = '0')
  OR update_by IN (SELECT user_id FROM sys_user WHERE user_type = 'sys_user' AND del_flag = '0'))
ORDER BY COALESCE(update_time, create_time) DESC, id DESC LIMIT 100

-- ?sort=recent&mine=true（★ mine 只挂在第二个析取项上）
SELECT ... WHERE del_flag='0' AND (create_by IN (内部)
  OR update_by IN (内部) AND (create_by = 9000000101 OR update_by = 9000000101))
ORDER BY COALESCE(update_time, create_time) DESC, id DESC LIMIT 100
```

> 注：整段外面那一层括号是 MyBatis-Plus 3.5.16 `getCustomSqlSegment()` 自己加的，**修前就有**，
> 它只把整段包起来、不构成分组；缺的是里层「经手人」那一组的括号。（这一点把「数括号深度找顶层 OR」
> 这种判据直接证伪了，见 §4.1 与 §6 坑 1。）

## 3 修法

### 3.1 后端：把「经手人」与 `mine` 各自包成一组（`and(w -> …)` 嵌套）

`SampleQueryService` 把 wrapper 组装抽成包内可见的 `buildWrapper(q, submitterIds, me)`（`list` 只负责分页、
执行与装配），两处 OR 全部改成嵌套：

```java
// 「经手人 ∈ 内部」—— 顶层裸 .or() 会短路前面全部筛选（issue #105）
wrapper.and(w -> w.inSql(Sample::getCreateBy, INTERNAL_USERS_SQL)
    .or()
    .inSql(Sample::getUpdateBy, INTERNAL_USERS_SQL));
if (Boolean.TRUE.equals(q.getMine()) && me != null) {
    // mine 同样包一组，再与上面那组**相与**（不是并列）
    wrapper.and(w -> w.eq(Sample::getCreateBy, me).or().eq(Sample::getUpdateBy, me));
}
```

口径落点：
- `sort=recent` = 「中心全部内部人员新增或最后修改过的」（`create_by ∈ 内部 OR update_by ∈ 内部`，
  内部 = `sys_user.user_type='sys_user'`）—— CR-20260918-07；
- `mine=true` = 在上述范围里再收窄到 `create_by = 我 OR update_by = 我`；
- 两者都只出现在 `sort=recent` 支（不带 `sort` 走「待核验置顶」的全表支，一个字未改）。

`INTERNAL_USERS_SQL` 提为包内常量（契约测试拿它拼期望串）。

### 3.2 全仓 `.or()` 扫描结论

命令：`grep -rn "\.or(" code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java`（并把范围放宽到整个
`code/RuoYi-Vue-Plus` 的 Java 主源码，`--include=*.java`，排除 `src/test`）。

| # | 位置 | 形态 | 是否同类问题 |
|---|---|---|---|
| 1 | `SampleQueryService:175-178`（keyword：内部编号等值 OR 来源单位模糊） | `.and(StringUtils.isNotBlank(keyword), w -> w.eq(...).or().like(...))` | **否**（在 `and(consumer)` 里，自带括号） |
| 2 | `SampleQueryService:188-190`（经手人 ∈ 内部） | 修前：`wrapper.inSql(createBy,…).or().inSql(updateBy,…)`（顶层） | **是 —— 本 S1 的病灶，已修** |
| 3 | `SampleQueryService:194`（mine：create_by 我 OR update_by 我） | `.and(w -> w.eq(...).or().eq(...))` | **形态对，但修前它 AND 在第二个析取项上** → 即 #105 的第二形态（`mine` 静默失效），随 2 一并修好 |
| 4 | `ruoyi-system` `SysMenuServiceImpl:381`（框架自带：path 或 routeName） | `.and(w -> w.eq(path).or().eq(routeName))` | **否** |
| 5 | `ruoyi-system` `SysUserServiceImpl:141`（框架自带：角色不等或为空） | `.and(w -> w.ne(...).or().isNull(...))` | **否** |

补充核查（同一「OR 越界」类的其它可能形态）：
- **lqg 模块内 `.or(` 只有上表 1/2/3 三处**，其余筛选全是 AND 链上的普通条件；
- 外部侧 `onlyMine`（`ExtSampleAssemblyService`）走的是 `in(可见 id 集合)` + `eq(onlyMine && userId != null, submitterId, userId)`，**没有 OR 形态**，不存在同类问题；
- XML mapper 不在这条链上：全仓**没有任何碰 `t_lqg_*` 的 XML mapper**（`grep -rln "t_lqg" --include=*.xml` 空；
  `ruoyi-lqg` 连 `src/main/resources` 都没有）→ lqg 的筛选全在 Java wrapper 里，`.or(` 的扫描覆盖是完整的；
- `MpSampleService` / `ExtSampleSubmitService` 等写路径不拼 wrapper。

> 结论：**全仓同类问题只有 #105 这一处**（外加它自己带出来的 `mine` 形态）。已修，且新增契约测试把这形态钉死（§3.4）。

### 3.3 类目身份（`sample_kind`）不可越类改 —— 两头都收

口径判定依据：
- `doc/authority/` 的 **`FIELD:t_lqg_sample.sample_kind`**：「tissue 组织样本（样本记录信息表）/ organoid 类器官（类器官收样记录）」
  —— 它决定这条记录属于哪张工作表、哪套必填集（`SampleKindRules`）；
- `doc/authority/flows.yaml`：`FLOW:F-SAMPLE-01.step1` / `FLOW:F-SAMPLE-02.step1|step2` 的 `writes` 里有 `sample_kind`
  （**创建当时**由入口定下），**修改路径的 `writes` 里没有它**；
- `doc/api-contract.md` 第 49 行：`PUT /mp/int/sample` 的不可改字段原先只列 `submitNo / submitSource / submitterId`。
  本次返工把 **`sampleKind` 一并列进不可改**（同一行最小改动：`有效样本除身份字段外全部可改（submitNo / submitSource / submitterId / sampleKind 不可改；sampleKind 与库里不一致 → 400，issue #105）`）。

**前端（防发生）**
- `code/miniapp/src/pages/organoid/api.ts`：拆成两个具名函数 ——
  `internalOrganoidPayload(form)`（新增 POST，带 `sampleKind:'organoid'`，内部就是
  `{ sampleKind, ...internalOrganoidPatch(form) }`）与 `internalOrganoidPatch(form)`（修改 PUT，**不带** `sampleKind`）。
  不用「加个 `withKind` 布尔开关」是因为开关忘传就又会静默改判，两个具名调用点一眼看得出走哪条口径。
- `code/miniapp/src/pages/organoid/form.vue`：修改模式改调 `internalOrganoidPatch`。
- `code/miniapp/src/pages/sample/form.vue`：`payload()` 里的 `sampleKind:'tissue'` 只在 `mode === 'new'` 时补。

**后端（防扩散）**
- `MpSampleService.update` 在状态闸之后加 `assertKindUnchanged(exists, bo)`：
  补丁里 `sampleKind` 非空且与库里不一致 → **400**（`"样本类别不可修改（当前 tissue，请求 organoid）…"`）；
  不传 / 空 / 只有空白 = 沿用库里现值，放行。
- 选「拒绝」而不是「静默忽略」：忽略只是把「改判」换成「别类字段写进了这条记录」，两种都不可见；
  拒绝让前端与 QA 当场看到 400。作用域限定在小程序口（`/mp/int/sample`），网页工作台的
  `PUT /lqg/sample` 未动（见 WARN-1）。

### 3.4 契约测试（新增，含「反同义反复」自检）

- 新增 `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/service/SampleRecentFilterContractTest.java`（6 例）
  —— 拿 `buildWrapper(...).getTargetSql()` **真生成的 SQL 片段**做结构断言：
  「组合筛选下 OR 不越界」（`sample_kind = ? AND (经手人一组)`）、
  「`mine=true` 真的收窄」（`(经手人一组) AND (create_by = ? OR update_by = ?)`，且等于基线 + 一组 AND）、
  「不带 sort=recent 没有经手人条件」、「取不到登录人不造「我」」，
  外加**判据自检**：修前/修后两句真实 p6spy SQL 走同一判据，修前必须判否、修后必须判是。
- `MpSampleContractTest` 追加 2 例：`sampleKind` 与库里不一致 → 400（且换个方向同样拒）；同类别 / 不传 / 空白 → 放行。
- `code/miniapp/src/pages/organoid/api.spec.ts`（新增 4 例）：新增带 kind、修改不带 kind、
  两个表单的调用点与源码形态（修改模式不许出现无条件 `sampleKind`）。

## 4 修前 / 修后对照

### 4.1 最小坐实复现（reseed → staff 改 1001 → 四条）

命令与原始输出见 `.tmp/rework-r3/postfix-repro.log`（修后）与 `.tmp/rework-r3/prefix-*.log`（修前）。

| # | 请求（`--as staff`） | 修前 | 修后 | 期望 |
|---|---|---|---|---|
| 1 | `list?sort=recent&sampleKind=organoid` | `total=2 [1001(tissue), 1009(organoid)]` | `total=1 [1009(organoid)]` | 只 organoid ✅ |
| 2 | `list?sort=recent&sampleKind=bogus` | `total=1 [1001]` | `total=0 []` | 空 ✅ |
| 3 | `list?sort=recent&sourceUnitId=9000009002` | `total=2 [1001, 1009]` | `total=1 [1009]` | 只 9000009002 ✅ |
| 4 | `list?sort=recent&internalNo=T-oco01` | `total=2 [1001, 1009]` | `total=1 [1009]` | 只 T-oco01 ✅ |
| 4b | `list?sort=recent&internalNo=T-hli01` | — | `total=1 [1001(tissue)]` | 只 T-hli01 ✅ |
| 4c | `list?sort=recent&sampleKind=organoid&mine=true` | `[1001, 1009]` | `[1009]` | 只 1009 ✅ |
| 5 | `list?sort=recent`（对照：全中心） | `total=4`（含 admin 新行） | `total=4 [admin行, 1001, 1008, 1009]` | 全中心含 admin 行 ✅ |
| 6 | `list?sort=recent&mine=true`（★ 开关收窄） | `total=4`（**含 admin 建、staff 没碰过的行**） | `total=3 [1001, 1008, 1009]`（admin 行消失） | 不含它 ✅ |

### 4.2 修后 p6spy 原始 SQL（`.tmp/rework-r3-backend-postfix.log`）

```
-- ?sort=recent&sampleKind=organoid            （经手人那一组被括起来、与 sample_kind 相与）
WHERE del_flag='0' AND (sample_kind = 'organoid'
  AND (create_by IN (内部) OR update_by IN (内部)))
ORDER BY COALESCE(update_time, create_time) DESC, id DESC LIMIT 100

-- ?sort=recent&mine=true                       （mine 与整组相与，不再挂在第二个析取项上）
WHERE del_flag='0' AND ((create_by IN (内部) OR update_by IN (内部))
  AND (create_by = 9000000101 OR update_by = 9000000101))
ORDER BY COALESCE(update_time, create_time) DESC, id DESC LIMIT 100

-- ?sort=recent&sourceUnitId=9000009002 / internalNo=T-oco01 / T-hli01 同形状
WHERE del_flag='0' AND (source_unit_id = 9000009002 AND (create_by IN (内部) OR update_by IN (内部)))
WHERE del_flag='0' AND (internal_no = 'T-oco01'  AND (create_by IN (内部) OR update_by IN (内部)))
WHERE del_flag='0' AND (internal_no = 'T-hli01'  AND (create_by IN (内部) OR update_by IN (内部)))
```

### 4.3 两页签硬断言 `L2r3-mp-tabs.mjs`（QA 实测 4/7）

| 断言 | 修前（本轮复跑，状态 = reseed + staff 改 1001） | 修后 | 对应修改 |
|---|---|---|---|
| R3T-01 两个页签存在 | PASS | PASS | — |
| R3T-02 样本页签逐行 tissue | PASS（`T-hli01, T-hli05`） | PASS（同） | — |
| R3T-03 类器官页签逐行 organoid | **FAIL**（`T-hli01(tissue), T-oco01`） | **PASS**（`T-oco01`） | §3.1 经手人 OR 包组 |
| R3T-04 两页签行集合不相交 | **FAIL**（相交 `T-hli01`） | **PASS**（`inter=[]`） | 同上 |
| R3T-05 类器官页签点行 → organoid 表单 | PASS 但 URL =`organoid/form?id=9000001001`（**一条 tissue 行！危害路径实况**） | PASS 且 URL =`organoid/form?id=9000001009`（真 organoid 行） | 同上（行集合对了，点行才对） |
| R3T-06 样本页签点行 → sample 表单 | PASS（`sample/form?id=9000001001`） | PASS（同） | — |
| R3T-07 无未捕获前端异常 | PASS | PASS | — |
| **合计** | **5/7**（QA 原始 4/7；差异只是库状态里被改过的行数不同，红的两条完全一样） | **7/7** | |

日志：`.tmp/rework-r3/prefix-tabs.log` / `.tmp/rework-r3/postfix-tabs.log`；
截图（已随提交更新）：`doc/waves/regression/D2/shots/L2r3-mp/t-03-tab-organoid.png` 等。

### 4.4 三个 L2 回归脚本（本轮要满足的回归面）

| 脚本 | 修前 | 修后 | 日志 |
|---|---|---|---|
| `L2r2-mp.mjs` | 39/39 | **39/39** | `.tmp/rework-r3/prefix-mp.log` / `postfix-mp.log` |
| `L2r2-mp-extra.mjs` | 6/6 | **6/6** | `.tmp/rework-r3/prefix-mp-extra.log` / `postfix-mp-extra.log` |
| `L2r2-web.mjs` | 28/28 | **28/28** | `.tmp/rework-r3/prefix-web.log` / `postfix-web.log` |

> `mp-extra` 的正确跑法是 **`reseed → mp → mp-extra`（中间不 reseed）**：它的 R2MX-03 依赖 `mp` 留下的
> `T-r2org1`（不在 seed 里）。我第一次分开跑（中间 reseed）时它红在 `SELECT organoid_type ... WHERE id=`
> —— 是跑法坑，不是产品问题（见 §6 坑 4）。

### 4.5 六个受影响 accept（原文，仅去掉沙箱恒非 0 的 `--fresh-module`）

脚本由 ticket frontmatter 的 `run:` 块**程序化抽取**（`.tmp/rework-r3/accepts/*.sh`），只做了一处替换
`--fresh-module ruoyi-lqg` → 删除（沙箱里该守卫恒 exit 2，按要求自己等价核实 jar 已含本次修改）。

| accept | form | 结果 | 日志 |
|---|---|---|---|
| `SAMPLE-MP-001` accept 1（布局 fixture + 禁字 grep） | STATE | ✅ GREEN | `accepts/SAMPLE-MP-001-acc1.log` |
| `SAMPLE-MP-001` accept 2（构建 + 内部接口口径） | API | ✅ GREEN | `accepts/SAMPLE-MP-001-acc2.log` |
| `SAMPLE-MP-002` accept 1（类器官工作表 `sort=recent`/修改改得动） | DATA | ✅ GREEN | `accepts/SAMPLE-MP-002-acc1.log` |
| `SAMPLE-MP-002` accept 3（类器官表单布局 fixture） | STATE | ✅ GREEN | `accepts/SAMPLE-MP-002-acc3.log` |
| `SAMPLE-WEB-001` accept 1（工作台筛选逐项） | API | ✅ GREEN | `accepts/SAMPLE-WEB-001-acc1.log` |
| `SAMPLE-WEB-001` accept 2（菜单 / 组件 / 禁下拉） | MENU | ✅ GREEN | `accepts/SAMPLE-WEB-001-acc2.log` |
| **合计** | | **6/6 GREEN**（`ACCEPTS FAIL=0`） | |

### 4.6 「类目身份两头收」的正反验证（新探针 `.tmp/rework-r3/probe-crosskind-form.mjs`）

危害路径 = 把**组织样本 9000001001** 用**类器官表单的修改模式**打开（等价于修前「从类器官页签点错行进来」），
填字段后点保存：

| 组合 | 库侧结果 | 结论 |
|---|---|---|
| 修后前端 + 修后后端 | `update_by=9000000101`（PUT 落库），`sample_kind` 仍 `tissue`，p6spy：`UPDATE t_lqg_sample SET … sample_kind='tissue' …` | 前端没发 `sampleKind`，类别未被改判 ✅ |
| **旧前端**（临时把 form.vue 还原成 `internalOrganoidPayload`）+ 修后后端 | `update_by=NULL`（PUT 被拒，库里一个字没动），后端日志：`样本类别不可修改（当前 tissue，请求 organoid）：类别是这条记录的身份，要换类别请在工作台按对应工作表新增` | 后端兜底真的挡得住「别的入口/别的客户端带错 kind」 ✅ |
| 直接 API：`PUT {"id":1001,"sampleKind":"organoid"}` | `400 样本类别不可修改（当前 tissue，请求 organoid）`，库里 `tissue|-` 不变 | §4.1 第 7 条 |
| 直接 API：`PUT {"id":1001,"sampleKind":"tissue"}` / 不传 kind | `200`，`tissue|肝组织（同类别可改）` | 正常保存不受影响 ✅ |

### 4.7 构建与单测

| 项 | 命令 | 结果 |
|---|---|---|
| 后端单测（含新增合同测试） | `mvn -o -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome -pl ruoyi-modules/ruoyi-lqg -am test` | `BUILD SUCCESS`；ruoyi-lqg **Tests run: 64, Failures: 0, Errors: 0, Skipped: 0**（修前 56 → 新增 8：`SampleRecentFilterContractTest` 6 + `MpSampleContractTest` 2） |
| 后端 jar（供 8081 起服） | `mvn … -pl ruoyi-admin -am -Dmaven.test.skip=true package` | `BUILD SUCCESS`；`ruoyi-admin/target/ruoyi-admin.jar` |
| plus-ui | `pnpm build:prod` | ✅ `✓ 3112 modules transformed. ✓ built in 8.06s` |
| miniapp | `rm -rf dist/build/mp-weixin && pnpm build:mp-weixin` | ✅ exit 0，`dist/build/mp-weixin/pages/{sample,organoid}/form.js`、`history/index.js` 均在 |
| 新增 miniapp 单测 | `pnpm vitest run src/pages/organoid/api.spec.ts` | `4 passed` |

## 5 改动文件

| 文件 | 改了什么 |
|---|---|
| `code/RuoYi-Vue-Plus/…/sample/service/SampleQueryService.java` | 抽出包内可见 `buildWrapper`；`INTERNAL_USERS_SQL` 提为常量；经手人 OR 与 mine 双双 `and(w -> …)` 包组；注释写清 p6spy 形态与 issue #105 |
| `code/RuoYi-Vue-Plus/…/sample/mp/MpSampleService.java` | `update` 加 `assertKindUnchanged`（不一致 → 400）；注释写清口径来源（FIELD 锚 + flows + 契约第 49 行）与「为什么选拒绝」 |
| `code/RuoYi-Vue-Plus/…/test/.../sample/service/SampleRecentFilterContractTest.java` | **新增** 6 例（OR 包组 / mine 收窄 / 判据自检） |
| `code/RuoYi-Vue-Plus/…/test/.../sample/mp/MpSampleContractTest.java` | **追加** 2 例（kind 不一致 400、同类别/不传放行） |
| `code/miniapp/src/pages/organoid/api.ts` | 新增 `internalOrganoidPatch`（修改不带 kind）；`internalOrganoidPayload` 只给新增 |
| `code/miniapp/src/pages/organoid/form.vue` | 修改模式改调 `internalOrganoidPatch` |
| `code/miniapp/src/pages/sample/form.vue` | `sampleKind` 只在 `mode==='new'` 时发 |
| `code/miniapp/src/pages/organoid/api.spec.ts` | **新增** 4 例（含两个表单的源码形态断言） |
| `doc/api-contract.md` 第 49 行 | `PUT` 不可改字段补 `sampleKind`（口径同步，见 WARN-4） |
| `doc/waves/regression/D2/shots/L2r3-mp/*.png` | 复跑更新的两页签证据截图 |

## 6 坑与解法

1. **「数括号深度找顶层 OR」这条判据是错的**：MyBatis-Plus 3.5.16 的 `getCustomSqlSegment()` 会把整段表达式
   再套一层括号（修前也有），病灶那个 `OR` 因此也在深度 1 —— 深度判据对它恒绿。改成找**里层那一组**
   （`AND (create_by IN (…) OR update_by IN (…))`，或它是唯一条件时的 `((…))`），并用修前/修后两句真实
   p6spy SQL 做「判据自检」，防止写出同义反复的测试。（初稿就踩了，测试反而没牙。）
2. **不启 Spring 的单测里 `Sample::getXxx` 会抛** `MybatisPlus can not find lambda cache for this entity`
   —— `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Sample.class)`
   手工注册一次即可（等价启动时的实体扫描），不用为测试起容器。
3. **`-DskipTests` 对 `ruoyi-lqg` 无效**：该模块 pom 里把 `<skipTests>false</skipTests>` 写死（覆盖父 pom 的
   `true`）。想跳过测试出 jar 要用 `-Dmaven.test.skip=true`；想跑测试就直接 `-pl … -am test`
   （本次两条都跑了：出 jar 用前者，测试证据用后者）。
4. **`L2r2-mp-extra` 不能单独跑**：R2MX-03 依赖 `L2r2-mp.mjs` 建出来的 `T-r2org1`（不在 seed 里），
   两个脚本之间 reseed 会让 SQL 变成 `WHERE id=` 直接红。正确链 = `reseed → mp → mp-extra`。
5. **`mine` 的 `.and(...)` 必须写在「经手人」那一组的后面**：嵌套本身只保证「自己带括号」，
   顺序错（写在包组之前）仍然会 AND 到错的层级上；两处都要包、且顺序固定，才同时治好「筛选串味」与「开关失效」。

## 7 WARN 清单

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| 1 | S3 | scope-gap（口径未收） | 网页工作台 `PUT /lqg/sample` 仍可改 `sample_kind` | 本次只收了小程序口（ticket 授权范围）。admin 在工作台抽屉里把一条 tissue 改成 organoid（或反之）不会被拒 —— 若「改判类别」在工作台是合法动作则现状正确，若不是则需一条 CR 明确，再在 `SampleService.update` 加同一道闸。**留台账，未动**。 |
| 2 | S3 | 前端入口 | 表单仍可被「非页签」入口带错 kind 打开（只靠后端 400 兜底） | 页签修好后正常路径不会带错；但手改 URL / 其它入口仍能打开类别不符的表单，用户看到的是后端 400 文案，而不是「你点错类别了，去对应表单」。方案：详情已有 `sampleKind`，表单加载时比对预期 kind → 渲染错误态或直接跳对应表单。 |
| 3 | S3 | 跨类字段残留 | 类别不同的表单保存到该类记录时，别类的**类型字段**仍会被写进去 | 探针实测：类器官表单保存到 tissue 行，`sample_kind` 保住 tissue，但 `organoid_type` 若填了会写进这条 tissue 行（本次探针因必填校验没填上，故为 `-`）。任务只授权收「类目身份」，故未拦。方案：`MpSampleService.mergePatch` 按 `exists.sampleKind` 丢掉别类字段（或 400）。 |
| 4 | S3 | doc-drift 风险 | 本次返工改了 `doc/api-contract.md` 第 49 行 | 把 `sampleKind` 加进 PUT 的「不可改」列表。权威文本变更需调度/人工确认；若 QA 以旧文本为准，会认为实现超出契约（实现是按 `FIELD:t_lqg_sample.sample_kind` + `flows.yaml` 的 `writes` 判的）。**若不同意改契约，说一声我把那一行还原、只留代码注释与报告。** |
| 5 | S3 | 预防（缺机器闸） | 全仓没有「顶层裸 `.or()`」的静态闸 | 本次靠人工 grep + 一条针对 `sort=recent` 的契约测试钉住。D3（石蜡包埋）/D4（冻存）在同一 registry 上再加页签与取数口时，同类形态可能复发。方案：加一条架构测试扫 lqg 模块源码（每个 `.or()` 必须落在 `and(` / `nested(` 的 lambda 里），或在每个新取数口复制本判据。 |
| 6 | S3 | 台账未碰（QA 已报） | QA r3 的三条非拦门项一条未动 | ① `/mp/int/sample/{id}` 对已软删/不存在回业务码 500、外部同情形回 404（「不存在」语义不一致）；② 实现方返工中改过的 WEB-L2-06b 探针被弱化；③ D1 归属 fixture 与实现同提交变更（QA 已核为规格驱动）。按「只修 #105」的范围留台账。 |

## 8 沙箱限制与未覆盖

- **微信开发者工具 / 真机未覆盖**（沙箱 EPERM + 需扫码）：小程序一律用 H5 dev(9200, `VITE_MOCK_LOGIN=1`)
  + Playwright 等价覆盖。
- **`api.sh --fresh-module` 恒非 0 退出**（沙箱），六个 accept 按原文跑、只去掉该守卫；
  已等价核实：8081 跑的是本次新构建的 jar（`ruoyi-admin.jar` 07:03:37 > 主源码最后修改 `SampleQueryService.java` 07:02:44；
  且修后 p6spy 打印出的 SQL 已是包组形态、复现/回归全绿）。
- 8080 / 5432 / 6379（Kevin 本机服务）全程未碰；容器 `lqg-dev-postgres/redis/minio` 未停。
