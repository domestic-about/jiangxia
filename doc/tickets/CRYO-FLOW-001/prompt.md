---
ticket: CRYO-FLOW-001
track: CRYO
phase: D4
size: M
req_refs:
  - REQ-CRYO-004
  - REQ-CRYO-005
  - REQ-CRYO-006
  - REQ-CRYO-008
depends_on:
  - CRYO-MODEL-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/flow/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/cryo/flow/**
adr_refs:
  - ADR-0010
blueprint_refs:
  - FLOW:F-CRYO-02.step1
  - FLOW:F-CRYO-02.step2
  - FLOW:F-CRYO-02.step3
  - FLOW:F-CRYO-02.step4
  - FLOW:F-CRYO-02.step5
  - FLOW:F-CRYO-01.step4
  - FIELD:t_lqg_cryo_flow.delta
  - FIELD:t_lqg_cryo_flow.from_location
  - FIELD:t_lqg_cryo_flow.flow_time
  - FIELD:t_lqg_cryo_batch.to_ln2_time
accept:
  - name: "取走补入调整后，接口给的剩余与直连库独立汇总的一致；取自位置自动带出；超取、调整缺原因、调成负数被拒且不产生流水"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      flow() { bash doc/verify/api.sh --as staff --bizcode POST "/lqg/cryo/batch/$1/flow" "$2"; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      flow 9000003002 '{"flowType":"take","qty":2,"purpose":"复苏培养"}' | grep -qE '^200' &&
      flow 9000003002 '{"flowType":"add","qty":1,"purpose":"同批补冻"}' | grep -qE '^200' &&
      flow 9000003003 '{"flowType":"take","qty":1,"purpose":"药敏实验"}' | grep -qE '^200' &&
      flow 9000003002 '{"flowType":"take","qty":9,"purpose":"超取"}' | grep -qE '^(400|500)' &&
      flow 9000003002 '{"flowType":"adjust","qty":-1}' | grep -qE '^(400|500)' &&
      flow 9000003002 '{"flowType":"adjust","qty":-9,"purpose":"调成负数"}' | grep -qE '^(400|500)' &&
      flow 9000003002 '{"flowType":"take","qty":-1,"purpose":"负的取走"}' | grep -qE '^(400|500)' &&
      flow 9000003002 '{"flowType":"adjust","qty":-1,"purpose":"盘点少一支"}' | grep -qE '^200' &&
      python3 doc/verify/db.py --sql "SELECT flow_type || ':' || delta || ':' || from_location FROM t_lqg_cryo_flow WHERE del_flag='0' AND create_time > now() - interval '5 minutes'" --col-set "take:-2:minus80,add:1:minus80,take:-1:ln2,adjust:-1:minus80" &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '[.rows[]|select((.id|tostring)=="9000003002" or (.id|tostring)=="9000003003")|.remainingQty] | sort == [2,3]' &&
      python3 doc/verify/db.py --sql "SELECT b.id || ':' || (b.init_qty + COALESCE(SUM(f.delta),0)) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.id IN (9000003002, 9000003003) GROUP BY b.id, b.init_qty" --col-set "9000003002:2,9000003003:3" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      take 的 delta 存成了正数（靠 flow_type 区分加减）→ 第 9 段集合红；任何人写报表时 SUM(delta) 就全错了。
      超取只在前端用步进器上限拦 → 第 4 段后端照收红。
      取自位置让前端传 → 3003 已转液氮却记成 minus80，集合红。
      被拒的四次如果有任何一次落了流水 → 集合里会多出一行红（集合是精确相等，不是包含）。
      两侧不同源：接口的 remainingQty vs db.py 独立汇总。
  - name: "改删登记：改支数后记下修改人、删补入后剩余与直连库独立汇总一致；挪早加量让中途为负（最终仍为正）、删补入让后面不够、改类型、跨批次改——全部被拒且库里不变"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg PUT /lqg/cryo/batch/9000003001/flow/9000003101 '{"qty":1,"purpose":"复苏培养（更正）"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT delta || '|' || purpose || '|' || from_location || '|' || del_flag || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_cryo_flow WHERE id=9000003101" --eq="-1|复苏培养（更正）|minus80|0|yes" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003001/flow/9000003101 '{"qty":1,"flowType":"add","purpose":"改类型"}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003002/flow/9000003101 '{"qty":1,"purpose":"跨批次"}' | grep -qE '^(400|404|500)' &&
      python3 doc/verify/db.py --sql "SELECT delta || '|' || purpose FROM t_lqg_cryo_flow WHERE id=9000003101" --eq="-1|复苏培养（更正）" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003003/flow/9000003104 "$(python3 -c 'import datetime,json;print(json.dumps({"qty":6,"purpose":"药敏实验","flowTime":(datetime.datetime.now()-datetime.timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")}))')" | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT delta || '|' || (flow_time::date = CURRENT_DATE - 10) FROM t_lqg_cryo_flow WHERE id=9000003104" --eq="-3|true" &&
      bash doc/verify/api.sh --as staff DELETE /lqg/cryo/batch/9000003003/flow/9000003103 | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT del_flag FROM t_lqg_cryo_flow WHERE id=9000003103" --eq 1 &&
      bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003006/flow '{"flowType":"add","qty":2,"purpose":"同批补冻"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003006/flow '{"flowType":"take","qty":7,"purpose":"全部取用"}' | jq -e '.code==200' &&
      A="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_cryo_flow WHERE batch_id=9000003006 AND flow_type='add' AND del_flag='0'" | head -1)" &&
      bash doc/verify/api.sh --as staff --bizcode DELETE "/lqg/cryo/batch/9000003006/flow/${A}" | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_flow WHERE batch_id=9000003006 AND del_flag='0'" --eq 2 &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '[.rows[]|select((.id|tostring)=="9000003001" or (.id|tostring)=="9000003003" or (.id|tostring)=="9000003006")|"\(.id|tostring):\(.remainingQty)"]|sort == ["9000003001:7","9000003003:2","9000003006:0"]' &&
      python3 doc/verify/db.py --sql "SELECT b.id || ':' || (b.init_qty + COALESCE(SUM(f.delta),0)) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.id IN (9000003001, 9000003003, 9000003006) GROUP BY b.id, b.init_qty" --col-set "9000003001:7,9000003003:2,9000003006:0" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      只校验最终剩余、不逐笔算 → 把 3104 挪到 30 天前并改成 6 支拿到 200 红（最终剩 1 支，可 30 天前那一步是 −1）。
      删除做成物理删除 → 3103 查不到行，del_flag 那段红：删了什么就再也查不到，追溯断了。
      删补入不校验后面的取走 → 3006 删掉那笔补入拿到 200 红：剩余会变成 −2。
      改登记允许换类型 → 取走改成补入拿到 200 红；不校验 flowId 归属 → 用 3002 的路径改到了 3001 的登记红。
      改登记时把 from_location 按批次当前位置重算，或者没记 update_by → 第 2 段的 minus80 / yes 红。
      两侧不同源：接口的 remainingQty vs db.py 独立汇总；期望集合由 seed 钉住。
  - name: "并发取最后两支只成功一次、剩余恰好为 0 不为负；转液氮时间早于冻存时间被拒、登记后位置变成液氮"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      for i in 1 2 3 4 5; do bash doc/verify/api.sh --as staff --bizcode POST /lqg/cryo/batch/9000003005/flow '{"flowType":"take","qty":2,"purpose":"并发"}' > "/tmp/lqg-race-${i}.txt" & done; wait &&
      test "$(cat /tmp/lqg-race-*.txt | grep -cE '^200')" = 1 &&
      python3 doc/verify/db.py --sql "SELECT b.init_qty + COALESCE(SUM(f.delta),0) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.id=9000003005 GROUP BY b.init_qty" --eq 0 &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003001/to-ln2 '{"toLn2Time":"2020-01-01","ln2Location":"1号罐"}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"3号罐-2架-C1"}))')" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003001 | jq -e '.data.location=="ln2" and .data.ln2Location=="3号罐-2架-C1"' &&
      rm -f /tmp/lqg-race-*.txt && bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      写流水时没锁批次行（先 SELECT 剩余、再 INSERT）→ 并发 5 个都读到剩余 2、都成功，成功数 5、剩余 −8 红。这是库存类功能最经典的事故。
      转液氮不校验时间先后 → 2020-01-01 那段拿到 200 红。
      结尾 reseed：别把转了液氮的 3001 留给后面的断言。
---

# CRYO-FLOW-001 · 冻存出入库：取走、补入、盘点调整、转液氮；登记可改可删（逐笔剩余不得为负）；并发下剩余不为负

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/CRYO` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**CRYO-MODEL-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0010**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0010` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **登记可以改、可以删**（CR-20260917-04：甲方要求「提交后可以修改」，推翻了原来的「只增不改不删」）。删 = 软删；登记类型不能改（要换类型就删掉重登）；`from_location` 保持登记时的值。
  2. **校验看每一步，不只看最后**：改删之后从初始支数出发、按 `flow_time` 正序（同一时刻按 id）逐笔累加，任何一步 < 0 都拒绝——只看最终剩余会放过「当时只剩 2 支却取走了 3 支」。与 CRYO-MODEL-001 改初始支数共用 `CryoBalanceChecker`。
  3. `from_location` 由批次当时所在位置**自动带出**，不让人选：已登记转液氮（或直接进液氮）→ `ln2`，否则 `minus80`。
  4. 取走数 > 剩余 → 拒绝。两个人同时取最后两支：只能成功一个——写、改、删流水前都对批次行 `SELECT … FOR UPDATE` 再重算。
  5. **这些写接口只有 `/lqg/cryo/**` 一份**：`/mp/int/cryo/**` 只读 `…/flows`，不转发任何写操作。2026-09-17 晚曾定小程序只查看（CR-20260917-05）；**2026-09-24 起小程序内部人员也在批次详情弹层里取走、补入、转液氮、改删取走 / 补入登记**（CR-20260924-10，甲方「要求小程序和工作台界面都能操作」），直接调这同一组 `/lqg/cryo/**` 写口（`lqg_internal` 本来就有 `lqg:cryo:flow / edit` 权限串，外部 403），规则一字不差；盘点调整只在工作台给入口（后端不区分调用端）。

## 1 背景与口径

模板批注：液氮取走需要追溯，自动减少相应的支数。会上 L103：-80 取走也要追溯；L109：拿走了几支相应减少，冻存的几支也可以增加或者减少。
2026-09-17 甲方看设计稿 v1：「这个-80冻存这儿写的提交后不能修改，麻烦改为可以修改」（REQ-CRYO-008）。

## 2 实现要点

- `POST /lqg/cryo/batch/{id}/flow`（权限 `lqg:cryo:flow`）：`take` / `add` 的 `qty` 必须是正整数，落库 `delta = ∓qty`；`adjust` 的 `qty` 带符号、不为 0、`purpose` 必填。
  事务内：锁批次行 → 用加上这一笔之后的流水集合跑 `CryoBalanceChecker` → 通过才插入。`operatorName` 缺省取当前用户姓名，`flowTime` 缺省取当前时间。
- `PUT /lqg/cryo/batch/{id}/flow/{flowId}`（权限 `lqg:cryo:flow`）：`{qty, purpose, operatorName, flowTime}`；入参带了 `flowType` 且与原来不同 → 400；`qty` 按原类型解释；
  `flowId` 不属于这个批次或已删 → 404。事务内：锁批次行 → 用改后的流水集合跑 `CryoBalanceChecker` → 通过才更新。
  改 / 删一笔被拒时的提示指出是哪一笔、变成多少（CR-20260924-10）：「这样改会让 MM-dd HH:mm 那一笔（-3 支）之后的剩余变成 -1 支，没有保存」/「删掉这一笔会让 …」（`CryoFlowService#overdraftMessage`，判定仍是 `CryoBalanceChecker.requireNonNegative` 那一个）；改初始支数被拒仍是「已取走 N 支，冻存数量不能少于 N」。
- `DELETE /lqg/cryo/batch/{id}/flow/{flowId}`：软删（`del_flag='1'`）；同样先锁、先校验（删掉一笔补入可能让后面的取走不够）。
- `PUT /lqg/cryo/batch/{id}/to-ln2`（权限 `lqg:cryo:edit`）：`toLn2Time`（不早于 `freeze_time`）+ `ln2Location` 必填；已转过的可以改位置，不可以清空。
- `GET /lqg/cryo/batch/{id}/flows`：未删流水，时间倒序，每行带 `balanceAfter`（按时间正序累计算出来，不落库）、`edited`、`updateByName`、`updateTime`。
- 单测：并发 10 线程各取 1 支、剩余 3 → 恰好 3 个成功；改删的校验复用 `CryoBalanceCheckerTest` 的用例。

## 3 边界（明确不做）

- 不做页面（工作台 CRYO-WEB-001；小程序批次详情弹层里的取用登记与登记表单在 CRYO-MP-001，CR-20260924-10）
- 不做审批、不做「取走申请」
- 不按用途做统计
- 不建登记的修改历史表（只记最后修改人与时间）

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-24 按 CR-20260924-10 更新：§0 口径 5 改为「写口仍只有 /lqg/cryo 一份，小程序内部身份也直接调它」，§2 补改删被拒的新提示句式，§3 同步；accept 逐条核过不用改（被拒只断业务码，不断提示原文）。
