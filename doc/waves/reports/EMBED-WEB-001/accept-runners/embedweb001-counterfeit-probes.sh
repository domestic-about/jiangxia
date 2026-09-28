#!/usr/bin/env bash
# EMBED-WEB-001 · accept 1/2 的 counterfeit 逐条排掉（accept 之外的机器证据）
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$ROOT"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true
bash doc/verify/reseed.sh --yes >/dev/null

echo "---- (b) 染色筛选是整元素匹配：HE 不串 OTHER，OTHER 不串 HE ----"
echo -n "stain=HE    → "; bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=HE&pageSize=100' | jq -c '[.rows[].paraffinBlockNo]'
echo -n "stain=OTHER → "; bash doc/verify/api.sh --as staff GET '/lqg/embed/list?stain=OTHER&pageSize=100' | jq -c '[.rows[].paraffinBlockNo]'

echo "---- (c) 待核验的外部送样也导出（石蜡块编号 / 样本编号 两格为空） ----"
rm -f /tmp/x.xlsx && bash doc/verify/api.sh --as staff --out /tmp/x.xlsx POST /lqg/embed/export
python3 - <<'PY'
import openpyxl, warnings
warnings.filterwarnings('ignore')
ws = openpyxl.load_workbook('/tmp/x.xlsx').worksheets[0]
rows = list(ws.iter_rows(values_only=True))
print('  rows:', len(rows) - 1, '第一行（待核验）=', (rows[1][0], rows[1][1]))
PY

echo "---- (d) 带筛选导出只出筛选结果 ----"
rm -f /tmp/y.xlsx && bash doc/verify/api.sh --as staff --out /tmp/y.xlsx POST '/lqg/embed/export?internalNo=T-hli01'
python3 - <<'PY'
import openpyxl, warnings
warnings.filterwarnings('ignore')
ws = openpyxl.load_workbook('/tmp/y.xlsx').worksheets[0]
rows = list(ws.iter_rows(values_only=True))
print('  rows:', len(rows) - 1, 'blockNos:', [r[0] for r in rows[1:]])
PY

echo "---- (f) 判无效后仍然导出，且与列表 total 一致 ----"
bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify \
  '{"action":"valid","receiveDate":"2026-09-18","internalNo":"T-probe-invalid"}' | jq -c '{code}'
bash doc/verify/api.sh --as staff --bizcode PUT /lqg/embed/9000002006/verify '{"action":"invalid","reason":"探针：无效也要导出"}' | head -1
rm -f /tmp/z2.xlsx && bash doc/verify/api.sh --as staff --out /tmp/z2.xlsx POST '/lqg/embed/export?verifyStatus=invalid'
python3 - <<'PY'
import openpyxl, warnings
warnings.filterwarnings('ignore')
ws = openpyxl.load_workbook('/tmp/z2.xlsx').worksheets[0]
rows = list(ws.iter_rows(values_only=True))
print('  export rows:', len(rows) - 1, [(r[0], r[1]) for r in rows[1:]])
PY
echo -n "  list total: "; bash doc/verify/api.sh --as staff GET '/lqg/embed/list?verifyStatus=invalid&pageSize=100' | jq -c '{total}'

echo "---- (g) 导出视图不是实体 VO：没有 样本ID / 创建时间 / 核验状态 ----"
rm -f /tmp/z4.xlsx && bash doc/verify/api.sh --as staff --out /tmp/z4.xlsx POST /lqg/embed/export
python3 - <<'PY'
import openpyxl, warnings
warnings.filterwarnings('ignore')
h = list(next(openpyxl.load_workbook('/tmp/z4.xlsx').worksheets[0].iter_rows(values_only=True)))
print('  header:', h)
for banned in ['样本ID', 'sampleId', '创建时间', '核验状态', '内部编号', 'marker 的表达情况']:
    print('  contains %-16s: %s' % (banned, banned in h))
PY

bash doc/verify/reseed.sh --yes >/dev/null
echo "PROBES DONE"
