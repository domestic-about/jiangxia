# AUTH-LOGIN-001 · 完工报告

- **ticket**：AUTH-LOGIN-001（track AUTH / phase D1 / size L）
- **status**：**done**
- **accept**：**3/3 绿**（第 2 条的 `--fresh-module` 原样跑法在本 agent 沙箱里 `ps` 被禁 → exit 1，用等价手工证据替代，见 §4.2 与 WARN-1；第 3 条的 `mvn test` 原样跑法在本机缺 `-s/-Dmaven.repo.local/-Duser.home` → 必挂，见 WARN-2）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 14:54`，后端进程 72651（8081，dev profile）——
  **最终代码**（含自调用事务修正）重新打包、重启后三条 accept 各又跑了一遍，全绿（§4 的输出即最终码的结果）
- **分支**：`task/D1`，未 push / 未 merge（`doc/waves/`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`doc/verify/api.sh` 一个字节没动）

---

## 1 mp client 的 client_id

`client_secret` 取 **`mpDevSecret#2026`**（dev/test 公开常量，不是真密钥；真实小程序 secret 走部署期配置），
按 ticket 口径 `client_id = md5('mp' || client_secret)`：

```
$ printf 'mp%s' 'mpDevSecret#2026' | md5
22b2aecd0710671691ec1c07f2542b9d
```

已写进 `doc/verify/verify.env`（该文件被 gitignore，是 `api.sh --as staff/extA/phone:*` 的登录前提）：

```
LQG_CLIENT_MP=22b2aecd0710671691ec1c07f2542b9d
```

库里那行（同一支迁移里插入，`WHERE NOT EXISTS` 幂等）：

```
$ python3 doc/verify/db.py --sql "SELECT grant_type || '|' || status FROM sys_client WHERE client_key='mp' AND del_flag='0'" --eq "xcx|0"
xcx|0
```

## 2 一次真实微信登录的联调结论

**真实路径未联调，等甲方小程序 appid / secret。** 本票落地的是完整的两步真实调用
（`xcxCode → WxMaService.jsCode2SessionInfo` 拿 openid/unionid；`phoneCode →
getUserService().getPhoneNumber` 拿微信背书的手机号，取 `purePhoneNumber`），
但**没有可用的 appid/secret 与真机 code**，所以只做了「路能通」级别的保证：

- 真实路径与 mock 路径的**分叉点只有一个**（`WxIdentityResolver.resolve`），
  拿到 `WxIdentity` 之后绑定/建号/签 token 是同一段代码 —— mock 验收绿即覆盖了真实路径的下游全部逻辑；
- appid / secret 从 `lqg.wx.miniapp.appid` / `.secret` 读（dev 走 `${LQG_WX_APPID:}` / `${LQG_WX_SECRET:}`），
  部署期 `export` 即生效，不用改代码；
- 引的是 `weixin-java-miniapp 4.7.0`（本机 `.m2repo` 里已有，随上游 `justauth` 传递进来）。

**列进遗留**（见 §5）：拿到 appid/secret 后需在真机跑一次 `wx.login` + `getPhoneNumber`，
确认 appid 与小程序主体一致（`code2session` 的 40013 类错误只有真调才发现）。

## 3 改了哪些文件

### 3.1 迁移（取号依据：`doc/lint-profile.yaml`，D1 = `20260921`、AUTH 域 HHmm `09xx` → 0910）

| 文件 | 内容 |
|---|---|
| `ruoyi-admin/src/main/resources/db/migration/V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql`（92 行，新增） | ① `python3 doc/tools/gen_ddl_pg.py --migration V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql` 的**逐字节输出**（`t_lqg_wx_bind` + `t_lqg_ext_profile`，公共 6 字段、部分唯一索引 `WHERE del_flag='0'`、无 `tenant_id`/无 `del_unique`）② 同一支里 `INSERT INTO sys_client … WHERE NOT EXISTS` 幂等加 mp 行（`id=3` / `client_key='mp'` / `grant_type='xcx'` / `device_type='xcx'` / `status='0'`） |

```
$ python3 doc/verify/db.py --sql "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank"
202609210800|V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql|True
202609210810|V202609210810__SYS-BASE-001-lqg-dicts.sql|True
202609210820|V202609210820__SYS-BASE-001-lqg-roles.sql|True
202609210910|V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql|True
```

### 3.2 新增代码（`ruoyi-lqg`，包 `org.dromara.lqg.auth` —— ADR-0001 的「业务代码只在这一个模块」）

| 文件 | 职责 |
|---|---|
| `domain/WxLoginCredentials.java` | 小程序登录入参：只声明 `xcxCode` / `phoneCode`，**刻意没有** identity/role/isInternal 之类属性（认手机号的第一道闸） |
| `domain/WxIdentity.java` | openid + 微信背书手机号 + unionid 的最小事实集（mock/真实收敛到它） |
| `domain/WxBind.java` / `domain/ExtProfile.java` | `t_lqg_wx_bind` / `t_lqg_ext_profile` 实体（含 `@TableLogic del_flag`，公共字段走 `BaseEntity`） |
| `domain/vo/MPUserVo.java` / `domain/vo/ExtProfileVo.java` | `/mp/me` 的 data 与 `data.ext` 形状（逐字对齐 `doc/api-contract.md`） |
| `mapper/WxBindMapper.java` / `mapper/ExtProfileMapper.java` | 两张表的 mapper |
| `guard/MockLoginGuard.java` | **静态纯函数** `check(String[] activeProfiles, boolean mockEnabled)`（ADR-0008 的守卫本体） |
| `guard/MockLoginGuardAutoConfiguration.java` | 启动接线：`ApplicationRunner` 用真实 profiles + `lqg.auth.mock-login` 调 `MockLoginGuard.check` |
| `service/WxIdentityResolver.java` | mock / 真实两条路径 → `WxIdentity`（唯一分叉点） |
| `service/WeChatMiniAppService.java` | code2session（openid/unionid）与手机号快速验证（手机号） |
| `service/WxAccountBindService.java` | **按手机号**查号 → 建外部账号（角色 103 + `unbound` 档案）/ 绑 openid；Redisson 按手机号加锁；整段一个事务（注入自身代理走 `@Transactional`，见 §8 坑 2） |
| `service/ExtProfileQueryService.java` | 档案读侧；单位/组别名称读时 join（那两张表属 AUTH-GROUP-001，见 §8 坑 3） |
| `service/CurrentUserService.java` | `/mp/me` 取数：identity（按角色）、phoneMasked、ext |
| `controller/MPMemberController.java` | `GET /mp/me` |
| `src/test/java/org/dromara/lqg/auth/guard/MockLoginGuardContractTest.java` | 需求层 fixture 的**逐字节拷贝**（`cmp` 已核，sha256 `b743e138…2944b`） |

### 3.3 新增代码（`ruoyi-admin` —— 只为拿到 `IAuthStrategy` / `LoginVo` 这两个上游类型）

| 文件 | 改动 |
|---|---|
| `org/dromara/web/service/impl/LqgXcxAuthStrategy.java`（新增，`@Service("xcx" + IAuthStrategy.BASE_NAME)`） | xcx 策略接线：解析入参 → 调 lqg 侧 resolver/bind → `buildLoginUser` → 签 token → `LoginVo`。**全部业务规则都在 lqg 侧**，这里只有框架接线（另见 §8 坑 1 的 `DataPermissionHelper.ignore`） |
| `org/dromara/web/service/impl/XcxAuthStrategy.java`（**删除**） | 上游那个 `loadUserByOpenid` 里只有 `// todo 自行实现`、`SysUserVo` 是 `new` 出来的空壳。`IAuthStrategy` 按 **bean 名**取策略，同名 bean 会 `ConflictingBeanDefinitionException`，所以顶替必须先删它（详见 §5 raise） |

### 3.4 修改（配置 / pom / ping）

| 文件 | 改动 |
|---|---|
| `ruoyi-lqg/pom.xml` | +`ruoyi-system`（sys_user / sys_user_role / sys_client 的 mapper 与 `ISysPermissionService`；方向单一不成环）、+`ruoyi-common-redis`（Redisson 锁）、+`weixin-java-miniapp 4.7.0`、+`junit-jupiter 5.10.2`（test）；`<properties>` + surefire 覆盖，让**不带 `@Tag` 的契约测试真的会跑**（见 §8 坑 4） |
| `ruoyi-admin/.../application-dev.yml` | +`lqg.auth.mock-login: true`（ADR-0008，**只在 dev**）、+`lqg.wx.miniapp.appid/secret`（走 `${LQG_WX_APPID:}` / `${LQG_WX_SECRET:}`） |
| `ruoyi-lqg/.../sys/controller/SysPingController.java` | 接上 `mockLogin` 字段：`@Value("${lqg.auth.mock-login:false}")`，与护栏同源同缺省（SYS-BASE-001 的 WARN-2 闭环） |
| `ruoyi-lqg/.../sys/domain/vo/SysPingVo.java` | `mockLogin` 的注释改为「已接上」 |
| `doc/verify/verify.env`（gitignore，不进仓库） | `LQG_CLIENT_MP` 填上 client_id |

**接口清单（本票）**：`POST /auth/login`（`grantType=xcx`，上游路径、本票换实现）、`GET /mp/me`（新增）。
**无前端页面**（SYS-MP-001 的范围）。

## 4 accept 逐条 ✅/❌ + 关键输出

### 4.1 accept 1 · DDL —— ✅

```
$ python3 doc/verify/ddl_vs_ssot.py --table t_lqg_wx_bind --table t_lqg_ext_profile --require-public create_dept,create_by,create_time,update_by,update_time,del_flag
✓ 2 张表与 SSOT 逐列相符（含公共字段 6 个、部分唯一索引、普通索引）
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260921091%__AUTH-LOGIN-001-%'" --eq 1
1
$ grep -qi 'CREATE TABLE t_lqg_wx_bind' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260921091*__AUTH-LOGIN-001-*.sql && echo 命中
命中
$ python3 doc/verify/db.py --sql "SELECT grant_type || '|' || status FROM sys_client WHERE client_key='mp' AND del_flag='0'" --eq "xcx|0"
xcx|0
ACCEPT-1 EXIT=0
```

### 4.2 accept 2 · DATA —— ✅（首行 `--fresh-module` 在沙箱内跑不了，用等价证据替代）

原样跑法（沙箱限制，见 WARN-1）：

```
$ bash doc/verify/api.sh --as phone:newbie1:13800000099 --fresh-module ruoyi-lqg GET /mp/me
doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
date: illegal option -- d
原样 EXIT=1
```

手工替代 —— 覆盖 `api.sh` 那道守卫的**两个半边**（源码不得比 jar 新 + 进程不得早于 jar）：

```
jar mtime                 : 2026-09-21 14:55:05
ruoyi-lqg 比 jar 新的源码  : []          ← 空 = 没有比 jar 新的源码
ruoyi-admin 比 jar 新的源码: []
8081 PID                  : 72651（`lsof -ti tcp:8081 -sTCP:LISTEN`）
进程持有 jar               : …/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar   ← 就是新打的那个
后端日志 mtime             : 2026-09-21 14:56:03（进程启动即写日志，晚于 jar 的 14:55:05 = 没 stale）
（`ps -o lstart=` 在本沙箱被禁，进程启动时间的替代证据是「该进程写的日志 mtime」）
```

其余逐字执行：

```
$ bash doc/verify/reseed.sh --yes >/dev/null && echo ok
ok
$ bash doc/verify/api.sh --as phone:newbie1:13800000099 GET /mp/me
{"code":200,"msg":"操作成功","data":{"userId":"2101928510464487426","name":"wx_13800000099",
 "phoneMasked":"138****0099","identity":"external",
 "ext":{"unitId":null,"unitName":null,"groupId":null,"groupName":null,
        "unitNameInput":null,"groupNameInput":null,"bindStatus":"unbound","rejectReason":null}}}
$ … | jq -e '.code==200 and .data.identity=="external" and .data.ext.bindStatus=="unbound" and .data.phoneMasked=="138****0099"'
true
$ bash doc/verify/api.sh --as phone:newbie2:13800000099 GET /mp/me | jq -e '.data.identity=="external"'
true
$ python3 doc/verify/db.py --sql "SELECT (SELECT count(*) …phonenumber='13800000099'…) || '|' || (…wx_bind…) || '|' || (…ext_profile bind_status='unbound'…) || '|' || (…string_agg(role_key)…)" --eq "1|2|1|lqg_external"
1|2|1|lqg_external
$ bash doc/verify/api.sh --as staff GET /mp/me
{"code":200,…"data":{"userId":9000000101,"name":"李工","phoneMasked":"138****0001","identity":"internal","ext":null}}
$ … | jq -e '.data.identity=="internal" and .data.ext==null'
true
$ bash doc/verify/api.sh --as extA GET /mp/me
{"code":200,…"data":{"userId":9000000111,"name":"王医生","phoneMasked":"138****0011","identity":"external",
 "ext":{"unitId":9000009001,"unitName":null,"groupId":9000009101,"groupName":null,
        "unitNameInput":null,"groupNameInput":null,"bindStatus":"verified","rejectReason":null}}}
$ … | jq -e '.data.identity=="external" and .data.name=="王医生"'
true
$ bash doc/verify/reseed.sh --yes >/dev/null && echo ok
ok
ACCEPT-2 GREEN
```

**两侧同源对照**（ticket 明说「/mp/me 走登录链路，计数走直连库」）：
`/mp/me` 报的 `userId=2101928510464487426 / identity=external / ext.bindStatus=unbound / phoneMasked=138****0099`
与上面那条 `1|2|1|lqg_external` 的直连 SQL 是同一批行：
**1** 个 `sys_user`（该手机号）、**2** 行 `t_lqg_wx_bind`（newbie1/newbie2 两个 openid → 同一个 user_id）、
**1** 行 `unbound` 档案、角色串恰为 `lqg_external`。
（雪花 id 每次跑都不同，属正常；两侧同源看的是「同一次运行的同一个 id」。）

**并发首登加测**（ticket §2.2 明确要求「不能建出两个账号」，accept 没写这条，我自己补跑）：

```
8 个线程同时用 8 个不同 openid + 同一手机号 13800000077 首登：
responses: [(200,True) × 8]      成功数: 8
库计数: 1|8|1                     ← 1 个账号、8 行绑定（8 个微信号）、1 行档案
```

### 4.3 accept 3 · STATE —— ✅

```
$ cmp doc/verify/fixtures/java/MockLoginGuardContractTest.java \
      code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/auth/guard/MockLoginGuardContractTest.java
（无输出；exit 0）  sha256=b743e1389977a02b7f00c11396882970c987b8780b268595ec43beedbe82944b

$ (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test \
      -Dtest=MockLoginGuardContractTest -Dsurefire.failIfNoSpecifiedTests=true
      -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome)
[INFO] Running org.dromara.lqg.auth.guard.MockLoginGuardContractTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.041 s
[INFO] BUILD SUCCESS
surefire 报告：Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
（不带本机三个参数时必挂，见 WARN-2）

$ ! grep -rn 'mock-login' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml
（无命中 → exit 0）
旁证：application-dev.yml:289:    mock-login: true      ← 只出现在 dev

$ grep -rn 'MockLoginGuard.check' …/ruoyi-lqg/src/main/java | grep -v '/guard/MockLoginGuard.java'
…/guard/MockLoginGuardAutoConfiguration.java:45:        MockLoginGuard.check(environment.getActiveProfiles(), mockEnabled);

$ curl -s -X POST "$LQG_API_BASE/auth/login" -H 'Content-Type: application/json' \
    -d '{"grantType":"xcx","tenantId":"000000","xcxCode":"mock:extA","phoneCode":"mock:13800000011"}'
{"code":500,"msg":"Auth clientid cannot be blank","data":null}
token 字段 = rejected
ACCEPT-3 GREEN
```

**ADR-0008 的正面证据（护栏不是装饰品）** —— 契约测试是用例级，下面这条是**真启动级**：
用 `prod,dev` 双 profile + `mock-login=true` 起真进程（为避免抢 8081，临时换 8099 端口）：

```
$ java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=prod,dev \
       --server.port=8099 --api-decrypt.enabled=false
 - Started DromaraApplication in 5.466 seconds
java.lang.IllegalStateException: lqg.auth.mock-login=true 与 profile=[prod, dev] 冲突：
        mock 登录只允许存在于 dev / test（ADR-0008）。已拒绝启动。
$ lsof -ti tcp:8099 -sTCP:LISTEN
8099 无监听（进程已退出）
```

**`/lqg/sys/ping` 的 mockLogin 已被接上**（SYS-BASE-001 的 WARN-2 闭环）：

```
$ bash doc/verify/api.sh --as admin GET /lqg/sys/ping
{"module":"ruoyi-lqg","db":"PostgreSQL","dbVersion":16,"tenantEnabled":false,
 "encryptEnabled":true,"mockLogin":true,"profile":"dev","buildCommit":"unknown"}
```

## 5 遗留与 raise

1. **真实微信路径未联调**（§2）——等甲方小程序 appid/secret，拿到后在真机跑一次
   `wx.login`（code2session）+ `getPhoneNumber`（手机号快速验证）。代码已就位，只需 `export LQG_WX_APPID/LQG_WX_SECRET`。
2. **越出 `touches` 的改动**：
   - 删除了上游空壳 `ruoyi-admin/.../web/service/impl/XcxAuthStrategy.java`（§3.3）。
     `touches` 只列了 `org.dromara.lqg.auth/**`，这一处在 `org.dromara.web.**`。
     没办法不删：`IAuthStrategy.login` 用 `SpringUtils.getBean(grantType + "AuthStrategy")`
     **按 bean 名**取策略，两个同名 bean 直接 `ConflictingBeanDefinitionException`；
     `@Primary` 对 `getBean(name)` 不生效。这是对上游**删除一个 TODO 空壳**（无任何调用方，
     全仓库只有它自己引自己），不是改上游逻辑。请主会话确认这个边界判断。
   - 新增的 `LqgXcxAuthStrategy.java` 落在 `ruoyi-admin`：`IAuthStrategy` 与 `LoginVo` 本身就是
     ruoyi-admin 的类型，ruoyi-lqg 引用会成环。类里**只有框架接线**，业务规则全在 `org.dromara.lqg.auth`。
3. **`ext.unitName` / `ext.groupName` 本票恒为 null**：`t_lqg_source_unit` / `t_lqg_unit_group`
   是 AUTH-GROUP-001 的表，本票库里没有。`ExtProfileQueryService.lookupName` 用
   `to_regclass(...) IS NOT NULL` 探针判表在不在，**健在时自动开始带出名称**，AUTH-GROUP-001 不用回来改。
   `ext.unitId` / `groupId` / `unitNameInput` / `groupNameInput` / `bindStatus` / `rejectReason` 全部按契约返回。
4. **`/mp/me` 的 `identity` 判据是角色键**（`lqg_internal` / `lqg_admin` → internal），不是 `user_type`
   （seed 里 staff 是 `sys_user`、extA 是 `app_user`，那是巧合不是口径）。与 ADR-0003「内外部只由角色决定」一致。
5. **`username` / `nickname` 首登时都是 `wx_<手机号>`**：登录这一步没有任何微信昵称来源
   （`xcxCode`/`phoneCode` 都换不出昵称），姓名要等用户在「我的」里填（AUTH-EXT-001 的 `real_name`）。
   accept 第 5 段断的是 `extA` 的 `name=="王医生"`，走的是 seed 里已有的 `nick_name`，与本票无关。
6. **`phoneCode` 只做「非空 + 去 `mock:` 前缀」**，没做 11 位格式校验：格式与合法性的把关在
   真实微信接口（它返回什么就是什么）；mock 路径刻意不收紧，免得 accept 里构造测试号要多绕一步。
   AUTH-EXT-001 若要收手机号格式校验，在那一层加。
7. **没有改 `/lqg/sys/ping` 的其它字段**（`buildCommit` 仍是 unknown，属 SYS-STAGING-001/SYS-PROD-001）。

## 6 验证用的长进程

- 后端 java（8081）：**已关**（`pkill -9 -f ruoyi-admin.jar`）。起停脚本留在 `.tmp/run-backend.sh`（gitignore）：
  ```
  nohup bash .tmp/run-backend.sh > .tmp/backend.log 2>&1 &      # 起（dev profile + --api-decrypt.enabled=false）
  pkill -9 -f 'ruoyi-admin.jar'                                  # 停
  ```
  **没留给后续 ticket**：D1 的下一张票自己起即可（脚本可复用）。
- 临时起过的 8099 端口护栏验证进程：已随 `IllegalStateException` 自己退出（`lsof` 已确认无监听）。
- docker 容器：**留着**（`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003），
  起停 `docker compose -f code/deploy/dev/docker-compose.yml up -d|down`。
- 未占 8080 / 5432 / 6379 / 9000 / 9001。
- 数据库收尾状态：`reseed` 后的干净 seed（临时测试号 `1380000007x/8x/9x` 已清，见 `.tmp/clean-test-accounts.sh`）。

## 7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | harness | `api.sh --fresh-module` 的 `ps -o lstart=` 在本 agent 沙箱被禁，accept 第 2 条原样跑 exit 1 | 同 SYS-BASE-001 WARN-1（已记过一次）。只影响 subagent 沙箱，人工/CI 正常。本票用「`find … -newer jar` 为空 + `lsof` 拿 PID + 进程日志 mtime 晚于 jar」三项等价替代。方案：`api.sh` 把启动时间换成 `lsof` / 日志 mtime 兼容写法（本票按规矩没改 `api.sh`）。 |
| WARN-2 | S2 | doc-drift | accept 第 3 条的 `mvn -pl ruoyi-modules/ruoyi-lqg -am test …` 在本机**原样跑必挂**：缺 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`，`~/.m2` 只读 → `FileSystemException: …/spring-boot-dependencies/3.5.10: Operation not permitted` | 同 SYS-BASE-001 坑 5，但这次是**写进 ticket accept 的命令**，所以必须点名。只影响本机/沙箱；普通开发机原样跑即可。方案：ticket 模板给 Maven 类 accept 加一句「本机需带这三个参数」，或在仓库根放 `mvnw` 包装好参数。 |
| WARN-3 | S2 | counterfeit-risk | 根 pom 给 surefire 配了 `<groups>${profiles.active}</groups>` + `<skipTests>true</skipTests>`：**accept 里 `mvn test` 本来会「0 个用例、BUILD SUCCESS」假绿** | 已在本票修掉（`ruoyi-lqg/pom.xml` 覆盖 groups/excludedGroups 为对应属性 + `skipTests=false`，实测从 `Tests run: 0, Skipped: 0` 变成 `Tests run: 4, Skipped: 0`）。**但同一形态会影响后续所有 ticket 的 java 契约测试**：只要 fixture 没有 `@Tag(profile)`，就会静默不跑。方案：把这条写进 ticket 生成器/验收清单（凡 `form: STATE` 且跑 `mvn test` 的票，验收人必须核 surefire 的 `Tests run > 0`），或由主会话决定在根 pom 去掉那个 groups 默认值（会影响全仓测试筛选，本票没动上游）。 |
| WARN-4 | S3 | doc-drift | `doc/api-contract.md` 的 `/mp/me` 未列 `name` 的取值来源 | 契约写的 `data:{userId, name, phoneMasked, identity, ext}` 没说明 `name` 是 `sys_user.nick_name`（本票实现）还是 `t_lqg_ext_profile.real_name`（外部自填）。accept 第 5 段断 `extA.name=="王医生"`，两边恰好都是"王医生"（seed 的 nick_name 与 real_name 同值），所以**两种实现都能过**。方案：在契约里写明 `name = 账号昵称（nick_name）`；AUTH-EXT-001 改 `real_name` 时是否同步昵称，请主会话定。 |
| WARN-5 | S3 | debt | `ruoyi-lqg` 现在依赖 `ruoyi-system`（本票为拿 `sys_user`/角色/权限的 mapper 与 service 而加） | 与 ADR-0001「业务代码只在一个模块」不冲突（方向单一、不成环），但后续 ticket 容易顺手把 `ruoyi-system` 当自家的用。方案：约定「只读框架账号/角色表时用它的 mapper，不往 `sys_user` 写业务字段」（本票只写了 `login_date` 与两条审计字段）。 |
| WARN-6 | S3 | clarify | mp client 的 `client_secret` 是 dev 常量 `mpDevSecret#2026`，且与 `client_id` 的 md5 关系写在注释里 | 只用于 dev/test 的 xcx 登录（token 签发不校验 secret，secret 只在 `sys_client` 里留档）。方案：上线前由部署 ticket 换成随机值并同步 `client_id`（`md5('mp'||secret)`）；本票不引入密钥管理。 |

## 8 坑与解法（给下游，非显然的）

1. **登录前不能跑带 `@DataPermission` 的 SQL**：`SysUserMapper` 的 `selectXxxById` / `updateById`
   与角色、权限查询都带数据权限注解，`PlusDataPermissionHandler` 会调
   `LoginHelper.getLoginUser()` 取当前用户 —— 而登录这一刻**还没有 token**，Sa-Token 抛
   `NotLoginException("token 已被冻结")`，被 `MybatisExceptionHandler` 包成 **401**。
   现象极具迷惑性：账号、绑定行、外部档案**都建出来了**，接口却回 `{"code":401,"msg":"认证失败，无法访问系统资源"}`。
   解法：把「建号/绑定 + `buildLoginUser`」整段包进 `DataPermissionHelper.ignore(...)`
   （RuoYi 自己在 `SysLoginService.recordLoginInfo` 里用的就是它）。`/mp/me` 读自己那一行同理要 ignore，
   否则会被部门数据范围滤掉 —— 表现为 200 但 `phoneMasked=null`、`ext=null`（比 401 更难查）。
   **下游任何「无 token 上下文里读 sys_* 表」的代码都要包 ignore。**
2. **自调用会绕过 `@Transactional`，必须注入自身代理**：`bindOrCreate`（Redisson 加锁）与
   `bindOrCreateLocked`（事务体）拆成两个 public 方法时，写成 `this.bindOrCreateLocked(...)` 就是
   **自调用**，Spring 事务代理被绕过 —— `@Transactional` 变成装饰品，账号/角色/档案/绑定行分属四个事务，
   中途失败会留下半个账号。本票的修法是注入 `ObjectProvider<WxAccountBindService>`，
   用 `selfProvider.getObject().bindOrCreateLocked(identity)` 走代理（`ObjectProvider` 懒取，不形成构造期循环依赖）。
   并发本身由 Redisson 按手机号加锁（key `lqg:auth:wx-bind:<手机号>`）兜住，事务保证「四条写一起成功/一起失败」。
   **下游拆事务方法时先自问：这个调用点走的是代理还是 this？**
3. **PostgreSQL 会先规划整条语句，`CASE` 的不可达分支照样报缺表**：
   想用 `CASE WHEN to_regclass('public.t_lqg_source_unit') IS NULL THEN NULL ELSE (SELECT … FROM t_lqg_source_unit …) END`
   兼容「AUTH-GROUP-001 的表还没建」，实测直接
   `ERROR: relation "t_lqg_source_unit" does not exist` → `/mp/me` 500。
   解法：把名称的读法挪到 Java（`ExtProfileQueryService.lookupName`），先用 `to_regclass(...) IS NOT NULL` 探针
   判表在不在，在才发第二条单表查询。**下游若要写「表可能还没建」的兼容 SQL，别指望 CASE/LEFT JOIN 能挡。**
4. **契约测试会被 surefire 静默跳过、构建仍 SUCCESS**：根 pom 的 surefire 配了
   `<groups>${profiles.active}</groups>`（= `dev`）+ `<skipTests>true</skipTests>`，而需求层给的
   `MockLoginGuardContractTest` 没有 `@Tag`，于是 `Tests run: 0, Skipped: 0` + `BUILD SUCCESS` ——
   「护栏坏了」这条断言**恒绿**。契约测试逐字节不许改（`cmp` 校验），所以只能从构建侧解决：
   在 `ruoyi-lqg/pom.xml` 的 `<properties>` 里给 `lqg.test.groups` / `lqg.test.excludedGroups` 空值 +
   `skipTests=false`，并在 `<build><plugins>` 的 surefire 里引用它们（**注意根 pom 把 surefire 放在
   `pluginManagement`，模块必须覆盖「插件级」配置；写在 `<executions>` 里对 `default-test` 不生效**）。
   改完验证方式是看 surefire 报告的 `Tests run`：`0` → `4`。**下游写 java 契约测试时先确认这一条，否则测试等于没写。**
5. **`.mvn-settings.xml` 的 mirror id 必须是 `public`**（SYS-BASE-001 已记）——换 id 会让整个 `.m2repo`
   缓存逐件重新联网校验，构建从秒级变分钟级。本票的所有 mvn 命令都带这三个参数，没踩。
6. **`mvn package` 也会跑契约测试**：本票把 `ruoyi-lqg` 的 `skipTests` 覆盖成 false 之后，
   连 `-DskipTests` 的打包命令也会执行那 4 个用例（日志里能看到护栏的 WARN）——这是**故意**的
   （护栏是防线，打包时也该验）。若将来嫌慢，请只改 `lqg.test.*` 而不是把 skipTests 关回 true。
