#!/usr/bin/env bash
# D2 r4 · L3 —— 独立邪路断言（接口/库层）。**本片自写**，不复用 regression/D2 既有脚本。
# 覆盖 qa_scope L3 第 1-5 条 + 第 7 条的接口段。
# 跑法：bash doc/waves/regression/D2/L3r4-api.sh
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)" || exit 2
API="bash doc/verify/api.sh"
DB="python3 doc/verify/db.py --quiet --sql"

PASS=0; FAIL=0
ck() { # ck <name> <cond:0|1> <detail>
  if [ "$2" = "1" ]; then PASS=$((PASS+1)); printf 'PASS  %s\n' "$1"
  else FAIL=$((FAIL+1)); printf 'FAIL  %s -- %s\n' "$1" "$3"; fi
}
# 业务码（若依把 404/403 包进响应体，HTTP 恒 200）
bcode() { $API --as "$1" "$2" "$3" ${4:+"$4"} 2>/dev/null | jq -r '.code // "ERR"' 2>/dev/null; }
bbody() { $API --as "$1" "$2" "$3" ${4:+"$4"} 2>/dev/null; }
rowjson() { $DB "select row_to_json(t)::text from t_lqg_sample t where id=$1"; }
rowcol() { $DB "select coalesce($2::text,'<null>') from t_lqg_sample where id=$1"; }
rowid_recent_organoid() { $DB "select id from t_lqg_sample where create_time > now() - interval '5 minutes' and sample_kind='organoid' and submit_source='external' order by id desc limit 1"; }
changed_keys() { jq -rn --argjson a "$1" --argjson b "$2" '[($a|keys_unsorted[]) as $k | select(($a[$k]|tostring) != ($b[$k]|tostring)) | $k] | join(",")' ; }
STAFF_UID="$($DB "select user_id from sys_user where phonenumber='13800000001' limit 1")"
ADMIN_UID="$($DB "select user_id from sys_user where phonenumber='13800000000' limit 1")"
echo "== staff_uid=$STAFF_UID admin_uid=$ADMIN_UID =="

echo
echo "##################### C1 越权（猜 id 取详情） #####################"
# 正向对照：extA/extB（同组）真的能取到 1001 —— 防「全站 404」式假绿
ck "C1-pos extA GET 1001 = 200" "$([ "$(bcode extA GET /mp/ext/sample/9000001001)" = 200 ] && echo 1)" "got $(bcode extA GET /mp/ext/sample/9000001001)"
ck "C1-pos extB(同组) GET 1001 = 200" "$([ "$(bcode extB GET /mp/ext/sample/9000001001)" = 200 ] && echo 1)" "got $(bcode extB GET /mp/ext/sample/9000001001)"
# ① extC 猜 1001
C1C="$(bbody extC GET /mp/ext/sample/9000001001)"
ck "C1-a extC GET 1001 code=404" "$([ "$(printf '%s' "$C1C" | jq -r .code)" = 404 ] && echo 1)" "$C1C"
ck "C1-a extC 响应体不含任何样本字段" "$(printf '%s' "$C1C" | grep -qE 'SJ90000001|T-hli01|internalNo|submitSource|receiveDate|verifyBy|donorName|hospitalNo|submitNo' && echo 0 || echo 1)" "$C1C"
ck "C1-a extC data 为 null" "$([ "$(printf '%s' "$C1C" | jq -r '.data')" = null ] && echo 1)" "$C1C"
# extE 同组未核验
C1E="$(bbody extE GET /mp/ext/sample/9000001001)"
ck "C1-b extE(同组未核验) GET 1001 code=404" "$([ "$(printf '%s' "$C1E" | jq -r .code)" = 404 ] && echo 1)" "$C1E"
ck "C1-b extE 响应体不含任何样本字段" "$(printf '%s' "$C1E" | grep -qE 'SJ90000001|T-hli01|internalNo|submitSource|receiveDate|verifyBy|donorName|hospitalNo|submitNo' && echo 0 || echo 1)" "$C1E"
ck "C1-pos extE GET 自己 1007 = 200" "$([ "$(bcode extE GET /mp/ext/sample/9000001007)" = 200 ] && echo 1)" "got $(bcode extE GET /mp/ext/sample/9000001007)"
# 新键 invalidReason 不夹带内部键（列表 ExtSampleVo / 详情 ExtSampleDetailVo）
LEAK='internalNo|verifyBy|submitSource|receiveDate|operatorName|invalidReason'
LV="$(bbody extA GET /mp/ext/sample/list)"
DET="$(bbody extA GET /mp/ext/sample/9000001001)"
ck "C1-c ExtSampleVo 行带 invalidReason 新键" "$([ "$(printf '%s' "$LV" | jq -r '[.rows[]|has("invalidReason")]|all')" = true ] && echo 1)" "$(printf '%s' "$LV" | jq -c '.rows[0]|keys')"
ck "C1-c ExtSampleVo 无 internalNo/verifyBy/submitSource/receiveDate/operatorName" "$(printf '%s' "$LV" | jq -r '[.rows[]|keys[]]|unique|join(",")' | grep -qE '^(.*,)?(internalNo|verifyBy|submitSource|receiveDate|operatorName)(,.*)?$' && echo 0 || echo 1)" "$(printf '%s' "$LV" | jq -c '[.rows[]|keys[]]|unique')"
ck "C1-c ExtSampleDetailVo 无 internalNo/verifyBy/submitSource/receiveDate/operatorName" "$(printf '%s' "$DET" | jq -r '.data|keys|join(",")' | grep -qE '^(.*,)?(internalNo|verifyBy|submitSource|receiveDate|operatorName)(,.*)?$' && echo 0 || echo 1)" "$(printf '%s' "$DET" | jq -c '.data|keys')"
ck "C1-c 详情 invalidReason 键在（新键真带出来）" "$([ "$(printf '%s' "$DET" | jq -r '.data|has("invalidReason")')" = true ] && echo 1)" ""
ck "C1-c 详情无 invalidReason 明文泄漏到别的键" "$([ "$(printf '%s' "$DET" | jq -r '.data.invalidReason // ""')" = "" ] && echo 1)" "1001 是 valid，invalidReason 应为空"

echo
echo "##################### C2 外部 PUT 夹带 / 可看不可改 / 有效自己改不了 #####################"
# C2-0 外部列表行为未受「内部来源单位 / keyword 筛法」改动影响（回归探针）
ck "C2-0 extA 外部列表 tissue = {1001,1002,1003,1004}" "$([ "$(bbody extA GET '/mp/ext/sample/list?sampleKind=tissue' | jq -r '[.rows[].id|tostring]|sort|join(",")')" = "9000001001,9000001002,9000001003,9000001004" ] && echo 1)" "got $(bbody extA GET '/mp/ext/sample/list?sampleKind=tissue' | jq -c '[.rows[].id|tostring]|sort')"
ck "C2-0 extC 外部列表 tissue = {1005}（异组不外泄）" "$([ "$(bbody extC GET '/mp/ext/sample/list?sampleKind=tissue' | jq -r '[.rows[].id|tostring]|sort|join(",")')" = "9000001005" ] && echo 1)" "got $(bbody extC GET '/mp/ext/sample/list?sampleKind=tissue' | jq -c '[.rows[].id|tostring]|sort')"
ck "C2-0 extA onlyMine = {1001,1002,1003}" "$([ "$(bbody extA GET '/mp/ext/sample/list?onlyMine=true' | jq -r '[.rows[].id|tostring]|sort|join(",")')" = "9000001001,9000001002,9000001003" ] && echo 1)" "got $(bbody extA GET '/mp/ext/sample/list?onlyMine=true' | jq -c '[.rows[].id|tostring]|sort')"
B4="$(rowjson 9000001002)"
TAMPER='{"sourceUnitName":"A 医院","donorName":"测试供体甲","gender":"男","age":"55","hospitalNo":"ZY0000001","tissueType":"肝","hasPathology":"1","remark":"L3r4-c2-tamper","internalNo":"HACK-C2","verifyStatus":"valid","submitSource":"internal","receiveDate":"2020-01-01","sampleKind":"organoid","submitNo":"HACK-NO","verifyBy":1,"submitterId":1}'
R2A="$(bbody extA PUT /mp/ext/sample/9000001002 "$TAMPER")"
A2="$(rowjson 9000001002)"
CK2="$(changed_keys "$B4" "$A2")"
echo "C2-a resp=$R2A"; echo "C2-a changed_cols=[$CK2]"
printf 'note: C2-a source_unit_id before=%s after=%s ; source_unit_name after=%s\n' \
  "$(printf '%s' "$B4" | jq -r '.source_unit_id')" "$(printf '%s' "$A2" | jq -r '.source_unit_id')" "$(printf '%s' "$A2" | jq -r '.source_unit_name')"
ck "C2-a 夹带列全部不生效(internal_no/verify_status/submit_source/receive_date)" \
   "$([ "$(rowcol 9000001002 internal_no)" = "<null>" ] && [ "$(rowcol 9000001002 verify_status)" = pending ] && [ "$(rowcol 9000001002 submit_source)" = external ] && [ "$(rowcol 9000001002 receive_date)" = "<null>" ] && [ "$(rowcol 9000001002 sample_kind)" = tissue ] && echo 1)" \
   "internal_no=$(rowcol 9000001002 internal_no) verify=$(rowcol 9000001002 verify_status) src=$(rowcol 9000001002 submit_source) recv=$(rowcol 9000001002 receive_date) kind=$(rowcol 9000001002 sample_kind)"
# 外部可改字段 = 送检段（ExtSampleSubmitBo 的 9 个键）+ 审计列；source_unit_id/name 属送检段，外部口合法可写。
# 断言「变化的列 ⊆ 允许集」，不要求逐列都变。
ALLOWED=" source_unit_id source_unit_name donor_name gender age hospital_no tissue_type has_pathology remark update_by update_time "
BAD=""
for k in $(printf '%s' "$CK2" | tr ',' ' '); do case "$ALLOWED" in *" $k "*) ;; *) BAD="$BAD $k" ;; esac; done
ck "C2-a 变化的列 ⊆ 外部可改字段(送检段+审计列)，无越界列" "$([ -z "$BAD" ] && echo 1)" "越界列=[$BAD] changed=[$CK2]"
ck "C2-a 请求真被处理过（remark 落库 = 非静默丢弃）" "$([ "$(rowcol 9000001002 remark)" = "L3r4-c2-tamper" ] || printf '%s' "$R2A" | jq -e '.code!=200' >/dev/null && echo 1)" "remark=$(rowcol 9000001002 remark) resp=$R2A"
# 同组别人的样本：可看不可改
B4="$(rowjson 9000001002)"
ck "C2-b extB 可看 extA 的 1002" "$([ "$(bcode extB GET /mp/ext/sample/9000001002)" = 200 ] && echo 1)" "got $(bcode extB GET /mp/ext/sample/9000001002)"
R2B="$(bbody extB PUT /mp/ext/sample/9000001002 '{"donorName":"HACK-B","remark":"HACK-B"}')"
A2="$(rowjson 9000001002)"
ck "C2-b extB 改不了 extA 的 1002（被拒）" "$([ "$(printf '%s' "$R2B" | jq -r .code)" != 200 ] && echo 1)" "$R2B"
ck "C2-b 被拒后库内不变" "$([ "$B4" = "$A2" ] && echo 1)" "before≠after"
# 已有效的自己也改不了
B1="$(rowjson 9000001001)"
R2C="$(bbody extA PUT /mp/ext/sample/9000001001 '{"donorName":"HACK-A","remark":"HACK-A","internalNo":"HACK-C2C"}')"
A1="$(rowjson 9000001001)"
ck "C2-c extA 改不了自己已有效的 1001（被拒）" "$([ "$(printf '%s' "$R2C" | jq -r .code)" != 200 ] && echo 1)" "$R2C"
ck "C2-c 被拒后库内不变" "$([ "$B1" = "$A1" ] && echo 1)" "changed=[$(changed_keys "$B1" "$A1")]"

echo
echo "##################### C3 内部经小程序 PUT #####################"
B2="$(rowjson 9000001002)"
R3A="$(bbody staff PUT /mp/int/sample '{"id":9000001002,"remark":"L3r4-c3-pending"}')"
A2="$(rowjson 9000001002)"
ck "C3-a staff PUT 待核验外部样本 1002 → 被拒(非200)" "$([ "$(printf '%s' "$R3A" | jq -r .code)" != 200 ] && echo 1)" "$R3A"
ck "C3-a 被拒后库内不变" "$([ "$B2" = "$A2" ] && echo 1)" "changed=[$(changed_keys "$B2" "$A2")]"
B3="$(rowjson 9000001003)"
R3B="$(bbody staff PUT /mp/int/sample '{"id":9000001003,"remark":"L3r4-c3-invalid"}')"
A3="$(rowjson 9000001003)"
ck "C3-b staff PUT 无效外部样本 1003 → 被拒(非200)" "$([ "$(printf '%s' "$R3B" | jq -r .code)" != 200 ] && echo 1)" "$R3B"
ck "C3-b 被拒后库内不变" "$([ "$B3" = "$A3" ] && echo 1)" "changed=[$(changed_keys "$B3" "$A3")]"
# 内部改有效样本 → update_by 有值
R3C="$(bbody staff PUT /mp/int/sample '{"id":9000001001,"remark":"L3r4-c3-valid"}')"
UB="$(rowcol 9000001001 update_by)"; UT="$(rowcol 9000001001 update_time)"; RM="$(rowcol 9000001001 remark)"
ck "C3-c staff 改有效样本 1001 → 200" "$([ "$(printf '%s' "$R3C" | jq -r .code)" = 200 ] && echo 1)" "$R3C"
ck "C3-c update_by 有值且 = staff($STAFF_UID)" "$([ "$UB" = "$STAFF_UID" ] && echo 1)" "update_by=$UB"
ck "C3-c 改动真的落库（remark）" "$([ "$RM" = "L3r4-c3-valid" ] && echo 1)" "remark=$RM"
ck "C3-c update_time 是本次（5 分钟内）" "$($DB "select count(*) from t_lqg_sample where id=9000001001 and update_time > now() - interval '5 minutes'")" "$UT"
# sampleKind 保护：只拦不一致，不误伤一致
B1="$(rowjson 9000001001)"
R3D="$(bbody staff PUT /mp/int/sample '{"id":9000001001,"sampleKind":"organoid","remark":"L3r4-c3-kindbad"}')"
A1="$(rowjson 9000001001)"
ck "C3-d sampleKind 与库里不一致 → 400" "$([ "$(printf '%s' "$R3D" | jq -r .code)" = 400 ] && echo 1)" "$R3D"
ck "C3-d 被拒后库内不变" "$([ "$B1" = "$A1" ] && echo 1)" "changed=[$(changed_keys "$B1" "$A1")]"
R3E="$(bbody staff PUT /mp/int/sample '{"id":9000001001,"sampleKind":"tissue","remark":"L3r4-c3-kindok"}')"
ck "C3-e sampleKind 一致时不误伤 → 200 且落库" "$([ "$(printf '%s' "$R3E" | jq -r .code)" = 200 ] && [ "$(rowcol 9000001001 remark)" = "L3r4-c3-kindok" ] && echo 1)" "$R3E remark=$(rowcol 9000001001 remark)"
R3F="$(bbody staff PUT /mp/int/sample '{"id":9000001001,"remark":"L3r4-c3-nokind"}')"
ck "C3-f 完全不传 sampleKind 的老式修改照常 200" "$([ "$(printf '%s' "$R3F" | jq -r .code)" = 200 ] && [ "$(rowcol 9000001001 remark)" = "L3r4-c3-nokind" ] && echo 1)" "$R3F remark=$(rowcol 9000001001 remark)"

echo
echo "##################### C4 角色闸 #####################"
ck "C4-a 外部 extA 打 /lqg/sample/list → 403" "$([ "$(bcode extA GET '/lqg/sample/list?pageNum=1&pageSize=1')" = 403 ] && echo 1)" "got $(bcode extA GET '/lqg/sample/list?pageNum=1&pageSize=1')"
ck "C4-a 外部 extA 打 /mp/int/sample/list → 403" "$([ "$(bcode extA GET '/mp/int/sample/list')" = 403 ] && echo 1)" "got $(bcode extA GET '/mp/int/sample/list')"
ck "C4-a 外部 extA POST /mp/int/sample → 403" "$([ "$(bcode extA POST /mp/int/sample '{"donorName":"X"}')" = 403 ] && echo 1)" "got $(bcode extA POST /mp/int/sample '{"donorName":"X"}')"
ck "C4-b 内部 staff 打 /mp/ext/sample/list → 403" "$([ "$(bcode staff GET '/mp/ext/sample/list')" = 403 ] && echo 1)" "got $(bcode staff GET '/mp/ext/sample/list')"
ck "C4-b 内部 staff 打 /mp/ext/organoid → 403" "$([ "$(bcode staff POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"x"}')" = 403 ] && echo 1)" "got $(bcode staff POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"x"}')"
ck "C4-pos staff /mp/int/sample/list = 200" "$([ "$(bcode staff GET '/mp/int/sample/list')" = 200 ] && echo 1)" "got $(bcode staff GET '/mp/int/sample/list')"
ck "C4-pos staff /lqg/sample/list = 200" "$([ "$(bcode staff GET '/lqg/sample/list?pageNum=1&pageSize=1')" = 200 ] && echo 1)" "got $(bcode staff GET '/lqg/sample/list?pageNum=1&pageSize=1')"
ck "C4-pos extA /mp/ext/sample/list = 200" "$([ "$(bcode extA GET '/mp/ext/sample/list')" = 200 ] && echo 1)" "got $(bcode extA GET '/mp/ext/sample/list')"

echo
echo "##################### C5 核验状态机 #####################"
VB="$(rowjson 9000001002)"
# 判有效缺内部编号
R5A="$(bbody admin PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-22"}')"
VA="$(rowjson 9000001002)"
ck "C5-a 判有效缺内部编号 → 被拒" "$([ "$(printf '%s' "$R5A" | jq -r .code)" != 200 ] && echo 1)" "$R5A"
ck "C5-a 被拒后库内不变" "$([ "$VB" = "$VA" ] && echo 1)" "changed=[$(changed_keys "$VB" "$VA")]"
# 内部编号撞号（T-hli01 是 1001 的、未软删）
R5B="$(bbody admin PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-22","internalNo":"T-hli01"}')"
VB2="$(rowjson 9000001002)"
ck "C5-b 内部编号撞号(T-hli01) → 被拒" "$([ "$(printf '%s' "$R5B" | jq -r .code)" != 200 ] && echo 1)" "$R5B"
ck "C5-b 被拒后库内不变" "$([ "$VA" = "$VB2" ] && echo 1)" "changed=[$(changed_keys "$VA" "$VB2")]"
# 判无效缺原因
R5C="$(bbody admin PUT /lqg/sample/9000001002/verify '{"action":"invalid"}')"
VC="$(rowjson 9000001002)"
ck "C5-c 判无效缺原因 → 被拒" "$([ "$(printf '%s' "$R5C" | jq -r .code)" != 200 ] && echo 1)" "$R5C"
ck "C5-c 被拒后库内不变" "$([ "$VB2" = "$VC" ] && echo 1)" "changed=[$(changed_keys "$VB2" "$VC")]"
# 软删的内部编号可重用（T-del99 属于 del_flag=1 的 1010）
DEL="$($DB "select internal_no||'|'||del_flag from t_lqg_sample where id=9000001010")"
R5D="$(bbody admin PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-22","internalNo":"T-del99","operatorName":"李工"}')"
ck "C5-d 软删编号 T-del99 可重用 → 200" "$([ "$(printf '%s' "$R5D" | jq -r .code)" = 200 ] && echo 1)" "$R5D (1010 = $DEL)"
ck "C5-d 1002 真变 valid 且 internal_no=T-del99" "$([ "$(rowcol 9000001002 verify_status)" = valid ] && [ "$(rowcol 9000001002 internal_no)" = "T-del99" ] && echo 1)" "verify=$(rowcol 9000001002 verify_status) no=$(rowcol 9000001002 internal_no)"
# 撞号反向对照：现在 T-del99 已被 1002 占用（未软删），再给 1007 用应被拒
R5E="$(bbody admin PUT /lqg/sample/9000001007/verify '{"action":"valid","receiveDate":"2026-09-22","internalNo":"T-del99"}')"
ck "C5-e 反向对照：T-del99 被 1002 占用后给 1007 用 → 被拒" "$([ "$(printf '%s' "$R5E" | jq -r .code)" != 200 ] && echo 1)" "$R5E"

echo
echo "##################### C7（接口段）外部类器官夹带内部字段 #####################"
ORG='{"sourceUnitName":"A 医院","organoidType":"L3r4类器官","remark":"L3r4-c7","internalNo":"HACK-ORG","receiveDate":"2020-01-01","verifyStatus":"valid","submitSource":"internal","sampleKind":"tissue","submitNo":"HACK-ORG-NO"}'
R7="$(bbody extA POST /mp/ext/organoid "$ORG")"
NID="$(rowid_recent_organoid)"
echo "C7 resp=$R7 new_id=$NID"
ck "C7 POST /mp/ext/organoid 成功建行" "$([ "$(printf '%s' "$R7" | jq -r .code)" = 200 ] && [ -n "$NID" ] && echo 1)" "$R7"
if [ -n "$NID" ]; then
  ck "C7 夹带 internalNo 不生效" "$([ "$(rowcol "$NID" internal_no)" = "<null>" ] && echo 1)" "internal_no=$(rowcol "$NID" internal_no)"
  ck "C7 夹带 receiveDate 不生效" "$([ "$(rowcol "$NID" receive_date)" = "<null>" ] && echo 1)" "receive_date=$(rowcol "$NID" receive_date)"
  ck "C7 夹带 verifyStatus 不生效（恒 pending）" "$([ "$(rowcol "$NID" verify_status)" = pending ] && echo 1)" "verify_status=$(rowcol "$NID" verify_status)"
  ck "C7 夹带 submitSource 不生效（恒 external）" "$([ "$(rowcol "$NID" submit_source)" = external ] && echo 1)" "submit_source=$(rowcol "$NID" submit_source)"
  ck "C7 夹带 sampleKind 不生效（恒 organoid）" "$([ "$(rowcol "$NID" sample_kind)" = organoid ] && echo 1)" "sample_kind=$(rowcol "$NID" sample_kind)"
  ck "C7 夹带 submitNo 不生效（服务端自生成）" "$([ "$(rowcol "$NID" submit_no)" != "HACK-ORG-NO" ] && echo 1)" "submit_no=$(rowcol "$NID" submit_no)"
fi

echo
echo "########## 契约码核对（信息项，不参与 pass/fail 计数；用于 doc-drift 取证） ##########"
# api-contract.md 第 10 行「参数 / 业务校验不过 code=400 类」；第 49 行「待核验、无效的外部样本 → 400」
# MpSampleService 类注释也写「PUT 直接 400 / 待核验、无效 → 400」，但实现抛的是无 code 的 ServiceException（默认 500）
printf 'drift: PUT /mp/int/sample(pending 1003)     契约=400  实测=%s\n' "$(printf '%s' "$R3B" | jq -r .code)"
printf 'drift: PUT /mp/int/sample(invalid 1003)     契约=400  实测=%s\n' "$(printf '%s' "$R3B" | jq -r .code)"
printf 'drift: verify valid 缺内部编号              契约=400类 实测=%s\n' "$(printf '%s' "$R5A" | jq -r .code)"
printf 'drift: verify invalid 缺原因                契约=400类 实测=%s\n' "$(printf '%s' "$R5C" | jq -r .code)"
printf 'drift: sampleKind 不一致（对照组，已按契约 400）          实测=%s\n' "$(printf '%s' "$R3D" | jq -r .code)"

echo
echo "== D2-r4-L3-api: PASS=$PASS FAIL=$FAIL =="
[ "$FAIL" = 0 ] || exit 1
