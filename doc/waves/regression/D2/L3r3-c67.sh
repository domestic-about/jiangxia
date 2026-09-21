#!/usr/bin/env bash
# D2 r3 · L3 第 6 条（空数据：新外部用户首页/历史记录数据源不报错）
#          + 第 7 条接口侧（外部类器官入参夹带内部编号 / 收样日期不生效）
set -u
cd "$(dirname "$0")/../../../.."
OUT=.tmp/qa-r3
mkdir -p "$OUT"
DB() { python3 doc/verify/db.py "$@"; }
API() { bash doc/verify/api.sh "$@"; }
PHONE=13800000096
IDENT="phone:r3new1:${PHONE}"

echo "########## 6 空数据：新外部用户 ${PHONE}（首登自动建号）##########"
API --as "$IDENT" --bizcode --out "$OUT/c6-me.json" GET /mp/me
echo "GET /mp/me  → $(cat "$OUT/c6-me.json")"
echo "identity = $(jq -r '.data.identity' "$OUT/c6-me.json")"
echo "ext      = $(jq -c '.data.ext' "$OUT/c6-me.json")"
API --as "$IDENT" --bizcode --out "$OUT/c6-list.json" GET "/mp/ext/sample/list?pageSize=20"
echo "GET /mp/ext/sample/list       → total=$(jq -r '.total' "$OUT/c6-list.json") rows=$(jq -c '.rows' "$OUT/c6-list.json")"
API --as "$IDENT" --bizcode --out "$OUT/c6-org.json" GET "/mp/ext/sample/list?sampleKind=organoid&pageSize=20"
echo "  ?sampleKind=organoid        → code=$(jq -r '.code' "$OUT/c6-org.json") msg=$(jq -r '.msg' "$OUT/c6-org.json") total=$(jq -r '.total' "$OUT/c6-org.json")"
API --as "$IDENT" --bizcode --out "$OUT/c6-emb.json" GET "/mp/ext/sample/list?sampleKind=embed&pageSize=20"
echo "  ?sampleKind=embed           → code=$(jq -r '.code' "$OUT/c6-emb.json") msg=$(jq -r '.msg' "$OUT/c6-emb.json") total=$(jq -r '.total' "$OUT/c6-emb.json")"
API --as "$IDENT" --bizcode --out "$OUT/c6-units.json" GET /mp/ext/units
echo "GET /mp/ext/units             → $(jq -r '"\(.code) \(.msg)"' "$OUT/c6-units.json")"
echo "--- 库内 corroboration ---"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE create_by=(SELECT user_id FROM sys_user WHERE phonenumber='${PHONE}')"
echo "该号样本数（应 0）exit=$?"
DB --sql "SELECT bind_status,coalesce(unit_id::text,'-'),coalesce(group_id::text,'-'),coalesce(unit_name_input,'-'),coalesce(group_name_input,'-') FROM t_lqg_ext_profile WHERE user_id=(SELECT user_id FROM sys_user WHERE phonenumber='${PHONE}')"
echo "（应 unbound|-|-|-|-）exit=$?"

echo
echo "########## 7b 外部类器官入参夹带 ##########"
N0=$(DB --quiet --sql "SELECT count(*) FROM t_lqg_sample WHERE sample_kind='organoid'")
echo "验前 organoid 行数 = $N0"
API --as extC --bizcode --out "$OUT/c7-org.json" POST /mp/ext/organoid \
  '{"sourceUnitName":"A 医院","organoidType":"L3R3类器官探针","remark":"L3R3类器官备注","internalNo":"T-L3R3ORG","receiveDate":"2026-01-02","verifyStatus":"valid","submitSource":"internal","isFixed":"Y"}'
echo "POST /mp/ext/organoid → $(jq -r '"\(.code) \(.msg)"' "$OUT/c7-org.json")  id=$(jq -r '.data' "$OUT/c7-org.json")"
N1=$(DB --quiet --sql "SELECT count(*) FROM t_lqg_sample WHERE sample_kind='organoid'")
echo "验后 organoid 行数 = ${N1} 应 $((N0+1))（确实建了行 → 夹带是被忽略不是整体被拒）"
echo "--- 新行字段 ---"
DB --sql "SELECT id,sample_kind,submit_source,verify_status,coalesce(internal_no,'<空>'),coalesce(receive_date::text,'<空>'),coalesce(is_fixed,'<空>'),remark FROM t_lqg_sample WHERE remark='L3R3类器官备注'"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE organoid_type='L3R3类器官探针' AND sample_kind='organoid' AND submit_source='external' AND verify_status='pending' AND coalesce(internal_no,'')='' AND receive_date IS NULL AND is_fixed IS NULL AND remark='L3R3类器官备注'"
echo "夹带 4 列不生效断言 exit=$?"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-L3R3ORG'"
echo "全库 T-L3R3ORG 行数（应 0）exit=$?"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE receive_date='2026-01-02'"
echo "全库 receive_date=2026-01-02 行数（应 0）exit=$?"
