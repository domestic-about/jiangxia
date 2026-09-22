#!/usr/bin/env bash
# CRYO-REMIND-001 · 回归自查（派单 §5.2 点名的那几项）。
#   ★ 顺序很重要：**先 reseed + clean-orphan，再跑回归** —— 同一张票里「先跑 accept、
#     后跑 D1」会因为运行时按手机号建出的孤儿账号让 D1 的 L1.2 / L1.3c 假红（issue #157）。
#   每一项都落日志到本目录，最后汇总 rc。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "${ROOT}"
OUT="doc/waves/reports/CRYO-REMIND-001/accept-runners"
WS="${ROOT}"
LOG="${OUT}/regression.out"
: > "${LOG}"

step() { echo "########## $* ##########" | tee -a "${LOG}"; }

clean() {
  bash doc/verify/reseed.sh --yes >/dev/null 2>&1
  bash doc/waves/tools/clean-orphan-accounts.sh --yes >/dev/null 2>&1
}

RC_ALL=0
run_one() {
  local name="$1"; shift
  step "${name}"
  "$@" >>"${LOG}" 2>&1
  local rc=$?
  echo "  ${name} EXIT=${rc}" | tee -a "${LOG}"
  [ ${rc} -eq 0 ] || RC_ALL=1
}

clean
run_one "CRYO-MODEL-001 accept 1" bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc1.sh
run_one "CRYO-MODEL-001 accept 2" bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc2.sh
run_one "CRYO-MODEL-001 accept 3" bash doc/waves/reports/CRYO-MODEL-001/accept-runners/cryo001-acc3.sh
run_one "CRYO-FLOW-001 accept 1"  bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc1.sh
run_one "CRYO-FLOW-001 accept 2"  bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc2.sh
run_one "CRYO-FLOW-001 accept 3"  bash doc/waves/reports/CRYO-FLOW-001/accept-runners/flow001-acc3.sh
run_one "SAMPLE-HINT-001 accept 1" bash doc/waves/reports/SAMPLE-HINT-001/accept-runners/hint001-acc1.sh

step "ExtChokepointContractTest"
(cd code/RuoYi-Vue-Plus && mvn -q -s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome" \
  -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='ExtChokepointContractTest' -Dsurefire.failIfNoSpecifiedTests=true) >>"${LOG}" 2>&1
RC_EXT=$?
echo "  ExtChokepointContractTest EXIT=${RC_EXT}" | tee -a "${LOG}"
[ ${RC_EXT} -eq 0 ] || RC_ALL=1

clean
step "D1 回归包（--skip-build）"
bash doc/waves/regression/D1/verify.sh --skip-build >>"${LOG}" 2>&1
RC_D1=$?
echo "  D1 verify.sh EXIT=${RC_D1}" | tee -a "${LOG}"

clean
echo "########## 汇总 ##########" | tee -a "${LOG}"
echo "  RC_ALL（前 7 项 + ExtChokepoint）=${RC_ALL}" | tee -a "${LOG}"
echo "  D1 verify.sh rc=${RC_D1}（预期 1：唯一那条红是既有 harness 缺陷——flyway 迁移总数写死）" | tee -a "${LOG}"
exit $(( RC_ALL != 0 ? 1 : 0 ))
