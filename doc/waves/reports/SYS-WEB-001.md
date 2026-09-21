# SYS-WEB-001 · 完工报告

- **ticket**：SYS-WEB-001（track SYS / phase D1 / size M）
- **status**：**done**
- **accept**：**2/2 绿**（第 1 条的 `--fresh-module ruoyi-lqg` 原样跑法在本 agent 沙箱里 `ps` 被禁 → api.sh exit 2，用等价手工证据替代，见 §5.1 与 WARN-1/2）
- **分支**：`task/D1`，本地 commit **`887f972`**（未 push / 未 merge；`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`doc/verify/api.sh` 一个字节没动）
- **上游 plus-ui**：`JavaLionLi/plus-ui` tag **`v5.5.3-v2.5.3`**（commit `5c9c9405887559ed61186b2fe8638b2d4ef444d4`）—— 与后端 RuoYi-Vue-Plus **5.5.3 精确配套**，不是 5.6.x
- **验收对象**：`code/plus-ui` 工作树；后端 jar `ruoyi-admin.jar` @ `2026-09-21 15:01:46`（含本票迁移），后端进程 PID 4392（8081，dev profile）@ 15:03

---

## 1. plus-ui 落盘与配套版本

`code/plus-ui` 之前**不存在**（SYS-BASE-001 只落了后端），本票从干净上游拉：

```
$ git ls-remote --tags https://github.com/JavaLionLi/plus-ui | grep 5.5.3
5a0047ab9773fd552e02f39fe0866b909afd303c  refs/tags/v5.5.2-v2.5.2
7aca5fde5366a2e9b3162b718ba2570f41849b82  refs/tags/v5.5.3-v2.5.3
5c9c9405887559ed61186b2fe8638b2d4ef444d4  refs/tags/v5.5.3-v2.5.3^{}   ← 实际 checkout
$ git clone --depth 1 --branch v5.5.3-v2.5.3 … && cat package.json | head -5
  "name": "ruoyi-vue-plus",
  "version": "5.5.3-2.5.3",
```

落盘方式与 SYS-BASE-001 一致：**不带 `.git`、不拷 dongjiaoshan 任何文件**（参考项目 `dongjiaoshan/code/main/plus-ui` 是 5.6.1-2.6.1，只用来对照目录结构，一行没拷）。
`pnpm install`（`--registry https://registry.npmmirror.com`）→ `Done in 3m 21.5s`。

> 上游 `.gitignore` 忽略 `pnpm-lock.yaml` / `auto-imports.d.ts` / `components.d.ts` → 本票按上游约定，锁文件**没有强加**（见 WARN-4）。

## 2. 本票立的三条约定（后续 WEB ticket 照此办）

### 2.1 i18n 按域拆分（不碰上游两个大文件）

```
src/lang/lqg/sys.zh_CN.ts      ← 样例（域 = sys）
src/lang/lqg/sys.en_US.ts
src/lang/index.ts              ← import.meta.glob('./lqg/*.zh_CN.ts', { eager: true }) 合并进 lqg.<域>.*
```

`src/lang/zh_CN.ts` / `en_US.ts`（上游的共享大文件）**一个字没加**：并行 ticket 往里加 key 必撞。
新增一个域 = 加 `<域>.zh_CN.ts` + `<域>.en_US.ts` 两个文件，入口不用改。
**运行期证据**：首页占位文案与登录页页脚都来自 `lqg.sys.*`（截图里能看到 `工作台首页建设中（内容见 SYS-HOME-001）`、页脚 `类器官样本管理`）——
glob 合并是真的生效，不是只写了文件。

### 2.2 业务页目录约定

`src/views/lqg/<域>/` 与 `src/api/lqg/<域>/` 各建 `.gitkeep`，域标签与后端 `ruoyi-lqg` 子包一致：
`sys / auth / sample / embed / cryo / qc / doc / ocr / ext`（9 × 2 = 18 个文件）。

### 2.3 主色 `#0E7C7B`（唯一真相源 + 运行期接线）

`src/assets/styles/lqg-tokens.scss`：定义 `design-authority.md` §B 的全部 `--lqg-*` 变量（primary / primary-soft / bg / card / ink 3 档 / line / ext / warn / ok / danger 及各自 soft、等宽字体），
并把 Element Plus 的 `--el-color-primary` + `light-1..9` + `dark-1..9` 全系列映射到 `#0E7C7B`（数值口径与上游 `utils/theme.ts` 的 `getLightColor`/`getDarkColor` 一致，预先算好写在文件里）。
在 `src/assets/styles/index.scss` 里排在 `element-plus/dist/index.css` **之后** `@use`，保证 `:root` 覆盖生效。

> ⚠️ 只写 scss **不够**：上游 `App.vue` 会调 `handleThemeStyle(settingsStore.theme)` 把同一批变量写成 `documentElement` 的**内联样式**，内联样式优先级更高、会在运行时把主色改回上游蓝。
> 所以 `src/settings.ts` 的 `theme` 同步改成 `#0E7C7B`（文件里写了这条因果，防止后人只改一处）。

**运行期证据（不是 grep 出来的）**：

```
$ node /tmp/shots/shots.mjs        # puppeteer-core + 本机 Google Chrome，headless
[shots] login probe {"htmlTitle":"类器官样本管理","loginTitle":"类器官样本管理",
  "buttonBg":"rgb(14, 124, 123)","buttonBorder":"rgb(14, 124, 123)",
  "hasTenantSelect":false,"hasRegisterLink":false,"footer":"类器官样本管理","hasSocialButtons":false}
[shots2] 03-dict.png {"url":"/system/dict",
  "primaryButtons":[{"text":"搜索","bg":"rgb(14, 124, 123)"},{"text":"新增","bg":"rgb(230, 241, 241)"}],"tableRows":10}
```

`rgb(14, 124, 123)` = **`#0E7C7B`**。截图见 §5.3。

## 3. 品牌与登录页

| 位置 | 改动 |
|---|---|
| 标题 / 侧边栏 logo 文字 | `.env.development` + `.env.production` 的 `VITE_APP_TITLE` / `VITE_APP_LOGO_TITLE` = **类器官样本管理**（`index.html` 用 `%VITE_APP_TITLE%`，所以 dist/index.html 里就是品牌名） |
| 登录页 | 去掉**租户下拉**（多租户关闭，`tenantId` 写死 `000000`，后端仍要这个字段）、去掉**「注册」入口**、去掉上游演示用的 5 个第三方登录按钮（微信/MaxKey/TopIAM/Gitee/GitHub）；背景从上游蓝色演示图换成 token 拼的青绿渐变；页脚换成 `lqg.sys.login.copyright` |
| 首页 | 上游两栏「RuoYi-Vue-Plus / RuoYi-Cloud-Plus」演示介绍 + 外链按钮整段删掉，换成一句占位（`lqg.sys.homePlaceholder`）；真正的待办五张卡片是 SYS-HOME-001 |
| 上游多租户字样 | `.env.*` ×2、`package.json` description、`src/views/index.vue` 三处清干净；`grep -rlE '多租户管理系统\|RuoYi-Vue-Plus多租户' dist` 无命中 |

> **登录页的注册页本身没删**：上游 `/register` 路由 + `register.vue` + `permission.ts` 白名单还在，只是没有入口了。实测后端有开关兜底（`POST /auth/register` → `{"code":500,"msg":"当前系统没有开启注册功能！"}`，`sys.account.registerUser=false`），不构成口子，见 WARN-5。

## 4. 迁移 `V202609210830__SYS-WEB-001-admin-dict-menu.sql`（79 行，新增）

**取号依据** `doc/lint-profile.yaml`：D1 = `20260921`，SYS 域 HHmm `08xx`；0830 未被占用（已有 0800 / 0810 / 0820 / 0910）。

内容三段：

1. **角色 101（lqg_admin）授权**，`menu_id` **一律按上游 `sys_menu` 的真实值取、不猜号**：
   ```sql
   WITH target_menu   AS (SELECT menu_id FROM sys_menu WHERE component IN ('system/dict/index','system/config/index')),
        target_parent AS (SELECT DISTINCT parent_id FROM sys_menu WHERE component IN ('system/dict/index','system/config/index')),
        target_button AS (SELECT menu_id FROM sys_menu WHERE parent_id IN (SELECT menu_id FROM target_menu) AND menu_type='F')
   INSERT INTO sys_role_menu (role_id, menu_id) SELECT 101, menu_id FROM (…UNION…) t
   ON CONFLICT (role_id, menu_id) DO NOTHING;
   ```
   基线里实测命中 **`1` = 系统管理（M）、`105` = 字典管理（C）、`106` = 参数设置（C）、`1026-1030` = 字典按钮、`1031-1035` = 参数按钮**，共 13 行。
   **只授这两条 C 菜单 + 各自的 F 按钮 + 它们共同的父目录**；用户 / 角色 / 菜单 / 部门 / 岗位 / 通知公告 / 日志 / 文件管理一条都没授。
2. 显式清一次 102（lqg_internal）在 `menu_id < 5000` 上的角色菜单（幂等兜底；本票本来也不授它）。内部人员的业务菜单由各业务域 WEB ticket 在自己号段（5100+）里 seed。
3. 两行 `sys_config`（CR-20260918-07 / `UI:admin.config` 的两个开关，`config_type='N'` = 非系统内置、内部人员可改）：
   `lqg.ext.show-internal-no = false`、`lqg.cryo.overdue-days = 14`（`WHERE NOT EXISTS` 幂等，`config_id` 取 5001/5002 避开上游 1-11 与雪花 id）。

**为什么要连父目录一起授**（本张最容易踩的坑）：上游 `SysMenuServiceImpl.selectMenuTreeByUserId` 用 `getChildPerms(menus, 0)` 建树 ——
父节点不在授权集合里，两条 C 菜单**挂不上去**，`getRouters` 里就一条都没有，accept 第 2/3 段直接红。

**迁移实测**（空库端到端，不是补跑）：

```
$ docker exec lqg-dev-postgres psql -U lqg -d postgres -c "DROP DATABASE IF EXISTS lqg_dev WITH (FORCE)" -c "CREATE DATABASE lqg_dev OWNER lqg"
$ (重启后端)
- Migrating schema "public" to version "202609210800 - SYS-BASE-001-ruoyi-postgres-baseline"
- Migrating schema "public" to version "202609210810 - SYS-BASE-001-lqg-dicts"
- Migrating schema "public" to version "202609210820 - SYS-BASE-001-lqg-roles"
- Migrating schema "public" to version "202609210830 - SYS-WEB-001-admin-dict-menu"     ← 本票
- Migrating schema "public" to version "202609210910 - AUTH-LOGIN-001-wx-bind-ext-profile"
- Successfully applied 5 migrations to schema "public", now at version v202609210910 (execution time 00:00.379s)
- Started DromaraApplication in 6.202 seconds

$ python3 doc/verify/db.py --sql "SELECT version||'|'||script||'|'||success FROM flyway_schema_history ORDER BY installed_rank"
202609210800|V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql|true
202609210810|V202609210810__SYS-BASE-001-lqg-dicts.sql|true
202609210820|V202609210820__SYS-BASE-001-lqg-roles.sql|true
202609210830|V202609210830__SYS-WEB-001-admin-dict-menu.sql|true
202609210910|V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql|true

$ python3 doc/verify/db.py --sql "SELECT rm.role_id||':'||m.menu_id||':'||m.menu_name||':'||m.menu_type FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE rm.role_id=101 ORDER BY m.menu_id"
101:1:系统管理:M
101:105:字典管理:C
101:106:参数设置:C
101:1026:字典查询:F  101:1027:字典新增:F  101:1028:字典修改:F  101:1029:字典删除:F  101:1030:字典导出:F
101:1031:参数查询:F  101:1032:参数新增:F  101:1033:参数修改:F  101:1034:参数删除:F  101:1035:参数导出:F
```

> ⚠️ 重建 dev 库是**必须**的：0830 的版本号小于已经应用过的 0910，而 `spring.flyway.out-of-order=false`（SYS-BASE-001 配的）→ 直接补跑会让后端**启动即 `FlywayValidateException`**。见 WARN-2。

## 5. 改了哪些文件

### 5.1 后端

| 文件 | 改动 |
|---|---|
| `code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609210830__SYS-WEB-001-admin-dict-menu.sql` | **新增**，见 §4 |

**没有新增任何 Java 类、没有新增任何后端接口**（本票只做菜单授权 + 两行参数，业务接口是各域 ticket 的事）。
前端消费的都是上游现成接口：`GET /system/menu/getRouters`、`GET /system/config/configKey/{key}`、`/system/dict/*`、`/system/config/list`。

### 5.2 前端（`code/plus-ui`，与干净上游 `v5.5.3-v2.5.3` 的差异正好是下面这些）

**修改（9 个）**

| 文件 | 改动 |
|---|---|
| `.env.development` | 品牌 2 行；`VITE_APP_PORT` 80 → **8082**（80 要 root，8080 留给本机日常服务）；新增 `VITE_APP_PROXY_TARGET='http://127.0.0.1:8081'`；`VITE_APP_ENCRYPT` true → **false**（与本地后端 `--api-decrypt.enabled=false` 对齐，否则前端加密了后端不解密、登录一律失败） |
| `.env.production` | 品牌 2 行（`VITE_APP_ENCRYPT` 保持 true，生产后端会解密） |
| `package.json` | description 去掉上游「多租户管理系统」 |
| `vite.config.ts` | 代理 target 改读 `env.VITE_APP_PROXY_TARGET`（缺省仍是 8080）；`server.open` true → false（headless / CI 友好） |
| `src/settings.ts` | `theme` `#409EFF` → `#0E7C7B`（原因见 §2.3，不是可选） |
| `src/assets/styles/index.scss` | 末尾 `@use './lqg-tokens.scss'`（排在 element-plus 之后） |
| `src/lang/index.ts` | 加 `import.meta.glob('./lqg/*.zh_CN.ts' / '*.en_US.ts', { eager: true })` 两行合并，注入 `lqg.<域>` 命名空间 |
| `src/views/login.vue` | 去租户下拉 / 注册入口 / 5 个第三方登录按钮；背景改 token 渐变；色值字面量换 `var(--lqg-*)` |
| `src/views/index.vue` | 上游演示首页 → 一句占位 |

**新增**

- `src/assets/styles/lqg-tokens.scss`（§B 全套 `--lqg-*` + EP 主色系列映射）
- `src/lang/lqg/sys.zh_CN.ts`、`src/lang/lqg/sys.en_US.ts`
- `src/views/lqg/<域>/.gitkeep` × 9、`src/api/lqg/<域>/.gitkeep` × 9

```
$ diff -rq /tmp/plus-ui-553 code/plus-ui --exclude=.git --exclude=node_modules --exclude=dist
Files …/.env.development and …/.env.development differ
Files …/.env.production and …/.env.production differ
Files …/package.json and …/package.json differ
Files …/src/assets/styles/index.scss and … differ
Files …/src/lang/index.ts and … differ
Files …/src/settings.ts and … differ
Files …/src/views/index.vue and … differ
Files …/src/views/login.vue and … differ
Files …/vite.config.ts and … differ
Only in code/plus-ui: pnpm-lock.yaml                      ← 上游 .gitignore 忽略，未提交
Only in code/plus-ui/src/api: lqg
Only in code/plus-ui/src/assets/styles: lqg-tokens.scss
Only in code/plus-ui/src/lang: lqg
Only in code/plus-ui/src/types: auto-imports.d.ts         ← 构建生成，上游 .gitignore 忽略
Only in code/plus-ui/src/types: components.d.ts           ← 同上
Only in code/plus-ui/src/views: lqg
```

## 6. accept 逐条 ✅ / ❌ + 关键输出

### 6.1 accept 1 · MENU —— ✅（`--fresh-module` 用等价手工证据替代）

**原样跑法（沙箱限制，起于第 2 段）**：

```
$ bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET /system/menu/getRouters | jq -e '…' && …（后 6 段）
doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
date: illegal option -- d
（EXIT=4：api.sh exit 2 → `&&` 链断在第 2 段；ps 是本 agent 沙箱禁的，不是脚本 bug）
```

**手工替代 —— 覆盖那道守卫的两个半边**（与 SYS-BASE-001 / AUTH-LOGIN-001 同一手法）：

```
jar mtime             : 2026-09-21 15:01:46
lqg src newer than jar: []          ← 没有比 jar 新的 ruoyi-lqg 源码
8081 PID              : 4392（`lsof -ti tcp:8081 -sTCP:LISTEN`）
process holds jar     : ruoyi-admin.jar（就是新打的那个）
backend log mtime     : 2026-09-21 15:04:50（进程起于 15:03，晚于 jar 的 15:01:46 = 没 stale）
```

**逐字执行（去掉沙箱禁的 `--fresh-module`，其余一字不改）**：

```
$ bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as admin GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "system/dict/index")] | length == 1 and (.[0].path | length > 0)' &&
  bash doc/verify/api.sh --as admin GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "system/config/index")] | length == 1 and (.[0].path | length > 0)' &&
  bash doc/verify/api.sh --as admin GET '/system/config/configKey/lqg.ext.show-internal-no' | jq -e '.msg == "false" or .data == "false"' &&
  bash doc/verify/api.sh --as admin GET '/system/config/configKey/lqg.cryo.overdue-days' | jq -e '.msg == "14" or .data == "14"' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE rm.role_id = 101 AND m.component = 'system/dict/index' AND m.path <> ''" --eq 1 &&
  python3 doc/verify/db.py --sql "SELECT m.menu_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id WHERE rm.role_id IN (101,102) AND m.menu_type IN ('M','C') AND (m.path IS NULL OR m.path = '')" --empty &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_role_menu WHERE role_id = 102 AND menu_id < 5000" --eq 0 &&
  echo ACCEPT-1 GREEN
true          ← 字典管理 命中 1 条且 path 非空
true          ← 参数设置 命中 1 条且 path 非空
true          ← configKey lqg.ext.show-internal-no 读到 false
true          ← configKey lqg.cryo.overdue-days 读到 14
1             ← 101 的 system/dict/index 菜单数（--eq 1）
（空）         ← 101/102 名下 M/C 菜单里没有空 path（--empty 零行）
0             ← 102 名下 menu_id < 5000 的行数（--eq 0）
ACCEPT-1 GREEN
```

**侧证 —— 树与值的真身**：

```
$ bash doc/verify/api.sh --as admin GET /system/menu/getRouters | jq -c '[.data[] | {path, meta: .meta.title, children:[.children[]? | {path, component, meta:.meta.title}]}]'
[{"path":"/system","meta":"系统管理","children":[
   {"path":"dict","component":"system/dict/index","meta":"字典管理"},
   {"path":"config","component":"system/config/index","meta":"参数设置"}]}]

$ bash doc/verify/api.sh --as admin GET '/system/config/configKey/lqg.ext.show-internal-no'
{"code":200,"msg":"操作成功","data":"false"}
$ bash doc/verify/api.sh --as admin GET '/system/config/configKey/lqg.cryo.overdue-days'
{"code":200,"msg":"操作成功","data":"14"}
```

### 6.2 accept 2 · API —— ✅

```
$ cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null &&
  grep -q '类器官样本管理' dist/index.html &&
  ! grep -rlE '多租户管理系统|RuoYi-Vue-Plus多租户' dist | grep -q . &&
  test -f src/lang/lqg/sys.zh_CN.ts && test -f src/lang/lqg/sys.en_US.ts &&
  grep -q "import.meta.glob" src/lang/index.ts &&
  grep -q -- '--el-color-primary' src/assets/styles/lqg-tokens.scss && grep -qi '0E7C7B' src/assets/styles/lqg-tokens.scss &&
  echo ACCEPT-2 GREEN
ACCEPT-2 GREEN
```

构建本身（第 1 段不吞输出时）：

```
$ pnpm build:prod
> ruoyi-vue-plus@5.5.3-2.5.3 build:prod …/code/plus-ui
> vite build --mode production
✓ built in 7.22s
dist/assets/index-DhS11GHm.js   1,659.58 kB │ gzip: 555.97 kB
```

`mvn package` 也实跑了（jar 内含本票迁移）：

```
$ mvn -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome -DskipTests package
[INFO] ruoyi-lqg … SUCCESS   [INFO] ruoyi-admin … SUCCESS
[INFO] BUILD SUCCESS   Total time: 5.392 s
$ unzip -l ruoyi-admin/target/ruoyi-admin.jar | grep db/migration
 4143  09-21-2026 15:01  BOOT-INF/classes/db/migration/V202609210830__SYS-WEB-001-admin-dict-menu.sql
```

### 6.3 截图与主色证据（`doc/waves/reports/SYS-WEB-001/`）

| 文件 | 内容 |
|---|---|
| `01-login.png` | 登录页：品牌「类器官样本管理」、**无租户下拉**、**无注册入口**、无第三方登录按钮、青绿渐变底、青绿登录按钮、页脚品牌名 |
| `02-home-sidebar.png` | 首页占位（「工作台首页建设中（内容见 SYS-HOME-001）」）+ 侧边栏（首页 / 系统管理） |
| `03-dict.png` | 系统管理 → **字典管理**：侧边栏展开「系统管理 › 字典管理 / 参数设置」，列表 36 条字典，`搜索` 主色按钮 |
| `04-config.png` / `05-config-fullpage.png` | **参数设置**：7 行里能看到本项目两行 `lqg.ext.show-internal-no=false`、`lqg.cryo.overdue-days=14`（系统内置列「否」） |
| `probe-login.json` / `probe-sidebar.json` / `probe-dict.json` / `probe-config.json` / `probe-config-rows.json` | 上面每张图的机器可读证据（`document.title`、按钮 `getComputedStyle().backgroundColor`、侧边栏菜单文字、参数设置逐行文字） |

**主色生效的三处同源证据**：登录按钮 `rgb(14, 124, 123)`、字典页 `搜索` 按钮 `rgb(14, 124, 123)`、激活的页签/分页 `1` 与侧边栏选中项同色 —— 都等于 `#0E7C7B`。

`probe-sidebar.json`：

```json
{ "logoTitle": "类器官样本管理", "topMenus": ["首页", "系统管理", "字典管理", "参数设置"] }
```

`probe-config-rows.json`（节选后两行）：

```
"合作单位可见内部编号 lqg.ext.show-internal-no false 否 CR-20260918-07：… 2026-09-21 15:04:01",
"-80 冻存超期提醒天数 lqg.cryo.overdue-days 14 否 CR-20260918-07：… 2026-09-21 15:04:01"
```

## 7. 遗留与 raise

1. **侧边栏不是「只有字典管理一项」**（ticket §4.2 的原话）：accept 第 1 条要求 `system/config/index` 路由也在，CR-20260918-07 又要求两个开关能在「参数设置」里改，而上游建树必须先授父目录 ——
   所以 lqg_admin 的侧边栏必然是 **「系统管理 › 字典管理 / 参数设置」两项**（截图如实呈现）。除了这两项，**没有任何其它系统管理菜单、也没有任何业务菜单**（业务菜单尚未 seed）。见 WARN-3。
2. **`/register` 页面本体还在**（只摘了登录页入口）：后端有 `sys.account.registerUser=false` 兜底，不构成口子。见 WARN-5。
3. **dev 库被重建过**（`DROP DATABASE lqg_dev` → 5 支迁移重跑 → `reseed.sh --yes`）：原因是 0830 < 已应用的 0910 + `out-of-order=false`，见 WARN-2。
   收尾状态 = **干净 seed**（`reseed.sh --yes` 后未再做任何写操作；探测注册接口那次请求被后端开关挡在写库之前，`sys_user` 里没有 `zz_probe_web001`）。
4. **`VITE_APP_ENCRYPT=false` 只写在 dev**：生产保持 true。这不是「关掉加密」，是与本地后端 `--api-decrypt.enabled=false`（SYS-BASE-001 留下的起法）对齐；真部署时两端一起开。
5. **`src/settings.ts` 的 `theme` 与 `lqg-tokens.scss` 是两处同值**：上游把主题写成内联样式，绕不开；已在两个文件里互相写明原因。后续若换主色必须两处一起改（或把 `handleThemeStyle` 换成不写内联）。
6. **根 pom / 上游代码未动**：本票没改任何后端 Java。`code/miniapp/`（unibest 脚手架）与根 `.pnpm-store/` **不是本票产物**，没进 commit，见 WARN-6。
7. **越出 `touches` 的改动**：无。`touches` 列的 `code/plus-ui/**` 与 `…/db/migration/V20260921083*__SYS-WEB-001-*.sql` 覆盖了本票全部改动。

## 8. 验证用长进程

- 后端 java（8081）：**已关**（`pkill -9 -f ruoyi-admin.jar`，`lsof -ti tcp:8081` 已空）。起停脚本留在 `.tmp/run-backend.sh`（gitignore）：
  ```
  nohup bash .tmp/run-backend.sh > .tmp/web001-backend.log 2>&1 &   # 起（dev profile + --api-decrypt.enabled=false）
  pkill -9 -f 'ruoyi-admin.jar'                                      # 停
  ```
  **没留给后续 ticket**：D1 下一张票自己起即可（脚本可复用；起之前不用重新打包，jar 已含全部 5 支迁移）。
- 前端 vite dev server（临时占 8083，因为 8082 已被本机别的进程占用）：**已关**（`lsof -ti tcp:8083` 已空）。
  起停：`cd code/plus-ui && pnpm dev`（读 `.env.development` 的 `VITE_APP_PORT=8082`，端口被占会自动 +1；代理到 127.0.0.1:8081）。
- 截图用的 puppeteer 脚本与 `puppeteer-core` 装在 `/tmp/shots`，**不在仓库里**；截图产物在 `doc/waves/reports/SYS-WEB-001/`。
- docker 容器：**留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），
  起停 `docker compose -f code/deploy/dev/docker-compose.yml up -d|down`。
- 未占 **8080 / 5432 / 6379 / 9000 / 9001**（Kevin 本机日常服务）。8082 上有一个**不是本票起的** node 进程（PID 31060），没碰它。

## 9. 坑与解法（给下游）

1. **补一支「版本号更小」的迁移会让后端起不来**：`out-of-order=false` + 已应用过更高的版本号 → `FlywayValidateException: Validate failed: Migrations have failed validation`，
   报错栈一路包成 `authController → sysLoginService → sysTenantMapper`，**根因隔着 5 层**，看着像「数据库连接坏了」。
   解法：dev 库 `DROP DATABASE … WITH (FORCE)` + `CREATE DATABASE` 后让 5 支迁移按版本序一次跑完（真部署本来就是按序跑的）。生产环境若已上线，补号迁移必须另起新号（> 当前最高）。
2. **`getRouters` 只返回 `parent_id=0` 的根**：`selectMenuTreeByUserId` 用 `getChildPerms(menus, 0)` 建树，**父目录不在 `sys_role_menu` 里，子菜单就整条消失**（不报错、就是没有）。
   所以「只授字典管理、不授系统管理」在这个框架里做不到；授父目录 ≠ 授它的其它子菜单。
3. **只改 scss 不能改主色**：`App.vue` 的 `handleThemeStyle(settingsStore.theme)` 写的是 `documentElement` **内联样式**，优先级高于任何 `:root` 样式表规则。必须同时改 `src/settings.ts`。
4. **pnpm 10 默认拦构建脚本**：`pnpm install` 报 `Ignored build scripts: esbuild …`。本机实测**不影响** vite 构建（`node_modules/.pnpm/esbuild@*/node_modules/esbuild/bin/esbuild` 与 `@esbuild/darwin-arm64` 都在，`✓ built in 7.22s`）；若哪天构建报 esbuild 缺可执行文件，跑 `pnpm approve-builds` 或 `pnpm rebuild esbuild`。
5. **`.env.development` 的 `VITE_APP_ENCRYPT` 必须与后端 `--api-decrypt.enabled` 同向**：不一致时前端把请求体 AES 加密、后端不解密，表现是「登录一直失败」，而 curl 直接打后端却成功 —— 很容易误判成前端逻辑坏了。

## 10. WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | harness | `doc/verify/api.sh --fresh-module` 的 `ps -o lstart=` 在 subagent 沙箱被禁，accept 原样跑 exit 2 | **已连续三张票命中**（SYS-BASE-001 WARN-1、AUTH-LOGIN-001 WARN-1、本票）。只影响 subagent 沙箱，人工/CI 正常。本票用「`find … -newer jar` 为空 + `lsof` 拿 PID + 进程持有该 jar + 日志 mtime 晚于 jar」四项等价替代。方案：把启动时间换成 `lsof`/日志 mtime 的兼容写法（改动属验收执行器，三张票都按规矩没动 `api.sh`）。 |
| WARN-2 | S2 | doc-drift | ticket 指定的迁移号 `202609210830` 落在**已应用的** `202609210910` 之前 → `out-of-order=false` 下后端启动即 `FlywayValidateException` | 影响任何「跨域补号」的场景（SYS 08xx 的票排在 AUTH 09xx 之后做）。本票靠重建 dev 库解决（真部署按序不受影响）。方案二选一：① ticket 生成器禁止跨域补号；② dev 环境显式 `spring.flyway.out-of-order: true`（prod 保持 false）。请主会话定。 |
| WARN-3 | S3 | clarify | ticket §4.2「侧边栏应只有「字典管理」一项」与 accept 第 1 条 + CR-20260918-07 冲突 | accept 要求 `system/config/index` 路由可达、CR 要求两个开关能在「参数设置」里改，而上游建树必须先授父目录「系统管理」→ 侧边栏必为「系统管理 › 字典管理 / 参数设置」。本票按 accept + CR 办。方案：把 §4.2 那句改成「除字典管理与参数设置外不应有别的菜单」。 |
| WARN-4 | S3 | debt | `code/plus-ui` 未提交 `pnpm-lock.yaml`（上游 `.gitignore` 忽略它）→ 前端构建不可复现 | 影响 CI/他人重建：依赖版本随镜像漂移。本票按上游约定没强加。方案：若要可复现，`git add -f code/plus-ui/pnpm-lock.yaml`（或项目自己维护一份）。 |
| WARN-5 | S3 | clarify | 上游 `/register` 路由 + `register.vue` + `permission.ts` 白名单仍在，URL 直达会渲染一个必然失败的注册页 | 本票只按要求摘掉登录页入口。已实测后端开关兜底：`POST /auth/register` → `{"code":500,"msg":"当前系统没有开启注册功能！"}`（`sys.account.registerUser=false`），**不是安全口子**。方案：AUTH-STAFF-001 或后续壳票顺手删路由 + 页面 + 白名单。 |
| WARN-6 | S2 | process | **本工作区有并行 agent 在写**：`code/miniapp/`（unibest 脚手架，`name: lqg-miniapp`）在 15:02 出现，`code/miniapp/.tmp/pnpm-store` 也在；根目录另有 826MB 未跟踪的 `.pnpm-store/` | 与「按分支 `task/D1` 串行跑」的调度假设冲突：同一 worktree 上并行提交会互相夹带（本票靠**只显式 `git add` 自己的路径**规避）。方案：主会话确认是不是 SYS-MP-001 在同 worktree 并行；若要走并行，应各自 worktree。根 `.gitignore` 建议补 `.pnpm-store/`。 |
