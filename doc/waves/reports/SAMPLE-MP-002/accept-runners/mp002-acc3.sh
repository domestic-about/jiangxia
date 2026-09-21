#!/usr/bin/env bash
# SAMPLE-MP-002 · accept 3（STATE）—— 类器官收样填写页布局只认后端给的身份 / 状态 / 入口模式；
#                      外部只渲染三项且永远没有收样段；内部七项；只读页也没有「修改」的病灶；
#                      身份缺失什么都不渲染。
#
# 跑法（cwd 随意，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/SAMPLE-MP-002/accept-runners/mp002-acc3.sh
#
# ★ 与 ticket 正文 accept.run 的差异：`pnpm vitest` 前加了 `npm_config_store_dir=`（沙箱里
#   pnpm 全局缓存只读，SYS-MP-001 坑 4）。别的字面逐字。
#
# ★ 长链包进函数判整条 rc。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"

run_accept_3() {
  cd code/miniapp &&
  grep -q 'doc/verify/fixtures/organoid-form-cases.json' src/pages/organoid/layout.fixture.spec.ts &&
  ! grep -nE '\.(skip|todo|only)\(' src/pages/organoid/layout.fixture.spec.ts &&
  npm_config_store_dir="${WS}/.pnpm-store" pnpm vitest run src/pages/organoid/layout.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-organoid-layout.json >/dev/null &&
  jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-organoid-layout.json &&
  node -e "const f=require('../../doc/verify/fixtures/organoid-form-cases.json'); const c=f.cases; const ext=c.filter(x=>x.identity==='external'); if(c.length!==9||f.externalFields.length!==3||f.internalFields.length!==7||ext.some(x=>x.expect.fields.some(k=>f.internalFields.includes(k)&&!f.externalFields.includes(k)))||c.filter(x=>!x.expect.editable).length!==5) process.exit(1)" &&
  ! grep -nE 'donorName|hospitalNo|tissueType' src/pages/organoid/form.vue && grep -q 'organoidType' src/pages/organoid/form.vue
}

echo "########## SAMPLE-MP-002 · accept 3（STATE）##########"
echo "（差异：pnpm 前加 npm_config_store_dir；其余字面逐字）"
run_accept_3
RC=$?
echo "ACCEPT-3 EXIT=${RC}"
exit "${RC}"
