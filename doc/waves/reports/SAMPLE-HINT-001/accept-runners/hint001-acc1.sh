#!/usr/bin/env bash
# SAMPLE-HINT-001 · accept 1（DATA）逐段实跑器。
#
# 与 ticket 的 run **逐字相同**，唯一差异 = 去掉 `--fresh-module ruoyi-lqg`：
# 本 subagent 沙箱里 api.sh 第 71 行的 `ps -o lstart=` 被禁（`/bin/ps: Operation not permitted`），
# macOS 上 `date -d` 回退也不认 → 该守卫恒非 0 退出（连续 10+ 张票的既有 WARN）。
# 按规矩**没有改 api.sh**，等价反 stale 证据见完工报告 §4.0。
#
# ★ 长链包进函数判**整条** rc：`a && b && c` 在 `set -e` 下不管非末尾位置的失败，
#   会静默短路 exit 0（AUTH-STAFF-001 坑 3）。
# ★ 开头清 token 缓存：api.sh 的 token 只按 mtime 判 20 分钟新鲜，改库后会假 401 / 假 200。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run() {
  bash doc/verify/reseed.sh --yes >/dev/null || { echo "reseed FAILED"; return 1; }

  local ROWS
  ROWS="$(bash doc/verify/api.sh --as staff GET '/lqg/sample/list?pageSize=100')" || return 1

  # 逐样本取 [blockCount, sectioned, stains]
  h() { printf '%s' "${ROWS}" | jq -c --arg id "$1" '.rows[] | select((.id|tostring)==$id) | [.hint.blockCount, .hint.sectioned, .hint.stains]'; }

  echo "---- 1) 1001 两块、一块已切片 HE+IHC"
  local got
  got="$(h 9000001001)"; echo "$got"
  [ "$got" = '[2,true,["HE","IHC"]]' ] || { echo "RED 1"; return 1; }

  echo "---- 2) 1004 一块已切片、无染色（NONE 不是一种染色）"
  got="$(h 9000001004)"; echo "$got"
  [ "$got" = '[1,true,[]]' ] || { echo "RED 2"; return 1; }

  echo "---- 3) 1006 一块已切片、OTHER"
  got="$(h 9000001006)"; echo "$got"
  [ "$got" = '[1,true,["OTHER"]]' ] || { echo "RED 3"; return 1; }

  echo "---- 4) 1008 名下唯一那块是软删 → 零值"
  got="$(h 9000001008)"; echo "$got"
  [ "$got" = '[0,false,[]]' ] || { echo "RED 4"; return 1; }

  echo "---- 5) 1002 名下只有待核验的外部送样 → 零值"
  got="$(h 9000001002)"; echo "$got"
  [ "$got" = '[0,false,[]]' ] || { echo "RED 5"; return 1; }

  echo "---- 6) 没有包埋记录的行 hint 也不许是 null（整页扫一遍）"
  printf '%s' "${ROWS}" | jq -e '[.rows[] | select(.hint == null)] | length == 0' || { echo "RED 6"; return 1; }

  echo "---- 7) 各行块数之和 == 直连库的独立 count（两侧不同源）"
  local dbcount
  dbcount="$(python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id AND s.del_flag='0' WHERE e.del_flag='0' AND e.verify_status='valid'")" || return 1
  echo "db=${dbcount} sum=$(printf '%s' "${ROWS}" | jq '[.rows[].hint.blockCount]|add')"
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id AND s.del_flag='0' WHERE e.del_flag='0' AND e.verify_status='valid'" \
    --eq "$(printf '%s' "${ROWS}" | jq '[.rows[].hint.blockCount]|add')" || { echo "RED 7"; return 1; }

  echo "---- 8) /mp/int/sample/list 同样带 hint"
  bash doc/verify/api.sh --as staff GET '/mp/int/sample/list?pageSize=100' \
    | jq -e '[.rows[]|select((.id|tostring)=="9000001001")|.hint.blockCount]==[2]' || { echo "RED 8"; return 1; }

  echo "---- 9) 收尾 reseed"
  bash doc/verify/reseed.sh --yes >/dev/null || return 1
  return 0
}

echo "########## SAMPLE-HINT-001 · accept 1（DATA）##########"
echo "（差异：去掉 --fresh-module ruoyi-lqg）"
run
RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit "${RC}"
