#!/usr/bin/env bash
# 票面 accept（逐字重放）：DOC-RENDER-001 accept[1] form=DATA
# 渲染出的 Word：带出字段与填写字段都在、两句印死的注逐字保留、没有残留占位符、图片真的嵌进去了；外部版里找不到内部编号而内部版里有
# 归一化：NF1
set -euo pipefail
bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-int.docx /tmp/lqg-ext.docx /tmp/lqg-score.docx &&
OSS="$(bash doc/verify/api.sh --as staff --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" && test -n "${OSS}" && test "${OSS}" != null &&
bash doc/verify/api.sh --as staff GET /lqg/qc/9000001005 >/dev/null &&
bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/sample-qc '{"patientNo":"P-RENDER","samplingSite":"胃窦","samplingMethod":"活检","clinicalDiagnosis":"渲染探针诊断","origDesc":"探针描述甲"}' | jq -e '.code==200' &&
bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"orig\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"observe\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
for A in internal external; do bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001005/sample_qc/render?audience=${A}" | jq -e '.code==200 and .data.status=="done"' || exit 1; done &&
curl -sSf -o /tmp/lqg-int.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=docx&audience=internal' | jq -r '.data.url')" &&
curl -sSf -o /tmp/lqg-ext.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=docx&audience=external' | jq -r '.data.url')" &&
python3 doc/verify/docx_check.py --file /tmp/lqg-int.docx --no-placeholder --min-images 2 --contains "T-hga03" --contains "测试供体戊" --contains "A 医院" --contains "P-RENDER" --contains "渲染探针诊断" --contains "探针描述甲" --contains "样本按质控要求，保持2-8℃低温环境运输至实验室。" --contains "注：合格，活率≥70%；基本合格，50%~70%；不合格，＜50%或活细胞＜1x104。" --not-contains "要求图片可以放大" &&
python3 doc/verify/docx_check.py --file /tmp/lqg-ext.docx --no-placeholder --min-images 2 --contains "测试供体戊" --contains "P-RENDER" --not-contains "T-hga03" &&
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
curl -sSf -o /tmp/lqg-score.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/download?format=docx&audience=internal' | jq -r '.data.url')" &&
python3 doc/verify/docx_check.py --file /tmp/lqg-score.docx --no-placeholder --contains "85" --contains "30~100μm" --contains "注：类器官质量评分≤50表示类器官质量偏差，药敏实验失败风险较大；50~75表示类器官质量中等；≥75表示类器官质量良好。" &&
bash doc/verify/reseed.sh --yes >/dev/null
