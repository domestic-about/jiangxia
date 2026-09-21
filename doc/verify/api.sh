#!/usr/bin/env bash
# accept / 回归断言的 API 执行器 —— 登录、带 token 调接口、把**响应体**原样吐到 stdout。
#
#   bash doc/verify/api.sh [--as <身份>] [--bizcode] [--out <文件>] [--header 'K: V']… [--form k=v|k=@文件]… [--fresh-module ruoyi-lqg] <METHOD> <PATH> [JSON_BODY]
#
#   --as 身份（默认 admin）：
#       admin   工作台账号密码登录（seed 里的 lqgadmin，角色 lqg_admin）
#       staff   内部人员，小程序 mock 登录（13800000001）
#       extA    外部 · A 医院 / 肝胆外科组 · 已核验（13800000011）
#       extB    外部 · 与 extA 同组 · 已核验（13800000012）
#       extC    外部 · 与 extA 同单位不同组 · 已核验（13800000013）
#       extD    外部 · B 大学 · 已核验（13800000014）
#       extE    外部 · 与 extA 同组但**未核验**（13800000015）
#       extF    外部 · 自填单位待核验（13800000016）
#       anon    不带 token
#       phone:<key>:<手机号>  任意手机号的测试登录（测「首次登录自动建外部账号」用；key 决定 openid）
#   --bizcode        只输出「业务码<TAB>msg」。⚠️ 若依把 404 / 403 / 500 全包进响应体，HTTP 状态码几乎恒为 200，
#                    **拿 HTTP 码判成败的断言恒绿**——断业务码要用这个，或者 jq -e 断 body。
#   --out 文件       把响应体原样写进文件（导出 Excel、下载文档用），stdout 不再输出
#   --header 'K: V'  追加请求头（可重复）
#   --form k=v       multipart 表单项（可重复；k=@路径 传文件）。带了 --form 就不再发 JSON body
#   --fresh-module M 反 stale 守卫：M 模块源码不得新于后端 jar，后端进程不得早于 jar。不满足 → exit 2。
#
# 退出码：0 = 请求发出并拿到响应（**不代表业务成功**，由调用方 jq -e 断言）；2 = 用法 / 登录 / 连接 / stale 错。
# mock 登录只在 dev / test 配置下存在（ADR-0008）：xcxCode="mock:<身份>"，phoneCode="mock:<手机号>"。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"
ENV_FILE="${LQG_VERIFY_ENV_FILE:-${HERE}/verify.env}"
[ -f "${ENV_FILE}" ] && set -a && . "${ENV_FILE}" && set +a
BASE="${LQG_API_BASE:-http://127.0.0.1:8080}"
CLIENT_PC="${LQG_CLIENT_PC:-}"
CLIENT_MP="${LQG_CLIENT_MP:-}"

AS="admin"; BIZ=0; FRESH=""; OUTFILE=""; HEADERS=(); FORMS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --as) AS="$2"; shift 2 ;;
    --bizcode) BIZ=1; shift ;;
    --out) OUTFILE="$2"; shift 2 ;;
    --header) HEADERS+=("$2"); shift 2 ;;
    --form) FORMS+=("$2"); shift 2 ;;
    --fresh-module) FRESH="$2"; shift 2 ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) break ;;
  esac
done
[ $# -ge 2 ] || { echo "[error] 用法：api.sh [--as X] [--bizcode] [--fresh-module M] METHOD PATH [JSON]" >&2; exit 2; }
METHOD="$1"; REQ_PATH="$2"; BODY="${3:-}"

phone_of() {
  case "$1" in
    staff) echo 13800000001 ;; extA) echo 13800000011 ;; extB) echo 13800000012 ;; extC) echo 13800000013 ;;
    extD) echo 13800000014 ;; extE) echo 13800000015 ;; extF) echo 13800000016 ;;
    *) echo "" ;;
  esac
}

# ── 反 stale 守卫 ─────────────────────────────────────────────────────────
if [ -n "${FRESH}" ]; then
  SRC="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-modules/${FRESH}/src"
  JAR="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
  [ -d "${SRC}" ] || { echo "[error] --fresh-module：找不到源码目录 ${SRC}" >&2; exit 2; }
  [ -f "${JAR}" ] || { echo "[error] --fresh-module：找不到 ${JAR}（先 mvn package）" >&2; exit 2; }
  NEWER="$(find "${SRC}" -type f -newer "${JAR}" | head -1)"
  [ -z "${NEWER}" ] || { echo "[error] stale：${NEWER#${ROOT}/} 比后端 jar 新——改了源码没重新打包" >&2; exit 2; }
  PORT="$(printf '%s' "${BASE}" | sed -E 's#.*:([0-9]+).*#\1#')"
  PID="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  [ -n "${PID}" ] || { echo "[error] stale 守卫：${PORT} 端口上没有后端进程" >&2; exit 2; }
  START_EPOCH="$(date -j -f '%a %b %d %T %Y' "$(ps -o lstart= -p "${PID}" | sed 's/  */ /g')" +%s 2>/dev/null || date -d "$(ps -o lstart= -p "${PID}")" +%s)"
  JAR_EPOCH="$(stat -f %m "${JAR}" 2>/dev/null || stat -c %Y "${JAR}")"
  [ "${START_EPOCH}" -ge "${JAR_EPOCH}" ] || { echo "[error] stale：后端进程启动早于 jar——打了包没重启" >&2; exit 2; }
fi

# ── 登录（token 缓存 20 分钟）──────────────────────────────────────────────
TOKEN=""; CLIENT=""
if [ "${AS}" != "anon" ]; then
  CACHE="${TMPDIR:-/tmp}/lqg-verify-token-$(printf '%s' "${AS}" | tr ':' '_')-$(printf '%s' "${BASE}" | cksum | cut -d' ' -f1)"
  if [ -f "${CACHE}" ] && [ -n "$(find "${CACHE}" -mmin -20 2>/dev/null)" ]; then
    TOKEN="$(sed -n 1p "${CACHE}")"; CLIENT="$(sed -n 2p "${CACHE}")"
  else
    if [ "${AS}" = "admin" ]; then
      CLIENT="${CLIENT_PC}"
      LOGIN="$(jq -nc --arg c "${CLIENT}" --arg u "${LQG_ADMIN_USER:-lqgadmin}" --arg p "${LQG_ADMIN_PASSWORD:-}" \
        '{clientId:$c,grantType:"password",tenantId:"000000",username:$u,password:$p}')"
    else
      MOCK_KEY="${AS}"; PHONE="$(phone_of "${AS}")"
      case "${AS}" in phone:*:*) MOCK_KEY="$(printf '%s' "${AS}" | cut -d: -f2)"; PHONE="$(printf '%s' "${AS}" | cut -d: -f3)" ;; esac
      [ -n "${PHONE}" ] || { echo "[error] 不认识的身份 --as ${AS}" >&2; exit 2; }
      CLIENT="${CLIENT_MP}"
      LOGIN="$(jq -nc --arg c "${CLIENT}" --arg x "mock:${MOCK_KEY}" --arg p "mock:${PHONE}" \
        '{clientId:$c,grantType:"xcx",tenantId:"000000",xcxCode:$x,phoneCode:$p}')"
    fi
    [ -n "${CLIENT}" ] || { echo "[error] verify.env 里没配 LQG_CLIENT_PC / LQG_CLIENT_MP" >&2; exit 2; }
    RESP="$(curl -sS --max-time 15 -X POST "${BASE}/auth/login" -H 'Content-Type: application/json' -H "clientid: ${CLIENT}" -d "${LOGIN}")" \
      || { echo "[error] 连不上后端 ${BASE}" >&2; exit 2; }
    TOKEN="$(printf '%s' "${RESP}" | jq -r 'select(.code==200) | .data.access_token // empty' 2>/dev/null || true)"
    [ -n "${TOKEN}" ] || { echo "[error] ${AS} 登录失败：${RESP}" >&2
      echo "        /auth/login 带 @ApiEncrypt：本地起后端要加 --api-decrypt.enabled=false，否则裸 curl 一律失败" >&2; exit 2; }
    printf '%s\n%s\n' "${TOKEN}" "${CLIENT}" > "${CACHE}"; chmod 600 "${CACHE}"
  fi
fi

# ── 发请求 ────────────────────────────────────────────────────────────────
ARGS=(-sS --max-time 60 -X "${METHOD}" "${BASE}${REQ_PATH}")
[ -n "${TOKEN}" ] && ARGS+=(-H "Authorization: Bearer ${TOKEN}" -H "clientid: ${CLIENT}")
for h in ${HEADERS[@]+"${HEADERS[@]}"}; do ARGS+=(-H "${h}"); done
if [ "${#FORMS[@]}" -gt 0 ]; then
  for f in "${FORMS[@]}"; do ARGS+=(-F "${f}"); done
else
  ARGS+=(-H 'Content-Type: application/json')
  [ -n "${BODY}" ] && ARGS+=(-d "${BODY}")
fi
if [ -n "${OUTFILE}" ]; then
  curl "${ARGS[@]}" -o "${OUTFILE}" || { echo "[error] 请求失败 ${METHOD} ${REQ_PATH}" >&2; exit 2; }
  exit 0
fi
OUT="$(curl "${ARGS[@]}")" || { echo "[error] 请求失败 ${METHOD} ${REQ_PATH}" >&2; exit 2; }
if [ "${BIZ}" = 1 ]; then
  printf '%s' "${OUT}" | jq -r '[(.code|tostring), (.msg // "")] | @tsv'
else
  printf '%s\n' "${OUT}"
fi
