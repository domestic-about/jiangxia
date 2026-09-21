#!/usr/bin/env bash
# D1 r2 / L3 独立邪路断言（QA L3 分片自写；不复用 r1 的 L23-l3-api.sh 结论，只重放它作对照）
#
# 设计原则（对着 doc/verify/README.md 的坑 1/2/7）：
#   - 每条「拒绝」用正码白名单（.code==500）+ msg 分支关键词，不用 `.code != 200` 黑名单；
#   - 每条「拒绝」后面必跟一条「库内不变」断言（只看业务码的话，先落盘再返回 500 也是绿的）；
#   - 每条邪路都带一条正向对照，避免「一律拒绝」也绿。
#
# 前置：后端 8081（.tmp/run-backend.sh，dev + api-decrypt.enabled=false）。脚本会改库，开头/分段前 reseed。
# 用法：bash doc/waves/regression/D1/L3-r2-independent.sh
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
[ -f doc/verify/verify.env ] && set -a && . doc/verify/verify.env && set +a
BASE="${LQG_API_BASE:?缺 LQG_API_BASE}"; PC="${LQG_CLIENT_PC:?}"; MP="${LQG_CLIENT_MP:?}"

P=0; F=0
ok()  { echo "  PASS  $1${2:+ — $2}"; P=$((P+1)); }
bad() { echo "  FAIL  $1${2:+ — $2}"; F=$((F+1)); }
hdr() { echo; echo "### $1"; }
tc()  { rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*; }
q()   { python3 doc/verify/db.py --sql "$1"; }
assert_eq() { if q "$1" --eq "$2" >/dev/null 2>&1; then ok "$3" "$2"; else bad "$3" "期望 $2，实际 $(q "$1" 2>/dev/null)"; fi; }
assert_ne() { if q "$1" --eq "$2" >/dev/null 2>&1; then bad "$3" "实际仍是 $2"; else ok "$3"; fi; }
reseed() { bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed 失败"; exit 2; }
           # ★ reseed 不会清运行时建的账号（如正向对照建的第二个 lqg_admin）——必须补 clean-orphan，
           #   否则下一段会带着 2 个管理员起跑，「最后一个管理员」闸门合理地不触发，断言全歪（自测踩过）。
           bash doc/waves/tools/clean-orphan-accounts.sh --yes >/dev/null 2>&1 || true
           tc; }

login_pw() { # $1 username $2 password  → 完整响应
  curl -sS --max-time 15 -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${PC}" \
    -d "$(jq -nc --arg c "${PC}" --arg u "$1" --arg p "$2" '{clientId:$c,grantType:"password",tenantId:"000000",username:$u,password:$p}')"
}
pc_tok() { login_pw "$1" "$2" | jq -r '.data.access_token // empty'; }
mp_tok() { # $1 mock key $2 phone
  curl -sS --max-time 15 -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${MP}" \
    -d "$(jq -nc --arg c "${MP}" --arg x "mock:$1" --arg p "mock:$2" '{clientId:$c,grantType:"xcx",tenantId:"000000",xcxCode:$x,phoneCode:$p}')" \
    | jq -r '.data.access_token // empty'
}
# ★ token 与 clientid 必须配套：pc token 用 PC 头，mp token 用 MP 头，否则一律 401（自测踩过）。
call_cli() { # $1 clientid $2 token $3 method $4 path [$5 body]
  local c="$1" t="$2" m="$3" p="$4" b="${5:-}"
  if [ -n "${b}" ]; then curl -sS --max-time 30 -X "${m}" "${BASE}${p}" -H "Authorization: Bearer ${t}" -H "clientid: ${c}" -H 'Content-Type: application/json' -d "${b}"
  else curl -sS --max-time 30 -X "${m}" "${BASE}${p}" -H "Authorization: Bearer ${t}" -H "clientid: ${c}"; fi
}
call()  { call_cli "${PC}" "$@"; }   # pc token
mcall() { call_cli "${MP}" "$@"; }   # mp token
code_of() { printf '%s' "$1" | jq -r '.code'; }
msg_of()  { printf '%s' "$1" | jq -r '.msg // ""'; }
tok_of()  { printf '%s' "$1" | jq -r '.data.access_token // "ABSENT"'; }

reseed

# ═══════════════════════════════════════════════════════════════════════════
hdr "① 外部账号带可用口令登工作台：被拒且走「无权」；正反对照"
echo "-- 独立证明 extB 的口令 admin123 真的可用（bcrypt 直校 seed 哈希，不经 app）"
BC="$(python3 - <<'PY'
import bcrypt, subprocess
h = subprocess.run(["python3","doc/verify/db.py","--sql",
    "SELECT password FROM sys_user WHERE user_name='wx_13800000012'"],
    capture_output=True, text=True).stdout.strip()
print("yes" if h and bcrypt.checkpw(b"admin123", h.encode()) else "no")
PY
)"
[ "${BC}" = "yes" ] && ok "extB 哈希与 admin123 匹配（密码确实可用）" || bad "extB 口令不可用 → ①的『无权』结论不成立"

echo "-- 正向对照：内部 lqg_13800000001 登得上并有 token"
R="$(login_pw lqg_13800000001 admin123)"
C="$(code_of "$R")"; T="$(tok_of "$R")"
[ "${C}" = "200" ] && [ "${T}" != "ABSENT" ] && ok "内部登工作台 code=200 且拿到 token" || bad "内部登不上（对照失败）" "code=${C} tok=${T:0:12}"

echo "-- 邪路：extB 用同一口令登工作台"
R="$(login_pw wx_13800000012 admin123)"
echo "     $(printf '%s' "$R" | jq -c '{code,msg}')  token=$(tok_of "$R" | cut -c1-8)"
C="$(code_of "$R")"; M="$(msg_of "$R")"; T="$(tok_of "$R")"
[ "${C}" = "500" ] && ok "白名单正码：extB 被拒 code=500（非 404/000）" || bad "拒绝码不是 500" "code=${C}"
[ "${T}" = "ABSENT" ] && ok "响应里没有任何 access_token" || bad "extB 拿到了 token！" "${T:0:16}"
printf '%s' "${R}" | jq -e '.data == null' >/dev/null 2>&1 && ok "整个 data 都是 null（token 没有藏在别的字段）" || bad "被拒响应的 data 不是 null，可能夹带 token" "$(printf '%s' "$R"|jq -c '.data')"
printf '%s' "${M}" | grep -q '无权' && ok "msg 走「无权」分支" "${M}" || bad "msg 不在无权分支" "${M}"
printf '%s' "${M}" | grep -qi 'password' && bad "msg 混入密码分支文案" "${M}" || ok "msg 不含密码错误文案"

echo "-- 反证分支确实分开了：先把口令写错，再比两个身份的 msg"
RW="$(login_pw lqg_13800000001 'DefinitelyWrong#1')"
MW="$(msg_of "$RW")"
echo "     内部+错口令: $(printf '%s' "$RW" | jq -c '{code,msg}')"
printf '%s' "${MW}" | grep -q '无权' && bad "错口令也报无权——两个分支没分开" "${MW}" || ok "内部错口令不走无权分支" "${MW}"
printf '%s' "${MW}" | grep -qi 'password' && ok "内部错口令 msg 明确是密码错" "${MW}" || bad "内部错口令 msg 不像密码分支" "${MW}"
RE="$(login_pw wx_13800000012 'DefinitelyWrong#1')"
ME="$(msg_of "$RE")"
echo "     extB+错口令: $(printf '%s' "$RE" | jq -c '{code,msg}')"
printf '%s' "${ME}" | grep -qi 'password' && ok "extB 错口令先走密码分支 → ①的『无权』确是口令对后才判的角色" "${ME}" \
  || bad "extB 错口令未走密码分支：①的『无权』可能是空断言" "${ME}"
printf '%s' "${ME}" | grep -q '无权' && bad "extB 错口令也报无权（结论被架空）" "${ME}" || ok "extB 错口令不报无权"

echo "-- 换门也进不去：mp client+password / pc client+xcx"
R="$(curl -sS -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${MP}" -d "$(jq -nc --arg c "${MP}" '{clientId:$c,grantType:"password",tenantId:"000000",username:"wx_13800000012",password:"admin123"}')")"
[ "$(tok_of "$R")" = "ABSENT" ] && ok "mp client + password 拿不到 token" "$(printf '%s' "$R"|jq -r '.msg')" || bad "mp client+password 拿到 token"
R="$(curl -sS -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${PC}" -d "$(jq -nc --arg c "${PC}" '{clientId:$c,grantType:"xcx",tenantId:"000000",xcxCode:"mock:extB",phoneCode:"mock:13800000012"}')")"
[ "$(tok_of "$R")" = "ABSENT" ] && ok "pc client + xcx 拿不到 token" "$(printf '%s' "$R"|jq -r '.msg')" || bad "pc client+xcx 拿到 token"

echo "-- 库内不变：口令/角色/账号都没被这些失败登录动过"
assert_eq "SELECT length(password)||'|'||del_flag FROM sys_user WHERE user_name='wx_13800000012'" "60|0" "extB 口令哈希与账号原样"
assert_eq "SELECT string_agg(r.role_key,',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id JOIN sys_user u ON u.user_id=ur.user_id WHERE u.user_name='wx_13800000012'" "lqg_external" "extB 仍是 lqg_external"

# ═══════════════════════════════════════════════════════════════════════════
hdr "② 撤销内部授权：旧 token 立 401 / 不能撤自己 / 不能撤到没有 lqg_admin / 撤销≠删号"
reseed
ATOK="$(pc_tok lqgadmin admin123)"
STOK="$(mp_tok staff 13800000001)"
echo "-- 起点：staff(9000000101) 内部，持有 mp token(len=${#STOK})，微信绑定 mock-openid-staff"
echo "     起点 lqg_admin 数=$(q "SELECT count(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE r.role_key='lqg_admin'")（必须是 1，否则最后管理员闸门不该触发）"
R="$(mcall "${STOK}" GET /mp/me)"; echo "     撤销前 /mp/me: $(printf '%s' "$R"|jq -c '{code,identity:.data.identity}')"
[ "$(code_of "$R")" = "200" ] && ok "撤销前旧 token 可用（对照）" || bad "撤销前 token 就不可用，后面的 401 无意义"
# 端点二必须用一个「真实支持的方法」探：GET /mp/ext/profile 恒 405（405 在鉴权前就由 handler 映射层决定，
# 拿它断 token 失效会永远看不到 401——自测踩过）。PUT 才是该端点的真实方法。
PBODY='{"realName":"李工","unitId":9000009001,"groupId":9000009101}'
R="$(mcall "${STOK}" PUT /mp/ext/profile "${PBODY}")"; echo "     撤销前 PUT /mp/ext/profile: $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" != "401" ] && ok "第二端点撤销前不是 401（token 有效，可作对照）" || bad "第二端点撤销前就 401，对照失效" "code=$(code_of "$R")"
BIND0="$(q "SELECT count(*) FROM t_lqg_wx_bind WHERE user_id=9000000101 AND del_flag='0'")"

echo "-- 撤销 staff 的内部授权（admin 发起）"
R="$(call "${ATOK}" DELETE /lqg/auth/staff/9000000101)"; echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "200" ] && ok "撤销成功 code=200" || bad "撤销失败" "$(code_of "$R")"
R="$(mcall "${STOK}" GET /mp/me)"; echo "     撤销后 /mp/me: $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "401" ] && ok "撤销后旧 token 立即 401（端点一）" || bad "旧 token 未立即失效" "code=$(code_of "$R")"
R="$(mcall "${STOK}" PUT /mp/ext/profile "${PBODY}")"; echo "     撤销后 PUT /mp/ext/profile: $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "401" ] && ok "撤销后旧 token 在端点二（PUT，真实方法）也 401（不是 405/404 恰好)" || bad "端点二未 401" "code=$(code_of "$R")"
EA="$(mp_tok extA 13800000011)"; R="$(mcall "${EA}" GET /mp/me)"
[ "$(code_of "$R")" = "200" ] && ok "对照：别人的 token 仍 200（401 不是全局失效）" || bad "extA token 也挂了"

echo "-- 撤销 ≠ 删号：账号 / 微信绑定 / 外部档案都应还在"
assert_eq "SELECT count(*) FROM sys_user WHERE user_id=9000000101 AND del_flag='0'" "1" "账号仍在且 del_flag=0"
assert_eq "SELECT count(*) FROM t_lqg_wx_bind WHERE user_id=9000000101 AND del_flag='0'" "${BIND0}" "微信绑定仍是 ${BIND0} 条"
assert_eq "SELECT string_agg(r.role_key,',') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=9000000101" "lqg_external" "角色降回 lqg_external（是降权不是删号）"
echo "     （D1 库内没有样本表 t_lqg_sample：information_schema 只有 ext_profile/source_unit/unit_group/wx_bind 四张 t_lqg_* 表，'旧样本还在'在本任务不可测）"

echo "-- 不能撤销自己（admin 撤 9000000100 自己）"
R="$(call "${ATOK}" DELETE /lqg/auth/staff/9000000100)"; echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "撤销自己被拒 code=500" || bad "撤销自己没被拒" "code=$(code_of "$R")"
printf '%s' "$(msg_of "$R")" | grep -q '不能撤销自己' && ok "msg 明确是「不能撤销自己」分支" "$(msg_of "$R")" || bad "msg 不是撤销自己分支" "$(msg_of "$R")"
assert_eq "SELECT count(*) FROM sys_user_role WHERE user_id=9000000100 AND role_id=101" "1" "被拒后管理员角色 101 仍在库"
assert_eq "SELECT del_flag FROM sys_user WHERE user_id=9000000100" "0" "被拒后 admin 账号 del_flag 仍 0"

echo "-- 不能把系统里最后一个 lqg_admin 改成普通内部人员"
R="$(call "${ATOK}" PUT /lqg/auth/staff/9000000100/role '{"roleKey":"lqg_internal"}')"; echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "改最后管理员角色被拒 code=500" || bad "没被拒" "code=$(code_of "$R")"
assert_eq "SELECT string_agg(role_id::text,',') FROM sys_user_role WHERE user_id=9000000100" "101" "被拒后角色仍只有 101"

echo "-- 不能撤到系统里一个 lqg_admin 都不剩（用上游超管发起，绕过「不能撤自己」）"
SA="$(pc_tok admin admin123)"
[ -n "${SA}" ] && ok "拿到上游超管 token（非同一个人，可单独打最后管理员闸门）" || bad "拿不到上游超管 token"
R="$(call "${SA}" DELETE /lqg/auth/staff/9000000100)"; echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "撤掉唯一 lqg_admin 被拒 code=500" || bad "最后管理员被撤掉了！" "code=$(code_of "$R")"
printf '%s' "$(msg_of "$R")" | grep -q '最后一个' && ok "msg 明确是「最后一个管理员」闸门" "$(msg_of "$R")" || bad "msg 不是最后管理员分支" "$(msg_of "$R")"
assert_eq "SELECT count(*) FROM sys_user_role WHERE user_id=9000000100 AND role_id=101" "1" "被拒后库里仍有 1 个 lqg_admin"
assert_eq "SELECT count(*) FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE r.role_key='lqg_admin' AND r.del_flag='0'" "1" "全库 lqg_admin 计数仍是 1"

echo "-- 正向对照：先建第二个管理员，再由新管理员撤原管理员 → 允许（闸门不是一律拒绝）"
bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000079","name":"L3管理员乙","roleKey":"lqg_admin","password":"Lqg@test123"}' >/dev/null
T2="$(pc_tok lqg_13800000079 'Lqg@test123')"
[ -n "${T2}" ] && ok "第二个管理员登得上" || bad "第二个管理员登不上"
R="$(call "${T2}" DELETE /lqg/auth/staff/9000000100)"; echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "200" ] && ok "有两个管理员时撤销被允许 code=200" || bad "有两个管理员仍拒绝" "$(code_of "$R")"

# ═══════════════════════════════════════════════════════════════════════════
hdr "③ 改单位/组别立刻回待核验；自填不选新建/归并不过；组别与单位不匹配被拒"
reseed
echo "-- 起点：extA(9000000111) = A 医院(9000009001)/肝胆外科组(9000009101)/verified/verified_by=9000000100"
assert_eq "SELECT bind_status||'|'||unit_id||'|'||group_id||'|'||COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" "verified|9000009001|9000009101|9000000100" "seed 起点确认"

echo "-- 邪路 A：组别与单位不匹配（A 医院 + B 大学的 9000009103）"
R="$(bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009103}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "不匹配被拒 code=500" || bad "不匹配没被拒" "code=$(code_of "$R")"
printf '%s' "$(msg_of "$R")" | grep -q '不属于' && ok "msg 指明组别不属于该单位" "$(msg_of "$R")" || bad "msg 未指明不匹配" "$(msg_of "$R")"
assert_eq "SELECT bind_status||'|'||unit_id||'|'||group_id||'|'||COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" "verified|9000009001|9000009101|9000000100" "★被拒后库内完全不变（仍 verified + 原单位组 + verified_by 未清）"

echo "-- 邪路 B：停用单位 + 别单位的组（9000009003 已停用）"
R="$(bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009003,"groupId":9000009103}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "停用单位/不匹配被拒 code=500" || bad "停用单位没被拒" "code=$(code_of "$R")"
assert_eq "SELECT bind_status||'|'||unit_id||'|'||group_id FROM t_lqg_ext_profile WHERE user_id=9000000111" "verified|9000009001|9000009101" "★被拒后库内不变"

echo "-- 独立补强（r1 只改了组别）：改【单位】到 B 大学 + 其匹配组 → 接受且立刻回 pending"
R="$(bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009002,"groupId":9000009103}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "200" ] && ok "换单位（含匹配组）被接受 code=200" || bad "合法换单位被拒" "code=$(code_of "$R")"
assert_eq "SELECT bind_status||'|'||unit_id||'|'||group_id||'|'||COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" "pending|9000009002|9000009103|-" "★换单位后立刻 pending 且 verified_by 清空(NULL)"

echo "-- 改【组别】回 A 医院/消化内科组 → 同样立刻回 pending"
R="$(bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009102}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "200" ] && ok "换组别被接受" || bad "合法换组别被拒"
assert_eq "SELECT bind_status||'|'||group_id||'|'||COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" "pending|9000009102|-" "★换组别后立刻 pending 且 verified_by 清空"

echo "-- 邪路 C：请求体夹带 bindStatus/verifiedBy 想跳过核验"
R="$(bash doc/verify/api.sh --as extA PUT /mp/ext/profile '{"realName":"王医生","unitId":9000009001,"groupId":9000009101,"bindStatus":"verified","verifiedBy":9000000100}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
assert_eq "SELECT bind_status||'|'||COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000111" "pending|-" "★外部在 body 里写核验状态不被采信（仍 pending/NULL）"

echo "-- 邪路 D：自填档案 extF(9000000116) 直接 approve，不选新建/归并"
assert_eq "SELECT bind_status||'|'||COALESCE(unit_id::text,'-')||'|'||COALESCE(group_id::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116" "pending|-|-" "extF 起点：自填待核验、单位组别空"
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve"}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "自填不选新建/归并被拒 code=500" || bad "自填裸 approve 通过了！" "code=$(code_of "$R")"
printf '%s' "$(msg_of "$R")" | grep -qE '新建|归并|二选一' && ok "msg 指明必须二选一" "$(msg_of "$R")" || bad "msg 未指明二选一" "$(msg_of "$R")"
assert_eq "SELECT bind_status||'|'||COALESCE(unit_id::text,'-')||'|'||COALESCE(group_id::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116" "pending|-|-" "★被拒后库内不变（未偷偷落盘单位/组别）"

echo "-- 邪路 E：自填 approve 时给不匹配的 unitId+groupId"
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","unitId":9000009001,"groupId":9000009103}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "归并时组别与单位不匹配被拒 code=500" || bad "不匹配归并没被拒" "code=$(code_of "$R")"
printf '%s' "$(msg_of "$R")" | grep -q '不属于' && ok "msg 指明不匹配" "$(msg_of "$R")" || bad "msg 未指明不匹配" "$(msg_of "$R")"
assert_eq "SELECT bind_status||'|'||COALESCE(unit_id::text,'-')||'|'||COALESCE(group_id::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116" "pending|-|-" "★被拒后库内不变"

echo "-- 正向对照：归并到已有（A 医院/肝胆外科组）→ 应通过"
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","unitId":9000009001,"groupId":9000009101}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "200" ] && ok "归并路径被接受 code=200" || bad "合法归并被拒" "code=$(code_of "$R")"
assert_eq "SELECT bind_status||'|'||unit_id||'|'||group_id||'|'||COALESCE(verified_by::text,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116" "verified|9000009001|9000009101|9000000100" "★归并后落库正确且 verified_by=admin"

echo "-- 正向对照：createUnit+createGroup 新建路径（reseed 后）"
reseed
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"approve","createUnit":true,"createGroup":true}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "200" ] && ok "新建路径被接受 code=200" || bad "合法新建被拒" "code=$(code_of "$R")"
assert_eq "SELECT p.bind_status||'|'||u.unit_name||'|'||u.unit_status||'|'||g.group_name FROM t_lqg_ext_profile p JOIN t_lqg_source_unit u ON u.id=p.unit_id JOIN t_lqg_unit_group g ON g.id=p.group_id AND g.unit_id=u.id WHERE p.user_id=9000000116" "verified|C 研究所|active|肿瘤组" "★新建的单位/组别落库正确"

echo "-- 非法状态转移：verified 后再 reject 应被拒且库不变"
assert_eq "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" "verified" "起点 verified"
R="$(bash doc/verify/api.sh --as staff PUT /lqg/auth/ext-user/9000000116/verify '{"action":"reject","reason":"x"}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')"
[ "$(code_of "$R")" = "500" ] && ok "verified→rejected 被拒 code=500" || bad "非法转移没被拒" "code=$(code_of "$R")"
assert_eq "SELECT bind_status FROM t_lqg_ext_profile WHERE user_id=9000000116" "verified" "★被拒后仍是 verified"

# ═══════════════════════════════════════════════════════════════════════════
hdr "④ 不带 clientid 拿不到 token；prod 配置无 mock-login 键"
login_nc() { curl -sS --max-time 15 -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' "$@"; }
echo "-- 密码登录：完全不带 clientid"
R="$(login_nc -d '{"grantType":"password","tenantId":"000000","username":"lqgadmin","password":"admin123"}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')  token=$(tok_of "$R")"
[ "$(tok_of "$R")" = "ABSENT" ] && ok "无 clientid 的密码登录拿不到 token" || bad "无 clientid 也拿到 token！"
[ "$(code_of "$R")" = "500" ] && ok "无 clientid 是 500 拒绝（白名单正码）" || bad "拒绝码异常" "code=$(code_of "$R")"
printf '%s' "$(msg_of "$R")" | grep -qi 'clientid' && ok "msg 指明缺 clientid" "$(msg_of "$R")" || bad "msg 未指明 clientid" "$(msg_of "$R")"

echo "-- xcx 登录：完全不带 clientid"
R="$(login_nc -d '{"grantType":"xcx","tenantId":"000000","xcxCode":"mock:extA","phoneCode":"mock:13800000011"}')"
echo "     $(printf '%s' "$R"|jq -c '{code,msg}')  token=$(tok_of "$R")"
[ "$(tok_of "$R")" = "ABSENT" ] && ok "无 clientid 的 xcx 登录拿不到 token" || bad "无 clientid 的 xcx 拿到 token！"

echo "-- clientid 头存在但为空 / 是垃圾值"
R="$(login_nc -H 'clientid;' -d '{"grantType":"password","tenantId":"000000","username":"lqgadmin","password":"admin123"}')"
[ "$(tok_of "$R")" = "ABSENT" ] && ok "空 clientid 头拿不到 token" "$(printf '%s' "$R"|jq -r '.msg')" || bad "空 clientid 拿到 token"
R="$(login_nc -H 'clientid: deadbeefdeadbeefdeadbeefdeadbeef' -d '{"grantType":"password","tenantId":"000000","username":"lqgadmin","password":"admin123"}')"
[ "$(tok_of "$R")" = "ABSENT" ] && ok "不存在的 clientid 拿不到 token" "$(printf '%s' "$R"|jq -r '.msg')" || bad "垃圾 clientid 拿到 token"
echo "-- 正向对照：带上正确 clientid 就能拿到（证明④不是『一律拒』）"
R="$(login_pw lqgadmin admin123)"
[ "$(tok_of "$R")" != "ABSENT" ] && ok "带 clientid 时 admin 拿到 token" || bad "带 clientid 也拿不到（对照失败）"

echo "-- prod 配置里没有 mock-login 这个键"
PRODF="code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-prod.yml"
[ -f "${PRODF}" ] && ok "prod 配置文件存在：${PRODF}" || bad "找不到 ${PRODF}"
if grep -rn 'mock-login' "${PRODF}"; then bad "prod 配置里出现了 mock-login 键"; else ok "grep mock-login ${PRODF} → 无匹配（退出码非 0）"; fi
NFILES="$(find code/RuoYi-Vue-Plus -name 'application-prod*.yml' -not -path '*/target/*' | wc -l | tr -d ' ')"
HITS="$(grep -rl 'mock-login' $(find code/RuoYi-Vue-Plus -name 'application-prod*.yml' -not -path '*/target/*') 2>/dev/null | wc -l | tr -d ' ')"
[ "${HITS}" = "0" ] && ok "整棵源码树 ${NFILES} 个 application-prod*.yml 里 0 个含 mock-login" || bad "有 prod 文件含 mock-login"
echo "-- 对照：dev 里确有 mock-login: true（说明 grep 有效、不是文件都空）"
grep -q 'mock-login: *true' code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-dev.yml && ok "dev 里 mock-login: true 存在（ADR-0008）" || bad "dev 里没有 mock-login: true，grep 对照失效"

echo
echo "== L3 独立重跑 ${P}/$((P+F)) 通过 =="
[ "${F}" -eq 0 ] || exit 1
