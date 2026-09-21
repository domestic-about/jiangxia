#!/usr/bin/env bash
# SAMPLE-MP-002 · accept 1（DATA）—— 表格页与库侧同源 / 内部录入落成内部有效 / 修改模式改得动 /
#                      外部提交落成待核验且提交人是本人 / 两种身份的历史编辑记录都找得回。
#
# 跑法（cwd 随意，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc1.sh
#
# ★ 与 ticket 正文 accept.run 的**两处**差异（都能单独复现，见下）：
#
#   1) 去掉 `--fresh-module ruoyi-lqg`：本沙箱 `/bin/ps` 被禁，api.sh 的反 stale 守卫恒 exit 2
#      （SYS-MP-001 起连续 8+ 张命中的既有 WARN）。等价证据见完工报告 §4.0。
#
#   2) `MP002_ACC1_KEEP_QUIET=1` 时不改第 5 段 —— 也就是**逐字**保留 ticket 里的
#      `python3 doc/verify/db.py --quiet --sql "SELECT id …"`。
#      那个写法拿到的永远是空串：`doc/verify/db.py` 的 `--quiet` 语义是「**不打印任何行**」
#      （`if not a.quiet: for ln in lines: print(ln)`），空串拼进 `printf '{"id":%s,…}'` 就得到
#      `{"id":,…}` → 后端 JSON 解析 400 → `jq -e '.code==200'` 必红。**与实现无关**。
#      默认（不带这个环境变量）把该段里的 `--quiet` 去掉，别的一个字都不动 —— 那才是这段
#      想做的事（拿刚建出来的那行的 id 去 PUT）。两跑的输出都在完工报告 §4.1。
#
# ★ 长链包进函数判整条 rc：`set -e` 不管非末尾位置的失败（AUTH-STAFF-001 坑 3）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根

DBQ=""
if [ "${MP002_ACC1_KEEP_QUIET:-0}" = "1" ]; then
  DBQ="--quiet"
fi

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run_accept_1() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sampleKind=organoid' | jq -e '([.rows[].id|tostring]|sort)==["9000001009"]' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND sample_kind='organoid'" --eq "$(bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sampleKind=organoid' | jq -r '.total')" &&
  bash doc/verify/api.sh --as staff POST /mp/int/sample '{"sampleKind":"organoid","sourceUnitId":9000009002,"organoidType":"肝类器官","receiveDate":"2026-09-17","internalNo":"T-oco55","hasViabilityReport":"Y","operatorName":"李工"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT sample_kind || '|' || submit_source || '|' || verify_status || '|' || source_unit_name || '|' || has_viability_report FROM t_lqg_sample WHERE internal_no='T-oco55'" --eq "organoid|internal|valid|B 大学|Y" &&
  bash doc/verify/api.sh --as staff PUT /mp/int/sample "$(printf '{"id":%s,"operatorName":"王工"}' "$(python3 doc/verify/db.py ${DBQ} --sql "SELECT id FROM t_lqg_sample WHERE internal_no='T-oco55'" | head -1)")" | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT operator_name || '|' || sample_kind || '|' || verify_status FROM t_lqg_sample WHERE internal_no='T-oco55'" --eq "王工|organoid|valid" &&
  bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sampleKind=organoid&mine=true' | jq -e '[.rows[].internalNo] == ["T-oco55","T-oco01"]' &&
  bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官","remark":"外部送类器官"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT s.sample_kind || '|' || s.verify_status || '|' || COALESCE(s.internal_no,'-') || '|' || COALESCE(s.receive_date::text,'-') || '|' || p.real_name FROM t_lqg_sample s JOIN t_lqg_ext_profile p ON p.user_id = s.submitter_id WHERE s.organoid_type='胃类器官' AND s.del_flag='0'" --eq "organoid|pending|-|-|赵医生" &&
  bash doc/verify/api.sh --as extC GET '/mp/ext/sample/list?pageSize=100&sampleKind=organoid&onlyMine=true' | jq -e '[.rows[].organoidType]==["胃类器官"] and .rows[0].editable==true and .rows[0].mine==true' &&
  bash doc/verify/api.sh --as extA GET '/mp/ext/sample/list?pageSize=100&sampleKind=organoid' | jq -e '.rows==[]' &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

echo "########## SAMPLE-MP-002 · accept 1（DATA）##########"
if [ -n "${DBQ}" ]; then
  echo "（MP002_ACC1_KEEP_QUIET=1：第 5 段逐字保留 ticket 的 --quiet —— 预期红）"
else
  echo "（差异：去掉 --fresh-module；第 5 段去掉 db.py 的 --quiet，其余字面逐字）"
fi
run_accept_1
RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit "${RC}"
