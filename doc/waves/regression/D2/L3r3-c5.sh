#!/usr/bin/env bash
# D2 r3 · L3 第 5 条：核验状态机 —— 缺内部编号 / 撞号 / 缺原因 → 被拒且库内不变；软删的内部编号可重用。
set -u
cd "$(dirname "$0")/../../../.."
DB() { python3 doc/verify/db.py "$@"; }
MD5() { DB --quiet --sql "SELECT md5(t.*::text) FROM t_lqg_sample t WHERE id=$1"; }
API() { bash doc/verify/api.sh "$@"; }

H0_1002=$(MD5 9000001002); H0_1001=$(MD5 9000001001)
echo "H0 1002=$H0_1002"
echo "H0 1001=$H0_1001"
echo "验前 1002: $(DB --quiet --sql "SELECT verify_status FROM t_lqg_sample WHERE id=9000001002")"

echo "--- 5a 判有效缺内部编号 ---"
API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01"}'
echo "--- 5b 判有效缺收样日期 ---"
API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","internalNo":"T-l3r3x"}'
echo "--- 5c 内部编号撞号（T-hli01 已属 1001）---"
API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-hli01"}'
echo "--- 5d 判无效缺原因 ---"
API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"invalid"}'

H1_1002=$(MD5 9000001002); H1_1001=$(MD5 9000001001)
echo "H1 1002=$H1_1002  相同=$([ "$H0_1002" = "$H1_1002" ] && echo yes || echo NO)"
echo "H1 1001=$H1_1001  相同=$([ "$H0_1001" = "$H1_1001" ] && echo yes || echo NO)"
echo "--- 库内不变断言 ---"
DB --sql "SELECT id,verify_status,coalesce(internal_no,'<空>'),coalesce(receive_date::text,'<空>'),coalesce(invalid_reason,'<空>') FROM t_lqg_sample WHERE id=9000001002"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001001 AND internal_no='T-hli01' AND verify_status='valid'"
echo "1001 仍是 T-hli01/valid exit=$?"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli01'"
echo "全库 T-hli01 行数（应 1，没被偷走/没第二条）exit=$?"

echo "--- 5e 软删编号重用：1010 = del_flag=1 | T-del99 ---"
DB --sql "SELECT id,del_flag,verify_status,internal_no FROM t_lqg_sample WHERE id=9000001010"
API --as admin --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-del99"}'
DB --sql "SELECT id,del_flag,verify_status,internal_no,receive_date,verify_by FROM t_lqg_sample WHERE id IN (9000001002,9000001010) ORDER BY id"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99' AND del_flag='0'"
echo "有效行 T-del99 恰好 1（部分唯一索引 uk_sample_internal_no WHERE del_flag='0'）exit=$?"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99'"
echo "全库 T-del99 行数（含软删行，应 2）exit=$?"
