#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-MODEL-001 accept[3] form=STATE
# 首次打开幂等建三份草稿且默认文字逐字照模板；图片位规则（归属、每位至多三张）被拒时库里不变；患者编号落库是密文
# 归一化：NF1
set -euo pipefail
bash doc/verify/reseed.sh --yes >/dev/null &&
bash doc/verify/api.sh --as staff GET /lqg/qc/9000001006 | jq -e '.code==200 and .data.sample.internalNo=="T-hco04" and .data.sampleQc.docStatus=="draft" and .data.sampleQc.receiveDesc=="样本按质控要求，保持2-8℃低温环境运输至实验室。" and .data.sampleQc.observeDesc=="样本外观呈黄白色。" and .data.sampleQc.pretreatDesc=="样本经剪切等预处理，显微镜下观察组织漏出细胞量适中，细胞活性中等；培养3d照片如左图所示。" and .data.score.totalScore==18' &&
bash doc/verify/api.sh --as staff GET /lqg/qc/9000001006 >/dev/null &&
python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM t_lqg_qc_sample WHERE sample_id=9000001006) || '|' || (SELECT count(*) FROM t_lqg_qc_organoid WHERE sample_id=9000001006) || '|' || (SELECT count(*) FROM t_lqg_qc_score WHERE sample_id=9000001006)" --eq "1|1|1" &&
bash doc/verify/api.sh --as staff --bizcode GET /lqg/qc/9000001002 | grep -qE '^(400|500)' &&
bash doc/verify/api.sh --as staff POST /lqg/qc/9000001001/sample-qc/image '{"slot":"orig","ossId":9000004005}' | jq -e '.code==200' &&
bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001001/sample-qc/image '{"slot":"orig","ossId":9000004006}' | grep -qE '^(400|500)' &&
bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001001/sample-qc/image '{"slot":"organoid_observe","ossId":9000004006}' | grep -qE '^(400|500)' &&
bash doc/verify/api.sh --as staff --bizcode POST /lqg/qc/9000001001/score/image '{"slot":"orig","ossId":9000004006}' | grep -qE '^(400|404|500)' &&
python3 doc/verify/db.py --sql "SELECT slot || ':' || count(*) FROM t_lqg_doc_image WHERE doc_type='sample_qc' AND doc_id=9000005001 AND del_flag='0' GROUP BY slot" --col-set "orig:3,observe:1" &&
bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001006/sample-qc '{"patientNo":"P-PROBE","samplingSite":"结肠"}' | jq -e '.code==200' &&
python3 doc/verify/db.py --sql "SELECT patient_no FROM t_lqg_qc_sample WHERE sample_id=9000001006" --eq "$(printf '%s' 'P-PROBE' | openssl enc -aes-128-ecb -K 4c7167546573744165734b6579233031 -nosalt -base64 -A)" &&
bash doc/verify/reseed.sh --yes >/dev/null
