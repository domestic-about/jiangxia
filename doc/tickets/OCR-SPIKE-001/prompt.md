---
ticket: OCR-SPIKE-001
track: OCR
phase: D5
size: M
req_refs:
  - REQ-OCR-004
depends_on: []
touches:
  - doc/ocr-spike/**
  - _input/ocr-photos/**
adr_refs:
  - ADR-0007
blueprint_refs:
  - FLOW:F-OCR-01.step2
  - FLOW:F-OCR-01.step3
accept:
  - name: "报告里的命中率与逐张逐字段的原始记录重算一致；样本量、手写样本数、候选数（不按次收费 ≥ 2、按次收费 ≥ 1）达标；按次收费方案的价格有查询日期与出处"
    form: DATA
    run: |-
      python3 doc/verify/ocr_spike_check.py &&
      diff <(tail -n +2 doc/ocr-spike/results.csv | cut -d, -f4 | sort -u) <(sed -n '/summary:begin/,/summary:end/p' doc/ocr-spike/report.md | grep '^|' | tail -n +3 | cut -d'|' -f2 | tr -d ' ' | sort -u) &&
      test "$(ls _input/ocr-photos | grep -ciE '\.(jpg|jpeg|png|heic)$')" -ge 10 &&
      grep -q '推荐' doc/ocr-spike/report.md && grep -q '给甲方的说明' doc/ocr-spike/report.md &&
      ! git -C . ls-files --error-unmatch _input/ocr-photos >/dev/null 2>&1
    counterfeit: |-
      只测了印刷体样张就下结论「免费方案够用」→ 手写样本 < 3 张，检查器红。
      报告里的命中率是估的、和逐条记录对不上 → 重算差值超过 0.5 个百分点红。两侧不同源：报告正文 vs results.csv。
      只比了免费方案、没给 AI 的对照与单价 → 候选数红；价格凭记忆写、没有查询日期和链接 → 红。
      拿自拍的模拟样张充数 → 照片目录里不足 10 张真实照片红（完工报告要写明每张照片的来源）。
      把带供体信息的照片提交进了 git → 最后一段红。
---

# OCR-SPIKE-001 · 【探路】拍照识别方案实测：拿甲方真实照片，对比不按次收费的方案与按次收费的 AI，出书面结论

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/OCR` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：无（本任务的起点之一）
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 方案 v6「附：项目难点——拍照识别信息」（`_input/04-初步方案-v6（甲方已确认）.md` L69-L87）：难点五条 + 我方做法三条，这是对甲方的书面承诺
  - 合同第二条第 5 款、附件二第 4 条：优先不产生按次费用的方案；接按次收费的服务须甲方书面同意、费用甲方出
  - `_input/presale/00-brief（内部）.md`「拍照识别调研」一节：要比的几条路。**各家的价格和免费额度现查，不许凭记忆写**
  - **ADR-0007**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0007` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **这是探路不是实现**：产出是一份对比报告和一个推荐，不是要留下的代码。测试脚本随手写，放 `doc/ocr-spike/scripts/`，不进业务模块。
  2. **非开发前置：甲方的 10-20 张真实照片**（管壁、袋子、送检单、截图各几张，可遮挡隐私）。照片没到 → 本张 blocked，别拿自拍样张出结论。
  3. 照片含供体信息：放 `_input/ocr-photos/`（不进 git），**不上传到任何海外服务**，测国内云服务前先确认其数据留存条款。
  4. 没有手写样本的结论不算数——手写正是难点。

## 1 背景与口径

Kevin 会上答应的待办（AI 纪要待办第 2 条）：调研照片提取信息的方案，优先确认有没有不集成 AI 就能实现的，结果同步甲方。
方案 v6 承诺：先用不按次收费的方案逐张测试，结果书面同步；效果不够再给 AI 的效果对比和按次费用测算，甲方确认后接入。

## 2 实现要点

1. 收照片 → `_input/ocr-photos/`，逐张登记：种类（管壁 / 袋子 / 送检单 / 截图）、是否手写、每个目标字段的**标准答案**（人工读出来的）。
2. 候选至少三个：不按次收费 ≥ 2（如微信侧的 OCR 能力、自部署开源 OCR），按次收费 ≥ 1（国内视觉大模型）。每个候选写清：怎么接、要不要服务器资源、数据会不会留存在对方。
3. 每个候选 × 每张照片 × 每个字段，跑出结果，记进 `doc/ocr-spike/results.csv`（表头见 `doc/verify/ocr_spike_check.py` 的说明）。字段解析可以先手工判（探路阶段看的是「识别出来的字对不对」）。
4. `doc/ocr-spike/report.md`：汇总表（机器可读标记段）、价格出处（查询日期 + 链接）、按「印刷 / 手写」分开的结论、**推荐方案 + 不推荐的理由**、对 2 核 4G 服务器的资源影响、给甲方的一页说明稿（大白话，可直接转发）。
5. 结论回写：`doc/_oq.md` 的 OQ-4 填结论；ADR-0007 不用改（架构与选哪家无关）；OCR-IMPL-001 的 provider 实现按推荐方案落。

## 3 边界（明确不做）

- 不写业务代码、不接进小程序
- 不替甲方决定要不要接按次收费的服务——只给对比和测算
- 不承诺识别率（合同第八条 7(4) 已列为免责）

## 4 完工报告要求

1. 照片清单（种类 × 手写与否的分布）
2. 推荐方案与一句话理由；若推荐按次收费方案，按甲方月样本量估的月费用
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
