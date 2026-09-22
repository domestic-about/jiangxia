---
ticket: AUTH-EXT-003
track: AUTH
phase: D7
size: M
req_refs:
  - REQ-DOC-009
  - REQ-AUTH-006
  - REQ-QC-011
depends_on:
  - DOC-PUBLISH-001
  - AUTH-EXT-002
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/**
adr_refs:
  - ADR-0004
  - ADR-0005
blueprint_refs:
  - FLOW:F-DOC-02.step1
  - FLOW:F-DOC-02.step3
  - FLOW:F-EXT-01.step2
  - FLOW:F-EXT-01.step3
  - FIELD:t_lqg_doc_file.audience
  - FIELD:t_lqg_qc_sample.doc_status
accept:
  - name: "外部文档清单钉死在 seed 上：同组可见、草稿不给、外部版没渲染成功的不给、异组按不存在返回；带 audience=internal 也拿不到内部版；链接里的对象键是外部版"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      for K in "9000001001/sample_qc" "9000001001/organoid_qc" "9000001001/organoid_score" "9000001004/sample_qc"; do bash doc/verify/api.sh --as staff POST "/lqg/doc/${K}/render?audience=external" >/dev/null; done &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null && sleep 5 &&
      python3 doc/verify/db.py --sql "SELECT doc_kind || ':' || render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND audience='external' AND file_format='docx' AND del_flag='0'" --col-set "organoid_qc:done,organoid_score:done,sample_qc:done" &&
      bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100' | jq -e '([.rows[] | "\(.sampleId|tostring):\(.docKind)"] | sort) == ["9000001001:organoid_qc","9000001001:organoid_score","9000001001:sample_qc","9000001004:sample_qc"] and ([.rows[]|select(.docKind=="organoid_score")|.totalScore]==[85]) and ([.rows[]|keys[]]|unique|index("internalNo")==null)' &&
      bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100' | jq -e '.rows==[]' &&
      bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '[.rows[].docKind]==["sample_qc","organoid_qc","organoid_score"]' &&
      bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '.rows==[]' &&
      bash doc/verify/api.sh --as extC GET /mp/ext/doc/9000001001/organoid_score/pages | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001004/organoid_qc/pages | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001001/sample_qc/pages | jq -e '.code==200' &&
      URL="$(bash doc/verify/api.sh --as extA GET '/mp/ext/doc/9000001001/organoid_score/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      printf '%s' "${URL}" | grep -q '/external/' && ! printf '%s' "${URL}" | grep -q '/internal/' &&
      curl -sSf -o /tmp/lqg-ext-score.pdf "${URL}" && pdftotext /tmp/lqg-ext-score.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001001 | jq -e '[.data.docs[].docKind]==["sample_qc","organoid_qc","organoid_score"]'
    counterfeit: |-
      清单只按「已完成」过滤、不看外部版渲染成没成 → 会多出 1001 的 sample_qc 与 organoid_qc（seed 里它俩的图片是假地址，外部版渲染必然失败——第 4 段先确认这个病灶真的在），集合红。送检方点进去是一片空白。
      清单把 1004 的类器官质控表草稿也给了 → 红。
      pages / download 只校验了样本可见、没校验文档状态 → extA 取 1004 草稿拿到 200 红。
      audience 从请求参数里读 → 带 audience=internal 的下载链接里出现 /internal/ 红：外部拿到了带内部编号的那一份。
      异组用户猜 id → 必须 404。
      sampleId 过滤写在可见范围之前、或直接按 sampleId 查 → extC 带 sampleId=1001 拿到了评分表红：预览页的切换条成了越权口子。
      最后用 curl -f 真的把文件下下来、抽出文字，证明链接可用且内容是评分表，而不是只看接口返回了一个字符串。
  - name: "四条结构性不变量在加了文档接口之后仍然成立；新增的 VO 不带内部专用字段"
    form: STATE
    run: |-
      cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true) &&
      test -f code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/controller/ExtDocController.java &&
      ! grep -nE 'publishedBy|errorMsg|contentHash|internalNo' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/domain/vo/ExtDocVo.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/domain/vo/ExtDocPagesVo.java &&
      bash doc/verify/api.sh --as extA --bizcode GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | grep -qE '^403'
    counterfeit: |-
      ExtDocController 为了省事直接返回内部的 DocPagesVo → I2 红。
      ExtDocVo 带了 publishedBy（完成人）→ I3 / grep 红。
      外部角色能直接打工作台的文档接口 → 最后一段不是 403 红：那条路上 audience 是可以传 internal 的。
---

# AUTH-EXT-003 · 外部看文档：列表、页面图片、下载链接全部过咽喉；只给已完成且外部版渲染成功的；越权回归

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/AUTH` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-PUBLISH-001**、**AUTH-EXT-002**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0004**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0004` 取结构化口径）
  - **ADR-0005**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0005` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. 外部能拿到的文档 = 可见样本 ∩ `doc_status='published'` ∩ **外部版**渲染 `done`。三个条件缺一个都不给。
  2. `audience` 在外部接口里**写死 external**，不接受参数——请求里带 `audience=internal` 要么被忽略要么被拒，绝不能生效。
  3. 文件链接是后端按权限签发的 10 分钟签名链接；签发前再核一遍对象键里的 audience 段是 `external`（OSS 键约定见 DOC-RENDER-001）。
  4. 不可见 / 未完成 / 未渲染成功 → 一律按「不存在」返回 404。
  5. 三种文档（样本质控表、类器官质控表、类器官质量评分表）都给外部，不按类型挑——甲方 2026-09-17 专门强调「要让他们看到三个 word」；挡住的只能是「不可见」「未完成」「外部版没生成成功」。

## 1 背景与口径

合同附件第 4 行：外部人员只能查看、下载与自己样本相关的内容。会上 L370：外部要能查看他们送的样本对应的这三个表。微信答复：质控结论、评分都能看。

## 2 实现要点

- ext 包新增 `ExtDocController`（`/mp/ext/doc`）、`ExtDocVo`、`ExtDocPagesVo`；走 `ExtScopeService.assertVisible` + doc 域 service（不碰 Mapper）。
- `GET /mp/ext/doc/list`：参数 `publishedBegin/End`、`docKind`、`sampleId`（先过可见范围、再按样本过滤——不可见样本的 sampleId 返回空列表；预览页顶部三份切换用，CR-20260917-04）；行 = `ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}`（`totalScore` 只在评分表行上有）。
- `GET /mp/ext/doc/{sampleId}/{docKind}/pages`、`…/download?format=docx|pdf`：`docKind` 可为三种之一或 `merged`（merged = 该样本**已完成且外部版成功**的几份）。
- `ExtSampleDetailVo.docs` 接上同一份查询。
- 契约测试文件不动。

## 3 边界（明确不做）

- 外部不能看内部版、不能看草稿、不能看渲染失败的原因
- 不做文档分享给第三人的链接
- 小程序页面在 DOC-MP-001 / 002

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
