#!/usr/bin/env bash
# EMBED-MP-001 · accept 1（API + STATE）—— 小程序构建产物里有填写页与表格页、
#   石蜡包埋工作表与历史页签已注册、染色切换过 fixture、内部接口只给内部角色、
#   内部历史默认是中心全部内部人员的石蜡包埋记录（CR-20260918-07）。
#
# 跑法（cwd 随意，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/EMBED-MP-001/accept-runners/mpembed-acc1.sh
#
# ★ 与 ticket 正文 accept.run 的两处差异（都只影响 subagent 沙箱，别的字面逐字）：
#   1) 去掉 `--fresh-module ruoyi-lqg`：api.sh 第 71 行用 `ps -o lstart=`，本沙箱被禁 → 恒 exit 2。
#      等价证据见报告 §4.0（源码不新于 jar / PID 持有该 jar / 嵌套 jar 内含新类）。
#   2) `pnpm` 前加 `npm_config_store_dir=`（本机 pnpm 全局缓存在沙箱里只读）。
# ★ 长链包进函数判整条 rc（`set -e` 不管非末尾位置的失败）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"

run_accept_1() {
  (cd code/miniapp &&
   rm -rf dist/build/mp-weixin &&
   npm_config_store_dir="${WS}/.pnpm-store" pnpm build:mp-weixin >/dev/null &&
   test -f dist/build/mp-weixin/pages/embed/form.js && test -f dist/build/mp-weixin/pages/ledger/index.js &&
   grep -q "embed" src/pages/ledger/sheets.ts && grep -q "embed" src/pages/history/sources.ts &&
   grep -qE "sort=recent|sort: *'recent'" src/pages/history/sources.ts &&
   ! grep -qE "int/embed/list\?[^\"']*mine=true" src/pages/history/sources.ts &&
   grep -q 'doc/verify/fixtures/stain-toggle-cases.json' src/pages/embed/stain.fixture.spec.ts && ! grep -nE '\.(skip|todo|only)\(' src/pages/embed/stain.fixture.spec.ts &&
   npm_config_store_dir="${WS}/.pnpm-store" pnpm vitest run src/pages/embed/stain.fixture.spec.ts --reporter=json --outputFile=/tmp/lqg-stain-mp.json >/dev/null && jq -e '.numFailedTests == 0 and .numPassedTests >= 9' /tmp/lqg-stain-mp.json) &&
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002002","9000002003","9000002004","9000002006"]' &&
  bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/embed/list' | grep -qE '^403' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100&sort=recent' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002002","9000002003","9000002004","9000002006"] and (.rows[0].id|tostring)=="9000002006" and ([.rows[]|select(.handlerName==null)]|length)==0 and ([.rows[]|select((.id|tostring)=="9000002001")|[.handlerName,.mine]])==[["李工",true]] and ([.rows[]|select((.id|tostring)=="9000002004")|[.handlerName,.mine]])==[["测试管理员",false]]' &&
  bash doc/verify/api.sh --as staff GET '/mp/int/embed/list?pageSize=100&sort=recent&mine=true' | jq -e '([.rows[].id|tostring]|sort)==["9000002001","9000002003"]' &&
  python3 doc/verify/db.py --sql "SELECT id FROM t_lqg_embed WHERE del_flag='0' AND (create_by=9000000101 OR update_by=9000000101)" --col-set "9000002001,9000002003"
}

echo "########## EMBED-MP-001 · accept 1（API + STATE）##########"
echo "（差异：去掉 --fresh-module；pnpm 前加 npm_config_store_dir；其余字面逐字）"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null
run_accept_1
RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit "${RC}"
