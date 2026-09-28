#!/usr/bin/env bash
# AUTH-EXT-003 · accept 1 —— 票面 15 段逐字重放，**只改「外部版渲染会失败」这一个前提**。
#
# 为什么有这份脚本（票面缺陷，见完工报告 §3）：
#   票面 accept 1 第 4 段断言
#     SELECT doc_kind||':'||render_status … sample_id=9000001001 AND audience='external'
#     --col-set "sample_qc:failed,organoid_qc:failed,organoid_score:done"
#   它建立在「seed 里 1001 的 sample_qc / organoid_qc 有假地址的图 → 外部版渲染必然失败」之上
#   （票面 counterfeit 原文）。**这个前提在当前上游不成立**：
#   DOC-RENDER-001 的 `DocOssBytes` 明确「取不到字节的图 → 跳过 + WARN，文档照出 done」
#   （issue #217 裁定①；任务书 §1.1 也这么写），DOC-PUBLISH-001 的完工报告 WARN-3 同样登记了
#   「票面字面（整份 failed）与实际行为（跳过 + done）冲突，本票没有改上游」。
#   实测：1001 的 sample_qc / organoid_qc **外部版渲染成功**（三行全 done，见本脚本第 4 段输出）。
#
#   因此这条断言不是「实现做错了」，而是**票面把一条被上游改掉的行为当成病灶**。
#   本脚本只把「由这个前提推出的 5 处期望」改掉，其余 10 段逐字保留：
#     第 4 段 col-set           failed,failed,done → done,done,done
#     第 5 段 清单集合           2 行 → 4 行（1001 的三份 + 1004 的样本质控表）
#     第 7 段 sampleId=1001      ["organoid_score"] → 三份（顺序 = 样本质控表→类器官质控表→评分表）
#     第 11 段 1001/sample_qc/pages  404 → 200（它确实渲染成功了，就该给）
#     第 14 段 详情 docs         ["organoid_score"] → 三份
#   其余（同组可见 / 异组空集 + 404 / 草稿 1004/organoid_qc 404 / audience=internal 被忽略 /
#   对象键只有 /external/ / 真下载 PDF 且内容是评分表）**逐字未改**。
#
# 跑法（任意 cwd）：bash doc/waves/reports/AUTH-EXT-003/accept-runners/ext003-acc1-premise-fixed.sh
# ★ 与票面正文的另一处差异只有 NF1（去掉 `--fresh-module ruoyi-lqg`，本沙箱 ps 被禁的既有 WARN）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."          # → 项目根
export LQG_VERIFY_ENV_FILE="${LQG_VERIFY_ENV_FILE:-$PWD/.tmp/qa-env/8094/verify.env}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  for K in "9000001001/sample_qc" "9000001001/organoid_qc" "9000001001/organoid_score" "9000001004/sample_qc"; do bash doc/verify/api.sh --as staff POST "/lqg/doc/${K}/render?audience=external" >/dev/null; done &&
  bash doc/verify/api.sh --as staff GET /lqg/sys/ping >/dev/null && sleep 5 &&
  python3 doc/verify/db.py --sql "SELECT doc_kind || ':' || render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND audience='external' AND file_format='docx' AND del_flag='0'" --col-set "sample_qc:done,organoid_qc:done,organoid_score:done" &&
  bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100' | jq -e '([.rows[] | "\(.sampleId|tostring):\(.docKind)"] | sort) == ["9000001001:organoid_qc","9000001001:organoid_score","9000001001:sample_qc","9000001004:sample_qc"] and ([.rows[]|select(.docKind=="organoid_score")|.totalScore]==[85]) and ([.rows[]|keys[]]|unique|index("internalNo")==null)' &&
  bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100' | jq -e '.rows==[]' &&
  bash doc/verify/api.sh --as extB GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '[.rows[].docKind]==["sample_qc","organoid_qc","organoid_score"]' &&
  bash doc/verify/api.sh --as extC GET '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -e '.rows==[]' &&
  bash doc/verify/api.sh --as extC GET /mp/ext/doc/9000001001/organoid_score/pages | jq -e '.code==404' &&
  bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001004/organoid_qc/pages | jq -e '.code==404' &&
  bash doc/verify/api.sh --as extA GET /mp/ext/doc/9000001001/sample_qc/pages | jq -e '.code==200 and .data.status=="done" and (.data.pages|length>0) and ([.data.pages[]|keys[]]|unique)==["pageNo","url"]' &&
  URL="$(bash doc/verify/api.sh --as extA GET '/mp/ext/doc/9000001001/organoid_score/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
  printf '%s' "${URL}" | grep -q '/external/' && ! printf '%s' "${URL}" | grep -q '/internal/' &&
  curl -sSf -o /tmp/lqg-ext-score.pdf "${URL}" && pdftotext /tmp/lqg-ext-score.pdf - | tr -d ' \n' | grep -q '类器官质量评分表' &&
  bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001001 | jq -e '[.data.docs[].docKind]==["sample_qc","organoid_qc","organoid_score"]'
}
run
rc=$?
echo "ACCEPT-1-PREMISE-FIXED EXIT=$rc"
exit $rc
