#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════════
# SYS-ACCEPT-001 · 六个热点的**行为判据 + 变异验证**（本票 accept 1 / accept 2 的落点）
#
#   bash doc/waves/regression/D7/mutation-assert.sh                 # 默认：变异验证
#   bash doc/waves/regression/D7/mutation-assert.sh --verify-only   # 只跑「未改坏的树上判据全绿」
#   bash doc/waves/regression/D7/mutation-assert.sh --hotspot H1a,H1b   # 只跑部分热点（票面按票分片用）
#
# 默认模式 = ① 先在**未改坏**的树上跑完 scope 内全部判据（必须全绿）→
#            ② 对每个热点：施加已定义变异 → 断言对应判据**变红** → 还原 → 断言**复绿**；
#            ③ 任一「改坏了还绿」→ exit 1（不吞失败、不把断言塞进不回传退出码的 if 里）。
# --verify-only = 只做 ①；任一判据不绿 → exit 1。
#
# 判据（判据的输入全部来自**运行中的系统**，见 accept-strengthened/）：
#   H1a 导出请求真发一次 → 真请求头 Authorization+clientid，200，真 xlsx          （SYS-EXPORT-001 acc2）
#   H1b 文档下载真点一次 → OSS 直链真请求头**无 Authorization**，200                （SYS-EXPORT-001 acc2）
#   H2  工作台首页真 DOM 五个数字 == 同一次 /lqg/home/todo 返回值                  （SYS-HOME-001 acc2）
#   H3a 剥注释后 `showMenu: true` 仍在真代码里（本票唯一允许读源码的例外，变异即注释独立性证明）（DOC-MP-002 acc1）
#   H3b 点缩略图后打开层 src == 原图 url 且 ≠ previewUrl                          （DOC-MP-002 acc1）
#   H4  预览面板真 DOM 下载入口恰好 4 个，逐个点击的 format/合并位正确              （DOC-PUBLISH-001 acc2）
#
# 环境：默认 8094 后端 / 8093 工作台 / 9204 小程序 H5（端口纪律：8080/5432/6379 留给 Kevin）。
#   · 已在跑 → 复用（收尾不动别人的进程）；缺哪个只补起哪个（detach.sh + 按 PID 关停）。
#   · 本脚本自己起来的进程，在本脚本退出时**按 PID** 关掉。
# 纪律：关进程只按 PID；**严禁 pkill -f 'ruoyi-admin.jar'**（issue #46）；
#       `code/miniapp/src/pages.json` 是有意保留的状态（issue #258），本脚本不 checkout 它。
# ══════════════════════════════════════════════════════════════════════════════
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
# ★ 这个脚本在 doc/waves/regression/D7/ 下 → 仓库根要再往上**四级**；
#   深度写错会让 `git diff -- code/` 静默变空（起点守卫形同虚设，实测踩过）→ 这里硬校验一次。
[ "$(git rev-parse --show-toplevel 2>/dev/null)" = "${ROOT}" ] \
  || { echo "[error] ROOT 推导错了：${ROOT} 不是 git 仓库根" >&2; exit 2; }
[ -f "${ROOT}/code/miniapp/src/pages.json" ] \
  || { echo "[error] ${ROOT} 下找不到 code/miniapp/src/pages.json —— 不是本仓库根" >&2; exit 2; }
AS="${ROOT}/doc/waves/regression/D7/accept-strengthened"
TMP="${ROOT}/.tmp/sys-accept-001"
LOGDIR="${TMP}/logs"
BPORT="${LQG_ACCEPT_BACKEND_PORT:-8094}"; WPORT="${LQG_ACCEPT_WEB_PORT:-8093}"; MPORT="${LQG_ACCEPT_MP_PORT:-9204}"
VERIFY_ONLY=0
SCOPE="H1a,H1b,H2,H3a,H3b,H4"
ALL="H1a,H1b,H2,H3a,H3b,H4"

# 调用方（gate 的 accept-run）会导出**它自己那份** LQG_VERIFY_ENV_FILE —— 尊重它携带的后端端口，
# 别硬把人家拉到 8094（端口纪律只要求避开 8080/5432/6379）。
if [ -n "${LQG_VERIFY_ENV_FILE:-}" ] && [ -f "${LQG_VERIFY_ENV_FILE}" ]; then
  _p="$(sed -nE 's#^LQG_API_BASE=[^:]*://[^:]*:([0-9]+).*#\1#p' "${LQG_VERIFY_ENV_FILE}" | head -1)"
  if [ -n "${_p}" ]; then
    [ "${LQG_ACCEPT_BACKEND_PORT:-}" = "" ] && BPORT="${_p}"
  fi
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --verify-only) VERIFY_ONLY=1; shift ;;
    --hotspot) SCOPE="$2"; shift 2 ;;
    -h|--help) sed -n '2,26p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "[error] 未知参数 $1" >&2; exit 2 ;;
  esac
done
mkdir -p "${LOGDIR}" "${TMP}"

# ── scope 校验 ────────────────────────────────────────────────────────────────
IFS=',' read -r -a HOTS <<<"${SCOPE}"
for h in "${HOTS[@]}"; do
  case ",${ALL}," in *",${h},"*) ;; *) echo "[error] 未知热点 ${h}（可选 ${ALL}）" >&2; exit 2 ;; esac
done
FULL=0; [ "${SCOPE}" = "${ALL}" ] && FULL=1
# 需要真渲染产物的热点（下载 / 看原图 / 四个下载入口）
NEED_FIXTURE=0
for h in "${HOTS[@]}"; do case "$h" in H1b|H3b|H4) NEED_FIXTURE=1 ;; esac; done

# ── 判据执行器：0 = 绿（判据成立）｜1 = 红（判据被违反）─────────────────────────
run_probe() { # $1 = 热点；stdout 同时进终端与日志
  local hot="$1" rc
  case "${hot}" in
    H3a) python3 "${AS}/probe-h3a.py" 2>&1 | tee -a "${LOGDIR}/${hot}.log" ;;
    *)   node "${AS}/probe.mjs" "${hot}" 2>&1 | tee -a "${LOGDIR}/${hot}.log" ;;
  esac
  rc=${PIPESTATUS[0]}
  return "${rc}"
}

# ── 环境：只补缺的（进程只按 PID 起停）────────────────────────────────────────
port_pid() { lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null | head -1 || true; }
STARTED_WEB_PID=""; STARTED_MP_PID=""; STARTED_BACKEND=0
cleanup() {
  local rc=$?
  if [ -n "${STARTED_WEB_PID}" ] && kill -0 "${STARTED_WEB_PID}" 2>/dev/null; then
    kill "${STARTED_WEB_PID}" 2>/dev/null || true; echo "[env] 已按 PID 关停本脚本起的工作台 dev（pid ${STARTED_WEB_PID}）"
  fi
  if [ -n "${STARTED_MP_PID}" ] && kill -0 "${STARTED_MP_PID}" 2>/dev/null; then
    kill "${STARTED_MP_PID}" 2>/dev/null || true; echo "[env] 已按 PID 关停本脚本起的小程序 H5 dev（pid ${STARTED_MP_PID}）"
  fi
  if [ "${STARTED_BACKEND}" = 1 ]; then
    bash doc/waves/tools/qa-up.sh --down --backend-port "${BPORT}" --web-port "${WPORT}" --mp-port "${MPORT}" >/dev/null 2>&1 || true
    echo "[env] 已按 PID 关停本脚本起的后端+dev（qa-up --down ${BPORT}）"
  fi
  exit "${rc}"
}
trap cleanup EXIT

ensure_env() {
  local DETACH="${ROOT}/doc/waves/tools/detach.sh"
  if [ -z "$(port_pid "${BPORT}")" ]; then
    echo "[env] 后端 ${BPORT} 没在跑 → qa-up（--skip-build --no-reseed；夹具自己会 reseed）"
    bash doc/waves/tools/qa-up.sh --backend-port "${BPORT}" --web-port "${WPORT}" --mp-port "${MPORT}" \
      --skip-build --no-reseed >"${LOGDIR}/qa-up.log" 2>&1 \
      || { echo "[error] qa-up 起不来 → ${LOGDIR}/qa-up.log" >&2; tail -12 "${LOGDIR}/qa-up.log" >&2; exit 2; }
    STARTED_BACKEND=1
  fi
  if [ -z "$(port_pid "${WPORT}")" ]; then
    echo "[env] 工作台 ${WPORT} 没在跑 → 起 plus-ui dev（本脚本负责关）"
    STARTED_WEB_PID="$(cd "${ROOT}/code/plus-ui" && bash "${DETACH}" "${TMP}/plusui.log" \
      env VITE_APP_PORT="${WPORT}" VITE_APP_PROXY_TARGET="http://127.0.0.1:${BPORT}" pnpm dev)"
    for _ in $(seq 1 90); do [ -n "$(port_pid "${WPORT}")" ] && break; sleep 1; done
  fi
  if [ -z "$(port_pid "${MPORT}")" ]; then
    echo "[env] 小程序 H5 ${MPORT} 没在跑 → 起 unibest dev:h5（本脚本负责关）"
    STARTED_MP_PID="$(cd "${ROOT}/code/miniapp" && bash "${DETACH}" "${TMP}/miniapp.log" \
      env VITE_APP_PORT="${MPORT}" VITE_SERVER_BASEURL="http://127.0.0.1:${BPORT}" pnpm dev:h5)"
    for _ in $(seq 1 120); do [ -n "$(port_pid "${MPORT}")" ] && break; sleep 1; done
  fi
  [ -n "$(port_pid "${BPORT}")" ] || { echo "[error] 后端 ${BPORT} 仍不可用" >&2; exit 2; }
  [ -n "$(port_pid "${WPORT}")" ] || { echo "[error] 工作台 ${WPORT} 仍不可用 → ${TMP}/plusui.log" >&2; exit 2; }
  [ -n "$(port_pid "${MPORT}")" ] || { echo "[error] 小程序 H5 ${MPORT} 仍不可用 → ${TMP}/miniapp.log" >&2; exit 2; }
  # api.sh / db.py 的 env（★ 不导出它 DATA 段会落到默认 8081，「连不上后端」是工具红不是产品红）
  # 调用方已经给了一份指向同一后端的 → 尊重它（gate 的 accept-run 就会导出自己的 ENVF）
  local _given="${LQG_VERIFY_ENV_FILE:-}"
  if [ -n "${_given}" ] && [ -f "${_given}" ] \
     && [ "$(sed -nE 's#^LQG_API_BASE=[^:]*://[^:]*:([0-9]+).*#\1#p' "${_given}" | head -1)" = "${BPORT}" ]; then
    export LQG_VERIFY_ENV_FILE="${_given}"
  else
    if [ ! -f "${ROOT}/.tmp/qa-env/${BPORT}/verify.env" ]; then
      mkdir -p "${ROOT}/.tmp/qa-env/${BPORT}"
      sed -E "s#^LQG_API_BASE=.*#LQG_API_BASE=http://127.0.0.1:${BPORT}#" \
        "${ROOT}/doc/verify/verify.env" > "${ROOT}/.tmp/qa-env/${BPORT}/verify.env"
    fi
    export LQG_VERIFY_ENV_FILE="${ROOT}/.tmp/qa-env/${BPORT}/verify.env"
  fi
  echo "[env] 后端 $(port_pid "${BPORT}") · 工作台 $(port_pid "${WPORT}") · H5 $(port_pid "${MPORT}") ｜ LQG_VERIFY_ENV_FILE=${LQG_VERIFY_ENV_FILE}"
  for p in "${BPORT}" "${WPORT}" "${MPORT}"; do
    for _ in $(seq 1 60); do curl -s -o /dev/null -m 2 "http://127.0.0.1:${p}/" && break; sleep 1; done
  done
}

# ── 树状态守卫 ────────────────────────────────────────────────────────────────
TARGETS=(code/miniapp/src/utils/fileHandoff.ts code/miniapp/src/components/lqg/DownloadBar.vue
         code/miniapp/src/pages/ledger/index.vue code/miniapp/src/components/lqg/ThumbStrip.vue
         code/plus-ui/src/views/lqg/home/index.vue code/plus-ui/src/views/lqg/qc/components/PreviewPane.vue)

# 不变式：`git diff -- code/` 里**只允许** code/miniapp/src/pages.json，且它只动 2 行平台注释
# （issue #258：H5 dev server 会重写这两行注释顺序，这是有意保留的状态 → 不算脏、也不 checkout）。
# ★ 不用「diff sha 必须与起点逐字节相同」——dev server 合法重写这 2 行会造成**假红**。
tree_ok() {
  local other numstat
  other="$(git diff --name-only -- code/ | grep -v '^code/miniapp/src/pages.json$' || true)"
  [ -z "${other}" ] || { echo "[error] $1：git diff -- code/ 里出现了 pages.json 以外的文件：${other}" >&2; return 1; }
  numstat="$(git diff --numstat -- code/miniapp/src/pages.json | awk '{print $1"/"$2}')"
  case "${numstat}" in ""|"2/2") ;; *) echo "[error] $1：pages.json 的 diff 是 ${numstat} 行（期望 2/2 或干净）" >&2; return 1 ;; esac
  return 0
}
guard_start() {
  for f in "${TARGETS[@]}"; do
    git diff --quiet -- "${f}" || { echo "[error] 起点不干净：${f} 有未提交改动，变异还原会失真 —— 先处理再跑" >&2; exit 1; }
  done
  local stash; stash="$(git stash list)"
  [ -z "${stash}" ] || { echo "[error] git stash list 不空，拒绝在脏 stash 上跑变异：${stash}" >&2; exit 1; }
  tree_ok "起点" || exit 1
  BASE_DIFF_SHA="$(git diff -- code/ | shasum -a 256 | awk '{print $1}')"
  BASE_DIFF_FILES="$(git diff --name-only -- code/ | tr '\n' ' ')"
  echo "[tree] 起点 git diff -- code/ = [${BASE_DIFF_FILES}] sha=${BASE_DIFF_SHA:0:12}（期望只有 pages.json 的 2 行平台注释）"
}
guard_end() {
  local now_sha now_files stash
  now_sha="$(git diff -- code/ | shasum -a 256 | awk '{print $1}')"
  now_files="$(git diff --name-only -- code/ | tr '\n' ' ')"
  stash="$(git stash list)"
  TREE_AFTER="git diff -- code/ = [${now_files}] sha=${now_sha:0:12}（起点 ${BASE_DIFF_SHA:0:12}）; git stash list=${stash:-空}"
  tree_ok "收尾" || return 1
  [ -z "${stash}" ] || { echo "[error] 收尾 git stash list 不空：${stash}" >&2; return 1; }
  for f in "${TARGETS[@]}"; do
    git diff --quiet -- "${f}" || { echo "[error] 收尾 ${f} 仍有差异" >&2; return 1; }
  done
  echo "[tree] 收尾 ✅ ${TREE_AFTER}"
  return 0
}

# ══════════════════════════════════════════════════════════════════════════════
echo "════ mutation-assert：scope=[${SCOPE}] mode=$([ "${VERIFY_ONLY}" = 1 ] && echo verify-only || echo mutation) ════"
guard_start
ensure_env
echo "── 夹具：$([ "${NEED_FIXTURE}" = 1 ] && echo "reseed + 真图 + publish/render（H1b/H3b/H4 需要真产物）" || echo "reseed 到确定性快照")"
if [ "${NEED_FIXTURE}" = 1 ]; then
  bash "${AS}/setup-fixture.sh" 2>&1 | sed 's/^/   /' || { echo "[error] 夹具失败（工具红，不是产品红）" >&2; exit 2; }
else
  bash doc/verify/reseed.sh --yes >/dev/null 2>&1 || { echo "[error] reseed 失败" >&2; exit 2; }
  # reseed 作废服务端会话，但 api.sh 的 token 缓存在 $TMPDIR/lqg-verify-token-*（macOS 的 TMPDIR
  # 不是 /tmp），reseed.sh 自己不清 → 不清就会拿着死 token 一路 401（与 qa-up.sh:190 同款处理）。
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true
  echo "   reseed 完成"
fi

FAILED=0

# ── ① 未改坏的树上：scope 内判据全绿 ─────────────────────────────────────────
echo
echo "── ① 未改坏的树：scope 内判据全绿（预期全 GREEN）"
for h in "${HOTS[@]}"; do
  echo "   ── preflight ${h}"
  if run_probe "${h}"; then echo "   ✓ ${h} GREEN"; else FAILED=1; echo "   ✗ ${h} RED（未改坏就不绿 → 判据本身有问题，不再做变异）"; fi
done
if [ "${FAILED}" = 1 ]; then
  echo
  echo "════ 结果：preflight 有红 → exit 1（这种红不是「变异捕捉到」，是判据/环境的问题）════"
  [ "${FULL}" = 1 ] && python3 "${AS}/merge-evidence.py" verify-only "${SCOPE}" "" >/dev/null 2>&1
  exit 1
fi

if [ "${VERIFY_ONLY}" = 1 ]; then
  if [ "${FULL}" = 1 ]; then
    python3 "${AS}/merge-evidence.py" verify-only "${SCOPE}" "" || FAILED=1
  else
    echo "[evidence] scope 不是六个热点 → 不覆盖 evidence.json（票面按票分片跑，不动全量证据）"
  fi
  echo
  echo "════ 结果（--verify-only）：scope=[${SCOPE}] 判据全绿 → exit ${FAILED} ════"
  exit ${FAILED}
fi

# ── ② 变异验证：施加 → 对应判据必须红 → 还原 → 必须复绿 ───────────────────────
echo
echo "── ② 变异验证（每个热点：施加变异 → 判据必须变红 → 还原 → 必须复绿）"
: > "${TMP}/mutation-outcomes.ndjson"
for h in "${HOTS[@]}"; do
  echo
  echo "   ══ ${h} ══"
  APPLY_JSON="$(python3 "${AS}/mutate.py" apply "${h}")" || { echo "   ✗ ${h} 变异施加失败"; echo "{\"hotspot\":\"${h}\",\"applied\":false}" >>"${TMP}/mutation-outcomes.ndjson"; FAILED=1; continue; }
  echo "   [变异] ${APPLY_JSON}"
  sleep 3   # 让 Vite dev（plus-ui / uniapp H5）把改动编译进去
  curl -s -o /dev/null -m 5 "http://127.0.0.1:${WPORT}/" || true
  curl -s -o /dev/null -m 5 "http://127.0.0.1:${MPORT}/" || true
  sleep 2

  RED_RC=0; run_probe "${h}" || RED_RC=$?
  if [ "${RED_RC}" != 0 ]; then
    echo "   ✓ ${h}:改坏之后判据变红（符合预期）"
  else
    echo "   ✗ ${h}:★改坏了还绿 —— 变异没被这条判据捕捉到（本票就是为防这个）"
    FAILED=1
  fi
  cp -f "${AS}/observations/${h}.json" "${TMP}/red-${h}.json" 2>/dev/null || true

  RESTORE_JSON="$(python3 "${AS}/mutate.py" restore "${h}")" || { echo "   ✗ ${h} 还原失败"; echo "{\"hotspot\":\"${h}\",\"applied\":true,\"criterion_red\":$([ "${RED_RC}" != 0 ] && echo true || echo false),\"restored\":false}" >>"${TMP}/mutation-outcomes.ndjson"; FAILED=1; break; }
  echo "   [还原] ${RESTORE_JSON}"
  sleep 3
  GREEN_RC=0; run_probe "${h}" || GREEN_RC=$?
  if [ "${GREEN_RC}" = 0 ]; then
    echo "   ✓ ${h}:还原之后判据复绿"
  else
    echo "   ✗ ${h}:★还原之后判据没复绿 —— 树或环境没回到起点"
    FAILED=1
  fi

  python3 - "${h}" "${APPLY_JSON}" "${RESTORE_JSON}" "${RED_RC}" "${GREEN_RC}" "${TMP}/mutation-outcomes.ndjson" "${TMP}/red-${h}.json" <<'PY'
import json, sys
hot, apply_json, restore_json, red_rc, green_rc, ndjson, red_path = sys.argv[1:8]
red_obs = {}
try:
    red_obs = (json.load(open(red_path, encoding="utf-8")).get("observed") or {})
except Exception:
    pass
rec = {
    "hotspot": hot,
    "applied": bool(json.loads(apply_json).get("applied")),
    "mutation": json.loads(apply_json),
    "criterion_red": red_rc != "0",
    "red_probe_exit": int(red_rc),
    "restored": bool(json.loads(restore_json).get("restored")),
    "restore": json.loads(restore_json),
    "criterion_green_after_restore": green_rc == "0",
    "green_probe_exit": int(green_rc),
    "observed_when_mutated": red_obs,
}
with open(ndjson, "a", encoding="utf-8") as f:
    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
PY
done

# ── ③ 收尾：树守卫 + evidence.json ───────────────────────────────────────────
echo
guard_end || FAILED=1

python3 - "${TMP}/mutation-outcomes.ndjson" "${TREE_AFTER:-（guard_end 未产出）}" "${TMP}/outcomes.json" "${SCOPE}" "${BPORT}" <<'PY'
import json, sys
ndjson, tree_after, out, scope, bport = sys.argv[1:6]
mut = {}
try:
    for line in open(ndjson, encoding="utf-8"):
        line = line.strip()
        if line:
            r = json.loads(line)
            mut[r.pop("hotspot")] = r
except FileNotFoundError:
    pass
json.dump({"mutations": mut, "tree_after": tree_after,
           "env": {"backend_port": int(bport), "web_port": 8093, "mp_port": 9204,
                   "verify_env_file": f".tmp/qa-env/{bport}/verify.env"}},
          open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
PY

if [ "${FULL}" = 1 ]; then
  python3 "${AS}/merge-evidence.py" mutation "${SCOPE}" "${TMP}/outcomes.json" || FAILED=1
else
  echo "[evidence] scope 不是六个热点 → 不覆盖 evidence.json"
fi

echo
if [ "${FAILED}" = 0 ]; then
  echo "════ 结果：scope=[${SCOPE}] 六个热点全部「变异真变红 + 还原真复绿」 → exit 0 ════"
else
  echo "════ 结果：scope=[${SCOPE}] 有热点没通过变异验证（改坏了还绿 / 还原没复绿）→ exit 1 ════"
fi
exit ${FAILED}
