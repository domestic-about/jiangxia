---
ticket: SYS-MANUAL-001
track: SYS
phase: D8
size: S
req_refs:
  - REQ-SYS-007
depends_on:
  - DOC-MP-002
  - SYS-HOME-001
  - CRYO-WEB-001
  - EMBED-WEB-001
  - SYS-EXPORT-001
  - CRYO-MP-001
touches:
  - doc/manual/**
adr_refs: []
blueprint_refs:
  - FLOW:F-SAMPLE-01.step1
  - FLOW:F-SAMPLE-01.step3
  - FLOW:F-SAMPLE-02.step7
  - FLOW:F-QC-01.step6
  - FLOW:F-CRYO-02.step1
  - FLOW:F-CRYO-02.step5
  - FLOW:F-DOC-02.step4
  - FLOW:F-MP-01.step2
accept:
  - name: "内部版覆盖合同功能清单 14 行且每章有截图；外部版一页、不出现内部用语、把「下载」讲明白了；两份 PDF 是本次导出的"
    form: DATA
    run: |-
      I=doc/manual/内部人员操作说明.md && E=doc/manual/外部用户操作说明.md && test -f "${I}" && test -f "${E}" &&
      for K in 四张表 历史编辑记录 内部管理 修改 拍照识别 预览 下载 微信登录 同组 样本总表 导出 切片 质控文档 冻存代数 超过两周 无染色 备份; do grep -q "${K}" "${I}" || { echo "内部版缺：${K}"; exit 1; }; done &&
      test "$(grep -cE '^\| *[0-9]+ *\|' "${I}")" -ge 14 && test "$(grep -c '!\[' "${I}")" -ge 12 &&
      ! grep -nE '内部编号|核验状态|verify|ExtScope' "${E}" && grep -q '发送到微信' "${E}" && grep -q '右上角' "${E}" && grep -q '核对' "${E}" && grep -q '历史编辑记录' "${E}" && ! grep -q '冻存' "${E}" &&
      test "$(pdfinfo doc/manual/pdf/外部用户操作说明.pdf | awk '/^Pages:/{print $2}')" -le 2 &&
      test doc/manual/pdf/内部人员操作说明.pdf -nt "${I}" && test doc/manual/pdf/外部用户操作说明.pdf -nt "${E}" &&
      pdftotext doc/manual/pdf/外部用户操作说明.pdf - | tr -d ' \n' | grep -q '送检'
    counterfeit: |-
      内部版只写了工作台、漏了冻存取用或导出 → 关键词缺失红；对照表不足 14 行红——甲方要拿它逐项验收。
      内部版没写「历史编辑记录」「内部管理」、没写记录怎么修改 → 关键词缺失红（Kevin 9-17 晚定的小程序结构）。
      外部版没写怎么找回自己填过的、或写进了冻存 → 「历史编辑记录」缺失红 / 「冻存」出现红（外部没有 -80 冻存记录）。
      外部版写成了三页的功能介绍 → 页数红。外部版里出现「内部编号」→ 红（那是不想让外部知道的东西）。
      改了 md 没重新导出 PDF → -nt 红。PDF 里中文是方框 → 抽不出「送检」红。
---

# SYS-MANUAL-001 · 两份操作说明：内部人员版（工作台 + 小程序内部功能）、外部用户版（一页图文）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-MP-002**、**SYS-HOME-001**、**CRYO-WEB-001**、**EMBED-WEB-001**、**SYS-EXPORT-001**、**CRYO-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 合同第五条：验收标准含「乙方交付操作说明」；第六条第 1 款：为内部人员提供操作培训，并提供供外部用户使用的小程序操作说明
  - 合同附件《系统功能清单》14 行——内部版说明要让甲方能**拿着它逐项验收**
- [ ] 口径复述（本张最容易做反的）：
  1. 外部版是给合作单位的人看的：**一页**，大白话，不出现「内部编号」「核验状态机」这类词；重点四件事——怎么登录、怎么填三张表（含拍照识别与核对）、在「我的 → 历史编辑记录」找回并修改、怎么看结果和下载。
  2. 要把小程序「下载」的真实样子写明白：「打开」后右上角菜单可保存，或「发送到微信」。否则甲方培训时第一个问题就是「下载到哪去了」。
  3. 截图用测试环境的假数据，不用真实样本。
  4. 内部版要写清 2026-09-17 定下的小程序结构：首页点表填写；「我的 → 历史编辑记录」找回并修改自己填过的；「我的 → 内部管理」查看、筛选、导出 Excel（CR-20260917-05）。冻存登记填错了在工作台怎么改、怎么删（CR-20260917-04）。

## 1 背景与口径

交付物。培训本身是人的活（Kevin 线上讲），这张产出培训用的材料。

## 2 实现要点

- `doc/manual/内部人员操作说明.md`：按一天的工作顺序写——登录与授权同事 → 核验外部送检 → 录入收样（小程序首页点表填写，填错了从「我的 → 历史编辑记录」改；工作台总表）→ 石蜡包埋补填 → 冻存、取用、转液氮与超期提醒（登记填错了怎么改、怎么删）→ 编辑质控文档、预览、完成并同步 → 导出四张 Excel（工作台与小程序「我的 → 内部管理」两处）→ 维护单位组别与核验外部用户 → 常见问题（识别不准怎么办、文档生成失败怎么办、误判有效怎么改）。
  文末附一张「合同功能清单 14 行 ↔ 本说明章节」对照表。
- `doc/manual/外部用户操作说明.md`：一页。
- 两份各导出一份 PDF（`pandoc` + 中文字体），放 `doc/manual/pdf/`。

## 3 边界（明确不做）

- 不录视频教程
- 不做在线帮助中心
- 不写开发文档（那是部署手册、运维手册的事）

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
