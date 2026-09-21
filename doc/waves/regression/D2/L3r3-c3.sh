#!/usr/bin/env bash
# D2 r3 · L3 第 3 条：内部经小程序 PUT —— 待核验/无效被拒且库内不变；改有效样本后 update_by 有值。
set -u
cd "$(dirname "$0")/../../../.."
DB() { python3 doc/verify/db.py "$@"; }
MD5() { DB --quiet --sql "SELECT md5(t.*::text) FROM t_lqg_sample t WHERE id=$1"; }
API() { bash doc/verify/api.sh "$@"; }

H0_1002=$(MD5 9000001002); H0_1003=$(MD5 9000001003); H0_1001=$(MD5 9000001001)
echo "H0 1002=$H0_1002"; echo "H0 1003=$H0_1003"; echo "H0 1001=$H0_1001"

echo "--- 3a --as staff PUT /mp/int/sample {\"id\":9000001002,…}（pending）---"
API --as staff --bizcode PUT /mp/int/sample '{"id":9000001002,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体乙","remark":"L3R3内部改待核验"}'
echo "--- 3b 同上 id=9000001003（invalid）---"
API --as staff --bizcode PUT /mp/int/sample '{"id":9000001003,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体丙","remark":"L3R3内部改无效"}'

H1_1002=$(MD5 9000001002); H1_1003=$(MD5 9000001003)
echo "H1 1002=$H1_1002  相同=$([ "$H0_1002" = "$H1_1002" ] && echo yes || echo NO)"
echo "H1 1003=$H1_1003  相同=$([ "$H0_1003" = "$H1_1003" ] && echo yes || echo NO)"
echo "--- 被拒 payload 是否落盘（应 0）---"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE remark IN ('L3R3内部改待核验','L3R3内部改无效')"
echo "落盘行数 exit=$?"
DB --sql "SELECT id,verify_status,coalesce(internal_no,'<空>'),submit_source,remark FROM t_lqg_sample WHERE id IN (9000001002,9000001003) ORDER BY id"

echo "--- 3c --as staff PUT /mp/int/sample {\"id\":9000001001,…}（valid，正向）---"
API --as staff --bizcode PUT /mp/int/sample '{"id":9000001001,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体甲","remark":"L3R3内部改有效"}'
echo "--- update_by 有值（应 1/exit0）---"
DB --sql "SELECT s.id,s.verify_status,s.remark,s.update_by,u.phonenumber FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.update_by WHERE s.id=9000001001"
DB --sql "SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.update_by WHERE s.id=9000001001 AND u.phonenumber='13800000001' AND s.remark='L3R3内部改有效'"
echo "update_by=staff 断言 exit=$?"
