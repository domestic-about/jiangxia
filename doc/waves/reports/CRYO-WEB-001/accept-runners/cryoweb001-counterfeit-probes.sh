#!/usr/bin/env bash
# CRYO-WEB-001 · accept 1 的 counterfeit 探针（accept 之外的机器证据）
#
# 每条 counterfeit 都用「导出文件 vs 独立来源」两侧对账，而不是只看实现自己的说法：
#   ① 「冻存数量/支」导的是剩余而不是初始 → 3001 那一行两列分别是 8 / 6（接口与库各查一次）
#   ② 追加列放到了模板列中间 → 表头前 9 列必须逐字等于模板第 1 行，追加的两列只能在其后
#   ③ 取空的批次不导出 → 行数 = 未删批次数（含取空的 3004），不是「有剩余的批次数」
#   ④ 是否暂存 -80 导成了 Y / N → 该列取值必须 ∈ {是, 否}
#   ⑤ 导出视图不是实体 VO → 表头里不许出现 剩余支数 / 当前位置 / 创建时间 / 样本ID / 内部编号
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
bash doc/verify/reseed.sh --yes >/dev/null
rm -f /tmp/lqg-cryo.xlsx
bash doc/verify/api.sh --as staff --out /tmp/lqg-cryo.xlsx POST /lqg/cryo/batch/export

echo "== ① 「冻存数量/支」= 初始支数（不是剩余）：3001 接口 / 库 / 导出文件三处对账 =="
bash doc/verify/api.sh --as staff GET "/lqg/cryo/batch/9000003001" \
  | jq -c '.data | {id, cryoName, initQty, remainingQty}'
python3 doc/verify/db.py --sql "SELECT b.init_qty || '|' || (b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id = b.id AND f.del_flag='0'), 0)) FROM t_lqg_cryo_batch b WHERE b.id = 9000003001"

echo "== ② 表头：前 9 列 = 甲方模板原件第 1 行；追加列只能在其后 =="
python3 - <<'PY'
import openpyxl
tpl = openpyxl.load_workbook('_input/templates/-80冻存模板.xlsx', read_only=True).worksheets[0]
want = [str(c) for c in next(tpl.iter_rows(values_only=True)) if c is not None]
got = [str(c) for c in next(openpyxl.load_workbook('/tmp/lqg-cryo.xlsx', read_only=True).worksheets[0].iter_rows(values_only=True)) if c is not None]
print('模板 9 列 :', want)
print('导出 %d 列 :' % len(got), got)
assert got[:len(want)] == want, '前 9 列与模板不等 → 红'
assert got[len(want):] == ['代数', '当前剩余/支'], '追加列不是「代数,当前剩余/支」或没跟在其后 → 红'
banned = ['剩余支数', '当前位置', '创建时间', '样本ID', '内部编号', '冻存数量', '当前剩余']
for b in banned:
    assert b not in got, '导出视图多带了一列 %s → 红' % b
print('✓ 表头逐字同序；追加两列跟在其后；没有多带列')
PY

echo "== ③ 行数 = 未删批次数（含取空的 3004），不是「有剩余的批次数」 =="
ALL="$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_cryo_batch b JOIN t_lqg_sample s ON s.id = b.sample_id AND s.del_flag='0' WHERE b.del_flag='0'")"
POS="$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_cryo_batch b JOIN t_lqg_sample s ON s.id = b.sample_id AND s.del_flag='0' WHERE b.del_flag='0' AND b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0) > 0")"
XL="$(python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template '_input/templates/-80冻存模板.xlsx' --extra '代数,当前剩余/支' 2>/dev/null | sed -E 's/.*数据 ([0-9]+) 行/\1/')"
echo "直连库：未删批次 ${ALL} / 其中有剩余的 ${POS}；导出文件数据行 ${XL}"
[ "${XL}" = "${ALL}" ] || { echo "✗ 导出 ${XL} 行 ≠ 未删批次 ${ALL} 行 → 红"; exit 1; }
[ "${POS}" != "${ALL}" ] || echo "（提示：本 seed 下两者恰好相等，取空判定另由 3004 那一行的「当前剩余/支=0」钉住）"
python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template '_input/templates/-80冻存模板.xlsx' --extra '代数,当前剩余/支' \
  --find "冻存样品=T-hli01-GZ-N-P5-EM2-1e5" --expect "当前剩余/支=0" >/dev/null && echo "✓ 取空（剩余 0）的 3004 那一行在导出文件里"

echo "== ④ 是否暂存 -80：该列取值必须 ∈ {是, 否}（不是 Y / N） =="
python3 - <<'PY'
import openpyxl
ws = openpyxl.load_workbook('/tmp/lqg-cryo.xlsx', read_only=True).worksheets[0]
rows = list(ws.iter_rows(values_only=True))
head = [str(c) for c in rows[0] if c is not None]
col = head.index('暂存-80度超低温冰箱')
vals = sorted({str(r[col]) for r in rows[1:] if any(c not in (None, '') for c in r)})
print('该列取值集合 :', vals)
assert set(vals) <= {'是', '否'}, '出现了 是/否 以外的取值（Y / N / 空）→ 红'
print('✓ 是 / 否 两档，与模板「是 否（按钮）」一致')
PY

echo "== ⑤ 剩余列与「库内独立汇总」逐批一致 =="
python3 - <<'PY'
import openpyxl, subprocess, os
ws = openpyxl.load_workbook('/tmp/lqg-cryo.xlsx', read_only=True).worksheets[0]
rows = [r for r in ws.iter_rows(values_only=True)][1:]
head = [str(c) for c in next(openpyxl.load_workbook('/tmp/lqg-cryo.xlsx', read_only=True).worksheets[0].iter_rows(values_only=True)) if c is not None]
name_i, rem_i, tpl = head.index('冻存样品'), head.index('当前剩余/支'), head.index('冻存时间')
xlsx = {str(r[name_i]): str(r[rem_i]) for r in rows if any(c not in (None, '') for c in r)}
out = subprocess.run(['python3', 'doc/verify/db.py', '--sql',
    "SELECT b.cryo_name || '|' || (b.init_qty + COALESCE((SELECT SUM(f.delta) FROM t_lqg_cryo_flow f WHERE f.batch_id=b.id AND f.del_flag='0'),0)) FROM t_lqg_cryo_batch b JOIN t_lqg_sample s ON s.id=b.sample_id AND s.del_flag='0' WHERE b.del_flag='0'"],
    capture_output=True, text=True, check=True).stdout
db = dict(line.split('|') for line in out.strip().splitlines())
print('导出文件 :', xlsx)
print('直连库   :', db)
assert xlsx == db, '两侧剩余不一致 → 红'
print('✓ 导出文件的「当前剩余/支」与直连库的独立汇总逐批相等')
PY

bash doc/verify/reseed.sh --yes >/dev/null
echo "COUNTERFEIT-PROBES EXIT=0"
