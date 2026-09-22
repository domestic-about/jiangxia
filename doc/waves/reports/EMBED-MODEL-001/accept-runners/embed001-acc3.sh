#!/usr/bin/env bash
# EMBED-MODEL-001 · accept 3（API）实跑脚本。
#
# 与 ticket 的 accept.run **逐字相同**，唯一差异 = 去掉详情那一行的 `--fresh-module ruoyi-lqg`
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
  bash doc/verify/api.sh --as staff GET /lqg/embed/9000002001 | jq -e '.data.stainTypes==["HE","IHC"] and .data.internalNo=="T-hli01" and ([.data.markers[]|"\(.markerName):\(.expression)"]|sort)==["CK19:negative","Ki67:strong"]' &&
  bash doc/verify/api.sh --as staff GET '/lqg/embed/list?pageSize=100' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002002","9000002003","9000002004","9000002006"] and (.rows[0].id|tostring)=="9000002006" and .rows[0].verifyStatus=="pending" and .rows[0].submitSource=="external" and .rows[0].paraffinBlockNo==null and .rows[0].submitNo=="SJ90000002" and .rows[0].sampleVerifyStatus=="pending"' &&
  bash doc/verify/api.sh --as staff GET '/lqg/embed/list?internalNo=T-hli01' | jq -e '(.rows|length)==2' &&
  bash doc/verify/api.sh --as staff GET '/lqg/embed/list?verifyStatus=pending' | jq -e '[.rows[].id|tostring]==["9000002006"]' &&
  bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=IHC' | jq -e '[.rows[].paraffinBlockNo]==["T-E01-1"]'
}

if run; then
  echo "ACCEPT-3 EXIT=0"
  exit 0
fi
echo "ACCEPT-3 FAILED"
exit 1
