#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-WEB-002 accept[1] form=STATE
# 评分即时反馈过 fixture：0 分档照算、没选全合计为空；保存只提交档位
set -euo pipefail
cd code/plus-ui &&
grep -q 'doc/verify/fixtures/score-cases.json' src/views/lqg/qc/editor/score.fixture.spec.ts &&
! grep -nE '\.(skip|todo|only)\(' src/views/lqg/qc/editor/score.fixture.spec.ts &&
pnpm vitest run src/views/lqg/qc/editor/score.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-score.json >/dev/null &&
jq -e '.numFailedTests == 0 and .numPassedTests >= 6' /tmp/lqg-score.json &&
node -e "const c=require('../../doc/verify/fixtures/score-cases.json').cases; if(c.length!==6||!c.some(x=>x.expect.items.includes(0))||!c.some(x=>x.expect.total===null)) process.exit(1)" &&
! grep -nE '(preCulture|cultureDays|organoidCount|diameter)Score|totalScore' src/api/lqg/qc/*.ts | grep -iE 'put|save|update' | grep -q .
