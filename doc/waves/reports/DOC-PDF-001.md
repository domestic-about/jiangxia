# DOC-PDF-001 · 完工报告

- **票**：DOC-PDF-001（D6）· docx → PDF → 页面图片的渲染流水线 + 失败可见可重试 + 合并件
- **分支**：`task/D6`（未切分支、未 push、未合分支、未动 `doc/waves/state.json` / `_manifest.json`）
- **accept**：**2 / 2 ✅**（`.tmp/doc-pdf-accept.json`，日志 `doc/waves/reports/DOC-PDF-001/accept-run.txt` + `.tmp/doc-pdf-accept-logs/`）
- **验收对象**：jar `code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar`（qa-up 于 2026-09-22 18:10 重打并重启），后端 8094 pid 67406；转换容器 `lqg-dev-gotenberg`（`127.0.0.1:3010` → 容器 3000）
- **取证目录**：`doc/waves/reports/DOC-PDF-001/`（PDF/PNG/截图/逐条探针输出都在里面）

---

## 0. 状态自检（3 级）

- ✅ 分支 `task/D6`（调度器注入的期望；没有写死 `feature/dayN`）
- ✅ `depends_on` 全部 done：**DOC-RENDER-001**。其报告的「给下游的坑」10 条**逐条读过并对齐**（尤其第 1、2、3、4 条：取图缺失的处置、指纹复用、`t_lqg_doc_file` 的 `page_no` 分工、模板换字体的路子）
- ✅ 扫 `doc/change-log.md`（顶部 8 条 CR）：
  - **CR-20260921-08**（小程序视觉方向 A）：只动小程序视觉与 token，不涉及本票产物
  - **CR-20260918-07** 第 4 条：「外部版文档里内部编号一格仍留空、开关不作用于预渲染文档」→ 本票不改 `audience` 口径，外部版仍是模型层就空
  - 其余 CR 逐条核过（`impact` 里没有 DOC-PDF-001 的其它项）
- ✅ 6 个 `blueprint_refs` + **ADR-0005** 逐条 `python3 doc/authority/authority_lint.py show <锚>` 取到（垫片路径可用，无 WARN）：
  - `FLOW:F-DOC-01.step3`（转 PDF，超时 60 秒、失败记 error_msg）· `step4`（PDFBox 150 DPI，page_no 从 1 起）· `step5`（合并 = 先拼 docx 再整体转 PDF；只有一份时合并件 = 那一份）· `step6`（失败可见可重试）· `FIELD:t_lqg_doc_file.page_no`（png 从 1，docx/pdf 恒 0）· `FIELD:t_lqg_doc_file.error_msg`
- ✅ 外部前提自探：`gotenberg/gotenberg:8` 官方镜像**有 arm64 变体**（本机 `docker pull` 实测成功，1.71GB）→ 无硬阻塞；poppler 三件套可用

**结论**：3 级全过，无 STOP 项。

---

## 1. 交付物

### 1.1 三份 + 合并件（真渲染、真下载，不是本地拼的）

`rendered/`（都是通过接口真渲染 → 10 分钟签名链接真下载拿到的）：

| 文件 | 页数 | 说明 |
|---|---|---|
| `rendered/sample_qc-9000001001-internal.pdf` / `.docx` | 2 | 1001 样本质控表（3 个图片位，seed 的图取不到 → 跳过 + WARN） |
| `rendered/organoid_qc-9000001001-internal.pdf` / `.docx` | 1 | 1001 类器官质控表 |
| `rendered/organoid_score-9000001001-internal.pdf` / `.docx` | 2 | 1001 类器官质量评分表 |
| `rendered/merged-9000001001-internal.pdf` / `.docx` | **5** | 合并件：2+1+2，顺序 = 样本质控表 → 类器官质控表 → 类器官质量评分表 |
| `rendered/organoid_score-9000001001-p1.png` | — | pages 端点签发的第 1 页图（1275×1649，灰度极值 0/255） |

`rendered/pdf-forensics.txt` 是四份 PDF 的 `pdfinfo` + `pdffonts` + `pdftotext` 原样输出。**逐份都是同一组字体、全部嵌入**：

```
$ pdffonts doc/waves/reports/DOC-PDF-001/rendered/organoid_score-9000001001-internal.pdf
name                                 type              encoding         emb sub uni object ID
------------------------------------ ----------------- ---------------- --- --- --- ---------
BAAAAA+NotoSerifSC-Bold              Type 1            Builtin          yes yes yes     12  0
CAAAAA+NotoSerifSC-Regular           Type 1            Builtin          yes yes yes     22  0
DAAAAA+Tinos-Regular                 TrueType          WinAnsi          yes yes yes     32  0
EAAAAA+Tinos-Regular                 TrueType          WinAnsi          yes yes yes     37  0
FAAAAA+NotoSerifSC-Regular           Type 1            Builtin          yes yes yes     17  0
GAAAAA+Tinos-Regular                 TrueType          WinAnsi          yes yes yes     27  0

$ pdfinfo … | grep ^Pages
Pages:           2
```

★ **只有 NotoSerifSC 与 Tinos**（既没有 DejaVu，也没有 Liberation）—— 这正是 accept 1 第 4/5 段与 counterfeit 第 1/2 条要的形态。

### 1.2 与甲方原件的并排截图（给 DOC-PROOF-001 用）

`compare/`（左＝`_input/templates/*.docx` 在本机 LibreOffice 出的第 1 页；右＝本票在 **Gotenberg 容器**里出的第 1 页；110 DPI，生成脚本 `make-compare-shots.py`）：

- `compare/sample_qc-甲方原件-vs-容器渲染.png`
- `compare/organoid_qc-甲方原件-vs-容器渲染.png`
- `compare/organoid_score-甲方原件-vs-容器渲染.png`

**并排看要说的一句话**：版式（表格线、列宽、行高、文字落点）没动，**动的只有字形** ——
eastAsia「宋体」→ **Noto Serif SC**（思源宋体的 Google 发行版），ascii/hAnsi/cs「Times New Roman」→ **Tinos**（Times New Roman 的度量兼容开源替代，所以拉丁字母与数字的**宽度**与原件一致，中文只是换了一套开源字形）。
模板改动的机器证据在 `font-before.txt` / `font-after.txt` / `font-verify.txt`：三份模板的 `tblGrid` 与 DOC-RENDER-001 记录的逐字相同（`[1835,1626,1400,1122,846,1444] / [4261,4261] / [2857,1968,1405,2440]`），只改了 `<w:rFonts>` 与 `theme1.xml` 的字体名。

### 1.3 改了哪些文件

**tracked（`git status --porcelain` 的 M，11 个）**

| 文件 | 说明 |
|---|---|
| `code/deploy/common/gotenberg/**`（新增，含 `Dockerfile`、`README.md`、`fonts/`） | 基于官方 `gotenberg/gotenberg:8` 加一层：`COPY fonts/` 到 `/usr/local/share/fonts/lqg` + `fc-cache`。字体 4 个（Noto Serif SC Regular/Bold 24MB、Tinos Regular/Bold 1.1MB）+ 两份 **OFL-1.1 许可文件**，全部进仓库（ticket §2「字体文件要进仓库，许可一并放」）。**不用**宋体/微软雅黑/黑体等商业字体 |
| `code/deploy/dev/docker-compose.yml` | **M**：加 `gotenberg` 服务（build 上面那层；`127.0.0.1:3010:3000` 只在内网暴露；`--api-timeout=60s`；healthcheck 探 `/health`） |
| `code/deploy/test/docker-compose.yml` | **M**：加同名服务（**不发布宿主端口**，内网走服务名）+ backend 的 `LQG_GOTENBERG_URL=http://gotenberg:3000` 与 `depends_on: gotenberg (healthy)` |
| `…/ruoyi-admin/src/main/resources/application-dev.yml` | **M**：`lqg.doc.gotenberg.url=http://127.0.0.1:3010`、`timeout-seconds=60` |
| `…/ruoyi-admin/src/main/resources/application-test.yml` | **M**：`lqg.doc.gotenberg.url=${LQG_GOTENBERG_URL:http://gotenberg:3000}`、`timeout-seconds=60` |
| `…/ruoyi-lqg/pom.xml` | **M**：加 `org.apache.pdfbox:pdfbox:3.0.5`（本模块此前类路径上没有 PDF 库；F-DOC-01.step4 的决策值字面就是 PDFBox） |
| `…/ruoyi-lqg/src/main/resources/lqg/doc-templates/{sample_qc,organoid_qc,organoid_score}.docx` | **M**：字体名替换（脚本 `make-font-templates.py`，就地改属性、不重画） |
| `…/lqg/doc-templates/template-version.txt` | **M**：`1` → `2`（REQ-DOC-011：换模板 = 换文件 + 版本号加一 → 指纹全量失效重出） |
| `…/ruoyi-lqg/.../doc/render/service/DocRenderService.java` | **M**：流水线扩成 docx → pdf → png，同一指纹，任何一步失败 → 整体 failed（ticket §2 点名要改这个类） |
| `…/ruoyi-lqg/.../doc/render/controller/DocRenderController.java` | **M**：加 `GET …/pages`；`download` 支持 `format=pdf` |
| `code/plus-ui/src/api/lqg/doc/index.ts`、`code/plus-ui/src/views/lqg/doc/index.vue` | **M/新增**：工作台「文档渲染状态」页（failed 显示原因 + 「重新生成」→ 调 render）与它的 API 客户端 |

**新增（untracked）**

- `…/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/pdf/**`：**7 个类** + 4 个 VO（清单见 §1.4）
- `…/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/pdf/**`：**3 个测试类 / 12 个用例**
- `…/ruoyi-admin/src/main/resources/db/migration/V202609261420__DOC-PDF-001-doc-console.sql`
- `doc/waves/reports/DOC-PDF-001/**`（本报告 + 取证）

**Flyway 取号依据**

- D6 = `20260926`；库里已应用的最大版本 = `202609261410`（DOC-RENDER-001）→ 本票取 **`202609261420`**（>1410，`out-of-order=false` 下安全）。
- 菜单号段 5500-5599（`V202609261300`/`1410` 的注释）：5501-5503 = QC 三个 F、5504-5505 = DOC 两个 F、5510 留给 QC-WEB-*，本票取 **5520**（DOC 的 C 页面菜单）。
- 已在 dev 库成功应用（`flyway_schema_history` 最新 = `202609261420`），`/system/user/getInfo` 里 staff 拿到 `lqg:doc:query` + `lqg:doc:render`。

### 1.4 新增的类 / 接口清单

| 层 | 类 | 职责 |
|---|---|---|
| 转换 | `pdf/PdfConvertService` | `POST {gotenberg}/forms/libreoffice/convert`；**单线程执行器排队**；整个调用（含排队）预算 60 秒；连不上/HTTP 非 2xx/超时/返回不是 PDF 一律抛一句人能读的话；**显式不走系统代理** |
| 转换 | `pdf/MultipartForm` | 手搓 `multipart/form-data`（字段名 `files`），纯函数可单测；`Gotenberg-Output-Filename` 用 ASCII slug（中文文件名会 400） |
| 出图 | `pdf/PdfPageRasterizer` | PDFBox 3 `Loader.loadPDF` + `PDFRenderer.renderImageWithDPI(i, 150, RGB)` → 每页 PNG（纯函数） |
| 出图 | `pdf/PageImageService` | 每页上传 OSS + 落 `png/page_no` 行（**page_no 从 1 起**）+ **页数变少时软删多余旧页行** |
| 产物 | `pdf/DocArtifactStore` | docx/pdf/png 的统一上传（对象键 `lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前12位>[-pN].<ext>`）+ 10 分钟签名链接 |
| 产物 | `pdf/DocArtifactRows` | `t_lqg_doc_file` 的行读写：header（docx/page_no=0）、`upsertPending` / `markDone` / `markFailed`、`pngPages`（**只认当前指纹 + done**）、`prunePagesAbove`、软删 |
| 查询 | `pdf/DocPagesService` | 组装 `pages` 返回：状态取自 header 行；done 才给页面图；`images/attachments` 从 `t_lqg_doc_image/attachment` 取（merged = 三份并集） |
| VO | `pdf/domain/vo/{DocPagesVo,DocPageItemVo,DocPagesImageVo,DocPagesAttachmentVo}` | 票面形状 + 一个 `errorMsg` 键（见 WARN-6） |

```
POST /lqg/doc/{sampleId}/{docKind}/render?audience=internal|external         权限 lqg:doc:render
GET  /lqg/doc/{sampleId}/{docKind}/download?format=docx|pdf&audience=…       权限 lqg:doc:query
GET  /lqg/doc/{sampleId}/{docKind}/pages?audience=…                          权限 lqg:doc:query   ← 本票新增
     data = {status, errorMsg, docKind, audience, contentHash, templateVersion,
             pages:[{pageNo,url}], images:[{url,previewUrl}], attachments:[{fileName,fileSize,url}]}
docKind ∈ sample_qc | organoid_qc | organoid_score | merged（与字典 lqg_doc_kind 同名）
```

工作台新增一页：`/doc-console` → `code/plus-ui/src/views/lqg/doc/index.vue`（菜单 5520，`lqg:doc:query`；`pnpm build:prod` 通过、`getRouters` 里有这条路由 —— 见 §5 WARN-2 的「隐藏菜单其实不隐藏」实测）。

---

## 2. accept 逐条 ✅ / ❌ + 关键输出

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket DOC-PDF-001 --run \
      --json .tmp/doc-pdf-accept.json --logdir .tmp/doc-pdf-accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ DOC-PDF-001 acc1 [DATA]  PDF 里的中文是真字不是豆腐块（能抽出文字、字体已嵌入且不是回退字体）；页面图片页数与 PDF 页数一致且图不是空白；合并文件 (1.9s)
  ✓ DOC-PDF-001 acc2 [STATE] 失败看得见、能重试：转换服务不可用时状态为 failed 且带原因、旧产物不再被当成最新返回；恢复后重新生成成功 (10.9s)
[ok] 结果落盘 .tmp/doc-pdf-accept.json
[run] 通过 2/2
```

### accept 1 · DATA —— ✅（跑了两遍都绿；`accept-forensics.txt` 是这一条产物的逐项取证）

| 票面断言 | 实测 |
|---|---|
| `render` 回 `done` | `{"status":"done","errorMsg":null,"contentHash":"fa9054c9…","templateVersion":"2"}` |
| `pdftotext -layout` 有「类器官质量评分表」 | 命中 1 次（标题行原样抽出） |
| `pdftotext` 有「药敏实验失败风险较大」 | 命中 1 次 |
| `pdffonts` 的 emb 列不是全 no | 6 行全 `yes`（见 §1.1） |
| 字体名匹配 `Noto\|SourceHan\|思源` | `NotoSerifSC-Bold` / `NotoSerifSC-Regular` |
| **不**出现 `DejaVu\|Liberation` | 没有（`! pdffonts … grep -qiE 'DejaVu\|Liberation'` 通过） |
| `pages` 长度 == `pdfinfo` 的页数、pageNo == 1..N | `PAGES=2`，`pages=[1,2]`，`.data.status=="done"` |
| 页面图 ≥1000×1400 且不是白图 | `(1275, 1649)`，灰度极值 `(0, 255)` |
| 合并件按固定顺序含三份 | 位置 样本质控表=0 < 类器官质控表=257 < 类器官质量评分表=329 ✅ |
| 合并件页数 ≥3 | **5** 页（2+1+2，每份另起一页） |

**counterfeit 逐条对账**：容器没装中文字体（方框）→ 不会，中文字体在镜像层里且 `fc-list` 认到；回退到 DejaVu → 不会；字体没嵌入 → 不会（emb=yes）；页面图另渲染/页数对不上 → 不会，页面图就是**同一份 PDF** 出的；全白 → 不会（极值 0/255）；合并按完成时间拼 → 不会，顺序写死在 `DocKinds.MERGED_ORDER`；少一份 → 不会（5 页 + 三个标题都在）。

★ counterfeit 最后那句要求的「1001 样本质控表此刻的渲染状态与 `error_msg`」（本票最后一次实测，accept 之后）：

```
$ POST /lqg/doc/9000001001/sample_qc/render?audience=internal
{"status":"done","errorMsg":null,"contentHash":"d1d85c94","templateVersion":"2"}
```

即 **`status=done` / `errorMsg=null`**：该样本名下 4 张图都是 seed 假地址（`sys_oss.service='seed'`），docx 阶段取不到 → **跳过 + WARN**，文档照出 done。这与 counterfeit 的字面相反，是跨票冲突的**已裁定分类**，见 §5 **WARN-1**（本票如实记录，未改上游、未改票面）。原样日志：

```
渲染取图失败，跳过这张图 ossId=9000004001 service=seed file=seed/lqg/img-1.jpg：
  org.dromara.common.oss.exception.OssException: 系统异常, 'seed'配置信息不存在!
```

### accept 2 · STATE —— ✅（`failure-retry-probe.txt` 是同一序列的手工重放，带库内快照）

| 票面断言 | 实测（手工重放的原样输出） |
|---|---|
| 停掉 gotenberg 后改分再 render → `failed` + `errorMsg` 非空 | `{"status":"failed","errorMsg":"转换服务不可用（POST http://127.0.0.1:3010/forms/libreoffice/convert 失败：java.net.ConnectException）","hash":"fa9cb732"}` |
| 恢复后 `pages` 仍是 `failed` 且 `pages` 为空 | `{"status":"failed","errorMsg":"转换服务不可用（…）","pages":[]}` |
| 恢复后重新生成成功 | `{"status":"done","errorMsg":null,"cached":false}`，`pages=[1,2]` |
| counterfeit：状态仍 done / 返回改分之前的旧图 | 不会 —— 见下面的库内快照 |
| counterfeit：失败后不能重试（要重启后端） | 不会 —— 转换是**每次调用现做**，恢复后立刻成功（accept 2 实测 ~1s） |
| counterfeit：不管成败都把 gotenberg 启回来 | 那是 accept 脚本自己 `RC=$?` 后再 `start`；本票额外确认了容器最终状态（见 §7） |

「旧产物保留但不再被当成最新返回」的库内快照（失败发生后）：

```
docx/0 status=failed oss=2102340199655706626 hash=fa9cb732   ← header 行：新指纹 + failed + 原因；oss_id 还是上一版那份 Word
pdf/0  status=done   oss=2102340199685066754 hash=e2a380fd   ← 上一版的 PDF 还在（旧指纹，不被返回）
png/1  status=done   oss=2102340199995445249 hash=e2a380fd   ← 同上
png/2  status=done   oss=2102340200024805378 hash=e2a380fd   ← 同上

$ GET …/download?format=pdf  → {"code":400,"msg":"这份文档还没生成（先 POST …）"}
$ GET …/download?format=docx → {"code":400,"msg":"这份文档还没生成（先 POST …）"}
```

### accept 没覆盖、本票另跑的探针

```
1) 页数变少 → 多余的旧页行软删（prune-probe.txt）
   merged 由 3 份（5 页）变 2 份（3 页）：1/2/3 行原地更新成新指纹 c74cf730，4/5 行 del_flag=1（旧指纹 e40056fc 原样保留）
   $ pages → {"status":"done","pages":[1,2,3],"templateVersion":"2"} ✅
   （怎么造出「少一份」：/lqg/qc/{id}/{docType}/unpublish 还没实现 —— 那是 DOC-PUBLISH-001 的 D7 范围，
     实测 404；所以用一次**调试用的库写**把评分表打回 draft，等价于「撤回一份」，探针跑完 reseed 还原）

2) 图片位与附件（extra-probes.txt）
   sample_qc: pages=[1,2] images=3 attachments=1
   organoid_qc: pages=[1] images=1 attachments=0
   organoid_score: pages=[1,2] images=0 attachments=0        ← 评分表本来就没有图片位
   merged: pages=[1,2,3,4,5] images=4 attachments=1          ← 三份的并集（3+1+0 / 1+0+0）

3) 缓存语义（DOC-RENDER-001 的 accept 不能被本票弄坏）
   内容没变再 render → 同一个 contentHash、cached=true、零写库；改了分 → 指纹变 → 整条流水线重出

4) 单元测试 38 个全绿（unit-tests.txt），其中本票 12 个：
   PdfPageRasterizerTest 3（页数一致 / A4@150DPI ≥1000×1400 且非白 / 垃圾 PDF 不静默返回空）
   PdfConvertServiceTest 5（连不上 / url 空 / 空 Word 不发请求 / HTTP 503 带响应体 / 非 PDF 被拒且请求体是 files 字段）
   MultipartFormTest 4（边界、CRLF、字段名、ASCII slug）
   另外 DOC-RENDER-001 的 DocFingerprintTest 12 + DocTemplateContractTest 6 **仍全绿**（模板换字体没破坏契约）
   ExtChokepointContractTest 4 + MockLoginGuardContractTest 4 也全绿

5) 内外部两套产物共存（audience-probe.txt）：9000001005/sample_qc 分别按 internal / external 渲染
   → 库里 8 行（每边 docx/0 + pdf/0 + png/1 + png/2）、两套指纹不同（a2ceb799 vs bfb2d09c）、
   外部版 PDF 里 `T-hga03` 出现 0 次（内部编号在模型层就是空的，不是下载时抹）
   同时留了一条反例证据：1006 没有样本质控表草稿 → `{"code":400,"msg":"这个样本还没有样本质控表（先在工作台打开一次质控页）"}`（不静默出空文件）
```

---

## 3. 实现里几个「做反了就红」的点（口径留痕）

1. **一个指纹、三种产物**。pdf 与每页 png 的行都写的是同一个 `content_hash`；`pages` / `download?format=pdf` 只认「与 header 行指纹一致」的行。于是「Word 是新的、预览图/PDF 是旧的」在**缓存层结构上**不可能发生 —— 这正是 accept 2 第 5/6 段防的事故。
2. **「整体 failed」记在 docx 那一行（header）**。一行一份产物的表结构里，`(docKind, audience)` 的状态只能有一个落点；`download` / `pages` 都读它。失败时**只**改状态与 `error_msg`，`oss_id` 一个字节都不动 → 旧产物保留。
3. **失败时不产生新 OSS 垃圾**：先渲染 docx、**先转 PDF**，两者都成了才上传。所以「转换服务挂了」的那次调用不会在桶里留半份产物。
4. **转换前先转再传** 还带来一个副作用：失败后 header 的 `oss_id` 仍指向**上一版**那份 Word（不是空）——「旧产物保留」的字面含义。
5. **单线程执行器 + 两层超时**：外层 `future.get(60+15s)`、内层 HTTP 60s，先报出来的那句带上「到底是排队超时还是 HTTP 超时」。Gotenberg 自己还带 `--api-timeout=60s`。
6. **显式不走代理**：`PdfConvertService` 的 `HttpClient` 挂了一个返回 `Proxy.NO_PROXY` 的 `ProxySelector`。本机 macOS 系统代理会被 JDK 灌成 `http.proxyHost`（DOC-RENDER-001 的血泪），而 Gotenberg 是内网服务 —— 这条不依赖任何人记得 export `NO_PROXY`。
7. **模板字体的 Latin 也一起换**（Times New Roman → Tinos）。票面字面只说「宋体换成思源/Noto」，但不换 Latin 就会回退到 **Liberation Serif** → accept 1 第 5 段（`! grep DejaVu|Liberation`）直接红。Tinos 与 Times 同度量，版式不漂。见 WARN-7。
8. **theme1.xml 也改了**（majorFont/minorFont 的 `a:latin/a:ea/a:cs` 与 `script="Hans"`）。所有 `<w:rFonts>`（含 `styles.xml` 的 `docDefaults`）已经显式写死字体，主题本来是死路；改它是为了堵住「将来谁加一个不带 rFonts 的 run 就悄悄回退」这条退路。
9. **合并 = 先拼 docx 再整体转 PDF**（不是拼 PDF）：`renderer.merge()` 出来的那一份 Word 直接交给 Gotenberg → 合并版的 Word 与 PDF 同源（ticket §0 口径复述 4）。
10. **`page_no` 的分工**照 SSOT：docx / pdf 恒 `page_no=0`，png 从 1 起。pdf 与 docx 靠 `file_format` 区分（部分唯一索引里有它），没有抢行。

---

## 4. 越出 `touches` 的改动（**请入账**）

`touches` 里只有 `doc/pdf/**` + 测试 + `code/deploy/common/gotenberg/**` + 两份 compose + `doc-templates/**` + `application*.yml`。**票面 §2 自己点名要改的东西有两处不在 `touches` 里**，另有工作台那一步需要一个落点：

| # | 文件 | 为什么必须动 | 性质 |
|---|---|---|---|
| 1 | `…/ruoyi-lqg/pom.xml` | 加 `pdfbox 3.0.5`。F-DOC-01.step4 / ADR-0005 的决策值就是「PDFBox 按 150 DPI 出 PNG」，模块此前没有 PDF 库 | 票面 §2 要求（touches 漏列） |
| 2 | `…/doc/render/service/DocRenderService.java` | 票面 §2 原话「`DocRenderService` 的流水线扩成 docx → pdf → png」 | 票面 §2 要求（touches 漏列，`touches` 只给了 `doc/pdf/**`） |
| 3 | `…/doc/render/controller/DocRenderController.java` | 票面 §2 要求落 `GET …/pages` 与 `download?format=pdf`；这两个端点的控制器在 `doc/render/controller` | 同上 |
| 4 | `…/db/migration/V202609261420__DOC-PDF-001-doc-console.sql` | 工作台那一步（failed 显示原因 + 「重新生成」）需要一条可路由的菜单，否则页面没有落点 | **新增越界**（见 §5 WARN-2 的实测副作用） |
| 5 | `code/plus-ui/src/api/lqg/doc/index.ts`、`code/plus-ui/src/views/lqg/doc/index.vue` | 同上；`api/lqg/doc/**` 本来就在 **DOC-PUBLISH-001** 的 touches 里，会继续被它用 | **新增越界** |

**tracked 文件里没有删除、没有重写历史**；`doc/requirements.yaml` 的 text、`doc/authority/*.yaml`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`api.sh`、`_input/` **一个都没碰**（`reseed.sh` / `db.py` / `api.sh` / `accept-run.py` 只调用）。

---

## 5. WARN 清单（请逐条入账）

- **WARN-1 · 「取图失败」的跨票冲突，本票按「分两类」实现（与上游对齐）**。
  - ① **docx 阶段个别图片取不到** = **跳过该图 + WARN，文档照出 done**（DOC-RENDER-001 的既定行为；本票不改上游）。实测：9000001001 名下 4 张 `service='seed'` 假图 → `sample_qc` = `done` / `errorMsg=null`；日志见 `image-skip-warn.txt`。
  - ② **流水线步骤本身失败**（Gotenberg 连不上 / 超时 / 返回非 PDF / HTTP 非 2xx / PDFBox 出图失败）= **该 `(docKind, audience)` 整体 `failed` + 原因**。这就是本票 accept 2 断的那一条。
  - 票面 counterfeit 那句字面（「图片地址取不到 → 整份 failed」）**在 ① 上仍然冲突**：与 DOC-RENDER-001 的 accept 2（1001/organoid_qc 必须 done）和本票 accept 1（合并件必须 done 且 ≥3 页）无法同时成立。本票**没有**改上游、**没有**改票面，按分类实现并如实记录。
- **WARN-2 · 工作台那一步：宿主页面还没建 + 「隐藏菜单」在这个代码库里其实不隐藏（实测）**。
  - /lqg/qc/editor/index（质控文档页）是 **QC-WEB-001** 的、**还没建**；`api/lqg/doc/**` 是 DOC-PUBLISH-001 的。所以本票把「failed 显示原因 + 重新生成」做成一页独立的 `views/lqg/doc/index.vue`（走 `/lqg/doc/**` 三个接口），**没有**去建编辑页。
  - ★ 实测发现：本代码库的 `RouterVo.hidden` **从未被赋值**（`grep -rn setHidden` 无命中），所以 `visible='1'`（隐藏）**不会**让菜单从侧边栏消失 —— 我建的 5520 会作为「文档渲染状态」出现在侧边栏，`getRouters` 里那条路由也没有 `hidden` 键。**连带**：`QC-WEB-001` 的 accept 1 断 `.hidden == true`，照现状会红；先补 `router.setHidden("1".equals(menu.getVisible()))`（或改断言/改静态路由）再写那张票。
  - 若不能接受这个侧边栏项：由后续拥有工作台菜单口径的票（QC-WEB-001 / DOC-PUBLISH-001 / SYS-WEB-*）删掉 5520 或把它挪进子树即可（本票的页面组件与 API 客户端都可直接复用）。
- **WARN-3 · `sys_oss_config` 的 minio 行指的是 9000 上另一个 MinIO（DOC-RENDER-001 的 WARN-2 仍在）**。产物实际落在 `dev-minio`（`127.0.0.1:9000`, bucket `ruoyi`），不是 compose 起的 `lqg-dev-minio`（9002 / bucket `lqg`）。本票沿用、没改（上传与读图走同一行，自洽）。**下游按 `.env` 去 9002 找产物会找不到。**
- **WARN-4 · 本机构建 gotenberg 镜像要用 `DOCKER_BUILDKIT=0`**。buildx 要写 `~/.docker/buildx/...`，被本会话的文件沙箱拒（`operation not permitted`）；legacy builder 正常：
  `DOCKER_BUILDKIT=0 docker build -t lqg-gotenberg:8 code/deploy/common/gotenberg`。
  `docker compose up -d gotenberg` 只要 `lqg-gotenberg:8` 已在本地就不会触发 build，accept 的 `stop/start` 更不会。
- **WARN-5 · 测试机的 gotenberg 镜像要在测试机上就位**（SYS-STAGING-001 的坑，见 §6）。
- **WARN-6 · `pages` 的 `data` 里多了一个 `errorMsg` 键**（票面形状 `{status,pages,images,attachments}` 之外）。工作台要在 failed 时显示原因（F-DOC-01.step6），只能从响应里拿。加键不改形状，DOC-MP-*/QC-WEB-* 直接用即可；若要求严格等于票面形状，删掉这个字段不影响任何 accept。
- **WARN-7 · 模板的 Latin 字体也从 Times New Roman 换成了 Tinos**（票面字面只说宋体）。理由见 §3 第 7 条（不换就有 Liberation → accept 红）。Tinos = Times New Roman 的度量兼容开源字体（OFL-1.1），所以拉丁数字的**宽度与原件一致**，甲方样张上看到的差异只有中文字形（并排截图已附，给 DOC-PROOF-001 做差异清单）。
- **WARN-8 · 老版本产物只靠指纹过滤，不清理**。旧 docx/pdf/png 行与 OSS 对象都留着（ticket §2「已有的旧产物保留」），只有页数变少时软删多余**页行**。也就是说**存储会随每次重出单调增长**（每次重存一份 docx+pdf+N 张 PNG）。清理由谁负责建议明确（SYS-PROD-001 的备份/清理，或 DOC-PUBLISH-001 的发布策略）。
- **WARN-9 · `thumbnail/preview` 口径沿用不改**：`pages.images[].previewUrl` 用 `preview_oss_id`（QC-MODEL-001 的回落：没有预览图时等于 `oss_id`），进 Word 的也是它；点开看原图才用 `url`。
- **WARN-10 · `GET …/pages` 在「这份文档从没渲染过」时返回 400 带原因**（不是空壳、也不是 404）。与 `download` 同一个口径；DOC-MP-* 的 list 只列 done 的文档，正常不会踩到。

---

## 6. 给下游的坑（DOC-PROOF-001 / QC-WEB-* / DOC-PUBLISH-001 / DOC-MP-* / SYS-STAGING-001）

1. ★ **DOC-PROOF-001 打样必须在 Gotenberg 容器里出 PDF**：宿主（macOS）没有 Tinos / Noto Serif SC 的话会回退，出来的样张跟线上不一样。容器服务名 `gotenberg`（dev 宿主 `127.0.0.1:3010`）。当前的样张产物可直接从 `doc/waves/reports/DOC-PDF-001/rendered/` 取（三份 + 合并件 + 它们的 docx），字体差异清单看 `compare/` 里三张并排图与 §1.2 那句话。
2. **模板版本现在是 `2`**（`template-version.txt`）。**改模板 = 用 `doc/waves/reports/DOC-PDF-001/make-font-templates.py` 的路子就地改 + 版本号加一**；改完跑 `DocTemplateContractTest`（断两句注逐字、占位符齐、没有残留「要求图片可以放大」）。指纹里带着版本号，加一即全量重出（docx/pdf/png 三样一起）。
3. **`t_lqg_doc_file` 的行分工（别抢行）**：`docx/page_no=0` 是**整体状态**（header），`pdf/page_no=0`，`png/page_no≥1`。DOC-PUBLISH-001 的 accept 那条
   `render_status='done' AND page_no IN (0,1)` + `--col-set internal:docx,internal:pdf,internal:png,external:…` 现在**正好**落在 docx / pdf / 第一页 PNG 上（每份 audience 三行、共 6 行，形状与本票实现自洽）；要更稳一点的话，筛选里带上 `file_format` 比靠 `page_no` 推断清楚。
4. **QC-WEB-001**：① 上面 WARN-2 的 `hidden` 断言问题（不补框架就会红）；② 质控文档页的 failed 块可以直接用 `@/api/lqg/doc` 的 `renderDoc/getDocPages`（`views/lqg/doc/index.vue` 里那段 alert + 「重新生成」可以整块搬进编辑页）；③ 菜单号 **5520 已被占**（DOC 的 C 页面），你要的 5510 没被动。
5. **DOC-PUBLISH-001**：`code/plus-ui/src/api/lqg/doc/index.ts` 里已经有 `renderDoc / getDocPages / getDocDownload` 三个客户端与类型（`DocPagesVO.errorMsg` 是 failed 的原因）；PreviewPane 取页面图就是 `getDocPages(...).data.pages[].url`（**10 分钟签名链接**，别缓存别存库）。
6. **DOC-MP-001 / DOC-MP-002**：`/mp/int|ext/doc/**` 那组端点还没实现（不在本票范围）。你们要的 `pages` 形状与 `audience` 口径已经就位：内部接口 `audience` 是显式入参（可指定 external 预览外部版），外部接口固定 external（ADR-0004）。merged 的 `images/attachments` 是三份的并集、顺序 = 合并件顺序。
7. **SYS-STAGING-001（test compose）**：`code/deploy/test/docker-compose.yml` 新增了 `gotenberg` 服务 —— **不发布宿主端口**（测试机 3000 是别的项目的 next-server），backend 用 `LQG_GOTENBERG_URL=http://gotenberg:3000` + `depends_on: service_healthy`。两件前置：① `gotenberg/gotenberg:8`（**amd64**）基础镜像必须先在测试机上就位（本机是 arm64，镜像搬不过去）；② `deploy.sh` 要把 `code/deploy/common/gotenberg/` 一起 rsync 上机，否则 `build:` 没有上下文。dev 侧宿主端口选的是 3010（3000/3001 被本机别的 node 服务占着）。
8. **所有人**：`PdfConvertService` 的 `HttpClient` 是**不走代理**的。如果哪天有人把它换成 RestTemplate/OkHttp，务必保留 `nonProxyHosts` 或同样的 NO_PROXY 语义 —— 否则会重演 DOC-RENDER-001 查了很久的那个「发往 127.0.0.1 的请求被丢给 macOS 系统代理、120 秒后 500」的病灶。

---

## 7. 长进程与端口 / 收工自检

```
$ git branch --show-current
task/D6
$ git status --porcelain          # §1.3 的 11 个 M（含模板与 compose）+ 7 组新增；没有 D（删除）
 M code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-dev.yml
 M code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-test.yml
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml
 M …/doc/render/controller/DocRenderController.java
 M …/doc/render/service/DocRenderService.java
 M …/lqg/doc-templates/{sample_qc,organoid_qc,organoid_score}.docx
 M …/lqg/doc-templates/template-version.txt
 M code/deploy/dev/docker-compose.yml
 M code/deploy/test/docker-compose.yml
 ?? …/db/migration/V202609261420__DOC-PDF-001-doc-console.sql
 ?? …/lqg/src/main/java/org/dromara/lqg/doc/pdf/
 ?? …/lqg/src/test/java/org/dromara/lqg/doc/pdf/
 ?? code/deploy/common/gotenberg/
 ?? code/plus-ui/src/api/lqg/doc/index.ts
 ?? code/plus-ui/src/views/lqg/doc/index.vue
 ?? doc/waves/reports/DOC-PDF-001/
$ git diff --stat HEAD | tail -3
 11 files changed, 208 insertions(+), 161 deletions(-)
```

- **全程没有 `pkill`**；关进程一律按 PID。**8080 / 5432 / 6379 一个都没碰**（Kevin 的本机服务）。
- **后端 8094**：pid `67406`（qa-up 于 18:10 重打 jar 后重启）。收工命令：
  `bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --no-web --no-mp`
- **gotenberg 容器 `lqg-dev-gotenberg`：留着 running**（`127.0.0.1:3010→3000`，`healthy`）。
  理由：它是 dev compose 的常驻服务（和 postgres/redis/minio 同性质），**后续 DOC-PUBLISH-001 / QC-WEB-* / DOC-MP-* 的 accept 都要用它**（accept 2 自己会 stop/start 它，跑完是 running）。`qa-up --down` 不管它（它不属于 qa-up 的进程表）。**如果要彻底清掉**：
  `docker compose -f code/deploy/dev/docker-compose.yml stop gotenberg`（或 `rm -f` 该容器）；镜像 `lqg-gotenberg:8` 与基础镜像 `gotenberg/gotenberg:8` 建议保留。
- **本机 LibreOffice**：只用于出「甲方原件 vs 容器渲染」的并排截图；因为沙箱不许写 `~/Library/Application Support/LibreOffice`，用
  `soffice -env:UserInstallation=file://$PWD/.tmp/lo-profile --headless …`（profile 落在工作区 `.tmp/`）。
- 库状态：探针与 accept 结束后都 `reseed.sh --yes` 回到确定性快照（最后一步已确认）。
- 未改：`doc/waves/state.json`、`doc/waves/_manifest.json`、`doc/requirements.yaml` 的 text、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`_input/`。

**结论**：status = **done**；accept = **2 / 2 ✅**；单元测试 38/38 ✅；`pnpm build:prod` ✅。

---

## 附：取证文件索引（`doc/waves/reports/DOC-PDF-001/`）

| 文件 | 内容 |
|---|---|
| `accept-run.txt` / `.tmp/doc-pdf-accept.json` / `.tmp/doc-pdf-accept-logs/` | accept 2/2 的运行结果与逐条日志（NF1） |
| `accept-forensics.txt` | accept 1 三份产物的 pdfinfo/pdffonts/pdftotext、页面图尺寸极值、merged 顺序位置、1001 样本质控表的 status/error_msg |
| `rendered/*.pdf` `*.docx` `*-p1.png` | 三份 + 合并件的真产物（真渲染、真签名链接下载） |
| `rendered/pdf-forensics.txt` | 四份 PDF 的 pdfinfo + pdffonts + pdftotext 原样输出 |
| `compare/*.png` | 甲方原件 vs 容器渲染的并排截图（3 张） |
| `font-before.txt` `font-after.txt` `font-verify.txt` `template-sha256-*.txt` | 模板字体改动的 before/after/机器校验（含 tblGrid 逐字不变、残留字体清单） |
| `image-skip-warn.txt` | 「取图失败 → 跳过 + WARN」的后端日志原样（WARN-1 的证据） |
| `failure-retry-probe.txt` | 失败可见可重试 + 旧产物保留的手工重放（含库内快照与 errorMsg 原文） |
| `prune-probe.txt` | 页数变少 → 多余旧页行软删 |
| `extra-probes.txt` | 四份文档的 pages 输出（页数/图片/附件）与页面图尺寸极值 |
| `audience-probe.txt` | 内外部两套产物共存（8 行 / 两套指纹 / 外部版不含内部编号）+ 缺草稿时 400 带原因 |
| `render-1001-internal.txt` | 四份文档在 1001 上的 render 返回值（status/errorMsg/hash/版本） |
| `unit-tests.txt` | mvn test 的原样输出（38/38） |
| `make-font-templates.py` `make-compare-shots.py` | 两个可复现脚本 |
