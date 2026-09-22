# D2 r2 L2 笔记（边跑边写）

## 环境 / 反 stale（我自己核的）
- 起 8081 前 `lsof -ti tcp:8081` 为空 → 本片 `bash .tmp/run-backend.sh` 起（**受管后台作业 bash-254，不是 nohup**），日志 `.tmp/r2l2-backend.log`，
  日志里 `RuoYi-Vue-Plus启动成功` + `MockLoginGuard - mock 登录已开启（profile=[dev]）` 时间戳 **2026-09-22 01:26:51**。
- jar：`code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar` mtime **Sep 22 01:19**（晚于 1e8ea8c 返工 commit 01:16:43）；
  `find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer <jar>` → **空**（无源码比 jar 新）。
- **8081 PID = 41338**：`lsof -ti tcp:8081` = 41338，`lsof -p 41338 | grep -c ruoyi-admin.jar` = **2**（进程确持该 jar 的 fd）。
- 前端：miniapp H5 dev **9200**（`VITE_MOCK_LOGIN=1`，受管作业 bash-255）、plus-ui dev **8082**（受管作业 bash-256），均 `curl -o /dev/null -w %{http_code}` = 200。

## 「编辑过的脚本不能当证据」这条的核对
- `git log --oneline -- doc/waves/regression/D2/L2-mp-h5.mjs L2-web-plusui.mjs` → 两个脚本最后一次改动都在 **1e8ea8c（返工 commit）**。
- 返工 commit 里 QA 脚本的两处改动：
  ① **G3-20 断言正负号翻转**（`chipErr.length > 0` → `=== 0`）；② **WEB-L2-06b 由「页面请求的假字典名 sample_kind 为空」改成「真字典名 lqg_sample_kind 有 2 条」**。
- 我的判定：`lqg_sample_kind` 是权威真名（`doc/authority/field-ssot.yaml:33` 定义 key、`:137` 写 `dict: lqg_sample_kind`，迁移 `V202609210810` 里有），
  页面原写 `sample_kind` 确实是产品 bug → **修复方向正确，不算「调成迎合实现」**；但改完后 WEB-L2-06b **不再测页面**（只测库里的字典），
  G3-20 也退化成「console 没异常」——两条回归断言对页面失去约束力 → 记 **S3 harness**，并自写 R2W-06a/06b、R2M-06 正面补上。

## 组 2（工作台样本总表）—— `L2r2-web.mjs`（自写，期望集合用 psql 现算）
**26/26 通过**（`.tmp/r2l2-web.log` 为 reseed 后那次；`.tmp/r2l2-web2.log` 为最终 DB 状态那次）。截图 `shots/L2r2-web/`。
期望值不是抄的：每项筛选的 id 集合由脚本自己 `psql SELECT` 现算后与 UI 列表对照（来源单位/组别/样本类别/提交来源/核验状态/收样日期区间/组织类型 LIKE/内部编号/操作人/组合）；
只有加密列的「供体姓名 / 住院号 精确」按 `doc/verify/README.md` 的 seed 表推断单行。
- 待核验置顶：前 N 行集合 == 库内 `verify_status='pending'` 集合；第 3 行起无 pending 类；计算样式 `background-color = rgb(252, 241, 218)` = `--lqg-warn-soft`。截图 `10-sample-list.png`。
- 11 项筛选 UI 驱动逐项 == 库内现算（含组合 A 医院+有效+外部={1001,1004,1005}）。截图 `11-filter-1..11.png`。
- **r1-S1-1 复验**：样本类别下拉 = `["组织样本","类器官"]`（UI 实测，`11b-samplekind-dropdown.png`）；1009 行的「样本类别」列渲染中文「类器官」而非 `organoid`（dict-tag 生效）。
- 核验抽屉两出口：缺内部编号 / 缺原因各被拦一次（抽屉、弹窗都不关）；判有效 → 库 `valid|T-r2w01`；判无效 → 库 `invalid|R2W 独立复验原因：缺住院号`（原因原样）。截图 12/13/14/15。
- **线索复核（工作台列表行是否渲染无效原因）**：UI 行文本里**没有**原因（SJ90000003 行 = `—|SJ90000003|A 医院|组织样本|外部|无效|测试供体丙|…`），同行点「编辑」开抽屉后**原因可见**（`信息不全：缺住院号`）→ 截图 `16-invalid-row-reason.png` / `17-invalid-drawer-reason.png`。权威 `UI:admin.sample.list` 的表格列清单里**没有**无效原因（原因在 `UI:admin.sample.edit` 抽屉）→ 定 **S3 clarify**，不拦门。

## ★ 本轮新发现（S1）：工作台「来源单位」筛选筛不出内部录的样本
`L2r2-probe-sourceunit.mjs`（截图 `shots/L2r2-web/18-sourceunit-via-internalNo.png` / `19-sourceunit-filter-B.png`）：
- 按「内部编号=T-oco01」筛 → 出 SJ90000009，行内「来源单位」列 = **B 大学**（`T-oco01|SJ90000009|B 大学|类器官|内部|有效|…`）。
- 改按「来源单位=B 大学」筛 → 只剩 SJ90000006（`total=1`）。
- 库：`SJ90000009 = internal|9000009002|B 大学|T-oco01`（id 与名称都落在 B 大学）；`source_unit_name='B 大学'` 的可见行 = `SJ90000006,SJ90000009`。
- 接口：`GET /lqg/sample/list?sourceUnitId=9000009002` → `total=1`，只回 SJ90000006。
- 根因：`SampleQueryService.list` 把「组别 / 来源单位」一律走 `t_lqg_ext_profile`（提交人外部档案）取 user_id 集合，再用 `submitter_id IN (...)` 收窄；
  注释的理由「样本行上没有 group_id」只对组别成立，**来源单位在样本行上有 `source_unit_id`/`source_unit_name`**。
  → `submit_source='internal'` 的行（提交人是内部账号、无外部档案）永远筛不出来。
- 为什么以前没暴露：旧 accept 只测「来源单位=A 医院」，而 A 医院下没有内部样本；seed 里内部样本 1008 没单位 id、1009 只在 B 大学。
- 影响：老师按来源单位筛选会拿不到界面明明标着该单位的样本；`FLOW:F-SAMPLE-02.step5` 的导出范围 = 当前筛选结果 → D3 导出会继承同一漏行。
- 回归资产已加硬：`L2r2-web.mjs` 的 **R2W-04.2**（来源单位=B 大学，期望从库内现算；reseed 后跑 = FAIL，`26/28`）。

## 组 1 / 组 3（小程序 H5 等价）—— `L2r2-mp.mjs` + `L2r2-mp-extra.mjs`
**39/39 + 6/6 通过**（`.tmp/r2l2-mp.log` / `.tmp/r2l2-mp-extra.log`）。截图 `shots/L2r2-mp/`、`shots/L2r2-mp-extra/`。
与 r1 脚本的三个实质差别：① 主线**从首页入口点进去**跑完（不再另开 `?mode=new` 抄近路）；② 内部类器官收样**真的操作「来源单位」**（底部弹层选单位，r1 harness 漏了这一步）；③ 五条 r1 拦门项逐条正面复验。
- 组 1（extA 体验路径）：三格无数字 → 首页点样本记录信息表（URL `?mode=new`）→ **在这一页填完直接提交成功**（库 `external|pending`）→ 历史编辑记录出现「待核验」→ 工作台判无效 → 外部**历史行内**看到「无效原因：…」→ 点开是 edit 表单且顶部红条同原因 → 改后保存（库 `pending` 且 `invalid_reason` 清空）→ 工作台判有效（`valid|T-r2m01`）→ 外部详情只读（`pages/sample/detail-ext`，0 输入 0 提交/保存，正文有送检单号/供体姓名/组织类型/来源单位）→ extB 同组看得到、打开「只看我提交的」后看不到。
- 组 3（staff 小程序）：四格顺序 → 首页点「样本记录信息表」进 `?mode=new` 并**真提交一条**（`internal|tissue|valid`，`shots/L2r2-mp-extra/x-03`）→ 我的有「内部管理」四表 + 历史编辑记录 → 表格页两工作表表头 **== 甲方模板原件第 1 行**（脚本自己跑 `xlsx_header.py --print-header` 取模板，把冻结列「内部编号」提前 + 末尾追加「切片染色」；样本 15 列 / 类器官 8 列）→ 首列 `position:sticky left:0px` → 可横滑（`overflow-x:auto` scrollWidth 958 > clientWidth 390）→ 没有新增/保存（只有置灰导出）→ 点一行 `mode=view` 只读、**有字段值**、每个库内有值的按钮组恰好 1 项选中 → 待核验外部样本冻结列显示 `SJ90000007|待核验 · 外部` → 历史编辑记录点本人有效样本 T-hli05 改备注保存成功（`update_by=9000000101`）且排到最前 → 性别/有无固定/质控表/细胞活率报告按钮组点得动（`--on` 切换，`细胞活率报告 off->ON`）→ 类器官收样 7 项齐、收样日期 `open()` 弹层可见、来源单位底部弹层选中、提交成功（库 `internal|organoid|valid`）→ 该条可从历史编辑记录改（类器官类型 `R2复验类器官` → `R2复验类器官已改` 落库）→ extC 三项提交后库 `pending|external|organoid`。
- 首页 7 个宫格逐格落点（`L2r2-probe-entries.mjs`，`.tmp/r2l2-entries.log`）：D2 的两格（样本记录 / 类器官收样）内外都是 `?mode=new` 且有「提交」、无「没能加载」；石蜡包埋 / -80 冻存落 `embed/form` `cryo/form` 占位页（D3/D4 范围，无提交按钮、无错误态，不作 D2 判据）。
- 只读详情里点右上「修改」→ 页面变可写（10 个输入 + 「保存」），URL 仍 `mode=view`（刷新会退回只读）——功能可用，只是 URL 不同步，仅记账不入 issue。
- **未覆盖（如实记账）**：微信开发者工具（CLI 要写 `~/Library/Application Support/微信开发者工具/**`，EPERM + 需扫码）与真机 / 体验版**全部未跑**；小程序侧一律为 H5 dev(9200, `VITE_MOCK_LOGIN=1`) + Playwright 等价覆盖。extB / extC 不在调试面板的 3 个 seed 里（`src/api/mock-seeds.ts`），改用后端 mock 登录（ADR-0008，`xcookie=mock:<key>`）拿 token 注入 `localStorage['lqg_mp_token']` —— 走的是产品自己的 mock 路径，只绕开面板按钮。工作台只跑了 `lqgadmin` 一个身份。
