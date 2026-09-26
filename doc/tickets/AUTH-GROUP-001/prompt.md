---
ticket: AUTH-GROUP-001
track: AUTH
phase: D1
size: M
req_refs:
  - REQ-AUTH-011
  - REQ-AUTH-007
  - REQ-SYS-002
depends_on:
  - AUTH-LOGIN-001
  - AUTH-STAFF-001
  - SYS-MP-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/auth/group/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/auth/group/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921093*__AUTH-GROUP-001-*.sql
  - code/plus-ui/src/views/lqg/auth/unit/**
  - code/plus-ui/src/views/lqg/auth/extuser/**
  - code/plus-ui/src/api/lqg/auth/group.ts
  - code/plus-ui/src/lang/lqg/auth-group.*.ts
  - code/miniapp/src/pages/me/profile/**
  - code/miniapp/src/api/auth.ts
adr_refs:
  - ADR-0003
blueprint_refs:
  - FLOW:F-AUTH-03.step1
  - FLOW:F-AUTH-03.step2
  - FLOW:F-AUTH-03.step3
  - FLOW:F-AUTH-03.step4
  - UI:admin.auth.unit
  - UI:admin.auth.extuser
  - UI:mp.me.profile
  - FIELD:t_lqg_ext_profile.bind_status
  - FIELD:t_lqg_source_unit.unit_status
  - FIELD:t_lqg_unit_group.group_status
accept:
  - name: "两张表与 SSOT 逐列相符且出自本票 Flyway；同单位内组别名唯一、不同单位可重名、软删后可重建"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_source_unit --table t_lqg_unit_group --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260921093%__AUTH-GROUP-001-%'" --eq 1 &&
      python3 doc/verify/db.py --sql "SELECT indexdef LIKE '%(unit_id, group_name)%' AND indexdef LIKE '%WHERE (del_flag = ''0''%' FROM pg_indexes WHERE indexname = 'uk_unit_group'" --eq True
    counterfeit: |-
      组别唯一索引只建在 group_name 上（漏了 unit_id）→ 第 3 段红：A 医院有了「肝胆外科组」，B 大学就建不了同名组。
      唯一索引不带 WHERE del_flag='0' → ddl_vs_ssot 红：停用后误删的组别名永远占着。
      create_dept 漏建（若依 insertFill 会写它，INSERT 时才炸）→ 公共字段缺失红。
  - name: "核验状态机：非法转移被拒且库里不变；自填单位不选新建或归并不许通过；外部一改单位组别立刻回到待核验；内部账号调外部档案接口 403 且不会给自己建出外部档案（CR-20260923-09）"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --bizcode PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve"}' | grep -qvE '^200' &&
      python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq pending &&
      bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000115/verify '{"action":"reject"}' | grep -qvE '^200' &&
      bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","createUnit":true,"createGroup":true}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT p.bind_status || '|' || u.unit_name || '|' || u.unit_status || '|' || g.group_name FROM t_lqg_ext_profile p JOIN t_lqg_source_unit u ON u.id=p.unit_id JOIN t_lqg_unit_group g ON g.id=p.group_id AND g.unit_id=u.id WHERE p.user_id=9000000116" --eq "verified|C 研究所|active|肿瘤组" &&
      bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009102}' | jq -e '.code==200' &&
      python3 doc/verify/db.py --sql "SELECT bind_status || '|' || group_id || '|' || COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq "pending|9000009102|-" &&
      bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009103}' | grep -qvE '^200' &&
      bash doc/verify/api.sh --as staff --bizcode PUT /mp/ext/profile '{"realName":"李工","unitId":9000009001,"groupId":9000009101}' | grep -qE '^403' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_ext_profile WHERE user_id=9000000101" --eq 0 &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      approve 不校验自填档案 → 第 2 段拿到 200 红；库里会出现 unit_id 为空却 verified 的档案，同组互看按 group_id 算时永远匹配不上。
      reject 不要求原因 → 第 4 段拿到 200 红。
      「新建」只建了单位没建组别，或组别挂错了单位 → 第 6 段 JOIN 不出行红。
      外部改组别后状态仍是 verified → 第 8 段红：他换到别的组之后，还能继续看原来那组的样本。
      第 9 段：组别 9000009103 属于 B 大学，却和单位 A 医院一起提交 → 必须拒绝（单位与组别不匹配）。
      /mp/ext/profile 没挂外部角色闸（CR-20260923-09 查实的形态）→ 第 10 段 staff 拿到 200 红，第 11 段还会多出一行 staff 的外部档案：内部账号给自己建了一个外部身份，「外部用户」页与同组互看都会把他算进去。请求体是合法的（单位与组别匹配），红只能来自角色闸。
      每段「被拒」后面都跟一条库内状态断言——只看业务码的话，先落盘再返回 400 也是绿的。
  - name: "对外的单位选择器只含启用项且不带任何人数；两个菜单落在 5100 段、可达、内部人员也看得到"
    form: MENU
    run: |-
      bash doc/verify/api.sh --as extF GET /mp/ext/units | jq -e '.code==200 and ([.data[].unitName]|sort) == ["A 医院","B 大学"] and ([.data[] | select(.unitName=="A 医院") | .groups[].groupName]|sort) == ["消化内科组","肝胆外科组"] and ([.data[] | .. | objects | keys[]] | map(select(test("count|Count"))) | length == 0)' &&
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id IN (5120, 5130)" --col-set "5120:unit:lqg/auth/unit/index,5130:extuser:lqg/auth/extuser/index" &&
      bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/auth/extuser/index" or .component? == "lqg/auth/unit/index")] | length == 2'
    counterfeit: |-
      选择器把「已停用单位」也返回了 → 单位集合不等红。
      图省事复用了工作台的组别 VO（带 verifiedCount）→ 最后一个 jq 条件红：外部能看到每个组有多少人。
      菜单只授给了管理员 → 第 3 段用 staff 身份取路由，找不到两个页面红。
---

# AUTH-GROUP-001 · 来源单位与组别维护、外部用户组别核验、小程序「单位与组别」页

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/AUTH` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-LOGIN-001**、**AUTH-STAFF-001**、**SYS-MP-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **REQ-AUTH-011 是 clarify**：组别怎么分、谁维护，甲方还没最终确认。本张按合同附件二第 1 条的乙方建议做（实验室维护、外部自选、核验后生效）；甲方若选「不让外部自选」，只是小程序这一页不出，表和核验页不变
  - **ADR-0003**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0003` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **只有 `verified` 才参与同组互看**。pending、rejected、unbound 一律只看本人的——可见范围本身在 AUTH-EXT-001 算，但状态在这里维护，状态错了那边全错。
  2. **改单位或组别 → 立刻回到 pending**，不是等核验人驳回才失效。
  3. 外部自填的单位 / 组别，核验通过时必须二选一：「新建」或「归并到已有」。不许留着一个 `unit_id` 为空却 `verified` 的档案。

## 1 背景与口径

微信答复：「外部同单位的人不能互看对方送的样，同组可以看。」会上（L64）：同一单位同一组别的可以看到自己相关样本。
合同附件二第 1 条（待甲方确认，乙方建议）：由甲方在网页工作台维护组别，外部用户首次提交时选择所属单位与组别，甲方核验后生效。

## 2 实现要点

### 2.1 DDL（`V202609210930__AUTH-GROUP-001-unit-group.sql`）
- `gen_ddl_pg.py --migration V202609210930__AUTH-GROUP-001-unit-group.sql` → `t_lqg_source_unit`、`t_lqg_unit_group`。
- 同迁移 seed 菜单：5120「来源单位与组别」（`path='unit'`，`component='lqg/auth/unit/index'`）、5130「外部用户」（`path='extuser'`，`component='lqg/auth/extuser/index'`）及按钮；授给 101 与 102（核验组别是日常工作，普通内部人员也要能做）。
### 2.2 后端
- `/lqg/auth/unit`、`/lqg/auth/group`：增改、启用 / 停用（不物理删；已有人绑定的组别停用后，已绑定的人不受影响）。组别列表带 `verifiedCount`（读时 count）。同单位内组别名唯一、单位名全库唯一（部分唯一索引已保证，service 层给人话报错）。
- `GET /mp/ext/units`：只返回 `active` 的单位及其 `active` 的组别。**对外部开放**，所以只有 id 和名称，不带任何人数。
- `PUT /mp/ext/profile`：写 `real_name` + （`unit_id`,`group_id`）或（`unit_name_input`,`group_name_input`）；两套互斥，选了列表项就清空自填项。任何一次成功保存 → `bind_status='pending'`，清 `verified_by / verified_time / reject_reason`。
  **只对外部角色开放**（类级 `@SaCheckRole("lqg_external")`，与其余 `/mp/ext/**` 写接口一致）：内部账号调用一律 403，库里不建档案（CR-20260923-09：以前漏了这道闸，内部账号调它会给自己补建一行外部档案）。新登录的外部账号建号时就挂了 103，还没填单位也调得通。
- `PUT /lqg/auth/ext-user/{userId}/verify`：
  - `approve`：档案是自填的 → 必须带 `createUnit:true`（把自填名建成 active 单位 / 组别）或 `unitId + groupId`（归并）；最终 `unit_id`、`group_id` 都非空才允许 `verified`。
  - `reject`：`reason` 必填。
  - 合法转移：`pending→verified`、`pending→rejected`、`verified→pending`（外部自己改）、`rejected→pending`（外部改后重提）、`verified→verified`（内部「改归组」）。其余拒绝。
- `GET /lqg/auth/ext-user/list`：姓名、手机号、单位、组别（自填的带 `selfInput:true`）、核验状态、`sampleCount`、`lastLoginTime`，全部读时算。
### 2.3 前端
- 工作台两页按 `UI:admin.auth.unit`（左单位右组别）与 `UI:admin.auth.extuser`（列表 + 核验弹窗）。
- 小程序 `pages/me/profile`：`UnitGroupPicker`（单位 → 组别联动，末项「列表里没有，手动填写」）。保存后回「我的」，状态显示「待核验」；被驳回时显示原因。首页 `bind_status=unbound` 的提示条点击进这一页。

## 3 边界（明确不做）

- 不算可见范围（AUTH-EXT-001）
- 不做邀请码、不做单位管理员自助管理本单位成员
- 不做短信 / 订阅消息通知核验结果
- 单位不挂地址、联系人等字段（甲方没要）

## 4 完工报告要求

1. 四个外部身份（extA / extE / extF / 一个新建的）在「外部用户」页的截图
2. 自填单位「新建」与「归并」两条核验路径各一次的接口输出
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 2 补「staff 带合法请求体调 PUT /mp/ext/profile 返回 403、staff 的外部档案仍是 0 行」两段；§2.2 写明该接口只对外部角色开放。
