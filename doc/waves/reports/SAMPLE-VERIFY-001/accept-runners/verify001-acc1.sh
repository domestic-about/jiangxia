#!/usr/bin/env bash
# SAMPLE-VERIFY-001 · accept 1（STATE）实跑脚本。
#
# 与 ticket 的 accept.run **逐字相同**，唯一差异 = 去掉 `--fresh-module ruoyi-lqg`：
# 本 subagent 沙箱里 api.sh 第 71 行的 `ps -o lstart=` 被禁（`Operation not permitted`）→ 恒 exit 2。
# 按规矩**没有改 api.sh**，等价反 stale 证据见完工报告 §4.0（源码不比 jar 新 + 进程持该 jar + 启动晚于 jar）。
#
# ★ 长链包进函数判**整条** rc：直接写一行 `a && b && c` 时，被 `if` / `&&` 包住的函数体会让
#   set -e 失效，非末尾失败会静默短路成 exit 0（AUTH-STAFF-001 坑 3）。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"

# 改库 / 改身份之后，api.sh 的 token 缓存（$TMPDIR/lqg-verify-token-*，20 分钟只看 mtime）必须先清，
# 否则会出现假 401 / 假 200。
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17"}' | grep -qE '^(400|500)' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli01"}' | grep -qE '^(400|500)' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001002/verify '{"action":"invalid"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(verify_by::text,'-') FROM t_lqg_sample WHERE id=9000001002" --eq "pending|-|-" &&
  bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli77","isFixed":"Y","operatorName":"李工"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || internal_no || '|' || verify_by || '|' || is_fixed || '|' || submit_source FROM t_lqg_sample WHERE id=9000001002" --eq "valid|T-hli77|9000000101|Y|external" &&
  bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"invalid","reason":"误判，退回"}' | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001003/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-hli78"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT id || ':' || verify_status || ':' || COALESCE(invalid_reason,'') FROM t_lqg_sample WHERE id IN (9000001002, 9000001003)" --col-set "9000001002:invalid:误判，退回,9000001003:valid:" &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

if run; then
  echo "ACCEPT-1 EXIT=0"
  exit 0
fi
echo "ACCEPT-1 FAILED"
exit 1
