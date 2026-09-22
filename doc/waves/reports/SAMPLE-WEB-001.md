# SAMPLE-WEB-001 · 完工报告

- **ticket**：SAMPLE-WEB-001（track SAMPLE / phase D2 / size L）—— D2 第五张：工作台样本总表
- **status**：**done**
- **accept**：**2/2 绿**（两条 `run` 逐条实跑，真实输出见 §5 与 `accept-runners/accept-transcript.txt`）
  - accept 1 · API：✅（9 个筛选 + 软删 + 待核验置顶，逐条钉死在 seed 上）
  - accept 2 · MENU：✅（菜单 5210、授权 101/102、`getRouters` 恰好 1 条、六个权限串、SegButtons ≥4、无开关/下拉）
- **分支**：`task/D2`，未切分支 / 未 push / 未 merge
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 23:50:17`，后端进程 8081（dev + `--api-decrypt.enabled=false`，PID 28734）
- **迁移**：**新增一支** `V202609221010__SAMPLE-WEB-001-sample-menu.sql`（SAMPLE 域 10xx，1010 未被占用）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**}`、`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`、`doc/api-contract.md`
- **没碰**：8080（Kevin 的 java 服务）/ 5432 / 6379；关进程一律按 PID（`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`），**没用过 `pkill -f`**
- **产物**：后端 4 个新类 + 3 个既有类的小改 + 1 个测试类 + 1 支迁移；前端 2 个页面/组件 + 1 个公共组件 + 1 个 api + 2 个 i18n 文件

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D2`；未切分支 |
| `depends_on` 全部 done 且产物在盘上 | ✅ PASS | `doc/waves/state.json`：`SAMPLE-VERIFY-001` / `SYS-WEB-001` / `AUTH-GROUP-001` 全 `done`；三条的接口（`/lqg/sample/{id}/verify`、字典/参数菜单、`/lqg/auth/unit|group`）实跑都在 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | 顶部四条里两条直接相关：**CR-20260917-05**（外部送来的类器官收样也在这张表里核验；核验抽屉按 `sample_kind` 切字段）、**CR-20260918-07**（历史记录、内部编号开关、两个参数；不影响本票筛选口径）—— 逐条落进实现，见 §2.2 |
| 8 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `UI:admin.sample.list` / `UI:admin.sample.edit` / `FLOW:F-SAMPLE-01.step2` / `FLOW:F-SAMPLE-02.step3` / 四个 `FIELD:t_lqg_sample.*` 全部 `active`；口径落点见 §2 |
| 视觉基准 = 方案 A（宽表 + 右侧抽屉） | ✅ PASS | `UI:admin.sample.list` 的 `prototype` = `gallery.html#admin-sample-a`；实现是「一张宽表（23 列）+ 工具栏 + `el-drawer`」 |
| 抽屉按 ADR / ticket：`el-drawer`、点蒙层可关 | ✅ PASS | `SampleDrawer.vue` 用 `el-drawer` 且 `:close-on-click-modal="true"`；DOM 实测点蒙层后 `.el-drawer.open` 计数 = 0 |
| 口径复述 4 条（按钮组 / 组别走档案 / 加密列精确 / 类器官核验切字段） | ✅ PASS | 见 §2.1–§2.4；每条的机器证据在 §5 |
| 两条上游「打在脸上」的事已处理 | ✅ PASS | ① `updateTime == null` = 从未修改：列表「最后修改」列显式渲染「从未修改」（9/9 行实测）；② `submitSource` 用样本行落库的 `submit_source` 列做 eq（**不按提交人当前角色现算**），并把 SAMPLE-VERIFY-001 的空转断言变回真断言（§5 段 3） |
| 动手前代码是绿的 | ✅ PASS | 改动前 `Tests run: 47`（SAMPLE-MP-001 报告写的 46 + 1）；本票后 `Tests run: 55`，0 失败 |
| 环境可用（8081 / PG 5433 / Redis 6380） | ✅ PASS | 三个容器全程在跑，未停；8081 用 `.tmp/run-backend.sh`（受管后台作业）起 |

**STOP 判定：无。**

---

## §1 取号依据与迁移

`doc/lint-profile.yaml`：D2 = `20260922`；HHmm 按域分段，**SAMPLE = 10xx**；返工迁移沿用本任务日期段。

- 已被占用：`202609221000`（SAMPLE-MODEL-001）、`202609221001`（SAMPLE-VERIFY-001）
- 派单指定 1010-1019 给 SAMPLE-WEB-001，且**避开 1002-1009**（留给后续 SAMPLE 票）→ 取 **`V202609221010__SAMPLE-WEB-001-sample-menu.sql`**
- 版本号大于已应用最大值 → `out-of-order=false` 下不会触发 SYS-WEB-001 那个 `FlywayValidateException` 坑

**空库端到端实测**（不是补跑）：10 支迁移按序跑完，本票那支最后一支 `success=true`（§5 段 0b）。

### 迁移为什么要把 5200 段**整体搬到** 5210 段（本张最容易做错的一步）

accept 2 同时要求：

1. `SELECT menu_id||':'||path||':'||component WHERE menu_id = 5210` == `5210:sample:lqg/sample/index`
2. `getRouters` 里 `component == 'lqg/sample/index'` 的路由**恰好 1 条**

而 SAMPLE-MODEL-001 的 `V202609221000` 已经把 **5200** 建成「样本总表」（C、`path='sample'`、`component='lqg/sample/index'`）。
若在 5200 下**再插一条同样 component 的 5210**，②会数成 **2 条 → 红**。

所以迁移做四件事：

1. 建 **5210**（C、顶级 `parent_id=0`、`path='sample'`、`component='lqg/sample/index'`、`perms='lqg:sample:list'`）
2. 建 **5211-5216**（F、`parent_id=5210`）= `list / query / add / edit / remove / verify` 六个权限串
3. 授给 **101（lqg_admin）** 与 **102（lqg_internal）**
4. **删掉 5200-5206**（旧 C + 旧 5 个 F + SAMPLE-VERIFY-001 的 5206），避免同一 component 两条路由、也避免 `role_menu` 里留指向已删菜单的孤儿行

**授权没丢**：5201-5205 的权限串在 5211-5215 上逐字重建，5206 的 `lqg:sample:verify` 在 5216 上重建；`sa-token` 的权限集合来自 perms 字符串、与 `menu_id` 无关，所以 `/lqg/sample/**` 的鉴权一个字都没变。

---

## §2 改了哪些文件 + 口径落点

### 2.1 后端 · 新增（`org.dromara.lqg.sample.query` 包）

| 文件 | 职责 |
|---|---|
| `sample/query/SampleSubmitterProfileVo.java` | 「提交人的外部档案」读视图：`userId / submitterName / groupId / groupName` |
| `sample/query/SampleSubmitterProfileQuery.java` | 组别 / 来源单位筛选 → 提交人 id 集合；给一页样本行批量补两个键（一次 `IN`，不是每行一次查询） |
| `sample/query/mapper/SampleSubmitterProfileMapper.java` | 两条 `@Select`：按档案的 `unit_id` / `group_id` 取 `user_id`；按 `user_id IN` 取「姓名 + 组别名（LEFT JOIN `t_lqg_unit_group`）」 |

> ★ **包名踩过的坑**：若依的 mapper 扫描路径是 `org.dromara.**.mapper`（`application.yml` 的 `mybatis-plus.mapperPackage`），所以 mapper 必须在 **`.mapper` 结尾的包**里。第一版放在 `sample.query` 下 → 后端起不来（`required a bean of type '...SampleSubmitterProfileMapper' that could not be found`）。放进 `sample.query.mapper` 后正常，且不必动 SAMPLE-MODEL-001 的 `sample.mapper` 包。

### 2.2 后端 · 修改（三处，都在 SAMPLE 域既有文件里）

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `sample/domain/bo/SampleQueryBo.java` | +`sourceUnitId` / `groupId` / `submitSource` / `receiveDateBegin` / `receiveDateEnd`（`LocalDate`）/ `tissueType` / `operatorName` | **accept 1 的筛选参数要住在 SAMPLE-MODEL-001 建的 BO 上**（Spring MVC 按字段名绑定。ticket 的 `touches` 里没有这个文件，见 §7 WARN-1） |
| `sample/service/SampleQueryService.java` | ① 拼上七个筛选；② **待核验置顶** `ORDER BY (verify_status='pending') DESC, create_time DESC, id DESC`；③ `list` / `detail` 补 `submitterName / groupId / groupName`；④ 档案查空 → 回空页（不退化成全表） | 同上：读路径只有 `list` 一条（小程序 `sort=recent` 也走它），筛选与排序只能落在这里 |
| `sample/domain/vo/SampleVo.java` | +`submitterName` / +`groupId` / +`groupName` | ticket §2.1「每行补 `submitterName`、`groupName`（读时带出）」 |

**口径落点（逐条对 ticket §0 的复述）**：

1. **「按钮」字段一律按钮组**：前端 `SegButtons`（§2.5）；四个「有无」字段（`isFixed` / `hasQcSheet` / `hasViabilityReport` / `hasPathology`）+ 性别都用它，**没有** `<el-switch>` / `<el-select>`（accept 2 最后一段 grep 断的就是这个）。
2. **按组别筛选走提交人的外部档案**（样本上没有 `group_id`）：`SampleSubmitterProfileMapper.selectSubmitterIds(unitId, groupId)` 先查档案，再 `in(Sample::getSubmitterId, ids)`；**不看档案的核验状态**（`bind_status` 不进 SQL）→ extE（档案待核验）送的 1007 照样能被 9101 组筛出来。
3. **加密列精确匹配**：`donorName` / `hospitalNo` 仍走 `SampleFieldCipher.encrypt` 后 `eq`（SAMPLE-MODEL-001 的既有口径，本票没碰）；两个筛选框旁边在页面上标了「精确匹配」小字（DOM 证据：`exactMarks: ["精确匹配","精确匹配"]`）。
4. **外部送来的类器官收样也在这张表里核验**（CR-20260917-05）：核验抽屉按 `sampleKind` 切字段 —— 类器官是「来源单位 / 类器官类型 / 备注 / 收样日期 / 内部编号 / 处理时间 / 细胞活率报告 / 操作人」，组织样本另加供体段与「有无病理 / 有无固定 / 质控表」。

**★ 两条上游语义（按 ticket 与派单要求显式处理）**：

- `submitSource`：`SampleQueryService` 里是 `.eq(StringUtils.isNotBlank(q.getSubmitSource()), Sample::getSubmitSource, trim(...))` —— **钉在样本行落库的 `submit_source` 列上**，全类不出现 `LoginHelper.getLoginUser`（契约测试有一条断言专门禁它）。该列是**提交当时的快照**。
- `updateTime`：**`null` ⇔ 这一行从没被改过**（SAMPLE-MP-001 的跨票行为变更）。列表「最后修改」列 `v-if="neverModified(row)"` → 渲染「从未修改」；抽屉顶部同理（`lqg.sample.drawer.lastModifiedNever`）。实测 seed 的 9 行 `updateTime` 全为 `null`、页面上 9 格全是「从未修改」。

### 2.3 后端 · 新增契约测试

`sample/query/SampleTableQueryContractTest.java`（**8 例**，不启 Spring 容器，钉结构 + 两条最易做反的语义）：

1. `SampleQueryBo` 七个新筛选字段的类型逐一对（日期必须是 `LocalDate`）；五个老筛选一个不许丢
2. `SampleVo` 带 `submitterName / groupId / groupName`
3. 组别 SQL 查 `t_lqg_ext_profile`、**不含 `bind_status`**、不用 `${}` 拼接
4. 姓名 / 组别名走 `IN` 批量 + JOIN `t_lqg_unit_group`，不逐行查
5. 「待核验置顶」是排序，且 `sort=recent` 的 `COALESCE(update_time, create_time)` 没被顶掉
6. `submitSource` 打在 `Sample::getSubmitSource` 上、读路径不出现 `LoginHelper.getLoginUser`；日期区间是 `ge` / `le`（两端都含）
7. 档案查空 → 回空页（`submitterIds.isEmpty()` 分支在），不退化成全表
8. 档案读侧没有任何 delete 方法（`t_lqg_sample` 的软删不变量没被削弱）

### 2.4 前端 · 新增（`code/plus-ui`）

| 文件 | 内容 |
|---|---|
| `src/components/lqg/SegButtons/index.vue` | **公共按钮组**：props `options / modelValue / multiple / exclusive / clearable / disabled`；`options` 支持字符串数组或 `{label,value}`；基于 `el-radio-button` / `el-checkbox-button`；**单选下再点一次 = 取消选择（emit null）** —— 这是「还没选」这个状态唯一的入口（甲方模板的三个「有无」都有这个状态）；`exclusive` 是互斥值数组（选中互斥值清掉其余，反之亦然），本票用不到但后面染色/样本类型要用 |
| `src/views/lqg/sample/index.vue` | 总表页：筛选区（9 项 + 起止日期区间 + 精确匹配标注）、工具栏（新增两个入口 / 导出两个按钮 / 刷新）、**23 列宽表**、待核验行浅黄底、行操作、分页、抽屉挂载 |
| `src/views/lqg/sample/SampleDrawer.vue` | 录入 / 编辑 / 核验抽屉：送检信息 + 收样信息两段；新增时选类别切字段；待核验底部「判为有效并保存 / 判为无效（弹原因）」；有效样本底部「保存」+ 顶部「最后修改」；「更多 → 改判无效」 |
| `src/api/lqg/sample/index.ts` | 六个接口 + 类型；`neverModified(row)` 纯函数把 `updateTime == null` 的口径收到一处 |
| `src/lang/lqg/sample.zh_CN.ts` / `.en_US.ts` | 150 组 key，键路径 `lqg.sample.*`（**没往上游 `zh_CN.ts` / `en_US.ts` 加一个 key**） |

**页面口径细节**：

- **零颜色字面量**：两个页面 + `SegButtons` 里 `grep -nE '#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\('` = 空；待核验行底色吃 `--lqg-warn-soft`，主色按钮吃 `--el-color-primary`（实测 `rgb(14,124,123)` = `#0E7C7B`）
- **`updateTime` 为 null 处理**：见 §2.2
- **导出两个按钮**：点了提示「导出在下一个任务接入（SAMPLE-EXPORT-001）」，不假装能用
- **「质控文档 / 石蜡包埋 / 冻存」三个行操作置灰**（`disabled` + `title="在后续任务接入"`），符合 §3 边界
- **切片染色提示列刻意不做**（SAMPLE-HINT-001 的 `hint` 键后端还没有，做了就是假列）；见 §7 WARN-3

### 2.5 前端 · 删掉两个占位文件

`src/views/lqg/sample/.gitkeep`、`src/api/lqg/sample/.gitkeep` —— 目录里已有真文件，占位文件不再需要（SYS-WEB-001 建的）。

---

## §3 后端接口清单（本票改动）

```
GET /lqg/sample/list
  新增筛选参数：sourceUnitId, groupId, submitSource, receiveDateBegin, receiveDateEnd,
                tissueType（模糊）, operatorName（模糊）
  排序：不带 sort→「待核验置顶 + 创建时间倒序」；sort=recent（小程序）→ 最后修改时间倒序（口径不变）
  行内新增键：submitterName, groupId, groupName
GET /lqg/sample/{id}
  同样补 submitterName / groupId / groupName
```

**与 `doc/api-contract.md` 第 45 行逐条对齐**：契约里 `GET /lqg/sample/list` 的参数列已经写了 `sourceUnitId, groupId, sampleKind, submitSource, verifyStatus, receiveDateBegin/End, tissueType, internalNo, operatorName, donorName（精确）, hospitalNo（精确）, pageNum, pageSize` —— 本票把缺的七个补齐，**没有改契约**（无 doc-drift）。契约没写「每行带 `submitterName` / `groupName`」，是本票按 ticket §2.1 加的**多键**（不破任何既有断言，见 §7 WARN-2）。

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module` 与 Maven 三参数（既有 WARN，不重复计数）

- **`--fresh-module ruoyi-lqg` 在本沙箱恒 exit 2**：`api.sh` 第 71 行用 `ps -o lstart=`，而 subagent 沙箱里 `/bin/ps` 是 `Operation not permitted`（SYS-WEB-001 / SAMPLE-MP-001 等 **7+ 张票**的既有 WARN-5）。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边：

```
jar mtime                  : 2026-09-21 23:50:17
jar epoch                  : 1790005817
lqg src newer than jar     : []        ← find ruoyi-lqg/src -newer <jar> 为空
admin src newer than jar   : []        ← ruoyi-admin/src 一个字节没改
8081 PID                   : 28734
该 PID 打开 jar 的次数      : 2          ← lsof -p <PID> | grep -c ruoyi-admin.jar
进程启动                    : Started DromaraApplication in 5.367 s
日志 mtime                 : 2026-09-21 23:57:15（晚于 jar 的 23:50:17 = 没 stale）
嵌套 jar 复核               : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含 sample/query/ 4 个 class
                             + SampleSubmitterProfileMapper（含 __Javadoc.json）
```

- **accept 里没有 Maven 行**（本票两条 run 都不跑 mvn），所以 Maven 三参数这条既有 WARN 本票**不新增命中**；`mvn package` 实跑时按规矩带了 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`。
- 两条 run 都是长链，**包进函数判整条 rc**（不丢给 `set -e`），runner 在 `accept-runners/`。

### 4.1 accept 1 · API —— ✅

```
$ bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc1-api.sh
---- 1) sourceUnitId=9000009001
["9000001001","9000001002","9000001003","9000001004","9000001005","9000001007"]
---- 2) groupId=9000009101
["9000001001","9000001002","9000001003","9000001004","9000001007"]
---- 3) sampleKind=organoid
["9000001009"]
---- 4) submitSource=internal
["9000001008","9000001009"]
---- 5) verifyStatus=pending
["9000001002","9000001007"]
---- 6) receiveDate 区间
B=2026-08-26 E=2026-09-02
["9000001004","9000001005"]
---- 7) 三条件 AND
["9000001001","9000001004","9000001005"]
---- 8) 全量（软删 1010 不出现）
["9000001001","9000001002","9000001003","9000001004","9000001005","9000001006","9000001007","9000001008","9000001009"]
---- 9) 待核验置顶（pageSize=2）
true
---- 10) submitterName / groupName 读时带出
true
ACCEPT-1 GREEN (exit 0)
```

**逐条钉死在哪**（accept 的每条都是「一个 id 集合」，不是「非空」）：

| 段 | 期望集合 | 这条在钉什么 |
|---|---|---|
| 1 | 6 条（无 1006 异单位 / 无 1008、1009 内部） | 来源单位按**提交人档案**筛；内部人员没有档案行 |
| 2 | 5 条（无 1005 异组） | 组别住在档案上，不是样本行 |
| 3 | `["1009"]` | `sampleKind` |
| 4 | `["1008","1009"]` | ★ **`submit_source` 是快照列**（不是按当前角色现算） |
| 5 | `["1002","1007"]` | 核验状态 |
| 6 | `["1004","1005"]` | 区间**两端都含**（端点取在两条样本外侧各一天） |
| 7 | 3 条 | 多条件 **AND** 不是 OR |
| 8 | 9 条（**没有软删的 1010**） | `@TableLogic` 不变量 |
| 9 | 前两行都是 pending、`total=9` | **待核验置顶** |
| 10 | 1001 行 `submitterName=王医生`、`groupName=肝胆外科组` | 读时 join 出的两个键 |

**counterfeit 逐条排掉**：

- 组别写成「该单位下所有组」→ 段 2 会带出 1005（extC 在 9102）→ 红
- 组别筛选只认已核验的人 → 段 2 会少 1007（extE 待核验）→ 红
- 日期区间写成开区间 / 漏一端 → 段 6 的 1004（第 25 天）或 1005（第 20 天）掉出去 → 红
- 多条件 OR 拼 → 段 7 红
- 软删的 1010 混进来 → 段 8 红
- 待核验没置顶 → 段 9 红
- **`submitSource` 按提交人当前角色现算 → 段 4 红**（seed 里 extA 后来被 AUTH-STAFF-001 的 accept 升级成内部，但他送的 1001/1002/1003 仍是 external；SAMPLE-VERIFY-001 的 accept 2 段 5/6 用两侧不同源的独立取证钉过这一点）
- **档案查空退化成「不过滤 = 全表」→ 筛一个不存在的组会看到全部样本**（本票专门有 `submitterIds.isEmpty()` 分支 + 契约测试第 7 条守着）

**SAMPLE-VERIFY-001 WARN-2 的空转断言收口**（本票的份内事之一）：

```
$ bash doc/verify/api.sh --as staff GET '/lqg/sample/list?submitSource=external&pageSize=100'
{"n":7,"ids":["9000001007","9000001002","9000001003","9000001006","9000001005","9000001004","9000001001"],
 "sources":["external","external","external","external","external","external","external"]}
external 集合精确匹配: true      ← 7 条全是 external（1008/1009 不再混进来）
internal 集合精确匹配: true      ← 恰好 ["9000001008","9000001009"]
```

改前那段是**空转**（`SampleQueryBo` 没有 `submitSource` 字段，Spring 忽略未知参数 → 返回全表，`index("9000001001") != null` 自然成立）。改后它返回的集合精确等于 external 的 7 条 —— WARN-2 要的「变回有意义的断言」已成事实（**未改 `doc/verify/**` 的只读资产，只让实现把那段断言变真**）。

### 4.2 accept 2 · MENU —— ✅

```
$ bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc2-menu.sh
---- 1) 菜单 5210 落在 5200 段（C，path/component 逐字）
5210:sample:lqg/sample/index
---- 2) 授给 101 与 102
101
102
---- 3) getRouters（--as staff）里 lqg/sample/index 恰好 1 条
true
---- 4) 5210 下的按钮权限串集合
lqg:sample:list
lqg:sample:query
lqg:sample:add
lqg:sample:edit
lqg:sample:remove
lqg:sample:verify
---- 5) 公共按钮组组件在盘上
---- 6) SampleDrawer.vue 里 SegButtons 出现 ≥4 次
---- 7) 四个「有无」按钮字段不许用 el-switch / el-select
ACCEPT-2 GREEN (exit 0)
```

**counterfeit 逐条排掉**：

- 按钮权限串与后端 `@SaCheckPermission` 对不上（前端 `verify` / 后端 `audit`）→ 段 4 集合红
- 「有无固定」做成 `el-switch`（开关没有「还没选」这个状态）→ 段 7 红
- 抽屉里四个按钮字段只有两个用 `SegButtons` → 段 6 计数不足红
- 只在 5200 下补一行 C 菜单（不动 5200）→ 段 3 数成 **2 条** → 红（本票把整段搬到 5210 就是为了这个）

### 4.3 追加（accept 之外的机器证据）

**模糊筛选 + `updateTime` 语义**：

```
tissueType=肝 (模糊) → ["9000001001","9000001003","9000001004","9000001007","9000001008"]   （1005 是胃组织、1006 结直肠、1002 胆管、1009 无组织类型 → 正确排除）
operatorName=李 (模糊) → ["9000001001","9000001004","9000001005","9000001006","9000001008","9000001009"]   （张工 / 管理员经手的排除）
全表 updateTime 全为 null（SAMPLE-MP-001 的跨票行为）: true
```

**软删行任何筛选都不出现**：段 8 的全量集合没有 1010；另外 `t_lqg_sample` 的软删由实体 `@TableLogic` 兜住，本票新增的两条 `@Select` 都在**档案表**上，没碰样本表。

### 4.4 Java 全模块测试：`Tests run: 55, Failures: 0, Errors: 0, Skipped: 0`

```
ExtBindStateMachineContractTest   Tests run: 11
StaffGrantRulesContractTest       Tests run:  6
MockLoginGuardContractTest        Tests run:  4
ExtChokepointContractTest         Tests run:  4
VerifyTransitionsContractTest     Tests run:  8
MpSampleContractTest              Tests run:  8
SampleKindRulesContractTest       Tests run:  6
SampleTableQueryContractTest      Tests run:  8   ← 本票新增
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
```

（`ExtChokepointContractTest` 的 4 条 ADR-0004 不变量本票没碰：新代码不在 `ext` 包，也没往 `ext` 包加任何持 Mapper 的类。）

---

## §5 视觉证据（截图清单 + DOM/样式断言）

截图在 `doc/waves/reports/SAMPLE-WEB-001/`，由 `accept-runners/web001-shots.mjs`（puppeteer-core + 本机 Google Chrome，headless）产出。
**全程没有把任何 PNG 读进上下文**；断言只读 DOM 文本 / `getComputedStyle`（机器可读证据在对应的 `probe-*.json`）。

| 文件 | 覆盖项 | 实测断言输出 |
|---|---|---|
| `01-sample-list.png` | 总表（23 列，含 2 条待核验行） | 9 行；列名 23 个；待核验行底色 `rgb(252, 241, 218)`（= `--lqg-warn-soft`），有效行 `rgb(255,255,255)`；主色按钮 `rgb(14, 124, 123)`（= `#0E7C7B`）；「最后修改」9/9 格 = `从未修改`；筛选区「精确匹配」小字 2 处 |
| `02-verify-drawer.png` | 核验抽屉（待核验样本 SJ90000007） | 标题 `核验样本`；两段 `送检信息` / `收样信息`；**6 个按钮组**（类别 / 性别 / 有无病理 / 有无固定 / 质控表 / 细胞活率报告）+ **0 个 `el-switch`**；底部 `判为有效并保存` / `判为无效` / `取 消`；收样段两个日期控件；小字提示 = `外部送来的组织样本：收样日期与内部编号必填，内部编号全库唯一。` |
| `03-edit-drawer.png` | 编辑抽屉（有效样本 SJ90000006） | 标题 `编辑样本`；顶部小字 `从未修改`；底部 `保存` / `取 消`；「更多」菜单在；**未选**字段（有无病理）实测 `checked: []` —— 这正是「按钮组有『还没选』状态」的直接证据 |
| `probe-table.json` | 23 列 × 9 行的逐格文本 + 每行底色 + 列名 | 见上 |
| `probe-filters.json` | 筛选区标签 + 精确匹配小字 | `["来源单位","组别","样本类别","提交来源","核验状态","收样日期","组织类型","内部编号","操作人","供体姓名 精确匹配","住院号 精确匹配"]` |
| `probe-verify-drawer.json` | 核验抽屉结构（按钮组 / 开关数 / 下拉数 / 底部按钮） | `switchCount: 0`、`selectCount: 1`（来源单位）、`dateInputs: ["date","date"]` |
| `probe-edit-drawer.json` | 编辑抽屉结构 + 各按钮组的选中态 | 见上 |
| `probe-overlay-close.json` | **点蒙层可关**（禁 `:close-on-click-modal="false"`） | `{"closedByOverlay": true}` |

**主色同源证据**：搜索按钮 `rgb(14, 124, 123)` = `#0E7C7B`（`--el-color-primary` 映射生效，页面自身零色值字面量）。

**未覆盖（如实写明）**：核验 / 保存的**提交链路**没有端到端点过去（accept 不要求；SAMPLE-VERIFY-001 已把 `PUT /lqg/sample/{id}/verify` 的状态机、必填、唯一性钉死），本票只在 DOM 层面证明按钮与字段在。真机/浏览器兼容性未覆盖（工作台是桌面页，与 SYS-WEB-001 同口径）。

---

## §6 遗留与 raise

### 6.1 越出 `touches` 的改动（逐条列清）

`touches` 只列了 `ruoyi-lqg/.../sample/query/**` 与前端的 4 个路径 + 迁移。实际改了 **3 个不在 touches 里的后端既有文件**（派单时已口头授权「需要动 `SampleQueryBo` / `SampleQueryService` / `SampleController` 时就动，并在报告里显式记一条 WARN」）：

| # | 文件 | 为什么非改不可 |
|---|---|---|
| 1 | `sample/domain/bo/SampleQueryBo.java` | accept 1 的七个筛选参数必须住在 SAMPLE-MODEL-001 建的 BO 上（Spring MVC 按字段名绑定；另起一个 BO 类改不了 `@GetMapping("/list")` 的绑定类型而不动 controller） |
| 2 | `sample/service/SampleQueryService.java` | 读路径只有 `list` 一条（小程序 `sort=recent` 共用它）。筛选、待核验置顶、`submitterName/groupName` 只能落在这里，否则要么复制一份 wrapper 拼装逻辑（两处口径打架），要么破坏 SAMPLE-MP-001 的 `sort=recent` |
| 3 | `sample/domain/vo/SampleVo.java` | ticket §2.1 要求每行补 `submitterName / groupName`；行 VO 只有这一个（**没有另立一个 VO**，所以小程序接口也会多带这三个键） |

三处都是**新增字段 / 新增分支**，没有改任何既有方法签名；`SampleController` **一个字没动**（`@SaCheckPermission` 与路径都复用）。

### 6.2 没把握 / 没做的（交给后续 CR 或票决）

1. **「改判无效」在「名下有包埋 / 冻存 / 质控文档」时禁用**这一条，前端 `hasChildren` 目前**恒 false**：详情接口不带这个标记，而 `SampleChildrenCheckers` 在盘上**注册 0 个实现**（SAMPLE-MODEL-001 起的扩展点，EMBED / CRYO / QC 各自注册）。所以「禁用并说明原因」的**前端前置禁用**现在做不到；**后端兜底是有效的**（`SampleVerifyService` 在 `valid + hasChildren` 时拒绝并回原因），用户会看到一句报错而不是一个置灰的菜单项。这是**跨票缺口**：EMBED-MODEL-001 / CRYO-MODEL-001 / QC-MODEL-001 注册实现后，本抽屉还需要一个「详情带 hasChildren」的小改。建议记为 issue。
2. **「更多 → 改判无效」的口径**：ticket §2.2 说「收在更多菜单里」，契约 `PUT /lqg/sample/{id}/verify` 的 `action=invalid` 就是它（无效原因 `reason` 必填）。本票按此实现。
3. **切片染色提示列刻意不做**：它是 SAMPLE-HINT-001 的（`hint` 键后端还没有）。做了就是假列。
4. **列表的「提交人 / 组别」两列是新增显示列**（ticket §2.1 要求读时带出）。`UI:admin.sample.list` 的表格列清单里没有显式写这两列（它写的是「来源单位」），我按 §2.1 的正文加的；如果甲方认为列太多，删两列即可（后端两个键留着不碍事）。
5. **`/lqg/sample/{id}` 多出三个键**（`submitterName / groupId / groupName`）：与 SAMPLE-MP-001 报告 WARN 的口径一样，JSON 多键不破任何既有断言（本票核过 SAMPLE-MODEL-001 / SAMPLE-VERIFY-001 / SAMPLE-MP-001 的 accept：它们都只断具体键或 id 集合）。
6. **自填单位名的行**（`t_lqg_ext_profile.unit_name_input`，seed 的 extF 吴同学）在总表里 `groupName` 是 null、`sourceUnitName` 走样本行的快照 —— 与外部接口的口径一致（`/mp/me` 才回 `unitNameInput`）。如果甲方要在总表里区分「自填」，需要后端再加一个键，本票没做（ticket 没要求）。

### 6.3 与权威不一致的地方

- `doc/api-contract.md` 第 45 行的 `GET /lqg/sample/list` 参数列**已经写全了**（含 `submitSource`），本票实现与它逐字对齐，**没有改契约**（无 doc-drift）。
- 唯一契约没写、本票加的是行内 `submitterName / groupName` 两个键（ticket §2.1 的正文要求）。已在 6.2-5 说明。

---

## §7 坑与解法（给下游）

1. ★ **mapper 必须在 `.mapper` 结尾的包里**：若依的扫描路径是 `org.dromara.**.mapper`（`application.yml` 的 `mybatis-plus.mapperPackage`）。想「把新代码都放进 `sample/query/**`」而把 mapper 放 `sample.query` → **后端起不来**，报的是 `Parameter 0 of constructor in SampleSubmitterProfileQuery required a bean of type '...SampleSubmitterProfileMapper' that could not be found`（看着像 Spring 配置问题，其实是包名少了一个 `.mapper`）。解法：`sample/query/mapper/`，既在 touches 的 `sample/query/**` 里，又被扫到。
2. ★ **`getRouters` 里同一 `component` 只能有一条**：SAMPLE-MODEL-001 已经把 5200 建成 `lqg/sample/index`，所以本票**不能**在 5200 下再补一行 C 菜单（accept 2 段 3 会数成 2 条）。正确做法是把菜单节点整体搬到 5210 段 + 删旧段（授权靠 perms 字符串延续，不会丢）。**同域后续票若要改菜单挂载点，先查这个 component 有几条。**
3. ★ **`Segment/tabs` 类字段千万别用 `el-switch`**：开关只有开 / 关，**没有「还没选」**；甲方模板的三个「有无」在新增时都是「还没选」。`SegButtons` 单选下「再点一次 = emit null」是唯一的回退入口；`el-radio-group` 自己不会因为点了已选项就取消，必须手动处理。
4. ★ **「按组别筛选」不能拿档案的核验状态做过滤**：核验状态回答的是「这个人的组别认不认」，不是「这条样本算不算这个组的」。加上 `bind_status='verified'` 会让 extE（待核验）送的 1007 从 9101 组消失 —— accept 1 段 2 会红，而现象看起来完全合理（很容易自己说服自己）。
5. ★ **表达式的 `wrapper.last()` 会互相覆盖**：MyBatis-Plus 3.5.16 没有按列名排序的重载（SAMPLE-MP-001 的坑），表达式排序只能 `wrapper.last("ORDER BY …")`；但 `last()` **追加**、调两次就变成 `ORDER BY a, b` 这种拼接 —— 所以「待核验置顶」与「`sort=recent` 的最后修改倒序」必须写成 `if/else` 的**两条互斥分支**，不能先 `orderBy` 再 `last`。本票两条分支都在 `SampleQueryService.list`，且有契约测试钉住两句都在。
6. **空库重建后的 dev 库**：本票为了「空库端到端跑迁移」`DROP DATABASE lqg_dev` → 重启后端 → 10 支迁移按序跑完；收尾时已 `reseed.sh --yes` + `clean-orphan-accounts.sh --yes`（收尾状态 = 干净 seed）。

---

## §8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | ticket-drift | ticket 的 `touches` 与 accept 不符：筛选参数住在 SAMPLE-MODEL-001 的 `sample/domain/bo/SampleQueryBo.java` + `sample/service/SampleQueryService.java` + `sample/domain/vo/SampleVo.java`，都不在 `touches`（只列了 `sample/query/**`） | 派单时已口头授权可改，本票改了 3 个文件（新增字段 / 新增分支，无签名变更），并在 §6.1 逐条列清。方案：ticket 生成器把「读路径 / VO / BO 所在文件」也写进 `touches`（同 AUTH-EXT-001、SAMPLE-MP-001、SAMPLE-VERIFY-001 三次同型命中）。 |
| WARN-2 | S3 | doc-drift | `doc/api-contract.md` 第 45 行没写行内 `submitterName` / `groupName` 两个键，但 ticket §2.1 要求「读时带出」 | 本票按 ticket 加了（多键，不破既有断言）。方案：把这两个键补进契约第 45 行的行形状说明（`/lqg/sample/{id}` 同样）。 |
| WARN-3 | S3 | debt | 「改判无效」的**前端前置禁用**做不了：详情接口不带「名下有包埋 / 冻存 / 质控文档」标记，`SampleChildrenCheckers` 在盘上注册 0 个实现 | 见 §6.2-1。后端兜底有效（`SampleVerifyService` 拒绝 + 回原因），但 UI 上是一个点了才知道错的菜单项，与 `UI:admin.sample.edit`「禁用并说明原因」有差距。方案：EMBED / CRYO / QC 的 MODEL 票注册 checker 后，给 `SampleVo` 加一个只读标记（或详情加 `hasChildren`），本抽屉再补一行判断。**建议排进 D3 的 EMBED-MODEL-001 / QC-MODEL-001**。 |
| WARN-4 | S3 | clarify | 总表新增了「提交人 / 组别」两列，`UI:admin.sample.list` 的表格列清单里没有这两列 | 依据是 ticket §2.1 的正文（「每行补 `submitterName`、`groupName`」）。若甲方嫌列多，删两列即可（后端键留着不碍事）。方案：把这两列补进 `UI:admin.sample.list` 的表格列清单，或明确「只做筛选用、不显示」。 |
| WARN-5 | S2 | harness | `api.sh --fresh-module` 在本沙箱恒 exit 2（`ps` 被禁）+ Maven 需带三参数 | 已连续 8+ 张命中（SYS-BASE / AUTH-LOGIN / SYS-WEB / AUTH-STAFF / AUTH-GROUP / AUTH-EXT / SAMPLE-MP / 本票）。只影响 subagent 沙箱。本票用「`find -newer jar` 为空 + `lsof` 拿 PID + 进程持该 jar + 日志 mtime 晚于 jar + 嵌套 jar 内含新 class」五项等价替代（§4.0）。方案同上游：换成 `lsof` / 日志 mtime 的兼容写法，或给 `api.sh` 加「跳过启动时间守卫」的开关。 |
| WARN-6 | S3 | process | **`nohup … &`（不带受管 job）起的后端在本沙箱会被中途杀掉**，并且会在杀掉前把 8081 占住 → 下一次受管 job 启动报 `Port 8081 was already in use` | 本票实测踩到一次（自己确认过 bash 工具的 `run_in_background` 才是可靠路径，且**不用** `kill -9` / `pkill`，一律按 `lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID 再 kill）。方案：把「长进程走受管后台 job」补进给下游 ticket 的接口说明（SAMPLE-MP-001 WARN-2 已记过同源现象，本票是它在**端口占用**方向上的第二个症状）。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`updateTime` 兜底被删（SAMPLE-MP-001 WARN-4，本票按它处理 null）、`submitSource` 筛选缺失（SAMPLE-VERIFY-001 WARN-2，本票**收口**并在 §4.1 贴了证据）、`db.py` 是只读执行器却 exit 0、`reseed.sh` 清不掉运行时账号（收尾跑 `clean-orphan-accounts.sh --yes`）、surefire `groups` 假绿、`PageQuery` 只有两参构造、`updateById` 忽略 null、MyBatis-Plus 无按列名排序重载、跨域补号的 Flyway 坑。

---

## §9 收尾（长进程 / 端口 / DB）

```
$ for p in 8080 8081 8082 8083 8099 9200 9201; do printf '%s: ' $p; lsof -ti tcp:$p -sTCP:LISTEN | tr '\n' ' '; echo; done
8080: （未碰，Kevin 的 java 服务）
8081: （空）   ← 本票后端，收尾按 PID 关掉
8082: （空）   ← 本票 plus-ui dev server，收尾按 PID 关掉
8083 / 8099 / 9200 / 9201: （空）
```

- **关进程一律按 PID**：`lsof -ti tcp:<端口> -sTCP:LISTEN` → `kill`（本票**从未**用过 `pkill -f`，也从未对 8080 做任何操作）
- **DB 收尾**：`doc/verify/reseed.sh --yes` + `doc/waves/tools/clean-orphan-accounts.sh --yes`；`sys_config` 的 `lqg.ext.show-internal-no` 未被本票改动（保持 `false`）
- **docker 容器**：`lqg-dev-postgres`(5433) / `lqg-dev-redis`(6380) / `lqg-dev-minio`(9002+9003) 全程在跑，**没停**（留给后续 ticket）
- **复跑材料**：`accept-runners/{web001-acc1-api.sh, web001-acc2-menu.sh, web001-fresh-evidence.sh, web001-shots.mjs, accept-transcript.txt}`
