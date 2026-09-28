#!/usr/bin/env bash
# AUTH-EXT-002 · accept 2（STATE）—— 提交与重提的写保护：替同组别人的样本送样、挂到自己无效的样本、
#   挂到看不见的样本、改别人的送样、改实验室录入的石蜡块——全部被拒且库里不变；
#   入参夹带的编号 / 染色 / 核验状态不生效；无效的改后重提回到待核验。
#
# 跑法（任意 cwd，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc2.sh
#
# ★ 与 ticket 正文 accept 2 的 `run` **逐字相同**（这条链没有 `--fresh-module`、也没有 Maven 行，
#   本沙箱跑得动）。长链包进函数判整条 rc（`set -e` 不管非末尾位置的失败）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run_accept_2() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as extB --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | grep -qE '^(400|403|404)' &&
  bash doc/verify/api.sh --as extA --bizcode POST /mp/ext/embed '{"sampleId":9000001003,"sampleType":"组织"}' | grep -qE '^(400|403)' &&
  bash doc/verify/api.sh --as extC --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | grep -qE '^(400|404)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'" --eq 1 &&
  bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"类器官","organoidSourceType":"肝类器官","paraffinBlockNo":"T-hack9","verifyStatus":"valid","embedBy":"外部","stainTypes":["HE"],"operatorName":"外部","submitSource":"internal"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT e.submit_source || '|' || e.verify_status || '|' || COALESCE(e.paraffin_block_no,'-') || '|' || COALESCE(e.embed_by,'-') || '|' || COALESCE(e.stain_types,'-') || '|' || COALESCE(e.operator_name,'-') || '|' || p.real_name FROM t_lqg_embed e JOIN t_lqg_ext_profile p ON p.user_id = e.submitter_id WHERE e.sample_id=9000001001 AND e.submit_source='external'" --eq "external|pending|-|-|-|-|王医生" &&
  bash doc/verify/api.sh --as extB --bizcode PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"被同组人改"}' | grep -qE '^(400|403|404)' &&
  bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/embed/9000002001 '{"sampleId":9000001001,"sampleType":"改实验室的块"}' | grep -qE '^(400|403|404)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_type IN ('被同组人改','改实验室的块')" --eq 0 &&
  bash doc/verify/api.sh --as staff PUT /lqg/embed/9000002006/verify '{"action":"invalid","reason":"样本类型写错"}' | jq -e '.code==200' &&
  bash doc/verify/api.sh --as extA PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"类器官","organoidSourceType":"胆管类器官"}' | jq -e '.code==200' &&
  python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(invalid_reason,'-') || '|' || sample_type || '|' || organoid_source_type FROM t_lqg_embed WHERE id=9000002006" --eq "pending|-|类器官|胆管类器官" &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

echo "== accept 2 · 写保护 / 夹带 / 无效重提 =="
echo "-- 1) extB 替 extA 的 1001 送样（可见但非本人）--"
bash doc/verify/api.sh --as extB --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}'
echo "-- 2) extA 挂到自己已无效的 1003 --"
bash doc/verify/api.sh --as extA --bizcode POST /mp/ext/embed '{"sampleId":9000001003,"sampleType":"组织"}'
echo "-- 3) extC 挂到看不见的 1001 --"
bash doc/verify/api.sh --as extC --bizcode POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}'
echo "-- 4) 被拒三次后 external 送样仍只有 seed 那一条 --"
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'"
echo "-- 5) extA 夹带编号 / 状态 / 包埋人 / 染色 / 操作人 / 来源 提交 --"
bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"类器官","organoidSourceType":"肝类器官","paraffinBlockNo":"T-hack9","verifyStatus":"valid","embedBy":"外部","stainTypes":["HE"],"operatorName":"外部","submitSource":"internal"}' | jq -c '{code,msg}'
echo "-- 6) 库内：夹带的一个都没生效（external|pending|-|-|-|-|王医生）--"
python3 doc/verify/db.py --sql "SELECT e.submit_source || '|' || e.verify_status || '|' || COALESCE(e.paraffin_block_no,'-') || '|' || COALESCE(e.embed_by,'-') || '|' || COALESCE(e.stain_types,'-') || '|' || COALESCE(e.operator_name,'-') || '|' || p.real_name FROM t_lqg_embed e JOIN t_lqg_ext_profile p ON p.user_id = e.submitter_id WHERE e.sample_id=9000001001 AND e.submit_source='external'"
echo "-- 7) extB 改 extA 的 2006 / extA 改实验室录入的 2001 --"
bash doc/verify/api.sh --as extB --bizcode PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"被同组人改"}'
bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/embed/9000002001 '{"sampleId":9000001001,"sampleType":"改实验室的块"}'
echo "-- 8) 两次被拒后库里没有这两条样本类型 --"
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE sample_type IN ('被同组人改','改实验室的块')"
echo "-- 9) 实验室把 2006 判无效 --"
bash doc/verify/api.sh --as staff PUT /lqg/embed/9000002006/verify '{"action":"invalid","reason":"样本类型写错"}' | jq -c '{code,msg}'
echo "-- 10) extA 改后重提 2006 --"
bash doc/verify/api.sh --as extA PUT /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"类器官","organoidSourceType":"胆管类器官"}' | jq -c '{code,msg}'
echo "-- 11) 库内：回 pending、无效原因清空、两个字段落库 --"
python3 doc/verify/db.py --sql "SELECT verify_status || '|' || COALESCE(invalid_reason,'-') || '|' || sample_type || '|' || organoid_source_type FROM t_lqg_embed WHERE id=9000002006"
run_accept_2
RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit "${RC}"
