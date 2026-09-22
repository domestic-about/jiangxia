#!/usr/bin/env bash
# CRYO-MP-001 · counterfeit 探针（accept 之外的逐条反证）。
#
# 每一条都对着 ticket `counterfeit` 里的一句「做反了会怎样」，把那个错误的**可观测后果**
# 直接打出来 —— 全部走真接口 / 直连库，不改任何只读区。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&

  echo '── P1 外部能调冻存接口 → 必须 403（冻存信息明确不对外）' &&
  bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/cryo/batch/list' &&
  bash doc/verify/api.sh --as extA --bizcode GET /mp/int/cryo/batch/9000003001/flows &&

  echo '── P2 /mp/int/cryo 上的四个写路径都要「没有这个接口」（404 / 405）' &&
  bash doc/verify/api.sh --as staff --bizcode POST /mp/int/cryo/batch/9000003002/flow '{"flowType":"take","qty":1,"purpose":"x"}' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch/9000003001/flow/9000003101 '{"qty":1}' &&
  bash doc/verify/api.sh --as staff --bizcode DELETE /mp/int/cryo/batch/9000003001/flow/9000003101 &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch/9000003001/to-ln2 '{"toLn2Time":"2026-09-17","ln2Location":"1号罐"}' &&
  bash doc/verify/api.sh --as staff --bizcode DELETE /mp/int/cryo/batch/9000003001 &&

  echo '── P3 经手人取的是「最后改的人」而不是冻存人（3002：frozen_by=李工、handlerName=测试管理员）' &&
  python3 doc/verify/db.py --sql "SELECT frozen_by || '|' || create_by FROM t_lqg_cryo_batch WHERE id=9000003002" &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100' | jq -c '[.rows[]|select((.id|tostring)=="9000003002")|[.frozenBy,.handlerName,.mine]]' &&

  echo '── P4 mine 不看软删（软删的 3008 是李工建的，但不许出现在 mine=true 里；这里已改过 3004 故 4 条）' &&
  bash doc/verify/api.sh --as staff PUT /mp/int/cryo/batch '{"id":9000003004,"remark":"探针：改一次"}' | jq -c '.code' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&sort=recent&mine=true' | jq -c '[.rows[].id|tostring]|sort' &&
  python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_cryo_batch WHERE del_flag='0' AND (create_by=9000000101 OR update_by=9000000101)" --col-set "9000003001,9000003003,9000003004" &&

  echo '── P5 页签数字是整表口径（超期页签只 2 行，数字仍是 7 / 2 / 2）' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/cryo/batch/list?pageSize=100&overdueOnly=true' | jq -c '{rows:(.rows|length),tabCounts}' &&

  echo '── P6 改冻存数量改到某一步为负 → 后端拒绝，且把原话吐出来（前端照实显示、不自己算）' &&
  bash doc/verify/api.sh --as staff GET /mp/int/cryo/batch/9000003004 | jq -c '.data.initQty' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/cryo/batch '{"id":9000003004,"initQty":2}' &&
  python3 doc/verify/db.py --sql "SELECT init_qty FROM t_lqg_cryo_batch WHERE id=9000003004" --eq 3 &&

  echo '── P7 内部人员改谁录的都行（3002 是管理员建的）：200 且 update_by 记成本人' &&
  bash doc/verify/api.sh --as staff PUT /mp/int/cryo/batch '{"id":9000003002,"frozenBy":"王工"}' | jq -c '.code' &&
  python3 doc/verify/db.py --sql "SELECT frozen_by || '|' || update_by FROM t_lqg_cryo_batch WHERE id=9000003002" --eq "王工|9000000101" &&

  echo '── P8 残留：库里一笔流水都没被小程序动过（5 笔未删）' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_cryo_flow WHERE del_flag='0'" --eq 5 &&

  bash doc/verify/reseed.sh --yes >/dev/null
}

run
rc=$?
echo "PROBES EXIT=$rc"
exit "$rc"
