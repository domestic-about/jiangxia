---
ticket: EMBED-MODEL-001
track: EMBED
phase: D3
size: M
req_refs:
  - REQ-EMBED-001
  - REQ-EMBED-002
  - REQ-EMBED-003
  - REQ-EMBED-004
  - REQ-AUTH-004
depends_on:
  - SAMPLE-VERIFY-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/embed/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/embed/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260923110*__EMBED-MODEL-001-*.sql
adr_refs:
  - ADR-0010
  - ADR-0009
blueprint_refs:
  - FLOW:F-EMBED-01.step1
  - FLOW:F-EMBED-01.step2
  - FLOW:F-EMBED-01.step3
  - FLOW:F-EMBED-01.step6
  - FLOW:F-EMBED-01.step7
  - FIELD:t_lqg_embed.paraffin_block_no
  - FIELD:t_lqg_embed.stain_types
  - FIELD:t_lqg_embed.stain_other
  - FIELD:t_lqg_embed_marker.expression
  - FIELD:t_lqg_embed.sample_id
  - FIELD:t_lqg_embed.verify_status
  - FIELD:t_lqg_embed.submit_source
  - FIELD:t_lqg_embed.submitter_id
  - FIELD:t_lqg_embed.invalid_reason
accept:
  - name: "两张表与 SSOT 逐列相符（含公共字段、核验状态六列、石蜡块编号的部分唯一索引）且出自本票 Flyway"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_embed --table t_lqg_embed_marker --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260923110%__EMBED-MODEL-001-%'" --eq 1 &&
      grep -qi 'CREATE TABLE t_lqg_embed_marker' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260923110*__EMBED-MODEL-001-*.sql
    counterfeit: |-
      把「样本编号」建成了一列 sample_no 存字符串 → 「库里有、SSOT 没有」红；内部编号改了之后这里就对不上了。
      七个工序时间（tissue_receive_time / tissue_process_time / agarose_embed_time / dehydrate_time / agarose_send_time / paraffin_embed_time / section_time）任何一个建成 NOT NULL → 红：这张表是陆续补填的，建块当天只有编号。
      石蜡块编号还是 NOT NULL → 红：外部送样在核验前没有编号，落不了库。
      marker 直接在主表上建 marker_name + expression 两列 → SSOT 没有这两列，红；一个蜡块测两个 marker 就没处放。
  - name: "染色与挂靠规则：无染色与其余互斥、选其他必须写名称、字典外的值、挂到未核验样本、内部录入缺石蜡块编号、编号撞号——全部被拒且库里不变；有已核验石蜡块的样本不许改判无效"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      post() { bash doc/verify/api.sh --as staff --bizcode POST /lqg/embed "$1"; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      post '{"sampleId":9000001005,"paraffinBlockNo":"T-X1","stainTypes":["NONE","HE"]}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"paraffinBlockNo":"T-X2","stainTypes":["OTHER"]}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"paraffinBlockNo":"T-X3","stainTypes":["PAS"]}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001002,"paraffinBlockNo":"T-X4"}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"paraffinBlockNo":"T-E01-1"}' | grep -qE '^(400|500)' &&
      post '{"sampleId":9000001005,"sampleType":"组织"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no LIKE 'T-X%' OR (paraffin_block_no='T-E01-1' AND sample_id<>9000001001) OR (paraffin_block_no IS NULL AND submit_source='internal')" --eq 0 &&
      post '{"sampleId":9000001005,"paraffinBlockNo":"T-OK1","stainTypes":["IHC","HE"],"markers":[{"markerName":"Ki67","expression":"weak"},{"expression":"negative"}]}' | grep -qE '^200' &&
      python3 doc/verify/db.py --sql "SELECT e.stain_types || '|' || COALESCE(e.tissue_receive_time::text,'-') || '|' || e.submit_source || '|' || e.verify_status = 'HE,IHC|' || s.receive_date::text || '|internal|valid' FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id WHERE e.paraffin_block_no='T-OK1'" --eq True &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed_marker m JOIN t_lqg_embed e ON e.id=m.embed_id WHERE e.paraffin_block_no='T-OK1' AND m.del_flag='0'" --eq 2 &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001004/verify '{"action":"invalid","reason":"想改判"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT verify_status FROM t_lqg_sample WHERE id=9000001004" --eq valid &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      互斥只在前端做了 → 第 1 段后端照单全收红。
      「其他」不要求名称 → 导出时染色一格只有「其他」两个字，第 2 段红。
      挂到待核验样本 1002 也放行 → 第 4 段红：那个样本还没有内部编号，导出的「样本编号」是空的。
      石蜡块编号改成可空之后，内部录入也跟着不要求编号 → 第 6 段红、count 红：内部建的块没有编号，对外展示时什么标识都没有。
      落库没按固定顺序排（存成了 IHC,HE）或内部录入没落成 internal / valid → 「True」那段红。
      没注册 SampleChildrenChecker → 有石蜡块的 1004 被改判无效（拿到 200）红。
      每组被拒之后统一断库里没多出行：先落盘再报错骗不过去。
  - name: "读出来的形状：染色是数组、marker 成组、内部编号读时带出；外部送样带着核验状态与送检单号、待核验置顶；软删的石蜡块不出现"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/embed/9000002001 | jq -e '.data.stainTypes==["HE","IHC"] and .data.internalNo=="T-hli01" and ([.data.markers[]|"\(.markerName):\(.expression)"]|sort)==["CK19:negative","Ki67:strong"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/embed/list?pageSize=100' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002002","9000002003","9000002004","9000002006"] and (.rows[0].id|tostring)=="9000002006" and .rows[0].verifyStatus=="pending" and .rows[0].submitSource=="external" and .rows[0].paraffinBlockNo==null and .rows[0].submitNo=="SJ90000002" and .rows[0].sampleVerifyStatus=="pending"' &&
      bash doc/verify/api.sh --as staff GET '/lqg/embed/list?internalNo=T-hli01' | jq -e '(.rows|length)==2' &&
      bash doc/verify/api.sh --as staff GET '/lqg/embed/list?verifyStatus=pending' | jq -e '[.rows[].id|tostring]==["9000002006"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=IHC' | jq -e '[.rows[].paraffinBlockNo]==["T-E01-1"]'
    counterfeit: |-
      stainTypes 原样返回逗号串 → 第 1 段红；三个端各自 split 迟早有一个忘了处理空串。
      列表手写 SQL join 样本表、忘了带 e.del_flag='0' → 软删的 T-E05-X 出现，第 2 段红。
      外部送样没置顶、或行里没带所挂样本的核验状态 → 第 2 段红：工作台核验抽屉不知道「判为有效」该不该置灰。
      按染色筛选用 LIKE '%HE%' → 查 HE 会把含 OTHER 的也带出来（O-T-**HE**-R）；这里用 IHC 钉住一条，完工报告另贴 stain=HE 的结果应只有 T-E01-1。
  - name: "外部送样的核验状态机：所挂样本还没有效时判有效被拒、缺原因判无效被拒、缺编号或撞号被拒、普通保存改不动待核验送样——每次被拒库里都不变；样本有效后判有效落下编号与核验人"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      v() { bash doc/verify/api.sh --as staff --bizcode PUT "/lqg/embed/$1/verify" "$2"; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null &&
      v 9000002006 '{"action":"valid","paraffinBlockNo":"T-E06-1"}' | grep -qE '^(400|500)' &&
      v 9000002006 '{"action":"invalid"}' | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-9","sampleType":"被普通保存改掉"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') || '|' || COALESCE(verify_by::text,'-') || '|' || sample_type FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-|-|组织" &&
      bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli77"}' | jq -e '.code==200' &&
      v 9000002006 '{"action":"valid"}' | grep -qE '^(400|500)' &&
      v 9000002006 '{"action":"valid","paraffinBlockNo":"T-E01-1"}' | grep -qE '^(400|500)' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-" &&
      v 9000002006 '{"action":"valid","paraffinBlockNo":"T-E06-1"}' | grep -qE '^200' &&
      python3 doc/verify/db.py --sql "SELECT e.verify_status || '|' || e.paraffin_block_no || '|' || e.verify_by || '|' || e.submit_source || '|' || e.submitter_id || '|' || s.internal_no FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id WHERE e.id=9000002006" --eq "valid|T-E06-1|9000000101|external|9000000111|T-hli77" &&
      bash doc/verify/api.sh --as staff PUT /lqg/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-1","dehydrateTime":"2026-09-17","verifyStatus":"pending"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || dehydrate_time::text FROM t_lqg_embed WHERE id=9000002006" --eq "valid|2026-09-17" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      判有效不看所挂样本的状态 → 第 1 段拿到 200 红：一块石蜡挂在一个还没有内部编号的样本上，导出的「样本编号」是空的。
      判有效不要求编号、或编号撞号没拦 → 中间两段红；库里会出现两块同编号的石蜡，对外展示时分不清。
      普通保存 PUT /lqg/embed 不看状态 → 待核验送样被直接填上编号、样本类型被改，库内状态那段红：核验被一次普通保存绕过去了。
      先 UPDATE 再校验（或事务没回滚）→ 被拒之后库内不是 pending|- 红。
      PUT 的入参带着 verifyStatus 且被写进库 → 最后一段不是 valid 红：状态只许经核验接口改。
      没复用 VerifyTransitions、自己写了一份少了某条转移 → 完工报告里单测对照表会对不上（本条断的是这几条最容易漏的路径）。
  - name: "外部送样的读写口不泄露记录在不在（CR-20260923-09，issue #299）：看不见的石蜡块与样本，和根本不存在的 id 相比业务码与提示逐字相同（404）；看得见但不是本人的、所挂样本已判无效的才回 400；每次被拒库里都不变"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as extC --fresh-module ruoyi-lqg GET /mp/me >/dev/null &&
      test "$(bash doc/verify/api.sh --as extC --bizcode PUT /mp/ext/embed/9000002001 '{"sampleType":"x"}')" = "$(bash doc/verify/api.sh --as extC --bizcode PUT /mp/ext/embed/999999999 '{"sampleType":"x"}')" &&
      bash doc/verify/api.sh --as extC --bizcode PUT /mp/ext/embed/9000002001 '{"sampleType":"x"}' | grep -qE '^404' &&
      test "$(bash doc/verify/api.sh --as extC --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"x"}')" = "$(bash doc/verify/api.sh --as extC --bizcode POST /mp/ext/embed '{"sampleId":999999999,"sampleType":"x"}')" &&
      bash doc/verify/api.sh --as extC --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"x"}' | grep -qE '^404' &&
      test "$(bash doc/verify/api.sh --as extC --bizcode GET /mp/ext/embed/9000002001)" = "$(bash doc/verify/api.sh --as extC --bizcode GET /mp/ext/embed/999999999)" &&
      bash doc/verify/api.sh --as extC --bizcode GET /mp/ext/embed/9000002001 | grep -qE '^404' &&
      bash doc/verify/api.sh --as extB --bizcode PUT /mp/ext/embed/9000002006 '{"sampleType":"x"}' | grep -qE '^400' &&
      bash doc/verify/api.sh --as extB --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"x"}' | grep -qE '^400' &&
      bash doc/verify/api.sh --as extA --bizcode POST /mp/ext/embed '{"sampleId":9000001003,"sampleType":"x"}' | grep -qE '^400' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_embed WHERE sample_type='x') || '|' || (SELECT count(*) FROM t_lqg_embed WHERE submit_source='external') || '|' || (SELECT sample_type FROM t_lqg_embed WHERE id=9000002001)" --eq "0|1|组织" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      `EmbedExternalService` 自己按 id 查记录和样本：别人的回 400「只能改本人的」、不存在的回 404 → PUT / POST 两组逐字比对红：400 与 404 之差就是「这个 id 有没有」的预言机，雪花 id 难猜也不该留。
      读口与写口各写一句提示（例如 GET 看不见的回「样本不存在」、不存在的回「石蜡包埋记录不存在」）→ GET 那组红：可见性只在 `ExtScopeService` 一处判，读写同一句。
      只比对不断码 → 两边都连不上时空串相等也会过，所以每组后面再断一次 404。
      把「看得见但不是本人的」也改成 404 → extB / extA 那三段 `^400` 红：同组的人在详情里明明看得见这条，写口却说它不存在，自相矛盾。
      先写库再判可见（或事务没回滚）→ 最后的库内计数红：被拒之后库里不能多出外部送样、2001 的样本类型也不能被改成 x。
  - name: "核验时一并保存补填段（CR-20260923-09，V02b）：判有效时带 fill，补填的工序时间、染色、marker、备注与核验结论同一次落库；fill 不合规（无染色与其余并存）、判无效却带实验室补填项、fill 里夹带所挂样本——整次被拒且库里一个字不变；判无效只存原因与样本类型的更正"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      v() { bash doc/verify/api.sh --as staff --bizcode PUT /lqg/embed/9000002006/verify "$1"; } &&
      row() { python3 doc/verify/db.py --quiet --sql "SELECT e.verify_status || '|' || COALESCE(e.paraffin_block_no,'-') || '|' || COALESCE(e.sample_type,'-') || '|' || COALESCE(e.dehydrate_time::text,'-') || '|' || COALESCE(e.stain_types,'-') || '|' || COALESCE(e.remark,'-') || '|' || COALESCE(e.invalid_reason,'-') || '|' || (SELECT count(*) FROM t_lqg_embed_marker m WHERE m.embed_id=e.id AND m.del_flag='0') FROM t_lqg_embed e WHERE e.id=9000002006" | head -1; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli77"}' | jq -e '.code==200' &&
      test "$(row)" = "pending|-|组织|-|-|-|-|0" &&
      ! v '{"action":"valid","paraffinBlockNo":"T-F5-X","fill":{"stainTypes":["NONE","HE"],"dehydrateTime":"2026-09-19"}}' | grep -qE '^200' &&
      test "$(row)" = "pending|-|组织|-|-|-|-|0" &&
      v '{"action":"invalid","reason":"信息不符","fill":{"sampleType":"组织（更正）","dehydrateTime":"2026-09-19","stainTypes":["HE"]}}' | grep -qE '^400.*脱水时间' &&
      test "$(row)" = "pending|-|组织|-|-|-|-|0" &&
      v '{"action":"valid","paraffinBlockNo":"T-F5-X","fill":{"sampleId":9000001005,"dehydrateTime":"2026-09-19"}}' | grep -qE '^400' &&
      test "$(row)" = "pending|-|组织|-|-|-|-|0" &&
      v '{"action":"invalid","reason":"信息不符","fill":{"sampleType":"组织（更正）"}}' | grep -qE '^200' &&
      test "$(row)" = "invalid|-|组织（更正）|-|-|-|信息不符|0" &&
      v '{"action":"valid","paraffinBlockNo":"T-F5-1","fill":{"dehydrateTime":"2026-09-19","stainTypes":["IHC","HE"],"markers":[{"markerName":"Ki67","expression":"strong"}],"remark":"核验时补填"}}' | grep -qE '^200' &&
      test "$(row)" = "valid|T-F5-1|组织（更正）|2026-09-19|HE,IHC|核验时补填|-|1" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      核验接口不认 fill、抽屉里补填的内容被静默丢弃（CR-20260923-09 之前的形态，issue #147）→ 最后一段库里脱水时间、染色、备注仍是空、marker 0 行，红。
      fill 另起一个请求或另一个事务先存、再判核验 → 第 6 段（无染色与 HE 并存被拒）之后脱水时间已经落库，第 7 段红：核验被拒了，补填段却改了。
      fill 不走石蜡包埋修改的同一份规则（EmbedFillRules）→ 无染色与 HE 并存被放行，第 6 段拿到 200 红。
      判无效时把实验室补填项也照存、或静默丢掉却回 200 → 第 8 段不是 400「脱水时间…」红：界面必须在原因弹窗里明说这些不保存，接口带了就拒。
      fill 能夹带 sampleId 换掉所挂样本 → 第 10 段不是 400 红：所挂样本核验时不能换。
      判无效时样本类型的更正没随原因一起存 → 第 13 段不是「组织（更正）」红；改判有效时没清掉 invalid_reason → 最后一段不是「-」红。
      每个被拒后面都跟一次整行比对：只看业务码的话，先落盘再报错也是绿的。
---

# EMBED-MODEL-001 · 石蜡包埋送样记录：建模、染色与 marker 的校验规则、内部增删改查接口、外部送样的提交与核验状态机

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/EMBED` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-VERIFY-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 2026-09-17 晚 Kevin 定外部也能填石蜡包埋送样记录（CR-20260917-05）：`FLOW:F-EMBED-01.step6`（外部提交）与 `.step7`（实验室核验）是本张新加的一半；合法转移复用 SAMPLE-VERIFY-001 的 `VerifyTransitions`，不另写一份
  - **ADR-0010**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0010` 取结构化口径）
  - **ADR-0009**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0009` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **染色是五个按钮**：HE / IF / IHC / 其他 / **无染色**（「无染色」是会上补的，模板里没有）。多选；「无染色」与其余四个互斥；选「其他」必须写具体名称。
  2. **这张表会被反复打开补填**：七个工序时间全部可空（= 模板里的 7 个时间列，别少数一个），保存不要求填完。
  3. 模板的「样本编号」= 样本主档的内部编号，**读时带出，不落库**；表里只存 `sample_id`。**内部录入**只能挂到已核验有效的样本上，且石蜡块编号必填、直接有效。
  4. **外部送样**只能挂本人送检过、没被判无效的样本（待核验的样本可以挂），只收样本类型、类器官来源类型两项；落库待核验、石蜡块编号为空。
  5. **判有效要同时满足**：石蜡块编号非空且全库唯一、所挂样本已核验有效；判无效必须写原因。缺了就拒绝，库里什么都不变。
  6. **待核验、无效的送样不能被普通保存改掉**：`PUT /lqg/embed` 对这两种状态直接拒绝，入参里也没有 `verifyStatus`——状态只经核验接口改。

## 1 背景与口径

模板 C：石蜡包埋送样记录 16 列（REQ-EMBED-001）；染色与 mark 的表达情况「需加按钮进行选择」。会上 L46：再添加一个「没有染色」选项。
微信答复：石蜡块编号是实验室自己取的名字（如 E15-1-2026.07.29），对外展示用它。
会上 L295（REQ-AUTH-004）：「比如说这个石蜡包埋送样本，这个我也可以填，我填了之后，然后你们看我填的是不是有效的」；2026-09-17 晚 Kevin 定外部首页三张表里有它（CR-20260917-05）。

## 2 实现要点

- DDL：`gen_ddl_pg.py --migration V202609231100__EMBED-MODEL-001-embed.sql` → `t_lqg_embed`（含 `submit_source / submitter_id / verify_status / verify_by / verify_time / invalid_reason`，石蜡块编号可空 + 部分唯一索引）、`t_lqg_embed_marker`。
- 包 `org.dromara.lqg.embed`，接口 `/lqg/embed`（权限串 `lqg:embed:{list,query,add,edit,remove,export,verify}`），形状见 `doc/api-contract.md`：
  `stainTypes` 对外是数组、落库是逗号串（固定按 HE, IF, IHC, OTHER, NONE 排序）；`markers` 整组替换（先软删旧的再插新的，同一事务）。
- 内部录入校验（service 层，不满足 → `ServiceException`，什么都不落）：
  1. `sampleId` 存在、未删、`verify_status='valid'`；`paraffinBlockNo` 非空且全库唯一（部分唯一索引兜底，service 层给人话）
  2. `stainTypes` 每个值都在字典 `lqg_stain_type` 里；含 `NONE` 时不得有别的；含 `OTHER` 时 `stainOther` 非空；不含 `OTHER` 时 `stainOther` 置空
  3. marker 的 `expression` 在字典 `lqg_marker_expr` 里；`markerName` 可空
  内部新增落 `submit_source='internal'`、`verify_status='valid'`、`submitter_id = verify_by = 当前用户`。`PUT /lqg/embed`：记录是待核验或无效 → 400。
- 新建时 `tissue_receive_time`、`tissue_process_time` 未传则从样本带出（处理时间取日期部分）。
- 列表每行读时带出 `internalNo`、`sourceUnitName`、`submitNo`、`sampleVerifyStatus`（所挂样本的核验状态，工作台核验抽屉据此置灰「判为有效」）；筛选：`paraffinBlockNo`、`internalNo`、`sampleId`、`stain`、`sectionTimeBegin/End`、`verifyStatus`、`submitSource`；待核验置顶，其余按创建时间倒序。
- 核验：`EmbedVerifyService` + `PUT /lqg/embed/{id}/verify`（`{action, paraffinBlockNo, reason, fill?}`），转移合法性调 `VerifyTransitions.check`；判有效落编号、`verify_by`、`verify_time`，清 `invalid_reason`。
  **核验一并保存补填段**（CR-20260923-09，V02b，`FLOW:F-EMBED-01.step7`）：`fill` 可选，键为 `PUT /lqg/embed` 去掉 `id`、`sampleId`、`paraffinBlockNo` 后的 15 项；补丁语义与校验同 `PUT /lqg/embed`（`EmbedFillRules` + `EmbedFillWriter` 同一份），与核验结论拼进同一个事务；判有效 15 项都收；判无效只收 `sampleType`、`organoidSourceType`，带其余 13 项回 400 并写明是哪几项；`fill` 里带 `sampleId` 或 `paraffinBlockNo` → 400；任何一条被拒库里都不变；不带 `fill`（或 `fill:null`）= 补填段不动（老调用方不受影响）。
  调用方（CR-20260924-10）：工作台核验抽屉，以及小程序内部人员的核验页（`UI:mp.verify.embed`，mp client token、`lqg_internal`，权限串 `lqg:embed:verify` 同一个；外部 403）。小程序的 `fill` 只带改过的键，一项没改就不带；业务拒绝（如所挂样本还未核验有效、石蜡块编号重复）目前回的是 `ServiceException` 缺省码而不是 400，小程序与工作台都按「非 200 就原样显示后端原话」处理。
- 外部送样（只写 service，外部 controller 在 AUTH-EXT-002）：`EmbedExternalService.submit(userId, sampleId, sampleType, organoidSourceType)`、`resubmit(userId, embedId, …)`——
  校验样本 `submitter_id == userId` 且样本状态 ≠ invalid；resubmit 另校验记录 `submitter_id == userId` 且状态 ∈ {pending, invalid}，改完回到 pending 并清 `invalid_reason / verify_by / verify_time`。
  **可见性不在本类里判**（CR-20260923-09，issue #299）：挂样走 `ExtScopeService.assertUsableForEmbed`，按记录走 `ExtScopeService.assertEmbedVisible`——不可见、不存在、已软删同一个 404、同一句提示（「样本不存在」/「石蜡包埋记录不存在」，读口写口同一句），看得见之后才判「本人 / 没被判无效」，不满足回 400。
  本类因此不持有样本的 mapper；`EmbedExternalScopeContractTest`（本票 test 目录下）钉住这条结构与「不可见 = 不存在」的行为。resubmit 的判定顺序：可见 → 本人 → 状态 → 字段 → 换挂的新样本。
- 注册一个 `SampleChildrenChecker`：该样本名下有未删且**已核验有效**的石蜡块 → true（样本不许再改判无效）。

## 3 边界（明确不做）

- 不做页面（EMBED-WEB-001 / EMBED-MP-001）、不做导出
- 不做外部 controller 与对外展示（AUTH-EXT-002）
- 不做切片染色提示（SAMPLE-HINT-001）
- 不校验工序时间的先后顺序（甲方没提；实验记录常有补录）
- 石蜡块编号不自动生成
- 不做核验通知

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁

## 5 票面更新

- 2026-09-23 按 CR-20260923-09 更新：新增 accept 5——外部送样的 PUT / POST / GET 对看不见的记录与根本不存在的 id（999999999）码与提示逐字相同且为 404，看得见但不是本人的、已判无效的回 400，被拒后库里不变；§2 写明可见性统一交给 `ExtScopeService`。
- 2026-09-23 按 CR-20260923-09 更新（F5 石蜡包埋核验抽屉，V02b；权威 FLOW:F-EMBED-01.step7 变更）：§2 核验入参加上 `fill`（15 项补填、补丁语义与校验同 `PUT /lqg/embed`、同一事务；判无效只收样本类型与类器官来源类型）；新增 accept 6——2006 带非法 fill、判无效带实验室补填项、fill 夹带 sampleId 都被拒且库里整行不变，判无效只存原因与样本类型更正，判有效带合法 fill 后补填段与 marker 同一次落库。
- 2026-09-24 按 CR-20260924-10 更新：§2 核验一段补「小程序核验页也调这个接口、fill 只带改过的键」（FLOW:F-EMBED-01.step7 的 actor 改为 mp/admin）；accept 逐条核过不用改。
