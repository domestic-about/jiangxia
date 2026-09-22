# D2 r3 · L2（端侧 UI）独立复验笔记

- 环境：后端 8081（dev + api-decrypt=false）、PG 5433、plus-ui 8082、miniapp H5 9200（VITE_MOCK_LOGIN=1）
- 微信开发者工具在本沙箱跑不通（EPERM + 需扫码）→ 用 H5 + Playwright 等价覆盖；**开发者工具 / 真机未覆盖**。
- 先看：`doc/waves/regression/D2/` git log → 实现方 r1 返工动过两处探针（G3-20 正负号翻转、WEB-L2-06b 补鉴权头）。方向翻转属合理，但 WEB-L2-06b 改成只打字典接口、不再验「页面是否用对 key」→ 记 harness 弱化（r2 的 R2W-06a/06b 用页面下拉正面覆盖，我以此为准）。

## 发现 1（S1，L2，硬断言失败）：/mp/int/sample/list 的 sampleKind 过滤不生效
- 我的脚本 `L2r3-mp-tabs.mjs`：内部历史编辑记录「样本记录信息表」页签 3 行含 organoid（T-r2org1）；「类器官收样记录」页签 4 行含 tissue（T-hli05、T-r2m01）。两页签行集合相交 3 行。
- 独立 API 复现（页面同源口，`--as staff`，sort=recent&pageSize=100）：
  - 无 sampleKind → 4 行 [T-hli05(tissue), T-r2org1(organoid), T-r2m01(tissue), T-oco01(organoid)]
  - sampleKind=tissue → 3 行（**排除了 organoid 的 T-oco01，却留下 T-r2org1 organoid**）
  - sampleKind=organoid → 4 行（**含 tissue 行**）
  - sampleKind=bogus → 与 tissue 同 3 行；kind=organoid → 4 行
  - 对照：extC 的 `/mp/ext/sample/list?sampleKind=...` **过滤正确**（tissue→SJ90000005，organoid→SJ00000066）→ 病灶只在内部口。
- 影响：剧本 7「内部管理/历史编辑记录按类别分页签」；类器官页签点 tissue 行会进 `/pages/organoid/form?id=9000001008`（错类别表单）。可能与本轮 L1 的 SampleQueryService 筛选拼装 S1 同根。

## 组 2 独立 API 佐证（我的口径，非 r2 脚本结论）
- 工作台 `/lqg/sample/list`（--as admin）逐条与库内对账：sourceUnitId=9000009002(B 大学)→2 行 [SJ90000006,SJ90000009]；sampleKind=organoid→[SJ90000009]；verifyStatus=pending→[SJ90000007,SJ90000002]；submitSource=internal→[SJ90000008,SJ90000009] → 全部与 `SELECT submit_no ...` 一致（r2 的 source-unit S1 已修）。
- 对照：外部口 `/mp/ext/sample/list?sampleKind=` 过滤正确。

## 最小复现（S1 根因）
1. reseed 后：`--as staff GET /mp/int/sample/list?sort=recent&sampleKind=tissue` → 1 行 [T-hli05]；`=organoid` → 1 行 [T-oco01]（正确，因 1008/1009 的 update_by 都是 NULL）。
2. `UPDATE t_lqg_sample SET update_by=9000000101, update_time=now() WHERE id=9000001008`（模拟「在历史编辑记录改一个字段保存」）。
3. 再查：`sampleKind=organoid` → 2 行 [T-hli05(tissue), T-oco01(organoid)]（tissue 行漏进类器官页签）；`sampleKind=tissue` → 1 行。
4. 源码：SampleQueryService.list 的 `wrapper.inSql(createBy,...).or().inSql(updateBy,...)` 未加 `and(w->…)` 括号 → `(全部筛选 AND create_by IN 内部) OR update_by IN 内部`，OR 短路前面所有条件。
5. 危害：类器官页签点中 tissue 行 → /pages/organoid/form?id=9000001008&mode=edit，保存走 updateIntSample({id,sampleKind:'organoid',…})（organoid/api.ts:69-81）→ 组织样本被改成类器官。

## 反 stale
- `find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -newer <jar>` 为空；jar mtime 2026-09-22 06:18:43。
- 8081 PID=81361，lsof -p 81361 持该 ruoyi-admin.jar；后端启动日志 06:26:01（晚于 jar mtime）。
