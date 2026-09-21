---
ticket: DOC-MP-001
track: DOC
phase: D7
size: M
req_refs:
  - REQ-DOC-001
  - REQ-DOC-002
  - REQ-DOC-010
  - REQ-DOC-006
depends_on:
  - AUTH-EXT-003
  - SYS-MP-001
touches:
  - code/miniapp/src/pages/doc/index.vue
  - code/miniapp/src/pages/doc/group.ts
  - code/miniapp/src/pages/doc/group.fixture.spec.ts
  - code/miniapp/src/api/doc.ts
  - code/miniapp/src/components/lqg/DocGroupCard.vue
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/mp/**
  - code/miniapp/src/pages/sample/detail-ext.vue
adr_refs: []
blueprint_refs:
  - UI:mp.doc.list
  - FLOW:F-DOC-02.step1
accept:
  - name: "分组规则过 fixture：组内固定顺序、组间按最新完成时间倒序、两份起才有合并入口、混进来的 merged 行不算一份"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/doc-group-cases.json' src/pages/doc/group.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/doc/group.fixture.spec.ts &&
      pnpm vitest run src/pages/doc/group.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-docgroup.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/lqg-docgroup.json &&
      node -e "const c=require('../../doc/verify/fixtures/doc-group-cases.json').cases; if(c.length!==4||!c.some(x=>x.rows.some(r=>r.docKind==='merged'))) process.exit(1)"
    counterfeit: |-
      组内按完成时间排（而不是固定顺序）→ 第一例里评分表会排到质控表前面，红。
      showMerge 写成 length > 2 → 第二例（恰好两份）红。
      把 merged 行当成一份文档 → 第四例 showMerge 变成 true 红。
  - name: "构建是本次产物；内部清单只给内部角色且带内部编号、外部清单不带；页签名走配置；分组卡片上有单份下载与合并预览、合并下载"
    form: API
    run: |-
      (cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && jq -e '.tabBar.list[1].pagePath=="pages/doc/index"' dist/build/mp-weixin/app.json &&
       grep -q '合并下载' src/components/lqg/DocGroupCard.vue && grep -q '合并预览' src/components/lqg/DocGroupCard.vue &&
       grep -q 'DOC_TAB_NAME' src/config/app.ts && grep -q '@/components/lqg/DocGroupCard.vue' src/pages/doc/index.vue && ! grep -n 'internalNo' src/pages/doc/index.vue src/components/lqg/DocGroupCard.vue) &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001006/organoid_score/render?audience=internal' >/dev/null && sleep 5 &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/mp/int/doc/list?pageSize=100' | jq -e '[.rows[]|select((.sampleId|tostring)=="9000001006")|[.title,.subtitle,.docKind,.totalScore]]==[["T-hco04","B 大学","organoid_score",18]]' &&
      bash doc/verify/api.sh --as extD --bizcode GET '/mp/int/doc/list' | grep -qE '^403'
    counterfeit: |-
      前端按身份自己拼标题、外部视角下用了内部编号字段 → 页面源码里出现 internalNo 红（要求标题由后端给）。
      内部清单接口忘了角色注解 → 外部拿到全部文档清单连同内部编号，最后一段红。
      组底还是一个「合并预览 / 下载」入口、没有单独的「合并下载」按钮 → grep 红：甲方要的是列表上一眼就能下。
---

# DOC-MP-001 · 小程序 · 文档页签：按样本分组的已完成文档列表（每份带下载、可合并下载），内部看全部、外部看本人与同组

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-EXT-003**、**SYS-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` §5 与 §6 本页那一行；样式类用 SYS-MP-001 已落的 `src/style/components.scss`（`.lqg-*`），零颜色字面量。下面提到的图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**
  - 视觉基准 = **方案 A**（按样本分组），Kevin 2026-09-17 已选定（CR-20260917-03）；看图 `doc/design-options/gallery.html#mp-doc-a`。方案 B（按时间平铺）已否决
  - **REQ-DOC-010 是 clarify**：页签名与板块名甲方还没定。默认「文档」「质控文档」，放配置里，改名不发版
- [ ] 口径复述（本张最容易做反的）：
  1. 外部的组标题 = 送检单号 + 供体姓名（掩码）；内部的 = 内部编号 + 来源单位。用哪个由接口给的字段决定，前端不自己拼内部编号。
  2. 组内固定顺序：样本质控表 → 类器官质控表 → 类器官质量评分表；组间按组内最新完成时间倒序（这就是甲方说的「按时间找」）。
  3. 「合并预览」「合并下载」只在该样本已完成 ≥ 2 份时出现；每份文档一行右侧有「下载」——本张把按钮放上（点了先提示「即将开放」），弹层与真正的打开 / 发送到微信在 DOC-MP-002 接（甲方 2026-09-17 要求列表上直接能下，CR-20260917-04）。
  4. 合作单位看到的同样是三份（样本质控表、类器官质控表、类器官质量评分表），前端不按身份再挑文档类型。

## 1 背景与口径

会上 L166-L169：一个文件的列表，找到对应时间的那个 Word，点击之后可以预览。L226：中间的页签就是已经生成了结果的各种文档。

## 2 实现要点

- 后端 `org.dromara.lqg.doc.mp`：`GET /mp/int/doc/list`（内部：全部已完成且内部版渲染成功的；行带 `internalNo`、`sourceUnitName`），类级 `@SaCheckRole("lqg_internal")`。外部用 AUTH-EXT-003 的 `/mp/ext/doc/list`。
  两个接口的行形状对齐到：`{sampleId, title, subtitle, docKind, publishedTime, totalScore?}`——`title / subtitle` 由后端按身份给（外部：送检单号 / 掩码姓名；内部：内部编号 / 来源单位）。
- `pages/doc/index`（tab 页）：筛选（近一周 / 近一月 / 自定义；文档类型）→ `groupDocs(rows)`（`group.ts`，fixture 单测）→ `DocGroupCard`。空状态「结果出具后会显示在这里」。
  `DocGroupCard`：每份一行 = 文档名 + 完成时间 + 右侧「下载」小按钮（点这一行别处进预览）；组底「合并预览」「合并下载」两个按钮。
  页签文字与板块标题取自 `src/config/app.ts` 的 `DOC_TAB_NAME` / `DOC_SECTION_NAME`。
- 外部样本详情第三段「质控文档」接上，点条目进预览页（DOC-MP-002；本张先跳占位页）。

## 3 边界（明确不做）

- 不做预览与下载（DOC-MP-002）
- 不做搜索框（外部没有可搜的编号以外的东西；内部去工作台搜）
- 不做未读红点

## 4 完工报告要求

1. 内部、外部两种视角的文档页截图；空状态截图
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁
