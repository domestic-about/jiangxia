#!/usr/bin/env bash
# CRYO-MODEL-001 · accept 1（DDL）—— 与 ticket 的 `run` 逐字相同（本条本来就没有 --fresh-module）。
# 从工作区根或任意 cwd 执行均可。长链包进函数判**整条** rc（别丢给 set -e：非末尾位置失败会静默短路）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
run() {
  python3 doc/verify/ddl_vs_ssot.py --table t_lqg_cryo_batch --table t_lqg_cryo_flow --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260924120%__CRYO-MODEL-001-%'" --eq 1 &&
  python3 doc/verify/db.py --sql "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='t_lqg_cryo_batch' AND (column_name LIKE '%remain%' OR column_name LIKE '%current%' OR column_name LIKE '%stock%')" --empty
}
run; RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit ${RC}
