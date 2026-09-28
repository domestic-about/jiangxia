#!/usr/bin/env bash
# SAMPLE-WEB-001 accept 1 —— **原文照抄**，只去掉沙箱里恒非 0 的 `--fresh-module ruoyi-lqg`
# （沙箱 `ps` 被禁 + 回退走 GNU `date -d`，api.sh 的 stale 守卫必失败；反 stale 由报告里
#  jar mtime / `find -newer` / `lsof -p PID | grep ruoyi-admin.jar` 三件自行核实）。
# 另加一行清 token 缓存（改库/改身份后必须清，见 doc/waves/qa 口径）。
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../.."
bash doc/verify/reseed.sh --yes >/dev/null &&
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* &&
q() { bash doc/verify/api.sh --as staff GET "/lqg/sample/list?pageSize=100&$1" | jq -c '[.rows[].id|tostring]|sort'; } &&
test "$(q 'sourceUnitId=9000009001')" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001007"]' &&
test "$(q 'groupId=9000009101')" = '["9000001001","9000001002","9000001003","9000001004","9000001007"]' &&
test "$(q 'sampleKind=organoid')" = '["9000001009"]' &&
test "$(q 'submitSource=internal')" = '["9000001008","9000001009"]' &&
test "$(q 'verifyStatus=pending')" = '["9000001002","9000001007"]' &&
B="$(python3 -c 'import datetime;print(datetime.date.today()-datetime.timedelta(days=26))')" && E="$(python3 -c 'import datetime;print(datetime.date.today()-datetime.timedelta(days=19))')" &&
test "$(q "receiveDateBegin=${B}&receiveDateEnd=${E}")" = '["9000001004","9000001005"]' &&
test "$(q 'sourceUnitId=9000009001&verifyStatus=valid&submitSource=external')" = '["9000001001","9000001004","9000001005"]' &&
test "$(q '')" = '["9000001001","9000001002","9000001003","9000001004","9000001005","9000001006","9000001007","9000001008","9000001009"]' &&
bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=2' | jq -e '[.rows[].verifyStatus] == ["pending","pending"] and .total == 9'
echo "SAMPLE-WEB-001 accept 1: PASS"
