# D2 r1 · L3（独立邪路）QA 笔记

分支 task/D2 · 只做 phase-plan D2 qa_scope 的 L3 七条 + 两条浅端侧检查。不复跑 L2 的深度 UI 走查。

## 0 环境 / 反 stale（本片自跑，不引用别人结论）

- 后端：`bash .tmp/run-backend.sh`（受管后台作业 bash-229）→ 8081，日志 `.tmp/d2l3-backend.log`，`RuoYi-Vue-Plus启动成功` @ 2026-09-22 00:55:42。
  - 起前 `lsof -ti tcp:8081` 为空；起后 PID = 34097。
  - 反 stale：`find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer ruoyi-admin/target/ruoyi-admin.jar` → **0 行**；jar mtime = Sep 22 00:29:46；`lsof -p 34097 | grep -c ruoyi-admin.jar` = **2**（进程确持该 jar）；后端启动日志 00:55:42 **晚于** jar mtime。
- reseed：`bash doc/verify/reseed.sh --yes` 已跑（本片写断言前）。
- token 缓存：`rm -f $TMPDIR/lqg-verify-token-*` 已在 reseed 前清。

### ★ harness 缺陷（本片自测复现）：`db.py --quiet` 不打印任何行

```
$ python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_sample ORDER BY id LIMIT 3"
9000001001
9000001002
9000001003
$ python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_sample ORDER BY id LIMIT 3"
（空输出，exit 0）
```
→ `OID="$(db.py --quiet --sql ...)"` 类句式永远拿到空值。本片所有取值一律**去掉 `--quiet`**。

## 1 · 越权取详情（extC 猜 id / extE 同组未核验）

| 调用 | 输出 |
|---|---|
| `api.sh --as extC GET /mp/ext/sample/9000001001` | `{"code":404,"msg":"样本不存在","data":null}` |
| `api.sh --as extC --bizcode GET /mp/ext/sample/9000001001` | `404	样本不存在` |
| `api.sh --as extE GET /mp/ext/sample/9000001001` | `{"code":404,"msg":"样本不存在","data":null}` |
| `api.sh --as extE --bizcode GET /mp/ext/sample/9000001001` | `404	样本不存在` |
| 对照 `--as extA GET /mp/ext/sample/9000001001` | `200	操作成功`（端点可用，不是整段 404） |

响应体逐字只有 `code` / `msg` / `data:null` —— **无 donorName / hospitalNo / internalNo / submitterId / sourceUnit 等任何样本字段**
（`grep -c` 见下）。→ **PASS**

## 2 · 外部 PUT 夹带 / 同组别人的 / 已有效的

基线（reseed 后）：`1001|valid|T-hli01|external|2026-08-23`、`1002|pending||external|-`。

| 调用 | 输出 | 库内 |
|---|---|---|
| `--as extA PUT /mp/ext/sample/9000001002`（legit 8 字段 + 夹带 `internalNo=T-L3HACK, verifyStatus=valid, submitSource=internal, receiveDate=2026-01-01`） | `{"code":200,"msg":"操作成功"}` | `1002\|pending\|<空>\|external\|<NULL>\|L3夹带探针\|Y` |
| `--as extB PUT /mp/ext/sample/9000001001` | `{"code":400,"msg":"只能修改重提本人提交的样本（同组的样本可以看，但不能改）"}` | 1001 不变 |
| `--as extB PUT /mp/ext/sample/9000001002` | 同上 400 | 1002 不变 |
| `--as extA PUT /mp/ext/sample/9000001001`（自己的 valid） | `{"code":400,"msg":"样本当前状态不允许外部修改重提（只有待核验、无效的样本可以）"}` | 1001 不变 |

库内断言（去掉 `--quiet`，用真值）：
- 夹带列不变：`SELECT count(*) … WHERE id=9000001002 AND verify_status='pending' AND (internal_no IS NULL OR internal_no='') AND submit_source='external' AND receive_date IS NULL` → `1`，exit 0
- legit 写入确实生效：`… AND remark='L3夹带探针' AND has_pathology='Y'` → `1`，exit 0（证明请求走完了写路径，夹带是被**忽略**而非整体被拒）
- 2b/2c 后复断：`id=9000001001 AND verify_status='valid' AND internal_no='T-hli01'` → `1`，exit 0
→ **PASS**

### S3 记账：外部 PUT 非法 `hasPathology` / 缺 `sourceUnitName` → HTTP 体里回 500 + 整行 DB 明细与 SQL

- `--as extA PUT /mp/ext/sample/9000001002`，body `hasPathology:"yes"`（字典只有 `Y/N`，见 `sys_dict_data lqg_yes_no`）
  → `{"code":500,"msg":"### Error updating database. Cause: … value too long for type character(1) … SQL: UPDATE t_lqg_sample SET …"}`
- 省略 `sourceUnitName` → `{"code":500,"msg":"… null value in column \"source_unit_name\" … Detail: Failing row contains (9000001002, SJ90000002, tissue, external, 9000000111, pending, … T-hli01 密文 …)"}`
- 影响：只影响手工构造 body 的调用方（小程序表单只会送 Y/N 且全字段），主流程不受影响 → **S3 / debt**。

## 3 · 内部经小程序 PUT

| 调用 | 输出 | 库内（1002 基线 = 2a 之后：`remark='L3夹带探针'`、`update_by=9000000111`） |
|---|---|---|
| `--as staff PUT /mp/int/sample {"id":9000001002,…}` | `{"code":500,"msg":"待核验与无效的样本只能在网页工作台核验或改判，小程序里不能改"}` | remark 仍 `L3夹带探针`、update_by 仍 `9000000111`（不是 staff 的 `9000000101`）、仍 `pending` |
| `--as staff PUT /mp/int/sample {"id":9000001003,…}` | 同上 500 | `1003\|invalid\|<空>\|<NULL>` 一字未变 |
| `--as staff PUT /mp/int/sample {"id":9000001001,"remark":"L3内部改有效"}` | `{"code":200,"msg":"操作成功"}` | `1001\|valid\|L3内部改有效\|9000000101` |

库内断言：
- `WHERE id IN (9000001002,9000001003)` 各断行不变 → `1` / `1`，exit 0
- 反证 staff 的两条 payload 字符串在库里 `LIKE` 计数 = `0`，exit 0（没落盘）
- `UPDATE_BY`：`SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.update_by WHERE s.id=9000001001 AND u.phonenumber='13800000001' AND s.remark='L3内部改有效'` → `1`，exit 0
→ **PASS**（记账：拒绝走的是业务码 **500**，不是 400；msg 分支正确，仅码不规范 → S3 / debt）

## 4 · 角色闸（正反都断）

| 调用 | 输出 |
|---|---|
| `--as extA /lqg/sample/list` | `403	没有访问权限，请联系管理员授权` |
| `--as extA PUT /lqg/sample/9000001001/verify`（真核验端点） | `403	没有访问权限，请联系管理员授权` |
| `--as extA /mp/int/sample/list` | `403	没有访问权限，请联系管理员授权` |
| `--as staff /mp/ext/sample/list` | `403	没有访问权限，请联系管理员授权` |
| `--as staff POST /mp/ext/organoid` | `403	没有访问权限，请联系管理员授权` |
| 对照 `--as extA /mp/ext/sample/list` | `200	查询成功` |
| 对照 `--as staff /mp/int/sample/list` | `200	查询成功` |
| 对照 `--as admin /lqg/sample/list` | `200	查询成功` |
→ **PASS**（三个 403 都是权限分支，不是端点不存在；对照组证明同一路径在正确角色下 200）

## 5 · 核验状态机

1002 的整行指纹：`H0 = SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002` = `e7e180cbd0a5748419e7fe7c2159e7c2`

| 调用（`--as admin PUT /lqg/sample/9000001002/verify`） | 输出 |
|---|---|
| `{"action":"valid","receiveDate":"2026-09-01"}`（缺内部编号） | `{"code":500,"msg":"判有效必须同时给收样日期与内部编号，缺少：内部编号"}` |
| `{"action":"valid","internalNo":"T-l3x"}`（缺收样日期） | `{"code":500,"msg":"…缺少：收样日期"}` |
| `{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-hli01"}`（撞号） | `{"code":500,"msg":"内部编号「T-hli01」已存在，请换一个"}` |
| `{"action":"invalid"}`（缺原因） | `{"code":500,"msg":"判无效必须写原因"}` |

库内不变断言：
- `H1`（四条之后重算整行 md5）= `e7e180cbd0a5748419e7fe7c2159e7c2`，**H0 == H1** → 库内一字未变
- `SELECT count(*) FROM t_lqg_sample WHERE id=9000001001 AND internal_no='T-hli01' AND verify_status='valid'` → `1`，exit 0（没被偷走）
- `SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli01'` → `1`，exit 0（没有第二条）

软删编号重用：
- 病灶行 `9000001010 | del_flag=1 | internal_no=T-del99`
- `PUT … {"action":"valid","receiveDate":"2026-09-01","internalNo":"T-del99"}` → `{"code":200,"msg":"操作成功"}`
- `1002` → `del_flag=0 | T-del99 | valid | 2026-09-01 | verify_by=9000000100`；`1010` → `del_flag=1 | T-del99` 仍在
- `count(*) WHERE internal_no='T-del99' AND del_flag='0'` → `1`，exit 0（部分唯一索引 `uk_sample_internal_no WHERE del_flag='0'` 成立）
→ **PASS**（记账：拒绝码又是 **500**，msg 分支正确）

## 6 · 空数据：新外部用户（无样本、未填组别）

身份：`--as phone:newbie1:13800000099`（`xcxCode=mock:newbie1`，首次登录自动建外部账号；`mock-seeds.ts` 里叫「外部人员 · 新号（未绑定）」）。

| 调用 | 输出 |
|---|---|
| `POST /auth/login`（grantType=xcx） | `200	操作成功` |
| `GET /mp/me` | `{"code":200,…"identity":"external","ext":{"unitId":null,"unitName":null,"groupId":null,"groupName":null,"unitNameInput":null,"groupNameInput":null,"bindStatus":"unbound","rejectReason":null}}` |
| `GET /mp/ext/sample/list?pageSize=20`（= 外部历史编辑记录的数据源） | `{"total":0,"rows":[],"code":200,"msg":"查询成功"}` |
| `GET /mp/ext/units` | `200	操作成功` |

库内 corroboration：`SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.submitter_id WHERE u.phonenumber='13800000099'` → `0`；
`t_lqg_ext_profile` 该号一行 `bind_status=unbound`、`unit_id/group_id/unit_name_input/group_name_input` 全 `NULL`。

浅 H5 DOM（`doc/waves/regression/D2/L3-mp-shallow.mjs`，脚本自跑，shots `shots/L3-mp/`）：

```
PASS  首页宫格数 = 3（外部人员 · 新号（未绑定）） — 实际 3
PASS  ① newbie1 首页不报错（无失败/重试文案） — "…合作单位\n还没填单位与组别 · 未绑定\n补充单位与组别，可与同组同事互看样本\n填写…"
PASS  ① newbie1 历史编辑记录不报错（有数据源且走空态，不是 ErrorState） — "历史编辑记录\n样本记录信息表\n类器官收样记录\n石蜡包埋送样记录\n只看我提交的\n你填过的记录会出现在这里"
```
→ **PASS**

## 7 · 内部管理板块 / 身份缺失 / 类器官夹带

浅 H5 DOM（同一脚本）：

```
PASS  ② 外部「我的」正文不含「内部管理」 — "我的\n王\n王医生\n合作单位\n138****0011\n我的记录\n史\n历史编辑记录…\n单位与组别\n单\nA 医院 · 肝胆外科组…已核验…"
PASS  ② 外部「我的」有「历史编辑记录」与「单位与组别」
PASS  ② 内部「我的」正文含「内部管理」 — "…历史编辑记录…\n内部管理\n查看 · 筛选 · 导出\n样 样本记录信息表…冻 -80 冻存记录…核验、冻存取用请到网页工作台…"
PASS  ③ identity=null：首页宫格 0 格 — tiles=0；正文 = "没能确认你的身份，请重新登录后再试\n去登录"
PASS  ③ identity=null：「我的」不渲染历史编辑记录 / 内部管理 / 单位与组别
PASS  ③ identity="INTERNAL"（契约外大小写）：首页宫格 0 格（不默认内部）
== pageerror 0 条 / console.error 0 条 ==
```
（身份缺失用 Playwright 拦截 `**/mp/me` 把 `identity` 置 `null` / `"INTERNAL"`；authority `ui-index.yaml:48` 逐字是「身份缺失或不认识 → **一格都不渲染**」，
实现除兜底文案「没能确认你的身份」+「去登录」按钮外不渲染任何入口/板块 —— 符合 authority，且不存在「默认当内部」的降级。）

接口（第 7 条第三条）：`--as extC POST /mp/ext/organoid`，body = legit 3 项 + 夹带 `internalNo=T-L3ORG, receiveDate=2026-01-02, verifyStatus=valid, submitSource=internal, isFixed=Y`
→ `{"code":200,"msg":"操作成功","data":"2102080219732103169"}`

库内新行：`2102080219732103169|organoid|external|pending|<空>|<NULL>|<NULL>|L3夹带探针2|L3类器官探针`
- `count(*) WHERE organoid_type='L3类器官探针' AND sample_kind='organoid' AND submit_source='external' AND verify_status='pending' AND internal_no IS NULL AND receive_date IS NULL AND is_fixed IS NULL` → `1`，exit 0
- 行数 10 → 11（确实建了行，夹带是被忽略而非整体被拒）；`internal_no='T-L3ORG'` 计数 `0`，exit 0
→ **PASS**

### S2 记账：非法 / 缺字段的 body → 500 响应体里回吐**完整 SQL + 整行 DB 明细**

- `--as extA PUT /mp/ext/sample/9000001002`，`hasPathology:"yes"`（字典只给 `Y/N`）→
  `{"code":500,"msg":"### Error updating database. Cause: … value too long for type character(1) … SQL: UPDATE t_lqg_sample SET update_by=?,…"}`
- 省略 `sourceUnitName`（同一条 PUT / 以及 `POST /mp/ext/organoid`）→
  `msg` 里连 `Detail: Failing row contains (9000001002, SJ90000002, tissue, external, 9000000111, pending, … donor_name/hospital_no 密文 …, 9000000111, …)` 一起吐出
- 范围：任何**已登录外部账号**都能构造（两个外部写端点都可复现）；泄漏表名/列名/密文/内部 user_id。小程序表单只送 `Y/N` 且全字段，**主流程走不到** → S2 / debt。

### S3 记账：同一模块内「客户端错误」业务码不统一

`PUT /mp/ext/sample/{id}` 的归属 / 状态拒绝回 **400**，而同一轮里：
`/mp/int/sample` 待核验拒绝、核验缺参 / 撞号 / 缺原因 四条都回 **500**。msg 分支都对，仅码不规范。
