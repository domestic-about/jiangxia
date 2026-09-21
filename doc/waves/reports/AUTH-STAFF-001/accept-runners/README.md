# AUTH-STAFF-001 · accept 复跑脚本

全部命令**从工作区根**执行（`bash doc/waves/reports/AUTH-STAFF-001/accept-runners/sta001-acc1.sh`）。
`sta001-acc1/2/3.sh` 是 ticket front-matter 三条 `accept[].run` 的**原文逐字**，
唯一改动 = 去掉本 subagent 沙箱跑不了的 `--fresh-module ruoyi-lqg`（`api.sh` 第 71 行 `ps -o lstart=` 被禁 → exit 2）；
等价替代证据见 `../AUTH-STAFF-001.md` §4.0。

| 脚本 | 对应 |
|---|---|
| `sta001-acc1.sh` | accept 1 · STATE |
| `sta001-acc2.sh` | accept 2 · API |
| `sta001-acc3.sh` | accept 3 · MENU（**零改动**，原样） |
| `sta001-kick-probe.sh` | **追加的对抗性探针**：撤销后旧 token 必须立刻 401。刻意构造「撤销前 5 秒内触发过 `cleanOnlineUser`」的时序，用来暴露上游 `PlusSaTokenDao.searchData` 的 5 秒 Caffeine 缓存导致的漏踢（详见报告 §4.4 / WARN-2）。把 `StaffGrantService.revoke` 里那行 `StpUtil.logout(...)` 注掉即可看到它变红 |

⚠️ 为什么把 `&&` 长链包进函数再判 rc：bash 的 `set -e` **不管** `a && b && c` 里非末尾位置的失败，
直接 `set -e; 长链` 会「静默短路 + exit 0」——标准假绿。三个脚本都按函数 + 判 rc 写。
