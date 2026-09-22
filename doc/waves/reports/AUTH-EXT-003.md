# AUTH-EXT-003 · 完工报告

- **ticket**：AUTH-EXT-003（track AUTH / phase D7 / size M）—— D7 的外部文档那一半：清单 / 页面图 / 下载链接全部过咽喉
- **status**：**done**
  - ★ **票面字面原有 2 处缺陷**（§3）：acc1 第 4 段的「病灶」在当前上游不存在 · acc2 第 3/4 段的文件路径少了子包段。
    本 impl **没有改票面**，按规矩如实报主会话 → **主会话已修正这 7 处字面量**（commit `9911e65`，逐字见 §3.7），
    **实现零改动**。
  - **accept 逐字重放 2/2 绿**（修正后的票面）：acc1 ✓ `9.1s` · acc2 ✓ `4.2s`（`accept-result-FINAL-fixed-ticket.json`）
  - 修正前的原始票面逐字重放 **0/2**，两处失败点与根因见 §3.2–§3.4（保留，因为它们是这次修正的依据）
  - 修正前另做了**最小改动重放 2/2 绿**（只改那 7 处字面，其余逐字不动）：acc1 `EXIT=0` · acc2 `EXIT=0`
  - 对抗性探针 **46/46 PASS**（`probes/ext003-probes.sh`）
  - 上游回归抽跑 **7/7 全绿**（AUTH-EXT-001/002 · EMBED-MODEL-001 ×2 · CRYO-MODEL-001 · SAMPLE-MP-001）
  - `ExtChokepointContractTest`（需求层 fixture，逐字节 `cmp` 校验）**4/4 绿**；本票自加 `ExtDocShapeContractTest` **6/6 绿**
- **分支**：`task/D7`（开工 `git branch --show-current` 确认过；**未切分支 / 未 push / 未 merge**；未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 20:51:47`（epoch 1790081507，**本票源码全在里面**），8094 后端
  （dev profile + `--api-decrypt.enabled=false`）：原始票面那两轮 listener = **PID 1812**（state 文件 `.tmp/qa-env/8094/state.json` 记的就是它，`lsof -p 1812` 命中该 jar）；
  最终（修正后票面）那轮 listener = **PID 25262**（jar 未变、未重新打包，源码无一处新于 jar）。**两次都已按 PID 关停**，8094 现为空（见 §9）。
- **迁移**：**一支都没有加**（无 DDL / 无菜单 / 无授权 → 不取号；库里已应用的最大版本仍是 `202609261430`，见 §2.1）
- **只读区未动**：`_input/`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/api-contract.md`、`doc/verify/{seed/**,gen_seed.py,api.sh,fixtures/**,reseed.sh,db.py}`、`doc/waves/state.json`、`doc/waves/_manifest.json`（`git status --porcelain` 对这七个路径**全空**）
- **没碰**：8080/5432/6379（8080 全程为空）；关进程**只按 PID**，全程**没有** `pkill -f 'ruoyi-admin.jar'`
- **产物**：后端 **7 个新类**（ext 包 6 + doc 域读口 1）+ **1 个新测试** + **2 处既有类的小改**；取证目录 `doc/waves/reports/AUTH-EXT-003/`

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D7`（全程没切分支） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `DOC-PUBLISH-001`：`DocPublishService` / `DocArtifactsRows.markStale` / `DocRenderService.invalidateMerged` 都在盘且被本票真依赖；`AUTH-EXT-002`：`ExtEmbedAssemblyService` / `ExtScopeService` 在盘。另：`DOC-PUBLISH-001` 的两条 accept 现在 **2/2 绿**（`accept-run.py --ticket DOC-PUBLISH-001`，见 `upstream-regression.txt`） |
| 扫 `doc/change-log.md` 顶部 CR | ✅ PASS | 顶部三条都核过：**CR-20260918-07** 第 4 条（外部版文档里内部编号一格仍留空、开关不作用于预渲染文档 → 本票**不**给文档 VO 加 `internalNo`）· **CR-20260921-08**（视觉方向 A，明说工作台不受影响）· CR-20260917-04（预览页顶部切换条 → `sampleId` 筛选）。无一条与本票实现口径冲突 |
| 6 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | 6 个锚全部 `active`；`FLOW:F-DOC-02.step1` 的 produces（「外部只见可见样本集合内的、外部版已渲染成功的文档」）与 `step3`（「外部只能拿 audience=external 的文件」）逐字落进 §1 |
| `ADR-0004` / `ADR-0005` 取过 | ✅ PASS | I4（ext 包只有 `ExtScopeServiceImpl` 持 Mapper）→ 本票新增的读口落在 **doc 域**；I2（只能返回 `Ext*`）→ 三个 handler 全返回 ext 包 VO；ADR-0005（audience 两份独立产物）→ 下载键只出 `/external/` |
| 环境可用（8094 / PG 5433 / Redis 6380 / MinIO 9002-9003 / Gotenberg 3010） | ✅ PASS | 四个容器全程在跑（`lqg-dev-gotenberg` 未停）；后端起停只按 PID |
| 「本栈已知的坑」表逐条过 | ✅ PASS | 判成败一律用 `--bizcode` / `jq -e` 断业务码，并断库内真值与副作用（本次**没有**踩到 HTTP 恒 200 那条） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

`ADR-0004` 结构化口径里 `implementation_status` 仍是 `"not-yet-implemented"`；AUTH-EXT-002 已提过一次。本票也**没改** ADR（只读区），请主会话在 AUTH 域收口后统一改。

---

## §1 口径复述（ticket §0 点名「最容易做反的」5 点，逐条对 accept 核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **外部能拿到的文档 = 可见样本 ∩ `doc_status='published'` ∩ 外部版渲染 `done`** | 可见集合：`ExtScopeService.visibleSampleIds`；命中集合：`DocExternalQueryService.available`（一处判据，清单 / 预览 / 下载三处共用） | 探针 P15–P19（草稿不给：1004 的类器官质控表、1005 的样本质控表）· P20–P24（外部版置 `failed` → 清单少一行 + 预览 404 + 下载 404，重渲染后恢复）· acc1 第 5/7 段（集合逐字） |
| 2 | **`audience` 写死 external，请求里带 `audience=internal` 不能生效** | ext 侧三个 handler **签名里没有** `audience`；doc 域读口所有调用点写死 `DocAudiences.EXTERNAL`（7 处） | acc1 第 12 段 `/external/` √ 且 `/internal/` ✗ · 探针 P39（预览页图 URL 也只出 `/external/`）· P40（下载 URL）· 声明期守卫 `ExtDocShapeContractTest#audienceIsNeverABoundInputOnTheExternalDocPath`（三份源码的**代码行**里 `audience` 出现 0 次） |
| 3 | **10 分钟签名链接；签发前再核对象键的 audience 段** | `DocArtifactStore.signedUrlOrFail`（TTL 10min，产物在私有桶）；`DocExternalQueryService.requireExternalObjectKey` 先核 `sys_oss.file_name` 含 `/external/` 再签 | acc1 第 13 段：`curl` 真下载 PDF + `pdftotext` 抽到「类器官质量评分表」· 探针 P43/P44（docx 头两字节 `504b` = OOXML zip；pdf 抽到表名）· P45/P46（内部版键含 `/internal/`、与外部版不是同一个对象） |
| 4 | **不可见 / 未完成 / 未渲染成功 → 一律按「不存在」回 404** | `ExtScopeService.assertVisible` → `ServiceException("样本不存在", 404)`；`DocExternalQueryService.requireAvailable` → `ServiceException("文档不存在", 404)`（**不区分原因**，免得「还是草稿」泄露内部进度） | acc1 第 9/10 段 · 探针 P7（未核验者取 1001）· P9（软删 1010）· P13（不存在的 id）· P15–P17（草稿）· P21/P22（外部版 failed） |
| 5 | **三种文档都给外部，不按类型挑；外加 `merged`** | `DocExternalQueryService.rowsOfSample` 遍历 `DocKinds.MERGED_ORDER` + `merged` | acc1 第 5 段 1001 的三份都在（`sample_qc` / `organoid_qc` / `organoid_score`）· 探针 P25（`merged` 渲染完即进清单） |

**★ 第 1 条的第三款我做了加强，并因此**推翻**了上游给的一条口径** —— 见 §6 WARN-3：
`DOC-PUBLISH-001` 交接说「外部可见还要 `header.content_hash == 此刻算出来的指纹`」。实测这条**无效**：`invalidateMerged` 会把 header 的 `content_hash` **改写成此刻该有的指纹**（`DocArtifactRows#markStale`），比较当场成立，而桶里那份 docx 还是旧成员拼的。本票改成锚在**产物**上的判据（当前指纹下有页图 + PDF 与 header 同版 —— 正是内部 `pages`/`download` 本就用的那两道闸），探针 P28–P30 从红转绿。

---

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway 迁移：**一支都没有**（以及为什么）

- 外部文档接口**不挂菜单、不建表、不加权限行**：`/mp/ext/**` 的角色闸是既有的 `lqg_external`（AUTH-GROUP-001 落的），
  `t_lqg_doc_file` / 三张质控表都是上游票建好的，本票**只读**。
- 库里已应用的最大版本 = **`202609261430`**（QC-WEB-001，实测 `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1`）。
  **给 D7 之后要加迁移的票留一句：版本号必须 > `202609261430`**（`spring.flyway.out-of-order=false`）。
- 本票因此**没有**取号、没有建 `V202609261440__…sql`，也不存在 out-of-order 风险（AUTH-EXT-002 撞过一次，本票没撞）。

### 2.2 后端 · ext 包（`touches` 内）

**新增**

| 文件 | 职责 |
|---|---|
| `ext/controller/ExtDocController.java` | `/mp/ext/doc`：list / {sampleId}/{docKind}/pages / {sampleId}/{docKind}/download，**类级 `@SaCheckRole("lqg_external")`**；只转发不查库；**两个 handler 都没有 `audience` 入参** |
| `ext/service/ExtDocAssemblyService.java` | 拼装（**零 `*Mapper` 字段**）：可见集合 → doc 域读口 → 对外 VO；`sampleId` 先与可见集合求交；内存切页；排序「同一样本相邻」 |
| `ext/domain/bo/ExtDocQueryBo.java` | 列表筛选：`sampleId?` / `docKind?` / `publishedBegin?` / `publishedEnd?`（显式无参构造转调 `PageQuery`） |
| `ext/domain/vo/ExtDocPagesVo.java` | 预览形状 `{docKind, status, pages}` —— **无**失败原因 / **无**产物指纹 / **无**模板版本 / **无**内部编号 |
| `ext/domain/vo/ExtDocPageItemVo.java` | 一页图 `{pageNo, url}` |
| `ext/domain/vo/ExtDocDownloadVo.java` | 下载 `{url, fileName}` |
| `src/test/java/.../ext/ExtDocShapeContractTest.java` | **6 例**纯反射契约：四个 VO 字段集合**恰等于**白名单 / 不含内部专用键 / `ExtDocQueryBo` 只有四个筛选 / controller 的角色注解与三个路径 / **代码行里不许出现 `audience`** |

**修改**

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `ext/domain/vo/ExtDocVo.java` | 给 `totalScore` 加 `@JsonInclude(NON_NULL)`（+1 import） | 票面 §2：「`totalScore` 只在评分表行上有」——非评分表行现在连键都不出（AUTH-EXT-001 建的 6 键形状不变，禁字一个没多） |
| `ext/service/ExtSampleAssemblyService.java` | 注入 `ExtDocAssemblyService`；`detail()` 里 `vo.setDocs(List.of())` → `docsOfSample(sampleId)`；删掉因此不再引用的 `ExtDocVo` import | ticket §2：「`ExtSampleDetailVo.docs` 接上同一份查询」 |

**接口清单（本票新增，全部类级 `@SaCheckRole("lqg_external")`）**

```
GET /mp/ext/doc/list?sampleId=&docKind=&publishedBegin=&publishedEnd=&pageNum=&pageSize=
      可见样本 ∩ doc_status=published ∩ 外部版产物完整；行 = ExtDocVo{sampleId, submitNo,
      donorNameMasked, docKind, publishedTime, totalScore?}；同一份文档按样本分组相邻
GET /mp/ext/doc/{sampleId}/{docKind}/pages      页面图（10 分钟签名链接）；不可用 404
GET /mp/ext/doc/{sampleId}/{docKind}/download?format=docx|pdf   签名链接；不可用 404
docKind ∈ sample_qc | organoid_qc | organoid_score | merged（与内部 /lqg/doc/** 同一套下划线取值）
★ 没有 audience 参数 —— 请求里带 audience=internal 只是被 Spring 忽略的陌生查询参数
```

### 2.3 后端 · doc 域（**越出 `touches`，见 §5**）

| 文件 | 职责 |
|---|---|
| `doc/service/DocExternalQueryService.java`（新增） | **对外的读口**（被调方）：`rowsOfSamples` / `rowsOfSample` / `available` / `pages` / `download`。`audience` 在 7 个调用点写死 `EXTERNAL`；`available` 是清单/预览/下载三处**唯一**判据；签发前核对象键 |

**明确没有动**：`DocRenderService` / `DocPagesService` / `DocArtifactRows` / `DocArtifactStore` / `DocPublishService` / `DocRenderModelFactory` / 任何 `*Mapper` / `ExtScopeService(Impl)` / `ExtInternalNoSwitch` / 上游 SAMPLE / EMBED / CRYO 域 —— **一个字节都没改**。根 pom、`ruoyi-lqg/pom.xml`、`application*.yml` 也没动。

---

## §3 accept 逐条 ✅ / ❌ + 关键输出（ticket §4.2）

### 3.0 关于 `--fresh-module` 与 Maven 三参数（本 agent 沙箱限制，既有 WARN）

- `--fresh-module ruoyi-lqg`（只在 acc1 第 3 段出现）在 runner 里被 **NF1** 去掉：`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` → 带它恒 `exit 2`。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边：

```
jar mtime        : 2026-09-22 20:51:47（epoch 1790081507）
lqg src newer    : []                    ← find ruoyi-modules/ruoyi-lqg/src -newer <jar> 为空
8080（Kevin）    : 全程为空，未碰
8094 listener    : PID 1812（.tmp/qa-env/8094/state.json 的 backend_pid；lsof -p 1812 命中 jar ×2）
state.started    : 2026-09-22T12:55:33Z（20:55:33 本地）> jar mtime
嵌套 jar 复核     : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含本票全部新类
                   （ExtDocController / ExtDocAssemblyService / ExtDocQueryBo /
                    ExtDocVo / ExtDocPagesVo / ExtDocPageItemVo / ExtDocDownloadVo /
                    DocExternalQueryService + $DocExternalRow）
Flyway 启动日志   : "Successfully validated 23 migrations"（本票没加迁移，仍是 23 支；库内 count(*) = 23）
```

- acc2 那行 `mvn … test` 在 runner 里被 **NF2** 补了 `-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`（**连续 9+ 张命中的既有 WARN，本票不重复计数**）。

### 3.1 最终结论：**accept 2/2 绿**（票面经主会话修正后逐字重放）

```
$ python3 doc/waves/tools/accept-run.py --ticket AUTH-EXT-003 --run \
      --json .tmp/auth-ext003-accept-final.json --logdir .tmp/auth-ext003-accept-logs-final
[run] 2 条 accept，单条超时 900s
  ✓ AUTH-EXT-003 acc1 [API]   外部文档清单钉死在 seed 上：同组可见、草稿不给、外部版没渲染成功的不给、异组按不存在返回；带 audience=internal 也拿不到内部版；链接里的对象键是外部版 (9.1s)
  ✓ AUTH-EXT-003 acc2 [STATE] 四条结构性不变量在加了文档接口之后仍然成立；新增的 VO 不带内部专用字段 (4.2s)
[ok] 结果落盘 .tmp/auth-ext003-accept-final.json
[run] 通过 2/2
```

acc1 的关键输出（逐字，完整见 `accept-runners/ext003-acc1-verbatim-FINAL.sh.log`）：

```
sample_qc:done            ┐ 第 4 段：1001 三份外部版产物状态（库内真值，--col-set 精确相等）
organoid_qc:done          │
organoid_score:done       ┘
true    ← 第 5 段：清单集合 4 行 + 评分表 totalScore=85 + 键里没有 internalNo
true    ← 第 6 段：extC（同单位异组）空
true    ← 第 7 段：extB `sampleId=1001` 三份
true    ← 第 8 段：extC 带 `sampleId=1001` 空（猜 id 换不到东西）
true    ← 第 9 段：extC 取 1001/organoid_score/pages → 404
true    ← 第 10 段：extA 取 1004/organoid_qc/pages → 404（草稿）
true    ← 第 11 段：extA 取 1001/sample_qc/pages → 200（它确实渲染成功了）
true    ← 第 14 段：详情 docs 三份
（第 12/13 段是 grep / curl+pdftotext，静默通过：链接里只有 /external/，且真下下来的 PDF 里抽到「类器官质量评分表」）
```

acc2 的关键输出：静默通过（`cmp` 逐字节无差异 + `mvn ExtChokepointContractTest` 4/4 + 三个文件路径都在 + 禁字 grep 无命中 + 外部打 `/lqg/doc/**` 得 `403`）。

### 3.2 修正前的原始票面：逐字重放 ❌ 0/2（保留，作为本次票面修正的依据）

```
[run] 2 条 accept，单条超时 900s
  ✗ AUTH-EXT-003 acc1 [API]   外部文档清单钉死在 seed 上：… → exit 1 (7.1s) 日志 .tmp/auth-ext003-accept-logs/AUTH-EXT-003-acc1.sh.log
  ✗ AUTH-EXT-003 acc2 [STATE] 四条结构性不变量在加了文档接口之后仍然成立；新增的 VO 不带内部专用字段 → exit 1 (4.3s)
[run] 通过 0/2
[run] ⛔ 2 条未登记的红（= 产品断言不成立）：AUTH-EXT-003 acc1; AUTH-EXT-003 acc2
```

**acc1 死在票面第 4 段**（逐字输出）：

```
[FAIL] 集合不相等：多出 ['organoid_qc:done', 'sample_qc:done']，缺少 ['organoid_qc:failed', 'sample_qc:failed']
sample_qc:done
organoid_qc:done
organoid_score:done
```

**acc2 死在票面第 3 段**（`bash -x` 逐行，见 `accept-runners/ext003-acc2-verbatim-xtrace.txt`）：

```
+ cmp doc/verify/fixtures/java/ExtChokepointContractTest.java …/ExtChokepointContractTest.java   ← 过
+ (cd code/RuoYi-Vue-Plus && mvn … test -Dtest=ExtChokepointContractTest)                        ← 过
+ test -f code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/ExtDocController.java
                                                                                                 ← 死在这里
```

### 3.3 ★ 票面缺陷 1：acc1 第 4 段的「病灶」在当前上游**不存在**

**票面原文**（accept 1 的 counterfeit）：「清单只按「已完成」过滤、不看外部版渲染成没成 → 会多出 1001 的 sample_qc 与 organoid_qc（**seed 里它俩的图片是假地址，外部版渲染必然失败**——第 4 段先确认这个病灶真的在）」。

**实测这个病灶不在**：1001 的三份外部版**全部渲染成功**（上面那三行 `*:done` 就是票面那条 SQL 的输出）。

**根因（上游已裁定，且任务书 §1.1 也这么写）**：`DocOssBytes` 的类注释逐字写着
「取不到一律返回 `null`，不抛异常 …… 取不到的图**跳过并打 WARN**，文档照样出，页面上少一张图不会比「送检方永远拿不到文档」更糟」，
且点名「DOC-PDF-001 的 counterfeit 里写的是「应整体 failed」，与本票 accept 冲突（**issue #217 裁定①**）」。
`DOC-PUBLISH-001` 的 WARN-3 登记的也是同一处冲突。

**连带 4 处期望偏差**（都由这同一个前提推出）：

| 票面段 | 票面期望 | 实测（正确值） |
|---|---|---|
| 4 | `sample_qc:failed, organoid_qc:failed, organoid_score:done` | `sample_qc:done, organoid_qc:done, organoid_score:done` |
| 5 | 清单 = `1001:organoid_score` + `1004:sample_qc`（2 行） | 4 行（1001 的三份 + 1004 的样本质控表） |
| 7 | `sampleId=1001` → `["organoid_score"]` | `["sample_qc","organoid_qc","organoid_score"]` |
| 11 | `1001/sample_qc/pages` → `404` | `200`（它确实渲染成功了，就该给） |
| 14 | 详情 `docs` → `["organoid_score"]` | 三份 |

**最小改动重放**（`accept-runners/ext003-acc1-premise-fixed.sh`，只改上表那 5 处期望，其余 10 段逐字不动）：

```
$ bash doc/waves/reports/AUTH-EXT-003/accept-runners/ext003-acc1-premise-fixed.sh
sample_qc:done
organoid_qc:done
organoid_score:done
true      ← 第 5 段：清单集合 + totalScore=85 + 无 internalNo 键
true      ← 第 6 段：extC 空
true      ← 第 7 段：extB sampleId=1001 三份
true      ← 第 8 段：extC 带 sampleId=1001 空
true      ← 第 9 段：extC 取 1001/organoid_score/pages → 404
true      ← 第 10 段：extA 取 1004/organoid_qc/pages → 404（草稿）
true      ← 第 11 段：extA 取 1001/sample_qc/pages → 200 + pages 非空 + 键只有 pageNo/url
true      ← 第 14 段：详情 docs 三份
ACCEPT-1-PREMISE-FIXED EXIT=0
```

（第 12/13 段是 `grep`/`curl`+`pdftotext`，静默通过 —— `curl -sSf` 真把 PDF 下下来并抽到「类器官质量评分表」。）

★ **本 impl 不改票面**（任务书明令）。建议修法：把 1001 的「病灶」换成**真能控制**的那一条 ——
探针 P20–P24 已经证明「外部版 `render_status='failed'` → 清单 / 预览 / 下载三处同时消失」，
把第 4 段改成「先用 psql 把 1001/organoid_score 的外部 header 置 failed → 断言三处都不给 → 恢复」，
同一票的 counterfeit 意图（只按 doc_status 过滤会红）照样被钉住，且不依赖上游的取图行为。

### 3.4 ★ 票面缺陷 2：acc2 第 3/4 段的文件路径**少了子包段**

| 票面字面 | 盘上（也是本仓 5 个既有 ext controller 的一致约定） |
|---|---|
| `…/lqg/ext/ExtDocController.java` | `…/lqg/ext/**controller**/ExtDocController.java` |
| `…/lqg/ext/ExtDocVo.java` | `…/lqg/ext/**domain/vo**/ExtDocVo.java` |
| `…/lqg/ext/ExtDocPagesVo.java` | `…/lqg/ext/**domain/vo**/ExtDocPagesVo.java` |

- `ExtDocVo.java` 这一条**最能证明是笔误**：那个文件**本票开工前**就在 `ext/domain/vo/`（AUTH-EXT-001 建的占位 VO，票面 §0 自己也把 AUTH-EXT-001 列为必读上游）。
- `touches` 是 `ext/**`（含子包），`ExtChokepointContractTest` 的 I1/I2/I3/I4 都**递归扫包**，所以子包位置完全合规。
- **没有**为了迎合票面字面把类搬到 `ext` 根：那会与既有 `ExtSampleController` / `ExtOrganoidController` / `ExtEmbedController` / `ExtUnitController` / `ExtProfileController` 的布局打架，也白搬一个上游票建好的 VO（属「照测试改结构」的反模式）。
- **最小改动重放**（`accept-runners/ext003-acc2-paths-fixed.sh`，只补全这三个路径，其余逐字不动）：

```
$ bash doc/waves/reports/AUTH-EXT-003/accept-runners/ext003-acc2-paths-fixed.sh
ACCEPT-2-PATHS-FIXED EXIT=0
```

（= `cmp` 逐字节无差异 + `mvn ExtChokepointContractTest` 4/4 + `test -f` 三个文件都在 + 禁字 `grep` 无命中 + 外部打 `/lqg/doc/**` 得 `403`。）

### 3.5 对抗性探针 46/46（`probes/ext003-probes.sh`，完整输出 `probes-transcript.txt`）

```
PASS P1-staff打外部文档清单-403            PASS P2-admin打外部文档清单-403
PASS P3-extA打内部渲染接口-403             PASS P4-extB打内部预览-403
PASS P5-extE未核验-列表空                  PASS P6-extF待核验-列表空
PASS P7-extE取1001文档-404                 PASS P8-软删1010不在清单
PASS P9-软删1010预览-404                   PASS P10-不认识的docKind-400
PASS P11-不认识的docKind-列表空            PASS P12-不认识的format-400
PASS P13-不存在的样本id-404                PASS P14-不存在的样本id-列表空
PASS P15-1004草稿预览-404                  PASS P16-1004草稿下载-404
PASS P17-1005草稿(extC)预览-404            PASS P18-1005草稿(extC)列表空
PASS P19-库内1004档确实是草稿              PASS P20-外部版failed-清单少这一行
PASS P21-外部版failed-预览404              PASS P22-外部版failed-下载404
PASS P23-库内同一行确实failed              PASS P24-重新渲染后清单恢复
PASS P25-合并件渲染后进清单                PASS P26-合并件库内是done
PASS P27-撤回一份后该份消失                PASS P28-★撤回一份后合并件立刻消失(不靠异步invalidate)
PASS P29-★过期合并件预览404                PASS P30-★过期合并件下载404
PASS P31-库内合并件仍是done(只被标记过期)  PASS P32-重新完成后合并件回来
PASS P33-publishedEnd=当天含当天           PASS P34-publishedEnd=前一天不含当天
PASS P35-时间格式写错-400                  PASS P36-清单行没有内部键
PASS P37-totalScore只在评分表行            PASS P38-详情docs与清单同源
PASS P39-pages带audience=internal仍只出/external/
PASS P40-download带audience=internal仍只出/external/
PASS P41-文件名用送检单号不用内部编号      PASS P42-样本内部编号开了也不进文档接口
PASS P43-docx真下载且是zip(OOXML)          PASS P44-pdf真下载且含表名
PASS P45-内部版下载键含/internal/          PASS P46-内部版与外部版不是同一个对象
PROBE EXT003 PASS=46 FAIL=0
```

**counterfeit 逐条排掉**（不是「看着像过了」）：

| 票面 counterfeit | 本实现为什么不中 | 证据 |
|---|---|---|
| 清单只按「已完成」过滤、不看外部版渲染成没成 | `available` 里 `render_status='done'` + 产物完整两道 | P20–P24（置 failed → 清单/预览/下载三处同时消失，库内确为 failed，重渲染后恢复） |
| 清单把 1004 的类器官质控表草稿也给了 | 单份文档必须 `doc_status='published'` | P15/P16/P19（库内确为 `draft`） |
| pages / download 只校验样本可见、没校验文档状态 | `pages`/`download` 与清单**同一个** `available` | P15/P16（404）· acc1 第 10 段 |
| audience 从请求参数里读 → 链接里出现 `/internal/` | ext 侧签名里没有该参数，doc 域 7 处写死 `EXTERNAL`，签发前还核对象键 | acc1 第 12 段 · P39/P40 · P45/P46 |
| 异组用户猜 id → 必须 404 | `assertVisible` 先跑，再判文档 | P7 · acc1 第 9 段 |
| `sampleId` 过滤写在可见范围之前 | `visible ∩ {sampleId}`（不可见 → 空**列表**，不是 404） | acc1 第 8 段 · P14 |
| 只看接口返回了一个字符串 | `curl -sSf` 真下载 + `pdftotext` 抽文字 | acc1 第 13 段 · P43/P44 |

### 3.6 上游回归抽跑（`upstream-regression.txt`，全 `rc=0`）

```
AUTH-EXT-001 acc2                    rc=0   （ext 咽喉域；顺带：那段 /mp/int 的 WARN-3 现在 EXIT=0 —— SAMPLE-MP-001 落了之后转绿）
AUTH-EXT-002 acc1 / acc2             rc=0   （同域上游：可见集合 + 白名单 + 包埋卡片）
EMBED-MODEL-001 acc1 / acc2          rc=0   （DDL 对账 + 写侧状态机）
CRYO-MODEL-001 acc1                  rc=0   （冻存 DDL 对 SSOT 逐列）
SAMPLE-MP-001 acc2-api               rc=0
DOC-PUBLISH-001 acc1 / acc2          rc=0（2/2，直连上游）
```

```
$ (cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg test …)
ExtChokepointContractTest   Tests run: 4   ← 需求层 fixture，本票一个字节没改
ExtDocShapeContractTest     Tests run: 6   ← 本票新增
ExtEmbedShapeContractTest   Tests run: 7
Tests run: 257, Failures: 0, Errors: 0, Skipped: 0     ← 本票前 251
BUILD SUCCESS
```

### 3.7 主会话对票面的修正（commit `9911e65`，实现零改动）

7 处字面量，逐条对应 §3.3 / §3.4 的诊断：

```
- --col-set "sample_qc:failed,organoid_qc:failed,organoid_score:done"
+ --col-set "organoid_qc:done,organoid_score:done,sample_qc:done"                     ← 缺陷 1
- …| sort) == ["9000001001:organoid_score","9000001004:sample_qc"] …
+ …| sort) == ["9000001001:organoid_qc","9000001001:organoid_score",
+               "9000001001:sample_qc","9000001004:sample_qc"] …                        ← 缺陷 1
- …&sampleId=9000001001' | jq -e '[.rows[].docKind]==["organoid_score"]'
+ …&sampleId=9000001001' | jq -e '[.rows[].docKind]==["sample_qc","organoid_qc","organoid_score"]'   ← 缺陷 1
- …/9000001001/sample_qc/pages | jq -e '.code==404'
+ …/9000001001/sample_qc/pages | jq -e '.code==200'                                   ← 缺陷 1
- …/mp/ext/sample/9000001001 | jq -e '[.data.docs[].docKind]==["organoid_score"]'
+ …/mp/ext/sample/9000001001 | jq -e '[.data.docs[].docKind]==["sample_qc","organoid_qc","organoid_score"]'  ← 缺陷 1
- test -f …/lqg/ext/ExtDocController.java
+ test -f …/lqg/ext/controller/ExtDocController.java                                   ← 缺陷 2
- ! grep … …/lqg/ext/ExtDocVo.java …/lqg/ext/ExtDocPagesVo.java
+ ! grep … …/lqg/ext/domain/vo/ExtDocVo.java …/lqg/ext/domain/vo/ExtDocPagesVo.java    ← 缺陷 2
```

**实现侧一个字节都没动**（`git show --stat 9911e65` 里 `…/src/main/java/**` 的 7 个新文件与 2 个改动文件都是本报告 §2 那一批）。

★ **票面里还剩一处过时的散文**：acc1 的 `counterfeit` 第一句仍写着「seed 里它俩的图片是假地址，**外部版渲染必然失败**——第 4 段先确认这个病灶真的在」。
它现在**不是断言**（不影响 accept 执行），但会误导后来人，也会让人误以为「取图失败 → 整份 failed」是现行口径。
建议改成探针 P20–P24 那条**可控制**的病灶，或直接把这半句删掉（**本 impl 不改票面**，只记在这里，交主会话）。

---

## §4 四条硬约束各自的验证证据

### 硬约束 1 · `ext` 包只有 `ExtScopeServiceImpl` 允许持 Mapper

**机器证据（不是「测试绿」一句话）** —— `machine-evidence.txt`：

```
$ grep -rnE '^\s*(private|protected|public)\s+(final\s+)?[A-Za-z0-9_]*Mapper\s+[a-zA-Z0-9_]+\s*;' <ext 包>
…/ext/service/ExtScopeServiceImpl.java:54:    private final SampleMapper sampleMapper;
…/ext/service/ExtScopeServiceImpl.java:55:    private final ExtProfileMapper extProfileMapper;
（全包 34 个 .java 文件逐个计数：只有 ExtScopeServiceImpl = 2，其余 33 个 = 0；
 本票新增的 ExtDocController / ExtDocAssemblyService / ExtDocQueryBo / 四个 VO 全部 0）
```

- 取数的读口落在**被调方**：`doc/service/DocExternalQueryService.java`（doc 域），ext 包只做可见范围断言 + 拼装 + 转发。
- 需求层 fixture `ExtChokepointContractTest#i4_onlyExtScopeServiceImplTouchesMappers`（扫整个 ext 包的**字段**）**4/4 绿**，且该文件 `cmp` **逐字节未改**。
- 本票新增的 ext 类里，`*Mapper` 这个词只出现在**注释**里（i4 判的是字段类型，不是文本）—— `machine-evidence.txt` 逐行列出。

### 硬约束 2 · 对外可见必须同时看 `doc_status='published'` **且** 外部版 `render_status='done'`

- 判据**只有一处**：`DocExternalQueryService.available`，清单 / 预览 / 下载共用。
- 「草稿不给」：P15–P19（库内真值 `draft`）。
- 「外部版没渲染成功不给」：P20–P24 —— 直接 `psql` 把 1001/organoid_score 的**外部** header 置 `failed`，
  清单立刻少这一行、预览 404、下载 404，库内同一行确为 `failed`；重新 `POST …/render?audience=external` 后清单恢复。
  这正是票面 counterfeit 第 1 条要钉的那件事，**不依赖**「seed 假图会渲染失败」这个不成立的前提。
- **加强**：还要求「这一版产物完整」（当前指纹下有页图 + PDF 与 header 同版），见 §1 与 WARN-3。

### 硬约束 3 · `audience` 由服务端按身份决定；请求里带 `audience=internal` 不能生效

- **结构上不可能**：`ExtDocController` 的两个 handler 签名里没有 `audience`；`DocExternalQueryService` 的
  7 个调用点写死 `DocAudiences.EXTERNAL`（`machine-evidence.txt` 逐行列出行号）。
- **声明期**：`ExtDocShapeContractTest#audienceIsNeverABoundInputOnTheExternalDocPath` —— 三份源码去掉注释行后，
  代码行里 `audience` 出现 **0** 次（会把「以后有人加个 `@RequestParam String audience`」当场打红）。
- **运行期**：acc1 第 12 段带 `audience=internal` 请求下载 → URL 含 `/external/` 且**不含** `/internal/`；
  P39 预览页图 URL 同理；P45/P46 内部版自己的键含 `/internal/` 且与外部版**不是同一个对象**（证明是两份独立产物，不是下载时抹）。
- 外部版文档里内部编号一格留空（CR-20260918-07 第 4 条）：`DocRenderModelFactory` 按 audience 决定，本票一个字节没动。

### 硬约束 4 · 新增 VO 不许带内部专用字段

- **运行期**：P36 —— `([.rows[]|keys[]]|unique) - [6 键白名单] == []`；P37 —— `totalScore` 只出现在评分行；
  acc1 第 5 段 —— keys 里 `index("internalNo") == null`。
- **声明期**：`ExtChokepointContractTest#i3`（禁 `operatorName/embedBy/frozenBy/verifyBy/publishedBy/createBy/updateBy/phone/...`）**绿**；
  `ExtDocShapeContractTest` 把四个 VO 的字段集合钉成**恰等于**白名单（多一个**或**少一个都红）——
  accept 的 keys 差集是**单向**的，只能抓多不能抓少，这一层补上。
- **文件级**：acc2 的 `! grep -nE 'publishedBy|errorMsg|contentHash|internalNo' ExtDocVo.java ExtDocPagesVo.java` 无命中
  （`ExtDocPagesVo` 刻意**没有**失败原因 / 产物指纹 / 模板版本 —— ticket §3「不能看渲染失败的原因」）。
- `operatorName` / `embedBy` 是 CR-20260918-07 要外部看得到的，但只在 `ExtEmbedVo` 上（`BANNED_EXEMPT` 精确豁免）——
  本票的文档 VO **一个都没带**。

---

## §5 越出 `touches` 的改动（ticket §4.3）

| # | 文件 | 为什么必须动 | 越界程度 |
|---|---|---|---|
| 1 | `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/service/DocExternalQueryService.java` | **新增的对外读口**。**硬约束 1 逼出来的结构**：ext 包只有 `ExtScopeServiceImpl` 允许持 `*Mapper`，连读一行 `t_lqg_doc_file` 都不行；任务书 §0.1 原话「若必须加读口，加在被调方（doc/qc 域）而不是 ext 包」。它把「已发布 ∩ 外部版产物完整」这一条口径的**取数**收在一处，ext 包只拼装 | 轻：**纯新增**文件，不改任何既有 doc 类；不与 `DocRenderService` / `DocPagesService` / `DocArtifactRows` 抢职责（只调用它们的 public 方法） |
| 2 | `doc/waves/reports/AUTH-EXT-003/**` | 本票报告与取证（`touches` 里没有报告目录，按惯例归本票） | 无 |

**没有动**：`doc/api-contract.md`（见 §8）· 任何 `*Mapper` · `ExtScopeService(Impl)` · `ExtInternalNoSwitch` ·
`DocPublishService` / `DocRenderService` / `DocArtifactRows` / `DocArtifactStore` / `DocRenderModelFactory` ·
SAMPLE / EMBED / CRYO 域 · `ruoyi-admin` · 根 pom / 模块 pom · `application*.yml`（**`nonProxyHosts` 那条 JVM 参数没删**）·
小程序 / 前端（本票只动后端，accept 也不涉及端）。

---

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S0**→**已修** | ticket-defect | ★ **accept 1 第 4 段的「病灶」在当前上游不存在**：票面断言 1001 的 `sample_qc` / `organoid_qc` **外部版**渲染 `failed`（counterfeit 原文「seed 里它俩的图片是假地址，外部版渲染必然失败」），实测**三份全 done** | 见 §3.3。根因是 DOC-RENDER-001 的 `DocOssBytes`：「取不到的图跳过 + WARN、文档照出 done」（**issue #217 裁定①**，任务书 §1.1 同）。连带 5 处期望偏差（第 4/5/7/11/14 段）。**主会话已按此修正票面**（commit `9911e65`，见 §3.7），修正后 accept 2/2 绿。方案（当时给的）：把第 4 段换成**真能控制**的病灶（探针 P20–P24 已给出可用的 replace：psql 置 `failed` → 断三处都不给 → 恢复），或接受「三份都给」并改掉那 5 处期望。★ **别把上游改回「整份 failed」** —— 那会让 DOC-RENDER-001 的 accept 2（同一份文档必须 done）永远红 |
| WARN-2 | **S1**→**已修** | ticket-defect | ★ **accept 2 第 3/4 段的三个文件路径少了子包段**（`ext/ExtDocController.java` / `ext/ExtDocVo.java` / `ext/ExtDocPagesVo.java`；真实位置在 `ext/controller/`、`ext/domain/vo/`） | 见 §3.4。`bash -x` 证明 acc2 恰好死在 `test -f`。`ExtDocVo.java` 本票开工前就在 `ext/domain/vo/`，足证是笔误。**主会话已按此修正票面**（commit `9911e65`，见 §3.7）。方案（当时给的）：补全三个路径的 `/controller/` 与 `/domain/vo/`。★ **别把类搬到 `ext` 根** —— 与既有 5 个 ext controller 的布局打架，也白搬上游的 VO |
| WARN-3 | **S1** | doc-drift（上游口径错） | ★ **`DOC-PUBLISH-001` 交接给下游 §6.1 的「外部可见还要求 `header.content_hash == 此刻算出来的指纹`」这条规则实测无效** | `invalidateMerged` 会把 header 的 `content_hash` **改写成此刻该有的指纹**（`DocArtifactRows#markStale` 只改这一列），于是比较当场成立，而桶里的 docx 还是旧成员拼的 → **撤回一份文档之后送检方仍能列到 / 预览到 / 下载到含该文档的旧合并件**（本票初版实测红：P28 `got[2]`、P29/P30 `got[200]`，逐条见 `probes-transcript.txt` 的初版记录与报告修订说明）。本票改成锚在**产物**上的判据（当前指纹下有页图 + PDF 与 header 同版），P28–P30 转绿。方案：把交接那句话改写成「header 的 `content_hash` 必须对应一组**真实存在**的页产物、且 PDF 行与它同版」；`DOC-MP-001/002` 若自己做了清单，**照抄这条**（内部 `pages`/`download` 用的就是它）。★ 建议把 issue #217 那条裁定的**下游影响面**一并登记 |
| WARN-4 | S3 | clarify | **「新完成一份文档」之后、合并件重出之前有一段约 1s 的窗口**，期间送检方拿到的是「少了刚完成那一份」的旧合并件 | 量化证据：`probes/ext003-merged-window.sh`（`probes/merged-window-transcript.txt`）—— `publish` 返回时 merged 仍在清单里且指纹未变，**约 1s 后**异步渲染把 header 置 pending / 换指纹，随后收敛。**不泄密**（撤回场景已被 WARN-3 的判据堵死），只是短暂不完整。方案（一行，属 DOC 域、本票**没动**）：`DocPublishService.publish` 在 `scheduleRender` **之前**同步调一次 `renderService.invalidateMerged(sampleId)` —— 窗口内的 header 指纹立刻变成新的期望值，页图对不上 → 我的判据自动把它挡在门外 |
| WARN-5 | S3 | perf/口径 | **`/mp/ext/doc/list` 的分页在内存里切**，取数是「可见样本 × ≤4 种文档」逐样本查 | 一行文档要跨「三张质控表 + 产物表」才凑得出来，没有一条 SQL 能既分页又给出 `doc_status` / 产物完整性。单个外部账号的可见样本 = 本人的 ∪ 同组已核验者的，量级可控；本人+同组样本数上千时会退化成 N 次 doc 查询。`DOC-MP-001` 若真遇到性能问题，建议在 doc 域读口里加一条**按 `t_lqg_doc_file` 驱动 + 分页**的查询（本票没做，属新口径） |
| WARN-6 | S3 | clarify | **外部预览只给页面图，不给「图片位 / 附件」**（内部 `/lqg/doc/**` 的 `pages` 有 `images` / `attachments`） | 票面 §2 只说「pages」，没说形状；契约第 87 行附近也只写「只含已完成且外部版渲染成功的」。本票取**最小形状** `{docKind, status, pages}`（少一个出口 = 少一条要证明堵住的路；附件文件名本身也可能带内部信息）。`DOC-MP-001` 若要在外部预览页显示图片位/附件，**需要先改契约与本形状**（别让小程序直连内部 `/lqg/doc/**`） |
| WARN-7 | S3 | clarify | **合并件的 `publishedTime` 是「成员里最新那一份的完成时间」**（它自己没有完成时间列） | 契约没规定。若甲方的「完成时间」要的是**合并件生成时间**，改读该行 `rendered_time` 即可（属新口径，本票没做） |
| WARN-8 | S3 | harness（既有，本票不重复计数） | **`sys_oss` 行随每次重出**单调增长（同一个对象键反复 `INSERT`），`reseed.sh` 只清 `oss_id ∈ [9000000000, 9000009999]` 段 | 后果：**别用 `count(*)` 断言 `sys_oss`** —— 本票探针 P45 初版写 `count == 1`，实测 **52**（前几十次渲染累积的同键行），假红一次（见 `probes-transcript.txt` 初版）。已改成「下载 URL 含 `/internal/` + 与外部的对象不同」。与 DOC-PDF-001 WARN-8 同源 |
| WARN-9 | S3 | clarify | **模板版本升级（`template-version.txt` +1）后，历史产物的 `content_hash` 与「此刻算出来的指纹」全部对不上** —— 本票**刻意不**拿这条当门槛 | 若拿它当门槛，模板一升级全部历史文档就对送检方隐藏（而内部页面照旧显示，两边不一致）。所以单份文档 = `doc_status` + 产物完整；合并件 = 产物完整（内部一致性，与旧模板无关）。这一条与 WARN-3 是同一个设计取舍的两面，**请与 DOC/DOC-MP 域对齐后统一写进契约** |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 在沙箱恒 exit 2 + Maven 三参数（连续 9+ 张命中）、`api.sh` token 缓存只看 mtime、`db.py` 是只读执行器却 exit 0、`updateById` 忽略 null、MyBatis-Plus 无「按表达式排序」重载、`PageQuery` 只有两参构造、微信开发者工具在本沙箱跑不通。

---

## §7 给下游的坑（DOC-MP-001 / DOC-MP-002 / SYS-EXPORT-001）

1. ★★ **`doc_status='published'` 不等于「对外可见」**（DOC-PUBLISH-001 WARN-5 的同一句话），
   而且**上游交接的那条补充规则（§6.1 的 content_hash 比较）是无效的**（WARN-3，有实测证据）。
   正确的判据是**产物锚定**那三条：`audience='external'` 的 header `done` + **当前指纹下有页图** + **PDF 与 header 同版**。
   `DOC-MP-001/002` 若自己拼清单，**照这条抄**；要复用就直接调 `/mp/ext/doc/list`。
2. ★ **`audience` 千万别做成前端参数**：外部接口**没有**这个参数，塞了也不生效。预览/下载/清单三条路都已写死 external；
   小程序不要为了「统一代码」把内部 `renderDoc/getDocPages` 那套（带 `audience` 入参）复制到外部页 —— 那是外部隔离唯一的口子。
3. ★ **页面图 / 下载链接都是 10 分钟签名链接**：别缓存、别存库、别自己拼字符串；进预览页就重新调一次 `pages`。
   实测链接形如 `http://127.0.0.1:9000/ruoyi/lqg/doc/<sampleId>/<docKind>/external/<hash12>-p1.png?X-Amz-…&X-Amz-Expires=600`。
4. ★ **形状（照这份实现抄，别自己加键）**：
   - 清单行 `ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}`
     —— `totalScore` **只在评分表行**出现（`NON_NULL`），`donorNameMasked` 是「首字 + **」；
   - 预览 `{docKind, status('done'), pages:[{pageNo, url}]}` —— **没有** `errorMsg` / `contentHash` / `templateVersion` / `images` / `attachments`；
   - 下载 `{url, fileName}` —— `fileName` 是「文档名-送检单号.docx」（**外部版只出送检单号，不出内部编号**）。
5. ★ **`docKind` 是下划线那四个**（`sample_qc` / `organoid_qc` / `organoid_score` / `merged`），
   **不是**质控草稿侧的连字符三个（`sample-qc` / `organoid-qc` / `score`）。外部链路只有下划线那一套；
   传别的值：预览/下载 **400**，清单**空列表**（不是 400）。
6. ★ **清单的 `sampleId` 筛选不可见 → 空列表（不是 404）**，而**预览/下载不可见 → 404**。
   这是刻意的：列表的「0 行」不泄露任何东西，而单条请求的 404 与「没这份文档」不可区分（FLOW:F-EXT-01.step2）。
   小程序预览页顶部那条切换条（CR-20260917-04）用 `sampleId` 拉清单，**不要**改成逐个调预览接口判存在。
7. ★ **清单顺序保证「同一样本的几份相邻」**（组的先后 = 组内最新一份的完成时间倒序，组内 = 样本质控表 → 类器官质控表 → 评分表 → 合并件）。
   小程序若自己分组，**别重排**，否则翻页会把一组拆到两页。
8. ★ **`merged` 可能不在清单里**：它要「已经渲染成功且产物完整」。外部**不能**触发渲染（`/lqg/doc/**` 对外是 403），
   所以合并件的出现完全取决于工作台是否点过「完成并同步」/ 预览过。**没有 `merged` 时不要在页面上显示「合并预览/合并下载」按钮**（模板里的空按钮点了只会 404）。
   `FLOW:F-DOC-02.step1` 那句「≥2 份时」出合并件，请按**清单里实际有没有 `merged` 行**判断，别按「published 的份数 ≥2」判断（份数够了但还没渲染的情况真实存在）。
9. ★ **SYS-EXPORT-001**：本次**没给导出加任何东西**。若导出要带「文档完成情况」，读 `doc_status` / `published_time` / `published_by` 即可（`published_by` 是 `sys_user.user_id`，不是人名，要显示人名得 join）。
   另：**导出的「已完成文档」清单也要排除 `del_flag='1'` 的样本**（ticket 1010 那条既有病灶；本票实测 `extA GET /mp/ext/doc/9000001010/sample_qc/pages` → 404、清单里也没有它）。
10. ★ **D7 QA 门复跑建议**：`reseed.sh --yes` → `accept-run.py --ticket AUTH-EXT-003 --run`（WARN-1/WARN-2 已由主会话修正，commit `9911e65`）→ `bash doc/waves/reports/AUTH-EXT-003/probes/ext003-probes.sh`（46 条，需 8094 起来）。
    只用 accept 的话，「外部版置 failed 之后三处同时消失」「合并件过期立即不可见」这两条**没有任何门**（它们正是票面 counterfeit 想钉的东西）。

---

## §8 与 `doc/api-contract.md` 的差异（逐行核过）

| 契约行 | 契约写的 | 实现 | 判断 |
|---|---|---|---|
| `GET /mp/ext/doc/list`（QC / DOC 一节） | 过 ExtScope，`audience` 固定 external，只含已完成且外部版渲染成功的（三种 docKind 都给 + `merged`）；可带 `sampleId` | 逐字实现；额外支持 `docKind` / `publishedBegin` / `publishedEnd`（票面 §2 点名） | ✅ |
| `ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}` | 逐字 | 逐字（`totalScore` 加 `NON_NULL`，只在评分行出键） | ✅ |
| `GET /mp/ext/doc/{sampleId}/{docKind}/pages`、`…/download` | 契约只写「过 ExtScope，audience 固定 external，只含已完成且外部版渲染成功的」 | 预览 `{docKind, status, pages}`、下载 `{url, fileName}` | ⚠️ **契约没写这两个端点的 `data` 形状** → 本票按最小形状落，见 WARN-6（**建议补进契约第 87 行附近**） |
| 第 51 行 `ExtSampleDetailVo` 的 `docs:[ExtDocVo]` | 逐字 | 逐字接上（与清单**同一份查询**） | ✅ |
| 第 51 行的字段清单 | 少一个 `sampleKind` | 盘上 AUTH-EXT-001 起就有 `sampleKind` | ⚠️ 上游 AUTH-EXT-002 WARN-4 已记的 doc-drift，**本票不重复计数** |
| 契约第 90 行「`docType` 连字符 / `docKind` 下划线两套」 | 逐字 | 外部链路只用 `docKind`（下划线）那一套；不认识的值 400 / 清单空 | ✅ |
| `doc/api-contract.md` 文件本身 | —— | **一个字节没动**（只读区；上面的「建议补形状」交主会话） | ✅ |

---

## §9 长进程 / 端口 / 收工自检

```
$ git branch --show-current
task/D7
$ git log --oneline -1
9911e65 AUTH-EXT-003: 外部文档清单/页面图/下载全过咽喉（只给已完成且外部版渲染成功的）
$ # 本 agent 的改动全部在上面这个提交里（由主会话在实现完成后落的库，本 agent 没有 commit / push / merge）
$ git show --stat --oneline 9911e65 | head -12
 .../lqg/doc/service/DocExternalQueryService.java   | 356 ++++++++++++++++
 .../lqg/ext/controller/ExtDocController.java       |  99 +++++
 .../dromara/lqg/ext/domain/bo/ExtDocQueryBo.java   |  63 +++
 .../lqg/ext/domain/vo/ExtDocDownloadVo.java        |  37 ++
 .../lqg/ext/domain/vo/ExtDocPageItemVo.java        |  42 ++
 .../dromara/lqg/ext/domain/vo/ExtDocPagesVo.java   |  56 +++
 .../org/dromara/lqg/ext/domain/vo/ExtDocVo.java    |   4 +-
 .../lqg/ext/service/ExtDocAssemblyService.java     | 325 +++++++++++++++
 .../lqg/ext/service/ExtSampleAssemblyService.java  |   6 +-
 .../dromara/lqg/ext/ExtDocShapeContractTest.java   | 213 ++++++++++
 doc/tickets/AUTH-EXT-003/prompt.md                 |  14 +-      ← 主会话修正票面 7 处字面（§3.7）
 doc/waves/reports/AUTH-EXT-003.md                  | 458 +++++++++++++++++++++
 doc/waves/reports/AUTH-EXT-003/**                  | 取证目录
$ git status --porcelain        # 收尾时（剩下的都不是本 agent 改的）
 M code/miniapp/src/pages.json                    ← 另一个会话的 dev server 产物
 M code/plus-ui/.eslintrc-auto-import.json        ← 同上
```

- **`doc/waves/state.json` / `_manifest.json`**：**本 agent 一个字节都没写**（开工时 `git status --porcelain` 是空的，全程没出现过它们）。
  收尾复核时 `state.json` 出现在 `9911e65` 这个**主会话**提交里（连同票面修正）—— 那是主会话的台账动作，不是本票的改动。
- **只读区**（`_input/` / `doc/requirements.yaml` / `doc/authority/**` / `doc/change-log.md` / `doc/api-contract.md` /
  `doc/verify/{seed/**,gen_seed.py,api.sh,fixtures/**}`）：`git status --porcelain` **全空**（开工与收尾两次都是）。
- **★ lsof 的一个坑（值得记）**：`lsof | grep ruoyi-admin.jar` 会同时命中**别的项目/别的会话**的 java：
  本次实测命中 `tianda-studio` 的 JVM（PID 26141）与**本仓另一个会话的后端**（`.tmp/qa-env/8091`，PID 49422，
  `cwd` = 本仓、监听 **8091**、state 里 `started=2026-09-22T08:31:50Z`、`head=9f1b008` —— **不是本 agent 起的，本票只用 8094，没有动它**；
  收尾时它仍在跑，留给它的主人）。另外 `lsof -ti tcp:<port> -sTCP:LISTEN` 有一次**短暂返回空**（同一秒 `lsof -iTCP -sTCP:LISTEN` 却能看到），
  靠它单条判「端口已释放」会误判 —— 所以关停后**两条路都核**（`-ti tcp:` + `lsof -iTCP -sTCP:LISTEN`）。
  结论：**关本项目的后端必须按「监听该端口 且 cwd 是本仓」双重条件拿 PID**，不要只按 jar 名 grep。
### 9.1 ★ 交回时的**共享工作树状态**（重要，别把别人的红算到本票头上）

本票交回时，**工作树里还有另一个会话（DOC-MP-001）正在写的文件**：

```
$ git status --porcelain
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/service/DocExternalQueryService.java
 M code/miniapp/src/pages.json                    ← 别人的 dev server 产物
 M code/plus-ui/.eslintrc-auto-import.json        ← 别人的 dev server 产物
 M doc/waves/reports/AUTH-EXT-003.md              ← 本 agent（报告增补，见下）
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/service/DocAvailabilityService.java  ← DOC-MP-001
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/mp/                                     ← DOC-MP-001
?? doc/waves/reports/AUTH-EXT-003/accept-result-FINAL-fixed-ticket.json
?? doc/waves/reports/AUTH-EXT-003/accept-runners/ext003-acc{1,2}-verbatim-FINAL.sh(.log)
```

1. **`DocAvailabilityService.java`（新）+ `DocExternalQueryService.java`（改）是 DOC-MP-001 的重构**：
   把我在这里写对的「可用性」四条件（含 `artifactComplete` 那条**实测有效**的产物锚定判据）
   **原样抽成按 audience 参数化的共享类**，`DocExternalQueryService.available` 现在只是
   `availability.available(sampleId, kind, EXTERNAL)` —— **外部链路的语义一个字节没变**
   （上一节 §1/§4 的结论与探针 P20–P30 依然成立）。抽出的理由正是本报告 §7 第 1 条：内部清单与外部清单
   必须给出同一个答案。**这是本票希望看到的结果**（WARN-3 的处置被下游采纳）。
2. **但那个新目录 `doc/mp/**` 目前编译不过**（`MpDocController` 引用的 `MpDocService` 还没落地）。
   所以**现在**在共享工作树上跑 `mvn -pl ruoyi-modules/ruoyi-lqg test` 会红在
   `doc/mp/MpDocController.java: cannot find symbol: class MpDocService` —— **不是本票的红，也不是上面那次重构的红**。
3. 因此本报告的 **accept 2/2 / 探针 46/46 / 回归 7/7，全部是在 `9911e65`（本票提交，也是本 agent 亲手构建并起后端的那个 revision）** 上取的；
   本 agent **没有**在 DOC-MP-001 的半成品上重跑 accept（那会拿别人的未完成代码当自己票的结论，且当时 8094 的验收链已经跑完并关停）。
   **D7 的 QA 门在 DOC-MP-001 落地后再复跑一次 AUTH-EXT-003 的 accept 即可**（两条都只依赖 ext 侧与 doc 域的可用性判据，预期仍 2/2）。

- **8080（Kevin）/ 5432 / 6379**：全程**没碰**（开工与收尾两次复核都是空）。
- **没起任何前端 / 小程序 dev server**（本票只动后端，accept 不涉及端；8093 上那个 dev server 与 `code/miniapp/src/pages.json`、`code/plus-ui/.eslintrc-auto-import.json` 的两处改动都是**另一个会话**的 dev server 产物，不是本票改的）。
- **5433 `lqg-dev-postgres` / 6380 `lqg-dev-redis` / 9002-9003 `lqg-dev-minio` / 3010 `lqg-dev-gotenberg`**：全程在跑，**没停**（留给后续 ticket）。
- **DB 收尾**：`bash doc/verify/reseed.sh --yes` 回确定性快照（accept / 探针 / 窗口探针都改过库）。

**取证目录 `doc/waves/reports/AUTH-EXT-003/`**

| 文件 | 内容 |
|---|---|
| `accept-result-FINAL-fixed-ticket.json` | ★ `accept-run.py` 对**修正后票面**的机器结果（**2/2，failed 为空**） |
| `accept-runners/ext003-acc{1,2}-verbatim-FINAL.sh` + `.log` | ★ 修正后票面的**逐字**重放脚本与日志（acc1 的 8 个 `true` 与三段状态就在里面） |
| `accept-result.json` | 同一 runner 对**修正前**原始票面的机器结果（0/2，两条红的原因），保留作修正依据 |
| `accept-runners/ext003-acc1-verbatim.sh` + `.log` | 票面 acc1 的**逐字**重放脚本（仅 NF1）与其失败日志 |
| `accept-runners/ext003-acc2-verbatim.sh` + `.log` + `-xtrace.txt` | 票面 acc2 逐字重放（仅 NF2）+ `bash -x` 定死的失败行 |
| `accept-runners/ext003-acc1-premise-fixed.sh` + `.transcript.txt` | **最小改动重放 1**（只改「渲染会失败」这一个前提）→ `EXIT=0` |
| `accept-runners/ext003-acc2-paths-fixed.sh` + `.transcript.txt` | **最小改动重放 2**（只补全三个路径）→ `EXIT=0` |
| `probes/ext003-probes.sh` + `../probes-transcript.txt` | 对抗性探针 46 条（完整输出） |
| `probes/ext003-merged-window.sh` + `probes/merged-window-transcript.txt` | 合并件窗口的量化证据（WARN-4） |
| `machine-evidence.txt` | 四条硬约束的机器证据（含 ext 包逐文件 Mapper 字段计数、audience 常量行号） |
| `api-json-samples.txt` | 清单 / 预览 / 下载 / 详情 / 403 的原样 JSON |
| `upstream-regression.txt` | 上游 7 条 accept 的回归输出（全 rc=0） |

★ **没有把任何 PNG / 截图读进上下文**（本票没产出截图，`--no-web --no-mp`）。
