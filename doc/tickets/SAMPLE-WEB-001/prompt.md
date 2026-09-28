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
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/relation/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260922101*__SAMPLE-WEB-001-*.sql
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260928101*__CR-20260924-10-*.sql
adr_refs:
  - ADR-0006
blueprint_refs:
  - UI:admin.sample.list
  - UI:admin.sample.organoid
  - UI:admin.sample.edit
  - FLOW:F-SAMPLE-01.step2
  - FLOW:F-SAMPLE-02.step3
  - FLOW:F-SAMPLE-02.step8
  - FIELD:t_lqg_sample.is_fixed
  - FIELD:t_lqg_sample.has_qc_sheet
  - FIELD:t_lqg_sample.has_viability_report
  - FIELD:t_lqg_sample.has_pathology
accept:
  - name: "筛选逐项钉死在 seed 上：来源单位、组别、类别、内外部、核验状态、收样日期区间、供体姓名精确、类器官类型模糊（CR-20260924-10）；两页各自显式带的 sampleKind 各取一类；软删样本任何筛选都不出现；待核验置顶"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      q() { bash doc/verify/api.sh --as staff GET "/lqg/sample/list?pageSize=100&$1" | jq -c '[.rows[].id|tostring]|sort'; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      test "$(q 'sourceUnitId=9000009001')" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001007"]' &&
      test "$(q 'groupId=9000009101')" = '["9000001001","9000001002","9000001003","9000001004","9000001007"]' &&
      test "$(q 'sampleKind=organoid')" = '["9000001009"]' &&
      test "$(q 'sampleKind=tissue')" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001006","9000001007","9000001008"]' &&
      test "$(q 'sampleKind=organoid&organoidType=%E7%BB%93%E7%9B%B4%E8%82%A0')" = '["9000001009"]' && test "$(q 'sampleKind=organoid&organoidType=%E8%82%9D')" = '[]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100&sampleKind=organoid' | jq -e '[.rows[]|select((.id|tostring)=="9000001009")|.passage]==["P3"]' &&
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
      拆页后类器官页还用「组织类型」筛（恒为空结果）、后端不认 organoidType → 「结直肠」那段不是 1009 红；做成精确匹配 → 同样红（要的是模糊）。列表行不带代数 → P3 那段红。
  - name: "菜单落在 5200 段、可达、内部人员看得到（5210 下七个按钮权限，含 SAMPLE-EXPORT-001 的导出）；CR-20260924-10 拆页：5210 改名「样本记录信息表」、新增 5220「类器官收样记录」（/sample-organoid），凡能看 5210 的角色都能看 5220，按钮行不复制，四张业务表在菜单里依次排开，两页列按「模板列 + 插入列」并由 fixture 驱动的单测钉住；页面与公共按钮组组件已建且按钮字段没有用下拉或开关；核验两个出口（判有效 / 判无效）都把整份送检段随 submitSegment 带上（CR-20260923-09）"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5210" --eq "5210:sample:lqg/sample/index" &&
      python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5210" --col-set 101,102 &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/index")] | length == 1' &&
      python3 doc/verify/db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5210 AND menu_type = 'F'" --col-set "lqg:sample:list,lqg:sample:query,lqg:sample:add,lqg:sample:edit,lqg:sample:remove,lqg:sample:verify,lqg:sample:export" &&
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || menu_name || ':' || path || ':' || component || ':' || parent_id FROM sys_menu WHERE menu_id IN (5210, 5220)" --col-set "5210:样本记录信息表:sample:lqg/sample/index:0,5220:类器官收样记录:sample-organoid:lqg/sample/organoid:0" &&
      python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5220" --col-set 101,102 &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_menu WHERE parent_id = 5220" --eq 0 &&
      python3 doc/verify/db.py --sql "SELECT string_agg(menu_id::text, ',' ORDER BY order_num, menu_id) FROM sys_menu WHERE menu_id IN (5210, 5220, 5310, 5410)" --eq "5210,5220,5310,5410" &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/organoid")] | length == 1' &&
      grep -q '<SampleIndex kind="organoid" />' code/plus-ui/src/views/lqg/sample/organoid.vue &&
      (cd code/plus-ui && grep -q 'doc/verify/fixtures/ledger-columns-cases.json' src/views/lqg/sample/pages.fixture.spec.ts && ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/sample/pages.fixture.spec.ts && pnpm vitest run src/views/lqg/sample/pages.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-sample-pages.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-sample-pages.json) &&
      test -f code/plus-ui/src/components/lqg/SegButtons/index.vue &&
      grep -c 'SegButtons' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue | awk '{exit !($1 >= 4)}' &&
      ! grep -nE '<el-switch|<el-select[^>]*(isFixed|hasQcSheet|hasViabilityReport|hasPathology)' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue &&
      grep -q 'submitSegment: submitSegmentPayload()' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue &&
      grep -qE "verifySample\(.*verifyPayload\('valid'\)" code/plus-ui/src/views/lqg/sample/SampleDrawer.vue && grep -qE "verifySample\(.*verifyPayload\('invalid'" code/plus-ui/src/views/lqg/sample/SampleDrawer.vue &&
      test "$(grep -c 'verifySample(' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue)" = "$(grep -cE "verifySample\(.*verifyPayload\('" code/plus-ui/src/views/lqg/sample/SampleDrawer.vue)"
    counterfeit: |-
      按钮权限串和后端 @SaCheckPermission 对不上（前端写 lqg:sample:verify、后端写 lqg:sample:audit）→ 第 4 段集合红；普通内部人员会点了没反应。
      （2026-09-23 改写，原 issue #129）第 4 段原来断 5210 下「恰好六个」权限串；SAMPLE-EXPORT-001 为两个导出端点在 5210 下加了第七个按钮 5217（lqg:sample:export），与「恰好六个」按构造互斥、重放必红。现在期望集合是七个：少了 export 两个导出端点全 403，多出别的串照样红。
      「有无固定」做成了 el-switch（开关没有「还没选」这个状态，甲方模板写的是「有 无（按钮）」）→ 第 7 段红。
      拆页（CR-20260924-10）：只改了页面、菜单没拆 → 5210 / 5220 那段集合红；5220 只给了管理员、内部人员看不到 → 授权集合红；在 5220 下复制了一份按钮行（同一权限串出现两个勾选框、SAMPLE-EXPORT-001 断的「lqg:sample:export 恰好一行」也会红）→ count 那段红；菜单顺序与甲方四张 Excel 对不上（石蜡包埋插在两张样本表中间、或排序并列不固定）→ string_agg 那段红；类器官页另写一份列表组件 → organoid.vue 那段红；两页的列手写而不读 fixture、「代数」没紧跟「类器官类型」→ pages.fixture.spec 红。
      抽屉里四个按钮字段只有两个用了 SegButtons → 计数不足红。
      核验抽屉的送检段显示成可编辑、请求体里却没有它（CR-20260923-09 之前的形态：改了被静默丢弃）→ 第 8 段红；只在「判为有效」带、「判为无效」漏了 → 第 9 段红；另写一个绕开 verifyPayload 的 verifySample 调用 → 最后一段两个计数对不上红。后端「同一事务落库、不带不动、校验不过库里不变」的行为断言在 SAMPLE-VERIFY-001 accept 3，这里只钉前端两个出口都带上。
  - name: "四张表之间的关系（2026-09-24 Kevin 本机验收）：样本行带「石蜡包埋 / 冻存」关联数（蜡块沿用 hint.blockCount，待核验送样数与冻存批次数读时算、每行都有零值），数与点过去那一页按样本筛出的条数一致；石蜡包埋 / 冻存的行带所挂样本类别供「样本编号」点回；「操作」列不再有石蜡包埋 / 冻存两个按钮，关联列的去向与数量为 0 的「新增」由 relation.spec 钉住"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      ROWS="$(bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/lqg/sample/list?pageSize=100')" &&
      r() { printf '%s' "${ROWS}" | jq -c --arg id "$1" '.rows[] | select((.id|tostring)==$id) | [.hint.blockCount, .relation.pendingEmbedCount, .relation.cryoBatchCount]'; } &&
      test "$(r 9000001001)" = '[2,0,2]' && test "$(r 9000001002)" = '[0,1,0]' && test "$(r 9000001004)" = '[1,0,1]' && test "$(r 9000001008)" = '[0,0,2]' && test "$(r 9000001005)" = '[0,0,0]' && test "$(r 9000001009)" = '[0,0,2]' &&
      printf '%s' "${ROWS}" | jq -e '.total == 9 and ([.rows[] | (.relation.pendingEmbedCount|type) == "number" and (.relation.cryoBatchCount|type) == "number"] | all)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_batch c JOIN t_lqg_sample s ON s.id=c.sample_id AND s.del_flag='0' WHERE c.del_flag='0'" --eq "$(printf '%s' "${ROWS}" | jq '[.rows[].relation.cryoBatchCount]|add')" &&
      test "$(bash doc/verify/api.sh --as staff GET '/lqg/embed/list?sampleId=9000001002&verifyStatus=pending&pageSize=100' | jq '.total')" = "$(r 9000001002 | jq '.[1]')" &&
      test "$(bash doc/verify/api.sh --as staff GET '/lqg/embed/list?sampleId=9000001001&verifyStatus=valid&pageSize=100' | jq '.total')" = "$(r 9000001001 | jq '.[0]')" &&
      test "$(bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?sampleId=9000001001&pageSize=100' | jq '.total')" = "$(r 9000001001 | jq '.[2]')" &&
      bash doc/verify/api.sh --as staff GET '/lqg/embed/list?pageSize=100' | jq -e '([.rows[] | select((.sampleId|tostring)=="9000001001") | .sampleKind] | unique) == ["tissue"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '([.rows[] | select((.sampleId|tostring)=="9000001009") | .sampleKind] | unique) == ["organoid"]' &&
      ! grep -nE 'rowAction\.(embed|cryo)' code/plus-ui/src/views/lqg/sample/index.vue && grep -q '<RelationLinks' code/plus-ui/src/views/lqg/sample/index.vue &&
      (cd code/plus-ui && ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/sample/relation.spec.ts && pnpm vitest run src/views/lqg/sample/relation.spec.ts --reporter=json --outputFile=/tmp/lqg-sample-relation.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 15' /tmp/lqg-sample-relation.json)
    counterfeit: |-
      冻存批次数把软删的也数进来 → 1008 名下有一批软删的 3008，[0,0,3] 红；两侧对账（接口逐行求和 vs 直连库 count）同样红。
      「待核验」直接拿全部送样数减有效块数、或把判了无效的也算进来 → 与石蜡包埋页按「样本 + 待核验」筛出的 total 对不上红；1002（2006 是它名下唯一一条、待核验）必须是 1。
      蜡块数另起一份口径（比如把待核验的外部送样也当一块）→ 1002 变成 [1,…] 红、与石蜡包埋页按「样本 + 有效」筛出的 total 对不上红。
      没有关联记录的行给 null（前端 undefined 上读数）→ 类型那段红。前端逐行再请求一次数量 → 这里测不到，但 relation.spec 钉的是「只读行上的 hint / relation」。
      石蜡包埋 / 冻存的行不带 sampleKind → 「样本编号」点回时不知道回哪一页（类器官会落到样本记录信息表的空抽屉）→ 两段 sampleKind 红。
      「操作」列里还留着「石蜡包埋」「冻存」两个按钮（与关联列两个入口并存、仍是跳整张表）→ grep 红。
---

# SAMPLE-WEB-001 · 工作台样本表：「样本记录信息表」与「类器官收样记录」两页（CR-20260924-10 由原「样本总表」拆成两页）、批量筛选、内外部与核验状态标识、录入 / 编辑 / 核验抽屉

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
  4. **外部送来的类器官收样记录在「类器官收样记录」页核验**（CR-20260917-05 / CR-20260924-10）：核验抽屉按 `sample_kind` 切字段——送检段是来源单位、类器官类型、代数（CR-20260924-10 加，选填，形如 P3，小写 p 转大写）、备注，收样段是收样日期、内部编号、处理时间、细胞活率报告、操作人。
  6. **两页、一份数据**（CR-20260924-10，甲方第 25 行「应该是分开的表，导出4个EXCEL表」）：5210 改名「样本记录信息表」（沿用 `/sample`、`lqg/sample/index`，只放组织样本），新增 5220「类器官收样记录」（`/sample-organoid`、`lqg/sample/organoid`，只放类器官）；两页是同一个列表组件（`index.vue`，`organoid.vue` 只是 `<SampleIndex kind="organoid" />`），类别由页面钉死、列表与导出每次都显式带 `sampleKind`（后端不带 = 两类都查）。
     筛选去掉「样本类别」；组织页保留组织类型 / 供体姓名 / 住院号，类器官页换成「类器官类型」（模糊）、没有供体姓名 / 住院号。列按「冻结列内部编号 → 送检单号、来源、核验状态 → 模板列 + 插入列（类器官在类器官类型后插「代数」）→ 有无病理或备注、提交人、组别、切片染色、最后修改」，期望读 `doc/verify/fixtures/ledger-columns-cases.json`。
     工具栏只有本类的「新增」「导出」；新增抽屉的类别锁定为本页类别。按钮权限沿用 5211-5217 那一组，**不在 5220 下复制按钮行**；授权 = 凡是有 5210 的角色都补 5220。顶级菜单顺序：样本记录信息表 4 → 类器官收样记录 5 → 石蜡包埋 6 → 冻存管理 7 → 人员与单位 8。
  5. **核验时送检段也可改，并且真的会保存**（`UI:admin.sample.edit`，CR-20260923-09）：「判为有效并保存」「判为无效」都把整份送检段随核验请求的 `submitSegment` 带上，与核验结论一次保存；来源单位在下拉里可改选正式单位，把只填了单位名的外部样本归口。以前送检段显示成可编辑、请求里却没有它，改了被静默丢弃。

## 1 背景与口径

会上（L319-L328）：网页上有一个操作台，批量筛选、搜索、查询，按来源单位看；所有样本信息不管来源单位都在这个表中呈现，不要一个样本一个表。
外部提交的样本在这里核验（SAMPLE-VERIFY-001 的接口）。

## 2 实现要点

### 2.1 后端补齐筛选（`org.dromara.lqg.sample.query`）
- `GET /lqg/sample/list` 补：`sourceUnitId`、`groupId`（join `t_lqg_ext_profile` on `submitter_id`，不看核验状态）、`submitSource`、`receiveDateBegin/End`、`tissueType`（模糊）、`operatorName`（模糊）。
  排序：待核验置顶，其余按创建时间倒序。每行补 `submitterName`、`groupName`（读时带出）。
### 2.2 页面（`views/lqg/sample/index.vue`）
- 筛选区、表格列、工具栏、行操作按 `UI:admin.sample.list`。待核验行浅黄底。导出两个按钮先放着、点了提示「导出在下一个任务接入」（SAMPLE-EXPORT-001 接）；「质控文档」行操作同理先置灰。
  CR-20260924-11 起行操作只有编辑 / 核验、质控文档、删除；石蜡包埋、冻存的入口不再是行操作，改在固定在右侧的「石蜡包埋 / 冻存」一列（`RelationLinks.vue`，`FLOW:F-SAMPLE-02.step8`）：「蜡块 N」取 `hint.blockCount`，「待核验 N」「冻存 N 批」取行上的 `relation`（后端 `sample/relation/**`，整页一次查询、零值不是 null），数字可点、带 sampleId 跳过去，为 0 时有效样本给「新增」（`?sampleId=…&add=1`）；地址带 `?sampleId=` 进本页时直接打开这条样本的抽屉并把参数从地址里去掉。页头一句话写明「一行是什么、关联记录看哪一列」（UI:admin.sample.list）。
- 抽屉 `SampleDrawer.vue` 按 `UI:admin.sample.edit`：新增时选类别切字段；打开待核验样本时底部两个出口「判为有效并保存」「判为无效」（弹原因）；「改判无效」收在更多菜单里。
  核验时送检段也可改（CR-20260923-09）：两个出口的请求体都由同一个 `verifyPayload` 组出，带上整份送检段 `submitSegment`（来源单位 id 与名称、供体姓名、性别、年龄、住院号、组织类型或类器官类型、有无病理、备注），后端与核验结论同一事务落库；判无效前先过一遍送检段必填，免得在原因弹窗里才看到 400。来源单位下拉放开，可把只填了单位名的外部样本归口到正式单位。
  有效样本打开时底部是「保存」、顶部小字「最后修改：某某 · 时间」——提交后随时可改，包括已核验有效的（REQ-SAMPLE-016，CR-20260917-04）。
- `components/lqg/SegButtons`：props `options / modelValue / multiple / exclusive（互斥值数组）`；基于 `el-radio-button` / `el-checkbox-button`。
- 字典全部走 `useDict`；文案走 `lqg.sample.*`。
### 2.3 菜单（`V202609221010__SAMPLE-WEB-001-menu.sql`；拆页在 `V202609281010__CR-20260924-10-sample-menu-split.sql`）
- 5210「样本总表」（C，`path='sample'`，`component='lqg/sample/index'`，顶级菜单）+ 按钮 5211-5216（list / query / add / edit / remove / verify）；授给 101、102。
- CR-20260924-10：5210 改名「样本记录信息表」（路由不变，只放组织样本）；新增 5220「类器官收样记录」（C，`path='sample-organoid'`，`component='lqg/sample/organoid'`，顶级）；授权平移；按钮行不复制；顶级菜单排序拉开（见 §0 口径 6）。后端 `SampleQueryBo` 加 `organoidType`（模糊）。

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

- 2026-09-23 按 CR-20260923-09 更新：accept 2 的期望权限集合加上 lqg:sample:export（与 SAMPLE-EXPORT-001 的 5217 按构造互斥，原 issue #129），补前端断言「判有效 / 判无效两个出口都经 verifyPayload 带上 submitSegment」；§0 口径复述与 §2.2 同步 UI:admin.sample.edit 新口径（核验时送检段可改、一次保存、来源单位可归口）。
- 2026-09-24 按 CR-20260924-10 更新：样本总表拆成两页——accept 1 补 `sampleKind=tissue` 集合、类器官类型模糊筛选两段、1009 行带代数 P3；accept 2 补 5210 改名与 5220 新菜单、5220 授权与 5210 相同、5220 下没有复制按钮行、四张业务表菜单顺序、getRouters 有 organoid 组件、organoid.vue 复用同一个列表、两页列的 fixture 单测 ≥ 9 例；blueprint_refs 补 UI:admin.sample.organoid，touches 补拆页迁移；§0 新增口径 6、口径 4 加代数，§2.3 补拆页迁移。重放后修正：accept 1 类器官类型筛选的查询串里「结直肠」「肝」直接写汉字，后端对未编码的中文查询串一律回 400、jq 读空，两段假红；改成与 SAMPLE-MODEL-001 同样的百分号编码（%E7%BB%93%E7%9B%B4%E8%82%A0 / %E8%82%9D），断言意图不变。
- 2026-09-24 按 Kevin 本机验收意见更新：新增 accept 3——样本行的「石蜡包埋 / 冻存」关联数（蜡块 / 待核验送样 / 冻存批次）逐样本钉在 seed 上并与目标页按样本筛出的条数对账，石蜡包埋 / 冻存行带所挂样本类别，「操作」列去掉石蜡包埋 / 冻存两个按钮，relation.spec ≥ 15 例；touches 补 sample/relation/**。
- 2026-09-24 按 CR-20260924-11 更新：§2.2 行操作改为编辑 / 核验、质控文档、删除，石蜡包埋 / 冻存入口改在关联列并写明数的来源与「新增」、地址带 sampleId 打开抽屉；blueprint_refs 补 FLOW:F-SAMPLE-02.step8。accept 3（H 批已在隔离环境跑绿）不动。
