#!/usr/bin/env bash
# D2 r3 · L3 第 4 条：角色闸 —— 外部打 /lqg/** 与 /mp/int/** 一律 403；内部打 /mp/ext/** 403（正反都断）。
# 注意：api.sh 的选项（--as/--bizcode）必须写在 METHOD PATH 之前，放后面会被当成 JSON body。
set -u
cd "$(dirname "$0")/../../../.."
WANT='没有访问权限，请联系管理员授权'
CHK() { # $1=身份 $2=期望业务码 $3=说明 $4=METHOD $5=PATH [$6=BODY]
  local as="$1" want="$2" desc="$3" method="$4" path="$5" body="${6:-}"
  local got
  if [ -n "$body" ]; then got=$(bash doc/verify/api.sh --as "$as" --bizcode "$method" "$path" "$body" 2>&1 | head -1)
  else got=$(bash doc/verify/api.sh --as "$as" --bizcode "$method" "$path" 2>&1 | head -1); fi
  local code="${got%%$'\t'*}" msg="${got#*$'\t'}"
  local mark="OK  "
  if [ "$code" != "$want" ]; then mark="!!BAD"; fi
  if [ "$code" = "403" ] && [ "$msg" != "$WANT" ]; then mark="!!MSG"; fi
  if [ "$code" = "$want" ] && [ "$code" != "403" ] && [ "$code" != "200" ]; then mark="!!BAD"; fi
  printf '%s  want=%-3s got=%-3s  %-42s  msg=%s\n' "$mark" "$want" "$code" "$desc" "$msg"
}

echo "== A 外部 → /lqg/** 必须 403 =="
CHK extA 403 "GET /lqg/sample/list"            GET /lqg/sample/list
CHK extA 403 "PUT /lqg/sample/9000001002/verify" PUT /lqg/sample/9000001002/verify '{"action":"invalid","invalidReason":"L3R3越权"}'
echo "== B 外部 → /mp/int/** 必须 403 =="
CHK extA 403 "GET /mp/int/sample/list"       GET /mp/int/sample/list
CHK extA 403 "GET /mp/int/sample/9000001001" GET /mp/int/sample/9000001001
CHK extA 403 "PUT /mp/int/sample"            PUT /mp/int/sample '{"id":9000001001,"sampleKind":"tissue"}'
echo "== C 内部 → /mp/ext/** 必须 403 =="
CHK staff 403 "GET /mp/ext/sample/list"        GET /mp/ext/sample/list
CHK staff 403 "GET /mp/ext/sample/9000001001"  GET /mp/ext/sample/9000001001
CHK staff 403 "POST /mp/ext/organoid"          POST /mp/ext/organoid '{"sourceUnitName":"A 医院","organoidType":"L3R3"}'
CHK admin 403 "GET /mp/ext/sample/list（超管也 403）" GET /mp/ext/sample/list
echo "== D 对照组：同路径正确角色下 200（证明是权限分支，不是端点不存在）=="
CHK extA  200 "GET /mp/ext/sample/list"  GET /mp/ext/sample/list
CHK staff 200 "GET /mp/int/sample/list"  GET /mp/int/sample/list
CHK admin 200 "GET /lqg/sample/list"     GET /lqg/sample/list
echo "== E 越权请求的库内副作用自查（extA 那条 PUT verify 不得改库）=="
python3 doc/verify/db.py --sql "SELECT count(*) FROM t_lqg_sample WHERE verify_status='invalid' AND invalid_reason='L3R3越权'"
echo "越权 invalid_reason 落库行数（应 0）exit=$?"
