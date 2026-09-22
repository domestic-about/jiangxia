#!/usr/bin/env bash
# D2 r3 · L3 第 1 条（越权取详情）——本片自写探针，不复用 r1/r2 脚本。
# extC（同单位异组）猜 id 取 1001 → 404 且响应体不含任何样本字段；extE（同组未核验）同样 404。
# 顺手确认 r3 返工新增的 invalidReason 键没有把内部专用字段带出来。
set -u
cd "$(dirname "$0")/../../../.."
OUT=.tmp/qa-r3
mkdir -p "$OUT"
API() { bash doc/verify/api.sh "$@"; }
KEYS() { jq -r 'keys|join(",")' "$1" 2>/dev/null; }
LEAK() { grep -Eoi 'donorName|hospitalNo|patientNo|internalNo|submitterId|submitterName|sourceUnit|sourceUnitName|sampleNo|submitNo|submitSource|verifyStatus|receiveDate|donor_name|internal_no|verify_by' "$1" 2>/dev/null | wc -l | tr -d ' '; }

for IDENT in extC extE; do
  echo "===== 1a/1b --as ${IDENT} GET /mp/ext/sample/9000001001 ====="
  API --as "$IDENT" --bizcode --out "$OUT/c1-${IDENT}.json" GET /mp/ext/sample/9000001001
  echo "-- body: $(cat "$OUT/c1-${IDENT}.json")"
  echo "-- top-level keys: $(KEYS "$OUT/c1-${IDENT}.json")"
  echo "-- data: $(jq -c '.data' "$OUT/c1-${IDENT}.json")"
  echo "-- 敏感/样本字段正则命中数: $(LEAK "$OUT/c1-${IDENT}.json")"
done

echo "===== 1c 对照：extA（本人）GET 自己的 1001（证明 404 是隔离，不是端点不存在）====="
API --as extA --bizcode --out "$OUT/c1-extA.json" GET /mp/ext/sample/9000001001
echo "-- data keys: $(jq -r '.data|keys|join(",")' "$OUT/c1-extA.json")"

echo "===== 1d 对照：extA GET 自己的 1003（invalid）——invalidReason 应可见、internalNo 不应出现 ====="
API --as extA --bizcode --out "$OUT/c1-extA1003.json" GET /mp/ext/sample/9000001003
echo "-- data: $(jq -c '.data|{id,verifyStatus,invalidReason,submitNo}' "$OUT/c1-extA1003.json")"
echo "-- data keys: $(jq -r '.data|keys|join(",")' "$OUT/c1-extA1003.json")"
echo "-- internalNo 在任何层级出现次数: $(jq -r '[paths(scalars)]|map(join("."))|map(select(test("internalNo|verifyBy|submitSource|receiveDate|donorName|hospitalNo|patientNo";"i")))|length' "$OUT/c1-extA1003.json")"
echo "-- 1d 敏感字段正则命中数（含 invalidReason 值里的中文不算）: $(LEAK "$OUT/c1-extA1003.json")"

echo "===== 1e 对照：extC GET 自己的 1005（正向存在性，他拿得到自己的）====="
API --as extC --bizcode GET /mp/ext/sample/9000001005

echo "===== 1f 对照：extB（与 extA 同组已核验）GET 1001 —— 同组可看 ====="
API --as extB --bizcode GET /mp/ext/sample/9000001001
