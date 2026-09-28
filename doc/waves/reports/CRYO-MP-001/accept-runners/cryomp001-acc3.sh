#!/usr/bin/env bash
# CRYO-MP-001 · accept 3（STATE）—— ticket `run` 逐字，唯一偏差 = 去掉 `--fresh-module ruoyi-lqg`。
# 口径：超期是**读时算**的（CRYO-REMIND-001 的唯一判定），所以工作台一登记转液氮，
# 小程序这边的 `tabCounts.overdue` 与超期页签**当场**跟着变。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==2 and ([.rows[]|select(.overdue)|.id|tostring]|sort)==["9000003001","9000003005"]' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -e '([.rows[].id|tostring]|sort)==["9000003001","9000003005"]' &&
  bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"2号罐-1架-A1"}))')" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":1,"ln2":3} and ([.rows[]|select((.id|tostring)=="9000003001")|[.overdue,.location]]==[[false,"ln2"]])' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -e '([.rows[].id|tostring])==["9000003005"]' &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

run
rc=$?
echo "ACCEPT-3 EXIT=$rc"
exit "$rc"
