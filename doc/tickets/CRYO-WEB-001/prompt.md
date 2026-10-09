---
ticket: CRYO-WEB-001
track: CRYO
phase: D4
size: M
req_refs:
  - REQ-CRYO-001
  - REQ-CRYO-002
  - REQ-CRYO-003
  - REQ-CRYO-004
  - REQ-CRYO-006
  - REQ-CRYO-007
  - REQ-SAMPLE-013
  - REQ-CRYO-008
depends_on:
  - CRYO-REMIND-001
  - SAMPLE-WEB-001
touches:
  - code/plus-ui/src/views/lqg/cryo/**
  - code/plus-ui/src/api/lqg/cryo/**
  - code/plus-ui/src/lang/lqg/cryo.*.ts
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/export/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260924121*__CRYO-WEB-001-*.sql
  - code/plus-ui/src/views/lqg/sample/index.vue
adr_refs: []
blueprint_refs:
  - UI:admin.cryo.list
  - FLOW:F-CRYO-01.step3
  - FLOW:F-CRYO-01.step5
  - FLOW:F-CRYO-02.step1
  - FLOW:F-CRYO-02.step3
  - FLOW:F-CRYO-02.step5
  - FLOW:F-SAMPLE-02.step8
accept:
  - name: "导出的 Excel 与甲方模板原件逐字对表头（只许追加代数与当前剩余两列）；行数 = 未删批次数；初始与剩余、是否暂存、代数逐格钉死"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-cryo.xlsx &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --out /tmp/lqg-cryo.xlsx POST /lqg/cryo/batch/export &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支,种属" --rows 7 --find "冻存样品=T-hli01-GZ-N-P2-EM2-2e5" --expect "冻存数量/支=8,冻存密度=2e5,暂存-80度超低温冰箱=是,冻存人=李工,代数=P2,当前剩余/支=6" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支,种属" --find "冻存样品=T-oco01-JC-T-P4-EM1-5e5" --expect "暂存-80度超低温冰箱=否,液氮储存位置=1号罐-1架-A2,当前剩余/支=5" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支,种属" --find "冻存样品=T-hli01-GZ-N-P5-EM2-1e5" --expect "冻存数量/支=3,当前剩余/支=0" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支,种属" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_cryo_batch b JOIN t_lqg_sample s ON s.id = b.sample_id AND s.del_flag='0' WHERE b.del_flag='0'" | head -1)"
    counterfeit: |-
      「冻存数量/支」导出了剩余而不是初始 → 3001 那行期望 8 实际 6 红。甲方拿导出去对纸质记录，对的是当初冻了几支。
      追加列放到了模板列中间 → 表头顺序不等红。
      取空的批次不导出 → 行数 6≠7 红、第 3 段找不到行红。
      两侧不同源：导出文件 vs 甲方模板原件；数值再由 seed 期望钉住。
  - name: "菜单落在 5400 段、可达、流水与导出两个权限串下发到内部人员；页面已建；样本表（CR-20260924-10 起是样本记录信息表 / 类器官收样记录两页，同一个列表组件）的「冻存」入口已点亮；流水抽屉接上了改删两个接口"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5410" --eq "5410:cryo:lqg/cryo/index" &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/cryo/index")] | length == 1' &&
      bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '(.data.permissions | index("lqg:cryo:flow") != null) and (.data.permissions | index("lqg:cryo:export") != null)' &&
      test -f code/plus-ui/src/views/lqg/cryo/index.vue && grep -q 'tabCounts' code/plus-ui/src/views/lqg/cryo/index.vue &&
      grep -rqE 'updateFlow|editFlow' code/plus-ui/src/api/lqg/cryo && grep -rqE 'delFlow|deleteFlow|removeFlow' code/plus-ui/src/api/lqg/cryo &&
      ! grep -nE '<el-switch' code/plus-ui/src/views/lqg/cryo/*.vue
    counterfeit: |-
      页签上的数字是前端对当前页 rows 自己数的 → 翻页就错；这里要求用后端的 tabCounts。
      取走按钮的权限串没 seed → staff 的 permissions 里没有 lqg:cryo:flow 红；只有管理员能取样，日常没法用。
      是否暂存 -80 用了开关 → 最后一段红（模板写的是「是 否（按钮）」）。
      流水抽屉还是只读、没接改删两个接口 → api 目录里找不到 updateFlow / deleteFlow 红：甲方要求登记填错了能直接改。
  - name: "工作台冻存的列序与取空提示（CR-20260924-10）：列表列 = 甲方模板 9 列按序 → 代数、当前剩余/支（与导出同序）→ 内部编号、当前位置、最后修改；「已取空」页签接后端 tabCounts.emptied 与 emptiedOnly、剩 0 支的行只认后端的 emptied；取走让剩余变 0 前确认，规则与小程序读同一份用例；取空后页签与导出都跟着变"
    form: STATE
    run: |-
      cd code/plus-ui &&
      test "$(grep -oE 'lqg\.cryo\.col\.[a-zA-Z0-9]+' src/views/lqg/cryo/index.vue | sed 's/lqg\.cryo\.col\.//' | tr '\n' ',')" = "freezeTime,cryoName,initQty,density,inMinus80,frozenBy,toLn2Time,ln2Location,remark,passage,remainingQty,internalNo,location,updateTime,action," &&
      grep -q 'name="emptied"' src/views/lqg/cryo/index.vue && grep -q 'tabCounts.emptied' src/views/lqg/cryo/index.vue && grep -q 'row.emptied' src/views/lqg/cryo/index.vue &&
      grep -q 'emptiedOnly' src/api/lqg/cryo/index.ts && grep -q 'route.query.emptiedOnly' src/views/lqg/cryo/index.vue &&
      grep -q "emptyConfirm: '登记后这一批就取空了（剩 0 支），确定吗？'" src/lang/lqg/cryo.zh_CN.ts &&
      grep -q 'doc/verify/fixtures/cryo-take-cases.json' src/views/lqg/cryo/flow.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/cryo/flow.fixture.spec.ts &&
      pnpm vitest run src/views/lqg/cryo/flow.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-cryo-flow-web.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 13' /tmp/lqg-cryo-flow-web.json &&
      cd ../.. && bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/lqg/cryo/batch/list?emptiedOnly=true&pageSize=100' | jq -e '([.rows[].id|tostring])==["9000003004"] and .rows[0].emptied==true and .tabCounts.emptied==1' &&
      bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003006/flow '{"flowType":"take","qty":5,"purpose":"取完"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?emptiedOnly=true&pageSize=100' | jq -e '([.rows[].id|tostring]|sort)==["9000003004","9000003006"] and .tabCounts.emptied==2 and ([.rows[]|select((.id|tostring)=="9000003006")|[.remainingQty,.overdue]]==[[0,false]])' &&
      rm -f /tmp/lqg-cryo-e.xlsx && bash doc/verify/api.sh --as staff --out /tmp/lqg-cryo-e.xlsx POST '/lqg/cryo/batch/export?emptiedOnly=true' &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo-e.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支,种属" --rows 2 --find "冻存样品=T-hli01-GZ-N-P5-EM2-1e5" --expect "当前剩余/支=0" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      列表列还是「模板 9 列 → 内部编号 → 代数 → 当前剩余」（甲方第 22 行「请参照我发你的模板，理解先后顺序」之前的排法）或把液氮两列挤到后面 → 第 1 段列序比对红：模板外的两列要紧跟模板、与导出同序，工作台自用的列放最后。
      「已取空」只做了行上的颜色、没有页签，或页签数前端自己数 → name="emptied" / tabCounts.emptied 两段红；行上按「剩余 == 0」自己判（与后端剩余算式各算各的）→ row.emptied 那段红。
      取空确认工作台另写一份规则、不读共用用例 → fixture grep 或单测红；提示语改了说法 → 词条那段红（两端要一字不差）。
      emptiedOnly 只在页面上过滤、后端不认 → 两段 emptiedOnly 列表或导出行数红；取空后超期还挂着（3006 是第 13 天的 -80 批次）→ overdue 那格红。
---

# CRYO-WEB-001 · 工作台 · 冻存管理：批次列表（超期置顶标红）、出入库与盘点调整、流水抽屉（登记可改可删）、按模板导出 Excel

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/CRYO` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**CRYO-REMIND-001**、**SAMPLE-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 甲方模板原件 `_input/templates/-80冻存模板.xlsx` 第 1 行 9 列——导出表头逐字按序一致，其后只许追加「代数」「当前剩余/支」两列
- [ ] 口径复述（本张最容易做反的）：
  1. 「冻存数量/支」导出的是**初始**支数；剩余另起一列放在模板 9 列之后。
  2. 初始支数在编辑抽屉里**可改**（CR-20260917-04）；被拒时把后端提示原样显示（「已取走 N 支……」）。盘点调整**只在工作台**；取走、补入、转液氮、改删取走 / 补入登记 CR-20260924-10 起**小程序也能做**（甲方「要求小程序和工作台界面都能操作」），两端调同一组 `/lqg/cryo/**` 写口、规则一字不差（原先 CR-20260917-05「小程序只读查看」作废）。
  3. 流水抽屉每行「修改」「删除」：修改弹窗与取走 / 补入 / 调整同款（类型不可改），删除二次确认；改删成功后列表的剩余与超期标记当场刷新，被拒时显示后端指出的是哪一笔。
  4. 「暂存-80度超低温冰箱」是两个按钮（是 / 否），用 `SegButtons`。
  5. **编辑抽屉的保存是补丁语义**（CR-20260923-09，`PUT /lqg/cryo/batch`）：请求体里没出现的键不改；出现且值为 `null` 或空串 = 清空；清必填项 → 400 并写明是哪一项（冻存的必填 = 所挂样本、样品名称、代数、冻存时间、冻存数量、是否暂存 -80），前端原样显示。
     抽屉是整份回传，清掉的项要真的传 `null`，别在提交前把空值过滤掉。**「-80 转移至液氮时间」登记错了，在抽屉里清掉保存 = 撤销转液氮**（`toLn2Time:null`；液氮位置要一并清掉就同时传 `ln2Location:null`）：批次回到 -80，按冻存时间重新算超期。
     以前是「null = 不动」，清掉点保存提示已保存而库里还在，这一批永远算「已转液氮」、退出超期提醒。
  6. **流水抽屉改登记**（`PUT /lqg/cryo/batch/{id}/flow/{flowId}`）：`purpose` 传空串 = 清空用途（取走、补入可以不写用途）；盘点调整清空用途 → 400「盘点调整必须写原因」，原样显示（CR-20260923-09）。改删一笔被拒时后端指出是哪一笔：「这样改（或：删掉这一笔）会让 MM-dd HH:mm 那一笔（±N 支）之后的剩余变成 -M 支，没有保存」（CR-20260924-10）。
  7. **列序对齐甲方模板、支数取空要提示**（CR-20260924-10，甲方第 22 行）：列表列 = 模板 9 列 → 代数、当前剩余/支（与导出同序）→ 内部编号、当前位置、最后修改 → 操作；新增 / 编辑抽屉字段也按模板先后（选择样本、冻存时间、冻存样品、冻存数量/支、冻存密度 ‖ 暂存-80、冻存人、转移至液氮时间、液氮储存位置、备注 ‖ 补充信息：代数），合成一个 el-form（液氮位置的必填校验要真的跑）。
     页签加「已取空」（数取 `tabCounts.emptied`，列表带 `emptiedOnly=true`，`?emptiedOnly=true` 直达）；剩 0 支的行在「当前剩余/支」下方标琥珀「已取空」、流水抽屉抬头同样标——判据只认后端行上的 `emptied`；取走让剩余从有变成 0 前确认「登记后这一批就取空了（剩 0 支），确定吗？」（纯函数 `views/lqg/cryo/flow.ts`，`flow.fixture.spec.ts` 与小程序读同一份 `doc/verify/fixtures/cryo-take-cases.json`）。

## 1 背景与口径

模板 D 的 9 列 + 两条批注（超两周提示、取走追溯）。会上 L340：四个 Excel 内部人员都可以下载。

## 2 实现要点

- 页面 `views/lqg/cryo/index.vue` 按 `UI:admin.cryo.list`：顶部页签（全部 / -80 超期 / 液氮 / 已取空，数字取 `tabCounts`；「已取空」CR-20260924-10 加）；超期行整行浅红 +「已超 N 天」徽标、置顶。
  行操作：取走、补入、盘点调整（三个小弹窗，取走的上限 = 剩余）、转液氮、流水（抽屉，时间线：每行显示操作后剩余与「已改 · 某某 时间」，带「修改」「删除」）。新增 / 编辑抽屉（初始支数可改）。
  从样本表（CR-20260924-10 起是「样本记录信息表」「类器官收样记录」两页）带 `sampleId` 跳入并过滤。入口 CR-20260924-11 起不再是「冻存」行操作，而是样本两页的「石蜡包埋 / 冻存」一列（「冻存 N 批」带 sampleId，为 0 的「新增」带 `add=1`；`FLOW:F-SAMPLE-02.step8`）。
  带 sampleId 进来时页顶一条提示「只看 ××（单位）的冻存批次 · 共 N 批 · 打开样本 · 看全部」，页签数字仍是整表口径（tabCounts 不收窄）；「新增」默认挂这个样本（`CryoDrawer.openAdd(presetSample)`）；「内部编号」一格做成链接，按行上的 `sampleKind` 回到对应那一页并打开这条样本；页头写明「冻存记录只有中心内部人员填写，合作单位看不到也不能填」；页面被标签页缓存时按 `route.fullPath` 重新套地址里的筛选（UI:admin.cryo.list）。
  ★ 上面这些改动没有在 `views/lqg/cryo/index.vue` 里新增任何 `lqg.cryo.col.*`（accept 3 按出现顺序比对这组键）。
- 后端 `POST /lqg/cryo/batch/export`：导出专用 VO；列 = 模板 9 列 + 代数 + 当前剩余/支；「暂存-80度超低温冰箱」导出「是 / 否」；日期 `yyyy-MM-dd`。
- 菜单 `V202609241210__CRYO-WEB-001-menu.sql`：5410「冻存管理」（`path='cryo'`，`component='lqg/cryo/index'`）+ 按钮 5411-5417（含 `lqg:cryo:flow`、`lqg:cryo:export`）；授 101、102。

## 3 边界（明确不做）

- 不做液氮罐位置的图形化
- 不做出入库统计报表
- 不做批量取走

## 4 完工报告要求

1. 列表（含超期行）、取走弹窗、流水抽屉截图；导出文件
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新：§0 口径复述补 5、6 两条——编辑抽屉的补丁语义与冻存必填项、清掉转液氮时间（`toLn2Time:null`）即撤销转液氮；流水改登记时用途传空串即清空，盘点调整清空用途回 400。accept 不动。
- 2026-09-24 按 CR-20260924-10 更新：**新增 accept 3**（列表列序 = 模板 9 列 → 代数、当前剩余 → 内部编号、位置、最后修改；「已取空」页签 / 行标记只认后端 `tabCounts.emptied` 与 `emptied`；取空确认与小程序同一份用例、词条一字不差；取空后 emptiedOnly 列表与导出都跟着变）；blueprint_refs 补 F-CRYO-02.step1；§0 口径 2 改为小程序也能登记（盘点调整仍只在工作台）、口径 6 补改删被拒的新提示、新增口径 7。accept 1 / 2 逐条核过不用改（导出表头本来就对）。
- 2026-09-24 按 CR-20260924-11 更新：§2 从样本表跳入的入口改为样本两页的「石蜡包埋 / 冻存」一列，补提示条（页签数字仍整表口径）、新增默认挂样本、内部编号点回样本、页头说明与缓存页按地址重套筛选；blueprint_refs 补 FLOW:F-SAMPLE-02.step8。accept 不动（H 批已重放全绿）。
