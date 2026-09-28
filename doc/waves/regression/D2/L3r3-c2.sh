#!/usr/bin/env bash
# D2 r3 · L3 第 2 条（外部 PUT 夹带 / 同组可看不可改 / 已有效改不了 + 外部列表筛选隔离）
# 本片自写探针。探针值一律带 L3R3 前缀，避免与 r1/r2 残留混淆。
set -u
cd "$(dirname "$0")/../../../.."
OUT=.tmp/qa-r3
mkdir -p "$OUT"
DB() { python3 doc/verify/db.py "$@"; }
MD5() { DB --quiet --sql "SELECT md5(t.*::text) FROM t_lqg_sample t WHERE id=$1"; }
API() { bash doc/verify/api.sh "$@"; }
IDS() { jq -r '.rows[].id|tostring' "$1" 2>/dev/null | sort -n | paste -sd, - ; }

echo "########## A 夹带不生效 ##########"
H0_1002=$(MD5 9000001002)
echo "H0 1002 md5 = $H0_1002"
BODY='{"sourceUnitId":null,"sourceUnitName":"A 医院","donorName":"测试供体乙","gender":"male","age":"50","hospitalNo":"ZY0000002","tissueType":"肝组织","hasPathology":"N","remark":"L3R3夹带","internalNo":"T-L3R3HACK","verifyStatus":"valid","submitSource":"internal","receiveDate":"2026-01-01"}'
echo "--- 2a --as extA PUT /mp/ext/sample/9000001002（legit 全字段 + 夹带 4 键）---"
API --as extA --bizcode PUT /mp/ext/sample/9000001002 "$BODY"
echo "--- 2a 库内：夹带 4 列不生效 + legit 生效（应 1/exit0）---"
DB --sql "SELECT id,verify_status,coalesce(internal_no,'<空>'),submit_source,coalesce(receive_date::text,'<空>'),remark,has_pathology FROM t_lqg_sample WHERE id=9000001002"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001002 AND verify_status='pending' AND coalesce(internal_no,'')='' AND submit_source='external' AND receive_date IS NULL AND remark='L3R3夹带' AND has_pathology='N'"
echo "2a 库内不变/生效断言 exit=$?"

echo "--- 2b --as extB PUT /mp/ext/sample/9000001001（同组别人的 valid）---"
API --as extB --bizcode PUT /mp/ext/sample/9000001001 "$BODY"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001001 AND verify_status='valid' AND internal_no='T-hli01' AND remark IS DISTINCT FROM 'L3R3夹带'"
echo "2b 1001 未变 exit=$?"

echo "--- 2c --as extA PUT /mp/ext/sample/9000001001（自己的、已 valid）---"
API --as extA --bizcode PUT /mp/ext/sample/9000001001 "$BODY"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001001 AND verify_status='valid' AND internal_no='T-hli01' AND remark IS DISTINCT FROM 'L3R3夹带'"
echo "2c 1001 未变 exit=$?"

echo "--- 2d --as extB GET /mp/ext/sample/9000001001（同组可看）---"
API --as extB --bizcode GET /mp/ext/sample/9000001001

echo "--- 2a' --as extA PUT /mp/ext/sample/9000001003（自己的、invalid：状态允许，应 200 正向对照）---"
API --as extA --bizcode PUT /mp/ext/sample/9000001003 "$BODY"
DB --sql "SELECT id,verify_status,remark FROM t_lqg_sample WHERE id=9000001003"
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE id=9000001003 AND verify_status='invalid' AND coalesce(internal_no,'')='' AND submit_source='external' AND remark='L3R3夹带'"
echo "2a' invalid 本人可改 exit=$?"

echo "--- 2e 造一条 extA 的新 pending 样本（POST），再用 extB 改它 ---"
API --as extA --bizcode --out "$OUT/c2-new.json" POST /mp/ext/sample "$BODY"
NEW=$(jq -r '.data|tostring' "$OUT/c2-new.json" 2>/dev/null)
echo "新样本 id = $NEW"
HN0=$(MD5 "$NEW")
echo "H0 new md5 = $HN0"
API --as extB --bizcode PUT "/mp/ext/sample/$NEW" "$BODY"
HN1=$(MD5 "$NEW")
echo "H1 new md5 = $HN1  → 相同: $([ "$HN0" = "$HN1" ] && echo yes || echo NO)"
API --as extB --bizcode GET "/mp/ext/sample/$NEW"
printf '%s\n' "$NEW" > "$OUT/c2-newid.txt"

echo "########## B 外部列表筛选隔离（keyword / sourceUnitId 不在外部 BO 上）##########"
API --as extA --bizcode --out "$OUT/c2-list-base.json" GET "/mp/ext/sample/list?pageSize=20"
echo "base      total=$(jq -r .total "$OUT/c2-list-base.json") ids=$(IDS "$OUT/c2-list-base.json")"
API --as extA --bizcode --out "$OUT/c2-list-kw.json" GET "/mp/ext/sample/list?pageSize=20&keyword=T-hli01"
echo "keyword(内部编号 T-hli01) total=$(jq -r .total "$OUT/c2-list-kw.json") ids=$(IDS "$OUT/c2-list-kw.json")  ← 外部 BO 无此参数，应被忽略=base"
API --as extA --bizcode --out "$OUT/c2-list-su.json" GET "/mp/ext/sample/list?pageSize=20&sourceUnitId=1"
echo "sourceUnitId=1 total=$(jq -r .total "$OUT/c2-list-su.json") ids=$(IDS "$OUT/c2-list-su.json")  ← 同上应被忽略=base"
API --as extA --bizcode --out "$OUT/c2-list-sort.json" GET "/mp/ext/sample/list?pageSize=20&sort=recent"
echo "sort=recent total=$(jq -r .total "$OUT/c2-list-sort.json") ids=$(IDS "$OUT/c2-list-sort.json")  ← 外部口不受内部 sort 支影响"
API --as extA --bizcode --out "$OUT/c2-list-pending.json" GET "/mp/ext/sample/list?pageSize=20&verifyStatus=pending"
echo "verifyStatus=pending total=$(jq -r .total "$OUT/c2-list-pending.json") ids=$(IDS "$OUT/c2-list-pending.json")"
API --as extA --bizcode --out "$OUT/c2-list-mine.json" GET "/mp/ext/sample/list?pageSize=20&onlyMine=true"
echo "onlyMine=true total=$(jq -r .total "$OUT/c2-list-mine.json") ids=$(IDS "$OUT/c2-list-mine.json")"
API --as extA --bizcode --out "$OUT/c2-list-organoid.json" GET "/mp/ext/sample/list?pageSize=20&sampleKind=organoid"
echo "sampleKind=organoid total=$(jq -r .total "$OUT/c2-list-organoid.json") ids=$(IDS "$OUT/c2-list-organoid.json")"
API --as extC --bizcode --out "$OUT/c2-list-extC-kw.json" GET "/mp/ext/sample/list?pageSize=20&keyword=T-hli01"
echo "extC keyword(T-hli01) total=$(jq -r .total "$OUT/c2-list-extC-kw.json") ids=$(IDS "$OUT/c2-list-extC-kw.json")  ← 不得因别人的内部编号筛出东西"
