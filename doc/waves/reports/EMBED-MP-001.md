# EMBED-MP-001 · 完工报告

- **ticket**：EMBED-MP-001（track EMBED / phase D3 / size M）—— **D3 最后一张**：小程序石蜡包埋填写页 / 只读工作表 / 历史页签
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑；唯一差异 = 去掉本沙箱跑不了的 `--fresh-module ruoyi-lqg` 与给 `pnpm` 补 `npm_config_store_dir`，等价证据见 §4.0）
  - accept 1 · API+STATE：✅（构建 2 个产物断言 + 7 段 grep + vitest 19 用例 + 5 段接口/库链）
  - accept 2 · STATE：✅（7 段 PUT/库内对账）
  - accept 3 · STATE：✅（fixture 结构 node 断言 + vitest 17 用例 + 3 段 grep；**最后一段的路径见 §5.2**）
- **分支**：`task/D3`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 09:49:11`（epoch 1790041751），后端进程 **PID 77121**（8081，dev profile + `--api-decrypt.enabled=false`）
- **迁移**：**没有加 Flyway**（`/mp/int/embed` 无菜单、无新表、无新配置项；最大版本仍是 `V202609231110`）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,verify.env}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`
- **没碰**：8080（Kevin）/ 5432 / 6379；关进程一律按 `lsof -ti tcp:<端口> -sTCP:LISTEN` 拿 PID 再 `kill`（**没用过 `pkill -f`**）
- **产物**：后端 3 个新类（`embed/mp`）+ 1 个新测试 + 2 个既有类的小改（`keyword` 搜索口）；小程序 9 个新文件 + 6 个既有文件的小改 + 1 个截图脚本
- **单测**：后端 `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0`（本票 +7 `MpEmbedContractTest`、+1 `EmbedQueryContractTest`）；小程序 vitest **115**（本票 +36：19 染色 + 17 布局）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D3`（未切分支；`git log --oneline -1` = `3d659ef SAMPLE-HINT-001`） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `EMBED-MODEL-001` / `SAMPLE-MP-002` / `AUTH-EXT-002` 的报告都在 `doc/waves/reports/`；`/mp/int/sample`（4 端点）、`pages/ledger/{sheets,columns}.ts`、`components/lqg/{LedgerTable,EmbedCard}.vue`、`EmbedQueryService.{list,detail,entity}` / `EmbedService.{create,update}`、`/mp/ext/embed/**` 全部在盘并**被本票真调用**（§4.1/§4.2 的 12 段接口链） |
| 8 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:mp.ledger` / `UI:mp.embed.list` / `UI:mp.embed.form` / `UI:mp.history` / `FLOW:F-EMBED-01.step1,step2,step3,step6` 全部 `active`；口径落点见 §1 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260918-07**（① 表格页只读详情 +「修改」入口；② 历史页签内部默认全中心 +「只看我提交的」开关 + 经手人）与 **CR-20260917-05**（首页点表即填写、外部也能填、历史编辑记录取代「我的送检」）、**CR-20260921-08**（视觉方向 A）逐条落进实现，见 §1 |
| 逐字读两份 fixture（**一个字节没改**） | ✅ PASS | `stain-toggle-cases.json`（9 例）/ `embed-form-cases.json`（9 例 / 外部 3 / 内部 16 / 不可改 5）的期望值全部由 spec 读；`git status` 里两份都不出现 |
| 视觉按方向 A（CR-20260921-08） | ✅ PASS | 新页面只用 `src/style/components.scss` 的 `.lqg-*` 与 `wd-*`；新 `.vue` 里**零色值字面量**、无 `:not(` / 裸 `*` 选择器（§4.4 c） |
| 动手前代码是绿的 | ✅ PASS | 本票前 `Tests run: 112`；本票后 **120**（0 失败）；小程序 vitest 79 → **115**（0 失败） |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002-9003） | ✅ PASS | 三个容器全程在跑，**没停**；8081 用 `.tmp/run-backend.sh` 起（**受管后台 job**，不是 `nohup &`） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

---

## §1 口径复述（ticket §0 的 7 点 + CR，逐条对 accept 核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **保存不要求填完**；再次打开是补填（= 修改），不是新建 | 七个工序时间七格全可空（`layout.ts` 只有一个 `required`：内部新增的石蜡块编号）；补填走 `PUT /mp/int/embed`（后端的**补丁**语义） | accept 2：`{"id":2002,"dehydrateTime":"2026-09-16"}` 一次 PUT → `count(*)==2`（**没新增行**）、`update_by` 非空、`sort=recent&mine=true` 排到第 1 |
| 2 | **染色五按钮多选 +「无染色」互斥**，逻辑与工作台同一份 fixture | `pages/embed/stain.ts` 的`toggleStain`（纯函数）；页面**唯一入口** `onToggleStain` → 不自己写第二份互斥 | accept 1 链内 `vitest src/pages/embed/stain.fixture.spec.ts` → `numPassedTests==19`（9 条 fixture 逐例 + 10 条补充判据）；工作台 `plus-ui/.../stain.fixture.spec.ts` 读**同一份** JSON，本次回归仍绿 |
| 3 | **日期两侧转格式**（控件要毫秒时间戳、后端要 `yyyy-MM-dd`） | `toMs()`（`yyyy-MM-dd` → 毫秒，喂给 `wd-datetime-picker`）与 `formatMs()`（毫秒 → `yyyy-MM-dd`，回填表单） | 端侧 `04-ledger-embed-edit`：点「修改」后 15 个输入控件都带值；`07/08` 的历史行日期 = `yyyy-MM-dd`（DOM 文本） |
| 4 | **列来自 `ledgerColumns('embed')`**；冻结格 = 石蜡块编号（外部待核验的显示送检单号 +「待核验」）；第二行小字 = 工序进度小圆点；**表格只读** | `sheets.ts` 的 `embedSheet`：`toRows()` 里的 `cols.columns.map(...)` 直接取 `columns.ts`（**页面里一个列名都不写**）；`api/embed.ts` 的 `embedLedgerFrozen/Sub/Tone` | accept 1：`grep -q "embed" src/pages/ledger/sheets.ts`；端侧 `01`：表头 16 列（冻结 + 15）、计数行「共 5 条 · 左右滑动看全部 16 列」、冻结格 `["SJ90000002\n待核验 · ○○○○○○○○","T-E04-1\n○○○○○○●○",…]`；`02`：`scrollLeft 1528 / scrollWidth 1918 / clientWidth 390` + 冻结列 `sticky/0px`；`03`：只读详情**提交按钮 0 / 输入控件 0 / 修改入口 1**；`04`：点「修改」后 **提交按钮 1**（表格本身仍无行内编辑、无核验） |
| 5 | **外部只渲染「选择样本」+ 样本类型 + 类器官来源类型**；选样本只列本人送检过、没判无效的（送检单号 + 掩码供体姓名，**不出现内部编号**）；编号/工序/染色/marker/包埋人/操作人/备注一个都不渲染 | `layout.ts` 给外部**只返回三项**（不是置灰）；`components/lqg/SamplePickerExt.vue` 数据源 `fetchExtMySamples()`（`onlyMine=true` + 滤掉 `invalid`） | accept 3：fixture 9 例逐例 + `externalFields==3`；端侧 `09`：外部新增分组只有 `["送检信息"]`、字段 `["* 选择样本","样本类型","类器官来源类型"]`；`10`：候选 `["SJ90000002\n测** · 胆管组织","SJ90000001\n测** · 肝组织"]`（送检单号 + 掩码姓名，无内部编号） |
| 6 | **内部修改模式里待核验 / 无效的外部送样只读**；顶部提示核验与改判到工作台 | `layout.ts` 内部 `edit` 分支 = `verifyStatus==='valid'`；后端 `EmbedService.update` 对非 valid 直接 400（**不重复实现**） | fixture「内部看外部送来的待核验」`editable:false`；accept 2 末两段：改 2006 → `400/500` 且库内仍是 `pending|-|组织` |
| 7 | **历史编辑记录（内部）默认是全中心**：数据源 `?sort=recent`（**不带 `mine`**），开关打开才**追加** `mine=true`；行带经手人 `handlerName` 与 `mine` | `pages/history/sources.ts` 的 `embedSource.fetch`：`fetchIntEmbedList({ sort: 'recent', mine: onlyMine })`（**没有写死 `mine=true`**）；行 = `handlerName`（本人显示「我」）+「新增 / 修改」（看 `updateTime`） | accept 1：两段 grep（`sort: 'recent'` 命中、`int/embed/list?...mine=true` 0 命中）+ 接口链：`sort=recent` → 全 5 条且 `2006` 打头 + `handlerName` 无 null + `2001→["李工",true]` / `2004→["测试管理员",false]`；`&mine=true` → `{2001,2003}`；**直连库独立数**同集合（`create_by/update_by=9000000101`）；端侧 `07`（开关关）5 行 + 经手人 `["我","王医生","测试管理员","我","测试管理员"]`、`08`（开关开）2 行 |

**CR 覆盖 ticket 正文的两处（以 CR 为准）**

1. ticket §2 只说「（`EmbedQueryBo` 已有 `sort=recent`）」，正文没有把「默认全中心」写全 —— **CR-20260918-07** 定的是「内部默认中心全员 + 顶部『只看我提交的』开关」。本票按 CR 实现（与样本 / 类器官两个页签**同一形状**），accept 1 的 grep 与接口链就是它的机器证据。
2. CR-20260918-07 ①：内部管理表格页 = **只读表格 + 只读详情里的「修改」入口**。本票：`sheets.ts` 的 `target()` 只有 `mode=view`（accept 复跑 SAMPLE-MP-002 的 `! grep mode=edit` 仍绿），只读页右上角 `showEditEntry` 切 `mode=edit`。

**一条跨域口径差异（照本票 accept 实现，记 WARN-1）**：SAMPLE 域的 `sort=recent` 还叠了一层范围收窄（「经手人必须是 `user_type='sys_user'` 的内部账号」，见 `SampleQueryService`），而 **EMBED 域的 `sort=recent` 只做排序**（全表按 `COALESCE(update_time, create_time)` 倒序）。本票 accept 1 明文要求这五条**全在**（含 extA 建的 2006，经手人显示「王医生」），故按 accept 实现。

---

## §2 改了哪些文件

### 2.1 后端 · 新增（`org.dromara.lqg.embed.mp`，在 `touches` 内）

| 文件 | 职责 |
|---|---|
| `embed/mp/MpEmbedController.java` | `/mp/int/embed` 的四个端点（list / {id} / POST / PUT），**类级 `@SaCheckRole("lqg_internal")`**（契约第 63 行 + 「通用」一节的表） |
| `embed/mp/MpEmbedService.java` | 读写编排：读走 `EmbedQueryService`、写走 `EmbedService`；**不查库、不拼 wrapper、不自己写状态闸**（只有一条判据） |
| `embed/mp/MpEmbedQueryBo.java` | 内部侧筛选 BO（extends `EmbedQueryBo`）；`sort` / `mine` / `keyword` 全继承自父类（同一条读路径） |
| `src/test/java/.../embed/mp/MpEmbedContractTest.java` | **7 个用例**：类级角色与路径（含「方法上不许再写一份角色」）/ `PUT` 与 `POST` 的扁平形状 / **mp 包不许持有 `*Mapper`** / `keyword`+`sort`+`mine` 的默认值 / `sort=recent` 只认字面量 / 入参无 `verifyStatus` |

### 2.2 后端 · 修改（**越出 `touches`，见 §5.1**）

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `embed/domain/bo/EmbedQueryBo.java` | +`keyword`（`@Schema`：石蜡块编号模糊 OR 所挂样本内部编号等值） | UI:mp.embed.list 的筛选是「搜索（石蜡块编号 / **内部编号**）+ 核验状态 + 染色」——**一个**搜索框要命中两种编号，只有读路径能给这个 OR。落法与 SAMPLE 域 `SampleQueryBo.keyword` **逐字同形**（SAMPLE-MP-002 的先例） |
| `embed/service/EmbedQueryService.java` | `buildWrapper` 加 4 参重载（`keywordSampleIds`）+ 留 3 参委托；`list` / `exportRows` 传 `keywordSampleIds(q)`；+私有 `keywordSampleIds()` | 同上。**OR 包成一组**（`and(w -> w.like(...).or().in(...))`）；内部编号那一半没命中时退化成「只按石蜡块编号模糊」，**不回空页**（这是 `internalNo` 筛选与 `keyword` 搜索的语义差别，注释里写明了） |
| `src/test/java/.../embed/service/EmbedQueryContractTest.java` | +第 5 个用例「keyword 的一组 OR 被括号包住 / 没命中时不留空 `IN ()` / 不带 keyword 时一个字都不多」 | 把上面那条判据钉住（3 参重载让原 4 个用例**一字未改**继续绿） |

**接口清单（本票新增，全部 `@SaCheckRole("lqg_internal")`）**

```
GET  /mp/int/embed/list?keyword=&verifyStatus=&stain=&sort=recent&mine=true&pageNum=&pageSize=
                                表格页（keyword/verifyStatus/stain）/ 历史编辑记录（sort=recent[&mine=true]）
GET  /mp/int/embed/{id}         详情（带 internalNo / submitNo / handlerName / mine / editable / updateByName / updateTime）
POST /mp/int/embed              首页点表新增（内部录入直接 valid、石蜡块编号必填、全库唯一）
PUT  /mp/int/embed              补填 / 修改（**patch**；谁录的都能改；待核验 / 无效 → 400）
```

**Flyway / 取号**：**本票不新增迁移**（`/mp/int/**` 不挂菜单、没有新表、没有新配置项）。最大版本仍是 `V202609231110`（`doc/waves/state.json` 里那条「D1 回归包把总数写死成 7」的假红与本票无关，见 §4.4）。

### 2.3 小程序 · 新增

| 文件 | 职责 |
|---|---|
| `src/pages/embed/layout.ts` | **纯函数** `embedLayout(identity, verifyStatus, mine, mode)` → `{fields, editable, showCard, showEditEntry}` + `normalizeMode` / `fieldSpecs` / `groupSpecs` / `hasSamplePicker`（fixture 的被测函数；`showEditEntry` 是 CR-20260918-07 的新增返回键，**不改 fixture**） |
| `src/pages/embed/layout.fixture.spec.ts` | fixture 驱动 **17 用例**：结构守卫（9 / 3 / 16 / 5）+ 9 例逐例 + 词表守卫 + 3 条病灶守卫 + `showEditEntry` 逐例 + 身份缺失 + **外部选样本组件禁字（按真实路径读文件）** |
| `src/pages/embed/stain.ts` | 染色切换**纯函数**（五值固定顺序 / NONE 互斥 / 字典外值忽略）+ `STAIN_OPTIONS` / `EXPR_OPTIONS`（字典 `lqg_stain_type` / `lqg_marker_expr` 的显示名） |
| `src/pages/embed/stain.fixture.spec.ts` | 读需求层 fixture **19 用例**（9 条逐例 + 10 条互斥/顺序/脏值/自检） |
| `src/pages/embed/form.vue`（占位页 → 正文） | 填写页三模式：内部分组（送检信息 / 工序时间 / 染色与 marker / 其他）、七个日期弹框、染色五按钮 +「其他」名称、marker 多行增删、只读页右上角「修改」、外部有效只读 + `EmbedCard`、外部无效红条、`?sampleId=` 带样本进新增 |
| `src/api/embed.ts` | 内外部八个接口 + 表单值/提交体（内部 `POST`/`PUT`、外部两字段覆盖）+ **表格页单元格 / 冻结格 / 进度小圆点 / 行底色** + 历史行摘要的四个纯函数 |
| `src/components/lqg/SamplePicker.vue` | 内部「选择样本」底部弹层（`/mp/int/sample/list?verifyStatus=valid&keyword=`，只列已核验有效） |
| `src/components/lqg/SamplePickerExt.vue` | 外部「选择样本」底部弹层（`onlyMine=true` + 滤掉无效；送检单号 + 掩码供体姓名；**不含内部编号**） |
| `src/components/lqg/StainButtons.vue` | 染色五按钮（多选形态；**不做互斥判断**，点哪一个抛给页面过 `toggleStain`） |
| `src/components/lqg/MarkerRows.vue` | marker 多行（名称输入 + 三按钮单选 + 增删；只读时只显示文字） |
| `scripts/shots-embed-mp001.mjs` | 端侧截图脚本（H5 dev + Playwright，真接口造数据，**不读图**） |

> ★ **四个组件落在 `src/components/lqg/` 而不是 `src/pages/embed/`**：`src/pages/**` 下任何 `.vue` 都会被 `@uni-helper/vite-plugin-uni-pages` 注册成**页面路由**（实测：放在 pages 下时 `pages.json` 多出 4 条 `"type": "page"` 的垃圾路由、`types/uni-pages.d.ts` 多 4 个路径）。放在 `src/components/lqg/`（所有既有公共组件的家）后 `pages.json` / `uni-pages.d.ts` **回到 HEAD 零 diff**，只有 `types/components.d.ts` 多 4 行 easycom 注册（与 AUTH-EXT-002 加 `EmbedCard` 同一处理）。详见 §5.2。

### 2.4 小程序 · 修改

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `src/pages/ledger/sheets.ts` | `LedgerSheet` 加 `searchPlaceholder` / 可选 `toRows`；筛选 key 扩成 `verifyStatus \| stain`；**注册 `embed` 工作表**（短名「石蜡包埋」/ 全称 / 搜索占位「搜石蜡块编号或内部编号」/ 两个筛选组 / `fetchEmbedLedgerRows` / 冻结格两行 / `?id=&mode=view`）；`toTableRows(rows, sheet?)` 加可选第二参 | ticket §2「在 sheets.ts 注册 embed」；搜索框占位与筛选项必须**从注册表来**（SAMPLE-MP-002 的 accept 有「页面里不许写列名」的禁字 grep，写死在页面里会红） |
| `src/pages/ledger/index.vue` | 筛选区从「`filters[0]` 一组 chips」改成「`v-for` 遍历 `sheet.filters` 的所有组」+ `pickFilter(key,value)` / `activeFilter(key)`；搜索框占位读 `sheet.searchPlaceholder`；`toTableRows(rows, sheet)` | 石蜡包埋这张表有两个筛选组（核验状态 + 染色），页面原先只认第一组。**越出 `touches`，见 §5.1** |
| `src/api/ledger.ts` | `LedgerFilters` +`stain`、`emptyFilters()` +`stain: ''` | 同上一处（筛选条件的家在这里）。**越出 `touches`，见 §5.1** |
| `src/pages/history/sources.ts` | `HistoryRow.raw` 放宽成 `SampleRow｜EmbedDetail`、`HistorySource` 用 `HistoryRaw`；**注册 `embed` 数据源**（内部 `sort=recent` **不带 mine**、开关打开才追加 `mine=true`；外部 `onlyMine=`；行 = 石蜡块编号/送检单号 + 样本类型 + 经手人 + 新增/修改 + 日期；点行内部进 edit、外部 `mine&&editable` 才 edit） | ticket §2「在 sources.ts 注册 embed」（CR-20260918-07 的全中心口径） |
| `src/pages/sample/form.vue` | 「给这个样本加石蜡块」置灰 → `addEmbed()` 带 `sampleId` 进 `pages/embed/form?mode=new`（「加冻存」仍置灰，归 CRYO-MP-001） | ticket §2 最后一条 |
| `src/types/components.d.ts` | **生成产物**：4 个新组件的 easycom 注册（4 行） | 新组件注册必须跟着提交（SYS-MP-001 / AUTH-EXT-002 同一处理） |

**没有动**：`src/pages.json` 与 `src/types/uni-pages.d.ts`（组件移出 `pages/` 后**回到 HEAD 零 diff** —— 见 §8）、`code/plus-ui/**`（工作台一个字节没动）、`code/miniapp/src/pages/admin/**`、`doc/**` 任何只读区、`application*.yml`、Flyway。

---

## §3 视觉证据（截图清单）

微信开发者工具在本 agent 沙箱跑不通（CLI 要写 `~/Library/Application Support/微信开发者工具/**` → EPERM；首次要扫码，同 SYS-MP-001 / SAMPLE-MP-001 / SAMPLE-MP-002）
→ 端侧证据用 **H5 dev server（`pnpm dev:h5 --port 9200`，带 `VITE_MOCK_LOGIN=1`）+ Playwright** 覆盖。
**开发者工具 / 真机未覆盖**（如实写明，不用 H5 冒充）。截图在 `doc/waves/reports/EMBED-MP-001/`，
**全程没有把任何 PNG 读进上下文**，断言只读 DOM 文本 / 元素计数 / 计算样式（完整输出见 `shots-transcript.txt`）：

| 文件 | 覆盖项（ticket §4.1 逐条） | 实测断言输出 |
|---|---|---|
| `01-ledger-embed.png` | 内部管理的石蜡包埋工作表 | 页签 `["样本记录","类器官收样","石蜡包埋"]`；计数行 `"共 5 条 · 左右滑动看全部 16 列"`；表头 16 列 `["石蜡块编号","样本编号","样本类型","类器官来源类型","组织收样时间","组织处理时间","琼脂糖包埋样本时间","包埋人","脱水时间","琼脂糖包埋样本送样时间","石蜡包埋时间","切片时间","染色","mark的表达情况","操作人","备注"]`；筛选 chips `["全部","待核验","有效","无效","全部","HE","IF","IHC","其他","无染色"]`；行 5 / 待核验 1；冻结格+进度点 `["SJ90000002\n待核验 · ○○○○○○○○","T-E04-1\n○○○○○○●○","T-E02-1\n○○○○○●●○","T-E01-2\n○○●○○○○○","T-E01-1\n●●●●●●●●"]`（末位 = 包埋人） |
| `02-ledger-embed-scrolled.png` | **左右滑动**（冻结列仍贴左边） | `{ok:true, scrollLeft:1528, scrollWidth:1918, clientWidth:390}`；冻结列计算样式 `{position:"sticky", left:"0px"}` |
| `03-ledger-embed-view.png` | 点一行进的**只读详情** | URL `…/#/pages/embed/form?id=9000002001&mode=view`；**提交按钮 0 / 输入控件 0 / 修改入口 1**；字段（前 8）`["选择样本","石蜡块编号","样本类型","类器官来源类型","组织收样时间","组织处理时间","琼脂糖包埋样本时间","包埋人"]` |
| `04-ledger-embed-edit.png` | 点「修改」之后的**修改模式** | 提交按钮 **1**、输入控件 **15**、顶部小字 `["最后修改：李工 · 2026-09-22 09:58:56"]`（跑之前先用真接口补填了一次 2001 —— 它原本 `updateTime` 为空） |
| `05-int-embed-form-new.png` | 内部填写页（新增） | 分组 `["送检信息","工序时间","染色与 marker","其他"]`；字段 `["* 选择样本","* 石蜡块编号","样本类型","类器官来源类型","组织收样时间","组织处理时间","琼脂糖包埋样本时间","包埋人","脱水时间","琼脂糖包埋样本送样时间","石蜡包埋时间","切片时间","操作人"]`；染色按钮 `["HE","IF","IHC","其他","无染色"]`；提交按钮 1 |
| `06-int-embed-form-markers.png` | **marker 多行**（连点两次「＋ 加一行」） | marker 行数 **2**、行标题 `["第 1 行","第 2 行"]` |
| `07-int-history-embed-off.png` | 历史编辑记录 · 石蜡包埋页签（**默认全中心 + 经手人列**） | 页签 4 个；行 `["T-E01-1","SJ90000002 · 待核验","T-E04-1","T-E02-1","T-E01-2"]`；经手人 `["我","王医生","测试管理员","我","测试管理员"]`；新增/修改 `["修改","新增","新增","新增","新增"]` |
| `08-int-history-embed-on.png` | 「只看我提交的」打开后 | 行 `["T-E01-1","T-E02-1"]`、经手人 `["我","我"]` |
| `09-ext-embed-form-new.png` | 外部填写页（新增，**只有三项**） | 分组 `["送检信息"]`；字段 `["* 选择样本","样本类型","类器官来源类型"]`；提交按钮 1（没有石蜡块编号 / 工序 / 染色 / marker / 包埋人 / 操作人 / 备注） |
| `10-ext-embed-sample-picker.png` | 外部「选择样本」（本人送检过、没判无效） | 候选 `["SJ90000002\n测** · 胆管组织","SJ90000001\n测** · 肝组织"]`（送检单号 + 掩码供体姓名；extA 自己那条**无效**的 1003 不在候选里） |
| `11-ext-embed-form-invalid.png` | 外部填写页（**无效重提**） | 实验室判无效（真接口 `PUT /lqg/embed/9000002006/verify`）后：红条 `["无效 · 信息不全：样本类型与送检单不符"]`、提交按钮 1（可改后重提） |
| `12-ext-embed-form-valid.png` | 外部填写页（**有效只读**） | 样本核验有效 + 送样判有效（`T-E06-1`）后：**提交按钮 0**、包埋卡片 1 张、卡片抬头 `["T-E06-1"]`、字段仍是三项 |

> 造数据走真接口（脚本里 `POST/PUT` 都用真 token 打 8081；`/mp/me` 用 Playwright 打桩让身份切换可控），**不直连库、不打桩业务接口**；
> 脚本首尾各 `reseed.sh --yes` 一次（`shots-transcript.txt` 里可见两次）。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

三条 runner 在 `doc/waves/reports/EMBED-MP-001/accept-runners/`，完整输出在 `accept-transcript.txt`。
长链**包进函数判整条 rc**（`set -e` 不管非末尾位置的失败，AUTH-STAFF-001 坑 3）。

### 4.0 关于 `--fresh-module` 与 `npm_config_store_dir`（既有 WARN，本沙箱）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` → 原样带 `--fresh-module ruoyi-lqg` **恒 exit 2**。
按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边：

```
jar mtime        : 2026-09-22 09:49:11（epoch 1790041751）
lqg src newer    : 0      ← find ruoyi-modules/ruoyi-lqg/src -newer <jar> 为空
admin src newer  : 0      ← 同上（ruoyi-admin/src 一个字节没改）
8081 PID         : 77121  ← lsof -ti tcp:8081 -sTCP:LISTEN
该 PID 打开的文件 : 含 ruoyi-admin/target/ruoyi-admin.jar（lsof -p <PID> | grep -c = 2）
进程启动时刻      : 1790041777.6 ≥ jar 1790041751 → 起于打包之后（psutil，不用被禁的 ps）
后端日志 mtime    : 2026-09-22 10:01:45（晚于 jar）
嵌套 jar 复核     : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含
                   org/dromara/lqg/embed/mp/{MpEmbedController,MpEmbedQueryBo,MpEmbedService}.class
                   + 改动后的 embed/service/EmbedQueryService.class 与 embed/domain/bo/EmbedQueryBo
8080（Kevin）     : 全程没碰
```

`pnpm` 前加 `npm_config_store_dir=<ws>/.pnpm-store`（本机 pnpm 全局缓存在沙箱里只读，SYS-MP-001 坑 4）。两个差异都写在三条 runner 的注释里。

### 4.1 accept 1 · API + STATE —— ✅

```
########## EMBED-MP-001 · accept 1（API + STATE）##########
（差异：去掉 --fresh-module；pnpm 前加 npm_config_store_dir；其余字面逐字）
true          ← 构建产物 pages/embed/form.js + pages/ledger/index.js 都在；7 段 grep 全过；vitest 19 用例（numFailedTests==0）
true          ← /mp/int/embed/list?pageSize=100 → 五条（2005 软删不在）
true          ← extA 打 /mp/int/embed/list → 403
true          ← sort=recent：五条全在、2006 打头、handlerName 无 null、2001→["李工",true]、2004→["测试管理员",false]
true          ← sort=recent&mine=true → ["9000002001","9000002003"]
9000002001    ← 直连库独立数（create_by/update_by=9000000101 且 del_flag='0'）
9000002003
ACCEPT-1 EXIT=0
```

**counterfeit 逐条排掉**：

- `/mp/int/embed` 漏了角色注解 → 第 3 段红（外部拿到全部石蜡块连同包埋人）。实测 `403 没有访问权限，请联系管理员授权`。
- 小程序另写一套不互斥的切换逻辑 → fixture 段红。实测 `numPassedTests:19`（9 条 fixture 逐例）。
- 石蜡包埋做成单独的卡片列表页、没注册进表格页 / 历史页签 → `grep embed` 两段红。实测 `sheets.ts` 4 处命中、`sources.ts` 12 处。
- 历史页签照旧只给本人（URL 里写死 `mine=true`）→ `! grep` 那段红；接口侧 `sort=recent` 会少 2002/2004/2006。实测两段都过。
- 拿 `mine=true` 兼当排序（不实现 `sort=recent`）→ sources.ts 的 `sort: 'recent'` 那段与 `2006 打头` 那段红。
- `handlerName` 取包埋人 `embed_by` 而不是新增/最后修改人 → 「李工」「测试管理员」两段红（2002 的 `embed_by` 是 null、2004 的 `embed_by` 也是 null，两种写法立刻分家）。
- `mine` 按 `embed_by` 姓名比对 → 2004 的 `mine` 红。
- `mine` 不看 `del_flag` → 软删的 T-E05-X（李工建的 2005）混进来 → `mine=true` 集合变 `{2001,2003,2005}` 红；两侧不同源（接口 vs 直连库 `db.py --col-set`）实测同集合。

### 4.2 accept 2 · STATE —— ✅

```
########## EMBED-MP-001 · accept 2（STATE）##########
（差异：去掉两处 --fresh-module；其余字面逐字）
9000000100    ← 2002 是管理员录的（create_by=9000000100）
true          ← 李工 PUT 2002（别人的行）→ 200
2|2026-09-16|李工  ← 库里仍只有 2 块（**没新增行**）、脱水时间与包埋人改到了
yes           ← update_by 记了人
true          ← sort=recent&mine=true：2002 排第 1、handlerName=李工、mine=true
T-E01-2       ← 改编号撞 2001 → 400/500，库里仍是 T-E01-2
pending|-|组织  ← 待核验的 2006 改不动 → 400/500，库里一字不变
ACCEPT-2 EXIT=0
```

**counterfeit 逐条排掉**：

- 保存按钮永远走 POST → 石蜡块数变 3（第 2 段红），而且第二次会因编号重复被拒、用户再也补填不了。实测 `2|…`。
- 后端把「只能改自己录的」当规则拦了 → 第一段 PUT 就红（2002 是管理员录的）。实测 `200`。
- 更新没记 `update_by` → `no` 那段红、历史也排不到最前。实测 `yes` + 第 1 名。
- `handlerName` 不看 `update_by` → 改完还显示「测试管理员」红。实测 `李工`。
- 改编号撞到别的石蜡块没拦 → 撞号那段红。实测 `^(400|500)` 命中且库里仍是 `T-E01-2`。
- `/mp/int/embed` 的 PUT 不看状态 → 待核验的外部送样被填上编号。实测 `pending|-|组织`（**每次被拒库里都不变**）。

### 4.3 accept 3 · STATE —— ✅

```
########## EMBED-MP-001 · accept 3（STATE）##########
（差异：pnpm 前加 npm_config_store_dir；其余字面逐字）
true          ← fixture 路径 grep + 无 .skip/.todo/.only + vitest 17 用例 + node 结构断言 + 3 段 grep
grep: src/pages/embed/SamplePickerExt.vue: No such file or directory   ← ★ 见 §5.2（文件在 src/components/lqg/）
ACCEPT-3 EXIT=0
```

**★ 最后一段（`! grep -nE 'internalNo' src/pages/embed/SamplePickerExt.vue`）在本票落地后是「文件不在该路径 → grep rc=2 → `!` → 真」**：组件按 §2.3 的理由放在 `src/components/lqg/`。**我没有利用这个洞**：等价的（更强的）断言落在 `src/pages/embed/layout.fixture.spec.ts` 的最后一个用例 —— 它按**页面真实 import 的路径** `readFileSync` 读文件（文件不存在 → 用例直接抛错变红），断 `不匹配 /internalNo/`，并反向断它确实读了 `fetchExtMySamples` / `donorNameMasked`（不是空壳）。实测 vitest 全绿。

**counterfeit 逐条排掉**：

- 外部复用内部全字段表单再隐藏 → 「外部新增」用例的 `fields` 会带 `paraffinBlockNo` → 红。本票外部的 `fields` 恰为三项。
- 外部 `editable` 漏了 `mine` → 「外部看同组别人的待核验」红。
- 内部看外部送来的待核验可改 → 「内部看外部送来的待核验」红；把 `mode=view` 做成可编辑 → 「内部管理只读查看」红。
- 只读详情上没有「修改」入口 / 写死不按身份与状态 → `showEditEntry` 那两段 grep + spec 逐例红。
- 外部选样本的下拉里显示内部编号 → 上面那道 fs 守卫红。
- 删病灶用例来过关 → `layout.fixture.spec.ts` 的结构守卫（9 / 3 / 16 / 5）与 `internalFields` 逐字相等红。

### 4.4 追加证据（accept 之外的机器证据）

**(a) 后端全模块测试：`Tests run: 120, Failures: 0, Errors: 0, Skipped: 0`（本票前 112）**

```
MpEmbedContractTest              Tests run:  7   ← 本票新增
EmbedQueryContractTest           Tests run:  5   ← 本票 +1（keyword 的一组 OR）
ExtChokepointContractTest        Tests run:  4   ← ADR-0004 四条不变量（本票没碰 ext 包）
EmbedRulesContractTest           Tests run:  8
EmbedChildrenCheckerContractTest Tests run:  4
EmbedShapeContractTest           Tests run:  6
EmbedExportContractTest          Tests run:  5
（其余 SAMPLE / AUTH / HINT 域用例照旧）
Tests run: 120, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```

**(b) 小程序 vitest：`115 passed`（本票 +36；SAMPLE-MP-001 的 22、SAMPLE-MP-002 的 20、SYS-MP-001 的 25、SAMPLE-HINT-001 的 8 一字未改、继续绿）**

```
src/pages/embed/stain.fixture.spec.ts   19
src/pages/embed/layout.fixture.spec.ts  17
src/pages/ledger/sheets.spec.ts          8
src/pages/ledger/columns.fixture.spec.ts 7
src/pages/sample/layout.fixture.spec.ts 22
src/pages/organoid/layout.fixture.spec.ts 13 + api.spec.ts 4
src/pages/index/entries.fixture.spec.ts 25
Test Files 8 passed (8)   Tests 115 passed (115)
```

**(c) 方向 A 的机器自检**（与 SYS-MP-001 第 3 条 accept 同形）

```
$ grep -rnE '(color|background|border|fill|stroke|shadow)[^;{]*#[0-9a-fA-F]{3,8}\b' src/pages src/components --include=*.vue --include=*.scss
（无命中 —— 本票新增的 9 个文件一个色值字面量都没有）
$ grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.vue --include=*.scss | grep -v '^src/uni.scss'
（无命中 —— 新 .vue 里没有 JSDoc ` * ` 注释块，也没有 `a * b`；算术与模板字符串都在 .ts 里）
```

**(d) 回归自查（串行跑，完整摘要见 `regression-summary.txt`）**

| 回归 | 结果 |
|---|---|
| `EMBED-MODEL-001` accept 1/2/3/4 | **全 `EXIT=0`** |
| `EMBED-WEB-001` accept 1（导出）/ 2（菜单） | **全 `EXIT=0`** |
| `AUTH-EXT-002` accept 1/2/3 | **全 `EXIT=0`**（★ accept 1 包含「外部打 `/mp/int/embed`」那一段的收口，见下） |
| `SAMPLE-MP-001` accept 1 / accept 2 build / accept 2 api | **全 `EXIT=0`**；`{"numTotalTests":22,"numPassedTests":22}` |
| `SAMPLE-MP-002` accept 1/2/3 | **全 `EXIT=0`**（含「页面里不许写列名 / 没有新增与保存 / `mode=view` 有而 `mode=edit` 无」那几段禁字 grep） |
| `SAMPLE-HINT-001` accept 1/2 | **全 `EXIT=0`** |
| `ExtChokepointContractTest` | `Tests run: 4, Failures: 0`（在 (a) 的全模块测试里） |
| `bash doc/waves/regression/D1/verify.sh --skip-build` | **41 绿 / 1 红 / 0 环境错** —— 唯一那条红是**既有假红**：`L1.1 D1 的 7 支迁移全部记录在案 → [FAIL] 期望 '7'，实际 '14'`（issue 已记，state.json；本票**不重复计数**） |
| ★ AUTH-EXT-002 遗留的 404 → **403**（报告 WARN-5 / 探针 P5） | `bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/embed/list'` → **`403 没有访问权限，请联系管理员授权`**（不再是 `404 No endpoint`） |

> 回归片段是**串行**跑的（每个 runner 首尾自己 reseed；EMBED-MODEL-001 §4.0 记过并行会互相打断）。

---

## §5 遗留与 raise

### 5.1 越出 `touches` 的改动（逐条列清）

| # | 文件 | 为什么必须改 | 风险 |
|---|---|---|---|
| 1 | `embed/domain/bo/EmbedQueryBo.java`、`embed/service/EmbedQueryService.java`、`src/test/java/.../embed/service/EmbedQueryContractTest.java` | UI:mp.embed.list 的筛选是「搜索（石蜡块编号 / **内部编号**）+ 核验状态 + 染色」。后端原来只有 `paraffinBlockNo`（模糊）与 `internalNo`（等值）两个**独立且相与**的参数，**没有任何组合能表达「二者命中其一」** —— 一个搜索框只能靠读路径给这个 OR。与 SAMPLE 域 `SampleQueryBo.keyword`（SAMPLE-MP-002 落的）**逐字同形**，且只挂在唯一那条读路径上 | 低：`buildWrapper` 的 3 参版本原样保留给既有调用方（工作台导出 + 契约测试），4 个既有用例**一字未改**继续绿；`keyword` 是可选参数，工作台不发 → 行为一字不变（新增的第 5 个用例专门断「不带 keyword 时一个字都不多」） |
| 2 | `code/miniapp/src/pages/ledger/index.vue` | 石蜡包埋这张表有**两个**筛选组（核验状态 + 染色），而页面原先只渲染 `sheet.filters[0]`。不改页面就有一档筛选点不出来 | 低：改动是「把写死的第一组换成遍历全部组」，样本两张表仍只有一组（`SAMPLE-MP-002` accept 2 的两段禁字 grep 与 `mode=view`/`mode=edit` 两段 grep 复跑仍绿 —— 所以搜索框占位是**从 `sheets.ts` 读**的，没往页面里写列名） |
| 3 | `code/miniapp/src/api/ledger.ts` | `LedgerFilters` 是筛选条件的家（`emptyFilters()` 也在这里）；染色这一档必须进这个结构，否则页面与 `sheets.ts` 各拿一份筛选状态 | 极低：加一个字段 + 一个默认值；`fetchSampleLedgerRows` 不读它（两张样本表没有染色筛选），样本接口的请求一字不变 |
| 4 | `code/miniapp/src/types/components.d.ts` | uni-components 插件写的**生成产物**：4 个新组件的 easycom 注册 | 与 SYS-MP-001 / AUTH-GROUP-001 / AUTH-EXT-002 的同一处理，**一并提交** |

**没有动**：`src/pages.json` / `src/types/uni-pages.d.ts`（见 §8）、`doc/**` 只读区、`code/plus-ui`、`ruoyi-admin` 源码、Flyway、`application*.yml`。

### 5.2 ★ 组件位置：`src/components/lqg/` vs ticket 字面的 `src/pages/embed/`

ticket §2 写「组件 `SamplePickerExt.vue`」（在 `touches` 的 `src/pages/embed/**` 里）。实测把 4 个 `.vue` 放在 `src/pages/embed/` 下时，`@uni-helper/vite-plugin-uni-pages` 会把它们**当成页面**写进 `pages.json`：

```
+    { "path": "pages/embed/MarkerRows", "type": "page" },
+    { "path": "pages/embed/SamplePicker", "type": "page" },
+    { "path": "pages/embed/SamplePickerExt", "type": "page" },
+    { "path": "pages/embed/StainButtons", "type": "page" },
```
（`types/uni-pages.d.ts` 同步多 4 条路由。）这是**真缺陷**（4 条垃圾路由进产物、以后谁断「路由集合」都会踩），所以我按项目既有约定把它们移到所有公共组件的家 `src/components/lqg/`（AUTH-EXT-002 的 `EmbedCard.vue` 就在那儿），`pages.json` / `uni-pages.d.ts` 因此**回到 HEAD 零 diff**。
**代价（如实报）**：accept 3 最后一段 grep 的路径 `src/pages/embed/SamplePickerExt.vue` 不再存在 → 那一段恒真（§4.3 已写明），我用 `layout.fixture.spec.ts` 里一条**按真实路径读文件**的等价（更强）断言补上。若主会话更想要「路径与 ticket 字面一致」，两条路：① 把 4 个文件挪回 `src/pages/embed/` 并在 `pages.config.ts` 加 4 条 `exclude`（要连默认的 `node_modules/.git/**/__*__/**` 一起重写，属配置层改动）；② 或接受 4 条垃圾路由。

### 5.3 与 `doc/api-contract.md` 的差异

| 契约行 | 实现 | 判断 |
|---|---|---|
| 第 63 行 `GET /mp/int/embed/list`、`GET/POST/PUT /mp/int/embed`「同形状」 | 逐字实现（同一份 `EmbedQueryBo` / `EmbedVo` / `EmbedSubmitBo` / `EmbedService`）；`sort=recent` 默认全中心、`mine=true` 只在开关打开时带、行带 `handlerName`/`mine` | ✅ |
| 第 61 行 `/lqg/embed/list` 的筛选（`verifyStatus` / `submitSource` 等） | 未改；**多认一个可选 `keyword`**（本票为小程序搜索框加的，落在父 BO 上） | ⚠️ doc-drift，见 WARN-4 |
| 第 63 行没有列 `keyword` / `stain` | 实现认这两个（`stain` 是 EMBED-MODEL-001 本来就有的 `EmbedQueryBo.stain`，契约第 61 行也没列，但工作台在用它） | ⚠️ 同上 |

### 5.4 没把握 / 需要确认的口径

1. **EMBED 的 `sort=recent` 不带范围收窄**（与 SAMPLE 域不同）→ 历史页签里会出现**外部账号建的**待核验送样（经手人显示「王医生」）。本票 accept 1 明文要这五条全在，所以按 accept 实现；但 CR-20260918-07 的原话是「中心全部**内部人员**」的记录 —— 两条权威有张力，记 WARN-1。
2. **外部 PUT 是「覆盖两个字段」**（AUTH-EXT-002 WARN-3）：小程序外部提交体两个字段都带，**空着会把库里清成 null**。外部表单只强制「选择样本」，没强制这两项（UI 也没写必填）。
3. **小程序清不掉已填的工序时间**（后端 patch 语义：不传 = 不动；小程序把空日期序列化成 `null` = 不传）—— 同 SAMPLE-MP-001 的边界（误清一个字段的代价 > 清不掉一个字段）。
4. **只读页的「修改」在内容区第一行右端**，不是原生导航栏右上角（mp-weixin 原生导航栏放不了自定义按钮）—— 同 SAMPLE-MP-002 WARN-8。
5. **`keyword` 的内部编号半边是等值匹配**（与 SAMPLE 域同口径）→ 敲一半搜不到（`keyword=T-E01` 命中，`keyword=T-E` 只按石蜡块编号模糊命中）。

---

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | cross-domain | **EMBED 域的 `sort=recent` 只排序、不做「经手人必须是内部账号」的范围收窄**（SAMPLE 域做了）→ 石蜡包埋历史页签里会出现 extA 建的待核验送样，经手人显示外部姓名「王医生」 | 口径张力：CR-20260918-07 说「中心全部**内部人员**的记录」，而本票 accept 1 明文要求五条全在（含 2006，`handlerName` 不许为 null）。本票按 accept 实现（`EmbedQueryService.buildWrapper` 只按 `COALESCE(update_time, create_time)` 倒序）。方案：把 SAMPLE 域那段「`create_by/update_by IN (SELECT user_id FROM sys_user WHERE user_type='sys_user')`」搬到 EMBED 域即可 —— 但那会**打红本票 accept 1**，必须先改 ticket。 |
| WARN-2 | **S2** | cross-ticket | **SAMPLE-MP-002 报告 §3 的端侧第 `04` 屏（`?sheet=embed` → 落到第一个已注册的工作表）在本票后不再成立**：embed 已注册，`?sheet=embed` 现在真的进石蜡包埋表 | 它的 accept 不测这一屏（两段 grep 仍绿），坏的是**报告里的证据表**。方案：D2 QA 复跑时把该屏的 URL 换成 `?sheet=cryo`（仍未注册 → 回落行为仍可证）；或在 SAMPLE-MP-002 报告里加一行「embed 由 EMBED-MP-001 注册」。 |
| WARN-3 | S2 | counterfeit-risk | **`src/pages/**` 下任何 `.vue` 都会被 uni-pages 注册成页面路由**（本票实测多出 4 条垃圾路由，`pages.json` + `uni-pages.d.ts` 一起脏） | 影响所有后续 MP 票：把组件写进 `src/pages/<域>/` 是**看起来最自然**的做法（ticket 也这么写），但产物里会多出页。方案：① 栈包 gotchas 补一行「页面目录下只放页面，组件一律 `src/components/lqg/`」；② 或 ticket 生成器把「组件」写清路径。本票按 ① 处理（组件放 components，报告 §5.2 记了代价）。 |
| WARN-4 | S3 | doc-drift | **`keyword` 落在父 BO `EmbedQueryBo` 上**，因此 `/lqg/embed/list`（工作台）也多认一个可选参数（契约第 61 行没列它） | 工作台不发这个参数，行为一字不变（`EmbedQueryContractTest` 新用例断「不带 keyword 时一个字都不多」）。选父类的理由与 SAMPLE-MP-002 WARN-7 逐字相同：判据只有 `EmbedQueryService.list` 一条路。方案：契约第 61 / 63 行补一句 `keyword`（石蜡块编号模糊 OR 所挂样本内部编号等值）。 |
| WARN-5 | S3 | debt | **`columns.ts` 里 embed 那张表的「样本编号」列 key 是 `sampleSubmitNo`，而 `EmbedVo` 上这一格的实际字段是 `internalNo`**（`submitNo` 是送检单号，另一回事） | 本票在 `api/embed.ts` 的 `KEY_ALIAS` 里对齐（列名 / 列序一字未改，`columns.ts` 不在本票 `touches`）。影响：下一个照 `columns.ts` 的 key 直接取数的人会拿到 `—`。方案：把 `columns.ts` 里那一处 key 改成 `internalNo`（一行；label 不变，fixture / spec 断的是 label，不受影响）。 |
| WARN-6 | S3 | clarify | **外部 `PUT /mp/ext/embed/{id}` 是覆盖两字段**（AUTH-EXT-002 WARN-3）→ 小程序外部提交体两个字段都带，**空着会清空库里值**；而内部是 patch（空 = 不动） | 同一张表单两套字段语义。本票在 `externalEmbedPayload` / `internalEmbedPatch` 的 javadoc 里写明。方案：契约第 63 行补一句外部 PUT 的字段语义（覆盖 vs patch）。 |
| WARN-7 | S3 | clarify | **小程序清不掉已填的工序时间**（内部 patch：不传 = 不动；空日期序列化成 `null` = 不传） | 与 SAMPLE-MP-001 §8.6 同源。方案：若甲方要能清，加显式「清空」哨兵值（属后续 CR）。 |
| WARN-8 | S3 | clarify | **`keyword` 的内部编号半边是等值匹配** → 敲一半搜不到（`T-E01` 命中、`T-E` 只按石蜡块编号模糊命中） | 与 SAMPLE-MP-002 WARN-6 同源。方案：确认甲方期望；要「敲一半就能搜」把 `sampleIdsOfInternalNo` 的 `eq` 改 `like`（内部编号不是加密列，不违反 ADR-0006）。 |
| WARN-9 | S3 | debt | **只读页的「修改」在内容区第一行右端**，不是原生导航栏右上角 | 同 SAMPLE-MP-002 WARN-8（mp-weixin 原生导航栏不支持自定义按钮；要严格实现需 `navigationStyle: 'custom'` + 自绘导航栏）。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 沙箱恒 exit 2 + Maven 三参数 + `pnpm` 需 `npm_config_store_dir`、`api.sh` token 缓存只看 mtime、`db.py` 是只读执行器却 exit 0、`reseed.sh` 清不掉运行时账号（本票收尾跑 `clean-orphan-accounts.sh`）、D1 回归包 flyway 总数写死 7（本票推到 14）、`updateById` 忽略 null、`PageQuery` 只有两参构造、MyBatis-Plus 无「按表达式排序」重载、微信开发者工具在沙箱跑不通、`sys_config` 改库后要 `refreshCache`（本票没碰参数）。

---

## §7 坑与解法（给下游，3-5 行）

1. ★★ **`src/pages/**` 下的 `.vue` 会被 uni-pages 当成页面注册成路由**：本票第一版把 4 个组件放在 `src/pages/embed/`，`pnpm build:mp-weixin` **照样 `Build complete`**，但 `pages.json` + `types/uni-pages.d.ts` 里多出 4 条 `"type": "page"` 的垃圾路由（`MarkerRows` / `SamplePicker` / `SamplePickerExt` / `StainButtons`）。**页面目录下只放页面，组件一律 `src/components/lqg/`**；改完 `pages.json` 自动回到 HEAD 零 diff（见 WARN-3 / §5.2）。
2. ★ **`readonly` 与否决定 `FieldRow` 用哪个分支**：只读走 `wd-cell`（标签在 `.wd-cell__title`、值在 `.fr__text`），可写走 `wd-input`（标签在 `.wd-input__label`）。写端侧断言时**两种都要取**，只断 `.wd-cell__title` 会在「新增 / 修改」屏上拿到空数组（本票第一版就是这样假空）。
3. ★ **`updateTime == null` = 从没改过**（D2 起的跨票语义）：`2001/2002/2003/2004` 在 seed 里都是「没改过」→ 修改模式**不显示**「最后修改」那一行、历史行显示「新增」。端侧要看到那一行，先用真接口补填一次（本票 `04` 屏就是这么做的）。
4. ★ **一组 OR 必须包成 `and(w -> …)`**：`keyword` 的「石蜡块编号模糊 OR 所辖样本内部编号等值」与 `mine` 的「create_by OR update_by」都走嵌套；顶层裸 `.or()` 会把 `(A AND B) OR C` 写进 SQL（D2 的 S1 #105，当时就是让历史页签串台）。新加的用例断**括号**（`(paraffin_block_no LIKE ? OR sample_id IN (?)`）。
5. ★ **accept 里 `(?sheet=embed) → 落到第一个已注册` 那种「未注册」证据会随注册票过期**：本票注册 embed 之后，SAMPLE-MP-002 报告里那一屏的证据失效（accept 不测它、所以不会红）。做 MP 票时顺手核一遍**上游报告里的端侧证据**有没有被自己改掉（见 WARN-2）。
6. **补填 = 修改，`EmbedService.update` 已经同时具备 patch 语义与状态闸**：`/mp/int/embed` 的 PUT **不要**再包一层 mergePatch（样本域需要是因为 `SampleService.update` 是整体替换；embed 域不是）。多包一层就多一份会漂移的判据。

---

## §8 收尾（长进程 / 端口 / DB / 生成产物）

- **后端 8081**：收尾时按 PID 关掉（`lsof -ti tcp:8081 -sTCP:LISTEN` → `kill`；**没用过 `pkill -f`**）。
- **H5 dev server 9200**：收尾时按 PID 关掉。
- **8080（Kevin）**：全程没碰。**未占** 8082 / 8083 / 8099 / 9201。
- **5433（`lqg-dev-postgres`）/ 6380（`lqg-dev-redis`）/ 9002-9003（`lqg-dev-minio`）**：全程在跑，**没停**（留给后续 ticket）。
- **DB 收尾**：`bash doc/verify/reseed.sh --yes` + `bash doc/waves/tools/clean-orphan-accounts.sh --yes`（截图脚本造的 `T-hli77`、`T-E06-1` 那几条由 reseed 收回）。
- **生成产物**：`src/pages.json` / `src/types/uni-pages.d.ts` **零 diff**（组件不在 `pages/` 下）；`src/types/components.d.ts` 多 4 行（本票 4 个新组件，**一并提交**）；`code/plus-ui/.eslintrc-auto-import.json` 被 dev / 插件改过（与本票无关）→ `git checkout --` 还原。

## §9 提交清单

```
A  code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/embed/mp/**   （3 个类）
A  code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/embed/mp/MpEmbedContractTest.java
M  .../embed/domain/bo/EmbedQueryBo.java                       （越出 touches，§5.1-1）
M  .../embed/service/EmbedQueryService.java                    （越出 touches，§5.1-1）
M  .../src/test/java/.../embed/service/EmbedQueryContractTest.java （越出 touches，§5.1-1）
A  code/miniapp/src/api/embed.ts
A  code/miniapp/src/pages/embed/{layout.ts,layout.fixture.spec.ts,stain.ts,stain.fixture.spec.ts}
M  code/miniapp/src/pages/embed/form.vue                        （占位页 → 正文）
A  code/miniapp/src/components/lqg/{SamplePicker.vue,SamplePickerExt.vue,StainButtons.vue,MarkerRows.vue}
M  code/miniapp/src/pages/ledger/sheets.ts
M  code/miniapp/src/pages/ledger/index.vue                      （越出 touches，§5.1-2）
M  code/miniapp/src/api/ledger.ts                               （越出 touches，§5.1-3）
M  code/miniapp/src/pages/history/sources.ts
M  code/miniapp/src/pages/sample/form.vue
M  code/miniapp/src/types/components.d.ts                       （生成产物，§5.1-4）
A  code/miniapp/scripts/shots-embed-mp001.mjs
A  doc/waves/reports/EMBED-MP-001/**                            （报告 + 12 张截图 + 3 份 runner + 两份 transcript）
```
