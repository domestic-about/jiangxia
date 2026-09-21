#!/usr/bin/env bash
# AUTH-STAFF-001 · accept 3 原文逐字（无 --fresh-module，原样）
set -e
acc3() {
python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5110" --eq "5110:staff:lqg/auth/staff/index" &&
python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5110" --col-set 101 &&
bash doc/verify/api.sh --as admin GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/auth/staff/index")] | length == 1' &&
test -f code/plus-ui/src/views/lqg/auth/staff/index.vue
}
if acc3; then echo "ACCEPT-3 EXIT=0"; else rc=$?; echo "ACCEPT-3 FAILED rc=$rc"; exit 1; fi
