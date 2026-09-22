#!/usr/bin/env bash
# D3 返工 r1 · issue #145（核验抽屉点不动）—— 受影响的 accept + 回归自查 逐条实跑。
#
# 全部复用各票既有的 accept-runners：它们与 ticket 正文的 `run` 字面逐字相同，
# 只去掉了本沙箱跑不了的 `--fresh-module ruoyi-lqg`（api.sh 第 71 行用 `ps -o lstart=`，
# 沙箱里 /bin/ps 是 "Operation not permitted" → 恒非 0），并为 Maven / pnpm 补上
# 本机必需的三个参数与 store_dir（都是既有 WARN）。
#
# 跑法：bash doc/waves/reports/D3-rework-r1-issue145/accept-runners/rework-accepts.sh
# 前置：8081 后端在听（jar 反 stale 自核见报告 §4）；本脚本不走 DB 并发，逐条串行。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"
MVN=(-s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome")
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

step() { echo; echo "##################### $* #####################"; }

step "1) EMBED-WEB-001 · accept 1（DATA · 按模板导出）"
bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc1-export.sh 2>&1 | tail -14

step "2) EMBED-WEB-001 · accept 2（MENU · 菜单 5310 段 / 接核验接口 / 染色 fixture）"
bash doc/waves/reports/EMBED-WEB-001/accept-runners/embedweb001-acc2-menu.sh 2>&1 | tail -6

step "3) EMBED-MP-001 · accept 1（API + STATE · 小程序构建 + 内部历史 + 角色）"
bash doc/waves/reports/EMBED-MP-001/accept-runners/mpembed-acc1.sh 2>&1 | tail -8

step "4) EMBED-MODEL-001 · accept 1（DDL）"
bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc1.sh 2>&1 | tail -5

step "5) EMBED-MODEL-001 · accept 2（STATE · 核验转移表 + 唯一性）"
bash doc/waves/reports/EMBED-MODEL-001/accept-runners/embed001-acc2.sh 2>&1 | tail -5

step "6) AUTH-EXT-002 · accept 1（API · 含 ExtChokepointContractTest 4 条）"
bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc1.sh 2>&1 | tail -12

step "7) AUTH-EXT-002 · accept 2（STATE · 写保护 / 夹带 / 无效重提）"
bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc2.sh 2>&1 | tail -6

step "8) SAMPLE-HINT-001 · accept 1（DATA）"
bash doc/waves/reports/SAMPLE-HINT-001/accept-runners/hint001-acc1.sh 2>&1 | tail -6

step "9) SAMPLE-EXPORT-001 · accept 1（DATA · 两张导出）"
bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-acc1-export.sh 2>&1 | tail -6

step "10) D1 回归（L0 + L1，--skip-build；已知只剩 flyway 写死总数一条假红）"
bash doc/waves/regression/D1/verify.sh --skip-build 2>&1 | tail -25
echo "D1-RC=${PIPESTATUS[0]:-$?}"

step "11) mvn -pl ruoyi-modules/ruoyi-lqg -am test（要求 Tests run > 0 且 0 失败）"
(cd code/RuoYi-Vue-Plus && mvn -pl ruoyi-modules/ruoyi-lqg -am test "${MVN[@]}" 2>&1 | grep -E "Tests run:|BUILD SUCCESS|BUILD FAILURE|ERROR.*ruoyi-lqg" | tail -12)

step "12) plus-ui build:prod"
(cd code/plus-ui && npm_config_store_dir="${WS}/.pnpm-store" pnpm build:prod 2>&1 | tail -8)
echo "BUILD-PROD-RC=$?"

step "收尾：reseed + 清孤儿账号 + 清 token 缓存"
bash doc/verify/reseed.sh --yes >/dev/null && echo "reseed ok"
bash doc/waves/tools/clean-orphan-accounts.sh --yes 2>&1 | tail -3
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
echo "ALL DONE"
