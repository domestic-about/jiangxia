# SYS-HOME-001 · 完工报告（工作台首页五张待办卡片 + 最近提交表 + 侧边菜单角标）

- **branch**：`task/D7`（`git branch --show-current` 实测；未切分支、未 push、未 merge、未动 `state.json` / `_manifest.json`）
- **status**：**done（accept 2/2，但 acc1 有票面字面缺陷，见 §2.1 —— 请主会话裁定）**
- **accept**：`2/2`（**在「造一次真实渲染失败」的配置下**）；**环境原样（gotenberg 在跑）时为 1/2**，唯一红的是 acc1 第 17 行
  `jq -e '.data.renderFailed >= 1'` —— 票面假设「1001 的假图地址 → 渲染失败」，而 issue #217 的裁定是「个别图取不到 = 跳过 + WARN、文档照出 done」。**这是同一形态的第 3 次**（前两次：#248 DOC-PUBLISH-001、#251 AUTH-EXT-003），按规矩**没有改票面**。
- **本报告目录**：`doc/waves/reports/SYS-HOME-001/`
  - `probes/probe-home.mjs`：首页/角标/卡片跳转 + **dev 模式点菜单不白屏** 的 13 项探针
  - `probes/falsify-counters.sh`：accept 的 counterfeit **逐条反证**（手工把错法的前提造出来）
  - `evidence/`：两份 accept 的 json + 逐行日志、探针原样输出、D1 菜单回归、dev 编译产物根节点
  - `shots/`：首页与五张卡片落点、四个菜单跳转的截图（headless；负对照见 D1 报告）

---

## §0 状态自检（3 级）

| # | 检查 | 结论 |
|---|---|---|
| 1 | 当前分支 = 调度器注入的期望 | ✅ `task/D7` |
| 2 | `depends_on` 全部 done | ✅ `state.json`：DOC-PDF-001(done/D6)、CRYO-REMIND-001(done/D4)、AUTH-GROUP-001(done/D1)、EMBED-WEB-001(done/D3) |
| 3 | 扫 `doc/change-log.md` 的 CR | ✅ **CR-20260917-05**：小程序首页数字去掉（**小程序里不做任何计数**）；外部可提交石蜡包埋送样 → 工作台首页是唯一待办计数，且多一张「待核验石蜡包埋送样」卡片。本票严格照此（只改工作台 + 后端，`code/miniapp/**` 一个字节没碰） |
| 4 | 逐条取权威（不全文读蓝图） | ✅ `authority_lint.py show` 取 `UI:admin.home` + 四个 FLOW 步骤，正文见 §1.4 |
| 5 | 与蓝图的冲突 | ⚠️ **一处**：票面 §2 说 `renderFailed` = (样本, 文档种类, **版本**) 组数；acc1 第 16 行的机器判据是 `DISTINCT sample_id, doc_kind, **audience**`。冲突时以**蓝图 + 机器判据**为准 → 本票按 `(sample_id, doc_kind, audience)` 实现（见 WARN-2） |
| 6 | 环境可用 | ✅ 后端 8094 + 工作台 dev 8093（`qa-up.sh`），gotenberg 3010 |

---

## §1 改了哪些文件

### 1.1 后端（`ruoyi-lqg`）

**新增（全部在 `touches` 内）**

| 文件 | 职责 |
|---|---|
| `sys/home/service/HomeCounterService.java` | ★★ **计数的唯一来源**：五个数一次算出来。超期转发 `CryoOverdueService#countOverdue()`；样本/包埋/外部档案走各自域的**既有 mapper** + 各自域的**状态常量**（`VerifyTransitions.PENDING` / `ExtBindStateMachine.PENDING`）；渲染失败走 DOC 域的新读口。读侧一律 `DataPermissionHelper.ignore` |
| `sys/home/controller/HomeController.java` | `GET /lqg/home/todo`、`GET /lqg/home/recent`，**只挂 `@SaCheckLogin`**（不建权限串、不建菜单，见 WARN-5） |
| `sys/home/domain/vo/HomeTodoVo.java` | **恰好五个键**（acc1 第 1 段断对象逐字相等，多一个键就红） |
| `sys/home/domain/vo/HomeRecentVo.java` | 最近提交一行：`submitTime / submitNo / sourceUnitName / submitSource / verifyStatus` |
| `sys/home/{controller,service,domain/vo}/package-info.java` | 三个新包的包说明（与 `org.dromara.lqg.sys.*` 现有垫片同风格） |
| `test/…/sys/home/HomeCounterContractTest.java` | **6 例**：五键形状 / 最近提交五格 / **超期不重写**（源码级白名单：不许出现 `in_minus80`、`to_ln2_time`、`freeze_time`、`t_lqg_cryo_batch`、`CryoOverdueMapper`、`CryoOverdueSqlProvider`）/ 待核验样本**条件里没有 `sample_kind`** / 失败读口是 **DISTINCT 三键 + 手写 `del_flag='0'`** / 两端点只有 `@SaCheckLogin`。`Tests run: 6, Failures: 0` |

**修改（★ 越出 `touches`，1 个文件 —— 见 §3.1）**

| 文件 | 改动 | 为什么 |
|---|---|---|
| `doc/render/mapper/DocFileMapper.java` | +`countFailedGroups()`（`@Select` 的 `COUNT(DISTINCT sample_id, doc_kind, audience)`） | 「能在被调方加读口就不要在本包写 SQL」（§0.1 硬要求 ①）。**产物表的分工是 DOC 域的知识**：一次流水线失败会 `markFailed` **两次**（docx header + pdf，见 `DocRenderService#render` 的 catch），按行数数会把一份文档报成 2 |

**接口清单（本票新增 2 个端点）**

```
GET /lqg/home/todo    → data:{pendingSamples, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}   登录即可
GET /lqg/home/recent  → data:[{submitTime, submitNo, sourceUnitName, submitSource, verifyStatus}, …]（≤10） 登录即可
```

**★ 本票没有 Flyway 迁移**：不建表、不加列、不建菜单/权限行（首页路由 `/index` 是 `constantRoutes` 里的既有静态路由，角标挂在既有菜单 5210/5310/5410/5100 上）。
→ 派单里那条「迁移号必须 > `202609261430`」**本票用不到**；库里已应用最大版本实测 = `202609261430`（`QC-WEB-001-qc-menu`，`flyway_schema_history` 按 `installed_rank` 倒序前 3 = `…1430 / …1420 / …1410`），本票**没有** `db/migration/**` 的改动。

### 1.2 前端（`code/plus-ui`）

**新增（全部在 `touches` 内）**

| 文件 | 说明 |
|---|---|
| `src/api/lqg/home.ts` | 两个接口的 TS 封装与类型（`grep -q 'home/todo'` 命中） |
| `src/store/modules/lqgTodo.ts` | ★★ **前端唯一调 `/lqg/home/todo` 的地方**。卡片与角标读同一份 `todo`；`refresh()` 用 in-flight Promise 去重（首屏侧边栏 + 首页同帧挂载只打**一个**请求）；`ensureLoaded()` 给侧边栏用；`badgeOf(path)` 做「路径 → 数字」映射 |
| `src/views/lqg/home/index.vue` | **五张 `TodoCard`** + 「最近提交」表；数字只从 store 读；进页/回到页各重算一次（数字必须跟着真实变化走） |
| `src/views/lqg/home/components/TodoCard.vue` | 单张卡片：`:value` 为 0 时**仍渲染**并加 `is-zero` 变灰（**不是** `v-if`） |
| `src/lang/lqg/home.zh_CN.ts` / `home.en_US.ts` | 域内 i18n（键路径 `lqg.home.*`，靠 `src/lang/index.ts` 自动合并，未碰两个共享大文件） |

**修改（在 `touches` 内）**

| 文件 | 改动 |
|---|---|
| `src/views/index.vue` | 占位卡整段换成 `<lqg-home />`；**顶层模板注释挪进 `<script setup>`**（D1 的 S1 纪律：dev 保留模板注释 → 根变 Fragment）。实测 dev 编译产物根节点已是 `_createElementBlock("div", …)`、`grep -cE '_Fragment\|DEV_ROOT_FRAGMENT\|2112'` = **0**（`evidence/index-vue-dev-root.txt`），并**顺手修掉 issue #41**（dev 下首页 enter 动画：进入首页这一跳实测录到 `animate__animated animate__fadeIn`，`evidence/home-enter-animation.txt`） |
| `src/layout/components/Sidebar/SidebarItem.vue` | 菜单标题外包 `<el-badge>`：**样表=2/石蜡包埋=1/冻存管理=2/人员与单位**，`:hidden="badgeOf(...) <= 0"`（0 **不显示**角标）；`import { useLqgTodoStore }`、`onMounted(ensureLoaded)`；**没有**在本文件里出现任何接口路径 |

**修改（★ 越出 `touches`，4 个文件 —— 见 §3.1）**

| 文件 | 改动 |
|---|---|
| `src/views/lqg/sample/index.vue` | `onMounted` 认领 `?verifyStatus=`（只认 pending/valid/invalid 三个白名单值） |
| `src/views/lqg/embed/index.vue` | 同上（与既有 `?sampleId=` 并存） |
| `src/views/lqg/cryo/index.vue` | 认领 `?overdueOnly=true` → 直接落在「超期」页签（页签本来就是 `overdueOnly` 的派生值，只设这一个字段） |
| `src/views/lqg/auth/extuser/index.vue` | 认领 `?bindStatus=`（白名单 unbound/pending/verified/rejected） |

> 没有这 4 处，卡片就只是「跳到对的页面」，筛选条件会被目标页丢掉 —— 票面 §2 明确要求「点击带筛选条件跳对应页面」。

### 1.3 构建副产物（交接前已还原）

`code/plus-ui/.eslintrc-auto-import.json` 被 `pnpm build:prod` / dev server 下 `unplugin-auto-import` 重新生成（多了 `ElLoading/ElMessage/ElMessageBox/ElNotification` 四个 globals），与逐字改动无关。**已在收尾时 `git checkout --` 还原**；QA 门跑构建后它会再次出现（已知的构建副产物，不是缺陷）。

### 1.4 权威口径（逐字取回）

```
$ python3 doc/authority/authority_lint.py show UI:admin.home
"五张待办卡片，每张一个数字 + 点击直达：待核验样本（组织与类器官）、待核验石蜡包埋送样、
 -80 超期批次、待核验的外部用户组别、文档渲染失败。
 数字全部读时计算，为 0 时卡片变灰不隐藏。… 卡片下方「最近提交」10 条（提交时间、来源单位、
 内外部、状态）。不做统计图表、不做看板（甲方没提）。"
$ … show FLOW:F-CRYO-01.step3   → 工作台首页待办卡片显示超期批次数并可直达（系统内提示）
$ … show FLOW:F-SAMPLE-01.step2 → 待核验的行在总表里有醒目标记，工作台首页待办计数 +1
$ … show FLOW:F-DOC-01.step6    → 工作台首页待办显示渲染失败数；在质控文档页点「重新生成」
$ … show FLOW:F-EMBED-01.step7  → …工作台首页「待核验石蜡包埋送样」数字随之变化
```

---

## §2 accept 逐条 ✅ / ❌ + 关键输出

### 2.1 ★ acc1（DATA）：**环境原样 = ❌（红在第 17 行）**；**造一次真实失败 = ✅**

**(a) 环境原样（`lqg-dev-gotenberg` 在跑，派单说「别停」）**

```
$ python3 doc/waves/tools/accept-run.py --ticket SYS-HOME-001 --run \
      --json .tmp/sys-home-accept.json --logdir .tmp/sys-home-accept-logs
  ✗ SYS-HOME-001 acc1 [DATA] … → exit 1 (8.8s)
  ✓ SYS-HOME-001 acc2 [API] …
[run] 通过 1/2
[run] ⛔ 1 条未登记的红（= 产品断言不成立）：SYS-HOME-001 acc1
```

`evidence/accept1-gotenberg-up.log` 逐行（`set -x` 起来每段断言的原样输出）：

| 行 | 段 | 输出 |
|---|---|---|
| 7 | `todo` == 五个键逐字 | `true` |
| 8 | 库里 `2\|1\|2` | `2\|1\|2` |
| 9 | 首页超期数 == `/lqg/cryo/overdue` 长度 | `true` |
| 10 / 11 | extC 交类器官收样 / extA 交石蜡包埋送样 | `true` / `true` |
| 12 | 待核验样本 3、待核验送样 2 | `true` |
| 13 | 库里类器官=1、送样合计=2 | `1\|2` |
| 16 | 失败「组数」 == 接口 `renderFailed` | `0` |
| **17** | **`renderFailed >= 1`** | **`false`** ← 唯一红 |
| 18 | 最近提交 ≤10 且不含 `SJ90000010` | 未执行（`set -e` 停在第 17 行） |

根因（本 agent 独立复现，非猜测）：

```
$ curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3010/health      → 200（gotenberg 在跑）
$ bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=internal'
  {"status":"done","errorMsg":null}
$ db.py --sql "… COUNT(DISTINCT sample_id,doc_kind,audience) … render_status='failed'"  → 0
```

即：**1001 名下 4 张图都是 `sys_oss.service='seed'` 的假地址，但 `DocOssBytes.read()` 取不到就
「跳过 + WARN、文档照出 done」**（DOC-RENDER-001 的既定行为，issue #217 已裁定，DOC-PDF-001
WARN-1 / #245 也复核过）。票面 counterfeit 里那句「图片地址取不到 → 整份 failed」是**裁定前的旧写法**。
所以第 17 行在 gotenberg 正常时**永远不可能成立**（与实现无关）。

**(b) 造一次真实失败（把 gotenberg 停掉→渲染→立刻起回来）**

```
$ docker stop lqg-dev-gotenberg ; curl … /health → 000
$ python3 doc/waves/tools/accept-run.py --ticket SYS-HOME-001 --run \
      --json .tmp/sys-home-accept-gotenberg-down.json --logdir .tmp/sys-home-accept-logs-down
  ✓ SYS-HOME-001 acc1 [DATA] … (7.8s)
  ✓ SYS-HOME-001 acc2 [API] … (10.8s)
[run] 通过 2/2
$ docker start lqg-dev-gotenberg ; sleep 6 ; curl -s -o /dev/null -w '%{http_code}' …/health → 200
```

`evidence/accept1-gotenberg-down.log`（第 15~18 行的段）：

```
true            ← 第 7 行   todo == 五键逐字
2|1|2           ← 第 8 行   库里 样本2 / 送样1 / 外部档案2
true            ← 第 9 行   首页超期数 == 超期清单长度
true  true      ← 第 10/11 外部交类器官收样 / 石蜡包埋送样
true            ← 第 12 行  待核验样本 3、待核验送样 2
1|2             ← 第 13 行  库里类器官=1 / 送样合计=2
1               ← 第 16 行  失败「组数」== 接口 renderFailed = 1
true            ← 第 17 行  renderFailed >= 1
true            ← 第 18 行  最近提交 ≤10 且不含软删的 SJ90000010
```

渲染真失败的现场（另一份实测）：

```
render → {"status":"failed","errorMsg":"转换服务不可用（POST http://127.0.0.1:3010/forms/libreoffice/convert 失败：java.net.ConnectException）"}
```

> **给主会话的处置建议（三选一，本票不擅自改票面）**：
> ① 照 DOC-PDF-001 acc2 的先例，把 acc1 第 15 行前后改成显式
> `docker stop lqg-dev-gotenberg … render … docker start lqg-dev-gotenberg`；
> ② 或删掉第 17 行、保留第 16 行（「接口 == 库里独立口径」这一段已经是同源证明，
> 而「会随真实失败变化」由 §2.3 的 A/B 反证覆盖）；
> ③ 或把 counterfeit 那句与 acc1 的期望一起改成 issue #217 的裁定口径（个别图取不到 = done）。

### 2.2 acc2（API）：**✅ 两轮都绿**

```
$ cd code/plus-ui && rm -rf dist && pnpm build:prod   → EXIT=0，dist/index.html 存在
$ grep -q 'home/todo' src/api/lqg/home.ts                                   → 命中
$ grep -c 'TodoCard' src/views/lqg/home/index.vue                           → 6（≥5）
$ ! grep -nE 'v-if="[^"]*(pendingSamples|pendingEmbeds|cryoOverdue|pendingExtUsers|renderFailed)[^"]*> *0"' src/views/lqg/home/index.vue  → 无命中
$ ! grep -nE 'echarts|el-statistic' src/views/lqg/home/index.vue             → 无命中
$ grep -q 'pendingEmbeds' src/store/modules/lqgTodo.ts                      → 命中
$ grep -rq 'lqgTodo' src/layout/components/Sidebar && ! grep -rq 'home/todo' src/layout/components/Sidebar → 命中
```

> ★ 这 7 段都是**源码 grep 式的结构断言**，本身不证明运行时行为。真正的行为证据是 §2.3 的探针
> （同一次请求、为 0 不隐藏、角标与卡片同数、点菜单不白屏）。这一点请 QA 门当作 acc2 的已知弱点（WARN-6）。

### 2.3 探针：首页 13/13、D1 菜单回归 9/9、反证 5 组全中

**(a) `probes/probe-home.mjs`（dev 8093，headless）** → `evidence/probe-home-dev.txt`

```
① 登录落在 /index 且首页渲染出内容（不是白屏）              len=13540 url=/index
② 恰好五张待办卡片        ["待核验样本","待核验石蜡包埋送样","-80 超期批次","待核验外部用户","文档渲染失败"]
③ 五个数字与接口/seed 一致  渲染=[2,1,2,2,0] 期望=[2,1,2,2,0]
④ 为 0 的那张卡片仍在（不隐藏）且带 is-zero   {"title":"文档渲染失败","num":"0","zero":true,"visible":true}
⑤ 登录到首页整轮只请求 1 次待办接口   /lqg/home/todo 请求数=1；/lqg/home/recent 请求数=1
⑥ 四个菜单角标与卡片同数  样本总表=2 石蜡包埋=1 冻存管理=2 人员与单位=2（其余菜单角标 display:none）
⑦ 角标里没有「0」        可见角标=["2","1","2","2"]
⑧ 最近提交表 9 行、不含软删的 SJ90000010
⑨ 点「待核验样本」卡片 → /sample?verifyStatus=pending，页面真的套上了筛选（len=43743）
⑩ dev 模式连点四个菜单都不白屏  石蜡包埋:ok(47367) 冻存管理:ok(53921) 外部用户:ok(21751) 首页:ok(13600)
⑪ 回到首页卡片仍是五个、数字不变，角标与卡片仍是同一份（同源未分叉）
⑫ 全程无 console.error / pageerror
⑬ 另外四张卡片也都直达正确页面 + 带筛选 + 渲染正常
   card1 /embed?verifyStatus=pending(47412 含「待核验」) card2 /cryo?overdueOnly=true(53975 含「超期」)
   card3 /auth/extuser?bindStatus=pending(21751 含「待核验」) card4 /qc-console/doc-console(5355 含「文档渲染状态」)
== 13/13 通过（headless=true，EXPECT_SAMPLES=2）==
```

**「五个数会随真实变化而变」（前端侧）**：外部新交一条类器官收样后 `EXPECT_SAMPLES=3` 再跑一遍 → **13/13**，
卡片与角标一起从 2 变 3（`渲染=[3,1,2,2,0]`、角标 `["3","1","2","2"]`）。

**(b) ★「dev 模式点菜单不白屏」的实测证据（派单点名要的）**

| 证据 | 内容 |
|---|---|
| 本票探针 ⑩ | 从首页出发连点 石蜡包埋 → 冻存管理 → 外部用户 → 首页，四跳 `.app-main innerHTML` = 47367 / 53921 / 21751 / 13600，**无一为 7**（白屏的特征值就是 7） |
| **D1 的原始回归探针** | `LQG_WEB_BASE=http://127.0.0.1:8093 node doc/waves/reports/D1-rework-S1/probe-web-menu-nav.mjs` → **9/9 通过**（含「离开首页」这个回归点、enter 动画类仍在、全程无 console 报错）。原样输出见 `evidence/d1-menu-nav-regression.txt`，截图 `shots/d1-regression/` |
| dev 编译产物根节点 | `_createElementBlock("div", …)`，`_Fragment / DEV_ROOT_FRAGMENT / 2112` **0 命中** —— 首页不再是 Fragment 根（`evidence/index-vue-dev-root.txt`） |
| 顺手修掉 issue #41 | 进入首页这一跳实测录到 `animate__animated animate__fadeIn` 挂在新页根元素上（`evidence/home-enter-animation.txt`） |

**(c) `probes/falsify-counters.sh`：counterfeit 逐条反证** → `evidence/falsify-counters.txt`

| counterfeit（票面原文） | 反证做法（把错法的前提造出来） | 实测 |
|---|---|---|
| 渲染失败数写死成 0 | **真停 gotenberg**渲染一次 → 真失败 | 库里组数=1，接口 `renderFailed=1`（写死 0 会在 acc1 第 16 段就红） |
| 渲染失败数按**行数**数 | 手工补一行**同一组**的 failed pdf 行 | 库里失败行数=**2**、组数=**1**，接口 `renderFailed=1`（按行数数的实现会给 2） |
| 待核验用户数把 `unbound` 也算进去 | seed 里**没有** unbound，手工把一条 verified 改成 unbound | 库里 `pending=2`、`unbound+pending=3`，接口 `pendingExtUsers=2` |
| 待核验样本只数组织样本 | 外部真交一条**类器官**收样 | 库里全 pending=3、其中类器官=1、组织=2；接口 `pendingSamples=3`（只数组织会给 2） |
| 待核验送样数了全部外部送样 | 同一次：extA 交一条石蜡包埋送样 | 接口 `pendingEmbeds=2` |
| 最近提交没过滤软删 | seed 的病灶：`9000001010 / SJ90000010`（`del_flag='1'`） | 库里未删=10、含软删=11；接口 10 行、`含 SJ90000010=false` |
| 首页与冻存列表各写各的超期 where | —— | 首页 `cryoOverdue=2` == `/lqg/cryo/overdue` 长度 2（且 `CryoOverdueService.countOverdue()` 是唯一调用口，源码级白名单见单测） |

### 2.4 回归自查

| 项 | 结果 |
|---|---|
| `ruoyi-lqg` **全模块单测** | **263 / 263 通过，0 failures**（含本票新增的 6 例；`Tests run: 263, Failures: 0, Errors: 0`） |
| D1 菜单导航回归探针 | 9/9（见 §2.3b） |
| `pnpm build:prod` | EXIT=0，`dist/index.html` 存在 |
| 我改过的 4 个列表页 | 探针 ⑨⑬ 覆盖：sample / embed / cryo / extuser 四页都正常渲染且筛选生效；后端接口不变（只多读 query） |
| 库 | 收尾 `reseed.sh --yes` 回确定性快照；`todo` = `{2,1,2,2,0}` |
| 只读区 | `_input/`、`doc/requirements.yaml` text、`doc/authority/*.yaml`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`api.sh`、`fixtures/`、`code/miniapp/**`、`src/pages.json` **一个字节没碰**（`git status --porcelain` 见 §5） |

---

## §3 遗留与 raise

### 3.1 越出 `touches` 的改动（5 个文件，请入账）

| # | 文件 | 为什么非改不可 | 级别 |
|---|---|---|---|
| 1 | `…/doc/render/mapper/DocFileMapper.java` | ★ **票面 §0.1 硬要求 ① 点名**：「能在被调方加读口就不要在本包写 SQL」。失败数要 `COUNT(DISTINCT …)`，MyBatis-Plus 的 wrapper 表达不了；产物表的分工（一次失败两行）是 DOC 域的知识 | 允许（派单已授权） |
| 2~5 | `plus-ui` 的 `views/lqg/{sample,embed,cryo,auth/extuser}/index.vue` | 票面 §2 要求卡片「点击**带筛选条件**跳对应页面」。这四页原先只认 `?sampleId=`（cryo/embed）或完全不认 query，不认领就只是「跳到对的页面」而筛选丢失 | 允许（派单已授权） |

### 3.2 与 `doc/api-contract.md` 的差异：**0 处**

契约第 88 行「`GET /lqg/home/todo`、`GET /lqg/home/recent` → `data:{pendingSamples, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}`」与本票实现逐字一致（`pendingEmbeds` = 待核验石蜡包埋送样数，CR-20260917-05）。`recent` 的**每行字段**契约没写，本票按票面 §2 的五个字段落地（已在 §1.1 记录）。

### 3.3 没把握 / 需要确认的口径

1. **`renderFailed` 的分组键**：票面 §2 = (样本, 文档种类, **版本**)；acc1 第 16 行 = (样本, 文档种类, **受众**)。本票按 acc1 实现（机器判据优先），并且**版本不进键也等价**（同一组的行会被 `upsertPending` 原地复用，`content_hash` 只会有当前一版）—— 但这两个写法在「同一组存在多版本 failed 行」的假想场景下会不同，请主会话把票面 §2 的「版本」更正为「受众」。
2. **acc1 第 17 行**（§2.1）—— 本票最大的 raise。
3. **「待核验外部用户」要不要含 `unbound`**：蓝图只说「待核验的外部用户组别」，`unbound` = 还没提交单位（没有可核验的东西）。本票按**只数 pending** 实现，并用反证证明（§2.3c）。若口径是「含 unbound」，那是 seed 里没有 unbound 行导致的判据空转，请票面补一条 seed 或断言。

---

## §4 WARN 清单（请逐条入账）

- **WARN-1 · acc1 第 17 行与 issue #217 的裁定冲突（票面字面缺陷，同形态第 3 次）**。环境原样 1/2；造真实失败 2/2。详见 §2.1。**未改票面**。
- **WARN-2 · 票面 §2 与 acc1 对 `renderFailed` 的分组键写法不同**（版本 vs 受众），本票按 acc1。见 §3.3-1。
- **WARN-3 · acc1 的 counterfeit「待核验用户数把 unbound 也算进去」在 seed 上是空转**：seed 里 `t_lqg_ext_profile` 只有 `pending=2 / verified=4`，**一条 unbound 都没有** —— 把 unbound 算进去的实现照样绿。本票另做反证（§2.3c）补上这个洞，但**票面的机器判据仍缺这一格**。
- **WARN-4 · acc2 的 7 段全是源码 grep，不是行为断言**。`! grep v-if…`、`grep -rq lqgTodo` 这类断言换个写法就能绕过（与 issue #37 同形）。本票的可运行行为证据在 `probes/probe-home.mjs`（13 项），建议 QA 门把它当 L2 资产复用。
- **WARN-5 · 两个端点只挂 `@SaCheckLogin` → 外部账号也能读到全中心五个待办数**。票面 §2 写的是「登录即可调（101 / 102）」，本票照字面实现（**不建权限串**的理由：首页是登录后第一屏，「少一行 sys_menu 就 403」正是本票要消灭的「没有待办 vs 功能坏了」那种混淆）。风险面：五个数**都是聚合数字，不含任何患者信息**；且 `plus-ui` 工作台本来就不对部分外部账号开放。若要求收紧：加一句 `@SaCheckPermission("lqg:cryo:list")`（101/102 都有，外部没有）即可，**不影响两条 accept**。
- **WARN-6 · 角标的刷新时机**：数字只在「进首页 / 回到首页 / 点首页的刷新按钮」时重算（store 同源）。用户若一直待在某个列表页里把待办核验掉，侧边角标要等他回首页才更新。这是有意的（避免每个路由切换都打一次接口），但**不是实时**；若甲方要求实时，需要事件/轮询（本票 §3 边界外）。
- **WARN-7 · `pnpm build:prod` 会重写 `code/plus-ui/.eslintrc-auto-import.json`**（多加 4 个 Element Plus globals）。本票交接前已 `git checkout --` 还原；QA 门跑构建后它会再次出现，属**生成物**，与逐字改动无关。
- **WARN-8 · 失败数读口写在 DOC 域（越界 1 处）**，见 §3.1-1。若 DOC 域后续给 `t_lqg_doc_file` 加列（比如按 `content_hash` 分组），只需改 `DocFileMapper#countFailedGroups()` 这一个方法。

---

## §5 给下游的坑（D7 QA 门 / SYS-MANUAL-001）

**给 D7 QA 门（重要）**

1. **acc1 照字面跑会红**（第 17 行）。要么先 `docker stop lqg-dev-gotenberg`（跑完 `docker start`，本报告就是这么跑的，2/2），要么等主会话改票面。**别把这条红当成实现缺陷**；对应的实现证据在 `evidence/accept1-gotenberg-down.log` 与 §2.3c。
2. **本票不需要 Flyway 迁移**，别去找 `V2026…SYS-HOME-001*.sql`（不存在）。
3. acc2 是 grep 式断言（WARN-4），L2 请直接用 `doc/waves/reports/SYS-HOME-001/probes/probe-home.mjs`（13 项，已含「点菜单不白屏」）。它需要 dev server（8093）+ 后端（8094），**不接受 build 产物**（白屏那条路径只在 dev 出现）。
4. `probes/falsify-counters.sh` 会写库（psql UPDATE/INSERT）并在结束时 reseed —— 只在 dev 库上跑。
5. 我改过 4 个列表页的 `onMounted`（只多读 route.query，白名单值），回归时请顺带确认 样本总表 / 石蜡包埋 / 冻存管理 / 外部用户 的**既有 `?sampleId=` 跳转仍正常**（探针 ⑨⑬ 已覆盖）。

**给 SYS-MANUAL-001（操作说明可以直接抄这几行）**

6. 工作台首页（登录后落地页）有五张卡片，**数字为 0 也显示**，灰掉的卡片就是「现在没有待办」，不是功能坏了；点卡片直达对应列表并自动带上筛选（待核验样本/待核验石蜡包埋送样/超期批次/待核验外部用户/渲染失败的文档）。
7. 侧边菜单角标与卡片是**同一次请求**的数字；**角标为 0 时不显示角标**（不是显示 0）。角标出现在：样本总表、石蜡包埋、冻存管理、人员与单位（父菜单，对应待核验外部用户）。
8. 「文档渲染失败」那一张是**按「样本 + 文档种类 + 内部/外部版」组的**，不是按文件数 —— 一次失败写了 Word 与 PDF 两行也只算 1。
9. 「最近提交」只列**没有删除**的样本，最多 10 条。

---

## §6 长进程与端口 / 收工自检

```
$ bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --no-mp
  ✓ 已按 PID 关停 工作台 dev(8093)（pid 92191）
  ✓ 已按 PID 关停 后端(8094)（pid 91300）
  ✓ 8094 已释放   ✓ 8093 已释放   ✓ 9202 已释放
```

- 起过的进程**全部按 PID 关停**（`qa-up.sh --down`，**没有**用过 `pkill -f 'ruoyi-admin.jar'`）；8080/5432/6379 全程没碰；
- 三个 dev 容器（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002）+ **`lqg-dev-gotenberg` 3010** 都没停（gotenberg 只为 §2.1b 停过约 20 秒，跑完立刻 `docker start`，`/health` = 200）；
- 收尾跑了一次 `reseed.sh --yes`（库回确定性快照，`todo` = `{2,1,2,2,0}`）；
- 临时调试脚本在 `.tmp/`（gitignore 覆盖），`code/plus-ui/.eslintrc-auto-import.json` 已还原；
- 未 push、未 merge、未动 `doc/waves/state.json` 与 `_manifest.json`、未改写历史。
