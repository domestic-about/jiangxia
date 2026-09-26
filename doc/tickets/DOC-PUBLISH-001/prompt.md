---
ticket: DOC-PUBLISH-001
track: DOC
phase: D7
size: M
req_refs:
  - REQ-QC-011
  - REQ-DOC-008
  - REQ-DOC-006
  - REQ-DOC-007
depends_on:
  - DOC-PDF-001
  - QC-WEB-002
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/publish/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/publish/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/qc/**
  - code/plus-ui/src/views/lqg/qc/components/PreviewPane.vue
  - code/plus-ui/src/views/lqg/qc/editor/index.vue
  - code/plus-ui/src/api/lqg/doc/**
adr_refs:
  - ADR-0005
blueprint_refs:
  - FLOW:F-QC-01.step5
  - FLOW:F-QC-01.step6
  - FLOW:F-QC-01.step7
  - FLOW:F-DOC-01.step5
  - UI:admin.doc.preview
  - FIELD:t_lqg_qc_sample.doc_status
  - FIELD:t_lqg_qc_sample.published_time
accept:
  - name: "两态状态机：重复完成、对草稿撤回被拒；完成后记下完成人与时间并触发内外部版渲染；改内容、加图片、改评分任何一种都让已完成的文档回到草稿"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      st() { python3 doc/verify/db.py --quiet --sql "SELECT doc_status || '|' || COALESCE(published_by::text,'-') || '|' || (published_time IS NOT NULL) FROM $1 WHERE sample_id=$2 AND del_flag='0'" | head -1; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/qc/9000001006 >/dev/null &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001006/sample-qc/unpublish | grep -qE '^(400|500)' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001006/sample-qc/publish | jq -e '.code==200' &&
      test "$(st t_lqg_qc_sample 9000001006)" = "published|9000000101|true" &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001006/sample-qc/publish | grep -qE '^(400|500)' &&
      sleep 20 && python3 doc/verify/db.py --sql "SELECT audience || ':' || file_format FROM t_lqg_doc_file WHERE sample_id=9000001006 AND doc_kind='sample_qc' AND render_status='done' AND page_no IN (0,1) AND del_flag='0'" --col-set "internal:docx,internal:pdf,internal:png,external:docx,external:pdf,external:png" &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/sample-qc '{"samplingSite":"改了一个字"}' | jq -e '.code==200' &&
      test "$(st t_lqg_qc_sample 9000001006)" = "draft|-|false" &&
      test "$(st t_lqg_qc_score 9000001006)" = "published|9000000101|true" &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/score '{"preCultureLevel":"gt80","cultureDaysLevel":"le14","organoidCountLevel":"lt100","diameterLevel":"lt30"}' | jq -e '.code==200' &&
      test "$(st t_lqg_qc_score 9000001006)" = "draft|-|false" &&
      test "$(st t_lqg_qc_organoid 9000001001)" = "published|9000000101|true" &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/organoid-qc/image '{"slot":"organoid_observe","ossId":9000004005}' | jq -e '.code==200' &&
      test "$(st t_lqg_qc_organoid 9000001001)" = "draft|-|false" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      「改内容回草稿」只接在了三个 PUT 上、漏了图片与附件的增删 → 最后一组（加一张图）状态仍是 published 红。送检方会看到一份和预览图对不上的文档。
      钩子写在 controller 里、评分接口忘了调 → 评分那组红。
      完成只改了状态、没触发外部版渲染 → 等 20 秒后 doc_file 里没有 external 的三种产物，集合红。
      重复完成不拒绝（每点一次重置一次完成时间）→ 第 5 段红；文档列表按完成时间排序会乱跳。
      状态断言全部直连库读，不信接口自报。
  - name: "前端构建是本次产物；预览面板接入编辑页，有内外部版切换、**真 DOM 恰好四个下载入口且逐个点下去 format/合并位都正确**、失败态与重新生成；外部版按后端给的 internalNoShown 说明这一版「内部编号」一格印没印、outdated 时提示正在按新设置重新生成（CR-20260924-10）"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -q 'PreviewPane' src/views/lqg/qc/editor/index.vue &&
      grep -q 'audience' src/views/lqg/qc/components/PreviewPane.vue && grep -q 'merged' src/views/lqg/qc/components/PreviewPane.vue &&
      grep -qE 'failed|重新生成' src/views/lqg/qc/components/PreviewPane.vue &&
      grep -q 'internalNoShown' src/views/lqg/qc/components/PreviewPane.vue && grep -q 'outdated' src/views/lqg/qc/components/PreviewPane.vue &&
      grep -q "externalShown" src/lang/lqg/qc-ino.zh_CN.ts && grep -q "externalHidden" src/lang/lqg/qc-ino.zh_CN.ts &&
      ! grep -nE ':disabled="true"' src/views/lqg/qc/editor/index.vue &&
      cd ../.. && bash doc/waves/regression/D7/mutation-assert.sh --verify-only --hotspot H4
    counterfeit: |-
      预览面板直接 iframe 了 PDF 链接 → 和小程序看到的页面图片不是同一份东西；要求用 pages 接口的页面图片。
      「完成并同步」按钮还留着 QC-WEB-001 的写死置灰 → 最后一段红。
      外部版的提示还写死「外部版里内部编号一格为空」（CR-20260924-10 之前的口径）、不看后端这一版到底印没印 → internalNoShown / 词条那几段红：开关打开后内部人员对照看时会被误导。（`PreviewPane.vue` 第 176 行的旧注释里还留着这句，代码不在文档组范围，注释不作判据。）
      ★ **「四个下载入口」不再由字面量 grep 判**（旧写法 `grep -cE "format.*(docx|pdf)|'docx'|'pdf'"` 只钉住文里有 docx 与 pdf 两个字面量；
      D7 r1 L2 证伪 F6：把「下载合并 PDF」整个 el-button 删掉，五段 grep 全绿，而票面 §2 明确要求四个按钮真下载）。
      现在由 `mutation-assert.sh --hotspot H4` 判：真浏览器打开 1001 的质控文档编辑页 → 点「预览」→
      在**真 DOM** 上数 `.lqg-preview-pane__downloads-row` 的按钮**恰好 4 个**、文案恰好 [下载 Word / 下载 PDF / 下载合并 Word / 下载合并 PDF]，
      并**逐个真点一次**，抓真请求断言 `format=docx|pdf` 与 `/merged/` 位都正确（分别命中 `/lqg/doc/9000001001/sample_qc/download` 与 `…/merged/download`）；
      已定义变异 = 删掉「下载合并 PDF」那一个按钮 → 必须变红，还原必须复绿。
  - name: "撤回合并件里的一份：同一请求里旧合并件就不再下发（下载 400 并说明正在重新生成）；后台按新成员重出，新合并件的 Word 与 PDF 里都没有撤回的那份；之后普通生成命中缓存，「重新生成」（force）真的重出、不空转"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/qc/9000001001 >/dev/null &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/merged/render?audience=internal' | jq -e '.data.status=="done"' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/score/unpublish | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff --bizcode GET '/lqg/doc/9000001001/merged/download?format=docx&audience=internal' | grep -qE '^400[[:space:]]+合并件正在按最新的已完成文档重新生成' &&
      for i in $(seq 1 60); do python3 doc/verify/db.py --sql "SELECT render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND doc_kind='merged' AND audience='internal' AND file_format='docx' AND page_no=0 AND del_flag='0'" --eq done >/dev/null 2>&1 && break; sleep 1; done &&
      rm -f /tmp/lqg-merged-after.docx /tmp/lqg-merged-after.pdf &&
      curl -sSf -o /tmp/lqg-merged-after.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/merged/download?format=docx&audience=internal' | jq -r '.data.url')" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-merged-after.docx --not-contains "注：类器官质量评分≤50表示类器官质量偏差" &&
      curl -sSf -o /tmp/lqg-merged-after.pdf "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/merged/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      ! pdftotext /tmp/lqg-merged-after.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/merged/pages?audience=internal' | jq -e '.data.status=="done" and (.data.pages|length)>=1' &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/merged/render?audience=internal' | jq -e '.data.cached==true' &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/merged/render?audience=internal&force=true' | jq -e '.data.cached==false and .data.status=="done"' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      撤回只改了成员的状态、合并件只改指纹不失效（V04 修之前的样子）→ 撤回后马上下载合并件仍是 200 和旧文件，那段 `^400[[:space:]]+合并件正在按最新的已完成文档重新生成` 红：送检方下载到的合并件里还有已撤回的评分表。
      失效了但没人重出（只置 pending、等人来点）→ 60 秒内合并件回不到 done，后面的下载与 pages 红：页面停在「生成中」干等。
      后台重出用的还是撤回前的成员 → 新合并件的 Word 里还有评分表那句注、PDF 里还能抽出「类器官质量评分表」，红。
      重出的 Word 与 PDF / 页面图不是同一版（只重出了一种）→ Word 与 PDF 两段一红一绿，或 pages 不是 done，红。
      「重新生成」走缓存空转 → force=true 那段 cached 不是 false，红；反过来每次都重出（没有缓存）→ 普通 render 那段 cached 不是 true，红。
      提示语以代码为准（DocRenderService 的「合并件正在按最新的已完成文档重新生成，请稍后再下载」）；状态靠直连库与真下载的文件判，不信接口自报。
---

# DOC-PUBLISH-001 · 完成并同步 / 撤回：两态状态机、改了内容自动回到草稿、工作台预览面板与下载

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-PDF-001**、**QC-WEB-002**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0005**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0005` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **已完成的文档一旦内容被改，立刻回到草稿、对外撤下**——包括增删图片、增删附件、改评分档位。送检方不该看到半成品，也不该看到和下载文件对不上的预览。
  2. 「完成并同步」之后要等**外部版渲染成功**，这份文档才算对送检方可见（可见性判断在 AUTH-EXT-003，但渲染是这里触发的）。
  3. 合法转移只有 draft→published、published→draft。对已完成的再点完成、对草稿点撤回 → 拒绝。
  4. 任何一份的状态或内容变化都让该样本的 `merged` 失效。失效 = 同一请求里置回 pending，旧产物不再下发；后台按新成员重出，一份不剩则撤下（CR-20260923-09，`FLOW:F-DOC-01.step5`）。

## 1 背景与口径

方案流程图后半段：网页编辑质控文档 → [预览无误？] → 结果同步送检方；有误重新编辑。会上 L370：外部人员要能查看他们送的样本对应的这三个表。L136：预览之后觉得没问题再下载。

## 2 实现要点

- `DocPublishService`（包 `org.dromara.lqg.doc.publish`）：
  - `publish(sampleId, docType)`：须为 draft → 置 published + 完成人 + 完成时间 → 同一请求里让该样本的合并件失效（§0 第 4 条）→ 提交内部版、外部版、merged 两个版本的渲染任务（异步排队）。合并件每份自成一节（分节符 = 下一页，保留各自纸张，CR-20260924-10）。
  - `unpublish(sampleId, docType)`：须为 published → draft、清完成时间，同一请求里让合并件失效；后台按剩下的已完成成员重出，一份不剩则撤下。
  - `onContentChanged(sampleId, docType)`：QC 域每个写接口（三个 PUT、图片与附件的增删排序）保存成功后调用；文档若是 published → 自动 unpublish。**在 qc 包的 service 里接这个钩子**，别靠 controller 记得调。
- `POST /lqg/qc/{sampleId}/{docType}/publish`、`…/unpublish`（权限 `lqg:qc:publish`）。
- 工作台 `PreviewPane.vue`：点「预览」→ 调 render（内部版）→ 轮询 pages → 逐页显示图片；「内部版 / 外部版」切换（CR-20260924-10：外部版的说明按 pages 的 `internalNoShown` 说这一版「内部编号」一格已印出 / 为空，`outdated=true` 时提示「设置改过了，正在按新设置重新生成」并在后台轮询到新的一版；词条在 `lang/lqg/qc-ino.*.ts`）；四个下载按钮（Word / PDF / 合并 Word / 合并 PDF，真下载）；渲染中、失败（原因 +「重新生成」，调 `render?force=true` 一定重出）两态；内部版照出但缺图时提示缺了哪几张（CR-20260923-09）。
  从没生成过（pages 回 `status=none`，CR-20260924-11）只显示空态「这份文档还没生成过页面图 [预览]」、不弹提示；换页签只由面板自己 watch 读一次状态（编辑页不再另调 `loadPages`），请求带序号、晚到的旧响应不覆盖新的，面板卸载后停轮询；只有点下载而确实没有产物时提示一句（请求层那一条，面板不再补第二条）。
  把 QC-WEB-001 里置灰的「预览」「完成并同步」点亮；已完成状态下页脚显示「已同步给送检方 · 修改后需重新同步」。

## 3 边界（明确不做）

- 不做审批流（谁编辑谁完成）
- 不做完成通知（订阅消息 / 短信）
- 不做历史版本留存——撤回后旧产物不再可取
- 外部接口在 AUTH-EXT-003

## 4 完工报告要求

1. 完成前后、修改后三个时刻的页签徽标截图；预览面板内外部版对照截图
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：新增 accept 3（V04：撤回合并件里的一份 → 同一请求里旧合并件不再下发，后台按新成员重出，重新生成不空转）；§0 口径 4 与 §2 写明「失效」= 同一请求里置回 pending、旧产物不再下发、后台重出、一份不剩则撤下。
- 2026-09-24 按 CR-20260924-10 更新：accept 2 补预览面板的外部版说明接后端 `internalNoShown` / `outdated` 与两条词条两段；§2 补面板口径与合并件分节。其余 accept 逐条核过不用改。
- 2026-09-24 按 CR-20260924-11 更新：§2 预览面板补「从没生成过 = status=none 只显示空态、不弹提示，一次切换只读一次状态，下载失败只提示一条」。accept 不动（H 批已在隔离环境重放 3/3 绿）。
