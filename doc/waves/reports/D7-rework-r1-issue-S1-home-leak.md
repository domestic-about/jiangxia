# D7 返工 r1 · S1 修复报告：外部账号（mp token）可读工作台首页两个端点

- 任务：D7 r1 L3 抓到的一条 **S1 产品缺陷**（返工单，非新 ticket）
- 缺陷来源：`doc/waves/qa/D7-r1-L3.json` 的 issues[0]（L3 第 7 条额外条，把 issue #270 从「理论上可读」升级为「实测可读且带跨单位明细」），由 owner 定级为 **S1**
- 分支：`task/D7`（已确认，未切分支 / 未 push / 未合分支 / 未动 `state.json` 与 `_manifest.json`）
- 环境：`qa-up.sh --backend-port 8094 --no-web --no-mp`；`LQG_VERIFY_ENV_FILE=.tmp/qa-env/8094/verify.env`
- 结论：**已堵住**。admin(101) / staff(102) / 上游 superadmin 两个端点 200；extA/extB/extC/extE/extF 两个端点一律 403（`data:null`）；pc 密码登录护栏未受影响（extB → 500）。
- ★ 返工中发现一个**必须记账的坑**：照票面/返工单字面写的 `@SaCheckRole({"lqg_admin","lqg_internal"})` **会让 admin 和 staff 一起 403**（Sa-Token 默认 `SaMode.AND`）。见第 4 节。
- ★ **补记（同返工单收尾）**：角色集最终扩成 `{lqg_admin, lqg_internal, superadmin}`，与 `StaffGrantRules.INTERNAL_ROLE_KEYS` 对齐，并由契约测试守一致性 —— 见**第 10 节**（含 superadmin 实测）。

---

## 1. 缺陷复述（L3 实测原文口径，修复前）

`HomeController` 的两个 handler 只挂 `@SaCheckLogin`，**不需要工作台会话** —— 小程序（mp client）签发的 token 就够。

| 端点 | 身份 | 修复前业务码 | 实际返回 |
|---|---|---|---|
| `GET /lqg/home/todo` | extA / extB / extC / extE / extF | **200** | `{pendingSamples:2, pendingEmbeds:1, cryoOverdue:2, pendingExtUsers:2, renderFailed:0}`（与 staff/admin 完全一致） |
| `GET /lqg/home/recent` | extA / extB / extC / extE / extF | **200** | **9 行**，字段 `{submitTime, submitNo, sourceUnitName, submitSource, verifyStatus}`，跨 A 医院 / B 大学 / 本中心、跨内外部 |

极端对照（L3）：**extC 的样本可见集合只有 `{1005}`（A 医院·消化内科组）**，却能读到

- `SJ90000006`（**B 大学** / external / valid）、
- `SJ90000008`（**本中心** / internal / valid）、
- 以及 SJ90000007/02/03/05/04/01（A 医院）。

唯一还成立的对照是「extB 走 pc 密码登录会被拒」——即**只有 pc 那条路被挡住**，mp token 不经过它。

**根因**：两个 handler 只挂 `@SaCheckLogin`，绕过了本项目的外部隔离咽喉（ADR-0004 / `ExtChokepointContractTest` 守着的 I1–I4 不变量）；类注释还把票面 §2 的「登录即可调（101 / 102）」当成「登录门 ⇒ 内部」的依据，与 AUTH-STAFF-001 §2.2「工作台拒外部」冲突。

**为什么是 S1 而不是 cosmetic**：外部隔离是甲方明确要求 + 由 `ExtChokepointContractTest` 守着的不变量；这两个端点等于在咽喉之外开了一个读口，泄露的是全中心 5 个聚合待办数 **+ 最近 10 条送检单号 / 来源单位 / 内外部 / 核验状态**（L3 原话：「不止 5 个聚合数」）。

---

## 2. 改了什么

三个文件，**均在 SYS-HOME-001 的 ticket `touches` 范围内**（main + test 的 `sys/home/**`）。

### 2.1 `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/sys/home/controller/HomeController.java`（+44/-? 行）

```diff
-import cn.dev33.satoken.annotation.SaCheckLogin;
+import cn.dev33.satoken.annotation.SaCheckRole;
+import cn.dev33.satoken.annotation.SaMode;
```

```diff
     /**
      * 五个待办数（卡片与侧边菜单角标共用这一次请求的结果）。
+     *
+     * <p>内部角色闸（{@code mode = SaMode.OR}）：101 {@code lqg_admin} / 102
+     * {@code lqg_internal} / 上游 {@code superadmin} 任一即可（= {@code INTERNAL_ROLE_KEYS}），
+     * 外部 403。
      */
-    @SaCheckLogin
+    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
     @GetMapping("/todo")
     public R<HomeTodoVo> todo() { … }

     /**
      * 最近提交 10 条（送检时间倒序；不含软删的样本）。
      */
-    @SaCheckLogin
+    @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
     @GetMapping("/recent")
     public R<List<HomeRecentVo>> recent() { … }
```

类注释：删掉把「登录即可调」当依据的那段，改成三段**为什么**：

1. **鉴权是「内部角色闸」，不是「登录门」** —— 101 / 102 / 上游 `superadmin`，103 `lqg_external` 一律 403；
2. **为什么不能只挂 `@SaCheckLogin`** —— mp token 也是「已登录」；AUTH-STAFF-001 §2.2 那条护栏只拦 pc 密码登录，mp token 不经过它，所以「登录门 ⇒ 内部」前提不成立；这两个端点绕过了 `ExtChokepointContractTest`（ADR-0004）守着的咽喉，所以是隔离缺陷；
3. **保留原设计决定**：仍然**不挂权限串、不新建菜单/权限行**（否则会因缺一行 `sys_menu` 而 403，把「功能坏了」和「没有待办」混起来），并写明**角色闸与权限串是两件事**（角色闸答「你是不是内部的人」，权限串答「这个菜单有没有授权」；上游 admin 的 `*:*:*` 答得了后者、答不了前者）。
4. **与 `StaffGrantRules.INTERNAL_ROLE_KEYS` 对齐**：写明这三个角色键必须与那条常量逐字一致
   （`WorkbenchLoginGuardAspect` 用它判「能不能登工作台」），**注解引用不了 `List.of(...)` 常量**，
   所以一致性由契约测试守 —— 见第 10 节；
5. 另外新增一段 ★★ 记录 `SaMode.OR` 不能省（防后人照字面改回去）。

### 2.2 `.../sys/home/service/HomeCounterService.java`（只改注释，逻辑一行未动）

```diff
- * <p>★ 权限：登录即可调（101 / 102 / 外部都行，见 {@code HomeController}）。数字不带任何
- * 患者信息，外部角色看到的是同样的五个数，前端不给他渲染入口。
+ * <p>★ 权限：<b>内部角色闸</b>（101 {@code lqg_admin} / 102 {@code lqg_internal}），外部
+ * 103 {@code lqg_external} 一律 403 —— 见 {@code HomeController} 的类注释。
+ * <b>不是「登录即可调」</b>：小程序 token 也算登录过，只挂登录门外部就读得到
+ * （D7 r1 L3 的 S1）。数字本身不带患者信息，但 {@code /recent} 带跨单位送检单号与
+ * 内外部标记，所以这两个读口都不对外部开放。
```

### 2.3 `.../src/test/java/org/dromara/lqg/sys/home/HomeCounterContractTest.java`（★ 必要改动，见 WARN-2）

第 ⑥ 条**原本逐字钉住的正是这条缺陷**：`assertTrue(hasAnnotation(method, SaCheckLogin.class), "必须挂 @SaCheckLogin")`，DisplayName 叫「两个端点只挂 @SaCheckLogin」。若不动它，`-Dtest='…,*Home*Test'` 必然红，而且留下一个「下次重构把漏洞改回来也照样绿」的陷阱。

改法（**不是删断言，是把断言换成更强的**）：

- `@SaCheckLogin` 断言 → `@SaCheckRole` 必须存在；
- **新增** `role.mode() == SaMode.OR` 断言（把第 4 节的坑钉死）；
- 角色集合必须**恰等于** `StaffGrantRules.INTERNAL_ROLE_KEYS`（不逐字钉字面量 —— 一致性靠测试守，见第 10 节）；
- 任一角色都不得含 `external`；
- **保留**「不许挂 `@SaCheckPermission`」（原票「不因缺菜单行而 403」的设计决定不丢）；
- GET 路径断言不变。

> 未改票面（`doc/tickets/SYS-HOME-001/prompt.md` 的 front-matter 一字未动）；未动 `ExtChokepointContractTest`（AUTH-EXT-00x 的 accept 会对它做逐字节 `cmp`）。

---

## 3. 修复前 / 后逐身份请求对照表

命令（每个格子）：`bash doc/verify/api.sh --as <身份> --bizcode GET <端点>`（`--bizcode` 取**业务码**，若依把 403/500 包在响应体里）。
基线在**改代码之前**测得；每次切换身份/重打包/重灌 seed 后都 `rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*`（issue #286）。

| 身份 | 角色（实测 `/system/user/getInfo`） | 端点 | 修复前业务码 | 修复后业务码 |
|---|---|---|---|---|
| `admin`（lqgadmin，pc 密码登录，101） | `["lqg_admin"]` | `/lqg/home/todo` | 200 | **200** ✅ |
| `admin` | `["lqg_admin"]` | `/lqg/home/recent` | 200 | **200** ✅ |
| `staff`（13800000001，mp，102） | `["lqg_internal"]` | `/lqg/home/todo` | 200 | **200** ✅ |
| `staff` | `["lqg_internal"]` | `/lqg/home/recent` | 200 | **200** ✅ |
| `superadmin`（**上游超管 `admin`，user_id=1**，pc 密码登录） | `["superadmin"]` | `/lqg/home/todo` | 200 | **200** ✅（见第 10 节） |
| `superadmin` | `["superadmin"]` | `/lqg/home/recent` | 200 | **200** ✅ |
| `extA`（13800000011） | `["lqg_external"]` | `/lqg/home/todo` | 200 | **403** ✅ |
| `extA` | | `/lqg/home/recent` | 200 | **403** ✅ |
| `extB`（13800000012） | `lqg_external` | `/lqg/home/todo` | 200 | **403** ✅ |
| `extB` | | `/lqg/home/recent` | 200 | **403** ✅ |
| `extC`（13800000013，可见集合仅 {1005}） | `lqg_external` | `/lqg/home/todo` | 200 | **403** ✅ |
| `extC` | | `/lqg/home/recent` | 200（9 行跨单位） | **403** ✅ |
| `extE`（13800000015，未核验） | `lqg_external` | `/lqg/home/todo` | 200 | **403** ✅ |
| `extE` | | `/lqg/home/recent` | 200 | **403** ✅ |
| `extF`（13800000016，自填待核验） | `lqg_external` | `/lqg/home/todo` | 200 | **403** ✅ |
| `extF` | | `/lqg/home/recent` | 200 | **403** ✅ |
| `anon`（无 token） | — | `/lqg/home/todo` | 401 | **401**（认证失败，无法访问系统资源） |

修复后 externals 的响应体（**不含任何数据**）：

```json
{"code":403,"msg":"没有访问权限，请联系管理员授权","data":null}
```

修复后 admin 的载荷仍是契约形状（与修复前逐字一致）：

```json
{"code":200,"data":{"pendingSamples":2,"pendingEmbeds":1,"cryoOverdue":2,"pendingExtUsers":2,"renderFailed":0}}
{"code":200,"n":9}   // /recent 仍是 9 行（对 admin/staff）
```

### pc 密码登录护栏对照（必须保留、未被弄坏）

`POST /auth/login`（`clientid=<pc>`, `grantType=password`）：

| 账号 | 修复后 |
|---|---|
| `lqgadmin` / `admin123` | `{"code":200,"msg":"操作成功","hasToken":true}` |
| `wx_13800000012`（extB，**故意带可用口令**）/ `admin123` | `{"code":500,"msg":"该账号无权登录工作台，请使用小程序登录","hasToken":false}` |

→ 说明我们只收紧了工作台首页这两个读口的**角色闸**，没有动 AUTH-STAFF-001 §2.2 那条登录护栏。

---

## 4. ★ 返工中发现的坑（核心，必须记账）：`@SaCheckRole` 默认 `SaMode.AND`

**照字面写 `@SaCheckRole({"lqg_admin", "lqg_internal"})` 的结果是「必须同时具备两个角色」**，不是「任意一个」。

依据（本机依赖，实测）：`sa-token-core-1.45.0.jar` 的 `cn.dev33.satoken.annotation.SaCheckRole`：

```java
/** 验证模式：AND | OR，默认AND */
SaMode mode() default SaMode.AND;
```

第一版按字面改完、重打包重启后的**实测**（14 个格子全 403）：

```
admin  /lqg/home/todo     403	没有访问权限，请联系管理员授权
admin  /lqg/home/recent   403	没有访问权限，请联系管理员授权
staff  /lqg/home/todo     403	没有访问权限，请联系管理员授权
staff  /lqg/home/recent   403	没有访问权限，请联系管理员授权
extA…extF                 403	（同上）
```

原因：dev seed（`doc/verify/seed/01-accounts.sql`）里 `lqgadmin` 只被授了 **101**、内部人员只有 **102**，`SaMode.AND` 下**两边都不满足** → 工作台首页（本票主场景）被打红。

**最终修法**：显式 `mode = SaMode.OR`：

```java
@SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
```

并把它钉进契约测试（第 2.3 节）。**`mode = SaMode.OR` 是相对返工单字面指令的偏离，理由是实测证据：字面写法无法满足返工单自己写死的验收（admin 200 + staff 200）。** 角色集合后经同单收尾扩为 `INTERNAL_ROLE_KEYS` 全量（加 `superadmin`，见第 10 节），仍不放行任何外部角色。

---

## 5. 票面 accept 结果

命令（逐字按返工单）：

```bash
export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
python3 doc/waves/tools/accept-run.py --ticket SYS-HOME-001 --run --json .tmp/fix-syshome.json --logdir .tmp/fix-syshome-logs
```

```
[run] 2 条 accept，单条超时 900s
  ✓ SYS-HOME-001 acc1 [DATA] 五个数与库里独立数的一致并钉在 seed 上…（6.8s）
  ✗ SYS-HOME-001 acc2 [API] 前端构建是本次产物；首页五张卡片接的是接口…→ exit 1 (10.5s)
[run] 通过 1/2
[run] ⛔ 1 条未登记的红（= 产品断言不成立）：SYS-HOME-001 acc2
```

### 5.1 acc1 = 绿（`exit 0`，归一化仅 NF1）

**最终 build（含第 10 节的三角色对齐）复跑一次，结果不变**：`✓ acc1 … (6.0s)`、`✗ acc2 … exit 1 (10.4s)`，acc2 红因仍是同一条起点守卫（`.tmp/fix-syshome-logs-final/`、`.tmp/fix-syshome-final.json`）。即角色集扩成 `INTERNAL_ROLE_KEYS` 全量后，acc1（全程 `--as staff`）仍绿。

acc1 自带「短暂停 gotenberg 造真失败」，跑完已自愈：`lqg-dev-gotenberg Up (healthy)`、`/health → 200`。日志（`.tmp/fix-syshome-logs/SYS-HOME-001-acc1.sh.log`）末段逐断言 `true`：

```
true
2|1|2
true
true
true
1|2
true
1
true
true
```

### 5.2 acc2 = **工具红（起点守卫），不是产品断言红** —— 需 D7 门在干净树上复跑

失败点**不在票面断言**，而在 acc2 第二个半段 `bash doc/waves/regression/D7/mutation-assert.sh --verify-only --hotspot H2` 的**起点守卫**：

```
════ mutation-assert：scope=[H2] mode=verify-only ════
[error] 起点：git diff -- code/ 里出现了 pages.json 以外的文件：code/…/sys/home/controller/HomeController.java
code/…/sys/home/service/HomeCounterService.java
code/…/sys/home/test/java/…/HomeCounterContractTest.java
```

`mutation-assert.sh:157` 的 `tree_ok()` 要求 `git diff -- code/` **只允许** `code/miniapp/src/pages.json`（mutation 的还原需要干净起点）。**返工单的树按定义就是脏的**（修复本身就在 `code/` 里），所以 H2 探针根本没跑起来 —— 这是 harness 前提与「返工」场景的冲突，不是判据不成立。

**重要**：acc2 的**产品侧断言全部通过**（`pnpm build:prod` 成功、5 处 `grep` 全过），红只发生在 mutation 守卫。

为不留「H2 没验」的空档，我在修好的后端上**手工跑了 H2 探针本体**（等同 mutation-assert 的 preflight）：

```bash
# 起 plus-ui dev（8093，PID 21083）后：
node doc/waves/regression/D7/accept-strengthened/probe.mjs H2
```

```
PASS  H2:工作台登录 lqgadmin  :: http://127.0.0.1:8093/index
PASS  H2:真 DOM 渲染出五张待办卡片  :: ["待核验样本","待核验石蜡包埋送样","-80 超期批次","待核验外部用户","文档渲染失败"]
PASS  H2:抓到同一次 /lqg/home/todo 的响应 :: {"pendingSamples":2,"pendingEmbeds":1,"cryoOverdue":2,"pendingExtUsers":2,"renderFailed":0}
PASS  H2:★ 卡片「样本」的 DOM 数字 == todo.pendingSamples  :: {"dom":"2","api":"2"}
PASS  H2:★ 卡片「石蜡」的 DOM 数字 == todo.pendingEmbeds  :: {"dom":"1","api":"1"}
PASS  H2:★ 卡片「超期」的 DOM 数字 == todo.cryoOverdue     :: {"dom":"2","api":"2"}
PASS  H2:★ 卡片「外部」的 DOM 数字 == todo.pendingExtUsers :: {"dom":"2","api":"2"}
PASS  H2:★ 卡片「渲染」的 DOM 数字 == todo.renderFailed    :: {"dom":"0","api":"0"}

==== H2: 8/8 PASS → GREEN（判据成立） ====
```

→ **修复没有把工作台首页打坏（admin 仍能登录并拿到 5 个数字），DOM 数字与接口返回值逐格一致。**

收尾：8093 已**按 PID**（`kill 21083`）关停；probe 覆写的 `doc/waves/regression/D7/accept-strengthened/observations/H2.json` 已 `git checkout` 还原（sha `7280c2999c60ac23`，与运行前一致）。

**给 D7 门的动作**：acc2 必须在**已提交 / 干净树**上复跑（那时 `tree_ok` 通过，H2 会像上面一样 GREEN）→ 预期 **2/2**。若门在脏树上复跑，红因就是本条，不是产品缺陷。

---

## 6. 单测（ext 域不变量必须仍绿）

命令（在 `code/RuoYi-Vue-Plus` 下，`-Dmaven.repo.local` / `-Duser.home` 指向仓库根，与 `qa-up.sh` 同源）：

```bash
mvn -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='ExtChokepointContractTest,*Home*Test' \
    -Dsurefire.failIfNoSpecifiedTests=true -s ../../.mvn-settings.xml \
    -Dmaven.repo.local="$PWD/../../.m2repo" -Duser.home="$PWD/../../.buildhome"
```

```
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in org.dromara.lqg.ext.ExtChokepointContractTest
[INFO] Running org.dromara.lqg.sys.home.HomeCounterContractTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in org.dromara.lqg.sys.home.HomeCounterContractTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **`ExtChokepointContractTest` 4/4 绿**（I1–I4 不变量未被破坏；该文件一字未改）；
- `HomeCounterContractTest` **6/6 绿**（含改写后的第 ⑥ 条：内部角色闸 + `SaMode.OR` + **角色集恰等于 `StaffGrantRules.INTERNAL_ROLE_KEYS`**）。

---

## 7. 越界 / WARN

- **WARN-1（★ 已按调度器裁定消掉，见第 10 节）**：角色白名单初版只有 `{lqg_admin, lqg_internal}`，不含上游 `superadmin`。AUTH-STAFF-001 §2.2 明确「不含 `lqg_internal` / `lqg_admin` / 上游 `superadmin` → 登录失败」，`StaffGrantRules.INTERNAL_ROLE_KEYS` 也把 `superadmin` 算内部 —— 即上游超管 **能登进工作台**，却会在首页 **403**（首页是登录后第一屏 = 坏页）。**已把注解角色集扩成与 `INTERNAL_ROLE_KEYS` 一致，并加实测（`roles:["superadmin"]` 的纯超管 token → 200）与防漂移测试。**
- **WARN-2（必要改动，非越界）**：改了 `HomeCounterContractTest` 第 ⑥ 条。理由见第 2.3 节：它原本逐字钉住 S1 缺陷本身，不改则单测必红且留回退陷阱。文件在 SYS-HOME-001 的 `touches` 内，且没有任何 accept 对它做 `cmp`/字面量断言（已 grep 确认：只有 AUTH-EXT-00x 对 `ExtChokepointContractTest` 做 `cmp`，我没有碰它）。
- **WARN-3（非本单，仅记录，未改）**：`ocr/controller/OcrStatusController.java` 的类注释写「实测工作台 token 的 `rolePermission` 里没有 `lqg_admin`，用角色判会把 admin 挡在 403」。**本环境实测该说法对 seed 的 `lqgadmin` 不成立**：`--as admin GET /system/user/getInfo` → `roles:["lqg_admin"]`（pc 密码登录，`SysLoginService:161` 灌 `permissionService.getRolePermission(userId)`）。该注释很可能是拿 **user_id=1 的上游超管**（`SysPermissionServiceImpl:37` 对超管只回 `superadmin`）测出来的 —— 两者不是同一个账号。该文件**不在本单范围，未改**；但 D7 门/后续票据若依赖那条注释做鉴权选型，会走错（正是 WARN-1 的同源问题）。
- **未越界声明**：`ExtChokepoint` 体系、`/mp/**`、`doc/api-contract.md`、票面 front-matter、`doc/waves/state.json`、`_manifest.json`、`code/miniapp/src/pages.json` 全部未动。
- 实验残留：无。probe 覆写的 `observations/H2.json` 已还原（sha `7280c2999c60ac23`，与运行前一致）。
  ★ 轮次状态：第 1 轮（S1 修复 + 报告）已由调度器提交为 `a19763f`（含 `HomeCounterService` 的注释改动）；
  第 10 节的收尾改动（`HomeController` 三角色 + `HomeCounterContractTest` 第 ⑥ 条 + 本报告）在本报告写成时
  仍是工作区未提交改动 —— 因此此刻 `git diff -- code/` = `HomeController.java` + `HomeCounterContractTest.java`（2 个文件）。

---

## 8. 给 D7 门复跑的说明

1. **复跑 accept 必须在干净树上**（本单改动已提交后），否则 acc2 会复现第 5.2 节的 `起点守卫` 红。预期 `SYS-HOME-001` **2/2**。
2. **L3 复测点**（判「是否真堵住」）：
   ```bash
   export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
   rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*     # 切换身份前必清（issue #286）
   for id in admin staff extA extB extC extE extF; do
     for ep in /lqg/home/todo /lqg/home/recent; do
       printf '%-6s %-18s ' "$id" "$ep"
       bash doc/verify/api.sh --as "$id" --bizcode GET "$ep" | head -1
     done
   done
   ```
   期望：`admin`/`staff` 6 个格子 200，`ext*` 10 个格子 403（`data:null`），`anon` 401。
3. **pc 登录对照不能丢**：`extB`（`wx_13800000012`/`admin123`，clientid=pc）→ `code:500`「该账号无权登录工作台，请使用小程序登录」。
4. **别照字面改回 `@SaCheckRole({...})`**：漏 `mode = SaMode.OR` 会让 admin + staff 一起 403（第 4 节）；角色集也**别删 `superadmin`**（会打红上游超管的首页，第 10 节）。契约测试第 ⑥ 条把这两条都钉住了（`mode == OR` + 角色集恰等于 `INTERNAL_ROLE_KEYS`）。
5. **越权面复核**：`/lqg/home/*` 之外，本次没有改动任何其它 `/lqg/**` 或 `/mp/**` 的鉴权 —— L3 的其余结论（外部下载带 `audience=internal`、桶匿名可读等）不受本修复影响，仍按各自 issue 处理。
6. **superadmin 复测**（可选，零改库）：dev 库里 `user_id=1 / admin` 是**纯 `superadmin`**（口令 `admin123`），把 `LQG_ADMIN_USER` / `LQG_ADMIN_PASSWORD` 换成它即可复现 —— 注意 `api.sh` 会 `source` env 文件、**导出的变量会被文件覆盖**，所以要改文件而不是只 export（第 10.3 节踩过）。

---

## 9. 环境收尾

- 后端 8094：两轮验证各起停一次，收尾均 `qa-up.sh --down --backend-port 8094 --no-web --no-mp`
  （**按 PID**：第一轮 pid 15163、第二轮 pid 35386、最终 accept 复跑 pid 47410）；8094 / 8093 / 9202 均已释放。
- 8093（plus-ui dev，手工起的 H2 探针用）：已按 PID `kill 21083` 关停，端口已释放。
- 严禁项遵守：**未**执行 `pkill -f 'ruoyi-admin.jar'`；8080/5432/6379 未触碰（收尾复核 8080 仍空闲）。
- `lqg-dev-gotenberg`：acc1 自行 stop/start 后已回到 `Up (healthy)`，`/health → 200`，未停它。
- 数据库：本单**零写入**（第 10.3 节）；QA 期间为核对身份/角色只用 `SELECT`。

---

## 10. 与 `INTERNAL_ROLE_KEYS` 对齐（WARN-1 收尾 · 同返工单）

调度器核了 WARN-1 并确认成立：`StaffGrantRules.INTERNAL_ROLE_KEYS` 是项目「内部角色」的唯一口径来源，
`WorkbenchLoginGuardAspect` 正是用它判「能不能登工作台」。初版白名单少了 `superadmin` → 「超管能登工作台、却打不开首页」的不一致。本节是消掉该不一致的改动与实测。

### 10.1 改了什么

1. **`HomeController` 注解角色集扩成与 `INTERNAL_ROLE_KEYS` 一致**（`mode = SaMode.OR` 不变）：

   ```java
   @SaCheckRole(value = {"lqg_admin", "lqg_internal", "superadmin"}, mode = SaMode.OR)
   ```

   ★ 注解**不能引用 `List.of(...)` 常量**（不是编译期常量），所以只能硬写这三个字符串 → **防漂移靠测试**（下一条）。

2. **类注释新增一段 ★★**：写明这三个角色键必须与 `StaffGrantRules.INTERNAL_ROLE_KEYS` 逐字一致、
   口径来源是 `WorkbenchLoginGuardAspect`、少 `superadmin` 的后果（超管坏首页）、
   以及「注解引用不了常量，所以一致性由 `HomeCounterContractTest` 第 ⑥ 条守」。

3. **契约测试第 ⑥ 条从「钉字面量」改成「守一致性」**（不再写死 `{lqg_admin, lqg_internal}`）：

   ```java
   assertEquals(Set.copyOf(StaffGrantRules.INTERNAL_ROLE_KEYS), Set.of(role.value()), …);
   assertEquals(SaMode.OR, role.mode(), …);
   assertFalse(AnnotatedElementUtils.hasAnnotation(method, SaCheckPermission.class), …);
   ```

   → 将来谁改了 `INTERNAL_ROLE_KEYS`（例如再加一个内部角色），这条测试**立刻红**；`mode` 与「不许挂权限串」也一并钉住。

### 10.2 逐身份实测（对齐后，重打包重启 + reseed 后）

| 身份 | 实测角色（`/system/user/getInfo`） | `/lqg/home/todo` | `/lqg/home/recent` |
|---|---|---|---|
| `admin`（lqgadmin 9000000100，pc 密码登录） | `["lqg_admin"]` | **200** ✅ | **200** ✅ |
| `staff`（13800000001，mp） | `["lqg_internal"]` | **200** ✅ | **200** ✅ |
| **`superadmin`（`admin` user_id=1，pc 密码登录）** | **`["superadmin"]`** | **200** ✅ | **200** ✅ |
| `extA` | `["lqg_external"]` | **403** ✅ | **403** ✅ |
| `extB` | `["lqg_external"]` | **403** ✅ | **403** ✅ |
| `extC` | `["lqg_external"]` | **403** ✅ | **403** ✅ |
| `extE` | `["lqg_external"]` | **403** ✅ | **403** ✅ |
| `extF` | `["lqg_external"]` | **403** ✅ | **403** ✅ |

- admin / staff 未被打回 403（**8 个身份 16 个格子**：3 个内部身份 6 格 200、5 个外部身份 10 格 403）；
- superadmin 的载荷也是契约形状：`{"pendingSamples":2,"pendingEmbeds":1,"cryoOverdue":2,"pendingExtUsers":2,"renderFailed":0}`；
- 收尾把身份切回 `lqgadmin` 再测一次：`roles:["lqg_admin"]`、两端点 200、`extC` 仍 403（确认没有把身份/缓存留在超管态）。

### 10.3 ★ superadmin 实测：**没有改库**（比返工单建议的做法更干净）

返工单建议「临时给 `lqgadmin` 加 `superadmin` 角色（或临时建一个），测完删掉」。核查 dev 库后发现**不需要动库**：

```sql
-- 只读核查
SELECT user_id,user_name,user_type,(password<>'') FROM sys_user WHERE user_id<=2;
--  1|admin|sys_user|t          ← 上游超管真实存在，且带口令
SELECT role_id,role_key FROM sys_role WHERE role_key='superadmin';
--  1|superadmin                ← 角色行本来就有
SELECT user_id,role_id FROM sys_user_role WHERE user_id=1;
--  1|1                         ← admin 本来就挂着 superadmin
```

于是直接用**真实的纯超管账号**登录（`user_id=1 / admin`，口令 = 上游 dev 默认 `admin123`）：

```
identity: {"code":200,"userId":1,"userName":"admin","roles":["superadmin"]}
/lqg/home/todo           200	操作成功
/lqg/home/recent         200	操作成功
```

这比「借用 lqgadmin 加角色」更强：那是**纯 `superadmin`**（不含 `lqg_admin` / `lqg_internal`），
唯一角色就是白名单里新增的那个 → 200 只可能来自 `superadmin` 这一项。
**因此本次对数据库零写入**（只有 `SELECT`），没有临时角色需要还原，也不存在残留风险。

> 副作用记录：`sys_user_role` 里 `9000000100` 始终只有 `101`（对齐前后各查一次，一致）；
> 库里另有 `user_id=1`、`3 test`、`4 test1`（上游默认行）与 `wx_13800000099`（**D7 r1 L3 那轮**为「外部新号」造的账号，非本轮产生）—— 均非本次写入。

### 10.4 坑：`api.sh` 会 `source` env 文件，导出的 `LQG_ADMIN_USER` 会被覆盖

第一次尝试用 `export LQG_ADMIN_USER=admin` 直接跑，`api.sh` 的
`set -a && . "${ENV_FILE}"` 又把文件里的 `LQG_ADMIN_USER=lqgadmin` 灌了回来 → 实际仍是 lqgadmin
（`getInfo` 回 `userId:9000000100`）。正确做法：**改文件**（本轮用 `.tmp/qa-env/8094/verify-superadmin.env`，
指向 8094、只替换这两行，跑完删除），并 `rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*` 防止
admin 身份的 token 缓存串用（缓存键只含 `<身份>-<BASE>`，不含用户名）。

### 10.5 单测（对齐后重跑）

```
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running org.dromara.lqg.sys.home.HomeCounterContractTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

第 ⑥ 条现在断的是「角色集恰等于 `INTERNAL_ROLE_KEYS`」——它绿即证明注解与常量当前一致。

### 10.6 同单性质

本节是对**同一条 S1 返工单**的收尾（消掉本次引入的角色集不一致），不是新 ticket：不新增缺陷、不改票面、
不改 `staff` 授权逻辑与 `WorkbenchLoginGuardAspect`；只动 `HomeController` 注解/注释与
`HomeCounterContractTest` 第 ⑥ 条，均在 SYS-HOME-001 的 `touches` 内。
