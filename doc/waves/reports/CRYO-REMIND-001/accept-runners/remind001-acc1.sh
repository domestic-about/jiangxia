#!/usr/bin/env bash
# CRYO-REMIND-001 · accept 1（DATA）—— 与 ticket 的 `run` 逐字相同，唯一差异：
#   ① 去掉本 agent 沙箱恒非 0 的 `--fresh-module ruoyi-lqg`（等价反 stale 证据见
#      accept-runners/remind001-freshness.sh 与完工报告 §4.0）；
#   ② 末尾的 Maven 段补本机三参数（-s / -Dmaven.repo.local / -Duser.home，见 WARN「Maven 三参数」）。
# 长链包进函数判**整条** rc（别丢给 set -e：非末尾位置失败会静默短路成 exit 0）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
WS="$(pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq -e '([.data[] | "\(.id|tostring):\(.overdueDays)"] | sort) == ["9000003001:6","9000003005:0"]' &&
  python3 doc/verify/db.py --sql "SELECT b.id FROM t_lqg_cryo_batch b WHERE b.del_flag='0' AND b.in_minus80='Y' AND b.to_ln2_time IS NULL AND CURRENT_DATE - b.freeze_time >= (SELECT config_value::int FROM sys_config WHERE config_key='lqg.cryo.overdue-days') AND b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0) > 0" --col-set 9000003001,9000003005 &&
  LIST="$(bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100')" &&
  printf '%s' "${LIST}" | jq -e '.tabCounts == {"all":7,"overdue":2,"ln2":2} and ([.rows[]|select(.overdue)|.id|tostring]|sort)==["9000003001","9000003005"] and ([.rows[0:2][].overdue]==[true,true])' &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?overdueOnly=true&pageSize=100' | jq -e '(.rows|length)==2' &&
  (cd code/RuoYi-Vue-Plus && mvn -q -s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome" -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='CryoOverdue*Test' -Dsurefire.failIfNoSpecifiedTests=true)
}
run; RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit ${RC}
