# SAMPLE-EXPORT-001 · 完工报告

- **ticket**：SAMPLE-EXPORT-001（track SAMPLE / phase D3 / size M）—— D3：样本记录信息表 / 类器官收样记录两张 Excel 按甲方模板导出
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑；真实输出见 §4 与 `accept-runners/accept-transcript.txt`）
  - accept 1 · DATA（两张导出与甲方模板原件逐字对表头；行数与同条件列表 total 一致；按钮字段 / 性别 / 加密列逐格钉死；软删样本不导出）：✅
  - accept 2 · MENU（5217 按钮权限出自本票迁移、5210 path、`getRouters` 恰 1 条、页面两个导出按钮接上并带筛选）：✅
- **分支**：`task/D3`（未切分支 / 未 push / 未 merge / 未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 09:22:04`；后端进程 **PID 53332**（8081，dev profile + `--api-decrypt.enabled=false`）；plus-ui dev server 8082（PID 77376）
- **迁移**：**新增一支** `V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql`（SAMPLE 域 D3 的 1000 段；**重建 dev 库**后 14 支按版本号升序跑完，见 §1）
- **单测**：Java `Tests run: 103, Failures: 0, Errors: 0, Skipped: 0`（本票 +5）；plus-ui `pnpm build:prod` EXIT=0
- **回归**：`EMBED-WEB-001` accept 1/2 **绿** · `SAMPLE-WEB-001` accept 1 **绿** / accept 2 **红（已知规格冲突，非本票引入 —— 主会话已裁定按需求归属解、记 issue #129，见 §5.2）** · `SAMPLE-MP-002` accept 2 **绿** · `ExtChokepointContractTest` 4/4 · D1 回归 **41 绿 / 1 红**（只剩 flyway 写死总数那条已知假红）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,verify.env}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/lint-profile.yaml`、`doc/api-contract.md`、`doc/waves/state.json`、`_manifest.json`、`doc/waves/regression/**`
- **没碰**：8080（Kevin 的本机服务）/ 5432 / 6379；关进程一律按 `lsof -ti tcp:<端口> -sTCP:LISTEN` 拿 PID 再 `kill`（**没用过 `pkill -f`**）
- **产物**：后端 4 个新类 + 1 个新测试 + 1 个既有类的小改（新增方法）+ 1 支迁移；前端 1 个新 api + 1 个页面改动 + 2 本 i18n 的 toolbar 段

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D3`；全程未切分支、未 push、未 merge |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `doc/waves/state.json`：`SAMPLE-WEB-001` `done`；`views/lqg/sample/index.vue`（23 列宽表 + 9 项筛选）、`api/lqg/sample/index.ts`、5210-5216 菜单全部在盘且被本票真复用（§4.2 逐段实跑） |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | **CR-20260917-04 / -05** 扫过：本票落点就是 `FLOW:F-SAMPLE-02.step5`（导出视图放 service 层供小程序复用、范围=当前筛选、**待核验 / 无效也导**）—— 见 §2.3 的 `exportRows` 与 §4.4 探针。CR-20260918-07（内部编号开关 / 操作人对外）**不影响导出**：那两条只作用于外部接口与外部版文档，导出是内部接口、全字段明文（ADR-0006） |
| 4 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `FLOW:F-SAMPLE-02.step5`（active：「tissue 类导 14 列 / organoid 类导 7 列；表头逐字同序；范围=当前筛选；按钮字段导「有 / 无」」）、`UI:admin.sample.list`（active；工具栏两个导出按钮）、`FIELD:t_lqg_sample.sample_kind`（active）、`FIELD:t_lqg_sample.donor_name`（active）全部 `active`；口径落点见 §3 |
| **ADR-0006** 口径 | ✅ PASS | 「工作台内部人员看明文」→ 导出导**明文**；实现路径是 `SampleQueryService.toVo` 解密后的 `SampleVo`，**不绕过实体**（§4.4 全表扫描：供体姓名 / 住院号无裸 Base64 密文） |
| 甲方模板原件第 1 行已读 | ✅ PASS | `xlsx_header.py --print-header` → tissue **14 列** / organoid **7 列**；导出与它逐字比对（accept 1 每段 + `SampleExportContractTest` 用例 ①②） |
| 口径复述 3 条 | ✅ PASS | ① 两张导出是同一张样本表的两个视图（14 / 7 列，**有无病理不进导出**）② 按钮字段「有 / 无」、性别中文、加密列明文 ③ 导出范围 = 当前筛选、与列表同一个查询条件对象 —— 逐条落进 §3，机器证据在 §4 |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002） | ✅ PASS | 三个 docker 容器全程在跑、未停；后端起停只按 PID；8080 全程无监听也没碰 |
| 动手前代码是绿的 | ✅ PASS | 开工基线 `Tests run: 98, Failures: 0`（EMBED-WEB-001 报告的口径）；本票后 **103**（+5，全为本票新增的 `SampleExportContractTest`） |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。
**唯一的红**（`SAMPLE-WEB-001` accept 2 段 4）**不是本票实现缺陷**，而是两条 accept 互斥的**已知规格冲突**，且**已由主会话裁定按需求归属解**（导出权限归 REQ-SAMPLE-011/012 = 本票所有；`SAMPLE-WEB-001` 那句「perms 精确等于六个」是写早了、过紧的断言）→ 记 WARN-2 / issue **#129**，不 STOP、不改上游 ticket。

---

## §1 取号依据与迁移

`doc/lint-profile.yaml`：D3 = `20260923`；HHmm 按域分段，**SAMPLE = 10xx**。

- 已被占用：`202609221000`（SAMPLE-MODEL-001）、`202609221001`（SAMPLE-VERIFY-001）、`202609221010`（SAMPLE-WEB-001）、`202609230902`（AUTH-EXT-002）、`202609231100`（EMBED-MODEL-001）、`202609231110`（EMBED-WEB-001）
- ticket §2 明文指定 `V202609231000__SAMPLE-EXPORT-001-perm.sql`；本票取 **`V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql`**（同一支，只把 description 写全；Flyway 的 description 不参与任何断言）。SAMPLE-HINT-001（同域 D3）**不建迁移**（`touches` 里没有 db/migration）
- 本迁移**只做三件事**：建 **5217**（F、`parent_id=5210`、`perms='lqg:sample:export'`）→ 授给 **101 / 102** → 别的什么都不动。
  - **不动 5210 的 `path='sample'`**（accept 2 段 3 断它）；**不动 5211-5216**；**不建第二条 `lqg/sample/index` 路由**（accept 2 段 4 断「恰 1 条」）
  - ★ `@SaCheckPermission("lqg:sample:export")` 缺 `sys_menu` 权限行时是 **403 不是 500** —— 少了这行两个端点会静默全 403，所以这行是本票的硬前置

### ★★ 为什么必须重建 dev 库

`202609231000` **小于**已应用的最大值 `202609231110`（EMBED-WEB-001），而 `spring.flyway.out-of-order=false` → 在**已有库**上直接启动会 `FlywayValidateException`（SYS-WEB-001 / issue #12 的先例）。

**解法（本票照做，未改 out-of-order 配置 —— prod 语义要保持）**：

```
$ PGPASSWORD=… psql -h 127.0.0.1 -p 5433 -U lqg -d postgres \
    -c "DROP DATABASE IF EXISTS lqg_dev;" -c "CREATE DATABASE lqg_dev OWNER lqg;"
DROP DATABASE
CREATE DATABASE
$ bash .tmp/run-backend.sh     ← 受管后台作业，空库起后端让 Flyway 跑全量
  Successfully validated 14 migrations (execution time 00:00.011s)
  Migrating schema "public" to version "202609231000 - SAMPLE-EXPORT-001-sample-export-perm"
  Successfully applied 14 migrations to schema "public", now at version v202609231110
$ # flyway_schema_history（按 installed_rank）
  202609210800 … 202609221010 → 202609230902 → **202609231000** → 202609231100 → 202609231110（14 支全 success=true）
```

版本号升序排列，空库端到端跑通；随后 `bash doc/verify/reseed.sh --yes` 回确定性快照。

---

## §2 改了哪些文件（ticket §4.1）

### 2.1 后端 · 新增（`org.dromara.lqg.sample.export` 包，全部在 `touches` 的 `sample/export/**` 里）

| 文件 | 职责 |
|---|---|
| `sample/export/SampleTissueExportVo.java` | ★ **导出专用 VO**（不是 `SampleVo`）：14 个 `@ExcelProperty(value=…, index=…)`，文字与顺序照 `_input/templates/样本记录信息表模板.xlsx` 第 1 行；`@ExcelIgnoreUnannotated` 保证不多带列 |
| `sample/export/SampleOrganoidExportVo.java` | ★ 第二个导出专用 VO：7 列照 `_input/templates/类器官收样记录模板.xlsx`；**列集与 tissue 不同**（没有供体段 / 固定 / 质控表 / 备注） |
| `sample/export/SampleExportService.java` | 导出编排 + 一套纯函数格子判据（`flagText` / `genderText` / `dateText` / `dateTimeText`）+ 两张表的 `headerIndex()` 自检表；**放在 service 层**供小程序 `/mp/int/export/{tissue,organoid}`（SYS-EXPORT-001）复用 |
| `sample/export/SampleExportController.java` | `POST /lqg/sample/export/tissue` 与 `/organoid`，`@SaCheckPermission("lqg:sample:export")`，`SampleQueryBo query` + `HttpServletResponse` |

**★ 为什么必须是导出专用 VO**（accept 1 counterfeit 第 1 条）：直接拿 `SampleVo` 导，表头会是「内部编号 / 送检单号 / 来源单位 / 类别 / 来源 / 核验状态 / … / 最后修改」，多出送检单号、核验状态、有无病理、提交人、组别等列，与甲方模板逐字不等红。反证见 §4.4（全表扫描：表头无这些列）。

**★ 为什么单独一个 controller**：ticket 的 `touches` 只给了 `sample/export/**`；把端点与导出视图放同一个包，既满足「导出逻辑放 `SampleExportService`、别写死在 controller 里」，也**不动 SAMPLE-MODEL-001 建的 `SampleController`（一个字节没改）**。

### 2.2 后端 · 修改（一处，**不在 `touches` 里** → WARN-1）

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `sample/service/SampleQueryService.java` | +`exportRows(SampleQueryBo)`（与 `list` **同一份 `buildWrapper`、同一条档案口径、同一份 `toVo` 装配**，只是走 `selectList` 不分页） | 「带筛选导出只出筛选结果」「导出行数与列表 `total` 一致」两条口径只能落在**同一份读侧**；在 `sample/export/**` 里另写一份 WHERE —— 或者复制一份 wrapper 拼装 —— 正是 counterfeit 点名的形态。**只新增方法，既有方法签名 / 行为一字未改** |

### 2.3 后端 · 新增契约测试（5 例，不启 Spring 容器）

`sample/export/SampleExportContractTest.java`：

1. 两张导出视图的**列数 / 列名 / 列序**，且**读甲方模板原件第 1 行两侧对账**（tissue 14 / organoid 7）
2. **导出视图不是列表 VO**：不许出现「送检单号 / 核验状态 / 有无病理 / 提交人 / 组别 / 最后修改 / 类别」；且两张表的列集**必须不同**
3. **按钮列 `Y→有 / N→无`、没选留空**；**性别 `male→男 / female→女 / unknown→未知`**；显式断言 `flagText("Y") != "Y"`
4. **日期 / 时间格**：收样日期 `yyyy-MM-dd`、处理时间 `yyyy-MM-dd HH:mm:ss`、空 → 空格子
5. `headerIndex()` 与 `@ExcelProperty` 注解一致（给小程序导出复用）

> 读模板用 JDK 自带 `ZipFile` + DOM（★ `setNamespaceAware(false)`）：本模块**测试**类路径里 POI 与 commons-io 版本不配套（`NoSuchMethodError: BoundedInputStream.builder()`），为一个表头对账去动 `pom.xml` 是拿全模块编译风险换一条断言（EMBED-WEB-001 的结论）。

### 2.4 前端 · 新增（`code/plus-ui`）

| 文件 | 内容 |
|---|---|
| `src/api/lqg/sample/export.ts` | `exportTissueSamples(query)` / `exportOrganoidSamples(query)`：`POST` + `params: query` + `responseType: 'blob'`（与 `api.sh` 的 `POST '/lqg/sample/export/tissue?…'` 同一形状） |

### 2.5 前端 · 修改（三处）

| 文件 | 改动 |
|---|---|
| `src/views/lqg/sample/index.vue` | 工具栏两个导出按钮从 `SAMPLE-WEB-001` 的「点了提示下一个任务接入」改成**真导出**：`v-hasPermi="['lqg:sample:export']"` + `:loading="exporting"` + `@click="exportTissue" / "exportOrganoid"`；新增 `buildExportQuery()`（把 `receiveDateRange` 落进 queryParams 后原样带上当前筛选）、`downloadExport()`（blob → `a[download]`，空文件给提示而不是下 0 字节 xlsx）、`exportTissue` / `exportOrganoid` |
| `src/lang/lqg/sample.zh_CN.ts` / `.en_US.ts` | toolbar 段：删 `exportNotYet`，加 `exportTissueFile / exportOrganoidFile / exportTissueDone / exportOrganoidDone / exportEmpty`（**没往上游 `zh_CN.ts` / `en_US.ts` 大文件加 key**；两本 key 集合实测 119 = 119） |

> ★ **两个导出按钮必须带当前筛选**：日期区间住在本地 `receiveDateRange` ref 里 —— 导出前不落进 `queryParams`，「按收样日期区间筛出来再导出」会静默导成不带日期条件的全量。UI 证据见 §5。

---

## §3 接口清单与口径落点

```
POST /lqg/sample/export/tissue      按当前筛选导出「样本记录信息表」xlsx（14 列）   lqg:sample:export
POST /lqg/sample/export/organoid    按当前筛选导出「类器官收样记录」xlsx（7 列）     lqg:sample:export
     · 筛选走 **query / 表单参数**，与 GET /lqg/sample/list 同一个 SampleQueryBo
     · 表头 14 / 7 列逐字同序照甲方模板原件；响应 = xlsx 文件流
     · sampleKind 由**端点决定**（/tissue 恒导 tissue 类、/organoid 恒导 organoid 类，调用方传的值被覆盖）
     · 待核验 / 无效的样本也导（内部编号等空格子）；del_flag='1' 的不导
```

与 `doc/api-contract.md` 第 48 行 **逐字对齐**（`POST /lqg/sample/export/tissue`、`POST /lqg/sample/export/organoid` —— 「按当前筛选导出 xlsx（表单参数同 list）」），**没有改契约**（无 doc-drift）。

契约第 55 行要求「表头列序与 `/lqg/sample/export/*` … **同一个导出视图**」→ 本票把视图放在 `SampleExportService.tissueRowsOf / organoidRowsOf`（public，不碰 `HttpServletResponse`），小程序侧直接调它。

**口径落点（逐条对 ticket §0 的复述）**：

| 口径 | 落在哪 | 机器证据 |
|---|---|---|
| 两张导出是同一张样本表的两个视图：tissue 14 列 / organoid 7 列；**有无病理不进导出** | 两个导出 VO 的字段集 | §4.1 ①、§4.4 ①② |
| 按钮字段「有 / 无」、没选留空；性别导中文 | `SampleExportService.flagText / genderText` | §4.1 ①、§4.4 ③④ |
| 供体姓名 / 住院号导**明文** | 只消费 `exportRows` 已解密的 `SampleVo` | §4.1 ①、§4.4 ⑤ |
| 导出范围 = 当前筛选结果，与列表**同一份 where** | `SampleQueryService.exportRows` → `buildWrapper` | §4.1 ④、§4.4 ⑧（六组筛选逐一对照 `list.total`） |
| 软删样本不导 | 实体 `@TableLogic`（不写任何原生 SQL） | §4.1 ②、§4.4 ② |
| 外部角色不能导出 | `@SaCheckPermission("lqg:sample:export")` + 5217 只授 101/102 | §4.1 ⑤、§4.4 ⑨ |

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（既有 WARN，不重复计数）

ticket accept 1 的 `run` 第 2 段带 `--fresh-module ruoyi-lqg`。本 subagent 沙箱里它**恒非 0 退出**：

```
$ bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --out /tmp/lqg-tissue.xlsx POST /lqg/sample/export/tissue
doc/verify/api.sh: line 71: /bin/ps: Operation not permitted
date: illegal option -- d
（exit 1）
```

`api.sh` 第 71 行用 `ps -o lstart=`，沙箱里 `/bin/ps` 被禁，回退的 `date -d` 在 macOS 上不存在 → 守卫自己挂了（**既有 WARN，8+ 张上游报告记过**）。按规矩**没有改 `api.sh`**（只读区），runner 里**只去掉这一个 flag**（其余逐字相同），并用等价反 stale 取证覆盖那道守卫的两个半边：

```
$ bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-fresh-evidence.sh
jar mtime               : 2026-09-22 09:22:04（epoch 1790040124）
lqg src newer than jar  : 0 个文件     ← find ruoyi-lqg/src -newer <jar> 为空
admin src newer than jar: 0 个文件
8081 PID                : 53332
该 PID 持有 jar 的行数  : 2
进程启动                : 2026-09-22 09:22:08（≥ jar 09:22:04 → True）
后端日志                : Successfully validated 14 migrations；Started DromaraApplication
嵌套 jar                : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 sample/export/ 9 个条目
```

Maven 实跑按规矩带了 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`；两条 `run` 本身都没有 Maven 行。两条 run 都是长链，**包进函数判整条 rc**（不丢给 `set -e`）。

### 4.1 accept 1 · DATA —— ✅

```
$ bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-acc1-export.sh
✓ 表头 14 列与模板逐字一致；数据 8 行          ← 全量 tissue + T-hli01 那行逐格（10 格）
✓ 表头 14 列与模板逐字一致；数据 8 行          ← --rows = db 的 del_flag='0' AND sample_kind='tissue'
✓ 表头 7 列与模板逐字一致；数据 1 行           ← organoid 全量 + T-oco01 三格
✓ 表头 14 列与模板逐字一致；数据 1 行          ← ?sourceUnitId=9000009002，行数 = 列表 total
ACCEPT-1 EXIT=0
```

逐段对应（原文 9 段 `&&` 链）：

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `reseed` + `rm -f /tmp/lqg-tissue.xlsx …` | （静默） | 先删旧文件：读到上一次导出的文件骗不过去 |
| 2 | `POST /lqg/sample/export/tissue` | 4619 字节 xlsx | 全量导出，8 行 |
| 3 | `--rows 8 --find 内部编号=T-hli01 --expect 来源单位=A 医院,供体姓名=测试供体甲,性别=男,年龄=56,住院号=ZY0000001,组织类型=肝组织,有无固定=有,质控表=有,细胞活率报告=有,操作人=李工` | ✓ 8 行 | ★ 十格逐字：来源单位、**明文**供体姓名、中文性别、年龄、**明文**住院号、组织类型、三个「有」、操作人 |
| 4 | `--rows $(db.py count(*) WHERE del_flag='0' AND sample_kind='tissue')` | 8 / 8 | ★ 行数 = 未删的 tissue 类；**软删的 1010（T-del99）与类器官 1009 都不在** |
| 5 | `POST /lqg/sample/export/organoid` | 1 行 | 全量 organoid |
| 6 | `--rows 1 --find 内部编号=T-oco01 --expect 来源单位=B 大学,类器官类型=结直肠类器官,细胞活率报告=有` | ✓ 1 行 | ★ 三格逐字（organoid 没有供体段 / 备注） |
| 7 | `POST '/lqg/sample/export/tissue?sourceUnitId=9000009002'` | 1 行 | 带筛选导出 |
| 8 | `--rows "$(list?sourceUnitId=9000009002&sampleKind=tissue&pageSize=100 \| jq .total)"` | 1 = 1 | ★ 与列表**同一口径** |
| 9 | `--as extA --bizcode POST /lqg/sample/export/tissue \| grep -qE '^403'` | `403 没有访问权限，请联系管理员授权` | ★ 外部角色挡住 |

**导出文件的两个视图（真实格值，`accept-runners/accept-transcript.txt` 同源）**：

```
样本记录信息表（14 列 × 8 行；顺序 = 待核验置顶 + create_time DESC）
来源单位 | 供体姓名 | 性别 | 年龄 | 住院号 | 组织类型 | 收样日期 | 内部编号 | 有无固定 | 处理时间 | 质控表 | 细胞活率报告 | 操作人 | 备注
A 医院 | 测试供体庚 | 男 | 52 | ZY0000007 | 肝组织 |  |  |  |  |  |  |  |         ← pending：还没填的格子留空
A 医院 | 测试供体乙 | 女 | 48 | ZY0000002 | 胆管组织 |  |  |  |  |  |  |  |
A 医院 | 测试供体丙 | 未知 |  |  | 肝组织 |  |  |  |  |  |  |  |        ← invalid：unknown → 未知
B 大学 | 测试供体己 | 男 | 70 | ZY0000006 | 结直肠组织 | 2026-09-04 | T-hco04 | 有 | 2026-09-04 10:00:00 | 有 | 有 | 李工 |
A 医院 | 测试供体戊 | 女 | 39 | ZY0000005 | 胃组织   | 2026-09-02 | T-hga03 | 有 | 2026-09-02 10:00:00 | 有 | 有 | 李工 |
A 医院 | 测试供体丁 | 男 | 61 | ZY0000004 | 肝组织   | 2026-08-28 | T-hli02 | 有 | 2026-08-28 09:40:00 | 有 | 有 | 李工 |
A 医院 | 测试供体甲 | 男 | 56 | ZY0000001 | 肝组织   | 2026-08-23 | T-hli01 | 有 | 2026-08-23 14:20:00 | 有 | 有 | 李工 |
本中心 | 测试供体辛 | 女 | 45 | ZY0000008 | 肝组织   | 2026-08-08 | T-hli05 | 有 | 2026-08-08 10:00:00 | 有 | 有 | 李工 |

类器官收样记录（7 列 × 1 行）
来源单位 | 类器官类型 | 细胞活率报告 | …（收样日期 / 内部编号 / 处理时间 / 操作人）
B 大学   | 结直肠类器官 | 有 | 2026-07-24 | T-oco01 | 2026-07-24 10:00:00 | 李工
```

（注：`T-del99`（软删的 1010）与 `T-oco01` 都不在 tissue 文件里 —— §4.4 探针逐条断。）

### 4.2 accept 2 · MENU —— ✅

```
$ bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-acc2-menu.sh
5217:lqg:sample:export:5210
101
102
sample
true
true
ACCEPT-2 EXIT=0
```

| 段 | 命令 | 真实输出 | 说明 |
|---|---|---|---|
| 1 | `SELECT menu_id\|\|':'…\|\|parent_id WHERE perms='lqg:sample:export'` `--eq "5217:lqg:sample:export:5210"` | `5217:lqg:sample:export:5210` | ★ 出自本票迁移（`V202609231000`），parent 是 5210 |
| 2 | `SELECT role_id FROM sys_role_menu WHERE menu_id=5217` `--col-set 101,102` | `101` / `102` | 授给 lqg_admin 与 lqg_internal |
| 3 | `SELECT path FROM sys_menu WHERE menu_id=5210` `--eq sample` | `sample` | ★ 本票迁移**没碰** 5210（路由可达性底线） |
| 4 | `getRouters`（`--as staff`）`jq '[.. \| objects \| select(.component? == "lqg/sample/index")] \| length == 1'` | `true` | ★ 5217 是 F 按钮，没有造出第二条路由 |
| 5 | `getInfo` `.data.permissions \| index("lqg:sample:export") != null` | `true` | ★ 权限串真的下发到了普通内部人员（不是「后端加了注解、菜单没 seed」→ 点了 403） |
| 6 | `grep -q 'export/tissue' …/export.ts && grep -q 'export/organoid' …/export.ts` | 命中 | 两个端点的前端函数都在 |
| 7 | `grep -cE 'exportTissue\|exportOrganoid' …/index.vue \| awk '{exit !($1 >= 2)}'` | `9` 行 | ★ 页面真的接上了（import 1 + buildQuery 0 + downloadExport 0 + 两个 const 2 + 模板 2 + 注释/调用 4） |

**counterfeit 逐条排掉**：

| counterfeit | 本实现为什么不中 |
|---|---|
| 后端加了 `@SaCheckPermission("lqg:sample:export")` 却没 seed 按钮 → 普通内部人员 403 | 5217 有这一行且授了 101/102；段 5 实测权限串在 `getInfo` 里；§4.5 用 lqgadmin 真点出两个非空 xlsx |
| 按钮还留着 `SAMPLE-WEB-001` 的「下一个任务接入」提示 | `exportNotYet` key 与 `handleExportNotYet` 已删；工具栏按钮是 `@click="exportTissue" / "exportOrganoid"` |
| 父菜单 5210 的 `path` 被本票迁移误改成空 | 段 3 = `sample`；迁移只 `INSERT … 5217`，**没有任何 UPDATE / DELETE** |

### 4.3 追加 · Java 单测：`Tests run: 103, Failures: 0, Errors: 0, Skipped: 0`

```
$ mvn -pl ruoyi-modules/ruoyi-lqg -am install -s … -Dmaven.repo.local=… -Duser.home=…
[INFO] Running org.dromara.lqg.sample.export.SampleExportContractTest   Tests run: 5, Failures: 0   ← 本票新增
[INFO] Running org.dromara.lqg.sample.query.SampleTableQueryContractTest Tests run: 9, Failures: 0
[INFO] Running org.dromara.lqg.ext.ExtChokepointContractTest            Tests run: 4, Failures: 0   ← L0 咽喉门仍绿
[INFO] Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
（逐类清单见 accept-runners/java-test-transcript.txt）
```

### 4.4 追加 · counterfeit 探针（accept 之外，`accept-runners/counterfeit-transcript.txt`）

```
$ bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-counterfeit-probes.sh
== ① 全表扫描 ==
tissue：8 行 × 14 列
  ✓ 表头没有列表 VO 才有的列（送检单号, 核验状态, 有无病理, 提交人, 组别, 最后修改, 类别）
  ✓ 类器官那条（T-oco01）没有混进 tissue 导出
  ✓ 软删的 1010（T-del99）没有导出
  ✓ 按钮列只有「有 / 无」：['有']
  ✓ 没选的「有无」按钮留空（空格子数 9）
  ✓ 性别列只有 男/女/未知：['女', '未知', '男']
  ✓ 「供体姓名」是明文（没有裸 Base64 密文）：['测试供体庚', '测试供体乙', '测试供体丙']…
  ✓ 「住院号」是明文（没有裸 Base64 密文）：['ZY0000007', 'ZY0000002', 'ZY0000006']…
organoid：1 行 × 7 列（同上，另加「组织样本 T-hli01 没有混进 organoid 导出」）
== ② 类别由端点决定（传反了也不串） ==
  /export/tissue?sampleKind=organoid    → 行数 8（期望 8 = tissue 全量）
  /export/organoid?sampleKind=tissue    → 行数 1（期望 1 = organoid 全量）
== ③ 导出与列表同一份 where（行数 == 列表 total） ==
  tissue?sourceUnitId=9000009001                       导出 6 / total 6
  tissue?verifyStatus=pending                          导出 2 / total 2
  tissue?tissueType=肝                                  导出 5 / total 5
  tissue?receiveDateBegin=2026-08-20&receiveDateEnd=2026-09-05  导出 4 / total 4
  tissue?operatorName=李&submitSource=external          导出 4 / total 4
  organoid?sourceUnitId=9000009002                     导出 1 / total 1
== ④ 外部 403；匿名 401（未认证） ==
  extA / extB / extA×两个端点 → 403   anon → 401
COUNTERFEIT PROBES  PASS
```

**逐条对 ticket 的 `counterfeit`**：

| counterfeit | 探针结论 |
|---|---|
| 导出 VO 复用列表 VO → 多出送检单号 / 核验状态 / 有无病理 | ① 表头禁用列清单在；全文件扫描无这些列 |
| 按钮字段导出了 Y / N | ① 「有 / 无」；用例 ③ 显式断 `flagText("Y") != "Y"` |
| 性别导出了 male | ① 只有 `男/女/未知`；用例 ③ 断 `genderText("male") != "male"` |
| 导出时没解密 → 一格是 Base64 密文 | ① 两个加密列全表扫描无「16 字节块 base64」形态 |
| tissue 导出把类器官那条也带上 → 9≠8 | ① 无 `T-oco01`；② 行数 8 |
| 软删的 1010 被导出 | ① 无 `T-del99`；accept 1 段 4 行数 = 8 |
| 带筛选导出另写一套 where、漏了 sourceUnitId | ③ 六组筛选逐一与 `list.total` 相等 |
| 外部角色能调导出 | ④ 两个端点 × extA/extB 全 403；accept 1 段 9 |

### 4.5 追加 · UI DOM / 网络证据（截图只落盘，全程没有把任何 PNG 读进上下文）

`accept-runners/sampleexp001-shots.mjs`（puppeteer-core + 本机 Google Chrome，headless；8082 dev server）：

```
[sampleexp001] toolbar {"url":"/sample","title":"样本总表",
  "toolbarButtons":["新增样本记录","导出样本记录信息表","导出类器官收样记录"],
  "hasExportTissue":true,"hasExportOrganoid":true,"exportTissueDisabled":false,"exportOrganoidDisabled":false,"switchCount":0}
[sampleexp001] filtered {"rowCount":1,"firstInternalNo":"T-hli01","total":"共 1 条"}
[sampleexp001] export requests [
  {"method":"POST","url":"…/lqg/sample/export/tissue?pageNum=1&pageSize=10&internalNo=T-hli01&operatorName=%E6%9D%8E"},
  {"method":"POST","url":"…/lqg/sample/export/organoid?pageNum=1&pageSize=10&internalNo=T-hli01&operatorName=%E6%9D%8E"}]
[sampleexp001] export responses [{"status":200,"contentType":"…spreadsheetml.sheet","bytes":4087…},{"…","bytes":3804…}]
✓ 页面上两个导出按钮都在
✓ 点「导出样本记录信息表」发出 POST /lqg/sample/export/tissue
✓ 点「导出类器官收样记录」发出 POST /lqg/sample/export/organoid
✓ tissue 导出带着当前筛选 internalNo=T-hli01
✓ tissue 导出带着当前筛选 operatorName=李
✓ organoid 导出带着当前筛选 internalNo=T-hli01
✓ 两个导出各拿到一个非空 xlsx（HTTP 200）
```

| 文件 | 覆盖项 | 实测断言 |
|---|---|---|
| `01-sample-list-with-export-buttons.png` | 样本总表工具栏 | 三个按钮文本；两个导出按钮 `disabled=false`；`switchCount: 0` |
| `02-sample-list-filtered.png` | 按「内部编号=T-hli01 + 操作人=李」筛过后 | 1 行、首行内部编号 `T-hli01`、分页 `共 1 条` |
| `probe-toolbar.json` / `probe-filtered-list.json` / `probe-export-requests.json` | 上面三段的机器可读版本 | 见上 |

**未覆盖（如实写明）**：导出的 **Excel 视觉样式**（列宽 / 字体 / 冻结）没有比对 —— accept 只钉表头与单元格文本；`/mp/int/export/{tissue,organoid}` 端到端不在本票（SYS-EXPORT-001），本票只把视图放在 service 层供它复用（`tissueRowsOf` / `organoidRowsOf` 是 public、不碰 `HttpServletResponse`）。

---

## §5 回归自查

完整转录：`accept-runners/regression-transcript.txt`（runner：`sampleexp001-regression.sh`）。

| 回归 | 结果 | 说明 |
|---|---|---|
| `EMBED-WEB-001` accept 1 | **`ACCEPT-1 EXIT=0`** | 16 列表头 + 5/2/4 行，四条筛选段全绿（本票没碰 embed 域） |
| `EMBED-WEB-001` accept 2 | **`ACCEPT-2 EXIT=0`** | 5310 段七个 perms 一字未动 |
| `SAMPLE-WEB-001` accept 1 | **`ACCEPT-1 GREEN (exit 0)`** | 10 段全绿：`SampleQueryService` 只**新增**了 `exportRows`，`list` 的十个筛选与「待核验置顶」逐条不变 |
| `SAMPLE-WEB-001` accept 2 | **`ACCEPT-2 RED (exit 1)`** ← ★ **已知规格冲突，非本票引入**（见 §5.2） | 段 1/2/3/5/6/7 全绿；**只有段 4** 因为本票按 ticket §2 新增 5217 而红 |
| `SAMPLE-MP-002` accept 2 | **`ACCEPT-2 EXIT=0`** | 表格页 fixture 仍绿（本票没碰 mp 侧） |
| `ExtChokepointContractTest` | **`Tests run: 4, Failures: 0`** | 本票新代码不在 `ext` 包（ADR-0004 的 I4 不变量没被削弱） |
| `bash doc/waves/regression/D1/verify.sh --skip-build` | **41 绿 / 1 红 / 0 环境错，rc=1** | 唯一红 = 既有假红（见 §5.1）；**L0.2 那条 SIGPIPE 假红已被主会话修掉，本次实测为绿** |

### 5.1 D1 回归包唯一那条红（既有，本票不重复计数）

```
  ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '14' 14
```

`SELECT count(*) FROM flyway_schema_history --eq 7` 这条**写死总数**的断言：D1 的 7 支逐个点名全绿、无失败行（`success IS NOT TRUE` 的行 = 0）；D2 +3 → 10、EMBED-MODEL-001 +1 → 11、AUTH-EXT-002 +1 → 12、EMBED-WEB-001 +1 → 13、**本票 +1 → 14**。state.json / 上游报告都记过，本票不重复计数。

### 5.2 ★ `SAMPLE-WEB-001` accept 2 段 4 的红 —— **已知规格冲突，非本票引入**（主会话已裁定）

> **裁定（主会话 @ 2026-09-22）**：导出权限是 **REQ-SAMPLE-011 / REQ-SAMPLE-012** 的要求、归 `SAMPLE-EXPORT-001` 所有；`SAMPLE-WEB-001` accept 2 段 4 那句「5210 下 perms **精确等于六个**」是写早了、过紧的断言。**正确现状就是「六个 + `lqg:sample:export`」这一条**，本票实现是对的。已记 issue **#129**；**不**改 `doc/tickets/SAMPLE-WEB-001/prompt.md`（需求层资产，由主会话 / ① 处理）；待 ① 放宽该断言后该段即绿。D3 的 QA 门会把这条当「已知规格冲突」告知分片，**不据此判产品红**。

```
$ bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc2-menu.sh
---- 1) 菜单 5210 落在 5200 段（C，path/component 逐字）     5210:sample:lqg/sample/index   ✓
---- 2) 授给 101 与 102                                      101 / 102                      ✓
---- 3) getRouters 里 lqg/sample/index 恰好 1 条             true                            ✓
---- 4) 5210 下的按钮权限串集合
[FAIL] 集合不相等：多出 ['lqg:sample:export']，缺少 []
---- 5/6/7) SegButtons / 抽屉计数 / 无开关下拉                全绿
ACCEPT-2 RED (exit 1)
```

- 该段断的是 `SELECT perms FROM sys_menu WHERE parent_id = 5210 AND menu_type='F'` **精确等于六个**；
- 而**本票 accept 2 段 1 强制**：5217 / `perms='lqg:sample:export'` / `parent_id=5210`（段 2 还要求授 101、102）；
- 两条 accept **在任何满足本票 accept 2 的实现下都不可能同时绿**（`--col-set` 是精确集合，`db.py` 是只读区）；按上面的裁定，**要改的是那句过紧的断言，不是本票实现**。
- 本票**没有改它**（accept 是需求层资产，也不在 `touches` 里）。建议的最小修法：把该段改成「包含这六个」或加一句 `AND perms <> 'lqg:sample:export'`（EMBED 域的同类操作——把 5300 段搬成 5310——当时**没有**踩到，因为 EMBED-MODEL-001 的 accept 里没有「perms 精确集合」这条）。
- 影响面：**只影响回归包**，不影响本票两条 accept、不影响任何运行时鉴权（5211-5216 六个 perms 逐字未动）。

---

## §6 遗留与 raise（ticket §4.3）

### 6.1 越出 `touches` 的改动（逐条列清）

| # | 文件 | 为什么非改不可 | WARN |
|---|---|---|---|
| 1 | `db/migration/V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql` | **ticket 的 `touches` 里没有 `db/migration/**`**，但 accept 2 段 1/2 断「`sys_menu` 里 `menu_id=5217`、`perms='lqg:sample:export'`、`parent_id=5210`，且出自本票迁移，并授给 101/102」——ticket §2 也明文点名这支迁移。`SAMPLE-WEB-001` 只建到 5216，5217 是空的；少了它两个导出端点会**静默全 403**（`@SaCheckPermission` 缺 `sys_menu` 行是 403 不是 500） | **WARN-1** |
| 2 | `sample/service/SampleQueryService.java` | 「导出与列表同一口径」按 ticket 只能落在同一份 `buildWrapper` + 同一条档案口径 + 同一份 `toVo` 装配上；只**新增** `exportRows`（不分页），既有方法签名与行为一字未改 | **WARN-3** |

两处都是**新增文件 / 新增方法**：`SampleController`、`SampleVo`、`SampleQueryBo`、`SampleFieldCipher`、`SampleService`、`sample.mapper` **一个字节没动**。

### 6.2 没做的（边界，ticket §3）

- 石蜡包埋导出（`EMBED-WEB-001` 已做）、冻存导出（`CRYO-WEB-001`）、小程序端四张表导出（`SYS-EXPORT-001`，D7）—— 均明确不做。
- **不给外部任何导出**：两个端点都只授 101/102；实测 extA / extB 403。

### 6.3 与权威 / 契约不一致的地方

- `doc/api-contract.md` 第 48 行的两个导出路径与实现**逐字对齐**（**没有改契约**，无 doc-drift）。
- 契约第 55 行「同一个导出视图」→ 视图放在 `SampleExportService`（service 层），满足。
- 契约没写「导出 14 / 7 列的具体列名」→ 以甲方模板原件为准（ticket §0 明文），实现与它逐字对账。
- 契约没写「`sampleKind` 由端点决定」→ 是本票的实现决定（否则 7 列视图会拿 14 列表头导组织样本）；已写进 controller / service 的类注释。

### 6.4 没把握的口径（交给 CR 或票决）

1. **「处理时间」导 `yyyy-MM-dd HH:mm:ss`**（不是只导日期）：`process_time` 落库是 `timestamp`，抽屉 / 小程序都用 `type="datetime"` 填；accept 没钉这一格（只钉了收样日期之外的十格）。
2. **性别 `unknown → 未知`**：`lqg_gender` 的 label 就是这个（`doc/authority/field-ssot.yaml`），与工作台列表的 `dict-tag` 同源。ticket 只说「男 / 女」，没说第三个值 —— 按字典导「未知」而不是留空。
3. **`/export/tissue?sampleKind=organoid` 会被端点覆盖成 tissue**：由端点决定视图这件事必须在服务端钉死，否则「两列视图同一张表」守不住。若甲方要「传错类别就 400」，需另记一条口径。
4. **`pageNum / pageSize` 在导出请求里原样带着**（后端忽略，走 `selectList`）：前端没有剥掉（避免 TS 未用变量 / `undefined` 参数的序列化歧义）。

---

## §7 坑与解法（给下游，3-5 行）

1. ★ **跨域补号的迁移只能靠重建库**：`V202609231000` < 已应用的 `V202609231110`，`out-of-order=false` 下已有库启动即 `FlywayValidateException`。`DROP DATABASE lqg_dev; CREATE DATABASE lqg_dev OWNER lqg;` → 空库按版本号升序跑全量（本票实测 14 支全绿），**别改 out-of-order**（prod 语义要保持）。收尾必须再跑一次 `reseed.sh --yes`。
2. ★ **两条导出的数据源只能是 `SampleQueryService.exportRows`**：与 `list` 共用 `buildWrapper`（含 `@TableLogic` 的 `del_flag='0'`、组别→外部档案的 id 集合、加密列 `eq` 前的 `encrypt`）与同一份 `toVo`（**解密**）。绕过实体直接查 Map / 原生 SQL，导出的就是裸 Base64 密文（accept 的 `供体姓名=测试供体甲` 会红），而且「带筛选导出」会与列表分叉。
3. ★ **加密列的导出值只能来自 `SampleVo`**：`SampleFieldCipher` 出的密文每次相同（AES/ECB），所以明文一格、密文一格在文件里长得完全不一样 —— 探针 `probe_cells.py` 用「能 base64 解码且长度是 16 的倍数」来抓密文，比人眼看更可靠。
4. ★ **`menu_type='F'` 的权限行缺失是 403 不是 500**：新加 `@SaCheckPermission("<域>:<资源>:export")` 之前先确认 `sys_menu.perms` 有这一行（本票就是 5217），否则两个端点会「静默全 403」，日志里什么都不报。反过来，**往别人建的父菜单下加按钮会打红「父菜单下 perms 精确集合」这类快照断言**（本票踩到 `SAMPLE-WEB-001` accept 2 段 4，见 §5.2）—— 加按钮前先 grep 一遍上游 accept 有没有数集合。
5. **类别由端点决定、不由调用方决定**：`/export/tissue` 里强制 `sampleKind='tissue'`（`forceKind`），否则 `?sampleKind=organoid` 会用 7 列的表头导组织样本；「不带参数时行数 = 该类未删总数」这条（accept 1 段 4）也只能靠它。

---

## §8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | ticket-drift | ticket 的 `touches` 里**没有 `db/migration/**`**，但 accept 2 强制「5217 的权限行**出自本票迁移**」并要求 `lqg:sample:export` 授给 101/102 | 本票新增一支 `V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql`（只 `INSERT 5217` + 两条 `role_menu`，无 UPDATE/DELETE），并在 §6.1 显式记账。方案：ticket 生成器把 `db/migration/V*__<ticket>-*.sql` 也写进 `touches`（AUTH-EXT-002、EMBED-WEB-001、SYS-WEB-001 已多次同型命中）。**这同时解释了「202609231000 < 已应用最大值」为什么要重建库**。 |
| WARN-2 | **S2**（主会话裁定后降级；已记 **issue #129**） | accept-conflict | **`SAMPLE-WEB-001` accept 2 段 4 与本票 accept 2 不可同时满足**：前者断「5210 下的 F 权限集合精确等于六个」，后者强制新增 5217（`lqg:sample:export`，parent 5210）。任何满足本票 accept 2 的实现都必然打红它。实测 `[FAIL] 集合不相等：多出 ['lqg:sample:export']`。 | **★ 已由主会话裁定**：导出权限是 **REQ-SAMPLE-011 / REQ-SAMPLE-012** 的要求、归本票所有；`SAMPLE-WEB-001` 那句「精确等于六个」是**写早了、过紧的断言**。**正确现状 = 六个 + `lqg:sample:export`**，本票实现是对的 → **不改本票实现**。**只影响回归包**，不影响运行时鉴权（5211-5216 逐字未动）与两条本票 accept。**没有改上游 ticket**（需求层资产，不在 touches，由主会话 / ① 处理）。待办：① 放宽该断言（改成「包含这六个」/ 加 `AND perms <> 'lqg:sample:export'`），放宽后该段即绿。D3 QA 门已知情，不据此判产品红。 |
| WARN-3 | S2 | ticket-drift | `touches` 只列了 `sample/export/**` 与前端的 4 个路径，但「导出与列表同一口径」必须落在 `sample/service/SampleQueryService.java`（+`exportRows`） | 本票只新增一个方法（不分页），既有签名 / 行为未改，§6.1 已记账。方案同 WARN-1：把「读路径所在文件」写进 `touches`（SAMPLE-WEB-001 / EMBED-WEB-001 / SAMPLE-MP-001 / AUTH-EXT-001 / SAMPLE-VERIFY-001 已五次同型命中）。 |
| WARN-4 | S3 | clarify | 「处理时间」这一格的格式没被任何 accept 钉住：本票导 `yyyy-MM-dd HH:mm:ss`（`process_time` 是 timestamp，抽屉与小程序都按 `datetime` 填），组织样本与类器官两张表同格式 | 甲方若希望「处理时间」只到日，改 `SampleExportService.dateTimeText` 一处即可（两张表共用）。方案：在 `doc/authority/field-ssot.yaml` 的 `process_time` comment 或 `UI:admin.sample.list` 里写明导出格式。 |
| WARN-5 | S3 | clarify | 性别第三个值 `unknown` 本票导「**未知**」（字典 `lqg_gender` 的 label，与工作台列表的 `dict-tag` 同源），ticket §0 只写了「男 / 女」 | 若甲方要求「未知留空」，改 `SampleExportService.genderText` 一处。方案：在 ticket 口径里补一句。 |
| WARN-6 | S3 | harness（既有，本票不重复计数） | `api.sh --fresh-module` 在本 subagent 沙箱恒非 0 退出（`/bin/ps: Operation not permitted` + macOS 无 `date -d`）+ Maven 需带三参数 | 已连续 9+ 张命中。本票用「`find -newer jar` 为空 + `lsof` 拿 PID + 进程持该 jar + 进程启动 ≥ jar + 日志 mtime 晚于 jar + 嵌套 jar 内含新 class」六项等价替代（§4.0 / `sampleexp001-fresh-evidence.sh`）。方案同上游：守卫换成 `lsof` / 日志 mtime 的兼容写法，或加「跳过启动时间守卫」的开关。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：D1 回归包把 flyway 迁移总数写死成 7、`db.py` 是只读执行器却 exit 0、`reseed.sh` 清不掉运行时账号（收尾跑 `clean-orphan-accounts.sh`）、surefire `groups` 假绿、`PageQuery` 只有两参构造、`updateById` 忽略 null、MyBatis-Plus 无按列名排序重载、跨域补号的 Flyway 坑、`@SaCheckPermission` 缺 `sys_menu` 行是 403。

---

## §9 收尾（长进程 / 端口 / DB / 提交）

```
$ for p in 8080 8081 8082 8083 8099; do printf '%s: ' $p; lsof -ti tcp:$p -sTCP:LISTEN | tr '\n' ' '; echo; done
8080: （未碰，Kevin 的本机服务；本票全程没对它做任何操作）
8081: （空）   ← 本票后端，收尾按 PID 关掉
8082: （空）   ← 本票 plus-ui dev server，收尾按 PID 关掉
8083 / 8099: （空）
```

- **关进程一律按 PID**：`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`（**从未**用过 `pkill -f`，也从未 `kill -9`）
- **DB 收尾**：`doc/verify/reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`
- **docker 容器**：`lqg-dev-postgres`(5433) / `lqg-dev-redis`(6380) / `lqg-dev-minio`(9002+9003) 全程在跑，**没停**（留给后续 ticket）
- **`git checkout -- code/plus-ui/.eslintrc-auto-import.json code/miniapp/src/pages.json`**：已执行（两文件本票全程未变，checkout 为空操作）
- **提交**：只 `git add` 本票自己的路径（`sample/export/**`（main+test）、`sample/service/SampleQueryService.java`、`plus-ui/src/{api/lqg/sample/export.ts,views/lqg/sample/index.vue,lang/lqg/sample.*.ts}`、`db/migration/V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql`、本报告目录）；**没有** `git add -A`，**没有**动 `_manifest.json` / `doc/waves/state.json` / `_manifest.json`，未 push、未 merge
- **复跑材料**：`accept-runners/{sampleexp001-acc1-export.sh, sampleexp001-acc2-menu.sh, sampleexp001-counterfeit-probes.sh, sampleexp001-regression.sh, sampleexp001-fresh-evidence.sh, probe_cells.py, count_rows.py, sampleexp001-shots.mjs, accept-transcript.txt, counterfeit-transcript.txt, regression-transcript.txt, d1-regression-transcript.txt, fresh-evidence.txt, java-test-transcript.txt, ui-transcript.txt}`
  （`sampleexp001-shots.mjs` 需要放在有 `puppeteer-core` 的目录里跑，例如 `cp` 到 `/tmp/shots/` 后 `node /tmp/shots/sampleexp001-shots.mjs`）
