#!/usr/bin/env bash
# CRYO-MODEL-001 · accept 2（STATE）—— 与 ticket 的 `run` 逐字相同，唯一差异 = 去掉本 agent 沙箱
# 跑不了的 `--fresh-module ruoyi-lqg`（api.sh 第 71 行 `ps -o lstart=` 被禁；等价证据见完工报告 §4.0）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  post() { bash doc/verify/api.sh --as staff --bizcode POST /lqg/cryo/batch "$1"; } &&
  post '{"sampleId":9000001005,"cryoName":"T-BAD1","passage":"3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"cryoName":"T-BAD2","passage":"P3","freezeTime":"2026-09-17","initQty":0,"inMinus80":"Y"}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"cryoName":"T-BAD3","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"N"}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001002,"cryoName":"T-BAD4","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"cryoName":"T-BAD5","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y","toLn2Time":"2026-09-01","ln2Location":"1号罐"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_batch WHERE cryo_name LIKE 'T-BAD%'" --eq 0 &&
  post '{"sampleId":9000001005,"cryoName":"T-hga03-W-N-P12-EM2-1e5","passage":"P12","freezeTime":"2026-09-17","initQty":4,"density":"1e5","inMinus80":"Y","frozenBy":"李工"}' | grep -qE '^200' &&
  B="$(bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003001 | jq -c '.data | {id, sampleId, cryoName, passage, freezeTime, density, inMinus80, frozenBy}')" &&
  bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 10')" | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT init_qty || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_cryo_batch WHERE id=9000003001" --eq "10|yes" &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 1')" | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT init_qty FROM t_lqg_cryo_batch WHERE id=9000003001" --eq 10 &&
  bash doc/verify/api.sh --as staff --bizcode DELETE /lqg/cryo/batch/9000003001 | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT del_flag FROM t_lqg_cryo_batch WHERE id=9000003001" --eq 0 &&
  bash doc/verify/reseed.sh --yes >/dev/null
}
run; RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit ${RC}
