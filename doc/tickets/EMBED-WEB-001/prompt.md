---
ticket: EMBED-WEB-001
track: EMBED
phase: D3
size: M
req_refs:
  - REQ-EMBED-001
  - REQ-EMBED-002
  - REQ-EMBED-003
  - REQ-EMBED-004
  - REQ-SAMPLE-013
  - REQ-SAMPLE-016
depends_on:
  - EMBED-MODEL-001
  - SAMPLE-WEB-001
touches:
  - code/plus-ui/src/views/lqg/embed/**
  - code/plus-ui/src/api/lqg/embed/**
  - code/plus-ui/src/lang/lqg/embed.*.ts
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/embed/export/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260923111*__EMBED-WEB-001-*.sql
  - code/plus-ui/src/views/lqg/sample/index.vue
adr_refs: []
blueprint_refs:
  - UI:admin.embed.list
  - FLOW:F-EMBED-01.step5
  - FLOW:F-EMBED-01.step7
  - FLOW:F-SAMPLE-02.step5
  - FLOW:F-SAMPLE-02.step8
accept:
  - name: "导出的 Excel 与甲方模板原件逐字对表头；行数 = 未删的石蜡包埋送样记录数（含待核验的外部送样）；样本编号、染色、marker 的导出格式逐格钉死；带筛选导出只出筛选结果"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-embed.xlsx /tmp/lqg-embed-f.xlsx &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --out /tmp/lqg-embed.xlsx POST /lqg/embed/export &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows 5 --find "石蜡块编号=T-E01-1" --expect "样本编号=T-hli01,染色=HE染色、IHC染色,mark的表达情况=Ki67：强表达；CK19：阴性,包埋人=李工" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --find "石蜡块编号=T-E04-1" --expect "染色=其他（Masson）,mark的表达情况=弱表达" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --find "石蜡块编号=T-E02-1" --expect "染色=无染色,mark的表达情况=" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id AND s.del_flag='0' WHERE e.del_flag='0'" | head -1)" &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-embed-f.xlsx POST '/lqg/embed/export?internalNo=T-hli01' &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed-f.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows 2 &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-embed-f.xlsx POST '/lqg/embed/export?verifyStatus=valid' &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed-f.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows "$(bash doc/verify/api.sh --as staff GET '/lqg/embed/list?verifyStatus=valid&pageSize=100' | jq '.total')"
    counterfeit: |-
      直接拿实体 VO 导出 → 表头是「石蜡块编号 / 样本ID / …」而且多出创建时间、核验状态等列，与模板不等红。两侧不同源：一侧是系统导出的文件，一侧是甲方发来的模板原件。
      「mark的表达情况」被顺手「修正」成「marker 的表达情况」→ 逐字比对红。甲方拿导出去对他们的旧台账，列名变了就对不上。
      染色导出了字典 value（HE,IHC）而不是中文标签 → 红。
      导出时偷偷只导有效的（和列表口径不一致）→ 第 1 段行数 4≠5 红；导出不带筛选条件（永远导全量）→ 后两段行数红。
      先 rm 旧文件：读到上一次导出的文件骗不过去。
  - name: "菜单落在 5300 段、可达、按钮权限含核验；页面已建且接上核验接口；染色切换逻辑过 fixture（无染色互斥）；样本表（CR-20260924-10 拆成样本记录信息表 / 类器官收样记录两页，同一个列表组件）的「石蜡包埋」入口已点亮；核验抽屉的两个出口都把补填段随 fill 带上、判无效只带样本类型两项（CR-20260923-09，V02b）"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5310" --eq "5310:embed:lqg/embed/index" &&
      python3 doc/verify/db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5310 AND menu_type = 'F'" --col-set "lqg:embed:list,lqg:embed:query,lqg:embed:add,lqg:embed:edit,lqg:embed:remove,lqg:embed:export,lqg:embed:verify" &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/embed/index")] | length == 1' &&
      cd code/plus-ui && grep -q 'doc/verify/fixtures/stain-toggle-cases.json' src/views/lqg/embed/stain.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/embed/stain.fixture.spec.ts &&
      pnpm vitest run src/views/lqg/embed/stain.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-stain-web.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-stain-web.json &&
      grep -q "sampleId" src/views/lqg/embed/index.vue && grep -rqE '/verify' src/api/lqg/embed/ && grep -rq 'sampleVerifyStatus' src/views/lqg/embed/ &&
      ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/embed/verifyFill.spec.ts &&
      pnpm vitest run src/views/lqg/embed/verifyFill.spec.ts --reporter=json --outputFile=/tmp/lqg-embed-fill.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 8' /tmp/lqg-embed-fill.json &&
      grep -q 'fill: fillPayload()' src/views/lqg/embed/EmbedDrawer.vue && grep -q 'fill: invalidFill(fillPayload())' src/views/lqg/embed/EmbedDrawer.vue &&
      test "$(grep -cE 'await verifyEmbed\(' src/views/lqg/embed/EmbedDrawer.vue)" = 2
    counterfeit: |-
      「无染色」做成普通多选项 → fixture 第 5、6 条红：页面上能同时选中「HE染色」和「无染色」，提交才被后端打回。
      点了字典外的值（fixture 最后一条 PAS）没被忽略 → 红。
      菜单 path 留空 → 第 1 段红。后端加了 lqg:embed:verify 却没 seed 这个按钮 → 第 2 段集合红，普通内部人员点「判为有效」没反应。
      核验抽屉没接核验接口（拿编辑接口顶替）→ /verify 那段红；「判为有效」不看所挂样本状态 → sampleVerifyStatus 那段红。
      核验抽屉里补填的工序、染色、marker 显示成可编辑、请求里却没带（CR-20260923-09 之前的形态，issue #147：点保存被静默丢弃）→ `fill: fillPayload()` 那段红；判无效也把实验室补填项一起送（后端会 400）或静默丢掉却不提示 → `invalidFill(...)` 那段与 verifyFill.spec 红；另写一个绕开这两处的 verifyEmbed 调用 → 最后的计数不是 2 红。后端「同一事务、判无效只收两项、被拒库里不变」的行为断言在 EMBED-MODEL-001 accept 6。
---

# EMBED-WEB-001 · 工作台 · 石蜡包埋送样记录：列表（内外部与核验状态、待核验置顶）、录入与核验抽屉、按模板导出 Excel

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/EMBED` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**EMBED-MODEL-001**、**SAMPLE-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 甲方模板原件 `_input/templates/石蜡包埋送样记录模板.xlsx` 第 1 行——导出的表头要和它**逐字、按序**一致（含 `mark的表达情况` 这个原样的列名）
  - 外部送样的核验（CR-20260917-05）：看 `UI:admin.embed.list` 的后半段；核验抽屉与样本表的核验抽屉同一个样子（两个出口）。CR-20260924-10 起小程序内部人员也能核验石蜡包埋送样（`UI:mp.verify.embed`，页面归 SAMPLE-MP-002），调同一个 `PUT /lqg/embed/{id}/verify`、同一份规则；侧边菜单红点与首页卡片、小程序首页「待处理」读同一个 `pendingEmbeds`
- [ ] 口径复述（本张最容易做反的）：
  1. 导出列序 = 模板列序 16 列，不多不少；「样本编号」一列填内部编号。待核验 / 无效的外部送样也导出（石蜡块编号、样本编号两格为空），软删的不导——与样本导出同一口径。
  2. 染色导出成中文标签用顿号连接（`HE染色、IHC染色`）；「其他」导出成 `其他（具体名称）`；marker 拼成 `名称：表达` 用中文分号连接，没名称的只写表达。
  3. 染色按钮组复用 SAMPLE-WEB-001 的 `SegButtons`（`multiple` + `exclusive=['NONE']`）；切换逻辑用 `doc/verify/fixtures/stain-toggle-cases.json` 做单测。
  4. **核验抽屉里「判为有效并保存」在所挂样本还没核验有效时置灰并写明原因**（读行里的 `sampleVerifyStatus`），不是点了再等后端报错。
  5. **核验抽屉里补填的内容真的会保存**（`UI:admin.embed.list`、`FLOW:F-EMBED-01.step7`，CR-20260923-09，V02b）：「判为有效并保存」把抽屉里的补填段（工序时间、包埋人、染色、marker、操作人、备注以及样本类型、类器官来源类型）随核验请求的 `fill` 一起送，与结论同一次保存；「判为无效」只带样本类型、类器官来源类型的更正，改过的实验室补填项在原因弹窗里明说「这次不会保存」。以前抽屉里能填、保存时被静默丢弃（issue #147）。

## 1 背景与口径

会上 L340-L346：四个 Excel 内部人员都可以下载。模板 C 的 16 列就是这张导出的样子。
2026-09-17 晚 Kevin 定外部也能提交石蜡包埋送样（CR-20260917-05）：实验室在这一页核验，判有效时给石蜡块编号，再照常补工序与染色。

## 2 实现要点

- 页面 `views/lqg/embed/index.vue` 按 `UI:admin.embed.list`：列 = 「内 / 外部」「核验状态」两个徽标列 + 模板 16 列；待核验行浅黄底置顶，石蜡块编号一格显示「待核验」；
  筛选（石蜡块编号、内部编号、染色、切片时间区间、核验状态、内 / 外部）；新增 / 编辑抽屉（选样本用远程搜索下拉，只列有效样本）；marker 可增删多行；编辑抽屉顶部小字「最后修改：某某 · 时间」——记录提交后随时可改（REQ-SAMPLE-016）。
  打开待核验或无效的外部送样 = 核验抽屉：底部「判为有效并保存」（石蜡块编号必填）、「判为无效」（弹原因），调 `PUT /lqg/embed/{id}/verify`。
  核验入参带 `fill`（CR-20260923-09，V02b；形状见 `doc/api-contract.md`）：判有效 = `fill: fillPayload()`（与普通保存同一份取值）；判无效 = `fill: invalidFill(fillPayload())`（只剩样本类型、类器官来源类型），打开抽屉时记一份快照，`labChanges` 算出改过的实验室补填项写进原因弹窗。两组键与后端 `EmbedFillRules` 逐字同序，由 `views/lqg/embed/verifyFill.spec.ts` 钉住。
  支持从样本表带 `sampleId` 跳入并自动过滤。入口 CR-20260924-11 起不再是样本表的「石蜡包埋」行操作，而是样本两页的「石蜡包埋 / 冻存」一列（「蜡块 N」带 sampleId、「待核验 N」再带 `verifyStatus=pending`、为 0 的「新增」带 `add=1`；`FLOW:F-SAMPLE-02.step8`）。
  带 sampleId 进来时页顶一条提示「只看 ××（单位）的石蜡包埋记录 · 共 N 条 · 打开样本 · 看全部」（「看全部」只清样本这一个筛选并把 sampleId 从地址里去掉），「新增」默认挂这个样本（`EmbedDrawer.openAdd(presetSample)`，带出组织收样 / 处理时间）；「样本编号」一格做成链接，按行上的 `sampleKind` 回到样本记录信息表或类器官收样记录并打开这条样本。筛选区「核验状态」「来源」（合作单位（外部）/ 中心内部）排在最前，工具栏加一键筛选「合作单位送来待核验 N」（N 取首页待办同一个数）；页面被标签页缓存时按 `route.fullPath` 重新套地址里的筛选；核验、删除后刷新菜单红点（UI:admin.embed.list）。
- `toggleStain(current, clicked)` 放 `views/lqg/embed/stain.ts`，单测 `stain.fixture.spec.ts` 从 fixture 读用例。
- 后端 `POST /lqg/embed/export`：按当前筛选导出；用框架的 `ExcelUtil.exportExcel` + 一个导出专用 VO（`@ExcelProperty` 的文字与顺序照模板），不要用实体 VO 直接导。日期 `yyyy-MM-dd`。导出视图（VO + 查询）放在 service 层（`EmbedExportService`）：小程序表格页的导出 `/mp/int/export/embed`（SYS-EXPORT-001）直接复用，两处文件逐列一致。
- 菜单 `V202609231110__EMBED-WEB-001-menu.sql`：5310「石蜡包埋」（`path='embed'`，`component='lqg/embed/index'`）+ 按钮 5311-5317（list / query / add / edit / remove / export / verify）；授 101、102。菜单角标（待核验送样数）在 SYS-HOME-001。

## 3 边界（明确不做）

- 不做导入
- 不做小程序页（EMBED-MP-001）
- 不做石蜡块的图片上传（模板里没有）
- 不做批量核验
- 不做菜单角标（SYS-HOME-001）

## 4 完工报告要求

1. 列表（含一条待核验外部送样）、录入抽屉、核验抽屉（所挂样本未核验时按钮置灰）截图；导出文件本身附在报告里
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新（F5 石蜡包埋核验抽屉，V02b；权威 FLOW:F-EMBED-01.step7、UI:admin.embed.list 变更）：§0 补口径 5、§2 写明核验入参带 `fill`（判有效带整份补填段、判无效只带样本类型两项、原因弹窗明说不保存的项）；accept 2 补 verifyFill.spec 八例、两个出口都经 fillPayload / invalidFill 带上 fill 的 grep。
- 2026-09-24 按 CR-20260924-10 更新：「样本总表」改称样本表两页（accept 2 的名字与 §2 同步；列表仍是 `views/lqg/sample/index.vue`，那段 grep 不受影响）；§0 补小程序也能核验石蜡包埋送样、同一个接口与规则。accept 逐条核过不用改。
- 2026-09-24 按 CR-20260924-11 更新：§2 从样本表跳入的入口改为样本两页的「石蜡包埋 / 冻存」一列，补提示条、新增默认挂样本、样本编号点回、来源筛选与「合作单位送来待核验」一键筛选、缓存页按地址重套筛选；blueprint_refs 补 FLOW:F-SAMPLE-02.step8。accept 不动（acc2 的 `sampleId` grep 仍成立，H 批已重放全绿）。
