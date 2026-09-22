#!/usr/bin/env bash
# EMBED-MP-001 · accept 2（STATE）—— 补填就是修改：对已有石蜡块 PUT 只改传入的工序时间、
#   不新增行、记下修改人并排到历史最前；内部人员改得动别人录的；改编号撞到别的石蜡块被拒；
#   待核验的外部送样在小程序里改不动 —— 被拒之后库里都不变。
#
# 跑法：bash doc/waves/reports/EMBED-MP-001/accept-runners/mpembed-acc2.sh
#
# ★ 与 ticket 正文的差异：去掉两处 `--fresh-module ruoyi-lqg`（本沙箱恒 exit 2，见 accept 1 说明）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根

run_accept_2() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  python3 doc/verify/db.py --sql "SELECT create_by FROM t_lqg_embed WHERE id=9000002002" --eq 9000000100 &&
  bash doc/verify/api.sh --as staff PUT /mp/int/embed '{"id":9000002002,"sampleId":9000001001,"paraffinBlockNo":"T-E01-2","dehydrateTime":"2026-09-16","embedBy":"李工"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT count(*) || '|' || max(dehydrate_time)::text || '|' || max(embed_by) FROM t_lqg_embed WHERE sample_id=9000001001 AND del_flag='0'" --eq "2|2026-09-16|李工" &&
  python3 doc/verify/db.py --sql "SELECT CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_embed WHERE id=9000002002" --eq yes &&
  bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100&sort=recent&mine=true' | jq -e '(.rows[0].id|tostring)=="9000002002" and .rows[0].handlerName=="李工" and .rows[0].mine==true' &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/embed '{"id":9000002002,"sampleId":9000001001,"paraffinBlockNo":"T-E01-1"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT paraffin_block_no FROM t_lqg_embed WHERE id=9000002002" --eq "T-E01-2" &&
  bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/embed '{"id":9000002006,"sampleId":9000001002,"paraffinBlockNo":"T-E06-9","sampleType":"小程序里改的"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(paraffin_block_no,'-') || '|' || sample_type FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-|组织" &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

echo "########## EMBED-MP-001 · accept 2（STATE）##########"
echo "（差异：去掉两处 --fresh-module；其余字面逐字）"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null
run_accept_2
RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit "${RC}"
