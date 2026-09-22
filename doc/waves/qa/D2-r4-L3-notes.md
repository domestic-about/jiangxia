# D2 r4 · L3（独立邪路场景）notes —— 边跑边写

## 环境 / 反 stale（自跑，不引用他人结论）
- 分支 `task/D2`，工作区根 `<ws>`。
- 后端**本片自起**：`bash .tmp/run-backend.sh` → PID 4312，监听 8081，启动日志首行 `2026-09-22 08:12:07`（本片 log `.tmp/l3r4/backend-8081.log`）。
- jar `code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar` mtime `2026-09-22 07:03:37`。
- 反 stale 三件（自跑）：
  - `find ruoyi-modules/ruoyi-lqg/src -type f -newer <jar>` → **0 行**（源码不新于 jar）
  - `lsof -ti tcp:8081 -sTCP:LISTEN` → 4312；`lsof -p 4312 | grep -c ruoyi-admin.jar` → **2**（进程真持该 jar）
  - 进程启动时刻 **08:12:07 > jar mtime 07:03:37**（沙箱禁 `ps`，用本片自起的后端日志首行时间戳自证）
- 容器 5433 / 6380 / 9002 / 9003 在跑；8080 / 5432 / 6379 未碰。
- H5 dev 9200：`VITE_MOCK_LOGIN=1 pnpm dev:h5 --port 9200`，本片自起。

## 本片资产（全部本片自写，放 doc/waves/regression/D2/）
| 文件 | 覆盖 |
|---|---|
| `L3r4-api.sh` | 第 1-5 条 + 第 7 条接口段（越权/夹带/状态机/角色闸/库不变） |
| `L3r4-mp-shallow.mjs` | 第 6、7 条的 H5 浅渲染检查 |

（下面逐条随跑随记。）

---

## 第 1 条 越权 —— **PASS**（13/13 子断言）
命令：`bash doc/waves/regression/D2/L3r4-api.sh`（本片自写；日志 `.tmp/l3r4/c1c5.log`）
- `--as extC GET /mp/ext/sample/9000001001` → `{"code":404,"msg":"样本不存在","data":null}`
  - 响应体全文 grep `SJ90000001|T-hli01|internalNo|submitSource|receiveDate|verifyBy|donorName|hospitalNo|submitNo` → **0 命中**；`data=null`。
- `--as extE GET /mp/ext/sample/9000001001` → 同样 `404` + 无样本字段。
- **正向对照**（防「全站 404」假绿）：extA / extB(同组) GET 1001 → 200；extE GET 自己的 1007 → 200。
- ★ `invalidReason` 新键取证：`ExtSampleVo` 行 keys = `[createTime,donorNameMasked,editable,id,invalidReason,mine,organoidType,sampleKind,submitNo,submitterName,tissueType,updateTime,verifyStatus]`；
  `ExtSampleDetailVo` keys = `[age,createTime,docs,donorName,editable,embeds,gender,hasPathology,hospitalNo,id,invalidReason,mine,organoidType,remark,sampleKind,sourceUnitName,submitNo,submitterName,tissueType,verifyStatus]`。
  **两者都没有** `internalNo / verifyBy / submitSource / receiveDate / operatorName` → 新键没把内部字段带出来。

## 第 2 条 外部 PUT 夹带 / 可看不可改 / 有效自己改不了 —— **PASS**
- 夹带：`extA PUT /mp/ext/sample/9000001002` 带 `internalNo=HACK-C2 / verifyStatus=valid / submitSource=internal / receiveDate=2020-01-01 / sampleKind=organoid / submitNo=HACK-NO / verifyBy / submitterId` → `code=200`，但库内
  `internal_no=<null> verify_status=pending submit_source=external receive_date=<null> sample_kind=tissue` —— **夹带列一条都没生效**；
  变化列 `[source_unit_id,donor_name,gender,age,hospital_no,tissue_type,has_pathology,remark,update_by,update_time]` ⊆ 外部可改的送检段 + 审计列，**无越界列**。
  `remark` 落库证明请求真被处理（不是静默丢弃）。
- 同组别人的：`extB GET 1002` = 200（可看）；`extB PUT 1002` 被拒且**整行 json 逐字节不变**。
- 已有效的自己：`extA PUT 1001` 被拒且整行不变。
- ★ 外部列表回归（针对 `SampleQueryService`/`SampleQueryBo` 来源单位 + keyword 改动）：
  extA `sampleKind=tissue` = `{1001,1002,1003,1004}`、extC = `{1005}`、extA `onlyMine=true` = `{1001,1002,1003}` —— 外部口行为未被内部筛法改动影响。
- ⚠️ 记账（S3）：PUT 会把原本非空的 `source_unit_id`（seed 里 1002 = 9000009001）写成 `null`（`source_unit_name` 仍是「A 医院」）。
  根因同 L2 已记的 S2（外部 BO 只带单位名/`sourceUnitId` 原样透传，无「名字→id」解析）；权威 `FIELD:t_lqg_sample.source_unit_id` 明写「自填单位名时为空」→ 不判 S0/S1。

## 第 3 条 内部经小程序 PUT —— **PASS**
- `staff PUT /mp/int/sample {id:1002}` → `{"code":500,"msg":"待核验与无效的样本只能在网页工作台核验或改判，小程序里不能改"}`，整行 json 不变。
- `staff PUT {id:1003}`（无效）→ 同上被拒，整行不变。
- `staff PUT {id:1001, remark:L3r4-c3-valid}` → 200；`update_by=9000000101`（= staff 本人）、`update_time` 在 5 分钟窗口内、`remark` 真落库。
- ★ `sampleKind` 保护（契约第 49 行 / issue #105）：与库里不一致 `organoid` → **400** 且整行不变；
  一致 `tissue` → 200 且落库；**完全不传 `sampleKind`** → 200 照常改 —— 只拦 kind 不一致，不误伤正常字段修改。

## 第 4 条 角色闸 —— **PASS**（8/8）
| 调用 | 结果 |
|---|---|
| extA GET `/lqg/sample/list` | 403 没有访问权限 |
| extA GET `/mp/int/sample/list` | 403 |
| extA POST `/mp/int/sample` | 403 |
| staff GET `/mp/ext/sample/list` | 403 |
| staff POST `/mp/ext/organoid` | 403 |
| **正向对照** staff `/mp/int/sample/list` / `/lqg/sample/list` / extA `/mp/ext/sample/list` | 200 / 200 / 200 |

## 第 5 条 核验状态机 —— **PASS**
- 判有效缺内部编号 → 被拒（`code=500`，msg「判有效必须同时给收样日期与内部编号，缺少：内部编号」），整行不变。
- 内部编号撞号 `T-hli01` → 被拒，整行不变。
- 判无效缺原因 → 被拒（msg「判无效必须写原因」），整行不变。
- 软删编号可重用：`admin PUT /lqg/sample/9000001002/verify {action:valid, receiveDate, internalNo:T-del99}` → **200**，库里 1002 `verify_status=valid / internal_no=T-del99`（T-del99 原属 del_flag=1 的 1010）。
- **反向对照**：T-del99 被 1002 占用后（未软删）再给 1007 用 → 被拒。

## 第 7 条（接口段）外部类器官夹带 —— **PASS**
`extA POST /mp/ext/organoid` 带 `internalNo/receiveDate/verifyStatus/submitSource/sampleKind/submitNo` → 200 建行，库里
`sample_kind=organoid / submit_source=external / verify_status=pending / internal_no=null / receive_date=null / submit_no=服务端自生成` —— 夹带列一条不生效。

## ★ 契约码核对（信息项，用于 doc-drift 取证；不改变上面任何一条 pass/fail）
| 调用 | 契约 | 实测 |
|---|---|---|
| `PUT /mp/int/sample` 待核验外部样本 | 400（契约第 49 行；`MpSampleService` 类注释也写 400） | **500** |
| `PUT /lqg/sample/{id}/verify` 缺必填 | 400 类（契约第 10 行） | **500** |
| `PUT /mp/int/sample` `sampleKind` 不一致（对照） | 400 | 400 ✅ |
前端 `src/utils/request.ts` 对任意非 200 一律 toast `msg`，故 500/400 无用户可见差异 → 判 doc-drift（S3），不拦门。

---

## 第 6 条 空数据（新外部用户）—— **PASS**（接口 4 项 + H5 7 项）
接口（`--as phone:newbie1:13800000099`，即调试登录面板的「外部人员 · 新号（未绑定）」）：
- `GET /mp/me` → `identity=external`、`ext.bindStatus=unbound`、`unitId/groupId=null`。
- `GET /mp/ext/sample/list` / `?sampleKind=organoid` / `?onlyMine=true` → 三条都 `code=200, total=0, rows=0`（不报错、不 500）。
- 库里该号样本数 = 0。
H5（`node doc/waves/regression/D2/L3r4-mp-shallow.mjs`，日志 `.tmp/l3r4/c67.log`）：
- 首页 3 格 `["样本记录信息表","类器官收样记录","石蜡包埋送样记录"]`（无「-80 冻存」）；正文含未绑定提示；无 `pageerror`/console error。
- 「我的」→「历史编辑记录」→ 空状态「你填过的记录会出现在这里」，**无错误文案**、无 `pageerror`；
  切「只看我提交的」开关后仍不报错。

## 第 7 条（渲染段）—— **PASS**
- 外部 extA「我的」行 = `["历史编辑记录","A 医院 · 肝胆外科组","用户协议","隐私政策"]`，正文**不含**「内部管理」与「核验、冻存取用请到网页工作台」，`.adm` 节点数 = 0（**不渲染**，不是置灰）。
- 内部 staff「我的」有「内部管理」板块，`.adm .merow__t` = `["样本记录信息表","类器官收样记录","石蜡包埋送样记录","-80 冻存记录"]`（4 个入口）。
- 身份缺失（route stub `/lqg-api/mp/me` → `identity:null`）：首页 `.lqg-tile` = **0**，显示「没能确认你的身份，请重新登录后再试」；
  「我的」`.merow` = **0**，正文不含「历史编辑记录 / 单位与组别 / 内部管理」任一板块名。
- 身份缺失（`identity:"INTERNAL"` 不认识的串）：首页 0 格（**不默认当内部**），「我的」0 板块。
- 真·无 token（清 localStorage 后直接进首页）：「我的」/首页均 0 格/0 板块，被弹回登录页，无错误文案。

---

## 汇总
- **L3 = PASS**：`L3r4-api.sh` 60/60；`L3r4-mp-shallow.mjs` 22/22。
- **0 × S0 / 0 × S1** → 不拦门，收工。
- 记账 2 条 S3（见 audit）：① 外部 PUT 清空既有 `source_unit_id`（与 L2 已记 S2 同源）；② 状态机/核验缺必填实回 500 而契约写 400（doc-drift，前端无可见差异）。


