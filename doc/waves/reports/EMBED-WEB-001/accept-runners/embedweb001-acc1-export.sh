#!/usr/bin/env bash
# EMBED-WEB-001 · accept 1（DATA · 按模板导出 Excel）
#
# 与 ticket 的 `run` 逐字相同，唯一差异 = 去掉本 agent 沙箱跑不了的 `--fresh-module ruoyi-lqg`
# （api.sh 第 71 行用 `ps -o lstart=`，subagent 沙箱里 ps 被禁 → 恒 exit 2；等价证据见完工报告 §4.0）。
# 长链包进函数判**整条** rc（不丢给 set -e：非末尾失败会被静默短路）。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
cd "$ROOT"

run_accept1() {
  bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-embed.xlsx /tmp/lqg-embed-f.xlsx &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-embed.xlsx POST /lqg/embed/export &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows 5 --find "石蜡块编号=T-E01-1" --expect "样本编号=T-hli01,染色=HE染色、IHC染色,mark的表达情况=Ki67：强表达；CK19：阴性,包埋人=李工" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --find "石蜡块编号=T-E04-1" --expect "染色=其他（Masson）,mark的表达情况=弱表达" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --find "石蜡块编号=T-E02-1" --expect "染色=无染色,mark的表达情况=" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_embed e JOIN t_lqg_sample s ON s.id = e.sample_id AND s.del_flag='0' WHERE e.del_flag='0'" | head -1)" &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-embed-f.xlsx POST '/lqg/embed/export?internalNo=T-hli01' &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed-f.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows 2 &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-embed-f.xlsx POST '/lqg/embed/export?verifyStatus=valid' &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-embed-f.xlsx --template "_input/templates/石蜡包埋送样记录模板.xlsx" --rows "$(bash doc/verify/api.sh --as staff GET '/lqg/embed/list?verifyStatus=valid&pageSize=100' | jq '.total')"
}
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-* 2>/dev/null || true
run_accept1
echo "ACCEPT-1 EXIT=$?"
