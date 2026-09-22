# D7 返工 r1 · S1 修复报告：外部账号（mp token）可读工作台首页两个端点

- 任务：D7 r1 L3 抓到的一条 **S1 产品缺陷**（返工单，非新 ticket）
- 缺陷来源：`doc/waves/qa/D7-r1-L3.json` 的 issues[0]（L3 第 7 条额外条，把 issue #270 从「理论上可读」升级为「实测可读且带跨单位明细」），由 owner 定级为 **S1**
- 分支：`task/D7`（已确认，未切分支 / 未 push / 未合分支 / 未动 `state.json` 与 `_manifest.json`）
- 环境：`qa-up.sh --backend-port 8094 --no-web --no-mp`；`LQG_VERIFY_ENV_FILE=.tmp/qa-env/8094/verify.env`
- 结论：**已堵住**。admin(101) / staff(102) 两个端点 200；extA/extB/extC/extE/extF 两个端点一律 403（`data:null`）；pc 密码登录护栏未受影响（extB → 500）。
- ★ 返工中发现一个**必须记账的坑**：照票面/返工单字面写的 `@SaCheckRole({"lqg_admin","lqg_internal"})` **会让 admin 和 staff 一起 403**（Sa-Token 默认 `SaMode.AND`）。见第 5 节。

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
+     * <p>内部角色闸（{@code mode = SaMode.OR}）：101 {@code lqg_admin} <b>或</b> 102
+     * {@code lqg_internal} 放行，外部 403。
      */
-    @SaCheckLogin
+    @SaCheckRole(value = {"lqg_admin", "lqg_internal"}, mode = SaMode.OR)
     @GetMapping("/todo")
     public R<HomeTodoVo> todo() { … }

     /**
      * 最近提交 10 条（送检时间倒序；不含软删的样本）。
      */
-    @SaCheckLogin
+    @SaCheckRole(value = {"lqg_admin", "lqg_internal"}, mode = SaMode.OR)
     @GetMapping("/recent")
     public R<List<HomeRecentVo>> recent() { … }
```

类注释：删掉把「登录即可调」当依据的那段，改成三段**为什么**：

1. **鉴权是「内部角色闸」，不是「登录门」** —— 101 / 102，103 `lqg_external` 一律 403；
2. **为什么不能只挂 `@SaCheckLogin`** —— mp token 也是「已登录」；AUTH-STAFF-001 §2.2 那条护栏只拦 pc 密码登录，mp token 不经过它，所以「登录门 ⇒ 内部」前提不成立；这两个端点绕过了 `ExtChokepointContractTest`（ADR-0004）守着的咽喉，所以是隔离缺陷；
3. **保留原设计决定**：仍然**不挂权限串、不新建菜单/权限行**（否则会因缺一行 `sys_menu` 而 403，把「功能坏了」和「没有待办」混起来），并写明**角色闸与权限串是两件事**（角色闸答「你是不是内部的人」，权限串答「这个菜单有没有授权」；上游 admin 的 `*:*:*` 答得了后者、答不了前者）。
4. 另外新增一段 ★★ 记录 `SaMode.OR` 不能省（防后人照字面改回去）。

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
- **新增** `role.mode() == SaMode.OR` 断言（把第 5 节的坑钉死）；
- 角色集合必须**恰好** `{lqg_admin, lqg_internal}`（只放 internal 会打红管理员）；
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
@SaCheckRole(value = {"lqg_admin", "lqg_internal"}, mode = SaMode.OR)
```

并把它钉进契约测试（第 2.3 节）。**这是相对返工单字面指令的唯一偏离，理由是实测证据：字面写法无法满足返工单自己写死的验收（admin 200 + staff 200）。** 角色集合仍严格是 `{lqg_admin, lqg_internal}`，未加也未减。

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
- `HomeCounterContractTest` **6/6 绿**（含改写后的第 ⑥ 条内部角色闸 + `SaMode.OR` 断言）。

---

## 7. 越界 / WARN

- **WARN-1（口径，需 owner/D7 门裁决）**：角色白名单**只有** `{lqg_admin, lqg_internal}`，不含上游 `superadmin`。而 AUTH-STAFF-001 §2.2 明确「不含 `lqg_internal` / `lqg_admin` / 上游 `superadmin` → 登录失败」，`StaffGrantRules.INTERNAL_ROLE_KEYS` 也把 `superadmin` 算内部 —— 即上游超管 **能登进工作台**，但按本修复访问这两个端点会 **403**（seed 的 `lqgadmin` 是 user_id 9000000100 / 101，不是 superadmin，所以本环境测不出来）。返工单字面点名两个角色，我未擅自扩白名单；**若生产会有上游 superadmin 用工作台，建议由 owner 决定是否补 `superadmin`**（追加一个枚举值即可，不影响已测行为）。
- **WARN-2（必要改动，非越界）**：改了 `HomeCounterContractTest` 第 ⑥ 条。理由见第 2.3 节：它原本逐字钉住 S1 缺陷本身，不改则单测必红且留回退陷阱。文件在 SYS-HOME-001 的 `touches` 内，且没有任何 accept 对它做 `cmp`/字面量断言（已 grep 确认：只有 AUTH-EXT-00x 对 `ExtChokepointContractTest` 做 `cmp`，我没有碰它）。
- **WARN-3（非本单，仅记录，未改）**：`ocr/controller/OcrStatusController.java` 的类注释写「实测工作台 token 的 `rolePermission` 里没有 `lqg_admin`，用角色判会把 admin 挡在 403」。**本环境实测该说法对 seed 的 `lqgadmin` 不成立**：`--as admin GET /system/user/getInfo` → `roles:["lqg_admin"]`（pc 密码登录，`SysLoginService:161` 灌 `permissionService.getRolePermission(userId)`）。该注释很可能是拿 **user_id=1 的上游超管**（`SysPermissionServiceImpl:37` 对超管只回 `superadmin`）测出来的 —— 两者不是同一个账号。该文件**不在本单范围，未改**；但 D7 门/后续票据若依赖那条注释做鉴权选型，会走错（正是 WARN-1 的同源问题）。
- **未越界声明**：`ExtChokepoint` 体系、`/mp/**`、`doc/api-contract.md`、票面 front-matter、`doc/waves/state.json`、`_manifest.json`、`code/miniapp/src/pages.json` 全部未动。
- 实验残留：无。`git diff -- code/` 只有上面三个文件；probe 覆写的 `observations/H2.json` 已还原。

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
4. **别照字面改回 `@SaCheckRole({...})`**：漏 `mode = SaMode.OR` 会让 admin + staff 一起 403（第 4 节）。契约测试第 ⑥ 条已把这个坑钉住。
5. **越权面复核**：`/lqg/home/*` 之外，本次没有改动任何其它 `/lqg/**` 或 `/mp/**` 的鉴权 —— L3 的其余结论（外部下载带 `audience=internal`、桶匿名可读等）不受本修复影响，仍按各自 issue 处理。

---

## 9. 环境收尾

- 后端 8094：本次收尾 `qa-up.sh --down --backend-port 8094 --no-web --no-mp`（**按 PID**）。
- 8093（plus-ui dev，手工起的 H2 探针用）：已按 PID `kill 21083` 关停，端口已释放。
- 严禁项遵守：**未**执行 `pkill -f 'ruoyi-admin.jar'`；8080/5432/6379 未触碰。
- `lqg-dev-gotenberg`：acc1 自行 stop/start 后已回到 `Up (healthy)`，`/health → 200`，未停它。
