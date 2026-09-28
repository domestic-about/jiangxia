#!/usr/bin/env bash
# EMBED-MODEL-001 · accept 2（STATE）实跑脚本。
#
# 与 ticket 的 accept.run **逐字相同**，唯一差异 = 去掉 `--fresh-module ruoyi-lqg`：
# 本 subagent 沙箱里 api.sh 第 71 行的 `ps -o lstart=` 被禁（`Operation not permitted`）
# → 恒 exit 1。按规矩**没有改 api.sh**，等价反 stale 证据见完工报告 §4.0
# （源码不比 jar 新 + 进程持该 jar + 启动晚于 jar + 嵌套 jar 内含本票全部 class + Flyway 启动日志）。
#
# ★ 长链包进函数判**整条** rc（AUTH-STAFF-001 坑 3）。
# ★ 开头清 token 缓存：api.sh 的 token 只按 mtime 判 20 分钟新鲜，改库后会假 401 / 假 200。
set -uo pipefail

RUNNER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${RUNNER_DIR}/../../../../.." && pwd)"
cd "${ROOT}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  post() { bash doc/verify/api.sh --as staff --bizcode POST /lqg/embed "$1"; } &&
  bash doc/verify/api.sh --as staff GET /lqg/sys/ping >/dev/null &&
  post '{"sampleId":9000001005,"paraffinBlockNo":"T-X1","stainTypes":["NONE","HE"]}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"paraffinBlockNo":"T-X2","stainTypes":["OTHER"]}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"paraffinBlockNo":"T-X3","stainTypes":["PAS"]}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001002,"paraffinBlockNo":"T-X4"}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"paraffinBlockNo":"T-E01-1"}' | grep -qE '^(400|500)' &&
  post '{"sampleId":9000001005,"sampleType":"组织"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed WHERE paraffin_block_no LIKE 'T-X%' OR (paraffin_block_no='T-E01-1' AND sample_id<>9000001001) OR (paraffin_block_no IS NULL AND submit_source='internal')" --eq 0 &&
  post '{"sampleId":9000001005,"paraffinBlockNo":"T-OK1","stainTypes":["IHC","HE"],"markers":[{"markerName":"Ki67","expression":"weak"},{"expression":"negative"}]}' | grep -qE '^200' &&
  python3 doc/verify/db.py --sql "SELECT e.stain_types || '|' || COALESCE(e.tissue_receive_time::text,'-') || '|' || e.submit_source || '|' || e.verify_status = 'HE,IHC|' || s.receive_date::text || '|internal|valid' FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id=e.sample_id WHERE e.paraffin_block_no='T-OK1'" --eq True &&
  python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_embed_marker m JOIN t_lqg_embed e ON e.id=m.embed_id WHERE e.paraffin_block_no='T-OK1' AND m.del_flag='0'" --eq 2 &&
  bash doc/verify/api.sh --as staff --bizcode PUT /lqg/sample/9000001004/verify '{"action":"invalid","reason":"想改判"}' | grep -qE '^(400|500)' &&
  python3 doc/verify/db.py --sql "SELECT verify_status FROM t_lqg_sample WHERE id=9000001004" --eq valid &&
  bash doc/verify/reseed.sh --yes >/dev/null
}

if run; then
  echo "ACCEPT-2 EXIT=0"
  exit 0
fi
echo "ACCEPT-2 FAILED"
exit 1
