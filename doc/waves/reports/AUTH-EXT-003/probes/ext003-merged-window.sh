#!/usr/bin/env bash
# AUTH-EXT-003 · 附加上探针：**「新完成一份文档」之后、合并件重出之前**的那段窗口里，
# 旧合并件会不会被当成「可用」发给送检方。
#
# 背景：`DocPublishService.publish` 只把渲染排进线程池（`scheduleRender`），**不同步**
# `invalidateMerged`。合并件是四个渲染任务里的最后一个排队项，所以窗口 ≈ 三次渲染的耗时。
# 本票的可用性判据锚在**产物内部一致性**上（当前指纹下有页图 + PDF 与 header 同版），
# 旧合并件在窗口内这三条全成立 → 会被放行。这一段是**量化**它，供主会话决定要不要在
# `DocPublishService.publish` 里补一次同步 `invalidateMerged`（一行，见完工报告 WARN-4）。
#
# 跑法：bash doc/waves/reports/AUTH-EXT-003/probes/ext003-merged-window.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."
export LQG_VERIFY_ENV_FILE="${LQG_VERIFY_ENV_FILE:-$PWD/.tmp/qa-env/8094/verify.env}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
hdr() { python3 doc/verify/db.py --quiet --sql "SELECT content_hash FROM t_lqg_doc_file WHERE sample_id=9000001004 AND doc_kind='merged' AND audience='external' AND file_format='docx' AND del_flag='0'"; }
hasmerged() { bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001004' | jq -c '[.rows[].docKind]|index("merged")!=null'; }

bash doc/verify/reseed.sh --yes >/dev/null
# 1004：样本质控表 published，类器官质控表 draft。先把「只有 1 个成员」的合并件渲染出来。
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001004/sample_qc/render?audience=external' >/dev/null
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001004/merged/render?audience=external' >/dev/null
H0="$(hdr)"
echo "窗口前：merged 在清单里 = $(hasmerged)   header 指纹 = ${H0:0:12}"

# 完成并同步第二份（类器官质控表）→ 成员从 1 变 2。它返回时 merged 还是旧的那一份。
T0="$(date +%s)"
bash doc/verify/api.sh --as staff POST '/lqg/qc/9000001004/organoid-qc/publish' >/dev/null
echo "publish 返回后立刻：merged 在清单里 = $(hasmerged)   指纹 = $(hdr | cut -c1-12)（仍 == 窗口前的 ${H0:0:12}）"

# 轮询到合并件重出（指纹变化）
WINDOW=""
for i in $(seq 1 240); do
  H="$(hdr)"
  if [ "${H}" != "${H0}" ] && [ -n "${H}" ]; then
    WINDOW=$(( $(date +%s) - T0 ))
    echo "合并件重出：第 ${i} 次轮询（约 ${WINDOW}s）指纹 = ${H:0:12}"
    break
  fi
  sleep 0.5
done
# 等外部版合并件真的 done 并可下载（渲染是 docx→pdf→png 三步）
for i in $(seq 1 120); do
  if [ "$(hasmerged)" = "true" ]; then break; fi
  sleep 1
done
echo "收敛后：merged 在清单里 = $(hasmerged)"
echo "★ 结论：publish 返回到合并件重出之间约 ${WINDOW:-未测到}s，这段窗口内送检方拿到的是「少了刚完成那一份」的旧合并件"
bash doc/verify/reseed.sh --yes >/dev/null
