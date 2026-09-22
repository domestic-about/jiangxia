#!/usr/bin/env bash
# SAMPLE-MP-001 · accept 2 的**构建段**（ticket run 的第一段，逐字照跑）
#
# 与 ticket 逐字相同，只有一处环境差异：`pnpm` 带 `npm_config_store_dir=<ws>/.pnpm-store`
# （本沙箱 store 只能靠环境变量传；`pnpm --store-dir` 在 10.33 会 EACCES）。
set -uo pipefail
WS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "${WS}/code/miniapp" || exit 1

rm -rf dist/build/mp-weixin && \
npm_config_store_dir="${WS}/.pnpm-store" pnpm build:mp-weixin >/dev/null && \
test -f dist/build/mp-weixin/pages/sample/form.js && \
test -f dist/build/mp-weixin/pages/history/index.js && \
! test -e dist/build/mp-weixin/pages/sample/mine.js

RC=$?
if [ "${RC}" -eq 0 ]; then
  echo "ACCEPT-2-BUILD GREEN (exit 0)"
  ls dist/build/mp-weixin/pages/sample/ dist/build/mp-weixin/pages/history/
else
  echo "ACCEPT-2-BUILD RED (exit ${RC})"
  exit 1
fi
