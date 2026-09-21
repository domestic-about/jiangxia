#!/usr/bin/env bash
# D2 r2 · L3 第 2 条：外部 PUT 夹带 / 同组别人的样本 / 已有效的自己
# 用法：bash doc/waves/regression/D2/L3r2-c2.sh   （前置：8081 后端 + reseed）
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
API="bash doc/verify/api.sh"
DB="python3 doc/verify/db.py"

echo "## H0 1002 整行 md5"; $DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002"
echo "## 2a extA PUT 1002（合法字段 + 夹带 internalNo/verifyStatus/submitSource/receiveDate）"
BODY='{"id":9000001002,"sampleKind":"tissue","sourceUnitName":"A 医院","donorName":"测试供体乙","gender":"female","age":"48","hospitalNo":"ZY0000002","tissueType":"胆管组织","hasPathology":"N","remark":"L3R2夹带","internalNo":"T-L3R2HACK","verifyStatus":"valid","submitSource":"internal","receiveDate":"2026-01-01"}'
$API --as extA --bizcode PUT /mp/ext/sample/9000001002 "$BODY"; echo "(exit=$?)"
echo "## 2a 库内：夹带列不变 + legit 写入生效"
$DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001002 AND verify_status='pending' AND coalesce(internal_no,'')='' AND submit_source='external' AND receive_date IS NULL AND remark='L3R2夹带' AND has_pathology='N'"
echo "## 2b extB PUT 1001（同组别人的样本，可看不可改）"
$API --as extB --bizcode PUT /mp/ext/sample/9000001001 "$BODY"; echo "(exit=$?)"
echo "## 2c extA PUT 1001（自己的、已 valid）"
$API --as extA --bizcode PUT /mp/ext/sample/9000001001 "$BODY"; echo "(exit=$?)"
echo "## 2b/2c 库内：1001 一字未变"
$DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001001 AND verify_status='valid' AND internal_no='T-hli01' AND remark IS DISTINCT FROM 'L3R2夹带'"
echo "## 2d 可见性对照：extB 看得到 1001"
$API --as extB --bizcode GET /mp/ext/sample/9000001001
echo "## H1 1002 整行 md5（应 = H0 之外只多了 2a 的 legit 改动；再算一次留证据）"
$DB --sql "SELECT md5(t::text) FROM t_lqg_sample t WHERE id=9000001002"
echo "## 2a 后 1002 行"
$DB --sql "SELECT id,verify_status,coalesce(internal_no,'-'),submit_source,coalesce(receive_date::text,'-'),remark,coalesce(update_by::text,'-') FROM t_lqg_sample WHERE id=9000001002"
