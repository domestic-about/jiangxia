# D3 r1 — L2 (端侧 UI) 工作笔记

范围：工作台石蜡包埋列表/抽屉/核验抽屉/提示列/三导出；小程序内部流程；小程序外部 extA 流程。

## 环境
- 后端 8081 `bash .tmp/run-backend.sh`
- plus-ui 8082, miniapp H5 9200

## 进行中

## 组 1 工作台：24/24 绿（脚本 `doc/waves/regression/D3/L2-g1-web.mjs`，截图 `doc/waves/regression/D3/L2-shots/g1-web/`）
- G1A 列表：5 行 = 库 5 行；2006 置顶（外部·待核验·待核验），DOM class `lqg-embed__row-pending`，`getComputedStyle` 行底色 **rgb(252,241,218)**（浅黄）；对照组非待核验行 rgb(255,255,255)。表头 = 内/外部 + 核验状态 + 模板 16 列（`mark的表达情况` 逐字）。
- G1B 核验抽屉：2006 行「核验」→ 抽屉；「判为有效并保存」`is-disabled` 且 `disabled=true`；alert 文案「所挂样本还没核验有效（当前状态：待核验），不能判为有效」；悬停 tooltip 同文案。
- G1C 编辑抽屉：T-E01-1 回填（input value=T-E01-1），染色 HE/IHC 高亮，顶部「最后修改：从未修改」（2001 update_time is null → 文案值待商榷，S3 记账）。
- G1D 三个导出按钮：都真下到文件（PK 头）→ `embed-export.xlsx` 4431B（suggested 石蜡包埋送样记录.xlsx）、`sample-organoid-export.xlsx` 3905B、`sample-tissue-export.xlsx`。按钮文案 = 导出石蜡包埋送样记录 / 导出样本记录信息表 / 导出类器官收样记录。
- G1E 提示列：表头「切片染色」；1001 = 石蜡块 2|已切片|HE|IHC；1002 = 「—」；悬停 popper = `T-E01-2|—|T-E01-1|2026-08-27`；点徽标 → `/embed?sampleId=9000001001`。
- 反 stale：jar mtime 2026-09-22 10:13，`find ruoyi-lqg/src -newer jar` 为空；后端 PID 起于 10:17 持该 jar。

## 组 2 小程序内部：32/32 绿（脚本 `L2-g2-mp-int.mjs`，截图 `L2-shots/g2-mp-int/`）
- 首建：新 id=2102223016846004225（雪花 id，非 9000002xxx 段），sample=9000001001，create_by=9000000101，verify_status=valid；POST body `tissueReceiveTime":"2026-08-23"`（**字符串 yyyy-MM-dd**，不是 ms 时间戳 → 选样本带出的两个工序时间也转换正确）。
- 内部选样本弹层 = 6 条已核验有效样本，**没有** pending 的 SJ90000002。
- 表格页：页签 = 样本记录/类器官收样/石蜡包埋；石蜡包埋 5+1 行；冻结格 = 石蜡块编号（2006 显示 `SJ90000002` + `待核验 · ○○○○○○○○`，行 class `lqg-ledger__row--pending`）；行内 input 数 = 0（只读），无新增按钮。
- 点行进 `mode=view` 只读详情，`.emb__edit`「修改」入口存在，可编辑输入框 = 0。
- 历史（内部）默认 6 行 = 全中心：T-r1l2-01(我) / **SJ90000002·待核验｜王医生｜新增** / T-E04-1(测试管理员) / T-E02-1(我) / T-E01-2(测试管理员) / T-E01-1(我)；开关默认关；打开后 6→3。
  → **王医生那行 = issue #137 已知规格冲突（EMBED sort=recent 不收窄到内部人员）**，照实记录，不判产品红。
- 补填脱水时间：日期选择器 open() → 完成 → 字段 `2026-09-22`；PUT body `"dehydrateTime":"2026-09-22"`；库里 `dehydrate_time=2026-09-22`、`update_by=9000000101`、1001 名下石蜡块仍 3 行（不新增）；圆点 `●●○○○○○○` → `●●○●○○○○`（+1，其余行不变）；改完排历史最前、经手人显示「我」。
- 样本修改页「给这个样本加石蜡块」已点亮 → `/pages/embed/form?mode=new&sampleId=9000001001`，填写页样本已带好 `T-hli01 · SJ90000001`。

## 组 3 小程序外部 + 工作台核验闭环：19/21（2 红同一条 S0）
脚本 `L2-g3-mp-ext.mjs`，截图 `L2-shots/g3-mp-ext/` 与 `L2-shots/g3b-verify-probe/`。
- 绿：外部首页三格（无冻存）；外部填写页**只有** 选择样本 + 样本类型 + 类器官来源类型（rows=["选择样本","样本类型","类器官来源类型"]，无石蜡块编号/脱水时间/染色/marker/包埋人/操作人/备注）；外部选样本弹层 = SJ90000002、SJ90000001（排除无效的 90000003），无内部编号，供体姓名掩码 `测**`；选 1001 → POST /mp/ext/embed → 落库 `pending|-|external|create_by=9000000111`。
- 绿：工作台石蜡包埋页该行置顶（首行 = 外部·待核验·待核验·T-hli01），计算样式浅黄 `rgb(252, 241, 218)`；核验抽屉 alert「所挂样本已核验有效，可以判为有效」、按钮**可点**（与 2006 的置灰成对照）；空编号点一次不落库（校验拦住）。
- **红（S0）**：填好唯一石蜡块编号再点「判为有效并保存」→ 只发出 `PUT /lqg/embed`（= `saveBeforeVerify()` 的预保存），后端回 **400「待核验 / 无效的送样不能通过普通保存修改（核验与改判只走 PUT /lqg/embed/{id}/verify）」**，异常在 `verifyEmbed()` 之前抛出 → **`PUT /lqg/embed/{id}/verify` 从未发出**，库内仍 `pending|-|noverify`，抽屉不关、`el-message` 空（静默失败），并抛一个未捕获 rejection（pageerror）。`submitInvalid()` 是同一段 `saveBeforeVerify()` 前置，判为无效同样走不通（源码证据）。
- 定位对照：直接 `api.sh --as staff PUT /lqg/embed/{id}/verify {"action":"valid",...}` → 200，库里 `valid|T-r1l2-E1|9000000101` → **后端核验端点本身是好的，缺陷在前端抽屉的调用顺序**。
- 为让下游卡片渲染能被独立检查，用 API 兜底完成核验（明示为兜底，不是 UI 通过）：extA 历史页签出现 `T-r1l2-E1|有效|组织|我|新增`；extA 与 extB 打开 1001 详情都看到 3 张 `.ecard`，`.ecard__no` = `["T-E01-1","T-E01-2","T-r1l2-E1"]`（标识=石蜡块编号）。extB 用 `mpToken('extB','13800000012')` 真后端 mock 登录后访问。

## 其他观测
- 内部历史页签出现 extA 建的 `SJ90000002 · 待核验｜王医生｜新增` = 台账 issue #137（EMBED `sort=recent` 不收窄到内部人员）；按指示记为 doc-drift 观测。
- 石蜡包埋编辑抽屉里 `update_time` 为空的记录顶部显示「最后修改：从未修改」（2001）；有修改的显示「最后修改：李工 · 2026-09-22 10:26:39」。文案自洽，S3 记账。
- 反 stale：jar mtime `2026-09-22 10:13:47`，`find ruoyi-lqg/src ruoyi-admin/src -newer jar` = **0 个**；8081 PID **13050** 由 `lsof -p` 确认持有该 jar；进程启动于 `10:17:44`（后台作业日志）晚于 jar mtime；Flyway `Current version of schema "public": 202609231110`。
- 未覆盖：微信开发者工具 / 真机（沙箱跑不通，EPERM+扫码）→ 小程序侧全部用 H5 + Playwright 等价覆盖。
