# D1 r2 · L2 收尾（L2-r2-fin）notes

范围：**只做最后 4 步**。不重写别人的脚本（仅加稳健等待/选择器）、产品代码 0 改动、不读
PNG、不读 `doc/waves/reports/**` 与他人 qa json。

产物两条路：dev 8082（plus-ui dev，代理 8081）、build 8083（静态服务 `code/plus-ui/dist` 的
`/dev-api` → 8081）。

---

## 0. harness 加固（我改了哪几处）

前一版在 dev 抛的两类异常，独立探针
（`.tmp/qa-r2-l2-fin/probe.mjs` / `probe2.mjs`）查明**都不是产品问题**，是「残留 overlay /
下拉 / 弹窗过渡吞掉下一次点击」与「固定 sleep 读到上一状态的文案」。改动**只在等待与选择器**，
断言、产品代码未动（`grep clickRowAndWait` = 10 处含定义）：

1. 新增 `waitDialogText(re, timeout)`：轮询到目标文案出现再返回。
   - 用于 WEB-07（`13800000011` → 升级文案）与 **WEB-09**（`13800000091` → 预建文案），
     替换原来的 `waitForTimeout(2000)` + 单次读文本。**这是 WEB-09 唯一改动点**。
2. 新增 `clickRowAndWait(rowSel, btnSel, dialogSel, pagePath, waitText)`：点行内按钮 →
   `waitForSelector` 等弹窗；点击被吞就重试（≤4 次），连续失败则 `hardOpen` 整页重载清状态。
   替换了原来 5 处「裸 `page.click` + 裸 `waitForSelector`」：
   - `/auth/staff` 李工三个行操作（改角色 / 重置密码 / 撤销，`button >> nth=0/1/2`；
     这三个按钮是 **icon-only `is-link`**，`innerText` 为空，只能按序号定位）
   - `/auth/extuser` 吴同学「核验」×2（自填分支 + 驳回分支前重开）、周医生「核验」、
     王医生「改归组」
   - 失败重试会打 `[harness] clickRowAndWait 重试 attempt=N` 到日志，便于区分是 harness 抖动
     还是产品真的打不开。
4. `L2-r2-mp-h5-indep.mjs` 的 `gotoMe()`：改成先断 `.uni-tabbar__item:has-text("我的")`
   的**可见性**，不可见（非 tab 页 `/pages/cryo/form` 会把 tabbar 留在 DOM 但隐藏）就
   `goto(BASE + '/#/pages/index/index')` 回首页 tab 页再点。
   （第一版改用 `count()===0` 判断，**不够**——隐藏节点 count 仍 >0，仍 30s 超时；已修正。）
5. `L2-r2-mp-h5-indep.mjs` 的 MP-33 取值选择器：`.profile__input` 在 uni-app H5 里是
   **`<uni-input>` 包装元素**（内层才是 `<input class="uni-input-input">`），原来
   `.map(i => i.value)` 恒得 `undefined`（JSON 里显示 `[null]`），**不是产品没回填**。
   探针实证内层 `input.value === '王医生'` → 改成读内层 input 的 value（**只改选择器，断言没动**）。
6. 没有改任何 `check()` 的期望值，也没有因为断言失败去放宽断言。

## 1. 两条产物跑完的结果

### dev 8082（`LQG_WEB_BASE=http://127.0.0.1:8082 LQG_SHOT_TAG=web-dev`）

`.tmp/qa-r2-l2-fin/web-dev.log` —— **39/40 通过，脚本未抛异常，`grep clickRowAndWait 重试` = 0**
（即加固后的重试一次都没触发，说明两处异常确实是偶发的状态残留，不是稳定复现）。

关键几条（原文）：

```
PASS  WEB-04 菜单导航无白屏步（r1 S1 独立复验） — bad=0/6
PASS  WEB-09 ① 库里没有的手机号 → 弹窗提示「将预建一个内部账号」 — ["该手机号还没有账号，将预建一个内部账号"]
PASS  WEB-17 ② 左栏三个 seed 单位 + 组别数 + 状态（A 医院 2 启用 / B 大学 1 启用 / 已停用单位 0 停用）
      — rows=[["A 医院","2","启用",""],["B 大学","1","启用",""],["已停用单位","0","停用",""]]
PASS  WEB-22 ②「新增单位」弹窗：标题 + 单位名称（placeholder「全库唯一」）+ 备注
      — placeholder="请输入单位名称（全库唯一）"
PASS  WEB-23 ②「新增组别」弹窗：所属单位 = 当前选中的 B 大学 + placeholder「同一单位内不可重名」
      — placeholder="同一单位内不可重名"
FAIL  WEB-36 全流程没有未捕获的前端异常 — ["cancel"]
```

★ **WEB-23 的争议点其实已经不存在**：那句话**在 DOM 里**，是新增组别输入框的 **placeholder**
「同一单位内不可重名」（WEB-22 同理：「请输入单位名称（全库唯一）」）。前一版 build 日志里
`WEB-22/23 FAIL` 是**更旧的脚本版本**（那时断言读的是弹窗 innerText，placeholder 读不到）。
现版本脚本 + dev 8082 → 两条都 PASS。**WEB-23 判 PASS，不是 issue。**

### WEB-36 唯一的 FAIL：`pageerror: cancel`（S3 · debt · 真实产品侧小缺陷）

`doc/waves/.../web-dev.log` 第 20 行、紧跟 WEB-14 之后（脚本点了撤销确认框的「取消」）：

```
PASS  WEB-14 ① 撤销授权有二次确认 … "系统提示//撤销后该账号将降回外部人员并立即退出登录…"
[pageerror] cancel
PASS  WEB-15 ① 取消撤销后列表不变（李工仍在，两条内部账号） — rows=2
```

根因（产品代码，`code/plus-ui/src/views/lqg/auth/staff/index.vue:301-307`）：

```ts
const handleRevoke = async (row: StaffMemberVO) => {
  await proxy?.$modal.confirm(t('lqg.auth.staff.revokeConfirm', { name: row.name }));  // ← 无 try/catch
  await revokeStaff(row.userId);
  …
};
```

`$modal.confirm` = Element Plus `ElMessageBox.confirm`，用户点「取消」时它 **reject 字符串 `'cancel'`**；
`await` 无 catch → unhandled promise rejection → `pageerror`。**任何真实用户点一次「取消」都会触发。**

影响面：**纯控制台噪音，无用户可见故障** —— 弹窗正常关、列表不变（WEB-15 PASS）、没有错数据。
所以按产品影响定 **S3 · `debt`**（记账，不拦门），归属 **AUTH-STAFF-001**（内部人员授权）。
修法一行：`await …confirm(…).catch(() => null)` 或 try/catch（本轮不改产品代码）。

## 4. 反 stale 核验（本轮，不引用别人的结论）

`doc/verify/api.sh --fresh-module` 在本沙箱**跑不通**（见 issue）：`ps` 被沙箱禁
（`/bin/ps: Operation not permitted`）+ 回退分支用了 GNU 的 `date -d`（macOS 无）→ 真实 exit=1。
改用本目录既有的等价脚本 `bash doc/waves/regression/D1/L23-freshness.sh 8081`
（`.tmp/qa-r2-l2-fin/freshness.log`），**四条全 PASS**：

```
jar mtime : 2026-09-21 16:32:24
jar sha256: 2c10af1121e7f5d1feddf40a49fa6590…
PASS ① 模块源码没有比 jar 新的文件
PASS ② 8081 上有监听进程 pid=84839
PASS ③ 该进程持有这只 jar（fd 命中 2 次）
PASS ③' 进程打开的 jar 就是工作区这只：<WS>/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
PASS ④ 后端启动日志：.tmp/r2-backend.log（首条时间戳 2026-09-21 17:26:18，晚于 jar mtime）
```

即：**跑断言的 8081 = 工作区这只 jar，且进程启动（17:26:18）晚于 jar mtime（16:32:24），
ruoyi-lqg 源码也没有比 jar 新的文件** → 不是 stale 后端。

### build 8083（`LQG_WEB_BASE=http://127.0.0.1:8083 LQG_SHOT_TAG=web-build`）

`.tmp/qa-r2-l2-fin/web-build.log` —— **40/40 通过，exit 0**，同样 0 次 harness 重试：

```
PASS  WEB-04 菜单导航无白屏步（r1 S1 独立复验） — bad=0/6
PASS  WEB-09 ① … — ["该手机号还没有账号，将预建一个内部账号"]
PASS  WEB-22 ② … placeholder="请输入单位名称（全库唯一）"
PASS  WEB-23 ② … placeholder="同一单位内不可重名"
PASS  WEB-36 全流程没有未捕获的前端异常 — []
== L2-r2(web-plusui web-build) 40/40 通过 ==
```

★ 同一条 `cancel` 在 build 里表现为 `[console.error] cancel`（日志第 20 行）而非 `pageerror`：
Vue **dev 模式**会把 confirm 取消产生的未处理 reject 冒成页面级异常，生产 build 只落到
console。**底层漏 catch 是同一处**（见上），两种产物都复现，只是暴露形态不同。

### 小程序 H5（9200，`VITE_MOCK_LOGIN=1`）

```
LQG_H5_BASE=http://127.0.0.1:9200 LQG_SHOT_TAG=mp-dev       node …/L2-r2-mp-h5.mjs
  → == L2-r2(mp-h5 mp-dev) 26/26 通过 ==  exit 0   （与前一任实测 26/26 一致）
LQG_H5_BASE=http://127.0.0.1:9200 LQG_SHOT_TAG=mp-h5-indep  node …/L2-r2-mp-h5-indep.mjs
  → 首跑 12/13 + 脚本异常（见下），加固后重跑见 .tmp/qa-r2-l2-fin/mp-h5-indep2.log
```

**MP2 首跑异常（第 4 处 harness 加固，非产品问题）**：`FAIL 脚本异常 — page.click: Timeout
30000ms … waiting for locator('.uni-tabbar__item:has-text("我的")')`。
独立查明：uni-app H5 **只在 tab 页渲染 `.uni-tabbar`**，而 MP-12 会落到**非 tab 页**
`/pages/cryo/form`，紧接着的 `gotoMe()` 直接点「我的」→ 必然 30s 超时。
加固：`gotoMe()` 里先判断 tabbar 是否存在，不在就 `goto(BASE + '/#/pages/index/index')`
再点（**只动导航/等待，没动任何断言**）。

### 截图目录（全部在 r2 自己的目录下，**未覆盖 r1 的 `shots-L23/`**）

```
doc/waves/regression/D1/shots-L23-r2/web-dev/        （dev 8082，39/40 那一轮）
doc/waves/regression/D1/shots-L23-r2/web-build/      （build 8083，40/40）
doc/waves/regression/D1/shots-L23-r2/mp-dev/         （26/26）
doc/waves/regression/D1/shots-L23-r2/mp-h5-indep/    （加固后那一轮）
```

**未覆盖（如实记，不冒充）**：微信开发者工具（CLI 要写
`~/Library/Application Support/微信开发者工具/**` EPERM + 需人工扫码）与**真机**都没跑；
小程序侧一律用 **H5 dev server(9200) 等价路径**覆盖，结论只对 H5 等价路径成立。

### MP2 加固后结果（`.tmp/qa-r2-l2-fin/mp-h5-indep2.log`，跑到底不抛异常）：**36/38**

两条 FAIL **都是 harness 侧**，产品侧已独立实证无缺陷：

- `FAIL MP-33 … {"inputs":[null],"picks":["A 医院\n›","肝胆外科组\n›"]}`
  → 见 §0 第 5 条：读的是 `<uni-input>` 包装元素的 `.value`。独立探针
  （`.tmp/qa-r2-l2-fin/probe3.mjs`）实证 DOM：

  ```
  profileInputs: [{"tag":"UNI-INPUT","cls":"profile__input","value":null}]
  allInputs:     [{"cls":"uni-input-input","value":"王医生"}]
  picks:         ["A 医院\n›","肝胆外科组\n›"]
  ```

  **表单确实回填了 王医生 / A 医院 / 肝胆外科组** —— 断言意图成立，坏的是取值选择器，已修。
- `FAIL MP-31 单位与组别页：…「当前已核验：A 医院 · 肝胆外科组」`
  → 页面真实文案（探针 + 源码 `code/miniapp/src/pages/me/unit-group.vue:142` +
  `src/utils/ext-profile.ts:40 unitDisplay()`）只渲染**单位**：

  ```
  "当前状态","已核验","当前已核验：A 医院","姓名","单位","A 医院","›","组别","肝胆外科组","›"
  ```

  权威 `UI:mp.me.profile` 正文**根本没要求**这条「当前已核验」摘要行（只要求：姓名输入框 +
  单位选择器 + 组别选择器 / 保存后变待核验 / 顶部说明逐字 / 驳回显示原因），更没规定
  `单位 · 组别` 的串接格式。→ 断言**无权威支撑**，属 harness 过严；组别在下方「组别」选择器里
  明确可见（`肝胆外科组 ›`），信息没丢。**不改这条断言**（改了就是挪球门），按 S3 harness 记账。

## 2. WEB-09 裁定：**不成立（非产品缺陷）**

前一版 dev 日志 `FAIL WEB-09 … ["该手机号已登录过小程序，将把原账号升级为内部人员"]`。

**后端直判**（我跑的）：

```
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/staff/check?phone=13900001234'
{"code":200,"msg":"操作成功","data":{"exists":false,"external":false,"name":null}}
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/staff/check?phone=13800000001'   # seed 内部 · 李工
{"code":200,"msg":"操作成功","data":{"exists":true,"external":false,"name":"李工"}}
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/staff/check?phone=13800000091'   # 脚本实际用的空号
{"code":200,"msg":"操作成功","data":{"exists":false,"external":false,"name":null}}
```

**UI 实测（dev 8082，探针轮询时间线）**：

```
[5.6s]  phone=13800000011 tip@0.5s => 该手机号已登录过小程序，将把原账号升级为内部人员
[17.7s] phone=13800000091 tip@0.5s => 该手机号还没有账号，将预建一个内部账号
[29.9s] phone=13900001234 tip@0.5s => 该手机号还没有账号，将预建一个内部账号
```

结论：库里没有的号（`exists:false`）UI **正确地**提示「将预建一个内部账号」，**0.5s 内**就正确，
`13900001234` 与 `13800000091` 两例都对。前一版的 FAIL 是**读到上一步（`13800000011`）的旧文案**
（固定 `waitForTimeout(2000)` 之后单次读 `.el-dialog:visible` 的竞态），build 8083 那一轮同一断言
本来就 PASS（`["该手机号还没有账号，将预建一个内部账号"]`）。→ **WEB-09 不作为 issue**，
已在 harness 侧用 `waitDialogText` 消除该竞态。

## 3. WEB-23（及同源的 WEB-22）裁定：**断言过严，产品侧无缺陷（记账级）**

**权威口径**（`UI:admin.auth.unit`，`authority_lint.py show`）正文全文只说：

> 左单位列表、右该单位的组别列表（主从布局）。单位 / 组别：新增、改名、停用 / 启用；待核验
> （外部自填）的带黄色徽标。显示每个组别下已核验的外部人数。

`form_factor`：整页 + 弹窗。**没有任何一句要求弹窗里出现「同单位内不可重名」/「全库唯一」的字样。**

**ticket 正文**（`doc/tickets/AUTH-GROUP-001/prompt.md`）：

- L107：「组别列表带 `verifiedCount`（读时 count）。**同单位内组别名唯一、单位名全库唯一
  （部分唯一索引已保证，service 层给人话报错）**」
- L38（accept 清单）：「…**同单位内组别名唯一、不同单位可重名、软删后可重建**」
- L45-46：讲的是**唯一索引要带 `unit_id`、要带 `WHERE del_flag='0'`** 的 DDL 断言。

→ 口径是「**DB 部分唯一索引 + service 层给人话报错**」，**不是**要求在新增弹窗里常驻一句提示。

**后端实测（我跑的，写操作已由 reseed 复原）**：

```
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/group?unitId=9000009001'   # A 医院
[(9000009101,'肝胆外科组','active',2),(9000009102,'消化内科组','active',1)]
$ bash doc/verify/api.sh --as admin POST '/lqg/auth/group' \
    '{"unitId":9000009001,"groupName":"肝胆外科组","groupStatus":"active","remark":"L2 dup probe"}'
{"code":500,"msg":"该单位下已有组别「肝胆外科组」","data":null}          ← 同单位重名被拒 + 人话报错
$ bash doc/verify/api.sh --as admin POST '/lqg/auth/group' \
    '{"unitId":9000009002,"groupName":"肝胆外科组","groupStatus":"active","remark":"L2 dup probe crossUnit"}'
{"code":200,"msg":"操作成功","data":"2102036942970494977"}                ← 不同单位可重名（ticket L38 要求）
$ bash doc/verify/api.sh --as admin GET '/lqg/auth/group?unitId=9000009001'
[(9000009101,'肝胆外科组','active'),(9000009102,'消化内科组','active')]   ← 被拒后库里不变
```

后端行为**完全符合 ticket**：同单位重名被拒（`code:500` + 人话「该单位下已有组别「肝胆外科组」」）、
不同单位可重名、被拒后库不变。**WEB-23 失败的是「弹窗里必须有那句话」这条自造断言，不是产品。**

**弹窗真实文案（dev 8082 探针，DOM 文本）**：

```
新增单位 => "新增来源单位 / 单位名称 / 备注 / 确 定 / 取 消"
新增组别 => "新增组别 / 所属单位 / A 医院 / 组别名称 / 备注 / 确 定 / 取 消"
```

WEB-22 期望的「全库唯一」同样是**权威里没有的**字样，同源同判。→ 记账（不拦门），
`type: harness`（长在断言上）；**不是** `clarify`：口径已经写清楚「service 层给人话报错」，
所以不需要回头问甲方。

> 副作用声明：上面那条跨单位写操作在 B 大学建了一个临时组别，**跑 web 断言前已
> `bash doc/verify/reseed.sh --yes` 复原**（`B大学: [('类器官课题组','active')]` 已确认），
> 收尾会再跑一次 reseed。
