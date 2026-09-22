#!/usr/bin/env bash
# D2 r2 · L3 第 4 条：角色闸（外部→/lqg/** 与 /mp/int/** 一律 403；内部→/mp/ext/** 403）
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
API="bash doc/verify/api.sh"
echo "## 外部 extA 打 /lqg/**"
$API --as extA --bizcode GET /lqg/sample/list; echo "(exit=$?)"
$API --as extA --bizcode PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-01","internalNo":"T-l3r2x"}'; echo "(exit=$?)"
echo "## 外部 extA 打 /mp/int/**"
$API --as extA --bizcode GET /mp/int/sample/list; echo "(exit=$?)"
echo "## 内部 staff 打 /mp/ext/**"
$API --as staff --bizcode GET /mp/ext/sample/list; echo "(exit=$?)"
$API --as staff --bizcode POST /mp/ext/organoid '{"id":9000001001,"sampleKind":"organoid"}'; echo "(exit=$?)"
echo "## 对照组"
$API --as extA --bizcode GET /mp/ext/sample/list
$API --as staff --bizcode GET /mp/int/sample/list
$API --as admin --bizcode GET /lqg/sample/list
