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
  - FLOW:F-CRYO-02.step3
  - FLOW:F-CRYO-02.step5
accept:
  - name: "导出的 Excel 与甲方模板原件逐字对表头（只许追加代数与当前剩余两列）；行数 = 未删批次数；初始与剩余、是否暂存、代数逐格钉死"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-cryo.xlsx &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --out /tmp/lqg-cryo.xlsx POST /lqg/cryo/batch/export &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --rows 7 --find "冻存样品=T-hli01-GZ-N-P2-EM2-2e5" --expect "冻存数量/支=8,冻存密度=2e5,暂存-80度超低温冰箱=是,冻存人=李工,代数=P2,当前剩余/支=6" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --find "冻存样品=T-oco01-JC-T-P4-EM1-5e5" --expect "暂存-80度超低温冰箱=否,液氮储存位置=1号罐-1架-A2,当前剩余/支=5" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --find "冻存样品=T-hli01-GZ-N-P5-EM2-1e5" --expect "冻存数量/支=3,当前剩余/支=0" &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_cryo_batch b JOIN t_lqg_sample s ON s.id = b.sample_id AND s.del_flag='0' WHERE b.del_flag='0'" | head -1)"
    counterfeit: |-
      「冻存数量/支」导出了剩余而不是初始 → 3001 那行期望 8 实际 6 红。甲方拿导出去对纸质记录，对的是当初冻了几支。
      追加列放到了模板列中间 → 表头顺序不等红。
      取空的批次不导出 → 行数 6≠7 红、第 3 段找不到行红。
      两侧不同源：导出文件 vs 甲方模板原件；数值再由 seed 期望钉住。
  - name: "菜单落在 5400 段、可达、流水与导出两个权限串下发到内部人员；页面已建；样本总表的「冻存」入口已点亮；流水抽屉接上了改删两个接口"
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
  2. 初始支数在编辑抽屉里**可改**（CR-20260917-04）；被拒时把后端提示原样显示（「已取走 N 支……」）。取走、补入、盘点调整、转液氮、改删登记**只在工作台**（CR-20260917-05：小程序只读查看）。
  3. 流水抽屉每行「修改」「删除」：修改弹窗与取走 / 补入 / 调整同款（类型不可改），删除二次确认；改删成功后列表的剩余与超期标记当场刷新，被拒时显示后端指出的是哪一笔。
  4. 「暂存-80度超低温冰箱」是两个按钮（是 / 否），用 `SegButtons`。

## 1 背景与口径

模板 D 的 9 列 + 两条批注（超两周提示、取走追溯）。会上 L340：四个 Excel 内部人员都可以下载。

## 2 实现要点

- 页面 `views/lqg/cryo/index.vue` 按 `UI:admin.cryo.list`：顶部页签（全部 / -80 超期 / 液氮，数字取 `tabCounts`）；超期行整行浅红 +「已超 N 天」徽标、置顶。
  行操作：取走、补入、盘点调整（三个小弹窗，取走的上限 = 剩余）、转液氮、流水（抽屉，时间线：每行显示操作后剩余与「已改 · 某某 时间」，带「修改」「删除」）。新增 / 编辑抽屉（初始支数可改）。
  从样本总表带 `sampleId` 跳入并过滤——点亮 SAMPLE-WEB-001 里置灰的「冻存」行操作。
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
