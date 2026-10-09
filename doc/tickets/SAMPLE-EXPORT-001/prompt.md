---
ticket: SAMPLE-EXPORT-001
track: SAMPLE
phase: D3
size: M
req_refs:
  - REQ-SAMPLE-013
  - REQ-SAMPLE-001
  - REQ-SAMPLE-007
depends_on:
  - SAMPLE-WEB-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/export/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/export/**
  - code/plus-ui/src/views/lqg/sample/index.vue
  - code/plus-ui/src/api/lqg/sample/export.ts
adr_refs:
  - ADR-0006
blueprint_refs:
  - FLOW:F-SAMPLE-02.step5
  - UI:admin.sample.list
  - UI:admin.sample.organoid
  - FIELD:t_lqg_sample.sample_kind
  - FIELD:t_lqg_sample.donor_name
  - FIELD:t_lqg_sample.passage
accept:
  - name: "两张导出与甲方模板原件逐字对表头（类器官收样记录在「类器官类型」后插入「代数」，CR-20260924-10）；行数与同条件的列表总数一致；按钮字段、性别、加密列、代数的导出值逐格钉死；软删样本不导出"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-tissue.xlsx /tmp/lqg-organoid.xlsx /tmp/lqg-tissue-f.xlsx &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --out /tmp/lqg-tissue.xlsx POST /lqg/sample/export/tissue &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-tissue.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --insert "种属@来源单位" --rows 8 --find "内部编号=T-hli01" --expect "来源单位=A 医院,供体姓名=测试供体甲,性别=男,年龄=56,住院号=ZY0000001,组织类型=肝组织,有无固定=有,质控表=有,细胞活率报告=有,操作人=李工" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-tissue.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --insert "种属@来源单位" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND sample_kind='tissue'" | head -1)" &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-organoid.xlsx POST /lqg/sample/export/organoid &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-organoid.xlsx --template "_input/templates/类器官收样记录模板.xlsx" --insert "种属@来源单位,代数@类器官类型" --rows 1 --find "内部编号=T-oco01" --expect "来源单位=B 大学,类器官类型=结直肠类器官,代数=P3,细胞活率报告=有" &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-tissue-f.xlsx POST '/lqg/sample/export/tissue?sourceUnitId=9000009002' &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-tissue-f.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --insert "种属@来源单位" --rows "$(bash doc/verify/api.sh --as staff GET '/lqg/sample/list?sourceUnitId=9000009002&sampleKind=tissue&pageSize=100' | jq '.total')" &&
      bash doc/verify/api.sh --as extA --bizcode POST /lqg/sample/export/tissue | grep -qE '^403'
    counterfeit: |-
      导出 VO 直接复用列表 VO → 表头多出送检单号、核验状态、有无病理等列，与模板不等红。
      按钮字段导出了 Y / N，性别导出了 male → --expect 红。
      导出时没解密（绕过实体直接查 Map）→ 供体姓名一格是 Base64 密文红。
      tissue 导出把类器官那条也带上了 → 行数 9≠8 红；软删的 1010 被导出 → 同样红。
      带筛选导出另写了一套 where、漏了 sourceUnitId → 行数与列表 total 不等红。两侧不同源：导出文件 vs 列表接口。
      外部角色能调导出 → 最后一段红。
      类器官导出没加「代数」列、或加在末尾而不是「类器官类型」后面 → 带 --insert 的表头比对红（甲方 2026-09-24 第 18 行要的是紧跟类器官类型）；列加了但值没导出来 → 代数=P3 那格红（seed 1009 的代数是 P3）。
  - name: "导出按钮权限已 seed 且出自本票迁移；两张样本表页（样本记录信息表 / 类器官收样记录）的导出按钮已接上并带筛选条件"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || perms || ':' || parent_id FROM sys_menu WHERE perms = 'lqg:sample:export'" --eq "5217:lqg:sample:export:5210" &&
      python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5217" --col-set 101,102 &&
      python3 doc/verify/db.py --sql "SELECT path FROM sys_menu WHERE menu_id = 5210" --eq sample &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/index")] | length == 1' &&
      bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '.data.permissions | index("lqg:sample:export") != null' &&
      grep -q 'export/tissue' code/plus-ui/src/api/lqg/sample/export.ts && grep -q 'export/organoid' code/plus-ui/src/api/lqg/sample/export.ts &&
      grep -cE 'exportTissue|exportOrganoid' code/plus-ui/src/views/lqg/sample/index.vue | awk '{exit !($1 >= 2)}'
    counterfeit: |-
      后端加了 @SaCheckPermission("lqg:sample:export") 却没 seed 这个按钮 → 普通内部人员点导出 403；这里用 staff 身份断权限串真的下发到了 getInfo。
      按钮还留着 SAMPLE-WEB-001 的「下一个任务接入」提示 → 最后一段红。
      父菜单 5210 的 path 被本票迁移误改成空 → 第 3 段红（路由可达性的底线）。
---

# SAMPLE-EXPORT-001 · 样本记录信息表、类器官收样记录两张 Excel：按甲方模板列序导出当前筛选结果

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 甲方模板原件 `_input/templates/样本记录信息表模板.xlsx`（14 列）与 `类器官收样记录模板.xlsx`（7 列）的第 1 行
  - **ADR-0006**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0006` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. 两张导出是同一张样本表的两个视图：tissue 类导 14 列、organoid 类导 7 列 + 「代数」= 8 列（CR-20260924-10：甲方要求在「类器官类型」后插入「代数」，模板原件没有这一列；导出 VO 在类器官类型后插 index=2 的「代数」，其后顺延）。**有无病理不进导出**（模板里没有这一列）。
  2. 按钮字段导出成「有 / 无」，没选的留空；性别导出中文；供体姓名、住院号导出**明文**（内部人员导出，合同允许）。
  3. 导出范围 = 当前筛选结果，和列表用同一个查询条件对象，别另写一套 where。

## 1 背景与口径

会上 L340-L346：要导出 Excel，可以查看所有的；四个 Excel 内部人员都可以下载。合同附件第 8 行。

## 2 实现要点

- `POST /lqg/sample/export/tissue`、`POST /lqg/sample/export/organoid`（权限 `lqg:sample:export`，菜单按钮 5217，授 101、102——按钮行写进本票自己的迁移 `V202609231000__SAMPLE-EXPORT-001-perm.sql`）。
- 两个导出专用 VO，`@ExcelProperty` 的文字与顺序照模板第 1 行；复用列表的查询 BO 与 service 查询方法（不分页）。待核验 / 无效的样本也导出（内部编号一格为空），软删的不导。
  导出逻辑放 `SampleExportService`（VO + 查询），别写死在 controller 里：小程序表格页的导出 `/mp/int/export/tissue|organoid`（SYS-EXPORT-001）直接复用，两处导出的文件逐列一致。
- 工作台两张样本表页的导出按钮接上（带当前筛选条件）：CR-20260924-10 起「样本记录信息表」页只有「导出样本记录信息表」、「类器官收样记录」页只有「导出类器官收样记录」，请求显式带本页的 `sampleKind`（重置筛选不清它）。

## 3 边界（明确不做）

- 不给外部任何导出
- 不做自定义列、不做导入
- 石蜡包埋与冻存的导出在各自域的 WEB ticket；小程序里的导出在 SYS-EXPORT-001

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-24 按 CR-20260924-10 更新：类器官导出在「类器官类型」后插入「代数」——accept 1 的类器官表头比对加 `--insert "代数@类器官类型"`、期望值加 `代数=P3`（seed 1009 补了代数 P3）；blueprint_refs 补 UI:admin.sample.organoid、FIELD:t_lqg_sample.passage；§0 / §2 按「模板 7 列 + 代数」与两页各自的导出按钮改写。accept 2 逐条核过不用改（按钮行没复制，`lqg:sample:export` 仍恰好一行 5217；列表仍在 `views/lqg/sample/index.vue`）。
