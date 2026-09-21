#!/usr/bin/env bash
# SAMPLE-VERIFY-001 · accept 2（DATA）实跑脚本。
#
# 与 ticket 的 accept.run **逐字相同**，唯一差异 = 去掉 `--fresh-module ruoyi-lqg`（同 acc1 的理由）。
#
# ★ 这条断的是「submit_source 是提交当时的快照」：一侧是 AUTH-STAFF-001 的授权接口改掉的角色，
#   一侧是样本表里**早先**落下的那一列。两次删 extA 的 token 缓存是为了让升级后的身份真的生效
#   （api.sh 的 token 缓存 20 分钟只看 mtime）。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000011","name":"王医生","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -e '.data.upgraded==true' &&
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extA-* &&
  bash doc/verify/api.sh --as extA POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-snap01"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT submit_source || ':' || count(*) FROM t_lqg_sample WHERE submitter_id=9000000111 AND del_flag='0' GROUP BY submit_source" --col-set "external:3,internal:1" &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample s JOIN sys_user_role ur ON ur.user_id = s.submitter_id JOIN sys_role r ON r.role_id = ur.role_id WHERE s.submitter_id=9000000111 AND s.del_flag='0' AND r.role_key='lqg_internal' AND s.submit_source='external'" --eq 3 &&
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?submitSource=external&pageSize=100' | jq -e '[.rows[].id|tostring] | index("9000001001") != null' &&
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extA-* && bash doc/verify/reseed.sh --yes >/dev/null
}

if run; then
  echo "ACCEPT-2 EXIT=0"
  exit 0
fi
echo "ACCEPT-2 FAILED"
exit 1
