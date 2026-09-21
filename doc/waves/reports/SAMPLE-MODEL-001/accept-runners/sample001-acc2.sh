#!/usr/bin/env bash
# SAMPLE-MODEL-001 accept 2（DATA）—— ticket 原文逐字（去掉本沙箱跑不了的 --fresh-module）。
# 用法：cwd = 项目根，bash .tmp/sample001-acc2.sh
set -uo pipefail
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*        # 改库后必须清 token 缓存（api.sh 只按 mtime 判新鲜）
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  NEW="$(bash doc/verify/api.sh --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","donorName":"加密探针","gender":"male","age":"50","hospitalNo":"ZYPROBE01","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-probe01"}')" &&
  printf '%s' "${NEW}" | jq -e '.code==200' &&
  WANT="$(printf '%s' '加密探针' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A)" &&
  python3 doc/verify/db.py --sql "SELECT donor_name FROM t_lqg_sample WHERE internal_no='T-probe01'" --eq "${WANT}" &&
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?internalNo=T-probe01' | jq -e '.rows|length==1 and .[0].donorName=="加密探针" and .[0].hospitalNo=="ZYPROBE01" and .[0].submitSource=="internal" and .[0].verifyStatus=="valid" and (.[0].submitNo|test("^SJ[0-9]{8}$"))' &&
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93%E7%94%B2' | jq -e '[.rows[].id|tostring] == ["9000001001"]' &&
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93' | jq -e '.rows|length==0' &&
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' | jq -e '([.rows[].id|tostring] | index("9000001010")) == null and (.rows|length) == 10' &&
  bash doc/verify/reseed.sh --yes >/dev/null
}
run; rc=$?
echo "ACCEPT-2 EXIT=$rc"
exit "$rc"
