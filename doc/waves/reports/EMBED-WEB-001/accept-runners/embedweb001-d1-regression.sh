#!/usr/bin/env bash
# EMBED-WEB-001 · D1 回归复跑（**不改上游脚本**）
#
# 上游：`bash doc/waves/regression/D1/verify.sh --skip-build`
# 本票实跑结果：**40 绿 / 2 红 / 0 环境错**，两条红都与实现无关：
#
#  红①  L1.1 「D1 的 7 支迁移全部记录在案」→ 期望 '7'，实际 '13'
#        —— 既有假红（D2 起每加一支迁移就假红；D1 那 7 支逐个点名全绿、无失败行）。
#           state.json 已记 issue，EMBED-MODEL-001 报告 §5 也记过。本票新增第 13 支。
#
#  红②  L0.2 「pid … 没持有 ruoyi-admin.jar（跑的不是这个 jar）」
#        —— 沙箱/SIGPIPE 现象，不是实现问题：
#             verify.sh 第 112 行 `if ! lsof -p "$pid" | grep -q 'ruoyi-admin.jar'`
#             `grep -q` 命中第一条就退出并关掉读端；`lsof` 的输出有 390+ 行 / 55KB，
#             它继续写 → SIGPIPE（141）；`set -uo pipefail` 把 141 当管道失败 → 走 bad 分支。
#             同一句里的 `grep -c` 数出 **2** 行命中（见下面的等价取证）。
#           在 `bash -c` 里逐字重放同一句是 HELD —— 依赖 lsof 的输出分块，是竞态。
#
# 本脚本只做「等价取证」，不改上游一个字（`doc/waves/regression/**` 不在本票 touches 里）。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$ROOT"
set -a; . "${ROOT}/doc/verify/verify.env"; set +a
JAR="${ROOT}/code/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar"
PID="$(lsof -ti tcp:8081 -sTCP:LISTEN | head -1)"
OUT="$(lsof -p "${PID}" 2>/dev/null)"

echo "== L0.2 等价取证（不用管道，避开 SIGPIPE） =="
echo "8081 PID            : ${PID}"
echo "进程持有该 jar      : $(printf '%s\n' "${OUT}" | grep -c 'ruoyi-admin.jar') 行命中"
echo "lqg 源码新于 jar    : $(find code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src -type f -newer "${JAR}" | wc -l | tr -d ' ') 个文件"
echo "jar mtime           : $(stat -f '%Sm' -t '%F %T' "${JAR}")"
echo "lsof 输出行数       : $(printf '%s\n' "${OUT}" | wc -l | tr -d ' ')"
echo
echo "== 上游回归包（原样跑；两条红与实现无关） =="
bash doc/waves/regression/D1/verify.sh --skip-build > "${ROOT}/.tmp/ew001-d1-regression.log" 2>&1
rc=$?
grep -E "L0\.2|失败 [0-9]|^  - " "${ROOT}/.tmp/ew001-d1-regression.log" | sed 's/\x1b\[[0-9;]*m//g' | head -8
echo "green=$(grep -c '✓' "${ROOT}/.tmp/ew001-d1-regression.log") red=$(grep -c '✗' "${ROOT}/.tmp/ew001-d1-regression.log") rc=${rc}"
