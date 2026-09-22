# D3 r2 — L3（独立邪路场景）工作笔记

范围：qa_scope L3 五条 + 顺带独立复验 #145 后端侧。**不复跑 L0/L1/L2**（L0+L1 已 pass；L2 r2 三组 24/24+32/32+21/21 已 pass）。
硬纪律：不读任何 PNG/截图；前台单命令 ≤ 2 分钟；后台长任务落 log 后 tail/grep；边跑边写本文件。

## 环境与反 stale（自跑，不引用别人结论，不用 api.sh --fresh-module）
- 后端 8081 PID **21501**（`bash .tmp/run-backend.sh`，log `/tmp/l3r2-backend.log`）。
- jar `code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar` mtime **2026-09-22 10:13:47**；
  `lsof -p 21501 | grep ruoyi-admin.jar` → **2** 个 REG fd 指向该 jar；
  `find code/RuoYi-Vue-Plus -path '*/src/*' -newer <jar> | wc -l` → **0**；
  后端自身启动日志 `Starting DromaraApplication ... with PID 21501 (...ruoyi-admin.jar...)`，启动时间 **11:26:20**（晚于 jar mtime）。
- `LQG_API_BASE=http://127.0.0.1:8081`（verify.env）；8080 PID 55735 = Kevin 的，未碰。
- 脚本改动核查（不当独立证据）：`git log --oneline -8 -- doc/waves/regression/D3/` → 094bf87（r2 L2 新写 L2-r2-*，r1 脚本未动）、10c3558、59c053f。→ 本片自写新脚本独立重跑。

## 我的探针（自写，独立重跑）
- `doc/waves/regression/D3/L3-r2-probes.sh`（g1..g6，新文件，没覆盖任何人的资产）；全量日志 `doc/waves/regression/D3/L3-r2-full.log`。
- 断言强度：每条「被拒」= 业务码白名单 + msg 关键词 + 紧跟一条 `db.py --eq` 精确库内不变断言；
  关键前置都加了正向存在性断言（如 1004 确有已核验块、external 基线非空、全量导出行数>1），避免「零行恒绿」。
- reseed 后清 `$TMPDIR/lqg-verify-token-*` 再跑。

## 跑出来的结果：80/80，0 fail，0×S0/S1
| 段 | 覆盖 | 结果 |
|---|---|---|
| g1 | 染色与挂靠 4 条被拒 + 库内不变 | 11/11 |
| g2 | 有已核验石蜡块的样本改判无效被拒 + 库内不变 | 6/6 |
| g3 | 外部送样 7 条被拒/不生效 + 库内不变 | 21/21 |
| g4 | 外部详情 embeds 键集合（CR 精确豁免）+ extC/extE 404 + 开关 | 13/13 |
| g5 | 导出边界（403/筛选/空结果） | 12/12 |
| g6 | #145 后端侧 | 17/17 |

### g1（染色与挂靠）
- `NONE+HE` → 500「「无染色」与其余染色互斥，不能同时选」；`OTHER` 不带名称 → 500「选了「其他」必须写具体染色名称」；
  `PAS` → 500「染色「PAS」不在字典 lqg_stain_type 里」；挂 pending 的 1002 → 500「内部录入只能挂到已核验有效的样本（当前状态：pending）」。
- 每次拒后 `count(*) FROM t_lqg_embed` 恒 = 6；终态 `paraffin_block_no LIKE 'T-X%'` = 0。

### g2（valid→invalid 闸）
- 前置：1004 名下已核验石蜡块 = 1、样本 = valid；`PUT /lqg/sample/9000001004/verify {action:invalid}` → 500「该样本名下已有包埋 / 冻存 / 质控文档，不能改判无效」；
  库内仍 `valid`、`invalid_reason IS NULL`、石蜡块数 = 1 不变。

### g3（外部送样）
- 基线 external=1 / all=6 / marker=3。
- extB 替同组 extA 的 1001 → 400「只能挂本人送检过的样本」（ext 仍 1）；extA 挂自己 invalid 的 1003 → 400「这条样本已判无效」（all 仍 6）。
- **夹带 6 字段**（paraffinBlockNo/verifyStatus/embedBy/stainTypes/operatorName/submitSource）→ body code=200，但落库精确 = `external|pending|-|-|-|-`，submitter=王医生，all=7（只多 1 行，无额外副作用），marker 仍 3，全库无 `T-hack9`。
- 所挂 1002 pending 时判 2006 有效 → 500「所挂样本还未核验有效（当前状态：pending）」，2006 仍 `pending|-`。
- 普通保存改 2006 → 400「待核验 / 无效的送样不能通过普通保存修改…」，2006 仍 `pending|-|-|组织`。
- extA 改实验室录的 2001 / extB 改同组 extA 的 2006 → 均 400「只能修改重提本人提交的送样」，库内无被改行、2001 仍 `valid|T-E01-1|组织`。

### g4（外部详情 embeds 键集合，按 CR-20260918-07 与 fixtures/java/ExtChokepointContractTest.java:47-51 豁免）
- extB `GET /mp/ext/sample/9000001001`：embeds = 2 块 `T-E01-1/T-E01-2`，`[0].embedBy=李工`、`[0].operatorName=李工`（**必须对外可见**），`[1].embedBy=null`；已切片 / HE+IHC。
- 实到 24 键 = agaroseEmbedTime, agaroseSendTime, dehydrateTime, editable, embedBy, id, invalidReason, markers, mine, operatorName, organoidSourceType, paraffinBlockNo, paraffinEmbedTime, sampleId, sampleType, sectionTime, sectioned, stainOther, stainTypes, submitNo, submitterName, tissueProcessTime, tissueReceiveTime, verifyStatus
  → **恰好 = 契约 ExtEmbedVo 列表**，⊆ 白名单；`embedBy/operatorName` 确在键集合内（另加一条防「被漏掉而恒绿」的存在性断言）。
- 无 `internalNo`（默认开关 false；详情级与 embeds 级都没有）；无 `verifyBy/verifiedBy/remark/冻存/创建更新人/手机号`；样本详情级也无 `operatorName/embedBy/verifyBy`。
- extC / extE 取 1001 详情均 404「样本不存在」。
- 开关 `lqg.ext.show-internal-no` false → true（extB 详情 `internalNo=T-hli01`）→ 还原 false，库内确认还原。

### g5（导出边界）
- 前置：全量 `POST /lqg/sample/export/tissue` 表头 14 列逐字一致、8 行（=同条件列表 total，>1）。
- extA 调导出 → 403「没有访问权限，请联系管理员授权」。
- 带筛选 `?sourceUnitId=9000009002`：同条件列表 total=1 / ids=[9000001006]，导出恰 1 行、含 T-hco04、不含 T-hli01、非 0 行。
- 空结果 `?sourceUnitId=9999999999`：接口 exit 0（不报错），xlsx 仍 3935 bytes，表头一致、0 数据行。

### g6（#145 后端侧独立复验）
- **pending 分支**：extA 建 E1（pending，挂已核验 1001）→ `PUT /lqg/embed` 仍拒 400 且库内 `pending|-|组织` 原样；
  `PUT /lqg/embed/{E1}/verify {action:valid, paraffinBlockNo:T-r2l3-A}` → 200，库 `valid|T-r2l3-A|verify_by=set`。
- **invalid 分支**：建 E2 → verify `{action:invalid, reason:测-r2l3-无效原因}` → 200，库 `invalid|测-r2l3-无效原因`；
  `PUT /lqg/embed` 仍拒 400 且库内原样；再 verify `{action:valid, T-r2l3-B}`（invalid→valid 改判）→ 200，库 `valid|T-r2l3-B|旧原因已清`。
- 负向：E3 verify 缺编号 → 500「判有效必须填石蜡块编号」、缺原因 → 500「判无效必须写原因」，库内仍 `pending|-|-`。
- **结论：#145 后端侧已修（r1 S0 的前端部分由 L2-r2 关闭，本片独立确认后端两个端点行为正确）。**

## 台账 / doc-drift（不判实现缺陷）
- **#146**：`doc/phase-plan.yaml` D3 qa_scope L3 第 4 条仍是 CR-20260918-07 **之前**的旧写法（要求 embeds「没有 embedBy / operatorName」），
  与 CR + `change-log.md`（operatorName/embedBy 只对 ExtEmbedVo 精确豁免）+ 契约 ExtEmbedVo 互斥。实测实现按 CR 正确 → 记 **S3 doc-drift**，不判实现缺陷。
- #129（perms 六/七）、#137（EMBED sort=recent 不收窄）、#138（EMBED-MP-001 accept3 grep 恒真）本轮 L3 未触及其断言点，未据以判实现缺陷。

## 结论
- **L3 = pass**（80/80，0×S0/S1）；台账 1 条 S3 doc-drift（#146）。escalated：无。
- 未复跑 L0/L1/L2；未读任何 PNG/截图。
