#!/usr/bin/env bash
# D2 / r4 / L2 —— 组 1 全链路驱动（本片自写）：
#   reseed → g1a（外部新增）→ g2（工作台九种筛选 + 核验抽屉两出口）→ g1b（外部看原因/改后重提/只读 + extB 只看我提交的）
# 全部步骤共享同一条记录（id 通过 .l2r4-state.json 传），所以必须整链一起跑。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
LOG="${ROOT}/.tmp/logs"
mkdir -p "$LOG"
cd "$ROOT"

echo "### reseed $(date '+%T')"
bash doc/verify/reseed.sh --yes >"$LOG/r4-reseed.log" 2>&1 || { echo "reseed 失败"; exit 2; }
rm -f "$TMPDIR"/lqg-verify-token-* 2>/dev/null || true

echo "### g1a 外部新增送检 $(date '+%T')"
node doc/waves/regression/D2/L2r4-g1a-ext-submit.mjs >"$LOG/r4-g1a.log" 2>&1
G1A=$?

echo "### g2 工作台总表 + 核验抽屉 $(date '+%T')"
node doc/waves/regression/D2/L2r4-g2-web-table.mjs >"$LOG/r4-g2.log" 2>&1
G2=$?

echo "### g1b 外部看原因/重提/只读 + extB $(date '+%T')"
node doc/waves/regression/D2/L2r4-g1b-ext-resubmit.mjs >"$LOG/r4-g1b.log" 2>&1
G1B=$?

echo "================ 汇总 ================"
for f in r4-g1a r4-g2 r4-g1b; do
  printf '%-8s %s\n' "$f" "$(grep -E '^== ' "$LOG/$f.log" | tail -1)"
done
grep -h '^FAIL' "$LOG"/r4-g1a.log "$LOG"/r4-g2.log "$LOG"/r4-g1b.log || echo "（无 FAIL 行）"
echo "exit: g1a=$G1A g2=$G2 g1b=$G1B"
exit $(( G1A + G2 + G1B ))
