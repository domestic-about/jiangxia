# SYS-EXPORT-001 · 完工报告

- **ticket**：SYS-EXPORT-001（track SYS / phase D7 / size M）—— 小程序 · 内部管理表格页「导出 Excel」：四张表按当前筛选导出，与工作台同一个导出视图，打开或发送到微信
- **status**：**done**
- **accept**：**2/2 绿**（`python3 doc/waves/tools/accept-run.py --ticket SYS-EXPORT-001 --run …` → `通过 2/2`；acc1 归一化 **NF1**，acc2 **零归一化**）
- **分支**：`task/D7`（开工 `git branch --show-current` 确认过；**未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`**）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 22:11:32`（sha256 `75df5108…ca20d`），后端 **PID 4963**（8094，dev profile + `--api-decrypt.enabled=false`）；`find lqg/src admin/src -newer <jar>` **为空**
- **迁移**：**一支都没有**（无 DDL / 无菜单 / 无授权 → 不取号；库里已应用的最大版本仍是 `202609261430`，见 §1）
- **产物**：后端 **3 个新类 + 1 个新契约测试**（11 例）；小程序 **5 个新文件 + 3 处改造**；取证目录 `doc/waves/reports/SYS-EXPORT-001/`
- **★ 口径**：小程序走**本地 Mock**（Kevin 2026-09-22 决策，appid 无法提供）→ **没有做体验版 / 真机档**，验收面 = **H5（`pnpm dev:h5`，`VITE_MOCK_LOGIN=1`）+ Playwright 真 DOM + 真后端 8094 + 真库 5433**。**真机 / 微信里未覆盖**（§9.4）
- **★ 两条已知红（都不是本票实现缺陷，见 §5）**：
  - `DOC-MP-002` accept 1 · **本票「抽公共段」的直接后果**（票面互斥，需主会话放宽两句 grep）
  - `SYS-MP-001` accept 1 · **预先就红**（JSDoc 注释触发一条过宽的选择器正则，与 4 个我没碰过的 HEAD 文件同型）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D7`（全程没切） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `SAMPLE-EXPORT-001`：`sample/export/{SampleExportService,SampleTissueExportVo,SampleOrganoidExportVo}.java` 在盘（`tissueRowsOf` / `organoidRowsOf` 是 public）；`EMBED-WEB-001`：`EmbedExportService.SHEET_NAME` + `rowsOf`；`CRYO-WEB-001`：`CryoExportService.SHEET_NAME` + `rowsOf`；`DOC-MP-002`：`DownloadBar.vue` + `download.ts` + 17 条 fixture 单测；`EMBED-MP-001` / `CRYO-MP-001`：`/mp/int/{embed,cryo}/batch/list` 在盘且本票复用它们的 BO |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260917-04**（导出为内部人员可下载、同一导出视图）· **CR-20260917-05**（表格页挪「我的 → 内部管理」、导出是它底部唯一的按钮）· **CR-20260918-07**（表格页加修改入口，**页底那行小字去掉「修改」二字** → 本票点亮导出时**逐字保持**「核验、冻存取用请到网页工作台」，H5 实测 `noteIsNewWording: true`）· **CR-20260921-08**（视觉方向 A：导出按钮用描边样式、不加辉光，§5.9/§5.8） |
| `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:mp.ledger`（active：④ 底部只有「导出 Excel」，按当前筛选，导出后「打开」或「发送到微信」）· `FLOW:F-SAMPLE-02.step7`（active：按当前筛选导出、与工作台同一个导出视图、外部 403）· `FLOW:F-DOC-02.step4`（active：`wx.downloadFile` → `wx.openDocument(菜单开关)` / 转发到微信）—— 三条逐条对上 |
| 视觉按方向 A（`落地规范.md` §5.8 / §5.9 / §6 本页那一行） | ✅ PASS | 导出按钮保持既有「描边样式、不加辉光」（本票只把 `disabled` 换成 `:disabled="exporting"`）；`fileHandoff.ts` 是纯逻辑、零样式；新代码**零色值字面量** |
| 「本栈已知的坑」表逐条过 | ✅ PASS | 判成败一律 `--bizcode` / `grep -qE '^403'`（若依 HTTP 恒 200）；未知 sheet 走 **404 No endpoint**（不是 400 —— 票面写的是 `^(400|404)`，见 §7 WARN-4） |
| 环境可用（8094 / PG 5433 / Redis 6380 / MinIO 9002-9003 / Gotenberg 3010） | ✅ PASS | 容器全程在跑（`lqg-dev-gotenberg` 未停）；进程起停**只按 PID**；8080 全程为空 |
| ★ 平台能力差异如实标注（任务书 §0.1 ①） | ✅ PASS | §9.4：`shareFileMessage` / `openDocument` 的菜单 / `downloadFile` 合法域名 **H5 上不可验**；能验的（组件 / 纯函数 / 请求 / 鉴权头 / 公共段被两边共用）全部在 H5 + Playwright 上断过 |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

---

## §1 迁移号：本票一支都没加（取号依据）

**没有取号**，没有 `V202609261440__SYS-EXPORT-001-*.sql`。理由：

- 本票只加**四个读端点**（`GET /mp/int/export/{tissue,organoid,embed,cryo}`）：**不建表、不加菜单、不加授权**；
- 类级闸是**既有的** `@SaCheckRole("lqg_internal")`（SAMPLE-MP-001 建的角色，DOC-MP-001/002、EMBED-MP-001、CRYO-MP-001 的 `/mp/int/**` 都在用），`sys_menu` 里不需要任何新行；
- ★ **刻意不用** `@SaCheckPermission("lqg:sample:export")`（工作台那个按钮权限）：绑到它会让「菜单没 seed」把小程序整条导出**静默打成 403**（SAMPLE-EXPORT-001 §7.4 点名的坑）。`@SaCheckRole` 不需要菜单行，所以没有迁移。

**库里已应用的最大版本 = `202609261430`**（实测 `select version from flyway_schema_history order by installed_rank desc limit 1`，见 `machine-evidence.txt` §2）；`spring.flyway.out-of-order=false` → **D7 之后要加迁移的票：版本号必须 > `202609261430`**（DOC-MP-002 §2.1 同一句）。

---

## §2 改了哪些文件（ticket §4.3）

### 2.1 后端 · 新增（`touches` 内：`sys/export/**`）

| 文件 | 行数 | 职责 |
|---|---|---|
| `sys/export/ExcelSheet.java` | 95 | 枚举：`tissue` / `organoid` / `embed` / `cryo` 四个键 + **工作表名从上游常量取**（`SampleExportService.TISSUE_SHEET_NAME` 等，编译期常量）；`resolve(raw)` 未知/空/大小写不符 → `ServiceException(400)` |
| `sys/export/SysExportService.java` | 210 | ① 四个 `exportXxx(query, response)` 分派到上游 service；② `fileNameOf` / `contentDispositionOf`（RFC 5987）纯函数；③ `ContentDispositionResponse`（响应包装，拦住 `ExcelUtil` 对 `Content-Disposition` 的 UUID 改写） |
| `sys/export/MpExportController.java` | 94 | 四个 `@GetMapping` handler（`/tissue` `/organoid` `/embed` `/cryo`），参数分别是 `SampleQueryBo` / `EmbedQueryBo` / `CryoQueryBo`（**与各自 list 端点逐字段同名**），类级 `@SaCheckRole("lqg_internal")` |
| `test/.../sys/export/MpExportContractTest.java` | 411 | **11 例**契约测试（不启 Spring）：① 四键与工作表名；② 上游 16 列表头 ↔ 甲方模板原件第 1 行两侧对账；③ `sys/export` 包里**没有列名**（源码文本扫描 32 个上游列名）；④ 文件名 + RFC 5987 编码；⑤ **真跑一次上游 `EmbedExportService.export`** 断 `Content-Disposition` 是我们的中文名（不是 UUID_）；⑥ 四个 handler 的参数类型 + 路由值；⑦ 类级 `@SaCheckRole` |

**接口清单（本票新增）**

```
GET /mp/int/export/tissue      按当前筛选导出「样本记录信息表」xlsx（14 列）
GET /mp/int/export/organoid    按当前筛选导出「类器官收样记录」xlsx（7 列）
GET /mp/int/export/embed       按当前筛选导出「石蜡包埋送样记录」xlsx（16 列）
GET /mp/int/export/cryo        按当前筛选导出「-80 冻存」xlsx（9 + 2 列）
     · 查询参数 = 对应工作表 list 的筛选参数（与 /mp/int/sample/list、/mp/int/embed/list、
       /mp/int/cryo/batch/list 同一组字段名）
     · 响应 = xlsx 文件流；Content-Disposition 用 RFC 5987 写中文名
       attachment; filename=export.xlsx;filename*=utf-8''<工作表名>-<yyyyMMddHHmmss>.xlsx
     · 外部角色（lqg_external）→ 403；匿名 → 401；不存在的 sheet 路径 → 404 No endpoint
★ 四个端点都**不**复制列定义，一律调上游 service：
  tissue/organoid → SampleExportService#exportTissue/#exportOrganoid
  embed           → EmbedExportService#export
  cryo            → CryoExportService#export
```

★ **`ContentDispositionResponse` 为什么必须有**：`ExcelUtil.exportExcel(rows, sheetName, clazz, response)` 内部第一步 `resetResponse` → `FileUtils.setAttachmentResponseHeader(response, <随机 UUID>_<sheetName>.xlsx)`，会把文件名改写成 `1a2b…_样本记录信息表.xlsx`。ticket §2 要求 `Content-Disposition` 用 RFC 5987 写中文文件名，所以用一个只忽略 `Content-Disposition` / `download-filename` 两个头的 `HttpServletResponseWrapper` 把上游那一次改写拦掉（**不动上游 service 一个字节**，`touches` 之外零改动）。

### 2.2 小程序 · 新增

| 文件 | 行数 | 说明 |
|---|---|---|
| `src/utils/fileHandoff.ts` | 261 | ★★ **公共段**（ticket §0 口径 4）：`downloadToTemp(url, header?)` / `openFile(path, fileType)`（菜单开关传开）/ `shareFile(path, fileName)`；另有 `absoluteUrl` / `isLocalFile` / `isRemoteUrl` 三个纯辅助；`openDocumentType` 从 `pages/doc/download.ts` **re-export**（判据只有一处，DOC-MP-002 的单测钉着它） |
| `src/utils/baseUrl.ts` | 23 | `resolveBaseUrl()` 从 `utils/request.ts` 抽出来（**零 import**）：让 `fileHandoff` 能在 node 单测环境里拿到根地址而**不**把 `uni.*` / `import.meta.env` 拉进加载图；`utils/request.ts` 原样 re-export，既有调用面（`api/ocr.ts`）一字不用改 |
| `src/pages/ledger/export.ts` | 126 | 纯函数层（不 import `uni`）：`exportUrl(sheet, filters)`（拼查询串、空值不带、只有该表有的参数才带）· `authHeader()`（**`Authorization` + `clientid`**）· `exportFileName`（拿不到服务端名时的兜底）· `isExportSheet` / `EXPORT_PATH_PREFIX` |
| `src/pages/ledger/export.spec.ts` | 83 | **10 条用例**（≥3 达标）：整串断言四张表的查询串、空值不带、中文 URL 编码、冻存三页签、未知 sheet |
| `scripts/shots-sys-export-001.mjs` | 203 | H5 取证脚本（跑法见 §8.3）；与 `shots-doc-mp002.mjs` 同款约定 |

### 2.3 小程序 · 改造（三处）

| 文件 | 改动 |
|---|---|
| `src/components/lqg/DownloadBar.vue` | **删掉本地的 `uni.downloadFile` / `openDocument` / `shareFileMessage` 三处实现 + `declare const window`**（−84 行），改为 `import { downloadToTemp, openFile, shareFile } from '@/utils/fileHandoff'`；`run()` 里 `downloadToTemp(url, authHeader())` → `openFile(filePath, openDocumentType(fileName))` / `shareFile(filePath, fileName)`；只保留「合并件生成中 / 轮询 / 文件名 / 失败一句人话 / H5 一步都下不下来时的浏览器兜底」。**行为等效**：平台调用代码整体搬到公共段（不是复制一份） |
| `src/pages/ledger/index.vue` | ① `exportNotYet`（占位 toast）→ **真 `exportExcel()`**：按当前筛选 → `downloadToTemp(exportUrl(...), authHeader())` → `uni.showActionSheet(['打开','发送到微信'])` → 公共段；② 导出中显示「正在导出…」+ `:disabled="exporting"`；③ `INTERNAL_ADMIN_NOTE` **保持**「核验、冻存取用请到网页工作台」（CR-20260918-07，未改回旧版） |
| `src/utils/request.ts` | `resolveBaseUrl` 实现挪到 `utils/baseUrl.ts`（顶部 `export { resolveBaseUrl }` re-export）——**调用面零变化**，只是把根地址判据放到一个零依赖模块里 |

### 2.4 越出 `touches` 的改动（逐条）

| # | 文件 | 为什么必须动 | 越界程度 |
|---|---|---|---|
| 1 | `code/miniapp/src/utils/baseUrl.ts`（新） | `fileHandoff.ts` 在 `touches` 里，但它要用「根地址」而根地址的判据长在 `utils/request.ts`（不在 `touches`）。直接 import 会把 `uni` / `import.meta.env` 拉进 node 单测加载图 | 极轻：**纯新增**，内容是从 `request.ts` 原样搬出的一个函数 |
| 2 | `code/miniapp/src/utils/request.ts` | 同上（把函数搬走 + re-export；**既有行为一字未改**） | 轻：−25/+18 行，全是这次搬迁 |
| 3 | `code/miniapp/src/pages/doc/download.ts` | **没改**（只被 `fileHandoff.ts` re-export `openDocumentType`，`git status` 里没有它） | 无 |
| 4 | `code/miniapp/scripts/shots-sys-export-001.mjs`（新） | 本票的 H5 取证脚本；与 SYS-MP-001 / DOC-MP-001 / DOC-MP-002 的 `scripts/shots-*.mjs` 同款约定（`touches` 里没有 scripts 目录，按惯例归本票） | 无（脚本） |
| 5 | `doc/waves/reports/SYS-EXPORT-001/**` | 本票报告与取证 | 无 |

**没有动**：`doc/api-contract.md`（见 §6）· `SampleExportService` / `SampleExportController` / `EmbedExportService` / `CryoExportService` / `*ExportVo`（**以导出为单位的 0 字节**）· `SampleQueryService` / `EmbedQueryService` / `CryoQueryService` · `SampleQueryBo` / `EmbedQueryBo` / `CryoQueryBo` / `PageQuery` · 任何 `*Mapper` · `ext/**` · `pages/ledger/{sheets,columns}.ts` · `components/lqg/{DocGroupCard,DownloadSheet,AttachmentList,ThumbStrip,PageImageViewer,DocTabs}.vue` · `pages/doc/{preview,download}.ts` · 根 pom / 模块 pom / `application*.yml`（**`nonProxyHosts` 那条 JVM 参数没删**）· `doc/verify/**` 只读区（`seed/` / `gen_seed.py` / `api.sh` / `fixtures/` / `reseed.sh` / `db.py` / `xlsx_header.py`，`git status` 全空）· `doc/waves/state.json` / `_manifest.json` · `code/miniapp/src/pages.json`（**本次 H5 dev 没改写它**）· `_input/` / `doc/change-log.md` / `doc/requirements.yaml` / `doc/authority/**`。

---

## §3 accept 逐条 ✅ / ❌ + 关键输出（ticket §4.2）

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket SYS-EXPORT-001 --run \
      --json .tmp/sys-export-accept.json --logdir .tmp/sys-export-accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ SYS-EXPORT-001 acc1 [DATA] 小程序导出的四个文件与甲方模板原件逐字对表头、行数与库内独立计数一致；带筛选只出筛选结果；外部角色 403、未知工作表被拒 (1.9s)
  ✓ SYS-EXPORT-001 acc2 [API]  构建是本次产物；导出与文档下载共用同一段「打开 / 发送到微信」；下载带鉴权头；拼查询串的纯函数过单测；抽公共函数后文档下载的单测仍过 (5.9s)
[run] 通过 2/2
```

`.tmp/sys-export-accept.json`：acc1 `nf: ["NF1"]`（§5.0）；acc2 `nf: []`（**零归一化**）。

### 3.1 accept 1 · DATA —— ✅（逐段重放：`accept-evidence-1.txt`）

| 段 | 命令要点 | 真实输出 |
|---|---|---|
| 1 | `reseed --yes` + `rm -f /tmp/lqg-mpx-*.xlsx` | 静默（先删旧文件，读到上一次导出的骗不过去） |
| 2 | `GET /mp/int/export/tissue` | **4619 bytes**，`PK` 头（xlsx zip） |
| 3 | `xlsx_header.py --rows $(db tissue count) --find 内部编号=T-hli01 --expect 供体姓名=测试供体甲,有无固定=有` | **`✓ 表头 14 列与模板逐字一致；数据 8 行`** |
| 4 | 同上行数来源 = `python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND sample_kind='tissue'"` | **库内独立计数 = 8**（两边相等；★「有无固定 = 有」不是 Y） |
| 5 | `GET /mp/int/export/organoid` | 3905 bytes |
| 6 | `xlsx_header.py --rows 1`（模板 `类器官收样记录模板.xlsx`） | **`✓ 表头 7 列与模板逐字一致；数据 1 行`** |
| 7 | `GET /mp/int/export/embed` | 4431 bytes |
| 8 | `--rows $(db embed JOIN sample count)` | **库内独立计数 = 5 → `✓ 表头 16 列与模板逐字一致；数据 5 行`** |
| 9 | `GET /mp/int/export/cryo` | 4484 bytes |
| 10 | `--extra "代数,当前剩余/支" --rows 7 --find 冻存样品=T-hli01-GZ-N-P2-EM2-2e5 --expect 冻存数量/支=8,当前剩余/支=6` | **`✓ 表头 11 列与模板逐字一致；数据 7 行`**（9 列模板 + 追加 2 列；初始 8 / 剩余 6 逐格对） |
| 11 | `GET '/mp/int/export/tissue?verifyStatus=pending'` | 4118 bytes |
| 12 | `xlsx_header.py --rows 2` | **`✓ 表头 14 列与模板逐字一致；数据 2 行`** ← ★「带筛选只出筛选结果」 |
| 13 | `--as extA --bizcode GET /mp/int/export/tissue \| grep -qE '^403'` | **`403  没有访问权限，请联系管理员授权`** |
| 14 | `--as staff --bizcode GET /mp/int/export/qc \| grep -qE '^(400\|404)'` | **`404  No endpoint GET /mp/int/export/qc.`** |

**★ 逐字对的是甲方模板原件**（`_input/templates/*.xlsx`，只读区），不是自己那份导出：`xlsx_header.py` 把导出文件第 1 行与**模板原件**第 1 行逐字按序比。四个文件的行数是**两个独立来源**：导出文件 + 直连库计数（`db.py`）。

### 3.2 accept 2 · API —— ✅（逐段重放：`accept-evidence-2.txt`）

```
$ rm -rf dist/build/mp-weixin && pnpm build:mp-weixin        → exit 0（DONE Build complete.）
$ test -f dist/build/mp-weixin/pages/ledger/index.js         → 存在（4679 B，sha256 bd93a307…）
$ grep -q 'fileHandoff' src/components/lqg/DownloadBar.vue   → 命中 5 行
$ grep -q 'fileHandoff' src/pages/ledger/index.vue           → 命中 2 行
$ grep -qE 'showMenu:[[:space:]]*true' src/utils/fileHandoff.ts → 命中 204 行（真代码唯一一处）
$ grep -q 'shareFileMessage' src/utils/fileHandoff.ts        → 命中（真代码唯一一处）
$ grep -qE 'Authorization|clientid' src/pages/ledger/export.ts src/utils/fileHandoff.ts → 命中
$ pnpm vitest run src/pages/ledger/export.spec.ts --reporter=json → {"numPassedTests":10,"numFailedTests":0} → jq -e → true
$ pnpm vitest run src/pages/doc/download.fixture.spec.ts --reporter=json → {"numPassedTests":17,"numFailedTests":0} → jq -e → true
```

★ 「抽公共函数后文档下载的单测仍过」= 最后一段 **17/17**（DOC-MP-002 的原样 fixture 单测，我一个字没改它）。
★ 全量 vitest：**181 / 181 passed, 0 failed**（`vitest-all.json`）。
★ Java：`ruoyi-lqg` 全模块 **`Tests run: 274, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（本票新增 11 例，见 `java-test-transcript.txt`）。

### 3.3 accept 的票面缺陷：**本票没有发现**（一条都没改票面）

`sheet` 四个取值、`/mp/int/export/{sheet}` 路径、`--find` / `--expect` 的格值与 seed 完全对得上；`--rows 7`（cryo）与 `--rows "$(db embed JOIN sample)"`（=5）实测都成立（README 的 seed 速查把 embed 写成 6 行，是**文档口径**问题不是票面错，见 §7 WARN-5）。**唯一需要解释的是 acc1 的 NF1**（§5.0）与**两条上游红了**（§5，都不是票面错）。

### 3.4 counterfeit 逐条排掉（`counterfeit-transcript.txt`：改坏真文件 → 跑真断言 → 红 → 还原 → 绿，**12/12**）

| 探针 | 票面 counterfeit | 怎么改坏 | 红了没 |
|---|---|---|---|
| 1 | 「表格导出另写了一套 downloadFile + openDocument、没带 showMenu」 | 把 `ledger/index.vue` 对 `fileHandoff` 的 import 与调用**整行删掉**（连注释一起），换成自写的 `downloadFile` + `openDocument` | ✅ 共用公共段的 4 条 grep 红 |
| 2 | 同上（另一侧：「抽公共段时把文档下载改坏」） | 把 `DownloadBar.vue` 的公共段 import 与三处调用整行删掉，换成自写替身 | ✅ 红 |
| 3 | 「downloadFile 没带 Authorization」 | 两个调用方都改成 `downloadToTemp(url)`（**不带 header**） | ✅ ★ 票面那句 `grep -qE 'Authorization\|clientid'` **抓不到**这种改坏（它只看文件里有没有这两个词）；本票加了**结构性断言**（`grep -qE 'downloadToTemp\([^,)]*\)'` → 任何不带 header 的调用立刻红）+ 公共段**运行时守卫**（`!header.Authorization` 直接抛）；探针 3 抓到了（见 §7 WARN-2） |
| 4 | 「忽略筛选参数、永远导全量 → 带 `verifyStatus=pending` 那段行数不是 2」 | `exportUrl` 里插一句「直接 return 空串」 | ✅ 单测 **4 条失败** → 还原 10 条通过 |
| 5 | 「没带 showMenu → 用户打开了 Excel 却没有保存 / 转发入口」 | 公共段里 `showMenu: true` → `false` | ✅ 红 → 还原绿（★ 本票把 `fileHandoff.ts` 里**注释中的同字面量全改写成中文描述**，这个 grep 只可能命中真代码 —— DOC-MP-002 WARN-3 那条坑） |
| 6 | 「抽 fileHandoff 时把文档下载改坏 → 最后一段 DOC-MP-002 的单测红」 | 把 `download.ts` 的 `MERGED_FILE_BASE` 从「质控文档（合并）」改回「质控文档合并件」 | ✅ DOC-MP-002 单测 **1 条失败** → 还原 17 条通过 |

---

## §4 ★ 「公共段」的实现与两侧接线（ticket §0 口径 4 + Accept 2 核心）

**一份实现、两个调用方**（`grep -rn "uni.downloadFile\|uni.openDocument\|uni.shareFileMessage" src` 只剩公共段与附件那条独立功能）：

```
src/utils/fileHandoff.ts
  downloadToTemp(url, header?)   ← 鉴权头必带（运行时守卫）；小程序走 uni.downloadFile，
                                    H5 走 fetch + blob（uni-h5 的 downloadFile 不认相对路径 / 不吃鉴权头）
  openFile(path, fileType)       ← 菜单开关传开（Accept 2 第 3 段 grep）；H5 退化成浏览器打开
  shareFile(path, fileName)      ← 小程序专属；H5 给一句人话「「发送到微信」要在微信里用」
  absoluteUrl(url) / isLocalFile / isRemoteUrl
  export { openDocumentType } from '@/pages/doc/download'   ← 判据只有一处（DOC-MP-002 单测钉着）
     ▲                                            ▲
     │ import                                     │ import
components/lqg/DownloadBar.vue              pages/ledger/index.vue
  downloadToTemp(url, authHeader())            downloadToTemp(exportUrl(sheet, filters), authHeader())
  openFile(filePath, openDocumentType(name))   → showActionSheet(['打开','发送到微信'])
  shareFile(filePath, fileName)                  → openFile(...) / shareFile(...)
```

- **下载带鉴权头**：两处都传 `authHeader()`（`pages/ledger/export.ts` 的 `Authorization: Bearer <token>` + `clientid`）。H5 实测请求头原样记录在 `shots-evidence.json`。
- **根地址只有一处**：`utils/baseUrl.ts#resolveBaseUrl`（H5 dev = vite 代理前缀 `/lqg-api`；小程序 / 真机 = `VITE_SERVER_BASEURL`），`utils/request.ts` re-export 保持既有调用面。H5 实测导出请求打到 `http://127.0.0.1:9204/lqg-api/mp/int/export/tissue?verifyStatus=pending` → 200 + `spreadsheetml`。
- ★ **不改上游 service**：四张表的表头 / 列序 / 格式化在「分派 + 出流 + 文件名」这一层**一个字都没有**；`MpExportContractTest#sysExportPackageOwnsNoColumnNames` 把 32 个上游列名逐个扫 `sys/export` 的源码文本（改一处就红）。

---

## §5 ★ 两条上游红（**逐条给证据；都不是本票实现缺陷，请主会话裁定**）

### 5.1 `DOC-MP-002` accept 1 —— **本票「抽公共段」的直接后果**（票面互斥，**必须放宽两句 grep**）

```
$ python3 doc/waves/tools/accept-run.py --ticket DOC-MP-002 --run …
  ✓ DOC-MP-002 acc2 [DATA] …
  ✗ DOC-MP-002 acc1 [API] … → exit 1
[run] 通过 1/2 · ⛔ 1 条未登记的红
```

逐段定位（`regression-transcript.txt` + 手工逐段）：

```
$ grep -q 'previewImage' src/components/lqg/PageImageViewer.vue            → rc=0 ✓
$ grep -q 'previewImage' src/components/lqg/ThumbStrip.vue                 → rc=0 ✓
$ grep -qE 'showMenu:[[:space:]]*true' src/components/lqg/DownloadBar.vue  → rc=1 ✗  ← 就这两段
$ grep -q 'shareFileMessage' src/components/lqg/DownloadBar.vue            → rc=1 ✗
$ grep -c "@/components/lqg/.*\.vue" src/pages/doc/preview.vue             → 7 ✓
$ grep -q '@/components/lqg/DownloadSheet.vue' DocGroupCard.vue            → rc=0 ✓
$ grep -q '@/components/lqg/DownloadBar.vue' DownloadSheet.vue             → rc=0 ✓
$ ! grep -nE 'errorMsg|error_msg' src/pages/doc/preview.vue                → rc=0 ✓
$ pnpm vitest run src/pages/doc/download.fixture.spec.ts                   → 17/17 ✓
```

**为什么必然红**：`DOC-MP-002` acc1 的两句 grep 写死了「平台调用必须出现在 `DownloadBar.vue` 里」（它是按当时的实现写的）；而 `SYS-EXPORT-001` 的 §2 / §0.1-③ **强制**把这一段抽到 `src/utils/fileHandoff.ts` 且**禁止复制两份**。**任何满足 SYS-EXPORT-001 的实现都必然打红这两句** —— 两条票面不可同时满足。

**平台能力没有丢**（这是关键）：

```
$ grep -nE 'showMenu:[[:space:]]*true' src/utils/fileHandoff.ts   → 204: showMenu: true,
$ grep -n 'uni.shareFileMessage' src/utils/fileHandoff.ts         → 232: const api = (uni as any).shareFileMessage
$ grep -n 'uni.openDocument' src/utils/fileHandoff.ts             → 189: const api = (uni as any).openDocument
$ MP 产物复核：dist/build/mp-weixin/utils/fileHandoff.js → showMenu:!0 · shareFileMessage · downloadFile 都在
$ DownloadBar.vue 的 openFile 调用：openFile(filePath, openDocumentType(fileName)) → 平台调用落到同一个函数
```

**建议的最小修法（1 行改两处路径，我只报不改）**：

```
- grep -qE 'showMenu:[[:space:]]*true' src/components/lqg/DownloadBar.vue && grep -q 'shareFileMessage' src/components/lqg/DownloadBar.vue &&
+ grep -qE 'showMenu:[[:space:]]*true' src/utils/fileHandoff.ts && grep -q 'shareFileMessage' src/utils/fileHandoff.ts &&
```

语义等价且**更硬**：改完「平台调用恰好一处」这件事仍然被钉着（只是从 `DownloadBar.vue` 挪到了公共段 —— 那正是 SYS-EXPORT-001 要求的落点）。**没有改 `doc/tickets/DOC-MP-002/prompt.md`**（需求层资产，不在 `touches`，由主会话 / ① 处理）。

### 5.2 `SYS-MP-001` accept 1 —— **预先就红**（与我的改动无关；JSDoc 注释触发一条过宽正则）

```
$ python3 doc/waves/tools/accept-run.py --ticket SYS-MP-001 --run …
  ✗ SYS-MP-001 acc1 [API] … → exit 1 ；acc2 / acc3 ✓ ×2
[run] 通过 2/3 · ⛔ 1 条未登记的红
```

逐段定位（acc1 共 5 段，只有第 3 段红）：

```
$ jq -e '[.tabBar.list[].text] == ["首页","文档","我的"]' app.json                       → true ✓
$ ! grep -rnE "^import \{[^}]*\} from '@/components/biz'" src --include=*.vue --include=*.ts | grep -v 'import type' | grep -q .  → rc=0 ✓
$ ! grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.vue --include=*.scss | grep -v '^src/uni.scss' | grep -q .                → rc=1 ✗
$ ! grep -rq 'mock:ext' dist/build/mp-weixin                                              → rc=0 ✓
```

**为什么是预先就红**：那条正则是给「裸 `*` CSS 选择器、`:not()`」写的（模板红线 §8.4：小程序 wxss 不支持），但 `(^|[ ,{])\*[ ,{]` 会命中**任何 JSDoc 注释的中点行**（` * 文字`，前面是空格、后面空一个格）。**HEAD 上（不含本票任何文件）就已经有 4 个文件命中**：

```
$ git show HEAD:code/miniapp/src/components/lqg/OcrBar.vue       | grep -cE ':not\(|(^|[ ,{])\*[ ,{]' → 6   ← 我没碰过
$ git show HEAD:code/miniapp/src/components/lqg/EmbedCard.vue    | … → 2   ← 我没碰过
$ git show HEAD:code/miniapp/src/components/lqg/AttachmentList.vue | … → 3  ← 我没碰过
$ git show HEAD:code/miniapp/src/components/lqg/ThumbStrip.vue   | … → 4   ← 我没碰过
$ git show HEAD:code/miniapp/src/pages/ledger/index.vue          | … → 2   ← 本票开工前的既有注释
$ 全量：src/**/*.{vue,scss} 命中 67 行，全是注释（grep 无一行是真样式）
```

`SYS-MP-001` 的 `state.json` 记着它 done 时 acc1 是绿的 —— 那时 `OcrBar.vue` / `ThumbStrip.vue` 等（OCR-MP-001 / DOC-MP-002 的产物）**还不存在**；是后续 ticket 加 JSDoc 把它顶红的（既有技术债，不是我的引入）。本票**没有**为了迎合它改掉自己 `sys/export` 之外的任何注释 —— 我的新文件里的 ` * ` 行与上游风格完全一致（也命中），把上游 4 个文件放着不改、只改自己的，那是假装通过。

**建议修法（我只报不改）**：把该段收窄成只扫**样式块**，例如 `grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.scss` + 对 `.vue` 只取 `<style>` 块；或简单加 `| grep -vE '^\s*[0-9]+:\s*\*'` 排除注释行。

---

## §5.0 关于 NF1（本 agent 沙箱限制，既有 WARN 不重复计数）

acc1 的 `--fresh-module ruoyi-lqg` 被 accept-run 去掉（`doc/verify/api.sh` 用 `ps -o lstart=`，本沙箱 **`/bin/ps: Operation not permitted`** → `date -d` 在 macOS 不存在 → 带它恒 exit 2；**连续 12+ 张命中的既有 WARN**）。**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边（`machine-evidence.txt` §1）：

```
jar mtime              : 2026-09-22 22:11:32
jar sha256             : 75df51080789c1b4fc93b6d3bee8a9625c3a31bb64e30e8b5c686b67a76ca20d
lqg src newer than jar : []          ← find ruoyi-modules/ruoyi-lqg/src -newer <jar> 为空
admin src newer        : []          ← find ruoyi-admin/src -newer <jar> 为空
8094 listener          : PID 4963
该 PID 持有 jar         : lsof -p 4963 | grep -c ruoyi-admin.jar → 2
8080（Kevin）           : []          ← 全程没碰
嵌套 jar               : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 含 sys/export/ 9 个条目
Flyway 启动            : 本票没加迁移（最大版本仍 202609261430）
```

Maven 实跑按规矩带了 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`；两条 `run` 本身都没有 Maven 行。

---

## §6 与 `doc/api-contract.md` 的一致性（逐行核过）

| 契约行 | 契约写的 | 实现 | 判断 |
|---|---|---|---|
| 第 55 行 `GET /mp/int/export/{sheet}` | `sheet ∈ tissue \| organoid \| embed \| cryo`；查询参数 = 对应工作表 list 的筛选参数；xlsx 文件流；`Content-Disposition` 带中文文件名；表头列序与 `/lqg/sample/export/*`、`/lqg/embed/export`、`/lqg/cryo/batch/export` **同一个导出视图**；`wx.downloadFile({url, header})` 带 `Authorization` 与 `clientid`；外部角色 403 | 逐条对齐：四个 handler 各收对应 BO；表头列序来自上游 service；`Content-Disposition` = RFC 5987 中文名；`fileHandoff.downloadToTemp` 收 header；`@SaCheckRole("lqg_internal")` → 403 | ✅ **一个字节没改契约**（无 doc-drift） |
| 第 55 行「外部角色 403」 | 403 | 实测 extA → `403`；anon → `401`（未认证，契约通用段的口径） | ✅ |
| 契约没写「未知 sheet 的码」 | —— | 不存在的路径 → Spring 的 `404 No endpoint`（若依把它包进响应体、HTTP 200）；枚举里的未知值 → 400 | ⚠️ **契约缺一行**（§7 WARN-4）：建议把「未知 sheet → 404（路径不匹配）/ 空路径同理」补进第 55 行 |
| 契约没写文件名的时间段格式 | 例子 `样本记录信息表-20260918.xlsx` | 本实现 `样本记录信息表-20260918102030.xlsx`（`yyyyMMddHHmmss`，带时分秒：一天导两次不互相覆盖） | ⚠️ §7 WARN-3（要不要退成只到日，改 `SysExportService.FILE_TIME_PATTERN` 一处 + 前端 `exportFileName` 一处） |

---

## §7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | accept-conflict | ★ **`DOC-MP-002` accept 1 与本票 §2/§0.1-③ 不可同时满足**：前者的两句 grep 把 `showMenu: true` / `shareFileMessage` 钉在 `src/components/lqg/DownloadBar.vue`，后者**强制**把这段抽到 `src/utils/fileHandoff.ts` 且禁止复制两份。实测 `grep … DownloadBar.vue` **rc=1**。 | 见 §5.1。**平台能力没丢**（`fileHandoff.ts` 里真代码唯一一处 + MP 产物 `showMenu:!0`）。建议最小修法：把那两句 grep 的文件参数改成 `src/utils/fileHandoff.ts`（语义等价且更硬）。**没有改票面**（需求层资产）。 |
| WARN-2 | **S2** | accept-strength | ★ `SYS-EXPORT-001` accept 2 第 5 段 `grep -qE 'Authorization\|clientid' src/pages/ledger/export.ts src/utils/fileHandoff.ts` 是**文件级**断言：把真代码里的鉴权头**删掉**、只要文件里还有这两个词（注释 / 别处），grep 照样绿。 | 探针 3 实测：票面那句**抓不到**「`downloadToTemp(url)` 不带 header」。本票加了两道结构性防线：① 公共段**运行时守卫**（`!header.Authorization` → 抛「下载缺少鉴权头」）；② 探针脚本里的**结构性断言**（`grep -qE 'downloadToTemp\([^,)]*\)'`）。建议票面把它改成断言**调用点带参**（如 `grep -q 'downloadToTemp(url, authHeader())'` + `grep -q 'downloadToTemp(exportUrl'`）。 |
| WARN-3 | **S3** | clarify | 导出文件名的时间段格式没被任何 accept 钉住：契约第 55 行的例子只到日（`样本记录信息表-20260918.xlsx`），本实现到秒（`…-20260918102030.xlsx`）。 | 见 §6。甲方若要「只到日」，改 `SysExportService.FILE_TIME_PATTERN` + `export.ts#exportFileName` 各一处（两侧必须同改）。 |
| WARN-4 | **S3** | contract-drift | 契约第 55 行没写「未知 `sheet` 给什么码」。实测不存在的路径 → **404 No endpoint**（若依包进响应体、HTTP 200）；`ExcelSheet.resolve` 的未知值 → 400。 | 本票**没改契约**。建议补一句「未知 sheet → 404（不匹配任何 handler）；路径缺 sheet → 404」，免得下游把 400 当唯一期望。票面 acc1 最后一段写的是 `^(400\|404)`，与现状相符。 |
| WARN-5 | **S3** | seed-doc-drift | `doc/verify/README.md` 的 seed 速查把石蜡包埋写成 **6 行**（`T-E01-1…2004` + `2006`），实际**未删且所挂样本未删 = 5**（1008 的 2005 软删；README 自己下面一行写着 2005 是软删）。accept 用的是 `db.py` 直连计数（5），所以票面是对的。 | 只读区（`doc/verify/README.md` 不在本票 `touches`），**没改**。建议主会话顺手把「石蜡包埋」那段的计数改准（`→ 5 条`），避免下游把 6 当期望。 |
| WARN-6 | **S3** | ticket-drift | `touches` 里没有 `code/miniapp/src/utils/baseUrl.ts` / `request.ts`，但 `fileHandoff.ts`（在 `touches` 里）需要「根地址的判据」，而它长在 `request.ts`。 | 本票**纯新增** `baseUrl.ts`（内容是从 `request.ts` 原样搬出的一个函数）+ `request.ts` 里 re-export（行为零变化）。方案同上游 WARN-1：把「根地址所在文件」写进 `touches`，或把根地址判据一开始就放在零依赖模块里。 |
| WARN-7 | **S3** | harness（既有，本票不重复计数） | `accept-run` 对 acc1 施加 **NF1**（去掉 `--fresh-module ruoyi-lqg`）+ Maven 三参数 NF2；`/bin/ps` 在本沙箱恒被禁。 | 连续 12+ 张命中。本票用等价证据覆盖（§5.0）。方案同上游：守卫换成 `lsof` / 日志 mtime 的兼容写法。 |
| WARN-8 | **S3** | release-blocker（口径未定） | ★ `downloadFile` 的**合法域名没配也验不了**（Mock 口径无 appid）：真机上「打开 / 发送到微信」是否可用**未验证**。 | 交 SYS-RELEASE-001。DOC-MP-002 §7.1 已给两条路（① 把 OSS 域名配进 downloadFile 合法域名；② 后端反代签名链接）。**本票新增一条更简单的观察**：导出**不是** OSS 签名链接、就是自己的后端域名（`VITE_SERVER_BASEURL`）→ 只要把**后端域名**配进 downloadFile 合法域名，导出这一路就通了（文档下载那一路仍看 OSS）。 |
| WARN-9 | **S3** | leftover（既有，本票不重复计数） | `code/plus-ui/.eslintrc-auto-import.json` 会被 `qa-up.sh` 起的 plus-ui dev server 重写（删掉 4 行 Element Plus 条目）。 | 本票收工已 `git checkout --` 还原（`git status` 里没有它）。不是本票产物。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 恒挂 + Maven 三参数、`api.sh` token 缓存只看 mtime、`db.py` 只读却 exit 0、`updateById` 忽略 null、`PageQuery` 只有两参构造、跨域补号的 Flyway 坑、`@SaCheckPermission` 缺 `sys_menu` 行是 403、微信开发者工具在本沙箱跑不通、`pnpm type-check` 被 wot 自身类型错误打红。

---

## §8 给下游的坑（D7 QA 门 / SYS-MANUAL-001）

1. ★★ **导出与文档下载共用 `src/utils/fileHandoff.ts`**：以后任何人要改「打开 / 发送到微信 / 下载」的行为，**只改公共段一处**。`grep -rn "uni.downloadFile\|uni.openDocument\|uni.shareFileMessage" src` 应当只剩公共段与 `AttachmentList.vue`（附件是另一条功能）——多一处就是又一次「两份平台实现」。
2. ★ **公共段要求调用方传 `Authorization`**：`downloadToTemp` 没有 `header.Authorization` 会**直接抛**（不是静默 401）。新增第三个调用方时别忘 `authHeader()`（`@/pages/ledger/export`）。
3. ★ **`uni-h5` 的 `downloadFile` 不认相对路径、也不吃鉴权头**：公共段在 H5 走 `fetch` + `blob:`，所以拿到的是 `blob:` 地址（小程序端是 `wxfile://…`）。**改公共段时别把 `absoluteUrl()` 那一步删掉** —— 删了 H5 会拿回一页 517 字节的 HTML（本票实测踩过，`shots-evidence.json` 里 `bytes: 0` + `contentType: text/html` 就是那个形态）。
4. ★ **四张表的表头 / 列序一个字节都不在 `sys/export` 里**：全在 `SampleExportService` / `EmbedExportService` / `CryoExportService`。`MpExportContractTest#sysExportPackageOwnsNoColumnNames` 会扫源码文本；要加列就改上游（并同步改甲方模板对账的 accept）。
5. ★ **文件名不是 `ExcelUtil` 给的**：`ContentDispositionResponse` 会拦住上游那一次 `setHeader("Content-disposition", <UUID>_…xlsx)`。有人把这一层删掉，文件名会变成随机 UUID 前缀（单测 `exportKeepsOurChineseFileName` 会红）。
6. **`@SaCheckRole` 不是 `@SaCheckPermission`**：`/mp/int/**` 是角色维度（`lqg_internal`），不需要 `sys_menu` 行 → 本票零迁移。要改成按钮权限的话，**先** seed `sys_menu`（否则静默全 403，SAMPLE-EXPORT-001 §7.4）。
7. **H5 取证的三个坑**（DOC-MP-001 §7.12 同款）：① cwd 必须是 `code/miniapp`（Playwright 的 require 锚点是本包 `package.json`，issue #229）；② 脚本把 PNG 只写盘、断言值打 stdout（**别把 PNG 读进上下文**）；③ 读 Playwright 的 `response.body()` 常给 0（流已被消费）——用 `URL.createObjectURL` 拦一道拿 blob 的真实字节数（本票脚本 `blobEvidence()` 的做法，tissue 带筛选那次 = **4117 B**，与 `api.sh` 的 4118 B 同量级）。
8. **D7 QA 门要注意的两条既有红**：`SYS-MP-001` acc1（§5.2，预先就红，与 D7 无关）与 `DOC-MP-002` acc1（§5.1，本票抽公共段的必然结果）——两条都已在本报告里给了逐段证据与建议修法。

### 8.3 跑法（复现本报告的全部证据）

```bash
# 1) 环境（后端 8094 / 工作台 8093 / 小程序 H5 9204）
bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --mp-port 9204
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"

# 2) accept（2/2）
python3 doc/waves/tools/accept-run.py --ticket SYS-EXPORT-001 --run \
  --json .tmp/sys-export-accept.json --logdir .tmp/sys-export-accept-logs

# 3) accept 1 / 2 的逐段重放
bash /tmp/acc1-evidence.sh                                     # 见 accept-evidence-1.txt
# （accept-evidence-2.txt 由报告生成时逐段重放写入，命令都印在文件里）

# 4) counterfeit 探针（改坏→红→还原→绿，12/12）
bash doc/waves/reports/SYS-EXPORT-001/sysexp001-counterfeit-probes.sh

# 5) H5 真 DOM 取证（★ cwd 必须是 code/miniapp）
cd code/miniapp && node scripts/shots-sys-export-001.mjs       # 3 张 PNG + shots-evidence.json

# 6) 单测
cd code/miniapp && pnpm vitest run                             # 181/181
cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg test \
  -s ../../.mvn-settings.xml -Dmaven.repo.local=../../.m2repo -Duser.home=../../.buildhome   # 274/274

# 7) 收尾
bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --mp-port 9204
```

---

## §9 长进程 / 端口 / 收工自检

### 9.1 `git status --porcelain`（收工态）

```
 M code/miniapp/src/components/lqg/DownloadBar.vue
 M code/miniapp/src/pages/ledger/index.vue
 M code/miniapp/src/utils/request.ts
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sys/export/
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sys/export/
?? code/miniapp/scripts/shots-sys-export-001.mjs
?? code/miniapp/src/pages/ledger/export.spec.ts
?? code/miniapp/src/pages/ledger/export.ts
?? code/miniapp/src/utils/baseUrl.ts
?? code/miniapp/src/utils/fileHandoff.ts
?? doc/waves/reports/SYS-EXPORT-001/
```

- **`code/miniapp/src/pages.json` 没有出现在 porcelain 里**（本次 H5 dev server **没有**改写它）—— 按任务书②，它是「有意保留」的状态，本票既没 `git checkout` 它、也没重生成它。
- `code/plus-ui/.eslintrc-auto-import.json`（plus-ui dev server 的生成物）**已 `git checkout --` 还原**（WARN-9）。
- 只读区（`_input/` / `doc/requirements.yaml` / `doc/authority/**` / `doc/change-log.md` / `doc/api-contract.md` / `doc/verify/{seed/**,gen_seed.py,api.sh,fixtures/**,reseed.sh,db.py,xlsx_header.py}`）：porcelain **全空**。
- `doc/waves/state.json` / `_manifest.json`：**没动**。
- 本票**没有** `git add` / `git commit` / `git push` / 切分支 / 改历史。

### 9.2 长进程 / 端口（收工）

| 进程 | 端口 | 处置 |
|---|---|---|
| 后端（本票重建的 jar） | 8094 | **已按 PID 关停** |
| 工作台 plus-ui | 8093 | **已按 PID 关停** |
| 小程序 H5（`pnpm dev:h5`，本票取证用） | 9204 | **已按 PID 关停** |
| 8080（Kevin）/ 5432 / 6379 | —— | **全程没碰**（开工与收尾两次复核都为空） |
| `lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002-9003 / `lqg-dev-gotenberg` 3010 | —— | 全程在跑，**没停** |

- 关进程**只按 PID**（`lsof -ti tcp:<port> -sTCP:LISTEN` → `kill`）；全程**没有** `pkill -f 'ruoyi-admin.jar'`，**没有** `kill -9`。
- **DB 收尾**：`bash doc/verify/reseed.sh --yes` 回确定性快照（accept / 取证跑过多轮导出与登录）。

### 9.3 取证目录 `doc/waves/reports/SYS-EXPORT-001/`

| 文件 | 内容 |
|---|---|
| `accept-result.json` / `accept-logs/SYS-EXPORT-001-acc{1,2}.sh.log` | accept-run 的机器结果（2/2 + acc1 的 `nf:["NF1"]` + acc2 零归一化）+ 逐条日志 |
| `accept-evidence-1.txt` | **accept 1 的 `run` 逐段重放**：每段的原样输出 + 两个独立行数来源（导出文件 / `db.py`）|
| `accept-evidence-2.txt` | **accept 2 的 `run` 逐段重放**（票面逐字，**零归一化**）+ 产物 sha256 |
| `counterfeit-transcript.txt` | 6 个探针逐条「改坏→红→还原→绿」（**12/12**） |
| `machine-evidence.txt` | 6 节：进程 / jar / 迁移号 · 四端点原始头 · 错误面业务码 · H5 代理 · 平台能力不可验清单 |
| `java-test-transcript.txt` | `ruoyi-lqg` 全模块 `Tests run: 274, Failures: 0` + 逐类清单（含本票 11 例） |
| `vitest-all.json` | 小程序全量 vitest：**181/181** |
| `regression-transcript.txt` | 三条上游 accept 的复跑（DOC-MP-001 2/2 · SYS-MP-001 2/3 · DOC-MP-002 1/2）+ 逐条解释 |
| `shots-evidence.json` / `shots-evidence.stderr.txt` | H5 Playwright 的**全部断言值**（11 条断言全 true）+ blob 真实字节数 |
| `shots-sys-export-001.mjs` | 取证脚本副本（原件在 `code/miniapp/scripts/`） |
| `sysexp001-counterfeit-probes.sh` | counterfeit 探针脚本（可复跑） |
| `01-ledger-export-enabled.png` · `02-ledger-filter-pending.png` · `03-handoff-action-sheet.png` | 三张截图（**未读进上下文**，只登记路径与断言值） |

**H5 真 DOM 断言值（`shots-evidence.json` 全文 11 条，全 true）**：

```
beforeFilter   : {"exportText":"导出 Excel","exportDisabled":false,
                  "note":"核验、冻存取用请到网页工作台","hasPlaceholderToast":false}
filtered       : {"count":"共 2 条 · 左右滑动看全部 15 列", "filterChips":["全部","待核验","有效","无效"]}
actionSheet    : ["打开","发送到微信","Cancel"]
tissueBlob     : {"present":true,"size":4117,"type":"application/vnd…spreadsheetml.sheet","isXlsx":true}
requests[0]    : GET http://127.0.0.1:9204/lqg-api/mp/int/export/tissue?verifyStatus=pending
                 Authorization: Bearer eyJ… (hasAuthHeader=true)   clientid: 22b2aecd… (hasClientid=true)
                 → 200, content-type=…spreadsheetml.sheet, content-disposition=…filename*=utf-8''%E6%A0%B7…
requests[2]    : GET http://127.0.0.1:9204/lqg-api/mp/int/export/cryo?overdueOnly=true（同样带头）
assertions     : exportEnabled ✓ · noPlaceholderToast ✓ · noteIsNewWording ✓ · handedOff ✓
                 tissueFilteredRequest ✓ · cryoOverdueRequest ✓ · everyRequestHasAuth ✓
                 everyRequestHasClientid ✓ · everyResponseIsXlsx ✓ · chineseFileNameInHeader ✓
                 downloadedNonEmptyXlsx ✓
```

### 9.4 ★ 真机 / 微信里**未覆盖**（如实说明，不是遗漏）

- **未做体验版 / 真机档**：没跑 `pnpm upload:mp`，不要求 appid / 上传密钥 / 合法域名 —— Kevin 2026-09-22 的明确口径（appid 暂时无法提供，小程序一律走本地 Mock）。
- **未在微信开发者工具里看过**（沙箱跑不通它：要写 `~/Library/Application Support/微信开发者工具/**` 且首次要扫码）。
- **因此下列项在真机上仍未验证**（交 SYS-STAGING-001 / SYS-RELEASE-001 或人工）：
  1. **`wx.downloadFile` 的合法域名**是否放行**后端域名**（导出走 `/mp/int/export/*`，不是 OSS 签名链接 —— 见 WARN-8，这是本票最大的未验证点）；
  2. `wx.openDocument({showMenu: true})` 的右上角菜单在真机上的实际观感 / 能不能「保存到手机」（**H5 上没有查看器，退化成浏览器打开，所以这条完全没验**）；
  3. `wx.shareFileMessage` 发送到聊天（**H5 上 `uni.shareFileMessage` 不存在**，实测走的是「给一句人话」那一支）；
  4. `uni.downloadFile` 带 `header` 在真机上是否真的把 `Authorization` / `clientid` 发出去（H5 走的是 `fetch`，验的是**同一份 header 构造**，不是 `downloadFile` 本身）；
  5. 390 宽下四张表列数最多 15 列的横向滑动 + 底部栏安全区在真机的观感。
- **替代验收面**：`pnpm dev:h5`（`VITE_MOCK_LOGIN=1`）+ Playwright 真 DOM + **真后端 8094** + **真库 5433**；三张截图与 11 条断言值都由这条路跑出来；**小程序产物本身是构建过的**（`pnpm build:mp-weixin` 是 accept 2 的一部分，`pages/ledger/index.js` + `utils/fileHandoff.js` 都在盘，且 `showMenu:!0` / `shareFileMessage` / `downloadFile` 都在产物里）。

---

## §10 遗留与 raise（ticket §4.5）

1. **`DOC-MP-002` acc1 的两句 grep 要放宽**（WARN-1 / §5.1）——本票抽公共段的**必然**结果，需主会话改票面（我只报不改）。
2. **`SYS-MP-001` acc1 是预先就红**（WARN-2 之外 / §5.2）——JSDoc 注释触发一条过宽正则，与 4 个我没碰过的 HEAD 文件同型；建议收窄成只扫样式块。
3. **accept 2 第 5 段的 grep 强度**（WARN-2）——文件级断言抓不到「调用点不带 header」；本票加了运行时守卫 + 探针，建议票面也加一条结构性断言。
4. **导出文件名的时间段格式未定**（WARN-3）——到秒（本实现）还是到日（契约例子）？两侧各改一处。
5. **契约第 55 行缺「未知 sheet 的码」**（WARN-4）——本票不改契约、按现状（404 / 400）实现并如实记录。
6. **`downloadFile` 合法域名未定**（WARN-8 / §9.4）——不影响本票 accept，但**会挡住真机档**；导出这一路只需要**后端域名**（比文档下载那一路更简单）。
7. **`doc/verify/README.md` 的石蜡包埋计数写成 6（实际 5）**（WARN-5）——只读区，没改。
8. **`touches` 缺 `utils/baseUrl.ts` / `utils/request.ts`**（WARN-6）——本票纯新增迁移，行为零变化。
