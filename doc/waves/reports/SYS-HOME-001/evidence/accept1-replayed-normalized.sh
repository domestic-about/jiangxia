#!/usr/bin/env bash
# 票面 accept（逐字重放）：SYS-HOME-001 accept[1] form=DATA
# 五个数与库里独立数的一致并钉在 seed 上；外部新交一条类器官收样、一条石蜡包埋送样后两个待核验数各加一；超期数与超期清单同源；渲染失败数会随真实失败变化
# 归一化：NF1
set -euo pipefail
bash doc/verify/reseed.sh --yes >/dev/null &&
bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data == {"pendingSamples":2,"pendingEmbeds":1,"cryoOverdue":2,"pendingExtUsers":2,"renderFailed":0}' &&
python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending') || '|' || (SELECT count(*) FROM t_lqg_embed WHERE del_flag='0' AND verify_status='pending') || '|' || (SELECT count(*) FROM t_lqg_ext_profile WHERE del_flag='0' AND bind_status='pending')" --eq "2|1|2" &&
test "$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq '.data.cryoOverdue')" = "$(bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq '.data|length')" &&
bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官"}' | jq -e '.code==200' &&
bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | jq -e '.code==200' &&
bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.pendingSamples==3 and .data.pendingEmbeds==2' &&
python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending' AND sample_kind='organoid') || '|' || (SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id WHERE e.del_flag='0' AND e.verify_status='pending')" --eq "1|2" &&
bash doc/verify/reseed.sh --yes >/dev/null &&
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=internal' >/dev/null ; sleep 5 ;
python3 doc/verify/db.py --sql "SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed') x" --eq "$(bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -r '.data.renderFailed')" &&
bash doc/verify/api.sh --as staff GET /lqg/home/todo | jq -e '.data.renderFailed >= 1' &&
bash doc/verify/api.sh --as staff GET /lqg/home/recent | jq -e '(.data|length) <= 10 and ([.data[].submitNo] | index("SJ90000010")) == null' &&
bash doc/verify/reseed.sh --yes >/dev/null
