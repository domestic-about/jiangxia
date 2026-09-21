#!/usr/bin/env bash
# SAMPLE-MP-002 · accept 2（DATA）—— 列名/列序只有一个来源且与甲方四份 xlsx 原件逐字对得上；
#                      构建产物里有表格页与类器官填写页；页面里不手写列、没有新增与保存；
#                      点一行进只读模式；页底小字已去掉「修改」二字（CR-20260918-07）。
#
# 跑法（cwd 随意，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc2.sh
#
# ★ 与 ticket 正文 accept.run 的差异：`pnpm build:mp-weixin` 前加了 `npm_config_store_dir=`
#   环境变量（本机 pnpm / npm 的全局缓存在沙箱里只读，SYS-MP-001 坑 4；只影响 subagent 沙箱）。
#   别的字面逐字。
#
# ★ 长链包进函数判整条 rc（`set -e` 不管非末尾位置的失败）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"

run_accept_2() {
  cd code/miniapp &&
  rm -rf dist/build/mp-weixin &&
  npm_config_store_dir="${WS}/.pnpm-store" pnpm build:mp-weixin >/dev/null &&
  test -f dist/build/mp-weixin/pages/ledger/index.js && test -f dist/build/mp-weixin/pages/organoid/form.js &&
  grep -q 'doc/verify/fixtures/ledger-columns-cases.json' src/pages/ledger/columns.fixture.spec.ts &&
  ! grep -nE '\.(skip|todo|only)\(' src/pages/ledger/columns.fixture.spec.ts &&
  npm_config_store_dir="${WS}/.pnpm-store" pnpm vitest run src/pages/ledger/columns.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-ledger-cols.json >/dev/null &&
  jq -e '.numFailedTests == 0 and .numPassedTests >= 4' /tmp/lqg-ledger-cols.json &&
  grep -q '@/components/lqg/LedgerTable.vue' src/pages/ledger/index.vue &&
  ! grep -nE '来源单位|供体姓名|冻存样品|石蜡块编号' src/pages/ledger/index.vue src/components/lqg/LedgerTable.vue &&
  ! grep -nE '新增|保存|核验并|判为' src/pages/ledger/index.vue src/components/lqg/LedgerTable.vue &&
  grep -q 'mode=view' src/pages/ledger/sheets.ts && ! grep -q 'mode=edit' src/pages/ledger/sheets.ts &&
  grep -q '核验、冻存取用请到网页工作台' src/pages/ledger/index.vue && ! grep -q '修改、核验、冻存取用' src/pages/ledger/index.vue &&
  cd ../.. &&
  for S in "tissue:样本记录信息表模板" "organoid:类器官收样记录模板" "embed:石蜡包埋送样记录模板" "cryo:-80冻存模板"; do
    diff <(python3 doc/verify/xlsx_header.py --print-header --template "_input/templates/${S#*:}.xlsx") <(jq -r --arg k "${S%%:*}" '.sheets[$k].template[]' doc/verify/fixtures/ledger-columns-cases.json) >/dev/null || exit 1;
  done &&
  jq -e '.sheets | to_entries | all(.value as $v | $v.expect == ([$v.template[] | select(. != $v.frozen)] + $v.extra))' doc/verify/fixtures/ledger-columns-cases.json
}

echo "########## SAMPLE-MP-002 · accept 2（DATA）##########"
echo "（差异：pnpm 前加 npm_config_store_dir；其余字面逐字）"
run_accept_2
RC=$?
echo "ACCEPT-2 EXIT=${RC}"
exit "${RC}"
