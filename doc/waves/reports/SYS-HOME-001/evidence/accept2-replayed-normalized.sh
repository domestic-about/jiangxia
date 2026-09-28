#!/usr/bin/env bash
# 票面 accept（逐字重放）：SYS-HOME-001 accept[2] form=API
# 前端构建是本次产物；首页五张卡片接的是接口而不是写死的数；为 0 不隐藏；菜单角标与卡片同一个来源（含石蜡包埋）
set -euo pipefail
cd code/plus-ui && rm -rf dist && pnpm build:prod >/dev/null && test -f dist/index.html &&
grep -q 'home/todo' src/api/lqg/home.ts && grep -c 'TodoCard' src/views/lqg/home/index.vue | awk '{exit !($1 >= 5)}' &&
! grep -nE 'v-if="[^"]*(pendingSamples|pendingEmbeds|cryoOverdue|pendingExtUsers|renderFailed)[^"]*> *0"' src/views/lqg/home/index.vue &&
! grep -nE 'echarts|el-statistic' src/views/lqg/home/index.vue &&
grep -q 'pendingEmbeds' src/store/modules/lqgTodo.ts && grep -rq 'lqgTodo' src/layout/components/Sidebar && ! grep -rq 'home/todo' src/layout/components/Sidebar
