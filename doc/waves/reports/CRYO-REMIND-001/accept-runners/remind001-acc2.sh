#!/usr/bin/env bash
# CRYO-REMIND-001 · accept 2（STATE）—— 与 ticket 的 `run` 逐字相同，唯一差异 =
#   去掉本 agent 沙箱恒非 0 的 `--fresh-module ruoyi-lqg`（等价反 stale 证据见
#   accept-runners/remind001-freshness.sh 与完工报告 §4.0）。
#   ★ `--as extA --bizcode GET /lqg/cryo/overdue | grep -qE '^403'` 这一段**照原文跑**：
#     ticket 在这里本来就带了 `--bizcode`（变体是 `code<TAB>msg`，首字符是 `4`），所以这条断言
#     成立；issue #155 的缺陷形态是「不带 --bizcode 却 grep ^200/^(400|500)」——本票没有那种写法。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==2' &&
  bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch/9000003001/to-ln2 "$(python3 -c 'import datetime,json;print(json.dumps({"toLn2Time":str(datetime.date.today()),"ln2Location":"2号罐-1架-A1"}))')" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '[.data[].id|tostring]==["9000003005"]' &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==1 and ([.rows[]|select((.id|tostring)=="9000003001")|.overdue]==[false])' &&
  bash doc/verify/api.sh --as staff POST /lqg/cryo/batch/9000003005/flow '{"flowType":"take","qty":2,"purpose":"全部取用"}' | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '.data==[]' &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==0' &&
  bash doc/verify/api.sh --as extA --bizcode GET /lqg/cryo/overdue | grep -qE '^403' &&
  test "$(grep -rlE 'freeze_time|freezeTime' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg --include=*.java | xargs grep -lE '\b14\b|OVERDUE_DAYS' | grep -v '/cryo/remind/' | wc -l | tr -d ' ')" = 0 &&
  bash doc/verify/reseed.sh --yes >/dev/null
}
run; RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit ${RC}
