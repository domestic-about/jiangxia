---
ticket: SAMPLE-HINT-001
track: SAMPLE
phase: D3
size: S
req_refs:
  - REQ-SAMPLE-010
depends_on:
  - EMBED-MODEL-001
  - SAMPLE-WEB-001
  - SAMPLE-MP-002
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sample/hint/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/sample/hint/**
  - code/plus-ui/src/views/lqg/sample/index.vue
  - code/plus-ui/src/views/lqg/sample/HintBadges.vue
  - code/miniapp/src/pages/ledger/sheets.ts
adr_refs: []
blueprint_refs:
  - FLOW:F-SAMPLE-02.step4
  - UI:admin.sample.list.hint
  - FIELD:t_lqg_embed.section_time
  - FIELD:t_lqg_embed.stain_types
  - FIELD:t_lqg_embed.verify_status
accept:
  - name: "提示逐样本钉死在 seed 上（两块一切片 HE+IHC；无染色不算染色；软删的块不计；待核验的外部送样不算块；没有包埋的也有零值）；各行块数之和与库里独立数的一致"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      ROWS="$(bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/lqg/sample/list?pageSize=100')" &&
      h() { printf '%s' "${ROWS}" | jq -c --arg id "$1" '.rows[] | select((.id|tostring)==$id) | [.hint.blockCount, .hint.sectioned, .hint.stains]'; } &&
      test "$(h 9000001001)" = '[2,true,["HE","IHC"]]' && test "$(h 9000001004)" = '[1,true,[]]' &&
      test "$(h 9000001006)" = '[1,true,["OTHER"]]' && test "$(h 9000001008)" = '[0,false,[]]' && test "$(h 9000001002)" = '[0,false,[]]' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id AND s.del_flag='0' WHERE e.del_flag='0' AND e.verify_status='valid'" --eq "$(printf '%s' "${ROWS}" | jq '[.rows[].hint.blockCount]|add')" &&
      bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' | jq -e '[.rows[]|select((.id|tostring)=="9000001001")|.hint.blockCount]==[2]'
    counterfeit: |-
      聚合 SQL 手写、忘了 e.del_flag='0' → 1008 名下那块软删的石蜡块被数进来，[1,true,["HE","IF"]] 红。seed 里这块就是为这条埋的。
      把 NONE 当成一种染色 → 1004 变成 ["NONE"] 红。
      没有包埋记录的行 hint 为 null → 1002 那段红（前端会 undefined.blockCount）。
      把外部提交还没核验的送样也数成一块 → 1002 变成 [1,false,[]] 红（seed 里 2006 就是为这条埋的）。
      两侧不同源：一侧是接口逐行返回的块数求和，一侧是直连库的 count。
  - name: "没有在样本表上偷加冗余字段；工作台提示组件已接入总表；一页只发一次聚合查询"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_sample --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      grep -q 'HintBadges' code/plus-ui/src/views/lqg/sample/index.vue && test -f code/plus-ui/src/views/lqg/sample/HintBadges.vue &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='SampleHint*Test' -Dsurefire.failIfNoSpecifiedTests=true)
    counterfeit: |-
      为了列表快，在 t_lqg_sample 上加了 block_count / has_section 并在保存石蜡块时回写 → ddl_vs_ssot 报「库里有、SSOT 没有」红。
      单测里要有一条：传 20 个样本 id 只触发 1 次查询（用 Mapper spy 或 SQL 计数）——逐行查的实现过不了。
---

# SAMPLE-HINT-001 · 样本总表的切片染色提示：读时计算，工作台一列、小程序表格页一列

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/SAMPLE` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**EMBED-MODEL-001**、**SAMPLE-WEB-001**、**SAMPLE-MP-002**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 口径复述（本张最容易做反的）：
  1. **读时算，不落库、不手填**。在样本表上加 `has_section` 之类的字段 = 第二个真相源，迟早和包埋记录对不上。
  2. 软删的石蜡块不计；「无染色 NONE」不算一种染色。
  3. 一页 20 行不要发 20 次查询：按本页的样本 id 集合一次聚合查出来。
  4. **只数已核验有效的石蜡块**：外部提交还没核验（或判了无效）的送样还没有石蜡块编号，不是一块石蜡（CR-20260917-05）。

## 1 背景与口径

会上 L97：领导想在收样记录里看到这个样本做了切片或者染色的情况，要有提示。

## 2 实现要点

- `SampleHintService.hintsOf(Collection<Long> sampleIds)` → `Map<Long, HintVo{blockCount, sectioned, stains}>`：一条 GROUP BY 查询（`del_flag='0' AND verify_status='valid'`）；`stains` = 各块 `stain_types` 拆开后的并集去掉 NONE，按字典顺序。
- `/lqg/sample/list`、`/mp/int/sample/list` 的每行挂 `hint`（没有包埋记录的也要有：`{blockCount:0, sectioned:false, stains:[]}`）。
- 工作台 `HintBadges.vue`：「石蜡块 N」「已切片」+ 染色缩写；悬停列出各石蜡块编号与切片时间（悬停时再查）；点击带 `sampleId` 跳石蜡包埋页。没有包埋记录显示「—」。
- 小程序表格页「样本记录」「类器官收样」两个工作表的最后一列「切片染色」填上同样的徽标（SAMPLE-MP-002 已留好这一列）。

## 3 边界（明确不做）

- 不给外部列表加提示（外部在详情里直接看包埋情况，AUTH-EXT-002）
- 不统计冻存、不统计质控文档
- 不做成可筛选条件

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
