# D3 返工 r1 · issue #145（S0）· 工作台核验抽屉「判为有效并保存」点不动

- **issue**：#145（D3 r1 L2 分片唯一拦门 S0）· ticket `EMBED-WEB-001` / `EMBED-MP-001`（下游连带）
- **status**：**已修**（改动只有前端 4 个文件 + 本报告目录）
- **分支**：`task/D3`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`、`doc/verify/**`、`doc/waves/regression/**`）
- **验证对象**：后端 jar `ruoyi-admin.jar` mtime `2026-09-22 10:13`（**后端一个字节没改**）· 8081 PID **31342** · plus-ui dev 8082 PID **31369** · miniapp H5 9200 PID **31722**
- **机器证据**：本目录 `issue145-probe.mjs`（19 检查全绿，走真 UI）、`accept-runners/rework-accepts.sh`（12 段，全绿）、`probe-transcript.txt`（修前/修后原始日志）、`accept-runners/accept-transcript.txt`（accept 原始输出）、`shots/`（截图只落盘，**全程没有 read_image / cat 图片**）
- **一句话**：核验抽屉不再「先打一个必被后端 400 拒的普通保存」，`pending` / `invalid` 直接走 `PUT /lqg/embed/{id}/verify`（必填项随这次请求送）；失败必 toast 出后端 msg、不关抽屉、不再漏未捕获 rejection。

---

## §1 现象（返工前实测，非转述）

`doc/waves/qa/D3-r1-L2.json` 第 1 条 S0 的复现，我在动手前用 QA 的定点探针又跑了一遍，逐字命中：

```
$ node doc/waves/regression/D3/L2-probe-verify-drawer.mjs        # 修前（.tmp/D3-baseline-probe.log）
first row after extA submit: 外部| |待核验| |待核验| |T-hli01| |组织| |—|…|核验
form items = 16 blockNo index = 1
input value after fill = T-R1L2-PROBE
btn disabled = false
--- NET/ERR LOG ---
REQ PUT http://127.0.0.1:8082/dev-api/lqg/embed :: {"sampleId":9000001001,"paraffinBlockNo":"T-R1L2-PROBE",…}
PAGEERROR: error
RESP 200 http://127.0.0.1:8082/dev-api/lqg/embed :: {"code":400,"msg":"待核验 / 无效的送样不能通过普通保存修改（核验与改判只走 PUT /lqg/embed/{id}/verify）","data":null}
--- drawer still visible: true
--- toast: []          ← .el-message 为空（页面上只有全局拦截器的一条 el-notification）
--- db row: pending|-
```

- 唯一写请求 = `PUT /lqg/embed`（**普通保存**）→ 后端 `{"code":400,…}`（HTTP 200 包着业务码 400）；
- `PUT /lqg/embed/{id}/verify` **一次都没发**；
- 库里仍 `pending|-`；抽屉不关；`.el-message` 空；浏览器另抛 `PAGEERROR: error`（未捕获 rejection）。
- `submitInvalid()`（判为无效）源码上是同一段前置 → 同一条死路。

**定级复核（同意 QA 的 S0）**：这是 CR-20260917-05 的主流程本身（外部送样 → 实验室在这一页核验给石蜡块编号）。照说明书走到这一步按钮无反应、拿不到编号、也判不了无效 → 主流程卡死。

---

## §2 根因（源码级）

`code/plus-ui/src/views/lqg/embed/EmbedDrawer.vue`（修前）：

```ts
const saveBeforeVerify = async () => {
  if (mode.value !== 'verify' || !form.value.id) return;
  await updateEmbed({ ...payload(), id: form.value.id });   // ← PUT /lqg/embed
};
const submitValid = async () => {
  …
  await saveBeforeVerify();                                  // ← 必被 400 拒，异常在此抛出
  await verifyEmbed(id, { action: 'valid', paraffinBlockNo });// ← 永远执行不到
```

后端 `EmbedService.update`（`ruoyi-lqg/.../embed/service/EmbedService.java:154`）只放行 `verifyStatus === 'valid'`：

```java
if (!VerifyTransitions.VALID.equals(exists.getVerifyStatus())) {
    throw new ServiceException("待核验 / 无效的送样不能通过普通保存修改…", 400);
}
```

而核验抽屉（`mode='verify'`）**只能**由 `isExternalPending(row)`（`submitSource=external && verifyStatus ∈ {pending, invalid}`）打开 → 预保存**恒被拒**。换句话说 `saveBeforeVerify()` 在核验档里 100% 是个「注定失败的写请求”，设计时的理由（“把抽屉里补填的工序 / 染色存掉”）与后端状态机直接冲突。

**同一根因的第二个症状**：`submitValid` / `submitInvalid` / `submitSave` 都是 `try…finally`（没有 `catch`），`@/utils/request` 的响应拦截器对 400 走 `ElNotification.error(msg)` + `Promise.reject('error')` → 抽屉里既没有 `el-message`，rejection 也漏成 pageerror。

---

## §3 修法

### 3.1 判有效 / 判无效两条路径的新顺序

```
【判有效】submitValid()
  1. 前端自检：石蜡块编号非空（空 → msgWarning，不发请求）+ 染色互斥自检
  2. preSaveIfEditable()   ← 仅当「这条记录本身允许普通保存」时才发 PUT /lqg/embed
                              （判据 = 后端 editable = verifyStatus==='valid'；核验档恒为 false）
  3. PUT /lqg/embed/{id}/verify  {"action":"valid","paraffinBlockNo":"…"}   ← 必填项随这次请求一起送
  4. 成功：msgSuccess「已判为有效」→ 关抽屉 → emit('saved') 刷新列表
     失败：catch → msgError(后端 msg) → 抽屉不关、rejection 就地吃掉

【判无效】submitInvalid()
  1. 原因必填（表单 rules）
  2. preSaveIfEditable()   ← 同上，核验档为 no-op
  3. PUT /lqg/embed/{id}/verify  {"action":"invalid","reason":"…"}
  4. 成功：msgSuccess「已判为无效」→ 关原因弹窗 + 关抽屉
     失败：catch → msgError → 原因弹窗与抽屉都留着

【普通保存】submitSave()（新增 / 编辑档）
  只有一步写请求（POST 或 PUT /lqg/embed），失败也补上 catch → msgError（同一类静默失败一并收口）
```

### 3.2 改了哪些文件（4 个，全在既有 touches 内）

| 文件 | 改动 |
|---|---|
| `code/plus-ui/src/views/lqg/embed/EmbedDrawer.vue` | `saveBeforeVerify()` → `preSaveIfEditable()`（加 `shouldSaveBeforeVerify` 判据）；`submitValid` / `submitInvalid` 直接走 `/verify` 并把必填项随请求送；三处 `try…finally` 补 `catch` + `msgError`；新增纯函数 `failText()`（优先后端 msg）；把「为什么这么排序」写成源码注释 |
| `code/plus-ui/src/api/lqg/embed/index.ts` | 新增纯判据 `shouldSaveBeforeVerify(row)`（判据与后端同源，复用既有 `isEditable`）+ 长注释（issue #145 的防复发说明） |
| `code/plus-ui/src/lang/lqg/embed.zh_CN.ts` | `msg.saveFailed` / `msg.verifyFailed` 两条兜底话术（**只在拿不到后端 msg 时才用**，见 §3.3） |
| `code/plus-ui/src/lang/lqg/embed.en_US.ts` | 同上（两文件 key 集合保持成对） |

**没改**：后端一个字节没改（`EmbedVerifyBo` 只收 `action/paraffinBlockNo/reason`，契约 `doc/api-contract.md:62` 就这么写的，改它就是接口契约漂移）；`doc/verify/**`、`doc/waves/regression/**`（QA 资产）、`api.sh`、`state.json`、`_manifest.json` 全未动。

### 3.3 `failText()`：后端 msg 从哪来

| 后端业务码 | 全局拦截器（`@/utils/request`） | 抽屉 catch 拿到的 | 用户看到 |
|---|---|---|---|
| 500 / 601（`ServiceException` 默认，例：撞号、样本未核验） | `ElMessage({message: msg})` + `reject(new Error(msg))` | `e.message` = **后端原话** | toast 里就是后端那句（如「石蜡块编号「T-E01-1」已存在，请换一个」） |
| 400（本票这条普通保存守卫，修后核验档不再触发） | `ElNotification.error(msg)` + `reject('error')` | 只剩字符串 `'error'` | 通知里有后端原话 + toast 出通用兜底话术 |
| 网络 / 超时 | `ElMessage({message: 中文翻译})` + `reject(error)` | axios Error 的 message | toast 出该翻译 |

### 3.4 核验档里补填的工序 / 染色 / marker 怎么办（明说，不藏）

契约的 `EmbedVerifyBo` 只收三个键，所以它们**不随核验落库**。修前的「预保存」本意是把它们一起存掉，但那一步恒被拒 —— 用户填的东西其实一个字都没存。修后的行为与抽屉自己的提示一致（「判为有效要给石蜡块编号…**之后照常补工序与染色**」）：核验通过后这行变成 `valid`（`editable=true`），从列表点「编辑」补填即可。**没有**改成「verify 成功后再补一次 PUT」——那会给 r2 的「没有多余的普通保存」断言制造歧义，且不是本 issue 的修法方向。

---

## §4 同类调用顺序 · 全仓扫描结论（★ 这条 S0 最值得防的复发形态）

扫描范围：`code/plus-ui/src/**`（含 `views/lqg/**` 全部 8 个 .vue）、`code/miniapp/src/**`（pages / api / components）、以及后端所有状态转移写入口（`/verify` 家族）。判据 = **「先做一个必被后端拒的写请求，再走主流程」**。

| 位置 | 调用顺序 | 结论 |
|---|---|---|
| `views/lqg/embed/EmbedDrawer.vue` `submitValid/submitInvalid` | 修前：`updateEmbed(PUT /lqg/embed)` → `verifyEmbed` | ❌ **就是本 issue**，已修 |
| `views/lqg/sample/SampleDrawer.vue` `submitVerifyValid/submitInvalid` | 只有一次写请求：`verifySample(id, verifyPayload(action,…))`，`verifyPayload()` 把 receiveDate/internalNo/isFixed/processTime/…+reason **全部并进 verify 请求体** | ✅ **本来就是对的**，是本次修法的参照实现（Embed 域的差别只是后端 `EmbedVerifyBo` 不收别的键） |
| `views/lqg/auth/extuser/index.vue` `submitVerify` | 单次 `verifyExtUser(userId, payload)` | ✅ 无预写 |
| `views/lqg/auth/{staff,unit}/index.vue` 各 submit | 单次写（grant/role/pwd/unit/group） | ✅ 无预写（其中几处缺 `catch` → 见 §8 WARN-2） |
| `code/miniapp/src/pages/embed/form.vue` `submit()` | 单次写：内部 `POST/PUT /mp/int/embed`、外部 `POST/PUT /mp/ext/embed`；**补填不再 POST 一次**（注释里点名过撞号 counterfeit） | ✅ 无预写；`catch` + `uni.showToast(e.message)` 齐 |
| `code/miniapp/src/pages/{sample,organoid}/form.vue` `submit()` | 单次写 + `catch` + toast | ✅ |
| `code/miniapp/src/pages/me/unit-group.vue` `save()` | 单次 `PUT` + `catch` + toast | ✅ |
| 后端 `/verify` 家族（sample / embed / ext-user） | 都是「先全部校验、再写库」的单一事务入口 | ✅ 没有「先写一半再转移」的两段式 |

**结论：全仓只有 `EmbedDrawer` 这一处**。另有一处「同类症状但不同形态」的隐患记在 §8 WARN-2（缺 `catch` 导致的未捕获 rejection，本票只收口了 `EmbedDrawer` 自己的三处）。

---

## §5 修前 / 修后网络序列与库内对照

### 5.1 并排对照（同一条待核验外部送样、同一个石蜡块编号、同一个「判为有效并保存」按钮）

| | 修前（`L2-probe-verify-drawer.mjs`，`.tmp/D3-baseline-probe.log`） | 修后（同一条 QA 探针，`.tmp/D3-probes-after.log`） |
|---|---|---|
| 写请求序列 | `PUT /lqg/embed` → | **`PUT /lqg/embed/2102229490548617218/verify`**（唯一一条） |
| 请求体 | 整份 `payload()`（sampleId/paraffinBlockNo/sampleType/… 16 键） | `{"action":"valid","paraffinBlockNo":"T-R1L2-PROBE"}` |
| 响应 | `{"code":400,"msg":"待核验 / 无效的送样不能通过普通保存修改（核验与改判只走 PUT /lqg/embed/{id}/verify）"}` | `{"code":200,"msg":"操作成功","data":null}` |
| `PUT /lqg/embed/{id}/verify` | **从未发出** | 发出 1 次，200 |
| 库内该行 | `pending|-` | **`valid|T-R1L2-PROBE`** |
| 抽屉 | 仍打开 | 关闭 |
| 可见反馈 | 无 `el-message`（只有拦截器 el-notification） | `el-message`「已判为有效」 |
| JS 异常 | `PAGEERROR: error`（未捕获 rejection） | 无 |

### 5.2 我自己的三条路径探针（`issue145-probe.mjs`，19/19 全绿）

```
$ node doc/waves/reports/D3-rework-r1-issue145/issue145-probe.mjs
PASS I145-03 判有效只发了一条 PUT /lqg/embed/{id}/verify（普通 PUT /lqg/embed 一条都没发）
             net=["REQ PUT /lqg/embed/2102230920617537538/verify :: {"action":"valid","paraffinBlockNo":"T-I145-R1-A"}",
                  "RESP 200 /lqg/embed/…/verify :: {"code":200,"msg":"操作成功","data":null}"]
PASS I145-06 判有效真的落库：valid + 编号 + 核验人 9000000100 — row=valid|T-I145-R1-A|9000000100
PASS I145-07 成功有明确提示（el-message 含「已判为有效」）— toasts="已判为有效"
PASS I145-08 判有效成功后抽屉关闭
PASS I145-09 全程没有未捕获 rejection / pageerror
PASS I145-10 判无效也直接走 /verify（不带普通 PUT），请求体带 reason
             net=[… "REQ PUT /lqg/embed/…/verify :: {"action":"invalid","reason":"issue145 返工验收：外部送样信息不符"}", "RESP 200 …"]
PASS I145-11 判无效落库：invalid + 原因 + 核验人 9000000100
PASS I145-12 判无效成功有提示且抽屉关闭 — toasts="已判为无效"
PASS I145-14 负对照：撞号的 verify 请求确实发出且被后端拒（非 200）
             net=[… "RESP 200 /lqg/embed/…/verify :: {"code":500,"msg":"石蜡块编号「T-E01-1」已存在，请换一个","data":null}"]
PASS I145-15 负对照：库内该行仍是 pending|-（什么都没变）
PASS I145-16 负对照：失败必 toast（el-message 非空且是后端那句 msg）— toasts="石蜡块编号「T-E01-1」已存在，请换一个" msgNodes=2
PASS I145-17 负对照：失败不关抽屉（留着让人改编号）
PASS I145-18 负对照：失败也没有未捕获 rejection

== issue145 19/19 通过 ==
```

负对照的 `msgNodes=2`（同一句话两条 el-message）= 全局拦截器 500 分支自己也弹一条 + 抽屉 catch 再弹一条 → 记进 §8 WARN-1。

### 5.3 下游连带一并核实（真实路径，不再用 API 兜底）

`L2-g3-mp-ext.mjs` 里原本要靠 API 兜底才能验的两条，现在**由 UI 自己**完成核验后就绿了：

```
PASS G3A-10 extA 在 1001 详情里看到石蜡块卡片，标识是石蜡块编号 — cards=3 nos=["T-E01-1","T-E01-2","T-r1l2-E1"]
PASS G3B-07 extB 打开 1001 详情看到石蜡块卡片，标识是石蜡块编号 — cards=3
PASS G3A-09 extA 历史编辑记录的石蜡包埋页签能看到这块石蜡（标识=石蜡块编号）— rows=["T-r1l2-E1|有效|组织|我|新增", …]
```

---

## §6 重跑的 QA 探针（含 3 条**探针自身**的红 · 需 r2 改断言）

| 探针 | 结果 | 说明 |
|---|---|---|
| `L2-probe-verify-drawer.mjs`（定点） | **修好** | 见 §5.1：只发 `/verify`、200、抽屉关、库内 `valid|T-R1L2-PROBE` |
| `L2-g1-web.mjs`（工作台 24 检查） | **24/24 绿** | 必须**单独**跑（该脚本自己**不 reseed**；我第一轮跟 g3 串跑时被 g3 留下的第 3 个石蜡块带红了一条 G1E-02，`reseed` 后单跑 24/24） |
| `L2-g3-mp-ext.mjs`（小程序外部 + 核验闭环） | **18/21** | r1 的两条红 **G3B-06 / G3B-07**：**G3B-07 已转绿**，G3B-06 见下；另新增 2 条红是**断言与修后语义冲突**，不是实现问题 |

**三条红的逐条定位（都不是实现缺陷，请 r2 QA 按此改探针）**：

1. **G3B-06（断言写死的核验人 id 与探针自己的登录身份矛盾）**
   - 探针 `webLogin()` 用 `lqgadmin` 登录 → `sys_user` 里 `lqgadmin` = **9000000100**（测试管理员）；但断言写的是 `` `valid|${BLOCK}|9000000101` ``（李工）。
   - 实测库内：`row=valid|T-r1l2-E1|9000000100` —— 值本身完全正确（`verify_by` = 点按钮的那个内部账号 = `LoginHelper.getUserId()`）。
   - 也就是说这条在 r1 是「因为根本没落库才红」，修后是「因为 id 写错才红」。建议改成读 `SELECT user_id FROM sys_user WHERE user_name='lqgadmin'` 或直接用 `9000000100`。
2. **G3B-08（这条断言的内容就是「缺陷必须存在」）** —— 断言 `PUT /lqg/embed -> 400` 存在**且** `/verify` 不存在。修好后按定义必红（实测 net 只有一条 `PUT /lqg/embed/{id}/verify -> 200`）。建议删掉，或反转成「恰好一条 `/verify` 且零条普通 PUT」（我 §5.2 的 I145-03 就是这个断言，可直接抄）。
3. **G3B-09（「API 兜底」在修好后变成无害的 no-op）** —— 它先让 UI 核验，再用 `--as staff`（9000000101）打一次 `/verify`；此时该行已是 `valid`，`valid→valid` 被转移表拒 → 库里仍是 `9000000100`，断言期望的 `9000000101` 落空。修好后这条兜底已无意义，建议删掉或改成「改判被转移表拒 + 库里不变」。

> **我没有改 QA 的回归资产一个字节**（`doc/waves/regression/**` 保持原样；重新跑出来的截图 / xlsx 也已 `git checkout --` 还原）。上面三条是「探针需要跟着实现语义更新」，不是「改断言来过关」——原始红/绿记录都在 QA 的 `D3-r1-L2.json` 与本次 `.tmp/D3-probes-after.log` 里，可对照。

---

## §7 重跑的 accept 与回归自查（12 段全绿）

跑法：`bash doc/waves/reports/D3-rework-r1-issue145/accept-runners/rework-accepts.sh`（逐条串行，避免 DB 竞争；每条复用各票既有 accept-runner，与 ticket `run` 字面逐字相同，只去掉沙箱跑不了的 `--fresh-module ruoyi-lqg`）。完整输出 `.tmp/D3-accepts.log`。

| # | 项 | 结果 |
|---|---|---|
| 1 | `EMBED-WEB-001` accept 1（DATA · 导出与模板逐字） | **ACCEPT-1 EXIT=0**（表头 16 列 ×3 段、行数=库 count=5、筛选导出 2 行 / 4 行） |
| 2 | `EMBED-WEB-001` accept 2（MENU · 5310 段 / 接核验接口 / 染色 fixture） | **ACCEPT-2 EXIT=0** |
| 3 | `EMBED-MP-001` accept 1（构建产物 + 内部历史 + 角色 + 经手人） | **ACCEPT-1 EXIT=0** |
| 4 | `EMBED-MODEL-001` accept 1（DDL vs SSOT） | **ACCEPT-1 EXIT=0** |
| 5 | `EMBED-MODEL-001` accept 2（STATE · 转移表 + 唯一性） | **ACCEPT-2 EXIT=0** |
| 6 | `AUTH-EXT-002` accept 1（API · **含 `ExtChokepointContractTest` 4 条**） | **ACCEPT-1 EXIT=0** |
| 7 | `AUTH-EXT-002` accept 2（STATE · 写保护 / 夹带 / 无效重提） | **ACCEPT-2 EXIT=0** |
| 8 | `SAMPLE-HINT-001` accept 1（DATA） | **ACCEPT-1 EXIT=0** |
| 9 | `SAMPLE-EXPORT-001` accept 1（DATA · 两张导出） | **ACCEPT-1 EXIT=0** |
| 10 | `bash doc/waves/regression/D1/verify.sh --skip-build` | **只剩 1 条已知假红**：`L1.1 D1 的 7 支迁移全部记录在案 → 期望 '7'，实际 '14'`（D2/D3 又加了 7 支，脚本里的总数写死 —— 与本票无关，r1 起就在） |
| 11 | `mvn -pl ruoyi-modules/ruoyi-lqg -am test` | **`Tests run: 120, Failures: 0, Errors: 0, Skipped: 0` → BUILD SUCCESS**（`> 0` 满足） |
| 12 | plus-ui `pnpm build:prod` | **BUILD-PROD-RC=0** |

另有：`npx eslint` 改动文件 = **7 条 prettier 报错，与 HEAD 基线逐条同数同位**（全部在本次未触碰的行上）→ 本次改动**零新增 lint 问题**。

### 4.0 · 反 stale 自核（替 `--fresh-module`，未改 `api.sh`）

```
jar mtime                : 2026-09-22 10:13  （backend 无源码改动：本票只改前端）
find ruoyi-lqg/src ruoyi-admin/src -newer jar -type f | wc -l = 0
8081 PID 31342  lsof -p 31342 | grep -c ruoyi-admin.jar = 2
后端日志（本次自起 10:46:49）: Successfully validated 14 migrations / Current version of schema "public": 202609231110 / Started DromaraApplication
8082 : plus-ui dev（vite 6.4.1，从当前源码现编；HMR 日志见 .tmp/D3-rework-plusui.log）
9200 : miniapp H5 dev（QA g3 探针用）
```

---

## §8 WARN 清单

1. **WARN-1（新引入的观感问题，非功能）**：`ServiceException`（业务码 500）失败时，全局拦截器已经 `ElMessage` 弹过一次后端 msg，抽屉的 `catch` 又弹一次 → 页面上是**两条同文案**的 el-message 堆叠（探针实测 `msgNodes=2`）。想消掉只有两条路：① 抽屉 catch 里不再 toast（退回「靠拦截器」，但 code=400 那条只有 el-notification，且 QA 明确要求 `$modal.msgError`）；② 让 `@/utils/request` 的 500 分支不再自动 toast（全局行为，超出本票范围）。**本次按「可见性优先」保留双弹**。
2. **WARN-2（同类症状 · 别的文件，未动）**：缺 `catch` 的提交函数（`try…finally` 只有 finally）会漏未捕获 rejection —— `views/lqg/sample/SampleDrawer.vue`（4 处）、`views/lqg/auth/extuser/index.vue`（2 处）、`views/lqg/auth/staff/index.vue`（3 处）、`views/lqg/auth/unit/index.vue`（4 处）、`views/lqg/embed/index.vue`（2 处）、以及**本抽屉的 `searchSamples()`**（样本远程搜索失败）。它们都不是「先写一个必被拒的请求」，所以**不属本 S0**，留台账；建议后续统一按 `EmbedDrawer` 的 `catch + failText` 收口。
3. **WARN-3（口径留痕）**：核验档里补填的工序 / 染色 / marker **不随核验落库**（契约 `EmbedVerifyBo` 只收三键）。要么接受「核验后点编辑补填」（本次选择，与抽屉提示「之后照常补工序与染色」一致），要么后续由甲方确认后扩 `EmbedVerifyBo` + 改 `doc/api-contract.md:62` —— 那是接口契约变更，不该在返工里偷偷做。
4. **WARN-4（QA 资产需跟改，见 §6）**：`L2-g3-mp-ext.mjs` 的 G3B-06（核验人写死 9000000101，实际登录 9000000100）、G3B-08（断言缺陷存在）、G3B-09（断言需要 API 兜底）三条在修后必红。**r2 QA 必须先按 §6 更新这三条**，否则会把「探针过期」误读成「S0 未修」。
5. **WARN-5（既有 · 非本票）**：`api.sh --fresh-module` 在沙箱恒非 0（`ps` 被禁）；D1 的 `L1.1` flyway 总数写死 `7`（实际 14）。两条 r1 起就在，未动。
6. **WARN-6（流程）**：`doc/waves/reports/D3-rework-r1-issue145/` 是本票新增的**证据目录**（探针 + runner + 4 张截图）；截图只作落盘证据，**构建 / 断言全程没有读图**。

---

## §9 坑与解法（3-5 行）

1. **`el-message` 默认 3s 就消失**：跑完再查 `.el-message` 一定是空的 —— 我第一版探针就是这么把「成功提示」误判成「静默失败」的。解法：`addInitScript` + `MutationObserver` 在节点出现瞬间把文案记进 `window.__lqgToasts`。
2. **业务码 ≠ HTTP 码**：RuoYi 把 `ServiceException` 包在 **HTTP 200** 里（`{"code":400|500,…}`），拦截器对 400 用 `ElNotification`、对 500 用 `ElMessage`，且 400 分支 `reject('error')` 把后端 msg 丢了 —— 所以 `failText()` 必须分「能拿到 msg」和「只剩兜底话术」两条路。
3. **web 登录账号决定 `verify_by`**：`lqgadmin` 是 **9000000100**（测试管理员），`api.sh --as staff` 才是 9000000101（李工）。QA 探针 G3B-06 把这两个混了（§6-1），凡是断言 `verify_by` 的地方都该从库里查登录账号，别写死。
4. **L2-g1-web.mjs 自己不 reseed**：跟 g3 串跑会继承 g3 留下的石蜡块（1001 从 2 块变 3 块）→ `G1E-02` 假红。解法：`reseed.sh --yes` 后**单独**跑 g1。
5. **runner 的相对路径深度**：`doc/waves/reports/<票>/accept-runners/x.sh` 回项目根要 **5 个** `..`（`accept-runners→票→reports→waves→doc→根`），我第一版写 4 个 → 12 段全部 "No such file or directory" 还全打 ALL DONE。解法：`cd` 完立刻断言 `pwd` 或 `[ -d code/plus-ui ]`。

---

## §10 收尾状态

- `git checkout -- code/miniapp/src/pages.json code/plus-ui/.eslintrc-auto-import.json`（dev 起的副作用）+ `git checkout -- doc/waves/regression/D3/L2-shots/**`（还原 QA 的 r1 截图 / xlsx，本票证据只在 `doc/waves/reports/D3-rework-r1-issue145/shots/`）
- `bash doc/verify/reseed.sh --yes` + `bash doc/waves/tools/clean-orphan-accounts.sh --yes`（**注意**：清孤儿账号脚本在 `doc/waves/tools/`，不在 `doc/verify/`）
- 按 PID 关掉本票起的 8081 / 8082 / 9200；确认 8081 / 8082 / 8083 / 8099 / 9200 / 9201 无监听；**8080 / 5432 / 6379 全程未碰**（docker 容器 `lqg-dev-postgres` / `redis` / `minio` 未停）
