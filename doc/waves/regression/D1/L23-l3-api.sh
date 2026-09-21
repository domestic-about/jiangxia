#!/usr/bin/env bash
# D1 / L3 邪路场景（QA 分片 L23，独立重跑）—— 逐条打印命令与结果，可重放。
#
# 覆盖 qa_scope L3 四类（每条都带「正向对照」，避免「一律拒绝也绿」的假绿）：
#   ① 外部账号带可用口令登工作台 → 被拒且 msg 走到「无权」分支（seed 里 extB 带口令）
#   ② 撤销内部授权 → 旧 token 立即 401；不能撤销自己；不能撤到没有管理员
#   ③ 外部改单位/组别 → 立刻回待核验；自填单位不选「新建/归并」不许通过；组别与单位不匹配被拒
#   ④ 不带 clientid 的登录请求拿不到 token；prod 配置文件里没有 mock-login 这个键
#
# 前置：后端 8081（.tmp/run-backend.sh）。本脚本会改库，开头与结尾都 reseed。
# ★ 不用 doc/verify/api.sh 的 --fresh-module：它内部用 `ps -o lstart=`，本沙箱 ps 被禁会 exit 2。
#   反 stale 由本目录的 L23-freshness.sh 单独负责（find 源码较 jar 新旧 + lsof 拿 PID + 进程持 jar + 启动晚于 jar）。
#
# 用法：bash doc/waves/regression/D1/L23-l3-api.sh
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
[ -f doc/verify/verify.env ] && set -a && . doc/verify/verify.env && set +a
BASE="${LQG_API_BASE:?缺 LQG_API_BASE}"
PC="${LQG_CLIENT_PC:?缺 LQG_CLIENT_PC}"
MP="${LQG_CLIENT_MP:?缺 LQG_CLIENT_MP}"

PASS=0; FAIL=0
ok()   { echo "  PASS  $1${2:+ — $2}"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1${2:+ — $2}"; FAIL=$((FAIL+1)); }
hdr()  { echo; echo "### $1"; }
tokens_clear() { rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*; }

tokens_clear
bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
tokens_clear

# ─────────────────────────────────────────────────────────────────────────────
hdr "① 外部账号带着可用口令登工作台必须被拒，且 msg 走「无权」分支"
echo "-- 病灶确认：extB(wx_13800000012) 的口令可用（length=60）+ 角色只有 lqg_external"
python3 doc/verify/db.py --sql "SELECT user_name || '|len=' || length(password) || '|role=' || (SELECT string_agg(r.role_key,',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id) FROM sys_user u WHERE user_name='wx_13800000012'"
echo "-- 正向对照：内部 lqg_13800000001 登得上"
L1="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $PC" -d "$(jq -nc --arg c "$PC" '{clientId:$c,grantType:"password",tenantId:"000000",username:"lqg_13800000001",password:"admin123"}')")"
[ "$(printf '%s' "$L1" | jq -r '.code')" = "200" ] && ok "内部账号工作台登录 200" || bad "内部账号工作台登录" "$(printf '%s' "$L1" | jq -c '{code,msg}')"
echo "-- 邪路：extB 用同一口令登工作台"
L2="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $PC" -d "$(jq -nc --arg c "$PC" '{clientId:$c,grantType:"password",tenantId:"000000",username:"wx_13800000012",password:"admin123"}')")"
echo "     $(printf '%s' "$L2" | jq -c '{code,msg,token:(.data.access_token//"ABSENT")}')"
[ "$(printf '%s' "$L2" | jq -r '.code')" != "200" ] && [ "$(printf '%s' "$L2" | jq -r '.data.access_token // "ABSENT"')" = "ABSENT" ] && ok "外部账号被拒且没有 token" || bad "外部账号被拒" "$(printf '%s' "$L2" | jq -c '{code}')"
printf '%s' "$L2" | jq -r '.msg' | grep -q '无权' && ok "msg 走到「无权」分支" "$(printf '%s' "$L2" | jq -r '.msg')" || bad "msg 不是「无权」分支" "$(printf '%s' "$L2" | jq -r '.msg')"
echo "-- 对照：口令错时的分支文案（与「无权」必须不同）"
L3="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $PC" -d "$(jq -nc --arg c "$PC" '{clientId:$c,grantType:"password",tenantId:"000000",username:"lqg_13800000001",password:"WRONGPWD"}')")"
echo "     $(printf '%s' "$L3" | jq -c '{code,msg}')"
printf '%s' "$L3" | jq -r '.msg' | grep -q '无权' && bad "口令错也报「无权」——两个分支没分开" || ok "口令错走的是密码分支（不是「无权」）"
echo "-- 换门也进不去：外部账号走 mp client+password / pc client+xcx"
printf '%s' "$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $MP" -d "$(jq -nc --arg c "$MP" '{clientId:$c,grantType:"password",tenantId:"000000",username:"wx_13800000012",password:"admin123"}')" | jq -c '{code,msg}')" | grep -q '"code":200' && bad "mp client+password 拿到了 token" || ok "mp client+password 被拒"
printf '%s' "$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $PC" -d "$(jq -nc --arg c "$PC" '{clientId:$c,grantType:"xcx",tenantId:"000000",xcxCode:"mock:extB",phoneCode:"mock:13800000012"}')" | jq -c '{code,msg}')" | grep -q '"code":200' && bad "pc client+xcx 拿到了 token" || ok "pc client+xcx 被拒"
echo "-- 不给外部账号设工作台口令"
B0="$(python3 doc/verify/db.py --sql "SELECT length(password) FROM sys_user WHERE user_id=9000000111")"
R="$(bash doc/verify/api.sh --as admin PUT /lqg/auth/staff/9000000111/reset-pwd '{"password":"Lqg@test123"}' | jq -r '.code')"
B1="$(python3 doc/verify/db.py --sql "SELECT length(password) FROM sys_user WHERE user_id=9000000111")"
[ "$R" != "200" ] && [ "$B0" = "$B1" ] && ok "reset-pwd 被拒且库里 password 长度不变（${B0}）" || bad "reset-pwd" "code=$R len ${B0}→$B1"

# ─────────────────────────────────────────────────────────────────────────────
hdr "② 撤销内部授权：旧 token 立即 401 / 不能撤自己 / 不能撤到没有管理员"
tokens_clear
echo "-- 用 13800000078 首登建外部账号 → 授权升级 → 撤销，全程用同一个旧 token"
bash doc/verify/api.sh --as phone:l3b:13800000078 GET /mp/me | jq -c '{code,identity:.data.identity}'
U="$(python3 doc/verify/db.py --sql "SELECT user_id FROM sys_user WHERE phonenumber='13800000078' AND del_flag='0'")"
bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000078","name":"L3乙","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -c '{code,upgraded:.data.upgraded,userId:.data.userId}'
python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM sys_user WHERE phonenumber='13800000078' AND del_flag='0') || '|' || (SELECT string_agg(r.role_key,',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=$U) || '|' || (SELECT count(*) FROM t_lqg_wx_bind WHERE user_id=$U AND del_flag='0')" --eq "1|lqg_internal|1" && ok "升级不新建：1 个账号 / 角色 lqg_internal / 微信绑定原样" || bad "升级路径"
I="$(bash doc/verify/api.sh --as phone:l3b:13800000078 GET /mp/me | jq -r '.data.identity')"
[ "$I" = "internal" ] && ok "升级后旧 token 看到 internal（没被踢）" || bad "升级后 /mp/me" "$I"
bash doc/verify/api.sh --as admin DELETE "/lqg/auth/staff/$U" | jq -c '{code,msg}'
B="$(bash doc/verify/api.sh --as phone:l3b:13800000078 --bizcode GET /mp/me)"
echo "     旧 token 打 /mp/me → $B"
printf '%s' "$B" | grep -q '^401' && ok "撤销后旧 token 立即 401" || bad "撤销后旧 token 没失效" "$B"
python3 doc/verify/db.py --sql "SELECT (SELECT count(*) FROM sys_user WHERE user_id=$U AND del_flag='0') || '|' || (SELECT string_agg(r.role_key,',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=$U) || '|' || (SELECT count(*) FROM t_lqg_wx_bind WHERE user_id=$U AND del_flag='0')" --eq "1|lqg_external|1" && ok "撤销≠删号：账号与微信绑定都还在，角色降回 103" || bad "撤销后的库内状态"
echo "-- 不能撤销自己（admin 撤 9000000100）"
R="$(bash doc/verify/api.sh --as admin --bizcode DELETE /lqg/auth/staff/9000000100)"
echo "     $R"
printf '%s' "$R" | grep -q '^500' && ok "撤销自己被拒（${R}）" || bad "撤销自己没被拒" "$R"
python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user_role WHERE user_id=9000000100 AND role_id=101" --eq 1 && ok "库里管理员角色没被先落盘（role 101 仍在）" || bad "撤销自己后角色被改了"
echo "-- 不能把最后一个管理员改成普通内部人员"
R="$(bash doc/verify/api.sh --as admin --bizcode PUT /lqg/auth/staff/9000000100/role '{"roleKey":"lqg_internal"}')"
echo "     $R"
printf '%s' "$R" | grep -q '^500' && ok "最后一个管理员改角色被拒（${R}）" || bad "改角色没被拒" "$R"
python3 doc/verify/db.py --sql "SELECT string_agg(role_id::text,',') FROM sys_user_role WHERE user_id=9000000100" --eq "101" && ok "库里角色没变（只有 101）" || bad "改角色后库变了"
echo "-- 不能撤到没有管理员（用上游超管发起，绕过「不能撤自己」这条，单独打最后管理员闸门）"
SA="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $PC" -d "$(jq -nc --arg c "$PC" '{clientId:$c,grantType:"password",tenantId:"000000",username:"admin",password:"admin123"}')" | jq -r '.data.access_token')"
R="$(curl -s -X DELETE "$BASE/lqg/auth/staff/9000000100" -H "Authorization: Bearer $SA" -H "clientid: $PC" | jq -c '{code,msg}')"
echo "     $R"
printf '%s' "$R" | grep -q '"code":500' && ok "撤掉唯一 lqg_admin 被拒（${R}）" || bad "最后管理员被撤掉了" "$R"
python3 doc/verify/db.py --sql "SELECT count(*) FROM sys_user_role WHERE user_id=9000000100 AND role_id=101" --eq 1 && ok "库里仍有一个管理员" || bad "库里没管理员了"
echo "-- 正向对照：先建第二个管理员，再用新管理员撤掉原管理员 → 允许（证明闸门不是「一律拒绝」）"
bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000079","name":"L3管理员乙","roleKey":"lqg_admin","password":"Lqg@test123"}' >/dev/null
T2="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -H "clientid: $PC" -d "$(jq -nc --arg c "$PC" '{clientId:$c,grantType:"password",tenantId:"000000",username:"lqg_13800000079",password:"Lqg@test123"}')" | jq -r '.data.access_token')"
R="$(curl -s -X DELETE "$BASE/lqg/auth/staff/9000000100" -H "Authorization: Bearer $T2" -H "clientid: $PC" | jq -c '{code,msg}')"
echo "     $R"
printf '%s' "$R" | grep -q '"code":200' && ok "有两个管理员时撤销被允许（${R}）" || bad "有两个管理员时仍拒绝" "$R"
tokens_clear; bash doc/verify/reseed.sh --yes >/dev/null; bash doc/waves/tools/clean-orphan-accounts.sh --yes >/dev/null 2>&1 || true; tokens_clear

# ─────────────────────────────────────────────────────────────────────────────
hdr "③ 外部改单位/组别立刻回待核验；自填单位不选新建/归并不过；组别与单位不匹配被拒"
echo "-- 起点：extA(9000000111) = A 医院/肝胆外科组/verified"
python3 doc/verify/db.py --sql "SELECT bind_status || '|' || unit_id || '|' || group_id || '|' || COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111"
echo "-- 不匹配：A 医院 + B 大学的 9000009103"
R="$(bash doc/verify/api.sh --as extA --bizcode PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009103}')"
echo "     $R"
printf '%s' "$R" | grep -q '^500' && ok "组别与单位不匹配被拒（${R}）" || bad "不匹配没被拒" "$R"
python3 doc/verify/db.py --sql "SELECT bind_status || '|' || unit_id || '|' || group_id FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq "verified|9000009001|9000009101" && ok "库里原样（verified / 原单位组）" || bad "库被改了"
echo "-- 合法改组别 → 立刻回 pending 且清 verified_by"
bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009102}' | jq -c '{code,msg}'
python3 doc/verify/db.py --sql "SELECT bind_status || '|' || group_id || '|' || COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq "pending|9000009102|-" && ok "改完立刻 pending 且 verified_by 清空" || bad "改组别后没回待核验"
echo "-- 夹带 bindStatus/verifiedBy 想跳过核验 → 仍 pending"
bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009101,"bindStatus":"verified","verifiedBy":9000000100}' >/dev/null
python3 doc/verify/db.py --sql "SELECT bind_status || '|' || COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" --eq "pending|-" && ok "请求体里的 bindStatus/verifiedBy 不生效" || bad "外部能自己写核验状态"
echo "-- 自填档案 extF(9000000116)：approve 不选新建/归并 → 拒"
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve"}' | jq -c '{code,msg}')"
echo "     $R"
printf '%s' "$R" | grep -q '"code":500' && ok "自填不选新建/归并被拒" || bad "自填直接 approve 通过了" "$R"
python3 doc/verify/db.py --sql "SELECT bind_status || '|' || COALESCE(unit_id::text,'-') || '|' || COALESCE(group_id::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq "pending|-|-" && ok "库里仍是 pending 且单位组别为空" || bad "自填档案被错误落盘"
echo "-- 合法：createUnit+createGroup → verified 且单位/组别落库"
bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","createUnit":true,"createGroup":true}' | jq -c '{code,msg}'
python3 doc/verify/db.py --sql "SELECT p.bind_status || '|' || u.unit_name || '|' || u.unit_status || '|' || g.group_name FROM t_lqg_ext_profile p JOIN t_lqg_source_unit u ON u.id=p.unit_id JOIN t_lqg_unit_group g ON g.id=p.group_id AND g.unit_id=u.id WHERE p.user_id=9000000116" --eq "verified|C 研究所|active|肿瘤组" && ok "新建路径落库正确" || bad "新建路径"
echo "-- 非法转移：verified → reject 被拒且库不变"
python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq verified
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"reject","reason":"x"}' | jq -c '{code,msg}')"
printf '%s' "$R" | grep -q '"code":500' && ok "verified→rejected 被拒" || bad "非法转移没被拒" "$R"
python3 doc/verify/db.py --sql "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" --eq verified && ok "库里仍是 verified" || bad "非法转移落了盘"
echo "-- 驳回必须带原因（extE 9000000115 是 pending）"
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000115/verify '{"action":"reject"}' | jq -c '{code,msg}')"
printf '%s' "$R" | grep -q '"code":500' && ok "reject 缺原因被拒" || bad "reject 缺原因通过了" "$R"
bash doc/verify/reseed.sh --yes >/dev/null; tokens_clear

# ─────────────────────────────────────────────────────────────────────────────
hdr "④ 不带 clientid 拿不到 token；prod 配置里没有 mock-login"
R="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"grantType":"password","tenantId":"000000","username":"lqgadmin","password":"admin123"}' | jq -c '{code,msg,token:(.data.access_token//"ABSENT")}')"
echo "     密码登录无 clientid：$R"
printf '%s' "$R" | grep -q 'ABSENT' && ok "无 clientid 的密码登录拿不到 token" || bad "无 clientid 也拿到 token" "$R"
R="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' -d '{"grantType":"xcx","tenantId":"000000","xcxCode":"mock:extA","phoneCode":"mock:13800000011"}' | jq -c '{code,msg,token:(.data.access_token//"ABSENT")}')"
echo "     xcx 登录无 clientid：$R"
printf '%s' "$R" | grep -q 'ABSENT' && ok "无 clientid 的 xcx 登录拿不到 token" || bad "无 clientid 的 xcx 拿到 token" "$R"
grep -rn 'mock-login' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml && bad "prod 配置里出现了 mock-login" || ok "prod 配置里没有 mock-login 这个键"
grep -q 'mock-login: true' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-dev.yml && ok "dev 里才有 mock-login: true（与 ADR-0008 一致）" || bad "dev 里没有 mock-login: true"

tokens_clear
echo
echo "== L3 ${PASS}/$((PASS+FAIL)) 通过 =="
[ "$FAIL" -eq 0 ] || exit 1
