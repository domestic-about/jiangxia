# OCR-IMPL-001 · 完工报告

- **ticket**：OCR-IMPL-001（track OCR / phase D5 / size M）—— 识别后端：统一接口、可插拔的识别适配层、字段解析规则、限流、付费通道默认关
- **status**：**done**
- **accept**：**2/2 绿**（票面 `run` 逐字重放；唯一归一化 = 工具自带的 NF1 去掉 `--fresh-module ruoyi-lqg`、NF2 给 mvn 补本机三参数，逐条见 §4）
- **分支**：`task/D5`（未切分支 / 未 push / 未 merge / **未动** `doc/waves/state.json`、`_manifest.json`）
- **验收对象**：jar `ruoyi-admin.jar`（重打于本票源码），后端进程 **PID 92929 → 后续重启**（8094，dev profile + `--api-decrypt.enabled=false` + `lqg.ocr.provider=stub`），`GET /lqg/sys/ping` → 200
- **单测**：`OcrFieldParserFixtureTest` **7/7** + `OcrProviderSelectionTest` **9/9**；全模块 **197 run / 0 failures / 0 errors / 0 skipped**（改前 181 → 净增 16，无回归）
- **迁移**：**0 支**（本票不建表、不建菜单、不加权限字符串 —— 见 §2.4）
- **只读区未动**：`_input/`、`doc/requirements.yaml`、`doc/authority/*.yaml`、`doc/change-log.md`、`doc/api-contract.md`、`doc/verify/seed/**`、`doc/verify/gen_seed.py`、`doc/verify/api.sh`、`doc/verify/fixtures/**`、`doc/waves/state.json`、`_manifest.json`
- **端口纪律**：只用 8094（后端）/ 6380（Redis）/ 5433（PG）；**没碰** 8080 / 5432 / 6379；进程一律 `lsof -ti tcp:<port> -sTCP:LISTEN` 拿 PID 后 `kill`（**从没用过 `pkill -f 'ruoyi-admin.jar'`**）

---

## §0 状态自检（3 级）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 分支符合调度器注入的期望 | ✅ PASS | `git branch --show-current` → `task/D5`；全程未切 / 未 push / 未 merge |
| `depends_on` 全部 done 且产物在盘 | ✅ PASS | `AUTH-LOGIN-001`、`AUTH-GROUP-001` 的产物（`/mp/me`、`t_lqg_source_unit` + `SourceUnitMapper`）都在盘且被本票复用（units 白名单查的就是它） |
| 扫 `doc/change-log.md` 涉及本票的 CR | ✅ PASS | 顶部四条 CR 逐条核过：**CR-20260921-08**（视觉方向 A）与 **CR-20260918-07**（表格页/历史记录/字段可见/开关）都**不动本票**的接口形状与解析口径；本票实现未与任何 CR 冲突 |
| 3 个 `blueprint_refs` 逐条 `authority_lint.py show` | ✅ PASS | `FLOW:F-OCR-01.step2`（统一接口收图 → 限流每人每分钟 ≤6 → provider → 原始文本行；图只在内存、不落库不落 OSS）· `FLOW:F-OCR-01.step3`（规则解析出候选；**解析不出的字段不返回**）· `FIELD:t_lqg_source_unit.unit_name`（length 100、`uk_unit_name`）全部 `active`，与实现逐条对齐 |
| 口径复述 4 条 | ✅ PASS | §1 逐条 + 机器证据 |
| 环境可用 | ✅ PASS | PG 5433 / Redis 6380 在跑；`qa-up.sh` 一键起 8094；动手前全模块 181 单测全绿 |
| **与蓝图冲突？** | **无冲突** | prompt §2 与蓝图 step3 有两处**措辞差**（单位名"完全相等" vs 蓝图"单位名称表匹配"、年龄整数 vs 蓝图 step3 未细化），实现按 prompt 的**更严**口径做（不做模糊匹配），已在 §6 WARN-1 记录 |

**STOP 判定：无。**

---

## §1 口径复述（逐条对 accept 核）

| # | 口径（ticket §0/§2 + 权威锚） | 落点 | 机器证据 |
|---|---|---|---|
| 1 | **本票不依赖 OCR-SPIKE-001**：适配层 + 规则现在就能做；provider 可插拔 | `OcrProvider` 接口 + `NoneOcrProvider`（缺省）/ `StubOcrProvider`（dev/test）/ `PaidOcrProvider`（标记接口，真实实现等探路结论） | accept 2 第 3 段单测；`provider=none` 现场取证（`none-provider-live.txt`） |
| 2 | **识别不写任何业务表、不存图** | `MpOcrController` / `OcrRecognizeService` 不碰 OSS、不写 mapper 的写方法；图只在 `byte[]` 里 | accept 1 首尾 `t_lqg_sample + sys_oss` 计数相等（5 例 × 识别前后） |
| 3 | **噪声里的数字不得被猜成年龄 / 住院号** | `OcrFieldParser`：没有词表标签一律不认；年龄要 1-120 且后面不跟数字；住院号要 5-20 位字母数字且遇中文停 | fixture 第 03 例（`2-8℃`、日期、床号）+ 第 04 例（`床号:12` 不被当住院号）；`OcrFieldParserFixtureTest` 逐例 |
| 4 | **单位名只在与 active 单位完全相等时才返回** | `OcrRecognizeService.activeUnitNames()`（`unit_status='active'`）→ `OcrFieldParser` 整行 strip 后 `Set.contains` 全等 | fixture 第 01 例认 `A 医院`、第 05 例 `某某市第九医院` 不认；单测 `unitNameIsExactMatchOnly`；**实测第 01 例 live 返回 `sourceUnitName: "A 医院"`** |
| 5 | **付费通道默认关，prod 配置里不出现这个键** | `OcrProperties` 两个 `@Value` 都带缺省（`none` / `false`）；`application-prod.yml` 里 **没有任何 `lqg.ocr.*`** | accept 2 第 1 段（`paidEnabled==false`）+ 第 2 段（prod grep 无命中）；`OcrProviderSelectionTest` 4 例 |
| 6 | **没配 provider 时明确报「请手动填写」而不是 500** | `NoneOcrProvider.recognize` 抛 `ServiceException("识别暂不可用，请手动填写")` | 单测断言 message 含「手动填写」且不含「系统异常」；**8096 现场取证**：`provider=none` 时 `{"code":500,"msg":"识别暂不可用，请手动填写"}`（不是 NPE 系统异常） |
| 7 | **限流每用户每分钟 6 次** | `@RateLimiter(time=60, count=6)` + **key 带 userId**（`#{@ocrRateLimitKey.userKey()}`） | accept 2 第 5 段：extB 连打 8 次 → 6×200 + 2×非 200；`rate-limit-live.txt`（含每用户独立桶的对照） |

---

## §2 改了哪些文件

### 2.1 新增 · 后端（全部在 `touches` 的 `.../org/dromara/lqg/ocr/**` 内）

| 文件 | 职责 |
|---|---|
| `ocr/provider/OcrProvider.java` | 适配层接口：`List<String> recognize(byte[] image)`（只出原始文本行） |
| `ocr/provider/NoneOcrProvider.java` | 缺省实现（bean 名 `none`）：抛「识别暂不可用，请手动填写」 |
| `ocr/provider/StubOcrProvider.java` | 测试桩（bean 名 `stub`，`@Profile("dev","test")`）：按请求头 `X-Ocr-Stub-Case` 前两位从 classpath `ocr-cases.json` 取 `rawLines`；夹具缺失只打 ERROR 不炸启动 |
| `ocr/provider/PaidOcrProvider.java` | **标记接口**（无方法）：按次收费的实现都实现它 → 付费开关自动对将来每一个真实 provider 生效 |
| `ocr/domain/OcrFieldParser.java` | ★★ **纯函数**解析器（标签词表 + 归一 + 值合法性；不查库、不抛业务异常） |
| `ocr/domain/vo/OcrRecognizeVo.java` | `{rawLines, fields}`（fields 里解析不出的键**不出现**） |
| `ocr/domain/vo/OcrStatusVo.java` | `{provider, paidEnabled}` |
| `ocr/config/OcrProperties.java` | `lqg.ocr.{provider, paid-enabled, max-bytes}` 运行期读取（缺省 `none` / `false` / 5MB） |
| `ocr/config/OcrProviderFactory.java` | 每次请求现选 provider；`providerName()` / `availableNames()` / `paidNames()` / `status()` |
| `ocr/guard/OcrProviderGuard.java` | ★ 选择口径纯函数：付费开关 / 未知 provider / stub-in-prod 三条判定 + 启动错误文案 |
| `ocr/guard/OcrProviderGuardAutoConfiguration.java` | `ApplicationRunner`：用运行期 profile 调护栏，不合法**拒绝启动** |
| `ocr/guard/OcrRateLimitKey.java` | 限流 key 的「用户维度」bean（`#{@ocrRateLimitKey.userKey()}`） |
| `ocr/service/OcrRecognizeService.java` | 编排：provider → 解析；active 单位名白名单查询（`DataPermissionHelper.ignore`） |
| `ocr/controller/MpOcrController.java` | `POST /mp/ocr/recognize`（multipart `file`，≤5MB，jpg/png，`@RateLimiter`） |
| `ocr/controller/OcrStatusController.java` | `GET /lqg/ocr/status`（`@SaCheckPermission("lqg:sample:list")`） |

### 2.2 新增 · 测试（在 `touches` 内）

| 文件 | 例数 | 覆盖 |
|---|---|---|
| `test/.../ocr/OcrFieldParserFixtureTest.java` | 7 | 夹具 5 例逐例 expect/absent；03 号噪声单独钉；单位名全等；裸数字不认；一行多字段 |
| `test/.../ocr/OcrProviderSelectionTest.java` | 9 | 缺省→none+手填文案；付费开关关/开；付费开关配非付费实现报错；stub-in-prod/无 profile/大小写 prod 拒启；未知 provider；桩夹具五例；协议名稳定 |

### 2.3 修改（**都在 `touches` 内**）

| 文件 | 改动 |
|---|---|
| `ruoyi-admin/src/main/resources/application-dev.yml` | +11 行：`lqg.ocr.provider: stub` + `paid-enabled: false`（带注释说明 prod 刻意不出现） |
| `ruoyi-modules/ruoyi-lqg/pom.xml` | +32 行：① 显式声明 `ruoyi-common-ratelimiter`（此前只有 admin 依赖它，lqg 类路径上没有 `@RateLimiter`，写了编不过）；② `<resources>` 把 `doc/verify/fixtures/ocr-cases.json` 拷进 **main** 资源（放 test 资源会「单测绿、接口空」，实测踩过） |

### 2.4 没动的（口径说明）

- **不加 Flyway 迁移**：`doc/lint-profile.yaml` 写死「**OCR 无菜单（只有小程序入口）**」+「权限串 `lqg:<域>:...`」，而 api-contract.md 的硬口径是「`@SaCheckPermission` 与菜单 `perms` 逐字一致」→ 本票不建菜单、不造 `lqg:ocr:*` 串；`/lqg/ocr/status` 复用 `lqg:sample:list`（识别按钮就长在样本录入表单上，能看样本列表的人本来就该能读它）。
- **不建识别记录表 / 不做历史**（ticket §3）。
- **不接任何真实识别服务**（等 OCR-SPIKE-001）。
- **不做小程序端**（OCR-MP-001）；**不接海外服务**。

---

## §3 单测 + 全模块回归

```
$ cd code/RuoYi-Vue-Plus
$ mvn -o -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='OcrFieldParserFixtureTest,OcrProviderSelectionTest' \
    -Dsurefire.failIfNoSpecifiedTests=true -s ../../.mvn-settings.xml \
    -Dmaven.repo.local=../../.m2repo -Duser.home=../../.buildhome
15:14:25 INFO StubOcrProvider - 识别测试桩已就绪：5 组用例（ocr-cases.json）
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- OcrFieldParserFixtureTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- OcrProviderSelectionTest
```
```
$ mvn -o -q -pl ruoyi-modules/ruoyi-lqg -am test   # 全模块
TOTAL run=197 failures=0 errors=0 skipped=0
```

---

## §4 accept 逐条结果（票面 `run` 逐字重放）

```
$ export LQG_VERIFY_ENV_FILE="$PWD/.tmp/qa-env/8094/verify.env"
$ python3 doc/waves/tools/accept-run.py --ticket OCR-IMPL-001 --run \
    --json .tmp/ocr-impl-accept.json --logdir .tmp/gate/OCR-IMPL-001/accept-logs
[run] 2 条 accept，单条超时 900s
  ✓ OCR-IMPL-001 acc1 [API] 五个解析用例逐例过：… (1.0s)
  ✓ OCR-IMPL-001 acc2 [STATE] 付费通道默认关且 prod 配置里不出现这个开关；… 限流生效 (3.4s)
[run] 通过 2/2
```

- **归一化**：`acc1: NF1`（去 `--fresh-module`）；`acc2: NF1,NF2`（再补 mvn 三参数）。两条都是 runner 自带的工具归一化，与实现无关。
- 取证（全部落 `doc/waves/reports/OCR-IMPL-001/`）：
  - `accept-logs/OCR-IMPL-001-acc1.sh.log`：`true`×5（五例逐例过）
  - `accept-logs/OCR-IMPL-001-acc2.sh.log`：`true`（第一段状态）+ Maven 段 `StubOcrProvider 已就绪`（后两段由 shell 退出码与 awk 判定，无 stdout）
  - `accept-run-final.json`：`passed 2 / total 2`

### 4.1 acc1 五例逐例（现场输出）

| 例 | expect | absent | 实测 |
|---|---|---|---|
| 01-印刷标签 | donorName/gender/age/hospitalNo/tissueType/sourceUnitName | — | 全中（含 `sourceUnitName="A 医院"`） |
| 02-系统截图（空格 + 全角冒号） | donorName/gender/age/hospitalNo | tissueType/sourceUnitName | 全中、absent 全不出现 |
| 03-只有噪声 | — | 六个键全部不出现 | `fields={}` |
| 04-只有部分字段 | hospitalNo/gender | donorName/age/tissueType | `hospitalNo="ZY0000002"`（`床号:12` 没被吃进来）、`gender="female"` |
| 05-单位名不在表里 | donorName/age | sourceUnitName/gender/hospitalNo | 全中、absent 全不出现 |

现场 rawLines 与 fields（第 01 例，`bash doc/verify/api.sh --as extA --header "X-Ocr-Stub-Case: 01" … POST /mp/ocr/recognize`）：
```json
{"code":200,"msg":"操作成功","data":{
  "rawLines":["A 医院","姓名：测试供体甲","性别：男  年龄：56岁","住院号：ZY0000001","标本：肝组织"],
  "fields":{"donorName":"测试供体甲","gender":"male","age":"56","hospitalNo":"ZY0000001",
            "tissueType":"肝组织","sourceUnitName":"A 医院"}}}
```

### 4.2 acc2 四段的现场证据

| 段 | 命令 | 结果 |
|---|---|---|
| ① `paidEnabled==false` | `api.sh --as admin GET /lqg/ocr/status` | `{"code":200,"data":{"provider":"stub","paidEnabled":false}}` |
| ② prod 不出现开关 | `grep -rnE 'paid-enabled\|provider:\s*stub' application-prod.yml` | 无命中（`application-prod.yml` 里连 `ocr` 这个词都没有） |
| ③ 单测 + 夹具引用 | `mvn -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=…` + `grep ocr-cases.json` | 16/16 绿；grep 有命中 |
| ④ 限流 | extB 连打 8 次 | 6×200 + 2×500「识别太频繁了，请稍后再试（每分钟最多 6 次）」 |

### 4.3 `provider=none` 现场取证（票面没要求，但 accept 2 的 counterfeit 第 3 条点名的形态）

后端 8096（启动参数 `--lqg.ocr.provider=none`）：
```
$ api.sh --as admin GET /lqg/ocr/status
{"code":200,"msg":"操作成功","data":{"provider":"none","paidEnabled":false}}
$ api.sh --bizcode --as extA --form file=@doc/verify/fixtures/ocr-sample.png POST /mp/ocr/recognize
500	识别暂不可用，请手动填写
```
→ 是**明确的手填提示**，不是 `NullPointerException` / 系统异常。启动日志：`识别通道已就绪：provider=none，paid-enabled=false`。

---

## §5 越出 `touches` 的改动

**一条都没有。** `git status --porcelain` 里本票新增/修改的路径只有：

```
 M code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-dev.yml          ← touches 内
 M code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml                            ← ★ 不在 touches 字面里
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ocr/  ← touches 内
?? code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ocr/  ← touches 内
?? doc/waves/reports/OCR-IMPL-001/                                              ← 报告（票面 §4.2 要求）
```

**唯一一条需要主会话记 `issue` 的**：`ruoyi-modules/ruoyi-lqg/pom.xml` 不在 `touches` 里，但两处改动都**非它不可**：

1. `@RateLimiter` 注解的依赖：全仓只有 `ruoyi-admin` 直接依赖 `ruoyi-common-ratelimiter`，依赖方向是 admin → lqg，所以 lqg 里写 `@RateLimiter` **编不过**（实测 `package org.dromara.common.ratelimiter.annotation does not exist`）。
2. `doc/verify/fixtures/ocr-cases.json` 进 classpath：票面 §2 明说「从 classpath 里的 `ocr-cases.json` 副本（构建时从 `doc/verify/fixtures/` 拷）」，而**只拷进 test 资源会「单测绿、接口空」**（实测：`rawLines` 一直是 `[]`，日志 `识别测试桩找不到夹具 ocr-cases.json`）—— 因为 `StubOcrProvider` 是**运行期 bean**、跑在 jar 里。改成 main `<resources>` 后两边共用同一份夹具（没有在 `src/test/resources` 存副本）。

（`doc/waves/state.json` 的 ` M` 与 `doc/waves/ops/tianda-test-env.md` 的 `??` **不是本票产生的** —— 开工时 `state.json` 就已 modified，`tianda-test-env.md` 是别的会话的产物；本票一个字都没写它们。）

---

## §6 WARN 清单（请主会话逐条 `issue add`）

1. **WARN-1（口径差 · 低）**：prompt §2 写「单位名：整行与 active 单位名**完全相等**才认」，蓝图 `FLOW:F-OCR-01.step3` 写「来源单位（关键词 + 正则 + **单位名称表匹配**）」。实现按 prompt 的**更严**口径（全等、不相似度、不包含）——因为 prompt 的 accept 1 与 fixture 第 05 例把「不硬匹配」钉死了，且 prompt 明说「prompt 字面与蓝图冲突时以蓝图为准」在这里**不冲突**（蓝图只说"用单位名称表匹配"，没说匹配方式）。**未做模糊匹配**，如需放宽请走 CR。
2. **WARN-2（接口形状 · 低）**：`GET /lqg/ocr/status` 的权限串用的是既有的 `lqg:sample:list`，不是新造的 `lqg:ocr:status`。理由：`doc/lint-profile.yaml` 规定「OCR 无菜单」，而 api-contract.md 要求 `@SaCheckPermission` 与菜单 `perms` 逐字一致 —— 造新串就得建菜单（越出本票边界）。**副作用**：能看样本列表的内部人员都能读识别通道状态（信息量只有 provider 名与付费开关，无业务数据）。若 OCR-MP-001/后续票要独立权限行，届时补迁移。
3. **WARN-3（限流状态 · 中）**：Redisson 的令牌桶状态在 Redis 里**存活 24h**（框架 `@RateLimiter.timeout` 缺省 86400），且**删 key 不够** —— 同一个 JVM 里的 `RateLimiter` 实例还会拿旧状态继续判（实测：删光 `global:rate_limit:/mp/ocr/*` 后立刻发 8 次，全 500；清桶 + `sleep 70` 后才是标准的 6×200 + 2×500）。影响：**连续两遍跑本票 accept 会互相干扰**（acc1 的 5 次会吃掉 extA 的额度，acc2 的限流段吃掉 extB 的额度）。复现口径见 `rate-limit-live.txt`。建议：给验收环境加一条「跑 OCR 相关 accept 前清 `global:rate_limit:/mp/ocr/*` 并等 >60s」的前置，或把本票限流改成滑动窗口（`timeout == time`）—— 后者是口径变更，本票没擅自动。
4. **WARN-4（限流 key 的框架坑 · 中）**：框架 `@RateLimiter` 的缺省 key **不含用户**（`global:rate_limit:<URI>:`），`RateType.OVERALL` 是**全局桶** → 一个用户刷完**所有人**都被拦（实测：别的用例刷完 extA 的桶后，`doc/verify/api.sh` 起的所有身份都被「识别太频繁」挡住，连 accept 都跑不完）。本票用 `#{@ocrRateLimitKey.userKey()}` 补了用户维度，才符合 ticket §2 的「每用户」。**这个坑对将来所有用 `@RateLimiter` 的票都成立**，建议进 `doc/verify/README.md` 的「本栈已知的坑」表。
5. **WARN-5（SpEL 用法 · 低）**：框架建的是没有 `TypeLocator` 的 `MethodBasedEvaluationContext`：`#T(...)` → `EL1006E: Function 'T' could not be found`；`user:#T(...)` 还会先撞冒号语法 → `EL1041E`；`@bean.method()` 不含 `#` 会被当**字面量**（整个 key 变成 bean 名字符串）。可用的写法只有 `#{@bean.method()}`（模板形）。三种错法都实测过，写进了 `MpOcrController` / `OcrRateLimitKey` 的注释。
6. **WARN-6（`pom.xml` 的 `<resources>` · 低）**：给 `ruoyi-lqg` 加了 `<resources>` 后，`src/main/resources` 仍是标准默认目录，配置类能正常加载（`mvn package` + 起 8094 全绿）；`qa-up.sh` 的 stale 检查会因**测试源码**比 jar 新而重打 jar（无副作用，只是多花一次 build）。
7. **WARN-7（`/mp/ocr/recognize` 没带 file 时的响应）**：`POST /mp/ocr/recognize` **不带 multipart** 时，框架在进 controller 前就抛 `MultipartException` → `{"code":500,"msg":"Current request is not a multipart request"}`（不是本票的中文提示）。票面没要求这条，未改；小程序侧一直是 multipart 调用。
8. **WARN-8（`ocr-cases.json` 进 main 资源）**：运行时 jar 里现在带了一份夹具（1 组 5 例的测试文本）。它是**测试桩的输入**、不含任何真实数据；生产配 `provider=none` 时这份夹具不会被打到接口上，但**它确实进了交付 jar**。若 SYS-PROD-001 那条「上线包不带测试桩」的票要做，记得把 `stub` 的实现与夹具一起从 prod 构建里摘掉（`@Profile({"dev","test"})` 已经保证 prod 不注册这个 bean，但资源还在 jar 里）。

---

## §7 给下游的硬坑

1. **`OcrFieldParser` 是纯函数、只认「标签 + 值」**：一行里可以多个字段（`性别:男  年龄:56岁`），靠「值到下一个词表标签 / 下一个冒号 / 英文数字后遇中文」三个停点切。给**新字段**加标签时只需往 `LABELS` 里加一行 —— 但标签必须是**词表**里的，`床号` 这种不在词表里的字段名会被当成值的结束边界（这是 feature，不是 bug）。
2. **`ocr-cases.json` 是权威夹具、只有一份**：改解析规则要同步改 `doc/verify/fixtures/ocr-cases.json`（只读区，要改先在主会话确认）+ 本票单测；`StubOcrProvider` 与 `OcrFieldParserFixtureTest` 读的是同一份。
3. **`textResources` 的教训**：需要运行期（而不是只在测试期）读的 classpath 资源，必须放 **main** `<resources>`；放 test 资源的表现是「单测绿、接口空」，而且日志只在 dev 起服务时打一条 ERROR（`识别测试桩找不到夹具`）。
4. **provider 名是协议**：`none` / `stub` 同时是**配置值**、**bean 名**、**单测断言**；加真实 provider 时只要实现 `OcrProvider`（收费的再实现 `PaidOcrProvider`），配置里写类名首字母小写即可，不需要改工厂分支。
5. **付费开关**：`lqg.ocr.paid-enabled` 缺省 false 且 prod 不出现；把付费 provider 的 bean 加进来之后，**它不会被选中**（除非显式打开开关），这是 ADR-0007 + 合同第二条第 5 款要求的默认安全态。

---

## §8 收尾

- 后端（8094 / PID 92929 → 本报告写作前最后一次重启后的监听 PID）已按 PID 关停；**8094 已释放**（见 §9 的最终 `qa-up --down` 输出）。
- 8096（`provider=none` 取证实例）已按 PID（37131）关停、端口已释放。
- 工作台 / 小程序 H5 前端**本票全程没起**（`--no-web --no-mp`）。
- 未 push、未合分支、未动 `state.json` / `_manifest.json`。

### 报告目录内容（`doc/waves/reports/OCR-IMPL-001/`）

| 文件 | 内容 |
|---|---|
| `accept-run-final.json` | runner 的最终结果 JSON（passed 2 / total 2） |
| `accept-run-1.json`、`accept-run-2-after-60s.json` | 早先两次成功跑（干净桶 / 等 60s 后） |
| `accept-logs/OCR-IMPL-001-acc{1,2}.sh.log` | 票面两条 accept 的逐字 stdout |
| `accept-logs-repeat-noflush/*.log`、`accept-repeat-noflush-console.txt` | 不清桶连跑第二遍的失败现场（WARN-3 的证据） |
| `accept-repeat-after-60s-console.txt` | 等 >60s 后重跑成功的 console |
| `rate-limit-live.txt` | 限流实测 A/B/C + 每用户独立桶对照 |
| `none-provider-live.txt` | `provider=none` 的现场响应 + 启动日志 |
