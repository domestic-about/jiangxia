#!/usr/bin/env bash
# SAMPLE-MP-001 · accept 1 的**逐条实跑**（cwd = code/miniapp，与 ticket 的 run 一致）
#
# 与 ticket `run` 逐字相同，只有一处环境差异：
#   `pnpm vitest …` 加了 `npm_config_store_dir=<ws>/.pnpm-store`
#   （本沙箱 pnpm 只能靠环境变量传 store；`pnpm --store-dir` 在 10.33 会 EACCES）。
# 整条链包进函数判 rc（不丢给 `set -e`：非末尾位置失败会静默短路 + exit 0）。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "${WS}/code/miniapp" || exit 1

run() {
  grep -q 'doc/verify/fixtures/sample-form-cases.json' src/pages/sample/layout.fixture.spec.ts &&
  ! grep -nE '\.(skip|todo|only)\(' src/pages/sample/layout.fixture.spec.ts &&
  npm_config_store_dir="${WS}/.pnpm-store" pnpm vitest run src/pages/sample/layout.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-layout.json >/dev/null &&
  jq -e '.numFailedTests == 0 and .numPassedTests >= 13' /tmp/lqg-layout.json &&
  node -e "const f=require('../../doc/verify/fixtures/sample-form-cases.json'); const c=f.cases; if(c.length!==13||f.receiveFields.length!==7||c.filter(x=>x.identity==='internal'&&!x.expect.editable).length!==5||c.filter(x=>x.mode==='view'&&x.expect.editable).length!==0||c.filter(x=>x.mode==='view').length!==2||!c.some(x=>!['new','edit','view'].includes(x.mode))) process.exit(1)" &&
  ! grep -nE 'receiveDate|processTime|hasQcSheet|hasViabilityReport|verifyByName|[Cc]ryo' src/pages/sample/detail-ext.vue &&
  grep -q 'internalNo' src/pages/sample/detail-ext.vue &&
  ! grep -nE 'lqg.ext.show-internal-no|lqgExtShowInternalNo' src/pages/sample/detail-ext.vue &&
  grep -q 'entriesFor' src/pages/history/index.vue && ! test -e src/pages/sample/mine.vue &&
  grep -rq '只看我提交的' src/pages/history && grep -rq 'handlerName' src/pages/history &&
  grep -q 'sort=recent' src/pages/history/sources.ts
}

echo "########## accept 1 · STATE（fixture + 各种 grep）##########"
if run; then
  echo "ACCEPT-1 GREEN (exit 0)"
else
  echo "ACCEPT-1 RED (exit $?)"
  exit 1
fi

echo
echo "---- vitest 计数（accept 里 jq 断的就是它）----"
jq -c '{numTotalTests,numPassedTests,numFailedTests}' /tmp/lqg-layout.json
echo "---- fixture 结构（node 断言的输入）----"
node -e "const f=require('../../doc/verify/fixtures/sample-form-cases.json'); console.log(JSON.stringify({cases:f.cases.length,receiveFields:f.receiveFields.length,internalReadonly:f.cases.filter(x=>x.identity==='internal'&&!x.expect.editable).length,viewEditable:f.cases.filter(x=>x.mode==='view'&&x.expect.editable).length,view:f.cases.filter(x=>x.mode==='view').length,unknownMode:f.cases.some(x=>!['new','edit','view'].includes(x.mode))}))"
