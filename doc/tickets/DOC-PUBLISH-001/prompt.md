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
      test "$(st t_lqg_qc_sample 9000001006)" = "published|9000000101|True" &&
      bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001006/sample-qc/publish | grep -qE '^(400|500)' &&
      sleep 20 && python3 doc/verify/db.py --sql "SELECT audience || ':' || file_format FROM t_lqg_doc_file WHERE sample_id=9000001006 AND doc_kind='sample_qc' AND render_status='done' AND page_no IN (0,1) AND del_flag='0'" --col-set "internal:docx,internal:pdf,internal:png,external:docx,external:pdf,external:png" &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/sample-qc '{"samplingSite":"改了一个字"}' | jq -e '.code==200' &&
      test "$(st t_lqg_qc_sample 9000001006)" = "draft|-|False" &&
      test "$(st t_lqg_qc_score 9000001006)" = "published|9000000101|True" &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/score '{"preCultureLevel":"gt80","cultureDaysLevel":"le14","organoidCountLevel":"lt100","diameterLevel":"lt30"}' | jq -e '.code==200' &&
      test "$(st t_lqg_qc_score 9000001006)" = "draft|-|False" &&
      test "$(st t_lqg_qc_organoid 9000001001)" = "published|9000000101|True" &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/organoid-qc/image '{"slot":"organoid_observe","ossId":9000004005}' | jq -e '.code==200' &&
      test "$(st t_lqg_qc_organoid 9000001001)" = "draft|-|False" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      「改内容回草稿」只接在了三个 PUT 上、漏了图片与附件的增删 → 最后一组（加一张图）状态仍是 published 红。送检方会看到一份和预览图对不上的文档。
      钩子写在 controller 里、评分接口忘了调 → 评分那组红。
      完成只改了状态、没触发外部版渲染 → 等 20 秒后 doc_file 里没有 external 的三种产物，集合红。
      重复完成不拒绝（每点一次重置一次完成时间）→ 第 5 段红；文档列表按完成时间排序会乱跳。
      状态断言全部直连库读，不信接口自报。
  - name: "前端构建是本次产物；预览面板接入编辑页，有内外部版切换、四个下载入口、失败态与重新生成"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -q 'PreviewPane' src/views/lqg/qc/editor/index.vue &&
      grep -q 'audience' src/views/lqg/qc/components/PreviewPane.vue && grep -q 'merged' src/views/lqg/qc/components/PreviewPane.vue &&
      grep -cE "format.*(docx|pdf)|'docx'|'pdf'" src/views/lqg/qc/components/PreviewPane.vue | awk '{exit !($1 >= 2)}' &&
      grep -qE 'failed|重新生成' src/views/lqg/qc/components/PreviewPane.vue &&
      ! grep -nE ':disabled="true"' src/views/lqg/qc/editor/index.vue
    counterfeit: |-
      预览面板直接 iframe 了 PDF 链接 → 和小程序看到的页面图片不是同一份东西；要求用 pages 接口的页面图片。
      「完成并同步」按钮还留着 QC-WEB-001 的写死置灰 → 最后一段红。
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
  4. 任何一份的状态或内容变化都让该样本的 `merged` 失效。

## 1 背景与口径

方案流程图后半段：网页编辑质控文档 → [预览无误？] → 结果同步送检方；有误重新编辑。会上 L370：外部人员要能查看他们送的样本对应的这三个表。L136：预览之后觉得没问题再下载。

## 2 实现要点

- `DocPublishService`（包 `org.dromara.lqg.doc.publish`）：
  - `publish(sampleId, docType)`：须为 draft → 置 published + 完成人 + 完成时间 → 提交内部版、外部版、merged 两个版本的渲染任务（异步排队）。
  - `unpublish(sampleId, docType)`：须为 published → draft、清完成时间。
  - `onContentChanged(sampleId, docType)`：QC 域每个写接口（三个 PUT、图片与附件的增删排序）保存成功后调用；文档若是 published → 自动 unpublish。**在 qc 包的 service 里接这个钩子**，别靠 controller 记得调。
- `POST /lqg/qc/{sampleId}/{docType}/publish`、`…/unpublish`（权限 `lqg:qc:publish`）。
- 工作台 `PreviewPane.vue`：点「预览」→ 调 render（内部版）→ 轮询 pages → 逐页显示图片；「内部版 / 外部版」切换；四个下载按钮（Word / PDF / 合并 Word / 合并 PDF，真下载）；渲染中、失败（原因 +「重新生成」）两态。
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
