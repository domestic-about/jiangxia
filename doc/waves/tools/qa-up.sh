#!/usr/bin/env bash
# 模式 B：**一条命令把验证环境拉起来** —— 后端 +（可选）两端前端 + reseed 到确定性快照。
#
#   bash doc/waves/tools/qa-up.sh --backend-port 8092 [--web-port 8093] [--mp-port 9202]
#                                 [--no-web] [--no-mp] [--skip-build] [--no-reseed]
#   bash doc/waves/tools/qa-up.sh --down [--backend-port 8092]      # 按 PID 关停
#   bash doc/waves/tools/qa-up.sh --status [--backend-port 8092]
#
# 为什么有它（D1–D4 的实测账）：每个 QA 分片都各自重建一次环境（mvn + 两个前端 build +
# reseed + 起三个进程），一轮三片 = 三倍固定成本。模式 B 让**整个门的三个阶段共用一台环境**，
# 只在阶段之间 reseed；构建只在环境起不来或 HEAD 变了时才重做。
#
# 纪律（都是踩过的坑，别改回去）：
#   · 关进程**只按 PID**（`lsof -ti tcp:<端口>`）；**严禁 `pkill -f 'ruoyi-admin.jar'`**
#     —— 8080 上跑着 Kevin 的本机服务，会被误杀（issue #46）。
#   · 端口 8080 / 5432 / 6379 留给 Kevin；后端默认 8092、工作台 8093、小程序 H5 9202。
#   · reseed 只许指向含 dev / test 的库（reseed.sh 自己会拒），并且会清 token 缓存。
#   · 状态落在 .tmp/qa-env/<backend-port>.json，--down 只信这个文件，不猜进程名。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT}"

BPORT=8092; WPORT=8093; MPORT=9202
NO_WEB=0; NO_MP=0; SKIP_BUILD=0; NO_RESEED=0
MODE=up
while [ $# -gt 0 ]; do
  case "$1" in
    --backend-port) BPORT="$2"; shift 2 ;;
    --web-port) WPORT="$2"; shift 2 ;;
    --mp-port) MPORT="$2"; shift 2 ;;
    --no-web) NO_WEB=1; shift ;;
    --no-mp) NO_MP=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --no-reseed) NO_RESEED=1; shift ;;
    --down) MODE=down; shift ;;
    --status) MODE=status; shift ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "[error] 未知参数 $1" >&2; exit 2 ;;
  esac
done
case "${BPORT}" in 8080|8081|5432|6379) echo "[error] 端口 ${BPORT} 留给 Kevin（8080/5432/6379）或既有环境（8081），换一个" >&2; exit 2 ;; esac

RUNDIR="${ROOT}/.tmp/qa-env/${BPORT}"
STATE="${RUNDIR}/state.json"
mkdir -p "${RUNDIR}"

kill_pid() { # kill_pid <pid> <标签>
  local pid="$1" label="$2"
  [ -n "${pid}" ] || return 0
  if kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    for _ in $(seq 1 20); do kill -0 "${pid}" 2>/dev/null || break; sleep 0.5; done
    kill -0 "${pid}" 2>/dev/null && { kill -9 "${pid}" 2>/dev/null || true; sleep 0.5; }
    printf '  \033[32m✓\033[0m 已按 PID 关停 %s（pid %s）\n' "${label}" "${pid}"
  else
    printf '  \033[33m!\033[0m %s（pid %s）已经不在了\n' "${label}" "${pid}"
  fi
}
port_pid() { lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null | head -1 || true; }

if [ "${MODE}" = down ]; then
  echo "── qa-up --down（后端 ${BPORT}）"
  if [ -f "${STATE}" ]; then
    B="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("backend_pid") or "")' "${STATE}")"
    W="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("web_pid") or "")' "${STATE}")"
    M="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("mp_pid") or "")' "${STATE}")"
    kill_pid "${W}" "工作台 dev(${WPORT})"
    kill_pid "${M}" "小程序 H5(${MPORT})"
    kill_pid "${B}" "后端(${BPORT})"
    rm -f "${STATE}"
  else
    echo "  没有 ${STATE}（不是本脚本起的，或已经 --down 过）"
  fi
  for p in "${BPORT}" "${WPORT}" "${MPORT}"; do
    [ -z "$(port_pid "${p}")" ] && printf '  \033[32m✓\033[0m %s 已释放\n' "${p}" \
      || printf '  \033[31m✗\033[0m %s 仍被 pid %s 占着\n' "${p}" "$(port_pid "${p}")"
  done
  # 小程序 H5 dev 会顺手改写 tracked 的 pages.json；收工时如实报出来（不擅自 checkout 别人的改动）
  if ! git diff --quiet -- code/miniapp/src/pages.json 2>/dev/null; then
    echo "  [warn] code/miniapp/src/pages.json 被 dev server 改过 —— 确认没用后：git checkout -- code/miniapp/src/pages.json"
  fi
  exit 0
fi

if [ "${MODE}" = status ]; then
  echo "── qa-up --status（后端 ${BPORT}）"
  for pair in "后端:${BPORT}" "工作台:${WPORT}" "小程序H5:${MPORT}"; do
    p="${pair##*:}"; n="${pair%%:*}"
    pid="$(port_pid "${p}")"
    [ -n "${pid}" ] && printf '  \033[32m✓\033[0m %s %s ← pid %s\n' "${n}" "${p}" "${pid}" \
      || printf '  \033[33m!\033[0m %s %s 空闲\n' "${n}" "${p}"
  done
  [ -f "${STATE}" ] && cat "${STATE}"
  exit 0
fi

echo "════ qa-up：后端 ${BPORT} · 工作台 ${WPORT} · 小程序H5 ${MPORT} · 根 ${RUNDIR} ════"

# ── 0. 幂等：端口被占就先按 PID 收掉（只收本项目端口上的，不猜进程名）────────
for pair in "后端:${BPORT}" "工作台:${WPORT}" "小程序H5:${MPORT}"; do
  p="${pair##*:}"; n="${pair%%:*}"
  pid="$(port_pid "${p}")"
  if [ -n "${pid}" ]; then echo "  ${n} ${p} 被 pid ${pid} 占着 —— 先关"; kill_pid "${pid}" "${n}(${p})"; fi
done

# 分离启动的薄封装：detach.sh 打印真实子进程 PID（macOS 没有 setsid，见该脚本头注释）
DETACH="${ROOT}/doc/waves/tools/detach.sh"
start_detached() { # start_detached <cwd> <日志> <命令...>
  local cwd="$1" log="$2"; shift 2
  ( cd "${cwd}" && bash "${DETACH}" "${log}" "$@" )
}

# ── 1. 后端：jar + 启动 + 等就绪 ───────────────────────────────────────────
JAR="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
STALE=""
[ -f "${JAR}" ] && STALE="$(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src" \
  "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/src" -type f -newer "${JAR}" 2>/dev/null | head -1)"
if [ "${SKIP_BUILD}" != 1 ] && { [ ! -f "${JAR}" ] || [ -n "${STALE}" ]; }; then
  echo "── 打后端 jar（$([ -f "${JAR}" ] && echo "源码比 jar 新：${STALE#${ROOT}/}" || echo "jar 不存在")）"
  ( cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-admin -am package -DskipTests \
      -s "${ROOT}/.mvn-settings.xml" -Dmaven.repo.local="${ROOT}/.m2repo" -Duser.home="${ROOT}/.buildhome" ) \
    > "${RUNDIR}/mvn-package.log" 2>&1
  rc=$?
  [ "${rc}" = 0 ] || { echo "[error] mvn package exit ${rc} → ${RUNDIR}/mvn-package.log" >&2; tail -12 "${RUNDIR}/mvn-package.log" >&2; exit 2; }
  echo "  ✓ jar 已重建"
fi
[ -f "${JAR}" ] || { echo "[error] 缺 ${JAR}（去掉 --skip-build 再跑）" >&2; exit 2; }

echo "── 起后端（dev profile + --api-decrypt.enabled=false + 端口 ${BPORT}）"
set -a; . "${ROOT}/code/deploy/dev/.env"; set +a
# ★ 必须显式给 JVM 一个含 127.0.0.1 的 nonProxyHosts（2026-09-22 DOC-RENDER-001 查出的环境病灶）：
#   macOS 的系统代理（127.0.0.1:1081）会被 JDK 灌成 http.proxyHost，而 JDK 自带的
#   http.nonProxyHosts **只含 localhost、不含 127.0.0.1** → AWS SDK(Netty) 把发往
#   127.0.0.1:9000 的 MinIO 请求丢给那个代理 → 框架自带 POST /resource/oss/upload 直接 500
#   （阻塞到 120s 才报 "doBlockingWrite ... within 120 seconds"）。
#   curl 不受影响（它认 NO_PROXY），**只有 JVM 踩**，所以极难从外部看出是代理问题。
#   原先的修法写在 **gitignored** 的 code/deploy/dev/.env 里 —— 那不可复现（换台机器/清库就丢），
#   所以在这里直接作为 JVM 参数注入：任何用 qa-up 起的环境都自带，不依赖任何 gitignored 文件。
BACKEND_PID="$(start_detached "${ROOT}" "${RUNDIR}/backend.log" java \
  '-Dhttp.nonProxyHosts=localhost|127.0.0.1|*.local|local' -jar "${JAR}" \
  --spring.profiles.active=dev --api-decrypt.enabled=false --server.port="${BPORT}")"
READY=0
for i in $(seq 1 90); do
  code="$(curl -s -o /dev/null -m 2 -w '%{http_code}' "http://127.0.0.1:${BPORT}/lqg/sys/ping" 2>/dev/null || true)"
  # 任何 HTTP 码都算「起来了」（401/403 也证明服务在听）；连不上才是 000
  if [ -n "${code}" ] && [ "${code}" != "000" ]; then READY=1; break; fi
  kill -0 "${BACKEND_PID}" 2>/dev/null || break
  sleep 2
done
if [ "${READY}" != 1 ]; then
  echo "[error] 后端 ${BPORT} 90 次探测没起来 → tail ${RUNDIR}/backend.log" >&2
  tail -25 "${RUNDIR}/backend.log" >&2
  kill_pid "${BACKEND_PID}" "后端(${BPORT})"
  exit 2
fi
BACKEND_PID="$(port_pid "${BPORT}")"   # 以「谁在监听」为准，不靠 $!（setsid / 包装层会让 $! 失真）
echo "  ✓ 后端就绪（pid ${BACKEND_PID}，/lqg/sys/ping → ${code}）"

# ── 2. 可选：两端 dev server ───────────────────────────────────────────────
WEB_PID=""; MP_PID=""
if [ "${NO_WEB}" != 1 ]; then
  echo "── 起工作台（plus-ui dev，端口 ${WPORT}，代理到 ${BPORT}）"
  start_detached "${ROOT}/code/plus-ui" "${RUNDIR}/plusui.log" \
    env VITE_APP_PORT="${WPORT}" VITE_APP_PROXY_TARGET="http://127.0.0.1:${BPORT}" pnpm dev >/dev/null
  WEB_PID=""
  for i in $(seq 1 60); do
    WEB_PID="$(port_pid "${WPORT}")"; [ -n "${WEB_PID}" ] && break; sleep 1
  done
  [ -n "$(port_pid "${WPORT}")" ] && echo "  ✓ 工作台 http://127.0.0.1:${WPORT}（pid ${WEB_PID}）" \
    || { echo "  [warn] 工作台 ${WPORT} 没起来 → ${RUNDIR}/plusui.log"; tail -8 "${RUNDIR}/plusui.log" | sed 's/^/      /'; }
fi
if [ "${NO_MP}" != 1 ]; then
  echo "── 起小程序 H5（unibest dev:h5，端口 ${MPORT}，直连 ${BPORT}）"
  start_detached "${ROOT}/code/miniapp" "${RUNDIR}/miniapp.log" \
    env VITE_APP_PORT="${MPORT}" VITE_SERVER_BASEURL="http://127.0.0.1:${BPORT}" pnpm dev:h5 >/dev/null
  MP_PID=""
  for i in $(seq 1 90); do
    MP_PID="$(port_pid "${MPORT}")"; [ -n "${MP_PID}" ] && break; sleep 1
  done
  [ -n "$(port_pid "${MPORT}")" ] && echo "  ✓ 小程序 H5 http://127.0.0.1:${MPORT}（pid ${MP_PID}，mock 登录见 env/.env.development）" \
    || { echo "  [warn] 小程序 ${MPORT} 没起来 → ${RUNDIR}/miniapp.log"; tail -8 "${RUNDIR}/miniapp.log" | sed 's/^/      /'; }
fi

# ── 3. api.sh 的 env（不改共享 verify.env）＋ reseed ───────────────────────
sed -E "s#^LQG_API_BASE=.*#LQG_API_BASE=http://127.0.0.1:${BPORT}#" \
  "${ROOT}/doc/verify/verify.env" > "${RUNDIR}/verify.env"
if [ "${NO_RESEED}" != 1 ]; then
  echo "── reseed 到确定性快照 + 清孤儿账号"
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true
  bash doc/verify/reseed.sh --yes > "${RUNDIR}/reseed.log" 2>&1 \
    && echo "  ✓ reseed 完成（$(grep -c '已灌' "${RUNDIR}/reseed.log" || echo '?') 段 seed）" \
    || { echo "  [warn] reseed 失败 → ${RUNDIR}/reseed.log"; tail -8 "${RUNDIR}/reseed.log" | sed 's/^/      /'; }
  [ -x doc/waves/tools/clean-orphan-accounts.sh ] && bash doc/waves/tools/clean-orphan-accounts.sh >> "${RUNDIR}/reseed.log" 2>&1 && echo "  ✓ 孤儿账号已清"
fi

cat > "${STATE}" <<JSON
{
 "backend_port": ${BPORT}, "web_port": ${WPORT}, "mp_port": ${MPORT},
 "backend_pid": ${BACKEND_PID}, "web_pid": "${WEB_PID}", "mp_pid": "${MP_PID}",
 "jar": "${JAR}", "started": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
 "head": "$(git rev-parse --short HEAD 2>/dev/null || echo none)"
}
JSON

cat <<TXT

════ 环境就绪 ════
  后端        http://127.0.0.1:${BPORT}   （日志 ${RUNDIR}/backend.log）
$([ "${NO_WEB}" != 1 ] && echo "  工作台      http://127.0.0.1:${WPORT}")
$([ "${NO_MP}"  != 1 ] && echo "  小程序 H5   http://127.0.0.1:${MPORT}")
  api.sh env  export LQG_VERIFY_ENV_FILE="${RUNDIR}/verify.env"
  关停        bash doc/waves/tools/qa-up.sh --down --backend-port ${BPORT} --web-port ${WPORT} --mp-port ${MPORT}
  状态        bash doc/waves/tools/qa-up.sh --status --backend-port ${BPORT}
TXT
