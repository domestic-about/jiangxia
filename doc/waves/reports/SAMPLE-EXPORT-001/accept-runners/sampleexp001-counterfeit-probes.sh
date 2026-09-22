#!/usr/bin/env bash
# SAMPLE-EXPORT-001 · counterfeit 探针（accept 之外的对抗性取证）
#   ① 全表扫描两个导出文件：按钮列只有「有 / 无」、性别只有中文、加密列是明文、
#      软删行与另一类的行不出现、表头没有列表 VO 才有的列、日期格格式；
#   ② 类别由端点决定（sampleKind 传反了也不串）；
#   ③ 五种筛选下导出**行数 = 同条件列表 total**（两侧不同源：导出文件 vs 列表接口）；
#   ④ 外部角色 / 匿名一律 403。
#
#   bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-counterfeit-probes.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."
HERE="doc/waves/reports/SAMPLE-EXPORT-001/accept-runners"

fail=0
np() { python3 "$@" 2>/dev/null; }

echo "== ① 全表扫描 =="
bash doc/verify/api.sh --as staff --out /tmp/lqg-probe-tissue.xlsx POST /lqg/sample/export/tissue
bash doc/verify/api.sh --as staff --out /tmp/lqg-probe-organoid.xlsx POST /lqg/sample/export/organoid
np "${HERE}/probe_cells.py" tissue /tmp/lqg-probe-tissue.xlsx || fail=1
np "${HERE}/probe_cells.py" organoid /tmp/lqg-probe-organoid.xlsx || fail=1

echo
echo "== ② 类别由端点决定（传反了也不串） =="
bash doc/verify/api.sh --as staff --out /tmp/lqg-probe-tissue2.xlsx POST "/lqg/sample/export/tissue?sampleKind=organoid"
obs_t="$(np "${HERE}/count_rows.py" /tmp/lqg-probe-tissue2.xlsx)"
echo "  /export/tissue?sampleKind=organoid    → 行数 ${obs_t}（期望 8 = tissue 全量）"
[ "${obs_t}" = "8" ] || { echo "  ✗ 类别没被端点钉住"; fail=1; }

bash doc/verify/api.sh --as staff --out /tmp/lqg-probe-organoid2.xlsx POST "/lqg/sample/export/organoid?sampleKind=tissue"
obs_o="$(np "${HERE}/count_rows.py" /tmp/lqg-probe-organoid2.xlsx)"
echo "  /export/organoid?sampleKind=tissue    → 行数 ${obs_o}（期望 1 = organoid 全量）"
[ "${obs_o}" = "1" ] || { echo "  ✗ 类别没被端点钉住"; fail=1; }

echo
echo "== ③ 导出与列表同一份 where（五组筛选，行数 == 列表 total） =="
probe_filter() { # probe_filter <sheet> <query>
  local sheet="$1" query="$2" file="/tmp/lqg-probe-f.xlsx" total rows
  bash doc/verify/api.sh --as staff --out "${file}" POST "/lqg/sample/export/${sheet}?${query}" || return 1
  rows="$(np "${HERE}/count_rows.py" "${file}")"
  total="$(bash doc/verify/api.sh --as staff GET "/lqg/sample/list?${query}&sampleKind=${sheet}&pageSize=100" | jq '.total')"
  printf '  %-52s 导出 %s 行 / 列表 total %s\n' "${sheet}?${query}" "${rows}" "${total}"
  [ "${rows}" = "${total}" ]
}
probe_filter tissue "sourceUnitId=9000009001" || fail=1
probe_filter tissue "verifyStatus=pending" || fail=1
probe_filter tissue "tissueType=%E8%82%9D" || fail=1
probe_filter tissue "receiveDateBegin=2026-08-20&receiveDateEnd=2026-09-05" || fail=1
probe_filter tissue "operatorName=%E6%9D%8E&submitSource=external" || fail=1
probe_filter organoid "sourceUnitId=9000009002" || fail=1

echo
echo "== ④ 外部 403；匿名 401（未认证） =="
for as in extA extB; do
  out="$(bash doc/verify/api.sh --as "${as}" --bizcode POST /lqg/sample/export/tissue 2>&1)"
  printf '  --as %-5s → %s\n' "${as}" "${out}"
  printf '%s' "${out}" | grep -qE '^403' || { echo "  ✗ ${as} 没被挡住"; fail=1; }
done
for sheet in tissue organoid; do
  out="$(bash doc/verify/api.sh --as extA --bizcode POST "/lqg/sample/export/${sheet}" 2>&1)"
  printf '  --as extA /export/%-8s → %s\n' "${sheet}" "${out}"
  printf '%s' "${out}" | grep -qE '^403' || { echo "  ✗ extA 能调 /export/${sheet}"; fail=1; }
done
out="$(bash doc/verify/api.sh --as anon --bizcode POST /lqg/sample/export/tissue 2>&1)"
printf '  --as %-5s → %s\n' anon "${out}"
printf '%s' "${out}" | grep -qE '^(401|403)' || { echo "  ✗ anon 没被挡住"; fail=1; }

echo
echo "COUNTERFEIT PROBES " $([ "${fail}" = 0 ] && echo PASS || echo FAIL)
exit "${fail}"
