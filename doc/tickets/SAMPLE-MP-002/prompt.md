---
ticket: SAMPLE-MP-002
track: SAMPLE
phase: D2
size: L
req_refs:
  - REQ-SAMPLE-007
  - REQ-SAMPLE-004
  - REQ-SAMPLE-015
  - REQ-SYS-016
  - REQ-SYS-019
  - REQ-SAMPLE-016
depends_on:
  - SAMPLE-MP-001
  - AUTH-EXT-001
touches:
  - code/miniapp/src/pages/organoid/**
  - code/miniapp/src/pages/ledger/**
  - code/miniapp/src/components/lqg/LedgerTable.vue
  - code/miniapp/src/api/ledger.ts
  - code/miniapp/src/pages/history/sources.ts
  - code/miniapp/src/pages/me/index.vue
  - code/miniapp/src/pages/verify/**
  - code/miniapp/src/api/verify.ts
  - code/miniapp/src/components/lqg/ReasonSheet.vue
adr_refs: []
blueprint_refs:
  - UI:mp.ledger
  - UI:mp.sample.list
  - UI:mp.organoid.form
  - FLOW:F-MP-01.step3
  - FLOW:F-SAMPLE-02.step2
  - FLOW:F-SAMPLE-02.step6
  - FLOW:F-MP-01.step4
  - UI:mp.verify.list
  - UI:mp.verify.sample
  - UI:mp.verify.embed
  - FIELD:t_lqg_sample.organoid_type
  - FIELD:t_lqg_sample.passage
accept:
  - name: "表格页的类器官工作表与库里独立数的一致（行上带代数）；类器官收样内部录入落成内部有效、带代数、修改模式改得动；给组织样本补丁带代数被拒；外部提交落成待核验且收样段为空、提交人是本人；两种身份的历史编辑记录都找得回刚填的那条"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/sample/list?pageSize=100&sampleKind=organoid' | jq -e '([.rows[].id|tostring]|sort)==["9000001009"] and .rows[0].passage=="P3"' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND sample_kind='organoid'" --eq "$(bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sampleKind=organoid' | jq -r '.total')" &&
      bash doc/verify/api.sh --as staff POST /mp/int/sample '{"species":"人","sampleKind":"organoid","sourceUnitId":9000009002,"organoidType":"肝类器官","passage":"p5","receiveDate":"2026-09-17","internalNo":"T-oco55","hasViabilityReport":"Y","operatorName":"李工"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT sample_kind || '|' || submit_source || '|' || verify_status || '|' || source_unit_name || '|' || has_viability_report || '|' || COALESCE(passage,'-') FROM t_lqg_sample WHERE internal_no='T-oco55'" --eq "organoid|internal|valid|B 大学|Y|P5" &&
      bash doc/verify/api.sh --as staff PUT /mp/int/sample '{"species":"人","id":9000001008,"passage":"P3"}' | jq -e '.code==400 and (.msg|contains("代数"))' &&
      python3 doc/verify/db.py --sql "SELECT COALESCE(passage,'-') FROM t_lqg_sample WHERE id=9000001008" --eq="-" &&
      bash doc/verify/api.sh --as staff PUT /mp/int/sample "$(printf '{"id":%s,"operatorName":"王工"}' "$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_sample WHERE internal_no='T-oco55'" | head -1)")" | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT operator_name || '|' || sample_kind || '|' || verify_status FROM t_lqg_sample WHERE internal_no='T-oco55'" --eq "王工|organoid|valid" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sampleKind=organoid&mine=true' | jq -e '[.rows[].internalNo] == ["T-oco55","T-oco01"]' &&
      bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"species":"人","sourceUnitName":"A 医院","organoidType":"胃类器官","remark":"外部送类器官"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT s.sample_kind || '|' || s.verify_status || '|' || COALESCE(s.internal_no,'-') || '|' || COALESCE(s.receive_date::text,'-') || '|' || p.real_name FROM t_lqg_sample s JOIN t_lqg_ext_profile p ON p.user_id = s.submitter_id WHERE s.organoid_type='胃类器官' AND s.del_flag='0'" --eq "organoid|pending|-|-|赵医生" &&
      bash doc/verify/api.sh --as extC GET '/mp/ext/sample/list?pageSize=100&sampleKind=organoid&onlyMine=true' | jq -e '[.rows[].organoidType]==["胃类器官"] and .rows[0].editable==true and .rows[0].mine==true' &&
      bash doc/verify/api.sh --as extA GET '/mp/ext/sample/list?pageSize=100&sampleKind=organoid' | jq -e '.rows==[]' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      工作表的 sampleKind 没传到后端（前端拿全量自己筛）→ 第 1 段拿到 9 行红；total 与库里独立数的对不上红。两侧不同源：一侧走接口，一侧直连库。
      选了单位 id 却没落单位名快照 → source_unit_name 不是「B 大学」红；导出时「来源单位」一列会是空的。
      类器官的修改模式其实走了 POST → 内部编号 T-oco55 撞号被拒、改不动，「王工」那段红。
      内部历史不按最后修改时间倒序 → T-oco55 排不到 T-oco01 前面红。
      外部类器官页复用内部的七项表单直接提交到 /mp/int → 外部 403，POST 那段红；提交到外部接口但 submitter 没落本人 → join 出来不是赵医生红。
      外部历史的类器官页签把别组的也列出来 → extA 那段不是空数组红。
      代数（CR-20260924-10）：列表行没带 passage → 第 1 段 .rows[0].passage 红（seed 1009 的代数是 P3）；内部新增没落代数或没把小写 p5 转成 P5 → 「…|P5」那段红；小程序补丁给组织样本写进了代数（本类别没有这一项却不拒）→ 400 那段或随后的库内读回红。
  - name: "表格页的列名、列序只有一个来源，且与甲方四份模板原件逐字对得上（冻结列从模板里去掉、追加列在最后）；构建产物里有表格页与类器官填写页；页面里不手写列、没有新增与保存；从表格页点一行进的是只读模式、不直接进修改模式，合作单位送来待核验的那一条进核验页（CR-20260924-10）；页底小字是「核验、冻存登记在小程序和网页工作台都能做」、没有「修改」二字（CR-20260918-07 / CR-20260924-10）；类器官表「代数」插在「类器官类型」后（fixture 的 inserted）；表格页与历史编辑记录触底分页，「共 N 条」只认后端 total、不再写死 pageSize=100 只取第一页（CR-20260923-09）"
    form: DATA
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
      test -f dist/build/mp-weixin/pages/ledger/index.js && test -f dist/build/mp-weixin/pages/organoid/form.js &&
      grep -q 'doc/verify/fixtures/ledger-columns-cases.json' src/pages/ledger/columns.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/ledger/columns.fixture.spec.ts &&
      pnpm vitest run src/pages/ledger/columns.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-ledger-cols.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/lqg-ledger-cols.json &&
      grep -q '@/components/lqg/LedgerTable.vue' src/pages/ledger/index.vue &&
      ! grep -nE '来源单位|供体姓名|冻存样品|石蜡块编号' src/pages/ledger/index.vue src/components/lqg/LedgerTable.vue &&
      ! grep -nE '新增|保存|核验并|判为' src/pages/ledger/index.vue src/components/lqg/LedgerTable.vue &&
      grep -q 'mode=view' src/pages/ledger/sheets.ts && ! grep -q 'mode=edit' src/pages/ledger/sheets.ts && grep -q 'verifyTarget' src/pages/ledger/sheets.ts &&
      pnpm vitest run src/pages/ledger/sheets.spec.ts --reporter=json --outputFile=/tmp/lqg-ledger-sheets.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 15' /tmp/lqg-ledger-sheets.json &&
      grep -q '核验、冻存登记在小程序和网页工作台都能做' src/pages/ledger/index.vue && ! grep -q '请到网页工作台' src/pages/ledger/index.vue && ! grep -q '修改、核验' src/pages/ledger/index.vue &&
      grep -q usePagedList src/pages/ledger/index.vue && grep -q usePagedList src/pages/history/index.vue &&
      pnpm vitest run src/utils/paging.spec.ts --reporter=json --outputFile=/tmp/lqg-paging.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 5' /tmp/lqg-paging.json &&
      cd ../.. &&
      for S in "tissue:样本记录信息表模板" "organoid:类器官收样记录模板" "embed:石蜡包埋送样记录模板" "cryo:-80冻存模板"; do
        diff <(python3 doc/verify/xlsx_header.py --print-header --template "_input/templates/${S#*:}.xlsx") <(jq -r --arg k "${S%%:*}" '.sheets[$k].template[]' doc/verify/fixtures/ledger-columns-cases.json) >/dev/null || exit 1;
      done &&
      jq -e '.sheets | to_entries | all(.value as $v | $v.expect == ((reduce ($v.inserted // [])[] as $i ([$v.template[] | select(. != $v.frozen)]; (index($i.after) + 1) as $k | .[:$k] + [$i.label] + .[$k:])) + $v.extra))' doc/verify/fixtures/ledger-columns-cases.json &&
      jq -e '(.sheets.organoid.inserted | map([.label, .after])) == [["种属","来源单位"],["代数","类器官类型"]] and (.sheets.tissue.inserted | map([.label, .after])) == [["种属","来源单位"]] and (.sheets.embed.inserted | map([.label, .after])) == [["种属","样本编号"]] and ([.sheets[].inserted[]?.source] | all(length > 0))' doc/verify/fixtures/ledger-columns-cases.json
    counterfeit: |-
      页面里手写了一份列清单（顺手把「有无固定」写成「是否固定」、把「mark的表达情况」改成「marker 表达」）→ index.vue / LedgerTable.vue 里出现列名红：列名只许从 ledgerColumns 来。
      fixture 被改得去迁就代码、不再是甲方原件的表头 → diff 红。两侧不同源：一侧是甲方发来的 xlsx 原件，一侧是 fixture。
      冻结列没从模板列里去掉（内部编号出现两次）或追加列插到了中间 → 最后一段 jq 红，spec 用例也红。
      表格页还留着 CR-04 时的「＋ 新增一行」，或点一行直接进修改模式（跳过只读页）→ 「新增」grep 红、mode=edit 红：表格本身只读，要改是从只读页右上角的「修改」进（CR-20260918-07）。
      页底小字照抄旧版「修改、核验、冻存取用请到网页工作台」或 CR-20260918-07 那版「核验、冻存取用请到网页工作台」→ 那几段 grep 红：表格页已经能改、能核验、冻存能登记了，这行小字里不该再把人支到工作台、也不该有「修改」（CR-20260924-10）。
      待核验的行照旧进只读详情（核验只能去工作台）→ verifyTarget 那段与 sheets.spec「点一行去哪」的用例红。
      页签短名又各写各的（表格页、历史编辑记录、待核验各抄一份，或把全称塞回页签、390 宽下折成两行）→ sheets.spec「页签短名」四例红（短名只在 `pages/index/entries.ts#ENTRY_SHORT` 一处定义、三处都取它、每个不超过 5 个字，CR-20260924-11）；删掉这几例凑绿 → 通过数不足 15 红。
      「代数」插错位置（追加到最后、或插在收样日期后）、或为了让 jq 过把「代数」塞进 template（fixture 不再是甲方原件的表头）→ 最后两段 jq 或上面的 diff 红：模板原件没有这一列，只能以 inserted 的形式、紧跟「类器官类型」出现，并写明来源。
      列表还写死 pageSize=100 只取第一页（CR-20260923-09 之前的形态）→ 两个 usePagedList grep 红：表格页写着「共 N 条」，第 101 条以后永远看不到。分页工具把已加载行数当 total、分页边界上同一行出现两次、换筛选后上一轮晚到的响应拼进来、第一页失败后触底还在请求 → paging.spec 用例红（共 5 例，skip 掉就不够数）。
      先 rm 产物目录再构建：上次剩下的 dist 骗不过去。
  - name: "类器官收样填写页布局只认后端给的身份、状态与入口模式：外部只渲染四项（来源单位、类器官类型、代数、备注）且永远没有收样段、内部八项（代数紧跟类器官类型，CR-20260924-10）；外部有效与同组别人的只读；内部看外部待核验只读（只读页也没有「修改」）；内部管理进来的先是只读模式、右上角「修改」才切到修改模式；身份缺失什么都不渲染"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/organoid-form-cases.json' src/pages/organoid/layout.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/organoid/layout.fixture.spec.ts &&
      pnpm vitest run src/pages/organoid/layout.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-organoid-layout.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-organoid-layout.json &&
      node -e "const f=require('../../doc/verify/fixtures/organoid-form-cases.json'); const c=f.cases; const ext=c.filter(x=>x.identity==='external'); const ins=f.inserted||[]; const follows=(a,i)=>a[a.indexOf(i.after)+1]===i.field; if(c.length!==9||f.externalFields.length!==5||f.internalFields.length!==9||ins.map(i=>i.field).join()!=='species,passage'||!ins.every(i=>follows(f.externalFields,i)&&follows(f.internalFields,i))||ext.some(x=>x.expect.fields.some(k=>f.internalFields.includes(k)&&!f.externalFields.includes(k)))||c.filter(x=>!x.expect.editable).length!==5) process.exit(1)" &&
      ! grep -nE 'donorName|hospitalNo|tissueType' src/pages/organoid/form.vue && grep -q 'organoidType' src/pages/organoid/form.vue
    counterfeit: |-
      外部复用内部七项再把收样段 v-show 掉 → 「外部新增」用例的 fields 里带着 receiveDate 红。
      外部 editable 漏了 mine → 「外部看同组别人的待核验」用例红。
      内部看外部送来的待核验可改 → 用例红：会绕过工作台核验直接把收样段填了。
      只读模式按状态判断可改（把「修改」当成进来就能编辑）→ 「内部管理只读查看」用例红：进来先是只读，点了右上角「修改」才是 mode=edit（CR-20260918-07）。
      只读页把「修改」无脑显示，外部送来还没核验的样本也给改 → 「内部看外部送来的待核验」用例红：按钮显不显示与 mode=edit 下的 editable 同源。身份缺失按内部渲染 → 用例红。
      类器官填写页复用组织样本的表单 → 出现 donorName 等红（模板 B 只有 7 列）。删病灶用例来过关 → node 那段红。
      代数只加给内部、外部没有（甲方原话「合作单位和内部人员的都要再添加一项」）→ externalFields 不是 4 项红；代数追加在最后而不是紧跟类器官类型 → next() 那两格红。
  - name: "小程序也能核验（CR-20260924-10）：待核验列表与两个核验页注册进了构建产物；核验页的必填与请求体形状（送检段不改不带、改了带整段；石蜡包埋 fill 只带改过的键）由 fixture 驱动的单测钉住；内部身份（小程序 token）调工作台同一个核验接口能判有效 / 无效且业务规则照旧、首页待办数随之变，外部身份 403"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
      jq -e '(["pages/verify/index","pages/verify/sample","pages/verify/embed"] - .pages) == []' dist/build/mp-weixin/app.json &&
      grep -q 'fixtures/verify-form-cases.json' src/pages/verify/rules.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/verify/rules.fixture.spec.ts src/pages/verify/tabs.spec.ts &&
      pnpm vitest run src/pages/verify/rules.fixture.spec.ts src/pages/verify/tabs.spec.ts --reporter=json --outputFile=/tmp/lqg-mp-verify.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 40' /tmp/lqg-mp-verify.json &&
      grep -q '/lqg/sample/' src/api/verify.ts && grep -q '/lqg/embed/' src/api/verify.ts &&
      cd ../.. && bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as extA --fresh-module ruoyi-lqg --bizcode PUT /lqg/sample/9000001007/verify '{"action":"invalid","reason":"外部不许核验"}' | grep -qE '^403' &&
      bash doc/verify/api.sh --as extA --bizcode PUT /lqg/embed/9000002006/verify '{"action":"invalid","reason":"外部不许核验"}' | grep -qE '^403' &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001007/verify '{"action":"invalid"}' | jq -e '.code==400 and (.msg|contains("判无效必须写原因"))' &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-24","internalNo":"T-hli01"}' | jq -e '.code==400 and (.msg|contains("已存在"))' &&
      bash doc/verify/api.sh --as staff PUT /lqg/embed/9000002006/verify '{"action":"valid","paraffinBlockNo":"T-MPV-1"}' | jq -e '(.code==400 or .code==500) and (.msg|contains("所挂样本还未核验有效"))' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT verify_status FROM t_lqg_sample WHERE id=9000001002) || '|' || (SELECT verify_status FROM t_lqg_embed WHERE id=9000002006) || '|' || (SELECT verify_status FROM t_lqg_sample WHERE id=9000001007)" --eq "pending|pending|pending" &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001007/verify '{"action":"invalid","reason":"小程序核验：信息不全"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-24","internalNo":"T-mpv01","operatorName":"李工"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff PUT /lqg/embed/9000002006/verify '{"action":"valid","paraffinBlockNo":"T-MPV-1","fill":{"sectionTime":"2026-09-24"}}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT verify_status || '|' || invalid_reason FROM t_lqg_sample WHERE id=9000001007) || '|' || (SELECT verify_status || '|' || internal_no FROM t_lqg_sample WHERE id=9000001002) || '|' || (SELECT verify_status || '|' || paraffin_block_no || '|' || section_time::text FROM t_lqg_embed WHERE id=9000002006)" --eq "invalid|小程序核验：信息不全|valid|T-mpv01|valid|T-MPV-1|2026-09-24" &&
      bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.pendingSamples==0 and .data.pendingEmbeds==0' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      新页面没注册进 pages.json（uni-pages 没重新生成、或放错目录被当成组件）→ app.json 那段红：首页「待处理」点进去是白屏。
      核验页把送检段拆成「只发改过的键」（后端 submitSegment 是整段替换，其余键会被清空）→ rules.fixture.spec 里「改了一项就带整段、没改的带原值」的用例红；反过来一项没改也带整段 → 「不带 submitSegment」用例红。
      石蜡包埋的 fill 带了整份（把没改的键也发过去，判无效时后端对实验室补填项回 400）→ 「fill 只带改过的键」用例红。删用例凑过关 → 40 例不够数红。
      小程序另写了一个核验接口、或在前端自己拼状态直接调 PUT /mp/int/sample 改 verify_status → api/verify.ts 里没有 /lqg/sample/、/lqg/embed/ 红；而后端的 PUT /mp/int/sample 对待核验记录本来就回 400，前端也绕不过去。
      为了让小程序能调，把核验接口的权限放宽到「登录即可」→ extA 那两段拿不到 403，红：外部给自己的样本判了有效。
      小程序这条路径跳过了工作台的业务规则（判无效不要原因、内部编号不查重、所挂样本没核验就给石蜡块编号）→ 三段 400 / 拒绝各自红，随后「三条都仍是 pending」的库内读回红。
      核完首页数字不变（首页读的是缓存或另一套口径）→ 最后 todo 那段红；fill 没与核验结论同一事务保存 → section_time 那一格红。
---

# SAMPLE-MP-002 · 小程序 · 内部管理表格页（四张表共用、只读表格 + 修改入口 + 待核验进核验页：顶部切换、首列冻结、列照模板）与样本两个工作表；类器官收样填写页（内外部、修改与只读模式，含代数）与历史编辑记录的类器官页签；小程序待核验列表与核验页（CR-20260924-10）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-MP-001**、**AUTH-EXT-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` §5 与 §6 本页那一行；样式类用 SYS-MP-001 已落的 `src/style/components.scss`（`.lqg-*`），零颜色字面量。下面提到的图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**
  - 表格页看图 `doc/design-options/gallery.html#mp-ledger`（表格）、`#mp-ledger-view`（只读详情，右上角「修改」）、`#mp-ledger-edit`（修改模式）。挂在「我的 → 内部管理」（Kevin 2026-09-17 晚定，CR-20260917-05；⑩ 原来的 A / B 两种挂法都作废）。**表格本身没有新增、没有行内编辑**，底部只有「导出 Excel」；点一行进该表填写页的只读模式，只读页右上角有「修改」，点它切到修改模式（CR-20260918-07）；合作单位送来、待核验的那一条点进去是核验页（CR-20260924-10）
  - **CR-20260924-10（以它为准）**：① 类器官收样记录加「代数」（`passage`，选填，形如 P3，紧跟类器官类型；外部四项、内部八项；表格页类器官表在「类器官类型」后插一列「代数」，fixture 里以 `inserted` 记、模板原件不动）；② 甲方「要求小程序和工作台界面都能操作」：小程序新增「待核验」列表与核验页（`UI:mp.verify.list` / `UI:mp.verify.sample` / `UI:mp.verify.embed`、`FLOW:F-MP-01.step4`），调工作台同一个 `PUT /lqg/sample/{id}/verify`、`PUT /lqg/embed/{id}/verify`，规则同一份；改判仍只在工作台。核验页的布局与请求体形状用例在 `doc/verify/fixtures/verify-form-cases.json`
  - 甲方四份 xlsx 模板原件第 1 行（`_input/templates/`）：表格页的列名、列序照它；期望已抄进 `doc/verify/fixtures/ledger-columns-cases.json`，并由 accept 第 2 条和原件逐字对账
  - 类器官收样填写页的布局用例在 `doc/verify/fixtures/organoid-form-cases.json`：外部四项、内部八项（含代数，CR-20260924-10）、三种模式
- [ ] 口径复述（本张最容易做反的）：
  1. **列名、列序只有一个来源**：`ledgerColumns(sheet)`，期望从 fixture 读。表格页与 `LedgerTable` 里不许再手写列清单。
  2. **第一列冻结**（样本 = 内部编号；还没有内部编号的待核验外部样本显示送检单号 +「待核验」），冻结格第二行小字放状态；其余列横向滑动。**手机上不做格子里直接编辑**。
  3. **表格本身只读**：页面上没有「新增」「保存」，也没有行内编辑、表格上不做核验。点一行进该表填写页的 `mode=view`，只读页右上角「修改」切到 `mode=edit`（CR-20260918-07）：内部人员对四张表的任何记录都能改，不限本人录的。
     **合作单位送来、待核验的那一条点进去是核验页**（`sheets.ts` 的 `target` 与待核验列表同一个 `verifyTarget`，CR-20260924-10）；核完回来表格重新取数、那一行不再浅黄。改完写 update_by / update_time，那条记录随之出现在改动人的「历史编辑记录」里。新增仍走首页；核验、冻存登记小程序与工作台都能做，改判只在工作台。
  4. **类器官收样外部也能填**（CR-20260917-05）：外部只渲染来源单位、类器官类型、代数、备注四项（代数 CR-20260924-10 加），走 `/mp/ext/organoid`；收样段不渲染（不是置灰）。
  5. 「内部管理」板块只给内部：本张把 SYS-MP-001 板块里四个入口的占位目标换成表格页；外部仍然不渲染这个板块。
  6. **小程序核验页的送检段是整段**（CR-20260924-10）：后端 `submitSegment` 是整段替换、没给的键写成空，所以送检信息一项没改就**不带** `submitSegment`，改了任意一项就带**整段**（没改的项带原值，类器官含代数）；石蜡包埋的 `fill` 是补丁语义，只带改过的键。业务错误原样弹框、停在本页。

## 1 背景与口径

类器官收样记录 7 列（REQ-SAMPLE-007），方案 v6 L99：内部人员可填写全部四张表、查看全部样本。
2026-09-17 甲方看设计稿 v1（CR-20260917-04）：内部人员「点击进去填写是 excel 表」（REQ-SYS-016），提交后要能改（REQ-SAMPLE-016）。
同日晚 Kevin 定（CR-20260917-05）：表格形式的全表查看挪到「我的 → 内部管理」、只读（查看、筛选、导出）；首页不再有数字；外部也能填类器官收样记录。
2026-09-18 甲方看设计图后提「还要加一条可以编辑」（CR-20260918-07）：表格本身仍然只读，点一行进该表填写页的只读模式，只读页右上角有「修改」，点了切到修改模式。
所以四张表共用一个表格页（只读表格 + 修改入口）：本张把页面、表格组件、四张表的列一次定下，并接上样本两个工作表；石蜡包埋、-80 冻存两个工作表由 EMBED-MP-001 / CRYO-MP-001 注册。

## 2 实现要点

- `pages/ledger/index`（表格页，按 `UI:mp.ledger`）：路由参数 `sheet`（`tissue | organoid | embed | cryo`）+ 可选 `verifyStatus`；没注册的 `sheet` 落到第一个已注册的工作表。
  顶部工作表切换条读 `pages/ledger/sheets.ts` 的注册表（每项 = key、短名、全称、取数函数、筛选项、冻结格第二行、点行动作）——本张注册 `tissue`、`organoid`，没注册的不显示。
  底部只有「导出 Excel」（本张置灰，SYS-EXPORT-001 点亮）；页底小字「核验、冻存登记在小程序和网页工作台都能做」（CR-20260924-10；此前 CR-20260918-07 是「核验、冻存取用请到网页工作台」，都没有「修改」二字）。
- `pages/ledger/columns.ts`：`ledgerColumns(sheet)` → `{ frozen: {key, label}, columns: [{key, label}] }`，四张表本张一次定完（冻结列从模板列里去掉，其余照模板第 1 行，模板没有的追加在后）；
  `columns.fixture.spec.ts` 从 `doc/verify/fixtures/ledger-columns-cases.json` 读每张表的期望（冻结列标签 + 其余列标签）。
- `components/lqg/LedgerTable.vue`：表头固定、首列 `position: sticky; left: 0`、横向 `scroll-view`；行点击抛 `row-tap`；行底色由 `rowTone(row)` 给（待核验浅黄、超期浅红）。**直接 `.vue` 路径导入**。
- 样本两个工作表：数据走 `/mp/int/sample/list`（`sampleKind` 区分）；供体姓名列掩码（保留姓）；「切片染色」列本张留空（SAMPLE-HINT-001 接）；
  点一行 → `pages/sample/form?id=&mode=view`（组织）/ `pages/organoid/form?id=&mode=view`（类器官），只读页右上角「修改」再切到 `mode=edit`（CR-20260918-07：行本身不直接进修改模式）；待核验的行 → `verifyTarget(...)` 进核验页（CR-20260924-10）；两个工作表之间切换时筛选条件保留。
  类器官表的列在「类器官类型」后插入「代数」（`columns.ts` 按「模板列 + 插入列 + 追加列」拼，期望读 fixture 的 `inserted`）。
- `pages/organoid/form`：布局由纯函数 `organoidLayout(identity, verifyStatus, mine, mode)`（`src/pages/organoid/layout.ts`）决定，`layout.fixture.spec.ts` 的用例与期望从 `doc/verify/fixtures/organoid-form-cases.json` 读。
  内部八项（来源单位、类器官类型〔联想 `type=organoid`〕、代数、收样日期、内部编号、处理时间、细胞活率报告〔两按钮〕、操作人）→ `POST /mp/int/sample`（`sampleKind=organoid`）；
  外部四项（来源单位〔默认档案里的单位〕、类器官类型、代数、备注）→ `POST /mp/ext/organoid`；代数占位「选填，如 P3」，提交前按「P 加数字」预检（小写 p 转大写），后端同一规则兜底（CR-20260924-10）；`mode=edit` 回填后 `PUT`（外部 `PUT /mp/ext/organoid/{id}`），顶部小字「最后修改：某某 · 时间」；`mode=view` 全部只读，右上角「修改」切到 `mode=edit`（CR-20260918-07）——按钮显不显示与能不能改同源：`organoidLayout(identity, verifyStatus, mine, 'edit').editable` 为真才显示（外部送来还没核验的样本因此没有「修改」）。
- 历史编辑记录：在 `pages/history/sources.ts` 注册 `organoid`——外部 `GET /mp/ext/sample/list?sampleKind=organoid&onlyMine=`，内部 `GET /mp/int/sample/list?sampleKind=organoid&mine=true`；
  点行：外部 `mine && editable` → `pages/organoid/form?id=&mode=edit`，外部其余 → `mode=view`；内部 → `mode=edit`。
- 「我的 → 内部管理」四个入口 → `pages/ledger/index?sheet=tissue|organoid|embed|cryo`。
- 小程序核验（CR-20260924-10）：`pages/verify/index`（待核验列表，三个页签，数据 = `/mp/int/sample/list?verifyStatus=pending&sampleKind=…`、`/mp/int/embed/list?verifyStatus=pending`，每个页签的数取 `total`）、`pages/verify/sample`（组织 / 类器官共用）、`pages/verify/embed`；纯函数在 `pages/verify/{rules,tabs}.ts`，接口在 `src/api/verify.ts`；判无效的原因面板 `components/lqg/ReasonSheet.vue`（限 200 字）。入口：首页「待处理」（SYS-MP-001）与本表格页点待核验的行。

## 3 边界（明确不做）

- 表格上不做核验（待核验的那一条点进去是核验页，CR-20260924-10）；小程序里不做改判（已核验的改判仍只在工作台）；表格上不做新增、不做行内编辑（要改走只读页右上角的「修改」，CR-20260918-07）
- 不做工作台首页（SYS-HOME-001）
- 不做石蜡包埋、-80 冻存两个工作表的数据（EMBED-MP-001 / CRYO-MP-001 注册）
- 不做导出（SYS-EXPORT-001）
- 不做格子里直接编辑、不做列的显示隐藏与拖动排序

## 4 完工报告要求

1. 表格页两个工作表（含左右滑动后的截图）、从表格页点进去的只读填写页与点「修改」后切到的修改模式（CR-20260918-07）、类器官收样填写页（内部 / 外部 / 修改模式）、历史编辑记录的类器官页签截图
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 2 补触底分页断言（表格页与历史编辑记录都用 usePagedList，paging.spec 五例全过，F3）。
- 2026-09-24 按 CR-20260924-10 更新：① 代数——accept 1 断列表行带 P3、内部新增带 p5 落成 P5、给组织样本补丁带代数 400 且库里不写；accept 2 的列序 jq 改为「模板列 + inserted + extra」并钉住 inserted 只有「代数@类器官类型」且写了来源；accept 3 的外部 / 内部字段数改为 4 / 8 并断代数紧跟类器官类型；② 小程序核验——accept 2 页底小字改为「核验、冻存登记在小程序和网页工作台都能做」、补 `verifyTarget` 与 sheets.spec；**新增 accept 4**（构建产物有三个核验页、核验规则单测 ≥ 40 例、staff 走小程序 token 调 `/lqg/sample|embed/{id}/verify` 判有效 / 无效且规则照旧、首页待办归零、extA 403）；blueprint_refs 补 F-MP-01.step4、UI:mp.verify.*、FIELD:t_lqg_sample.passage；touches 补核验页；§0–§3 按新口径改写。
- 2026-09-24 按 CR-20260924-11 更新：accept 2 里 sheets.spec 的通过数门槛由 ≥ 3 提到 ≥ 15（现有 15 例，含新增的「页签短名」4 例：短名只在 `entries.ts#ENTRY_SHORT` 一处定义、表格页与待核验页签都取它、每个不超过 5 个字），反例补一句；run 其余不动。
