---
ticket: CRYO-REMIND-001
track: CRYO
phase: D4
size: S
req_refs:
  - REQ-CRYO-003
  - REQ-CRYO-901
depends_on:
  - CRYO-FLOW-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/cryo/remind/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/cryo/remind/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260924120*__CRYO-REMIND-001-*.sql
adr_refs: []
blueprint_refs:
  - FLOW:F-CRYO-01.step2
  - FLOW:F-CRYO-01.step3
  - FIELD:t_lqg_cryo_batch.freeze_time
  - FIELD:t_lqg_cryo_batch.to_ln2_time
  - FIELD:t_lqg_cryo_batch.in_minus80
accept:
  - name: "超期清单钉死在 seed 的边界病灶上：阈值取系统参数 `lqg.cryo.overdue-days`（默认 14，CR-20260918-07）——恰好第 14 天算、第 13 天不算、取空了不算、直接进液氮不算、软删不算；清单、页签计数、只看超期三处同源相等，计数函数与清单同源"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/cryo/overdue | jq -e '([.data[] | "\(.id|tostring):\(.overdueDays)"] | sort) == ["9000003001:6","9000003005:0"]' &&
      python3 doc/verify/db.py --sql "SELECT b.id FROM t_lqg_cryo_batch b WHERE b.del_flag='0' AND b.in_minus80='Y' AND b.to_ln2_time IS NULL AND CURRENT_DATE - b.freeze_time >= (SELECT config_value::int FROM sys_config WHERE config_key='lqg.cryo.overdue-days') AND b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0) > 0" --col-set 9000003001,9000003005 &&
      LIST="$(bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100')" &&
      printf '%s' "${LIST}" | jq -e '.tabCounts == {"all":7,"overdue":2,"ln2":2} and ([.rows[]|select(.overdue)|.id|tostring]|sort)==["9000003001","9000003005"] and ([.rows[0:2][].overdue]==[true,true])' &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?overdueOnly=true&pageSize=100' | jq -e '(.rows|length)==2' &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='CryoOverdue*Test' -Dsurefire.failIfNoSpecifiedTests=true)
    counterfeit: |-
      判定写成「大于阈值天数」→ 少了恰好第 14 天的 3005 红。
      漏了「剩余 > 0」→ 多出已取空的 3004 红：提醒人去把一个空盒子转进液氮。
      漏了「没登记转液氮」→ 多出 3003 红。把直接进液氮的 3007 也算进来 → 红。
      页签计数和清单各写各的 where → tabCounts.overdue 与清单长度不等红；countOverdue 另写一份 → 单测里「计数 = 清单长度」那条红，工作台首页的数字与列表对不上。
      迁移没把 `lqg.cryo.overdue-days` 插进 sys_config → 独立 SQL 的子查询是 NULL、清单查空，--col-set 红（默认值只写在 java 里不算数：甲方要在工作台改得着）。
      两侧不同源：接口 vs db.py 里独立写的 SQL（阈值两边都从 sys_config 取，不写字面量）；再由 seed 的期望集合钉住。
  - name: "提醒跟着状态走：登记转液氮当场出清单、超期计数减 1；支数取空了也当场出清单（CR-20260918-07 甲方问「转移后还会有提示吗」）；判定函数只有一处"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==2' &&
      bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"2号罐-1架-A1"}))')" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '[.data[].id|tostring]==["9000003005"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==1 and ([.rows[]|select((.id|tostring)=="9000003001")|.overdue]==[false])' &&
      bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003005/flow '{"flowType":"take","qty":2,"purpose":"全部取用"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '.data==[]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==0' &&
      bash doc/verify/api.sh --as extA --bizcode GET /lqg/cryo/overdue | grep -qE '^403' &&
      test "$(grep -rlE 'freeze_time|freezeTime' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg --include=*.java | xargs grep -lE '\b14\b|OVERDUE_DAYS' | grep -v '/cryo/remind/' | wc -l | tr -d ' ')" = 0 &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      超期存成了标志位、靠每天的定时任务刷新 → 转了液氮之后到第二天早上 8 点前，清单里还挂着它，第 4、5 段红。这正是甲方 9-18 问的那句「转移后还会有提示吗」（CR-20260918-07）。
      清单去掉了它、页签数字另算一份 → tabCounts.overdue 还是 2 红：甲方看见的是那个数字。
      阈值口径（`14` 或 `OVERDUE_DAYS` 常量）散落在 remind 包之外的别处（比如列表 service 自己又写了一遍）→ 最后一段 grep 红；remind 包里也只许留「读不到参数时的回落值」这一处。
      结尾 reseed：别把转了液氮的 3001 留给后面的断言。
  - name: "阈值是系统参数不是常量（CR-20260918-07）：sys_config 里种着 `lqg.cryo.overdue-days`=14；在工作台把它改成 13，下一次读清单就多出第 13 天的 3006、计数 2→3，改回 14 又退出"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/cryo/overdue | jq -e '([.data[].id|tostring]|sort)==["9000003001","9000003005"]' &&
      C="$(bash doc/verify/api.sh --as admin GET '/system/config/list?configKey=lqg.cryo.overdue-days&pageSize=10' | jq -e -c '.rows[0] | select(.configValue=="14") | {configId, configName, configKey, configType, remark}')" &&
      bash doc/verify/api.sh --as admin PUT /system/config "$(printf '%s' "${C}" | jq -c '. + {configValue:"13"}')" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '([.data[] | "\(.id|tostring):\(.overdueDays)"] | sort) == ["9000003001:7","9000003005:1","9000003006:0"]' &&
      bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==3' &&
      python3 doc/verify/db.py --sql "SELECT b.id FROM t_lqg_cryo_batch b WHERE b.del_flag='0' AND b.in_minus80='Y' AND b.to_ln2_time IS NULL AND CURRENT_DATE - b.freeze_time >= (SELECT config_value::int FROM sys_config WHERE config_key='lqg.cryo.overdue-days') AND b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0) > 0" --col-set 9000003001,9000003005,9000003006 &&
      bash doc/verify/api.sh --as admin PUT /system/config "$(printf '%s' "${C}" | jq -c '. + {configValue:"14"}')" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '([.data[].id|tostring]|sort)==["9000003001","9000003005"]' &&
      python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.cryo.overdue-days'" --eq 14
    counterfeit: |-
      判定里照旧写死 14、参数只是摆在参数设置里看看 → 改成 13 之后清单还是两条红。甲方 9-18 问的就是「这儿是有什么设置吗」。
      阈值在启动时读一次、或缓存进静态字段 → 改完当场不生效红（口径是「改完下一次读时即生效」）。
      迁移没插这一行 / 键名拼错（写成 cryo_overdue_days 之类）→ 第 2 段 select 拿不到 configValue=="14" 红。
      只把清单接口接上参数、页签计数还用常数 → tabCounts.overdue==3 那段红。
      阈值当天数差算错（`overdueDays = 今天 − 冻存日 − 阈值`）→ 13 天阈值下的 7 / 1 / 0 三个数对不上红。
      这条会真改一次系统参数：跑挂在中间要手工把 `lqg.cryo.overdue-days` 改回 14 再重跑（reseed 只清 `t_lqg_*`，不管 sys_config）。
---

# CRYO-REMIND-001 · -80 超两周提醒：唯一的超期判定函数、超期清单接口、列表上的超期标记与页签计数、给工作台首页的计数

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/CRYO` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**CRYO-FLOW-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 口径复述（本张最容易做反的）：
  1. **超期 = 暂存 -80 为是 且 没登记转液氮 且 剩余 > 0 且 冻存满阈值天数（第 N 天当天就算）**。四个条件缺一个都会在真实场景里误报或漏报。
  2. **阈值不是常量，是系统参数 `lqg.cryo.overdue-days`（默认 14）**（CR-20260918-07）：若依 `sys_config` 的一项，内部人员在工作台「系统管理 → 参数设置」里改，**改完下一次读时即生效**——所以每次判定都去读，别在启动时读一次或缓存进静态字段。不建表、不做专门的配置页面。
  3. **判定只有一个函数**。工作台列表、小程序内部管理的冻存表格页、超期清单、工作台首页计数四处都调它；各写各的 where，四个数字迟早不相等。
  4. 读时算，不落「是否超期」标志位。**所以登记转液氮（`to_ln2_time` 落值）或支数被取空（剩余 = 0）之后，这一条立刻退出超期清单、提醒消失**（CR-20260918-07，甲方 9-18 问「转移后还会有提示吗」——答：不会，转完就没了）。定时任务只负责每天写一行日志，不是判定依据。
  5. 提示只在系统内（工作台首页卡片与菜单角标、列表置顶标红、小程序「我的 → 内部管理」冻存表格页的超期页签）。小程序首页没有数字（CR-20260917-05）。**不做**订阅消息、短信、企业微信推送。

## 1 背景与口径

模板批注：在 -80 放置的时间超过两周要提示放入液氮。会上 L103：想要有一个提示，哪些该放进液氮。设计回流 D-3：页签计数与「已超 N 天」读时算。
2026-09-18 甲方看过设计图后两问（CR-20260918-07）：一问「这儿是有什么设置吗」→ 两周这个阈值改成系统参数 `lqg.cryo.overdue-days`（默认 14），他们自己在工作台改；二问「转移后还会有提示吗」→ 不会，登记了转液氮或支数取空就立刻退出清单，这条本来就是读时算的结果，这次把它显式写进任务书并加了断言。

## 2 实现要点

- `CryoOverdueService`：`isOverdue(batch, remaining, today, overdueDays)` 纯函数（阈值当入参传进来，函数自己不读配置，才测得动边界）+ `List<OverdueVo> listOverdue()`；SQL 侧同口径的 where 片段只写一份（`CryoOverdueSqlProvider`），阈值用参数绑定、不写字面量，列表与计数复用。
- 阈值取值只有一处（CR-20260918-07）：`CryoOverdueProperties#days()` 每次读时取 `ConfigService#getConfigValue("lqg.cryo.overdue-days")`（若依给业务模块读 `sys_config` 的 SPI），取不到或不是正整数 → 回落 14 并打 WARN。**不缓存**，甲方改完下一次读即生效。
- 迁移 `V202609241205__CRYO-REMIND-001-config.sql`：往 `sys_config` 插一行 `lqg.cryo.overdue-days` = `14`（`config_name`「-80 冻存超期天数」、`config_type='Y'` 内置可改不可删、`remark` 写清含义）。**不建表**（CR-20260918-07）。
- `GET /lqg/cryo/overdue`：超期批次，按已超天数倒序；每行 `overdueDays = today - freeze_time - 阈值天数`。
- `/lqg/cryo/batch/list` 每行补 `overdue`、`overdueDays`；响应补 `tabCounts:{all, overdue, ln2}`；支持 `overdueOnly=true`；默认排序超期置顶。
- `CryoOverdueService.countOverdue()`：与清单同一个 where 片段，给工作台首页待办与菜单角标（SYS-HOME-001）用。
- `@Scheduled(cron = "0 0 8 * * ?")`：把当日超期批次数与清单写一行 INFO 日志。
- 单测 `CryoOverdueServiceTest`：边界四例（第 13 天 / 第 14 天 / 已取空 / 已转液氮），阈值换成 7 时边界跟着移动一例，外加一条「`countOverdue()` = `listOverdue().size()`」。

## 3 边界（明确不做）

- 不做任何站外推送
- 阈值只有一个全局参数：不做按单位 / 按批次各自的阈值，也不做专门的参数配置页面——就在若依自带的「系统管理 → 参数设置」里改（CR-20260918-07；本节原先写的「不做超期天数可配置、做成常量 `OVERDUE_DAYS = 14`」作废）
- 不做工作台首页卡片（SYS-HOME-001）

## 4 完工报告要求

1. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
2. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
3. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
4. 验证用的后端 / 前端长进程已关，或明示留给谁
