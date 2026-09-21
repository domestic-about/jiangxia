---
ticket: SAMPLE-MP-001
track: SAMPLE
phase: D2
size: L
req_refs:
  - REQ-SAMPLE-001
  - REQ-SAMPLE-002
  - REQ-SAMPLE-003
  - REQ-SAMPLE-004
  - REQ-SAMPLE-006
  - REQ-SAMPLE-014
  - REQ-AUTH-009
  - REQ-SYS-020
depends_on:
  - AUTH-EXT-001
  - SYS-MP-001
touches:
  - code/miniapp/src/pages/sample/**
  - code/miniapp/src/pages/history/**
  - code/miniapp/src/api/sample.ts
  - code/miniapp/src/components/lqg/**
  - code/miniapp/src/pages/me/index.vue
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/mp/**
adr_refs:
  - ADR-0004
blueprint_refs:
  - UI:mp.sample.form
  - UI:mp.history
  - UI:mp.sample.detail.ext
  - FLOW:F-MP-01.step2
  - FLOW:F-SAMPLE-01.step1
  - FLOW:F-SAMPLE-01.step4
  - FLOW:F-SAMPLE-02.step1
accept:
  - name: "表单布局只认后端给的身份、状态与入口模式：外部永远不渲染收样段、有效样本与同组别人的样本只读；内部改有效样本可写、内部看待核验与无效只读；内部管理进来的一律只读；模式缺失按只读；身份缺失什么都不渲染。外部详情仍不渲染收样段、冻存与核验人，内部编号照接口给的渲染、开关不在前端判（CR-20260918-07）；历史编辑记录内外部共用同一个「只看我提交的」开关，内部页签默认取全中心、行里带经手人"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/sample-form-cases.json' src/pages/sample/layout.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/sample/layout.fixture.spec.ts &&
      pnpm vitest run src/pages/sample/layout.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-layout.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 13' /tmp/lqg-layout.json &&
      node -e "const f=require('../../doc/verify/fixtures/sample-form-cases.json'); const c=f.cases; if(c.length!==13||f.receiveFields.length!==7||c.filter(x=>x.identity==='internal'&&!x.expect.editable).length!==5||c.filter(x=>x.mode==='view'&&x.expect.editable).length!==0||c.filter(x=>x.mode==='view').length!==2||!c.some(x=>!['new','edit','view'].includes(x.mode))) process.exit(1)" &&
      ! grep -nE 'receiveDate|processTime|hasQcSheet|hasViabilityReport|verifyByName|[Cc]ryo' src/pages/sample/detail-ext.vue &&
      grep -q 'internalNo' src/pages/sample/detail-ext.vue &&
      ! grep -nE 'lqg.ext.show-internal-no|lqgExtShowInternalNo' src/pages/sample/detail-ext.vue &&
      grep -q 'entriesFor' src/pages/history/index.vue && ! test -e src/pages/sample/mine.vue &&
      grep -rq '只看我提交的' src/pages/history && grep -rq 'handlerName' src/pages/history &&
      grep -q 'sort=recent' src/pages/history/sources.ts
    counterfeit: |-
      外部视角下把收样段渲染出来再 v-show=false → formLayout 返回的 fields 里带着 receiveDate 等，用例「外部新增」红。
      editable 写成 identity==='external' && status!=='valid'（漏了 mine）→「外部看同组别人的待核验」用例红：页面上会出现一个点了必然被后端拒绝的提交按钮。
      身份缺失时按内部渲染 → 「身份缺失」用例红。
      内部一律可编辑（editable 写成 identity==='internal'）→「内部看外部的待核验」「内部看无效」两条用例红：小程序里点一下保存就能把待核验样本的收样段填了，绕过工作台的核验。
      只看状态不看 mode → 「内部管理只读查看」两条用例红：Kevin 定的内部管理只能看。mode 缺失时默认可改 → 「模式缺失」用例红。
      外部详情页里出现了收样日期、处理时间、质控表、细胞活率报告、核验人或任何冻存字段（哪怕没显示）→ `detail-ext.vue` 的禁字 grep 红：字段名进了外部的包就是泄露。
      内部编号被写死成「永不渲染」（照 9-17 的老口径）→ `grep internalNo` 红：`lqg.ext.show-internal-no` 打开后外部该看得见（CR-20260918-07）；反过来把开关搬到前端判（页面自己读系统参数再 v-if）→ `lqg.ext.show-internal-no` 那段红：该给不该给是后端的事，前端只渲染接口给的。
      历史编辑记录自己手写一份页签清单、或旧的「我的送检」页还在 → `entriesFor` / `mine.vue` 两段红。
      「只看我提交的」开关只做在外部页签上（内部还是老口径的「本人」清单）→ 开关那段 grep 红：9-18 甲方要的是「江夏实验室所有的工作人员」（CR-20260918-07）。行里不给经手人 → `handlerName` 那段红：谁录的、谁改的看不出来。
      内部页签仍旧写死 mine=true 取数 → `sort=recent` 那段红：默认得是全中心（`sort=recent` 不带 `mine`），本人是开关打开之后的事。
      删 fixture 里的病灶用例来过关 → node 那段红。
  - name: "小程序构建通过且是本次产物；内部接口只给内部角色；内部「历史编辑记录」默认 = 中心全部内部人员新增或最后修改过的（CR-20260918-07；与库里独立数的一致，外部自己改的不算经手，改完一条立刻排到最前，行里带经手人与「新增 / 修改」），「只看我提交的」再收窄到本人；内部改得动有效样本、别人录的也改得动并记下修改人，改不动待核验样本（被拒且库里不变）"
    form: API
    run: |-
      (cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && test -f dist/build/mp-weixin/pages/sample/form.js && test -f dist/build/mp-weixin/pages/history/index.js && ! test -e dist/build/mp-weixin/pages/sample/mine.js) &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/sample/list?pageSize=100' | jq -e '.code==200 and (.rows|length)==9 and ([.rows[]|select((.id|tostring)=="9000001001")|.internalNo]==["T-hli01"])' &&
      bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/sample/list' | grep -qE '^403' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent' | jq -e '([.rows[].id|tostring]|sort)==["9000001008","9000001009"] and ([.rows[]|select(.handlerName=="李工" and .mine==true and .updateTime==null)]|length)==2' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent&mine=true' | jq -e '([.rows[].id|tostring]|sort)==["9000001008","9000001009"]' &&
      bash doc/verify/api.sh --as extA PUT /mp/ext/sample/9000001002 '{"sourceUnitName":"A 医院","donorName":"测试供体乙","tissueType":"胆管组织"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent' | jq -e '([.rows[].id|tostring]|index("9000001002"))==null' &&
      bash doc/verify/api.sh --as staff PUT /mp/int/sample '{"id":9000001001,"tissueType":"肝组织（更正）"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT tissue_type || '|' || verify_status || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_sample WHERE id=9000001001" --eq "肝组织（更正）|valid|yes" &&
      bash doc/verify/api.sh --as staff PUT /mp/int/sample '{"id":9000001004,"remark":"别人录的也能改（CR-20260918-07）"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent' | jq -e '(.rows[0].id|tostring)=="9000001004" and (.rows[1].id|tostring)=="9000001001" and ([.rows[].id|tostring]|sort)==["9000001001","9000001004","9000001008","9000001009"] and ([.rows[]|select((.id|tostring)=="9000001001")|[.handlerName,.mine,(.updateTime!=null)]]==[["李工",true,true]])' &&
      python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_sample WHERE del_flag='0' AND (create_by IN (SELECT user_id FROM sys_user WHERE user_type='sys_user' AND del_flag='0') OR update_by IN (SELECT user_id FROM sys_user WHERE user_type='sys_user' AND del_flag='0'))" --col-set "9000001001,9000001004,9000001008,9000001009" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent&mine=true' | jq -e '([.rows[].id|tostring]|sort)==["9000001001","9000001004","9000001008","9000001009"]' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/sample '{"id":9000001002,"tissueType":"不该改进去"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE tissue_type='不该改进去'" --eq 0 &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      `sort=recent` 没做、历史编辑记录还是照 9-17 的老口径按本人取数 → 参数不认、接口把全部 9 条都给回来，`sort=recent` 的第一段集合断言红。这正是甲方 9-18 提的那条：「我们内部人员也有多个哦，江夏实验室所有的工作人员」（CR-20260918-07）。
      `sort=recent` 只当排序用、不挡「没人经手过的」→ 1002、1007 这些外部送来没人动过的混进历史编辑记录红：那是待核验清单，不是谁编辑过的记录。
      「经手过」写成「update_by 非空」→ extA 自己改过的 1002 混进来红：只算内部账号（`user_type='sys_user'`），外部改自己的送检不是中心的编辑记录。
      「经手过」按 verify_by 算（「中心核验过的」）→ 一开始就多出 1001、1003-1006 红；按 submitter_id 算 → 改完 1001 之后它不出现红：内部改过的记录在历史里找不回来。
      `mine`（「只看我提交的」开关）被忽略，或还拿 `mine=true` 兼当排序 → 开关那两段与最后的库侧集合对不上红。排序按创建时间 → 刚改的 1004、1001 排不到最前红。两侧不同源：一侧是接口，一侧是直连库按 create_by / update_by 独立数。
      handlerName 取提交人 → 1001 会显示「王医生」红：经手人是最后改它的人（李工），不是送检的人。行上不给布尔 `mine`、或不给 `updateTime` → 行里「我」和「新增 / 修改」无从显示，同段红。
      内部 PUT 加了「只能改本人录的」→ extB 录的 1004 改不动红：9-18 起内部人员对四张表的任何记录都能改（CR-20260918-07）。
      内部 PUT 不看状态 → 待核验的 1002 被改进了组织类型，count 不为 0 红：核验人看到的已经不是外部提交的原样。
      /mp/int/sample 的 PUT 借用了外部那条「只改送检段、改完回到待核验」的 service → 1001 的 verify_status 变成 pending 红；或者没记 update_by → 「yes」红，「最后修改」无从显示，历史编辑记录也找不回来。
      /mp/int/** 忘了加角色注解 → 外部拿到 200 红：外部能看到全部样本连同内部编号。
      先 rm 产物目录再构建：上次剩下的 dist 骗不过去；旧的「我的送检」页产物还在也红。
---

# SAMPLE-MP-001 · 小程序 · 样本记录信息表填写页（新增、修改、只读三种模式）、外部样本详情、历史编辑记录页（页签框架与样本记录页签）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-EXT-001**、**SYS-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` §5 与 §6 本页那一行；样式类用 SYS-MP-001 已落的 `src/style/components.scss`（`.lqg-*`），零颜色字面量。下面提到的图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**
  - 填写页视觉基准 = **方案 A**（单页分组长表单），Kevin 2026-09-17 已选定（CR-20260917-03）；看图 `doc/design-options/gallery.html#mp-form-a`。方案 B（三步向导）已否决。识别条本张只留位置，OCR-MP-001 接
  - 历史编辑记录看图 `gallery.html#mp-history`（外部）、`#mp-history-int`（内部）；口径以 `UI:mp.history` 为准。Kevin 2026-09-17 晚：「所有人员想看历史编辑记录，都可以在我的页面查看」（CR-20260917-05）。**原来的「我的送检」页作废**，由它取代。
    2026-09-18 甲方「我们内部人员也有多个哦，江夏实验室所有的工作人员」：**内部视角改为中心全员默认全列 + 「只看我提交的」开关 + 经手人列**（CR-20260918-07），外部视角不变
  - 栈包 gotchas §6.3：`wd-datetime-picker` 的 value 是毫秒时间戳，输入输出两侧都要转；§6.4：数字输入用 `type="digit"` 收 string
  - **ADR-0004**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0004` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **外部看不到收样段是「不渲染」，不是置灰**。渲染了再隐藏，字段名就进了外部的包。
  2. **同组别人的样本可看不可改**：`editable` 以后端详情里的为准，前端不自己判断。
  3. **填写页三种模式由入口决定**：首页点进来 = `new`；「历史编辑记录」点进来 = `edit`（外部本人的待核验 / 无效可改后重提；内部改有效样本，**别人录的也进修改模式**，CR-20260918-07）；「内部管理」点进来 = `view`（一律只读，本人录的也只读）。`mode` 缺失或不认识按只读处理，绝不按可改。
  4. **内部修改模式**：有效样本两段全部可改，**不限本人录的**——内部人员对中心的任何一条有效记录都能改（CR-20260918-07）；**待核验、无效的外部样本只读**，顶部提示「核验与改判请到网页工作台」——核验是带必填项的状态转移，不能被一次普通保存绕过去，后端 `PUT /mp/int/sample` 对这两种状态直接拒绝。
  5. **历史编辑记录不是逐次修改日志**：内部 = **中心全部内部人员**新增或最后修改过的记录，默认全列（取数口 `sort=recent`，后端按 `create_by / update_by` 是不是内部账号算），顶部「只看我提交的」开关（默认关）打开才**另外**带 `mine=true` 收窄到本人（CR-20260918-07）；外部 = 可见集合（本人 + 同组）加同一个「只看我提交的」开关（`onlyMine`）。别在前端自己过滤，也别建修改日志表（REQ-SYS-020 的 note：要逐次日志是另一个 CR）。
     「经手过」只认 `create_by / update_by`：外部送来没人动过的（待核验、无效）不进这张清单，外部自己改自己的送检也不算——它们在「内部管理」表格页和工作台的核验清单里。
  6. **历史编辑记录的页签清单直接用 `entriesFor(identity)`**（SYS-MP-001 的同一个函数），不另写一份；还没注册数据源的页签显示空状态。
  7. 列表里供体姓名用后端给的 `donorNameMasked`；详情才有全名。
  8. **外部详情里的内部编号由后端的开关说了算**（CR-20260918-07）：系统参数 `lqg.ext.show-internal-no` 默认 false，关着时接口根本不给这个键，打开后才给——页面照接口给的渲染，**前端不读系统参数、也不写死「永不渲染」**。石蜡包埋卡片里的操作人与包埋人对外可见（卡片组件在 AUTH-EXT-002，本张这一段还是空状态）；**冻存信息与核验人仍然全页不出现**。

## 1 背景与口径

样本记录信息表是外部送检的主表（L220）。提交后实时进总表（那是同一张表，没有同步动作）。无效的可以改了重提（方案流程图）。内部人员也能在小程序里填全部 14 列。
2026-09-17 晚 Kevin 定（CR-20260917-05）：首页点表就是填写，内外部一样；所有人在「我的 → 历史编辑记录」找回自己填过的记录，改也从这里进。
2026-09-18 甲方看了设计图（CR-20260918-07）：内部视角的历史编辑记录改成**中心全部内部人员**新增或修改过的记录、默认全列，顶部同样有「只看我提交的」开关，每行多一列经手人；点一行照旧进修改模式，**别人录的也能改**。
外部视角不变；外部样本详情的石蜡包埋卡片这次多给两个字段（操作人、包埋人），内部编号由系统参数 `lqg.ext.show-internal-no`（默认 false）决定给不给。

## 2 实现要点

- 公共组件（`src/components/lqg/`，**直接 .vue 路径导入**）：`FieldRow`、`SegButtons`（二态 / 三态 / 多选互斥）、`StatusChip`、`SampleCard`、`EmptyState` / `LoadingState` / `ErrorState`。
- `pages/sample/form`：路由参数 `id?`、`mode`（`new | edit | view`）；布局由纯函数 `formLayout(identity, verifyStatus, mine, mode)`（`src/pages/sample/layout.ts`）决定；
  写 `layout.fixture.spec.ts`，**用例与期望从 `doc/verify/fixtures/sample-form-cases.json` 读**。来源单位：外部默认带档案里的单位名；组织类型输入框接 `/mp/dict/hints?type=tissue` 的联想。
  外部走 `/mp/ext/sample`，内部走 `/mp/int/sample`（内部接口本张在 `org.dromara.lqg.sample.mp` 包里补：`GET list / GET {id} / POST / PUT`，类级 `@SaCheckRole("lqg_internal")`，复用 sample 域 service）。
  内部 `GET list` 的两个参数（CR-20260918-07；**三张 MP ticket 同一套命名**，CRYO-MP-001 已按此落地）：
  `sort=recent` = 历史编辑记录的取数口——按 `COALESCE(update_time, create_time)` 倒序，范围是**中心内部人员经手过的**（`create_by` 或 `update_by` 是内部账号，判据 `sys_user.user_type='sys_user'`——外部登录建的一律是 `app_user`，见 AUTH-LOGIN-001）。
  样本这张表同时还给内部管理表格页用，所以这个口除了排序还要把「没人经手过的」挡在外面：外部送来待核验、无效、外部自己改过的都不进历史编辑记录（`UI:mp.history`），它们在表格页和工作台的核验清单里。
  `mine=true` **单独一个参数**（「只看我提交的」开关打开才带，默认不带 = 中心全员），在上面的范围里再收窄到 `create_by = 当前用户 OR update_by = 当前用户`；**别再拿 `mine=true` 兼当排序**。不带这两个参数 = 内部管理表格页的全表（不变）。
  list 的行补 `handlerName`（经手人 = 最后修改人，没改过就是创建人，取 `sys_user.nick_name`）与布尔 `mine`（前端据此显示「我」）；「新增 / 修改」看行上的 `updateTime` 空不空。
  **`doc/api-contract.md` 那一行只写了 `mine=true` 与详情的 `updateByName / updateTime`**：`sort=recent`、行上的 `handlerName / mine / updateTime` 是本次按 CR-20260918-07 加的（api-contract 由调度侧同步），完工报告里照样 raise 一句。
  内部 `PUT`：样本是待核验或无效 → 400；**有效样本谁录的都能改，不加「只能改本人录的」这条限制**（CR-20260918-07）；详情带 `updateByName / updateTime`。修改模式顶部小字「最后修改：某某 · 时间」，底部「保存」旁两个小链接「给这个样本加石蜡块」「加冻存」（先置灰，EMBED-MP-001 / CRYO-MP-001 接）。
- `pages/history/index`（按 `UI:mp.history`）：顶部页签 = `entriesFor(identity)`；页头一个「只看我提交的」开关（默认关，**内外部共用同一个**，CR-20260918-07），开关状态传给数据源；每个页签的数据源在 `pages/history/sources.ts` 注册（key → 取数函数、行摘要、点行去哪）。本张注册 `sample`：
  外部 `GET /mp/ext/sample/list?sampleKind=tissue&onlyMine=`（开关值）；行 = 送检单号（等宽）、掩码供体 · 组织类型、「我 / 同组 某某」、状态徽标、日期；
  点行：`mine && editable` → `pages/sample/form?id=&mode=edit`，其余 → `pages/sample/detail-ext?id=`。
  内部 `GET /mp/int/sample/list?sampleKind=tissue&sort=recent`（开关打开再另带 `&mine=true`，CR-20260918-07）；行 = 内部编号（没有则送检单号）、摘要、**经手人**（行上 `mine` 为真显示「我」，否则 `handlerName`）、「新增 / 修改」（看 `updateTime` 空不空）、日期；
  点行 → `pages/sample/form?id=&mode=edit`，**别人录的也进修改模式**（外部送来还没核验的由后端挡成只读，核验在工作台）。
  空状态「你填过的记录会出现在这里」。把 SYS-MP-001「我的」里「历史编辑记录」一行的占位目标换成本页。
- `pages/sample/detail-ext`：按 `UI:mp.sample.detail.ext` 三段；包埋与文档两段此刻是空状态（包埋卡片里对外可见的操作人与包埋人在 AUTH-EXT-002 的 `EmbedCard` 上，本张不做）。
  内部编号一行**照接口给的渲染**：系统参数 `lqg.ext.show-internal-no` 默认 false 时接口不给这个键，页面就不显示这一行；打开后接口给了才显示（CR-20260918-07）——开关的判断全在后端，页面不读系统参数。收样段其余字段、冻存信息与核验人仍然全页不出现。
  无效时顶部红条 + 「修改后重新提交」（仅 `editable=true` 时出现，进 `mode=edit`）。

## 3 边界（明确不做）

- 不做拍照识别（OCR-MP-001），只在表单顶部留出识别条的插槽
- 不做内部管理表格页、类器官收样填写页与它的历史页签（SAMPLE-MP-002）
- 不做草稿箱、不做离线暂存
- 外部不能删除已提交的样本（甲方没提；无效的放着不管）
- 不做逐次修改日志（历史编辑记录只列记录）
- 不做系统参数 `lqg.ext.show-internal-no` 的后端读取与工作台上的开关页（AUTH-EXT-002 与工作台系统管理；本张只保证外部详情照接口给的渲染）

## 4 完工报告要求

1. 外部：填写页、历史编辑记录（开关开 / 关各一）、详情（有效 / 无效各一）截图；内部：填写页（能看到收样信息一组）、修改模式（有效样本可改、待核验样本只读）、只读模式截图；
   内部历史编辑记录两张：开关关着（中心全员的记录，每行看得见经手人——本人显示「我」，别人显示姓名）、开关打开（只剩本人的）（CR-20260918-07）
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
