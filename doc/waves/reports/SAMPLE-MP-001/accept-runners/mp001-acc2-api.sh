#!/usr/bin/env bash
# SAMPLE-MP-001 · accept 2 的 API 链（逐段原样实跑；长链包进函数判整条 rc）
#
# 与 ticket `run` 的唯一差异：
#   1. `pnpm build:mp-weixin` 那一段在 acc-build.sh（本文件只跑后端段）；
#   2. 去掉 `--fresh-module ruoyi-lqg`（本沙箱 ps 被禁，守卫恒 exit 2；等价证据见完工报告 §4.0）。
# 其余命令逐字照 accept 正文。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." || exit 1   # → 工作区根
WS="$(pwd)"

step() { printf '\n──── %s\n' "$*"; }
fail() { printf '✗ %s\n' "$*"; exit 1; }

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* || true

step "0) reseed"
bash doc/verify/reseed.sh --yes >/dev/null || fail "reseed"

step "1) staff GET /mp/int/sample/list?pageSize=100 → 9 条 + 1001 的 internalNo"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' \
  | jq -e '.code==200 and (.rows|length)==9 and ([.rows[]|select((.id|tostring)=="9000001001")|.internalNo]==["T-hli01"])' \
  || fail "S1"

step "2) extA GET /mp/int/sample/list → 403（角色闸，AUTH-EXT-001 那段从此收口）"
bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/sample/list' | grep -qE '^403' || fail "S2"

step "3) sort=recent → 只有中心内部人员经手过的两条，且 updateTime 为空 = 新增"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent' \
  | jq -e '([.rows[].id|tostring]|sort)==["9000001008","9000001009"] and ([.rows[]|select(.handlerName=="李工" and .mine==true and .updateTime==null)]|length)==2' \
  || fail "S3"

step "4) sort=recent&mine=true → 同上（本人）"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent&mine=true' \
  | jq -e '([.rows[].id|tostring]|sort)==["9000001008","9000001009"]' || fail "S4"

step "5) extA 改自己的待核验 1002（外部侧，用来验证「外部自己改的不算内部经手」）"
bash doc/verify/api.sh --as extA PUT /mp/ext/sample/9000001002 \
  '{"sourceUnitName":"A 医院","donorName":"测试供体乙","tissueType":"胆管组织"}' | jq -e '.code==200' || fail "S5"

step "6) recent 里没有 1002"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent' \
  | jq -e '([.rows[].id|tostring]|index("9000001002"))==null' || fail "S6"

step "7) staff PUT /mp/int/sample {\"id\":1001,\"tissueType\":…}（部分字段补丁）"
bash doc/verify/api.sh --as staff PUT /mp/int/sample '{"id":9000001001,"tissueType":"肝组织（更正）"}' \
  | jq -e '.code==200' || fail "S7"

step "8) 库内：tissue_type 改了、verify_status 仍是 valid、update_by 记了人"
python3 doc/verify/db.py --sql "SELECT tissue_type || '|' || verify_status || '|' || CASE WHEN update_by IS NULL THEN 'no' ELSE 'yes' END FROM t_lqg_sample WHERE id=9000001001" --eq "肝组织（更正）|valid|yes" || fail "S8"

step "9) staff PUT /mp/int/sample 改别人录的 1004（CR-20260918-07）"
bash doc/verify/api.sh --as staff PUT /mp/int/sample '{"id":9000001004,"remark":"别人录的也能改（CR-20260918-07）"}' \
  | jq -e '.code==200' || fail "S9"

step "10) recent 顺序：刚改的排最前，1001 行带经手人 / mine / updateTime"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent' \
  | jq -e '(.rows[0].id|tostring)=="9000001004" and (.rows[1].id|tostring)=="9000001001" and ([.rows[].id|tostring]|sort)==["9000001001","9000001004","9000001008","9000001009"] and ([.rows[]|select((.id|tostring)=="9000001001")|[.handlerName,.mine,(.updateTime!=null)]]==[["李工",true,true]])' \
  || fail "S10"

step "11) 库侧独立数（create_by / update_by 是不是内部账号）—— 与接口两侧同源"
python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_sample WHERE del_flag='0' AND (create_by IN (SELECT user_id FROM sys_user WHERE user_type='sys_user' AND del_flag='0') OR update_by IN (SELECT user_id FROM sys_user WHERE user_type='sys_user' AND del_flag='0'))" --col-set "9000001001,9000001004,9000001008,9000001009" || fail "S11"

step "12) sort=recent&mine=true → 四条（本人都经手过）"
bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100&sort=recent&mine=true' \
  | jq -e '([.rows[].id|tostring]|sort)==["9000001001","9000001004","9000001008","9000001009"]' || fail "S12"

step "13) staff 改待核验的 1002 → 被拒（400/500）"
bash doc/verify/api.sh --as staff --bizcode PUT /mp/int/sample '{"id":9000001002,"tissueType":"不该改进去"}' \
  | grep -qE '^(400|500)' || fail "S13"

step "14) 库里没有被改进去"
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE tissue_type='不该改进去'" --eq 0 || fail "S14"

step "15) 收尾 reseed"
bash doc/verify/reseed.sh --yes >/dev/null || fail "reseed 收尾"

printf '\nACCEPT-2-API GREEN (exit 0)\n'
