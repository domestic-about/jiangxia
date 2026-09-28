#!/usr/bin/env bash
# SAMPLE-WEB-001 · accept 1（API）逐段实跑器。
#
# 与 ticket 的 run 逐字一致，只有两处**沙箱必须**的偏差，都在报告里单列：
#   1. 去掉 `--fresh-module ruoyi-lqg`：本沙箱 `ps` 被禁 → api.sh 恒 exit 2（SYS-WEB-001 / SAMPLE-MP-001
#      等 7+ 张票的既有 WARN-5），用等价手工证据替代（见 accept-transcript.txt 的 §0 段）。
#   2. Maven 那一行（本票 accept 里没有）不涉及。
#
# ★ 长链包进函数判整条 rc：`a && b && c` 在 `set -e` 下不管非末尾失败，
#   会静默短路 exit 0（SAMPLE-MP-001 报告的纪律）。
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2
ROOT="$(pwd)"
echo "ROOT=${ROOT}"

q() { bash doc/verify/api.sh --as staff GET "/lqg/sample/list?pageSize=100&$1" | jq -c '[.rows[].id|tostring]|sort'; }

run_accept1() {
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed FAILED"; return 1; }

  echo "---- 1) sourceUnitId=9000009001"
  local got
  got="$(q 'sourceUnitId=9000009001')"; echo "$got"
  [ "$got" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001007"]' ] || { echo "RED 1"; return 1; }

  echo "---- 2) groupId=9000009101"
  got="$(q 'groupId=9000009101')"; echo "$got"
  [ "$got" = '["9000001001","9000001002","9000001003","9000001004","9000001007"]' ] || { echo "RED 2"; return 1; }

  echo "---- 3) sampleKind=organoid"
  got="$(q 'sampleKind=organoid')"; echo "$got"
  [ "$got" = '["9000001009"]' ] || { echo "RED 3"; return 1; }

  echo "---- 4) submitSource=internal"
  got="$(q 'submitSource=internal')"; echo "$got"
  [ "$got" = '["9000001008","9000001009"]' ] || { echo "RED 4"; return 1; }

  echo "---- 5) verifyStatus=pending"
  got="$(q 'verifyStatus=pending')"; echo "$got"
  [ "$got" = '["9000001002","9000001007"]' ] || { echo "RED 5"; return 1; }

  echo "---- 6) receiveDate 区间"
  local B E
  B="$(python3 -c 'import datetime;print(datetime.date.today()-datetime.timedelta(days=26))')"
  E="$(python3 -c 'import datetime;print(datetime.date.today()-datetime.timedelta(days=19))')"
  echo "B=${B} E=${E}"
  got="$(q "receiveDateBegin=${B}&receiveDateEnd=${E}")"; echo "$got"
  [ "$got" = '["9000001004","9000001005"]' ] || { echo "RED 6"; return 1; }

  echo "---- 7) 三条件 AND"
  got="$(q 'sourceUnitId=9000009001&verifyStatus=valid&submitSource=external')"; echo "$got"
  [ "$got" = '["9000001001","9000001004","9000001005"]' ] || { echo "RED 7"; return 1; }

  echo "---- 8) 全量（软删 1010 不出现）"
  got="$(q '')"; echo "$got"
  [ "$got" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001006","9000001007","9000001008","9000001009"]' ] || { echo "RED 8"; return 1; }

  echo "---- 9) 待核验置顶（pageSize=2）"
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=2' | jq -e '[.rows[].verifyStatus] == ["pending","pending"] and .total == 9' || { echo "RED 9"; return 1; }

  echo "---- 10) 待核验行浅黄底之外的表形状抽样（submitterName / groupName 读时带出）"
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100&sourceUnitId=9000009001' \
    | jq -e '[.rows[] | select(.id == 9000001001)][0] | (.submitterName == "王医生") and (.groupName == "肝胆外科组") and (.submitterId == 9000000111)' || { echo "RED 10"; return 1; }

  return 0
}

run_accept1
rc=$?
if [ "${rc}" -eq 0 ]; then
  echo "ACCEPT-1 GREEN (exit 0)"
else
  echo "ACCEPT-1 RED (exit ${rc})"
fi
exit "${rc}"
