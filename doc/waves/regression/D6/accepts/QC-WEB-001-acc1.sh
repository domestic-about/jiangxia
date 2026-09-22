#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-WEB-001 accept[1] form=MENU
# 隐藏菜单可路由：path 与 component 非空、visible 为隐藏、路由下发里存在且标了 hidden；三个权限串下发到内部人员
set -euo pipefail
python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component || ':' || visible FROM sys_menu WHERE menu_id = 5510" --eq "5510:qc-editor:lqg/qc/editor/index:1" &&
bash doc/verify/api.sh --as staff GET /system/menu/getRouters | jq -e '[.data | .. | objects | select(.component? == "lqg/qc/editor/index")] | length == 1 and .[0].hidden == true' &&
bash doc/verify/api.sh --as staff GET /system/user/getInfo | jq -e '[.data.permissions[] | select(startswith("lqg:qc:"))] | sort == ["lqg:qc:edit","lqg:qc:publish","lqg:qc:query"]' &&
test -f code/plus-ui/src/views/lqg/qc/editor/index.vue
