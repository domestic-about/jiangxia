---
ticket: CRYO-MODEL-001
track: CRYO
phase: D4
size: M
req_refs:
  - REQ-CRYO-001
  - REQ-CRYO-002
  - REQ-CRYO-007
  - REQ-SAMPLE-008
  - REQ-CRYO-008
depends_on:
  - SAMPLE-VERIFY-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/batch/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/cryo/batch/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260924120*__CRYO-MODEL-001-*.sql
adr_refs:
  - ADR-0010
  - ADR-0009
blueprint_refs:
  - FLOW:F-CRYO-01.step1
  - FLOW:F-CRYO-02.step4
  - FLOW:F-CRYO-02.step5
  - FIELD:t_lqg_cryo_batch.sample_id
  - FIELD:t_lqg_cryo_batch.passage
  - FIELD:t_lqg_cryo_batch.init_qty
  - FIELD:t_lqg_cryo_batch.in_minus80
  - FIELD:t_lqg_cryo_batch.cryo_name
  - FIELD:t_lqg_cryo_flow.delta
accept:
  - name: "两张表与 SSOT 逐列相符、出自本票 Flyway；批次表上没有任何「剩余」类的冗余列"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_cryo_batch --table t_lqg_cryo_flow --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260924120%__CRYO-MODEL-001-%'" --eq 1 &&
      python3 doc/verify/db.py --sql "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='t_lqg_cryo_batch' AND (column_name LIKE '%remain%' OR column_name LIKE '%current%' OR column_name LIKE '%stock%')" --empty
    counterfeit: |-
      加了 remaining_qty 列并在写流水时回写 → 第 3 段非空红（ddl_vs_ssot 也红）。一旦有这一列，就会有人直接改它，「取走需要追溯」就成了空话。
      流水表没建 create_dept → 公共字段缺失红。
  - name: "批次校验：代数格式、支数、直接进液氮必须有位置、转液氮时间不早于冻存时间、只能挂有效样本——全部被拒且库里不变；初始支数改得动并记下修改人，改到让剩余为负时被拒"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      post() { bash doc/verify/api.sh --as staff --bizcode POST /lqg/cryo/batch "$1"; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      post '{"sampleId":9000001005,"cryoName":"T-BAD1","passage":"3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"cryoName":"T-BAD2","passage":"P3","freezeTime":"2026-09-17","initQty":0,"inMinus80":"Y"}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"cryoName":"T-BAD3","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"N"}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001002,"cryoName":"T-BAD4","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"cryoName":"T-BAD5","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y","toLn2Time":"2026-09-01","ln2Location":"1号罐"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_batch WHERE cryo_name LIKE 'T-BAD%'" --eq 0 &&
      post '{"sampleId":9000001005,"cryoName":"T-hga03-W-N-P12-EM2-1e5","passage":"P12","freezeTime":"2026-09-17","initQty":4,"density":"1e5","inMinus80":"Y","frozenBy":"李工"}' | grep -qE '^200' &&
      B="$(bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003001 | jq -c '.data | {id, sampleId, cryoName, passage, freezeTime, density, inMinus80, frozenBy}')" &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 10')" | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT init_qty || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_cryo_batch WHERE id=9000003001" --eq "10|yes" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 1')" | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT init_qty FROM t_lqg_cryo_batch WHERE id=9000003001" --eq 10 &&
      bash doc/verify/api.sh --as staff --bizcode DELETE /lqg/cryo/batch/9000003001 | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT del_flag FROM t_lqg_cryo_batch WHERE id=9000003001" --eq 0 &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      代数不校验格式 → 第 1 段拿到 200 红；库里会同时出现 "3"、"p3"、"P3"、"第3代"，导出和筛选全乱。
      直接进液氮不要求位置 → 第 3 段红：东西进了液氮罐却没人知道在哪。
      PUT 时静默忽略 initQty → 拿到 200 但库里仍是 8，「10|yes」那段红：用户以为改成功了。
      改初始支数不校验流水 → 改成 1 拿到 200 红：3001 已经取走 2 支，剩余会变成 −1。
      有流水的批次允许删除 → 倒数第 3 段红：删了批次，「谁取走了几支」就再也查不到了。
  - name: "剩余支数读时计算：接口给的每批剩余与直连库独立汇总的逐批一致（含取空为 0、软删流水不计、软删批次不出现）"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      API="$(bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/lqg/cryo/batch/list?pageSize=100' | jq -r '[.rows[] | "\(.id|tostring):\(.remainingQty)"] | sort | join(",")')" &&
      test "${API}" = "9000003001:6,9000003002:4,9000003003:4,9000003004:0,9000003005:2,9000003006:5,9000003007:5" &&
      python3 doc/verify/db.py --sql "SELECT b.id || ':' || (b.init_qty + COALESCE(SUM(f.delta),0)) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.del_flag='0' GROUP BY b.id, b.init_qty" --col-set "${API}" &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '([.rows[]|select(.location=="ln2")|.id|tostring]|sort)==["9000003003","9000003007"] and ([.rows[]|select((.id|tostring)=="9000003001")|.internalNo]==["T-hli01"])'
    counterfeit: |-
      汇总时没过滤软删流水 → 3002 的剩余变成 3 红（seed 里那条软删的 −1 就是为这条埋的）。
      剩余为 0 的批次被列表过滤掉了 → 少了 3004 红；取空的批次仍要能查到它的流水。
      location 只看 in_minus80、不看 to_ln2_time → 3003（先 -80 后转液氮）被判成 minus80 红。
      两侧不同源：一侧是接口（应用层聚合），一侧是 db.py 里独立写的 SQL；再用 seed 的期望值把两边一起钉住，防两边同错。
---

# CRYO-MODEL-001 · 冻存批次与出入库流水：建模、批次的校验规则、剩余支数读时计算、内部增删改查接口（初始支数可改，逐笔剩余不得为负）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/CRYO` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-VERIFY-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0010**：冻存剩余支数不可直接改，恒等于初始支数加出入库流水累计；2026-09-17 起登记与初始支数可以改（CR-20260917-04），剩余照样只算不存
  - **ADR-0009**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0009` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **表里没有「剩余支数」这一列**，也不许加。剩余 = `init_qty + SUM(未删流水的 delta)`，读时算。
  2. **初始支数可以改**（CR-20260917-04 推翻了原来的「建后不可改」）：保存前锁批次行，从新的初始支数出发按 `flow_time` 正序（同一时刻按 id）逐笔累加未删流水，**任何一步 < 0 就拒绝**，提示「已取走 N 支，冻存数量不能少于 N」。逐笔校验写成 `CryoBalanceChecker`，CRYO-FLOW-001 改删流水时复用同一个。
  3. 冻存样品名称（如 `hli39-GZ-N-P3-EM2-2e5`）手填，系统不解析、不自动拼；代数单独一栏，形如 `P3`。
  4. 暂存 -80 选「否」= 直接进液氮：液氮储存位置必填，且永远不参与超期提醒。

## 1 背景与口径

模板 D：-80 冻存 9 列（REQ-CRYO-001），「暂存-80度超低温冰箱」是 / 否按钮（REQ-CRYO-002）。微信答复：内部编号贯穿到冻存；冻存时记代数，
不是每一代都冻（P2 冻、P3 不冻、P7 或 P8 再冻），命名如 hli39-GZ-N-P3-EM2-2e5（REQ-CRYO-007）。

## 2 实现要点

- DDL：`gen_ddl_pg.py --migration V202609241200__CRYO-MODEL-001-cryo.sql` → `t_lqg_cryo_batch`、`t_lqg_cryo_flow`（流水表本张建，写流水的接口在 CRYO-FLOW-001）。
- 包 `org.dromara.lqg.cryo.batch`，`/lqg/cryo/batch`（权限 `lqg:cryo:{list,query,add,edit,remove,export}`）。
- 校验：`sampleId` 是有效样本；`passage` 匹配 `^P\d{1,3}$`；`initQty` 为正整数；`inMinus80='N'` 时 `ln2Location` 必填；`toLn2Time` 不早于 `freezeTime`；
  `PUT` 改 `initQty`：锁批次行 → `CryoBalanceChecker.check(新初始支数, 未删流水按时间正序)` → 任一步为负则拒绝且库里不变，通过才更新（不许静默忽略入参里的 initQty）。
- `CryoBalanceChecker` 放 `org.dromara.lqg.cryo.batch`（纯函数，不碰库）+ 单测 `CryoBalanceCheckerTest`：最终为正但中途为负必须拒绝；同一时刻按 id 排序。
- 读模型（列表与详情每行都带）：`remainingQty`、`location`（`inMinus80='N'` 或 `toLn2Time` 非空 → `ln2`，否则 `minus80`）、`internalNo`、`sourceUnitName`。
  剩余用一条聚合查询按本页 id 集合算，别逐行查。`overdue / overdueDays / tabCounts` 三个键在 CRYO-REMIND-001 补。
- 删除批次：有未删流水的批次不许删（否则追溯断了）。
- 注册 `SampleChildrenChecker`：样本名下有未删批次 → true。

## 3 边界（明确不做）

- 不做写流水、转液氮的接口（CRYO-FLOW-001）
- 不做超期判定（CRYO-REMIND-001）
- 不做页面与导出
- 不建液氮罐 / 架 / 盒的位置字典（位置就是一段文本）
- 不解析冻存命名、不校验命名里的代数与 `passage` 一致

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
