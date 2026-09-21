# AUTH-GROUP-001 · accept 复跑脚本

从**工作区根**执行（脚本自己会 `cd` 到根，也可以直接 `bash <路径>`）：

```bash
bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc1.sh   # DDL
bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc2.sh   # STATE（会 reseed 两次）
bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc3.sh   # MENU
```

## 与 ticket front-matter 的 `run` 的差异（只有一处，且只针对沙箱）

| accept | 原文 | 本脚本 | 为什么 |
|---|---|---|---|
| 2 | `bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --bizcode PUT …` | 去掉 `--fresh-module ruoyi-lqg` | `api.sh` 第 71 行用 `ps -o lstart=`，在本 agent 沙箱被禁（`/bin/ps: Operation not permitted`）→ `exit 2`。按规矩**没有改 `api.sh`**，用等价证据覆盖那道守卫的两个半边，见完工报告 §5。 |

三条 `run` 里其余的 `python3 doc/verify/db.py` / `jq -e` / `grep -qvE` 段落**逐字未改**。

## 两个必须知道的前提

1. **跑之前清 token 缓存**：`api.sh` 的缓存（`$TMPDIR/lqg-verify-token-*`）只按 mtime 判新鲜，
   陈旧 token 会被误判成假 401（AUTH-STAFF-001 WARN-4）。三个脚本开头都 `rm -f` 过了。
2. **accept 2 会留下一个雪花 id 的外部账号**（`--as extA` 之外没有；它读的是 seed 的固定 id）。
   收尾若发现库里多了临时手机号账号，用 `bash doc/waves/tools/clean-orphan-accounts.sh --yes`
   清一遍（`reseed.sh` 只清 `9000000000-9000009999` 段，清不掉雪花 id）。

## 长链的坑（脚本里已经处理）

ticket 的每条 `run` 都是 `a && b && c` 长链。直接把长链丢给 `set -e` 会踩
「非末尾位置失败 → 静默短路 + exit 0」的假绿（AUTH-STAFF-001 坑 3）。
三个脚本都把整条链包进函数再判**整条链的 rc**（`if accN; then … else exit 1; fi`）。
