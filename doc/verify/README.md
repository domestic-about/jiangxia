# 验收执行器 + 确定性测试数据

全部 ticket 的 accept 只许用这里的几个执行器。**别在 accept 里自造鉴权、连库、造数据的样板**——那是假绿的温床。
accept 的 `run` 一律按 **bash** 语义写（别依赖变量的分词展开，zsh 不拆），cwd = 项目根。

| 执行器 | 干什么 | 退出码 |
|---|---|---|
| `db.py --sql "…" --eq / --rows / --empty / --nonempty / --col-set` | PostgreSQL **只读**断言 | 0 成立 · 1 **不成立** · 2 用法 / 连接 / SQL 错 |
| `db.py --quiet --sql "…"` | **只取值**（给 `X="$(…)"` 插值用）：每行打印**第一列**，无表头、无 `\|` 拼接、不截断。**别拿它当断言** | 同 `--sql`（断言标志一个都不带时恒 0） |
| `ddl_vs_ssot.py --table t… --require-public …` | 库里的真实表 vs `authority/field-ssot.yaml` 逐列对账（含部分唯一索引） | 同上 |
| `api.sh [--as 身份] [--bizcode] [--out 文件] [--fresh-module ruoyi-lqg] METHOD PATH [JSON]` | 登录 + 调接口 + 吐响应体 | 0 拿到响应（**不代表业务成功**）· 2 用法 / 登录 / stale |
| `xlsx_header.py --file … --template … [--insert "列@锚点列"] [--extra …] [--rows N] [--find … --expect …]` | 导出的 Excel vs 甲方模板原件逐字对表头（`--insert` = 甲方后来要求插在模板某列后面的列，如类器官收样记录 `--insert "代数@类器官类型"`，CR-20260924-10） | 0 / 1 / 2 |
| `xlsx_header.py --print-header --template …` | 只打印模板第 1 行（一列一行），给 fixture 里抄的表头与原件做 `diff` | 0 / 2 |
| `reseed.sh --yes` | 清空全部 `t_lqg_*` + seed 账号 → 逐段灌 `seed/*.sql`（某段依赖的表还没建就跳过：表建到哪，seed 灌到哪） | 库名不含 dev / test 直接拒绝 |
| `gen_seed.py` | 重新生成 `seed/*.sql`（列名对着 SSOT 校验；加密列预算密文） | — |

1 和 2 必须分开：否则「表还没建」和「实现做错了」长得一模一样。

## 起验证环境

```bash
cp doc/verify/verify.env.example doc/verify/verify.env      # 填本机开发库与两个 clientId；verify.env 不进 git
bash doc/verify/reseed.sh --yes                              # 任何会改数据的 accept 跑之前都先 reseed
# 后端必须：dev 或 test 配置 + --api-decrypt.enabled=false（/auth/login 带 @ApiEncrypt，不关则裸 curl 一律失败）
#           mybatis-encryptor: enable=true, algorithm=AES, encode=BASE64, password=LqgTestAesKey#01（seed 的密文按它算的）
#           lqg.auth.mock-login=true（只在 dev / test；ADR-0008）
```

## 隔离环境重放（2026-09-24 起，CR-20260924-11）

accept 里凡是要连后端、库、转换服务或写临时文件的地方，一律从下面这几个环境变量读。**缺省值就是 Kevin 本机的开发环境**（`code/deploy/dev/docker-compose.yml` 起的 `lqg-dev-*` 容器），
所以 Kevin 本机的 `gate.sh` / `qa-up.sh` 不用设任何东西就照常跑；**在别的机器、别的容器组（并行的修复组、回归组）里重放 accept，下表「必须设」的几项一项都不能漏**——
漏了就会连到、甚至停掉 Kevin 正在用的环境（2026-09-24 有一个隔离组照原样重放 SYS-HOME-001，停掉过 Kevin 的 lqg-dev-gotenberg）。

| 变量 | 缺省（Kevin 本机） | 谁在读 | 隔离环境 |
|---|---|---|---|
| `LQG_VERIFY_ENV_FILE` | `doc/verify/verify.env`（后端 8081、库 5433） | `api.sh`、`db.py`、`reseed.sh` | **必须设**，指向自己那套后端与库 |
| `LQG_GOTENBERG_CONTAINER` | `lqg-dev-gotenberg` | DOC-PDF-001 accept 2、SYS-HOME-001 accept 1：`docker stop / start` 它来造一次真实的转换失败 | **必须设**，填自己的转换服务容器名 |
| `LQG_GOTENBERG_URL` | `http://127.0.0.1:3010` | 同上两条等 `…/health` 恢复；后端 `application-dev.yml` 与 `mutation-assert.sh` 自起 JVM 时也读同名变量 | **必须设**，填自己的转换服务地址 |
| `TMPDIR` | 系统临时目录（没有就 `/tmp`） | accept 的临时文件一律写 `"${TMPDIR:-/tmp}"/lqg-*`；`api.sh` 的 token 缓存 `${TMPDIR:-/tmp}/lqg-verify-token-*` | **必须设**成自己的目录，免得与本机验收同名文件互相覆盖 |
| `LQG_ACCEPT_BACKEND_PORT` / `LQG_ACCEPT_WEB_PORT` / `LQG_ACCEPT_MP_PORT` / `LQG_ACCEPT_OSS_BASE` | 8094 / 8093 / 9204 / — | `doc/waves/regression/D7/mutation-assert.sh`（SYS-ACCEPT-001，以及 SYS-EXPORT-001、SYS-HOME-001、DOC-MP-002、DOC-PUBLISH-001 里调它的那几段） | 要跑这几段就设；后端不在时还要显式给全 `LQG_DB_* / LQG_REDIS_* / LQG_GOTENBERG_URL`，否则它 exit 2 |

- 写新 accept 时同样照这个规矩：不写死 `lqg-dev-*` 容器名、不写死 5433 / 6380 / 9002 / 3010 / 8081 这些端口、不 `docker compose -f code/deploy/dev/…` 起停服务、临时文件放 `"${TMPDIR:-/tmp}"` 下；缺省值写成 `${变量:-Kevin 本机的值}`，本机不用改就能跑。
- 连公网测试机、生产机的段（SYS-STAGING-001、SYS-PROD-001，经 `verify.test.env` / `verify.prod.env`）不受影响，照原样只连远程。
- 早先有些票（不在上面几张里）仍把临时文件写在 `/tmp/lqg-*`：在隔离环境重放前先确认 `TMPDIR` 已设、并留意同名文件；这些票改到哪张再顺手改成 `"${TMPDIR:-/tmp}"`。

## 本栈已知的坑（写 accept 前过一遍）

| # | 坑 | 后果 | 正确做法 |
|---|---|---|---|
| 1 | 若依把 404 / 403 / 500 全包进响应体，**HTTP 状态码几乎恒为 200** | 拿 HTTP 码判成败的断言恒绿 | `--bizcode` 或 `jq -e` 断 body 的 `.code` |
| 2 | `.code != 200` 是黑名单式断言 | 端点不存在（404）也满足，实现什么都没做照样绿 | 断**正码白名单**（`.code==400`）并断 `.msg` 含目标分支的关键词，再断**副作用没发生**（库里行数不变） |
| 3 | 改了 `ruoyi-lqg` 没重新打包 / 打了包没重启 | 断言打在旧 jar 上 | 每条 API accept 的第一次调用带 `--fresh-module ruoyi-lqg` |
| 4 | POST body 的时间写成 ISO 的 `T` 格式 | 400「could not be parsed」，实现对了也永远红 | `yyyy-MM-dd HH:mm:ss` |
| 5 | 小 id 序列化成数字、大 id 序列化成字符串 | `== "9000001001"` 比较忽真忽假 | `jq` 里一律 `tostring` |
| 6 | shell 里 `$VAR` 紧跟全角标点 | 多字节首字节被吃进变量名 → `unbound variable`，脚本崩了输出为空，黑名单式断言反而判绿 | 一律写 `${VAR}` |
| 7 | 超管天然全权限 | 权限缺失类问题在它身上测不出来 | 权限断言用 `--as extA / extC / staff`，不用 `admin` |
| 8 | 每条 `--empty` 差异集前面没有正向存在性断言 | 表没建 / 零行时恒绿 | 先断一条「样本里确实有该被查到的东西」 |
| 9 | 期望值以负号开头：`db.py --eq "-2\|0"` | argparse 把 `-2|0` 当成一个选项，exit 2「expected one argument」——实现对了也永远红（2026-09-17 在冻存两张 ticket 里查出） | 写成 `--eq="-2\|0"`，或把 SQL 拼接顺序调成不以负数开头 |
| 10 | accept 里用 `HOME` 当变量名存接口返回 | 覆盖了家目录，后面的 `python3` 找不到装在用户目录里的包 | 换个名字，比如 `H` |

## seed 速查（期望值都从这里推）

**身份**（`api.sh --as`）

| 代号 | 手机号 | 身份 | 单位 · 组别 · 核验 |
|---|---|---|---|
| `admin` | 13800000000 | 工作台管理员 `lqgadmin` / `admin123` | — |
| `staff` | 13800000001 | 内部人员 李工 | — |
| `extA` | 13800000011 | 外部 王医生 | A 医院 · 肝胆外科组 · 已核验 |
| `extB` | 13800000012 | 外部 陈医生 | A 医院 · 肝胆外科组 · 已核验（与 A **同组**）。病灶：这个外部账号**带着可用口令** `admin123`，也不许登上工作台 |
| `extC` | 13800000013 | 外部 赵医生 | A 医院 · 消化内科组 · 已核验（**同单位异组**） |
| `extD` | 13800000014 | 外部 孙老师 | B 大学 · 类器官课题组 · 已核验 |
| `extE` | 13800000015 | 外部 周医生 | A 医院 · 肝胆外科组 · **待核验**（病灶：同组但不得互看） |
| `extF` | 13800000016 | 外部 吴同学 | 自填「C 研究所 / 肿瘤组」· 待核验 |

**样本**（id = 9000001000 + 序号）

| # | 送检单号 | 提交人 | 状态 | 内部编号 | 备注 |
|---|---|---|---|---|---|
| 1001 | SJ90000001 | extA | valid | T-hli01 | 供体「测试供体甲」住院号 ZY0000001；2 个石蜡块、2 批冻存、3 份文档已完成 |
| 1002 | SJ90000002 | extA | pending | — | extA 在它上面交了一条待核验的石蜡包埋送样 2006（病灶：样本没核验，送样判有效必须被拒） |
| 1003 | SJ90000003 | extA | invalid | — | 原因「信息不全：缺住院号」 |
| 1004 | SJ90000004 | extB | valid | T-hli02 | 质控表已完成、类器官质控表**草稿**（病灶） |
| 1005 | SJ90000005 | extC | valid | T-hga03 | 只有质控表草稿 |
| 1006 | SJ90000006 | extD | valid | T-hco04 | 评分已完成 18 分 |
| 1007 | SJ90000007 | extE | pending | — | |
| 1008 | SJ90000008 | staff | valid（内部） | T-hli05 | 名下唯一的石蜡块是**软删**的（病灶） |
| 1009 | SJ90000009 | staff | valid（内部 · 类器官） | T-oco01 | 唯一的类器官样本；代数 `passage = P3`（CR-20260924-10） |
| 1010 | SJ90000010 | extA | valid · **del_flag=1** | T-del99 | 病灶：任何接口、任何导出都不得出现 |

**可见集合**：extA = extB = {1001, 1002, 1003, 1004}｜extC = {1005}｜extD = {1006}｜extE = {1007}｜extF = ∅

**石蜡包埋**：T-E01-1（2001，1001，已切片，HE + IHC，Ki67 强 / CK19 阴）· T-E01-2（2002，1001，未切片）· T-E02-1（2003，1004，已切片，无染色）· T-E04-1（2004，1006，其他：Masson）· T-E05-X（2005，1008，**软删**）
· 2006（1002，**extA 提交的送样、待核验、没有石蜡块编号**；CR-20260917-05）。2001–2005 是内部录入（有效），2001、2003、2005 由李工建，其余由管理员建
→ 切片染色提示（只数有效的块）：1001 = 2 块 / 已切片 / HE、IHC；1004 = 1 块 / 已切片 / 无；1008 = 0 块；1002 = 0 块（待核验送样不算）
→ 外部看得到的石蜡包埋记录：extA = extB = {2001, 2002, 2003, 2006}｜extD = {2004}｜extC = extE = extF = ∅；「只看我提交的」：extA = {2006}，extB = ∅

**冻存**（id = 9000003000 + 序号；日期相对当天）

| # | 样本 | 冻存于 | 初始 | 流水 | 剩余 | 超期 |
|---|---|---|---|---|---|---|
| 3001 | 1001 | 20 天前 · -80 | 8 | −2 | **6** | ✅ 已超 6 天 |
| 3002 | 1009 | 5 天前 · -80 | 4 | （一条软删的 −1，不计） | **4** | — |
| 3003 | 1008 | 40 天前 · 已转液氮 | 6 | −1、+2、−3 | **4** | — |
| 3004 | 1001 | 14 天前 · -80 | 3 | −3 | **0** | —（取空了） |
| 3005 | 1004 | 14 天前 · -80 | 2 | — | **2** | ✅ 已超 0 天（**恰好第 14 天**） |
| 3006 | 1008 | 13 天前 · -80 | 5 | — | **5** | —（第 13 天） |
| 3007 | 1009 | 60 天前 · 直接液氮 | 5 | — | **5** | — |
| 3008 | 1008 | 50 天前 · **软删** | 9 | — | — | 不得出现 |

→ 超期集合 = {3001, 3005}；页签计数 `tabCounts` = 全部 7 / 超期 2 / 液氮 2（3003、3007）/ 已取空 1（3004；`emptied` 键 CR-20260924-10 追加）

**质控文档**：1001 三份已完成（评分 20 + 10 + 25 + 30 = **85**）｜1004 质控表已完成 + 类器官质控表草稿｜1005 质控表草稿｜1006 评分已完成（8 + 0 + 0 + 10 = **18**）
→ extA 在文档页看得到：1001 的三份 + 1004 的质控表；**看不到** 1004 的类器官质控表（草稿）

**工作台首页待办期望**：待核验样本 2（1002、1007）= 组织 `pendingTissue` 2 + 类器官 `pendingOrganoid` 0｜待核验石蜡包埋送样 1（2006）｜-80 超期 2｜待核验外部用户 2（extE、extF）｜渲染失败 0
**小程序首页**：外部没有任何数字；内部首页「待处理」= 待核验样本 2、待核验石蜡包埋送样 1、-80 超期 2（与工作台首页同一口径，读 `pendingSamples / pendingEmbeds / cryoOverdue`；CR-20260924-10 推翻 CR-20260917-05 的「首页没有数字」）。
**历史编辑记录期望**（内部 = 中心全部内部人员的记录，「只看我提交的」打开后才按 `create_by` / `update_by` 收窄到本人，CR-20260918-07；外部 = 可见集合，「只看我提交的」再按提交人收窄）：
staff 的样本 {1008, 1009}｜石蜡包埋 {2001, 2003}｜冻存 {3001, 3003}（软删的 2005、3008 不算）；extA「只看我提交的」样本 {1001, 1002, 1003}；extB「只看我提交的」样本 {1004}

**创建时间**：seed 行的 `create_time` 一律在过去（样本按各自的故事给，其余表统一 30 天前），只有 1002、1007、1010 三条样本与石蜡包埋送样 2006 是「今天零点过几秒」。
所以 accept 里用「`create_time > now() - 5 分钟`」去找本次断言刚建出来的那一行是安全的——reseed 不会往这个时间窗里放任何东西。

## fixtures

- `fixtures/ocr-cases.json`：识别原文 → 必须解析出 / 必须不出现的字段（OCR-IMPL-001）
- `fixtures/prefill-cases.json`：预填合并规则的输入与期望（OCR-MP-001）
- `fixtures/ledger-columns-cases.json`：小程序表格页四张表的列（冻结列 + 其余列，表头由脚本从甲方 xlsx 原件读出；SAMPLE-MP-002）
- `fixtures/home-entries-cases.json`：首页入口（内部四张、外部三张没有冻存）、`targetCases` 点进去去哪（内外部一律是该表填写页；外部点冻存、未知身份 = 没有目标）、`meCases`「我的」页按身份出现的区块（所有人历史编辑记录；外部单位与组别；内部「内部管理」）——SYS-MP-001，CR-20260917-05
- `fixtures/sample-form-cases.json`：样本记录信息表填写页布局，含入口模式 `mode`（new 首页 / edit 历史编辑记录 / view 内部管理，缺失按只读；SAMPLE-MP-001）
- `fixtures/organoid-form-cases.json`：类器官收样填写页布局（内部八项、外部四项，`inserted` 记「代数」插在「类器官类型」后；SAMPLE-MP-002，CR-20260924-10）
- `fixtures/embed-form-cases.json`：石蜡包埋送样填写页布局（内部全字段、外部选择样本加两项、外部有效后显示包埋卡片；EMBED-MP-001）
- `fixtures/home-todo-cases.json`：内部首页「待处理」的显示规则（只给内部、三项顺序、0 弱化、全 0 一行）与点击去向（SYS-MP-001，CR-20260924-10）
- `fixtures/verify-form-cases.json`：小程序核验页的必填与请求体形状（送检段整段 / 不带、石蜡包埋 `fill` 只带改过的键；SAMPLE-MP-002，CR-20260924-10）
- `fixtures/cryo-take-cases.json`：取走让剩余变 0 前确认的规则，小程序与工作台两端 spec 共用（CRYO-MP-001，CR-20260924-10）
