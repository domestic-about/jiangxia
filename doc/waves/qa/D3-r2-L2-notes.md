# D3 r2 — L2 (端侧 UI) 工作笔记

范围：三组。① 工作台（列表/抽屉/核验抽屉/提示列/三导出）② 小程序内部 ③ 小程序外部 extA + 工作台核验闭环。
独立复验：#145（r1 S0，核验抽屉调用顺序）；首验：#147（verify 模式可填项静默丢弃）。

## 环境（我起的，按 PID 关）
- 后端 8081 PID 69862（`bash .tmp/run-backend.sh`，log `.tmp/r2l2b-backend.log`）
- plus-ui 8082 PID 69904，H5 9200 PID 69939
- 反 stale：见下方「反 stale」节

## 进行中

## 反 stale 核验
- 后端 jar `ruoyi-admin/target/ruoyi-admin.jar` mtime **2026-09-22 10:13**；`find ruoyi-lqg/src ruoyi-admin/src -newer jar` = **0 个**；
  8081 PID **69862** `lsof -p` 命中该 jar（2 行）；启动成功日志 `11:14:25`（晚于 jar mtime）。jar 内 `EmbedVerifyBo.class` mtime 同为 10:13。
- 前端：plus-ui 8082 / H5 9200 都是 **dev server 直跑源码**（非产物），无需比对构建产物。
- Flyway `Current version of schema "public": 202609231110`（新版）；本片脚本每条自己 reseed。

## 组 1 工作台：24/24 绿（脚本 `L2-g1-web.mjs`，log `.tmp/r2l2b-g1b.log`，截图 `L2-shots/r2-g1-web/`）
- 复用 r1 脚本（本片自己的资产），**未改任何期望**；先 `reseed.sh --yes` 再单跑（g1 自己不 reseed）。第一次跑登录 30s 超时（dev server 冷启动），热身后重跑全绿。
- G1A：5 行 = 库 5 行；2006 置顶「外部·待核验·待核验」，行 class `lqg-embed__row-pending`，`getComputedStyle` 底 **rgb(252,241,218)**；对照行 rgb(255,255,255)；表头 = 内/外部+核验状态+模板 16 列（`mark的表达情况` 逐字）。
- G1B：2006「核验」→ 抽屉；「判为有效并保存」`disabled=true`、`is-disabled`；alert「所挂样本还没核验有效（当前状态：待核验），不能判为有效」；悬停 tooltip 同文案。
- G1C：编辑抽屉回填 T-E01-1、HE/IF/IHC/OTHER/NONE、marker Ki67/CK19；顶部「最后修改：从未修改」（r1 已记 S3 文案观感）。
- G1D：三导出都真下到文件且**有真行**：`embed-export.xlsx` 4431B / sheet1 **6 行**（表头+5）、suggested `石蜡包埋送样记录.xlsx`；`sample-tissue-export.xlsx` 4619B / **9 行**；`sample-organoid-export.xlsx` 3905B / **2 行**。三个都是 PK 头。
- G1E：表头「切片染色」；1001 = `石蜡块 2|已切片|HE|IHC`；1002 = `—`；悬停 popper = `T-E01-2|—|T-E01-1|2026-08-27`；点徽标 → `/embed?sampleId=9000001001`。

## 组 2 小程序内部：32/32 绿（脚本 `L2-g2-mp-int.mjs` 自 reseed，log `.tmp/r2l2b-g2.log`，截图 `L2-shots/g2-mp-int/`）
- 首建：新 id `2102236086011957250`（雪花），sample=9000001001，create_by=9000000101，verify_status=valid；POST body `"tissueReceiveTime":"2026-08-23"`（**yyyy-MM-dd 字符串**，不是 ms）。
- 必填项真的都操作了：页面标 `*` 的是「选择样本 + 石蜡块编号」，都填了才提交成功（未出现「操作了仍提交不了」）。
- 内部选样本弹层 = 6 条已核验有效样本，**没有** pending 的 SJ90000002；选完自动带出前两个工序时间。
- 表格页：页签 样本记录/类器官收样/石蜡包埋；冻结格 = 石蜡块编号（2006 = `SJ90000002` + `待核验 · ○○○○○○○○`，行 class `lqg-ledger__row--pending`）；行内 input=0（只读）；点行进 `mode=view` 只读详情（可编辑输入框=0），有「修改」入口。
- 历史（内部）默认 6 行 = 全中心：`T-r1l2-01|有效|我|修改` / **`SJ90000002 · 待核验|待核验|组织|王医生|新增`** / T-E04-1 / T-E02-1 / T-E01-2 / T-E01-1；开关默认关，打开后 6→3。
  → 王医生那行 = 台账 **issue #137**（EMBED `sort=recent` 不收窄到内部人员），**doc-drift 记账，不判产品红**。
- 补填脱水时间：日期选择器写回 `2026-09-22`；PUT body `"dehydrateTime":"2026-09-22"`；库里 `dehydrate_time=2026-09-22`、`update_by=9000000101`、1001 名下石蜡块仍 3 行（不新增）；圆点 `●●○○○○○○` → `●●○●○○○○`（+1，别的行不变）；改完排历史最前、经手人=「我」。
- 样本修改页「给这个样本加石蜡块」已点亮 → `/pages/embed/form?mode=new&sampleId=9000001001`，填写页样本已带好 `T-hli01 · SJ90000001`。

## 组 3 小程序外部 + 核验闭环：21/21 绿（脚本 **`L2-r2-g3-mp-ext.mjs`**，log `.tmp/r2l2b-g3.log`，截图 `L2-shots/r2-g3-mp-ext/`）
脚本 = r1 的 `L2-g3-mp-ext.mjs` 复制品，**只改过期期望**（见下「#145」节）：shots 目录改 `r2-g3-mp-ext`、BLOCK 改 `T-r2l2-E1`、核验人不再写死、G3B-08 改正向、删 G3B-09 兜底、**新增 G3B-11 失败路径**。r1 脚本一字未动。
- extA 首页三格（无冻存）→ 填写页**只有** 选择样本/样本类型/类器官来源类型（无内部字段）→ 选样本弹层 = SJ90000002、SJ90000001（排除无效的 90000003），无内部编号，供体姓名掩码 → 选 1001 提交 → 落库 `2102236554213724161|9000001001|pending|-|9000000111|external`，POST body `{"sampleId":"9000001001","sampleType":"组织","organoidSourceType":""}`。
- 工作台：该行**置顶**（`外部|待核验|待核验|T-hli01|...`），计算样式 **rgb(252,241,218)**；核验抽屉 alert「所挂样本已核验有效，可以判为有效」、按钮**可点**（与 2006 置灰对照）；编号空点一次 → 库里仍 `pending|-`（前端拦住）。
- 判有效 → `PUT /lqg/embed/2102236554213724161/verify -> 200`，库 `valid|T-r2l2-E1|9000000100`，toast「已判为有效」，抽屉关。
- extA 历史石蜡包埋页签首行 = `T-r2l2-E1|有效|组织|我|新增`；extA 1001 详情 3 张 `.ecard`，`nos=["T-E01-1","T-E01-2","T-r2l2-E1"]`。
- extB（token 解出 `loginId=app_user:9000000112`，与 extA 9000000111 不同，身份真实生效）打开 1001 详情同样 3 张卡，`nos` 同；标识 = 石蜡块编号。
- 无 JS 运行时报错（r1 那条被拒 PUT 的未捕获 rejection 随 #145 修复一并消失）。

## #145 独立复验（真实点击路径，不是复读旧断言）
**结论：已修（r1 的 S0 关闭）。** 两条分支各走一次真实点击：
- **判为有效**（`L2-r2-g3-mp-ext.mjs` G3B-08/G3B-06）：点「判为有效并保存」→ 网络**只有** `PUT /lqg/embed/{id}/verify -> 200`，**没有**任何 `PUT /lqg/embed` 预保存；库 `valid|T-r2l2-E1|9000000100`（=登录身份 lqgadmin）；toast「已判为有效」；抽屉关闭。
- **失败必 toast + 不关抽屉**（同脚本 G3B-11，**r2 新增**）：先填一个库里已存在的编号 `T-E01-1` 再点 → `PUT .../verify -> 500 {"msg":"石蜡块编号「T-E01-1」已存在，请换一个"}`，页面出现该文案的 `el-message`（2 条），抽屉**仍开着**，库里仍是 `pending|-`（原样未动）。
- **判为无效**（`L2-r2-issue145-invalid.mjs`）：对 seed 的 2006（所挂样本未核验 → 判有效置灰）点「判为无效」→ 空原因不发请求、库内不变；填原因后 → `PUT /lqg/embed/2006/verify -> 200`（无预发普通保存），库 `invalid|测-r2-145-无效原因|9000000100`，toast 可见。
- 源码佐证（只认代码）：`EmbedDrawer.vue` 的 `preSaveIfEditable()` 在 `!shouldSaveBeforeVerify(form)` 时早返回，而 `shouldSaveBeforeVerify = isEditable = verifyStatus==='valid'`；核验抽屉只开 `isExternalPending`（pending/invalid）的行 → 恒早返回。
- r1 那个未捕获 rejection（pageerror）在本轮两脚本里都**不再出现**（G3Z-99/#145-06 均无报错）。
- 探针过期期望修正（**只改我复制出来的 r2 脚本，r1 脚本一字未动**）：`L2-r2-g3-mp-ext.mjs` 里 ①核验人不再写死 `9000000101`，改成动态读 `sys_user.user_name='lqgadmin'` 的 user_id（=9000000100）；②G3B-08 由「缺陷必须存在」改成正向断言「发了 /verify 且没有 PUT /lqg/embed」；③删掉 G3B-09 的 API 兜底（UI 已自己核验成功，兜底成 valid→valid no-op）；④新增 G3B-11 失败路径；shots 目录改 `L2-shots/r2-g3-mp-ext/`、编号改 `T-r2l2-E1`。

## #147 首验（脚本 `L2-r2-issue147.mjs`，log `.tmp/r2l2b-147c.log`，截图 `L2-shots/r2-issue147/`）→ **9/9，结论：展示了却丢弃 = 真**
- 前置：extA 走真实 UI 建一条 pending 外部送样（挂已核验有效的 1001）→ `2102237423801995265`，判有效按钮可点。
- **① DOM 取证**（verify 模式，`.el-drawer` 内 16 个 form-item，**15 个可填且全部 `disabled=false / readonly=false`**）：
  `选择样本`(只读文本) / 石蜡块编号 / 样本类型 / 类器官来源类型 / 包埋人 / **组织收样时间 / 组织处理时间 / 琼脂糖包埋样本时间 / 脱水时间 / 琼脂糖包埋样本送样时间 / 石蜡包埋时间 / 切片时间** / **染色**（HE染色·IF染色·IHC染色·其他·无染色 五个可点）/ **marker 表达**（可填名称 + 阴性/弱表达/强表达）/ **操作人** / **备注**。段落 = 包埋信息 + 工序时间 + 染色与 marker + 操作与备注。
- **② 真填进去**（UI 全部接受）：`{"dehy":"2026-09-01","stain":["HE染色"],"marker":"M-147","op":"测-147操作人","remark":"测-147备注","block":"T-r2-147"}`。
- **③ 点「判为有效并保存」**：请求体 = `{"action":"valid","paraffinBlockNo":"T-r2-147"}`（**只有契约那 3 键的子集**）；核验本身成功。
- **④ 库内对照**：`t_lqg_embed` = `valid|T-r2-147|NULL|NULL|NULL|NULL|NULL`（dehydrate_time / stain_types / operator_name / remark / embed_by 全 NULL），`t_lqg_embed_marker` **0 行** → **填的值全部静默丢弃，无任何提示**。
- **⑤ 提示文案**：抽屉顶部 alert 只说「判为有效要给石蜡块编号（全库唯一），**之后照常补工序与染色**；判为无效必须写原因。」——**没有任何一句说本次填写不会保存**（实测 `#147-07` 断言 `/不保存|不会保存|请到编辑/` 不匹配）。
- **定级：S2 / ux / EMBED-WEB-001**。依据：契约 `EmbedVerifyBo` 只收 3 键、工序时间全部可空、核验产出（status + 编号）本身正确、无跨 ticket 依赖，核验通过后立刻可从列表点「编辑」补填（G1C 已验编辑抽屉正常）→ 不满足 S1「影响另一个 ticket 实现路径 / 产出明显是坏的」；但「15 个可编辑控件 + 一句含糊提示 → 用户填完保存全丢」是真实的产品体验缺陷，建议修法 = verify 模式下隐藏或禁用这些控件，或把提示改成明说「本次填写不保存」。

## 其他观测 / 未覆盖
- 内部历史页签出现 extA 建的 `SJ90000002 · 待核验｜王医生` = 台账 **#137**（见 doc-drift）。
- 编辑抽屉对 `update_time` 为空的 seed 记录顶部显示「最后修改：从未修改」（2001 等）；有修改的显示「最后修改：李工 · 2026-09-22 11:18:35」。文案自洽，S3 记账。
- **微信开发者工具 / 真机未覆盖**：沙箱内 wx devtools 跑不通（EPERM + 需扫码）→ 小程序侧三组全部用 **H5 + Playwright**（真后端 8081 + 真库 5433 + 真 seed）等价覆盖。
- 反 mock：无 mock 数据兜底；小程序调试登录面板是产品自带 dev 功能（`VITE_MOCK_LOGIN=1`）。

