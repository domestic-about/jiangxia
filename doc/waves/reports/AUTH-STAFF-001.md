# AUTH-STAFF-001 · 完工报告

- **ticket**：AUTH-STAFF-001（track AUTH / phase D1 / size M）
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 原文逐条实跑，见 §4；唯一改动 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`，等价证据见 §4.0）
- **分支**：`task/D1`，本地**一条** `AUTH-STAFF-001: …` 提交（`git log --oneline -1` 取号；未 push / 未 merge；`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/verify/seed/`、`gen_seed.py`、`doc/verify/api.sh` 一个字节没动）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 15:47:18`，后端进程 **PID 44605**（8081，dev profile + `--api-decrypt.enabled=false`）
- **接手说明**：上一任 impl subagent 在收尾阶段崩溃，本报告由接手 agent 完成。接手时代码已在盘上，本 agent 逐文件审完、**修掉两处真问题**（§3.1）、重打包、重跑全部 accept 并补了对抗性探针；上一任的 `.tmp/accept*.sh` 只当交叉核对，结论全部来自本 agent 对 ticket 原文的实跑。

---

## 1 升级与预建两条路径各一次的接口输出（ticket §4.1）

两条路径都真跑，输出逐字如下（最终 jar，dev 库）。

### 升级路径 —— 手机号已有账号（`13800000016`，seed 里是外部账号 `wx_13800000016` / 角色 103）

```
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/staff/check?phone=13800000016'
{"code":200,"msg":"操作成功","data":{"exists":true,"external":true,"name":"吴同学"}}

$ bash doc/verify/api.sh --as admin POST /lqg/auth/staff \
    '{"phone":"13800000016","name":"吴同学","roleKey":"lqg_internal","password":"Lqg@test123"}'
{"code":200,"msg":"操作成功","data":{"userId":9000000116,"upgraded":true}}

$ python3 doc/verify/db.py --sql "账号数|角色串|t_lqg_wx_bind 行数|user_name"
1|lqg_internal|1|wx_13800000016
```

**这三行合起来就是 ticket 口径「授权是升级不是新建」的机器证据**：手机号只有 **1** 个账号、角色换成内部、
**微信绑定原样 1 行**、`user_name` 还是原来的 `wx_13800000016`（没有改成 `lqg_…`）。
`upgraded:true` 与 `userId=9000000116` 同时成立 —— 若实现成「INSERT 新账号」，两个值都会变。

管理员角色同时挂内部（ticket §2.1）：

```
$ bash doc/verify/api.sh --as admin PUT /lqg/auth/staff/9000000116/role '{"roleKey":"lqg_admin"}'
{"code":200,"msg":"操作成功","data":null}
$ python3 doc/verify/db.py --sql "SELECT string_agg(r.role_key,',' ORDER BY r.role_id) …"
lqg_admin,lqg_internal
```

### 预建路径 —— 手机号库里没有（`13800000088`）

```
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/staff/check?phone=13800000088'
{"code":200,"msg":"操作成功","data":{"exists":false,"external":false,"name":null}}

$ bash doc/verify/api.sh --as admin POST /lqg/auth/staff \
    '{"phone":"13800000088","name":"预建同学","roleKey":"lqg_internal","password":"Lqg@test123"}'
{"code":200,"msg":"操作成功","data":{"userId":"2101941511770910722","upgraded":false}}

$ python3 doc/verify/db.py --sql "user_name|user_type|nick_name|角色|t_lqg_wx_bind 行数"
lqg_13800000088|sys_user|预建同学|lqg_internal|0
```

`upgraded:false` + `user_name='lqg_'+手机号` + `user_type='sys_user'` + **0 行微信绑定**
（他还没登录过小程序；首次登录时 AUTH-LOGIN-001 按手机号自然对上这个账号）。

> 列表接口（`GET /lqg/auth/staff`，含 `wxBound` 读时算）实跑输出：
> ```json
> {"code":200,"msg":"操作成功","data":[
>   {"userId":9000000100,"name":"测试管理员","phone":"13800000000","roleKey":"lqg_admin","roleName":"实验室管理员","wxBound":false},
>   {"userId":9000000116,"name":"吴同学","phone":"13800000016","roleKey":"lqg_admin","roleName":"实验室管理员","wxBound":true},
>   {"userId":9000000101,"name":"李工","phone":"13800000001","roleKey":"lqg_internal","roleName":"内部人员","wxBound":true},
>   {"userId":"2101941511770910722","name":"预建同学","phone":"13800000088","roleKey":"lqg_internal","roleName":"内部人员","wxBound":false}]}
> ```

## 2 外部账号尝试登录工作台的响应体（ticket §4.2）

```
$ # 病灶先确认：seed 里 extB（wx_13800000012）**故意带着可用口令** admin123
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user WHERE user_name='wx_13800000012' AND length(password) = 60" --eq 1
1

$ login wx_13800000012          # curl POST /auth/login，clientid=PC，password=admin123
{"code":500,"msg":"该账号无权登录工作台，请使用小程序登录","data":null}

$ login lqg_13800000001         # 正向对照：内部人员必须登得上，否则「一律拒绝」也能绿
{"code":200,"msg":"操作成功","tokenLen":363}
```

**关键点**：返回体里 `data` 是 `null` —— **根本没发 token**，拒绝发生在**登录接口**里（不是「登录成功后前端没菜单」）。
`msg` 含「**无权**」，与「用户名或密码错误」是两个分支：病灶（外部账号有可用口令）先被第 1 段证实，
所以第 2 段的失败**不可能**是密码分支。实现方式是本模块自己的 AOP 切面
（`WorkbenchLoginGuardAspect` 后置校验 `PasswordAuthStrategy.login`），**没改上游一个字节**。

外部账号不许设工作台密码（ticket §4.2 后半 + §2.1）：

```
$ bash doc/verify/api.sh --as admin --bizcode PUT /lqg/auth/staff/9000000111/reset-pwd '{"password":"Lqg@test123"}'
500	该账号不是内部人员
$ python3 doc/verify/db.py --sql "SELECT length(password) FROM sys_user WHERE user_id=9000000111" --eq 0
0
```

拒绝 + **库里长度仍为 0**：不是「先写进去再报错」。

## 3 改了哪些文件（ticket §4.3）

### 3.1 本 agent 接手后的改动（2 处，都在本票 `touches` 内）

| 文件 | 改动 | 为什么 |
|---|---|---|
| `.../auth/staff/service/StaffGrantService.java` | 撤销时新增 **`StpUtil.logout(user.getUserType() + ":" + userId)`** 作为主手段，`roleService.cleanOnlineUser(...)` 降为兜底 | 上一任只靠 `cleanOnlineUser` 踢人，**会漏踢**：它内部 `StpUtil.searchTokenValue("")` → `PlusSaTokenDao.searchData` 把 Redis 扫描结果缓存进 Caffeine（`expireAfterWrite=5s`），撤销前 5 秒内签发的 token 不在快照里 → 被撤销的人拿旧 token 照样 200。负对照实测复现，见 §4.4。这正是 ticket 点名的假绿形态「撤了权限的人还能看半小时数据」 |
| `.../auth/staff/controller/StaffController.java` | 删掉上一任给 `reset-pwd` / `revoke` 加的「先 `response.setStatus(500)` 再抛异常」 | 上游 `GlobalExceptionHandler` 对 `ServiceException` 的约定是 **HTTP 200 + `code:500`**，前端 `utils/request.ts` 按 `code` 分支把 `msg` 原样弹给用户；多写一个 HTTP 500 会让 axios 落进 error 分支，页面只剩「系统接口500异常」，真实原因（「该账号不是内部人员」）被吞。accept 用的是 `api.sh --bizcode`，读的本来就是业务码 —— 那三行 hack 既不必要又有害 |

### 3.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.auth.staff`）

| 文件 | 职责 |
|---|---|
| `controller/StaffController.java` | 6 个端点：`GET /lqg/auth/staff`（列表）、`GET …/check`（只读预检）、`POST /lqg/auth/staff`（授权）、`PUT …/{userId}/role`、`PUT …/{userId}/reset-pwd`、`DELETE …/{userId}`；权限串 `lqg:auth:staff:{list,grant,edit,resetPwd,revoke}` |
| `service/StaffGrantService.java` | 授权=升级/预建、改角色、重置密码（只对内部）、撤销=降回 103+补 unbound+踢 token；守卫（不能撤自己 / 不能弄没最后一个管理员）全部**在写库之前**抛 |
| `guard/StaffGrantRules.java` | 纯函数规则（`isInternal` / `isExternalOnly` / `canRevokeSelf` / `canLoseAdmin` / `grantedRoleKeys`），无 Spring 无库，可被契约测试直接钉 |
| `aspect/WorkbenchLoginGuardAspect.java` | 工作台登录后置校验：不含 `lqg_internal`/`lqg_admin`（或上游 superadmin）→ 拒。**放本模块，不改上游**；`@AfterReturning` 让「密码错」与「无权」天然分开 |
| `mapper/StaffAccountMapper.java` | 唯一的自写 SQL（SELECT）：带内部角色的账号列表 + 角色键/角色名 |
| `domain/bo/StaffGrantBo·StaffRoleBo·StaffResetPwdBo`、`domain/vo/StaffGrantVo·StaffCheckVo·StaffMemberVo` | 入参/出参，字段与 `doc/api-contract.md` 逐字对齐（`{userId, upgraded}`） |
| `src/test/java/.../staff/guard/StaffGrantRulesContractTest.java` | 6 个用例钉住五条规则（`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`） |

### 3.3 迁移（Flyway）

`ruoyi-admin/src/main/resources/db/migration/V202609210920__AUTH-STAFF-001-menu.sql`

**取号依据 `doc/lint-profile.yaml`**：日期段按任务取、后做的一定更大（`out-of-order=false`）→ D1 = `20260921`；
HHmm 按域分段 → AUTH = `09xx`；`0920` 未被占用且 **大于当前最高版本 `0910`**（不会触发 `FlywayValidateException`）。
菜单号依据同文件：AUTH 域 `5100-5199`。

| menu_id | 类型 | 名字 | path / component | perms |
|---|---|---|---|---|
| 5100 | M | 人员与单位 | `auth` / — | — |
| 5110 | C | 内部人员授权 | `staff` / `lqg/auth/staff/index` | `lqg:auth:staff:list` |
| 5111-5115 | F | 查询/授权/改角色/重置密码/撤销 | — | `lqg:auth:staff:{list,grant,edit,resetPwd,revoke}` |

`sys_role_menu` **只授 101**（含父目录 5100 —— 上游 `getChildPerms(menus, 0)` 建树要求，SYS-WEB-001 踩过）；
并显式 `DELETE` 一次 102 在本号段的行（幂等）。库内实测：

```
$ python3 doc/verify/db.py --sql "SELECT menu_id||':'||string_agg(role_id::text,',' ORDER BY role_id) FROM sys_role_menu WHERE menu_id BETWEEN 5100 AND 5115 GROUP BY menu_id ORDER BY menu_id"
5100:101  5110:101  5111:101  5112:101  5113:101  5114:101  5115:101
$ python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_role_menu WHERE role_id=102 AND menu_id BETWEEN 5100 AND 5115" --eq 0
0
$ bash doc/verify/api.sh --as admin GET /system/menu/getRouters | jq -c '[.data[]|select(.path=="/auth")|…]'
[{"path":"/auth","meta":"人员与单位","children":[{"path":"staff","component":"lqg/auth/staff/index","meta":"内部人员授权"}]}]
```

### 3.4 前端（`code/plus-ui`）

| 文件 | 说明 |
|---|---|
| `src/views/lqg/auth/staff/index.vue`（320 行） | 列表（姓名/手机号/角色/微信绑定/操作）+「按手机号授权」`el-dialog`（**显式 `:close-on-click-modal="true"`**，因为上游 `main.ts` 把 ElDialog 默认改成了 false）+ 提交前调 `GET …/check` 显示「该手机号已登录过小程序，将把原账号升级为内部人员」+ 行操作改角色/重置密码/撤销（`$modal.confirm` 二次确认） |
| `src/api/lqg/auth/staff.ts` | 6 个接口的 TS 封装与类型 |
| `src/lang/lqg/auth-staff.zh_CN.ts` / `.en_US.ts` | 域内 i18n（键路径 `lqg.auth.staff.*`），不往上游两个共享大文件里加 key（SYS-WEB-001 立的约定） |
| `src/lang/index.ts` | **修改**：域名解析从 `path.replace(...)` 改成 `base.split('-')[0]` 并按域 merge —— 让 `auth-staff.*` 落进 `lqg.auth`、与后续 `auth-group.*` 合并而不是互相覆盖。**这一处不在 ticket 的 `touches` 里，见 §5.1** |

**构建 + 运行期证据**（不是 grep 出来的）：

```
$ cd code/plus-ui && rm -rf dist && pnpm build:prod      # vite build --mode production
✓ 3087 modules transformed.
✓ built in 7.27s
$ grep -q 'lqg/auth/staff/index' dist/assets/*.js && echo 组件路径命中
组件路径命中      # 中文文案「该手机号已登录过小程序」、英文 "Internal Staff" 同样命中
```

截图与机器可读探针在 `doc/waves/reports/AUTH-STAFF-001/`：

| 文件 | 内容 |
|---|---|
| `01-staff-list.png` | 侧边栏「人员与单位 › 内部人员授权」+ 列表两行（测试管理员/实验室管理员/未绑定、李工/内部人员/已绑定）+ 面包屑 |
| `02-grant-dialog-upgrade-tip.png` | 授权弹窗：手机号 `13800000016`、姓名、角色、工作台初始密码，黄色提示「该手机号已登录过小程序，将把原账号升级为内部人员」 |
| `03-dialog-closed-by-mask.png` | 点蒙层后弹窗已关（`probe-dialog-close-on-mask.json`：`{"beforeMask":true,"overlayVisibleAfterMaskClick":false}`） |
| `04-revoke-confirm.png` | 撤销二次确认（`probe-revoke-confirm.json` 文案逐字） |
| `probe-staff-list.json` | `url=http://127.0.0.1:8082/auth/staff`、表头 `[姓名,手机号,角色,微信绑定,操作]`、两行数据、`rawI18nKeysLeaked:false`（证明 i18n 合并真的生效，没漏原始 key） |
| `probe-grant-dialog.json` / `probe-sidebar.json` | 弹窗标签与提示、侧边栏文字 |

> 截图由上一任在本票前端工作树上拍摄（`15:36`）；前端文件最后改动是 `15:14`/`index.ts 15:29`，
> **均早于截图**，本 agent 复核过前端产物未再改动，且本次 `pnpm build:prod` 重新构建通过 —— 图与当前代码同源。

## 4 accept 逐条 ✅ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，第 4 次命中）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，在本 subagent 沙箱里是 `/bin/ps: Operation not permitted` → `exit 2`。
按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的**两个半边**（源码不得比 jar 新 + 进程不得早于 jar）：

```
jar mtime                : 2026-09-21 15:47:18
lqg src newer than jar   : [空]        ← 没有比 jar 新的 ruoyi-lqg 源码
8081 PID                 : 44605
该 PID 打开的文件含       : .../ruoyi-admin/target/ruoyi-admin.jar   ← 就是上面那个 jar
后端日志 mtime            : 2026-09-21 15:47:26（进程启动即写日志，晚于 jar）
嵌套 jar 复核             : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 auth/staff/{controller,service,guard,aspect,mapper,domain} 全部 class（15:47 时间戳）
```

**复跑脚本随报告落盘**：`doc/waves/reports/AUTH-STAFF-001/accept-runners/`（`sta001-acc1/2/3.sh` 与
`sta001-kick-probe.sh` + `README.md`）。acc1/2/3 与本次实跑的 `.tmp` 版本**逐字节相同**（`cmp` 已核），
从工作区根执行即可；kick-probe 只多改了一行 `cd`（因为它不再放在 `.tmp/` 下）。

### 4.1 accept 1 · STATE —— ✅

`bash .tmp/sta001-acc1.sh`（原文逐字，只去掉 `--fresh-module ruoyi-lqg`）：

```
true                                        ← extF 撤销前 identity=external
true                                        ← POST 授权：code=200 && upgraded=true && userId=9000000116
1|lqg_internal|1                            ← 账号数 1 | 角色 lqg_internal | wx_bind 1 行
true                                        ← 同一 token 再打 /mp/me：identity=internal（角色改完即时生效）
true                                        ← DELETE 撤销：code=200
lqg_external                                ← 撤销后角色串恰为 lqg_external（不是 NULL = 没删号）
1                                           ← 撤销自己/最后一个 admin 被拒后，9000000100 的 101 行仍在
ACCEPT-1 EXIT=0
```

（第 6 段 `… --bizcode GET /mp/me | grep -qE '^401'` 无 stdout 输出 —— `grep -q` 成功即静默；
它在 `&&` 链里，链断则整条 accept 返回非 0，见 §4.3 的「假绿」说明。）

撤销「没有外部档案」的内部人员 → 补建 `unbound` 一行（ticket §2.1，accept 没直接断，本 agent 补跑）：

```
$ POST /lqg/auth/staff {phone:13800000088, roleKey:lqg_internal}   → userId=2101940610796740610
$ python3 db.py "wx_bind 行数|ext_profile 行数"                     → 0|0        ← 确实没有外部档案
$ DELETE /lqg/auth/staff/2101940610796740610                        → {"code":200,…}
$ python3 db.py "角色|账号仍在|ext_profile.bind_status"              → lqg_external|1|unbound
$ DELETE 同一个 userId 第二次                                        → 500  该账号不是内部人员（业务码非 200）
```

**同一旧 token 的前后对照**（撤销 = 立刻失效，不是「等半小时」）：

```
$ extF /mp/me                          → {"code":200,…"identity":"external"}
$ admin POST 授权 13800000016           → {"code":200,…"upgraded":true}
$ extF /mp/me                          → {"code":200,…"identity":"internal"}   ← 同一个 token，权限即时变
$ admin DELETE /lqg/auth/staff/9000000116 → {"code":200,…}
$ 同一个 token 再打 /mp/me              → 401（accept 原文断的就是这条）
```

### 4.2 accept 2 · API —— ✅

`bash .tmp/sta001-acc2.sh`（原文逐字，只去掉 `--fresh-module ruoyi-lqg`）：

```
true      ← 内部人员 lqg_13800000001 登得上，access_token 长度 > 10
1         ← 病灶在位：wx_13800000012 确实带着 length=60 的可用口令
true      ← 外部账号登录：code!=200 且无 token 且 msg 含「无权」
0         ← 外部账号 reset-pwd 被拒后，库里 length(password) 仍为 0
ACCEPT-2 EXIT=0
```

原始响应体见 §2。

### 4.3 accept 3 · MENU —— ✅

`bash .tmp/sta001-acc3.sh`（**原文逐字，无任何改动**）：

```
5110:staff:lqg/auth/staff/index      ← menu_id:path:component 精确相等
101                                  ← sys_role_menu 里 5110 只授给 101（--col-set）
true                                 ← getRouters 下发里 component == "lqg/auth/staff/index" 恰好 1 条
ACCEPT-3 EXIT=0                      ← test -f code/plus-ui/src/views/lqg/auth/staff/index.vue
```

### 4.4 追加 · 对抗性探针「撤销后旧 token 必须立刻 401」—— ✅（修复前红 / 修复后绿）

accept 原文的顺序**恰好**让上游那份 5 秒快照是新鲜的，所以它对「只靠 `cleanOnlineUser` 踢人」这个实现是**恒绿**的。
本 agent 另写了 `.tmp/sta001-kick-probe.sh` 刻意构造 5 秒窗口：先撤销一个一次性账号（触发一次
`cleanOnlineUser`，把 `Authorization:**` 的搜索快照坐实）→ **立刻（<5s）**登录 extF → 授权 → 撤销 → 用旧 token 打 `/mp/me`。

```
负对照（只留上游 roleService.cleanOnlineUser，停用本 agent 的 StpUtil.logout 一行）：
  撤销后用旧 token 打 /mp/me 的业务码/消息: 200	操作成功
  KICK-PROBE FAILED rc=1                     ← 旧 token 还活着！这就是 ticket 点名的假绿

修复后（最终 jar）：
  撤销后用旧 token 打 /mp/me 的业务码/消息: 401	认证失败，无法访问系统资源
  （探针 exit 0）
```

### 4.5 Java 契约测试 —— ✅（`Tests run > 0`，避开 surefire 假绿）

```
$ (cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg -am test \
      -Dtest=StaffGrantRulesContractTest -Dsurefire.failIfNoSpecifiedTests=true \
      -s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome)
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in org.dromara.lqg.auth.staff.guard.StaffGrantRulesContractTest
BUILD SUCCESS
$ 打包时（-DskipTests，lqg 模块覆盖 skipTests=false 让护栏仍跑）：
Tests run: 6 … + Tests run: 4 … → Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

> 注意：`-Dtest=…` 式的 accept 命令在本机**必须**带那三个参数（`~/.m2` 只读），这是 AUTH-LOGIN-001
> WARN-2 已记过的 doc-drift，本票不再重复计数。

### 4.6 accept 全绿后的收尾状态

```
$ bash doc/verify/reseed.sh --yes            → 已灌 01/02，03-07 因表未建跳过 → reseed 完成
$ 临时账号（1380000007x/8x/9x）              → 0 个
$ 8 个 seed 账号的角色                        → lqgadmin:lqg_admin, lqg_13800000001:lqg_internal,
                                              wx_13800000011..16:lqg_external
```

（库里另有 `admin` / `test` / `test1` 三个上游基线账号，来自 `V202609210800` 基线，不是本票残留。）

## 5 遗留与 raise（ticket §4.5）

### 5.1 越出 `touches` 的改动（1 处，请主会话确认边界）

**`code/plus-ui/src/lang/index.ts`**（modified）。`touches` 只列了 `code/plus-ui/src/lang/lqg/auth-staff.*.ts`，
但本票的 i18n 文件叫 `auth-staff.*`，而 SYS-WEB-001 在 `index.ts` 里把「文件名去掉后缀」直接当域名 —— 不改它，
键路径就变成 `lqg['auth-staff'].staff.*`，且后续 `auth-group.*` 会整片覆盖 `auth-staff.*`。
改法（向后兼容，`sys.*` 行为不变）：域名取 `base.split('-')[0]`，同域多文件按 spread 合并 →
`auth-staff` / `auth-group` 都落进 `lqg.auth.*`。运行期已证（`probe-staff-list.json` 的 `rawI18nKeysLeaked:false`
+ `pnpm build:prod` 通过）。**没有别的越界改动**；没有动上游 `ruoyi-system` / `ruoyi-common-*` 源码。

### 5.2 与 `doc/api-contract.md` 的差异

契约第 37 行列了 `GET/POST /lqg/auth/staff`、`PUT …/role`、`PUT …/reset-pwd`、`DELETE …/{userId}`，
POST 的形状与实现**逐字一致**。**缺一条**：ticket §2.3 要求的前端只读预检 **`GET /lqg/auth/staff/check?phone=`**
契约里没写（这是契约侧的 doc-drift，已记 WARN-3；本票按 ticket 实现，契约是只读区，未改）。

### 5.3 口径上没把握 / 需要后端确认的两点

1. **撤销用 `StpUtil.logout(loginId)` 而不是 ticket 字面的 `StpUtil.logout(loginId)` 语义**：ticket §2.1 写的是
   `StpUtil.logout(loginId)`。若依里 loginId 的形状是 `userType + ":" + userId`（`LoginUser.getLoginId()`），
   所以实现里显式拼了这个前缀，而不是传裸 `userId`（传裸 id 匹配不上任何 token，等于没踢）。
2. **工作台准入的 `superadmin` 判据**：`StaffGrantRules.INTERNAL_ROLE_KEYS` 里留了 `superadmin` 角色键，
   切面另外对 `userId == 1` 直接放行。若依基线里超管就是 `user_id=1` + 角色 `superadmin`，两者等价；
   留双重判据只是防「角色键被改名」。

### 5.4 明确没做（ticket §3 边界，逐条核对过）

不做单位/组别/外部用户核验（AUTH-GROUP-001）；不做「授权时间」列与授权流水表；不改上游用户管理页；
不给 102 授本页或用户管理；不做短信通知。`t_lqg_source_unit` / `t_lqg_unit_group` 一行没建，
菜单 5100 目录先建好（AUTH-GROUP-001 的页面可挂进来）。

## 6 验证用的长进程（ticket §4.6）

- **后端 java（8081）：已关**（`pkill -9 -f 'ruoyi-admin.jar'`），`lsof -ti tcp:8081` 已空。
  起停脚本 `.tmp/run-backend.sh`（gitignore）可复用。
- **8082 上的 node vite dev server（PID 14410，上一任留下的）：已关**。
- 8080 / 8083 / 8099 上没有本票留下的监听（8080/9000/9001/6379 是 Kevin 本机日常服务，始终没碰）。
- **DB 收尾 = 干净 seed**（`reseed.sh --yes` 后未再做任何写操作；临时手机号账号用
  `.tmp/clean-test-accounts.sh` 清过并核过为 0）。
- docker 容器**留着**：`lqg-dev-postgres` 5433 / `lqg-dev-redis` 6380 / `lqg-dev-minio` 9002+9003
  （D1 后续 ticket 直接用；`docker compose -f code/deploy/dev/docker-compose.yml up -d|down`）。
- `code/plus-ui/.env.development` **已回到 SYS-WEB-001 提交时的值**
  （`VITE_APP_PROXY_TARGET='http://127.0.0.1:8081'`、`VITE_APP_PORT=8082`），`git diff code/plus-ui/.env.development` **无输出**。

## 7 坑与解法（给下游，3-5 条）

1. **「踢人」不能只调上游 `roleService.cleanOnlineUser(userIds)`**：它内部 `StpUtil.searchTokenValue("")`
   → `PlusSaTokenDao.searchData` 把整段 Redis 扫描结果缓存进 Caffeine（`expireAfterWrite=5s`）。
   **撤销前 5 秒内刚签发的 token 不在快照里 → 漏踢**，被撤销的人拿旧 token 继续 200。
   解法：`StpUtil.logout(user.getUserType() + ":" + userId)`（Sa-Token 按 loginId 注销它名下全部 token），
   `cleanOnlineUser` 只当兜底。**下游任何「改角色/停用账号要踢下线」的票都照这个办，并且要为它写一条能红的负对照探针**
   —— accept 原文的顺序会让这个 bug 恒绿。
2. **拒绝业务操作不要动 HTTP 状态码**：上游 `GlobalExceptionHandler` 对 `ServiceException` 的约定是
   HTTP 200 + `code:500`，前端 `utils/request.ts` 正是按 `code` 分支把 `msg` 弹给用户；额外置 HTTP 500 会让
   axios 落进 error 分支，页面只剩「系统接口500异常」。`api.sh --bizcode` 读的也是业务码，不是 HTTP 码。
3. **`set -e` 管不住 `a && b && c` 里非末尾位置的失败**（bash 手册明写）——accept 原文是一条 `&&` 长链，
   直接 `set -e; 长链` 一旦中途断掉会「静默短路 + 脚本 exit 0」，是标准的**假绿**。
   本次把长链包进函数再判整条链的 rc（`if acc1; then … else rc=$?; exit 1; fi`），第一次实跑就靠它抓到了真失败。
4. **`api.sh` 的 token 缓存只看文件 mtime（<20 min），不看 token 是否还有效**：接手时上一任留下的
   `$TMPDIR/lqg-verify-token-admin-*` 里的 token 已不在 Redis，缓存命中 → 一上来就
   `401 认证失败，无法访问系统资源`，看着像「实现坏了」。**跑 accept 前先
   `rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*`**。
5. **`reseed.sh` 清不掉雪花 id 的 `sys_user`**：它只 `DELETE … BETWEEN 9000000000 AND 9000009999`，
   而运行时新建的账号是雪花 id。本票「预建路径」探测会留下 `lqg_13800000088` 这种孤儿，
   必须用 `.tmp/clean-test-accounts.sh` 按手机号补一刀（否则下次跑 `list` 会多一行）。
   **任何建 `sys_user` 的票，accept 收尾都要加这一步。**

## 8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | harness | `doc/verify/api.sh --fresh-module` 的 `ps -o lstart=` 在本 agent 沙箱被禁，accept 原样跑 `exit 2` | **已连续四张票命中**（SYS-BASE-001 / AUTH-LOGIN-001 / SYS-WEB-001 / 本票）。只影响 subagent 沙箱，人工/CI 正常。本票用「`find … -newer jar` 为空 + `lsof` 拿 PID + 进程持有该 jar + 日志 mtime 晚于 jar + 嵌套 jar 内含本票 class」五项等价替代。方案：把启动时间换成 `lsof`/日志 mtime 兼容写法，或给 `api.sh` 一个「跳过启动时间守卫」的开关（改动属验收执行器，四张票都按规矩没动 `api.sh`）。 |
| WARN-2 | **S1** | counterfeit-risk | 上游 `PlusSaTokenDao.searchData` 的 Caffeine 5 秒写缓存，让 `roleService.cleanOnlineUser(userIds)` **漏踢刚签发的 token** | 影响**所有**「改角色 / 停用 / 撤销后要踢下线」的票（AUTH-EXT-002、AUTH-GROUP-001、SYS-*）与将来任何照着上游 `cleanOnlineUser` 写踢人的实现：表现为「撤销成功（200）、库也改了，但旧 token 还能用」。本票已改为 `StpUtil.logout(loginId)` 并写了能红的负对照探针（§4.4）。方案：① 生成器/验收清单加一条「凡踢人断言，必须先构造 5 秒窗口的负对照」；② 长期考虑给 `cleanOnlineUser` 换成不被缓存的枚举（属上游 `ruoyi-system` 改动，本票没动）。 |
| WARN-3 | S3 | doc-drift | `doc/api-contract.md` 的 AUTH 一节没列 `GET /lqg/auth/staff/check?phone=` | ticket §2.3 明确要求这个只读预检接口（前端弹窗提示用它），实现已按 ticket 落地。契约是只读区，本票未改。方案：补一行 `GET /lqg/auth/staff/check?phone=` → `data:{exists, external, name?}`。 |
| WARN-4 | S3 | harness | `api.sh` 的 token 缓存（`$TMPDIR/lqg-verify-token-*`）只按 mtime 判新鲜，陈旧 token 造成假 401 | 接手本票时被它误导过一次（一上来 `401`，误以为实现坏了）。方案：`api.sh` 命中缓存后先探一次轻接口（如 `GET /lqg/sys/ping`），非 200 就重登；或在 accept 文档里写明「跑前清 `$TMPDIR/lqg-verify-token-*`」。 |
| WARN-5 | S2 | process | ticket `touches` 未包含 `code/plus-ui/src/lang/index.ts`，但本票**必须**改它（否则键路径退化成 `lqg['auth-staff']`，且与后续 `auth-group.*` 互相覆盖） | 已改（向后兼容，`sys.*` 行为不变）并随本票提交。影响后续所有「按域拆 i18n 文件」的 WEB 票：`auth-group` / `sample-*` 等都要靠这条解析。方案：把 `src/lang/index.ts` 加进相关 ticket 的 `touches`，或把「同域多文件」的解析约定写进 SYS-WEB-001 立的 `src/lang/lqg/` 规范。 |
| WARN-6 | S3 | debt | 工作台拒绝外部用的是 `@AfterReturning`：拒绝发生时上游**已经签发**了 token（存在 Redis，但没返回给客户端） | 不构成可利用口子（客户端拿不到 token），但会给 Redis 留一个到 TTL 才消失的会话。方案：若要求在签发前拦，就得在本模块复制一遍口令校验逻辑（不划算，且会与上游口令策略漂移）；建议保持现状，仅在文档里写明。 |
| WARN-7 | S3 | harness | `doc/verify/reseed.sh` 只清 `sys_user` 的 `9000000000-9000009999` 段，清不掉运行时按雪花 id 新建的账号 | 任何「会新建 sys_user」的票（AUTH-EXT-*、AUTH-GROUP-001…）跑完 accept 都会留孤儿，下一次 `list`/计数类断言就会红。本票靠 `.tmp/clean-test-accounts.sh` 手工清。方案：`reseed.sh` 增加一段「删掉全部非 seed 段账号（或按 phone 白名单）」的清理，或把清理脚本收进 `doc/verify/` 作为标准收尾步骤。 |

> 已在上游验收报告里记过、本票**不重复计数**的既有 WARN：Maven 三参数（AUTH-LOGIN-001 WARN-2）、
> surefire `groups` 假绿形态（AUTH-LOGIN-001 WARN-3，本票已核 `Tests run: 6 > 0`）。
