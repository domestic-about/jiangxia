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
  - FLOW:F-DOC-01.step2
  - FLOW:F-DOC-02.step1
  - FLOW:F-DOC-02.step2
  - FLOW:F-DOC-02.step3
  - FLOW:F-EXT-01.step2
  - FLOW:F-EXT-01.step3
  - UI:mp.doc.preview
  - FIELD:t_lqg_doc_file.audience
  - FIELD:t_lqg_qc_sample.doc_status
accept:
  - name: "外部文档清单钉死在 seed 上：同组可见、草稿不给、外部版没渲染成功的不给（seed 里 1001 的样本质控表、类器官质控表挂着假地址图 → 外部版 failed、不上清单、预览 404；换上真图重新完成后恢复）、异组按不存在返回；带 audience=internal 也拿不到内部版；链接里的对象键是外部版"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      for K in "9000001001/sample_qc" "9000001001/organoid_qc" "9000001001/organoid_score" "9000001004/sample_qc"; do bash doc/verify/api.sh --as staff POST "/lqg/doc/${K}/render?audience=external" >/dev/null; done &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null && sleep 5 &&
      python3 doc/verify/db.py --sql "SELECT doc_kind || ':' || render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND audience='external' AND file_format='docx' AND del_flag='0'" --col-set "organoid_qc:failed,organoid_score:done,sample_qc:failed" &&
      bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100' | jq -e '([.rows[] | "\(.sampleId|tostring):\(.docKind)"] | sort) == ["9000001001:organoid_score","9000001004:sample_qc"] and ([.rows[]|select(.docKind=="organoid_score")|.totalScore]==[85]) and ([.rows[]|keys[]]|unique|index("internalNo")==null)' &&
      bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100' | jq -e '.rows==[]' &&
      bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '[.rows[].docKind]==["organoid_score"]' &&
      bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '.rows==[]' &&
      bash doc/verify/api.sh --as extC GET /mp/ext/doc/9000001001/organoid_score/pages | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001004/organoid_qc/pages | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001001/sample_qc/pages | jq -e '.code==404' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001004/sample_qc/pages | jq -e '.code==200 and (.data.pages|length)>=1' &&
      URL="$(bash doc/verify/api.sh --as extA GET '/mp/ext/doc/9000001001/organoid_score/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      printf '%s' "${URL}" | grep -q '/external/' && ! printf '%s' "${URL}" | grep -q '/internal/' &&
      curl -sSf -o /tmp/lqg-ext-score.pdf "${URL}" && pdftotext /tmp/lqg-ext-score.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001001 | jq -e '[.data.docs[].docKind]==["organoid_score"]' &&
      OSS="$(bash doc/verify/api.sh --as staff --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" && test -n "${OSS}" &&
      for ID in $(python3 doc/verify/db.py --quiet --sql "SELECT i.id FROM t_lqg_doc_image i JOIN t_lqg_qc_sample q ON q.id = i.doc_id WHERE i.doc_type='sample_qc' AND q.sample_id=9000001001 AND i.del_flag='0'"); do bash doc/verify/api.sh --as staff DELETE "/lqg/qc/9000001001/sample-qc/image/${ID}" | jq -e '.code==200' >/dev/null || exit 1; done &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/sample-qc/image "{\"slot\":\"orig\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/sample-qc/publish | jq -e '.code==200' && sleep 15 &&
      bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '[.rows[].docKind]|index("sample_qc")!=null' &&
      bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001001/sample_qc/pages | jq -e '.code==200 and (.data.images|length)>=1' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      清单只按「已完成」过滤、不看外部版渲染成没成 → 集合红：seed 里 1001 的样本质控表、类器官质控表挂着假地址图，外部版按 CR-20260923-09 整份 failed，却仍出现在清单里（期望只有 1001 评分表与 1004 样本质控表）；带 sampleId=1001 的切换条、样本详情的 docs 同理只该剩评分表。送检方点进去是一片空白。
      渲染时把取不到的图静默跳过、外部版照判 done（CR-20260923-09 之前的做法）→ 第 4 段 col-set 里 sample_qc / organoid_qc 不是 failed 红；extA 预览 1001 样本质控表拿到 200 而不是 404 红：送检方会拿到一份图位空着的文档。
      外部版一旦 failed 就再也回不来（按样本拉黑、补图后不重出、清单缓存了旧结果）→ 末尾那组红：把 1001 样本质控表的假图删掉、换成 fixture 真图、重新「完成并同步」，15 秒后 sample_qc 必须重新出现在带 sampleId=1001 的清单里、extA 预览 200 且带原图。这一组同时证明前面那几段红的原因是「图取不到」，而不是「1001 的这两份被写死不给」。
      清单把 1004 的类器官质控表草稿也给了 → 红。
      pages / download 只校验了样本可见、没校验文档状态 → extA 取 1004 草稿拿到 200 红。
      audience 从请求参数里读 → 带 audience=internal 的下载链接里出现 /internal/ 红：外部拿到了带内部编号的那一份。
      异组用户猜 id → 必须 404。
      sampleId 过滤写在可见范围之前、或直接按 sampleId 查 → extC 带 sampleId=1001 拿到了评分表红：预览页的切换条成了越权口子。
      最后用 curl -f 真的把文件下下来、抽出文字，证明链接可用且内容是评分表，而不是只看接口返回了一个字符串。
      （2026-09-23 按 CR-20260923-09 更正：撤回 2026-09-22 那段援引「issue #217 的裁定」、说假地址图「跳过 + WARN、文档照出 done」的更正——那个裁定查实不存在（见 CR-20260923-09 背景）。现行口径以 CR-20260923-09 与 FLOW:F-DOC-01.step2 为准：外部版有任一张图取不到即整份 failed、不对外，补图后重新生成恢复；内部版照出并记缺图。）
  - name: "四条结构性不变量在加了文档接口之后仍然成立；新增的 VO 不带内部专用字段，外部预览的形状恰好是 {docKind, status, pages, images, attachments}"
    form: STATE
    run: |-
      cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='ExtChokepointContractTest,ExtDocShapeContractTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      test -f code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/controller/ExtDocController.java &&
      ! grep -nE 'publishedBy|errorMsg|contentHash|internalNo' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/domain/vo/ExtDocVo.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/domain/vo/ExtDocPagesVo.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/domain/vo/ExtDocImageVo.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/domain/vo/ExtDocAttachmentVo.java &&
      bash doc/verify/api.sh --as extA --bizcode GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | grep -qE '^403'
    counterfeit: |-
      ExtDocController 为了省事直接返回内部的 DocPagesVo → I2 红。
      ExtDocVo 带了 publishedBy（完成人）→ I3 / grep 红。
      外部预览照抄内部 DocPagesVo 的字段、带出 errorMsg / contentHash → grep 红；带出 missingImageCount / missingImages、或 ExtDocAttachmentVo 带了 ossId → ExtDocShapeContractTest 的白名单红（失败原因、指纹、缺图明细、对象 id 都不对外）。
      外部预览退回只给页面图、少了 images / attachments（#254 时的旧形状，CR-20260923-09 已作废）→ 白名单（恰好 docKind、status、pages、images、attachments 五键）红。
      外部角色能直接打工作台的文档接口 → 最后一段不是 403 红：那条路上 audience 是可以传 internal 的。
  - name: "外部预览给原图与附件（含活率附件），和页面图走同一个咽喉：签发前逐个核对对象只属于本样本外部版——误挂成附件的内部版 docx 不出现在外部附件里、外部链接里没有 /internal/；异组按不存在 404"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/qc/9000001005 >/dev/null &&
      IMG="$(bash doc/verify/api.sh --as staff --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" &&
      VIA="$(bash doc/verify/api.sh --as staff --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/sample-qc "{\"viabilityOssId\":${VIA},\"viabilityFileName\":\"活率报告-探针.png\"}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"orig\",\"ossId\":${IMG}}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/publish | jq -e '.code==200' && sleep 15 &&
      P="$(bash doc/verify/api.sh --as extC GET /mp/ext/doc/9000001005/sample_qc/pages)" &&
      printf '%s' "${P}" | jq -e '(.data.images|length)==1 and (.data.attachments|map(.fileName)|index("活率报告-探针.png"))!=null' &&
      printf '%s' "${P}" | jq -r '.data.images[0].url, .data.attachments[0].url' | while read -r U; do curl -sSf -o /dev/null "${U}" || exit 1; done &&
      INT="$(python3 doc/verify/db.py --quiet --sql "SELECT oss_id FROM t_lqg_doc_file WHERE sample_id=9000001005 AND doc_kind='sample_qc' AND audience='internal' AND file_format='docx' AND page_no=0 AND del_flag='0'")" &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/attachment "{\"ossId\":${INT},\"fileName\":\"误挂的内部版.docx\"}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/publish | jq -e '.code==200' && sleep 15 &&
      bash doc/verify/api.sh --as extC GET /mp/ext/doc/9000001005/sample_qc/pages | jq -e '(.data.attachments|map(.fileName)|index("误挂的内部版.docx"))==null and ([.data.attachments[].url]|map(test("/internal/"))|any|not)' &&
      bash doc/verify/api.sh --as extA --bizcode GET /mp/ext/doc/9000001005/sample_qc/pages | grep -qE '^404' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      外部预览还是只给页面图（#254 时的旧形状）→ images 长度不是 1、附件里找不到「活率报告-探针.png」，红：送检方看不到显微照片原图、也打不开活率报告，UI:mp.doc.preview 与 FLOW:F-DOC-02.step2 要的「文档中的图片」「附件」两段是空的（CR-20260923-09）。
      活率附件没并进附件列表（它在表上是单独一栏 viability_oss_id）→ 同上红：文档里有这个附件（CR-20260924-11 起 Word 里嵌着它、页面图上是图标 + 文件名），预览页的附件栏里却打不开。
      签了链接但签错了对象、或签出来打不开 → curl -f 那段红：接口说有图，点开 404。
      签发前不核对对象、附件表里挂的是谁就签谁 → 把内部版 docx 的 oss_id 误挂成样本质控表的附件、重新完成后，外部附件里出现「误挂的内部版.docx」或出现 /internal/ 链接，红：外部拿到了带内部编号的那一份。这正是 V24 要堵的口子——图片与附件和页面图走同一个咽喉，指向渲染产物目录的对象只许是本样本的外部版。
      图片与附件另走了一条没过可见范围的查询 → extA（看不到 1005）拿到 200 而不是 404，最后一段红。
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
  1. 外部能拿到的文档 = 可见样本 ∩ `doc_status='published'` ∩ **外部版**渲染 `done`。三个条件缺一个都不给。外部版有任一张图取不到即整份 `failed`（CR-20260923-09，以 `FLOW:F-DOC-01.step2` 为准），同样不给；图补上、重新生成后恢复。
     CR-20260924-10 起多一个条件：外部版「内部编号」一格随系统参数 `lqg.ext.show-internal-no`（开印、关留空），**这一版印了内部编号而开关此刻已关的，清单不列、pages / download 按不存在 404**，后台按新设置重出后恢复（判据在 doc 域 `DocRenderService#delivery`，外部清单先过 `DocAvailabilityService#available`，ext 包不另判）。seed 与本张 accept 全程开关为 false，产物都按「留空」出，断言不受影响。
  2. `audience` 在外部接口里**写死 external**，不接受参数——请求里带 `audience=internal` 要么被忽略要么被拒，绝不能生效。
  3. 文件链接是后端按权限签发的 10 分钟签名链接；签发前再核一遍对象键里的 audience 段是 `external`（OSS 键约定见 DOC-RENDER-001）。
  4. 不可见 / 未完成 / 未渲染成功 → 一律按「不存在」返回 404。
  5. 三种文档（样本质控表、类器官质控表、类器官质量评分表）都给外部，不按类型挑——甲方 2026-09-17 专门强调「要让他们看到三个 word」；挡住的只能是「不可见」「未完成」「外部版没生成成功」。
  6. **外部预览按 `UI:mp.doc.preview` 与 `FLOW:F-DOC-02.step2` 给原图与附件（活率附件排最前）**（CR-20260923-09，「外部只给页面图」的做法作废）：图片与附件和页面图走同一个咽喉——样本可见、这份文档对外可用，签发前再逐个核对对象只属于本样本的外部版（误挂的内部版文件、别的样本的对象一律不签）；外部这条不给失败原因、指纹与缺图明细。

## 1 背景与口径

合同附件第 4 行：外部人员只能查看、下载与自己样本相关的内容。会上 L370：外部要能查看他们送的样本对应的这三个表。微信答复：质控结论、评分都能看。

## 2 实现要点

- ext 包新增 `ExtDocController`（`/mp/ext/doc`）、`ExtDocVo`、`ExtDocPagesVo`；走 `ExtScopeService.assertVisible` + doc 域 service（不碰 Mapper）。
- `GET /mp/ext/doc/list`：参数 `publishedBegin/End`、`docKind`、`sampleId`（先过可见范围、再按样本过滤——不可见样本的 sampleId 返回空列表；预览页顶部三份切换用，CR-20260917-04）；行 = `ExtDocVo{sampleId, submitNo, donorNameMasked, docKind, publishedTime, totalScore?}`（`totalScore` 只在评分表行上有）。
- `GET /mp/ext/doc/{sampleId}/{docKind}/pages`、`…/download?format=docx|pdf`：`docKind` 可为三种之一或 `merged`（merged = 该样本**已完成且外部版成功**的几份）。
  `pages` 返回 `ExtDocPagesVo{docKind, status, pages:[{pageNo, url}], images:[{url, previewUrl}], attachments:[{fileName, fileSize, url}]}`：`status` 恒为 `done`（没渲染成功的到不了这里）；`images` 的 `url` 是原图、`previewUrl` 是缩略；`attachments` 里样本质控表的细胞活率测定附件排最前；全是 10 分钟签名链接，签发前逐个核对对象（CR-20260923-09，独立验收 V24）。
- `ExtSampleDetailVo.docs` 接上同一份查询。
- 契约测试文件不动。

## 3 边界（明确不做）

- 外部不能看内部版、不能看草稿、不能看渲染失败的原因与缺图明细
- 不做文档分享给第三人的链接
- 小程序页面在 DOC-MP-001 / 002

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 1 改为「seed 假图 → 外部版 failed、不上清单、预览 404，换真图重新完成后恢复」，撤回援引不存在的 #217 裁定的那段更正；新增 accept 3（V24：外部预览给原图与附件、签发前核对对象只属于本样本外部版）；外部 pages 形状改为 {docKind, status, pages, images, attachments}，accept 2 加跑 ExtDocShapeContractTest 钉住这五键白名单、黑名单 grep 扩到两个新 VO。
- 2026-09-24 按 CR-20260924-10 更新：§0 口径 1 补「外部版印了内部编号而开关已关的一版不列、不签发」这一条件（开关管到外部版文档）；accept 不动（全程开关为 false，已逐条核过）。
- 2026-09-24 按 CR-20260924-11 更新：accept 反例里「文档正文里印了文件名」改为嵌入口径（Word 里嵌着附件、页面图上是图标 + 文件名）；run 不动（H 批已在隔离环境重放 3/3 绿）。
