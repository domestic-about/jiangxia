#!/usr/bin/env bash
# 票面 accept（逐字重放）：QC-WEB-002 accept[2] form=API
# 前端构建是本次产物；两个页签已接入编辑页；评分选项文字照模板原文、没有质量等级结论
set -euo pipefail
cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
grep -q 'OrganoidQcTab' src/views/lqg/qc/editor/index.vue && grep -q 'ScoreTab' src/views/lqg/qc/editor/index.vue &&
grep -q 'organoid_observe' src/views/lqg/qc/editor/OrganoidQcTab.vue &&
grep -q 'useDict' src/views/lqg/qc/editor/ScoreTab.vue &&
! grep -nE '质量偏差|质量中等|质量良好' src/views/lqg/qc/editor/ScoreTab.vue
