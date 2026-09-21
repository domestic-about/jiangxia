#!/usr/bin/env bash
# D1 回归包 —— L0（静态硬门）+ L1（集成硬门），供 D1 之后每个任务的 QA 门在 reseed 快照上全量重放。
#
#   bash doc/waves/regression/D1/verify.sh [--skip-build]
#
# 退出码：0 = 全绿｜1 = 有断言不成立（实现问题）｜2 = 环境 / 工具 / 用法错
#   （库连不上、后端没起、node_modules 缺、db.py 报 SQL 错、构建工具挂……这些和「实现做错了」必须分开）
#
# 幂等：每次都先 `reseed.sh --yes` 把库拉回确定性快照；结束时再 reseed + 清孤儿账号 + 清 token 缓存。
#       连跑两次结果一致。跑之前后端必须已经在 ${LQG_API_BASE}（本机 8081）上听着。
#
# 断言只打在 doc/verify/seed/ 的确定性数据上（期望值见 doc/verify/README.md 的身份表 / 样本表），
# 每条都自己直连库对账（db.py），不靠接口返回自证。
#
# ★ 本机两处已知事实（与 doc/verify/README.md gotcha #3 有关，别照抄 → 会误判）：
#   1. `api.sh --fresh-module` 在本运行时**必挂**：守卫用 `ps -o lstart=`，沙箱里 /bin/ps 是
#      "Operation not permitted"，落到 `date -d`（macOS 没有）→ 命令替换失败 → `set -e` 让 api.sh
#      **exit 1**（README 里 1 = 断言不成立，于是工具故障被伪装成红）。本脚本因此不调 --fresh-module，
#      自己在 L0.2 用等价手段核实新鲜度：源码不得新于 jar + 进程必须持有该 jar + 进程启动不得早于 jar。
#   2. token 缓存（$TMPDIR/lqg-verify-token-*）只按 mtime 判 20 分钟新鲜，改库/重置身份前必须先清。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"

ENV_FILE="${LQG_VERIFY_ENV_FILE:-${ROOT}/doc/verify/verify.env}"
[ -f "${ENV_FILE}" ] || { echo "[env] 缺 ${ENV_FILE}" >&2; exit 2; }
set -a; . "${ENV_FILE}"; set +a
BASE="${LQG_API_BASE:-http://127.0.0.1:8081}"
PORT="$(printf '%s' "${BASE}" | sed -E 's#.*:([0-9]+).*#\1#')"

SKIP_BUILD=0
[ "${1:-}" = "--skip-build" ] && SKIP_BUILD=1

LOGDIR="${ROOT}/.tmp/regression-D1"
mkdir -p "${LOGDIR}"

FAILED=()
ENV_BROKEN=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; FAILED+=("$1"); }
env()  { printf '  \033[33m!\033[0m %s\n' "$1"; ENV_BROKEN=1; FAILED+=("ENV: $1"); }
head1(){ printf '\n\033[1m%s\033[0m\n' "$1"; }

clear_tokens() { rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true; }

# ── 断言执行器薄封装 ────────────────────────────────────────────────────────
db_assert() { # db_assert <名称> <db.py 参数...>
  local name="$1"; shift
  local out rc
  out="$(python3 doc/verify/db.py "$@" 2>&1)"; rc=$?
  case "${rc}" in
    0) ok "${name}" ;;
    1) bad "${name} → $(printf '%s' "${out}" | tr '\n' ' ')" ;;
    *) env "${name} → db.py exit ${rc}：$(printf '%s' "${out}" | tr '\n' ' ')" ;;
  esac
}

api_jq() { # api_jq <名称> <身份> <jq 过滤> <METHOD> <PATH> [JSON]
  local name="$1" as="$2" filt="$3" method="$4" path="$5" body="${6:-}"
  local out rc
  if [ -n "${body}" ]; then out="$(bash doc/verify/api.sh --as "${as}" "${method}" "${path}" "${body}" 2>&1)"; rc=$?
  else out="$(bash doc/verify/api.sh --as "${as}" "${method}" "${path}" 2>&1)"; rc=$?; fi
  if [ "${rc}" -ne 0 ]; then env "${name} → api.sh exit ${rc}：$(printf '%s' "${out}" | tr '\n' ' ')"; return; fi
  if printf '%s' "${out}" | jq -e "${filt}" >/dev/null 2>&1; then ok "${name}"
  else bad "${name} → jq [${filt}] 不成立，响应=$(printf '%s' "${out}" | head -c 300)"; fi
}

# ── 环境前置：后端在听着 + 进程新鲜度（等价 --fresh-module 守卫）─────────────
proc_start_epoch() { # 不用 ps（沙箱里被禁）：psutil → libproc.proc_pidinfo
  python3 - "$1" <<'PY' 2>/dev/null
import ctypes, ctypes.util, sys
pid = int(sys.argv[1])
try:
    import psutil
    print(int(psutil.Process(pid).create_time())); raise SystemExit(0)
except SystemExit:
    raise
except Exception:
    pass
lib = ctypes.CDLL(ctypes.util.find_library('proc') or '/usr/lib/libproc.dylib')
class PBI(ctypes.Structure):
    _fields_ = [("pbi_flags", ctypes.c_uint32), ("pbi_status", ctypes.c_uint32), ("pbi_xstatus", ctypes.c_uint32),
                ("pbi_pid", ctypes.c_uint32), ("pbi_ppid", ctypes.c_uint32), ("pbi_uid", ctypes.c_uint32),
                ("pbi_gid", ctypes.c_uint32), ("pbi_ruid", ctypes.c_uint32), ("pbi_rgid", ctypes.c_uint32),
                ("pbi_svuid", ctypes.c_uint32), ("pbi_svgid", ctypes.c_uint32), ("rfu_1", ctypes.c_uint32),
                ("pbi_comm", ctypes.c_char * 16), ("pbi_name", ctypes.c_char * 32),
                ("pbi_nfiles", ctypes.c_uint32), ("pbi_pgid", ctypes.c_uint32), ("pbi_pjobc", ctypes.c_uint32),
                ("e_tdev", ctypes.c_uint32), ("e_tpgid", ctypes.c_uint32), ("pbi_nice", ctypes.c_int32),
                ("pbi_start_tvsec", ctypes.c_uint64), ("pbi_start_tvusec", ctypes.c_uint64)]
b = PBI()
if lib.proc_pidinfo(pid, 3, 0, ctypes.byref(b), ctypes.sizeof(b)) <= 0:
    raise SystemExit(1)
print(b.pbi_start_tvsec)
PY
}

preflight() {
  head1 "L0.0 环境前置"
  local pid
  pid="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  if [ -z "${pid}" ]; then
    env "后端没在 ${BASE} 上监听（bash .tmp/run-backend.sh，dev profile + --api-decrypt.enabled=false）"
    return 1
  fi
  ok "后端在 ${BASE} 监听（pid ${pid}，lsof 取）"
  local jar="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
  if [ ! -f "${jar}" ]; then env "缺 ${jar}（先 mvn -pl ruoyi-admin package -DskipTests）"; return 1; fi
  local newer
  newer="$(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src" -type f -newer "${jar}" 2>/dev/null | head -1)"
  if [ -n "${newer}" ]; then bad "L0.2 stale：${newer#${ROOT}/} 比 jar 新——改了源码没重新打包"; return 1; fi
  if ! lsof -p "${pid}" 2>/dev/null | grep -q 'ruoyi-admin.jar'; then
    bad "L0.2 stale：pid ${pid} 没持有 ${jar}（跑的不是这个 jar）"; return 1
  fi
  local jar_epoch start_epoch
  jar_epoch="$(stat -f %m "${jar}" 2>/dev/null || stat -c %Y "${jar}")"
  start_epoch="$(proc_start_epoch "${pid}")"
  if [ -z "${start_epoch}" ]; then
    env "L0.2 取不到 pid ${pid} 的启动时间（psutil / libproc 都失败）——新鲜度只核到「源码 vs jar」与「进程持有 jar」"
  elif [ "${start_epoch}" -lt "${jar_epoch}" ]; then
    bad "L0.2 stale：后端进程启动（$(date -r "${start_epoch}" '+%F %T')）早于 jar（$(date -r "${jar_epoch}" '+%F %T')）——打了包没重启"
  else
    ok "L0.2 新鲜度：源码不新于 jar；pid ${pid} 持有该 jar；进程启动 $(date -r "${start_epoch}" '+%F %T') ≥ jar $(date -r "${jar_epoch}" '+%F %T')"
  fi
  return 0
}

cleanup() {
  printf '\n\033[1m收尾\033[0m\n'
  clear_tokens
  bash doc/verify/reseed.sh --yes >/dev/null 2>&1 || true
  bash doc/waves/tools/clean-orphan-accounts.sh --yes >/dev/null 2>&1 || true
  # clean-orphan 只清 app_user；按手机号授权（AUTH-STAFF-001）建出来的是 user_type='sys_user'，
  # 它清不到——本脚本自己建的回归手机号在这里补删，保证库回到 seed 快照。
  PGPASSWORD="${LQG_DB_PASSWORD:-}" psql -q -h "${LQG_DB_HOST}" -p "${LQG_DB_PORT:-5432}" \
    -U "${LQG_DB_USER}" -d "${LQG_DB_NAME}" >/dev/null 2>&1 <<'SQL' || true
DELETE FROM sys_user_role     WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber IN ('13800000097','13800000098'));
DELETE FROM t_lqg_ext_profile WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber IN ('13800000097','13800000098'));
DELETE FROM t_lqg_wx_bind     WHERE user_id IN (SELECT user_id FROM sys_user WHERE phonenumber IN ('13800000097','13800000098'));
DELETE FROM sys_user          WHERE phonenumber IN ('13800000097','13800000098');
SQL
  echo "  已 reseed + 清孤儿账号（含回归手机号 13800000097 / 13800000098）+ 清 token 缓存"
}
trap cleanup EXIT

preflight || true

# ── L0.1 后端编译 + 单测 ────────────────────────────────────────────────────
head1 "L0.1 后端 mvn -pl ruoyi-modules/ruoyi-lqg -am install"
if [ "${SKIP_BUILD}" = 1 ]; then
  echo "  --skip-build：跳过"
else
  MVNLOG="${LOGDIR}/mvn-install.log"
  ( cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg -am install \
      -s "${ROOT}/.mvn-settings.xml" \
      -Dmaven.repo.local="${ROOT}/.m2repo" \
      -Duser.home="${ROOT}/.buildhome" ) > "${MVNLOG}" 2>&1
  rc=$?
  if [ "${rc}" -ne 0 ]; then
    env "mvn install exit ${rc}（不是断言问题）→ tail ${MVNLOG}"; tail -15 "${MVNLOG}" | sed 's/^/      /'
  elif ! grep -q 'BUILD SUCCESS' "${MVNLOG}"; then
    bad "L0.1 BUILD SUCCESS 缺失 → ${MVNLOG}"
  else
    SUMMARY="$(grep -oE 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' "${MVNLOG}" | tail -1)"
    N="$(printf '%s' "${SUMMARY}" | sed -E 's/Tests run: ([0-9]+).*/\1/')"
    F="$(printf '%s' "${SUMMARY}" | sed -E 's/.*Failures: ([0-9]+).*/\1/')"
    E="$(printf '%s' "${SUMMARY}" | sed -E 's/.*Errors: ([0-9]+).*/\1/')"
    if [ -z "${SUMMARY}" ]; then bad "L0.1 找不到 surefire 汇总行（测试被 groups/skipTests 静默跳过？）→ ${MVNLOG}"
    elif [ "${N}" -le 0 ]; then bad "L0.1 Tests run: 0 —— 0 用例 BUILD SUCCESS 也是红 → ${MVNLOG}"
    elif [ "${F}" != 0 ] || [ "${E}" != 0 ]; then bad "L0.1 ${SUMMARY}（有失败/错误）→ ${MVNLOG}"
    else ok "L0.1 BUILD SUCCESS，${SUMMARY}"; fi
    if grep -q 'Running org.dromara.lqg.auth.guard.MockLoginGuardContractTest' "${MVNLOG}"; then
      ok "L0.1 MockLoginGuardContractTest 真的跑了"
    else bad "L0.1 MockLoginGuardContractTest 没跑 → ${MVNLOG}"; fi
  fi
fi

# ── L0.3 / L0.4 两个前端生产构建 ────────────────────────────────────────────
build_front() { # build_front <名称> <目录> <产物相对路径> <脚本名>
  local name="$1" dir="$2" out="$3" script="$4"
  [ -d "${dir}/node_modules" ] || { env "${name}：${dir}/node_modules 缺（先 pnpm install --store-dir=${ROOT}/.pnpm-store）"; return; }
  local log="${LOGDIR}/$(basename "${dir}")-${script//:/-}.log"
  ( cd "${dir}" && rm -rf "${out}" && pnpm --store-dir="${ROOT}/.pnpm-store" "${script}" ) > "${log}" 2>&1
  local rc=$?
  if [ "${rc}" -ne 0 ]; then
    env "${name} exit ${rc} → tail ${log}"; tail -15 "${log}" | sed 's/^/      /'
  elif [ ! -e "${dir}/${out}" ]; then
    bad "${name}：退出码 0 但产物 ${out} 不存在（旧产物已被 rm -rf 清掉，骗不过去）"
  else
    ok "${name} 通过，产物 ${out} 已重建（$(find "${dir}/${out}" -type f 2>/dev/null | wc -l | tr -d ' ') 个文件）"
  fi
}

if [ "${SKIP_BUILD}" = 1 ]; then
  head1 "L0.3/L0.4 前端生产构建"; echo "  --skip-build：跳过"
else
  head1 "L0.3 plus-ui pnpm build:prod"
  build_front "L0.3 plus-ui build:prod" "code/plus-ui" "dist/index.html" "build:prod"
  head1 "L0.4 miniapp pnpm build:mp-weixin"
  build_front "L0.4 miniapp build:mp-weixin" "code/miniapp" "dist/build/mp-weixin/app.json" "build:mp-weixin"
fi

# ── reseed：L1 的所有断言都打在确定性快照上 ─────────────────────────────────
head1 "L1 前置：reseed 到确定性快照"
clear_tokens
if bash doc/verify/reseed.sh --yes > "${LOGDIR}/reseed.log" 2>&1; then
  ok "reseed 完成（$(grep -c '已灌' "${LOGDIR}/reseed.log") 段 seed 灌入）"
else
  env "reseed 失败 → ${LOGDIR}/reseed.log"; tail -10 "${LOGDIR}/reseed.log" | sed 's/^/      /'
fi

# ── L1.1 Flyway / 字典 / 角色 ───────────────────────────────────────────────
head1 "L1.1 Flyway 历史 · 26 个字典 · 评分分值在 remark · 角色 role_key"
db_assert "L1.1 flyway 无失败行（success IS NOT TRUE 的行 = 0）" \
  --sql "SELECT count(*) FROM flyway_schema_history WHERE success IS NOT TRUE" --eq 0
db_assert "L1.1 D1 的 7 支迁移全部记录在案（D1 无迁移的只有 SYS-MP-001）" \
  --sql "SELECT count(*) FROM flyway_schema_history" --eq 7
for v in 202609210800 202609210810 202609210820 202609210830 202609210910 202609210920 202609210930; do
  db_assert "L1.1 flyway 有 V${v}" --sql "SELECT count(*) FROM flyway_schema_history WHERE version='${v}' AND success" --eq 1
done
db_assert "L1.1 26 个 lqg_ 字典逐个点名（集合精确相等）" \
  --sql "SELECT dict_type FROM sys_dict_type WHERE dict_type LIKE 'lqg\_%'" \
  --col-set lqg_sample_kind,lqg_submit_source,lqg_verify_status,lqg_gender,lqg_has_none,lqg_yes_no,lqg_stain_type,lqg_marker_expr,lqg_cryo_flow_type,lqg_cryo_location,lqg_doc_status,lqg_doc_type,lqg_doc_kind,lqg_doc_audience,lqg_file_format,lqg_render_status,lqg_image_slot,lqg_unit_status,lqg_bind_status,lqg_hint_tissue_type,lqg_hint_organoid_type,lqg_hint_sample_type,lqg_score_pre_culture,lqg_score_culture_days,lqg_score_count,lqg_score_diameter
db_assert "L1.1 评分四字典的分值都在 remark（培养前）" \
  --sql "SELECT dict_value||':'||remark FROM sys_dict_data WHERE dict_type='lqg_score_pre_culture'" \
  --col-set "40to80:16,gt80:20,lt40:8"
db_assert "L1.1 评分四字典的分值都在 remark（培养天数）" \
  --sql "SELECT dict_value||':'||remark FROM sys_dict_data WHERE dict_type='lqg_score_culture_days'" \
  --col-set "gt14:0,le14:10"
db_assert "L1.1 评分四字典的分值都在 remark（类器官数量）" \
  --sql "SELECT dict_value||':'||remark FROM sys_dict_data WHERE dict_type='lqg_score_count'" \
  --col-set "lt100:0,100to1500:10,1500to4000:25,gt4000:40"
db_assert "L1.1 评分四字典的分值都在 remark（类器官直径）" \
  --sql "SELECT dict_value||':'||remark FROM sys_dict_data WHERE dict_type='lqg_score_diameter'" \
  --col-set "lt30:10,30to100:20,gt100:30"
db_assert "L1.1 角色 101/102/103 的 role_key 精确" \
  --sql "SELECT role_key FROM sys_role WHERE role_id BETWEEN 101 AND 103 AND del_flag='0'" \
  --col-set lqg_admin,lqg_internal,lqg_external
for f in V202609210800__SYS-BASE-001-ruoyi-postgres-baseline.sql V202609210810__SYS-BASE-001-lqg-dicts.sql V202609210820__SYS-BASE-001-lqg-roles.sql V202609210830__SYS-WEB-001-admin-dict-menu.sql V202609210910__AUTH-LOGIN-001-wx-bind-ext-profile.sql V202609210920__AUTH-STAFF-001-menu.sql V202609210930__AUTH-GROUP-001-unit-group.sql; do
  [ -f "code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/${f}" ] \
    && ok "L1.1 迁移在源码树：${f}" || bad "L1.1 迁移不在源码树（只在 target/？）：${f}"
done

# ── L1.2 8 个 seed 账号 + 单位 / 组别 + 四张表 DDL ──────────────────────────
head1 "L1.2 reseed 后 8 个测试账号与角色（README 身份表）· 单位组别 · 四张表 ddl_vs_ssot"
db_assert "L1.2 8 个 seed 账号的手机号|user_type|角色 逐一对上" \
  --sql "SELECT string_agg(phonenumber||'|'||user_type||'|'||role_key, ',' ORDER BY phonenumber) FROM (SELECT u.phonenumber, u.user_type, coalesce(string_agg(r.role_key, ',' ORDER BY r.role_key),'-') AS role_key FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.user_id LEFT JOIN sys_role r ON r.role_id=ur.role_id WHERE u.del_flag='0' AND u.phonenumber IN ('13800000000','13800000001','13800000011','13800000012','13800000013','13800000014','13800000015','13800000016') GROUP BY u.user_id, u.phonenumber, u.user_type) s" \
  --eq "13800000000|sys_user|lqg_admin,13800000001|sys_user|lqg_internal,13800000011|app_user|lqg_external,13800000012|app_user|lqg_external,13800000013|app_user|lqg_external,13800000014|app_user|lqg_external,13800000015|app_user|lqg_external,13800000016|app_user|lqg_external"
db_assert "L1.2 6 个外部档案的单位 / 组别 / 核验状态逐一对上（A 同组、C 同单位异组、D 异单位、E 待核验、F 自填）" \
  --sql "SELECT string_agg(phonenumber||'|'||bind_status||'|'||coalesce(su.unit_name,'-')||'|'||coalesce(g.group_name,'-'), ',' ORDER BY phonenumber) FROM t_lqg_ext_profile p JOIN sys_user u ON u.user_id=p.user_id LEFT JOIN t_lqg_source_unit su ON su.id=p.unit_id LEFT JOIN t_lqg_unit_group g ON g.id=p.group_id WHERE u.phonenumber IN ('13800000011','13800000012','13800000013','13800000014','13800000015','13800000016')" \
  --eq "13800000011|verified|A 医院|肝胆外科组,13800000012|verified|A 医院|肝胆外科组,13800000013|verified|A 医院|消化内科组,13800000014|verified|B 大学|类器官课题组,13800000015|pending|A 医院|肝胆外科组,13800000016|pending|-|-"
db_assert "L1.2 extF 的自填单位 / 组别原样保留（未核验不许落 unit_id）" \
  --sql "SELECT coalesce(unit_name_input,'-')||'|'||coalesce(group_name_input,'-') FROM t_lqg_ext_profile WHERE user_id=9000000116" \
  --eq "C 研究所|肿瘤组"
DDLOUT="$(python3 doc/verify/ddl_vs_ssot.py --table t_lqg_wx_bind --table t_lqg_ext_profile --table t_lqg_source_unit --table t_lqg_unit_group \
  --require-public create_dept,create_by,create_time,update_by,update_time,del_flag 2>&1)"; DDRC=$?
if [ "${DDRC}" -eq 0 ]; then ok "L1.2 t_lqg_wx_bind / t_lqg_ext_profile / t_lqg_source_unit / t_lqg_unit_group ddl_vs_ssot 全过"
elif [ "${DDRC}" -eq 1 ]; then bad "L1.2 ddl_vs_ssot 有差异 → $(printf '%s' "${DDLOUT}" | tr '\n' ' ')"
else env "L1.2 ddl_vs_ssot exit ${DDRC}：$(printf '%s' "${DDLOUT}" | tr '\n' ' ')"; fi
db_assert "L1.2 没有任何手机号在 sys_user 里有两行（活跃行）" \
  --sql "SELECT phonenumber||' x'||count(*) FROM sys_user WHERE del_flag='0' AND phonenumber IS NOT NULL AND phonenumber<>'' GROUP BY phonenumber HAVING count(*)>1" --empty

# ── L1.3 同一手机号恒为 1 行：三条路径 ─────────────────────────────────────
head1 "L1.3 同一手机号在 sys_user 里恒为 1 行（首登自动建 / 换微信号再登 / 按手机号授权升级）"
PH="13800000098"
api_jq "L1.3a 首登自动建：identity=external、bind_status=unbound、手机号打码" "phone:regA:${PH}" \
  '.code==200 and .data.identity=="external" and .data.ext.bindStatus=="unbound" and .data.phoneMasked=="138****0098"' GET /mp/me
db_assert "L1.3a 首登后同手机号 1 行" --sql "SELECT count(*) FROM sys_user WHERE phonenumber='${PH}' AND del_flag='0'" --eq 1
api_jq "L1.3b 换微信号再登：还是同一个人（external）" "phone:regB:${PH}" \
  '.code==200 and .data.identity=="external"' GET /mp/me
db_assert "L1.3b 换微信号再登后同手机号仍 1 行" --sql "SELECT count(*) FROM sys_user WHERE phonenumber='${PH}' AND del_flag='0'" --eq 1
db_assert "L1.3b 是同一 user_id 上挂了 2 个 openid（不是建了第二个账号）" \
  --sql "SELECT count(*) FROM t_lqg_wx_bind b JOIN sys_user u ON u.user_id=b.user_id WHERE u.phonenumber='${PH}' AND b.del_flag='0'" --eq 2
api_jq "L1.3c 按手机号授权升级：upgraded=true" admin \
  '.code==200 and .data.upgraded==true' POST /lqg/auth/staff "{\"phone\":\"${PH}\",\"name\":\"QA回归\",\"roleKey\":\"lqg_internal\",\"password\":\"Lqg@test123\"}"
db_assert "L1.3c 授权升级后同手机号仍 1 行" --sql "SELECT count(*) FROM sys_user WHERE phonenumber='${PH}' AND del_flag='0'" --eq 1
db_assert "L1.3c 角色换成内部、微信绑定原样（2 行）" \
  --sql "SELECT (SELECT string_agg(r.role_key, ',' ORDER BY r.role_key) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id WHERE u.phonenumber='${PH}') || '|' || (SELECT count(*) FROM t_lqg_wx_bind b JOIN sys_user u ON u.user_id=b.user_id WHERE u.phonenumber='${PH}' AND b.del_flag='0')" \
  --eq "lqg_internal|2"
clear_tokens
api_jq "L1.3c 升级后同一微信号再登：identity=internal" "phone:regB:${PH}" \
  '.code==200 and .data.identity=="internal"' GET /mp/me
# 交叉路径：先按手机号授权（账号由授权建出）→ 再首登，也不许建出第二行
PH2="13800000097"
api_jq "L1.3d 先授权后首登：授权建号 upgraded=false" admin \
  '.code==200 and .data.upgraded==false' POST /lqg/auth/staff "{\"phone\":\"${PH2}\",\"name\":\"QA先授权\",\"roleKey\":\"lqg_internal\",\"password\":\"Lqg@test123\"}"
api_jq "L1.3d 该手机号首次微信登录：identity=internal（认到授权建出的账号）" "phone:regC:${PH2}" \
  '.code==200 and .data.identity=="internal"' GET /mp/me
db_assert "L1.3d 授权 + 首登后同手机号仍 1 行" --sql "SELECT count(*) FROM sys_user WHERE phonenumber='${PH2}' AND del_flag='0'" --eq 1

# ── 汇总 ────────────────────────────────────────────────────────────────────
printf '\n\033[1m═══ 汇总 ═══\033[0m\n'
if [ "${ENV_BROKEN}" = 1 ]; then
  printf '\033[33m环境 / 工具错 %d 条\033[0m（先修环境，别当成实现问题）\n' "$(printf '%s\n' "${FAILED[@]}" | grep -c '^ENV: ' || true)"
fi
if [ "${#FAILED[@]}" -eq 0 ]; then
  echo "D1 L0+L1 全绿"; exit 0
fi
printf '\033[31m失败 %d 条：\033[0m\n' "${#FAILED[@]}"
for f in "${FAILED[@]}"; do printf '  - %s\n' "${f}"; done
[ "${ENV_BROKEN}" = 1 ] && exit 2
exit 1
