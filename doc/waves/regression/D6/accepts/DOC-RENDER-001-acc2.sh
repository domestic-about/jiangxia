#!/usr/bin/env bash
# 票面 accept（逐字重放）：DOC-RENDER-001 accept[2] form=STATE
# 渲染产物表与 SSOT 相符；指纹缓存成立：内容没变不重出、改了任何一处都重出、内外部各一份互不覆盖
# 归一化：NF1,NF2
set -euo pipefail
python3 doc/verify/ddl_vs_ssot.py --table t_lqg_doc_file --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
bash doc/verify/reseed.sh --yes >/dev/null &&
r() { bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001001/organoid_qc/render?audience=$1" | jq -e '.data.status=="done"' >/dev/null; } &&
snap() { python3 doc/verify/db.py --quiet --sql "SELECT audience || ':' || content_hash || ':' || oss_id || ':' || rendered_time FROM t_lqg_doc_file WHERE sample_id=9000001001 AND doc_kind='organoid_qc' AND file_format='docx' AND del_flag='0' ORDER BY audience" | tr '\n' ','; } &&
bash doc/verify/api.sh --as staff GET /lqg/sys/ping >/dev/null && r internal && r external && S1="$(snap)" && r internal && r external && S2="$(snap)" && test "${S1}" = "${S2}" &&
test "$(printf '%s' "${S1}" | tr ',' '\n' | grep -c .)" = 2 &&
bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001001/organoid-qc '{"growthState":"指纹探针：已改"}' | jq -e '.code==200' &&
r internal && S3="$(snap)" && test "${S1}" != "${S3}" &&
(cd code/RuoYi-Vue-Plus && mvn -s /Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.mvn-settings.xml -Dmaven.repo.local=/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.m2repo -Duser.home=/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.buildhome -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocFingerprintTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
bash doc/verify/reseed.sh --yes >/dev/null
