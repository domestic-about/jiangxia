---
ticket: SAMPLE-WEB-001
track: SAMPLE
phase: D2
size: L
req_refs:
  - REQ-SAMPLE-011
  - REQ-SAMPLE-012
  - REQ-SAMPLE-005
  - REQ-SAMPLE-002
  - REQ-SAMPLE-003
  - REQ-SAMPLE-004
  - REQ-SAMPLE-006
  - REQ-SAMPLE-016
depends_on:
  - SAMPLE-VERIFY-001
  - SYS-WEB-001
  - AUTH-GROUP-001
touches:
  - code/plus-ui/src/views/lqg/sample/**
  - code/plus-ui/src/api/lqg/sample/**
  - code/plus-ui/src/lang/lqg/sample.*.ts
  - code/plus-ui/src/components/lqg/SegButtons/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/query/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260922101*__SAMPLE-WEB-001-*.sql
adr_refs:
  - ADR-0006
blueprint_refs:
  - UI:admin.sample.list
  - UI:admin.sample.edit
  - FLOW:F-SAMPLE-01.step2
  - FLOW:F-SAMPLE-02.step3
  - FIELD:t_lqg_sample.is_fixed
  - FIELD:t_lqg_sample.has_qc_sheet
  - FIELD:t_lqg_sample.has_viability_report
  - FIELD:t_lqg_sample.has_pathology
accept:
  - name: "筛选逐项钉死在 seed 上：来源单位、组别、类别、内外部、核验状态、收样日期区间、供体姓名精确；软删样本任何筛选都不出现；待核验置顶"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      q() { bash doc/verify/api.sh --as staff GET "/lqg/sample/list?pageSize=100&$1" | jq -c '[.rows[].id|tostring]|sort'; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      test "$(q 'sourceUnitId=9000009001')" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001007"]' &&
      test "$(q 'groupId=9000009101')" = '["9000001001","9000001002","9000001003","9000001004","9000001007"]' &&
      test "$(q 'sampleKind=organoid')" = '["9000001009"]' &&
      test "$(q 'submitSource=internal')" = '["9000001008","9000001009"]' &&
      test "$(q 'verifyStatus=pending')" = '["9000001002","9000001007"]' &&
      B="$(python3 -c 'import datetime;print(datetime.date.today()-datetime.timedelta(days=26))')" && E="$(python3 -c 'import datetime;print(datetime.date.today()-datetime.timedelta(days=19))')" &&
      test "$(q "receiveDateBegin=${B}&receiveDateEnd=${E}")" = '["9000001004","9000001005"]' &&
      test "$(q 'sourceUnitId=9000009001&verifyStatus=valid&submitSource=external')" = '["9000001001","9000001004","9000001005"]' &&
      test "$(q '')" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001006","9000001007","9000001008","9000001009"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=2' | jq -e '[.rows[].verifyStatus] == ["pending","pending"] and .total == 9'
    counterfeit: |-
      按组别筛选写成了「样本来源单位下的所有组」→ groupId=9101 会带出 1005（extC 在 9102 组）红。
      组别筛选只认已核验的人 → 少了 1007（extE 待核验）红；内部人员筛组别是为了找样本，不该受核验状态影响。
      日期区间是开区间 / 漏了一端 → 1004（第 25 天）或 1005（第 20 天）掉出去红。区间端点特意取在两条样本的外侧各一天。
      多条件是 OR 拼的 → 三条件组合那一段红。
      软删的 1010 混进来 → 全量集合红。待核验没置顶 → 最后一段红。
  - name: "菜单落在 5200 段、可达、内部人员看得到；页面与公共按钮组组件已建且按钮字段没有用下拉或开关"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5210" --eq "5210:sample:lqg/sample/index" &&
      python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5210" --col-set 101,102 &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/index")] | length == 1' &&
      python3 doc/verify/db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5210 AND menu_type = 'F'" --col-set "lqg:sample:list,lqg:sample:query,lqg:sample:add,lqg:sample:edit,lqg:sample:remove,lqg:sample:verify" &&
      test -f code/plus-ui/src/components/lqg/SegButtons/index.vue &&
      grep -c 'SegButtons' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue | awk '{exit !($1 >= 4)}' &&
      ! grep -nE '<el-switch|<el-select[^>]*(isFixed|hasQcSheet|hasViabilityReport|hasPathology)' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue
    counterfeit: |-
      按钮权限串和后端 @SaCheckPermission 对不上（前端写 lqg:sample:verify、后端写 lqg:sample:audit）→ 第 4 段集合红；普通内部人员会点了没反应。
      「有无固定」做成了 el-switch（开关没有「还没选」这个状态，甲方模板写的是「有 无（按钮）」）→ 最后一段红。
      抽屉里四个按钮字段只有两个用了 SegButtons → 计数不足红。
---

# SAMPLE-WEB-001 · 工作台样本总表：一张表、批量筛选、内外部与核验状态标识、录入 / 编辑 / 核验抽屉

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-VERIFY-001**、**SYS-WEB-001**、**AUTH-GROUP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 视觉基准 = **方案 A**（宽表 + 右侧抽屉），Kevin 2026-09-17 已选定（CR-20260917-03）；看图 `doc/design-options/gallery.html#admin-sample-a`。方案 B（左表右常驻详情）已否决
  - admin 录入形态：抽屉 `el-drawer`，点蒙层可关，**禁 `:close-on-click-modal="false"`**
  - **ADR-0006**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0006` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **模板里写「按钮」的字段一律用按钮组**（有无固定、质控表、细胞活率报告、有无病理、性别），不用下拉、不用开关。本张产出公共组件 `SegButtons`，后面几个域直接用。
  2. **按组别筛选走提交人的外部档案**，不是样本上的字段（样本上没有 group_id）。
  3. 供体姓名、住院号的筛选框旁边标「精确匹配」——加密列做不了模糊。
  4. **外部送来的类器官收样记录也在这张表里核验**（CR-20260917-05）：核验抽屉按 `sample_kind` 切字段——送检段是来源单位、类器官类型、备注，收样段是收样日期、内部编号、处理时间、细胞活率报告、操作人。

## 1 背景与口径

会上（L319-L328）：网页上有一个操作台，批量筛选、搜索、查询，按来源单位看；所有样本信息不管来源单位都在这个表中呈现，不要一个样本一个表。
外部提交的样本在这里核验（SAMPLE-VERIFY-001 的接口）。

## 2 实现要点

### 2.1 后端补齐筛选（`org.dromara.lqg.sample.query`）
- `GET /lqg/sample/list` 补：`sourceUnitId`、`groupId`（join `t_lqg_ext_profile` on `submitter_id`，不看核验状态）、`submitSource`、`receiveDateBegin/End`、`tissueType`（模糊）、`operatorName`（模糊）。
  排序：待核验置顶，其余按创建时间倒序。每行补 `submitterName`、`groupName`（读时带出）。
### 2.2 页面（`views/lqg/sample/index.vue`）
- 筛选区、表格列、工具栏、行操作按 `UI:admin.sample.list`。待核验行浅黄底。导出两个按钮先放着、点了提示「导出在下一个任务接入」（SAMPLE-EXPORT-001 接）；「质控文档 / 石蜡包埋 / 冻存」三个行操作同理先置灰。
- 抽屉 `SampleDrawer.vue` 按 `UI:admin.sample.edit`：新增时选类别切字段；打开待核验样本时底部两个出口「判为有效并保存」「判为无效」（弹原因）；「改判无效」收在更多菜单里。
  有效样本打开时底部是「保存」、顶部小字「最后修改：某某 · 时间」——提交后随时可改，包括已核验有效的（REQ-SAMPLE-016，CR-20260917-04）。
- `components/lqg/SegButtons`：props `options / modelValue / multiple / exclusive（互斥值数组）`；基于 `el-radio-button` / `el-checkbox-button`。
- 字典全部走 `useDict`；文案走 `lqg.sample.*`。
### 2.3 菜单（`V202609221010__SAMPLE-WEB-001-menu.sql`）
- 5210「样本总表」（C，`path='sample'`，`component='lqg/sample/index'`，顶级菜单）+ 按钮 5211-5216（list / query / add / edit / remove / verify）；授给 101、102。

## 3 边界（明确不做）

- 不做导出（SAMPLE-EXPORT-001）、不做切片染色提示列（SAMPLE-HINT-001）、不做质控文档入口的目标页（QC-WEB-001）
- 不做批量核验、批量删除（甲方说的「批量」是批量筛选查询）
- 不做列配置、不做保存筛选方案

## 4 完工报告要求

1. 总表截图（含一条待核验行）、核验抽屉截图
2. 九种筛选各自返回的 id 集合
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
