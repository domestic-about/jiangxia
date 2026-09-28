---
ticket: SYS-WEB-001
track: SYS
phase: D1
size: M
req_refs:
  - REQ-SYS-003
depends_on:
  - SYS-BASE-001
touches:
  - code/plus-ui/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921083*__SYS-WEB-001-*.sql
adr_refs:
  - ADR-0001
blueprint_refs:
  - UI:admin.home
  - UI:admin.config
  - FLOW:F-OPS-01.step1
accept:
  - name: "管理员拿得到字典管理与参数设置且路由可达、path 与 component 非空（参数设置是 CR-20260918-07 两个开关的落点，见 UI:admin.config）；内部人员角色拿不到任何系统管理菜单（上传质控图片要用的 system:oss 三个按钮权限 1600–1602 除外，D6 DOC-RENDER-001 授的）"
    form: MENU
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "system/dict/index")] | length == 1 and (.[0].path | length > 0)' &&
      bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "system/config/index")] | length == 1 and (.[0].path | length > 0)' &&
      bash doc/verify/api.sh --as admin GET '/system/config/configKey/lqg.ext.show-internal-no' | jq -e '.msg == "false" or .data == "false"' &&
      bash doc/verify/api.sh --as admin GET '/system/config/configKey/lqg.cryo.overdue-days' | jq -e '.msg == "14" or .data == "14"' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE rm.role_id = 101 AND m.component = 'system/dict/index' AND m.path <> ''" --eq 1 &&
      python3 doc/verify/db.py --sql "SELECT m.menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE rm.role_id IN (101,102) AND m.menu_type IN ('M','C') AND (m.path IS NULL OR m.path = '')" --empty &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_role_menu WHERE role_id = 102 AND menu_id < 5000 AND menu_id NOT IN (1600, 1601, 1602)" --eq 0
    counterfeit: |-
      只在 sys_role_menu 里插了行、menu_id 却猜错（指到不存在的菜单）→ getRouters 里找不到 system/dict/index 红。
      图省事把整个「系统管理」目录授给 lqg_admin 不会让本条红——但完工报告的侧边栏截图会露馅；QA 门按截图判。
      给内部人员角色顺手授了用户管理 → 最后一段红（内部人员能改别人密码）。
      （2026-09-23 改写，a2 重放发现）最后一段原来断 102 在 5000 以下一个都没有；D6 的 DOC-RENDER-001 为内部人员在质控页上传图片，把 1600–1602（system:oss:query / upload / download 三个 F 按钮，挂在文件管理下、没有页面）授给了 102（功能性授权，见 V202609261410 的注释），原断言按构造永远红。现在只把这三个按钮点名排除，5000 以下其余的一个都不许有。没改成「只断 102 看不到系统管理目录」：那样只挡目录与页面，漏掉单授 F 按钮——例如只授 system:user:resetPwd 不授页面，内部人员照样能调接口改别人密码。
      参数设置菜单没授给管理员、或那两行 sys_config 没插（CR-20260918-07：内部编号开关与冻存超期天数）→ 中间三段红：甲方要能自己开关，进不去这一页就等于没这个功能。
      第 4 段防空 path：空 path 会让 plus-ui 的路由整体崩掉，前面先有第 3 段的正向存在性断言垫底，--empty 不会在零行上空转。
  - name: "前端生产构建通过且是本次产物；品牌已换、上游的多租户字样与注册入口已清；i18n 按域拆分的约定已立；产物里没有上游的 Gitee 仓库与文档外链，包管理器版本钉在 pnpm@10.33.0（CR-20260923-09）"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null &&
      grep -q '类器官样本管理' dist/index.html &&
      ! grep -rlE '多租户管理系统|RuoYi-Vue-Plus多租户' dist | grep -q . &&
      test -f src/lang/lqg/sys.zh_CN.ts && test -f src/lang/lqg/sys.en_US.ts &&
      grep -q "import.meta.glob" src/lang/index.ts &&
      grep -q -- '--el-color-primary' src/assets/styles/lqg-tokens.scss && grep -qi '0E7C7B' src/assets/styles/lqg-tokens.scss &&
      ! grep -rlE 'gitee\.com/dromara|plus-doc\.dromara' dist | grep -q . &&
      grep -q '"packageManager": "pnpm@10.33.0"' package.json &&
      cd ../.. && node doc/waves/regression/V-round/workbench-behavior-dom.mjs --only=token
    counterfeit: |-
      只改了 .env 里的标题、登录页上还印着上游的「多租户管理系统」→ 第 3 段红。
      i18n 还是往 zh_CN.ts 大文件里加 → 第 4、5 段红；后面每张 WEB ticket 都会在这个文件上冲突。
      真门是 build 不是类型检查：vue-tsc 过了但 vite 构建挂了 → 第 1 段红。先 rm dist，旧产物骗不过去。
      导航栏上还挂着上游的 RuoYiGit / RuoYiDoc 两个外链按钮（或通知页里的 gitee 链接）→ 产物里搜得到 gitee.com/dromara 或 plus-doc.dromara，第 7 段红：甲方的工作台上挂着上游项目的源码与文档入口（CR-20260923-09）。组件文件本身还在 src/components 里不算违规，没人引用就不进产物。
      package.json 的 packageManager 不是 pnpm@10.33.0（与 lockfile 生成时的版本不一致）→ 最后一段红：换一台机器 pnpm install 可能解析出另一棵依赖树。
      lockfile 入库（`git ls-files --error-unmatch code/plus-ui/pnpm-lock.yaml`）要等这批修复提交之后才会绿，所以不放进 run；提交后在完工报告里贴这条命令的输出（CR-20260923-09：工作台 lockfile 以前被 .gitignore 忽略，改为入库）。
---

# SYS-WEB-001 · 网页工作台壳：plus-ui 接后端、品牌与主色、按域拆分的 i18n、管理员可用的字典管理

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SYS-BASE-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - `doc/lint-profile.yaml`：菜单号段（SYS 5000-5099）与角色 id
  - `doc/design-options/design-authority.md` §B：主色 `#0E7C7B`，只做浅色一套
  - 栈包 gotchas §7：字典走 `useDict`，不 per-page 拉、不硬编码 option
  - **ADR-0001**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0001` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **i18n 不往上游的 `zh_CN.ts` / `en_US.ts` 两个大文件里加 key**——那是所有域 ticket 的共享文件，并行时必撞。本张立约定：`src/lang/lqg/<域>.zh_CN.ts` / `.en_US.ts`，由入口 glob 自动合并。
  2. lqg_admin 需要「字典管理」菜单（联想词字典由实验室自己维护，REQ-SAMPLE-901），但**不要**把整个「系统管理」都授给它。

## 1 背景与口径

合同技术架构：网页工作台 Vue3 + Element Plus（= 若依的 plus-ui）。本张只立壳和约定，业务页面由各域的 WEB ticket 往里加。
工作台只给内部人员用（REQ-AUTH-010 的后端拒绝在 AUTH-STAFF-001），本张不涉及。

## 2 实现要点

- `code/plus-ui/`：上游 plus-ui，与后端版本配套。`.env.development` 指向本地后端；`VITE_APP_CLIENT_ID` = pc client。
- 品牌：系统名「类器官样本管理」（标题、登录页、侧边栏 logo 文字）；登录页去掉租户下拉与「注册」入口；去掉上游的演示首页内容，首页先放一句占位（内容在 SYS-HOME-001）。
- 主色：`src/assets/styles/lqg-tokens.scss` 定义 `design-authority.md` §B 的 `--lqg-*` 变量，并把 Element Plus 的 `--el-color-primary` 系列映射到 `#0E7C7B`。后续业务页**零颜色字面量**。
- i18n 约定：`src/lang/lqg/` 目录 + 在 `src/lang/index.ts` 用 `import.meta.glob('./lqg/*.zh_CN.ts', { eager: true })` 合并到 `lqg.<域>.*` 命名空间；放一个 `sys.zh_CN.ts` / `sys.en_US.ts` 作样例。
- 业务页目录约定：`src/views/lqg/<域>/`、`src/api/lqg/<域>/`（本张各建一个 `.gitkeep`）。
- 迁移 `V202609210830__SYS-WEB-001-admin-dict-menu.sql`：给角色 101（lqg_admin）授上游「字典管理」菜单及其按钮（按上游 `sys_menu` 里 `component='system/dict/index'` 那一条及其子按钮的 menu_id 取，别猜号）。
  给 102（lqg_internal）**不授**任何系统管理菜单。

## 3 边界（明确不做）

- 不做任何业务页面、不 seed 任何业务菜单（各域 WEB ticket 在自己的号段里 seed）
- 不做工作台首页待办（SYS-HOME-001）
- 不做「只有内部能登录工作台」的后端拒绝（AUTH-STAFF-001）
- 不做深色主题、不改上游布局组件

## 4 完工报告要求

1. 登录页与首页截图；主色生效的证据（按钮颜色）
2. lqg_admin 登录后侧边栏截图：应只有「字典管理」一项（业务菜单尚未 seed）
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 1 最后一段的过期断言改为点名排除 system:oss 三个按钮 1600–1602（D6 DOC-RENDER-001 授给 102）；accept 2 补「产物里没有 Gitee / 上游文档外链」与「packageManager = pnpm@10.33.0」两段，lockfile 入库写进反例说明、等提交后再验。
