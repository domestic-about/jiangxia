#!/usr/bin/env bash
# 模式 B（2026-09-22 Kevin 决定，D5 起生效）的 **L0 + L1 闸门** —— 一条命令跑完，零 LLM 上下文。
#
#   bash doc/waves/tools/gate.sh --phase D5 [--backend-port 8091] [--skip-build]
#                                [--skip-accept] [--skip-d1] [--json FILE]
#
# 为什么有它（实测账 D1–D4）：分片 audit 里 10 条拦门 S0/S1 —— L2 占 9 条、L1 占 1 条、
# **L0 与 L3 各 0 条**；而每轮 L0+L1 都要派一个全新 agent 把同样的确定性对账重推一遍，
# 还各自重建一次环境。把 L0+L1 固化成脚本：确定性、可逐轮 diff、不烧上下文。
#
# 它干这些：
#   L0.0 环境前置：后端在端口上监听 + **新鲜度**（源码不得新于 jar、进程必须持有该 jar）
#   L0.1 后端编译 + 单测（mvn -pl ruoyi-modules/ruoyi-lqg -am install，逐条读 surefire 汇总）
#   L0.2 前端生产构建（按票面 touches 决定要不要跑 plus-ui / miniapp）
#   L1.0 reseed 到确定性快照 + 清孤儿账号
#   L1.1 该任务涉及的表 ddl_vs_ssot 逐列对账
#   L1.2 **逐字重放票面的 accept 断言**（doc/waves/tools/accept-run.py）
#   L1.3 D1 回归包全量重放（EXPECT_REV 注入；含 #82 已修）
#   L1.4 收尾 reseed，把库拉回基线
#
# 退出码：0 = 全绿 ｜ 1 = 有断言不成立（实现问题）｜ 2 = 环境 / 工具 / 用法错
#   （库连不上、后端没起、node_modules 缺、mvn 拉不到依赖……这些与「实现做错了」必须分开，
#    否则工具故障会被伪装成红。）
#
# 归一化：票面 accept 里的 `--fresh-module` 由 accept-run.py 去掉（NF1，issue #1/#13/#82），
#   新鲜度由 L0.0 承担 —— 本脚本因此**不调 --fresh-module**。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "${ROOT}"

PHASE=""; BPORT="8091"; SKIP_BUILD=0; SKIP_ACCEPT=0; SKIP_D1=0; JSON=""; AUDIT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --phase) PHASE="$2"; shift 2 ;;
    --backend-port) BPORT="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --skip-accept) SKIP_ACCEPT=1; shift ;;
    --skip-d1) SKIP_D1=1; shift ;;
    --json) JSON="$2"; shift 2 ;;
    --audit) AUDIT="$2"; shift 2 ;;   # 试跑/演练用：把审计写到别处，不污染 doc/waves/qa/
    -h|--help) sed -n '2,32p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "[error] 未知参数 $1" >&2; exit 2 ;;
  esac
done
[ -n "${PHASE}" ] || { echo "[error] 缺 --phase（如 --phase D5）" >&2; exit 2; }

LOGDIR="${ROOT}/.tmp/gate/${PHASE}"
mkdir -p "${LOGDIR}"
JSON="${JSON:-${LOGDIR}/gate.json}"
TSV="${LOGDIR}/steps.tsv"
: > "${TSV}"
HEAD="$(git rev-parse --short HEAD 2>/dev/null || echo none)"
STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
ENV_BROKEN=0; FAILED=0

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; printf 'pass\t%s\t%s\n' "$2" "$1" >> "${TSV}"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; printf 'fail\t%s\t%s\n' "$2" "$1" >> "${TSV}"; FAILED=1; }
envb() { printf '  \033[33m!\033[0m %s\n' "$1"; printf 'env\t%s\t%s\n' "$2" "$1" >> "${TSV}"; ENV_BROKEN=1; }
head1(){ printf '\n\033[1m%s\033[0m\n' "$1"; }

# ── api.sh / db.py 指向本项目自己的后端端口（不改共享 verify.env）────────────
ENVF="${LOGDIR}/verify.env"
sed -E "s#^LQG_API_BASE=.*#LQG_API_BASE=http://127.0.0.1:${BPORT}#" \
    "${ROOT}/doc/verify/verify.env" > "${ENVF}"
grep -q "^LQG_API_BASE=http://127.0.0.1:${BPORT}$" "${ENVF}" \
  || { echo "[error] 生成 ${ENVF} 失败（doc/verify/verify.env 缺 LQG_API_BASE 行？）" >&2; exit 2; }
export LQG_VERIFY_ENV_FILE="${ENVF}"
export LQG_API_BASE="http://127.0.0.1:${BPORT}"
BASE="${LQG_API_BASE}"
clear_tokens() { rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true; }

echo "════ gate ${PHASE} @ ${HEAD} · 后端 ${BASE} · 日志 ${LOGDIR} ════"

# ── 票面元信息（要不要跑前端 build / 哪些表做 ddl_vs_ssot）──────────────────
META="$(python3 doc/waves/tools/accept-run.py --phase "${PHASE}" --meta 2>"${LOGDIR}/meta.err")"
if [ $? -ne 0 ]; then
  echo "[error] 读票面元信息失败：$(cat "${LOGDIR}/meta.err")" >&2; exit 2
fi
printf '%s\n' "${META}" > "${LOGDIR}/meta.json"
NEEDS_PLUSUI="$(printf '%s' "${META}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["needs_plusui"])')"
NEEDS_MINIAPP="$(printf '%s' "${META}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["needs_miniapp"])')"
ACCEPT_N="$(printf '%s' "${META}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["accept_count"])')"
echo "  票：$(printf '%s' "${META}" | python3 -c 'import json,sys;print(",".join(json.load(sys.stdin)["tickets"]))')"
echo "  accept ${ACCEPT_N} 条 · plus-ui build=$([ "${NEEDS_PLUSUI}" = True ] && echo 要 || echo 免) · miniapp build=$([ "${NEEDS_MINIAPP}" = True ] && echo 要 || echo 免)"

# ── L0.0 环境前置：后端在听 + 新鲜度（等价 --fresh-module 守卫）──────────────
head1 "L0.0 环境前置（后端在听 + 产物新鲜度）"
PORT_PID="$(lsof -ti "tcp:${BPORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
JAR="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
if [ -z "${PORT_PID}" ]; then
  envb "后端没在 ${BASE} 监听（先 bash doc/waves/tools/qa-up.sh --backend-port ${BPORT}）" "L0.0 后端监听"
else
  ok "后端在 ${BASE} 监听（pid ${PORT_PID}）" "L0.0 后端监听"
fi
if [ ! -f "${JAR}" ]; then
  envb "缺 ${JAR}（先 mvn -pl ruoyi-admin -am package -DskipTests）" "L0.0 jar 存在"
else
  NEWER="$(find "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src" \
           "${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/src" -type f -newer "${JAR}" 2>/dev/null | head -1)"
  if [ -n "${NEWER}" ]; then
    bad "L0.0 stale：${NEWER#${ROOT}/} 比 jar 新 —— 改了源码没重新打包" "L0.0 产物新鲜度"
  else
    ok "L0.0 产物新鲜：lqg/admin 源码没有比 jar 新的文件" "L0.0 产物新鲜度"
  fi
fi
if [ -n "${PORT_PID}" ] && [ -f "${JAR}" ]; then
  HOLD="$(lsof -p "${PORT_PID}" 2>/dev/null > "${LOGDIR}/lsof-pid.txt"; grep -c 'ruoyi-admin.jar' "${LOGDIR}/lsof-pid.txt" || true)"
  # ★ 别写 `lsof -p … | grep -q …`：grep -q 命中即退 → lsof 收 SIGPIPE(141) → pipefail 下恒假红
  if [ "${HOLD}" -ge 1 ]; then ok "L0.0 进程 ${PORT_PID} 持有该 jar（inode 同一）" "L0.0 进程持 jar"
  else bad "L0.0 进程 ${PORT_PID} 没持有 ${JAR} —— 监听的可能是别的服务" "L0.0 进程持 jar"; fi
fi
if [ "${ENV_BROKEN}" = 1 ]; then
  echo -e "\n[gate] 环境没准备好，后续步骤无意义 —— 提前收工（exit 2）"
  python3 doc/waves/tools/gate-audit.py --gate "${JSON}" --tsv "${TSV}" --phase "${PHASE}" --head "${HEAD}" \
    --started "${STARTED}" --exit 2 --logdir "${LOGDIR}" ${AUDIT:+--audit "${AUDIT}"} >/dev/null 2>&1 || true
  exit 2
fi

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
    envb "mvn install exit ${rc}（工具/依赖问题，不是断言）→ tail ${MVNLOG}" "L0.1 mvn"
    tail -12 "${MVNLOG}" | sed 's/^/      /'
  elif ! grep -q 'BUILD SUCCESS' "${MVNLOG}"; then
    bad "L0.1 BUILD SUCCESS 缺失 → ${MVNLOG}" "L0.1 BUILD SUCCESS"
  else
    SUMMARY="$(grep -oE 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' "${MVNLOG}" | tail -1)"
    N="$(printf '%s' "${SUMMARY}" | sed -E 's/Tests run: ([0-9]+).*/\1/')"
    F="$(printf '%s' "${SUMMARY}" | sed -E 's/.*Failures: ([0-9]+).*/\1/')"
    E="$(printf '%s' "${SUMMARY}" | sed -E 's/.*Errors: ([0-9]+).*/\1/')"
    if [ -z "${SUMMARY}" ]; then bad "L0.1 找不到 surefire 汇总行（测试被静默跳过？）→ ${MVNLOG}" "L0.1 surefire 汇总"
    elif [ "${N}" -le 0 ]; then bad "L0.1 Tests run: 0 —— 0 用例 BUILD SUCCESS 也是红 → ${MVNLOG}" "L0.1 用例数"
    elif [ "${F}" != 0 ] || [ "${E}" != 0 ]; then bad "L0.1 ${SUMMARY}（有失败/错误）→ ${MVNLOG}" "L0.1 单测"
    else ok "L0.1 BUILD SUCCESS，${SUMMARY}" "L0.1 单测"; fi
  fi
fi

# ── L0.2 前端生产构建（按票面 touches）──────────────────────────────────────
build_front() { # build_front <标签> <目录> <产物相对路径> <脚本名>
  local label="$1" dir="$2" out="$3" script="$4"
  if [ ! -d "${dir}/node_modules" ]; then
    envb "${label}：${dir}/node_modules 缺（先 npm_config_store_dir=${ROOT}/.pnpm-store pnpm install --frozen-lockfile）" "${label}"
    return
  fi
  local log="${LOGDIR}/$(basename "${dir}")-${script//:/-}.log"
  ( cd "${dir}" && rm -rf "${out}" && pnpm "${script}" ) > "${log}" 2>&1
  local rc=$?
  if [ "${rc}" -ne 0 ]; then envb "${label} exit ${rc} → tail ${log}" "${label}"; tail -12 "${log}" | sed 's/^/      /'
  elif [ ! -e "${dir}/${out}" ]; then bad "${label}：退出码 0 但产物 ${out} 不存在（旧产物已 rm -rf，骗不过去）" "${label}"
  else
    local cntdir="${dir}/${out}"; [ -d "${cntdir}" ] || cntdir="$(dirname "${cntdir}")"
    ok "${label} 通过，产物 ${out} 已重建（$(find "${cntdir}" -type f 2>/dev/null | wc -l | tr -d ' ') 个文件）" "${label}"
  fi
}
if [ "${SKIP_BUILD}" = 1 ]; then
  head1 "L0.2 前端生产构建"; echo "  --skip-build：跳过"
else
  head1 "L0.2 前端生产构建"
  [ "${NEEDS_PLUSUI}" = True ]  && build_front "L0.2 plus-ui build:prod"       "code/plus-ui" "dist/index.html"              "build:prod"
  [ "${NEEDS_MINIAPP}" = True ] && build_front "L0.2 miniapp build:mp-weixin" "code/miniapp" "dist/build/mp-weixin/app.json" "build:mp-weixin"
  [ "${NEEDS_PLUSUI}" = True ] || [ "${NEEDS_MINIAPP}" = True ] || echo "  票面 touches 不含前端 → 两个 build 都免跑"
fi

# ── L1.0 reseed 到确定性快照 ───────────────────────────────────────────────
head1 "L1.0 reseed 到确定性快照 + 清孤儿账号"
clear_tokens
if bash doc/verify/reseed.sh --yes > "${LOGDIR}/reseed.log" 2>&1; then
  ok "reseed 完成（$(grep -c '已灌' "${LOGDIR}/reseed.log" || echo '?') 段 seed）" "L1.0 reseed"
else
  envb "reseed 失败 → ${LOGDIR}/reseed.log" "L1.0 reseed"; tail -10 "${LOGDIR}/reseed.log" | sed 's/^/      /'
fi
if [ -x doc/waves/tools/clean-orphan-accounts.sh ]; then
  bash doc/waves/tools/clean-orphan-accounts.sh --yes >> "${LOGDIR}/reseed.log" 2>&1 \
    && ok "孤儿账号已清（非 seed 段 wx_*/lqg_*）" "L1.0 清孤儿账号" \
    || envb "clean-orphan-accounts.sh 非 0 → ${LOGDIR}/reseed.log" "L1.0 清孤儿账号"
fi

# ── L1.1 该任务涉及的表 ddl_vs_ssot ────────────────────────────────────────
head1 "L1.1 ddl_vs_ssot（该任务 accept 点名的表）"
TABLES="$(printf '%s' "${META}" | python3 -c 'import json,sys;print("\n".join(json.load(sys.stdin)["ddl_tables"]))')"
REQFILE="${ROOT}/doc/waves/regression/${PHASE}/require-public.txt"
if [ -z "${TABLES}" ]; then
  echo "  票面 accept 没有 ddl_vs_ssot 断言 → 免跑"
else
  ARGS=(); while IFS= read -r t; do [ -n "${t}" ] && ARGS+=(--table "${t}"); done <<< "${TABLES}"
  if [ -f "${REQFILE}" ]; then
    REQ="$(tr -d '[:space:]' < "${REQFILE}")"
    [ -n "${REQ}" ] && ARGS+=(--require-public "${REQ}")
  else
    # 默认：每个 lqg 表都该有的 6 个公共字段。要免跑就在 regression/<D>/require-public.txt 里写空。
    ARGS+=(--require-public "create_dept,create_by,create_time,update_by,update_time,del_flag")
  fi
  DDL="$(python3 doc/verify/ddl_vs_ssot.py "${ARGS[@]}" 2>&1)"; rc=$?
  case "${rc}" in
    0) ok "L1.1 $(printf '%s' "${DDL}" | tail -1)" "L1.1 ddl_vs_ssot" ;;
    1) bad "L1.1 ddl_vs_ssot 不成立：$(printf '%s' "${DDL}" | tail -3 | tr '\n' ' ')" "L1.1 ddl_vs_ssot" ;;
    *) envb "L1.1 ddl_vs_ssot exit ${rc}：$(printf '%s' "${DDL}" | tail -2 | tr '\n' ' ')" "L1.1 ddl_vs_ssot" ;;
  esac
fi

# ── L1.2 逐字重放票面 accept ───────────────────────────────────────────────
head1 "L1.2 票面 accept 逐字重放（${ACCEPT_N} 条）"
if [ "${SKIP_ACCEPT}" = 1 ]; then
  echo "  --skip-accept：跳过"
else
  python3 doc/waves/tools/accept-run.py --phase "${PHASE}" --run \
    --json "${LOGDIR}/accept.json" --logdir "${LOGDIR}/accept-logs" --timeout 900
  rc=$?
  AP="$(python3 -c 'import json;d=json.load(open("'"${LOGDIR}"'/accept.json"));print(f"{d[\"passed\"]}/{d[\"total\"]}")' 2>/dev/null || echo '?/?')"
  if [ "${rc}" = 0 ]; then ok "L1.2 票面 accept 全部成立（${AP}）" "L1.2 accept 重放"
  else bad "L1.2 票面 accept 有红（${AP}）→ ${LOGDIR}/accept.json 与 accept-logs/" "L1.2 accept 重放"; fi
fi

# ── L1.3 D1 回归包全量重放 ─────────────────────────────────────────────────
head1 "L1.3 D1 回归包全量重放（EXPECT_REV=${HEAD}）"
if [ "${SKIP_D1}" = 1 ]; then
  echo "  --skip-d1：跳过"
else
  D1LOG="${LOGDIR}/d1-regression.log"
  EXPECT_REV="${HEAD}" bash doc/waves/regression/D1/verify.sh --skip-build > "${D1LOG}" 2>&1
  rc=$?
  SUM="$(grep -cE '✓' "${D1LOG}" || true)"; BADN="$(grep -cE '✗' "${D1LOG}" || true)"
  if [ "${rc}" = 0 ]; then ok "L1.3 D1 回归包全绿（${SUM}✓）" "L1.3 D1 回归包"
  elif [ "${rc}" = 2 ]; then envb "L1.3 D1 回归包 exit 2（环境坏）→ ${D1LOG}" "L1.3 D1 回归包"
  else bad "L1.3 D1 回归包有红（${SUM}✓/${BADN}✗）→ ${D1LOG}" "L1.3 D1 回归包"; fi
fi

# ── L1.4 收尾：把库拉回基线 ────────────────────────────────────────────────
head1 "L1.4 收尾 reseed（库回基线，交给下一片）"
clear_tokens
bash doc/verify/reseed.sh --yes > "${LOGDIR}/reseed-final.log" 2>&1 \
  && ok "收尾 reseed 完成" "L1.4 收尾 reseed" \
  || envb "收尾 reseed 失败 → ${LOGDIR}/reseed-final.log" "L1.4 收尾 reseed"

FINISHED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RC=0; [ "${FAILED}" = 1 ] && RC=1; [ "${ENV_BROKEN}" = 1 ] && RC=2
python3 doc/waves/tools/gate-audit.py --gate "${JSON}" --tsv "${TSV}" --phase "${PHASE}" --head "${HEAD}" \
  --started "${STARTED}" --finished "${FINISHED}" --exit "${RC}" --logdir "${LOGDIR}" \
  ${AUDIT:+--audit "${AUDIT}"} || { echo "[warn] gate-audit.py 落盘失败" >&2; }

PASS_N="$(grep -c '^pass' "${TSV}" || true)"; FAIL_N="$(grep -c '^fail' "${TSV}" || true)"; ENV_N="$(grep -c '^env' "${TSV}" || true)"
printf '\n════ gate %s 结果：%s 步 pass / %s 步 fail / %s 步 env-broken → exit %s ════\n' \
  "${PHASE}" "${PASS_N}" "${FAIL_N}" "${ENV_N}" "${RC}"
echo "  机器可读：${JSON}"
exit "${RC}"
