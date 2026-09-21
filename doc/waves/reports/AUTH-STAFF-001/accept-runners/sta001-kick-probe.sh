#!/usr/bin/env bash
# AUTH-STAFF-001 · 「撤销必须立刻踢掉旧 token」的**对抗性复现**（accept 第 1 条倒数第 4 段那条断言的压力版）。
#
# 为什么要单独跑：accept 原文的顺序恰好让上游 roleService.cleanOnlineUser 的快照是新鲜的，断言恒绿；
# 但只要在撤销前 5 秒内**先触发一次 cleanOnlineUser**（PlusSaTokenDao.searchData 的 Caffeine
# expireAfterWrite=5s），被撤销人**之后**签发的 token 就不在那份快照里 —— 旧 token 撤完照样 200。
# 本脚本刻意构造那个时序：
#   ① 撤销一个一次性内部账号 → 触发 cleanOnlineUser → 把 `Authorization:**` 的搜索快照坐实
#   ② 立刻（<5s）登录 extF（新 token 不进快照）→ 授权成内部 → 撤销
#   ③ 用 extF 的旧 token 打 /mp/me：必须 401（修好前这里是 200）
set -e
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # 回到工作区根（本脚本随报告落盘，不在 .tmp/ 下）

kick_probe() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  # ① 造一个一次性内部账号并撤销它 —— 只为触发一次 cleanOnlineUser（坐实搜索快照）
  TMPID="$(bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000088","name":"踢人探针","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -r '.data.userId')" &&
  bash doc/verify/api.sh --as admin DELETE "/lqg/auth/staff/${TMPID}" | jq -e '.code==200' >/dev/null &&
  # ② 立刻登录 extF（新 token）、升级、撤销
  rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-extF-* &&
  bash doc/verify/api.sh --as extF GET /mp/me | jq -e '.data.identity=="external"' >/dev/null &&
  bash doc/verify/api.sh --as admin POST /lqg/auth/staff '{"phone":"13800000016","name":"吴同学","roleKey":"lqg_internal","password":"Lqg@test123"}' | jq -e '.data.upgraded==true' >/dev/null &&
  bash doc/verify/api.sh --as admin DELETE /lqg/auth/staff/9000000116 | jq -e '.code==200' >/dev/null &&
  # ③ 旧 token 必须已经失效
  OUT="$(bash doc/verify/api.sh --as extF --bizcode GET /mp/me)"
  echo "撤销后用旧 token 打 /mp/me 的业务码/消息: ${OUT}"
  printf '%s' "${OUT}" | grep -qE '^401'
}
if kick_probe; then :; else echo "KICK-PROBE FAILED rc=$?"; exit 1; fi