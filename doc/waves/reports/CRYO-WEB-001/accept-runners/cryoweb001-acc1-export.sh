#!/usr/bin/env bash
# CRYO-WEB-001 · accept 1（DATA）—— 与 ticket 的 `run` 逐字相同，唯一差异：
#   去掉本 agent 沙箱恒非 0 的 `--fresh-module ruoyi-lqg`（`api.sh` 第 71 行 `ps -o lstart=`
#   被禁 + macOS 没有 `date -d` 回退 → 该守卫恒非 0 退出，issue #151；按规矩**没有改 api.sh**）。
#   等价反 stale 证据见 accept-runners/cryoweb001-freshness.sh 与完工报告 §4.0。
# 长链包进函数判**整条** rc（别丢给 set -e：非末尾位置失败会静默短路成 exit 0）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-cryo.xlsx &&
  bash doc/verify/api.sh --as staff --out /tmp/lqg-cryo.xlsx POST /lqg/cryo/batch/export &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --rows 7 --find "冻存样品=T-hli01-GZ-N-P2-EM2-2e5" --expect "冻存数量/支=8,冻存密度=2e5,暂存-80度超低温冰箱=是,冻存人=李工,代数=P2,当前剩余/支=6" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --find "冻存样品=T-oco01-JC-T-P4-EM1-5e5" --expect "暂存-80度超低温冰箱=否,液氮储存位置=1号罐-1架-A2,当前剩余/支=5" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --find "冻存样品=T-hli01-GZ-N-P5-EM2-1e5" --expect "冻存数量/支=3,当前剩余/支=0" &&
  python3 doc/verify/xlsx_header.py --file /tmp/lqg-cryo.xlsx --template "_input/templates/-80冻存模板.xlsx" --extra "代数,当前剩余/支" --rows "$(python3 doc/verify/db.py --quiet --sql "SELECT count(*) FROM t_lqg_cryo_batch b JOIN t_lqg_sample s ON s.id = b.sample_id AND s.del_flag='0' WHERE b.del_flag='0'" | head -1)"
}
run; RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit ${RC}
