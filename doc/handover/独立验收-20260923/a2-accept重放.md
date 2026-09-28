# a2 独立验收：D1–D7 票面 accept 隔离重放（HEAD b89d791）

- 副本：`$A/w` = `git archive b89d791`（`git diff --stat integration b89d791 -- ruoyi-modules/plus-ui/miniapp` 为空）；$A = `…/scratchpad/a2`
- 时间：2026-09-23 10:18–10:33（Asia/Shanghai）；工具：`doc/waves/tools/accept-run.py`，**未传 --known-red、未传 --include-escalated**；每个任务开始前 reseed 一次
- 结果目录：`$A/out/`（`Dn.json` / `Dn-logs/` / `Dn-accepts/` / `Dn-run.log`；D5 拆成 `D5-OCR-IMPL-001.json`、`D5-OCR-MP-001.json`）；合并清单 `out/all-results.json`；补丁 `$A/PATCHES.md`

## A. 环境自证

**容器（全部只绑 127.0.0.1，随机口令，已删除）**：lqg-a2-pg `postgres:16-alpine`（57433，库 lqg_a2_test，UTF8/C，TZ=Asia/Shanghai，PG 16.13）· lqg-a2-redis `redis:7.2-alpine` requirepass（57380）· lqg-a2-minio `minio/minio:latest`（57900 API / 57901 控制台）· lqg-a2-gotenberg `lqg-gotenberg:8 gotenberg --api-timeout=60s`（57010）。

**后端**：副本内 `mvn -o -q … -pl ruoyi-admin -am package` 成功（顺带 274 个单测，0 失败 0 错误，43 个测试类）；按 qa-up.sh 的 java 命令起（`-Dhttp.nonProxyHosts=…`、dev、`--api-decrypt.enabled=false`、`--server.port=8171`），环境变量给全 LQG_DB_*/LQG_REDIS_*/LQG_GOTENBERG_URL，另加 `-Djava.io.tmpdir=$A/tmp/java`。Flyway 在空库上成功 23 支迁移。

**后端连接清单（lsof -p）摘录**：
- 首次启动（pid 63006）：`*:8171 LISTEN`、10×`→127.0.0.1:57433`、9×`→127.0.0.1:57380`，别无他连。
- 改 OSS 后重启（pid 66104）：每个任务结束各抓一次（`out/Dn-backend-lsof.txt`），始终只有 57433 / 57380。
- D7 期间高频采样 2829 次（`out/backend-peers-sampled.txt`）+ 收尾前 25 秒采样（`out/backend-peers-final.txt`）：外连只出现 **57433、57380、57010、57900** 四个端口，其余都是打进 8171 的入站连接。**全程没有出现 5433/6380/3010/9000/9002**，没有触发「立即杀进程」条件。
- 工作台 dev（8172）代理实测打到 8171（后端日志的 /lqg/sys/ping 计数随代理请求 +1）；H5（9171）的 mock 登录走 8171（D7 探针页面 URL 为 `http://127.0.0.1:9171/#/`）。

**对象存储落点证明**：
- 第一次起后端建完表后停机 → 在 a2 库把 sys_oss_config 行 1（minio，默认）与行 5（image）改为 endpoint `127.0.0.1:57900`、a2 MinIO 凭据、bucket `lqg-a2`（access_policy 保持 '1'）→ a2 redis FLUSHALL（DBSIZE=0）→ a2 MinIO 里 `mc mb lqg-a2` → 重启后端。改之前那次运行只收到 1 个 `/lqg/sys/ping`（401），日志里的 OSS 相关只有「初始化OSS配置成功」（只写 redis 缓存，不连 MinIO），lsof 快照里也没有 9000。
- 验证上传：`POST /resource/oss/upload` 返回 url `http://127.0.0.1:57900/lqg-a2/2026/09/23/21c551d8….png`，`mc ls a2/lqg-a2` 立即看到该对象（73B）。
- 收尾前：sys_oss 里 **minio 行 520 条全部是 127.0.0.1:57900**（另 7 条是 seed 的 seed.invalid 假地址）；桶里 202 个对象（渲染出的 docx/pdf/png、上传的探针图）；后端签发的下载直链前缀 `http://127.0.0.1:57900/lqg-a2/lqg/doc/9000001001/…`，GET 200。
- 桶在 MinIO 层保持 private：lqg 的所有下载都走 `createPresignedGetUrl`（DocArtifactStore / QcImagePreviewResolver），不依赖匿名读。

**PATCHES.md 摘要**（104 行 / 28 个文件，code/ 一行未动；另有非逐行的环境适配）：
1. 票面 front-matter（17 张 D1–D7 票）：`/tmp/lqg-*` → `${TMPDIR}/lqg-*`（a3 正在并行跑，同名 /tmp 文件会互相覆盖/互删，如 CRYO-FLOW-001 acc3 数 `/tmp/lqg-race-*.txt`）；DOC-PDF-001 acc2 / SYS-HOME-001 acc1 的 `docker compose -f code/deploy/dev/docker-compose.yml stop|start gotenberg` → `docker stop|start lqg-a2-gotenberg`，start 后等 57010/health；SYS-HOME-001 的 `3010/health` → 57010。重新 emit 的 94 条脚本与原文逐字 diff，只有这几类差异。
2. D7 accept-strengthened：`mutate.py / merge-evidence.py / probe-h3a.py / common.mjs` 的 **ROOT 写死为工作区绝对路径**（不改会把变异写进工作区源码并在工作区 `git checkout`）→ 改为 `$A/w`；common.mjs 的 WEB 8093→8172、MP 9204→9171、OSS 前缀 9000→57900；setup-fixture.sh 兜底 env 8094→本环境 verify.env。
3. mutation-assert.sh：缺省端口 8094/8093/9204→8171/8172/9171；「后端不在就 qa-up」分支改为直接 exit 2（qa-up 会 source 不存在的 dev/.env、不带 LQG_DB_* 起 JVM → 缺省连 dev 库 5433）；evidence 元数据端口改真值。
4. 兜底缺省值（正常流程不走，纯防御）：api.sh 8080→8171、db.py/reseed.sh/clean-orphan-accounts.sh 5432→57433、D1/verify.sh 8081→8171、5432→57433。
5. 非逐行适配：副本内 `git init` 并把导出树原样提交为基线 db10f22（mutation-assert 的树守卫与 mutate.py 的还原都要求是 git 仓库；只在副本，工作区零 git 写）；verify.env 按指令新建；运行期导出 TMPDIR=$A/tmp、LQG_VERIFY_ENV_FILE、LQG_* 与 LQG_ACCEPT_*_PORT。
6. 未改：code/miniapp/env/.env 的 8081、plus-ui/.env.development 的 8081（启动时用同名环境变量覆盖，已实测生效）；qa-up.sh（补丁后不可达）；probe.mjs 判据名里的「H5(9204)」「127.0.0.1:9000」文案。
- 起跑前自检：accept 脚本 + doc/verify + doc/waves/tools + D1/D7 回归脚本共 109 个文件，5433/6380/9000/9002/3010/lqg-dev-/dev-minio/工作区路径已无功能性引用（只剩注释、判据文案和我自己的报错提示）；verify.env 指向 57433 与 8171。

## B. 总表（94 条票面 accept + 2 条 escalated 票的 accept）

| 任务 | 票 | accN | accept 名（截断） | 结果 | 归类 | 一句依据 | 日志 |
|---|---|---|---|---|---|---|---|
| D1 | AUTH-GROUP-001 | acc1 | 两张表与 SSOT 逐列相符且出自本票 Flyway；同单位… | green | — | exit 0（0.4s） | `out/D1-logs/AUTH-GROUP-001-acc1.sh.log` |
| D1 | AUTH-GROUP-001 | acc2 | 核验状态机：非法转移被拒且库里不变；自填单位不选新建或归并不… | green | — | exit 0（1.9s） | `out/D1-logs/AUTH-GROUP-001-acc2.sh.log` |
| D1 | AUTH-GROUP-001 | acc3 | 对外的单位选择器只含启用项且不带任何人数；两个菜单落在 51… | green | — | exit 0（0.2s） | `out/D1-logs/AUTH-GROUP-001-acc3.sh.log` |
| D1 | AUTH-LOGIN-001 | acc1 | 两张表与 SSOT 逐列相符（含公共字段与部分唯一索引）且出… | green | — | exit 0（0.3s） | `out/D1-logs/AUTH-LOGIN-001-acc1.sh.log` |
| D1 | AUTH-LOGIN-001 | acc2 | 首次登录自动建外部账号、再登录不重复建、换微信号仍是同一个人… | green | — | exit 0（1.7s） | `out/D1-logs/AUTH-LOGIN-001-acc2.sh.log` |
| D1 | AUTH-LOGIN-001 | acc3 | mock 登录护栏：prod 打开 mock 必须拒绝启动（… | green | — | exit 0（4.4s） | `out/D1-logs/AUTH-LOGIN-001-acc3.sh.log` |
| D1 | AUTH-STAFF-001 | acc1 | 授权是升级不是新建：给已登录过的外部手机号授权后账号仍只有一… | green | — | exit 0（1.9s） | `out/D1-logs/AUTH-STAFF-001-acc1.sh.log` |
| D1 | AUTH-STAFF-001 | acc2 | 工作台只让内部进：带着可用口令的外部账号也登不上（被拒在登录… | green | — | exit 0（1.0s） | `out/D1-logs/AUTH-STAFF-001-acc2.sh.log` |
| D1 | AUTH-STAFF-001 | acc3 | 菜单落在 5100 段、path 与 component 非… | green | — | exit 0（0.2s） | `out/D1-logs/AUTH-STAFF-001-acc3.sh.log` |
| D1 | SYS-BASE-001 | acc1 | 库是 PostgreSQL 16、基线与字典与角色都出自本票… | green | — | exit 0（0.4s） | `out/D1-logs/SYS-BASE-001-acc1.sh.log` |
| D1 | SYS-BASE-001 | acc2 | 业务模块真的挂进了后端且反映真实环境：ping 的 db /… | green | — | exit 0（0.7s） | `out/D1-logs/SYS-BASE-001-acc2.sh.log` |
| D1 | SYS-BASE-001 | acc3 | 测试账号灌得进真实的若依表结构：8 个账号与角色逐一对上，且… | **red** | ② | 断言把 D1 当时「后续表未建→04 起 6 段 seed 被跳过」写成永久条件；HEAD 上 7 段全灌，按构造必红 | `out/D1-logs/SYS-BASE-001-acc3.sh.log` |
| D1 | SYS-MP-001 | acc1 | 产物是本次构建的、三个页签名称与顺序钉死、组件导入与选择器两… | green | — | exit 0（5.0s） | `out/D1-logs/SYS-MP-001-acc1.sh.log` |
| D1 | SYS-MP-001 | acc2 | 首页与「我的」只认后端给的身份：内部四个入口、外部三个（没有… | green | — | exit 0（1.2s） | `out/D1-logs/SYS-MP-001-acc2.sh.log` |
| D1 | SYS-MP-001 | acc3 | 方向 A 设计 token 落地：src/style/tok… | green | — | exit 0（0.0s） | `out/D1-logs/SYS-MP-001-acc3.sh.log` |
| D1 | SYS-WEB-001 | acc1 | 管理员拿得到字典管理与参数设置且路由可达、path 与 co… | **red** | ②(可议) | D6 迁移为 staff 上传 QC 图给 102 授了 1600-1602（system:oss:*，F 按钮）；与 DOC-RENDER-001 acc1 票面冲突，路由里无系统管理菜单 | `out/D1-logs/SYS-WEB-001-acc1.sh.log` |
| D1 | SYS-WEB-001 | acc2 | 前端生产构建通过且是本次产物；品牌已换、上游的多租户字样与注… | green | — | exit 0（10.7s） | `out/D1-logs/SYS-WEB-001-acc2.sh.log` |
| D2 | AUTH-EXT-001 | acc1 | 四条结构性不变量（契约测试逐字节未改）：入口只有 /mp/e… | green | — | exit 0（4.3s） | `out/D2-logs/AUTH-EXT-001-acc1.sh.log` |
| D2 | AUTH-EXT-001 | acc2 | 可见集合逐身份钉死：同组互看、同单位异组不可见、同组但未核验… | green | — | exit 0（1.9s） | `out/D2-logs/AUTH-EXT-001-acc2.sh.log` |
| D2 | AUTH-EXT-001 | acc3 | 写保护：同组别人的样本可看不可改、已核验有效的自己也改不了、… | green | — | exit 0（1.9s） | `out/D2-logs/AUTH-EXT-001-acc3.sh.log` |
| D2 | SAMPLE-MODEL-001 | acc1 | 样本表与 SSOT 逐列相符（含公共字段、内部编号与送检单号… | green | — | exit 0（0.3s） | `out/D2-logs/SAMPLE-MODEL-001-acc1.sh.log` |
| D2 | SAMPLE-MODEL-001 | acc2 | 写入时真的加了密、读出时真的解了密（库里的密文与 opens… | green | — | exit 0（1.6s） | `out/D2-logs/SAMPLE-MODEL-001-acc2.sh.log` |
| D2 | SAMPLE-MODEL-001 | acc3 | 内部编号全库唯一但软删后可重用、重复时拒绝且不落库；内部新增… | green | — | exit 0（1.6s） | `out/D2-logs/SAMPLE-MODEL-001-acc3.sh.log` |
| D2 | SAMPLE-MP-001 | acc1 | 表单布局只认后端给的身份、状态与入口模式：外部永远不渲染收样… | green | — | exit 0（0.8s） | `out/D2-logs/SAMPLE-MP-001-acc1.sh.log` |
| D2 | SAMPLE-MP-001 | acc2 | 小程序构建通过且是本次产物；内部接口只给内部角色；内部「历史… | green | — | exit 0（6.2s） | `out/D2-logs/SAMPLE-MP-001-acc2.sh.log` |
| D2 | SAMPLE-MP-002 | acc1 | 表格页的类器官工作表与库里独立数的一致；类器官收样内部录入落… | green | — | exit 0（1.9s） | `out/D2-logs/SAMPLE-MP-002-acc1.sh.log` |
| D2 | SAMPLE-MP-002 | acc2 | 表格页的列名、列序只有一个来源，且与甲方四份模板原件逐字对得… | green | — | exit 0（5.6s） | `out/D2-logs/SAMPLE-MP-002-acc2.sh.log` |
| D2 | SAMPLE-MP-002 | acc3 | 类器官收样填写页布局只认后端给的身份、状态与入口模式：外部只… | green | — | exit 0（0.7s） | `out/D2-logs/SAMPLE-MP-002-acc3.sh.log` |
| D2 | SAMPLE-VERIFY-001 | acc1 | 非法转移被拒且库里不变：判有效缺内部编号、内部编号撞号、判无… | green | — | exit 0（1.7s） | `out/D2-logs/SAMPLE-VERIFY-001-acc1.sh.log` |
| D2 | SAMPLE-VERIFY-001 | acc2 | 内外部属性是提交当时的快照：提交人后来被授权成内部，他以前送… | green | — | exit 0（1.7s） | `out/D2-logs/SAMPLE-VERIFY-001-acc2.sh.log` |
| D2 | SAMPLE-WEB-001 | acc1 | 筛选逐项钉死在 seed 上：来源单位、组别、类别、内外部、… | green | — | exit 0（1.0s） | `out/D2-logs/SAMPLE-WEB-001-acc1.sh.log` |
| D2 | SAMPLE-WEB-001 | acc2 | 菜单落在 5200 段、可达、内部人员看得到；页面与公共按钮… | **red** | ② | =#129：5210 下 perms「恰六个」与 SAMPLE-EXPORT-001 必加的 lqg:sample:export 按构造互斥 | `out/D2-logs/SAMPLE-WEB-001-acc2.sh.log` |
| D3 | AUTH-EXT-002 | acc1 | 可见集合逐身份钉死（含待核验的外部送样）；同组可看、异组按不… | green | — | exit 0（5.9s） | `out/D3-logs/AUTH-EXT-002-acc1.sh.log` |
| D3 | AUTH-EXT-002 | acc2 | 提交与重提的写保护：替同组别人的样本送样、挂到自己无效的样本… | green | — | exit 0（1.8s） | `out/D3-logs/AUTH-EXT-002-acc2.sh.log` |
| D3 | AUTH-EXT-002 | acc3 | 小程序外部详情页接上了包埋卡片（含待核验的送样），卡片上有操… | green | — | exit 0（4.4s） | `out/D3-logs/AUTH-EXT-002-acc3.sh.log` |
| D3 | EMBED-MODEL-001 | acc1 | 两张表与 SSOT 逐列相符（含公共字段、核验状态六列、石蜡… | green | — | exit 0（0.3s） | `out/D3-logs/EMBED-MODEL-001-acc1.sh.log` |
| D3 | EMBED-MODEL-001 | acc2 | 染色与挂靠规则：无染色与其余互斥、选其他必须写名称、字典外的… | green | — | exit 0（1.8s） | `out/D3-logs/EMBED-MODEL-001-acc2.sh.log` |
| D3 | EMBED-MODEL-001 | acc3 | 读出来的形状：染色是数组、marker 成组、内部编号读时带… | green | — | exit 0（0.8s） | `out/D3-logs/EMBED-MODEL-001-acc3.sh.log` |
| D3 | EMBED-MODEL-001 | acc4 | 外部送样的核验状态机：所挂样本还没有效时判有效被拒、缺原因判… | green | — | exit 0（1.8s） | `out/D3-logs/EMBED-MODEL-001-acc4.sh.log` |
| D3 | EMBED-MP-001 | acc1 | 小程序构建是本次产物、填写页与表格页在产物里、石蜡包埋工作表… | green | — | exit 0（5.6s） | `out/D3-logs/EMBED-MP-001-acc1.sh.log` |
| D3 | EMBED-MP-001 | acc2 | 补填就是修改：对已有石蜡块 PUT 只改传入的工序时间、不新… | green | — | exit 0（1.7s） | `out/D3-logs/EMBED-MP-001-acc2.sh.log` |
| D3 | EMBED-MP-001 | acc3 | 石蜡包埋填写页布局只认后端给的身份、状态与入口模式：外部只渲… | green | — | exit 0（0.7s） | `out/D3-logs/EMBED-MP-001-acc3.sh.log` |
| D3 | EMBED-WEB-001 | acc1 | 导出的 Excel 与甲方模板原件逐字对表头；行数 = 未删… | green | — | exit 0（2.7s） | `out/D3-logs/EMBED-WEB-001-acc1.sh.log` |
| D3 | EMBED-WEB-001 | acc2 | 菜单落在 5300 段、可达、按钮权限含核验；页面已建且接上… | green | — | exit 0（2.4s） | `out/D3-logs/EMBED-WEB-001-acc2.sh.log` |
| D3 | SAMPLE-EXPORT-001 | acc1 | 两张导出与甲方模板原件逐字对表头；行数与同条件的列表总数一致… | green | — | exit 0（1.6s） | `out/D3-logs/SAMPLE-EXPORT-001-acc1.sh.log` |
| D3 | SAMPLE-EXPORT-001 | acc2 | 导出按钮权限已 seed 且出自本票迁移；总表页面的两个导出… | green | — | exit 0（0.3s） | `out/D3-logs/SAMPLE-EXPORT-001-acc2.sh.log` |
| D3 | SAMPLE-HINT-001 | acc1 | 提示逐样本钉死在 seed 上（两块一切片 HE+IHC；无… | green | — | exit 0（0.8s） | `out/D3-logs/SAMPLE-HINT-001-acc1.sh.log` |
| D3 | SAMPLE-HINT-001 | acc2 | 没有在样本表上偷加冗余字段；工作台提示组件已接入总表；一页只… | green | — | exit 0（3.3s） | `out/D3-logs/SAMPLE-HINT-001-acc2.sh.log` |
| D4 | CRYO-FLOW-001 | acc1 | 取走补入调整后，接口给的剩余与直连库独立汇总的一致；取自位置… | green | — | exit 0（1.8s） | `out/D4-logs/CRYO-FLOW-001-acc1.sh.log` |
| D4 | CRYO-FLOW-001 | acc2 | 改删登记：改支数后记下修改人、删补入后剩余与直连库独立汇总一… | green | — | exit 0（2.0s） | `out/D4-logs/CRYO-FLOW-001-acc2.sh.log` |
| D4 | CRYO-FLOW-001 | acc3 | 并发取最后两支只成功一次、剩余恰好为 0 不为负；转液氮时间… | green | — | exit 0（1.5s） | `out/D4-logs/CRYO-FLOW-001-acc3.sh.log` |
| D4 | CRYO-MODEL-001 | acc1 | 两张表与 SSOT 逐列相符、出自本票 Flyway；批次表… | green | — | exit 0（0.3s） | `out/D4-logs/CRYO-MODEL-001-acc1.sh.log` |
| D4 | CRYO-MODEL-001 | acc2 | 批次校验：代数格式、支数、直接进液氮必须有位置、转液氮时间不… | green | — | exit 0（1.8s） | `out/D4-logs/CRYO-MODEL-001-acc2.sh.log` |
| D4 | CRYO-MODEL-001 | acc3 | 剩余支数读时计算：接口给的每批剩余与直连库独立汇总的逐批一致… | green | — | exit 0（0.8s） | `out/D4-logs/CRYO-MODEL-001-acc3.sh.log` |
| D4 | CRYO-MP-001 | acc1 | 工作表的页签计数、逐批剩余与超期天数和库里独立数的一致且钉在… | green | — | exit 0（1.8s） | `out/D4-logs/CRYO-MP-001-acc1.sh.log` |
| D4 | CRYO-MP-001 | acc2 | 小程序里没有取用登记的写接口：取走、改登记、删登记、转液氮在… | green | — | exit 0（1.6s） | `out/D4-logs/CRYO-MP-001-acc2.sh.log` |
| D4 | CRYO-MP-001 | acc3 | 转液氮之后提醒就没了（CR-20260918-07 甲方问「… | green | — | exit 0（1.5s） | `out/D4-logs/CRYO-MP-001-acc3.sh.log` |
| D4 | CRYO-MP-001 | acc4 | 小程序构建是本次产物；-80 冻存工作表与历史页签已注册、填… | green | — | exit 0（4.4s） | `out/D4-logs/CRYO-MP-001-acc4.sh.log` |
| D4 | CRYO-REMIND-001 | acc1 | 超期清单钉死在 seed 的边界病灶上：阈值取系统参数 `l… | green | — | exit 0（4.0s） | `out/D4-logs/CRYO-REMIND-001-acc1.sh.log` |
| D4 | CRYO-REMIND-001 | acc2 | 提醒跟着状态走：登记转液氮当场出清单、超期计数减 1；支数取… | green | — | exit 0（1.7s） | `out/D4-logs/CRYO-REMIND-001-acc2.sh.log` |
| D4 | CRYO-REMIND-001 | acc3 | 阈值是系统参数不是常量（CR-20260918-07）：sy… | green | — | exit 0（1.1s） | `out/D4-logs/CRYO-REMIND-001-acc3.sh.log` |
| D4 | CRYO-WEB-001 | acc1 | 导出的 Excel 与甲方模板原件逐字对表头（只许追加代数与… | green | — | exit 0（1.6s） | `out/D4-logs/CRYO-WEB-001-acc1.sh.log` |
| D4 | CRYO-WEB-001 | acc2 | 菜单落在 5400 段、可达、流水与导出两个权限串下发到内部… | green | — | exit 0（0.2s） | `out/D4-logs/CRYO-WEB-001-acc2.sh.log` |
| D5 | OCR-IMPL-001 | acc1 | 五个解析用例逐例过：该出现的字段相等、不该出现的字段不出现（… | green | — | exit 0（1.0s） | `out/D5-logs/OCR-IMPL-001-acc1.sh.log` |
| D5 | OCR-IMPL-001 | acc2 | 付费通道默认关且 prod 配置里不出现这个开关；没配 pr… | green | — | exit 0（3.4s） | `out/D5-logs/OCR-IMPL-001-acc2.sh.log` |
| D5 | OCR-MP-001 | acc1 | 预填合并规则过 fixture：只填空项、已手填的不覆盖、空… | green | — | exit 0（0.8s） | `out/D5-logs/OCR-MP-001-acc1.sh.log` |
| D5 | OCR-MP-001 | acc2 | 构建是本次产物；识别条只在新增时出现；小程序包里没有任何第三… | green | — | exit 0（4.4s） | `out/D5-logs/OCR-MP-001-acc2.sh.log` |
| D6 | DOC-PDF-001 | acc1 | PDF 里的中文是真字不是豆腐块（能抽出文字、字体已嵌入且不… | green | — | exit 0（3.5s） | `out/D6-logs/DOC-PDF-001-acc1.sh.log` |
| D6 | DOC-PDF-001 | acc2 | 失败看得见、能重试：转换服务不可用时状态为 failed 且… | green | — | exit 0（11.6s） | `out/D6-logs/DOC-PDF-001-acc2.sh.log` |
| D6 | DOC-RENDER-001 | acc1 | 渲染出的 Word：带出字段与填写字段都在、两句印死的注逐字… | green | — | exit 0（2.6s） | `out/D6-logs/DOC-RENDER-001-acc1.sh.log` |
| D6 | DOC-RENDER-001 | acc2 | 渲染产物表与 SSOT 相符；指纹缓存成立：内容没变不重出、… | green | — | exit 0（6.4s） | `out/D6-logs/DOC-RENDER-001-acc2.sh.log` |
| D6 | QC-MODEL-001 | acc1 | 五张表与 SSOT 逐列相符、出自本票 Flyway；每个样… | green | — | exit 0（0.4s） | `out/D6-logs/QC-MODEL-001-acc1.sh.log` |
| D6 | QC-MODEL-001 | acc2 | 评分由后端按字典回填：前端夹带的假分值不生效、0 分档不被当… | green | — | exit 0（1.7s） | `out/D6-logs/QC-MODEL-001-acc2.sh.log` |
| D6 | QC-MODEL-001 | acc3 | 首次打开幂等建三份草稿且默认文字逐字照模板；图片位规则（归属… | green | — | exit 0（1.8s） | `out/D6-logs/QC-MODEL-001-acc3.sh.log` |
| D6 | QC-WEB-001 | acc1 | 隐藏菜单可路由：path 与 component 非空、vi… | green | — | exit 0（0.1s） | `out/D6-logs/QC-WEB-001-acc1.sh.log` |
| D6 | QC-WEB-001 | acc2 | 前端构建是本次产物；编辑页用了图片位上传与附件组件；带出的只… | green | — | exit 0（10.8s） | `out/D6-logs/QC-WEB-001-acc2.sh.log` |
| D6 | QC-WEB-002 | acc1 | 评分即时反馈过 fixture：0 分档照算、没选全合计为空… | green | — | exit 0（1.8s） | `out/D6-logs/QC-WEB-002-acc1.sh.log` |
| D6 | QC-WEB-002 | acc2 | 前端构建是本次产物；两个页签已接入编辑页；评分选项文字照模板… | green | — | exit 0（10.2s） | `out/D6-logs/QC-WEB-002-acc2.sh.log` |
| D7 | AUTH-EXT-003 | acc1 | 外部文档清单钉死在 seed 上：同组可见、草稿不给、外部版… | green | — | exit 0（7.6s） | `out/D7-logs/AUTH-EXT-003-acc1.sh.log` |
| D7 | AUTH-EXT-003 | acc2 | 四条结构性不变量在加了文档接口之后仍然成立；新增的 VO 不… | green | — | exit 0（4.2s） | `out/D7-logs/AUTH-EXT-003-acc2.sh.log` |
| D7 | DOC-MP-001 | acc1 | 分组规则过 fixture：组内固定顺序、组间按最新完成时间… | green | — | exit 0（0.8s） | `out/D7-logs/DOC-MP-001-acc1.sh.log` |
| D7 | DOC-MP-001 | acc2 | 构建是本次产物；内部清单只给内部角色且带内部编号、外部清单不… | green | — | exit 0（10.4s） | `out/D7-logs/DOC-MP-001-acc2.sh.log` |
| D7 | DOC-MP-002 | acc1 | 构建是本次产物；预览页用到了两层放大、打开、发送到微信四个平… | green | — | exit 0（18.9s） | `out/D7-logs/DOC-MP-002-acc1.sh.log` |
| D7 | DOC-MP-002 | acc2 | 内部的页面图片接口可用且只给内部；页数与 PDF 一致；外部… | green | — | exit 0（6.2s） | `out/D7-logs/DOC-MP-002-acc2.sh.log` |
| D7 | DOC-PUBLISH-001 | acc1 | 两态状态机：重复完成、对草稿撤回被拒；完成后记下完成人与时间… | green | — | exit 0（22.0s） | `out/D7-logs/DOC-PUBLISH-001-acc1.sh.log` |
| D7 | DOC-PUBLISH-001 | acc2 | 前端构建是本次产物；预览面板接入编辑页，有内外部版切换、**… | green | — | exit 0（23.7s） | `out/D7-logs/DOC-PUBLISH-001-acc2.sh.log` |
| D7 | SYS-ACCEPT-001 | acc1 | 四条高危断言改成行为判据后，**改坏必须变红**：对每个热点… | green | — | exit 0（234.0s） | `out/D7-logs/SYS-ACCEPT-001-acc1.sh.log` |
| D7 | SYS-ACCEPT-001 | acc2 | 强化后的四条断言在未改坏的树上全绿，且每条都留下了真实运行证… | green | — | exit 0（64.9s） | `out/D7-logs/SYS-ACCEPT-001-acc2.sh.log` |
| D7 | SYS-EXPORT-001 | acc1 | 小程序导出的四个文件与甲方模板原件逐字对表头、行数与库内独立… | green | — | exit 0（2.1s） | `out/D7-logs/SYS-EXPORT-001-acc1.sh.log` |
| D7 | SYS-EXPORT-001 | acc2 | 构建是本次产物；导出与文档下载共用同一段「打开 / 发送到微… | green | — | exit 0（53.2s） | `out/D7-logs/SYS-EXPORT-001-acc2.sh.log` |
| D7 | SYS-HOME-001 | acc1 | 五个数与库里独立数的一致并钉在 seed 上；外部新交一条类… | green | — | exit 0（6.1s） | `out/D7-logs/SYS-HOME-001-acc1.sh.log` |
| D7 | SYS-HOME-001 | acc2 | 前端构建是本次产物；首页五张卡片接的是接口而不是写死的数（*… | green | — | exit 0（17.2s） | `out/D7-logs/SYS-HOME-001-acc2.sh.log` |
| D5 | SYS-STAGING-001 | acc1 | 测试环境跑的就是当前这份代码… | skipped-remote | — | 要 verify.test.env + 公网测试机；执行方 known-red #191,#198 | — |
| D5 | SYS-STAGING-001 | acc2 | 测试机数据库与缓存端口不对公网开放… | skipped-remote | — | 要对公网测试机 nc；执行方 known-red #192,#181 | — |
| D5 | OCR-SPIKE-001 | acc1 | 报告里的命中率与逐张逐字段原始记录… | 未重放 | — | state=escalated，accept-run 默认跳过（按指令未加 --include-escalated） | — |
| D7 | DOC-PROOF-001 | acc1 | 样张齐全、是测试环境当前模板版本… | 未重放 | — | state=escalated，同上 | — |

## C. 计数与对照

| 口径 | 条数 |
|---|---|
| D1–D7 票面 accept 总数（accept-run 可见，不含 escalated） | 94 |
| 实际重放 | 92 |
| green | 89 |
| red | 3（① 产品缺陷 0 ｜ ② 断言或夹具缺陷 3，其中 SYS-WEB-001 acc1 可议 ｜ ③ 隔离环境引入 0） |
| skipped-remote | 2（SYS-STAGING-001 acc1/acc2） |
| 另：escalated 票未重放 | 2（OCR-SPIKE-001 acc1、DOC-PROOF-001 acc1；不在执行方的 38 张 done 里） |

分任务：D1 15/17；D2 14/15；D3 16/16；D4 15/15；D5 4/4；D6 11/11；D7 14/14（D5 另 2 条 skipped-remote）。另外按 gate.sh L1.3 的调法跑了 D1 回归包（`EXPECT_REV=b89d791 bash doc/waves/regression/D1/verify.sh --skip-build`）：**42 ✓ / 0 ✗，exit 0**（`out/D1-regression.log`）。

**与执行方「38 张 done、D1–D7 全过」的对照**：各任务当期的门在当时是绿的，这一点和本次结果不矛盾；但**在 HEAD 上把 D1–D7 全部票面一起重放，有 3 条红**，全部落在 D1/D2 的老票上，都是后续任务改了它们断言的前提、而模式 B 只重放当期票，所以没有哪一道门再跑过它们：
- SAMPLE-WEB-001 acc2：从 D3（SAMPLE-EXPORT-001 加了第 7 个按钮）起就是红的。执行方台账 #129 说它「按构造必红」，**实测确实是红**。
- SYS-BASE-001 acc3：从 D2 起就是红的（后续任务的表建起来以后，seed 不会再被跳过）。执行方没为它开过 issue。
- SYS-WEB-001 acc1：从 D6（DOC-RENDER-001 的迁移给 102 授了 1600-1602）起就是红的。执行方 #218 只记了「给内部角色扩了上传权限」，**没有把它和这条 D1 断言关联起来**。

执行方对 D1 的常驻保护只有 D1 回归包，而回归包里没有这三条断言，所以它的 42 条全绿，挡不住这 3 条红。

## D. 每条 red 的诊断

**1. D1 SYS-BASE-001 acc3（② 断言依赖了已不存在的前提）** — `out/D1-logs/SYS-BASE-001-acc3.sh.log`
- 断点：第一个 `&&` 链的第 3 段 `printf '%s' "${OUT}" | grep -q '跳过 04-sample.sql'`，exit 1。`grep -q` 不输出任何东西，所以日志正文是空的。
- 复现（同一库）：`reseed.sh --yes` 的输出是 `已灌 01-accounts.sql … 已灌 07-qc-docs.sql / reseed 完成`，7 段全部灌入，没有一行「跳过」。`grep 已灌 01` 的 rc=0，`grep 跳过 04` 的 rc=1。后半段「8 个账号与角色」的 `db.py --col-set` 单独跑 exit 0。
- 依据：票面原话是「此刻其余 6 段 seed 因表未建被跳过」，这只在 D1 当时成立。D2 起 t_lqg_sample 等表建好了，这段就按构造必红，与实现无关。

**2. D1 SYS-WEB-001 acc1（② 票面间冲突；可议）** — `out/D1-logs/SYS-WEB-001-acc1.sh.log`
- 断点：最后一段 `db.py --sql "SELECT count(*) FROM sys_role_menu WHERE role_id = 102 AND menu_id < 5000" --eq 0` → `[FAIL] 期望 '0'，实际 '3'`。前 6 段都绿：管理员拿得到字典与参数设置的路由、两个参数值 false/14 对、101 有字典菜单、101/102 没有空 path 的 M/C 菜单。
- 实际数据：这 3 行是 `1600 文件查询 system:oss:query`、`1601 文件上传 system:oss:upload`、`1602 文件下载 system:oss:download`，都是 F 按钮，父菜单是 118（文件管理）。来源是 `V202609261410__DOC-RENDER-001-doc-menu.sql`（D6）第 39-44 行，给 101/102 补授。迁移注释说明了原因：staff 要调 `/resource/oss/upload`。工作台 `ImageSlotUploader` 和 `api/lqg/qc/index.ts` 上传质控图片也走这个接口。staff 的 getRouters 里没有系统管理节点，只有样本总表、石蜡包埋、人员与单位、质控文档、冻存管理。
- 依据：D1 用「102 在 <5000 段没有任何授权」来代表「拿不到系统管理菜单」。D6 为满足 DOC-RENDER-001 acc1（它用 `--as staff` 调 `/resource/oss/upload`）加了这 3 条按钮授权，这个代理条件就失效了。用户能看到的菜单没受影响；执行方 #218 只登记了扩权，没改 D1 票面，也没走 CR。
- 为什么说可议：如果坚持 D1 字面，这就是 D6 实现越界（①）。确实存在两全的做法：在 5xxx 段新建 F 行来承载 system:oss:* 权限串。

**3. D2 SAMPLE-WEB-001 acc2（② = 执行方 #129）** — `out/D2-logs/SAMPLE-WEB-001-acc2.sh.log`
- 断点：第 4 段 `db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5210 AND menu_type = 'F'" --col-set "lqg:sample:list,…,lqg:sample:verify"` → `[FAIL] 集合不相等：多出 ['lqg:sample:export']，缺少 []`。前 3 段都绿：5210 菜单、授给 101/102、staff 路由可达。
- 依据：D3 的 SAMPLE-EXPORT-001 acc2 强制要求 `5217:lqg:sample:export:5210`（迁移是 V202609231000），两张票的断言按构造互斥。#129（open，S2 doc-drift）的裁定是「SAMPLE-WEB-001 的断言写早了、过紧」。**#129 说在 HEAD 上必红，实测确实是红。**

**执行方已登记的 known-red（单列，没有跑）**：D5 SYS-STAGING-001 acc1（#191 票面漏 `&sort=recent`、#198 buildCommit 落后 HEAD）和 acc2（#192 BSD sed 不支持 `\?` 导致 HOST 恒空、#181 本机 nc 被代理劫持）。这两条都要读 `verify.test.env` 去连公网测试机 songjian.tianda.studio，或者对它 nc。按指令记 skipped-remote。另外，副本里没有 verify.test.env（它被 gitignore 了），硬跑 acc1 会让 api.sh 落到缺省地址。

**隔离重放中发现、但本次没有致红的工具隐患**（供执行方参考）：
1. D7 accept-strengthened 的 ROOT 写死在工作区。**任何在副本里重放 SYS-ACCEPT-001 的人，如果不打补丁，都会改工作区源码并在工作区 `git checkout`。** a3 正在并行跑，建议确认它打过这个补丁。本次收尾时工作区 `git status` 干净，observations 目录的时间戳仍是 08:54。
2. DOC-PDF-001 acc2 和 SYS-HOME-001 acc1 的 `RC=$?` 写法在 `set -e` 下不生效：「渲染应失败」那条管道一旦红，脚本会在 `docker start` 之前直接退出，gotenberg 就一直停着，之后所有渲染类 accept 都会被连带打红。这次两条都绿，没有触发。我的 runner 在每个任务结束后都核过 gotenberg 在跑。
3. mutation-assert 在后端不在时会调 qa-up，由它自动拉起后端；api.sh 在缺 LQG_API_BASE 时会落到 8080（Kevin 的服务）。这两处我都打了补丁。

## E. 没做成的部分、偏差与收尾

- **没跑**：SYS-STAGING-001 的 acc1/acc2（远程）；OCR-SPIKE-001 和 DOC-PROOF-001 的 accept（escalated，按指令不加 --include-escalated）。D1 回归包按 gate L1.3 的写法带了 `--skip-build`，所以它自己的 mvn install 和两个前端构建没跑。不过后端的 274 个单测在我自己打包时已经全部跑过，两个前端的生产构建也由多条 accept 当场重建过（mp-weixin 的 app.json 10:31、plus-ui 的 dist/index.html 10:32）。
- **执行上的偏差**（都已记录）：
  - 每个任务 reseed 之前，先清了本环境私有的 token 缓存（`$A/tmp/lqg-verify-token-*`），和 gate.sh 的 clear_tokens 一样。
  - D5 用 `--only` 分两次跑，避开 SYS-STAGING-001。reseed 只在第一次之前做了一次。
  - 各任务之间没有跑 clean-orphan-accounts（指令里没有这一步）。AUTH-LOGIN-001 acc2 按设计建出的孤儿号 wx_13800000099 一直留到 D7 结束，没有任何一条红和它有关；D1 回归包收尾时把它清掉了。
  - 在工作区只跑过只读 git 命令。其中有一次 `git status --ignored` 没带 `--no-optional-locks`，理论上可能刷新 .git/index 的 stat 缓存；事后 `git status` 干净，HEAD 仍是 b89d791。
- **旁证（不是 a2 造成的）**：工作区根目录的 `logs/sys-*.log`（gitignored）在本次运行期间被 PID 59051 持续写入。它是 a3 的后端：jar 在 `scratchpad/a3/w`，监听 :8181，**cwd 就是工作区**。a2 后端的 cwd 是 `$A/w`，日志写在 `$A/w/logs`。
- **已关进程**（都按 PID/PGID，全是 a2 自己起的）：
  - 后端 java：pid 63006（第一次启动，改 OSS 前按 PID 关掉）、pid 66104。
  - 工作台 dev：pgid 69166，含 69166（pnpm）、69196（vite）、69423（esbuild）。
  - H5 dev：pgid 69170，含 69170、69198（pnpm）、69216（vite）、69424（esbuild）。
- **已删容器**：`docker rm -f -v lqg-a2-pg lqg-a2-redis lqg-a2-minio lqg-a2-gotenberg`，匿名卷一起删了。
- **端口**：8171、8172、9171、57433、57380、57900、57901、57010 已全部释放。lqg-dev-*、dev-*、lqg-a3-* 容器全程没动过：lqg-dev-gotenberg 仍是「Up 2 hours」，没有被 D6/D7 的起停波及。
- **已删 / 保留**：删了 `$A/w` 里 node_modules ×2 和 .m2repo 这三个克隆，以及随机口令文件 secrets.env（对应的容器已销毁）。保留 `$A/out/`、`$A/logs/`（后端、前端、mvn 日志）、`$A/PATCHES.md`、`$A/patches.jsonl`、`$A/w`（含基线 git 仓库与已打补丁的副本）。
