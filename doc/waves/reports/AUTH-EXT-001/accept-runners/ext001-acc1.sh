#!/usr/bin/env bash
# AUTH-EXT-001 · accept 1（STATE）—— 四条结构性不变量（契约测试逐字节未改 + 跑绿 + 角色注解 + 入口唯一）。
#
# 跑法（cwd = 项目根）：
#   bash doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc1.sh
#
# ★ 与 ticket 正文 accept.run 的唯一差异：Maven 那行**补了三个本机必需的参数**
#   （`-s <ws>/.mvn-settings.xml -Dmaven.repo.local=<ws>/.m2repo -Duser.home=<ws>/.buildhome`）。
#   原样跑会挂：本沙箱 `~/.m2` 只读（已连续多张票命中，见完工报告 WARN 清单 doc-drift 那条）。
#   其余字面逐字保留。
#
# ★ 长链包进函数判整条 rc：`set -e` 不管非末尾位置的失败（AUTH-STAFF-001 坑 3）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"
MVN_ARGS=(-s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome")

run_accept_1() {
  cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
  (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true "${MVN_ARGS[@]}") &&
  test "$(grep -rlE '@SaCheckRole\("lqg_external"\)' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext | wc -l | tr -d ' ')" -ge 2 &&
  ! grep -rnE '"/mp/ext' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java --include=*.java | grep -v '/org/dromara/lqg/ext/' | grep -q .
}

echo "== accept 1 · 四条结构性不变量 =="
cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java \
  && echo "1a cmp 逐字节未改: OK (sha256 $(sha256sum doc/verify/fixtures/java/ExtChokepointContractTest.java | cut -c1-16)…)"
run_accept_1
RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit "${RC}"
