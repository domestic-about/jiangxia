#!/usr/bin/env bash
# EMBED-MODEL-001 · accept 4（STATE · 外部送样核验状态机）实跑脚本。
#
# 与 ticket 的 accept.run **逐字相同**，唯一差异 = 去掉 `--fresh-module ruoyi-lqg`
# （沙箱恒 exit 1，理由与等价证据见完工报告 §4.0）。
#
# ★ 长链包进函数判**整条** rc；开头清 token 缓存。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  v() { bash doc/verify/api.sh --as staff --bizcode PUT "/lqg/embed/$1/verify" "$2"; } &&
  bash doc/verify/api.sh --as staff GET /lqg/sys/ping >/dev/null &&
  v 9000002006 '{"action":"valid","paraffinBlockNo":"T-E06-1"}' | grep -qE '^(400|500)' &&
  v 9000002006 '{"action":"invalid"}' | grep -qE '^(400|500)' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-9","sampleType":"被普通保存改掉"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') || '|' || COALESCE(verify_by::text,'-') || '|' || sample_type FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-|-|组织" &&
  bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli77"}' | jq -e '.code==200' &&
  v 9000002006 '{"action":"valid"}' | grep -qE '^(400|500)' &&
  v 9000002006 '{"action":"valid","paraffinBlockNo":"T-E01-1"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-" &&
  v 9000002006 '{"action":"valid","paraffinBlockNo":"T-E06-1"}' | grep -qE '^200' &&
  python3 doc/verify/db.py --sql "SELECT e.verify_status || '|' || e.paraffin_block_no || '|' || e.verify_by || '|' || e.submit_source || '|' || e.submitter_id || '|' || s.internal_no FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id WHERE e.id=9000002006" --eq "valid|T-E06-1|9000000101|external|9000000111|T-hli77" &&
  bash doc/verify/api.sh --as staff PUT /lqg/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-1","dehydrateTime":"2026-09-17","verifyStatus":"pending"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || dehydrate_time::text FROM t_lqg_embed WHERE id=9000002006" --eq "valid|2026-09-17" &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

if run; then
  echo "ACCEPT-4 EXIT=0"
  exit 0
fi
echo "ACCEPT-4 FAILED"
exit 1
