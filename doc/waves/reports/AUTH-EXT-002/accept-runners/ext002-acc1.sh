#!/usr/bin/env bash
# AUTH-EXT-002 · accept 1（API）—— 可见集合逐身份钉死 + 石蜡包埋键集合白名单 + 内部编号开关两态
#                                  + 四条结构性不变量（契约测试逐字节未改并跑绿）。
#
# 跑法（任意 cwd，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/AUTH-EXT-002/accept-runners/ext002-acc1.sh
#
# ★ 与 ticket 正文 accept 1 的 `run` 只有两处差异，都是**本 agent 沙箱限制**，逐条在此声明：
#   1. 去掉 `--fresh-module ruoyi-lqg`（原文出现在「extB 看 1001 详情」那一段）：
#      该守卫第 71 行用 `ps -o lstart=`，本沙箱 `/bin/ps: Operation not permitted` →
#      带它恒 exit 2（文档化的既有 WARN）。等价证据见完工报告 §4.0：
#      `find <模块>/src -newer <jar>` 为空 + lsof 拿 PID + 进程持该 jar + 启动晚于 jar mtime。
#   2. Maven 那行补三个参数（`-s .mvn-settings.xml -Dmaven.repo.local=.m2repo -Duser.home=.buildhome`）：
#      原样跑会挂在只读的 `~/.m2`（连续多张票命中的既有 WARN）。其余字面逐字保留。
#
# ★ 长链一律包进函数判**整条** rc：`set -e` 不管非末尾位置的失败，`a && b && c` 里 b 挂了会静默 exit 0。
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."   # → 项目根
WS="$(pwd)"
MVN_ARGS=(-s "${WS}/.mvn-settings.xml" -Dmaven.repo.local="${WS}/.m2repo" -Duser.home="${WS}/.buildhome")
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

ids() { bash doc/verify/api.sh --as "$1" GET "/mp/ext/embed/list?pageSize=100&${2:-}" | jq -c '[.rows[].id|tostring]|sort'; }

run_accept_1() {
  bash doc/verify/reseed.sh --yes >/dev/null &&
  bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '([.data.embeds[].paraffinBlockNo])==["T-E01-1","T-E01-2"] and .data.embeds[0].sectioned==true and .data.embeds[0].stainTypes==["HE","IHC"] and ([.data.embeds[0].markers[]|"\(.markerName):\(.expression)"]|sort)==["CK19:negative","Ki67:strong"] and .data.embeds[0].embedBy=="李工" and .data.embeds[0].operatorName=="李工" and (.data.embeds[1].embedBy // null)==null and .data.embeds[1].sectioned==false and ([.data.embeds[]|keys[]]|unique) - ["id","sampleId","submitNo","paraffinBlockNo","sampleType","organoidSourceType","tissueReceiveTime","tissueProcessTime","agaroseEmbedTime","dehydrateTime","agaroseSendTime","paraffinEmbedTime","sectionTime","sectioned","stainTypes","stainOther","markers","verifyStatus","invalidReason","submitterName","mine","editable","embedBy","operatorName"] == []' &&
  bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001002 | jq -e '.code==200 and ([.data.embeds[] | [.paraffinBlockNo, .verifyStatus, .mine, .editable]] == [[null,"pending",true,true]])' &&
  bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001002 | jq -e '[.data.embeds[] | [.verifyStatus, .mine, .editable]] == [["pending",false,false]]' &&
  test "$(ids extA)" = '["9000002001","9000002002","9000002003","9000002006"]' &&
  test "$(ids extB)" = '["9000002001","9000002002","9000002003","9000002006"]' &&
  test "$(ids extC)" = '[]' &&
  test "$(ids extD)" = '["9000002004"]' &&
  test "$(ids extE)" = '[]' &&
  test "$(ids extF)" = '[]' &&
  test "$(ids extA onlyMine=true)" = '["9000002006"]' &&
  test "$(ids extB onlyMine=true)" = '[]' &&
  bash doc/verify/api.sh --as extC GET /mp/ext/sample/9000001001 | jq -e '.code==404 and ((.data // {})|length==0)' &&
  bash doc/verify/api.sh --as extC GET /mp/ext/embed/9000002001 | jq -e '.code==404' &&
  python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" --eq false &&
  bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '(.data|has("internalNo"))==false' &&
  bash doc/verify/api.sh --as extA --bizcode GET '/system/config/list?configKey=lqg.ext.show-internal-no' | grep -qE '^(401|403)' &&
  CID=$(bash doc/verify/api.sh --as admin GET '/system/config/list?configKey=lqg.ext.show-internal-no' | jq -r '.rows[0].configId') && test -n "${CID}" && test "${CID}" != null &&
  bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"true\",\"configType\":\"N\"}" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '.data.internalNo=="T-hli01"' &&
  bash doc/verify/api.sh --as extA GET /mp/ext/sample/9000001002 | jq -e '(.data.internalNo // null)==null' &&
  bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"false\",\"configType\":\"N\"}" | jq -e '.code==200' &&
  bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -e '(.data|has("internalNo"))==false' &&
  cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
  (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true "${MVN_ARGS[@]}")
}

echo "== accept 1 · 可见集合 / 白名单键 / 内部编号开关两态 / 四条不变量 =="
bash doc/verify/reseed.sh --yes >/dev/null
echo "-- 六身份 /mp/ext/embed/list id 集合 + onlyMine 两档 --"
printf 'extA            %s\n' "$(ids extA)"
printf 'extB            %s\n' "$(ids extB)"
printf 'extC            %s\n' "$(ids extC)"
printf 'extD            %s\n' "$(ids extD)"
printf 'extE            %s\n' "$(ids extE)"
printf 'extF            %s\n' "$(ids extF)"
printf 'extA onlyMine   %s\n' "$(ids extA onlyMine=true)"
printf 'extB onlyMine   %s\n' "$(ids extB onlyMine=true)"
echo "-- 1001 详情的 embeds 键集合差集（对 24 键白名单，应为空）--"
bash doc/verify/api.sh --as extB GET /mp/ext/sample/9000001001 | jq -c '([.data.embeds[]|keys[]]|unique) - ["id","sampleId","submitNo","paraffinBlockNo","sampleType","organoidSourceType","tissueReceiveTime","tissueProcessTime","agaroseEmbedTime","dehydrateTime","agaroseSendTime","paraffinEmbedTime","sectionTime","sectioned","stainTypes","stainOther","markers","verifyStatus","invalidReason","submitterName","mine","editable","embedBy","operatorName"]'
echo "-- 开关初值 --"
python3 doc/verify/db.py --sql "SELECT config_key||'='||config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'"
run_accept_1
RC=$?
if [ "${RC}" -ne 0 ]; then
  # 链中途红了可能停在开关=true 的中间态（ticket 的 counterfeit 备注点名过这件事）
  bash doc/verify/api.sh --as admin PUT /system/config '{"configId":5001,"configName":"外部页面显示内部编号","configKey":"lqg.ext.show-internal-no","configValue":"false","configType":"N"}' >/dev/null 2>&1 || true
  echo "[runner] 链失败 → 已把开关兜底还原成 false"
fi
echo "ACCEPT-1 EXIT=${RC}"
exit "${RC}"
