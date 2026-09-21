#!/usr/bin/env bash
# SAMPLE-MODEL-001 · 对抗性探针（accept 没直接断的形态）
#
# 用法：cwd = 项目根，bash .tmp/sample001-probe.sh
# 前置：后端在 8081（dev profile + --api-decrypt.enabled=false）。
# 每条「被拒」后面都跟库内断言 —— 只看业务码的话，「先落盘再报错」也是绿的。
set -uo pipefail
A() { bash doc/verify/api.sh "$@"; }
DB() { python3 doc/verify/db.py "$@"; }
BIZ() { A --bizcode "$@"; }

echo "── P0 干净 seed 起点 ────────────────────────────────────────────"
bash doc/verify/reseed.sh --yes >/dev/null
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0'" --eq 9

echo "── P1 tissue 缺 tissueType 被拒且不落库 ─────────────────────────"
BIZ --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","receiveDate":"2026-09-17","internalNo":"X1"}'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='X1'" --eq 0

echo "── P2 organoid 缺 internalNo 被拒且不落库 ───────────────────────"
BIZ --as staff POST /lqg/sample '{"sampleKind":"organoid","sourceUnitName":"本中心","organoidType":"肝类器官","receiveDate":"2026-09-17"}'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE organoid_type='肝类器官'" --eq 0

echo "── P3 未知 sampleKind 被拒 ─────────────────────────────────────"
BIZ --as staff POST /lqg/sample '{"sampleKind":"para","sourceUnitName":"本中心","internalNo":"X3"}'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='X3'" --eq 0

echo "── P4 重复内部编号被拒且不落库 ─────────────────────────────────"
BIZ --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-hli02"}'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hli02'" --eq 1

echo "── P5 软删后再录同一个内部编号：允许（部分唯一索引） ────────────"
A --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-del99"}' | jq -e '.code==200'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99'" --eq 2
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-del99' AND del_flag='0'" --eq 1

echo "── P6 加密列只支持精确匹配：明文 eq / LIKE 都查不到 ─────────────"
WANT=$(printf '%s' '测试供体甲' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A)
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE donor_name='测试供体甲'" --eq 0
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE donor_name LIKE '%测试供体%'" --eq 0
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE donor_name='${WANT}'" --eq 1
A --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93' | jq -e '.rows|length==0'
A --as staff GET '/lqg/sample/list?donorName=%E6%B5%8B%E8%AF%95%E4%BE%9B%E4%BD%93%E7%94%B2' | jq -e '[.rows[].id|tostring]==["9000001001"]'

echo "── P7 联想词来自字典，不混入样本历史值（构造私有值验证） ────────"
docker exec lqg-dev-postgres psql -U lqg -d lqg_dev -q -c "UPDATE t_lqg_sample SET tissue_type='某某单位私有组织' WHERE id=9000001001" >/dev/null
A --as extA GET '/mp/dict/hints?type=tissue' | jq -e '(.data|index("某某单位私有组织")==null) and (.data|index("肝组织")!=null) and (.data|length==7)'
A --as extA --bizcode GET '/mp/dict/hints?type=nope' | grep -qE '^500'
bash doc/verify/reseed.sh --yes >/dev/null

echo "── P8 软删行任何条件都查不到，也不进分页总数 ───────────────────"
A --as staff GET '/lqg/sample/list?pageSize=100' | jq -e '([.rows[].id|tostring]|index("9000001010"))==null and (.rows|length)==9 and .total==9'
A --as staff GET '/lqg/sample/list?internalNo=T-del99' | jq -e '.rows|length==0'
BIZ --as staff GET /lqg/sample/9000001010 | grep -qE '^500'

echo "── P9 外部身份进不了 /lqg/sample/** ────────────────────────────"
BIZ --as extA GET '/lqg/sample/list' | grep -qE '^403' && echo "extA GET  /lqg/sample/list       → 403"
BIZ --as extF GET '/lqg/sample/9000001001' | grep -qE '^403' && echo "extF GET  /lqg/sample/9000001001 → 403"

echo "── P10 PUT：submitNo/submitSource/submitterId 不可改 ────────────"
A --as staff PUT /lqg/sample '{"id":9000001001,"sampleKind":"tissue","sourceUnitId":9000009001,"donorName":"测试供体甲","gender":"male","age":"56","hospitalNo":"ZY0000001","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-hli01","submitNo":"HACKED","submitSource":"external","submitterId":1}' | jq -e '.code==200'
DB --sql "SELECT submit_no||'|'||submit_source||'|'||submitter_id FROM t_lqg_sample WHERE id=9000001001" --eq "SJ90000001|external|9000000111"

echo "── P11 PUT 写回 update_by，详情 updateByName 随人变 ─────────────"
A --as staff GET /lqg/sample/9000001001 | jq -e '.data.updateByName=="李工"'
A --as admin PUT /lqg/sample '{"id":9000001001,"sampleKind":"tissue","sourceUnitId":9000009001,"donorName":"测试供体甲","gender":"male","age":"56","hospitalNo":"ZY0000001","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-hli01"}' >/dev/null
A --as admin GET /lqg/sample/9000001001 | jq -e '.data.updateByName=="测试管理员"'

echo "── P12 DELETE 走软删（del_flag=1，行还在），编号可重用 ──────────"
BIZ --as staff DELETE /lqg/sample/9000001005 | grep -qE '^200'
DB --sql "SELECT del_flag FROM t_lqg_sample WHERE id=9000001005" --eq 1
A --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitName":"本中心","tissueType":"胃组织","receiveDate":"2026-09-18","internalNo":"T-hga03"}' | jq -e '.code==200'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-hga03' AND del_flag='0'" --eq 1

echo "── P13 送检单号走序列：连开 5 条，号不重复且形状对 ──────────────"
for i in 1 2 3 4 5; do
  A --as staff POST /lqg/sample "{\"sampleKind\":\"tissue\",\"sourceUnitName\":\"本中心\",\"tissueType\":\"肝组织\",\"receiveDate\":\"2026-09-17\",\"internalNo\":\"T-seq0${i}\"}" | jq -r '.data' >/dev/null
done
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no LIKE 'T-seq%'" --eq 5
DB --sql "SELECT count(DISTINCT submit_no) FROM t_lqg_sample WHERE internal_no LIKE 'T-seq%'" --eq 5
A --as staff GET '/lqg/sample/list?pageSize=100' | jq -e '[.rows[].submitNo]|all(test("^SJ[0-9]{8}$"))'

echo "── P14 来源单位快照：选单位时取单位表名称（不信请求里的名字） ───"
A --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitId":9000009001,"sourceUnitName":"假的单位名","tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-unit01"}' | jq -e '.code==200'
DB --sql "SELECT source_unit_name FROM t_lqg_sample WHERE internal_no='T-unit01'" --eq "A 医院"
BIZ --as staff POST /lqg/sample '{"sampleKind":"tissue","sourceUnitId":9999999999,"tissueType":"肝组织","receiveDate":"2026-09-17","internalNo":"T-unit02"}' | grep -qE '^500'
DB --sql "SELECT count(*) FROM t_lqg_sample WHERE internal_no='T-unit02'" --eq 0

bash doc/verify/reseed.sh --yes >/dev/null
echo "PROBE SAMPLE-MODEL-001 ALL PASS"
