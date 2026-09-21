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
accept:
  - name: "五个数与库里独立数的一致并钉在 seed 上；外部新交一条类器官收样、一条石蜡包埋送样后两个待核验数各加一；超期数与超期清单同源；渲染失败数会随真实失败变化"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/home/todo | jq -e '.data == {"pendingSamples":2,"pendingEmbeds":1,"cryoOverdue":2,"pendingExtUsers":2,"renderFailed":0}' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending') || '|' || (SELECT count(*) FROM t_lqg_embed WHERE del_flag='0' AND verify_status='pending') || '|' || (SELECT count(*) FROM t_lqg_ext_profile WHERE del_flag='0' AND bind_status='pending')" --eq "2|1|2" &&
      test "$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq '.data.cryoOverdue')" = "$(bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq '.data|length')" &&
      bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.pendingSamples==3 and .data.pendingEmbeds==2' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending' AND sample_kind='organoid') || '|' || (SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id WHERE e.del_flag='0' AND e.verify_status='pending')" --eq "1|2" &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=internal' >/dev/null ; sleep 5 ;
      python3 doc/verify/db.py --sql "SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed') x" --eq "$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -r '.data.renderFailed')" &&
      bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.renderFailed >= 1' &&
      bash doc/verify/api.sh --as staff GET /lqg/home/recent | jq -e '(.data|length) <= 10 and ([.data[].submitNo] | index("SJ90000010")) == null' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      渲染失败数写死成 0（「反正现在没有失败的」）→ 故意渲染一份图片地址是假的文档（1001 的样本质控表，seed 埋的病灶）之后仍是 0，红。
      待核验用户数把 unbound 也算进去 → 2 变 2+ 红。
      待核验样本只数组织样本 → 外部交了类器官之后仍是 2 红。待核验送样没做或数了全部外部送样（含已核验的）→ 1 / 2 那两段红。
      工作台首页和冻存列表各写各的超期 where → 与超期清单长度不等红。
      最近提交没过滤软删 → SJ90000010 出现红。
      两侧不同源：接口 vs 直连库；再由 seed 期望钉住。
  - name: "前端构建是本次产物；首页五张卡片接的是接口而不是写死的数；为 0 不隐藏；菜单角标与卡片同一个来源（含石蜡包埋）"
    form: API
    run: |-
      cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
      grep -q 'home/todo' src/api/lqg/home.ts && grep -c 'TodoCard' src/views/lqg/home/index.vue | awk '{exit !($1 >= 5)}' &&
      ! grep -nE 'v-if="[^"]*(pendingSamples|pendingEmbeds|cryoOverdue|pendingExtUsers|renderFailed)[^"]*> *0"' src/views/lqg/home/index.vue &&
      ! grep -nE 'echarts|el-statistic' src/views/lqg/home/index.vue &&
      grep -q 'pendingEmbeds' src/store/modules/lqgTodo.ts && grep -rq 'lqgTodo' src/layout/components/Sidebar && ! grep -rq 'home/todo' src/layout/components/Sidebar
    counterfeit: |-
      数为 0 的卡片用 v-if 隐藏 → 第 3 段红：实验室的人会分不清「没有待办」和「功能坏了」。
      顺手加了个送样量趋势图 → 第 4 段红（甲方没要，做了就得维护）。
      侧边栏自己再请求一次 home/todo（两个请求之间数字可能不同）→ 最后一段红；角标漏了石蜡包埋 → pendingEmbeds 那段红。
---

# SYS-HOME-001 · 工作台首页待办与菜单角标：五个数读时计算、同一个来源

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SYS` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**DOC-PDF-001**、**CRYO-REMIND-001**、**AUTH-GROUP-001**、**EMBED-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - 2026-09-17 晚 Kevin 定（CR-20260917-05）：小程序首页的数字去掉（「首页顶部数字去掉，不合理」），外部可提交石蜡包埋送样。所以工作台首页是唯一的待办计数，并多一张「待核验石蜡包埋送样」卡片
- [ ] 口径复述（本张最容易做反的）：
  1. 五个数**全部读时计算**，不落任何计数字段、不写死。
  2. **计数只有一个来源 `HomeCounterService`**（本张建）：卡片与菜单角标取同一次请求；超期数调 CRYO-REMIND-001 的 `countOverdue()`，别再写一遍 where。
  3. **待核验样本含组织与类器官两种**（外部也能交类器官收样记录了）；**待核验石蜡包埋送样** = 未删且 `verify_status='pending'` 的石蜡包埋记录。
  4. 数字为 0 时卡片变灰**不隐藏**。不做统计图表、不做看板。

## 1 背景与口径

设计回流 D-1（REQ-SYS-901）。冻存超两周的「提示」（REQ-CRYO-003）在工作台的落点就是这里。外部送样的核验入口（石蜡包埋页）靠第二张卡片与菜单角标提醒。

## 2 实现要点

- 后端 `org.dromara.lqg.sys.home`：`HomeCounterService`（`pendingSamples()`、`pendingEmbeds()`、`cryoOverdue()`、`pendingExtUsers()`、`renderFailed()`）。
- `GET /lqg/home/todo` → `{pendingSamples, pendingEmbeds, cryoOverdue, pendingExtUsers, renderFailed}`；`renderFailed` = `t_lqg_doc_file` 里 `render_status='failed'` 的 (样本, 文档种类, 版本) 组数。
  `GET /lqg/home/recent` → 最近提交 10 条（提交时间、送检单号、来源单位、内外部、核验状态）。登录即可调（101 / 102）。
- `views/lqg/home/index.vue` 挂到上游首页路由（`views/index.vue` 里替换内容）：五张 `TodoCard`（点击带筛选条件跳对应页面：待核验样本 → 样本总表 `verifyStatus=pending`；待核验送样 → 石蜡包埋 `verifyStatus=pending`；超期 → 冻存管理超期页签；外部用户 → 人员与单位；渲染失败 → 质控文档）+ 最近提交表格。
- 侧边菜单角标：样本总表 = `pendingSamples`、石蜡包埋 = `pendingEmbeds`、冻存管理 = `cryoOverdue`、人员与单位 = `pendingExtUsers`（0 不显示）；与卡片取同一次请求的结果（放 `store/modules/lqgTodo.ts`）。

## 3 边界（明确不做）

- 不做图表、趋势、统计
- 不做消息推送
- 不做可配置的首页
- 小程序里不做任何计数（CR-20260917-05）

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
