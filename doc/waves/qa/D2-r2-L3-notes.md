# D2 r2 · L3（独立邪路）QA 笔记

分支 task/D2 @ `c898bb3` · 只做 phase-plan D2 qa_scope 的 L3 七条 + 第 6/7 条的两小项浅 H5 检查。
**不复跑 L2 的深度 UI 走查**（r2 L2 片已跑完并判 fail，1×S1）。不重复验 L2 那条 S1。
本文件边跑边写；每条 = 自跑命令 + 输出 + 库内不变断言。

## 0 环境 / 反 stale（本片自跑，不引用别人结论）

- 后端：`bash .tmp/run-backend.sh` → 8081，日志 `.tmp/r2l3b-backend.log`，`RuoYi-Vue-Plus启动成功` 落在 2026-09-22 02:03:37（log 文件创建时间），PID = 47148。
  - 起前 `lsof -ti tcp:8081` 为空。
  - 反 stale（自跑三条，不看 `--fresh-module` 的结论）：`find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer ruoyi-admin/target/ruoyi-admin.jar | wc -l` = **0**；jar mtime = `Sep 22 01:19:57 2026`；`lsof -p 47148 | grep -c ruoyi-admin.jar` = **2**（进程确持该 jar）；后端日志时间 02:03:37 **晚于** jar mtime。
  - `ps` 在本沙箱被禁（`/bin/ps: Operation not permitted`），进程启动时间用「启动日志落地时间」代替（见上）。
- reseed：`bash doc/verify/reseed.sh --yes` exit 0（0.59s）。
- token 缓存：`rm -f $TMPDIR/lqg-verify-token-*` 已在 reseed 前清。
- H5：`VITE_MOCK_LOGIN=1 npm_config_store_dir=<ws>/.pnpm-store pnpm dev:h5 --port 9200`，ready in 1020ms，PID 54156（受管后台作业 bash-265）。

### ★ harness 复现：`db.py --quiet` 不打印任何行（本片自测）

```
$ python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_sample ORDER BY id LIMIT 3"
9000001001
9000001002
9000001003
$ python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_sample ORDER BY id LIMIT 3"
（空输出，exit 0）
```
→ 本片所有取值一律**去掉 `--quiet`**。

## 1 · 越权取详情（extC 猜 id / extE 同组未核验）— PASS

命令：`bash doc/verify/api.sh --as <身份> --bizcode GET /mp/ext/sample/9000001001`（脚本 `.tmp/r2l3b-c1.log`）

| 身份 | 输出 |
|---|---|
| extC（同单位异组、已核验） | `404	样本不存在` |
| extE（同组、未核验） | `404	样本不存在` |
| extA（本人）对照 | `200	操作成功` |

响应体逐字（`.tmp/r2l3b-c1-extC.json` / `-extE.json`）：
`{"code":404,"msg":"样本不存在","data":null}`，键集合 = `['code','data','msg']`，`data=null`。
敏感字段正则 `donorName|hospitalNo|patientNo|internalNo|submitterId|sourceUnit|sampleNo|submitSource|verifyStatus|receiveDate|organoid` 命中数 = **0 / 0**。
对照 extA 同一接口的 `data` 键 = `['age','createTime','docs','donorName','editable','embeds','gender','hasPathology','hospitalNo','id','invalidReason','mine','organoidType','remark','sampleKind','sourceUnitName','submitNo','submitterName','tissueType','verifyStatus']` → 证明这是**隔离**不是整段端点不存在。
正向存在性：extC `GET /mp/ext/sample/9000001005` = `200 操作成功`（他确实拿得到自己的）。

## 2 · 外部 PUT 夹带 / 同组别人的 / 已有效的 — PASS

脚本 `doc/waves/regression/D2/L3r2-c2.sh`，日志 `.tmp/r2l3b-c2.log`。H0 1002 整行 md5 = `7f8fc73f0ae2943f3bbeaa64ebcb5f4f`。

| 调用 | 输出 | 库内 |
|---|---|---|
| 2a `--as extA PUT /mp/ext/sample/9000001002`（合法字段 + 夹带 `internalNo=T-L3R2HACK`、`verifyStatus=valid`、`submitSource=internal`、`receiveDate=2026-01-01`） | `200	操作成功` | 1002 = `pending\|(空)\|external\|NULL\|L3R2夹带\|9000000111` |
| 2b `--as extB PUT /mp/ext/sample/9000001001`（同组别人的） | `400	只能修改重提本人提交的样本（同组的样本可以看，但不能改）` | 1001 未变 |
| 2c `--as extA PUT /mp/ext/sample/9000001001`（自己的、已 valid） | `400	样本当前状态不允许外部修改重提（只有待核验、无效的样本可以）` | 1001 未变 |
| 2d `--as extB GET /mp/ext/sample/9000001001` | `200	操作成功` | —（可看） |
| 2e `--as extB PUT /mp/ext/sample/<extA 的新 pending 样本 2102097004980084737>`（状态允许改、但归属不是他） | `400	只能修改重提本人提交的样本（同组的样本可以看，但不能改）` | 该行仍 `remark='L3R2探针C' + pending` → `1`（exit 0） |
| 2f `--as extB GET /mp/ext/sample/2102097004980084737`（同组可看） | `200	操作成功` | — |

库内不变/生效断言（`db.py`，去 `--quiet`）：
- 夹带列不生效 + legit 生效：`… WHERE id=9000001002 AND verify_status='pending' AND coalesce(internal_no,'')='' AND submit_source='external' AND receive_date IS NULL AND remark='L3R2夹带' AND has_pathology='N'` → `1`（exit 0）→ 请求走完了写路径，夹带是被**忽略**而非整体被拒。
- 1001：`… AND verify_status='valid' AND internal_no='T-hli01' AND remark IS DISTINCT FROM 'L3R2夹带'` → `1`（exit 0）。
- H1 1002 = `79c0e0df4d926750182b8f8c77cd2d15`（只多了 2a 的 legit 改动，`update_by=9000000111`）。

★ 记账（S3/debt）：2b/2c 的拒绝码是 **400**，而第 3/5 条同一模块的拒绝走 **500** —— 同一模块客户端错误码不统一。

## 3 · 内部经小程序 PUT — PASS

脚本 `doc/waves/regression/D2/L3r2-c3.sh`，日志 `.tmp/r2l3b-c3.log`。

| 调用 | 输出 | 库内（整行 md5） |
|---|---|---|
| 3a `--as staff PUT /mp/int/sample {"id":9000001002,…}`（待核验） | `500	待核验与无效的样本只能在网页工作台核验或改判，小程序里不能改` | H0 `79c0e0…` == H1 `79c0e0…` |
| 3b 同上 1003（无效） | 同上 500 | H0 `cdd6d0…` == H1 `cdd6d0…` |
| 3c `--as staff PUT /mp/int/sample {"id":9000001001,"remark":"L3R2内部改有效"}` | `200	操作成功` | `1001\|valid\|T-hli01\|L3R2内部改有效\|9000000101` |

库内断言：`remark IN ('L3R2内部改待核验','L3R2内部改无效')` 命中 **0**（被拒 payload 没落盘）；
`SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.update_by WHERE s.id=9000001001 AND u.phonenumber='13800000001' AND s.remark='L3R2内部改有效'` → `1`（exit 0）→ **update_by 有值**。

★ 记账（S3/debt）：3a/3b 拒绝码 = **500**（msg 分支正确）。

## 4 · 角色闸 — PASS

脚本 `doc/waves/regression/D2/L3r2-c4.sh`，日志 `.tmp/r2l3b-c4.log`。

| 调用 | 输出 |
|---|---|
| `--as extA GET /lqg/sample/list` | `403	没有访问权限，请联系管理员授权` |
| `--as extA PUT /lqg/sample/9000001002/verify` | `403	没有访问权限，请联系管理员授权` |
| `--as extA GET /mp/int/sample/list` | `403	没有访问权限，请联系管理员授权` |
| `--as staff GET /mp/ext/sample/list` | `403	没有访问权限，请联系管理员授权` |
| `--as staff POST /mp/ext/organoid` | `403	没有访问权限，请联系管理员授权` |
| 对照 `--as extA GET /mp/ext/sample/list` | `200	查询成功` |
| 对照 `--as staff GET /mp/int/sample/list` | `200	查询成功` |
| 对照 `--as admin GET /lqg/sample/list` | `200	查询成功` |

→ 四处 403 都是**权限分支**（msg 走「无权」），不是端点不存在；对照组证明同路径在正确角色下 200。

## 5 · 核验状态机 — PASS

脚本 `doc/waves/regression/D2/L3r2-c5.sh`，日志 `.tmp/r2l3b-c5.log`。1002 在验前为 `pending`。

| 调用（`--as admin PUT /lqg/sample/9000001002/verify`） | 输出 |
|---|---|
| `{"action":"valid","receiveDate":"2026-09-01"}`（缺内部编号） | `500	判有效必须同时给收样日期与内部编号，缺少：内部编号` |
| `{"action":"valid","internalNo":"T-l3r2x"}`（缺收样日期） | `500	…缺少：收样日期` |
| `{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-hli01"}`（撞号） | `500	内部编号「T-hli01」已存在，请换一个` |
| `{"action":"invalid"}`（缺原因） | `500	判无效必须写原因` |

库内不变：H0 1002 = H1 1002 = `79c0e0df4d926750182b8f8c77cd2d15`（四条之后整行 md5 一字未变）；
`id=9000001001 AND internal_no='T-hli01' AND verify_status='valid'` → `1`；`internal_no='T-hli01'` 全库 → `1`（没被偷走 / 没第二条）。

软删编号重用：1010 = `del_flag=1 | T-del99 | valid`；`PUT …/9000001002/verify {"action":"valid","receiveDate":"2026-09-01","internalNo":"T-del99"}` → `200	操作成功`；
库内 `9000001002 | del_flag=0 | T-del99 | valid | 2026-09-01 | verify_by=9000000100`，1010 仍 `del_flag=1 | T-del99`；
`internal_no='T-del99' AND del_flag='0'` → `1`（部分唯一索引 `uk_sample_internal_no WHERE del_flag='0'` 成立；全库 2 行含软删行，符合设计）。

★ 记账（S3/debt）：四条拒绝码 = **500**（msg 分支正确）。

## 6 · 空数据：新外部用户（0 样本、未填组别）— PASS

接口侧脚本 `doc/waves/regression/D2/L3r2-c67.sh`，日志 `.tmp/r2l3b-c67.log`；身份 `--as phone:r2new1:13800000097`（首登自动建号，api.sh 自身登录路径成功）。

| 调用 | 输出 |
|---|---|
| `GET /mp/me` | `200	操作成功`；`identity":"external"`，`ext:{"unitId":null,"unitName":null,"groupId":null,"groupName":null,"bindStatus":"unbound"}` |
| `GET /mp/ext/sample/list?pageSize=20`（历史编辑记录数据源） | `200	查询成功`，`total=0, rows=[]` |
| `GET /mp/ext/sample/list?sampleKind=organoid&pageSize=20` | `200	查询成功` |
| `GET /mp/ext/sample/list?sampleKind=embed&pageSize=20` | `200	查询成功` |
| `GET /mp/ext/units` | `200	操作成功` |

库内 corroboration：该号样本数 = `0`；`t_lqg_ext_profile` 一行 = `unbound | - | - | - | -`（unit/group/自填全空）。
端侧（首页 + 历史编辑记录不报错）见第 7 节的浅 H5 输出。

（记账，非缺陷：我另发的裸 `POST /auth/login '{"grantType":"xcx"}'` 回 `500 Auth clientid cannot be blank` —— 那是我手写 body 缺 clientId，api.sh 自身的登录已成功建号，这不是产品路径。）

## 7 · 端侧渲染 + 外部类器官夹带 — PASS

### 7a 浅 H5（`doc/waves/regression/D2/L3r2-mp-shallow.mjs` = r1 独立脚本的 r2 副本；1e8ea8c 未改过该文件，本次执行输出为准），日志 `.tmp/r2l3b-h5shallow.log`：**17/17 PASS**，pageerror 0 / console.error 0

```
PASS  首页宫格数 = 3（外部人员 · 新号（未绑定）） — 实际 3
PASS  ① newbie1 首页不报错（无失败/重试文案）
PASS  ① newbie1 首页仍未绑定提示可见（unbound）
PASS  ① newbie1 首页无 pageerror
PASS  ① newbie1 历史编辑记录不报错（有数据源且走空态，不是 ErrorState） — "你填过的记录会出现在这里"
PASS  首页宫格数 = 3（外部人员 · 王医生（已核验）） — 实际 3
PASS  ② 外部「我的」正文不含「内部管理」
PASS  ② 外部「我的」有「历史编辑记录」与「单位与组别」
PASS  首页宫格数 = 4（内部人员 · 李工） — 实际 4
PASS  ② 内部「我的」正文含「内部管理」（样本/类器官/石蜡包埋/-80 冻存四入口）
PASS  ② 内部「我的」不含「单位与组别」区块
PASS  ③ identity=null：首页宫格 0 格 — tiles=0；正文只有「没能确认你的身份，请重新登录后再试 / 去登录」
PASS  ③ identity=null：首页无 pageerror
PASS  ③ identity=null：「我的」不渲染历史编辑记录 / 内部管理 / 单位与组别
PASS  ③ identity="INTERNAL"（契约外大小写）：首页宫格 0 格（不默认内部）
```
（身份缺失用 Playwright 拦截 `**/mp/me` 把 `identity` 置 `null` / `"INTERNAL"`；登录页先硬 reload 复位再点 `.login__box` 勾协议，见脚本 `resetToLogin`/`mockLogin`。）

### 7b 接口侧（脚本 `L3r2-c67.sh` 后半）：extC 外部类器官夹带

`--as extC POST /mp/ext/organoid`，body = legit（`sourceUnitName/organoidType/remark`）+ 夹带 `internalNo=T-L3R2ORG`、`receiveDate=2026-01-02`、`verifyStatus=valid`、`submitSource=internal`、`isFixed=Y`
→ `200	操作成功`；库内行数 10 → 11（确实建了行，夹带是被忽略而非整体被拒）。

库内新行：`2102096826747330561 | organoid | external | pending | (internal_no 空) | (receive_date NULL) | (is_fixed NULL) | L3R2类器官备注`
- `… WHERE organoid_type='L3R2类器官探针' AND sample_kind='organoid' AND submit_source='external' AND verify_status='pending' AND coalesce(internal_no,'')='' AND receive_date IS NULL AND is_fixed IS NULL AND remark='L3R2类器官备注'` → `1`（exit 0）
- `internal_no='T-L3R2ORG'` 全库 → `0`（exit 0）

## 8 · 复现的记账项（r1 已记账、r2 rework 未涉及，本片独立复现仍在）

### S2 · 外部写端点非法/缺字段 body → 500 响应体回吐完整 SQL 与整行 DB 明细

```
$ bash doc/verify/api.sh --as extA POST /mp/ext/organoid '{"organoidType":"L3R2探针B","remark":"L3R2探针B"}'
{"code":500,"msg":"\n### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: null value in column \"source_unit_name\" of relation \"t_lqg_sample\" violates not-null constraint
  Detail: Failing row contains (2102096986307043330, SJ00000055, organoid, external, 9000000111, pending, null, …
### The error may exist in org/dromara/lqg/sample/mapper/SampleMapper.java (best guess)
### The error may involve …SampleMapper.insert-Inline …"}

$ bash doc/verify/api.sh --as extA PUT /mp/ext/sample/2102097004980084737 '{"…","hasPathology":"yes","…"}'   # 字典 lqg_yes_no 只有 Y/N
{"code":500,"msg":"\n### Error updating database.  Cause: … ERROR: value too long for type character(1) … ### SQL: UPDATE t_lqg_sample  SET update_by=?,…,has_pathology=?   WHERE del_flag='0'     AND (id = ?) …"}
```
库内：探针行 `has_pathology` 未变（`count(*) WHERE id=2102097004980084737 AND has_pathology='N'` → `1`）、`remark IN ('L3R2探针A','L3R2探针B')` → `0`（回滚成立）。
范围：任何已登录外部账号可构造（两个外部写端点都可复现）；小程序表单只送 `Y/N` 且全字段 → 主流程走不到。

### S3 · 同一模块客户端错误业务码不统一（400 / 500 混用）

`PUT /mp/ext/sample/{id}` 归属拒绝、状态拒绝 → **400**；`/mp/int/sample` 待核验拒绝、`PUT /lqg/sample/{id}/verify` 缺编号/缺日期/撞号/缺原因四条 → **500**。msg 分支都对，仅码不规范。

### harness · `db.py --quiet` 不打印任何行（本片自测复现，见 §0）

### 记账：README「已知的坑 #6」在本片自己的 echo 里中了一次
`echo "## 2e extB PUT extA 的 pending 样本 $NEW（状态允许改…）"` → `$NEW（` 的多字节首字节被吃进变量名，输出里 `$NEW` 位置空 + 乱码一字节。
**只是我自己的显示行**（真正的命令用了 `"$NEW"` 引号，请求与断言不受影响），与 README 第 6 条记载一致，不新增 issue。
