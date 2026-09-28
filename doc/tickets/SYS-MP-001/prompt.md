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
  - UI:mp.home.todo
  - UI:mp.home.entries
  - UI:mp.me
  - FLOW:F-MP-01.step1
  - FLOW:F-AUTH-01.step4
accept:
  - name: "产物是本次构建的、三个页签名称与顺序钉死、组件导入与选择器两条小程序专属坑已避开、调试登录入口没进生产构建（mock 身份、openid、测试手机号一个都不在包里）；根组件 App.ku 在包里、没有以开发机路径命名的 chunk（CR-20260923-09）"
    form: API
    run: |-
      cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null &&
      jq -e '[.tabBar.list[].text] == ["首页","文档","我的"]' dist/build/mp-weixin/app.json &&
      ! grep -rnE "^import \{[^}]*\} from '@/components/biz'" src --include=*.vue --include=*.ts | grep -v 'import type' | grep -q . &&
      ! grep -rnE ':not\(|(^|[ ,{])\*[ ,{]' src --include=*.vue --include=*.scss | grep -v '^src/uni.scss' | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|/\*|\*/|\*[[:space:]]+[^{[:space:]]|\*[[:space:]]*$)' | grep -q . &&
      ! grep -rqE 'mock:|mock-openid-|1380000[0-9]{4}' dist/build/mp-weixin &&
      test -f dist/build/mp-weixin/App.ku.js && test -f dist/build/mp-weixin/App.ku.wxss && grep -q '<slot' dist/build/mp-weixin/App.ku.wxml &&
      ! ls dist/build/mp-weixin | grep -qE '^L3[A-Za-z0-9+/=_-]{20,}'
    counterfeit: |-
      页签顺序写成「首页 / 我的 / 文档」或把「文档」叫成「报告」→ jq 红。
      组件走桶口运行时导入（小程序里渲染成空白且不报错）→ 第 3 段红。
      第 4 段的注释过滤只跳过真正的注释行（//、/*、*/、「* 」后面不是 { 的续行、单独的 *）：原写法把行首是 * 的行一律滤掉，行首的 `* {` 通配选择器也一起被放过了（2026-09-23 改写时用样例行自测过：`a.scss:3:* {`、`a.scss:11:  *, *::before {` 保留；`a.vue:5: * 说明`、`a.vue:6: */`、`a.ts:7: // x`、单独的 ` *` 滤掉）。页面样式里写了 `* { … }` → 第 4 段红。
      调试用的身份面板（身份清单、面板文案、`mock:` 拼接、`mock-openid-`、1380000xxxx 测试手机号）没挂在构建期常量上、进了生产包 → 第 5 段红：等于在正式小程序里留了一个不用微信就能登录的入口。原来只查 `mock:ext`，`mock:staff`、`mock:newbie…`、`mock-openid-*` 与 1380000xxxx 测试手机号都漏得过去（CR-20260923-09）。
      App.ku.vue 里又挂回 wd-toast / wd-message-box 这类 wd-* 组件 → mp-weixin 构建把根组件的注册代码打进一个以开发机绝对路径 base64 命名（L3… 开头）的共享 chunk，App.ku.js / App.ku.wxss 从包里消失，每页报一次 global-ku-root 组件缺失 → 第 6、7 段红（CR-20260923-09，V12）。
      App.ku.vue 的注释里写了根视图标签原文 → 插件只换全文件第一处，换到注释里去了，App.ku.wxml 里没有 <slot → 第 6 段红：页面内容投不进来。
      先 rm 产物目录再构建：上一次跑剩下的 dist 骗不过去。
  - name: "首页与「我的」只认后端给的身份：内部四个入口、外部三个（没有冻存），顺序 = 模板顺序；点哪个都是填写页；内部管理板块只给内部；缺失或未知身份什么都不渲染（绝不默认当内部）；外部首页没有任何计数，内部首页只有「待处理」三项（只给 internal、三项顺序与去向由 fixture 钉死，CR-20260924-10）；内部管理板块底部小字 = 「核验、冻存登记在小程序和网页工作台都能做」（CR-20260924-10；不再把人支到工作台，也没有「修改」二字）"
    form: STATE
    run: |-
      cd code/miniapp &&
      grep -q 'doc/verify/fixtures/home-entries-cases.json' src/pages/index/entries.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/index/entries.fixture.spec.ts &&
      pnpm vitest run src/pages/index/entries.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-entries.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 22' /tmp/lqg-entries.json &&
      grep -q 'targetCases' src/pages/index/entries.fixture.spec.ts && grep -q 'meCases' src/pages/index/entries.fixture.spec.ts &&
      node -e "const f=require('../../doc/verify/fixtures/home-entries-cases.json'); const ext=f.cases.find(x=>x.identity==='external'); if(f.cases.length!==6||f.cases.filter(x=>x.expect.length===0).length!==4||!ext||ext.expect.includes('cryo')||ext.expect.length!==3||f.targetCases.length!==10||f.targetCases.filter(x=>x.expect===null).length!==3||f.meCases.length!==6||f.meCases.filter(x=>x.expect.includes('internalAdmin')).length!==1) process.exit(1)" &&
      ! grep -nE 'wd-badge|is-dot|角标|StatTile|todayNew|recent' src/pages/index/index.vue &&
      grep -q '@/components/biz/TodoCard.vue' src/pages/index/index.vue && grep -q 'showTodo' src/pages/index/index.vue &&
      grep -q 'fixtures/home-todo-cases.json' src/pages/index/todo.fixture.spec.ts &&
      ! grep -nE '\.(skip|todo|only)\(' src/pages/index/todo.fixture.spec.ts &&
      pnpm vitest run src/pages/index/todo.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-home-todo.json >/dev/null &&
      jq -e '.numFailedTests == 0 and .numPassedTests >= 17' /tmp/lqg-home-todo.json &&
      node -e "const f=require('../../doc/verify/fixtures/home-todo-cases.json'); const on=f.showCases.filter(x=>x.expect===true); if(on.length!==1||on[0].identity!=='internal'||f.showCases.length<6||JSON.stringify(Object.keys(f.targets))!==JSON.stringify(['pendingSamples','pendingEmbeds','cryoOverdue'])||!String(f.targets.pendingSamples).startsWith('/pages/verify/index')||!String(f.targets.pendingEmbeds).startsWith('/pages/verify/index')||!String(f.targets.cryoOverdue).includes('sheet=cryo&tab=overdue')) process.exit(1)" &&
      grep -q "INTERNAL_ADMIN_NOTE = '核验、冻存登记在小程序和网页工作台都能做'" src/pages/index/entries.ts && ! grep -q '请到网页工作台' src/pages/index/entries.ts &&
      grep -q 'INTERNAL_ADMIN_NOTE' src/pages/me/index.vue && ! grep -q '请到网页工作台' src/pages/me/index.vue
    counterfeit: |-
      entriesFor 写成 identity === 'external' ? 三张 : 全部四张 → identity 为空 / 'admin' / 'INTERNAL' 三条用例红：这正是「拿不到身份时被当成内部」的事故形态。
      外部入口清单里混进了 cryo → 「外部」用例红，node 那段也红：冻存对外不可见。
      meSections 写成 identity !== 'external' 就给内部管理 → 空身份 / 'admin' / 'INTERNAL' 三条红：身份没取到的人会看到内部管理入口。
      entryTarget 把内部入口指到表格页 → targetCases 期望填写页红（Kevin 定的是点进去都是编辑）。
      首页还留着方案 B 的数字摘要（StatTile / 今日新增）、最近记录，或入口上挂了红点角标 → `! grep` 那段红（身份徽标用 .lqg-tag 或 wd-tag，都不算角标）。「待处理」是 CR-20260924-10 甲方要的内部待办块，数字映射在 `todo.ts`，不算方案 B 的数字摘要。
      「待处理」给外部也显示、或身份缺失 / 'admin' / 'INTERNAL' 时显示（又一次「拿不到身份当内部」）→ todo.fixture.spec 的 showCases 用例红，node 那段「只有 internal 为 true」也红。
      点「待核验样本」跳进表格页而不是待核验列表、或 -80 超期没带 tab=overdue（落在「全部」页签）→ node 那段 targets 红。读了拆出的 pendingTissue / pendingOrganoid 而不是 pendingSamples → itemCases「接口多给的数不读」用例红。
      删 fixture 用例凑过关 → 17 例不够数红。
      spec 自己写期望值而不读 fixture → 第 1 段 grep 红；把不过的用例标 skip → 第 2 段红。
      删 fixture 里期望为空的病灶用例来过关 → node 那段红。
      底部小字照抄 9-17 的老版「修改、核验、冻存取用请到网页工作台」或 9-18 那版「核验、冻存取用请到网页工作台」→ 最后两段红：CR-20260924-10 起核验与冻存登记小程序里也能做，这行小字再把人支到工作台去就是错的。小字只在 entries.ts 写一份，me 页引用它，别在 me 页再写一份字面量。
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

# SYS-MP-001 · 小程序壳：三个页签、登录页、请求层、首页（点表就是填写；内部多一块「待处理」，CR-20260924-10）、我的（历史编辑记录入口与内部管理板块）

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-LOGIN-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 首页与「我的」按 Kevin 2026-09-17 晚定的结构做（CR-20260917-05）：看图 `doc/design-options/gallery.html#mp-home-final`（内部首页）、`#mp-home-final-ext`（外部首页）、`#mp-me-int`（内部的「我的」）、`#mp-me-ext`（外部的「我的」）；口径以 `UI:mp.home`、`UI:mp.home.entries`、`UI:mp.me` 为准。**方案 B 顶部的数字摘要已作废，别照着做**。
    `#mp-me-int` 图上内部管理板块底部那行小字还是 9-17 的老版「修改、核验、冻存取用请到网页工作台」，**别照抄**：以 `UI:mp.me` 为准写成「核验、冻存登记在小程序和网页工作台都能做」（CR-20260924-10；9-18 那版「核验、冻存取用请到网页工作台」也已作废）
  - **CR-20260924-10（以它为准）**：甲方要求「内部人员的小程序首页和网页台首页都需要显示待核验、冻存超期这些需要处理的事」，推翻 CR-20260917-05 的「首页不放数字」：内部首页在问候行与宫格之间加「待处理」（`UI:mp.home.todo`），外部首页不变；「我的」内部管理板块小字改新文案。图廊没有这一块的帧，以系统为准
  - **视觉按方向 A**（CR-20260921-08）：`doc/design-options/direction-a/落地规范.md` 全文，看图 `doc/design-options/directions/设计方向对比.html` 方向 A 的首页。上面那几张图廊帧画于方向 A 之前，**只取内容块与排布，不取它的无阴影小圆角外观**。组件职责仍见 `design-authority.md` §C；组件内零颜色字面量
  - 栈包 gotchas §6：biz 组件必须直接 `.vue` 路径导入；wxss 不支持 `:not()` / `*`；业务页只用 `wd-*` 组件
  - **ADR-0001**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0001` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **首页与「我的」只认后端 `/mp/me` 的 `identity`**。identity 缺失、为空、是不认识的值 → 一个入口都不渲染、「我的」里也不出历史编辑记录与内部管理，**绝不默认当内部**。
  2. **首页**：没有方案 B 的数字摘要、没有最近记录（Kevin：「首页顶部数字去掉，不合理」）。内部四格，外部三格（没有 -80 冻存记录），三格时第三格横向占满整行。
     **内部多一块「待处理」**（CR-20260924-10）：只给 identity=internal；三项固定顺序「待核验样本 / 待核验石蜡包埋送样 / -80 超期未转液氮」，数取 `GET /lqg/home/todo` 的 `pendingSamples / pendingEmbeds / cryoOverdue`（与工作台首页同一次现算，拆出的 `pendingTissue / pendingOrganoid` 不读）；0 的项弱化不隐藏，三项全 0 只剩一行「暂无待处理」；每次 onShow 重取，失败只显示一行「待处理没能加载，点一下重试」、不影响宫格；点前两项进待核验列表（`/pages/verify/index?tab=tissue|embed`），点 -80 超期进 `/pages/ledger/index?sheet=cryo&tab=overdue`。规则与期望在 `doc/verify/fixtures/home-todo-cases.json`。
  3. **点哪格都是进该表的填写页新增一条**，内部外部一样；填写页在各域的 MP ticket 里，本张先放占位页。
  4. **「内部管理」板块只给内部人员**，外部不渲染（不是置灰）。板块里四个入口进表格页（SAMPLE-MP-002 起建，本张先放占位页）。「历史编辑记录」所有人都有（页面在 SAMPLE-MP-001 起建）。
     板块底部那行小字是**「核验、冻存登记在小程序和网页工作台都能做」**（CR-20260924-10；写在 `entries.ts#INTERNAL_ADMIN_NOTE` 一处，me 页引用）——没有「修改」二字（CR-20260918-07），也不再把人支到工作台。逐字照抄，别自己改措辞。
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
  拒绝授权手机号：停在本页，提示「需要手机号才能送检和查看结果」。开发者工具里 `VITE_MOCK_LOGIN=1` 时提供一个调试入口选 seed 身份（只在 dev 构建里存在，生产构建里这段代码必须被摇掉：身份清单、面板文案、`mock:` 拼接全部挂在构建期常量 `__LQG_MOCK_LOGIN__` 上，CR-20260923-09）。
- `src/App.ku.vue`（@uni-ku/root 的根组件外壳）模板里只放根视图那一个标签，**别挂任何 wd-* 组件**（挂了 mp-weixin 构建会把 App.ku 打进以开发机路径命名的共享 chunk、从包里消失）；注释里**别写根视图标签原文**（插件只换全文件第一处）。两条都是 CR-20260923-09 修复时踩实的，accept 1 最后两段钉住。
- 首页 `pages/index/index` 自上而下：问候行（姓名 + 身份徽标；外部多一行单位·组别·核验状态）→（仅内部）「待处理」`components/biz/TodoCard.vue`（纯函数 `src/pages/index/todo.ts` 的 `showTodo / todoItems / todoAllClear / TODO_TARGET`，`todo.fixture.spec.ts` 读 `home-todo-cases.json`；接口 `src/api/verify.ts#fetchHomeTodo`，`silent` 不弹 toast；CR-20260924-10）→「填写」`EntryTile` 宫格（内部 2×2；外部三格、第三格占满整行；入口上不放角标）。
- 我的 `pages/me/index`：页头（CR-20260924-11 重做：身份色浅渐变底、实心圆头像、姓名 + 身份徽标、手机号掩码，下一行所属单位——内部 = 中心名称 `config/legal.ts#LEGAL_OPERATOR`，合作单位 = 单位 · 组别）→「历史编辑记录」一行（所有人）→（外部）「修改单位与组别」一行，右侧核验状态（本张只展示 `/mp/me` 返回的值，修改页在 AUTH-GROUP-001）
  →（内部）「内部管理」板块：四个入口 + 底部小字「核验、冻存登记在小程序和网页工作台都能做」（CR-20260924-10）→「协议与隐私」分组、退出登录。
  图标（CR-20260924-11）：首页宫格与「我的」各行一律用 `components/ui/LineIcon.vue` 那套线性图标（SVG 遮罩、颜色随文字，不用在线字体图标），取代原来的单字方块；根节点类名别用 `me` 这类短词（会撞 UnoCSS 工具类，右侧多出 16px 留白的根因）。
- 三个纯函数放 `src/pages/index/entries.ts`：`entriesFor(identity)`、`entryTarget(identity, key)`、`meSections(identity)`；写一份 `entries.fixture.spec.ts`，
  **三组用例与期望都从 `doc/verify/fixtures/home-entries-cases.json` 读**（`cases`、`targetCases`、`meCases`），不许在 spec 里另写期望值。
  入口与板块点进去的目标页此刻都可以是占位页（填写页在各域 MP ticket，历史编辑记录在 SAMPLE-MP-001，表格页在 SAMPLE-MP-002）。
- 《用户协议》《隐私政策》：正文已落地（SYS-RELEASE-001；CR-20260923-09 修复时补成真实通用正文：`src/pages/legal/agreement.vue`、`privacy.vue`，共用 `src/components/ui/LegalArticle.vue`，主体名称与联系方式集中在 `src/config/legal.ts`）。原「本张先放占位标题」的说法作废，别再改回占位。

## 3 边界（明确不做）

- 不做任何一张表的填写页、表格页、历史编辑记录页的内容（各域 MP ticket）
- 不做文档页签的内容（DOC-MP-001）
- 不做单位与组别的修改页（AUTH-GROUP-001）
- 不做消息中心、不做设置项列表、不做深色主题
- 外部首页不做任何计数；两边都不做最近记录；内部首页只有「待处理」三项，不做别的计数（CR-20260924-10）
- 不上传体验版（SYS-STAGING-001）

## 4 完工报告要求

1. 微信开发者工具里（外观对照方向 A：白卡柔阴影、圆角 14、青绿主按钮带辉光）三个页签、登录页、内部首页（2×2）、外部首页（三格）、内部的「我的」（有内部管理板块）、外部的「我的」（没有）的截图各一张；点四格各自跳到哪（占位页标题即可）的录屏
2. 生产构建产物里搜不到调试登录入口的证据（grep 输出）
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 1 的调试入口检查从只查 `mock:ext` 扩成「mock: / mock-openid- / 1380000xxxx 都不在生产包」，补 App.ku 四件套与 L3… 长文件名两段（F3），选择器检查的注释行过滤改成只跳过真正的注释行（行首 `* {` 不再被放过）；§2 协议页「先放占位」作废，补 App.ku.vue 的两条约束。
- 2026-09-24 按 CR-20260924-10 更新：内部首页加「待处理」、我的页小字换新文案——accept 2 的首页禁字去掉 `pendingSamples|cryoOverdue`（数字映射在 todo.ts，意图改为「没有方案 B 的数字摘要」），补 TodoCard / showTodo、todo.fixture.spec ≥ 17 例、node 钉住 fixture（只 internal 显示、三项去向），小字断言改为 entries.ts 里的新文案且 me 页引用它、两处都不再有「请到网页工作台」；blueprint_refs 补 UI:mp.home.todo；§0 / §2 / §3 按新口径改写。
- 2026-09-24 按 CR-20260924-11 更新：§2「我的」页改写页头、「修改单位与组别」、「协议与隐私」分组，补线性图标与根节点类名的约束。accept 不动（H 批已在隔离环境重放 3/3 绿）。
