#!/usr/bin/env bash
# D2 r2 · L3 第 3 条：内部经小程序 PUT（待核验/无效被拒且库内不变；改有效后 update_by 有值）
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
API="bash doc/verify/api.sh"; DB="python3 doc/verify/db.py"

H2=$($DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002")
H3=$($DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001003")
echo "H0 1002=$H2  H0 1003=$H3"

echo "## 3a staff PUT /mp/int/sample 1002（待核验）"
$API --as staff --bizcode PUT /mp/int/sample '{"id":9000001002,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体乙","gender":"female","age":"48","hospitalNo":"ZY0000002","tissueType":"胆管组织","hasPathology":"N","remark":"L3R2内部改待核验"}'; echo "(exit=$?)"
echo "## 3b staff PUT /mp/int/sample 1003（无效）"
$API --as staff --bizcode PUT /mp/int/sample '{"id":9000001003,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体丙","gender":"male","age":"50","hospitalNo":"ZY0000003","tissueType":"肝组织","hasPathology":"N","remark":"L3R2内部改无效"}'; echo "(exit=$?)"

H2b=$($DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002")
H3b=$($DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001003")
echo "H1 1002=$H2b 1003=$H3b"
[ "$H2" = "$H2b" ] && echo "PASS 1002 整行未变" || echo "FAIL 1002 变了"
[ "$H3" = "$H3b" ] && echo "PASS 1003 整行未变" || echo "FAIL 1003 变了"
echo -n "被拒 payload 落库计数(应 0) = "; $DB --sql "SELECT count(*) FROM t_lqg_sample WHERE remark IN ('L3R2内部改待核验','L3R2内部改无效')"

echo "## 3c staff PUT /mp/int/sample 1001（有效）"
$API --as staff --bizcode PUT /mp/int/sample '{"id":9000001001,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体甲","gender":"male","age":"45","hospitalNo":"ZY0000001","tissueType":"肝组织","hasPathology":"Y","remark":"L3R2内部改有效"}'; echo "(exit=$?)"
echo -n "update_by=staff 且 remark 落库(应 1) = "
$DB --sql "SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.update_by WHERE s.id=9000001001 AND u.phonenumber='13800000001' AND s.remark='L3R2内部改有效'"
$DB --sql "SELECT id,verify_status,internal_no,remark,update_by FROM t_lqg_sample WHERE id=9000001001"
