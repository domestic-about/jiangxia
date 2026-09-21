# D1 r2 — L3 独立邪路 QA notes

分支 task/D1。后端 8081 自起：`bash .tmp/run-backend.sh`（dev + api-decrypt.enabled=false）。
log: `.tmp/qa-r2-l3-backend.log`。PID 见反 stale 段。

## 0. 反 stale（四项，自跑）
（待补：jar mtime/sha256 + lsof PID + 进程持 jar + 启动晚于 jar + 源码无新于 jar）

## 回归重放：doc/waves/regression/D1/L23-l3-api.sh
（待补）

## 独立重跑四类
（逐类边跑边记）

（反 stale 完成）
- jar: code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar mtime=2026-09-21 16:32:24 sha256=2c10af1121e7f5d1feddf40a49fa65903bd991ffd3bb4576bdfa23d95ea5f6ca
- lsof -ti tcp:8081 -sTCP:LISTEN → PID 17220
- lsof -p 17220 有 2 个 fd 指向该 jar（说明进程确实跑这个 jar）
- 进程启动 2026-09-21 22:22:35 > jar mtime 16:32:24（psutil，ps 被禁）
- find ruoyi-lqg 源码 *.java newer than jar → count=0

## 回归重放
`bash doc/waves/regression/D1/L23-l3-api.sh` → **L3 32/32 通过**（log .tmp/qa-r2-l3-replay.log），exit 0。

## 独立重跑（自写脚本 doc/waves/regression/D1/L3-r2-independent.sh）
第 1 次跑 69/72。3 条 FAIL 全是**我自己脚本的 harness 失误**，不是产品缺陷：
`call()` 把 clientid 硬写成 PC，而 staff/extA 用的是 **mp** token → clientid 与 token 不配套被 401。
已改为 `mcall()`（mp token 配 MP 头）重跑。（教训记录在案，供后续 L3 脚本复用。）

第 2、3 次跑的 FAIL 根因（harness landmine，非产品缺陷；最终已定位）：
- **`reseed.sh --yes` 不清运行时建的账号**。r1 正向对照建的第二个管理员 `lqg_13800000079`(id 2102041564275646466, lqg_admin) 在 reseed 后仍在库里 ⇒ 我脚本第 2 次跑时 ② 起点就有 **2 个 lqg_admin**，「最后一个管理员」闸门合理地不触发（role-change/DELETE 都返 200），断言全歪。
  修法：`reseed()` 后补 `clean-orphan-accounts.sh --yes`（r1 脚本结尾有做，我漏了）。
  受控复现（干净 1 管理员）：PUT role→`500 不能把系统里最后一个实验室管理员改成普通内部人员`，DB role 仍 101；超管 DELETE→`500 不能撤销系统里最后一个实验室管理员的权限`，DB 仍 1 个 admin。**闸门正确**。
- **`GET /mp/ext/profile` 恒返 405**（405 在鉴权前由 handler 映射层定），拿它断「token 失效后 401」永远看不到 401。改用真实方法 `PUT /mp/ext/profile` 探第二个端点。
  （`/mp/me` 的撤销后 401 一直正确。）
- 受控复现 2 管理员路径：role-change 返 200 且 **DB 真的写成 102(lqg_internal)**，撤销返 200；闸门不是「一律拒绝」。

## 最终
- 独立重跑 `doc/waves/regression/D1/L3-r2-independent.sh` → **73/73 通过**，exit 0（log .tmp/qa-r2-l3-indep5.log）。
- 回归重放 r1 `L23-l3-api.sh` → 32/32。
- audit 落 doc/waves/qa/D1-r2-L3.json（L3 pass；4 条 S3：3 harness + 1 doc-drift，均不拦门）。
- 未发现 S0/S1；产品代码未改；分支 task/D1 未切/未 push/未 merge。
