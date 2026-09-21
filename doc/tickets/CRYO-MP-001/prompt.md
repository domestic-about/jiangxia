---
ticket: CRYO-MP-001
track: CRYO
phase: D4
size: M
req_refs:
  - REQ-CRYO-001
  - REQ-CRYO-002
  - REQ-CRYO-003
  - REQ-CRYO-007
  - REQ-CRYO-901
  - REQ-SYS-016
  - REQ-CRYO-008
  - REQ-SYS-020
depends_on:
  - CRYO-REMIND-001
  - CRYO-FLOW-001
  - SAMPLE-MP-002
touches:
  - code/miniapp/src/pages/cryo/form.vue
  - code/miniapp/src/api/cryo.ts
  - code/miniapp/src/pages/ledger/sheets.ts
  - code/miniapp/src/pages/ledger/index.vue
  - code/miniapp/src/pages/history/sources.ts
  - code/miniapp/src/pages/sample/form.vue
  - code/miniapp/src/components/lqg/CryoBatchSheet.vue
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/mp/**
adr_refs: []
blueprint_refs:
  - UI:mp.ledger
  - UI:mp.cryo.list
  - UI:mp.cryo.form
  - UI:mp.cryo.flow
  - UI:mp.history
  - FLOW:F-CRYO-01.step1
  - FLOW:F-CRYO-01.step3
  - FLOW:F-CRYO-02.step5
accept:
  - name: "工作表的页签计数、逐批剩余与超期天数和库里独立数的一致且钉在 seed 上；改冻存数量走同一套逐笔校验（改到剩余为负被拒、改到不为负成功，别人录的也能改）；内部历史默认列中心全部内部人员的记录、每行带经手人，「只看我提交的」（`mine=true`）才收窄到本人（CR-20260918-07）；只给内部角色"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":2,"ln2":2} and (.rows|length)==7 and ([.rows[]|select((.id|tostring)=="9000003001")|[.remainingQty,.overdueDays,.location]]==[[6,6,"minus80"]])' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_cryo_batch WHERE del_flag='0') || '|' || (SELECT count(*) FROM t_lqg_cryo_batch WHERE del_flag='0' AND (in_minus80='N' OR to_ln2_time IS NOT NULL))" --eq "7|2" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent' | jq -e '(.rows|length)==7 and ([.rows[]|select((.id|tostring)=="9000003002")|[.handlerName,.mine]]==[["测试管理员",false]]) and ([.rows[]|select((.id|tostring)=="9000003001")|[.handlerName,.mine]]==[["李工",true]])' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent&mine=true' | jq -e '([.rows[].id|tostring]|sort)==["9000003001","9000003003"]' &&
      B="$(bash doc/verify/api.sh --as staff GET /mp/int/cryo/batch/9000003004 | jq -c '.data | {id, sampleId, cryoName, passage, freezeTime, density, inMinus80, frozenBy}')" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 2')" | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff PUT /mp/int/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 5 | .frozenBy = "王工"')" | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT b.init_qty || '|' || b.frozen_by || '|' || (b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0)) FROM t_lqg_cryo_batch b WHERE b.id=9000003004" --eq "5|王工|2" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent' | jq -e '(.rows|length)==7 and (.rows[0].id|tostring)=="9000003004" and ([.rows[0]|[.handlerName,.mine]]==[["李工",true]])' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent&mine=true' | jq -e '(.rows[0].id|tostring)=="9000003004" and ([.rows[].id|tostring]|sort)==["9000003001","9000003003","9000003004"]' &&
      python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_cryo_batch WHERE del_flag='0' AND (create_by=9000000101 OR update_by=9000000101)" --col-set "9000003001,9000003003,9000003004" &&
      bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/cryo/batch/list' | grep -qE '^403' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      小程序的修改接口另写了一个 update、绕过逐笔校验 → 把 3004 的冻存数量改成 2 拿到 200 红：它已经取走 3 支，剩余会变成 −1。
      修改时只认初始支数、把其他字段清空 → 冻存人不是「王工」或剩余不是 2 红。
      历史页签照老口径只列本人的（默认就带 `mine=true`）→ 全中心那段 7 条红。甲方原话：「我们内部人员也有多个哦，江夏实验室所有的工作人员」（CR-20260918-07）——3002 是管理员录的，必须看得见。
      行里不给经手人、或经手人取的是冻存人（`frozen_by`）→ 3002 的 `handlerName` 不是「测试管理员」红：经手人 = 最后改这条记录的人（没改过就是建的人），不是在冰箱前冻样的人。
      只读得到别人的、改不动别人的 → 3004 是管理员录的，PUT 拿不到 200 红：内部人员可以改任何人录的记录。
      mine 按冻存人（frozen_by 姓名）算 → 7 个批次全是「李工」，集合红；不看软删 → 3008 混进来红。改完没排到最前 → 红。两侧不同源：接口 vs 直连库。
      页签数字前端按当前页 rows 自己数 → 与 tabCounts 对不上（这里断接口给的数，页面接的是哪个数看截图）。
      外部能调冻存接口 → 403 那段红：冻存信息明确不对外。
  - name: "小程序里没有取用登记的写接口：取走、改登记、删登记、转液氮在 /mp/int 上都不存在，库里不变；批次详情的取用登记只读且逐笔剩余与 seed 一致；外部看不到"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /mp/int/cryo/batch/9000003003/flows | jq -e '.code==200 and ([.data[].id|tostring])==["9000003104","9000003103","9000003102"] and ([.data[].balanceAfter])==[4,7,5]' &&
      bash doc/verify/api.sh --as staff --bizcode POST /mp/int/cryo/batch/9000003002/flow '{"flowType":"take","qty":1,"purpose":"小程序取走"}' | grep -qE '^(404|405)' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch/9000003001/flow/9000003101 '{"qty":1,"purpose":"小程序改登记"}' | grep -qE '^(404|405)' &&
      bash doc/verify/api.sh --as staff --bizcode DELETE /mp/int/cryo/batch/9000003001/flow/9000003101 | grep -qE '^(404|405)' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch/9000003001/to-ln2 '{"toLn2Time":"2026-09-17","ln2Location":"1号罐"}' | grep -qE '^(404|405)' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_cryo_flow WHERE del_flag='0') || '|' || (SELECT delta FROM t_lqg_cryo_flow WHERE id=9000003101) || '|' || (SELECT COALESCE(to_ln2_time::text,'-') FROM t_lqg_cryo_batch WHERE id=9000003001)" --eq="5|-2|-" &&
      bash doc/verify/api.sh --as extA --bizcode GET /mp/int/cryo/batch/9000003001/flows | grep -qE '^403' &&
      (cd code/miniapp && ! grep -rnE 'flowType|to-ln2|FlowSheet' src/components/lqg/CryoBatchSheet.vue src/api/cryo.ts src/pages/ledger) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      照 CR-04 时的设计把取走 / 改删登记 / 转液氮接口搬进了 /mp/int → 这四段拿到 200 或 400 而不是「没有这个接口」红，库里的登记数或 3101 的支数随之变化红。Kevin 定的是这些只在工作台。
      只在前端去掉按钮、接口还留着 → 同样红：接口在，就有人能绕过页面调。
      取用登记不按时间倒序、或操作后剩余没按时间正序累计 → 第 1 段红。
      外部能调 flows → 403 那段红：冻存信息明确不对外。
  - name: "转液氮之后提醒就没了（CR-20260918-07 甲方问「转移后还会有提示吗」）：工作台登记转液氮后，小程序冻存工作表的超期页签计数 2→1、该批次不再出现在超期页签、行上 overdue=false 且位置变液氮；取空的批次同样不在超期页签里"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==2 and ([.rows[]|select(.overdue)|.id|tostring]|sort)==["9000003001","9000003005"]' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -e '([.rows[].id|tostring]|sort)==["9000003001","9000003005"]' &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"2号罐-1架-A1"}))')" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":1,"ln2":3} and ([.rows[]|select((.id|tostring)=="9000003001")|[.overdue,.location]]==[[false,"ln2"]])' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -e '([.rows[].id|tostring])==["9000003005"]' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      超期做成了标志位、靠每天 8 点的定时任务刷 → 转完液氮当天页签里还挂着 3001 红。甲方 9-18 问的就是这一句「转移后还会有提示吗」，答的是「登记了转液氮就立刻不提示了」。
      页签数字前端按当前页 rows 自己数 → tabCounts 那段对不上红（页面接的是哪个数看截图）。
      小程序这边自己又写了一份超期 where、没调 CRYO-REMIND-001 的那一个判定函数 → 转液氮后工作台与小程序两边数字不一致红。
      只把行从超期页签里去掉、`overdue` 还是 true（行仍标红底）→ 红。
      结尾 reseed：别把转了液氮的 3001 留给后面的断言。
  - name: "小程序构建是本次产物；-80 冻存工作表与历史页签已注册、填写页在产物里、批次详情弹层直接 .vue 路径导入且带「修改」入口（CR-20260918-07）；页面里不手写冻存列；小程序里没有盘点调整"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
      test -f dist/build/mp-weixin/pages/cryo/form.js && test -f dist/build/mp-weixin/pages/ledger/index.js &&
      grep -q "cryo" src/pages/ledger/sheets.ts && grep -rq "overdueOnly" src/pages/ledger/sheets.ts src/api/cryo.ts && grep -q "cryo" src/pages/history/sources.ts &&
      grep -rq "@/components/lqg/CryoBatchSheet.vue" src/pages/ledger &&
      grep -qE 'pages/cryo/form.*mode=edit|mode=edit.*pages/cryo/form' src/components/lqg/CryoBatchSheet.vue &&
      ! grep -nE '冻存密度|液氮储存位置' src/pages/ledger/index.vue &&
      ! grep -rnE 'adjust|盘点调整' src/pages/cryo src/pages/ledger src/components/lqg/CryoBatchSheet.vue
    counterfeit: |-
      冻存还做成单独的卡片列表页、没注册进表格页或历史页签 → sheets.ts / sources.ts 里找不到 cryo 红。
      CryoBatchSheet 走桶口导入 → 小程序里弹层是空白的且不报错，.vue 路径那段红。
      批次详情弹层还是老样子、右上角没有「修改」→ 那段 grep 红（CR-20260918-07：内部管理里改一条冻存记录，就从详情右上角「修改」进填写页的修改模式）。
      「修改」做成了在弹层里就地编辑、或跳到了工作台 → 同样红：它只是跳 `pages/cryo/form?id=…&mode=edit`，弹层本身仍然只读。
      表格页里为冻存手写了一份列 → index.vue 出现冻存列名红：列只从 ledgerColumns 来。
      顺手把工作台的盘点调整搬进了小程序 → 最后一段红。
---

# CRYO-MP-001 · 小程序 · -80 冻存记录：填写页（新增、从历史编辑记录修改）、内部管理的 -80 冻存工作表（只读、超期页签）与批次详情弹层（只读取用登记）、历史编辑记录的冻存页签

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/CRYO` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**CRYO-REMIND-001**、**CRYO-FLOW-001**、**SAMPLE-MP-002**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 2026-09-17 晚 Kevin 定（CR-20260917-05）：「网页工作台要有最全面的功能，只是把部分查看和筛选以及导出等功能放到小程序里」。小程序里的冻存只有三件事：首页新增冻存记录、「历史编辑记录」里改本人录的、「内部管理」里只读查看。**取走、补入、转液氮、改删登记都只在工作台**（原来做这些的 CRYO-MP-002 已删除）
  - 2026-09-18 甲方看过设计图后（CR-20260918-07）改了两处口径：① 内部管理表格页从「纯只读」变成「**只读表格 + 修改入口**」——冻存这张表点一行**仍然是只读的批次详情弹层**，要改这条冻存记录从**详情右上角「修改」**进冻存填写页的修改模式，内部人员改谁录的都行；② 「我的 → 历史编辑记录」的内部视角从「本人的记录」改成「**中心全部内部人员的记录**，默认全列，顶部「只看我提交的」开关（默认关），每行显示经手人」
  - `doc/design-options/gallery.html#mp-cryo`：-80 冻存工作表与点一行弹出的批次详情（只读版）
- [ ] 口径复述（本张最容易做反的）：
  1. 冻存工作表的列来自 `ledgerColumns('cryo')`（SAMPLE-MP-002 已定）：冻结格 = 冻存样品，第二行小字「剩 N / 初始 M 支」+ 超期红字「已超 N 天」；模板 9 列去掉冻存样品后按序，最后追加代数、当前剩余/支。
  2. 三个筛选页签（全部 / -80 超期 / 液氮）的数字取接口的 `tabCounts`，不在前端自己数；超期行浅红底、置顶。
  3. **批次详情弹层里的取用登记只读**：上部批次摘要，下部取用登记（每行带操作后剩余、改过的标「已改」），没有取走 / 补入 / 转液氮 / 改删登记按钮。`/mp/int/cryo/**` 上**根本没有**这些写接口——不是前端不放按钮就算了。弹层右上角只有一个「修改」，跳的是本条冻存记录的填写页（下一条）。
  4. **修改冻存记录全部可改，含冻存数量/支（= 初始支数）**：改小到让某一步剩余为负时后端拒绝，前端把提示原样显示，不自己算。修改入口有**两个**（CR-20260918-07）：「我的 → 历史编辑记录」点一行，和「内部管理」冻存工作表点一行 → 批次详情弹层右上角「修改」；两个都进 `pages/cryo/form?id=&mode=edit`。**内部人员改谁录的都行**，不限本人录的。
  5. 冻存样品名称：选了样本后用「内部编号-」预填，手改；代数单独一栏（`P` + 数字键盘）。
  6. 外部没有冻存：首页三格与历史编辑记录的页签里都没有（`entriesFor` 已管住），接口 403。
  7. **历史编辑记录的冻存页签默认列中心全部内部人员录的批次**（CR-20260918-07，甲方：「我们内部人员也有多个哦，江夏实验室所有的工作人员」），顶部「只看我提交的」开关默认关、打开才带 `mine=true`；每行显示经手人（最后改这条的人，没改过就是建的人，本人显示「我」）。**别再把 `mine=true` 当成历史页签的固定参数**。
  8. 超期是读时算的（CRYO-REMIND-001 的那一个判定函数），所以工作台一登记转液氮、或支数被取空，这条当场退出超期页签、计数减 1（CR-20260918-07 甲方问「转移后还会有提示吗」）。小程序这边只管把接口给的 `overdue` / `tabCounts` 照实显示，不自己判。

## 1 背景与口径

人在冰箱前用手机登记新冻的批次。2026-09-17 甲方要求点进去是表（REQ-SYS-016）、提交后能改（REQ-CRYO-008）。
同日晚 Kevin 定（CR-20260917-05）：工作台功能最全，小程序只放查看、筛选、导出；首页不再有「-80 超期」数字（超期提醒在工作台首页与冻存列表，小程序在内部管理的超期页签）。
2026-09-18 甲方看过设计图（CR-20260918-07）：内部管理那张只读表格「还要加一条可以编辑」→ 表格本身仍不可编辑，改走「点一行 → 只读详情 → 右上角修改」；历史编辑记录「我们内部人员也有多个哦」→ 内部视角默认看中心全部内部人员的记录，加「只看我提交的」开关。超期阈值同时改成系统参数 `lqg.cryo.overdue-days`（默认 14，判定在 CRYO-REMIND-001，本张只显示）。

## 2 实现要点

- 后端 `org.dromara.lqg.cryo.mp`（`MpCryoBatchController`）：`/mp/int/cryo/batch`（`list / {id} / POST / PUT`）+ `GET /mp/int/cryo/batch/{id}/flows`（只读，调 CRYO-FLOW-001 的查询）；类级 `@SaCheckRole("lqg_internal")`；
  复用 cryo 域 service（改初始支数的逐笔校验是同一个 `CryoBalanceChecker`）；详情带 `updateByName / updateTime`。
  list 的两个参数分开（CR-20260918-07：历史页签默认要全中心，`mine` 不能再兼职当「历史模式」）：`sort=recent` 按 `COALESCE(update_time, create_time)` 倒序（历史页签用；缺省仍是内部管理工作表的超期置顶），
  `mine=true` 才收窄到 `create_by / update_by = 当前用户`（「只看我提交的」打开时才带）。list 行补 `handlerName`（经手人：有 `update_by` 取最后修改人，否则取创建人）与 `mine`（本人为 true，前端显示成「我」）——与样本 / 石蜡包埋两张历史页签同名，对不上就 raise。
  PUT 不限本人录的（内部人员改谁录的都行，CR-20260918-07）。**不建**流水的 POST / PUT / DELETE、`to-ln2`、删除批次。
- 在 `pages/ledger/sheets.ts` 注册 `cryo` 工作表：数据 `/mp/int/cryo/batch/list`；筛选页签 全部 / 超期（`overdueOnly=true`）/ 液氮（`location=ln2`）；点一行打开 `CryoBatchSheet`。
- `CryoBatchSheet`（`wd-popup`，**直接 .vue 路径导入**）按 `UI:mp.cryo.flow`：上部批次摘要；下部取用登记（`…/flows`，时间倒序，每行 = 时间 · 类型 · ±支数 · 用途 · 经手人 · 操作后剩余，改过的标「已改」）；底部小字「取走、补入、转液氮与修改登记请到网页工作台」。
  **右上角加一个「修改」**（CR-20260918-07）：点它关掉弹层并跳 `pages/cryo/form?id=&mode=edit`。除此之外弹层仍然只读——取用登记那一块不加任何按钮。
- `pages/cryo/form` 按 `UI:mp.cryo.form`：新增（首页进来）与修改（历史编辑记录、或内部管理的批次详情「修改」进来，`mode=edit`）两种模式；修改模式顶部小字「最后修改：某某 · 时间」。
- 历史编辑记录：在 `pages/history/sources.ts` 注册 `cryo`（只有内部有这个页签）——`GET /mp/int/cryo/batch/list?sort=recent`，「只看我提交的」打开时再加 `mine=true`（开关默认关，CR-20260918-07）；行 = 冻存样品（等宽）、「剩 N / 初始 M 支」、经手人（本人显示「我」）、「新增 / 修改」、日期；点行 → `pages/cryo/form?id=&mode=edit`（别人录的也点得进去）。开关本身在共用的历史页（SAMPLE-MP-001），本张只把 `mine` 透传进来源。
- 把 SAMPLE-MP-001 样本修改页里置灰的「加冻存」点亮（带 `sampleId` 进 `pages/cryo/form`）。

## 3 边界（明确不做）

- 不做取走 / 补入 / 转液氮 / 盘点调整 / 改删登记（只在工作台，CRYO-WEB-001）
- 表格本身仍然没有行内编辑、没有新增、没有核验：CR-20260918-07 加的只是「点一行 → 只读详情 → 右上角修改」这一条路
- 不做超期阈值的读取与判定（CRYO-REMIND-001 的 `lqg.cryo.overdue-days`），本张只显示接口给的 `overdue` / `overdueDays` / `tabCounts`
- 不做删除批次
- 不做导出（SYS-EXPORT-001）
- 不做扫码定位冻存盒（合同不含）

## 4 完工报告要求

1. -80 冻存工作表（超期页签、左右滑动）、批次详情弹层（取用登记 + 右上角「修改」）、冻存记录填写页与修改模式（从详情进的那一路）、历史编辑记录的冻存页签（默认全中心带经手人 + 打开「只看我提交的」各一张）截图
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
