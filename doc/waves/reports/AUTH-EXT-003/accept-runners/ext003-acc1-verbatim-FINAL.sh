#!/usr/bin/env bash
# 票面 accept（逐字重放）：AUTH-EXT-003 accept[1] form=API
# 外部文档清单钉死在 seed 上：同组可见、草稿不给、外部版没渲染成功的不给、异组按不存在返回；带 audience=internal 也拿不到内部版；链接里的对象键是外部版
# 归一化：NF1
set -euo pipefail
bash doc/verify/reseed.sh --yes >/dev/null &&
for K in "9000001001/sample_qc" "9000001001/organoid_qc" "9000001001/organoid_score" "9000001004/sample_qc"; do bash doc/verify/api.sh --as staff POST "/lqg/doc/${K}/render?audience=external" >/dev/null; done &&
bash doc/verify/api.sh --as staff GET /lqg/sys/ping >/dev/null && sleep 5 &&
python3 doc/verify/db.py --sql "SELECT doc_kind || ':' || render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND audience='external' AND file_format='docx' AND del_flag='0'" --col-set "organoid_qc:done,organoid_score:done,sample_qc:done" &&
bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100' | jq -e '([.rows[] | "\(.sampleId|tostring):\(.docKind)"] | sort) == ["9000001001:organoid_qc","9000001001:organoid_score","9000001001:sample_qc","9000001004:sample_qc"] and ([.rows[]|select(.docKind=="organoid_score")|.totalScore]==[85]) and ([.rows[]|keys[]]|unique|index("internalNo")==null)' &&
bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100' | jq -e '.rows==[]' &&
bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '[.rows[].docKind]==["sample_qc","organoid_qc","organoid_score"]' &&
bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '.rows==[]' &&
bash doc/verify/api.sh --as extC GET /mp/ext/doc/9000001001/organoid_score/pages | jq -e '.code==404' &&
bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001004/organoid_qc/pages | jq -e '.code==404' &&
bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001001/sample_qc/pages | jq -e '.code==200' &&
URL="$(bash doc/verify/api.sh --as extA GET '/mp/ext/doc/9000001001/organoid_score/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
printf '%s' "${URL}" | grep -q '/external/' && ! printf '%s' "${URL}" | grep -q '/internal/' &&
curl -sSf -o /tmp/lqg-ext-score.pdf "${URL}" && pdftotext /tmp/lqg-ext-score.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' &&
bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001001 | jq -e '[.data.docs[].docKind]==["sample_qc","organoid_qc","organoid_score"]'
