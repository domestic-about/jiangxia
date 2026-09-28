#!/usr/bin/env bash
# SAMPLE-EXPORT-001 · accept 2（MENU：5217 按钮权限出自本票迁移 + 5210 path + getRouters 恰 1 条
#   + 页面上两个导出按钮接上并带筛选条件）。
#
#   bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-acc2-menu.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."

acc2() {
  python3 doc/verify/db.py --sql "SELECT menu_id || ':' || perms || ':' || parent_id FROM sys_menu WHERE perms = 'lqg:sample:export'" --eq "5217:lqg:sample:export:5210" &&
  python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5217" --col-set 101,102 &&
  python3 doc/verify/db.py --sql "SELECT path FROM sys_menu WHERE menu_id = 5210" --eq sample &&
  bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/index")] | length == 1' &&
  bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '.data.permissions | index("lqg:sample:export") != null' &&
  grep -q 'export/tissue' code/plus-ui/src/api/lqg/sample/export.ts && grep -q 'export/organoid' code/plus-ui/src/api/lqg/sample/export.ts &&
  grep -cE 'exportTissue|exportOrganoid' code/plus-ui/src/views/lqg/sample/index.vue | awk '{exit !($1 >= 2)}'
}
acc2 2>&1
rc=$?
if [ "${rc}" -eq 0 ]; then
  echo "ACCEPT-2 EXIT=0"
else
  echo "ACCEPT-2 EXIT=${rc}  ← 失败（上面最后一段的输出就是断点）"
fi
exit "${rc}"
