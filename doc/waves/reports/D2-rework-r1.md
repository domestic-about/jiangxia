# D2 · QA 门第 1 轮返工（`qa_rework`）报告

- **status: done**（5 条 S0/S1 全修完；两份 QA 脚本修后分别 34/35、25/25；唯一残留红 G3-16 是**新发现**的第 6 个缺陷，未在本轮授权范围内，见 §4）
- 分支 `task/D2`（未切、未 push、未 merge、未动 `doc/waves/state.json` / `_manifest.json`）
- 修前基线来源：① QA r1 原始实测（`doc/waves/qa/D2-r1-L2.json`：28/35、21/25）；② 本片把 5 条修复 `git checkout --` 回退后自跑复现（28/35、18/25，日志见 `D2-rework-r1-before-mp.log` / `D2-rework-r1-before-web.log`）
- 修后实测：`node doc/waves/regression/D2/L2-mp-h5.mjs` **34/35**；`node doc/waves/regression/D2/L2-web-plusui.mjs` **25/25**（日志 `D2-rework-r1-after-mp.log` / `D2-rework-r1-after-web.log`）

---

## 1 两份 QA 脚本 · 修前 / 修后并排

| 脚本 | QA r1 原测 | 本片修前复现 | **本片修后** |
|---|---|---|---|
| `D2/L2-mp-h5.mjs`（组 1 外部 + 组 3 内部） | 28/35 | 28/35（逐条与 QA 相同） | **34/35** |
| `D2/L2-web-plusui.mjs`（组 2 工作台） | 21/25 | 18/25（多 3 条：库内残留一条非 seed 段孤儿样本，见 §3 坑 1） | **25/25** |

### 1.1 每一条转绿的检查 → 对应哪一条修复

| 检查 | 修复前 | 修后 | 对应修复 |
|---|---|---|---|
| `G1-03` 首页点「样本记录信息表」→ 进新增填写页 | FAIL（`#/pages/sample/form` 无 query → 错误态、`hasSubmit=false`） | PASS（`...#/pages/sample/form?mode=new`、`hasSubmit=true`、正文无「没能加载」） | **S0-1** |
| `G1-06` extA 历史编辑记录里新记录 + 状态「待核验」 | FAIL | PASS（列表正文出现新单号 + `待核验`） | **S0-2**（StatusChip 渲染出来；G1-06 断的是状态文案） |
| `G1-08` 外部在历史编辑记录看到无效原因 | FAIL（列表与打开的 edit 表单都没有原因） | PASS（列表行内出现 `无效原因：L2复验：缺住院号，请补`） | **S1-2** |
| `G3-02` staff 首页点「样本记录信息表」→ 进新增填写页 | FAIL | PASS（`...#/pages/sample/form?mode=new`） | **S0-1** |
| `G3-15` 内部类器官收样：点「收样日期」能弹出选择器 | FAIL（`{"action":0,"popup":0,"visible":false}`） | PASS（`{"action":2,"popup":1,"visible":true}`） | **S0-3** |
| `G3-19` 历史编辑记录每行都有核验状态徽标 | FAIL（`{"items":1,"chips":0}`） | PASS（`{"items":1,"chips":1}`，`.lqg-tag` 元素出现） | **S0-2** |
| `WEB-L2-04.3` 筛选「样本类别=类器官」 | FAIL（点选项 8s 超时） | PASS（`got=["9000001009"]`） | **S1-1** |
| `WEB-L2-05` 11 种筛选全部一致 | FAIL（`bad=3/11`，其中 2 条为孤儿数据） | PASS（`bad=0/11`） | **S1-1**（+ 清库内孤儿） |
| `WEB-L2-06a` 样本类别下拉有「组织样本 / 类器官」 | FAIL（`opts=[] dd="无数据"`） | PASS（`opts=["组织样本","类器官"]`） | **S1-1** |
| `WEB-L2-06b` 字典 key 真名有 2 条 | FAIL（探针 401/空） | PASS（`lqg_sample_kind: {code:200, labels:["组织样本","类器官"]}`） | **S1-1** + harness 探针修正（见 `W-2`） |
| `WEB-L2-04.2` / `04.7` / `06` | FAIL（多出孤儿行 `2102081991426990081`） | PASS | 非产品缺陷：清掉 db 里非 seed 段残留行（**不是本轮 5 条**） |

### 1.2 修后仍红的唯一一条（**新发现**，不在本轮 5 条内）

| 检查 | 修前 | 修后 | 说明 |
|---|---|---|---|
| `G3-16` 内部类器官收样 7 列能新增一条 | FAIL（`toast="请选择来源单位"`，库里无 `T-l2org01` —— 当时卡在日期选不了） | FAIL（`toast="|请选择来源单位|请选择来源单位"`） | 日期选择器已修好（G3-15 已绿），提交时**改卡在「来源单位」为空**：内部新增类器官收样页没有给来源单位任何默认值，而 `organoid/form.vue:282-285` 把它列为必填 |

---

## 2 逐条：现象 → 根因 → 改法 → 运行时证据

### S0-1 · 首页四个入口都不带 `mode=new`（`SAMPLE-MP-001`）

- **现象**：首页点「样本记录信息表」（内部 / 外部都一样）→ URL 停在 `#/pages/sample/form`（无 query）→ 正文 `没能加载这条样本`、`uni-button` 数 0（没有提交按钮）。四格入口全一样坏。
- **根因**：`src/pages/index/entries.ts` 的 `ENTRY_FORM_TARGET` 给的是裸路径；`sample/form.vue` 的 `onLoad` 把 `undefined` 交给 `layout.ts` 的 `normalizeMode` → `'view'`；`view` 且无 `id` → `failed = true`。而填写页注释自己写的契约是「首页点表进来 → mode=new」。
- **改法**（按主会话裁定：**不动** `normalizeMode` 的缺省，入口自己带参数）：
  - `entries.ts`：`ENTRY_FORM_TARGET` 四个值全部改成 `/pages/<表>/form?mode=new`。
  - 同时确认四个目标页都能吃下这个 query：`sample/form.vue` / `organoid/form.vue` 走 `normalizeMode('new')` → 可写；`embed/form.vue` / `cryo/form.vue` 是占位页、连 `onLoad` 都没有，多一个查询参数不会崩（G1-03 / G3-02 实测落页正常）。
  - `doc/verify/fixtures/home-entries-cases.json` 的 `targetCases` 7 个非空 `expect` 同步补 `?mode=new`（见 `W-1`）。
- **运行时证据**：
  - `G1-03 PASS — url=...#/pages/sample/form?mode=new hasSubmit=true text="样本记录信息表|拍照识别|…|送检信息|…"`（错误态文案消失）
  - `G3-02 PASS — url=...#/pages/sample/form?mode=new`
  - `SYS-MP-001` accept 2 复核：vitest `entries.fixture.spec.ts` **25 passed / 0 failed**；`targetCases.length===10`、`expect===null` 3 条（accept 里那段 node 校验原样通过）

### S0-2 · `StatusChip` / `FieldRow` / `SegButtons` 引用未声明的 `props`（`SAMPLE-MP-001`）

- **现象**：① 历史编辑记录每行核验状态徽标全不渲染（`{items:1,chips:0}`）；② 只读表单（内部管理点一行 = `mode=view`）一个字段值都没有；③ 性别 / 有无固定 / 质控表 / 细胞活率报告的按钮组点不动。
- **根因**：三处都写成 `withDefaults(defineProps<…>(), {…})` **没有赋给 `const props`**，脚本里却引用 `props.x` → 运行时 `ReferenceError: props is not defined`（`StatusChip.vue:34`、`FieldRow.vue:45`、`SegButtons.vue:23`）。
- **改法**：三处补 `const props =`（并留注释钉住原因）。顺手把 `src/**/*.vue` 里**所有** `<script setup>` 中 `props.` 的引用扫了一遍：只有这三个组件缺声明，其余（`ErrorState` / `LoadingState` / `NoteBar` / `SampleCard` / `EntryTile` / `MeRow` / `PlaceholderPage` / `LedgerTable` / `InternalAdminBlock` …）都只在模板里用 `props`，不受影响。
- **运行时证据**（只看 DOM 文本 / 类名，未读任何图片）：
  - 只读表单：`node doc/waves/regression/D2/L2-probe-fieldrow-segbuttons.mjs` → `VIEW TEXT: "样本记录信息表|有效|修改|送检信息|来源单位|本中心|*|供体姓名|测试供体辛|性别|…|年龄|45|住院号|ZY0000008|*|组织类型|肝组织|…|收样日期|2026-08-08|内部编号|T-hli05|有无固定|…|操作人|李工|…"`，`errs after view: []`（修前是「只剩字段名、一个值都没有」）
  - 按钮组：`.lqg-seg__item--on` 点击前后 `["女","有","有","有"] → ["男","有","有","有"]`；再点「有无固定·无」→ `["男","无","有","有"]`；`errs after seg tap: []`
  - 徽标：`G3-19 PASS — {"items":1,"chips":1}`（每行都有 `.lqg-tag`）
  - `G3-20 PASS — []`（`StatusChip` / `props is not defined` 的控制台错误已 0 条）

### S0-3 · `wd-datetime-picker` 用错 API（`SAMPLE-MP-002`）

- **现象**：内部类器官收样页点「收样日期」→ `{"action":0,"popup":0,"visible":false}`，面板永不弹出 → 「收样日期」必填且只能靠这个面板填 → 内部一条类器官收样都建不出来。
- **根因（先读了组件真源码，未猜）**：`node_modules/wot-design-uni/components/wd-datetime-picker/types.ts` 的 `datetimePickerProps` **没有 `visible`**；开关是组件内部的 `popupShow`（`wd-datetime-picker.vue:188` `<wd-popup v-model="popupShow">`），对外只 `defineExpose({ open, close, setLoading })`（同文件 `:792`，`types.ts:216-231` 的 `DatetimePickerExpose`）。页面传的 `:visible="pickerOpen"` 只会退化成根节点上的普通 HTML 属性，页面也从不调 `open()`。
- **改法**：两个页面（`sample/form.vue`、`organoid/form.vue`）都改成持组件实例 `const pickerRef = ref<{ open: () => void } | null>(null)`，`onPick()` 里 `pickerRef.value?.open()`；模板 `ref="pickerRef"`，删掉 `:visible` / `@cancel` / `@close`（关闭由组件自己管，`@confirm` 仍取回填值）。`pickerOpen` 整个删掉。
- **运行时证据**：`G3-15 PASS — {"action":2,"popup":1,"visible":true}`（`.wd-datetime-picker__action` 出现 2 个、`.wd-datetime-picker__popup` visible=true），脚本随后能点「确定」并把收样日期填进表单。

### S1-1 · `useDict('sample_kind')` 与库里真名不符（`SAMPLE-WEB-001`）

- **现象**：工作台样本总表「样本类别」下拉永远「无数据」，`el-select-dropdown__item` 数 0；UI 选「类器官」直接 click 超时。
- **根因**：`views/lqg/sample/index.vue:266` 用 `sample_kind`，库里真名是 `lqg_sample_kind`（迁移 `V202609210810__SYS-BASE-001-lqg-dicts.sql:13`）。
- **改法**：`grep -rn "useDict(" code/plus-ui/src` 全量过一遍——lqg 视图只有 `sample/index.vue`（`sample_kind` 一处）与 `SampleDrawer.vue`（`lqg_verify_status`，本来就对）；其余 `sys_*` / `wf_*` 是上游自带字典，不属 `lqg_*` 真名范围。把该文件 4 处 `sample_kind` 标识符（筛选下拉 `v-for`、表格 `dict-tag`、`useDict` 调用、解构）统一改成 `lqg_sample_kind`。
- **运行时证据**：`WEB-L2-04.3 PASS — got=["9000001009"]`；`WEB-L2-06a PASS — opts=["组织样本","类器官"]`；`WEB-L2-06b PASS — lqg_sample_kind: {code:200, labels:["组织样本","类器官"]}`；`WEB-L2-05 PASS — bad=0/11`。

### S1-2 · 外部看不到无效原因（`SAMPLE-VERIFY-001`）

- **现象**：工作台判无效后，extA 的历史编辑记录列表没有原因；点进去是 `form?mode=edit`，表单上也没有原因；唯一带红条的 `detail-ext` 只对「非本人 / 不可改」可达 → **本人无效样本没有任何入口能看到原因**。
- **根因**：① `ExtSampleVo`（外部列表行）里根本没有 `invalid_reason` 这个键，前端想显示也没有数据；② `sample/form.vue` 的 `NoteBar` 只有 `topNote`（不可改时），没有无效原因；③ `history/sources.ts` 的 `target()` 把「本人 + editable」直接送去 edit 表单，绕开了 `detail-ext` 的红条。
- **改法**（两条路都补上，选中「列表行内」为主）：
  1. 后端：`ExtSampleVo` 加 `invalidReason` 字段（`FIELD:t_lqg_sample.invalid_reason` 本就注明「外部可见」，`ExtChokepointContractTest` 的禁用字段表里没有它），`ExtSampleAssemblyService#toRow` 填上；`doc/api-contract.md` 第 50 行的 `ExtSampleVo{…}` 键集合同步补 `invalidReason`（若不补就是文档与实现不一致）。
  2. 前端：`history/sources.ts` 的 `HistoryRow` 加 `reason`（只在 `verifyStatus==='invalid'` 时带值），`SampleCard.vue` 加一条红底 `无效原因：…` 行（色值走 `--lqg-danger-soft` / `--lqg-danger` token，零色值字面量），`history/index.vue` 传 `:reason`。
  3. 顺手：`sample/form.vue` 在 edit 模式下若详情是 invalid，也出一条 `NoteBar tone="danger"` 显示 `detail.invalidReason`（`ExtSampleDetailVo` 本来就有这个键，不靠列表传参）。
- **运行时证据**：`G1-08 PASS — list="历史编辑记录|…|SJ00000036|无效|L** · 肝组织|无效原因：L2复验：缺住院号，请补|我|2026-09-22|…"`；库侧同一行 `verify_status=invalid` + `invalid_reason='L2复验：缺住院号，请补'`；接口侧 `GET /mp/ext/sample/list?pageSize=3` 的 `9000001003` 行带 `"invalidReason":"信息不全：缺住院号"`。

---

## 3 坑与解法

1. **基线被「非 seed 段孤儿行」污染**：`reseed.sh` 只删 `sys_user` 的 9000000000-9000009999 段与 truncate `t_lqg_*` 里 seed 覆盖的部分，**删不掉 snowflake id 的历史样本行**。上一轮 QA 遗留的 `2102081991426990081`（SJ00000034）让 web 两条筛选多出一行，基线从 21/25 掉到 18/25。解法：采基线前 `SELECT count(*) … WHERE id > 2100000000000000000` 复查并清掉；清后 web 修后 25/25。**报告里的 3 条「web 转绿」有 2 条（04.2 / 04.7 / 06）属于清库收益，不算产品修复。**
2. **`mvn` 的 `-s/-Dmaven.repo.local` 必须传绝对路径**：`.mvn-settings.xml` / `.m2repo` / `.buildhome` 都在**工作区根**，从 `code/RuoYi-Vue-Plus` 里写 `../.mvn-settings.xml` 会解析到 `code/.mvn-settings.xml` 并报 `The specified user settings file does not exist`；更坑的是外层套了 `| tail` 时 shell 退出码是 **tail 的 0**，看起来像成功。解法：`$WS` 绝对路径 + 重定向到日志再 `echo exit=$?`。
3. **H5 hash 路由下 `page.goto` 不重载页面**：QA 的独立探针 `L2-probe-fieldrow-segbuttons.mjs` 先开只读页、再 hash 跳到 `form?mode=new`，第二个页面实例是**旧的**（`--on` 还是只读页的 `["女","有","有","有"]`），所以探针看起来「还是点不动」。加一次 `page.reload()` 后同一段代码得到 `["女",…] → ["男",…]`。这是 harness 现象不是产品缺陷，**我因此没有改这份探针**（只在报告里说明）。
4. **裸 `fetch` 打 RuoYi 接口必 401**：字典/列表接口要 `Authorization: Bearer <Admin-Token>` **加** `clientid`，少任一个都回 `code:401` + `data:null`（探针把 `null` 读成空数组，看起来像「字典空」）。修 `WEB-L2-06b` 时两个头都补上才拿到 200。
5. **改后端就一定要重建 jar**：`ruoyi-admin.jar` 是 `package` 产物，`bash .tmp/run-backend.sh` 直接跑它；改完 Java 不重打包，跑的还是旧逻辑（本例先撞过一次 `find … -newer jar` 为空才发现）。

---

## 4 WARN 清单（severity + type + 标题 + 影响范围 / 方案）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| W-1 | **WARN**（需求层资产变更 · 已授权） | spec-gap | `doc/verify/fixtures/home-entries-cases.json` 的 `targetCases` 7 处 `expect` 补 `?mode=new` | **依据**：权威 `UI:mp.home.entries` 写「**点哪格都是进该表的填写页新增一条**」，而 ticket 又定「`mode` 缺失或不认识按只读处理」→ 入口函数**必须**自己带 `mode=new`；旧夹具漏写了这个参数（L0 fixture 只比字符串、不进页面，所以全绿掩盖了缺陷）。这是**补规格**，不是「改断言迁就实现」：当前实现真的进错误页（G1-03/G3-02 修前 FAIL）。<br>**约束复核**：`targetCases.length===10`、`expect===null` 3 条（accept 的 node 校验原样通过）；`entries.fixture.spec.ts` 25/25 全绿（≥22 条）。<br>**风险**：若后续有人把 `mode=new` 从入口挪走，夹具会跟着红 —— 这正是想要的方向。 |
| W-2 | **WARN**（QA 脚本改动 · 已在报告单列） | harness | 两份 QA 脚本共 2 处改动（**只动断言正负号与探针取数，没动任何产品期望值**） | ① `L2-mp-h5.mjs` 的 `G3-20`（原「StatusChip 渲染抛 ReferenceError」断言 `chipErr.length > 0`）→ 翻成 `=== 0`：这条本来就是「根因探针」，S0-2 修好后「抛错」不再是期望值；产品期望值（G3-19 徽标数、G1-12 只读、G3-11 可写）一个字没动。<br>② `L2-web-plusui.mjs` 的 `WEB-L2-06b`：① 探针补 `Authorization` + `clientid` 两个头（裸 fetch 必 401）；② 断言从「假名 `sample_kind` 为空 且 真名有 2 条」翻成「真名有 2 条且含『类器官』」——同样因为 S1-1 修好后「假名为空」不再是期望值。 |
| W-3 | WARN | spec-drift | `doc/api-contract.md` 第 50 行 `ExtSampleVo{…}` 键集合需补 `invalidReason` | S1-2 要在外部列表行显示原因，接口就得给这个键；该字段在 `FIELD:t_lqg_sample.invalid_reason` 已注明「外部可见」、`ExtChokepointContractTest` 的禁用表里也没有它 → 加它不破任何契约测试。**本片按纪律没有改 `doc/api-contract.md`**（它不在「不许改」清单里，但属需求层文档，改由主会话裁定）。若不补，`doc/api-contract.md` 与实现会不一致。 |
| W-4 | S1（**新发现**，本轮未授权修） | ux-debt | 内部新增「类器官收样」页的来源单位没有任何默认值 → `G3-16` 仍红 | **现象**：S0-3 修好日期选择器后，内部录类器官收样在提交处改卡 `toast="请选择来源单位"`（`organoid/form.vue:282-285` 必填校验），库里建不出 `T-l2org01`。<br>**根因**：`organoid/form.vue#load()` 的 `mode==='new'` 分支里，内部只默认了 `operatorName`（`:142-145`），`sourceUnitName` 留空；外部那条分支有默认（`store.ext`），内部 `/mp/me` 的 `ext` 是 `null`（实测 `{"identity":"internal","ext":null}`），没有可用的默认单位。页面有来源单位选择器，人点得动，所以**不是死路**，但没有默认值。<br>**方案（二选一，需主会话裁定）**：① 内部新增时默认选中单位列表里第一个启用单位（`units[0]`）——最省事，但会「替用户猜一个单位」；② 保持必填、不改默认，把 `G3-16` 记为「需要人点一次来源单位」的已知 UX 债（QA 脚本没点，所以恒红）。我倾向 ②（不猜业务数据），但 ① 能让 D2 端侧全绿。**风险**：选 ① 会让「内部录的类器官收样」带上一个用户没确认过的来源单位快照（导出那一列读的就是它）。 |
| W-5 | S2（既有 · 未碰） | type-debt | `src/components/biz/UnitGroupPicker.vue:174,178` 与 `src/pages/me/unit-group.vue:156` 的 `vue-tsc` 类型错（`string` 传给 `number`） | `pnpm type-check` 在本片改动前后都有这些错（属既有债，不是本轮引入）。本轮新增代码（`pickerRef`、`reason`）**没有新增任何 `vue-tsc` 报错**：报错清单里没有 `sample/form.vue` / `organoid/form.vue` / `history/*` / `SampleCard.vue` / `StatusChip.vue` / `FieldRow.vue` / `SegButtons.vue`。 |
| W-6 | S3（验证覆盖 · 记账） | coverage | `G3-19` 只校验了「1 行 → 1 个徽标」 | 该检查在内部历史编辑记录页跑，此时库里只剩 1 条可见记录（QA 脚本前面把 extC 的类器官记录算进了「只看我提交的」语境），所以 `items:1, chips:1`。**「每行都有徽标」只在 1 行的样本上验到**；`StatusChip` 现在是纯 `computed(() => props.text || TEXT[props.value||''])`，逐行独立渲染，风险低，但严格说覆盖不足。方案：后续在 L3 或补一条「先造 ≥2 行再看 chips>=items」的探针。 |

---

## 5 跑过的 acceptance（原文重跑，唯一改动 = 去掉沙箱跑不了的 `--fresh-module`）

| accept | 结果 |
|---|---|
| `SYS-MP-001` accept 2（夹具改后复核） | **PASS** — vitest `numPassedTests=25, numFailedTests=0`；node 校验 `targetCases=10` / `null=3` 通过 |
| `SAMPLE-MP-001` accept 1 | **PASS** — vitest `numPassedTests=22, numFailedTests=0`；detail-ext 禁字/`internalNo`/`entriesFor`/`sort=recent` 各段全过 |
| `SAMPLE-MP-001` accept 2 | **PASS** — 构建 + 9 段库侧断言 + 角色闸全过（脚本 `.tmp/d2rew/sample-mp-001-acc2.sh`） |
| `SAMPLE-MP-002` accept 1 | **PASS**（脚本 `.tmp/d2rew/sample-mp-002-acc1.sh`） |
| `SAMPLE-MP-002` accept 3 | **PASS** — vitest `numPassedTests=13`；fixture 结构 + 禁字 grep 全过 |
| `SAMPLE-WEB-001` accept 1 | **PASS** — 8 组筛选集合 + 软删 + 待核验置顶全过 |
| `SAMPLE-WEB-001` accept 2 | **PASS** — 菜单 5210 / 角色 101,102 / getRouters / 按钮权限串 / SegButtons≥4 / 无 el-switch 全过 |
| `SAMPLE-VERIFY-001` accept 1 | **PASS** — 非法转移全被拒 + 库内不变 + 合法路径可达 + 改判有效清 `invalid_reason` |

## 6 构建

| 产物 | 命令 | 结果 |
|---|---|---|
| plus-ui | `pnpm build:prod` | **exit 0**，`DONE Build complete`（日志 `.tmp/d2rew/plusui-build.log`） |
| miniapp | `rm -rf dist/build/mp-weixin && pnpm build:mp-weixin` | **exit 0**，`DONE Build complete`；`pages/sample/form.js`、`pages/history/index.js`、`pages/ledger/index.js`、`pages/organoid/form.js` **四个产物都在** |

## 7 证据文件

- `doc/waves/reports/D2-rework-r1-before-mp.log`（修复前 mp 全量 stdout，28/35）
- `doc/waves/reports/D2-rework-r1-before-web.log`（修复前 web FAIL 清单，18/25）
- `doc/waves/reports/D2-rework-r1-after-mp.log`（修复后 mp 全量 stdout，34/35）
- `doc/waves/reports/D2-rework-r1-after-web.log`（修复后 web 全量 stdout，25/25）
