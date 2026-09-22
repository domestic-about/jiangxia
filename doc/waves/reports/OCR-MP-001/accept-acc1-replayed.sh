#!/usr/bin/env bash
# 票面 accept（逐字重放）：OCR-MP-001 accept[1] form=STATE
# 预填合并规则过 fixture：只填空项、已手填的不覆盖、空值不填不标、表单里没有的键忽略
set -euo pipefail
cd code/miniapp &&
grep -q 'doc/verify/fixtures/prefill-cases.json' src/pages/sample/ocr/prefill.fixture.spec.ts &&
! grep -nE '\.(skip|todo|only)\(' src/pages/sample/ocr/prefill.fixture.spec.ts &&
pnpm vitest run src/pages/sample/ocr/prefill.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-prefill.json >/dev/null &&
jq -e '.numFailedTests == 0 and .numPassedTests >= 5' /tmp/lqg-prefill.json &&
node -e "const c=require('../../doc/verify/fixtures/prefill-cases.json').cases; if(c.length!==5||!c.some(x=>x.case.startsWith('B-'))||!c.some(x=>x.case.startsWith('E-'))) process.exit(1)"
