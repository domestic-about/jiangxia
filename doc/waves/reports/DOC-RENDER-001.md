# DOC-RENDER-001 · 完工报告

- **票**：DOC-RENDER-001（D6）· Word 渲染：三份 docx 模板（甲方原件改占位符）、poi-tl 渲染服务、内部版/外部版、按内容指纹缓存
- **分支**：`task/D6`（未切分支、未 push、未合、未动 `doc/waves/state.json` / `_manifest.json`）
- **accept**：**2 / 2 ✅**（`.tmp/doc-render-accept.json`，日志见本目录 `DOC-RENDER-001-acc{1,2}.sh.log`）
- **验收对象**：jar `code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar` @ `2026-09-22 17:44:45`，后端 8094（dev profile + `--api-decrypt.enabled=false`）
- **取证目录**：`doc/waves/reports/DOC-RENDER-001/`

---

## 0. 状态自检（3 级）

- ✅ 分支 `task/D6`（调度器注入的期望，未写死 `feature/dayN`）
- ✅ `depends_on` 全部 done：QC-MODEL-001（其报告 §2 的接口清单与 10 条坑已逐条读过并对齐）
- ✅ 扫 `doc/change-log.md`：涉及本票的只有 **CR-20260918-07**（第 4 条）——「外部版质控文档里的内部编号一格仍然留空，开关切换不会重出历史文档」，
  与 ticket §0 口径复述 2 一致，实现照此；CR-20260921-08 只是小程序视觉，不涉及本票
- ✅ 6 个 `blueprint_refs` 逐条 `python3 doc/authority/authority_lint.py show <锚>` 取到（**垫片路径现在可用**，无需 WARN）
- ⚠️ 见 §5 WARN-1（环境病灶）——不是票面冲突，已自行解决并落盘

---

## 1. 交付物

### 1.1 三份渲染出的 Word（内部版）+ 与甲方原件并排的截图

`rendered/`（都是**通过接口真渲染 + 签名链接真下载**的文件，不是本地拼的）：

| 文件 | 样本 | 说明 |
|---|---|---|
| `rendered/sample_qc-9000001005-internal.docx` | 9000001005 | 2 张真图（accept 1 那张上传图），字段全填 |
| `rendered/organoid_qc-9000001001-internal.docx` | 9000001001 | 该样本名下图片是 **seed 假图**（取不到字节）→ 图片位为空，字段全填 |
| `rendered/organoid_score-9000001001-internal.docx` | 9000001001 | 分值格 20/10/25/30，表尾合计 **85** |
| `rendered/merged-9000001001-internal.docx` | 9000001001 | 合并件（§2 要求，accept 不测）：三份依次拼接、每份另起一页，转 PDF 后 5 页、文字顺序正确 |

与甲方原件并排的截图各一张（左＝`_input/templates/*.docx` 原件，右＝本票渲染的内部版；用 LibreOffice 转 PDF 再 `pdftoppm -r 110` 出图，再并排拼）：

- `compare/sample_qc-甲方原件-vs-渲染内部版.png`
- `compare/organoid_qc-甲方原件-vs-渲染内部版.png`
- `compare/organoid_score-甲方原件-vs-渲染内部版.png`

**并排看要说的一句话**：表格结构、列宽、边框、字体（宋体/Times New Roman）、两句「注」的位置都没动 —— 因为模板是**拿甲方原件改字**出来的，
不是重画的（生成脚本 `make-doc-templates.py` 只做「空白格 → `{{占位符}}`」「示例文字 → 占位符」两件事，
每一处都用 `w14:paraId` 精确定位，改完打印 diff；生成后 `tblGrid` 与原件逐字节相同，见 §3 的探针）。

### 1.2 改了哪些文件

**tracked（`git status --porcelain` 只有这些）**

| 文件 | 说明 |
|---|---|
| `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml` | **M**：加 `com.deepoove:poi-tl:1.12.2`（exclude 它自带的 poi-ooxml）+ 显式钉 `org.apache.poi:poi-ooxml:5.4.1` |
| `…/ruoyi-admin/src/main/resources/db/migration/V202609261400__DOC-RENDER-001-doc-file.sql` | **新增**：`t_lqg_doc_file` |
| `…/ruoyi-admin/src/main/resources/db/migration/V202609261410__DOC-RENDER-001-doc-menu.sql` | **新增**：菜单/授权 |
| `…/ruoyi-lqg/src/main/resources/lqg/doc-templates/{sample_qc,organoid_qc,organoid_score}.docx` + `template-version.txt` | **新增**：三份模板 + 版本号 `1` |
| `…/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/render/**` | **新增**：16 个类（见 1.3） |
| `…/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/render/{DocFingerprintTest,DocTemplateContractTest}.java` | **新增**：18 个用例全绿 |
| `doc/waves/reports/DOC-RENDER-001/**` | **新增**：本报告 + 取证目录 |

**取号依据（Flyway）**

- D6 = `20260926`；库里已应用的最大版本 = `202609261310`（QC-MODEL-001）。Flyway `out-of-order=false` → 必须**单调递增**。
- 第一支照 `field-ssot.yaml` 里 `t_lqg_doc_file.migration` 的字面值取 `202609261400`（`touches` 的号段 `V20260926140*` 正好覆盖）。
- 第二支取 `202609261410`（同一分钟内 1400 之后的下一号，菜单/授权），命名 `V202609261410__DOC-RENDER-001-doc-menu.sql`。
- 两支都在 qa-up 起后端时被 Flyway 成功应用（`flyway_schema_history` 最新两条 = `202609261400 / 202609261410`，success=t）。

**新增的类 / 接口清单（16 个类 + 2 个端点）**

| 层 | 类 | 职责 |
|---|---|---|
| 常量 | `DocKinds` / `DocAudiences` | 四种 `docKind` / 两种 `audience` 的校验与中文名；URL 路径段与字典取值同名（`sample_qc`，非质控侧的连字符） |
| 模板 | `DocTemplate` | 三份 docx + `template-version.txt` 的唯一取用口（classpath，带缓存） |
| 指纹 | `DocContentHash` / `DocRenderModel` | sha256；`DocRenderModel.canonical()` = 指纹规范文本，**渲染数据与指纹同源**（同一对象） |
| 组装 | `DocRenderModelFactory` | 三张质控表 + 样本主档 + 图片位 + 附件 → `DocRenderModel`；含 merged 的成员筛选（读 `doc_status`） |
| 渲染 | `DocxRenderer` | poi-tl 填文本/图片（每位 3 个独立 run、多图等分单元格宽）；`merge()` 用 poi-tl `NiceXWPFDocument.merge` 拼合并件 |
| 图片 | `DocOssBytes` / `DocImageBytes` | 按 `sys_oss.service` 取字节（取不到→跳过+WARN）；让重复图片在 docx 里各自成为独立媒体（见 WARN-4） |
| 持久层 | `domain/DocFile`、`mapper/DocFileMapper`、`mapper/DocDictMapper` | `t_lqg_doc_file` 实体/mapper；性别字典标签（一条 SQL，避开 Redis 字典缓存） |
| 服务 | `service/DocRenderService` | 指纹 → 命中零写库返回 / 未命中 pending → 渲染 → 传 OSS → done；失败 → failed + error_msg |
| 接口 | `controller/DocRenderController`、`domain/vo/DocRenderVo`、`domain/vo/DocDownloadVo` | 见下 |

```
POST /lqg/doc/{sampleId}/{docKind}/render?audience=internal|external    权限 lqg:doc:render
GET  /lqg/doc/{sampleId}/{docKind}/download?format=docx&audience=…      权限 lqg:doc:query
   → data.status / ossId / contentHash / templateVersion / errorMsg / renderedTime / cached
   → data.url（10 分钟签名链接）+ data.fileName（文档名 + 内部编号 / 送检单号）
docKind ∈ sample_qc | organoid_qc | organoid_score | merged（与字典 lqg_doc_kind 同名）
OSS 对象键：lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前 12 位>.docx（audience 进路径）
```

`t_lqg_doc_file` 与 SSOT 逐列相符（`ddl_vs_ssot.py` exit 0），含公共字段 6 个、部分唯一索引 `uk_doc_file`（带 `WHERE del_flag='0'`）、普通索引 `idx_doc_file_sample`。

---

## 2. accept 逐条 ✅ / ❌ + 关键输出

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket DOC-RENDER-001 --run \
      --json .tmp/doc-render-accept.json --logdir .tmp/doc-render-accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ DOC-RENDER-001 acc1 [DATA]  渲染出的 Word：带出字段与填写字段都在、两句印死的注逐字保留、没有残留占位符、图片真的嵌进去了；外部版里找不到内部编号而内部 (1.9s)
  ✓ DOC-RENDER-001 acc2 [STATE] 渲染产物表与 SSOT 相符；指纹缓存成立：内容没变不重出、改了任何一处都重出、内外部各一份互不覆盖 (5.9s)
[ok] 结果落盘 .tmp/doc-render-accept.json
[run] 通过 2/2
```

### accept 1 · DATA —— ✅（`DOC-RENDER-001-acc1.sh.log`，NF1 去 `--fresh-module`）

```
$ 逐段输出（票面逐字重放的 9 个 true = 上传/GET/PUT/两次挂图/两次渲染/jq 断言全过）
true
true
true
true
true
✓ 全文 280 字，图片 2 张          ← /tmp/lqg-int.docx：内部版样本质控表
✓ 全文 273 字，图片 2 张          ← /tmp/lqg-ext.docx：外部版（比内部版少 7 字 = 内部编号 T-hga03）
✓ 全文 212 字，图片 0 张          ← /tmp/lqg-score.docx：评分表（无图片位）
```

逐条对照票面断言：

| 票面要求 | 证据 |
|---|---|
| 带出字段都在（样本主档） | 内部版含 `测试供体戊`（供体姓名，密文解密）、`A 医院`（来源单位）、`T-hga03`（内部编号） |
| 填写字段都在 | `P-RENDER`（患者编号）、`渲染探针诊断`（临床诊断）、`探针描述甲`（收样原始情况） |
| 两句印死的注逐字保留 | 样本质控表那句 `注：合格，活率≥70%；基本合格，50%~70%；不合格，＜50%或活细胞＜1x104。`（注意是「＜」与 ASCII 的 `1x104`，不是 `<`/`1×10⁴`）；评分表那句同样逐字 |
| 无残留占位符 | `--no-placeholder` 通过（poi-tl 的 `ClearHandler` 把没有数据的标签清空；三段模板文字在渲染期回落到 `QcDocRules` 常量） |
| 图片真的嵌进去了 | `--min-images 2` → 内部版 2 张、外部版 2 张（accept 上传一次、挂到 orig 与 observe 两个位） |
| 外部版搜不到内部编号 | `--not-contains "T-hga03"` 通过；内部版 `--contains "T-hga03"` 通过 |
| 模板里没有残留说明文字 | `--not-contains "要求图片可以放大"` 通过（三处都换成了图片占位符） |
| 样本主档带出的字段真的接了 | 若没接，`--contains "测试供体戊"` 必红（counterfeit 第 3 条） |

### accept 2 · STATE —— ✅（`DOC-RENDER-001-acc2.sh.log`，NF1+NF2）

```
✓ 1 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）   ← ddl_vs_ssot.py，exit 0
true                                                              ← PUT organoid-qc 的 .code==200
（mvn -q … test -Dtest='DocFingerprintTest' 静默通过；见下方单独取证）
```

指纹探针的逐段取值（`fingerprint-probe.txt`，票面同一序列手工重放）：

```
S1 = external:99a3cd87…:2102332965567623169:2026-09-22 17:43:33,
     internal:bfffdb32…:2102332965357907969:2026-09-22 17:43:33,
S2 = （与 S1 逐字节相同）                        ← 内容没变 → 零写库，oss_id 与 rendered_time 都不动
行数 = 2                                         ← 内外部各一行，互不覆盖
$ PUT /lqg/qc/9000001001/organoid-qc {"growthState":"指纹探针：已改"} → {"code":200}
S3 = external:99a3cd87…（**一字未变**）,
     internal:7abdbcc6…:2102332970659508226:2026-09-22 17:43:34   ← 只有 internal 那一行变了
S1 != S3 ✅
```

`DocFingerprintTest` 12 个用例（`unit-tests.txt`）：

```
$ mvn -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocFingerprintTest,DocTemplateContractTest' -Dsurefire.failIfNoSpecifiedTests=true
[INFO] Running org.dromara.lqg.doc.render.DocTemplateContractTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.dromara.lqg.doc.render.DocFingerprintTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`DocFingerprintTest` 的写法是**遍历模型的每一个输入翻一个值**（不是手写五六条）：
`texts` 的每一个标签（含从样本主档带出的 `source_unit_name` / `donor_name` / `internal_no`）、`images` 的每一个位与顺序、附件集、
模板版本、`audience`、`docKind`、评分表五个分值与四个 level、合并件三个成员的任一变化。
—— 以后有人往模型里加字段却忘了加进指纹，这里立刻红，不靠人记。

### accept 没覆盖、本票另跑的探针 —— ✅（`extra-probes.txt`）

```
1) merged（§2 要求）：status=done；539 字；顺序 样本质控表<类器官质控表<类器官质量评分表 OK；转 PDF 5 页（每份另起一页）
   merged 外部版：contains 测试供体甲、not-contains T-hli01 ✅
2) 样本还没建质控草稿 → 400 带原因（不是静默出空文件）：
   {"code":400,"msg":"这个样本还没有样本质控表（先在工作台打开一次质控页）"}
   {"code":400,"msg":"这个样本还没有已完成的质控文档，合并件无从拼起"}
   不认识的 docKind / audience → 400 带原因
3) format=pdf → 400（本票 §3 明确不做 PDF）
4) 未渲染就下载 → 400
5) 对象键形状：http://127.0.0.1:9000/ruoyi/lqg/doc/9000001001/merged/internal/6374ba628a0f.docx
   指纹前 12 位与 content_hash 逐字对得上；audience 在路径里 ✅
```

---

## 3. 实现里几个「做反了就红」的点（口径留痕）

1. **模板是从甲方原件改出来的**。`make-doc-templates.py` 只动 `<w:t>` 文字与插入 run：
   12 + 5 个空白格 → 文本占位符；「要求图片可以放大」→ **3 个各自独立的 run**（挤在一个 run 里，
   poi-tl 遇到空数据会按 `ClearHandler` 把整个 run 清空，一个位没图会把另外两张也抹掉）；
   评分表四个**纵合并**的「类器官质量评分」格各一个分值占位符 + 表尾合计行（复制末行 tcPr、去掉 `vMerge`）。
   表格 `tblGrid`：`[1835,1626,1400,1122,846,1444]` / `[4261,4261]` / `[2857,1968,1405,2440]` —— 与原件逐字相同。
2. **`content_hash` 与渲染数据同源**（`DocRenderModel` 一个对象出两份东西），所以「页面上改了、指纹没跟上」结构上不可能。
   覆盖：文档全部字段 + 样本主档带出字段 + 每个图片位的 oss_id **序列**（有序，换顺序也算变）+ 附件 oss_id 序列 + 模板版本 + audience。
3. **`audience` 参与缓存键与对象键路径**。外部版的 `internal_no` 在**模型里就是空串**，不是下载时抹 ——
   于是 external 的指纹、对象键、产物三者都与 internal 独立，谁后渲染都不会覆盖谁（accept 2 断「行数恰好 2」）。
4. **命中缓存是零写库**：`done` + 指纹一致 + 有 oss_id → 直接返回，连 `rendered_time` 都不碰（accept 2 的快照带它）。
5. **失败不甩 500**：渲染异常 → `failed` + `error_msg`（截断 480 字），下次调用可重试（DOC-PDF-001 的 accept 2 依赖这条行为）。
6. **进 Word 的图用 `preview_oss_id`**（QC-MODEL-001 的回落口径：无预览图时等于 `oss_id`），多张并排等分单元格宽度
   （图片位单元格宽度从模板 `tblGrid` 量出来：样本质控表图片位跨 3 列 = 4148 twips ≈ 276px，类器官质控表 4261 twips ≈ 284px）。
7. **三段模板默认文字只有一个真相源**：`QcDocRules.RECEIVE_DESC_DEFAULT / OBSERVE_DESC_DEFAULT / PRETREAT_DESC_DEFAULT`，
   渲染期对空值回落（见 WARN-6），代码里没有再手打一遍。

---

## 4. 越出 `touches` 的改动

**tracked 文件：没有。** `git diff --stat HEAD` 只有 `ruoyi-lqg/pom.xml`（touches 第 5 条，显式允许），
其余全是 touches 内新增 + 本票报告目录。

**一个 untracked 的越界：`code/deploy/dev/.env`**（**已被 `.gitignore:9 code/deploy/**/.env` 忽略，不进 git、不进交付物**）
—— 加了一行 `JAVA_TOOL_OPTIONS='-Dhttp.nonProxyHosts=localhost|127.0.0.1|*.local|local'`。
原因见 WARN-1：本机 macOS 系统代理让 JVM 把发往 `127.0.0.1:9000`（MinIO）的请求丢给代理，
**框架自带的 `POST /resource/oss/upload` 直接 500**，不改这一行 accept 1 永远红。
放在这个文件里是因为 `qa-up.sh` 起后端前会 `set -a; . code/deploy/dev/.env; set +a` —— 任何一次 qa-up 都自动带上，不依赖人记得 export。

---

## 5. WARN 清单（请逐条入账）

- **WARN-1 · 环境病灶：macOS 系统代理打断了 JVM 的所有 OSS 访问（本票踩到、已修、下游必踩）**。
  本机系统代理 `127.0.0.1:1081`，JDK 启动时把它灌成 `http.proxyHost/http.proxyPort`，
  而 JDK 的 `http.nonProxyHosts` **不含 `127.0.0.1`**（只有 `localhost`）→ AWS SDK v2 的 Netty 客户端把
  `http://127.0.0.1:9000` 的请求发去代理 → 连接被关。表现：
  `POST /resource/oss/upload` → 500「The service request was not made within 120 seconds of doBlockingWrite being invoked」（耗时 120397ms）。
  `curl` 不受影响（认 `NO_PROXY=127.0.0.1`），**只有 JVM 侧踩**，所以这个坑很隐蔽。
  独立复现与修法全程见 `oss-proxy-root-cause.txt`（含脱离 Spring 的 `OssProbe` / `AwsProbe`，源码在 `oss-probe/`；
  失败时 `ChannelDiagnostics` 里明写 `R:/127.0.0.1:1081`）。
  **下游（DOC-PDF-001 传 PDF/PNG、DOC-MP-* 取图、DOC-PROOF-001 打样）都会走同一条 OssClient 路径，务必确认这条修还在。**
- **WARN-2 · `sys_oss_config` 的 `minio` 行是若依基线原值，指向的是**另一个** MinIO 容器**。
  现值：`endpoint=127.0.0.1:9000 / bucket=ruoyi / ak=ruoyi / sk=ruoyi123` —— 落在 Docker 的 `dev-minio`
  （0.0.0.0:9000，RuoYi 同源凭据）；而本项目的 compose 起的是 `lqg-dev-minio`
  （`127.0.0.1:9002`，`minioadmin/minioadmin`，`LQG_MINIO_BUCKET=lqg`，**那个桶还没建**，PUT 会 404）。
  本票**没改这一行**（改它属于环境数据、且别的域在用 9000），上传与取图都走同一行所以自洽。
  但这是一颗埋着的雷：哪天有人按 `.env` 去 9002 找产物会找不到。**建议由环境 owner 统一**。
- **WARN-3 · 「取不到图」的处置是「跳过这张图」，不是「整份 failed」** —— 与 DOC-PDF-001 counterfeit 里的一句相反，见 §6 第 1 条。
- **WARN-4 · POI 会按字节给图片去重，必须给每次出现打标记**（`DocImageBytes`）。
  `XWPFDocument.addPictureData` 先比 checksum 再 `Arrays.equals`，**字节一样就复用同一个 part**。
  accept 1 是「一次上传、挂到 orig 与 observe 两个位」，不去重的话 `word/media/` 只有 1 份、
  `docx_check --min-images 2` 必红（两个格子都显示出来了，但媒体文件只有一个）。
  做法：PNG 在 `IEND` 前插一个 `tEXt` 块、JPEG 在 `SOI` 后插一个 `COM` 段（都是规范内的辅助段，解码器一律忽略），
  其它格式原样返回（宁可少一份媒体文件也不弄坏不认识的容器）。标记值取「位 + 第几张 + ossId」，确定性。
- **WARN-5 · 本票给 role 101/102 补授了 `system:oss:upload/query/download`（菜单 1600-1602）**。
  此前这三行**只授给 role 3（超管）**，而 accept 1 要求 `--as staff`（102）调 `POST /resource/oss/upload`
  —— 不授就是 403、`.data.ossId` 为空、accept 1 直接红。内部人员在质控页上传图片/附件本来就要这个接口，
  所以是**功能性授权**；但它确实越出了 DOC 域的权限行，登记在此。本票只加 `sys_role_menu` 行，没有改 1600-1603 的定义、没有建任何 C 页面菜单。
- **WARN-6 · 三段模板默认文字在渲染期做空值回落**。seed 里 `9000001005` 的样本质控表草稿行是**直接 INSERT 的**（不走「新建草稿」），
  `receive_desc/observe_desc/pretreat_desc` 三列是 NULL；不回落的话甲方样张上「收样描述」那句会凭空消失
  （accept 1 的 `--contains "样本按质控要求…"` 就是断这个）。回落值取 `QcDocRules` 的三个常量，没有再手打一份。
- **WARN-7 · `merged` 的「每份另起一页」用 run 级分页符而不是段落属性 `w:pageBreakBefore`**。
  后者是「这一段自己从新页开始」，拼到上一份末尾会先空出一整页（实测 6 页）；run 级 `w:br type="page"` 是在当前位置断页（实测 5 页，顺序仍对）。
  `merged` 的成员按 `doc_status='published'` 筛，一份都没有 → 400 带原因。
- **WARN-8 · 指纹里放了不进正文的两类输入：`doc_status` 与四个 `*level`**。发布/撤回会让文档重出（`doc_status` 变了）。
  这是刻意的：ticket §0 口径复述 3 要求「文档全部字段」，且代价只是多渲染一次，绝不会出现「内容变了还在发旧文件」。
- **WARN-9 · `download` 只认 `format=docx`**，传 `pdf` 明确 400 并说明归 DOC-PDF-001（不是悄悄给一份 docx）。
- **WARN-10 · `GET …/pages` 端点本票没做**（§3 明确不做 PDF/页面图，归 DOC-PDF-001），
  但它在 `doc/api-contract.md` 第 84 行的 DOC 一节里 —— DOC-PDF-001 落这个端点时**沿用本票的 `docKind`/`audience` 取值与 `data.status` 形状**即可。
- **WARN-11 · 包位置**：`field-ssot.yaml` 给 `t_lqg_doc_file` 的 `module` 是 `ruoyi-lqg/doc`，本票照此把新类放
  `org.dromara.lqg.doc.render.*`；QC-MODEL-001 的两张资源表（`t_lqg_doc_image/attachment`）仍在 `qc` 包（其 WARN-2），
  所以实体与 mapper 是跨包复用的（`org.dromara.lqg.qc.mapper.DocImageMapper` 等）。搬包不在本票 touches 内。

---

## 6. 给下游的坑（DOC-PDF-001 / DOC-PROOF-001 / DOC-PUBLISH-001 / DOC-MP-* / QC-WEB-*）

1. ★★ **DOC-PDF-001 的 counterfeit 里那句「取图失败时该文档应整体 failed 而不是静默出一份缺图的文件」与本票 accept 直接冲突，请按本票的行为对齐。**
   理由（两条硬证据）：
   - 本票 accept 2 要求 `POST /lqg/doc/9000001001/organoid_qc/render` 返回 `done`，而该样本名下**恰好有一张 seed 假图**
     （`sys_oss.service='seed'`，URL `https://seed.invalid/...`，取不到字节）；「取不到就 failed」→ 那条断言永远红。
   - 你那边 accept 1 的合并件就是 1001 的三份（合并件里必然包含这张假图），同样要求 `done` 且 ≥3 页。
   所以本票实现为：**取不到的图跳过 + 打 WARN，文档照样 `done`**（`DocOssBytes` 类注释写明了这条取舍）。
   你报告里要的「1001 样本质控表此刻的渲染状态与 error_msg」：**`status=done, errorMsg=null`**（`fingerprint-probe.txt` 末尾有原样输出）。
   真要「缺图就失败」，得先让 seed 的图变成可取的真图，那是 seed 的改动，不在本票范围。
2. ★ **取图请用 `preview_oss_id`，并把「哪张图进文档」当成指纹的一部分**：本票已经把每个图片位的 `preview_oss_id` 序列
   放进 `content_hash`；你新增 pdf/png 产物时，指纹要**复用同一份 `DocRenderModel.contentHash()`**
   （或把它作为输入的一部分），否则会出现「docx 重出了、PDF/页面图还是旧的」——那正是你 accept 2 第 5 段要防的事故。
3. ★ **表结构就绪，直接加行不要改我的行**：`t_lqg_doc_file` 的唯一键是
   `(sample_id, doc_kind, audience, file_format, page_no) WHERE del_flag='0'`。
   你的 pdf 恒 `page_no=0`、每页 png 从 1 起；`t_lqg_doc_file.render_status/error_msg/rendered_time/oss_id` 四个字段
   本票已经按「pending → done / failed」用起来了，DOC-PUBLISH-001 的 accept 也已经在用
   `… AND render_status='done' AND page_no IN (0,1)` 的判据 —— 别把 pdf/png 行塞进 docx 行的 `page_no=0`。
4. **`DocTemplate.version()` 是模板版本的唯一取用口**。你们要往模板里换中文字体（宋体 → 思源宋体/Noto Serif CJK），
   换完**必须把 `template-version.txt` 加一**（REQ-DOC-011 的 clarify 口径：换模板 = 换文件 + 版本号加一）——
   本票的指纹里带着版本号，加了就自动全量重出。改模板请沿用
   `doc/waves/reports/DOC-RENDER-001/make-doc-templates.py` 的「原件改字」路子（不要重画），并跑
   `DocTemplateContractTest`：它会断「两句注逐字」「占位符齐不齐」「没有残留『要求图片可以放大』」。
5. **模板里图片位是 `{{@<slot>_img1..3}}` 三个独立 run**（`slot ∈ orig|observe|pretreat|organoid_observe`，每位上限 3 见 `QcDocRules.MAX_IMAGES_PER_SLOT`）。
   要加/减图片位请同时改模板、`QcDocRules.slotsOf` 与 `DocxRenderer.SLOT_CELL_WIDTH_PX`（单元格宽度）。
   另外 PNG/JPEG 的「同图多次出现」靠 `DocImageBytes.distinct` 打标记，别绕过它。
6. **不要拿 `GET /lqg/qc/{sampleId}` 当渲染链路的探活**（QC-MODEL-001 的坑 6：它是「会写库」的读，还会建草稿）。
   本票的渲染链路只读三张质控表 + 样本主档；缺草稿直接 400 带原因，不会偷偷建一份。
7. **`audience` 是显式入参**，`/lqg/doc/**` 是内部接口，内部人员可以指定 `audience=external` 预览外部版
   （甲方样张核对就是这个用法）。真正的对外隔离在 `/mp/ext/**`（ADR-0004 咽喉）—— 外部接口只许取 `external`，
   而 external 产物里内部编号那一格**在模型里就是空的**（不是下载时抹），你直接发链接就行，不需要再过滤一遍。
8. **`GET /mp/int/doc/{id}/{docKind}/pages` 需要的 `images/attachments` 不是从 docx 里读的**：
   图片位与附件在 `t_lqg_doc_image` / `t_lqg_doc_attachment`（QC-MODEL-001 的表）上，`docId` 用 `t_lqg_qc_sample/organoid` 的 id，
   图片 URL 取 `preview_url`（点开看原图才用 `url`）。签名链接有效期：本票 download 用的是 **10 分钟**。
9. **DOC-PROOF-001 打样时**：`content_hash` / `template_version` / `file_format` / `audience` 都在 `t_lqg_doc_file` 上，
   直接 SELECT 即可（manifest 要的 `sha256` 得自己算产物字节）；注意内外部两份的 `content_hash` **天生不同**（audience 进指纹），
   别把「内外部 hash 不一样」当成缓存坏了。
10. **QC-WEB-***：本票落了两个 F 权限行 `5504 lqg:doc:render` / `5505 lqg:doc:query`（授给 101/102），
    **没有建 C 页面菜单**（沿用 QC 的 5501-5503 先例，C 菜单留给页面票）；另外给 101/102 补授了
    `system:oss:upload/query/download`（见 WARN-5）。

---

## 7. 长进程与端口

- 后端 8094 由 `bash doc/waves/tools/qa-up.sh --backend-port 8094 --no-web --no-mp` 起，收工用 `--down` **按 PID** 关停；
  **全程没有 `pkill -f 'ruoyi-admin.jar'`**，8080 / 5432 / 6379 一个都没碰，8093 / 9202 全程没起（本票只动后端 + 迁移 + 模板资源）。
- 本票是**唯一**碰 PG 5433 的会话（另一张票按纪律没并行派）。
- 环境里额外起过一次 `code/deploy/dev/.env` 的 JVM 参数（见 WARN-1），随 qa-up 生效、随 qa-up 停止。
- 收工状态见 §8。

---

## 8. 收工自检

```
$ git branch --show-current
task/D6
$ git status --porcelain          # 只有 §1.2 列的 6 项（1 个 M + 5 组新增），没有 D、没有越界 tracked 文件
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml
?? code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609261400__DOC-RENDER-001-doc-file.sql
?? code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609261410__DOC-RENDER-001-doc-menu.sql
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/render/
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/
?? doc/waves/reports/DOC-RENDER-001/
$ git diff --stat HEAD
 .../ruoyi-modules/ruoyi-lqg/pom.xml | 29 ++++++++++++++++++++++
 1 file changed, 29 insertions(+)
```

未改：`doc/waves/state.json`、`doc/waves/_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、
`doc/change-log.md`、`doc/verify/seed/`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`_input/`。
`docx_check.py` / `db.py` / `ddl_vs_ssot.py` / `reseed.sh` / `api.sh` / `accept-run.py` **都只调用、没改**。
`code/deploy/dev/.env` 被改了一行 —— 它 **gitignored**（`.gitignore:9`），不进 git、不进交付物，原因见 WARN-1。

后端 8094：`bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --no-web --no-mp`（按 PID），**已释放**（见 §9）。

---

## 9. 收工

```
$ bash doc/waves/tools/qa-up.sh --down --backend-port 8094 --no-web --no-mp
── qa-up --down（后端 8094）
  ✓ 已按 PID 关停 后端(8094)（pid 37927）
  ✓ 8094 已释放 · ✓ 8093 已释放 · ✓ 9202 已释放
```

后端 8094 已释放；8093（工作台）/ 9202（小程序 H5）本票全程没起，也是空闲的。
MinIO 里我为排查代理问题临时放的 3 个探针对象（`lqg-probe*.txt`）已删除（HTTP 204）。

**结论**：status = done；accept = **2 / 2 ✅**。
