#!/usr/bin/env bash
# AUTH-EXT-001 · accept 3（STATE）—— 写保护 / 夹带不生效 / 无效重提 / 类别闸。
#
# 跑法（cwd = 项目根）：
#   bash doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc3.sh
#
# ★ 与 ticket 正文 accept.run 的唯一差异：**去掉了 `--fresh-module ruoyi-lqg`**（沙箱里恒 exit 2，
#   `ps -o lstart=` 被禁），等价证据见完工报告 §4.0。别的字面逐字保留。
#
# ★ 长链包进函数判整条 rc：`set -e` 不管非末尾位置的失败（AUTH-STAFF-001 坑 3）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run_accept_3() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as extB --bizcode PUT /mp/ext/sample/9000001002 '{"sourceUnitName":"A 医院","donorName":"被同组人篡改","tissueType":"肝组织"}' | grep -qE '^(400|403|404)' &&
  bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/sample/9000001001 '{"sourceUnitName":"A 医院","donorName":"改已核验的","tissueType":"肝组织"}' | grep -qE '^(400|403)' &&
  bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100' | jq -e '[.rows[] | select((.id|tostring)=="9000001001" or (.id|tostring)=="9000001002") | .donorName] | sort == ["测试供体乙","测试供体甲"]' &&
  bash doc/verify/api.sh --as extA PUT /mp/ext/sample/9000001003 '{"sourceUnitName":"A 医院","donorName":"测试供体丙","gender":"male","hospitalNo":"ZY0000003","tissueType":"肝组织","internalNo":"T-hack01","verifyStatus":"valid","submitSource":"internal","receiveDate":"2026-09-17"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(internal_no,'-') || '|' || submit_source || '|' || COALESCE(receive_date::text,'-') || '|' || COALESCE(invalid_reason,'-') FROM t_lqg_sample WHERE id=9000001003" --eq "pending|-|external|-|-" &&
  bash doc/verify/api.sh --as extC POST /mp/ext/sample '{"sourceUnitName":"A 医院","donorName":"新送检","gender":"female","age":"40","hospitalNo":"ZYNEW01","tissueType":"胃组织","hasPathology":"N"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT sample_kind || '|' || submit_source || '|' || verify_status || '|' || submitter_id || '|' || (submit_no ~ '^SJ[0-9]{8}$') FROM t_lqg_sample WHERE tissue_type='胃组织' AND submitter_id=9000000113 AND create_time > now() - interval '5 minutes'" --eq "tissue|external|pending|9000000113|true" &&
  bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官","remark":"外部送类器官","internalNo":"T-hack02","verifyStatus":"valid","receiveDate":"2026-09-17","hasViabilityReport":"Y","operatorName":"外部自填"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT sample_kind || '|' || submit_source || '|' || verify_status || '|' || COALESCE(internal_no,'-') || '|' || COALESCE(receive_date::text,'-') || '|' || COALESCE(has_viability_report,'-') || '|' || COALESCE(operator_name,'-') || '|' || COALESCE(donor_name,'-') FROM t_lqg_sample WHERE organoid_type='胃类器官' AND submitter_id=9000000113" --eq "organoid|external|pending|-|-|-|-|-" &&
  OID="$(python3 doc/verify/db.py --quiet --sql "SELECT id FROM t_lqg_sample WHERE organoid_type='胃类器官' AND submitter_id=9000000113" | head -1)" &&
  bash doc/verify/api.sh --as extD --bizcode PUT "/mp/ext/organoid/${OID}" '{"sourceUnitName":"A 医院","organoidType":"被外单位改"}' | grep -qE '^(400|403|404)' &&
  bash doc/verify/api.sh --as extC --bizcode PUT "/mp/ext/sample/${OID}" '{"sourceUnitName":"A 医院","donorName":"借组织样本的口改","tissueType":"肝组织"}' | grep -qE '^(400|404)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE organoid_type='被外单位改' OR (donor_name IS NOT NULL AND sample_kind='organoid' AND submitter_id=9000000113)" --eq 0 &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

echo "== accept 3 · 写保护 / 夹带 / 类别闸 =="
run_accept_3
RC=$?
echo "ACCEPT-3 EXIT=${RC}"
exit "${RC}"
