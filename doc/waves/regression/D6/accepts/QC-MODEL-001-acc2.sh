#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-MODEL-001 accept[2] form=DATA
# 评分由后端按字典回填：前端夹带的假分值不生效、0 分档不被当成未选、没选全合计为空；落库的分值与字典表两侧对得上
# 归一化：NF1
set -euo pipefail
bash doc/verify/reseed.sh --yes >/dev/null &&
bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/score '{"preCultureLevel":"gt80","cultureDaysLevel":"gt14","organoidCountLevel":"lt100","diameterLevel":"gt100","preCultureScore":99,"cultureDaysScore":99,"totalScore":100}' | jq -e '.code==200' &&
python3 doc/verify/db.py --sql "SELECT pre_culture_score || '|' || culture_days_score || '|' || organoid_count_score || '|' || diameter_score || '|' || total_score FROM t_lqg_qc_score WHERE sample_id=9000001005 AND del_flag='0'" --eq "20|0|0|30|50" &&
python3 doc/verify/db.py --sql "SELECT s.sample_id FROM t_lqg_qc_score s WHERE s.del_flag='0' AND s.total_score IS NOT NULL AND s.total_score <> (SELECT sum(d.remark::int) FROM sys_dict_data d WHERE (d.dict_type, d.dict_value) IN (('lqg_score_pre_culture', s.pre_culture_level), ('lqg_score_culture_days', s.culture_days_level), ('lqg_score_count', s.organoid_count_level), ('lqg_score_diameter', s.diameter_level)))" --empty &&
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_qc_score WHERE del_flag='0' AND total_score IS NOT NULL" --eq 3 &&
bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/score '{"preCultureLevel":"gt80","cultureDaysLevel":null,"organoidCountLevel":"lt100","diameterLevel":"gt100"}' | jq -e '.code==200' &&
python3 doc/verify/db.py --sql "SELECT COALESCE(total_score::text,'NULL') || '|' || COALESCE(culture_days_score::text,'NULL') FROM t_lqg_qc_score WHERE sample_id=9000001005 AND del_flag='0'" --eq "NULL|NULL" &&
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/qc/9000001005/score '{"preCultureLevel":"gt999"}' | grep -qE '^(400|500)' &&
bash doc/verify/reseed.sh --yes >/dev/null
