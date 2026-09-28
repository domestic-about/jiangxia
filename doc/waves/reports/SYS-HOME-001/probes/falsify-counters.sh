#!/usr/bin/env bash
# SYS-HOME-001 的**反证探针**（counterfeit 逐条证伪）—— 不是 accept，是 accept 的补充：
# accept 里那几条「故意的错法会不会红」在 seed 上不一定真能红（例如 seed 里没有 unbound 档案），
# 所以这里**手工把错法的前提造出来**，再看接口的数字有没有跟着错。
#
#   前置：后端 8094（qa-up 起的 dev profile）+ 库 lqg_dev(5433)
#   跑法：bash doc/waves/reports/SYS-HOME-001/probes/falsify-counters.sh | tee evidence/falsify-counters.txt
#
# 会写库（psql UPDATE/INSERT）并在结束时 reseed 回快照 —— 只在 dev 库上跑。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."
ROOT="$PWD"
export LQG_VERIFY_ENV_FILE="${ROOT}/.tmp/qa-env/8094/verify.env"
export PGPASSWORD=lqg_dev_pwd
PSQL=(psql -q -h 127.0.0.1 -p 5433 -U lqg -d lqg_dev -tA)
sql() { "${PSQL[@]}" -c "$1"; }
todo() { bash doc/verify/api.sh --as staff GET /lqg/home/todo; }
num() { jq -r ".data.$1"; }
header() { printf '\n════ %s\n' "$1"; }

gotenberg_up() { docker start lqg-dev-gotenberg >/dev/null 2>&1 || true; }
trap gotenberg_up EXIT

header "0. 回到确定性快照"
bash doc/verify/reseed.sh --yes >/dev/null && echo "reseed ok"

# ─────────────────────────────────────────────────────────────────────────────
header "A. counterfeit「待核验用户数把 unbound 也算进去」—— seed 里没有 unbound，先手工造一条"
echo "改之前：$(todo)"
sql "UPDATE t_lqg_ext_profile SET bind_status='unbound' WHERE id=9000000311" >/dev/null
echo "库里 bind_status 分布：$(sql "SELECT bind_status||'='||count(*) FROM t_lqg_ext_profile WHERE del_flag='0' GROUP BY bind_status ORDER BY bind_status" | paste -sd' ' -)"
echo "pending 计数       = $(sql "SELECT count(*) FROM t_lqg_ext_profile WHERE del_flag='0' AND bind_status='pending'")"
echo "pending+unbound    = $(sql "SELECT count(*) FROM t_lqg_ext_profile WHERE del_flag='0' AND bind_status IN ('unbound','pending')")"
AFTER_UNBOUND="$(todo)"
echo "改之后：${AFTER_UNBOUND}"
echo "→ pendingExtUsers = $(printf '%s' "${AFTER_UNBOUND}" | num pendingExtUsers)（必须仍是 2；把 unbound 算进去的实现会给 3）"
bash doc/verify/reseed.sh --yes >/dev/null

# ─────────────────────────────────────────────────────────────────────────────
header "B. counterfeit「渲染失败数写死成 0」+「按行数数而不是组数」"
echo "停掉 gotenberg（造一次**真实**失败），跑完会起回来"
docker stop lqg-dev-gotenberg >/dev/null
bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/sample_qc/render?audience=internal' | jq -c '{status:.data.status, errorMsg:.data.errorMsg}'
sleep 3
echo "库里失败「行数」       = $(sql "SELECT count(*) FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed'")"
echo "库里失败「组数」       = $(sql "SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed') x")"
FAIL1="$(todo)"
echo "接口（真实失败刚发生）：${FAIL1}"
echo "→ renderFailed = $(printf '%s' "${FAIL1}" | num renderFailed)（写死 0 的实现这里会是 0 → accept 第 43 段红）"

echo "再手工补一行**同一组**的失败产物（pdf），模拟「一次失败写多行」（DocRenderService 的 catch 会 markFailed 两次）"
sql "INSERT INTO t_lqg_doc_file (id, sample_id, doc_kind, audience, file_format, page_no, content_hash, template_version, render_status, error_msg, del_flag) SELECT 9900000001, sample_id, doc_kind, audience, 'pdf', 0, content_hash, template_version, 'failed', error_msg, '0' FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed' AND sample_id=9000001001" >/dev/null
echo "库里失败「行数」       = $(sql "SELECT count(*) FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed'")"
echo "库里失败「组数」       = $(sql "SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE del_flag='0' AND render_status='failed') x")"
FAIL2="$(todo)"
echo "接口：${FAIL2}"
echo "→ renderFailed = $(printf '%s' "${FAIL2}" | num renderFailed)（按行数数的实现这里会是 2 → accept 第 43 段红）"
docker start lqg-dev-gotenberg >/dev/null && sleep 5
echo "gotenberg /health = $(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:3010/health)"
bash doc/verify/reseed.sh --yes >/dev/null

# ─────────────────────────────────────────────────────────────────────────────
header "C. counterfeit「待核验样本只数组织」—— 外部交一条**类器官**收样"
echo "交之前：$(todo)"
bash doc/verify/api.sh --as extC POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"胃类器官"}' | jq -c '{code}'
bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}' | jq -c '{code}'
AFTER="$(todo)"
echo "交之后：${AFTER}"
echo "库里 pending 样本：全部=$(sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending'")  其中类器官=$(sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending' AND sample_kind='organoid'")  其中组织=$(sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0' AND verify_status='pending' AND sample_kind='tissue'")"
echo "→ pendingSamples = $(printf '%s' "${AFTER}" | num pendingSamples)（只数组织的实现会给 2）pendingEmbeds = $(printf '%s' "${AFTER}" | num pendingEmbeds)"

# ─────────────────────────────────────────────────────────────────────────────
header "D. counterfeit「最近提交没过滤软删」"
echo "库里未删样本 = $(sql "SELECT count(*) FROM t_lqg_sample WHERE del_flag='0'")  含软删 = $(sql "SELECT count(*) FROM t_lqg_sample")"
echo "软删那行的送检单号 = $(sql "SELECT submit_no FROM t_lqg_sample WHERE del_flag='1'")"
RECENT="$(bash doc/verify/api.sh --as staff GET /lqg/home/recent)"
echo "接口行数 = $(printf '%s' "${RECENT}" | jq '.data|length')；含 SJ90000010 = $(printf '%s' "${RECENT}" | jq '[.data[].submitNo]|index("SJ90000010")!=null')"

header "E. counterfeit「工作台与冻存清单各写各的超期 where」"
HOME_CRYO="$(printf '%s' "$(todo)" | num cryoOverdue)"
LIST_LEN="$(bash doc/verify/api.sh --as staff GET /lqg/cryo/overdue | jq '.data|length')"
echo "首页 cryoOverdue=${HOME_CRYO}  超期清单长度=${LIST_LEN}"

header "收尾：reseed 回快照"
bash doc/verify/reseed.sh --yes >/dev/null && echo "reseed ok"
echo "最终 todo：$(todo)"
