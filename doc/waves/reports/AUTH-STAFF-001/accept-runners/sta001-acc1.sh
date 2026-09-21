#!/usr/bin/env bash
# AUTH-STAFF-001 · accept 1 原文逐字（唯一改动：去掉 `--fresh-module ruoyi-lqg`）
# 理由：本 subagent 沙箱里 `ps` 被禁 → doc/verify/api.sh 第 71 行 "/bin/ps: Operation not permitted" → exit 2。
# 等价替代证据（源码不比 jar 新 + 进程持有该 jar + 启动晚于 jar）见报告 §accept-1 与 .tmp/sta001-fresh.sh。
#
# ★ 为什么包成函数再判整体退出码：`set -e` **不会**对 `a && b && c` 里非末尾位置的失败生效
#   （bash 手册：the shell does not exit if the failing command is part of any command executed in
#   a `&&` or `||` list except the command following the final `&&` or `||`）——
#   直接 `set -e; a && b && c` 一旦中途失败会「静默短路 + 脚本 exit 0」，是标准的假绿。
set -e
acc1() {
bash doc/verify/reseed.sh --yes >/dev/null && rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extF-* &&
bash doc/verify/api.sh --as extF GET /mp/me | jq -e '.data.identity=="external"' &&
bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000016","name":"吴同学","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -e '.code==200 and .data.upgraded==true and (.data.userId|tostring)=="9000000116"' &&
python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM sys_user WHERE phonenumber='13800000016' AND del_flag='0') || '|' || (SELECT string_agg(r.role_key, ',' ORDER BY r.role_key) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=9000000116) || '|' || (SELECT count(*) FROM t_lqg_wx_bind WHERE user_id=9000000116 AND del_flag='0')" --eq "1|lqg_internal|1" &&
bash doc/verify/api.sh --as extF GET /mp/me | jq -e '.data.identity=="internal"' &&
bash doc/verify/api.sh --as admin DELETE /lqg/auth/staff/9000000116 | jq -e '.code==200' &&
bash doc/verify/api.sh --as extF --bizcode GET /mp/me | grep -qE '^401' &&
python3 doc/verify/db.py --sql "SELECT string_agg(r.role_key, ',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=9000000116" --eq "lqg_external" &&
bash doc/verify/api.sh --as admin --bizcode DELETE /lqg/auth/staff/9000000100 | grep -qvE '^200' &&
python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user_role WHERE user_id=9000000100 AND role_id=101" --eq 1 &&
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extF-* && bash doc/verify/reseed.sh --yes >/dev/null
}
if acc1; then echo "ACCEPT-1 EXIT=0"; else rc=$?; echo "ACCEPT-1 FAILED rc=$rc"; exit 1; fi
