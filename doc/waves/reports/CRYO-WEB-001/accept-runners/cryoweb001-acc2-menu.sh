#!/usr/bin/env bash
# CRYO-WEB-001 · accept 2（MENU）—— 与 ticket 的 `run` **逐字相同**（这条 run 里本来就没有
# `--fresh-module`，也没有 Maven 段）。
# 长链包进函数判**整条** rc（`set -e` 不管 `&&` 链非末尾命令的失败）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5410" --eq "5410:cryo:lqg/cryo/index" &&
  bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/cryo/index")] | length == 1' &&
  bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '(.data.permissions | index("lqg:cryo:flow") != null) and (.data.permissions | index("lqg:cryo:export") != null)' &&
  test -f code/plus-ui/src/views/lqg/cryo/index.vue && grep -q 'tabCounts' code/plus-ui/src/views/lqg/cryo/index.vue &&
  grep -rqE 'updateFlow|editFlow' code/plus-ui/src/api/lqg/cryo && grep -rqE 'delFlow|deleteFlow|removeFlow' code/plus-ui/src/api/lqg/cryo &&
  ! grep -nE '<el-switch' code/plus-ui/src/views/lqg/cryo/*.vue
}
run; RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit ${RC}
