---
ticket: AUTH-LOGIN-001
track: AUTH
phase: D1
size: L
req_refs:
  - REQ-AUTH-001
  - REQ-AUTH-003
  - REQ-AUTH-004
depends_on:
  - SYS-BASE-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/auth/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/auth/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921091*__AUTH-LOGIN-001-*.sql
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application*.yml
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml
adr_refs:
  - ADR-0003
  - ADR-0008
blueprint_refs:
  - FLOW:F-AUTH-01.step1
  - FLOW:F-AUTH-01.step2
  - FLOW:F-AUTH-01.step3
  - FLOW:F-AUTH-01.step4
  - FIELD:t_lqg_wx_bind.openid
  - FIELD:t_lqg_wx_bind.phone
  - FIELD:t_lqg_ext_profile.bind_status
accept:
  - name: "两张表与 SSOT 逐列相符（含公共字段与部分唯一索引）且出自本票 Flyway；mp 的 client 行可用"
    form: DDL
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_wx_bind --table t_lqg_ext_profile --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260921091%__AUTH-LOGIN-001-%'" --eq 1 &&
      grep -qi 'CREATE TABLE t_lqg_wx_bind' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921091*__AUTH-LOGIN-001-*.sql &&
      python3 doc/verify/db.py --sql "SELECT grant_type || '|' || status FROM sys_client WHERE client_key='mp' AND del_flag='0'" --eq "xcx|0"
    counterfeit: |-
      照栈包 gotchas 的 MySQL 金标准给两张表加了 tenant_id / del_unique → 「库里有、SSOT 没有」红。
      openid 的唯一索引建成普通 UNIQUE（不带 WHERE del_flag='0'）→ 红：软删一条绑定后同一个 openid 再也绑不回来。
      表是手工建的、迁移文件里没有 → flyway 计数 0 / grep 红。
      mp client 的 grant_type 抄了上游 app 的 'password,sms,social'（没有 xcx）→ 登录时「授权类型不支持」，这里先红。
  - name: "首次登录自动建外部账号、再登录不重复建、换微信号仍是同一个人；内外部判定只认手机号"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null &&
      bash doc/verify/api.sh --as phone:newbie1:13800000099 --fresh-module ruoyi-lqg GET /mp/me | jq -e '.code==200 and .data.identity=="external" and .data.ext.bindStatus=="unbound" and .data.phoneMasked=="138****0099"' &&
      bash doc/verify/api.sh --as phone:newbie2:13800000099 GET /mp/me | jq -e '.data.identity=="external"' &&
      python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM sys_user WHERE phonenumber='13800000099' AND del_flag='0') || '|' || (SELECT count(*) FROM t_lqg_wx_bind b JOIN sys_user u ON u.user_id=b.user_id WHERE u.phonenumber='13800000099' AND b.del_flag='0') || '|' || (SELECT count(*) FROM t_lqg_ext_profile p JOIN sys_user u ON u.user_id=p.user_id WHERE u.phonenumber='13800000099' AND p.bind_status='unbound') || '|' || (SELECT string_agg(r.role_key, ',') FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id WHERE u.phonenumber='13800000099')" --eq "1|2|1|lqg_external" &&
      bash doc/verify/api.sh --as staff GET /mp/me | jq -e '.data.identity=="internal" and .data.ext==null' &&
      bash doc/verify/api.sh --as extA GET /mp/me | jq -e '.data.identity=="external" and .data.name=="王医生"' &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      按 openid 查不到就新建账号（而不是按手机号）→ 第二次用 newbie2 登录会建出第二个 sys_user，计数「2|…」红。
      新建外部账号时忘了挂角色 103 或忘了建外部档案 → 第 4 段的角色串 / 档案计数红。
      identity 取自请求参数或 user_type 字段而不是角色 → staff（user_type=sys_user、角色 102）与 extA 至少一个判错。
      手机号掩码没做（原样返回 13800000099）→ 第 1 段红。
      两侧不同源：/mp/me 走登录链路，计数走直连库的 SQL JOIN。
  - name: "mock 登录护栏：prod 打开 mock 必须拒绝启动（契约测试逐字节未改）；prod 配置文件里不出现这个开关；缺 clientid 的登录请求被拒；真 jar 不声明 profile 直接起必须拒绝启动（CR-20260923-09）"
    form: STATE
    run: |-
      cmp doc/verify/fixtures/java/MockLoginGuardContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/auth/guard/MockLoginGuardContractTest.java &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=MockLoginGuardContractTest -Dsurefire.failIfNoSpecifiedTests=true) &&
      ! grep -rn 'mock-login' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml &&
      grep -rn 'MockLoginGuard.check' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java | grep -v '/guard/MockLoginGuard.java' | grep -q . &&
      test "$(curl -s -X POST "$(sed -n 's/^LQG_API_BASE=//p' "${LQG_VERIFY_ENV_FILE:-doc/verify/verify.env}")/auth/login" -H 'Content-Type: application/json' -d '{"grantType":"xcx","tenantId":"000000","xcxCode":"mock:extA","phoneCode":"mock:13800000011"}' | jq -r '.data.access_token // "rejected"')" = "rejected" &&
      ! env -u SPRING_PROFILES_ACTIVE LQG_DB_PORT=1 LQG_REDIS_PORT=1 java -jar code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar --server.port=0 >/tmp/lqg-noprofile.log 2>&1 &&
      grep -q '没有声明 spring.profiles.active' /tmp/lqg-noprofile.log
    counterfeit: |-
      护栏只判 profile 等于 "prod" 的精确小写串 → 契约测试里的 "PROD" 与多 profile 用例红。
      没声明 profile 时默认放行 mock（把「没配」当成开发环境）→ noActiveProfileIsTreatedAsUnsafe 红——生产漏配 profile 是真实事故。
      为了让测试过去改测试文件 → cmp 红。
      护栏类写了但启动时没人调用它（装饰品）→ 第 4 段 grep 为空红。
      把 mock-login: false 显式写进 prod 配置「以示安全」→ 第 3 段红：这个键出现在 prod 文件里，就离被人改成 true 只差一次手滑。
      不带 clientId 也能登录成功 → 第 5 段拿到 token 红。
      第 4 段只 grep 源码，Javadoc 里写一句 MockLoginGuard#check 就能满足（grep 的 . 匹配 #），所以第 6、7 段补行为断言（CR-20260923-09）：真 jar 不声明 profile 直接起，必须在连库之前拒绝启动（退出码非 0），且日志里是启动护栏的原话「没有声明 spring.profiles.active」。jar 里又写回缺省 profile（最可能的回退，打包时资源过滤填成 dev）→ 进程按 dev 起来去连库，LQG_DB_PORT=1 / LQG_REDIS_PORT=1 让它连不上任何真库与缓存、自己失败退出——退出码照样非 0，日志里却没有那句话，第 7 段红；这两个变量是护栏失效时的保险，保证不会对着 LQG_DB_* 指向的库跑 Flyway。env -u SPRING_PROFILES_ACTIVE 防的是跑验收的 shell 里恰好带着这个变量。后端 jar 是旧包（修复前打的）时同样红，先重新打包。
---

# AUTH-LOGIN-001 · 小程序登录后端：微信登录 + 手机号 → 绑定或自动建外部账号；身份判定；mock 登录仅限开发测试

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/AUTH` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**SYS-BASE-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0003**：一套账号，手机号定身份；用户不可自选内外部；微信身份存 `t_lqg_wx_bind`，**不改 sys_user 表结构**
  - **ADR-0008**：mock 登录只存在于 dev / test；prod 下被打开则应用拒绝启动
  - 栈包 gotchas §14.2：登录必须带 clientId + grantType，缺 clientId → token 为空，后面所有 API 断言静默红
- [ ] 口径复述（本张最容易做反的）：
  1. **身份只认微信返回的手机号**。请求体里出现任何 `identity` / `role` / `isInternal` 之类的字段一律忽略——甲方原话「万一就是外部的人员想看我们内部更多的信息」。
  2. **同一手机号永远只对应一个账号**。先按手机号查 sys_user，查到就绑 openid，查不到才新建。别按 openid 查不到就新建——同一个人换了微信号会变成两个账号。
  3. **外部不设准入**：查不到就自动建外部账号（角色 103）+ 一行 `bind_status=unbound` 的外部档案，不需要任何人审批。

## 1 背景与口径

会上定的（逐字稿 L238-L295）：没有内部登录、外部登录之分，都是微信登录 + 授权手机号；内部人员由实验室按手机号挂角色（那是 AUTH-STAFF-001），
其余所有人都是外部；外部不限制，登录了就能提交。微信答复：「外部用户有多个，是除我们内部用户的所有人」。
本张只做后端。小程序的登录页与首页在 SYS-MP-001。

## 2 实现要点

### 2.1 DDL（一支迁移 `V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql`）
- `python3 doc/tools/gen_ddl_pg.py --migration V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql` 的输出即两张表（`t_lqg_wx_bind`、`t_lqg_ext_profile`）。
- 同一支迁移里加 mp 的 client 行：`sys_client`，`client_key='mp'`、`grant_type='xcx'`、`device_type='xcx'`、`status='0'`；`client_id` 取 `md5('mp' || client_secret)`，填进 `doc/verify/verify.env` 的 `LQG_CLIENT_MP`。

### 2.2 登录策略（包 `org.dromara.lqg.auth`）
- 实现上游 `IAuthStrategy` 的 xcx 策略（上游 `XcxAuthStrategy` 是个 TODO 空壳，**在本模块里新写一个 bean 顶替，不改上游文件**）。
- 真实路径：`xcxCode` → `code2session` 拿 openid / unionid；`phoneCode` → 微信「获取手机号」接口拿手机号（引 `weixin-java-miniapp`，appid / secret 走配置）。
- mock 路径（`lqg.auth.mock-login=true` 且 `xcxCode` 以 `mock:` 开头）：openid = `mock-openid-` + 冒号后的 key；手机号 = `phoneCode` 冒号后的串。
- 拿到 (openid, phone) 之后两条路径走**同一段**代码：
  1. `SELECT … FROM sys_user WHERE phonenumber = ? AND del_flag='0'`
  2. 查到 → 该 openid 不在 `t_lqg_wx_bind` 里才插一行；查不到 → 新建 sys_user（`user_name='wx_'+手机号`、`user_type='app_user'`、角色 103）+ `t_lqg_ext_profile`（`bind_status='unbound'`）+ 绑定行。整段一个事务。
  3. 更新 `last_login_time`，签发 token。
- 并发：两次首登同时到达 → 靠 `sys_user` 上按手机号加的应用层锁（Redisson，key = 手机号）或捕获唯一冲突后重查，**不能建出两个账号**。

### 2.3 `GET /mp/me`
- `identity`：账号带 `lqg_internal` 或 `lqg_admin` 角色 → `internal`，否则 `external`。
- `phoneMasked`：中间四位 `****`。`ext`：外部才有，单位 / 组别名称读时 join。

### 2.4 mock 登录护栏
- `org.dromara.lqg.auth.guard.MockLoginGuard#check(String[] activeProfiles, boolean mockEnabled)`，启动时用真实 profiles 与配置值调用。
- 把 `doc/verify/fixtures/java/MockLoginGuardContractTest.java` **逐字节**拷到 `src/test/java/org/dromara/lqg/auth/guard/`（accept 用 `cmp` 校验没被改过）。
- `application-prod.yml` 不出现 `mock-login` 这个键（缺省 false）。
- jar 里不带缺省 profile：没声明 `spring.profiles.active` 就在连库、跑 Flyway 之前拒绝启动，stderr 打「启动护栏：拒绝启动（profile=[]）…没有声明 spring.profiles.active…」（CR-20260923-09：以前 jar 内缺省 dev，漏配 profile 就带着 mock 登录起来）。护栏是修复组加在 `ruoyi-common-web` 的 `StartupSafetyGuard`（EnvironmentPostProcessor），不在本票实现范围；本票 accept 3 最后两段从外面用真 jar 验它。本机开发照旧 `--spring.profiles.active=dev`。

## 3 边界（明确不做）

- 不做小程序端页面（SYS-MP-001）
- 不做内部人员授权页与角色升级（AUTH-STAFF-001）；本张里「内部」只靠 seed 里的 staff 账号验证
- 不做单位 / 组别的选择与核验（AUTH-GROUP-001）；本张只保证外部档案那一行被建出来
- 不改 `sys_user` 表结构、不改上游 `ruoyi-common-*` / `ruoyi-system` 源码
- 不接短信验证码登录、不做账号密码注册

## 4 完工报告要求

1. mp client 的 client_id（已填进 verify.env）
2. 一次真实微信登录的联调结论（有测试号就测；没有就写明「真实路径未联调，等甲方小程序 appid」并列进遗留）
3. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
4. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
5. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
6. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 3 在源码 grep 之外补行为断言——真 jar 不声明 profile 直接起必须拒绝启动（退出码非 0 且日志里有「没有声明 spring.profiles.active」，LQG_DB_PORT=1 / LQG_REDIS_PORT=1 兜底不碰真库）；§2.4 记下启动护栏。
