# D2 r3 · L3（独立邪路）QA 笔记

分支 task/D2 @ `f1dcfdd` · 只做 phase-plan D2 qa_scope 的 L3 七条 + 第 6/7 条的两小项浅端侧检查。
**不复跑 L2 的深度 UI 走查**（r3 L2 片已跑完，fail，与 L1 同一条 S1）。**不重复验那条 S1**。
本文件边跑边写；每条 = 自跑命令 + 输出 + 库内不变断言。
**本片所有探针脚本自写（`doc/waves/regression/D2/L3r3-*`）**，不复用实现方返工动过的 r1/r2 脚本当证据。

## 0 环境 / 反 stale（本片自跑）

- 起前 `lsof -ti tcp:8081` 空 → `bash .tmp/run-backend.sh` → 8081，日志 `.tmp/qa-r3/backend.log`。
- 反 stale 三条自跑：`find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer ruoyi-admin/target/ruoyi-admin.jar | wc -l` = **0**；
  jar mtime = `Sep 22 06:18`；`lsof -p 32553 | grep -c ruoyi-admin.jar` = **2**（进程确持该 jar）；启动日志时间 `06:38:39` **晚于** jar mtime（`ps` 沙箱禁，用启动日志落地时间）。PID = **32553**。
- reseed：`bash doc/verify/reseed.sh --yes` exit 0（0.59s），token 缓存已清。

### harness 复验：`db.py --quiet` 已修好（本片自测）

```
$ python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_sample ORDER BY id LIMIT 3"
9000001001 / 9000001002 / 9000001003
$ python3 doc/verify/db.py --quiet --sql "同一句"
9000001001 / 9000001002 / 9000001003   ← 只打印值，无表头
```
→ r2 记的「`--quiet` 恒空」harness 问题**已由 `aee9fa5` 修掉**，本片不再记 issue；取值句式用 `--quiet`。

## 1 · 越权取详情 — PASS

脚本 `doc/waves/regression/D2/L3r3-c1.sh`（本片自写），日志 `.tmp/qa-r3/c1.log`。

| 调用 | 输出 |
|---|---|
| `--as extC --bizcode GET /mp/ext/sample/9000001001` | `404	样本不存在` |
| `--as extE --bizcode GET /mp/ext/sample/9000001001` | `404	样本不存在` |

两条响应体**逐字**都是 `{"code":404,"msg":"样本不存在","data":null}`；顶层键 = `code,data,msg`；`data=null`；
敏感/样本字段名正则（`donorName|hospitalNo|patientNo|internalNo|submitterId|submitterName|sourceUnit|sampleNo|submitNo|submitSource|verifyStatus|receiveDate|donor_name|internal_no|verify_by`）命中数 = **0 / 0**。
→ 越权取详情**响应体不含任何样本字段**。

对照（证明 404 是隔离，不是端点不存在）：
- `--as extA GET /mp/ext/sample/9000001001` → `200`，`data` 键 = `age,createTime,docs,donorName,editable,embeds,gender,hasPathology,hospitalNo,id,invalidReason,mine,organoidType,remark,sampleKind,sourceUnitName,submitNo,submitterName,tissueType,verifyStatus`。
- `--as extC GET /mp/ext/sample/9000001005` → `200 操作成功`（拿得到自己的）。
- `--as extB GET /mp/ext/sample/9000001001` → `200 操作成功`（同组可看）。

★ **`invalidReason` 新键复核**（r3 返工新增）：`--as extA GET /mp/ext/sample/9000001003`（自己的 invalid）→
`data` = `{"id":9000001003,"verifyStatus":"invalid","invalidReason":"信息不全：缺住院号","submitNo":"SJ90000003",…}`；
`data` 键集合与 1001 完全相同（含 `invalidReason`）。逐路径扫 `internalNo|verifyBy|submitSource|receiveDate|donorName|hospitalNo|patientNo|sourceUnit|submitterId`（不区分大小写）命中 **2** 条，
逐条核对 = `data.sourceUnitName`（"A 医院"）与 `data.donorName`（"测试供体丙"）——**都是外部本该看到的字段**（本人记录），
**没有** `internalNo` / `verifyBy` / `submitSource` / `receiveDate`。即：新键只多带出无效原因，**没把内部专用字段带出来**。

## 2 · 外部 PUT 夹带 / 同组可看不可改 / 已有效改不了 + 外部筛选隔离 — PASS

脚本 `doc/waves/regression/D2/L3r3-c2.sh`（本片自写），日志 `.tmp/qa-r3/c2.log`。H0 1002 整行 md5 = `7f8fc73f0ae2943f3bbeaa64ebcb5f4f`。
夹带 body = legit 全字段 + `internalNo=T-L3R3HACK` / `verifyStatus=valid` / `submitSource=internal` / `receiveDate=2026-01-01`。

| 调用 | 输出 | 库内断言 |
|---|---|---|
| 2a `--as extA PUT /mp/ext/sample/9000001002` | `200	操作成功` | `1002 = pending \| internal_no=<空> \| external \| receive_date=<空> \| L3R3夹带 \| N`；`…AND verify_status='pending' AND coalesce(internal_no,'')='' AND submit_source='external' AND receive_date IS NULL AND remark='L3R3夹带' AND has_pathology='N'` → `1` exit 0 |
| 2b `--as extB PUT /mp/ext/sample/9000001001`（同组别人的） | `400	只能修改重提本人提交的样本（同组的样本可以看，但不能改）` | 1001 仍 `valid \| T-hli01 \| remark≠L3R3夹带` → `1` exit 0 |
| 2c `--as extA PUT /mp/ext/sample/9000001001`（自己的、已 valid） | `400	样本当前状态不允许外部修改重提（只有待核验、无效的样本可以）` | 1001 同上 → `1` exit 0 |
| 2d `--as extB GET /mp/ext/sample/9000001001` | `200	操作成功` | —（同组可看） |
| 2a' `--as extA PUT /mp/ext/sample/9000001003`（自己的、invalid，**正向对照**） | `200	操作成功` | `1003 = pending \| L3R3夹带`；`…AND verify_status='pending' AND coalesce(internal_no,'')='' AND submit_source='external' AND receive_date IS NULL AND remark='L3R3夹带'` → `1` exit 0（invalid 改后重提按契约回到 pending） |
| 2e `--as extB PUT /mp/ext/sample/<extA 新建 pending>` | `400` 同 2b 归属 msg | H0 == H1 md5 = `5a55a6285f5c5dbcd16752dc52d9a1f3`（一字未变）；随后 `--as extB GET` 同一条 → `200`（同组可看） |

全库副作用自查：`internal_no='T-L3R3HACK'` 全库 **0** 行；`--empty` 断 `internal_no='T-L3R3HACK' OR receive_date='2026-01-01'` → exit 0（夹带值一个都没落库）。
→ 夹带四键是**被忽略**（请求走完了写路径、legit 字段生效），不是整条请求被拒。

★ **外部列表筛选隔离**（针对 `SampleQueryService`/`SampleQueryBo` 返工的关键词与来源单位改动）：读源码 `ExtSampleController.list(ExtSampleQueryBo)` → `ExtSampleQueryBo` 只有 `sampleKind / verifyStatus / onlyMine` 三个字段（无 `keyword` / `sourceUnitId` / `sort`），与内部 BO 是两条路；运行时逐条验：

| `--as extA GET /mp/ext/sample/list?pageSize=20&…` | total | ids |
|---|---|---|
| （无）base | 5 | 1001,1002,1003,1004,新建 |
| `keyword=T-hli01`（内部编号，内部口才认） | 5 | 同 base（**被忽略**） |
| `sourceUnitId=1` | 5 | 同 base（**被忽略**） |
| `sort=recent` | 5 | 同 base（外部口**不受内部 sort 支影响**） |
| `verifyStatus=pending` | 3 | 1002,1003,新建 |
| `onlyMine=true` | 4 | 1001,1002,1003,新建 |
| `sampleKind=organoid` | 0 | （空） |
| `--as extC keyword=T-hli01` | 1 | 1005（**没因别人的内部编号筛出任何东西**） |

→ 外部口的筛选行为**未被**内部那套 `keyword`/`sourceUnitId` 改动影响：内部专用参数被静默忽略，有效参数各自收窄正确。

★ 记账（S3/debt，r1 已记、本片独立复现仍在）：同一模块客户端错误业务码 400 / 500 混用（2b/2c = 400；第 3/5 条 = 500）。

## 3 · 内部经小程序 PUT — PASS

脚本 `doc/waves/regression/D2/L3r3-c3.sh`（本片自写），日志 `.tmp/qa-r3/c3.log`。（跑前已 reseed 复原 seed：1002 pending / 1003 invalid。）

| 调用 | 输出 | 库内（整行 md5） |
|---|---|---|
| 3a `--as staff PUT /mp/int/sample {"id":9000001002,…}`（待核验） | `500	待核验与无效的样本只能在网页工作台核验或改判，小程序里不能改` | H0 `7f8fc73f…` == H1 `7f8fc73f…` |
| 3b 同上 `{"id":9000001003,…}`（无效） | 同上 500 同 msg | H0 `cdd6d089…` == H1 `cdd6d089…` |
| 3c `--as staff PUT /mp/int/sample {"id":9000001001,…,"remark":"L3R3内部改有效"}` | `200	操作成功` | `9000001001 \| valid \| L3R3内部改有效 \| update_by=9000000101` |

库内不变断言：`remark IN ('L3R3内部改待核验','L3R3内部改无效')` → **0**（被拒 payload 未落盘）；`1002/1003` 分别仍 `pending/…`、`invalid/…` 且 `internal_no` 空、`submit_source='external'`。
`update_by 有值`：`SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.update_by WHERE s.id=9000001001 AND u.phonenumber='13800000001' AND s.remark='L3R3内部改有效'` → **1**（exit 0），`s.update_by=9000000101`。

★ 记账（S3/doc-drift）：`MpSampleController` 的 javadoc 写「PUT /mp/int/sample 修改（valid 谁录的都能改；**待核验 / 无效 → 400**）」，实际返回 **500**；同模块 `PUT /mp/ext/sample/{id}` 的拒绝又真是 400 → 码不统一之外，注释与实现也不一致。

## 4 · 角色闸 — PASS（正反都断）

脚本 `doc/waves/regression/D2/L3r3-c4.sh`（本片自写），日志 `.tmp/qa-r3/c4.log`。13 条全 `OK`，403 分支 msg 逐条 == `没有访问权限，请联系管理员授权`。

| 方向 | 调用 | 输出 |
|---|---|---|
| 外部→`/lqg/**` | `--as extA GET /lqg/sample/list` | `403	没有访问权限，请联系管理员授权` |
| | `--as extA PUT /lqg/sample/9000001002/verify` | 同上 403 |
| 外部→`/mp/int/**` | `--as extA GET /mp/int/sample/list` | 同上 403 |
| | `--as extA GET /mp/int/sample/9000001001` | 同上 403 |
| | `--as extA PUT /mp/int/sample` | 同上 403 |
| 内部→`/mp/ext/**` | `--as staff GET /mp/ext/sample/list` | 同上 403 |
| | `--as staff GET /mp/ext/sample/9000001001` | 同上 403 |
| | `--as staff POST /mp/ext/organoid` | 同上 403 |
| | `--as admin GET /mp/ext/sample/list`（超管也 403） | 同上 403 |
| 对照（正确角色 200） | `--as extA GET /mp/ext/sample/list` / `--as staff GET /mp/int/sample/list` / `--as admin GET /lqg/sample/list` | 三条均 `200	查询成功` |

库内副作用自查：越权那条 `PUT /lqg/sample/9000001002/verify` 的 payload `invalid_reason='L3R3越权'` → 全库 **0** 行（越权没改成库）。

## 5 · 核验状态机 — PASS

脚本 `doc/waves/regression/D2/L3r3-c5.sh`（本片自写），日志 `.tmp/qa-r3/c5.log`。验前 1002 = `pending`，H0 1002 md5 `7f8fc73f0ae2943f3bbeaa64ebcb5f4f`。

| 调用（`--as admin PUT /lqg/sample/9000001002/verify`） | 输出 |
|---|---|
| 5a `{"action":"valid","receiveDate":"2026-09-01"}`（缺内部编号） | `500	判有效必须同时给收样日期与内部编号，缺少：内部编号` |
| 5b `{"action":"valid","internalNo":"T-l3r3x"}`（缺收样日期） | `500	…缺少：收样日期` |
| 5c `{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-hli01"}`（撞号） | `500	内部编号「T-hli01」已存在，请换一个` |
| 5d `{"action":"invalid"}`（缺原因） | `500	判无效必须写原因` |

**库内不变**：H1 1002 == H0 1002（整行 md5 一字未变）；`1002 = pending \| internal_no=<空> \| receive_date=<空> \| invalid_reason=<空>`；
`1001 仍 valid/T-hli01` → 1（exit 0）；全库 `internal_no='T-hli01'` → **1**（没被偷走、也没第二条）。1001 的 H0==H1。

**软删编号可重用**：`1010 = del_flag=1 \| valid \| T-del99`；
`PUT …/9000001002/verify {"action":"valid","receiveDate":"2026-09-01","internalNo":"T-del99"}` → `200	操作成功`；
库内 `1002 = del_flag=0 \| valid \| T-del99 \| 2026-09-01 \| verify_by=9000000100`，`1010` 原行仍在（`del_flag=1 \| T-del99`）；
`internal_no='T-del99' AND del_flag='0'` → **1**（部分唯一索引 `uk_sample_internal_no WHERE del_flag='0'` 成立），全库含软删 = **2**。

## 6 · 空数据：新外部用户 — PASS

接口侧脚本 `doc/waves/regression/D2/L3r3-c67.sh`（本片自写），日志 `.tmp/qa-r3/c67.log`。
身份 `--as phone:r3new1:13800000096`（首登自动建号，api.sh 自身登录路径成功；userId `2102166394308149250`）。

| 调用 | 输出 |
|---|---|
| `GET /mp/me` | `200`；`identity="external"`，`ext={"unitId":null,"unitName":null,"groupId":null,"groupName":null,"unitNameInput":null,"groupNameInput":null,"bindStatus":"unbound","rejectReason":null}` |
| `GET /mp/ext/sample/list?pageSize=20`（历史编辑记录数据源） | `200	查询成功`，`total=0, rows=[]` |
| `?sampleKind=organoid` / `?sampleKind=embed` | 各 `200	查询成功`，total=0 |
| `GET /mp/ext/units` | `200	操作成功` |

库内 corroboration：该号样本数 = **0**；`t_lqg_ext_profile` 一行 = `unbound | - | - | - | -`（unit/group/自填全空）。
端侧（首页 + 历史编辑记录不报错）见第 7 节浅 H5：`① 新号（mock seed 13800000099，同样 0 样本、unbound）首页 3 格、无错误/重试文案、pageerror 0；历史编辑记录走空态「你填过的记录会出现在这里」、有页签（数据源可用）、无 ErrorState、pageerror 0`。

## 7 · 板块不渲染 / 身份缺失 / 外部类器官夹带 — PASS

### 7a 浅 H5（`doc/waves/regression/D2/L3r3-mp-shallow.mjs`，**本片自写脚本**），日志 `.tmp/qa-r3/h5shallow.log`：**18/18 PASS**，pageerror 0 / console.error 0

```
PASS  ① 新号首页宫格 = 3（外部三张表） — tiles=["样本记录信息表…","类器官收样记录…","石蜡包埋送样记录…"]
PASS  ① 新号首页无错误/重试文案      PASS ① 新号首页无 pageerror
PASS  ① 历史编辑记录不报错（不是 ErrorState）— "…你填过的记录会出现在这里"
PASS  ① 历史编辑记录有页签（数据源可用）      PASS ① 历史编辑记录无 pageerror
PASS  ② 外部（extA）首页宫格 = 3
PASS  ② 外部「我的」不含「内部管理」  PASS ② 外部「我的」含「历史编辑记录」+「单位与组别」
PASS  ③ 内部（staff）首页宫格 = 4 — [样本/类器官/石蜡包埋/-80 冻存记录]
PASS  ② 内部「我的」含「内部管理」    PASS ② 内部管理四入口齐全（miss=[]）  PASS ② 内部「我的」不含「单位与组别」
PASS  ③ identity=null：首页 0 格      PASS ③ identity=null：首页出「没能确认你的身份」兜底、不默认内部
PASS  ③ identity=null：首页无 pageerror
PASS  ③ identity=null：「我的」不渲染 历史编辑记录/内部管理/单位与组别
PASS  ③ identity="INTERNAL"（契约外）：首页 0 格（绝不默认内部）
```
（身份缺失用 Playwright 拦截 `**/mp/me` 把 `identity` 置 `null` / `"INTERNAL"`；登录页先硬 reload 复位再点 `.login__box` 勾协议，再点调试登录。）

### 7b 接口侧：外部类器官入参夹带（脚本 `L3r3-c67.sh` 后半）

`--as extC POST /mp/ext/organoid` body = legit（`sourceUnitName/organoidType/remark`）+ 夹带 `internalNo=T-L3R3ORG`、`receiveDate=2026-01-02`、`verifyStatus=valid`、`submitSource=internal`、`isFixed=Y`
→ `200	操作成功`，id `2102166397856530434`；库内 `sample_kind='organoid'` 行数 **1 → 2**（确实建行 → 夹带被忽略而非整体被拒）。
新行 = `organoid \| external \| pending \| internal_no=<空> \| receive_date=<空> \| is_fixed=<空> \| L3R3类器官备注`；
`…AND submit_source='external' AND verify_status='pending' AND coalesce(internal_no,'')='' AND receive_date IS NULL AND is_fixed IS NULL AND remark='L3R3类器官备注'` → **1**（exit 0）；
全库 `internal_no='T-L3R3ORG'` → **0**；全库 `receive_date='2026-01-02'` → **0**。

## 8 · 记账项（本片独立复现）

### S2 · 外部写端点非法/缺字段 body → 500 响应体回吐完整 SQL 与整行 DB 明细（独立复现仍在）
```
$ bash doc/verify/api.sh --as extA --out .tmp/qa-r3/s2a.json POST /mp/ext/organoid '{"organoidType":"L3R3探针B","remark":"L3R3探针B"}'
code=500；msg 含 '### Error updating database' / 'PSQLException' / '### SQL' / 'Failing row contains'
     / 'SampleMapper' / 't_lqg_sample'（逐项 True）
  Detail: Failing row contains (2102166638072709122, SJ00000070, organoid, external, 9000000111, pending, null, …
```
库内回滚成立：`remark='L3R3探针B'` → **0** 行（exit 0）。影响：任何已登录外部账号可构造，拿到表名/列名、整行明细与内部 `user_id`（9000000111）；小程序表单全字段且只送 Y/N → 老师主流程走不到。

### S3 · 客户端错误业务码 400 / 500 混用 + javadoc 与实现不一致
`PUT /mp/ext/sample/{id}` 归属/状态拒绝 → **400**；`PUT /mp/int/sample` 待核验/无效拒绝、`PUT /lqg/sample/{id}/verify` 缺编号/缺日期/撞号/缺原因四条 → **500**（msg 分支全对）。
并且 `MpSampleController` javadoc 明写「待核验 / 无效 → 400」，实际 500。

### 本片自己的坑（不计 issue）
`--quiet` 已修好（§0）；`db.py --quiet` 只打印**每行第一列**（多列取值必须去掉 `--quiet`，本片 §5 一条多列查询一开始只回 id 就是这个）。
`echo "$N1（…）"` 中了 README 坑 #6（多字节首字节吃进变量名 → unbound variable）——只影响我脚本的显示行，改成 `${N1}` 后断言照跑。
