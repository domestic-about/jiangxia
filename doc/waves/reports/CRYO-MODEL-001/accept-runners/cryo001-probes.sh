#!/usr/bin/env bash
# CRYO-MODEL-001 · accept 之外的对抗性探针（报告 §4.4 的取证脚本）。
# 逐条打印真实输出，便于人眼核对；任一条不符期望就 echo ✗（本脚本只取证，不判绿）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
post() { bash doc/verify/api.sh --as staff --bizcode POST /lqg/cryo/batch "$1"; }
v()    { bash doc/verify/api.sh --as staff --bizcode PUT "/lqg/sample/$1/verify" "$2"; }

echo "== P0 干净 seed 起点"
bash doc/verify/reseed.sh --yes >/dev/null
python3 doc/verify/db.py --sql "SELECT '批次(未删)=' || count(*) FROM t_lqg_cryo_batch WHERE del_flag='0'"
python3 doc/verify/db.py --sql "SELECT '批次(含软删)=' || count(*) FROM t_lqg_cryo_batch"
python3 doc/verify/db.py --sql "SELECT '流水(未删)=' || count(*) || ' 流水(含软删)=' || (SELECT count(*) FROM t_lqg_cryo_flow) FROM t_lqg_cryo_flow WHERE del_flag='0'"

echo
echo "== P1 五条被拒的批次校验（逐条真实消息）+ 库里 T-BAD* 行数"
post '{"sampleId":9000001005,"cryoName":"T-BAD1","passage":"3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}'
post '{"sampleId":9000001005,"cryoName":"T-BAD2","passage":"P3","freezeTime":"2026-09-17","initQty":0,"inMinus80":"Y"}'
post '{"sampleId":9000001005,"cryoName":"T-BAD3","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"N"}'
post '{"sampleId":9000001002,"cryoName":"T-BAD4","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}'
post '{"sampleId":9000001005,"cryoName":"T-BAD5","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y","toLn2Time":"2026-09-01","ln2Location":"1号罐"}'
post '{"sampleId":9000001005,"cryoName":"   ","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}'
post '{"cryoName":"T-BAD6","passage":"P3","freezeTime":"2026-09-17","initQty":4,"inMinus80":"Y"}'
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_batch WHERE cryo_name LIKE 'T-BAD%'" --eq 0

echo
echo "== P2 正面：新建一条（挂已核验有效的 1005）"
post '{"sampleId":9000001005,"cryoName":"T-hga03-W-N-P12-EM2-1e5","passage":"P12","freezeTime":"2026-09-17","initQty":4,"density":"1e5","inMinus80":"Y","frozenBy":"李工"}'

echo
echo "== P3 改初始支数：10 成功（并记 update_by）；1 被拒 + 库内不变"
B="$(bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003001 | jq -c '.data | {id, sampleId, cryoName, passage, freezeTime, density, inMinus80, frozenBy}')"
printf '%s\n' "${B}"
bash doc/verify/api.sh --as staff PUT /lqg/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 10')" | jq -c '{code,msg}'
python3 doc/verify/db.py --sql "SELECT 'init=' || init_qty || ' update_by=' || COALESCE(update_by::text,'NULL') || ' update_time非空=' || (update_time IS NOT NULL) FROM t_lqg_cryo_batch WHERE id=9000003001"
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 1')"
python3 doc/verify/db.py --sql "SELECT '库内 init=' || init_qty FROM t_lqg_cryo_batch WHERE id=9000003001"
bash doc/verify/api.sh --as staff --bizcode DELETE /lqg/cryo/batch/9000003001
python3 doc/verify/db.py --sql "SELECT '库内 del_flag=' || del_flag FROM t_lqg_cryo_batch WHERE id=9000003001"
bash doc/verify/reseed.sh --yes >/dev/null

echo
echo "== P4 位置筛选与行上 location 同源（3003 先 -80 后转液氮、3007 直接进液氮）"
bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?location=ln2&pageSize=100' | jq -c '[.rows[].id|tostring]|sort'
bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?location=minus80&pageSize=100' | jq -c '[.rows[].id|tostring]|sort'
bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?internalNo=T-hli01&pageSize=100' | jq -c '[.rows[]|"\(.id|tostring):\(.cryoName):\(.remainingQty):\(.location)"]|sort'

echo
echo "== P5 软删批次不出现 / 详情查不到；取空的批次仍在列表里"
bash doc/verify/api.sh --as staff --bizcode GET /lqg/cryo/batch/9000003008
bash doc/verify/api.sh --as staff GET /lqg/cryo/batch/9000003004 | jq -c '.data | {id, initQty, remainingQty, location}'
python3 doc/verify/db.py --sql "SELECT 'sample 1008 未删批次数=' || count(*) FROM t_lqg_cryo_batch WHERE sample_id=9000001008 AND del_flag='0'"

echo
echo "== P6 D4 L3：有冻存批次的样本改判无效被拒（1009 名下只有冻存批次、没有石蜡块）"
v 9000001009 '{"action":"invalid","reason":"探针：名下有两批冻存"}'
python3 doc/verify/db.py --sql "SELECT '1009 verify_status=' || verify_status FROM t_lqg_sample WHERE id=9000001009"

echo
echo "== P7 D2 回归：1002 名下没有冻存批次 → valid→invalid 仍放行（SAMPLE-VERIFY-001 accept 1 那一格）"
bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-cryo99"}' | jq -c '{code}'
v 9000001002 '{"action":"invalid","reason":"探针：名下无冻存批次"}'
python3 doc/verify/db.py --sql "SELECT '1002 verify_status=' || verify_status FROM t_lqg_sample WHERE id=9000001002"

echo
echo "== P8 软删的冻存批次不算 children：1005 建批 → 被拒；软删该批 → 放行"
bash doc/verify/reseed.sh --yes >/dev/null
NEW="$(bash doc/verify/api.sh --as staff POST /lqg/cryo/batch '{"sampleId":9000001005,"cryoName":"T-probe-del","passage":"P1","freezeTime":"2026-09-17","initQty":2,"inMinus80":"Y"}' | jq -r '.data')"
echo "新建批次 id=${NEW}"
v 9000001005 '{"action":"invalid","reason":"探针：名下有未删冻存批次"}'
bash doc/verify/api.sh --as staff --bizcode DELETE "/lqg/cryo/batch/${NEW}"
python3 doc/verify/db.py --sql "SELECT '软删后 del_flag=' || del_flag FROM t_lqg_cryo_batch WHERE id=${NEW}"
v 9000001005 '{"action":"invalid","reason":"探针：名下的冻存批次已软删"}'
python3 doc/verify/db.py --sql "SELECT '1005 verify_status=' || verify_status FROM t_lqg_sample WHERE id=9000001005"

echo
echo "== P9 权限：外部身份进不了 /lqg/cryo/**"
bash doc/verify/api.sh --as extA --bizcode GET '/lqg/cryo/batch/list'
bash doc/verify/api.sh --as extA --bizcode POST /lqg/cryo/batch '{"sampleId":9000001001,"cryoName":"X","passage":"P1","freezeTime":"2026-09-17","initQty":1,"inMinus80":"Y"}'
bash doc/verify/reseed.sh --yes >/dev/null
echo "PROBES DONE"
