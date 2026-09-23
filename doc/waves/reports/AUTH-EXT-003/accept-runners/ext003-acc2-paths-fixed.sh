#!/usr/bin/env bash
# AUTH-EXT-003 · accept 2 —— 票面逐字重放，**只把三处文件路径补全到真实位置**。
#
# 为什么有这份脚本（票面缺陷，见完工报告 §3）：
#   票面 accept 2 第 3/4 段的字面路径 **少了子包段**：
#     test -f …/java/org/dromara/lqg/ext/ExtDocController.java          ← 文件不在 ext 根，也不该在
#     ! grep -nE … …/java/org/dromara/lqg/ext/ExtDocVo.java …/ExtDocPagesVo.java
#   而盘上（以及本仓 5 个既有 ext controller 的一致约定）是：
#     …/lqg/ext/controller/ExtDocController.java
#     …/lqg/ext/domain/vo/ExtDocVo.java        （AUTH-EXT-001 建的就是这个位置）
#     …/lqg/ext/domain/vo/ExtDocPagesVo.java
#   `ExtDocVo.java` 这一条尤其能证明是笔误：它在**本票开工前**就存在于 `ext/domain/vo/`
#   （AUTH-EXT-001 的占位 VO），票面把它写成 `ext/ExtDocVo.java` 只是路径没写全。
#
#   因此这三段是**断言自身的路径写错**，不是实现没落。本脚本只把这三个路径补全，
#   其余（cmp 逐字节 / mvn ExtChokepointContractTest / 禁字 grep / 外部打 /lqg/doc 必须 403）**逐字未改**。
#   ★ 没有为了迎合票面字面而把类搬到 ext 根：那会与 `ext/controller/**`（ExtSample/ExtOrganoid/
#     ExtEmbed/ExtUnit/ExtProfile 五个）的既有布局打架，也白搬一个上游票建好的 VO。
#
# 跑法（任意 cwd）：bash doc/waves/reports/AUTH-EXT-003/accept-runners/ext003-acc2-paths-fixed.sh
# ★ 与票面正文的另一处差异只有 NF2（给 mvn 补三个本机必需参数，连续多张票命中的既有 WARN）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."          # → 项目根
WS="$(pwd)"
export LQG_VERIFY_ENV_FILE="${LQG_VERIFY_ENV_FILE:-$PWD/.tmp/qa-env/8094/verify.env}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
M=code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext

run() {
  cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
  (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true \
      -s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome") &&
  test -f "${M}/controller/ExtDocController.java" &&
  ! grep -nE 'publishedBy|errorMsg|contentHash|internalNo' "${M}/domain/vo/ExtDocVo.java" "${M}/domain/vo/ExtDocPagesVo.java" &&
  bash doc/verify/api.sh --as extA --bizcode GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | grep -qE '^403'
}
run
rc=$?
echo "ACCEPT-2-PATHS-FIXED EXIT=$rc"
exit $rc
