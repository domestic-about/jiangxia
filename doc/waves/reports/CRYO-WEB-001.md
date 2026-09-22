# CRYO-WEB-001 · 完工报告

- **ticket**：CRYO-WEB-001（track CRYO / phase D4 / size M）—— D4 第四张：工作台冻存页 + 流水抽屉 + 按模板导出
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑，真实输出见 §4 与 `accept-runners/accept-transcript.txt`）
  - accept 1 · DATA（按模板导出 Excel）：✅（表头 11 列 = 模板 9 列逐字同序 + 追加「代数」「当前剩余/支」；行数 7 = 未删批次数；三格逐字钉死）
  - accept 2 · MENU（菜单 5410 段 / 可达 / 两个权限串 / 页面 / api 接上改删 / 无开关控件）：✅
- **分支**：`task/D4`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 12:34:20`，后端进程 **PID 14130**（8081，dev profile + `--api-decrypt.enabled=false`），`GET /lqg/sys/ping` → **200**
- **迁移**：**新增一支** `V202609241210__CRYO-WEB-001-menu.sql`（CRYO 号段 1210，> 已应用最大值 1205；日志 `Successfully applied 1 migration … now at version v202609241210`）
- **单测**：Java `Tests run: 174, Failures: 0, Errors: 0, Skipped: 0`（本票前 169 → +5，全为本票新增的 `CryoExportContractTest`）；前端 vitest `11 passed / 0 failed`；`pnpm build:prod` EXIT=0
- **回归**（先 `reseed` + `clean-orphan` 再跑）：`CRYO-MODEL-001` acc1/2/3 ✅ · `CRYO-FLOW-001` acc1/2/3 ✅ · `CRYO-REMIND-001` acc1/2/3 ✅ · `EMBED-WEB-001` acc1/2 ✅ · `SAMPLE-WEB-001` acc1 ✅ · `ExtChokepointContractTest` **4/4** ✅ · D1 回归包 **41 绿 / 1 红 / 0 环境错**（唯一红 = **既有** flyway 迁移总数写死成 7，issue #82）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,xlsx_header.py}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`、`doc/waves/regression/**`
- **没碰**：8080（Kevin 的本机服务，PID 72163 全程未碰）/ 5432 / 6379 / 9000 / 9001；关进程一律 `lsof -ti tcp:<端口> -sTCP:LISTEN` 拿 PID 再 `kill`（**从没用过 `pkill -f`**）；长进程全走受管后台作业（**没用过 `nohup &`**）
- **产物**：后端 2 个新类（`cryo/export/`）+ 1 个新测试 + 3 个既有类的小改 + 1 支迁移；前端 8 个新文件（页面 / 3 个抽屉弹窗 / 纯函数 / 单测 / api / 两本 i18n）+ 样本总表 1 处改动 + 删除 2 个 `.gitkeep` 占位

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D4`；全程未切分支、未 push、未 merge |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `state.json`：`CRYO-REMIND-001` / `SAMPLE-WEB-001` 都 `done`；`CryoOverdueService.isOverdue`、`tabCounts`、`SegButtons`、`views/lqg/sample/**` 全在盘且被本票真调用 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260917-04**（`CRYO-WEB-001 ← FLOW:F-CRYO-02.step3, step5, UI:admin.cryo.list`：流水抽屉可改可删、初始支数可改）· **CR-20260917-05**（写流水只在工作台）· **CR-20260918-07**（超期阈值读系统参数；`UI:mp.cryo.list` 的「详情弹层加修改入口」不在本票）。逐条落进实现，见 §2.5 |
| 5 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:admin.cryo.list` / `FLOW:F-CRYO-01.step3` / `FLOW:F-CRYO-01.step5` / `FLOW:F-CRYO-02.step3` / `FLOW:F-CRYO-02.step5` 全部 `active`；口径落点见 §1 |
| 甲方模板原件第 1 行已读 | ✅ PASS | `python3 doc/verify/xlsx_header.py --template "_input/templates/-80冻存模板.xlsx" --print-header` → 9 列；导出与它逐字比对（accept 1 第 1 段 + `CryoExportContractTest` 用例 ② 两侧对账） |
| 口径复述 4 条（初始≠剩余 / 初始可改 / 改删流水当场刷新 / 暂存-80 用两个按钮） | ✅ PASS | 见 §1；每条的机器证据在 §4 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | `docker ps` 三个容器全程在跑，未停；**没碰** 8080 / 5432 / 6379 |
| 动手前代码是绿的 | ✅ PASS | 开工基线 `Tests run: 169, Failures: 0`（D4 第三张收口值）；本票后 **174**（+5） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

> ★ 上游 **刻意留空 5410**：`V202609241200__CRYO-MODEL-001-cryo.sql` 只建了 5401-5407 七个 **F** 权限行、**不建 C 页面菜单**（它第 28-35 行写明是为 CRYO-WEB-001 让号）。所以本票**不需要**像 EMBED-WEB-001 那样「搬号」——实测迁移前 `component='lqg/cryo/index'` 的路由是 **0** 条，建完 5410 后**恰 1 条**。

---

## §1 口径复述（逐条对 accept 与权威核）

| # | 口径（ticket §0/§2 + 权威锚 + CR） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | ★ **「冻存数量/支」导的是初始支数，剩余另起一列放在模板 9 列之后** | `CryoExportVo.initQty`（index 2，取 `CryoBatchVo.initQty`）与 `remainingQty`（index 10，取读时算的 `remainingQty`） | accept 1 第 1 段：3001 那一格 `冻存数量/支=8` 而 `当前剩余/支=6`（**两个不同的数**）；counterfeit ① 三处对账（接口 `initQty:8/remainingQty:6`、库内 `8|6`、xlsx 两格） |
| 2 | **初始支数在编辑抽屉里可改**；被拒时把后端提示原样显示 | `CryoDrawer` 的 `initQty` 可编辑 + `flow.ts#failText` 优先用后端 `msg` | `CryoExportContractTest` 只钉导出；「可改 + 被拒」本体由 `CRYO-MODEL-001` acc2 钉住（本票回归复跑 EXIT=0），前端只负责把后端原话弹出来（`probe-edit-drawer.json`：编辑档 `冻存数量/支 = 8`，提示文案写明「改小到透支会被后端拒绝」） |
| 3 | **流水抽屉每行「修改」「删除」**：修改弹窗与取走/补入/调整同款（类型不可改），删除二次确认；改删成功后列表剩余与超期标记当场刷新，被拒时显示后端指出的是哪一笔 | `CryoFlowDrawer`（时间线 + 每行 修改/删除）+ `CryoFlowDialog`（四态同款）+ `updateFlow` / `deleteFlow` | DOM 实测（§4.4）：抽屉 3 行各带「修改」「删除」；点「修改」打开的弹窗标题 =「修改登记」、类型格 =「取走」且写明「登记类型不能改」、支数输入框 = `3`（不是 `-3`）；保存触发 **`PUT /lqg/cryo/batch/9000003003/flow/9000003104`**，行上当场变 `-2 / 操作后剩余：5 / 已改 · 测试管理员 2026-09-22 12:40:50`；删除二次确认后触发 **`DELETE …/flow/9000003102`**，3 行 → 2 行且两步剩余当场重算成 6 / 8 |
| 4 | **「暂存-80度超低温冰箱」是两个按钮（是 / 否），用 `SegButtons`** | `CryoDrawer` 的 `<SegButtons :options="flagOptions">`；列表与导出都是「是 / 否」 | DOM 实测：编辑档里是 `[{是, checked:true},{否, checked:false}]` 两个 `el-radio-button`、`switchCount: 0`；导出该列取值集合 = `['否','是']`（counterfeit ④）；accept 2 最后一段 `! grep '<el-switch'` 绿 |
| 5 | 页签（全部 / -80 超期 / 液氮）数字取 `tabCounts`；超期行整行浅红 +「已超 N 天」徽标、置顶 | `index.vue` 的 `tabCounts`（响应顶层，**不是**数 rows）；`rowClassName` 吃 `--lqg-danger-soft`；后端默认排序置顶 | DOM 实测：页签文本 `全部 7 / -80 超期 2 / 液氮 2` **逐字等于**同一次请求的 `tabCounts`（两侧不同源：一侧读 DOM、一侧直接打接口）；超期 2 行底色 `rgb(251,228,225)` = `#FBE4E1`、非超期 `rgb(255,255,255)`；徽标 `今天到期` / `已超 6 天`；前两行都是超期行；切「超期」页签 → 2 行、切「液氮」 → 2 行（`probe-tabs.json`） |
| 6 | 从样本总表带 `sampleId` 跳入并过滤；点亮 SAMPLE-WEB-001 里置灰的「冻存」行操作 | `sample/index.vue#handleCryo` → `router.push({path:'/cryo', query:{sampleId}})`；`index.vue` 的 `onMounted` 读 `route.query.sampleId` | DOM 实测：样本总表首行操作 `[核验(可点), 质控文档(灰), 石蜡包埋(可点), 冻存(可点)]`（`probe-sample-row-action.json`）；点「冻存」→ `url=/cryo?sampleId=9000001001`、`rowCount=2`、`names=["T-hli01-GZ-N-P2-EM2-2e5","T-hli01-GZ-N-P5-EM2-1e5"]` |

---

## §2 改了哪些文件（ticket §4.2）

### 2.1 Flyway / 取号依据

**`ruoyi-admin/src/main/resources/db/migration/V202609241210__CRYO-WEB-001-menu.sql`（新增，+1 支）**

- **取号**：票面 §2 点名 `V202609241210__CRYO-WEB-001-menu.sql`；落在 frontmatter 的 `V20260924121*__CRYO-WEB-001-*.sql` 通配内，`202609241210` **大于**已应用最大值 `202609241205`（CRYO-REMIND-001）→ `out-of-order=false` 下不会触发 `FlywayValidateException`（SYS-WEB-001 / AUTH-EXT-002 踩过跨域补号的坑）。**没有撞上游任何票的号**。
- **内容**：5410（C、顶级、`path='cryo'`、`component='lqg/cryo/index'`、`perms='lqg:cryo:list'`）+ 5411-5417 七个 F 按钮（`list/query/add/edit/remove/export/flow`，与 `@SaCheckPermission` 逐字一致）+ 授给 **101（lqg_admin）/ 102（lqg_internal）**。全程 `ON CONFLICT DO NOTHING` 幂等。
- **★ 不删 5401-5407、不搬号**：上游刻意只建了 F 行、把 C 菜单留给 5410（见 §0 末的引用）。5401-5407 的 perms 与 5411-5417 逐字相同，sa-token 的权限集合是**字符串集合**、与 `menu_id` 无关，所以 `/lqg/cryo/**` 的鉴权一个字没变；反过来删掉它们只会给别的票/别的角色的授权挖坑。
- **实测**（后端重启）：
  ```
  Successfully applied 1 migration to schema "public", now at version v202609241210 (execution time 00:00.012s)
  $ python3 doc/verify/db.py --sql "SELECT menu_id||':'||menu_type||':'||COALESCE(parent_id::text,'-')||':'||COALESCE(path,'-')||':'||COALESCE(component,'-')||':'||perms FROM sys_menu WHERE menu_id BETWEEN 5410 AND 5417 ORDER BY menu_id"
  5410:C:0:cryo:lqg/cryo/index:lqg:cryo:list
  5411:F:5410::-:lqg:cryo:list
  5412:F:5410::-:lqg:cryo:query
  5413:F:5410::-:lqg:cryo:add
  5414:F:5410::-:lqg:cryo:edit
  5415:F:5410::-:lqg:cryo:remove
  5416:F:5410::-:lqg:cryo:export
  5417:F:5410::-:lqg:cryo:flow
  ```
- **只动菜单与授权**：不建表、不加列、不动任何业务数据、不动任何 `t_lqg_*` 表。

### 2.2 后端 · 新增（`org.dromara.lqg.cryo.export` 包）

| 文件 | 职责 |
|---|---|
| `cryo/export/CryoExportVo.java` | ★ **导出专用 VO**（不是 `CryoBatchVo`）：11 个 `@ExcelProperty(value=…, index=…)` = 模板 9 列 + 追加「代数」「当前剩余/支」；`@ExcelIgnoreUnannotated` 保证不多带列 |
| `cryo/export/CryoExportService.java` | 导出编排（`export` / `rowsOf` / `toExportVo`）+ 三个纯函数格子判据（`flagText` / `text` / `numberText`）+ `headerIndex()` 自检表；**放在 service 层**供 `/mp/int/export/cryo`（SYS-EXPORT-001）复用 |
| `cryo/export/CryoExportContractTest.java`（test 侧） | 5 例：11 列表头逐字同序 + 与甲方模板原件两侧对账（JDK zip + DOM 读 `sheet1.xml` / `sharedStrings.xml`）+ 初始≠剩余 + 是/否 + 日期/整数格子 + `headerIndex()` 一致 |

**为什么必须是导出专用 VO**（accept 1 counterfeit 第 1 条）：直接拿 `CryoBatchVo` 导，表头会是「冻存样品 / 样本ID / 剩余支数 / 当前位置 / 创建时间 …」，与模板 9 列逐字不等红。反证见 §4.3 ②。

### 2.3 后端 · 修改（**★ 越出 ticket `touches`**，逐条见 §6.1）

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `cryo/batch/service/CryoQueryService.java` | +`exportRows(query)`（与 `list` 同 wrapper / 同 `assemble`，去分页）+ 私有 `missingSampleIds(rows)` | 「带筛选导出只出筛选结果」「行数 = 未删批次数且所挂样本未删」两条口径只能落在同一份读侧；另写一份导出 SQL 就是两处口径打架（`buildWrapper` 是**包内可见**的，换包就得复制一份） |
| `cryo/batch/controller/CryoBatchController.java` | +`POST /export`（`@SaCheckPermission("lqg:cryo:export")`，`CryoQueryBo query` + `HttpServletResponse`）+ 类 Javadoc 更新 | 契约第 67 行点名的端点；权限串 5416 已 seed |

> 两处都是**新增方法 / 新增依赖**，没有改任何既有方法签名；`CryoBatchService` / `CryoBalanceChecker` / `CryoFlowService` / `CryoOverdue*` / 两个 mapper **一个字节没动**。

**接口清单（本票新增 1 个端点）**

```
POST /lqg/cryo/batch/export?internalNo&cryoName&sampleId&location
                           &overdueOnly&freezeTimeBegin&freezeTimeEnd      lqg:cryo:export
     → xlsx 文件流；筛选走 **query 参数**（不是 JSON body）；
       表头 = 模板 9 列 + 追加「代数」「当前剩余/支」，逐字同序
（服务口）CryoExportService#rowsOf(query)  给 /mp/int/export/cryo（SYS-EXPORT-001）复用
```

与 `doc/api-contract.md` 第 67 行的 `POST /lqg/cryo/batch/export` 逐字对齐（**无 doc-drift**）；第 55 行要求「与 `/mp/int/export/{sheet}` 同一个导出视图」→ 本票把视图放在 `CryoExportService`（service 层，`rowsOf` 是 public、不碰 `HttpServletResponse`）。

### 2.4 前端 · 新增（`code/plus-ui`，8 个文件）

| 文件 | 内容 |
|---|---|
| `src/views/lqg/cryo/index.vue` | 页面：**三个页签（数字取 `tabCounts`）**、筛选 5 项（内部编号 / 冻存样品 / 位置 / 只看超期 / 冻存时间区间）、工具栏（新增 / 导出 -80 冻存 / 刷新 / 样本筛选标签）、**15 列宽表**（模板 9 列 + 内部编号 / 代数 / 当前剩余 / 当前位置 + 最后修改 + 操作）、超期行整行浅红 +「已超 N 天」徽标、分页、四个子组件挂载、`route.query.sampleId` 自动过滤 |
| `src/views/lqg/cryo/CryoDrawer.vue` | 新增 / 编辑抽屉：选样本（远程搜索，只列有效样本）、冻存样品、代数（`^P\d{1,3}$`）、冻存时间、**初始支数可改**、密度、**暂存-80 用 `SegButtons` 是/否**、冻存人、转液氮时间、液氮位置、备注；底部保存；顶部「最后修改」小字 |
| `src/views/lqg/cryo/CryoFlowDrawer.vue` | 流水抽屉：`el-timeline` 时间倒序，每行显示类型 / 变化量 / 取自 / 经手人 / 时间 / 用途 / **操作后剩余** / **「已改 · 某某 时间」或「从未修改」**，带「修改」「删除」 |
| `src/views/lqg/cryo/CryoFlowDialog.vue` | 取走 / 补入 / 盘点调整 / 修改登记 **四态同款**弹窗（类型不可改、只显示）；支数上限 = 剩余、调整量 ≠ 0、调整必填原因；被拒时弹后端原话 |
| `src/views/lqg/cryo/CryoToLn2Dialog.vue` | 转液氮弹窗（时间不得早于冻存时间、位置必填） |
| `src/views/lqg/cryo/flow.ts` | **纯函数**：`isFlowKind` / `editableQtyOf`（take/add 存的是负 delta，弹窗要填绝对值）/ `qtyProblem`（上限 = 剩余；adjust ≠ 0）/ `purposeProblem` / `failText` |
| `src/views/lqg/cryo/flow.spec.ts` | 11 例 vitest，逐条钉住上面三个判据（`11 passed / 0 failed`） |
| `src/api/lqg/cryo/index.ts` | 11 个接口 + 类型 + 四个纯展示小工具（`neverModified` / `overdueDaysText` / `deltaText` / `flowTypeKey` / `locationKey`） |
| `src/lang/lqg/cryo.zh_CN.ts` / `cryo.en_US.ts` | 约 150 组 key，键路径 `lqg.cryo.*`（**没往上游 `zh_CN.ts` / `en_US.ts` 加一个 key**） |

### 2.5 前端 · 修改（一处）+ 删除占位

| 文件 | 改动 |
|---|---|
| `src/views/lqg/sample/index.vue` | 行操作「冻存」从 `disabled` 点亮成 `v-hasPermi="['lqg:cryo:list']"` + `handleCryo(row)` → `router.push({ path: '/cryo', query: { sampleId } })` |
| `src/views/lqg/cryo/.gitkeep`、`src/api/lqg/cryo/.gitkeep` | 删除（目录里已有真文件；与 SAMPLE-WEB-001 / EMBED-WEB-001 的处理一致） |

**CR 逐条落地**（ticket §0 要求「以 CR 为准」）：

- **CR-20260917-04**：「冻存登记可改可删（软删）、初始支数可改；改删后按 `flow_time` 逐笔算剩余，任何一步为负就拒绝；登记类型不可改」→ 本体在 `CRYO-FLOW-001`（本票回归复跑全绿），**前端把「改」「删」两个入口接到 `PUT|DELETE …/{id}/flow/{flowId}`**（DOM + 网络实测见 §4.4），类型在弹窗里做成只读展示；初始支数在编辑抽屉里可改。
- **CR-20260917-05**：「写流水只在工作台；小程序只读」→ 本票**只加 `/lqg/cryo/**` 的端点**，`touches` 与实现里没有任何 `/mp/**` 路由；小程序侧复用 `CryoExportService#rowsOf`（SYS-EXPORT-001）。
- **CR-20260918-07**：⑧ 超期阈值读系统参数 → 本票的页签数字与超期徽标**全部取后端读时算的值**，前端**没有任何天数常量**（`grep -nE '\b14\b' src/views/lqg/cryo src/api/lqg/cryo` = 空）；`UI:mp.cryo.list` 的「详情弹层加修改入口」是 CRYO-MP-001 的活（ticket §3 边界）。

**页面口径细节**

- **零颜色字面量**：`views/lqg/cryo/**` + `api/lqg/cryo/**` 里 `grep -nE '#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\('` = 空；超期行底色吃 `--lqg-danger-soft`（实测 `rgb(251, 228, 225)`）、徽标吃 `--lqg-danger`。
- **零开关控件**：全部 `views/lqg/cryo/*.vue` 里 `grep -nE '<el-switch'` 命中 **0**（连注释里都不写那个标签字面量——写了会被自己的 accept grep 命中，见 §7-1）；DOM 实测 `switchCount: 0`。
- **`useDict` 参数**：本票页面**不需要字典**（代数 / 密度都是手填文本，位置是枚举常量），所以一个 `useDict` 都没调 —— 也就没有「参数写错名字静默无数据」的风险。
- **`updateTime == null` = 从未修改**：列表「最后修改」列与抽屉顶部小字都显式渲染（实测 5 行都是「最后修改：从未修改」，流水行显示「从未修改」/「已改 · 某某 时间」）。
- **路由页模板恰好一个元素根**：`index.vue` 顶层是 `<div class="p-2 lqg-cryo">`，**顶层没有 HTML 注释**；生产构建 EXIT=0、`vue-tsc --noEmit` 对 `views/lqg/**` + `api/lqg/**` + `lang/lqg/**` **零 error**（全仓其它文件的既有 TS 报错与本票无关）。
- **`el-dialog` 根组件不能写 `.lqg-xxx { &__y {} }` 嵌套样式**：模板根是 `el-dialog`（teleport 到 body），父选择器匹配不到任何节点 → 整段样式静默失效。本票的 `CryoFlowDialog` / `CryoToLn2Dialog` 直接按 class 写。

---

## §3 关键设计取舍（本票自己定的三处）

1. **页签是 `queryParams` 的派生值，不是第二份状态**：`activeTab` 是 `computed`（`overdueOnly ? 'overdue' : location==='ln2' ? 'ln2' : 'all'`），setter 反过来写 `queryParams`。「页签」与「位置下拉 + 只看超期勾选」共用一个真相源——否则点了「液氮」页签、下拉还显示「全部」这种不一致迟早出现。两个筛选互斥时（选了位置就清 `overdueOnly`，勾了超期就清 `location`），避免「超期 ∧ 液氮」这个恒空组合。
2. **计数格子导成文本而不是数值**：`CryoExportVo.initQty` / `remainingQty` 是 `String`。第一版写成 `Integer`，FastExcel 落的是 `<v>8.0</v>`，`xlsx_header.py` 读回来是 `8.0` ≠ 期望的 `8`（**实跑红过**，见 §7-3）。SAMPLE-EXPORT-001 / EMBED-WEB-001 的导出 VO 本来就全是 `String` —— 本票跟上了这个约定。
3. **导出受 `mine` 影响但不拨动它**：`exportRows` 与 `list` 用同一份 `buildWrapper`，所以 `mine=true` 也会一并生效；工作台页面不带这个参数、导出按当前筛选走。这是「同一份口径」的自然结果，不是额外行为。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（既有 WARN，不重复计数）

`doc/verify/api.sh` 第 71 行 `ps -o lstart=` 在本 subagent 沙箱被禁（`/bin/ps: Operation not permitted`），macOS 又没有 `date -d` 回退 → **恒非 0 退出**（issue #151，连续十几张票的既有 WARN）。按规矩**没有改 `api.sh`**。等价反 stale 证据（`accept-runners/cryoweb001-freshness.sh`，八项）：

```
① jar：Sep 22 12:34 174095024
② 源码比 jar 新的文件（必须为空，两处：src 与 本票新包）：
② 命中行数 = 0
③ 8081 监听 PID = 14130
   jar mtime = 2026-09-22 12:34:20 (1790051660)
   进程启动  = 2026-09-22 12:34:35 (1790051675)
   ✓ 进程不早于 jar
④ 进程持有 jar：2 处
⑤ 嵌套 jar 内含本票新 class：2 个
⑥ flyway 里本票那一行：V202609241210__CRYO-WEB-001-menu.sql
⑦ 菜单 5410 的注册情况：8
⑧ GET /lqg/sys/ping（--as staff）：200	操作成功
```

本票两条 accept 的 `run` 里**都没有** Maven 行；`mvn package` / `mvn test` 实跑时都带了 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`。两条 run 都是长链，**包进函数判整条 rc**（不丢给 `set -e`），runner 在 `accept-runners/`。

> ★ **issue #155 那段**：本票两条 accept 的 `run` 里**都没有** `grep -qE '^200'` 这类业务码断言（accept 1 只看 xlsx 与库、accept 2 用 `jq -e` 与 `grep` 文件内容），所以**没有需要改成 `--bizcode` 的段落**。

### 4.1 accept 1 · DATA（按模板导出 Excel）—— ✅

```
$ bash doc/waves/reports/CRYO-WEB-001/accept-runners/cryoweb001-acc1-export.sh
✓ 表头 11 列与模板逐字一致；数据 7 行
✓ 表头 11 列与模板逐字一致；数据 7 行
✓ 表头 11 列与模板逐字一致；数据 7 行
✓ 表头 11 列与模板逐字一致；数据 7 行
ACCEPT-1 EXIT=0
```

逐段对应（原文 8 段 `&&` 链；唯一偏差 = 去掉 `--fresh-module`）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `reseed` + `rm -f /tmp/lqg-cryo.xlsx` | （静默） | 先删旧文件：读到上一次导出的文件骗不过去 |
| 2 | `POST /lqg/cryo/batch/export --out /tmp/lqg-cryo.xlsx` | 4500 字节 xlsx | 全量导出 |
| 3 | `--rows 7 --find 冻存样品=T-hli01-GZ-N-P2-EM2-2e5 --expect 冻存数量/支=8,冻存密度=2e5,暂存-80度超低温冰箱=是,冻存人=李工,代数=P2,当前剩余/支=6` | ✓ | ★ 六格逐字：**初始 8 / 剩余 6 两个数都在**、是否暂存 = **是**、代数 = P2 |
| 4 | `--find 冻存样品=T-oco01-JC-T-P4-EM1-5e5 --expect 暂存-80度超低温冰箱=否,液氮储存位置=1号罐-1架-A2,当前剩余/支=5` | ✓ | ★ 直接进液氮那一档（`in_minus80='N'`）→ **否** |
| 5 | `--find 冻存样品=T-hli01-GZ-N-P5-EM2-1e5 --expect 冻存数量/支=3,当前剩余/支=0` | ✓ | ★ 取空的批次：初始 3 / 剩余 **0**（0 不是空格子） |
| 6 | `--rows "$(db.py --quiet --sql "SELECT count(*) … JOIN t_lqg_sample …")"` | 期望 7 / 实际 7 | ★ 行数 = **未删批次数且所挂样本未删**（含取空的 3004、不含软删的 3008） |

**counterfeit 逐条排掉**（完整转录：`accept-runners/counterfeit-transcript.txt`）：

① **「冻存数量/支」导出了剩余而不是初始** → 实测三方对账：接口 `.data = {initQty:8, remainingQty:6}`、直连库 `8|6`、xlsx 两格 `8` / `6`。**3001 那一行期望 8 实际 6 会红**，实际两个数都在、且分居第 3 列与第 11 列。
② **「追加列放到了模板列中间」** → 实测表头前 9 列**逐字等于**模板原件第 1 行，追加的 `['代数','当前剩余/支']` 跟在其后；`xlsx_header.py --extra` 正是这条判据。
③ **「取空的批次不导出」** → 直连库：未删批次 **7** / 其中有剩余的只有 **6**；导出文件数据 **7** 行，且 `T-hli01-GZ-N-P5-EM2-1e5`（剩余 0）在文件里。
④ **「是否暂存 -80 导成 Y / N」** → 该列取值集合 = `['否','是']`。
⑤ **「导出视图顺手拿实体 VO」** → 表头里不含 `剩余支数 / 当前位置 / 创建时间 / 样本ID / 内部编号`（`CryoExportContractTest` 用例 ① 把这几项列进禁用清单）。
⑥ **两侧不同源**：导出文件 vs 甲方模板原件（表头）；导出文件 vs `db.py` 直连库的独立 `init_qty + SUM(delta)` 汇总（剩余列，逐批相等）；数值再由 seed 期望钉住。

**导出文件已附在报告目录**：`export-cryo-all.xlsx`（7 行全量）。

### 4.2 accept 2 · MENU（菜单与页面）—— ✅

```
$ bash doc/waves/reports/CRYO-WEB-001/accept-runners/cryoweb001-acc2-menu.sh
5410:cryo:lqg/cryo/index
true
true
ACCEPT-2 EXIT=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `SELECT menu_id\|\|':'…WHERE menu_id = 5410` `--eq "5410:cryo:lqg/cryo/index"` | `5410:cryo:lqg/cryo/index` | path 与 component 都不是空 |
| 2 | `getRouters`（`--as staff`）`jq '[.. \| objects \| select(.component? == "lqg/cryo/index")] \| length == 1'` | `true` | ★ **恰 1 条**（上游刻意留空 5410，所以不用搬号） |
| 3 | `GET /system/user/getInfo`（`--as staff`）`jq '(.data.permissions\|index("lqg:cryo:flow") != null) and (…"lqg:cryo:export"…)` | `true` | ★ 两条权限串都下发到内部人员（102）：实测 `["lqg:cryo:add","lqg:cryo:edit","lqg:cryo:export","lqg:cryo:flow","lqg:cryo:list","lqg:cryo:query","lqg:cryo:remove"]` |
| 4 | `test -f …/cryo/index.vue && grep -q 'tabCounts' …` | 命中 | 页面在盘、页签数字读后端键 |
| 5 | `grep -rqE 'updateFlow\|editFlow' api/lqg/cryo && grep -rqE 'delFlow\|deleteFlow\|removeFlow' api/lqg/cryo` | 命中（`updateFlow` / `deleteFlow`） | ★ 流水抽屉接上了改删两个接口 |
| 6 | `! grep -nE '<el-switch' views/lqg/cryo/*.vue` | 命中（无开关控件） | ★ 暂存 -80 是 `SegButtons` 两个按钮 |

accept 2 的 `name` 里还写着「样本总表的『冻存』入口已点亮」——`run` 里没有对应段，本票用 DOM 证据补上（§4.4 ⑤）。

**counterfeit 逐条排掉**：

| counterfeit | 本实现为什么不中 |
|---|---|
| 「页签上的数字是前端对当前页 rows 自己数的 → 翻页就错」 | DOM 实测页签 `全部 7 / -80 超期 2 / 液氮 2` **逐字等于**同一次请求响应顶层的 `tabCounts`（两侧不同源）；代码里 `tabCounts` 直接吃 `res.tabCounts`，缺键时**保持上一次的值**而不退回 `rows.length`；切到「超期」页签（只 2 行）时数字仍是 `7 / 2 / 2`（整表口径，正好证明不是数 rows） |
| 「取走按钮的权限串没 seed → staff 的 permissions 里没有 `lqg:cryo:flow`」 | 段 3 `true`；且 DOM 里 `--as staff` 的取走/补入/调整/流水按钮都在（`v-hasPermi` 命中） |
| 「是否暂存 -80 用了开关」 | 段 6 命中 0；DOM `switchCount: 0`；编辑档里是 `是/否` 两个 `el-radio-button` |
| 「流水抽屉还是只读、没接改删两个接口」 | 段 5 命中 `updateFlow` / `deleteFlow`；DOM 实测点「修改」→ `PUT /lqg/cryo/batch/9000003003/flow/9000003104`、点「删除」→ `DELETE …/flow/9000003102`（§4.4 ③） |

### 4.3 追加证据（accept 之外的机器证据）

**(a) Java 单测：`Tests run: 174, Failures: 0, Errors: 0, Skipped: 0`**

```
[INFO] Running org.dromara.lqg.cryo.export.CryoExportContractTest            Tests run: 5, Failures: 0   ← 本票新增
[INFO] Running org.dromara.lqg.cryo.batch.CryoBalanceCheckerTest             Tests run: 9, Failures: 0
[INFO] Running org.dromara.lqg.cryo.batch.service.CryoQueryContractTest      Tests run: 7, Failures: 0
[INFO] Running org.dromara.lqg.cryo.flow.service.CryoFlowConcurrencyTest     Tests run: 7, Failures: 0
[INFO] Running org.dromara.lqg.cryo.remind.service.CryoOverdueServiceTest    Tests run: 8, Failures: 0
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest                 Tests run: 4, Failures: 0
[INFO] Tests run: 174, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

完整逐类输出：`accept-runners/java-test-transcript.txt`。

**(b) 前端 vitest（流水弹窗纯判据）：11 passed / 0 failed**

```
$ node node_modules/vitest/vitest.mjs run src/views/lqg/cryo/flow.spec.ts --reporter=json --outputFile=/tmp/lqg-cryo-flow.json
numPassedTests 11 numFailedTests 0
```

覆盖：类型白名单 / 「修改」弹窗支数取绝对值（take 存的是负 delta）/ take·add 正整数 / **取走上限 = 当前剩余** / **adjust ≠ 0 且原因必填** / 小数不合法 / 失败文案优先用后端原话。转录：`accept-runners/ui-test-transcript.txt`。

**(c) 生产构建 + 类型检查**

```
$ rm -rf dist && npm_config_store_dir=<ws>/.pnpm-store pnpm build:prod     → EXIT=0（dist 里有 lqg/cryo 的 chunk）
$ npx vue-tsc --noEmit -p tsconfig.json | grep -E 'views/lqg/|api/lqg/|lang/lqg/'  → （空，本票零 error）
```

**(d) DOM / 截图证据（★ 截图只落盘，全程没有把任何 PNG 读进上下文）**

| 文件 | 覆盖项 | 实测断言输出（`probe-*.json`） |
|---|---|---|
| `01-cryo-list.png` | 列表（7 行，2 条超期置顶） | `tabs: ["全部 7","-80 超期 2","液氮 2"]`、`headers: 15`、`rowCount: 7`、超期行 `rowBg: rgb(251,228,225)` = `--lqg-danger-soft`、非超期 `rgb(255,255,255)`、`badges: ["今天到期","已超 6 天"]`、`pinnedFirstTwo: true`、`switchCount: 0`、首行操作 `["取走","补入","盘点调整","流水","编辑",""]` |
| `01b-cryo-tab-overdue.png` | 「-80 超期」页签 | `rowCount: 2`、`names: ["T-hli02-…-2e5","T-hli01-…-2e5"]`，而页签数字仍是 `7 / 2 / 2`（整表口径） |
| `02-take-dialog.png` | 取走弹窗 | 标题 `取走登记`、五格 `类型 / 取走支数 / 用途·原因 / 经手人 / 发生时间`、正文含 `当前剩余 5 支`、`switchCount: 0` |
| `03-flow-drawer.png` | ★ 流水抽屉（3003） | `count: 3`、`editButtons: 3`、`removeButtons: 3`；正文逐行含 `取走 -3 … 操作后剩余：4 … 药敏实验 从未修改 修改 删除` / `补入 +2 … 操作后剩余：7` / `取走 -1 … 操作后剩余：5`；头部 `当前剩余：4 支` |
| `04-flow-edit-dialog.png` | ★ 「修改」= 同款弹窗、类型不可改 | 标题 `修改登记`、`typeText: 取走`、正文含 `登记类型不能改；要换类型就删掉重登。`、`qtyValue: "3"`（**不是 -3**）、`switchCount: 0` |
| — | ★ 改完之后当场刷新（网络 + DOM） | 写接口调用 = `[{"method":"PUT","url":"/dev-api/lqg/cryo/batch/9000003003/flow/9000003104"}]`；行变 `取走 -2 … 操作后剩余：5 … 已改 · 测试管理员 2026-09-22 12:40:50`，另两行剩余跟着从 `4/7` 变 `5/7`（**同一份读时算**） |
| — | ★ 删除（二次确认 + 软删 + 重算） | 确认框原文 `确认删除这一笔「取走 -1」登记？删掉之后追溯就断了（是软删，库里仍留痕）。`；调用 = `[{"method":"DELETE","url":"/dev-api/lqg/cryo/batch/9000003003/flow/9000003102"}]`；行数 3 → 2、剩余重算成 `6 / 8` |
| `05-edit-drawer.png` | 编辑抽屉 | 标题 `编辑冻存批次`、`lastMod: 最后修改：从未修改`、两段 `批次信息 / 存放位置`、`initQtyValue: "8"`、暂存 -80 = `[{是, checked:true},{否, checked:false}]`、`switchCount: 0` |
| `06-sample-row-action.png` | 样本总表行操作 | `[核验(可点), 质控文档(灰), 石蜡包埋(可点), 冻存(可点)]`、`cryoButtonDisabled: false` |
| `07-cryo-filtered-by-sample.png` | 带 `sampleId` 跳入 | `url=/cryo?sampleId=9000001001`、`rowCount: 2`、`names=["T-hli01-GZ-N-P2-EM2-2e5","T-hli01-GZ-N-P5-EM2-1e5"]` |
| `probe-sidebar.json` | 菜单 5410 真挂在侧栏 | 侧栏有 `{"text":"冻存管理","href":"/cryo"}` |
| `probe-errors.json` | 整轮零前端错误 | `[]`（`pageerror` 与 HTTP ≥ 400 都是 0） |

**未覆盖（如实写明）**：导出的 **Excel 视觉样式**（列宽、字体、单元格类型）没有比对 —— accept 只钉表头与单元格**文本**；`/mp/int/export/cryo` 端到端不在本票（SYS-EXPORT-001），本票只把导出视图放在 service 层供它复用。「被拒时库不变」的**后端**本体在 `CRYO-FLOW-001`（本票回归复跑全绿），前端只做了「把后端原话弹出来、并把界面重新对齐」这一层。

### 4.4 回归自查（先 `reseed` + `clean-orphan`，再跑）

脚本：`bash doc/waves/reports/CRYO-WEB-001/accept-runners/cryoweb001-regression.sh`（日志 `accept-runners/regression.out`）

| 回归 | 结果 |
|---|---|
| `CRYO-MODEL-001` accept 1 / 2 / 3 | **EXIT=0 / 0 / 0** |
| `CRYO-FLOW-001` accept 1 / 2 / 3 | **EXIT=0 / 0 / 0** |
| `CRYO-REMIND-001` accept 1 / 2 / 3 | **EXIT=0 / 0 / 0** |
| `EMBED-WEB-001` accept 1 / 2 | **EXIT=0 / 0** |
| `SAMPLE-WEB-001` accept 1 | **`ACCEPT-1 GREEN (exit 0)`** |
| `ExtChokepointContractTest` | **`Tests run: 4, Failures: 0`** |
| D1 回归包 | **41 绿 / 1 红 / 0 环境错，rc=1** —— 唯一那条红是**既有** harness 缺陷 |

```
$ bash doc/waves/regression/D1/verify.sh --skip-build
  ✓ L1.1 flyway 无失败行（success IS NOT TRUE 的行 = 0）
  ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '17' 17
  …（L1.2 / L1.3a-d 全绿）
═══ 汇总 ═══
失败 1 条：
  - L1.1 D1 的 7 支迁移全部记录在案 → [FAIL] 期望 '7'，实际 '17' 17
```

★ 这一条**不是「以前好的坏了」**：D1 的 7 支迁移**逐个点名全绿**、无失败行；红的是 `SELECT count(*) FROM flyway_schema_history --eq 7` 这个**写死总数**的断言（D2 起每加一支迁移就假红一条，CRYO-MODEL-001 §5 / CRYO-FLOW-001 §5 逐字同款，state.json 已记 issue #82）。本票的迁移把它从 16 推到 **17**，**本票不重复计数**。

> ★ **本票的回归脚本每一步前都先 `reseed` + `clean-orphan-accounts.sh`**（issue #157）：accept 用 `--as staff` mock 登录与 `POST /lqg/auth/staff` 会按手机号建出运行时 `sys_user`，D1 的 L1.2/L1.3c 恰好查这两个手机号 —— 不清就跑会假红 5 条（CRYO-FLOW-001 实录）。

---

## §5 越界与 raise（ticket §4.4）

### 5.1 越出 ticket `touches` 的改动：**2 个文件**（WARN-1）

`touches` 只列了 `cryo/export/**` + 迁移，但 ticket §2 要求的「导出与列表同一口径（带筛选导出只出筛选结果、行数 = 未删批次数）」与「契约点名的 `POST /lqg/cryo/batch/export` 端点」落在 `cryo/batch/**`：

| 文件 | 性质 | 非改不可的理由 |
|---|---|---|
| `cryo/batch/service/CryoQueryService.java` | **纯新增方法**（`exportRows` + 私有 `missingSampleIds`） | 导出必须与列表共用同一个 `buildWrapper` + `assemble`；那个 `buildWrapper` 是**包内可见**（`static`，无修饰符），换包就只能复制一份 → 两处口径打架。派单已授权「确实动到的 `ruoyi-lqg/.../cryo/**`（导出视图/端点）」 |
| `cryo/batch/controller/CryoBatchController.java` | **纯新增方法**（`POST /export`）+ 类 Javadoc | 契约第 67 行点名的端点就挂在这张表的 controller 上 |

两处都是新增方法 / 新增依赖，**没有改任何既有方法签名**；`CryoBatchService` / `CryoBalanceChecker` / `CryoFlowService` / `CryoOverdue*` / 两个 mapper / 上游任何测试 **一个字节没动**。

### 5.2 与权威 / 契约不一致的地方

- `doc/api-contract.md` 第 67 行的 `POST /lqg/cryo/batch/export` 与实现逐字对齐（**没有改契约**，无 doc-drift）。
- 契约第 55 行要求「与 `/mp/int/export/{sheet}` 同一个导出视图」→ 本票把视图放在 `cryo/export/CryoExportService`（service 层，`rowsOf` public、不碰 `HttpServletResponse`），满足。
- 契约**没写**导出 11 列的具体列名 → 以甲方模板原件为准（ticket §0 明文），实现与它对账（`CryoExportContractTest` 用例 ② 读原件两侧对账）。
- `UI:admin.cryo.list` 说的列表列 = 「模板 9 列 + 内部编号、代数、当前剩余、当前位置」→ 本票实现逐条一致（另加「最后修改」与「操作」两列，前者与样本总表 / 石蜡包埋页同口径、后者是行操作的容器）。**「已超 N 天」徽标放在「冻存时间」列内**（权威没把它单列成列，所以没占第 15 列）。

### 5.3 没把握 / 需要主会话确认的口径（不改权威，只报）

1. **`tabCounts` 的作用域**：本票按 `CRYO-REMIND-001` 的既有实现取**整表口径**（三个数不随页签/筛选收窄；`total` 才是当前筛选下的行数）。页签数字因此切页签时**不变** —— 这是「`overdue` 必须与超期清单 / 首页计数恒等」的必然结果，但契约与票面都没明写。已记 WARN-4（CRYO-REMIND-001 报告 §6.3 也报过）。
2. **「位置」下拉与「只看超期」勾选 vs 三个页签是两套控件做同一件事**：权威的筛选清单要求「位置」与「只看超期」，票面 §2 要求三个页签。本票把它们做成**同一个真相源**（页签是派生值），两个筛选互斥时互相清空。若甲方希望页签与筛选相互独立（例如「液氮」页签里再勾「只看超期」= 空集也允许），需要改这一处。
3. **计数格子导成文本**：`冻存数量/支` / `当前剩余/支` 在 xlsx 里是**文本**（`t="inlineStr"`）而不是数值。这是为了让「8」不被读成「8.0」（accept 的 `xstr(cell)` 判据）并与 SAMPLE-EXPORT-001 / EMBED-WEB-001 同一约定。副作用：Excel 里这两列会有「数字存为文本」的绿三角提示。如果甲方更在意单元格类型，需要后端换一个自定义 `WriteHandler` 把数字写成整数 `<v>8</v>`，同时保持 accept 的对账脚本可读 —— 属于跨票（三个导出）的口径决定。
4. **「暂存-80 是/否」在列表里是纯文本**，不是「两个按钮」——模板上的「是 否（按钮）」在**编辑抽屉**里用 `SegButtons` 落地；列表是只读展示。`UI:admin.cryo.list` 的措辞（accept 2 counterfeit 最后一条）指的是录入控件，我按这个理解做的。

---

## §6 坑与解法（给下游，5 行）

1. **★ accept 的 grep 会扫你自己的注释。** `! grep -nE '<el-switch' code/plus-ui/src/views/lqg/cryo/*.vue` 是**整文件**匹配：我第一版在 `CryoDrawer.vue` 的 Javadoc 里写了那个标签字面量来解释「为什么不用它」→ 段 6 直接红。凡是 accept 会 grep 的字面量（禁用标签、阈值数字），**连注释里都不能出现**（CRYO-REMIND-001 的 `\b14\b` 同款）。
2. **★ `Integer` 字段导出的数值是 `<v>8.0</v>`，对账脚本读回 `8.0`。** FastExcel 把 `Integer` 当 double 写；`xlsx_header.py` 用 `str(cell)` 比对，期望是 `8` → **第一版实跑红**。处置：**导出侧所有格子都在 Java 里拼成文本**（SAMPLE-EXPORT-001 / EMBED-WEB-001 的导出 VO 本来就全是 `String`），日期用 `toString()`、计数用 `String.valueOf(intValue())`（取空是 `"0"`、不是空格子）。
3. **★ `el-dialog` 当模板根时，`scoped` 样式不能写 `.lqg-x { &__y {} }` 嵌套。** teleport 到 body 的根元素上没有那个父 class，父选择器匹配不到任何节点 → 整段样式**静默失效**（页面不报错、只是难看）。按 class 平铺写，scoped 的 `data-v` 挂在元素本身上照样生效。
4. **★ 页签 / 下拉 / 勾选做同一件事时，只留一份状态。** 三个控件各自 `ref` 一份「当前视图」，切换时必然出现「点了液氮页签、下拉还显示全部」。本票把页签做成 `computed`（getter 从 `queryParams` 推、setter 写回 `queryParams`），并让「位置」与「只看超期」互斥时互相清空 —— 否则会出现「超期 ∧ 液氮」这个恒空组合，用户以为是 bug。
5. **★ `AxiosResponse<T>` 在这个项目里被 `src/types/axios.d.ts` 补成了 `{code,msg,rows,total}`**（不是 `data`）：所以 `listXxx()` 的响应能直接读 `res.rows` / `res.total`，但**读不到** `res.tabCounts` 之类的自定义顶层键（`vue-tsc` 报 `Property 'tabCounts' does not exist`）。本票没有去动那个**全仓共用**的类型声明文件（改它会牵动所有页面），而是在页面里 `const res: any = await listBatches(...)` 再逐键收窄。

---

## §7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| **WARN-1** | **S2** | ticket-drift | CRYO-WEB-001 的 `touches` 装不下实现：导出的「同一口径」与端点落在 `cryo/batch/**` 两个文件（`CryoQueryService` 加 `exportRows`、`CryoBatchController` 加 `POST /export`），都不在 `touches`（只列了 `embed/export/**` 式的 `cryo/export/**`） | 逐条取证与「非改不可」见 §5.1。这是**同型第 N 次**（SAMPLE-WEB-001 / EMBED-WEB-001 / AUTH-EXT-001 / CRYO-REMIND-001 都命中过）。方案：ticket 生成器把「读侧 service / controller 所在文件」也写进 `touches`。派单已授权本票动 `cryo/**`，故未 STOP。 |
| **WARN-2** | **S2** | harness（既有，不重复计数） | `api.sh --fresh-module` 在 subagent 沙箱恒非 0（`ps -o lstart=` 被禁 + macOS 无 `date -d`） | state.json 已记 issue #151。本票的等价证据 = `cryoweb001-freshness.sh` **八项**（§4.0）。**本票没改 `api.sh`。** |
| **WARN-3** | **S2** | harness（既有，不重复计数） | D1 回归包把 flyway 迁移总数写死成 7 —— 本票的迁移把它推到 **17** | 取证见 §4.4：D1 的 7 支逐个点名全绿、无失败行；红的是 `--eq 7`（issue #82）。**本票没改回归包**（`doc/waves/regression/**` 不在 `touches`）。 |
| **WARN-4** | **S3** | clarify | **计数格子导成文本**（`冻存数量/支` / `当前剩余/支` 在 xlsx 里是 `inlineStr` 而不是数值） | 见 §5.3-3。这是为了让 accept 的 `str(cell)` 判据拿到 `8` 而不是 `8.0`，也是三个导出（SAMPLE-EXPORT-001 / EMBED-WEB-001 / 本票）的既有约定；副作用是 Excel 的「数字存为文本」提示。方案：要么在契约里写清「导出计数列是文本」，要么三个导出一起换自定义 `WriteHandler` 写整数并同步改对账脚本。 |
| **WARN-5** | **S3** | clarify | **页签与「位置 / 只看超期」筛选是两个控件做同一件事**（本票做成同一个真相源 + 互斥） | 见 §5.3-2。若甲方希望它们独立（允许「液氮 ∧ 只看超期」这个空集），要改这一处；契约与票面都没写。 |
| **WARN-6** | **S3** | clarify | **`tabCounts` 是整表口径**（切页签时数字不变） | 与 CRYO-REMIND-001 的 WARN-4 同一条，本票只是**消费方**、不重复计数；写在这里是为了让「页签数字看着不跟着变」有一个可查的解释。 |
| **WARN-7** | **S3** | debt | **`CryoExportService.rowsOf(query)` 这一层可复用，但 `/mp/int/export/cryo`（SYS-EXPORT-001）没有任何运行时证据** | 本票的 accept 只打 `/lqg/cryo/batch/export`；两张票共用的「同一个导出视图」目前只有代码结构与 `CryoExportContractTest` 的形状断言。方案：SYS-EXPORT-001 的 accept 里加一条「`/mp/int/export/cryo` 与 `/lqg/cryo/batch/export` 两个文件逐列一致」。 |
| **WARN-8** | **S3** | harness（既有，不重复计数） | 同票「先跑 accept、后跑回归」会让 D1 假红 5 条（issue #157） | 本票的 `cryoweb001-regression.sh` **每一步前都先 `reseed` + `clean-orphan-accounts.sh`**，实测 41 绿 / 1 红（既有那条）。**不重复计数。** |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：Maven 三参数、`api.sh` token 缓存只看 mtime（本票 runner 开头都 `rm -f /tmp/lqg-verify-token-*`）、`PageQuery` 只有两参构造、`@SaCheckPermission` 缺菜单行是 403、MyBatis 类型别名按短名去重（本票新类 `CryoExportVo` / `CryoExportService` 短名全仓唯一，后端实跑启动成功）、MyBatis-Plus 带参 ORDER BY 的 count 坑（本票的导出走 `selectList` 不分页，未触发；列表由 CRYO-REMIND-001 处置）、`update(null, LambdaUpdateWrapper)` 不填 `update_by`、`db.py --quiet` 是「只打印第一列」（本票 accept 1 第 6 段正好用它取 `count(*)`，语义正确）。

---

## §8 验证用长进程与本票起过的进程（ticket §4.5）

| 进程 | 端口 | 收尾 |
|---|---|---|
| 后端 java（`bash .tmp/run-backend.sh`） | 8081 | **收尾时已关**（`lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID → `kill <PID>`；本票的 PID 依次为 82636 → 14130 → 收尾时的最终 PID）。日志 `/tmp/cryoweb-backend{,2,3}.log` |
| plus-ui dev server（`pnpm dev`，受管后台作业） | 8082 | **收尾时已关**（PID 18448）。日志 `/tmp/cryoweb-ui-dev.log` |
| docker 容器 | 5433 / 6380 / 9002+9003 | **留着**（`lqg-dev-postgres` / `lqg-dev-redis` / `lqg-dev-minio`），未停 |
| 8080（Kevin 的本机服务）/ 5432 / 6379 / 9000 / 9001 | — | **全程未碰**（8080 上的 PID 72163 是别人的） |

- **起长进程一律用受管后台作业**（`run_in_background: true`），**没有用过 `nohup ... &`**；**关进程一律按 PID**（`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`），**从没用过 `pkill -f 'ruoyi-admin.jar'`**。
- ★ 中途后端进程被 SIGKILL（exit 137，日志停在一次 `GET /lqg/cryo/batch/list`）——与本票代码无关（同机同时跑 headless Chrome + vite dev + JVM）；**按 PID 重启**后重新取证，两条 accept 与全部回归都是在**重启后**跑的（§4.0 的 freshness 对的就是重启后的进程）。
- **DB 收尾**：`bash doc/verify/reseed.sh --yes` + `bash doc/waves/tools/clean-orphan-accounts.sh --yes`（见 §9）。
- **复跑材料**：`accept-runners/{cryoweb001-acc1-export.sh, cryoweb001-acc2-menu.sh, cryoweb001-freshness.sh, cryoweb001-counterfeit-probes.sh, cryoweb001-regression.sh, cryoweb001-shots.mjs, accept-transcript.txt, counterfeit-transcript.txt, java-test-transcript.txt, ui-test-transcript.txt, regression.out}`
  （`cryoweb001-shots.mjs` 需要放在有 `puppeteer-core` 的目录里跑，例如 `cp` 到 `/tmp/shots/` 后 `node /tmp/shots/cryoweb001-shots.mjs`）

---

## §9 收尾记录

```
1) git checkout -- code/plus-ui/.eslintrc-auto-import.json code/miniapp/src/pages.json   # 若被 dev 改过
2) bash doc/verify/reseed.sh --yes                     # 回干净 seed
3) bash doc/waves/tools/clean-orphan-accounts.sh --yes # 清运行时按手机号建的雪花 id 账号
4) lsof -ti tcp:8081 -sTCP:LISTEN  →  PID  →  kill <PID>   # 只按 PID；没用 pkill -f
5) lsof -ti tcp:8082 -sTCP:LISTEN  →  PID  →  kill <PID>   # plus-ui dev server
6) for p in 8080 8081 8082 8083 8099; do lsof -ti tcp:$p -sTCP:LISTEN; done   # 8081/8082/8083/8099 空；8080 未碰
7) docker ps：lqg-dev-postgres(5433) / lqg-dev-redis(6380) / lqg-dev-minio(9002+9003) 保留在跑，未停
```

- **git**：只 `git add` 本票自己的路径（`plus-ui/src/views/lqg/cryo/**`、`plus-ui/src/api/lqg/cryo/**`、`plus-ui/src/lang/lqg/cryo.*.ts`、`plus-ui/src/views/lqg/sample/index.vue`、`ruoyi-lqg/.../cryo/export/**`、`ruoyi-lqg/.../cryo/batch/{service/CryoQueryService.java,controller/CryoBatchController.java}`、`db/migration/V202609241210__CRYO-WEB-001-menu.sql`、`doc/waves/reports/CRYO-WEB-001/**`），**没有 `git add -A`**；未 push、未 merge、未动 `doc/waves/state.json` 与 `_manifest.json`。
