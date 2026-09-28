#!/usr/bin/env bash
# SAMPLE-HINT-001 · accept 2（DDL / 接线 / 一页一查）逐段实跑器。
#
# 与 ticket 的 run **逐字相同**，差异只有一处（沙箱必须，与 D1/D2/D3 全部票同源）：
#   Maven 行补上本机三参数 `-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo
#   -Duser.home=<ws>/.buildhome`（离线仓库与 settings 都在工作区里，沙箱不读 ~/.m2）。
#   本票的 run 里**没有** `--fresh-module`。
#
# ★ 长链包进函数判整条 rc（`set -e` 不管非末尾位置的失败）。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
WS="${ROOT}"
MVN_ARGS=(-s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome")
cd "${ROOT}"

run() {
  echo "---- 1) 样本表上没有被偷加的冗余列（ddl_vs_ssot 逐列相符）"
  python3 doc/verify/ddl_vs_ssot.py --table t_lqg_sample \
    --require-public create_dept,create_by,create_time,update_by,update_time,del_flag || return 1

  echo "---- 2) 工作台总表已接入提示组件"
  grep -q 'HintBadges' code/plus-ui/src/views/lqg/sample/index.vue || return 1
  test -f code/plus-ui/src/views/lqg/sample/HintBadges.vue || return 1

  echo "---- 3) 契约测试（含「传 20 个样本 id 只触发 1 次查询」）"
  (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test \
    -Dtest='SampleHint*Test' -Dsurefire.failIfNoSpecifiedTests=true "${MVN_ARGS[@]}") || return 1

  return 0
}

echo "########## SAMPLE-HINT-001 · accept 2（DDL / 接线 / 一页一查）##########"
echo "（差异：Maven 补三参数；本票 run 里没有 --fresh-module）"
run
RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit "${RC}"
