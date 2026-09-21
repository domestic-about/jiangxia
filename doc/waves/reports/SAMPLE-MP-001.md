# SAMPLE-MP-001 · 完工报告

- **ticket**：SAMPLE-MP-001（track SAMPLE / phase D2 / size L）—— D2 第四张，小程序侧最大的一张
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑；唯一差异 = 去掉本沙箱恒 exit 2 的 `--fresh-module`，等价证据见 §4.0）
  - accept 1 · STATE：✅（vitest 22 用例全绿、fixture 结构断言的 6 个数逐项相等、8 段 grep 全过）
  - accept 2 · API：✅（构建段 3 个产物断言 + 15 段接口/库侧链，逐段真实输出见 §4.2 / §4.3）
- **分支**：`task/D2`，未切分支 / 未 push / 未 merge
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 23:18:30` 左右，后端进程 8081（dev + `--api-decrypt.enabled=false`）
- **迁移**：**没有加 Flyway**（小程序接口不挂菜单、无新表；最大版本仍是 `V202609221001`）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**}`、`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/api-contract.md`
- **没碰**：8080（Kevin 的 java 服务）/ 5432 / 6379；关进程一律按 PID（`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`），**没用过 `pkill -f`**
- **产物**：后端 3 个新类 + 4 个既有类的小改（+190 行）；小程序 5 个新页面/模块 + 10 个新组件 + 3 个既有文件的小改（+676 行）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D2`；未切分支 |
| `depends_on` 全部 done 且产物在盘上 | ✅ PASS | `doc/waves/state.json`：`AUTH-EXT-001` / `SYS-MP-001` 都 `done`；两者的产物（`/mp/ext/**`、`src/pages/index/entries.ts`、`src/style/*`）都在盘 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | 顶部四条里两条直接相关：**CR-20260918-07**（内部历史记录改中心全员 + 「只看我提交的」开关 + 经手人列；外部详情两个字段由开关决定）、**CR-20260917-05**（历史编辑记录取代「我的送检」）—— 逐条落进实现，见 §1 |
| 8 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:mp.sample.form` / `UI:mp.history` / `UI:mp.sample.detail.ext` / `UI:mp.sample.list` / `FLOW:F-MP-01.step2` / `FLOW:F-SAMPLE-01.step1,step4` / `FLOW:F-SAMPLE-02.step1` / `ADR-0004` 全部 `active`；口径落点见 §1 |
| 逐字读 `doc/verify/fixtures/sample-form-cases.json` | ✅ PASS | 13 例 / `sendFields` 8 / `receiveFields` 7，**一个字节没改**（`git status` 里它没出现）；spec 的期望值全部从它读 |
| 视觉按方向 A（CR-20260921-08） | ✅ PASS | 只用 `src/style/components.scss` 的 `.lqg-*` 类与 `wd-*`；`grep` 色值字面量 = 空；图廊帧只取内容块与排布（`#mp-history(-int)`、`#mp-form-a`、`#mp-detail-ext`） |
| 动手前代码是绿的 | ✅ PASS | 改动前后端 `Tests run: 39`；本票后 `Tests run: 46`（新增 7 例 `MpSampleContractTest`），0 失败 |
| 环境可用（8081 / PG 5433 / Redis 6380） | ✅ PASS | 三个容器全程在跑，未停；8081 用 `.tmp/run-backend.sh` 起 |

**STOP 判定：无。**

---

## §1 口径复述（ticket §0 点名的 8 点，逐条对 accept 核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **外部看不到收样段是「不渲染」不是置灰** | `layout.ts` 的 `formLayout` 给外部**只返回送检段**（`fields` 里根本没有收样段的键） | fixture「外部新增」「外部改自己的待核验」等 6 例的 `fields` 逐字相等；`layout.fixture.spec.ts` 另有一条「外部任何一例都不含收样段字段」的守卫；`detail-ext.vue` 的禁字 grep（accept 1） |
| 2 | **`editable` 以后端详情为准** | `formLayout` 出「结构结论」，页面用 `resolveEditable(layout, detail.editable)` 合成：**模式是硬闸**（`view` 恒只读）**且**详情的 `editable` 为真才可写 | fixture「外部看同组别人的待核验」`editable=false`；spec 有「详情说不可改就不可改」一例；端侧实测 view 模式保存按钮 = 0（§3 `11-int-form-view`） |
| 3 | **三种模式由入口决定；`mode` 缺失/不认识按只读** | `normalizeMode` 只认 `new`/`edit`/`view`，其余 → `view`；`formLayout` 里 `view` 分支恒 `editable:false` | fixture「入口模式缺失」一例 `editable=false`；spec 的「mode 缺失或不认识按只读」一例（含 `EDIT` 大写） |
| 4 | **内部修改模式不限本人录的；待核验/无效只读 + 顶部提示** | `formLayout` 内部 `edit` 分支 = `verifyStatus==='valid'`；后端 `MpSampleService.update` 先读状态，非 valid → 400；页面出 `.lqg-note--warn`「核验与改判请到网页工作台」 | accept 2 段 9（改别人录的 1004 → 200）、段 13（改待核验 1002 → 400/500 且库里 0 行）；fixture 两条内部只读病灶例 |
| 5 | **历史编辑记录 ≠ 逐次修改日志；内部默认全中心** | `sources.ts` 内部那条 `fetch`：`sort=recent` **不带** `mine`；开关打开才**另外**带 `mine=true`；后端 `sort=recent` 用 `create_by/update_by IN (sys_user.user_type='sys_user')` 挡「没人经手过的」 | accept 2 段 3-4（2 条）、段 6（外部自己改的 1002 不进）、段 10-12（改完立刻排最前、`mine=true` 四条）；段 11 是**直连库独立数**，与接口两侧同源 |
| 6 | **页签清单直接用 `entriesFor(identity)`，旧「我的送检」页作废** | `pages/history/index.vue` 的 `tabs = entriesFor(store.identity)`（不另写一份）；`src/pages/sample/mine.vue` 不存在 | accept 1 的两段 grep（`entriesFor` + `! test -e mine.vue`）；端侧实测内部 4 个页签、外部 3 个 |
| 7 | **列表供体姓名用 `donorNameMasked`，详情才有全名** | 外部行摘要用 `row.donorNameMasked`（后端给）；详情页渲染 `donorName` | `api/sample.ts` 的类型与 `sources.ts` 的 `toRow`；后端 AUTH-EXT-001 已保证形状（本票不重做） |
| 8 | **外部详情的内部编号照接口给的渲染；冻存与核验人全页不出现** | `detail-ext.vue`：`v-if="has(internalNo)"` 一行；模板里**没有**任何收样段其余字段 / 核验人 / 冻存字段名 | accept 1 的三段 grep（禁字为空、`internalNo` 在、**不含** `lqg.ext.show-internal-no` 这个串）；开关打开后实测该行出现且值 = `T-hli01`（§3 `04b`） |

---

## §2 改了哪些文件

### 2.1 后端 · 新增（`org.dromara.lqg.sample.mp` 包）

| 文件 | 职责 |
|---|---|
| `sample/mp/MpSampleController.java` | `/mp/int/sample` 的四个端点（list / {id} / POST / PUT），**类级 `@SaCheckRole("lqg_internal")`**（契约第 19 行） |
| `sample/mp/MpSampleService.java` | 读写编排：读走 `SampleQueryService`、写走 `SampleService`；`update` 先读状态（非 valid → 400）再把**部分字段补丁**合并成完整入参（`mergePatch`） |
| `sample/mp/MpSampleQueryBo.java` | 内部侧的筛选 BO（extends `SampleQueryBo`）；`keyword` 刻意不声明（表格页搜索是 SAMPLE-MP-002 的活） |
| `src/test/java/.../sample/mp/MpSampleContractTest.java` | **7 个用例**（见 §4.4）：类级角色与路径、`PUT` 是扁平形状、`isEditable` 只看状态、`mine` = create_by 或 update_by、`sort=recent` 只认字面量、`mine` 默认 null、`mergePatch` 不清没传的加密列 |

### 2.2 后端 · 修改

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `sample/domain/vo/SampleVo.java` | +`handlerName` / +`mine` / +`editable` | 契约第 49 行给小程序内部行定的三个键；`editable` 给前端「以后端详情为准」用 |
| `sample/service/SampleQueryService.java` | `list()` 支持 `sort=recent`（范围 + `COALESCE(update_time, create_time) DESC` 排序）与 `mine=true`；`toVo()` 补 `handlerName` / `mine` / `editable`；新增 `entity(id)`（读出即解密的实体，给补丁合并用）；新增 `isHandledBy` / `isEditable` / `currentUserId` 三个静态判据 | 读路径只有一条：小程序与工作台共用同一份筛选与判据 |
| `sample/domain/bo/SampleQueryBo.java` | +`sort` / +`mine` 两个字段 + `isRecentSort` | 契约第 49 行的两个参数；与工作台那五个筛选挂同一个 BO（工作台不带它们，行为一字不变） |
| `sample/service/SampleNameResolver.java` | **删掉**「`updateTime` 为空时兜底成 `createTime`」那一段 | ★ 见 §5 坑 1：那个兜底把「从没被改过」这个信息抹掉了，小程序的「新增 / 修改」正是靠 `updateTime` 空不空 |

**接口清单（本票新增，全部 `@SaCheckRole("lqg_internal")`）**

```
GET  /mp/int/sample/list?sampleKind=&sort=recent[&mine=true]   内部管理表格页（不带两个参数）/ 历史编辑记录（带 sort=recent）
GET  /mp/int/sample/{id}                                       详情（带 updateByName / updateTime / handlerName / mine / editable）
POST /mp/int/sample                                            首页点表新增（internal + valid，提交人 = 核验人 = 当前用户）
PUT  /mp/int/sample                                            修改（valid 谁录的都能改；待核验 / 无效 → 400）
```

**契约同步（ticket §2 要求 raise 的那一句）**：`doc/api-contract.md` **第 49 行已经把本票的接口形状写全了** —— `sort=recent` 的语义与范围、`mine=true` 的收窄口径、行上的 `handlerName` / `mine`、详情的 `updateByName` / `updateTime`、`PUT` 的「有效样本全部字段可改 / 待核验与无效 → 400」都在里面。调度侧后来又说明「契约已在位，不需要改」——**本票没有动 `doc/api-contract.md`**，实现与它逐条对齐（无 doc-drift）。

### 2.3 小程序 · 新增

| 文件 | 职责 |
|---|---|
| `src/pages/sample/layout.ts` | **纯函数** `formLayout(identity, verifyStatus, mine, mode)` + `resolveEditable` / `normalizeMode` / `fieldSpecs` / `hasReceiveGroup`（fixture 的被测函数） |
| `src/pages/sample/layout.fixture.spec.ts` | fixture 驱动 **22 用例**（13 例逐例 + 3 条结构守卫 + 6 条口径守卫），期望值全部从 fixture 读 |
| `src/pages/sample/form.vue` | 填写页三种模式（`new` / `edit` / `view`）：送检信息 + 收样信息两组、识别条插槽、日期走底部弹框、底部固定栏（保存 / 提交 + 两个置灰小链接） |
| `src/pages/sample/detail-ext.vue` | 外部样本详情三段；无效红条 + 条件出现的「修改后重新提交」；内部编号照接口给的渲染 |
| `src/pages/history/index.vue` | 历史编辑记录：页签 = `entriesFor(identity)`、「只看我提交的」开关（内外部共用）、行卡片、点行路由 |
| `src/pages/history/sources.ts` | 页签数据源注册表（本张只登记 `sample`）；内部/外部两套取数、行摘要、点行去哪 |
| `src/api/sample.ts` | 内外部两套接口 + 行摘要纯函数（`handlerLabel` / `ownerLabel` / `rowActionLabel` / `summaryLabel` / `toFormValue`） |
| `src/components/lqg/{FieldRow,SegButtons,StatusChip,SampleCard,NoteBar,EmptyState,LoadingState,ErrorState}.vue` | 公共组件（**全部直接 `.vue` 路径导入**，无桶口运行时导入） |
| `scripts/shots-sample-mp001.mjs` | 端侧截图脚本（H5 dev + Playwright，真实调后端取 token，不读图） |

### 2.4 小程序 · 修改

| 文件 | 改动 |
|---|---|
| `src/utils/request.ts` | +`raw` 选项：分页接口（`TableDataInfo`）的形状是 `{code,msg,rows,total}`、**没有 `data` 键**，走默认那条路会 resolve 出 `undefined`（实测踩过，见 §5 坑 3） |
| `src/pages.json` / `src/types/uni-pages.d.ts` / `src/types/components.d.ts` | 三个**生成产物**，由新页面/新组件自动重生成（`page` 增加 `pages/sample/detail-ext`）。与 SYS-MP-001 / AUTH-GROUP-001 的处理一致，**一并提交**（只 `git checkout` 会丢掉新页面的路由） |
| 我的页 | **一个字节没改**：`ME_TARGET.history` 本来就指向 `/pages/history/index`（SYS-MP-001 预留） |

**Flyway / 取号**：**本票不新增迁移**（小程序接口无菜单；`lqg.ext.show-internal-no` 那行是 SYS-WEB-001 的 `V202609210830` 插的，本票只读它）。

**越出 `touches` 的改动（逐条列清）**：

| # | 文件 | 为什么必须改 |
|---|---|---|
| 1 | `sample/domain/vo/SampleVo.java`、`sample/service/SampleQueryService.java`、`sample/domain/bo/SampleQueryBo.java`、`sample/service/SampleNameResolver.java` | 这四处都不在 ticket 的 `touches`（那里只列了 `sample/mp/**`），但**契约第 49 行的 `sort=recent`、`handlerName`、`mine`、`editable` 与「PUT 有效样本全部字段可改」没法只在 mp 包里实现**：读路径只有 `SampleQueryService.list` 一条，行 VO 只有 `SampleVo` 一个。四处都是**同模块内的既有一行方法 / 字段**（新增字段、新增分支、删一段错误的兜底），没有改任何既有方法签名。`SampleNameResolver` 那一处是**跨票行为变更**，单独记 WARN-4。 |
| 2 | `src/utils/request.ts` | 不在 `touches`（那里只列了 `src/api/sample.ts`）。分页接口 `{code,msg,rows,total}` 没有 `data` 键，不加 `raw` 选项两个 list 接口恒 resolve `undefined`（§5 坑 3）。改动是**新增一个可选选项**，默认行为一字不变。 |
| 3 | `src/pages.json`、`src/types/uni-pages.d.ts`、`src/types/components.d.ts` | 三个**生成产物**，由新页面 / 新组件自动重生成（`pages` 增加 `pages/sample/detail-ext`）。与 SYS-MP-001 / AUTH-GROUP-001 的处理一致，**一并提交** —— 只 `git checkout` 会丢掉新页面的路由。 |

**没有动任何只读区**（见报告抬头那份清单）。

---

## §3 视觉证据（截图清单）

微信开发者工具在本 agent 沙箱跑不通（CLI 要写 `~/Library/Application Support/微信开发者工具/**` → EPERM；首次要扫码）
→ 端侧证据用 **H5 dev server（`pnpm dev:h5 --port 9200`，带 `VITE_MOCK_LOGIN=1`）+ Playwright** 覆盖。
**开发者工具 / 真机未覆盖**（如实写明，不用 H5 冒充）。
截图在 `doc/waves/reports/SAMPLE-MP-001/`，**全程没有把任何 PNG 读进上下文**，断言只读 DOM 文本：

| 文件 | 覆盖项 | 实测断言输出 |
|---|---|---|
| `01-ext-history-off.png` | 外部历史编辑记录（开关关）：3 个页签、当前可改性 | 页签 `["样本记录信息表","类器官收样记录","石蜡包埋送样记录"]`；行 `["SJ00000043","SJ90000004","SJ90000001","SJ90000002","SJ90000003"]`（含同组陈医生的两条） |
| `02-ext-history-on.png` | 外部历史编辑记录（开关开） | 行 `["SJ90000001","SJ90000002","SJ90000003"]` —— 同组那两条被收窄掉（开关真的在起作用） |
| `03-ext-detail-invalid.png` | 外部详情（无效）：红条 + 重提按钮 | 红条 `["信息不全：缺住院号"]`、重提按钮数 `1` |
| `04-ext-detail-valid.png` | 外部详情（有效）：无重提按钮、**没有内部编号行**（开关默认关） | 重提按钮数 `0`、`getByText('内部编号')` 计数 `0` |
| `04b-ext-detail-internal-no-on.png` | 同页，**把系统参数打开后**：多出内部编号一行 | 计数 `1`、值 `["T-hli01"]`（★ 前端不读开关，只照接口给的渲染） |
| `05-ext-form-new.png` | 外部填写页（新增）：只有送检信息一组 + 识别条 | 分组 `["送检信息"]`、识别条 `1` —— **收样信息组连标题都没有** |
| `06-int-history-off.png` | 内部历史编辑记录（开关关 = 中心全员）：4 个页签、经手人列 | 页签 4 个；行 `["T-hli05","T-hli02","T-hli01"]`，经手人 `["我","张工","张工"]`、新增/修改 `["修改","修改","修改"]` |
| `07-int-history-on.png` | 内部历史编辑记录（开关开 = 只看我提交的） | 行 `["T-hli05"]`（张工经手的两条被收窄掉） |
| `08-int-form-new.png` | 内部填写页（新增）：**能看到收样信息一组** | 分组 `["送检信息","收样信息"]` |
| `09-int-form-edit-valid.png` | 内部修改模式（有效样本）：可改 + 顶部「最后修改」 | 保存按钮 `1`、顶部小字 `["最后修改：张工 · 2026-09-21 23:36:16"]` |
| `10-int-form-edit-pending.png` | 内部修改模式（待核验样本）：只读 + 顶部提示 | 保存按钮 `0`、提示 `["核验与改判请到网页工作台"]` |
| `11-int-form-view.png` | 内部只读模式（内部管理点一行） | 保存按钮 `0`、输入控件数 `0` |
| `12-int-form-nomode.png` | `mode` 缺失：按只读 | 保存按钮 `0` |

> 开关那一屏（`04b`）跑之前要先 `UPDATE sys_config SET config_value='true' …` **并且** `DELETE /system/config/refreshCache`（AUTH-EXT-001 坑 4：`sys_config` 的读带 Redis 缓存）；**跑完已还原成 `false`**（报告收尾时复核过：`lqg.ext.show-internal-no=false`）。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（与本沙箱的既有限制同源）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` → 原样带 `--fresh-module` **恒 exit 2**。
按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边（源码不得比 jar 新 + 进程不得早于 jar）：

```
jar mtime        : 2026-09-21 23:18:28（epoch 1790003908）
lqg src newer    : []     ← find ruoyi-modules/ruoyi-lqg/src -newer <jar> 为空
admin src newer  : []     ← 同上（ruoyi-admin/src 一个字节没改）
8081 PID         : 48311（`lsof -ti tcp:8081 -sTCP:LISTEN`，收尾时按它 kill）
该 PID 打开的文件 : 含 ruoyi-admin/target/ruoyi-admin.jar（`lsof -p <PID> | grep -c` = 2）
后端日志 mtime    : 晚于 jar（进程起于打包之后 = 没 stale）
嵌套 jar 复核     : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 sample/mp/ 下 4 个 class
                   （MpSampleController / MpSampleService / MpSampleQueryBo + 三个 Javadoc json）
8080（Kevin）     : 全程没碰
```

accept 里 `mvn … test` 那一行**原样跑在本机会挂**（`~/.m2` 只读），补 `-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome` 三个参数（**第 7+ 张命中的既有 WARN**，本票不重复计数）。
三条 runner 与脚本都在 `doc/waves/reports/SAMPLE-MP-001/accept-runners/`，长链**包进函数判整条 rc**（不丢给 `set -e`）。

### 4.1 accept 1 · STATE —— ✅

```
$ bash doc/waves/reports/SAMPLE-MP-001/accept-runners/mp001-acc1-state.sh
########## accept 1 · STATE（fixture + 各种 grep）##########
true
ACCEPT-1 GREEN (exit 0)

---- vitest 计数（accept 里 jq 断的就是它）----
{"numTotalTests":22,"numPassedTests":22,"numFailedTests":0}
---- fixture 结构（node 断言的输入）----
{"cases":13,"receiveFields":7,"internalReadonly":5,"viewEditable":0,"view":2,"unknownMode":true}
```

逐段（原文顺序）：

| 段 | 命令 | 结果 |
|---|---|---|
| 1 | `grep -q 'doc/verify/fixtures/sample-form-cases.json' src/pages/sample/layout.fixture.spec.ts` | 命中 |
| 2 | `! grep -nE '\.(skip\|todo\|only)\(' …` | 无命中 |
| 3 | `pnpm vitest run … --reporter=json --outputFile=/tmp/lqg-layout.json` | `numTotalTests=22`、`numFailedTests=0` |
| 4 | `jq -e '.numFailedTests == 0 and .numPassedTests >= 13'` | `true`（22 ≥ 13） |
| 5 | `node -e "… fixture 结构 …"` | 通过（6 个数：`13 / 7 / 5 / 0 / 2 / true`） |
| 6 | `! grep -nE 'receiveDate\|processTime\|hasQcSheet\|hasViabilityReport\|verifyByName\|[Cc]ryo' src/pages/sample/detail-ext.vue` | 无命中 |
| 7 | `grep -q 'internalNo' src/pages/sample/detail-ext.vue` | 命中（`v-if="has(internalNo)"` 那一行） |
| 8 | `! grep -nE 'lqg.ext.show-internal-no\|lqgExtShowInternalNo' …` | 无命中（**前端不读系统参数**） |
| 9 | `grep -q 'entriesFor' src/pages/history/index.vue && ! test -e src/pages/sample/mine.vue` | 成立 |
| 10 | `grep -rq '只看我提交的' src/pages/history && grep -rq 'handlerName' src/pages/history` | 成立 |
| 11 | `grep -q 'sort=recent' src/pages/history/sources.ts` | 命中 |

**counterfeit 逐条排掉**：

- 外部把收样段渲染出来再 `v-show=false` → `formLayout` 给外部的 `fields` 里带着 `receiveDate` 等 → fixture「外部新增」红（本票外部 6 例的 `fields` 都只有 8 个送检段键）。
- `editable` 写成 `identity==='external' && status!=='valid'`（漏了 `mine`）→「外部看同组别人的待核验」红（该例 `mine=false`、期望 `editable=false`）。
- 身份缺失时按内部渲染 →「身份缺失」例红（期望 `fields: []`）。
- 内部一律可编辑 →「内部看外部的待核验」「内部看无效」两条红（`editable=false`）。
- 只看状态不看 `mode` →「内部管理只读查看」两条红；`mode` 缺失默认可改 →「模式缺失」例红。
- 内部编号被写死「永不渲染」→ `grep internalNo` 红；把开关搬到前端 → 第 8 段 grep 红。
- 自写一份页签清单 / 留着 `mine.vue` → 第 9 段红。
- 内部页签写死 `mine=true` → 第 11 段（`sort=recent`）与 accept 2 段 3 红。

### 4.2 accept 2 · 构建段 —— ✅

```
$ bash doc/waves/reports/SAMPLE-MP-001/accept-runners/mp001-acc2-build.sh
ACCEPT-2-BUILD GREEN (exit 0)
dist/build/mp-weixin/pages/history/: index.js index.json index.wxml index.wxss sources.js
dist/build/mp-weixin/pages/sample/:  detail-ext.js detail-ext.json detail-ext.wxml detail-ext.wxss
                                     form.js form.json form.wxml form.wxss layout.js
```

（`rm -rf dist/build/mp-weixin` 先跑过 —— 上次剩下的 dist 骗不过去；`pages/sample/mine.js` 不存在。）

### 4.3 accept 2 · API 链（15 段）—— ✅

```
$ bash doc/waves/reports/SAMPLE-MP-001/accept-runners/mp001-acc2-api.sh

──── 0) reseed
──── 1) staff GET /mp/int/sample/list?pageSize=100 → 9 条 + 1001 的 internalNo
true
──── 2) extA GET /mp/int/sample/list → 403（角色闸，AUTH-EXT-001 那段从此收口）
──── 3) sort=recent → 只有中心内部人员经手过的两条，且 updateTime 为空 = 新增
true
──── 4) sort=recent&mine=true → 同上（本人）
true
──── 5) extA 改自己的待核验 1002（外部侧，用来验证「外部自己改的不算内部经手」）
true
──── 6) recent 里没有 1002
true
──── 7) staff PUT /mp/int/sample {"id":1001,"tissueType":…}（部分字段补丁）
true
──── 8) 库内：tissue_type 改了、verify_status 仍是 valid、update_by 记了人
肝组织（更正）|valid|yes
──── 9) staff PUT /mp/int/sample 改别人录的 1004（CR-20260918-07）
true
──── 10) recent 顺序：刚改的排最前，1001 行带经手人 / mine / updateTime
true
──── 11) 库侧独立数（create_by / update_by 是不是内部账号）—— 与接口两侧同源
9000001008
9000001009
9000001001
9000001004
──── 12) sort=recent&mine=true → 四条（本人都经手过）
true
──── 13) staff 改待核验的 1002 → 被拒（400/500）
──── 14) 库里没有被改进去
0
──── 15) 收尾 reseed

ACCEPT-2-API GREEN (exit 0)
```

**★ AUTH-EXT-001 遗留的那段收口了**：`--as extA --bizcode GET /mp/int/sample/list` 现在逐字返回
`403	没有访问权限，请联系管理员授权`（AUTH-EXT-001 报告 WARN-3 里那段「端点未注册 → 404」随之关闭；
那段断言在 D2 QA 门复跑时应变绿）。

**counterfeit 逐条排掉**：

- `sort=recent` 没做 / 还是按本人取数 → 段 3 的第一段集合是 `["9000001008","9000001009"]`（不是 9 条）→ 红；本票实测 2 条。
- `sort=recent` 只当排序用、不挡「没人经手过的」→ 段 6 会看到 1002（extA 自己改过的）→ 红；实测 `index("9000001002")==null`。
- 「经手过」写成 `update_by 非空` → 1002 混进段 6 → 红；写成 `verify_by` → 一开始就多出 1001/1003-1006 → 段 3 红；写成 `submitter_id` → 改完 1001 后它不出现 → 段 10 红。
- `mine` 被忽略 / 拿它兼当排序 → 段 4 与段 12 与段 11 的库侧独立数对不上 → 红。
- `handlerName` 取提交人 → 1001 会显示「王医生」→ 段 10 的 `["李工",true,true]` 红；`mine` / `updateTime` 不给 → 同一段红。
- 内部 PUT 加「只能改本人录的」→ 段 9（改 extB 录的 1004）红；不看状态 → 段 13/14 红（库里被改进去）。
- 内部 PUT 借用外部那条「改完回 pending」的 service → 段 8 的 `verify_status` 不是 `valid` → 红；没记 `update_by` → 段 8 末位不是 `yes` → 红。
- `/mp/int/**` 忘加角色注解 → 段 2 红（外部拿到 200，能看到全部样本连同内部编号）。

### 4.4 追加证据（accept 之外的机器证据）

**(a) Java 全模块测试：`Tests run: 46, Failures: 0, Errors: 0, Skipped: 0`**

```
ExtBindStateMachineContractTest   Tests run: 11
MockLoginGuardContractTest        Tests run:  4
StaffGrantRulesContractTest       Tests run:  6
ExtChokepointContractTest         Tests run:  4   ← ADR-0004 的四条不变量（本票没碰 ext 包）
SampleKindRulesContractTest       Tests run:  6
MpSampleContractTest              Tests run:  7   ← 本票
VerifyTransitionsContractTest     Tests run:  8
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
```

`MpSampleContractTest` 的 7 个用例：类级 `@RestController` + `/mp/int/sample` + `@SaCheckRole("lqg_internal")`；`PUT` 是扁平形状（1 个 `SampleSubmitBo` 参数、没有 `@PathVariable`）；`POST` 同形；`isEditable` 只看 `verify_status='valid'`（valid 可改 / pending 不可 / invalid 不可 / null 不可 / 没有行不可）；`mine` = `create_by` 或 `update_by`；`sort=recent` 只认 `recent`（大小写与空白不敏感、别的值不认）；`mine` 默认 `null`；`mergePatch` 不清没传的加密列。

**(b) 端侧断言（H5 + Playwright，DOM 文本）**：见 §3 的表格 —— 12 屏 + 开关那一屏的关键值都贴在里面。

---

## §5 坑与解法（给下游）

1. ★ **`updateTime` 是「这一行有没有被改过」的**唯一**判据，别拿 `updateByName` 当判据**：`SampleNameResolver.fill` 原来在 `updateTime` 为空时把它兜底成 `createTime`，于是「从没改过的行」在接口上看起来像改过 —— 小程序「新增 / 修改」正是看 `updateTime` 空不空（CR-20260918-07），accept 段 3 直接断 `updateTime == null`。**本票把那个兜底删了**（`update_by` 为空的行库里 `update_time` 本来就是 NULL）。凡是要在行上显示「新增 / 修改」「最后修改」的域（石蜡包埋 / 冻存 / 文档）都照这一条：**判据取 `update_time`，`updateByName` 只回答「谁」，不回答「改没改」**。
2. ★ **contract 的 `PUT` 收的是补丁，`SampleService.update` 是「整体替换」**：accept 的请求体只有 `{"id":…,"tissueType":…}`，直接透传给 `SampleService.update` 会把没传的字段（含两个加密列）清成 NULL。解法：`MpSampleService.mergePatch(exists, patch)` 先读现状（`SampleQueryService.entity` 读出即解密）再合并，**没传的沿用库里现值**。下游 MP 票（EMBED / CRYO）照这个形状做。
3. ★ **分页接口没有 `data` 键**：若依的 `TableDataInfo` 是 `{code,msg,rows,total}`，而 `utils/request.ts` 默认只 resolve `body.data` → 列表接口 resolve 出 `undefined`，页面显示「没能加载」（本票实测踩过，`/mp/int/sample/list` 明明 200）。解法：请求层加 `raw: true`（返回整个响应体），两个 list 接口都带它。
4. ★ **`<script setup>` 页面里必须自己拉一次 `/mp/me`**：`store.me` 只在「有人调过 `loadMe()`」之后才有值。首页/我的页会调，但**深链直接进历史编辑记录 / 填写页时没人调** —— 结果是页面渲染出来了、`identity` 还是 `undefined`、页签为空、**一个请求都没发**（本票实测踩过两次：一次是页签空，一次是表单直接显示「没能确认你的身份」）。解法：`load()` 开头 `if (!store.me) await store.loadMe()`。`detail-ext` 不依赖身份，不需要。
5. ★ **H5 深链的 `onShow` 不触发**：`onShow` 只在页面显示时（navigateTo / switchTab）触发，H5 首屏直接进这一页时它**不跑**。本票的历史页用 `onMounted + onShow` 双钩子 + `started` 标志去重。下游 MP 页若要在 H5 上出证据，照这个写法。
6. ★ **「组件里只用 easycom 引用 `wd-*`」会让该模块的 `.js` 产物消失**：本票第一版 `form.vue` / `history/index.vue` / `FieldRow.vue` 只写 `<wd-datetime-picker>` / `<wd-switch>` / `<wd-cell>` 等标签，构建**报 Build complete 但只 emit 了 `.json/.wxml`**，页面在真机/开发者工具上加载不到逻辑（`pages.json` 里有路由、文件也是 base64 名躺在产物根目录）。解法：**显式 `import WdXxx from 'wot-design-uni/components/wd-xxx/wd-xxx.vue'`**（与 SYS-MP-001 的「直接 .vue 路径导入」纪律同向）。加了显式导入后三个模块的 `.js` 都回来了。下游 MP 票在页面里用新 `wd-*` 组件时**先看 `dist/build/mp-weixin/pages/<页>.js` 在不在**。
7. **`@SaCheckRole` 不挡「方法不匹配」的请求**（AUTH-EXT-001 坑 3 同源）：`PUT /mp/int/sample` 是真实端点，所以角色断言有效；但如果有人写 `GET /mp/int/sample`（只有 POST/PUT），Spring 会在 handler mapping 阶段回 405、角色切面根本不跑。别在「不存在的 METHOD+PATH」上做角色断言。

---

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | counterfeit-risk | **「组件里只用 easycom 引用 `wd-*`」会静默丢掉该模块的 `.js` 产物**（构建仍然 `DONE  Build complete.`） | 见 §5 坑 6。本票三个模块（`pages/sample/form.vue`、`pages/history/index.vue`、`components/lqg/FieldRow.vue`）第一版都中招：产物里只有 `.json/.wxml`，`pages/sample/form.js` 与 `pages/history/index.js` **不存在** —— accept 2 的构建段正好会红（`test -f …/form.js`），但**如果哪张票的 accept 只 grep 产物里的字符串**（`.wxml` 里就有文案），它会**假绿**。方案：① ticket 生成器给 MP 票统一加「页面 `.js` 产物存在」的断言；② 或在 SYS-MP-001 的栈包 gotchas 里补一行「页面/组件一律显式 import `wd-*` 的 `.vue`，不要只靠 easycom」。**本票按 ② 落地，未改任何权威。** |
| WARN-2 | S3 | harness | 本沙箱的长进程会被中途杀掉：`nohup … &` + `disown` 起的后端在 3-4 分钟后收到 `SpringApplicationShutdownHook`（exit 143） | 本票撞了两次（23:27、23:31），表现为 H5 代理 `ECONNREFUSED 127.0.0.1:8081`、静态脚本第一段拿不到 token。解法：用 `bash` 工具的 `run_in_background: true`（受管 job）起后端，全程稳定。方案：给下游 ticket 的接口说明补一句「长进程走受管后台 job」。 |
| WARN-3 | S3 | doc-drift | `doc/api-contract.md` 第 49 行**已经**把 `sort=recent` / `mine=true` / `handlerName` / `mine` / 详情 `updateByName`+`updateTime` / PUT 语义写全；ticket §2 那句「api-contract 由调度侧同步」是**过时的** | 本票逐条对齐契约、**没有改契约**；调度侧后来也确认「契约已在位，不需要改」。方案：把 ticket §2 那句话从生成器模板里删掉（免得下游票再去改一份已经正确的契约）。 |
| WARN-4 | S3 | debt | **`SampleNameResolver` 的行为是跨票变更**：删掉「`updateTime` 兜底成 `createTime`」后，`GET /lqg/sample/list` 与 `/lqg/sample/{id}` 上**从没改过的行**的 `updateTime` 从「= createTime」变成 `null` | 所属域：SAMPLE-MODEL-001 落的 VO、SAMPLE-WEB-001 的前端工作台还没做（`code/plus-ui` 里没有 `updateByName` 的消费方，本票核过）。**本票没改任何 accept**；SAMPLE-WEB-001 做修改页时按「`update_time` 空 = 新增」用即可。方案：把这条写进 SAMPLE-WEB-001 的必读（或在其 ticket 的 §0 补一行）。 |
| WARN-5 | S2 | harness | **`api.sh --fresh-module` 在本沙箱恒 exit 2**（`ps` 被禁）+ **Maven 需带三参数** | 已连续 7+ 张命中（SYS-MODEL / AUTH-LOGIN / SYS-WEB / AUTH-STAFF / AUTH-GROUP / AUTH-EXT / 本票）。只影响 subagent 沙箱。本票用「`find -newer jar` 为空 + `lsof` 拿 PID + 进程持该 jar + 日志 mtime 晚于 jar + 嵌套 jar 内含新 class」五项等价替代（§4.0）。方案同上游：换成 `lsof` / 日志 mtime 的兼容写法，或给 `api.sh` 加「跳过启动时间守卫」的开关。 |
| WARN-6 | S3 | docs | `sys_config` 的「运行时可改」在**没有工作台前端**时不好验证缓存失效：`UPDATE sys_config` 后必须 `DELETE /system/config/refreshCache`，而本票实测**同一条 refresh 请求有时不立刻生效**（需要重启后端才稳） | 影响所有要演示 `lqg.ext.show-internal-no` / `lqg.cryo.overdue-days` 的票。本票的处置：`04b` 那一屏跑之前先 `UPDATE` + `refreshCache`，**并用接口断出 `hasKey:true` 之后才截图**；跑完还原成 `false` 且用接口复核 `hasKey:false`。方案：AUTH-EXT-002 / SYS-WEB-001 给「参数设置」页加一条「保存后立即生效」的 accept 断言（`@CachePut` 那条路），把「改库 + refreshCache」这条不可靠的路降级为调试手段。 |
| WARN-7 | S3 | clarify | **`GET /mp/int/sample/list` 的角色闸是 `lqg_internal`，`admin`（`lqg_admin`）打它会 403** | 契约第 19 行写的就是 `lqg_internal`（管理员同时带 internal 角色时才进得来），seed 里的 `lqgadmin` **只有 101 角色**，所以 `--as admin` 实测 403。这不是缺陷，但**下游若写 `--as admin` 断 200 会红**。方案：契约第 19 行补一句「`lqg_admin` 单独不满足 `/mp/int/**`，需同时带 `lqg_internal`」，或 seed 给 `lqgadmin` 补 102 角色（**属需求层，本票没动**）。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`doc/verify/db.py` 是只读执行器却 exit 0（AUTH-EXT-001 WARN-4）、`reseed.sh` 清不掉运行时账号（本票收尾跑 `clean-orphan-accounts.sh --yes`）、surefire `groups` 假绿、`PageQuery` 只有两参构造、`updateById` 忽略 null、MyBatis-Plus 无按列名排序重载（AUTH-EXT-001 §9 坑 1，本票按它用 `wrapper.last`）。

---

## §7 收尾（长进程 / 端口 / DB）

- **后端 8081**：收尾时按 PID 关掉（`lsof -ti tcp:8081 -sTCP:LISTEN` → `kill`；**没用 `pkill -f`**）。
- **H5 dev server 9200**：收尾时按 PID 关掉。
- **8080（Kevin）**：全程没碰。**未占** 8082 / 8083 / 8099 / 9201。
- **5433（`lqg-dev-postgres`）/ 6380（`lqg-dev-redis`）/ 9002-9003（`lqg-dev-minio`）**：全程在跑，**没停**（留给后续 ticket）。
- **DB 收尾**：`reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`；`sys_config` 复核 `lqg.ext.show-internal-no=false`。
- **报告里的复跑 runner**：`doc/waves/reports/SAMPLE-MP-001/accept-runners/{mp001-acc1-state.sh, mp001-acc2-build.sh, mp001-acc2-api.sh}` + `accept-transcript.txt`（三条链的完整输出）。

---

## §8 遗留与 raise

1. **开发者工具 / 真机未覆盖**（同 SYS-MP-001 WARN-1）：本票端侧证据全部是 H5 dev + Playwright。真机差异点：`wd-datetime-picker` 的原生 picker、`open-type` 类控件、tabbar 安全区。
2. **石蜡包埋卡片内容**（外部详情第②段）与**质控文档第③段**：本张按边界只给空状态（`暂无包埋记录` / `结果出具后会显示在这里`），内容在 AUTH-EXT-002 / AUTH-EXT-003。
3. **识别条只留位置**：两个按钮是纯展示（点了由 OCR-MP-001 接），本票**没有**接 `wx.chooseMedia`。
4. **`keyword`（内部编号 / 来源单位搜索）没落**：契约把它列在 `GET /mp/int/sample/list` 上，但那是「内部管理」表格页（SAMPLE-MP-002）的搜索框要的；本票刻意不写半截实现（`MpSampleQueryBo` 里留了注释说明谁补）。
5. **类器官收样页 / 表格页 / 它们的页签**：SAMPLE-MP-002；本票的页签清单已经把它们列出来（`entriesFor` 内部 4 张、外部 3 张），未注册数据源的页签显示空状态「这一档的记录在后续版本开放」。
6. **`merchPatch` 的边界**：内部 PUT 收部分字段时「没传 = 沿用现值」，所以**小程序里清不掉一个字段**（传空串也当成没传）。票面与契约都没有「小程序清字段」这条口径，本票按「误清一个字段的代价 > 清不掉一个字段」取舍，写进 `MpSampleService.mergePatch` 的 javadoc。若甲方要能清，加一个显式的「清空」哨兵值即可（属后续 CR）。
7. **`GET /lqg/sample/*` 的 `SampleVo` 多出 `handlerName` / `mine` / `editable` 三个键**（本票没另立一个 VO）：契约第 49 行的三个键是给小程序内部接口的，但读路径共用同一个 VO，所以工作台那两个接口也会带上它们。JSON 多键不破任何既有断言（本票核过 SAMPLE-MODEL-001 / SAMPLE-VERIFY-001 的 accept 与报告），但 SAMPLE-WEB-001 若做严格 keys 差集要注意。

---

## §9 收尾时的端口实况（补记）

```
$ for p in 8080 8081 8082 8083 8099 9200 9201; do lsof -ti tcp:$p -sTCP:LISTEN; done
8080: （空）
8081: （空）   ← 本票的后端，收尾时按 PID / 受管 job 关掉
8082: （空）｜ 8083: （空）｜ 8099: （空）
9200: （空）   ← 本票的 H5 dev server，按 PID 关掉
9201: （空）
```

> ★ 补记一句**不是本票造成的**现状：本票开工时 `lsof -ti tcp:8080 -sTCP:LISTEN` 有 PID 36755（Kevin 的 java 服务），
> 收尾复核时它已经不在了（`ps -p 36755` 无输出）。本票全程**没有对 8080 做任何操作** ——
> 关进程用的都是 `lsof -ti tcp:<8081|9200> -sTCP:LISTEN` 拿到的 PID（**从未用过 `pkill -f`**）。
> 如果那个服务需要常驻，请 Kevin 按自己的方式拉起来。
