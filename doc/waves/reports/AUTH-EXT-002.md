# AUTH-EXT-002 · 完工报告

- **ticket**：AUTH-EXT-002（track AUTH / phase D3 / size M）—— D3 的第三张，外部隔离咽喉的**石蜡包埋送样**那一半
- **status**：**done**
- **accept**：**3/3 绿**（三条 `run` 逐条实跑；唯一差异 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg` + Maven 补三个参数，等价证据见 §4.0）
  - 另有对抗性探针 **26/26 PASS**（`probes/ext002-probes.sh`）
- **分支**：`task/D3`（已切好；**未切分支 / 未 push / 未 merge**；未动 `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar` @ `2026-09-22 08:46:31`（epoch 1790037991），后端进程 **PID 79986**（8081，dev profile + `--api-decrypt.enabled=false`）
- **迁移**：**1 支** —— `V202609230902__AUTH-EXT-002-ext-show-internal-no.sql`（幂等插入 `lqg.ext.show-internal-no`；见 §2.1 的取号依据与 ★ out-of-order 处置）
- **只读区未动**：`doc/verify/{api.sh,reseed.sh,db.py,seed/**,gen_seed.py,fixtures/**,verify.env}`、`doc/requirements.yaml`、`doc/authority/**`、`doc/change-log.md`、`doc/api-contract.md`、`doc/lint-profile.yaml`、`doc/waves/state.json`、`_manifest.json`
- **没碰**：8080（Kevin 的本机服务）/ 5432 / 6379；关进程一律按 `lsof -ti tcp:8081 -sTCP:LISTEN` 拿 PID 再 `kill`（**没用过 `pkill -f`**）
- **产物**：后端 **5 个新类**（controller / service / marker VO / 提交 BO / 筛选 BO）+ **1 个新测试** + **2 处既有类的小改**（`ExtEmbedVo` 满配、`ExtSampleAssemblyService` 接上 embeds）；小程序 **1 个新组件** + **3 个既有文件的小改** + 1 支 Flyway 迁移

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 当前分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D3`（全程没切分支） |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `state.json`：`EMBED-MODEL-001` / `AUTH-EXT-001` / `SAMPLE-MP-001` 都 `done`；`EmbedExternalService`（`submit` / `resubmit` 两个重载）、`EmbedQueryService.{detail,listBySampleId,listBySampleIds,entity,pageByWrapper}`、`ExtScopeServiceImpl`、`ExtInternalNoSwitch`、`ExtSampleAssemblyService` 全部在盘并被本票真调用 |
| 扫 `doc/change-log.md`：涉及本票的 CR | ✅ PASS | 顶部两条直接相关：**CR-20260918-07**（⑦ 外部可见操作人与包埋人、内部编号开关 `lqg.ext.show-internal-no` 改成运行时 sys_config）与 **CR-20260917-05**（外部可提交石蜡包埋送样）。逐条落进实现，见 §1 |
| 8 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | 8 个锚全部 `active`；`FLOW:F-EMBED-01.step4` 的 action 正文**已经是 CR 之后的口径**（含「操作人与包埋人（CR-20260918-07 按甲方意见放开）…内部编号按系统参数 lqg.ext.show-internal-no 决定」）；`ADR-0004` 的 decision 也已重写（`implementation_status` 仍是 `not-yet-implemented`，见 §6） |
| §0 的「连带前置」已满足 | ✅ PASS | `doc/verify/fixtures/java/ExtChokepointContractTest.java:50-51` 已有 `BANNED_EXEMPT = Map.of("ExtEmbedVo", Set.of("operatorName","embedBy"))`；`cmp` 逐字节无差异（sha256 `6b1da29a7c12744798910c115be8981745253335a8db4c8b68a992a88567e812`）。**本票一个字节没动这份 fixture** |
| 契约测试动手前就绿 | ✅ PASS | 开工第一次 `mvn … test -Dtest=ExtChokepointContractTest` → `Tests run: 4, Failures: 0`；收工仍绿（accept 1 链内复跑） |
| 环境可用（8081 / PG 5433 / Redis 6380 / MinIO 9002-9003） | ✅ PASS | 三个 docker 容器全程在跑，**没停**；后端起停只按 PID |

**STOP 判定：无。** 三类硬阻塞（上游产物缺失 / 与权威冲突且无法判断 / 环境不可用）一条都没出现。

### 本票遇到并已按项目先例处置的一件环境事（不是 STOP）

迁移号 `V202609230902` **小于**已应用的 `V202609231100`（EMBED-MODEL-001），而 `spring.flyway.out-of-order=false`
→ 直接启动会 `FlywayValidateException`。这正是 **issue #12**（SYS-WEB-001 先例）记过的形态。
**处置照派单**：`DROP DATABASE lqg_dev; CREATE DATABASE lqg_dev OWNER lqg;` → 启后端让 **12 支迁移按版本号顺序全量重跑**
（日志：`Successfully applied 12 migrations … now at version v202609231100`）→ `reseed.sh --yes` 灌 seed。
**没有改 `out-of-order` 配置，也没有改 `doc/lint-profile.yaml`**（prod 语义与权威段落都要保持）。
重启时 Flyway `Successfully validated 12 migrations`。这条会在 AUTH-EXT-003 上**再撞一次**，见 WARN-1。

---

## §1 口径复述（ticket §0 点名的 6 点，逐条对 accept 核）

| # | 口径 | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **对外的标识是石蜡块编号**；`ExtEmbedVo` 是**白名单键集合**；要有 `operatorName` 与 `embedBy`（CR-20260918-07）；**没有**内部编号 / 备注 / 核验人 / 冻存 | `ExtEmbedVo`（24 键，**不 extends** 内部 `EmbedVo`）；`ExtEmbedMarkerVo`（只有 `markerName` / `expression`，把内部 VO 的 `sort` 挡在外面） | accept 1 的 keys 差集 == `[]`（diff 输出 `[]`）；`ExtEmbedShapeContractTest` 用反射断「字段集合**恰等于**那 24 键」；探针 P15a/P15b |
| 2 | **仍然走咽喉**：列表先取 `visibleSampleIds` 再按样本 id 集合调 embed 域 service；单条先按记录取 `sampleId` 再 `assertVisible`；ext 包不碰 `EmbedMapper` | `ExtEmbedAssemblyService`（**零 `*Mapper` 字段**）：可见集合走 `ExtScopeService`，记录走 `EmbedQueryService`（embed 包）；`ExtEmbedController` 只转发 | `ExtChokepointContractTest` 4 条全绿（i4 扫整个 ext 包，本票新增类里没有一个 `*Mapper` **字段**；`ExtEmbedVo` / `ExtEmbedMarkerVo` / `ExtEmbedSubmitBo` / `ExtEmbedQueryBo` 四个类文本里连「Mapper」这个词都没有，`ExtEmbedAssemblyService` / `ExtEmbedController` 只在注释里提到它）；accept 1 的 `extC GET /mp/ext/embed/9000002001 → 404` |
| 3 | **提交只许挂本人送检过、没被判无效的样本**（同组别人看得见但不能替他送样）；入参 `ExtEmbedSubmitBo` **只有** `sampleId / sampleType / organoidSourceType` | `ExtEmbedSubmitBo`（3 键）；service 用 `EmbedExternalService.submit(userId, sampleId, sampleType, organoidSourceType)` | accept 2 第 1-3 段：extB 替 extA 的 1001 → `400 只能挂本人送检过的样本`；extA 挂自己已无效的 1003 → `400 这条样本已判无效…`；extC 挂看不见的 1001 → `400`（不泄露存在性）；**被拒三次后 external 记录数仍是 1** |
| 4 | **改后重提只许本人的待核验 / 无效**（同组可看不可改；内部录入的石蜡块外部永远只读）；service 签名里**没有** blockNo / 日期 / 染色 / marker / 状态 | `ExtEmbedController#resubmit` → `EmbedExternalService.resubmit(userId, embedId, sampleId, sampleType, organoidSourceType)` | accept 2 第 7-8 段：extB 改 extA 的 2006 → 400、extA 改实验室录的 2001 → 400，`count(*) IN ('被同组人改','改实验室的块') == 0`；探针 P9b/P10a/P11a/P12a（含「核验有效后 PUT 被拒」「换挂别人的样本被拒且 `sample_id` 不变」） |
| 5 | **详情里的 `embeds` 含外部提交还没核验的送样**（`paraffinBlockNo` 空、带状态与无效原因）；`mine` / `editable` **逐行算** | `ExtSampleAssemblyService.detail` → `ExtEmbedAssemblyService.embedsOfSample(userId, sampleId)`；`mine` = **本条记录的** `submitter_id == 我`，`editable` = `mine && pending/invalid` | accept 1：extA 看 1002 → `[[null,"pending",true,true]]`；extB 看 1002 → `[["pending",false,false]]`；探针 P8a/P8b（单条详情同一判据）、P9a（核验有效后 `editable` 变 false） |
| 6 | **内部编号开关读运行时 sys_config，一处读一处判**（缺行按 false）；关着**连键都不出**；开着也只给**样本详情**上的内部编号，列表行照旧按送检单号指代；待核验的样本不许现编 | 读：`ExtInternalNoSwitch`（AUTH-EXT-001 已落，注入 `ConfigService`，唯一读点）；判：`ExtSampleAssemblyService.detail` 一处；`ExtEmbedVo` **根本没有**这个字段 | accept 1：关 → `has("internalNo")==false`；开 → `T-hli01`；1002（待核验，无编号）→ `.data.internalNo // null == null`；再关 → 键又没了。探针 P15a（开关**开**着时包埋 VO 也没有这个键）、P15b（包埋列表不给内部编号） |

另外两条 CR 口径：

- **CR-20260918-07**：① 包埋卡片「看得到操作人、包埋人，看不到冻存信息」→ `ExtEmbedVo.embedBy` / `operatorName` 真装配（seed 2001 的两个值都是「李工」，accept 1 逐字断 `"李工"`；冻存类键在本 VO 里一个都没有）。② 内部编号按要求走 sys_config **运行时**生效（accept 1 用 admin `PUT /system/config` 改，下一次读即生效 —— 走的是带 `@CachePut` 的那条路）。
- **CR-20260917-05**：外部可提交石蜡包埋送样（`FLOW:F-EMBED-01.step6`）→ 本票落 `/mp/ext/embed` 的四个端点；写侧 service 是 EMBED-MODEL-001 落的，签名即白名单。

---

## §2 改了哪些文件（ticket §4.4）

### 2.1 迁移（Flyway，**1 支**，在 `touches` 的 `V20260923090*__AUTH-EXT-002-*.sql` 里）

**`code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V202609230902__AUTH-EXT-002-ext-show-internal-no.sql`**（新增，25 行）

**取号依据 `doc/lint-profile.yaml`**：`D3 = 20260923`；HHmm 按域分段 **AUTH = 09xx**；0902 在本域未被占用（既有：0910 / 0920 / 0930）。
**内容**：一段 `INSERT … SELECT … WHERE NOT EXISTS (config_key='lqg.ext.show-internal-no')`（`config_id=5001`、`config_value='false'`、`config_type='N'`、参数名「外部页面显示内部编号」）。
这一行本来由 SYS-WEB-001 的 `V202609210830` 插入（本票只读它）；本支是**幂等兜底**、**绝不覆盖已有值**（甲方可能已在工作台里把开关打开）。
实测（重建库后全量重跑）：V202609210830 先插 → 本支 `WHERE NOT EXISTS` 命中跳过 → `sys_config` 里仍是 `lqg.ext.show-internal-no=false|5001|合作单位可见内部编号`（一个键一行，没有重复插）。

★ **out-of-order 处置见 §0** —— 本票**没有**改 `application.yml` 的 `out-of-order: false`，也没有改 `doc/lint-profile.yaml`。

### 2.2 后端（`ruoyi-lqg`，包 `org.dromara.lqg.ext`）

**新增**

| 文件 | 职责 |
|---|---|
| `ext/controller/ExtEmbedController.java` | `/mp/ext/embed`：list / {id} / POST / PUT，**类级 `@SaCheckRole("lqg_external")`**；只转发不查库 |
| `ext/service/ExtEmbedAssemblyService.java` | 拼装（**零 `*Mapper` 字段**）：可见集合 → embed 域读口 → 对外 VO；`mine`/`editable` 与 `onlyMine` 同一处判据 |
| `ext/domain/vo/ExtEmbedMarkerVo.java` | marker 对外形状，只有 `markerName` / `expression`（内部 `EmbedMarkerVo` 的 `sort` 不外泄） |
| `ext/domain/bo/ExtEmbedSubmitBo.java` | 提交 / 重提入参，**只有** `sampleId / sampleType / organoidSourceType` |
| `ext/domain/bo/ExtEmbedQueryBo.java` | 列表筛选，**只有** `onlyMine / verifyStatus`（显式无参构造转调 `PageQuery`） |
| `src/test/java/.../ext/ExtEmbedShapeContractTest.java` | **7 个用例**：VO 字段集合恰等于 24 键白名单 / 永不含内部专用键 / 保留 `operatorName`+`embedBy` / marker 两键 / 提交 BO 三键 / 筛选 BO 两键 / controller 角色注解与路径 |

**修改**

| 文件 | 改动 | 为什么必须改 |
|---|---|---|
| `ext/domain/vo/ExtEmbedVo.java` | 占位（6 键）→ **满配 24 键白名单**（含 `submitNo` / 七个工序时间 / `sectioned` / `stainTypes` / `markers` / `invalidReason` / `submitterName` / `mine` / `editable` / `embedBy` / `operatorName`；**不含** `internalNo` / `remark` / `verifyBy`） | AUTH-EXT-001 把它留成占位并写明「AUTH-EXT-002 加满字段」（契约第 64 行） |
| `ext/service/ExtSampleAssemblyService.java` | 注入 `ExtEmbedAssemblyService`；`detail()` 里 `vo.setEmbeds(List.of())` → `embedsOfSample(userId, sampleId)`；顺手删掉因此不再引用的 `ExtEmbedVo` import | ticket §0 口径 5（详情要含待核验送样） |

**接口清单（本票新增，全部类级 `@SaCheckRole("lqg_external")`）**

```
GET  /mp/ext/embed/list?onlyMine=&verifyStatus=&pageNum=&pageSize=   可见样本下的全部未删记录（含待核验送样）
                                                                    按 COALESCE(update_time, create_time) DESC, id DESC
GET  /mp/ext/embed/{id}                                              先按记录取 sampleId，再 assertVisible；不可见 404
POST /mp/ext/embed        {sampleId, sampleType, organoidSourceType} → external / pending / 编号空
PUT  /mp/ext/embed/{id}   同形；仅本人的 pending / invalid，成功后回 pending
```

**没有新增/修改任何 `*Mapper`、没有动 `EmbedExternalService` 一个字节、没有动 `ExtScopeService(Impl)` / `ExtInternalNoSwitch`** —— 上游签名即白名单，本票只做入口与装配。

### 2.3 小程序（`code/miniapp`）

| 文件 | 说明 |
|---|---|
| `src/components/lqg/EmbedCard.vue`（**新增**） | 一张包埋卡片：编号等宽（`lqg-mono`）、「已切片」徽标、无效红条带原因、七个工序时间行、染色与 marker 徽标、**操作人 / 包埋人**；没编号时抬头显示「待核验」/「无效」并出 `StatusChip`。零色值字面量（只用 `src/style/tokens.scss` 的变量与 `components.scss` 的范式类） |
| `src/pages/sample/detail-ext.vue`（改） | 第②段从空状态换成 `EmbedCard` 列表（`v-if="embeds.length"`，否则「暂无包埋记录」）；`embeds` 计算属性把「缺键 / 空数组」都当空；**内部编号那一行照旧 `v-if="has(internalNo)"`**（前端不读系统参数、也不写死永不渲染） |
| `src/api/sample.ts`（改） | 新增 `EmbedRow` / `EmbedMarker` 类型（键集合 = 后端白名单）；`SampleDetail.embeds` 从 `unknown[]` 收紧成 `EmbedRow[]`；把那句挂错位置的注释挪正 |
| `src/types/components.d.ts`（改，**生成产物**） | 自动注册 `EmbedCard`（一行）。与 SYS-MP-001 / AUTH-GROUP-001 的处理一致，**一并提交** |

**Flyway / 取号** 见 §2.1。**`src/pages.json` / `uni-pages.d.ts` 没变**（本票不新增页面）。

**越出 `touches` 的改动（逐条列清）**

| # | 文件 | 为什么必须改 |
|---|---|---|
| 1 | `code/miniapp/src/types/components.d.ts` | `touches` 只列了 `detail-ext.vue` 与 `EmbedCard.vue`。这是 uni-app 自动 import 插件写的**生成产物**，新组件注册必须跟着提交（SYS-MP-001 已把该文件纳入版本控制，AUTH-GROUP-001 有同样一处） |
| 2 | `code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtEmbedShapeContractTest.java` | `touches` 列了 `ext/**` 的 test 目录，属于**范围内**；列在这里只为说明它是本票**新加**的判断（accept 没要求，是本票自己加的声明期防线） |

**没有动**：`doc/api-contract.md`、上游 `ruoyi-system` / `ruoyi-common-*`、根 pom、`ruoyi-lqg/pom.xml`、`EmbedExternalService` / `EmbedQueryService` / `EmbedVerifyService` / 三个纯函数 guard、`doc/verify/**` 只读区。

---

## §3 ticket §4 要求的三项材料

### 3.1 六个外部身份各自的 `/mp/ext/embed/list` id 集合

```
extA            ["9000002001","9000002002","9000002003","9000002006"]
extB            ["9000002001","9000002002","9000002003","9000002006"]
extC            []
extD            ["9000002004"]
extE            []
extF            []
extA onlyMine   ["9000002006"]
extB onlyMine   []
```

（extA = extB 因为是同组双方都 verified；extC 同单位异组 → 只看自己，1005 名下没有块；extD 只有自己 1006 上的 2004；extE 与 A 同组但未核验 → 只看自己，1007 名下没有块；extF 自填待核验 → 空。
`onlyMine` 是**按这条记录自己的 `submitter_id`** 收窄：extA 只剩他自己提交的 2006；extB 的样本 1004 上那块 2003 是实验室建的（`submitter_id=9000000101`）→ 空 —— 这正是「按样本提交人算」会假绿的那一格。）

### 3.2 JSON（原样）

`extA GET /mp/ext/sample/9000001002` → `.data.embeds`（**待核验的送样要看得见**）：

```json
[{"id":9000002006,"sampleId":9000001002,"submitNo":"SJ90000002","paraffinBlockNo":null,"sampleType":"组织","organoidSourceType":null,"tissueReceiveTime":null,"tissueProcessTime":null,"agaroseEmbedTime":null,"dehydrateTime":null,"agaroseSendTime":null,"paraffinEmbedTime":null,"sectionTime":null,"sectioned":false,"stainTypes":[],"stainOther":null,"markers":[],"verifyStatus":"pending","invalidReason":null,"submitterName":"王医生","mine":true,"editable":true,"embedBy":null,"operatorName":null}]
```

`extB GET /mp/ext/sample/9000001001` → `.data.embeds[0]`（**包埋人与操作人都看得到**）：

```json
{"id":9000002001,"sampleId":9000001001,"submitNo":"SJ90000001","paraffinBlockNo":"T-E01-1","sampleType":"组织","organoidSourceType":null,"tissueReceiveTime":"2026-08-23","tissueProcessTime":"2026-08-23","agaroseEmbedTime":"2026-08-24","dehydrateTime":"2026-08-25","agaroseSendTime":"2026-08-25","paraffinEmbedTime":"2026-08-26","sectionTime":"2026-08-27","sectioned":true,"stainTypes":["HE","IHC"],"stainOther":null,"markers":[{"markerName":"Ki67","expression":"strong"},{"markerName":"CK19","expression":"negative"}],"verifyStatus":"valid","invalidReason":null,"submitterName":null,"mine":false,"editable":false,"embedBy":"李工","operatorName":"李工"}
```

键集合差集（对 24 键白名单）原样输出：`[]`。

### 3.3 内部编号开关两态（CR-20260918-07）

```
# 改参数用的 configId（admin GET /system/config/list?configKey=lqg.ext.show-internal-no）
{"configId":5001,"configName":"外部页面显示内部编号","configKey":"lqg.ext.show-internal-no","configValue":"false"}

# 关着时：extB GET /mp/ext/sample/9000001001
{"code":200,"hasInternalNo":false,"keys":["age","createTime","docs","donorName","editable","embeds","gender","hasPathology","hospitalNo","id","invalidReason","mine","organoidType","remark","sampleKind","sourceUnitName","submitNo","submitterName","tissueType","verifyStatus"]}

# admin PUT /system/config {configId:5001, configValue:"true"}
{"code":200}
{"hasInternalNo":true,"internalNo":"T-hli01"}          # extB 看 1001（已核验有效、有编号）
{"hasInternalNo":false,"internalNo":null}              # extA 看 1002（待核验样本本来没编号，不许现编）

# 跑完已改回 false
{"code":200}
{"hasInternalNo":false}
lqg.ext.show-internal-no=false                        # python3 doc/verify/db.py … 直连库复核
```

外部角色够不着这个开关：`--as extA --bizcode GET '/system/config/list?configKey=lqg.ext.show-internal-no'` → `403 没有访问权限，请联系管理员授权`。

### 3.4 文件清单 → 见 §2（迁移文件名与取号依据在 §2.1）

---

## §4 accept 逐条 ✅ / ❌ + 关键输出

### 4.0 关于 `--fresh-module`（本 agent 沙箱限制，与 D1/D2 全部票同源）

`doc/verify/api.sh` 第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` → 原样带 `--fresh-module` **恒 exit 2**。
按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边：

```
jar            : code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar
jar mtime      : 2026-09-22 08:46:31（epoch 1790037991）
lqg src newer  : []                       ← find ruoyi-modules/ruoyi-lqg/src -newer <jar> 为空
admin src newer: []                       ← 同上（ruoyi-admin/src 一个字节没改）
8081 PID       : 79986                    ← lsof -ti tcp:8081 -sTCP:LISTEN
PID holds jar  : 2                        ← lsof -p 79986 | grep -c 'ruoyi-admin/target/ruoyi-admin.jar'
start >= jar   : 1790037995 >= 1790037991 -> True   （psutil.Process(pid).create_time()，不用被禁的 ps）
backend log    : 2026-09-22 08:46:41
嵌套 jar 复核   : BOOT-INF/lib/ruoyi-lqg-5.5.3.jar 内含本票全部新类
                 （ExtEmbedController / ExtEmbedAssemblyService / ExtEmbedVo / ExtEmbedMarkerVo /
                  ExtEmbedSubmitBo / ExtEmbedQueryBo + 改动后的 ExtSampleAssemblyService）
Flyway 启动日志 : "Successfully validated 12 migrations"（重建库那次是 "Successfully applied 12 migrations"）
8080（Kevin）   : 全程为空，未碰
```

accept 里 `mvn … test` 那一行**原样跑在本机会挂**（`~/.m2` 只读），补 `-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome` 三个参数（**连续 8+ 张命中的既有 WARN，本票不重复计数**）。
三条 runner 与探针脚本随报告落盘：`doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc{1,2,3}.sh`、`probes/ext002-probes.sh`；
完整输出 `accept-transcript.txt`（75 行）与 `probes-transcript.txt`。三条链都**包进函数判整条 rc**，并在开头 `rm -f $TMPDIR/lqg-verify-token-*`。

### 4.1 accept 1 · 可见集合 / 白名单键 / 开关两态 / 四条不变量 —— ✅

```
$ bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc1.sh
== accept 1 · 可见集合 / 白名单键 / 内部编号开关两态 / 四条不变量 ==
-- 六身份 /mp/ext/embed/list id 集合 + onlyMine 两档 --
extA            ["9000002001","9000002002","9000002003","9000002006"]
extB            ["9000002001","9000002002","9000002003","9000002006"]
extC            []
extD            ["9000002004"]
extE            []
extF            []
extA onlyMine   ["9000002006"]
extB onlyMine   []
-- 1001 详情的 embeds 键集合差集（对 24 键白名单，应为空）--
[]
-- 开关初值 --
lqg.ext.show-internal-no=false
true        ← 1001 embeds 大断言（编号顺序 / 已切片 / HE+IHC / markers / 两个「李工」/ 2002 的 embedBy 为 null / keys 差集）
true        ← extA 看 1002：[[null,"pending",true,true]]
true        ← extB 看 1002：[["pending",false,false]]
true        ← extC 取 1001 详情 = 404
true        ← extC 取 2001 单条 = 404
false       ← db.py：开关默认 false
true        ← 关着时 has("internalNo")==false
true        ← 外部打 /system/config/list = 401/403
true        ← 开开关 + internalNo=="T-hli01"
true        ← 1002 待核验样本没有编号
true        ← 关回去 + 键又没了
true        ← cmp 逐字节未改 + mvn ExtChokepointContractTest 绿
ACCEPT-1 EXIT=0
```

**counterfeit 逐条排掉**（不是「看着像过了」）：

- `ExtEmbedVo` 若直接复用内部 `EmbedVo` → keys 差集里出现 `internalNo` / `remark` / `verifyBy` / `submitSource` → 红。本票的 VO 是**另写的一个类**，且 `ExtEmbedShapeContractTest` 用反射把字段集合钉成**恰等于** 24 键（accept 的差集是单向的，只能抓多的；反射这条两个方向都抓）。
- 操作人 / 包埋人被剔掉 → 两个「李工」那段红；顺手带出冻存或核验人 → keys 差集红。本 VO 里那两类字段**根本不存在**。
- ext 包注入 `EmbedMapper` 直接查 → I4 红。本票新增的 6 个类里**没有一个 `*Mapper` 字段**（`ExtChokepointContractTest#i4_onlyExtScopeServiceImplTouchesMappers` 扫整个 ext 包，绿）。
- 详情重查样本绕过 `assertVisible`、或单条不查所挂样本可见性 → extC 那两段红。实测都是 `404 样本不存在` / `404 石蜡包埋记录不存在`。
- 详情只列有编号的块 → extA 看 1002 是空数组红。实测 `[[null,"pending",true,true]]`。
- 列表按「本人提交的」而不是可见样本算 → extB 看不到 2001-2003 红；`onlyMine` 按样本提交人算 → extB 带 `onlyMine` 会多出 2003 红。实测两条都对。
- 内部编号写死不给 / 写死给 / 读成启动时常量 / 给待核验样本现编编号 → 开关那五段逐个红。实测五段全绿，且**开关块跑完参数已是 false**（runner 失败分支另加了兜底还原）。
- 契约测试在空集合上空转 → 测试自带 `handlers > 0` / `vos > 0` / `assertFalse(ext.isEmpty())`；本票落地后它真扫到 **5 个 `@RestController`、7 个 `Ext*Vo`、12 个处理方法**（`grep -rl '@RestController'` / `class Ext.*Vo` / 12 个方法级 `@*Mapping`）。

### 4.2 accept 2 · 写保护 / 夹带 / 无效重提 —— ✅

```
$ bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc2.sh
== accept 2 · 写保护 / 夹带 / 无效重提 ==
-- 1) extB 替 extA 的 1001 送样（可见但非本人）--
400	只能挂本人送检过的样本
-- 2) extA 挂到自己已无效的 1003 --
400	这条样本已判无效，不能提交石蜡包埋送样
-- 3) extC 挂到看不见的 1001 --
400	只能挂本人送检过的样本
-- 4) 被拒三次后 external 送样仍只有 seed 那一条 --
1
-- 5) extA 夹带编号 / 状态 / 包埋人 / 染色 / 操作人 / 来源 提交 --
{"code":200,"msg":"操作成功"}
-- 6) 库内：夹带的一个都没生效（external|pending|-|-|-|-|王医生）--
external|pending|-|-|-|-|王医生
-- 7) extB 改 extA 的 2006 / extA 改实验室录入的 2001 --
400	只能修改重提本人提交的送样（同组的可以看，但不能改）
400	只能修改重提本人提交的送样（同组的可以看，但不能改）
-- 8) 两次被拒后库里没有这两条样本类型 --
0
-- 9) 实验室把 2006 判无效 --
{"code":200,"msg":"操作成功"}
-- 10) extA 改后重提 2006 --
{"code":200,"msg":"操作成功"}
-- 11) 库内：回 pending、无效原因清空、两个字段落库 --
pending|-|类器官|胆管类器官
ACCEPT-2 EXIT=0

（链内断言逐段：1 / true / external|pending|-|-|-|-|王医生 / 0 / true / true / pending|-|类器官|胆管类器官）
```

**counterfeit 逐条排掉**：

- 提交只校验「样本可见」不校验「样本是本人送检的」→ extB 替 1001 送样拿到 200、`count` 不是 1 → 红；实测 400 且 count=1。
- 不看样本状态 → extA 挂到自己无效的 1003 → 红；实测 400。
- 外部 BO 复用内部 `EmbedSubmitBo` → 夹带的 `T-hack9` / `valid` / `外部` / `HE` / `internal` 落库 → join 那段红；实测 `external|pending|-|-|-|-|王医生`（**一个都没落**，因为 `ExtEmbedSubmitBo` 里没有这些键，反序列化不到任何地方）。
- PUT 只校验可见不校验本人 / 不看来源 → extB 改掉 extA 的 2006、extA 改掉实验室的 2001 → `count != 0` → 红；实测两次 400 且 count=0。
- 重提不回到 pending / 没清无效原因 → 最后一段红；实测 `pending|-|类器官|胆管类器官`（清空走 `LambdaUpdateWrapper.set(col, null)`，避开 `updateById` 忽略 null 的坑）。
- **被拒的几段后面都跟真实值**：第 4 段直连库数 external 记录数、第 6 段 JOIN `t_lqg_ext_profile` 取提交人姓名、第 8 段直连库数、第 11 段直连库读——「先落盘再返回 400」会在这些格子红。

### 4.3 accept 3 · 小程序外部详情页包埋卡片 —— ✅

```
$ bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc3.sh
== accept 3 · 小程序外部详情页包埋卡片 ==
-- 产物 --
-rw-r--r--  1 wkui  staff  2846 ... code/miniapp/dist/build/mp-weixin/pages/sample/detail-ext.js
-rw-r--r--  1 wkui  staff  2346 ... code/miniapp/dist/build/mp-weixin/components/lqg/EmbedCard.js
-- detail-ext 的 usingComponents（embed-card 必须在内）--
{"embed-card":"../../components/lqg/EmbedCard","note-bar":"../../components/lqg/NoteBar","error-state":"../../components/lqg/ErrorState","loading-state":"../../components/lqg/LoadingState","status-chip":"../../components/lqg/StatusChip"}
ACCEPT-3 EXIT=0
```

八段 grep 逐条（`EmbedCard` 名字 / `@/components/lqg/EmbedCard.vue` 直接路径 / 卡片里有 `verifyStatus`、`embedBy`、`operatorName` / 卡片里**没有** `internalNo|verifyBy|frozenBy|cryo` / 详情页 `v-if="…internalNo…"` / 详情页**没有** `verifyBy|frozenBy|cryo`）全过。

**counterfeit 逐条排掉**：

- 走桶口导入 → 第 2 段要求 `.vue` 直接路径；实测 `import EmbedCard from '@/components/lqg/EmbedCard.vue'`。
- 卡片不区分待核验 → `verifyStatus` 那段红。实测编译产物 `EmbedCard.wxml` 里三个分支都在：`已切片`（`wx:if="{{c}}"`）、`status-chip`（`wx:if="{{d}}"`）、`无效 · {{g}}`（`wx:if="{{f}}"`）。
- 包埋人 / 操作人被藏 → `embedBy` / `operatorName` 两段红。
- 卡片顺手带出冻存或核验人 → `! grep` 红。**EmbedCard.vue 里连注释都没有这几个词**（刻意的，见文件头那段）。
- 详情页把内部编号写死渲染 → `v-if` 那段红；实际是 `v-if="has(internalNo)"`，且 `embeds` 与它无关（`ExtEmbedVo` 根本没有这个字段）。
- 编译产物复核：`dist/build/mp-weixin/pages/sample/detail-ext.wxml` 第②段是 `<block wx:if="{{n}}"><embed-card wx:for="{{o}}" …/></block><view wx:else …>暂无包埋记录</view>` —— 循环与空状态都在，且 `detail-ext.js` 存在（没踩 SAMPLE-MP-001 WARN-1 那个「只 emit .json/.wxml」的坑）。

### 4.4 追加证据（accept 之外的机器证据）

**(a) Java 全模块测试：`Tests run: 93, Failures: 0, Errors: 0, Skipped: 0`（本票前 86）**

```
ExtEmbedShapeContractTest          Tests run: 7   ← 本票新增
ExtChokepointContractTest          Tests run: 4   ← ADR-0004 四条不变量
EmbedQueryContractTest             Tests run: 4
EmbedRulesContractTest             Tests run: 8
EmbedChildrenCheckerContractTest   Tests run: 4
EmbedShapeContractTest             Tests run: 6
ExtBindStateMachineContractTest    Tests run: 11
StaffGrantRulesContractTest        Tests run: 6
MockLoginGuardContractTest         Tests run: 4
VerifyTransitionsContractTest      Tests run: 8
MpSampleContractTest               Tests run: 10
SampleRecentFilterContractTest     Tests run: 6
SampleKindRulesContractTest        Tests run: 6
SampleTableQueryContractTest       Tests run: 9
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**(b) 对抗性探针 26 条：`PROBE EXT002 PASS=26 FAIL=0`**（完整输出 `probes-transcript.txt`）

```
PASS P1-staff打外部列表-403          PASS P2-admin打外部单条-403
PASS P3-staff打外部提交-403          PASS P4-外部打内部lqg-403
PASS P5-外部打mpint-未注册404        PASS P6-verifyStatus=pending只出待核验 — ["9000002006"]
PASS P7a-extA-onlyMine+pending      PASS P7b-extB-onlyMine+pending-空
PASS P8a-extA看自己待核验送样 [pending,true,true]
PASS P8b-extB看同组送样-不可改 [pending,false,false]
PASS P9a-核验有效后 editable=false [["valid",true,false]]
PASS P9b-已核验送样 PUT 被拒(400)且库内不变
PASS P10a-extC改不可见记录被拒且库内不变 — 400 / 0
PASS P11a-实验室录入的块外部只读 — 400 / 组织
PASS P12a-换挂别人样本被拒且 sample_id 不变 — 400 / 9000001002
PASS P13-软删记录不在外部接口出现（404）
PASS P13b-软删 2005 不在列表
PASS P14-单条 submitNo=所挂样本送检单号 — SJ90000002
PASS P15a-开关开-待核验样本仍无编号、包埋VO也无该键 — [false,null]
PASS P15b-开关开-外部包埋列表也不给内部编号 — null
PASS P15c-开关已还原 false
PASS P16-没记录的样本 embeds=[]      PASS P17-不可见样本详情 404
PASS P18a-不存在的样本被拒且未建记录 — 404 / 1
PASS P19-夹带包埋人/操作人/工序时间/备注全不落库 — -|-|-|-|-
PASS P19b-夹带的 marker 也没落库 — 0
PROBE EXT002 PASS=26 FAIL=0
```

★ **只有 2 条探针在初版假红，两条都是脚本自己的问题，不是实现缺陷**：
`P9b` 的期望值我写成了 `类器官`（那是**前一轮 accept 2 诊断**留下的值），seed 里 2006 的 `sample_type` 是 `组织` → 期望值写错；
`P15a` 用了 `[.data|has(…), ([.data.embeds[]…])]` 这种写法，**jq 的 `,` 比 `|` 结合得紧**，第二段被喂给了 `.data` 而不是整个响应 → `Cannot iterate over null`。
两条都已定位修好并复跑全绿（见 §8 坑 3）。

---

## §5 回归自查（本票在咽喉上，最容易打红别人）

| 回归 | 命令 | 结果 |
|---|---|---|
| **EMBED-MODEL-001 accept 1**（DDL + 迁移行数） | `bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc1.sh` | **`ACCEPT-1 EXIT=0`**（`✓ 2 张表与 SSOT 逐列相符` / `1`） |
| **EMBED-MODEL-001 accept 2**（写侧状态机 + checker） | `…/embed001-acc2.sh` | **`ACCEPT-2 EXIT=0`**（`0 / True / 2 / valid`） |
| **SAMPLE-MP-001 accept 1**（fixture + vitest 22 + 8 段 grep） | `…/SAMPLE-MP-001/accept-runners/mp001-acc1-state.sh` | **`ACCEPT-1 GREEN (exit 0)`**；`{"numTotalTests":22,"numPassedTests":22,"numFailedTests":0}` |
| **SAMPLE-MP-001 accept 2 · 构建段** | `…/mp001-acc2-build.sh` | **`ACCEPT-2-BUILD GREEN (exit 0)`**（`pages/sample/detail-ext.js` 仍在） |
| **SAMPLE-MP-001 accept 2 · API 链 15 段** | `…/mp001-acc2-api.sh` | **`ACCEPT-2-API GREEN (exit 0)`** |
| **D1 回归包** | `bash doc/waves/regression/D1/verify.sh --skip-build` | **41 绿 / 1 红 / 0 环境错** —— 唯一那条红是**既有 harness 缺陷**：`SELECT count(*) FROM flyway_schema_history --eq 7` 写死总数，现在实际 12（见 WARN-1 关联的既有 issue #82） |
| **本票的 L0 咽喉门** | `ExtChokepointContractTest` | `Tests run: 4, Failures: 0`（accept 1 链内实跑） |
| **契约 fixture 逐字节** | `cmp doc/verify/fixtures/java/ExtChokepointContractTest.java …/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java` | 无输出；两侧 sha256 都是 `6b1da29a7c12744798910c115be8981745253335a8db4c8b68a992a88567e812` |
| **跨票依赖面** | `Get /mp/ext/sample/{id}` 的 `embeds` 由本票接数据；`ExtEmbedVo` 不再为空壳 | AUTH-EXT-003（外部文档）与 EMBED-MP-001 不读 `/mp/ext/embed`；`/lqg/embed/**` 与 `/mp/int/embed/**` 本票一个字节没动 |

```
$ D1_VERIFY_RC=1  green=41 red=1 env=0
$   ✗ L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001） → [FAIL] 期望 '7'，实际 '12' 12
```

★ 这条**不是「以前好的坏了」**：D1 的 7 支迁移逐个点名全绿、无失败行；红的是写死总数那条断言（issue #82 已记，本票**不重复计数**）。
★ 另一条**本票引入、但设计如此**的变化：`flyway_schema_history` 从 11 支变 **12 支**（新增 `V202609230902`）；任何断「迁移总数」的既有断言都会跟着变一位。

---

## §6 遗留与 raise（ticket §4.6）

### 6.1 越出 `touches` 的改动

只有 §2.3 那两处（`components.d.ts` 生成产物、本票自加的 `ExtEmbedShapeContractTest`），都已在 §2.3 说明。
**`doc/api-contract.md` 一个字节没动**（ticket §2 那句「契约由调度侧同步」在本张不需要：契约第 63-64 行**已经**是 CR 之后的写法）。

### 6.2 与 `doc/api-contract.md` 的差异（逐行核过）

| 契约行 | 契约写的 | 实现 | 判断 |
|---|---|---|---|
| 第 63 行 `/mp/ext/embed/**` 四端点 + `onlyMine?` / `verifyStatus?` + PUT 规则 | 逐字 | 逐字实现 | ✅ |
| 第 64 行 `ExtEmbedVo` 的 24 键（含 `embedBy` / `operatorName`、注明「仍然没有 remark / verifyBy / internalNo」） | 逐字 | 逐字实现（本票的键差集断言就是照它写的） | ✅ |
| 第 51 行 `ExtSampleDetailVo{id, submitNo, sourceUnitName, donorName, gender, age, hospitalNo, tissueType, hasPathology, remark, verifyStatus, invalidReason, submitterName, mine, editable, createTime, embeds, docs}` | 这份清单**少一个 `sampleKind`** | 盘上的 VO 从 AUTH-EXT-001 起就有 `sampleKind`（accept 1 是单向差集，所以没红） | ⚠️ **doc-drift**，见 WARN-4。本票不再改契约（只读区），只报 |
| ticket §4.6 那句「契约第 51 / **64** 行还是 CR-20260918-07 之前的写法」 | —— | **第 64 行已经是新写法**（`embedBy` / `operatorName` 都在，还带 CR 注），只有第 51 行的 `ExtSampleDetailVo` 清单缺 `sampleKind` | ⚠️ ticket 正文这句话**过时**，见 WARN-4 |

### 6.3 口径上没把握 / 需要确认的四点

1. **`ExtEmbedVo.submitterName` 对实验室录入的送样是 `null`**（seed 的 2001/2002/2003/2004 都是）。原因：本票按 AUTH-EXT-001 的口径用 `ExtScopeService.submitterNames`，它只查 `t_lqg_ext_profile`（外部档案）——实验室人员没有外部档案。语义上说得通（「外部提交人姓名」），且卡片上已经能看到操作人 / 包埋人（李工），信息没丢。若要「谁提交的」也带上实验室人员，得换 `SampleNameResolver`（`sys_user.nick_name`）→ 属口径变更，见 WARN-2。
2. **`/mp/ext/embed/{id}` 的 PUT 是「覆盖两个字段」而不是 patch**：`EmbedExternalService.resubmit` 无条件 `.set(sampleType, 传值)` / `.set(organoidSourceType, 传值)` —— 请求里不传 `sampleType` 会把它清成 null。accept 2 的 PUT 两个字段都传，所以看不出来。这是 EMBED-MODEL-001 已验收的 service 语义，本票**没有改它**（改了会动上游票的结论）。下游 EMBED-MP-001 的填写页要**两个字段都传**，见 WARN-3。
3. **`ExtSampleDetailVo.embeds` 的排序**：ticket §2 写「有编号的按编号排序在前，没编号的按提交时间排在后」。本票直接复用 EMBED-MODEL-001 的读口 `EmbedQueryService.listBySampleId`，它排的是 `(paraffin_block_no IS NULL) ASC, create_time ASC, id ASC` —— **「有编号的在前、没编号的在后」逐字一致**，但「有编号的那一档内部」是按提交时间 / id，**不是按编号字符串**。accept 1 断的 1001（两块都有编号、同一 `create_time`）两档口径给出同一个答案 `["T-E01-1","T-E01-2"]`，所以无差异。若甲方要按编号字典序，改 `EmbedQueryService` 一处 `last(...)` 即可（属 EMBED 域，不在本票 `touches`）。
4. **`mine` / `editable` 的判据是「这条包埋记录自己的提交人」**（不是所挂样本的提交人）。依据是 accept 1 的 `onlyMine` 那一格（extB 的样本上由实验室建的 2003 必须被 `onlyMine` 排除）+ ticket §4.2 的 counterfeit 点名。副作用：实验室在 extA 的样本上建的块，extA 看它 `mine=false / editable=false`（正确 —— 他不能改实验室的块）。

### 6.4 明确没做（ticket §3 边界逐条核对）

不做工作台页面（EMBED-WEB-001）｜不做小程序其它包埋页与外部送样填写页 / 历史页签（EMBED-MP-001）｜不做切片染色提示（SAMPLE-HINT-001）｜不做导出（SAMPLE-EXPORT-001）｜**外部仍然看不到冻存**（CR-20260918-07 只放宽了操作人与包埋人；`ExtEmbedVo` 里一个冻存字段都没有）｜**外部列表行不显示内部编号**（开关只作用于样本详情，探针 P15b）｜不做「系统管理 → 参数设置」的菜单与授权（SYS-WEB-001 的活，本票只读这个参数）｜外部列表不加包埋提示｜文档在 AUTH-EXT-003。

### 6.5 ADR-0004 的 `implementation_status`

结构化口径里还是 `"implementation_status": "not-yet-implemented"`。石蜡包埋这一半现在已经实现，但 ADR 是只读区（`doc/authority/**`），**本票没改**。请主会话在 AUTH-EXT-003 收口后统一改成 `implemented`。

---

## §7 WARN 清单（交主会话落 `issue add`）

| # | severity | type | 标题 | 影响范围 / 方案 |
|---|---|---|---|---|
| WARN-1 | **S2** | harness | **D3 的 AUTH 域迁移号回填必撞 `out-of-order=false`**：本票的 `V202609230902` **小于**同任务已应用的 `V202609231100`（EMBED-MODEL-001）。本票按派单重建 dev 库让 12 支全量重跑才起得来 | 这是 issue #12 的**第二次命中**，但本票带出一个新信息：**同一任务的域段交叉**（D3 的 AUTH=09xx / EMBED=11xx，日期段相同）在 `out-of-order=false` 下**必然**冲突 —— 只要 EMBED 先落（它确实是 D3 链头），后续任何 AUTH/CRYO/QC 票都会再撞一次（**AUTH-EXT-003 是同票型的下一张**）。方案（三选一，都属需求层）：① D3 起把「日期段 = 任务日」改成「日期段 = 任务日 + 域内序号」让各域段天然递增；② 或把同任务内所有域的 HHmm 统一排成 `11xx` 之后；③ 或给 `.tmp/run-backend.sh` 加一个「必要时重建 dev 库」的开关（**别改 `out-of-order`**）。本票**没改** `application.yml` / `lint-profile.yaml`。 |
| WARN-2 | S3 | clarify | **`ExtEmbedVo.submitterName` 对实验室录入的送样恒为 `null`** | 见 §6.3 第 1 点。影响：外部看到的实验室录入块，「提交人」一格是空的（但「操作人 / 包埋人」有值）。方案：确认语义 —— 若是「外部提交人」则现状正确、在契约第 64 行注明；若是「谁录的」则换 `SampleNameResolver`（本票没改，因为它会把 `sys_user.nick_name` 与外部档案混在一个键里）。 |
| WARN-3 | S3 | clarify | **`PUT /mp/ext/embed/{id}` 不是 patch**：不传 `sampleType` / `organoidSourceType` 会清成 null | 见 §6.3 第 2 点。影响 EMBED-MP-001 的「改后重提」填写页（必须两个字段都带）。方案：在契约第 63 行补一句 PUT 的字段语义（patch 还是覆盖），或给 `EmbedExternalService.resubmit` 加「传 null 即不改」的语义。 |
| WARN-4 | S3 | doc-drift | **契约第 51 行的 `ExtSampleDetailVo` 字段清单缺 `sampleKind`**（AUTH-EXT-001 起盘上就有这个键）；另：**ticket §4.6 说「契约第 51 / 64 行还是 CR 之前的写法」不准确** —— 第 64 行已经是 CR-20260918-07 之后的写法（含 `embedBy` / `operatorName`） | 影响：照第 51 行做严格 keys 差集的下游票会误判。方案：把 `sampleKind` 补进第 51 行的清单；把 ticket 生成器里那句过时的 diff 说明删掉（真实差异只剩 `sampleKind` 一处）。 |
| WARN-5 | S3 | blocked-upstream | **外部角色打 `/mp/int/embed/list` 目前是 `404 No endpoint`**（EMBED-MP-001 还没落这个端点），探针 P5 只能断「403 **或** 未注册的 404」 | 与 AUTH-EXT-001 WARN-3 / SAMPLE-MP-001 的收口形态**同源**（那次是 `/mp/int/sample/list`，SAMPLE-MP-001 落完后变绿）。**本票不重复计数**，只记：EMBED-MP-001 落 `/mp/int/embed/**` 之后，这一条自动变 403。方案：给「外部打 `/mp/int/**`」类断言加 `@requires` 前置标注，或允许 `^403|404 No endpoint`。 |
| WARN-6 | S3 | harness | **jq 的 `,` 比 `|` 结合得紧**，`[.data\|has("x"), ([.data.list[]…])]` 会把第二段喂给 `.data` → `Cannot iterate over null`，**看起来像实现坏了** | 本票探针 P15a 第一版就假红在这里（见 §4.4 note）。影响所有写 accept 探针的票。方案：写进 ticket 生成器的「探针写法」提示：数组里每段各自加括号；或直接断 `${V}` 字符串不要嵌套管道。 |
| WARN-7 | S2 | harness（既有，本票不重复计数） | **D1 回归包把 flyway 迁移总数写死成 7** → 本票推到 **12**，那条断言仍假红 | 取证见 §5（D1 的 7 支逐个点名全绿、无失败行）。issue #82 已记（D2 r1 首次命中）。方案同上游：断「D1 那 7 支版本号逐个存在且无 `success=false`」，别断总数。**本票没改回归包**（不在 `touches`）。 |

> 已在上游报告记过、本票**不重复计数**的既有 WARN：`api.sh --fresh-module` 在沙箱恒 exit 2 + Maven 三参数（连续 8+ 张命中）、`api.sh` token 缓存只看 mtime、`db.py` 是只读执行器却 exit 0、`updateById` 忽略 null、MyBatis-Plus 无「按表达式排序」重载、`PageQuery` 只有两参构造、`@TableLogic` 只保证 `del_flag`、微信开发者工具在本沙箱跑不通、`sys_config` 改库后要 `refreshCache`（**本票走 admin `PUT /system/config` 那条带 `@CachePut` 的路，没有踩这个坑**）。

---

## §8 坑与解法（3-5 行，给下游）

1. ★★ **`EmbedQueryService` 已经是 embed 域对外的读口，别在 ext 包再写一份查询**：EMBED-MODEL-001 把 `detail / listBySampleId / listBySampleIds / entity / pageByWrapper` 五个方法留给本票，本票**一个 SQL 都没写**（ext 包里没有一个 `*Mapper` 字段，i4 绿），既满足 I4，又天然继承了它的软删过滤（`@TableLogic`）、`(paraffin_block_no IS NULL) ASC` 排序与 marker 批量装配。**下游 EXT 票先翻一遍业务域的 `*QueryService` 有没有留读口，比自己在 ext 包拼 wrapper 稳。**
2. ★ **`onlyMine` / `mine` / `editable` 的判据是「这条记录自己的提交人」，不是所挂样本的提交人**：accept 1 用 extB 的样本 1004 上那块**实验室建的** 2003 把这两种写法区分开（按样本算会多出 2003）。同一处判据要同时喂给列表筛选、列表行、单条详情、样本详情四张脸，否则会出现「列表筛掉了、行上却标我可改」。
3. ★ **jq 的 `,` 比 `|` 结合得紧**：`jq '[.data|has("x"), ([.data.list[]…])]'` 里第二段其实是在 `.data` 上求值 → `.data.list` 变 null → `Cannot iterate over null (null)`，**看上去像后端返回了空**。写探针时数组里每段各自加括号（`[(.data|has("x")), …]`），或者干脆分两次 jq。本票探针 P15a 因此假红过一轮。
4. ★ **`--fresh-module` 之外，跨域回填迁移号才是真正会「启动即失败」的那一个**：`out-of-order=false` + 同任务多域段（D3 的 AUTH=09xx / EMBED=11xx）必然冲突。别去改配置（prod 语义要保持），按 issue #12 的先例重建 dev 库让全量按序重跑，再 `reseed.sh --yes`；重建后**所有依赖「迁移总数」的既有断言都会跟着变一位**（D1 回归包那条假红就是）。
5. ★ **小程序组件里那三个词是禁字，连注释都别写**：accept 3 的 `! grep -nE 'internalNo|verifyBy|frozenBy|cryo'` 打的是**整个文件**（含注释与字符串），而 `detail-ext.vue` 上还叠着 SAMPLE-MP-001 的 `! grep -nE 'receiveDate|processTime|hasQcSheet|hasViabilityReport|verifyByName|[Cc]ryo'`。新增卡片文件时把禁字规矩写在文件头的注释里（本票的 `EmbedCard.vue` 就是这么做的），比每次跑 accept 才发现划算。

---

## §9 验证用的长进程 / 收尾

- **后端 java（8081）**：收尾时按 PID 关掉（`lsof -ti tcp:8081 -sTCP:LISTEN` → `kill 79986`；**没用过 `pkill -f`**）。收尾复核 `8081: []`。
- **8080（Kevin）/ 5432 / 6379**：全程**没碰**（开工与收尾两次复核都是空）。
- **8082 / 8083 / 9200 / 9201**：本票没起过任何前端 / H5 dev server（accept 3 只用 `pnpm build:mp-weixin`），收尾复核全空。
- **5433 `lqg-dev-postgres` / 6380 `lqg-dev-redis` / 9002-9003 `lqg-dev-minio`**：全程在跑，**没停**（留给后续 ticket）。
- **DB 收尾**：
  ```
  $ bash doc/verify/reseed.sh --yes            → reseed 完成
  $ bash doc/waves/tools/clean-orphan-accounts.sh --yes
      复核：非 seed 段的运行时账号 wx_* / lqg_*（应为空） → (0 rows)
  $ python3 doc/verify/db.py --sql "SELECT config_key||'='||config_value FROM sys_config WHERE config_key LIKE 'lqg.%'"
      lqg.cryo.overdue-days=14
      lqg.ext.show-internal-no=false      ← 探针 / accept 打开过，已还原成默认 false
  ```
- **报告附带的复跑脚本**：`doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc{1,2,3}.sh`、`probes/ext002-probes.sh`、`accept-transcript.txt`、`probes-transcript.txt`。
