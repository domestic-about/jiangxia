---
ticket: SYS-HOME-001
track: SYS
phase: D7
size: S
req_refs:
  - REQ-SYS-901
depends_on:
  - DOC-PDF-001
  - CRYO-REMIND-001
  - AUTH-GROUP-001
  - EMBED-WEB-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sys/home/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sys/home/**
  - code/plus-ui/src/views/lqg/home/**
  - code/plus-ui/src/api/lqg/home.ts
  - code/plus-ui/src/lang/lqg/home.*.ts
  - code/plus-ui/src/views/index.vue
  - code/plus-ui/src/store/modules/lqgTodo.ts
  - code/plus-ui/src/layout/components/Sidebar/**
adr_refs: []
blueprint_refs:
  - UI:admin.home
  - FLOW:F-CRYO-01.step3
  - FLOW:F-SAMPLE-01.step2
  - FLOW:F-DOC-01.step6
  - FLOW:F-EMBED-01.step7
  - FIELD:t_lqg_doc_file.missing_image_count
accept:
  - name: "七个键与库里独立数的一致并钉在 seed 上（待核验样本按组织 / 类器官拆成两个数，总数 = 两者之和）；外部新交一条类器官收样、一条石蜡包埋送样后类器官与送样两个待核验数各加一；超期数与超期清单同源；渲染失败数会随真实失败变化；渲染异常 = 渲染失败加内部版缺图，与异常清单同一口径，清单外部 403"
    form: DATA
    run: |-
      RC=0 ; { bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/home/todo | jq -e '.data == {"pendingSamples":2,"pendingTissue":2,"pendingOrganoid":0,"pendingEmbeds":1,"cryoOverdue":2,"pendingExtUsers":2,"renderFailed":0}' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending') || '|' || (SELECT count(*) FROM t_lqg_embed WHERE del_flag='0' AND verify_status='pending') || '|' || (SELECT count(*) FROM t_lqg_ext_profile WHERE del_flag='0' AND bind_status='pending')" --eq "2|1|2" &&
      test "$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq '.data.cryoOverdue')" = "$(bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq '.data|length')" &&
      bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.pendingSamples==3 and .data.pendingEmbeds==2 and .data.pendingTissue==2 and .data.pendingOrganoid==1 and .data.pendingSamples==(.data.pendingTissue+.data.pendingOrganoid)' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending' AND sample_kind='organoid') || '|' || (SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id WHERE e.del_flag='0' AND e.verify_status='pending')" --eq "1|2" &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      docker stop "${LQG_GOTENBERG_CONTAINER:-lqg-dev-gotenberg}" >/dev/null 2>&1 &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=internal' | jq -e '.data.status=="failed"' ; } || RC=$? ;
      docker start "${LQG_GOTENBERG_CONTAINER:-lqg-dev-gotenberg}" >/dev/null 2>&1 ;
      for i in $(seq 1 30); do curl -sf "${LQG_GOTENBERG_URL:-http://127.0.0.1:3010}/health" >/dev/null 2>&1 && break; sleep 1; done ;
      test "${RC}" = 0 &&
      sleep 2 &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE del_flag='0' AND file_format='docx' AND page_no=0 AND (render_status='failed' OR (audience='internal' AND render_status='done' AND missing_image_count>0))) x" --eq "$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -r '.data.renderFailed')" &&
      bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.renderFailed >= 1' &&
      bash doc/verify/api.sh --as staff GET /lqg/home/recent | jq -e '(.data|length) <= 10 and ([.data[].submitNo] | index("SJ90000010")) == null and all(.data[]; .sampleKind=="tissue" or .sampleKind=="organoid") and ([.data[]|select(.submitNo=="SJ90000009")|.sampleKind]|unique)==["organoid"]' &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=internal' | jq -e '.data.status=="done" and .data.missingImageCount==3' &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=external' | jq -e '.data.status=="failed"' &&
      N="$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq '.data.renderFailed')" && test "${N}" = 2 &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE del_flag='0' AND file_format='docx' AND page_no=0 AND (render_status='failed' OR (audience='internal' AND render_status='done' AND missing_image_count>0))) x" --eq "${N}" &&
      bash doc/verify/api.sh --as staff GET /lqg/home/render-issues | jq -e --argjson n "${N}" '(.data|length)==$n and ([.data[].issue]|sort)==["failed","missing_images"]' &&
      bash doc/verify/api.sh --as extA --bizcode GET /lqg/home/render-issues | grep -qE '^403' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      渲染失败数写死成 0（「反正现在没有失败的」）→ 停掉 gotenberg 造一次**真实的**渲染失败之后仍是 0，红。
      renderFailed 只数 render_status='failed'、漏了内部版缺图 → 末尾那组红：seed 里 1001 样本质控表挂着 3 张假地址图，内部版照出 done 且记缺图 3 张、外部版 failed，渲染异常必须是 2，只数 failed 会得 1。两段对账 SQL（停 gotenberg 之后那段、末尾那组）口径相同：failed，或内部版 done 且 missing_image_count>0，与接口两侧独立算。
      卡片和异常清单各写各的 where → 清单行数 ≠ renderFailed、或 issue 集合不是 [failed, missing_images]，红：卡片说有 2 条异常，点开却对不上。
      异常清单只挂登录门 → extA 拿到跨单位的送检单号与失败原因，最后 403 那段红。
      （2026-09-23 按 CR-20260923-09 更正：撤回 2026-09-22 那段援引「issue #217 的裁定」、说假地址图片「跳过 + WARN、文档照出 done、不会让渲染失败」的更正——那个裁定查实不存在。现行口径：假地址图片会让外部版整份 failed、内部版照出但记缺图，两者都计入 renderFailed。停 gotenberg 那段保留：它造的是流水线步骤失败，与缺图是两类异常，都该被数到。）
      待核验用户数把 unbound 也算进去 → 2 变 2+ 红。
      待核验样本只数组织样本 → 外部交了类器官之后仍是 2 红。拆卡后 pendingSamples 不再等于两类之和（例如只剩一类、或另算一遍 where 算出不同的数）→ 第二个 todo 段的求和等式红；多出第八个键或少了 pendingTissue / pendingOrganoid → 第一段整对象相等红。
      最近提交没带 sampleKind（首页点一行就不知道进哪一页）→ recent 那段 all(...) 红；把类器官样本 1009 标成 tissue → 同段最后一格红。待核验送样没做或数了全部外部送样（含已核验的）→ 1 / 2 那两段红。
      工作台首页和冻存列表各写各的超期 where → 与超期清单长度不等红。
      最近提交没过滤软删 → SJ90000010 出现红。
      两侧不同源：接口 vs 直连库；再由 seed 期望钉住。
      停 gotenberg 那段的收尾：从第一段 reseed 到「渲染应失败」整段包在 `RC=0 ; { …; } || RC=$?` 里，任何一段失败都把失败码存进 RC，不会被 set -e 在 start 之前提前退出、也不会被 `;` 吞掉；之后无论成败先把 gotenberg 启回来并等 /health 通过，再由 `test "${RC}" = 0 && …` 这条链决定退出码——不把一个停掉的容器留给后面的任务。
      停 / 启的容器与健康检查地址读 `LQG_GOTENBERG_CONTAINER` / `LQG_GOTENBERG_URL`（缺省 = Kevin 本机的 lqg-dev-gotenberg 与 http://127.0.0.1:3010，CR-20260924-11）：2026-09-24 有一个隔离组照原样重放本条，停掉的是 Kevin 的 lqg-dev-gotenberg——隔离环境重放必须把这两个变量设成自己那一套（见 doc/verify/README.md「隔离环境重放」）。
  - name: "前端构建是本次产物；首页六张卡片接的是接口而不是写死的数（**真 DOM 的数字 == 同一次 /lqg/home/todo 的返回值**）；为 0 不隐藏；菜单角标与卡片同一个来源（含石蜡包埋，两张样本表各取各的数）"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -q 'home/todo' src/api/lqg/home.ts && grep -c '<TodoCard' src/views/lqg/home/index.vue | awk '{exit !($1 >= 6)}' &&
      grep -q 'todo.pendingTissue' src/views/lqg/home/index.vue && grep -q 'todo.pendingOrganoid' src/views/lqg/home/index.vue &&
      ! grep -nE 'v-if="[^"]*(pendingSamples|pendingTissue|pendingOrganoid|pendingEmbeds|cryoOverdue|pendingExtUsers|renderFailed)[^"]*> *0"' src/views/lqg/home/index.vue &&
      ! grep -nE 'echarts|el-statistic' src/views/lqg/home/index.vue &&
      grep -q 'pendingEmbeds' src/store/modules/lqgTodo.ts && grep -q 'pendingTissue' src/store/modules/lqgTodo.ts && grep -q 'pendingOrganoid' src/store/modules/lqgTodo.ts && grep -rq 'lqgTodo' src/layout/components/Sidebar && ! grep -rq 'home/todo' src/layout/components/Sidebar &&
      cd ../.. && bash doc/waves/regression/D7/mutation-assert.sh --verify-only --hotspot H2
    counterfeit: |-
      数为 0 的卡片用 v-if 隐藏 → 第 3 段红：实验室的人会分不清「没有待办」和「功能坏了」。
      顺手加了个送样量趋势图 → 第 4 段红（甲方没要，做了就得维护）。
      侧边栏自己再请求一次 home/todo（两个请求之间数字可能不同）→ 最后一段红；角标漏了石蜡包埋 → pendingEmbeds 那段红；两张样本表的角标仍共用 pendingSamples → pendingTissue / pendingOrganoid 那段红。
      拆页后首页仍只有一张「待核验样本」卡（没拆）→ `<TodoCard` 少于 6 或 index.vue 里没有 todo.pendingTissue / todo.pendingOrganoid，红。
      ★ **「数字是读时算的、页面真的展示接口值」不再由源码 grep 判**（旧写法只 grep「卡片接了接口」，D7 r1 L2 证伪 F4：
      把 `:value="todo.pendingSamples"` 改成写死的 7、`pendingEmbeds` 改成 9（真值 2/1），acc1+acc2 全绿，
      而工作台首页当众显示 7 / 9）。现在由 `mutation-assert.sh --hotspot H2` 判：
      真浏览器打开工作台首页，从**真 DOM** 读五张卡片的数字（`.lqg-todo-card__num`），
      与**同一次** `GET /lqg/home/todo` 的返回值逐个比对；已定义变异 = 把两张卡写死成 7 / 9 → 判据必须变红，还原必须复绿。
---

# SYS-HOME-001 · 工作台首页待办与菜单角标：待办数读时计算、同一个来源

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-PDF-001**、**CRYO-REMIND-001**、**AUTH-GROUP-001**、**EMBED-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 2026-09-17 晚 Kevin 定（CR-20260917-05）：小程序首页的数字去掉（「首页顶部数字去掉，不合理」），外部可提交石蜡包埋送样。所以工作台首页多一张「待核验石蜡包埋送样」卡片
  - **CR-20260924-10（以它为准）**：① 工作台「样本总表」拆成「样本记录信息表」（/sample，组织样本）与「类器官收样记录」（/sample-organoid，类器官）两页，首页「待核验样本」随之拆成两张卡，`HomeTodoVo` 加 `pendingTissue` / `pendingOrganoid`，`pendingSamples` **保留且恒等于两者之和**；侧边菜单红点按页分开；「最近提交」多 `sampleKind`、界面多「所在表」一列，点一行按类别进对应页。② 甲方要求内部人员的**小程序首页也显示待办**：小程序内部首页「待处理」读同一个 `GET /lqg/home/todo`（只用 `pendingSamples / pendingEmbeds / cryoOverdue`），所以 `pendingSamples` 不能删、口径不能改
- [ ] 口径复述（本张最容易做反的）：
  1. 七个数（`pendingSamples = pendingTissue + pendingOrganoid`、`pendingEmbeds`、`cryoOverdue`、`pendingExtUsers`、`renderFailed`）**全部读时计算**，不落任何计数字段、不写死。
  2. **计数只有一个来源 `HomeCounterService`**（本张建）：卡片与菜单角标取同一次请求；超期数调 CRYO-REMIND-001 的 `countOverdue()`，别再写一遍 where。
  3. **待核验样本含组织与类器官两种**（外部也能交类器官收样记录了），按类别各数一次、总数是两者之和（CR-20260924-10）；**待核验石蜡包埋送样** = 未删且 `verify_status='pending'` 的石蜡包埋记录。
  4. 数字为 0 时卡片变灰**不隐藏**。不做统计图表、不做看板。
  5. **「文档渲染失败」卡片 = 渲染失败加内部版缺图**（CR-20260923-09，`FLOW:F-DOC-01.step6`）：假地址图片会让外部版整份 `failed`、内部版照出但记缺图，两者都算；卡片点开的异常清单与卡片上的数同一段 WHERE，清单同样只给内部角色。

## 1 背景与口径

设计回流 D-1（REQ-SYS-901）。冻存超两周的「提示」（REQ-CRYO-003）在工作台的落点就是这里。外部送样的核验入口（石蜡包埋页）靠第二张卡片与菜单角标提醒。

## 2 实现要点

- 后端 `org.dromara.lqg.sys.home`：`HomeCounterService`（`todo()` 按类别各数一次待核验样本、`pendingSamples()`、`pendingEmbeds()`、`cryoOverdue()`、`pendingExtUsers()`、`renderFailed()`、`renderIssues()`）。
- `GET /lqg/home/todo` → `{pendingSamples, pendingTissue, pendingOrganoid, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}`（恰好七个键；`pendingSamples = pendingTissue + pendingOrganoid`，CR-20260924-10）；`renderFailed` = 渲染异常的 (样本, 文档种类, 内部版 / 外部版) 组数 = **渲染失败加内部版缺图**：`t_lqg_doc_file` 的 docx 头行 `render_status='failed'`，或内部版 `render_status='done'` 且 `missing_image_count>0`（CR-20260923-09；JSON 键名仍叫 `renderFailed`）。
  `GET /lqg/home/render-issues` → 渲染异常清单 `[{sampleId, internalNo, submitNo, sourceUnitName, docKind, audience, issue: failed|missing_images, errorMsg, missingImageCount, missingImages, time}]`，与 `renderFailed` 同一段 WHERE（最多列 200 行；不超过时卡片上的数 == 清单行数）。
  `GET /lqg/home/recent` → 最近提交 10 条（提交时间、送检单号、`sampleKind`、来源单位、内外部、核验状态；`sampleKind` 用来按类别进对应那一页，CR-20260924-10）。
  三个接口都挂内部角色闸（`lqg_admin` / `lqg_internal` / `superadmin`，任一即可），外部账号 403。
- `views/lqg/home/index.vue` 挂到上游首页路由（`views/index.vue` 里替换内容）：六张 `TodoCard`（点击带筛选条件跳对应页面：待核验样本记录 → `/sample?verifyStatus=pending`；待核验类器官收样 → `/sample-organoid?verifyStatus=pending`（CR-20260924-10，路径统一取自 `views/lqg/sample/pages.ts`）；待核验送样 → 石蜡包埋 `verifyStatus=pending`；超期 → 冻存管理超期页签；外部用户 → 人员与单位；渲染失败 → 点开是异常清单抽屉，点一行进该样本质控页，可一键重新生成（`render?force=true`，一定重出））+ 最近提交表格（多「所在表」一列，点一行按 `sampleKind` 进对应页）。
- 侧边菜单角标：样本记录信息表 = `pendingTissue`、类器官收样记录 = `pendingOrganoid`（CR-20260924-10）、石蜡包埋 = `pendingEmbeds`、冻存管理 = `cryoOverdue`、人员与单位 = `pendingExtUsers`（0 不显示）；与卡片取同一次请求的结果（放 `store/modules/lqgTodo.ts`）。
- ⚠️ accept 2 末段的 `mutation-assert.sh --hotspot H2` 依赖 `doc/waves/regression/D7/accept-strengthened/` 里的两处旧定义：`probe.mjs` 断「真 DOM 渲染出五张待办卡片」并按关键字「样本 → pendingSamples」对数，`mutate.py` 的 H2 变异替换 `:value="todo.pendingSamples"`。拆卡后首页是六张、样本两张卡读 `pendingTissue` / `pendingOrganoid`，**这两处不改 H2 必红**（变异找不到目标、卡片数对不上）。`doc/waves/**` 不在本次文档组的改动范围，由调度者同步改成六张卡、样本卡对 `pendingTissue` / `pendingOrganoid`、变异写死 `todo.pendingTissue`。

## 3 边界（明确不做）

- 不做图表、趋势、统计
- 不做消息推送
- 不做可配置的首页
- 小程序里不另做计数接口：小程序内部首页「待处理」读同一个 `/lqg/home/todo`（CR-20260924-10 推翻了 CR-20260917-05 的「小程序里不做任何计数」）

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 1 修 set -e 缺陷（从 reseed 到「渲染应失败」包进 `RC=0 ; { …; } || RC=$?`，无论成败先启回 gotenberg 并等 /health 再判结果），对账 SQL 改为「failed 或内部版 done 且缺图」，末尾追加渲染异常 = failed + 内部版缺图、清单与数字同一口径、外部 403 三段；撤回援引不存在的 #217 裁定的更正；§0 / §2 写明 renderFailed 新口径、`GET /lqg/home/render-issues` 与卡片点开的异常清单抽屉。
- 2026-09-24 按 CR-20260924-10 更新：待核验样本拆成 `pendingTissue` / `pendingOrganoid` 两张卡（`pendingSamples` 保留 = 两者之和，小程序内部首页也读它）——accept 1 整对象相等改为七个键、外部交类器官后断两类之和、recent 断 `sampleKind`；accept 2 卡片数 ≥ 6、禁字表加两个新键、store 必须有两个新键；§0 / §2 / §3 按拆页与小程序首页待办改写，并写明 H2 变异定义需由调度者同步。
- 2026-09-24 按 CR-20260924-11 更新：accept 1 停 / 启转换服务改为 `docker stop|start "${LQG_GOTENBERG_CONTAINER:-lqg-dev-gotenberg}"`、健康检查读 `${LQG_GOTENBERG_URL:-http://127.0.0.1:3010}`，不再用 dev compose；缺省值与原来相同，Kevin 本机的 gate 不用改，隔离环境重放必须设成自己的。断言本身不变。
