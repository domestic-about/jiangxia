# DOC-MP-001 · 完工报告

- **ticket**：DOC-MP-001（track DOC / phase D7 / size M）—— 小程序「我的文档」清单页：分组卡片、单份下载、合并预览 / 合并下载
- **status**：**done**
- **accept**：**2/2 绿**（票面逐字重放，唯一归一化 = NF1 去掉 `--fresh-module ruoyi-lqg`，见 §3.0）
- **分支**：`task/D7`（开工 `git branch --show-current` 确认过；**未切分支 / 未 push / 未 merge**；未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 21:05:26`（epoch 1790082326），后端 **PID 64236**（8094，dev profile + `--api-decrypt.enabled=false`）；`find ruoyi-lqg/src -newer <jar>` **为空**
- **迁移**：**一支都没有**（无 DDL / 无菜单 / 无授权 → 不取号；库里已应用的最大版本仍是 `202609261430`，见 §2.1）
- **产物**：后端 **5 个新类 + 1 个既有类的小改**；小程序 **5 个新文件 + 6 处改造**；取证目录 `doc/waves/reports/DOC-MP-001/`
- **★ 口径**：小程序走**本地 Mock**（Kevin 2026-09-22 决策，appid 暂时无法提供）→ **没有做体验版 / 真机档**，验收面 = **H5（`pnpm dev:h5`，`VITE_MOCK_LOGIN=1`）+ Playwright 真 DOM + 真后端 + 真库**。**真机 / 微信里未覆盖**（§9.4）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D7`（全程没切分支） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | **AUTH-EXT-003**：`/mp/ext/doc/list` 在盘且被本票真依赖（复跑其 accept **2/2** + 探针 **46/46**，见 §3.4）；**SYS-MP-001**：`EntryGrid` / 三个页签 / `pages.config.ts` / `src/style/{tokens,components}.scss` 都在盘 |
| 扫 `doc/change-log.md` | ✅ PASS | 顶部三条核过：**CR-20260921-08**（视觉方向 A；本票 §0 与 accept 不动视觉断言，只保证零色值字面量）· **CR-20260918-07**（外部看得到操作人与包埋人：**不作用于预渲染文档** → 本票不给文档 VO 加内部可见字段）· **CR-20260917-04**（列表上直接能下：本票把按钮放上，弹层归 DOC-MP-002）。另 CR-20260917-03（方案 A 按样本分组）= 本票的分组口径 |
| `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:mp.doc.list`（方案 A：组内固定顺序、组间最新完成时间倒序、组底两个按钮、空态「结果出具后会显示在这里」）· `FLOW:F-DOC-02.step1`（produces：「外部只见可见样本集合内的、外部版已渲染成功的文档」；「每组**最多三份**」）。两个锚都 `active` |
| 视觉按方向 A（`落地规范.md` §5.1/§5.5/§5.11） | ✅ PASS | 新文件只用 `.lqg-*` 范式类与 token；**零色值字面量**（证据 `machine-evidence.txt` §6）；用到的 `--lqg-*` 变量逐个在 `tokens.scss` 里存在 |
| 「本栈已知的坑」表逐条过 | ✅ PASS | 判成败一律 `--bizcode` / `jq -e` 断业务码，并断正码 + 副作用；`internalNo` 禁字按 accept 逐字跑；大 id 比较一律 `tostring`（accept 那行就是） |
| 环境可用（8094 / PG 5433 / Redis 6380 / MinIO 9002-9003 / Gotenberg 3010） | ✅ PASS | 四个容器全程在跑（`lqg-dev-gotenberg` 未停）；进程起停**只按 PID** |
| ★「外部可见」的判据锚在产物上（任务书 §0.1 ②） | ✅ PASS | **没有**自己再写一遍可见性判断：内部清单与外部清单**共用** `DocAvailabilityService`（当前指纹下有页图 + PDF 与 header 同版 + 单份还要 `published`）。撤回一份后**内外两个清单同时**少掉那一份与合并件（`machine-evidence.txt` §5） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

### 0.1 与任务书收尾要求的一处**有意偏离**（先说，免得被当成遗漏）

任务书要求收尾 `git checkout -- code/miniapp/src/pages.json`。**本票没有还原它**，因为还原会让 accept 2 当场红：

```
保留本次 build 重新生成的 pages.json → app.json tabBar = [index, doc, me] → jq → true   ✅
git checkout -- src/pages.json 后再 build → app.json tabBar = [index, me, doc] → jq → false ❌
```

根因：`@uni-helper/vite-plugin-uni-pages@0.3.19` 的 `mergePlatformItems` 是 **old 优先**（读盘上已有的 `pages.json` 建序，新页面才 append），所以 tabBar 顺序由**上次生成结果**决定，不由 `pages.config.ts` 决定；`069801e` 里那份 pages.json 是我改 `pages.config.ts` 时的**中间态**（doc 被 append 到 me 之后）。完整机理与两个方向的实跑见 `doc/waves/reports/DOC-MP-001/pages-json-tabbar-order.txt`。
**给 QA**：若把 `src/pages.json` 还原成 HEAD，请先 `rm code/miniapp/src/pages.json` 再构建（缺文件时 old 为空 → 顺序完全由配置决定，实测 `[index, doc, me]`），否则 accept 2 必红。

---

## §1 口径复述（ticket §0 点名「最容易做反的」4 点，逐条对实现核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **组标题 = 接口给、前端不拼**：内部 = 内部编号 + 来源单位；外部 = 送检单号 + 供体姓名（掩码） | 后端 `MpDocService.toVo` 给 `title/subtitle`（内部）；外部仍由 AUTH-EXT-003 给 `submitNo/donorNameMasked`；前端**唯一一处**归一化 `group.ts#groupTitle/groupSubtitle` | accept 2 的禁字 `! grep -n 'internalNo' src/pages/doc/index.vue src/components/lqg/DocGroupCard.vue` 无命中 · 截图断言：内部组标题 `["T-hco04","T-hli01"]` / 外部 `["SJ90000001","SJ90000004"]`，外部页 `bodyHasInternalNo=false` |
| 2 | **组内固定顺序**（样本质控表 → 类器官质控表 → 评分表）+ **组间按组内最新完成时间倒序** | `src/pages/doc/group.ts#groupDocs`（纯函数，fixture 单测） | accept 1（7/7）；三条 counterfeit 逐条打红（§4） |
| 3 | **「合并预览 / 合并下载」≥2 份才出**；每份一行右侧「下载」；本张只上形态（点了提示「即将开放」） | `DocGroupCard.vue`：`.gcd__dl` 每行一个 + `v-if="group.showMerge"` 两个按钮 | `/tmp/lqg-docgroup.json` → 7 passed / 0 failed；`jq` 断 `numFailedTests==0 and numPassedTests>=4` → `true`；截图断言 `mergePreview:1 mergeDownload:1`（3 份那一组）与 `0/0`（1 份那一组） |
| 4 | **合作单位看到的同样是三份，前端不按身份挑类型**；身份只决定打哪个清单接口 | `pages/doc/index.vue` 只按 `normalizeIdentity` 选 `fetchIntDocList` / `fetchExtDocList`，不做类型取舍 | 截图断言：外部组内 `["样本质控表","类器官质控表","类器官质量评分表"]` |

**★ 交付终局（实测两个接口的原样输出）**

```
$ --as staff GET /mp/int/doc/list?pageSize=100
[{"sampleId":9000001006,"title":"T-hco04","subtitle":"B 大学","internalNo":"T-hco04","sourceUnitName":"B 大学",
  "docKind":"organoid_score","publishedTime":"2026-09-18 10:00:00","totalScore":18},
 {"sampleId":9000001001,"title":"T-hli01","subtitle":"A 医院","docKind":"sample_qc","publishedTime":"2026-08-31 10:00:00"},
 {"sampleId":9000001001,...,"docKind":"organoid_qc","publishedTime":"2026-09-10 10:00:00"},
 {"sampleId":9000001001,...,"docKind":"organoid_score","publishedTime":"2026-09-13 10:00:00","totalScore":85}]
$ --as extA GET /mp/ext/doc/list?pageSize=100
[{"sampleId":9000001001,"submitNo":"SJ90000001","donorNameMasked":"测**","docKind":"sample_qc",...}, ... ]
```

**★ 「能不能给出去」只有一处判据**（撤一份 → 内外同时少）：见 `machine-evidence.txt` §5。

---

## §2 改了哪些文件（ticket §4.1）

### 2.1 Flyway 迁移：**一支都没有**（以及为什么）

- 内部清单**不建表 / 不加菜单 / 不加授权行**：`t_lqg_doc_file` + 三张质控表都是上游票建好的，本票**只读**；
  `/mp/int/doc/**` 的角色闸是既有的 `lqg_internal`（SAMPLE-MP-001 用的同一个）。
- 库里已应用的最大版本 = **`202609261430`**（QC-WEB-001，实测 `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1`）。
  **取号依据**：本票没有需要迁移的东西，所以**没有取号**、没有 `V202609261440__DOC-MP-001-*.sql`。
  给 D7 之后要加迁移的票：版本号**必须 > `202609261430`**（`spring.flyway.out-of-order=false`）。

### 2.2 后端（`touches` 内：`doc/mp/**`）

**新增**

| 文件 | 职责 |
|---|---|
| `doc/mp/MpDocController.java` | `GET /mp/int/doc/list`，**类级 `@SaCheckRole("lqg_internal")`**；只转发不查库 |
| `doc/mp/MpDocService.java` | 取候选行（产物表驱动）→ 过共享可见性闸 → 过滤 / 排序（同组相邻、组间最新倒序）→ 内存切页 → 装 `MpDocVo`；`title/subtitle` 在这里按内部身份给 |
| `doc/mp/MpDocQueryBo.java` | 四个筛选（`sampleId` / `docKind` / `publishedBegin` / `publishedEnd`）+ 显式无参构造（`PageQuery` 5.5.3 没有无参构造） |
| `doc/mp/MpDocVo.java` | 行形状 `{sampleId, title, subtitle, internalNo, sourceUnitName, docKind, publishedTime, totalScore?}`；`totalScore` 加 `@JsonInclude(NON_NULL)`（非评分行连键都不出） |

**接口清单（本票新增）**

```
GET /mp/int/doc/list?sampleId=&docKind=&publishedBegin=&publishedEnd=&pageNum=&pageSize=
      全部样本里「doc_status=published 且内部版产物完整」的文档（+ 已渲染成功的 merged）；
      行 = {sampleId, title(内部编号), subtitle(来源单位), internalNo, sourceUnitName, docKind, publishedTime, totalScore?}
      docKind ∈ sample_qc | organoid_qc | organoid_score | merged（下划线那套，与外部一致）
      不认识的值 → 空列表（不是 400）；时间格式写错 → 400
★ 没有 audience 参数 —— 内部清单只会是内部版；外部那一份走 AUTH-EXT-003 的 /mp/ext/doc/list
```

### 2.3 前端（`touches` 内）

| 文件 | 说明 |
|---|---|
| `src/api/doc.ts` | 两个清单接口的类型与取数（`raw:true`，分页响应没有 `data` 键）；`fetchSampleDocs(sampleId, identity)` |
| `src/pages/doc/group.ts` | `groupDocs(rows)` 纯函数（组内固定顺序 / 组间最新倒序 / `showMerge` / **merged 不算一份**）+ `DOC_KIND_LABEL` + 组标题归一化（`title ?? submitNo`） |
| `src/pages/doc/group.fixture.spec.ts` | fixture 驱动用例（期望值**全部**从 `doc/verify/fixtures/doc-group-cases.json` 读，spec 里零期望值） |
| `src/components/lqg/DocGroupCard.vue` | 一张分组卡：组标题/副标题 → 每份一行（文档名 + 完成时间 + 右侧「下载」）→ 组底「合并预览」「合并下载」（≥2 份） |
| `src/pages/doc/index.vue` | **tab 页**：筛选（全部/近一周/近一月/自定义 + 文档类型）→ `groupDocs` → `DocGroupCard`；空状态「结果出具后会显示在这里」 |
| `src/pages/sample/detail-ext.vue` | 第③段「质控文档」接上：`GET /mp/ext/doc/list?sampleId=`（**不碰**内部 `/lqg/doc/**`），点条目进预览占位页 |

### 2.4 越出 `touches` 的改动（ticket §4.3，逐条）

| # | 文件 | 为什么必须动 | 越界程度 |
|---|---|---|---|
| 1 | `doc/service/DocAvailabilityService.java`（**新增**） | 内部清单需要「这一份现在能不能给出去」，而 `DocExternalQueryService` 把 `audience` **写死**成 external（它自己的口径 2）。**任务书 §0.1 ② 明令不许自己再实现一遍可见性判断**，所以把四个条件（header `done`+产物 / `artifactComplete` / 单份 `published`）抽成**按 audience 参数化**的共享类，两边共用 | 轻：纯新增；**零 Mapper 字段**（只用 `DocArtifactRows` + `DocRenderModelFactory`） |
| 2 | `doc/service/DocExternalQueryService.java`（**修改**，行为逐字不变） | 上一条的另一半：把 `available` / `requireAvailable` / `artifactComplete` / 三个取数私有方法删掉，改成**委托** `DocAvailabilityService`（`audience` 写死 `DocAudiences.EXTERNAL`）；`requireFormat` 也挪到共享类。**公开 API 一字未改**（`rowsOfSamples` / `rowsOfSample` / `available` / `pages` / `download`） | 中：动了上游文件，但**只删重复实现、不改编排**。回归见 §3.4：AUTH-EXT-003 accept 2/2 + 探针 46/46 + 模块 257 测全绿 |
| 3 | `src/config/app.ts`（新增） | accept 2 逐字要求 `grep -q 'DOC_TAB_NAME' src/config/app.ts`；REQ-DOC-010 是 clarify（页签名与板块名放配置里，改名不发版） | 无（新文件） |
| 4 | `pages.config.ts` | accept 2 逐字要求 `tabBar.list[1].pagePath == "pages/doc/index"`（SYS-MP-001 原来指 `pages/docs/index`）；页签文字改成引 `DOC_TAB_NAME` | 轻（2 行） |
| 5 | `src/router/config.ts` | `TAB_PAGES` 里的 tab 路径跟着改成 `/pages/doc/index`（否则 `goPage` 会对 tab 页用 `navigateTo` 而失败） | 轻（1 行） |
| 6 | `src/pages/doc/preview.vue`（新增） | ticket §2：「点条目进预览页（DOC-MP-002；本张先跳占位页）」——占位页得有地方跳 | 无（新文件） |
| 7 | `src/pages.json`（**生成产物**，保留本次 build 的干净版本） | 见 §0.1：还原它 accept 2 必红（实测） | 轻但**必须留**；WARN-2 |
| 8 | `src/types/uni-pages.d.ts`（生成产物） | 同上，dev server / build 依 pages.json 重写（多了 `/pages/doc/index`、`/pages/doc/preview`） | 无（生成物） |
| 9 | `code/miniapp/scripts/shots-doc-mp001.mjs`（新增） | 本票的取证截图脚本（跑法见 §9.3）；与 SYS-MP-001 的 `scripts/shots-h5.mjs` 同款约定 | 无（脚本；`touches` 里没有 scripts 目录，按惯例归本票） |
| 10 | `doc/waves/reports/DOC-MP-001/**` | 本票报告与取证 | 无 |

**没有动**：`doc/api-contract.md`（见 §8）· 任何 `*Mapper` · `Ext*` 包 / `ExtChokepointContractTest` / `ExtDocShapeContractTest`（**一个字节没改**）· `DocRenderService` / `DocPagesService` / `DocArtifactRows` / `DocArtifactStore` / `DocPublishService` / `DocRenderModelFactory` · SAMPLE / EMBED / CRYO / QC 域 · `ruoyi-admin` · 根 pom / 模块 pom / `application*.yml`（**`nonProxyHosts` 那条 JVM 参数没删**）· `doc/verify/**` 只读区（`seed/` / `gen_seed.py` / `api.sh` / `fixtures/` / `reseed.sh` / `db.py`）· `doc/waves/state.json` / `_manifest.json` · `_input/` / `doc/change-log.md` / `doc/requirements.yaml`。

> 说明：`069801e`（AUTH-EXT-003 终报入账那次提交）把我**当时在盘上的半成品**一并提交了（后端 5 个类 + `api/doc.ts` / `group.ts` / `group.fixture.spec.ts` / `config/app.ts` / `pages.json` 等）。那不是我提交的，我也没有再提交；本票的 `git status --porcelain` 见 §9.1。

---

## §3 accept 逐条 ✅ / ❌ + 关键输出（ticket §4.2）

### 3.0 关于 NF1（本 agent 沙箱限制，既有 WARN 不重复计数）

```
$ python3 doc/waves/tools/accept-run.py --ticket DOC-MP-001 --run --json .tmp/doc-mp001-accept.json --logdir .tmp/doc-mp001-accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ DOC-MP-001 acc1 [STATE] … (1.0s)
  ✓ DOC-MP-001 acc2 [API]   … (12.5s)
[run] 通过 2/2
```
`.tmp/doc-mp001-accept.json` 里 acc2 的 `nf: ["NF1"]`：`--fresh-module ruoyi-lqg` 被去掉（`doc/verify/api.sh` 用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` → 带它恒 exit 2，**连续 9+ 张命中的既有 WARN**）。**没有改 `api.sh`**，用等价证据覆盖那道守卫（`machine-evidence.txt` §1）：

```
jar mtime        : 2026-09-22 21:05:26（epoch 1790082326）
find lqg/src -newer <jar> : []         ← 源码不比 jar 新
8094 listener    : PID 64236（.tmp/qa-env/8094/state.json 的 backend_pid）
8080（Kevin）/5432 : 全程为空，未碰
嵌套 jar 复核     : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含本票全部新类
                   （MpDocController / MpDocService / MpDocQueryBo / MpDocVo / DocAvailabilityService）
Flyway 启动       : 本票没加迁移（库里 count 仍是 23，最大 202609261430）
```

### 3.1 accept 1 · STATE（分组规则过 fixture）—— ✅

```
$ grep -q 'doc/verify/fixtures/doc-group-cases.json' src/pages/doc/group.fixture.spec.ts   → exit 0
$ ! grep -nE '\.(skip|todo|only)\(' src/pages/doc/group.fixture.spec.ts                    → exit 0（无命中）
$ pnpm vitest run src/pages/doc/group.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-docgroup.json
$ jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/lqg-docgroup.json
true
{"numPassedTests":7,"numFailedTests":0}
$ node -e "const c=require('../../doc/verify/fixtures/doc-group-cases.json').cases; …"     → exit 0
```

7 个用例 = fixture 4 例（两个样本组内顺序与组间顺序 / 恰好两份也显示合并入口 / 空列表 / merged 病灶）+ 3 条守卫（fixture 非空 / 空输入 → 空分组 / **fixture 里确实有 merged 病灶例**）。
**fixture 一个字节没改**（`git status` 里它没出现）。

### 3.2 accept 2 · API（构建产物 / 两条清单 / 页签名 / 卡片按钮）—— ✅

```
$ (cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
   jq -e '.tabBar.list[1].pagePath=="pages/doc/index"' dist/build/mp-weixin/app.json &&
   grep -q '合并下载' src/components/lqg/DocGroupCard.vue && grep -q '合并预览' src/components/lqg/DocGroupCard.vue &&
   grep -q 'DOC_TAB_NAME' src/config/app.ts && grep -q '@/components/lqg/DocGroupCard.vue' src/pages/doc/index.vue &&
   ! grep -n 'internalNo' src/pages/doc/index.vue src/components/lqg/DocGroupCard.vue)
true                                             ← 整段 exit 0
app.json tabBar[1] = {"pagePath":"pages/doc/index","text":"文档","iconPath":"static/tabbar/docs.png",...}

$ bash doc/verify/reseed.sh --yes >/dev/null                                            → 快照已回
$ bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001006/organoid_score/render?audience=internal'
{"code":200,"msg":"操作成功","data":{...,"docKind":"organoid_score","audience":"internal","status":"done",
 "ossId":"2102386187661504514","contentHash":"e2a380fdcf89…","templateVersion":"2","cached":false}}   sleep 5
$ bash doc/verify/api.sh --as staff GET '/mp/int/doc/list?pageSize=100' | jq -e \
    '[.rows[]|select((.sampleId|tostring)=="9000001006")|[.title,.subtitle,.docKind,.totalScore]]==[["T-hco04","B 大学","organoid_score",18]]'
true
[["T-hco04","B 大学","organoid_score",18]]        ← 同一个 select 的原样输出

$ bash doc/verify/api.sh --as extD --bizcode GET '/mp/int/doc/list' | grep -qE '^403'
403	没有访问权限，请联系管理员授权                    ← 整段 exit 0
```

逐段重放见 `doc/waves/reports/DOC-MP-001/accept-evidence.txt`；accept 的机器结果 `accept-result.json`，日志 `accept-logs/`。

**另外手工补的两条口径验证**（accept 没断，但票面标题点了）：

```
$ --as staff GET '/mp/int/doc/list?docKind=bogus'        → {"code":200,"rows":[],"total":0}   （空列表，不是 400）
$ --as staff GET '/mp/int/doc/list?publishedEnd=2026-09-13' → 含 09-13 当天那份（[[1001,"sample_qc"],[1001,"organoid_qc"],[1001,"organoid_score"]]）
$ --as staff GET '/mp/int/doc/list?publishedEnd=2026-09-12' → 不含（[[1001,"sample_qc"],[1001,"organoid_qc"]]）
$ --as staff GET '/mp/int/doc/list?publishedBegin=oops'    → 400 时间范围只能是 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss
$ --as staff GET '/mp/int/doc/list?sampleId=9000001001'    → ["sample_qc","organoid_qc","organoid_score"]
```

**「外部清单不带内部编号」**这一半：accept 的命令里只有 extD→403；「外部 VO 的键集合恰好 6 个、禁字一个不多」由 AUTH-EXT-003 的 `ExtDocShapeContractTest`（6/6）与探针 P36/P42 守着 —— 本票复跑这两样，**全绿**（§3.4）。

### 3.3 accept 的票面缺陷：**本票没有发现**（一条都没改票面）

- `--fresh-module` 那条是**沙箱限制**（NF1，accept-run 自己处理），不是票面缺陷。
- 断言里的 `grep -q 'doc/verify/fixtures/doc-group-cases.json'` 能匹配 spec 里的 `@doc/verify/fixtures/doc-group-cases.json`（子串命中），**照字面成立**，没有改票面也没有为了让断言好看去改 import 写法。
- `touches` 与 accept 想定的文件路径/写法逐条对得上（`src/config/app.ts` 的 `DOC_TAB_NAME`、`@/components/lqg/DocGroupCard.vue` 的 import 写法、卡片里必须出现「合并预览」「合并下载」）。
- **唯一需要"解释"的是 `pages.json`**（§0.1）：不是票面写错，而是生成产物入库导致的顺序陷阱。

### 3.4 上游回归（我改了 `DocExternalQueryService`，必须证明行为没变）—— ✅

`doc/waves/reports/DOC-MP-001/upstream-regression.txt`：

```
### AUTH-EXT-003 accept（复跑）
[run] 2 条 accept，单条超时 900s
  ✓ AUTH-EXT-003 acc1 [API]   …
  ✓ AUTH-EXT-003 acc2 [STATE] …
[run] 通过 2/2

### AUTH-EXT-003 对抗性探针 46 条
PASS P1 … P46（含 P20–P24 外部版 failed → 清单/预览/下载三处同时消失；
               P28–P30 ★撤回一份后合并件立刻消失/预览404/下载404；
               P36 清单行没有内部键；P39/P40 带 audience=internal 仍只出 /external/）
PROBE EXT003 PASS=46 FAIL=0
```

模块测试（parent 点名要）：

```
$ (cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg -am test -s ../../.mvn-settings.xml \
     -Dmaven.repo.local=../../.m2repo -Duser.home=../../.buildhome)
[INFO] Tests run: 257, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
TEST_EXIT=0
```

---

## §4 counterfeit 逐条排掉（不是「看着像过了」）

`doc/waves/reports/DOC-MP-001/counterfeit-transcript.txt`（每条都是把 `group.ts` 改坏 → 跑真 spec → 红 → 还原 → 绿）：

| 票面 counterfeit | 实测 | 证据 |
|---|---|---|
| 组内按完成时间排（而不是固定顺序）→ 第一例里评分表会排到质控表前面 | ✅ 红 | 把组内排序换成按时间**倒序**：6 passed / **1 failed**，死在「两个样本，组内顺序与组间顺序」；还原后 7/7 |
| `showMerge` 写成 `length > 2` → 第二例（恰好两份）红 | ✅ 红 | 6 / **1 failed**，死在「恰好两份也显示合并入口」；还原后 7/7 |
| 把 `merged` 行当成一份文档 → 第四例 `showMerge` 变成 `true` 红 | ✅ 红 | 6 / **1 failed**，死在「病灶：接口若混进了 merged 行…」；还原后 7/7 |

> 附注（诚实记一笔）：票面那句「组内按完成时间排」如果写成**升序**，第一例恰好与固定顺序同形（08-31 → 09-10 → 09-13 就是 sample_qc → organoid_qc → score），会改由**第二例**打红。两种方向都实测过，counterfeit 的意图（按时间排必红）都成立；转录里两条都在。

---

## §5 视觉证据（截图）

`doc/waves/reports/DOC-MP-001/*.png`（780×… 逻辑像素 390×844 @2x）。**★ 全部由真 H5 dev server + 真后端 + 真库跑出来**（Playwright 真 DOM，路径 `#/pages/doc/index`），断言值打印在 stdout；**本 agent 没有把任何 PNG 读进上下文**（只看路径与断言值）：

| 文件 | 覆盖 | 断言值（摘要） |
|---|---|---|
| `01-doc-list-internal.png` | 内部视角：2 组。`T-hco04`（B 大学）1 份；`T-hli01`（A 医院）3 份 + 组底两个按钮 | `titles:["T-hco04","T-hli01"]` · `rows:[["类器官质量评分表"],["样本质控表","类器官质控表","类器官质量评分表"]]` · `downloadBtns:4` · `mergePreview:1 mergeDownload:1` · `sectionTitle:"质控文档"` |
| `02-doc-list-external.png` | 外部视角（extA）：组标题是**送检单号 + 掩码姓名**，没有内部编号 | `titles:["SJ90000001","SJ90000004"]` · `subs:["测**","测**"]` · `bodyHasInternalNo:false` · `mergePreview:1 mergeDownload:1` |
| `03-doc-empty.png` | 空状态（未绑定外部账号 newbie1） | `groups:0` · `emptyText:"结果出具后会显示在这里"`（甲方口径逐字） |
| `04-ext-sample-docs.png` | 外部样本详情第③段「质控文档」（ticket §2） | `docs:["样本质控表","类器官质控表","类器官质量评分表"]` · `emptyShown:false` |

脚本原件：`code/miniapp/scripts/shots-doc-mp001.mjs`（副本在取证目录）。

---

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S1** | doc-drift（票面前提不成立） | ★ **票面 §2 说「两个接口的行形状对齐到 `{sampleId,title,subtitle,docKind,publishedTime,totalScore?}`」——实测外部那份给的是 `{sampleId,submitNo,donorNameMasked,…}`**（AUTH-EXT-003 的 `ExtDocVo` 6 键形状契约把它钉成**恰好 6 键**，加不了 `title/subtitle`；`doc/api-contract.md` 第 87 行也只写这 6 键） | 前端在 `group.ts` 做**唯一一处归一化**（`title ?? submitNo` / `subtitle ?? donorNameMasked`），**没有改外部 VO**（改了会打红 AUTH-EXT-003 的 6/6 契约测试）。截图第一版就是因此组标题空着（内部有、外部没有）——已按此修好并留了断言值。方案：把**内部**行形状（含 `title/subtitle/internalNo/sourceUnitName`）补进契约第 86 行，并写明「外部的组标题键名不同」 |
| WARN-2 | **S2** | harness | ★ **`code/miniapp/src/pages.json`（生成产物）入库 → tabBar 顺序取决于「上次生成结果」**；按任务书收尾 `git checkout` 会让 accept 2 红（实测两个方向） | 机理与实跑见 `pages-json-tabbar-order.txt`。本票**保留**重新生成的干净版本；若 QA 还原成 HEAD，请先 `rm code/miniapp/src/pages.json` 再构建。根因方案：把 `src/pages.json` 移出 git（`src/manifest.json` 那个种子例外，SYS-MP-001 坑 1 要留） |
| WARN-3 | S2 | harness（既有，本票不重复计数） | `accept-run` 对 acc2 施加 **NF1**（去掉 `--fresh-module ruoyi-lqg`）+ Maven 三参数 NF2 | 连续 9+ 张命中；本票用等价证据覆盖（§3.0） |
| WARN-4 | **S2** | 口径冲突（上游建议 vs 蓝图） | ★ **AUTH-EXT-003 §7.8 建议「合并按钮按清单里实际有没有 `merged` 行判断」；但蓝图 `FLOW:F-DOC-02.step1` 写「每组最多三份」、`UI:mp.doc.list` 写「≥2 份已完成才显示」，fixture 第四例也要求 `merged` **不算一份**、`showMerge` 由非 merged 份数决定** | 本票**以蓝图 + fixture 为准**：`groupDocs` 把 `merged` 过滤出组，`showMerge = 非 merged 份数 ≥ 2`；同时**接口仍会返回**已渲染成功的 `merged` 行（与外部清单同款），供 DOC-MP-002 判「合并件到底在不在」。本张两个按钮只提示「即将开放」，所以没有 404 面；**DOC-MP-002 把它们接成真下载前必须处理「份数够但 merged 还没渲染」那条路**（否则点了 404 —— 这正是 AUTH-EXT-003 提醒的风险） |
| WARN-5 | S3 | perf | 内部清单的分页在**内存**里切，且取数是「产物表驱动的候选 × 每个样本再查三张质控表」 | 与 AUTH-EXT-003 的 ext 清单（WARN-5）同源、同一个取舍。全库渲染过的文档上千时会退化成 N 次 doc 查询。方案（属新口径，本票没做）：在 doc 域读口加一条**按 `t_lqg_doc_file` 驱动 + 真分页**的查询 |
| WARN-6 | S3 | doc-drift（#254，AUTH-EXT-003 已记） | 外部 `pages` / `download` 两个端点的 `data` 形状没进契约，且外部预览只给页面图、不给 `images/attachments` | 本票**不需要**图片位/附件（分组卡片只用清单行），所以**既没改契约、也没发明形状**；DOC-MP-002 要接预览页时按 #254 的处置「先改契约再改断言」，**别**去调内部 `/lqg/doc/.../pages`（那是外部隔离面） |
| WARN-7 | S3 | leftover | `src/pages/docs/index.vue`（SYS-MP-001 的占位页）现在**没有任何入口**（tab 已改指 `pages/doc/index`），仍被扫描进 `pages` → 产物里多一个死页面；`code/miniapp/scripts/shots-h5.mjs` 的 `06-docs` 仍指向 `/pages/docs/index` | 本票**没删没改**（不在 `touches`，且是 SYS-MP-001 的取证脚本）。建议 DOC-MP-002 或一张清理票一起收掉 |
| WARN-8 | S3 | clarify | 内部清单的 `title` 在样本**没有内部编号**时**回落送检单号**（本票加的防御：组标题空着比露送检单号更糟） | 若甲方口径是「内部编号为空就不显示/显示占位」，改 `MpDocService.toVo` 一处即可；契约没规定 |
| WARN-9 | S3 | doc-drift | 契约第 86 行写「内部：全部已完成文档」，票面 §2 写「全部已完成**且内部版渲染成功**的」 | 本票按**票面（更严）**实现：只列内部版产物完整的（否则点进去是空的）。验收 `reseed → render → 出现` 正是这个顺序。若甲方要「已完成就列」，要同时改 `MpDocService` 与蓝图 |
| WARN-10 | S3 | clarify | 内部清单**包含 `merged` 行**（与外部一致），但 UI 口径「每组最多三份」由**前端**过滤 | `docKind=merged` 能被筛出来（接口支持），页面不显示它。DOC-MP-002 若要在页面上体现「合并件已就绪」，可以从接口行里找 `merged`（分组结果里已经把它丢了） |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 在沙箱恒 exit 2 + Maven 三参数、`api.sh` token 缓存只看 mtime、`db.py` 是只读却 exit 0、`updateById` 忽略 null、`sys_oss` 行随重出单调增长（别用 `count(*)` 断言）、微信开发者工具在本沙箱跑不通（本票走 H5 + Playwright）、`pnpm type-check` 被 wot 自身类型错误打红。

---

## §7 给下游的坑（DOC-MP-002 / SYS-EXPORT-001）

1. ★★ **不要自己写「能不能看」的判断**：内部 / 外部两个清单共用 `DocAvailabilityService.available(sampleId, docKind, audience)`。
   正确判据是**锚在产物上**：当前指纹下有页图 + PDF 与 header 同版（+ 单份要 `published`）。
   **别比 `header.content_hash` 与此刻算出来的指纹** —— 那条实测无效（issue #250 / AUTH-EXT-003 WARN-3）。
   `DocAvailabilityService` 就是这次抽出来的那一个类，**直接注入用**，别再复制一份。
2. ★ **两个清单接口的组标题键名不同**（WARN-1）：内部 `title/subtitle`（= 内部编号 / 来源单位）、外部 `submitNo/donorNameMasked`（= 送检单号 / 掩码姓名）。归一化只许有一处（现在在 `src/pages/doc/group.ts`）。**前端不许自己算内部编号或掩码**（accept 的禁字 grep）。
3. ★ **DOC-MP-002 要建的端点**：契约第 86 行写了 `GET /mp/int/doc/{sampleId}/{docKind}/pages` 与 `…/download`，**本票没建**（ticket §3 明确不做预览与下载）。
   建的时候：挂在 `/mp/int/doc/**`、**类级 `@SaCheckRole("lqg_internal")`**、`audience` 写死 internal、**别**让小程序直连内部 `/lqg/doc/**`（那是给工作台带 `audience` 入参的那一套）。
4. ★ **合并按钮的两条路**：`showMerge` 现在只表示「≥2 份」（蓝图 + fixture 口径）。真去预览/下载 `merged` 之前，**先确认这一版的合并件产物在不在**（`/mp/int/doc/list` 的行里会有 `merged`，分组时被前端丢掉了；也可以调 pages/download 并优雅处理 404）。契约与 AUTH-EXT-003 §7.8 都提醒过「份数够但还没渲染」是真实存在的。
5. ★ **页面图 / 下载链接都是 10 分钟签名链接**：别缓存、别存库、别自己拼字符串；进预览页重新调一次。`download` 的 `fileName` 内部版是「文档名-内部编号」，外部版「文档名-送检单号」。
6. ★ **`docKind` 只有下划线那四个**（`sample_qc/organoid_qc/organoid_score/merged`）；质控草稿侧的连字符三个（`sample-qc/organoid-qc/score`）是另一套。清单里不认识的值 → **空列表**；时间格式写错 → 400。
7. ★ **合并件的 `publishedTime` = 成员里最新那一份的完成时间**（它自己没有完成时间列；issue #255 已记）。本票内部 / 外部两个清单都这么给，实测 `merged → 2026-09-13`（= organoid_score 那份）。
8. ★ **模板升级后历史文档刻意仍对外可见**（issue #256）：所以单份文档**不**拿指纹当门槛。若 DOC-MP-002 加断言，别把「指纹对不上 → 不显示」写进去。
9. ★ **`merged` 不进组、也不计入合并判断**：`groupDocs` 的输入里混进 `merged` 是**合法**的（外部清单本来就会给），它必须被丢掉（fixture 第四例就是这个病灶）。别把这段过滤"优化"掉。
10. ★ **SYS-EXPORT-001**：内部清单的口径是「**内部版渲染成功**的才算」（§WARN-9）。导出若要「文档完成情况」，读 `doc_status` / `published_time` / `published_by`（`published_by` 是 `sys_user.user_id`，要人名得 join）；**排除 `del_flag='1'` 的样本**（1010，AUTH-EXT-003 §7.9 的既有病灶；本票清单在 `requireSample` 那道就绕开了）。
11. ★ **`src/pages.json` 的顺序陷阱**（WARN-2）：改 tab / 增删页面后若发现 `app.json` 的 tabBar 顺序不对，**先删 `code/miniapp/src/pages.json` 再构建**。
12. ★ **跑 H5 取证的三个坑**（本票踩过，省时间）：① Playwright 的 require 锚点必须是 `code/miniapp/package.json`（issue #229），脚本放 `code/miniapp/scripts/` 下跑；② 登录页要勾协议时**点 `.login__box` 勾选框**，点整行 `.login__agree` 的中心会命中《用户协议》那个 `@click.stop` 链接而跳走；③ uni-h5 登录后首页的 hash 是 `#/`（不是 `#/pages/index/index`），别用 `waitForURL(/pages\/index/)`。

---

## §8 与 `doc/api-contract.md` 的差异（逐行核过）

| 契约行 | 契约写的 | 实现 | 判断 |
|---|---|---|---|
| 第 86 行 `GET /mp/int/doc/list` | 「内部：全部已完成文档，`audience` 固定 internal。list 可带 `sampleId`」 | 逐字实现；**更严**：只列「`published` 且内部版产物完整」的（票面 §2 的口径，WARN-9）；`audience` 不是参数（内部清单只有内部版）；额外支持 `docKind` / `publishedBegin` / `publishedEnd` | ⚠️ 契约缺**行形状**（WARN-1），建议补 |
| 第 86 行 `GET /mp/int/doc/{sampleId}/{docKind}/pages` / `…/download` | 契约列了这两个端点 | **本票没建**（ticket §3：不做预览与下载；归 DOC-MP-002） | ⏳ 下游票 |
| 第 87 行 `GET /mp/ext/doc/list` | 逐字（含 `ExtDocVo` 6 键） | 本票**只读消费**，未改；前端按 6 键归一化组标题（WARN-1） | ✅ |
| 第 87 行外部 `pages` / `download` 的 `data` 形状 | 契约没写 | 本票不消费（分组卡片只用清单行） | ⚠️ #254，AUTH-EXT-003 已记；**本票不改契约、不发明形状** |
| `doc/api-contract.md` 文件本身 | —— | **一个字节没动**（只读区） | ✅ |

---

## §9 长进程 / 端口 / 收工自检

### 9.1 `git status --porcelain`（收工态）

```
 M code/miniapp/pages.config.ts
 M code/miniapp/src/api/doc.ts
 M code/miniapp/src/components/lqg/DocGroupCard.vue
 M code/miniapp/src/pages.json
 M code/miniapp/src/pages/doc/group.ts
 M code/miniapp/src/pages/sample/detail-ext.vue
 M code/miniapp/src/router/config.ts
 M code/miniapp/src/types/uni-pages.d.ts
?? code/miniapp/scripts/shots-doc-mp001.mjs
?? code/miniapp/src/pages/doc/index.vue
?? code/miniapp/src/pages/doc/preview.vue
?? doc/waves/reports/DOC-MP-001/
```

- **`src/pages.json` 是「有意不还原」**（§0.1，WARN-2）——**不是**忘了 `git checkout`。
- `src/types/uni-pages.d.ts` 与 `src/pages.json` 同源（生成物，随最后一面构建重写）。
- 其余后端文件（`doc/mp/MpDoc*`、`doc/service/DocAvailabilityService.java`、`DocExternalQueryService.java` 的改动）与 `src/config/app.ts` / `src/pages/doc/group.fixture.spec.ts` / `src/pages/doc/group.ts`(部分) 已经落在 `069801e`（AUTH-EXT-003 终报入账那次提交，**不是本票提交的**），所以不出现在 porcelain 里。
- **只读区**（`_input/` / `doc/requirements.yaml` / `doc/authority/**` / `doc/change-log.md` / `doc/api-contract.md` / `doc/verify/{seed/**,gen_seed.py,api.sh,fixtures/**,reseed.sh,db.py}`）：`git status --porcelain` **全空**。
- **`doc/waves/state.json` / `_manifest.json`**：本票**没动**（porcelain 里没有它们）。
- 本票**没有** `git add` / `git commit` / `git push` / 切分支 / 改历史。

### 9.2 长进程 / 端口（收工）

| 进程 | 端口 | 处置 |
|---|---|---|
| 后端（本票重建的 jar） | 8094 | **已按 PID 关停**（`bash doc/waves/tools/qa-up.sh --down --backend-port 8094`） |
| 工作台 plus-ui | 8093 | **已关停**（早前 `--down` 一起收掉，收工复核 `lsof` 为空） |
| 小程序 H5（`pnpm dev:h5`，本票取证用） | 9204 | **已按 PID 关停**（`kill <pid>`；先 `lsof -ti tcp:9204` 取 PID） |
| 8080（Kevin）/ 5432 / 6379 | —— | **全程没碰**（开工与收尾两次复核都为空） |
| `lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002-9003 / `lqg-dev-gotenberg` 3010 | —— | 全程在跑，**没停**（留给后续 ticket） |

- 关进程**只按 PID**；全程**没有** `pkill -f 'ruoyi-admin.jar'`。
- **DB 收尾**：`bash doc/verify/reseed.sh --yes` 回确定性快照（accept / 探针 / 渲染 / 截图都改过库）。

### 9.3 取证目录 `doc/waves/reports/DOC-MP-001/`

| 文件 | 内容 |
|---|---|
| `accept-result.json` | `accept-run.py` 的机器结果（2/2 + acc2 的 `nf:["NF1"]`） |
| `accept-transcript.txt` / `accept-logs/` | accept 运行输出 + acc1/acc2 的逐条日志 |
| `accept-evidence.txt` | **两条 accept 的 `run` 逐段重放**（票面逐字，唯一差别 NF1），每段带原样输出 |
| `counterfeit-transcript.txt` | 三条 counterfeit 逐条打红 + 还原转绿（含票面字面那一版倒序排序） |
| `upstream-regression.txt` | AUTH-EXT-003 accept 2/2 + 探针 46/46 |
| `machine-evidence.txt` | jar/进程/迁移号/模块测试/两个接口原样 JSON/过滤边界/**共享产物闸的前后对照**/前端禁字与零色值 |
| `pages-json-tabbar-order.txt` | `src/pages.json` 顺序陷阱的机理与两个方向的实跑（WARN-2） |
| `shots-doc-mp001.mjs` | 取证截图脚本副本（原件在 `code/miniapp/scripts/`） |
| `01-doc-list-internal.png` · `02-doc-list-external.png` · `03-doc-empty.png` · `04-ext-sample-docs.png` | 四张截图（**未读进上下文**，只登记路径与断言值） |

### 9.4 ★ 真机 / 微信里**未覆盖**（如实说明，不是遗漏）

- **未做体验版 / 真机档**：没跑 `pnpm upload:mp`，不要求 appid / 上传密钥 / 合法域名 —— 这是 **Kevin 2026-09-22 明确的口径**（appid 暂时无法提供，小程序一律走本地 Mock）。
- **未在微信开发者工具里看过**：本 agent 沙箱跑不通它（要写 `~/Library/Application Support/微信开发者工具/**`，且首次要扫码登录；SYS-MP-001 WARN-1 已记）。
- **替代验收面**：`pnpm dev:h5`（`env/.env.development` 带 `VITE_MOCK_LOGIN=1`）+ Playwright 真 DOM + **真后端（8094）** + **真库（PG 5433）**，登录走后端 mock 路径（`xcxCode=mock:<key>` + `phoneCode=mock:<手机号>`），全部数据来自真接口。
- **因此下列项在真机上仍未验证**（留给 SYS-STAGING-001 或人工）：微信 `open-type="getPhoneNumber"` 真链路、原生 tabbar 图标在真机的观感、`uni.navigateTo` 在页面栈满时的退化路径、真机字体下组标题的换行。**小程序产物本身是构建过的**（`pnpm build:mp-weixin` 是 accept 2 的一部分，`pages/doc/index.js` / `.wxml` / `.wxss` / `.json` 四个产物都在盘）。
