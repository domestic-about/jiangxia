#!/usr/bin/env bash
# CRYO-MP-001 · accept 4（API）—— ticket `run` 逐字，唯一偏差 = `pnpm` 前加
# `npm_config_store_dir=<ws>/.pnpm-store`（本机 pnpm 全局缓存在沙箱里只读，SYS-MP-001 坑 4）。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$WS"

run() {
  cd code/miniapp && rm -rf dist/build/mp-weixin && npm_config_store_dir="${WS}/.pnpm-store" pnpm build:mp-weixin >/dev/null &&
  test -f dist/build/mp-weixin/pages/cryo/form.js && test -f dist/build/mp-weixin/pages/ledger/index.js &&
  grep -q "cryo" src/pages/ledger/sheets.ts && grep -rq "overdueOnly" src/pages/ledger/sheets.ts src/api/cryo.ts && grep -q "cryo" src/pages/history/sources.ts &&
  grep -rq "@/components/lqg/CryoBatchSheet.vue" src/pages/ledger &&
  grep -qE 'pages/cryo/form.*mode=edit|mode=edit.*pages/cryo/form' src/components/lqg/CryoBatchSheet.vue &&
  ! grep -nE '冻存密度|液氮储存位置' src/pages/ledger/index.vue &&
  ! grep -rnE 'adjust|盘点调整' src/pages/cryo src/pages/ledger src/components/lqg/CryoBatchSheet.vue
}

run
rc=$?
echo "ACCEPT-4 EXIT=$rc"
exit "$rc"
