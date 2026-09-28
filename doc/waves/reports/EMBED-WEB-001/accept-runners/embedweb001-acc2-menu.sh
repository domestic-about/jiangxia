#!/usr/bin/env bash
# EMBED-WEB-001 · accept 2（MENU · 菜单 5310 段 + 页面接核验接口 + 染色 fixture + 样本总表入口）
#
# 与 ticket 的 `run` 逐字相同（本票两条 run 里没有 --fresh-module，也没有 Maven）。
# 长链包进函数判**整条** rc。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$ROOT"

run_accept2() {
  python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5310" --eq "5310:embed:lqg/embed/index" &&
  python3 doc/verify/db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5310 AND menu_type = 'F'" --col-set "lqg:embed:list,lqg:embed:query,lqg:embed:add,lqg:embed:edit,lqg:embed:remove,lqg:embed:export,lqg:embed:verify" &&
  bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/embed/index")] | length == 1' &&
  cd code/plus-ui && grep -q 'doc/verify/fixtures/stain-toggle-cases.json' src/views/lqg/embed/stain.fixture.spec.ts &&
  ! grep -nE '\.(skip|todo|only)\(' src/views/lqg/embed/stain.fixture.spec.ts &&
  pnpm vitest run src/views/lqg/embed/stain.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-stain-web.json >/dev/null &&
  jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-stain-web.json &&
  grep -q "sampleId" src/views/lqg/embed/index.vue && grep -rqE '/verify' src/api/lqg/embed/ && grep -rq 'sampleVerifyStatus' src/views/lqg/embed/
}
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true
run_accept2
echo "ACCEPT-2 EXIT=$?"
