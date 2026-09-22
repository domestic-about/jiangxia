#!/usr/bin/env bash
# EMBED-MP-001 · accept 3（STATE）—— 填写页布局只认后端给的身份、状态与入口模式：
#   外部只渲染选择样本与两项；外部有效与同组别人的只读；内部看外部待核验只读；
#   内部管理进来的仍是只读详情，右上角「修改」由 showEditEntry 决定；身份缺失什么都不渲染。
#
# 跑法：bash doc/waves/reports/EMBED-MP-001/accept-runners/mpembed-acc3.sh
#
# ★ 与 ticket 正文的差异：`pnpm` 前加 `npm_config_store_dir=`（本机 pnpm 全局缓存在沙箱里只读）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"

run_accept_3() {
  cd code/miniapp &&
  grep -q 'doc/verify/fixtures/embed-form-cases.json' src/pages/embed/layout.fixture.spec.ts &&
  ! grep -nE '\.(skip|todo|only)\(' src/pages/embed/layout.fixture.spec.ts &&
  npm_config_store_dir="${WS}/.pnpm-store" pnpm vitest run src/pages/embed/layout.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-embed-layout.json >/dev/null &&
  jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-embed-layout.json &&
  node -e "const f=require('../../doc/verify/fixtures/embed-form-cases.json'); const c=f.cases; if(c.length!==9||f.externalFields.length!==3||c.filter(x=>x.identity==='external').some(x=>x.expect.fields.some(k=>!f.externalFields.includes(k)))||c.filter(x=>!x.expect.editable).length!==5) process.exit(1)" &&
  grep -q 'showEditEntry' src/pages/embed/layout.ts && grep -q 'showEditEntry' src/pages/embed/form.vue &&
  ! grep -nE 'internalNo' src/pages/embed/SamplePickerExt.vue
}

echo "########## EMBED-MP-001 · accept 3（STATE）##########"
echo "（差异：pnpm 前加 npm_config_store_dir；其余字面逐字）"
run_accept_3
RC=$?
echo "ACCEPT-3 EXIT=${RC}"
exit "${RC}"
