# SAMPLE-MP-002 · 完工报告

- **ticket**：SAMPLE-MP-002（track SAMPLE / phase D2 / size L）—— D2 最后一张：内部管理表格页 + 类器官收样填写页
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑，输出见 §4 与 `accept-transcript.txt`）
  - accept 1 · DATA：✅（**两处**差异，都能单独复现：去掉本沙箱恒 exit 2 的 `--fresh-module`；第 5 段去掉 `db.py --quiet` —— 见 §4.0）
  - accept 2 · DATA：✅（构建 2 个产物断言 + 7 段 grep + 甲方四份 xlsx 原件逐字 diff + fixture 结构 jq，全过）
  - accept 3 · STATE：✅（vitest 13 用例全绿 + fixture 结构 node 断言 + 两段 grep）
- **分支**：`task/D2`，未切分支 / 未 push / 未 merge
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 00:09:37`（epoch 1790006977），后端进程 8081
- **迁移**：**没有加 Flyway**（小程序接口无菜单、无新表；最大版本仍是 `V202609221010`）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,xlsx_header.py}`、`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/api-contract.md`
- **没碰**：8080（Kevin）/ 5432 / 6379；关进程一律按 PID（`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`），**没用过 `pkill -f`**
- **产物**：小程序 6 个新页面/模块 + 5 个既有文件小改 + 1 个截图脚本；后端 1 个新字段 + 1 段判据（3 个既有文件）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D2`（未切分支） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `SAMPLE-MP-001` / `AUTH-EXT-001` 的报告都在 `doc/waves/reports/`；`/mp/int/sample`（4 端点）、`/mp/ext/organoid`（POST/PUT）、`src/pages/index/entries.ts`、`src/api/sample.ts`、`history/sources.ts` 全部在盘并实跑通（§4.3 的 15 段里有 12 段直接打它们） |
| 7 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:mp.ledger` / `UI:mp.sample.list` / `UI:mp.organoid.form` / `FLOW:F-MP-01.step3` / `FLOW:F-SAMPLE-02.step2,step6` / `FIELD:t_lqg_sample.organoid_type` 全部 `active`；口径落点见 §1 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260918-07**（表格页点一行 → 只读详情 + 详情右上角「修改」；页底小字去掉「修改」二字）与 **CR-20260917-05**（内部管理只读、外部可填类器官、历史编辑记录取代「我的送检」）**逐条落进实现**，见 §1；本票命中 CR-20260918-07 的三处（只读页修改入口 / 小字 / 历史记录范围） |
| 逐字读两份 fixture（一个字节没改） | ✅ PASS | `ledger-columns-cases.json` / `organoid-form-cases.json` 的期望值全部由 spec 读；`git status` 里两份都不出现 |
| 视觉按方向 A（CR-20260921-08） | ✅ PASS | 只用 `src/style/components.scss` 的 `.lqg-*` 类（`lqg-sheets` / `lqg-filter` / `lqg-count` / `lqg-ledger*` / `lqg-gl` / `lqg-bar` / `lqg-note`）；`src/pages` `src/components` 里零色值字面量（`grep -rnE '(color\|background\|border\|fill\|stroke\|shadow)[^;{]*#[0-9a-fA-F]{3,8}\b' src/pages src/components --include=*.vue --include=*.scss` 只命中 `pages.json` 那个 route 块；见 §4.4(c)）；无 `:not(` / 裸 `*` 选择器 |
| 动手前代码是绿的 | ✅ PASS | 改动前后端 `Tests run: 55`、小程序 vitest 47；本票后 `Tests run: 55`（0 失败）、vitest **67**（0 失败） |
| 环境可用（8081 / PG 5433 / Redis 6380） | ✅ PASS | 三个容器全程在跑，未停；8081 用 `.tmp/run-backend.sh` 起（受管后台 job，**没用 `nohup &`**） |
| 顺带复跑上一张的端侧证据（跨票行为变更自检） | ⚠️ WARN-4 | 见 §6 WARN-4：给 `pages/sample/form.vue` 的只读页补了「修改」入口（CR-20260918-07 覆盖四张表），SAMPLE-MP-001 的 `11-int-form-view` 那一屏会多出一个「修改」文本 |

**STOP 判定：无。**

---

## §1 口径复述（ticket §0 点名的 5 点 + 本票最容易做反的 3 件事）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **列名、列序只有一个来源** | `pages/ledger/columns.ts` 是**代码侧唯一**一份列清单；`index.vue` / `LedgerTable.vue` 一个列名都不写（表头文案全是 `columnsOf(sheet)` 给的） | accept 2 的两段禁字 grep（`来源单位\|供体姓名\|冻存样品\|石蜡块编号` 在这两个文件里 0 命中）+ spec 逐表断 `frozen.label` 与 `columns.map(label)` 深相等 fixture + `xlsx_header.py --print-header` 与四份原件逐字 diff |
| 2 | **第一列冻结、其余横滑** | `components/lqg/LedgerTable.vue`：`scroll-view scroll-x` + 显式算宽（`118 + 列数×120`），首列走 `.lqg-ledger__fz`（`position: sticky; left: 0`） | 端侧实测（§3 `01/02`）：`scrollLeft 1408 / scrollWidth 1798 / clientWidth 390`；冻结列计算样式 `{position:"sticky", left:"0px"}` |
| 3 | **表格本身只读**：没有「＋ 一行」、没有行内编辑、没有状态流转 | `index.vue` 只有切换条 + 筛选行 + 计数行 + 表格 +「导出 Excel」（置灰）；点一行 → `sheets.ts` 的 `target()` → `…&mode=view` | accept 2：`新增\|保存\|核验并\|判为` 两文件 0 命中、`grep -q 'mode=view'` 命中、`! grep -q 'mode=edit'` 成立；端侧 `05`（提交按钮 0、修改入口 1）、`06`（点修改后提交按钮 1） |
| 4 | **「修改」在只读页右上角，且与「能不能改」同源** | `sample/form.vue` 与 `organoid/form.vue`：`canEditFromView = mode==='view' && 纯函数(…,'edit').editable` | fixture「内部从内部管理查看」`editable=false`；端侧 `10`（内部看外部待核验：修改入口 **0**、保存按钮 0）；`07`（外部待核验的样本记录：修改入口 **0**） |
| 5 | **外部只在类器官收样页渲染三项，收样段不渲染** | `organoid/layout.ts` 给外部**只返回三项**；`hasReceiveGroup()` 为 false → 收样信息整组连标题都不出 | fixture 9 例逐例 + spec 的「外部任何一例都不含收样段字段」守卫；端侧 `12`（外部新增：分组只有 `["送检信息"]`、收样信息组数 0） |
| 6 | **内部七项、模板 B 没有备注** | `INTERNAL_FIELDS` 7 项，与 fixture / `FIELD:t_lqg_sample.organoid_type` 同源；`remark` 只在外部三项里 | fixture `internalFields.length === 7`；端侧 `11`（内部新增分组 `["送检信息","收样信息"]`、`备注` 字段数 **0**） |
| 7 | **内部录入落成 `internal|valid` 且单位名是快照** | 表单同时带 `sourceUnitId` 与 `sourceUnitName`；后端 `SampleService.resolveUnitName` 按 id 取单位表当前名 | accept 1 第 4 段：`organoid\|internal\|valid\|B 大学\|Y`；第 7 段 `mine=true` 顺序 `["T-oco55","T-oco01"]` |
| 8 | **外部历史只列可见集合** | 外部 `sampleKind=organoid&onlyMine=`（可见集合 = 本人 + 同组已核验）；内部 `sort=recent` + 开关打开才 `mine=true` | 端侧 `14`（extC 只看到自己那条 `SJ00000014`）、`15`（extA 行 `[]` + 空态）；accept 1 第 11 段 `extA → .rows==[]` |

**CR 覆盖 ticket 正文的两处（以 CR 为准）**：

1. ticket §2 写「内部历史 `GET /mp/int/sample/list?sampleKind=organoid&mine=true`」；**CR-20260918-07** 定的是「内部默认**中心全部内部人员**经手过的 + 顶部『只看我提交的』开关」。本票按 CR 实现（与 SAMPLE-MP-001 的样本页签**同一形状**：`sort=recent`，开关打开才另带 `mine=true`），并把这一条记进 WARN-3。
2. ticket §0 的小字逐字 =「核验、冻存取用请到网页工作台」（CR-20260918-07 去掉「修改」）—— 已逐字落进 `pages/ledger/index.vue`（accept 2 的两段 grep 断它）。

---

## §2 改了哪些文件

### 2.1 小程序 · 新增

| 文件 | 行 | 职责 |
|---|---|---|
| `src/pages/ledger/columns.ts` | 122 | **列清单唯一来源**：`ledgerColumns(sheet)` → `{frozen, columns}`，四张表一次定完（`tissue` / `organoid` / `embed` / `cryo`），冻结列从模板列里去掉、追加列在最后 |
| `src/pages/ledger/columns.fixture.spec.ts` | 60 | fixture 驱动 **7 用例**：结构守卫 + `expect == template−frozen+extra` 同源守卫 + 四张表逐表标签相等 + 冻结列唯一性 + 不认识的 sheet → null |
| `src/pages/ledger/sheets.ts` | 138 | **工作表注册表**（key / 短名 / 全称 / 取数函数 / 筛选项 / 冻结格第二行 / 行底色 / 点行动作）。只注册 `tissue`、`organoid`；`target()` 只有 `mode=view`；`toTableRows()` 把行拍成哑组件要的纯数据矩阵、`ledgerTableWidth()` 算整表宽度 |
| `src/pages/ledger/index.vue` | 284 | 表格页：切换条（只列已注册的）+ 搜索框 + 核验状态 chips + 计数行「共 N 条 · 左右滑动看全部 M 列」+ 表格 + 底部「导出 Excel」（置灰）+ 页底小字；`?sheet=` 没注册 → 落第一个已注册 |
| `src/components/lqg/LedgerTable.vue` | 112 | 哑表格组件：`scroll-view scroll-x`、首列 sticky、表头吸顶、行点击抛 `row-tap`、`lqg-ledger__row--pending/--overdue` 行底色。**props 全是可序列化纯数据**（无函数 prop） |
| `src/api/ledger.ts` | 140 | 表格页取数（`/mp/int/sample/list` + `keyword` / `verifyStatus` + `raw: true`）与单元格/冻结格/行底色/掩码（`maskDonorName` 与后端 `MaskRules` 同形：首字 + `**`） |
| `src/pages/organoid/layout.ts` | 157 | **纯函数** `organoidLayout(identity, verifyStatus, mine, mode)` + `normalizeMode` / `fieldSpecs` / `fieldLabel` / `hasReceiveGroup` + 三份字段词表（fixture 的被测函数） |
| `src/pages/organoid/layout.fixture.spec.ts` | 75 | fixture 驱动 **13 用例**：结构守卫（9 / 3 / 7 / 5）+ 9 例逐例 + 词表守卫 + 两条病灶守卫 |
| `src/pages/organoid/api.ts` | 106 | 类器官收样接口层：内部/外部提交体构造（外部**只带三项**、内部七项 + `sampleKind=organoid`）、`POST/PUT /mp/ext/organoid`、`/mp/dict/hints?type=organoid` |
| `src/pages/organoid/form.vue` | 534 | 类器官收样填写页三模式：内部七项 / 外部三项、来源单位「选择 + 手填」底部弹层、日期时间弹框、只读页右上角「修改」、顶部「最后修改」小字 |
| `scripts/shots-sample-mp002.mjs` | 340 | 端侧截图脚本（H5 dev + Playwright，真调后端取 token 与造数据，**不读图**） |

### 2.2 小程序 · 修改

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `src/pages/history/sources.ts` | 抽出共用的 `toHistoryRow()`；**新增 `organoid` 数据源**（外部 `sampleKind=organoid&onlyMine=`；内部 `sort=recent` + 开关打开才 `mine=true`；点行 → 类器官填写页 `edit`／`view`） | ticket §2「历史编辑记录：注册 organoid」 |
| `src/pages/index/entries.ts` | `ledgerTarget(key)`：`/pages/admin/${key}` → `/pages/ledger/index?sheet=${key}` | ticket §2「『我的 → 内部管理』四个入口 → 表格页」。**越出 `touches`（见 §5.1）** |
| `src/components/lqg/FieldRow.vue` | `control` 联合类型加 `'select'`，点击也抛 `pick` | 类器官页的「来源单位」是**选择器**（落地规范 §5.4：日期与选择走底部弹框），FieldRow 原先只有 text/digit/date/datetime/seg/textarea 六种。**越出 `touches`（见 §5.1）** |
| `src/pages/sample/form.vue` | 只读页顶部一行：`StatusChip` + 右上角「修改」（`canEditFromView` 用 `formLayout(…,'edit').editable` 重算） | CR-20260918-07 的「点一行进只读详情，详情右上角『修改』」**覆盖四张表**，`tissue` 工作表指向这一页；SAMPLE-MP-001 只做了「view 只读」，没做修改入口。**越出 `touches`（见 §5.1 与 WARN-4）** |
| `src/pages.json`、`src/types/uni-pages.d.ts`、`src/types/components.d.ts` | 三个**生成产物**（新增 `pages/ledger/index` 路由、`LedgerTable` 组件类型） | 与 SYS-MP-001 / SAMPLE-MP-001 的处理一致，**一并提交**（只 `git checkout` 会丢掉新页面的路由）。收尾已用 `pnpm build:mp-weixin` 重生成，diff 只有 7 行（见 §7） |

### 2.3 后端 · 修改（3 个文件，越出 `touches`，见 §5.1）

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `sample/domain/bo/SampleQueryBo.java` | +`keyword` 字段（`@Schema`：内部编号精确 / 来源单位模糊） | SAMPLE-MP-001 在 `MpSampleQueryBo` 里留的**移交注释**：「由 SAMPLE-MP-002 在 `SampleQueryService` 里补上『内部编号等值 OR 来源单位模糊』那一段」；契约第 49 行也把 `keyword` 列在 `/mp/int/sample/list` 上，而 UI:mp.sample.list 要求表格页有搜索框 |
| `sample/service/SampleQueryService.java` | `list()` 里加一段：`.and(isNotBlank(keyword), w -> w.eq(internalNo).or().like(sourceUnitName))` | 同上。挂在**唯一那条读路径**上，与别的筛选同源（不另写一份判据） |
| `sample/mp/MpSampleQueryBo.java` | 注释更新（指向新落点） | 原注释说「本票不声明 keyword，由 SAMPLE-MP-002 补」，已过时 |

**Flyway / 取号**：**本票不新增迁移**（小程序接口无菜单、无新表、无新配置项）。最大版本仍是 `V202609221010`。

**接口清单（本票新增 / 补完）**

```
GET  /mp/int/sample/list?sampleKind=&keyword=&verifyStatus=   表格页（keyword 是本票补的：
                                                              内部编号等值 OR 来源单位模糊）
GET  /mp/int/sample/{id}        表格页点一行 → 只读填写页（mode=view）
PUT  /mp/int/sample             只读页点「修改」后保存（已有，本票只消费）
POST /mp/ext/organoid           外部送类器官（已有，本票只消费）
PUT  /mp/ext/organoid/{id}      外部改自己的待核验 / 无效（已有，本票只消费）
GET  /mp/ext/units              来源单位选择器的候选（已有，内外部都能调）
GET  /mp/dict/hints?type=organoid  类器官类型联想词（已有）
```

**没有新增类 / 页面之外的接口**：表格页与两个填写页全部复用 SAMPLE-MP-001 / AUTH-EXT-001 已落的小程序接口；本票在后端只补了一个筛选参数。

---

## §3 视觉证据（截图清单）

微信开发者工具在本 agent 沙箱跑不通（CLI 要写 `~/Library/Application Support/微信开发者工具/**` → EPERM；首次要扫码，同 SYS-MP-001 WARN-1）
→ 端侧证据用 **H5 dev server（`pnpm dev:h5 --port 9200`，带 `VITE_MOCK_LOGIN=1`）+ Playwright** 覆盖。
**开发者工具 / 真机未覆盖**（如实写明，不用 H5 冒充）。截图在 `doc/waves/reports/SAMPLE-MP-002/`，
**全程没有把任何 PNG 读进上下文**，断言只读 DOM 文本 / 元素计数 / 计算样式（完整输出见 `shots-transcript.txt`）：

| 文件 | 覆盖项 | 实测断言输出 |
|---|---|---|
| `01-ledger-tissue.png` | 表格页 · 样本记录（含待核验浅黄行） | 页签 `["样本记录","类器官收样"]`；计数行 `"共 8 条 · 左右滑动看全部 15 列"`；表头 `["内部编号","来源单位","供体姓名","性别","年龄","住院号","组织类型","收样日期","有无固定","处理时间","质控表","细胞活率报告","操作人","备注","切片染色"]`；8 行 / 待核验 2 行；冻结格 `["SJ90000007\n待核验 · 外部","SJ90000002\n待核验 · 外部","SJ90000003\n无效 · 外部"]` |
| `02-ledger-tissue-scrolled.png` | **左右滑动后**（冻结列仍贴在左边） | `{ok:true, scrollLeft:1408, scrollWidth:1798, clientWidth:390}`；冻结列计算样式 `{position:"sticky", left:"0px"}` |
| `03-ledger-organoid.png` | 表格页 · 类器官收样 | 表头 `["内部编号","来源单位","类器官类型","收样日期","处理时间","细胞活率报告","操作人","切片染色"]`；计数行 `"共 3 条 · 左右滑动看全部 8 列"`；冻结格 `["SJ00000009\n待核验 · 外部","T-oco55\n有效 · 内部","T-oco01\n有效 · 内部"]` |
| `04-ledger-unregistered-sheet-fallback.png` | `?sheet=embed`（未注册）→ 落到第一个已注册的 | 页签 `["样本记录","类器官收样"]`、高亮 `["样本记录"]` |
| `05-ledger-row-to-view.png` | 点一行（有效样本）→ **只读**填写页 | URL `…/#/pages/sample/form?id=9000001001&mode=view`；提交按钮 **0**、修改入口 **1**、输入控件 **0** |
| `06-ledger-view-to-edit.png` | 点右上角「修改」→ **修改模式** | 提交按钮 **1**、输入控件 **9** |
| `07-ledger-row-to-view-pending.png` | 点一行（外部送来待核验）→ 只读且**没有「修改」** | URL `…sample/form?id=9000001007&mode=view`；提交按钮 0、**修改入口 0** |
| `08-organoid-view-valid.png` | 类器官 · 内部管理进来（只读） | 分组 `["送检信息","收样信息"]`；修改入口 1、保存按钮 0；`备注` 字段数 **0**（模板 B 只有 7 列） |
| `09-organoid-edit-valid.png` | 点「修改」后的修改模式 | 保存按钮 **1**；顶部小字 `[]`（T-oco01 从没被改过 → `updateTime` 为 null，正是 SAMPLE-MP-001 坑 1 的口径） |
| `10-organoid-view-ext-pending.png` | 内部看**外部送来的待核验**类器官 | 修改入口 **0**、保存按钮 0；分组 `["送检信息","收样信息"]` |
| `11-organoid-new-internal.png` | 类器官 · 内部新增（七项） | 分组 `["送检信息","收样信息"]`、提交按钮 1；`备注` 0 |
| `12-organoid-new-external.png` | 类器官 · 外部新增（三项、**没有收样段**） | 分组只有 `["送检信息"]`；`收样信息` 组数 **0**；字段 `["备注"]` |
| `13-history-organoid-internal.png` | 历史编辑记录的类器官页签（内部） | 行 `["T-oco55","T-oco01"]`；经手人 `["我","我"]`；新增/修改 `["修改","新增"]` |
| `14-history-organoid-extC.png` | 历史编辑记录的类器官页签（外部 extC） | 行 `["SJ00000014"]`（**只列可见集合**） |
| `15-history-organoid-extA-empty.png` | 历史编辑记录的类器官页签（外部 extA，异组） | 行 `[]` + 空态 `["你填过的类器官收样记录会出现在这里"]` |

> 造数据走真接口（脚本里 `POST /mp/int/sample` 建 T-oco55、`extC POST /mp/ext/organoid` 建待核验那条），不直连库、不打桩业务接口；
> 只有 `/mp/me` 用 Playwright 打桩（让身份切换可控），其余请求真的打到 8081。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

三条 runner 在 `doc/waves/reports/SAMPLE-MP-002/accept-runners/`，完整输出在 `accept-transcript.txt`。
长链**包进函数判整条 rc**（`set -e` 不管非末尾位置的失败，AUTH-STAFF-001 坑 3）。

### 4.0 两处差异（都能单独复现）

**(a) `--fresh-module ruoyi-lqg`（既有 WARN，本沙箱恒 exit 2）** —— `api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted`。
按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边：

```
jar mtime        : 2026-09-22 00:09:37（epoch 1790006977）
lqg src newer    : []     ← find ruoyi-modules/ruoyi-lqg/src -newer <jar> 为空
admin src newer  : []     ← 同上（ruoyi-admin/src 一个字节没改）
8081 PID         : 77051（lsof -ti tcp:8081 -sTCP:LISTEN）
该 PID 打开的文件 : 含 ruoyi-admin/target/ruoyi-admin.jar（lsof -p <PID> | grep -c = 2）
后端日志 mtime    : 2026-09-22 00:14:53（晚于 jar = 进程起于打包之后）
嵌套 jar 复核     : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 9 个 sample/mp|SampleQueryBo 条目
                   （含新字段的 SampleQueryBo.class + MpSampleController/Service/QueryBo）
8080（Kevin）     : 全程没碰
```

**(b) `python3 doc/verify/db.py --quiet --sql "SELECT id …"` 拿不到值**（★ 这条是 **accept 自身的缺陷**，不是环境）：

`db.py` 的 `--quiet` 是 `action="store_true"`，语义是「**不打印任何行**」（`if not a.quiet: for ln in lines: print(ln)`）。
于是 accept 1 第 5 段拿到的 id 恒为空串，`printf '{"id":%s,…}' ""` 拼出 `{"id":,…}` → 后端 JSON 解析 400 → `jq -e '.code==200'` **必红**。
两跑并排（同一份脚本，`MP002_ACC1_KEEP_QUIET=1` 只切换这一处）：

```
===== accept 1 · 逐字变体（保留 ticket 里的 db.py --quiet）—— 预期红 =====
true          ← 段 1
1             ← 段 2
true          ← 段 3（POST 成功）
organoid|internal|valid|B 大学|Y   ← 段 4
false         ← 段 5：PUT「id」是空串 → 400
ACCEPT-1 EXIT=1

===== accept 1（去掉该段里的 --quiet，别的一个字不动）=====
true / 1 / true / organoid|internal|valid|B 大学|Y / true / 王工|organoid|valid /
true / true / organoid|pending|-|-|赵医生 / true / true
ACCEPT-1 EXIT=0
```

> 同一个句式在 `doc/tickets/` 里**一共 10 张**（含本票；除本票外还有 9 张）（`AUTH-EXT-001` / `SAMPLE-EXPORT-001` / `SYS-EXPORT-001` / `CRYO-FLOW-001` / `DOC-RENDER-001` / `EMBED-WEB-001` / `CRYO-WEB-001` / `DOC-PUBLISH-001` / `OCR-IMPL-001` / 本票）。
> **AUTH-EXT-001 因此在那两段上假绿**：它的断言是 `--bizcode PUT "/mp/ext/organoid/${OID}"` + `grep -qE '^(400|403|404)'`，
> 空 `OID` 让路径变成 `/mp/ext/organoid/` → 404 → **正好落进那个正则**。本票把它记成 **WARN-1（S2）**。

### 4.1 accept 1 · DATA —— ✅

```
########## SAMPLE-MP-002 · accept 1（DATA）##########
（差异：去掉 --fresh-module；第 5 段去掉 db.py 的 --quiet，其余字面逐字）
true                          ← 1) organoid 工作表集合 == ["9000001009"]
1                             ← 2) 接口 total == 库里独立数
true                          ← 3) 内部 POST 类器官 200
organoid|internal|valid|B 大学|Y   ← 4) 单位名快照 + 内部有效
true                          ← 5) PUT 补丁 200
王工|organoid|valid           ← 6) 库内 operator_name 改到了、状态仍是 valid
true                          ← 7) mine=true 顺序 ["T-oco55","T-oco01"]
true                          ← 8) 外部 POST /mp/ext/organoid 200
organoid|pending|-|-|赵医生   ← 9) join 出提交人 = extC 的真实姓名
true                          ← 10) extC 列表：只有自己那条、editable/mine 都 true
true                          ← 11) extA 列表：.rows==[]
ACCEPT-1 EXIT=0
```

**counterfeit 逐条排掉**：

- 工作表的 `sampleKind` 没传到后端（前端拿全量自己筛）→ 段 1 会拿到 8-9 行 → 红；实测 1 行。
- 两侧不同源：段 2 一侧走接口、一侧直连库（`db.py`）→ 我另跑了 `keyword` 的对照组（§4.4 b）。
- 选了单位 id 却没落单位名快照 → 段 4 的 `source_unit_name` 不是 `B 大学` → 红。
- 修改模式其实走 POST → 内部编号 `T-oco55` 撞号被拒 → 段 5/6 红。
- 外部类器官复用内部七项直接提交到 `/mp/int` → 段 8 会 403 红（本票走 `/mp/ext/organoid`，`submitter_id` 落本人 = 段 9 的「赵医生」）。
- 外部历史的类器官页签把别组的也列出来 → 段 11 `extA → []` 红。

### 4.2 accept 2 · DATA —— ✅

```
########## SAMPLE-MP-002 · accept 2（DATA）##########
（差异：pnpm 前加 npm_config_store_dir；其余字面逐字）
true      ← rm -rf dist + build:mp-weixin + pages/ledger/index.js 与 pages/organoid/form.js 存在
true      ← 7 段 grep（fixture 路径 / 无 .skip|.todo|.only / LedgerTable 直接 .vue 导入 /
          两段禁字 / mode=view 有 & mode=edit 无 / 页底小字）+ vitest 7 用例 +
          四份 xlsx 原件逐字 diff + fixture 的 expect 同源 jq
ACCEPT-2 EXIT=0
```

产物实况：

```
$ ls dist/build/mp-weixin/pages/ledger/
columns.js  index.js  index.json  index.wxml  index.wxss  sheets.js
$ ls dist/build/mp-weixin/pages/organoid/
api.js  form.js  form.json  form.wxml  form.wxss  layout.js
$ jq -c '{numTotalTests,numPassedTests,numFailedTests}' /tmp/lqg-ledger-cols.json
{"numTotalTests":7,"numPassedTests":7,"numFailedTests":0}
```

**counterfeit 逐条排掉**：

- 页面里手写一份列清单 → 两段禁字 grep 会命中（本票 `index.vue` / `LedgerTable.vue` 里**一个列名都没有**，表头文案从 `columns.ts` 来）。
- fixture 被改得去迁就代码 → `xlsx_header.py --print-header` 与四份原件的 diff 会红（本票没动 fixture）。
- 冻结列没从模板列里去掉 / 追加列插到中间 → 最后一段 jq 红（本票逐表 `expect == template−frozen+extra`）。
- 表格页还留着「＋ 一行」或点一行直接进修改模式 → 两段 grep 红（`mode=edit` 在 `sheets.ts` 里 0 命中）。
- 页底小字照抄旧版 → 两段 grep 红（本票逐字「核验、冻存取用请到网页工作台」）。
- 先 `rm -rf dist/build/mp-weixin` 再构建（runner 里第一句就是它）。

### 4.3 accept 3 · STATE —— ✅

```
########## SAMPLE-MP-002 · accept 3（STATE）##########
（差异：pnpm 前加 npm_config_store_dir；其余字面逐字）
true      ← fixture 路径 grep + 无 .skip|.todo|.only + vitest 13 用例 + fixture 结构 node 断言 +
           form.vue 无 donorName|hospitalNo|tissueType 且含 organoidType
ACCEPT-3 EXIT=0

$ jq -c '{numTotalTests,numPassedTests,numFailedTests}' /tmp/lqg-organoid-layout.json
{"numTotalTests":13,"numPassedTests":13,"numFailedTests":0}
```

**counterfeit 逐条排掉**：

- 外部复用内部七项再把收样段 `v-show` 掉 → 「外部新增」用例的 `fields` 里会带 `receiveDate` → 红（本票 `organoidLayout` 给外部只返回三项）。
- 外部 `editable` 漏了 `mine` → 「外部看同组别人的待核验」红（`mine=false` → false）。
- 内部看外部送来的待核验可改 → 「内部看外部送来的待核验」红（`pending` → false）。
- 只读模式按状态判断可改 → 「内部管理只读查看」红（`view` → false，永远）。
- 只读页把「修改」无脑显示 → 端侧 `10` 断出来修改入口 = 0。
- 身份缺失按内部渲染 → 「身份缺失」例 `fields: []`、`editable:false`。
- 类器官填写页复用组织样本的表单 → `form.vue` 的禁字 grep 红。

### 4.4 追加证据（accept 之外的机器证据）

**(a) 后端 `ruoyi-lqg` 全模块测试：`Tests run: 55, Failures: 0, Errors: 0, Skipped: 0`**

```
ExtBindStateMachineContractTest   Tests run: 11
StaffGrantRulesContractTest       Tests run:  6
MockLoginGuardContractTest        Tests run:  4
ExtChokepointContractTest         Tests run:  4
VerifyTransitionsContractTest     Tests run:  8
MpSampleContractTest              Tests run:  8
SampleKindRulesContractTest       Tests run:  6
SampleTableQueryContractTest      Tests run:  8
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```

**(b) `keyword` 的对照试验**（本票新补的后端判据，accept 没测，但它是 UI:mp.sample.list 要的搜索框）

```
$ as staff GET /mp/int/sample/list?sampleKind=organoid&keyword=<urlenc B 大学>
{"total":1,"rows":["T-oco01"]}          ← 来源单位模糊命中
$ as staff GET /mp/int/sample/list?sampleKind=tissue&keyword=<urlenc B 大学>
{"total":1,"rows":["T-hco04"]}          ← 不跨 sampleKind 泄漏
$ as staff GET /mp/int/sample/list?sampleKind=organoid&keyword=T-oco01
{"total":1,"rows":["T-oco01"]}          ← 内部编号等值
$ as staff GET /mp/int/sample/list?sampleKind=organoid&keyword=T-oco
{"total":0}                             ← 部分编号**搜不到**（等值口径，见 WARN-6）
$ as extA --bizcode GET '/mp/int/sample/list?keyword=x'
403	没有访问权限，请联系管理员授权        ← 角色闸没被新参数绕开
```

**(c) vitest 全量：67 用例 / 4 个 spec 文件全绿**（本票新增 20 例；SAMPLE-MP-001 的 22 例与 SYS-MP-001 的 25 例一字未改、继续绿）

**(d) 方向 A 的机器自检**（与 SYS-MP-001 第 3 条 accept 同形，本票复跑）

```
$ grep -rnE '(color|background|border|fill|stroke|shadow)[^;{]*#[0-9a-fA-F]{3,8}\b' src/pages src/components --include=*.vue --include=*.scss
（无命中 —— 本票新增的 6 个文件一个色值字面量都没有）
$ grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.vue --include=*.scss | grep -v '^src/uni.scss'
（无命中 —— 这一条是 SYS-MP-001 第 3 条 accept 里**扫整个 src** 的那一段，本票特意复跑过两次：
  第一版 LedgerTable.vue 里有个 JSDoc ` * ` 注释块与一处 `a * b` 乘法，两处都会命中它 → 见 §7 坑 4）
$ cmp doc/design-options/direction-a/tokens.scss src/style/tokens.scss   # 无输出
```

---

## §5 遗留与 raise

### 5.1 越出 `touches` 的改动（逐条列清）

| # | 文件 | 为什么必须改 | 风险 |
|---|---|---|---|
| 1 | `src/pages/index/entries.ts` | `ledgerTarget()` 是「内部管理四个入口点进去哪」的**唯一来源**（`me/index.vue` 就调它）；不改它就得在 `me/index.vue` 里另写一份目标串，两处迟早漂移。改的是 1 行字符串 | 极低：`entries.fixture.spec.ts` 不覆盖 `ledgerTarget`（只断 `entriesFor` / `entryTarget` / `meSections`），实测 25 例仍绿 |
| 2 | `src/components/lqg/FieldRow.vue` | 「来源单位」是**选择器**（落地规范 §5.4 要求走底部弹框 + 右侧 `›`），FieldRow 原先的六种控件里没有「点了抛 pick、值只读显示」这一支。加 `'select'` 是**纯增量**（默认 `text`，既有调用一字不变） | 低：SAMPLE-MP-001 的 22 例与端侧证据与本改动无关 |
| 3 | `src/pages/sample/form.vue` | CR-20260918-07 的「只读详情右上角『修改』」覆盖**四张表**；`tissue` 工作表指向这一页，SAMPLE-MP-001 只做了「view 全只读」没做修改入口。不补的话「样本记录」这一档点进去**没有回去改的路** | **跨票行为变更**：MP-001 的端侧 `11-int-form-view` 那一屏会多出一个「修改」文本（它断的是「保存按钮 = 0、输入控件 = 0」，两条仍成立）。记 WARN-4 |
| 4 | 后端 `SampleQueryBo` / `SampleQueryService` / `MpSampleQueryBo` | `keyword` 是 SAMPLE-MP-001 **明确移交**给本票的判据（`MpSampleQueryBo` 的类注释写着「由 SAMPLE-MP-002 在 `SampleQueryService` 里补上『内部编号等值 OR 来源单位模糊』那一段」），契约第 49 行也把它列在 `/mp/int/sample/list` 上。不补的话搜索框是个**不生效的装饰** | 低：`/lqg/sample/list` 多认一个可选参数（工作台不发，行为一字不变）；`Tests run: 55` 全绿。记 WARN-7 |

**没有动**：`doc/**` 的任何只读区、`doc/api-contract.md`、`code/deploy/**`、`application*.yml`、Flyway、`ruoyi-admin` 任何源码、`code/plus-ui`、`code/miniapp/src/pages/admin/**`（见 §5.4）。

### 5.2 与 `doc/api-contract.md` 的差异

**没有差异。** 逐行核过：

| 契约行 | 实现 | 判断 |
|---|---|---|
| 第 49 行 `GET /mp/int/sample/list` 的形状与参数（含 `keyword`） | 行 = `SampleVo`（模板列 + `handlerName` / `mine` / `editable`）；`keyword` 与 `verifyStatus` 都是可选 | ✅（`keyword` 是本票补的，契约**已经**列了它） |
| 第 49 行「`sort=recent` / `mine=true`」 | 表格页**两个都不带**（带 `sort=recent` 会把这页变成「有人经手过的」而不是全表）；历史页签用 `sort=recent` | ✅ |
| 第 53 行 `POST/PUT /mp/ext/organoid` | 外部表单只带 `ExtOrganoidSubmitBo` 的四个键（`sourceUnitId?` / `sourceUnitName` / `organoidType` / `remark?`） | ✅ |
| `GET /mp/ext/sample/{id}` 无 `updateByName` / `updateTime` | 外部的「最后修改」小字因此只在内部侧出现（`lastModified` 判 `isInternal`），与契约一致 | ✅ |

### 5.3 没把握 / 需要确认的口径

1. **只读页的「右上角修改」是页面内第一行，不是原生导航栏**：mp-weixin 的原生导航栏放不了自定义按钮（`app-plus` 的 `titleNView` 不适用），
   所以「右上角」落在内容区第一行右端（`.lqg-sec` 行，左状态徽标 / 右「修改」）。视觉上与图廊 `#mp-ledger-view` 的意图一致，但严格说不是标题栏那一行。
2. **内部看外部送来的待核验类器官，只读页仍然渲染「收样信息」整组（7 项、全置灰）**：这是 fixture 第 7 例的口径（`fields` = 内部七项、`editable=false`）。
   「外部没有收样段」说的是**外部身份**，不是「待核验的样本」——两者别混。本票按 fixture 实现。
3. **`mergePatch` 的「没传 = 沿用现值」让一个操作做不出来**：把「已选单位 id 的行」改成「手填单位名」（`sourceUnitId` 由有变无）在小程序里改不动
   （SAMPLE-MP-001 已记的边界：误清一个字段的代价 > 清不掉一个字段）。要在小程序里做这件事得加显式的「清空」哨兵值，属后续 CR。
4. **`keyword` 的内部编号是等值匹配**（SAMPLE-MP-001 的移交口径逐字如此）→ 输入部分编号搜不到（§4.4 b 的 `T-oco` → 0 行）。
   若甲方期望「敲一半就能搜」，改 `SampleQueryService` 那一段的 `eq` → `like` 即可（记 WARN-6）。

### 5.4 明确没做（ticket §3 边界逐条核对）

不做石蜡包埋 / -80 冻存两个工作表的数据与注册（列已在 `columns.ts` 定完，注册票 EMBED-MP-001 / CRYO-MP-001）｜不做导出（按钮置灰，SYS-EXPORT-001）｜
不做工作台首页（SYS-HOME-001）｜不做格子里直接编辑、不做列的显示隐藏与拖动排序｜不做核验与改判｜识别条只留位置（OCR-MP-001，类器官页按 UI:mp.organoid.form「无识别条」**一个都没渲染**）。

> `code/miniapp/src/pages/admin/{sample,organoid,embed,cryo}.vue` 四个占位页（SYS-MP-001 建、`owner: SAMPLE-MP-002`）在本票后**已无任何引用**，
> `pages.json` 里还挂着 4 条死路由。它们不在本票 `touches` 里，**本票没删**（删文件属 touches 之外）——记 WARN-5 交主会话定。

---

## §6 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | accept-bug | **`python3 doc/verify/db.py --quiet --sql "SELECT id …"` 永远拿不到值**：`--quiet` 的语义是「不打印任何行」（`if not a.quiet: print`），空串拼进 `printf '{"id":%s,…}'` → JSON 解析 400 | **本票 accept 1 第 5 段因此恒红**（去掉 `--quiet` 后全绿，两跑并排见 §4.0）。同一句式在 `doc/tickets/` 里**一共 10 张**（除本票外还有 9 张）；**AUTH-EXT-001 已经因此假绿过**——它的断言是 `--bizcode PUT "/mp/ext/organoid/${OID}"` + `grep -qE '^(400\|403\|404)'`，空 `OID` → 路径 `/mp/ext/organoid/` → 404 → 正好落进正则。方案：① 把 `db.py --quiet` 的语义改成「只打印值、不打印 `…（共 N 行）` 尾巴」（一行改动，10 张票一起修好）；② 或在 ticket 生成器里把取 id 统一写成 `jq -r '.data'`（POST 的返回值就是新 id，本票实测 `{"code":200,"data":"2102…"}`）。**本票没改 `db.py`（只读区）。** |
| WARN-2 | **S2** | harness | **`api.sh --fresh-module` 在本沙箱恒 exit 2**（`ps -o lstart=` 被禁）+ **`pnpm` 必须带 `npm_config_store_dir`**（全局缓存在沙箱里只读） | 已连续 **9+** 张命中（SYS-MODEL / AUTH-LOGIN / SYS-WEB / AUTH-STAFF / AUTH-GROUP / AUTH-EXT / SAMPLE-MP-001 / 本票）。只影响 subagent 沙箱。本票的等价替代五项见 §4.0(a)；三条 runner 头部都显式记了差异。 |
| WARN-3 | S3 | doc-drift | **ticket §2 写「内部历史 `GET /mp/int/sample/list?sampleKind=organoid&mine=true`」，与 CR-20260918-07 冲突**（CR：内部默认**中心全员** + 顶部「只看我提交的」开关） | 本票**按 CR 实现**（`sort=recent`，开关打开才另带 `mine=true`），与样本页签同一形状。方案：把 SAMPLE-MP-002 的 ticket 正文那一句改成与 CR 一致的措辞，免得下游票照 `mine=true` 写死（EMBED-MP-001 / CRYO-MP-001 的历史页签会照抄）。 |
| WARN-4 | S2 | counterfeit-risk | **给 `pages/sample/form.vue` 补了只读页的「修改」入口**（CR-20260918-07 覆盖四张表；SAMPLE-MP-001 只做了 view 全只读），属**跨票行为变更** | 影响 D2 QA 复跑 SAMPLE-MP-001 的端侧证据：`11-int-form-view` 那一屏会多出一个「修改」文本（它断的「保存按钮 0 / 输入控件 0」两条仍成立）。**本票没改任何 accept**。方案：把这条写进 D2 QA 的返工脚本备注，或在 SAMPLE-MP-001 的报告里加一行「修改入口由 SAMPLE-MP-002 补」。 |
| WARN-5 | S3 | debt | **`src/pages/admin/{sample,organoid,embed,cryo}.vue` 四个占位页已无引用**（`pages.json` 里还有 4 条死路由，文件里 `owner: SAMPLE-MP-002`） | 本票把「内部管理」四个入口的目标换成了 `pages/ledger/index?sheet=…`（ticket §2 要求），占位页因此无人可达。它们在 `touches` 之外，本票**没删**。方案：一张 5 分钟的小票删掉这 4 个文件并重生成 `pages.json`（或由 SYS-MP-001 的 owner 一并处理）。 |
| WARN-6 | S3 | clarify | **表格页搜索框的「内部编号」是等值匹配**（SAMPLE-MP-001 的移交口径逐字是「内部编号等值 OR 来源单位模糊」）→ 输入部分编号搜不到 | 本票按移交口径实现（§4.4 b：`keyword=T-oco` → 0 行）。方案：请主会话确认甲方期望；要「敲一半就能搜」只需把 `SampleQueryService.list` 里那段 `eq(Sample::getInternalNo, …)` 改成 `like`（内部编号不是加密列，不违反 ADR-0006）。 |
| WARN-7 | S3 | doc-drift | **`keyword` 落在了父类 `SampleQueryBo` 上**，因此 `/lqg/sample/list` 也多认一个可选参数（契约第 49 行只把 `keyword` 写在 `/mp/int/sample/list` 上） | 工作台不发这个参数，行为一字不变（`Tests run: 55` 全绿、SAMPLE-WEB-001 的 8 例也过）。选父类的理由：判据只有 `SampleQueryService.list` 一条路，落在那儿才与别的筛选同源（子类声明会让 service 反向 import `sample.mp`）。方案：若主会话认为工作台不该认这个参数，把字段挪回 `MpSampleQueryBo` 并在 service 里按类型取（会引入一条 `service → mp` 的依赖）。 |
| WARN-8 | S3 | clarify | **只读页的「修改」在内容区第一行右端，不是原生导航栏右上角** | mp-weixin 原生导航栏不支持自定义按钮。视觉意图与图廊 `#mp-ledger-view` 一致；若甲方要严格「标题栏右上角」，需要 `navigationStyle: 'custom'` + 自绘导航栏（会自己处理返回键与胶囊避让，约 0.5 人日）。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`doc/verify/db.py` 只读执行器但 exit 0、`reseed.sh` 清不掉运行时账号（本票收尾跑 `clean-orphan-accounts.sh --yes`）、surefire `groups` 假绿、`PageQuery` 只有两参构造、`updateById` 忽略 null、MyBatis-Plus 无按列名排序重载、微信开发者工具在沙箱跑不通、`sys_config` 读带 Redis 缓存。

---

## §7 坑与解法（给下游，3-5 行）

1. ★ **`doc/verify/db.py --quiet` 不打印任何东西**（不是「打印得更干净」）：凡是 `$(python3 doc/verify/db.py --quiet --sql "SELECT id …")` 这种取值的写法拿到的都是空串。
   本票 accept 1 第 5 段因此**必然红**，去掉 `--quiet` 后全绿；`doc/tickets/` 里还有 10 张同样写法（AUTH-EXT-001 已在上面假绿）→ 见 WARN-1。**改单测脚本前先改这条口径**。
2. ★ **页面/组件里一律显式 `import WdXxx from 'wot-design-uni/components/wd-xxx/wd-xxx.vue'`**（SAMPLE-MP-001 坑 6：只靠 easycom 会静默丢掉该模块的 `.js` 产物）。
   本票 6 个新文件**零 easycom 引用**，`test -f dist/build/mp-weixin/pages/ledger/index.js` 与 `…/pages/organoid/form.js` 都在；`LedgerTable.js` 也在。
3. ★ **`pages.json` 是生成产物、且 dev 与 build 写出的平台注释顺序不同**（`H5 || MP-WEIXIN` vs `MP-WEIXIN || H5`）：
   `pnpm dev:h5` 跑过之后 pages.json 会多出两条无关 diff。收尾用 **`pnpm build:mp-weixin` 重生成一次**（写回 `H5 || MP-WEIXIN`），得到「只多 7 行新路由」的最小 diff —— 比 `git checkout` 好，`checkout` 会把新页面路由一起丢掉。
4. ★ **`.vue` / `.scss` 里不要出现 JSDoc 的 ` * ` 注释块，也不要写 `a * b`**：SYS-MP-001 第 3 条 accept 有一段
   `grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.vue --include=*.scss`（**扫整个 src**，本意是抓裸 `*` 选择器），
   它对源码文本一样敏感 —— 我第一版 `LedgerTable.vue` 里一个 `/** … */` 注释块（每行以 ` * ` 开头）与一处
   `length * columnWidth` 乘法**两处都被命中**，会让**上一张票的 accept 变红**。解法：`.vue` 里一律用 `//` 注释；
   算术挪到 `.ts`（本票把它挪成了 `pages/ledger/sheets.ts` 的 `ledgerTableWidth()`）。**同类票在写 `.vue` 前先跑一遍那条 grep。**
5. **哑组件用「可序列化 props」而不是函数 prop**：`LedgerTable.vue` 收的是 `{id, tone, frozen, sub, cells[]}` 的纯数据矩阵（页面侧 `toTableRows()` 拍好），
   小程序渲染层拿不到函数，函数 prop 是「H5 看着对、真机一片空白」的经典来源；同时列名只从 `columns.ts` 来，组件里一个列名都没有。
6. **`scroll-view scroll-x` 里做「首列冻结」别依赖 `width: max-content`**：显式算 `frozenWidth + 列数×columnWidth` 当整表宽度（本票 118 + n×120，在 `sheets.ts` 里算），
   行用 `display:flex` + 单元格 `flex:none; width:118/120px`，首列 `.lqg-ledger__fz` 给 `position:sticky; left:0`。H5 实测 `scrollLeft` 能拉到 1408/1798、冻结列计算样式 `sticky/0px`。
7. **拿 `keyword` 试接口时中文 / 空格要 URL-encode**：`api.sh` 把 path 原样交给 curl，`keyword=B 大学` 里的空格会让 curl 直接 `URL rejected: Malformed input`（exit 2，看着像后端挂了）；
   用 `%20` / `%E5%A4%A7%E5%AD%A6` 即可。

---

## §8 收尾（长进程 / 端口 / DB）

- **后端 8081**：收尾时按 PID 关掉（`lsof -ti tcp:8081 -sTCP:LISTEN` → `kill`；**没用 `pkill -f`**）。
- **H5 dev server 9200**：收尾时按 PID 关掉。
- **8080（Kevin）**：全程没碰（开工时与收尾时 `lsof` 都为空）。**未占** 8082 / 8083 / 8099 / 9201。
- **5433（`lqg-dev-postgres`）/ 6380（`lqg-dev-redis`）/ 9002-9003（`lqg-dev-minio`）**：全程在跑，**没停**（留给后续 ticket）。
- **DB 收尾**：`reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`（截图脚本造的 T-oco55 与外部那条由 reseed 收回）。
- **提交**：只 `git add` 本票自己的路径（见 §9），`doc/waves/state.json` / `_manifest.json` 一个字节没动。
- **`git status` 里的生成产物**：`src/pages.json` / `src/types/uni-pages.d.ts` / `src/types/components.d.ts` 三个是 uni-pages / uni-components 插件写的，**一并提交**
  （新页面 `pages/ledger/index` 的路由只能从这里来）；`src/pages.json` 的 diff 已用 build 重生成成最小形态（+7 行，见 §7 坑 3）。

## §9 提交清单

```
A  code/miniapp/src/pages/ledger/**                      （columns.ts / columns.fixture.spec.ts / sheets.ts / index.vue）
A  code/miniapp/src/pages/organoid/api.ts
A  code/miniapp/src/pages/organoid/layout.ts
A  code/miniapp/src/pages/organoid/layout.fixture.spec.ts
A  code/miniapp/scripts/shots-sample-mp002.mjs
A  code/miniapp/src/api/ledger.ts
A  code/miniapp/src/components/lqg/LedgerTable.vue
M  code/miniapp/src/pages/organoid/form.vue
M  code/miniapp/src/pages/history/sources.ts
M  code/miniapp/src/pages/index/entries.ts            （越出 touches，§5.1-1）
M  code/miniapp/src/components/lqg/FieldRow.vue        （越出 touches，§5.1-2）
M  code/miniapp/src/pages/sample/form.vue              （越出 touches，§5.1-3）
M  code/miniapp/src/pages.json / src/types/uni-pages.d.ts / src/types/components.d.ts   （生成产物）
M  code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/domain/bo/SampleQueryBo.java
M  .../sample/service/SampleQueryService.java
M  .../sample/mp/MpSampleQueryBo.java                  （越出 touches，§5.1-4）
A  doc/waves/reports/SAMPLE-MP-002/**                  （报告 + 15 张截图 + 3 份 runner + 两份 transcript）
```
