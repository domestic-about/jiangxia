#!/usr/bin/env bash
# CRYO-MP-001 · accept 1（DATA）—— ticket `run` 逐字，唯一偏差 = 去掉 `--fresh-module ruoyi-lqg`
# （本 subagent 沙箱 `api.sh` 第 71 行 `ps -o lstart=` 被禁 → 恒 exit 2；等价反 stale 证据见
#  cryomp001-freshness.sh 的八项）。长链包进函数判整条 rc（`set -e` 不管 `&&` 链非末尾的失败）。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -e '.tabCounts == {"all":7,"overdue":2,"ln2":2} and (.rows|length)==7 and ([.rows[]|select((.id|tostring)=="9000003001")|[.remainingQty,.overdueDays,.location]]==[[6,6,"minus80"]])' &&
  python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_cryo_batch WHERE del_flag='0') || '|' || (SELECT count(*) FROM t_lqg_cryo_batch WHERE del_flag='0' AND (in_minus80='N' OR to_ln2_time IS NOT NULL))" --eq "7|2" &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent' | jq -e '(.rows|length)==7 and ([.rows[]|select((.id|tostring)=="9000003002")|[.handlerName,.mine]]==[["测试管理员",false]]) and ([.rows[]|select((.id|tostring)=="9000003001")|[.handlerName,.mine]]==[["李工",true]])' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent&mine=true' | jq -e '([.rows[].id|tostring]|sort)==["9000003001","9000003003"]' &&
  B="$(bash doc/verify/api.sh --as staff GET /mp/int/cryo/batch/9000003004 | jq -c '.data | {id, sampleId, cryoName, passage, freezeTime, density, inMinus80, frozenBy}')" &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 2')" | grep -qE '^(400|500)' &&
  bash doc/verify/api.sh --as staff PUT /mp/int/cryo/batch "$(printf '%s' "${B}" | jq -c '.initQty = 5 | .frozenBy = "王工"')" | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT b.init_qty || '|' || b.frozen_by || '|' || (b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0)) FROM t_lqg_cryo_batch b WHERE b.id=9000003004" --eq "5|王工|2" &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent' | jq -e '(.rows|length)==7 and (.rows[0].id|tostring)=="9000003004" and ([.rows[0]|[.handlerName,.mine]]==[["李工",true]])' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent&mine=true' | jq -e '(.rows[0].id|tostring)=="9000003004" and ([.rows[].id|tostring]|sort)==["9000003001","9000003003","9000003004"]' &&
  python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_cryo_batch WHERE del_flag='0' AND (create_by=9000000101 OR update_by=9000000101)" --col-set "9000003001,9000003003,9000003004" &&
  bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/cryo/batch/list' | grep -qE '^403' &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

run
rc=$?
echo "ACCEPT-1 EXIT=$rc"
exit "$rc"
