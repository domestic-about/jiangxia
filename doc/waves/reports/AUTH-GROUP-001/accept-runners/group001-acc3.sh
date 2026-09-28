#!/usr/bin/env bash
# AUTH-GROUP-001 · accept 3（MENU）：对外单位选择器只含启用项且不带任何人数；
# 两个菜单落在 5100 段、可达、内部人员（--as staff = 102）也看得到。
#
# 与 ticket front-matter 的 run 逐字一致。
# 用法：bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc3.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

acc3() {
  bash doc/verify/api.sh --as extF GET /mp/ext/units | jq -e '.code==200 and ([.data[].unitName]|sort) == ["A 医院","B 大学"] and ([.data[] | select(.unitName=="A 医院") | .groups[].groupName]|sort) == ["消化内科组","肝胆外科组"] and ([.data[] | .. | objects | keys[]] | map(select(test("count|Count"))) | length == 0)' &&
  python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id IN (5120, 5130)" --col-set "5120:unit:lqg/auth/unit/index,5130:extuser:lqg/auth/extuser/index" &&
  bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/auth/extuser/index" or .component? == "lqg/auth/unit/index")] | length == 2'
}

if acc3; then
  echo "ACCEPT-3 EXIT=0"
else
  rc=$?
  echo "ACCEPT-3 FAILED rc=${rc}"
  exit 1
fi
