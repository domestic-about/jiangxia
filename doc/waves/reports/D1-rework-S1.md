# D1 QA 门第 1 轮 · S1 返工报告（`qa_rework`）

- **返工项**：`SYS-WEB-001` —— 工作台在 `pnpm dev` 下任何菜单导航都渲染成空白页（D1 QA r1 唯一一条 S1）
- **status**：**done**（修复已落盘；探针 9/9 绿；负对照实测会红；`pnpm build:prod` 通过）
- **分支**：`task/D1`（未切分支、未 push、未 merge）
- **改动**：`code/plus-ui/src/layout/components/AppMain.vue`（**唯一一个源码文件**，+16 −1；只删了 `mode="out-in"`，其余全是说明注释）
- **本报告目录**：`doc/waves/reports/D1-rework-S1/`
  - 探针：`probe-web-menu-nav.mjs`
  - 修复后截图：`shots/probe-01..05-*-headless.png`
  - 修复前（负对照）截图：`shots-negative/probe-01..05-*-headless.png`
  - 证据：`evidence/index.vue.dev-compiled.js.txt`（dev server 实际吐给浏览器的编译产物）
- **越界**：**有，1 条 WARN-1**（`code/plus-ui/src/layout/components/AppMain.vue` 属于「上游布局组件」，SYS-WEB-001 §3 明确写了不改）——见 §7

---

## 1 现象（我自己独立复现，与 QA 完全一致）

后端 8081（dev profile + `--api-decrypt.enabled=false`）+ `code/plus-ui` dev server 8082：

```
$ node .tmp/explore.mjs            # 首轮复现脚本
[login] url = http://127.0.0.1:8082/index
[before-click] len=594             # 首页正常
  （点侧边栏「人员与单位 → 内部人员授权」）
[t+100ms] url=/auth/staff len=488
[t+300ms] url=/auth/staff len=7    # ← 只剩 <!---->
[t+8000ms] url=/auth/staff len=7   # 永远停在 7
[events] []                        # 一个 transition/animation 事件都没有
```

- `.app-main` 的 `innerHTML` = `<!---->`（7 字符），页面全空；
- **控制台没有任何 `pageerror` / `console.error`**（连 Vue 的 warn 都没有）；
- 硬刷新同一 URL → 正常（`/auth/staff` = 11533）。

导航矩阵（`node .tmp/matrix.mjs`）把影响面钉死在**「离开首页」这一步**：

| 路径 | 修复前 |
|---|---|
| 首页 → 内部人员授权 | **空白（7）** |
| 非首页 → 非首页（外部用户 → 来源单位与组别） | 正常（19338） |
| 非首页 → 首页（进入首页） | 正常（594），但首页的 enter 动画不播 |
| 首页 → 外部用户 | **空白（7）** |
| 首页 → 之后任何一页 | **一直空白（7）**（`state.isLeaving` 卡死后不可自愈） |

即：**只要离开「首页」，工作台从此永久空白，只能手改地址栏硬刷新**。这解释了 QA 报告里「点任何菜单都空白」——他们每次都是从落地首页出发。

---

## 2 根因（定位到具体机制，不是「去掉 mode 就好了」）

### 2.1 一句话

`src/views/index.vue` 的模板**顶层有一条 HTML 注释**；`vite dev` 会保留模板注释，于是首页根 vnode 变成了 **Fragment**。`AppMain.vue` 的 `<transition mode="out-in">` 把过渡钩子挂在 **Fragment** 上，而渲染器卸载 Fragment 时**只逐个 remove 子节点、从不回调 Fragment 上的 `afterLeave`** → `BaseTransition` 的 `state.isLeaving` 永远停在 `true` → 此后每次渲染都走 `emptyPlaceholder()` 分支，只输出 `<!---->`。

### 2.2 证据链（四步，逐步加硬）

**(a) dev 编译产物：首页根节点是 Fragment**

`curl http://127.0.0.1:8082/src/views/index.vue`（原文存 `evidence/index.vue.dev-compiled.js.txt`）：

```js
const _sfc_render = (_ctx, _cache) => (_openBlock(), _createElementBlock(_Fragment, null, [
    _createCommentVNode("\n    SYS-WEB-001：上游的演示首页…整段去掉。…"),
    _createElementVNode("div", _hoisted_1, [ … ])
  ], 2112 /* STABLE_FRAGMENT, DEV_ROOT_FRAGMENT */))
```

`2112 = 64 (STABLE_FRAGMENT) | 2048 (DEV_ROOT_FRAGMENT)`。注释来自 `src/views/index.vue` 第 2–5 行（SYS-WEB-001 自己写的说明注释）。
顺手扫了全部 58 个 `.vue` 页面（逐个从 dev server 取编译产物、找 `_createElementBlock(_Fragment`）：
**今天只有 `src/views/index.vue` 一个**，其余 57 个都是单一元素根。

**(b) Vue 运行时到底卡在哪：直接读实例树**

从页面里抓 `AppMain` 下那个 `<transition>` 的 `BaseTransition` 实例，并给它的 `update` 打桩（`.tmp/bt.mjs`）：

```
[pre-click]  {"mainLen":594,"subTreeType":"KeepAlive","subTreeChildren":"object"}
[stuck?]     {"mainLen":7,  "subTreeType":"KeepAlive","subTreeChildren":"NULL",   ← children===null
              "log":[{"what":"update()"},{"what":"update()->done"}] }             ← 全程只被调用 1 次
```

- `subTree === KeepAlive vnode with children === null` 是 `BaseTransitionImpl` 里
  `if (state.isLeaving) return emptyPlaceholder(child)` 的**专属产物**（`emptyPlaceholder` 就是
  `cloneVNode(keepAlive); children = null`）→ 确认卡在 `state.isLeaving === true`；
- 我手动再调一次 `__origUpdate()` 也还是 `NULL` → 不是「没人触发重渲染」，是 `isLeaving` 本身没被复位；
- `update()` 在整个导航里**只被调用一次**，说明 `afterLeave`（唯一会复位它的地方）**从未执行**。

**(c) 哪条代码路径：给 dev 优化后的 Vue bundle 打桩，拿到全路径 trace**

方法：临时给 `code/plus-ui/node_modules/.vite/deps/chunk-NV24IIX7.js`（vite 预打包的 Vue 运行时）插入 trace，重启 dev server 后重跑；**用完已用备份还原**（`grep -c __TR` = 0），`node_modules/.vite` 只是生成缓存。关键输出：

```
{"e":"out-in: isLeaving=true, set afterLeave","leavingKey":"/index","shapeFlag":4}
{"e":"unmount","shapeFlag":4, "doRemove":true,  "hasTransition":true, "hasAfterLeave":true,  "key":"/index"}   ← 首页组件 vnode
{"e":"unmount","shapeFlag":16,"doRemove":true,  "hasTransition":true, "hasAfterLeave":true,  "key":"null"}     ← 它的 subTree = Fragment
{"e":"unmount","shapeFlag":36,"doRemove":false,…}
{"e":"unmount","shapeFlag":17,"doRemove":false,…}
{"e":"remove2.performRemove","hasAfterLeave":false,"shapeFlag":17}   ← 真正 hostRemove 掉首页根 <div>，没有 transition
（全程没有 "afterLeave FIRED"）
```

对得上 `@vue/runtime-core@3.5.22` 的源码：

| 位置 | 代码 | 后果 |
|---|---|---|
| `runtime-core.esm-bundler.js:1190` | `if (state.isLeaving) { return emptyPlaceholder(child) }` | 卡住后每次渲染只出 `<!---->` |
| `:1216` | `setTransitionHooks(oldInnerChild, leavingHooks)` | `oldInnerChild` = 首页**组件** vnode |
| `:1456` | `setTransitionHooks(vnode,…)`：`else { vnode.transition = hooks }` | 根是 Fragment → 钩子落在 **Fragment** 上 |
| `:6683-6688` | `if (vnode.transition) { __DEV__ && !isElementRoot(root) && warn(...); setTransitionHooks(root, vnode.transition) }` | 钩子挂到 Fragment，**不传给子节点** |
| `:5967-5975` | `remove()` 的 Fragment 分支：`patchFlag & 2048` 时 `children.forEach(child => remove(child))`，否则 `removeFragment(el, anchor)` | 两条路都**不看 Fragment 自己的 `transition.afterLeave`** |
| `:5987-5991` | `performRemove(){ hostRemove(el); if (transition && transition.afterLeave) transition.afterLeave() }` | 子 `<div>` 没有 `transition` → `afterLeave` 丢失 |

于是：leave 阶段**连 leave 动画都没开始**（元素上的 `transition` 是 undefined），但 `mode="out-in"` 早已把 `state.isLeaving = true` 并且**只把复位责任交给了那个丢了回调的 `afterLeave`** → 单向卡死。

**(d) 为什么 `keep-alive` 单独不触发、`transition` 单独就触发（复核 QA 的隔离结论）**
`state.isLeaving` 只在 `BaseTransitionImpl` 的 `out-in` 分支被置 `true`（`runtime-core:1217-1224`），而这条分支要求 `oldInnerChild` 是「上一次渲染的子树」——`keep-alive` 只是让 `getInnerChild$1()` 多剥一层；两者的组合不是必要条件。QA 的 ③⑤ 两条隔离实验（只留 transition、去掉 animate class）与这里完全一致：**`mode="out-in"` 是唯一触发点**。

### 2.3 为什么只在 `pnpm dev` 下出现？

因为 **`@vitejs/plugin-vue` 按 `isProduction` 决定是否保留模板注释**：
`plugin-vue/dist/index.mjs:2882` → `isProduction: process.env.NODE_ENV === "production"`，再作为 `compilerOptions.isProd` 传给 `compileTemplate`。
- **dev**（`vite serve --mode development`）：`isProd=false` → 保留注释 → 首页根 = `Fragment[comment, div]`，`DEV_ROOT_FRAGMENT`；
- **build**（`vite build`，含 `--mode development`）：`NODE_ENV=production` → 注释被丢弃 → 首页根 = **单个 `<div>`**。

实测（本轮 `pnpm build:prod` 产物）：

```
$ grep -rl 'SYS-WEB-001：上游的演示首页' dist   → 无命中          # 注释真的没了
$ 从 dist/assets/index-C4rzT-HT.js 里取出 AppMain 的 render：
  nt(Is, { "enter-active-class": I(o) }, …)                      # 只剩 enter-active-class
```

所以构建产物里首页是元素根、过渡钩子落在元素上、`remove()` 走元素分支 → `afterLeave` 正常回调 → 不白屏（与 QA 的 `build:dev` 结论一致）。

### 2.4 ★ headless vs 有头：**真实浏览器一样中，不是 headless 特性**

用同一个探针，在**真正的有头 Google Chrome 窗口**（`HEADLESS=0 CHANNEL=chrome`）里跑，**修复前**：

```
FAIL  ① 离开首页 → 内部人员授权「人员与单位」页正常渲染（回归点） — .app-main innerHTML length=7 url=/auth/staff
FAIL  ② 侧边栏 → 外部用户（5130）正常渲染 — .app-main innerHTML length=7 url=/auth/extuser
FAIL  ③ 侧边栏 → 字典管理正常渲染 — .app-main innerHTML length=7 url=/system/dict
FAIL  ④ 侧边栏 → 参数设置正常渲染 — .app-main innerHTML length=7 url=/system/config
FAIL  ⑤ 侧边栏 → 首页正常渲染 — .app-main innerHTML length=7 url=/index
FAIL  ⑥ 再离开一次首页 → 来源单位与组别仍正常 — .app-main innerHTML length=7 url=/auth/unit
== SYS-WEB-001 菜单导航回归探针 2/9 通过（headless=false）==
```

**修复后**同一有头跑法 9/9 通过。再加上根因本身是一条**编译产物 + Vue 渲染器**的确定性问题（与事件时序、`transitionend` 是否触发、渲染帧率都无关：`getTransitionInfo` 全程 0 时长、事件探针一个事件都没收到），结论是：

> **这不是 headless 伪影，真人用真实浏览器走 `pnpm dev` 的工作台一样会看到整片空白。**

（顺带排除了「`transitionend` 不触发」这条假设：`.app-main` 上挂的 transition/animation 全事件监听录到 **0 个事件**，leave 根本没进入动画阶段。）

---

## 3 最小修复

```diff
--- a/code/plus-ui/src/layout/components/AppMain.vue
+++ b/code/plus-ui/src/layout/components/AppMain.vue
@@ -1,7 +1,7 @@
 <template>
   <section class="app-main">
     <router-view v-slot="{ Component, route }">
-      <transition :enter-active-class="animate" mode="out-in">
+      <transition :enter-active-class="animate">
         <keep-alive :include="tagsViewStore.cachedViews">
           <component :is="Component" v-if="!route.meta.link" :key="route.path" />
         </keep-alive>
```

外加在 `<script setup>` 里补 16 行说明注释（**不能写在 template 里**：`.app-main` 的 `innerHTML` 正是 QA 的断言对象，往 DOM 里插注释会污染它，也会让 dev / build 的 `innerHTML` 长度不一致）。**没有动样式、没有动 `keep-alive`、没有动 `:key`、没有重构 `AppMain`、没有动其它布局组件。**

### 3.1 为什么是「去掉 `mode="out-in"`」而不是别的

**为什么必须改 `AppMain.vue`（而不是改 `index.vue` 的注释）**

`index.vue` 的顶层注释只是**当前唯一的扳机**，不是根因。我在修复后做了一次鲁棒性实验：临时给 `index.vue` 加第二个根节点（`STABLE_FRAGMENT`，**不带 `DEV_ROOT_FRAGMENT`**），再用**未修复**的 `AppMain.vue` 跑探针：

```
PASS  登录后落在首页 /index 且首页有内容 — .app-main innerHTML length=669     ← 多根节点页正常渲染
FAIL  ① 离开首页 → 内部人员授权 — .app-main innerHTML length=7
FAIL  ②…⑥ 全部 length=7
== SYS-WEB-001 菜单导航回归探针 2/9 通过 ==
```

**多根节点（无注释）一样卡死**——因为 `remove()` 的普通 Fragment 分支走 `removeFragment`，同样不看 Fragment 的 `transition`。D2–D7 每个 WEB ticket 都要新增页面，「模板写两个根节点」是极常见的写法，所以只挪注释等于留一颗地雷。改 `AppMain.vue` 才是把整类缺陷关掉（`out-in` 是**唯一**会设置 `isLeaving` 的分支，去掉它后这条路径不存在）。

**过渡动画语义有没有丢？没有。**

| | 修复前 | 修复后 |
|---|---|---|
| 进入动画（`animate__animated animate__fadeIn`） | 有 | **有**（探针 ① 实测录到这两个类挂在新页根元素上） |
| 离开动画 | **无**：全项目 `src/assets/styles/**` + element-plus 都没有 `.v-leave-from/-active/-to` 定义；本组件也只给了 `enter-active-class` | 无（同前） |
| `out-in` 的「旧页先走完 leave、新页再进」 | 名义上有，但 leave 时长为 **0s**（探针在首页根元素上读到 `animationDuration: 0s / transitionDuration: 0s`），等价于立即切换 | 立即切换（同上） |

即：`mode="out-in"` 在这份上游代码里的实际视觉效果是 **0**。唯一的「语义」是把「等 leave 完成」的责任交给了 `afterLeave`，而这个责任在非元素根页面上必然丢失。

**换别的方式行不行（都试过/推演过，都不如它）**

| 方案 | 结论 |
|---|---|
| 去掉 `index.vue` 顶层注释 | 只治今天这一颗雷；多根节点页照样白屏（上面已实测），且会顺便毁掉探针的负对照与这条缺陷的可复现性 |
| 保留 `out-in`、补 `leave-active-class` | **没用**：根是 Fragment 时 leave 钩子根本不在子元素上，`remove()` 连 `leave()` 都不调 |
| 在 `AppMain` 里给 `router-view` 的内容套一层 `<div :key>` 让根恒为元素 | 会改变 `.app-main` 的 DOM/高度语义，更糟的是 `:key` 在外层会**每次路由都重建 keep-alive 子树的缓存**（等于废掉 `cachedViews` 缓存），是重新设计布局 |
| 自己写 `onAfterLeave` 兜底复位 | 复位回调同样从 `transition.afterLeave` 来，丢的就是它，兜不住 |
| 用 `mode="in-out"` | 同属「需要单一元素根」的语义，`delayLeave`/`el[leaveCbKey]` 一样会退化；且 `in-out` 会让新旧两页同屏，反而更危险 |
| 依赖上游修复 | **上游没有修复可 backport**：`JavaLionLi/plus-ui` master（本轮 fetch 核对）与本地 5.6.1 参考项目 `dongjiaoshan/code/main/plus-ui` 的 `AppMain.vue` **至今仍写着 `mode="out-in"`** |

---

## 4 探针与正负对照（证明探针真的在测这件事，不是恒绿）

探针：`doc/waves/reports/D1-rework-S1/probe-web-menu-nav.mjs`
**故意从 `/index` 出发**（即从那个 Fragment 根页面离开），断言链：
登录落首页 → ① 点「内部人员授权」（**回归点**：离开首页）→ ②「外部用户」→ ③「字典管理」→ ④「参数设置」→ ⑤ 回首页 → ⑥ 再离开一次首页到「来源单位与组别」；每跳都断言 `.app-main innerHTML > 1000` + URL + 页面关键文案；① 额外断言 `animate__fadeIn`/`animate__animated` 类出现过；末尾断言全程无 `console.error`/`pageerror`。

| 实验 | 命令 | 结果 |
|---|---|---|
| **修复后（headless）** | `node doc/waves/reports/D1-rework-S1/probe-web-menu-nav.mjs` | **9/9 绿**，各页 `innerHTML` = 594 / 11533 / 21707 / 42198 / 39121 / 594 / 19344 |
| **负对照 1：回退修复（headless）** | `git stash push -- …/AppMain.vue` → 同一条命令 | **2/9**：①–⑥ 全红、`innerHTML` = **7**；截图落在 `shots-negative/` |
| **负对照 2：回退修复 + 多根节点页（headless）** | 临时给 `index.vue` 加第二个根（无注释）→ `git stash` → 探针 | **2/9**：①–⑥ 全红、`length=7`（证明「不是注释的锅」） |
| **修复后（真·有头 Chrome）** | `HEADLESS=0 CHANNEL=chrome node …` | **9/9 绿**，截图 `shots/probe-0*-headed.png` |
| **负对照 3：回退修复（有头 Chrome）** | `HEADLESS=0 CHANNEL=chrome` + `git stash` | **2/9**：①–⑥ 全红、`length=7` ← **真人浏览器同样中招** |

回退/还原均用 `git stash push/pop`（路径限定到 `AppMain.vue`）或 `git checkout --`，收尾 `git status` 已确认只剩本报告目录与那一处修复。

**另外**：QA 自己的 L2 资产 `doc/waves/regression/D1/L23-web-plusui.mjs` 在修复后跑出 **15/16**，唯一 FAIL 正是它内建的「缺陷复现」断言（`blankLen <= 20`，现在实测 `len=11533`）——即**业务断言一条没退，只有那条「记录缺陷复现」的快照式断言按预期翻红**（见 §8 待办）。

---

## 5 dev 走查与构建门

### 5.1 `pnpm dev` 人工/自动走查（8082 → 8081）

见 §4 表格：**内部人员授权（5120）/ 外部用户（5130）/ 字典管理 / 参数设置 / 首页 / 来源单位与组别 全部正常、无白屏、无 console 报错**，且 enter 动画仍在。
修复后截图（人眼可核）：`shots/probe-01-staff-headless.png`（= 之前空白的同一页，现在正常出「按手机号授权」+ 两行 seed 账号）、`probe-02-extuser-headless.png`、`probe-03-dict-headless.png`、`probe-04-config-headless.png`、`probe-05-unit-headless.png`。
同时用起来仍正常的既有能力（未改）：`keep-alive :include=cachedViews` 缓存、tags-view、`iframe-toggle`、`:key="route.path"`。

### 5.2 生产构建

```
$ cd code/plus-ui && rm -rf dist && npm_config_store_dir=<ws>/.pnpm-store pnpm build:prod
EXIT=0                      （vite 6.4.1，dist/ 8.2M，index.html 含「类器官样本管理」1 处）
$ grep -rl 'SYS-WEB-001：上游的演示首页' dist     → 无命中（注释被丢弃，与 §2.3 一致）
$ 产物里 AppMain 的 render → nt(Is,{"enter-active-class":I(o)},…)   无 mode
```

> **WARN-4**：主会话给的 `pnpm --store-dir <ws>/.pnpm-store build:prod` 在这台机的 **pnpm 10.33.0 上不解析** —— pnpm 把 `<path>` 当成脚本名去 spawn：`ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL / Command failed with EACCES: <ws>/.pnpm-store build:prod`。等价可用写法是 `npm_config_store_dir=<abs> pnpm build:prod`（本轮用的就是这个）。后续 ticket 的 accept 里若写 `--store-dir` 会误红。

### 5.3 没有引入新的控制台报错

探针全程断言 `consoleErrors.length === 0`（含 `pageerror`、`console.error`）→ 修复后 9/9 跑法下均为空。修复前也一样为空（这条缺陷**本来就是完全静默的**，这正是它危险的地方）。

---

## 6 改动清单

| 文件 | 改动 | 说明 |
|---|---|---|
| `code/plus-ui/src/layout/components/AppMain.vue` | `-1 +16` | template 删掉 `mode="out-in"`；`<script setup>` 补 16 行因果注释（讲清 Fragment 根 → `afterLeave` 丢失 → `isLeaving` 卡死，防止后人手滑加回去） |
| `doc/waves/reports/D1-rework-S1.md`、`doc/waves/reports/D1-rework-S1/**` | 新增 | 本报告 + 探针 + 前后截图 + 编译产物证据 |

**一个字节都没动**：`doc/verify/api.sh`、`doc/verify/seed/`、`doc/verify/gen_seed.py`、`doc/requirements.yaml`、`doc/authority/*.yaml`、`doc/change-log.md`、`_manifest.json`、`doc/waves/state.json`、QA 的三个 S2 项。
三条 S2（`SYS-MP-001` 小字断言 grep 到注释 / 「首页没有数字」黑名单可绕过 / `AUTH-STAFF-001` `roleKey` 白名单被折叠）**本轮一条没碰**，按规矩留在台账等 all_done。

---

## 7 ★ 越界 WARN-1（SYS-WEB-001 §3 边界被突破）

**越界事实**：`doc/tickets/SYS-WEB-001/prompt.md` §3「边界（明确不做）」写着「不做深色主题、**不改上游布局组件**」；`AppMain.vue` 就是上游布局组件。虽然它落在 `touches: code/plus-ui/**` 的路径语义里（所以不是越出 `touches`），但 **§3 这条语义边界被突破了**，按要求显式记账。

**为什么非改不可**
1. 这是 D1 唯一拦门的 S1，且不是「体验差」而是**工作台在开发/人工验收路径下除首屏等于不可用**（必须每次手改地址栏刷新）；
2. 它是 D2–D7 **每一个 WEB ticket** 的 L2 人工/自动走查的必经路径（走查就是在 dev server 上点菜单），不改就会持续吃掉后续所有 WEB ticket 的验收时间；
3. 它**没有任何项目内的替代修法**：唯一不动布局组件的做法是「保证每个路由页都只有一个元素根」，而这既管不住未来页面（多根节点照样白屏，已实测），也无法修好已存在的 `index.vue`（除非同时改 `index.vue` 的注释 —— 那是另一种踩边界，且只是掩盖）；
4. 改动是**对上游缺陷的修补而非重新设计**：删掉 1 个属性，0 行样式、0 处 DOM 结构变化、`keep-alive`/`:key`/`iframe-toggle` 全未动。

**风险与代价（如实记）**
- 与上游分叉（**WARN-2**）：上游 master 至今仍写 `mode="out-in"`，将来同步上游时这行会冲突，必须人工保留本改动；
- 如果后续真要做「旧页离场动画」，必须先**保证所有路由页单一元素根**并显式给 `leave-active-class`，否则这个白屏会以新的形式回来（详见 §8）；
- 越界已在本报告与 commit message 里标明，主会话如需回滚：`git revert <本 commit>` 即恢复上游原样（修复前状态可复现，探针会转红）。

**WARN-2**：与上游分叉（同上）。
**WARN-3**：QA 资产需在 r2 翻转一条断言（见 §8.1）。
**WARN-4**：`pnpm --store-dir` 在 pnpm 10.33.0 不解析（见 §5.2）。
**WARN-5（残留、非新增）**：开发态下**首页的 enter 动画本来就不播**（Vue 对非元素根不做动画：`renderComponentRoot` 会把钩子挂在 Fragment 上）。这是 `index.vue` 顶层注释在 dev 下的既有副作用，**不属于本次 S1**，因此没有顺手改 `index.vue`；构建产物里首页是元素根、动画正常。留档，避免后续误判成新问题。

---

## 8 对 D2–D7 各 WEB ticket 的影响 / 要注意什么

### 8.1 立刻要做的（给 QA r2 / 主会话）
1. **`doc/waves/regression/D1/L23-web-plusui.mjs` 里那条「缺陷复现」断言现在是红的、且是应当红的**：
   `check('【缺陷复现】…', blankLen <= 20, …)`（脚本第 87–92 行）。修复后实测 `len=11533`。
   → **r2 请把它翻成正向断言**（例如 `blankLen > 1000 && 页面含「按手机号授权」`），或直接删掉并把「点菜单 → 正常渲染」并入 ① 的断言。**我这轮没有改 QA 资产**（按规矩只报不改）。我这一轮跑它是为了看业务断言有没有被我改坏：**15/16，唯一红的就是这一条**（跑完已用 `git checkout -- doc/waves/regression/D1/shots-L23/` 还原它覆盖掉的截图，`git status` 干净）。
2. 给 r2 的复现资产：探针 `doc/waves/reports/D1-rework-S1/probe-web-menu-nav.mjs`（可直接当 L2 的「菜单导航」子断言复用，含 headless / 有头两种跑法）。

### 8.2 写 WEB 页面时的硬约束（重要）
3. **路由页面的模板必须恰好一个元素根**。以下两种写法都会让该页根成为 Fragment：
   - 模板**顶层写 HTML 注释**（dev 保留注释 → `DEV_ROOT_FRAGMENT`；生产构建会丢注释，于是「dev 和生产不一样」）；
   - 写**多个根节点**。
   后果（修复后）：不再是白屏（`out-in` 已去掉），但**该页的 enter 动画会静默不播**（Vue 明确警告过 `Component inside <Transition> renders non-element root node that cannot be animated.`），并且在 dev/生产之间表现不一致。
   → 需要写说明注释时，请放进 `<script setup>`，或写进那个唯一根元素的**内部**。
4. **绝对不要给这个 `<transition>` 加回 `mode="out-in"`（或 `in-out`）**。加了就重新引入「离开 Fragment 根页面 → 整个工作台永久空白、控制台无报错」这条 S1。`AppMain.vue` 的注释里写了因果，r2 的 code review 请把这条当红线。
5. 页面级动效/过渡请**留在页面自己内部**（像上游其它页面那样用 `animate.searchAnimate` 的 enter/leave class），不要依赖 `AppMain` 的 `out-in` 语义。

### 8.3 其它
6. `keep-alive :include="tagsViewStore.cachedViews"` 的缓存语义**未变**：`cachedViews` 里的名字必须是页面的 `name`（`<script setup name="Xxx">` / `defineOptions`）。本轮已验证 `['Index','Staff5110']` 这类缓存项照常进出，`onActivated` 行为不变。
7. 首页（`views/index.vue`）在 dev 下不播 enter 动画这条残留见 **WARN-5**，谁会动 SYS-HOME-001 的首页，谁可以顺手把那条顶层注释挪进根 `<div>` 里（一行改动，dev 下就与其它页一致了）——我这轮刻意没做，避免掩盖本轮 S1 的可复现性。
8. 本轮修复对**构建产物**零影响（`build:prod` 通过、品牌与产物正常），所以「老师走构建产物」的路径保持原样；改善的只有开发/人工验收路径——**恰好就是 QA 门与后续 WEB ticket 走的那条**。

---

## 9 收尾状态（本 agent 落盘时）

- 起过的长进程已全部关掉：`pnpm dev`（8082/8083）已 kill；后端 java 用 `pkill -9 -f 'ruoyi-admin.jar'`；
- 8080/8081/8082/8083/8099/9200/9201 无监听；三个 dev 容器（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002/9003）没停；
- 收尾跑了一次 `bash doc/verify/reseed.sh --yes`（库回到确定性快照）；
- 临时实验产物（`.tmp/*.mjs`、`node_modules/.vite/deps` 的桩、`index.vue` 的多根实验）全部还原；`.tmp/` 与 `node_modules/` 均被 gitignore 覆盖；
- `git status --short` 只剩：`code/plus-ui/src/layout/components/AppMain.vue`（本修复）与 `doc/waves/reports/D1-rework-S1/`（本报告与证据）；
- 未 push、未 merge、未动 `_manifest.json`。
