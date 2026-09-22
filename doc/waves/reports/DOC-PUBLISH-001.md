# DOC-PUBLISH-001 · 完工报告

- **票**：DOC-PUBLISH-001（track DOC / phase D7 / size M）—— 「完成并同步 / 撤回」两态状态机 + 工作台预览面板
- **分支**：工作树当前在 **`integration`**（`task/D7` 不存在，见 §0 与 WARN-1；**未切分支、未 push、未合分支**）
- **accept**：**1 / 2 ✗** —— acc2 全绿；**acc1 红在一个字符上**（票面期望 `True`，PostgreSQL 的布尔→文本恒为小写 `true`），
  详见 §3（**这是票面断言的字面缺陷，不是实现行为不符；把票面那三处 `True` / 两处 `False` 只改这一个字母，
  整条 15 段逐字重放 `RC=0`**）。其余 13 段（含 counterfeit 点名的 4 个方向）全部成立。
- **人眼验收**：`ui-check.mjs` **28/28 PASS**（真浏览器 + 真后端 + 真库 + 真 Gotenberg）
- **单元测试**：整个 `ruoyi-lqg` 模块 **251 / 251 绿**（含本票新增 5 例 + 上游全量 0 回归）
- **前端构建**：`pnpm build:prod` exit 0（`dist/index.html` 存在）
- **取证目录**：`doc/waves/reports/DOC-PUBLISH-001/`
- **环境**：后端 8094（pid 13869 → 收工前最后一次重启的 pid 见 §7）、工作台 8093；`lqg-dev-gotenberg` 全程 running（未停）

---

## 0. 状态自检（3 级）

| 项 | 结果 |
|---|---|
| 分支符合调度器注入的期望 | ⚠️ **WARN-1**：任务书要求 `task/D7`，`git branch -a` 只有 `integration`（HEAD = `7fea2bc merge(task/D6)`）与 `task/D6`，`git worktree list` 只有主工作树。任务书同时说「不要自己切分支」→ **按现状在 `integration` 上实现**（该 HEAD 已含 D6 全部 5 张票），未切分支 |
| `depends_on` 全部 done | ✅ **DOC-PDF-001**（`V202609261420` 已应用、`/lqg/doc/**` 三端点可用）、**QC-WEB-002**（三页签接真）都在；上游 5 份报告的「给下游的坑」逐条读过 |
| 扫 `doc/change-log.md` 顶部 CR | ✅ 无一条改本票的字段 / 接口 / 页面口径。相关两条：**CR-20260918-07 第 4 条**（外部版里内部编号一格仍留空、开关不作用于预渲染文档）—— 本票不改 `audience` 口径；**CR-20260921-08**（小程序视觉 A）明说「网页工作台不受影响」 |
| 环境可用 | ✅ `qa-up.sh --backend-port 8094 --web-port 8093 --no-mp` 一条命令起齐（含 reseed 7 段）+ 工作台可登录 |
| 权威锚逐条取过 | ✅ 7 条全部 `authority_lint.py show` 取到（`FLOW:F-QC-01.step5/6/7`、`UI:admin.doc.preview`、`FIELD:t_lqg_qc_sample.doc_status/published_time`、`ADR-0005`）。**prompt 与蓝图无冲突** |
| 迁移号 | ✅ **本票一支迁移都没加**（见 §1.1），无取号风险 |

**没有 STOP。** 一条 ⚠️（分支）与若干 WARN，见 §5。

### 0.1 issue #228 的裁定（票面已经裁定，本票收口）

`doc_status` 的语义两处注释冲突：
- `V202609261300` 的 DDL 注释：「published 后再保存内容 → 回到 draft」；
- `QcDocService` 类注释（QC-MODEL-001）：「本票一个字都不动 doc_status（归 DOC-PUBLISH-001）」。

本票 **accept 1 的第 7/9/12 段**（改内容 / 改评分 / 加图片任何一种都让已完成的文档回到草稿）+ 蓝图
`FLOW:F-QC-01.step7`（原话「对 published 文档保存任何内容改动（含增删图片、附件）→ doc_status 回到 draft、
published_time 清空」）**两侧一致地指向 DDL 注释那一半**。

**结论：#228 收口 = 采 DDL 注释那一侧。** QC-MODEL-001 的类注释只是「那张票的范围」而不是口径；
本票把 `QcDocService` 的类注释改写成新口径（并指向 `DocPublishService`），钩子落在 **qc 包的 service** 里
（ticket §2 原话「别靠 controller 记得调」）。`FLOW:F-QC-01.step7` 的 `produces` 也照做：
「送检方看不到半成品；重新『完成并同步』后再次可见」。

---

## 1. 改了哪些文件

### 1.1 Flyway 迁移：**一支都没有**（以及为什么）

本票「完成并同步 / 撤回」要写的 `doc_status` / `published_by` / `published_time` 三列
**QC-MODEL-001 的 `V202609261300` 已经建好**（`t_lqg_qc_sample/organoid/score` 三张表都有），
权限行 `5503 lqg:qc:publish` 也已落并授给 101/102，两个端点所属的 C 页面菜单 5510 是 **QC-WEB-001** 落的 ——
**本票没有任何 DDL / 菜单 / 授权要加**，因此不取号、不加迁移。库里已应用的最大版本仍是
`202609261430__QC-WEB-001-qc-menu.sql`（收工前实测；也给 D7 之后要加迁移的票留一句：
**版本号必须 > 202609261430**）。

### 1.2 后端新增（全部落在 `touches` 的 `org.dromara.lqg.doc.publish/**`）

| 文件 | 说明 |
|---|---|
| `.../doc/publish/DocPublishService.java` | 状态机本体：`publish` / `unpublish` / `onContentChanged` / `invalidateMerged` 的排队。**刻意不加 `@Transactional`**（渲染线程要读到已提交的 `doc_status`，见 §3 的实现要点 2） |
| `.../doc/publish/DocPublishController.java` | `POST /lqg/qc/{sampleId}/{docType}/publish`、`…/unpublish`，权限 `lqg:qc:publish` |
| `.../test/java/.../doc/publish/DocPublishStateContractTest.java` | **5 例**纯反射契约测试：路径段↔字典取值两套命名、钩子入参归一、两个端点形状+权限、钩子接在 service 上（不是 controller）、两个 audience 都在 |

### 1.3 后端改动（`touches` 内的 `org.dromara.lqg.qc/**` + 两处上游 doc 包）

| 文件 | 改动 |
|---|---|
| `.../qc/service/QcDocService.java` | **M**：① 类注释按 §0.1 改写；② 注入 `DocPublishService`（字段 + `@Setter`/`@Autowired`：构造器注入会与 `DocPublishService` 读三张表**成环**）；③ 加 `afterChange(sampleId, docType)` 收口；④ **7 个写入口**全部挂上钩子：三个 `PUT`（样本质控 / 类器官质控 / 评分）+ 图片增/删/排序 + 附件增/删 |
| `.../doc/render/DocAudiences.java` | **M**：加 `ALL = [internal, external]`（「完成并同步」两个 audience 都排进渲染） |
| `.../doc/pdf/DocArtifactRows.java` | **M**：加 `markStale(id, currentHash)` —— 只换 `content_hash`，`render_status`/`oss_id` 一个字节不动（旧产物保留，但不再被当成最新返回） |
| `.../doc/render/service/DocRenderService.java` | **M**：加 `invalidateMerged(sampleId)` —— 成员集合一变就把合并件标记过期（复用 `DocRenderModel.merged(...).contentHash()` 与 `DocAudiences.ALL`，**不自己算指纹**） |

> 后三个文件不在 `touches` 的字面里（`touches` 只给了 `doc/publish/**`），但它们都在 `doc/**` 域内、
> 且是「合并件失效」这条口径的唯一正确落点。**如实登记，见 §4 第 1~3 条。**

### 1.4 前端（`touches` 的 `code/plus-ui/src/api/lqg/doc/**`、`views/lqg/qc/components/PreviewPane.vue`、`views/lqg/qc/editor/index.vue`）

| 文件 | 说明 |
|---|---|
| `code/plus-ui/src/views/lqg/qc/components/PreviewPane.vue` | **新增**（票面点名的文件）：内外部版切换、逐页页面图（`pages[].url`，**不是 iframe PDF**）、四个下载入口（Word / PDF / 合并 Word / 合并 PDF，真取 10 分钟签名链接）、渲染中 / 失败（原因 + 「重新生成」）两态、`waitForRender` 轮询（2.5s × 40） |
| `code/plus-ui/src/views/lqg/qc/editor/index.vue` | **M**：右栏占位换成**单个** `PreviewPane` 实例（`v-for` 里的 ref 会变成数组，点「预览」就没反应 —— 实现过程中真踩到，见 §3 的实现要点 5）；页脚「预览」「完成并同步」点亮，已完成态按钮变「撤回」，页脚提示变「已同步给送检方 · 修改后需重新同步」 |
| `code/plus-ui/src/api/lqg/doc/index.ts` | **M**：加 `publishQcDoc` / `unpublishQcDoc` 两个客户端 + `DocPathType` |
| `code/plus-ui/src/lang/lqg/qc-publish.zh_CN.ts` / `.en_US.ts` | **新增**：`lqg.qc.preview.*`（新顶层键 `preview`，与既有 `qc*.ts` 不撞键） |
| `code/plus-ui/src/lang/lqg/qc.zh_CN.ts` / `.en_US.ts` | **M**：页脚提示与预览占位文案更新；删掉已无用的 `editor.notYet` |

### 1.5 报告与取证目录 `doc/waves/reports/DOC-PUBLISH-001/`

`accept-result.json`（accept-run 机器结果）· `accept-acc1.sh.log` / `accept-acc2.sh.log`（逐条原样日志）·
`accept-acc1-forensics.txt`（**票面 15 段逐字重放；含 `True`→`true` 一字之差的最小改动重放 `RC=0`**）·
`state-machine-forensics.txt`（三态取样 + 产物行全量 + 合并件指纹失效探针）· `extra-probes.txt`
（非法转移 / 权限 / 附件增删 / 图片删除 / 图片重排也回草稿）· `ui-check.mjs` + `ui-check.log`（**28/28**）·
`shots/`（6 张：草稿 / 已完成 / 回草稿三个徽标态 + 预览面板内外部版 + 发布后外部版）。

---

## 2. 接口清单与口径

```
POST /lqg/qc/{sampleId}/{docType}/publish      权限 lqg:qc:publish   draft → published（+ 排队渲染）
POST /lqg/qc/{sampleId}/{docType}/unpublish    权限 lqg:qc:publish   published → draft（清完成人/时间）
docType ∈ sample-qc | organoid-qc | score（路径段用连字符；docKind 是下划线）
```

- **合法转移只有两条**：`draft→published`、`published→draft`。其余一律 **400 带原因**：
  对已完成再点完成 →「这份文档已经完成并同步过了，不用再点一次」；
  对草稿撤回 →「这份文档还没完成，没什么可撤回的」；
  行不存在 →「这个样本还没有这份质控文档（先在工作台打开一次质控页）」。
- 状态更新是**条件式 UPDATE**（`where sample_id=? and doc_status=<期望>`）：并发两次「完成并同步」只有一次改到行
  —— 「重复完成不拒绝（每点一次重置一次完成时间）」这条 counterfeit 在 SQL 层就被堵死。
- `publish` 写完之后**另起线程**排队 4 次渲染：`{sample_qc|organoid_qc|organoid_score} × {internal, external}`
  + `merged × {internal, external}`。接口立刻返回 200，页面图由前端轮询 `/pages` 拿。
- 钩子**在 qc 包的 service 里**（`QcDocService.afterChange`），7 个写入口一个不漏：
  三个 PUT + 图片增/删/排序 + 附件增/删。

---

## 3. accept 逐条 ✅ / ❌ 与关键输出

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket DOC-PUBLISH-001 --run \
      --json .tmp/doc-publish-accept.json --logdir .tmp/doc-publish-accept-logs
[run] 2 条 accept，单条超时 900s
  ✗ DOC-PUBLISH-001 acc1 [STATE] … → exit 1 (0.9s)
  ✓ DOC-PUBLISH-001 acc2 [API]  前端构建是本次产物；预览面板接入编辑页，有内外部版切换、四个下载入口、失败态与重新生成 (10.4s)
[ok] 结果落盘 .tmp/doc-publish-accept.json
[run] 通过 1/2
[run] ⛔ 1 条未登记的红（= 产品断言不成立）：DOC-PUBLISH-001 acc1
```

### accept 1 · STATE —— ❌（红在一个字符上；实现侧 15 段全部成立）

`exit 1 @ 1.0s`，日志停在：

```
published|9000000101|true
```

**根因**：票面第 11 / 13 / 14 段写

```bash
test "$(st t_lqg_qc_sample 9000001006)" = "published|9000000101|True"
```

而 `st()` 的 SQL 是 `… || (published_time IS NOT NULL)` —— PostgreSQL 里布尔与文本拼接时
按 `boolout` **恒为小写 `true`**。证据（两条独立路径）：

```
$ python3 doc/verify/db.py --sql "SELECT (published_time IS NOT NULL)::text"
true
$ PGPASSWORD=… psql -t -A -c "SELECT doc_status || '|' || COALESCE(published_by::text,'-') || '|' || (published_time IS NOT NULL) FROM t_lqg_qc_sample WHERE sample_id=9000001006 AND del_flag='0'"
published|9000000101|true
```

**最小改动重放**（只把票面三处 `True` 改成 `true`、两处 `False` 改成 `false`，其余逐字不动）：

```
$ sed -e 's/= "published|9000000101|True"/= "published|9000000101|true"/' \
      -e 's/= "draft|-|False"/= "draft|-|false"/' .tmp/DOC-PUBLISH-001-acc1.sh > .tmp/acc1-lowercase.sh
$ bash .tmp/acc1-lowercase.sh ; echo RC=$?
true                     ← 第 3 段 jq .code==200
internal:png             ┐
internal:docx            │ 第 6 段 --col-set 成立
internal:pdf             │
external:png             │
external:docx            │
external:pdf             ┘
true                     ← 第 8 段 jq（改内容保存）
true                     ← 第 10 段 jq（改评分保存）
true                     ← 第 12 段 jq（加图片）
RC=0                     ← 15 段全过
```

原文与逐段输出：`doc/waves/reports/DOC-PUBLISH-001/accept-acc1-forensics.txt`。

**同一份票面在别处一律写小写**（`AUTH-EXT-001` 的 `--eq "…|true"`、`CRYO-FLOW-001` 的 `--eq="-3|true"`），
本机 `CRYO-FLOW-001` 三条 accept 实测全绿 —— 说明是**这一张票的 3 处笔误**，不是本栈的 DB 层差异。
**修法（一个字符）**：把票面 acc1 的 `True`→`true`、`False`→`false`。
**本 impl 不改票面**（任务书明令），如实报 failed。

counterfeit 逐条对账（都对得上，且都有机器证据）：

| counterfeit | 本实现为什么不中 |
|---|---|
| 「改内容回草稿」只接在三个 PUT 上、漏了图片与附件的增删 → 最后一组红 | 钩子接在 **service 的 7 个写入口**；accept 最后一段（加一张图 → 回草稿）通过，另跑探针证明**附件增 / 附件删 / 图片删 / 图片排序**四路也回草稿（`extra-probes.txt` §4~§7） |
| 钩子写在 controller 里、评分接口忘了调 → 评分那组红 | trace 明确：钩子在 `QcDocService` 内（`afterChange`），`DocPublishStateContractTest` ③ 用反射钉住「controller 里没有 `onContentChanged`」；accept 第 10 段（改评分 → 回草稿）通过 |
| 完成只改了状态、没触发外部版渲染 → 20 秒后没有 external 三种产物 | 第 6 段 `--col-set` 六行齐全（`internal/external × docx/pdf/png` 全 `done`）；`state-machine-forensics.txt` §C 还列出合并件（`merged × 2 audience`，每份 3~4 页） |
| 重复完成不拒绝（每点一次重置一次完成时间） | 第 5 段 400「这份文档已经完成并同步过了，不用再点一次」；状态更新是条件式 UPDATE，SQL 就在后端日志里（`… WHERE … AND doc_status = 'draft'`） |
| 状态断言全部直连库读，不信接口自报 | 本实现**从不**依赖接口自报状态；accept 的 `st()` 全部直连库 —— 这也是为什么第 11 段的 `true` 一出现就立刻暴露 |

### accept 2 · API —— ✅

```
$ cd code/plus-ui && rm -rf dist && pnpm build:prod   → exit 0，dist/index.html 存在
$ grep -q 'PreviewPane' src/views/lqg/qc/editor/index.vue          → 命中（import + 模板）
$ grep -q 'audience' …/PreviewPane.vue && grep -q 'merged' …        → 命中（版本切换 + 合并件下载）
$ grep -cE "format.*(docx|pdf)|'docx'|'pdf'" …/PreviewPane.vue      → ≥2
$ grep -qE 'failed|重新生成' …/PreviewPane.vue                      → 命中
$ ! grep -nE ':disabled="true"' src/views/lqg/qc/editor/index.vue   → 无命中（两个写死置灰的按钮已点亮）
```

### 3.1 另加：人眼验收 28/28（`ui-check.mjs`，真浏览器 + 真上传 + 真库 + 真 Gotenberg）

`node doc/waves/reports/DOC-PUBLISH-001/ui-check.mjs`（Playwright + chromium headless；连接 8093 / 8094 / 5433）。
逐项摘要（完整日志 `ui-check.log`）：

| 组 | 断言（证据值） |
|---|---|
| ① 进入态 | 页签徽标 `样本质控表 草稿 / 类器官质控表 草稿 / 类器官质量评分表 已完成`；库内 `draft|-|false`；页脚「完成并同步给送检方」+ 提示「预览只看不发布」 |
| ② 预览 | `POST 9000001006/sample_qc/render?audience=internal` 真被调；轮询 `GET pages`；**2 页页面图**；`src = http://127.0.0.1:9000/ruoyi/lqg/doc/9000001006/sample_qc/internal/b7914884482d-p1.png?X-Am…`（真签名地址，**不是 seed.invalid**） |
| ③ 内外部版 | 切外部版重新取 pages；提示语「外部版里内部编号一格为空」 |
| ④ 下载 | 四个入口都在（Word / PDF / 合并 Word / 合并 PDF）且 **4/4 都不是置灰** |
| ⑤ 完成并同步 | 库内 `published|9000000100|true`（完成人 = 当前登录人 lqgadmin）；徽标变**已完成**；按钮变**撤回**；页脚提示变「已同步给送检方 · 修改后需重新同步」；**外部版页面图也出得来** |
| ⑥ 改内容保存 | 库内回 `draft|-|false`；徽标回**草稿**；页脚恢复；**评分表仍是已完成**（只回受影响那一份） |
| ⑦ 撤回 | 重新完成后 `published|9000000100|true` → 点「撤回」→ `draft|-|false`、徽标回草稿 |
| ⑧ | 全程 `pageErrors == []`（seed 假地址图的资源加载失败已按平台限制排除） |

★ **6 张截图只落盘、没有读进上下文**（纪律）。`01-badge-draft.png` / `04-badge-published.png` /
`06-badge-back-to-draft.png` = 票面 §4 要的「完成前后、修改后三个时刻的页签徽标」；
`02-preview-internal.png` / `03-preview-external.png` = 「预览面板内外部版对照」。

### 3.2 实现里几个「做反了就红」的点（口径留痕）

1. **路径段 vs 字典取值（连字符 ↔ 下划线）**：URL 是 `sample-qc`，`docKind` 是 `sample_qc`。
   本票的实现**先后踩了两次**（第一次 `requireDocType` 收到字典值 → 保存接口 400；
   第二次 `scheduleRender` 收到 `sample_qc` → 接口 400 但状态已经改了）。收口办法是
   `DocPublishService.normalizePath` + `docKindOf` **两套都认**，并由 `DocPublishStateContractTest` ①/①b 钉死。
2. **`DocPublishService` 刻意不用 `@Transactional`**：渲染线程（`DocRenderModelFactory`）要回读
   `doc_status`（合并件按它筛成员、指纹里也带它）；把「改状态」与「触发渲染」裹在同一个事务里，
   渲染线程读到的是**旧状态**。所以每步各自提交、渲染排在线程池里。
3. **条件式 UPDATE**（`and doc_status='draft'`）：重复完成天然被拒，「每点一次重置一次完成时间」在 SQL 层不可能。
4. **合并件失效不自己算指纹**：`DocRenderService.invalidateMerged` 复用
   `DocRenderModel.merged(...).contentHash()`（成员集合的函数），只把 header 行的 `content_hash` 换成新值
   —— `pages` / `download` 的「与 header 同指纹」那一道自然拦下旧产物。实测：改内容前
   `468f8766…` → 改内容后 `5324360e…`（`state-machine-forensics.txt` §D）。
5. **`v-for` 里的 `ref` 是数组**：右栏一开始把 `PreviewPane` 放进 `v-for` 的 `el-tab-pane` 里，
   `previewPaneRef.value?.render()` 静默无效（点「预览」什么都不发生）。改成**单个实例** +
   `:doc-kind="currentTab.docKind"`（`ui-check` 的 ② 就是这条的回归门）。
6. **`published_by` 是「当前登录人」**：`accept` 用 `--as staff`（9000000101），工作台用 lqgadmin（9000000100）
   —— 两条路径都对，页面/探针的期望值不能写死其中一个。
7. **行不存在 ≠ 状态不对**：`statusOf` 返回 `null` 时单独报「这个样本还没有这份质控文档」；
   否则样本不存在也会说「已经完成并同步过了」，把「没这份文档」伪装成「已经做过了」。

---

## 4. 越出 `touches` 的改动（全部如实列出）

| # | 文件 | 为什么动 | 越界程度 |
|---|---|---|---|
| 1 | `.../doc/render/DocAudiences.java` | 加 `ALL=[internal,external]`：「完成并同步」要把两个 audience 都排进渲染（票面 §2 原话「内部版、外部版、merged 两个版本」） | 轻：加法，既有调用方不受影响 |
| 2 | `.../doc/pdf/DocArtifactRows.java` | 加 `markStale(id, hash)`：「任何一份变化都让该样本的 merged 失效」（票面 §0 口径复述 4）需要「旧产物保留但不再被当成最新」的落点 | 轻：新增方法，不改既有方法 |
| 3 | `.../doc/render/service/DocRenderService.java` | 加 `invalidateMerged(sampleId)`：合并件失效只有这里拿得到指纹（`DocRenderModel.merged`）与 `rows` | 中：改了 DOC-PDF-001 的服务（只新增一个 public 方法 + 一个 private 方法，既有 render/download 一字未动） |
| 4 | `code/plus-ui/src/lang/lqg/qc-publish.zh_CN.ts` / `.en_US.ts` | 文案一律进 `lqg.<域>.*`（SYS-WEB-001 立的约定）；`qc*.ts` 的顶层键已被占，新开 `qc-publish` 文件用新顶层键 `preview` | 轻：新文件，不动既有键 |
| 5 | `code/plus-ui/src/lang/lqg/qc.zh_CN.ts` / `.en_US.ts` | 页脚提示与预览占位文案要改（QC-WEB-001 建的键，本票就地更新）；顺手删掉已无用的 `editor.notYet`（两个文件同步删，键集仍一致） | 轻：只改值 + 删一个没人用的键 |
| 6 | `doc/waves/reports/DOC-PUBLISH-001/**` | 本票报告与取证（`touches` 里没有报告目录，按惯例归本票） | 无 |
| 7 | `.../test/java/**/doc/publish/**` | 票面 `touches` 第 2 条就是 `src/test/java/org/dromara/lqg/doc/publish/**` | 无（在 touches 内） |

**明确没动的**：`_input/`、`doc/requirements.yaml` 的 text、`doc/authority/*.yaml`（`authority_lint.py` 垫片未删）、
`doc/change-log.md`、`doc/verify/seed/**`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`doc/verify/fixtures/**`、
`doc/api-contract.md`、`code/miniapp/**`、`code/deploy/**`、`application-*.yml`（**没动 `nonProxyHosts` 那条 JVM 参数**）、
`doc/waves/_manifest.json`。

`git status --porcelain` 里那条 `M doc/waves/state.json` **不是本票改的**（任务书明令不动它；见 §7 的收工输出）。

---

## 5. WARN 清单（请逐条入账）

- **WARN-1 · 任务书要求的分支 `task/D7` 不存在**。实测 `git branch -a` = `integration`(+`task/D6`)、
  `git worktree list` 只有主工作树；任务书又说「不要自己切分支」→ 本票在 `integration`（HEAD `7fea2bc`，已含 D6 五张票）
  上完成。**请主会话确认这是调度器的预期**；若不是，本票的改动都在工作树里、没有 commit，切分支后原样保留。
- **WARN-2 · ★ accept 1 的 `True` 是票面笔误**（详见 §3）。建议把 acc1 的 3 处 `True` → `true`、2 处 `False` → `false`。
  在此之前 `accept-run.py` 会一直把这条报成「未登记的红」。
- **WARN-3 · 「取图失败」的两类处置（承 DOC-RENDER-001 WARN-3 / DOC-PDF-001 WARN-1）**：
  ① docx 阶段个别图片取不到 = **跳过 + WARN，文档照出 done**；② **流水线步骤**失败 = 整体 failed + 原因。
  本票 accept 第 6 段用 1006（seed 图 `service='seed'`）跑的正是 ① —— 能出 `done` 就是这条口径的证据。
  accept 1 counterfeit 那句字面（「图片地址取不到 → 整份 failed」）在 ① 上仍然冲突，
  **本票没有改上游、没有改票面**，如实记录。
- **WARN-4 · 合并件的失效是「标记过期」，不是「删产物」**。改内容后 merged header 行的 `content_hash`
  被换成当前该有的指纹（实测 `468f8766…` → `5324360e…`），旧 docx/pdf/png 仍在库里与桶里
  （ticket §2「已有的旧产物保留」）。**存储随每次重出单调增长**（承 DOC-PDF-001 WARN-8），
  清理由谁负责仍未定（建议 SYS-PROD-001 / 或者某张「产物保留策略」的票）。
- **WARN-5 · 「完成并同步」的渲染是后台线程 + 前端轮询**，接口返回 200 时产物**还没好**。
  外部可见性（AUTH-EXT-003）必须同时看 `doc_status='published'` **与** 外部版 `render_status='done'`
  —— 只看前者会在渲染完成前的一小段时间里把「看不见」的文档列出来。
- **WARN-6 · `publish` 不校验「内容是否有意义」**（三个字段全空的草稿也能被完成并同步）。
  票面 §3 明确不做审批流、也不要求「非空才允许完成」；若要加，属新口径。
- **WARN-7 · `unpublish` 后重新 `publish` 会走全文重渲染**（`content_hash` 里带着 `doc_status`，
  所以状态一变指纹就变）。代价是每次「撤回 → 再完成」多一次渲染（docx+pdf+N×png），
  这是 DOC-RENDER-001 WARN-8 的既有取舍，本票未改。
- **WARN-8 · 本票新增的 `markStale` 只改 `content_hash`**，不改 `render_status`。
  因此一个「上一版 done 的合并件」在库里长这样：`render_status=done` 但指纹与 header 上的新指纹不一致。
  按 DOC-PDF-001 的既定口径（`pages`/`download` 只认「与 header 同指纹」）它**不会**被返回；
  但**别用「render_status='done' 的行数」当产物计数**，要带上 `content_hash`。
- **WARN-9 · 本票没做「完成通知」**（票面 §3 明确不做：订阅消息 / 短信）。
- **WARN-10 · 卡片式 UI 的两套 tabs**：右栏预览面板不再自带版本页签（用左栏的页签驱动
  `docKind`），只在面板头部保留「内部版 / 外部版」切换。若后续票要「一个页面同时看三份文档的预览」，
  需要另设计——本票按 `UI:admin.doc.preview` 的「右栏 / 或弹窗」落在右栏。

---

## 6. 给下游的坑（AUTH-EXT-003 / DOC-MP-001 / DOC-MP-002 / SYS-EXPORT-001）

1. **★ `doc_status='published'` 不等于「对外可见」**。`publish` 只保证「状态 + 排队渲染」；
   对外可见 = `doc_status='published'` **且** 该 `(docKind, audience='external')` 的
   `t_lqg_doc_file` header 行 `render_status='done'` 且该行 `content_hash` 与
   `DocRenderService` 此刻算出来的指纹一致。**AUTH-EXT-003 的 list 请照这三个条件一起过滤**
   （`content_hash` 那一条就是「改了内容还没重出」的挡板）。
2. **★ `published_time` / `published_by` 会被清空**。「改内容 → 回草稿」和「撤回」都会把两列写 `NULL`
   （`DocPublishService.updateStatus`）。外部列表按 `published_time` 倒序时，**draft 行不参与**
   （它们本来也不该出现）；但别把「`published_by IS NULL` 的历史行」当成脏数据。
3. **★ 合并件是「已完成的几份」的拼接，成员集合随时会变**。`merged` 的成员按
   `doc_status='published'` 筛（`DocRenderModelFactory.mergedMembers`），顺序写死
   `样本质控表 → 类器官质控表 → 类器官质量评分表`（`DocKinds.MERGED_ORDER`）。
   一份成员都没有时 `merged` 渲染会 400「这个样本还没有已完成的质控文档，合并件无从拼起」
   —— **DOC-MP 的合并下载要能显示这句话，不是白屏**。
4. **★ 页面图 / 下载链接都是 10 分钟短时签名链接**（`DocArtifactStore.SIGNED_URL_TTL`）：
   别缓存、别存库、别拼字符串。小程序那边每次进预览页都要重新取。
5. **★ 工作台已经有的现成件可以直接用**：
   - `@/api/lqg/doc` 的 `renderDoc / getDocPages / getDocDownload / publishQcDoc / unpublishQcDoc`；
   - `views/lqg/qc/components/PreviewPane.vue`（`props: sampleId / docKind / docStatus`；
     `expose: render / reset / loadPages`；`emit: changed / busy`）——
     小程序端的预览页若也要「内外部版 + 四个下载 + 失败重试」，口径照它抄就不会两边打架；
   - 「失败态」的判据是 `pages` 接口的 `data.status === 'failed'` + `data.errorMsg`；
     「从没渲染过」是 400 + 原因（不是 failed，别显示成失败）。
6. **★ SYS-EXPORT-001**：导出若要带「文档完成情况」，读 `doc_status` / `published_time` / `published_by` 即可；
   `published_by` 是 `sys_user.user_id`（不是用户名），要显示人名得 join（工作台现状是只显状态徽标）。
   另：**导出的「已完成文档」清单要排除 `del_flag='1'` 的样本**（ticket 1010 那条既有病灶）。
7. **★ 所有人**：`/lqg/qc/{sampleId}/{docType}/publish|unpublish` 的 `docType` 是**连字符**
   （`sample-qc`），而 `/lqg/doc/{sampleId}/{docKind}/...` 的 `docKind` 是**下划线**（`sample_qc`）。
   本票在 `DocPublishService` 内部把两套都收口了，但**新写的调用方请照各自那一套**，
   别再引入第三种写法。
8. **★ D7 QA 门复跑建议**：`reseed.sh --yes` → `accept-run.py --ticket DOC-PUBLISH-001 --run`（先按 WARN-2 处理 `True`）
   → `node doc/waves/reports/DOC-PUBLISH-001/ui-check.mjs`（**28 项，需要 8093/8094 起来**）。
   只用 accept 的话，「预览面板真的在调 render / 页面图是真签名地址」这两条没有任何门。

---

## 7. 长进程与端口 / 收工自检

- 起环境：`bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --no-mp`（日志 `.tmp/qa-env/8094/`）
- 收工：`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --no-mp`（**只按 PID**；
  全程**没有** `pkill -f 'ruoyi-admin.jar'`）
- **8080 / 5432 / 6379 一个都没碰**；`lqg-dev-gotenberg` 全程 running（未停）；
  `code/miniapp/src/pages.json` 未被改（本票 `--no-mp`）
- 收工前 `bash doc/verify/reseed.sh --yes` 回确定性快照（accept / ui-check / 探针都改过库）

```
$ for p in 8094 8093 9202; do lsof -ti tcp:$p -sTCP:LISTEN; done    # 起着的 pid
10320          ← 后端 8094
10947          ← 工作台 8093
$ bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --web-port 8093 --no-mp
  ✓ 已按 PID 关停 工作台 dev(8093)（pid 10947）
  ✓ 已按 PID 关停 后端(8094)（pid 10320）
  ✓ 8094 已释放   ✓ 8093 已释放   ✓ 9202 已释放
$ for p in 8094 8093 9202; do lsof -ti tcp:$p -sTCP:LISTEN; done    # 三个端口全空
（无输出）
$ docker ps --format '{{.Names}} {{.Status}}' | grep gotenberg
lqg-dev-gotenberg Up (healthy)      ← 全程未停
```

```
$ git branch --show-current
integration
$ git status --porcelain
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/pdf/DocArtifactRows.java
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/render/DocAudiences.java
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/render/service/DocRenderService.java
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/qc/service/QcDocService.java
 M code/plus-ui/src/api/lqg/doc/index.ts
 M code/plus-ui/src/lang/lqg/qc.en_US.ts
 M code/plus-ui/src/lang/lqg/qc.zh_CN.ts
 M code/plus-ui/src/views/lqg/qc/editor/index.vue
 M doc/waves/state.json                    ← ★ 不是本票改的（进工作树时就是 M；本票没碰它）
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/publish/
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/publish/
?? code/plus-ui/src/lang/lqg/qc-publish.en_US.ts
?? code/plus-ui/src/lang/lqg/qc-publish.zh_CN.ts
?? code/plus-ui/src/views/lqg/qc/components/PreviewPane.vue
?? doc/waves/reports/DOC-PUBLISH-001/
```

**结论**：status = **failed**（唯一原因 = 票面 acc1 的 `True`/`true` 一字之差，实现侧 15 段与全部 counterfeit 均成立）；
accept = **1 / 2**（acc2 ✅）；人眼验收 28/28 ✅；单元测试 251/251 ✅；`pnpm build:prod` ✅。
