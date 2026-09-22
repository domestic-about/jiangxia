#!/usr/bin/env bash
# CRYO-MP-001 · 回归自查（**每一步之前都先 reseed + clean-orphan-accounts**，issue #157：
# accept 用 `--as staff` mock 登录会按手机号建运行时账号，不清就会把副作用误读成回归失败）。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"

clean() {
  bash doc/verify/reseed.sh --yes >/dev/null 2>&1
  bash doc/waves/tools/clean-orphan-accounts.sh --yes >/dev/null 2>&1
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true
  return 0
}

step() {
  local label="$1"; shift
  clean
  "$@" >/tmp/cryomp-reg-step.log 2>&1
  local rc=$?
  echo "[$label] EXIT=$rc"
  tail -3 /tmp/cryomp-reg-step.log | sed 's/^/    /'
  return 0
}

step 'CRYO-MODEL-001 acc1' bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc1.sh
step 'CRYO-MODEL-001 acc2' bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc2.sh
step 'CRYO-MODEL-001 acc3' bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc3.sh
step 'CRYO-FLOW-001 acc1' bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc1.sh
step 'CRYO-FLOW-001 acc2' bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc2.sh
step 'CRYO-FLOW-001 acc3' bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc3.sh
step 'CRYO-REMIND-001 acc1' bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-acc1.sh
step 'CRYO-REMIND-001 acc2' bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-acc2.sh
step 'CRYO-REMIND-001 acc3' bash doc/waves/reports/CRYO-REMIND-001/accept-runners/remind001-acc3.sh
step 'CRYO-WEB-001 acc1' bash doc/waves/reports/CRYO-WEB-001/accept-runners/cryoweb001-acc1-export.sh
step 'CRYO-WEB-001 acc2' bash doc/waves/reports/CRYO-WEB-001/accept-runners/cryoweb001-acc2-menu.sh
step 'EMBED-MP-001 acc1' bash doc/waves/reports/EMBED-MP-001/accept-runners/mpembed-acc1.sh
step 'SAMPLE-MP-002 acc2' bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc2.sh

echo '── ExtChokepointContractTest（D1 的外部咽喉门，四条不变量）'
clean
(cd code/RuoYi-Vue-Plus && mvn -q -s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" \
  -Duser.home="${WS}/.buildhome" -pl ruoyi-modules/ruoyi-lqg -am test \
  -Dtest='ExtChokepointContractTest' -Dsurefire.failIfNoSpecifiedTests=true) 2>&1 \
  | grep -E 'Tests run|BUILD|ERROR' | sed 's/^/    /'
echo "[ExtChokepointContractTest] EXIT=${PIPESTATUS[0]}"

echo '── D1 回归包（--skip-build）'
clean
bash doc/waves/regression/D1/verify.sh --skip-build 2>&1 | tail -8 | sed 's/^/    /'
echo "[D1 verify.sh] EXIT=${PIPESTATUS[0]}"

clean
echo 'REGRESSION DONE'
