#!/usr/bin/env bash
# SAMPLE-MODEL-001 accept 1（DDL）—— ticket 原文逐字；长链包进函数判整条 rc。
# 用法：cwd = 项目根，bash .tmp/sample001-acc1.sh
set -uo pipefail
run() {
  python3 doc/verify/ddl_vs_ssot.py --table t_lqg_sample --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260922100%__SAMPLE-MODEL-001-%'" --eq 1 &&
  grep -qi 'CREATE TABLE t_lqg_sample' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260922100*__SAMPLE-MODEL-001-*.sql &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM pg_sequences WHERE schemaname='public' AND sequencename='seq_lqg_submit_no'" --eq 1
}
run; rc=$?
echo "ACCEPT-1 EXIT=$rc"
exit "$rc"
