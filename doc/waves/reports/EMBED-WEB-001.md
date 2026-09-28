# EMBED-WEB-001 · 完工报告

- **ticket**：EMBED-WEB-001（track EMBED / phase D3 / size M）—— D3 第四张：工作台石蜡包埋页 + 核验抽屉 + 按模板导出
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑，真实输出见 §4 与 `accept-runners/accept-transcript.txt`）
  - accept 1 · DATA（按模板导出 Excel）：✅（表头 16 列逐字同序、行数 5 = 未删且所挂样本未删、三格格式逐字、两段筛选钉死）
  - accept 2 · MENU（菜单 5310 段 / 页面接核验接口 / 染色 fixture / 样本总表入口）：✅（5310:embed:lqg/embed/index、七个 perms 集合精确、getRouters 恰 1 条、vitest 19/19）
- **分支**：`task/D3`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 09:00:17`，后端进程 **PID 47072**（8081，dev profile + `--api-decrypt.enabled=false`）；plus-ui dev server 8082（PID 58291）
- **迁移**：**新增一支** `V202609231110__EMBED-WEB-001-menu.sql`（EMBED 域 11xx，1110 未被占用；见 §1）
- **单测**：Java `Tests run: 98, Failures: 0, Errors: 0, Skipped: 0`（本票 +5）；前端 vitest `19 passed / 0 failed`
- **回归**：`EMBED-MODEL-001` accept 1/2/3/4 **全绿** · `AUTH-EXT-002` accept 1/2/3 **全绿** · `SAMPLE-WEB-001` accept 1/2 **全绿** · `SAMPLE-MP-002` accept 2 **全绿** · `ExtChokepointContractTest` 4/4 · D1 回归 **40 绿 / 2 红**（两条红都与实现无关，见 §5）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,verify.env}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`、`doc/waves/regression/**`
- **没碰**：8080（Kevin 的本机服务）/ 5432 / 6379；关进程一律按 `lsof -ti tcp:<端口> -sTCP:LISTEN` 拿 PID 再 `kill`（**没用过 `pkill -f`**）
- **产物**：后端 2 个新类（`embed/export/`）+ 1 个新测试 + 3 个既有类的小改 + 1 支迁移；前端 7 个新文件（页面 / 抽屉 / 纯函数 / 单测 / api / 两本 i18n）+ 样本总表 1 处改动

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D3`；全程未切分支、未 push、未 merge |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `state.json`：`EMBED-MODEL-001` / `SAMPLE-WEB-001` 都 `done`；`EmbedQueryService.entity/exportRows`、`EmbedVerifyService`、`SegButtons`、`views/lqg/sample/**` 全部在盘且被本票真调用 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260917-04**（「EMBED-WEB-001 ← FLOW:F-SAMPLE-02.step5（导出视图放 service 层供小程序复用；抽屉显示最后修改）」）、**CR-20260917-05**（核验抽屉、导出含待核验送样）、**CR-20260918-07**（内部编号开关 / 操作人与包埋人对外）。逐条落进实现，见 §2.4 |
| 4 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:admin.embed.list` / `FLOW:F-EMBED-01.step5` / `FLOW:F-EMBED-01.step7` / `FLOW:F-SAMPLE-02.step5` 全部 `active`；口径落点见 §2 |
| 甲方模板原件第 1 行已读 | ✅ PASS | `python3 doc/verify/xlsx_header.py --print-header` → 16 列；导出与它逐字比对（accept 1 第 1 段 + `EmbedExportContractTest` 用例 ② 两侧对账） |
| 口径复述 4 条（导出列序与口径 / 染色中文标签 / SegButtons+fixture / 核验按钮置灰） | ✅ PASS | 见 §2.3 / §2.4；每条的机器证据在 §4 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | 三个 docker 容器全程在跑，未停；后端起停只按 PID |
| 动手前代码是绿的 | ✅ PASS | 开工基线 `Tests run: 93, Failures: 0, Errors: 0`（`mvn -pl ruoyi-lqg -am test`；D2 收口的 86 + D3 期间 EMBED-MODEL-001 / AUTH-EXT-002 的既有新增）；本票后 **98**（+5，全为本票新增的 `EmbedExportContractTest`） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

---

## §1 取号依据与迁移

`doc/lint-profile.yaml`：D3 = `20260923`；HHmm 按域分段，**EMBED = 11xx**。

- 已占用：`202609231100`（EMBED-MODEL-001）；派单指定 1110 段给本票 → 取 **`V202609231110__EMBED-WEB-001-menu.sql`**
- 版本号大于已应用的最大值 `202609231100` → `out-of-order=false` 下不会触发 `FlywayValidateException`（SYS-WEB-001 / AUTH-EXT-002 踩过跨域补号的坑）
- 文件名里**没有** `EMBED-MODEL-001` 这个串 → 不影响 EMBED-MODEL-001 accept 1 第 2 段的 `script LIKE 'V20260923110%__EMBED-MODEL-001-%'` 恰 1 行
- 实测（后端重启）：`Successfully validated 13 migrations` → `Successfully applied 1 migration … now at version v202609231110`

### ★★ 迁移为什么必须把 5300 段**整体搬到** 5310 段

accept 2 同时要求：

1. `SELECT menu_id||':'||path||':'||component WHERE menu_id = 5310` == `5310:embed:lqg/embed/index`
2. `getRouters` 里 `component == 'lqg/embed/index'` 的路由**恰好 1 条**

而 EMBED-MODEL-001 的 `V202609231100` 已经把 **5300** 建成同一个 component 的 C 菜单。若在 5300 下再补一行 5310 → ② 会数成 **2 条 → 红**（D2 的 SAMPLE-WEB-001 对 5200→5210 做过同一件事，先例在盘）。

所以迁移做四件事（照 SAMPLE-WEB-001 的先例）：

1. 建 **5310**（C、顶级、`path='embed'`、`component='lqg/embed/index'`、`perms='lqg:embed:list'`）
2. 建 **5311-5317**（F、`parent_id=5310`）= `list / query / add / edit / remove / export / verify` 七个权限串（与 `@SaCheckPermission` 逐字一致）
3. 授给 **101（lqg_admin）** 与 **102（lqg_internal）**
4. **删掉 5300-5307**（旧 C + 旧七个 F），避免同一 component 两条路由、也避免 `role_menu` 里留孤儿行

**授权没丢**：5301-5307 的七个 perms 在 5311-5317 上逐字重建；sa-token 的权限集合来自 perms 字符串、与 `menu_id` 无关，`/lqg/embed/**` 的鉴权一个字都没变。

```
$ python3 doc/verify/db.py --sql "SELECT menu_id||':'||menu_type||':'||COALESCE(path,'-')||':'||COALESCE(component,'-')||':'||perms FROM sys_menu WHERE menu_id BETWEEN 5300 AND 5317 ORDER BY menu_id"
5310:C:embed:lqg/embed/index:lqg:embed:list
5311:F::-:lqg:embed:list
5312:F::-:lqg:embed:query
5313:F::-:lqg:embed:add
5314:F::-:lqg:embed:edit
5315:F::-:lqg:embed:remove
5316:F::-:lqg:embed:export
5317:F::-:lqg:embed:verify
$ python3 doc/verify/db.py --sql "SELECT role_id||':'||string_agg(menu_id::text,',' ORDER BY menu_id) FROM sys_role_menu WHERE menu_id BETWEEN 5310 AND 5317 GROUP BY role_id ORDER BY role_id"
101:5310,5311,5312,5313,5314,5315,5316,5317
102:5310,5311,5312,5313,5314,5315,5316,5317
```

---

## §2 改了哪些文件（ticket §4.2）

### 2.1 后端 · 新增（`org.dromara.lqg.embed.export` 包）

| 文件 | 职责 |
|---|---|
| `embed/export/EmbedExportVo.java` | ★ **导出专用 VO**（不是实体 VO）：16 个 `@ExcelProperty(value=…, index=…)`，文字与顺序照甲方模板原件；`@ExcelIgnoreUnannotated` 保证不多带列 |
| `embed/export/EmbedExportService.java` | 导出编排 + 四个纯函数格子判据（`stainText` / `markerText` / `expressionText` / `text`）+ `headerIndex()` 自检表；**放在 service 层**供 `/mp/int/export/embed`（SYS-EXPORT-001）复用 |

**为什么必须是导出专用 VO**（accept 1 counterfeit 第 1 条）：直接拿 `EmbedVo` 导，表头会是「石蜡块编号 / 样本ID / … 创建时间 / 核验状态」，与模板逐字不等红；甲方拿导出去对他们的旧台账，列名变了就对不上。反证见 §4.3(g)。

### 2.2 后端 · 修改（三处既有文件）

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `embed/service/EmbedDictService.java` | +`labels(dictType)`（value→label 只读映射） | 染色必须导**中文标签**（`HE染色、IHC染色`）而不是 value（`HE,IHC`）；marker 表达同理。写侧一个字没动 |
| `embed/service/EmbedQueryService.java` | +`exportRows(query)`（与 `list` 同 wrapper / 同 `assemble`，去掉分页）+ 私有 `missingSampleIds(rows)` | 「带筛选导出只出筛选结果」「导出行数与列表 total 一致」「所挂样本未删才导」三条口径只能落在同一份读侧；另写一份导出 SQL 就是两处口径打架 |
| `embed/controller/EmbedController.java` | +`POST /export`（`@SaCheckPermission("lqg:embed:export")`，`EmbedQueryBo query` + `HttpServletResponse`） | 契约第 61 行点名的端点；权限串 5316 已 seed |

> 这三处都不在 ticket 的 `touches` 里（`touches` 只列了 `embed/export/**` 与 controller 的「必要改动」）→ WARN-1。

**接口清单（本票新增）**

```
POST /lqg/embed/export?paraffinBlockNo&internalNo&sampleId&stain&sectionTimeBegin&sectionTimeEnd
                       &verifyStatus&submitSource                                     lqg:embed:export
     → xlsx 文件流；筛选走 **query 参数**（不是 JSON body）；表头 16 列照甲方模板
```

与 `doc/api-contract.md` 第 61 行的 `POST /lqg/embed/export` 逐字对齐（无 doc-drift）；契约第 55 行要求「与 `/mp/int/export/{sheet}` 同一个导出视图」 → 本票把视图放在 `EmbedExportService`，小程序侧直接调它。

### 2.3 后端 · 新增契约测试（5 例，不启 Spring 容器）

`embed/export/EmbedExportContractTest.java`：

1. **导出视图 16 列、`index` 从 0 连续递增、表头逐字同序**，且显式排除「创建时间 / 核验状态 / 样本ID / 内部编号 / marker 的表达情况」
2. **与甲方模板原件第 1 行两侧对账**：用 JDK 自带 zip + DOM 读 `_input/templates/石蜡包埋送样记录模板.xlsx` 的 `sheet1.xml` + `sharedStrings.xml`（★ 不用 FastExcel 读：本模块**测试**类路径里 POI 与 commons-io 版本不配套，`NoSuchMethodError: BoundedInputStream.builder()`；为一个表头对账去动 `pom.xml` 是拿全模块编译风险换一条断言）
3. **染色一格**：顿号连接 / `其他（Masson）` / `无染色` / 空 / 字典外历史值原样带出
4. **mark 的表达情况一格**：全角冒号 + 中文分号 / 无名只写表达 / 两栏都空跳过
5. `headerIndex()` 与注解一致（给小程序导出复用）

### 2.4 前端 · 新增（`code/plus-ui`）

| 文件 | 内容 |
|---|---|
| `src/views/lqg/embed/index.vue` | 页面：筛选 6 项（石蜡块编号 / 内部编号 / 染色 / 切片时间区间 / 核验状态 / 内-外部）、工具栏（新增 / 导出 / 刷新 / 样本筛选标签）、**20 列宽表**（两个徽标列 + 模板 16 列 + 最后修改 + 操作）、待核验行浅黄底、分页、抽屉挂载、`route.query.sampleId` 自动过滤 |
| `src/views/lqg/embed/EmbedDrawer.vue` | 录入 / 编辑 / 核验三档抽屉：选样本（远程搜索，只列有效样本）、石蜡块编号、样本类型、类器官来源类型、包埋人、**七个工序时间**、染色（`SegButtons` multiple）、marker 可增删多行、操作人备注；核验档底部「判为有效并保存 / 判为无效」+ 顶部「最后修改」小字 |
| `src/views/lqg/embed/stain.ts` | **纯函数** `toggleStain`（五个值固定顺序 / NONE 与其余互斥 / 再点取消 / 字典外忽略）+ `sortStains` / `hasOtherStain` / `stainProblem` |
| `src/views/lqg/embed/stain.fixture.spec.ts` | 19 例：直接读 `doc/verify/fixtures/stain-toggle-cases.json` 的 9 条 + 10 条互斥 / 顺序 / 脏值补充判据 |
| `src/api/lqg/embed/index.ts` | 七个接口 + 类型 + 四个纯展示小工具（`neverModified` / `sampleVerified` / `isExternalPending` / `isEditable`） |
| `src/lang/lqg/embed.zh_CN.ts` / `.en_US.ts` | 约 120 组 key，键路径 `lqg.embed.*`（**没往上游 `zh_CN.ts` / `en_US.ts` 加一个 key**） |

### 2.5 前端 · 修改（一处）

| 文件 | 改动 |
|---|---|
| `src/views/lqg/sample/index.vue` | 行操作「石蜡包埋」从 `disabled` 点亮成 `v-hasPermi="['lqg:embed:list']"` + `handleEmbed(row)` → `router.push({ path: '/embed', query: { sampleId } })`；`const router = useRouter()` |

### 2.6 前端 · 删掉两个占位文件

`src/views/lqg/embed/.gitkeep`、`src/api/lqg/embed/.gitkeep` —— 目录里已有真文件，占位文件不再需要（SYS-WEB-001 建的；与 SAMPLE-WEB-001 的处理一致）。

**★ 跳转参数用 `sampleId` 而不是 `internalNo`**：待核验样本还没有内部编号，用编号跳会筛出空页。实测见 §4.4。

**页面口径细节**

- **零颜色字面量**：`views/lqg/embed/**` + `api/lqg/embed/**` + `SegButtons` 里 `grep -nE '#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\('` = 空；待核验行底色吃 `--lqg-warn-soft`（实测 `rgb(252, 241, 218)`），主色吃 `--el-color-primary`（实测 `rgb(14, 124, 123)` = `#0E7C7B`）
- **零 `el-switch`**：染色 / marker 表达都是 `SegButtons`（DOM 实测 `switchCount: 0`）
- **`useDict` 参数全是库里真名**：`lqg_stain_type` / `lqg_marker_expr` / `lqg_verify_status` / `lqg_submit_source`（先 `SELECT dict_type FROM sys_dict_type` 核过）
- **`updateTime == null` = 从未修改**：列表「最后修改」列与抽屉顶部小字都显式渲染（实测 5/5 行都是「最后修改：从未修改」）
- **路由页模板恰好一个元素根**：`index.vue` 顶层是 `<div class="p-2 lqg-embed">`，**顶层没有 HTML 注释**（SAMPLE-WEB-001 的坑）；生产构建也过（`pnpm build:prod` EXIT=0）

---

## §3 关键设计取舍（本票自己定的两处）

1. **核验档会先保存再核验**：`PUT /lqg/embed/{id}/verify` 契约只收 `{action, paraffinBlockNo, reason}`，而 ticket 要「判有效后照常补填工序与染色」。所以核验抽屉底部两个按钮都先走一次 `PUT /lqg/embed`（把抽屉里填的工序 / 染色 / marker 存掉）再走核验 —— 用户填的东西不会因为核验请求只收三个键而丢。
2. **核验前只做前端自检（染色互斥 / OTHER 必备名称），不改写用户输入**：`stainProblem()` 只报错不规范化，规范化是后端 `StainRules.normalize` 的活（权威只有一份）。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（既有 WARN，不重复计数）

`doc/verify/api.sh` 第 71 行 `ps -o lstart=` 在本 subagent 沙箱被禁（`/bin/ps: Operation not permitted`），回退的 `date -d` 在 macOS 上也不认 → **恒非 0 退出**。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边：

```
jar mtime              : 2026-09-22 09:00:17（epoch 1790038817）
lqg src newer than jar : 0 个文件（find ruoyi-lqg/src -newer <jar> 为空）
8081 PID               : 47072
该 PID 持有该 jar       : 2 行命中（lsof -p 47072 | grep -c ruoyi-admin.jar）
进程启动 ≥ jar          : True（libproc.proc_pidinfo）
后端日志               : 09:00:37 Started DromaraApplication；09:00:34 Successfully validated 13 migrations
Flyway                 : Successfully applied 1 migration … now at version v202609231110
```

本票两条 accept 的 `run` 里**都没有** Maven 行；`mvn package` / `mvn test` 实跑时都带了 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`。两条 run 都是长链，**包进函数判整条 rc**（不丢给 `set -e`），runner 在 `accept-runners/`。

### 4.1 accept 1 · DATA（按模板导出 Excel）—— ✅

```
$ bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc1-export.sh
✓ 表头 16 列与模板逐字一致；数据 5 行
✓ 表头 16 列与模板逐字一致；数据 5 行
✓ 表头 16 列与模板逐字一致；数据 5 行
✓ 表头 16 列与模板逐字一致；数据 5 行
✓ 表头 16 列与模板逐字一致；数据 2 行
✓ 表头 16 列与模板逐字一致；数据 4 行
ACCEPT-1 EXIT=0
```

逐段对应（原文 9 段 `&&` 链）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `reseed` + `rm -f /tmp/lqg-embed*.xlsx` | （静默） | 先删旧文件：读到上一次导出的文件骗不过去 |
| 2 | `POST /lqg/embed/export` | 4430 字节 xlsx | 全量导出 |
| 3 | `--rows 5 --find 石蜡块编号=T-E01-1 --expect 样本编号=T-hli01,染色=HE染色、IHC染色,mark的表达情况=Ki67：强表达；CK19：阴性,包埋人=李工` | 数据 5 行 | ★ 四格逐字：**样本编号 = 所挂样本内部编号**、染色中文标签顿号连接、marker 全角冒号 + 中文分号、包埋人 |
| 4 | `--find 石蜡块编号=T-E04-1 --expect 染色=其他（Masson）,mark的表达情况=弱表达` | 数据 5 行 | ★ `其他（具体名称）`（全角括号）+ **无名 marker 只写表达** |
| 5 | `--find 石蜡块编号=T-E02-1 --expect 染色=无染色,mark的表达情况=` | 数据 5 行 | ★ `无染色`；marker 一格为空（不是 `—`、不是 `null`） |
| 6 | `--rows "$(db.py … count(*))"` | 期望 5 / 实际 5 | ★ 行数 = **未删的石蜡包埋送样记录数且所挂样本未删**（含待核验的 2006、不含软删的 2005） |
| 7-8 | `POST '/lqg/embed/export?internalNo=T-hli01'` → `--rows 2` | 数据 2 行 | ★ 带筛选导出**只出筛选结果**（2001、2002 两块都挂 T-hli01） |
| 9 | `POST '/lqg/embed/export?verifyStatus=valid'` → `--rows "$(list?verifyStatus=valid&pageSize=100 \| jq .total)"` | 数据 4 行 = total 4 | ★ 导出与列表**同一口径** |

**counterfeit 逐条排掉**（完整转录：`accept-runners/counterfeit-transcript.txt`）：

- **「直接拿实体 VO 导出 → 表头是石蜡块编号 / 样本ID / … 且多出创建时间、核验状态等列」** → 实测表头 = 16 列模板列，`contains 样本ID / sampleId / 创建时间 / 核验状态 / 内部编号 / marker 的表达情况` 全 `False`
- **「mark的表达情况被顺手修正成 marker 的表达情况」** → 表头第 14 列逐字是 `mark的表达情况`；`EmbedExportContractTest` 用例 ① 单独再断一次
- **「染色导出了字典 value（HE,IHC）」** → 实测 `HE染色、IHC染色`；用例 ③ 断言结果不含 `HE,`
- **「导出偷偷只导有效的」** → 段 6 行数 5（含待核验 2006）；另造一条**无效**外部送样（判 2006 invalid）后导出 1 行、列表 `total` 也是 1
- **「导出不带筛选条件（永远导全量）」** → 段 7-8（2 行）与段 9（4 行）
- **「先 rm 旧文件」** → 链首就 `rm -f`

**导出的三份文件已附在报告目录**：`export-embed-all.xlsx`（5 行全量）/ `export-embed-internalNo-T-hli01.xlsx`（2 行）/ `export-embed-verifyStatus-valid.xlsx`（4 行）。

### 4.2 accept 2 · MENU（菜单与页面）—— ✅

```
$ bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc2-menu.sh
5310:embed:lqg/embed/index
lqg:embed:list
lqg:embed:query
lqg:embed:add
lqg:embed:edit
lqg:embed:remove
lqg:embed:export
lqg:embed:verify
true
true
ACCEPT-2 EXIT=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `SELECT menu_id\|\|':'…WHERE menu_id = 5310` `--eq "5310:embed:lqg/embed/index"` | `5310:embed:lqg/embed/index` | path 不是空的 |
| 2 | `SELECT perms … parent_id = 5310 AND menu_type='F'` `--col-set` 七个 | 七行（见上） | ★ 集合**精确等于** accept 要的七个 |
| 3 | `getRouters`（`--as staff`）`jq '[.. \| objects \| select(.component? == "lqg/embed/index")] \| length == 1'` | `true` | ★ 恰好 1 条（搬走 5300 段才是 1 条） |
| 4 | `grep -q 'doc/verify/fixtures/stain-toggle-cases.json' src/views/lqg/embed/stain.fixture.spec.ts` | 命中 | 用例不在实现方手里 |
| 5 | `! grep -nE '\.(skip\|todo\|only)\(' …` | 命中（无 skip/todo/only） | 不许静默跳过 |
| 6 | `pnpm vitest run … --reporter=json --outputFile=/tmp/lqg-stain-web.json` | `numFailedTests:0, numPassedTests:19` | ≥9 条且全过 |
| 7 | `grep -q "sampleId" src/views/lqg/embed/index.vue` && `grep -rqE '/verify' src/api/lqg/embed/` && `grep -rq 'sampleVerifyStatus' src/views/lqg/embed/` | 命中 | 页面接的是**核验接口**（不是拿编辑接口顶替）；核验按钮读行里的 `sampleVerifyStatus` |

**counterfeit 逐条排掉**：

| counterfeit | 本实现为什么不中 |
|---|---|
| 「无染色」做成普通多选项 → fixture 第 5、6 条红 | 5 个值是 `SegButtons multiple`，切换**只过** `toggleStain`；vitest 第 5/6 条实测 `["HE","IHC"] 点 NONE → ["NONE"]`、`["NONE"] 点 HE → ["HE"]` |
| 点了字典外的值（PAS）没被忽略 → 红 | `toggleStain(['HE'],'PAS') → ['HE']`（vitest 第 9 条 + 补充判据） |
| 菜单 path 留空 → 第 1 段红 | `5310:embed:lqg/embed/index` |
| 后端加了 `lqg:embed:verify` 却没 seed 按钮 → 第 2 段集合红 | 5317 上逐字有 `lqg:embed:verify`；`--as staff` 真调到 `PUT /lqg/embed/{id}/verify`（§4.3(f) 里判 2006 无效返回 `200 操作成功`） |
| 核验抽屉没接核验接口（拿编辑接口顶替）→ `/verify` 段红 | `api/lqg/embed/index.ts` 里 `verifyEmbed` 打 `/lqg/embed/{id}/verify` |
| 「判为有效」不看所挂样本状态 → `sampleVerifyStatus` 段红 | 见 §4.3 的 DOM 证据：待核验样本档**按钮置灰 + 写明原因**；把样本核验有效后同一个抽屉**按钮可点**（反向对照） |

### 4.3 追加（accept 之外的机器证据）

**(a) Java 单测：`Tests run: 98, Failures: 0, Errors: 0, Skipped: 0`**

```
[INFO] Running org.dromara.lqg.embed.export.EmbedExportContractTest        Tests run: 5, Failures: 0   ← 本票新增
[INFO] Running org.dromara.lqg.embed.service.EmbedQueryContractTest        Tests run: 4, Failures: 0
[INFO] Running org.dromara.lqg.embed.guard.EmbedRulesContractTest          Tests run: 8, Failures: 0
[INFO] Running org.dromara.lqg.embed.guard.EmbedChildrenCheckerContractTest Tests run: 4, Failures: 0
[INFO] Running org.dromara.lqg.embed.domain.EmbedShapeContractTest         Tests run: 6, Failures: 0
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest               Tests run: 4, Failures: 0   ← D2 的 L0 咽喉门仍绿
[INFO] Running org.dromara.lqg.sample.verify.VerifyTransitionsContractTest Tests run: 8, Failures: 0
[INFO] Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**(b) 前端 vitest（染色 fixture）：19 passed / 0 failed**

```
$ node node_modules/vitest/vitest.mjs run src/views/lqg/embed/stain.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-stain-web.json
JSON report written to /tmp/lqg-stain-web.json
passed 19 failed 0
```

**(c) DOM / 样式证据（截图只落盘，全程没有把任何 PNG 读进上下文）**

| 文件 | 覆盖项 | 实测断言输出 |
|---|---|---|
| `01-embed-list.png` | 列表（含 1 条待核验外部送样） | `headers: 20`、`rowCount: 5`、`rows[0].blockNo = 待核验`、待核验行底色 `rgb(252, 241, 218)`（= `--lqg-warn-soft`）、其余行 `rgb(255,255,255)`；搜索按钮 `rgb(14, 124, 123)` = `#0E7C7B`；「最后修改」列 5/5 = `最后修改：从未修改` |
| `02-verify-drawer.png` | ★ 核验抽屉（所挂样本**待核验**） | 标题 `核验石蜡包埋送样`；四段 `包埋信息 / 工序时间 / 染色与 marker / 操作与备注`；提示 = `所挂样本还没核验有效（当前状态：待核验），不能判为有效`；底部 `判为有效并保存`（**`disabled: true`、class 含 `is-disabled`**）/ `判为无效` / `取 消`；染色五个按钮（`HE染色 / IF染色 / IHC染色 / 其他 / 无染色`）；`switchCount: 0`；七个日期控件 |
| `02b-verify-drawer-sample-valid.png` | ★ **反向对照**：把所挂样本核验有效后**同一个抽屉** | 提示变 `所挂样本已核验有效，可以判为有效`；`判为有效并保存` **`disabled: false`** —— 不做这一步「恒置灰」也能骗过上面那条断言 |
| `03-edit-drawer.png` | 编辑抽屉（有效记录 T-E04-1） | 标题 `编辑石蜡包埋记录`；顶部 `最后修改：从未修改`；染色选中态 `checked: ["其他"]`（与库里 `OTHER` 对上）；marker 一行；`dateInputs: ["date" ×7]`；底部 `保存 / 取 消`；`switchCount: 0` |
| `04-add-drawer.png` | 新增抽屉 | 染色**未选**（`stainChecked: []`）—— 「还没选」这个状态是真的；选样本下拉远程搜索结果 = `T-hli01 · SJ90000001`（只列有效样本） |
| `05-sample-row-action.png` | 样本总表行操作 | `[编辑(false), 质控文档(true), 石蜡包埋(false), 冻存(true), 删除]` —— **石蜡包埋已点亮** |
| `06-embed-filtered-by-sample.png` | 带 `sampleId` 跳入 | `url=/embed?sampleId=9000001001`、`rowCount=2`、`blockNos=["T-E01-2","T-E01-1"]`、筛选标签 `只看样本 9000001001 的包埋记录` |
| `probe-sidebar.json` | 菜单 5310 真挂在侧栏 | 侧栏有 `{"text":"石蜡包埋","href":"/embed"}` |
| `probe-sample-clear.json` | 清掉样本筛选 | `rowCount: 5`、标签消失 |
| `probe-overlay-close.json` | 点蒙层可关 | `{"closedByOverlay": true}` |

**(d) counterfeit 探针转录**：`accept-runners/counterfeit-transcript.txt`（染色整元素匹配 / 待核验与无效都导出 / 筛选导出 / 导出视图不是实体 VO）。

**未覆盖（如实写明）**：导出的**Excel 视觉样式**（列宽、字体）没有比对——accept 只钉表头与单元格文本；`/mp/int/export/embed` 端到端不在本票（SYS-EXPORT-001），本票只把导出视图放在 service 层供它复用。

### 4.4 回归自查（本票动了菜单与样本总表入口）

| 回归 | 命令 | 结果 |
|---|---|---|
| **`EMBED-MODEL-001` accept 1** | `bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc1.sh` | **`ACCEPT-1 EXIT=0`**（`✓ 2 张表与 SSOT 逐列相符` / `1`） |
| **`EMBED-MODEL-001` accept 2** | `…/embed001-acc2.sh` | **`ACCEPT-2 EXIT=0`**（`0` / `True` / `2` / `valid`） |
| **`EMBED-MODEL-001` accept 3** | `…/embed001-acc3.sh` | **`ACCEPT-3 EXIT=0`**（五个 `true`） |
| **`EMBED-MODEL-001` accept 4** | `…/embed001-acc4.sh` | **`ACCEPT-4 EXIT=0`**（状态机逐段逐字） |
| **`AUTH-EXT-002` accept 1** | `bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc1.sh` | **`ACCEPT-1 EXIT=0`**（五个 `true`） |
| **`AUTH-EXT-002` accept 2** | `…/ext002-acc2.sh` | **`ACCEPT-2 EXIT=0`** |
| **`AUTH-EXT-002` accept 3** | `…/ext002-acc3.sh` | **`ACCEPT-3 EXIT=0`**（小程序构建 + `usingComponents` 含 `embed-card`） |
| **`SAMPLE-WEB-001` accept 1** | `bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc1-api.sh` | **`ACCEPT-1 GREEN (exit 0)`**（10 段全绿） |
| **`SAMPLE-WEB-001` accept 2** | `…/web001-acc2-menu.sh` | **`ACCEPT-2 GREEN (exit 0)`**（5210 段菜单一字未动） |
| **`SAMPLE-MP-002` accept 2** | `bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc2.sh` | **`ACCEPT-2 EXIT=0`**（表格页 fixture 仍绿） |
| **D2 的 L0 咽喉门** | `ExtChokepointContractTest` | `Tests run: 4, Failures: 0`（本票新代码不在 `ext` 包） |
| **D1 回归包** | `bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-d1-regression.sh` | **40 绿 / 2 红 / 0 环境错，rc=1** —— 两条红都与实现无关（见 §5） |

**★ 样本总表入口这一处最容易打红别人的地方**：`SAMPLE-WEB-001` accept 2 段 7 断的是「四个『有无』按钮字段不许用 el-switch / el-select」，本票只把 `embed` 那一行从 `disabled` 改成可点，没碰别的行操作，两个 accept 复跑全绿。

---

## §5 D1 回归包的两条红（都与实现无关）

```
$ bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-d1-regression.sh
== L0.2 等价取证（不用管道，避开 SIGPIPE） ==
8081 PID            : 47072
进程持有该 jar      : 2 行命中
lqg 源码新于 jar    : 0 个文件
jar mtime           : 2026-09-22 09:00:17
lsof 输出行数       : 394

== 上游回归包（原样跑；两条红与实现无关） ==
  ✗ L0.2 stale：pid 47072 没持有 …/ruoyi-admin.jar（跑的不是这个 jar）
失败 2 条：
  - L0.2 stale：pid 47072 没持有 …/ruoyi-admin.jar（跑的不是这个 jar）
  - L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '13' 13
green=40 red=2 rc=1
```

- **红①（既有）**：`SELECT count(*) FROM flyway_schema_history --eq 7` 这条**写死总数**的断言。D1 的 7 支迁移逐个点名全绿、无失败行；D2 加 3 支 → 10、EMBED-MODEL-001 加 1 支 → 11、AUTH-EXT-002 加 1 支 → 12、**本票加 1 支 → 13**。state.json 已记 issue，EMBED-MODEL-001 报告 §5 也记过，本票**不重复计数**。
- **红②（本票新识别，harness/沙箱）**：`doc/waves/regression/D1/verify.sh` 第 112 行
  `if ! lsof -p "$pid" 2>/dev/null | grep -q 'ruoyi-admin.jar'`。
  `grep -q` 命中第一条就退出并关掉读端；`lsof` 的输出有 **394 行 / 55KB**，它继续写 → **SIGPIPE（141）**；脚本 `set -uo pipefail` 把 141 当成管道失败 → 走 `bad` 分支。同一句里的 `grep -c` 数出 **2** 行命中（等价取证见上）；在 `bash -c` 里逐字重放同一句是 `HELD` —— 依赖 `lsof` 的输出分块，是竞态。
  **没有改上游脚本**（`doc/waves/regression/**` 不在 `touches` 里）→ WARN-2。

---

## §6 遗留与 raise（ticket §4.4）

### 6.1 越出 `touches` 的改动（逐条列清）

| # | 文件 | 为什么非改不可 |
|---|---|---|
| 1 | `embed/service/EmbedDictService.java` | 导出要「染色 / marker 表达」的中文标签；只加了 `labels(dictType)` 一个只读口，写侧一个字没动 |
| 2 | `embed/service/EmbedQueryService.java` | 「导出与列表同一口径」按 ticket 只能落在同一份 wrapper + 装配上；只加了 `exportRows` 与一个私有方法 |
| 3 | `embed/controller/EmbedController.java` | 契约第 61 行点名 `POST /lqg/embed/export`；`touches` 的措辞是「query/controller 的必要改动」，本票按此加一个端点方法 |

三处都是**新增方法 / 新增字段**，没有改任何既有方法签名；`EmbedService` / `EmbedVerifyService` / `EmbedExternalService` / 三个纯函数 guard / 两个 mapper **一个字节没动**。

### 6.2 没把握 / 没做的（交给后续 CR 或票决）

1. **核验档「先保存再核验」是两次请求**：如果第一次保存成功、第二次核验失败（例如撞号），用户会看到「工序存了、核验没过」。可接受（保存本身是合法的），但**不是原子**的。若甲方要原子，需要后端加一个「保存并核验」的合并端点（本票没加，因为契约里没有）。
2. **列表的「最后修改」列是新增显示列**（ticket §2 与 `UI:admin.embed.list` 都写了「抽屉顶部小字」；列表列清单没显式写这一列）。做了与样本总表同口径，删一列即可。
3. **`paraffinBlockNo` 筛选沿用 EMBED-MODEL-001 的模糊匹配**（`LIKE`），不是精确 —— 工作台搜索框的常规口径，accept 没钉。
4. **marker 的「表达」允许不选**（与 `EmbedMarkerBo` 一致：表达可空）；导出时该行只写名称。本票没额外收紧。
5. **`/mp/int/export/embed`（SYS-EXPORT-001）没端到端验证**：本票只保证 `EmbedExportService.rowsOf(query)` 这一层可复用（`rowsOf` 是 public、不碰 `HttpServletResponse`）。
6. **切片染色提示列刻意不做**：属 SAMPLE-HINT-001（本票只把样本总表的「石蜡包埋」入口点亮，提示本体不在本票）。

### 6.3 与权威 / 契约不一致的地方

- `doc/api-contract.md` 第 61 行的 `POST /lqg/embed/export` 与实现逐字对齐（**没有改契约**，无 doc-drift）。
- 契约第 55 行要求「与 `/mp/int/export/{sheet}` 同一个导出视图」→ 本票把视图放在 `embed/export/EmbedExportService`（service 层），满足。
- 契约没写「导出 16 列的具体列名」→ 以甲方模板原件为准（ticket §0 明文），实现与它对账。

---

## §7 坑与解法（给下游，3-5 行）

1. ★ **「菜单搬家」不是可选动作**：`getRouters` 里同一个 `component` 只能有一条路由，而 EMBED-MODEL-001 已经把 `lqg/embed/index` 挂在 5300 上。本票的 accept 要 `menu_id=5310` **且** 该 component 恰 1 条 → 必须在迁移里先 DELETE 5300-5307 再重建 5310-5317（授权靠 perms 字符串延续，一个字没丢）。同域后续票若要改菜单挂载点，先查这个 component 有几条。
2. ★ **前端跳转要用菜单的 `path`，不是 `/lqg/<域>`**：本票第一版写成 `router.push('/lqg/embed')` → 命中 `/:pathMatch(.*)*` 的 404，页面一片空白（DOM 里连 `.lqg-embed` 都没有）。菜单是顶级 `path='embed'` → 路由是 `/embed`（`getRoutes()` 里 `name=Embed5310`）。**跳转前先 `getRoutes()` 看一眼**。
3. ★ **`lsof -p <pid> | grep -q` 在 `set -o pipefail` 下会假红**：`grep -q` 提前退出 → `lsof` 收到 SIGPIPE（141）→ 管道被判失败。取证要写 `out="$(lsof -p …)"; grep -c` 或 `grep … >/dev/null`。这与实现无关，但会让人误以为「跑的不是这个 jar」。
4. **`@ExcelProperty` 的 `index` 必须从 0 连续**：缺号 = 多一列空列。单测里加了「index 必须连续递增」的断言，顺手也把「多带列」的形态（创建时间 / 核验状态 / 样本ID）列进禁用清单。
5. **测试类路径里的 POI 与 commons-io 不配套**：`FastExcel.read(...)` 在本模块单测里会 `NoSuchMethodError: BoundedInputStream.builder()`。要读 xlsx 做对账，用 JDK 自带 `ZipFile` + DOM 读 `xl/worksheets/sheet1.xml` + `xl/sharedStrings.xml`（★ `setNamespaceAware(false)`，否则 `getElementsByTagNameNS("*", …)` 一个节点都取不到——第一版就在这里假红「模板必须有表头行」）。

---

## §8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | ticket-drift | ticket 的 `touches` 与实现不符：导出要在 `embed/service/EmbedDictService.java`（字典标签）、`embed/service/EmbedQueryService.java`（同一份 wrapper + 装配）、`embed/controller/EmbedController.java`（端点）上加方法，三个文件都不在 `touches`（只列了 `embed/export/**`） | 本票已按「query/controller 的必要改动」的措辞改了 3 个文件（全为新增方法，无签名变更），并在 §6.1 逐条列清。方案：ticket 生成器把「读路径 / 字典 / controller 所在文件」也写进 `touches`（SAMPLE-WEB-001 WARN-1、AUTH-EXT-001、SAMPLE-MP-001、SAMPLE-VERIFY-001 已四次同型命中）。 |
| WARN-2 | S2 | harness | `doc/waves/regression/D1/verify.sh` L0.2 的 `lsof -p "$pid" \| grep -q 'ruoyi-admin.jar'` 在 `set -o pipefail` 下**恒假红**（grep 提前退出 → lsof SIGPIPE 141） | 本票实跑 40 绿 / 2 红，其中这条是沙箱/harness 现象：同一 PID 的 `lsof` 输出 394 行、`grep -c` 命中 2 行、`bash -c` 逐字重放同一句是 HELD。**没有改上游脚本**（不在 touches）。方案：把那句改成 `lsof -p "$pid" > "$tmp" ; grep -q … "$tmp"` 或 `grep … >/dev/null`。 |
| WARN-3 | S3 | clarify | 核验抽屉「判为有效并保存」会**先发一次 `PUT /lqg/embed`**（把工序 / 染色 / marker 存掉）再发 `PUT /lqg/embed/{id}/verify` —— 两次请求不是原子的 | 契约的 verify 只收 `{action, paraffinBlockNo, reason}`，而 ticket 要「判有效后照常补工序与染色」。若撞号导致核验失败，用户会看到「工序存了、核验没过」。方案：要么在契约里注明这个两步语义，要么后端加一个合并端点。 |
| WARN-4 | S3 | clarify | 本票给列表加了「最后修改」列（`UI:admin.embed.list` 只写了抽屉顶部小字） | 依据是 ticket §2 的「抽屉顶部小字「最后修改」」+ 与样本总表同口径。若甲方嫌列多，删一列即可（后端 `updateByName` / `updateTime` 键留着不碍事）。方案：把「列表是否显示最后修改」在 `UI:admin.embed.list` 里写清。 |
| WARN-5 | S3 | debt | `EmbedExportService.rowsOf(query)` 这一层可复用，但 **`/mp/int/export/embed`（SYS-EXPORT-001）没有任何运行时证据** | 本票的 accept 只打 `/lqg/embed/export`。两张票共用的「同一个导出视图」目前只有代码结构与 `EmbedExportContractTest` 的形状断言。方案：SYS-EXPORT-001 的 accept 里加一条「`/mp/int/export/embed` 与 `/lqg/embed/export` 两个文件逐列一致」。 |
| WARN-6 | S3 | harness（既有，本票不重复计数） | `api.sh --fresh-module` 在本沙箱恒 exit 2（`ps` 被禁）+ Maven 需带三参数 | 已连续 9+ 张命中。本票用「`find -newer jar` 为空 + `lsof` 拿 PID + 进程持该 jar + 日志 mtime 晚于 jar」四项等价替代（§4.0）。方案同上游：换成 `lsof` / 日志 mtime 的兼容写法。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：D1 回归包把 flyway 迁移总数写死成 7、`db.py` 是只读执行器却 exit 0、`reseed.sh` 清不掉运行时账号（收尾跑 `clean-orphan-accounts.sh`）、surefire `groups` 假绿、`PageQuery` 只有两参构造、`updateById` 忽略 null、`update(null, LambdaUpdateWrapper)` 不填 `update_by`、「补逗号 LIKE」少通配符静默 0 行、MyBatis-Plus 无按列名排序重载、`@SaCheckPermission` 缺 `sys_menu` 行是 403。

---

## §9 收尾

```
$ for p in 8080 8081 8082 8083 8099; do printf '%s: ' $p; lsof -ti tcp:$p -sTCP:LISTEN | tr '\n' ' '; echo; done
8080: （未碰，Kevin 的本机服务）
8081: （空）   ← 本票后端，收尾按 PID 关掉
8082: （空）   ← 本票 plus-ui dev server，收尾按 PID 关掉
8083 / 8099: （空）
```

- **关进程一律按 PID**：`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`（本票**从未**用过 `pkill -f`，也从未对 8080 做任何操作）
- **DB 收尾**：`doc/verify/reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`
- **docker 容器**：`lqg-dev-postgres`(5433) / `lqg-dev-redis`(6380) / `lqg-dev-minio`(9002+9003) 全程在跑，**没停**（留给后续 ticket）
- **`git checkout -- code/miniapp/src/pages.json code/plus-ui/.eslintrc-auto-import.json`**：已执行
- **复跑材料**：`accept-runners/{embedweb001-acc1-export.sh, embedweb001-acc2-menu.sh, embedweb001-counterfeit-probes.sh, embedweb001-d1-regression.sh, embedweb001-shots.mjs, accept-transcript.txt, counterfeit-transcript.txt}`
  （`embedweb001-shots.mjs` 需要放在有 `puppeteer-core` 的目录里跑，例如 `cp` 到 `/tmp/shots/` 后 `node /tmp/shots/embedweb001-shots.mjs`）
