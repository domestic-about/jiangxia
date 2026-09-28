#!/usr/bin/env bash
# AUTH-GROUP-001 · accept 2（STATE）：核验状态机 —— 非法转移被拒且库里不变；自填必须二选一；
# 外部一改单位 / 组别立刻回到待核验。
#
# 与 ticket front-matter 的 run 逐字一致，只去掉本 agent 沙箱跑不了的 --fresh-module ruoyi-lqg（见完工报告 §4.0）。
# 用法：bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc2.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2

# token 缓存只看 mtime，陈旧 token 会造成假 401（AUTH-STAFF-001 WARN-4）
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

acc2() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve"}' | grep -qvE '^200' &&
  python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq pending &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/auth/ext-user/9000000115/verify '{"action":"reject"}' | grep -qvE '^200' &&
  bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","createUnit":true,"createGroup":true}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT p.bind_status || '|' || u.unit_name || '|' || u.unit_status || '|' || g.group_name FROM t_lqg_ext_profile p JOIN t_lqg_source_unit u ON u.id=p.unit_id JOIN t_lqg_unit_group g ON g.id=p.group_id AND g.unit_id=u.id WHERE p.user_id=9000000116" --eq "verified|C 研究所|active|肿瘤组" &&
  bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009102}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT bind_status || '|' || group_id || '|' || COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq "pending|9000009102|-" &&
  bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009103}' | grep -qvE '^200' &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

if acc2; then
  echo "ACCEPT-2 EXIT=0"
else
  rc=$?
  echo "ACCEPT-2 FAILED rc=${rc}"
  exit 1
fi
