#!/usr/bin/env bash
# SAMPLE-WEB-001 accept 2（MENU）—— 原文照抄，无 `--fresh-module`。
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../.."
python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5210" --eq "5210:sample:lqg/sample/index" &&
python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5210" --col-set 101,102 &&
bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/index")] | length == 1' &&
python3 doc/verify/db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5210 AND menu_type = 'F'" --col-set "lqg:sample:list,lqg:sample:query,lqg:sample:add,lqg:sample:edit,lqg:sample:remove,lqg:sample:verify" &&
test -f code/plus-ui/src/components/lqg/SegButtons/index.vue &&
grep -c 'SegButtons' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue | awk '{exit !($1 >= 4)}' &&
! grep -nE '<el-switch|<el-select[^>]*(isFixed|hasQcSheet|hasViabilityReport|hasPathology)' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue
echo "SAMPLE-WEB-001 accept 2: PASS"
