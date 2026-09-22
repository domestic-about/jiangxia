# D2 r1 · L2（端侧 UI）独立 QA 笔记

分支 task/D2。只认代码 / 库 / 运行中的系统。截图只落盘，不进上下文。

## 0 环境与反 stale（本片自跑）

- 后端：`bash .tmp/run-backend.sh` → 8081，日志 `.tmp/d2l2-backend.log`，启动成功。
  - 反 stale：`find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer ruoyi-admin/target/ruoyi-admin.jar` → 空
  - jar mtime = Sep 22 00:29；8081 起的进程是**本片 kill 掉旧监听后自己起的**（起前 `lsof -ti tcp:8081` 已空）
  - `lsof -p 60924 | grep -c ruoyi-admin.jar` = 2 → 进程确持该 jar；`ps` 被沙箱禁（`Operation not permitted`）→ 启动时刻用后端日志时间戳 2026-09-22 00:32 佐证，晚于 jar mtime
- miniapp H5 dev：9200（`VITE_MOCK_LOGIN=1`，见 `.tmp/d2l2-mp.log`）
- plus-ui dev：8082（见 `.tmp/d2l2-web.log`）
- reseed：`bash doc/verify/reseed.sh --yes`（失败项：embed/cryo/qc 表在 D2 未建，按 README 语义跳过，与本片无关）

## 组 2 · 工作台样本总表（脚本 `doc/waves/regression/D2/L2-web-plusui.mjs`，shots `doc/waves/regression/D2/shots/L2-web/`）

登录 lqgadmin → 侧栏「样本总表」（url `/sample`）。断言一律来自 DOM 文本 / 计算样式 / 接口返回。

| 检查 | 结果 | 断言值 |
|---|---|---|
| 待核验置顶 | PASS | 行序 id = [1007,1002,1003,1006,1005,1004,1001,1008,1009]，前两行 class 含 `lqg-sample__row-pending`，第三行没有 |
| 待核验行浅黄 | PASS | `tr` 计算样式 `background-color = rgb(252, 241, 218)`（= `--lqg-warn-soft #fcf1da`） |
| 来源单位=A 医院 | PASS | {1001,1002,1003,1004,1005,1007} |
| 组别=肝胆外科组 | PASS | {1001,1002,1003,1004,1007} |
| **样本类别=类器官** | **FAIL** | **下拉是「无数据」，选不到任何选项** |
| 提交来源=内部 | PASS | {1008,1009} |
| 核验状态=待核验 | PASS | {1002,1007} |
| 收样日期区间 | PASS | 第 26 天..第 19 天 → {1004,1005} |
| 组织类型=肝组织（模糊） | PASS | {1001,1003,1004,1007,1008} |
| 内部编号=T-hli01 | PASS | {1001} |
| 操作人=李工 | PASS | {1001,1004,1005,1006,1008,1009} |
| 供体姓名=测试供体甲 | PASS | {1001} |
| 住院号=ZY0000001 | PASS | {1001} |
| 组合（A 医院+有效+外部） | PASS | {1001,1004,1005} |
| 核验抽屉两个出口 | PASS | 抽屉内同时有「判为有效并保存」「判为无效」+ 组织样本提示语 |
| 出口 A 缺内部编号 | PASS | 抽屉不关，出必填提示；补 T-l2a01+收样日期 → code=200，1002 变「有效」且不再置顶 |
| 出口 B 原因为空 | PASS | 弹窗不关，出「判无效必须填原因」；填原因 → code=200，1007 变「无效」 |
| 前端未捕获异常 | PASS | 0 条 |

### ★ 发现（组 2）：样本类别筛选下拉永远是「无数据」——筛不了类别

- 证据：`doc/waves/regression/D2/shots/L2-web/11b-samplekind-dropdown.png`（下拉框正文 = 「无数据」，`el-select-dropdown__item` 数量 0）。
- 根因（代码 + 库 + 接口三方对齐）：
  - `code/plus-ui/src/views/lqg/sample/index.vue:266` → `proxy?.useDict('sample_kind', ...)`
  - 库里真名 `lqg_sample_kind`（`V202609210810__SYS-BASE-001-lqg-dicts.sql:13-15`）：`GET /system/dict/data/type/lqg_sample_kind` → `[('组织样本','tissue'),('类器官','organoid')]`
  - `GET /system/dict/data/type/sample_kind` → `code 200, data []` → 下拉空
  - 后端筛选本身是对的：`api.sh --as staff GET '/lqg/sample/list?pageSize=100&sampleKind=organoid'` → `total=1, ids=['9000001009']`
- 权威：`UI:admin.sample.list` 明写筛选区含「样本类别」。

## 组 1 · 外部（extA）体验路径 + 组 3 · 内部小程序（脚本 `doc/waves/regression/D2/L2-mp-h5.mjs`，shots `.../D2/shots/L2-mp/`）

覆盖方式：H5 dev 9200 + Playwright（★ 微信开发者工具 / 真机**未覆盖**，本沙箱 CLI 要写
`~/Library/Application Support/微信开发者工具/**` EPERM + 需扫码；不冒充）。extA/extF 走产品自带的
调试登录面板；**extB / extC 不在面板的 3 个 seed 里**（`src/api/mock-seeds.ts` 只有 staff/extA/newbie1），
所以用后端 mock 登录（ADR-0008，`xcxCode=mock:<key>`）拿 token 注入 `localStorage['lqg_mp_token']`
—— 同一条 mock 路径，仅绕开面板按钮，如实记账。

结果 28/35 通过。逐条断言值见脚本 stdout（`/tmp` 侧日志），截图 20 张在 shots 目录。关键红项：

### ★ S0-1：小程序首页四个入口都不带 `mode=new` → 填写页变成「没能加载这条样本」

- 复现：extA（或 staff）登录 → 首页点「样本记录信息表」→ URL `#/pages/sample/form`（无 query）→
  页面正文 `样本记录信息表|没能加载这条样本|重试|2026-09-22`，`uni-button` 数 0（没有「提交」）。
- 机制（代码对齐）：`src/pages/index/entries.ts` 的 `ENTRY_FORM_TARGET` 给的是裸路径
  `/pages/sample/form`；`goPage(target)` 不追加参数（`src/router/config.ts`）；
  `sample/form.vue` 的 `onLoad` → `normalizeMode(undefined) === 'view'`（`pages/sample/layout.ts:62-64`），
  `view` 且没有 `id` → `load()` 直接 `failed = true`（`sample/form.vue:150-153`）。
  而 `sample/form.vue:38` / `organoid/form.vue:45` 的注释自己写的是「首页点表进来 → mode=new」。
- 影响：外部**根本无法从首页开始送检**（D2 主流程第 1 步），内部也无法从首页录两种收样；
  四个入口（样本 / 类器官 / 包埋 / 冻存）同一个函数，一起坏。
- 证据截图：`g1-02-entry-sample.png`、`g3-02-entry-sample.png`。
- ⚠️ `doc/verify/fixtures/home-entries-cases.json` 的 `targetCases` 恰好也写的是
  `/pages/sample/form`（L0 fixture 只比字符串，没进页面），所以 L0 全绿也发现不了。

### ★ S0-2：三个 lqg 组件在 `<script setup>` 里引用未声明的 `props` → 状态徽标 / 只读值 / 按钮组全挂

同一根因（`withDefaults(defineProps<...>(), {...})` **没有赋值给 `const props`**，脚本里却写 `props.x`），
运行时 `ReferenceError: props is not defined`：

| 组件 | 行 | 现场 | 影响 |
|---|---|---|---|
| `src/components/lqg/StatusChip.vue` | 34（编译后 23:40 / render 32） | 每次渲染都抛错，DOM 里是 `<!---->` | **历史编辑记录每行的核验状态徽标全没了**（`UI:mp.history` 明写要有徽标）；样本详情页「核验状态」只有标签没有值 |
| `src/components/lqg/FieldRow.vue` | 45 | readonly 分支抛错 | **只读表单一个字段值都不渲染**：内部管理表格页点一行 → `mode=view` 页面正文只剩字段名（`来源单位|*|供体姓名|性别|…|内部编号|有无固定|…`），没有 T-hli05 / 肝组织 / 日期等任何值 |
| `src/components/lqg/SegButtons.vue` | 23 | `pick()` 抛错 | **按钮组点不动**：点「性别·男」后 `--on` 仍是「女」；内部收样的有无固定 / 质控表 / 细胞活率报告同样点不动 |

- 证据：`g1-06`（列表无「待核验」）、`G3-19 {"items":1,"chips":0}`、`G3-20` 控制台原始栈、
  `g3-05-ledger-row-readonly.png`、`g3-07`（只读/可写页都缺值）。
- 反证：`components/ui/IdentityBar.vue`、`biz/UnitGroupPicker.vue`、`biz/MeHeader.vue` 写的是
  `const props = withDefaults(...)`，所以只有这三个组件炸。
- 独立探针 `node doc/waves/regression/D2/L2-probe-fieldrow-segbuttons.mjs`：`VIEW TEXT` 无任何值；`seg on before ["女","有","有","有"] after ["女","有","有","有"]`。

### ★ S0-3：日期/时间选择器永远不弹出 → 内部类器官收样（收样日期必填）新增被卡死

- 复现：staff → `#/pages/organoid/form?mode=new` → 点「收样日期」→
  `{"action":0,"popup":0,"visible":false}`（`.wd-datetime-picker__action` 与 `__popup` 都不存在）。
- 机制：`organoid/form.vue`（`sample/form.vue` 同样）把 `:visible="pickerOpen"` 传给
  `wd-datetime-picker`，但该组件（`node_modules/wot-design-uni/components/wd-datetime-picker/wd-datetime-picker.vue`）
  **没有 `visible` 这个 prop**，内部只用 `popupShow`（`v-model="popupShow"`，第 56 行），
  `:visible` 变成落到根节点的普通 HTML 属性（DOM 上能看到 `visible="true"`）；页面也没有调用组件暴露的
  `open()`（`onPick` 只把 `pickerOpen` 置 true）。→ 选择器面板永不渲染。
- 影响：内部类器官收样 7 项里「收样日期」必填（`organoid/form.vue:286-290`）且只能靠这个面板填 →
  **内部一条类器官收样都建不出来**（脚本 G3-16 库内 0 行）；内部样本的「处理时间」也选不了。
- 证据截图 `g3-11-organoid-datepicker.png`。

### S1-1：工作台「样本类别」筛选下拉永远是「无数据」

见上文组 2。

### S1-2：外部看不到「无效原因」

- 复现：工作台判无效（原因写「L2复验：缺住院号，请补」）→ extA 历史编辑记录：
  列表正文没有这句原因（`SJ…|L** · 肝组织|我|2026-09-22`，连状态徽标也没有，见 S0-2）；
  点这一行进的是 `#/pages/sample/form?id=…&mode=edit`，页面上也没有原因（`topNote` 只在
  「不可改」时出「这条记录现在不能修改」，`sample/form.vue:111-117`），而
  `pages/sample/detail-ext.vue` 的红条（`invalidReason`，第 61-62 / 118 行）在 `sources.ts` 的
  `target()` 里只对「非本人 / 不可改」的行可达 → 本人无效记录**没有任何入口看到原因**。
- 影响：L2 口径「外部在历史编辑记录看到原因并修改重提」拿不到「原因」，只能盲改。
- 证据截图 `g1-09-history-invalid.png`、`g1-10-history-open-invalid.png`。

### 组 1 / 组 3 其余通过项（断言值）

- extA 首页三格顺序 ✓、首页无数字 ✓（digits=[]）；extA 提交成功（库内新行 SJ00000030）✓
- extA 历史编辑记录出现新记录 ✓；工作台判无效 code=200 / 库 invalid ✓；修后保存 → remark 变 + 回 pending ✓
- 工作台判有效 code=200 / 库 `valid|T-l2ext01` ✓；核验有效后外部打开 = `pages/sample/detail-ext` 只读（inputs=0, 提交/保存=0）✓
- extB（同组，token 注入）历史编辑记录看得到 SJ00000030 ✓；打开「只看我提交的」后列表只剩 `SJ90000004`，看不到 extA 这条 ✓
- staff 首页四格 ✓；「我的」内部管理四张只读表入口 ✓
- 表格页两个工作表 = [样本记录, 类器官收样] ✓；样本记录表头 15 列逐字逐序 ✓；类器官收样 8 列 ✓
- 首列冻结 `position:sticky / left:0px` ✓；可横滑（`DIV.uni-scroll-view` overflowX=auto, scrollWidth 958 > clientWidth 390）✓
- 页面无「新增/保存」按钮、底部只有置灰「导出 Excel」（`disabled` 真）✓；点一行进 `mode=view` 且无输入/无保存 ✓
- 内部历史编辑记录点本人有效样本 T-hli05 → edit 可写 → 改备注保存：库 `update_by=9000000101`、remark 变、该行排到最前 ✓
- 内部类器官收样页 7 项字段齐 ✓（但填不了日期，见 S0-3）
- extC 类器官收样正好三项（来源单位/类器官类型/备注，没有内部字段）✓；提交后库 `pending|external|organoid` ✓
