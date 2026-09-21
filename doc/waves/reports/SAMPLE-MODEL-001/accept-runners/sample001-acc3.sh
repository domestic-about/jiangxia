#!/usr/bin/env bash
# SAMPLE-MODEL-001 accept 3（STATE）—— ticket 原文逐字（去掉本沙箱跑不了的 --fresh-module）。
# 用法：cwd = 项目根，bash .tmp/sample001-acc3.sh
set -uo pipefail
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff --bizcode POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-hli01"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli01'" --eq 1 &&
  bash doc/verify/api.sh --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-del99"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT del_flag FROM t_lqg_sample WHERE internal_no='T-del99'" --col-set 0,1 &&
  bash doc/verify/api.sh --as staff --bizcode POST /lqg/sample '{"sampleKind":"organoid","sourceUnitName":"本中心","receiveDate":"2026-09-17","internalNo":"T-oco77"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-oco77'" --eq 0 &&
  bash doc/verify/api.sh --as extA GET '/mp/dict/hints?type=tissue' | jq -e '.code==200 and (.data|index("肝组织")!=null) and (.data|index("测试供体甲")==null)' &&
  bash doc/verify/reseed.sh --yes >/dev/null
}
run; rc=$?
echo "ACCEPT-3 EXIT=$rc"
exit "$rc"
