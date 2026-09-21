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
      bash doc/verify/api.sh --as extA POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-snap01"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT submit_source || ':' || count(*) FROM t_lqg_sample WHERE submitter_id=9000000111 AND del_flag='0' GROUP BY submit_source" --col-set "external:3,internal:1" &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample s JOIN sys_user_role ur ON ur.user_id = s.submitter_id JOIN sys_role r ON r.role_id = ur.role_id WHERE s.submitter_id=9000000111 AND s.del_flag='0' AND r.role_key='lqg_internal' AND s.submit_source='external'" --eq 3 &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?submitSource=external&pageSize=100' | jq -e '[.rows[].id|tostring] | index("9000001001") != null' &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extA-* && bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      submit_source 不落库、每次按提交人当前角色现算 → 授权之后 1001 / 1002 / 1003 全变成 internal，集合红。这是「按人区分」最自然也最错的实现。
      列表的 submitSource 筛选也是现算的 → 倒数第 2 段找不到 1001 红。
      两侧不同源：一侧是授权接口改的角色，一侧是样本表里早先落下的值。结尾清 extA 的 token 缓存并 reseed，不把升级后的身份留给后面的断言。
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
- 转移表写成纯函数 `VerifyTransitions.check(from, to, actorIsInternal)`（同包），`SampleVerifyService` 调它；EMBED-MODEL-001 的石蜡包埋送样核验也调它，合法转移只有这一份。
- `resubmitByExternal(sampleId, userId, 送检段字段)`：只改该种样本的送检段（组织：来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注；类器官：来源单位、类器官类型、备注）；
  校验 `submitter_id == userId` 且状态 ∈ {pending, invalid}；pending 的改完仍是 pending。本张只写 service，外部 controller 在 AUTH-EXT-001。
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
