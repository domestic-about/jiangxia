# D7 返工单 r1 · S1 缺陷修复报告 —— 文档下载把 `Authorization` 带到了 OSS 预签名直链

- **分支**：`task/D7`（`git branch --show-current` 确认，未切分支、未 push、未合分支）
- **缺陷来源**：D7 QA 门 r1 L2（`doc/waves/qa/D7-r1-L2.json` → `issues[0]`，severity S1，ticket DOC-MP-002）
- **修复文件**：`code/miniapp/src/utils/fileHandoff.ts`（核心）、`code/miniapp/src/components/lqg/DownloadBar.vue`、`code/miniapp/src/pages/ledger/index.vue`、`code/miniapp/src/pages/ledger/export.ts`（仅注释）
- **验收环境**：`bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --mp-port 9204`
  （后端 pid 17763 / 工作台 18393 / 小程序 H5 18646；`-Dhttp.nonProxyHosts=…` 未删；
  `lqg-dev-gotenberg` 全程 running 未停）

---

## 1. 缺陷复述（QA 实测）

两个调用方共用一个 `downloadToTemp(url, header?)`，该函数把「**必须带 `Authorization`**」写成了
**所有调用方**的硬前提（缺头直接 `throw`）——但这个前提只对其中一类 URL 成立：

| 调用方 | URL 是什么 | 该不该带鉴权头 |
|---|---|---|
| **文档下载** `components/lqg/DownloadBar.vue`（DOC-MP-002） | 后端签发的 **OSS 预签名直链** | **不带**（带了被对象存储判「多重认证」） |
| **表格导出** `pages/ledger/index.vue`（SYS-EXPORT-001） | 后端域名上的**鉴权端点** `/mp/int/export/{sheet}` | **必须带**（`Authorization` + `clientid`） |

后果：MinIO/S3 回 **400 `InvalidRequest: request has multiple authentication types`**，
单份下载与合并下载在 H5 上 **0/4 全失败**，页面只有「暂时下不了，请稍后再试」。

QA 的**逐头隔离**（同一浏览器，每条现取新签名链接 + `cache:'no-store'`，避开缓存假 200）：

| 请求头 | MinIO 响应 |
|---|---|
| 不带任何头 | **200** |
| 只带 `clientid` | **200** |
| 只带 `Authorization`（真 token） | **400** `InvalidRequest` |
| 两个都带（= 产品代码现状 `authHeader()`） | **400** |
| 工作台对照：`window.open(res.data.url)` 不带任何头 | **200**（pdf/word content-type 都对） |

⇒ 签名链接本身完全可用；**只有小程序这条链路多带了头**，病灶就是 `Authorization` 这一个头（`clientid` 无害）。

根因那句话（已改正）：`fileHandoff.ts` 文件头原文写
「**导出与文档下载都是鉴权接口（不是 OSS 签名链接）**」—— 正是把实现带偏的描述。

---

## 2. 改了什么

### 2.1 `src/utils/fileHandoff.ts` —— 「要不要鉴权」变成调用方的显式选择

```ts
export interface DownloadOptions {
  /** 只在 requireAuth: true（后端鉴权端点）时需要有 Authorization；OSS 直链不传 */
  header?: Record<string, string>
  /** 默认 false = 直链 / 签名链接，一个头都不带 */
  requireAuth?: boolean
}

export async function downloadToTemp(url: FileUrl, opts?: DownloadOptions): Promise<string> {
  const header = opts?.header
  // 护栏保留：调用方声明了要鉴权却没带头 → 仍然 throw（这条是给导出留的）
  if (opts?.requireAuth && !header?.Authorization) {
    throw new Error('下载缺少鉴权头（调用方声明了 requireAuth，就必须带 Authorization）')
  }
  …
  api({ url: target, header: header ?? {}, … })     // 小程序端 wx.downloadFile
  …  return downloadToBlob(target, header)          // H5 兜底 fetch + blob
}
```

- **旧**：`downloadToTemp(url, header?)` + `if (!header || !header.Authorization) throw`（全局硬前提）。
- **新**：`downloadToTemp(url, opts?)`；守卫条件收窄成 `opts.requireAuth && !header?.Authorization`。
  对「不鉴权」的调用方，`header` 不传 → `{}` → **一个头都不带**；导出的护栏一条没丢。
- 文件头注释：把「两类 URL 都是鉴权接口」这句**错误描述**改成两类 URL 的区别
  （OSS 预签名直链 = 鉴权在 query 串里、不带头；`/mp/int/export/{sheet}` = 后端鉴权端点、必须带
  `Authorization` + `clientid`），并写明 `requireAuth` 由调用方声明。
- 注释里 `Authorization` / `clientid` 字样仍在（ticket acc2 第 5 段 grep 依赖它）。

### 2.2 `src/components/lqg/DownloadBar.vue` —— 下载这条路不再碰鉴权头

```diff
-import { authHeader } from '@/pages/ledger/export'
-    const filePath = await downloadToTemp(url, authHeader())
+    const filePath = await downloadToTemp(url)
```

对 `@/pages/ledger/export` 的 import **已整条删除**（`grep authHeader DownloadBar.vue` 无命中）。
仍走公共段 `downloadToTemp` → `openFile` / `shareFile`（**没有**改成 `window.open` 当主路径；
小程序端仍是 `uni.downloadFile`，`showMenu: true` 与 `shareFileMessage` 原样保留）。

### 2.3 `src/pages/ledger/index.vue` —— 导出显式声明「要鉴权」

```diff
-    const path = await downloadToTemp(exportUrl(sheet.value.key, filters.value), authHeader())
+    const path = await downloadToTemp(exportUrl(sheet.value.key, filters.value), {
+      header: authHeader(),
+      requireAuth: true,
+    })
```

### 2.4 `src/pages/ledger/export.ts` —— 仅注释

`authHeader()` 的文档注释由「导出 / 文档下载要带的头」改成「**导出**要带的头」，
并写明这个头只跟导出走、文档下载不带头（函数实现未动）。

### 2.5 「不退化成两份实现」

公共段仍然**只有一份**：`grep -rn "downloadFile" src/` 的真实调用点只有
`src/utils/fileHandoff.ts`（两个 ticket 调用方都调它）；`openDocument({showMenu: true})`、
`shareFileMessage` 也只在公共段。差异只是**参数**（`requireAuth` / `header`），不是两份实现。

---

## 3. 修复前后对照（真 H5 DOM + 真后端 + 真 MinIO）

探针：`.tmp/rework-r1/download-probe.mjs`（Playwright，require 锚点 = `code/miniapp/package.json`；
截图不落盘、PNG 未读进上下文）。「修复前」是用 `git stash push -- <4 个文件>`
临时还原后跑的真前后对照（stash 只含这 4 个文件，**未动 `pages.json`**；跑完已 `git stash pop`）。

### 3.1 文档下载（内部 staff，H5 9204）

| | 请求头（实际发出） | OSS 响应 | 页面 | 打开 / 发送到微信 |
|---|---|---|---|---|
| **修复前** 单份 Word | `authorization: Bearer eyJ…` + `clientid` | **400** `application/xml` | `.db__hint` = 「暂时下不了，请稍后再试」 | ✗（没到那一步） |
| **修复前** 单份 PDF | `authorization` + `clientid` | **400** `application/xml` | 同上 | ✗ |
| **修复前** 合并 Word | `authorization` + `clientid` | **400** `application/xml` | 同上 | ✗ |
| **修复前** 合并 PDF | `authorization` + `clientid` | **400** `application/xml` | 同上 | ✗ |
| **修复后** 单份 Word | `referer / user-agent / sec-ch-ua*`（**无 authorization、无 clientid**） | **200** `…wordprocessingml.document` | 无失败态 | ✓ `window.open(blob)` |
| **修复后** 单份 PDF | 同上（无鉴权头） | **200** `application/pdf` | 无失败态 | ✓ `window.open(blob)` |
| **修复后** 合并 Word | 同上（无鉴权头） | **200** `…wordprocessingml.document` | 无失败态 | ✓ toast「发送到微信」要在微信里用 |
| **修复后** 合并 PDF | 同上（无鉴权头） | **200** `application/pdf` | 无失败态 | ✓ 同上 |

探针断言：**修复前 12/28**（4 条下载全部失败、16 条关键断言红），**修复后 28/28**。
落盘：`.tmp/rework-r1/download-before.json` / `download-after.json`。

四条链路的后端一步本来就是对的：`GET /mp/int/doc/9000001001/{sample_qc|merged}/download?format=…`
→ `code=200`，`data.url` = `http://127.0.0.1:9000/ruoyi/lqg/doc/…?X-Amz-…`（OSS 预签名直链）。

### 3.2 表格导出（我的 → 内部管理 → 筛「待核验」→ 导出 Excel）

探针：`.tmp/rework-r1/export-probe.mjs`（用 `page.route` 拦下这一发，取真实请求头 + 响应字节）。

| | 请求头 | 响应 | 响应体 |
|---|---|---|---|
| 修复前 | `authorization: Bearer eyJ…` + `clientid: 22b2aecd…` | **200** | **PK**，4118 B（Microsoft OOXML） |
| 修复后 | `authorization: Bearer eyJ…` + `clientid: 22b2aecd…` | **200** | **PK**，4118 B（Microsoft OOXML） |

URL = `/lqg-api/mp/int/export/tissue?verifyStatus=pending`；页面「共 2 条」；
落到「打开 / 发送到微信」actionSheet（`["打开","发送到微信","Cancel"]`）。
探针断言 **11/11**（前后各一次）。落盘：`export-before.json` / `export-after.json`、`export-*.xlsx`。

---

## 4. 票面 accept（逐字重放）

```bash
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
python3 doc/waves/tools/accept-run.py --ticket DOC-MP-002    --run --json .tmp/fix-docmp002.json     --logdir .tmp/fix-docmp002-logs
python3 doc/waves/tools/accept-run.py --ticket SYS-EXPORT-001 --run --json .tmp/fix-sysexport.json   --logdir .tmp/fix-sysexport-logs
```

| 票 | acc1 | acc2 | 结果 |
|---|---|---|---|
| **DOC-MP-002** | ✓ 4.9s（API：构建 + `showMenu: true` + `shareFileMessage` + 组件直连 + 失败态不泄露 + download 单测） | ✓ 6.4s（DATA，NF1：pages 数 == `pdfinfo` 页数 + extD 403） | **2/2** |
| **SYS-EXPORT-001** | ✓ 2.2s（DATA，NF1：四张表逐字对模板 + 行数 + 带筛 + extA 403 + 未知 sheet 400/404） | ✓ 5.8s（API：公共段 + `showMenu: true` + `shareFileMessage` + `Authorization\|clientid` + 两条单测） | **2/2** |

- 只有 NF1（去 `--fresh-module`）被施加，逐条印在日志里，非静默弱化。
- SYS-EXPORT-001 acc1 的真实输出（节选）：
  `✓ 表头 14 列与模板逐字一致；数据 8 行` / `✓ 表头 7 列…1 行` / `✓ 表头 16 列…5 行` /
  `✓ 表头 11 列…7 行` / `✓ 表头 14 列…2 行`（带 `verifyStatus=pending` 那一段 = 2 行）。
- ⚠️ **harness 小记**（不是产品红）：第一次跑时我漏 `export LQG_VERIFY_ENV_FILE`，两条 DATA
  accept（DOC-MP-002 acc2 / SYS-EXPORT-001 acc1）落到 api.sh 默认 `8081` → `exit 2`
  「连不上后端」。导出变量后同两条全绿。下游复跑**先导出这个变量**。

---

## 5. 单测

```bash
cd code/miniapp && pnpm vitest run
```

- `src/pages/doc/download.fixture.spec.ts` → **17 passed**
- `src/pages/ledger/export.spec.ts` → **10 passed**
- 全量：**13 files / 181 tests passed，0 failed**（`pnpm vitest run`，313ms）

`pnpm type-check`：改动文件（`fileHandoff.ts` / `DownloadBar.vue` / `ledger/index.vue` / `export.ts`）
**无新增错误**；输出的报错全部是既有的（`wot-design-uni` 依赖、`UnitGroupPicker.vue`、
`history/index.vue`、`group.fixture.spec.ts`、`export.spec.ts:49` 的 `location` 字段等），与本单无关。

---

## 6. 越界 / WARN

1. **【WARN·断言盲区，未改票面】** QA `issues[1]`（S2 harness）：SYS-EXPORT-001 acc2 的
   「下载带鉴权头」只 `grep -qE 'Authorization|clientid' export.ts fileHandoff.ts`（**词存在**），
   对「哪个调用方把哪个头传给哪个 URL」零覆盖。本单按派单要求**没有改票面**；修完之后这条盲区
   依然存在（若有人删掉 `ledger/index.vue` 里的 `requireAuth: true`/`authHeader()`，acc2 仍会绿）。
   建议（供 D7 门/后续票决策，不要我越界改）：把断言落到调用点，例如
   `grep -q 'requireAuth: true' src/pages/ledger/index.vue` + `grep -A2 'downloadToTemp' src/components/lqg/DownloadBar.vue`
   判「文档下载不带 header」，或给 `downloadToTemp` 加一条单测（`requireAuth:true` 缺头必 throw、
   默认不带头时 fetch 的 headers 为空）。
2. **【WARN·真机未覆盖】** appid / 合法域名未提供（owner 决策），本单只在 H5（owner 指定的验收面）
   实测。机理同上：小程序分支把 `header ?? {}`（现在为空）交给 `wx.downloadFile`，
   MinIO/S3 的判据与服务端无关 → 真机应随 H5 一起好；**要真机确认需 appid 到位后跑一次「下载 PDF」**。
3. **【观察·未改】** `src/components/lqg/AttachmentList.vue` 另有一处**局部** `downloadToTemp` +
   `openDoc`（附件签名链接，本来就不带任何头 → 行为正确，无此缺陷）。它比公共段窄
   （无 `shareFile` / 无 H5 `fetch` 兜底）。不在本返工单范围，未碰。
4. **【观察·非本单】** H5 上 `DownloadSheet` 反复开合时，`DownloadBar` 的 Word/PDF 选择会**跨弹层留存**
   （探针里「合并 Word」那次默认落在上一次选的 PDF，所以探针改成每次显式点格式）。这是既有行为、
   与本单无关，仅记录以免下游误判。
5. **【环境噪声，已还原】** 起 dev server / build 会顺手改写几个 tracked 文件：
   `code/miniapp/src/types/components.d.ts`（掉了 `WdPopup` 一行）与
   `code/plus-ui/.eslintrc-auto-import.json`（plus-ui dev server 重排）。二者都**不是本单改动**，
   收尾已 `git checkout --` 还原。
6. **【有意保留】** `code/miniapp/src/pages.json` 被 H5 dev server 改写（注释里平台顺序
   `MP-WEIXIN || H5` → `H5 || MP-WEIXIN`；tabBar 仍是 `[index, doc, me]`，无语义变化）。
   按派单口径**不 checkout**。

---

## 7. 给下游（D7 门复跑）的说明

1. **修复点只有一处语义**：`downloadToTemp` 的「要不要鉴权头」现在由调用方用 `requireAuth` 声明；
   文档下载路径**一个请求头都不带**。要回归的就是这条：抓 OSS 直链请求，断言**无 `Authorization`**
   且响应 200。
2. **复现「文档下载」的前提**：`qa-up.sh` 的 reseed **不灌 `t_lqg_doc_file`**（默认 `doc_status`
   在 qc 表里是 published，但渲染产物为空 → `GET /mp/int/doc/list` 返回 `total=0`，页面上没有卡片）。
   本轮我是先 `POST /lqg/qc/9000001001/{sample-qc,organoid-qc,score}/unpublish` + `publish`
   （触发渲染，等价于 QA 上一轮「上传真图 + 重新 publish」），等 `t_lqg_doc_file` 全 `done` 再跑探针。
   **accept 重放不受影响**（它们自己 render / 不需要列表）。
3. **探针可直接复用**：`.tmp/rework-r1/download-probe.mjs`、`.tmp/rework-r1/export-probe.mjs`
   （`node .tmp/rework-r1/download-probe.mjs <tag>`）。它们不动任何 tracked 文件、不落截图。
4. **口径替换照旧**：真机 → H5(9204) + mock 登录 + Playwright 真 DOM + 真后端 8094 + 真库 5433；
   Playwright require 锚点 `code/miniapp/package.json`（issue #229）。
5. 本单**未改**票面 accept、**未改** `doc/waves/state.json` 与 `_manifest.json`，**未 push / 未合分支**。

---

## 8. 落盘产物索引

| 文件 | 内容 |
|---|---|
| `.tmp/rework-r1/download-before.json` / `download-after.json` | 下载探针前后对照（请求头 / 响应码 / 页面态 / 是否走到交接步） |
| `.tmp/rework-r1/export-before.json` / `export-after.json` | 导出探针前后对照（Authorization + clientid + 200 + xlsx 魔数） |
| `.tmp/rework-r1/export-before.xlsx` / `export-after.xlsx` | 真 xlsx（`file` = Microsoft OOXML，各 4118 B） |
| `.tmp/fix-docmp002.json` / `.tmp/fix-sysexport.json` + `*-logs/` | 两条票面 accept 的结果与日志 |
| `doc/waves/reports/D7-rework-r1-issue-S1.md` | 本报告 |
