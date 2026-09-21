#!/usr/bin/env bash
# D2 r2 · L3 第 5 条：核验状态机（判有效缺内部编号 / 编号撞号 / 判无效缺原因 → 拒且库内不变；软删编号可重用）
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
API="bash doc/verify/api.sh"; DB="python3 doc/verify/db.py"
H0=$($DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002")
echo "H0 1002=$H0"
echo "## 5a 判有效缺内部编号"
$API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01"}'; echo "(exit=$?)"
echo "## 5b 判有效缺收样日期"
$API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","internalNo":"T-l3r2x"}'; echo "(exit=$?)"
echo "## 5c 内部编号撞号（T-hli01 属于 1001）"
$API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-hli01"}'; echo "(exit=$?)"
echo "## 5d 判无效缺原因"
$API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"invalid"}'; echo "(exit=$?)"
H1=$($DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002")
echo "H1 1002=$H1"; [ "$H0" = "$H1" ] && echo "PASS 库内不变" || echo "FAIL 库内被改"
echo -n "1001 的 T-hli01 未被偷走(应 1) = "; $DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001001 AND internal_no='T-hli01' AND verify_status='valid'"
echo -n "T-hli01 全库唯一(应 1) = "; $DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli01'"
echo "## 5e 软删编号重用：1010(del_flag=1) 持有 T-del99"
$DB --sql "SELECT id,del_flag,internal_no,verify_status FROM t_lqg_sample WHERE id=9000001010"
$API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-del99"}'; echo "(exit=$?)"
$DB --sql "SELECT id,del_flag,internal_no,verify_status,receive_date,verify_by FROM t_lqg_sample WHERE id IN (9000001002,9000001010)"
echo -n "未删且 T-del99 的唯一行数(应 1) = "; $DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99' AND del_flag='0'"
echo -n "库内重复编号总数(应 1) = "; $DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99'"
