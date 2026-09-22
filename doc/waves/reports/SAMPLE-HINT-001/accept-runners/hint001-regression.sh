#!/usr/bin/env bash
# SAMPLE-HINT-001 · 回归自查（串行；四条上游 accept + 咽喉门 + D1 回归包）。
#
# ★ 必须**串行**跑：多条 accept 各自 reseed 首尾，并行会互相打断（EMBED-MODEL-001 踩过）。
# ★ 长链包进函数判整条 rc（`set -e` 不管非末尾失败）。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
WS="${ROOT}"
MVN_ARGS=(-s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome")
LOG_DIR="/tmp/hint001-regression"
mkdir -p "${LOG_DIR}"
cd "${ROOT}"

PASS=0; FAIL=0
declare -a FAILED

one() {
  local name="$1"; shift
  echo "==================== ${name} ===================="
  if "$@" > "${LOG_DIR}/${name}.log" 2>&1; then
    echo "GREEN  ${name}"; PASS=$((PASS + 1))
  else
    echo "RED    ${name}（rc=$?）—— 末 12 行："; tail -12 "${LOG_DIR}/${name}.log"; FAIL=$((FAIL + 1)); FAILED+=("${name}")
  fi
}

# ── 0) 全模块单测（含 ExtChokepointContractTest 这条 L0 咽喉门）─────────────────
one "java-full-test" bash -c "cd '${ROOT}/code/RuoYi-Vue-Plus' && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test ${MVN_ARGS[*]}"

# ── 1) EMBED-MODEL-001 accept 1 / 2 ─────────────────────────────────────────
one "embed001-acc1" bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc1.sh
one "embed001-acc2" bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc2.sh

# ── 2) EMBED-WEB-001 accept 1 / 2 ───────────────────────────────────────────
one "embedweb001-acc1" bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc1-export.sh
one "embedweb001-acc2" bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc2-menu.sh

# ── 3) SAMPLE-WEB-001 accept 1（段 4 的已知规格冲突见报告 §5）────────────────
one "web001-acc1" bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc1-api.sh

# ── 4) SAMPLE-MP-002 accept 2 ───────────────────────────────────────────────
one "mp002-acc2" bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc2.sh

# ── 5) SAMPLE-EXPORT-001 accept 1 ───────────────────────────────────────────
one "sampleexp001-acc1" bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-acc1-export.sh

# ── 6) D1 回归包（已知只剩 flyway 写死总数一条假红）─────────────────────────
one "d1-verify" bash doc/waves/regression/D1/verify.sh --skip-build

echo
echo "==================== 汇总 ===================="
echo "green=${PASS} red=${FAIL}"
for name in "${FAILED[@]:-}"; do [ -n "${name}" ] && echo "  - ${name}"; done
[ "${FAIL}" -eq 0 ]
