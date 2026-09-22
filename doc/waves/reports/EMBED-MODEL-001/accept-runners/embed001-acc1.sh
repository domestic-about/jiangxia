#!/usr/bin/env bash
# EMBED-MODEL-001 · accept 1（DDL）实跑脚本。
#
# 与 ticket 的 accept.run **逐字相同**（本条没有 `--fresh-module`，无需任何替换）。
#
# ★ 长链包进函数判**整条** rc：直接写一行 `a && b && c` 时，`set -e` 不管非末尾位置的失败，
#   会静默短路成 exit 0（AUTH-STAFF-001 坑 3）。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"

run() {
  python3 doc/verify/ddl_vs_ssot.py --table t_lqg_embed --table t_lqg_embed_marker --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM flyway_schema_history WHERE success AND script LIKE 'V20260923110%__EMBED-MODEL-001-%'" --eq 1 &&
  grep -qi 'CREATE TABLE t_lqg_embed_marker' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260923110*__EMBED-MODEL-001-*.sql
}

if run; then
  echo "ACCEPT-1 EXIT=0"
  exit 0
fi
echo "ACCEPT-1 FAILED"
exit 1
