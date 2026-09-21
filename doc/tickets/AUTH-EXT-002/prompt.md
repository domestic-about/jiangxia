---
ticket: AUTH-EXT-002
track: AUTH
phase: D3
size: M
req_refs:
  - REQ-AUTH-006
  - REQ-AUTH-013
  - REQ-AUTH-015
  - REQ-AUTH-004
depends_on:
  - EMBED-MODEL-001
  - AUTH-EXT-001
  - SAMPLE-MP-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/**
  - code/miniapp/src/pages/sample/detail-ext.vue
  - code/miniapp/src/components/lqg/EmbedCard.vue
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260923090*__AUTH-EXT-002-*.sql
adr_refs:
  - ADR-0004
blueprint_refs:
  - FLOW:F-EMBED-01.step4
  - FLOW:F-EMBED-01.step6
  - FLOW:F-EXT-01.step3
  - FLOW:F-EXT-01.step4
  - UI:mp.sample.detail.ext
  - FIELD:t_lqg_embed.paraffin_block_no
  - FIELD:t_lqg_embed.submitter_id
  - FIELD:t_lqg_embed.verify_status
accept:
  - name: "可见集合逐身份钉死（含待核验的外部送样）；同组可看、异组按不存在返回；石蜡块键集合是白名单（含操作人与包埋人，CR-20260918-07）；内部编号默认不出现、开关打开才出现；四条结构性不变量仍然成立"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      ids() { bash doc/verify/api.sh --as "$1" GET "/mp/ext/embed/list?pageSize=100&${2:-}" | jq -c '[.rows[].id|tostring]|sort'; } &&
      bash doc/verify/api.sh --as extB --fresh-module ruoyi-lqg GET /mp/ext/sample/9000001001 | jq -e '([.data.embeds[].paraffinBlockNo])==["T-E01-1","T-E01-2"] and .data.embeds[0].sectioned==true and .data.embeds[0].stainTypes==["HE","IHC"] and ([.data.embeds[0].markers[]|"\(.markerName):\(.expression)"]|sort)==["CK19:negative","Ki67:strong"] and .data.embeds[0].embedBy=="李工" and .data.embeds[0].operatorName=="李工" and (.data.embeds[1].embedBy // null)==null and .data.embeds[1].sectioned==false and ([.data.embeds[]|keys[]]|unique) - ["id","sampleId","submitNo","paraffinBlockNo","sampleType","organoidSourceType","tissueReceiveTime","tissueProcessTime","agaroseEmbedTime","dehydrateTime","agaroseSendTime","paraffinEmbedTime","sectionTime","sectioned","stainTypes","stainOther","markers","verifyStatus","invalidReason","submitterName","mine","editable","embedBy","operatorName"] == []' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001002 | jq -e '.code==200 and ([.data.embeds[] | [.paraffinBlockNo, .verifyStatus, .mine, .editable]] == [[null,"pending",true,true]])' &&
      bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001002 | jq -e '[.data.embeds[] | [.verifyStatus, .mine, .editable]] == [["pending",false,false]]' &&
      test "$(ids extA)" = '["9000002001","9000002002","9000002003","9000002006"]' && test "$(ids extB)" = '["9000002001","9000002002","9000002003","9000002006"]' &&
      test "$(ids extC)" = '[]' && test "$(ids extD)" = '["9000002004"]' && test "$(ids extE)" = '[]' && test "$(ids extF)" = '[]' &&
      test "$(ids extA onlyMine=true)" = '["9000002006"]' && test "$(ids extB onlyMine=true)" = '[]' &&
      bash doc/verify/api.sh --as extC GET /mp/ext/sample/9000001001 | jq -e '.code==404 and ((.data // {})|length==0)' &&
      bash doc/verify/api.sh --as extC GET /mp/ext/embed/9000002001 | jq -e '.code==404' &&
      python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" --eq false &&
      bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '(.data|has("internalNo"))==false' &&
      bash doc/verify/api.sh --as extA --bizcode GET '/system/config/list?configKey=lqg.ext.show-internal-no' | grep -qE '^(401|403)' &&
      CID=$(bash doc/verify/api.sh --as admin GET '/system/config/list?configKey=lqg.ext.show-internal-no' | jq -r '.rows[0].configId') && test -n "${CID}" && test "${CID}" != null &&
      bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"true\",\"configType\":\"N\"}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '.data.internalNo=="T-hli01"' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001002 | jq -e '(.data.internalNo // null)==null' &&
      bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"false\",\"configType\":\"N\"}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '(.data|has("internalNo"))==false' &&
      cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true)
    counterfeit: |-
      ExtEmbedVo 直接用了内部的 EmbedVo → keys 差集里出现 internalNo / remark / verifyBy 红；契约测试 I2 / I3 同时红。
      （`embedBy`、`operatorName` 自 CR-20260918-07 起在白名单里；**I3 的禁用字段表要同步摘掉这两个名字**，否则 cmp 后的契约测试必红——见 §0 的连带前置）
      照旧口径把操作人 / 包埋人剔了 → 两个「李工」那段红：甲方要的就是这两个。顺手把冻存或核验人也带出来 → keys 差集红。
      在 ext 包里注入 EmbedMapper 直接查 → I4 红。
      详情接口为了拼 embeds 重新按 id 查样本、绕过了 assertVisible → extC 拿到 200 红。单条石蜡包埋接口不查所挂样本的可见性 → extC 取 2001 拿到 200 红。
      详情只列有编号的石蜡块 → extA 看 1002 是空数组红：外部看不到自己送的样。
      列表按「本人提交的」而不是可见样本算 → extB 看不到 2001-2003 红；onlyMine 按样本的提交人算 → extB 带 onlyMine 会多出 2003（那是实验室在他的样本上建的块）红。
      外部角色够得着系统参数（能查甚至能改这个开关）→ extA 那段 403 红：CR-20260918-07 说了只有内部人员能在工作台系统管理里改。
      内部编号写死不给 → 开关打开那段红；写死给 → 默认那两段红（开关块跑完会把参数改回 false，中途红了要手动改回来再重跑）。
      开关读成启动时的 `@Value` / 常量而不是运行时的 sys_config → 改完参数下一次读不生效，`T-hli01` 那段红。
      开关打开就顺手给待核验的 1002 也编了个内部编号 → 1002 那段红：核验前本来就没有编号。
  - name: "提交与重提的写保护：替同组别人的样本送样、挂到自己无效的样本、挂到看不见的样本、改别人的送样、改实验室录入的石蜡块——全部被拒且库里不变；入参夹带的编号、染色、核验状态不生效；无效的改后重提回到待核验"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as extB --fresh-module ruoyi-lqg --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | grep -qE '^(400|403|404)' &&
      bash doc/verify/api.sh --as extA --bizcode POST /mp/ext/embed '{"sampleId":9000001003,"sampleType":"组织"}' | grep -qE '^(400|403)' &&
      bash doc/verify/api.sh --as extC --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | grep -qE '^(400|404)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'" --eq 1 &&
      bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"类器官","organoidSourceType":"肝类器官","paraffinBlockNo":"T-hack9","verifyStatus":"valid","embedBy":"外部","stainTypes":["HE"],"operatorName":"外部","submitSource":"internal"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT e.submit_source || '|' || e.verify_status || '|' || COALESCE(e.paraffin_block_no,'-') || '|' || COALESCE(e.embed_by,'-') || '|' || COALESCE(e.stain_types,'-') || '|' || COALESCE(e.operator_name,'-') || '|' || p.real_name FROM t_lqg_embed e JOIN t_lqg_ext_profile p ON p.user_id = e.submitter_id WHERE e.sample_id=9000001001 AND e.submit_source='external'" --eq "external|pending|-|-|-|-|王医生" &&
      bash doc/verify/api.sh --as extB --bizcode PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"被同组人改"}' | grep -qE '^(400|403|404)' &&
      bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/embed/9000002001 '{"sampleId":9000001001,"sampleType":"改实验室的块"}' | grep -qE '^(400|403|404)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_type IN ('被同组人改','改实验室的块')" --eq 0 &&
      bash doc/verify/api.sh --as staff PUT /lqg/embed/9000002006/verify '{"action":"invalid","reason":"样本类型写错"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as extA PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"类器官","organoidSourceType":"胆管类器官"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(invalid_reason,'-') || '|' || sample_type || '|' || organoid_source_type FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-|类器官|胆管类器官" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      提交只校验「样本可见」不校验「样本是本人送检的」→ extB 替 extA 的 1001 送样拿到 200，count 不是 1 红。
      不看样本状态 → extA 挂到自己无效的 1003 上，count 红。
      外部 BO 复用内部 EmbedBo → 夹带的石蜡块编号、包埋人、染色、操作人落库，join 那段红：外部自己给自己编了一个石蜡块。
      PUT 只校验可见不校验本人 → extB 改掉 extA 的 2006；不看来源 → extA 改掉实验室录的 2001，count 红。
      重提不回到待核验、或没清无效原因 → 最后一段红。
  - name: "小程序外部详情页接上了包埋卡片（含待核验的送样），卡片上有操作人与包埋人、没有冻存与核验人；内部编号这一行随后端给不给键来渲染（CR-20260918-07）"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && test -f dist/build/mp-weixin/pages/sample/detail-ext.js &&
      grep -q "EmbedCard" src/pages/sample/detail-ext.vue && grep -q "@/components/lqg/EmbedCard.vue" src/pages/sample/detail-ext.vue &&
      grep -q 'verifyStatus' src/components/lqg/EmbedCard.vue &&
      grep -q 'embedBy' src/components/lqg/EmbedCard.vue && grep -q 'operatorName' src/components/lqg/EmbedCard.vue &&
      ! grep -nE 'internalNo|verifyBy|frozenBy|cryo' src/components/lqg/EmbedCard.vue &&
      grep -qE 'v-if="[^"]*(internalNo|showInternalNo)' src/pages/sample/detail-ext.vue &&
      ! grep -nE 'verifyBy|frozenBy|cryo' src/pages/sample/detail-ext.vue
    counterfeit: |-
      EmbedCard 走桶口导入（小程序里渲染成空白且不报错）→ 第 3 段要求的是 .vue 直接路径，红。
      卡片不区分待核验 → verifyStatus 那段红：没编号的送样会显示成一块空白石蜡。
      照旧口径把包埋人 / 操作人藏了 → embedBy、operatorName 两段红（CR-20260918-07 起这两个要给）。
      卡片上顺手带出冻存或核验人 → 那条 `! grep` 红：甲方原话是「看不到冻存信息」。
      详情页把内部编号写死渲染（关着时显示空行 / `undefined`）→ v-if 那段红：关着的时候后端连键都不给，页面上这一行整行不该出现。
---

# AUTH-EXT-002 · 外部石蜡包埋送样：提交与改后重提、可见范围内的列表与详情；外部样本详情补上石蜡包埋情况（石蜡块编号标识，含待核验的送样；操作人与包埋人对外可见、内部编号按开关，CR-20260918-07）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/AUTH` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**EMBED-MODEL-001**、**AUTH-EXT-001**、**SAMPLE-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 2026-09-17 晚 Kevin 定外部可填石蜡包埋送样记录（CR-20260917-05）：`FLOW:F-EXT-01.step4` 的第三种记录；service 已在 EMBED-MODEL-001（`EmbedExternalService`），本张只接外部入口
  - **CR-20260918-07（甲方 2026-09-18 看设计图后提的）**：① 外部样本详情的石蜡包埋卡片**看得到操作人与包埋人**，冻存信息仍然看不到（甲方原话：「这儿应该是可以看得到操作人、包埋人，看不到冻存信息」）；
    ② 内部编号默认仍不对外，但打开系统参数 `lqg.ext.show-internal-no`（若依 sys_config，默认 false）后外部页面上显示，只有内部人员在工作台「系统管理 → 参数设置」里改，改完下一次读即生效。两条的权威口径在 `UI:mp.sample.detail.ext`
  - **⚠️ CR-20260918-07 的连带前置（动工前先确认，缺一就 STOP 报 Kevin）**：`doc/verify/fixtures/java/ExtChokepointContractTest.java` 的 I3 禁用字段表里还挂着 `operatorName`、`embedBy`——不摘掉，accept 段 1 的 cmp + 契约测试必红（AUTH-EXT-001 / 003 共用同一份基线）；
    `FLOW:F-EXT-01.step3`、`FLOW:F-EMBED-01.step4`、`ADR-0004` 的正文也还停在「不含操作人 / 包埋人」的旧口径——以 CR 与 `UI:mp.sample.detail.ext` 为准，别按旧句子做
  - **ADR-0004**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0004` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. 对外的标识是**石蜡块编号**——甲方原话：那是他们取的名字，不想让外部知道真实内部编号。`ExtEmbedVo` 里没有内部编号、备注、核验人、冻存；
     **操作人（`operatorName`）与包埋人（`embedBy`）自 CR-20260918-07 起要给**。
  2. 仍然走咽喉：列表先取 `visibleSampleIds` 再按样本 id 集合调 embed 域 service；单条先按记录取出 `sampleId` 再 `assertVisible`；ext 包不碰 `EmbedMapper`。
  3. **提交只许挂本人送检过、没被判无效的样本**：同组别人的样本看得见，但不能替他送样；入参 `ExtEmbedSubmitBo` 只有 `sampleId`、`sampleType`、`organoidSourceType`。
  4. **改后重提只许本人的待核验 / 无效**：同组的可看不可改；内部录入的石蜡块外部永远只读。
  5. 详情里的 `embeds` 包含外部提交还没核验的送样（`paraffinBlockNo` 为空、带状态与无效原因），外部要能看到自己送的样走到哪一步。
  6. **内部编号的开关读运行时的 sys_config，不读启动时的配置**（CR-20260918-07）：`lqg.ext.show-internal-no` 一处读、一处判（放 `ExtScopeService`，缺行按 false），
     关着时**连键都不出**（`FLOW:F-EXT-01.step3`：外部拿到的 JSON 里没有这些字段的键）；开着也只给样本详情上的内部编号，列表行照旧按送检单号指代（`UI:mp.history` 的外部行定义里没有内部编号），且待核验的样本本来就没有编号，不许现编。

## 1 背景与口径

微信答复：外部能看到与自己样本相关的所有东西，如质控结论、评分、石蜡包埋情况。模板批注 N2：包埋、培养情况。
会上 L295（REQ-AUTH-004）：「比如说这个石蜡包埋送样本，这个我也可以填，我填了之后，然后你们看我填的是不是有效的」。2026-09-17 晚 Kevin 定外部三张表里有它（CR-20260917-05）。
2026-09-18 甲方看过设计图后又放宽了两处（CR-20260918-07）：包埋卡片上「可以看得到操作人、包埋人，看不到冻存信息」；内部编号「加个开关就能决定让不让外部人员看到」，默认仍然不给。

## 2 实现要点

- `ExtEmbedController`（`/mp/ext/embed`，类级 `@SaCheckRole("lqg_external")`），形状见 `doc/api-contract.md`：
  `GET list`（`onlyMine?`、`verifyStatus?`）= 可见样本名下全部未删的石蜡包埋记录，按 `COALESCE(update_time, create_time)` 倒序；`GET {id}`；
  `POST`：`EmbedExternalService.submit`；`PUT {id}`：`EmbedExternalService.resubmit`。不可见按 404。
- `ExtEmbedVo`：`sectioned` = `section_time` 非空；`stainTypes` 数组；`markers` 只有名称与表达；`submitNo` 读时带出；`mine`、`editable`；
  **`embedBy`、`operatorName`（CR-20260918-07）**——仍然没有内部编号、备注、核验人、冻存。
- `ExtSampleDetailVo.embeds`：该样本未删的全部记录——有编号的按编号排序在前，没编号的（待核验 / 无效的送样）按提交时间排在后。
- `ExtSampleDetailVo.internalNo`（CR-20260918-07）：只在 `lqg.ext.show-internal-no=true` 时装配，默认（false）连键都不出；开关由 `ExtScopeService` 一处读 sys_config（`ConfigService` 按 key 取，缺行按 false），别在每个 VO 装配处各判一次。
  本张的 Flyway 迁移补上这一行参数（`V20260923090*__AUTH-EXT-002-ext-show-internal-no.sql`：`config_key='lqg.ext.show-internal-no'`、`config_value='false'`、`config_type='N'`、参数名「外部页面显示内部编号」；已经存在就跳过，别重复插）。
- 小程序外部详情页第二段接上：每条一张 `EmbedCard`（编号等宽字体、「已切片」徽标、工序时间、染色与 marker 徽标、**操作人与包埋人**；没编号的显示「待核验」或「无效 · 原因」）；没有则「暂无包埋记录」。
  第一段的内部编号一行按后端给没给这个键渲染（关着时整行不出现）。
- 契约测试文件**实施方不动**（accept 用 cmp 校验它逐字节没改）；`operatorName` / `embedBy` 从 I3 禁用字段表里摘掉是需求层的事，见 §0 的连带前置。新加的 VO、BO、controller 自然被它扫到。

## 3 边界（明确不做）

- 外部不能看冻存（CR-20260918-07 只放宽了操作人与包埋人）
- 外部列表行不显示内部编号——开关只作用于外部**样本详情**（CR-20260918-07）
- 不做「系统管理 → 参数设置」的菜单与授权（那是工作台壳 SYS-WEB-001 的活），本张只读这个参数
- 外部列表不加包埋提示
- 文档在 AUTH-EXT-003
- 小程序的外部送样填写页与历史页签在 EMBED-MP-001

## 4 完工报告要求

1. 六个外部身份各自的 `/mp/ext/embed/list` id 集合，以及 extA 带 `onlyMine=true` 的集合
2. extA 看 1002 详情的 `embeds` JSON；extB 看 1001 详情里一张包埋卡片的 JSON（要能看见 `embedBy` / `operatorName`）
3. **内部编号开关两态的输出**（CR-20260918-07）：改参数用的 `configId`，关着时 `has("internalNo")==false` 的原样输出、打开后 `internalNo` 的值，以及跑完已改回 `false`
4. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
5. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
6. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方（本张已知：契约第 51 / 64 行的 `ExtSampleDetailVo` / `ExtEmbedVo` 字段清单还是 CR-20260918-07 之前的写法）、没把握的口径
7. 验证用的后端 / 前端长进程已关，或明示留给谁
