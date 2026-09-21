---
ticket: DOC-RENDER-001
track: DOC
phase: D6
size: L
req_refs:
  - REQ-DOC-004
  - REQ-DOC-011
  - REQ-QC-002
  - REQ-QC-004
  - REQ-QC-008
depends_on:
  - QC-MODEL-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/render/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/render/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260926140*__DOC-RENDER-001-*.sql
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml
adr_refs:
  - ADR-0005
  - ADR-0004
blueprint_refs:
  - FLOW:F-DOC-01.step1
  - FLOW:F-DOC-01.step2
  - FIELD:t_lqg_doc_file.content_hash
  - FIELD:t_lqg_doc_file.audience
  - FIELD:t_lqg_doc_file.template_version
  - FIELD:t_lqg_doc_file.render_status
accept:
  - name: "渲染出的 Word：带出字段与填写字段都在、两句印死的注逐字保留、没有残留占位符、图片真的嵌进去了；外部版里找不到内部编号而内部版里有"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-int.docx /tmp/lqg-ext.docx /tmp/lqg-score.docx &&
      OSS="$(bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" && test -n "${OSS}" && test "${OSS}" != null &&
      bash doc/verify/api.sh --as staff GET /lqg/qc/9000001005 >/dev/null &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/sample-qc '{"patientNo":"P-RENDER","samplingSite":"胃窦","samplingMethod":"活检","clinicalDiagnosis":"渲染探针诊断","origDesc":"探针描述甲"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"orig\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"observe\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
      for A in internal external; do bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001005/sample_qc/render?audience=${A}" | jq -e '.code==200 and .data.status=="done"' || exit 1; done &&
      curl -sSf -o /tmp/lqg-int.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=docx&audience=internal' | jq -r '.data.url')" &&
      curl -sSf -o /tmp/lqg-ext.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=docx&audience=external' | jq -r '.data.url')" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-int.docx --no-placeholder --min-images 2 --contains "T-hga03" --contains "测试供体戊" --contains "A 医院" --contains "P-RENDER" --contains "渲染探针诊断" --contains "探针描述甲" --contains "样本按质控要求，保持2-8℃低温环境运输至实验室。" --contains "注：合格，活率≥70%；基本合格，50%~70%；不合格，＜50%或活细胞＜1x104。" --not-contains "要求图片可以放大" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-ext.docx --no-placeholder --min-images 2 --contains "测试供体戊" --contains "P-RENDER" --not-contains "T-hga03" &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      curl -sSf -o /tmp/lqg-score.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/download?format=docx&audience=internal' | jq -r '.data.url')" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-score.docx --no-placeholder --contains "85" --contains "30~100μm" --contains "注：类器官质量评分≤50表示类器官质量偏差，药敏实验失败风险较大；50~75表示类器官质量中等；≥75表示类器官质量良好。" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      模板是照着重新画的、页脚那句注打错了一个符号（「＜」写成「<」、「1x104」写成「1×10⁴」）→ 逐字比对红。
      外部版只是下载时换了个文件名、内容同内部版 → 外部版里搜到 T-hga03 红。
      从样本主档带出的字段没接（患者姓名一格空着）→ 缺「测试供体戊」红。
      图片位占位符没处理好、图没进去 → min-images 红；模板里的说明文字「要求图片可以放大」还留着 → not-contains 红。
      有字段没填上、占位符原样印了出来 → no-placeholder 红。
      读到上一次的旧文件：开头先 rm，下载用 curl -f（签名链接失效或 404 直接失败）。
  - name: "渲染产物表与 SSOT 相符；指纹缓存成立：内容没变不重出、改了任何一处都重出、内外部各一份互不覆盖"
    form: STATE
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_doc_file --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      r() { bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001001/organoid_qc/render?audience=$1" | jq -e '.data.status=="done"' >/dev/null; } &&
      snap() { python3 doc/verify/db.py --quiet --sql "SELECT audience || ':' || content_hash || ':' || oss_id || ':' || rendered_time FROM t_lqg_doc_file WHERE sample_id=9000001001 AND doc_kind='organoid_qc' AND file_format='docx' AND del_flag='0' ORDER BY audience" | tr '\n' ','; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null && r internal && r external && S1="$(snap)" && r internal && r external && S2="$(snap)" && test "${S1}" = "${S2}" &&
      test "$(printf '%s' "${S1}" | tr ',' '\n' | grep -c .)" = 2 &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001001/organoid-qc '{"growthState":"指纹探针：已改"}' | jq -e '.code==200' &&
      r internal && S3="$(snap)" && test "${S1}" != "${S3}" &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocFingerprintTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      每次请求都重新渲染（没有缓存）→ S1 与 S2 的 rendered_time / oss_id 不同红。渲染要几秒，送检方每点一次预览都等几秒。
      缓存了但指纹只算了文档自己的字段 → 改了内容之后 S3 仍等于 S1 红；`DocFingerprintTest` 里「只改样本主档的来源单位」「只换一张图」「只升模板版本」三条同理。
      内外部版共用一行缓存 → 行数不是 2 红：谁后渲染谁覆盖，外部可能拿到带内部编号的那份。
---

# DOC-RENDER-001 · Word 渲染：三份 docx 模板（由甲方模板改占位符）、poi-tl 渲染服务、内部版与外部版、按内容指纹缓存

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**QC-MODEL-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0005**：docx 模板是唯一的版式来源；每份文档出内部版与外部版；产物按『文档内容 + 模板版本』的指纹缓存
  - **REQ-DOC-011 是 clarify**：甲方还没给定稿模板。先按 `_input/templates/` 现有三份做；甲方给了新模板 = 换模板文件 + 模板版本号加一
  - **REQ-QC-002 是 clarify**：「细胞活率测定」一格默认印附件文件名，不做 OLE 嵌入
  - **ADR-0004**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0004` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **模板从甲方原件改出来，不是照着重画**：保留原件的表格结构、列宽、字体、页脚两句「注」；只把空白格换成 poi-tl 占位符、把「要求图片可以放大」几个字换成图片占位。
  2. **外部版里内部编号一格留空**——外部版和内部版是两份独立缓存的产物，别在下载时临时抹。
     CR-20260918-07 起系统参数 `lqg.ext.show-internal-no` 能让外部在**页面上**看到内部编号，但**本张不跟着变**：外部版文档一律留空，开关切换也不重出历史文档（范围已在 CR 里写死；甲方若要文档也跟着放开，另记变更）。
  3. 指纹必须覆盖：文档全部字段 + 从样本主档带出的字段 + 图片与附件的 oss_id 序列 + 模板版本 + audience。漏了哪个，哪个改了之后就还在发旧文件。
  4. 进 Word 的图用**预览图**（≤ 2000px 的 JPEG），不用原图——一张显微 TIFF 几十 MB。

## 1 背景与口径

会上 L148-L157：预览要和 Word 的样子一模一样，「因为这个我可能后面会给客户」。合同第五条：三份 Word 的预览与导出效果以甲方确认的样张为准。
两句印死的注（REQ-QC-004 / 008）原样保留。

## 2 实现要点

- DDL：`gen_ddl_pg.py --migration V202609261400__DOC-RENDER-001-doc-file.sql` → `t_lqg_doc_file`。OSS 对象键约定：`lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前 12 位>[-p<页码>].<扩展名>`——audience 进路径，外部接口签发链接时据此再核一遍。
- 模板：`resources/lqg/doc-templates/{sample_qc,organoid_qc,organoid_score}.docx` + `template-version.txt`（从 `1` 起）。评分表：被选中的那一档在「类器官质量评分」列填分值，其余档留空；表尾加一行合计。
  图片位：poi-tl 的图片列表插件，多张并排等分单元格宽度。字体先保持模板原字体（字体替换是 DOC-PDF-001 的事）。
- `DocRenderService.render(sampleId, docKind, audience)`：算指纹 → 命中 `done` 且指纹一致 → 直接返回；否则置 `pending` → 渲染 docx → 传 OSS（私有）→ `done`。失败 → `failed` + `error_msg`。
  `merged`：按「样本质控表 → 类器官质控表 → 类器官质量评分表」顺序拼接**已完成**的几份（每份另起一页）；本张里 published 的判断直接读 `doc_status`。
- `POST /lqg/doc/{sampleId}/{docKind}/render?audience=`、`GET …/download?format=docx&audience=`（返回 10 分钟签名链接 + 文件名 = 文档名 + 内部编号 / 外部版用送检单号）。
- 单测：指纹对每一类输入变化都敏感（逐项改一个值，指纹必须变）。

## 3 边界（明确不做）

- 不转 PDF、不出页面图（DOC-PDF-001）
- 不做完成 / 撤回状态机与对外可见（DOC-PUBLISH-001 / AUTH-EXT-003）
- 不做在线编辑模板、不做模板管理界面
- 不做 OLE 嵌入附件

## 4 完工报告要求

1. 三份渲染出的 Word（内部版）附在报告里；与甲方模板原件并排的截图各一张
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
