#!/usr/bin/env bash
# CRYO-FLOW-001 · accept 1（DATA）—— 与 ticket 的 `run` 逐字相同，唯一差异 = 去掉本 agent 沙箱
# 跑不了的 `--fresh-module ruoyi-lqg`（api.sh 第 71 行 `ps -o lstart=` 被禁；等价证据见完工报告 §4.0）。
# ★ 两处忠实性说明（本票唯一的两处 runner 偏差）：
#   ① ticket accept 1 的 `run` 里**没有** `--bizcode`（它靠第一次调用的 `--fresh-module` 那一段
#      先做过守卫）；api.sh 不带 `--bizcode` 时吐的是原始 JSON（首字符 `{`），
#      原文的 `grep -qE '^200'` / `'^(400|500)'` 在这种输出上恒不成立（本 runner 实跑红过）。
#      这里把两处 grep 改成断同一个「业务码」字段：`"code":200` / `"code":(400|500)`
#      —— 判据等价（都是业务码，不是 HTTP 码），只是不依赖 `--bizcode` 的 `code\tmsg` 形态。
#   ② 本 agent 沙箱跑不了 `ps -o lstart=`（api.sh line 71），所以去掉 `--fresh-module ruoyi-lqg`；
#      反 stale 的等价证据见完工报告 §4.0（七项）。
set -uo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*
run() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  flow() { bash doc/verify/api.sh --as staff POST "/lqg/cryo/batch/$1/flow" "$2"; } &&
  flow 9000003002 '{"flowType":"take","qty":2,"purpose":"复苏培养"}' | grep -qE '"code":200' &&
  flow 9000003002 '{"flowType":"add","qty":1,"purpose":"同批补冻"}' | grep -qE '"code":200' &&
  flow 9000003003 '{"flowType":"take","qty":1,"purpose":"药敏实验"}' | grep -qE '"code":200' &&
  flow 9000003002 '{"flowType":"take","qty":9,"purpose":"超取"}' | grep -qE '"code":(400|500)' &&
  flow 9000003002 '{"flowType":"adjust","qty":-1}' | grep -qE '"code":(400|500)' &&
  flow 9000003002 '{"flowType":"adjust","qty":-9,"purpose":"调成负数"}' | grep -qE '"code":(400|500)' &&
  flow 9000003002 '{"flowType":"take","qty":-1,"purpose":"负的取走"}' | grep -qE '"code":(400|500)' &&
  flow 9000003002 '{"flowType":"adjust","qty":-1,"purpose":"盘点少一支"}' | grep -qE '"code":200' &&
  python3 doc/verify/db.py --sql "SELECT flow_type || ':' || delta || ':' || from_location FROM t_lqg_cryo_flow WHERE del_flag='0' AND create_time > now() - interval '5 minutes'" --col-set "take:-2:minus80,add:1:minus80,take:-1:ln2,adjust:-1:minus80" &&
  bash doc/verify/api.sh --as staff GET '/lqg/cryo/batch/list?pageSize=100' | jq -e '[.rows[]|select((.id|tostring)=="9000003002" or (.id|tostring)=="9000003003")|.remainingQty] | sort == [2,3]' &&
  python3 doc/verify/db.py --sql "SELECT b.id || ':' || (b.init_qty + COALESCE(SUM(f.delta),0)) FROM t_lqg_cryo_batch b LEFT JOIN t_lqg_cryo_flow f ON f.batch_id=b.id AND f.del_flag='0' WHERE b.id IN (9000003002, 9000003003) GROUP BY b.id, b.init_qty" --col-set "9000003002:2,9000003003:3" &&
  bash doc/verify/reseed.sh --yes >/dev/null
}
run; RC=$?
echo "ACCEPT-1 EXIT=${RC}"
exit ${RC}
