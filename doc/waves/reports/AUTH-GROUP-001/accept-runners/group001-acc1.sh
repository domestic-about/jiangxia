#!/usr/bin/env bash
# AUTH-GROUP-001 · accept 1（DDL）：两张表与 SSOT 逐列相符、出自本票 Flyway、组别唯一索引带 unit_id + 部分条件
#
# 与 ticket front-matter 的 run 逐字一致，只去掉本 agent 沙箱跑不了的 --fresh-module（见完工报告 §4.0）。
# 用法：bash doc/waves/reports/AUTH-GROUP-001/accept-runners/group001-acc1.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 2

acc1() {
  python3 doc/verify/ddl_vs_ssot.py --table t_lqg_source_unit --table t_lqg_unit_group \
      --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260921093%__AUTH-GROUP-001-%'" --eq 1 &&
  python3 doc/verify/db.py --sql "SELECT indexdef LIKE '%(unit_id, group_name)%' AND indexdef LIKE '%WHERE (del_flag = ''0''%' FROM pg_indexes WHERE indexname = 'uk_unit_group'" --eq True
}

if acc1; then
  echo "ACCEPT-1 EXIT=0"
else
  rc=$?
  echo "ACCEPT-1 FAILED rc=${rc}"
  exit 1
fi
