---
ticket: EMBED-MP-001
track: EMBED
phase: D3
size: M
req_refs:
  - REQ-EMBED-001
  - REQ-EMBED-002
  - REQ-EMBED-003
  - REQ-EMBED-004
  - REQ-SYS-016
  - REQ-SAMPLE-016
  - REQ-AUTH-015
  - REQ-SYS-020
depends_on:
  - EMBED-MODEL-001
  - SAMPLE-MP-002
  - AUTH-EXT-002
touches:
  - code/miniapp/src/pages/embed/**
  - code/miniapp/src/components/lqg/SamplePickerExt.vue
  - code/miniapp/src/api/embed.ts
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/embed/mp/**
  - code/miniapp/src/pages/ledger/sheets.ts
  - code/miniapp/src/pages/history/sources.ts
  - code/miniapp/src/pages/sample/form.vue
adr_refs: []
blueprint_refs:
  - UI:mp.ledger
  - UI:mp.embed.list
  - UI:mp.embed.form
  - UI:mp.history
  - FLOW:F-EMBED-01.step1
  - FLOW:F-EMBED-01.step2
  - FLOW:F-EMBED-01.step3
  - FLOW:F-EMBED-01.step6
accept:
  - name: "小程序构建是本次产物、填写页与表格页在产物里、石蜡包埋工作表与历史页签已注册；染色切换过 fixture；内部接口只给内部角色；内部历史默认是中心全部内部人员的石蜡包埋记录、行上带经手人与「是不是我」，「只看我提交的」打开才收窄到本人（与库里独立数的一致）（CR-20260918-07）"
    form: API
    run: |-
      (cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && test -f dist/build/mp-weixin/pages/embed/form.js && test -f dist/build/mp-weixin/pages/ledger/index.js &&
       grep -q "embed" src/pages/ledger/sheets.ts && grep -q "embed" src/pages/history/sources.ts &&
       grep -qE "sort=recent|sort: *'recent'" src/pages/history/sources.ts &&
       ! grep -qE "int/embed/list\?[^\"']*mine=true" src/pages/history/sources.ts &&
       grep -q 'doc/verify/fixtures/stain-toggle-cases.json' src/pages/embed/stain.fixture.spec.ts && ! grep -nE '\.(skip|todo|only)\(' src/pages/embed/stain.fixture.spec.ts &&
       pnpm vitest run src/pages/embed/stain.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-stain-mp.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-stain-mp.json) &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/embed/list?pageSize=100' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002002","9000002003","9000002004","9000002006"]' &&
      bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/embed/list' | grep -qE '^403' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100&sort=recent' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002002","9000002003","9000002004","9000002006"] and (.rows[0].id|tostring)=="9000002006" and ([.rows[]|select(.handlerName==null)]|length)==0 and ([.rows[]|select((.id|tostring)=="9000002001")|[.handlerName,.mine]])==[["李工",true]] and ([.rows[]|select((.id|tostring)=="9000002004")|[.handlerName,.mine]])==[["测试管理员",false]]' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100&sort=recent&mine=true' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002003"]' &&
      python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_embed WHERE del_flag='0' AND (create_by=9000000101 OR update_by=9000000101)" --col-set "9000002001,9000002003"
    counterfeit: |-
      /mp/int/embed 漏了角色注解 → 外部拿到全部石蜡块连同包埋人，403 那段红。
      小程序自己另写了一套不互斥的切换逻辑 → fixture 红。
      石蜡包埋还做成一个单独的卡片列表页、没注册进表格页或历史页签 → sheets.ts / sources.ts 里找不到 embed 红。
      历史页签照旧只给本人（数据源把 `mine=true` 写死在 URL 里）→ sources.ts 那段 `! grep` 红；接口侧则是 `sort=recent` 那段少了 2002 / 2004 / 2006 红：CR-20260918-07 之后内部看的是江夏实验室**全员**的记录。
      拿 `mine=true` 兼当排序（不实现 `sort=recent`）→ sources.ts 的 sort 那段红。
      行上不给经手人、或 `handlerName` 取的是包埋人 `embed_by` 而不是新增 / 最后修改人 → 「李工」「测试管理员」两段红；`mine` 按 `embed_by` 姓名比对 → 2004 的 `mine` 红。
      最近的没排在前（没实现 `sort=recent` 的 `COALESCE(update_time, create_time)` 倒序）→ 2006 打头那段红。
      mine 不看 del_flag → 软删的 T-E05-X（李工建的）混进来红；mine 按 embed_by（包埋人姓名）算 → 集合对不上红。两侧不同源：接口 vs 直连库。
  - name: "补填就是修改：对已有石蜡块 PUT 只改传入的工序时间、不新增行、记下修改人并排到历史最前；内部人员改得动别人录的（2002 是管理员录的，CR-20260918-07）；改编号撞到别的石蜡块被拒；待核验的外部送样在小程序里改不动——被拒之后库里都不变"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      python3 doc/verify/db.py --sql "SELECT create_by FROM t_lqg_embed WHERE id=9000002002" --eq 9000000100 &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg PUT /mp/int/embed '{"id":9000002002,"sampleId":9000001001,"paraffinBlockNo":"T-E01-2","dehydrateTime":"2026-09-16","embedBy":"李工"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT count(*) || '|' || max(dehydrate_time)::text || '|' || max(embed_by) FROM t_lqg_embed WHERE sample_id=9000001001 AND del_flag='0'" --eq "2|2026-09-16|李工" &&
      python3 doc/verify/db.py --sql "SELECT CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_embed WHERE id=9000002002" --eq yes &&
      bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100&sort=recent&mine=true' | jq -e '(.rows[0].id|tostring)=="9000002002" and .rows[0].handlerName=="李工" and .rows[0].mine==true' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/embed '{"id":9000002002,"sampleId":9000001001,"paraffinBlockNo":"T-E01-1"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT paraffin_block_no FROM t_lqg_embed WHERE id=9000002002" --eq "T-E01-2" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-9","sampleType":"小程序里改的"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') || '|' || sample_type FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-|组织" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      保存按钮永远走 POST → 石蜡块数变 3 红（而且第二次会因编号重复被拒，用户就再也补填不了）。
      后端把「只能改自己录的」当规则拦了（CR-20260918-07 之前的想当然）→ 第一段 PUT 就红：2002 是管理员录的，李工照样能改。
      更新没记 update_by → 「yes」那段红，历史编辑记录也排不到最前：修改页的「最后修改」无从显示。
      `handlerName` 不看 update_by（一直取新增人）→ 改完还显示「测试管理员」红：经手人要的是最后动手的那个人。
      改编号撞到别的石蜡块没拦 → 撞号那两段红。
      /mp/int/embed 的 PUT 不看状态 → 待核验的外部送样被填上编号，最后一段红：核验被小程序里一次普通保存绕过去了。
  - name: "石蜡包埋填写页布局只认后端给的身份、状态与入口模式：外部只渲染选择样本与两项；外部有效与同组别人的只读；内部看外部待核验只读；内部管理进来的仍是只读详情，右上角「修改」由 showEditEntry 决定（CR-20260918-07）；身份缺失什么都不渲染"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/embed-form-cases.json' src/pages/embed/layout.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/embed/layout.fixture.spec.ts &&
      pnpm vitest run src/pages/embed/layout.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-embed-layout.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-embed-layout.json &&
      node -e "const f=require('../../doc/verify/fixtures/embed-form-cases.json'); const c=f.cases; if(c.length!==9||f.externalFields.length!==3||c.filter(x=>x.identity==='external').some(x=>x.expect.fields.some(k=>!f.externalFields.includes(k)))||c.filter(x=>!x.expect.editable).length!==5) process.exit(1)" &&
      grep -q 'showEditEntry' src/pages/embed/layout.ts && grep -q 'showEditEntry' src/pages/embed/form.vue &&
      test -f src/components/lqg/SamplePickerExt.vue && ! grep -nE 'internalNo' src/components/lqg/SamplePickerExt.vue
    counterfeit: |-
      外部复用内部全字段表单再隐藏 → 「外部新增」用例 fields 里带着 paraffinBlockNo / embedBy 红：字段名进了外部的包。
      外部 editable 漏了 mine → 「外部看同组别人的待核验」用例红。
      内部看外部送来的待核验可改 → 用例红：会绕过工作台核验。把 `mode=view` 直接做成可编辑（以为 CR-20260918-07 是「表格页能改了」）→ 「内部管理只读查看」用例红：进来仍是只读详情，改要先点「修改」。
      只读详情上没有「修改」入口、或把它写死在模板里不看身份与状态（外部、待核验的外部送样也冒出一个「修改」）→ showEditEntry 那两段红。
      外部选样本的下拉里显示内部编号 → 最后一段红（REQ-AUTH-013：不想让外部知道内部编号）。删病灶用例来过关 → node 那段红。
      组件挪了位置或改了名、`! grep` 对着一个不存在的文件 → `test -f` 红（这一段原先写成 `src/pages/embed/SamplePickerExt.vue`，文件不在那里，`! grep` 读不到文件照样返回真，恒绿）。
---

# EMBED-MP-001 · 小程序 · 石蜡包埋送样记录：填写页（内部全字段、外部送样两项；新增 / 修改 / 只读）、内部管理的石蜡包埋工作表（只读表格 + 只读详情里的「修改」入口）、历史编辑记录的石蜡包埋页签（内部看全中心）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/EMBED` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**EMBED-MODEL-001**、**SAMPLE-MP-002**、**AUTH-EXT-002**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` §5 与 §6 本页那一行；样式类用 SYS-MP-001 已落的 `src/style/components.scss`（`.lqg-*`），零颜色字面量。下面提到的图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**
  - 2026-09-17 晚 Kevin 定（CR-20260917-05）：首页点「石蜡包埋送样记录」就是填写，外部也能填；改自己填过的从「我的 → 历史编辑记录」进；内部管理里的石蜡包埋工作表只读（表格仍然只读，但 CR-20260918-07 在只读详情上加了「修改」入口，见下一条）。布局用例在 `doc/verify/fixtures/embed-form-cases.json`；外部填写页看图 `doc/design-options/gallery.html#mp-embed-ext`
  - **CR-20260918-07（甲方 2026-09-18 看设计图后提的）**，本张连带两处：
    ① 内部管理表格页从「纯只读」改成**只读表格 + 只读详情里的「修改」入口**——点一行进 `mode=view` 的只读详情，右上角「修改」切到 `mode=edit`，内部人员**可以改任何人录的**（外部送来还没核验 / 判无效的仍只读）；表格本身仍没有行内编辑、没有新增、没有核验（`UI:mp.ledger`、`UI:mp.embed.list`）
    ② 「我的 → 历史编辑记录」内部视角改为**中心全部内部人员**的记录（甲方原话：「我们内部人员也有多个哦，江夏实验室所有的工作人员」），默认全列，顶部「只看我提交的」开关（默认关），每行显示经手人（`UI:mp.history`）
- [ ] 口径复述（本张最容易做反的）：
  1. 保存**不要求填完**；再次打开是补填（也就是修改，从历史编辑记录进来），不是新建。
     内部修改是**补丁语义**（CR-20260923-09）：请求体里没出现的键不改；出现且值为 `null` 或空串 = 清空（工序时间填错了能清掉）；清必填项（所挂样本、石蜡块编号）→ 400「所挂样本不能为空」/「石蜡块编号不能为空」，库里不变。
     所以补填只传做完的那一步就行，要清掉一项必须显式传空，别在前端把空值过滤掉。
  2. 染色五按钮多选 + 无染色互斥，逻辑与工作台同一份 fixture。
  3. 日期选择器的 value 是毫秒时间戳，后端要 `yyyy-MM-dd`，输入输出两侧都要转（栈包 gotchas §6.3）。
  4. 石蜡包埋工作表的列来自 `ledgerColumns('embed')`（SAMPLE-MP-002 已定，fixture 已和模板原件对过），本张只注册工作表、接数据；冻结格 = 石蜡块编号（外部送样还没核验的显示送检单号 +「待核验」），第二行小字是工序进度小圆点。
     **表格只读**（没有行内编辑、没有新增，表格上不做核验），点一行进 `mode=view` 的只读详情；只读详情右上角「修改」切到 `mode=edit`，内部人员改得动任何人录的（CR-20260918-07）。
     CR-20260924-10 起合作单位送来、**待核验的那一条点进去是小程序核验页**（`UI:mp.verify.embed`，`sheets.ts` 的 `target` 用 `verifyTarget('embed', id)`，核验页归 SAMPLE-MP-002）；本张注册的工作表只是去向换了，别再写死 `mode=view`。
  5. **外部填写页只渲染「选择样本」+ 样本类型 + 类器官来源类型**；选样本只列本人送检过、没被判无效的（送检单号 + 掩码供体姓名，不出现内部编号）。石蜡块编号、工序、染色、marker、包埋人、操作人、备注都不渲染。
  6. **内部修改模式里，待核验、无效的外部送样只读**（核验走核验页 `PUT /lqg/embed/{id}/verify`，小程序与工作台都能核，CR-20260924-10；改判只在工作台），顶部提示「核验请从首页「待处理」进入，改判请到网页工作台」；后端 `PUT /mp/int/embed` 对这两种状态直接拒绝。这条 CR-20260918-07 没动：能改别人录的，不等于能用普通保存改外部送来还没核验的。
  7. **历史编辑记录（内部）默认是全中心**（CR-20260918-07）：数据源 `GET /mp/int/embed/list?sort=recent`，不带 `mine`；顶部「只看我提交的」开关（默认关）打开才追加 `mine=true`。
     `sort=recent` 只管排序，别拿 `mine=true` 兼当排序；行上多一列经手人（`handlerName`）与 `mine` 标记（本人显示「我」）。

## 1 背景与口径

内部人员在操作台前用手机补填工序时间。字段与工作台抽屉一致（模板 C 的 16 列）。提交后随时可改（CR-20260917-04）。
**工序时间是七个**（模板里有 7 个时间列）：组织收样、组织处理、琼脂糖包埋样本、脱水、琼脂糖包埋样本送样、石蜡包埋、切片；前两个选样本后自动带出、可改，七个全部可空。2026-09-18 逐列对过模板，原先权威里写的「六个」是笔误。
2026-09-17 晚 Kevin 定（CR-20260917-05）：外部首页三张表里有它，合作单位挂自己送检过的样本提交送样，实验室核验有效时给石蜡块编号。
2026-09-18 甲方看过设计图后又提了两条（CR-20260918-07）：表格页「还要加一条可以编辑」——折中成只读详情里的「修改」入口，内部人员改谁录的都行；历史编辑记录「我们内部人员也有多个哦」——内部看的是江夏实验室全员的记录，想只看自己再开开关。

## 2 实现要点

- 后端 `org.dromara.lqg.embed.mp`：`/mp/int/embed`（`list / {id} / POST / PUT`），类级 `@SaCheckRole("lqg_internal")`，复用 embed 域 service（校验规则只有一份）；
  list 的三个参数按跨 ticket 约定（CRYO-MP-001 / SAMPLE-MP-001 同款，CR-20260918-07）：`sort=recent` = 按 `COALESCE(update_time, create_time)` 倒序；
  `mine=true`（只在「只看我提交的」打开时带）= `create_by = 当前用户 OR update_by = 当前用户`；行上返回 `handlerName`（`update_by` 非空取最后修改人姓名，否则取新增人姓名）与布尔 `mine`。详情带 `updateByName / updateTime`。
  **PUT 不看是谁录的**：内部人员改得动中心里任何人录的记录（CR-20260918-07），拦的只有外部送来待核验 / 无效的那两种状态。
  PUT 与工作台 `PUT /lqg/embed` 走同一个 `EmbedService.update`，补丁语义与必填见 §0 口径 1（CR-20260923-09）。
- 在 `pages/ledger/sheets.ts` 注册 `embed` 工作表：数据 `/mp/int/embed/list`；筛选 = 搜索（石蜡块编号 / 内部编号）+ 核验状态 + 染色；点一行 → `pages/embed/form?id=&mode=view`（只读详情，通用挂法 SAMPLE-MP-002 已定）；待核验的那一条 → 核验页（CR-20260924-10）。
- `pages/embed/form` 按 `UI:mp.embed.form`：布局由纯函数 `embedLayout(identity, verifyStatus, mine, mode)`（`src/pages/embed/layout.ts`）决定，`layout.fixture.spec.ts` 读 `doc/verify/fixtures/embed-form-cases.json`。
  返回值加一个 `showEditEntry`（CR-20260918-07）：`mode==='view' && identity==='internal' && 该记录内部可改`（即不是外部送来待核验 / 无效的）才为真——只读详情右上角的「修改」据此渲染，点它把本页 `mode` 切成 `edit`，不新开页。
  fixture 的 9 条用例与 `fields / editable / showCard` 期望**不变**（`mode=view` 进来仍然是只读），`showEditEntry` 是新增的返回键，不改 fixture。
  内部：选择样本（搜内部编号，只列有效样本；从样本修改页跳入时带好）→ 全字段；marker 多行增删；`POST / PUT /mp/int/embed`。
  外部：选择样本（组件 `src/components/lqg/SamplePickerExt.vue`，直接 .vue 路径导入；数据 `GET /mp/ext/sample/list?onlyMine=true`、排除无效的，显示送检单号 + 掩码供体姓名）→ 样本类型、类器官来源类型 → `POST /mp/ext/embed`；`mode=edit` 回填后 `PUT /mp/ext/embed/{id}`；无效时顶部红条显示原因；有效后只读，展示石蜡块编号与已填的工序、染色、marker（复用 AUTH-EXT-002 的 `EmbedCard`）。
  修改模式顶部小字「最后修改：某某 · 时间」。`toggleStain` 放 `src/pages/embed/stain.ts` + `stain.fixture.spec.ts`（读 `doc/verify/fixtures/stain-toggle-cases.json`）。
- 历史编辑记录：在 `pages/history/sources.ts` 注册 `embed`——外部 `GET /mp/ext/embed/list?onlyMine=`（外部接口本来就按最近倒序，不另收 `sort`），
  内部 `GET /mp/int/embed/list?sort=recent`（默认全中心；「只看我提交的」开关默认关，打开才追加 `mine=true`）（CR-20260918-07）；
  行 = 石蜡块编号（没有则送检单号 +「待核验」）、样本类型、状态或「新增 / 修改」、**经手人**（`handlerName`，`mine` 为真时显示「我」）、日期；点行：外部 `mine && editable` → `mode=edit`，外部其余 → `mode=view`；内部 → `mode=edit`（别人录的也进修改模式）。
- 把 SAMPLE-MP-001 样本修改页里置灰的「给这个样本加石蜡块」点亮（带 `sampleId` 进 `pages/embed/form`）。

## 3 边界（明确不做）

- 小程序不做删除石蜡块（删除只在工作台）
- 本张不做核验页（CR-20260924-10 起小程序也能核验石蜡包埋送样，核验页 `pages/verify/embed` 归 SAMPLE-MP-002）；小程序不做改判
- 表格页不做行内编辑、不做新增——改只走「只读详情 → 修改」这一条路（CR-20260918-07）
- 表格页那条通用的「点一行进只读详情」挂法归 SAMPLE-MP-002，本张只把石蜡包埋这张表接上去
- 不做导出（SYS-EXPORT-001）

## 4 完工报告要求

1. 内部管理的石蜡包埋工作表（含左右滑动）、**点一行进的只读详情（右上角「修改」）与点「修改」之后的修改模式**、内部填写页（含 marker 多行）、外部填写页（新增 / 无效重提 / 有效只读）、历史编辑记录的石蜡包埋页签截图（**默认全中心 + 经手人列**，以及「只看我提交的」打开后的样子）（CR-20260918-07）
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新：accept 3 最后一段改查组件的真实位置 `src/components/lqg/SamplePickerExt.vue`（先 `test -f`，原路径不存在导致恒绿），touches 补上这个文件；§0 口径 1 与 §2 补内部修改的补丁语义（没出现的键不改、出现且为空即清空、清所挂样本或石蜡块编号回 400）。
- 2026-09-24 按 CR-20260924-10 更新：小程序也能核验石蜡包埋送样——§0 口径 4、6 与 §2、§3 改写（表格里待核验的那一条进核验页、填写页只读时的提示换成新文案、核验页归 SAMPLE-MP-002）；accept 逐条核过不用改（`PUT /mp/int/embed` 对待核验仍拒、布局 fixture 未变）。
