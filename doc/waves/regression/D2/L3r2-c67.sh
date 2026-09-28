#!/usr/bin/env bash
# D2 r2 · L3 第 6 条（接口侧）空数据：新外部用户（0 样本、未填组别）
#            + 第 7 条第三条（接口侧）外部类器官夹带内部编号 / 收样日期不生效
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
API="bash doc/verify/api.sh"; DB="python3 doc/verify/db.py"
NB="phone:r2new1:13800000097"

echo "== 第 6 条：新外部用户 $NB =="
$API --as "$NB" --bizcode POST /auth/login '{"grantType":"xcx"}'
echo "## /mp/me"
$API --as "$NB" --bizcode GET /mp/me
$API --as "$NB" GET /mp/me
echo "## 历史编辑记录数据源（外部三类）"
for P in "/mp/ext/sample/list?pageSize=20" "/mp/ext/organoid/list?pageSize=20" "/mp/ext/embed/list?pageSize=20" "/mp/ext/units"; do
  echo "-- GET $P"; $API --as "$NB" --bizcode GET "$P"; echo "(exit=$?)"
done
echo "## 库内 corroboration"
$DB --sql "SELECT count(*) FROM t_lqg_sample s JOIN sys_user u ON u.user_id=s.submitter_id WHERE u.phonenumber='13800000097'"
$DB --sql "SELECT bind_status,coalesce(unit_id::text,'-'),coalesce(group_id::text,'-'),coalesce(unit_name_input,'-'),coalesce(group_name_input,'-') FROM t_lqg_ext_profile p JOIN sys_user u ON u.user_id=p.user_id WHERE u.phonenumber='13800000097'"

echo
echo "== 第 7 条第三条：extC 外部类器官夹带 =="
N0=$($DB --sql "SELECT count(*) FROM t_lqg_sample")
echo "库内样本行数(前)=$N0"
$API --as extC --bizcode POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"L3R2类器官探针","remark":"L3R2类器官备注","internalNo":"T-L3R2ORG","receiveDate":"2026-01-02","verifyStatus":"valid","submitSource":"internal","isFixed":"Y"}'
echo "(exit=$?)"
N1=$($DB --sql "SELECT count(*) FROM t_lqg_sample")
echo "库内样本行数(后)=$N1"
$DB --sql "SELECT id,sample_kind,submit_source,verify_status,coalesce(internal_no,'-'),coalesce(receive_date::text,'-'),coalesce(is_fixed,'-'),coalesce(remark,'-') FROM t_lqg_sample WHERE organoid_type='L3R2类器官探针'"
echo -n "夹带不生效 + 服务端写死(应 1) = "
$DB --sql "SELECT count(*) FROM t_lqg_sample WHERE organoid_type='L3R2类器官探针' AND sample_kind='organoid' AND submit_source='external' AND verify_status='pending' AND coalesce(internal_no,'')='' AND receive_date IS NULL AND is_fixed IS NULL AND remark='L3R2类器官备注'"
echo -n "夹带内部编号全库计数(应 0) = "; $DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-L3R2ORG'"
