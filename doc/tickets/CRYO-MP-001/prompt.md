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
  - code/miniapp/src/components/lqg/CryoFlowForm.vue
  - code/miniapp/src/components/lqg/CryoLn2Form.vue
  - code/miniapp/src/pages/cryo/flow.ts
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
  - FLOW:F-CRYO-01.step4
  - FLOW:F-CRYO-02.step1
  - FLOW:F-CRYO-02.step5
accept:
  - name: "工作表的页签计数、逐批剩余与超期天数和库里独立数的一致且钉在 seed 上；改冻存数量走同一套逐笔校验（改到剩余为负被拒、改到不为负成功，别人录的也能改）；内部历史默认列中心全部内部人员的记录、每行带经手人，「只看我提交的」（`mine=true`）才收窄到本人（CR-20260918-07）；只给内部角色"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":2,"ln2":2,"emptied":1} and (.rows|length)==7 and ([.rows[]|select((.id|tostring)=="9000003001")|[.remainingQty,.overdueDays,.location]]==[[6,6,"minus80"]])' &&
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
  - name: "冻存取用登记的写口只有 /lqg/cryo/** 一份（CR-20260924-10）：/mp/int 上取走、改登记、删登记、转液氮仍然不存在、库里不变；小程序内部身份调 /lqg/cryo 的写口能取走、转液氮（之后从液氮取）、改删一笔，逐笔校验照旧并指出是哪一笔，取空后「已取空」页签与行标记跟着变；外部身份 403；取用登记逐笔剩余与 seed 一致"
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
      D="$(date +%F)" &&
      bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003002/flow "{\"flowType\":\"take\",\"qty\":1,\"purpose\":\"小程序取走\",\"flowTime\":\"${D} 08:00:00\"}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003002/to-ln2 "{\"toLn2Time\":\"${D}\",\"ln2Location\":\"2号罐-1架-A3\"}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003002/flow "{\"flowType\":\"take\",\"qty\":3,\"purpose\":\"从液氮取完\",\"flowTime\":\"${D} 09:00:00\"}" | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT string_agg(from_location || ':' || delta, ',' ORDER BY flow_time) FROM t_lqg_cryo_flow WHERE batch_id=9000003002 AND del_flag='0'" --eq "minus80:-1,ln2:-3" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.emptied==2 and ([.rows[]|select((.id|tostring)=="9000003002")|[.remainingQty,.emptied,.location]]==[[0,true,"ln2"]])' &&
      F1="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_cryo_flow WHERE batch_id=9000003002 AND del_flag='0' ORDER BY flow_time LIMIT 1" | head -1)" &&
      F2="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_cryo_flow WHERE batch_id=9000003002 AND del_flag='0' ORDER BY flow_time DESC LIMIT 1" | head -1)" &&
      bash doc/verify/api.sh --as staff PUT "/lqg/cryo/batch/9000003002/flow/${F1}" '{"qty":2}' | jq -e '.code==400 and (.msg|test("这样改会让 .*那一笔（-3 支）之后的剩余变成 -1 支，没有保存"))' &&
      bash doc/verify/api.sh --as staff DELETE "/lqg/cryo/batch/9000003002/flow/${F2}" | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT delta FROM t_lqg_cryo_flow WHERE id=${F1}) || '|' || (SELECT del_flag FROM t_lqg_cryo_flow WHERE id=${F2})" --eq="-1|1" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.emptied==1 and ([.rows[]|select((.id|tostring)=="9000003002")|[.remainingQty,.emptied]]==[[3,false]])' &&
      bash doc/verify/api.sh --as extA --bizcode POST /lqg/cryo/batch/9000003002/flow '{"flowType":"take","qty":1}' | grep -qE '^403' &&
      bash doc/verify/api.sh --as extA --bizcode PUT /lqg/cryo/batch/9000003001/to-ln2 '{"toLn2Time":"2026-09-17","ln2Location":"1号罐"}' | grep -qE '^403' &&
      (cd code/miniapp && grep -q '/lqg/cryo/batch/' src/pages/cryo/flow.ts && ! grep -rnE 'http\.(post|put|delete)[^;]*/mp/int/cryo/batch/[^;]*(flow|to-ln2)' src/pages/cryo src/api/cryo.ts src/components/lqg) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      为了让小程序能登记，在 /mp/int 上另开一份取走 / 改删登记 / 转液氮的写口 → 这四段拿到 200 或 400 而不是「没有这个接口」红，库里的登记数或 3101 的支数随之变化红：两份写口迟早规则不一样（CR-20260924-10 定的是小程序直接调 /lqg/cryo 那一份，规则一字不差）。
      小程序内部身份调 /lqg/cryo 写口被挡（权限串没授给 lqg_internal、或按客户端类型拦了）→ staff 取走那段拿不到 200 红：甲方要的是「小程序和工作台界面都能操作」。
      转液氮之后取走的 from_location 还是 minus80（没按批次当时的位置带）→ string_agg 那段不是「minus80:-1,ln2:-3」红：甲方原话「一般情况下，我们都是从液氮取走细胞」，追溯要对得上。
      剩余到 0 后行上没标 emptied、或「已取空」页签数前端自己数 → emptied==2 那段红；删一笔之后页签没退回去 → emptied==1 那段红。
      改一笔被拒时仍是给改初始支数用的那句「已取走 N 支，冻存数量不能少于 N」（用户会以为要去改冻存数量）→ test(...) 那段红：要指出是哪一笔、变成负几支。
      为了让小程序能调，把 /lqg/cryo 写口的权限放宽到「登录即可」→ extA 两段拿不到 403 红：冻存信息明确不对外。
      取用登记不按时间倒序、或操作后剩余没按时间正序累计 → 第 1 段红。
      外部能调 flows → 403 那段红：冻存信息明确不对外。
  - name: "转液氮之后提醒就没了（CR-20260918-07 甲方问「转移后还会有提示吗」）：工作台登记转液氮后，小程序冻存工作表的超期页签计数 2→1、该批次不再出现在超期页签、行上 overdue=false 且位置变液氮；取空的批次同样不在超期页签里；登记错了在工作台编辑抽屉里清掉转液氮时间（`toLn2Time:null`）即撤销，提醒当场回来；小程序修改接口同一套补丁语义，清必填项被拒且库里不变（CR-20260923-09）"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==2 and ([.rows[]|select(.overdue)|.id|tostring]|sort)==["9000003001","9000003005"]' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -e '([.rows[].id|tostring]|sort)==["9000003001","9000003005"]' &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"2号罐-1架-A1"}))')" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":1,"ln2":3,"emptied":1} and ([.rows[]|select((.id|tostring)=="9000003001")|[.overdue,.location]]==[[false,"ln2"]])' &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -e '([.rows[].id|tostring])==["9000003005"]' &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch '{"id":9000003001,"toLn2Time":null,"ln2Location":null}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch '{"id":9000003001,"initQty":null}' | grep -qE '^400' &&
      python3 doc/verify/db.py --sql "SELECT COALESCE(to_ln2_time::text,'-') || '|' || COALESCE(ln2_location,'-') || '|' || init_qty FROM t_lqg_cryo_batch WHERE id=9000003001" --eq="-|-|8" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":2,"ln2":2,"emptied":1} and ([.rows[]|select((.id|tostring)=="9000003001")|[.overdue,.location]]==[[true,"minus80"]])' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      超期做成了标志位、靠每天 8 点的定时任务刷 → 转完液氮当天页签里还挂着 3001 红。甲方 9-18 问的就是这一句「转移后还会有提示吗」，答的是「登记了转液氮就立刻不提示了」。
      页签数字前端按当前页 rows 自己数 → tabCounts 那段对不上红（页面接的是哪个数看截图）。
      小程序这边自己又写了一份超期 where、没调 CRYO-REMIND-001 的那一个判定函数 → 转液氮后工作台与小程序两边数字不一致红。
      只把行从超期页签里去掉、`overdue` 还是 true（行仍标红底）→ 红。
      修改接口还是「null = 不动」→ 撤销那一步拿到 200，库里转液氮时间还在、计数回不到 2 红：登记错了的转液氮永远撤不掉，这一批再也不会有超期提醒（CR-20260923-09）。
      超期按「当天登记过转液氮」之类的标志位算、撤销后不重算 → 最后一段 3001 不回 `[true,"minus80"]` 红。
      小程序的修改接口另写了一个 update、补丁语义做成「null 一律清空」连必填项也照清 → 冻存数量那段拿不到 400（或库里 init_qty 不再是 8）红：清必填项必须被拒并写明是哪一项。
      结尾 reseed：别把改过的 3001 留给后面的断言。
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
  - name: "小程序冻存登记与取空提示的前端（CR-20260924-10）：批次详情弹层直接 .vue 路径导入登记与转液氮两张表单、有「已取空」标记、不再把人支到工作台；冻存表有第四个页签「已取空」、表格页认 ?tab= 直达；取空前确认与工作台读同一份用例（两端 spec 都读 cryo-take-cases.json，用例里「取到恰好 0 才问、补入不问、本来就是 0 不问」都在）；支数规则、位置文案、tab 解析过单测"
    form: API
    run: |-
      cd code/miniapp &&
      grep -q '@/components/lqg/CryoFlowForm.vue' src/components/lqg/CryoBatchSheet.vue && grep -q '@/components/lqg/CryoLn2Form.vue' src/components/lqg/CryoBatchSheet.vue &&
      grep -q '已取空' src/components/lqg/CryoBatchSheet.vue && ! grep -q '请到网页工作台' src/components/lqg/CryoBatchSheet.vue src/pages/cryo/form.vue &&
      grep -q "value: 'emptied'" src/pages/ledger/sheets.ts && grep -q 'cryoViewOfTab' src/pages/ledger/index.vue &&
      grep -q '登记后这一批就取空了（剩 0 支），确定吗？' src/pages/cryo/flow.ts &&
      grep -q 'fixtures/cryo-take-cases.json' src/pages/cryo/flow.fixture.spec.ts && grep -q 'fixtures/cryo-take-cases.json' ../plus-ui/src/views/lqg/cryo/flow.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/cryo/flow.fixture.spec.ts src/api/cryo.spec.ts &&
      pnpm vitest run src/pages/cryo/flow.fixture.spec.ts src/api/cryo.spec.ts --reporter=json --outputFile=/tmp/lqg-mp-cryo-flow.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 47' /tmp/lqg-mp-cryo-flow.json &&
      node -e "const c=require('../../doc/verify/fixtures/cryo-take-cases.json').cases; const yes=c.filter(x=>x.expect.confirm===true); if(c.length<11||yes.length<1||yes.some(x=>x.kind!=='take'||x.expect.remainingAfter!==0||!(x.remaining>0))||!c.some(x=>x.kind==='add'&&x.expect.confirm===false)||!c.some(x=>x.kind==='take'&&x.remaining===0&&x.expect.confirm===false)) process.exit(1)"
    counterfeit: |-
      弹层里的表单走桶口导入（小程序里渲染成空白且不报错）→ 两条 .vue 路径 grep 红。
      弹层底部照旧写「取走、补入、转液氮与修改登记请到网页工作台」、或填写页的提示还写「在工作台」→ `! grep` 那段红：甲方要的是小程序里也能登记。
      「已取空」只在工作台有、小程序冻存表没有第四个页签，或首页「-80 超期」跳过来的 `?tab=overdue` 表格页不认 → sheets / cryoViewOfTab 两段红。
      取空确认两端各写一份规则（小程序的读用例、工作台的自己写期望）→ 工作台 spec 那段 grep 红：两边迟早一个问一个不问。
      确认条件写成「取走后剩余 ≤ 0」（本来就是 0 的批次改一笔用途也问）或「补入也问」→ 用例红；删用例凑过关 → 47 例不够数、或 node 那段「必须有补入不问、本来就是 0 不问」的病灶用例缺了红。
---

# CRYO-MP-001 · 小程序 · -80 冻存记录：填写页（新增、从历史编辑记录修改）、内部管理的 -80 冻存工作表（超期 / 已取空页签）与批次详情弹层（取走、补入、转液氮、改删登记，CR-20260924-10）、历史编辑记录的冻存页签

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/CRYO` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**CRYO-REMIND-001**、**CRYO-FLOW-001**、**SAMPLE-MP-002**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` §5 与 §6 本页那一行；样式类用 SYS-MP-001 已落的 `src/style/components.scss`（`.lqg-*`），零颜色字面量。下面提到的图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**
  - 2026-09-17 晚 Kevin 定（CR-20260917-05）：「网页工作台要有最全面的功能，只是把部分查看和筛选以及导出等功能放到小程序里」。小程序里的冻存只有三件事：首页新增冻存记录、「历史编辑记录」里改本人录的、「内部管理」里只读查看。**取走、补入、转液氮、改删登记都只在工作台**（原来做这些的 CRYO-MP-002 已删除）
  - 2026-09-18 甲方看过设计图后（CR-20260918-07）改了两处口径：① 内部管理表格页从「纯只读」变成「**只读表格 + 修改入口**」——冻存这张表点一行**仍然是只读的批次详情弹层**，要改这条冻存记录从**详情右上角「修改」**进冻存填写页的修改模式，内部人员改谁录的都行；② 「我的 → 历史编辑记录」的内部视角从「本人的记录」改成「**中心全部内部人员的记录**，默认全列，顶部「只看我提交的」开关（默认关），每行显示经手人」
  - `doc/design-options/gallery.html#mp-cryo`：-80 冻存工作表与点一行弹出的批次详情（只读版，登记部分已被 CR-20260924-10 推翻，以 `UI:mp.cryo.flow` 为准）
  - **CR-20260924-10（以它为准，推翻上面「只在工作台」那几句）**：甲方 2026-09-24 第 20 行「要求小程序和工作台界面都能操作」、第 22 行「支数取空的要提示」「请参照我发你的模板，理解先后顺序」。① 批次详情弹层里能取走、补入、转液氮、改删取走 / 补入登记，**直接调工作台那一份 `/lqg/cryo/**` 写口**（`lqg_internal` 本来就有这些权限串），`/mp/int/cryo/**` 上**仍然不开**写口；盘点调整只在工作台；② 「已取空」：行上 `emptied`、第四个页签（`tabCounts.emptied`、`emptiedOnly=true`）、取走让剩余从有变成 0 前确认「登记后这一批就取空了（剩 0 支），确定吗？」（两端同一份用例 `doc/verify/fixtures/cryo-take-cases.json`）；③ 字段顺序对齐甲方 -80 冻存模板，「-80度超低温冰箱转移至液氮时间」「液氮储存位置」处处看得见；④ 表格页认 `?sheet=cryo&tab=overdue|ln2|emptied|all` 直达（首页「待处理」的 -80 超期跳 `tab=overdue`）
- [ ] 口径复述（本张最容易做反的）：
  1. 冻存工作表的列来自 `ledgerColumns('cryo')`（SAMPLE-MP-002 已定）：冻结格 = 冻存样品，第二行小字「剩 N / 初始 M 支」+ 超期红字「已超 N 天」；模板 9 列去掉冻存样品后按序，最后追加代数、当前剩余/支。
  2. 四个筛选页签（全部 / -80 超期 / 液氮 / 已取空，「已取空」CR-20260924-10 加）的数字取接口的 `tabCounts`，不在前端自己数；超期行浅红底、置顶；已取空的行冻结格小字琥珀加粗「已取空 · 初始 N 支」。
  3. **批次详情弹层**（CR-20260924-10 改）：上部写清放在哪（「-80℃ 暂存 · 冻存 N 天」用后端 `frozenDays` / 「液氮 · 位置 xxx · 转入 yyyy-mm-dd」/「液氮 · 位置 xxx · 直接进液氮」）、已取空 / 已超 N 天标记、「剩 N / 初始 M 支」，再按模板先后列全部字段（列名与顺序取 `ledgerColumns('cryo')`）；
     操作「取走」「补入」（还在 -80 没转过的多「转液氮」），点了就地换成表单；取用登记每行带操作后剩余、改过的标「已改」，取走 / 补入那两种可「改」「删」（删二次确认），盘点调整的行只看不改。
     写口**只有 `/lqg/cryo/**` 一份**：小程序直接调它（`silent`，被拒时后端原话显示在表单里）；`/mp/int/cryo/**` 上**仍然没有**这些写接口，不另开转发。弹层右上角「修改」仍只跳本条冻存记录的填写页（下一条）。
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
  PUT 与工作台 `PUT /lqg/cryo/batch` 走同一个 `CryoBatchService.update`，是**补丁语义**（CR-20260923-09）：请求体里没出现的键不改；出现且值为 `null` 或空串 = 清空；清必填项 → 400 并写明是哪一项（冻存的必填 = 所挂样本、样品名称、代数、冻存时间、冻存数量、是否暂存 -80）；
  `toLn2Time:null` = 撤销登记错的转液氮（批次回到 -80、按冻存时间重新算超期；液氮位置要一并清掉就同时传 `ln2Location:null`）。小程序填写页**不带** `toLn2Time` 这个键——不传 = 不动，保存不会把工作台登记的转液氮冲掉；撤销在工作台编辑抽屉里做。
  取用登记的写口（取走、补入、转液氮、改删）只有工作台那一份 `/lqg/cryo/**`（CRYO-FLOW-001），CR-20260924-10 起小程序内部身份直接调它，本包不开转发；「用途传空串 = 清空、盘点调整清空用途 → 400」的规则同在那里。
  列表另收 `emptiedOnly`、行带 `emptied` / `frozenDays`、`tabCounts.emptied`（CRYO-REMIND-001 的同一份片段，本包只透传）。
- 在 `pages/ledger/sheets.ts` 注册 `cryo` 工作表：数据 `/mp/int/cryo/batch/list`；筛选页签 全部 / 超期（`overdueOnly=true`）/ 液氮（`location=ln2`）/ 已取空（`emptiedOnly=true`，CR-20260924-10）；点一行打开 `CryoBatchSheet`；表格页 `onLoad` 认 `?sheet=cryo&tab=`（`cryoViewOfTab`，不认识的落「全部」）；登记成功后 `@changed` 触发表格重新取数。
- `CryoBatchSheet`（`wd-popup`，**直接 .vue 路径导入**）按 `UI:mp.cryo.flow`：上部放在哪 + 全部字段；中部操作（取走 / 补入 / 转液氮，表单是 `components/lqg/CryoFlowForm.vue`、`CryoLn2Form.vue`，同样直接 .vue 路径导入）；下部取用登记（`…/flows`，时间倒序，每行 = 时间 · 类型 · ±支数 · 用途 · 经手人 · 操作后剩余，改过的标「已改」，取走 / 补入可改删）；底部小字「每一笔都写明操作后还剩几支，改过的标「已改」；登记错了点「改」或「删」。」（CR-20260924-10，原先「……请到网页工作台」那句撤掉）。
  纯函数在 `pages/cryo/flow.ts`（`needsEmptyConfirm`、`flowQtyProblem`、`canRegisterLn2`、`canChangeFlow` 等），`flow.fixture.spec.ts` 读 `doc/verify/fixtures/cryo-take-cases.json`（与工作台同一份）。
  **右上角「修改」**（CR-20260918-07）：点它关掉弹层并跳 `pages/cryo/form?id=&mode=edit`。
- `pages/cryo/form` 按 `UI:mp.cryo.form`：新增（首页进来）与修改（历史编辑记录、或内部管理的批次详情「修改」进来，`mode=edit`）两种模式；修改模式顶部小字「最后修改：某某 · 时间」。字段按甲方模板先后（CR-20260924-10）：选择样本 → 冻存时间、冻存样品名称、冻存数量/支、冻存密度、暂存-80、冻存人、-80度超低温冰箱转移至液氮时间（只读）、液氮储存位置、备注 → 代数；页内提示「取走、补入、转液氮：在「内部管理 → -80 冻存」里点这一批登记」。
- 历史编辑记录：在 `pages/history/sources.ts` 注册 `cryo`（只有内部有这个页签）——`GET /mp/int/cryo/batch/list?sort=recent`，「只看我提交的」打开时再加 `mine=true`（开关默认关，CR-20260918-07）；行 = 冻存样品（等宽）、「剩 N / 初始 M 支」、经手人（本人显示「我」）、「新增 / 修改」、日期；点行 → `pages/cryo/form?id=&mode=edit`（别人录的也点得进去）。开关本身在共用的历史页（SAMPLE-MP-001），本张只把 `mine` 透传进来源。
- 把 SAMPLE-MP-001 样本修改页里置灰的「加冻存」点亮（带 `sampleId` 进 `pages/cryo/form`）。

## 3 边界（明确不做）

- 不做盘点调整（只在工作台，CRYO-WEB-001）；取走 / 补入 / 转液氮 / 改删取走补入登记 CR-20260924-10 起在批次详情弹层里做，但**不在 `/mp/int` 上另开写口**
- 表格本身仍然没有行内编辑、没有新增、没有核验：CR-20260918-07 加的是「点一行 → 详情 → 右上角修改」这一条路，CR-20260924-10 加的是详情弹层里的登记
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

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新：§2 补修改接口的补丁语义、冻存必填项与 `toLn2Time:null` 撤销转液氮；accept 3 在收尾 reseed 之前追加四段——工作台传 `toLn2Time:null` 撤销转液氮、小程序修改接口传 `initQty:null` 回 400、库里转液氮时间与位置为空且冻存数量仍是 8、超期计数回到 2 且 3001 回到 -80 超期。
- 2026-09-24 按 CR-20260924-10 更新：小程序也能做冻存取用登记、支数取空要提示——accept 1 / 3 的 `tabCounts` 整对象补 `"emptied":1`；accept 2 改名为「写口只有 /lqg/cryo 一份」，保留 /mp/int 仍 404 的四段，删掉「小程序里没有 flowType / to-ln2」那段 grep，补 staff 走 /lqg/cryo 取走 → 转液氮 → 从液氮取到 0（from_location 追溯、已取空页签 +1）→ 改一笔被拒并指出哪一笔 → 删一笔后恢复、extA 两处 403；**新增 accept 5**（弹层导入登记表单、已取空标记与页签、tab 直达、取空确认两端同一份用例、单测 ≥ 47 例）；blueprint_refs 补 F-CRYO-01.step4、F-CRYO-02.step1；§0–§3 改写。
