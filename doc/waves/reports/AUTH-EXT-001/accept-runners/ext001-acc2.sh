#!/usr/bin/env bash
# AUTH-EXT-001 · accept 2（API）—— 可见集合逐身份钉死 / 越权 404 / 键集合 / 角色闸。
#
# 跑法（cwd = 项目根）：
#   bash doc/waves/reports/AUTH-EXT-001/accept-runners/ext001-acc2.sh
#
# ★ 与 ticket 正文的 accept.run 的差异**只有两处**，都在下面逐条注明：
#   (1) 去掉 `--fresh-module ruoyi-lqg`：那个守卫在 subagent 沙箱里恒 exit 2
#       （`ps -o lstart=` 被禁），等价证据见完工报告 §4.0。别的字面逐字保留。
#   (2) `/mp/int/sample/list` 那一段（正文倒数第 2 段）**拆成独立函数** `run_mpint_literal()`：
#       它现在必然红，但红的原因**不在本票**。逐字原样跑：
#         $ bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/sample/list'
#         404	No endpoint GET /mp/int/sample/list.
#       `/mp/int/**` 的端点由 SAMPLE-MP-001（小程序内部侧）注册，本票**不能**去注册它
#       （ticket §3 边界），所以那段断言测的是一个还不存在的前置条件。按「不许自造」的规矩
#       不删不改，只把它隔离出来单独报告 —— 见完工报告 §4.2 与 WARN-3。
#       角色闸**两个方向**本票都实测到了（内部打 /mp/ext 全部 403；外部打 /lqg 403），
#       证据在 probes/ext001-probes.sh 的 P11a-d / P12。
#
# ★ 长链不写成一行 `a && b && c`：`set -e` 不管非末尾位置的失败，会静默短路 exit 0
#   （AUTH-STAFF-001 坑 3 / AUTH-GROUP-001 §7.5）。这里包进函数判**整条链的 rc**。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
ROOT="$(pwd)"

rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*    # token 缓存只按 mtime 判新鲜（AUTH-STAFF-001 WARN-4）

ids() { bash doc/verify/api.sh --as "$1" GET "/mp/ext/sample/list?pageSize=100&${2:-}" | jq -c '[.rows[].id|tostring]|sort'; }

run_accept_2() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  test "$(ids extA)" = '["9000001001","9000001002","9000001003","9000001004"]' &&
  test "$(ids extB)" = '["9000001001","9000001002","9000001003","9000001004"]' &&
  test "$(ids extC)" = '["9000001005"]' && test "$(ids extD)" = '["9000001006"]' &&
  test "$(ids extE)" = '["9000001007"]' && test "$(ids extF)" = '[]' &&
  test "$(ids extA onlyMine=true)" = '["9000001001","9000001002","9000001003"]' && test "$(ids extB onlyMine=true)" = '["9000001004"]' &&
  test "$(ids extB 'onlyMine=true&verifyStatus=pending')" = '[]' && test "$(ids extA 'sampleKind=organoid')" = '[]' &&
  bash doc/verify/api.sh --as extC GET /mp/ext/sample/9000001001 | jq -e '.code==404 and ((.data // {}) | length == 0)' &&
  bash doc/verify/api.sh --as extE GET /mp/ext/sample/9000001001 | jq -e '.code==404' &&
  bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001010 | jq -e '.code==404' &&
  bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '.code==200 and .data.donorName=="测试供体甲" and .data.mine==false and .data.editable==false and .data.submitterName=="王医生" and ((.data|keys) - ["id","submitNo","sampleKind","sourceUnitName","donorName","gender","age","hospitalNo","tissueType","organoidType","hasPathology","remark","verifyStatus","invalidReason","submitterName","mine","editable","createTime","embeds","docs"] == [])' &&
  bash doc/verify/api.sh --as extA GET '/mp/ext/sample/list?pageSize=100' | jq -e '[.rows[] | keys[]] | unique | (index("internalNo")==null and index("donorName")==null and index("hospitalNo")==null and index("operatorName")==null) and (index("donorNameMasked")!=null)' &&
  bash doc/verify/api.sh --as extA --bizcode GET /mp/ext/home | grep -qE '^404' &&
  bash doc/verify/api.sh --as extA --bizcode GET '/lqg/sample/list' | grep -qE '^403' &&
  bash doc/verify/api.sh --as staff --bizcode GET '/mp/ext/sample/list' | grep -qE '^403'
}

# ticket 正文里本票跑不绿的那一段（原因见文件头 (2)）——单独跑、单独报，不混进上面那条链。
run_mpint_literal() {
  bash doc/verify/api.sh --as extA --bizcode GET '/mp/int/sample/list' | grep -qE '^403'
}

echo "== accept 2 · 可见集合 / 越权 / 键集合 / 角色闸 =="
run_accept_2
RC=$?
echo "ACCEPT-2 EXIT=${RC}"
echo "== 正文倒数第 2 段（/mp/int，上游未注册，预期红）=="
run_mpint_literal
echo "MPINT-LITERAL EXIT=$? （1 = 该端点是 404 而不是 403，原因见 WARN-3）"
exit "${RC}"

