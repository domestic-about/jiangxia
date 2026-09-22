#!/usr/bin/env bash
# CRYO-WEB-001 · 回归自查（★ 顺序固定：先 reseed + clean-orphan，再跑）
#
# 为什么必须先清孤儿账号（issue #157 / CRYO-FLOW-001 WARN-3）：
#   accept 用 `--as staff` mock 登录 + `POST /lqg/auth/staff` 会按手机号建出运行时 sys_user，
#   D1 的 L1.2 / L1.3c 恰好查这两个手机号 → 不清就跑会假红 5 条（本票第一次跑 D1 前已避开）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
WS="$(pwd)"
R="doc/waves/reports"
LOG="${R}/CRYO-WEB-001/accept-runners/regression.out"
: > "${LOG}"

say() { echo "$@" | tee -a "${LOG}"; }
clean() {
  bash doc/verify/reseed.sh --yes >/dev/null 2>&1
  bash doc/waves/tools/clean-orphan-accounts.sh --yes >/dev/null 2>&1
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
}
step() { # step <标签> <脚本>
  clean
  local label="$1"; shift
  local out; out="$("$@" 2>&1)"; local rc=$?
  say "== ${label} : EXIT=${rc}"
  echo "${out}" | tail -6 | sed 's/^/     /' | tee -a "${LOG}"
}

say "=== CRYO-WEB-001 回归自查（每步前先 reseed + clean-orphan）==="
step "CRYO-MODEL-001 accept 1"      bash "${R}/CRYO-MODEL-001/accept-runners/cryo001-acc1.sh"
step "CRYO-MODEL-001 accept 2"      bash "${R}/CRYO-MODEL-001/accept-runners/cryo001-acc2.sh"
step "CRYO-MODEL-001 accept 3"      bash "${R}/CRYO-MODEL-001/accept-runners/cryo001-acc3.sh"
step "CRYO-FLOW-001 accept 1"       bash "${R}/CRYO-FLOW-001/accept-runners/flow001-acc1.sh"
step "CRYO-FLOW-001 accept 2"       bash "${R}/CRYO-FLOW-001/accept-runners/flow001-acc2.sh"
step "CRYO-FLOW-001 accept 3"       bash "${R}/CRYO-FLOW-001/accept-runners/flow001-acc3.sh"
step "CRYO-REMIND-001 accept 1"     bash "${R}/CRYO-REMIND-001/accept-runners/remind001-acc1.sh"
step "CRYO-REMIND-001 accept 2"     bash "${R}/CRYO-REMIND-001/accept-runners/remind001-acc2.sh"
step "CRYO-REMIND-001 accept 3"     bash "${R}/CRYO-REMIND-001/accept-runners/remind001-acc3.sh"
step "EMBED-WEB-001 accept 1（导出）" bash "${R}/EMBED-WEB-001/accept-runners/embedweb001-acc1-export.sh"
step "EMBED-WEB-001 accept 2（菜单）" bash "${R}/EMBED-WEB-001/accept-runners/embedweb001-acc2-menu.sh"
step "SAMPLE-WEB-001 accept 1"      bash "${R}/SAMPLE-WEB-001/accept-runners/web001-acc1-api.sh"

# D1 的 L0 咽喉门（外部包不变量）
clean
say "== ExtChokepointContractTest"
(cd code/RuoYi-Vue-Plus && mvn -q -s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome" \
   -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='ExtChokepointContractTest' -Dsurefire.failIfNoSpecifiedTests=true 2>&1 \
   | grep -E "Tests run|ERROR" | tail -3) | tee -a "${LOG}"

# D1 回归包（★ 已知只剩「flyway 迁移总数写死成 7」一条假红，issue #82）
clean
say "== D1 回归包（bash doc/waves/regression/D1/verify.sh --skip-build）"
bash doc/waves/regression/D1/verify.sh --skip-build > /tmp/cw001-d1.log 2>&1
say "D1_VERIFY_RC=$?"
grep -E "^(green=|失败 [0-9]+ 条|  - )" /tmp/cw001-d1.log | tee -a "${LOG}"
grep -E "^\s+✗" /tmp/cw001-d1.log | tee -a "${LOG}"

clean
say "=== 回归自查结束（原始输出：accept-runners/regression.out、/tmp/cw001-d1.log）==="
