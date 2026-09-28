#!/usr/bin/env bash
# 登录页快捷入口的**账号可用性**判据（2026-09-28 加）
#
# 起因（Kevin 报的 bug）：我把工作台的**管理员**账号（lqgadmin / 角色 lqg_admin）加进了小程序的
# 测试身份面板。管理员没有小程序内部接口要的 `lqg_internal` 角色，而 `/mp/me` 仍把管理员判成
# identity=internal → 小程序以为自己是内部身份、去调 `/mp/int/**` → **处处 403**
# （首页待办、文档清单、样本清单全挂），界面上却还显示「内部人员」徽标。极难自查。
#
# 这条判据把「面板里每个账号真的能用」变成机器可判定：逐个账号登录后，
#   ① `/mp/me` 回的 identity 必须与面板声明的 expectIdentity 一致
#   ② **与自己身份相符**的那一侧清单接口必须 200
#   ③ 另一侧必须 403（证明角色闸真的在拦，不是「什么都放行」）
# 这样「面板列了一个用不了的账号」或「角色配错」都会当场红。
#
# 跑法：
#   # 本地
#   LQG_VERIFY_ENV_FILE=$PWD/.tmp/qa-env/8091/verify.env bash doc/waves/regression/staging-hardening/12-panel-accounts-usable.sh
#   # 测试环境
#   LQG_VERIFY_ENV_FILE=doc/verify/verify.test.env bash doc/waves/regression/staging-hardening/12-panel-accounts-usable.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "${ROOT}"
SEEDS="code/miniapp/src/api/mock-seeds.ts"
[ -f "${SEEDS}" ] || { echo "[error] 找不到 ${SEEDS}" >&2; exit 2; }

echo "目标环境：${LQG_VERIFY_ENV_FILE:-doc/verify/verify.env}"
echo "面板定义：${SEEDS}"
echo

fail=0
n=0
# 从面板里抽 key / phone / expectIdentity（只认真正列出来的条目）
while IFS=$'\t' read -r key phone expect; do
  [ -n "${key}" ] || continue
  n=$((n + 1))
  printf '%-9s %-34s ' "${key}" "${expect}"
  ME="$(bash doc/verify/api.sh --as "phone:${key}:${phone}" GET /mp/me 2>/dev/null | head -1)"
  got="$(printf '%s' "${ME}" | python3 -c 'import sys,json
try: print(json.load(sys.stdin)["data"]["identity"])
except Exception: print("?")' 2>/dev/null)"

  if [ "${got}" != "${expect}" ]; then
    echo "✗ /mp/me 身份不符（期望 ${expect}，得到 ${got}）"
    fail=$((fail + 1)); continue
  fi

  # 自己那侧的**所有**接口都必须是 200（样本清单 + 文档清单 —— 后者是 Kevin 截图里挂掉的那一页）
  if [ "${expect}" = "internal" ]; then
    own_eps=("/mp/int/sample/list?pageSize=1" "/mp/int/doc/list?pageSize=1")
    other_eps=("/mp/ext/sample/list?pageSize=1" "/mp/ext/doc/list?pageSize=1")
  else
    own_eps=("/mp/ext/sample/list?pageSize=1" "/mp/ext/doc/list?pageSize=1")
    other_eps=("/mp/int/sample/list?pageSize=1" "/mp/int/doc/list?pageSize=1")
  fi

  bad=""
  for ep in "${own_eps[@]}"; do
    code="$(bash doc/verify/api.sh --as "phone:${key}:${phone}" --bizcode GET "${ep}" 2>/dev/null | tail -1 | awk '{print $1}')"
    [ "${code}" = "200" ] || bad="${bad} 自己的 ${ep} → ${code};"
  done
  for ep in "${other_eps[@]}"; do
    code="$(bash doc/verify/api.sh --as "phone:${key}:${phone}" --bizcode GET "${ep}" 2>/dev/null | tail -1 | awk '{print $1}')"
    [ "${code}" = "403" ] || bad="${bad} 另一侧 ${ep} → ${code}（期望 403）;"
  done

  if [ -n "${bad}" ]; then
    echo "✗${bad} —— 这个账号在小程序里用不了 / 或角色闸失效"
    fail=$((fail + 1)); continue
  fi
  echo "✓ 身份对 / 自己那侧 4 条全 200 / 另一侧 4 条全 403"
done < <(grep -oE "key: '[a-zA-Z0-9]+', label: '[^']+', phone: '[0-9]+', expectIdentity: '[a-z]+'" "${SEEDS}" \
          | sed -E "s/key: '([^']+)'.*phone: '([0-9]+)'.*expectIdentity: '([^']+)'/\1\t\2\t\3/")

echo
if [ "${n}" = "0" ]; then echo "✗ 一个面板条目都没解析到（面板被关了？判据失效）"; exit 1; fi
echo "共 ${n} 个账号，失败 ${fail} 条"
[ "${fail}" = "0" ] || exit 1
echo "结果：PASS"
