#!/usr/bin/env bash
# SAMPLE-WEB-001 · accept 2（MENU）逐段实跑器。
#
# 与 ticket 的 run 逐字一致（本票 accept 2 里没有 --fresh-module / Maven）。
# 长链包进函数判整条 rc（set -e 不管 `a && b && c` 的非末尾失败）。
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2
echo "ROOT=$(pwd)"

run_accept2() {
  echo "---- 1) 菜单 5210 落在 5200 段（C，path/component 逐字）"
  python3 doc/verify/db.py --sql "SELECT menu_id || ':' || path || ':' || component FROM sys_menu WHERE menu_id = 5210" --eq "5210:sample:lqg/sample/index" || return 1

  echo "---- 2) 授给 101 与 102"
  python3 doc/verify/db.py --sql "SELECT role_id FROM sys_role_menu WHERE menu_id = 5210" --col-set 101,102 || return 1

  echo "---- 3) getRouters（--as staff）里 lqg/sample/index 恰好 1 条"
  bash doc/verify/api.sh --as staff GET /system/menu/getRouters \
    | jq -e '[.data | .. | objects | select(.component? == "lqg/sample/index")] | length == 1' || return 1

  echo "---- 4) 5210 下的按钮权限串集合"
  python3 doc/verify/db.py --sql "SELECT perms FROM sys_menu WHERE parent_id = 5210 AND menu_type = 'F'" \
    --col-set "lqg:sample:list,lqg:sample:query,lqg:sample:add,lqg:sample:edit,lqg:sample:remove,lqg:sample:verify" || return 1

  echo "---- 5) 公共按钮组组件在盘上"
  test -f code/plus-ui/src/components/lqg/SegButtons/index.vue || return 1

  echo "---- 6) SampleDrawer.vue 里 SegButtons 出现 ≥4 次"
  grep -c 'SegButtons' code/plus-ui/src/views/lqg/sample/SampleDrawer.vue | awk '{exit !($1 >= 4)}' || return 1

  echo "---- 7) 四个「有无」按钮字段不许用 el-switch / el-select"
  ! grep -nE '<el-switch|<el-select[^>]*(isFixed|hasQcSheet|hasViabilityReport|hasPathology)' \
      code/plus-ui/src/views/lqg/sample/SampleDrawer.vue || return 1

  return 0
}

run_accept2
rc=$?
if [ "${rc}" -eq 0 ]; then
  echo "ACCEPT-2 GREEN (exit 0)"
else
  echo "ACCEPT-2 RED (exit ${rc})"
fi
exit "${rc}"
