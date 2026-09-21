---
ticket: AUTH-EXT-001
track: AUTH
phase: D2
size: L
req_refs:
  - REQ-AUTH-005
  - REQ-AUTH-006
  - REQ-AUTH-007
  - REQ-AUTH-009
  - REQ-AUTH-012
  - REQ-AUTH-015
depends_on:
  - SAMPLE-VERIFY-001
  - AUTH-GROUP-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application*.yml
adr_refs:
  - ADR-0004
  - ADR-0003
blueprint_refs:
  - FLOW:F-EXT-01.step1
  - FLOW:F-EXT-01.step2
  - FLOW:F-EXT-01.step3
  - FLOW:F-EXT-01.step4
  - FLOW:F-SAMPLE-01.step1
  - FLOW:F-SAMPLE-01.step4
  - FIELD:t_lqg_ext_profile.bind_status
  - FIELD:t_lqg_sample.submitter_id
  - FIELD:t_lqg_sample.internal_no
  - FIELD:t_lqg_sample.organoid_type
accept:
  - name: "四条结构性不变量（契约测试逐字节未改）：入口只有 /mp/ext、只返回 Ext 类型、Ext VO 不带内部专用字段、只有范围解析器能碰 Mapper"
    form: STATE
    run: |-
      cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true) &&
      test "$(grep -rlE '@SaCheckRole\("lqg_external"\)' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext | wc -l | tr -d ' ')" -ge 2 &&
      ! grep -rnE '"/mp/ext' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java --include=*.java | grep -v '/org/dromara/lqg/ext/' | grep -q .
    counterfeit: |-
      图省事让外部 controller 直接返回内部的 SampleVo → I2 红。
      ExtSampleDetailVo extends SampleVo 再 @JsonIgnore 几个字段 → I3 红（继承来的 operatorName 也算）：哪天有人给 SampleVo 加字段，外部就多看到一个。
      在 ExtSampleController 里直接注入 SampleMapper 写查询 → I4 红：那条查询就绕过了范围解析器。
      改测试文件让它过 → cmp 红。ext 包名写错导致测试在空集合上空转 → 测试自己的 assertFalse(isEmpty) 红。
      controller 忘了加类级角色注解 → 第 3 段红（内部账号、甚至将来的别的角色都能调外部接口）。
  - name: "可见集合逐身份钉死：同组互看、同单位异组不可见、同组但未核验不可见、软删不可见；「只看我提交的」只在可见集合里收窄；越权访问单条按不存在返回且不泄露任何字段；返回体里没有内部编号等键"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      ids() { bash doc/verify/api.sh --as "$1" GET "/mp/ext/sample/list?pageSize=100&${2:-}" | jq -c '[.rows[].id|tostring]|sort'; } &&
      bash doc/verify/api.sh --as extA --fresh-module ruoyi-lqg GET /mp/me >/dev/null &&
      test "$(ids extA)" = '["9000001001","9000001002","9000001003","9000001004"]' &&
      test "$(ids extB)" = '["9000001001","9000001002","9000001003","9000001004"]' &&
      test "$(ids extC)" = '["9000001005"]' && test "$(ids extD)" = '["9000001006"]' &&
      test "$(ids extE)" = '["9000001007"]' && test "$(ids extF)" = '[]' &&
      test "$(ids extA onlyMine=true)" = '["9000001001","9000001002","9000001003"]' && test "$(ids extB onlyMine=true)" = '["9000001004"]' &&
      test "$(ids extB 'onlyMine=true&verifyStatus=pending')" = '[]' && test "$(ids extA 'sampleKind=organoid')" = '[]' &&
      bash doc/verify/api.sh --as extC GET /mp/ext/sample/9000001001 | jq -e '.code==404 and ((.data // {}) | length == 0)' &&
      bash doc/verify/api.sh --as extE GET /mp/ext/sample/9000001001 | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001010 | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '.code==200 and .data.donorName=="测试供体甲" and .data.mine==false and .data.editable==false and .data.submitterName=="王医生" and ((.data|keys) - ["id","submitNo","sampleKind","sourceUnitName","donorName","gender","age","hospitalNo","tissueType","organoidType","hasPathology","remark","verifyStatus","invalidReason","submitterName","mine","editable","createTime","embeds","docs"] == [])' &&
      bash doc/verify/api.sh --as extA GET '/mp/ext/sample/list?pageSize=100' | jq -e '[.rows[] | keys[]] | unique | (index("internalNo")==null and index("donorName")==null and index("hospitalNo")==null and index("operatorName")==null) and (index("donorNameMasked")!=null)' &&
      bash doc/verify/api.sh --as extA --bizcode GET /mp/ext/home | grep -qE '^404' &&
      bash doc/verify/api.sh --as extA --bizcode GET '/lqg/sample/list' | grep -qE '^403' &&
      bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/sample/list' | grep -qE '^403' &&
      bash doc/verify/api.sh --as staff --bizcode GET '/mp/ext/sample/list' | grep -qE '^403'
    counterfeit: |-
      按「来源单位名称相同」算可见（最直觉的写法）→ extC 会看到 A、B 的样本，集合红。
      同组判断漏了「双方都 verified」→ extE 看到 1001-1004，或 extA 看到 1007，红。seed 里 extE 就是为这条埋的病灶。
      onlyMine 写成另起一套「submitter_id = 本人」的查询、不再过可见集合 → 今天看不出来；但把它写成「create_by = 本人」时，extB 会多出别人替他建的记录。这里钉住 extA / extB 各自的集合，外加与状态筛选叠加的空集。
      onlyMine 被忽略（前端自己过滤）→ extA 带 onlyMine 仍有 1004 红。
      列表过滤了但详情接口忘了 assertVisible（只在列表里看不到、猜 id 能直接打开）→ extC 取 1001 拿到 200 红。这是越权事故最常见的形态。
      越权时返回 403 → .code==404 红（403 泄露了存在性）。
      详情 VO 多带了 receiveDate / internalNo / operatorName 任何一个 → keys 差集非空红。列表里直接给了全名而不是掩码 → 红。
      旧的 /mp/ext/home 还留着 → 404 那段红（首页已经没有最近记录，留着就是一个没人维护的外部出口）。
      最后三段是角色闸：外部打内部接口、内部打外部接口都必须 403。
  - name: "写保护：同组别人的样本可看不可改、已核验有效的自己也改不了、入参里夹带内部字段不生效（组织样本与类器官收样两种）；无效的改后重提回到待核验；每个被拒之后库里都没变"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as extB --fresh-module ruoyi-lqg --bizcode PUT /mp/ext/sample/9000001002 '{"sourceUnitName":"A 医院","donorName":"被同组人篡改","tissueType":"肝组织"}' | grep -qE '^(400|403|404)' &&
      bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/sample/9000001001 '{"sourceUnitName":"A 医院","donorName":"改已核验的","tissueType":"肝组织"}' | grep -qE '^(400|403)' &&
      bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' | jq -e '[.rows[] | select((.id|tostring)=="9000001001" or (.id|tostring)=="9000001002") | .donorName] | sort == ["测试供体乙","测试供体甲"]' &&
      bash doc/verify/api.sh --as extA PUT /mp/ext/sample/9000001003 '{"sourceUnitName":"A 医院","donorName":"测试供体丙","gender":"male","hospitalNo":"ZY0000003","tissueType":"肝组织","internalNo":"T-hack01","verifyStatus":"valid","submitSource":"internal","receiveDate":"2026-09-17"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || submit_source || '|' || COALESCE(receive_date::text,'-') || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=9000001003" --eq "pending|-|external|-|-" &&
      bash doc/verify/api.sh --as extC POST /mp/ext/sample '{"sourceUnitName":"A 医院","donorName":"新送检","gender":"female","age":"40","hospitalNo":"ZYNEW01","tissueType":"胃组织","hasPathology":"N"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT sample_kind || '|' || submit_source || '|' || verify_status || '|' || submitter_id || '|' || (submit_no ~ '^SJ[0-9]{8}$') FROM t_lqg_sample WHERE tissue_type='胃组织' AND submitter_id=9000000113 AND create_time > now() - interval '5 minutes'" --eq "tissue|external|pending|9000000113|true" &&
      bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官","remark":"外部送类器官","internalNo":"T-hack02","verifyStatus":"valid","receiveDate":"2026-09-17","hasViabilityReport":"Y","operatorName":"外部自填"}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT sample_kind || '|' || submit_source || '|' || verify_status || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(receive_date::text,'-') || '|' || COALESCE(has_viability_report,'-') || '|' || COALESCE(operator_name,'-') || '|' || COALESCE(donor_name,'-') FROM t_lqg_sample WHERE organoid_type='胃类器官' AND submitter_id=9000000113" --eq "organoid|external|pending|-|-|-|-|-" &&
      OID="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_sample WHERE organoid_type='胃类器官' AND submitter_id=9000000113" | head -1)" &&
      bash doc/verify/api.sh --as extD --bizcode PUT "/mp/ext/organoid/${OID}" '{"sourceUnitName":"A 医院","organoidType":"被外单位改"}' | grep -qE '^(400|403|404)' &&
      bash doc/verify/api.sh --as extC --bizcode PUT "/mp/ext/sample/${OID}" '{"sourceUnitName":"A 医院","donorName":"借组织样本的口改","tissueType":"肝组织"}' | grep -qE '^(400|404)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE organoid_type='被外单位改' OR (donor_name IS NOT NULL AND sample_kind='organoid' AND submitter_id=9000000113)" --eq 0 &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      PUT 只校验「可见」不校验「本人」→ extB 改掉了 extA 的 1002，第 4 段供体姓名变成「被同组人篡改」红。同组是能看不能改。
      不校验状态 → extA 改掉了已核验的 1001，红。
      外部 BO 复用内部 SampleBo → 夹带的 internalNo / verifyStatus / submitSource / receiveDate 被写进库，第 6 段红。这是「外部给自己的样本判了有效、还写了个内部编号」的事故形态。
      类器官收样的外部入参复用内部 BO → 夹带的内部编号、收样日期、细胞活率报告、操作人落库，类器官那段红。
      外部类器官 PUT 没过 assertVisible → extD 改掉了 extC 的记录，最后的 count 红。
      组织样本的 PUT 不校验 sample_kind → extC 借 /mp/ext/sample 给类器官样本写进了供体姓名，count 红。
      重提后没清 invalid_reason → 末位不是 '-' 红。
      被拒的几段后面跟的都是走内部接口或直连库读出来的真实值，不是只看业务码。
---

# AUTH-EXT-001 · 外部隔离咽喉：/mp/ext/** 接口组、可见范围解析、专用 VO、写保护（组织样本与类器官收样两种送检），以及四条结构性不变量

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/AUTH` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SAMPLE-VERIFY-001**、**AUTH-GROUP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0004** 全文——本张就是它的实现。四条不变量：入口只有一组、范围只有一处算、字段只有一种出口、ext 包不直接碰 Mapper
  - **外部可填三张表**（Kevin 2026-09-17 晚定，CR-20260917-05）：本张管样本主档上的两种——组织样本（样本记录信息表）与类器官收样记录；石蜡包埋送样在 AUTH-EXT-002。外部没有任何 -80 冻存接口
  - **REQ-AUTH-012 甲方 2026-09-18 已答**（CR-20260918-07，OQ-2 关闭）：内部编号默认**仍不**对外，但开关 `lqg.ext.show-internal-no` 从 `application.yml` 配置项改成若依 **sys_config 系统参数**（默认 false，甲方在工作台「系统管理 → 参数设置」里自己改，运行时生效）。本张只管装配时读它、关着连键都不出；那行 sys_config 由 AUTH-EXT-002 的 Flyway 插入（已存在则跳过），菜单授权在 SYS-WEB-001
  - 记忆里的教训（Kevin 的 stop-when-proving-a-negative）：别去证明「所有接口都没漏」，立一个能验的咽喉不变量
  - **ADR-0003**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0003` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **同组 = 本人 verified 且对方 verified 且 group_id 相同**。extE 和 extA 同组但 extE 未核验：E 看不到 A 的，A 也看不到 E 的。
  2. **不可见的样本按「不存在」返回**（业务码 404、data 为空），不要返回 403——403 等于告诉对方「这个 id 存在」。
  3. **Ext VO 里根本没有那些字段**，不是返回 null 也不是前端不显示。`internalNo` 只有开关打开时才出现这个键（`@JsonInclude(NON_NULL)`）。
  4. 外部写接口的入参 BO **只有送检段字段**，两种各一个：`ExtSampleSubmitBo`（组织）、`ExtOrganoidSubmitBo`（来源单位、类器官类型、备注）。不要复用内部的 SampleBo 再「忽略多余字段」——哪天有人给 SampleBo 加字段，外部就能写它。
  5. **`onlyMine` 只是在可见集合里再按 `submitter_id = 本人` 收窄**（「历史编辑记录」的「只看我提交的」开关），不是另一套范围；不传就是整个可见集合。

## 1 背景与口径

模板批注 N2：外部人员只能看到自己相关样本……看不到其他样本的信息。微信答复：同单位的人不能互看，同组可以看；外部能看到与自己样本相关的所有东西。
会上 L280-L289：外部只能动样本记录信息表里的内容。2026-09-17 晚 Kevin 定（CR-20260917-05）：外部首页三张表（没有 -80 冻存记录），点进去都是填写；
所有人在「我的 → 历史编辑记录」找回自己填过的记录——外部看到本人与同组的，可切「只看我提交的」。
这是合同验收里最不能出事的一条，所以不把它散在每个接口里各自判断，而是收成一处。

## 2 实现要点

### 2.1 包与角色
- 包 `org.dromara.lqg.ext`：`ExtScopeService` / `ExtScopeServiceImpl`、`ExtSampleController`（`@RequestMapping("/mp/ext/sample")`）、`ExtOrganoidController`（`/mp/ext/organoid`）、各 `Ext*Vo`、`ExtSampleSubmitBo`、`ExtOrganoidSubmitBo`。
- 类级 `@SaCheckRole("lqg_external")`。角色 103 **不授任何菜单与权限串**——它在 `/lqg/**`、`/mp/int/**` 上天然 403。
  （AUTH-GROUP-001 已有的 `/mp/ext/units`、`/mp/ext/profile` 若不在本包，**搬进本包**并补上同样的类级注解。）
### 2.2 ExtScopeService（唯一允许注入 Mapper 的 ext 类）
- `Set<Long> visibleSampleIds(Long userId)`：`FLOW:F-EXT-01.step1` 的口径；`doc/verify/README.md` 里有一份参考 SQL。
- `void assertVisible(Long userId, Long sampleId)`：不可见抛 `ServiceException("样本不存在", 404)`。
- `boolean isMine(...)`、`boolean editable(...)`（本人 且 状态 ∈ {pending, invalid}）。
### 2.3 接口（形状见 `doc/api-contract.md`）
- `GET /mp/ext/sample/list`：参数 `sampleKind?`、`verifyStatus?`、`onlyMine?`；`visibleSampleIds` → 调 sample 域 service 按 id 集合查 → 装 `ExtSampleVo`（`donorNameMasked` = 姓 + `**`；`submitterName` 取外部档案的 `real_name`；`mine`、`editable`）；按 `COALESCE(update_time, create_time)` 倒序。
- `GET /mp/ext/sample/{id}`：先 `assertVisible`；`ExtSampleDetailVo`（带 `sampleKind`、`organoidType`，两种样本共用）的 `embeds`、`docs` 本张返回空数组（AUTH-EXT-002 / 003 接）。
- `POST /mp/ext/sample`：`ExtSampleSubmitBo`（来源单位、供体姓名、性别、年龄、住院号、组织类型、有无病理、备注）→ `sample_kind='tissue'`、`submit_source='external'`、`verify_status='pending'`。
- `POST /mp/ext/organoid`：`ExtOrganoidSubmitBo`（`sourceUnitId?`、来源单位名称、类器官类型、备注）→ `sample_kind='organoid'`、`submit_source='external'`、`verify_status='pending'`；收样段全空。
- `PUT /mp/ext/sample/{id}`、`PUT /mp/ext/organoid/{id}`：`assertVisible` → 校验 `sample_kind` 与路径一致 → `SampleVerifyService.resubmitByExternal`（只改该种样本的送检段）。
- **没有** `/mp/ext/home`（首页不再有最近记录，CR-20260917-05）。
### 2.4 契约测试
- 把 `doc/verify/fixtures/java/ExtChokepointContractTest.java` **逐字节**拷到 `src/test/java/org/dromara/lqg/ext/`。

## 3 边界（明确不做）

- 不做外部的石蜡包埋送样（AUTH-EXT-002）、不做外部看文档与下载（AUTH-EXT-003）
- 不做小程序页面（SAMPLE-MP-001 / SAMPLE-MP-002）
- 不给外部任何 Excel 导出、任何统计数字、任何 -80 冻存信息
- 不做「单位管理员看本单位全部样本」——甲方明确说同单位不同组不能互看

## 4 完工报告要求

1. 六个外部身份各自的 `/mp/ext/sample/list` id 集合（一行一个），以及 extA、extB 带 `onlyMine=true` 的集合
2. `ExtSampleDetailVo` 的完整 JSON 一份（贴出来让人一眼看到里面有什么键）
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
