#!/usr/bin/env bash
# SAMPLE-EXPORT-001 · accept 1（DATA：两张导出与甲方模板原件逐字对表头；行数与同条件列表 total 一致；
#   按钮字段 / 性别 / 加密列逐格钉死；软删样本不导出）。
#
# ticket 的 `run` 是一行长链（`a && b && c`）—— 这里**逐字照抄每一段**，但有两处工程化差异：
#   ① 去掉本 subagent 沙箱跑不了的 `--fresh-module ruoyi-lqg`：api.sh 第 71 行用
#      `ps -o lstart=`，沙箱里 `/bin/ps` 是 "Operation not permitted"，回退的 `date -d`
#      在 macOS 上不存在 → 恒非 0 退出（既有 WARN，8+ 张上游报告记过）。
#      按规矩**没有改 api.sh**（只读区），等价反 stale 证据在本目录
#      `sampleexp001-fresh-evidence.sh` 与完工报告 §4.0。
#   ② 把整条链包进函数判**整条** rc（`set -e` 管不到非末尾失败，直接写一行会静默短路 exit 0）。
#
#   bash doc/waves/reports/SAMPLE-EXPORT-001/accept-runners/sampleexp001-acc1-export.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."

acc1() {
  bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-tissue.xlsx /tmp/lqg-organoid.xlsx /tmp/lqg-tissue-f.xlsx &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-tissue.xlsx POST /lqg/sample/export/tissue &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-tissue.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --rows 8 --find "内部编号=T-hli01" --expect "来源单位=A 医院,供体姓名=测试供体甲,性别=男,年龄=56,住院号=ZY0000001,组织类型=肝组织,有无固定=有,质控表=有,细胞活率报告=有,操作人=李工" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-tissue.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND sample_kind='tissue'" | head -1)" &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-organoid.xlsx POST /lqg/sample/export/organoid &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-organoid.xlsx --template "_input/templates/类器官收样记录模板.xlsx" --rows 1 --find "内部编号=T-oco01" --expect "来源单位=B 大学,类器官类型=结直肠类器官,细胞活率报告=有" &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-tissue-f.xlsx POST '/lqg/sample/export/tissue?sourceUnitId=9000009002' &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-tissue-f.xlsx --template "_input/templates/样本记录信息表模板.xlsx" --rows "$(bash doc/verify/api.sh --as staff GET '/lqg/sample/list?sourceUnitId=9000009002&sampleKind=tissue&pageSize=100' | jq '.total')" &&
  bash doc/verify/api.sh --as extA --bizcode POST /lqg/sample/export/tissue | grep -qE '^403'
}
acc1 2>&1
rc=$?
if [ "${rc}" -eq 0 ]; then
  echo "ACCEPT-1 EXIT=0"
else
  echo "ACCEPT-1 EXIT=${rc}  ← 失败（上面最后一段的输出就是断点）"
fi
exit "${rc}"
