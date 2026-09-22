---
ticket: SYS-ACCEPT-001
track: SYS
phase: D7
size: M
req_refs:
  - REQ-DOC-003
  - REQ-DOC-007
  - REQ-SYS-017
depends_on:
  - DOC-MP-002
  - DOC-PUBLISH-001
  - SYS-EXPORT-001
  - SYS-HOME-001
touches:
  - doc/tickets/DOC-MP-002/prompt.md
  - doc/tickets/DOC-PUBLISH-001/prompt.md
  - doc/tickets/SYS-EXPORT-001/prompt.md
  - doc/tickets/SYS-HOME-001/prompt.md
  - doc/waves/regression/D7/**
adr_refs: []
blueprint_refs:
  - FLOW:F-DOC-02.step4
  - UI:mp.doc.list
  - UI:admin.home
accept:
  - name: "四条高危断言改成行为判据后，**改坏必须变红**：对每个热点施加已定义变异 → 对应断言红；还原 → 复绿（一条都不能「改坏了还绿」）"
    form: STATE
    run: |-
      bash doc/waves/regression/D7/mutation-assert.sh
    counterfeit: |-
      把「行为断言」写成新的源码 grep（换个词继续扫注释/文件存在性）→ 变异 1（删真代码只留注释）照样绿，第 1 段红。
      H1 只在 fileHandoff.ts 里 grep Authorization 而不看调用点 → 变异 H1b（导出调用点不传头）仍绿，红。
      H2 只断后端接口返回值、不看页面 DOM → 变异 H2（把卡片数字写死成错数）仍绿，红。
      H3 只看组件存在 / 只 grep previewImage 而不比 URL → 变异 H3b（看原图改成看预览图）仍绿，红。
      H4 只 grep 组件名不数渲染出的入口数 → 变异 H4（删掉一个下载入口）仍绿，红。
      变异脚本自己吞掉失败（`|| true`、把断言写在 `if` 里不回传退出码）→ 第 1 段自称全绿但实际没测，红。
  - name: "强化后的四条断言在未改坏的树上全绿，且每条都留下了真实运行证据（真请求头 / 真 DOM 值 / 真 URL），不是源码字面量"
    form: DATA
    run: |-
      bash doc/waves/regression/D7/mutation-assert.sh --verify-only &&
      test -f doc/waves/regression/D7/accept-strengthened/evidence.json &&
      python3 -c "import json;d=json.load(open('doc/waves/regression/D7/accept-strengthened/evidence.json'));assert set(d['hotspots'])=={'H1a','H1b','H2','H3a','H3b','H4'};[print(k,'ok') for k in sorted(d['hotspots'])]" &&
      ! grep -qF "grep -E 'Authorization|clientid'" doc/tickets/SYS-EXPORT-001/prompt.md &&
      ! grep -qF "'showMenu:[[:space:]]*true' src/components/lqg/DownloadBar.vue" doc/tickets/DOC-MP-002/prompt.md &&
      grep -qF 'mutation-assert.sh' doc/tickets/SYS-EXPORT-001/prompt.md &&
      grep -qF 'mutation-assert.sh' doc/tickets/SYS-HOME-001/prompt.md &&
      grep -qF 'mutation-assert.sh' doc/tickets/DOC-MP-002/prompt.md &&
      grep -qF 'mutation-assert.sh' doc/tickets/DOC-PUBLISH-001/prompt.md
    counterfeit: |-
      evidence.json 里只有「字段存在」没有实际观测值（没有真请求头、没有 DOM 读数、没有 URL）→ 第 3 段无法证明判据是行为型，红。
      只写 H1 不写 H1b（调用点变异）→ 集合不等于六个热点，第 3 段红。
      --verify-only 与默认模式行为相同（即变异根本没施加）→ 第 1 段会因「改坏了没红」露出，红。
---

# SYS-ACCEPT-001 · 把已放跑过缺陷的四条 accept 从「源码 grep」改成「行为判据」（并用变异验证）

## 0. 为什么有这张票（不是镀金，是止损）

D7 的 QA 门 r1 里，**一条真 S1 被一个源码 grep 型断言放跑了**：

> `SYS-EXPORT-001` 的 accept 2 写 `grep -E 'Authorization|clientid' export.ts fileHandoff.ts` —— 它只问「这两个文件里有没有这两个词」。
> 实测：**把导出的调用点改成不传 header，整条 acc2 仍全绿**（含两条 vitest 10/0、17/0）。
> 于是「鉴权头这件事已经验过」的假信心成立了 —— 而实际上**「哪个调用方该带头」从来没被验过**。
> 后果：`fileHandoff#downloadToTemp` 把「必须带 Authorization」强加给**所有**调用方，于是文档下载把 API 鉴权头塞到了 **OSS 预签名直链**上 → MinIO `400 request has multiple authentication types` → **H5 上单份/合并下载 0/4 全失败**（issue #279，已修并 A/B 复验）。

同族的账已有 8 条：`#236`（评分分值硬编码进页面 → 三处 accept 全绿）、`#242`（`form[internalNo]` 绕过只读 grep）、`#264`（注释满足 grep）、`#275`（调用点漏传 header）、`#280`（= 本票 H1）、`#281`（首页数字写死成错的数仍全绿）、`#282`（删真代码只留注释 / 看原图改成看预览图）、`#283`（删掉一个下载入口仍绿）。

**共同根因**：这些断言打的是**源码字面量**，而它对三种情况都无力 —— ①注释里有同样字面量；②等价写法（括号绑定、参数改写）；③**调用点**（被调方没问题，调用方用错了）。
真正打不穿的是 D7 的 L2 片已经在做的那种判据：**真请求头 / 真 DOM 读数 / 真 URL 对照**。

## 1. 要改的六个热点（**只做这六个，别扩范围**）

| 热点 | 归属 ticket | 现状（弱） | 改成（行为判据） | 已定义变异（必须使它变红） |
|---|---|---|---|---|
| **H1a** | `SYS-EXPORT-001` acc2 | 文件里有没有 `Authorization` 这个词 | **导出请求**（真发一次）必须带 `Authorization` + `clientid`，且 200/真 xlsx | 去掉 `ledger/index.vue` 里 `header: authHeader()` |
| **H1b** | `SYS-EXPORT-001` acc2 | 同上 | **文档下载请求**（真点一次单份下载）必须**不带** `Authorization`（且 200） | 给 `DownloadBar.vue` 的 `downloadToTemp` 传回 `authHeader()` |
| **H2** | `SYS-HOME-001` acc2 | 只 grep「卡片接了接口」 | 真 DOM 读五张卡片的数字 **== `/lqg/home/todo` 的返回值** | 把某张卡的 `:value="todo.pendingSamples"` 改成写死的 `7` |
| **H3a** | `DOC-MP-002` acc1 | `grep 'showMenu:[[:space:]]*true'` | `showMenu: true` 必须出现在**真代码**里（**注释不算**：把注释里的字面量换掉后仍须命中） | 删掉真代码那一行、只在注释保留字面量 |
| **H3b** | `DOC-MP-002` acc1 | 对 ThumbStrip 只有一句 `grep previewImage` | 点缩略图后打开层拿到的 src **== 原图 URL**（且 `≠ previewUrl`） | 把「看原图」改成打开 `previewUrl` |
| **H4** | `DOC-PUBLISH-001` acc2 | 「四个下载入口」只有字面量 | 真 DOM 数渲染出的下载入口**恰好 4 个**，且各自 `format` 正确 | 删掉「下载合并 PDF」按钮 |

## 2. 怎么做（形态由你决定，但两条硬要求）

1. **判据必须是行为型的**：允许用 Playwright / `curl` / `api.sh`，但**不许**用「源文件里有没有某个词」作为判据。`showMenu` 这个特例（H3a）允许读源码，但**必须证明它与注释无关**（例如先把注释里的字面量抹掉再断言 —— 这正是变异 H3a 要验的）。
2. **变异验证必须自动化**：写 `doc/waves/regression/D7/mutation-assert.sh`，默认模式 = 「施加每个变异 → 断言对应判据红 → 还原 → 断言复绿」；`--verify-only` = 只跑「未改坏的树上判据全绿」。**任何一个变异「改坏了还绿」就 exit 1**。脚本自己负责起停它需要的前端/后端（可用 `bash doc/waves/tools/detach.sh` + `qa-up.sh`；**关进程只许按 PID**），并把六个热点的实际观测值写进 `doc/waves/regression/D7/accept-strengthened/evidence.json`。

## 3. 边界（明确不做）

- **不动产品代码**：本票只改**断言 + 回归资产**（`doc/tickets/<那四张>/prompt.md` 的 `run` 与 `counterfeit`，以及 `doc/waves/regression/D7/**`）。若你发现产品真的还有缺陷 → **不要顺手修**，记 issue 报我。
- **不做**那四张票的其他断言（只这六个热点）；**不重构** `fileHandoff`；**不改** `doc/api-contract.md`（那是另一件事）。
- 不要为了省事把判据写成「跑一遍 L2 的整脚本」—— 那会失去「每个热点各自有可施加的变异」这个性质。

## 4. 环境与纪律

- 起环境：`bash doc/waves/tools/qa-up.sh --backend-port 8094 --web-port 8093 --mp-port 9204`（工作台 8093 / H5 9204 / 后端 8094）；`export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"`（★ 不导出的话 DATA accept 会落到默认 8081 报 exit 2「连不上后端」——那是工具红不是产品红）。
- **关进程只许按 PID**；**严禁 `pkill -f 'ruoyi-admin.jar'`**（8080 是别的项目，issue #46）。端口纪律：**8080/5432/6379 留给 Kevin**。
- ★ **`code/miniapp/src/pages.json` 是有意保留的状态**（tabBar `[index,doc,me]`）——**不要 `git checkout` 它**；要重生成就先 `rm` 再 build（issue #258）。
- **Playwright 的 require 锚点用 `code/miniapp/package.json`**（用 plus-ui 会 `MODULE_NOT_FOUND`，issue #229）。**绝对不要把 PNG/截图读进自己的上下文**。
- `lqg-dev-gotenberg` 在 running——**别停**。**seed 的图是假地址**（渲染时跳过+WARN、文档仍 done，issue #217 裁定①）；`t_lqg_doc_file` **reseed 后是空的**，要验下载/预览得先 `publish`+`render` 造出产物（或用真图）。
- 跑 accept：`python3 doc/waves/tools/accept-run.py --ticket SYS-ACCEPT-001 --run --json .tmp/sys-accept.json --logdir .tmp/sys-accept-logs`。

## 5. 完工报告要求

`doc/waves/reports/SYS-ACCEPT-001.md`：六个热点各自的「旧判据 → 新判据」对照、**变异前后实际观测值**（真请求头 / 真 DOM 数 / 真 URL）、两个 accept 的实际输出、`evidence.json` 摘要、越界与 WARN、给 D7 门复跑的说明；并如实说明**哪几条仍做不到行为化**（若真有）。
