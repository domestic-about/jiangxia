# D3 r1 L3 (独立邪路场景) notes

- ws branch: task/D3. Port exclusives: backend 8081 / PG 5433 / redis 6380 / minio 9002-9003. 8080 是 Kevin 的，不碰。
- 先看 git log（脚本可被动过，不当独立证据）：
  - `git log --oneline -8 -- doc/waves/regression/D2/` → 852de43/2245349/3cbe0cf/724939f/01e2ce3/8d9ed0c/c898bb3/7de97b0（D2 返工改过探针期望值；L3 不引用其结论）
  - `git log --oneline -8 -- doc/waves/regression/D3/` → 59c053f（L2 片 S0）
- jar: code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar mtime 2026-09-22 10:13:47；`find code/RuoYi-Vue-Plus -path '*/src/*' -newer <jar>` 为空 → 源码不新于 jar。
- 8081 起前为空；8080 → PID 55735（Kevin 的 java，不碰）。

## 反 stale 自跑证据（不用 api.sh --fresh-module）
- `lsof -ti tcp:8081` → PID 97871
- `lsof -p 97871 | grep ruoyi-admin.jar` → 两个 REG fd 指向 `code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar`（进程持该 jar）
- jar mtime `2026-09-22 10:13:47`；后端启动日志 `2026-09-22 10:40:24 Starting DromaraApplication ... PID 97871`（启动晚于 jar mtime）
- `find code/RuoYi-Vue-Plus -path '*/src/*' -newer <jar> | wc -l` → `0`
- 注：`ps` 被沙箱禁（`/bin/ps: Operation not permitted`），启动时间取后端自身日志。

## 我的探针
- 自写 `doc/waves/regression/D3/L3-r1-probes.sh`（g1..g5），全量日志 `doc/waves/regression/D3/L3-r1-full.log`。
- 反 stale 用上面四步自证；**未**用 `api.sh --fresh-module`（该开关在此沙箱会走被禁的 ps / GNU `date -d`）。

## 结论
- **L3 = pass**，38/38 断言通过，0×S0/S1。台账 1 条：S3 doc-drift（phase-plan D3 qa_scope 第 4 条 embeds 键集合仍是 CR-20260918-07 之前的旧写法，要求「没有 embedBy / operatorName」，与 CR + 契约 fixture 精确豁免互斥；实测实现按 CR 正确，故不判实现缺陷）。
- L2 抓到的 S0（核验抽屉前端调用顺序）不在本片范围，未复跑、未重复记账。
- escalated：无。

## 收尾
- 按 PID kill 8081 后端（PID 97871）；`lsof` 复核 8081/8082/8083/8099/9200/9201 全空（8080 = 55735，Kevin 的，未碰）。
- `bash doc/verify/reseed.sh --yes` → 完成；`bash doc/waves/tools/clean-orphan-accounts.sh --yes` → 运行时账号 0 行。
- 清 `$TMPDIR/lqg-verify-token-*`；`doc/waves/state.json` 未改；未 push / 未 merge。
- 新增文件：`doc/waves/qa/D3-r1-L3.json`、`doc/waves/qa/D3-r1-L3-notes.md`、`doc/waves/regression/D3/L3-r1-probes.sh`、`doc/waves/regression/D3/L3-r1-full.log`。

## 进度
- [x] 起后端 8081 + 反 stale 取证
- [x] L3-1 染色与挂靠规则 — pass 8/8（4 条被拒全 code=500，各带库内不变断言；T-X% 行数=0）
- [x] L3-2 valid→invalid 闸 — pass 3/3（1004 改判被拒 code=500，仍 valid、invalid_reason 仍空）
- [x] L3-3 外部送样 — pass 13/13（替同组/无效样本 code=400；夹带字段 200 但不生效；未核验判有效 500；普通保存 400；外部改实验室块 400）
- [x] L3-4 外部详情 embeds 键集合 — pass 8/8（24 键，含 embedBy/operatorName=李工；无 internalNo/verifyBy/remark/冻存键；extC 1001 → 404；开关 true 才出 T-hli01，已还原 false）
- [x] L3-5 导出边界 — pass 6/6（extA 403；筛选行数=列表 total=1；空结果 0 行只有表头不报错）

## 过程坑（已修）
- 一开始照 ticket accept 抄了 `api.sh --fresh-module ruoyi-lqg` → 违反本任务硬性纪律（不许用该开关），已全量剥离，改为自证反 stale。
- `"行数=$TOT）"` → 全角括号首字节被吃进变量名（README 坑 #6），改 `${TOT}`。
