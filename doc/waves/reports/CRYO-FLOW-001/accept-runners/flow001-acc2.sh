#!/usr/bin/env bash
# CRYO-FLOW-001 · accept 2（STATE）—— 与 ticket 的 `run` 逐字相同（本票 `run` 里本来就没有
# `--fresh-module`；api.sh 第 71 行 `ps -o lstart=` 在本 agent 沙箱被禁，见完工报告 §4.0）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/flow/9000003101 '{"qty":1,"purpose":"复苏培养（更正）"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT delta || '|' || purpose || '|' || from_location || '|' || del_flag || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_cryo_flow WHERE id=9000003101" --eq="-1|复苏培养（更正）|minus80|0|yes" &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003001/flow/9000003101 '{"qty":1,"flowType":"add","purpose":"改类型"}' | grep -qE '^(400|500)' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003002/flow/9000003101 '{"qty":1,"purpose":"跨批次"}' | grep -qE '^(400|404|500)' &&
  python3 doc/verify/db.py --sql "SELECT delta || '|' || purpose FROM t_lqg_cryo_flow WHERE id=9000003101" --eq="-1|复苏培养（更正）" &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch/9000003003/flow/9000003104 "$(python3 -c 'import datetime,json;print(json.dumps({"qty":6,"purpose":"药敏实验","flowTime":(datetime.datetime.now()-datetime.timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")}))')" | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT delta || '|' || (flow_time::date = CURRENT_DATE - 10) FROM t_lqg_cryo_flow WHERE id=9000003104" --eq="-3|true" &&
  bash doc/verify/api.sh --as staff DELETE /lqg/cryo/batch/9000003003/flow/9000003103 | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT del_flag FROM t_lqg_cryo_flow WHERE id=9000003103" --eq 1 &&
  bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003006/flow '{"flowType":"add","qty":2,"purpose":"同批补冻"}' | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003006/flow '{"flowType":"take","qty":7,"purpose":"全部取用"}' | jq -e '.code==200' &&
  A="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_cryo_flow WHERE batch_id=9000003006 AND flow_type='add' AND del_flag='0'" | head -1)" &&
  bash doc/verify/api.sh --as staff --bizcode DELETE "/lqg/cryo/batch/9000003006/flow/${A}" | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_flow WHERE batch_id=9000003006 AND del_flag='0'" --eq 2 &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '[.rows[]|select((.id|tostring)=="9000003001" or (.id|tostring)=="9000003003" or (.id|tostring)=="9000003006")|"\(.id|tostring):\(.remainingQty)"]|sort == ["9000003001:7","9000003003:2","9000003006:0"]' &&
  python3 doc/verify/db.py --sql "SELECT b.id || ':' || (b.init_qty + COALESCE(SUM(f.delta),0)) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.id IN (9000003001, 9000003003, 9000003006) GROUP BY b.id, b.init_qty" --col-set "9000003001:7,9000003003:2,9000003006:0" &&
  bash doc/verify/reseed.sh --yes >/dev/null
}
run; RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit ${RC}
