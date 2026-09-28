#!/usr/bin/env bash
# SAMPLE-EXPORT-001 · 回归自查（本票动了 sample 读侧 + 样本总表页面 + sample 域菜单段）
#
#   bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-regression.sh
#
# ★ 已知红（不是本票实现问题，逐条见完工报告 §6）：
#   - SAMPLE-WEB-001 accept 2 段 4：那条断言要求**5210 下的 F 权限集合精确等于六个**，
#     而本票的 accept 2 强制「5217（lqg:sample:export，parent 5210，授 101/102）」。
#     两条 accept 不可能同时绿 —— 需求层冲突（WARN-2）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."

run() { # run <名称> <脚本>
  local name="$1"; shift
  echo "========== ${name} =========="
  local out rc
  out="$("$@" 2>&1)"; rc=$?
  printf '%s\n' "${out}" | tail -18
  echo "---- ${name} rc=${rc}"
  return "${rc}"
}

fail=0
run "EMBED-WEB-001 accept 1"  bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc1-export.sh || fail=1
run "EMBED-WEB-001 accept 2"  bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc2-menu.sh   || fail=1
run "SAMPLE-WEB-001 accept 1" bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc1-api.sh       || fail=1
run "SAMPLE-WEB-001 accept 2" bash doc/waves/reports/SAMPLE-WEB-001/accept-runners/web001-acc2-menu.sh      || fail=1
run "SAMPLE-MP-002 accept 2"  bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc2.sh             || fail=1
echo "========== ExtChokepointContractTest（D2 的 L0 咽喉门；本票新代码不在 ext 包）=========="
grep -h "ExtChokepointContractTest" code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/target/surefire-reports/*.txt | head -2
echo "REGRESSION rc=${fail}（1 = 有红，逐条见上）"
exit "${fail}"
