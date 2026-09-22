#!/usr/bin/env bash
# CRYO-FLOW-001 · accept 3（STATE）—— 与 ticket 的 `run` 逐字相同，唯一差异 = 去掉本 agent 沙箱
# 跑不了的 `--fresh-module ruoyi-lqg`（api.sh 第 71 行 `ps -o lstart=` 被禁；等价证据见完工报告 §4.0）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  for i in 1 2 3 4 5; do bash doc/verify/api.sh --as staff --bizcode POST /lqg/cryo/batch/9000003005/flow '{"flowType":"take","qty":2,"purpose":"并发"}' > "/tmp/lqg-race-${i}.txt" & done; wait &&
  test "$(cat /tmp/lqg-race-*.txt | grep -cE '^200')" = 1 &&
  python3 doc/verify/db.py --sql "SELECT b.init_qty + COALESCE(SUM(f.delta),0) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.id=9000003005 GROUP BY b.init_qty" --eq 0 &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003001/to-ln2 '{"toLn2Time":"2020-01-01","ln2Location":"1号罐"}' | grep -qE '^(400|500)' &&
  bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"3号罐-2架-C1"}))')" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003001 | jq -e '.data.location=="ln2" and .data.ln2Location=="3号罐-2架-C1"' &&
  rm -f /tmp/lqg-race-*.txt && bash doc/verify/reseed.sh --yes >/dev/null
}
run; RC=$?
echo "ACCEPT-3 EXIT=${RC}"
exit ${RC}
