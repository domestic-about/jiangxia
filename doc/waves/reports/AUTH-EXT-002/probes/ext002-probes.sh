#!/usr/bin/env bash
# AUTH-EXT-002 · 对抗性探针 —— accept 三条没直接断的形态（越权 / 状态闸 / 键集合 / 软删 / 开关边界）。
# 每条自带库内或响应体断言，最后打印 PASS/FAIL 计数。
#
# 跑法（任意 cwd，脚本自己 cd 到项目根）：
#   bash doc/waves/reports/AUTH-EXT-002/probes/ext002-probes.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."
rm -f "${TMPDIR:-/tmp}"/lqg-verify-token-*

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf 'PASS %s\n' "$*"; }
bad()  { FAIL=$((FAIL+1)); printf 'FAIL %s\n' "$*"; }
biz()  { bash doc/verify/api.sh --as "$1" --bizcode GET "$2"; }
body() { bash doc/verify/api.sh --as "$1" GET "$2"; }
put()  { bash doc/verify/api.sh --as "$1" --bizcode PUT "$2" "$3"; }
post() { bash doc/verify/api.sh --as "$1" --bizcode POST "$2" "$3"; }
sql()  { python3 doc/verify/db.py --sql "$1"; }

bash doc/verify/reseed.sh --yes >/dev/null

# ── P1-P4 角色闸两个方向（真实存在的方法，不是 405/404 的 METHOD+PATH 组合）──
V="$(biz staff /mp/ext/embed/list)"; case "${V}" in 403*) ok "P1-staff打外部列表-403 — ${V}";; *) bad "P1-staff打外部列表 — ${V}";; esac
V="$(biz admin /mp/ext/embed/9000002001)"; case "${V}" in 403*) ok "P2-admin打外部单条-403 — ${V}";; *) bad "P2-admin打外部单条 — ${V}";; esac
V="$(post staff /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织"}')"; case "${V}" in 403*) ok "P3-staff打外部提交-403 — ${V}";; *) bad "P3-staff打外部提交 — ${V}";; esac
V="$(biz extA /lqg/embed/list)"; case "${V}" in 403*) ok "P4-外部打内部lqg-403 — ${V}";; *) bad "P4-外部打内部lqg — ${V}";; esac

# ── P5 外部打 /mp/int/embed/list（EMBED-MP-001 的端点，本票时点可能还没注册）──
V="$(biz extA /mp/int/embed/list)"
case "${V}" in
  403*) ok "P5-外部打mpint-403（端点已注册） — ${V}";;
  404*) ok "P5-外部打mpint-未注册404（EMBED-MP-001 尚未落，与 AUTH-EXT-001 WARN-3 同源） — ${V}";;
  *)    bad "P5-外部打mpint — ${V}";;
esac

# ── P6-P7 列表筛选：verifyStatus / onlyMine（后者是「按记录提交人 ≠ 按样本提交人」的判据）──
V="$(body extA '/mp/ext/embed/list?pageSize=100&verifyStatus=pending' | jq -c '[.rows[].id|tostring]|sort')"
[ "${V}" = '["9000002006"]' ] && ok "P6-verifyStatus=pending只出待核验 — ${V}" || bad "P6-verifyStatus=pending — ${V}"
V="$(body extA '/mp/ext/embed/list?pageSize=100&onlyMine=true&verifyStatus=pending' | jq -c '[.rows[].id|tostring]|sort')"
[ "${V}" = '["9000002006"]' ] && ok "P7a-extA-onlyMine+pending — ${V}" || bad "P7a-extA-onlyMine+pending — ${V}"
V="$(body extB '/mp/ext/embed/list?pageSize=100&onlyMine=true&verifyStatus=pending' | jq -c '[.rows[].id|tostring]|sort')"
[ "${V}" = '[]' ] && ok "P7b-extB-onlyMine+pending-空（2003 是实验室在他样本上建的） — ${V}" || bad "P7b-extB-onlyMine+pending — ${V}"

# ── P8 单条详情也逐行算 mine/editable（不是只算列表）──
V="$(body extA /mp/ext/embed/9000002006 | jq -c '[.data.verifyStatus,.data.mine,.data.editable]')"
[ "${V}" = '["pending",true,true]' ] && ok "P8a-extA看自己待核验送样 — ${V}" || bad "P8a-extA看自己待核验送样 — ${V}"
V="$(body extB /mp/ext/embed/9000002006 | jq -c '[.data.verifyStatus,.data.mine,.data.editable]')"
[ "${V}" = '["pending",false,false]' ] && ok "P8b-extB看同组送样-不可改 — ${V}" || bad "P8b-extB看同组送样 — ${V}"

# ── P9 核验有效后只读：所挂样本与送样都判有效 → extA 的 editable 变 false，PUT 被拒且库内不变 ──
bash doc/verify/api.sh --as staff PUT /lqg/sample/9000001002/verify '{"action":"valid","receiveDate":"2026-09-17","internalNo":"T-probe88"}' >/dev/null
bash doc/verify/api.sh --as staff PUT /lqg/embed/9000002006/verify '{"action":"valid","paraffinBlockNo":"T-P9-1"}' >/dev/null
V="$(body extA /mp/ext/sample/9000001002 | jq -c '[.data.embeds[]|[.verifyStatus,.mine,.editable]]')"
[ "${V}" = '[["valid",true,false]]' ] && ok "P9a-核验有效后详情里 editable=false — ${V}" || bad "P9a-核验有效后 editable — ${V}"
V="$(put extA /mp/ext/embed/9000002006 '{"sampleId":9000001002,"sampleType":"核验后还想改"}')"
V2="$(sql "SELECT sample_type FROM t_lqg_embed WHERE id=9000002006")"
case "${V}" in 400*) : ;; *) bad "P9b-已核验送样 PUT 未被拒 — ${V}";; esac
[ "${V2}" = '组织' ] && ok "P9b-已核验送样 PUT 被拒(400)且库内不变（seed 值=组织） — ${V} / ${V2}" || bad "P9b-库内被改 — ${V2}"
bash doc/verify/reseed.sh --yes >/dev/null

# ── P10 越权改：extC 看不到 2001（样本 1001 不可见）→ 被拒且库内不变 ──
V="$(put extC /mp/ext/embed/9000002001 '{"sampleId":9000001001,"sampleType":"外单位改"}')"
V2="$(sql "SELECT count(*) FROM t_lqg_embed WHERE sample_type='外单位改'")"
case "${V}" in 400*|404*) : ;; *) bad "P10a-extC 改 2001 未被拒 — ${V}";; esac
[ "${V2}" = '0' ] && ok "P10a-extC改不可见记录被拒且库内不变 — ${V} / ${V2}" || bad "P10a-库内被改 — ${V2}"

# ── P11 改实验室的块：extA 对 2001（internal，submitter=李工）→ 400 且库内不变 ──
V="$(put extA /mp/ext/embed/9000002001 '{"sampleId":9000001001,"sampleType":"改实验室的块"}')"
V2="$(sql "SELECT sample_type FROM t_lqg_embed WHERE id=9000002001")"
case "${V}" in 400*) : ;; *) bad "P11a-改实验室的块未被拒 — ${V}";; esac
[ "${V2}" = '组织' ] && ok "P11a-实验室录入的块外部只读 — ${V} / ${V2}" || bad "P11a-库内被改 — ${V2}"

# ── P12 PUT 换挂样本：换成别人的样本 → 400 且 sample_id 不变 ──
V="$(put extA /mp/ext/embed/9000002006 '{"sampleId":9000001004,"sampleType":"换挂别人的样本"}')"
V2="$(sql "SELECT sample_id FROM t_lqg_embed WHERE id=9000002006")"
case "${V}" in 400*) : ;; *) bad "P12a-换挂别人样本未被拒 — ${V}";; esac
[ "${V2}" = '9000001002' ] && ok "P12a-换挂别人样本被拒且 sample_id 不变 — ${V} / ${V2}" || bad "P12a-sample_id 被改 — ${V2}"

# ── P13 软删的 2005 任何入口都不出现 ──
V="$(sql "SELECT count(*) FROM t_lqg_embed WHERE id=9000002005 AND del_flag='1'")"
V2="$(biz extA /mp/ext/embed/9000002005)"
[ "${V}" = '1' ] && [ "${V2}" = '404	石蜡包埋记录不存在' ] && ok "P13-软删记录不在外部接口出现（404）— ${V}/${V2}" || bad "P13-软删记录 — ${V}/${V2}"
V="$(body extB '/mp/ext/embed/list?pageSize=100' | jq -c '[.rows[].id|tostring]|sort')"
case "${V}" in *2005*) bad "P13b-软删 2005 出现在列表 — ${V}";; *) ok "P13b-软删 2005 不在列表 — ${V}";; esac

# ── P14 submitNo 是所挂样本的送检单号 ──
V="$(body extA /mp/ext/embed/9000002006 | jq -r '.data.submitNo')"
[ "${V}" = 'SJ90000002' ] && ok "P14-单条 submitNo=所挂样本送检单号 — ${V}" || bad "P14-submitNo — ${V}"

# ── P15 开关打开时「外面那层」（样本详情）才有内部编号；包埋记录 VO 永远没有这个键 ──
CID=$(bash doc/verify/api.sh --as admin GET '/system/config/list?configKey=lqg.ext.show-internal-no' | jq -r '.rows[0].configId')
bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"true\",\"configType\":\"N\"}" >/dev/null
# ★ 注意 jq 的 `,` 比 `|` 结合得紧：数组里两段各自要括号，否则第二段会被喂给 `.data`
#   （第一版没括号 → `.data.embeds` 变成「样本对象自己的 embeds」= null → Cannot iterate over null）
V="$(body extA /mp/ext/sample/9000001002 | jq -c '[(.data|has("internalNo")), ([.data.embeds[]|keys[]]|unique|index("internalNo"))]')"
[ "${V}" = '[false,null]' ] && ok "P15a-开关开-待核验样本仍无编号、包埋VO也无该键 — ${V}" || bad "P15a-开关开键集合 — ${V}"
V="$(body extB '/mp/ext/embed/list?pageSize=100' | jq -c '([.rows[]|keys[]]|unique|index("internalNo"))')"
[ "${V}" = 'null' ] && ok "P15b-开关开-外部包埋列表也不给内部编号（列表行按送检单号指代） — ${V}" || bad "P15b-列表给了内部编号 — ${V}"
bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"false\",\"configType\":\"N\"}" >/dev/null
V="$(sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'")"
[ "${V}" = 'false' ] && ok "P15c-开关已还原 false — ${V}" || bad "P15c-开关未还原 — ${V}"

# ── P16 没包埋记录的样本：详情 embeds = [] 且不报错 ──
V="$(body extA /mp/ext/sample/9000001003 | jq -c '[.code, .data.embeds]')"
[ "${V}" = '[200,[]]' ] && ok "P16-没记录的样本 embeds=[] — ${V}" || bad "P16-没记录的样本 — ${V}"

# ── P17 不可见的样本详情仍是 404（没因为 embeds 装配而漏出去）──
V="$(biz extA /mp/ext/sample/9000001005)"
case "${V}" in 404*) ok "P17-不可见样本详情 404 — ${V}";; *) bad "P17-不可见样本详情 — ${V}";; esac

# ── P18 不存在的样本 id 提交 → 被拒且 external 记录数不变 ──
V="$(post extA /mp/ext/embed '{"sampleId":9000999999,"sampleType":"不存在的样本"}')"
V2="$(sql "SELECT count(*) FROM t_lqg_embed WHERE submit_source='external' AND del_flag='0'")"
case "${V}" in 400*|404*) : ;; *) bad "P18a-不存在的样本未被拒 — ${V}";; esac
[ "${V2}" = '1' ] && ok "P18a-不存在的样本被拒且未建记录 — ${V} / ${V2}" || bad "P18a-建了记录 — ${V2}"

# ── P19 夹带的包埋人工段时间等一个都落不进去（第二次、另一个样本）──
bash doc/verify/api.sh --as extA POST /mp/ext/embed '{"sampleId":9000001001,"sampleType":"组织","embedBy":"外部","operatorName":"外部","tissueReceiveTime":"2026-01-01","sectionTime":"2026-01-02","markers":[{"markerName":"X","expression":"strong"}],"remark":"外注"}' >/dev/null
V="$(sql "SELECT COALESCE(embed_by,'-')||'|'||COALESCE(operator_name,'-')||'|'||COALESCE(tissue_receive_time::text,'-')||'|'||COALESCE(section_time::text,'-')||'|'||COALESCE(remark,'-') FROM t_lqg_embed WHERE sample_id=9000001001 AND submit_source='external'")"
[ "${V}" = '-|-|-|-|-' ] && ok "P19-夹带包埋人/操作人/工序时间/备注全不落库 — ${V}" || bad "P19-夹带落库了 — ${V}"
V="$(sql "SELECT count(*) FROM t_lqg_embed_marker m JOIN t_lqg_embed e ON e.id=m.embed_id WHERE e.submit_source='external'")"
[ "${V}" = '0' ] && ok "P19b-夹带的 marker 也没落库 — ${V}" || bad "P19b-marker 落库了 — ${V}"

bash doc/verify/reseed.sh --yes >/dev/null
echo "PROBE EXT002 PASS=${PASS} FAIL=${FAIL}"
[ "${FAIL}" -eq 0 ]
