#!/usr/bin/env bash
# AUTH-EXT-003 · 对抗性探针 —— 每条自带「副作用 / 库内真值」断言，不看接口自报。
#
# 跑法（任意 cwd）：bash doc/waves/reports/AUTH-EXT-003/probes/ext003-probes.sh
# 前后各 reseed 一次（探针会改库：改渲染状态、撤回 / 重发文档）。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."
export LQG_VERIFY_ENV_FILE="${LQG_VERIFY_ENV_FILE:-$PWD/.tmp/qa-env/8094/verify.env}"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
PASS=0; FAIL=0
ok()   { if [ "$2" = "$3" ]; then echo "PASS $1 — $2"; PASS=$((PASS+1)); else echo "FAIL $1 — got[$2] want[$3]"; FAIL=$((FAIL+1)); fi; }
code() { bash doc/verify/api.sh --as "$1" --bizcode "$2" "$3" ${4:+"$4"} | head -1 | cut -f1; }
json() { bash doc/verify/api.sh --as "$1" GET "$2"; }
PSQL="psql -q -h 127.0.0.1 -p 5433 -U lqg -d lqg_dev"
dbset() { PGPASSWORD=lqg_dev_pwd ${PSQL} -c "$1" >/dev/null; }

bash doc/verify/reseed.sh --yes >/dev/null

# ── 准备：把 1001 的三份 + 1004 的样本质控表渲染成外部版 ────────────────────
for K in "9000001001/sample_qc" "9000001001/organoid_qc" "9000001001/organoid_score" "9000001004/sample_qc"; do
  bash doc/verify/api.sh --as staff POST "/lqg/doc/${K}/render?audience=external" >/dev/null
done

# ── 角色闸（三个方向）────────────────────────────────────────────────────────
ok "P1-staff打外部文档清单-403"  "$(code staff GET /mp/ext/doc/list)" "403"
ok "P2-admin打外部文档清单-403"  "$(code admin GET /mp/ext/doc/list)" "403"
ok "P3-extA打内部渲染接口-403"   "$(code extA GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal')" "403"
ok "P4-extB打内部预览-403"       "$(code extB GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal')" "403"

# ── 没可见样本的身份 ─────────────────────────────────────────────────────────
ok "P5-extE未核验-列表空"  "$(json extE '/mp/ext/doc/list?pageSize=100' | jq -c '.rows')" "[]"
ok "P6-extF待核验-列表空"  "$(json extF '/mp/ext/doc/list?pageSize=100' | jq -c '.rows')" "[]"
ok "P7-extE取1001文档-404" "$(json extE /mp/ext/doc/9000001001/organoid_score/pages | jq -r '.code')" "404"

# ── 软删样本（1010）任何入口都不出现 ─────────────────────────────────────────
ok "P8-软删1010不在清单" \
  "$(json extA '/mp/ext/doc/list?pageSize=100' | jq -c '[.rows[].sampleId|tostring]|unique|index("9000001010")')" "null"
ok "P9-软删1010预览-404" "$(json extA /mp/ext/doc/9000001010/sample_qc/pages | jq -r '.code')" "404"

# ── 参数校验 ─────────────────────────────────────────────────────────────────
ok "P10-不认识的docKind-400" "$(code extA GET /mp/ext/doc/9000001001/bogus/pages)" "400"
ok "P11-不认识的docKind-列表空" "$(json extA '/mp/ext/doc/list?pageSize=100&docKind=bogus' | jq -c '.rows')" "[]"
ok "P12-不认识的format-400" "$(code extA GET '/mp/ext/doc/9000001001/organoid_score/download?format=xlsx')" "400"
ok "P13-不存在的样本id-404" "$(json extA /mp/ext/doc/9000009999/sample_qc/pages | jq -r '.code')" "404"
ok "P14-不存在的样本id-列表空" "$(json extA '/mp/ext/doc/list?pageSize=100&sampleId=9000009999' | jq -c '.rows')" "[]"

# ── 草稿不给（1004 的类器官质控表是草稿；1005 的样本质控表是草稿）─────────────
ok "P15-1004草稿预览-404"  "$(json extB /mp/ext/doc/9000001004/organoid_qc/pages | jq -r '.code')" "404"
ok "P16-1004草稿下载-404"  "$(json extB '/mp/ext/doc/9000001004/organoid_qc/download?format=docx' | jq -r '.code')" "404"
ok "P17-1005草稿(extC)预览-404" "$(json extC /mp/ext/doc/9000001005/sample_qc/pages | jq -r '.code')" "404"
ok "P18-1005草稿(extC)列表空" "$(json extC '/mp/ext/doc/list?pageSize=100' | jq -c '.rows')" "[]"
ok "P19-库内1004档确实是草稿" \
  "$(python3 doc/verify/db.py --quiet --sql "SELECT doc_status FROM t_lqg_qc_organoid WHERE sample_id=9000001004 AND del_flag='0'")" "draft"

# ── ★ 外部版渲染没成功 → 一律不给（票面 counterfeit 第 1 条的真形态）─────────
#   1001/organoid_score 外部版本来是 done；人为置 failed（模拟「渲染没成」），
#   清单 / 预览 / 下载三处必须同时消失 —— 「只按 doc_status 已完成过滤」会在这里红。
dbset "UPDATE t_lqg_doc_file SET render_status='failed' WHERE sample_id=9000001001 AND doc_kind='organoid_score' AND audience='external' AND file_format='docx' AND del_flag='0'"
ok "P20-外部版failed-清单少这一行" \
  "$(json extB '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]|index("organoid_score")')" "null"
ok "P21-外部版failed-预览404" "$(json extB /mp/ext/doc/9000001001/organoid_score/pages | jq -r '.code')" "404"
ok "P22-外部版failed-下载404" "$(json extB '/mp/ext/doc/9000001001/organoid_score/download?format=pdf' | jq -r '.code')" "404"
ok "P23-库内同一行确实failed" \
  "$(python3 doc/verify/db.py --quiet --sql "SELECT render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND doc_kind='organoid_score' AND audience='external' AND file_format='docx' AND del_flag='0'")" "failed"
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_score/render?audience=external' >/dev/null
ok "P24-重新渲染后清单恢复" \
  "$(json extB '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]|index("organoid_score")')" "2"

# ── ★ 合并件：成员集合一变就立刻不可见（指纹门槛，DOC-PUBLISH-001 的交接）─────
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/merged/render?audience=external' >/dev/null
ok "P25-合并件渲染后进清单" \
  "$(json extB '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]|index("merged")!=null')" "true"
ok "P26-合并件库内是done" \
  "$(python3 doc/verify/db.py --quiet --sql "SELECT render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND doc_kind='merged' AND audience='external' AND file_format='docx' AND del_flag='0'")" "done"
bash doc/verify/api.sh --as staff POST '/lqg/qc/9000001001/sample-qc/unpublish' >/dev/null
ok "P27-撤回一份后该份消失" \
  "$(json extB '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]|index("sample_qc")')" "null"
ok "P28-★撤回一份后合并件立刻消失(不靠异步invalidate)" \
  "$(json extB '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]|index("merged")')" "null"
ok "P29-★过期合并件预览404" "$(json extB /mp/ext/doc/9000001001/merged/pages | jq -r '.code')" "404"
ok "P30-★过期合并件下载404" "$(json extB '/mp/ext/doc/9000001001/merged/download?format=docx' | jq -r '.code')" "404"
ok "P31-库内合并件仍是done(只被标记过期)" \
  "$(python3 doc/verify/db.py --quiet --sql "SELECT render_status FROM t_lqg_doc_file WHERE sample_id=9000001001 AND doc_kind='merged' AND audience='external' AND file_format='docx' AND del_flag='0'")" "done"
bash doc/verify/api.sh --as staff POST '/lqg/qc/9000001001/sample-qc/publish' >/dev/null
MER=0
for i in $(seq 1 40); do
  if [ "$(json extB '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]|index("merged")!=null')" = "true" ]; then MER=1; break; fi
  sleep 3
done
ok "P32-重新完成后合并件回来" "$MER" "1"

# ── 时间范围筛选（只给日期时 End 含当天）─────────────────────────────────────
D="$(python3 doc/verify/db.py --quiet --sql "SELECT to_char(published_time,'YYYY-MM-DD') FROM t_lqg_qc_score WHERE sample_id=9000001001 AND del_flag='0'")"
ok "P33-publishedEnd=当天含当天" \
  "$(json extB "/mp/ext/doc/list?pageSize=100&sampleId=9000001001&publishedEnd=${D}" | jq -c '[.rows[].docKind]|index("organoid_score")!=null')" "true"
ok "P34-publishedEnd=前一天不含当天" \
  "$(json extB "/mp/ext/doc/list?pageSize=100&sampleId=9000001001&publishedEnd=$(date -j -v-1d -f '%Y-%m-%d' "${D}" '+%Y-%m-%d' 2>/dev/null || date -d "${D} -1 day" '+%Y-%m-%d')" | jq -c '[.rows[].docKind]|index("organoid_score")')" "null"
ok "P35-时间格式写错-400" "$(code extB GET '/mp/ext/doc/list?publishedBegin=not-a-date')" "400"

# ── 键集合 / 白名单（清单与详情两处）─────────────────────────────────────────
ok "P36-清单行没有内部键" \
  "$(json extB '/mp/ext/doc/list?pageSize=100' | jq -c '([.rows[]|keys[]]|unique) - ["docKind","donorNameMasked","publishedTime","sampleId","submitNo","totalScore"]')" "[]"
ok "P37-totalScore只在评分表行" \
  "$(json extB '/mp/ext/doc/list?pageSize=100' | jq -c '[.rows[]|select(.docKind!="organoid_score")|has("totalScore")]|unique')" "[false]"
ok "P38-详情docs与清单同源" \
  "$(json extA /mp/ext/sample/9000001001 | jq -c '[.data.docs[].docKind]')" \
  "$(json extA '/mp/ext/doc/list?pageSize=100&sampleId=9000001001' | jq -c '[.rows[].docKind]')"

# ── ★ audience 不是入参：预览 / 下载两条路都只给 external 那一份 ─────────────
PURL="$(json extA '/mp/ext/doc/9000001001/organoid_score/pages?audience=internal' | jq -r '.data.pages[0].url')"
ok "P39-pages带audience=internal仍只出/external/" \
  "$(printf '%s' "${PURL}" | grep -c '/external/' )/$(printf '%s' "${PURL}" | grep -c '/internal/')" "1/0"
DURL="$(json extA '/mp/ext/doc/9000001001/organoid_score/download?format=docx&audience=internal' | jq -r '.data.url')"
ok "P40-download带audience=internal仍只出/external/" \
  "$(printf '%s' "${DURL}" | grep -c '/external/' )/$(printf '%s' "${DURL}" | grep -c '/internal/')" "1/0"
ok "P41-文件名用送检单号不用内部编号" \
  "$(json extA '/mp/ext/doc/9000001001/organoid_score/download?format=docx' | jq -r '.data.fileName')" "类器官质量评分表-SJ90000001.docx"
ok "P42-样本内部编号开了也不进文档接口" \
  "$(python3 doc/verify/db.py --quiet --sql "SELECT DISTINCT file_name FROM sys_oss WHERE file_name LIKE 'lqg/doc/%/external/%' AND file_name LIKE '%organoid_score%'" | head -1 | grep -c '/external/')" "1"

# ── ★ 真下载：字节级下下来、抽文字 ───────────────────────────────────────────
curl -sSf -o /tmp/lqg-p41.docx "$(json extA '/mp/ext/doc/9000001001/organoid_score/download?format=docx' | jq -r '.data.url')"
ok "P43-docx真下载且是zip(OOXML)" "$(head -c2 /tmp/lqg-p41.docx | xxd -p)" "504b"
curl -sSf -o /tmp/lqg-p43.pdf "$(json extA '/mp/ext/doc/9000001001/organoid_score/download?format=pdf' | jq -r '.data.url')"
ok "P44-pdf真下载且含表名" "$(pdftotext /tmp/lqg-p43.pdf - | tr -d ' \n' | grep -c '类器官质量评分表')" "1"
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_score/render?audience=internal' >/dev/null
# 内部版与外部版是**两份独立产物**（不是下载时抹）：两条链各自签到自己那一段键
IURL="$(json staff '/lqg/doc/9000001001/organoid_score/download?format=docx&audience=internal' | jq -r '.data.url')"
EURL="$(json extA '/mp/ext/doc/9000001001/organoid_score/download?format=docx' | jq -r '.data.url')"
ok "P45-内部版下载键含/internal/" "$(printf '%s' "${IURL}" | grep -c '/internal/')" "1"
ok "P46-内部版与外部版不是同一个对象" "$([ "${IURL%%\?*}" != "${EURL%%\?*}" ] && echo diff || echo same)" "diff"

bash doc/verify/reseed.sh --yes >/dev/null
echo "PROBE EXT003 PASS=${PASS} FAIL=${FAIL}"
[ "${FAIL}" = 0 ]
