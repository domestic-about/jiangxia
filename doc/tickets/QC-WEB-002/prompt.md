---
ticket: QC-WEB-002
track: QC
phase: D6
size: M
req_refs:
  - REQ-QC-005
  - REQ-QC-006
  - REQ-QC-007
  - REQ-QC-009
depends_on:
  - QC-WEB-001
touches:
  - code/plus-ui/src/views/lqg/qc/editor/OrganoidQcTab.vue
  - code/plus-ui/src/views/lqg/qc/editor/ScoreTab.vue
  - code/plus-ui/src/views/lqg/qc/editor/score.ts
  - code/plus-ui/src/views/lqg/qc/editor/score.fixture.spec.ts
  - code/plus-ui/src/views/lqg/qc/editor/index.vue
adr_refs: []
blueprint_refs:
  - UI:admin.qc.editor
  - FLOW:F-QC-01.step3
  - FLOW:F-QC-01.step4
  - FIELD:t_lqg_qc_score.total_score
  - FIELD:t_lqg_qc_organoid.planned_drug_screen
accept:
  - name: "评分即时反馈过 fixture：0 分档照算、没选全合计为空；保存只提交档位"
    form: STATE
    run: |-
      cd code/plus-ui &&
      grep -q 'doc/verify/fixtures/score-cases.json' src/views/lqg/qc/editor/score.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/qc/editor/score.fixture.spec.ts &&
      pnpm vitest run src/views/lqg/qc/editor/score.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-score.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 6' /tmp/lqg-score.json &&
      node -e "const c=require('../../doc/verify/fixtures/score-cases.json').cases; if(c.length!==6||!c.some(x=>x.expect.items.includes(0))||!c.some(x=>x.expect.total===null)) process.exit(1)" &&
      ! grep -nE '(preCulture|cultureDays|organoidCount|diameter)Score|totalScore' src/api/lqg/qc/*.ts | grep -iE 'put|save|update' | grep -q .
    counterfeit: |-
      合计写成 items.reduce((a,b)=>a+(b||0)) 且用 `if(!item)` 判未选 → 第 2、6 例（含 0 分档）合计对了但 items 错，或第 4 例（没选全）合计不为 null，红。
      把 fixture 里带 0 和带 null 的用例删了 → node 那段红。
      保存时把前端算的分值一起提交（后端会忽略，但这会让人以为前端的数算数）→ 最后一段红。
  - name: "前端构建是本次产物；两个页签已接入编辑页；评分选项文字照模板原文、没有质量等级结论"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -q 'OrganoidQcTab' src/views/lqg/qc/editor/index.vue && grep -q 'ScoreTab' src/views/lqg/qc/editor/index.vue &&
      grep -q 'organoid_observe' src/views/lqg/qc/editor/OrganoidQcTab.vue &&
      grep -q 'useDict' src/views/lqg/qc/editor/ScoreTab.vue &&
      ! grep -nE '质量偏差|质量中等|质量良好' src/views/lqg/qc/editor/ScoreTab.vue &&
      cd ../.. && node doc/waves/regression/V-round/workbench-behavior-dom.mjs --only=tabs
    counterfeit: |-
      评分选项在页面里硬编码（没走字典）→ useDict 那段红；将来改分值要改前端。
      顺手根据合计显示了「质量良好」→ 最后一段红：那是 v1 被砍掉的自动结论。
---

# QC-WEB-002 · 工作台 · 质控文档编辑页（二）：类器官质控表页签、类器官质量评分表页签

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/QC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**QC-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - `doc/verify/fixtures/score-cases.json`：评分即时反馈的验收用例（含 0 分档与没选全两类病灶）
- [ ] 口径复述（本张最容易做反的）：
  1. 评分表：四个变量各一组单选，选项文字**照模板的「分类」原文**（`<40`、`40~80`、`>14d`、`30~100μm`…），旁边显示该档分值；底部显示合计。
  2. 页面上的分值与合计只是即时反馈；**保存时只提交四个档位**，落库的分值以后端为准（保存后用返回值刷新显示）。
  3. **0 分不是没选**。`>14d` = 0 分、`<100` = 0 分，选了就要显示 0，合计照算。
  4. 不出「偏差 / 中等 / 良好」的结论文字——那句注是文档页脚的固定文字。

## 1 背景与口径

模板 F：类器官质控表六栏；模板 G：评分表四变量 12 档 + 一句注。

## 2 实现要点

- `OrganoidQcTab.vue`：样本观察情况（`ImageSlotUploader`，slot=`organoid_observe`）+ 五栏文本；「形成类器官时间」「反馈时间」= 日期选择器选了填 `yyyy-MM-dd`，同时允许直接手输文字。
- `ScoreTab.vue`：`ScoreRadioGroup` ×4（选项与分值来自 `useDict` 的 label 与 remark）；右侧「类器官质量评分」列与底部合计由 `scoreSummary(levels, dictScores)`（`score.ts`）给出；
  `score.fixture.spec.ts` 从 `doc/verify/fixtures/score-cases.json` 读用例。
- 在 `editor/index.vue` 里把两个占位页签换成真的。

## 3 边界（明确不做）

- 不做质量等级结论
- 不做评分历史 / 趋势
- 不改后端（评分回填在 QC-MODEL-001）

## 4 完工报告要求

1. 两个页签截图；评分表选了含 0 分档的组合的截图
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
