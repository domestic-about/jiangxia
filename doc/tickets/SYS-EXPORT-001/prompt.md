---
ticket: SYS-EXPORT-001
track: SYS
phase: D7
size: M
req_refs:
  - REQ-SYS-017
depends_on:
  - DOC-MP-002
  - SAMPLE-EXPORT-001
  - EMBED-WEB-001
  - CRYO-WEB-001
  - EMBED-MP-001
  - CRYO-MP-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sys/export/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sys/export/**
  - code/miniapp/src/pages/ledger/index.vue
  - code/miniapp/src/pages/ledger/export.ts
  - code/miniapp/src/pages/ledger/export.spec.ts
  - code/miniapp/src/utils/fileHandoff.ts
  - code/miniapp/src/components/lqg/DownloadBar.vue
adr_refs:
  - ADR-0005
blueprint_refs:
  - UI:mp.ledger
  - FLOW:F-SAMPLE-02.step7
  - FLOW:F-DOC-02.step4
accept:
  - name: "小程序导出的四个文件与甲方模板原件逐字对表头、行数与库内独立计数一致；带筛选只出筛选结果；外部角色 403、未知工作表被拒"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-mpx-*.xlsx &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --out /tmp/lqg-mpx-tissue.xlsx GET /mp/int/export/tissue &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-mpx-tissue.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND sample_kind='tissue'" | head -1)" --find "内部编号=T-hli01" --expect "供体姓名=测试供体甲,有无固定=有" &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-mpx-organoid.xlsx GET /mp/int/export/organoid &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-mpx-organoid.xlsx --template "_input/templates/类器官收样记录模板.xlsx" --rows 1 &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-mpx-embed.xlsx GET /mp/int/export/embed &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-mpx-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id AND s.del_flag='0' WHERE e.del_flag='0'" | head -1)" &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-mpx-cryo.xlsx GET /mp/int/export/cryo &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-mpx-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --rows 7 --find "冻存样品=T-hli01-GZ-N-P2-EM2-2e5" --expect "冻存数量/支=8,当前剩余/支=6" &&
      bash doc/verify/api.sh --as staff --out /tmp/lqg-mpx-tissue-f.xlsx GET '/mp/int/export/tissue?verifyStatus=pending' &&
      python3 doc/verify/xlsx_header.py --file /tmp/lqg-mpx-tissue-f.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --rows 2 &&
      bash doc/verify/api.sh --as extA --bizcode GET /mp/int/export/tissue | grep -qE '^403' &&
      bash doc/verify/api.sh --as staff --bizcode GET /mp/int/export/qc | grep -qE '^(400|404)'
    counterfeit: |-
      sys 包里另写了一份导出 VO → 表头与模板不一致红，或某列格式和工作台不同（有无固定导成 Y）→ --expect 红。两侧不同源：小程序导出的文件 vs 甲方发来的模板原件 + 直连库计数。
      忽略筛选参数、永远导全量 → 带 verifyStatus=pending 那段行数不是 2 红。
      /mp/int/export 忘了角色注解 → extA 拿到 200 红：外部能把全部样本连同供体姓名明文导走。
      sheet 不校验、未知值落到默认表 → 最后一段拿到 200 红。
  - name: "构建是本次产物；导出与文档下载共用同一段「打开 / 发送到微信」；下载带鉴权头；拼查询串的纯函数过单测；抽公共函数后文档下载的单测仍过"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && test -f dist/build/mp-weixin/pages/ledger/index.js &&
      grep -q 'fileHandoff' src/components/lqg/DownloadBar.vue && grep -q 'fileHandoff' src/pages/ledger/index.vue &&
      grep -qE 'showMenu:[[:space:]]*true' src/utils/fileHandoff.ts && grep -q 'shareFileMessage' src/utils/fileHandoff.ts &&
      grep -qE 'Authorization|clientid' src/pages/ledger/export.ts src/utils/fileHandoff.ts &&
      pnpm vitest run src/pages/ledger/export.spec.ts --reporter=json --outputFile=/tmp/lqg-export.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 3' /tmp/lqg-export.json &&
      pnpm vitest run src/pages/doc/download.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-dl2.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/lqg-dl2.json
    counterfeit: |-
      表格导出另写了一套 downloadFile + openDocument、没带 showMenu → 用户打开了 Excel 却没有保存 / 转发入口，第 3、4 段红。
      downloadFile 没带 Authorization → 真机上拿到的是一段 401 的 JSON，当成 xlsx 打开失败；第 5 段要求请求头出现在导出链路里，完工报告附真机录屏。
      抽 fileHandoff 时把文档下载改坏 → 最后一段 DOC-MP-002 的单测红。
---

# SYS-EXPORT-001 · 小程序 · 内部管理：表格页「导出 Excel」——四张表按当前筛选导出，与工作台同一个导出视图，打开或发送到微信

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-MP-002**、**SAMPLE-EXPORT-001**、**EMBED-WEB-001**、**CRYO-WEB-001**、**EMBED-MP-001**、**CRYO-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 四张导出的表头、列序、格式化规则已在工作台钉死：SAMPLE-EXPORT-001（两张样本表）、EMBED-WEB-001（石蜡包埋）、CRYO-WEB-001（-80 冻存）——本张只做「小程序里也能拿到同一个文件」，不重新定义导出
  - 平台限制：小程序不能把文件存进手机文件夹。拿到文件后「打开」（`wx.openDocument({showMenu: true})`，右上角可保存 / 转发）或「发送到微信」（`wx.shareFileMessage`）——与文档下载同一套（DOC-MP-002 的 `DownloadBar`）
  - **ADR-0005**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0005` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **同一个导出视图**：`/mp/int/export/{sheet}` 直接调三个域已有的导出 service（VO + 查询条件对象），不许在 sys 包里再写一份列定义——两处导出迟早不一样。
  2. 筛选参数与表格页当前筛选一致：用户筛了什么就导出什么，不筛就是全部。
  3. `wx.downloadFile` 要带 `Authorization` 与 `clientid` 两个请求头（导出是鉴权接口，不是 OSS 签名链接）；后端域名要在小程序后台的 downloadFile 合法域名里（写进报告，SYS-RELEASE-001 用）。
  4. 把 `DownloadBar` 里「临时文件 → 打开 / 发送到微信」那段抽到 `src/utils/fileHandoff.ts`，文档下载与表格导出共用；抽完 DOC-MP-002 的单测仍要过。

## 1 背景与口径

2026-09-17 甲方看设计稿 v1：「这4个表都要求内部人员可下载，导出为 excel」（REQ-SYS-017，CR-20260917-04）。工作台四张表本来就能导出，这次要的是在小程序里也能导。同日晚 Kevin 定表格页挪到「我的 → 内部管理」、表格本身只读，导出是它底部唯一的按钮（CR-20260917-05）。2026-09-18 表格页加了修改入口——点一行进只读模式、只读页右上角「修改」切到修改模式（CR-20260918-07）：**导出逻辑本身不变**，只有页底那行小字去掉「修改」二字。

## 2 实现要点

- 后端 `org.dromara.lqg.sys.export`：`GET /mp/int/export/{sheet}`（`sheet` ∈ `tissue | organoid | embed | cryo`，未知值 400），类级 `@SaCheckRole("lqg_internal")`；
  查询参数绑定到对应工作表 list 的查询对象，调 `SampleExportService` / `EmbedExportService` / 冻存的导出 service，响应 xlsx 流（`Content-Disposition` 用 RFC 5987 写中文文件名，如 `样本记录信息表-20260918.xlsx`）。
- `src/utils/fileHandoff.ts`：`downloadToTemp(url, header?)`、`openFile(path, fileType)`（`showMenu: true`）、`shareFile(path, fileName)`；`DownloadBar` 改为调用它。
- `src/pages/ledger/export.ts`：`exportUrl(sheet, filters)` 纯函数（拼查询串，空值不带）+ `export.spec.ts`；表格页底部「导出 Excel」点亮 → 生成中提示 → 弹出「打开 / 发送到微信」；页底那行小字是「核验、冻存取用请到网页工作台」（CR-20260918-07，SAMPLE-MP-002 已按新口径改成这句，点亮导出时别改回旧版）。

## 3 边界（明确不做）

- 不给外部任何导出
- 不做自定义导出列、不做导出历史
- 不把导出文件存进 OSS（直接流式返回）

## 4 完工报告要求

1. 真机录屏：表格页筛选 → 导出 Excel → 打开 → 右上角转发；同一筛选在工作台导出一份，两份文件并排截图
2. downloadFile 用的是哪个域名
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
