---
ticket: AUTH-STAFF-001
track: AUTH
phase: D1
size: M
req_refs:
  - REQ-AUTH-002
  - REQ-AUTH-008
  - REQ-AUTH-010
depends_on:
  - AUTH-LOGIN-001
  - SYS-WEB-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/auth/staff/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/auth/staff/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921092*__AUTH-STAFF-001-*.sql
  - code/plus-ui/src/views/lqg/auth/staff/**
  - code/plus-ui/src/api/lqg/auth/staff.ts
  - code/plus-ui/src/lang/lqg/auth-staff.*.ts
adr_refs:
  - ADR-0003
blueprint_refs:
  - FLOW:F-AUTH-02.step1
  - FLOW:F-AUTH-02.step2
  - FLOW:F-AUTH-02.step3
  - FLOW:F-AUTH-02.step4
  - UI:admin.auth.staff
accept:
  - name: "授权是升级不是新建：给已登录过的外部手机号授权后账号仍只有一个、角色换成内部、微信绑定原样；撤销后降回外部且旧 token 立即失效；改角色（升或降）同样让该账号全部旧 token 立即失效，重新登录才按新角色生效（CR-20260923-09）"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extF-* &&
      bash doc/verify/api.sh --as extF --fresh-module ruoyi-lqg GET /mp/me | jq -e '.data.identity=="external"' &&
      bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000016","name":"吴同学","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -e '.code==200 and .data.upgraded==true and (.data.userId|tostring)=="9000000116"' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM sys_user WHERE phonenumber='13800000016' AND del_flag='0') || '|' || (SELECT string_agg(r.role_key, ',' ORDER BY r.role_key) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=9000000116) || '|' || (SELECT count(*) FROM t_lqg_wx_bind WHERE user_id=9000000116 AND del_flag='0')" --eq "1|lqg_internal|1" &&
      bash doc/verify/api.sh --as extF GET /mp/me | jq -e '.data.identity=="internal"' &&
      bash doc/verify/api.sh --as admin DELETE /lqg/auth/staff/9000000116 | jq -e '.code==200' &&
      bash doc/verify/api.sh --as extF --bizcode GET /mp/me | grep -qE '^401' &&
      python3 doc/verify/db.py --sql "SELECT string_agg(r.role_key, ',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=9000000116" --eq "lqg_external" &&
      bash doc/verify/api.sh --as admin --bizcode DELETE /lqg/auth/staff/9000000100 | grep -qvE '^200' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user_role WHERE user_id=9000000100 AND role_id=101" --eq 1 &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-staff-* && bash doc/verify/api.sh --as staff --bizcode GET /lqg/auth/staff | grep -qE '^403' &&
      bash doc/verify/api.sh --as admin PUT /lqg/auth/staff/9000000101/role '{"roleKey":"lqg_admin"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff --bizcode GET /mp/me | grep -qE '^401' &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-staff-* && bash doc/verify/api.sh --as staff --bizcode GET /lqg/auth/staff | grep -qE '^200' &&
      bash doc/verify/api.sh --as admin PUT /lqg/auth/staff/9000000101/role '{"roleKey":"lqg_internal"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff --bizcode GET /lqg/auth/staff | grep -qE '^401' &&
      python3 doc/verify/db.py --sql "SELECT string_agg(r.role_key, ',' ORDER BY r.role_key) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=9000000101" --eq "lqg_internal" &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-staff-* && bash doc/verify/api.sh --as staff --bizcode GET /lqg/auth/staff | grep -qE '^403' &&
      rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extF-* "${TMPDIR:-/tmp}"/lqg-verify-token-staff-* && bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      授权时直接 INSERT 一个新 sys_user → 计数「2|…」红，返回的 userId 也不是 9000000116。
      升级时只加了 102 没去掉 103 → 角色串变成 lqg_external,lqg_internal 红；此人会同时命中内外两套接口。
      按手机号授权（原地升级）不踢下线：第 5 段用升级前的旧 token 调 /mp/me 就得看到 internal（/mp/me 读库里的角色）；但角色与权限是登录时写进会话的，他访问内部接口要重新登录一次（FLOW:F-AUTH-02.step2，CR-20260923-09）。
      撤销只改了库、没踢 token → 第 7 段：旧 token 还能调 /mp/me（拿到 200）红。这正是「撤了权限的人还能看半小时数据」。
      撤销做成了删号 → 第 8 段查不到角色行（NULL）红。
      允许管理员撤销自己 → 拿到 200 红；返回了错误码却已经把角色删了（先落盘再报错）→ 紧跟的那条库内断言红。
      改角色只改库、不踢 token（CR-20260923-09 之前的形态：会话里缓存着登录时的角色）→ 第 13 段 staff 的旧 token 调 /mp/me 仍是 200 红；更要命的是降级：管理员降成内部人员之后，管理员期间签发的 token 照样能调 /lqg/auth/staff，第 16 段拿到 200 红——管理员操作一直能做到 token 过期。
      第 11 段是反向对照：内部人员本来调不动 /lqg/auth/staff（403，只授给 101），第 14 段重新登录后拿到 200 才说明是新角色生效、不是接口本来就放开；第 17 段库里只剩 lqg_internal（降级时 101 真的删掉了），第 18 段再登录又回到 403。
      改角色那几段每次换 token 前都先清 staff 的 token 缓存（api.sh 按身份缓存 token、没有 401 自动重登）；结尾清掉 extF 与 staff 的 token 缓存再 reseed：它们都被踢过，留着会让后面用这两个身份的断言全部 401。
  - name: "工作台只让内部进：带着可用口令的外部账号也登不上（被拒在登录接口，且走到的是「无权」分支不是「密码错」分支）；内部人员登得上；不许给外部账号设工作台密码"
    form: API
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      ENVF="${LQG_VERIFY_ENV_FILE:-doc/verify/verify.env}" && PC="$(sed -n 's/^LQG_CLIENT_PC=//p' "$ENVF")" && BASE="$(sed -n 's/^LQG_API_BASE=//p' "$ENVF")" &&
      login() { curl -s -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${PC}" -d "$(jq -nc --arg c "${PC}" --arg u "$1" '{clientId:$c,grantType:"password",tenantId:"000000",username:$u,password:"admin123"}')"; } &&
      login lqg_13800000001 | jq -e '.code==200 and (.data.access_token|length>10)' &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user WHERE user_name='wx_13800000012' AND length(password) = 60" --eq 1 &&
      login wx_13800000012 | jq -e '.code!=200 and ((.data.access_token // "") == "") and (.msg|test("无权"))' &&
      bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg --bizcode PUT /lqg/auth/staff/9000000111/reset-pwd '{"password":"Lqg@test123"}' | grep -qE '^(400|403|500)' &&
      python3 doc/verify/db.py --sql "SELECT length(password) FROM sys_user WHERE user_id=9000000111" --eq 0
    counterfeit: |-
      只在前端路由守卫里拦外部角色 → 第 5 段：登录接口照样发 token 红。
      seed 里 extB（wx_13800000012）**故意带着一个可用口令**（病灶）：如果外部账号都没口令，登录失败走的是「密码错误」分支，角色校验那段代码删掉也照样绿——第 4 段先确认病灶在，第 5 段再断 msg 里有「无权」。
      reset-pwd 不校验目标是不是内部人员 → 第 6 段拿到 200 红；返回了错误码却已经把密码写进去了 → 第 7 段长度不为 0 红。
      第 3 段是正向对照：内部人员必须登得上，否则「一律拒绝」也能让后面几段绿。
  - name: "菜单落在 5100 段、path 与 component 非空、路由下发可达、只授给管理员"
    form: MENU
    run: |-
      python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5110" --eq "5110:staff:lqg/auth/staff/index" &&
      python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5110" --col-set 101 &&
      bash doc/verify/api.sh --as admin GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/auth/staff/index")] | length == 1' &&
      test -f code/plus-ui/src/views/lqg/auth/staff/index.vue
    counterfeit: |-
      菜单 seed 了但 path 留空 → 第 1 段红（空 path 会让整个 vue-router 崩）。
      component 写成 'lqg/auth/staff'（少了 /index）→ 第 1 段红；页面文件不存在 → 第 4 段红。
      顺手也授给了 102 → 第 2 段集合不等红：普通内部人员就能给别人授权了。
---

# AUTH-STAFF-001 · 内部人员按手机号授权：已有账号就升级、没有才预建；撤销即降回外部并踢下线；工作台拒绝外部角色登录

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/AUTH` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**AUTH-LOGIN-001**、**SYS-WEB-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0003** 三个会做错的点：授权是升级不是新建；样本的内外部属性是提交当时的快照；工作台拒绝外部角色是后端行为
- [ ] 口径复述（本张最容易做反的）：
  1. **授权 = 找到就改角色，找不到才预建**。这个手机号多半已经以外部身份登录过；此时再新建一个内部账号，同一个手机号就有两个账号，下次登录命中哪个全看查询顺序。
  2. **撤销 ≠ 删号**。撤销是把角色改回 103 并踢下线；账号、微信绑定、他以前录的样本都留着。
  3. 工作台拒绝外部登录要在**登录接口**里拒，不是登录成功后前端没菜单。
  4. **改角色 = 踢下线；按手机号授权的原地升级不踢**（FLOW:F-AUTH-02.step2，CR-20260923-09）。角色与权限是登录时写进会话的：改角色后该账号全部 token 失效，重新登录按新角色生效——不踢的话「管理员降成内部人员」之后，旧 token 里还是 lqg_admin。外部账号按手机号升级为内部时不踢下线，`/mp/me` 下次请求即显示内部身份，但访问内部接口要重新登录一次。

## 1 背景与口径

会上（逐字稿 L238、L265）：内部人员由实验室在网页端按手机号去挂载。模板批注 N4：内部人员的权限要高于外部人员。
L349-L361：外部人员不能登录网页工作台——「那那不行呀」。

## 2 实现要点

### 2.1 后端（`/lqg/auth/staff`，权限串 `lqg:auth:staff:{list,grant,edit,resetPwd,revoke}`，只授给 101）
- `POST`：`{phone, name, roleKey, password}`。按手机号查 sys_user：
  - 存在 → 删掉它的 103、加上目标角色；`nick_name` 以本次填的为准；设置密码；返回 `upgraded:true`。**不新建账号，不动 `t_lqg_wx_bind`。**
    **不踢下线**：`/mp/me` 下次请求即显示内部身份；角色在登录时写入会话，他访问内部接口要重新登录一次（FLOW:F-AUTH-02.step2，CR-20260923-09）。
  - 不存在 → 新建 sys_user（`user_name='lqg_'+手机号`、`user_type='sys_user'`），返回 `upgraded:false`。他首次小程序登录时 AUTH-LOGIN-001 按手机号自然对上。
  - `lqg_admin` 同时挂 `lqg_internal`（管理员也要能用小程序内部功能）。
- `GET list`：带内部角色的账号；列 = 姓名、手机号、角色、`wxBound`（该账号在 `t_lqg_wx_bind` 有没有行，读时算）。
- `PUT /{userId}/role`（`{roleKey}`，只收 `lqg_internal` / `lqg_admin`）：改完角色 `StpUtil.logout(loginId)` 踢掉该账号全部 token，重新登录按新角色生效（CR-20260923-09：以前只改库，降级之后旧 token 的会话里仍是 lqg_admin，管理员操作照做）。不能把系统里最后一个 lqg_admin 改成普通内部人员。
- `PUT /{userId}/reset-pwd`。
- `DELETE /{userId}`（撤销）：角色改回只剩 103；没有外部档案则补建一行 `unbound`；`StpUtil.logout(loginId)` 踢掉全部 token。不能撤销自己；不能撤到系统里一个 `lqg_admin` 都不剩。
### 2.2 工作台登录拒绝外部
- 密码登录策略之后加一道校验：账号不含 `lqg_internal` / `lqg_admin` / 上游 `superadmin` → 登录失败，msg「该账号无权登录工作台」。放在本模块里通过登录事件 / 策略装饰实现，不改上游源码。
### 2.3 前端页 + 菜单
- `views/lqg/auth/staff/index.vue`：列表 + 「按手机号授权」弹窗（`el-dialog`，点蒙层可关）；提交前调一个只读的 `GET /lqg/auth/staff/check?phone=`，该手机号已有外部账号时在弹窗里提示「该手机号已登录过小程序，将把原账号升级为内部人员」。行操作：改角色、重置密码、撤销（二次确认）。
- 迁移 `V202609210920__AUTH-STAFF-001-menu.sql`：目录「人员与单位」5100（M）→ 菜单「内部人员授权」5110（C，`path='staff'`，`component='lqg/auth/staff/index'`）+ 按钮 5111-5115；`sys_role_menu` 授给 101。

## 3 边界（明确不做）

- 不做单位、组别、外部用户核验（AUTH-GROUP-001）
- 不做「授权时间」列与授权流水表（design-authority.md E-4：已删）
- 不改上游用户管理页；不给内部人员角色授用户管理
- 不做短信通知被授权人

## 4 完工报告要求

1. 升级与预建两条路径各一次的接口输出
2. 外部账号尝试登录工作台的响应体
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 1 补改角色的旧 token 失效断言（staff 先升成管理员、再降回内部人员：两次改角色后旧 token 都 401，重新登录按新角色生效，库里降级后只剩 lqg_internal；每次换 token 前与结尾都清 staff 的 token 缓存并 reseed）；§0 口径复述与 §2.1 按 FLOW:F-AUTH-02.step2 新口径写明「改角色踢下线、按手机号授权的原地升级不踢但访问内部接口要重新登录一次」。
