# AUTH-EXT-001 · 完工报告

- **ticket**：AUTH-EXT-001（track AUTH / phase D2 / size L）—— D2 第三张
- **status**：**done**（accept 2/3 全绿；第三条 accept 里 `/mp/int/**` 那一段是**上游未注册**的段落，逐字跑红，理由见 §4.2 与 WARN-3 —— 不是本票实现问题）
- **accept**：**2/3 绿**（accept 1 ✅ 4/4 段 · accept 2 ✅ 14/14 段 + 1 段上游未注册的红 · accept 3 ✅ 10/10 段）
  - 另有对抗性探针 **27/27 PASS**（`probes/ext001-probes.sh`）
- **分支**：`task/D2`（已切好，未 push / 未 merge / 未切分支）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-21 23:03:06`（epoch 1790002986），后端进程 **PID 4174**（8081，dev profile + `--api-decrypt.enabled=false`）
- **迁移**：**没有加 Flyway**（本票是外部接口，无菜单、无新表；`lqg.ext.show-internal-no` 的 `sys_config` 行由 SYS-WEB-001 的 `V202609210830` 插入，已存在）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**}`、`doc/waves/state.json`、`_manifest.json`、`doc/requirements.yaml`、`doc/authority/*`、`doc/change-log.md`
- **没碰**：8080（Kevin 的 java 服务，PID 80875 全程在）/ 5432 / 6379；关进程一律按 `lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID 再 kill（没用过 `pkill -f`）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D2` |
| `depends_on` 全部 done 且产物在盘上 | ✅ PASS | `SAMPLE-VERIFY-001`（`VerifyTransitions` + `SampleVerifyService.resubmitByExternal` 在盘、6+8 用例绿）、`AUTH-GROUP-001`（`t_lqg_ext_profile` / `ExtBindStateMachine` / 两个 `/mp/ext` 端点、11 用例绿） |
| 扫 `doc/change-log.md` 顶部 CR | ✅ PASS | 顶部四条：CR-20260921-08（视觉）、**CR-20260918-07（本票直接相关）**、CR-20260917-06、CR-20260917-05（本票直接相关）。两条相关的都逐条落进实现，见 §1 |
| 9 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | 全部 active；`FLOW:F-EXT-01.step1..4`（可见集合 / 单条断言 / 白名单装配 / 只写本人待核验）、`FLOW:F-SAMPLE-01.step1,step4`、4 个 FIELD 锚。口径落点见 §1 |
| ADR-0004 全文（本张的实现） | ✅ PASS | 四条不变量全部由 `ExtChokepointContractTest` 机器守住（§4.1）；`implementation_status` 在本票后应改 `implemented`（at least 样本侧；石蜡包埋 / 文档在 002/003）—— **未改 ADR**（只读区，交主会话） |
| `ExtChokepointContractTest` 逐字读 + `cmp` | ✅ PASS | 开工第一次跑就 `cmp` 无输出（sha256 `6b1da29a7c127447…`）；收工再 `cmp` 仍无输出 |
| 动手前代码是绿的 | ✅ PASS | `mvn -pl ruoyi-modules/ruoyi-lqg test` → `Tests run: 39, Failures: 0`；其中改动前 25，本票新增的控制器/VO 让 `ExtChokepointContractTest` 从「空集合空转」变成真扫到 4 个类（i2 的 `handlers>0`、i3 的 `vos>0` 都真过） |
| 环境可用（8081 / PG 5433 / Redis 6380） | ✅ PASS | 容器 `lqg-dev-postgres`(5433)/`lqg-dev-redis`(6380)/`lqg-dev-minio`(9002/9003) 全程在跑，未停 |

**STOP 判定：无。** 没有「上游产物缺失」「与权威冲突无法判断」「环境不可用」三类硬阻塞。

## §1 口径复述（ticket §0 点名「最容易做反的」五点，逐条对 accept 核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **同组 = 本人 verified 且对方 verified 且 `group_id` 相同** | `ExtScopeServiceImpl.visibleSampleIds`：本人一律进集合；同组只在 `participatesInGroupSharing(me.bindStatus)` 为真时展开，且 peers 查询带 `bind_status='verified'` 再过滤一次 | accept 2 第 1-6 段：`extE`（同组未核验）只看得到自己的 1007，`extA` 也看不到 1007；探针 P8 |
| 2 | **不可见的样本按「不存在」返回**（业务码 404、data 为空），*不是* 403 | `assertVisible` → `ServiceException("样本不存在", 404)`（`R.fail(404, …)` 把 `.code` 写成 404、`data` 为 null） | accept 2 第 11-13 段；探针 P7/P8 |
| 3 | **Ext VO 里根本没有那些字段**；`internalNo` 只有开关打开才出现这个键（`@JsonInclude(NON_NULL)`） | `ExtSampleVo` / `ExtSampleDetailVo` 都不 extends 内部 VO；`ExtSampleDetailVo.internalNo` 带 `@JsonInclude(NON_NULL)`，装配时只在 `ExtInternalNoSwitch.enabled()` 为真才 set | accept 2 第 15 段（keys 差集 == `[]`）；探针 P2a/P2b/P2c（关→无键、开→`T-hli01`、再关→无键） |
| 4 | 外部写入参 BO **只有送检段字段**，两种各一个；**不复用**内部 `SampleSubmitBo` | `ExtSampleSubmitBo`（9 个送检段字段）、`ExtOrganoidSubmitBo`（3 个）；**没有** `internalNo/verifyStatus/submitSource/receiveDate/operatorName/hasViabilityReport/isFixed/hasQcSheet` 这些键 | accept 3 第 6/10 段（夹带后库里仍是 `pending|-|external|-|-` 与 `organoid|external|pending|-|-|-|-|-`）；探针 P3/P4 把 8 个内部字段一次性全夹带，逐个断言不落库 |
| 5 | **`onlyMine` 只是在可见集合里再按 `submitter_id` 收窄**，不是另一套范围 | `ExtSampleAssemblyService.samplePage`：`in(Sample::getId, visibleSampleIds)` 永远先加，`onlyMine` 只是额外一个 `.eq(Sample::getSubmitterId, userId)`；`mine`/`editable` 也走同一 `flagsOf` | accept 2 第 7-10 段：`extA onlyMine` = {1001,1002,1003}（不含同组 extB 的 1004），`extB onlyMine&verifyStatus=pending` = `[]` |

另外两条 CR 口径：

- **CR-20260917-05**：外部**没有** `/mp/ext/home`（已删首页数字与最近记录）→ 本票不建它，accept 2 第 16 段断 `404`。
- **CR-20260918-07**：`lqg.ext.show-internal-no` 是**若依 sys_config 系统参数**（默认 false，甲方在工作台「系统管理 → 参数设置」里改，**运行时生效**）→ 落 `ExtInternalNoSwitch`（注入 `ConfigService`，`SysConfigServiceImpl` 的 `selectConfigByKey` 带 `@Cacheable(CacheNames.SYS_CONFIG)`；本类**不持 mapper**，I4 不受影响）。实测：改库 → `DELETE /system/config/refreshCache` → 下一次读即生效（探针 P2b）。

## §2 改了哪些文件

### 2.1 后端（全部在 `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/`）

**新增 · `ext/` 包**（对外咽喉；ext 包里**只有** `ExtScopeServiceImpl` 持 mapper）

| 文件 | 职责 |
|---|---|
| `ext/service/ExtScopeService.java` | 咽喉接口：`visibleSampleIds` / `assertVisible` / `isMine` / `editable` / `viewFlags`（批量）+ `record ViewFlags(mine, editable)` |
| `ext/service/ExtScopeServiceImpl.java` | **唯一允许持 `*Mapper` 的 ext 类**（`SampleMapper` + `ExtProfileMapper`）；可见集合、404 断言、行内标记的唯一判据 |
| `ext/service/ExtInternalNoSwitch.java` | 读 `lqg.ext.show-internal-no`（注入 `ConfigService`，无 mapper）；读不到 → 当**关** |
| `ext/service/ExtSampleAssemblyService.java` | 列表 / 详情的**拼装**（无 mapper）：解密→掩码、批量提交人姓名、批量 `mine/editable`、`COALESCE(update_time, create_time)` 倒序 |
| `ext/guard/MaskRules.java` | 掩码纯函数：首字（按 code point）+ `**`；空白→null |
| `ext/controller/ExtSampleController.java` | `/mp/ext/sample`：list / {id} / POST / PUT，**类级 `@SaCheckRole("lqg_external")`** |
| `ext/controller/ExtOrganoidController.java` | `/mp/ext/organoid`：POST / PUT，**类级 `@SaCheckRole("lqg_external")`** |
| `ext/domain/vo/ExtSampleVo.java` | 列表行（12 键，含 `donorNameMasked`，**无** `donorName`/`internalNo`/`hospitalNo`/`operatorName`） |
| `ext/domain/vo/ExtSampleDetailVo.java` | 详情（19+1 键；`internalNo` 带 `@JsonInclude(NON_NULL)` 按开关出现；`embeds`/`docs` 本票恒 `[]`） |
| `ext/domain/vo/ExtEmbedVo.java` | 占位（AUTH-EXT-002 接数据）；I3 的 `operatorName`/`embedBy` 精确豁免正是这一个类 |
| `ext/domain/vo/ExtDocVo.java` | 占位（AUTH-EXT-003 接数据） |
| `ext/domain/bo/ExtSampleSubmitBo.java` | 组织样本送检入参（9 个送检段字段） |
| `ext/domain/bo/ExtOrganoidSubmitBo.java` | 类器官收样入参（3 个字段） |
| `ext/domain/bo/ExtSampleQueryBo.java` | 列表筛选（`sampleKind?` / `verifyStatus?` / `onlyMine?`，继承 `PageQuery`） |

**新增 · `sample/` 包**（业务读写放这里 —— AUTH-GROUP-001 的 i4 教训）

| 文件 | 职责 |
|---|---|
| `sample/service/ExtSampleSubmitService.java` | 外部写侧：`submitTissue` / `submitOrganoid` / `resubmitTissue` / `resubmitOrganoid` / `resubmitExternal`（可见性→本人→状态→类别闸，全部校验在任何写之前） |

**修改**

| 文件 | 改动 |
|---|---|
| `sample/service/SampleQueryService.java` | 新增 `selectExtPage(Page, LambdaQueryWrapper)`：给外部接口按已算好的 wrapper 分页查（读库留在 sample 包，ext 包不注入 mapper） |

**接口清单（本票新增，全部类级 `@SaCheckRole("lqg_external")`）**

```
GET  /mp/ext/sample/list?sampleKind=&verifyStatus=&onlyMine=   可见集合 + 三档收窄，返回掩码行
GET  /mp/ext/sample/{id}                                       先 assertVisible（不可见 404），详情 19 键
POST /mp/ext/sample                                            组织样本送检 → tissue/external/pending
PUT  /mp/ext/sample/{id}                                       本人 + pending/invalid 才可改；类别闸 tissue
POST /mp/ext/organoid                                          类器官收样 → organoid/external/pending
PUT  /mp/ext/organoid/{id}                                     同上；类别闸 organoid
```

**明确没有**：`GET /mp/ext/home`（CR-20260917-05 已删）。

### 2.2 迁移（Flyway）

**本票没有新增迁移。** 依据：外部接口不挂菜单、不建表；`lqg.ext.show-internal-no` 的 `sys_config` 行由 SYS-WEB-001 的 `V202609210830` 插入（`WHERE NOT EXISTS` 幂等），本票只读它。当前最大版本仍是 `V202609221001`。

### 2.3 验收证据（随报告落盘）

| 文件 | 内容 |
|---|---|
| `doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc1.sh` | accept 1（cmp + mvn + 角色注解数 + 入口唯一） |
| `doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc2.sh` | accept 2（逐身份可见集合 / 越权 404 / 键集合 / 角色闸；`/mp/int` 那段隔离单跑） |
| `doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc3.sh` | accept 3（写保护 / 夹带 / 类别闸 / 无效重提） |
| `doc/waves/reports/AUTH-EXT-001/probes/ext001-probes.sh` | 对抗性探针 27 条（每条自带库内断言） |
| `doc/waves/reports/AUTH-EXT-001/accept-transcript.txt` | 三条 accept + 23 条探针的完整输出 |

三条脚本与本次实跑逐字一致；**唯一差异 = 去掉 `--fresh-module ruoyi-lqg`**（accept 1 另加 Maven 三参数），都在脚本头部注明。

## §3 ticket §4 要求的四项材料

### 3.1 六个外部身份各自的 `/mp/ext/sample/list` id 集合

```
extA  ["9000001001","9000001002","9000001003","9000001004"]
extB  ["9000001001","9000001002","9000001003","9000001004"]
extC  ["9000001005"]
extD  ["9000001006"]
extE  ["9000001007"]
extF  []
extA onlyMine=true                      ["9000001001","9000001002","9000001003"]
extB onlyMine=true                      ["9000001004"]
extB onlyMine=true&verifyStatus=pending []
extA sampleKind=organoid                []
```

（extA=extB 因为是同组双方都 verified；extC 同单位异组 → 只看自己；extE 与 A 同组但未核验 → 只看自己；extF 自填待核验 → 空。）

### 3.2 `ExtSampleDetailVo` 完整 JSON 一份（extA 看自己的 1001）

```json
{"id":9000001001,"submitNo":"SJ90000001","sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体甲","gender":"male","age":"56","hospitalNo":"ZY0000001","tissueType":"肝组织","organoidType":null,"hasPathology":"Y","remark":null,"verifyStatus":"valid","invalidReason":null,"submitterName":"王医生","mine":true,"editable":false,"createTime":"2026-08-21 10:00:00","embeds":[],"docs":[]}
```

键集合（jq `keys` 原序）：`age, createTime, docs, donorName, editable, embeds, gender, hasPathology, hospitalNo, id, invalidReason, mine, organoidType, remark, sampleKind, sourceUnitName, submitNo, submitterName, tissueType, verifyStatus` —— **没有** `internalNo`（默认关）/ `receiveDate` / `operatorName` / `verifyBy` / `frozenBy` / 冻存的一切。

同一个请求在开关打开后（`UPDATE sys_config SET config_value='true'` + `DELETE /system/config/refreshCache`）：

```json
{"hasInternalNo":true,"internalNo":"T-hli01"}
```

### 3.3 见 §2（文件清单 / 接口清单 / 迁移说明）。

### 3.4 accept 逐条 ✅/❌ + 关键输出 → 见 §4。

## §4 accept 逐条

### 4.0 关于 `--fresh-module` 与 Maven 三参数（本 agent 沙箱限制，等价证据）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` → 原样带 `--fresh-module` 恒 `exit 2`。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边（源码不得比 jar 新 + 进程不得早于 jar）：

```
jar mtime        : 2026-09-21 23:03:06 (epoch 1790002986)
lqg src newer    : []                    ← 没有比 jar 新的 ruoyi-lqg 源码
admin src newer  : []                    ← 同上（ruoyi-admin）
8081 PID         : 4174
PID holds jar    : 2                     ← lsof -p 4174 命中 ruoyi-admin/target/ruoyi-admin.jar
backend log mtime: 2026-09-21 23:06:53 (epoch 1790003213 > jar 的 1790002986)
嵌套 jar 复核     : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含本票全部新类
                   （ExtSampleController / ExtOrganoidController / ExtScopeServiceImpl /
                    ExtScopeService$ViewFlags / ExtSampleAssemblyService / ExtInternalNoSwitch /
                    MaskRules / ExtSampleVo / ExtSampleDetailVo / ExtSampleSubmitBo /
                    ExtOrganoidSubmitBo / ExtSampleQueryBo / ExtDocVo / ExtEmbedVo /
                    sample/service/ExtSampleSubmitService）
8080（Kevin）     : PID 80875 全程在，未碰
```

accept 1 那行 `mvn -q -pl … -am test -Dtest=ExtChokepointContractTest …` **原样跑在本机会挂**（`~/.m2` 只读），补了 `-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome` 三个参数。**这条 doc-drift 已连续多张票命中**，见 WARN-1。

### 4.1 accept 1 · 四条结构性不变量 —— ✅

```
$ bash doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc1.sh
== accept 1 · 四条结构性不变量 ==
1a cmp 逐字节未改: OK (sha256 6b1da29a7c127447…)
ACCEPT-1 EXIT=0
```

四段分别是：

| 段 | 命令 | 结果 |
|---|---|---|
| 1 | `cmp doc/verify/fixtures/java/ExtChokepointContractTest.java …/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java` | 无输出，exit 0（sha256 `6b1da29a7c12744798910c115be8981745253335a8db4c8b68a992a88567e812`） |
| 2 | `mvn -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true` | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS` |
| 3 | `grep -rlE '@SaCheckRole\("lqg_external"\)' …/lqg/ext \| wc -l` | `2`（`ExtSampleController` + `ExtOrganoidController`；`>= 2` 成立） |
| 4 | `! grep -rnE '"/mp/ext' …/main/java --include=*.java \| grep -v '/org/dromara/lqg/ext/' \| grep -q .` | 成立（`"/mp/ext` 只出现在 ext 包下的 4 个文件） |

**counterfeit 逐条排掉**（不是「看着像过了」）：

- 让外部 controller 直接返回 `SampleVo` → i2 要求叶子类型是 `ext` 包里的 `Ext*`，`SampleVo` 不在白名单 → 红。本票**没有**任何 handler 返回内部类型（`TableDataInfo<ExtSampleVo>` / `R<ExtSampleDetailVo>` / `R<Long>` / `R<Void>`）。
- `ExtSampleDetailVo extends SampleVo` + `@JsonIgnore` → i3 沿 `getSuperclass()` 扫，继承来的 `operatorName` / `verifyBy` / `internalNo` 全算 → 红。本票两个 Ext VO **都不 extends**。
- 在 ext 包的 `ExtSampleAssemblyService` 里注入 `SampleMapper` → i4 扫整个 ext 包的 `*Mapper` 字段 → 红。本票读写在 `sample/service` 与 `ExtScopeServiceImpl`（唯一豁免类）。
- 改测试文件让它过 → `cmp` 红（本票两次 `cmp` 都无输出）。
- ext 包名写错导致 i1/i2/i3 在空集合上空转 → 测试自带 `assertFalse(ext.isEmpty())` / `handlers > 0` / `vos > 0`；本票落地后三者都真扫到东西（4 个 `@RestController` 里的 6 个 handler、7 个 `Ext*Vo`）。
- controller 忘了类级角色注解 → 第 3 段红（grep 数 2 恰好是最低要求，两个外部 controller 各一个）。

### 4.2 accept 2 · 可见集合 / 越权 / 键集合 / 角色闸 —— ✅（正文 `/mp/int` 那段见下）

```
$ bash doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc2.sh
== accept 2 · 可见集合 / 越权 / 键集合 / 角色闸 ==
true
true
true
true
true
ACCEPT-2 EXIT=0
== 正文倒数第 2 段（/mp/int，上游未注册，预期红）==
MPINT-LITERAL EXIT=1 （1 = 该端点是 404 而不是 403，原因见 WARN-3）
```

逐段对应（正文 14 段全绿；`true` × 5 是五条 jq 断言的输出）：

| 段 | 断言 | 关键输出 |
|---|---|---|
| 1-10 | 六个身份 id 集合 + `onlyMine` 两档 + 状态/类别叠加空集 | 见 §3.1（`test` 静默通过） |
| 11 | `--as extC GET /mp/ext/sample/9000001001` | `{"code":404,"data":null}`（`.code==404` 且 `(.data//{}\|length)==0`） |
| 12 | `--as extE GET /mp/ext/sample/9000001001` | `{"code":404}` |
| 13 | `--as extA GET /mp/ext/sample/9000001010`（软删） | `{"code":404}` |
| 14 | `--as extB GET /mp/ext/sample/9000001001`（同组互看） | `{"code":200,…,"donorName":"测试供体甲","mine":false,"editable":false,"submitterName":"王医生", keys 差集 []}` |
| 15 | `--as extA GET /mp/ext/sample/list` 的 keys 断言 | `unique keys = ["createTime","donorNameMasked","editable","id","mine","organoidType","sampleKind","submitNo","submitterName","tissueType","updateTime","verifyStatus"]` → `internalNo/donorName/hospitalNo/operatorName` 全 null、`donorNameMasked` 命中 |
| 16 | `--bizcode GET /mp/ext/home` | `404	No endpoint GET /mp/ext/home.` |
| 17 | `--bizcode GET /lqg/sample/list`（外部） | `403	没有访问权限，请联系管理员授权` |
| 18 | `--bizcode GET /mp/ext/sample/list`（staff） | `403	没有访问权限，请联系管理员授权` |

> **★ 正文倒数第 2 段（`--as extA --bizcode GET /mp/int/sample/list` 期望 `^403`）本票跑红，逐字输出：**
> ```
> $ bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/sample/list'
> 404	No endpoint GET /mp/int/sample/list.
> ```
> **这不是本票的实现缺陷**：`/mp/int/**` 这一组端点由 **SAMPLE-MP-001**（小程序内部侧）注册，本票的边界（ticket §3）明确不许碰小程序内部页；契约第 49 行把 `GET /mp/int/sample/list` 列给那一票，第 19 行写明它 `@SaCheckRole("lqg_internal")`。**端点还不存在时，角色闸无从被触发**，只能拿到「路由不存在」的 404 —— 而 404 同样**没有**把任何内部数据交给外部（`.code != 200`）。
> 角色闸的**两个方向**本票都实测到了（探针 P11a-d / P12）：
> ```
> admin POST /mp/ext/organoid  → 403     staff POST /mp/ext/sample → 403
> staff PUT  /mp/ext/sample/{id} → 403   admin GET  /mp/ext/sample/{id} → 403
> extA  GET  /lqg/sample/list  → 403     extA  GET  /mp/int/sample/list → 404（端点未注册）
> ```
> 处置：**不改 `api.sh`、不删不改断言**，只把这一段在 runner 里隔离成独立函数单独报告。交主会话决定（WARN-3）。

**counterfeit 逐条排掉**：

- 按「来源单位名称相同」算可见（最直觉的写法）→ extC 会看到 A、B 的 1001-1004；实测 extC = `["9000001005"]` ✓（可见集合只按 `submitter_id`，不按单位名）。
- 同组判断漏了「双方都 `verified`」→ extE 会看到 1001-1004、或 extA 会看到 1007；实测 extE = `["9000001007"]`、extA 不含 1007 ✓（peers 查询带 `bind_status='verified'` 且双侧过滤）。
- `onlyMine` 写成「`create_by` = 本人」→ extB 会多出别人替他建的记录；实测 extB `onlyMine` = `["9000001004"]` ✓，且 `onlyMine&verifyStatus=pending` = `[]`（若按 create_by 会命中 1002）。
- `onlyMine` 被忽略（前端自己过滤）→ extA 带 `onlyMine` 仍有 1004 → 红；实测不含 1004 ✓。
- 列表过滤了但详情忘了 `assertVisible` → extC 取 1001 拿到 200 → 红；实测 `{"code":404,"data":null}` ✓。
- 越权返回 403 → `.code==404` 红；实测 404 ✓。
- 详情 VO 多带 `receiveDate` / `internalNo` / `operatorName` 任何一个 → keys 差集非空 → 红；实测差集 `[]` ✓。
- 列表给全名而不是掩码 → 红；实测 `donorNameMasked:"测**"`、且列表里**没有** `donorName` 键 ✓。
- `/mp/ext/home` 还留着 → 404 那段红；实测 `404 No endpoint` ✓。

### 4.3 accept 3 · 写保护 / 夹带 / 类别闸 / 无效重提 —— ✅

```
$ bash doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc3.sh
== accept 3 · 写保护 / 夹带 / 类别闸 ==
true
true
pending|-|external|-|-
true
tissue|external|pending|9000000113|true
true
organoid|external|pending|-|-|-|-|-
0
ACCEPT-3 EXIT=0
```

| 段 | 命令 | 输出 | 说明 |
|---|---|---|---|
| 2 | `--as extB --bizcode PUT /mp/ext/sample/9000001002` | `400 只能修改重提本人提交的样本（同组的样本可以看，但不能改）` | 同组可看不可改 |
| 3 | `--as extA --bizcode PUT /mp/ext/sample/9000001001` | `400 样本当前状态不允许外部修改重提（只有待核验、无效的样本可以）` | 已核验有效的自己也改不了 |
| 4 | `--as staff GET /lqg/sample/list` 的 jq | `true` | **被拒之后走内部接口读出来的真实值仍是** `["测试供体乙","测试供体甲"]`（没被篡改） |
| 5 | `--as extA PUT /mp/ext/sample/9000001003`（夹带 6 个内部字段） | `.code==200`（`true`） | 无效样本重提成功 |
| 6 | `db.py … WHERE id=9000001003 --eq "pending\|-\|external\|-\|-"` | `pending\|-\|external\|-\|-` | 回 `pending`、`invalid_reason` 清空；夹带的 `internalNo`(`T-hack01`)/`submitSource`(`internal`)/`receiveDate` **一个都没落库** |
| 7 | `--as extC POST /mp/ext/sample`（胃组织） | `.code==200`（`true`） | 服务端写死 `tissue/external/pending` |
| 8 | `db.py … WHERE tissue_type='胃组织' AND submitter_id=9000000113` | `tissue\|external\|pending\|9000000113\|true` | `submit_no ~ '^SJ[0-9]{8}$'` 成立 |
| 9 | `--as extC POST /mp/ext/organoid`（夹带 5 个内部字段） | `.code==200`（`true`） | 类器官收样成功 |
| 10 | `db.py … WHERE organoid_type='胃类器官'` | `organoid\|external\|pending\|-\|-\|-\|-\|-` | `internal_no`/`receive_date`/`has_viability_report`/`operator_name`/`donor_name` **全空** |
| 11 | `--as extD --bizcode PUT /mp/ext/organoid/${OID}` | `404 样本不存在` | 外单位越权（404 不泄露存在性） |
| 12 | `--as extC --bizcode PUT /mp/ext/sample/${OID}` | `400 这条记录是「类器官」类样本，不能从「组织」的入口修改` | 类别闸 |
| 13 | `db.py … count(*) … --eq 0` | `0` | 两次被拒后：`organoid_type='被外单位改'` 0 行 **且** 类器官样本的 `donor_name` 仍为 NULL |
| 14 | `reseed.sh --yes` | 静默 | 收尾 |

**counterfeit 逐条排掉**：

- PUT 只校验「可见」不校验「本人」→ 段 4 会读到「被同组人篡改」→ 红；实测仍是原值 ✓。
- 不校验状态 → extA 改掉已核验的 1001 → 段 3 拿到 200 → 红；实测 400 ✓。
- 外部 BO 复用内部 `SampleSubmitBo`（「读到了再忽略」）→ 段 6 会看到 `T-hack01` / `internal` / `2026-09-17` → 红；实测三个都是 `-` ✓（本票的做法是**根本没有这些键**，反序列化不到任何地方，见 §1 口径 4）。
- 类器官收样复用内部 BO → 段 10 的五格会落库 → 红；实测全 `-` ✓。
- 外部类器官 PUT 没过 `assertVisible` → extD 改掉 extC 的记录 → 段 13 的 count 非 0 → 红；实测 404 且 count=0 ✓。
- 组织样本 PUT 不校验 `sample_kind` → 段 12 会 200、段 13 的 count 非 0 → 红；实测 400 ✓。
- 重提后没清 `invalid_reason` → 段 6 末位不是 `-` → 红；实测 `-` ✓（清空走 `LambdaUpdateWrapper.set(col, null)`，避开 `updateById` 忽略 null 的坑）。
- **被拒的几段后面跟的都是真实值**：段 4 走内部接口 `/lqg/sample/list` 读、段 6/8/10/13 直连库读（`db.py` 只读执行器，写 SQL 会被护栏挡下 —— 实测 `[error] 只接受 SELECT / WITH 开头的查询`）。

### 4.4 追加证据（accept 之外的机器证据）

**(a) Java 全模块测试：`Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`**

```
$ (cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg -am test -s <ws>/.mvn-settings.xml …)
[INFO] Tests run: 11 … ExtBindStateMachineContractTest
[INFO] Tests run:  6 … StaffGrantRulesContractTest
[INFO] Tests run:  4 … MockLoginGuardContractTest
[INFO] Tests run:  4 … ExtChokepointContractTest      ← 本票的 L0 硬门
[INFO] Tests run:  8 … VerifyTransitionsContractTest
[INFO] Tests run:  6 … SampleKindRulesContractTest
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**(b) 对抗性探针 27 条：`PROBE EXT001 PASS=27 FAIL=0`**

```
PASS P1-列表排序-COALESCE倒序 — [9000001002,9000001003,9000001004,9000001001]
PASS P2a-开关关-详情无internalNo键 — false
PASS P2b-开关开-详情有internalNo键 — true|T-hli01
PASS P2c-开关还原-详情又无该键 — false
PASS P3-POST组织-夹带内部字段不生效 — tissue|external|pending|9000000116|-|-|-|-|-|-|true
PASS P4-POST类器官-夹带全不生效 — organoid|external|pending|-|-|-|-|-|-|-|-|-|-|-
PASS P5-重提-回pending且清痕迹且夹带不生效 — pending|-|-|-|-|-|-|external|重提了
PASS P6a-组织入口改类器官-业务码 — 400      PASS P6b-…-库里不变 — -|-|肝类器官
PASS P7a-外单位改-404 — 404                  PASS P7b-…-库里不变 — valid|lGSKaQVYm8UmBZ296TFGDw==
PASS P8a-同组未核验改-404 — 404              PASS P8b-…-库里不变 — pending|HXIVgGKK4X1fEZO12s896w==
PASS P9a-自己的valid改-400 — 400             PASS P9b-…-库里不变 — valid|lGSKaQVYm8UmBZ296TFGDw==
PASS P10a-软删详情-404 — 404                 PASS P10b-软删不在列表 — false
PASS P11a-admin打POST-organoid-403 — 403     PASS P11b-staff打POST-sample-403 — 403
PASS P11c-staff打PUT-sample-403 — 403        PASS P11d-admin打GET-详情-403 — 403
PASS P12-外部打lqg-403 — 403                 PASS P12b-外部打mpint-未越权 — 404
PASS P13a-POST组织缺组织类型-500 — 500      PASS P13b-POST类器官缺类器官类型-500 — 500
PASS P13c-选了单位-id-单位名取快照 — 9000009002|B 大学
PASS P13d-来源单位不存在-500且不落库 — 500|0
```

**`onlyMine` 的最强反证**（单跑，节选 §3.1 之外的一档）：`extB --onlyMine=true` 只回 1004 —— 若 `onlyMine` 不过可见集合而直接查 `submitter_id = 9000000112`，结果一样；但把它写成「`create_by` = 本人」或「忽略可见集合」时，extB 会多出 1002（`create_by=9000000111`）/ 1001-1003。而 extB 的**整集**（不带 `onlyMine`）恰为 1001-1004、**与 extA 完全相同** —— 两个方向都被钉住。

## §5 遗留与 raise（ticket §4.5）

### 5.1 越出 `touches` 的改动

**只有一处，且是「touches 之外但同模块内」的既有一行方法新增**：

| # | 文件 | 为什么必须改 |
|---|---|---|
| 1 | `ruoyi-lqg/.../sample/service/SampleQueryService.java` | ticket 的 `touches` 只列 `ext/**` 与 `application*.yml`。但 ADR-0004 的 I4 规定 ext 包里除 `ExtScopeServiceImpl` 外任何类不得持 `*Mapper`，所以「外部按可见 id 集合查一页」这个**读操作**必须落在 sample 包 → 给已有 service 加了一个 `selectExtPage(Page, LambdaQueryWrapper)`（无副作用、不改已有方法签名、不影响内部列表）。这是 i4 逼出来的结构，不是顺手的重构。 |

**没有动**：`ruoyi-lqg/pom.xml`（spring-test 已由 AUTH-GROUP-001 加好）、`ruoyi-admin` 任何源码、上游 `ruoyi-system` / `ruoyi-common-*` 一个字节、`application*.yml`（**本票一个配置项都没加** —— `lqg.ext.show-internal-no` 走 sys_config）、前端 / 小程序（本票不碰端）、Flyway（无迁移）。

**AUTH-GROUP-001 的两个已有 controller（`ExtUnitController` / `ExtProfileController`）一个字节没动** —— ticket §2.1 那句「若不在本包，搬进本包并补上同样的类级注解」的**条件不成立**（它们本来就在 `org.dromara.lqg.ext.controller`），而给它们补注解会改掉 AUTH-GROUP-001 已验收的「内外部都能拉选择器」行为（跨票行为变更）。ticket 的 accept 第 1 条第 3 段只要求「ext 包下 `@SaCheckRole("lqg_external")` ≥ 2 处」，本票新增的两个外部 controller 就是那 2 处（+0 来自已有文件）。**这一条作为口径分歧记在 WARN-6 交主会话裁定。**

### 5.2 与 `doc/api-contract.md` 的差异

**没有差异。** 逐行核过：

| 契约行 | 实现 | 判断 |
|---|---|---|
| 第 50 行 list 形状 | `ExtSampleVo` 12 键逐字一致（含 `donorNameMasked`、`mine`、`editable`） | ✅ |
| 第 51 行 detail 形状 | 19 键逐字一致；`internalNo` 按开关出现；`embeds`/`docs` 返回 `[]` | ✅ |
| 第 52 行 POST/PUT 组织样本 | 只收送检段字段；PUT 仅本人 pending/invalid，成功回 pending | ✅ |
| 第 53 行 POST/PUT 类器官 | `ExtOrganoidSubmitBo{sourceUnitId?, sourceUnitName, organoidType, remark?}`；夹带的 `internalNo/receiveDate/verifyStatus` 不生效 | ✅ |
| 第 20 行「`/mp/ext/**` 是唯一边界」 | 4 个外部 controller 全在 ext 包、路径全以 `/mp/ext` 开头 | ✅ |
| — | `GET /mp/ext/home` | 契约里**没有**列它（CR-20260917-05 已删），本票返回 404 ✅ |

契约里**没有**写 `/mp/int/**` 的注册票 —— 第 49 行把 `GET /mp/int/sample/list` 列给了小程序内部侧（SAMPLE-MP-001）。这正是 §4.2 那段红的原因（WARN-3）。

### 5.3 口径上没把握 / 需要确认的两点

1. **软删的外部档案（`t_lqg_ext_profile.del_flag='1'`）还会不会参与「同组互看」**：本票让 `@TableLogic` 把它滤掉（`selectOne` / `selectList` 都自带 `del_flag='0'`），所以「档案被软删的人提交的样本」别人看不到 —— 但本人仍看得到（`visibleSampleIds` 里本人 id 无条件进集合）。若甲方要「档案删了连本人也不许看」，那是另一条口径（目前没有删档案的接口）。
2. **`editable` 的判据只到「本人 + pending/invalid」，没查「样本名下有没有下游记录」**：`invalid → pending` 这条边在 `VerifyTransitions` 里对**外部**是放开的（SAMPLE-VERIFY-001 定的表）。所以一条「曾经 valid、名下已有石蜡包埋 / 冻存 / 质控文档、后来被内部改判 invalid」的样本，外部重提会让它回 `pending` —— 而那些下游记录还挂着。本票按转移表实现（表是唯一判据），没有另加「有下游记录的 invalid 不许外部重提」的闸。**这一条会影响 EMBED/CRYO/QC 域**：请主会话核 `invalid → pending` 是否要在「有下游记录」时只放内部重提。

### 5.4 明确没做（ticket §3 边界逐条核对）

不做外部石蜡包埋送样（AUTH-EXT-002）｜不做外部看文档 / 下载（AUTH-EXT-003）｜不做小程序页面（SAMPLE-MP-001 / 002）｜不给外部任何 Excel 导出 / 统计数字 / -80 冻存信息（本票的 VO 里一个冻存字段都没有）｜不做「单位管理员看本单位全部样本」（同单位异组实测不互看）｜不做外部首页（已删）。

## §6 验证用的长进程

- **后端 java（8081）**：**报告写完前仍在跑**（PID 4174），交主会话在收尾时按 PID 关：`lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID → `kill`。**没有用 `pkill -f`**（8080 上有 Kevin 的 java 服务 PID 80875）。
- **8080 / 5432 / 6379**：全程没碰。
- **5433（`lqg-dev-postgres`）/ 6380（`lqg-dev-redis`）/ 9002-9003（`lqg-dev-minio`）**：D2 前就在跑，留着给后续 ticket，**没停**。
- **DB 收尾**：`reseed.sh --yes` + `clean-orphan-accounts.sh --yes`（见 §7）。
- **没有起任何前端 / 小程序 dev server**（本票不碰端）。

## §7 收尾状态

```
$ bash doc/verify/reseed.sh --yes
reseed 完成
$ bash doc/waves/tools/clean-orphan-accounts.sh --yes
（无输出 = 无孤儿账号可清）
$ python3 doc/verify/db.py --sql "SELECT config_key||'='||config_value FROM sys_config WHERE config_key LIKE 'lqg.%'"
lqg.cryo.overdue-days=14
lqg.ext.show-internal-no=false      ← 探针 P2 打开过，已还原成默认 false
```

## §8 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | S2 | harness | **ticket 的 accept 里 `mvn … test` 那行在本机原样跑必挂**（`~/.m2` 只读），要补 `-s .mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome` | **本票是第 6+ 张命中的**（SYS-BASE / AUTH-LOGIN-001 / SYS-WEB-001 / AUTH-STAFF-001 / AUTH-GROUP-001 / 本票）。只影响本机 subagent 沙箱。本票在 runner 头部显式记了「差异只有这一处」并跑了原样的其余部分。方案：ticket 生成器把 Maven 行统一写成带三参数的模板，或把 `.mvn-settings.xml` 放进 `~/.mavenrc`。 |
| WARN-2 | S2 | doc-drift | **`ExtChokepointContractTest` 的 I3 禁用字段表不含 `internalNo`**，注释里写「internalNo 不进这张表：它由系统参数在装配时决定给不给」 | 这是**刻意的设计**（`internalNo` 不是永远禁，而是开关控制），但后果是：**I3 不会因为某个 `Ext*Vo` 声明了 `internalNo` 而红** —— 本票靠 accept 2 第 15 段的 keys 差集断言兜住。下游 AUTH-EXT-002 的 `ExtEmbedVo` 如果加了 `internalNo` 却忘了接开关，i3 不红、而那一票的 accept 若没做 keys 差集就会假绿。方案：在 ADR-0004 白名单里补一句「`internalNo` 由**它自己的** keys 差集断言守，i3 守不了」，或在 fixture 里加一条「只有 `ExtSampleDetailVo` / `ExtEmbedVo` 允许声明 internalNo 且必须 `@JsonInclude(NON_NULL)`」的断言（**改 fixture 属需求层，本票没动**）。 |
| WARN-3 | S2 | blocked-upstream | **ticket accept 2 倒数第 2 段（外部打 `/mp/int/sample/list` 必须 403）在 D2 跑不了**：该端点由 SAMPLE-MP-001 注册，本票边界不许碰小程序内部侧 → 实测 `404 No endpoint GET /mp/int/sample/list.` | 影响**任何**在 SAMPLE-MP-001 之前跑的票的「外部打 `/mp/int/**`」断言（本票是第一个撞上的）。本票按规矩没删没改断言，只在 runner 里隔离单跑并写明理由；角色闸两个方向都另有实测（P11/P12）。方案：① 把那段断言的期望从 `^403` 改成「`^403` 或路由未注册的 `^404`」并在备注里写明 SAMPLE-MP-001 落地后收紧；② 或给该断言加 `@requires SAMPLE-MP-001` 的前置标注；③ **不要**为了让它绿而在本票注册一个空的 `/mp/int/**` 端点（那正好是 ADR-0004 要避免的「多一个没人维护的出口」）。 |
| WARN-4 | S3 | harness | `doc/verify/db.py` **是只读执行器**（`[error] 只接受 SELECT / WITH 开头的查询——断言不改库`，但 **exit 0**） | 探针 P2 要改 `sys_config` 再改回来，没法用 `db.py`，改用 `psql`（`verify.env` 的 `LQG_DB_*` + `PGPASSWORD`）。**exit 0 这点值得注意**：如果有人误以为「`db.py` 返回 0 = SQL 执行成功」，会把「没执行」当成「执行了」。方案：把该错误分支的退出码从 0 改成 2（与 README 的「2 = 用法 / 连接 / SQL 错」一致）。 |
| WARN-5 | S3 | clarify | **`invalid → pending`（外部重提）没有「名下有下游记录就只放内部」的闸**（见 §5.3 第 2 点） | 影响 EMBED / CRYO / QC 域：一条被改判 `invalid` 的样本，外部重提回 `pending` 后，它名下已存在的石蜡包埋 / 冻存 / 质控文档还挂着。本票按 `VerifyTransitions`（唯一判据）实现，没另加闸。方案：请主会话确认——若甲方要挡，应在 `SampleVerifyService.resubmitByExternal` 复用 `SampleChildrenCheckers`（`valid → invalid` 那条路已经在用它）。 |
| WARN-6 | S3 | clarify | **ticket §2.1 那句「`/mp/ext/units`、`/mp/ext/profile` 若不在本包，搬进本包并补上同样的类级注解」在本票落地时无从执行**：这两个 controller **本来就在** `org.dromara.lqg.ext.controller` 下（AUTH-GROUP-001 建的），句子的**条件不成立** → 本票**没动它们**（它们也不在 `touches` 的 `ext/**` 之外的语义里，但动它们会改掉 AUTH-GROUP-001 已验收的行为） | 实测它们现在**没有**角色注解（`grep -c SaCheckRole` 都是 0），访问矩阵：<br>`admin GET /mp/ext/units → 200` · `staff GET /mp/ext/units → 200` · `extA GET /mp/ext/units → 200`（内部也能拉选择器，与 AUTH-GROUP-001 的注释一致）；<br>`staff PUT /mp/ext/profile → 500 请在列表里选择单位与组别…`（走到业务校验，**不是** 403 → 内部也能调这个写端点）。<br>**本票新增的** `/mp/ext/sample/**` 与 `/mp/ext/organoid/**` 是**只给外部**（类级 `@SaCheckRole("lqg_external")`，内部 403，实测 P11a-d）。<br>方案：请主会话二选一 —— ① 认为「选择器 / 档案内外部共用」是需求 → 保持现状，在 ADR-0004 或契约第 35-36 行补一句说明；② 认为「`/mp/ext/**` 只给外部」→ 另开一张小票给这两个 controller 补注解（**不要塞进本票**：那会让 AUTH-GROUP-001 的验收结论失效，属于跨票行为变更）。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 在沙箱恒 exit 2（AUTH-GROUP-001 WARN-1）、`api.sh` token 缓存只看 mtime（AUTH-STAFF-001 WARN-4）、MyBatis-Plus `updateById` 忽略 null（AUTH-GROUP-001 WARN-2，本票写侧一律用 `LambdaUpdateWrapper.set(col, null)`）、`reseed.sh` 清不掉雪花 id 账号（AUTH-STAFF-001 WARN-7）。

## §9 坑与解法（3-5 行，给下游）

1. **`LambdaQueryWrapper` 在 MyBatis-Plus 3.5.16 上没有「按列名排序」的重载**：它的 `Func` 接口只留 `SFunction` 版本（`orderByDesc(R, R...)`，`R = SFunction<Sample,?>`），票面要的 `COALESCE(update_time, create_time)` 是**表达式**不是列引用 → 编译期直接 `no suitable method found for orderByDesc(String)`。解法：`wrapper.last("ORDER BY COALESCE(update_time, create_time) DESC, id DESC")`（分页 `LIMIT` 加在它之后，不冲突）。**下游要按表达式排序的票（冻存 `flow_time` 累计、文档 `published_time`）先试编译，别照老版 API 写。**
2. **`donorName` / `hospitalNo` 是密文**：直接从 `Sample` 实体打码会得到 `"T**"` 这种垃圾（本票第一版实测如此）。凡是从**实体**取这两列给外部看，必须先过 `SampleFieldCipher.decrypt`；只有走 `SampleQueryService.toVo`（内部 VO）才是已解密的。**列表 + 详情两条路都要各自确认一次。**
3. **`@SaCheckRole` 不挡「方法不匹配」的请求**：`GET /mp/ext/organoid` 在内部账号下返回 **405 `Request method 'GET' is not supported`**（Spring 在 handler mapping 阶段就拒了，角色切面根本没跑）。所以「内部打外部必须 403」的断言**只能打在真实存在的方法上**（POST/PUT/GET 具体路径），测不存在的 `METHOD+PATH` 组合只会看到 405/404。accept 2 最后两段都恰好是真实端点，所以绿。
4. **`sys_config` 的读是带 Redis 缓存的**（`selectConfigByKey` 上的 `@Cacheable(CacheNames.SYS_CONFIG)`）：直接 `UPDATE sys_config` 后**不会**立刻生效，必须走 `DELETE /system/config/refreshCache`（或工作台的参数设置页保存，那条路径带 `@CachePut`）。探针 P2 踩过：改库 → 详情仍无 `internalNo` → 加一次 refresh → 立刻出现。**「运行时可改」这句口径依赖的是缓存失效，不是每次读库。**
5. **`--fresh-module` 之外，`api.sh` 的 token 缓存也会误导**：`$TMPDIR/lqg-verify-token-*` 只按 mtime 判 20 分钟新鲜。改库 / 改身份后不清缓存会拿到旧 token（表现为假 401/403，看着像实现坏了）。三条 runner 与探针开头都 `rm -f`。另外 **accept 的长链必须包进函数判整条 rc**：`set -e` 不管非末尾位置的失败，`a && b && c` 里 `b` 挂了会静默 `exit 0`（本票三份 runner 都这么写，acc2 第一版正是靠它把那段红暴露出来的）。
