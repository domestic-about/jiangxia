---
ticket: SAMPLE-VERIFY-001
track: SAMPLE
phase: D2
size: M
req_refs:
  - REQ-SAMPLE-014
  - REQ-SAMPLE-009
  - REQ-AUTH-004
depends_on:
  - SAMPLE-MODEL-001
  - AUTH-STAFF-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/verify/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/verify/**
adr_refs:
  - ADR-0003
  - ADR-0010
blueprint_refs:
  - FLOW:F-SAMPLE-01.step3
  - FLOW:F-SAMPLE-01.step4
  - FLOW:F-SAMPLE-01.step5
  - FIELD:t_lqg_sample.verify_status
  - FIELD:t_lqg_sample.submit_source
  - FIELD:t_lqg_sample.invalid_reason
  - FIELD:t_lqg_sample.passage
accept:
  - name: "非法转移被拒且库里不变：判有效缺内部编号、内部编号撞号、判无效缺原因、有效样本被外部重提；合法路径 pending→valid→invalid 与 invalid→valid 全程可达"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17"}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli01"}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001002/verify '{"action":"invalid"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(verify_by::text,'-') FROM t_lqg_sample WHERE id=9000001002" --eq "pending|-|-" &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli77","isFixed":"Y","operatorName":"李工"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || internal_no || '|' || verify_by || '|' || is_fixed || '|' || submit_source FROM t_lqg_sample WHERE id=9000001002" --eq "valid|T-hli77|9000000101|Y|external" &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"invalid","reason":"误判，退回"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001003/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli78"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT id || ':' || verify_status || ':' || COALESCE(invalid_reason,'') FROM t_lqg_sample WHERE id IN (9000001002, 9000001003)" --col-set "9000001002:invalid:误判，退回,9000001003:valid:" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      判有效不校验内部编号必填 → 第 2 段拿到 200 红；库里会出现「有效但没有内部编号」的样本，后面石蜡包埋、冻存都选不到它。
      校验了但先 UPDATE 再校验（或事务没回滚）→ 第 5 段库内状态不是 pending|-|- 红。
      invalid→valid 这条路径没放开（内部想直接改判只能让外部重提）→ 倒数第 3 段红。
      改判有效后没清掉旧的 invalid_reason → 最后的集合里 1003 带着「信息不全：缺住院号」红。
      走的是真实分支：第 3 段用的是 seed 里真实存在的 T-hli01，不是一个不存在的编号。
  - name: "内外部属性是提交当时的快照：提交人后来被授权成内部，他以前送的样本仍是外部样本；他之后新录的才是内部样本"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg POST /lqg/auth/staff '{"phone":"13800000011","name":"王医生","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -e '.data.upgraded==true' &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extA-* &&
      bash doc/verify/api.sh --as extA POST /lqg/sample '{"species":"人","sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-snap01"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT submit_source || ':' || count(*) FROM t_lqg_sample WHERE submitter_id=9000000111 AND del_flag='0' GROUP BY submit_source" --col-set "external:3,internal:1" &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample s JOIN sys_user_role ur ON ur.user_id = s.submitter_id JOIN sys_role r ON r.role_id = ur.role_id WHERE s.submitter_id=9000000111 AND s.del_flag='0' AND r.role_key='lqg_internal' AND s.submit_source='external'" --eq 3 &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?submitSource=external&pageSize=100' | jq -e '[.rows[].id|tostring] | index("9000001001") != null' &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extA-* && bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      submit_source 不落库、每次按提交人当前角色现算 → 授权之后 1001 / 1002 / 1003 全变成 internal，集合红。这是「按人区分」最自然也最错的实现。
      列表的 submitSource 筛选也是现算的 → 倒数第 2 段找不到 1001 红。
      两侧不同源：一侧是授权接口改的角色，一侧是样本表里早先落下的值。结尾清 extA 的 token 缓存并 reseed，不把升级后的身份留给后面的断言。
  - name: "核验一并保存送检段（CR-20260923-09，V02）：带 submitSegment 就与核验结论同一次保存，来源单位可改选正式单位、名称取单位表快照；送检段校验不过、或核验本身缺必填，整次 400 且库里一个字不变；判无效时送检段照存、收样段不落库；不带 submitSegment 的老调用送检段不动；类器官的送检段含代数，核验时能改、格式不对整次 400（CR-20260924-10）"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --bizcode PUT /lqg/sample/9000001007/verify '{"action":"valid","receiveDate":"2026-09-20","internalNo":"T-v02a","submitSegment":{"species":"人","sourceUnitId":9000009002,"sourceUnitName":"A 医院","tissueType":""}}' | grep -qE '^400.*组织类型不能为空' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || tissue_type || '|' || source_unit_id FROM t_lqg_sample WHERE id=9000001007" --eq "pending|-|肝组织|9000009001" &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001007/verify '{"action":"valid","receiveDate":"2026-09-20","submitSegment":{"species":"人","sourceUnitId":9000009002,"sourceUnitName":"A 医院","tissueType":"不该存进去"}}' | grep -qE '^400.*内部编号' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || tissue_type || '|' || source_unit_id FROM t_lqg_sample WHERE id=9000001007" --eq "pending|-|肝组织|9000009001" &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001007/verify '{"action":"valid","receiveDate":"2026-09-20","internalNo":"T-v02a","submitSegment":{"species":"人","sourceUnitId":9000009002,"sourceUnitName":"A 医院","donorName":"测试供体庚","gender":"female","age":"53","hospitalNo":"ZY0000007","tissueType":"肝组织（核验更正）","hasPathology":"Y","remark":"核验时补的备注"}}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || internal_no || '|' || tissue_type || '|' || gender || '|' || age || '|' || has_pathology || '|' || source_unit_id || '|' || source_unit_name || '|' || remark FROM t_lqg_sample WHERE id=9000001007" --eq "valid|T-v02a|肝组织（核验更正）|female|53|Y|9000009002|B 大学|核验时补的备注" &&
      bash doc/verify/api.sh --as staff GET /lqg/sample/9000001007 | jq -e '.data.donorName=="测试供体庚" and .data.hospitalNo=="ZY0000007"' &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"invalid","reason":"住院号不对，请核对后重提","receiveDate":"2026-09-20","internalNo":"T-v02b","submitSegment":{"species":"人","sourceUnitId":9000009001,"sourceUnitName":"A 医院","donorName":"测试供体乙","gender":"female","age":"48","hospitalNo":"","tissueType":"胆管组织","hasPathology":"N","remark":"核验判无效时顺手改的备注"}}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || invalid_reason || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(receive_date::text,'-') || '|' || COALESCE(hospital_no,'-') || '|' || remark FROM t_lqg_sample WHERE id=9000001002" --eq "invalid|住院号不对，请核对后重提|-|-|-|核验判无效时顺手改的备注" &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-20","internalNo":"T-v02b"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || internal_no || '|' || COALESCE(invalid_reason,'-') || '|' || tissue_type || '|' || age || '|' || COALESCE(hospital_no,'-') || '|' || remark FROM t_lqg_sample WHERE id=9000001002" --eq "valid|T-v02b|-|胆管组织|48|-|核验判无效时顺手改的备注" &&
      bash doc/verify/api.sh --as extA POST /mp/ext/organoid '{"species":"人","sourceUnitName":"A 医院","organoidType":"肝类器官","passage":"P3"}' | jq -e '.code==200' &&
      OID="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_sample WHERE sample_kind='organoid' AND submitter_id=9000000111 AND del_flag='0'" | head -1)" && test -n "${OID}" &&
      bash doc/verify/api.sh --as staff --bizcode PUT "/lqg/sample/${OID}/verify" '{"action":"valid","receiveDate":"2026-09-24","internalNo":"T-v02c","submitSegment":{"species":"人","sourceUnitId":9000009001,"sourceUnitName":"A 医院","organoidType":"肝类器官","passage":"第4代"}}' | grep -qE '^400.*代数请填 P 加数字，如 P3' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(passage,'-') FROM t_lqg_sample WHERE id=${OID}" --eq "pending|-|P3" &&
      bash doc/verify/api.sh --as staff PUT "/lqg/sample/${OID}/verify" '{"action":"valid","receiveDate":"2026-09-24","internalNo":"T-v02c","operatorName":"李工","submitSegment":{"species":"人","sourceUnitId":9000009001,"sourceUnitName":"A 医院","organoidType":"肝类器官","passage":"p4","remark":"核验时改代数"}}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || internal_no || '|' || passage || '|' || remark FROM t_lqg_sample WHERE id=${OID}" --eq "valid|T-v02c|P4|核验时改代数" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      核验接口不认 submitSegment，抽屉里改的送检段被静默丢弃（CR-20260923-09 之前的形态，issue #147）→ 第 7 段库里组织类型仍是「肝组织」、单位仍是 A 医院，红。
      送检段另起一个请求或另一个事务先存、再判核验 → 第 4 段（缺内部编号被拒）之后组织类型已经变成「不该存进去」，第 5 段红：核验被拒了，送检段却改了。
      送检段不走 SubmitSegmentRules（清空必填也放行）→ 第 2 段拿到 200、或第 3 段库里组织类型被清空 / 样本已判有效，红。
      改选正式单位时名称照抄请求里的旧名（A 医院）、不取单位表快照 → 第 7 段单位 id 是 B 大学、名称还是 A 医院，红：导出与筛选各说各话。这正是「把只填了单位名的外部样本归口」的形态。
      送检段绕开加密直接写明文 → 第 8 段经详情接口读回来不是「测试供体庚 / ZY0000007」，红。
      判无效时顺手把请求里的收样日期、内部编号也落了库 → 第 10 段不是「-|-」红：判无效的样本不补收样信息；送检段没照存（备注、清空的住院号）→ 同段红。
      不带 submitSegment 被当成「整段替换成空」→ 第 11 段那次改判有效把组织类型、年龄、备注洗成空，第 12 段红：老调用方一核验就把送检信息洗掉了。
      每个被拒后面都跟一条库内断言：只看业务码的话，先落盘再报错也是绿的。
      代数（CR-20260924-10）：送检段的校验与写入漏了 passage（类器官送检段只认来源单位、类器官类型、备注）→ 核验时改成 p4 之后库里仍是 P3 红；格式不校验 → 「第4代」那次拿到 200、或库里样本已判有效红；没做小写 p 转大写 → 库里是 p4 红。
---

# SAMPLE-VERIFY-001 · 核验状态机：待核验 → 有效 / 无效、无效可重提、有效后外部只读；内外部属性是提交当时的快照

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-MODEL-001**、**AUTH-STAFF-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - `FLOW:F-SAMPLE-01.step5` 的合法转移表——本张的全部内容就是把它变成代码和断言
  - **ADR-0003**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0003` 取结构化口径）
  - **ADR-0010**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0010` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **判有效必须同时给收样日期和内部编号**；判无效必须写原因。缺了就拒绝，且库里什么都不变。
  2. **`submit_source` 是提交当时的快照**：外部用户后来被授权成内部，他以前提交的样本仍是外部样本；反过来也一样。
  3. 外部**不能**把自己的样本改成 valid——状态只能由内部改；外部重提只会回到 pending。
  4. **状态机不分 `sample_kind`**：2026-09-17 晚起外部也能提交类器官收样记录（CR-20260917-05），走同一张转移表；判有效时类器官样本同样要收样日期与内部编号。转移表本身写成与表无关的纯函数，石蜡包埋送样的核验（EMBED-MODEL-001）直接复用。
  5. **核验时可一并修改送检段，与核验结论同一次保存**（FLOW:F-SAMPLE-01.step3，CR-20260923-09）：核验请求可带 `submitSegment`（送检段整段：来源单位、供体姓名、性别、年龄、住院号、组织类型或类器官类型、类器官的代数〔CR-20260924-10，选填，去空格、小写 p 转大写后须形如 P3，与冻存批次代数同一规则；组织样本一律写空〕、有无病理、备注），校验规则与样本修改（`PUT /lqg/sample`）同一份，按样本自己的类别写、没传的按清空；**校验不过则库里不变**（送检段不合格、或核验本身缺必填，都是整次拒绝）；**判无效时送检段照存、收样段不落库**；**不带 `submitSegment` = 送检段一个字不动**（老调用方不受影响）；来源单位可改选正式单位，把只填了单位名的外部样本归口（名称取单位表快照）。

## 1 背景与口径

方案流程图：合作单位小程序送检 → [送检信息有效？] → 内部编号收样；无效需重新提交。会上（L295）：「你们看我填的是不是有效的，如果不是有效的，你就不管就完了。」
方案 v6 L100：外部可填写、修改自己提交的样本记录信息表，由实验室核验有效后再处理。L85-L88：内外部按提交人区分，「最好是按照人」。

## 2 实现要点

- `SampleVerifyService`（包 `org.dromara.lqg.sample.verify`）集中管状态转移，controller 与外部接口都只能经它改 `verify_status`：
  | 从 | 到 | 谁 | 条件 |
  |---|---|---|---|
  | pending | valid | 内部 | `receiveDate`、`internalNo` 必填且内部编号唯一 |
  | pending | invalid | 内部 | `reason` 必填 |
  | invalid | pending | 外部本人 | 修改送检段后重提；清空 `invalid_reason`、`verify_by`、`verify_time` |
  | invalid | valid | 内部 | 同 pending→valid |
  | valid | invalid | 内部 | `reason` 必填，且所有 `SampleChildrenChecker` 都返回 false |
  其余一律拒绝（含 valid→pending、任何人把状态直接写成任意值）。
- `PUT /lqg/sample/{id}/verify`（权限 `lqg:sample:verify`）：按 `doc/api-contract.md` 的请求体；判有效时一并落收样段其余字段。
  请求体可带可选的 `submitSegment`（口径复述 5，CR-20260923-09）：先判转移表、再校验送检段（`SubmitSegmentRules`，与工作台修改同一份）、再判核验必填与内部编号唯一，全部通过才把核验结论与送检段拼进同一条 UPDATE（送检段的列由 `SampleSubmitSegmentWriter` 写，与工作台修改、外部重提同一组）。业务拒绝一律 400（样本不存在 404）。
- 转移表写成纯函数 `VerifyTransitions.check(from, to, actorIsInternal)`（同包），`SampleVerifyService` 调它；EMBED-MODEL-001 的石蜡包埋送样核验也调它，合法转移只有这一份。
- 调用方（CR-20260924-10）：工作台核验抽屉，以及小程序内部人员的核验页（`UI:mp.verify.sample`，mp client token、`lqg_internal`，权限串同一个；外部 403）。小程序送检信息一项没改就不带 `submitSegment`，改了就带整段——因为这里是整段替换，只带改过的键会把其余键清空。
- `resubmitByExternal(sampleId, userId, 送检段字段)`：只改该种样本的送检段（组织：来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注；类器官：来源单位、类器官类型、备注）；
  校验 `submitter_id == userId` 且状态 ∈ {pending, invalid}；pending 的改完仍是 pending。本张只写 service，外部 controller 在 AUTH-EXT-001。类器官的送检段 CR-20260924-10 起多一项代数（外部重提同样整段替换）。
- `SAMPLE-MODEL-001` 的 `PUT /lqg/sample` 不许再直接改 `verify_status`（从入参 BO 里拿掉这个字段）。
- 单测覆盖转移表的每一格（合法 5 条 + 至少 6 条非法），tissue 与 organoid 各跑一遍。

## 3 边界（明确不做）

- 不做工作台的核验抽屉（SAMPLE-WEB-001）
- 不做外部 controller（AUTH-EXT-001）
- 不做核验通知（订阅消息 / 短信）
- 不做「核验历史」流水表——只记最后一次核验人与时间

## 4 完工报告要求

1. 转移表单测的用例清单与通过数
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：新增 accept 3（V02：核验带 submitSegment 与核验结论同一次保存、来源单位改选正式单位取快照；送检段校验不过或核验缺内部编号整次 400 且库里不变；判无效时送检段照存、收样段不落库；不带 submitSegment 送检段不动）；§0 口径复述与 §2 同步 FLOW:F-SAMPLE-01.step3 新口径。
- 2026-09-24 按 CR-20260924-10 更新：类器官送检段加「代数」、小程序也能核验——accept 3 末尾补四段（外部交一条带 P3 的类器官，核验时带「第4代」整次 400 且库里不变，带 p4 判有效后落成 P4）；blueprint_refs 补 FIELD:t_lqg_sample.passage；§0 口径 5 与 §2 补代数与小程序核验页这个调用方。
