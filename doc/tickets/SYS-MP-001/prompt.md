---
ticket: SYS-MP-001
track: SYS
phase: D1
size: M
req_refs:
  - REQ-SYS-001
  - REQ-SYS-002
  - REQ-SYS-018
  - REQ-AUTH-015
  - REQ-SYS-019
  - REQ-SYS-020
depends_on:
  - AUTH-LOGIN-001
touches:
  - code/miniapp/**
adr_refs:
  - ADR-0001
blueprint_refs:
  - UI:mp.login
  - UI:mp.home
  - UI:mp.home.entries
  - UI:mp.me
  - FLOW:F-MP-01.step1
  - FLOW:F-AUTH-01.step4
accept:
  - name: "产物是本次构建的、三个页签名称与顺序钉死、组件导入与选择器两条小程序专属坑已避开、调试登录入口没进生产构建"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
      jq -e '[.tabBar.list[].text] == ["首页","文档","我的"]' dist/build/mp-weixin/app.json &&
      ! grep -rnE "^import \{[^}]*\} from '@/components/biz'" src --include=*.vue --include=*.ts | grep -v 'import type' | grep -q . &&
      ! grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.vue --include=*.scss | grep -v '^src/uni.scss' | grep -q . &&
      ! grep -rq 'mock:ext' dist/build/mp-weixin
    counterfeit: |-
      页签顺序写成「首页 / 我的 / 文档」或把「文档」叫成「报告」→ jq 红。
      组件走桶口运行时导入（小程序里渲染成空白且不报错）→ 第 3 段红。
      调试用的身份选择器没用环境变量摇掉、进了生产包 → 最后一段红：等于在正式小程序里留了一个不用微信就能登录的入口。
      先 rm 产物目录再构建：上一次跑剩下的 dist 骗不过去。
  - name: "首页与「我的」只认后端给的身份：内部四个入口、外部三个（没有冻存），顺序 = 模板顺序；点哪个都是填写页；内部管理板块只给内部；缺失或未知身份什么都不渲染（绝不默认当内部）；首页没有任何计数；内部管理板块底部小字 = 「核验、冻存取用请到网页工作台」（CR-20260918-07：表格页有修改入口了，这行不再写「修改」）"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/home-entries-cases.json' src/pages/index/entries.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/index/entries.fixture.spec.ts &&
      pnpm vitest run src/pages/index/entries.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-entries.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 22' /tmp/lqg-entries.json &&
      grep -q 'targetCases' src/pages/index/entries.fixture.spec.ts && grep -q 'meCases' src/pages/index/entries.fixture.spec.ts &&
      node -e "const f=require('../../doc/verify/fixtures/home-entries-cases.json'); const ext=f.cases.find(x=>x.identity==='external'); if(f.cases.length!==6||f.cases.filter(x=>x.expect.length===0).length!==4||!ext||ext.expect.includes('cryo')||ext.expect.length!==3||f.targetCases.length!==10||f.targetCases.filter(x=>x.expect===null).length!==3||f.meCases.length!==6||f.meCases.filter(x=>x.expect.includes('internalAdmin')).length!==1) process.exit(1)" &&
      ! grep -nE 'wd-badge|is-dot|角标|StatTile|pendingSamples|todayNew|cryoOverdue|recent' src/pages/index/index.vue &&
      grep -q '核验、冻存取用请到网页工作台' src/pages/me/index.vue &&
      ! grep -q '修改、核验、冻存取用请到网页工作台' src/pages/me/index.vue
    counterfeit: |-
      entriesFor 写成 identity === 'external' ? 三张 : 全部四张 → identity 为空 / 'admin' / 'INTERNAL' 三条用例红：这正是「拿不到身份时被当成内部」的事故形态。
      外部入口清单里混进了 cryo → 「外部」用例红，node 那段也红：冻存对外不可见。
      meSections 写成 identity !== 'external' 就给内部管理 → 空身份 / 'admin' / 'INTERNAL' 三条红：身份没取到的人会看到内部管理入口。
      entryTarget 把内部入口指到表格页 → targetCases 期望填写页红（Kevin 定的是点进去都是编辑）。
      首页还留着方案 B 的数字、最近记录，或入口上挂了红点角标 → 最后一段红（身份徽标用 .lqg-tag 或 wd-tag，都不算角标）。
      spec 自己写期望值而不读 fixture → 第 1 段 grep 红；把不过的用例标 skip → 第 2 段红。
      删 fixture 里期望为空的病灶用例来过关 → node 那段红。
      底部小字照抄 9-17 的老版「修改、核验、冻存取用请到网页工作台」→ 最后一段红（老串把新串整个包在里面，所以这里要正反两条 grep）：⑩ 表格页 9-18 起有「修改」入口了（CR-20260918-07），这行小字再把人支到工作台去就是错的。
  - name: "方向 A 设计 token 落地：src/style/tokens.scss 与 doc 里的规范逐变量一致、全局引入 tokens 与范式类、页面与组件里没有色值字面量（CR-20260921-08）"
    form: API
    run: |-
      cd code/miniapp &&
      A=$(grep -oE -- '--(lqg|wot)-[a-z0-9-]+' ../../doc/design-options/direction-a/tokens.scss | sort -u) &&
      B=$(grep -oE -- '--(lqg|wot)-[a-z0-9-]+' src/style/tokens.scss | sort -u) &&
      [ -n "$B" ] && [ "$A" = "$B" ] &&
      grep -qE "@import ['\"]\./tokens(\.scss)?['\"]" src/style/index.scss &&
      grep -qE "@import ['\"]\./components(\.scss)?['\"]" src/style/index.scss &&
      grep -q -- '--wot-color-theme' src/style/tokens.scss &&
      ! grep -rnE '(color|background|border|fill|stroke|shadow)[^;{]*#[0-9a-fA-F]{3,8}\b' src/pages src/components --include=*.vue --include=*.scss 2>/dev/null | grep -vE 'navigationBar[A-Za-z]*Color|backgroundColor(Top|Bottom)?["'\'' ]*:' | grep -q .
    counterfeit: |-
      token 还照旧口径写在 src/uni.scss、或者 src/style/tokens.scss 少了阴影与圆角那几组变量 → 两个变量集合不相等，红：D1 就把「不用阴影、圆角 10 / 9 / 5」的旧外观焊进了壳子。
      tokens.scss 拷了但没在 index.scss 里引 → 两条 @import grep 红：变量不生效，wd-* 还是 wot 默认蓝。
      漏了 --wot-* 映射段 → 集合不等且 --wot-color-theme grep 红：wd-button 仍是 #4d80f0。
      页面里图省事写 color: #0E7C7B 或 background: #fff → 最后一段红：组件内零颜色字面量是硬约束，换肤时这些地方会漏。
      （页面 route 块里的 navigationBarBackgroundColor、backgroundColor 是小程序页面配置不是样式，已排除，不算违规。）
---

# SYS-MP-001 · 小程序壳：三个页签、登录页、请求层、首页（内外部同一个样子，点表就是填写）、我的（历史编辑记录入口与内部管理板块）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-LOGIN-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 首页与「我的」按 Kevin 2026-09-17 晚定的结构做（CR-20260917-05）：看图 `doc/design-options/gallery.html#mp-home-final`（内部首页）、`#mp-home-final-ext`（外部首页）、`#mp-me-int`（内部的「我的」）、`#mp-me-ext`（外部的「我的」）；口径以 `UI:mp.home`、`UI:mp.home.entries`、`UI:mp.me` 为准。**方案 B 顶部的数字摘要已作废，别照着做**。
    `#mp-me-int` 图上内部管理板块底部那行小字还是 9-17 的老版「修改、核验、冻存取用请到网页工作台」，**别照抄**：以 `UI:mp.me` 为准写成「核验、冻存取用请到网页工作台」（CR-20260918-07）
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` 全文，看图 `doc/design-options/directions/设计方向对比.html` 方向 A 的首页。上面那几张图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**。组件职责仍见 `design-authority.md` §C；组件内零颜色字面量
  - 栈包 gotchas §6：biz 组件必须直接 `.vue` 路径导入；wxss 不支持 `:not()` / `*`；业务页只用 `wd-*` 组件
  - **ADR-0001**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0001` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **首页与「我的」只认后端 `/mp/me` 的 `identity`**。identity 缺失、为空、是不认识的值 → 一个入口都不渲染、「我的」里也不出历史编辑记录与内部管理，**绝不默认当内部**。
  2. **首页内部外部同一个样子**：没有数字摘要、没有最近记录（Kevin：「首页顶部数字去掉，不合理」）。内部四格，外部三格（没有 -80 冻存记录），三格时第三格横向占满整行。
  3. **点哪格都是进该表的填写页新增一条**，内部外部一样；填写页在各域的 MP ticket 里，本张先放占位页。
  4. **「内部管理」板块只给内部人员**，外部不渲染（不是置灰）。板块里四个入口进表格页（SAMPLE-MP-002 起建，本张先放占位页）。「历史编辑记录」所有人都有（页面在 SAMPLE-MP-001 起建）。
     板块底部那行小字是**「核验、冻存取用请到网页工作台」**——9-18 起表格页自己有修改入口了，这行不再写「修改」（CR-20260918-07）。逐字照抄，别自己改措辞。
  5. 三个页签固定为「首页 / 文档 / 我的」，顺序不能变；文档页签本张只放空状态，内容在 DOC-MP-001。
  6. 没有「内部登录 / 外部登录」两个入口，没有账号密码框。

## 1 背景与口径

会上定的三个页签（逐字稿 L193-L229）：首页就是四张表的输入，中间是文档，最后是我的（用户信息、电话、设置之类）。
2026-09-17 晚 Kevin 定（CR-20260917-05）：首页内部外部都一样，内部四张表、外部三张（不显示 -80 冻存记录），点进去都是编辑；顶部数字去掉；
内部人员的「我的」多一个「内部管理」板块（网页工作台的部分查看、筛选、导出能力）；所有人都在「我的」看历史编辑记录。

## 2 实现要点

- `code/miniapp/`：unibest 模板（uni-app + Vue3 + wot-design-uni + pinia）。页面用文件路由生成 `pages.json`，别手改产物。
- 设计 token 与范式类（CR-20260921-08 方向 A）：把 `doc/design-options/direction-a/tokens.scss`、`components.scss` **原样**拷到 `src/style/`，在 `src/style/index.scss` 里先引 tokens 再引 components，全局引一次；`src/uni.scss` 保持模板默认、不写 token。全局导航栏白底黑字、页面底 `#F3F6F6`（写在 unibest 的 `pages.config.ts` 的 `globalStyle`，由它生成 pages.json，别手改产物）。首页宫格、我的分组卡、底部栏按落地规范 §5.2、§5.3、§5.8 用 `.lqg-*` 类；编号类文本统一用 `.lqg-mono`。
- 请求层 `src/utils/request.ts`：自动带 `Authorization` 与 `clientid`；业务码 401 → 清 token 跳登录页；其余非 200 → toast `msg`。
- 登录页 `pages/login/index`：协议勾选 + 一个按钮（`open-type="getPhoneNumber"`）。流程：`wx.login` 拿 code → 按钮回调拿 phoneCode → `POST /auth/login`（grantType=xcx）→ 存 token → `GET /mp/me` 入 store → 进首页。
  拒绝授权手机号：停在本页，提示「需要手机号才能送检和查看结果」。开发者工具里 `VITE_MOCK_LOGIN=1` 时提供一个调试入口选 seed 身份（只在 dev 构建里存在，生产构建里这段代码必须被摇掉）。
- 首页 `pages/index/index` 自上而下：问候行（姓名 + 身份徽标；外部多一行单位·组别·核验状态）→「填写」`EntryTile` 宫格（内部 2×2；外部三格、第三格占满整行；入口上不放角标）。
- 我的 `pages/me/index`：姓名、手机号掩码、身份徽标 →「历史编辑记录」一行（所有人）→（外部）单位与组别一行（本张只展示 `/mp/me` 返回的值，修改页在 AUTH-GROUP-001）
  →（内部）「内部管理」板块：四个入口 + 底部小字「核验、冻存取用请到网页工作台」（CR-20260918-07：表格页加了修改入口，小字去掉「修改」二字）→ 协议入口、退出登录。
- 三个纯函数放 `src/pages/index/entries.ts`：`entriesFor(identity)`、`entryTarget(identity, key)`、`meSections(identity)`；写一份 `entries.fixture.spec.ts`，
  **三组用例与期望都从 `doc/verify/fixtures/home-entries-cases.json` 读**（`cases`、`targetCases`、`meCases`），不许在 spec 里另写期望值。
  入口与板块点进去的目标页此刻都可以是占位页（填写页在各域 MP ticket，历史编辑记录在 SAMPLE-MP-001，表格页在 SAMPLE-MP-002）。
- 《用户协议》《隐私政策》两个静态页先放占位标题，正文在 SYS-RELEASE-001。

## 3 边界（明确不做）

- 不做任何一张表的填写页、表格页、历史编辑记录页的内容（各域 MP ticket）
- 不做文档页签的内容（DOC-MP-001）
- 不做单位与组别的修改页（AUTH-GROUP-001）
- 不做消息中心、不做设置项列表、不做深色主题
- 首页不做任何计数、不做最近记录
- 不上传体验版（SYS-STAGING-001）

## 4 完工报告要求

1. 微信开发者工具里（外观对照方向 A：白卡柔阴影、圆角 14、青绿主按钮带辉光）三个页签、登录页、内部首页（2×2）、外部首页（三格）、内部的「我的」（有内部管理板块）、外部的「我的」（没有）的截图各一张；点四格各自跳到哪（占位页标题即可）的录屏
2. 生产构建产物里搜不到调试登录入口的证据（grep 输出）
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁
