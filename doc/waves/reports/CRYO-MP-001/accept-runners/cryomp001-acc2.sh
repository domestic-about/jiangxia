#!/usr/bin/env bash
# CRYO-MP-001 · accept 2（STATE）—— ticket `run` 逐字，唯一偏差 = 去掉 `--fresh-module ruoyi-lqg`。
#
# ★ issue #155 那类段落：本条的 `grep -qE '^(404|405)'` / `'^403'` **本来就带 `--bizcode`**
#   （输出是 `code<TAB>msg`，首字符是业务码），所以照原文跑就是对的，不需要改成 jq。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET /mp/int/cryo/batch/9000003003/flows | jq -e '.code==200 and ([.data[].id|tostring])==["9000003104","9000003103","9000003102"] and ([.data[].balanceAfter])==[4,7,5]' &&
  bash doc/verify/api.sh --as staff --bizcode POST /mp/int/cryo/batch/9000003002/flow '{"flowType":"take","qty":1,"purpose":"小程序取走"}' | grep -qE '^(404|405)' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch/9000003001/flow/9000003101 '{"qty":1,"purpose":"小程序改登记"}' | grep -qE '^(404|405)' &&
  bash doc/verify/api.sh --as staff --bizcode DELETE /mp/int/cryo/batch/9000003001/flow/9000003101 | grep -qE '^(404|405)' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch/9000003001/to-ln2 '{"toLn2Time":"2026-09-17","ln2Location":"1号罐"}' | grep -qE '^(404|405)' &&
  python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_cryo_flow WHERE del_flag='0') || '|' || (SELECT delta FROM t_lqg_cryo_flow WHERE id=9000003101) || '|' || (SELECT COALESCE(to_ln2_time::text,'-') FROM t_lqg_cryo_batch WHERE id=9000003001)" --eq="5|-2|-" &&
  bash doc/verify/api.sh --as extA --bizcode GET /mp/int/cryo/batch/9000003001/flows | grep -qE '^403' &&
  (cd code/miniapp && ! grep -rnE 'flowType|to-ln2|FlowSheet' src/components/lqg/CryoBatchSheet.vue src/api/cryo.ts src/pages/ledger) &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

run
rc=$?
echo "ACCEPT-2 EXIT=$rc"
exit "$rc"
