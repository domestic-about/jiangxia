---
ticket: OCR-IMPL-001
track: OCR
phase: D5
size: M
req_refs:
  - REQ-OCR-002
  - REQ-OCR-003
  - REQ-OCR-005
depends_on:
  - AUTH-LOGIN-001
  - AUTH-GROUP-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ocr/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ocr/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application*.yml
adr_refs:
  - ADR-0007
blueprint_refs:
  - FLOW:F-OCR-01.step2
  - FLOW:F-OCR-01.step3
  - FIELD:t_lqg_source_unit.unit_name
accept:
  - name: "五个解析用例逐例过：该出现的字段相等、不该出现的字段不出现（噪声里的数字不当年龄、单位名不硬匹配）；识别前后业务表与文件表一行不多"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      BEFORE="$(python3 doc/verify/db.py --quiet --sql "SELECT (SELECT count(*) FROM t_lqg_sample) + (SELECT count(*) FROM sys_oss)" | head -1)" &&
      bash doc/verify/api.sh --as extA --fresh-module ruoyi-lqg GET /mp/me >/dev/null &&
      for N in 0 1 2 3 4; do
        CASE="$(jq -c ".cases[${N}]" doc/verify/fixtures/ocr-cases.json)" &&
        RESP="$(bash doc/verify/api.sh --as extA --header "X-Ocr-Stub-Case: $(printf '%s' "${CASE}" | jq -r '.case[0:2]')" --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /mp/ocr/recognize)" &&
        printf '%s' "${RESP}" | jq -e --argjson c "${CASE}" '. as $r | $r.code==200 and ($r.data.rawLines == $c.rawLines) and ([$c.expect | to_entries[] | ($r.data.fields[.key] == .value)] | all) and ([$c.absent[] | . as $k | ($r.data.fields | has($k) | not)] | all)' || exit 1
      done &&
      AFTER="$(python3 doc/verify/db.py --quiet --sql "SELECT (SELECT count(*) FROM t_lqg_sample) + (SELECT count(*) FROM sys_oss)" | head -1)" &&
      test "${BEFORE}" = "${AFTER}"
    counterfeit: |-
      年龄用「找到的第一个 1-3 位数字」→ 第 03 例把 2-8℃ 里的 2 或 8 当成年龄，absent 断言红。
      住院号用「最长的一串数字」→ 第 03 例的日期 20260917 被当成住院号红。
      单位名做了相似度匹配 → 第 05 例「某某市第九医院」被硬凑成 A 医院红。
      解析不出的字段返回空串而不是不返回 → has(key) 为真，absent 红；前端会拿空串去「预填」。
      为了方便排查把上传的图存进了 OSS → 前后计数不等红：那是多留了一份供体信息。
  - name: "付费通道默认关且 prod 配置里不出现这个开关；没配 provider 时明确报「请手动填写」而不是 500；限流生效"
    form: STATE
    run: |-
      bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET /lqg/ocr/status | jq -e '.code==200 and .data.paidEnabled==false' &&
      ! grep -rnE 'paid-enabled|provider:[[:space:]]*stub' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='OcrFieldParserFixtureTest,OcrProviderSelectionTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      grep -q 'ocr-cases.json' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ocr/OcrFieldParserFixtureTest.java &&
      for i in 1 2 3 4 5 6 7 8; do bash doc/verify/api.sh --as extB --bizcode --header 'X-Ocr-Stub-Case: 04' --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /mp/ocr/recognize; done | awk -F'\t' '$1!="200"{n++} END{exit !(n>=2)}'
    counterfeit: |-
      付费 provider 只要配了 key 就生效、没有独立开关 → `OcrProviderSelectionTest` 里「配了付费 provider 但 paid-enabled=false → 选不中」那条红。
      把 paid-enabled: false 显式写进 prod 配置 → 第 2 段红：这个键出现在 prod 文件里，就离被改成 true 只差一次手滑。
      没配 provider 时抛空指针 → 小程序上弹「系统异常」而不是「请手动填写」；单测里要有 NoneOcrProvider 的那条。
      没限流 → 连打 8 次全是 200，最后一段红（会被当成免费 OCR 刷）。
---

# OCR-IMPL-001 · 识别后端：统一接口、可插拔的识别适配层、字段解析规则、限流、付费通道默认关

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/OCR` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-LOGIN-001**、**AUTH-GROUP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0007**：识别走后端统一接口；一个方案一个 `OcrProvider` 实现类，配置切换；只返回给前端预填，不落业务表，原图识别完即弃；按次收费的 provider 默认关
  - `doc/verify/fixtures/ocr-cases.json`：解析规则的验收用例——**宁缺毋滥，解析不出就不返回，绝不猜**
- [ ] 口径复述（本张最容易做反的）：
  1. **本张不依赖 OCR-SPIKE-001 的结论**：适配层和解析规则现在就能做；真实 provider 等探路结论出来再加一个实现类。
  2. **识别接口不写任何业务表、不存图**。图片只在内存或临时文件里，识别完即删，不进 OSS、不进 sys_oss。
  3. 噪声里的数字（`2-8℃`、日期、床号）不得被当成年龄或住院号——fixture 第 03 例就是为这个埋的。
  4. `sourceUnitName` 只在与单位表里 **active** 单位名**完全一致**时才返回，不做模糊匹配。

## 1 背景与口径

会上 L70：管壁、袋子上有患者信息，希望抓取识别节省时间。方案 v6：识别结果只做预填，填写人核对后提交。
合同第二条第 5 款：按次收费的第三方服务须甲方书面同意才接入。

## 2 实现要点

- `POST /mp/ocr/recognize`（内外部都能调；multipart `file`，≤ 5MB，只收 jpg / png）→ `{rawLines, fields}`，形状见 `doc/api-contract.md`。
- `OcrProvider` 接口：`List<String> recognize(byte[] image)`；实现：
  - `NoneOcrProvider`（缺省）：直接抛「识别暂不可用，请手动填写」——没配 provider 时小程序照常能手填。
  - `StubOcrProvider`（仅 dev / test）：按请求头 `X-Ocr-Stub-Case` 的前两位，从 classpath 里的 `ocr-cases.json` 副本（构建时从 `doc/verify/fixtures/` 拷）取 `rawLines`。prod 下配置成 stub → 拒绝启动（同 mock 登录护栏的思路）。
  - 真实 provider：等 OCR-SPIKE-001 的结论，另起一个实现类；**按次收费的实现必须同时满足 `lqg.ocr.paid-enabled=true` 才会被选中**，该键缺省 false 且不出现在 prod 配置文件里。
- `OcrFieldParser`（纯函数，与 provider 无关）：标签词表（姓名 / 患者姓名、性别、年龄、住院号 / 住院号码、标本 / 组织类型）+ 全半角冒号与空格归一 + 值的合法性（年龄 1-120 的整数、性别 男 / 女、住院号 5-20 位字母数字）。
  没有标签的裸数字一律不认。单位名：整行与 active 单位名完全相等才认。
- 限流：`@RateLimiter`（框架自带），每用户每分钟 6 次，超了返回明确的 msg。
- `GET /lqg/ocr/status`（工作台）：`{provider, paidEnabled}`。
- 单测：`OcrFieldParserFixtureTest` 逐例跑 `ocr-cases.json`（expect 必须相等、absent 必须不出现）。

## 3 边界（明确不做）

- 不做小程序端（OCR-MP-001）
- 不在本张里接任何真实的识别服务（等探路结论）
- 不做识别记录表、不做识别历史
- 不接海外服务

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
