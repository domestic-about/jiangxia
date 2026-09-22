#!/usr/bin/env bash
# SAMPLE-HINT-001 · counterfeit 探针（accept 之外的对抗性证据）。
#
# 五条 counterfeits 逐条排（ticket accept 1 / accept 2 的 `counterfeit` 段）：
#   ① 聚合 SQL 忘了 e.del_flag='0'      → 1008 名下那块软删的石蜡块被数进来
#   ② 把 NONE 当成一种染色              → 1004 变成 ["NONE"]
#   ③ 没有包埋记录的行 hint 为 null     → 前端 undefined.blockCount
#   ④ 把待核验的外部送样也数成一块      → 1002 变成 [1,false,[]]
#   ⑤ 一页发 N 次查询                   → 后端日志里一次请求出现 N 条聚合 SQL
#
# 跑法：bash doc/waves/reports/SAMPLE-HINT-001/accept-runners/hint001-counterfeit.sh
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"
LOG=".tmp/hint001-backend.log"

bash doc/verify/reseed.sh --yes >/dev/null
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

echo "== ① 不加 del_flag 守卫：1008 名下会数出 1 块（那块 2005 是软删的） =="
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001008 AND verify_status='valid'"
echo "   正确口径（叠上 del_flag='0'）："
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001008 AND verify_status='valid' AND del_flag='0'" --eq 0

echo "== ② 不加 verify_status 守卫：1002 名下会数出 1 块（那 2006 还在待核验） =="
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001002 AND del_flag='0'"
echo "   正确口径（叠上 verify_status='valid'）："
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_id=9000001002 AND del_flag='0' AND verify_status='valid'" --eq 0

echo "== ③ 不拆 NONE：1004 那一块的 stain_types 原样是「NONE」 =="
python3 doc/verify/db.py --sql "SELECT COALESCE(string_agg(stain_types,','),'-') FROM t_lqg_embed WHERE sample_id=9000001004 AND del_flag='0' AND verify_status='valid'" --eq NONE
echo "   接口给的是（应为空数组）："
bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' \
  | jq -c '[.rows[]|select((.id|tostring)=="9000001004")|.hint.stains][0]'

echo "== ④ 每一行都有 hint 对象（含没有包埋记录的行），没有一个是 null =="
bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' \
  | jq -e '[.rows[] | select((.hint|type) != "object")] | length == 0'
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' \
  | jq -e '[.rows[] | select((.hint|type) != "object")] | length == 0'

echo "== ⑤ 一页只发一次聚合查询：一次请求在前端日志里只出现 1 条那条 SQL =="
BEFORE="$(wc -l < "${LOG}")"
bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' >/dev/null
sleep 1
AFTER="$(tail -n +"$((BEFORE + 1))" "${LOG}" | grep -c 'FROM t_lqg_embed e JOIN t_lqg_sample s')"
echo "   一次 /lqg/sample/list?pageSize=100（9 行）触发的聚合 SQL 条数 = ${AFTER}"
[ "${AFTER}" = "1" ] || { echo "RED ⑤：逐行查会 >1"; exit 1; }

BEFORE="$(wc -l < "${LOG}")"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' >/dev/null
sleep 1
AFTER="$(tail -n +"$((BEFORE + 1))" "${LOG}" | grep -c 'FROM t_lqg_embed e JOIN t_lqg_sample s')"
echo "   一次 /mp/int/sample/list?pageSize=100（9 行）触发的聚合 SQL 条数 = ${AFTER}"
[ "${AFTER}" = "1" ] || { echo "RED ⑤(mp)：逐行查会 >1"; exit 1; }

echo "== ⑥ 样本表上没有被偷加的冗余列（读时计算，两个真相源会打架） =="
python3 doc/verify/db.py --sql "SELECT count(*) FROM information_schema.columns WHERE table_name='t_lqg_sample' AND column_name IN ('block_count','has_section','sectioned','stain_types','stain_hint')" --eq 0

echo "== ⑦ 两条读路径同源：/lqg/sample/list 与 /mp/int/sample/list 的 hint 逐行相等 =="
LEFT="$(bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' | jq -c '[.rows[]|{(.id|tostring):.hint}]|add')"
RIGHT="$(bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' | jq -c '[.rows[]|{(.id|tostring):.hint}]|add')"
[ "${LEFT}" = "${RIGHT}" ] && echo "   true（两侧逐行一致）" || { echo "RED ⑦"; exit 1; }

echo "COUNTERFEIT PROBES EXIT=0"
