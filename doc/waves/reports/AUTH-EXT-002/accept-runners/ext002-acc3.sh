#!/usr/bin/env bash
# AUTH-EXT-002 · accept 3（API）—— 小程序外部详情页接上包埋卡片（含待核验送样）、
#   卡片有操作人与包埋人、没有冻存与核验人；内部编号那一行随后端给不给键渲染。
#
# 跑法（任意 cwd，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc3.sh
#
# ★ 与 ticket 正文 accept 3 的 `run` **逐字相同**（这条链没有 `--fresh-module`、也没有 Maven 行）。
#   长链包进函数判整条 rc。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根

run_accept_3() {
  # 子 shell 包住 `cd`：ticket 正文是从项目根 `cd code/miniapp` 起的一段链，
  # 这里跑完要让 cwd 回到项目根，后面几行诊断路径才不用改（断言内容一字未动）。
  (cd code/miniapp && rm -rf dist/build/mp-weixin && pnpm build:mp-weixin >/dev/null && test -f dist/build/mp-weixin/pages/sample/detail-ext.js &&
  grep -q "EmbedCard" src/pages/sample/detail-ext.vue && grep -q "@/components/lqg/EmbedCard.vue" src/pages/sample/detail-ext.vue &&
  grep -q 'verifyStatus' src/components/lqg/EmbedCard.vue &&
  grep -q 'embedBy' src/components/lqg/EmbedCard.vue && grep -q 'operatorName' src/components/lqg/EmbedCard.vue &&
  ! grep -nE 'internalNo|verifyBy|frozenBy|cryo' src/components/lqg/EmbedCard.vue &&
  grep -qE 'v-if="[^"]*(internalNo|showInternalNo)' src/pages/sample/detail-ext.vue &&
  ! grep -nE 'verifyBy|frozenBy|cryo' src/pages/sample/detail-ext.vue)
}

echo "== accept 3 · 小程序外部详情页包埋卡片 =="
run_accept_3
RC=$?
echo "-- 产物 --"
ls -l code/miniapp/dist/build/mp-weixin/pages/sample/detail-ext.js code/miniapp/dist/build/mp-weixin/components/lqg/EmbedCard.js 2>&1
echo "-- detail-ext 的 usingComponents（embed-card 必须在内）--"
jq -c '.usingComponents' code/miniapp/dist/build/mp-weixin/pages/sample/detail-ext.json 2>/dev/null
echo "ACCEPT-3 EXIT=${RC}"
exit "${RC}"
