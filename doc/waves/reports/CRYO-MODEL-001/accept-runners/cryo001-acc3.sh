#!/usr/bin/env bash
# CRYO-MODEL-001 · accept 3（DATA）—— 与 ticket 的 `run` 逐字相同，唯一差异 = 去掉本 agent 沙箱
# 跑不了的 `--fresh-module ruoyi-lqg`（等价证据见完工报告 §4.0）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  API="$(bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -r '[.rows[] | "\(.id|tostring):\(.remainingQty)"] | sort | join(",")')" &&
  test "${API}" = "9000003001:6,9000003002:4,9000003003:4,9000003004:0,9000003005:2,9000003006:5,9000003007:5" &&
  python3 doc/verify/db.py --sql "SELECT b.id || ':' || (b.init_qty + COALESCE(SUM(f.delta),0)) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.del_flag='0' GROUP BY b.id, b.init_qty" --col-set "${API}" &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '([.rows[]|select(.location=="ln2")|.id|tostring]|sort)==["9000003003","9000003007"] and ([.rows[]|select((.id|tostring)=="9000003001")|.internalNo]==["T-hli01"])'
}
run; RC=$?
echo "ACCEPT-3 EXIT=${RC}"
exit ${RC}
