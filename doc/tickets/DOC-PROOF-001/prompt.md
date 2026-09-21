---
ticket: DOC-PROOF-001
track: DOC
phase: D7
size: S
req_refs:
  - REQ-DOC-011
  - REQ-DOC-004
depends_on:
  - DOC-PUBLISH-001
  - SYS-STAGING-001
touches:
  - doc/proofs/**
adr_refs:
  - ADR-0005
blueprint_refs:
  - FLOW:F-DOC-01.step2
  - FLOW:F-DOC-01.step3
accept:
  - name: "样张齐全、是测试环境当前模板版本渲染出来的（清单里的指纹与模板版本和文件本身对得上）、中文字体已嵌入、假数据、差异清单逐条有结论"
    form: DATA
    run: |-
      V="$(ls -d doc/proofs/v* | sort -V | tail -1)" && test -n "${V}" &&
      test "$(ls "${V}"/*.docx | wc -l | tr -d ' ')" -ge 4 && test "$(ls "${V}"/*.pdf | wc -l | tr -d ' ')" -ge 5 &&
      jq -e 'length >= 9 and all(.[]; .contentHash and .templateVersion and .sha256)' "${V}/manifest.json" &&
      jq -r '.[] | "\(.sha256)  \(.file)"' "${V}/manifest.json" | (cd "${V}" && shasum -a 256 -c --quiet) &&
      test "$(jq -r '[.[].templateVersion]|unique|length' "${V}/manifest.json")" = 1 &&
      test "v$(jq -r '.[0].templateVersion' "${V}/manifest.json")" = "$(basename "${V}")" &&
      for F in "${V}"/*.pdf; do pdffonts "${F}" | grep -qiE 'Noto|SourceHan|Source Han|思源' || exit 1; done &&
      for F in "${V}"/*.docx; do python3 doc/verify/docx_check.py --file "${F}" --no-placeholder --contains "T-proof01" >/dev/null || exit 1; done &&
      EXT="$(jq -r '.[] | select(.audience=="external" and .format=="pdf") | .file' "${V}/manifest.json" | head -1)" && test -n "${EXT}" && ! pdftotext "${V}/${EXT}" - | grep -q 'T-proof01' &&
      test "$(grep -cE '与原件一致|有差异' "${V}/差异清单.md")" -ge 5
    counterfeit: |-
      样张是在开发机（装了宋体）上出的 → pdffonts 里是 SimSun 不是思源，红。甲方确认的就成了一份线上出不来的样子。
      样张出完之后又改了模板、没重出 → manifest 的 templateVersion 与目录名对不上，或 sha256 校验不过红。
      外部版样张里还印着内部编号 → 红。
      拿真实样本出样张 → 内部版里搜不到 T-proof01 红（要求用专用的假样本）。
      差异清单只写了「基本一致」→ 逐条结论不足 5 条红。
---

# DOC-PROOF-001 · 三份样张 + 合并样张（Word 与 PDF）出具，附版式差异清单，发甲方确认

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-PUBLISH-001**、**SYS-STAGING-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 合同第五条：三份 Word 文档的预览与导出效果，**以甲方确认的样张为准**；第六条第 5 款：定稿后再改版式属二次开发
  - DOC-PDF-001 完工报告里的「字体替换带来的差异」
  - **ADR-0005**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0005` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **样张必须在测试环境的容器里出**（那里的字体才是线上的字体），不是在开发机上出。
  2. 样张用**假数据**（测试供体、T- 开头的编号），不放任何真实样本。
  3. 这张的终点是甲方回一句「可以」——要人。把待确认写进 `_manifest.json` 的 `needs_human`（route=client）。

## 1 背景与口径

「和 Word 一模一样」是验收里最主观的一条。把它变成客观的办法只有一个：先出样张，甲方签认，之后以样张为尺。

## 2 实现要点

1. 测试环境里建一个专用演示样本（内部，编号 `T-proof01`），三份文档填满（每个图片位 2 张真实尺寸的示意图、细胞活率附件、评分四项），完成并同步。
2. 下载 8 个文件到 `doc/proofs/v<模板版本>/`：三份 × (docx, pdf) + 合并 × (docx, pdf)，内部版；另下一份外部版样本质控表 pdf（让甲方看到外部版里内部编号是空的）。
3. `doc/proofs/v<模板版本>/manifest.json`：每个文件的 `docKind / audience / format / contentHash / templateVersion / sha256`。
4. `doc/proofs/v<模板版本>/差异清单.md`：逐份与甲方模板原件对照——字体（宋体 → 思源宋体）、「细胞活率测定」一格（嵌入图标 → 文件名）、图片位多张并排的排法、评分表选中档的填法、页脚注释；每条写「与原件一致 / 有差异：……」。
5. 给甲方的一页说明（大白话）：这是系统将来导出的样子；哪里和你们原来的 Word 不一样、为什么；小程序里「下载」是「打开」和「发送到微信」；请回复确认或指出要改的地方。

## 3 边界（明确不做）

- 不按甲方还没给的新模板做（给了再出下一版样张，模板版本号加一）
- 不做可编辑的在线确认页

## 4 完工报告要求

1. 样张目录清单；给甲方的说明原文
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
