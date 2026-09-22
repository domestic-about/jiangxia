#!/usr/bin/env bash
# CRYO-REMIND-001 · accept 3（STATE，阈值参数化）—— 与 ticket 的 `run` 逐字相同。
#   本条的 run 里没有 --fresh-module，也没有 Maven，所以逐字可跑。
#   ★ 额外加了一条**收尾保险**（不改任何断言）：整条链跑完（无论 rc）都把
#     `lqg.cryo.overdue-days` 复位成 14 —— ticket 的提醒说「跑挂在中间要手工改回 14」，
#     这条 trap 就是那个手工步骤的自动化。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
C=""
restore() {
  local cur
  cur="$(bash doc/verify/api.sh --as admin GET '/system/config/list?configKey=lqg.cryo.overdue-days&pageSize=10' 2>/dev/null | jq -r '.rows[0].configValue' 2>/dev/null)"
  if [ "${cur}" != "14" ]; then
    bash doc/verify/api.sh --as admin PUT /system/config \
      "$(printf '%s' "${C}" | jq -c '. + {configValue:"14"}')" >/dev/null 2>&1 || true
    echo "[guard] 阈值已从 ${cur} 复位成 14"
  fi
}
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '([.data[].id|tostring]|sort)==["9000003001","9000003005"]' &&
  C="$(bash doc/verify/api.sh --as admin GET '/system/config/list?configKey=lqg.cryo.overdue-days&pageSize=10' | jq -e -c '.rows[0] | select(.configValue=="14") | {configId, configName, configKey, configType, remark}')" &&
  bash doc/verify/api.sh --as admin PUT /system/config "$(printf '%s' "${C}" | jq -c '. + {configValue:"13"}')" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '([.data[] | "\(.id|tostring):\(.overdueDays)"] | sort) == ["9000003001:7","9000003005:1","9000003006:0"]' &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '.tabCounts.overdue==3' &&
  python3 doc/verify/db.py --sql "SELECT b.id FROM t_lqg_cryo_batch b WHERE b.del_flag='0' AND b.in_minus80='Y' AND b.to_ln2_time IS NULL AND CURRENT_DATE - b.freeze_time >= (SELECT config_value::int FROM sys_config WHERE config_key='lqg.cryo.overdue-days') AND b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0) > 0" --col-set 9000003001,9000003005,9000003006 &&
  bash doc/verify/api.sh --as admin PUT /system/config "$(printf '%s' "${C}" | jq -c '. + {configValue:"14"}')" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '([.data[].id|tostring]|sort)==["9000003001","9000003005"]' &&
  python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.cryo.overdue-days'" --eq 14
}
run; RC=$?
restore
echo "ACCEPT-3 EXIT=${RC}"
exit ${RC}
